/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 30/08/2017
 */
package org.thunderdog.challegram.component.chat;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

import org.thunderdog.challegram.data.TGMessage;

public class MessageOverlayView extends View {
  private MessageView boundView;

  public MessageOverlayView (Context context) {
    super(context);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setEmpty();
        }
      });
    }
  }

  public MessageOverlayView setBoundView (MessageView boundView) {
    this.boundView = boundView;
    return this;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (getParent() instanceof MessageViewGroup) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } else {
      if (msg != null) {
        msg.buildLayout(getMeasuredWidth());
      }
      heightMeasureSpec = MeasureSpec.makeMeasureSpec(msg != null ? msg.getHeight() : 0, MeasureSpec.EXACTLY);
      setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
    }
  }

  private TGMessage msg;

  public void setMessage (TGMessage msg) {
    if (this.msg != msg) {
      if (this.msg != null) {
        this.msg.onDetachedFromOverlayView(this);
      }
      this.msg = msg;
      if (msg != null) {
        this.msg.onAttachedToOverlayView(this);
      }
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (msg != null) {
      if (boundView != null) {
        msg.drawOverlay(boundView, c); // Full overlay
      } else {

      }
    }
  }
}
