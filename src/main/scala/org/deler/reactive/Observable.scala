package org.deler.reactive

import java.util.concurrent.atomic.AtomicInteger
import org.joda.time.Duration
import scala.collection._
import scala.concurrent.SyncVar
import java.util.concurrent.{TimeoutException, LinkedBlockingQueue}

/**
 * An observable can be subscribed to by an [[org.deler.reactive.Observer]]. Observables produce zero or more values
 * using `onNext` optionally terminated by either a call to `onCompleted` or `onError`.
 *
 * @define coll observable sequence
 * @define Coll Observable
 */
trait Observable[+A] {
  import Observable._

  /**
   * Subscribes `observer`.
   */
  def subscribe(observer: Observer[A]): Closeable

  /**
   * Subscribes `onNext` to every value produced by this Observable.
   */
  def subscribe(onNext: A => Unit): Closeable = subscribe(onNext, defaultOnError, defaultOnCompleted)

  /**
   * Subscribes `onNext` and `onError` to this Observable.
   */
  def subscribe(onNext: A => Unit, onError: Exception => Unit): Closeable = subscribe(onNext, onError, defaultOnCompleted)

  /**
   * Subscribes `onNext` and `onCompleted` to this Observable.
   */
  def subscribe(onNext: A => Unit, onCompleted: () => Unit): Closeable = subscribe(onNext, defaultOnError, onCompleted)

