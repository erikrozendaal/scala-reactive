package org.deler.reactive

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.LinkedBlockingQueue

/**
 * Observers can subscribe to [[org.deler.reactive.Observable]]s and will be notified of zero or more values, optionally
 * followed by an completion or error.
 */
trait Observer[-A] {
  /**
   * Invoked for every value produced by an `Observable`
   */
  def onNext(value: A)

  /**
   * Invoked when an error is produced by an `Observable`. No further notifications will happen.
   */
  def onError(error: Exception) {
    throw error
  }

  /**
   * Invoked when an `Observable` completes. No further notifications will happen.
   */
  def onCompleted() {
  }
}

/**
 * An observer that just delegates to `delegate`. Used to mix in additional behavior on existing observers.
 */
class DelegateObserver[-A](delegate: Observer[A]) extends Observer[A] {
  override def onNext(value: A) = delegate.onNext(value)

  override def onError(error: Exception) = delegate.onError(error)

  override def onCompleted() = delegate.onCompleted()
}

/**
 * An observer that schedules all notifications to `delegate` on `scheduler`. Uses an internal queue to avoid
 * slowing down the source observable sequence.
 *
 * This SchedulingObserver is thread-safe.
 */
class SchedulingObserver[A](delegate: Observer[A], scheduler: Scheduler) extends DelegateObserver(delegate) {
  val queue = new LinkedBlockingQueue[Notification[A]]()
  val size = new AtomicInteger(0)

  override def onNext(value: A) = scheduleNotification(OnNext(value))

  override def onError(error: Exception) = scheduleNotification(OnError(error))

  override def onCompleted() = scheduleNotification(OnCompleted)

  private def scheduleNotification(notification: Notification[A]) {
    queue.put(notification)
    if (size.getAndIncrement() == 0) {
      startDeliveringNotifications()
    }
  }

  private def startDeliveringNotifications() {
    scheduler scheduleRecursive {
      reschedule =>
        queue.remove() match {
          case OnCompleted =>
            super.onCompleted()
          case OnError(error) =>
            super.onError(error)
          case OnNext(value) =>
            super.onNext(value)
            if (size.decrementAndGet() > 0) {
              reschedule()
            }
        }
    }
  }

}

/**
 * Mix-in trait that ensures every notification is synchronized.
 */
trait SynchronizedObserver[-A] extends Observer[A] {
  abstract override def onNext(value: A) = synchronized {
    super.onNext(value)
  }

  abstract override def onError(error: Exception) = synchronized {
    super.onError(error)
  }

  abstract override def onCompleted() = synchronized {
    super.onCompleted()
  }
}

/**
 * Mix-in trait that ensures the observer receives notifications conforming to the Observable protocol.
 *
 * This trait takes care that no more notifications are send after either `onError` or `onCompleted` occurred.
 *
 * The `close` method can be overridden to close the subscription to the underlying source when `onCompleted` or
 * `onError` is received.
 */
trait ConformingObserver[-A] extends Observer[A] {
  private var completed = false

  /**
   * Invoked when the first `onComplete` or `onError` notification is received. Used to close the subscription to the
   * underlying source.
   */
  protected def close() {}

  abstract override def onCompleted() {
    if (completed) return
    completed = true
    close()
    super.onCompleted()
  }

  abstract override def onError(error: Exception) {
    if (completed) return
    completed = true
    close()
    super.onError(error);
  }

  abstract override def onNext(value: A) {
    if (completed) return
    super.onNext(value)
  }

}
