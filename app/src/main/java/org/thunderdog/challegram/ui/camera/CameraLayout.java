package org.thunderdog.challegram.ui.camera;

import android.content.Context;

import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

/**
 * Date: 9/22/17
 * Author: default
 */

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

  private int originalWidth, originalHeight;

  public int getOriginalWidth () {
    return originalWidth;
  }

  public int getOriginalHeight () {
    return originalHeight;
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
        if (aspectRatio != maxAspectRatio) {
          if (viewWidth > viewHeight) {
            viewWidth = (int) ((float) viewWidth / aspectRatio * maxAspectRatio);
          } else {
            viewHeight = (int) ((float) viewHeight / aspectRatio * maxAspectRatio);
          }
        }
      }
    }

    super.onMeasure(MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.getMode(widthMeasureSpec)), MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.getMode(heightMeasureSpec)));

    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    if (lastWidth != width || lastHeight != height) {
      lastWidth = width;
      lastHeight = height;

      parent.checkDisplayRotation();
    }
  }
}
