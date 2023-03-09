/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 08/02/2016 at 10:19
 */
package org.thunderdog.challegram.component.user;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;

public class BubbleWrapView extends View {
  TextPaint paint;

  static final float START_X = 4f;
  static final float START_Y = 12f;
  static final float SPACING = 8f;

  private ArrayList<BubbleView> bubbles;
  private BubbleHeaderView headerView;

  public BubbleWrapView (Context context) {
    super(context);
    bubbles = new ArrayList<>(10);
    paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(Fonts.getRobotoRegular());
    paint.setTextSize(Screen.dp(14f));
  }

  public void setHeaderView (BubbleHeaderView headerView) {
    this.headerView = headerView;
  }

  public void addBubbleForce (final TGUser user) {
    int defaultWidth = Screen.dp(100f);
    int maxWidth = (int) ((float) (Screen.smallestSide() - Screen.dp(60f)) * .5f) - Screen.dp(SPACING) - Screen.dp(44f);

    final int maxTextWidth;
    if (maxWidth < defaultWidth) {
      maxTextWidth = defaultWidth;
    } else if (maxWidth > Screen.dp(200f)) {
      maxTextWidth = Screen.dp(200f);
    } else {
      maxTextWidth = maxWidth;
    }

    BubbleView view = new BubbleView(this, user, maxTextWidth);

    if (bubbles.size() == 0) {
      view.setXY(Screen.dp(START_X), Screen.dp(START_Y));
    } else {
      BubbleView prev = bubbles.get(bubbles.size() - 1);
      float add = Screen.dp(SPACING);
      float cx = prev.getX() + prev.getWidth() + add;
      float cy = prev.getY();
      if (cx + view.getWidth() > getMeasuredWidth() - add) {
        cx = Screen.dp(START_X);
        cy = cy + prev.getHeight() + add;
      }
      view.setXY((int) cx, (int) cy);
    }

    view.requestFile();
    bubbles.add(view);

    // final BubbleView view = new BubbleView(BubbleWrapView.this, user, Scree);
  }

