/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 14/03/2016 at 00:27
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.tool.Screen;

public class InvisibleImageView extends ImageView implements TooltipOverlayView.LocationProvider {
  public InvisibleImageView (Context context) {
    super(context);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return getVisibility() == View.VISIBLE && super.onTouchEvent(e);
  }

  public boolean setVisible (boolean visible) {
    boolean isVisible = getVisibility() == VISIBLE;
    if (isVisible != visible) {
      setVisibility(visible ? VISIBLE : INVISIBLE);
      return true;
    }
    return false;
  }

  public boolean isVisible () {
    return getVisibility() == VISIBLE;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    outRect.top += Screen.dp(8f);
    outRect.bottom -= Screen.dp(8f);
  }
}
