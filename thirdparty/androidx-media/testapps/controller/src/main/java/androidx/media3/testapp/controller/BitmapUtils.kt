/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.testapp.controller

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Utilities for [Bitmap]s. */
object BitmapUtils {
  /**
   * Converts a [Drawable] to an appropriately sized [Bitmap].
   *
   * @param resources Resources for the current [android.content.Context].
   * @param drawable The [Drawable] to convert to a Bitmap.
   * @param downScale Will downscale the Bitmap to `R.dimen.app_icon_size` dp.
   * @return A Bitmap, no larger than `R.dimen.app_icon_size` dp if desired.
   */
  fun convertDrawable(resources: Resources, drawable: Drawable, downScale: Boolean): Bitmap {
    val bitmap: Bitmap
    if (drawable is BitmapDrawable) {
      bitmap = drawable.bitmap
    } else {
      bitmap =
        Bitmap.createBitmap(
          drawable.intrinsicWidth,
          drawable.intrinsicHeight,
          Bitmap.Config.ARGB_8888
        )
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
    }
    if (!downScale) {
      return bitmap
    }
    val iconSize: Int = resources.getDimensionPixelSize(R.dimen.app_icon_size)
    return if (bitmap.height > iconSize || bitmap.width > iconSize) {
      // Which needs to be scaled to fit.
      val height: Int = bitmap.height
      val width: Int = bitmap.width
      val scaleHeight: Int
      val scaleWidth: Int

      // Calculate the new size based on which dimension is larger.
      if (height > width) {
        scaleHeight = iconSize
        scaleWidth = (width * iconSize.toFloat() / height).toInt()
      } else {
        scaleWidth = iconSize
        scaleHeight = (height * iconSize.toFloat() / width).toInt()
      }
      Bitmap.createScaledBitmap(bitmap, scaleWidth, scaleHeight, false)
    } else {
      bitmap
    }
  }

  /**
   * Creates a Material Design compliant [androidx.appcompat.widget.Toolbar] icon from a given full
   * sized icon.
   *
   * @param resources Resources for the current [android.content.Context].
   * @param icon The bitmap to convert.
   * @return A scaled Bitmap of the appropriate size and in-built padding.
   */
  fun createToolbarIcon(resources: Resources, icon: Bitmap): Bitmap {
    val padding: Int = resources.getDimensionPixelSize(R.dimen.margin_small)
    val iconSize: Int = resources.getDimensionPixelSize(R.dimen.toolbar_icon_size)
    val sizeWithPadding = iconSize + 2 * padding

    // Create a Bitmap backed Canvas to be the toolbar icon.
    val toolbarIcon: Bitmap =
      Bitmap.createBitmap(sizeWithPadding, sizeWithPadding, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(toolbarIcon)
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    // Resize the app icon to Material Design size.
    val scaledIcon: Bitmap = Bitmap.createScaledBitmap(icon, iconSize, iconSize, false)
    canvas.drawBitmap(scaledIcon, padding.toFloat(), padding.toFloat(), null)
    return toolbarIcon
  }
}
