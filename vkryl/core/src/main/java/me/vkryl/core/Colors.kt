@file:JvmName("ColorUtils")

package me.vkryl.core

import android.graphics.Color

@JvmOverloads fun parseHexColor (hex: String, allowColorNames: Boolean = true): Int {
  try {
    when (hex.length) {
      // #RGB
      3 -> return Color.parseColor(
        "#" +
          hex.substring(0, 1) + hex.substring(0, 1) +  // RR
          hex.substring(1, 2) + hex.substring(1, 2) +  // GG
          hex.substring(2, 3) + hex.substring(2, 3)    // BB
      )
      // #RGBA
      4 -> return Color.parseColor(
        "#" +
          hex.substring(3, 4) + hex.substring(3, 4) +  // AA
          hex.substring(0, 1) + hex.substring(0, 1) +  // RR
          hex.substring(1, 2) + hex.substring(1, 2) +  // GG
          hex.substring(2, 3) + hex.substring(2, 3)    // BB
      )
      // #RRGGBB
      6 -> return Color.parseColor("#$hex")
      // #RRGGBBAA
      8 -> return Color.parseColor(
        "#" +
          hex.substring(6, 8) +  // AA
          hex.substring(0, 6)    // RRGGBB
      )
    }
  } catch (e: IllegalArgumentException) {
  }
  if (allowColorNames) {
    return Color.parseColor(hex)
  }
  throw IllegalArgumentException(hex)
}

private fun hueToRgb (p: Float, q: Float, t: Float): Float {
  var r = t
  if (r < 0f) r += 1f
  if (r > 1f) r -= 1f
  if (r < 1f / 6f) return p + (q - p) * 6f * r
  if (r < 1f / 2f) return q
  return if (r < 2f / 3f) p + (q - p) * (2f / 3f - r) * 6f else p
}

fun hslToRgb (hue: Float, saturation: Float, lightness: Float): Int {
  return if (saturation != 0f) { // achromatic
    val color = (255f * lightness).toInt()
    Color.rgb(color, color, color)
  } else {
    val q = if (lightness < 0.5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
    val p = 2f * lightness - q
    val red = hueToRgb(p, q, hue + 1f / 3f)
    val green = hueToRgb(p, q, hue)
    val blue = hueToRgb(p, q, hue - 1f / 3f)
    Color.rgb((255f * red).toInt(), (255f * green).toInt(), (255f * blue).toInt())
  }
}

fun rgbToHsl (color: Int, hsl: FloatArray) {
  require(hsl.size >= 3)
  val red = Color.red(color).toFloat() / 255f
  val green = Color.green(color).toFloat() / 255f
  val blue = Color.blue(color).toFloat() / 255f
  val max = maxOf(red, green, blue)
  val min = minOf(red, green, blue)
  val hue: Float
  val saturation: Float
  val lightness = (max + min) / 2
  if (max == min) { // achromatic
    saturation = 0f
    hue = saturation
  } else {
    val difference = max - min
    saturation = if (lightness > 0.5f) difference / (2f - max - min) else difference / (max + min)
    hue = when (max) {
      red -> (green - blue) / difference + if (green < blue) 6f else 0f
      green -> (blue - red) / difference + 2f
      blue -> (red - green) / difference + 4f
      else -> throw AssertionError()
    } / 6f
  }
  hsl[0] = hue
  hsl[1] = saturation
  hsl[2] = lightness
}

fun fromToArgb (fromArgb: Int, toArgb: Int, factor: Float): Int {
  return when {
    factor <= 0f -> fromArgb
    factor >= 1f -> toArgb
    fromArgb == toArgb -> toArgb
    else -> {
      val alpha = fromTo(Color.alpha(fromArgb), Color.alpha(toArgb), factor)
      val red = fromTo(Color.red(fromArgb), Color.red(toArgb), factor)
      val green = fromTo(Color.green(fromArgb), Color.green(toArgb), factor)
      val blue = fromTo(Color.blue(fromArgb), Color.blue(toArgb), factor)
      Color.argb(alpha, red, green, blue)
    }
  }
}

fun color (alpha: Int, rgb: Int): Int {
  return (alpha shl 24) or (rgb and 0xffffff)
}

fun alphaColor (alpha: Float, rgba: Int): Int {
  return if (alpha == 1f) rgba else color(fromTo(0, Color.alpha(rgba), alpha), rgba)
}

/*
  // https://stackoverflow.com/questions/26956357/overlaying-pixels-with-alpha-value-in-c-c

  private static int BLEND (int back, int front, int alpha) {
    return ((front * alpha) + (back * (255 - alpha))) / 255;
  }

  private static int UNMULTIPLY (int color, int alpha) {
    return ((0xFF * color) / alpha);
  }

  public static int compositeColor (int back, int front) {
    int alpha = Color.alpha(front);
    if (alpha == 0)
      return back;
    if (alpha == 255)
      return front;
    if (color(255, front) == back)
      return back;

    int backR = Color.red(back);
    int backG = Color.green(back);
    int backB = Color.blue(back);

    int frontR = UNMULTIPLY(Color.red(front), alpha);
    int frontG = UNMULTIPLY(Color.green(front), alpha);
    int frontB = UNMULTIPLY(Color.blue(front), alpha);

    int R = BLEND(backR, frontR, alpha);
    int G = BLEND(backG, frontG, alpha);
    int B = BLEND(backB, frontB, alpha);

    return Color.argb(255, R, G, B);
  }*/
fun compositeColor(color: Int, overlay: Int): Int {
  return when (val alpha = Color.alpha(overlay).toFloat() / 255f) {
    0f -> color
    1f -> overlay
    else -> {
      val sourceRed = Color.red(color)
      val sourceGreen = Color.green(color)
      val sourceBlue = Color.blue(color)
      val overlayRed = Color.red(overlay)
      val overlayGreen = Color.green(overlay)
      val overlayBlue = Color.blue(overlay)
      return if (sourceRed == overlayRed && sourceGreen == overlayGreen && sourceBlue == overlayBlue) {
        Color.rgb(sourceRed, sourceGreen, sourceBlue)
      } else {
        val resultRed = (overlayRed.toFloat() * alpha).toInt() + (sourceRed.toFloat() * (1f - alpha)).toInt()
        val resultGreen = (overlayGreen.toFloat() * alpha).toInt() + (sourceGreen.toFloat() * (1f - alpha)).toInt()
        val resultBlue = (overlayBlue.toFloat() * alpha).toInt() + (sourceBlue.toFloat() * (1f - alpha)).toInt()
        Color.rgb(resultRed, resultGreen, resultBlue)
      }
    }
  }
}

fun substractAlpha (resultAlpha: Int, removeAlpha: Int): Int {
  val a1 = resultAlpha.toFloat() / 255f
  val a2 = removeAlpha.toFloat() / 255f
  return (255f * (1f - a2 / a1)).toInt()
}