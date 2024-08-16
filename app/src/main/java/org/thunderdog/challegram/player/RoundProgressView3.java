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
 * File created on 24/06/2024
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class RoundProgressView3 extends View {
  public static final int PADDING = 16;
  private static final int STROKE_WIDTH = 4;
  private static final int POINTER_RADIUS = 6;

  private final Paint strokePaintDefault;
  private final Paint strokePaintFilling;

  public RoundProgressView3(Context context) {
    super(context);
    strokePaintDefault = new Paint(Paints.videoStrokePaint());
    strokePaintFilling = new Paint(Paints.videoStrokePaint());
  }

  private float visualProgress;

  public void setVisualProgress (float progress) {
    if (this.visualProgress != progress) {
      this.visualProgress = progress;
      invalidate();
    }
  }




  private float lastDrawProgress;
  private float lastDrawStartPointerX, lastDrawStartPointerY, lastDrawEndPointerX, lastDrawEndPointerY;

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    final float progress = visualProgress;

    final int defaultColor = 0xFFFFFFFF;
    final int fillingColor = ColorUtils.fromToArgb(defaultColor, Theme.getColor(ColorId.fillingPositive), editFactor);

    strokePaintDefault.setStrokeWidth(Screen.dp(STROKE_WIDTH));
    strokePaintDefault.setColor(defaultColor);

    strokePaintFilling.setStrokeWidth(Screen.dp(STROKE_WIDTH));
    strokePaintFilling.setColor(fillingColor);


    final int padding = Screen.dp(PADDING);
    final float angle = 360f * progress;

    RectF rectF = Paints.getRectF();
    rectF.set(padding, padding, viewWidth - padding, viewHeight - padding);

    c.drawArc(rectF, -90, angle * trimStartFactor, false, strokePaintDefault);
    c.drawArc(rectF, -90 + angle * trimEndFactor, angle * (1f - trimEndFactor), false, strokePaintDefault);
    c.drawArc(rectF, -90 + angle * trimStartFactor, angle * (trimEndFactor - trimStartFactor), false, strokePaintFilling);

    {
      final double angleRad = Math.toRadians(-90 + angle * trimStartFactor);
      float x = lastDrawStartPointerX = (float) Math.cos(angleRad) * rectF.width() / 2f + rectF.centerX();
      float y = lastDrawStartPointerY = (float) Math.sin(angleRad) * rectF.width() / 2f + rectF.centerY();
      c.drawCircle(x, y, Screen.dp(POINTER_RADIUS) * editFactor, Paints.fillingPaint(fillingColor));
    }

    {
      final double angleRad = Math.toRadians(-90 + angle * trimEndFactor);
      float x = lastDrawEndPointerX = (float) Math.cos(angleRad) * rectF.width() / 2f + rectF.centerX();
      float y = lastDrawEndPointerY = (float) Math.sin(angleRad) * rectF.width() / 2f + rectF.centerY();
      c.drawCircle(x, y, Screen.dp(POINTER_RADIUS) * editFactor, Paints.fillingPaint(fillingColor));
    }

    this.lastDrawProgress = progress;
  }



  private float editFactor;

  public void setEditFactor (float editFactor) {
    if (this.editFactor != editFactor){
      this.editFactor = editFactor;
      invalidate();
    }
  }

  private float trimStartFactor = 0f, trimEndFactor = 1f;

  public void setTrimFactors (float start, float end) {
    if (captured != CAPTURED_NONE) {
      return;
    }

    this.trimStartFactor = start;
    this.trimEndFactor = end;
    invalidate();
  }

  public void reset () {
    this.trimStartFactor = 0f;
    this.trimEndFactor = 1f;
    this.visualProgress = 0f;
    this.editFactor = 0f;
  }





  public interface Delegate {
    void onTrimSliderDown (RoundProgressView3 view, float start, float end, boolean isEnd);
    void onTrimSliderMove (RoundProgressView3 view, float start, float end, boolean isEnd);
    void onTrimSliderUp (RoundProgressView3 view, float start, float end, boolean isEnd);
  }


  private static final int CAPTURED_NONE = 0;
  private static final int CAPTURED_START = 1;
  private static final int CAPTURED_END = 2;

  private Delegate delegate;
  private int captured;

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    final int action = event.getAction();
    final float x = event.getX();
    final float y = event.getY();

    if (editFactor == 0) {
      captured = CAPTURED_NONE;
      return false;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (MathUtils.distance(x, y, lastDrawEndPointerX, lastDrawEndPointerY) < Screen.dp(24)) {
          captured = CAPTURED_END;
          if (delegate != null) {
            delegate.onTrimSliderDown(this, trimStartFactor, trimEndFactor, true);
          }
          return true;
        }
        if (MathUtils.distance(x, y, lastDrawStartPointerX, lastDrawStartPointerY) < Screen.dp(24)) {
          captured = CAPTURED_START;
          if (delegate != null) {
            delegate.onTrimSliderDown(this, trimStartFactor, trimEndFactor, false);
          }
          return true;
        }
        break;
      case MotionEvent.ACTION_MOVE:
        final double rad = Math.atan2(y - getMeasuredHeight() / 2f, x - getMeasuredWidth() / 2f);
        float angle = ((float) Math.toDegrees(rad) + 360 + 90) % 360;

        if (captured == CAPTURED_END) {
          final float angleStart = 360 * visualProgress * trimStartFactor;
          final float angleEnd = 360 * visualProgress;

          if (!(angleStart <= angle && angle <= angleEnd)) {
            final float distanceToStart = angleDistance(angleStart, angle);
            final float distanceToEnd = angleDistance(angleEnd, angle);
            angle = (distanceToStart < distanceToEnd) ? angleStart : angleEnd;
          }

          trimEndFactor = angle / (360f * visualProgress);
          if (delegate != null) {
            delegate.onTrimSliderMove(this, trimStartFactor, trimEndFactor, true);
          }
          invalidate();
          return true;
        }

        if (captured == CAPTURED_START) {
          final float angleStart = 0;
          final float angleEnd = 360 * visualProgress * trimEndFactor;

          if (!(angleStart <= angle && angle <= angleEnd)) {
            final float distanceToStart = angleDistance(angleStart, angle);
            final float distanceToEnd = angleDistance(angleEnd, angle);
            angle = (distanceToStart < distanceToEnd) ? angleStart : angleEnd;
          }

          trimStartFactor = angle / (360f * visualProgress);
          if (delegate != null) {
            delegate.onTrimSliderMove(this, trimStartFactor, trimEndFactor, false);
          }
          invalidate();
          return true;
        }

        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (captured != CAPTURED_NONE) {
          /*updateSeekRunnable.cancelIfScheduled();
          if (controller != null) {
            controller.seekTo(MathUtils.clamp(visualProgress), true);
          }
          if (seekCaught != null) {
            seekCaught.requestDisallowInterceptTouchEvent(false);
          }*/

          if (delegate != null) {
            delegate.onTrimSliderUp(this, trimStartFactor, trimEndFactor, captured == CAPTURED_END);
          }
          captured = CAPTURED_NONE;
          return true;
        }
    }

    return false;
  }

  private static float angleDistance (float angle1, float angle2) {
    final float distance = Math.abs(angle1 - angle2);
    return distance > 180 ? 360 - distance : distance;
  }

}
