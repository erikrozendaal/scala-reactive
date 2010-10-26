package org.deler.reactive

import org.junit.runner.RunWith
import org.specs._
import org.specs.mock.Mockito
import org.specs.runner.{JUnitSuiteRunner, JUnit}
import scala.collection._
import org.joda.time.{Duration, Instant}

@RunWith(classOf[JUnitSuiteRunner])
class ObservableTest extends Specification with JUnit with Mockito {
  import Observable.iterableToObservableWrapper


  val ex = new Exception("fail")

  val scheduler = new TestScheduler
  val observer = new TestObserver[String](scheduler)

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

  }

  "Observable.iterate" should {
    "generate a sequence value seperated by duration" in {
      val observer = new TestObserver[Int](Scheduler.currentThread)

      Observable.interval(new Duration(100)).take(3).subscribe(observer)

      observer.notifications.map(_._2) must be equalTo Seq(OnNext(0), OnNext(1), OnNext(2), OnCompleted)
    }
  }

  "Iterable.toObservable" should {

    "invoke onComplete when empty" in {
      Seq[String]().subscribe(observer, scheduler)

      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnCompleted)
    }
    "invoke onNext for each contained element followed by onComplete" in {
      Seq("first", "second").subscribe(observer, scheduler)

      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("first"), 2 -> OnNext("second"), 3 -> OnCompleted)
    }
    "stop producing values when the subscription is closed" in {
      val subscription = Seq("first", "second").subscribe(observer, scheduler)
      scheduler.scheduleAt(new Instant(2)) {subscription.close()}

      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("first"))
    }
  }

  "Observable" should {
    val observable = Observable.value("value")(scheduler)

    val failingObservable: Observable[Nothing] = Observable.createWithSubscription {
      observer =>
        observer.onError(ex)
        NullSubscription
    }

    "allow easy subscription using single onNext method" in {
      observable subscribe (onNext = observer onNext _)
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("value"))
    }

    "allow easy subscription using multiple methods" in {
      observable subscribe (onCompleted = () => observer.onCompleted(), onNext = observer.onNext(_))
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("value"), 2 -> OnCompleted)
    }

    "collect values" in {
      val observable = Seq(1, "string").toObservable

      val collected = observable collect {case x: String => x} toSeq

      collected must be equalTo Seq("string")
    }

    "allow filtering by type" in {
      val observable = Seq(1, "string").toObservable

      val filtered: Observable[String] = observable.ofType(classOf[String])

      filtered.toSeq must be equalTo Seq("string")
    }

    "allow observing using for-comprehension" in {
      (for (event <- observable) yield event).subscribe(observer)
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("value"), 2 -> OnCompleted)
    }

    "allow filtering using for-comprehension" in {
      val observable = Seq("first", "second").toObservable(scheduler)

      (for (event <- observable if event == "first") yield event).subscribe(observer)
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("first"), 3 -> OnCompleted)
    }

    "allow for nested for-comprehension" in {
      val observable1 = Seq("a", "b").toObservable(scheduler)
      val observable2 = Seq("c", "d").toObservable(scheduler)

      (for (e1 <- observable1; e2 <- observable2) yield e1 + e2).subscribe(observer)
      scheduler.run()

      observer.notifications must be equalTo Seq(
        // 1 -> observable1 produces onNext("a") and schedules onNext("b")
        // 1 -> first observable2 schedules onNext("c")
        // 2 -> observable1 produces onNext("b") and schedules onCompleted
        // 2 -> first observable2 produces OnNext("c") and schedules onNext("d")
        2 -> OnNext("ac"),
        // 2 -> second observable2 schedules onNext("c")
        // 3 -> observable1 produces onCompleted
        // 3 -> first observable2 produces onNext("d") and schedules onCompleted
        3 -> OnNext("ad"),
        // 3 -> second observable2 produces onNext("c") and schedules onNext("d")
        3 -> OnNext("bc"),
        // 4 -> first observable2 produces onCompleted
        // 4 -> second observable2 produces onNext("d") and schedules onCompleted
        4 -> OnNext("bd"),
        // 5 -> second observable2 produces onCompleted
        5 -> OnCompleted)
    }

    "stop nested for-comprehension on unsubscribe" in {
      val observable1 = Seq("a", "b").toObservable(scheduler)
      val observable2 = Seq("c", "d").toObservable(scheduler)

      val subscription = (for (e1 <- observable1; e2 <- observable2) yield e1 + e2).subscribe(observer)
      scheduler.scheduleAt(new Instant(4)) {subscription.close()}
      scheduler.run()

      observer.notifications must be equalTo Seq(
        2 -> OnNext("ac"),
        3 -> OnNext("ad"),
        3 -> OnNext("bc"))
    }

    "stop flatten on unsubscribe" in {
      val observable = Seq(Seq("a", "b").toObservable(scheduler), Seq("c", "d").toObservable(scheduler)).toObservable(scheduler)

      val subscription = observable.flatten.subscribe(observer)
      scheduler.scheduleAt(new Instant(4)) {subscription.close()}
      scheduler.run()

      observer.notifications must be equalTo Seq(
        2 -> OnNext("a"),
        3 -> OnNext("b"),
        3 -> OnNext("c"))
    }

    "pass values from flattened observables in the order produced" in {
      val observable1 = Seq("a", "b").toObservable(scheduler)
      val observable2 = Seq("c", "d", "e").toObservable(scheduler)

      val subscription = (for (e1 <- observable1; e2 <- observable2) yield e1 + e2).subscribe(observer)
      scheduler.run()

      observer.notifications must be equalTo Seq(
        2 -> OnNext("ac"),
        3 -> OnNext("ad"),
        3 -> OnNext("bc"),
        4 -> OnNext("ae"),
        4 -> OnNext("bd"),
        5 -> OnNext("be"),
        6 -> OnCompleted)
    }

  }


  class TestObserver[T](scheduler: Scheduler) extends Observer[T] {
    private var _notifications = immutable.Queue[(Int, Notification[T])]()

    def notifications = _notifications.toSeq

    override def onCompleted() {
      _notifications = _notifications enqueue (scheduler.now.getMillis.toInt, OnCompleted)
    }

    override def onError(error: Exception) {
      _notifications = _notifications enqueue (scheduler.now.getMillis.toInt, OnError(error))
    }

    override def onNext(value: T) {
      _notifications = _notifications enqueue (scheduler.now.getMillis.toInt, OnNext(value))
    }

  }

  "empty observables" should {
    val subscription = Observable.empty(scheduler).subscribe(observer)

    "only publish onCompleted" in {
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnCompleted)
    }

    "not publish anything when subscription is closed" in {
      subscription.close()

      scheduler.run()

      observer.notifications must beEmpty
    }

  }

  "singleton observables" should {
    val scheduler = new TestScheduler
    val observer = new TestObserver[String](scheduler)
    val subscription = Observable.value("event")(scheduler).subscribe(observer)

    "only publish single event followed by onCompleted" in {
      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("event"), (2 -> OnCompleted))
    }

    "stop publishing when subscription is closed" in {
      scheduler.scheduleAt(new Instant(2)) {
        subscription.close()
      }

      scheduler.run()

      observer.notifications must be equalTo Seq(1 -> OnNext("event"))
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

        subscription = sequence.take(3).perform(_ => subscription.close()).subscribe(observer)
      }

      there was one(observer).onNext(1)
      there were noMoreCallsTo(observer)
    }

    "have independent subscribers" in {
      val observer1 = mock[Observer[Int]]
      val observer2 = mock[Observer[Int]]
      val subject = new Subject[Int]
      val take = subject.take(100)
      take.subscribe(observer1)
      take.subscribe(observer2)

      subject.onNext(1)

      there was one(observer1).onNext(1)
      there was one(observer2).onNext(1)
      there were noMoreCallsTo(observer1)
      there were noMoreCallsTo(observer2)
    }
  }

  "concatenated observables" should {
    val first = new Subject[String]
    val second = new Subject[String]
    val result = new ReplaySubject[Notification[Any]]
    val subscription = (first ++ second).materialize.subscribe(result)

    "first provide the values from the first observable followed by the second observable" in {
      first.onNext("first")
      first.onNext("second")
      first.onCompleted()
      second.onNext("third")
      second.onCompleted()

      result.toSeq must be equalTo Seq(OnNext("first"), OnNext("second"), OnNext("third"), OnCompleted)
    }

    "should only subscribe to second observable after first has completed" in {
      first.onNext("first")
      second.onNext("ignored")
      first.onCompleted()
      second.onNext("second")
      second.onCompleted()

      result.toSeq must be equalTo Seq(OnNext("first"), OnNext("second"), OnCompleted)
    }

    "terminate on error in first observable" in {
      first.onError(ex)
      second.onNext("unseen")

      result.toSeq must be equalTo Seq(OnError(ex))
    }

    "terminate when subscription is closed in first sequence" in {
      subscription.close()
      first.onNext("first")
      first.onCompleted()
      second.onNext("second")

      result.toSeq must beEmpty
    }

    "terminate when subscription is closed in second sequence" in {
      first.onNext("first")
      first.onCompleted()
      subscription.close()
      second.onNext("second")

      result.toSeq must be equalTo Seq(OnNext("first"))
    }
  }

  "materialized observables" should {
    val observable = new ReplaySubject[String]
    "provide each value in the stream as an instance of Notification" in {
      observable.onNext("hello")
      observable.onCompleted()

      val result = observable.materialize.toSeq

      result must be equalTo Seq(OnNext("hello"), OnCompleted)
    }

    "provide the error stream as an instance of OnError" in {
      observable.onError(ex)

      val result = observable.materialize.toSeq

      result must be equalTo Seq(OnError(ex))
    }
  }

  "dematerialized observables" should {
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

}
