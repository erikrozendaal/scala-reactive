package org.deler.events

import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{ JUnitSuiteRunner, JUnit }
import org.mockito.Matchers._
import scala.collection._

@RunWith(classOf[JUnitSuiteRunner])
class ObservableTest extends Specification with JUnit with Mockito {

  import Observable.traversable2observable

  val ex = new Exception("fail")

  val emptyTraversable = Seq[String]()
  val multivaluedTraversable = Seq("first value", "second value")

  val observer = mock[Observer[String]]

  "traversables as observable" should {

    "only invoke onComplete when empty" in {
      emptyTraversable.toObservable.subscribe(observer)

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "invoke onNext for each contained element followed by onComplete" in {
      multivaluedTraversable.toObservable.subscribe(observer)

      there was one(observer).onNext("first value") then one(observer).onNext("second value") then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
    "invoke onError when an exception is raised" in {
      val fail = new Traversable[String] {
        def foreach[U](f: String => U) = throw ex
      }

      fail.toObservable.subscribe(observer)

      there was one(observer).onError(ex)
      there were noMoreCallsTo(observer)
    }
    "stop producing values when the subscription is closed" in {
      Scheduler.currentThread schedule {
        var subscription: Subscription = null

        subscription = multivaluedTraversable.toObservable(Scheduler.currentThread).perform(subscription.close()).subscribe(observer)
      }
      there was one(observer).onNext("first value")
      there were noMoreCallsTo(observer)
    }
  }

  "observables" should {
    val observable = Seq("event").toObservable
    val failingObservable = new Observable[String] {
      override def subscribe(observer: Observer[String]) = {
        observer.onError(ex)
        Observable.noopSubscription
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
    "collect events" in {
      val observable = Seq(1, "event").toObservable

      val collected = Observable.toSeq(observable collect { case x: String => x })

      collected must be equalTo List("event")
    }
    "allow observing using for-comprehension" in {
      val events = Observable.toSeq(for (event <- observable) yield event)

      events must be equalTo List("event")
    }
    "allow filtering using for-comprehension" in {
      val events = Observable.toSeq(for (event <- multivaluedTraversable.toObservable if event == "first value") yield event)

      events must be equalTo List("first value")
    }
    "allow easy observation of last published value" in {
      val subject = new Subject[String]
      val observed = subject.latest

      observed.current must be equalTo None

      subject.onNext("hello")

      observed.current must be equalTo Some("hello")

      subject.onNext("world")

      observed.current must be equalTo Some("world")
    }
    //		"allow for nested for-comprehension" in {
    //			val events = Observable.asSeq(for (e1 <- observable; e2 <- observable) yield (e1, e2))
    //			events must have size 4
    //		}
  }

  "empty observables" should {
    "only publish onCompleted" in {
      Observable.empty.subscribe(observer)

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
  }

  "singleton observables" should {
    "only publish single event followed by onCompleted" in {
      Observable.singleton("event").subscribe(observer)

      there was one(observer).onNext("event") then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }
  }

  "take n" should {
    val observer: Observer[Int] = mock[Observer[Int]]
    val sequence = List(1, 2, 3, 4).toObservable

    "stop immediately when n is 0" in {
      sequence.take(0).subscribe(observer)

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }

    "stop return the first value when n = 1" in {
      sequence.take(1).subscribe(observer)

      there was one(observer).onNext(1) then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }

    "stop return the first three values when n = 3" in {
      sequence.take(3).subscribe(observer)

      there was one(observer).onNext(1) then one(observer).onNext(2) then one(observer).onNext(3) then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }

    "stop return the all values when n is larger than the observable length" in {
      sequence.take(12).subscribe(observer)

      there was one(observer).onNext(1) then one(observer).onNext(2) then one(observer).onNext(3) then one(observer).onNext(4) then one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }

    "stop producing values when the subscription is disposed" in {
      Scheduler.currentThread schedule {
        var subscription: Subscription = null

        subscription = sequence.observeOn(Scheduler.currentThread).take(3).perform { subscription.close() }.subscribe(observer)
      }

      there was one(observer).onNext(1)
      there were noMoreCallsTo(observer)
    }
  }

//  "repeat" should {
//    "repeat singleton until unsubscribed" in {
//      CurrentThreadScheduler.runOnCurrentThread {
//        Console.println("Running with repeat")
//        Observable.singleton("event", Scheduler.immediate).repeat.take(1).subscribe(observer)
//        Console.println("Done with repeat")
//      }
//      there was one(observer).onNext("event") then one(observer).onCompleted()
//      there were noMoreCallsTo(observer)
//    }
//  }

}
