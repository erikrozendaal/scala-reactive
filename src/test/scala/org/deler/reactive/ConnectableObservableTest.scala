package org.deler.reactive

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import org.specs.mock.Mockito
import org.joda.time.{Instant, Duration}

@RunWith(classOf[JUnitSuiteRunner])
class ConnectableObservableTest extends Specification with JUnit with Mockito {
  val scheduler = new TestScheduler

  "PublishConnectableObservable" should {
    "subscribe when connected and unsubscribe when closed" in {
      val source = scheduler.createHotObservable(Seq(
        250 -> OnNext("ignored"),
        400 -> OnNext("first"),
        500 -> OnNext("second"),
        700 -> OnNext("ignored")))
      val subject = source.publish(scheduler)
      var subscription = new MutableCloseable

      scheduler.scheduleAt(new Instant(300)) {subscription.set(subject.connect())}
      scheduler.scheduleAt(new Instant(600)) {subscription.close()}
      val notifications = scheduler.run(subject)

      source.subscriptions must be equalTo Seq(300 -> 600)
      notifications must be equalTo Seq(
        401 -> OnNext("first"),
        501 -> OnNext("second"))
    }

    "change underlying subscription when observers are unsubscribing" in {
      val source = scheduler.createHotObservable(Seq(
        250 -> OnNext("ignored"),
        400 -> OnNext("first"),
        500 -> OnNext("second")))
      val subject = source.publish(scheduler)
      val observer1 = new TestObserver[String](scheduler)
      val subscription = subject.subscribe(observer1)

      scheduler.scheduleAt(new Instant(300)) {subject.connect()}
      scheduler.scheduleAt(new Instant(450)) {subscription.close()}
      val notifications = scheduler.run(subject)

      source.subscriptions must be equalTo Seq(300 -> -1)
      notifications must be equalTo Seq(
        401 -> OnNext("first"),
        501 -> OnNext("second"))
      observer1.notifications must be equalTo Seq(
        401 -> OnNext("first"))
    }

    "support multiple observers" in {
      val source = scheduler.createHotObservable(Seq(
        250 -> OnNext("ignored"),
        400 -> OnNext("first"),
        500 -> OnNext("second"),
        700 -> OnNext("ignored")))
      val subject = source.publish(scheduler)
      val observer1 = new TestObserver[String](scheduler)
      val observer2 = new TestObserver[String](scheduler)
      subject.subscribe(observer1)
      subject.subscribe(observer2)

      var subscription = new MutableCloseable

      scheduler.scheduleAt(new Instant(300)) {subscription.set(subject.connect())}
      scheduler.scheduleAt(new Instant(600)) {subscription.close()}
      scheduler.run()

      source.subscriptions must be equalTo Seq(300 -> 600)
      observer1.notifications must be equalTo Seq(
        401 -> OnNext("first"),
        501 -> OnNext("second"))
      observer2.notifications must be equalTo Seq(
        401 -> OnNext("first"),
        501 -> OnNext("second"))
    }

    "close underlying subscription on completion" in {
      val source = scheduler.createHotObservable(Seq(
        400 -> OnNext("first"),
        500 -> OnCompleted))
      val subject = source.publish(scheduler)

      scheduler.scheduleAt(new Instant(300)) {subject.connect()}

      val notifications = scheduler.run(subject)

      source.subscriptions must be equalTo Seq(300 -> 500)
      notifications must be equalTo Seq(401 -> OnNext("first"), 501 -> OnCompleted)
    }

    "automatically connect and disconnect when using selector-based publish" in {
      val source = scheduler.createHotObservable(Seq(
        150 -> OnNext("ignored"),
        400 -> OnNext("first"),
        800 -> OnNext("second"),
        1200 -> OnNext("ignored"),
        1500 -> OnCompleted))

      val notifications = scheduler run {
        source.publish(published => Observable(published, published).merge, scheduler)
      }

      source.subscriptions must be equalTo Seq(200 -> 1000)
      notifications must be equalTo (Seq(
        401 -> OnNext("first"),
        401 -> OnNext("first"),
        801 -> OnNext("second"),
        801 -> OnNext("second")))
    }
  }
}
