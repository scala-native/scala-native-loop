package scala.scalanative.loop
import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.{Try, Success}
import scala.Option
import LibUV.Buffer
import LibUVConstants.check

trait LoopExtension {
  def activeRequests:Int
}

object EventLoop extends ExecutionContextExecutor {
  import LibUV._, LibUVConstants._


  val loop = uv_default_loop()
  private val taskQueue = ListBuffer[Runnable]()
  private val extensions = ListBuffer[LoopExtension]()
  var publishers = 0

  private def initDispatcher(loop:LibUV.Loop):PrepareHandle = {
    val handle = stdlib.malloc(uv_handle_size(UV_PREPARE_T))
    check(uv_prepare_init(loop, handle), "uv_prepare_init")
    check(uv_prepare_start(handle, prepareCallback), "uv_prepare_start")
    return handle
  }

  def addExtension(e:LoopExtension):Unit = {
    extensions.append(e)
  }

  private def extensionsWorking():Boolean = {
    extensions.exists( _.activeRequests > 0)
  }

  val prepareCallback = new PrepareCB {
    def apply(handle:PrepareHandle) = {
      while (taskQueue.nonEmpty) {
        val runnable = taskQueue.remove(0)
        try {
          runnable.run()
        } catch {
          case t: Throwable => reportFailure(t)
        }
      }
      if (taskQueue.isEmpty && !extensionsWorking) {
        println("stopping dispatcher")
        LibUV.uv_prepare_stop(handle)
      }

    }
  }

  private val dispatcher = initDispatcher(loop)

  def execute(runnable: Runnable): Unit = taskQueue += runnable
  def reportFailure(t: Throwable): Unit = {
    println(s"Future failed with Throwable $t:")
    t.printStackTrace()
  }

  def run(mode:Int = UV_RUN_DEFAULT):Unit = {
    var continue = 1
    while (continue != 0) {
        continue = uv_run(loop, mode)
        println(s"uv_run returned $continue")
    }
  }
}

@link("uv")
@extern
object LibUV {
  type PipeHandle = Ptr[Byte]
  type PollHandle = Ptr[Ptr[Byte]]
  type TCPHandle = Ptr[Byte]
  type PrepareHandle = Ptr[Byte]
  type TimerHandle = Ptr[Byte]

