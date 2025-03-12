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
 */
package org.thunderdog.challegram.ui.camera;

import android.content.Context;

import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class CameraLayout extends FrameLayoutFix {
  private CameraController parent;

  public CameraLayout (Context context) {
    super(context);
  }

  public void setParent (CameraController parent) {
    this.parent = parent;
  }

  private int lastWidth, lastHeight;

  private boolean disallowRatioChanges;

  public void setDisallowRatioChanges (boolean disallowRatioChanges) {
    if (this.disallowRatioChanges != disallowRatioChanges) {
      this.disallowRatioChanges = disallowRatioChanges;
      requestLayout();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int viewWidth = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    int viewHeight = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);

    parent.getManager().setParentSize(viewWidth, viewHeight);

    if (!disallowRatioChanges) {
      final float maxAspectRatio = Settings.instance().getCameraAspectRatio();
      if (maxAspectRatio != 0f) {
        float aspectRatio = MathUtils.aspectRatio(viewWidth, viewHeight);
        if (aspectRatio > maxAspectRatio) {
          if (viewWidth > viewHeight) {
            viewWidth = (int) ((float) viewWidth / aspectRatio * maxAspectRatio);
          } else {
            viewHeight = (int) ((float) viewHeight / aspectRatio * maxAspectRatio);
          }
        }
      }
    }

    super.onMeasure(
      MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.getMode(widthMeasureSpec)),
      MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.getMode(heightMeasureSpec))
    );

    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    if (lastWidth != width || lastHeight != height) {
      lastWidth = width;
      lastHeight = height;

      parent.checkDisplayRotation();
    }
  }
}
