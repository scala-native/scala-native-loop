import scalanative.native._
import string._, stdlib._, stdio._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.{Try, Success, Failure}
import scala.collection.mutable
import scala.collection.mutable.HashMap

case class ResponseState(var headers:HashMap[String,String] = HashMap(),var body:String = "")

object Curl extends LoopExtension {
  import LibCurl._
  import CurlConstants._
  import LibUV._
  import LibUVConstants._

  var req_count = 0L
  val req_promises:mutable.Map[Long,Promise[ResponseState]] = mutable.HashMap.empty
  var activeRequests = 0

  // START:get
  def get(url:CString, headers:Seq[String] = Seq.empty)
         (implicit ec:ExecutionContext):Future[ResponseState] = {
    if (!CurlInternals.initialized) {
      CurlInternals.init()
      EventLoop.addExtension(this)
    }

    req_count += 1
    activeRequests += 1
    val req_id = req_count
    val promise = Promise[ResponseState]()
    req_promises(req_id) = promise

    CurlInternals.beginRequest(req_id, url, headers)

    promise.future
  }
  // END:get

  // START:complete_request
  def complete_request(reqId:Long, data:ResponseState):Unit = {
    // val reqId = !request._4
    activeRequests -= 1
    println(s"completing reqId ${reqId}")
    val promise = Curl.req_promises.remove(reqId).get
    promise.success(data)
  }
  // END:complete_request

  def cleanup():Unit = {
    if (CurlInternals.initialized) {
      println("cleaning up internals")
      CurlInternals.cleanup_requests()
    }
  }
}

object CurlInternals {
  import LibUV._
  import LibUVConstants._
  import LibCurl._
  import CurlConstants._

  var initialized = false
  var loop:Loop = null
  var multi:MultiCurl = null
  var timer_handle:TimerHandle = null

  val requests = mutable.HashMap[Long,ResponseState]()

  // START:addHeaders
  def addHeaders(curl:Curl, headers:Seq[String]):Ptr[CurlSList] = {
      var slist:Ptr[CurlSList] = null
      for (h <- headers) {
          addHeader(slist,h)
      }
      easy_setopt(curl, HTTPHEADER, slist.cast[Ptr[Byte]])
      slist
  }

  def addHeader(slist:Ptr[CurlSList], header:String):Ptr[CurlSList] = Zone { implicit z =>
      slist_append(slist,toCString(header))
  }
  // END:addHeaders

  // START:beginRequest
  def beginRequest(reqId:Long, url:CString, headers:Seq[String]):Unit = {
    val curlHandle = easy_init()
    val req_id_ptr = malloc(sizeof[Long]).cast[Ptr[Long]]
    !req_id_ptr = reqId
    requests(reqId) = ResponseState()

    easy_setopt(curlHandle, URL, url)
    easy_setopt(curlHandle, WRITECALLBACK, writeCB)
    easy_setopt(curlHandle, WRITEDATA, req_id_ptr.cast[Ptr[Byte]])
    easy_setopt(curlHandle, HEADERCALLBACK, headerCB)
    easy_setopt(curlHandle, HEADERDATA, req_id_ptr.cast[Ptr[Byte]])
    easy_setopt(curlHandle, PRIVATEDATA, req_id_ptr.cast[Ptr[Byte]])

    multi_add_handle(multi, curlHandle)
  }
  // END:beginRequest

    def bufferToString(ptr:Ptr[Byte], size:CSize, nmemb: CSize):String = {
        val byteSize = size * nmemb
        val buffer = malloc(byteSize + 1)
        strncpy(buffer,ptr,byteSize + 1)
        val res = fromCString(buffer)
        free(buffer)
        return(res)
    }

