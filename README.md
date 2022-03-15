# scala-native-loop

Async IO and event loop for Scala Native

## What is it?

scala-native-loop provides asynchronous utilities for Scala Native.
It's backed by libuv, the same C library that the Node.js ecosystem runs on.
It currently offers:

- `scala.scalanative.loop.Timer`: to schedule callbacks to execute after a timeout
- `scala.scalanative.loop.Poll`: to schedule callbacks when data is read/written on a file descriptor
