package org.thunderdog.challegram.component.reaction;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.util.ObjectsCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.AttachDelegate;

import me.vkryl.td.Td;

public class ReactionView extends View implements AttachDelegate {

  @NonNull
  private final Tdlib tdlib;
  @NonNull
  private final GifReceiver gifReceiver;
  @NonNull
  private final ImageReceiver imageReceiver;
  @Nullable
  private GifReceiver activateAnimationReceiver;
  @Nullable
  private TdApi.Reaction reaction;

  public ReactionView (@NonNull Context context, @NonNull Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;

    gifReceiver = new GifReceiver(this);
    imageReceiver = new ImageReceiver(this, 0);

    setWillNotDraw(false);
  }

  public void setPadding (@Px int padding) {
    setPadding(padding, padding, padding, padding);
  }

  public void setPadding (@Px int horizontalPadding, @Px int verticalPadding) {
    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
  }

  public void setReaction (@Nullable TdApi.Reaction reaction, boolean showAppearAnimation) {
    String newReaction = reaction != null ? reaction.reaction : null;
    String oldReaction = this.reaction != null ? this.reaction.reaction : null;
    if (ObjectsCompat.equals(oldReaction, newReaction)) {
      return;
    }
    this.reaction = reaction;
    if (reaction != null) {
      if (showAppearAnimation) {
        TdApi.Sticker appearAnimation = reaction.appearAnimation;
        if (appearAnimation != null && Td.isAnimated(appearAnimation.type)) {
          GifFile gifFile = new GifFile(tdlib, appearAnimation);
          gifFile.setScaleType(GifFile.FIT_CENTER);
          gifFile.setUnique(true);
          gifFile.setPlayOnce(true);
          gifFile.setOptimize(true);

          gifReceiver.requestFile(gifFile);
        } else {
          gifReceiver.clear();
        }
      } else {
        gifReceiver.clear();
      }

      TdApi.Sticker staticIcon = reaction.staticIcon;

      if (staticIcon != null && !Td.isAnimated(staticIcon.type)) {
        ImageFile imageFile = new ImageFile(tdlib, staticIcon.sticker);
        imageFile.setScaleType(ImageFile.FIT_CENTER);
        imageFile.setWebp();
        imageFile.setNoBlur();

        imageReceiver.requestFile(imageFile);
      } else {
        imageReceiver.clear();
      }
    } else {
      gifReceiver.clear();
      imageReceiver.clear();
    }
    invalidate();
  }

  @Nullable
  private static TdApi.Sticker getActivateAnimation (@Nullable TdApi.Reaction reaction) {
    return reaction != null ? reaction.selectAnimation : null; // FIXME select -> activate
  }

  private void preloadAnimation (@Nullable TdApi.Sticker animation) {
    TdApi.File sticker = animation != null ? animation.sticker : null;
    if (sticker == null || TD.isFileLoaded(sticker)) {
      return;
    }
    tdlib.files().downloadFile(sticker);
  }

  public void preloadActivateAnimation () {
    if (reaction != null) {
      preloadAnimation(getActivateAnimation(reaction));
      preloadAnimation(reaction.effectAnimation);
    }
  }

  public void prepareActivateAnimation () {
    TdApi.Sticker activateAnimation = getActivateAnimation(reaction);
    if (activateAnimation != null && Td.isAnimated(activateAnimation.type)) {
      GifFile activateAnimationFile = new GifFile(tdlib, activateAnimation);
      activateAnimationFile.setScaleType(GifFile.CENTER_CROP);
      activateAnimationFile.setUnique(true);
      activateAnimationFile.setPlayOnce(true);
      activateAnimationFile.setOptimize(true);

      if (activateAnimationReceiver == null) {
        activateAnimationReceiver = new GifReceiver(this);
        if (isLaidOut()) {
          activateAnimationReceiver.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        }
      }
      activateAnimationReceiver.requestFile(activateAnimationFile);
    } else {
      if (activateAnimationReceiver != null) {
        activateAnimationReceiver.clear();
      }
    }
  }

  private boolean isActivateAnimationPlaying;

  public void playActivateAnimation () {
    if (isActivateAnimationPlaying) {
      return;
    }
    prepareActivateAnimation();
    if (activateAnimationReceiver == null) {
      return;
    }
    GifFile activateAnimationFile = activateAnimationReceiver.getCurrentFile();
    if (!TD.isFileLoadedAndExists(activateAnimationFile.getFile())) {
      return;
    }
    isActivateAnimationPlaying = true;
    activateAnimationFile.setLooped(false);
    invalidate();
  }

  @Nullable
  public TdApi.Reaction getReaction () {
    return reaction;
  }

  @Override
  public void attach () {
    gifReceiver.attach();
    imageReceiver.attach();

    if (activateAnimationReceiver != null) {
      activateAnimationReceiver.attach();
    }
  }

  @Override
  public void detach () {
    gifReceiver.detach();
    imageReceiver.detach();

    if (activateAnimationReceiver != null) {
      activateAnimationReceiver.detach();
    }
  }

  @Override
  public boolean hasOverlappingRendering () {
    return false;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    gifReceiver.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
    imageReceiver.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());

    if (activateAnimationReceiver != null) {
      activateAnimationReceiver.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
    }
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (isActivateAnimationPlaying) {
      if (activateAnimationReceiver != null) {
        GifFile currentFile = activateAnimationReceiver.getCurrentFile();
        if (currentFile != null && !currentFile.hasLooped()) {
          if (activateAnimationReceiver.needPlaceholder()) {
            gifReceiver.draw(canvas);
          } else {
            activateAnimationReceiver.draw(canvas);
          }
        } else {
          isActivateAnimationPlaying = false;
        }
      } else {
        isActivateAnimationPlaying = false;
      }
    }

    if (!isActivateAnimationPlaying) {
      if (gifReceiver.isEmpty()) {
        if (!imageReceiver.isEmpty()) {
          if (imageReceiver.needPlaceholder()) {
            imageReceiver.drawPlaceholderRounded(canvas, imageReceiver.getHeight() / 2);
          } else {
            imageReceiver.draw(canvas);
          }
        }
      } else {
        if (gifReceiver.needPlaceholder()) {
          if (!imageReceiver.isEmpty()) {
            imageReceiver.drawPlaceholderRounded(canvas, imageReceiver.getHeight() / 2);
          }
        } else {
          gifReceiver.draw(canvas);
        }
      }
    }
  }
}
