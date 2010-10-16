package org.deler.events

import org.joda.time._
import scala.collection._

trait Schedule {

  def now: Instant

  def schedule(action: => Unit, at: Instant = now): Subscription
  def schedule(action: => Unit, delay: Duration): Subscription

}

private class ScheduledAction(val time: Instant, val action: () => Unit) extends Ordered[ScheduledAction] {
  def compare(that: ScheduledAction) = this.time.compareTo(that.time)
}

class VirtualSchedule(initialNow: Instant = new Instant(0)) extends Schedule { self =>

  private var schedule = SortedSet[ScheduledAction]()
  private var _now = initialNow

  def now: Instant = synchronized { _now }

  def schedule(action: => Unit, at: Instant = now): Subscription = synchronized {
    val scheduledAction = new ScheduledAction(at, () => action)
    schedule += scheduledAction
    new Subscription { def close() = self.synchronized { schedule -= scheduledAction } }
  }

  def schedule(action: => Unit, delay: Duration): Subscription = synchronized {
    schedule(action, _now.plus(delay))
  }

  def run() {
    val actionToExecute = synchronized {
      val action = schedule.firstOption
      if (action.isDefined) {
        schedule = schedule.tail
        if (action.get.time isAfter _now) {
          _now = action.get.time
        }
      }
      action
    }
    if (actionToExecute.isDefined) {
      actionToExecute.get.action()
      run()
    }
  }

}
