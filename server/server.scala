package scala.scalanative.loop
import scala.scalanative.unsafe._
import scala.collection.mutable
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.concurrent._

object Server {
  import LibUV._, LibUVConstants._
  import Parser._

  var serial = 0

  val HTTP_REQUEST  = 0
  val HTTP_RESPONSE = 1
  val HTTP_BOTH     = 2

  type ConnectionState = CStruct3[Long, LibUV.TCPHandle, Ptr[HttpParser.Parser]]
  type RequestHandler  = (RequestState, TCPHandle) => Unit
  type WriteState      = (Promise[Unit], Ptr[Buffer])

  val listeners = mutable.Map[Long, RequestHandler]()
  val writes    = mutable.Map[Long, WriteState]()

  def respond(
      client: TCPHandle,
      code: Int,
      desc: String,
      headers: Seq[(String, String)],
      body: String
  ): Future[Unit] = {
    var resp = new StringBuilder(s"HTTP/1.1 $code $desc\r\n")
    for (h <- headers) {
      resp ++= s"${h._1}: ${h._2}\r\n"
    }
    resp ++= "\r\n" + body
    write(client, resp.toString)
  }

  def write(client: TCPHandle, s: String): Future[Unit] = {
    val buffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    Zone { implicit z =>
      val temp_resp = toCString(s)
      val resp_len  = strlen(temp_resp) + 1
      buffer._1 = malloc(resp_len)
      buffer._2 = resp_len
      strncpy(buffer._1, temp_resp, resp_len)
    }

    val writeReq = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
    serial += 1
    val id = serial
    !(writeReq.asInstanceOf[Ptr[Long]]) = id
    val promise = Promise[Unit]
    writes(id) = (promise, buffer)
    check(uv_write(writeReq, client, buffer, 1, onWrite), "uv_write")
    promise.future
  }

  def close(client: TCPHandle): Unit = {
    println(s"closing connection $client")
    uv_close(client, null)
  }

  def init(port: Int)(handler: RequestHandler): Unit = {
    listeners(port) = handler
    val addr = malloc(64)
    check(uv_ip4_addr(c"0.0.0.0", 9999, addr), "uv_ip4_addr")
    val server = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    check(uv_tcp_init(EventLoop.loop, server), "uv_tcp_init")
    !(server.asInstanceOf[Ptr[Long]]) = port
    check(uv_tcp_bind(server, addr, 0), "uv_tcp_bind")
    check(uv_listen(server, 4096, onConnect), "uv_listen")
    println("running")
    println(s"callbacks: ${onConnect}, ${onAlloc}, ${onRead}, ${onWrite}")
  }

  val onConnect = new ConnectionCB {
    def apply(server: TCPHandle, status: Int): Unit = {
      val port = !(server.asInstanceOf[Ptr[Long]])
      println(s"connection incoming on port $port with status $status")
      val client  = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
      val handler = listeners(port)

      val state = malloc(sizeof[ConnectionState])
        .asInstanceOf[Ptr[ConnectionState]]
      serial += 1
      val id = serial

      state._1 = id
      state._2 = client
      state._3 = Parser.initConnection(id) { r =>
        handler(r, client)
      }
      !(client.asInstanceOf[Ptr[Ptr[Byte]]]) = state.asInstanceOf[Ptr[Byte]]

      uv_tcp_init(EventLoop.loop, client)
      uv_accept(server, client)
      uv_read_start(client, onAlloc, onRead)
    }
  }

  val onAlloc = new AllocCB {
    def apply(handle: TCPHandle, size: CSize, buffer: Ptr[Buffer]): Unit = {
      val buf = malloc(4096)
      buf(4095) = 0
      buffer._1 = buf
      buffer._2 = 4095
    }
  }

  val onRead = new ReadCB {
    def apply(handle: TCPHandle, size: CSize, buffer: Ptr[Buffer]): Unit = {
      val state_ptr  = handle.asInstanceOf[Ptr[Ptr[ConnectionState]]]
      val parser     = (!state_ptr)._3
      val message_id = (!state_ptr)._1
      println(s"conn $message_id: read message of size $size")

      if (size < 0) {
        uv_close(handle, null)
        free(buffer._1)
      } else {
        HttpParser.http_parser_execute(parser, parserSettings, buffer._1, size)
        free(buffer._1)
      }
    }
  }

  val onWrite = new WriteCB {
    def apply(writeReq: WriteReq, status: Int): Unit = {
      val id = !(writeReq.asInstanceOf[Ptr[Long]])
      println(s"write $id completed")
      val (promise, buffer) = writes.remove(id).get
      free(buffer._1)
      free(buffer.asInstanceOf[Ptr[Byte]])
      free(writeReq.asInstanceOf[Ptr[Byte]])
      promise.success(())
    }
  }
}
