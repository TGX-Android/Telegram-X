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
 * File created on 12/06/2024
 */

package org.thunderdog.challegram.util.text.quotes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Layout;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.UpdateAppearance;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.emoji.PreserveCustomEmojiFilter;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.RippleDrawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animatorx.BoolAnimator;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class QuoteSpan implements LeadingMarginSpan, PreserveCustomEmojiFilter.RecoverableSpan {
  private static final int FLAG_FIRST = 1;
  private static final int FLAG_LAST = 1 << 1;
  private static final int FLAG_DISALLOW_MARGIN_TOP = 1 << 2;
  private static final int FLAG_DISALLOW_MARGIN_BOTTOM = 1 << 3;
  private static final int FLAG_HAS_BUTTON = 1 << 4;
  private static final int FLAG_BUTTON_BELOW_TEXT = 1 << 5;

  private static final float BUTTON_SIZE = 24f;
  private static final float BUTTON_BELOW_TEXT_HEIGHT =
    BUTTON_SIZE - QuoteBackground.QUOTE_VERTICAL_PADDING;
  private static final float BUTTON_RIPPLE_RADIUS = 14f;
  private static final float BUTTON_TOUCH_PADDING = 8f;
  // From the end of the longest line, as the chevron of the quote once sent
  private static final float BUTTON_TEXT_OFFSET = QuoteBackground.QUOTE_RIGHT_PADDING -
    QuoteBackground.QUOTE_ICON_RIGHT + QuoteBackground.QUOTE_ICON_SIZE / 2f;
  // Applied by priority: the quote's color, its collapsed part, then links, which keep theirs
  private static final int STYLE_SPAN_FLAGS =
    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | (2 << Spanned.SPAN_PRIORITY_SHIFT);
  private static final int COLLAPSED_PART_SPAN_FLAGS =
    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | (1 << Spanned.SPAN_PRIORITY_SHIFT);
  // The alpha of the field's hint: a little darker than white, readable on a white photo
  private static final float HIDDEN_TEXT_ALPHA_OVER_MEDIA = 0xBA / 255f;

  public final QuoteStyleSpan styleSpan;
  private final CollapsedPartSpan collapsedPartSpan = new CollapsedPartSpan();

  public int start, end, flags;
  public boolean rtl;
  private boolean isExpandable;
  private @Nullable BoolAnimator isExpanded;
  private @Nullable ThemeDelegate colorTheme;
  private boolean isOverMedia;

  public QuoteSpan (boolean isExpandable) {
    this.styleSpan = new QuoteStyleSpan(this);
    this.isExpandable = isExpandable;
  }

  public boolean isExpandable () {
    return isExpandable;
  }

  public void setColorTheme (@Nullable ThemeDelegate theme, boolean isOverMedia) {
    this.colorTheme = theme;
    this.isOverMedia = isOverMedia;
  }

  private int getColor (@ColorId int colorId) {
    return colorTheme != null ? colorTheme.getColor(colorId) : Theme.getColor(colorId);
  }

  private int getTextColor () {
    return getColor(isOverMedia ? ColorId.white : ColorId.blockQuoteText);
  }

  private int getLineColor () {
    // The bar and the chevron
    return getColor(isOverMedia ? ColorId.white : ColorId.blockQuoteLine);
  }

  private int getHiddenTextColor () {
    return isOverMedia ?
      ColorUtils.alphaColor(HIDDEN_TEXT_ALPHA_OVER_MEDIA, getColor(ColorId.white)) :
      getColor(ColorId.blockQuoteTextHidden);
  }

  public void toggleExpandable (View view) {
    final boolean wasExpanded = !isExpandable;
    isExpandable = !isExpandable;
    if (isExpanded == null) {
      isExpanded = new BoolAnimator(
        180L,
        AnimatorUtils.DECELERATE_INTERPOLATOR,
        wasExpanded,
        (state, value, stateChanged, prevState) -> view.invalidate()
      );
    }
    isExpanded.setAnimatedValue(!isExpandable);
  }

  private float getExpandFactor () {
    if (isExpanded != null) {
      return isExpanded.getFloatValue();
    }
    return isExpandable ? 0f : 1f;
  }

  private class CollapsedPartSpan extends CharacterStyle implements UpdateAppearance, NoCopySpan {
    @Override
    public void updateDrawState (TextPaint tp) {
      tp.setColor(getHiddenTextColor());
    }
  }

  private void updateCollapsedPart (Spannable spannable, Layout layout, Block block) {
    final int collapsedStart = BitwiseUtils.hasFlag(flags, FLAG_HAS_BUTTON) ?
      layout.getLineStart(block.lineStart + Config.INPUT_QUOTE_COLLAPSED_LINE_COUNT) :
      end;
    if (!isExpandable || collapsedStart >= end) {
      spannable.removeSpan(collapsedPartSpan);
      return;
    }
    if (spannable.getSpanStart(collapsedPartSpan) != collapsedStart ||
      spannable.getSpanEnd(collapsedPartSpan) != end) {
      spannable.setSpan(collapsedPartSpan, collapsedStart, end, COLLAPSED_PART_SPAN_FLAGS);
    }
  }

  private static void removeQuote (Spannable spannable, QuoteSpan span) {
    spannable.removeSpan(span);
    spannable.removeSpan(span.styleSpan);
    spannable.removeSpan(span.collapsedPartSpan);
  }

  @Override
  public int getLeadingMargin(boolean first) {
    return Screen.dp(QuoteBackground.QUOTE_LEFT_PADDING - 3);
  }

  @Override
  public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {

  }

  public static class QuoteStyleSpan extends MetricAffectingSpan implements LineHeightSpan {
    private final QuoteSpan span;

    private QuoteStyleSpan (QuoteSpan span) {
      this.span = span;
    }

    int ascent, descent, top, bottom;

    @Override
    public void updateDrawState(TextPaint tp) {
      if (tp == null) {
        return;
      }
      tp.setColor(span.getTextColor());

      final float size = tp.getTextSize();
      tp.setTextSize(size - 1);
      tp.setTextSize(size);
    }

    @Override
    public void updateMeasureState(@NonNull TextPaint tp) {
      final float size = tp.getTextSize();
      tp.setTextSize(size - 1);
      tp.setTextSize(size);

      Paint.FontMetricsInt fm = tp.getFontMetricsInt();
      ascent = fm.ascent;
      descent = fm.descent;
      top = fm.top;
      bottom = fm.bottom;
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
      fm.ascent = ascent;
      fm.descent = descent;
      fm.top = top;
      fm.bottom = bottom;

      if (start <= span.start) {
        fm.ascent -= Screen.dp(QuoteBackground.QUOTE_VERTICAL_PADDING + (!BitwiseUtils.hasFlag(span.flags, FLAG_DISALLOW_MARGIN_TOP) ? QuoteBackground.QUOTE_VERTICAL_MARGIN : 0));
        fm.top -= Screen.dp(QuoteBackground.QUOTE_VERTICAL_PADDING + (!BitwiseUtils.hasFlag(span.flags, FLAG_DISALLOW_MARGIN_TOP) ? QuoteBackground.QUOTE_VERTICAL_MARGIN : 0));
      }
      if (end >= span.end) {
        fm.descent += Screen.dp(QuoteBackground.QUOTE_VERTICAL_PADDING + (!BitwiseUtils.hasFlag(span.flags, FLAG_DISALLOW_MARGIN_BOTTOM) ? QuoteBackground.QUOTE_VERTICAL_MARGIN : 0));
        fm.bottom += Screen.dp(QuoteBackground.QUOTE_VERTICAL_PADDING + (!BitwiseUtils.hasFlag(span.flags, FLAG_DISALLOW_MARGIN_BOTTOM) ? QuoteBackground.QUOTE_VERTICAL_MARGIN : 0));
        if (BitwiseUtils.hasFlag(span.flags, FLAG_BUTTON_BELOW_TEXT)) {
          fm.descent += Screen.dp(BUTTON_BELOW_TEXT_HEIGHT);
          fm.bottom += Screen.dp(BUTTON_BELOW_TEXT_HEIGHT);
        }
      }
    }
  }

  public static class EmptySpan {
    private final boolean isExpandable;

    public EmptySpan (boolean isExpandable) {
      this.isExpandable = isExpandable;
    }
  }

  public static boolean isQuoteSpan (Object span) {
    return span instanceof QuoteSpan || span instanceof QuoteSpan.QuoteStyleSpan || span instanceof QuoteSpan.EmptySpan;
  }

  public static ArrayList<Block> updateQuoteBlocks(Layout layout, ArrayList<Block> blocks, boolean[] updateLayout) {
    if (layout == null) {
      if (blocks != null) {
        blocks.clear();
      }
      return blocks;
    }
    CharSequence text = layout.getText();
    if (text == null || !(text instanceof Spannable)) {
      if (blocks != null) {
        blocks.clear();
      }
      return blocks;
    }
    Spannable spannable = (Spannable) text;
    if (blocks != null) {
      blocks.clear();
    }
    QuoteSpan[] spans = spannable.getSpans(0, spannable.length(), QuoteSpan.class);
    if (spans == null || spans.length == 0) {
      return blocks;
    }

    Arrays.sort(spans, Comparator.comparingInt(s -> s.start));

    int lastLineEnd = -1;
    int lastSpanEnd = -1;
    for (int i = 0; i < spans.length; ++i) {
      final int oldFlags = spans[i].flags;
      Block block = new Block(text, layout, spannable, spans[i], lastLineEnd);

      if (block.span.start == block.span.end) {
        continue;
      }
      if (spannable.getSpanStart(spans[i].styleSpan) == -1) {
        spannable.setSpan(spans[i].styleSpan, block.span.start, block.span.end, STYLE_SPAN_FLAGS);
      }

      if (lastSpanEnd != -1 && block.span.start <= lastSpanEnd) {
        removeQuote(spannable, spans[i]);
        continue;
      }

      if (!(block.span.start == 0 || text.charAt(block.span.start - 1) == '\n')) {
      // if (!(block.span.start == 0 || text.charAt(block.span.start - 1) == '\n' || text.charAt(block.span.start) == '\n')) {
        removeQuote(spannable, spans[i]);
        continue;
      }
      if (!(block.span.end == text.length() || text.charAt(block.span.end) == '\n')) {
        // new line was removed after a quote, finding a new quote end
        int newEnd = block.span.end;
        for (; newEnd <= text.length() && !(newEnd == text.length() || text.charAt(newEnd) == '\n'); ++newEnd);
        spannable.removeSpan(spans[i]);
        spannable.removeSpan(spans[i].styleSpan);
        spannable.setSpan(spans[i], block.span.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(spans[i].styleSpan, block.span.start, newEnd, STYLE_SPAN_FLAGS);
        block = new Block(text, layout, spannable, spans[i], lastLineEnd);
      }
      block.span.updateCollapsedPart(spannable, layout, block);

      if (blocks == null) {
        blocks = new ArrayList<>();
      }
      if (spans[i].flags != oldFlags && updateLayout != null) {
        updateLayout[0] = true;
      }
      blocks.add(block);
      lastLineEnd = block.lineEnd;
      lastSpanEnd = block.span.end;
    }
    return blocks;
  }

  public static class Block {
    private static final RectF tmpRect = new RectF();

    public final int top, bottom, width;
    public final @NonNull QuoteSpan span;
    public final int lineStart, lineEnd;

    public Block(CharSequence text, Layout layout, Spanned spanned, @NonNull QuoteSpan span, int lastLineEnd) {
      this.span = span;
      span.start = spanned.getSpanStart(span);
      span.end = spanned.getSpanEnd(span);

      /*if (span.end - 1 >= 0 && span.end < spanned.length() && spanned.charAt(span.end) != '\n' && spanned.charAt(span.end - 1) == '\n') {
        span.end--;
      }*/

      //final int lineStart = layout.getLineForOffset(Math.min(text.charAt(span.start) == '\n' ? span.start + 1 : span.start, span.end));
      // final int lineEnd = layout.getLineForOffset(span.end);

      lineStart = layout.getLineForOffset(span.start);
      lineEnd = layout.getLineForOffset(/*span.end < text.length() && text.charAt(span.end) == '\n' ? span.end - 1 :*/ span.end);

      int flags = 0;
      flags = BitwiseUtils.setFlag(flags, FLAG_FIRST, lineStart <= 0);
      flags = BitwiseUtils.setFlag(flags, FLAG_LAST, lineEnd + 1 >= layout.getLineCount());
      flags = BitwiseUtils.setFlag(flags, FLAG_DISALLOW_MARGIN_TOP, lastLineEnd != -1 && lastLineEnd + 1 == lineStart || BitwiseUtils.hasFlag(flags, FLAG_FIRST));
      flags = BitwiseUtils.setFlag(flags, FLAG_DISALLOW_MARGIN_BOTTOM, BitwiseUtils.hasFlag(flags, FLAG_LAST));
      final boolean hasButton = lineEnd - lineStart + 1 > Config.INPUT_QUOTE_COLLAPSED_LINE_COUNT;
      flags = BitwiseUtils.setFlag(flags, FLAG_HAS_BUTTON, hasButton);

      span.flags = flags;

      this.top = layout.getLineTop(lineStart) + (!BitwiseUtils.hasFlag(flags, FLAG_DISALLOW_MARGIN_TOP) ? Screen.dp(QuoteBackground.QUOTE_VERTICAL_MARGIN) : 0);
      this.bottom = layout.getLineBottom(lineEnd) - (!BitwiseUtils.hasFlag(flags, FLAG_DISALLOW_MARGIN_BOTTOM) ? Screen.dp(QuoteBackground.QUOTE_VERTICAL_MARGIN) : 0);

      float width = 0;
      span.rtl = false;
      for (int line = lineStart; line <= lineEnd; ++line) {
        width = Math.max(width, layout.getLineRight(line));
        if (layout.getLineLeft(line) > 0)
          span.rtl = true;
      }
      this.width = (int) Math.ceil(width);

      this.hasButton = hasButton;
      final float buttonHalf = Screen.dp(BUTTON_SIZE) / 2f;
      // With the spaces at its end, where the cursor goes
      final float lastRight = Math.max(
        layout.getLineRight(lineEnd),
        layout.getPrimaryHorizontal(span.end)
      );
      this.buttonCenterX = Math.min(
        Math.max(width, lastRight) + Screen.dp(BUTTON_TEXT_OFFSET),
        layout.getWidth() - buttonHalf
      );
      this.buttonCenterY = bottom - buttonHalf;
      final boolean isButtonBelowText = hasButton && lastRight > buttonCenterX - buttonHalf;
      span.flags = BitwiseUtils.setFlag(span.flags, FLAG_BUTTON_BELOW_TEXT, isButtonBelowText);
      // Not where a tap puts the cursor at the end of the last line
      this.buttonLeft = isButtonBelowText ?
        buttonCenterX - buttonHalf :
        Math.max(buttonCenterX - buttonHalf, lastRight + Screen.dp(BUTTON_TOUCH_PADDING));
      this.lastLineTop = layout.getLineTop(lineEnd);
    }

    private final boolean hasButton;
    private final float buttonCenterX, buttonCenterY, buttonLeft;
    private final int lastLineTop;

    public boolean isButtonHit (float x, float y) {
      // Wider to the right and below, where no text of the quote is
      final float half = Screen.dp(BUTTON_SIZE) / 2f;
      final float padding = Screen.dp(BUTTON_TOUCH_PADDING);
      return hasButton &&
        x >= buttonLeft && x <= buttonCenterX + half + padding &&
        y >= Math.max(buttonCenterY - half, lastLineTop) && y <= buttonCenterY + half + padding;
    }

    public boolean hasButton () {
      return hasButton;
    }

    public void getButtonBounds (Rect rect) {
      final int half = Screen.dp(BUTTON_SIZE) / 2;
      final int centerX = Math.round(buttonCenterX);
      final int centerY = Math.round(buttonCenterY);
      rect.set(centerX - half, centerY - half, centerX + half, centerY + half);
    }

    public void setButtonRipple (RippleDrawable ripple) {
      final int radius = Screen.dp(BUTTON_RIPPLE_RADIUS);
      final int centerX = Math.round(buttonCenterX);
      final int centerY = Math.round(buttonCenterY);
      ripple.setBounds(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
      ripple.setRadius(radius, radius, radius, radius);
      ripple.setMainColor(span.getLineColor(), 0.25f);
    }

    public void draw (Canvas canvas, float offsetX, float offsetY, int maxWidth) {
      int width = maxWidth;
      if (width >= maxWidth * 0.95) {
        width = maxWidth;
      }

      int s = Views.save(canvas);
      canvas.translate(offsetX, offsetY);

      tmpRect.set(-Screen.dp(3), top, 0, bottom);
      final int lineColor = span.getLineColor();
      final Paint paint = Paints.fillingPaint(lineColor);
      canvas.drawRoundRect(tmpRect, Screen.dp(1.5f), Screen.dp(1.5f), paint);
      if (hasButton) {
        DrawAlgorithms.drawCollapse(
          canvas,
          buttonCenterX,
          buttonCenterY,
          lineColor,
          span.getExpandFactor(),
          0f
        );
      }

      Views.restore(canvas, s);
    }
  }

  public static void normalizeQuotesWithoutMerge (Editable text) {
    QuoteSpan.EmptySpan[] spans = text.getSpans(0, text.length(), QuoteSpan.EmptySpan.class);
    if (spans == null || spans.length == 0) {
      return;
    }

    for (int i = 0; i < spans.length; ++i) {
      final QuoteSpan.EmptySpan span = spans[i];
      final int start = text.getSpanStart(span);
      final int end = text.getSpanEnd(span);

      text.removeSpan(span);
      setQuoteSpan(text, start, end, span.isExpandable);
    }
  }

  public static void normalizeQuotes (Editable text) {
    QuoteSpan.EmptySpan[] spans = text.getSpans(0, text.length(), QuoteSpan.EmptySpan.class);
    if (spans == null || spans.length == 0) {
      return;
    }

    final int QUOTE_START = 1;
    final int QUOTE_END = 2;
    final int QUOTE_START_COLLAPSE = 1 << 2;
    final TreeSet<Integer> cutIndexes = new TreeSet<>();
    final HashMap<Integer, Integer> cutToType = new HashMap<>();

    for (int i = 0; i < spans.length; ++i) {
      final QuoteSpan.EmptySpan span = spans[i];
      final int start = text.getSpanStart(span);
      final int end = text.getSpanEnd(span);

      cutIndexes.add(start);
      cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | QUOTE_START | (span.isExpandable ? QUOTE_START_COLLAPSE : 0));
      cutIndexes.add(end);
      cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);

      text.removeSpan(span);
    }

    int offset = 0;
    int from = 0;
    boolean quoteCollapse = false;
    int quoteCount = 0;
    for (Iterator<Integer> i = cutIndexes.iterator(); i.hasNext(); ) {
      int cutIndex = i.next();
      final int type = cutToType.get(cutIndex);

      if ((type & QUOTE_END) != 0 && (type & QUOTE_START) != 0 || quoteCount > 0 && (type & QUOTE_START) != 0) {
        continue;
      }

      if (from != cutIndex) {
        int to = cutIndex;
        final boolean isQuote = quoteCount > 0;
        if (isQuote) {
          offset += setQuoteSpan(text, from + offset, to + offset, quoteCollapse);
        }
        from = cutIndex;
      }

      if ((type & QUOTE_END) != 0) quoteCount--;
      if ((type & QUOTE_START) != 0) {
        quoteCount++;
        quoteCollapse = (type & QUOTE_START_COLLAPSE) != 0;
      }
    }
    if (from < text.length()) {
      if (quoteCount > 0) {
        setQuoteSpan(text, from + offset, text.length(), quoteCollapse);
      }
    }
  }

  private static int setQuoteSpan (Editable editable, int start, int end, boolean isExpanable) {
    if (editable == null) {
      return -1;
    }

    int insertedBefore = 0;
    int insertedAfter = 0;
    start = MathUtils.clamp(start, 0, editable.length());
    end = MathUtils.clamp(end, 0, editable.length());
    if (start > 0 && editable.charAt(start - 1) != '\n') {
      editable.insert(start, "\n");
      insertedBefore++;
      start++;
      end++;
    }

    if (end < editable.length() && editable.charAt(end) != '\n') {
      editable.insert(end, "\n");
      insertedAfter++;
    }

    final QuoteSpan quoteSpan = new QuoteSpan(isExpanable);
    final QuoteStyleSpan styleSpan = quoteSpan.styleSpan;
    quoteSpan.start = start;
    quoteSpan.end = end;
    editable.setSpan(quoteSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    editable.setSpan(styleSpan, start, end, STYLE_SPAN_FLAGS);

    // editable.insert(end, "\uFEFF");
    // editable.delete(end, end + 1);

    return insertedAfter + insertedBefore;
  }

  public static void removeSpan (Editable editable, Object span) {
    editable.removeSpan(span);
    if (span instanceof QuoteSpan) {
      QuoteSpan.QuoteStyleSpan styleSpan = ((QuoteSpan) span).styleSpan;
      if (styleSpan != null) {
        editable.removeSpan(styleSpan);
      }
      editable.removeSpan(((QuoteSpan) span).collapsedPartSpan);
    }
    if (span instanceof QuoteSpan.QuoteStyleSpan) {
      QuoteSpan quoteSpan = ((QuoteStyleSpan) span).span;
      if (quoteSpan != null) {
        editable.removeSpan(quoteSpan);
        editable.removeSpan(quoteSpan.collapsedPartSpan);
      }
    }
  }
}
