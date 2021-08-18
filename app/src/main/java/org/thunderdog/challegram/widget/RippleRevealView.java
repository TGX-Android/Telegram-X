package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;

import me.vkryl.core.ColorUtils;

/**
 * Date: 10/28/17
 * Author: default
 */

public class RippleRevealView extends View {
  private @ThemeColorId int colorId = R.id.theme_color_filling;
  private float revealFactor;

  public RippleRevealView (Context context) {
    super(context);
  }

  public void setRevealFactor (float factor) {
    if (this.revealFactor != factor) {
      this.revealFactor = factor;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (revealFactor > 0f) {
      final int color = Theme.getColor(colorId);
      c.drawColor(ColorUtils.alphaColor(revealFactor, color));

      float width = getMeasuredWidth();
      float height = getMeasuredHeight();
      float radius = (float) Math.sqrt(width * width + height * height) * .5f;
      c.drawCircle(width / 2, height / 2, radius * revealFactor, Paints.fillingPaint(color));
    }
  }
}
