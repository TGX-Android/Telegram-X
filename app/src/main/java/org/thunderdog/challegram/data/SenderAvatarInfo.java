package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;

public class SenderAvatarInfo {
  private final TdApi.MessageSender sender;

  private final Tdlib tdlib;

  private ImageFile imageFile;

  private AvatarPlaceholder.Metadata avatarMetadata;

  public SenderAvatarInfo (@NonNull TdApi.MessageSender sender, @NonNull Tdlib tdlib) {
    this.sender = sender;
    this.tdlib = tdlib;

    initAvatarInfo();
  }

  private void initAvatarInfo () {
    if (isUser()) {
      TdApi.MessageSenderUser senderUser = (TdApi.MessageSenderUser) sender;
      TdApi.User user = tdlib.cache().user(senderUser.userId);
      imageFile = tdlib.cache().userAvatar(user.id);
      if (imageFile == null) {
        avatarMetadata = tdlib.cache().userPlaceholderMetadata(user, true);
      }
    } else {
      TdApi.MessageSenderChat senderChat = (TdApi.MessageSenderChat) sender;
      TdApi.Chat chat = tdlib.chat(senderChat.chatId);
      if (chat != null) {
        imageFile = tdlib.chatAvatar(chat.id);
        if (imageFile == null) {
          avatarMetadata = tdlib.chatPlaceholderMetadata(chat, true);
        }
      }
    }
  }

  public boolean isUser () {
    return sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR;
  }

  public ImageFile getImageFile() {
    return imageFile;
  }

  public AvatarPlaceholder.Metadata getAvatarMetadata() {
    return avatarMetadata;
  }


  public boolean hasAvatar() {
    return imageFile != null;
  }
}
