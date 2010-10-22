package org.deler.events

trait Subscription {
  def close()
}

object NoopSubscription extends Subscription {
  def close() = {}
}

class BooleanSubscription extends Subscription {
  private var _closed = false
  def closed: Boolean = _closed;

  def close() {
    _closed = true
  }
}

class FutureSubscription extends Subscription {
  private var _subscription: Option[Subscription] = None
  private var _closed = false

  def set(subscription: Subscription) {
    if (_closed) {
      subscription.close()
    } else {
      _subscription = Some(subscription)
    }
  }

  def close() {
    _closed = true
    _subscription foreach {_.close()}
  }
}

class MutableSubscription(initial: Option[Subscription] = None) extends Subscription {
  private var _subscription: Option[Subscription] = initial

  def replace(subscription: Option[Subscription]) {
    close()
    _subscription = subscription
  }

  def close() {
    _subscription foreach {_.close()}
  }
}