  /**
   * Subscribes `onNext`, `onError`, and `onCompleted` to this Observable.
   */
  def subscribe(onNext: A => Unit, onError: Exception => Unit, onCompleted: () => Unit): Closeable = {
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
   * Appends `that` observable to this observable.
   */
  def ++[B >: A](that: Observable[B]): Observable[B] = Concat(this, that)

  /**
   * Returns the first $coll to produce a notification.
   */
  def amb[B >: A](that: Observable[B]): Observable[B] = Ambiguous(this, that)

  /**
   * Returns the first $coll to produce a value.
   */
  def choice[B >: A](that: Observable[B]): Observable[B] = Choice(this, that)

  /**
   * A new observable defined by applying a partial function to all values produced by this observable on which the
   * function is defined.
   */
  def collect[B](pf: PartialFunction[A, B]): Observable[B] = {
    for (value <- this if pf.isDefinedAt(value)) yield pf(value)
  }

  /**
   * A new $coll by applying a function to all elements of this $coll and merging the results.
   */
  def flatMap[B](f: A => Observable[B]): Observable[B] = new NestedObservableWrapper(this.map(f)).merge

  /**
   * A new observable only containing the values from this observable for which the predicate is satisfied.
   */
  def filter(predicate: A => Boolean): Observable[A] = Filter(this, predicate)

  /**
   * Returns the first value in this $coll.
   *
   * @throws IllegalStateException this $coll is empty.
   */
  def first: A = {
    val result = new SyncVar[Notification[A]]
    this.materialize.take(1).subscribe(result.set(_))
    result.get match {
      case OnNext(value) => value
      case OnError(error) => throw error
      case OnCompleted => throw new IllegalStateException("sequence is empty")
    }
  }

  /**
   * Alias for `filter` that is used by the Scala for-loop construct.
   */
  def withFilter(predicate: A => Boolean) = filter(predicate)

  /**
   * Binds `this` to the parameter of `f` so you can reuse `this` without additional side-effects. Example:
   *
   * {{{
   * makeObservable(x).let(xs => xs ++ xs)
   * }}}
   *
   * will invoke `makeObservable(x)` only once.
   */
  def let[B](f: Observable[A] => Observable[B]) = f(this)

  /**
   * A new observable defined by applying a function to all values produced by this observable.
   */
  def map[B](f: A => B): Observable[B] = Map(this, f)

  /**
   * A new observable that materializes each notification of this observable as a [[org.deler.reactive.Notification]].
   */
  def materialize: Observable[Notification[A]] = Materialize(this)

  /**
   * A new observable that only contains the values from this observable which are instances of the specified type.
   */
  def ofType[B](clazz: Class[B]): Observable[B] = {
    for (value <- this if clazz.isInstance(value)) yield clazz.cast(value)
  }

  /**
   * A new observable that executes `action` for its side-effects for each value produced by this observable.
   */
  def perform(action: A => Unit): Observable[A] = {
    for (value <- this) yield {
      action(value)
      value
    }
  }

  /**
   * Returns a connectable $coll that shares a single subscription to this $coll using the immediate scheduler.
   */
  def publish(): ConnectableObservable[A] = publish(Scheduler.immediate)

  /**
   * Returns a connectable $coll that shares a single subscription to this $coll.
   */
  def publish(scheduler: Scheduler): ConnectableObservable[A] = new PublishConnectableObservable(this, scheduler)

  /**
   * Returns an $coll that is the result of invoking the `selector` on a connectable observable sequence that shares a
   * single subscription to the this $coll using the immediate scheduler.
   */
  def publish[B](selector: Observable[A] => Observable[B]): Observable[B] = publish(selector, Scheduler.immediate)

  /**
   * Returns an $coll that is the result of invoking the `selector` on a connectable observable sequence that shares a
   * single subscription to the this $coll.
   */
  def publish[B](selector: Observable[A] => Observable[B], scheduler: Scheduler): Observable[B] = Publish(this, selector, scheduler)

  /**
   * Repeats the source observable indefinitely.
   */
  def repeat: Observable[A] = Repeat(this)

  /**
   * Repeats the source observable `n` times.
   */
  def repeat(n: Int): Observable[A] = RepeatN(this, n)

  /**
   * A new observable that only produces up to `n` values from this observable and then completes.
   */
  def take(n: Int): Observable[A] = Take(this, n)

  def drop(n: Int): Observable[A] = Drop(this, n)

  def tail: Observable[A] = drop(1)

  /**
   * Takes values from this $coll until the `other` $coll produces a value.
   */
  def takeUntil(other: Observable[Any]): Observable[A] = TakeUntil(this, other)

  def skipUntil(other: Observable[Any]): Observable[A] = SkipUntil(this, other)

  /**
   * Returns an $coll that produces values from this $coll until `dueTime`, when it raises a
   * [[java.util.concurrent.TimeoutException]].
   *
   * Completes when either this $coll completes before the timeout or the timeout occurs.
   *
   * Uses the `Scheduler.threadPool` scheduler.
   */
  def timeout(dueTime: Duration): Observable[A] = timeout(dueTime, Scheduler.threadPool)

  /**
   * Returns an $coll that produces values from this $coll until `dueTime`, when it raises a
   * [[java.util.concurrent.TimeoutException]].
   *
   * Completes when either this $coll completes before the timeout or the timeout occurs.
   */
  def timeout(dueTime: Duration, scheduler: Scheduler): Observable[A] = {
    timeout(dueTime, Observable.throwing(new TimeoutException), scheduler)
  }

  /**
   * Returns an $coll that produces values from this $coll until `dueTime`, when it switches to the `other` $coll.
   *
   * Completes when either this $coll completes before the timeout or the `other` $coll completes after the timeout.
   *
   * Uses the `Scheduler.threadPool` scheduler.
   */
  def timeout[B >: A](dueTime: Duration, other: Observable[B]): Observable[B] = timeout(dueTime, other, Scheduler.threadPool)

  /**
   * Returns an $coll that produces values from this $coll until `dueTime`, when it switches to the `other` $coll.
   *
   * Completes when either this $coll completes before the timeout or the `other` $coll completes after the timeout.
   */
  def timeout[B >: A](dueTime: Duration, other: Observable[B], scheduler: Scheduler): Observable[B] = {
    val source = this.map(Some(_)) amb Observable.timer(dueTime, scheduler).map(_ => None)
    source flatMap {
      case Some(value) => Observable.returning(value)
      case None => other
    }
  }

  /**
   * Converts an Observable into a (lazy) Stream of values.
   */
  def toSeq: Seq[A] = {
    val result = new LinkedBlockingQueue[Notification[A]]
    val subscription = this.materialize.subscribe(result.put(_))

    def resultToStream: Stream[A] = {
      result.take() match {
        case OnCompleted =>
          subscription.close()
          Stream.Empty
        case OnNext(value) =>
          value #:: resultToStream
        case OnError(error) =>
          subscription.close()
          throw error
      }
    }

    resultToStream
  }

  def zip[B, C](other: Observable[B])(f: (A, B) => C): Observable[C] = Zip(this, other, f)

  def throttle(dueTime: Duration, scheduler: Scheduler = Scheduler.threadPool): Observable[A] = Throttle(this, dueTime, scheduler)

  def distinctUntilChanged: Observable[A] = DistinctUntilChanged(this)

  /**
   * Switches to `other` when `this` terminates with an error.
   */
  def catching[B >: A](other: Observable[B]): Observable[B] = Catching(this, other)

  /**
   * Asynchronously subscribe (and unsubscribe) observers on `scheduler`. The subscription is always completed
   * before the observer is unsubscribed.
   */
  def subscribeOn(scheduler: Scheduler): Observable[A] = SubscribeOn(this, scheduler)

  /**
   * Asynchronously notify observers on the specified `scheduler`. Notifications are still
   * delivered one at a time, in-order. An internal, unbounded queue is used to ensure the producer
   * is not limited by a slow consumer.
   */
  def observeOn(scheduler: Scheduler): Observable[A] = ObserveOn(this, scheduler)

  /**
   * Forces this $coll to conform to the $coll contract.
   */
  def conform: Observable[A] = Conform(this)

  /**
   * Forces this $coll to conform to the $coll contract and ensures that all notifications are serialized even
   * when multiple threads push notifications.
   */
  def synchronize: Observable[A] = Synchronize(this)
}

/**
 * @define coll observable sequence
 */
object Observable {

