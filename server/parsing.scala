package scala.scalanative.loop
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.collection.mutable
import scala.scalanative.libc.stdlib

case class RequestState(
    url: String,
    method: String,
    var lastHeader: String = "None",
    headerMap: mutable.Map[String, String] = mutable.Map[String, String](),
    var body: String = ""
)

object Parser {
  import HttpParser._

  val requests: mutable.Map[Long, RequestState] = mutable.Map()
  def http_method_str(i: Int)                   = c"GET"

  val HTTP_REQUEST  = 0
  val HTTP_RESPONSE = 1
  val HTTP_BOTH     = 2

  val connections = mutable.Map[Long, RequestState => Unit]()
  def initConnection(id: Long)(callback: RequestState => Unit): Ptr[Parser] = {
    connections(id) = callback
    val parser = stdlib.malloc(sizeof[Parser]).asInstanceOf[Ptr[Parser]]
    HttpParser.http_parser_init(parser, HTTP_REQUEST)
    parser._8 = id
    parser
  }

  val onUrl = new DataCB {
    def apply(p: Ptr[Parser], data: CString, len: Long): Int = {
      val url = bytesToString(data, len)
      println(s"got url: $url")
      // val state = (p._8).asInstanceOf[Ptr[ConnectionState]]
      val message_id = p._8
      val m          = p._6
      val method     = fromCString(http_method_str(m))
      println(s"method: $method ($m), request id:$message_id")
      requests(message_id) = RequestState(url, method)
      0
    }
  }

  val onHeaderKey = new DataCB {
    def apply(p: Ptr[Parser], data: CString, len: Long): Int = {
      val k = bytesToString(data, len)
      println(s"got key: $k")
      // val state = (p._8).asInstanceOf[Ptr[ConnectionState]]
      // val message_id = state._1
      val message_id = p._8
      val request    = requests(message_id)

      request.lastHeader = k
      requests(message_id) = request
      0
    }
  }

  val onHeaderValue = new DataCB {
    def apply(p: Ptr[Parser], data: CString, len: Long): Int = {
      val v = bytesToString(data, len)
      println(s"got value: $v")
      // val state = (p._8).asInstanceOf[Ptr[ConnectionState]]
      // val message_id = state._1
      val message_id = p._8
      val request    = requests(message_id)

      request.headerMap(request.lastHeader) = v
      requests(message_id) = request
      0
    }
  }

  val onBody = new DataCB {
    def apply(p: Ptr[Parser], data: CString, len: Long): Int = {
      // val state = (p._8).asInstanceOf[Ptr[ConnectionState]]
      // val message_id = state._1
      val message_id = p._8
      val request    = requests(message_id)

      val b = bytesToString(data, len)
      request.body += b
      requests(message_id) = request
      0
    }
  }

  val onComplete = new HttpCB {
    def apply(p: Ptr[Parser]): Int = {
      // val state = (p._8).asInstanceOf[Ptr[ConnectionState]]
      // val message_id = state._1
      val message_id = p._8
      val request    = requests(message_id)
      val callback   = connections(message_id)
      callback(request)
      // handleRequest(message_id,tcpHandle,request)
      // println(s"message ${message_id} done! $request")
      0
    }
  }

  def bytesToString(data: Ptr[Byte], len: Long): String = {
    val bytes = new Array[Byte](len.toInt)
    var c     = 0
    while (c < len) {
      bytes(c) = !(data + c)
      c += 1
    }

    new String(bytes)
  }

  val parserSettings =
    stdlib.malloc(sizeof[ParserSettings]).asInstanceOf[Ptr[ParserSettings]]
  http_parser_settings_init(parserSettings)
  parserSettings._2 = onUrl
  parserSettings._4 = onHeaderKey
  parserSettings._5 = onHeaderValue
  parserSettings._7 = onBody
  parserSettings._8 = onComplete
}

@extern
@link("http_parser")
object HttpParser {
  type Parser = CStruct8[
    Long,   // private data
    Long,   // private data
    UShort, // major version
    UShort, // minor version
    UShort, // status (request only)
    CChar,  // method
    CChar,  // Error (last bit upgrade)
    Long    // user data (serial #)
  ]

  type HttpCB = CFuncPtr1[Ptr[Parser], Int]
  type DataCB = CFuncPtr3[Ptr[Parser], CString, Long, Int]

  type ParserSettings = CStruct8[
    HttpCB, // on_message_begin
    DataCB, // on_url
    DataCB, // on_status
    DataCB, // on_header_field
    DataCB, // on_header_value
    HttpCB, // on_headers_complete
    DataCB, // on_body
    HttpCB  // on_message_complete
  ]

  def http_parser_init(p: Ptr[Parser], parser_type: Int): Unit = extern
  def http_parser_settings_init(s: Ptr[ParserSettings]): Unit  = extern
  def http_parser_execute(
      p: Ptr[Parser],
      s: Ptr[ParserSettings],
      data: Ptr[Byte],
      len: Long
  ): Long                                     = extern
  def http_method_str(method: CChar): CString = extern

}
