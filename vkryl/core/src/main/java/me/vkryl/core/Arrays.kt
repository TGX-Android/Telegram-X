@file:JvmName("ArrayUtils")

package me.vkryl.core

import android.os.Build
import android.util.SparseIntArray
import androidx.collection.LongSparseArray
import androidx.collection.SparseArrayCompat
import me.vkryl.core.lambda.Destroyable
import me.vkryl.core.lambda.Filter
import me.vkryl.core.lambda.RunnableData
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.random.Random

@JvmField
val EMPTY_INTS = intArrayOf()
@JvmField
val EMPTY_LONGS = longArrayOf()

fun indexOf(array: IntArray?, item: Int): Int = array?.indexOf(item) ?: -1
fun indexOf(array: CharArray?, item: Char): Int = array?.indexOf(item) ?: -1
fun indexOf(array: LongArray?, item: Long): Int = array?.indexOf(item) ?: -1
fun indexOf(array: FloatArray?, item: Float): Int = array?.indexOfFirst { it == item } ?: -1
fun <T> indexOf(array: Array<T>?, item: T?): Int = array?.indexOf(item) ?: -1

fun contains(array: IntArray?, item: Int): Boolean = array?.contains(item) ?: false
fun contains(array: CharArray?, item: Char): Boolean = array?.contains(item) ?: false
fun contains(array: LongArray?, item: Long): Boolean = array?.contains(item) ?: false
fun contains(array: FloatArray?, item: Float): Boolean = array?.any { it == item } ?: false
fun <T> contains(array: Array<T>?, item: T?): Boolean = array?.contains(item) ?: false

fun toString(array: IntArray?, limit: Int = array?.size ?: 0): String = array?.joinToString(", ", "[", "]", limit, "") ?: "null"
fun toString(array: LongArray?, limit: Int = array?.size ?: 0): String = array?.joinToString(", ", "[", "]", limit, "") ?: "null"

fun clear(array: FloatArray) = array.fill(0.0f)
fun <T> clear(array: Array<T?>) = array.fill(null)

fun min(array: IntArray): Int = array.minOrNull() ?: 0

fun sum(array: IntArray): Int = array.sum()
@JvmOverloads
fun sum(array: FloatArray, count: Int = array.size): Float {
  return if (count == array.size) {
    array.sum()
  } else {
    var result = 0.0f
    for (i in 0 until min(count, array.size)) {
      result += array[i]
    }
    result
  }
}

fun findIntersection(a: LongArray?, b: LongArray?): Long {
  a?.isNotEmpty()?.let {
    b?.isNotEmpty()?.let {
      for (x in a) {
        if (x == 0L)
          continue
        for (y in b) {
          if (x == y)
            return x
        }
      }
    }
  }
  return 0
}
fun intersect(a: LongArray?, b: LongArray?): LongArray? {
  a?.isNotEmpty()?.let {
    b?.isNotEmpty()?.let {
      return a.intersect(b.toSet()).toLongArray()
    }
  }
  return null
}

fun equalsSorted(a: IntArray?, b: IntArray?): Boolean {
  if (a === b)
    return true
  a?.let {
    b?.let {
      if (a.size == b.size) {
        a.sort()
        b.sort()
        return a.contentEquals(b)
      }
    }
  }
  return false
}

fun IntArray.addElement(item: Int): IntArray {
  val result = this.copyOf(this.size + 1)
  result[this.size] = item
  return result
}
fun LongArray.addElement(item: Long): LongArray {
  val result = this.copyOf(this.size + 1)
  result[this.size] = item
  return result
}

fun IntArray.removeElement(position: Int): IntArray {
  if (position < 0) return this
  val result = IntArray(this.size - 1)
  if (position > 0) {
    System.arraycopy(this, 0, result, 0, position)
  }
  if (position < this.size - 1) {
    System.arraycopy(this, position + 1, result, position, this.size - position - 1)
  }
  return result
}
fun LongArray.removeElement(position: Int): LongArray {
  if (position < 0) return this
  val result = LongArray(this.size - 1)
  if (position > 0) {
    System.arraycopy(this, 0, result, 0, position)
  }
  if (position < this.size - 1) {
    System.arraycopy(this, position + 1, result, position, this.size - position - 1)
  }
  return result
}
fun <T> Array<T>.removeElement(position: Int, out: Array<T>): Array<T> {
  if (position > 0) {
    System.arraycopy(this, 0, out, 0, position)
  }
  if (position < this.size - 1) {
    System.arraycopy(this, position + 1, out, position, this.size - position - 1)
  }
  return out
}

