package org.deler.reactive

import scala.collection._

/**
 * An observable can be subscribed to by an [[org.deler.reactive.Observer]]. Observables produce zero or more values
 * using <code>onNext</code> optionally terminated by either a call to <code>onCompleted</code> or <code>onError</code>.
 */
trait Observable[+A] {
  self =>
  import Observable._

  /**
   * Subscribes <code>observer</code>.
   */
  def subscribe(observer: Observer[A]): Subscription

  /**
   * Subscribes <code>onNext</code> to every value produced by this Observable.
   */
  def subscribe(onNext: A => Unit): Subscription = subscribe(onNext, defaultOnError, defaultOnCompleted)

  /**
   * Subscribes <code>onNext</code> and <code>onError</code> to this Observable.
   */
  def subscribe(onNext: A => Unit, onError: Exception => Unit): Subscription = subscribe(onNext, onError, defaultOnCompleted)

  /**
   * Subscribes <code>onNext</code> and <code>onCompleted</code> to this Observable.
   */
  def subscribe(onNext: A => Unit, onCompleted: () => Unit): Subscription = subscribe(onNext, defaultOnError, onCompleted)

  /**
   * Subscribes <code>onNext</code>, <code>onError</code>, and <code>onCompleted</code> to this Observable.
   */
  def subscribe(onNext: A => Unit, onError: Exception => Unit, onCompleted: () => Unit): Subscription = {
    val completedCallback = onCompleted
    val errorCallback = onError
    val nextCallback = onNext
    this subscribe (new Observer[A] {
      override def onCompleted() = completedCallback()

      override def onError(error: Exception) = errorCallback(error)

      override def onNext(value: A) = nextCallback(value)
    })
  }

  /**
   * A new observable defined by applying a partial function to all values produced by this observable on which the
   * function is defined.
   */
  def collect[B](pf: PartialFunction[A, B]): Observable[B] = {
    for (event <- this if pf.isDefinedAt(event)) yield pf(event)
  }

  /**
   * Appends <code>that</code> observable to this observable.
   */
  def ++[B >: A](that: Observable[B]): Observable[B] = createWithSubscription {
    observer =>
      val result = new MutableSubscription
      result.set(self.subscribe(
        onNext = {value => observer.onNext(value)},
        onError = {error => observer.onError(error); result.close},
        onCompleted = {() => result.set(that.subscribe(observer))}))
      result
  }

  /**
   * flatMap
   */
  def flatMap[B](f: A => Observable[B]): Observable[B] = new NestedObservableWrapper(self.map(f)).flatten

  /**
   * A new observable only containing the values from this observable for which the predicate is satisfied.
   */
  def filter(p: A => Boolean): Observable[A] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[A] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = observer.onError(error)

