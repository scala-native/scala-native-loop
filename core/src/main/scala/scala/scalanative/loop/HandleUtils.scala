package scala.scalanative.loop

import scala.scalanative.runtime.Intrinsics._
import scala.scalanative.unsafe.Ptr
import scala.scalanative.libc.stdlib
import scala.collection.mutable
import LibUV._

private [loop] object HandleUtils {
  private val references = mutable.Map.empty[Long, Int]

  @inline def getData[T <: Object](handle: Ptr[Byte]): T = {
    val data = LibUV.uv_handle_get_data(handle)
    val rawptr = castLongToRawPtr(data)
    castRawPtrToObject(rawptr).asInstanceOf[T]
  }
  @inline def setData[T <: Object](handle: Ptr[Byte], function: T): Unit = {
    val rawptr = castObjectToRawPtr(function)
    val data = castRawPtrToLong(rawptr)
    if(references.contains(data)) references(data) += 1
    else references(data) = 1
    LibUV.uv_handle_set_data(handle, data)
  }
  private val onCloseCB = new CloseCB {
    def apply(handle: UVHandle): Unit = {
      stdlib.free(handle)
    }
  }
  @inline def close(handle: Ptr[Byte]): Unit = {
    uv_close(handle, onCloseCB)
    val data = LibUV.uv_handle_get_data(handle)
    val current = references(data)
    if(current > 1) references(data) -= 1
    else references.remove(data)
  }
}
