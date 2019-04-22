import scalanative.native._
import collection.mutable
import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future,ExecutionContext}
import scala.concurrent.{Promise}

trait Pipe[T,U] {
  val handlers = mutable.Set[Pipe[U,_]]()

  def feed(input:T):Unit
  def done():Unit = {
    for (h <- handlers) {
      h.done()
    }
  }

  def addDestination[V](dest:Pipe[U,V]):Pipe[U,V] = {
    handlers += dest
    dest
  }

  case class SyncPipe[T,U](f:T => U) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      val output = f(input)
      for (h <- handlers) {
        h.feed(output)
      }
    }    
  }

  // START:pipeMap
  def map[V](g:U => V):Pipe[U,V] = {
    addDestination(SyncPipe(g))
  }
  // END:pipeMap

  // START:ConcatPipe
  case class ConcatPipe[T,U](f:T => Seq[U]) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      val output = f(input)
      for (h <- handlers;
           o <- output) {
        h.feed(o)
      }
    }
  }
  // END:ConcatPipe

  // START:mapConcat
  def mapConcat[V](g:U => Seq[V]):Pipe[U,V] = {
    addDestination(ConcatPipe(g))
  }
  // END:mapConcat

  // START:OptionPipe
  case class OptionPipe[T,U](f:T => Option[U]) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      val output = f(input)
      for (h <- handlers;
           o <- output) {
        h.feed(o)
      }
    }
  }
  // END:OptionPipe

  // START:mapOption
  def mapOption[V](g:U => Option[V]):Pipe[U,V] = {
    addDestination(OptionPipe(g))
  }
  // END:mapOption

  // START:AsyncPipe
  case class AsyncPipe[T,U](f:T => Future[U])(implicit ec:ExecutionContext) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      f(input).map { o =>
        for (h <- handlers) {
          h.feed(o)
        }
      }
    }
  }
  // END:AsyncPipe

  // START:mapAsync
  def mapAsync[V](g:U => Future[V])(implicit ec:ExecutionContext):Pipe[U,V] = {
    addDestination(AsyncPipe(g))
  }
  // END:mapAsync

  def onComplete(implicit ec:ExecutionContext):Future[Unit] = {
    val sink = OnComplete[U]()
    addDestination(sink)
    return sink.promise.future
  }
}

case class OnComplete[T]()(implicit ec:ExecutionContext) extends Pipe[T,Unit] {
  val promise = Promise[Unit]()
  override def feed(input:T) = {}

  override def done() = {
    println("done, completing promise")
    promise.success(())
  }
}

// START:CounterSink
case class CounterSink[T]() extends Pipe[T,Nothing] {
  var counter = 0
  override def feed(input:T) = {
    counter += 1
  }
}
// END:CounterSink

// START:FileOutputPipe
case class FileOutputPipe(fd:Int, serial:Int, async:Boolean) extends Pipe[String,Unit] {
  import LibUV._, LibUVConstants._
  import stdlib._, string._
  var offset = 0L

  val writeCB = if (async) { FileOutputPipe.writeCB } else null

  override def feed(input:String):Unit = {
    val output_size = input.size
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).cast[FSReq]
    
    val output_buffer = malloc(sizeof[Buffer]).cast[Ptr[Buffer]]
    !output_buffer._1 = malloc(output_size)
    Zone { implicit z =>
      val output_string = toCString(input)
      strncpy(!output_buffer._1, output_string, output_size)
    }
    !output_buffer._2 = output_size
    !req = output_buffer.cast[Ptr[Byte]]

    uv_fs_write(EventLoop.loop,req,fd,output_buffer,1,offset,writeCB)
    offset += output_size
  }

  override def done():Unit = {
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).cast[FSReq]
    uv_fs_close(EventLoop.loop,req,fd,null)
    FileOutputPipe.active_streams -= serial
  }
}
// END:FileOutputPipe

// START:FileOutputPipeInit
object FileOutputPipe extends LoopExtension {
  import LibUV._, LibUVConstants._
  import stdlib._

