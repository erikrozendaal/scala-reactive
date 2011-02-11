package org.deler.reactive.http

import org.deler.reactive._
import com.ning.http.client._

private case class ToObservable(req: AsyncHttpClient#BoundRequestBuilder, scheduler: Scheduler)
        extends BaseObservable[Response] with ConformingObservable[Response] {
  def doSubscribe(observer: Observer[Response]): Closeable = {
    val f = req.execute(new AsyncCompletionHandler[Response] {
        override def onCompleted(resp: Response) = {
          observer.onNext(resp)
          observer.onCompleted()
          resp
        }
        
        override def onThrowable(t: Throwable) {
          observer.onError(t.asInstanceOf[Exception]) // FIXME
        }
    })
    new ScheduledCloseable(new ActionCloseable(() => f.cancel(true)), scheduler)
  }
}
