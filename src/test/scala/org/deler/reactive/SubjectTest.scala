package org.deler.reactive

import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{JUnitSuiteRunner, JUnit}
import org.mockito.Matchers._
import scala.collection._

@RunWith(classOf[JUnitSuiteRunner])
class SubjectTest extends Specification with JUnit with Mockito {
  val observer = mock[Observer[String]]
  val observer2 = mock[Observer[String]]
  var subscription: Subscription = _

  var subject = new Subject[String]

  val replaySubject = beforeContext(subject = new ReplaySubject[String])

  val singleObserver = beforeContext(subscription = subject.subscribe(observer))
  val multipleObservers = beforeContext {subscription = subject.subscribe(observer); subject.subscribe(observer2)}

  val event = "event"
  val error = new Exception("error")

  "subjects with a single observer" definedAs singleObserver should {

    "publish events to observer" in {
      subject.onNext(event)

      there was one(observer).onNext(event)
    }
    "stop publishing events after onCompleted" in {
      subject.onCompleted()
      subject.onNext(event)

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "stop publishing events after onError" in {
      subject.onError(error)
      subject.onNext(event)

      there was one(observer).onError(error)
      there were noMoreCallsTo(observer)
    }
    "stop publishing onCompleted after onError" in {
      subject.onError(error)
      subject.onCompleted()

      there was one(observer).onError(error)
      there were noMoreCallsTo(observer)
    }
    "stop publishing after a subscription is closed" in {
      subscription.close()

      subject.onNext(event)

      there were noMoreCallsTo(observer)
    }
  }

  "subjects with multiple observers" definedAs multipleObservers should {

    "publish onNext to all observers" in {
      subject.onNext(event)

      there was one(observer).onNext(event)
      there was one(observer2).onNext(event)
    }
    "publish onCompleted to all observers" in {
      subject.onCompleted()

      there was one(observer).onCompleted()
      there was one(observer2).onCompleted()
    }
    "publish onError to all observers" in {
      subject.onError(error)

      there was one(observer).onError(error)
      there was one(observer2).onError(error)
    }
    "continue publishing events after one observer unsubscribes" in {
      subscription.close()

      subject.onNext(event)

      there were noMoreCallsTo(observer)
      there was one(observer2).onNext(event)
    }

  }

  "replay subjects" definedAs replaySubject should {
    behave like "subjects with a single observer"
    behave like "subjects with multiple observers"

    "replay all past events to new subscriptions" in {
      subject.onNext("a")
      subject.onNext("b")

      subject.subscribe(observer)

      there was one(observer).onNext("a") then one(observer).onNext("b")
      there were noMoreCallsTo(observer)
    }
    "replay all past events to a new subscription followed by onComplete when completed" in {
      subject.onNext(event)
      subject.onCompleted()

      subject.subscribe(observer)

      there was one(observer).onNext(event) then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "replay all past events to a new subscription followed by onError when failed" in {
      subject.onNext(event)
      subject.onError(error)

      subject.subscribe(observer)

      there was one(observer).onNext(event) then one(observer).onError(error)
      there were noMoreCallsTo(observer)
    }
  }

}
