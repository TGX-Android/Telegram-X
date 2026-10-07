/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.Gravity;
import org.thunderdog.challegram.widget.ArticleDocumentView;
import org.thunderdog.challegram.widget.ArticleEditorPopup;
import org.thunderdog.challegram.widget.ArticleEditorMedia;
import org.thunderdog.challegram.widget.ArticleFormulaEditor;
import org.thunderdog.challegram.tool.Keyboard;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.article.ArticleDocument;
import org.thunderdog.challegram.data.article.ArticleFiles;
import org.thunderdog.challegram.data.article.ArticleAiResult;
import org.thunderdog.challegram.data.article.ArticleDraftStore;
import org.thunderdog.challegram.data.article.ArticleEditorTree;
import org.thunderdog.challegram.data.article.ArticleHistory;
import org.thunderdog.challegram.data.article.ArticleMediaFiles;
import org.thunderdog.challegram.data.article.ArticleSendTracker;
import org.thunderdog.challegram.data.article.ArticleCodec;
import org.thunderdog.challegram.data.article.ArticlePreviewMapper;
import org.thunderdog.challegram.data.article.ArticleRichText;
import org.thunderdog.challegram.data.article.ArticleValidator;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ArticleTextInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import tgx.td.Td;
import tgx.td.client.TdlibOptions;

/** Inline rich-document composition. Drafts, history and transmission retain the complete TDLib tree. */
public final class ArticleEditorController extends ViewController<ArticleEditorController.Args> {
  public static final class Args {
    public final MessagesController owner;
    public final long chatId, messageId, userId;
    public final TdApi.MessageTopic topic;
    public final ArticleDocument document;
    public boolean fromComposer;
    public final java.util.Map<String, Integer> localDraftFiles = new java.util.HashMap<>();
    public Args (MessagesController owner, long messageId, ArticleDocument document) {
      this.owner = owner; this.chatId = owner.getChatId(); this.topic = owner.getMessageTopicId(); this.messageId = messageId; this.document = document; this.userId = owner.tdlib().myUserId();
    }
  }

  private TdApi.InputRichMessage working;
  private ArticleDocument baseline;
  private ArticleSendTracker sendTracker;
  private long pendingMessageId;
  private static final int PICK_MEDIA = 4181;
  private static final ExecutorService IMPORTS = Executors.newSingleThreadExecutor();
  private ArticleHistory history;
  private ArticleDraftStore store;
  private ArticleDocumentView fields;
  private ScrollView scroll;
  private ArticleTextInput focused;
  private final List<ArticleTextInput> textInputs = new ArrayList<>();
  private boolean restoring;
  private ImageView sendButton, undoButton, redoButton;
  private LinearLayout tools;
  private LinearLayout bottom;
  private boolean editingArticle;
  private final java.util.Map<ImageView, Integer> formatButtons = new java.util.LinkedHashMap<>();
  private ImageView textTool, listTool, quoteTool, linkTool, dateTool, inlineButtonTool;
  private View toolAnchor;
  private boolean formatsVisible;
  private org.thunderdog.challegram.widget.EmojiLayout emojiPanel;
  private android.app.Dialog formulaDialog;
  private android.app.Dialog dateDialog;
  private android.app.Dialog aiDialog;
  private final List<ArticleEditorMedia> mediaViews = new ArrayList<>();
  private LinearLayout root;
  private View emptyHeader;
  private boolean sending, sent, draftWriteFailed, recoveryPending, importing;
  private int pendingImports;
  private boolean draftTouched;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable saveDraft = this::saveDraft;

