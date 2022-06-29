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
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;

public class StickerTinyView extends View implements FactorAnimator.Target, Destroyable {

  private static final float MIN_SCALE = 1.3f;
  private static final long CLICK_LIFESPAN = 230l;
  private static final long LONG_PRESS_DELAY = 1000;

  private static final int ON_CLICK_ANIMATION_ID = 100;
  private static final int TARGET_ANIMATION_ID = 101;

  public static final float PADDING = 8f;
  private static final Interpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(3.2f);
  private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;
  private final FactorAnimator longPressAnimator;
  private final FactorAnimator translateAnimator;
  private @Nullable TGStickerObj sticker;
  private Path contour;
  private float factor;

  private boolean isAnimation;

  private CancellableRunnable longPress;

  private boolean isPressed;
  private boolean longPressScheduled;
  private boolean longPressReady;

  private int targetX = -1;
  private int targetY = -1;
  private int fromLeftMargin = -1;
  private int fromTopMargin = -1;

  private OnTouchCallback callback;

  public StickerTinyView (Context context) {
    super(context);
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
    this.longPressAnimator = new FactorAnimator(ON_CLICK_ANIMATION_ID, this, OVERSHOOT_INTERPOLATOR, CLICK_LIFESPAN);
    this.translateAnimator = new FactorAnimator(TARGET_ANIMATION_ID, this, LINEAR_INTERPOLATOR, CLICK_LIFESPAN);
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

  private void resetStickerState () {
    longPressAnimator.forceFactor(0f, true);
    factor = 0f;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ON_CLICK_ANIMATION_ID) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    } else {
      ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) getLayoutParams();
      float translateX = targetX * factor;
      float translateY = targetY * factor;

      android.util.Log.d("AKBOLAT", "translate X " + translateX + " Y " + translateY);
      android.util.Log.d("AKBOLAT", "margin L " + layoutParams.leftMargin + " T " + layoutParams.topMargin);

      layoutParams.leftMargin = (int) (fromLeftMargin + translateX);
      layoutParams.topMargin = (int) (fromTopMargin + translateY);
      setLayoutParams(layoutParams);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == TARGET_ANIMATION_ID) {
      android.util.Log.d("AKBOLAT", "onFinished");
//      ((ViewGroup) getParent()).removeView(this);
    }
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
    boolean saved = factor != 0f;
    if (saved) {
      c.save();
      float scale = MIN_SCALE + (1f - MIN_SCALE) * (1f - factor);
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
      callback.onSingleTap();
    }
  }

  private void onLongPressed () {
    longPressReady = true;
    setStickerPressed(true);

    UI.forceVibrate(this, true);
  }

  private void onLongReleased () {
    if (callback != null) {
      callback.onLongRelease();
    }
  }

  public void setCallback (OnTouchCallback callback) {
    this.callback = callback;
  }

  public void setTargetXY (View view, int targetX, int targetY, int yOffset) {
    if (view == null) return;

    int[] position = new int[2];
    view.getLocationOnScreen(position);

    this.targetX = position[0] + targetX - Screen.dp(10f);
    this.targetY = position[1] + targetY - yOffset - Screen.dp(10f);
  }

  public void playAnimation () {
    if (targetX == -1 || targetY == -1) return;

    fromLeftMargin = ((ViewGroup.MarginLayoutParams) getLayoutParams()).leftMargin;
    fromTopMargin = ((ViewGroup.MarginLayoutParams) getLayoutParams()).topMargin;

    targetX -= fromLeftMargin;
    targetY -= fromTopMargin;

    translateAnimator.animateTo(1f);
  }

  public interface OnTouchCallback {
    void onSingleTap ();

    void onLongRelease ();
  }
}
