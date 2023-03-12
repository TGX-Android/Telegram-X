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
 * File created on 11/06/2017
 */
package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.mediaview.paint.PaintState;

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
