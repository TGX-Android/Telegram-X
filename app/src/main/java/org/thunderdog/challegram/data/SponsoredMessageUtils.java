/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

public class SponsoredMessageUtils {
  public static final String TELEGRAM_AD_TYPE = "telegram_adx";

  public static TGMessage sponsoredToTgx (MessagesManager manager, long inChatId, TdApi.SponsoredMessage sMsg) {
    return new TGMessageText(manager, inChatId, sMsg);
  }

  public static TdApi.Message sponsoredToTd (long chatId, TdApi.SponsoredMessage sponsoredMessage, Tdlib tdlib) {
    TdApi.MessageText fMsgContent = (TdApi.MessageText) sponsoredMessage.content;
    fMsgContent.webPage = new TdApi.WebPage();
    fMsgContent.webPage.type = TELEGRAM_AD_TYPE;
    fMsgContent.webPage.url = "";

    TdApi.Message fMsg = new TdApi.Message();
    fMsg.senderId = null;
    fMsg.content = fMsgContent;
    fMsg.authorSignature = Lang.getString(sponsoredMessage.isRecommended ? R.string.RecommendedSign : R.string.SponsoredSign);
    fMsg.id = sponsoredMessage.messageId;
    fMsg.isOutgoing = false;
    fMsg.canBeSaved = true;
    fMsg.chatId = chatId;
    fMsg.isChannelPost = tdlib.isChannel(chatId);

    return fMsg;
  }
}
