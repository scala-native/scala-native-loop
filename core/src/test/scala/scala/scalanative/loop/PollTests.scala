package scala.scalanative.loop

import utest._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.posix.unistd._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

object PollTests extends LoopTestSuite {
  def usingPipe(f: (Int, Int) => Future[Unit]): Future[Unit] = {
    val fildes = stackalloc[CInt](2)
    if (pipe(fildes) != 0) {
      throw new Exception("Failed to create pipe")
    }
    val future = f(fildes(0), fildes(1))
    future.onComplete { _ =>
      close(fildes(0))
      close(fildes(1))
    }
    future
  }

  val tests = Tests {
    test("startRead") {
      usingPipe { (r, w) =>
        val promise = Promise[Unit]()
        val byte    = 10.toByte
        val poll    = Poll(r)
        poll.startRead { i =>
          if (i != 0) {
            throw new Exception("Poll result != 0")
          }
          val buf       = stackalloc[Byte]
          val bytesRead = read(r, buf, 1L.toULong)
          assert(bytesRead == 1)
          assert(buf(0) == byte)
          promise.success(())
          poll.stop()
        }
        val buf = stackalloc[Byte]
        buf(0) = byte
        val bytesWrote = write(w, buf, 1L.toULong)
        assert(bytesWrote == 1)
        promise.future
      }
    }
  }
}
