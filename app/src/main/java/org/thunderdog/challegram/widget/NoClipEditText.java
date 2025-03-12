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
 * File created on 10/11/2018
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.config.Config;

import java.lang.reflect.Field;

import me.vkryl.core.BitwiseUtils;

public class NoClipEditText extends EmojiEditText {
  private static Field mScrollYField;
  private int ignoreTopCount, ignoreBottomCount, scrollY;

  private static final int FLAG_IGNORE_AWAKEN_SCROLLBAR = 1 << 2;
  private static final int FLAG_IGNORE_SCROLL_CHANGED = 1 << 3;
  private int flags;

  public NoClipEditText(Context context) {
    super(context);
    initDefault();

    if (mScrollYField == null) {
      try {
        //noinspection JavaReflectionMemberAccess
        mScrollYField = View.class.getDeclaredField("mScrollY");
        mScrollYField.setAccessible(true);
      } catch (Throwable ignored) { }
    }
  }

  protected boolean noClippingWorks () {
    return mScrollYField != null || Config.USE_INPUT_VIEW_CLIPPING_FIX;
  }

  @Override
  public int getExtendedPaddingTop() {
    if (ignoreTopCount != 0) {
      ignoreTopCount--;
      return 0;
    }
    return super.getExtendedPaddingTop();
  }

  @Override
  public int getExtendedPaddingBottom() {
    if (ignoreBottomCount != 0) {
      ignoreBottomCount--;
      return scrollY != Integer.MAX_VALUE ? -scrollY : 0;
    }
    return super.getExtendedPaddingBottom();
  }

  @Override
  protected void onScrollChanged (int horiz, int vert, int oldHoriz, int oldVert) {
    if (BitwiseUtils.hasFlag(flags, FLAG_IGNORE_SCROLL_CHANGED)) {
      return;
    }
    super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
  }

  @Override
  protected boolean awakenScrollBars () {
    if (BitwiseUtils.hasFlag(flags, FLAG_IGNORE_AWAKEN_SCROLLBAR)) {
      return true;
    }
    return super.awakenScrollBars();
  }

  @Override
  protected void onDraw(Canvas c) {
    // Log.i("DEBUG", "draw");
    if (mScrollYField == null) {
      if (Config.USE_INPUT_VIEW_CLIPPING_FIX) {
        int topPadding = getExtendedPaddingTop();
        scrollY = getScrollY();
        flags = FLAG_IGNORE_SCROLL_CHANGED | FLAG_IGNORE_AWAKEN_SCROLLBAR;
        setScrollY(0);
        ignoreTopCount = 1;
        ignoreBottomCount = 1;
        c.save();
        c.translate(0, topPadding);
        try {
          super.onDraw(c);
        } catch (Throwable ignored) {  }
        setScrollY(scrollY);
        c.restore();
        flags = 0;
        return;
      }

      super.onDraw(c);
      return;
    }
    int topPadding = getExtendedPaddingTop();
    scrollY = Integer.MAX_VALUE;
    try {
      scrollY = mScrollYField.getInt(this);
      if (scrollY != 0) {
        mScrollYField.set(this, 0);
      }
    } catch (Throwable ignored) { }
    ignoreTopCount = 1;
    ignoreBottomCount = 1;
    c.save();
    c.translate(0, topPadding);
    try {
      super.onDraw(c);
    } catch (Throwable ignored) {  }
    if (scrollY != Integer.MAX_VALUE && scrollY != 0) {
      try {
        mScrollYField.set(this, scrollY);
      } catch (Throwable ignored) {  }
    }
    c.restore();
  }
}
