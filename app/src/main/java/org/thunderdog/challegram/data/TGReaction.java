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

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.Destroyable;

public class TGReaction {
  private final Tdlib tdlib;
  public TdApi.Reaction reaction;

  private final TGStickerObj _staticIconSicker;
  private final TGStickerObj _activateAnimationSicker;
  private final TGStickerObj _effectAnimationSicker;
  private final TGStickerObj _aroundAnimationSicker;
  private final TGStickerObj _centerAnimationSicker;

  public TGReaction (Tdlib tdlib, TdApi.Reaction reaction) {
    this.tdlib = tdlib;
    this.reaction = reaction;

    _staticIconSicker = newStaticIconSicker();
    _activateAnimationSicker = newActivateAnimationSicker();
    _effectAnimationSicker = newEffectAnimationSicker();
    _aroundAnimationSicker = newAroundAnimationSicker();
    _centerAnimationSicker = newCenterAnimationSicker();

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
    loadFileAndCache(_staticIconSicker);
    loadFileAndCache(_activateAnimationSicker);
    loadFileAndCache(_effectAnimationSicker);
    loadFileAndCache(_aroundAnimationSicker);
    loadFileAndCache(_centerAnimationSicker);
  }

  private void loadFileAndCache (TGStickerObj stickerObj) {
    loadAnimationAndCache(stickerObj.getPreviewAnimation());
    loadAnimationAndCache(stickerObj.getFullAnimation());
  }

  private void loadAnimationAndCache (GifFile gifFile) {
    if (gifFile == null) {
      return;
    }

    TdApi.File file = gifFile.getFile();
    if (!TD.isFileLoadedAndExists(file)) {
      tdlib.files().downloadFile(file);
    }
  }

  public static class ReactionDrawable extends Drawable /*implements Destroyable*/ {
    private final TGStickerObj stickerObj;
    private final int width;
    private final int height;
    private final boolean isAnimation;

    @Nullable private final ImageFile imageFile;
    @Nullable private final GifFile gifFile;

    @Nullable private ImageReceiver imageReceiver;
    @Nullable private GifReceiver gifReceiver;




    public ReactionDrawable (View view, TGStickerObj sticker, int width, int height) {
      this.stickerObj = sticker;
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

    /*public void attach () {
      if (imageReceiver != null) imageReceiver.attach();
      if (gifReceiver != null) gifReceiver.attach();
    }

    public void detach () {
      if (imageReceiver != null) imageReceiver.detach();
      if (gifReceiver != null) gifReceiver.detach();
    }

    @Override
    public void performDestroy () {
      if (imageReceiver != null) imageReceiver.destroy();
      if (gifReceiver != null) gifReceiver.destroy();
    }*/
  }
}
