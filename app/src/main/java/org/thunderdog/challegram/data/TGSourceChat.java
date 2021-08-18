/**
 * File created on 20/02/16 at 18:51
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.view.View;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;

import me.vkryl.core.StringUtils;
import me.vkryl.td.MessageId;

public class TGSourceChat extends TGSource implements Client.ResultHandler, Runnable {
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
        msg.tdlib().client().send(new TdApi.GetChat(chatId), this);
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
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Chat.CONSTRUCTOR: {
        setChat(msg.tdlib().chat(((TdApi.Chat) object).id));
        Background.instance().post(() -> {
          msg.rebuildForward();
          msg.postInvalidate();
        });
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        this.title = Lang.getString(R.string.ChannelPrivate);
        this.isReady = true;
        this.photo = null;
        Background.instance().post(() -> {
          msg.rebuildForward();
          msg.postInvalidate();
        });
        break;
      }
      default: {
        Log.unexpectedTdlibResponse(object, TdApi.GetChat.class, TdApi.Chat.class);
        break;
      }
    }
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
  public ImageFile getAvatar () {
    return photo != null ? TD.getAvatar(msg.tdlib, photo) : null;
  }

  @Override
  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    return msg.tdlib().chatPlaceholderMetadata(chatId, false);
  }
}
