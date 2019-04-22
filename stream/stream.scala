import scala.collection.mutable

trait Stream[T,U] {
  val handlers = mutable.Set[Pipe[U,_]]()

  def feed(input:T):Unit
  def done():Unit = {
    for (h <- handlers) {
      h.done()
    }
  }

  def addDestination[V](dest:Pipe[U,V]):Pipe[U,V] = {
    handlers += dest
    dest
  }

  def map[V](g:U => V):Pipe[U,V] = ???

  def mapConcat[V](g:U => Seq[V]):Pipe[U,V] = ???
}