  type TTYHandle = Ptr[Byte]
  type Loop = Ptr[Byte]
  type Buffer = CStruct2[Ptr[Byte],CSize]
  type WriteReq = Ptr[Ptr[Byte]]
  type FSReq = Ptr[Ptr[Byte]]
  type ShutdownReq = Ptr[Ptr[Byte]]
  type Connection = Ptr[Byte]
  type ConnectionCB = CFuncPtr2[TCPHandle,Int,Unit]
  type AllocCB = CFuncPtr3[TCPHandle,CSize,Ptr[Buffer],Unit]
  type ReadCB = CFuncPtr3[TCPHandle,CSSize,Ptr[Buffer],Unit]
  type WriteCB = CFuncPtr2[WriteReq,Int,Unit]
  type PrepareCB = CFuncPtr1[PrepareHandle, Unit]
  type ShutdownCB = CFuncPtr2[ShutdownReq,Int,Unit]
  type CloseCB = CFuncPtr1[TCPHandle,Unit]
  type PollCB = CFuncPtr3[PollHandle, Int, Int, Unit]
  type TimerCB = CFuncPtr1[TimerHandle,Unit]
  type FSCB = CFuncPtr1[FSReq,Unit]

  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle:Ptr[Byte]): Int = extern
  def uv_handle_size(h_type:Int): CSize = extern
  def uv_req_size(r_type:Int): CSize = extern
  def uv_prepare_init(loop:Loop, handle:PrepareHandle):Int = extern
  def uv_prepare_start(handle:PrepareHandle, cb: PrepareCB):Int = extern
  def uv_prepare_stop(handle:PrepareHandle):Unit = extern

  def uv_tty_init(loop:Loop, handle:TTYHandle, fd:Int, readable:Int):Int = extern

  def uv_tcp_init(loop:Loop, tcp_handle:TCPHandle):Int = extern
  def uv_tcp_bind(tcp_handle:TCPHandle, address:Ptr[Byte], flags:Int):Int = extern

  def uv_ip4_addr(address:CString, port:Int, out_addr:Ptr[Byte]):Int = extern
  def uv_ip4_name(address:Ptr[Byte], s:CString, size:Int):Int = extern

  def uv_pipe_init(loop:Loop, handle:PipeHandle, ipc:Int):Int = extern
  def uv_pipe_open(handle:PipeHandle, fd:Int):Int = extern
  def uv_pipe_bind(handle:PipeHandle, socketName:CString):Int = extern

  def uv_poll_init_socket(loop:Loop, handle:PollHandle, socket:Ptr[Byte]):Int = extern
  def uv_poll_start(handle:PollHandle, events:Int, cb: PollCB):Int = extern
  def uv_poll_stop(handle:PollHandle):Int = extern

  def uv_timer_init(loop:Loop, handle:TimerHandle):Int = extern
  def uv_timer_start(handle:TimerHandle, cb:TimerCB, timeout:Long, repeat:Long):Int = extern
  def uv_timer_stop(handle:TimerHandle):Int = extern

  def uv_listen(handle:PipeHandle, backlog:Int, callback:ConnectionCB): Int = extern
  def uv_accept(server:PipeHandle, client:PipeHandle): Int = extern
  def uv_read_start(client:PipeHandle, allocCB:AllocCB, readCB:ReadCB): Int = extern
  def uv_write(writeReq:WriteReq, client:PipeHandle, bufs: Ptr[Buffer], numBufs: Int, writeCB:WriteCB): Int = extern
  def uv_read_stop(client:PipeHandle): Int = extern
  def uv_shutdown(shutdownReq:ShutdownReq, client:PipeHandle, shutdownCB:ShutdownCB): Int = extern
  def uv_close(handle:PipeHandle, closeCB: CloseCB): Unit = extern
  def uv_is_closing(handle:PipeHandle): Int = extern
  def uv_run(loop:Loop, runMode:Int): Int = extern

  def uv_strerror(err:Int): CString = extern
  def uv_err_name(err:Int): CString = extern

  def uv_fileno(handle:TTYHandle, fileno:Ptr[Int]):Int = extern
  def uv_handle_type_name(handle:TTYHandle):Int = extern
  def uv_guess_handle(fd:Int):Int = extern

  def uv_fs_open(loop:Loop, req:FSReq, path:CString, flags:Int, mode:Int, cb:FSCB):Int = extern
  def uv_fs_read(loop:Loop, req:FSReq, fd:Int, bufs:Ptr[Buffer], numBufs:Int, offset:Long, fsCB:FSCB):Int = extern
  def uv_fs_write(loop:Loop, req:FSReq, fd:Int, bufs:Ptr[Buffer], numBufs:Int, offset:Long, fsCB:FSCB):Int = extern
  def uv_fs_close(loop:Loop, req:FSReq, fd:Int, fsCB:FSCB):Int = extern
  def uv_req_cleanup(req:FSReq):Unit = extern
  def uv_fs_get_result(req:FSReq):Int = extern
  def uv_fs_get_ptr(req:FSReq):Ptr[Byte] = extern
}

object LibUVConstants {
  import LibUV._

  // uv_run_mode
  val UV_RUN_DEFAULT = 0
  val UV_RUN_ONCE = 1
  val UV_RUN_NOWAIT = 2

  // UV_HANDLE_T
  val UV_PIPE_T = 7
  val UV_POLL_T = 8
  val UV_PREPARE_T = 9
  val UV_PROCESS_T = 10
  val UV_TCP_T = 12
  val UV_TIMER_T = 13
  val UV_TTY_T = 14
  val UV_UDP_T = 15

  // UV_REQ_T
  val UV_WRITE_REQ_T = 3
  val UV_FS_REQ_T = 6

  val UV_READABLE = 1
  val UV_WRITABLE = 2
  val UV_DISCONNECT = 4
  val UV_PRIORITIZED = 8

  val O_RDWR = 2
  val O_CREAT = sys.props("os.name") match {
    case "Mac OS X" => 512
    case _ => 64
  }
  val default_permissions = 420 // octal 0644

  def check(v:Int, label:String):Int = {
      if (v == 0) {
        println(s"$label returned $v")
        v
      } else {
        val error = fromCString(uv_err_name(v))
        val message = fromCString(uv_strerror(v))
        println(s"$label returned $v: $error: $message")
        v
      }
  }
}