package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.MathUtils;

public class ReactionBubble {
  private static final int paddingLeft = Screen.dp(8f);
  private static final int paddingTop = Screen.dp(8f);
  private static final int paddingRight = Screen.dp(8f);
  private static final int paddingBottom = Screen.dp(8f);

  private static final int marginTop = Screen.dp(5f);
  private static final int marginBottom = Screen.dp(5f);
  private static final int marginLeft = Screen.dp(5f);
  private static final int marginRight = Screen.dp(5f);

  public static final int outMarginTop = Screen.dp(5f);
  public static final int outMarginBottom = Screen.dp(5f);
  public static final int outMarginLeft = Screen.dp(10f);
  public static final int outMarginRight = Screen.dp(10f);

  private int id;

  private final Path path, clipPath;
  private final RectF pathRect, clipPathRect;

  private int count = 0;

  public ReactionBubble(int id) {
    this.id = id;
    this.path = new Path();
    this.pathRect = new RectF();
    this.clipPath = new Path();
    this.clipPathRect = new RectF();
  }

  private static int computeBubbleHeight () {
    return Screen.dp(10);
  }

  private static int computeBubbleWidth () {
    return Screen.dp(20);
  }

  public static int getHeight () {
    return computeBubbleHeight() + paddingTop + paddingBottom;
  }

  public static int getHeightWithMargins () {
    return  getHeight() + marginTop + marginBottom;
  }

  public static int getWidth () {
    return computeBubbleWidth() + paddingLeft + paddingRight;
  }

  public static int getWidthWithMargins () {
    return getWidth() + marginLeft + marginRight;
  }

  public void buildBubble (float leftContentEdge,  float bottomContentEdge) {
    int bubbleWidth = computeBubbleWidth();
    int bubbleHeight = computeBubbleHeight();

    bubbleWidth += paddingLeft + paddingRight;
    bubbleHeight += paddingTop + paddingBottom;

    float rightContentEdge = leftContentEdge + bubbleWidth;
    float topContentEdge = bottomContentEdge - bubbleHeight;

    int rad = Screen.dp(computeBubbleHeight() / 2f);

    path.reset();
    clipPath.reset();

    synchronized (path) {
      pathRect.set(leftContentEdge, topContentEdge, rightContentEdge, bottomContentEdge);
      DrawAlgorithms.buildPath(path, pathRect, rad, rad, rad, rad);
      clipPathRect.set(leftContentEdge + paddingLeft, topContentEdge + paddingTop, rightContentEdge - paddingRight, bottomContentEdge - paddingBottom);
      DrawAlgorithms.buildPath(clipPath, clipPathRect, rad, rad, rad, rad);
    }
  }


  public void drawBubble (Canvas c, Paint backgroundPaint, Paint textPaint, boolean stroke) {
    final float left = pathRect.left;
    final float top = pathRect.top;
    final float right = pathRect.right;
    final float bottom = pathRect.bottom;

    int rad = Screen.dp(computeBubbleHeight() / 2f);

    final RectF rectF = Paints.getRectF();
    if (rad != 0) {
      rectF.set(left, top, left + rad * 2, top + rad * 2);
      c.drawArc(rectF, 180f, 90f, !stroke, backgroundPaint);
      if (!stroke) {
        c.drawRect(left, top + rad, left + rad, top + rad, backgroundPaint);
      }
      rectF.set(right - rad * 2, top, right, top + rad * 2);
      c.drawArc(rectF, 270f, 90f, !stroke, backgroundPaint);
      if (!stroke) {
        c.drawRect(right - rad, top + rad, right, top + rad, backgroundPaint);
      }
      c.drawRect(left + rad, top, right - rad, top + rad, backgroundPaint);
      rectF.set(left, bottom - rad * 2, left + rad * 2, bottom);
      c.drawArc(rectF, 90f, 90f, !stroke, backgroundPaint);
      if (!stroke) {
        c.drawRect(left, bottom - rad, left + rad, bottom - rad, backgroundPaint);
      }
      rectF.set(right - rad * 2, bottom - rad * 2, right, bottom);
      c.drawArc(rectF, 0f, 90f, !stroke, backgroundPaint);
      if (!stroke) {
        c.drawRect(right - rad, bottom - rad, right, bottom - rad, backgroundPaint);
      }
      c.drawRect(left + rad, bottom - rad, right - rad, bottom, backgroundPaint);
    }
    if (stroke) {
      c.drawLine(left + rad, top, right - rad, top, backgroundPaint);
      c.drawLine(left + rad, bottom, right - rad, bottom, backgroundPaint);
      c.drawLine(left, top + rad, left, bottom - rad, backgroundPaint);
      c.drawLine(right, top + rad, right, bottom - rad, backgroundPaint);
    } else {
      float bubbleTop = top + rad;
      float bubbleBottom = bottom - rad;
      if (bubbleBottom > bubbleTop)
        c.drawRect(left, bubbleTop, right, bubbleBottom, backgroundPaint);
    }

    String text = String.valueOf(count);
    Rect textBounds = new Rect();
    textPaint.getTextBounds(text, 0, text.length(), textBounds);
    int textHeight = textBounds.height();
    int textWidth = textBounds.width();
    final float textX = (left + right) / 2 - textWidth / 2f;
    final float textY = (top + bottom) / 2 + textHeight / 2f;
    c.drawText(text, textX, textY, textPaint);
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    if (e.getAction() != MotionEvent.ACTION_DOWN) return false;
    float touchX = e.getX();
    float touchY = e.getY();

    final float left = pathRect.left;
    final float top = pathRect.top;
    final float right = pathRect.right;
    final float bottom = pathRect.bottom;
    if (touchX > left && touchX < right && touchY > top && touchY < bottom) {
      count++;
      view.invalidate();
      return true;
    } else {
      return false;
    }
  }
}
