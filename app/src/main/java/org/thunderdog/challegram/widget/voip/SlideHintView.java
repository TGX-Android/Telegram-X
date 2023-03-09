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
 * File created on 06/08/2017
 */
package org.thunderdog.challegram.widget.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.FloatRange;

import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class SlideHintView extends View implements Runnable {
  private boolean isLooping;

  public SlideHintView (Context context) {
    super(context);
  }

  @Override
  public void setAlpha (@FloatRange(from = 0.0, to = 1.0) float alpha) {
    super.setAlpha(alpha);
    checkLooping();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    checkLooping();
  }

  @Override
  public void setVisibility (int visibility) {
    super.setVisibility(visibility);
    checkLooping();
  }

  private void checkLooping () {
    setIsLooping(getVisibility() == View.VISIBLE && getAlpha() > 0f && getMeasuredWidth() != 0 && getMeasuredHeight() != 0);
  }

  private void setIsLooping (boolean isLooping) {
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;
      if (isLooping) {
        postDelayed(this, 18l);
      } else {
        removeCallbacks(this);
      }
    }
  }

  @Override
  public void run () {
    invalidate();
    if (isLooping) {
      postDelayed(this, 18l);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    long remain = SystemClock.elapsedRealtime() % 1200;
    float timeFactor = remain <= 300 ? 0f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) (remain - 300) / 900f);

    float minAlpha = .4f;

    // 1 2 3


    // 0 0 0
    // 1 0 0
    // 2 1 0
    // 1 2 1
    // 0 1 2
    // 0 0 1
    // 0 0 0

    int cx = Screen.dp(22f);
    int cy = getMeasuredHeight() / 2;

    float position = -2f + timeFactor * 8f;

    for (int i = 0; i < 3; i++) {
      float factor = 1f - MathUtils.clamp(Math.abs(position - i - 1) / 3f);
      float alpha = minAlpha + (1f - minAlpha) * factor;

      int color = ColorUtils.color((int) (255f * alpha), 0xffffff);
      DrawAlgorithms.drawHorizontalDirection(c, cx, cy, color, true);
      DrawAlgorithms.drawHorizontalDirection(c, getMeasuredWidth() - cx, cy, color, false);
      cx += Screen.dp(16f);
    }
  }
}
