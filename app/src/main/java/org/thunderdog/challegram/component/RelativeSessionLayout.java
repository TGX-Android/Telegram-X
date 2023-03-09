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
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.component;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.RelativeLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.RemoveHelper;

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
