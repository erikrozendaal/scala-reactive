package org.deler.events

import org.joda.time._
import scala.collection._

trait Scheduler {
  def now: Instant

  def schedule(action: => Unit): Subscription = scheduleAt(now)(action)
  def scheduleAt(at: Instant)(action: => Unit): Subscription = scheduleAfter(new Duration(now, at))(action)
  def scheduleAfter(delay: Duration)(action: => Unit): Subscription = scheduleAt(now plus delay)(action)
}

object Scheduler {
  val immediate: Scheduler = new ImmediateScheduler
  val currentThread: Scheduler = new CurrentThreadScheduler
}

/**
 * Scheduler that invokes the specified action immediately. Actions scheduled for the future will be block
 * the caller until the scheduled action has run.
 */
class ImmediateScheduler extends Scheduler {

  def now = new Instant

  override def schedule(action: => Unit): Subscription = {
    action
    Observable.noopSubscription
  }

  override def scheduleAfter(delay: Duration)(action: => Unit): Subscription = {
    if (delay.getMillis > 0) {
      Thread.sleep(delay.getMillis)
    }
    schedule(action)
  }

}

private[this] class ScheduledAction(val time: Instant, val sequence: Long, val action: () => Unit) extends Ordered[ScheduledAction] {
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

private[events] class Schedule { self =>

  private var sequence: Long = 0L
  private var schedule = SortedSet[ScheduledAction]()

  def enqueue(time: Instant, action: () => Unit): Subscription = {
    val scheduled = new ScheduledAction(time, sequence, action)
    schedule += scheduled
    sequence += 1
    new Subscription { def close() = { schedule -= scheduled } }
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

  def dequeue(noLaterThan: Instant): Option[ScheduledAction] = {
    if (schedule.isEmpty || (schedule.head.time isAfter noLaterThan)) {
      None
    } else {
      dequeue
    }
  }
}

class VirtualScheduler(initialNow: Instant = new Instant(0)) extends Scheduler { self =>

  private var scheduleAt = new Schedule
  private var _now = initialNow

  def now: Instant = _now

  override def scheduleAt(at: Instant)(action: => Unit): Subscription = {
    scheduleAt enqueue (at, () => action)
  }

  def run() {
    scheduleAt.dequeue match {
      case None =>
      case Some(scheduled) => {
        if (scheduled.time isAfter _now) {
          _now = scheduled.time
        }
        scheduled.action()
        run()
      }
    }
  }

  def runTo(instant: Instant) {
    scheduleAt.dequeue(instant) match {
      case None => _now = instant
      case Some(scheduled) => {
        if (scheduled.time isAfter _now) {
          _now = scheduled.time
        }
        scheduled.action()
        runTo(instant)
      }
    }
  }

}
