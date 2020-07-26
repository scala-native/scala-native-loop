package scala.scalanative.loop
package internals

import scala.scalanative.runtime._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.unsafe.Ptr
import scala.scalanative.libc.stdlib
import scala.collection.mutable
import LibUV._

private[loop] object HandleUtils {
  private val references = mutable.Map.empty[Object, Int]

  @inline def getData[T <: Object](handle: Ptr[Byte]): T = {
    // data is the first member of uv_loop_t
    val ptrOfPtr = handle.asInstanceOf[Ptr[Ptr[Byte]]]
    val dataPtr = !ptrOfPtr
    if (dataPtr == null) null.asInstanceOf[T]
    else {
      val rawptr = toRawPtr(dataPtr)
      castRawPtrToObject(rawptr).asInstanceOf[T]
    }
  }
  @inline def setData(handle: Ptr[Byte], obj: Object): Unit = {
    // data is the first member of uv_loop_t
    val ptrOfPtr = handle.asInstanceOf[Ptr[Ptr[Byte]]]
    if(obj != null) {
      if (references.contains(obj)) references(obj) += 1
      else references(obj) = 1
      val rawptr = castObjectToRawPtr(obj)
      !ptrOfPtr = fromRawPtr[Byte](rawptr)
    } else {
      !ptrOfPtr = null
    }
  }
  private val onCloseCB = new CloseCB {
    def apply(handle: UVHandle): Unit = {
      stdlib.free(handle)
    }
  }
  @inline def close(handle: Ptr[Byte]): Unit = {
    if(getData(handle) != null) {
      uv_close(handle, onCloseCB)
      val data    = getData[Object](handle)
      val current = references(data)
      if (current > 1) references(data) -= 1
      else references.remove(data)
      setData(handle, null)
    }
  }
}
