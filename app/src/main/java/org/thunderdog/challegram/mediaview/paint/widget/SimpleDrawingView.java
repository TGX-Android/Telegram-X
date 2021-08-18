package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.mediaview.paint.PaintState;

/**
 * Date: 11/6/17
 * Author: default
 */

public class SimpleDrawingView extends View implements PaintState.SimpleDrawingChangeListener {
  private PaintState state;

  public SimpleDrawingView (Context context) {
    super(context);
  }

  public void setPaintState (PaintState state) {
    if (this.state != null) {
      this.state.removeSimpleDrawingChangeListener(this);
    }
    this.state = state;
    if (state != null) {
      state.addSimpleDrawingChangeListener(this);
    }
    invalidate();
  }

  @Override
  public void onSimpleDrawingsChanged (PaintState state) {
    if (this.state == state) {
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (state != null) {
      state.draw(c, 0, 0, getMeasuredWidth(), getMeasuredHeight());
    }
  }
}
