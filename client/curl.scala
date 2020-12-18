package scala.scalanative.loop
import scala.scalanative.unsafe._
import scala.collection.mutable
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.concurrent._
import scala.concurrent.duration._
import scala.scalanative.runtime.Boxes

case class ResponseState(
    var code: Int = 200,
    var headers: mutable.Map[String, String] = mutable.Map(),
    var body: String = ""
)

object Curl {
  import LibCurl._
  import LibCurlConstants._
  import LibUVConstants._

  var serial = 0L

  var multi: MultiCurl = null

  val requestPromises = mutable.Map[Long, Promise[ResponseState]]()
  val requests        = mutable.Map[Long, ResponseState]()

  var initialized = false

  def init(): Unit = {
    if (!initialized) {
      println("initializing curl")
      global_init(1)
      multi = multi_init()
      println(s"initilized multiHandle $multi")
      println("socket function")
      val setopt_r_1 =
        multi_setopt_ptr(multi, SOCKETFUNCTION, func_to_ptr(socketCB))
      println("timer function")
      val setopt_r_2 =
        multi_setopt_ptr(multi, TIMERFUNCTION, func_to_ptr(startTimerCB))
      println(s"timerCB: $startTimerCB")

      initialized = true
      println("done")
    }
  }

  def startRequest(
      method: Int,
      url: String,
      headers: Seq[String] = Seq.empty,
      body: String = ""
  ): Future[ResponseState] = Zone { implicit z =>
    init()
    val curlHandle = easy_init()
    serial += 1
    val reqId = serial
    println(s"initializing handle $curlHandle for request $reqId")
    val req_id_ptr = malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
    !req_id_ptr = reqId
    requests(reqId) = ResponseState()
    val promise = Promise[ResponseState]()
    requestPromises(reqId) = promise

    method match {
      case GET =>
        check(easy_setopt_ptr(curlHandle, URL, toCString(url)), "easy_setopt")
        check(
          easy_setopt_ptr(curlHandle, WRITECALLBACK, func_to_ptr(dataCB)),
          "easy_setopt"
        )
        check(
          easy_setopt_ptr(
            curlHandle,
            WRITEDATA,
            req_id_ptr.asInstanceOf[Ptr[Byte]]
          ),
          "easy_setopt"
        )
        check(
          easy_setopt_ptr(curlHandle, HEADERCALLBACK, func_to_ptr(headerCB)),
          "easy_setopt"
        )
        check(
          easy_setopt_ptr(
            curlHandle,
            HEADERDATA,
            req_id_ptr.asInstanceOf[Ptr[Byte]]
          ),
          "easy_setopt"
        )
        check(
          easy_setopt_ptr(
            curlHandle,
            PRIVATEDATA,
            req_id_ptr.asInstanceOf[Ptr[Byte]]
          ),
          "easy_setopt"
        )
      case POST =>
        // TODO
        // notes: https://curl.haxx.se/libcurl/c/http-post.html
        // https://curl.haxx.se/libcurl/c/CURLOPT_POST.html
        ???
      case PUT =>
        // TODO
        // notes: https://curl.haxx.se/libcurl/c/httpput.html
        // https://curl.haxx.se/libcurl/c/CURLOPT_PUT.html
        ???
    }
    multi_add_handle(multi, curlHandle)

    println("request initialized")
    promise.future
  }

  val dataCB = new CurlDataCallback {
    def apply(
        ptr: Ptr[Byte],
        size: CSize,
        nmemb: CSize,
        data: Ptr[Byte]
    ): CSize = {
      val serial = !(data.asInstanceOf[Ptr[Long]])
      val len    = stackalloc[Double]
      !len = 0
      val strData = bufferToString(ptr, size, nmemb)
      println(s"req $serial: got data of size ${size} x ${nmemb}")

      val resp = requests(serial)
      resp.body = resp.body + strData
      requests(serial) = resp

      return size * nmemb
    }
  }

  val headerCB = new CurlDataCallback {
    def apply(
        ptr: Ptr[Byte],
        size: CSize,
        nmemb: CSize,
        data: Ptr[Byte]
    ): CSize = {
      val serial = !(data.asInstanceOf[Ptr[Long]])
      val len    = stackalloc[Double]
      !len = 0
      val strData = bufferToString(ptr, size, nmemb)
      println(s"req $serial: got header line of size ${size} x ${nmemb}")

      val resp = requests(serial)
      resp.body = resp.body + strData
      requests(serial) = resp

      return size * nmemb
    }
  }

