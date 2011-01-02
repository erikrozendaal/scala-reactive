package org.deler.reactive

package object swing {
  import java.awt.{AWTEvent, Component}
  import org.deler.reactive.swing._

  class ComponentToObservableWrapper(component: Component) {
    def toObservable[A <: AWTEvent](event: Event[A])
      (implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] = 
        ToObservable[A](component, event, scheduler)
  }

  implicit def componentToObservable(component: Component): ComponentToObservableWrapper = 
    new ComponentToObservableWrapper(component)
}
