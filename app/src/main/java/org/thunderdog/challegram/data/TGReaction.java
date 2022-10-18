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
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;

public class TGReaction {
  private final Tdlib tdlib;

  public final TdApi.ReactionType type;
  public final String key;

  public TdApi.EmojiReaction reaction;

  private final TGStickerObj _staticIconSicker;
  private final TGStickerObj _activateAnimationSicker;
  private final TGStickerObj _effectAnimationSicker;
  private final TGStickerObj _aroundAnimationSicker;
  private final TGStickerObj _centerAnimationSicker;

  private final TGStickerObj _staticCenterAnimationSicker;

  public TGReaction (Tdlib tdlib, TdApi.EmojiReaction reaction) {
    this.tdlib = tdlib;
    this.reaction = reaction;
    // TODO: custom reactions support
    this.type = new TdApi.ReactionTypeEmoji(reaction.emoji);
    this.key = TD.makeReactionKey(type);

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

  public boolean isPremium () {
    return type.getConstructor() == TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR;
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

  private TGStickerObj newStaticIconSicker () {
    return new TGStickerObj(tdlib, reaction.staticIcon, reaction.emoji, reaction.staticIcon.type);
  }

  private TGStickerObj newActivateAnimationSicker () {
    return new TGStickerObj(tdlib, reaction.activateAnimation, reaction.emoji, reaction.activateAnimation.type);
  }

  private TGStickerObj newEffectAnimationSicker () {
    return new TGStickerObj(tdlib, reaction.effectAnimation, reaction.emoji, reaction.effectAnimation.type);
  }

  public TGStickerObj newAroundAnimationSicker () {
    if (reaction.aroundAnimation != null) {
      return new TGStickerObj(tdlib, reaction.aroundAnimation, reaction.emoji, reaction.aroundAnimation.type);
    }
    return newEffectAnimationSicker();
  }

  public TGStickerObj newCenterAnimationSicker () {
    if (reaction.centerAnimation != null) {
      return new TGStickerObj(tdlib, reaction.centerAnimation, reaction.emoji, reaction.centerAnimation.type);
    }
    return newStaticIconSicker();
  }

  public TdApi.EmojiReaction getReaction () {
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
    private final TGReaction reaction;
    private final int width;
    private final int height;

    @Nullable private ImageFile imageFile;
    @Nullable private ImageReceiver imageReceiver;

    public ReactionDrawable (TGReaction reaction, int width, int height) {
      this.width = width;
      this.height = height;
      this.reaction = reaction;

      imageFile = reaction.staticCenterAnimationSicker().getImage();
    }

    public void setComplexReceiver (ComplexReceiver complexReceiver) {
      if (complexReceiver != null) {
        imageReceiver = complexReceiver.getImageReceiver(reaction.getId());
        imageReceiver.setBounds(0, 0, width, height);
        imageReceiver.requestFile(imageFile);
      } else if (imageReceiver != null) {
        imageReceiver.clear();
        imageReceiver = null;
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      if (imageReceiver != null) {
        imageReceiver.draw(c);
      }
    }

    @Override
    public void setAlpha (int i) {
      if (imageReceiver != null) {
        imageReceiver.setAlpha(i / 255f);
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
