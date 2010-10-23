package org.deler.reactive

import scala.collection._

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

  def materialize: Observable[Notification[E]] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[E] {
        override def onCompleted() = observer.onNext(OnCompleted)

        override def onError(error: Exception) = observer.onNext(OnError(error))

        override def onNext(value: E) = observer.onNext(OnNext(value))
      })
  }

  // only works for immediate observables!
  def toSeq: Seq[E] = {
    val result = new mutable.ArrayBuffer[E]
    self.subscribe(new Observer[E] {
      override def onNext(event: E) {result append event}
    }).close()
    result
  }

}

object Observable {
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
      Observable.noop
  }

  def value[E](event: E, scheduler: Scheduler = Scheduler.immediate): Observable[E] = {
    Seq(event).toObservable(scheduler)
  }

  class TraversableWithToObservable[E](val traversable: Traversable[E]) {
    def toObservable: Observable[E] = toObservable(Scheduler.currentThread)

    def toObservable(scheduler: Scheduler): Observable[E] = createWithSubscription {
      observer =>
        val subscription = new BooleanSubscription
        def loop() {
          for (event <- traversable) {
            if (subscription.closed) {
              return
            }
            observer.onNext(event)
          }
          if (!subscription.closed)
            observer.onCompleted()
        }

        scheduler.schedule(loop)
        subscription
    }
  }

  implicit def traversable2observable[E](traversable: Traversable[E]): TraversableWithToObservable[E] = new TraversableWithToObservable(traversable)

  class DematerializeObservableWrapper[T](source: Observable[Notification[T]]) {
    def dematerialize: Observable[T] = createWithSubscription { observer =>
      val relay = new Relay(observer)
      source.subscribe(new Observer[Notification[T]] {
        override def onCompleted() = relay.onCompleted()
        override def onError(error: Exception) = relay.onError(error)
        override def onNext(notification: Notification[T]) = notification match {
          case OnCompleted => onCompleted()
          case OnError(error) => onError(error)
          case OnNext(value) => relay.onNext(value)
        }
      })

    }
  }

  implicit def dematerializeObservableWrapper[T](source: Observable[Notification[T]]) = new DematerializeObservableWrapper(source)

}
