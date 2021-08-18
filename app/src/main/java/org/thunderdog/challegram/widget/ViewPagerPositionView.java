package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * Date: 1/11/18
 * Author: default
 */

public class ViewPagerPositionView extends View {
  public ViewPagerPositionView (Context context) {
    super(context);
  }

  private int itemCount;

  public void reset (int count, float factor) {
    if (this.itemCount != count || this.positionFactor != factor) {
      this.itemCount = count;
      this.positionFactor = factor;
      invalidate();
    }
  }

  private float positionFactor;

  public void setPositionFactor (float factor) {
    if (this.positionFactor != factor) {
      this.positionFactor = factor;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();

    final int spacing = Screen.dp(12f);

    final int cy = viewHeight / 2;
    int cx = viewWidth / 2 - (spacing * (itemCount / 2));

    for (int i = 0; i < itemCount; i++) {
      float factor = 1f - Math.abs(positionFactor - i);
      if (factor > 1f || factor < 0f) {
        factor = 0f;
      }
      c.drawCircle(cx, cy, Screen.dp(2f), Paints.fillingPaint(ColorUtils.color((int) (255f * (.6f + .4f * factor)), 0xffffff)));
      cx += spacing;
    }
  }
}