  val socketCB = new CurlSocketCallback {
    def apply(
        curl: Curl,
        socket: Int,
        action: Int,
        data: Ptr[Byte],
        socket_data: Ptr[Byte]
    ): Int = {
      println(s"socketCB called with action ${action}")
      val pollHandle = if (socket_data == null) {
        println(s"initializing handle for socket ${socket}")
        val poll = Poll(socket)
        check(
          multi_assign(multi, socket, poll.ptr),
          "multi_assign"
        )
        poll
      } else {
        new Poll(socket_data)
      }

      val in = action == POLL_IN || action == POLL_INOUT
      val out = action == POLL_OUT || action == POLL_INOUT

      if (in || out) {
        println(
          s"starting poll with in = $in and out = $out"
        )
        pollHandle.start(in, out) { res =>
          println(
            s"ready_for_curl fired with status ${res.result} and readable = ${res.readable} writable = ${res.writable}"
          )
          var actions = 0
          if (res.readable) actions |= 1
          if (res.writable) actions |= 2
          val running_handles = stackalloc[Int]
          val result =
            multi_socket_action(multi, socket, actions, running_handles)
          println("multi_socket_action", result)
        }
      } else {
        println("stopping poll")
        pollHandle.close()
        startTimerCB(multi, 1, null)
      }
      0
    }
  }

  val startTimerCB = new CurlTimerCallback {
    def apply(curl: MultiCurl, timeout_ms: Long, data: Ptr[Byte]): Int = {
      println(s"start_timer called with timeout ${timeout_ms} ms")
      val time = if (timeout_ms < 1) {
        println("setting effective timeout to 1")
        1
      } else timeout_ms
      println("starting timer")
      Timer.timeout(time.millis) { () =>
        println("in timeout callback")
        val running_handles = stackalloc[Int]
        multi_socket_action(multi, -1, 0, running_handles)
        println(s"on_timer fired, ${!running_handles} sockets running")
      }
      println("cleaning up requests")
      cleanup_requests()
      println("done")
      0
    }
  }

  def cleanup_requests(): Unit = {
    val messages                  = stackalloc[Int]
    val privateDataPtr            = stackalloc[Ptr[Long]]
    var message: Ptr[CurlMessage] = multi_info_read(multi, messages)
    while (message != null) {
      println(
        s"Got a message ${message._1} from multi_info_read, ${!messages} left in queue"
      )
      val handle: Curl = message._2
      println(s"about to getInfo on handle $handle")
      check(
        easy_getinfo(
          handle,
          GET_PRIVATEDATA,
          privateDataPtr.asInstanceOf[Ptr[Byte]]
        ),
        "getinfo"
      )
      // Printf.printf(c"private data ptr: %p\n",privateDataPtr)
      println(s"ok? $privateDataPtr")
      val privateData = !privateDataPtr
      // stdio.printf(c"privateDataPtr: %p  privateData: %p\n", privateDataPtr, privateData)
      println(s"getting refId from $privateData")
      val reqId   = !privateData
      val reqData = requests.remove(reqId).get
      // Curl.complete_request(reqId,reqData)
      val promise = Curl.requestPromises.remove(reqId).get
      promise.success(reqData)
      message = multi_info_read(multi, messages)
    }
    println("done handling messages")
  }

  def bufferToString(ptr: Ptr[Byte], size: CSize, nmemb: CSize): String = {
    val byteSize = size * nmemb
    val buffer   = malloc(byteSize + 1)
    strncpy(buffer, ptr, byteSize + 1)
    val res = fromCString(buffer)
    free(buffer)
    return (res)
  }

  def multi_setopt(curl: MultiCurl, option: CInt, parameters: CVarArg*): Int =
    Zone { implicit z =>
      curl_multi_setopt(curl, option, toCVarArgList(parameters.toSeq))
    }

  def easy_setopt(curl: Curl, option: CInt, parameters: CVarArg*): Int = Zone {
    implicit z =>
      curl_easy_setopt(curl, option, toCVarArgList(parameters.toSeq))
  }

  def func_to_ptr(f: Object): Ptr[Byte] = {
    Boxes.boxToPtr[Byte](Boxes.unboxToCFuncRawPtr(f))
  }

}

