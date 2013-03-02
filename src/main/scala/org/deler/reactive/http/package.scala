package org.deler.reactive

package object http {
  import com.ning.http.client._

  implicit class RequestToObservableWrapper(req: AsyncHttpClient#BoundRequestBuilder) {
    def toObservable(implicit scheduler: Scheduler = Scheduler.currentThread): Observable[Response] =
        ToObservable(req, scheduler)
  }
}
