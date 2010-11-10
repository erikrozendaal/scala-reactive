package org.deler.reactive

/**
 * Represents a subscription that can be cancelled by using the `close` method. Closing a subscription
 * allows the [[org.deler.reactive.Observable]] or [[org.deler.reactive.Scheduler]] to clean up any resources and
 * cancel future notifications or scheduled actions.
 *
 * Subscriptions must be idempotent (so `close` can be called multiple times without ill effects).
 *
 * Subscriptions must be thread-safe.
 */
trait Subscription {

  /**
   * Close (or cancel) the subscription. This operation is idempotent.
   */
  def close()
}

/**
 * A simple subscription that does not do anything when closed.
 */
object NullSubscription extends Subscription {
  def close() = {}
}

/**
 * A subscription that is marked as `closed` when `close` is invoked.
 */
class BooleanSubscription extends Subscription {
  private var _closed = false

  def closed: Boolean = _closed;

  def close() {
    _closed = true
  }
}

/**
 * A subscription that (optionally) contains another subscription. When this MutableSubscription is closed the
 * contained subscription is also closed. Any future replacements will also be closed automatically.
 *
 * When the contained subscription is replaced with a new subscription the contained subscription is first closed.
 */
class MutableSubscription(initial: Option[Subscription] = None) extends Subscription {
  private var _subscription: Option[Subscription] = initial
  private var _closed = false

  def set(subscription: Subscription) {
    if (_closed) {
      subscription.close()
    } else {
      _subscription foreach {_.close()}
      _subscription = Some(subscription)
    }
  }

  def close() {
    _closed = true
    _subscription foreach {_.close()}
    _subscription = None
  }
}

/**
 * A subscription that contains other subscriptions. When this CompositeSubscription is closed, all contained
 * subscriptions are also closed. A closed CompositeSubscription will also automatically close any new subscriptions
 * that are added.
 */
class CompositeSubscription extends Subscription {
  import scala.collection._

  private var _closed = false
  private val _subscriptions = mutable.Set[Subscription]()

  def add(subscription: Subscription) {
    if (_closed) {
      subscription.close()
    } else {
      _subscriptions add subscription
    }
  }

  def remove(subscription: Subscription) {
    if (_subscriptions remove subscription) {
      subscription.close()
    }
  }

  def isEmpty: Boolean = _subscriptions.isEmpty

  def close() {
    _closed = true
    _subscriptions foreach {_.close()}
    _subscriptions.clear()
  }
}
