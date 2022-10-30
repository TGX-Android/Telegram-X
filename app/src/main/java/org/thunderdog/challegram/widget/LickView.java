package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;

public class LickView extends View {
  public LickView (Context context) {
    super(context);
  }

  private float factor;

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  public float getFactor () {
    return factor;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (factor > 0f) {
      int bottom = getMeasuredHeight();
      int top = bottom - (int) ((float) bottom * factor);
      c.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.fillingColor()));
    }
  }
}