  var active_streams:mutable.Set[Int] = mutable.Set()
  var serial = 0

  override def activeRequests:Int = {
    active_streams.size
  }

  var initialized = false
  def apply(path:CString, async:Boolean = true):FileOutputPipe = {
    if (!initialized) {
      EventLoop.addExtension(this)
      initialized = true
    }
    active_streams += serial

    stdio.printf(c"opening %s for writing..\n", path)
    val fd = util.open(path,O_RDWR + O_CREAT,default_permissions)
    println(s"got back fd: $fd")


    val pipe = FileOutputPipe(fd,serial,async)
    serial += 1
    println(s"initialized $pipe")
    pipe
  }
// END:FileOutputPipeInit

// START:FileOutputPipeCallbacks
  def on_write(req:FSReq):Unit = {
    println("write completed")
    val resp_buffer = (!req).cast[Ptr[Buffer]]
    stdlib.free(!resp_buffer._1)
    stdlib.free(resp_buffer.cast[Ptr[Byte]])
    stdlib.free(req.cast[Ptr[Byte]])
  }
  val writeCB = CFunctionPtr.fromFunction1(on_write)
// END:FileOutputPipeCallbacks

  // def on_shutdown(shutdownReq:ShutdownReq, status:Int):Unit = {
  //   val client = (!shutdownReq).cast[PipeHandle]
  //   uv_close(client,closeCB)
  //   stdlib.free(shutdownReq.cast[Ptr[Byte]])
  // }
  // val shutdownCB = CFunctionPtr.fromFunction2(on_shutdown)

  // def on_close(client:PipeHandle):Unit = {
  //   stdlib.free(client.cast[Ptr[Byte]])
  // }
  // val closeCB = CFunctionPtr.fromFunction1(on_close)
}

// START:Tokenizer
case class Tokenizer(separator:String) extends Pipe[String,String] {
  var buffer = ""

  def scan(input:String):Seq[String] = {
      println(s"scanning: '$input'")
      buffer = buffer + input
      var o:Seq[String] = Seq()
      while (buffer.contains(separator)) {
        val space_position = buffer.indexOf(separator)
        val word = buffer.substring(0,space_position)

        o = o :+ word

        buffer = buffer.substring(space_position + 1)
      }
      o

  }
  override def feed(input:String):Unit = {
    for (h    <- handlers;
         word <- scan(input)) {
           h.feed(word)
    }
  }

  override def done():Unit = {
    println(s"done!  current buffer: $buffer")
    for (h <- handlers) {
           h.feed(buffer)
           h.done()
    }
  }
}
// END:Tokenizer

// START:FoldPipe
case class FoldPipe[I,O](init:O)(f:(O,I) => O) extends Pipe[I,O] {
  var accum = init

  override def feed(input:I):Unit = {
    accum = f(accum,input)
    for (h <- handlers) {
      h.feed(accum)
    }
  }

  override def done():Unit = {
    for (h <- handlers) {
      h.done()
    }
  }
}
// END:FoldPipe

object Pipe {
  case class PipeSource[I]() extends Pipe[I,I] {
    override def feed(input:I):Unit = {
      for (h <- handlers) {
        h.feed(input)
      }
    }
  }
  def source[I]:Pipe[I,I] = {
    PipeSource[I]()
  }
}

// START:FilePipe
case class FilePipe(serial:Long) extends Pipe[String,String] {
  override def feed(input:String):Unit = {
    for (h <- handlers) {
      h.feed(input)
    }
  }
}
// END:FilePipe

// START:FilePipeInit
object FilePipe extends LoopExtension {
  import LibUV._, LibUVConstants._
  type FilePipeState = CStruct3[Int,Ptr[Buffer],Long] // fd, buffer, offset

  var active_streams:mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int,FilePipe]()
  var serial = 0

  override def activeRequests:Int = {
    active_streams.size
  }

  var initialized = false
// END: FilePipeInit

