/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;

/** Typed access to the editable child of rich-text wrappers. Metadata is never reconstructed from labels. */
public final class ArticleRichText {
  private ArticleRichText () { }
  public static TdApi.RichText child (TdApi.RichText value) {
    switch (value.getConstructor()) {
      case TdApi.RichTextBold.CONSTRUCTOR: return ((TdApi.RichTextBold) value).text;
      case TdApi.RichTextItalic.CONSTRUCTOR: return ((TdApi.RichTextItalic) value).text;
      case TdApi.RichTextUnderline.CONSTRUCTOR: return ((TdApi.RichTextUnderline) value).text;
      case TdApi.RichTextStrikethrough.CONSTRUCTOR: return ((TdApi.RichTextStrikethrough) value).text;
      case TdApi.RichTextFixed.CONSTRUCTOR: return ((TdApi.RichTextFixed) value).text;
      case TdApi.RichTextSpoiler.CONSTRUCTOR: return ((TdApi.RichTextSpoiler) value).text;
      case TdApi.RichTextSubscript.CONSTRUCTOR: return ((TdApi.RichTextSubscript) value).text;
      case TdApi.RichTextSuperscript.CONSTRUCTOR: return ((TdApi.RichTextSuperscript) value).text;
      case TdApi.RichTextMarked.CONSTRUCTOR: return ((TdApi.RichTextMarked) value).text;
      case TdApi.RichTextButton.CONSTRUCTOR: return ((TdApi.RichTextButton) value).button.text;
      case TdApi.RichTextDateTime.CONSTRUCTOR: return ((TdApi.RichTextDateTime) value).text;
      case TdApi.RichTextMention.CONSTRUCTOR: return ((TdApi.RichTextMention) value).text;
      case TdApi.RichTextMentionName.CONSTRUCTOR: return ((TdApi.RichTextMentionName) value).text;
      case TdApi.RichTextHashtag.CONSTRUCTOR: return ((TdApi.RichTextHashtag) value).text;
      case TdApi.RichTextCashtag.CONSTRUCTOR: return ((TdApi.RichTextCashtag) value).text;
      case TdApi.RichTextBankCardNumber.CONSTRUCTOR: return ((TdApi.RichTextBankCardNumber) value).text;
      case TdApi.RichTextBotCommand.CONSTRUCTOR: return ((TdApi.RichTextBotCommand) value).text;
      case TdApi.RichTextUrl.CONSTRUCTOR: return ((TdApi.RichTextUrl) value).text;
      case TdApi.RichTextEmailAddress.CONSTRUCTOR: return ((TdApi.RichTextEmailAddress) value).text;
      case TdApi.RichTextPhoneNumber.CONSTRUCTOR: return ((TdApi.RichTextPhoneNumber) value).text;
      case TdApi.RichTextReference.CONSTRUCTOR: return ((TdApi.RichTextReference) value).text;
      case TdApi.RichTextReferenceLink.CONSTRUCTOR: return ((TdApi.RichTextReferenceLink) value).text;
      case TdApi.RichTextAnchorLink.CONSTRUCTOR: return ((TdApi.RichTextAnchorLink) value).text;
      case TdApi.RichTextDiff.CONSTRUCTOR: return ((TdApi.RichTextDiff) value).text;
      default: return null;
    }
  }

