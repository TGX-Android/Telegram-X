package org.thunderdog.challegram.mediaview.crop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

/**
 * Date: 10/18/17
 * Author: default
 */

public class CropAreaView extends View implements FactorAnimator.Target {
  public static final int MODE_NONE = 0;
  public static final int MODE_NORMAL = 1;
  public static final int MODE_PRECISE = 2;
  public static final int MODE_INVISIBLE = 3;

  public interface RectChangeListener {
    void onCropAreaChanged (double left, double top, double right, double bottom);
  }

  public interface NormalizeListener {
    void onCropNormalization (float factor);
    void onCropNormalizationComplete ();
  }

  public interface RotateModeChangeListener {
    void onCropRotateModeChanged (boolean rotateInternally);
  }

  private @Nullable RectChangeListener rectChangeListener;
  private @Nullable NormalizeListener normalizeListener;
  private @Nullable RotateModeChangeListener rotateModeChangeListener;

  private final Rect areaRect = new Rect();
  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

  public CropAreaView (Context context) {
    super(context);
  }

  public void setRectChangeListener (@Nullable RectChangeListener rectChangeListener) {
    this.rectChangeListener = rectChangeListener;
  }

  public void setNormalizeListener (@Nullable NormalizeListener normalizeListener) {
    this.normalizeListener = normalizeListener;
  }

  public void setRotateModeChangeListener (@Nullable RotateModeChangeListener rotateModeChangeListener) {
    this.rotateModeChangeListener = rotateModeChangeListener;
  }

  private static final int OUTER_LINE_COLOR = 0x99ffffff;
  private static final int CORNER_COLOR = 0xffffffff;
  private static final int INNER_LINE_COLOR = 0xeeffffff;
  private static final int INNER_SHADOW_COLOR = 0x22000000;

  private float activeFactor;
  private int mode;
  private int effectiveMode;

  public void forceActiveFactor (float activeFactor) {
    setActiveFactor(activeFactor);
    activeAnimator.changeValueSilently(activeFactor);
  }

  private void setActiveFactor (float activeFactor) {
    if (this.activeFactor != activeFactor) {
      this.activeFactor = activeFactor;
      checkRotateMode();
      invalidate();
    }
  }

