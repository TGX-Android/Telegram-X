/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;

/** Compatibility boundary until the optional TDLib preview API is available in the baseline bundle. */
final class MiniAppInlinePreview {
  final TdApi.MessageContent content;
  final TdApi.ReplyMarkup replyMarkup;

  MiniAppInlinePreview (TdApi.MessageContent content, TdApi.ReplyMarkup replyMarkup) {
    this.content = content; this.replyMarkup = replyMarkup;
  }

  static TdApi.Function<?> request (long queryId, String resultId) {
    try {
      return (TdApi.Function<?>) Class.forName("org.drinkless.tdlib.TdApi$GetInlineQueryResultMessage")
        .getConstructor(long.class, String.class).newInstance(queryId, resultId);
    } catch (ReflectiveOperationException e) { return null; }
  }

  static MiniAppInlinePreview parse (TdApi.Object result) {
    if (!result.getClass().getName().equals("org.drinkless.tdlib.TdApi$InlineQueryResultMessage")) return null;
    try {
      return new MiniAppInlinePreview((TdApi.MessageContent) result.getClass().getField("content").get(result),
        (TdApi.ReplyMarkup) result.getClass().getField("replyMarkup").get(result));
    } catch (ReflectiveOperationException e) { return null; }
  }
}