  /**
   * Returns the first $coll to produce a notification.
   */
  def amb[A](sources: Observable[A]*): Observable[A] = Ambiguous(sources: _*)

  /**
   * Returns the first $coll to produce a value.
   */
  def choice[A](sources: Observable[A]*): Observable[A] = Choice(sources: _*)

  def apply[A](values: A*): Observable[A] = values.toObservable(Scheduler.currentThread)

  def apply[A](scheduler: Scheduler, values: A*): Observable[A] = values.toObservable(scheduler)

  /**
   * The default `onNext` handler does nothing.
   */
  def defaultOnNext[A](value: A) {}

  /**
   * The default `onError` handler throws the `error`.
   */
  def defaultOnError(error: Exception) {throw error}

  /**
   * The default `onCompleted` handler does nothing.
   */
  def defaultOnCompleted() {}

  def create[A](delegate: Observer[A] => () => Unit): Observable[A] = createWithCloseable {
    observer =>
      val unsubscribe = delegate(observer)
      new ActionCloseable(unsubscribe)
  }

  def createWithCloseable[A](delegate: Observer[A] => Closeable): Observable[A] = Conform(new Observable[A] {
    override def subscribe(observer: Observer[A]) = delegate(observer)
  })

  /**
   * Returns an empty $coll using the immediate scheduler.
   */
  def empty: Observable[Nothing] = empty(Scheduler.immediate)

  /**
   * Returns an empty $coll.
   */
  def empty(scheduler: Scheduler): Observable[Nothing] = createWithCloseable {
    observer =>
      scheduler schedule observer.onCompleted()
  }

  /**
   * Returns a $coll that terminates with `error` using the immediate scheduler.
   */
  def throwing(error: Exception): Observable[Nothing] = throwing(error, Scheduler.immediate)

  /**
   * Returns a $coll that terminates with `error`.
   */
  def throwing(error: Exception, scheduler: Scheduler): Observable[Nothing] = createWithCloseable {
    observer =>
      scheduler schedule observer.onError(error)
  }

  /**
   * Returns a $coll that contains a single `value` using the immediate scheduler.
   */
  def returning[A](value: A): Observable[A] = returning(value, Scheduler.immediate)

  /**
   * Returns a $coll that contains a single `value`.
   */
  def returning[A](value: A, scheduler: Scheduler): Observable[A] = {
    Seq(value).toObservable(scheduler)
  }

