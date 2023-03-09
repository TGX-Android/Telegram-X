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
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;

public class CounterBadgeView extends View {
  public CounterBadgeView (Context context) {
    super(context);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    setMinimumHeight(Screen.dp(28f));
  }

  // Count

  private final Counter counter = new Counter.Builder().callback(new Counter.Callback() {
    @Override
    public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
      if (sizeChanged) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          invalidateOutline();
        }
      }
      invalidate();
    }

    @Override
    public boolean needAnimateChanges (Counter counter) {
      return Views.isValid(CounterBadgeView.this);
    }
  }).build();

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    setPivotX(getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()));
    setPivotY(getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom()));
  }

  public void setCounter (int count, boolean isMuted, boolean animated) {
    counter.setCount(count, isMuted, animated);
  }

  // Drawing

  @Override
  public void onDraw (Canvas c) {
    int paddingLeft = getPaddingLeft();
    int paddingRight = getPaddingRight();
    int x = paddingLeft + (getMeasuredWidth() - paddingLeft - paddingRight) / 2;
    int y = getMeasuredHeight() / 2;
    counter.draw(c, x, y, Gravity.CENTER_HORIZONTAL, 1f);
  }
}