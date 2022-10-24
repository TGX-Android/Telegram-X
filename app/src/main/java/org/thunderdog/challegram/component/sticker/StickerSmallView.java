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
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;

public class StickerSmallView extends View implements FactorAnimator.Target, Destroyable {
  public static final float PADDING = 8f;
  private static final Interpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(3.2f);

  private ImageReceiver imageReceiver;
  private GifReceiver gifReceiver;
  private FactorAnimator animator;
  private @Nullable TGStickerObj sticker;
  private Path contour;
  private Tdlib tdlib;
  private int padding;

  public StickerSmallView (Context context) {
    super(context);
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
    this.animator = new FactorAnimator(0, this, OVERSHOOT_INTERPOLATOR, 230l);
    this.padding = Screen.dp(PADDING);
  }

  public StickerSmallView (Context context, int padding) {
    super(context);
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
    this.animator = new FactorAnimator(0, this, OVERSHOOT_INTERPOLATOR, 230l);
    this.padding = padding;
  }

  public void init (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  private boolean isAnimation;

  public void setPadding (int padding) {
    this.padding = padding;
    measureReceivers();
  }

  public void setSticker (@Nullable TGStickerObj sticker) {
    this.sticker = sticker;
    this.isAnimation = sticker != null && sticker.isAnimated();
    resetStickerState();
    ImageFile imageFile = sticker != null && !sticker.isEmpty() ? sticker.getImage() : null;
    GifFile gifFile = sticker != null && !sticker.isEmpty() ? sticker.getPreviewAnimation() : null;
    if ((sticker == null || sticker.isEmpty()) && imageFile != null) {
      throw new RuntimeException("");
    }
    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
    imageReceiver.requestFile(imageFile);
    gifReceiver.requestFile(gifFile);
  }

  public void refreshSticker () {
    setSticker(sticker);
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

  private float factor;

  private void resetStickerState () {
    animator.forceFactor(0f, true);
    factor = 0f;
  }

  private static final float MIN_SCALE = .82f;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    /*if (finalFactor == 0f) {
      animator.setInterpolator(OVERSHOOT_INTERPOLATOR);
    } else if (finalFactor == 1f) {
      animator.setInterpolator(Anim.DECELERATE_INTERPOLATOR);
    }*/
  }

  private boolean isTrending;

  public void setIsTrending () {
    isTrending = true;
  }

  private boolean isSuggestion;

  public void setIsSuggestion () {
    isSuggestion = true;
  }

  private boolean emojiDisabled;

  public void setEmojiDisabled () {
    emojiDisabled = true;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (isSuggestion) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(Screen.dp(72f), MeasureSpec.EXACTLY), heightMeasureSpec);
    } else if (isTrending) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.smallestSide() / 5, MeasureSpec.EXACTLY));
    } else {
      //noinspection SuspiciousNameCombination
      super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
    measureReceivers();
  }

  private void measureReceivers () {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    imageReceiver.setBounds(padding, padding + getPaddingTop(), width - padding, height - getPaddingBottom() - padding);
    gifReceiver.setBounds(padding, padding + getPaddingTop(), width - padding, height - getPaddingBottom() - padding);
    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
  }

  @Override
  protected void onDraw (Canvas c) {
    float originalScale = sticker != null ? sticker.getDisplayScale() : 1f;
    boolean saved = originalScale != 1f || factor != 0f;
    if (saved) {
      c.save();
      float scale = originalScale * (MIN_SCALE + (1f - MIN_SCALE) * (1f - factor));
      int cx = getMeasuredWidth() / 2;
      int cy = getPaddingTop() + (getMeasuredHeight() - getPaddingBottom() - getPaddingBottom()) / 2;
      c.scale(scale, scale, cx, cy);
    }
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
    if (Config.DEBUG_STICKER_OUTLINES) {
      imageReceiver.drawPlaceholderContour(c, contour);
    }
    if (saved) {
      c.restore();
    }
  }

  // Touch

  private float startX, startY;
  private CancellableRunnable longPress;

  private void openPreviewDelayed () {
    cancelDelayedPreview();
    longPress = new CancellableRunnable() {
      @Override
      public void act () {
        openPreview();
        previewScheduled = false;
      }
    };
    previewScheduled = true;
    postDelayed(longPress, 300);
  }

  private boolean previewScheduled;

  private void cancelDelayedPreview () {
    if (longPress != null) {
      longPress.cancel();
      longPress = null;
    }
    previewScheduled = false;
  }

  public interface StickerMovementCallback {
    boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions);
    long getStickerOutputChatId ();
    void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed);
    boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY);
    default void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) { }
    default void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) { }
    default void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) { }
    boolean needsLongDelay (StickerSmallView view);
    int getStickersListTop ();
    int getViewportHeight ();
    default boolean needShowButtons () { return true; }
    default int getStickerViewLeft (StickerSmallView v) { return -1; }
    default int getStickerViewTop (StickerSmallView v) { return -1; }
    default StickerSmallView getStickerViewUnder (StickerSmallView v, int x, int y) { return null; }
    default TGReaction getReactionForPreview (StickerSmallView v) { return null; }
  }

  private @Nullable StickerMovementCallback callback;

  public void setStickerMovementCallback (StickerMovementCallback callback) {
    this.callback = callback;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        startX = e.getX();
        startY = e.getY();
        openPreviewDelayed();
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        closePreview(e);
        return true;
      }
      case MotionEvent.ACTION_UP: {
        boolean clicked = previewScheduled && !previewOpened;
        closePreview(e);
        if (clicked && callback != null && sticker != null) {
          ViewUtils.onClick(this);
          callback.onStickerClick(this, this, sticker, false, null);
        }
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (previewOpened) {
          int x = getRealLeft() + (int) e.getX();
          int y = getRealTop() + (int) e.getY();
          onFingerMovement(e, x, y);
        } else if (previewScheduled && Math.max(Math.abs(startX - e.getX()), Math.abs(startY - e.getY())) > Screen.getTouchSlop()) {
          cancelDelayedPreview();
        }
        break;
      }
    }
    return true;
  }

  private CancellableRunnable scheduledButtons;

  private void cancelScheduledButtons () {
    if (scheduledButtons != null) {
      scheduledButtons.cancel();
      scheduledButtons = null;
    }
  }

  public boolean scheduleButtons (final StickerSmallView view, final TGStickerObj sticker, final boolean longDelay, final boolean isChange) {
    cancelScheduledButtons();
    if (longDelay && isChange) {
      return false;
    }
    scheduledButtons = new CancellableRunnable() {
      @Override
      public void act () {
        openScheduledButtons(view, sticker);
      }
    };
    scheduledButtons.removeOnCancel(UI.getAppHandler());
    UI.post(scheduledButtons, /*longDelay ? 2500l :*/ isChange ? 1500l : 1000l);
    return true;
  }

  private void openScheduledButtons (StickerSmallView view, TGStickerObj stickerObj) {
    UI.forceVibrate(view, false);
    view.openStickerMenu();
  }

  private TGStickerObj currentSticker;

  private boolean ignoreNextStickerChanges;
  private boolean dispatchingMenuEvents;

  public void openStickerMenu () {
    ignoreNextStickerChanges = true;
    ((BaseActivity) getContext()).openStickerMenu(this, currentSticker);
  }

  private void processMenuTouchEvent (MotionEvent e) {
    StickerSmallView target = this;
    int i = 0;
    int deltaY = getRealTop(target);
    if (callback != null) {
      deltaY += callback.getStickersListTop();
    }
    int deltaX = getRealLeft(target);
    e.offsetLocation(deltaX, deltaY);
    ((BaseActivity) getContext()).dispatchStickerMenuTouchEvent(e);
  }

  private void onFingerMovement (MotionEvent e, int x, int y) {
    if (previewDroppedButStillOpen || getParent() == null) {
      return;
    }

    if (false && ignoreNextStickerChanges) {
      if (dispatchingMenuEvents || (dispatchingMenuEvents = Math.max(Math.abs(e.getX() - startX), Math.abs(e.getY() - startY)) >= Screen.getTouchSlopBig())) {
        processMenuTouchEvent(e);
      }
    } else {
      View view = null;
      if (callback != null) {
        view = callback.getStickerViewUnder(this, x, y);
      }
      if (view == null) {
        view = callback == null || callback.canFindChildViewUnder(this, x, y) ? ((RecyclerView) getParent()).findChildViewUnder(x, y) : null;
      }
      if (view instanceof StickerSmallView) {
        StickerSmallView stickerSmallView = (StickerSmallView) view;
        TGStickerObj sticker = ((StickerSmallView) view).getSticker();
        if (sticker != null && !sticker.isEmpty() && !sticker.equals(currentSticker)) {
          ignoreNextStickerChanges = false;
          if (callback != null) {
            callback.setStickerPressed(this, currentSticker, false);
          }
          currentSticker = sticker;
          boolean replaced = false;
          if (callback != null) {
            TGReaction reaction = callback.getReactionForPreview(stickerSmallView);
            if (reaction != null) {
              ((BaseActivity) getContext()).replaceReactionPreview(reaction, getRealLeft(stickerSmallView) + view.getMeasuredWidth() / 2, getRealTop(stickerSmallView) + view.getPaddingTop() + (view.getMeasuredHeight() - view.getPaddingBottom() - view.getPaddingTop()) / 2 + (callback != null ? callback.getStickersListTop() : 0));
              replaced = true;
            }
          }
          if (!replaced) {
            ((BaseActivity) getContext()).replaceStickerPreview(sticker, getRealLeft(stickerSmallView) + view.getMeasuredWidth() / 2, getRealTop(stickerSmallView) + view.getPaddingTop() + (view.getMeasuredHeight() - view.getPaddingBottom() - view.getPaddingTop()) / 2 + (callback != null ? callback.getStickersListTop() : 0));
          }
          boolean needShowButtons = true;
          boolean needLongDelay = false;
          if (callback != null) {
            callback.onStickerPreviewChanged(this, sticker);
            callback.setStickerPressed(this, sticker, true);
            needLongDelay = callback.needsLongDelay(this);
            needShowButtons = callback.needShowButtons();
          }
          if (needShowButtons) {
            scheduleButtons(this, sticker, needLongDelay, true);
          }
          if (Config.USE_STICKER_VIBRATE) {
            UI.forceVibrate(this, false);
          }
        }
      }
    }
  }

  // Preview

  private boolean previewOpened;

  private boolean isPressed;
  public void setStickerPressed (boolean isPressed) {
    if (this.isPressed != isPressed) {
      this.isPressed = isPressed;
      animator.animateTo(isPressed ? 1f : 0f);
    }
  }

  private void openPreview () {
    if (previewOpened || sticker == null || sticker.isEmpty()) {
      return;
    }

    getParent().requestDisallowInterceptTouchEvent(true);
    UI.getContext(getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_STICKER, true);

    previewOpened = true;
    ignoreNextStickerChanges = false;
    dispatchingMenuEvents = false;
    previewDroppedButStillOpen = false;
    setStickerPressed(true);
    currentSticker = sticker;

    boolean needShowButtons = true;
    boolean needLongDelay = false;
    if (callback != null) {
      callback.onStickerPreviewOpened(this, sticker);
      needLongDelay = callback.needsLongDelay(this);
      needShowButtons = callback.needShowButtons();
    }
    if (needShowButtons) {
      scheduleButtons(this, sticker, needLongDelay, false);
    }

    UI.forceVibrate(this, true);

    int width = getMeasuredWidth();
    int height = getMeasuredHeight() - getPaddingBottom() - getPaddingTop();
    int left = getRealLeft();
    int top = getRealTop() + getPaddingTop();

    if (callback != null) {
      TGReaction reaction = callback.getReactionForPreview(this);
      if (reaction != null) {
        ((BaseActivity) getContext()).openReactionPreview(tdlib, this, reaction, left + width / 2, top + height / 2 + (callback != null ? callback.getStickersListTop() : 0), Math.min(width, height) - Screen.dp(PADDING) * 2, callback.getViewportHeight(), isSuggestion || emojiDisabled);
        return;
      }
    }

    ((BaseActivity) getContext()).openStickerPreview(tdlib, this, sticker, left + width / 2, top + height / 2 + (callback != null ? callback.getStickersListTop() : 0), Math.min(width, height) - Screen.dp(PADDING) * 2, callback.getViewportHeight(), isSuggestion || emojiDisabled);
  }

  private int getRealLeft () {
    return getRealLeft(this);
  }

  private int getRealLeft (StickerSmallView v) {
    int left = getLeft();
    if (callback != null) {
      int newLeft = callback.getStickerViewLeft(v);
      if (newLeft != -1) left = newLeft;
    }
    return left;
  }

  private int getRealTop () {
    return getRealTop(this);
  }

  private int getRealTop (StickerSmallView v) {
    int top = getTop();
    if (callback != null) {
      int newTop = callback.getStickerViewTop(v);
      if (newTop != -1) top = newTop;
    }
    return top;
  }

  public @Nullable TGStickerObj getSticker () {
    return sticker;
  }

  public void closePreviewIfNeeded () {
    if (ignoreNextStickerChanges) {
      ignoreNextStickerChanges = false;
      closePreview(null);
    }
  }

  public boolean onSendSticker (View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
    return callback != null && callback.onStickerClick(this, view, sticker, true, sendOptions);
  }

  public long getStickerOutputChatId () {
    return callback != null ? callback.getStickerOutputChatId() : 0;
  }

  private boolean previewDroppedButStillOpen;

  private void closePreview (@Nullable MotionEvent e) {
    if (e != null && dispatchingMenuEvents) {
      processMenuTouchEvent(e);
      dispatchingMenuEvents = false;
    }
    if (ignoreNextStickerChanges) {
      previewDroppedButStillOpen = true;
      if (getParent() != null) {
        getParent().requestDisallowInterceptTouchEvent(false);
      }
      return;
    }

    cancelDelayedPreview();
    if (!previewOpened) {
      return;
    }

    if (!previewDroppedButStillOpen && getParent() != null) {
      getParent().requestDisallowInterceptTouchEvent(false);
    }
    UI.getContext(getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_STICKER, false);

    previewOpened = false;
    ignoreNextStickerChanges = false;
    setStickerPressed(false);

    if (callback != null) {
      if (currentSticker != null && this.sticker != null && !this.sticker.equals(currentSticker)) {
        callback.setStickerPressed(this, currentSticker, false);
      }

      callback.onStickerPreviewClosed(this, sticker);
    }
    cancelScheduledButtons();

    ((BaseActivity) getContext()).closeStickerPreview();
  }
}
