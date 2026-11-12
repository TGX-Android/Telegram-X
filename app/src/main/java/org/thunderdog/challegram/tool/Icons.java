/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 02/11/2016
 */
package org.thunderdog.challegram.tool;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;

import me.vkryl.core.BitwiseUtils;

public class Icons {
  public static void reset () {
    livePinIcon = null;
    chatSelfDrawable = null;
    secureDrawable = null;
    secureSmallDrawable = null;
    if (sparseDrawables != null)
      sparseDrawables.clear();
    if (bitmaps != null) {
      bitmaps.clear();
    }
    livePinIcon = null;
  }

  private static Bitmap livePinIcon;

  private static Drawable chatSelfDrawable;
  public static Drawable getChatSelfDrawable () {
    if (chatSelfDrawable == null) {
      chatSelfDrawable = Drawables.get(UI.getResources(), R.drawable.baseline_bookmark_24);
    }
    return chatSelfDrawable;
  }

  public static int getEditedIconWidth () {
    return Screen.dp(12f);
  }

  private static Drawable secureDrawable;
  public static Drawable getSecureDrawable () {
    if (secureDrawable == null) {
      secureDrawable = Drawables.get(UI.getResources(), R.drawable.deproko_baseline_lock_24);
    }
    return secureDrawable;
  }

  private static Drawable secureSmallDrawable;
  public static Drawable getSecureSmallDrawable () {
    if (secureSmallDrawable == null) {
      secureSmallDrawable = Drawables.get(UI.getResources(), R.drawable.deproko_baseline_lock_18);
    }
    return secureSmallDrawable;
  }

  // Sparse

  private static LongSparseArray<Drawable> sparseDrawables;

  public static @Nullable Drawable getSparseDrawable (@DrawableRes int res, @ColorId int colorId) {
    if (colorId == 0) {
      return null;
    }
    if (!(res == R.drawable.deproko_baseline_check_single_24 ||
      res == R.drawable.deproko_baseline_check_double_24 ||
      res == R.drawable.deproko_baseline_clock_24 ||
      res == R.drawable.deproko_baseline_notifications_off_24)) {
      return null;
    }
    long key = BitwiseUtils.mergeLong(res, colorId);
    if (sparseDrawables == null) {
      sparseDrawables = new LongSparseArray<>();
      Drawable drawable = Drawables.get(UI.getResources(), res);
      sparseDrawables.put(key, drawable);
      return drawable;
    }
    Drawable drawable = sparseDrawables.get(key);
    if (drawable == null) {
      drawable = Drawables.get(UI.getResources(), res);
      sparseDrawables.put(key, drawable);
    }
    return drawable;
  }

  // In a display list a VectorDrawable of 18 dp takes about 9 µs, a bitmap less than 1
  private static SparseArrayCompat<Bitmap> bitmaps;

  public static Bitmap getBitmap (@DrawableRes int res) {
    if (bitmaps == null) {
      bitmaps = new SparseArrayCompat<>();
    }
    Bitmap bitmap = bitmaps.get(res);
    if (bitmap == null) {
      bitmap = Drawables.getBitmap(res);
      bitmaps.put(res, bitmap);
    }
    return bitmap;
  }

  public static void drawBitmap (
    Canvas c,
    @DrawableRes int res,
    float x,
    float y,
    @Nullable Paint paint
  ) {
    c.drawBitmap(getBitmap(res), x, y, paint);
  }

  public static void drawBitmapRtl (
    Canvas c,
    @DrawableRes int res,
    float x,
    float y,
    @Nullable Paint paint,
    int viewWidth,
    boolean rtl
  ) {
    Bitmap bitmap = getBitmap(res);
    c.drawBitmap(bitmap, rtl ? viewWidth - x - bitmap.getWidth() : x, y, paint);
  }

  public static void drawBitmapOrVector (
    Canvas c,
    @DrawableRes int res,
    Drawable vector,
    boolean isScaled,
    float x,
    float y,
    @Nullable Paint paint
  ) {
    // Scaled on the GPU, a bitmap looks sharper than its vector, at its own size the same
    if (isScaled || res == 0) {
      Drawables.draw(c, vector, x, y, paint);
    } else {
      drawBitmap(c, res, x, y, paint);
    }
  }

  private static Drawable calendarSmallDrawable;

  public static Drawable getCalendarSmallDrawable () {
    if (calendarSmallDrawable == null)
      calendarSmallDrawable = Drawables.get(R.drawable.baseline_watch_later_10);
    return calendarSmallDrawable;
  }

  public static Bitmap getLivePin () {
    if (livePinIcon == null) {
      livePinIcon = BitmapFactory.decodeResource(UI.getResources(), R.drawable.bg_livepin);
    }
    return livePinIcon;
  }

  public static int getChatMuteDrawableWidth () {
    return Screen.dp(18f);
  }

  public static int getSingleTickWidth () {
    return Screen.dp(18f);
  }

  public static int getClockIconWidth () {
    return Screen.dp(12f);
  }

  public static float CLOCK_SHIFT_X = 6f;
  public static float CLOCK_SHIFT_Y = 6f;

  public static float TICKS_SHIFT_X = 3f;
  public static float TICKS_SHIFT_Y = 6f;
}
