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
 * File created on 27/02/2016 at 21:59
 */
package org.thunderdog.challegram.component.emoji;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import org.thunderdog.challegram.data.TGGif;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.android.util.ClickHelper;

public class GifView extends BaseView {
  private TGGif gif;
  private GifReceiver receiver;
  private ImageReceiver preview;

  public GifView (Context context) {
    super(context, null);
    receiver = new GifReceiver(this);
    preview = new ImageReceiver(this, 0);
  }

  private ClickHelper helper;

  public void initWithDelegate (ClickHelper.Delegate delegate) {
    helper = new ClickHelper(delegate);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return helper != null ? helper.onTouchEvent(this, event) : super.onTouchEvent(event);
  }

  private int getDesiredHeight () {
    return Screen.dp(118f);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getDesiredHeight(), MeasureSpec.EXACTLY));
    preview.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    receiver.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
  }

  public void setGif (TGGif gif) {
    if (this.gif == null || gif == null || this.gif.getId() != gif.getId()) {
      this.gif = gif;
      preview.requestFile(gif == null ? null : gif.getImage());
      receiver.requestFile(gif == null ? null : gif.getGif());
    }
    if (getDesiredHeight() != getMeasuredHeight()) {
      requestLayout();
    }
  }

  public TGGif getGif () {
    return gif;
  }

  public void attach () {
    preview.attach();
    receiver.attach();
  }

  public void detach () {
    preview.detach();
    receiver.detach();
  }

  public void destroy () {
    preview.requestFile(null);
    receiver.requestFile(null);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (receiver.needPlaceholder()) {
      if (preview.needPlaceholder()) {
        preview.drawPlaceholder(c);
      }
      preview.draw(c);
    }
    receiver.draw(c);
  }
}
