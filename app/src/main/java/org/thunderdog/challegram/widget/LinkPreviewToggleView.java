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
 * File created on 29/11/2023
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.appcompat.widget.AppCompatImageView;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.RunnableData;

public class LinkPreviewToggleView extends AppCompatImageView implements TooltipOverlayView.LocationProvider {
  private final BoolAnimator showAboveText = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final BoolAnimator hasMedia = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final FactorAnimator mediaStateFactor = new FactorAnimator(0, (id, factor, fraction, callee) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  private int rectWidth, horizontalInset, verticalInset, verticalLineSpacing, horizontalLineSpacing, lineSize;

  private final Drawable topDrawable;
  private boolean needFallback;

  public LinkPreviewToggleView (Context context) {
    super(context);
    setImageResource(R.drawable.baseline_link_preview_bg_24);
    setScaleType(ScaleType.CENTER);

    setColorFilter(Theme.iconColor());

    topDrawable = Drawables.get(R.drawable.baseline_link_preview_top_layer_24);

    updateDimensions();
  }

  private void updateDimensions () {
    long now = SystemClock.uptimeMillis();

    Drawable topDrawable = Drawables.get(R.drawable.baseline_link_preview_top_layer_24);
    toBitmap(topDrawable, bitmap -> {
      int centerX = bitmap.getWidth() / 2;

      int startTopY = -1, endTopY = -1, secondaryTopY = -1;
      int prevColor = 0;
      for (int y = 0; y < bitmap.getHeight(); y++) {
        int color = bitmap.getPixel(centerX, y);

        boolean hadColor = Color.alpha(prevColor) != 0;
        boolean hasColor = Color.alpha(color) != 0;
        if (hadColor != hasColor) {
          if (hasColor) {
            if (startTopY == -1) {
              startTopY = y;
            } else if (secondaryTopY == -1) {
              secondaryTopY = y;
              break;
            }
          } else {
            if (endTopY == -1) {
              endTopY = y;
            }
          }
        }

        prevColor = color;
      }

      this.verticalLineSpacing = secondaryTopY - endTopY;
      this.lineSize = endTopY - startTopY - 1;
      this.verticalInset = startTopY;

      prevColor = 0;

      int startRightX = -1;
      int endRightX = -1, secondaryRightX = -1;

      for (int x = bitmap.getWidth() - 1; x >= 0; x--) {
        int color = bitmap.getPixel(x, verticalInset);

        boolean hadColor = Color.alpha(prevColor) != 0;
        boolean hasColor = Color.alpha(color) != 0;

        if (hadColor != hasColor) {
          if (hasColor) {
            if (startRightX == -1) {
              startRightX = x;
            } else if (secondaryRightX == -1) {
              secondaryRightX = x;
              break;
            }
          } else {
            if (endRightX == -1) {
              endRightX = x;
            }
          }
        }

        prevColor = color;
      }

      needFallback = false;
      if (startTopY == -1 || endTopY == -1 || secondaryTopY == -1) {
        if (BuildConfig.DEBUG) {
          throw new IllegalStateException();
        }
        needFallback = true;
      }
      if (startRightX == -1 || endRightX == -1 || secondaryRightX == -1) {
        if (BuildConfig.DEBUG) {
          throw new IllegalStateException();
        }
        needFallback = true;
      }

      this.horizontalInset = bitmap.getWidth() - startRightX - 1;
      this.horizontalLineSpacing = endRightX - secondaryRightX;
      this.rectWidth = startRightX - endRightX - 1;

      if (BuildConfig.DEBUG) {
        Log.v("Measured icon in %dms", SystemClock.uptimeMillis() - now);
      }
    });
  }

  public void addThemeListeners (ViewController<?> themeProvider) {
    themeProvider.addThemeFilterListener(this, ColorId.icon);
    themeProvider.addThemeInvalidateListener(this);
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    MediaVisibility.NONE,
    MediaVisibility.SMALL,
    MediaVisibility.LARGE
  })
  public @interface MediaVisibility {
    int NONE = 0, SMALL = 1, LARGE = 2;
  }

  public void setShowAboveText (boolean showAboveText, boolean animated) {
    this.showAboveText.setValue(showAboveText, animated && Views.isValid(this));
  }

  private @MediaVisibility int mediaVisibility = MediaVisibility.NONE;

