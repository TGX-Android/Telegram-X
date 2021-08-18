package org.thunderdog.challegram.component;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.RelativeLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.RemoveHelper;

/**
 * Date: 08/12/2016
 * Author: default
 */

public class RelativeSessionLayout extends RelativeLayout implements RemoveHelper.RemoveDelegate {
  private RemoveHelper helper;

  public RelativeSessionLayout (Context context) {
    super(context);
    helper = new RemoveHelper(this, R.drawable.baseline_remove_circle_24);
  }

  public void draw (Canvas c) {
    helper.save(c);
    super.draw(c);
    helper.restore(c);
    helper.draw(c);
  }

  @Override
  public void setRemoveDx (float dx) {
    helper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    helper.onSwipe();
  }
}