  /**
   * Returns an infinite $coll that produces a new value each `period` using the thread pool scheduler. The values
   * produced are `0`, `1`, `2`, ...
   */
  def interval(period: Duration): Observable[Int] = interval(period, Scheduler.threadPool)

  /**
   * Returns an infinite $coll that produces a new value each `period`. The values produced are `0`, `1`, `2`, ...
   */
  def interval(period: Duration, scheduler: Scheduler): Observable[Int] = createWithCloseable {
    observer =>
      var counter = 0
      scheduler.scheduleRecursiveAfter(period) {
        reschedule =>
          observer.onNext(counter)
          counter += 1
          reschedule(period)
      }
  }

  /**
   * Returns an $coll that produces a single value after `dueTime` using the thread pool scheduler. The value produced
   * is `0`.
   */
  def timer(dueTime: Duration): Observable[Int] = timer(dueTime, Scheduler.threadPool)

  /**
   * Returns an $coll that produces a single value after `dueTime`. The value produced is `0`.
   */
  def timer(dueTime: Duration, scheduler: Scheduler): Observable[Int] = createWithCloseable {
    observer =>
      scheduler.scheduleAfter(dueTime) {
        observer.onNext(0)
        observer.onCompleted()
      }
  }

  class ObservableExtensions[A](observable: Observable[A]) {
    /**
     * Merges this observable sequence with `that` observable sequence.
     */
    def merge[B >: A](that: Observable[B]): Observable[B] = Merge(Observable(Scheduler.immediate, observable, that))
  }

  implicit def observableExtensions[A](observable: Observable[A]) = new ObservableExtensions(observable)

  class NestedObservableWrapper[A](source: Observable[Observable[A]]) {
    /**
     * Merges an observable sequence of observable sequences into an observable sequence.
     */
    def merge: Observable[A] = Merge(source)

    /**
     * Transforms an observable sequence of observable sequences into an observable
     * sequence producing values only from the most recent observable sequence
     */
    def switch: Observable[A] = Switch(source)
  }

  implicit def nestedObservableWrapper[A](source: Observable[Observable[A]]) = new NestedObservableWrapper(source)

  class DematerializeObservableWrapper[A](source: Observable[Notification[A]]) {
    def dematerialize: Observable[A] = Dematerialize(source)
  }

  implicit def dematerializeObservableWrapper[A](source: Observable[Notification[A]]) = new DematerializeObservableWrapper(source)

  implicit private def observableToConformingObservable[A](source: Observable[A]): ConformingObservable[A] = source.conform.asInstanceOf[ConformingObservable[A]]
}

private trait ConformingObservable[+A] extends Observable[A] {
  override def conform: Observable[A] = this
}

private trait SynchronizingObservable[+A] extends ConformingObservable[A] {
  override def synchronize: Observable[A] = this
}

private abstract class BaseObservable[+A] extends Observable[A] {
  protected def doSubscribe(observer: Observer[A]): Closeable

  final override def subscribe(observer: Observer[A]): Closeable = CurrentThreadScheduler runImmediate doSubscribe(observer)
}

private abstract class ConformedObservable[+A] extends ConformingObservable[A] {
  protected def doSubscribe(observer: Observer[A]): Closeable

  final override def subscribe(observer: Observer[A]): Closeable = CurrentThreadScheduler runImmediate {
    val subscription = new MutableCloseable
    val target = new DelegateObserver[A](observer) with ConformedObserver[A] {
      override def onError(error: Exception) {
        subscription.close()
        super.onError(error)
      }

      override def onCompleted() {
        subscription.close()
        super.onCompleted()
      }
    }

    subscription.set(doSubscribe(target))
    new CompositeCloseable(subscription, target)
  }
}

private abstract class SynchronizedObservable[+A] extends SynchronizingObservable[A] {
  protected def doSubscribe(observer: Observer[A]): Closeable