  private static final int ANIMATOR_ACTIVE = 0;
  private final BoolAnimator activeAnimator = new BoolAnimator(ANIMATOR_ACTIVE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 170l);

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ACTIVE:
        setActiveFactor(factor);
        break;
      case ANIMATOR_NORMALIZE:
        setNormalizeFactor(factor);
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_NORMALIZE:
        if (normalizeListener != null) {
          normalizeListener.onCropNormalizationComplete();
        }
        break;
    }
  }

  public void setMode (int mode, boolean animateValue) {
    if (this.mode != mode) {
      boolean isActive = mode != MODE_NONE;
      this.effectiveMode = isActive ? MODE_NONE : this.mode;
      this.mode = mode;
      checkRotateMode();
      if (animateValue) {
        activeAnimator.setValue(isActive, true);
      } else {
        activeAnimator.cancel();
        activeAnimator.changeValueSilently(isActive);
      }
    }
  }

  private int targetWidth, targetHeight;
  private double minWidthDiff, minHeightDiff;

  private static final int MINIMUM_PIXEL_COUNT = 18;

  public void resetState (int width, int height, double left, double top, double right, double bottom, boolean callListeners) {
    if (this.targetWidth != width || this.targetHeight != height || this.left != left || this.top != top || this.right != right || this.bottom != bottom) {
      this.targetWidth = width;
      this.targetHeight = height;
      this.minWidthDiff = (double) MINIMUM_PIXEL_COUNT / (double) width;
      this.minHeightDiff = (double) MINIMUM_PIXEL_COUNT / (double) height;
      setArea(left, top, right, bottom, callListeners);
      checkPivotCoordinates();
      invalidate();
    }
  }

  private double left = 0.0, top = 0.0, right = 1.0, bottom = 1.0;

  private int offsetLeft, offsetTop, offsetRight, offsetBottom;

  public void setOffsets (int left, int top, int right, int bottom) {
    if (this.offsetLeft != left || this.offsetTop != top || this.offsetRight != right || this.offsetBottom != bottom) {
      this.offsetLeft = left;
      this.offsetTop = top;
      this.offsetRight = right;
      this.offsetBottom = bottom;
      checkPivotCoordinates();
      invalidate();
    }
  }

  public void setOffsetBottom (int bottom) {
    if (this.offsetBottom != bottom) {
      this.offsetBottom = bottom;
      checkPivotCoordinates();
      invalidate();
    }
  }

  private void calculateAreaRect (@Nullable Rect srcRect, @Nullable Rect dstRect) {
    final int paddingLeft = offsetLeft;
    final int paddingTop = offsetTop;

    final int workAreaWidth = getWorkAreaWidth();
    final int workAreaHeight = getWorkAreaHeight();

    final int targetWidth = getTargetWidth();
    final int targetHeight = getTargetHeight();

    float scale = Math.min((float) workAreaWidth / (float) targetWidth, (float) workAreaHeight / (float) targetHeight);
    final int targetAreaWidth = (int) (targetWidth * scale);
    final int targetAreaHeight = (int) (targetHeight * scale);

    final int centerX = paddingLeft + workAreaWidth / 2;
    final int centerY = paddingTop + workAreaHeight / 2;

    final int targetAreaLeft = centerX - targetAreaWidth / 2;
    final int targetAreaTop = centerY - targetAreaHeight / 2;
    final int targetAreaRight = targetAreaLeft + targetAreaWidth;
    final int targetAreaBottom = targetAreaTop + targetAreaHeight;

    if (srcRect != null) {
      srcRect.set(targetAreaLeft, targetAreaTop, targetAreaRight, targetAreaBottom);
    }

    if (dstRect != null) {
      int left = targetAreaLeft + (int) Math.ceil((double) targetAreaWidth * this.left);
      int top = targetAreaTop + (int) Math.ceil((double) targetAreaHeight * this.top);
      int right = targetAreaLeft + (int) Math.floor((double) targetAreaWidth * this.right);
      int bottom = targetAreaTop + (int) Math.floor((double) targetAreaHeight * this.bottom);

      dstRect.set(left, top, right, bottom);
    }
  }

  private int getWorkAreaWidth () {
    return getMeasuredWidth() - offsetLeft - offsetRight;
  }

  private int getWorkAreaHeight () {
    return getMeasuredHeight() - offsetTop - offsetBottom;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    checkPivotCoordinates();
  }

  private void checkPivotCoordinates () {
    if (getWorkAreaWidth() > 0 && getWorkAreaHeight() > 0 && targetWidth > 0 && targetHeight > 0) {
      calculateAreaRect(srcRect, null);
      setPivotX(srcRect.centerX());
      setPivotY(srcRect.centerY());
    }
  }

  private final Rect srcRect = new Rect();
  private final Rect dstRect = new Rect();

  @Override
  protected void onDraw (Canvas c) {
    if (targetWidth == 0 || targetHeight == 0) {
      return;
    }

    float viewWidth = getMeasuredWidth();
    float viewHeight = getMeasuredHeight();

    int mode = this.mode != MODE_NONE ? this.mode : this.effectiveMode;
    int dragMode = this.dragMode != DRAG_NONE ? this.dragMode : this.effectiveDragMode;

    calculateAreaRect(srcRect, dstRect);

    int overlayColor = Theme.getColor(R.id.theme_color_transparentEditor);
    if (activeFactor < 1f) {
      overlayColor = Color.argb((int) (Color.alpha(overlayColor) * (1 + 0.8f * (1f - activeFactor))), 0, 0, 0);
    }
    int darkOverlay = ColorUtils.fromToArgb(0xff000000, overlayColor, activeFactor * (mode == MODE_PRECISE ? 1f : 0f));
    if (srcRect.top > 0) {
      c.drawRect(0, 0, viewWidth, srcRect.top, Paints.fillingPaint(darkOverlay));
    }
    if (srcRect.bottom < viewHeight) {
      c.drawRect(0, srcRect.bottom, viewWidth, viewHeight, Paints.fillingPaint(darkOverlay));
    }
    if (srcRect.left > 0) {
      c.drawRect(0, srcRect.top, srcRect.left, srcRect.bottom, Paints.fillingPaint(darkOverlay));
    }
    if (srcRect.right < viewWidth) {
      c.drawRect(srcRect.right, srcRect.top, viewWidth, srcRect.bottom, Paints.fillingPaint(darkOverlay));
    }
    if (srcRect.top < dstRect.top) {
      c.drawRect(srcRect.left, srcRect.top, srcRect.right, dstRect.top, Paints.fillingPaint(overlayColor));
    }
    if (srcRect.bottom > dstRect.bottom) {
      c.drawRect(srcRect.left, dstRect.bottom, srcRect.right, srcRect.bottom, Paints.fillingPaint(overlayColor));
    }
    if (srcRect.left < dstRect.left) {
      c.drawRect(srcRect.left, dstRect.top, dstRect.left, dstRect.bottom, Paints.fillingPaint(overlayColor));
    }
    if (srcRect.right > dstRect.right) {
      c.drawRect(dstRect.right, dstRect.top, srcRect.right, dstRect.bottom, Paints.fillingPaint(overlayColor));
    }

    int left = dstRect.left;
    int top = dstRect.top;
    int right = dstRect.right;
    int bottom = dstRect.bottom;

    int cornerWidth = Screen.dp(2f);
    int cornerHeight = Screen.dp(16f);
    int normalWidth = Screen.dp(1f);

    int strokeWidth = Math.max(1, Screen.dp(.5f));
    int cornerDiff = (strokeWidth * 2 - strokeWidth / 2) - cornerWidth;

    // left line
    c.drawRect(left - normalWidth, top + cornerHeight - cornerWidth, left, bottom - cornerHeight + cornerWidth, Paints.fillingPaint(OUTER_LINE_COLOR));
    // right line
    c.drawRect(right, top + cornerHeight - cornerWidth, right + normalWidth, bottom - cornerHeight + cornerWidth, Paints.fillingPaint(OUTER_LINE_COLOR));
    // top line
    c.drawRect(left + cornerHeight - cornerWidth, top - normalWidth, right - cornerHeight + cornerWidth, top, Paints.fillingPaint(OUTER_LINE_COLOR));
    // bottom line
    c.drawRect(left + cornerHeight - cornerWidth, bottom, right - cornerHeight + cornerWidth, bottom + normalWidth, Paints.fillingPaint(OUTER_LINE_COLOR));

    if (cornerHeight > 0) {
      float cornerAlpha = mode == MODE_NORMAL || mode == MODE_INVISIBLE ? 1f - activeFactor : 1f;
      int cornerColor = ColorUtils.fromToArgb(OUTER_LINE_COLOR, CORNER_COLOR, cornerAlpha);

      boolean inactive;
      float alpha;
      int color;
      int cw;

      // left-top corner
      inactive = dragMode == DRAG_OVERALL || (dragMode != DRAG_TOP_LEFT && dragMode != DRAG_TOP && dragMode != DRAG_LEFT);
      color = inactive ? cornerColor : CORNER_COLOR;
      alpha = inactive ? cornerAlpha : 1f;
      cw = (int) (cornerWidth + cornerDiff * (1f - alpha));
      final int cornerTopMin = Math.min(bottom, top + cornerHeight - cornerWidth);
      c.drawRect(left - cw, top - cw, left, cornerTopMin, Paints.fillingPaint(color));
      final int cornerLeftMin = Math.min(right, left + cornerHeight - cornerWidth);
      c.drawRect(left, top - cw, cornerLeftMin, top, Paints.fillingPaint(color));

      // cornerTopMin = (int) (top + (cornerTopMin - top) * (1f - alpha));
      // cornerLeftMin = (int) (left + (cornerLeftMin - left) * (1f - alpha));

      // right-top corner
      inactive = dragMode == DRAG_OVERALL || (dragMode != DRAG_TOP_RIGHT && dragMode != DRAG_TOP && dragMode != DRAG_RIGHT);
      color = inactive ? cornerColor : CORNER_COLOR;
      alpha = inactive ? cornerAlpha : 1f;
      cw = (int) (cornerWidth + cornerDiff * (1f - alpha));
      c.drawRect(right, top - cw, right + cw, Math.min(bottom, top + cornerHeight - cornerWidth), Paints.fillingPaint(color));
      c.drawRect(Math.max(Math.max((int) (left + (cornerLeftMin - left) * (1f - alpha)), left), right - cornerHeight + cornerWidth), top - cw, right, top, Paints.fillingPaint(color));
      // left-bottom corner
      inactive = dragMode == DRAG_OVERALL || (dragMode != DRAG_BOTTOM_LEFT && dragMode != DRAG_BOTTOM && dragMode != DRAG_LEFT);
      color = inactive ? cornerColor : CORNER_COLOR;
      alpha = inactive ? cornerAlpha : 1f;
      cw = (int) (cornerWidth + cornerDiff * (1f - alpha));
      c.drawRect(left - cw, Math.max((int) (top + (cornerTopMin - top) * (1f - alpha)), Math.max(top, bottom - cornerHeight + cornerWidth)), left, bottom + cw, Paints.fillingPaint(color));
      c.drawRect(left, bottom, Math.min(right, left + cornerHeight - cornerWidth), bottom + cw, Paints.fillingPaint(color));
      // right-bottom corner
      inactive = dragMode == DRAG_OVERALL || (dragMode != DRAG_BOTTOM_RIGHT && dragMode != DRAG_BOTTOM && dragMode != DRAG_RIGHT);
      color = inactive ? cornerColor : CORNER_COLOR;
      alpha = inactive ? cornerAlpha : 1f;
      cw = (int) (cornerWidth + cornerDiff * (1f - alpha));
      c.drawRect(right, Math.max((int) (top + (cornerTopMin - top) * (1f - alpha)), Math.max(top, bottom - cornerHeight + cornerWidth)), right + cw, bottom + cw, Paints.fillingPaint(color));
      c.drawRect(Math.max((int) (left + (cornerLeftMin - left) * (1f - alpha)), Math.max(left, right - cornerHeight + cornerWidth)), bottom, right, bottom + cw, Paints.fillingPaint(color));
    }


    final int count = mode == MODE_INVISIBLE ? 0 : mode == MODE_PRECISE ? 7 : 2;
    if (activeFactor > 0f && count > 0) {
      // lines

      int chunkWidth = (right - left) / (count + 1);
      int chunkHeight = (bottom - top) / (count + 1);

      int lineColor = ColorUtils.alphaColor(activeFactor, INNER_LINE_COLOR);
      int lineShadowColor = ColorUtils.alphaColor(activeFactor, INNER_SHADOW_COLOR);

      int shadowStroke = Screen.dp(1f);

      int startX, startY;

      linePaint.setColor(lineShadowColor);
      linePaint.setStrokeWidth(shadowStroke);

      startX = left + chunkWidth;
      startY = top + chunkHeight;
      for (int i = 0; i < count; i++) {
        // horizontal line
        c.drawLine(left, startY, right, startY, linePaint);
        // vertical line
        c.drawLine(startX, top, startX, bottom, linePaint);

        startX += chunkWidth;
        startY += chunkHeight;
      }

      linePaint.setColor(lineColor);
      linePaint.setStrokeWidth(strokeWidth);

      startX = left + chunkWidth;
      startY = top + chunkHeight;
      for (int i = 0; i < count; i++) {
        // horizontal line
        c.drawLine(left, startY, right, startY, linePaint);
        // vertical line
        c.drawLine(startX, top, startX, bottom, linePaint);

        startX += chunkWidth;
        startY += chunkHeight;
      }
    }
  }

  // Touch handling

  private static final int DRAG_NONE = 0;

  private static final int DRAG_LEFT = 1;
  private static final int DRAG_TOP = 2;
  private static final int DRAG_RIGHT = 3;
  private static final int DRAG_BOTTOM = 4;

  private static final int DRAG_TOP_LEFT = 5;
  private static final int DRAG_TOP_RIGHT = 6;
  private static final int DRAG_BOTTOM_LEFT = 7;
  private static final int DRAG_BOTTOM_RIGHT = 8;

  private static final int DRAG_OVERALL = 9;

  private int dragMode, effectiveDragMode;

  private void setDragMode (int mode) {
    if (this.dragMode != mode) {
      boolean isActive = mode != DRAG_NONE;
      this.effectiveDragMode = isActive ? DRAG_NONE : this.dragMode;
      this.dragMode = mode;
      setMode(mode == DRAG_OVERALL ? MODE_INVISIBLE : mode != DRAG_NONE ? MODE_NORMAL : MODE_NONE, true);
    }
  }

  private static float distance (float x1, float y1, float x2, float y2) {
    return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
  }

  private static float absDistance (float x1, float y1, float x2, float y2) {
    return Math.abs(distance(x1, y1, x2, y2));
  }

  private static final float TOUCH_PADDING = 26f;

  private static boolean matchesHorizontally (float x, float y, int padding, int centerX, int top, int bottom) {
    return x <= centerX + padding && x > centerX - padding && y <= bottom + padding && y > top - padding;
  }

  private static boolean matchesVertically (float x, float y, int padding, int centerY, int left, int right) {
    return y <= centerY + padding && y > centerY - padding && x <= right + padding && x > left - padding;
  }

  private boolean isDragging;
  private float downX, downY;
  private float startX, startY;
  private double startLeft, startTop, startRight, startBottom;

  private boolean setDragging (boolean isDragging) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      UI.getContext(getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_CROP, isDragging);
    }
    return isDragging;
  }

  private void setArea (double left, double top, double right, double bottom, boolean callListeners) {
    if (this.left != left || this.top != top || this.right != right || this.bottom != bottom) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      if (callListeners && rectChangeListener != null) {
        rectChangeListener.onCropAreaChanged(left, top, right, bottom);
      }
      invalidateArea();
    }
  }

  private void invalidateArea () {
    calculateAreaRect(srcRect, null);
    invalidate(srcRect.left - Screen.dp(2f), srcRect.top - Screen.dp(2f), srcRect.right + Screen.dp(2f), srcRect.bottom + Screen.dp(2f));
  }

  public boolean canRotate () {
    return this.mode == MODE_NONE && !activeAnimator.isAnimating();
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    final float x = e.getX();
    final float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        calculateAreaRect(null, areaRect);

        final int dragMode;
        int horizontalMode = DRAG_NONE;
        int verticalMode = DRAG_NONE;

        int centerX = areaRect.centerX();
        int centerY = areaRect.centerY();
        int padding = Screen.dp(TOUCH_PADDING);
        int areaWidth = areaRect.width();
        int areaHeight = areaRect.height();

        if (x <= areaRect.left || (x < areaRect.right && absDistance(areaRect.left, centerY, x, y) <= absDistance(areaRect.right, centerY, x, y))) {
          if (matchesHorizontally(x, y, padding, areaRect.left, areaRect.top, areaRect.bottom)) {
            horizontalMode = DRAG_LEFT;
          }
        } else {
          if (matchesHorizontally(x, y, padding, areaRect.right, areaRect.top, areaRect.bottom)) {
            horizontalMode = DRAG_RIGHT;
          }
        }

        if (y <= areaRect.top || (y < areaRect.bottom && absDistance(centerX, areaRect.top, x, y) <= absDistance(centerX, areaRect.bottom, x, y))) {
          if (matchesVertically(x, y, padding, areaRect.top, areaRect.left, areaRect.right)) {
            verticalMode = DRAG_TOP;
          }
        } else {
          if (matchesVertically(x, y, padding, areaRect.bottom, areaRect.left, areaRect.right)) {
            verticalMode = DRAG_BOTTOM;
          }
        }

        if (mode != MODE_NONE) {
          dragMode = DRAG_NONE;
        } else if (areaRect.contains((int) x, (int) y) && Math.abs(centerX - x) < areaWidth / 3 && Math.abs(centerY - y) < areaHeight / 3) {
          dragMode = DRAG_OVERALL;
        } else if (horizontalMode != DRAG_NONE && verticalMode != DRAG_NONE) {
          if (verticalMode == DRAG_TOP) {
            dragMode = horizontalMode == DRAG_LEFT ? DRAG_TOP_LEFT : DRAG_TOP_RIGHT;
          } else {
            dragMode = horizontalMode == DRAG_LEFT ? DRAG_BOTTOM_LEFT : DRAG_BOTTOM_RIGHT;
          }
        } else if (horizontalMode != DRAG_NONE || verticalMode != DRAG_NONE) {
          dragMode = Math.max(horizontalMode, verticalMode);
        } else if (x >= areaRect.left && y >= areaRect.top && x < areaRect.right && y < areaRect.bottom) {
          dragMode = DRAG_OVERALL;
        } else {
          dragMode = DRAG_NONE;
        }

        setDragMode(dragMode);

        this.isDragging = false;
        this.downX = x;
        this.downY = y;

        return dragMode != DRAG_NONE;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isDragging) {
          float targetScale = Math.min((float) getWorkAreaWidth() / (float) targetWidth, (float) getWorkAreaHeight() / (float) targetHeight);
          final int targetAreaWidth = (int) (targetWidth * targetScale);
          final int targetAreaHeight = (int) (targetHeight * targetScale);

          float diffXPx = x - startX;
          float diffYPx = y - startY;

          double diffX = (double) diffXPx / (double) targetAreaWidth;
          double diffY = (double) diffYPx / (double) targetAreaHeight;

          double newLeft = this.startLeft;
          double newTop = this.startTop;
          double newRight = this.startRight;
          double newBottom = this.startBottom;

          double shiftedLeft = Math.max(0.0, Math.min(newRight - minWidthDiff, newLeft + diffX));
          double shiftedRight = Math.min(1.0, Math.max(newLeft + minWidthDiff, newRight + diffX));
          double shiftedTop = Math.max(0.0, Math.min(newBottom - minHeightDiff, newTop + diffY));
          double shiftedBottom = Math.min(1.0, Math.max(newTop + minHeightDiff, newBottom + diffY));

          if (dragMode != DRAG_OVERALL) {
            float heightProportion = getProportion();
            if (heightProportion != 0f) {
              double centerX = (newLeft + newRight) / 2;
              double centerY = (newTop + newBottom) / 2;
              double newWidth, newHeight;
              switch (dragMode) {
                case DRAG_LEFT:
                case DRAG_RIGHT: {
                  if (dragMode == DRAG_LEFT) {
                    newWidth = newRight - shiftedLeft;
                  } else {
                    newWidth = shiftedRight - newLeft;
                  }
                  newHeight = newWidth * heightProportion;
                  double scale = Math.max(1.0, Math.max((float) minWidthDiff / newWidth, Math.max((float) minHeightDiff / newHeight, 1f))) * Math.min(1.0, Math.min(1.0 / newWidth, 1.0 / newHeight));
                  if (scale != 1.0) { newWidth *= scale; newHeight *= scale; }
                  if (dragMode == DRAG_LEFT) {
                    newLeft = newRight - newWidth;
                  } else {
                    newRight = newLeft + newWidth;
                  }
                  newTop = centerY - newHeight / 2;
                  newBottom = newTop + newHeight;
                  break;
                }
                case DRAG_TOP:
                case DRAG_BOTTOM: {
                  if (dragMode == DRAG_TOP) {
                    newHeight = newBottom - shiftedTop;
                  } else {
                    newHeight = shiftedBottom - newTop;
                  }
                  newWidth = newHeight / heightProportion;
                  double scale = Math.max(1.0, Math.max((float) minWidthDiff / newWidth, Math.max((float) minHeightDiff / newHeight, 1f))) * Math.min(1.0, Math.min(1.0 / newWidth, 1.0 / newHeight));
                  if (scale != 1.0) { newWidth *= scale; newHeight *= scale; }
                  if (dragMode == DRAG_TOP) {
                    newTop = newBottom - newHeight;
                  } else {
                    newBottom = newTop + newHeight;
                  }
                  newLeft = centerX - newWidth / 2;
                  newRight = newLeft + newWidth;
                  break;
                }
                case DRAG_TOP_LEFT:
                case DRAG_TOP_RIGHT:
                case DRAG_BOTTOM_LEFT:
                case DRAG_BOTTOM_RIGHT: {
                  double futureWidth, futureHeight;

                  if (dragMode == DRAG_TOP_LEFT || dragMode == DRAG_BOTTOM_LEFT) {
                    futureWidth = newRight - shiftedLeft;
                  } else {
                    futureWidth = shiftedRight - newLeft;
                  }
                  if (dragMode == DRAG_TOP_LEFT || dragMode == DRAG_TOP_RIGHT) {
                    futureHeight = newBottom - shiftedTop;
                  } else {
                    futureHeight = shiftedBottom - newTop;
                  }

                  double fw1 = futureWidth;
                  double fh1 = fw1 * heightProportion;

                  double fh2 = futureHeight;
                  double fw2 = fh2 / heightProportion;

                  boolean needMax;
                  if (Math.abs(diffXPx) >= Math.abs(diffYPx)) {
                    needMax = dragMode == DRAG_TOP_LEFT || dragMode == DRAG_BOTTOM_LEFT ? diffXPx < 0 : diffXPx > 0;
                  } else {
                    needMax = dragMode == DRAG_TOP_LEFT || dragMode == DRAG_TOP_RIGHT ? diffYPx < 0 : diffYPx > 0;
                  }

                  if (needMax) {
                    if ((fw1 * fh1) > (fw2 * fh2)) {
                      newWidth = fw1;
                      newHeight = fh1;
                    } else {
                      newWidth = fw2;
                      newHeight = fh2;
                    }
                  } else {
                    if ((fw1 * fh1) <= (fw2 * fh2)) {
                      newWidth = fw1;
                      newHeight = fh1;
                    } else {
                      newWidth = fw2;
                      newHeight = fh2;
                    }
                  }

                  double scale = Math.max(1.0, Math.max((float) minWidthDiff / newWidth, Math.max((float) minHeightDiff / newHeight, 1f))) * Math.min(1.0, Math.min(1.0 / newWidth, 1.0 / newHeight));
                  if (scale != 1.0) { newWidth *= scale; newHeight *= scale; }

                  if (dragMode == DRAG_TOP_LEFT || dragMode == DRAG_BOTTOM_LEFT) {
                    newLeft = newRight - newWidth;
                  } else {
                    newRight = newLeft + newWidth;
                  }
                  if (dragMode == DRAG_TOP_LEFT || dragMode == DRAG_TOP_RIGHT) {
                    newTop = newBottom - newHeight;
                  } else {
                    newBottom = newTop + newHeight;
                  }

                  break;
                }
              }

              /*double scale = Math.min(1.0 / newWidth, 1.0 / newHeight);

              if (scale < 1.0) {
                newWidth *= scale;
                newHeight *= scale;
              }

              newTop = centerY - newHeight / 2;
              newBottom = newTop + newHeight;
              newLeft = centerX - newWidth / 2;
              newRight = newLeft + newWidth;*/

              if (newTop < 0.0) {
                newBottom += -newTop;
                newTop = 0.0;
              } else if (newBottom > 1.0) {
                newTop -= (newBottom - 1.0);
                newBottom = 1.0;
              }

              if (newLeft < 0.0) {
                newRight += -newLeft;
                newLeft = 0.0;
              } else if (newRight > 1.0) {
                newLeft -= (newRight - 1.0);
                newRight = 1.0;
              }
            } else {
              if (dragMode == DRAG_LEFT || dragMode == DRAG_BOTTOM_LEFT || dragMode == DRAG_TOP_LEFT) {
                newLeft = shiftedLeft;
              } else if (dragMode == DRAG_RIGHT || dragMode == DRAG_BOTTOM_RIGHT || dragMode == DRAG_TOP_RIGHT) {
                newRight = shiftedRight;
              }
              if (dragMode == DRAG_TOP || dragMode == DRAG_TOP_LEFT || dragMode == DRAG_TOP_RIGHT) {
                newTop = shiftedTop;
              } else if (dragMode == DRAG_BOTTOM || dragMode == DRAG_BOTTOM_LEFT || dragMode == DRAG_BOTTOM_RIGHT) {
                newBottom = shiftedBottom;
              }
            }
          } else {
            double horizontalDiff = newRight - newLeft;
            double verticalDiff = newBottom - newTop;

            if (diffX >= 0) {
              newRight = Math.min(1.0, newRight + diffX);
              newLeft = newRight - horizontalDiff;
            } else {
              newLeft = Math.max(0.0, newLeft + diffX);
              newRight = newLeft + horizontalDiff;
            }

            if (diffY >= 0) {
              newBottom = Math.min(1.0, newBottom + diffY);
              newTop = newBottom - verticalDiff;
            } else {
              newTop = Math.max(0.0, newTop + diffY);
              newBottom = newTop + verticalDiff;
            }
          }

          setArea(newLeft, newTop, newRight, newBottom, true);

          this.startLeft = newLeft;
          this.startTop = newTop;
          this.startRight = newRight;
          this.startBottom = newBottom;
          this.startX = x;
          this.startY = y;

          trackMovement(diffXPx, diffYPx);
        } else if (setDragging(Math.max(Math.abs(x - downX), Math.abs(y - downY)) > Screen.getTouchSlop())) {
          startX = x;
          startY = y;
          startLeft = left;
          startTop = top;
          startRight = right;
          startBottom = bottom;

          trackMovement(0, 0);
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP: {
        if (dragMode != DRAG_NONE) {
          setDragMode(DRAG_NONE);
          setDragging(false);
          return true;
        }
        break;
      }
    }
    return true;
  }

  private static final int ANIMATOR_NORMALIZE = 1;
  private FactorAnimator normalizeAnimator;
  private float normalizeFactor;

  private double fromLeft, fromTop, fromRight, fromBottom;
  private double leftDiff, topDiff, rightDiff, bottomDiff;

  private void cancelPositionAnimator () {
    if (normalizeAnimator != null) {
      normalizeAnimator.cancel();
    }
  }

  private void normalizeProportion () {
    float heightProportion = getProportion();
    if (heightProportion == 0) {
      cancelPositionAnimator();
      return;
    }

    double centerX = (left + right) / 2;
    double centerY = (top + bottom) / 2;

    double fromWidth = right - left;
    double fromHeight = bottom - top;

    double newWidth;
    double newHeight;

    if (targetWidth < targetHeight) {
      newWidth = fromWidth;
      newHeight = newWidth * heightProportion;
    } else {
      newHeight = fromHeight;
      newWidth = newHeight / heightProportion;
    }

    double scale = Math.max(1.0, Math.max((float) minWidthDiff / newWidth, Math.max((float) minHeightDiff / newHeight, 1f))) * Math.min(1.0, Math.min(1.0 / newWidth, 1.0 / newHeight));
    if (scale != 1.0) {
      newWidth = Math.min(1.0, Math.max(0.0, newWidth * scale));
      newHeight = Math.min(1.0, Math.max(0.0, newHeight * scale));
    }

    double newLeft = centerX - newWidth / 2;
    double newRight = newLeft + newWidth;
    double newTop = centerY - newHeight / 2;
    double newBottom = newTop + newHeight;

    if (newTop < 0.0) {
      newBottom += -newTop;
      newTop = 0.0;
    } else if (newBottom > 1.0) {
      newTop -= (newBottom - 1.0);
      newBottom = 1.0;
    }

    if (newLeft < 0.0) {
      newRight += -newLeft;
      newLeft = 0.0;
    } else if (newRight > 1.0) {
      newLeft -= (newRight - 1.0);
      newRight = 1.0;
    }

    animateArea(newLeft, newTop, newRight, newBottom, false, false);
  }

  public boolean resetArea (boolean forceAnimation, boolean useFastAnimation) {
    resetProportion();
    return animateArea(0.0, 0.0, 1.0, 1.0, forceAnimation, useFastAnimation);
  }

  public boolean animateArea (double toLeft, double toTop, double toRight, double toBottom, boolean forceAnimation, boolean useFastAnimation) {
    if (normalizeAnimator == null) {
      normalizeAnimator = new FactorAnimator(ANIMATOR_NORMALIZE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else {
      normalizeAnimator.forceFactor(0f);
      normalizeFactor = 0f;
    }

    if (forceAnimation || this.left != toLeft || this.top != toTop || this.right != toRight || this.bottom != toBottom) {
      fromLeft = this.left;
      fromTop = this.top;
      fromRight = this.right;
      fromBottom = this.bottom;
      leftDiff = toLeft - fromLeft;
      topDiff = toTop - fromTop;
      rightDiff = toRight - fromRight;
      bottomDiff = toBottom - fromBottom;
      normalizeAnimator.setDuration(useFastAnimation ? 120l : 180l);
      normalizeAnimator.animateTo(1f);
      return true;
    }

    return false;
  }

  private void setNormalizeFactor (float factor) {
    if (this.normalizeFactor != factor) {
      this.normalizeFactor = factor;
      setArea(fromLeft + leftDiff * factor, fromTop + topDiff * factor, fromRight + rightDiff * factor, fromBottom + bottomDiff * factor, true);
      if (normalizeListener != null) {
        normalizeListener.onCropNormalization(factor);
      }
    }
  }

  private int proportionBig, proportionSmall;

  public float getFixedProportion () {
    return proportionBig > 0 && proportionSmall > 0 ? (float) proportionBig / (float) proportionSmall : 0f;
  }

  public float getOriginalProportion () {
    return (float) Math.max(targetWidth, targetHeight) / (float) Math.min(targetWidth, targetHeight);
  }

  private float getProportion () {
    int targetWidth = getTargetWidth();
    int targetHeight = getTargetHeight();
    return proportionBig > 0 && proportionSmall > 0 ? ((float) targetWidth / (float) targetHeight) * (targetWidth < targetHeight ? (float) proportionBig / (float) proportionSmall : (float) proportionSmall / (float) proportionBig) : 0f;
  }

  public int getTargetWidth () {
    return targetWidth;
  }

  public int getTargetHeight () {
    return targetHeight;
  }

  public void setFixedProportion (int big, int small) {
    if (this.proportionBig != big || this.proportionSmall != small) {
      this.proportionBig = big;
      this.proportionSmall = small;
      normalizeProportion();
    }
  }

  public void resetProportion () {
    this.proportionBig = 0;
    this.proportionSmall = 0;
  }

  public void rotateValues (int by) {
    double left = this.left;
    double top = this.top;
    double right = this.right;
    double bottom = this.bottom;

    int targetWidth = this.targetWidth;
    int targetHeight = this.targetHeight;

    while (by != 0) {
      int tempInt = targetWidth;
      targetWidth = targetHeight;
      targetHeight = tempInt;

      if (left != 0.0 || top != 0.0 || right != 1.0 || bottom != 1.0) {
        double prevLeft = left;
        double prevTop = top;
        double prevRight = right;
        double prevBottom = bottom;

        if (by < 0) {
          bottom = 1.0 - prevLeft;
          right = prevBottom;
          top = 1.0 - prevRight;
          left = prevTop;
          by += 90;
        } else {
          bottom = prevRight;
          left = 1.0 - prevBottom;
          top = prevLeft;
          right = 1.0 - prevTop;
          by -= 90;
        }
      } else {
        by += 90 * -Math.signum(by);
      }
    }

    resetState(targetWidth, targetHeight, left, top, right, bottom, true);
  }

  private boolean rotatingInternally;

  private void checkRotateMode () {
    boolean rotateInternally = activeFactor == 0f || (mode == MODE_NONE ? effectiveMode : mode) != MODE_PRECISE;
    if (rotatingInternally != rotateInternally) {
      rotatingInternally = rotateInternally;
      if (rotateModeChangeListener != null) {
        rotateModeChangeListener.onCropRotateModeChanged(rotateInternally);
      }
    }
  }

  private void trackMovement (float x, float y) {
    // TODO show zoomed view
  }
}
