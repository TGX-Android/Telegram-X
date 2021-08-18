package org.thunderdog.challegram.component.attach;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

/**
 * Date: 19/10/2016
 * Author: default
 */

public class MediaBottomBar extends FrameLayoutFix implements GestureDetector.OnGestureListener {
  static class BarItem {
    private final int iconResource;
    private final String name;
    private final int backgroundColorId;
    private final int paddingTop;

    // internal
    private float measuredWidth;
    private Drawable icon;
    private float factor;

    public BarItem (@DrawableRes int icon, int name, int backgroundColorId) {
      this.iconResource = icon;
      this.name = Lang.getString(name);
      this.backgroundColorId = backgroundColorId;
      this.paddingTop = 0;
    }

    public BarItem (@DrawableRes int icon, int name, int backgroundColorId, int paddingTop) {
      this.iconResource = icon;
      this.name = Lang.getString(name);
      this.backgroundColorId = backgroundColorId;
      this.paddingTop = paddingTop;
    }
  }

  interface Callback {
    boolean onBottomPrepareSectionChange (int fromIndex, int toIndex);
    void onBottomFactorChanged (float factor);
    void onBottomSectionChanged (int index);

    void onBottomTopRequested (int currentIndex);
  }

  // Data
  private BarItem[] items;
  private int index;
  private Callback callback;

  // Paints
  // private final TextPaint textPaint;

  // Metrics
  private final int barHeight;
  private final int textY;
  private final int iconActivePadding;

  private int lastBarWidth, lastScreenHeight;
  private float maxTextWidth;
  private int itemNormalWidth;
  private int itemActiveDiff;
  private int itemStartX;
  private float circleRadius;

  private final GestureDetector flingDetector;

  private static final float TEXT_SIZE = 14f;