  final override def subscribe(observer: Observer[A]): Closeable = CurrentThreadScheduler runImmediate {
    val subscription = new MutableCloseable
    val target = new DelegateObserver[A](observer) with SynchronizedObserver[A] {
      override def onError(error: Exception) = synchronized {
        subscription.close()
        super.onError(error)
      }

      override def onCompleted() = synchronized {
        subscription.close()
        super.onCompleted()
      }
    }

    subscription.set(doSubscribe(target))
    new CompositeCloseable(subscription, target)
  }
}

private case class Ambiguous[A](sources: Observable[A]*) extends BaseObservable[A] with SynchronizingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val subscriptions = sources.map(_ => new MutableCloseable)
    val result = new CompositeCloseable(subscriptions: _*)

    def closeOtherSubscriptions(i: Int) {
      for (j <- subscriptions.indices if i != j) {
        result -= subscriptions(j)
      }
    }

    val choice = new AtomicInteger(-1)
    for (i <- sources.indices) {
      subscriptions(i).set(sources(i).conform subscribe new Observer[A] {
        override def onNext(value: A) {
          if (choice.get == i) {
            observer.onNext(value)
          } else if (choice.compareAndSet(-1, i)) {
            closeOtherSubscriptions(i)
            observer.onNext(value)
          }
        }

        override def onError(error: Exception) {
          if (choice.get == i || choice.compareAndSet(-1, i)) {
            result.close()
            observer.onError(error)
          }
        }

        override def onCompleted() {
          if (choice.get == i || choice.compareAndSet(-1, i)) {
            result.close()
            observer.onCompleted()
          }
        }
      })
    }

    result
  }
}

private case class Choice[A](sources: Observable[A]*) extends BaseObservable[A] with SynchronizingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val subscriptions = sources.map(_ => new MutableCloseable)
    val result = new CompositeCloseable(subscriptions: _*)

    def closeOtherSubscriptions(i: Int) {
      for (j <- subscriptions.indices if i != j) {
        result -= subscriptions(j)
      }
    }

    val completed = new AtomicInteger(0)
    val choice = new AtomicInteger(-1)

    for (i <- sources.indices) {
      subscriptions(i).set(sources(i).conform subscribe new Observer[A] {
        override def onNext(value: A) {
          if (choice.get == i) {
            observer.onNext(value)
          } else if (choice.compareAndSet(-1, i)) {
            closeOtherSubscriptions(i)
            observer.onNext(value)
          }
        }

        override def onError(error: Exception) {
          if (choice.get == i || choice.compareAndSet(-1, i)) {
            result.close()
            observer.onError(error)
          }
        }

        override def onCompleted() {
          if (choice.get == i || completed.incrementAndGet == sources.size) {
            result.close()
            observer.onCompleted()
          } else {
            result -= subscriptions(i)
          }
        }

      })
    }

    result
  }
}

private case class Concat[A, B >: A](left: ConformingObservable[A], right: ConformingObservable[B])
        extends BaseObservable[B] with ConformingObservable[B] {
  def doSubscribe(observer: Observer[B]): Closeable = {
    val subscription = new MutableCloseable
    val result = new MutableCloseable(subscription)

    subscription.set(left.subscribe(new DelegateObserver(observer) {
      override def onCompleted() = result clearAndSet {right.subscribe(observer)}
    }))

    result
  }
}

private case class Zip[A, B, C](left: ConformingObservable[A], right: ConformingObservable[B], f: (A, B) => C)
        extends BaseObservable[C] with ConformingObservable[C] {
  def doSubscribe(observer: Observer[C]): Closeable = {
    import scala.collection.mutable.Queue

    val leftQueue = Queue[A]() 
    val rightQueue = Queue[B]() 
    val subscription1 = new MutableCloseable
    val subscription2 = new MutableCloseable
    val subscriptions = new CompositeCloseable(subscription1, subscription2)
    subscription1.set(left.subscribe(new ZipObserver[A](leftQueue)))
    subscription2.set(right.subscribe(new ZipObserver[B](rightQueue)))

    class ZipObserver[X](q: Queue[X]) extends Observer[X] {
      override def onError(error: Exception) {
        subscriptions.close()
        observer.onError(error)
      }

      override def onCompleted() {
        subscriptions.close()
        observer.onCompleted()
      }

      override def onNext(value: X) {
        q.enqueue(value)
        if (!leftQueue.isEmpty && !rightQueue.isEmpty) 
          observer.onNext(f(leftQueue.dequeue, rightQueue.dequeue))
      }
    }
    subscriptions
  }
}

