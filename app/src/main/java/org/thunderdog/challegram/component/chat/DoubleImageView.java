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
 * File created on 05/12/2016
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;

import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.BaseView;

public class DoubleImageView extends BaseView {
  private final DoubleImageReceiver preview;
  private final ImageReceiver receiver;

  public DoubleImageView (Context context) {
    super(context, null);

    preview = new DoubleImageReceiver(this, 0);
    receiver = new ImageReceiver(this, 0);
  }

  public void setImage (ImageFile miniThumbnail, ImageFile previewFile, ImageFile targetFile) {
    preview.requestFile(miniThumbnail, previewFile);
    receiver.requestFile(targetFile);
  }

  public void attach () {
    preview.attach();
    receiver.attach();
  }

  public void detach () {
    preview.detach();
    receiver.detach();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
      MeasureSpec.makeMeasureSpec(Screen.dp(118f), MeasureSpec.EXACTLY));
    preview.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    receiver.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
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
