/**
 * File created on 13/02/16 at 12:53
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

public class BackHeaderButton extends HeaderButton implements View.OnClickListener {
  public static final int TYPE_NONE = 1;
  public static final int TYPE_MENU = 2;
  public static final int TYPE_BACK = 3;
  public static final int TYPE_CLOSE = 4;

  private boolean reverseAngle;
  private float factor;

  private HeaderView parentHeader;
  private final Paint paint;

  public BackHeaderButton (Context context) {
    super(context);

    paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setStrokeWidth(Screen.dp(2f));
    paint.setColor(Theme.headerBackColor());
  }

  private int color;

  public void setColor (int color) {
    if (this.color != color) {
      this.color = color;
      paint.setColor(color);
      invalidate();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (parentHeader != null && e.getAction() == MotionEvent.ACTION_DOWN) {
      parentHeader.onBackTouchDown(e);
    }
    return super.onTouchEvent(e);
  }

  public int getColor () {
    return color;
  }

  public void setParentHeader (HeaderView parent) {
    this.parentHeader = parent;
  }

  public void setIsReverse (boolean isReverse) {
    this.reverseAngle = isReverse;
  }

  public float getFactor () {
    return factor;
  }

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  public static float toButtonFactor (int type) {
    switch (type) {
      case BackHeaderButton.TYPE_NONE:
      case BackHeaderButton.TYPE_MENU:
        return 0f;
      case BackHeaderButton.TYPE_BACK:
        return 1f;
      case BackHeaderButton.TYPE_CLOSE:
        return 2f;
    }
    return 0f;
  }

  public void setButtonFactor (int type) {
    if (type != BackHeaderButton.TYPE_NONE) {
      setFactor(toButtonFactor(type));
    }
  }

  @Override
  public void onClick (View view) {
    if (parentHeader != null) {
      if (parentHeader.inSelectMode()) {
        parentHeader.closeSelectMode(true, true);
        return;
      }
      if (parentHeader.inSearchMode()) {
        parentHeader.closeSearchMode(true, null);
        return;
      }
    }
    BaseActivity context = UI.getContext(getContext());
    if (context != null) {
      if (this.factor == 0f) { // Menu button
        if (context.getDrawer() != null && !context.getDrawer().isVisible()) {
          context.getDrawer().open();
        }
      } else {
        context.onBackPressed(true);
      }
    }
  }

  @Override
  public void onDraw (Canvas c) {
    super.onDraw(c);

    boolean reverseAngle = Lang.rtl() != this.reverseAngle;

    c.save();
    c.translate(getPaddingLeft() + getMeasuredWidth() / 2, getMeasuredHeight() / 2);
    float scale = 1f - Math.min(factor, 1f) * .12f;
    c.scale(scale, scale);
    int _9 = Screen.dp(9f);
    int _85 = Screen.dp(8.5f);
    int _5 = Screen.dp(5f);
    int _35 = Screen.dp(3.5f);
    int _05 = Screen.dp(0.5f);
    int _1 = Screen.dp(1f);

    if (factor <= 1f) {
      c.rotate(factor * (reverseAngle ? -180f : 180f));
      c.drawLine(-_9, 0, _9 - _1 * factor, 0, paint);

      final float startY = _5 + _35 * Math.abs(factor);
      final float endY = _5 * (1 - Math.abs(factor)) - _05 * Math.abs(factor);

      float startX, endX;
      if (Lang.rtl()) {
        startX = _9 - _85 * Math.abs(factor);
        endX = -_9 + _05 * Math.abs(factor);
      } else {
        startX = -_9 + _85 * Math.abs(factor);
        endX = _9 - _05 * Math.abs(factor);
      }

      c.drawLine(startX, -startY, endX, -endY, paint);
      c.drawLine(startX, startY, endX, endY, paint);
    } else {
      float factor = this.factor - 1f;

      c.rotate((reverseAngle ? 180 + (factor * -135f) : 180f + (factor * 135f)) * (Lang.rtl() ? -1 : 1));

      c.drawLine(-_9, 0, _9 - _1, 0, paint);

      float xDiff = (_9 - _05 + _9 - _85) * factor;

      if (Lang.rtl()) {
        float startX, endX;
        startX = _9 - _85;
        endX = -_9 + _05;

        final float startY = _5 + _35;
        final float endY = -_05;

        c.drawLine(startX, -startY, endX + xDiff, -endY, paint);
        c.drawLine(startX, startY, endX + xDiff, endY, paint);
      } else {
        c.drawLine(-_9 + _85, -_5 - _35, _9 - _05 - xDiff, _05, paint);
        c.drawLine(-_9 + _85, _5 + _35,  _9 - _05 - xDiff, -_05, paint);
      }
    }

    c.restore();
  }
}
