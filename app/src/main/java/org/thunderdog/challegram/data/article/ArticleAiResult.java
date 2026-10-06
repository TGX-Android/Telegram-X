/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;

/** Accepts AI corrections without putting the response-only RichTextDiff into an outgoing document. */
public final class ArticleAiResult {
  private ArticleAiResult () { }

  public static ArticleDocument toDocument (TdApi.RichMessage result) {
    if (result == null || !result.isFull) throw new IllegalArgumentException("Incomplete AI article");
    // Keep the server response intact. The codec also bounds depth, size and cycles before traversal.
    TdApi.RichMessage accepted = ArticleCodec.copy(result, TdApi.RichMessage.class);
    ArticleCodec.transformRichTexts(accepted, text -> {
      while (text instanceof TdApi.RichTextDiff) {
        text = ((TdApi.RichTextDiff) text).text;
        if (text == null) throw new IllegalArgumentException("Missing AI replacement text");
      }
      // An empty replacement is a deletion. oldText must never be substituted back into the result.
      return text;
    });
    // Keep the usual strict checks for icons, bot-only actions and unsupported blocks.
    return ArticleDocument.received(accepted);
  }
}
