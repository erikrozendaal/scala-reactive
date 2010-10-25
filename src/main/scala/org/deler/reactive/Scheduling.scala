package org.deler.reactive

import org.joda.time._
import scala.collection._

/**
 * A scheduler is used to schedule work. Various standard schedulers are provided.
 */
trait Scheduler {

  /**
   * The scheduler's concept of the current instant in time (now).
   */
  def now: Instant

  /**
   * Schedule <code>action</code> to be executed by the scheduler (the scheduler can determine when).
   *
   * @return a subscription that can be used to cancel the scheduled action.
   */
  def schedule(action: => Unit): Subscription = scheduleAt(now)(action)

  /**
   * Schedule <code>action</code> to be executed at or soon after <code>at</code>.
   *
   * @return a subscription that can be used to cancel the scheduled action.
   */
  def scheduleAt(at: Instant)(action: => Unit): Subscription = scheduleAfter(new Duration(now, at))(action)

  /**
   * Schedule <code>action</code> to be run after the specified <code>delay</code>.
   *
   * @return a subscription that can be used to cancel the scheduled action.
   */
  def scheduleAfter(delay: Duration)(action: => Unit): Subscription = scheduleAt(now plus delay)(action)

  /**
   * Schedule <code>action</code> to be executed by the scheduler (as with <code>schedule</code>). A callback
   * is passed to <code>action</code> that will reschedule <code>action</code> when invoked.
   *
   * @return a subscription that can be used to cancel the scheduled action and any rescheduled actions.
   */
  def scheduleRecursive(action: (() => Unit) => Unit): Subscription = {
    val result = new CompositeSubscription
    def self() {
      val subscription = new FutureSubscription
      result.add(subscription)
      subscription.set(schedule {
        result.remove(subscription)
        action(self)
      })
    }
    self()
    result
  }

  /**
   * Schedule <code>action</code> to be executed by the scheduler (as with <code>scheduleAfter</code>). A callback
   * is passed to <code>action</code> that will reschedule <code>action</code> with the specified delay when invoked.
   *
   * @return a subscription that can be used to cancel the scheduled action and any rescheduled actions.
   */
  def scheduleRecursiveAfter(delay: Duration)(action: (Duration => Unit) => Unit): Subscription = {
    val result = new CompositeSubscription
    def self(delay: Duration) {
      val subscription = new FutureSubscription
      result.add(subscription)
      subscription.set(scheduleAfter(delay) {
        result.remove(subscription)
        action(self)
      })
    }
    self(delay)
    result
  }
}

object Scheduler {
  val immediate: Scheduler = new ImmediateScheduler
  val currentThread: Scheduler = new CurrentThreadScheduler
}

/**
 * Scheduler that invokes the specified action immediately. Actions scheduled for the future execution will block
 * the caller until the specified moment has arrived and the scheduled action has completed.
 */
class ImmediateScheduler extends Scheduler {
  def now = new Instant

  override def schedule(action: => Unit): Subscription = {
    action
    NullSubscription
  }

  override def scheduleAfter(delay: Duration)(action: => Unit): Subscription = {
    if (delay.getMillis > 0) {
      Thread.sleep(delay.getMillis)
    }
    schedule(action)
  }
}

/**
 * A scheduler that doesn't run actions until activated and then runs through the actions as quickly as possible,
 * adjusting virtual time as needed.
 */
class VirtualScheduler(initialNow: Instant = new Instant(100)) extends Scheduler {
  self =>

  private var scheduleAt = new Schedule
  protected var _now = initialNow

  def now: Instant = _now

  override def scheduleAt(at: Instant)(action: => Unit): Subscription = {
    scheduleAt enqueue (at, () => action)
  }

  protected def runScheduled(scheduled: ScheduledAction) {
    if (scheduled.time isAfter _now) {
      _now = scheduled.time
    }
    scheduled.action()
  }

  /**
   * Run until the schedule is empty.
   */
  def run() {
    def loop() {
      scheduleAt.dequeue match {
        case None =>
        case Some(scheduled) => {
          runScheduled(scheduled);
          loop()
        }
      }
    }
    loop()
  }

  /**
   * Run until the schedule is empty or we arrived at the specified <code>instant</code>.
   */
  def runTo(instant: Instant) {
    def loop() {
      scheduleAt.dequeue(instant) match {
        case None => _now = instant
        case Some(scheduled) => {
          runScheduled(scheduled)
          runTo(instant)
        }
      }
    }
    loop()
  }

}

/**
 * A virtual scheduler that ensures actions scheduled inside other actions cannot occur at the same 'virtual' time.
 * The time is increased by one before scheduling, allowing you to trace casuality.
 */
class TestScheduler extends VirtualScheduler(new Instant(0)) {
  override def scheduleAt(at: Instant)(action: => Unit): Subscription = {
    val t = if (!(at isAfter now)) {
      now plus 1
    } else {
      at
    }
    super.scheduleAt(t)(action)
  }

}

private class ScheduledAction(val time: Instant, val sequence: Long, val action: () => Unit) extends Ordered[ScheduledAction] {
  def compare(that: ScheduledAction) = {
    var rc = this.time.compareTo(that.time)
    if (rc == 0) {
      if (this.sequence < that.sequence) {
        rc = -1;
      } else if (this.sequence > that.sequence) {
        rc = 1;
      }
    }
    rc
  }
}

private[reactive] class Schedule {
  self =>

  private var sequence: Long = 0L
  private var schedule = SortedSet[ScheduledAction]()

  def enqueue(time: Instant, action: () => Unit): Subscription = {
    val scheduled = new ScheduledAction(time, sequence, action)
    schedule += scheduled
    sequence += 1
    new Subscription {def close() = {schedule -= scheduled}}
  }

  def dequeue: Option[ScheduledAction] = {
    if (schedule.isEmpty) {
      None
    } else {
      val result = schedule.head
      schedule = schedule.tail
      Some(result)
    }
  }

  def dequeue(until: Instant): Option[ScheduledAction] = {
    if (!schedule.isEmpty && (schedule.head.time isBefore until)) {
      dequeue
    } else {
      None
    }
  }
}
