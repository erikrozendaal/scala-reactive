package org.deler.reactive

import scala.collection._

trait Subject[A] extends Observable[A] with Observer[A]

trait Dispatcher[A] extends Subject[A] {
  private val subscriptions = mutable.Set[SubjectSubscription]()

  def subscribe(observer: Observer[A]): Subscription = {
    new SubjectSubscription(observer)
  }

  override def onNext(value: A) {
    dispatch(_.onNext(value))
  }

  override def onError(error: Exception) {
    dispatch(_.onError(error))
    subscriptions.clear()
  }

  override def onCompleted() {
    dispatch(_.onCompleted())
    subscriptions.clear()
  }

  private def dispatch(f: Observer[A] => Unit) {
    subscriptions foreach {subscription => f(subscription.observer)}
  }

  private class SubjectSubscription(val observer: Observer[A]) extends Subscription {
    subscriptions += this

    def close {
      subscriptions -= this
    }
  }

}

trait Recorder[A] extends Observer[A] {
  private var values = immutable.Queue[Notification[A]]()

  protected def replay(observer: Observer[A]) {
    values foreach {_.accept(observer)}
  }

  abstract override def onNext(value: A) {
    values = values enqueue OnNext(value)
    super.onNext(value)
  }

  abstract override def onCompleted() {
    values = values enqueue OnCompleted
    super.onCompleted()
  }

  abstract override def onError(error: Exception) {
    values = values enqueue OnError(error)
    super.onError(error)
  }

}

class BasicSubject[A](scheduler: Scheduler = Scheduler.immediate)
        extends Dispatcher[A]
                with ConformingObserver[A]
                with SynchronizedObserver[A] {
  override def subscribe(observer: Observer[A]): Subscription = {
    super.subscribe(new SchedulingObserver(observer, scheduler))
  }
}

class ReplaySubject[A](scheduler: Scheduler = Scheduler.currentThread)
        extends Dispatcher[A]
                with Recorder[A]
                with ConformingObserver[A]
                with SynchronizedObserver[A] {
  override def subscribe(observer: Observer[A]): Subscription = CurrentThreadScheduler runImmediate {
    val wrapped = new SchedulingObserver(observer, scheduler)
    replay(wrapped)
    super.subscribe(wrapped)
  }

}
