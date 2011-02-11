package org.deler

import reactive.{Observer, Closeable, Scheduler, Observable}
import org.joda.time.Instant

package object reactive {

  // type GroupedObservable[K, +A] = Observable(K, Observable[(K, A)])

  class IterableToObservableWrapper[+A](iterable: Iterable[A]) {
    def subscribe(observer: Observer[A], scheduler: Scheduler = Scheduler.currentThread): Closeable = toObservable(scheduler).subscribe(observer)

    def toObservable(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] = ToObservable(iterable, scheduler)
  }

  implicit def iterableToObservableWrapper[A](iterable: Iterable[A]): IterableToObservableWrapper[A] = new IterableToObservableWrapper(iterable)

}
