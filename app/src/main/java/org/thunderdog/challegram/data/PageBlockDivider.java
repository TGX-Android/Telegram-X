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
 * File created on 24/02/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;

public class PageBlockDivider extends PageBlock {
  public PageBlockDivider (ViewController<?> context, @NonNull TdApi.PageBlock block) {
    super(context, block);
  }

  @Override
  public int getRelatedViewType () {
    return ListItem.TYPE_PAGE_BLOCK;
  }

  @Override
  protected int computeHeight (View view, int width) {
    return Screen.dp(20f);
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return false;
  }

  @Override
  protected int getContentTop () {
    return Screen.dp(9f);
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(1f);
  }

  @Override
  public void drawInternal (View view, Canvas c, Receiver preview, Receiver receiver, ComplexReceiver iconReceiver) {
    int top = Screen.dp(9f);
    int viewWidth = (view.getMeasuredWidth() - getTotalContentPadding()) / 2;
    int x = viewWidth - viewWidth / 2 + getMinimumContentPadding(true);
    c.drawRect(x, top, x + viewWidth, top + Screen.dp(1f), Paints.fillingPaint(Theme.getColor(R.id.theme_color_iv_separator)));
  }
}
