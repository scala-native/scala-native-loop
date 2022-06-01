package scala.scalanative.loop
package internals

import scala.scalanative.runtime._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.unsafe.Ptr
import scala.scalanative.libc.stdlib
import LibUV._

private[loop] object HandleUtils {
  private val references = new java.util.IdentityHashMap[Object, Int]()

  @inline def getData[T <: Object](handle: Ptr[Byte]): T = {
    // data is the first member of uv_loop_t
    val ptrOfPtr = handle.asInstanceOf[Ptr[Ptr[Byte]]]
    val dataPtr  = !ptrOfPtr
    if (dataPtr == null) null.asInstanceOf[T]
    else {
      val rawptr = toRawPtr(dataPtr)
      castRawPtrToObject(rawptr).asInstanceOf[T]
    }
  }
  @inline def setData(handle: Ptr[Byte], obj: Object): Unit = {
    // data is the first member of uv_loop_t
    val ptrOfPtr = handle.asInstanceOf[Ptr[Ptr[Byte]]]
    if (obj != null) {
      references.put(obj, references.get(obj) + 1)
      val rawptr = castObjectToRawPtr(obj)
      !ptrOfPtr = fromRawPtr[Byte](rawptr)
    } else {
      !ptrOfPtr = null
    }
  }
  private val onCloseCB: CloseCB = (handle: UVHandle) => {
    stdlib.free(handle)
  }
  @inline def close(handle: Ptr[Byte]): Unit = {
    if (getData(handle) != null) {
      uv_close(handle, onCloseCB)
      val data    = getData[Object](handle)
      val current = references.get(data)
      if (current > 1) references.put(data, current - 1)
      else references.remove(data)
      setData(handle, null)
    }
  }
}
