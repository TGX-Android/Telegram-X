/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.View;
import androidx.annotation.NonNull;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.data.article.ArticleCodec;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.widget.ArticleBodyView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A native message body. The server document stays separate from its visual expansion state. */
public final class TGMessageArticle extends TGMessage implements Text.ClickCallback {
  public static final class Row {
    public final PageBlock block;
    public final int top, height, indent, left, right;
    private Row (PageBlock block, int top, int height, int indent, int left, int right) {
      this.block = block; this.top = top; this.height = height; this.indent = indent;
      this.left = left; this.right = right;
    }
  }

  private TdApi.RichMessage article;
  private TdApi.RichMessage displayArticle;
  private List<PageBlock> blocks;
  private List<Row> rows = Collections.emptyList();
  private int width, height, bodyHeight, revision, requestGeneration;
  private boolean loading, loadFailed;
  private boolean checkboxPending;
  private String pendingAnchor;
  private java.lang.ref.WeakReference<ArticleBodyView> pendingAnchorView;

  public TGMessageArticle (MessagesManager manager, TdApi.Message message, TdApi.MessageRichMessage content) {
    super(manager, message);
    article = content.message;
  }

  public TdApi.RichMessage getArticle () { return article; }
  public List<Row> getArticleRows () { return rows; }
  public int getArticleRevision () { return revision; }
  public int getArticleBodyHeight () { return bodyHeight; }
  public boolean hasExpandButton () { return height > bodyHeight; }
  public int getExpandButtonText () { return loading ? R.string.ArticleLoading : loadFailed ? R.string.ArticleRetry : R.string.ArticleReadMore; }

  @Override public boolean needViewGroup () { return true; }
  @Override protected boolean preferFullWidth () {
    return UI.isPortrait() && !UI.isTablet() && isChannel() && !isEventLog();
  }
  @Override protected int getContentWidth () { return width; }
  @Override protected int getContentHeight () { return height; }
  @Override protected void drawContent (MessageView view, Canvas canvas, int x, int y, int maxWidth) { }
  @Override protected void drawContent (MessageView view, Canvas canvas, int x, int y, int maxWidth, Receiver preview, Receiver receiver) { }

  @Override protected void buildContent (int maxWidth) {
    width = maxWidth;
    if (blocks == null) {
      if (displayArticle == null) displayArticle = ArticleCodec.copy(article, TdApi.RichMessage.class);
      blocks = PageBlock.parseArticle(controller(), displayArticle, this);
      ArrayList<PageBlockMedia> media = new ArrayList<>();
      for (PageBlock block : blocks) {
        block.setIsChatContent();
        if (block instanceof PageBlockMedia) ((PageBlockMedia) block).setArticleMessage(getMessage());
        if (block instanceof PageBlockFile) ((PageBlockFile) block).setArticleMessage(this);
        if (block instanceof PageBlockMedia && ((PageBlockMedia) block).bindToList(controller(), null, media)) media.add((PageBlockMedia) block);
      }
    }
    List<Row> measured = new ArrayList<>(blocks.size());
    boolean fullWidth = useFullWidth();
    int top = 0;
    for (PageBlock block : blocks) {
      // Channel articles use the whole message width. Text and files need only
      // small reading margins, not the ordinary message's avatar column.
      boolean fullWidthMedia = fullWidth && block instanceof PageBlockMedia && block.isIndependent();
      int left = fullWidth && !fullWidthMedia ? Screen.dp(16f) : 0;
      int right = left;
      int rowWidth = Math.max(1, maxWidth - left - right);
      int indent = 0;
      if (block.getListItem() != null) {
        indent = Screen.dp(18f);
        for (PageBlock.ListItemInfo item : block.getListItem()) indent += item.list.getIndent();
        indent = Math.min(indent, rowWidth / 2);
      }
      if (article.isRtl) right += indent; else left += indent;
      int rowHeight = block.getHeight(null, Math.max(1, rowWidth - indent));
      measured.add(new Row(block, top, rowHeight, indent, left, right));
      top += rowHeight;
    }
    rows = Collections.unmodifiableList(measured);
    // A media block must not consume a fixed preview budget and hide all text below it.
    // Child views are already virtualized, so the complete document can retain its height.
    bodyHeight = top;
    height = bodyHeight + (!article.isFull ? Screen.dp(48f) : 0);
    revision++;
  }

