package scala.scalanative.loop

import utest._

abstract class LoopTestSuite extends TestSuite {
  override def utestAfterEach(path: Seq[String]): Unit = {
    EventLoop.run()
  }
}
