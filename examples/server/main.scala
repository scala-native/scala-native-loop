import scala.scalanative.loop._
import scala.concurrent._
import scala.concurrent.duration._

object Main {
  implicit val ec: ExecutionContext = EventLoop

  def main(args: Array[String]): Unit = {
    Server.init(9999) { (r, c) =>
      println(s"received request $r on connection $c")
      Server.respond(
        c,
        200,
        "OK",
        Seq(("Content-Type", "text/plain"), ("Content-Length", "6")),
        "hello!"
      )
    }

    EventLoop.run()
  }
}
