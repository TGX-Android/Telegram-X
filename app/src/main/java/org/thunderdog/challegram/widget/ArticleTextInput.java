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
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
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
public final class ArticleTextInput extends CustomEmojiEditText {
  private static final class EmojiNode {
    final TdApi.RichTextCustomEmoji node;
    EmojiNode (TdApi.RichTextCustomEmoji node) { this.node = node; }
  }
  private static final class NodeSpan extends MetricAffectingSpan {
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
        case TdApi.RichTextReference.CONSTRUCTOR: break;
        default: paint.setColor(Theme.textLinkColor()); break;
      }
    }
    @Override public void updateMeasureState (TextPaint paint) { updateDrawState(paint); }
  }
  private static final class AtomSpan extends ReplacementSpan {
    final TdApi.RichText node;
    final String label;
    final Context context;
    AtomSpan (Context context, TdApi.RichText node) {
      this.context = context;
      this.node = node;
      String text = ArticleRichText.plain(node);
      label = node instanceof TdApi.RichTextAnchor ? "⚑ " + ((TdApi.RichTextAnchor) node).name : node instanceof TdApi.RichTextReference ? "※ " + ((TdApi.RichTextReference) node).name : text.isEmpty() ? "◆" : text;
    }
    private android.graphics.Bitmap formula (Paint paint) {
      return node instanceof TdApi.RichTextMathematicalExpression ? org.thunderdog.challegram.data.article.ArticleMath.render(context, ((TdApi.RichTextMathematicalExpression) node).expression, paint.getTextSize()) : null;
    }
    @Override public int getSize (@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
      android.graphics.Bitmap image = formula(paint);
      if (image != null) {
        if (metrics != null) { Paint.FontMetricsInt original = paint.getFontMetricsInt(); metrics.ascent = Math.min(original.ascent, -image.getHeight()); metrics.top = Math.min(original.top, metrics.ascent); metrics.descent = original.descent; metrics.bottom = original.bottom; }
        return image.getWidth() + 8;
      }
      return (int) Math.ceil(paint.measureText(label)) + 8;
    }
    @Override public void draw (@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
      int color = paint.getColor();
      android.graphics.Bitmap image = formula(paint);
      if (image != null) { canvas.drawBitmap(image, x + 4, y - image.getHeight(), paint); return; }
      paint.setColor(Theme.textLinkColor());
      canvas.drawText(label, x + 4, y, paint);
      paint.setColor(color);
    }
  }

  private final RunnableData<TdApi.RichText> listener;
  private int order;
  private boolean settingText;
  private Runnable selectionListener;
  public interface BoundaryListener { boolean onEnter (); boolean onBackspace (); }
  private BoundaryListener boundaryListener;
  public interface DocumentActions { boolean replace (CharSequence text); boolean menu (int id); }
  private DocumentActions documentActions;
  public void setDocumentActions (DocumentActions actions) { documentActions = actions; }
  private final ArrayList<TdApi.RichText> typingFormats = new ArrayList<>();
  public void setBoundaryListener (BoundaryListener listener) { boundaryListener = listener; }
  public void setSelectionListener (Runnable listener) { selectionListener = listener; }
  @Override protected void onSelectionChanged (int start, int end) { super.onSelectionChanged(start, end); if (!settingText && selectionListener != null) selectionListener.run(); }

  public ArticleTextInput (Context context, TdApi.RichText value, RunnableData<TdApi.RichText> listener) {
    this(context, null, value, listener);
  }

  public ArticleTextInput (Context context, org.thunderdog.challegram.telegram.Tdlib tdlib, TdApi.RichText value, RunnableData<TdApi.RichText> listener) {
    super(context, tdlib);
    this.listener = listener;
    setTextColor(Theme.textAccentColor());
    setTextSize(16f);
    setSingleLine(false);
    setBackground(null);
    setPadding(0, org.thunderdog.challegram.tool.Screen.dp(5), 0, org.thunderdog.challegram.tool.Screen.dp(5));
    setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
    setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    setFilters(new InputFilter[] {(source, start, end, destination, dstart, dend) -> {
      for (AtomSpan span : destination.getSpans(dstart, dend, AtomSpan.class)) {
        int a = destination.getSpanStart(span), b = destination.getSpanEnd(span);
        if (dstart > a && dstart < b || dend > a && dend < b) return destination.subSequence(dstart, dend);
      }
      for (EmojiNode span : destination.getSpans(dstart, dend, EmojiNode.class)) {
        int a = destination.getSpanStart(span), b = destination.getSpanEnd(span);
        if (dstart > a && dstart < b || dend > a && dend < b) return destination.subSequence(dstart, dend);
      }
      return null;
    }});
    setRichText(value);
    addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) {
        if (!settingText && count > 0 && s instanceof Editable) for (TdApi.RichText format : typingFormats)
          ((Editable) s).setSpan(new NodeSpan(ArticleCodec.copy(format, TdApi.RichText.class), order++), start, start + count, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      @Override public void afterTextChanged (Editable text) { if (!settingText) notifyChanged(); }
    });
  }

  public void setRichText (TdApi.RichText value) {
    settingText = true;
    order = 0;
    typingFormats.clear();
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
    } else if (value instanceof TdApi.RichTextCustomEmoji) {
      TdApi.RichTextCustomEmoji emoji = (TdApi.RichTextCustomEmoji) value;
      int start = out.length(); String label = emoji.alternativeText.isEmpty() ? "✨" : emoji.alternativeText;
      out.append(org.thunderdog.challegram.data.TD.toCharSequence(new TdApi.FormattedText(label, new TdApi.TextEntity[] {new TdApi.TextEntity(0, label.length(), new TdApi.TextEntityTypeCustomEmoji(emoji.customEmojiId))})));
      out.setSpan(new EmojiNode(ArticleCodec.copy(emoji, TdApi.RichTextCustomEmoji.class)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    } else {
      TdApi.RichText child = ArticleRichText.child(value);
      if (value instanceof TdApi.RichTextReference && ArticleRichText.plain(child).isEmpty()) child = null;
      int start = out.length(), index = order++;
      if (child == null) {
        out.append('\ufffc');
        out.setSpan(new AtomSpan(getContext(), ArticleCodec.copy(value, TdApi.RichText.class)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      } else {
        append(out, child);
        if (start != out.length()) out.setSpan(new NodeSpan(ArticleCodec.copy(value, TdApi.RichText.class), index), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
  }

  public TdApi.RichText richText () {
    return richText(getText());
  }
  public TdApi.RichText richText (int start, int end) {
    return richText(new SpannableStringBuilder(getText(), start, end));
  }
  private TdApi.RichText richText (Spanned text) {
    TreeSet<Integer> boundaries = new TreeSet<>();
    boundaries.add(0); boundaries.add(text.length());
    for (Object span : text.getSpans(0, text.length(), Object.class)) {
      if (span instanceof NodeSpan || span instanceof AtomSpan || span instanceof EmojiNode) {
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
      EmojiNode[] emojis = text.getSpans(start, end, EmojiNode.class);
      if (emojis.length > 0) value = ArticleCodec.copy(emojis[0].node, TdApi.RichText.class);
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
    applyFormat(wrapper, false);
  }

  /** Set metadata (for example a link target) without toggling off an existing wrapper. */
  public void setFormat (TdApi.RichText wrapper) { applyFormat(wrapper, true); }

  public void setFormatEnabled (TdApi.RichText wrapper, boolean enabled) { applyFormat(wrapper, true, enabled); }

  public boolean isFormatApplied (int constructor) {
    int start = Math.min(getSelectionStart(), getSelectionEnd()), end = Math.max(getSelectionStart(), getSelectionEnd());
    if (start < 0) return false;
    if (start == end) {
      for (TdApi.RichText format : typingFormats) if (format.getConstructor() == constructor) return true;
      return false;
    }
    NodeSpan[] spans = getText().getSpans(start, end, NodeSpan.class);
    Arrays.sort(spans, Comparator.comparingInt(getText()::getSpanStart));
    int coveredUntil = start;
    for (NodeSpan span : spans) if (span.node.getConstructor() == constructor) {
      if (getText().getSpanStart(span) > coveredUntil) return false;
      coveredUntil = Math.max(coveredUntil, getText().getSpanEnd(span));
      if (coveredUntil >= end) return true;
    }
    return false;
  }

  private void applyFormat (TdApi.RichText wrapper, boolean replace) {
    applyFormat(wrapper, replace, null);
  }

  private void applyFormat (TdApi.RichText wrapper, boolean replace, Boolean enabled) {
    int start = Math.min(getSelectionStart(), getSelectionEnd()), end = Math.max(getSelectionStart(), getSelectionEnd());
    if (start < 0) return;
    if (start == end) {
      for (int i = 0; i < typingFormats.size(); i++) if (typingFormats.get(i).getConstructor() == wrapper.getConstructor()) { typingFormats.remove(i); return; }
      typingFormats.add(wrapper); return;
    }
    Editable text = getText();
    boolean covered = enabled != null ? !enabled : !replace && isFormatApplied(wrapper.getConstructor());
    for (NodeSpan span : text.getSpans(start, end, NodeSpan.class)) {
      boolean conflictingLink = wrapper instanceof TdApi.RichTextDateTime && span.node instanceof TdApi.RichTextUrl || wrapper instanceof TdApi.RichTextUrl && span.node instanceof TdApi.RichTextDateTime;
      boolean conflictingIndex = wrapper instanceof TdApi.RichTextSubscript && span.node instanceof TdApi.RichTextSuperscript || wrapper instanceof TdApi.RichTextSuperscript && span.node instanceof TdApi.RichTextSubscript;
      if (span.node.getConstructor() == wrapper.getConstructor() || conflictingLink || conflictingIndex) {
        int a = text.getSpanStart(span), b = text.getSpanEnd(span);
        text.removeSpan(span);
        if (a < start) text.setSpan(new NodeSpan(span.node, span.order), a, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (b > end) text.setSpan(new NodeSpan(span.node, span.order), end, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    if (!covered) text.setSpan(new NodeSpan(wrapper, order++), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    requestLayout(); invalidate();
    notifyChanged();
    if (selectionListener != null) selectionListener.run();
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
    return span instanceof AtomSpan ? ArticleCodec.copy(((AtomSpan) span).node, TdApi.RichText.class) : span instanceof EmojiNode ? ArticleCodec.copy(((EmojiNode) span).node, TdApi.RichText.class) : span instanceof NodeSpan ? ArticleCodec.copy(((NodeSpan) span).node, TdApi.RichText.class) : null;
  }
  private Object selectedSpan () {
    int start = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
    int end = Math.max(start, Math.max(getSelectionStart(), getSelectionEnd()));
    AtomSpan[] atoms = getText().getSpans(start, end, AtomSpan.class);
    if (atoms.length > 0) return atoms[0];
    EmojiNode[] emojis = getText().getSpans(start, end, EmojiNode.class);
    if (emojis.length > 0) return emojis[0];
    NodeSpan[] nodes = getText().getSpans(start, end, NodeSpan.class);
    for (NodeSpan node : nodes) if (!(node.node instanceof TdApi.RichTextBold || node.node instanceof TdApi.RichTextItalic || node.node instanceof TdApi.RichTextUnderline || node.node instanceof TdApi.RichTextStrikethrough || node.node instanceof TdApi.RichTextFixed || node.node instanceof TdApi.RichTextSpoiler || node.node instanceof TdApi.RichTextMarked || node.node instanceof TdApi.RichTextSuperscript || node.node instanceof TdApi.RichTextSubscript)) return node;
    return null;
  }
  public void updateSelectedElement (TdApi.RichText value) {
    Object span = selectedSpan(); if (span == null) return;
    int start = getText().getSpanStart(span), end = getText().getSpanEnd(span);
    if (span instanceof EmojiNode) { setSelection(start, end); insert(value); return; }
    getText().removeSpan(span);
    if (span instanceof AtomSpan) getText().setSpan(new AtomSpan(getContext(), value), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    else getText().setSpan(new NodeSpan(value, ((NodeSpan) span).order), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    requestLayout(); invalidate(); notifyChanged();
  }

  private void notifyChanged () { if (listener != null) listener.runWithData(richText()); }

  private boolean backspaceBoundary () {
    return boundaryListener != null && getSelectionStart() == 0 && getSelectionEnd() == 0 && boundaryListener.onBackspace();
  }
  @Override public boolean onKeyDown (int keyCode, KeyEvent event) {
    if (documentActions != null) {
      if (event.isCtrlPressed()) {
        int action = keyCode == KeyEvent.KEYCODE_A ? android.R.id.selectAll : keyCode == KeyEvent.KEYCODE_C ? android.R.id.copy : keyCode == KeyEvent.KEYCODE_X ? android.R.id.cut : keyCode == KeyEvent.KEYCODE_V ? android.R.id.paste : 0;
        if (action != 0 && documentActions.menu(action)) return true;
      }
      if (keyCode == KeyEvent.KEYCODE_DEL && documentActions.replace("")) return true;
      int character = event.getUnicodeChar();
      if (!event.isCtrlPressed() && character != 0 && documentActions.replace(new String(Character.toChars(character)))) return true;
    }
    if (keyCode == KeyEvent.KEYCODE_DEL && backspaceBoundary()) return true;
    if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed() && boundaryListener != null && boundaryListener.onEnter()) return true;
    return super.onKeyDown(keyCode, event);
  }
  @Override public boolean onTextContextMenuItem (int id) {
    return documentActions != null && documentActions.menu(id) || super.onTextContextMenuItem(id);
  }
  @Override public boolean onDragEvent (android.view.DragEvent event) {
    // Block moves belong to the document; EditText otherwise consumes the drop as text.
    return !(event.getLocalState() instanceof org.thunderdog.challegram.data.article.ArticleEditorTree.Entry) && super.onDragEvent(event);
  }
  @Override protected InputConnection createInputConnection (EditorInfo info) {
    InputConnection base = super.createInputConnection(info);
    if (base == null) return null;
    return new InputConnectionWrapper(base, false) {
      @Override public boolean commitText (CharSequence text, int position) {
        if (documentActions != null && documentActions.replace(text)) return true;
        if ("\n".contentEquals(text) && boundaryListener != null) {
          super.finishComposingText();
          if (boundaryListener.onEnter()) return true;
        }
        return super.commitText(text, position);
      }
      @Override public boolean deleteSurroundingText (int before, int after) {
        if (before > 0 && documentActions != null && documentActions.replace("")) return true;
        return before == 1 && after == 0 && backspaceBoundary() || super.deleteSurroundingText(before, after);
      }
      @Override public boolean deleteSurroundingTextInCodePoints (int before, int after) {
        if (before > 0 && documentActions != null && documentActions.replace("")) return true;
        return before == 1 && after == 0 && backspaceBoundary() || super.deleteSurroundingTextInCodePoints(before, after);
      }
    };
  }
}
