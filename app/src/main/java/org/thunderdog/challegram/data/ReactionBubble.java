package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.function.BiConsumer;

import me.vkryl.core.MathUtils;

public class ReactionBubble {
  private static final int paddingLeft = Screen.dp(8f);
  private static final int paddingTop = Screen.dp(8f);
  private static final int paddingRight = Screen.dp(8f);
  private static final int paddingBottom = Screen.dp(8f);

  private static final int marginTop = Screen.dp(4f);
  private static final int marginBottom = Screen.dp(4f);
  private static final int marginLeft = Screen.dp(4f);
  private static final int marginRight = Screen.dp(4f);

  public static final int outMarginTop = Screen.dp(0f);
  public static final int outMarginBottom = Screen.dp(8f);
  public static final int outMarginLeft = Screen.dp(8f);
  public static final int outMarginRight = Screen.dp(8f);

  private static final int reactionSize = Screen.dp(20f);

  private static final int spaceBetweenTextAndReaction = Screen.dp(4f);

  private int id;
  private int count;
  private String reaction;
  private boolean isBig;
  private boolean isChosen;

  private static final int height = Screen.dp(12);
  private int width = Screen.dp(36);

  private final Path path, clipPath;
  private final RectF pathRect, clipPathRect;

  private BiConsumer<String, Boolean> setMessageReaction;

  public ReactionBubble (int id, int count, String reaction, boolean isChosen, boolean isBig, BiConsumer<String, Boolean> setMessageReaction) {
    this.id = id;
    this.count = count;
    this.path = new Path();
    this.pathRect = new RectF();
    this.clipPath = new Path();
    this.clipPathRect = new RectF();
    this.reaction = reaction;
    this.isChosen = isChosen;
    this.isBig = isBig;
    this.setMessageReaction = setMessageReaction;

    adjustWidthWithCount();
  }

  public final int getId () {
    return id;
  }

  public final String getReaction () {
    return reaction;
  }

  private static int getHeight () {
    return height + paddingTop + paddingBottom;
  }

  public static int getHeightWithMargins () {
    return getHeight() + marginTop + marginBottom;
  }

  private int getWidth () {
    return width + paddingLeft + paddingRight;
  }

  public int getWidthWithMargins () {
    return getWidth() + marginLeft + marginRight;
  }

  private void adjustWidthWithCount () {
    if (count > 99) {
      width = Screen.dp(52f);
    } else if (count > 9) {
      width = Screen.dp(44f);
    }
  }

  public void buildBubble (int leftContentEdge, int bottomContentEdge) {
    int bubbleWidth = width;
    int bubbleHeight = height;

    bubbleWidth += paddingLeft + paddingRight;
    bubbleHeight += paddingTop + paddingBottom;

    int rightContentEdge = leftContentEdge + bubbleWidth;
    int topContentEdge = bottomContentEdge - bubbleHeight;

    int rad = height;

    path.reset();
    clipPath.reset();

    synchronized (path) {
      pathRect.set(leftContentEdge, topContentEdge, rightContentEdge, bottomContentEdge);
      DrawAlgorithms.buildPath(path, pathRect, rad, rad, rad, rad);
      clipPathRect.set(leftContentEdge + paddingLeft, topContentEdge + paddingTop, rightContentEdge - paddingRight, bottomContentEdge - paddingBottom);
      DrawAlgorithms.buildPath(clipPath, clipPathRect, rad, rad, rad, rad);
    }
  }


  public void drawBubble (Canvas c, int colorActive, int colorNegative, Paint textPaint, boolean stroke) {
    final int left = (int) pathRect.left;
    final int top = (int) pathRect.top;
    final int right = (int) pathRect.right;
    final int bottom = (int) pathRect.bottom;

    int rad = height;

    // Draw background

    Paint backgroundPaint = isChosen ? Paints.fillingPaint(colorActive) : Paints.fillingPaint(colorNegative);

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

    // Draw text

    String text = String.valueOf(count);
    Rect textBounds = new Rect();
    textPaint.getTextBounds(text, 0, text.length(), textBounds);
    int textHeight = textBounds.height();
    int textWidth = textBounds.width();
    int contentTotalWidth = textWidth + reactionSize + spaceBetweenTextAndReaction;
    int centerX = (left + right) / 2;
    int centerY = (top + bottom) / 2;
    final int textX = centerX + contentTotalWidth / 2 - textWidth;
    final int textY = centerY + textHeight / 2;
    c.drawText(text, textX, textY, textPaint);

    // Draw reaction

    Emoji emojiManager = Emoji.instance();
    EmojiInfo info = emojiManager.getEmojiInfo(reaction);
    int reactionLeft = centerX - contentTotalWidth / 2;
    int reactionRight = reactionLeft + reactionSize;
    int reactionTop = centerY - reactionSize / 2;
    int reactionBottom = reactionTop + reactionSize;
    Rect rect = new Rect(reactionLeft, reactionTop, reactionRight, reactionBottom);
    Emoji.instance().draw(c, info, rect);
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
      onClick();
      view.invalidate();
      return true;
    } else {
      return false;
    }
  }

  private void onClick () {
    String reaction = isChosen ? "" : this.reaction;
    setMessageReaction.accept(reaction, isBig);
    adjustWidthWithCount();
  }

  public void setIsChosen (boolean isChosen) {
    if (this.isChosen == isChosen) return;
    if (isChosen) {
      count++;
    } else {
      count--;
    }
    this.isChosen = isChosen;
  }
}
