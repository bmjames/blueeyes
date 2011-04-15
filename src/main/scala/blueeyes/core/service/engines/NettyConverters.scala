package blueeyes.core.service.engines

import org.jboss.netty.handler.codec.http.{HttpHeaders => NettyHttpHeaders, QueryStringDecoder, HttpResponseStatus, DefaultHttpResponse, HttpMethod => NettyHttpMethod, HttpResponse => NettyHttpResponse, HttpVersion => NettyHttpVersion, HttpRequest => NettyHttpRequest}

import blueeyes.core.http._
import scala.collection.JavaConversions._
import java.io.ByteArrayOutputStream
import blueeyes.core.http.HttpHeaders._
import blueeyes.core.http.HttpVersions._
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}
import java.net.{SocketAddress, InetSocketAddress}
import blueeyes.concurrent.FutureDeliveryStrategySequential
import blueeyes.core.data.{Chunk, MemoryChunk}

trait NettyConverters{
  implicit def fromNettyVersion(version: NettyHttpVersion): HttpVersion = version.getText.toUpperCase match {
    case "HTTP/1.0" => `HTTP/1.0`
    case "HTTP/1.1" => `HTTP/1.1`
  }
  
  implicit def toNettyVersion(version: HttpVersion): NettyHttpVersion   = NettyHttpVersion.valueOf(version.value)
  
  implicit def toNettyStatus(status : HttpStatus) : HttpResponseStatus = status.code match {
    case HttpStatusCodes.OK => HttpResponseStatus.OK
    case _ => new HttpResponseStatus(status.code.value, status.reason)
  }
  
  implicit def fromNettyMethod(method: NettyHttpMethod): HttpMethod = method match{
    case NettyHttpMethod.GET      => HttpMethods.GET
    case NettyHttpMethod.PUT      => HttpMethods.PUT
    case NettyHttpMethod.POST     => HttpMethods.POST
    case NettyHttpMethod.DELETE   => HttpMethods.DELETE
    case NettyHttpMethod.OPTIONS  => HttpMethods.OPTIONS
    case NettyHttpMethod.HEAD     => HttpMethods.HEAD
    case NettyHttpMethod.CONNECT  => HttpMethods.CONNECT
    case NettyHttpMethod.TRACE    => HttpMethods.TRACE
    case NettyHttpMethod.PATCH    => HttpMethods.PATCH
    case _ => HttpMethods.CUSTOM(method.getName)
  }

  implicit def toNettyResponse(response: HttpResponse[Chunk]): NettyHttpResponse = {
    val nettyResponse = new DefaultHttpResponse(response.version, response.status)

    response.headers.foreach(header => nettyResponse.setHeader(header._1, header._2))
    response.content.foreach(v => nettyResponse.setHeader(NettyHttpHeaders.Names.TRANSFER_ENCODING, "chunked"))

    nettyResponse
  }

  implicit def fromNettyRequest(request: NettyHttpRequest, remoteAddres: SocketAddress): HttpRequest[Chunk] = {
    val parameters          = getParameters(request.getUri())
    val headers             = buildHeaders(request.getHeaders())
    val nettyContent        = request.getContent()
    val content             = if (nettyContent.readable()) Some(new MemoryChunk(extractContent(nettyContent))) else None
  
    val xforwarded: Option[HttpHeaders.`X-Forwarded-For`] = (for (`X-Forwarded-For`(value) <- headers) yield `X-Forwarded-For`(value: _*)).headOption
    val remoteHost = xforwarded.flatMap(_.ips.toList.headOption.map(_.ip)).orElse(Some(remoteAddres).collect { case x: InetSocketAddress => x.getAddress })

    HttpRequest(request.getMethod, request.getUri(), parameters, headers, content, remoteHost, fromNettyVersion(request.getProtocolVersion()))
  }

  private def extractContent(content: ChannelBuffer) = {
    val stream = new ByteArrayOutputStream()
    try {
      content.readBytes(stream, content.readableBytes)
      stream.toByteArray
    }
    finally stream.close
  }

  private def getParameters(uri: String) = {
    val queryStringDecoder  = new QueryStringDecoder(uri)
    queryStringDecoder.getParameters().map(param => (Symbol(param._1), if (!param._2.isEmpty) param._2.head else "")).toMap
  }

  private def buildHeaders(nettyHeaders: java.util.List[java.util.Map.Entry[java.lang.String,java.lang.String]]) = {
    var headers = Map[String, String]()

    nettyHeaders.foreach(header => {
      val key   = header.getKey()
      val value = header.getValue()

      val values = headers.get(key).map(_ + "," + value).getOrElse(value)
      headers = headers + Tuple2(key, values)
    })
    headers
  }
}