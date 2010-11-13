package org.deler.reactive

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection._

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
  @volatile private var _closed = false

  def closed: Boolean = _closed;

  def close() {
    _closed = true
  }
}

/**
 * A subscription that executes `action` when closed for the first time.
 */
class ActionSubscription(action: () => Unit) extends Subscription {
  private val _closed = new AtomicBoolean(false)

  def close() {
    if (_closed.compareAndSet(false, true)) {
      action()
    }
  }
}

/**
 * Schedules the closing of `target` on the specified `scheduler` when closed.
 */
class ScheduledSubscription(target: Subscription, scheduler: Scheduler)
        extends ActionSubscription(() => scheduler schedule target.close())

/**
 * A subscription that (optionally) contains another subscription. When this MutableSubscription is closed the
 * contained subscription is also closed. Any future replacements will also be closed automatically.
 *
 * When the contained subscription is replaced with a new subscription the contained subscription is first closed.
 */
class MutableSubscription(initial: Option[Subscription] = None) extends Subscription {
  private var _subscription: Option[Subscription] = initial
  @volatile private var _closed = false

  def this(subscription: Subscription) = this (Some(subscription))

  /**
   * Closes and clears the contained subscription (if any) without closing this.
   */
  def clear(): Unit = synchronized {
    _subscription foreach {_.close()}
    _subscription = None
  }

  /**
   * First `clear`s this subscription before invoking `delegate` to set a new subscription. When this subscription
   * is already closed the `delegate` is not invoked!
   *
   * @note not synchronized to avoid running `delegate` with this subscription locked!
   */
  def clearAndSet(delegate: => Subscription) {
    if (_closed)
      return

    clear()
    val subscription = delegate
    set(subscription)
  }

  /**
   * Closes the contained subscription (if any) and replaces it with `subscription`. Immediately closes `subscription`
   * if this subscription is already closed.
   */
  def set(subscription: Subscription): Unit = synchronized {
    if (_closed) {
      subscription.close()
    } else {
      _subscription foreach {_.close()}
      _subscription = Some(subscription)
    }
  }

  def close(): Unit = synchronized {
    if (_closed)
      return

    _closed = true
    clear()
  }
}

/**
 * A subscription that contains other subscriptions. When this CompositeSubscription is closed, all contained
 * subscriptions are also closed. A closed CompositeSubscription will also automatically close any new subscriptions
 * that are added.
 */
class CompositeSubscription(initial: Subscription*) extends Subscription
        with generic.Growable[Subscription]
        with generic.Shrinkable[Subscription] {

  private var _closed = false
  private val _subscriptions = mutable.Set[Subscription](initial: _*)

  /**
   * Adds the `subscription` to this CompositeSubscription or closes the `subscription` if this CompositeSubscription
   * is already closed.
   */
  def +=(subscription: Subscription): this.type = {
    synchronized {
      if (_closed) {
        subscription.close()
      } else {
        _subscriptions add subscription
      }
    }
    this
  }

  /**
   * Removes and closes the `subscription`. Does nothing when the `subscription` is not part of this
   * CompositeSubscription.
   */
  def -=(subscription: Subscription): this.type = {
    synchronized {
      if (_subscriptions remove subscription) {
        subscription.close()
      }
    }
    this
  }

  /**
   * Closes and clears the contained subscriptions (if any) without closing this.
   */
  def clear(): Unit = synchronized {
    _subscriptions foreach {_.close()}
    _subscriptions.clear()
  }

  /**
   * Closes all contained Subscriptions and removes them from this CompositeSubscription. Then closes this.
   */
  def close(): Unit = synchronized {
    if (_closed)
      return

    _closed = true
    clear()
  }
}
