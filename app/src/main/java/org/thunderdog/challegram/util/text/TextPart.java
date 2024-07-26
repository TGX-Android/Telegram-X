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
 * File created on 02/05/2015 at 13:03
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.bidi.BiDiEntity;
import org.thunderdog.challegram.util.text.bidi.BiDiUtils;

import me.vkryl.core.ColorUtils;

public class TextPart {
  private final Text source;
  private String line;
  private @Nullable TextEntity entity;

  private int x, y;
  private int start, end;
  private float width;
  private int height = -1;
  private @BiDiEntity int bidiEntity;

  private int lineIndex, paragraphIndex;

  public TextPart (Text source, String line, int start, int end, int lineIndex, int paragraphIndex) {
    this.source = source;
    this.line = line;
    this.start = start;
    this.end = end;
    this.lineIndex = lineIndex;
    this.paragraphIndex = paragraphIndex;
  }

  public TooltipOverlayView.TooltipBuilder newTooltipBuilder (View view) {
    if (source.isDestroyed())
      throw new IllegalStateException();
    return UI.getContext(view.getContext()).tooltipManager()
      .builder(view, source.getViewProvider())
      .locate((targetView, outRect) -> source.locatePart(outRect, this, TextEntity.COMPARE_MODE_NORMAL));
  }

  public void setLine (String line, int start, int end) {
    this.line = line;
    this.start = start;
    this.end = end;
  }

  public void setLineIndex (int lineIndex, int paragraphIndex) {
    this.lineIndex = lineIndex;
    this.paragraphIndex = paragraphIndex;
  }

  public void setXY (int x, int y) {
    this.x = x;
    this.y = y;
  }

