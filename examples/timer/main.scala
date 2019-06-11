import scala.scalanative.loop._
import scala.concurrent._
import scala.concurrent.duration._

object Main {
  implicit val ec:ExecutionContext = EventLoop

  def main(args:Array[String]):Unit = {
    Timer.delay(3 seconds).flatMap { _ =>
      println("beep")
      Timer.delay(2 seconds)
    }.flatMap { _ =>
      println("boop")
      Timer.delay(1 second)
    }.onComplete { _ =>
      println("done")
    }

    EventLoop.run()
  }
}