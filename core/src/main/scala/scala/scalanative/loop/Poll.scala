package scala.scalanative.loop

import LibUV._, LibUVConstants._
import scala.scalanative.unsafe.{Ptr, sizeOf}
import scala.scalanative.runtime.BlobArray
import internals.HandleUtils

class RWResult(val result: Int, val readable: Boolean, val writable: Boolean)
@inline class Poll(private val data: BlobArray) extends AnyVal {
  private def handle: Ptr[Byte] = data.atUnsafe(0)

  def start(in: Boolean, out: Boolean)(callback: RWResult => Unit): Unit = {
    HandleUtils.setData(handle, callback)
    var events = 0
    if (out) events |= UV_WRITABLE
    if (in) events |= UV_READABLE
    uv_poll_start(handle, events, Poll.pollReadWriteCB)
  }

  def startReadWrite(callback: RWResult => Unit): Unit = {
    HandleUtils.setData(handle, callback)
    uv_poll_start(handle, UV_READABLE | UV_WRITABLE, Poll.pollReadWriteCB)
  }

  def startRead(callback: Int => Unit): Unit = {
    HandleUtils.setData(handle, callback)
    uv_poll_start(handle, UV_READABLE, Poll.pollReadCB)
  }

  def startWrite(callback: Int => Unit): Unit = {
    HandleUtils.setData(handle, callback)
    uv_poll_start(handle, UV_WRITABLE, Poll.pollWriteCB)
  }

  def stop(): Unit = {
    uv_poll_stop(handle)
    HandleUtils.close(handle)
  }
}

object Poll {
  private val pollReadWriteCB: PollCB = (
      handle: PollHandle,
      status: Int,
      events: Int
  ) => {
    val callback =
      HandleUtils.getData[RWResult => Unit](handle)
    callback.apply(
      new RWResult(
        result = status,
        readable = (events & UV_READABLE) != 0,
        writable = (events & UV_WRITABLE) != 0
      )
    )
  }
  private val pollReadCB: PollCB = (
      handle: PollHandle,
      status: Int,
      events: Int
  ) => {
    val callback = HandleUtils.getData[Int => Unit](handle)
    if ((events & UV_READABLE) != 0) callback.apply(status)
  }
  private val pollWriteCB: PollCB = (
      handle: PollHandle,
      status: Int,
      events: Int
  ) => {
    val callback = HandleUtils.getData[Int => Unit](handle)
    if ((events & UV_WRITABLE) != 0) callback.apply(status)
  }

  private lazy val size = uv_handle_size(UV_POLL_T)

  def apply(fd: Int): Poll = {
    // GC managed memory, but scans only user data 
    val data = BlobArray.alloc(size.toInt)
    data.setScannableLimitUnsafe(sizeOf[Ptr[_]])
    uv_poll_init(EventLoop.loop, data.atUnsafe(0), fd)
    new Poll(data)
  }
}
