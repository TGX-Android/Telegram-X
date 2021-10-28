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

/**
 * Date: 9/18/17
 * Author: default
 */

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
