/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.ui.MessagesController;

/** Keep the launch source, peer and reply context together throughout asynchronous work. */
public final class MiniAppLaunchRequest {
  public enum Source { REPLY_KEYBOARD, INLINE_KEYBOARD, BOT_MENU, INLINE_RESULTS, MAIN_APP, DIRECT_LINK, ATTACHMENT_MENU, SIDE_MENU }

  public final Source source;
  public final long botUserId, chatId;
  public final String url, buttonText, shortName, startParameter;
  public final TdApi.MessageTopic topicId;
  public final TdApi.InputMessageReplyTo replyTo;
  public final TdApi.WebAppOpenMode mode;
  public final boolean forceConfirmation;

  public MiniAppLaunchRequest (Source source, long botUserId, long chatId, String url, String buttonText,
                              String shortName, String startParameter, TdApi.MessageTopic topicId,
                              TdApi.InputMessageReplyTo replyTo, TdApi.WebAppOpenMode mode, boolean forceConfirmation) {
    if (botUserId == 0) throw new IllegalArgumentException("Missing Mini App bot");
    this.source = source;
    this.botUserId = botUserId;
    this.chatId = chatId;
    this.url = url != null ? url : "";
    this.buttonText = buttonText != null ? buttonText : "";
    this.shortName = shortName != null ? shortName : "";
    this.startParameter = startParameter != null ? startParameter : "";
    this.topicId = topicId;
    this.replyTo = replyTo;
    this.mode = mode != null ? mode : new TdApi.WebAppOpenModeCompact();
    this.forceConfirmation = forceConfirmation;
  }

  public static MiniAppLaunchRequest fromChat (MessagesController controller, Source source, long botUserId, String url, String buttonText) {
    MessagesController.ReplyInfo reply = controller.getCurrentReplyId();
    return new MiniAppLaunchRequest(source, botUserId, controller.getChatId(), url, buttonText, "", "",
      controller.getMessageTopicId(reply), reply != null ? reply.toInputMessageReply() : null, null, false);
  }

  public TdApi.Function<?> function (TdApi.ThemeParameters theme, boolean allowWriteAccess) {
    TdApi.WebAppOpenParameters parameters = new TdApi.WebAppOpenParameters(theme, "android", mode);
    switch (source) {
      case REPLY_KEYBOARD:
      case INLINE_RESULTS:
      case SIDE_MENU:
        return new TdApi.GetWebAppUrl(botUserId, url, parameters);
      case MAIN_APP:
        return new TdApi.GetMainWebApp(chatId, botUserId, startParameter, parameters);
      case DIRECT_LINK:
        return new TdApi.GetWebAppLinkUrl(chatId, botUserId, shortName, startParameter, allowWriteAccess, parameters);
      case INLINE_KEYBOARD:
      case BOT_MENU:
      case ATTACHMENT_MENU:
        return new TdApi.OpenWebApp(chatId, botUserId, url, topicId, replyTo, parameters);
      default:
        throw new IllegalStateException();
    }
  }
}
