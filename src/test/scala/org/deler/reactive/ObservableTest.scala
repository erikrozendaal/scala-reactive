package org.deler.reactive

import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{JUnitSuiteRunner, JUnit}
import scala.collection._
import org.joda.time.{Duration, Instant}
import org.scalacheck.{Gen, Arbitrary, Prop}

@RunWith(classOf[JUnitSuiteRunner])
class ObservableTest extends Specification with JUnit with Mockito with ScalaCheck {
  import Observable.iterableToObservableWrapper


  val ex = new Exception("fail")

  val scheduler = new TestScheduler
  val observer = new TestObserver[String](scheduler)

  /**
   * Generate arbitrary sequences for use with ScalaCheck.
   */
  implicit def arbitraryObservable[T](implicit a: Arbitrary[List[T]]): Arbitrary[Observable[T]] = Arbitrary {
    for (s <- Arbitrary.arbitrary[List[T]]) yield s.toObservable
  }

  "Observable.create" should {
    "invoke delegate on subscription with the observer as argument" in {
      var delegateCalled = false
      val observable: Observable[String] = Observable.create {
        observer =>
          delegateCalled = true
          observer.onNext("delegate")
          Observable.noop
      }

      observable.subscribe(observer)

      delegateCalled must be equalTo true
      observer.notifications must be equalTo Seq(0 -> OnNext("delegate"))
    }

    "invoke delegate's result when subscription is closed" in {
      var actionCalled = false
      val observable = Observable.create {observer: Observer[String] => () => actionCalled = true}
      val subscription = observable.subscribe(observer)

      subscription.close()

      actionCalled must be equalTo true
    }

    "enforce observable contract (no onNext after onCompleted)" in {
      val notifications = scheduler run {
        Observable.create {
          observer: Observer[String] =>
            observer.onCompleted()
            observer.onNext("ignored")
            () => {}
        }
      }

      notifications must be equalTo Seq(200 -> OnCompleted)
    }

    "enforce observable contract (no onNext after onError)" in {
      val notifications = scheduler run {
        Observable.create {
          observer: Observer[String] =>
            observer.onError(ex)
            observer.onNext("ignored")
            () => {}
        }
      }

      notifications must be equalTo Seq(200 -> OnError(ex))
    }

    "close subscription after onCompleted" in {
      var closed = false
      Observable.create {
        observer: Observer[String] =>
          observer.onCompleted()
          () => {closed = true}
      }.subscribe(observer)

      closed must beTrue
    }

  }

  "Observable.interval" should {
    "generate a sequence value seperated by duration" in {
      val notifications = scheduler.run {Observable.interval(new Duration(300))(scheduler)}

      notifications must be equalTo Seq(500 -> OnNext(0), 800 -> OnNext(1))
    }
  }

  "Iterable.toObservable" should {
    implicit val defaultToTestScheduler = scheduler

    "invoke onComplete when empty" in {
      val notifications = scheduler.run(Seq().toObservable)

      notifications must be equalTo Seq(201 -> OnCompleted)
    }
    "invoke onNext for each contained element followed by onComplete" in {
      val notifications = scheduler.run(Seq("first", "second").toObservable)

      notifications must be equalTo Seq(201 -> OnNext("first"), 202 -> OnNext("second"), 203 -> OnCompleted)
    }
    "stop producing values when the subscription is closed" in {
      val notifications = scheduler.run(Seq("first", "second").toObservable, unsubscribeAt = new Instant(202))

      notifications must be equalTo Seq(201 -> OnNext("first"))
    }
  }

  "Observable" should {
    implicit val defaultToTestScheduler = scheduler

    "allow easy subscription using single onNext method" in {
      Observable("value") subscribe (onNext = observer onNext _)
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("value"))
    }

