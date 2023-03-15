package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.text.TextMedia;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class TGReaction {
  private final Tdlib tdlib;

  public final TdApi.ReactionType type;
  public final String key;

  private final TdApi.EmojiReaction emojiReaction;
  private final TdApi.Sticker customReaction;

  private TGStickerObj _staticIconSicker;
  private TGStickerObj _activateAnimationSicker;
  private TGStickerObj _effectAnimationSicker;
  private TGStickerObj _aroundAnimationSicker;
  private TGStickerObj _centerAnimationSicker;
  private TGStickerObj _staticCenterAnimationSicker;

  public TGReaction (@NonNull Tdlib tdlib, @NonNull TdApi.EmojiReaction reaction) {
    this.tdlib = tdlib;
    this.emojiReaction = reaction;
    this.type = new TdApi.ReactionTypeEmoji(reaction.emoji);
    this.key = TD.makeReactionKey(type);
    this.customReaction = null;

    initialize();
  }

  public TGReaction (@NonNull Tdlib tdlib, @NonNull TdApi.Sticker customReaction) {
    this.tdlib = tdlib;
    this.customReaction = customReaction;
    this.type = new TdApi.ReactionTypeCustomEmoji(Td.customEmojiId(customReaction));
    this.key = TD.makeReactionKey(type);
    this.emojiReaction = null;

    initialize();
  }

  private void initialize () {
    _staticIconSicker = newStaticIconSicker();
    _activateAnimationSicker = newActivateAnimationSicker();

    _effectAnimationSicker = newEffectAnimationSicker();
    _aroundAnimationSicker = newAroundAnimationSicker();
    _centerAnimationSicker = newCenterAnimationSicker();

    _staticCenterAnimationSicker = newCenterAnimationSicker();
    if (_staticCenterAnimationSicker.getPreviewAnimation() != null && !_staticCenterAnimationSicker.isCustomReaction()) {
      _staticCenterAnimationSicker.getPreviewAnimation().setPlayOnce(true);
      _staticCenterAnimationSicker.getPreviewAnimation().setLooped(true);
    }

    loadAllAnimationsAndCache();
  }

  public boolean isPremium () {
    return isCustom();
  }

  public boolean isCustom () {
    return type.getConstructor() == TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR;
  }

  public String getTitle () {
    return emojiReaction != null ? emojiReaction.title : "";
  }

  public TGStickerObj staticIconSicker () {
    return _staticIconSicker;
  }

  public TGStickerObj activateAnimationSicker () {
    return _activateAnimationSicker;
  }

  @Nullable
  public TGStickerObj effectAnimationSicker () {
    return _effectAnimationSicker;
  }

  public void withEffectAnimation (RunnableData<TGStickerObj> after) {
    if (_effectAnimationSicker != null) {
      after.runWithData(_effectAnimationSicker);
    } else {
      tdlib.pickRandomGenericOverlaySticker(sticker -> {
        if (sticker != null) {
          TGStickerObj effectAnimation = new TGStickerObj(tdlib, sticker, null, sticker.fullType)
            .setReactionType(type);
          tdlib.ui().execute(() ->
            after.runWithData(effectAnimation)
          );
        } else {
          tdlib.ui().execute(() ->
            after.runWithData(null)
          );
        }
      });
    }
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

  public void loadAllAnimationsAndCache () {
    if (emojiReaction != null) {
      loadAnimationAndCache(emojiReaction.staticIcon.sticker);
      loadAnimationAndCache(emojiReaction.effectAnimation.sticker);
      loadAnimationAndCache(emojiReaction.activateAnimation.sticker);

      if (emojiReaction.aroundAnimation != null) {
        loadAnimationAndCache(emojiReaction.aroundAnimation.sticker);
      }

      if (emojiReaction.centerAnimation != null) {
        loadAnimationAndCache(emojiReaction.centerAnimation.sticker);
      }
    } else if (customReaction != null) {

    }
  }

  private void loadAnimationAndCache (TdApi.File file) {
    tdlib.files().isFileLoadedAndExists(file, loadedAndExists -> {
      if (!loadedAndExists) {
        tdlib.files().downloadFile(file);
      }
    });
  }

  public int getId () {
    return _staticIconSicker.getId();
  }

  private TGStickerObj newStaticIconSicker () {
    if (emojiReaction != null) {
      return new TGStickerObj(tdlib, emojiReaction.staticIcon, emojiReaction.emoji, emojiReaction.staticIcon.fullType).setReactionType(type).setDisplayScale(.5f);
    } else {
      float displayScale = TextMedia.getScale(customReaction, 0) * .5f;
      return new TGStickerObj(tdlib, customReaction, null, customReaction.fullType).setReactionType(type).setDisplayScale(displayScale).setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI);
    }
  }

  private TGStickerObj newActivateAnimationSicker () {
    if (emojiReaction != null && !Config.TEST_STATIC_REACTIONS) {
      return new TGStickerObj(tdlib, emojiReaction.activateAnimation, emojiReaction.emoji, emojiReaction.activateAnimation.fullType).setReactionType(type);
    } else {
      return newStaticIconSicker();
    }
  }

  private TGStickerObj newEffectAnimationSicker () {
    if (emojiReaction != null) {
      return new TGStickerObj(tdlib, emojiReaction.effectAnimation, emojiReaction.emoji, emojiReaction.effectAnimation.fullType).setReactionType(type);
    } else {
      return null;
    }
  }

  public TGStickerObj newAroundAnimationSicker () {
    if (emojiReaction != null && emojiReaction.aroundAnimation != null) {
      return new TGStickerObj(tdlib, emojiReaction.aroundAnimation, emojiReaction.emoji, emojiReaction.aroundAnimation.fullType).setReactionType(type);
    }
    return newEffectAnimationSicker();
  }

  public TGStickerObj newCenterAnimationSicker () {
    if (emojiReaction != null && emojiReaction.centerAnimation != null && !Config.TEST_STATIC_REACTIONS) {
      return new TGStickerObj(tdlib, emojiReaction.centerAnimation, emojiReaction.emoji, emojiReaction.centerAnimation.fullType).setReactionType(type);
    }
    return newStaticIconSicker();
  }

  public static class ReactionDrawable extends Drawable {
    private final TGReaction reaction;
    private final int width;
    private final int height;

    @Nullable private final ImageFile imageFile;
    private final float displayScale;
    @Nullable private ImageReceiver imageReceiver;

    public ReactionDrawable (TGReaction reaction, int width, int height) {
      this.width = width;
      this.height = height;
      this.reaction = reaction;

      TGStickerObj stickerObj = reaction.staticCenterAnimationSicker();
      imageFile = stickerObj.getImage();
      displayScale = stickerObj.getDisplayScale();
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
        imageReceiver.drawScaled(c, displayScale);
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
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }
}
