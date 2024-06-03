package org.thunderdog.challegram.util.text.quotes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class QuoteSpan implements LeadingMarginSpan {
  public final QuoteStyleSpan styleSpan;
  public boolean adaptLineHeight = true;
  public boolean edit = true;
  public int start, end;
  public boolean singleLine, first, last;
  public boolean rtl;

  public QuoteSpan () {
    this.styleSpan = new QuoteStyleSpan(this);
  }

  public int getColor () {
    return Theme.getColor(ColorId.textQuote);
  }

  public int getColorId () {
    return ColorId.textQuote;
  }

  @Override
  public int getLeadingMargin(boolean first) {
    return Screen.dp(adaptLineHeight ? 8 : 10);
  }

  @Override
  public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {

  }

  public static class QuoteStyleSpan extends MetricAffectingSpan implements LineHeightSpan {
    private final QuoteSpan span;

    private QuoteStyleSpan (QuoteSpan span) {
      this.span = span;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
      if (tp != null) {
        tp.setColor(span.getColor());
      }
    }

    @Override
    public void updateMeasureState (@NonNull TextPaint textPaint) {

    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
      if (span.adaptLineHeight) {
        final int dp = span.singleLine ? 7 : 2;
        if (start <= span.start) {
          fm.ascent -= Screen.dp((span.last ? 2 : 0) + dp);
          fm.top -= Screen.dp((span.last ? 2 : 0) + dp);
        }
        if (end >= span.end) {
          fm.descent += Screen.dp(dp);
          fm.bottom += Screen.dp(dp);
        }
      }
    }
  }

  public static class EmptySpan { }

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
    for (int i = 0; i < spans.length; ++i) {
      boolean wasLast = spans[i].last;
      Block block = new Block(text, layout, spannable, spans[i]);

      if (block.span.start == block.span.end) {
        continue;
      }
      if (spannable.getSpanStart(spans[i].styleSpan) == -1) {
        spannable.setSpan(spans[i].styleSpan, block.span.start, block.span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }

      if (block.span.edit) {
        if (!(block.span.start == 0 || text.charAt(block.span.start - 1) == '\n')) {
        // if (!(block.span.start == 0 || text.charAt(block.span.start - 1) == '\n' || text.charAt(block.span.start) == '\n')) {
          spannable.removeSpan(spans[i]);
          spannable.removeSpan(spans[i].styleSpan);
          continue;
        }
        if (!(block.span.end == text.length() || text.charAt(block.span.end) == '\n')) {
          // new line was removed after a quote, finding a new quote end
          int newEnd = block.span.end;
          for (; newEnd <= text.length() && !(newEnd == text.length() || text.charAt(newEnd) == '\n'); ++newEnd);
          spannable.removeSpan(spans[i]);
          spannable.removeSpan(spans[i].styleSpan);
          spannable.setSpan(spans[i], block.span.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          spannable.setSpan(spans[i].styleSpan, block.span.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          block = new Block(text, layout, spannable, spans[i]);
        }
      }
      if (blocks == null) {
        blocks = new ArrayList<>();
      }
      if (spans[i].last != wasLast && updateLayout != null) {
        updateLayout[0] = true;
      }
      blocks.add(block);
    }
    return blocks;
  }

  public static class Block {
    private static final RectF tmpRect = new RectF();
    private final QuoteBackground.BackgroundDrawable backgroundDrawable;

    public final int top, bottom, width;
    public final @NonNull QuoteSpan span;

    public Block(CharSequence text, Layout layout, Spanned spanned, @NonNull QuoteSpan span) {
      this.backgroundDrawable = new QuoteBackground.BackgroundDrawable();

      this.span = span;
      span.start = spanned.getSpanStart(span);
      span.end = spanned.getSpanEnd(span);

      if (span.end - 1 >= 0 && span.end < spanned.length() && spanned.charAt(span.end) != '\n' && spanned.charAt(span.end - 1) == '\n') {
        span.end--;
      }

      final int lineStart = layout.getLineForOffset(span.start);
      //final int lineStart = layout.getLineForOffset(Math.min(text.charAt(span.start) == '\n' ? span.start + 1 : span.start, span.end));
      final int lineEnd = layout.getLineForOffset(span.end);
      span.singleLine = lineEnd - lineStart < 1;
      span.first = lineStart <= 0;
      span.last = lineEnd + 1 >= layout.getLineCount();

      if (span.edit) {
        this.top = layout.getLineTop(lineStart) + Screen.dp(3 - (span.singleLine ? 0 : 3 + (span.first ? 2 : 0)));
        this.bottom = layout.getLineBottom(lineEnd) - Screen.dp(2 - (span.singleLine ? 0 : 3 + (span.last ? 2 : 0)));
      } else {
        this.top = layout.getLineTop(lineStart) + Screen.dp(3 - (span.singleLine ? 1 : 2));
        this.bottom = layout.getLineBottom(lineEnd) - Screen.dp(2 - (span.singleLine ? 1 : 2));
      }

      float width = 0;
      span.rtl = false;
      for (int line = lineStart; line <= lineEnd; ++line) {
        width = Math.max(width, layout.getLineRight(line));
        if (layout.getLineLeft(line) > 0)
          span.rtl = true;
      }
      this.width = (int) Math.ceil(width);
    }

    public void draw(Canvas canvas, float offsetX, float offsetY, int maxWidth) {
      final int color = span.getColor();

      int width = span.edit ? maxWidth : (this.width + Screen.dp(32));
      if (width >= maxWidth * 0.95) {
        width = maxWidth;
      }

      int s = Views.save(canvas);
      canvas.translate(offsetX, offsetY);

      backgroundDrawable.setBounds(Screen.dp(-1), top, width, bottom);
      backgroundDrawable.setColor(ColorUtils.alphaColor(0.05f, color));
      backgroundDrawable.draw(canvas);

      tmpRect.set(-Screen.dp(3), top, 0, bottom);
      canvas.drawRoundRect(tmpRect, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(color));

      Views.restore(canvas, s);
    }
  }

  public static void normalizeQuotes (Editable text) {
    QuoteSpan.EmptySpan[] spans = text.getSpans(0, text.length(), QuoteSpan.EmptySpan.class);
    if (spans == null || spans.length == 0) {
      return;
    }

    final int QUOTE_START = 1;
    final int QUOTE_END = 2;
    final TreeSet<Integer> cutIndexes = new TreeSet<>();
    final HashMap<Integer, Integer> cutToType = new HashMap<>();

    for (int i = 0; i < spans.length; ++i) {
      final QuoteSpan.EmptySpan span = spans[i];
      final int start = text.getSpanStart(span);
      final int end = text.getSpanEnd(span);

      cutIndexes.add(start);
      cutToType.put(start, (cutToType.containsKey(start) ? cutToType.get(start) : 0) | QUOTE_START);
      cutIndexes.add(end);
      cutToType.put(end, (cutToType.containsKey(end) ? cutToType.get(end) : 0) | QUOTE_END);

      text.removeSpan(span);
    }

    int offset = 0;
    int from = 0;
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
          offset += setQuoteSpan(text, from + offset, to + offset);
        }
        from = cutIndex;
      }

      if ((type & QUOTE_END) != 0) quoteCount--;
      if ((type & QUOTE_START) != 0) quoteCount++;
    }
    if (from < text.length()) {
      if (quoteCount > 0) {
        setQuoteSpan(text, from + offset, text.length());
      }
    }
  }

  public static int setQuoteSpan (Editable editable, int start, int end) {
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

    final QuoteSpan quoteSpan = new QuoteSpan();
    final QuoteStyleSpan styleSpan = quoteSpan.styleSpan;
    quoteSpan.start = start;
    quoteSpan.end = end;
    editable.setSpan(quoteSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    editable.setSpan(styleSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

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
    }
    if (span instanceof QuoteSpan.QuoteStyleSpan) {
      QuoteSpan quoteSpan = ((QuoteStyleSpan) span).span;
      if (quoteSpan != null) {
        editable.removeSpan(quoteSpan);
      }
    }
  }
}