fun removeAll(array: IntArray, itemsToRemove: IntArray): IntArray {
  val result = array.toMutableList()
  return if (result.removeAll(itemsToRemove.toMutableList())) {
    result.toIntArray()
  } else {
    array
  }
}
fun removeAll(array: LongArray, itemsToRemove: LongArray): LongArray {
  val result = array.toMutableList()
  return if (result.removeAll(itemsToRemove.toMutableList())) {
    result.toLongArray()
  } else {
    array
  }
}

fun <T> asList(vararg items: T?): MutableList<T?> {
  val result = ArrayList<T?>(items.size)
  result.addAll(items)
  return result
}

fun <T> MutableList<T?>.filter(filter: Filter<T?>): List<T?> {
  if (this.isEmpty())
    return Collections.emptyList()
  val result = ArrayList<T?>(this.size)
  for (item in this) {
    if (filter.accept(item)) {
      result.add(item)
    }
  }
  return result
}

@JvmOverloads
fun <T> Array<T?>.resize(newLength: Int, removeCallback: RunnableData<T>? = null): Array<T?> {
  if (this.size == newLength) return this
  val newArray = this.copyOf(newLength)
  if (removeCallback != null) {
    for (i in newLength until this.size) {
      this[i]?.let { removedItem ->
        removeCallback.runWithData(removedItem)
      }
    }
  }
  return newArray
}

fun <T : Destroyable> Array<T?>.resize(newLength: Int): Array<T?>? {
  return this.resize(newLength) { item: T? ->
    item?.performDestroy()
  }
}

fun LongArray.shuffle () {
  for (i in this.size - 1 downTo 0) {
    val index = Random.nextInt(i + 1)
    val item = this[index]
    this[index] = this[i]
    this[i] = item
  }
}

fun asArray(list: List<Long>): LongArray = list.toLongArray()
fun <T> asArray(array: LongSparseArray<T>, out: Array<T>): Array<T> {
  for (i in 0 until array.size()) {
    out[i] = array.valueAt(i)
  }
  return out
}
fun <T> asArray(array: SparseArrayCompat<T>, out: Array<T>): Array<T> {
  for (i in 0 until array.size()) {
    out[i] = array.valueAt(i)
  }
  return out
}
fun toArray(list: List<Int>, out: IntArray) {
  require(list.size == out.size)
  for (i in list.indices) {
    out[i] = list[i]
  }
}

fun <T> MutableList<T>.move(fromPosition: Int, toPosition: Int) {
  if (fromPosition != toPosition) {
    val item = this[fromPosition]
    removeAt(fromPosition)
    add(toPosition, item)
  }
}

fun List<*>.ensureCapacity(size: Int) {
  if (this is ArrayList<*>) {
    this.ensureCapacity(size)
  }
}

fun List<*>.trimToSize () {
  if (this is ArrayList<*>) {
    this.trimToSize()
  }
}

fun <T> LongSparseArray<T>.keys (): LongArray {
  val keys = LongArray(this.size())
  for (i in 0 until this.size()) {
    keys[i] = this.keyAt(i)
  }
  return keys
}

fun SparseIntArray.increment (key: Int): Int {
  val index = indexOfKey(key)
  val value = if (index >= 0) {
    valueAt(index) + 1
  } else {
    1
  }
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && index >= 0) {
    this.setValueAt(index, value)
  } else {
    this.put(key, value)
  }
  return value
}

fun <T> SparseArrayCompat<T>.removeWithKey(key: Int): T? {
  val index = this.indexOfKey(key)
  return if (index >= 0) {
    val result = this.valueAt(index)
    this.removeAt(index)
    result
  } else {
    null
  }
}

fun <T> LongSparseArray<T>.move(fromKey: Long, toKey: Long) {
  if (fromKey != toKey) {
    val index = this.indexOfKey(fromKey)
    if (index >= 0) {
      val value = this.valueAt(index)
      this.removeAt(index)
      this.put(toKey, value)
    }
  }
}

fun LongSparseArray<LongArray>?.contentEquals(to: LongSparseArray<LongArray>?): Boolean {
  return when {
    this == null && to == null -> true
    this == null || to == null || this.size() != to.size() -> false
    else -> {
      for (i in 0 .. size()) {
        if (keyAt(i) != to.keyAt(i) || !Arrays.equals(valueAt(i), to.valueAt(i)))
          return false
      }
      true
    }
  }
}

fun <T : Comparable<T>> ArrayList<T>.addSorted(element: T): Int {
  val index = Collections.binarySearch(this, element)
  if (index >= 0)
    throw IllegalArgumentException()
  val at = (-index) - 1
  this.add(at, element)
  return at
}

fun <T : Comparable<T>> ArrayList<T>.removeSorted(element: T): Boolean {
  val index = Collections.binarySearch(this, element)
  return if (index >= 0) {
    this.removeAt(index)
    true
  } else {
    false
  }
}