import scalanative.native._
import stdlib._, stdio._, string._
import collection.mutable

// START:RequestState
case class RequestState(url:String,
                        method:String,
                        var lastHeader:String = "None", 
                        headerMap:mutable.Map[String,String] = mutable.Map[String,String](),
                        body:String = "")
// END:RequestState

// START:parsing_defs
trait Parsing {
  import LibUV._,HttpParser._
  val requests:mutable.Map[Long,RequestState]

  def handleRequest(id:Long,handle:TCPHandle,request:RequestState):Unit

  type ConnectionState = CStruct3[Long,TCPHandle,Parser]

  val HTTP_REQUEST = 0
  val HTTP_RESPONSE = 1
  val HTTP_BOTH = 2
// END:parsing_defs

// START:bytesToString
  def bytesToString(data:Ptr[Byte],len:Long):String = {
    val bytes = new Array[Byte](len.toInt)
    var c = 0
    while (c < len) {
      bytes(c) = !(data + c)
      c += 1
    }

    new String(bytes)
  }
// END:bytesToString

// START:onURL
  def onURL(p:Ptr[Parser],data:CString,len:Long):Int = {
    val state = (!p._8).cast[Ptr[ConnectionState]]
    val message_id = !state._1
    val url = bytesToString(data,len)
    println(s"got url: $url")
    val m = !p._6
    val method = fromCString(http_method_str(m))
    println(s"method: $method ($m)")
    requests(message_id) = RequestState(url,method)
    0
  }
// END:onURL

// START:onHeaders
  def onHeaderKey(p:Ptr[Parser],data:CString,len:Long):Int = {
    val state = (!p._8).cast[Ptr[ConnectionState]]
    val message_id = !state._1
    val request = requests(message_id)

    val k = bytesToString(data,len)
    request.lastHeader = k
    requests(message_id) = request
    0
  }

  def onHeaderValue(p:Ptr[Parser],data:CString,len:Long):Int = {
    val state = (!p._8).cast[Ptr[ConnectionState]]
    val message_id = !state._1
    val request = requests(message_id)

    val v = bytesToString(data,len)
    request.headerMap(request.lastHeader) = v
    requests(message_id) = request
    0
  }
// END:onHeaders

// START:onMessageComplete
  def onMessageComplete(p:Ptr[Parser]):Int = {
    val state = (!p._8).cast[Ptr[ConnectionState]]
    val message_id = !state._1
    val tcpHandle = !state._2
    val request = requests(message_id)
    handleRequest(message_id,tcpHandle,request)
    // println(s"message ${message_id} done! $request")
    0
  }
// END:onMessageComple
}

@link("http_parser")
@extern
object HttpParser {
  // START:Parser
  type Parser = CStruct8[
    Long,   // private data
    Long,   // private data
    UShort, // major version
    UShort, // minor version
    UShort, // status (request only)
    CChar, // method
    CChar, // Error (last bit upgrade)
    Ptr[Byte] // user data
  ]
  // END:Parser

  // START:ParserSettings
  type HttpCB = CFunctionPtr1[Ptr[Parser],Int]
  type HttpDataCB = CFunctionPtr3[Ptr[Parser],CString,Long,Int]

  type ParserSettings = CStruct8[
    HttpCB, // on_message_begin
    HttpDataCB, // on_url
    HttpDataCB, // on_status
    HttpDataCB, // on_header_field
    HttpDataCB, // on_header_value
    HttpCB, // on_headers_complete
    HttpDataCB, // on_body
    HttpCB  // on_message_complete
  ]
  // END:ParserSettings

  // START:defs
  def http_parser_init(p:Ptr[Parser],parser_type:Int):Unit = extern
  def http_parser_settings_init(s:Ptr[ParserSettings]):Unit = extern
  def http_parser_execute(p:Ptr[Parser],s:Ptr[ParserSettings],data:Ptr[Byte],len:Long):Long = extern
  def http_method_str(method:CChar):CString = extern
  // END:defs
}