        override def onNext(event: A) = if (p(event)) observer.onNext(event)
      })
  }

  /**
   * Alias for <code>filter</code> that is used by the Scala for-loop construct.
   */
  def withFilter(p: A => Boolean) = filter(p)

  /**
   * A new observable defined by applying a function to all values produced by this observable.
   */
  def map[B](f: A => B): Observable[B] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[A] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = observer.onError(error)

        override def onNext(event: A) = observer.onNext(f(event))
      })
  }

  /**
   * A new observable that materializes each notification of this observable as a [[org.deler.reactive.Notification]].
   */
  def materialize: Observable[Notification[A]] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[A] {
        override def onCompleted() = observer.onNext(OnCompleted)

        override def onError(error: Exception) = observer.onNext(OnError(error))

        override def onNext(value: A) = observer.onNext(OnNext(value))
      })
  }

  /**
   * A new observable that only contains the values from this observable which are instances of the specified type.
   */
  def ofType[B](clazz: Class[B]): Observable[B] = {
    for (value <- this if clazz.isInstance(value)) yield clazz.cast(value)
  }

  /**
   * A new observable that executes <code>action</code> for its side-effects for each value produced by this observable.
   */
  def perform(action: A => Unit): Observable[A] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[A] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = {
          observer.onError(error)
        }

        override def onNext(event: A) = {
          action(event);
          observer.onNext(event)
        }
      })
  }

  /**
   * A new observable that only produces up to <code>n</code> values from this observable and then completes.
   */
  def take(n: Int): Observable[A] = createWithSubscription {
    observer =>
      self.subscribe(new RelayObserver(observer) {
        var count: Int = 0

        override def onNext(event: A) = {
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

  // only works for immediate observables!
  def toSeq: Seq[A] = {
    val result = new mutable.ArrayBuffer[A]
    self.subscribe(new Observer[A] {
      override def onNext(event: A) {result append event}
    }).close()
    result
  }

}

object Observable {
  /**
   * The default <code>onNext</code> handler does nothing.
   */
  def defaultOnNext[T](value: T) {}

  /**
   * The default <code>onError</code> handler throws the <code>error</code>.
   */
  def defaultOnError(error: Exception) {
    throw error
  }

  /**
   * The default <code>onCompleted</code> handler does nothing.
   */
  def defaultOnCompleted() {}

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

  def empty[T](scheduler: Scheduler = Scheduler.immediate): Observable[T] = createWithSubscription {
    observer =>
      scheduler schedule {observer.onCompleted()}
  }

  def value[E](event: E, scheduler: Scheduler = Scheduler.immediate): Observable[E] = {
    Seq(event).toObservable(scheduler)
  }

  class IterableToObservableWrapper[E](val traversable: Iterable[E]) {
    def toObservable: Observable[E] = toObservable(Scheduler.currentThread)

    def toObservable(scheduler: Scheduler): Observable[E] = createWithSubscription {
      observer =>
        val it = traversable.iterator
        scheduler scheduleRecursive { self =>
          if (it.hasNext) {
            observer.onNext(it.next())
            self()
          } else {
            observer.onCompleted()
          }
        }
    }
  }

  implicit def iterableToObservableWrapper[E](traversable: Iterable[E]): IterableToObservableWrapper[E] = new IterableToObservableWrapper(traversable)

  class NestedObservableWrapper[T](source: Observable[Observable[T]]) {
    def flatten: Observable[T] = createWithSubscription {
      observer =>
        val result = new CompositeSubscription
        val generatorSubscription = new MutableSubscription
        result.add(generatorSubscription)
        generatorSubscription.set(source.subscribe(
          onNext = {
            value =>
              val holder = new MutableSubscription
              result.add(holder)
              holder.set(value.subscribe(new Observer[T] {
                override def onCompleted() {
                  result.remove(holder)
                  if (result.isEmpty) {
                    observer.onCompleted()
                  }
                }

                override def onError(error: Exception) {
                  observer.onError(error)
                  result.close()
                }

                override def onNext(value: T) {
                  observer.onNext(value)
                }
              }))
          },
          onError = {error => observer.onError(error); result.close()},
          onCompleted = {
            () =>
              result.remove(generatorSubscription)
              if (result.isEmpty) {
                observer.onCompleted()
              }
          }))
        result
    }
  }

  implicit def nestedObservableWrapper[T](source: Observable[Observable[T]]) = new NestedObservableWrapper(source)

  class DematerializeObservableWrapper[T](source: Observable[Notification[T]]) {
    def dematerialize: Observable[T] = createWithSubscription {
      observer =>
        val relay = new RelayObserver(observer)
        source.subscribe(new Observer[Notification[T]] {
          override def onCompleted() = relay.onCompleted()

          override def onError(error: Exception) = relay.onError(error)

          override def onNext(notification: Notification[T]) = notification.accept(relay)
        })

    }
  }

  implicit def dematerializeObservableWrapper[T](source: Observable[Notification[T]]) = new DematerializeObservableWrapper(source)

}

/**
 * Observer that passes on all notifications to a <code>target</code> observer, taking care that no more notifications
 * are send after either <code>onError</code> or <code>onCompleted</code> occurred.
 */
private class RelayObserver[-T](target: Observer[T]) extends Observer[T] {
  private var completed = false

  override def onCompleted() {
    if (completed) return
    completed = true
    target.onCompleted()
  }

  override def onError(error: Exception) {
    if (completed) return
    completed = true
    target.onError(error);
  }

  override def onNext(value: T) {
    if (completed) return
    target.onNext(value)
  }

}
