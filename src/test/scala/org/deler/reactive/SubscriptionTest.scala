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

  "an open MutableSubscription" should {
    val subject = new MutableSubscription
    val delegate1 = mock[Subscription]
    val delegate2 = mock[Subscription]

    "close the current subscription when closed" in {
      subject.set(delegate1)

      subject.close()

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
  }

  "an open CompositeSubscription" should {
    val subject = new CompositeSubscription
    val delegate1 = mock[Subscription]
    val delegate2 = mock[Subscription]

    subject add delegate1
    subject add delegate2

    "not close subscriptions that are added" in {
      there were noMoreCallsTo(delegate1)
      there were noMoreCallsTo(delegate2)
    }

    "close subscriptions that are removed" in {
      subject remove delegate1

      there was one(delegate1).close()
      there were noMoreCallsTo(delegate2)
    }

    "close all added subscriptions when closed" in {
      subject.close()

      there was one(delegate1).close()
      there was one(delegate2).close()
    }
  }

  "a closed CompositeSubscription" should {
    val subject = new CompositeSubscription
    val delegate = mock[Subscription]
    subject.close()

    "close subscriptions that are added" in {
      subject add delegate

      there was one(delegate).close()
    }
  }
}
