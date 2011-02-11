package org.deler.reactive.http

import java.awt.{BorderLayout, Color}
import java.awt.event._
import javax.swing._
import org.deler.reactive._
import org.deler.reactive.Observable._
import org.deler.reactive.swing._
import com.ning.http.client._
import net.liftweb.json._
import net.liftweb.json.JsonAST._
import org.joda.time.Duration

object TwitterSearchExample extends Application {
  val f = new JFrame("Twitter search example")
  f.setSize(400, 350)
  val content = f.getContentPane
  content.setBackground(Color.white)
  val query = new JTextField 
  val view = new ListView
  content.add(BorderLayout.NORTH, query)
  content.add(BorderLayout.CENTER, view)
  f.addWindowListener(ExitListener)
  f.pack
  f.setVisible(true)
  val http = new AsyncHttpClient
  implicit val formats = DefaultFormats

  def searchTwitter(term: String): Observable[List[String]] = {
    val url = "http://search.twitter.com/search.json?q=" + term
    http.prepareGet(url).toObservable.map { resp => 
      val json = JsonParser.parse(resp.getResponseBody)
      for { JField("text", JString(t)) <- json } yield t
    }
  }

  val input = query.toObservable(DocumentChanged)
    .throttle(new Duration(250))
    .map(e => e.getDocument.getText(0, e.getDocument.getLength))
    .distinctUntilChanged

  val results = input.map(searchTwitter).switch

  results.subscribe { rs => view.changed(rs) }

  class ListView extends JTextArea("", 30, 80) {
    setEditable(false)

    def changed(items: List[String]) = setText(items.mkString("\n\n"))
  }

  object ExitListener extends WindowAdapter {
    override def windowClosing(e: WindowEvent) = System.exit(0)
  }
}
