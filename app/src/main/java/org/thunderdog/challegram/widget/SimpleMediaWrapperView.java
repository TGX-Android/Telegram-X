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
 * File created on 27/02/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;

import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;

public class SimpleMediaWrapperView extends SparseDrawableView {
  private final DoubleImageReceiver preview;
  private final ImageReceiver receiver;
  private final ViewProvider provider;

  public SimpleMediaWrapperView (Context context) {
    super(context);
    this.preview = new DoubleImageReceiver(this, 0);
    this.receiver = new ImageReceiver(this, 0);
    this.provider = new SingleViewProvider(this) {
      @Override
      public boolean invalidateContent (Object cause) {
        if (wrapper != null) {
          wrapper.requestImage(receiver);
        }
        return true;
      }
    };
  }

  private @ThemeColorId
  int backgroundColorId;

  public void setBackgroundColorId (@ThemeColorId int backgroundColorId) {
    this.backgroundColorId = backgroundColorId;
  }

  private MediaWrapper wrapper;

  public void setWrapper (MediaWrapper wrapper) {
    if (this.wrapper != wrapper) {
      if (this.wrapper != null) {
        this.wrapper.setViewProvider(null);
      }
      this.wrapper = wrapper;
      if (this.wrapper != null) {
        layoutWrapper();
        requestFiles();
        wrapper.getFileProgress().downloadAutomatically();
        wrapper.setViewProvider(provider);
      }
    }
  }

  public void requestFiles () {
    if (this.wrapper != null) {
      wrapper.requestPreview(preview);
      wrapper.requestImage(receiver);
    } else {
      preview.clear();
      receiver.clear();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutWrapper();
  }

  private boolean fitsBounds;

  public void setFitsBounds () {
    fitsBounds = true;
  }

  private void layoutWrapper () {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    if (wrapper != null && viewWidth > 0 && viewHeight > 0) {
      if (fitsBounds) {
        int photoWidth = wrapper.getContentWidth();
        int photoHeight = wrapper.getContentHeight();

        float ratio = Math.min((float) viewWidth / (float) photoWidth, (float) viewHeight / (float) photoHeight);

        photoWidth *= ratio;
        photoHeight *= ratio;

        wrapper.buildContent(photoWidth, photoHeight);
      } else {
        wrapper.buildContent(viewWidth, viewHeight);
      }
    }
  }

  public void attach () {
    receiver.attach();
    preview.attach();
  }

  public void detach () {
    receiver.detach();
    preview.detach();
  }

  public void clear () {
    setWrapper(null);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return wrapper != null && wrapper.onTouchEvent(this, e);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (backgroundColorId != 0l) {
      c.drawRect(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom(), Paints.fillingPaint(Theme.getColor(backgroundColorId)));
    }
    if (wrapper != null) {
      final int centerX = getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2;

      wrapper.draw(this, c, centerX - wrapper.getCellWidth() / 2, getPaddingTop(), preview, receiver, 1f);
    }
  }
}
