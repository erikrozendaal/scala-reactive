package org.deler.events

import scala.collection._

trait Subscription {
  def close()
}

trait Observer[-E] {
  def onCompleted() {}
  def onError(error: Exception) {}
  def onNext(event: E) {}
}

trait Observed[E] extends Subscription {
  def current: Option[E]
}

class LastObserved[E](observable: Observable[E]) extends Observed[E] {
  private var _current: Option[E] = None
  private var _subscription: Subscription = observable.subscribe(onNext = { event => _current = Some(event) })

  def current: Option[E] = _current

  def close() = _subscription.close()
}

trait Observable[+E] { self =>
  import Observable._

  def subscribe(observer: Observer[E]): Subscription

  def subscribe(onNext: E => Unit = defaultOnNext, onCompleted: () => Unit = defaultOnCompleted, onError: Exception => Unit = defaultOnError): Subscription = {
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
          override def onError(error: Exception) = observer.onError(error)
          override def onNext(event: E) = observer.onNext(f(event))
        })
      }
    }
  }

  def collect[F](pf: PartialFunction[E, F]): Observable[F] = {
    for (event <- this if pf.isDefinedAt(event)) yield pf(event)
  }

  def filter(p: E => Boolean): Observable[E] = {
    new Observable[E] {
      def subscribe(observer: Observer[E]): Subscription = {
        self.subscribe(new Observer[E] {
          override def onCompleted() = observer.onCompleted()
          override def onError(error: Exception) = observer.onError(error)
          override def onNext(event: E) = if (p(event)) observer.onNext(event)
        })
      }
    }
  }

  def observe[F >: E]: Observed[F] = new LastObserved[F](this)

}

object Observable {

  def defaultOnCompleted() {}
  def defaultOnError(error: Exception) {
    throw error
  }
  def defaultOnNext[E](event: E) {}

  val noopSubscription = new Subscription {
    def close() {}
  }

  def empty[E]: Observable[E] = {
    Seq.empty.toObservable
  }

  def singleton[E](event: E): Observable[E] = {
    Seq(event).toObservable
  }

  // only works for immediate observables!
  def toSeq[E](observable: Observable[E]): Seq[E] = {
    val result = new mutable.ArrayBuffer[E]
    observable.subscribe(new Observer[E] {
      override def onNext(event: E) { result append event }
    }).close()
    result
  }

  class TraversableWithToObservable[E](val traversable: Traversable[E]) {
    def toObservable: Observable[E] = new Observable[E] {
      override def subscribe(observer: Observer[E]): Subscription = {
        try {
          for (event <- traversable) {
            observer.onNext(event)
          }
          observer.onCompleted()
        } catch {
          case ex: Exception => observer.onError(ex)
        }
        noopSubscription
      }
    }
  }

  implicit def traversable2observable[E](traversable: Traversable[E]): TraversableWithToObservable[E] = new TraversableWithToObservable(traversable)
}
