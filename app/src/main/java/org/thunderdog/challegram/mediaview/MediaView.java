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
 * File created on 01/09/2015 at 01:50
 */
package org.thunderdog.challegram.mediaview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class MediaView extends FrameLayoutFix {
  public static final int DIRECTION_AUTO = 0;
  public static final int DIRECTION_BACKWARD = 1;
  public static final int DIRECTION_FORWARD = 2;
  public static final int DIRECTION_RESET = 3;

  private static final boolean USE_GRADIENTS = false;
  private static final float HEADER_ALPHA = USE_GRADIENTS ? 0xff : 0x99;

  public interface ClickListener {
    void onClick (MediaView mediaView, float x, float y);
  }

  // private final Paint backgroundPaint;
  // private final Paint headerPaint;

  private MediaStack stack;
  private MediaCellView baseCell;
  private @Nullable MediaCellView previewCell;

  private @Nullable MediaGestureDetector detector;

  // View setup

  public boolean isOpen () {
    return boundController != null && boundController.isFullyShown();
  }

  public MediaView (Context context) {
    super(context);

    // Paints

    /*backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    backgroundPaint.setColor(0xff000000);*/

    // Cells
  }

  boolean isKeyboardVisible () {
    return boundController != null && boundController.getKeyboardState();
  }

  public void prepare (boolean needPreview) {
    baseCell = new MediaCellView(getContext());
    if (needPreview) {
      previewCell = new MediaCellView(getContext());
      previewCell.setFactor(-1f);
      addView(previewCell);
    }

    addView(baseCell);

    // Touch components

    if (needPreview) {
      detector = new MediaGestureDetector(getContext());
      detector.setBoundView(this);
    }

    setWillNotDraw(false);
  }

  public MediaCellView findCellForItem (MediaItem item) {
    if (baseCell != null && baseCell.getMedia() == item) {
      return baseCell;
    }
    if (previewCell != null && previewCell.getMedia() == item) {
      return previewCell;
    }
    return null;
  }

  public MediaCellView getBaseCell () {
    return baseCell;
  }

  private MediaViewController boundController;

  public void setBoundController (MediaViewController clickController) {
    this.boundController = clickController;
  }

  public void dispatchOnMediaZoom () {
    if (boundController != null) {
      boundController.onMediaZoomStart();
    }
  }

  public void onMediaClick (float x, float y) {
    if (boundController != null) {
      boundController.onMediaItemClick(this, x, y);
    }
  }

  public void setCellCallback (MediaCellView.Callback callback) {
    baseCell.setCallback(callback);
    if (previewCell != null) {
      previewCell.setCallback(callback);
    }
  }

  public void pauseIfPlaying () {
    baseCell.pauseIfPlaying();
    if (previewCell != null) {
      previewCell.pauseIfPlaying();
    }
  }

  public void autoplayIfNeeded (boolean isSwitch) {
    baseCell.autoplayIfNeeded(isSwitch);
  }

  public void setSeekProgress (float progress) {
    baseCell.setSeekProgress(progress);
    if (previewCell != null) {
      previewCell.setSeekProgress(progress);
    }
  }

  public void resumeIfNeeded (float progress) {
    baseCell.resumeIfNeeded(progress);
    if (previewCell != null) {
      previewCell.resumeIfNeeded(progress);
    }
  }

  private boolean disallowMove;

  public void setDisallowMove (boolean disallowMove) {
    this.disallowMove = disallowMove;
  }

  private boolean disableTouch;

  public void setDisableTouch (boolean disableTouch) {
    this.disableTouch = disableTouch;
  }

  private ClickListener butStillNeedClick;

  public void setButStillNeedClick (ClickListener butStillNeedClick) {
    if (this.butStillNeedClick != butStillNeedClick){
      this.butStillNeedClick = butStillNeedClick;
      this.catchingClick = false;
    }
  }

  public boolean isTouchEnabled (boolean isTouchDown) {
    return !disableTouch && (boundController == null || boundController.allowMediaViewGestures(isTouchDown));
  }

  public void initWithStack (MediaStack stack) {
    this.stack = stack;

    baseCell.setFactor(0f);
    baseCell.setMedia(stack.getCurrent());
  }

  public boolean canZoom (MediaCellView cellView) {
    return baseCell == cellView && factor == 0f;
  }

  public boolean canMoveZoomedView (MediaCellView celView) {
    return boundController == null || boundController.getMode() != MediaViewController.MODE_GALLERY || boundController.allowMovingZoomedView();
  }

  private MediaCellView revealCell;
  private MediaViewThumbLocation thumb;

  public void setTarget (MediaViewThumbLocation thumb, float factor) {
    this.thumb = thumb;
    this.revealCell = baseCell;
    this.revealCell.setTargetLocation(thumb);
    this.revealCell.setRevealFactor(factor);
    this.revealCell.setDisappearing(factor == 1f);
  }

  public void setPendingOpenAnimator (FactorAnimator animator) {
    revealCell.setTargetAnimator(animator);
  }

  public void setRevealFactor (float revealFactor) {
    revealCell.setRevealFactor(revealFactor);
  }

  public boolean isBaseVisible () {
    return baseCell.hasVisibleContent();
  }

  public void setDisableAnimations (boolean disable) {
    baseCell.setDisableAnimations(disable);
    if (previewCell != null) {
      previewCell.setDisableAnimations(disable);
    }
  }

  // Current values

  private int currentWidth, currentHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec); // UI.get().getWindow().getDecorView().getMeasuredHeight();

    boolean sameWidth = currentWidth == width;
    boolean dimensionsChanged = currentHeight != height || !sameWidth;

    if (dimensionsChanged) {
      currentWidth = width;
      currentHeight = height;
      buildLayout(sameWidth);
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  public void setOffsetHorizontal (int offsetHorizontal) {
    if (this.offsetHorizontal != offsetHorizontal) {
      setOffsets(offsetHorizontal, offsetTop, offsetBottom);
    }
  }

  public void setOffsets (int offsetHorizontal, int offsetTop, int offsetBottom) {
    if (this.offsetHorizontal != offsetHorizontal || this.offsetTop != offsetTop || this.offsetBottom != offsetBottom) {
      this.offsetTop = offsetTop;
      this.offsetBottom = offsetBottom;
      this.offsetHorizontal = offsetHorizontal;

      if (layoutBuilt) {
        baseCell.setOffsets(offsetHorizontal, offsetTop, offsetBottom);
        if (previewCell != null) {
          previewCell.setOffsets(offsetHorizontal, offsetTop, offsetBottom);
        }
      }
    }
  }

  public void setOffsetBottom (int bottom) {
    setOffsets(this.offsetHorizontal, this.offsetTop, bottom);
  }

  public int getOffsetHorizontal () {
    return offsetHorizontal;
  }

  public int getOffsetBottom () {
    return offsetBottom;
  }

  public int getOffsetTop () {
    return offsetTop;
  }

  public boolean isZoomed () {
    return baseCell.isZoomed();
  }

  public void normalizeZoom () {
    baseCell.normalizeZoom();
  }

  private int offsetTop, offsetBottom, offsetHorizontal;
  private boolean layoutBuilt;

  private void buildLayout (boolean animated) {
    layoutBuilt = true;
    baseCell.getDetector().normalizeZoom(animated);
    if (previewCell != null) {
      previewCell.getDetector().reset();
    }
    dropPendingTouches();
    layoutCells();
  }

  public int getBottomAdd () {
    if (boundController != null && boundController.getKeyboardState() && boundController.getArgumentsStrict().mode == MediaViewController.MODE_GALLERY) {
      return boundController.getBottomInnerMargin();
    }
    return 0;
  }

  public void layoutCells () {
    int currentHeight = this.currentHeight + getBottomAdd();
    baseCell.layoutCell(offsetHorizontal, offsetTop, offsetBottom, currentWidth, currentHeight);
    if (previewCell != null) {
      previewCell.layoutCell(offsetHorizontal, offsetTop, offsetBottom, currentWidth, currentHeight);
    }
  }

  // Translation

  private float factor;

  public void switchPhoto (float newFactor, float oldFactor) {
    if (previewCell != null) {
      if (newFactor > 0f) {
        if (oldFactor <= 0f) {
          previewCell.setMedia(stack.getNext());
        }
      } else if (newFactor < 0f) {
        if (oldFactor >= 0f) {
          previewCell.setMedia(stack.getPrevious());
        }
      }
    }
  }

  public void replaceMedia (MediaItem oldItem, MediaItem newItem) {
    if (previewCell != null && previewCell.getMedia() == oldItem) {
      if (newItem != null) {
        previewCell.setMedia(newItem);
      } else {
        dropPreview(DIRECTION_RESET, 0f);
      }
    }
    if (baseCell.getMedia() == oldItem) {
      baseCell.setMedia(newItem);
    }
  }

  public void translate (float factor) { // 0f normal, -1f - left preview is showing, 1f - right preview is showing
    if (this.factor != factor) {
      switchPhoto(factor, this.factor);
      setFactorImpl(factor);
      translateCells();
      invalidate();
    }
  }

  private void translateCells () {
    if (factor == 0f || previewCell == null) {
      baseCell.setFactor(0f);
      if (previewCell != null) {
        previewCell.setFactor(-1f);
      }
      if (indexOfChild(baseCell) != 1) {
        bringChildToFront(baseCell);
      }
    } else {
      if (factor < 0f) { // sliding to left image
        baseCell.setFactor(factor);
        previewCell.setFactor(1f + factor);
        if (indexOfChild(previewCell) != 1) {
          bringChildToFront(previewCell);
        }
      } else { // sliding to right image
        baseCell.setFactor(factor);
        previewCell.setFactor(-1f + factor);
        if (indexOfChild(baseCell) != 1) {
          bringChildToFront(baseCell);
        }
      }
    }
  }

  public interface FactorChangeListener {
    void onFactorChanged (MediaView view, float factor);
  }

  private @Nullable FactorChangeListener factorChangeListener;

  public void setFactorChangeListener (@Nullable FactorChangeListener factorChangeListener) {
    this.factorChangeListener = factorChangeListener;
  }

  private boolean isAnimating;

  private void setFactorImpl (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (factorChangeListener != null) {
        factorChangeListener.onFactorChanged(this, factor);
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  public boolean isStill () {
    return factor == 0f;
  }

  private ValueAnimator animator;

  public void dropPreview (int direction, float velocity) { // velocity from 0f to 1f
    float nextFactor;

    switch (direction) {
      case DIRECTION_AUTO: {
        if (factor == 0f) {
          return;
        }
        nextFactor = factor <= -.5f && hasPrevious() ? -1f : factor >= .5f && hasNext() ? 1f : 0f;
        break;
      }
      case DIRECTION_FORWARD: {
        if (factor == 1f) {
          if (!applyPreview()) {
            dropPreview(DIRECTION_AUTO, velocity);
          }
          return;
        }
        nextFactor = hasNext() ? 1f : 0f;
        break;
      }
      case DIRECTION_BACKWARD: {
        if (factor == -1f && applyPreview()) {
          dropPreview(DIRECTION_AUTO, velocity);
          return;
        }
        nextFactor = hasPrevious() ? -1f : 0f;
        break;
      }
      default: {
        nextFactor = 0f;
        break;
      }
    }

    final float startFactor = getFactor();
    final float factorDiff = nextFactor - startFactor;
    animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> translate(startFactor + factorDiff * AnimatorUtils.getFraction(animation)));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (factor != 0f) {
          applyPreview();
        }
        isAnimating = false;
        if (factor == 0f && previewCell != null) {
          previewCell.setMedia(null);
        }
      }
    });
    animator.setDuration(300);

    isAnimating = true;
    animator.start();
  }

  public void onMediaActivityPause () {
    baseCell.onCellActivityPause();
    if (previewCell != null) {
      previewCell.onCellActivityPause();
    }
  }

  public void onMediaActivityResume () {
    baseCell.onCellActivityResume();
    if (previewCell != null) {
      previewCell.onCellActivityResume();
    }
  }

  public void destroy () {
    baseCell.destroy();
    if (previewCell != null) {
      previewCell.destroy();
    }
  }

  private boolean applyPreview () {
    if (factor <= -.5f) {
      if (!stack.hasPrevious()) {
        return false;
      }
      stack.applyPrevious();
      setFactorImpl(1f + factor);
    } else if (factor >= .5f) {
      if (!stack.hasNext()) {
        return false;
      }
      stack.applyNext();
      setFactorImpl(-1f + factor);
    } else {
      return false;
    }

    MediaCellView cell = baseCell;
    baseCell = previewCell;
    previewCell = cell;

    translateCells();
    invalidate();

    return true;
  }

  // Drawing

  /*private float headerFactor;

  public void setHeaderFactor (float factor) {
    if (this.headerFactor != factor) {
      this.headerFactor = factor;
      headerPaint.setAlpha((int) (HEADER_ALPHA * factor));
      invalidate(0, 0, currentWidth, Size.HEADER_PORTRAIT_SIZE);
    }
  }*/

  /*@Override
  public void draw (Canvas c) {


    *//*if (commonFactor > 0f) {
      if (thumb == null || commonFactor >= 1f || true) {

      } else {
        int fromX = thumb.centerX();
        int fromY = thumb.centerY();

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int targetX = width / 2;
        int targetY = height / 2;

        float startRadius = Math.min(thumb.width(), thumb.height()) / 2;
        float radius = (float) Math.sqrt(width * width + height * height) * .5f;

        float centerX = fromX + (float) (targetX - fromX) * alpha;
        float centerY = fromY + (float) (targetY - fromY) * alpha;
        float targetRadius = startRadius + (radius - startRadius) * alpha;

        c.drawCircle(centerX, centerY, targetRadius, Paints.fillingPaint(color));
      }
    }*//*

    super.draw(c);

    *//*if (true) {
      return;
    }

    float factor;

    if ((this.factor > 0f && !stack.hasNext()) || (this.factor < 0f && !stack.hasPrevious())) {
      factor = .25f * this.factor;
    } else {
      factor = this.factor;
    }

    if (factor == 0f) {
      baseCell.draw(c);
    } else if (factor > 0f) {
      previewCell.draw(c);
      baseCell.draw(c);
    } else {
      baseCell.draw(c);
      previewCell.draw(c);
    }*//*

    *//*if (headerFactor != 0f) {
      c.drawRect(0, 0, currentWidth, HeaderView.getSize(true), headerPaint);
    }*//*
  }*/

  // Touching

  private boolean dropTouches;
  private float startX;
  private boolean disallowIntercept;

  private float downStartX, downStartY;
  private boolean listenMove, isMoving;
  private boolean ignoreDisallowInterceptTouchEvent;

  public void setIgnoreDisallowInterceptTouchEvent (boolean ignoreDisallowInterceptTouchEvent) {
    this.ignoreDisallowInterceptTouchEvent = ignoreDisallowInterceptTouchEvent;
  }

  @Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    this.disallowIntercept = disallowIntercept;
    if (disallowIntercept && !ignoreDisallowInterceptTouchEvent) {
      drop();
    }
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }

  private void updateValues (float x) {
    if (factor == 0f) {
      startX = x;
      isMoving = false;
    } else {
      startX = x + (float) currentWidth * factor * (Lang.rtl() ? -1f : 1f);
      isMoving = true;
    }
  }

  public boolean isMovingItem () {
    return isMoving;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if ((disallowIntercept && (e.getAction() != MotionEvent.ACTION_DOWN /*|| !ignoreDisallowInterceptTouchEvent*/)) || disallowMove || disableTouch || detector == null) {
      // Logger.v("no intercept %s %b", MotionEvent.actionToString(e.getAction()), isMoving);
      return false;
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        downStartX = e.getX();
        downStartY = e.getY();

        stopAnimator();
        updateValues(downStartX);

        listenMove = !isMoving && e.getPointerCount() == 1;

        if (!isMoving) {
          return detector.onTouchEvent(e);
        }

        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listenMove && Math.abs(e.getX() - downStartX) > Screen.getTouchSlopBig() && Math.abs(e.getY() - downStartY) < Screen.getTouchSlopBig()) {
          listenMove = false;
          startX = e.getX();
          isMoving = true;
        }
        break;
      }
    }

    // Logger.v("intercept %s %b", MotionEvent.actionToString(e.getAction()), isMoving);

    return isMoving;
  }

  private void stopAnimator () {
    if (isAnimating) {
      isAnimating = false;
      animator.cancel();
    }
  }

  private void drop () {
    if (isMoving) {
      dropPreview(DIRECTION_AUTO, 0f);
    }
    isMoving = listenMove = false;
  }

  private float clickStartX, clickStartY;
  private boolean catchingClick;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (detector == null) {
      return false;
    }

    if (disableTouch) {
      if (butStillNeedClick != null) {
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            clickStartX = e.getX();
            clickStartY = e.getY();
            catchingClick = true;
            break;
          }
          case MotionEvent.ACTION_CANCEL: {
            catchingClick = false;
            break;
          }
          case MotionEvent.ACTION_MOVE: {
            if (catchingClick && Math.max(Math.abs(e.getX() - clickStartX), Math.abs(e.getY() - clickStartY)) > Screen.getTouchSlop()) {
              catchingClick = false;
            }
            break;
          }
          case MotionEvent.ACTION_UP: {
            if (catchingClick) {
              catchingClick = false;
              playSoundEffect(SoundEffectConstants.CLICK);
              butStillNeedClick.onClick(this, e.getX(), e.getY());
            }
            break;
          }
        }
        return catchingClick;
      }

      drop();
      return false;
    }

    if (e.getPointerCount() > 1 || disallowMove) {
      drop();
      return true;
    }

    boolean res = detector.onTouchEvent(e);

    float x = e.getX();
    // float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        if (isMoving) {
          float factor = (x - startX) / (float) currentWidth * (Lang.rtl() ? 1f : -1f);

          if ((factor > 0f && !stack.hasNext()) || (factor < 0f && !stack.hasPrevious())) {
            factor = factor * .5f;
          }

          translate(factor);
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (res) {
          isMoving = false;
          break;
        }

        if (isMoving) {
          dropPreview(DIRECTION_AUTO, 0f);
          isMoving = false;
        }

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isMoving) {
          dropPreview(DIRECTION_AUTO, 0f);
          isMoving = false;
        }
        break;
      }
    }

    return true;
  }

  private boolean doubleTapZoomDisabled;

  public void setDisableDoubleTapZoom (boolean disableDoubleTapZoom) {
    this.doubleTapZoomDisabled = disableDoubleTapZoom;
  }

  public boolean canDoubleTapZoom () {
    return !doubleTapZoomDisabled;
  }

  private void dropPendingTouches () {
    dropTouches = true;
  }

  // event listeners

  public int getActualImageHeight () {
    return getMeasuredHeight() - offsetTop - offsetBottom;
  }

  public int getActualImageWidth () {
    return getMeasuredWidth() - offsetHorizontal - offsetHorizontal;
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    baseCell.attach();
    if (previewCell != null) {
      previewCell.attach();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    baseCell.detach();
    if (previewCell != null) {
      previewCell.detach();
    }
  }

  public Receiver getBaseReceiver () {
    return baseCell.getReceiver();
  }

  // Stack utils

  public boolean hasNext () {
    return !inSingleMode() && stack.hasNext();
  }

  public boolean hasPrevious () {
    return !inSingleMode() && stack.hasPrevious();
  }

  public boolean inSingleMode () {
    return disallowMove;
  }
}
