package org.deler.reactive

import scala.collection._

class Subject[A](scheduler: Scheduler = Scheduler.immediate) extends Observable[A] with Observer[A] {
  private var subscriptions = Set[SubjectSubscription]()
  protected var completed = false

  def subscribe(observer: Observer[A]): Subscription = {
    new SubjectSubscription(observer)
  }

  override def onCompleted() {
    if (completed) return
    completed = true
    dispatch(_.onCompleted())
  }

  override def onNext(value: A) {
    if (completed) return
    dispatch(_.onNext(value))
  }

  override def onError(error: Exception) {
    if (completed) return
    completed = true
    dispatch(_.onError(error))
  }

  private def dispatch(f: Observer[A] => Unit) {
    subscriptions foreach {subscription => scheduler schedule f(subscription.observer)}
  }

  private class SubjectSubscription(val observer: Observer[A]) extends Subscription {
    subscriptions += this

    def close {
      subscriptions -= this
    }
  }

}

class ReplaySubject[A](scheduler: Scheduler = Scheduler.currentThread) extends Subject[A](scheduler) {
  private var values = immutable.Queue[Notification[A]]()

  override def subscribe(observer: Observer[A]): Subscription = CurrentThreadScheduler runImmediate {
    val result = new FutureSubscription
    val it = values.iterator
    result.set(scheduler scheduleRecursive {
      self =>
        if (it.hasNext) {
          it.next.accept(observer)
          self()
        } else {
          result.set(super.subscribe(observer))
        }
    })
    result
  }

  override def onNext(value: A) {
    if (!completed) {
      values = values enqueue OnNext(value)
    }
    super.onNext(value)
  }

  override def onCompleted() {
    if (!completed) {
      values = values enqueue OnCompleted
    }
    super.onCompleted()
  }

  override def onError(error: Exception) {
    if (!completed) {
      values = values enqueue OnError(error)
    }
    super.onError(error)
  }
}