object LibCurlConstants {
  val WRITEDATA              = 10001
  val URL                    = 10002
  val PORT                   = 10003
  val USERPASSWORD           = 10005
  val READDATA               = 10009
  val HEADERDATA             = 10029
  val PRIVATEDATA            = 10103
  val WRITECALLBACK          = 20011
  val READCALLBACK           = 20012
  val HEADERCALLBACK         = 20079
  val TIMEOUT                = 13
  val GET                    = 80
  val POST                   = 47
  val PUT                    = 54
  val CONTENTLENGTHDOWNLOADT = 0x300000 + 15
  val GET_PRIVATEDATA        = 0x100000 + 21
  val SOCKETFUNCTION         = 20001
  val SOCKETDATA             = 20002
  val TIMERFUNCTION          = 20004
  val TIMERDATA              = 20005
  val HTTPHEADER             = 10023

  val POLL_NONE   = 0
  val POLL_IN     = 1
  val POLL_OUT    = 2
  val POLL_INOUT  = 3
  val POLL_REMOVE = 4
}

@link("curl")
@extern object LibCurl {
  type Curl        = Ptr[Byte]
  type CurlBuffer  = CStruct2[CString, CSize]
  type CurlOption  = Int
  type CurlRequest = CStruct4[Ptr[Byte], Long, Long, Int]
  type CurlMessage = CStruct3[Int, Curl, Ptr[Byte]]

  type CurlDataCallback = CFuncPtr4[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]
  type CurlSocketCallback =
    CFuncPtr5[Curl, CInt, CInt, Ptr[Byte], Ptr[Byte], CInt]
  type CurlTimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]

  @name("curl_global_init")
  def global_init(flags: Long): Unit = extern

  @name("curl_global_cleanup")
  def global_cleanup(): Unit = extern

  @name("curl_easy_init")
  def easy_init(): Curl = extern

  @name("curl_easy_cleanup")
  def easy_cleanup(handle: Curl): Unit = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt(
      handle: Curl,
      option: CInt,
      parameter: CVarArgList
  ): CInt = extern

  @name("curl_easy_setopt")
  def easy_setopt_ptr(handle: Curl, option: CInt, parameter: Ptr[Byte]): CInt =
    extern

  @name("curl_easy_getinfo")
  def easy_getinfo(handle: Curl, info: CInt, parameter: Ptr[Byte]): CInt =
    extern

  @name("curl_easy_perform")
  def easy_perform(easy_handle: Curl): CInt = extern

  // START:curl_multi_bindings
  type MultiCurl = Ptr[Byte]

  @name("curl_multi_init")
  def multi_init(): MultiCurl = extern

  @name("curl_multi_add_handle")
  def multi_add_handle(multi: MultiCurl, easy: Curl): Int = extern

  @name("curl_multi_setopt")
  def curl_multi_setopt(
      multi: MultiCurl,
      option: CInt,
      parameter: CVarArg
  ): CInt = extern

  @name("curl_multi_setopt")
  def multi_setopt_ptr(
      multi: MultiCurl,
      option: CInt,
      parameter: Ptr[Byte]
  ): CInt = extern

  @name("curl_multi_assign")
  def multi_assign(
      multi: MultiCurl,
      socket: Int,
      socket_data: Ptr[Byte]
  ): Int = extern

  @name("curl_multi_socket_action")
  def multi_socket_action(
      multi: MultiCurl,
      socket: Int,
      events: Int,
      numhandles: Ptr[Int]
  ): Int = extern

  @name("curl_multi_info_read")
  def multi_info_read(multi: MultiCurl, message: Ptr[Int]): Ptr[CurlMessage] =
    extern

  @name("curl_multi_perform")
  def multi_perform(multi: MultiCurl, numhandles: Ptr[Int]): Int = extern

  @name("curl_multi_cleanup")
  def multi_cleanup(multi: MultiCurl): Int = extern
  // END:curl_multi_bindings

  // START:curlHeaders
  type CurlSList = CStruct2[Ptr[Byte], CString]

  @name("curl_slist_append")
  def slist_append(slist: Ptr[CurlSList], string: CString): Ptr[CurlSList] =
    extern

  @name("curl_slist_free_all")
  def slist_free_all(slist: Ptr[CurlSList]): Unit = extern
  // END:curlHeaders

  def curl_easy_strerror(code: Int): CString = extern
}
