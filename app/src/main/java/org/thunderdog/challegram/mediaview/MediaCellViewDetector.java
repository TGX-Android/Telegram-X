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
 * File created on 07/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.CancellableRunnable;

public class MediaCellViewDetector implements FactorAnimator.Target {
  public interface Callback {
    boolean onZoomStart ();
    void onZoomEnd ();
    void onZoom ();
    void onClick (float x, float y);
    boolean canDoubleTap(float x, float y);

    boolean canMoveZoomedView ();

    void getZoomDisplayRect (Rect rect);
    void getZoomContentRect (Rect rect);
  }

  private final View target;

  private final Rect displayRect = new Rect();
  private final Rect mediaRect = new Rect();
  private final RectF viewRect = new RectF();
  private final RectF centerRect = new RectF();

  private @NonNull Callback callback;

  public MediaCellViewDetector (Context context, View view, @NonNull Callback callback) {
    this.callback = callback;
    this.target = view;
  }

  private boolean listenClick;
  private float downStartX, downStartY;

  public boolean onTouchEvent (MotionEvent e) {
    int action = e.getActionMasked();

    int pointerCount = e.getPointerCount();
    setState(e, pointerCount == 2 ? STATE_ZOOMING : pointerCount == 1 && zoom > 1f && (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL && callback.canMoveZoomedView()) ? STATE_MOVING : STATE_NONE);

    if (pointerCount != 1 && listenClick) {
      listenClick = false;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN: {
        listenClick = pointerCount == 1;
        downStartX = e.getX();
        downStartY = e.getY();
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        listenClick = false;
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (listenClick) {
          onClick(e.getX(), e.getY());
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listenClick && Math.max(Math.abs(e.getX() - downStartX), Math.abs(e.getY() - downStartY)) > Screen.getTouchSlopBig()) {
          listenClick = false;
          if (state == STATE_MOVING) {
            onMoveStart(e);
          }
        }
        switch (state) {
          case STATE_MOVING: {
            if (!listenClick) {
              onMoveEvent(e);
            }
            break;
          }
          case STATE_ZOOMING: {
            onZoomEvent(e);
            break;
          }
        }
        break;
      }
    }

    return true;
  }

  private long lastClickTime;
  private float lastClickX, lastClickY;
  private static final long DOUBLE_TAP_DELAY = ViewConfiguration.getDoubleTapTimeout();

  private CancellableRunnable clickRunnable;

  private void cancelClick () {
    if (clickRunnable != null) {
      clickRunnable.cancel();
      clickRunnable = null;
    }
  }

  private void onClick (float x, float y) {
    cancelClick();
    if (!callback.canDoubleTap(x, y)) {
      callback.onClick(x, y);
      return;
    }
    long now = SystemClock.uptimeMillis();
    if (now - lastClickTime <= DOUBLE_TAP_DELAY) {
      performDoubleTap(x, y);
      return;
    }

    lastClickX = x;
    lastClickY = y;
    lastClickTime = now;
    scheduleClick();
  }

  private void performDoubleTap (float x, float y) {
    if (zoom > 1f) {
      animatePosition(1f, pivotX, pivotY, 0f, 0f, true);
      ViewUtils.onClick(target);
    } else if (zoom == 1f) {
      zoomIn(x, y);
      ViewUtils.onClick(target);
    }
  }

  private static final float MAX_ZOOM = 3f;

