package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class TGMessageSender {

  private final Tdlib tdlib;

  private TdApi.MessageSender messageSender;

  private boolean isSelected;

  private String title;

  private String subtitle;

  private ImageFile avatar;
  private AvatarPlaceholder.Metadata avatarPlaceholderMetadata;

  private TdApi.Chat chat;

  private TdApi.User user;

  private boolean needPremium;
  private boolean isAnonymousAdmin;

  private boolean isPersonal;

  public TGMessageSender (Tdlib tdlib, TdApi.MessageSender messageSender, boolean needPremium) {
    this.tdlib = tdlib;
    this.messageSender = messageSender;
    this.needPremium = needPremium;
    init();
  }

  public void setMessageSender (TdApi.MessageSender messageSender, boolean needPremium) {
    this.messageSender = messageSender;
    this.needPremium = needPremium;
    init();
  }

  public void setUser(TdApi.User user) {
    this.user = user;
    this.avatarPlaceholderMetadata = tdlib.cache().userPlaceholderMetadata(user, true);
    setPhoto(user.profilePhoto != null ? user.profilePhoto.small : null);
  }

  public void setChat(TdApi.Chat chat) {
    this.chat = chat;
    this.avatarPlaceholderMetadata = tdlib.chatPlaceholderMetadata(chat, true);
    setPhoto(chat.photo != null ? chat.photo.small : null);
  }

  public void setPhoto (TdApi.File photo) {
    if (photo != null) {
      this.avatar = new ImageFile(tdlib, photo);
      this.avatar.setSize(ChatView.getDefaultAvatarCacheSize());
    } else {
      this.avatar = null;
    }
  }

  public void setSelected (boolean selected) {
    this.isSelected = selected;
  }

  public boolean isSelected () {
    return isSelected;
  }

  public String getTitle () {
    return title;
  }

  public String getSubtitle () {
    return subtitle;
  }

  public boolean isAnonymousAdmin() {
    return isAnonymousAdmin;
  }

  public boolean isPersonal () {
    return isPersonal;
  }

  private void init () {
    if (messageSender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
      TdApi.MessageSenderChat senderChat = (TdApi.MessageSenderChat) messageSender;
      TdApi.Chat sendAsChat = tdlib.chat(senderChat.chatId);
      if (sendAsChat == null) return;

      this.title = sendAsChat.title;
      if (tdlib.isMultiChat(sendAsChat) && Td.isAnonymous(tdlib.chatStatus(sendAsChat.id)) /*&& !tdlib.isChannel(chat.messageSenderId)*/) {
        this.subtitle = Lang.getString(R.string.AnonymousAdmin);
        this.isAnonymousAdmin = true;
      } else {
        this.subtitle =  "@" + tdlib.chatUsername(senderChat.chatId);
      }
      setChat(sendAsChat);
    } else {
      TdApi.MessageSenderUser senderUser = (TdApi.MessageSenderUser) messageSender;
      TdApi.User user = tdlib.cache().user(senderUser.userId);
      if (user == null) return;

      this.title = user.firstName + " " + user.lastName;
      this.subtitle = Lang.getString(R.string.YourAccount);
      this.isPersonal = true;
      setUser(user);
    }
  }

  public ImageFile getAvatar () {
    return avatar;
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    return avatarPlaceholderMetadata;
  }

  public TdApi.Chat getChat () {
    return chat;
  }

  public TdApi.User getUser () {
    return user;
  }

  public long getAnyId () {
    if (chat != null) return chat.id;
    if (user != null) return user.id;
    return 0;
  }

  public TdApi.MessageSender getMessageSender () {
    return messageSender;
  }
}
