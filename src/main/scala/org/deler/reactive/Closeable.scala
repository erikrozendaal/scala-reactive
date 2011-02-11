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
trait Closeable {

  /**
   * Close (or cancel) the subscription. This operation is idempotent.
   */
  def close()
}

/**
 * A simple subscription that does not do anything when closed.
 */
object NullCloseable extends Closeable {
  def close() = {}
}

/**
 * A subscription that is marked as `closed` when `close` is invoked.
 */
class BooleanCloseable extends Closeable {
  @volatile private var _closed = false

  def closed: Boolean = _closed;

  def close() {
    _closed = true
  }
}

/**
 * A subscription that executes `action` when closed for the first time.
 */
class ActionCloseable(action: () => Unit) extends Closeable {
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
class ScheduledCloseable(target: Closeable, scheduler: Scheduler)
        extends ActionCloseable(() => scheduler schedule target.close())

/**
 * A subscription that (optionally) contains another subscription. When this MutableCloseable is closed the
 * contained subscription is also closed. Any future replacements will also be closed automatically.
 *
 * When the contained subscription is replaced with a new subscription the contained subscription is first closed.
 */
class MutableCloseable(initial: Option[Closeable] = None) extends Closeable {
  private var _subscription: Option[Closeable] = initial
  @volatile private var _closed = false

  def this(subscription: Closeable) = this (Some(subscription))

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
  def clearAndSet(delegate: => Closeable) {
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
  def set(subscription: Closeable): Unit = synchronized {
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
 * A subscription that contains other subscriptions. When this CompositeCloseable is closed, all contained
 * subscriptions are also closed. A closed CompositeCloseable will also automatically close any new subscriptions
 * that are added.
 */
class CompositeCloseable(initial: Closeable*) extends Closeable
        with generic.Growable[Closeable]
        with generic.Shrinkable[Closeable] {

  private var _closed = false
  private val _subscriptions = mutable.Set[Closeable](initial: _*)

  /**
   * Adds the `subscription` to this CompositeCloseable or closes the `subscription` if this CompositeCloseable
   * is already closed.
   */
  def +=(subscription: Closeable): this.type = {
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
   * CompositeCloseable.
   */
  def -=(subscription: Closeable): this.type = {
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
   * Closes all contained Subscriptions and removes them from this CompositeCloseable. Then closes this.
   */
  def close(): Unit = synchronized {
    if (_closed)
      return

    _closed = true
    clear()
  }

  /**
   * Synchronizes an action only if subscriptions are not closed.
   */
  def synchronizeIfNotClosed(action: => Unit): Unit = synchronized {
    if (!_closed) action
  }

  /**
   * Synchronizes an action only if subscriptions are not closed, then closes all subscriptions.
   */
  def synchronizeClose(action: => Unit): Unit = synchronizeIfNotClosed {
    action
    close()
  }
}