  private void scheduleClick () {
    UI.post(clickRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        lastClickTime = 0;
        callback.onClick(lastClickX, lastClickY);
      }
    }, DOUBLE_TAP_DELAY);
  }

  public void reset () {
    forcePosition(1f, 0f, 0f);
    setState(null, STATE_NONE);
  }

  public void forcePosition (float zoom, float positionX, float positionY) {
    if (positionAnimator != null) {
      positionAnimator.cancel();
    }
    if (this.zoom != zoom || this.positionX != positionX || this.positionY != positionY) {
      this.zoom = zoom;
      this.positionX = positionX;
      this.positionY = positionY;
      callback.onZoom();
    }
  }

  private void endZoom () {
    if (state == STATE_NONE) {
      normalizePosition();
      callback.onZoomEnd();
    }
  }

  private static final int ANIMATOR_POSITION = 1;
  private FactorAnimator positionAnimator;

  private final Matrix matrix = new Matrix();

  public Rect getViewRect () {
    Rect rect = Paints.getRect();
    rect.set((int) viewRect.left, (int) viewRect.top, (int) viewRect.right, (int) viewRect.bottom);
    return rect;
  }

  private float fromZoom, fromPivotX, fromPivotY, fromPositionX, fromPositionY;
  private float toZoom, toPivotX, toPivotY, toPositionX, toPositionY;
  private boolean animatingPosition;

  private void animatePosition (float toZoom, float toPivotX, float toPivotY, float toPositionX, float toPositionY, boolean animate) {
    this.fromZoom = this.zoom;
    this.fromPivotX = this.pivotX;
    this.fromPivotY = this.pivotY;
    this.fromPositionX = this.positionX;
    this.fromPositionY = this.positionY;
    this.toZoom = toZoom;
    this.toPivotX = toPivotX;
    this.toPivotY = toPivotY;
    this.toPositionX = toPositionX;
    this.toPositionY = toPositionY;

    if (positionAnimator != null) {
      positionAnimator.forceFactor(0f);
    }

    final boolean changed = this.zoom != toZoom || this.pivotX != toPivotX || this.pivotY != toPivotY || this.positionX != toPositionX || this.positionY != toPositionY;
    animatingPosition = changed;

    if (animate && changed) {
      if (positionAnimator == null) {
        positionAnimator = new FactorAnimator(ANIMATOR_POSITION, this, AnimatorUtils.QUADRATIC_EASE_IN_OUT_INTERPOLATOR, MediaCellView.ZOOM_DURATION);
      }
      positionAnimator.animateTo(1f);
    }
  }

  public void preparePositionReset () {
    animatePosition(1f, .5f, .5f, 0f, 0f, false);
  }

  public boolean setPositionFactor (float factor) {
    if (!animatingPosition) {
      return false;
    }
    float zoom = fromZoom + (toZoom - fromZoom) * factor;
    float pivotX = fromPivotX + (toPivotX - fromPivotX) * factor;
    float pivotY = fromPivotY + (toPivotY - fromPivotY) * factor;
    float positionX = fromPositionX + (toPositionX - fromPositionX) * factor;
    float positionY = fromPositionY + (toPositionY - fromPositionY) * factor;
    if (this.zoom != zoom || this.pivotX != pivotX || this.pivotY != pivotY || this.positionX != positionX || this.positionY != positionY) {
      this.zoom = zoom;
      this.pivotX = pivotX;
      this.pivotY = pivotY;
      this.positionX = positionX;
      this.positionY = positionY;
      callback.onZoom();
      return true;
    }
    return false;
  }

  private void normalizePosition () {
    float zoom = Math.max(1f, Math.min(MAX_ZOOM, this.zoom));

    callback.getZoomDisplayRect(displayRect);

    final int displayWidth = displayRect.width();
    final int displayHeight = displayRect.height();

    /*final int offsetHorizontal = callback.getOffsetHorizontal();
    final int offsetTop = callback.getOffsetTop();
    final int offsetBottom = callback.getOffsetBottom();*/

    viewRect.set(mediaRect);
    matrix.reset();
    matrix.preScale(zoom, zoom, pivotX * displayWidth, pivotY * displayHeight);
    matrix.postTranslate(positionX * displayWidth, positionY * displayHeight);
    matrix.mapRect(viewRect);
    matrix.reset();

    centerRect.set(mediaRect);
    matrix.preScale(zoom, zoom, .5f * displayWidth, .5f * displayHeight);
    matrix.mapRect(centerRect);

    float left = viewRect.left / (float) displayWidth;
    float right = viewRect.right / (float) displayWidth;
    float top = viewRect.top / (float) displayHeight;
    float bottom = viewRect.bottom / (float) displayHeight;

    float maxLeft = Math.max(0, centerRect.left / (float) displayWidth);
    float maxTop = Math.max(0, centerRect.top / (float) displayHeight);
    float minBottom = Math.min(1f, centerRect.bottom / (float) displayHeight);
    float minRight = Math.min(1f, centerRect.right / (float) displayWidth);

    float offsetX = 0, offsetY = 0;

    if (left > maxLeft) {
      offsetX = maxLeft - left;
    } else if (right < minRight) {
      offsetX = minRight - right;
    }
    if (top > maxTop) {
      offsetY = maxTop - top;
    } else if (bottom < minBottom) {
      offsetY = minBottom - bottom;
    }

    // Log.i("left: %f, top: %f, right: %f, bottom: %f\nleft: %f, top: %f, right: %f, bottom: %f\noffsetX: %f, offsetY: %f", left, top, right, bottom, maxLeft, maxTop, minRight, minBottom, offsetX, offsetY);

    animatePosition(zoom, pivotX, pivotY, positionX + offsetX, positionY + offsetY, true);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_POSITION: {
        setPositionFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  private static float distance (float x1, float y1, float x2, float y2) {
    float x = x1 - x2;
    float y = y1 - y2;
    return (float) Math.sqrt(x * x + y * y);
  }

  private float zoom = 1f;
  private int state;

  private static final int STATE_NONE = 0;
  private static final int STATE_MOVING = 1;
  private static final int STATE_ZOOMING = 2;

  private float zoomStart;
  private float positionX, positionY;
  private float pinchStartDistance;
  private float pivotX, pivotY;
  private float lastPinchCenterX, lastPinchCenterY;

  private void setState (@Nullable MotionEvent e, int state) {
    state = e != null ? state : STATE_NONE;
    if (this.state != state) {
      if (state == STATE_NONE) {
        this.state = STATE_NONE;
        endZoom();
      } else if (this.state != STATE_NONE || callback.onZoomStart()) {
        this.state = state;
        if (state == STATE_ZOOMING) {
          onZoomStart(e);
        } else if (state == STATE_MOVING && !listenClick) {
          onMoveStart(e);
        }
      }
    }
  }

  private void onZoomStart (MotionEvent e) {
    if (this.positionAnimator != null) {
      this.positionAnimator.cancel();
    }

    // PRE-CALCULATIONS
    float pinchX1 = e.getX(0);
    float pinchX2 = e.getX(1);

    float pinchY1 = e.getY(0);
    float pinchY2 = e.getY(1);

    float pinchCenterX = (pinchX1 + pinchX2) / 2;
    float pinchCenterY = (pinchY1 + pinchY2) / 2;

    float distance = distance(pinchX1, pinchY1, pinchX2, pinchY2);

    // Viewport

    callback.getZoomDisplayRect(displayRect);
    final int displayWidth = displayRect.width();
    final int displayHeight = displayRect.height();

    callback.getZoomContentRect(mediaRect);

    if (zoom <= 1f) {
      pivotX = Math.max(mediaRect.left, Math.min(mediaRect.right, pinchCenterX)) / (float) displayWidth;
      pivotY = Math.max(mediaRect.top, Math.min(mediaRect.bottom, pinchCenterY)) / (float) displayHeight;
    } else {
      applyPositionToPivot();
    }

    this.zoomStart = zoom;
    this.pinchStartDistance = distance;

    this.lastPinchCenterX = pinchCenterX;
    this.lastPinchCenterY = pinchCenterY;
  }

  private void applyPositionToPivot () {
    if (positionX == 0f && positionY == 0f) {
      return;
    }

    /* TODO

    this.pivotX -= positionX / 2;
    this.pivotY -= positionY / 2;

    this.positionX = 0;
    this.positionY = 0;*/
  }

  private void onZoomEvent (MotionEvent e) {
    // PRE-CALCULATIONS
    float pinchX1 = e.getX(0);
    float pinchX2 = e.getX(1);

    float pinchY1 = e.getY(0);
    float pinchY2 = e.getY(1);

    float pinchCenterX = (pinchX1 + pinchX2) / 2;
    float pinchCenterY = (pinchY1 + pinchY2) / 2;

    float distance = distance(pinchX1, pinchY1, pinchX2, pinchY2);

    // PINCHING

    offsetPositionBy(pinchCenterX - lastPinchCenterX, pinchCenterY - lastPinchCenterY);

    lastPinchCenterX = pinchCenterX;
    lastPinchCenterY = pinchCenterY;

    float zoom = pinchStartDistance == 0 || zoomStart == 0 ? 1f : Math.max(.3f, distance / (pinchStartDistance / zoomStart));

    this.zoom = zoom;

    callback.onZoom();
  }

  private void offsetPositionBy (float offsetX, float offsetY) {
    callback.getZoomDisplayRect(displayRect);
    final int displayWidth = displayRect.width();
    final int displayHeight = displayRect.height();

    callback.getZoomContentRect(mediaRect);
    final int mediaWidth = mediaRect.width();
    final int mediaHeight = mediaRect.height();

    int scaledWidth = (int) ((float) mediaWidth * zoom);
    int scaledHeight = (int) ((float) mediaHeight * zoom);

    positionX += offsetX / (float) displayWidth;
    positionY += offsetY / (float) displayHeight;

    /*if (scaledWidth <= displayWidth) {
      positionX = 0;
    }
    if (scaledHeight <= displayHeight) {
      positionY = 0;
    }*/
  }

  private void setMaxPivotForXY (float x, float y) {
    float zoom = MAX_ZOOM;

    callback.getZoomDisplayRect(displayRect);
    final int displayWidth = displayRect.width();
    final int displayHeight = displayRect.height();

    callback.getZoomContentRect(mediaRect);

    x = Math.max(mediaRect.left, Math.min(mediaRect.right, x));
    y = Math.max(mediaRect.top, Math.min(mediaRect.bottom, y));

    float pivotX = x / (float) displayWidth;
    float pivotY = y / (float) displayHeight;

    viewRect.set(mediaRect);
    matrix.reset();
    matrix.preScale(zoom, zoom, pivotX * displayWidth, pivotY * displayHeight);
    matrix.mapRect(viewRect);
    matrix.reset();

    centerRect.set(mediaRect);
    matrix.preScale(zoom, zoom, .5f * displayWidth, .5f * displayHeight);
    matrix.mapRect(centerRect);

    float maxLeft = Math.max(0, centerRect.left);
    float maxTop = Math.max(0, centerRect.top);
    float minBottom = Math.min(displayHeight, centerRect.bottom);
    float minRight = Math.min(displayWidth, centerRect.right);

    float offsetX = 0f, offsetY = 0f;
    if (centerRect.width() <= displayWidth) {
      pivotX = .5f;
    } else if (viewRect.left > maxLeft) {
      offsetX = maxLeft - viewRect.left;
    } else if (viewRect.right < minRight) {
      offsetX = minRight - viewRect.right;
    }
    if (centerRect.height() <= displayHeight) {
      pivotY = .5f;
    } else if (viewRect.top > maxTop) {
      offsetY = maxTop - viewRect.top;
    } else if (viewRect.bottom < minBottom) {
      offsetY = minBottom - viewRect.bottom;
    }
    offsetX /= 2;
    offsetY /= 2;

    pivotX -= offsetX / (float) displayWidth;
    pivotY -= offsetY / (float) displayHeight;

    this.pivotX = pivotX;
    this.pivotY = pivotY;
  }

  private void zoomIn (float x, float y) {
    if (zoom == 1f) {
      setMaxPivotForXY(x, y);
      animatePosition(MAX_ZOOM, pivotX, pivotY, 0f, 0f, true);
    }
  }

  // Movement

  private float lastMoveX, lastMoveY;

  private void onMoveStart (MotionEvent e) {
    lastMoveX = e.getX();
    lastMoveY = e.getY();
  }

  private void onMoveEvent (MotionEvent e) {
    float x = e.getX();
    float y = e.getY();

    offsetPositionBy(x - lastMoveX, y - lastMoveY);

    lastMoveX = x;
    lastMoveY = y;

    callback.onZoom();
  }

  // Utils

  public void zoomView (View parent, @Nullable View view) {
    if (view != null) {
      view.setScaleX(zoom);
      view.setScaleY(zoom);

      callback.getZoomDisplayRect(displayRect);
      final int displayWidth = displayRect.width();
      final int displayHeight = displayRect.height();

      view.setTranslationX(positionX * displayWidth);
      view.setTranslationY(positionY * displayHeight);

      view.setPivotX(pivotX * displayWidth - view.getLeft());
      view.setPivotY(pivotY * displayHeight - view.getTop());
    }
  }

  public void normalizeZoom (boolean animated) {
    if (animated && zoom > 1f) {
      animatePosition(1f, pivotX, pivotY, 0f, 0f, true);
    }
  }

  // Getters

  public float getZoom () {
    return zoom;
  }
}