// START:FilePipeApply
  def apply(path:CString):Pipe[String,String] = {
    if (!initialized) {
      EventLoop.addExtension(this)
      initialized = true
    }
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).cast[FSReq]
    val fd = util.open(path,0,0)
    if (fd < 0) {
      throw new Exception(s"opening file failed, returned $fd")
    }
    stdio.printf(c"open file at %s returned %d\n", path, fd)

    val state = stdlib.malloc(sizeof[FilePipeState]).cast[Ptr[FilePipeState]]
    val buf = stdlib.malloc(sizeof[Buffer]).cast[Ptr[Buffer]]
    !buf._1 = stdlib.malloc(4096)
    !buf._2 = 4095
    !state._1 = fd
    !state._2 = buf
    !state._3 = 0L
    !req = state.cast[Ptr[Byte]]

    uv_fs_read(EventLoop.loop,req,fd,buf,1,-1,readCB)
    val pipe = FilePipe(serial)
    serial += 1
    handlers(fd) = pipe
    active_streams += fd
    pipe
  }
// END:FilePipeApply

// START:FilePipeOnRead
  def on_read(req:FSReq):Unit = {
    val res = uv_fs_get_result(req)
    println(s"got result: $res")
    val state_ptr = (!req).cast[Ptr[FilePipeState]]
    val fd = !state_ptr._1
    val buf = !state_ptr._2
    val offset = !state_ptr._3

    if (res > 0) {
      (!buf._1)(res) = 0
      val output = fromCString(!buf._1)
      val pipe = handlers(fd)
      pipe.feed(output)
      !state_ptr._3 = !state_ptr._3 + res
      uv_fs_read(EventLoop.loop,req,fd,!state_ptr._2,1,!state_ptr._3,readCB)
    } else if (res == 0) {
      val pipe = handlers(fd)
      pipe.done()
      active_streams -= fd
    } else {
      active_streams -= fd
    }
  }
  val readCB = CFunctionPtr.fromFunction1(on_read)
// END:FilePipeOnRead
}


object SyncPipe extends LoopExtension {
  import LibUV._, LibUVConstants._

  var active_streams:mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int,Pipe[String,String]]()
  var serial = 0

  override def activeRequests:Int = {
    active_streams.size
  }

  var initialized = false

  def stream(fd:Int):Pipe[String,String] = {
    if (!initialized) {
      EventLoop.addExtension(this)
      initialized = true
    }
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(EventLoop.loop,handle,0)
    val pipe_data = handle.cast[Ptr[Int]]
    !pipe_data = serial
    active_streams += serial
    val pipe = Pipe.source[String]
    handlers(serial) = pipe

    serial += 1
    uv_pipe_open(handle,fd)
    uv_read_start(handle,allocCB,readCB)
    pipe
  }

  // def transform[T](pipe:Pipe[T])(f:Function1[String,_]) = {
  //   handlers(pipe.serial) = f
  // }

  def on_alloc(client:PipeHandle, size:CSize, buffer:Ptr[Buffer]):Unit = {
    val buf = stdlib.malloc(4096)
    !buffer._1 = buf
    !buffer._2 = 4096
  }
  val allocCB = CFunctionPtr.fromFunction3(on_alloc)

  def on_read(handle:PipeHandle,size:CSize,buffer:Ptr[Buffer]):Unit = {
    val pipe_data = handle.cast[Ptr[Int]]
    val pipe_id = !pipe_data
    println(s"read $size bytes from pipe $pipe_id")
    if (size < 0) {
      println("size < 0, closing")
      active_streams -= pipe_id
      val pipe_destination = handlers(pipe_id)
      pipe_destination.done()
      handlers.remove(pipe_id)
    } else {
      val data_buffer = stdlib.malloc(size + 1)
      string.strncpy(data_buffer, !buffer._1, size + 1)      
      val data_string = fromCString(data_buffer)
      stdlib.free(data_buffer)
      val pipe_destination = handlers(pipe_id)
      pipe_destination.feed(data_string.trim())
    }
  }
  val readCB = CFunctionPtr.fromFunction3(on_read)
}

@extern
object util {
  def open(path:CString, flags:Int, mode:Int):Int = extern
}