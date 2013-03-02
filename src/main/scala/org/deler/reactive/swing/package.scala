package org.deler.reactive

import java.awt.Component

package object swing {

  implicit class ComponentToObservableWrapper(component: Component) {
    // FIXME events: Event[A]*
    def toObservable[A](event: Event[A])(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[A] =
      ToObservable[A](component, event, scheduler)
  }
}
