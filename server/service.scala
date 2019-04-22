import scala.concurrent.{Future, ExecutionContext}
import argonaut._, Argonaut._

// START:main
object Main {
  import LibUVConstants._, LibUV.uv_run, ServiceHelpers._
  implicit val ec = EventLoop

  def main(args:Array[String]):Unit = {
    Service()
      .getAsync("/async/") { r => Future(OK(
        Map("asyncMessage" -> s"got (async routed) request $r")
      ))}
      .get("/") { r => OK( 
        Map("message" -> s"got (routed) request $r")
      )}
      .run(9999)
    uv_run(EventLoop.loop, UV_RUN_DEFAULT)
    println("done")
  }
}
// END:main

// START:helpers
object ServiceHelpers {
  def OK[T](body:T, headers:Map[String,String] = Map.empty)
           (implicit e:EncodeJson[T]):Response[String] = {
    val b = body.asJson.nospaces
    Response(200,"OK",headers,b)
  }
}
// END:helpers

// START:Service
case class Service(routes:Seq[Route] = Seq.empty)(implicit ec:ExecutionContext) {
  def dispatch(req:Request[String]):Route = {
    for (route <- routes) {
      if (req.method == route.method && req.url.startsWith(route.path)) {
        println(s"matched route ($route)")  
        return route
      }
    }
    throw new Exception("no match!")
  }

  def run(port:Int) = {
    Server.init(port, this.dispatch)
  }
// END:Service
// START:getRoutes
  def get(path:String)(h:Request[String] => Response[String]):Service = {    
    return Service(this.routes :+ SyncRoute("GET",path,h))
  }
  def getAsync(path:String)(h:Request[String] => Future[Response[String]]):Service = {
    return Service(this.routes :+ AsyncRoute("GET",path,h))
  }
// END:getRoutes
// START:postRoutes
// TODO
  def post[I,O](f:Request[I] => Response[O])(implicit d:DecodeJson[I]):Service = {
    ???
  }
  def postAsync[I,O](f:Request[I] => Future[Response[O]])(implicit d:DecodeJson[I]):Service = {
    ???
  }
// END:postRoutes
}