  public void rebuildArticle () {
    if (blocks != null) for (PageBlock block : blocks) if (block instanceof me.vkryl.core.lambda.Destroyable) ((me.vkryl.core.lambda.Destroyable) block).performDestroy();
    blocks = null;
    relayoutArticle();
  }
  public void relayoutArticle () {
    rebuildContent();
    invalidateContent(this);
    requestLayout();
  }

  public void expandArticle () {
    if (loading) return;
    if (article.isFull) return;
    final int generation = ++requestGeneration;
    final long messageId = getId();
    loading = true;
    loadFailed = false;
    rebuildArticle();
    tdlib.send(new TdApi.GetFullRichMessage(getChatId(), messageId), (full, error) -> executeOnUiThreadOptional(() -> {
      if (generation != requestGeneration || messageId != getId()) return;
      loading = false;
      if (error != null || full == null || !full.isFull) {
        loadFailed = true;
        if (error != null) UI.showError(error);
      } else {
        article = full;
        displayArticle = null;
      }
      rebuildArticle();
      if (article.isFull && pendingAnchor != null) {
        ArticleBodyView view = pendingAnchorView != null ? pendingAnchorView.get() : null;
        String anchor = pendingAnchor; pendingAnchor = null; pendingAnchorView = null;
        if (view != null) scrollToAnchor(view, anchor);
      }
    }));
  }

  @Override protected void onMessageAttachStateChange (boolean attached) {
    super.onMessageAttachStateChange(attached);
    // Full loading is part of reading a message, not an action hidden behind its last row.
    if (attached && !article.isFull && !loading && !loadFailed) {
      UI.post(() -> {
        if (isAttachedToView() && !article.isFull && !loading && !loadFailed) expandArticle();
      });
    }
  }

  public boolean scrollToAnchor (ArticleBodyView view, String anchor) {
    if (displayArticle != null && org.thunderdog.challegram.data.article.ArticleNavigation.revealAnchor(displayArticle, anchor)) {
      rebuildArticle();
      return view.scrollToLoadedAnchor(this, anchor);
    }
    if (!article.isFull) {
      pendingAnchor = anchor; pendingAnchorView = new java.lang.ref.WeakReference<>(view);
      expandArticle();
      return true;
    }
    return false;
  }

  public void toggleCheckbox (PageBlock.ListItemInfo item) {
    if (checkboxPending) return;
    if (!article.isFull) { expandArticle(); return; }
    final int[] position = {0}, target = {-1};
    TdApi.PageBlockListItem wanted = item.list.list.items[item.itemIndex];
    ArticleCodec.visit(displayArticle, (value, depth) -> {
      if (value instanceof TdApi.PageBlockListItem) { if (value == wanted) target[0] = position[0]; position[0]++; }
    });
    if (target[0] < 0) return;
    int generation = requestGeneration;
    checkboxPending = true;
    tdlib.send(new TdApi.GetMessageProperties(getChatId(), getId()), (properties, error) -> executeOnUiThreadOptional(() -> {
      if (generation != requestGeneration) { checkboxPending = false; return; }
      if (error != null || properties == null || !properties.canBeEdited) { checkboxPending = false; if (error != null) UI.showError(error); return; }
      org.thunderdog.challegram.data.article.ArticleDocument expected;
      try {
        expected = org.thunderdog.challegram.data.article.ArticleDocument.received(article);
      } catch (IllegalArgumentException e) { checkboxPending = false; UI.showToast(R.string.ArticleReadOnly, android.widget.Toast.LENGTH_SHORT); return; }
      tdlib.send(new TdApi.GetFullRichMessage(getChatId(), getId()), (full, loadError) -> executeOnUiThreadOptional(() -> {
        if (generation != requestGeneration) return;
        if (loadError != null) { checkboxPending = false; UI.showError(loadError); return; }
        org.thunderdog.challegram.data.article.ArticleDocument latest;
        try { latest = org.thunderdog.challegram.data.article.ArticleDocument.received(full); }
        catch (IllegalArgumentException e) { checkboxPending = false; UI.showToast(R.string.ArticleReadOnly, android.widget.Toast.LENGTH_SHORT); return; }
        if (!latest.equals(expected)) {
          checkboxPending = false; article = full; displayArticle = null; rebuildArticle();
          UI.showToast(R.string.ArticleEditConflict, android.widget.Toast.LENGTH_SHORT); return;
        }
        org.thunderdog.challegram.data.article.ArticleDocument document = org.thunderdog.challegram.data.article.ArticleCheckbox.toggle(latest, target[0]);
        wanted.isChecked = !wanted.isChecked;
        rebuildArticle();
        tdlib.send(new TdApi.EditMessageText(getChatId(), getId(), null, new TdApi.InputMessageRichMessage(document.toInput(), false)), (message, editError) -> executeOnUiThreadOptional(() -> {
          checkboxPending = false;
          if (generation != requestGeneration) return;
          if (editError != null) { displayArticle = null; rebuildArticle(); UI.showError(editError); }
          else if (message != null && message.content instanceof TdApi.MessageRichMessage) updateMessageContent(message, message.content, false);
        }));
      }));
    }));
  }

