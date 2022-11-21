package org.thunderdog.challegram.loader;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

public class AvatarReceiver extends ImageReceiver {
  private @Nullable Tdlib tdlib;
  private AvatarPlaceholder avatarPlaceholder;
  private AvatarPlaceholder.Metadata avatarPlaceholderMetadata;
  private TdApi.User user;
  private TdApi.Chat chat;
  private boolean hasPhoto;

  public AvatarReceiver (View v) {
    super(v, 0);
  }

  public AvatarReceiver (View v, Tdlib tdlib) {
    super(v, 0);
    this.tdlib = tdlib;
  }

  public AvatarReceiver setTdlib (Tdlib tdlib) {
    this.tdlib = tdlib;
    return this;
  }

  public void setMessageSender (@Nullable TdApi.MessageSender sender) {
    if (sender == null) {
      clear();
      return;
    };
    if (sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
      long userId = ((TdApi.MessageSenderUser) sender).userId;
      setUser(userId);
      return;
    }
    if (sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
      long chatId = ((TdApi.MessageSenderChat) sender).chatId;
      setChat(chatId);
      return;
    }
    clear();
  }

  public long getChatId () {
    return chat != null ? chat.id : 0;
  }

  private void setChat (long chatId) {
    setChat(tdlib.chat(chatId));
  }

  private void setChat (@Nullable TdApi.Chat chat) {
    long newChatId = chat != null ? chat.id : 0;
    long oldChatId = getChatId();
    if (oldChatId != newChatId) {
      this.chat = chat;
      if (chat != null) {
        setPhoto(chat);
      } else {
        clear();
      }
    }
  }

  private void setPhoto (TdApi.Chat chat) {
    if (hasPhoto = (chat.photo != null)) {
      setPhotoImpl(tdlib, chat.photo.small, chat.photo.big);
    } else {
      avatarPlaceholderMetadata = tdlib.chatPlaceholderMetadata(chat, true);
      clear();
    }
    invalidate();
  }

  public long getUserId () {
    return user != null ? user.id : 0;
  }

  public void setUser (long userId) {
    setUser(tdlib.cache().user(userId));
  }

  public void setUser (@Nullable TdApi.User user) {
    long newUserId = user != null ? user.id : 0;
    long oldUserId = getUserId();
    if (oldUserId != newUserId) {
      this.user = user;
      if (user != null) {
        setPhoto(user);
      } else {
        clear();
      }
    }
  }

  private void setPhoto (TdApi.User user) {
    if (hasPhoto = (user.profilePhoto != null)) {
      setPhotoImpl(tdlib, user.profilePhoto.small, user.profilePhoto.big);
    } else {
      avatarPlaceholderMetadata = tdlib.cache().userPlaceholderMetadata(user, false);
      clear();
    }
    invalidate();
  }

  private void setPhotoImpl (Tdlib tdlib, TdApi.File photoSmall, TdApi.File photoFull) {
    ImageFile imageFile = new ImageFile(tdlib, photoSmall == null ? photoFull : photoSmall);
    imageFile.setScaleType(ImageFile.CENTER_CROP);
    requestFile(imageFile);
  }

  @Override
  public void draw (Canvas c) {
    if (getUserId() == 0 && getChatId() == 0) return;
    if (hasPhoto) {
      if (needPlaceholder()) {
        drawPlaceholder(c, R.id.theme_color_placeholder);
      }
      super.draw(c);
    } else {
      if (avatarPlaceholderMetadata != null) {
        if (avatarPlaceholder == null)
          avatarPlaceholder = new AvatarPlaceholder(Screen.px(getWidth() / 2f), avatarPlaceholderMetadata, null);
        avatarPlaceholder.draw(c, centerX(), centerY());
      }
    }
  }

  private void drawPlaceholder (Canvas c, @ThemeColorId int colorId) {
    c.drawCircle(centerX(), centerY(), getRadius(), Paints.fillingPaint(Theme.getColor(colorId)));
  }
}
