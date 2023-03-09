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
 * File created on 08/08/2015 at 16:55
 */
package org.thunderdog.challegram.component.passcode;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.widget.FrameLayoutFix;

public class PinInputLayout extends FrameLayoutFix implements View.OnClickListener, View.OnLongClickListener {
  public static final int BUTTON_WIDTH = 106;
  private static final int BUTTON_HEIGHT = 82;

  private Callback callback;
  private boolean hasFeedback;

  public PinInputLayout (Context context) {
    super(context);
  }

  public void initWithFeedback (boolean hasFeedback) {
    this.hasFeedback = hasFeedback;
    fillButtons();
    setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(BUTTON_WIDTH * 3), ViewGroup.LayoutParams.MATCH_PARENT));
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  public void setHasFeedback (boolean hasFeedback) {
    if (this.hasFeedback != hasFeedback) {
      this.hasFeedback = hasFeedback;
      for (int i = 0; i < getChildCount(); i++) {
        View v = getChildAt(i);
        if (v != null && v instanceof PinButtonView) {
          ((PinButtonView) v).setHasFeedback(hasFeedback);
        }
      }
    }
  }

  private int buttonHeight;

  private void fillButtons () {
    PinButtonView view;
    FrameLayoutFix.LayoutParams params;

    int cx = 0;
    int cy = 0;

    int buttonWidth = Screen.dp(BUTTON_WIDTH);
    buttonHeight = getButtonHeight();

    for (int i = 0, number = 1; i < 9; number++) {
      params = FrameLayoutFix.newParams(buttonWidth, buttonHeight);
      params.setMargins(cx, cy, 0, 0);

      view = new PinButtonView(getContext());
      view.setHasFeedback(hasFeedback);
      view.setId(number);
      view.setNumber(number);
      view.setOnClickListener(this);
      view.setLayoutParams(params);

      addView(view);

      i++;

      if (i % 3 == 0) {
        cx = 0;
        cy += buttonHeight;
      } else {
        cx += buttonWidth;
      }
    }

    cx += buttonWidth;

    params = FrameLayoutFix.newParams(buttonWidth, buttonHeight);
    params.setMargins(cx, cy, 0, 0);

    view = new PinButtonView(getContext());
    view.setHasFeedback(hasFeedback);
    view.setId(0);
    view.setNumber(0);
    view.setOnClickListener(this);
    view.setLayoutParams(params);

    addView(view);

    cx += buttonWidth;

    params = FrameLayoutFix.newParams(buttonWidth, buttonHeight);
    params.setMargins(cx, cy, 0, 0);

    view = new PinButtonView(getContext());
    view.setHasFeedback(hasFeedback);
    view.setId(-1);
    view.setNumber(-1);
    view.setOnClickListener(this);
    view.setOnLongClickListener(this);
    view.setLayoutParams(params);

    addView(view);
  }

  public void updateHeights () {
    buttonHeight = getButtonHeight();
    int cy = 0;
    for (int i = 0; i < getChildCount();) {
      View view = getChildAt(i);

      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) view.getLayoutParams();
      params.height = buttonHeight;
      params.topMargin = cy;

      i++;

      if (i % 3 == 0) {
        cy += buttonHeight;
      }
    }
  }

  private int getButtonHeight () {
    int availableHeight = Screen.currentActualHeight() - HeaderView.getSize(false) - (UI.isLandscape() ? 0 : Screen.dp(156f) + Screen.dp(32));
    float height = Screen.dpf(BUTTON_HEIGHT);
    float factor = (float) availableHeight / (height * 4f);
    return (int) (height * Math.min(factor, 1.2f));
  }

  @Override
  public void onClick (View v) {
    if (callback != null) {
      if (v.getId() == -1) {
        callback.onPinRemove();
      } else {
        callback.onPinAppend(v.getId());
      }
      UI.hapticVibrate(v, false);
    }
  }

  /*@Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    ViewGroup.LayoutParams p = getLayoutParams();
    setMeasuredDimension(MeasureSpec.makeMeasureSpec(p.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(p.height, MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      MarginLayoutParams mp = (MarginLayoutParams) v.getLayoutParams();
      v.measure(MeasureSpec.makeMeasureSpec(mp.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mp.height, MeasureSpec.EXACTLY));
      v.layout(mp.leftMargin, mp.topMargin, mp.leftMargin + mp.width, mp.topMargin + mp.height);
    }
  }*/

  @Override
  public boolean onLongClick (View v) {
    return callback != null && callback.onPinRemoveAll();
  }

  public interface Callback {
    void onPinRemove ();
    boolean onPinRemoveAll ();
    void onPinAppend (int number);
  }
}
