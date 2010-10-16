package org.deler.events.example

import java.util.UUID

import org.deler.events.{ Observable, Observed, Observer }

abstract case class CustomerEvent
case class CustomerMovedEvent(source: UUID, address: String)

class CustomerView(customer: Observable[CustomerEvent]) extends Observer[CustomerEvent] {

	def address: Observed[String] = customer.collect { case x: CustomerMovedEvent => x.address }.observe
	
}