private case class Conform[+A](source: Observable[A]) extends ConformedObservable[A] {
  override protected def doSubscribe(observer: Observer[A]): Closeable = source.subscribe(observer)

  override def synchronize: Observable[A] = source.synchronize
}

private case class Dematerialize[+A](source: Observable[Notification[A]]) extends ConformedObservable[A] {
  override protected def doSubscribe(observer: Observer[A]): Closeable =
    source.subscribe(onNext = _.accept(observer), onError = observer.onError, onCompleted = observer.onCompleted)
}

private case class Filter[A](source: ConformingObservable[A], predicate: A => Boolean)
        extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val subscription = new MutableCloseable
    subscription.set(source.subscribe(new DelegateObserver(observer) {
      override def onNext(value: A) {
        util.control.Exception.catching(classOf[Exception]) either predicate(value) match {
          case Left(error) =>
            subscription.close()
            super.onError(error.asInstanceOf[Exception])
          case Right(true) => super.onNext(value)
          case Right(false) =>
        }
      }
    }))
    subscription
  }

  override def filter(q: A => Boolean): Observable[A] = source.filter(value => predicate(value) && q(value))
}

private case class Map[A, B](source: ConformingObservable[A], f: A => B)
        extends BaseObservable[B] with ConformingObservable[B] {
  def doSubscribe(observer: Observer[B]): Closeable = {
    val subscription = new MutableCloseable
    subscription.set(source.subscribe(new Observer[A] {
      override def onNext(value: A) {
        util.control.Exception.catching(classOf[Exception]) either f(value) match {
          case Left(error) =>
            subscription.close()
            observer.onError(error.asInstanceOf[Exception])
          case Right(mapped) =>
            observer.onNext(mapped)
        }
      }

      override def onError(error: Exception) = observer.onError(error)

      override def onCompleted() = observer.onCompleted()
    }))
    subscription
  }

  override def map[C](g: B => C): Observable[C] = source.map(f andThen g)
}

private case class Materialize[A](source: ConformingObservable[A])
        extends BaseObservable[Notification[A]] with ConformingObservable[Notification[A]] {
  def doSubscribe(observer: Observer[Notification[A]]): Closeable = {
    source.subscribe(new Observer[A] {
      override def onCompleted() = observer.onNext(OnCompleted)

      override def onError(error: Exception) = observer.onNext(OnError(error))

      override def onNext(value: A) = observer.onNext(OnNext(value))
    })
  }
}

private case class Merge[A](generator: ConformingObservable[Observable[A]]) extends SynchronizedObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val generatorSubscription = new MutableCloseable

    val activeSubscriptionCount = new AtomicInteger(1)
    val subscriptions = new CompositeCloseable(generatorSubscription)

    generatorSubscription.set(generator.subscribe(new Observer[Observable[A]] {
      override def onError(error: Exception) {
        subscriptions.close()
        observer.onError(error)
      }

      override def onCompleted() {
        subscriptions -= generatorSubscription
        if (activeSubscriptionCount.decrementAndGet() == 0) {
          observer.onCompleted()
        }
      }

      override def onNext(value: Observable[A]) {
        activeSubscriptionCount.incrementAndGet()

        val subscription = new MutableCloseable
        subscriptions += subscription

        subscription.set(value.conform.subscribe(new Observer[A] {
          override def onNext(value: A) {
            observer.onNext(value)
          }

          override def onError(error: Exception) {
            subscriptions.close()
            observer.onError(error)
          }

          override def onCompleted() {
            subscriptions -= subscription
            if (activeSubscriptionCount.decrementAndGet() == 0) {
              observer.onCompleted()
            }
          }
        }))
      }
    }))

    subscriptions
  }
}

private case class ObserveOn[A](source: ConformingObservable[A], scheduler: Scheduler)
        extends ConformedObservable[A] with SynchronizingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    source.subscribe(new ScheduledObserver(observer, scheduler))
  }
}