    def writeData(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Byte]): CSize = {
        val serial = !(data.cast[Ptr[Long]])
        val len = stackalloc[Double]
        !len = 0
        val strData = bufferToString(ptr,size,nmemb)
        println(s"req $serial: got data of size ${size} x ${nmemb}")

        val resp = requests(serial)
        resp.body = resp.body + strData
        requests(serial) = resp

        return size * nmemb
    }

    val statusLine =  raw"^.+? (\d+) (.+)$$".r
    val headerLine = raw"^([^:]+): (.*)$$".r

    def writeHeader(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Byte]): CSize = {
        val serial = !(data.cast[Ptr[Long]])
        val len = stackalloc[Double]
        !len = 0
        val byteSize = size * nmemb
        val headerString = bufferToString(ptr,size,nmemb).trim()
        println(s"req $serial: got header line of size ${size} x ${nmemb}")
        headerString match {
            case headerLine(k, v) =>
                val resp = requests(serial)
                resp.headers(k) = v
                requests(serial) = resp
            case statusLine(code, description) =>
                println(s"status code: $code $description")
            case l =>
                println(s"unrecognized line: $l")
        }
        return byteSize
    }

  // START:on_socket_ready
  def on_socket_ready(pollHandle:PollHandle, status:Int, events:Int):Unit = {
    println(s"ready_for_curl fired with status ${status} and events ${events}")
    val socket = !(pollHandle.cast[Ptr[Ptr[Byte]]])
    val actions = (events & 1) | (events & 2) // Whoa, nelly!
    val running_handles = stackalloc[Int]
    val result = multi_socket_action(multi, socket, actions, running_handles)
    println("multi_socket_action",result)
  }
  val readyCB = CFunctionPtr.fromFunction3(on_socket_ready)
  // END:on_socket_ready

  // START: on_timeout
  def on_timeout(handle:TimerHandle):Unit = {
    val running_handles = stackalloc[Int]
    multi_socket_action(multi,-1.cast[Ptr[Byte]],0,running_handles)
    println(s"on_timer fired, ${!running_handles} sockets running")
  }
  val timerCB = CFunctionPtr.fromFunction1(on_timeout)
  // END: on_timeout

  val writeCB = CFunctionPtr.fromFunction4(writeData)
  val headerCB = CFunctionPtr.fromFunction4(writeHeader)

  // START:handle_socket
  def handle_socket(curl:Curl, socket:Ptr[Byte], action:Int, data:Ptr[Byte],
   socket_data:Ptr[Byte]):Int = {
    println(s"handle_socket called with action ${action}")
    val pollHandle = if (socket_data == null) {
      val buf = malloc(uv_handle_size(UV_POLL_T)).cast[Ptr[Ptr[Byte]]]
      !buf = socket
      check(uv_poll_init_socket(loop, buf, socket), "uv_poll_init_socket")
      debug_check("multi_assign",multi_assign(multi, socket, buf.cast[Ptr[Byte]]))
      buf
    } else {
      socket_data.cast[Ptr[Ptr[Byte]]]
    }

    val events = action match {
      case POLL_NONE => None
      case POLL_IN => Some(UV_READABLE)
      case POLL_OUT => Some(UV_WRITABLE)
      case POLL_INOUT => Some(UV_READABLE | UV_WRITABLE)
      case POLL_REMOVE => None
    }

    events match {
      case Some(ev) =>
        println(s"starting poll with events $ev")
        uv_poll_start(pollHandle, ev, readyCB)
      case None =>
        println("stopping poll")
        uv_poll_stop(pollHandle)
        start_timer(multi, 1, null)
    }
    0
  }
  // END:handle_socket

  def cleanup_requests():Unit = {
    val messages = stackalloc[Int]
    val privateDataPtr= stackalloc[Ptr[Long]]
    var message:Ptr[CurlMessage] = multi_info_read(multi,messages)
    while (message != null) {
      println(s"Got a message ${!message._1} from multi_info_read, ${!messages} left in queue")
      val handle:Curl = !message._2
      easy_getinfo(handle, GET_PRIVATEDATA, privateDataPtr)
      val privateData = !privateDataPtr
      stdio.printf(c"privateDataPtr: %p  privateData: %p\n", privateDataPtr, privateData)
      val reqId = !privateData
      val reqData = requests.remove(reqId).get
      Curl.complete_request(reqId,reqData)
      message = multi_info_read(multi,messages)
    }
    println("done handling messages")
  }

  def shutdown():Unit = {
    debug_check("cleanup",multi_cleanup(multi))
    global_cleanup()
    initialized = false
  }

  // START:start_timer
  def start_timer(curl:MultiCurl, timeout_ms:Long, data:Ptr[Byte]):Int = {
    println(s"start_timer called with timeout ${timeout_ms} ms")
    val time = if (timeout_ms < 1) {
      println("setting effective timeout to 1")
      1
    } else timeout_ms
    check(uv_timer_start(timer_handle, timerCB, time, 0), "uv_timer_start")
    cleanup_requests()

    0
  }
  // END:start_timer

  val socketCB = CFunctionPtr.fromFunction5(handle_socket)
  val curltimerCB = CFunctionPtr.fromFunction3(start_timer)

  // START:init
  def init():Unit = {
    if (initialized == false) {
      loop = uv_default_loop()
      global_init(1)
      multi = multi_init()
      val setopt_r_1 = multi_setopt(multi, SOCKETFUNCTION, socketCB)
      val setopt_r_2 = multi_setopt(multi, TIMERFUNCTION, curltimerCB)

      timer_handle = malloc(uv_handle_size(UV_TIMER_T))
      check(uv_timer_init(loop,timer_handle),"uv_timer_init")
      initialized = true
    }
  }
  // END:init

  def debug_check(label:String,result:Int):Unit = {
    if (result != 0)
      println(s"call to $label returned $result")
  }
  
  def trace_check(label:String, result:Int):Unit = {
    if (result != 0)
      println(s"call to $label returned $result")
  }
}