    "allow easy subscription using multiple methods" in {
      Observable("value") subscribe (onCompleted = () => observer.onCompleted(), onNext = observer.onNext(_))
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("value"), 2 -> OnCompleted)
    }

    "allow easy subscription using the onError method" in {
      Observable.raise(ex) subscribe (onNext = observer.onNext(_), onError = observer.onError(_))
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnError(ex))
    }

    "collect values" in {
      val observable = Observable(1, "string")

      val notifications = scheduler run {
        observable.collect {case x: String => x.reverse}
      }

      notifications must be equalTo Seq(202 -> OnNext("gnirts"), 203 -> OnCompleted)
    }

    "allow filtering by type" in {
      val observable: Observable[Any] = Observable(1, "string")

      val notifications: Seq[(Int, Notification[String])] = scheduler run {
        observable.ofType(classOf[String])
      }

      notifications must be equalTo Seq(202 -> OnNext("string"), 203 -> OnCompleted)
    }

    "allow observing using for-comprehension" in {
      val observable = Observable("value")

      val notifications = scheduler run {
        for (v <- observable) yield v.reverse
      }

      notifications must be equalTo Seq(201 -> OnNext("eulav"), 202 -> OnCompleted)
    }

    "handle error on exception in map function" in {
      val notifications = scheduler run {Observable("value").map(_ => throw ex)}

      notifications must be equalTo Seq(201 -> OnError(ex))
    }

    "allow filtering using for-comprehension" in {
      val observable = Observable("first", "second")

      val notifications = scheduler run {
        for (v <- observable if v == "first") yield v.reverse
      }

      notifications must be equalTo Seq(201 -> OnNext("tsrif"), 203 -> OnCompleted)
    }

    "handle error on exception in filter predicate" in {
      val notifications = scheduler run {Observable("value").filter(_ => throw ex)}

      notifications must be equalTo Seq(201 -> OnError(ex))
    }

    "allow for nested for-comprehension" in {
      val observable1 = Observable("a", "b")
      val observable2 = Observable("c", "d")

      val notifications = scheduler run {
        for (e1 <- observable1; e2 <- observable2) yield e1 + e2
      }

      notifications must be equalTo Seq(
        // 201 -> observable1 produces onNext("a") and schedules onNext("b")
        // 201 -> first observable2 schedules onNext("c")
        // 202 -> observable1 produces onNext("b") and schedules onCompleted
        // 202 -> first observable2 produces OnNext("c") and schedules onNext("d")
        202 -> OnNext("ac"),
        // 202 -> second observable2 schedules onNext("c")
        // 203 -> observable1 produces onCompleted
        // 203 -> first observable2 produces onNext("d") and schedules onCompleted
        203 -> OnNext("ad"),
        // 203 -> second observable2 produces onNext("c") and schedules onNext("d")
        203 -> OnNext("bc"),
        // 204 -> first observable2 produces onCompleted
        // 204 -> second observable2 produces onNext("d") and schedules onCompleted
        204 -> OnNext("bd"),
        // 205 -> second observable2 produces onCompleted
        205 -> OnCompleted)
    }

    "stop nested for-comprehension on unsubscribe" in {
      val observable1 = Observable("a", "b")
      val observable2 = Observable("c", "d")

      val notifications = scheduler.run({
        for (e1 <- observable1; e2 <- observable2) yield e1 + e2
      }, unsubscribeAt = new Instant(204))

      notifications must be equalTo Seq(
        202 -> OnNext("ac"),
        203 -> OnNext("ad"),
        203 -> OnNext("bc"))
    }

    "stop flatten on unsubscribe" in {
      val observables = Observable(Observable("a", "b"), Observable("c", "d"))

      val notifications = scheduler.run({observables.flatten}, unsubscribeAt = new Instant(204))

      notifications must be equalTo Seq(
        202 -> OnNext("a"),
        203 -> OnNext("b"),
        203 -> OnNext("c"))
    }

    "pass values from flattened observables in the order produced" in {
      val observable1 = Observable("a", "b")
      val observable2 = Observable("c", "d", "e")

      val notifications = scheduler run {
        for (e1 <- observable1; e2 <- observable2) yield e1 + e2
      }

      notifications must be equalTo Seq(
        202 -> OnNext("ac"),
        203 -> OnNext("ad"),
        203 -> OnNext("bc"),
        204 -> OnNext("ae"),
        204 -> OnNext("bd"),
        205 -> OnNext("be"),
        206 -> OnCompleted)
    }

  }


  "Observable.toSeq" should {
    "return all values until observable is completed" in {
      val seq = Observable.interval(new Duration(10)).take(5).toSeq

      seq must be equalTo (0 to 4)
    }
  }

  "empty observables" should {
    implicit val defaultToTestScheduler = scheduler

    "only publish onCompleted" in {
      val notifications = scheduler.run(Observable.empty)

      notifications must be equalTo Seq(201 -> OnCompleted)
    }

    "not publish anything when subscription is closed" in {
      val notifications = scheduler.run(Observable.empty, unsubscribeAt = new Instant(201))

      notifications must beEmpty
    }

  }

  "singleton observables" should {
    implicit val defaultToTestScheduler = scheduler

    "only publish single event followed by onCompleted" in {
      val notifications = scheduler.run(Observable.value("value"))

      notifications must be equalTo Seq(201 -> OnNext("value"), (202 -> OnCompleted))
    }

    "stop publishing when subscription is closed" in {
      val notifications = scheduler.run(Observable.value("value"), unsubscribeAt = new Instant(202))

      notifications must be equalTo Seq(201 -> OnNext("value"))
    }

  }

  "take n" should {
    implicit val defaultToTestScheduler = scheduler

    val sequence = Observable(1, 2, 3, 4)

    "stop immediately when n is 0" in {
      val notifications = scheduler.run(sequence.take(0))

      notifications must be equalTo Seq(201 -> OnCompleted)
    }

    "return only the first value when n = 1" in {
      val notifications = scheduler.run(sequence.take(1))

      notifications must be equalTo Seq(201 -> OnNext(1), 201 -> OnCompleted)
    }

    "return only the first three values when n = 3" in {
      val notifications = scheduler.run(sequence.take(3))

      notifications must be equalTo Seq(201 -> OnNext(1), 202 -> OnNext(2), 203 -> OnNext(3), 203 -> OnCompleted)
    }

    "stop return the all values when n is larger than the observable length" in {
      val notifications = scheduler.run(sequence.take(99))

      notifications must be equalTo Seq(
        201 -> OnNext(1), 202 -> OnNext(2), 203 -> OnNext(3), 204 -> OnNext(4), 205 -> OnCompleted)
    }

    "stop producing values when the subscription is disposed" in {
      val notifications = scheduler.run(sequence.take(99), unsubscribeAt = new Instant(203))

      notifications must be equalTo Seq(201 -> OnNext(1), 202 -> OnNext(2))
    }

    "have independent subscribers" in {
      val taken = sequence.take(1)

      val ns1 = scheduler.run(taken)
      val ns2 = scheduler.run(taken, unsubscribeAt = new Instant(1300))

      ns1 must be equalTo Seq(201 -> OnNext(1), 201 -> OnCompleted)
      ns2 must be equalTo Seq(1002 -> OnNext(1), 1002 -> OnCompleted)
    }
  }

  "concatenated observables" should {

    "be equal to first and second concatenated" in {
      Prop.forAll {(o1: Observable[Int], o2: Observable[Int]) => (o1 ++ o2).toSeq == (o1.toSeq ++ o2.toSeq)} must pass
    }

    "should subscribe to second when first completes" in {
      val first = scheduler.createHotObservable(Seq(
        300 -> OnNext("first"),
        400 -> OnCompleted))
      val second = scheduler.createHotObservable(Seq(
        399 -> OnNext("early"),
        400 -> OnNext("second"),
        500 -> OnCompleted))

      val notifications = scheduler run {first ++ second}

      notifications must be equalTo Seq(
        300 -> OnNext("first"),
        400 -> OnNext("second"),
        500 -> OnCompleted)

      first.subscriptions must be equalTo Seq(200 -> 400)
      second.subscriptions must be equalTo Seq(400 -> 500)
    }

    "not subscribe to second when first terminates with an error" in {
      val first = scheduler.createHotObservable(Seq(
        300 -> OnNext("first"),
        400 -> OnError(ex)))
      val second = scheduler.createHotObservable(Seq(
        400 -> OnNext("ignored"),
        500 -> OnCompleted))

      val notifications = scheduler run {first ++ second}

      notifications must be equalTo Seq(
        300 -> OnNext("first"),
        400 -> OnError(ex))

      first.subscriptions must be equalTo Seq(200 -> 400)
      second.subscriptions must beEmpty
    }

    "terminate when subscription is closed while observing first sequence" in {
      val first = scheduler.createHotObservable(Seq(
        300 -> OnNext("first"),
        400 -> OnCompleted))
      val second = scheduler.createHotObservable(Seq(
        400 -> OnNext("ignored"),
        500 -> OnCompleted))

      val notifications = scheduler.run({first ++ second}, unsubscribeAt = new Instant(350))

      notifications must be equalTo Seq(
        300 -> OnNext("first"))

      first.subscriptions must be equalTo Seq(200 -> 350)
      second.subscriptions must beEmpty
    }

    "terminate when subscription is closed while obesrving second sequence" in {
      val first = scheduler.createHotObservable(Seq(
        300 -> OnNext("first"),
        400 -> OnCompleted))
      val second = scheduler.createHotObservable(Seq(
        400 -> OnNext("second"),
        500 -> OnCompleted))

      val notifications = scheduler.run({first ++ second}, unsubscribeAt = new Instant(450))

      notifications must be equalTo Seq(
        300 -> OnNext("first"),
        400 -> OnNext("second"))

      first.subscriptions must be equalTo Seq(200 -> 400)
      second.subscriptions must be equalTo Seq(400 -> 450)
    }
  }

  "repeated observables" should {
    implicit val defaultToTestScheduler = scheduler

    "not publish anything when source is empty" in {
      val notifications = scheduler.run {Observable.empty.repeat}

      notifications must beEmpty
    }

    "republish single value" in {
      val notifications = scheduler.run {Observable.value("value").repeat.take(3)}

      notifications must be equalTo Seq(202 -> OnNext("value"), 205 -> OnNext("value"), 208 -> OnNext("value"), 208 -> OnCompleted)
    }

    "repeat source observable" in {
      val notifications = scheduler.run {Observable(1, 2, 3).repeat.take(7)}

      notifications must be equalTo Seq(
        202 -> OnNext(1), 203 -> OnNext(2), 204 -> OnNext(3),
        207 -> OnNext(1), 208 -> OnNext(2), 209 -> OnNext(3),
        212 -> OnNext(1), 212 -> OnCompleted)
    }

    "repeat zero" in {
      val notifications = scheduler.run {Observable.value("value").repeatN(0)}

      notifications must be equalTo Seq(201 -> OnCompleted)
    }

    "repeat once" in {
      val notifications = scheduler.run {Observable.value("value").repeatN(1)}

      notifications must be equalTo Seq(202 -> OnNext("value"), 204 -> OnCompleted)
    }

    "repeat twice" in {
      val notifications = scheduler.run {Observable.value("value").repeatN(2)}

      notifications must be equalTo Seq(202 -> OnNext("value"), 205 -> OnNext("value"), 207 -> OnCompleted)
    }
  }

  "Observable.materialize" should {
    val observable = new ReplaySubject[String]
    val observer = mock[Observer[Notification[String]]]

    "provide each value in the stream as an instance of Notification" in {
      observable.onNext("hello")
      observable.onCompleted()

      val result = observable.materialize.subscribe(observer)

      there was one(observer).onNext(OnNext("hello")) then one(observer).onNext(OnCompleted)
    }

    "provide the error stream as an instance of OnError" in {
      observable.onError(ex)

      val result = observable.materialize.subscribe(observer)

      there was one(observer).onNext(OnError(ex))
    }
  }

  "Observable.dematerialize" should {
    val observable = new Subject[Notification[String]]
    val observer = mock[Observer[String]]

    "map each OnNext value to Observer.onNext" in {
      observable.dematerialize.subscribe(observer)

      observable.onNext(OnNext("first"))
      observable.onNext(OnNext("second"))

      there was one(observer).onNext("first") then one(observer).onNext("second")
      there were noMoreCallsTo(observer)
    }

    "map OnCompleted to Observer.onCompleted" in {
      observable.dematerialize.subscribe(observer)

      observable.onNext(OnCompleted)

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }

    "map OnError to Observer.onError" in {
      observable.dematerialize.subscribe(observer)

      observable.onNext(OnError(ex))

      there was one(observer).onError(ex)
      there were noMoreCallsTo(observer)
    }

    "stop once OnCompleted has been processed" in {
      observable.dematerialize.subscribe(observer)

      observable.onNext(OnCompleted)
      observable.onNext(OnNext("ignored"))

      there was one(observer).onCompleted()
      there were noMoreCallsTo(observer)
    }

    "stop once OnError has been processed" in {
      observable.dematerialize.subscribe(observer)

      observable.onNext(OnError(ex))
      observable.onNext(OnNext("ignored"))

      there was one(observer).onError(ex)
      there were noMoreCallsTo(observer)
    }
  }

  "Observable exception handling" should {
    implicit val defaultToTestScheduler = scheduler

    "raise an error" in {
      val notifications = scheduler.run(Observable.raise(ex))

      notifications must be equalTo Seq(201 -> OnError(ex))
    }

    "switch to next rescue source on error" in {
      val errorSource = Observable.raise(ex)
      val rescueSource = Observable.value("rescue")

      val notifications = scheduler.run(errorSource.rescue(rescueSource))

      notifications must be equalTo Seq(202 -> OnNext("rescue"), 203 -> OnCompleted)
    }
  }

  "scheduled subscriptions" should {
    implicit val defaultToTestScheduler = scheduler

    "use specified scheduler" in {
      val observable = scheduler.createHotObservable(Seq())

      scheduler.run {observable.subscribeOn(scheduler)}

      observable.subscriptions must be equalTo Seq(201 -> 1001)
    }

    "immediately schedule unsubscribe when closed" in {
      val observable = scheduler.createHotObservable(Seq())

      scheduler.run(observable.subscribeOn(scheduler), unsubscribeAt = new Instant(150))

      observable.subscriptions must be equalTo Seq(201 -> 201)
    }
  }

}
