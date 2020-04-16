/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package scala.scalajs.js.timers

import scalanative.libc.stdlib
import scalanative.loop.Timer
import scalanative.loop.LibUV.uv_timer_stop
import scalanative.loop.LibUVConstants.check

/**
 *  <span class="badge badge-non-std" style="float: right;">Non-Standard</span>
 *  Raw JavaScript timer methods.
 *
 *  The methods on this object expose the raw JavaScript methods for timers. In
 *  general it is more advisable to use the methods directly defined on
 *  [[timers]] as they are more Scala-like.
 */
object RawTimers {

  /** Schedule `handler` for execution in `interval` milliseconds.
   *
   *  @param handler the function to call after `interval` has passed
   *  @param interval duration in milliseconds to wait
   *  @return A handle that can be used to cancel the timeout by passing it
   *          to [[clearTimeout]].
   */
  def setTimeout(handler: () => Unit, interval: Double): SetTimeoutHandle = {
    val t = Timer.oneTime(interval, handler)
    new SetTimeoutHandle {
      val timerHandle = t
    }
  }

  /** Cancel a timeout execution
   *  @param handle The handle returned by [[setTimeout]]
   */
  def clearTimeout(handle: SetTimeoutHandle): Unit = {
    if (handle.timerHandle != null) {
      check(uv_timer_stop(handle.timerHandle), "uv_timer_stop")
      stdlib.free(handle.timerHandle)
    }
  }

  /** Schedule `handler` for repeated execution every `interval`
   *  milliseconds.
   *
   *  @param handler the function to call after each `interval`
   *  @param interval duration in milliseconds between executions
   *  @return A handle that can be used to cancel the interval by passing it
   *          to [[clearInterval]].
   */
  def setInterval(handler: () => Unit, interval: Double): SetIntervalHandle = {
    val t = Timer.repeat(interval, handler)
    new SetIntervalHandle {
      val timerHandle = t
    }
  }

  /** Cancel an interval execution
   *  @param handle The handle returned by [[setInterval]]
   */
  def clearInterval(handle: SetIntervalHandle): Unit = {
    if (handle.timerHandle != null) {
      check(uv_timer_stop(handle.timerHandle), "uv_timer_stop")
      stdlib.free(handle.timerHandle)
    }
  }
}
