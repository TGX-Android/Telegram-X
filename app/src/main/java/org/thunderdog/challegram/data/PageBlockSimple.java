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
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.util.DrawableProvider;

public class PageBlockSimple extends PageBlock {
  private final int viewType, backgroundColorId;

  public PageBlockSimple (ViewController<?> context, int viewType, int backgroundColorId) {
    super(context, null);
    this.viewType = viewType;
    this.backgroundColorId = backgroundColorId;
  }

  @Override
  public int getRelatedViewType () {
    return viewType;
  }

  @Override
  protected int computeHeight (View view, int width) {
    return SettingHolder.measureHeightForType(getRelatedViewType());
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return false;
  }

  @Override
  protected int getContentTop () {
    return 0;
  }

  @Override
  public int getBackgroundColorId () {
    return backgroundColorId;
  }

  @Override
  protected int getContentHeight () {
    return 0;
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, ComplexReceiver iconReceiver) { }
}
