package org.deler

import reactive.{Observer, Closeable, Scheduler, Observable}
import org.joda.time.Instant

package object reactive {
  implicit class IterableToObservableWrapper[+A](iterable: Iterable[A]) {
    def subscribe(observer: Observer[A], scheduler: Scheduler = Scheduler.currentThread): Closeable = toObservable(scheduler).subscribe(observer)

    def toObservable(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] = ToObservable(iterable, scheduler)
  }
}
