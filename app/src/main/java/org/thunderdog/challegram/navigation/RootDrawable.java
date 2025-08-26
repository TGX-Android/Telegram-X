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
 * File created on 24/03/2016 at 12:01
 */
package org.thunderdog.challegram.navigation;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

public class RootDrawable extends Drawable {
  private final RootState state;

  public RootDrawable (BaseActivity context) {
    this.state = new RootState(context);
  }

  @Nullable
  @Override
  public ConstantState getConstantState () {
    return state;
  }

  public RootDrawable setDisabled (boolean isDisabled) {
    state.isDisabled = isDisabled;
    return this;
  }

  @Override
  public void draw (@NonNull final Canvas c) {
    if (!state.isDisabled) {
      c.drawColor(Theme.getColor(state.getColorId()));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      int height = Screen.getNavigationBarHeight();
      if (height > 0 && state.context.hadSoftwareKeysOnActivityLaunch()) {
        int rotation = state.context.getWindowRotationDegrees();
        Rect bounds = getBounds();
        switch (rotation) {
          case 0:
          case 180:
            c.drawRect(bounds.left, bounds.bottom - height, bounds.right, bounds.bottom, Paints.fillingPaint(UI.NAVIGATION_BAR_COLOR));
            break;
          case 90:
            c.drawRect(bounds.right - height, bounds.top, bounds.right, bounds.bottom, Paints.fillingPaint(UI.NAVIGATION_BAR_COLOR));
            break;
          case 270:
            c.drawRect(bounds.left, bounds.top, bounds.left + height, bounds.bottom, Paints.fillingPaint(UI.NAVIGATION_BAR_COLOR));
            break;
        }
      }
    }
  }

  @Override
  public void setAlpha (int alpha) {

  }

  @Override
  public void setColorFilter (ColorFilter colorFilter) {

  }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }

  private static class RootState extends ConstantState {
    private final BaseActivity context;
    private boolean isDisabled;

    public RootState (BaseActivity context) {
      this.context = context;
    }

    private int getColorId () {
      if (context.isPasscodeShowing()) {
        return ColorId.headerBackground;
      }
      final NavigationController navigation = context.navigation();
      int colorId = ColorId.filling;
      final ViewController<?> popup = context.getCurrentlyOpenWindowedViewController();
      if (popup != null) {
        colorId = popup.getRootColorId();
      } else if (navigation != null) {
        final ViewController<?> v = navigation.getCurrentStackItem();
        if (v != null && !v.usePopupMode()) {
          colorId = v.getRootColorId();
        }
      }
      return colorId;
    }

    @NonNull
    @Override
    public Drawable newDrawable () {
      RootDrawable rootDrawable = new RootDrawable(context);
      rootDrawable.state.isDisabled = isDisabled;
      return rootDrawable;
    }

    @Override
    public int getChangingConfigurations () {
      return 0;
    }
  }
}
