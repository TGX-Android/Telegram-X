/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/12/2019
 */
package org.thunderdog.challegram.component.chat;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.SimpleDrawable;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.ColorUtils;

public class ChatBottomBarView extends BaseView {
  private Drawable drawable;

  public ChatBottomBarView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    Views.setClickable(this);
    Drawable drawable = new SimpleDrawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        RectF rectF = buildRectF();
        int radius = calculateRadius();
        int color = ColorUtils.fromToArgb(Theme.fillingColor(), Theme.getColor(R.id.theme_color_circleButtonChat), collapseFactor);
        if (radius == 0) {
          c.drawRect(rectF.left, rectF.top, rectF.right, rectF.bottom, Paints.fillingPaint(color));
        } else {
          c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
        }
      }
    };
    Drawable legacyPressedDrawable = new SimpleDrawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        RectF rectF = buildRectF();
        int radius = calculateRadius();
        int color = Theme.getColor(R.id.theme_color_fillingPressed);
        if (radius == 0) {
          c.drawRect(rectF.left, rectF.top, rectF.right, rectF.bottom, Paints.fillingPaint(color));
        } else {
          c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
        }
      }
    };
    ViewUtils.setBackground(this, this.drawable = Theme.customSelector(drawable, legacyPressedDrawable));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          RectF rectF = buildRectF();
          int radius = calculateRadius();
          if (radius == 0) {
            outline.setRect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
          } else {
            outline.setRoundRect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom, radius);
          }
        }
      });
      Views.setSimpleStateListAnimator(this);
    }
  }

  private int calculateRadius () {
    return (int) (Screen.dp(48f) / 2f * collapseFactor);
  }

  private RectF buildRectF () {
    int fromWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    int fromHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    int cx = getPaddingLeft() + fromWidth / 2;
    int cy = getPaddingTop() + fromHeight / 2;
    int toSize = Screen.dp(48f);
    int width = fromWidth + (int) ((float) (toSize - fromWidth) * collapseFactor);
    int height = fromHeight + (int) ((float) (toSize - fromHeight) * collapseFactor);
    RectF rectF = Paints.getRectF();
    rectF.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2);
    return rectF;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      RectF rectF = buildRectF();
      float x = e.getX();
      float y = e.getY();
      if (x < rectF.left || x > rectF.right || y < rectF.top || y > rectF.bottom) {
        return false;
      }
    }
    return super.onTouchEvent(e);
  }

  private static class State {
    private String text;
    private int iconRes;

    private float factor;
    private State prevState;

    private Drawable drawable;
    private Text drawingText;

    public State (String text, @DrawableRes int iconRes) {
      this.text = text;
      this.iconRes = iconRes;
      this.drawable = Drawables.get(iconRes);
    }

    public void layout (int width) {
      width -= Screen.dp(8f) * 2;
      this.drawingText = width > 0 ? new Text.Builder(this.text.toUpperCase(), width - Screen.dp(8f), Paints.robotoStyleProvider(16), TextColorSets.Regular.NEUTRAL).allBold().singleLine().build() : null;
    }

    private static final float SCALE = .8f;

    public void draw (Canvas c, View view, float collapseFactor, float factor) {
      int cx = view.getPaddingLeft() + (view.getMeasuredWidth() - view.getPaddingRight() - view.getPaddingLeft()) / 2;
      int cy = view.getPaddingTop() + (view.getMeasuredHeight() - view.getPaddingBottom() - view.getPaddingTop()) / 2;

      if (prevState != null) {
        c.save();
        float displayFactor = 1f - this.factor;
        float scale = SCALE + (1f - SCALE) * displayFactor;
        c.scale(scale, scale, cx, cy);
        prevState.draw(c, view, collapseFactor, displayFactor);
        c.restore();
      }
      factor *= this.factor;
      final int saveCount;
      final boolean needScale = factor != 1f;
      if (needScale) {
        saveCount = Views.save(c);
        float scale = SCALE + (1f - SCALE) * factor;
        c.scale(scale, scale, cx, cy);
      } else {
        saveCount = -1;
      }
      if (drawingText != null && collapseFactor < 1f) {
        drawingText.draw(c, cx - drawingText.getWidth() / 2, cy - drawingText.getHeight() / 2, null, factor * (1f - collapseFactor));
      }
      if (collapseFactor > 0f && drawable != null) {
        Paint paint = Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_circleButtonChatIcon));
        final int restoreAlpha = paint.getAlpha();
        paint.setAlpha((int) ((float) restoreAlpha * factor * collapseFactor));
        Drawables.draw(c, drawable, cx - drawable.getMinimumWidth() / 2, cy - drawable.getMinimumHeight() / 2, paint);
        paint.setAlpha(restoreAlpha);
      }
      if (needScale) {
        Views.restore(c, saveCount);
      }
    }
  }

  private State state;
  private final BoolAnimator replaceAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    if (this.state != null) {
      this.state.factor = factor;
      if (factor == 1f) {
        this.state.prevState = null;
      }
      invalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setAction (int id, String text, @DrawableRes int iconRes, boolean animated) {
    if (this.state != null && this.state.text.equals(text) && this.state.iconRes == iconRes) {
      return;
    }
    setId(id);

    State newState = new State(text, iconRes);
    newState.layout(getMeasuredWidth());

    if (!animated || this.state == null) {
      replaceAnimator.setValue(false, false);
      this.state = newState;
      this.state.factor = 1f;
      invalidate();
      return;
    }

    State prevState = this.state;
    this.state = null;
    replaceAnimator.setValue(false, false);
    newState.prevState = prevState;
    this.state = newState;
    replaceAnimator.setValue(true, true);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (state != null) {
      state.layout(getMeasuredWidth());
    }
  }

  private float collapseFactor;

  public void setCollapseFactor (float collapseFactor) {
    if (this.collapseFactor != collapseFactor) {
      this.collapseFactor = collapseFactor;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
      drawable.invalidateSelf();
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (state != null) {
      RectF rectF = buildRectF();
      c.save();
      c.clipRect(rectF.left, rectF.top, rectF.right, rectF.bottom);
      state.draw(c, this, collapseFactor, 1f);
      c.restore();
    }
  }
}
