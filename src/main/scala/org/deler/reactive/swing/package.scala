package org.deler.reactive

package object swing {
  import java.awt.Component
  import org.deler.reactive.swing._

  class ComponentToObservableWrapper(component: Component) {
    // FIXME events: Event[A]*
    def toObservable[A](event: Event[A])
      (implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] = 
        ToObservable[A](component, event, scheduler)
  }

  implicit def componentToObservable(component: Component): ComponentToObservableWrapper = 
    new ComponentToObservableWrapper(component)
}
