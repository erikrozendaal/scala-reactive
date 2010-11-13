package org.deler.reactive

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import org.specs.mock.Mockito

@RunWith(classOf[JUnitSuiteRunner])
class SubscriptionTest extends Specification with JUnit with Mockito {
  "BooleanSubscription" should {
    val subject = new BooleanSubscription
    "not be closed initially" in {
      subject.closed must beFalse
    }

    "be closed after closing" in {
      subject.close()

      subject.closed must beTrue
    }
  }

  "ActionSubscription" should {
    "execute action once on close" in {
      var count = 0

      val subject = new ActionSubscription(() => count += 1)

      subject.close()
      count must be equalTo 1

      subject.close()
      count must be equalTo 1
    }
  }

  "an open MutableSubscription" should {
    val subject = new MutableSubscription
    val delegate1 = mock[Subscription]
    val delegate2 = mock[Subscription]

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

  "a closed MutableSubscription" should {
    val subject = new MutableSubscription
    subject.close()
    val delegate = mock[Subscription]

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

  "an open CompositeSubscription" should {
    val delegate1 = mock[Subscription]
    val delegate2 = mock[Subscription]
    val subject = new CompositeSubscription(delegate1, delegate2)

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
      val subscription = mock[Subscription]

      subject -= subscription

      there were noMoreCallsTo(subscription)
    }

    "close all contained subscriptions when cleared, but not close itself" in {
      val subscription = mock[Subscription]

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
      val subscription = mock[Subscription]
      subject.close()

      subject -= subscription

      there were noMoreCallsTo(subscription)
    }
  }

  "a closed CompositeSubscription" should {
    val subject = new CompositeSubscription
    val delegate = mock[Subscription]
    subject.close()

    "close subscriptions that are added" in {
      subject += delegate

      there was one(delegate).close()
    }
  }
}