  @Override protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent content, boolean isBottomMessage) {
    if (!(content instanceof TdApi.MessageRichMessage)) return false;
    requestGeneration++;
    loading = loadFailed = checkboxPending = false;
    article = ((TdApi.MessageRichMessage) content).message;
    displayArticle = null;
    rebuildArticle();
    if (isAttachedToView() && !article.isFull) onMessageAttachStateChange(true);
    return true;
  }

  @Override protected void onMessageContainerDestroyed () {
    requestGeneration++;
    if (blocks != null) for (PageBlock block : blocks) if (block instanceof me.vkryl.core.lambda.Destroyable) ((me.vkryl.core.lambda.Destroyable) block).performDestroy();
    super.onMessageContainerDestroyed();
  }

  @Override public boolean onUrlClick (View view, String url, boolean isTextUrl, @NonNull TdlibUi.UrlOpenParameters parameters) {
    return clickCallback().onUrlClick(view, url, isTextUrl, parameters);
  }

  @Override public TdApi.LinkPreview findLinkPreview (String link) { return super.findLinkPreview(link); }
  @Override public boolean allowCopyText () { return canBeSaved(); }
  public TdApi.FormattedText getArticleText () {
    return article.isFull ? new TdApi.FormattedText(org.thunderdog.challegram.data.article.ArticleRichText.preview(article, Integer.MAX_VALUE), new TdApi.TextEntity[0]) : null;
  }
  @Override public boolean onUsernameClick (String username) { return clickCallback().onUsernameClick(username); }
  @Override public boolean onUserClick (long userId) { return clickCallback().onUserClick(userId); }
  @Override public boolean onHashtagClick (String tag) { return clickCallback().onHashtagClick(tag); }
  @Override public boolean onCommandClick (View view, Text text, org.thunderdog.challegram.util.text.TextPart part, String command, boolean longPress) { return clickCallback().onCommandClick(view, text, part, command, longPress); }

  @Override public boolean onAnchorClick (View view, String anchor) {
    ArticleBodyView body = ArticleBodyView.find(view);
    return body != null && body.scrollToAnchor(anchor);
  }

  @Override public boolean onReferenceClick (View view, String name, String anchor, @NonNull TdlibUi.UrlOpenParameters parameters) {
    final TdApi.RichText[] reference = new TdApi.RichText[1];
    ArticleCodec.visit(article, (value, depth) -> {
      if (value instanceof TdApi.RichTextReference && anchor.equals(((TdApi.RichTextReference) value).name)) reference[0] = ((TdApi.RichTextReference) value).text;
    });
    if (reference[0] == null || parameters.tooltip == null) return onAnchorClick(view, anchor);
    TextWrapper text = TextWrapper.parseRichText(controller(), this, reference[0], Paints.robotoStyleProvider(14f), parameters.tooltip.colorProvider(), parameters, null);
    parameters.tooltip.controller(controller()).show(text);
    return true;
  }

  @Override public boolean onButtonClick (View view, TdApi.InlineButton button, @NonNull TdlibUi.UrlOpenParameters parameters) {
    if (button.type instanceof TdApi.InlineKeyboardButtonTypeCopyText) {
      if (canBeSaved()) UI.copyText(((TdApi.InlineKeyboardButtonTypeCopyText) button.type).text, R.string.CopiedText);
      return true;
    }
    TGInlineKeyboard keyboard = new TGInlineKeyboard(this, false);
    TdApi.InlineKeyboardButton key = new TdApi.InlineKeyboardButton(TD.getText(button.text), 0, button.style, button.type);
    keyboard.set(getId(), new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][] {{key}}, false), Math.max(1, width), Math.max(1, width));
    return keyboard.clickFirstButton(view);
  }
}
