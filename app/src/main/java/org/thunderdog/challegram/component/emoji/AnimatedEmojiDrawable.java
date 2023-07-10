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
 * File created on 05/06/2023
 */
package org.thunderdog.challegram.component.emoji;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.tool.Drawables;

import me.vkryl.core.lambda.Destroyable;

public class AnimatedEmojiDrawable extends Drawable implements Destroyable {

  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;
  private Drawable drawable;

  public AnimatedEmojiDrawable (View parentView) {
    imageReceiver = new ImageReceiver(parentView, 0);
    gifReceiver = new GifReceiver(parentView);
  }

  public void setSticker (TGStickerObj sticker, boolean isPlayOnce) {
    if (sticker.isDefaultPremiumStar()) {
      drawable = Drawables.get(R.drawable.baseline_premium_star_28).mutate();
      return;
    }
    ImageFile imageFile = sticker.getImage();
    GifFile animation = sticker.getPreviewAnimation();
    if (animation != null) {
      if (isPlayOnce) {
        animation.setPlayOnce(true);
        animation.setLooped(false);
      }
      gifReceiver.requestFile(animation);
    }
    imageReceiver.requestFile(imageFile);
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
  public void draw (@NonNull Canvas canvas) {
    if (drawable != null) {
      drawable.draw(canvas);
      return;
    }
    if (gifReceiver.needPlaceholder() || Config.DEBUG_REACTIONS_ANIMATIONS) {
      imageReceiver.draw(canvas);
    }
    gifReceiver.draw(canvas);
  }

  @Override
  public void setBounds (@NonNull Rect bounds) {
    gifReceiver.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    imageReceiver.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    if (drawable != null) {
      drawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }
    super.setBounds(bounds);
  }

  @Override
  public void setBounds (int left, int top, int right, int bottom) {
    gifReceiver.setBounds(left, top, right, bottom);
    imageReceiver.setBounds(left, top, right, bottom);
    if (drawable != null) {
      drawable.setBounds(left, top, right, bottom);
    }
    super.setBounds(left, top, right, bottom);
  }

  @Override
  public void setAlpha (int i) {
    gifReceiver.setAlpha(i / 255f);
    imageReceiver.setAlpha(i / 255f);
    if (drawable != null) {
      drawable.setAlpha(i);
    }
  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {

  }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }

  @Override
  public void performDestroy () {
    gifReceiver.destroy();
    imageReceiver.destroy();
  }
}
