package org.thunderdog.challegram.component.emoji;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;

import me.vkryl.core.lambda.Destroyable;

public class AnimatedEmojiDrawable extends Drawable implements Destroyable {

  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;

  public AnimatedEmojiDrawable (View parentView) {
    imageReceiver = new ImageReceiver(parentView, 0);
    gifReceiver = new GifReceiver(parentView);
  }

  public void setSticker (TGStickerObj sticker, boolean isPlayOnce) {
    ImageFile imageFile = sticker.getImage();
    GifFile animation = sticker.getPreviewAnimation();
    float displayScale = sticker.getDisplayScale();
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
    if (gifReceiver.needPlaceholder() || Config.DEBUG_REACTIONS_ANIMATIONS) {
      imageReceiver.draw(canvas);
    }
    gifReceiver.draw(canvas);
  }

  @Override
  public void setBounds (@NonNull Rect bounds) {
    gifReceiver.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    imageReceiver.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    super.setBounds(bounds);
  }

  @Override
  public void setBounds (int left, int top, int right, int bottom) {
    gifReceiver.setBounds(left, top, right, bottom);
    imageReceiver.setBounds(left, top, right, bottom);
    super.setBounds(left, top, right, bottom);
  }

  @Override
  public void setAlpha (int i) {
    gifReceiver.setAlpha(i / 255f);
    imageReceiver.setAlpha(i / 255f);
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
