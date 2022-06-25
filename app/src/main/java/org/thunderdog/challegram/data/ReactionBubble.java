package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.MathUtils;

public class ReactionBubble {
  private int parentWidth;

  private final Path path, clipPath;
  private final RectF pathRect, clipPathRect;

  private final int paddingLeft = Screen.dp(8f);
  private final int paddingTop = Screen.dp(8f);
  private final int paddingRight = Screen.dp(8f);
  private final int paddingBottom = Screen.dp(8f);

  public final int marginTop = Screen.dp(10f);
  public final int marginBottom = Screen.dp(10f);
  public ReactionBubble() {
    this.path = new Path();
    this.pathRect = new RectF();
    this.clipPath = new Path();
    this.clipPathRect = new RectF();
  }

  private int computeBubbleHeight () {
    return Screen.dp(10);
  }

  private int computeBubbleWidth () {
    return Screen.dp(20);
  }

  public int getHeight () {
    return computeBubbleHeight() + paddingTop + paddingBottom;
  }

  public int getHeightWithMargins () {
    return  getHeight() + marginTop + marginBottom;
  }

  public int getWidth () {
    return computeBubbleWidth() + paddingLeft + paddingRight;
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


  public void drawBubble (Canvas c, Paint paint, boolean stroke, int padding) {
    if (paint.getAlpha() == 0) {
      return;
    }

    final float left = pathRect.left - padding;
    final float top = pathRect.top - padding;
    final float right = pathRect.right + padding;
    final float bottom = pathRect.bottom + padding;

    int rad = Screen.dp(computeBubbleHeight() / 2f);

    final RectF rectF = Paints.getRectF();
    if (rad != 0) {
      rectF.set(left, top, left + rad * 2, top + rad * 2);
      c.drawArc(rectF, 180f, 90f, !stroke, paint);
      if (!stroke) {
        c.drawRect(left, top + rad, left + rad, top + rad, paint);
      }
      rectF.set(right - rad * 2, top, right, top + rad * 2);
      c.drawArc(rectF, 270f, 90f, !stroke, paint);
      if (!stroke) {
        c.drawRect(right - rad, top + rad, right, top + rad, paint);
      }
      c.drawRect(left + rad, top, right - rad, top + rad, paint);
      rectF.set(left, bottom - rad * 2, left + rad * 2, bottom);
      c.drawArc(rectF, 90f, 90f, !stroke, paint);
      if (!stroke) {
        c.drawRect(left, bottom - rad, left + rad, bottom - rad, paint);
      }
      rectF.set(right - rad * 2, bottom - rad * 2, right, bottom);
      c.drawArc(rectF, 0f, 90f, !stroke, paint);
      if (!stroke) {
        c.drawRect(right - rad, bottom - rad, right, bottom - rad, paint);
      }
      c.drawRect(left + rad, bottom - rad, right - rad, bottom, paint);
    }
    if (stroke) {
      c.drawLine(left + rad, top, right - rad, top, paint);
      c.drawLine(left + rad, bottom, right - rad, bottom, paint);
      c.drawLine(left, top + rad, left, bottom - rad, paint);
      c.drawLine(right, top + rad, right, bottom - rad, paint);
    } else {
      float bubbleTop = top + rad;
      float bubbleBottom = bottom - rad;
      if (bubbleBottom > bubbleTop)
        c.drawRect(left, bubbleTop, right, bubbleBottom, paint);
    }
  }
}
