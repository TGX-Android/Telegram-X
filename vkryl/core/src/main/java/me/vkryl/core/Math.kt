@file:JvmName("MathUtils")

package me.vkryl.core

import kotlin.math.*
import kotlin.random.Random

fun fromTo (from: Float, to: Float, factor: Float): Float {
  return if (from != to) from + (to - from) * factor else from
}
fun fromTo (from: Int, to: Int, factor: Float): Int {
  return fromTo(from.toFloat(), to.toFloat(), factor).roundToInt()
}

@JvmOverloads
fun clamp (value: Float, min: Float = 0.0f, max: Float = 1.0f) = value.coerceIn(min .. max)
@JvmOverloads
fun clamp (value: Double, min: Double = 0.0, max: Double = 1.0) = value.coerceIn(min .. max)
fun clamp (value: Int, min: Int, max: Int) = value.coerceIn(min .. max)

fun modulo (x: Int, y: Int): Int = if (x >= 0) x % y else y - -x % y
fun modulo (x: Float, y: Float): Float = if (x >= 0) x % y else y - -x % y

fun distance (x1: Float, y1: Float, x2: Float, y2: Float): Float {
  val xDiff = x2 - x1
  val yDiff = y2 - y1
  return sqrt(xDiff * xDiff + yDiff * yDiff)
}

fun roundDouble (x: Double): Double {
  return round(x * 1e6) / 1e6
}

fun random(min: Int, max: Int): Int = min + round(Random.nextDouble() * (max - min).toDouble()).toInt()

fun aspectRatio (width: Int, height: Int): Float {
  return if (width != 0 && height != 0) {
    max(width, height).toFloat() / min(width, height).toFloat()
  } else {
    0.0f
  }
}

fun pickNumber (max: Int, basedOn: String): Int {
  var sum: Long = 0
  for (i in basedOn.indices) {
    sum += basedOn.codePointAt(i)
  }
  return (sum % max).toInt()
}