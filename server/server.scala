import scalanative.native._
import stdlib._, stdio._, string._
import collection.mutable
import scala.concurrent.{Future,ExecutionContext}

// START:defs
case class Request[T](method:String, url:String, headers:Map[String,String], body:T)
case class Response[T](code:Int, description:String, headers:Map[String,String],body:T)
// END:defs

// START:route
sealed trait Route {
  val method:String
  val path:String
}
case class SyncRoute(method:String, path:String, handler:Request[String] => Response[String]) extends Route
case class AsyncRoute(method:String, path:String, handler:Request[String] => Future[Response[String]]) extends Route
// END:route

// START:setup
object Server extends Parsing with LoopExtension {
  import LibUVConstants._, LibUV._,HttpParser._
  implicit val ec = EventLoop
  val loop = EventLoop.loop
  var serial = 1L
  // val services = mutable.Map[Long,Request[String] => Route]() TODO: implement
  override val requests = mutable.Map[Long,RequestState]()
  var activeRequests = 0

  val urlCB:HttpDataCB = CFunctionPtr.fromFunction3(onURL)
  val onKeyCB:HttpDataCB = CFunctionPtr.fromFunction3(onHeaderKey)
  val onValueCB:HttpDataCB = CFunctionPtr.fromFunction3(onHeaderValue)
  val completeCB:HttpCB = CFunctionPtr.fromFunction1(onMessageComplete)

  val parserSettings = malloc(sizeof[ParserSettings]).cast[Ptr[ParserSettings]]
  http_parser_settings_init(parserSettings)
  !parserSettings._2 = urlCB
  !parserSettings._4 = onKeyCB
  !parserSettings._5 = onValueCB
  !parserSettings._8 = completeCB

  var router:Function1[Request[String],Route] = null
// END:setup

// START:init
  def init(port:Int, f:Request[String] => Route):Unit = {
    EventLoop.addExtension(this)
    router = f
    val addr = malloc(64)
    check(uv_ip4_addr(c"0.0.0.0", 9999, addr),"uv_ip4_addr")
    val server = malloc(uv_handle_size(UV_TCP_T)).cast[TCPHandle]    
    check(uv_tcp_init(loop, server), "uv_tcp_init")
    check(uv_tcp_bind(server, addr, 0), "uv_tcp_bind")
    check(uv_listen(server, 4096, connectCB), "uv_listen")
    this.activeRequests = 1
    println("running")
  }
// END:init

// START:onConnect
  def onConnect(server:TCPHandle, status:Int):Unit = {
    println(s"connection incoming with status $status")
    val client = malloc(uv_handle_size(UV_TCP_T)).cast[TCPHandle]
    val id = serial
    serial += 1

    val state = malloc(sizeof[ConnectionState]).cast[Ptr[ConnectionState]]
    !state._1 = serial
    !state._2 = client
    http_parser_init(state._3,HTTP_REQUEST)
    !(state._3)._8 = state.cast[Ptr[Byte]]
    !(client.cast[Ptr[Ptr[Byte]]]) = state.cast[Ptr[Byte]]

    printf(c"initialized handle at %x, parser at %x\n", client, state)

    check(uv_tcp_init(loop, client), "uv_tcp_init (client)")
    check(uv_accept(server, client), "uv_accept")
    check(uv_read_start(client, allocCB, readCB), "uv_read_start")
  }
// END:onConnect

  def onAlloc(handle:TCPHandle, size:CSize, buffer:Ptr[Buffer]):Unit = {
    val buf = stdlib.malloc(4096)
    buf(4095) = 0
    !buffer._1 = buf
    !buffer._2 = 4095
  }

// START:onRead
  def onRead(handle:TCPHandle, size:CSize, buffer:Ptr[Buffer]):Unit = {
    val state_ptr = handle.cast[Ptr[Ptr[ConnectionState]]]
    val parser = (!state_ptr)._3
    val message_id = !(!state_ptr)._1
    println(s"conn $message_id: read message of size $size")

    if (size < 0) {
      uv_close(handle, null)
      stdlib.free(!buffer._1)
    } else {
      http_parser_execute(parser,parserSettings,!buffer._1,size)
      stdlib.free(!buffer._1)
    }
  }
// END:onRead

// START:handleRequest
  override def handleRequest(id:Long,client:TCPHandle, r:RequestState):Unit = {
    println(s"got complete request $id: $r\n")
    val request = Request(r.method,r.url,r.headerMap.toMap,r.body)
    val route = router(request)
    route match {
      case SyncRoute(_,_,handler) => 
        val resp = handler(request)
        println("sending sync response")
        sendResponse(id,client,resp)
      case AsyncRoute(_,_,handler) => 
        val resp = handler(request)
        resp.map { r =>
          println("about to send async response")
          sendResponse(id,client,r)
        }
        println("returning immediately, async handler invoked")
    }
  }
// END:handleRequest

// START:sendResponseAsync
  def sendResponseAsync(id:Long,client:TCPHandle, resp:Future[Response[String]]):Unit = {
    resp.map { r =>
      println("async?")
      sendResponse(id,client,r)
    }
  }
// END:sendResponseAsync

// START:sendResponse
  def sendResponse(id:Long,client:TCPHandle, resp:Response[String]):Unit = {
    var respString = s"HTTP/1.1 ${resp.code} ${resp.description}\r\n"
    val headers = if (!resp.headers.contains("Content-Length")) {
      resp.headers + ("Content-Length" -> resp.body.size)
    } else { resp.headers }

    for ( (k,v) <- headers) {
      respString += s"${k}: $v\r\n"
    }
    respString += s"\r\n${resp.body}"

    val buffer = malloc(sizeof[Buffer]).cast[Ptr[Buffer]]
    Zone { implicit z =>
      val temp_resp = toCString(respString)
      val resp_len = strlen(temp_resp) + 1
      !buffer._1 = malloc(resp_len)
      !buffer._2 = resp_len
      strncpy(!buffer._1, temp_resp, resp_len)
    }
    printf(c"response buffer:\n%s\n",!buffer._1)

    val writeReq = malloc(uv_req_size(UV_WRITE_REQ_T)).cast[WriteReq]
    !writeReq = buffer.cast[Ptr[Byte]]
    check(uv_write(writeReq, client,buffer,1,writeCB),"uv_write")
  }
// END:sendResponse

  def on_write(writeReq:WriteReq, status:Int):Unit = {
    println("write completed")
    val resp_buffer = (!writeReq).cast[Ptr[Buffer]]
    stdlib.free(!resp_buffer._1)
    stdlib.free(resp_buffer.cast[Ptr[Byte]])
    stdlib.free(writeReq.cast[Ptr[Byte]])
  }

  // TODO:more/better cleanup

  val connectCB = CFunctionPtr.fromFunction2(onConnect)
  val allocCB = CFunctionPtr.fromFunction3(onAlloc)
  val readCB = CFunctionPtr.fromFunction3(onRead)
  val writeCB = CFunctionPtr.fromFunction2(on_write)
}


