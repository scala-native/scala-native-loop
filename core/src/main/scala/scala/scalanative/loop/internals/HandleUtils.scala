package scala.scalanative.loop
package internals

import scala.scalanative.runtime._
import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.unsafe.Ptr
import LibUV._

private[loop] object HandleUtils {
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
      val rawptr = castObjectToRawPtr(obj)
      !ptrOfPtr = fromRawPtr[Byte](rawptr)
    } else {
      !ptrOfPtr = null
    }
  }
  @inline def close(handle: Ptr[Byte]): Unit = {
    val data = getData[Object](handle)
    if (data != null) {
      uv_close(handle, null)
      setData(handle, null)
    }
  }
}
