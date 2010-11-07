package org.deler.reactive

import org.joda.time._
import scala.collection._
import org.slf4j.LoggerFactory

/**
 * A scheduler is used to schedule work. Various standard schedulers are provided.
 */
trait Scheduler {

  /**
   * This scheduler's concept of the current instant in time (now).
   */
  def now: Instant

  /**
   * Schedule `action` to be executed by the scheduler (the scheduler can determine when).
   *
   * @return a subscription that can be used to cancel the scheduled action.
   */
  def schedule(action: => Unit): Subscription = scheduleAt(now)(action)

  /**
   * Schedule `action` to be executed at or soon after `at`.
   *
   * @return a subscription that can be used to cancel the scheduled action.
   */
  def scheduleAt(at: Instant)(action: => Unit): Subscription = scheduleAfter(new Duration(now, at))(action)

  /**
   * Schedule `action` to be run after the specified `delay`.
   *
   * @return a subscription that can be used to cancel the scheduled action.
   */
  def scheduleAfter(delay: Duration)(action: => Unit): Subscription = scheduleAt(now plus delay)(action)

  /**
   * Schedule `action` to be executed by the scheduler (as with `schedule`). A callback
   * is passed to `action` that will reschedule `action` when invoked.
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
   * Schedule `action` to be executed by the scheduler (as with `scheduleAfter`). A callback
   * is passed to `action` that will reschedule `action` with the specified delay when invoked.
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
 * Tracks state of the CurrentThreadScheduler.
 */
object CurrentThreadScheduler extends ThreadLocal[Schedule] {
  def runImmediate(action: => Subscription): Subscription = runWithSchedule(_ => action)

  def runWithSchedule(action: Schedule => Subscription): Subscription = {
    var schedule = get
    if (schedule != null) {
      action(schedule)
    } else {
      try {
        schedule = new Schedule
        set(schedule)
        val result = action(schedule)
        runQueued(schedule)
        result
      } finally {
        remove
      }
    }

  }

  private def runQueued(schedule: Schedule) {
    schedule.dequeue match {
      case None =>
      case Some(scheduled) => {
        val delay = scheduled.time.getMillis - Scheduler.currentThread.now.getMillis
        if (delay > 0) {
          Thread.sleep(delay)
        }
        scheduled.action()
        runQueued(schedule)
      }
    }
  }
}

/**
 * Schedules actions to run as soon as possible on the calling thread. As soon as possible means:
 *
 * <ol>
 * <li>Immediately if no action is currently execution,
 * <li>or directly after the currently executing action (and any other scheduled actions) has completed.
 * </ol>
 *
 * Actions scheduled for a later time will cause the current thread to sleep.
 */
class CurrentThreadScheduler extends Scheduler {
  private val schedule = new ThreadLocal[Schedule]

  def now = new Instant

  override def scheduleAt(at: Instant)(action: => Unit): Subscription = {
    CurrentThreadScheduler.runWithSchedule {
      schedule =>
        schedule.enqueue(at, () => action)
    }
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
   * Run until the schedule is empty or we arrived at the specified `instant`.
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

class TestObserver[T](scheduler: Scheduler) extends Observer[T] {
  private var _notifications = immutable.Queue[(Int, Notification[T])]()

  def notifications = _notifications.toSeq

  override def onCompleted() {
    _notifications = _notifications enqueue (scheduler.now.getMillis.toInt, OnCompleted)
  }

  override def onError(error: Exception) {
    _notifications = _notifications enqueue (scheduler.now.getMillis.toInt, OnError(error))
  }

  override def onNext(value: T) {
    _notifications = _notifications enqueue (scheduler.now.getMillis.toInt, OnNext(value))
  }
}

class TestHotObservable[T](scheduler: Scheduler) extends Observable[T] with Observer[T] {
  private var _observers = Set[Observer[T]]()
  private var _subscriptions = Set[Subscribe]()

  def subscribe(observer: Observer[T]): Subscription = {
    val result = new Subscribe(observer)
    _subscriptions += result
    result
  }

  def subscriptions: Seq[(Int, Int)] = {
    val pairs = _subscriptions map {s => s.subscribedAt -> s.unsubscribedAt.getOrElse(-1)}
    pairs.toArray.sorted
  }

  override def onNext(value: T) {
    _observers foreach (_.onNext(value))
  }

  override def onError(error: Exception) {
    _observers foreach (_.onError(error))
  }

  override def onCompleted() {
    _observers foreach (_.onCompleted())
  }

  private class Subscribe(observer: Observer[T]) extends Subscription {
    val subscribedAt = scheduler.now.getMillis.toInt
    var unsubscribedAt: Option[Int] = None
    _observers += observer;

    def close() {
      _observers -= observer;
      unsubscribedAt = Some(scheduler.now.getMillis.toInt)
    }
  }
}

/**
 * A virtual scheduler that ensures actions scheduled inside other actions cannot occur at the same 'virtual' time.
 * The time is increased by one before scheduling, allowing you to trace casuality.
 *
 * Inspired by the <a href="http://blogs.msdn.com/b/jeffva/archive/2010/08/27/testing-rx.aspx">Testing RX</a> blog and
 * video.
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

  def run[T](action: => Observable[T], unsubscribeAt: Instant = new Instant(1000)): Seq[(Int, Notification[T])] = {
    val observer = new TestObserver[T](this)
    var observable: Observable[T] = null;
    val subscription = new MutableSubscription

    scheduleAt(new Instant(100)) {observable = action}
    scheduleAt(new Instant(200)) {subscription.set(observable.subscribe(observer))}
    scheduleAt(unsubscribeAt) {subscription.close()}

    run()

    observer.notifications
  }

  def createHotObservable[T](notifications: Seq[(Int, Notification[T])]): TestHotObservable[T] = {
    val result = new TestHotObservable[T](this)
    notifications foreach {
      case (at, notification) =>
        scheduleAt(new Instant(at))(notification.accept(result))
    }
    result
  }
}

trait LoggingScheduler extends Scheduler {
  private val log = LoggerFactory.getLogger(getClass);

  private def trace(message: => String, s: => Subscription): Subscription = {
    if (log.isTraceEnabled()) {
      val m = message
      log.trace("schedule '{}'", m);
      val subscription = s
      new Subscription {
        def close() {
          log.trace("cancel '{}'", m);
          subscription.close
        }
      }
    } else {
      s
    }
  }

  override def schedule(action: => Unit): Subscription = {
    trace("schedule", super.schedule(action))
  }

  override def scheduleAt(at: Instant)(action: => Unit): Subscription = {
    trace("scheduleAt: " + at, super.scheduleAt(at)(action))
  }

  override def scheduleAfter(delay: Duration)(action: => Unit): Subscription = {
    trace("scheduleAfter: " + delay, super.scheduleAfter(delay)(action))
  }
}

private case class ScheduledAction(time: Instant, sequence: Long, action: () => Unit) extends Ordered[ScheduledAction] {
  def compare(that: ScheduledAction) = {
    var rc = this.time.compareTo(that.time)
    if (rc == 0) {
      rc = this.sequence.compare(that.sequence)
    }
    rc
  }
}

private[reactive] class Schedule {
  private var sequence: Long = 0L
  private var schedule = SortedSet[ScheduledAction]()

  def enqueue(time: Instant, action: () => Unit): Subscription = {
    val scheduled = new ScheduledAction(time, sequence, action)
    schedule += scheduled
    sequence += 1
    new Subscription {def close() = schedule -= scheduled}
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
