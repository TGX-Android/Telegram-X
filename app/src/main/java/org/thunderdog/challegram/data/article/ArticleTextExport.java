/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.util.ArrayList;

/** A display projection for the media viewer. Never use this projection to save an article. */
public final class ArticleTextExport {
  private final StringBuilder text = new StringBuilder();
  private final ArrayList<TdApi.TextEntity> entities = new ArrayList<>();
  private ArticleTextExport () { }

  public static TdApi.FormattedText caption (TdApi.PageBlockCaption caption) {
    if (caption == null) return null;
    ArticleTextExport out = new ArticleTextExport();
    out.append(caption.text, 0);
    if (!ArticleRichText.plain(caption.credit).isEmpty()) {
      if (out.text.length() != 0) out.text.append('\n');
      out.append(caption.credit, 0);
    }
    out.entities.sort((a, b) -> a.offset != b.offset ? Integer.compare(a.offset, b.offset) : Integer.compare(b.length, a.length));
    return new TdApi.FormattedText(out.text.toString(), out.entities.toArray(new TdApi.TextEntity[0]));
  }
  private void append (TdApi.RichText node, int depth) {
    if (node == null || depth > 128) return;
    int start = text.length(); TdApi.TextEntityType type = null;
    if (node instanceof TdApi.RichTextPlain) text.append(((TdApi.RichTextPlain) node).text);
    else if (node instanceof TdApi.RichTexts) for (TdApi.RichText item : ((TdApi.RichTexts) node).texts) append(item, depth + 1);
    else if (node instanceof TdApi.RichTextAnchor || node instanceof TdApi.RichTextReference) return;
    else if (node instanceof TdApi.RichTextMathematicalExpression) text.append(((TdApi.RichTextMathematicalExpression) node).expression);
    else if (node instanceof TdApi.RichTextCustomEmoji) {
      TdApi.RichTextCustomEmoji emoji = (TdApi.RichTextCustomEmoji) node;
      text.append(emoji.alternativeText); type = new TdApi.TextEntityTypeCustomEmoji(emoji.customEmojiId);
    } else {
      if (node instanceof TdApi.RichTextDiff) append(new TdApi.RichTextStrikethrough(((TdApi.RichTextDiff) node).oldText), depth + 1);
      append(ArticleRichText.child(node), depth + 1);
      if (node instanceof TdApi.RichTextBold) type = new TdApi.TextEntityTypeBold();
      else if (node instanceof TdApi.RichTextItalic) type = new TdApi.TextEntityTypeItalic();
      else if (node instanceof TdApi.RichTextUnderline) type = new TdApi.TextEntityTypeUnderline();
      else if (node instanceof TdApi.RichTextStrikethrough) type = new TdApi.TextEntityTypeStrikethrough();
      else if (node instanceof TdApi.RichTextSpoiler) type = new TdApi.TextEntityTypeSpoiler();
      else if (node instanceof TdApi.RichTextFixed) type = new TdApi.TextEntityTypeCode();
      else if (node instanceof TdApi.RichTextUrl) type = new TdApi.TextEntityTypeTextUrl(((TdApi.RichTextUrl) node).url);
      else if (node instanceof TdApi.RichTextEmailAddress) type = new TdApi.TextEntityTypeTextUrl("mailto:" + ((TdApi.RichTextEmailAddress) node).emailAddress);
      else if (node instanceof TdApi.RichTextPhoneNumber) type = new TdApi.TextEntityTypeTextUrl("tel:" + ((TdApi.RichTextPhoneNumber) node).phoneNumber);
      else if (node instanceof TdApi.RichTextMention) type = new TdApi.TextEntityTypeTextUrl("https://t.me/" + ((TdApi.RichTextMention) node).username);
      else if (node instanceof TdApi.RichTextMentionName) type = new TdApi.TextEntityTypeMentionName(((TdApi.RichTextMentionName) node).userId);
      else if (node instanceof TdApi.RichTextHashtag) type = new TdApi.TextEntityTypeHashtag();
      else if (node instanceof TdApi.RichTextCashtag) type = new TdApi.TextEntityTypeCashtag();
      else if (node instanceof TdApi.RichTextBotCommand) type = new TdApi.TextEntityTypeBotCommand();
      else if (node instanceof TdApi.RichTextBankCardNumber) type = new TdApi.TextEntityTypeBankCardNumber();
      else if (node instanceof TdApi.RichTextDateTime) { TdApi.RichTextDateTime date = (TdApi.RichTextDateTime) node; type = new TdApi.TextEntityTypeDateTime(date.unixTime, date.formattingType); }
      else if (node instanceof TdApi.RichTextAnchorLink && !((TdApi.RichTextAnchorLink) node).url.isEmpty()) type = new TdApi.TextEntityTypeTextUrl(((TdApi.RichTextAnchorLink) node).url);
      else if (node instanceof TdApi.RichTextReferenceLink && !((TdApi.RichTextReferenceLink) node).url.isEmpty()) type = new TdApi.TextEntityTypeTextUrl(((TdApi.RichTextReferenceLink) node).url);
      else if (node instanceof TdApi.RichTextButton) {
        TdApi.InlineKeyboardButtonType button = ((TdApi.RichTextButton) node).button.type;
        if (button instanceof TdApi.InlineKeyboardButtonTypeUrl) type = new TdApi.TextEntityTypeTextUrl(((TdApi.InlineKeyboardButtonTypeUrl) button).url);
        else if (button instanceof TdApi.InlineKeyboardButtonTypeUser) type = new TdApi.TextEntityTypeMentionName(((TdApi.InlineKeyboardButtonTypeUser) button).userId);
      }
    }
    if (type != null && text.length() > start) entities.add(new TdApi.TextEntity(start, text.length() - start, type));
  }
}
