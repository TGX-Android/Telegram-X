package org.thunderdog.challegram.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.core.ColorUtils;

/**
 * Date: 10/12/17
 * Author: default
 */

public class RecordLockView extends View {
  public RecordLockView (Context context) {
    super(context);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect(0, 0, view.getMeasuredWidth(), (int) (view.getMeasuredHeight() - Screen.dp(33f) * collapseFactor), Screen.dp(33f) / 2);
        }
      });
    }
    setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(33f), Screen.dp(66f)));
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return Views.onTouchEvent(this, e) && super.onTouchEvent(e);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    setPivotX(getMeasuredWidth() / 2);
    setPivotY(getCenterY());
  }

  private float getCenterY () {
    return (int) (getMeasuredHeight() - Screen.dp(33f) * collapseFactor) - Screen.dp(33f) / 2;
  }

  private float collapseFactor;

  public void setCollapseFactor (float factor) {
    if (this.collapseFactor != factor) {
      this.collapseFactor = factor;
      setPivotY(getCenterY());
      invalidate();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
    }
  }

  private float sendFactor;

  public void setSendFactor (float sendFactor) {
    if (this.sendFactor != sendFactor) {
      this.sendFactor = sendFactor;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    int fillingColor = Theme.fillingColor();

    RectF rectF = Paints.getRectF();
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    rectF.set(0, 0, viewWidth, viewHeight - Screen.dp(33f) * collapseFactor);
    int radius = Screen.dp(33f) / 2;
    c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(fillingColor));

    int bottomCy = (int) rectF.bottom - radius;

    int cx = viewWidth / 2;
    int cy = Screen.dp(33f) / 2;

    final int grayColor = Theme.iconColor();
    final int redColor = Theme.getColor(R.id.theme_color_iconNegative);

    int totalDy = (int) (Screen.dp(2f) * collapseFactor * (1f - sendFactor));

    int width = (int) (Screen.dp(6f) + Screen.dp(2f) * (1f - sendFactor));
    int height = (int) (Screen.dp(6f) + Screen.dp(1f) * (1f - sendFactor));
    int dy = (int) (Screen.dp(33f) /3 * (1f - collapseFactor));
    rectF.set(cx - width, cy - height + dy + totalDy, cx + width, cy + height + dy + totalDy);
    c.drawRoundRect(rectF, Screen.dp(2f), Screen.dp(2f), Paints.fillingPaint(ColorUtils.fromToArgb(grayColor, redColor, sendFactor)));

    if (sendFactor < 1f) {
      c.drawCircle(cx, rectF.centerY(), Screen.dp(2f), Paints.fillingPaint(ColorUtils.alphaColor(1f - sendFactor, fillingColor)));
      dy /= 2;
      rectF.offset(0, -dy);
      Paint paint = Paints.strokeBigPaint(ColorUtils.alphaColor(1f - sendFactor, grayColor));
      rectF.set(cx - Screen.dp(5f), rectF.top - Screen.dp(5f), cx + Screen.dp(5f), rectF.top + Screen.dp(5f));
      c.drawArc(rectF, 180f, 180f, false, paint);
      if (dy > 0) {
        int x = (int) rectF.left;
        int y = (int) rectF.centerY();
        c.drawLine(x, y, x, y + dy, paint);
        c.drawLine(rectF.right, y, rectF.right, y + Math.min(Screen.dp(2f), dy), paint);
      }
    }

    if (collapseFactor < 1f) {
      DrawAlgorithms.drawDirection(c, cx, bottomCy, ColorUtils.alphaColor(1f - (collapseFactor >= .5f ? 1f : collapseFactor / .5f), grayColor), Gravity.TOP);
    }
  }


}
