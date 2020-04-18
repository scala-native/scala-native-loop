package scala.scalanative.loop

import scala.scalanative.libc.stdlib
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import LibUV._, LibUVConstants._
import scala.scalanative.unsafe.Ptr

@inline class Timer private (private val ptr: Ptr[Byte]) extends AnyVal {
  def clear(): Unit = {
    uv_timer_stop(ptr)
    HandleUtils.close(ptr)
  }
}

object Timer {
  private val timeoutCB = new TimerCB {
    def apply(handle: TimerHandle): Unit = {
      val callback = HandleUtils.getData[() => Unit](handle)
      callback.apply()
      new Timer(handle)
    }
  }
  private val repeatCB = new TimerCB {
    def apply(handle: TimerHandle): Unit = {
      val callback = HandleUtils.getData[() => Unit](handle)
      callback.apply()
    }
  }
  @inline
  private def startTimer(timeout: Long, repeat: Long, callback: () => Unit): Timer = {
    val timerHandle = stdlib.malloc(uv_handle_size(UV_TIMER_T))
    uv_timer_init(EventLoop.loop, timerHandle)
    HandleUtils.setData(timerHandle, callback)
    uv_timer_start(timerHandle, timeoutCB, timeout, repeat)
    new Timer(timerHandle)
  }

  def delay(duration: FiniteDuration): Future[Unit] = {
    val promise = Promise[Unit]()
    timeout(duration.toMillis)(() => promise.success(()))
    promise.future
  }

  def timeout(millis: Long)(callback: () => Unit): Timer = {
    startTimer(millis, 0L, callback)
  }

  def repeat(millis: Long)(callback: () => Unit): Timer = {
    startTimer(millis, millis, callback)
  }
}
