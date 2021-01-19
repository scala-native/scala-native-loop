package scala.scalanative.loop

import scala.scalanative.libc.stdlib
import LibUV._, LibUVConstants._
import scala.scalanative.unsafe.Ptr
import internals.HandleUtils

class RWResult(val result: Int, val readable: Boolean, val writable: Boolean)
@inline class Poll(val ptr: Ptr[Byte]) extends AnyVal {
  def start(in: Boolean, out: Boolean)(callback: RWResult => Unit): Unit = {
    HandleUtils.setData(ptr, callback)
    var events = 0
    if (out) events |= UV_WRITABLE
    if (in) events |= UV_READABLE
    uv_poll_start(ptr, events, Poll.pollReadWriteCB)
  }

  def startReadWrite(callback: RWResult => Unit): Unit = {
    HandleUtils.setData(ptr, callback)
    uv_poll_start(ptr, UV_READABLE | UV_WRITABLE, Poll.pollReadWriteCB)
  }

  def startRead(callback: Int => Unit): Unit = {
    HandleUtils.setData(ptr, callback)
    uv_poll_start(ptr, UV_READABLE, Poll.pollReadCB)
  }

  def startWrite(callback: Int => Unit): Unit = {
    HandleUtils.setData(ptr, callback)
    uv_poll_start(ptr, UV_WRITABLE, Poll.pollWriteCB)
  }

  def stop(): Unit = {
    uv_poll_stop(ptr)
    HandleUtils.close(ptr)
  }
}

object Poll {
  private val pollReadWriteCB: PollCB = (handle: PollHandle, status: Int, events: Int) => {
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
  private val pollReadCB: PollCB = (handle: PollHandle, status: Int, events: Int) => {
    val callback = HandleUtils.getData[Int => Unit](handle)
    if ((events & UV_READABLE) != 0) callback.apply(status)
  }
  private val pollWriteCB: PollCB = (handle: PollHandle, status: Int, events: Int) => {
    val callback = HandleUtils.getData[Int => Unit](handle)
    if ((events & UV_WRITABLE) != 0) callback.apply(status)
  }

  private lazy val size = uv_handle_size(UV_POLL_T)

  def apply(fd: Int): Poll = {
    val pollHandle = stdlib.malloc(size)
    uv_poll_init(EventLoop.loop, pollHandle, fd)
    new Poll(pollHandle)
  }
}
