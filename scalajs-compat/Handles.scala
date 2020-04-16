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

import scalanative.loop.LibUV.TimerHandle

/** <span class="badge badge-non-std" style="float: right;">Non-Standard</span>
 *  A handle returned from a call to
 * [[setTimeout(interval:scala\.concurrent\.duration\.FiniteDuration)* setTimeout]].
 *
 *  May only be used to pass to [[clearTimeout]].
 */
trait SetTimeoutHandle {
  private[timers] val timerHandle: TimerHandle
}

/** <span class="badge badge-non-std" style="float: right;">Non-Standard</span>
 *  A handle returned from a call to
 *  [[setInterval(interval:scala\.concurrent\.duration\.FiniteDuration)* setInterval]].
 *
 *  May only be used to pass to [[clearInterval]].
 */
trait SetIntervalHandle {
  private[timers] val timerHandle: TimerHandle
}
