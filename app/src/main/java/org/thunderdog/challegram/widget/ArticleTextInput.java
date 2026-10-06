/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.widget.EditText;
import androidx.annotation.NonNull;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.article.ArticleCodec;
import org.thunderdog.challegram.data.article.ArticleRichText;
import org.thunderdog.challegram.theme.Theme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import me.vkryl.core.lambda.RunnableData;

/** Editor spans retain the complete TDLib node, including metadata not visible in its label. */
public final class ArticleTextInput extends EditText {
  private static final class NodeSpan extends CharacterStyle {
    final TdApi.RichText node;
    final int order;
    NodeSpan (TdApi.RichText node, int order) { this.node = node; this.order = order; }
    @Override public void updateDrawState (TextPaint paint) {
      switch (node.getConstructor()) {
        case TdApi.RichTextBold.CONSTRUCTOR: paint.setFakeBoldText(true); break;
        case TdApi.RichTextItalic.CONSTRUCTOR: paint.setTextSkewX(-.2f); break;
        case TdApi.RichTextUnderline.CONSTRUCTOR: paint.setUnderlineText(true); break;
        case TdApi.RichTextStrikethrough.CONSTRUCTOR: paint.setStrikeThruText(true); break;
        case TdApi.RichTextFixed.CONSTRUCTOR: paint.setTypeface(Typeface.MONOSPACE); break;
        case TdApi.RichTextSpoiler.CONSTRUCTOR:
        case TdApi.RichTextMarked.CONSTRUCTOR: paint.bgColor = 0x33888888; break;
        case TdApi.RichTextSubscript.CONSTRUCTOR: paint.baselineShift += (int) (paint.getTextSize() * .2f); break;
        case TdApi.RichTextSuperscript.CONSTRUCTOR: paint.baselineShift -= (int) (paint.getTextSize() * .3f); break;
        default: paint.setColor(Theme.textLinkColor()); break;
      }
    }
  }
  private static final class AtomSpan extends ReplacementSpan {
    final TdApi.RichText node;
    final String label;
    AtomSpan (TdApi.RichText node) {
      this.node = node;
      String text = ArticleRichText.plain(node);
      label = node instanceof TdApi.RichTextAnchor ? "⚑ " + ((TdApi.RichTextAnchor) node).name : node instanceof TdApi.RichTextReference ? "※ " + ((TdApi.RichTextReference) node).name : text.isEmpty() ? "◆" : text;
    }
    @Override public int getSize (@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
      return (int) Math.ceil(paint.measureText(label)) + 8;
    }
    @Override public void draw (@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
      int color = paint.getColor();
      paint.setColor(Theme.textLinkColor());
      canvas.drawText(label, x + 4, y, paint);
      paint.setColor(color);
    }
  }

  private final RunnableData<TdApi.RichText> listener;
  private int order;
  private boolean settingText;
  private Runnable selectionListener;
  public void setSelectionListener (Runnable listener) { selectionListener = listener; }
  @Override protected void onSelectionChanged (int start, int end) { super.onSelectionChanged(start, end); if (!settingText && selectionListener != null) selectionListener.run(); }

