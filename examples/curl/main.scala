import scala.scalanative.loop._
import LibCurlConstants._
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  def main(args: Array[String]): Unit = {
    Curl.startRequest(GET, "http://www.example.com", Seq()).map { response =>
      println(s"got response: $response")
    }
  }
}
