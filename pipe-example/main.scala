import LibUV._, LibUVConstants._
import scalanative.native._

object Main {
  implicit val ec = EventLoop
  def main(args:Array[String]):Unit = {
    val p = FilePipe(c"./data.txt")
    .map { d => 
      println(s"consumed $d") 
      d
    }.addDestination(Tokenizer("\n"))
    .addDestination(Tokenizer(" "))
    .map { d => d + "\n" }
    .addDestination(FileOutputPipe(c"./output.txt", false))
    println("running")
    uv_run(EventLoop.loop,UV_RUN_DEFAULT)
  }
}