  public static TdApi.RichText withChild (TdApi.RichText original, TdApi.RichText child) {
    TdApi.RichText value = ArticleCodec.copy(original, TdApi.RichText.class);
    switch (value.getConstructor()) {
      case TdApi.RichTextBold.CONSTRUCTOR: ((TdApi.RichTextBold) value).text = child; break;
      case TdApi.RichTextItalic.CONSTRUCTOR: ((TdApi.RichTextItalic) value).text = child; break;
      case TdApi.RichTextUnderline.CONSTRUCTOR: ((TdApi.RichTextUnderline) value).text = child; break;
      case TdApi.RichTextStrikethrough.CONSTRUCTOR: ((TdApi.RichTextStrikethrough) value).text = child; break;
      case TdApi.RichTextFixed.CONSTRUCTOR: ((TdApi.RichTextFixed) value).text = child; break;
      case TdApi.RichTextSpoiler.CONSTRUCTOR: ((TdApi.RichTextSpoiler) value).text = child; break;
      case TdApi.RichTextSubscript.CONSTRUCTOR: ((TdApi.RichTextSubscript) value).text = child; break;
      case TdApi.RichTextSuperscript.CONSTRUCTOR: ((TdApi.RichTextSuperscript) value).text = child; break;
      case TdApi.RichTextMarked.CONSTRUCTOR: ((TdApi.RichTextMarked) value).text = child; break;
      case TdApi.RichTextButton.CONSTRUCTOR: ((TdApi.RichTextButton) value).button.text = child; break;
      case TdApi.RichTextDateTime.CONSTRUCTOR: ((TdApi.RichTextDateTime) value).text = child; break;
      case TdApi.RichTextMention.CONSTRUCTOR: ((TdApi.RichTextMention) value).text = child; break;
      case TdApi.RichTextMentionName.CONSTRUCTOR: ((TdApi.RichTextMentionName) value).text = child; break;
      case TdApi.RichTextHashtag.CONSTRUCTOR: ((TdApi.RichTextHashtag) value).text = child; break;
      case TdApi.RichTextCashtag.CONSTRUCTOR: ((TdApi.RichTextCashtag) value).text = child; break;
      case TdApi.RichTextBankCardNumber.CONSTRUCTOR: ((TdApi.RichTextBankCardNumber) value).text = child; break;
      case TdApi.RichTextBotCommand.CONSTRUCTOR: ((TdApi.RichTextBotCommand) value).text = child; break;
      case TdApi.RichTextUrl.CONSTRUCTOR: ((TdApi.RichTextUrl) value).text = child; break;
      case TdApi.RichTextEmailAddress.CONSTRUCTOR: ((TdApi.RichTextEmailAddress) value).text = child; break;
      case TdApi.RichTextPhoneNumber.CONSTRUCTOR: ((TdApi.RichTextPhoneNumber) value).text = child; break;
      case TdApi.RichTextReference.CONSTRUCTOR: ((TdApi.RichTextReference) value).text = child; break;
      case TdApi.RichTextReferenceLink.CONSTRUCTOR: ((TdApi.RichTextReferenceLink) value).text = child; break;
      case TdApi.RichTextAnchorLink.CONSTRUCTOR: ((TdApi.RichTextAnchorLink) value).text = child; break;
      case TdApi.RichTextDiff.CONSTRUCTOR: ((TdApi.RichTextDiff) value).text = child; break;
      default: throw new IllegalArgumentException("Not a rich-text wrapper");
    }
    return value;
  }

  public static String plain (TdApi.RichText text) {
    if (text == null) return "";
    if (text instanceof TdApi.RichTextPlain) return ((TdApi.RichTextPlain) text).text;
    if (text instanceof TdApi.RichTextCustomEmoji) return ((TdApi.RichTextCustomEmoji) text).alternativeText;
    if (text instanceof TdApi.RichTextMathematicalExpression) return ((TdApi.RichTextMathematicalExpression) text).expression;
    if (text instanceof TdApi.RichTextButton) return plain(((TdApi.RichTextButton) text).button.text);
    if (text instanceof TdApi.RichTexts) {
      StringBuilder out = new StringBuilder();
      for (TdApi.RichText item : ((TdApi.RichTexts) text).texts) out.append(plain(item));
      return out.toString();
    }
    TdApi.RichText child = child(text);
    return child != null ? plain(child) : "";
  }

  public static String preview (TdApi.RichMessage article, int limit) {
    StringBuilder out = new StringBuilder();
    final int[] richDepth = {-1};
    final boolean[] truncated = {false};
    ArticleCodec.visit(article, (value, depth) -> {
      if (depth <= richDepth[0]) richDepth[0] = -1;
      String text = null;
      if (value instanceof TdApi.RichText && richDepth[0] == -1) {
        richDepth[0] = depth; text = plain((TdApi.RichText) value);
      } else if (value instanceof TdApi.PageBlockMathematicalExpression) {
        text = ((TdApi.PageBlockMathematicalExpression) value).expression;
      }
      if (!truncated[0] && text != null && !text.isEmpty() && out.length() < limit) {
        int separator = out.length() > 0 ? 1 : 0;
        int end = Math.min(text.length(), Math.max(0, limit - out.length() - separator));
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        if (end > 0) { if (separator != 0) out.append('\n'); out.append(text, 0, end); }
        truncated[0] = end < text.length();
      }
    });
    return out.toString();
  }
}
