package org.deler.reactive.swing

import org.deler.reactive._

import java.awt.{AWTEvent, Component}
import java.awt.event._

sealed trait Event[+A <: AWTEvent]
case object MouseDown extends Event[MouseEvent]
case object MouseUp   extends Event[MouseEvent]
case object MouseMove extends Event[MouseEvent]

private case class ToObservable[A <: AWTEvent](component: Component, event: Event[_ <: AWTEvent], scheduler: Scheduler)
        extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    // FIXME can this cast be eliminated?
    def onNext(e: AWTEvent) = scheduler schedule observer.onNext(e.asInstanceOf[A])

    val close = event match {
      case MouseDown => 
        val l = new MouseAdapter {
          override def mousePressed(e: MouseEvent) = onNext(e)
        }
        component.addMouseListener(l)
        () => component.removeMouseListener(l)
      case MouseUp => 
        val l = new MouseAdapter {
          override def mouseReleased(e: MouseEvent) = onNext(e)
        }
        component.addMouseListener(l)
        () => component.removeMouseListener(l)
      case MouseMove => 
        val l = new MouseMotionListener {
          override def mouseDragged(e: MouseEvent) = onNext(e)
          override def mouseMoved(e: MouseEvent) = onNext(e)
        }
        component.addMouseMotionListener(l)
        () => component.removeMouseMotionListener(l)
    }
    new ScheduledCloseable(new ActionCloseable(close), scheduler)
  }
}