  public void addBubble (final TGUser user) {
    isAnimating = true;
    changingHeight = false;

    Background.instance().post(() -> {
      int defaultWidth = Screen.dp(100f);
      int maxWidth = (int) ((float) (Screen.smallestSide() - Screen.dp(60f)) * .5f) - Screen.dp(SPACING) - Screen.dp(44f);

      final int maxTextWidth;
      if (maxWidth < defaultWidth) {
        maxTextWidth = defaultWidth;
      } else if (maxWidth > Screen.dp(200f)) {
        maxTextWidth = Screen.dp(200f);
      } else {
        maxTextWidth = maxWidth;
      }

      final BubbleView view = new BubbleView(BubbleWrapView.this, user, maxTextWidth);
      UI.post(() -> {
        view.requestFile();

        if (bubbles.size() == 0) {
          view.setXY(Screen.dp(START_X), Screen.dp(START_Y));
        } else {
          BubbleView prev = bubbles.get(bubbles.size() - 1);
          float add = Screen.dp(SPACING);
          float cx = prev.getX() + prev.getWidth() + add;
          float cy = prev.getY();
          if (cx + view.getWidth() > getMeasuredWidth() - add) {
            cx = Screen.dp(START_X);
            cy = cy + prev.getHeight() + add;
          }
          view.setXY((int) cx, (int) cy);
        }

        factor = 0f;

        rangeStart = bubbles.size();
        rangeEnd = rangeStart + 1;

        int height = getCurrentHeight();
        bubbles.add(view);
        int newHeight = getCurrentHeight();
        if (newHeight != height) {
          requestLayout();
        }
        changingHeight = headerView.prepareChangeHeight(newHeight, false);

        view.prepareShow();

        if (Views.HARDWARE_LAYER_ENABLED) {
          setBoundLayerType(LAYER_TYPE_HARDWARE);
        }

        ValueAnimator obj;
        obj = AnimatorUtils.simpleValueAnimator();
        // FIXME via implements
        obj.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
        obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        obj.setDuration(150l);
        obj.setStartDelay(20l);
        obj.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (Animator animation) {
            view.completeShow();
            if (changingHeight) {
              headerView.completeChangeHeight();
            }
            if (Views.HARDWARE_LAYER_ENABLED) {
              setBoundLayerType(LAYER_TYPE_NONE);
            }
            isAnimating = false;
          }
        });
        obj.start();
      });
    });
  }

  public void removeBubble (TGUser user) {
    long chatId = user.getChatId();
    int i = 0;
    for (BubbleView view : bubbles) {
      if (view.getChatId() == chatId) {
        hideAnimated(i, false);
        break;
      }
      i++;
    }
  }

  private void hideAnimated (int i, boolean byTouch) {
    int lastHeight = getCurrentHeight();

    bubbles.get(i).prepareHide();
    if (i + 1 < bubbles.size()) {
      for (int j = i + 1; j < bubbles.size(); j++) {
        bubbles.get(j).prepareMove();
      }
      buildLayout();
    }

    rangeStart = i;
    rangeEnd = bubbles.size();

    factor = 0f;
    changingHeight = false;
    isAnimating = true;

    int newHeight = getCurrentHeight();
    final boolean layout = newHeight != lastHeight;
    changingHeight = headerView.prepareChangeHeight(newHeight, byTouch);

    if (Views.HARDWARE_LAYER_ENABLED) {
      setBoundLayerType(LAYER_TYPE_HARDWARE);
    }

    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    // FIXME via implements
    obj.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.setDuration(150l);
    obj.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        deleteBubble();
        if (changingHeight) {
          headerView.completeChangeHeight();
        }
        if (layout) {
          requestLayout();
        }
        if (Views.HARDWARE_LAYER_ENABLED) {
          setBoundLayerType(LAYER_TYPE_NONE);
        }
        isAnimating = false;
      }
    });
    obj.start();
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    for (BubbleView view : bubbles) {
      view.onAttachedToWindow();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    for (BubbleView view : bubbles) {
      view.onDetachedFromWindow();
    }
  }

  private void setBoundLayerType (int type) {
    if (getMeasuredHeight() < 512) {
      Views.setLayerType(this, type);
    }
    Views.setLayerType(UI.getHeaderView(), type);
    if (headerView.callback != null) {
      Views.setLayerType(headerView.callback.getTranslationView(), type);
    }
  }

  public void deleteBubble () {
    for (int i = rangeStart; i < rangeEnd; i++) {
      bubbles.get(i).completeMove();
    }
    BubbleView view = bubbles.remove(rangeStart);
    view.destroy();
    rangeStart = rangeEnd = 0;
  }

  public void destroy () {
    for (BubbleView bubble : bubbles) {
      bubble.destroy();
    }
  }

  public int getCurrentHeight () {
    int length = bubbles.size();
    if (length == 0) {
      return 0;
    } else {
      BubbleView bubble = null;
      while (length != 0 && (bubble = bubbles.get(length - 1)).isHiding()) {
        length--;
      }
      return bubble.isHiding() ? 0 : bubble.getY() + bubble.getHeight();
    }
  }

  int lastMeasuredWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), getCurrentHeight());
    int width = getMeasuredWidth();
    if (lastMeasuredWidth != width) {
      lastMeasuredWidth = width;
      buildLayout();
      int height = getCurrentHeight();
      headerView.forceHeight(height); // fixme maybe move to post(this)
      setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);
    }
  }

  public void buildLayout () {
    int length = bubbles.size();

    if (length == 0) {
      return;
    }

    float add = Screen.dp(SPACING);
    float maxX = (getMeasuredWidth() == 0 ? Screen.currentWidth() - Screen.dp(60f) : getMeasuredWidth()) - add;
    float startX = Screen.dp(START_X);
    float cx = startX;
    float cy = Screen.dp(START_Y);

    for (int i = 0; i < length; i++) {
      BubbleView bubble = bubbles.get(i);
      if (bubble.isHiding()) {
        continue;
      }
      if (cx + bubble.getWidth() > maxX) {
        cx = startX;
        cy = cy + bubble.getHeight() + add;
      }
      bubble.setXY((int) cx, (int) cy);
      cx = cx + bubble.getWidth() + add;
    }
  }

  // Animation

  private boolean isAnimating;
  private boolean changingHeight;

  public boolean isAnimating () {
    return isAnimating;
  }

  private int rangeStart;
  private int rangeEnd;

  private float factor;

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      for (int i = rangeStart; i < rangeEnd; i++) {
        bubbles.get(i).setFactor(factor);
      }
      if (changingHeight) {
        headerView.setFactor(factor);
      }
      invalidate();
    }
  }

  public float getFactor () {
    return factor;
  }

  // Touch

  private int caughtIndex = -1;
  private int startX, startY;

  private void clearTouch () {
    bubbles.get(caughtIndex).cancelDeletion();
    caughtIndex = -1;
  }

  private void completeTouch () {
    ViewUtils.onClick(this);
    BubbleView view = bubbles.get(caughtIndex);
    hideAnimated(caughtIndex, true);
    if (headerView.callback != null) {
      headerView.callback.onBubbleRemoved(view.getChatId());
    }
  }

  int deleteIconStroke;
  int deleteIconWidth;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        startX = (int) e.getX();
        startY = (int) e.getY();

        int spacing = (int) ((float) Screen.dp(SPACING) * .5f);

        caughtIndex = -1;

        if (isAnimating) {
          return false;
        }

        for (int i = 0; i < bubbles.size(); i++) {
          BubbleView view = bubbles.get(i);

          int cx = view.getX();
          int cy = view.getY();
          int w = view.getWidth();
          int h = view.getHeight();

          if (Lang.rtl()) {
            cx = getMeasuredWidth() - cx - w;
          }

          if (startX >= cx - spacing && startX < cx + w + spacing && startY >= cy - spacing && startY < cy + h + spacing) {
            caughtIndex = i;
            deleteIconStroke = Screen.dp(1f);
            deleteIconWidth = Screen.dp(7f);
            view.startDeletion();
            break;
          }
        }

        return caughtIndex != -1;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (caughtIndex != -1) {
          clearTouch();
        }
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (caughtIndex != -1) {
          if (Math.abs(startX - e.getX()) > Screen.getTouchSlop() || Math.abs(startY - e.getY()) > Screen.getTouchSlop()) {
            clearTouch();
          }
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        if (caughtIndex != -1) {
          completeTouch();
          return true;
        }
        return false;
      }
      default: {
        return false;
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    for (BubbleView view : bubbles) {
      view.draw(c, this);
    }
  }
}
