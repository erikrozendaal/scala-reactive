package org.deler.events

import scala.collection._

trait Subscription {
  def close
}

trait Observer[E] {
  def onCompleted() {}
  def onError(error: Exception) {}
  def onNext(event: E) {}
}

trait Observable[E] { self =>
  def subscribe(observer: Observer[E]): Subscription

  def subscribe(onCompleted: () => Unit = () => {}, onError: Exception => Unit = {_ =>}, onNext: E => Unit = {_ =>}): Subscription = {
    val completed = onCompleted
    val error = onError
    val next = onNext
    this subscribe (new Observer[E] {
      override def onCompleted() = completed()
      override def onError(ex: Exception) = error(ex)
      override def onNext(event: E) = next(event)
    })
  }

  def map[F](f: E => F): Observable[F] = {
    new Observable[F] {
      def subscribe(observer: Observer[F]): Subscription = {
        self.subscribe(new Observer[E] {
          override def onCompleted() = observer.onCompleted()
          override def onNext(event: E) = observer.onNext(f(event))
          override def onError(error: Exception) = observer.onError(error)
        })
      }
    }
  }

}

object Observable {

  val noopSubscription = new Subscription {
    def close() {}
  }

  // only works for immediate observables!
  def asSeq[E](observable: Observable[E]): Seq[E] = {
    val result = new mutable.ArrayBuffer[E]
    observable.subscribe(new Observer[E] {
      override def onNext(event: E) { result append event }
    }).close
    result
  }

  implicit def traversable2observable[E](seq: Traversable[E]): Observable[E] = {
    new Observable[E] {
      override def subscribe(observer: Observer[E]): Subscription = {
        try {
          for (s <- seq) {
            observer.onNext(s)
          }
          observer.onCompleted()
        } catch {
          case ex: Exception => observer.onError(ex)
        }
        noopSubscription
      }
    }
  }
}