private case class Publish[A, B](source: ConformingObservable[A], selector: Observable[A] => Observable[B], scheduler: Scheduler)
        extends BaseObservable[B] with ConformingObservable[B] {
  def doSubscribe(observer: Observer[B]): Closeable = {
    val connectable = source.publish(scheduler)
    val observable = selector(connectable)

    new CompositeCloseable(observable.subscribe(observer), connectable.connect())
  }
}

private case class Repeat[+A](source: ConformingObservable[A]) extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val result = new MutableCloseable
    def run() {
      result clearAndSet source.subscribe(new DelegateObserver(observer) {
        override def onCompleted() = run()
      })
    }

    run()
    result
  }

  override def repeat: Observable[A] = this

  override def repeat(n: Int): Observable[A] = if (n > 0) this else RepeatN(source, n)
}

private case class RepeatN[+A](source: ConformingObservable[A], n: Int)
        extends BaseObservable[A] with ConformingObservable[A] {
  require(n >= 0, "n >= 0")

  def doSubscribe(observer: Observer[A]): Closeable = {
    val result = new MutableCloseable
    def run(count: Int) {
      if (count >= n) {
        observer.onCompleted()
      } else {
        result clearAndSet source.subscribe(new DelegateObserver(observer) {
          override def onCompleted() {
            run(count + 1)
          }
        })
      }
    }

    run(0)
    result
  }

  override def repeat: Observable[A] = if (n > 0) Repeat(source) else this

  override def repeat(n: Int): Observable[A] = RepeatN(source, this.n * n)
}

private case class Catching[A, B >: A](source: ConformingObservable[A], other: ConformingObservable[B])
        extends BaseObservable[B] with ConformingObservable[B] {
  def doSubscribe(observer: Observer[B]): Closeable = {
    val subscription = new MutableCloseable
    val result = new MutableCloseable(subscription)

    subscription.set(source.subscribe(new DelegateObserver(observer) {
      override def onError(error: Exception) = result clearAndSet {other.subscribe(observer)}
    }))

    result
  }
}

private case class SubscribeOn[A](source: ConformingObservable[A], scheduler: Scheduler)
        extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val scheduled = new MutableCloseable
    val result = new MutableCloseable(scheduled)

    scheduled.set(scheduler schedule {
      val subscription = source.subscribe(observer)
      result clearAndSet new ScheduledCloseable(subscription, scheduler)
    })
    result
  }
}

private case class Switch[A](generator: ConformingObservable[Observable[A]]) extends SynchronizedObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val generatorSubscription = new MutableCloseable
    val currentSubscription = new MutableCloseable
    val result = new CompositeCloseable(generatorSubscription, currentSubscription)

    @volatile var generatorActive = true
    @volatile var currentActive = false

    generatorSubscription.set(generator.subscribe(new Observer[Observable[A]] {
      override def onError(error: Exception) {
        result.close()
        observer.onError(error)
      }

      override def onCompleted() {
        result -= generatorSubscription
        generatorActive = false
        if (!currentActive) {
          result.close()
          observer.onCompleted()
        }
      }

      override def onNext(value: Observable[A]) {
        currentActive = true

        currentSubscription.clearAndSet(value.conform.subscribe(new Observer[A] {
          override def onNext(value: A) {
            observer.onNext(value)
          }

          override def onError(error: Exception) {
            result.close()
            observer.onError(error)
          }

          override def onCompleted() {
            currentSubscription.clear()
            currentActive = false
            if (!generatorActive) {
              result.close()
              observer.onCompleted()
            }
          }
        }))
      }
    }))

    result
  }
}

private case class Take[+A](source: ConformingObservable[A], n: Int)
        extends BaseObservable[A] with ConformingObservable[A] {
  require(n >= 0, "n >= 0")

  def doSubscribe(observer: Observer[A]): Closeable = {
    val subscription = new MutableCloseable
    subscription.set(source.subscribe(new DelegateObserver(observer) {
      var count: Int = 0

      override def onNext(value: A) {
        if (count < n) {
          count += 1
          super.onNext(value)
        }
        if (count >= n) {
          subscription.close()
          super.onCompleted()
        }
      }
    }))
    subscription
  }

  override def take(n: Int) = Take(source, this.n.min(n))
}