  public ArticleEditorController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_articleEditor; }
  @Override public CharSequence getName () { return Lang.getString(getArgumentsStrict().messageId == 0 ? R.string.ArticleCreate : R.string.ArticleEdit); }
  @Override public View getCustomHeaderCell () {
    // The document owns its navigation controls. A default title also intercepts their touches.
    if (emptyHeader == null) {
      emptyHeader = new View(context());
      emptyHeader.setLayoutParams(new FrameLayout.LayoutParams(0, 0));
    }
    return emptyHeader;
  }
  @Override protected int getBackButton () { return BackHeaderButton.TYPE_NONE; }
  @Override protected int getHeaderHeight () { return 0; }
  @Override protected boolean usePopupMode () { return true; }
  @Override protected boolean useDropShadow () { return false; }

  @Override protected View onCreateView (Context context) {
    Args args = getArgumentsStrict();
    ArticleDocument initial = args.document;
    baseline = args.document;
    ArticleDraftStore.Snapshot recovery = null;
    try {
      store = new ArticleDraftStore(context, tdlib.id(), args.userId, args.chatId, args.topic, args.messageId);
      recovery = store.read();
      // A newly typed ordinary draft is authoritative over an old, already-cleared article draft.
      if (args.fromComposer && recovery != null && recovery.pendingMessageId == 0) recovery = null;
      draftTouched = args.fromComposer || recovery != null;
      if (recovery != null && (recovery.baseline.hasSameContent(initial, args.localDraftFiles) || recovery.document.hasSameContent(initial, args.localDraftFiles) || recovery.pendingMessageId != 0)) {
        initial = recovery.document; baseline = recovery.baseline; pendingMessageId = recovery.pendingMessageId;
      } else if (recovery != null) recoveryPending = true;
    } catch (IOException | IllegalStateException e) {
      store = null; // Keep the unreadable draft for recovery; do not overwrite it.
      UI.showToast(R.string.ArticleDraftRestoreFailed, Toast.LENGTH_LONG);
    }
    working = initial.toInput();
    history = new ArticleHistory(initial);
    createEditorLayout(context, args.messageId != 0);
    if (args.fromComposer && !textInputs.isEmpty()) {
      ArticleTextInput last = textInputs.get(textInputs.size() - 1);
      last.requestFocus(); last.setSelection(last.length());
      handler.postDelayed(() -> { if (!isDestroyed()) Keyboard.show(last); }, 200);
    }
    if (recoveryPending) {
      ArticleDraftStore.Snapshot recovered = recovery;
      resolveDraftConflict(recovered, args);
    }
    if (pendingMessageId != 0) {
      setSending(true);
      createSendTracker(new ArticleDocument(working));
      tdlib.send(new TdApi.GetMessage(args.chatId, pendingMessageId), (message, error) -> {
        if (message != null) sendTracker.accept(message);
        else handler.post(() -> {
          if (isDestroyed()) { sendTracker.cancel(); return; }
          if (sendTracker.isComplete()) return;
          new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticlePendingRecovery))
          .setMessage(Lang.getString(R.string.ArticlePendingRecoveryHint)).setCancelable(false)
          .setPositiveButton(Lang.getString(R.string.ArticleContinueEditing), (dialog, which) -> sendTracker.accept(null))
          .setNegativeButton(Lang.getString(R.string.ArticleCheckChat), (dialog, which) -> { sendTracker.cancel(); navigateBack(); }).show();
        });
      });
    }
    return root;
  }

  private void recoverDraft (ArticleDraftStore.Snapshot recovered) {
    recoveryPending = false; baseline = recovered.baseline; restore(recovered.document); history = new ArticleHistory(recovered.document); enableTree(root, true); updateToolState(); updateHistory(); saveDraft();
  }

  private void resolveDraftConflict (ArticleDraftStore.Snapshot recovered, Args args) {
    enableTree(root, false);
    ArticleFiles.resolve(tdlib, new TdApi.Object[] {args.document.toInput(), recovered.document.toInput(), recovered.baseline.toInput()}, false, resolved -> IMPORTS.execute(() -> {
      java.util.Map<String, Integer> paths = org.thunderdog.challegram.data.article.ArticleDraftFiles.identities(resolved.files);
      java.util.Map<String, Integer> aliases = org.thunderdog.challegram.data.article.ArticleDraftFiles.aliases(recovered.document, paths);
      aliases.putAll(org.thunderdog.challegram.data.article.ArticleDraftFiles.aliases(recovered.baseline, paths));
      boolean same = recovered.document.hasSameContent(args.document, aliases) || recovered.baseline.hasSameContent(args.document, aliases);
      handler.post(() -> {
        if (isDestroyed() || args.userId != tdlib.myUserId()) return;
        if (same) { recoverDraft(recovered); return; }
        new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticleDraftConflict))
          .setMessage(Lang.getString(R.string.ArticleDraftConflictHint)).setCancelable(false)
          .setPositiveButton(Lang.getString(R.string.ArticleRecoverLocal), (dialog, which) -> recoverDraft(recovered))
          .setNegativeButton(Lang.getString(R.string.ArticleUseServer), (dialog, which) -> { recoveryPending = false; enableTree(root, true); saveDraft(); updateToolState(); updateHistory(); }).show();
      });
    }));
  }

  private void createEditorLayout (Context context, boolean editing) {
    root = new LinearLayout(context) {
      @Override protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int top = org.thunderdog.challegram.navigation.HeaderView.getTopOffset();
        if (getPaddingTop() != top) setPadding(getPaddingLeft(), top, getPaddingRight(), getPaddingBottom());
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Theme.fillingColor());
    root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
    FrameLayout page = new FrameLayout(context); root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
    scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.setClipToPadding(false);
    fields = new ArticleDocumentView(context, new ArticleDocumentView.Delegate() {
      @Override public Tdlib tdlib () { return tdlib; }
      @Override public void changed (boolean structural) { if (structural) structureChanged(); else ArticleEditorController.this.changed(); }
      @Override public void selection (ArticleTextInput input) {
        focused = input; rememberSelection();
        boolean selected = fields.hasSelection();
        if (selected != formatsVisible) { formatsVisible = selected; rebuildTools(); }
        updateToolState();
      }
      @Override public void options (ArticleEditorTree.Entry entry) { blockOptions(entry); }
      @Override public void dragging (boolean active) {
        if (!active) { rebuildTools(); return; }
        bottom.removeAllViews();
        ImageView trash = ArticleEditorPopup.icon(context, R.drawable.baseline_delete_24, R.string.Delete, () -> { });
        bottom.addView(trash, new LinearLayout.LayoutParams(-1, Screen.dp(44)));
        trash.setOnDragListener((view, event) -> {
          if (!(event.getLocalState() instanceof ArticleEditorTree.Entry)) return false;
          if (event.getAction() == android.view.DragEvent.ACTION_DRAG_ENTERED) view.setAlpha(.5f);
          if (event.getAction() == android.view.DragEvent.ACTION_DRAG_EXITED) view.setAlpha(1f);
          if (event.getAction() == android.view.DragEvent.ACTION_DROP) fields.deleteDragged(event.getLocalState());
          if (event.getAction() == android.view.DragEvent.ACTION_DRAG_ENDED) rebuildTools();
          return true;
        });
      }
      @Override public void media (ArticleEditorTree.Entry entry, LinearLayout parent) {
        ArticleEditorMedia media = new ArticleEditorMedia(ArticleEditorController.this, entry.block, () -> blockOptions(entry)); mediaViews.add(media); parent.addView(media, new LinearLayout.LayoutParams(-1, -2));
      }
      @Override public void formula (ArticleEditorTree.Entry entry, TextView view) {
        TdApi.InputPageBlockMathematicalExpression formula = (TdApi.InputPageBlockMathematicalExpression) entry.block;
        renderFormula(view, formula.expression);
        view.setOnClickListener(v -> formulaEditor(formula.expression, text -> {
          if (text.trim().isEmpty()) entry.group.remove(entry.index);
          else formula.expression = text;
          structureChanged();
        }));
      }
    });
    scroll.addView(fields); page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    org.thunderdog.challegram.widget.ArticleSelectionView selection = new org.thunderdog.challegram.widget.ArticleSelectionView(context, fields, scroll);
    fields.setSelectionView(selection); page.addView(selection, new FrameLayout.LayoutParams(-1, -1));
    ImageView back = ArticleEditorPopup.icon(context, R.drawable.baseline_arrow_back_24, R.string.ArticleBack, this::navigateBack);
    FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(Screen.dp(44), Screen.dp(44), Gravity.TOP | Gravity.LEFT); backParams.setMargins(Screen.dp(8), Screen.dp(8), 0, 0); page.addView(back, backParams);
    LinearLayout historyBar = new LinearLayout(context); historyBar.setBackground(ArticleEditorPopup.background(ArticleEditorPopup.surfaceColor(), 24));
    undoButton = icon(historyBar, R.drawable.article_iv_undo_24, R.string.ArticleUndo, () -> restore(history.undo()), 41);
    redoButton = icon(historyBar, R.drawable.article_iv_redo_24, R.string.ArticleRedo, () -> restore(history.redo()), 41);
    FrameLayout.LayoutParams historyParams = new FrameLayout.LayoutParams(Screen.dp(82), Screen.dp(44), Gravity.TOP | Gravity.RIGHT); historyParams.setMargins(0, Screen.dp(8), Screen.dp(8), 0); page.addView(historyBar, historyParams);
    historyBar.setOnLongClickListener(v -> { preview(); return true; });
    editingArticle = editing;
    bottom = new LinearLayout(context); bottom.setGravity(Gravity.CENTER_VERTICAL); bottom.setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));
    root.addView(bottom, new LinearLayout.LayoutParams(-1, Screen.dp(60)));
    rebuildTools(); rebuildFields(); updateHistory();
  }

  private ImageView icon (LinearLayout parent, int resource, int label, Runnable action, int width) {
    ImageView view = ArticleEditorPopup.icon(context(), resource, label, () -> {
      if (!sending && !importing && !recoveryPending) { toolAnchor = parent; action.run(); }
    });
    view.setBackground(null); int inset = Screen.dp((width - 24) / 2f); view.setPadding(inset, Screen.dp(10), inset, Screen.dp(10));
    view.setOnClickListener(v -> { if (!sending && !importing && !recoveryPending) { toolAnchor = v; action.run(); } });
    parent.addView(view, new LinearLayout.LayoutParams(Screen.dp(width), Screen.dp(44))); return view;
  }
  private void updateHistory () {
    if (undoButton == null) return;
    undoButton.setEnabled(history.canUndo()); undoButton.setAlpha(history.canUndo() ? 1 : .35f);
    redoButton.setEnabled(history.canRedo()); redoButton.setAlpha(history.canRedo() ? 1 : .35f);
  }
  private void rebuildTools () {
    if (bottom == null) return;
    bottom.removeAllViews(); formatButtons.clear();
    textTool = listTool = quoteTool = linkTool = dateTool = inlineButtonTool = null;
    ImageView ai = icon(bottom, R.drawable.article_input_ai_24, R.string.ArticleAi, this::aiMenu, 44);
    ai.setBackground(ArticleEditorPopup.background(ArticleEditorPopup.surfaceColor(), 22));
    HorizontalScrollView toolsScroll = new HorizontalScrollView(context()); toolsScroll.setHorizontalScrollBarEnabled(false);
    toolsScroll.setFillViewport(!formatsVisible); toolsScroll.setBackground(ArticleEditorPopup.background(ArticleEditorPopup.surfaceColor(), 22));
    if (android.os.Build.VERSION.SDK_INT >= 21) toolsScroll.setClipToOutline(true);
    tools = new LinearLayout(context()); tools.setGravity(Gravity.CENTER); tools.setPadding(Screen.dp(2), 0, Screen.dp(2), 0);
    toolsScroll.addView(tools, new ViewGroup.LayoutParams(formatsVisible ? -2 : -1, Screen.dp(44)));
    LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(0, Screen.dp(44), 1); toolsParams.leftMargin = Screen.dp(8);
    bottom.addView(toolsScroll, toolsParams);
    if (formatsVisible) {
      styleTool(R.drawable.article_formatting_bold_24, R.string.ArticleBold, new TdApi.RichTextBold(emptyText()));
      styleTool(R.drawable.article_formatting_italic_24, R.string.ArticleItalic, new TdApi.RichTextItalic(emptyText()));
      styleTool(R.drawable.article_formatting_underline_24, R.string.ArticleUnderline, new TdApi.RichTextUnderline(emptyText()));
      styleTool(R.drawable.article_formatting_strikethrough_24, R.string.ArticleStrike, new TdApi.RichTextStrikethrough(emptyText()));
      styleTool(R.drawable.article_formatting_spoiler_24, R.string.ArticleSpoiler, new TdApi.RichTextSpoiler(emptyText()));
      styleTool(R.drawable.article_iv_code_24, R.string.ArticleCode, new TdApi.RichTextFixed(emptyText()));
      styleTool(R.drawable.article_formatting_marked_24, R.string.ArticleMarked, new TdApi.RichTextMarked(emptyText()));
      styleTool(R.drawable.article_iv_sub_24, R.string.ArticleSubscript, new TdApi.RichTextSubscript(emptyText()));
      styleTool(R.drawable.article_iv_super_24, R.string.ArticleSuperscript, new TdApi.RichTextSuperscript(emptyText()));
      quoteTool = icon(tools, R.drawable.article_iv_quote_24, R.string.ArticleQuote, fields::toggleQuoteSelection, 40);
      inlineButtonTool = icon(tools, R.drawable.article_iv_button_24, R.string.ArticleButton, this::inlineButtonMenu, 40);
      LinearLayout links = toolGroup();
      linkTool = icon(links, R.drawable.article_media_link_24, R.string.ArticleLink, this::linkEditor, 38);
      dateTool = icon(links, R.drawable.baseline_date_range_24, R.string.ArticleInsertDate, this::dateEditor, 38);
      icon(toolGroup(), R.drawable.article_iv_math_24, R.string.ArticleFormula, this::inlineFormulaEditor, 38);
      sendButton = null;
    } else {
      icon(tools, R.drawable.baseline_emoticon_outline_24, R.string.ArticleEmoji, this::emojiPicker, 40);
      textTool = icon(tools, R.drawable.article_iv_text_24, R.string.ArticleFormat, this::textMenu, 40);
      listTool = icon(tools, R.drawable.article_iv_lists_24, R.string.ArticleList, this::listMenu, 40);
      icon(tools, R.drawable.article_iv_table_24, R.string.ArticleTable, () -> { if (!fields.showTableMenu(toolAnchor)) fields.insert(newBlock(R.string.ArticleTable)); }, 40);
      icon(tools, R.drawable.article_iv_math_24, R.string.ArticleFormula, () -> formulaEditor("", value -> fields.insert(new TdApi.InputPageBlockMathematicalExpression(value))), 40);
      icon(tools, R.drawable.article_outline_poll_attach_24, R.string.ArticleAttachment, this::attachmentMenu, 40);
      sendButton = icon(bottom, !editingArticle ? R.drawable.article_send_plane_24 : R.drawable.baseline_check_24, !editingArticle ? R.string.Send : R.string.Save, this::send, 44);
      ((LinearLayout.LayoutParams) sendButton.getLayoutParams()).leftMargin = Screen.dp(8);
      sendButton.setColorFilter(android.graphics.Color.WHITE); sendButton.setBackground(ArticleEditorPopup.background(Theme.textLinkColor(), 22));
    }
    updateToolState();
  }
  private LinearLayout toolGroup () {
    LinearLayout group = new LinearLayout(context()); group.setGravity(Gravity.CENTER_VERTICAL);
    group.setPadding(Screen.dp(2), 0, Screen.dp(2), 0);
    group.setBackground(ArticleEditorPopup.background(ArticleEditorPopup.surfaceColor(), 22));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Screen.dp(44)); params.leftMargin = Screen.dp(8);
    bottom.addView(group, params); return group;
  }
  private void styleTool (int icon, int label, TdApi.RichText wrapper) {
    ImageView button = icon(tools, icon, label, () -> format(wrapper), 40);
    formatButtons.put(button, wrapper.getConstructor());
  }
  private void selectTool (ImageView view, boolean selected, boolean enabled) {
    if (view == null) return;
    view.setSelected(selected); view.setEnabled(enabled && !sending && !importing && !recoveryPending);
    view.setAlpha(view.isEnabled() ? 1f : .35f);
    view.setColorFilter(selected ? Theme.textLinkColor() : Theme.textAccentColor());
    view.setBackground(selected ? ArticleEditorPopup.background(androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.textLinkColor(), 32), 22) : null);
  }
  private void updateToolState () {
    if (fields == null) return;
    TdApi.InputPageBlock block = fields.selectedBlock();
    boolean heading = block instanceof TdApi.InputPageBlockSectionHeading;
    for (java.util.Map.Entry<ImageView, Integer> style : formatButtons.entrySet()) {
      boolean enabled = focused != null && !(heading && (style.getValue() == TdApi.RichTextBold.CONSTRUCTOR || style.getValue() == TdApi.RichTextItalic.CONSTRUCTOR));
      selectTool(style.getKey(), fields.isFormatApplied(style.getValue()), enabled);
    }
    boolean quote = block instanceof TdApi.InputPageBlockBlockQuote || block instanceof TdApi.InputPageBlockPullQuote || block instanceof TdApi.InputPageBlockExpandableBlockQuote;
    selectTool(quoteTool, quote, fields.canChangeTextStyle());
    selectTool(inlineButtonTool, false, focused != null && !focused.isFormatApplied(TdApi.RichTextButton.CONSTRUCTOR));
    selectTool(linkTool, focused != null && focused.isFormatApplied(TdApi.RichTextUrl.CONSTRUCTOR), focused != null);
    selectTool(dateTool, focused != null && focused.isFormatApplied(TdApi.RichTextDateTime.CONSTRUCTOR), focused != null);
    int listStyle = fields.selectedListStyle();
    selectTool(textTool, listStyle == 0 && fields.canChangeTextStyle(), fields.canChangeTextStyle());
    selectTool(listTool, listStyle != 0, fields.canChangeTextStyle() || listStyle != 0);
    if (textTool != null) {
      int resource = heading ? new int[] {R.drawable.article_iv_h1_24, R.drawable.article_iv_h2_24, R.drawable.article_iv_h3_24, R.drawable.article_iv_h4_24, R.drawable.article_iv_h5_24, R.drawable.article_iv_h6_24}[Math.max(0, Math.min(5, ((TdApi.InputPageBlockSectionHeading) block).size - 1))] :
        block instanceof TdApi.InputPageBlockPreformatted ? R.drawable.article_iv_code_24 : block instanceof TdApi.InputPageBlockFooter ? R.drawable.article_iv_footer_24 :
        block instanceof TdApi.InputPageBlockPullQuote ? R.drawable.article_iv_pullquote_24 : quote ? R.drawable.article_iv_quote_24 : R.drawable.article_iv_text_24;
      textTool.setImageResource(resource);
    }
    if (listTool != null) listTool.setImageResource(listStyle == 2 ? R.drawable.article_iv_ordered_list_24 : listStyle == 3 ? R.drawable.article_iv_todo_24 : listStyle == 4 ? R.drawable.article_iv_details_24 : R.drawable.article_iv_lists_24);
  }
  private void textMenu () {
    TdApi.InputPageBlock block = fields.selectedBlock();
    new ArticleEditorPopup(context())
      .checked(R.drawable.article_iv_h1_24, R.string.ArticleHeading, block instanceof TdApi.InputPageBlockSectionHeading, this::headingMenu)
      .checked(R.drawable.article_iv_text2_24, R.string.ArticleTextStyleText, block instanceof TdApi.InputPageBlockParagraph, () -> fields.convert(TdApi.InputPageBlockParagraph::new))
      .checked(R.drawable.article_iv_quote_24, R.string.ArticleQuote, block instanceof TdApi.InputPageBlockBlockQuote || block instanceof TdApi.InputPageBlockExpandableBlockQuote, () -> fields.convert(text -> new TdApi.InputPageBlockBlockQuote(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text)}, emptyText())))
      .checked(R.drawable.article_iv_pullquote_24, R.string.ArticlePullQuote, block instanceof TdApi.InputPageBlockPullQuote, () -> fields.convert(text -> new TdApi.InputPageBlockPullQuote(text, emptyText())))
      .checked(R.drawable.article_iv_code_24, R.string.ArticleCode, block instanceof TdApi.InputPageBlockPreformatted, () -> fields.convert(text -> new TdApi.InputPageBlockPreformatted(text, "")))
      .checked(R.drawable.article_iv_footer_24, R.string.ArticleFooter, block instanceof TdApi.InputPageBlockFooter, () -> fields.convert(TdApi.InputPageBlockFooter::new))
      .show(toolAnchor);
  }
  private void headingMenu () {
    ArticleEditorPopup popup = new ArticleEditorPopup(context());
    popup.item(R.drawable.baseline_arrow_back_24, R.string.ArticleBack, this::textMenu).gap();
    int[] icons = {R.drawable.article_iv_h1_24, R.drawable.article_iv_h2_24, R.drawable.article_iv_h3_24, R.drawable.article_iv_h4_24, R.drawable.article_iv_h5_24, R.drawable.article_iv_h6_24};
    TdApi.InputPageBlock block = fields.selectedBlock();
    for (int i = 0; i < icons.length; i++) {
      int level = i + 1;
      popup.heading(icons[i], level, block instanceof TdApi.InputPageBlockSectionHeading && ((TdApi.InputPageBlockSectionHeading) block).size == level, () -> fields.convert(text -> new TdApi.InputPageBlockSectionHeading(text, level)));
    }
    popup.show(toolAnchor);
  }
  private void listMenu () {
    int style = fields.selectedListStyle();
    ArticleEditorPopup popup = new ArticleEditorPopup(context())
      .checked(0, R.string.ArticleNoList, style == 0, () -> fields.listStyle(0))
      .checked(R.drawable.article_iv_list_24, R.string.ArticleBulleted, style == 1, () -> fields.listStyle(1))
      .checked(R.drawable.article_iv_ordered_list_24, R.string.ArticleNumbered, style == 2, () -> fields.listStyle(2))
      .checked(R.drawable.article_iv_todo_24, R.string.ArticleChecklist, style == 3, () -> fields.listStyle(3))
      .checked(R.drawable.article_iv_details_24, R.string.ArticleToggle, style == 4, () -> fields.convert(text -> new TdApi.InputPageBlockDetails(text, paragraph(), true)));
    if (fields.canIndent() || fields.canOutdent()) popup.gap();
    if (fields.canIndent()) popup.item(R.drawable.article_iv_list_tab_24, R.string.ArticleIndent, () -> fields.indent(false));
    if (fields.canOutdent()) popup.item(R.drawable.article_iv_list_untab_24, R.string.ArticleOutdent, () -> fields.indent(true));
    popup.show(toolAnchor);
  }
  private void attachmentMenu () {
    new ArticleEditorPopup(context())
      .item(R.drawable.baseline_image_24, R.string.Gallery, () -> attachmentPicker(0))
      .item(R.drawable.baseline_insert_drive_file_24, R.string.File, () -> attachmentPicker(1))
      .item(R.drawable.baseline_music_note_24, R.string.Music, () -> attachmentPicker(2))
      .item(R.drawable.baseline_location_on_24, R.string.Location, () -> attachmentPicker(3))
      .show(toolAnchor);
  }

  private void attachmentPicker (int type) {
    Keyboard.hide(focused);
    if (type == 2 && context().permissions().requestReadExternalStorage(org.thunderdog.challegram.util.Permissions.ReadType.AUDIO, grant -> {
      if (isDestroyed()) return;
      if (grant == org.thunderdog.challegram.util.Permissions.GrantResult.ALL) attachmentPicker(2);
      else pickMedia(ArticleMediaFiles.Kind.AUDIO, fields::insert);
    })) return;
    org.thunderdog.challegram.component.attach.MediaLayout picker = new org.thunderdog.challegram.component.attach.MediaLayout(this);
    picker.setCallback(new org.thunderdog.challegram.component.attach.MediaLayout.ArticleCallback() {
      @Override public void onMediaSelected (org.thunderdog.challegram.loader.ImageGalleryFile file, boolean asFile, boolean spoiler) {
        beginImport();
        IMPORTS.execute(() -> {
          try {
            TdApi.InputMessageContent content = org.thunderdog.challegram.data.TD.toContent(tdlib, file, asFile, false, false, spoiler, false);
            TdApi.InputPageBlock block;
            TdApi.FormattedText text;
            if (content instanceof TdApi.InputMessagePhoto) { TdApi.InputMessagePhoto photo = (TdApi.InputMessagePhoto) content; text = photo.caption; block = new TdApi.InputPageBlockPhoto(photo.photo, emptyCaption(), photo.hasSpoiler); }
            else if (content instanceof TdApi.InputMessageVideo) { TdApi.InputMessageVideo video = (TdApi.InputMessageVideo) content; text = video.caption; block = new TdApi.InputPageBlockVideo(video.video, emptyCaption(), video.hasSpoiler); }
            else if (content instanceof TdApi.InputMessageAnimation) { TdApi.InputMessageAnimation animation = (TdApi.InputMessageAnimation) content; text = animation.caption; block = new TdApi.InputPageBlockAnimation(animation.animation, emptyCaption(), animation.hasSpoiler); }
            else if (content instanceof TdApi.InputMessageDocument) { TdApi.InputMessageDocument document = (TdApi.InputMessageDocument) content; text = document.caption; block = new TdApi.InputPageBlockDocument(document.document, emptyCaption()); }
            else throw new IllegalArgumentException("Unsupported gallery result");
            if (text != null && !text.text.isEmpty()) {
              TdApi.InputPageBlock[] paragraphs = ((TdApi.RichMessageSourceBlocks) org.thunderdog.challegram.data.article.ArticleComposer.fromText(text, working.isRtl).toInput().source).blocks;
              List<TdApi.RichText> caption = new ArrayList<>();
              for (TdApi.InputPageBlock paragraph : paragraphs) { if (!caption.isEmpty()) caption.add(new TdApi.RichTextPlain("\n")); for (ArticleEditorTree.TextField field : ArticleEditorTree.fields(paragraph)) caption.add(field.value); }
              ArticleEditorTree.caption(block).text = new TdApi.RichTexts(caption.toArray(new TdApi.RichText[0]));
            }
            handler.post(() -> { try { prepareMedia(block); fields.insert(block); saveDraft(); } finally { finishImport(); } });
          } catch (RuntimeException invalid) { handler.post(() -> { finishImport(); if (!isDestroyed()) UI.showToast(R.string.ArticleMediaImportFailed, Toast.LENGTH_LONG); }); }
        });
      }
      @Override public void onFileSelected (String path, boolean audio) {
        Uri uri = path.startsWith("content:") || path.startsWith("file:") ? Uri.parse(path) : Uri.fromFile(new java.io.File(path));
        importMedia(uri, audio ? ArticleMediaFiles.Kind.AUDIO : ArticleMediaFiles.Kind.DOCUMENT, fields::insert);
      }
      @Override public void onLocationSelected (TdApi.Location location, String title) {
        TdApi.PageBlockCaption caption = emptyCaption(); caption.text = new TdApi.RichTextPlain(title);
        fields.insert(new TdApi.InputPageBlockMap(location, 15, 640, 360, caption));
      }
      @Override public void onOpenGallery (boolean asFile) { pickMedia(asFile ? ArticleMediaFiles.Kind.DOCUMENT : null, fields::insert); }
    });
    if (type == 3) picker.init(org.thunderdog.challegram.component.attach.MediaLayout.MODE_LOCATION, null);
    else {
      picker.setItemsAdapter(new org.thunderdog.challegram.component.attach.MediaLayout.ItemsAdapter() {
        @Override public org.thunderdog.challegram.component.attach.MediaBottomBar.BarItem[] getBottomBarItems () { return new org.thunderdog.challegram.component.attach.MediaBottomBar.BarItem[] {new org.thunderdog.challegram.component.attach.MediaBottomBar.BarItem(type == 0 ? R.drawable.baseline_image_24 : type == 2 ? R.drawable.baseline_music_note_24 : R.drawable.baseline_insert_drive_file_24, type == 0 ? R.string.Gallery : type == 2 ? R.string.Music : R.string.File, org.thunderdog.challegram.theme.ColorId.attachFile)}; }
        @Override public int getDefaultItemIndex () { return 0; }
        @Override public boolean needBottomBar () { return true; }
        @Override public org.thunderdog.challegram.component.attach.MediaBottomBaseController<?> createControllerForIndex (int index) {
          if (type == 0) return new org.thunderdog.challegram.component.attach.MediaBottomGalleryController(picker);
          org.thunderdog.challegram.component.attach.MediaBottomFilesController files = new org.thunderdog.challegram.component.attach.MediaBottomFilesController(picker);
          files.setMusicOnly(type == 2);
          return files;
        }
      });
      picker.setFilesControllerDelegate(new org.thunderdog.challegram.component.attach.MediaBottomFilesController.Delegate() {
        @Override public boolean showRestriction (View view, int right) { return false; }
        @Override public void onFilesSelected (ArrayList<org.thunderdog.challegram.data.InlineResult<?>> results, boolean keyboard) {
          for (org.thunderdog.challegram.data.InlineResult<?> result : results) {
            Object tag = result instanceof org.thunderdog.challegram.data.InlineResultCommon ? ((org.thunderdog.challegram.data.InlineResultCommon) result).getTag() : null;
            String path = tag instanceof org.thunderdog.challegram.component.attach.MediaBottomFilesController.MusicEntry ? ((org.thunderdog.challegram.component.attach.MediaBottomFilesController.MusicEntry) tag).getPath()
              : tag instanceof org.thunderdog.challegram.component.attach.MediaBottomFilesController.FileEntry ? ((org.thunderdog.challegram.component.attach.MediaBottomFilesController.FileEntry) tag).getUri().toString() : result.getId();
            if (path != null) {
              Uri uri = path.startsWith("content:") || path.startsWith("file:") ? Uri.parse(path) : Uri.fromFile(new java.io.File(path));
              importMedia(uri, type == 2 || result.getType() == org.thunderdog.challegram.data.InlineResult.TYPE_AUDIO ? ArticleMediaFiles.Kind.AUDIO : ArticleMediaFiles.Kind.DOCUMENT, fields::insert);
            }
          }
          picker.hide(false);
        }
      });
      picker.init(org.thunderdog.challegram.component.attach.MediaLayout.MODE_CUSTOM_ADAPTER, null);
    }
    picker.show();
  }
  private void renderFormula (TextView view, String value) {
    android.graphics.Bitmap bitmap = org.thunderdog.challegram.data.article.ArticleMath.render(context(), value, Screen.dp(18));
    if (bitmap == null) { view.setCompoundDrawables(null, null, null, null); view.setText(value.isEmpty() ? Lang.getString(R.string.ArticleFormula) : value); }
    else {
      android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(context().getResources(), bitmap);
      drawable.setColorFilter(Theme.textAccentColor(), android.graphics.PorterDuff.Mode.SRC_IN);
      int maxWidth = context().getResources().getDisplayMetrics().widthPixels - Screen.dp(40); float scale = Math.min(1f, (float) maxWidth / bitmap.getWidth());
      drawable.setBounds(0, 0, (int) (bitmap.getWidth() * scale), (int) (bitmap.getHeight() * scale));
      view.setText(""); view.setCompoundDrawables(null, drawable, null, null);
    }
  }
  private void linkEditor () {
    if (focused == null) return;
    ArticleTextInput target = focused;
    TdApi.RichText element = target.selectedElement();
    String url = element instanceof TdApi.RichTextUrl ? ((TdApi.RichTextUrl) element).url : "https://";
    prompt(R.string.ArticleLink, url, value -> { if (!value.trim().isEmpty()) target.setFormat(new TdApi.RichTextUrl(emptyText(), value.trim(), false)); });
  }
  private void inlineButtonMenu () {
    if (focused == null) return;
    ArticleTextInput target = focused;
    new ArticleEditorPopup(context())
      .item(R.drawable.article_media_link_24, R.string.ArticleLink, () -> prompt(R.string.ArticleLink, "https://", value -> target.setFormat(new TdApi.RichTextButton(new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeUrl(value))))))
      .item(R.drawable.baseline_content_copy_24, R.string.ArticleInlineCopy, () -> prompt(R.string.ArticleCopyButton, ArticleRichText.plain(target.richText(Math.min(target.getSelectionStart(), target.getSelectionEnd()), Math.max(target.getSelectionStart(), target.getSelectionEnd()))), value -> target.setFormat(new TdApi.RichTextButton(new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeCopyText(value))))))
      .item(R.drawable.baseline_person_24, R.string.ArticleUser, () -> pickUser(user -> target.setFormat(new TdApi.RichTextButton(new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeUser(user))))))
      .show(toolAnchor);
  }
  private void inlineFormulaEditor () {
    if (focused == null) return;
    ArticleTextInput target = focused;
    int start = Math.min(target.getSelectionStart(), target.getSelectionEnd()), end = Math.max(target.getSelectionStart(), target.getSelectionEnd());
    formulaEditor(ArticleRichText.plain(target.richText(start, end)), value -> target.insert(new TdApi.RichTextMathematicalExpression(value)));
  }
  private void dateEditor () {
    if (focused == null) return;
    ArticleTextInput target = focused; TdApi.RichText element = target.selectedElement();
    TdApi.RichTextDateTime initial = element instanceof TdApi.RichTextDateTime ? (TdApi.RichTextDateTime) element : null;
    Keyboard.hide(target);
    dateDialog = org.thunderdog.challegram.widget.ArticleDatePicker.show(context(), initial == null ? System.currentTimeMillis() : initial.unixTime * 1000L, seconds -> {
      if (!isDestroyed()) target.setFormat(new TdApi.RichTextDateTime(emptyText(), seconds, initial == null ? new TdApi.DateTimeFormattingTypeRelative() : initial.formattingType));
    });
  }
  private void formulaEditor (String initial, Consumer<String> result) {
    formulaDialog = ArticleFormulaEditor.show(context(), initial, value -> {
      if (!isDestroyed() && (!initial.isEmpty() || !value.trim().isEmpty())) result.accept(value);
    });
  }
  private void aiMenu () {
    if (sending || importing || recoveryPending) return;
    ArticleTextInput target = focused;
    int start = target == null ? 0 : Math.max(0, Math.min(target.getSelectionStart(), target.getSelectionEnd()));
    int end = target == null ? 0 : Math.max(start, Math.max(target.getSelectionStart(), target.getSelectionEnd()));
    boolean multiple = fields.hasMultipleSelection();
    boolean create = !fields.hasSelection();
    TdApi.InputRichMessage selection = create ? null : new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(fields.selectedBlocks()), working.isRtl, true);
    if (target != null) Keyboard.hide(target);
    ArticleDocument before = new ArticleDocument(working);
    aiDialog = org.thunderdog.challegram.widget.ArticleAiEditor.show(this, selection, create, result -> {
      if (isDestroyed() || !before.equals(new ArticleDocument(working))) return false;
      TdApi.InputPageBlock[] blocks = ((TdApi.RichMessageSourceBlocks) result.toInput().source).blocks;
      if (create) fields.insertBlocks(blocks);
      else if (!(multiple ? fields.replaceSelectedBlocks(blocks) : fields.replaceSelection(target, start, end, blocks))) { UI.showToast(R.string.ArticleAiFailed, Toast.LENGTH_LONG); return false; }
      return true;
    });
  }

  private boolean applyAiResult (TdApi.RichMessage result) {
    final TdApi.InputRichMessage accepted;
    try { accepted = ArticleAiResult.toDocument(result).toInput(); }
    catch (IllegalArgumentException | IllegalStateException invalidResult) {
      // The original draft and undo history stay intact if any part of the response is uneditable.
      return false;
    }
    working = accepted;
    structureChanged();
    return true;
  }

  private Button button (LinearLayout parent, int text, Runnable action) {
    Button button = new Button(context());
    button.setText(Lang.getString(text));
    button.setTextColor(Theme.textLinkColor());
    button.setOnClickListener(view -> { if (!sending && !importing && !recoveryPending) action.run(); });
    parent.addView(button);
    return button;
  }

  private void format (TdApi.RichText wrapper) { fields.formatSelection(wrapper); }

  private void pickUser (Consumer<Long> callback) {
    ContactsController picker = new ContactsController(context(), tdlib);
    picker.initWithMode(ContactsController.MODE_PICK);
    picker.setArguments(new ContactsController.Args(new org.thunderdog.challegram.util.SenderPickerDelegate() {
      @Override public boolean allowGlobalSearch () { return true; }
      @Override public String getUserPickTitle () { return Lang.getString(R.string.ArticleUser); }
      @Override public boolean onSenderPick (ContactsController controller, View view, TdApi.MessageSender sender) {
        if (!(sender instanceof TdApi.MessageSenderUser)) return false;
        callback.accept(((TdApi.MessageSenderUser) sender).userId); return true;
      }
    }).useGlobalSearch(org.thunderdog.challegram.component.dialogs.SearchManager.FLAG_ONLY_USERS));
    navigateTo(picker);
  }

  private void emojiPicker () {
    if (emojiPanel != null) { closeEmoji(); if (focused != null) Keyboard.show(focused); return; }
    if (focused == null) return;
    Keyboard.hide(focused);
    emojiPanel = new org.thunderdog.challegram.widget.EmojiLayout(context());
    emojiPanel.initWithMediasEnabled(this, false, new org.thunderdog.challegram.widget.EmojiLayout.Listener() {
      @Override public void onEnterEmoji (String value) { if (focused != null) focused.insert(new TdApi.RichTextPlain(value)); }
      @Override public void onEnterCustomEmoji (org.thunderdog.challegram.component.sticker.TGStickerObj sticker) { if (focused != null) focused.insert(new TdApi.RichTextCustomEmoji(sticker.getCustomEmojiId(), sticker.getAllEmoji())); }
    }, this, false);
    root.addView(emojiPanel, new LinearLayout.LayoutParams(-1, Screen.dp(280)));
  }
  private void closeEmoji () { if (emojiPanel != null) { root.removeView(emojiPanel); emojiPanel.destroy(); emojiPanel = null; } }
  private void moreFormats () {
    if (focused == null) return;
    choose(R.string.ArticleMoreFormats, Arrays.asList(R.string.ArticleMarked, R.string.ArticleSubscript, R.string.ArticleSuperscript, R.string.ArticleEmail, R.string.ArticlePhone, R.string.ArticleMention, R.string.ArticleHashtag, R.string.ArticleCashtag, R.string.ArticleBankCard, R.string.ArticleCommand, R.string.ArticleDate, R.string.ArticleAnchor, R.string.ArticleAnchorLink, R.string.ArticleReference, R.string.ArticleReferenceLink, R.string.ArticleButton, R.string.ArticleUser, R.string.ArticleFormula, R.string.ArticleEditElement), Arrays.asList(
      () -> format(new TdApi.RichTextMarked(emptyText())), () -> format(new TdApi.RichTextSubscript(emptyText())), () -> format(new TdApi.RichTextSuperscript(emptyText())),
      () -> prompt(R.string.ArticleEmail, "", value -> format(new TdApi.RichTextEmailAddress(emptyText(), value))),
      () -> prompt(R.string.ArticlePhone, "", value -> format(new TdApi.RichTextPhoneNumber(emptyText(), value))),
      () -> prompt(R.string.ArticleMention, "", value -> format(new TdApi.RichTextMention(emptyText(), value.replace("@", "")))),
      () -> prompt(R.string.ArticleHashtag, "#", value -> format(new TdApi.RichTextHashtag(emptyText(), value))),
      () -> prompt(R.string.ArticleCashtag, "$", value -> format(new TdApi.RichTextCashtag(emptyText(), value))),
      () -> prompt(R.string.ArticleBankCard, "", value -> format(new TdApi.RichTextBankCardNumber(emptyText(), value))),
      () -> prompt(R.string.ArticleCommand, "/", value -> format(new TdApi.RichTextBotCommand(emptyText(), value))),
      () -> prompt(R.string.ArticleDate, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date()), value -> {
        try { java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US); parser.setLenient(false); long seconds = parser.parse(value).getTime() / 1000; if (seconds < 0 || seconds > Integer.MAX_VALUE) throw new IllegalArgumentException(); format(new TdApi.RichTextDateTime(emptyText(), (int) seconds, new TdApi.DateTimeFormattingTypeRelative())); } catch (java.text.ParseException | IllegalArgumentException e) { UI.showToast(R.string.ArticleInvalidValue, Toast.LENGTH_SHORT); }
      }),
      () -> prompt(R.string.ArticleAnchor, "", value -> focused.insert(new TdApi.RichTextAnchor(value))),
      () -> prompt(R.string.ArticleAnchorLink, "", value -> format(new TdApi.RichTextAnchorLink(emptyText(), value, ""))),
      () -> prompt(R.string.ArticleReference, "", name -> prompt(R.string.ArticleReferenceBody, "", value -> focused.insert(new TdApi.RichTextReference(name, new TdApi.RichTextPlain(value))))),
      () -> prompt(R.string.ArticleReferenceLink, "", value -> format(new TdApi.RichTextReferenceLink(emptyText(), value, ""))),
      () -> prompt(R.string.ArticleLink, "https://", value -> format(new TdApi.RichTextButton(new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeUrl(value))))),
      () -> { ArticleTextInput target = focused; pickUser(user -> target.format(new TdApi.RichTextMentionName(emptyText(), user))); },
      () -> { ArticleTextInput target = focused; formulaEditor("", value -> target.insert(new TdApi.RichTextMathematicalExpression(value))); },
      this::editElement));
  }
  private void editElement () {
    if (focused == null) return;
    ArticleTextInput target = focused; TdApi.RichText element = target.selectedElement();
    if (element == null) { UI.showToast(R.string.ArticleSelectElement, Toast.LENGTH_SHORT); return; }
    int title; String value; Consumer<String> setter;
    if (element instanceof TdApi.RichTextUrl) { TdApi.RichTextUrl e = (TdApi.RichTextUrl) element; title = R.string.ArticleLink; value = e.url; setter = v -> e.url = v; }
    else if (element instanceof TdApi.RichTextEmailAddress) { TdApi.RichTextEmailAddress e = (TdApi.RichTextEmailAddress) element; title = R.string.ArticleEmail; value = e.emailAddress; setter = v -> e.emailAddress = v; }
    else if (element instanceof TdApi.RichTextPhoneNumber) { TdApi.RichTextPhoneNumber e = (TdApi.RichTextPhoneNumber) element; title = R.string.ArticlePhone; value = e.phoneNumber; setter = v -> e.phoneNumber = v; }
    else if (element instanceof TdApi.RichTextMathematicalExpression) { TdApi.RichTextMathematicalExpression e = (TdApi.RichTextMathematicalExpression) element; title = R.string.ArticleFormula; value = e.expression; setter = v -> e.expression = v; }
    else if (element instanceof TdApi.RichTextAnchor) { TdApi.RichTextAnchor e = (TdApi.RichTextAnchor) element; title = R.string.ArticleAnchor; value = e.name; setter = v -> e.name = v; }
    else if (element instanceof TdApi.RichTextAnchorLink) { TdApi.RichTextAnchorLink e = (TdApi.RichTextAnchorLink) element; title = R.string.ArticleAnchorLink; value = e.anchorName; setter = v -> e.anchorName = v; }
    else if (element instanceof TdApi.RichTextReferenceLink) { TdApi.RichTextReferenceLink e = (TdApi.RichTextReferenceLink) element; title = R.string.ArticleReferenceLink; value = e.referenceName; setter = v -> e.referenceName = v; }
    else if (element instanceof TdApi.RichTextReference) {
      TdApi.RichTextReference e = (TdApi.RichTextReference) element;
      prompt(R.string.ArticleReference, e.name, name -> prompt(R.string.ArticleReferenceBody, ArticleRichText.plain(e.text), body -> {
        e.name = name;
        if (!body.equals(ArticleRichText.plain(e.text))) e.text = new TdApi.RichTextPlain(body);
        target.updateSelectedElement(e);
      })); return;
    }
    else if (element instanceof TdApi.RichTextMentionName) { TdApi.RichTextMentionName e = (TdApi.RichTextMentionName) element; pickUser(user -> { e.userId = user; target.updateSelectedElement(e); }); return; }
    else if (element instanceof TdApi.RichTextButton) { TdApi.RichTextButton e = (TdApi.RichTextButton) element; editButton(e.button, () -> target.updateSelectedElement(e)); return; }
    else { UI.showToast(R.string.ArticleSelectElement, Toast.LENGTH_SHORT); return; }
    prompt(title, value, result -> { setter.accept(result); target.updateSelectedElement(element); });
  }

  private void preview () {
    TdlibOptions limits = tdlib.options();
    ArticleValidator.Problem problem = ArticleValidator.validate(working, new ArticleValidator.Limits(limits.richMessageTextLengthMax, limits.richMessageBlockCountMax, limits.richMessageDepthMax, limits.richMessageMediaCountMax, limits.richMessageTableColumnCountMax));
    if (problem != null) { UI.showToast(problem == ArticleValidator.Problem.EMPTY ? R.string.ArticleEmpty : R.string.ArticleLimitExceeded, Toast.LENGTH_LONG); return; }
    ArticleDocument document = new ArticleDocument(working); saveDraft();
    ArticleFiles.resolve(tdlib, new TdApi.Object[] {working}, true, resolved -> runOnUiThreadOptional(() -> {
      try {
        TdApi.RichMessage article = new ArticlePreviewMapper(file -> {
          if (ArticleFiles.key(file) != null) { TdApi.File known = resolved.files.get(ArticleFiles.key(file)); if (known == null) throw new IllegalArgumentException("Missing preview file"); return known; }
          if (file instanceof TdApi.InputFileLocal || file instanceof TdApi.InputFileGenerated) {
            String path = file instanceof TdApi.InputFileLocal ? ((TdApi.InputFileLocal) file).path : ((TdApi.InputFileGenerated) file).originalPath; long size = new java.io.File(path).length();
            return new TdApi.File(-Math.max(1, path.hashCode() & 0x7fffffff), size, size, new TdApi.LocalFile(path, false, false, false, true, 0, size, size), new TdApi.RemoteFile("", "", false, false, 0));
          }
          throw new IllegalArgumentException("File is not ready for preview");
        }, file -> ArticleFiles.key(file) != null ? resolved.names.get(ArticleFiles.key(file)) : null).preview(document);
        ArticlePreviewController controller = new ArticlePreviewController(context(), tdlib); controller.setArguments(article); navigateTo(controller);
      } catch (IllegalArgumentException e) { UI.showToast(R.string.ArticlePreviewFailed, Toast.LENGTH_LONG); }
    }));
  }
  private static TdApi.RichText emptyText () { return new TdApi.RichTextPlain(""); }
  private static TdApi.PageBlockCaption emptyCaption () { return new TdApi.PageBlockCaption(emptyText(), emptyText()); }
  private static TdApi.InputPageBlock[] paragraph () { return new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(emptyText())}; }

  private void rebuildFields () {
    int scrollY = scroll.getScrollY();
    for (ArticleEditorMedia media : mediaViews) media.performDestroy(); mediaViews.clear();
    textInputs.clear(); fields.bind(working); textInputs.addAll(fields.inputs()); rememberSelection();
    scroll.post(() -> scroll.scrollTo(0, scrollY));
  }

  private void changed () {
    if (sending || restoring) return;
    draftTouched = true;
    history.push(new ArticleDocument(working));
    updateHistory();
    updateToolState();
    rememberSelection();
    handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 600);
  }
  private void rememberSelection () {
    if (!restoring && history != null && scroll != null) history.rememberSelection(textInputs.indexOf(focused), focused == null ? 0 : focused.getSelectionStart(), focused == null ? 0 : focused.getSelectionEnd(), scroll.getScrollY());
  }
  private void structureChanged () { changed(); if (!isDestroyed()) rebuildFields(); }
  private void restore (ArticleDocument document) {
    int[] selection = history.selection(); restoring = true;
    working = document.toInput(); rebuildFields();
    if (selection[0] >= 0 && selection[0] < textInputs.size()) {
      ArticleTextInput field = textInputs.get(selection[0]); field.requestFocus(); focused = field;
      field.setSelection(Math.max(0, Math.min(field.length(), selection[1])), Math.max(0, Math.min(field.length(), selection[2])));
    }
    restoring = false; updateHistory();
    scroll.post(() -> scroll.scrollTo(0, selection[3]));
    handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 600);
  }

  private void blockOptions (ArticleEditorTree.Entry entry) {
    if (toolAnchor == null || toolAnchor.getWindowToken() == null) toolAnchor = focused != null ? focused : tools;
    List<Integer> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
    labels.add(R.string.ArticleMoveUp); actions.add(() -> { entry.group.move(entry.index, -1); structureChanged(); });
    labels.add(R.string.ArticleMoveDown); actions.add(() -> { entry.group.move(entry.index, 1); structureChanged(); });
    labels.add(R.string.ArticleAddAfter); actions.add(() -> addBlock(entry.group, entry.index + 1));
    labels.add(R.string.ArticleMoreFormats); actions.add(this::moreFormats);
    labels.add(R.string.Delete); actions.add(() -> { entry.group.remove(entry.index); structureChanged(); });
    List<ArticleEditorTree.Group> children = ArticleEditorTree.children(entry.block);
    for (int i = 0; i < children.size(); i++) {
      ArticleEditorTree.Group group = children.get(i);
      labels.add(R.string.ArticleAddInside); actions.add(() -> addBlock(group, group.blocks().length));
    }
    if (entry.block instanceof TdApi.InputPageBlockList) {
      TdApi.InputPageBlockList list = (TdApi.InputPageBlockList) entry.block;
      labels.add(R.string.ArticleAddListItem); actions.add(() -> {
        TdApi.InputPageBlockListItem previous = list.items.length > 0 ? list.items[list.items.length - 1] : null;
        list.items = Arrays.copyOf(list.items, list.items.length + 1);
        list.items[list.items.length - 1] = new TdApi.InputPageBlockListItem(paragraph(), previous != null && previous.hasCheckbox, false,
          previous == null || previous.value == 0 ? 0 : previous.value == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.value + 1, previous == null ? "" : previous.type); structureChanged();
      });
    }
    if (entry.block instanceof TdApi.InputPageBlockDetails) {
      labels.add(R.string.ArticleExpanded); actions.add(() -> { TdApi.InputPageBlockDetails details = (TdApi.InputPageBlockDetails) entry.block; details.isOpen = !details.isOpen; structureChanged(); });
    }
    if (entry.block instanceof TdApi.InputPageBlockSectionHeading) {
      labels.add(R.string.ArticleHeadingSize); actions.add(() -> prompt(R.string.ArticleHeadingSize, Integer.toString(((TdApi.InputPageBlockSectionHeading) entry.block).size), value -> {
        try { ((TdApi.InputPageBlockSectionHeading) entry.block).size = Math.max(1, Math.min(6, Integer.parseInt(value))); structureChanged(); } catch (NumberFormatException e) { UI.showToast(R.string.ArticleInvalidValue, Toast.LENGTH_SHORT); }
      }));
    }
    TdApi.PageBlockCaption caption = ArticleEditorTree.caption(entry.block);
    if (ArticleEditorTree.isMedia(entry.block)) {
      String credit = caption != null ? ArticleRichText.plain(caption.credit) : "";
      labels.add(R.string.ArticleCredit); actions.add(() -> prompt(R.string.ArticleCredit, credit, value -> { if (!value.equals(credit)) { ArticleEditorTree.editCaption(entry.block).credit = new TdApi.RichTextPlain(value); structureChanged(); } }));
    }
    ArticleMediaFiles.Kind kind = mediaKind(blockName(entry.block));
    if (kind != null) {
      labels.add(R.string.ArticleReplaceMedia); actions.add(() -> pickMedia(kind, replacement -> {
        if (caption != null) { TdApi.PageBlockCaption after = ArticleEditorTree.editCaption(replacement); after.text = caption.text; after.credit = caption.credit; }
        if (entry.block instanceof TdApi.InputPageBlockPhoto) ((TdApi.InputPageBlockPhoto) replacement).hasSpoiler = ((TdApi.InputPageBlockPhoto) entry.block).hasSpoiler;
        if (entry.block instanceof TdApi.InputPageBlockVideo) ((TdApi.InputPageBlockVideo) replacement).hasSpoiler = ((TdApi.InputPageBlockVideo) entry.block).hasSpoiler;
        if (entry.block instanceof TdApi.InputPageBlockAnimation) ((TdApi.InputPageBlockAnimation) replacement).hasSpoiler = ((TdApi.InputPageBlockAnimation) entry.block).hasSpoiler;
        entry.group.replace(entry.index, replacement); structureChanged();
      }));
    }
    if (entry.block instanceof TdApi.InputPageBlockPhoto || entry.block instanceof TdApi.InputPageBlockVideo || entry.block instanceof TdApi.InputPageBlockAnimation) {
      labels.add(R.string.ArticleSpoiler); actions.add(() -> {
        if (entry.block instanceof TdApi.InputPageBlockPhoto) { TdApi.InputPageBlockPhoto b = (TdApi.InputPageBlockPhoto) entry.block; b.hasSpoiler = !b.hasSpoiler; }
        else if (entry.block instanceof TdApi.InputPageBlockVideo) { TdApi.InputPageBlockVideo b = (TdApi.InputPageBlockVideo) entry.block; b.hasSpoiler = !b.hasSpoiler; }
        else { TdApi.InputPageBlockAnimation b = (TdApi.InputPageBlockAnimation) entry.block; b.hasSpoiler = !b.hasSpoiler; }
        structureChanged();
      });
    }
    if (entry.block instanceof TdApi.InputPageBlockAudio) {
      TdApi.InputAudio audio = ((TdApi.InputPageBlockAudio) entry.block).audio;
      labels.add(R.string.ArticleAudioTitle); actions.add(() -> prompt(R.string.ArticleAudioTitle, audio.title, value -> { audio.title = value; structureChanged(); }));
      labels.add(R.string.ArticlePerformer); actions.add(() -> prompt(R.string.ArticlePerformer, audio.performer, value -> { audio.performer = value; structureChanged(); }));
    }
    if (entry.block instanceof TdApi.InputPageBlockPreformatted) {
      TdApi.InputPageBlockPreformatted code = (TdApi.InputPageBlockPreformatted) entry.block;
      labels.add(R.string.ArticleLanguage); actions.add(() -> prompt(R.string.ArticleLanguage, code.language, value -> { code.language = value; changed(); }));
    }
    if (entry.block instanceof TdApi.InputPageBlockButtonRow) {
      TdApi.InputPageBlockButtonRow row = (TdApi.InputPageBlockButtonRow) entry.block;
      for (int i = 0; i < row.buttons.length; i++) { int index = i; labels.add(R.string.ArticleEditElement); actions.add(() -> buttonOptions(row, index)); }
      labels.add(R.string.ArticleAddButton); actions.add(() -> { row.buttons = Arrays.copyOf(row.buttons, row.buttons.length + 1); row.buttons[row.buttons.length - 1] = new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeUrl("https://")); structureChanged(); });
      labels.add(R.string.ArticleAlignment); actions.add(() -> alignment(value -> { row.align = value; structureChanged(); }));
    }
    if (entry.block instanceof TdApi.InputPageBlockTable) {
      TdApi.InputPageBlockTable table = (TdApi.InputPageBlockTable) entry.block;
      labels.add(R.string.ArticleTableBorder); actions.add(() -> { table.isBordered = !table.isBordered; structureChanged(); });
      labels.add(R.string.ArticleTableStriped); actions.add(() -> { table.isStriped = !table.isStriped; structureChanged(); });
      labels.add(R.string.ArticleTableCompact); actions.add(() -> { table.isCompact = !table.isCompact; structureChanged(); });
    }
    if (entry.block instanceof TdApi.InputPageBlockMap) {
      TdApi.InputPageBlockMap map = (TdApi.InputPageBlockMap) entry.block;
      labels.add(R.string.ArticleCoordinates); actions.add(() -> prompt(R.string.ArticleCoordinates, map.location.latitude + ", " + map.location.longitude, value -> { TdApi.Location location = parseLocation(value); if (location != null) { map.location = location; structureChanged(); } }));
    }
    labels.add(R.string.ArticleRtl); actions.add(() -> { working.isRtl = !working.isRtl; structureChanged(); });
    choose(R.string.ArticleBlockOptions, labels, actions);
  }

  private static final int[] TYPES = { R.string.ArticleParagraph, R.string.ArticleHeading, R.string.ArticleCode, R.string.ArticleFooter, R.string.ArticleQuote, R.string.ArticleExpandableQuote, R.string.ArticlePullQuote, R.string.ArticleList, R.string.ArticleTable, R.string.ArticleDetails, R.string.ArticleDivider, R.string.ArticleFormula, R.string.ArticleAnchor, R.string.ArticleButton, R.string.Photo, R.string.Video, R.string.Gif, R.string.Audio, R.string.File, R.string.ArticleVoiceNote, R.string.Location, R.string.ArticleCollage, R.string.ArticleSlideshow };
  private void addBlock (ArticleEditorTree.Group group, int index) {
    List<Integer> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
    for (int type : TYPES) {
      labels.add(type); actions.add(() -> {
        ArticleMediaFiles.Kind kind = mediaKind(type);
        if (kind != null) pickMedia(kind, block -> { group.insert(index, block); structureChanged(); });
        else if (type == R.string.Location) prompt(R.string.ArticleCoordinates, "", value -> {
          TdApi.Location location = parseLocation(value);
          if (location != null) { group.insert(index, new TdApi.InputPageBlockMap(location, 15, 640, 360, emptyCaption())); structureChanged(); }
        });
        else { group.insert(index, newBlock(type)); structureChanged(); }
      });
    }
    choose(R.string.ArticleAddBlock, labels, actions);
  }
  private TdApi.InputPageBlock newBlock (int type) {
    if (type == R.string.ArticleHeading) return new TdApi.InputPageBlockSectionHeading(emptyText(), 1);
    if (type == R.string.ArticleCode) return new TdApi.InputPageBlockPreformatted(emptyText(), "");
    if (type == R.string.ArticleFooter) return new TdApi.InputPageBlockFooter(emptyText());
    if (type == R.string.ArticleQuote) return new TdApi.InputPageBlockBlockQuote(paragraph(), emptyText());
    if (type == R.string.ArticleExpandableQuote) return new TdApi.InputPageBlockExpandableBlockQuote(emptyText(), emptyText());
    if (type == R.string.ArticlePullQuote) return new TdApi.InputPageBlockPullQuote(emptyText(), emptyText());
    if (type == R.string.ArticleList) return new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {new TdApi.InputPageBlockListItem(paragraph(), false, false, 0, "")});
    if (type == R.string.ArticleDetails) return new TdApi.InputPageBlockDetails(emptyText(), paragraph(), true);
    if (type == R.string.ArticleDivider) return new TdApi.InputPageBlockDivider();
    if (type == R.string.ArticleFormula) return new TdApi.InputPageBlockMathematicalExpression("");
    if (type == R.string.ArticleAnchor) return new TdApi.InputPageBlockAnchor("");
    if (type == R.string.ArticleCollage) return new TdApi.InputPageBlockCollage(new TdApi.InputPageBlock[0], emptyCaption());
    if (type == R.string.ArticleSlideshow) return new TdApi.InputPageBlockSlideshow(new TdApi.InputPageBlock[0], emptyCaption());
    if (type == R.string.ArticleButton) return new TdApi.InputPageBlockButtonRow(new TdApi.InlineButton[] {new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeUrl("https://"))}, new TdApi.PageBlockHorizontalAlignmentLeft());
    if (type == R.string.ArticleTable) {
      TdApi.PageBlockTableCell[][] cells = new TdApi.PageBlockTableCell[2][2];
      for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) cells[r][c] = new TdApi.PageBlockTableCell(emptyText(), r == 0, 1, 1, new TdApi.PageBlockHorizontalAlignmentLeft(), new TdApi.PageBlockVerticalAlignmentTop());
      return new TdApi.InputPageBlockTable(emptyText(), cells, true, false, false);
    }
    return new TdApi.InputPageBlockParagraph(emptyText());
  }

  private static int blockName (TdApi.InputPageBlock block) {
    switch (block.getConstructor()) {
      case TdApi.InputPageBlockSectionHeading.CONSTRUCTOR: return R.string.ArticleHeading;
      case TdApi.InputPageBlockPreformatted.CONSTRUCTOR: return R.string.ArticleCode;
      case TdApi.InputPageBlockFooter.CONSTRUCTOR: return R.string.ArticleFooter;
      case TdApi.InputPageBlockBlockQuote.CONSTRUCTOR: return R.string.ArticleQuote;
      case TdApi.InputPageBlockExpandableBlockQuote.CONSTRUCTOR: return R.string.ArticleExpandableQuote;
      case TdApi.InputPageBlockPullQuote.CONSTRUCTOR: return R.string.ArticlePullQuote;
      case TdApi.InputPageBlockList.CONSTRUCTOR: return R.string.ArticleList;
      case TdApi.InputPageBlockTable.CONSTRUCTOR: return R.string.ArticleTable;
      case TdApi.InputPageBlockDetails.CONSTRUCTOR: return R.string.ArticleDetails;
      case TdApi.InputPageBlockDivider.CONSTRUCTOR: return R.string.ArticleDivider;
      case TdApi.InputPageBlockMathematicalExpression.CONSTRUCTOR: return R.string.ArticleFormula;
      case TdApi.InputPageBlockAnchor.CONSTRUCTOR: return R.string.ArticleAnchor;
      case TdApi.InputPageBlockButtonRow.CONSTRUCTOR: return R.string.ArticleButton;
      case TdApi.InputPageBlockCollage.CONSTRUCTOR: return R.string.ArticleCollage;
      case TdApi.InputPageBlockSlideshow.CONSTRUCTOR: return R.string.ArticleSlideshow;
      case TdApi.InputPageBlockPhoto.CONSTRUCTOR: return R.string.Photo;
      case TdApi.InputPageBlockVideo.CONSTRUCTOR: return R.string.Video;
      case TdApi.InputPageBlockAnimation.CONSTRUCTOR: return R.string.Gif;
      case TdApi.InputPageBlockAudio.CONSTRUCTOR: return R.string.Audio;
      case TdApi.InputPageBlockDocument.CONSTRUCTOR: return R.string.File;
      case TdApi.InputPageBlockVoiceNote.CONSTRUCTOR: return R.string.ArticleVoiceNote;
      case TdApi.InputPageBlockMap.CONSTRUCTOR: return R.string.Location;
      default: return R.string.ArticleParagraph;
    }
  }

  private void choose (int title, List<Integer> labels, List<Runnable> actions) {
    if (toolAnchor != null && toolAnchor.getWindowToken() != null) {
      ArticleEditorPopup popup = new ArticleEditorPopup(context());
      for (int i = 0; i < labels.size(); i++) popup.item(0, labels.get(i), actions.get(i)); popup.show(toolAnchor); return;
    }
    String[] text = new String[labels.size()]; for (int i = 0; i < text.length; i++) text[i] = Lang.getString(labels.get(i));
    new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(title)).setItems(text, (dialog, which) -> actions.get(which).run()).show();
  }
  private void prompt (int title, String value, Consumer<String> callback) {
    EditText input = new EditText(context()); input.setText(value); input.setTextColor(Theme.textAccentColor());
    new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(title)).setView(input).setPositiveButton(Lang.getString(R.string.Save), (dialog, which) -> callback.accept(input.getText().toString())).setNegativeButton(Lang.getString(R.string.Cancel), null).show();
  }

  private static ArticleMediaFiles.Kind mediaKind (int type) {
    if (type == R.string.Photo) return ArticleMediaFiles.Kind.PHOTO;
    if (type == R.string.Video) return ArticleMediaFiles.Kind.VIDEO;
    if (type == R.string.Gif) return ArticleMediaFiles.Kind.ANIMATION;
    if (type == R.string.Audio) return ArticleMediaFiles.Kind.AUDIO;
    if (type == R.string.File) return ArticleMediaFiles.Kind.DOCUMENT;
    if (type == R.string.ArticleVoiceNote) return ArticleMediaFiles.Kind.VOICE;
    return null;
  }
  private void pickMedia (@Nullable ArticleMediaFiles.Kind kind, Consumer<TdApi.InputPageBlock> callback) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(kind == ArticleMediaFiles.Kind.PHOTO ? "image/*" : kind == ArticleMediaFiles.Kind.VIDEO ? "video/*" : kind == ArticleMediaFiles.Kind.AUDIO || kind == ArticleMediaFiles.Kind.VOICE ? "audio/*" : "*/*");
    if (kind == null) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
    context().putActivityResultHandler(PICK_MEDIA, (request, result, data) -> {
      if (result != Activity.RESULT_OK || data == null || data.getData() == null || isDestroyed()) return;
      Uri uri = data.getData();
      String mime = context().getContentResolver().getType(uri);
      ArticleMediaFiles.Kind selectedKind = kind != null ? kind : mime != null && mime.startsWith("video/") ? ArticleMediaFiles.Kind.VIDEO : "image/gif".equals(mime) ? ArticleMediaFiles.Kind.ANIMATION : ArticleMediaFiles.Kind.PHOTO;
      importMedia(uri, selectedKind, callback);
    });
    try { context().startActivityForResult(intent, PICK_MEDIA); }
    catch (RuntimeException e) { context().putActivityResultHandler(PICK_MEDIA, null); UI.showToast(R.string.ArticleMediaImportFailed, Toast.LENGTH_LONG); }
  }
  private void beginImport () { pendingImports++; importing = true; enableTree(root, false); }
  private void finishImport () { pendingImports--; importing = pendingImports != 0; if (!isDestroyed()) { enableTree(root, !sending && !importing); updateToolState(); updateHistory(); } }
  private void importMedia (Uri uri, ArticleMediaFiles.Kind selectedKind, Consumer<TdApi.InputPageBlock> callback) {
    beginImport();
    IMPORTS.execute(() -> {
        try {
          TdApi.InputPageBlock media = ArticleMediaFiles.importFile(context().getApplicationContext(), tdlib.id(), getArgumentsStrict().userId, uri, selectedKind, (tdlib.hasPremium() ? 4L : 2L) * 1024 * 1024 * 1024);
          handler.post(() -> {
            // Preserve a completed import even if Android closed the editor during the picker flow.
            try { prepareMedia(media); callback.accept(media); saveDraft(); } finally { finishImport(); }
          });
        } catch (IOException | RuntimeException e) {
          handler.post(() -> { finishImport(); if (!isDestroyed()) UI.showToast(R.string.ArticleMediaImportFailed, Toast.LENGTH_LONG); });
        }
      });
  }
  private void prepareMedia (TdApi.InputPageBlock block) {
    TdApi.InputMessageContent content = null;
    if (block instanceof TdApi.InputPageBlockPhoto) content = new TdApi.InputMessagePhoto(((TdApi.InputPageBlockPhoto) block).photo, null, false, null, false);
    else if (block instanceof TdApi.InputPageBlockVideo) content = new TdApi.InputMessageVideo(((TdApi.InputPageBlockVideo) block).video, null, false, null, false);
    else if (block instanceof TdApi.InputPageBlockAnimation) content = new TdApi.InputMessageAnimation(((TdApi.InputPageBlockAnimation) block).animation, null, false, false);
    else if (block instanceof TdApi.InputPageBlockAudio) content = new TdApi.InputMessageAudio(((TdApi.InputPageBlockAudio) block).audio, null);
    else if (block instanceof TdApi.InputPageBlockDocument) content = new TdApi.InputMessageDocument(((TdApi.InputPageBlockDocument) block).document, null);
    if (content != null) tdlib.filegen().createThumbnail(content, false);
  }
  private TdApi.Location parseLocation (String text) {
    try {
      String[] coordinates = text.trim().split("[,;\\s]+");
      if (coordinates.length != 2) throw new IllegalArgumentException();
      double lat = Double.parseDouble(coordinates[0]), lon = Double.parseDouble(coordinates[1]);
      if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) throw new IllegalArgumentException();
      return new TdApi.Location(lat, lon, 0);
    } catch (IllegalArgumentException e) { UI.showToast(R.string.ArticleInvalidValue, Toast.LENGTH_SHORT); return null; }
  }
  private void alignment (Consumer<TdApi.PageBlockHorizontalAlignment> callback) {
    choose(R.string.ArticleAlignment, Arrays.asList(R.string.ArticleLeft, R.string.ArticleCenter, R.string.ArticleRight), Arrays.asList(() -> callback.accept(new TdApi.PageBlockHorizontalAlignmentLeft()), () -> callback.accept(new TdApi.PageBlockHorizontalAlignmentCenter()), () -> callback.accept(new TdApi.PageBlockHorizontalAlignmentRight())));
  }
  private void buttonOptions (TdApi.InputPageBlockButtonRow row, int index) {
    editButton(row.buttons[index], this::structureChanged, () -> { ArrayList<TdApi.InlineButton> buttons = new ArrayList<>(Arrays.asList(row.buttons)); buttons.remove(index); row.buttons = buttons.toArray(new TdApi.InlineButton[0]); structureChanged(); });
  }
  private void editButton (TdApi.InlineButton button, Runnable changed) { editButton(button, changed, null); }
  private void editButton (TdApi.InlineButton button, Runnable changed, Runnable remove) {
    List<Integer> labels = new ArrayList<>(Arrays.asList(R.string.ArticleLink, R.string.ArticleCopyButton, R.string.ArticleUser, R.string.ArticleButtonDefault, R.string.ArticleButtonPrimary, R.string.ArticleButtonSuccess, R.string.ArticleButtonDanger, R.string.ArticleButtonLink));
    List<Runnable> actions = new ArrayList<>(Arrays.asList(
      () -> prompt(R.string.ArticleLink, button.type instanceof TdApi.InlineKeyboardButtonTypeUrl ? ((TdApi.InlineKeyboardButtonTypeUrl) button.type).url : "https://", value -> { button.type = new TdApi.InlineKeyboardButtonTypeUrl(value); changed.run(); }),
      () -> prompt(R.string.ArticleCopyButton, button.type instanceof TdApi.InlineKeyboardButtonTypeCopyText ? ((TdApi.InlineKeyboardButtonTypeCopyText) button.type).text : "", value -> { button.type = new TdApi.InlineKeyboardButtonTypeCopyText(value); changed.run(); }),
      () -> pickUser(user -> { button.type = new TdApi.InlineKeyboardButtonTypeUser(user); changed.run(); }),
      () -> { button.style = new TdApi.ButtonStyleDefault(); changed.run(); },
      () -> { button.style = new TdApi.ButtonStylePrimary(); changed.run(); },
      () -> { button.style = new TdApi.ButtonStyleSuccess(); changed.run(); },
      () -> { button.style = new TdApi.ButtonStyleDanger(); changed.run(); },
      () -> { button.style = new TdApi.ButtonStyleLink(); changed.run(); }));
    if (remove != null) { labels.add(R.string.Delete); actions.add(remove); }
    choose(R.string.ArticleButton, labels, actions);
  }

  private void saveDraft () {
    handler.removeCallbacks(saveDraft);
    if (working == null || sent || recoveryPending || getArgumentsStrict().userId != tdlib.myUserId()) return;
    if (!draftTouched && pendingMessageId == 0 && !sending) return;
    ArticleDocument document = new ArticleDocument(working);
    if (store != null) try { store.write(new ArticleDraftStore.Snapshot(document, baseline, pendingMessageId)); } catch (IOException e) {
      if (!draftWriteFailed) UI.showToast(R.string.ArticleDraftSaveFailed, Toast.LENGTH_LONG);
      draftWriteFailed = true;
    }
    Args args = getArgumentsStrict();
    if (store != null && args.messageId == 0 && !args.owner.isDestroyed() && pendingMessageId == 0 && !sending) args.owner.saveArticleDraft(document, args.chatId, args.topic);
  }

  private void send () {
    if (sending || importing || recoveryPending || getArgumentsStrict().userId != tdlib.myUserId()) return;
    TdlibOptions options = tdlib.options();
    ArticleValidator.Problem problem = ArticleValidator.validate(working, new ArticleValidator.Limits(options.richMessageTextLengthMax, options.richMessageBlockCountMax, options.richMessageDepthMax, options.richMessageMediaCountMax, options.richMessageTableColumnCountMax));
    if (problem != null) { UI.showToast(problem == ArticleValidator.Problem.EMPTY ? R.string.ArticleEmpty : R.string.ArticleLimitExceeded, Toast.LENGTH_LONG); return; }
    saveDraft();
    Args args = getArgumentsStrict();
    ArticleDocument snapshot = new ArticleDocument(working);
    if (args.messageId != 0 && snapshot.equals(baseline)) {
      if (store != null) try { store.clearIfUnchanged(snapshot); } catch (IOException ignored) { }
      finishSending(true); return;
    }
    if (args.messageId == 0) {
      if (args.owner.areScheduledOnly()) tdlib.ui().showScheduleOptions(this, args.chatId, false, (options1, disableMarkdown) -> {
        if (!isDestroyed() && !sending) sendNew(new ArticleDocument(working), options1);
      }, null, null);
      else sendNew(snapshot, Td.newSendOptions());
    } else {
      setSending(true);
      tdlib.send(new TdApi.GetFullRichMessage(args.chatId, args.messageId), (full, error) -> runOnUiThreadOptional(() -> {
        if (error != null) { UI.showError(error); finishSending(false); return; }
        ArticleDocument remote;
        try { remote = ArticleDocument.received(full); } catch (IllegalArgumentException e) { UI.showToast(R.string.ArticleReadOnly, Toast.LENGTH_LONG); finishSending(false); return; }
        if (!remote.equals(baseline)) {
          finishSending(false);
          new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticleEditConflict))
            .setMessage(Lang.getString(R.string.ArticleEditConflictHint))
            .setPositiveButton(Lang.getString(R.string.ArticleOverwrite), (dialog, which) -> { baseline = remote; edit(snapshot); })
            .setNegativeButton(Lang.getString(R.string.Cancel), null).show();
        } else edit(snapshot);
      }));
    }
  }
  private void sendNew (ArticleDocument snapshot, TdApi.MessageSendOptions options) {
    Args args = getArgumentsStrict();
    if (args.userId != tdlib.myUserId()) return;
    TdlibOptions limits = tdlib.options();
    if (ArticleValidator.validate(snapshot.toInput(), new ArticleValidator.Limits(limits.richMessageTextLengthMax, limits.richMessageBlockCountMax, limits.richMessageDepthMax, limits.richMessageMediaCountMax, limits.richMessageTableColumnCountMax)) != null) { UI.showToast(R.string.ArticleLimitExceeded, Toast.LENGTH_LONG); return; }
    setSending(true); saveDraft(); createSendTracker(snapshot);
    if (!args.owner.sendArticle(snapshot, args.chatId, args.topic, options, sendTracker::accept)) sendTracker.accept(null);
  }
  private void createSendTracker (ArticleDocument snapshot) {
    ArticleDraftStore recoveryStore = store;
    sendTracker = new ArticleSendTracker(tdlib, getArgumentsStrict().chatId, new ArticleSendTracker.Callback() {
      @Override public void onPending (long messageId) {
        handler.post(() -> { pendingMessageId = messageId; saveDraft(); });
      }
      @Override public void onComplete (boolean success, TdApi.Error error) {
        handler.post(() -> {
          pendingMessageId = 0;
          if (success && recoveryStore != null) try { recoveryStore.clearIfUnchanged(snapshot); } catch (IOException ignored) { }
          if (error != null) UI.showError(error);
          if (isDestroyed()) { sent = success; if (!success) saveDraft(); return; }
          finishSending(success);
        });
      }
    });
  }
  private void edit (ArticleDocument snapshot) {
    Args args = getArgumentsStrict(); setSending(true);
    tdlib.send(new TdApi.GetMessageProperties(args.chatId, args.messageId), (properties, error) -> {
      if (isDestroyed() || args.userId != tdlib.myUserId()) return;
      if (error != null || properties == null || !properties.canBeEdited) {
        handler.post(() -> { if (error != null) UI.showError(error); else UI.showToast(R.string.ArticleEditUnavailable, Toast.LENGTH_LONG); finishSending(false); });
        return;
      }
      tdlib.send(new TdApi.EditMessageText(args.chatId, args.messageId, null, new TdApi.InputMessageRichMessage(snapshot.toInput(), false)), (message, editError) -> handler.post(() -> {
        boolean success = editError == null || "MESSAGE_NOT_MODIFIED".equals(editError.message);
        if (!success) UI.showError(editError);
        if (success && store != null) try { store.clearIfUnchanged(snapshot); } catch (IOException ignored) { }
        if (isDestroyed()) { sent = success; return; }
        finishSending(success);
      }));
    });
  }
  private void setSending (boolean value) { sending = value; enableTree(root, !value); if (!value) { updateHistory(); updateToolState(); } }
  private static void enableTree (View view, boolean enabled) {
    view.setEnabled(enabled);
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) enableTree(((ViewGroup) view).getChildAt(i), enabled);
  }
  private void finishSending (boolean success) {
    if (isDestroyed()) { sent = success; return; }
    setSending(false);
    if (success) {
      sent = true; handler.removeCallbacks(saveDraft);
      navigateBack();
    } else saveDraft();
  }
  @Override public void onBlur () { saveDraft(); super.onBlur(); }
  @Override public void destroy () { if (aiDialog != null) aiDialog.dismiss(); if (dateDialog != null) dateDialog.dismiss(); if (formulaDialog != null) formulaDialog.dismiss(); saveDraft(); handler.removeCallbacks(saveDraft); closeEmoji(); for (ArticleEditorMedia media : mediaViews) media.performDestroy(); mediaViews.clear(); if (fields != null) fields.performDestroy(); super.destroy(); }
}
