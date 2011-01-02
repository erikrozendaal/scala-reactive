package org.deler.reactive.swing

import org.deler.reactive._

import java.awt.Component
import java.awt.event._
import javax.swing.text.JTextComponent 
import javax.swing.event.{DocumentEvent, DocumentListener}

sealed trait Event[+A]
case object MouseDown   extends Event[MouseEvent]
case object MouseUp     extends Event[MouseEvent]
case object MouseMove   extends Event[MouseEvent]
case object DocumentChanged extends Event[DocumentEvent]

private case class ToObservable[A](component: Component, event: Event[_], scheduler: Scheduler)
        extends BaseObservable[A] with ConformingObservable[A] {
  def doSubscribe(observer: Observer[A]): Closeable = {
    // FIXME can this cast be eliminated?
    def onNext(e: AnyRef) = scheduler schedule observer.onNext(e.asInstanceOf[A])

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
      case DocumentChanged =>
        component match {
          case t: JTextComponent => 
            val l = new DocumentListener {
              override def changedUpdate(e: DocumentEvent) = onNext(e)
              override def insertUpdate(e: DocumentEvent) = onNext(e)
              override def removeUpdate(e: DocumentEvent) = onNext(e) 
            }
            t.getDocument.addDocumentListener(l)
            () => t.getDocument.removeDocumentListener(l)
          case _ => () => ()
        }
    }
    new ScheduledCloseable(new ActionCloseable(close), scheduler)
  }
}
