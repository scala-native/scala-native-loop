package scala.scalanative.loop

import utest._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

object TimerTests extends LoopTestSuite {
  val tests = Tests {
    def now(): Duration = System.currentTimeMillis().millis
    val d               = 200.millis
    test("delay") {
      var i         = 0
      val startTime = now()
      def checkDelay(time: Int) = {
        i += 1
        assert(i == time)
        assert(now() - startTime >= d * time)
      }
      for {
        () <- Timer.delay(d)
        _ = checkDelay(1)
        () <- Timer.delay(d)
        _ = checkDelay(2)
      } yield ()
    }
    test("repeat") {
      var i            = 0
      val startTime    = now()
      val times        = 3
      val p            = Promise[Unit]()
      var timer: Timer = null.asInstanceOf[Timer]
      timer = Timer.repeat(d) { () =>
        if (i == times) {
          p.success(())
          timer.clear()
        } else i += 1
      }
      p.future.map { _ =>
        assert(i == times)
        val took = now() - startTime
        assert(took >= d * 3)
      }
    }
    test("clear timeout") {
      val handle = Timer.timeout(d) { () =>
        throw new Exception("This timeout should have not triggered")
      }
      handle.clear()
      for {
        () <- Timer.delay(d * 2)
      } yield ()
    }
    test("close multiple times") {
      val timer = Timer.timeout(10.millis)(() => {})
      timer.clear()
      timer.clear()
      global.execute(new Runnable { def run(): Unit = timer.clear() })
      Timer.timeout(50.millis) { () =>
        timer.clear()
        timer.clear()
      }
      ()
    }
  }
}
