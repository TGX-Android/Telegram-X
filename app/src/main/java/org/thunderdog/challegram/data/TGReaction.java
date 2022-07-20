package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;

public class TGReaction {
  private final Tdlib tdlib;
  public TdApi.Reaction reaction;

  private final TGStickerObj _staticIconSicker;
  private final TGStickerObj _activateAnimationSicker;
  private final TGStickerObj _effectAnimationSicker;
  private final TGStickerObj _aroundAnimationSicker;
  private final TGStickerObj _centerAnimationSicker;

  private final TGStickerObj _staticCenterAnimationSicker;

  public TGReaction (Tdlib tdlib, TdApi.Reaction reaction) {
    this.tdlib = tdlib;
    this.reaction = reaction;

    _staticIconSicker = newStaticIconSicker();
    _activateAnimationSicker = newActivateAnimationSicker();
    _effectAnimationSicker = newEffectAnimationSicker();
    _aroundAnimationSicker = newAroundAnimationSicker();
    _centerAnimationSicker = newCenterAnimationSicker();

    _staticCenterAnimationSicker = newCenterAnimationSicker();
    if (_staticCenterAnimationSicker.getPreviewAnimation() != null) {
      _staticCenterAnimationSicker.getPreviewAnimation().setPlayOnce(true);
      _staticCenterAnimationSicker.getPreviewAnimation().setLooped(true);
    }

    tdlib.ui().post(this::loadAllAnimationsAndCache);
  }

  public TGStickerObj staticIconSicker () {
    return _staticIconSicker;
  }

  public TGStickerObj activateAnimationSicker () {
    return _activateAnimationSicker;
  }

  public TGStickerObj effectAnimationSicker () {
    return _effectAnimationSicker;
  }

  public TGStickerObj aroundAnimationSicker () {
    return _aroundAnimationSicker;
  }

  public TGStickerObj centerAnimationSicker () {
    return _centerAnimationSicker;
  }

  public TGStickerObj staticCenterAnimationSicker () {
    return _staticCenterAnimationSicker;
  }

  public TGStickerObj newStaticIconSicker () {
    return new TGStickerObj(tdlib, reaction.staticIcon, reaction.reaction, reaction.staticIcon.type);
  }

  public TGStickerObj newActivateAnimationSicker () {
    return new TGStickerObj(tdlib, reaction.activateAnimation, reaction.reaction, reaction.activateAnimation.type);
  }

  public TGStickerObj newEffectAnimationSicker () {
    return new TGStickerObj(tdlib, reaction.effectAnimation, reaction.reaction, reaction.effectAnimation.type);
  }

  public TGStickerObj newAroundAnimationSicker () {
    if (reaction.aroundAnimation != null) {
      return new TGStickerObj(tdlib, reaction.aroundAnimation, reaction.reaction, reaction.aroundAnimation.type);
    }
    return newEffectAnimationSicker();
  }

  public TGStickerObj newCenterAnimationSicker () {
    if (reaction.centerAnimation != null) {
      return new TGStickerObj(tdlib, reaction.centerAnimation, reaction.reaction, reaction.centerAnimation.type);
    }
    return newStaticIconSicker();
  }

  public TdApi.Reaction getReaction () {
    return this.reaction;
  }

  public void loadAllAnimationsAndCache () {
    loadAnimationAndCache(reaction.staticIcon.sticker);
    loadAnimationAndCache(reaction.effectAnimation.sticker);
    loadAnimationAndCache(reaction.activateAnimation.sticker);

    if (reaction.aroundAnimation != null) {
      loadAnimationAndCache(reaction.aroundAnimation.sticker);
    }

    if (reaction.centerAnimation != null) {
      loadAnimationAndCache(reaction.centerAnimation.sticker);
    }
  }

  private void loadAnimationAndCache (TdApi.File file) {
    if (!TD.isFileLoadedAndExists(file)) {
      tdlib.files().downloadFile(file);
    }
  }

  public int getId () {
    return _staticIconSicker.getId();
  }

  public static class ReactionDrawable extends Drawable {
    private final int width;
    private final int height;
    private boolean isAnimation;

    @Nullable private ImageFile imageFile;
    @Nullable private GifFile gifFile;

    @Nullable private ImageReceiver imageReceiver;
    @Nullable private GifReceiver gifReceiver;

    public ReactionDrawable (View view, TGStickerObj sticker, int width, int height) {
      this.width = width;
      this.height = height;
      this.imageFile = sticker != null && !sticker.isEmpty() ? sticker.getImage() : null;
      this.gifFile = sticker != null && !sticker.isEmpty() ? sticker.getPreviewAnimation() : null;
      this.isAnimation = sticker != null && sticker.isAnimated();
      init(view);
    }

    private void init (View view) {
      imageReceiver = new ImageReceiver(view, 0);
      imageReceiver.setBounds(0, 0, width, height);
      imageReceiver.requestFile(imageFile);
      if (view != null) {
        gifReceiver = new GifReceiver(view);
        gifReceiver.setBounds(0, 0, width, height);
        gifReceiver.requestFile(gifFile);
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      if (isAnimation) {
        if (gifReceiver != null) {
          gifReceiver.draw(c);
        }
      } else {
        if (imageReceiver != null) {
          imageReceiver.draw(c);
        }
      }
    }

    @Override
    public void setAlpha (int i) {
      if (imageReceiver != null) {
        imageReceiver.setAlpha(i / 255f);
      }
      if (gifReceiver != null) {
        gifReceiver.setAlpha(i / 255f);
      }
    }

    @Override
    public int getMinimumWidth () {
      return this.width;
    }

    @Override
    public int getMinimumHeight () {
      return this.height;
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }
}