  public ArticleTextInput (Context context, TdApi.RichText value, RunnableData<TdApi.RichText> listener) {
    super(context);
    this.listener = listener;
    setTextColor(Theme.textAccentColor());
    setTextSize(16f);
    setSingleLine(false);
    setFilters(new InputFilter[] {(source, start, end, destination, dstart, dend) -> {
      for (AtomSpan span : destination.getSpans(dstart, dend, AtomSpan.class)) {
        int a = destination.getSpanStart(span), b = destination.getSpanEnd(span);
        if (dstart > a && dstart < b || dend > a && dend < b) return destination.subSequence(dstart, dend);
      }
      return null;
    }});
    setRichText(value);
    addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }
      @Override public void afterTextChanged (Editable text) { if (!settingText) notifyChanged(); }
    });
  }

  public void setRichText (TdApi.RichText value) {
    settingText = true;
    SpannableStringBuilder text = new SpannableStringBuilder();
    append(text, value);
    setText(text);
    settingText = false;
  }

  private void append (SpannableStringBuilder out, TdApi.RichText value) {
    if (value == null) return;
    if (value instanceof TdApi.RichTextPlain) {
      out.append(((TdApi.RichTextPlain) value).text);
    } else if (value instanceof TdApi.RichTexts) {
      for (TdApi.RichText child : ((TdApi.RichTexts) value).texts) append(out, child);
    } else {
      TdApi.RichText child = value instanceof TdApi.RichTextReference ? null : ArticleRichText.child(value);
      int start = out.length(), index = order++;
      if (child == null) {
        out.append('\ufffc');
        out.setSpan(new AtomSpan(ArticleCodec.copy(value, TdApi.RichText.class)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else {
        append(out, child);
        if (start != out.length()) out.setSpan(new NodeSpan(ArticleCodec.copy(value, TdApi.RichText.class), index), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
  }

  public TdApi.RichText richText () {
    Editable text = getText();
    TreeSet<Integer> boundaries = new TreeSet<>();
    boundaries.add(0); boundaries.add(text.length());
    for (Object span : text.getSpans(0, text.length(), Object.class)) {
      if (span instanceof NodeSpan || span instanceof AtomSpan) {
        boundaries.add(text.getSpanStart(span)); boundaries.add(text.getSpanEnd(span));
      }
    }
    org.thunderdog.challegram.data.article.ArticleTextBuilder builder = new org.thunderdog.challegram.data.article.ArticleTextBuilder();
    Integer[] offsets = boundaries.toArray(new Integer[0]);
    for (int i = 0; i + 1 < offsets.length; i++) {
      int start = offsets[i], end = offsets[i + 1];
      if (start == end) continue;
      AtomSpan[] atoms = text.getSpans(start, end, AtomSpan.class);
      TdApi.RichText value = atoms.length == 0 ? new TdApi.RichTextPlain(text.subSequence(start, end).toString()) : ArticleCodec.copy(atoms[0].node, TdApi.RichText.class);
      NodeSpan[] nodes = text.getSpans(start, end, NodeSpan.class);
      Arrays.sort(nodes, Comparator.comparingInt((NodeSpan node) -> text.getSpanStart(node)).thenComparingInt(node -> -text.getSpanEnd(node)).thenComparingInt(node -> node.order));
      ArrayList<TdApi.RichText> wrappers = new ArrayList<>();
      for (NodeSpan node : nodes) {
        if (text.getSpanStart(node) <= start && text.getSpanEnd(node) >= end) wrappers.add(node.node);
      }
      builder.append(wrappers, value);
    }
    return builder.build();
  }

  public void format (TdApi.RichText wrapper) {
    int start = Math.min(getSelectionStart(), getSelectionEnd()), end = Math.max(getSelectionStart(), getSelectionEnd());
    if (start < 0 || start == end) return;
    Editable text = getText();
    boolean removed = false;
    for (NodeSpan span : text.getSpans(start, end, NodeSpan.class)) {
      if (span.node.getConstructor() == wrapper.getConstructor() && text.getSpanStart(span) == start && text.getSpanEnd(span) == end) {
        text.removeSpan(span); removed = true;
      }
    }
    if (!removed) text.setSpan(new NodeSpan(wrapper, order++), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    invalidate();
    notifyChanged();
  }

  public void insert (TdApi.RichText value) {
    SpannableStringBuilder insert = new SpannableStringBuilder();
    append(insert, value);
    int start = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
    int end = Math.max(start, Math.max(getSelectionStart(), getSelectionEnd()));
    getText().replace(start, end, insert);
    setSelection(start + insert.length());
  }

  /** Edits link/formula/reference metadata without replacing the styled label with plain text. */
  public TdApi.RichText selectedElement () {
    Object span = selectedSpan();
    return span instanceof AtomSpan ? ArticleCodec.copy(((AtomSpan) span).node, TdApi.RichText.class) : span instanceof NodeSpan ? ArticleCodec.copy(((NodeSpan) span).node, TdApi.RichText.class) : null;
  }
  private Object selectedSpan () {
    int start = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
    int end = Math.max(start, Math.max(getSelectionStart(), getSelectionEnd()));
    AtomSpan[] atoms = getText().getSpans(start, end, AtomSpan.class);
    if (atoms.length > 0) return atoms[0];
    NodeSpan[] nodes = getText().getSpans(start, end, NodeSpan.class);
    for (NodeSpan node : nodes) if (!(node.node instanceof TdApi.RichTextBold || node.node instanceof TdApi.RichTextItalic || node.node instanceof TdApi.RichTextUnderline || node.node instanceof TdApi.RichTextStrikethrough || node.node instanceof TdApi.RichTextFixed || node.node instanceof TdApi.RichTextSpoiler || node.node instanceof TdApi.RichTextMarked || node.node instanceof TdApi.RichTextSuperscript || node.node instanceof TdApi.RichTextSubscript)) return node;
    return null;
  }
  public void updateSelectedElement (TdApi.RichText value) {
    Object span = selectedSpan(); if (span == null) return;
    int start = getText().getSpanStart(span), end = getText().getSpanEnd(span);
    getText().removeSpan(span);
    if (span instanceof AtomSpan) getText().setSpan(new AtomSpan(value), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    else getText().setSpan(new NodeSpan(value, ((NodeSpan) span).order), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    requestLayout(); invalidate(); notifyChanged();
  }

  private void notifyChanged () { if (listener != null) listener.runWithData(richText()); }
}