object CurlConstants {
  val WRITEDATA = 10001
  val URL = 10002
  val PORT = 10003
  val USERPASSWORD = 10005
  val READDATA = 10009
  val HEADERDATA = 10029
  val PRIVATEDATA = 10103
  val WRITECALLBACK = 20011
  val READCALLBACK = 20012
  val HEADERCALLBACK = 20079
  val TIMEOUT = 13
  val GET = 80
  val POST = 47
  val PUT = 54
  val CONTENTLENGTHDOWNLOADT = 0x300000 + 15
  val GET_PRIVATEDATA = 0x100000 + 21
  val SOCKETFUNCTION = 20001
  val TIMERFUNCTION = 20004
  val HTTPHEADER = 10023

  // START:multiCallbacks
  type Curl = Ptr[Byte]
  type MultiCurl = Ptr[Byte]
  type SocketCallback = Function5[Curl, Ptr[Byte], CurlAction, Ptr[Byte], Ptr[Byte], CInt]
  type TimerCallback = Function3[MultiCurl, Long, Ptr[Byte], CInt]

  type CurlAction = CInt
  val POLL_NONE:CurlAction = 0
  val POLL_IN:CurlAction = 1
  val POLL_OUT:CurlAction = 2
  val POLL_INOUT:CurlAction = 3
  val POLL_REMOVE:CurlAction = 4
  // END:multiCallbacks
}

@link("curl")
@extern object LibCurl {    
  type Curl = Ptr[Byte]
  type CurlBuffer = CStruct2[CString, CSize]
  type CurlOption = Int   
  type CurlRequest = CStruct4[Ptr[Byte],Long,Long,Int]
  type CurlMessage = CStruct3[Int, Curl, Ptr[Byte]]

  @name("curl_global_init")
  def global_init(flags:Long):Unit = extern

  @name("curl_global_cleanup")
  def global_cleanup():Unit = extern

  @name("curl_easy_init")
  def easy_init():Curl = extern
  
  @name("curl_easy_cleanup")
  def easy_cleanup(handle: Curl): Unit = extern

  @name("curl_easy_setopt")
  def easy_setopt(handle: Curl, option: CInt, parameter: Any): CInt = extern
  
  @name("curl_easy_getinfo")
  def easy_getinfo(handle: Curl, info: CInt, parameter: Any): CInt = extern

  @name("curl_easy_perform")
  def easy_perform(easy_handle: Curl): CInt = extern

  // START:curl_multi_bindings
  type MultiCurl = Ptr[Byte]

  @name("curl_multi_init")
  def multi_init():MultiCurl = extern

  @name("curl_multi_add_handle")
  def multi_add_handle(multi:MultiCurl, easy:Curl):Int = extern

  @name("curl_multi_setopt")
  def multi_setopt(multi:MultiCurl, option:CInt, parameter:Any):CInt = extern

  @name("curl_multi_assign")
  def multi_assign(
    multi:MultiCurl, 
    socket:Ptr[Byte], 
    socket_data:Ptr[Byte]):Int = extern

  @name("curl_multi_socket_action")
  def multi_socket_action(
    multi:MultiCurl, 
    socket:Ptr[Byte], 
    events:Int, 
    numhandles:Ptr[Int]):Int = extern

  @name("curl_multi_info_read")
  def multi_info_read(multi:MultiCurl, message:Ptr[Int]):Ptr[CurlMessage] = extern

  @name("curl_multi_perform")
  def multi_perform(multi:MultiCurl, numhandles:Ptr[Int]):Int = extern

  @name("curl_multi_cleanup")
  def multi_cleanup(multi:MultiCurl):Int = extern
  // END:curl_multi_bindings

  // START:curlHeaders
  type CurlSList = CStruct2[Ptr[Byte],CString]

  @name("curl_slist_append")
  def slist_append(slist:Ptr[CurlSList], string:CString):Ptr[CurlSList] = extern

  @name("curl_slist_free_all")
  def slist_free_all(slist:Ptr[CurlSList]):Unit = extern
  // END:curlHeaders
}
