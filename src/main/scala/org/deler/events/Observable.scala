package org.deler.events

import scala.collection._

trait Subscription {
  def close()
}

object NoopSubscription extends Subscription {
  def close() = {}
}

class FutureSubscription extends Subscription {
  private var _subscription: Option[Subscription] = None
  private var _closed = false

  def set(subscription: Subscription) {
    if (_closed) {
      subscription.close()
    } else {
      _subscription = Some(subscription)
    }
  }

  def close() {
    _closed = true
    _subscription foreach {_.close()}
  }
}

trait Observable[+E] extends ObservableLike[E] {
  self =>
  import Observable._

  def collect[F](pf: PartialFunction[E, F]): Observable[F] = {
    for (event <- this if pf.isDefinedAt(event)) yield pf(event)
  }

  def filter(p: E => Boolean): Observable[E] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[E] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = observer.onError(error)

        override def onNext(event: E) = if (p(event)) observer.onNext(event)
      })
  }

  def map[F](f: E => F): Observable[F] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[E] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = observer.onError(error)

        override def onNext(event: E) = observer.onNext(f(event))
      })
  }

  def ofType[F](clazz: Class[F]): Observable[F] = {
    for (value <- this if clazz.isInstance(value)) yield clazz.cast(value)
  }

  def withFilter(p: E => Boolean) = filter(p)

  def observeOn(scheduler: Scheduler): Observable[E] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[E] {
        override def onCompleted() = scheduler schedule {
          observer.onCompleted()
        }

        override def onError(error: Exception) = scheduler schedule {
          observer.onError(error)
        }

        override def onNext(event: E) = scheduler schedule {
          observer.onNext(event)
        }
      })
  }

  def perform(action: => Unit): Observable[E] = perform(_ => action)

  def perform(action: E => Unit): Observable[E] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[E] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = {
          observer.onError(error)
        }

        override def onNext(event: E) = {
          action(event);
          observer.onNext(event)
        }
      })
  }

  def take(n: Int): Observable[E] = createWithSubscription {
    observer =>
      self.subscribe(new Relay(observer) {
        var count: Int = 0

        override def onNext(event: E) = {
          if (count < n) {
            super.onNext(event)
            count += 1
          }
          if (count >= n) {
            super.onCompleted()
          }
        }
      })
  }

}

object Observable {
  val noopSubscription = new Subscription {
    def close() {}
  }

  def noop() {}

  def create[T](delegate: Observer[T] => () => Unit): Observable[T] = new Observable[T] {
    override def subscribe(observer: Observer[T]) = {
      val unsubscribe = delegate(observer)
      new Subscription {def close() = unsubscribe()}
    }
  }

  def createWithSubscription[T](delegate: Observer[T] => Subscription): Observable[T] = new Observable[T] {
    override def subscribe(observer: Observer[T]) = delegate(observer)
  }

  def empty[T](scheduler: Scheduler = Scheduler.immediate): Observable[T] = create {
    observer =>
      scheduler schedule {observer.onCompleted()}
      noop
  }

  def value[E](event: E, scheduler: Scheduler = Scheduler.immediate): Observable[E] = {
    Seq(event).toObservable(scheduler)
  }

  // only works for immediate observables!
  def toSeq[E](observable: Observable[E]): Seq[E] = {
    val result = new mutable.ArrayBuffer[E]
    observable.subscribe(new Observer[E] {
      override def onNext(event: E) {result append event}
    }).close()
    result
  }

  class TraversableWithToObservable[E](val traversable: Traversable[E]) {
    def toObservable: Observable[E] = toObservable(Scheduler.currentThread)

    def toObservable(scheduler: Scheduler): Observable[E] = create {
      observer =>
        var cancel = false
        scheduler.schedule {
          for (event <- traversable if !cancel) {
            observer.onNext(event)
          }
          if (!cancel)
            observer.onCompleted()
        }
        () => {cancel = true}
    }
  }

  implicit def traversable2observable[E](traversable: Traversable[E]): TraversableWithToObservable[E] = new TraversableWithToObservable(traversable)
}
