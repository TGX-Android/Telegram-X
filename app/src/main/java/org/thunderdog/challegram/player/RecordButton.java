/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 20/08/2015 at 02:59
 */
package org.thunderdog.challegram.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class RecordButton extends View implements FactorAnimator.Target, ClickHelper.Delegate {

  private static final float MAX_FACTOR = 3f;

  private int radiusAdd;
  private int radius;
  private int center;

  private final ClickHelper helper = new ClickHelper(this);

  public RecordButton (Context context) {
    super(context);

    radiusAdd = Screen.dp(20f);
    radius = Screen.dp(41f);

    center = (int) (radius + radiusAdd * MAX_FACTOR);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(center * 2, center * 2, Gravity.TOP | Gravity.LEFT);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi (Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          if (expand <= 0f) {
            outline.setEmpty();
          } else {
            int radius = (int) (RecordButton.this.radius * expand);
            outline.setRoundRect(center - radius, center - radius, center + radius, center + radius, radius);
          }
        }
      });
      setElevation(Screen.dp(1f));
      setTranslationZ(Screen.dp(2f));
    }

    setLayoutParams(params);
  }

  private View.OnClickListener onClickListener;

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    super.setOnClickListener(l);
    this.onClickListener = l;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return onClickListener != null && (e.getAction() != MotionEvent.ACTION_DOWN || (expand == 1f && U.isInside(e.getX(), e.getY(), getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand))) && helper.onTouchEvent(this, e);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return U.isInside(x, y, getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (U.isInside(x, y, getMeasuredWidth() / 2, getMeasuredHeight() / 2, radius * expand)) {
      onClickListener.onClick(this);
    }
  }

  public float getCenter () {
    return center;
  }

  public int getSize () {
    return center * 2;
  }

  // Animators

  private float expand;

  public void setExpand (float expand) {
    if (this.expand != expand) {
      this.expand = expand;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
      invalidate();
    }
  }

  public float getExpand () {
    return expand;
  }

  private float volume;
  private FactorAnimator animator;

  private boolean scheduledToVolume;
  private float currentToVolume;

  public void setVolume (float toVolume, boolean animated) {
    toVolume = Math.min(MAX_FACTOR, toVolume / 150f);
    if (animated) {
      if (Math.round((float) radiusAdd * currentToVolume) != Math.round((float) radiusAdd * toVolume)) {
        if (animator == null) {
          if (volume == toVolume) {
            return;
          }
          this.animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 190l, volume);
        }
        currentToVolume = toVolume;
        if (toVolume >= currentToVolume || toVolume > 0 || !animator.isAnimating()) {
          scheduledToVolume = false;
          animator.animateTo(toVolume);
        } else {
          scheduledToVolume = true;
        }
      }
    } else {
      scheduledToVolume = false;
      if (animator != null) {
        animator.forceFactor(toVolume);
      }
      setVolume(toVolume);
    }
  }

  private void setVolume (float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setVolume(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (scheduledToVolume) {
      animator.animateTo(currentToVolume);
    }
  }

  @Override
  public void onDraw (Canvas c) {
    int color = Theme.getColor(R.id.theme_color_circleButtonRegular);
    c.drawCircle(center, center, (radius + radiusAdd * volume) * expand, Paints.fillingPaint(ColorUtils.alphaColor(.3f, color)));
    c.drawCircle(center, center, radius * expand, Paints.fillingPaint(color));
  }
}
