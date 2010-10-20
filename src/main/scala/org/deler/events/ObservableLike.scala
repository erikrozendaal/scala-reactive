package org.deler.events

trait ObservableLike[+T] {

	def subscribe(observer: Observer[T]): Subscription
	
}
