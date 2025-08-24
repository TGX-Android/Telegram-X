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
 * File created on 11/05/2015 at 20:06
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.Unlockable;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.util.ColorChanger;

public class OverlayView extends View {
  public static final int OVERLAY_MODE_DEFAULT = 1;
  public static final int OVERLAY_MODE_DRAWER = 2;
  public static final int OVERLAY_MODE_PROGRESS = 3;

  private Unlockable unlockable;
  private boolean emptyUnlockable;
  private boolean changeBarColor;

  public OverlayView (Context context) {
    super(context);
  }

  public void setEmptyUnlockable () {
    emptyUnlockable = true;
  }

  public void setUnlockable (Unlockable unlockable) {
    this.unlockable = unlockable;
  }

  private int color;
  // private boolean drawFilling;
  private Window window;
  private float brightnessFactor;
  private float barFactor;
  private ColorChanger statusChanger;

  public void setData (int backgroundColor, int mode) {
    barFactor = mode == OVERLAY_MODE_DEFAULT ? (Theme.getPopupOverlayAlpha()) : .6f;
    color = backgroundColor;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      ViewController<?> c = UI.getCurrentStackItem();
      changeBarColor = (c != null && !c.usePopupMode()) && color != 0x00000000 && color != 0x00ffffffff;
      if (changeBarColor) {
        window = UI.getWindow();
        brightnessFactor = 1f;

        int fromColor = mode == OVERLAY_MODE_DRAWER ? c.getStatusBarColor() : window.getStatusBarColor();
        int toColor = ColorUtils.compositeColor(fromColor, ColorUtils.color((int) (barFactor * brightnessFactor * 255f), color));

        if (statusChanger == null) {
          statusChanger = new ColorChanger(fromColor, toColor);
        } else {
          statusChanger.setFromTo(fromColor, toColor);
        }
      }
    }
  }

  public int getCurrentStatusBarColor () {
    return statusChanger == null ? 0 : statusChanger.getColor(getAlpha() / barFactor * brightnessFactor);
  }


  @Override
  public boolean onTouchEvent (MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      if (unlockable != null) {
        unlockable.unlock();
      }
    }
    return unlockable != null || emptyUnlockable;
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (Color.alpha(color) > 0) {
      canvas.drawColor(color);
    }
  }
}
