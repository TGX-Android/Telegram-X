/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.article.ArticleEditorTree;
import org.thunderdog.challegram.data.article.ArticleListLabels;
import org.thunderdog.challegram.data.article.ArticleRichText;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** The editable document itself: no property cards or parallel plain-text representation. */
public final class ArticleDocumentView extends LinearLayout {
  public interface Delegate {
    void changed (boolean structural);
    void selection (ArticleTextInput input);
    void options (ArticleEditorTree.Entry entry);
    void media (ArticleEditorTree.Entry entry, LinearLayout parent);
    void formula (ArticleEditorTree.Entry entry, TextView view);
  }
  private final Delegate delegate;
  private TdApi.InputRichMessage document;
  private final List<ArticleTextInput> inputs = new ArrayList<>();
  private final IdentityHashMap<ArticleTextInput, ArticleEditorTree.Entry> entries = new IdentityHashMap<>();
  private final IdentityHashMap<TdApi.InputPageBlock, ListContext> listContexts = new IdentityHashMap<>();
  private ArticleTextInput focused;
  private TdApi.InputPageBlock focusAfter;
  private int focusOffset;
  private int focusEndOffset = -1;
  private static final class ListContext {
    final ArticleEditorTree.Entry parent; final TdApi.InputPageBlockList list; final int index;
    ListContext (ArticleEditorTree.Entry parent, TdApi.InputPageBlockList list, int index) { this.parent = parent; this.list = list; this.index = index; }
  }
  public ArticleDocumentView (Context context, Delegate delegate) {
    super(context); this.delegate = delegate; setOrientation(VERTICAL); setPadding(Screen.dp(16), Screen.dp(64), Screen.dp(16), Screen.dp(40));
  }
  public List<ArticleTextInput> inputs () { return inputs; }
  public ArticleEditorTree.Entry focusedEntry () { return entries.get(focused); }
  public TdApi.InputPageBlock selectedBlock () {
    ArticleEditorTree.Entry quote = focusedQuote();
    ArticleEditorTree.Entry entry = focusedEntry();
    return quote != null ? quote.block : entry == null ? null : entry.block;
  }
  private ArticleEditorTree.Entry focusedQuote () {
    ArticleEditorTree.Entry entry = focusedEntry();
    return entry == null ? null : findQuote(ArticleEditorTree.root(document), entry.block, null);
  }
  private ArticleEditorTree.Entry findQuote (ArticleEditorTree.Group group, TdApi.InputPageBlock target, ArticleEditorTree.Entry parent) {
    for (int i = 0; i < group.blocks().length; i++) {
      ArticleEditorTree.Entry entry = new ArticleEditorTree.Entry(group, i, 0);
      boolean quote = entry.block instanceof TdApi.InputPageBlockBlockQuote || entry.block instanceof TdApi.InputPageBlockExpandableBlockQuote || entry.block instanceof TdApi.InputPageBlockPullQuote;
      if (entry.block == target) return quote ? entry : parent;
      for (ArticleEditorTree.Group child : ArticleEditorTree.children(entry.block)) {
        ArticleEditorTree.Entry found = findQuote(child, target, quote ? entry : parent);
        if (found != null) return found;
      }
    }
    return null;
  }
  public int selectedListStyle () {
    ArticleEditorTree.Entry entry = focusedEntry(); if (entry == null) return 0;
    if (entry.block instanceof TdApi.InputPageBlockDetails) return 4;
    ListContext list = listContexts.get(entry.block);
    if (list == null) return 0;
    TdApi.InputPageBlockListItem item = list.list.items[list.index];
    return item.hasCheckbox ? 3 : item.value != 0 ? 2 : 1;
  }
  public boolean canChangeTextStyle () {
    ArticleEditorTree.Entry entry = focusedEntry(); if (entry == null) return false;
    TdApi.InputPageBlock block = entry.block;
    boolean supported = block instanceof TdApi.InputPageBlockParagraph || block instanceof TdApi.InputPageBlockSectionHeading || block instanceof TdApi.InputPageBlockPreformatted || block instanceof TdApi.InputPageBlockFooter || block instanceof TdApi.InputPageBlockPullQuote || block instanceof TdApi.InputPageBlockExpandableBlockQuote;
    if (!supported) return false;
    for (ArticleTextInput input : inputs) if (entries.get(input).block == block) return input == focused;
    return false;
  }
  public boolean canIndent () {
    ArticleEditorTree.Entry entry = focusedEntry(); if (entry == null) return false;
    ListContext list = listContexts.get(entry.block);
    return list == null ? canChangeTextStyle() : list.index > 0;
  }
  public boolean canOutdent () { ArticleEditorTree.Entry entry = focusedEntry(); return entry != null && listContexts.containsKey(entry.block); }
  public boolean showTableMenu (View anchor) {
    if (focused == null) return false;
    for (android.view.ViewParent parent = focused.getParent(); parent != null; parent = parent.getParent()) {
      if (parent instanceof ArticleTableEditor) return ((ArticleTableEditor) parent).showCellMenu(focused, anchor);
    }
    return false;
  }
  public void bind (TdApi.InputRichMessage value) {
    ArticleEditorTree.Entry old = focusedEntry();
    TdApi.InputPageBlock target = focusAfter != null ? focusAfter : old == null ? null : old.block;
    int offset = focusAfter != null ? focusOffset : focused == null ? 0 : focused.getSelectionStart();
    int endOffset = focusAfter != null && focusEndOffset >= 0 ? focusEndOffset : offset;
    int subfield = 0;
    if (focusAfter == null && old != null) for (ArticleTextInput input : inputs) { if (input == focused) break; if (entries.get(input).block == old.block) subfield++; }
    removeAllViews(); inputs.clear(); entries.clear(); listContexts.clear(); focused = null; document = value;
    if (android.os.Build.VERSION.SDK_INT >= 17) setLayoutDirection(value.isRtl ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
    renderGroup(this, ArticleEditorTree.root(value), 0, null);
    if (inputs.isEmpty() || !(ArticleEditorTree.root(value).blocks()[ArticleEditorTree.root(value).blocks().length - 1] instanceof TdApi.InputPageBlockParagraph)) {
      // A trailing caret is inserted into the model only when the user types.
      ArticleTextInput tail = new ArticleTextInput(getContext(), new TdApi.RichTextPlain(""), text -> {
        TdApi.InputPageBlockParagraph paragraph = new TdApi.InputPageBlockParagraph(text);
        ArticleEditorTree.Group root = ArticleEditorTree.root(document); root.insert(root.blocks().length, paragraph);
        focusAfter = paragraph; focusOffset = ArticleRichText.plain(text).length(); delegate.changed(true);
      });
      tail.setHint(inputs.isEmpty() ? Lang.getString(R.string.ArticleWrite) : ""); tail.setMinHeight(Screen.dp(48)); addView(tail, new LayoutParams(-1, -2));
      tail.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) { focused = null; delegate.selection(tail); } });
    }
    for (ArticleTextInput input : inputs) if (entries.get(input).block == target && subfield-- == 0) {
      input.requestFocus(); focused = input; input.setSelection(Math.max(0, Math.min(input.length(), offset)), Math.max(0, Math.min(input.length(), endOffset))); break;
    }
    focusAfter = null;
    focusEndOffset = -1;
  }
  private void renderGroup (LinearLayout parent, ArticleEditorTree.Group group, int depth, ListContext listContext) {
    for (int i = 0; i < group.blocks().length; i++) {
      ArticleEditorTree.Entry entry = new ArticleEditorTree.Entry(group, i, depth);
      if (listContext != null) listContexts.put(entry.block, listContext);
      render(parent, entry);
    }
  }
  private LinearLayout column () { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(VERTICAL); return view; }
  private void render (LinearLayout parent, ArticleEditorTree.Entry entry) {
    TdApi.InputPageBlock block = entry.block;
    LinearLayout section = column(); parent.addView(section, new LayoutParams(-1, -2));
    section.setOnLongClickListener(v -> { delegate.options(entry); return true; });
    if (block instanceof TdApi.InputPageBlockList) {
      TdApi.InputPageBlockList list = (TdApi.InputPageBlockList) block;
      for (int i = 0; i < list.items.length; i++) {
        TdApi.InputPageBlockListItem item = list.items[i]; LinearLayout line = new LinearLayout(getContext()); line.setGravity(Gravity.TOP);
        if (item.hasCheckbox) {
          CheckBox check = new CheckBox(getContext()); check.setChecked(item.isChecked); if (android.os.Build.VERSION.SDK_INT >= 21) check.setButtonTintList(android.content.res.ColorStateList.valueOf(Theme.textLinkColor()));
          check.setOnCheckedChangeListener((v, checked) -> { item.isChecked = checked; delegate.changed(false); }); line.addView(check, new LayoutParams(Screen.dp(32), Screen.dp(36)));
        } else {
          TextView label = new TextView(getContext()); label.setText(item.value == 0 ? "•" : ArticleListLabels.label(item.value, item.type)); label.setTextSize(16); label.setTextColor(Theme.textAccentColor()); label.setGravity(Gravity.END); label.setPadding(0, Screen.dp(5), Screen.dp(8), 0); line.addView(label, new LayoutParams(Screen.dp(30), -2));
        }
        LinearLayout body = column(); line.addView(body, new LayoutParams(0, -2, 1)); section.addView(line);
        renderGroup(body, new ArticleEditorTree.Group(() -> item.blocks, items -> item.blocks = items), entry.depth + 1, new ListContext(entry, list, i));
      }
      return;
    }
    if (block instanceof TdApi.InputPageBlockTable) {
      TdApi.InputPageBlockTable table = (TdApi.InputPageBlockTable) block;
      ArticleTextInput caption = input(entry, table.caption, value -> table.caption = value, false); caption.setGravity(Gravity.CENTER); caption.setTypeface(null, Typeface.BOLD); caption.setHint(Lang.getString(R.string.ArticleTableTitle)); section.addView(caption);
      HorizontalScrollView scroll = new HorizontalScrollView(getContext()); scroll.setFillViewport(true); scroll.setClipToPadding(false);
      scroll.addView(new ArticleTableEditor(getContext(), table, cell -> register(cell, entry), () -> delegate.changed(false), () -> delegate.changed(true)));
      section.addView(scroll, new LayoutParams(-1, -2)); return;
    }
    if (block instanceof TdApi.InputPageBlockDivider) {
      View line = new View(getContext()); line.setBackgroundColor(0x66888888); LayoutParams params = new LayoutParams(-1, Screen.dp(1)); params.topMargin = params.bottomMargin = Screen.dp(16); section.addView(line, params); return;
    }
    if (block instanceof TdApi.InputPageBlockMathematicalExpression) {
      TextView formula = new TextView(getContext()); formula.setTextColor(Theme.textAccentColor()); formula.setTextSize(18); formula.setGravity(Gravity.CENTER); formula.setMinHeight(Screen.dp(48)); delegate.formula(entry, formula); section.addView(formula, new LayoutParams(-1, -2)); return;
    }
    if (block instanceof TdApi.InputPageBlockAnchor) {
      TdApi.InputPageBlockAnchor anchor = (TdApi.InputPageBlockAnchor) block;
      ArticleTextInput input = input(entry, new TdApi.RichTextPlain(anchor.name), value -> anchor.name = ArticleRichText.plain(value), false); input.setHint(Lang.getString(R.string.ArticleAnchor)); section.addView(input); return;
    }
    if (block instanceof TdApi.InputPageBlockDetails) {
      TdApi.InputPageBlockDetails details = (TdApi.InputPageBlockDetails) block;
      LinearLayout title = new LinearLayout(getContext()); TextView toggle = new TextView(getContext()); toggle.setText(details.isOpen ? "⌄" : "›"); toggle.setTextColor(Theme.textLinkColor()); toggle.setTextSize(22); toggle.setGravity(Gravity.CENTER); title.addView(toggle, new LayoutParams(Screen.dp(28), Screen.dp(38)));
      toggle.setOnClickListener(v -> { details.isOpen = !details.isOpen; delegate.changed(true); });
      ArticleTextInput input = input(entry, details.header, value -> details.header = value, false); input.setTypeface(null, Typeface.BOLD); title.addView(input, new LayoutParams(0, -2, 1)); section.addView(title);
      if (details.isOpen) { LinearLayout children = column(); children.setPadding(Screen.dp(28), 0, 0, 0); section.addView(children); renderGroup(children, ArticleEditorTree.children(block).get(0), entry.depth + 1, null); }
      return;
    }
    boolean quote = block instanceof TdApi.InputPageBlockBlockQuote || block instanceof TdApi.InputPageBlockExpandableBlockQuote || block instanceof TdApi.InputPageBlockPullQuote;
    if (quote) { section.setPadding(Screen.dp(12), Screen.dp(4), Screen.dp(8), Screen.dp(4)); section.setBackground(ArticleEditorPopup.background(0x18888888, 4)); }
    if (block instanceof TdApi.InputPageBlockBlockQuote) renderGroup(section, ArticleEditorTree.children(block).get(0), entry.depth + 1, null);
    TdApi.PageBlockCaption mediaCaption = ArticleEditorTree.caption(block);
    if (mediaCaption != null) delegate.media(entry, section);
    if (block instanceof TdApi.InputPageBlockCollage || block instanceof TdApi.InputPageBlockSlideshow) {
      ArticleEditorTree.Group children = ArticleEditorTree.children(block).get(0);
      for (int i = 0; i < children.blocks().length; i++) {
        ArticleEditorTree.Entry child = new ArticleEditorTree.Entry(children, i, entry.depth + 1);
        for (ArticleEditorTree.TextField field : ArticleEditorTree.fields(child.block)) if (!ArticleRichText.plain(field.value).isEmpty()) {
          ArticleTextInput caption = input(child, field.value, field.set, false); caption.setTextSize(14); section.addView(caption, new LayoutParams(-1, -2));
        }
      }
    }
    for (ArticleEditorTree.TextField field : ArticleEditorTree.fields(block)) {
      // Empty credit lines are available in the block menu; they do not add blank rows.
      if (field.name == ArticleEditorTree.FieldName.CREDIT && ArticleRichText.plain(field.value).isEmpty()) continue;
      ArticleTextInput input = input(entry, field.value, field.set, field.name == ArticleEditorTree.FieldName.TEXT);
      input.setHint(Lang.getString(field.name == ArticleEditorTree.FieldName.CAPTION ? R.string.ArticleCaption : R.string.ArticleWrite));
      if (block instanceof TdApi.InputPageBlockSectionHeading) { input.setTextSize(20 - ((TdApi.InputPageBlockSectionHeading) block).size); input.setTypeface(null, Typeface.BOLD); }
      if (block instanceof TdApi.InputPageBlockPreformatted) { input.setTypeface(Typeface.MONOSPACE); input.setTextSize(15); input.setBackground(ArticleEditorPopup.background(0x18888888, 6)); input.setPadding(Screen.dp(10), Screen.dp(8), Screen.dp(10), Screen.dp(8)); }
      if (block instanceof TdApi.InputPageBlockFooter || field.name == ArticleEditorTree.FieldName.CREDIT) { input.setTextSize(14); input.setTextColor(Theme.textDecentColor()); }
      if (block instanceof TdApi.InputPageBlockButtonRow) { input.setTextColor(Theme.textLinkColor()); input.setBackground(ArticleEditorPopup.background(0x22888888, 8)); }
      section.addView(input, new LayoutParams(-1, -2));
    }
  }
  private ArticleTextInput input (ArticleEditorTree.Entry entry, TdApi.RichText value, Consumer<TdApi.RichText> setter, boolean boundaries) {
    ArticleTextInput input = new ArticleTextInput(getContext(), value, text -> { setter.accept(text); delegate.changed(false); }); register(input, entry);
    if (boundaries && !(entry.block instanceof TdApi.InputPageBlockPreformatted)) input.setBoundaryListener(new ArticleTextInput.BoundaryListener() {
      @Override public boolean onEnter () { return split(input, entry, setter); }
      @Override public boolean onBackspace () { return join(input, entry); }
    });
    return input;
  }
  private void register (ArticleTextInput input, ArticleEditorTree.Entry entry) {
    inputs.add(input); entries.put(input, entry);
    input.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) { focused = input; delegate.selection(input); } });
    input.setSelectionListener(() -> { if (input.hasFocus()) { focused = input; delegate.selection(input); } });
  }
  private void structural (TdApi.InputPageBlock target, int offset) { focusAfter = target; focusOffset = offset; delegate.changed(true); }
  private boolean split (ArticleTextInput input, ArticleEditorTree.Entry entry, Consumer<TdApi.RichText> setter) {
    int start = Math.max(0, Math.min(input.getSelectionStart(), input.getSelectionEnd())), end = Math.max(start, Math.max(input.getSelectionStart(), input.getSelectionEnd()));
    ListContext list = listContexts.get(entry.block);
    if (list != null && input.length() == 0 && list.list.items[list.index].blocks.length == 1) { exitList(list); return true; }
    TdApi.InputPageBlockParagraph next = new TdApi.InputPageBlockParagraph(input.richText(end, input.length()));
    setter.accept(input.richText(0, start));
    if (list != null) {
      TdApi.InputPageBlockListItem item = list.list.items[list.index];
      TdApi.InputPageBlock[] following = new TdApi.InputPageBlock[item.blocks.length - entry.index]; following[0] = next;
      System.arraycopy(item.blocks, entry.index + 1, following, 1, following.length - 1); item.blocks = Arrays.copyOf(item.blocks, entry.index + 1);
      List<TdApi.InputPageBlockListItem> items = new ArrayList<>(Arrays.asList(list.list.items));
      items.add(list.index + 1, new TdApi.InputPageBlockListItem(following, item.hasCheckbox, false, item.value == 0 ? 0 : item.value + 1, item.type));
      list.list.items = items.toArray(new TdApi.InputPageBlockListItem[0]); renumber(list.list);
    } else entry.group.insert(entry.index + 1, next);
    structural(next, 0); return true;
  }
  private static TdApi.RichText concat (TdApi.RichText a, TdApi.RichText b) { return new TdApi.RichTexts(new TdApi.RichText[] {a, b}); }
  private boolean join (ArticleTextInput input, ArticleEditorTree.Entry entry) {
    ListContext list = listContexts.get(entry.block);
    if (entry.index == 0 && list != null) { indent(true); return true; }
    if (entry.index == 0) return false;
    TdApi.InputPageBlock previous = entry.group.blocks()[entry.index - 1];
    List<ArticleEditorTree.TextField> fields = ArticleEditorTree.fields(previous);
    if (fields.isEmpty() || fields.get(0).name != ArticleEditorTree.FieldName.TEXT) return false;
    ArticleEditorTree.TextField field = fields.get(0); int position = 0;
    for (ArticleTextInput candidate : inputs) if (entries.get(candidate).block == previous) { position = candidate.length(); break; }
    field.set.accept(concat(field.value, input.richText())); entry.group.remove(entry.index); structural(previous, position); return true;
  }
  private void exitList (ListContext context) {
    TdApi.InputPageBlockListItem item = context.list.items[context.index]; int insertion = context.parent.index;
    context.parent.group.remove(insertion);
    if (context.index > 0) context.parent.group.insert(insertion++, new TdApi.InputPageBlockList(Arrays.copyOf(context.list.items, context.index)));
    for (TdApi.InputPageBlock block : item.blocks) context.parent.group.insert(insertion++, block);
    if (context.index + 1 < context.list.items.length) context.parent.group.insert(insertion, new TdApi.InputPageBlockList(Arrays.copyOfRange(context.list.items, context.index + 1, context.list.items.length)));
    structural(item.blocks.length == 0 ? null : item.blocks[0], 0);
  }
  private static void renumber (TdApi.InputPageBlockList list) { for (int i = 1; i < list.items.length; i++) if (list.items[i].value != 0 && list.items[i - 1].value != 0) list.items[i].value = list.items[i - 1].value + 1; }
  public void listStyle (int style) {
    ArticleEditorTree.Entry entry = focusedEntry(); if (entry == null) return;
    ListContext context = listContexts.get(entry.block);
    if (style == 0) {
      if (context != null) exitList(context);
      else if (entry.block instanceof TdApi.InputPageBlockDetails) {
        TdApi.InputPageBlockDetails details = (TdApi.InputPageBlockDetails) entry.block;
        TdApi.InputPageBlockParagraph header = new TdApi.InputPageBlockParagraph(details.header);
        entry.group.replace(entry.index, header);
        for (int i = 0; i < details.blocks.length; i++) entry.group.insert(entry.index + 1 + i, details.blocks[i]);
        structural(header, 0);
      }
      return;
    }
    TdApi.InputPageBlockList list;
    if (context == null) {
      list = new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {entry.block}, style == 3, false, style == 2 ? 1 : 0, style == 2 ? "1" : "")});
      entry.group.replace(entry.index, list);
    } else list = context.list;
    for (int i = 0; i < list.items.length; i++) { list.items[i].hasCheckbox = style == 3; list.items[i].value = style == 2 ? i + 1 : 0; list.items[i].type = style == 2 ? "1" : ""; }
    structural(entry.block, focused == null ? 0 : focused.getSelectionStart());
  }
  public void indent (boolean outdent) {
    ArticleEditorTree.Entry entry = focusedEntry(); if (entry == null) return; ListContext context = listContexts.get(entry.block);
    if (context == null) { if (!outdent && canChangeTextStyle()) listStyle(1); return; }
    if (outdent) {
      ListContext outer = listContexts.get(context.parent.block);
      if (outer == null) { exitList(context); return; }
      TdApi.InputPageBlockListItem item = context.list.items[context.index];
      List<TdApi.InputPageBlockListItem> remaining = new ArrayList<>(Arrays.asList(context.list.items)); remaining.remove(context.index); context.list.items = remaining.toArray(new TdApi.InputPageBlockListItem[0]);
      List<TdApi.InputPageBlockListItem> outerItems = new ArrayList<>(Arrays.asList(outer.list.items)); outerItems.add(outer.index + 1, item); outer.list.items = outerItems.toArray(new TdApi.InputPageBlockListItem[0]);
      if (remaining.isEmpty()) context.parent.group.remove(context.parent.index); renumber(outer.list);
    } else if (context.index > 0) {
      TdApi.InputPageBlockListItem item = context.list.items[context.index], previous = context.list.items[context.index - 1];
      TdApi.InputPageBlock last = previous.blocks.length == 0 ? null : previous.blocks[previous.blocks.length - 1];
      TdApi.InputPageBlockList nested = last instanceof TdApi.InputPageBlockList ? (TdApi.InputPageBlockList) last : new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[0]);
      if (nested != last) { previous.blocks = Arrays.copyOf(previous.blocks, previous.blocks.length + 1); previous.blocks[previous.blocks.length - 1] = nested; }
      nested.items = Arrays.copyOf(nested.items, nested.items.length + 1); nested.items[nested.items.length - 1] = item;
      List<TdApi.InputPageBlockListItem> remaining = new ArrayList<>(Arrays.asList(context.list.items)); remaining.remove(context.index); context.list.items = remaining.toArray(new TdApi.InputPageBlockListItem[0]); renumber(context.list); renumber(nested);
    } else return;
    structural(entry.block, 0);
  }
  public void convert (Function<TdApi.RichText, TdApi.InputPageBlock> converter) {
    ArticleEditorTree.Entry entry = focusedEntry(); if (entry == null || focused == null) return;
    if (!canChangeTextStyle()) return;
    ArticleEditorTree.Entry quote = focusedQuote();
    TdApi.InputPageBlock block = converter.apply(focused.richText()); entry.group.replace(entry.index, block);
    if (quote != null && quote.block != entry.block && quote.block instanceof TdApi.InputPageBlockBlockQuote) unwrapQuote(quote);
    structural(block, focused.getSelectionStart());
  }
  private void unwrapQuote (ArticleEditorTree.Entry quote) {
    TdApi.InputPageBlockBlockQuote block = (TdApi.InputPageBlockBlockQuote) quote.block;
    quote.group.remove(quote.index);
    int position = quote.index;
    for (TdApi.InputPageBlock child : block.blocks) quote.group.insert(position++, child);
    if (!ArticleRichText.plain(block.credit).isEmpty()) quote.group.insert(position, new TdApi.InputPageBlockFooter(block.credit));
  }
  public void toggleQuoteSelection () {
    ArticleEditorTree.Entry entry = focusedEntry(); if (entry == null || focused == null || !canChangeTextStyle()) return;
    ArticleEditorTree.Entry quote = focusedQuote();
    if (quote != null) {
      if (quote.block instanceof TdApi.InputPageBlockBlockQuote) { unwrapQuote(quote); structural(entry.block, focused.getSelectionStart()); }
      else convert(TdApi.InputPageBlockParagraph::new);
      return;
    }
    int start = Math.max(0, Math.min(focused.getSelectionStart(), focused.getSelectionEnd())), end = Math.max(start, Math.max(focused.getSelectionStart(), focused.getSelectionEnd()));
    if (start == end) { start = 0; end = focused.length(); }
    TdApi.InputPageBlockParagraph selected = new TdApi.InputPageBlockParagraph(focused.richText(start, end));
    TdApi.RichText before = focused.richText(0, start), after = focused.richText(end, focused.length());
    int at = entry.index; entry.group.remove(at);
    if (start > 0) entry.group.insert(at++, new TdApi.InputPageBlockParagraph(before));
    entry.group.insert(at++, new TdApi.InputPageBlockBlockQuote(new TdApi.InputPageBlock[] {selected}, new TdApi.RichTextPlain("")));
    if (end < focused.length()) entry.group.insert(at, new TdApi.InputPageBlockParagraph(after));
    focusEndOffset = end - start;
    structural(selected, 0);
  }
  public void insert (TdApi.InputPageBlock block) {
    ArticleEditorTree.Entry entry = focusedEntry(); ArticleEditorTree.Group group = entry == null ? ArticleEditorTree.root(document) : entry.group;
    int index = entry == null ? group.blocks().length : entry.index + 1;
    // Insert at the caret, retaining rich text on both sides of an attachment/table.
    if (entry != null && entry.block instanceof TdApi.InputPageBlockParagraph && focused != null) {
      int start = Math.max(0, Math.min(focused.getSelectionStart(), focused.getSelectionEnd())), end = Math.max(start, Math.max(focused.getSelectionStart(), focused.getSelectionEnd()));
      TdApi.RichText after = focused.richText(end, focused.length()); ((TdApi.InputPageBlockParagraph) entry.block).text = focused.richText(0, start);
      if (start == 0) { group.remove(entry.index); index--; }
      group.insert(index, block); group.insert(index + 1, new TdApi.InputPageBlockParagraph(after));
    } else group.insert(index, block);
    structural(block, 0);
  }
  public boolean replaceSelection (ArticleTextInput target, int start, int end, TdApi.InputPageBlock[] replacement) {
    ArticleEditorTree.Entry entry = entries.get(target);
    if (entry == null || start < 0 || end < start || end > target.length()) return false;
    if (replacement.length == 1 && replacement[0] instanceof TdApi.InputPageBlockParagraph) {
      target.setSelection(start, end); target.insert(((TdApi.InputPageBlockParagraph) replacement[0]).text); return true;
    }
    // Captions and table cells cannot contain block nodes. Keep the original if AI
    // returns a structure which this selection cannot represent losslessly.
    if (!(entry.block instanceof TdApi.InputPageBlockParagraph)) return false;
    TdApi.RichText before = target.richText(0, start), after = target.richText(end, target.length());
    int index = entry.index; entry.group.remove(index);
    if (start > 0) entry.group.insert(index++, new TdApi.InputPageBlockParagraph(before));
    for (TdApi.InputPageBlock block : replacement) entry.group.insert(index++, block);
    TdApi.InputPageBlockParagraph tail = new TdApi.InputPageBlockParagraph(after); entry.group.insert(index, tail);
    structural(tail, 0); return true;
  }
  public void insertBlocks (TdApi.InputPageBlock[] blocks) {
    ArticleEditorTree.Entry entry = focusedEntry();
    if (entry != null && entry.block instanceof TdApi.InputPageBlockParagraph && focused != null) {
      int at = Math.max(0, focused.getSelectionStart()); replaceSelection(focused, at, at, blocks); return;
    }
    ArticleEditorTree.Group group = entry == null ? ArticleEditorTree.root(document) : entry.group;
    int at = entry == null ? group.blocks().length : entry.index + 1;
    for (TdApi.InputPageBlock block : blocks) group.insert(at++, block);
    TdApi.InputPageBlockParagraph tail = new TdApi.InputPageBlockParagraph(new TdApi.RichTextPlain("")); group.insert(at, tail); structural(tail, 0);
  }
}
