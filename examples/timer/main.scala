import scala.scalanative.loop._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  def main(args: Array[String]): Unit = {
    Timer
      .delay(3.seconds)
      .flatMap { _ =>
        println("beep")
        Timer.delay(2.seconds)
      }
      .flatMap { _ =>
        println("boop")
        Timer.delay(1.second)
      }
      .onComplete { _ =>
        println("done")
      }
  }
}