private case class Drop[+A](source: ConformingObservable[A], n: Int)
        extends BaseObservable[A] with ConformingObservable[A] {
  require(n >= 0, "n >= 0")

  def doSubscribe(observer: Observer[A]): Closeable = {
    val subscription = new MutableCloseable
    subscription.set(source.subscribe(new DelegateObserver(observer) {
      var count: Int = 0

      override def onNext(value: A) {
        if (count < n) {
          count += 1          
        } else super.onNext(value)
      }
    }))
    subscription
  }
}

private case class TakeUntil[+A](source: Observable[A], other: Observable[Any]) extends SynchronizedObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val otherSubscription = new MutableCloseable
    val result = new CompositeCloseable(otherSubscription)

    otherSubscription.set(other.subscribe(new Observer[Any] {
      override def onNext(value: Any) {
        result.close()
        observer.onCompleted()
      }

      override def onError(error: Exception) {
        result.close()
        observer.onError(error)
      }

      override def onCompleted() {
        result -= otherSubscription
      }
    }))

    result += source.subscribe(new DelegateObserver(observer) {
      override def onError(error: Exception) {
        otherSubscription.close()
        super.onError(error)
      }

      override def onCompleted() {
        otherSubscription.close()
        super.onCompleted()
      }
    })
    result
  }
}

private case class SkipUntil[+A](source: Observable[A], other: Observable[Any]) extends SynchronizedObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    val otherSubscription = new MutableCloseable
    val result = new CompositeCloseable(otherSubscription)
    var otherStarted = false

    otherSubscription.set(other.subscribe(new Observer[Any] {
      override def onNext(value: Any) {
        otherStarted = true
      }

      override def onError(error: Exception) {
        result.close()
        observer.onError(error)
      }

      override def onCompleted() {
        result -= otherSubscription
      }
    }))

    // FIXME onError + onCompleted copy paste from TakeUntil
    result += source.subscribe(new DelegateObserver(observer) {
      override def onNext(value: A) {
        if (otherStarted) super.onNext(value)
      }

      override def onError(error: Exception) {
        otherSubscription.close()
        super.onError(error)
      }

      override def onCompleted() {
        otherSubscription.close()
        super.onCompleted()
      }
    })
    result
  }
}

private case class ToObservable[+A](iterable: Iterable[A], scheduler: Scheduler)
        extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
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

private case class Throttle[+A](source: Observable[A], dueTime: Duration, scheduler: Scheduler)
        extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    var lastValue: Option[A] = None

    val subscription = Observable.interval(dueTime, scheduler)
      .subscribe { _ => lastValue.foreach(v => { lastValue = None; observer.onNext(v) }) }

    val result = new CompositeCloseable(subscription)

    // FIXME onError + onCompleted copy paste from TakeUntil
    result += source.subscribe(new DelegateObserver(observer) {
      override def onNext(value: A) {
        lastValue = Some(value)
      }

      override def onError(error: Exception) {
        subscription.close()
        super.onError(error)
      }

      override def onCompleted() {
        subscription.close()
        super.onCompleted()
      }
    })
    result
  }
}

private case class DistinctUntilChanged[+A](source: Observable[A])
        extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    var lastValue: Option[A] = None
    val subscription = new MutableCloseable

    // FIXME onError + onCompleted copy paste from TakeUntil
    subscription.set(source.subscribe(new DelegateObserver[A](observer) {
      override def onNext(value: A) {
        if (lastValue.map(_ != value).getOrElse(true)) {
          observer.onNext(value)
          lastValue = Some(value)
        }
      }

      override def onError(error: Exception) {
        subscription.close()
        super.onError(error)
      }

      override def onCompleted() {
        subscription.close()
        super.onCompleted()
      }
    }))
    subscription
  }
}

private case class Synchronize[+A](source: Observable[A]) extends SynchronizedObservable[A] {
  override def doSubscribe(observer: Observer[A]): Closeable = source.subscribe(observer)
}
