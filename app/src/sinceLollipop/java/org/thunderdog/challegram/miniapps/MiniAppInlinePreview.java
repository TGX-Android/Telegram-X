/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;

/** Uses TDLib's regular sending API in preview mode; no message is submitted. */
final class MiniAppInlinePreview {
  final TdApi.MessageContent content;
  final TdApi.ReplyMarkup replyMarkup;

  MiniAppInlinePreview (TdApi.MessageContent content, TdApi.ReplyMarkup replyMarkup) {
    this.content = content; this.replyMarkup = replyMarkup;
  }

  static TdApi.SendInlineQueryResultMessage request (long chatId, long queryId, String resultId) {
    TdApi.MessageSendOptions options = new TdApi.MessageSendOptions();
    options.onlyPreview = true;
    return new TdApi.SendInlineQueryResultMessage(chatId, null, null, options, queryId, resultId, false);
  }

  static MiniAppInlinePreview parse (TdApi.Object result) {
    if (!(result instanceof TdApi.Message)) return null;
    TdApi.Message message = (TdApi.Message) result;
    return new MiniAppInlinePreview(message.content, message.replyMarkup);
  }
}