  public boolean isRtl () {
    return BiDiUtils.isParagraphRtl(bidiEntity);
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

  public void setBidiEntity (@BiDiEntity int bidiEntity) {
    this.bidiEntity = bidiEntity;
  }

  public @BiDiEntity int getBidiEntity () {
    return bidiEntity;
  }

  public @Nullable TextEntity getEntity () {
    return entity;
  }

  public float getWidth () {
    return width;
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

  public void setEnd (int end) {
    if (end < start) {
      throw new RuntimeException("invalid");
    }
    if (this.end != end) {
      this.end = end;
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

  private @Nullable TextMedia media;
  private EmojiInfo emojiInfo;

  public void setEmoji (@Nullable EmojiInfo emoji) {
    this.emojiInfo = emoji;
  }

  @Nullable
  TextMedia getMedia () {
    return media;
  }

  public boolean isStaticElement () { // Media cannot be trimmed
    return isRecognizedEmoji() || isCustomEmoji() || hasMedia();
  }

  public boolean isCustomEmoji () {
    return entity != null && entity.isCustomEmoji();
  }

  public boolean requiresTopLayer () {
    return media != null && media.isAnimatedCustomEmoji();
  }

  public void attachToMedia (@NonNull TextMedia media) {
    media.attachedToParts.add(this);
    this.media = media;
  }

  public boolean isRecognizedEmoji () {
    return emojiInfo != null;
  }

  public boolean isBuiltInEmoji () {
    return isRecognizedEmoji() && !isCustomEmoji();
  }

  public boolean hasMedia () {
    // true for both custom emoji & RichTextIcon
    return media != null;
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
    boolean rtl = isRtl();
    if ((rtl || source.alignRight()) && endX != startX) {
      int lineWidth = source.getLineWidth(lineIndex);
      x = endX - lineWidth + this.x - endXPadding;
      if (lineIndex + 1 == source.getLineCount()) {
        x -= endXBottomPadding;
      }
    } else {
      x = startX + this.x + startXPadding;
    }
    return x;
  }

  public boolean wouldMergeWithNextPart (TextPart part) {
    return part != null && part != this && emojiInfo == null && part.emojiInfo == null && media == null && part.media == null && this.y == part.y && line == part.line && end == part.start && isSameEntity(part.entity) && bidiEntity == part.bidiEntity && requiresTopLayer() == part.requiresTopLayer();
  }

  @NonNull
  Text getSource () {
    return source;
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
    if (isStaticElement())
      throw new IllegalStateException("static elements can't be merged");
    TextPaint paint = getPaint(partIndex, alpha, colorProvider);
    final float textSize = paint.getTextSize();
    final int textY = y + source.getAscent(textSize) + paint.baselineShift;
    c.drawText(line, start, end, x, textY, paint);
  }

  private void drawError (Canvas c, float cx, float cy, float radius, float alpha, int color) {
    c.drawCircle(cx, cy, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha * .45f, color)));
  }

  private void drawEmoji (Canvas c, final int x, final int y, TextPaint textPaint, float alpha) {
    Rect rect = Paints.getRect();
    int reduce = Emoji.instance().getReduceSize();
    final int emojiY = y + textPaint.baselineShift - Screen.dp(1.5f);
    rect.set(x + reduce / 2, emojiY + reduce / 2, x + (int) width - reduce / 2 - reduce % 2, emojiY + (int) width - reduce / 2 - reduce % 2);
    Emoji.instance().draw(c, emojiInfo, rect, (int) (alpha * textPaint.getAlpha()));
  }

  private static final int DRAW_BATCH_LEVEL_LOCAL = 1;

  public @Nullable GifReceiver findTargetReceiver (@Nullable ComplexReceiver receiver) {
    if (receiver != null && media != null && !media.isNotFoundCustomEmoji() && media.isAnimated()) {
      final long displayMediaKey = media.getDisplayMediaKey();
      return receiver.getGifReceiver(displayMediaKey);
    }
    return null;
  }

  public void beginDrawBatch (@Nullable ComplexReceiver receiver, int externalBatchId) {
    if (externalBatchId <= 0)
      throw new IllegalArgumentException(Integer.toString(externalBatchId));
    GifReceiver gifReceiver = findTargetReceiver(receiver);
    if (gifReceiver != null) {
      gifReceiver.beginDrawBatch(DRAW_BATCH_LEVEL_LOCAL + externalBatchId);
    }
  }

  public void finishAllDrawBatches (@Nullable ComplexReceiver receiver) {
    finishDrawBatch(receiver, 0);
  }

  public void finishDrawBatch (@Nullable ComplexReceiver receiver, int externalBatchId) {
    if (externalBatchId < 0) {
      throw new IllegalArgumentException(Integer.toString(externalBatchId));
    }
    GifReceiver gifReceiver = findTargetReceiver(receiver);
    if (gifReceiver != null) {
      if (externalBatchId == 0) {
        gifReceiver.finishAllDrawBatches();
      } else {
        gifReceiver.finishDrawBatch(DRAW_BATCH_LEVEL_LOCAL + externalBatchId);
      }
    }
  }

  public void draw (int partIndex, Canvas c, int startX, int endX, int endXBottomPadding, int startY, float alpha, @Nullable TextColorSet colorProvider, @Nullable ComplexReceiver receiver) {
    final int y = startY + this.y;
    final int x = makeX(startX, endX, endXBottomPadding);
    final TextPaint textPaint = getPaint(partIndex, alpha, colorProvider);
    final float textSize = textPaint.getTextSize();
    final float textAlpha = textPaint.getAlpha() / 255f;
    if (media != null) {
      if (media.isNotFoundCustomEmoji()) {
        if (emojiInfo != null) {
          drawEmoji(c, x, y, textPaint, alpha);
        } else {
          drawError(c, x + height / 2f, y + height / 2f, height / 2f, alpha, 0xffff0000);
        }
        return;
      }
      final long displayMediaKey = media.getDisplayMediaKey();
      final int iconY = y + textPaint.baselineShift - (isCustomEmoji() ? Screen.dp(1.5f) : 0);
      final int height = this.height == -1 ? (int) width : this.height;
      if (receiver != null && displayMediaKey != -1) {
        final boolean needTranslate = media.attachedToParts.size() > 1;
        final boolean isFirst = needTranslate && media.attachedToParts.get(0) == this;
        final boolean isLast = needTranslate && media.attachedToParts.get(media.attachedToParts.size() - 1) == this;
        int left, top, right, bottom, restoreToCount;
        if (needTranslate) {
          left = 0; top = 0;
          right = (int) width;
          bottom = height;
          restoreToCount = Views.save(c);
          c.translate(x, iconY);
          if (isFirst && media.isAnimated()) {
            receiver.getGifReceiver(displayMediaKey).beginDrawBatch(DRAW_BATCH_LEVEL_LOCAL);
          }
        } else {
          left = x;
          top = iconY;
          right = (int) (x + width);
          bottom = iconY + height;
          restoreToCount = -1;
        }
        media.draw(c, receiver, left, top, right, bottom, alpha, displayMediaKey);
        if (needTranslate) {
          if (isLast && media.isAnimated()) {
            receiver.getGifReceiver(displayMediaKey).finishDrawBatch(DRAW_BATCH_LEVEL_LOCAL);
          }
          Views.restore(c, restoreToCount);
        }
      } else {
        drawError(c, x + width / 2f, iconY + height / 2f, width / 2f, textAlpha, 0xffff0000);
        if (emojiInfo != null) {
          drawEmoji(c, x, y, textPaint, .45f);
        }
      }
    } else if (isRecognizedEmoji()) {
      drawEmoji(c, x, y, textPaint, alpha);
    } else {
      final int textY = y + source.getAscent(textSize) + textPaint.baselineShift;
      if (DEBUG && BiDiUtils.isValid(bidiEntity)) {
        final int color = BiDiUtils.isRtl(bidiEntity) ? 0x400000FF : 0x40FF0000;
        c.drawRect(x, textY - Screen.dp(16), x + width, textY, Paints.fillingPaint(color));
        c.drawRect(x, textY - Screen.dp(16), x + width, textY, Paints.strokeSmallPaint(0xFF000000));
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && BiDiUtils.isValid(bidiEntity)) {
        c.drawTextRun(line, start, end, 0, line.length(), x, textY, BiDiUtils.isRtl(bidiEntity), textPaint);
      } else {
        c.drawText(line, start, end, x, textY, textPaint);
      }
    }
  }

  private static final boolean DEBUG = false;

  public int getQuoteEntityId () {
    return entity != null ? entity.getQuoteId() : -1;
  }

  public static int getAdditionalLinesBefore (TextPart part) {
    final TextEntity entity = part.getEntity();
    if (entity == null) return 0;

    final int startEntity = entity.getStart();
    final int startPart = part.getStart();
    int startLinesCount = 0;

    while ((startPart - startLinesCount - 1) >= startEntity && part.getLine().charAt(startPart - startLinesCount - 1) == '\n') {
      startLinesCount++;
    }

    return startLinesCount;
  }

  public static int getAdditionalLinesAfter (TextPart part) {
    final TextEntity entity = part.getEntity();
    if (entity == null) return 0;

    final int endEntity = entity.getEnd();
    final int endPart = part.getEnd();
    int endLinesCount = 0;

    while ((endPart + endLinesCount) < endEntity && part.getLine().charAt(endPart + endLinesCount) == '\n') {
      endLinesCount++;
    }

    return endLinesCount;
  }
}
