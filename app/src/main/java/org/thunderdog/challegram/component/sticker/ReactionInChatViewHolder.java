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
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;

public class ReactionInChatViewHolder extends FrameLayoutFix implements FactorAnimator.Target, Destroyable {

  private static final Interpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(3.2f);
  private static final int ON_CLICK_ANIMATION_ID = 100;
  private static final float SCALE_MULTIPLIER = 1.3f;

  private static final long CLICK_LIFESPAN = 230l;
  private static final long LONG_PRESS_DELAY = 1000;

  private OnTouchCallback callback;
  private ReactionInChatView reactionView;

  private final FactorAnimator longPressAnimator;
  private float factor;

  private CancellableRunnable longPress;

  private boolean isPressed;
  private boolean longPressScheduled;
  private boolean longPressReady;
  private boolean isChosen;

  public ReactionInChatViewHolder (@NonNull Context context) {
    super(context);
    reactionView = new ReactionInChatView(context);
    reactionView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40), Screen.dp(40)));
    addView(reactionView);

    this.longPressAnimator = new FactorAnimator(ON_CLICK_ANIMATION_ID, this, OVERSHOOT_INTERPOLATOR, CLICK_LIFESPAN);
  }

  public void setReactionData (Tdlib tdlib, TdApi.Reaction reaction, boolean isChosen) {
    this.isChosen = isChosen;
    reactionView.setSticker(new TGStickerObj(tdlib, reaction.appearAnimation, "", reaction.appearAnimation.type));
  }

  @Override
  public void performDestroy () {
    if (reactionView != null) {
      removeView(reactionView);
      reactionView = null;
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ON_CLICK_ANIMATION_ID) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    boolean saved = factor != 0f;
    if (saved) {
      c.save();
      float scale = SCALE_MULTIPLIER + (1f - SCALE_MULTIPLIER) * (1f - factor);
      int cx = getMeasuredWidth() / 2;
      int cy = getPaddingTop() + (getMeasuredHeight() - getPaddingBottom() - getPaddingBottom()) / 2;
      c.scale(scale, scale, cx, cy);
    }
    super.onDraw(c);
    if (saved) {
      c.restore();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        startTouch();
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        cancelTouch();
        return true;
      }
      case MotionEvent.ACTION_UP: {
        boolean isSingleClicked = longPressScheduled && !longPressReady;
        boolean isLongPressed = !longPressScheduled && longPressReady;
        cancelTouch();
        if (isSingleClicked) {
          onSingleTapped();
        }
        if (isLongPressed) {
          onLongReleased();
        }
        return true;
      }
    }
    return true;
  }

  private void startTouch () {
    openPreviewDelayed();
  }

  private void cancelTouch () {
    setStickerPressed(false);
    cancelDelayedPreview();
    longPressReady = false;
  }

  private void setStickerPressed (boolean isPressed) {
    if (this.isPressed != isPressed) {
      this.isPressed = isPressed;
      longPressAnimator.animateTo(isPressed ? 1f : 0f);
    }
  }

  private void openPreviewDelayed () {
    cancelDelayedPreview();
    longPress = new CancellableRunnable() {
      @Override
      public void act () {
        onLongPressed();
        longPressScheduled = false;
      }
    };
    longPressScheduled = true;
    postDelayed(longPress, LONG_PRESS_DELAY);
  }

  private void cancelDelayedPreview () {
    if (longPress != null) {
      longPress.cancel();
      longPress = null;
    }
    longPressScheduled = false;
  }

  private void onSingleTapped () {
    if (callback != null) {
      callback.onSingleTap(isChosen);
    }
  }

  private void onLongPressed () {
    longPressReady = true;
    setStickerPressed(true);

    UI.forceVibrate(this, true);
  }

  private void onLongReleased () {
    if (callback != null) {
      callback.onLongRelease(isChosen);
    }
  }

  public void setCallback (OnTouchCallback callback) {
    this.callback = callback;
  }

  public ReactionInChatView getReactionView () {
    return reactionView;
  }

  public interface OnTouchCallback {
    void onSingleTap (boolean isUndo);

    void onLongRelease (boolean isUndo);
  }
}
