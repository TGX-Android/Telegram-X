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
 * File created on 09/04/2019
 */
package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class GearView extends FrameLayoutFix {
  private ImageView gearBig, gearSmallLeft, gearSmallRight;

  public GearView (Context context) {
    super(context);
    int size = Screen.dp(96f) + Screen.dp(56f) + Screen.dp(12f);
    int offset = Screen.dp(56f) / 2;

    setLayoutParams(new ViewGroup.LayoutParams(size, size));

    ImageView imageView;

    imageView = new ImageView(context);
    imageView.setImageResource(R.drawable.baseline_settings_96);
    imageView.setColorFilter(Theme.getColor(R.id.theme_color_iconActive));
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    addView(gearBig = imageView);

    imageView = new ImageView(context);
    imageView.setImageResource(R.drawable.baseline_settings_56);
    imageView.setAlpha(.5f);
    imageView.setColorFilter(Theme.getColor(R.id.theme_color_iconActive));
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER, 0, offset, offset, 0));
    imageView.setRotation(90f);
    addView(gearSmallLeft = imageView);

    imageView = new ImageView(context);
    imageView.setImageResource(R.drawable.baseline_settings_56);
    imageView.setAlpha(.5f);
    imageView.setColorFilter(Theme.getColor(R.id.theme_color_iconActive));
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER, offset, 0,   0, offset));
    addView(gearSmallRight = imageView);
  }

  private boolean isLooping;
  private ValueAnimator animator;

  public void setLooping (boolean isLooping) {
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;

      if (isLooping) {
        animator = AnimatorUtils.simpleValueAnimator();
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setDuration(4000l);
        animator.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
          float rotation = AnimatorUtils.getFraction(animation);
          gearBig.setRotation(rotation * 360f);
          gearSmallLeft.setRotation(-rotation * 360f + 90f);
          gearSmallRight.setRotation(-rotation * 360f);
        });
        animator.start();
      } else {
        animator.cancel();
        animator = null;
      }
    }
  }
}
