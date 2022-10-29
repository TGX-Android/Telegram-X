package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;

public class Icon {
  private final Drawable src;
  private final int width;
  private final int height;

  private int paddingLeft = 0;
  private int paddingTop = 0;
  private int paddingRight = 0;
  private int paddingBottom = 0;

  private int additionalClickHandleWidth;
  private int additionalClickHandleHeight;

  private int x = 0;
  private int y = 0;

  private Runnable onClick;

  public Icon (Drawable src, int width, int height) {
    this.src = src;
    DrawableCompat.setTint(src, Theme.iconColor());
    this.width = width;
    this.height = height;
  }

  public void setPos (int x, int y) {
    this.x = x;
    this.y = y;
    src.setBounds(x, y, x + width, y + height);
  }

  public void setPadding (int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
    this.paddingLeft = paddingLeft;
    this.paddingTop = paddingTop;
    this.paddingRight = paddingRight;
    this.paddingBottom = paddingBottom;
  }

  public void setAdditionalClickHandleSpace (int additionalClickHandleWidth, int additionalClickHandleHeight) {
    this.additionalClickHandleWidth = additionalClickHandleWidth;
    this.additionalClickHandleHeight = additionalClickHandleHeight;
  }

  public void setTint (@ColorInt int color) {
    DrawableCompat.setTint(src, color);
  }

  public void setOnClick (Runnable onClick) {
    this.onClick = onClick;
  }

  private float touchStartX;
  private float touchStartY;

  public boolean onTouchEvent (View view, MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (touchIsInBounds(e.getX(), e.getY())) {
          touchStartX = e.getX();
          touchStartY = e.getY();
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (touchIsInBounds(e.getX(), e.getY())) {
          boolean isClick = isClick(e);
          if (isClick) {
            onClick.run();
            return true;
          }
        }
        break;
      }
    }

    return false;
  }

  private boolean touchIsInBounds (float touchX, float touchY) {
    boolean inXBounds = touchX > x - additionalClickHandleWidth && touchX < x + width + additionalClickHandleWidth;
    boolean inYBounds = touchY > y - additionalClickHandleHeight && touchY < y + height + additionalClickHandleHeight;
    return  inXBounds && inYBounds;
  }

  private boolean isClick (MotionEvent e) {
    boolean xNotChanged = Math.abs(touchStartX - e.getX()) < 10f;
    boolean yNotChanged = Math.abs(touchStartY - e.getY()) < 10f;
    return xNotChanged && yNotChanged;
  }

  public void draw (Canvas c) {
    src.draw(c);
  }

  public int getWidth () {
    return  width;
  }

  public int getHeight () {
    return height;
  }

  public int getPaddingLeft () {
    return paddingLeft;
  }

  public int getPaddingTop () {
    return paddingTop;
  }

  public int getPaddingRight () {
    return paddingRight;
  }

  public int getPaddingBottom () {
    return paddingBottom;
  }

  public int getX () {
    return x;
  }

  public int getY () {
    return y;
  }
}
