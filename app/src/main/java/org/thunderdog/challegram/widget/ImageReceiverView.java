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
 * File created on 30/08/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.DrawAlgorithms;

import me.vkryl.core.lambda.Destroyable;

public class ImageReceiverView extends View implements Destroyable, AttachDelegate {
  private final ImageReceiver receiver;
  private boolean isCircular;

  private Bitmap overlayBitmap;

  public ImageReceiverView (Context context) {
    super(context);
    this.receiver = new ImageReceiver(this, 0);
  }

  public void setOverlayBitmap (Bitmap bitmap) {
    if (this.overlayBitmap != bitmap) {
      this.overlayBitmap = bitmap;
      invalidate();
    }
  }

  public void setCircular (boolean circular) {
    isCircular = circular;
  }

  public ImageReceiver getReceiver () {
    return receiver;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();
    receiver.setBounds(getPaddingLeft(), getPaddingTop(), viewWidth - getPaddingRight(), viewHeight - getPaddingBottom());
    if (isCircular) {
      receiver.setRadius(Math.min(viewWidth, viewHeight) / 2);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    receiver.draw(c);
    DrawAlgorithms.drawScaledBitmap(this, c, overlayBitmap);
  }

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
  }
}