  public void setMediaVisibility (@MediaVisibility int newState, boolean animated) {
    if (this.mediaVisibility != newState || !animated) {
      this.mediaVisibility = newState;
      animated = animated && Views.isValid(this);
      this.hasMedia.setValue(newState != MediaVisibility.NONE, animated);
      if (animated) {
        mediaStateFactor.animateTo(newState);
      } else {
        mediaStateFactor.forceFactor(newState);
      }
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    Drawable drawable = getDrawable();
    if (drawable != null) {
      int centerX = getMeasuredWidth() / 2;
      int centerY = getMeasuredHeight() / 2;
      outRect.set(
        centerX - drawable.getMinimumWidth() / 2,
        centerY - drawable.getMinimumHeight() / 2,
        centerX + drawable.getMinimumWidth() / 2,
        centerY + drawable.getMinimumHeight() / 2
      );
    } else {
      outRect.setEmpty();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    super.onDraw(c);

    int width = topDrawable.getMinimumWidth();
    int height = topDrawable.getMinimumHeight();

    float centerX = getMeasuredWidth() / 2f;
    float centerY = getMeasuredHeight() / 2f;

    float minY = centerY - height / 2f + verticalInset + lineSize;
    float maxY = centerY + height / 2f - verticalInset - lineSize;

    centerY += MathUtils.clamp(1f - showAboveText.getFloatValue()) * ((int) (height / 2) + (int) (height % 2) + Screen.dp(.5f));

    final float left = centerX - width / 2f;
    final float top = centerY - height / 2f;

    final float right = left + width;
    final float bottom = top + height / 2f;

    if (needFallback) {
      Drawables.draw(c, topDrawable, left, top, PorterDuffPaint.get(ColorId.icon));
      return;
    }

    /*if (BuildConfig.DEBUG) {
      Drawables.draw(c, topDrawable, left, top, Paints.getPorterDuffPaint(0xaaff0000));
    }*/

    float factor = mediaStateFactor.getFactor();

    float cLeft = left, cTop = top, cRight = right, cBottom = bottom;
    cLeft += horizontalInset;
    cRight -= horizontalInset;
    cTop += verticalInset;
    cBottom -= verticalInset;

    int contentColor = Theme.textAccentColor();

    if (factor > 0f) {
      float rectWidth = factor <= 1f ? this.rectWidth * factor : MathUtils.fromTo(this.rectWidth, cRight - (cLeft + lineSize + horizontalLineSpacing), factor - 1f);
      c.drawRect(
        cRight - rectWidth,
        cTop,
        cRight,
        cTop + lineSize * 2 + verticalLineSpacing,
        Paints.fillingPaint(contentColor)
      );
    }

    float lineStartX = cLeft + lineSize + horizontalLineSpacing;
    float topLineEndX;
    if (factor <= 1f) {
      topLineEndX = MathUtils.fromTo(cRight, cRight - rectWidth - horizontalLineSpacing, factor);
    } else {
      topLineEndX = MathUtils.fromTo(cRight - rectWidth - horizontalLineSpacing, lineStartX, factor - 1f);
    }
    float bottomLineEndX = MathUtils.fromTo(cRight - rectWidth - horizontalLineSpacing, cRight, hasMedia.getFloatValue());

    c.drawRect(
      lineStartX,
      cTop,
      topLineEndX,
      cTop + lineSize,
      Paints.fillingPaint(contentColor)
    );
    c.drawRect(
      lineStartX,
      cTop + lineSize + verticalLineSpacing,
      topLineEndX,
      cTop + lineSize * 2 + verticalLineSpacing,
      Paints.fillingPaint(contentColor)
    );
    c.drawRect(
      lineStartX,
      cTop + lineSize * 2 + verticalLineSpacing * 2,
      bottomLineEndX,
      cTop + lineSize * 3 + verticalLineSpacing * 2,
      Paints.fillingPaint(contentColor)
    );

    rebuildPath(cLeft,
      cTop,
      cLeft + lineSize,
      cTop + lineSize * 3 + verticalLineSpacing * 2,
      MathUtils.clamp(showAboveText.getFloatValue()),
      MathUtils.clamp(1f - showAboveText.getFloatValue())
    );
    c.drawPath(leftLinePath, Paints.fillingPaint(contentColor));

    /*if (BuildConfig.DEBUG) {
      Drawables.drawCentered(c, Drawables.get(R.drawable.baseline_link_preview_bg_24), getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, Paints.getPorterDuffPaint(0xaaff0000));
    }*/
  }

  private final RectF leftLineRect = new RectF();
  private float leftLineTopRadius, leftLineBottomRadius;

  private void rebuildPath (float left, float top, float right, float bottom, float topRadiusFactor, float bottomRadiusFactor) {
    if (U.setRect(leftLineRect, left, top, right, bottom) || leftLineTopRadius != topRadiusFactor || leftLineBottomRadius != bottomRadiusFactor) {
      leftLinePath.reset();
      leftLineTopRadius = topRadiusFactor;
      leftLineBottomRadius = bottomRadiusFactor;
      float radius = (right - left);
      DrawAlgorithms.buildPath(leftLinePath, leftLineRect, radius * topRadiusFactor, 0, 0, radius * bottomRadiusFactor);
    }
  }

  private final Path leftLinePath = new Path();

  private static void toBitmap (Drawable drawable, RunnableData<Bitmap> callback) {
    if (drawable instanceof BitmapDrawable) {
      BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
      if (bitmapDrawable.getBitmap() != null) {
        callback.runWithData(bitmapDrawable.getBitmap());
        return;
      }
    }

    Bitmap bitmap;
    if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
      bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
    } else {
      bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
    }

    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    drawable.draw(canvas);
    callback.runWithData(bitmap);
    bitmap.recycle();
  }
}
