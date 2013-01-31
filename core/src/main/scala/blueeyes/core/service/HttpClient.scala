package blueeyes.core.service

import akka.dispatch.Future
import blueeyes.core.http._
import blueeyes.core.data._
import java.net.InetAddress
import org.jboss.netty.handler.codec.http.CookieEncoder
import blueeyes.json.JsonAST.JValue

trait HttpClient[A, B] extends HttpClientHandler[A, B] { self =>

  def get(path: String) = method(HttpMethods.GET, path)

  def post[C](path: String)(content: C)(implicit encoder: C <~> A): Future[HttpResponse[B]] =
    encode[C].method(HttpMethods.POST, path, Some(content))

  def put[C](path: String)(content: C)(implicit encoder: C <~> A): Future[HttpResponse[B]] =
    encode[C].method(HttpMethods.PUT, path, Some(content))

  def delete(path: String) = method(HttpMethods.DELETE, path)

  def options(path: String) = method(HttpMethods.OPTIONS, path)

  def head(path: String) = method(HttpMethods.HEAD, path)

  def connect(path: String) = method(HttpMethods.CONNECT, path)

  def trace(path: String) = method(HttpMethods.TRACE, path)

  def custom(custom: HttpMethod, path: String) = method(custom, path)

  def protocol(protocol: String): HttpClient[A, B] =
    transformRequest { request => request.withUriChanges(scheme = Some(protocol)) }

  def secure: HttpClient[A, B] = protocol("https")

  def host(host: String): HttpClient[A, B] = transformRequest { request => request.withUriChanges(host = Some(host)) }

  def port(port: Int): HttpClient[A, B] = transformRequest { request => request.withUriChanges(port = Some(port)) }

  def path(path: String): HttpClient[A, B] = transformRequest { request =>
    val originalURI = request.uri
    val uri = URI(originalURI.scheme, originalURI.userInfo, originalURI.host, originalURI.port, originalURI.path.map(path + _).orElse(Some(path)), originalURI.query, originalURI.fragment)
    HttpRequest(request.method, URI(uri.toString), request.parameters, request.headers, request.content, request.remoteHost, request.version)
  }

  def parameters(parameters: (Symbol, String)*): HttpClient[A, B] =
    transformRequest { request => request.copy(parameters = Map[Symbol, String](parameters: _*)) }

  def content[C](content: C)(implicit encoder: C <~> A): HttpClient[C, B] =
    transformRequest { request => request.copy(content = Some(encoder(content))) }

  def cookies(cookies: (String, String)*): HttpClient[A, B] = transformRequest { request =>
    val cookieEncoder = new CookieEncoder(false)
    cookies.foreach(cookie => cookieEncoder.addCookie(cookie._1, cookie._2))
    request.copy(headers = request.headers + Tuple2("Cookie", cookieEncoder.encode()))
  }

  def remoteHost(host: InetAddress): HttpClient[A, B] = transformRequest { request =>
    HttpRequest(request.method, request.uri, request.parameters, request.headers + Tuple2("X-Forwarded-For", host.getHostAddress) + Tuple2("X-Cluster-Client-Ip", host.getHostAddress), request.content, Some(host), request.version)
  }

  def header(name: String, value: String): HttpClient[A, B] = header((name, value))

  def header(h: HttpHeader): HttpClient[A, B] =
    transformRequest { request => request.copy(headers = request.headers + h) }

  def headers(h: Iterable[HttpHeader]): HttpClient[A, B] =
    transformRequest { request => request.copy(headers = request.headers ++ h) }

  def version(version: HttpVersion): HttpClient[A, B] = transformRequest { request =>
    HttpRequest(request.method, request.uri, request.parameters, request.headers, request.content, request.remoteHost, version)
  }

  def query(name: String, value: String): HttpClient[A, B] = queries((name, value))

  def queries(qs: (String, String)*): HttpClient[A, B] = transformRequest { request => addQueries(request)(qs) }

  def contentType(mimeType: MimeType): HttpClient[A, B] =
    transformRequest { request => request.copy(headers = request.headers + Tuple2("Content-Type", mimeType.value)) }

  def encode[C](implicit encoder: C <~> A): HttpClient[C, B] =
    transformRequest { request => request.copy(content = request.content map encoder)}

  def decode[C](implicit decoder: B <~> C): HttpClient[A, C] =
    transformResponse { response => response.copy(content = response.content map decoder.apply) }

  private def addQueries(request: HttpRequest[A])(queries: Iterable[(String, String)]): HttpRequest[A] = {
    import java.net.URLEncoder

    val url = request.uri.toString
    val qs  = queries.map(t => t._1 + "=" + URLEncoder.encode(t._2, "UTF-8")).mkString("&")

    val index = url.indexOf('?')

    val newUrl = (if (index >= 0) {
      if (index == url.length - 1) url + qs
      else url + "&" + qs
    }
    else url + "?" + qs)

    HttpRequest(request.method, URI(newUrl), request.parameters, request.headers, request.content, request.remoteHost, request.version)
  }

  private def method(method: HttpMethod, path: String, content: Option[A] = None): Future[HttpResponse[B]] =
    self.apply(HttpRequest(method, path,  Map(),  Map(), content))

  private def transformRequest[C](f: HttpRequest[C] => HttpRequest[A]) = new HttpClient[C, B] {
    def isDefinedAt(request: HttpRequest[C]) = self.isDefinedAt(f(request))
    def apply(request: HttpRequest[C]) = self.apply(f(request))
  }

  private def transformResponse[C](f: HttpResponse[B] => HttpResponse[C]) = new HttpClient[A, C] {
    def isDefinedAt(request: HttpRequest[A]) = self.isDefinedAt(request)
    def apply(request: HttpRequest[A]) = self.apply(request) map f
  }
}

object HttpClient extends blueeyes.bkka.AkkaDefaults {
  implicit def requestHandlerToHttpClient[A, B](h: HttpClientHandler[A, B]): HttpClient[A, B] = new HttpClient[A, B] {
    def isDefinedAt(r: HttpRequest[A]): Boolean = h.isDefinedAt(r)
    def apply(r: HttpRequest[A]): Future[HttpResponse[B]] = h.apply(r)
  }

  class EchoClient[A, B](f: HttpRequest[A] => Option[B]) extends HttpClient[A, B] {
    def apply(r: HttpRequest[A]) = Future(HttpResponse[B](content = f(r)))
    def isDefinedAt(x: HttpRequest[A]) = true
  }
}
