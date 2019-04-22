import scala.concurrent.Future
import argonaut._, Argonaut._
import LibUVConstants._, LibUV.uv_run, ServiceHelpers._
import scalanative.native._

object Main {
  implicit val ec = EventLoop

  def main(args:Array[String]):Unit = {
    Service()
      .getAsync("/async") { r => Future { 
          s"got (async routed) request $r"
        }.map { message => OK(
            Map("asyncMessage" -> message)      
          )
        }
      }      
      .getAsync("/fetch/example") { r => 
        Curl.get(c"https://www.example.com").map { response =>
          Response(200,"OK",Map(),response.body)
        }
      }
      .get("/") { r => OK {
          Map("default_message" -> s"got (default routed) request $r")
        }
      }
      .run(9999)
    uv_run(EventLoop.loop, UV_RUN_DEFAULT)
  }
}