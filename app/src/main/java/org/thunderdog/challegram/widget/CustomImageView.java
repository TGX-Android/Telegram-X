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
 * File created on 22/07/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

public class CustomImageView extends ImageView {
  public CustomImageView (Context context) {
    super(context);
  }

  public CustomImageView (Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public CustomImageView (Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return (e.getAction() != MotionEvent.ACTION_DOWN || (getVisibility() == View.VISIBLE && getAlpha() == 1f)) && super.onTouchEvent(e);
  }

  private boolean preventLayout;

  @Override
  public void setImageBitmap (Bitmap bm) {
    preventLayout = true;
    super.setImageBitmap(bm);
    preventLayout = false;
  }

  @Override
  public void requestLayout () {
    if (!preventLayout) {
      super.requestLayout();
    }
  }
}
