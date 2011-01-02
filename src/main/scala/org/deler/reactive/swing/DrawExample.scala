package org.deler.reactive.swing

import java.awt._
import java.awt.event._
import javax.swing._

object DrawExample extends Application {
  val f = new JFrame("Draw example")
  f.setSize(400, 350)
  val content = f.getContentPane
  content.setBackground(Color.white)
  f.addWindowListener(ExitListener)
  f.setVisible(true)

  val mouseDown = content.toObservable(MouseDown)
  val mouseUp   = content.toObservable(MouseUp)
  val mouseMove = content.toObservable(MouseMove)
  val drags = mouseMove.skipUntil(mouseDown).takeUntil(mouseUp)
  val path = drags.zip(drags.tail) { (prev, cur) => (prev.getPoint, cur.getPoint) }

  path.repeat.subscribe { line => 
    val (p1, p2) = line
    val g = content.getGraphics
    g.drawLine(p1.getX.intValue, p1.getY.intValue, p2.getX.intValue, p2.getY.intValue)
  }

  object ExitListener extends WindowAdapter {
    override def windowClosing(e: WindowEvent) = System.exit(0)
  }
}
