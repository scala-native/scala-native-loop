package scala.scalanative.loop

import scala.scalanative.libc.stdlib
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import LibUV._, LibUVConstants._
import scala.scalanative.unsafe.Ptr
import internals.HandleUtils

@inline final class Timer private (private val ptr: Ptr[Byte]) extends AnyVal {
  def clear(): Unit = {
    uv_timer_stop(ptr)
    HandleUtils.close(ptr)
  }
}

object Timer {
  private val timeoutCB: TimerCB = (handle: TimerHandle) => {
    val callback = HandleUtils.getData[() => Unit](handle)
    callback.apply()
  }
  private val repeatCB: TimerCB = (handle: TimerHandle) => {
    val callback = HandleUtils.getData[() => Unit](handle)
    callback.apply()
  }
  @inline
  private def startTimer(
      timeout: Long,
      repeat: Long,
      callback: () => Unit
  ): Timer = {
    val timerHandle = stdlib.malloc(uv_handle_size(UV_TIMER_T))
    uv_timer_init(EventLoop.loop, timerHandle)
    HandleUtils.setData(timerHandle, callback)
    val timer = new Timer(timerHandle)
    val withClearIfTimeout: () => Unit =
      if (repeat == 0L) { () =>
        {
          callback()
          timer.clear()
        }
      } else callback
    uv_timer_start(timerHandle, timeoutCB, timeout, repeat)
    timer
  }

  def delay(duration: FiniteDuration): Future[Unit] = {
    val promise = Promise[Unit]()
    timeout(duration)(() => promise.success(()))
    promise.future
  }

  def timeout(duration: FiniteDuration)(callback: () => Unit): Timer = {
    startTimer(duration.toMillis, 0L, callback)
  }

  def repeat(duration: FiniteDuration)(callback: () => Unit): Timer = {
    val millis = duration.toMillis
    startTimer(millis, millis, callback)
  }
}
