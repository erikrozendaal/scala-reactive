package org.deler.reactive

package object http {
  import org.deler.reactive.http._
  import com.ning.http.client._

  class RequestToObservableWrapper(req: AsyncHttpClient#BoundRequestBuilder) {
    def toObservable(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[Response] = 
        ToObservable(req, scheduler)
  }

  implicit def requestToObservable(req: AsyncHttpClient#BoundRequestBuilder): RequestToObservableWrapper = 
    new RequestToObservableWrapper(req)
}
