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
 * File created on 15/08/2015 at 23:37
 */
package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.UserProvider;
import org.thunderdog.challegram.util.text.Text;

import java.util.ArrayList;

import me.vkryl.core.StringUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class TGUser implements UserProvider {
  private static final int FLAG_LOCAL = 0x01;
  private static final int FLAG_ONLINE = 0x02;
  private static final int FLAG_GROUP_CREATOR = 0x04;
  private static final int FLAG_CONTACT = 0x08;
  private static final int FLAG_USERNAME = 0x10;
  private static final int FLAG_NO_BOT_STATE = 0x20;
  private static final int FLAG_SHOW_PHONE_NUMBER = 0x40;
  private static final int FLAG_CUSTOM_STATUS_TEXT = 0x80;
  private static final int FLAG_CHAT_TITLE_AS_USER_NAME = 0x160;

  private final Tdlib tdlib;
  private final long userId;
  private @Nullable TdApi.User user;
  private ImageFile imageFile;
  private AvatarPlaceholder.Metadata avatarPlaceholderMetadata;

  private float statusWidth;
  private String statusText;

  private float nameWidth;
  private boolean nameFake;
  private String nameText;

  private int role;
  private int flags;

  private @Nullable ArrayList<TGUser> boundList;

  /*public TGUser (int userId) {
    this.userId = userId;
    setUser(TdlibCache.instance().getUser(userId), 0);
  }*/

  public TGUser (Tdlib tdlib, @NonNull TdApi.Chat chat) {
    this.tdlib = tdlib;
    this.userId = tdlib.chatUserId(chat);
    TdApi.User user = tdlib.chatUser(chat);
    if (user != null) {
      setUser(user, 0);
    } else {
      setChat(chat.id, chat);
    }
  }

  public TGUser (Tdlib tdlib, @NonNull TdApi.User user) {
    this.tdlib = tdlib;
    this.userId = user.id;
    setUser(user, 0);
  }

  private TGUser (Tdlib tdlib, @NonNull TdApi.User user, boolean showPhone, boolean showUsername) {
    this.tdlib = tdlib;
    this.userId = user.id;
    if (showPhone) {
      flags = FLAG_CONTACT;
    } else if (showUsername) {
      flags = FLAG_USERNAME;
    }
    setUser(user, 0);
  }

  private int contactId, rawContactId;
  private String firstName, lastName, phoneNumber;

  public TGUser (Tdlib tdlib, int contactId, int rawContactId, String firstName, String lastName, String phoneNumber) {
    this.userId = 0;
    this.tdlib = tdlib;
    this.flags = FLAG_LOCAL;
    this.contactId = contactId;
    this.rawContactId = rawContactId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.phoneNumber = phoneNumber;
    buildContact();
  }

  @Override
  public TdApi.User getTdUser () {
    return user;
  }

  public TGUser setShowPhoneNumber () {
    this.flags |= FLAG_SHOW_PHONE_NUMBER;
    updateStatus();
    return this;
  }

  public void setNoBotState () {
    flags |= FLAG_NO_BOT_STATE;
    updateStatus();
  }

  public void setBoundList (@Nullable ArrayList<TGUser> boundList) {
    this.boundList = boundList;
  }

  public boolean needSeparator () {
    return boundList != null && boundList.size() > 1 && !boundList.get(boundList.size() - 1).equals(this);
  }

  private void buildContact () {
    avatarPlaceholderMetadata = new AvatarPlaceholder.Metadata(TD.getAvatarColorId(rawContactId, tdlib.myUserId()), TD.getLetters(firstName, lastName));
    updateName();
    updateStatus();
  }

  public void setCreatorId (int creatorId) {
    if (creatorId != 0 && userId == creatorId) {
      flags |= FLAG_GROUP_CREATOR;
    } else {
      flags &= ~FLAG_GROUP_CREATOR;
    }
    updateStatus();
  }

  public void setRole (int role) {
    if (this.role != role) {
      this.role = role;
      updateStatus();
    }
  }

  public void setCustomStatus (String statusText) {
    if (!StringUtils.equalsOrBothEmpty(this.statusText, statusText)) {
      if (StringUtils.isEmpty(statusText)) {
        this.flags &= ~FLAG_CUSTOM_STATUS_TEXT;
        updateStatus();
      } else {
        this.statusText = statusText;
        this.flags |= FLAG_CUSTOM_STATUS_TEXT;
        this.flags &= ~FLAG_ONLINE;
      }
    }
  }

  public void chatTitleAsUserName () {
    this.flags |= FLAG_CHAT_TITLE_AS_USER_NAME;
  }

  private long chatId;

  public long getChatId () {
    return chatId != 0 ? chatId : ChatId.fromUserId(getUserId());
  }

  public TdApi.MessageSender getSenderId () {
    if (userId != 0) {
      return new TdApi.MessageSenderUser(userId);
    } else if (chatId != 0) {
      if (ChatId.isUserChat(chatId)) {
        return new TdApi.MessageSenderUser(tdlib.chatUserId(chatId));
      } else {
        return new TdApi.MessageSenderChat(chatId);
      }
    } else {
      throw new IllegalStateException();
    }
  }

  public void setChat (long chatId, @Nullable TdApi.Chat chat) {
    this.user = null;
    this.chatId = chatId;
    avatarPlaceholderMetadata = tdlib.chatPlaceholderMetadata(chatId, chat, false);
    imageFile = tdlib.chatAvatar(chatId);
    this.nameText = tdlib.chatTitle(chat);
    this.nameFake = Text.needFakeBold(nameText);
    this.nameWidth = U.measureText(nameText, Paints.getTitleBigPaint());
    updateStatus();
  }

  public void setUser (@Nullable TdApi.User user, int creatorId) {
    this.user = user;
    if (creatorId != 0 && userId == creatorId) {
      flags |= FLAG_GROUP_CREATOR;
    } else {
      flags &= ~FLAG_GROUP_CREATOR;
    }
    if (user == null || TD.isPhotoEmpty(user.profilePhoto)) {
      avatarPlaceholderMetadata = new AvatarPlaceholder.Metadata(TD.getAvatarColorId(user, tdlib.myUserId()), TD.getLetters(user));
    } else {
      imageFile = new ImageFile(tdlib, user.profilePhoto.small);
      imageFile.setSize(ChatView.getDefaultAvatarCacheSize());
    }
    updateName();
    updateStatus();
  }

  public void setStatus (TdApi.UserStatus status) {
    if (user != null && status != null) {
      user.status = status;
      updateStatus();
    }
  }

  public boolean updateName () {
    if ((flags & FLAG_CHAT_TITLE_AS_USER_NAME) != 0) return false;
    String nameText = (flags & FLAG_LOCAL) != 0 ? TD.getUserName(firstName, lastName) : TD.getUserName(userId, user);
    if (!StringUtils.equalsOrBothEmpty(this.nameText, nameText)) {
      this.nameText = nameText;
      this.nameFake = Text.needFakeBold(nameText);
      this.nameWidth = U.measureText(nameText, Paints.getTitleBigPaint());
      return true;
    }
    return false;
  }

  public boolean isGroupCreator () {
    return (flags & FLAG_GROUP_CREATOR) != 0;
  }

  public boolean updateStatus () {
    int oldFlags = this.flags;
    String statusText;
    if ((flags & FLAG_CUSTOM_STATUS_TEXT) != 0) {
      return true;
    } else if (((flags & FLAG_CONTACT) != 0 || (flags & FLAG_SHOW_PHONE_NUMBER) != 0) && user != null) {
      statusText = Strings.formatPhone(user.phoneNumber);
    } else if ((flags & FLAG_USERNAME) != 0 && user != null && !Td.isEmpty(user.usernames)) {
      TdApi.Usernames usernames = user.usernames;
      statusText = Td.isEmpty(user.usernames) ? Strings.join(Lang.getConcatSeparator(), usernames.activeUsernames, username -> "@" + username) : null;
    } else {
      statusText = role != 0 ? TD.getRoleName(user, role) : null;
    }
    if ((flags & FLAG_LOCAL) != 0) {
      statusText = Strings.formatPhone(phoneNumber, false, true);
    } else if (statusText == null) {
      if (userId != 0) {
        flags = BitwiseUtils.setFlag(flags, FLAG_ONLINE, tdlib.cache().isOnline(userId));
        statusText = TD.getChatMemberSubtitle(tdlib, userId, user, (flags & FLAG_NO_BOT_STATE) == 0);
      } else {
        statusText = tdlib.isMultiChat(chatId) ? Lang.lowercase(Lang.getString(R.string.Group)) : tdlib.status().chatStatus(chatId).toString();
      }
    } else {
      flags &= ~FLAG_ONLINE;
    }
    if (!StringUtils.equalsOrBothEmpty(this.statusText, statusText)) {
      this.statusText = statusText;
      this.statusWidth = U.measureText(statusText, UserView.getStatusPaint());
      return true;
    }
    return this.flags != oldFlags;
  }

  public int getRole () {
    return role;
  }

  public long getUserId () {
    return (flags & FLAG_LOCAL) != 0 ? contactId : user == null ? 0 : user.id;
  }

  public String getName () {
    return nameText;
  }

  public float getNameWidth () {
    return nameWidth;
  }

  public String getStatus () {
    return statusText;
  }

  public float getStatusWidth () {
    return statusWidth;
  }

  public boolean isOnline () {
    return (flags & FLAG_ONLINE) != 0 || ((flags & FLAG_SHOW_PHONE_NUMBER) == 0 && tdlib.isSelfUserId(userId));
  }

  @Override
  public boolean equals (@Nullable Object obj) {
    if (obj instanceof TGUser) {
      TGUser other = (TGUser) obj;
      return getUserId() == other.getUserId() && getChatId() == other.getChatId() && this.flags == other.flags && this.role == other.role;
    }
    return super.equals(obj);
  }

  public @Nullable TdApi.User getUser () {
    return user;
  }

  public String getSortingKey () {
    return (flags & FLAG_LOCAL) != 0 ? getSortingKey(firstName, lastName) : getSortingKey(user);
  }

  public static String getSortingKey (String firstName, String lastName) {
    return Strings.clean((firstName + " " + lastName).trim());
  }

  public static String getSortingKey (TdApi.User u) {
    return u == null ? "#" : getSortingKey(u.firstName, u.lastName);
  }

  public String getFirstName () {
    return (flags & FLAG_CHAT_TITLE_AS_USER_NAME) != 0 ? nameText : (flags & FLAG_LOCAL) != 0 ? firstName : user != null ? user.firstName : "User#" + userId;
  }

  public String getLastName () {
    return (flags & FLAG_LOCAL) != 0 ? lastName : user == null ? "" : user.lastName;
  }

  public String getUsername () {
    return Td.primaryUsername(getUsernames());
  }

  public @Nullable TdApi.Usernames getUsernames () {
    return (flags & FLAG_LOCAL) == 0 && user != null ? user.usernames : null;
  }

  public ImageFile getAvatar () {
    return imageFile;
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    return avatarPlaceholderMetadata;
  }

  public boolean hasAvatar () {
    return imageFile != null;
  }

  // static stuff

  public static TGUser createWithPhone (Tdlib tdlib, TdApi.User user) {
    return new TGUser(tdlib, user, true, false);
  }

  public static TGUser createWithUsername (Tdlib tdlib, TdApi.User user) {
    return new TGUser(tdlib, user, false, true);
  }
}
