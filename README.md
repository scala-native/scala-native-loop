# native-loop (PRE-RELEASE)
Extensible event loop and async-oriented IO for Scala Native; powered by libuv.

## UNDER CONSTRUCTION

If you're looking for the new 0.4 rewrite, check the `04` branch.  The current state of master is mostly extracted from the book [Modern Systems Programming in Scala Native](https://pragprog.com/book/rwscala/modern-systems-programming-with-scala-native).

## What is it?

scala-native-loop provides a real, asynchronous ExecutionContext implementation for Scala Native.
It's backed by libuv, the same C library that the node.js ecosystem runs on; in addition to basic 
Future dispatching, we can also use libuv to provide other basic functionality, like:

- File IO
- Pipe IO
- TCP Sockets
- UDP Sockets
- Timers

To provide a working API for practical, async Scala Native programs, we have two subprojects,
`client` and `server`, which provide an async HTTP client and server, respectively, by integrating addtional C libraries: [nodejs/http-parser](https://github.com/nodejs/http-parser) for request parsing, and [curl](https://github.com/curl/curl) for a full featured client with HTTPS support.

That said - providing a full-featured ecosystem in a single library isn't feasible - instead, we provide a `LoopExtension` trait that allows other C libraries to be integrated to the underlying event loop, in the same way that libcurl and http-parser are integrated; this opens up the possiblity of fully asynchronous bindings for postgres, redis, and many others.

## Why is this here?

To demonstrate the architectural style of a full, extensible async ecosystem for Scala Native, with an idiomatic Future-based API, implemented entirely as a library, and to start discussion about what we our priorities are.  

## LoopExtension trait

To attach a new library to the event loop, all we need to do is provide the `LoopExtension` trait:

```
trait LoopExtension {
  def activeRequests:Int
}
```

And then register the component at runtime with `EventLoop.addExtension()`. 

This is necessary because we need some way to know if there are pending IO tasks being managed by a C library, even if there are no outstanding Futures, and prevent the event loop from shutting down prematurely in that case.

## Maintenance Status

This code is a pre-release preview - I am cleaning up both the style and the implementation, 
aiming to align with Scala Native 0.4 for something more robust.  

For now, I'm filing issues to remind myself of work that needs to be done.

I'll also create a few "discussion" issues for broader conversation.

Please feel free to file additional issues with questions, comments, and concerns!

## Server API Example

```
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
```

## Streaming API Example

```
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
```