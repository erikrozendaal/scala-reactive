package org.deler.events.example

import java.util.UUID

import org.deler.events._

abstract case class CustomerEvent
case class CustomerRegisteredEvent(customerId: UUID, address: String) extends CustomerEvent
case class CustomerMovedEvent(customerId: UUID, address: String) extends CustomerEvent

class CustomerView extends Subject[CustomerEvent] {

  val customerId: Observed[UUID] = collect {
    case x: CustomerRegisteredEvent => x.customerId
  }.latest

  val address: Observed[String] = collect {
    case x: CustomerRegisteredEvent => x.address
    case x: CustomerMovedEvent => x.address
  }.latest

}
