package org.deler.reactive

import scala.collection._

/**
 * An object that is both an observer and an observable sequence.
 */
trait Subject[A] extends Observable[A] with Observer[A]

/**
 * Dispatches notifications to all subscribers.
 */
abstract class Dispatcher[A] extends Subject[A] {
  @volatile private var subscriptions = Set[SubjectSubscription]()

  def subscribe(observer: Observer[A]): Closeable = {
    new SubjectSubscription(observer)
  }

  override def onNext(value: A) {
    dispatch(_.onNext(value))
  }

  override def onError(error: Exception) {
    dispatch(_.onError(error))
    subscriptions = Set.empty
  }

  override def onCompleted() {
    dispatch(_.onCompleted())
    subscriptions = Set.empty
  }

  private def dispatch(f: Observer[A] => Unit) {
    subscriptions foreach {subscription => f(subscription.observer)}
  }

  private class SubjectSubscription(val observer: Observer[A]) extends Closeable {
    Dispatcher.this synchronized {
      subscriptions += this
    }

    def close {
      Dispatcher.this synchronized {
        subscriptions -= this
      }
    }
  }

}

/**
 * Dispatches notifications to all subscribers using `scheduler`.
 */
abstract class ScheduledDispatcher[A](scheduler: Scheduler = Scheduler.immediate) extends Dispatcher[A] {
  override def subscribe(observer: Observer[A]): Closeable = CurrentThreadScheduler runImmediate {
    super.subscribe(new ScheduledObserver(observer, scheduler))
  }
}

/**
 * Records all received notifications and provides support for replaying these notifications to an
 * [[org.deler.reactive.Observer]].
 */
trait Recorder[A] extends Observer[A] {
  private var notifications = immutable.Queue[Notification[A]]()

  protected def replay(observer: Observer[A]) {
    notifications foreach {_.accept(observer)}
  }

  abstract override def onNext(value: A) {
    notifications = notifications enqueue OnNext(value)
    super.onNext(value)
  }

  abstract override def onCompleted() {
    notifications = notifications enqueue OnCompleted
    super.onCompleted()
  }

  abstract override def onError(error: Exception) {
    notifications = notifications enqueue OnError(error)
    super.onError(error)
  }

}

/**
 * An object that is both an observer and an observable sequence. This subject publishes all observed notifications to
 * all of its subscribers.
 */
class PublishSubject[A](scheduler: Scheduler = Scheduler.immediate)
        extends ScheduledDispatcher[A]
                with SynchronizedObserver[A]

/**
 * An object that is both an observer and an observable sequence and replays all past notifications to new subscribers.
 */
class ReplaySubject[A](scheduler: Scheduler = Scheduler.currentThread)
        extends Dispatcher[A]
                with Recorder[A]
                with SynchronizedObserver[A] {
  override def subscribe(observer: Observer[A]): Closeable = CurrentThreadScheduler runImmediate {
    synchronized {
      val wrapped = new ScheduledObserver(observer, scheduler)
      replay(wrapped)
      super.subscribe(wrapped)
    }
  }
}
