package org.deler.reactive

import scala.collection._
import org.joda.time.Duration
import java.util.concurrent.LinkedBlockingQueue

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
    for (value <- this if pf.isDefinedAt(value)) yield pf(value)
  }

  /**
   * Appends <code>that</code> observable to this observable.
   */
  def ++[B >: A](that: Observable[B]): Observable[B] = createWithSubscription {
    observer =>
      val result = new FutureSubscription
      result.set(self.subscribe(
        onNext = {value => observer.onNext(value)},
        onError = {error => observer.onError(error); result.close()},
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
  def filter(predicate: A => Boolean): Observable[A] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[A] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = observer.onError(error)

        override def onNext(value: A) = if (predicate(value)) observer.onNext(value)
      })
  }

  /**
   * Alias for <code>filter</code> that is used by the Scala for-loop construct.
   */
  def withFilter(predicate: A => Boolean) = filter(predicate)

  /**
   * A new observable defined by applying a function to all values produced by this observable.
   */
  def map[B](f: A => B): Observable[B] = createWithSubscription {
    observer =>
      self.subscribe(new Observer[A] {
        override def onCompleted() = observer.onCompleted()

        override def onError(error: Exception) = observer.onError(error)

        override def onNext(value: A) = observer.onNext(f(value))
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

        override def onNext(value: A) = {
          action(value);
          observer.onNext(value)
        }
      })
  }

  /**
   * Repeats the source observable indefinitely.
   */
  def repeat(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] = createWithSubscription {
    observer =>
      val result = new CompositeSubscription
      val subscription = new MutableSubscription
      result.add(subscription)
      result.add(scheduler scheduleRecursive {
        recurs =>
          subscription.set(self.subscribe(
            onNext = {value => observer.onNext(value)},
            onError = {error => result.close(); observer.onError(error)},
            onCompleted = () => recurs()))
      })
      result
  }

  /**
   *  A new observable that only produces up to <code>n</code> values from this observable and then completes.
   */
  def take(n: Int): Observable[A] = createWithSubscription {
    observer =>
      val result = new MutableSubscription
      result.set(self.subscribe(new RelayObserver(observer) {
        var count: Int = 0

        override def onNext(value: A) = {
          if (count < n) {
            count += 1
            super.onNext(value)
          }
          if (count >= n) {
            result.close()
            onCompleted()
          }
        }

      }))
      result
  }

  /**
   * Converts an Observable into a (lazy) Stream of values.
   */
  def toSeq: Seq[A] = {
    val result = new LinkedBlockingQueue[Notification[A]]
    val subscription = self.materialize.subscribe(value => result.put(value))

    def resultToStream: Stream[A] = {
      result.take match {
        case OnCompleted => subscription.close(); Stream.Empty
        case OnNext(value) => value #:: resultToStream
        case OnError(error) => subscription.close(); throw error
      }
    }

    resultToStream
  }

  def rescue[B >: A](source: Observable[B]): Observable[B] = createWithSubscription {
    observer =>
      val result = new CompositeSubscription
      val selfSubscription = new MutableSubscription
      result.add(selfSubscription)
      selfSubscription.set(self.subscribe(new RelayObserver(observer) {
        override def onError(error: Exception) {
          result.remove(selfSubscription)
          result.add(source.subscribe(observer))
        }
      }))
      result
  }

}

object Observable {
  def apply[A](values: A*)(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] =
    new IterableToObservableWrapper(values).toObservable(scheduler)

  /**
   * The default <code>onNext</code> handler does nothing.
   */
  def defaultOnNext[A](value: A) {}

  /**
   * The default <code>onError</code> handler throws the <code>error</code>.
   */
  def defaultOnError(error: Exception) {throw error}

  /**
   * The default <code>onCompleted</code> handler does nothing.
   */
  def defaultOnCompleted() {}

  def noop() {}

  def create[A](delegate: Observer[A] => () => Unit): Observable[A] = createWithSubscription {
    observer =>
      val unsubscribe = delegate(observer)
      new Subscription {def close() = unsubscribe()}
  }

  def createWithSubscription[A](delegate: Observer[A] => Subscription): Observable[A] = new Observable[A] {
    override def subscribe(observer: Observer[A]) = CurrentThreadScheduler runImmediate delegate(observer)
  }

  def empty[A](implicit scheduler: Scheduler = Scheduler.immediate): Observable[A] = createWithSubscription {
    observer =>
      scheduler schedule observer.onCompleted()
  }

  def raise[Nothing](error: Exception)(implicit scheduler: Scheduler = Scheduler.immediate): Observable[Nothing] = createWithSubscription {
    observer =>
      scheduler schedule observer.onError(error)
  }

  def value[A](value: A)(implicit scheduler: Scheduler = Scheduler.immediate): Observable[A] = {
    Seq(value).toObservable(scheduler)
  }

  def interval(interval: Duration)(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[Int] = createWithSubscription {
    observer =>
      var counter = 0
      val result = new MutableSubscription
      result.set(scheduler.scheduleRecursiveAfter(interval) {
        reschedule =>
          observer.onNext(counter)
          counter += 1
          reschedule(interval)
      })
      result
  }

  class IterableToObservableWrapper[+A](val iterable: Iterable[A]) {
    def subscribe(observer: Observer[A], scheduler: Scheduler = Scheduler.currentThread): Subscription = this.toObservable(scheduler).subscribe(observer)

    def toObservable(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] = createWithSubscription {
      observer =>
        val it = iterable.iterator
        scheduler scheduleRecursive {
          self =>
            if (it.hasNext) {
              observer.onNext(it.next())
              self()
            } else {
              observer.onCompleted()
            }
        }
    }
  }

  implicit def iterableToObservableWrapper[A](iterable: Iterable[A]): IterableToObservableWrapper[A] = new IterableToObservableWrapper(iterable)

  class NestedObservableWrapper[A](source: Observable[Observable[A]]) {
    def flatten: Observable[A] = createWithSubscription {
      observer =>
        val result = new CompositeSubscription
        val generatorSubscription = new MutableSubscription
        result.add(generatorSubscription)
        generatorSubscription.set(source.subscribe(
          onNext = {
            value =>
              val holder = new MutableSubscription
              result.add(holder)
              holder.set(value.subscribe(new Observer[A] {
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

                override def onNext(value: A) {
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

  implicit def nestedObservableWrapper[A](source: Observable[Observable[A]]) = new NestedObservableWrapper(source)

  class DematerializeObservableWrapper[A](source: Observable[Notification[A]]) {
    def dematerialize: Observable[A] = createWithSubscription {
      observer =>
        val relay = new RelayObserver(observer)
        source.subscribe(new Observer[Notification[A]] {
          override def onCompleted() = relay.onCompleted()

          override def onError(error: Exception) = relay.onError(error)

          override def onNext(notification: Notification[A]) = notification.accept(relay)
        })

    }
  }

  implicit def dematerializeObservableWrapper[A](source: Observable[Notification[A]]) = new DematerializeObservableWrapper(source)

}

/**
 * Observer that passes on all notifications to a <code>target</code> observer, taking care that no more notifications
 * are send after either <code>onError</code> or <code>onCompleted</code> occurred.
 */
private class RelayObserver[-A](target: Observer[A]) extends Observer[A] {
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

  override def onNext(value: A) {
    if (completed) return
    target.onNext(value)
  }

}
