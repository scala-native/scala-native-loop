package scala.scalanative.loop
import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.{Promise}

case class Handle(serial: Long, handle: Ptr[Byte]) {
  import LibUV._

  def stream(
      itemHandler: StreamIO.ItemHandler,
      doneHandler: StreamIO.DoneHandler
  ): Unit = {
    StreamIO.streams(serial) = (itemHandler, doneHandler)
    uv_read_start(handle, StreamIO.allocCB, StreamIO.readCB)
  }

  def streamUntilDone(handler: StreamIO.ItemHandler): Future[Long] = {
    val promise = Promise[Long]

    val itemHandler: StreamIO.ItemHandler = (data, handle, id) =>
      try {
        handler(data, handle, id)
      } catch {
        case t: Throwable => promise.failure(t)
      }

    val doneHandler: StreamIO.DoneHandler = (handle, id) => promise.success(id)

    stream(itemHandler, doneHandler)
    promise.future
  }
}

object StreamIO {
  import LibUV._, LibUVConstants._
  type ItemHandler = ((String, PipeHandle, Long) => Unit)
  type DoneHandler = ((PipeHandle, Long) => Unit)
  type Handlers    = (ItemHandler, DoneHandler)
  var streams = mutable.HashMap[Long, Handlers]()
  var serial  = 0L

  def fromPipe(fd: Int): Handle = {
    val id = serial
    serial += 1
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(EventLoop.loop, handle, 0)
    val pipe_data = handle.asInstanceOf[Ptr[Long]]
    !pipe_data = id
    uv_pipe_open(handle, fd)
    Handle(id, handle)
  }

  def stream(
      fd: Int
  )(itemHandler: ItemHandler, doneHandler: DoneHandler): Handle = {
    val pipeId = serial
    serial += 1
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(EventLoop.loop, handle, 0)
    val pipe_data = handle.asInstanceOf[Ptr[Long]]
    !pipe_data = serial
    streams(serial) = (itemHandler, doneHandler)

    uv_pipe_open(handle, fd)
    uv_read_start(handle, allocCB, readCB)

    Handle(pipeId, handle)
  }

  def open(fd: Int): (PrepareHandle, Long) = ???

  def write(handle: PrepareHandle, content: String): Long = ???

  // def defaultDone(handle:PipeHandle,id:Long):Unit = ()

  val defaultDone: DoneHandler = (handle, id) => ()

  val promises = mutable.HashMap[Long, Promise[Long]]()
  def streamUntilDone(fd: Int)(handler: ItemHandler): Future[Long] = {
    val promise = Promise[Long]

    val itemHandler: ItemHandler = (data, handle, id) =>
      try {
        handler(data, handle, id)
      } catch {
        case t: Throwable => promise.failure(t)
      }

    val doneHandler: DoneHandler = (handle, id) => promise.success(id)

    stream(fd)(itemHandler, doneHandler)
    promise.future
  }

  val allocCB = new AllocCB {
    def apply(client: PipeHandle, size: CSize, buffer: Ptr[Buffer]): Unit = {
      val buf = stdlib.malloc(4096)
      buffer._1 = buf
      buffer._2 = 4096
    }
  }

  val readCB = new ReadCB {
    def apply(handle: PipeHandle, size: CSize, buffer: Ptr[Buffer]): Unit = {
      val pipe_data = handle.asInstanceOf[Ptr[Int]]
      val pipeId    = !pipe_data
      if (size < 0) {
        val doneHandler = streams(pipeId)._2
        doneHandler(handle, pipeId)
        streams.remove(pipeId)
      } else {
        val data = bytesToString(buffer._1, size)
        stdlib.free(buffer._1)
        val itemHandler = streams(pipeId)._1
        itemHandler(data, handle, pipeId)
      }
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
}
