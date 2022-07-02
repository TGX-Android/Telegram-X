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
 * File created on 22/11/2016
 */
package org.thunderdog.challegram.component.sticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.lambda.Destroyable;

public class ReactionInChatView extends View implements Destroyable {

  public static final float PADDING = 8f;

  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;
  private @Nullable TGStickerObj sticker;
  private Path contour;

  private boolean isAnimation;

  public ReactionInChatView (Context context) {
    super(context);
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
  }

  public void setSticker (@Nullable TGStickerObj sticker) {
    this.sticker = sticker;
    this.isAnimation = sticker != null && sticker.isAnimated();
    ImageFile imageFile = sticker != null && !sticker.isEmpty() ? sticker.getImage() : null;
    GifFile gifFile = sticker != null && !sticker.isEmpty() ? sticker.getPreviewAnimation() : null;
    if ((sticker == null || sticker.isEmpty()) && imageFile != null) {
      throw new RuntimeException("");
    }
    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
    imageReceiver.requestFile(imageFile);
    if (gifFile != null)
      gifFile.setPlayOnce(true);
    gifReceiver.requestFile(gifFile);
  }

  public void attach () {
    imageReceiver.attach();
    gifReceiver.attach();
  }

  public void detach () {
    imageReceiver.detach();
    gifReceiver.detach();
  }

  @Override
  public void performDestroy () {
    imageReceiver.destroy();
    gifReceiver.destroy();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    int padding = Screen.dp(PADDING);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    imageReceiver.setBounds(padding, padding + getPaddingTop(), width - padding, height - getPaddingBottom() - padding);
    gifReceiver.setBounds(padding, padding + getPaddingTop(), width - padding, height - getPaddingBottom() - padding);
    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (isAnimation) {
      if (gifReceiver.needPlaceholder()) {
        if (imageReceiver.needPlaceholder()) {
          imageReceiver.drawPlaceholderContour(c, contour);
        }
        imageReceiver.draw(c);
      }
      gifReceiver.draw(c);
    } else {
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholderContour(c, contour);
      }
      imageReceiver.draw(c);
    }
  }
}
