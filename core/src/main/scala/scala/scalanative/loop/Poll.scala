package scala.scalanative.loop

import scala.scalanative.libc.stdlib
import LibUV._, LibUVConstants._
import scala.scalanative.unsafe.Ptr
import internals.HandleUtils

@inline class Poll(val ptr: Ptr[Byte]) extends AnyVal {
  def start(in: Boolean, out: Boolean)(
      callback: (Int, Boolean, Boolean) => Unit
  ): Unit = {
    HandleUtils.setData(ptr, callback)
    var events = 0
    if (out) events |= UV_WRITABLE
    if (in) events |= UV_READABLE
    uv_poll_start(ptr, events, Poll.pollCB)
  }

  def stop(): Unit = {
    uv_poll_stop(ptr)
    HandleUtils.close(ptr)
  }
}

object Poll {
  private val pollCB = new PollCB {
    def apply(handle: PollHandle, status: Int, events: Int): Unit = {
      val callback =
        HandleUtils.getData[(Int, Boolean, Boolean) => Unit](handle)
      callback.apply(
        status,
        (events & UV_READABLE) != 0,
        (events & UV_WRITABLE) != 0
      )
    }
  }

  private lazy val size = uv_handle_size(UV_POLL_T)

  def apply(fd: Int): Poll = {
    val pollHandle = stdlib.malloc(size)
    uv_poll_init(EventLoop.loop, pollHandle, fd)
    new Poll(pollHandle)
  }
}