  public MediaBottomBar (Context context) {
    super(context);

    flingDetector = new GestureDetector(context, this);

    barHeight = getBarHeight();
    iconActivePadding = Screen.dp(10f);
    textY = barHeight - Screen.dp(9f);

    setWillNotDraw(false);
    ViewUtils.setBackground(this, new MediaBottomBarBackground());
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight, Gravity.BOTTOM));
  }

  // Getters

  public static int getBarHeight () {
    return Screen.dp(56f);
  }

  public int getCurrentBarWidth () {
    /*if (lastBarWidth != 0 && lastBarWidth == getMeasuredWidth() && false) {
      return lastBarWidth;
    }*/
    int barWidth = Screen.currentWidth();
    int maxItemWidth = Screen.dp(168f);
    int itemWidth = barWidth / items.length;
    return itemWidth > maxItemWidth ? maxItemWidth * items.length : barWidth;
  }

  public int getCurrentIndex () {
    return index;
  }

  // Setters

  public void setItems (BarItem[] items, int currentIndex) {
    float maxTextWidth = 0;
    int index = 0;
    for (BarItem item : items) {
      if (item.name == null || item.name.isEmpty()) {
        throw new IllegalArgumentException("item.itemIconResource == 0 || item.itemName == null || item.itemName.isEmpty()");
      }
      item.measuredWidth = U.measureText(item.name, Paints.getRegularTextPaint(TEXT_SIZE));
      if (item.measuredWidth > maxTextWidth) {
        maxTextWidth = item.measuredWidth;
      }
      item.icon = item.iconResource == 0 ? null : Drawables.get(getResources(), item.iconResource);
      if (index == currentIndex) {
        item.factor = 1f;
      }
      index++;
    }
    this.index = currentIndex;
    this.maxTextWidth = maxTextWidth;
    this.items = items;
    this.lastBarWidth = 0; // Resetting all related metrics
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  // Metrics

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateBarMetrics();
  }

  private void updateBarMetrics () {
    int barWidth = getMeasuredWidth();
    if (barWidth <= 0) {
      return;
    }
    if (lastBarWidth == barWidth && lastScreenHeight == Screen.currentHeight()) {
      return;
    }

    lastBarWidth = barWidth;
    lastScreenHeight = Screen.currentHeight();

    int maxItemWidth = Screen.dp(168f);
    int itemWidth = barWidth / items.length;

    int totalWidth;
    if (itemWidth > maxItemWidth) {
      this.itemStartX = (maxItemWidth * items.length - barWidth) / 2;
      totalWidth = maxItemWidth * items.length;
      itemWidth = maxItemWidth;
    } else {
      this.itemStartX = 0;
      totalWidth = barWidth;
    }

    int itemActiveWidth = Math.max(itemWidth, (int) (maxTextWidth + Screen.dp(40f)));
    int itemNormalWidth = (totalWidth - itemActiveWidth) / (items.length - 1);

    this.itemNormalWidth = itemNormalWidth;
    this.itemActiveDiff = itemActiveWidth - itemNormalWidth;

    this.circleRadius = (float) Math.sqrt((barWidth * barWidth) + (barHeight * barHeight)) * .5f;
  }

  // Event handler

  private float touchStartX, touchStartY;
  private int touchIndex = -1, touchCenterX = -1;

  private int findCenterX (int lookupIndex) {
    if (items == null || items.length == 0) {
      return -1;
    }
    int cx = itemStartX;
    int index = 0;
    for (BarItem item : items) {
      int itemWidth = itemNormalWidth + (int) ((float) itemActiveDiff * item.factor);
      if (index == lookupIndex) {
        return cx + itemWidth / 2;
      }
      index++;
      cx += itemWidth;
    }
    return -1;
  }

  private int findTouchIndex (float x) {
    if (items == null || items.length == 0) {
      return -1;
    }
    int cx = itemStartX;
    int index = 0;
    for (BarItem item : items) {
      int itemWidth = itemNormalWidth + (int) ((float) itemActiveDiff * item.factor);
      if (x >= cx && x < cx + itemWidth) {
        return index;
      }
      index++;
      cx +=  itemWidth;
    }
    return -1;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    flingDetector.onTouchEvent(e);

    if (items == null || items.length == 0 || overlayFactor != 0f) {
      return true;
    }

    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        touchIndex = -1;
        touchCenterX = -1;
        touchStartX = x;
        touchStartY = y;

        int cx = itemStartX;
        int index = 0;
        for (BarItem item : items) {
          int itemWidth = itemNormalWidth + (int) ((float) itemActiveDiff * item.factor);
          if (x >= cx && x < cx + itemWidth) {
            touchIndex = index;
            touchCenterX = cx + (int) ((float) itemWidth * .5f);
            break;
          }
          index++;
          cx += itemWidth;
        }

        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (touchIndex != -1) {
          float xDiff = Math.abs(touchStartX - x);
          float yDiff = Math.abs(touchStartY - y);
          if (Math.max(xDiff, yDiff) > Screen.getTouchSlopBig()) {
            touchIndex = -1;
          }
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        touchIndex = -1;
        return true;
      }
      case MotionEvent.ACTION_UP: {
        if (touchIndex != -1 && findTouchIndex(x) == touchIndex) {
          performSelection(touchIndex);
          return true;
        }
        break;
      }
    }
    return true;
  }

  // Animation

  public boolean setSelectedIndex (int index) {
    int centerX = findCenterX(index);
    if (centerX == -1) {
      return false;
    }
    touchCenterX = centerX;
    touchIndex = index;
    return performSelection(index);
  }

  private boolean isAnimating;
  private BarItem fromItem, toItem;
  private float factor;

  private boolean performSelection (final int index) {
    if (isAnimating) {
      return false;
    }
    ViewUtils.onClick(this);
    if (callback != null) {
      if (this.index == index) {
        callback.onBottomTopRequested(index);
      } else if (!callback.onBottomPrepareSectionChange(this.index, index)) {
        return false;
      }
    }
    if (this.index == index) {
      return false;
    }
    this.isAnimating = true;
    this.fromItem = items[this.index];
    this.toItem = items[index];

    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
    obj.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        factor = 0f;
        MediaBottomBar.this.index = index;
        if (callback != null) {
          callback.onBottomSectionChanged(index);
        }
        isAnimating = false;
      }
    });
    obj.setDuration(240l);
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.start();

    return true;
  }

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      this.fromItem.factor = 1f - factor;
      this.toItem.factor = factor;
      invalidate();
      if (callback != null) {
        callback.onBottomFactorChanged(factor);
      }
    }
  }

  private float overlayFactor, overlayRadius;
  private int overlayCenterX, overlayCenterY;

  public void prepareOverlayAnimation () {
    overlayCenterX = itemStartX;
    overlayCenterY = barHeight / 2 - (int) ((float) (iconActivePadding - items[index].paddingTop) * factor);
    overlayRadius = (float) Math.sqrt((getMeasuredWidth() * getMeasuredWidth()) + (barHeight * barHeight)) * .5f;
    int cx = itemStartX;
    int i = 0;
    for (BarItem item : items) {
      int itemWidth = itemNormalWidth + Math.round((float) itemActiveDiff * item.factor);
      if (i == index) {
        overlayCenterX = cx + itemWidth / 2;
        break;
      }
      cx += itemWidth;
      i++;
    }
  }

  public void setOverlayFactor (float factor) {
    if (this.overlayFactor != factor) {
      this.overlayFactor = factor;
      invalidate();
    }
  }

  // Drawing

  private static final float TEXT_THRESHOLD = .55f;
  private static final float TEXT_SCALE = .2f;

  private class MediaBottomBarBackground extends Drawable {
    private Paint iconPaint;
    private int iconColor;

    @Override
    public void draw (@NonNull Canvas c) {
      updateBarMetrics();
      int barWidth = getMeasuredWidth();
      if (items == null || items.length == 0 || barWidth == 0) {
        return;
      }

      int centerX = barWidth / 2;
      int centerY = barHeight / 2;

      if (overlayFactor != 1f) {
        int backgroundColor = Theme.getColor(items[index].backgroundColorId);


        if (factor == 0f) {
          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(backgroundColor));
        } else {
          int toColor = Theme.getColor(toItem.backgroundColorId);

          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(ColorUtils.compositeColor(backgroundColor, ColorUtils.alphaColor(factor, toColor))));

          float cx = touchCenterX;
          float cy = centerY - (int) ((float) (iconActivePadding - fromItem.paddingTop) * factor);

          float x = cx + (centerX - cx) * factor;
          float y = cy + (centerY - cy) * factor;

          c.drawCircle(x, y, circleRadius * factor, Paints.fillingPaint(toColor));
        }

        int cx = itemStartX;
        for (BarItem item : items) {
          int itemWidth = itemNormalWidth + Math.round((float) itemActiveDiff * item.factor);
          int itemCenterX = cx + itemWidth / 2;

          int iconCenterY = item.factor == 0f ? centerY : centerY - (int) ((float) (iconActivePadding - item.paddingTop) * item.factor);
          int iconAlpha = item.factor == 1f ? 255 : 255 - (int) ((255f * .25f) * (1f - item.factor));
          int color = Theme.getColor(R.id.theme_color_attachText);
          Paint bitmapPaint = iconPaint != null && iconColor == color ? iconPaint : (iconPaint = Paints.createPorterDuffPaint(iconPaint, iconColor = color));
          bitmapPaint.setAlpha(iconAlpha);
          if (item.icon != null) {
            Drawables.draw(c, item.icon, itemCenterX - item.icon.getMinimumWidth() / 2, iconCenterY - (int) ((float) item.icon.getMinimumHeight() * .5f), bitmapPaint);
          } else {
            c.drawCircle(itemCenterX, iconCenterY, Screen.dp(12f), Paints.fillingPaint(0xffffffff));
          }

          if (item.factor == 1f) {
            int textX = itemCenterX - (int) (item.measuredWidth * .5f);
            c.drawText(item.name, textX, textY, Paints.getRegularTextPaint(TEXT_SIZE, color));
          } else if (item.factor >= TEXT_THRESHOLD) {
            c.save();
            c.translate(itemCenterX, textY);

            float scale = (1f - TEXT_SCALE) + TEXT_SCALE * item.factor;
            c.scale(scale, scale);

            int textX = -(int) (item.measuredWidth * .5f);
            float textFactor = (item.factor - TEXT_THRESHOLD) / (1f - TEXT_THRESHOLD);

            c.drawText(item.name, textX, 0, Paints.getRegularTextPaint(TEXT_SIZE, ColorUtils.alphaColor(textFactor, color)));
            c.restore();
          }

          cx += itemWidth;
        }
      }

      if (overlayFactor != 0f) {
        final int fillingColor = Theme.fillingColor();
        if (overlayFactor != 1f) {
          c.drawCircle(overlayCenterX + (centerX - overlayCenterX) * overlayFactor, overlayCenterY + (centerY - overlayCenterY) * overlayFactor, overlayRadius * overlayFactor, Paints.fillingPaint(fillingColor));
          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(ColorUtils.color((int) (255f * overlayFactor), fillingColor)));
        } else {
          c.drawRect(0, 0, barWidth, barHeight, Paints.fillingPaint(fillingColor));
        }
      }
    }

    @Override
    public void setAlpha (int alpha) {

    }

    @Override
    public void setColorFilter (ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }

  public int getCurrentColor ()  {
    int backgroundColor = Theme.getColor(items[index].backgroundColorId);
    if (factor != 0f) {
      int toColor = Theme.getColor(toItem.backgroundColorId);
      backgroundColor = ColorUtils.compositeColor(backgroundColor, ColorUtils.alphaColor(factor, toColor));
    }
    return backgroundColor;
  }

  // Gestures


  @Override
  public boolean onDown (MotionEvent e) {
    return true;
  }

  @Override
  public void onShowPress (MotionEvent e) {

  }

  @Override
  public boolean onSingleTapUp (MotionEvent e) {
    return false;
  }

  @Override
  public boolean onScroll (MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    return false;
  }

  @Override
  public void onLongPress (MotionEvent e) {

  }

  private boolean handleFling (boolean left) {
    if (items == null || items.length == 0 || overlayFactor != 0f) {
      return false;
    }
    int toIndex = left ? Math.max(0, index - 1) : Math.min(index + 1, items.length - 1);
    return toIndex != index && setSelectedIndex(toIndex);
  }

  @Override
  public boolean onFling (MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    return Math.abs(velocityX) > Screen.dp(250, 1f) && handleFling(velocityX >= 0);
  }
}
