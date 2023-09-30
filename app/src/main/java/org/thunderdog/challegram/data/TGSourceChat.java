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
 *
 * File created on 20/02/2016 at 18:51
 */
package org.thunderdog.challegram.data;

import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;

import me.vkryl.core.StringUtils;
import me.vkryl.td.MessageId;

public class TGSourceChat extends TGSource implements Runnable {
  private final long chatId;
  private final String authorSignature;
  private final long messageId;

  private String title;
  private TdApi.ChatPhotoInfo photo;

  public TGSourceChat (TGMessage msg, TdApi.MessageForwardOriginChannel channel) {
    super(msg);
    this.chatId = channel.chatId;
    this.authorSignature = channel.authorSignature;
    this.messageId = channel.messageId;
  }

  public TGSourceChat (TGMessage msg, TdApi.MessageForwardOriginChat chat) {
    super(msg);
    this.chatId = chat.senderChatId;
    this.authorSignature = chat.authorSignature;
    this.messageId = 0;
  }

  @Override
  public void load () {
    if (chatId != 0) {
      TdApi.Chat chat = msg.tdlib().chat(chatId);
      if (chat != null) {
        setChat(chat);
      } else {
        msg.tdlib().send(new TdApi.GetChat(chatId), (remoteChat, error) -> {
          if (error != null) {
            this.title = Lang.getString(R.string.ChannelPrivate);
            this.isReady = true;
            this.photo = null;
            Background.instance().post(() -> {
              msg.rebuildForward();
              msg.postInvalidate();
            });
          } else {
            setChat(msg.tdlib().chat(remoteChat.id));
            Background.instance().post(() -> {
              msg.rebuildForward();
              msg.postInvalidate();
            });
          }
        });
      }
    }
  }

  private void setChat (TdApi.Chat chat) {
    if (!StringUtils.isEmpty(authorSignature) && !(msg.forceForwardedInfo())) {
      this.title = Lang.getString(R.string.format_channelAndSignature, chat.title, authorSignature);
    } else {
      this.title = chat.title;
    }
    this.photo = chat.photo;
    this.isReady = true;
  }

  @Override
  public void run () {
    msg.rebuildForward();
    msg.postInvalidate();
  }

  @Override
  public boolean open (View view, Text text, TextPart part, TdlibUi.UrlOpenParameters openParameters, Receiver receiver) {
    if (chatId != 0) {
      if (messageId != 0) {
        msg.tdlib().ui().openMessage(msg.controller(), chatId, new MessageId(chatId, messageId), openParameters);
      } else {
        msg.tdlib().ui().openChat(msg.controller(), chatId, openParameters != null ? new TdlibUi.ChatOpenParameters().urlOpenParameters(openParameters) : null);
      }
      return true;
    }
    return false;
  }

  @Override
  public void destroy () {
    // TODO maybe add channel listener?
  }

  @Override
  public String getAuthorName () {
    return title == null ? Lang.getString(R.string.LoadingChannel) : title;
  }

  @Override
  public int getAuthorNameColorId () {
    return TD.getNameColorId(msg.tdlib.chatAvatarColorId(chatId));
  }

  @Override
  public void requestAvatar (AvatarReceiver receiver) {
    if (msg.tdlib.isSelfChat(chatId)) {
      receiver.requestUser(msg.tdlib, msg.tdlib.chatUserId(chatId), AvatarReceiver.Options.NONE);
    } else {
      receiver.requestChat(msg.tdlib, chatId, AvatarReceiver.Options.NONE);
    }
  }
}
