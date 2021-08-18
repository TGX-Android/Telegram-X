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

/**
 * Date: 3/20/18
 * Author: default
 */

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
