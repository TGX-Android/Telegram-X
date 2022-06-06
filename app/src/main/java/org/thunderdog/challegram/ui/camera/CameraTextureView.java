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
 * File created on 19/09/2017
 */
package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.view.TextureView;

import org.thunderdog.challegram.Log;

public class CameraTextureView extends TextureView {
  private CameraManagerTexture manager;

  private int ratioWidth, ratioHeight;
  public int scaledImageWidth;

  public CameraTextureView (Context context) {
    super(context);
  }

  public void setManager (CameraManagerTexture manager) {
    this.manager = manager;
  }

  private boolean aspectRatioRequested;

  public void setAspectRatio (int width, int height) {
    if (ratioWidth != width || ratioHeight != height) {
      ratioWidth = width;
      ratioHeight = height;
      if (!aspectRatioRequested) {
        requestLayout();
      }
    }
  }

  private boolean ignoreAspectRatio;

  public void setIgnoreAspectRatio (boolean ignore) {
    if (this.ignoreAspectRatio != ignore) {
      this.ignoreAspectRatio = ignore;
      requestLayout();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int viewWidth = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int viewHeight = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    aspectRatioRequested = true;
    manager.setPreviewSize(viewWidth, viewHeight);
    aspectRatioRequested = false;

    if (!ignoreAspectRatio && ratioWidth > 0 && ratioHeight > 0) {
      int width, height;
      if (viewWidth < viewHeight * ratioWidth / ratioHeight) {
        width = viewWidth;
        height = (int) ((float) viewWidth * ((float) ratioHeight / (float) ratioWidth));
      } else {
        width = (int) ((float) viewHeight * ((float) ratioWidth / (float) ratioHeight));
        height = viewHeight;
      }

      float ratio = Math.max((float) viewWidth / (float) width, (float) viewHeight / (float) height);
      if (ratio > 1f) {
        width *= ratio;
        height *= ratio;
      }

      scaledImageWidth = width;
      setMeasuredDimension(width, height);
    } else {
      setMeasuredDimension(viewWidth, viewHeight);
    }

    manager.prepareBitmaps(getMeasuredWidth(), getMeasuredHeight());

    Log.i(Log.TAG_CAMERA, "CameraTextureView: onMeasure %d %d", getMeasuredWidth(), getMeasuredHeight());
  }
}
