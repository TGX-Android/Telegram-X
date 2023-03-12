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
 * File created on 06/06/2017
 */
package org.thunderdog.challegram.tool;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.util.Arrays;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class DrawAlgorithms {
  public static void drawMark (Canvas c, float cx, float cy, float factor, int lineHeight, Paint paint) {
    int lineRadius = lineHeight / 2;
    float f1 = factor < .5f ? AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor / .5f) : 1f;
    float f2 = factor <= .5f ? 0f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((factor - .5f) / .5f);
    if (Lang.rtl()) {
      if (f1 > 0f) {
        int d = (int) ((float) lineHeight * f1);
        c.drawLine(cx - lineRadius, cy - lineRadius, cx - lineRadius + d, cy - lineRadius + d, paint);
      }
      if (f2 > 0f) {
        int d = (int) ((float) lineHeight * f2);
        c.drawLine(cx + lineRadius, cy - lineRadius, cx + lineRadius - d, cy - lineRadius + d, paint);
      }
    } else {
      if (f1 > 0f) {
        int d = (int) ((float) lineHeight * f1);
        c.drawLine(cx + lineRadius, cy - lineRadius, cx + lineRadius - d, cy - lineRadius + d, paint);
      }
      if (f2 > 0f) {
        int d = (int) ((float) lineHeight * f2);
        c.drawLine(cx - lineRadius, cy - lineRadius, cx - lineRadius + d, cy - lineRadius + d, paint);
      }
    }
  }

  public static void drawRoundRect (Canvas c, float radius, float left, float top, float right, float bottom, Paint paint) {
    drawRoundRect(c, radius, radius, radius, radius, left, top, right, bottom, paint);
  }
  public static void drawRoundRect (Canvas c, float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius, float left, float top, float right, float bottom, Paint paint) {
    float radius = Math.max(topLeftRadius, Math.max(topRightRadius, Math.max(bottomRightRadius, bottomLeftRadius)));
    if (radius > 0) {
      RectF rectF = Paints.getRectF();
      rectF.set(left, top, right, bottom);
      if (topLeftRadius != radius || topRightRadius != radius || bottomRightRadius != radius || bottomLeftRadius != radius) {
        Path path = Paints.getPath();
        path.reset();
        buildPath(path, rectF, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
        c.drawPath(path, paint);
        path.reset();
      } else {
        c.drawRoundRect(rectF, radius, radius, paint);
      }
    } else {
      c.drawRect(left, top, right, bottom, paint);
    }
  }

  public static void drawParticles (Canvas c, float radius, float left, float top, float right, float bottom, float alpha) {
    drawParticles(c, radius, radius, radius, radius, left, top, right, bottom, alpha);
  }

  public static void drawParticles (Canvas c, float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius, float left, float top, float right, float bottom, float alpha) {
    // TODO

    float centerX = left + (right - left) / 2f;
    float centerY = top + (bottom - top) / 2f;

    Drawable drawable = Drawables.get(R.drawable.deproko_baseline_whatshot_16);

    c.drawCircle(centerX, centerY,
      Math.max(drawable.getMinimumWidth(), drawable.getMinimumHeight()) / 2f * 1.65f,
      Paints.fillingPaint(ColorUtils.alphaColor(alpha, 0x44000000))
    );

    Drawables.drawCentered(c, drawable, centerX, centerY, PorterDuffPaint.get(R.id.theme_color_white, alpha));
  }
  public static void drawReceiver (Canvas c, Receiver preview, Receiver receiver, boolean clearPreview, boolean needPlaceholder, int left, int top, int right, int bottom) {
    drawReceiver(c, preview, receiver, clearPreview, needPlaceholder, left, top, right, bottom, 1f, 1f);
  }

  public static void drawReceiver (Canvas c, Receiver preview, Receiver receiver, boolean clearPreview, boolean needPlaceholder, int left, int top, int right, int bottom, float previewScale, float scale) {
    if (preview != null) {
      if (receiver == null || receiver.needPlaceholder()) {
        boolean needScale = previewScale != 1f;
        int saveCount = needScale ? Views.save(c) : -1;
        if (needScale) {
          c.scale(previewScale, previewScale, left + (right - left) / 2f, top + (bottom - top) / 2f);
        }

        preview.setBounds(left, top, right, bottom);
        if (needPlaceholder && preview.needPlaceholder()) {
          preview.drawPlaceholder(c);
        }
        preview.draw(c);
        if (needScale) {
          Views.restore(c, saveCount);
        }
      } else {
        preview.setBounds(left, top, right, bottom);
        if (clearPreview) {
          preview.clear();
        }
      }
    }
    if (receiver != null) {
      boolean needScale = scale != 1f;
      int saveCount = needScale ? Views.save(c) : -1;
      if (needScale) {
        c.scale(scale, scale, left + (right - left) / 2f, top + (bottom - top) / 2f);
      }
      receiver.setBounds(left, top, right, bottom);
      receiver.draw(c);
      if (needScale) {
        Views.restore(c, saveCount);
      }
    }
  }

  public static void drawCross (Canvas c, float cx, float cy, float factor, @ColorInt int iconColor, @ColorInt int backgroundColor) {
    drawCross(c, cx, cy, factor, iconColor, backgroundColor, Screen.dp(23f));
  }
  public static void drawCross (Canvas c, float cx, float cy, float factor, @ColorInt int iconColor, @ColorInt int backgroundColor, int lineHeight) {
    if (factor <= 0f) {
      return;
    }

    c.save();
    c.rotate(-45f, cx, cy);

    int lineWidth = Screen.dp(2f);
    int silentWidth = Screen.dp(1.5f);

    int padding = Screen.dp(1f);
    int lineTop = (int) (cy - lineHeight * .5f) + padding;
    int x = (int) (cx - padding) - Screen.dp(.5f);

    c.clipRect(x, lineTop, x + lineWidth + silentWidth, lineTop + lineHeight * factor);
    RectF rectF = Paints.getRectF();
    rectF.set(x, lineTop, x + lineWidth, lineTop + lineHeight);

    c.drawRoundRect(rectF, lineWidth / 2, lineWidth / 2, Paints.fillingPaint(iconColor));
    c.drawRect(x + lineWidth, lineTop, x + lineWidth + silentWidth, lineTop + lineHeight, Paints.fillingPaint(backgroundColor));

    c.restore();
  }

  public static void drawHorizontalDirection (Canvas c, float cx, float cy, @ColorInt int lineColor, boolean isRight) {
    c.save();

    int lineWidth = Screen.dp(2f);
    int lineHeight = Screen.dp(9f);

    int lineLeft = (int) cx - lineHeight / 2;
    int lineBottom = (int) cy + lineHeight / 2;

    c.translate(0, -lineHeight / 2);
    c.rotate(45f, lineLeft, lineBottom);
    c.translate(0, -Screen.dp(5f));
    if (isRight) {
      c.drawRect(lineLeft, lineBottom + lineHeight, lineLeft - lineWidth, lineBottom + lineWidth, Paints.fillingPaint(lineColor));
      c.drawRect(lineLeft, lineBottom + lineWidth, lineLeft - lineHeight, lineBottom, Paints.fillingPaint(lineColor));
    } else {
      c.drawRect(lineLeft, lineBottom - lineHeight, lineLeft + lineWidth, lineBottom - lineWidth, Paints.fillingPaint(lineColor));
      c.drawRect(lineLeft, lineBottom - lineWidth, lineLeft + lineHeight, lineBottom, Paints.fillingPaint(lineColor));
    }

    c.restore();
  }

  public static void drawCollapse (Canvas c, float cx, float cy, @ColorInt int color, float openFactor, float crossFactor) {
    Paint paint = Paints.getProgressPaint(color, Screen.dp(2f));

    double rad = Math.toRadians(MathUtils.fromTo(135f, 45f, openFactor));

    float distance = Screen.dp(6f);

    float diffX = (float) ((double) distance * Math.sin(rad));
    float diffY = (float) ((double) distance * Math.cos(rad));

    cy -= (diffY / 2f) * (1f - crossFactor);

    float y = cy + diffY;

    c.drawLine(cx + diffX, y, cx - diffX * crossFactor, cy - diffY * crossFactor, paint);
    c.drawLine(cx - diffX, y, cx + diffX * crossFactor, cy - diffY * crossFactor, paint);
  }

  public static void drawDirection (Canvas c, float cx, float cy, @ColorInt int lineColor, int gravity) {
    c.save();

    int lineWidth = Screen.dp(2f);
    int lineHeight = Screen.dp(9f);

    int rotation = 45;
    switch (gravity) {
      case Gravity.TOP:
        rotation += 90;
        break;
      case Gravity.BOTTOM:
        rotation -= 90;
        break;
      case Gravity.RIGHT:
        rotation += 180;
        break;
      case Gravity.LEFT:
        // nothing
        break;
    }
    c.rotate(rotation, cx, cy);
    c.drawRect(cx, cy - lineHeight, cx + lineWidth, cy, Paints.fillingPaint(lineColor));
    c.drawRect(cx + lineWidth, cy - lineWidth, cx + lineHeight, cy, Paints.fillingPaint(lineColor));

    c.restore();
  }

  public static void drawIcon (Canvas c, Receiver receiver, float degrees, float factor, int contentReplaceColor, Drawable drawable, Paint drawablePaint) {
    if (factor == 0f) {
      return;
    }
    
    float innerRadius = Screen.dp(4.5f);
    float outerRadius = innerRadius + Screen.dp(2f);
    double radians = Math.toRadians(degrees);
    float x = receiver.centerX() + (float) ((double) (receiver.getWidth() / 2) * Math.sin(radians));
    float y = receiver.centerY() + (float) ((double) (receiver.getHeight() / 2) * Math.cos(radians));

    c.drawCircle(x, y, outerRadius * factor, Paints.fillingPaint(contentReplaceColor));
    // c.drawCircle(x, y, innerRadius * factor, Paints.fillingPaint(backgroundColor));

    c.save();
    c.scale(factor, factor, x, y);
    Drawables.draw(c, drawable, x - drawable.getMinimumWidth() / 2f, y - drawable.getMinimumHeight() / 2f, drawablePaint);
    c.restore();
  }

  public static void drawOnline (Canvas c, Receiver receiver, float onlineFactor) {
    drawOnline(c, receiver, onlineFactor, Theme.fillingColor(), Theme.getColor(R.id.theme_color_online));
  }

  public static void drawOnline (Canvas c, Receiver receiver, float onlineFactor, int contentCutOutColor, int onlineColor) {
    if (onlineFactor > 0f) {
      float innerRadius = Screen.dp(4.5f);
      float outerRadius = innerRadius + Screen.dp(2f);
      double radians = Math.toRadians(45f);
      float x, y;
      if (receiver instanceof AvatarReceiver) {
        float displayRadius = ((AvatarReceiver) receiver).getDisplayRadius();
        float centerX = receiver.getRight() - displayRadius;
        float centerY = receiver.getBottom() - displayRadius;
        x = centerX + (float) ((double) displayRadius * Math.sin(radians));
        y = centerY + (float) ((double) displayRadius * Math.cos(radians));
      } else {
        x = receiver.centerX() + (float) ((double) (receiver.getWidth() / 2) * Math.sin(radians));
        y = receiver.centerY() + (float) ((double) (receiver.getHeight() / 2) * Math.cos(radians));
      }
      c.drawCircle(x, y, outerRadius * onlineFactor, Paints.fillingPaint(contentCutOutColor));
      c.drawCircle(x, y, innerRadius * onlineFactor, Paints.fillingPaint(onlineColor));
    }
  }

  public static void drawOnline (Canvas c, float cx, float cy, float radius, float onlineFactor) {
    if (onlineFactor > 0f) {
      float innerRadius = Screen.dp(4.5f);
      float outerRadius = innerRadius + Screen.dp(2f);
      double radians = Math.toRadians(45f);
      float x = cx + (float) ((double) (radius) * Math.sin(radians));
      float y = cy + (float) ((double) (radius) * Math.cos(radians));
      c.drawCircle(x, y, outerRadius * onlineFactor, Paints.fillingPaint(Theme.fillingColor()));
      c.drawCircle(x, y, innerRadius * onlineFactor, Paints.fillingPaint(Theme.getColor(R.id.theme_color_online)));
    }
  }

  public static void drawCloud (Canvas c, int centerX, int centerY, int originRadius, float factor, @ColorInt int color, int paddingRadius, @ColorInt int paddingColor, boolean needThirdPadding) {
    final boolean saved = centerX != 0 || centerY != 0;
    if (saved) {
      c.save();
      c.translate(centerX, centerY);
    }

    if (factor < 1f) {
      int baseRadius = Screen.dp(15f);
      int radiusStartLeft = Screen.dp(12);
      int radiusStartRight = Screen.dp(10f);
      int xLeft = -Screen.dp(12f);
      int yLeft = Screen.dp(6f);
      int xRight = Screen.dp(14f);
      int yRight = Screen.dp(8f);

      if (originRadius != baseRadius) {
        float scale = (float) originRadius / (float) baseRadius;
        baseRadius = originRadius;
        radiusStartLeft *= scale;
        radiusStartRight *= scale;
        xLeft *= scale;
        yLeft *= scale;
        xRight *= scale;
        yRight *= scale;
      }

      float radiusLeft = radiusStartLeft + (baseRadius - radiusStartLeft) * factor;
      float radiusRight = radiusStartRight + (baseRadius - radiusStartRight) * factor;

      xLeft *= (1f - factor);
      yLeft *= (1f - factor);
      xRight *= (1f - factor);
      yRight *= (1f - factor);

      if (paddingRadius != 0) {
        Paint paddingPaint = Paints.fillingPaint(paddingColor);
        c.drawCircle(0, 0, originRadius + paddingRadius, paddingPaint);
        c.drawCircle(xLeft, yLeft, radiusLeft + paddingRadius, paddingPaint);
        if (needThirdPadding) {
          c.drawCircle(xRight, yRight, radiusRight + paddingRadius, paddingPaint);
          c.drawRect(xLeft, yRight, xRight, yRight + radiusRight + paddingRadius, paddingPaint);
        }
      }

      Paint paint = Paints.fillingPaint(color);
      c.drawCircle(0, 0, originRadius, paint);
      c.drawCircle(xLeft, yLeft, radiusLeft, paint);
      c.drawCircle(xRight, yRight, radiusRight, paint);
      c.drawRect(xLeft, Math.max(yLeft, yRight), xRight, Math.max(yRight + radiusRight, yLeft + radiusLeft), paint);
    } else {
      if (paddingRadius != 0) {
        c.drawCircle(0, 0, originRadius + paddingRadius, Paints.fillingPaint(paddingColor));
      }
      c.drawCircle(0, 0, originRadius, Paints.fillingPaint(color));
    }

    if (saved) {
      c.restore();
    }

  }

  public static void drawSimplestCheckBox (Canvas c, Receiver receiver, float checkFactor) {
    drawSimplestCheckBox(c, receiver, checkFactor, Theme.fillingColor());
  }

  public static void drawSimplestCheckBox (Canvas c, Receiver receiver, float checkFactor, int contentReplaceColor) {
    if (checkFactor > 0f) {
      boolean rtl = Lang.rtl();
      final double radians = Math.toRadians(rtl ? 315f : 45f);
      float x, y;
      if (receiver instanceof AvatarReceiver) {
        float displayRadius = ((AvatarReceiver) receiver).getDisplayRadius();
        float centerX = receiver.getRight() - displayRadius;
        float centerY = receiver.getBottom() - displayRadius;
        x = centerX + (float) ((double) displayRadius * Math.sin(radians));
        y = centerY + (float) ((double) displayRadius * Math.cos(radians));
      } else {
        x = receiver.centerX() + (int) ((float) receiver.getWidth() / 2 * Math.sin(radians));
        y = receiver.centerY() + (int) ((float) receiver.getHeight() / 2 * Math.cos(radians));
      }
      SimplestCheckBox.draw(c, (int) x, (int) y, checkFactor, null);
      RectF rectF = Paints.getRectF();
      int radius = Screen.dp(11f);
      rectF.set(x - radius, y - radius, x + radius, y + radius);
      c.drawArc(rectF, rtl ? 225f + 170f * (1f - checkFactor) : 135f, 170f * checkFactor, false, Paints.getOuterCheckPaint(contentReplaceColor));
    }
  }

  public static float getCounterWidth (float textSize, boolean needBackground, CounterAnimator<?> counter, int drawableWidth) {
    return getCounterWidth(textSize, needBackground, counter.getWidth(), drawableWidth);
  }

  public static float getCounterWidth (float textSize, boolean needBackground, float counterWidth, int drawableWidth) {
    float contentWidth = counterWidth + drawableWidth;
    if (needBackground) {
      return Math.max(Screen.dp(textSize - 2f) * 2, contentWidth + Screen.dp(3f) * 2);
    } else {
      return contentWidth;
    }
  }

  public static void drawCounter (Canvas c, float cx, float cy, int gravity, CounterAnimator<Text> counter, float textSize, boolean needBackground, TextColorSet colorSet, Drawable drawable, int drawableGravity, int drawableColorId, int drawableMargin, float alpha, float drawableAlpha, float scale) {
    scale = .6f + .4f * scale;
    final boolean needScale = scale != 1f;

    final float radius, addRadius;
    if (needBackground) {
      radius = Screen.dp(textSize - 2f);
      addRadius = Screen.dp(1.5f);
    } else {
      radius = addRadius = 0f;
    }
    final float contentWidth = counter.getWidth() + (drawable != null ? drawable.getMinimumWidth() + drawableMargin : 0);
    final float width = getCounterWidth(textSize, needBackground, counter, drawable != null ? drawable.getMinimumWidth() + drawableMargin : 0);

    final int backgroundColor = colorSet.backgroundColor(false);
    final int outlineColor = colorSet.outlineColor(false);

    RectF rectF = Paints.getRectF();
    switch (gravity) {
      case Gravity.LEFT:
        rectF.set(cx - radius, cy - radius, cx - radius + width, cy + radius);
        break;
      case Gravity.RIGHT:
        rectF.set(cx - width + radius, cy - radius, cx + radius, cy + radius);
        break;
      case Gravity.CENTER:
      case Gravity.CENTER_HORIZONTAL:
      default:
        rectF.set(cx - width / 2f, cy - radius, cx + width / 2f, cy + radius);
        break;
    }

    if (needScale) {
      c.save();
      c.scale(scale, scale, rectF.centerX(), rectF.centerY());
    }

    if (needBackground) {
      boolean needOutline = Color.alpha(outlineColor) > 0 && addRadius > 0;
      if (rectF.width() == rectF.height()) {
        if (needOutline) {
          c.drawCircle(cx, cy, radius + addRadius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, outlineColor)));
        }
        c.drawCircle(cx, cy, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, backgroundColor)));
      } else {
        if (needOutline) {
          rectF.left -= addRadius;
          rectF.top -= addRadius;
          rectF.right += addRadius;
          rectF.bottom += addRadius;
          c.drawRoundRect(rectF, radius + addRadius, radius + addRadius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, outlineColor)));
          rectF.left += addRadius;
          rectF.top += addRadius;
          rectF.right -= addRadius;
          rectF.bottom -= addRadius;
        }
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, backgroundColor)));
      }
    }

    float startX = rectF.centerX() - contentWidth / 2f;
    if (drawable != null) {
      Paint paint = drawableColorId != 0 ?
        PorterDuffPaint.get(drawableColorId, drawableAlpha):
        Paints.getPorterDuffPaint(ColorUtils.alphaColor(drawableAlpha, colorSet.iconColor()));
      float iconY = cy - drawable.getMinimumHeight() / 2f;
      switch (drawableGravity) {
        case Gravity.RIGHT:
          Drawables.draw(c, drawable, startX + counter.getWidth() + drawableMargin,  iconY, paint);
          break;
        case Gravity.CENTER_HORIZONTAL:
        case Gravity.CENTER:
        case Gravity.LEFT:
        default:
          Drawables.draw(c, drawable, startX, iconY, paint);
          startX += drawable.getMinimumWidth() + drawableMargin;
          break;
      }
    }

    for (ListAnimator.Entry<CounterAnimator.Part<Text>> entry : counter) {
      int textStartX = Math.round(startX + entry.getRectF().left);
      int textEndX = textStartX + entry.item.getWidth();
      int startY = Math.round(cy - entry.item.getHeight() / 2f + entry.item.getHeight() * .8f * entry.item.getVerticalPosition());
      entry.item.text.draw(c, textStartX, textEndX, 0, startY, colorSet, alpha * entry.getVisibility() * (1f - Math.abs(entry.item.getVerticalPosition())));
    }

    if (needScale) {
      c.restore();
    }
  }

  public static void drawBitmapCentered (int viewWidth, int viewHeight, Canvas c, Bitmap bitmap, boolean crop, Paint paint) {
    if (bitmap == null || bitmap.isRecycled()) {
      return;
    }

    final int bitmapWidth = bitmap.getWidth();
    final int bitmapHeight = bitmap.getHeight();

    if (bitmapWidth == viewWidth && bitmapHeight == viewHeight) {
      c.drawBitmap(bitmap, 0, 0, paint);
      return;
    }

    int cx = viewWidth / 2;
    int cy = viewHeight / 2;

    float scale;
    if (crop) {
      scale = Math.max((float) viewWidth / (float) bitmapWidth, (float) viewHeight / (float) bitmapHeight);
    } else {
      scale = Math.min((float) viewWidth / (float) bitmapWidth, (float) viewHeight / (float) bitmapHeight);
    }

    c.save();
    c.scale(scale, scale, cx, cy);

    c.drawBitmap(bitmap, cx - bitmapWidth / 2, cy - bitmapHeight / 2, Paints.getBitmapPaint());
    c.restore();
  }

  public static void drawScaledBitmap (View view, Canvas c, Bitmap bitmap) {
    drawScaledBitmap(view, c, bitmap, 0);
  }

  public static void drawScaledBitmap (View view, Canvas c, Bitmap bitmap, int rotation) {
    final int viewWidth = view.getMeasuredWidth();
    final int viewHeight = view.getMeasuredHeight();

    drawScaledBitmap(viewWidth, viewHeight, c, bitmap, rotation, null);
  }

  public static void drawScaledBitmap (final int viewWidth, final int viewHeight, Canvas c, Bitmap bitmap, int rotation, @Nullable PaintState paintState) {
    if (bitmap != null && !bitmap.isRecycled()) {
      int bitmapWidth, bitmapHeight;

      bitmapWidth = bitmap.getWidth();
      bitmapHeight = bitmap.getHeight();

      if (U.isRotated(rotation)) {
        float scaleX = (float) viewHeight / (float) bitmapWidth;
        float scaleY = (float) viewWidth / (float) bitmapHeight;
        c.save();
        c.scale(scaleX, scaleY, viewWidth / 2, viewHeight / 2);
        c.rotate(rotation, viewWidth / 2, viewHeight / 2);
        int x = viewWidth / 2 - bitmapWidth / 2;
        int y = viewHeight / 2 - bitmapHeight / 2;
        c.drawBitmap(bitmap, x, y, Paints.getBitmapPaint());
        if (paintState != null) {
          c.clipRect(x, y, x + bitmapWidth, y + bitmapHeight);
          paintState.draw(c, x, y, bitmapWidth, bitmapHeight);
        }
        c.restore();
      } else {
        final boolean saved = rotation != 0 || (paintState != null && !paintState.isEmpty());
        if (saved) {
          c.save();
          if (rotation != 0) {
            c.rotate(rotation, viewWidth / 2, viewHeight / 2);
          }
        }
        Rect dst = Paints.getRect();
        dst.set(0, 0, viewWidth, viewHeight);
        c.drawBitmap(bitmap, null, dst, Paints.getBitmapPaint());
        if (paintState != null && !paintState.isEmpty()) {
          c.clipRect(0, 0, viewWidth, viewHeight);
          paintState.draw(c,  0, 0, viewWidth, viewHeight);
        }
        if (saved) {
          c.restore();
        }
      }
    }
  }

  public static void drawPainting (Canvas c, Bitmap bitmap, @Nullable Rect src, Rect dst, @NonNull PaintState state) {
    int srcWidth, srcHeight;
    int x, y;
    if (src != null) {
      srcWidth = src.width();
      srcHeight = src.height();
      x = -src.left;
      y = -src.top;
    } else {
      srcWidth = bitmap.getWidth();
      srcHeight = bitmap.getHeight();
      x = y = 0;
    }
    int dstWidth = dst.width(), dstHeight = dst.height();
    float scaleX = (float) srcWidth / (float) dstWidth, scaleY = (float) srcHeight / (float) dstHeight;
    c.save();
    c.clipRect(dst.left, dst.top, dst.right, dst.bottom);
    if (scaleX != 1f || scaleY != 1f) {
      c.scale(scaleX, scaleY, dst.centerX(), dst.centerY());
    }
    state.draw(c, x, y, bitmap.getWidth(), bitmap.getHeight());
    c.restore();
  }

  public static void drawBackground (TextView view, Canvas c, @ColorInt int color) {
    Layout layout = view.getLayout();
    if (layout == null) {
      return;
    }
    final int lineCount = layout.getLineCount();
    if (lineCount > 0) {
      RectF rectF = Paints.getRectF();
      int padding = Screen.dp(6f);
      int offset = Screen.dp(4f);
      final int radius = Screen.dp(12f);
      rectF.set(0, 0, 0, 0);
      for (int i = 0; i < lineCount; i++) {
        float lineLeft = layout.getLineLeft(i);
        float lineRight = layout.getLineRight(i);

        if (rectF.left == 0 || rectF.left > lineLeft) {
          rectF.left = lineLeft;
        }
        if (rectF.right == 0 || rectF.right < lineRight) {
          rectF.right = lineRight;
        }
      }

      rectF.left -= padding;
      rectF.right += padding;

      // int centerY = getMeasuredHeight() / 2;

      Rect rect = Paints.getRect();
      view.getLineBounds(0, rect);
      rectF.top = rect.top - offset;

      view.getLineBounds(lineCount - 1, rect);
      rectF.bottom = rect.top - offset + Screen.dp(29f);

      c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
    }
  }

  /*public static final int[] COLOR_PICKER_COLORS = {
    0xffff0000,
    0xffffff00,
    0xff00ff00,
    0xff00ffff,
    0xff0000ff,
    0xffff00ff,
    0xffff0000
  };*/

  public static final int[] COLOR_PICKER_COLORS_NICE = {
    0xffea2a2a,
    0xffeaea2a,
    0xff2aea2a,
    0xff2aeaea,
    0xff2a2aea,
    0xffea2aea,
    0xffea2a2a
  };

  /*    background: linear-gradient(to right,hsl(0,100%,50%),hsl(60,100%,50%),hsl(120,100%,50%),hsl(180,100%,50%),hsl(240,100%,50%),hsl(300,100%,50%),hsl(360,100%,50%)) */
  // rgb(255,0,0), rgb()

  public static void drawColorPicker (Canvas c, float width, float height) {
    /*new LinearGradient(height / 2, 0, width, height / 2, COLOR_PICKER_COLORS, null, Shader.TileMode.MIRROR);
    new SweepGradient()*/
  }

  public static @Nullable RectF pathHelper;

  public static void buildPath (Path path, RectF rect, float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {
    if (topLeftRadius == topRightRadius && topLeftRadius == bottomRightRadius && topLeftRadius == bottomLeftRadius) {
      float centerX = rect.centerX();
      float centerY = rect.centerY();
      float width = rect.width();
      float height = rect.height();
      if (width == height && topLeftRadius == width / 2) {
        path.addCircle(centerX, centerY, topRightRadius, Path.Direction.CW);
      } else {
        path.addRoundRect(rect, topLeftRadius, topLeftRadius, Path.Direction.CW);
      }
      return;
    }

    synchronized (DrawAlgorithms.class) {
      RectF rectF = pathHelper;
      if (rectF == null) {
        rectF = new RectF();
        pathHelper = rectF;
      }

      path.moveTo(rect.left, rect.top - topLeftRadius);
      if (topLeftRadius != 0) {
        rectF.set(rect.left, rect.top, rect.left + topLeftRadius * 2, rect.top + topLeftRadius * 2);
        path.arcTo(rectF, -180, 90);
      }
      path.lineTo(rect.right - topRightRadius, rect.top);
      if (topRightRadius != 0) {
        rectF.set(rect.right - topRightRadius * 2, rect.top, rect.right, rect.top + topRightRadius * 2);
        path.arcTo(rectF, -90, 90);
      }
      path.lineTo(rect.right, rect.bottom - bottomRightRadius);
      if (bottomRightRadius != 0) {
        rectF.set(rect.right - bottomRightRadius * 2, rect.bottom - bottomRightRadius * 2, rect.right, rect.bottom);
        path.arcTo(rectF, 0, 90);
      }
      path.lineTo(rect.left + bottomLeftRadius, rect.bottom);
      if (bottomLeftRadius != 0) {
        rectF.set(rect.left, rect.bottom - bottomLeftRadius * 2, rect.left + bottomLeftRadius * 2, rect.bottom);
        path.arcTo(rectF, 90, 90);
      }
      path.lineTo(rect.left, rect.top - topLeftRadius);
      path.close();

      /*path.moveTo(rect.left + topLeftRadius, rect.top);
      path.lineTo(rect.right - topRightRadius, rect.top);
      // path.quadTo(rect.right, rect.top, rect.right, rect.top + topRightRadius);
      path.lineTo(rect.right, rect.bottom - bottomRightRadius);
      // path.quadTo(rect.right, rect.bottom, rect.right - bottomRightRadius, rect.bottom);
      path.lineTo(rect.left + bottomLeftRadius, rect.bottom);
      // path.quadTo(rect.left, rect.bottom,rect.left, rect.bottom - bottomLeftRadius);
      path.lineTo(rect.left, rect.top + topLeftRadius);
      // path.quadTo(rect.left, rect.top, rect.left + topLeftRadius, rect.top);
      path.close();*/
    }
  }

  public static void drawPlayPause (Canvas c, int cx, int cy, int pauseWidth, Path path, float drawFactor, float factor, float loadProgress, @ColorInt int color) {
    float rotation = buildPlayPause(path, pauseWidth, drawFactor, factor);
    boolean saved = cx != 0 || cy != 0 || rotation != 0f || (loadProgress > 0f && loadProgress < 1f);
    if (saved) {
      c.save();
      if (cx != 0 || cy != 0)
        c.translate(cx, cy);
      if (rotation != 0)
        c.rotate(rotation, 0, 0);
    }
    c.drawPath(path, Paints.getPlayPausePaint(color, Paint.Style.FILL));
    if (saved) {
      c.restore();
    }
    // c.drawCircle(cx, cy, Screen.dp(2f), Paints.fillingPaint(0xffff0000));
  }

  public static float buildPlayPause (Path path, int sourcePauseWidth, float drawFactor, float factor) {
    if (factor < 0f || factor > 1f) {
      throw new IllegalArgumentException();
    }
    if (drawFactor == factor) {
      return 90f * factor;
    }
    path.reset();
    path.setFillType(Path.FillType.EVEN_ODD);

    int pauseWidth = Screen.dp(6f);
    int pauseSpacing = Screen.dp(6f);
    int pauseHeight = Screen.dp(21f);

    int width = Screen.dp(18f);
    int height = Screen.dp(22f);

    float scale = (float) sourcePauseWidth / (float) (pauseWidth * 2 + pauseSpacing);

    pauseWidth *= scale;
    pauseSpacing *= scale;
    pauseHeight *= scale;

    width *= scale;
    height *= scale;

    int baseStartX = -(int) (width * .75f) / 2;
    int baseStartY = -height / 2;

    if (factor == 0f) {
      path.moveTo(baseStartX, baseStartY);
      path.lineTo(baseStartX + width, baseStartY + height / 2);
      path.lineTo(baseStartX, baseStartY + height);
      path.close();
    } else if (factor == 1f && false) {
      int startX = -(pauseWidth * 2 + pauseSpacing) / 2;
      int startY = -pauseHeight / 2;

      path.moveTo(startX, startY);
      path.lineTo(startX + pauseWidth, startY);
      path.lineTo(startX + pauseWidth, startY + pauseHeight);
      path.lineTo(startX, startY + pauseHeight);
      path.lineTo(startX, startY);
      path.close();

      startX += pauseWidth + pauseSpacing;

      path.moveTo(startX, startY);
      path.lineTo(startX + pauseWidth, startY);
      path.lineTo(startX + pauseWidth, startY + pauseHeight);
      path.lineTo(startX, startY + pauseHeight);
      path.lineTo(startX, startY);
      path.close();
    } else {
      int fromTargetWidth = height;
      int toTargetWidth = pauseWidth * 2 + pauseSpacing;
      if (fromTargetWidth != toTargetWidth) {
        baseStartY -= (int) ((toTargetWidth - fromTargetWidth) * factor) / 2;
      }

      int fromTargetHeight = width;
      int toTargetHeight = pauseHeight;
      if (fromTargetHeight != toTargetHeight) {
        baseStartX -= (int) ((toTargetHeight - fromTargetHeight) * factor);
      }

      int fromTopWidth = height / 2;
      int toTopWidth = pauseWidth;
      int spacing = (int) (pauseSpacing * factor);

      int topWidth = fromTopWidth + (int) ((toTopWidth - fromTopWidth) * factor);
      int bottomWidth = (int) (topWidth * factor);

      int commonHeight = width + (int) ((pauseHeight - width) * factor);

      if (spacing > 0) {
        path.moveTo(baseStartX + commonHeight, baseStartY + topWidth);
        path.lineTo(baseStartX, baseStartY + topWidth);
        path.lineTo(baseStartX, baseStartY);
        if (bottomWidth > 0) {
          path.lineTo(baseStartX + commonHeight, baseStartY + topWidth - bottomWidth);
        }
        path.close();

        baseStartY += topWidth + spacing;

        path.moveTo(baseStartX + commonHeight, baseStartY);
        path.lineTo(baseStartX, baseStartY);
        path.lineTo(baseStartX, baseStartY + topWidth);
        if (bottomWidth > 0) {
          path.lineTo(baseStartX + commonHeight, baseStartY + bottomWidth);
        }
        path.close();
      } else {
        path.moveTo(baseStartX, baseStartY);
        path.lineTo(baseStartX, baseStartY + topWidth + topWidth);
        path.lineTo(baseStartX + commonHeight, baseStartY + topWidth + bottomWidth);
        if (bottomWidth > 0) {
          path.lineTo(baseStartX + commonHeight, baseStartY + topWidth - bottomWidth);
        }
        path.close();
      }

      return 90f * factor;
    }

    return 0f;
  }

  private static final long WAVE_DURATION = 2000l;
  private static final float WAVE_DISAPPEAR_THRESHOLD = .25f;

  /**
   * Draws two waves on left and right sides of cx,cy
   * @param c Canvas to draw
   * @param cx Center anchor point on x axis
   * @param cy Center anchor point on y axis
   * @param color Color of the wave
   * @param big
   * @return Delay before next update. -1 if no delay needed yet
   */
  public static long drawWaves (Canvas c, float cx, float cy, int color, boolean big, long scheduleTime) {
    int fromRadius, radiusDiff;
    int strokeSize;
    if (big) {
      fromRadius = Screen.dp(20f);
      radiusDiff = Screen.dp(8.5f);
      strokeSize = Screen.dp(3f);
    } else {
      fromRadius = Screen.dp(8f);
      radiusDiff = Screen.dp(3.5f);
      strokeSize = Screen.dp(1.5f);
    }

    float baseFactor = (float) ((double) (SystemClock.uptimeMillis() % WAVE_DURATION) / (double) WAVE_DURATION);
    RectF rectF = Paints.getRectF();

    for (int i = 0; i < 2; i++) {
      float factor = baseFactor < 0.0f ? 1f + baseFactor : baseFactor > 1.0f ? baseFactor - 1.0f : baseFactor;
      float alpha = factor < WAVE_DISAPPEAR_THRESHOLD ? factor / WAVE_DISAPPEAR_THRESHOLD : 1f - (factor - WAVE_DISAPPEAR_THRESHOLD) / (1f - WAVE_DISAPPEAR_THRESHOLD);
      float radius = fromRadius + (radiusDiff * 2.0f * factor);
      float degrees = 20.0f + (3.0f * 2.0f * factor);
      rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
      Paint paint = Paints.getProgressPaint(ColorUtils.alphaColor(alpha, color), strokeSize);
      c.drawArc(rectF, -degrees, degrees * 2, false, paint);
      c.drawArc(rectF, 180 - degrees, degrees * 2, false, paint);

      baseFactor += .5f;
    }
    long now = SystemClock.uptimeMillis();
    if (scheduleTime != 0 && now < scheduleTime) {
      return -1;
    }
    return Math.max(U.calculateDelayForDistance(radiusDiff * 2 * 3, WAVE_DURATION), ValueAnimator.getFrameDelay());
  }

  private static final long TYPING_DURATION = 600l;
  private static final long TYPING_DURATION_STEP = 150l;
  private static final long TYPING_DURATION_STEP_TIME = 300l;

  private static final long RECORDING_VOICE_DURATION = 680l;
  private static final long RECORDING_VOICE_DURATION_STEP = 120l;
  private static final long RECORDING_VOICE_DURATION_STEP_TIME = 240l;

  public static boolean supportsStatus (@NonNull TdApi.ChatAction action) {
    switch (action.getConstructor()) {
      case TdApi.ChatActionTyping.CONSTRUCTOR:
      case TdApi.ChatActionRecordingVoiceNote.CONSTRUCTOR:
      case TdApi.ChatActionRecordingVideoNote.CONSTRUCTOR:
      case TdApi.ChatActionRecordingVideo.CONSTRUCTOR:
      case TdApi.ChatActionUploadingVideo.CONSTRUCTOR:
      case TdApi.ChatActionUploadingVideoNote.CONSTRUCTOR:
      case TdApi.ChatActionUploadingVoiceNote.CONSTRUCTOR:
      case TdApi.ChatActionUploadingPhoto.CONSTRUCTOR:
      case TdApi.ChatActionUploadingDocument.CONSTRUCTOR:
      case TdApi.ChatActionStartPlayingGame.CONSTRUCTOR:
      case TdApi.ChatActionChoosingLocation.CONSTRUCTOR:
      case TdApi.ChatActionChoosingSticker.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public static int getStatusWidth (TdApi.ChatAction action) {
    if (action == null) {
      Log.w("[TYPING ICON BUG]: action == null");
      return 0;
    }
    switch (action.getConstructor()) {
      case TdApi.ChatActionTyping.CONSTRUCTOR:
      case TdApi.ChatActionRecordingVoiceNote.CONSTRUCTOR:
        return Screen.dp(24f);
      case TdApi.ChatActionRecordingVideoNote.CONSTRUCTOR:
      case TdApi.ChatActionRecordingVideo.CONSTRUCTOR:
      case TdApi.ChatActionStartPlayingGame.CONSTRUCTOR:
      case TdApi.ChatActionChoosingSticker.CONSTRUCTOR:
        return Screen.dp(20f);
      case TdApi.ChatActionUploadingVideo.CONSTRUCTOR:
      case TdApi.ChatActionUploadingVideoNote.CONSTRUCTOR:
      case TdApi.ChatActionUploadingVoiceNote.CONSTRUCTOR:
      case TdApi.ChatActionUploadingPhoto.CONSTRUCTOR:
      case TdApi.ChatActionUploadingDocument.CONSTRUCTOR:
        return Screen.dp(26f);
      case TdApi.ChatActionChoosingLocation.CONSTRUCTOR:
        return Screen.dp(36f);
      case TdApi.ChatActionChoosingContact.CONSTRUCTOR:
        // TODO ?
        break;
      case TdApi.ChatActionCancel.CONSTRUCTOR:
        break;
    }

    return 0;
  }

  public static int drawStatus (Canvas c, TdlibStatusManager.ChatState state, float cx, float cy, int color, DrawableProvider provider, @ThemeColorId int knownThemeId) {
    TdApi.ChatAction action = state.action();
    if (action == null) {
      return 0;
    }
    int direction = Lang.rtl() ? -1 : 1;
    long now = SystemClock.elapsedRealtime();
    switch (action.getConstructor()) {
      case TdApi.ChatActionTyping.CONSTRUCTOR: {
        int minRadius = Screen.dp(1.25f);
        int maxRadius = Screen.dp(2.5f);
        int spacing = Screen.dp(6.5f);
        cx += (minRadius * 2) * direction;
        for (int i = 0; i < 3; i++) {
          long time = (now - TYPING_DURATION_STEP * i) % TYPING_DURATION;
          float factor = time < TYPING_DURATION_STEP_TIME ? (float) time / (float) TYPING_DURATION_STEP_TIME : 1f - (float) (time - TYPING_DURATION_STEP_TIME) / (float) TYPING_DURATION_STEP_TIME;
          c.drawCircle(cx, cy, minRadius + (maxRadius - minRadius) * factor, Paints.fillingPaint(color));
          cx += (spacing) * direction;
        }
        return Screen.dp(24f);
      }
      case TdApi.ChatActionRecordingVoiceNote.CONSTRUCTOR: {
        int minRadius = Screen.dp(2f);
        int maxRadius = Screen.dp(5);
        int spacing = Screen.dp(6f);
        int width = Screen.dp(1.5f);
        int cornerRadius = Screen.dp(3f);

        cx += (minRadius * 2) * direction;

        for (int i = 0; i < 3; i++) {
          long time = (now - RECORDING_VOICE_DURATION_STEP * i) % RECORDING_VOICE_DURATION;
          float factor = MathUtils.clamp(time < RECORDING_VOICE_DURATION_STEP_TIME ? (float) time / (float) RECORDING_VOICE_DURATION_STEP_TIME : 1f - (float) (time - RECORDING_VOICE_DURATION_STEP_TIME) / (float) RECORDING_VOICE_DURATION_STEP_TIME);

          RectF rectF = Paints.getRectF();

          float radius = minRadius + (maxRadius - minRadius) * factor;
          rectF.set(cx - width, cy - radius, cx + width, cy + radius);
          c.drawRoundRect(rectF, cornerRadius, cornerRadius, Paints.fillingPaint(color));
          cx += (spacing) * direction;
        }
        return Screen.dp(24f);
      }
      case TdApi.ChatActionRecordingVideoNote.CONSTRUCTOR:
      case TdApi.ChatActionRecordingVideo.CONSTRUCTOR: {
        int minRadius = Screen.dp(1.5f);
        int maxRadius = Screen.dp(3f);

        long duration = 1300l;

        long time = now % duration;
        float factor = (float) time / (float) duration;

        int radius = Screen.dp(5.5f);
        RectF rectF = Paints.getRectF();

        cx += (radius + Screen.dp(2f)) * direction;

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
        float angle, sweep;
        float done = factor <= .5f ? factor / .5f : 1f - (factor - .5f) / .5f;
        if (factor <= .5f) {
          angle = 270f;
          sweep = 360f * done;
        } else {
          sweep = 360f * done;
          angle = MathUtils.modulo(270 + 360f * (1f - done), 360f);
        }
        sweep = Math.max(1f, sweep);
        c.drawArc(rectF, angle, sweep, false, Paints.getProgressPaint(color, Screen.dp(2f)));
        c.drawCircle(cx, cy, minRadius + (maxRadius - minRadius) * done, Paints.fillingPaint(color));

        return Screen.dp(20f);
      }
      case TdApi.ChatActionUploadingVideo.CONSTRUCTOR:
      case TdApi.ChatActionUploadingVideoNote.CONSTRUCTOR:
      case TdApi.ChatActionUploadingVoiceNote.CONSTRUCTOR:
      case TdApi.ChatActionUploadingPhoto.CONSTRUCTOR:
      case TdApi.ChatActionUploadingDocument.CONSTRUCTOR: {
        int width = Screen.dp(18f);
        int height = Screen.dp(5f);

        cx += (Screen.dp(2f)) * direction;

        if (direction == -1) {
          cx -= width;
        }

        int cornerRadius = Screen.dp(1.5f);

        int progress = state.progress();
        float progressFactor = progress == -1 ? 0f : MathUtils.clamp((float) progress / 100f);
        float progressWidth = height + (width - height * 1.5f) * progressFactor;

        long time = now % 800l;
        float timeFactor = (float) time / 800f;
        float position = width * timeFactor;

        RectF rectF = Paints.getRectF();
        rectF.top = cy - height / 2;
        rectF.bottom = rectF.top + height;
        rectF.left = cx;
        rectF.right = cx + width;
        c.drawRoundRect(rectF, cornerRadius, cornerRadius, Paints.fillingPaint(ColorUtils.alphaColor(.2f, color)));

        if (position < width) {
          rectF.left = cx + position;
          rectF.right = cx + Math.min(width, position + progressWidth);
          c.drawRoundRect(rectF, cornerRadius, cornerRadius, Paints.fillingPaint(color));
        }

        if (position + progressWidth > width) {
          float overflow = position + progressWidth - width;
          rectF.left = cx;
          rectF.right = cx + overflow;
          c.drawRoundRect(rectF, cornerRadius, cornerRadius, Paints.fillingPaint(color));
        }

        return Screen.dp(26f);
      }
      case TdApi.ChatActionChoosingSticker.CONSTRUCTOR: // TODO separate animation
      case TdApi.ChatActionStartPlayingGame.CONSTRUCTOR: {
        cy -= Screen.dp(1f);

        cx += Screen.dp(7.5f) * direction;

        int radius = Screen.dp(5.5f);

        float minSweep = 45f;
        float maxSweep = 90f;

        long faceDuration = 200;
        long faceTotal = 400;
        long faceTime = now % (faceTotal + faceTotal + faceDuration * 2);
        float faceFactor;
        float awaitFactor;
        if (faceTime < faceTotal) {
          faceFactor = 0f;
          awaitFactor = 1f - (float) faceTime / (float) faceTotal;
        } else if (faceTime <= faceTotal + faceDuration) {
          faceFactor = (float) (faceTime - faceTotal) / (float) faceDuration;
          awaitFactor = 0f;
        } else if (faceTime <= faceTotal + faceTotal + faceDuration) {
          faceFactor = 1f;
          awaitFactor = 1f - (float) (faceTotal + faceTotal + faceDuration - faceTime) / (float) faceTotal;
        } else {
          faceFactor = 1f - (float) (faceTime - (faceTotal + faceTotal + faceDuration)) / (float) faceDuration;
          awaitFactor = 0f;
        }

        awaitFactor = awaitFactor <= .5f ? awaitFactor / .5f : 1f - (awaitFactor - .5f) / .5f;
        float allowedSweep = .65f + .45f * awaitFactor;
        float sweepFactor = (faceFactor <= .5f ? 1f - faceFactor / .5f : (faceFactor - .5f) / .5f) * allowedSweep;

        float eyeY = cy - Screen.dp(1f);

        int eyeWidth = Screen.dp(2.5f);
        int eyeHeight = Screen.dp(3.5f) - (int) ((float) Screen.dp(1.5f) * faceFactor);
        RectF rectF = Paints.getRectF();
        rectF.left = cx - Screen.dp(2.25f) - eyeWidth / 2;
        rectF.right = rectF.left + eyeWidth;
        rectF.top = eyeY - eyeHeight / 2;
        rectF.bottom = rectF.top + eyeHeight;
        c.drawRoundRect(rectF, eyeHeight / 2, eyeHeight / 2, Paints.fillingPaint(color));
        rectF.offset(Screen.dp(4.5f), 0);
        c.drawRoundRect(rectF, eyeHeight / 2, eyeHeight / 2, Paints.fillingPaint(color));


        float sweep = minSweep + (maxSweep - minSweep) * sweepFactor; // minSweep + (maxSweep - minSweep) * sweepFactor;
        float angle = 90f;

        rectF.left = cx - radius;
        rectF.right = cx + radius;
        float diff = Screen.dp(2f) * faceFactor;
        rectF.top = cy - radius + faceFactor * (radius + radius + radius + radius) - diff;
        rectF.bottom = cy + radius - diff; // + (faceFactor <= .5f ? 0f : (faceFactor - .5f) / .5f) * (radius + radius);
        if (rectF.top > rectF.bottom) {
          float temp = rectF.top;
          rectF.top = rectF.bottom;
          rectF.bottom = temp;
          angle = 270f;
        }
        c.drawArc(rectF, angle - sweep / 2f, sweep, false, Paints.getProgressPaint(color, Screen.dp(2f)));

        return Screen.dp(20f);
      }
      case TdApi.ChatActionChoosingLocation.CONSTRUCTOR: {
        cx += Screen.dp(16f) * direction;
        DrawAlgorithms.drawWaves(c, cx, cy, color, false, -1);
        Drawable drawable = provider.getSparseDrawable(R.drawable.baseline_gps_fixed_12, knownThemeId);
        if (drawable != null) {
          Paint paint;
          switch (knownThemeId) {
            case R.id.theme_color_textLight:
              paint = Paints.getDecentPorterDuffPaint();
              break;
            case R.id.theme_color_chatListAction:
            case R.id.theme_color_headerText:
            default:
              paint = Paints.getPorterDuffPaint(color);
              break;
          }
          Drawables.draw(c, drawable, cx - drawable.getMinimumWidth() / 2, cy - drawable.getMinimumHeight() / 2, paint);
        }
        return Screen.dp(36f);
      }
      case TdApi.ChatActionChoosingContact.CONSTRUCTOR: {
        // TODO ?
        break;
      }
      case TdApi.ChatActionCancel.CONSTRUCTOR:
      case TdApi.ChatActionWatchingAnimations.CONSTRUCTOR:
        break;
    }

    return 0;
  }

  public static class GradientCache {
    private static final float[] MCG_CENTERS_X = new float[] { 0.9f, 0.3f, 0.7f, 0.2f };
    private static final float[] MCG_CENTERS_Y = new float[] { 0.1f, 0.3f, 0.7f, 0.9f };

    private final Paint paint;

    public GradientCache () {
      this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      this.paint.setStyle(Paint.Style.FILL);
    }

    private int lastStartX = -1, lastEndX = -1, lastStartY = -1, lastEndY = -1, topColor, bottomColor;

    private int[] freeformColors;
    private RadialGradient[] freeformGradients;
    private float freeformAlpha;

    public boolean set (int fromX, int fromY, int toX, int toY, int topColor, int bottomColor) {
      if (this.lastStartX != fromX || this.lastStartY != fromY || this.lastEndX != toX || this.lastEndY != toY || this.topColor != topColor || this.bottomColor != bottomColor) {
        this.freeformColors = null;
        this.freeformGradients = null;
        this.lastStartX = fromX;
        this.lastStartY = fromY;
        this.lastEndX = toX;
        this.lastEndY = toY;
        this.topColor = topColor;
        this.bottomColor = bottomColor;
        paint.setDither(false);
        paint.setShader(new LinearGradient(fromX, fromY, toX, toY, topColor, bottomColor, Shader.TileMode.CLAMP));
        return true;
      }
      return false;
    }

    public boolean set (int width, int height, int[] freeformColors, float alpha) {
      if (!Arrays.equals(freeformColors, this.freeformColors) || this.freeformAlpha != alpha) {
        this.freeformColors = freeformColors;
        this.freeformAlpha = alpha;

        float radius = (float) Math.min(Screen.currentHeight(), Screen.currentWidth());

        if (freeformColors.length != 4) {
          radius = radius * 2;
        }

        this.freeformGradients = new RadialGradient[freeformColors.length];

        for (int i = 0; i < freeformColors.length; i++) {
          this.freeformGradients[i] = new RadialGradient(
            MCG_CENTERS_X[i] * width,
            MCG_CENTERS_Y[i] * height,
            radius,
            ColorUtils.alphaColor(alpha, ColorUtils.color(255, freeformColors[i])),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
          );
        }

        paint.setDither(true);
        return true;
      }

      return false;
    }

    public void setFreeformIndex (int index) {
      paint.setShader(this.freeformGradients[index]);
    }
  }

  public static void drawGradient (Canvas c, GradientCache cache, final int left, final int top, final int right, final int bottom, final int topColor, final int bottomColor, final int rotationAngle, float alpha) {
    int fromX, fromY, toX, toY;
    if (rotationAngle == 0 /*top to bottom*/ || rotationAngle == 180 /*bottom to top*/) {
      fromX = toX = (left + right) / 2;
      if (rotationAngle == 0) {
        fromY = top;
        toY = bottom;
      } else {
        fromY = bottom;
        toY = top;
      }
    } else if (rotationAngle == 45 /*top-right to bottom-left*/) {
      fromX = right;
      fromY = top;
      toX = left;
      toY = bottom;
    } else if (rotationAngle == 90 /*right to left*/ || rotationAngle == 270 /*left to right*/) {
      fromY = toY = (top + bottom) / 2;
      if (rotationAngle == 90) {
        fromX = right;
        toX = left;
      } else {
        fromX = left;
        toX = right;
      }
    } else if (rotationAngle == 135 /*bottom-right to top-left*/) {
      fromY = bottom;
      toY = top;
      fromX = right;
      toX = left;
    } else if (rotationAngle == 225 /*bottom-left to top-right*/) {
      fromX = left;
      toX = right;
      fromY = bottom;
      toY = top;
    } else if (rotationAngle == 315) {
      fromX = left;
      toX = right;
      fromY = top;
      toY = bottom;
    } else {
      throw new IllegalArgumentException("rotation: " + rotationAngle);
    }
    cache.set(fromX, fromY, toX, toY, ColorUtils.alphaColor(alpha, ColorUtils.color(255, topColor)), ColorUtils.color(255, bottomColor));
    c.drawRect(left, top, right, bottom, cache.paint);
  }

  public static void drawMulticolorGradient (Canvas c, GradientCache cache, final int left, final int top, final int right, final int bottom, final int[] colors, float alpha) {
    cache.set(right, bottom, colors, alpha);
    for (int i = 0; i < colors.length; i++) {
      cache.setFreeformIndex(i);
      c.drawRect(left, top, right, bottom, cache.paint);
    }
  }

  // cx and cy specify location of the bottom corner
 /* public static void drawMissedOutgoing (Canvas c, float cx, float cy, float factor, @ColorInt int color) {
    float lineHeight = Screen.dp(9f);
    float lineHeightCorner = Screen.dp(6f);
    float lineWidth = Screen.dp(1.5f);
    float lineHalf1Width = lineWidth / 2;
    float lineHalf2Width = lineWidth / 2 + lineWidth % 2;


    // 0 .. .33f -> first line
    // .33 .. .66f -> second line
    // .66 .. 1.0f -> corner

    float line1Factor = factor <= .33f ? factor / .33f : 1f;
    float line2factor = factor <= .33f ? 0f : factor <= .66f ? (factor - .33f) / .33f : 1f;
    float cornerFactor = factor <= .66f ? 0f : (factor - .66f) / (1f - .66f);

    c.save();
    c.translate(cx, cy);
    c.rotate(-45f);
    c.drawRect(-lineHalf1Width, -lineHeight, lineHalf2Width, -lineHeight * (1f - line1Factor), Paints.fillingPaint(color));
    c.drawRect(lineHalf2Width, -lineWidth, lineHalf2Width + (lineHeight - lineHalf2Width) * line2factor, 0, Paints.fillingPaint(color));
    c.translate(lineHeight, 0);
    c.rotate(45f);
    c.drawRect(-(lineHeightCorner) * cornerFactor, -lineWidth, lineHalf2Width, 0, Paints.fillingPaint(color));
    c.drawRect(-lineHalf1Width, 0, lineHalf2Width, lineHeightCorner * cornerFactor, Paints.fillingPaint(color));
    c.restore();
  }*/

 public static void drawAnimatedCross (Canvas c, float cx, float cy, float factor, int color, int lineHeight) {
   final int lineRadius = (int) ((float) lineHeight * .5f);

   float f1 = factor < .5f ? AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor / .5f) : 1f;
   float f2 = factor <= .5f ? 0f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((factor - .5f) / .5f);
   Paint paint = Paints.getProgressPaint(color, Screen.dp(2f));
   if (Lang.rtl()) {
     if (f1 > 0f) {
       int d = (int) ((float) lineHeight * f1);
       c.drawLine(cx - lineRadius, cy - lineRadius, cx - lineRadius + d, cy - lineRadius + d, paint);
     }
     if (f2 > 0f) {
       int d = (int) ((float) lineHeight * f2);
       c.drawLine(cx + lineRadius, cy - lineRadius, cx + lineRadius - d, cy - lineRadius + d, paint);
     }
   } else {
     if (f1 > 0f) {
       int d = (int) ((float) lineHeight * f1);
       c.drawLine(cx + lineRadius, cy - lineRadius, cx + lineRadius - d, cy - lineRadius + d, paint);
     }
     if (f2 > 0f) {
       int d = (int) ((float) lineHeight * f2);
       c.drawLine(cx - lineRadius, cy - lineRadius, cx - lineRadius + d, cy - lineRadius + d, paint);
     }
   }
 }
}
