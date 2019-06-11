import scala.scalanative.loop._
import scala.concurrent._
import scala.concurrent.duration._
import LibCurlConstants._

object Main {
  implicit val ec:ExecutionContext = EventLoop

  def main(args:Array[String]):Unit = {
    Curl.startRequest(GET,"http://www.example.com",Seq()).map { response =>
      println(s"got response: $response")
    }

    EventLoop.run()
  }
}