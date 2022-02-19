/**
 * File created on 02/05/15 at 13:03
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.BitwiseUtils;

public class TextPart {
  private static final int FLAG_LINE_RTL = 1;
  private static final int FLAG_LINE_RTL_FAKE = 1 << 2;
  private static final int FLAG_ANIMATED_EMOJI = 1 << 3;

  private Text source;
  private String line;
  private @Nullable TextEntity entity;

  private int flags;
  private int x, y;
  private int start, end;
  private float width;
  private int height = -1;

  private final int lineIndex, paragraphIndex;

  public TextPart (Text source, String line, int start, int end, int lineIndex, int paragraphIndex) {
    this.source = source;
    this.line = line;
    this.start = start;
    this.end = end;
    this.lineIndex = lineIndex;
    this.paragraphIndex = paragraphIndex;
  }

  public TooltipOverlayView.TooltipBuilder newTooltipBuilder (View view) {
    return UI.getContext(view.getContext()).tooltipManager()
      .builder(view, source.getViewProvider())
      .locate((targetView, outRect) -> source.locatePart(outRect, this, TextEntity.COMPARE_MODE_NORMAL));
  }

  public void setLine (String line, int start, int end) {
    this.line = line;
    this.start = start;
    this.end = end;
  }

  public void setXY (int x, int y) {
    this.x = x;
    this.y = y;
  }

  public boolean isRtl () {
    return (flags & FLAG_LINE_RTL) != 0 || (flags & FLAG_LINE_RTL_FAKE) != 0;
  }

  public void setAnimateEmoji (boolean animate) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_ANIMATED_EMOJI, animate);
  }

  public void setWidth (float width) {
    this.width = width;
  }

  public void setHeight (int height) {
    this.height = height;
  }

  public void setEntity (@Nullable TextEntity entity) {
    this.entity = entity;
  }

  public @Nullable TextEntity getEntity () {
    return entity;
  }

  public float getWidth () {
    return trimmedLine != null ? trimmedWidth : width;
  }

  public int getHeight () {
    return height;
  }

  public int getX () {
    return x;
  }

  public int getY () {
    return y;
  }

  public String getLine () {
    return line;
  }

  public String getLinePart () {
    return line.substring(start, end);
  }

  public int getLineIndex () {
    return lineIndex;
  }

  public int getParagraphIndex () {
    return paragraphIndex;
  }

  public int getStart () {
    return start;
  }

  public int getEnd () {
    return end;
  }

  public boolean isSameEntity (@Nullable TextEntity entity) {
    return TextEntity.equals(this.entity, entity, TextEntity.COMPARE_MODE_NORMAL, null);
  }

  public boolean isSamePressHighlight (@NonNull TextEntity entity) {
    return TextEntity.equals(this.entity, entity, TextEntity.COMPARE_MODE_CLICK_HIGHLIGHT, null);
  }

  public boolean isSameSpoiler (@NonNull TextEntity entity) {
    return TextEntity.equals(this.entity, entity, TextEntity.COMPARE_MODE_SPOILER, source.getText());
  }

  public boolean isWhitespace () {
    return entity == null && (this.end - this.start) == Strings.countCharacters(line, start, end, (c) -> c == ' ');
  }

  public boolean isEssential () {
    return emojiInfo == null && (this.entity == null || this.entity.isEssential());
  }

  public void setPartDirection (int direction, boolean fake) {
    int flags = this.flags;
    /*switch (direction) {
      case Strings.DIRECTION_LTR:
        flags &= ~FLAG_DIRECTION_RTL;
        flags |= FLAG_DIRECTION_LTR;
        break;
      case Strings.DIRECTION_RTL:
        flags |= FLAG_DIRECTION_RTL;
        flags &= ~FLAG_DIRECTION_LTR;
        break;
      case Strings.DIRECTION_NEUTRAL:
        flags &= ~FLAG_DIRECTION_LTR;
        flags &= ~FLAG_DIRECTION_RTL;
        break;
    }*/
    // flags = U.setFlag(flags, )
    // flags = U.setFlag(flags, FLAG_LINE_RTL_FAKE, fake);
    this.flags = flags;
  }

  public void setRtlMode (boolean isRtl, boolean fake) {
    int flags = this.flags;
    flags = BitwiseUtils.setFlag(flags, FLAG_LINE_RTL, isRtl);
    flags = BitwiseUtils.setFlag(flags, FLAG_LINE_RTL_FAKE, fake);
    this.flags = flags;
  }

  public void setEnd (int end) {
    if (end < start) {
      throw new RuntimeException("invalid");
    }
    if (this.end != end) {
      this.end = end;
      if (trimmedLine != null) {
        trimContents(trimmedMaxWidth);
      }
    }
  }

  private String trimmedLine;
  private float trimmedWidth;
  private float trimmedMaxWidth;

  public void trimContents (float realMaxWidth) {
    this.trimmedMaxWidth = realMaxWidth;
    TextPaint paint = source.getTextPaint(entity);
    int ellipsis = (int) U.measureText("…", paint);
    int maxWidth = (int) realMaxWidth - ellipsis - x;
    trimmedLine = line.substring(start, end);
    trimmedLine = TextUtils.ellipsize(trimmedLine, paint, maxWidth, TextUtils.TruncateAt.END).toString();
    trimmedWidth = U.measureText(trimmedLine, paint);
    if (!trimmedLine.endsWith("…")) {
      trimmedLine = trimmedLine + "…";
      trimmedWidth += ellipsis;
    }
  }

  public boolean isClickable () {
    return entity != null && entity.isClickable();
  }

  public TdApi.TextEntity getSpoiler () {
    return entity != null ? entity.getSpoiler() : null;
  }

  public @Nullable TextEntity getClickableEntity () {
    return isClickable() ? entity : null;
  }

  public @Nullable TextEntity getSpoilerEntity () {
    return getSpoiler() != null ? entity : null;
  }

  private EmojiInfo emojiInfo;

  public void setEmoji (EmojiInfo emoji) {
    this.emojiInfo = emoji;
  }

  private int iconIndex;
  private TextIcon icon;

  public void setIcon (int iconIndex, TextIcon icon) {
    this.icon = icon;
    this.iconIndex = iconIndex;
  }

  public boolean isEmoji () {
    return emojiInfo != null;
  }

  public boolean isIcon () {
    return icon != null;
  }

  void requestIcon (ComplexReceiver receiver, int iconKeyOffset) {
    icon.requestFiles(iconKeyOffset + iconIndex, receiver);
  }

  public int makeX (int startX, int endX, int endXBottomPadding) {
    int x;
    int startXPadding, endXPadding;
    int maxWidth = source.getLineMaxWidth(lineIndex, y);
    int defaultMaxWidth = source.getMaxWidth();
    int lineStartMargin = source.getLineStartMargin(lineIndex, y);
    int lineEndMargin = maxWidth == defaultMaxWidth ? 0 : defaultMaxWidth - maxWidth + lineStartMargin;
    if (Lang.rtl()) {
      startXPadding = lineEndMargin;
      endXPadding = lineStartMargin;
    } else {
      startXPadding = lineStartMargin;
      endXPadding = lineEndMargin;
    }
    boolean rtl = (flags & FLAG_LINE_RTL) != 0;
    if ((rtl || source.alignRight()) && endX != startX) {
      if (!rtl || (flags & FLAG_LINE_RTL_FAKE) != 0) {
        int lineWidth = source.getLineWidth(lineIndex);
        x = endX - lineWidth + this.x - endXPadding;
      } else {
        x = endX - this.x - (int) this.width - endXPadding;
      }
      if (lineIndex + 1 == source.getLineCount()) {
        x -= endXBottomPadding;
      }
    } else {
      x = startX + this.x + startXPadding;
    }
    return x;
  }

  public boolean wouldMergeWithNextPart (TextPart part) {
    return part != null && part != this && emojiInfo == null && part.emojiInfo == null && icon == null && part.icon == null && trimmedLine == null && part.trimmedLine == null && this.y == part.y && line == part.line && end == part.start && isSameEntity(part.entity);
  }

  private TextPaint getPaint (int partIndex, float alpha, @Nullable TextColorSet defaultTheme) {
    TextPaint textPaint = source.getTextPaint(entity);
    int textColor = source.getTextColor(defaultTheme, entity, source.isClickable(entity), source.isPressed(partIndex));
    textPaint.setColor(ColorUtils.alphaColor(alpha, textColor));
    return source.modifyTextPaint(textPaint);
  }

  public void drawMerged (int partIndex, Canvas c, int end, int startX, int endX, int endXBottomPadding, int startY, float alpha, @Nullable TextColorSet colorProvider) {
    int y = startY + this.y;
    int x = makeX(startX, endX, endXBottomPadding);
    if (icon != null)
      throw new IllegalStateException("icon != null");
    if (emojiInfo != null)
      throw new IllegalStateException("emojiInfo != null");
    if (trimmedLine != null)
      throw new IllegalStateException("trimmedLine != null");
    TextPaint paint = getPaint(partIndex, alpha, colorProvider);
    c.drawText(line, start, end, x, y + source.getAscent() + paint.baselineShift, paint);
  }

  public void draw (int partIndex, Canvas c, int startX, int endX, int endXBottomPadding, int startY, float alpha, @Nullable TextColorSet colorProvider, @Nullable ComplexReceiver receiver, int iconKeyOffset) {
    int y = startY + this.y;
    int x = makeX(startX, endX, endXBottomPadding);
    TextPaint textPaint = getPaint(partIndex, alpha, colorProvider);
    if (icon != null) {
      y += textPaint.baselineShift;
      if (receiver != null) {
        Receiver content;
        int key = iconKeyOffset + iconIndex;
        if (icon.isImage()) {
          ImageReceiver image = receiver.getImageReceiver(key);
          image.setBounds(x, y, (int) (x + width), y + height);
          image.setPaintAlpha(image.getPaintAlpha() * alpha);
          content = image;
        } else if (icon.isGif()) {
          GifReceiver gif = receiver.getGifReceiver(key);
          gif.setBounds(x, y, (int) (x + width), y + height);
          gif.setAlpha(alpha);
          content = gif;
        } else {
          content = null;
        }
        if (content == null || content.needPlaceholder()) {
          DoubleImageReceiver preview = receiver.getPreviewReceiver(key);
          preview.setBounds(x, y, (int) (x + width), y + height);
          preview.setPaintAlpha(alpha);
          preview.draw(c);
          preview.restorePaintAlpha();
        }
        if (content != null) {
          content.draw(c);
          if (icon.isImage()) {
            content.restorePaintAlpha();
          } else {
            // ((GifReceiver) content).setAlpha(1f);
          }
        }
      } else {
        c.drawRect(x, y, x + width, y + height, Paints.fillingPaint(ColorUtils.color((int) (255f * alpha), 0xff0000)));
      }
    } else if (emojiInfo != null) {
      Rect rect = Paints.getRect();
      int reduce = Emoji.instance().getReduceSize();
      y -= Screen.dp(1.5f);
      y += textPaint.baselineShift;
      rect.set(x + reduce / 2, y + reduce / 2, x + (int) width - reduce / 2 - reduce % 2, y + (int) width - reduce / 2 - reduce % 2);
      Emoji.instance().draw(c, emojiInfo, rect, textPaint.getAlpha());
    } else {
      y += source.getAscent();
      if (trimmedLine != null) {
        c.drawText(trimmedLine, x, y + textPaint.baselineShift, textPaint);
      } else {
        c.drawText(line, start, end, x, y + textPaint.baselineShift, textPaint);
      }
    }
  }
}
