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
 * File created on 20/03/2018
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.lambda.Destroyable;

public class LiveLocationView extends View implements Destroyable {
  private Drawable liveLocationBmp;

  public LiveLocationView (Context context) {
    super(context);
    liveLocationBmp = Drawables.get(getResources(), R.drawable.baseline_location_on_48);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(132f)));
  }

  @Override
  public void performDestroy () {
    if (liveLocationBmp != null) {
      liveLocationBmp = null;
    }
  }

  private long nextScheduleTime;

  @Override
  protected void onDraw (Canvas c) {
    if (liveLocationBmp == null) {
      return;
    }
    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;
    Drawables.draw(c, liveLocationBmp, cx - liveLocationBmp.getMinimumWidth() / 2, cy - liveLocationBmp.getMinimumHeight() / 2, Paints.getPorterDuffPaint(0xffffffff));
    long delay = DrawAlgorithms.drawWaves(c, cx, cy - Screen.dp(4f), 0xffffffff, true, nextScheduleTime);
    if (delay != -1) {
      nextScheduleTime = SystemClock.uptimeMillis() + delay;
      postInvalidateDelayed(delay);
    }
  }
}
