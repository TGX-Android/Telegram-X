/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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

/** Structured article composition. The working tree is never replaced with a plain-text projection. */
public final class ArticleEditorController extends ViewController<ArticleEditorController.Args> {
  public static final class Args {
    public final MessagesController owner;
    public final long chatId, messageId, userId;
    public final TdApi.MessageTopic topic;
    public final ArticleDocument document;
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
  private LinearLayout fields;
  private ScrollView scroll;
  private ArticleTextInput focused;
  private final List<ArticleTextInput> textInputs = new ArrayList<>();
  private boolean restoring;
  private Button sendButton;
  private LinearLayout root;
  private CheckBox rtlControl;
  private boolean sending, sent, draftWriteFailed, recoveryPending, importing;
  private boolean draftTouched;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable saveDraft = this::saveDraft;

  public ArticleEditorController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_articleEditor; }
  @Override public CharSequence getName () { return Lang.getString(getArgumentsStrict().messageId == 0 ? R.string.ArticleCreate : R.string.ArticleEdit); }
  @Override protected int getBackButton () { return BackHeaderButton.TYPE_BACK; }

  @Override protected View onCreateView (Context context) {
    Args args = getArgumentsStrict();
    ArticleDocument initial = args.document;
    baseline = args.document;
    ArticleDraftStore.Snapshot recovery = null;
    try {
      store = new ArticleDraftStore(context, tdlib.id(), args.userId, args.chatId, args.topic, args.messageId);
      recovery = store.read();
      draftTouched = recovery != null;
      if (recovery != null && (recovery.baseline.equals(initial) || recovery.document.equals(initial) || recovery.pendingMessageId != 0)) {
        initial = recovery.document; baseline = recovery.baseline; pendingMessageId = recovery.pendingMessageId;
      } else if (recovery != null) recoveryPending = true;
    } catch (IOException | IllegalStateException e) {
      store = null; // Keep the unreadable draft for recovery; do not overwrite it.
      UI.showToast(R.string.ArticleDraftRestoreFailed, Toast.LENGTH_LONG);
    }
    working = initial.toInput();
    history = new ArticleHistory(initial);
    root = new LinearLayout(context);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Theme.fillingColor());
    root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    LinearLayout actions = new LinearLayout(context);
    button(actions, R.string.ArticleUndo, () -> restore(history.undo()));
    button(actions, R.string.ArticleRedo, () -> restore(history.redo()));
    button(actions, R.string.ArticlePreview, this::preview);
    button(actions, R.string.ArticleAddBlock, () -> addBlock(ArticleEditorTree.root(working), ArticleEditorTree.root(working).blocks().length));
    HorizontalScrollView actionScroll = new HorizontalScrollView(context);
    actionScroll.addView(actions); root.addView(actionScroll);
    scroll = new ScrollView(context);
    fields = new LinearLayout(context);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(Screen.dp(12f), 0, Screen.dp(12f), Screen.dp(24f));
    scroll.addView(fields);
    root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    HorizontalScrollView formattingScroll = new HorizontalScrollView(context);
    LinearLayout formatting = new LinearLayout(context);
    formattingScroll.addView(formatting);
    button(formatting, R.string.ArticleBold, () -> format(new TdApi.RichTextBold(emptyText())));
    button(formatting, R.string.ArticleItalic, () -> format(new TdApi.RichTextItalic(emptyText())));
    button(formatting, R.string.ArticleUnderline, () -> format(new TdApi.RichTextUnderline(emptyText())));
    button(formatting, R.string.ArticleStrike, () -> format(new TdApi.RichTextStrikethrough(emptyText())));
    button(formatting, R.string.ArticleSpoiler, () -> format(new TdApi.RichTextSpoiler(emptyText())));
    button(formatting, R.string.ArticleCode, () -> format(new TdApi.RichTextFixed(emptyText())));
    button(formatting, R.string.ArticleLink, () -> prompt(R.string.ArticleLink, "https://", url -> format(new TdApi.RichTextUrl(emptyText(), url, false))));
    button(formatting, R.string.ArticleFormula, () -> prompt(R.string.ArticleFormula, "", value -> { if (focused != null) focused.insert(new TdApi.RichTextMathematicalExpression(value)); }));
    button(formatting, R.string.ArticleEmoji, this::emojiPicker);
    button(formatting, R.string.ArticleUser, () -> {
      ArticleTextInput target = focused;
      if (target != null) pickUser(user -> target.format(new TdApi.RichTextMentionName(emptyText(), user)));
    });
    button(formatting, R.string.ArticleButton, () -> {
      ArticleTextInput target = focused;
      if (target != null) prompt(R.string.ArticleLink, "https://", url -> target.format(new TdApi.RichTextButton(new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeUrl(url)))));
    });
    button(formatting, R.string.ArticleMoreFormats, this::moreFormats);
    button(formatting, R.string.ArticleEditElement, this::editElement);
    root.addView(formattingScroll);
    LinearLayout footer = new LinearLayout(context);
    CheckBox rtl = rtlControl = new CheckBox(context);
    rtl.setText(Lang.getString(R.string.ArticleRtl)); rtl.setChecked(working.isRtl);
    rtl.setOnCheckedChangeListener((view, checked) -> { working.isRtl = checked; if (!restoring) changed(); });
    footer.addView(rtl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    sendButton = button(footer, args.messageId == 0 ? R.string.Send : R.string.Save, this::send);
    root.addView(footer);
    rebuildFields();
    if (recoveryPending) {
      ArticleDraftStore.Snapshot recovered = recovery;
      handler.post(() -> new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticleDraftConflict))
        .setMessage(Lang.getString(R.string.ArticleDraftConflictHint)).setCancelable(false)
        .setPositiveButton(Lang.getString(R.string.ArticleRecoverLocal), (dialog, which) -> {
          recoveryPending = false; baseline = recovered.baseline; restore(recovered.document); history = new ArticleHistory(recovered.document); saveDraft();
        }).setNegativeButton(Lang.getString(R.string.ArticleUseServer), (dialog, which) -> { recoveryPending = false; saveDraft(); }).show());
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

  private Button button (LinearLayout parent, int text, Runnable action) {
    Button button = new Button(context());
    button.setText(Lang.getString(text));
    button.setTextColor(Theme.textLinkColor());
    button.setOnClickListener(view -> { if (!sending && !importing && !recoveryPending) action.run(); });
    parent.addView(button);
    return button;
  }

  private void format (TdApi.RichText wrapper) { if (focused != null) focused.format(wrapper); }

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
    if (focused == null) return;
    ArticleTextInput target = focused;
    org.thunderdog.challegram.widget.EmojiLayout emoji = new org.thunderdog.challegram.widget.EmojiLayout(context());
    AlertDialog dialog = new AlertDialog.Builder(context(), Theme.dialogTheme()).setView(emoji).setNegativeButton(Lang.getString(R.string.Done), null).create();
    emoji.initWithMediasEnabled(this, false, new org.thunderdog.challegram.widget.EmojiLayout.Listener() {
      @Override public void onEnterEmoji (String value) { target.insert(new TdApi.RichTextPlain(value)); }
      @Override public void onEnterCustomEmoji (org.thunderdog.challegram.component.sticker.TGStickerObj sticker) { target.insert(new TdApi.RichTextCustomEmoji(sticker.getCustomEmojiId(), sticker.getAllEmoji())); }
    }, this, false);
    emoji.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(360f)));
    dialog.setOnDismissListener(ignored -> emoji.destroy()); dialog.show();
  }
  private void moreFormats () {
    if (focused == null) return;
    choose(R.string.ArticleMoreFormats, Arrays.asList(R.string.ArticleMarked, R.string.ArticleSubscript, R.string.ArticleSuperscript, R.string.ArticleEmail, R.string.ArticlePhone, R.string.ArticleMention, R.string.ArticleHashtag, R.string.ArticleCashtag, R.string.ArticleBankCard, R.string.ArticleCommand, R.string.ArticleDate, R.string.ArticleAnchor, R.string.ArticleAnchorLink, R.string.ArticleReference, R.string.ArticleReferenceLink), Arrays.asList(
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
      () -> prompt(R.string.ArticleReferenceLink, "", value -> format(new TdApi.RichTextReferenceLink(emptyText(), value, "")))));
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
    java.util.Map<Integer, TdApi.File> files = new java.util.concurrent.ConcurrentHashMap<>();
    java.util.Set<Integer> ids = new java.util.HashSet<>();
    ArticleCodec.visit(working, (value, depth) -> { if (value instanceof TdApi.InputFileId) ids.add(((TdApi.InputFileId) value).id); });
    Runnable show = () -> runOnUiThreadOptional(() -> {
      try {
        TdApi.RichMessage article = new ArticlePreviewMapper(file -> {
          if (file instanceof TdApi.InputFileId) { TdApi.File known = files.get(((TdApi.InputFileId) file).id); if (known == null) throw new IllegalArgumentException("Missing preview file"); return known; }
          if (file instanceof TdApi.InputFileLocal || file instanceof TdApi.InputFileGenerated) {
            String path = file instanceof TdApi.InputFileLocal ? ((TdApi.InputFileLocal) file).path : ((TdApi.InputFileGenerated) file).originalPath; long size = new java.io.File(path).length();
            return new TdApi.File(-Math.max(1, path.hashCode() & 0x7fffffff), size, size, new TdApi.LocalFile(path, false, false, false, true, 0, size, size), new TdApi.RemoteFile("", "", false, false, 0));
          }
          throw new IllegalArgumentException("File is not ready for preview");
        }).preview(document);
        ArticlePreviewController controller = new ArticlePreviewController(context(), tdlib); controller.setArguments(article); navigateTo(controller);
      } catch (IllegalArgumentException e) { UI.showToast(R.string.ArticlePreviewFailed, Toast.LENGTH_LONG); }
    });
    if (ids.isEmpty()) show.run();
    else {
      java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(ids.size());
      for (int id : ids) tdlib.send(new TdApi.GetFile(id), (file, error) -> { if (file != null) files.put(id, file); if (remaining.decrementAndGet() == 0) show.run(); });
    }
  }
  private static TdApi.RichText emptyText () { return new TdApi.RichTextPlain(""); }
  private static TdApi.PageBlockCaption emptyCaption () { return new TdApi.PageBlockCaption(emptyText(), emptyText()); }
  private static TdApi.InputPageBlock[] paragraph () { return new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(emptyText())}; }

  private void rebuildFields () {
    int scrollY = scroll.getScrollY();
    fields.removeAllViews();
    textInputs.clear();
    focused = null;
    for (ArticleEditorTree.Entry entry : ArticleEditorTree.entries(working)) {
      LinearLayout card = new LinearLayout(context());
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(Screen.dp(Math.min(4, entry.depth) * 12f), Screen.dp(6f), 0, Screen.dp(12f));
      LinearLayout bar = new LinearLayout(context());
      TextView name = new TextView(context()); name.setText(Lang.getString(blockName(entry.block))); name.setTextColor(Theme.textAccentColor());
      bar.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
      button(bar, R.string.ArticleBlockOptions, () -> blockOptions(entry));
      card.addView(bar);
      for (ArticleEditorTree.TextField field : ArticleEditorTree.fields(entry.block)) {
        ArticleTextInput input = new ArticleTextInput(context(), field.value, value -> { field.set.accept(value); changed(); });
        input.setHint(Lang.getString(field.name == ArticleEditorTree.FieldName.CREDIT ? R.string.ArticleCredit : field.name == ArticleEditorTree.FieldName.CAPTION ? R.string.ArticleCaption : R.string.ArticleText));
        textInputs.add(input);
        input.setOnFocusChangeListener((view, hasFocus) -> { if (hasFocus) { focused = input; rememberSelection(); } });
        input.setSelectionListener(this::rememberSelection);
        card.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      }
      if (entry.block instanceof TdApi.InputPageBlockMathematicalExpression) {
        TdApi.InputPageBlockMathematicalExpression formula = (TdApi.InputPageBlockMathematicalExpression) entry.block;
        textField(card, R.string.ArticleFormula, formula.expression, value -> formula.expression = value);
      } else if (entry.block instanceof TdApi.InputPageBlockAnchor) {
        TdApi.InputPageBlockAnchor anchor = (TdApi.InputPageBlockAnchor) entry.block;
        textField(card, R.string.ArticleAnchor, anchor.name, value -> anchor.name = value);
      } else if (entry.block instanceof TdApi.InputPageBlockPreformatted) {
        TdApi.InputPageBlockPreformatted code = (TdApi.InputPageBlockPreformatted) entry.block;
        textField(card, R.string.ArticleLanguage, code.language, value -> code.language = value);
      } else if (entry.block instanceof TdApi.InputPageBlockButtonRow) {
        for (TdApi.InlineButton key : ((TdApi.InputPageBlockButtonRow) entry.block).buttons) {
          if (key.type instanceof TdApi.InlineKeyboardButtonTypeUrl) textField(card, R.string.ArticleLink, ((TdApi.InlineKeyboardButtonTypeUrl) key.type).url, value -> ((TdApi.InlineKeyboardButtonTypeUrl) key.type).url = value);
          else if (key.type instanceof TdApi.InlineKeyboardButtonTypeCopyText) textField(card, R.string.ArticleCopyButton, ((TdApi.InlineKeyboardButtonTypeCopyText) key.type).text, value -> ((TdApi.InlineKeyboardButtonTypeCopyText) key.type).text = value);
        }
      }
      mediaProperties(card, entry);
      if (entry.block instanceof TdApi.InputPageBlockList) listProperties(card, (TdApi.InputPageBlockList) entry.block);
      if (entry.block instanceof TdApi.InputPageBlockTable) tableProperties(card, (TdApi.InputPageBlockTable) entry.block);
      fields.addView(card);
    }
    scroll.post(() -> scroll.scrollTo(0, scrollY));
  }

  private void textField (LinearLayout parent, int label, String value, Consumer<String> setter) {
    EditText edit = new EditText(context()); edit.setTextColor(Theme.textAccentColor()); edit.setHint(Lang.getString(label)); edit.setText(value);
    edit.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { setter.accept(s.toString()); changed(); }
      @Override public void afterTextChanged (Editable s) { }
    });
    parent.addView(edit);
  }

  private void changed () {
    if (sending || restoring) return;
    draftTouched = true;
    history.push(new ArticleDocument(working));
    rememberSelection();
    handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 600);
  }
  private void rememberSelection () {
    if (!restoring && history != null && scroll != null) history.rememberSelection(textInputs.indexOf(focused), focused == null ? 0 : focused.getSelectionStart(), focused == null ? 0 : focused.getSelectionEnd(), scroll.getScrollY());
  }
  private void structureChanged () { changed(); if (!isDestroyed()) rebuildFields(); }
  private void restore (ArticleDocument document) {
    int[] selection = history.selection(); restoring = true;
    working = document.toInput(); rtlControl.setChecked(working.isRtl); rebuildFields();
    if (selection[0] >= 0 && selection[0] < textInputs.size()) {
      ArticleTextInput field = textInputs.get(selection[0]); field.requestFocus(); focused = field;
      field.setSelection(Math.max(0, Math.min(field.length(), selection[1])), Math.max(0, Math.min(field.length(), selection[2])));
    }
    restoring = false;
    scroll.post(() -> scroll.scrollTo(0, selection[3]));
    handler.removeCallbacks(saveDraft); handler.postDelayed(saveDraft, 600);
  }

  private void blockOptions (ArticleEditorTree.Entry entry) {
    List<Integer> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
    labels.add(R.string.ArticleMoveUp); actions.add(() -> { entry.group.move(entry.index, -1); structureChanged(); });
    labels.add(R.string.ArticleMoveDown); actions.add(() -> { entry.group.move(entry.index, 1); structureChanged(); });
    labels.add(R.string.ArticleAddAfter); actions.add(() -> addBlock(entry.group, entry.index + 1));
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
  private void pickMedia (ArticleMediaFiles.Kind kind, Consumer<TdApi.InputPageBlock> callback) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(kind == ArticleMediaFiles.Kind.PHOTO ? "image/*" : kind == ArticleMediaFiles.Kind.VIDEO ? "video/*" : kind == ArticleMediaFiles.Kind.AUDIO || kind == ArticleMediaFiles.Kind.VOICE ? "audio/*" : "*/*");
    context().putActivityResultHandler(PICK_MEDIA, (request, result, data) -> {
      if (result != Activity.RESULT_OK || data == null || data.getData() == null || isDestroyed()) return;
      Uri uri = data.getData(); importing = true; enableTree(root, false);
      IMPORTS.execute(() -> {
        try {
          TdApi.InputPageBlock media = ArticleMediaFiles.importFile(context().getApplicationContext(), tdlib.id(), getArgumentsStrict().userId, uri, kind, (tdlib.hasPremium() ? 4L : 2L) * 1024 * 1024 * 1024);
          handler.post(() -> {
            importing = false;
            // Preserve a completed import even if Android closed the editor during the picker flow.
            prepareMedia(media); callback.accept(media); saveDraft();
            if (!isDestroyed()) enableTree(root, !sending);
          });
        } catch (IOException | RuntimeException e) {
          handler.post(() -> { importing = false; if (!isDestroyed()) { enableTree(root, !sending); UI.showToast(R.string.ArticleMediaImportFailed, Toast.LENGTH_LONG); } });
        }
      });
    });
    try { context().startActivityForResult(intent, PICK_MEDIA); }
    catch (RuntimeException e) { context().putActivityResultHandler(PICK_MEDIA, null); UI.showToast(R.string.ArticleMediaImportFailed, Toast.LENGTH_LONG); }
  }
  private void checkBox (LinearLayout parent, int label, boolean checked, Consumer<Boolean> setter) {
    CheckBox check = new CheckBox(context()); check.setText(Lang.getString(label)); check.setTextColor(Theme.textAccentColor()); check.setChecked(checked);
    check.setOnCheckedChangeListener((view, value) -> { setter.accept(value); changed(); }); parent.addView(check);
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
  private void integerField (LinearLayout parent, int label, int value, int min, int max, Consumer<Integer> setter) {
    textField(parent, label, Integer.toString(value), text -> { try { int number = Integer.parseInt(text); if (number >= min && number <= max) setter.accept(number); } catch (NumberFormatException ignored) { } });
  }
  private void mediaProperties (LinearLayout card, ArticleEditorTree.Entry entry) {
    TdApi.InputPageBlock block = entry.block;
    ArticleMediaFiles.Kind kind = mediaKind(blockName(block));
    if (kind != null) button(card, R.string.ArticleReplaceMedia, () -> pickMedia(kind, replacement -> {
      TdApi.PageBlockCaption before = ArticleEditorTree.caption(block), after = ArticleEditorTree.caption(replacement);
      after.text = before.text; after.credit = before.credit;
      if (block instanceof TdApi.InputPageBlockPhoto) ((TdApi.InputPageBlockPhoto) replacement).hasSpoiler = ((TdApi.InputPageBlockPhoto) block).hasSpoiler;
      if (block instanceof TdApi.InputPageBlockVideo) ((TdApi.InputPageBlockVideo) replacement).hasSpoiler = ((TdApi.InputPageBlockVideo) block).hasSpoiler;
      if (block instanceof TdApi.InputPageBlockAnimation) ((TdApi.InputPageBlockAnimation) replacement).hasSpoiler = ((TdApi.InputPageBlockAnimation) block).hasSpoiler;
      entry.group.remove(entry.index); entry.group.insert(entry.index, replacement); structureChanged();
    }));
    if (block instanceof TdApi.InputPageBlockPhoto) { TdApi.InputPageBlockPhoto b = (TdApi.InputPageBlockPhoto) block; checkBox(card, R.string.ArticleSpoiler, b.hasSpoiler, value -> b.hasSpoiler = value); }
    if (block instanceof TdApi.InputPageBlockVideo) { TdApi.InputPageBlockVideo b = (TdApi.InputPageBlockVideo) block; checkBox(card, R.string.ArticleSpoiler, b.hasSpoiler, value -> b.hasSpoiler = value); }
    if (block instanceof TdApi.InputPageBlockAnimation) { TdApi.InputPageBlockAnimation b = (TdApi.InputPageBlockAnimation) block; checkBox(card, R.string.ArticleSpoiler, b.hasSpoiler, value -> b.hasSpoiler = value); }
    if (block instanceof TdApi.InputPageBlockAudio) {
      TdApi.InputAudio audio = ((TdApi.InputPageBlockAudio) block).audio;
      textField(card, R.string.ArticleAudioTitle, audio.title, value -> audio.title = value);
      textField(card, R.string.ArticlePerformer, audio.performer, value -> audio.performer = value);
    }
    if (block instanceof TdApi.InputPageBlockMap) {
      TdApi.InputPageBlockMap map = (TdApi.InputPageBlockMap) block;
      button(card, R.string.ArticleCoordinates, () -> prompt(R.string.ArticleCoordinates, map.location.latitude + ", " + map.location.longitude, value -> { TdApi.Location location = parseLocation(value); if (location != null) { map.location = location; structureChanged(); } }));
      integerField(card, R.string.ArticleMapZoom, map.zoom, 0, 24, value -> map.zoom = value);
    }
    if (block instanceof TdApi.InputPageBlockButtonRow) {
      TdApi.InputPageBlockButtonRow row = (TdApi.InputPageBlockButtonRow) block;
      button(card, R.string.ArticleAddButton, () -> { row.buttons = Arrays.copyOf(row.buttons, row.buttons.length + 1); row.buttons[row.buttons.length - 1] = new TdApi.InlineButton(emptyText(), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeUrl("https://")); structureChanged(); });
      for (int i = 0; i < row.buttons.length; i++) {
        final int index = i;
        Button options = button(card, R.string.ArticleButton, () -> buttonOptions(row, index)); options.setText(Lang.getString(R.string.ArticleButtonNumber, i + 1));
      }
      button(card, R.string.ArticleAlignment, () -> alignment(value -> { row.align = value; structureChanged(); }));
    }
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
  private void listProperties (LinearLayout card, TdApi.InputPageBlockList list) {
    for (int i = 0; i < list.items.length; i++) {
      final int index = i; TdApi.InputPageBlockListItem item = list.items[i];
      LinearLayout itemCard = new LinearLayout(context()); itemCard.setOrientation(LinearLayout.VERTICAL); card.addView(itemCard);
      checkBox(itemCard, R.string.ArticleCheckbox, item.hasCheckbox, value -> item.hasCheckbox = value);
      checkBox(itemCard, R.string.ArticleChecked, item.isChecked, value -> item.isChecked = value);
      integerField(itemCard, R.string.ArticleListStart, item.value, 0, Integer.MAX_VALUE, value -> item.value = value);
      button(itemCard, R.string.ArticleListStyle, () -> {
        String[] styles = {"•", "1", "a", "A", "i", "I"};
        new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticleListStyle)).setItems(styles, (dialog, which) -> {
          for (int j = 0; j < list.items.length; j++) { list.items[j].type = which == 0 ? "" : styles[which]; list.items[j].value = which == 0 ? 0 : j + 1; }
          structureChanged();
        }).show();
      });
      button(itemCard, R.string.Delete, () -> { ArrayList<TdApi.InputPageBlockListItem> items = new ArrayList<>(Arrays.asList(list.items)); items.remove(index); list.items = items.toArray(new TdApi.InputPageBlockListItem[0]); structureChanged(); });
      button(itemCard, R.string.ArticleMoveUp, () -> { if (index > 0) { list.items[index] = list.items[index - 1]; list.items[index - 1] = item; structureChanged(); } });
      button(itemCard, R.string.ArticleMoveDown, () -> { if (index + 1 < list.items.length) { list.items[index] = list.items[index + 1]; list.items[index + 1] = item; structureChanged(); } });
    }
  }
  private static TdApi.PageBlockTableCell cell () { return new TdApi.PageBlockTableCell(emptyText(), false, 1, 1, new TdApi.PageBlockHorizontalAlignmentLeft(), new TdApi.PageBlockVerticalAlignmentTop()); }
  private void tableProperties (LinearLayout card, TdApi.InputPageBlockTable table) {
    checkBox(card, R.string.ArticleTableBorder, table.isBordered, value -> table.isBordered = value);
    checkBox(card, R.string.ArticleTableStriped, table.isStriped, value -> table.isStriped = value);
    checkBox(card, R.string.ArticleTableCompact, table.isCompact, value -> table.isCompact = value);
    button(card, R.string.ArticleAddRow, () -> { int count = table.cells.length == 0 ? 2 : table.cells[0].length; table.cells = Arrays.copyOf(table.cells, table.cells.length + 1); TdApi.PageBlockTableCell[] row = new TdApi.PageBlockTableCell[count]; for (int i = 0; i < count; i++) row[i] = cell(); table.cells[table.cells.length - 1] = row; structureChanged(); });
    button(card, R.string.ArticleAddColumn, () -> { for (int r = 0; r < table.cells.length; r++) { TdApi.PageBlockTableCell[] row = table.cells[r]; row = Arrays.copyOf(row, row.length + 1); row[row.length - 1] = cell(); table.cells[r] = row; } structureChanged(); });
    button(card, R.string.ArticleRemoveRow, () -> { if (table.cells.length > 0) table.cells = Arrays.copyOf(table.cells, table.cells.length - 1); structureChanged(); });
    button(card, R.string.ArticleRemoveColumn, () -> { for (int r = 0; r < table.cells.length; r++) if (table.cells[r].length > 0) table.cells[r] = Arrays.copyOf(table.cells[r], table.cells[r].length - 1); structureChanged(); });
    for (int r = 0; r < table.cells.length; r++) for (int c = 0; c < table.cells[r].length; c++) {
      TdApi.PageBlockTableCell cell = table.cells[r][c];
      Button properties = button(card, R.string.ArticleCell, () -> {
        LinearLayout form = new LinearLayout(context()); form.setOrientation(LinearLayout.VERTICAL);
        checkBox(form, R.string.ArticleCellHeader, cell.isHeader, value -> cell.isHeader = value);
        integerField(form, R.string.ArticleColspan, cell.colspan, 1, tdlib.options().richMessageTableColumnCountMax, value -> cell.colspan = value);
        integerField(form, R.string.ArticleRowspan, cell.rowspan, 1, table.cells.length, value -> cell.rowspan = value);
        button(form, R.string.ArticleAlignment, () -> alignment(value -> { cell.align = value; changed(); }));
        button(form, R.string.ArticleVerticalAlignment, () -> choose(R.string.ArticleVerticalAlignment, Arrays.asList(R.string.ArticleTop, R.string.ArticleMiddle, R.string.ArticleBottom), Arrays.asList(() -> { cell.valign = new TdApi.PageBlockVerticalAlignmentTop(); changed(); }, () -> { cell.valign = new TdApi.PageBlockVerticalAlignmentMiddle(); changed(); }, () -> { cell.valign = new TdApi.PageBlockVerticalAlignmentBottom(); changed(); })));
        new AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticleCell)).setView(form).setPositiveButton(Lang.getString(R.string.Done), (dialog, which) -> structureChanged()).show();
      });
      properties.setText(Lang.getString(R.string.ArticleCellPosition, r + 1, c + 1));
    }
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
  private void setSending (boolean value) { sending = value; enableTree(root, !value); }
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
  @Override public void destroy () { saveDraft(); handler.removeCallbacks(saveDraft); super.destroy(); }
}
