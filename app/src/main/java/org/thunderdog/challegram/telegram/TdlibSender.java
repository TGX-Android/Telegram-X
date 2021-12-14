package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.core.StringUtils;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.td.ChatId;

public class TdlibSender {
  private static final int FLAG_BOT = 1;
  private static final int FLAG_SERVICE_ACCOUNT = 1 << 1;

  private final Tdlib tdlib;
  private final long inChatId;
  private final TdApi.MessageSender sender;

  private final String name, nameShort, username;
  private final TdApi.ChatPhotoInfo photo;
  private final Letters letters;
  private final AvatarPlaceholder.Metadata placeholderMetadata;
  private final int flags;

  public TdlibSender (Tdlib tdlib, long inChatId, TdApi.MessageSender sender) {
    this(tdlib, inChatId, sender, null, false);
  }

  public TdlibSender (Tdlib tdlib, long inChatId, TdApi.MessageSender sender, @Nullable MessagesManager manager, boolean isDemo) {
    this.tdlib = tdlib;
    this.inChatId = inChatId;
    this.sender = sender;

    int flags = 0;
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        final long chatId = ((TdApi.MessageSenderChat) sender).chatId;
        TdApi.Chat chat = tdlib.chat(chatId);

        this.name = tdlib.chatTitle(chat, false);
        this.nameShort = tdlib.chatTitle(chat, false, true);
        this.username = tdlib.chatUsername(chat);
        this.photo = chat != null ? chat.photo : null;
        this.letters = tdlib.chatLetters(chat);
        this.placeholderMetadata = tdlib.chatPlaceholderMetadata(chatId, chat, false);

        flags = BitwiseUtils.setFlag(flags, FLAG_BOT, tdlib.isBotChat(chat));
        flags = BitwiseUtils.setFlag(flags, FLAG_SERVICE_ACCOUNT, tdlib.isServiceNotificationsChat(chatId));

        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        final long userId = ((TdApi.MessageSenderUser) sender).userId;
        TdApi.User user = isDemo && manager != null ? manager.demoParticipant(userId) : tdlib.cache().user(userId);
        TdApi.ProfilePhoto profilePhoto = user != null ? user.profilePhoto : null;

        this.name = TD.getUserName(userId, user);
        this.nameShort = TD.getUserSingleName(userId, user);
        this.username = user != null && !StringUtils.isEmpty(user.username) ? user.username : null;
        this.photo = profilePhoto != null ? new TdApi.ChatPhotoInfo(profilePhoto.small, profilePhoto.big, profilePhoto.minithumbnail, profilePhoto.hasAnimation) : null;
        this.letters = TD.getLetters(user);
        this.placeholderMetadata = tdlib.cache().userPlaceholderMetadata(userId, user, false);

        flags = BitwiseUtils.setFlag(flags, FLAG_BOT, TD.isBot(user));
        flags = BitwiseUtils.setFlag(flags, FLAG_SERVICE_ACCOUNT, tdlib.isServiceNotificationsChat(ChatId.fromUserId(userId)));

        break;
      }
      default: {
        throw new UnsupportedOperationException(sender.toString());
      }
    }
    this.flags = flags;
  }

  public boolean isUser () {
    return sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR;
  }

  public boolean isChat () {
    return sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR;
  }

  public boolean isAnonymousGroupAdmin () {
    return isChat() && inChatId == getChatId() && !tdlib.isChannel(getChatId());
  }

  public boolean isSelf () {
    return getUserId() == tdlib.myUserId();
  }

  public boolean isChannel () {
    return isChat() && tdlib.isChannel(getChatId());
  }

  public long getUserId () {
    return isUser() ? ((TdApi.MessageSenderUser) sender).userId : 0;
  }

  public long getChatId () {
    return isChat() ? ((TdApi.MessageSenderChat) sender).chatId : 0;
  }

  public String getName () {
    return name;
  }

  public String getNameShort () {
    return nameShort;
  }

  public String getUsername () {
    return username;
  }

  public TdApi.ChatPhotoInfo getPhoto () {
    return photo;
  }

  public Letters getLetters () {
    return letters;
  }

  public AvatarPlaceholder.Metadata getPlaceholderMetadata () {
    return placeholderMetadata;
  }

  public int getAvatarColorId () {
    return placeholderMetadata.colorId;
  }

  public int getNameColorId () {
    return TD.getNameColorId(getAvatarColorId());
  }

  public ImageFile getAvatar () {
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR:
        return tdlib.chatAvatar(((TdApi.MessageSenderChat) sender).chatId);
      case TdApi.MessageSenderUser.CONSTRUCTOR:
        return tdlib.cache().userAvatar(((TdApi.MessageSenderUser) sender).userId);
    }
    throw new AssertionError();
  }

  // flags

  public boolean isBot () {
    return BitwiseUtils.getFlag(flags, FLAG_BOT);
  }

  public boolean isServiceAccount () {
    return BitwiseUtils.getFlag(flags, FLAG_SERVICE_ACCOUNT);
  }
}
