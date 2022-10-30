package org.thunderdog.challegram.custom;

import android.content.Context;
import android.graphics.Canvas;

import org.thunderdog.challegram.widget.TextView;

public class CTextView extends TextView {
  public CTextView (Context context) {
    super(context);
  }

  @Override
  public void onDrawForeground (Canvas canvas) {
    super.onDrawForeground(canvas);
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);
  }
}
