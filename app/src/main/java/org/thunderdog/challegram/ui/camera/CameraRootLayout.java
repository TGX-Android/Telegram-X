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
 * File created on 18/09/2017
 */
package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.widget.FrameLayoutFix;

class CameraRootLayout extends FrameLayoutFix {
  protected ViewController<?> controller;

  public CameraRootLayout (@NonNull Context context) {
    super(context);
  }

  public void setController (ViewController<?> controller) {
    this.controller = controller;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (controller != null) {
      controller.executeScheduledAnimation();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (controller.isFocused()) {
          UI.getContext(getContext()).prepareCameraDragByTouchDown(null, false);
        }
        break;
      }
    }
    return true;
  }

  public void setQrCorner (RectF boundingBox, int height, int width, int rotation, boolean isLegacyZxing) {}
  public void resetQrCorner () {}
  public void setQrMode (boolean enable, boolean qrModeDebug) {}
  public void setQrModeSubtitle (@StringRes int subtitleRes) {}
  public void onCameraClosed () {}
  public void setComponentRotation (float rotation) {}
}
