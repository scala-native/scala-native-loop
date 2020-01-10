package scala.scalanative.loop
import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.{Try, Success}
import scala.Option
import scala.concurrent.duration._
import LibUV._, LibUVConstants._

object Timer {

  var serial    = 0L
  var callbacks = mutable.HashMap[Long, () => Unit]()

  def delay(dur: Duration): Future[Unit] = {
    val promise = Promise[Unit]()
    serial += 1
    val timer_id = serial
    callbacks(timer_id) = () => promise.success(())
    val millis = dur.toMillis

    val timer_handle = stdlib.malloc(uv_handle_size(UV_TIMER_T))
    uv_timer_init(EventLoop.loop, timer_handle)
    val timer_data = timer_handle.asInstanceOf[Ptr[Long]]
    !timer_data = timer_id
    uv_timer_start(timer_handle, timerCB, millis, 0)

    promise.future
  }

  val timerCB = new TimerCB {
    def apply(handle: TimerHandle): Unit = {
      val timer_data = handle.asInstanceOf[Ptr[Long]]
      val timer_id   = !timer_data
      val callback   = callbacks(timer_id)
      callbacks.remove(timer_id)
      callback()
      stdlib.free(handle)
    }
  }

  // low-level, intended for use in scala.js compat layer - leaks memory if
  // caller does not free() returned handle
  def oneTime(dur: Double, callback: () => Unit): PrepareHandle = {
    serial += 1
    val timer_id = serial

    val timer_handle = stdlib.malloc(uv_handle_size(UV_TIMER_T))
    uv_timer_init(EventLoop.loop, timer_handle)
    val timer_data = timer_handle.asInstanceOf[Ptr[Long]]
    !timer_data = timer_id

    callbacks(timer_id) = () => {
      callback()
    }

    uv_timer_start(timer_handle, timerCB, dur.toLong, 0)
    timer_handle
  }

  // low-level, intended for use in scala.js compat layer - leaks memory if
  // caller does not free() returned handle
  def repeat(dur: Double, callback: () => Unit): PrepareHandle = {
    serial += 1
    val timer_id = serial

    callbacks(timer_id) = () => callback()
    val timer_handle = stdlib.malloc(uv_handle_size(UV_TIMER_T))
    uv_timer_init(EventLoop.loop, timer_handle)
    val timer_data = timer_handle.asInstanceOf[Ptr[Long]]
    !timer_data = timer_id
    uv_timer_start(timer_handle, timerCB, dur.toLong, dur.toLong)
    timer_handle
  }
}
