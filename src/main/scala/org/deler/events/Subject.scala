package org.deler.events

import scala.collection._

class Subject[E] extends Observable[E] with Observer[E] {

  private var subscriptions = Set[SubjectSubscription]()
  protected var completed = false
  protected var error: Option[Exception] = None;

  def subscribe(observer: Observer[E]): Subscription = {
    new SubjectSubscription(observer)
  }
  
  override def onCompleted() {
	if (completed) return
    completed = true
    dispatch(_.onCompleted())
  }

  override def onNext(event: E) {
    if (completed) return
    dispatch(_.onNext(event))
  }

  override def onError(error: Exception) {
    if (completed) return
    completed = true
    this.error = Some(error)
    dispatch(_.onError(error))
  }

  private def dispatch(f: Observer[E] => Unit) {
	subscriptions foreach { subscription => f(subscription.observer) }
  }

  private class SubjectSubscription(val observer: Observer[E]) extends Subscription {
    subscriptions += this

    def close {
      subscriptions -= this
    }
  }

}

class ReplaySubject[E] extends Subject[E] {

	private var events = immutable.Queue[E]()
	
	override def subscribe(observer: Observer[E]): Subscription = {
		val subscription = super.subscribe(observer)
		events foreach { observer.onNext(_) }
		if (error.isDefined) observer.onError(error.get)
		else if (completed) observer.onCompleted()
		subscription
	}
	
	override def onNext(event: E) {
		super.onNext(event)
		if (!completed) events += event
	}
}
