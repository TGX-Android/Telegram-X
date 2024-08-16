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
 * File created on 30/06/2024
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class RecordControllerButton extends FrameLayoutFix {
  public static final int BUTTON_SIZE = 40;
  public static final int PADDING = 5;

  private ViewController<?> themeProvider;

  public RecordControllerButton (Context context) {
    super(context);

    Views.setClickable(this);
  }

  public void init (ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    RippleSupport.setCircleBackground(RecordControllerButton.this, BUTTON_SIZE, PADDING, ColorId.filling, true, themeProvider);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int sizeMeasureSpec = MeasureSpec.makeMeasureSpec(Screen.dp(BUTTON_SIZE + PADDING * 2), MeasureSpec.EXACTLY);
    super.onMeasure(sizeMeasureSpec, sizeMeasureSpec);
  }


  @Override
  protected void dispatchDraw (@NonNull Canvas canvas) {
    final float active = isActiveAnimator.getFloatValue();
    if (active > 0f) {
      final int color = ColorUtils.fromToArgb(Theme.getColor(ColorId.filling), Theme.getColor(ColorId.fillingPositive), active);
      canvas.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, Screen.dp(BUTTON_SIZE / 2f), Paints.fillingPaint(color));
    }

    super.dispatchDraw(canvas);
  }





  private final FactorAnimator.Target isActiveTarget = new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      invalidate();
      onActiveFactorChanged(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      onActiveFactorChangeFinished(finalFactor);
    }
  };
  private final BoolAnimator isActiveAnimator = new BoolAnimator(0, isActiveTarget, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);

  public void setActive (boolean active, boolean animated) {
    isActiveAnimator.setValue(active, animated);
  }

  public void toggleActive (boolean animated) {
    isActiveAnimator.setValue(!isActiveAnimator.getValue(), animated);
  }

  public boolean isActive () {
    return isActiveAnimator.getValue();
  }

  public float getActiveFactor () {
    return isActiveAnimator.getFloatValue();
  }

  protected void onActiveFactorChanged (float factor) {

  }

  protected void onActiveFactorChangeFinished (float factor) {

  }
}
