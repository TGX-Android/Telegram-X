/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
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

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.core.MathUtils;

public class SponsoredMessageUtils {
  public static final String TELEGRAM_AD_TYPE = "telegram_adx";

  public static TGMessage sponsoredToTgx (MessagesManager manager, long chatId, int date, TdApi.SponsoredMessage sMsg) {
    return new TGMessageText(manager, sponsoredToTd(chatId, date, sMsg, manager.controller().tdlib()), sMsg);
  }

  private static TdApi.Message sponsoredToTd (long chatId, int date, TdApi.SponsoredMessage sMsg, Tdlib tdlib) {
    TdApi.MessageText fMsgContent = (TdApi.MessageText) sMsg.content;
    fMsgContent.webPage = new TdApi.WebPage();
    fMsgContent.webPage.type = TELEGRAM_AD_TYPE;
    fMsgContent.webPage.url = "";

    TdApi.Message fMsg = new TdApi.Message();
    fMsg.senderId = tdlib.sender(sMsg.sponsorChatId);
    fMsg.content = fMsgContent;
    fMsg.authorSignature = Lang.getString(R.string.SponsoredSign);
    fMsg.id = sMsg.messageId;
    fMsg.date = date;
    fMsg.isOutgoing = false;
    fMsg.canBeSaved = true;
    fMsg.chatId = chatId;
    fMsg.isChannelPost = tdlib.isChannel(chatId);

    return fMsg;
  }

  public static TdApi.SponsoredMessages generateSponsoredMessages (Tdlib tdlib) {
    return generateUserSponsoredMessages(tdlib);
  }

  private static TdApi.SponsoredMessages generateUserSponsoredMessages (Tdlib tdlib) {
    TdApi.SponsoredMessage msg = new TdApi.SponsoredMessage();
    msg.sponsorChatId = tdlib.myUserId();
    msg.messageId = 1;
    msg.content = new TdApi.MessageText(new TdApi.FormattedText("Test ad message (from user/channel)", null), null);
    return new TdApi.SponsoredMessages(new TdApi.SponsoredMessage[] {msg}, 0);
  }
}
