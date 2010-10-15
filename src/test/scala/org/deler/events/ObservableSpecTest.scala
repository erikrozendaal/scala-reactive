package org.deler.events

import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{ JUnitSuiteRunner, JUnit }
import org.mockito.Matchers._
import scala.collection._

@RunWith(classOf[JUnitSuiteRunner])
class ObservableSpecTest extends Specification with JUnit with Mockito {

  import Observable._

  val ex = new Exception("fail")

    val emptyObservable: Observable[String] = Seq()
    val multivaluedObservable: Observable[String] = Seq("first value", "second value")

    val observer = mock[Observer[String]]

  "traversables as observable" should {

    "only invoke onComplete when empty" in {
      emptyObservable.subscribe(observer)

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "invoke onNext for each contained element followed by onComplete" in {
      multivaluedObservable.subscribe(observer)

      there was one(observer).onNext("first value") then one(observer).onNext("second value") then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "invoke onError when an exception is raised" in {
      val fail = new Traversable[String] {
        def foreach[U](f: String => U) = throw ex
      }

      fail.subscribe(observer)

      there was one(observer).onError(ex)
      there were noMoreCallsTo(observer)
    }
  }

  "observables" should {
    val observable: Observable[String] = Seq("event")
    val failingObservable = new Observable[String] {
      override def subscribe(observer: Observer[String]) = {
        observer.onError(ex)
        noopSubscription
      }
    }
  
    "allow easy subscription using single onNext method" in {
      observable subscribe (onNext = observer onNext _)

      there was one(observer).onNext("event")
      there were noMoreCallsTo(observer)
    }
    "allow easy subscription using multiple methods" in {
      observable subscribe (onCompleted = () => observer.onCompleted(), onNext = observer.onNext(_))

      there was one(observer).onNext("event") then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "allow easy subscription using single onError method" in {
      failingObservable.subscribe(onError = observer.onError(_))

      there was one(observer).onError(ex)
      there were noMoreCallsTo(observer)
    }
    "allow observing using for-comprehension" in {
      val events = Observable.asSeq(for (event <- observable) yield event)

      events must be equalTo List("event")
    }
//    "allow filtering using for-comprehension" in {
//      val events = Observable.asSeq(for (event <- multivaluedObservable if event == "first event") yield event)
//      
//      events must be equalTo List("first event")
//    }
    
    //		"allow for nested for-comprehension" in {
    //			val events = Observable.asSeq(for (e1 <- observable; e2 <- observable) yield (e1, e2))
    //			events must have size 4
    //		}
  }

}
