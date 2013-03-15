package org.deler.reactive

@org.junit.runner.RunWith(classOf[org.specs2.runner.JUnitRunner])
class CloseableTest extends Test {
  isolated

  "BooleanCloseable" should {
    val subject = new BooleanCloseable
    "not be closed initially" in {
      subject.closed must beFalse
    }

    "be closed after closing" in {
      subject.close()

      subject.closed must beTrue
    }
  }

  "ActionCloseable" should {
    "execute action once on close" in {
      var count = 0

      val subject = new ActionCloseable(() => count += 1)

      subject.close()
      count must be equalTo 1

      subject.close()
      count must be equalTo 1
    }
  }

  "an open MutableCloseable" should {
    val subject = new MutableCloseable
    val delegate1 = mock[Closeable]
    val delegate2 = mock[Closeable]

    "close the current subscription when closed" in {
      subject.set(delegate1)

      subject.close()

      there was one(delegate1).close()
    }

    "close the current subscription when cleared, but not close a new subscription" in {
      subject.set(delegate1)

      subject.clear()
      subject.set(delegate2)

      there was one(delegate1).close()
      there were noMoreCallsTo(delegate2)
    }

    "close the current subscription before invoking the action to set the new subscription" in {
      subject.set(delegate1)

      subject clearAndSet {
        there was one(delegate1).close()

        delegate2
      }

      there were noMoreCallsTo(delegate2)
    }

    "close the returned subscription when the action closes this subscription" in {
      subject clearAndSet {
        subject.close()
        delegate1
      }

      there was one(delegate1).close()
    }

    "close the previous subscription when it is replaced" in {
      subject.set(delegate1)
      subject.set(delegate2)

      there was one(delegate1).close()
      there were noMoreCallsTo(delegate2)
    }
  }

  "a closed MutableCloseable" should {
    val subject = new MutableCloseable
    subject.close()
    val delegate = mock[Closeable]

    "close a new subscription when set" in {
      subject.set(delegate)

      there was one(delegate).close()
    }

    "not invoke the action when clear-and-set is used" in {
      var invoked = false

      subject clearAndSet {
        invoked = true
        delegate
      }

      invoked must beFalse
    }
  }

  "an open CompositeCloseable" should {
    val delegate1 = mock[Closeable]
    val delegate2 = mock[Closeable]
    val subject = new CompositeCloseable(delegate1, delegate2)

    "not close subscriptions that are added" in {
      there were noMoreCallsTo(delegate1)
      there were noMoreCallsTo(delegate2)
    }

    "close added subscriptions that are removed" in {
      subject -= delegate1

      there was one(delegate1).close()
      there were noMoreCallsTo(delegate2)
    }

    "not close subscription that are removed but were not added" in {
      val subscription = mock[Closeable]

      subject -= subscription

      there were noMoreCallsTo(subscription)
    }

    "close all contained subscriptions when cleared, but not close itself" in {
      val subscription = mock[Closeable]

      subject.clear()
      subject += subscription

      there was one(delegate1).close()
      there was one(delegate2).close()
      there were noMoreCallsTo(subscription)
    }

    "close all contained subscriptions when closed" in {
      subject.close()

      there was one(delegate1).close()
      there was one(delegate2).close()
    }

    "not close subscriptions that are passed to remove when closed" in {
      val subscription = mock[Closeable]
      subject.close()

      subject -= subscription

      there were noMoreCallsTo(subscription)
    }
  }

  "a closed CompositeCloseable" should {
    val subject = new CompositeCloseable
    val delegate = mock[Closeable]
    subject.close()

    "close subscriptions that are added" in {
      subject += delegate

      there was one(delegate).close()
    }
  }
}
