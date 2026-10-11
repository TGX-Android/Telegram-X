/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;

/** The inline result describes discovery; only TDLib's cached message is used for the preview. */
public final class MiniAppPreparedMessagePolicy {
  private MiniAppPreparedMessagePolicy () { }

  public static String resultId (TdApi.InlineQueryResult result) {
    if (result instanceof TdApi.InlineQueryResultAnimation) return ((TdApi.InlineQueryResultAnimation) result).id;
    if (result instanceof TdApi.InlineQueryResultArticle) return ((TdApi.InlineQueryResultArticle) result).id;
    if (result instanceof TdApi.InlineQueryResultAudio) return ((TdApi.InlineQueryResultAudio) result).id;
    if (result instanceof TdApi.InlineQueryResultContact) return ((TdApi.InlineQueryResultContact) result).id;
    if (result instanceof TdApi.InlineQueryResultDocument) return ((TdApi.InlineQueryResultDocument) result).id;
    if (result instanceof TdApi.InlineQueryResultGame) return ((TdApi.InlineQueryResultGame) result).id;
    if (result instanceof TdApi.InlineQueryResultLocation) return ((TdApi.InlineQueryResultLocation) result).id;
    if (result instanceof TdApi.InlineQueryResultPhoto) return ((TdApi.InlineQueryResultPhoto) result).id;
    if (result instanceof TdApi.InlineQueryResultSticker) return ((TdApi.InlineQueryResultSticker) result).id;
    if (result instanceof TdApi.InlineQueryResultVenue) return ((TdApi.InlineQueryResultVenue) result).id;
    if (result instanceof TdApi.InlineQueryResultVideo) return ((TdApi.InlineQueryResultVideo) result).id;
    if (result instanceof TdApi.InlineQueryResultVoiceNote) return ((TdApi.InlineQueryResultVoiceNote) result).id;
    return null;
  }

  public static boolean validId (Object id) {
    return id instanceof String && !((String) id).isEmpty() && ((String) id).length() <= 1024;
  }

  /** A launch reply belongs only to its original chat and topic, never another recipient. */
  public static TdApi.InputMessageReplyTo replyTo (long launchChatId, TdApi.MessageTopic launchTopic,
                                                  TdApi.InputMessageReplyTo reply, long chatId, TdApi.MessageTopic topic) {
    return launchChatId == chatId && tgx.td.Td.equalsTo(launchTopic, topic) ? reply : null;
  }
}
