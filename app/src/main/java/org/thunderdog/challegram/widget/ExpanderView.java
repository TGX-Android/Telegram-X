/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 22/02/2018
 */
package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class ExpanderView implements FactorAnimator.Target {
  private final Path path;
  private final BoolAnimator expandAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private Counter counter;

  private final View parentView;

  public ExpanderView (View parentView) {
    this.parentView = parentView;
    this.path = new Path();

    int width = Screen.dp(10f);
    int height = Screen.dp(5f);

    path.setFillType(Path.FillType.EVEN_ODD);
    path.moveTo(-width / 2f, -height / 2f);
    path.rLineTo(width, 0);
    path.rLineTo(-width / 2f, height);
    path.rLineTo(-width / 2f, -height);
    path.close();
  }

  public void setUnreadCount (int unreadCount, boolean isMuted, boolean animated) {
    if (unreadCount <= 0 && counter == null) {
      return;
    }
    if (counter == null) {
      counter = new Counter.Builder().callback(parentView).colorSet(new TextColorSet() {
        @Override
        public int defaultTextColor () {
          return ColorUtils.fromToArgb(0xff000000, 0x7f000000, counter.getMuteFactor());
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          return ColorUtils.fromToArgb(0xffffffff, 0x7fffffff, counter.getMuteFactor());
        }
      }).build();
    }
    counter.setCount(unreadCount, isMuted, animated);
  }

  public boolean toggleExpanded () {
    boolean newValue;
    setExpanded(newValue = !expandAnimator.getValue(), true);
    return newValue;
  }

  public void setExpanded (boolean isExpanded, boolean animated) {
    expandAnimator.setValue(isExpanded, animated);
  }

  private float expandFactor;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.expandFactor != factor) {
      this.expandFactor = factor;
      parentView.invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public final void draw (Canvas c, int cx, int cy, int color) {
    c.save();
    c.translate(cx, cy);
    float degrees = 180 * expandFactor;
    if (degrees != 0) {
      c.rotate(degrees);
    }
    c.drawPath(path, Paints.fillingPaint(color));
    c.restore();

    if (counter != null && expandFactor < 1f) {
      boolean rtl = Lang.rtl();
      cx += Screen.dp(24f) * (rtl ? 1 : -1);
      // cy -= Screen.dp(13f);
      c.save();
      c.scale(.85f, .85f, cx, cy);
      counter.draw(c, cx, cy, rtl ? Gravity.LEFT : Gravity.RIGHT, 1f - expandFactor);
      c.restore();
    }
  }
}
