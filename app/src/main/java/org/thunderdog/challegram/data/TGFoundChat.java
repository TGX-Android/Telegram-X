package org.thunderdog.challegram.data;

import android.text.SpannableStringBuilder;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;

import me.vkryl.core.StringUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.td.ChatId;

/**
 * Date: 7/17/17
 * Author: default
 */

public class TGFoundChat {
  private static final int FLAG_SECRET = 1;
  private static final int FLAG_NO_UNREAD = 1 << 1;
  private static final int FLAG_SELF = 1 << 2;
  private static final int FLAG_USE_TME = 1 << 3;
  private static final int FLAG_FORCE_USERNAME = 1 << 4;

  private int flags;

  private final Tdlib tdlib;
  private final TdApi.ChatList chatList;
  private final long chatId;
  private long userId;


  private @Nullable TdApi.Chat chatTitleSource;
  private CharSequence title;
  private String singleLineTitle;

  private String forcedSubtitle;

  private ImageFile avatar;
  private AvatarPlaceholder.Metadata avatarPlaceholderMetadata;

  public TGFoundChat (Tdlib tdlib) {
    long userId = tdlib.myUserId();
    this.tdlib = tdlib;
    this.chatId = 0;
    this.chatList = null;
    this.userId = userId;
    this.flags |= FLAG_SELF;
    setTitleImpl(Lang.getString(R.string.Saved), null);
    this.avatarPlaceholderMetadata = tdlib.cache().selfPlaceholderMetadata();
  }

  public TGFoundChat (Tdlib tdlib, TdApi.ChatList chatList, long chatId, boolean isGlobal) {
    this.tdlib = tdlib;
    this.chatList = chatList;
    this.chatId = chatId;
    setChat(tdlib.chatStrict(chatId), null, isGlobal);
  }

  public TGFoundChat (Tdlib tdlib, TdApi.ChatList chatList, TdApi.Chat chat, boolean isGlobal, String query) { // SearchChats & SearchPublicChats
    this.tdlib = tdlib;
    this.chatList = chatList;
    this.chatId = chat.id;
    setChat(chat, query, isGlobal);
  }

  @Deprecated
  public TGFoundChat (Tdlib tdlib, long userId) {
    this(tdlib, tdlib.cache().user(userId), null, false);
  }

  public TGFoundChat (Tdlib tdlib, TdApi.User user, String highlight, boolean isSelf) { // SearchContacts
    this.tdlib = tdlib;
    this.chatId = 0;
    this.userId = user.id;
    this.chatList = null;
    if (isSelf) {
      this.flags |= FLAG_SELF;
    }
    setUser(user, highlight);
  }

  /*package*/ TGFoundChat (Tdlib tdlib, TdApi.ChatList chatList, TdApi.Chat chat, String highlight) { // TGFoundMessage
    this.tdlib = tdlib;
    this.chatList = chatList;
    this.chatId = chat.id;
    setChat(chat, highlight, false);
  }

  public boolean hasHighlight () {
    return !StringUtils.isEmpty(highlight);
  }

  public void setUseTme () {
    this.flags |= FLAG_USE_TME;
    updateUsername(chat);
  }

  public TGFoundChat setForceUsername () {
    this.flags |= FLAG_FORCE_USERNAME;
    return this;
  }

  public boolean needForceUsername () {
    return BitwiseUtils.getFlag(flags, FLAG_FORCE_USERNAME);
  }

  public TGFoundChat setForcedSubtitle (String forcedSubtitle) {
    this.forcedSubtitle = forcedSubtitle;
    return this;
  }

  public String getForcedSubtitle () {
    return forcedSubtitle;
  }

  public TGFoundChat setNoUnread () {
    this.flags |= FLAG_NO_UNREAD;
    return this;
  }

  private boolean noSubscription;

  public void setNoSubscription () {
    this.noSubscription = true;
  }

  public boolean noSubscription () {
    return noSubscription;
  }

  public long getId () {
    return chatId;
  }

  public TdApi.ChatList getList () {
    return chatList;
  }

  public long getChatOrUserId () {
    return chatId != 0 ? chatId : ChatId.fromUserId(userId);
  }

  public long getAnyId () {
    return chatId != 0 ? chatId : createdChatId != 0 ? createdChatId : ChatId.fromUserId(userId);
  }

  private TdApi.Chat chat;
  private String highlight;
  private boolean isGlobal;

  private void setChat (TdApi.Chat chat, String highlight, boolean isGlobal) {
    this.chat = chat;
    this.isGlobal = isGlobal;
    this.highlight = highlight;
    int flags = this.flags;
    flags = BitwiseUtils.setFlag(flags, FLAG_SECRET, ChatId.isSecret(chat.id));
    flags = BitwiseUtils.setFlag(flags, FLAG_SELF, tdlib.isSelfChat(chat.id));
    this.flags = flags;
    this.userId = TD.getUserId(chat.type);
    this.avatarPlaceholderMetadata = (flags & FLAG_SELF) != 0 ? tdlib.cache().selfPlaceholderMetadata() : tdlib.chatPlaceholderMetadata(chat, true);
    updateChat(chat);
  }

  private void updateUsername (TdApi.Chat chat) {
    if (!isGlobal)
      return;
    String username = tdlib.chatUsername(chat.id);

    SpannableStringBuilder fullUsername = new SpannableStringBuilder();
    StringBuilder rawUsername = new StringBuilder();
    StringBuilder additionalInfo = new StringBuilder();

    if (!StringUtils.isEmpty(username)) {
      if ((flags & FLAG_USE_TME) != 0) {
        rawUsername.append('/');
      } else {
        rawUsername.append('@');
      }
      rawUsername.append(username);
    }
    if (chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
      long supergroupId = ChatId.toSupergroupId(chat.id);
      TdApi.SupergroupFullInfo supergroupFull = tdlib.cache().supergroupFull(supergroupId);
      int memberCount = supergroupFull != null ? supergroupFull.memberCount : 0;
      if (memberCount == 0) {
        TdApi.Supergroup supergroup = tdlib.cache().supergroup(supergroupId);
        if (supergroup != null) {
          memberCount = supergroup.memberCount;
        }
      }
      if (memberCount != 0) {
        additionalInfo.append(Lang.plural(TD.isChannel(chat.type) ? Config.CHANNEL_MEMBER_STRING : R.string.xMembers, memberCount));
      }
    }

    fullUsername.append(Strings.highlightWords(rawUsername.toString(), highlight, 1, null));
    if (additionalInfo.length() > 0) {
      if (fullUsername.length() > 0) {
        fullUsername.append(", ");
      }
      fullUsername.append(additionalInfo.toString());
    }
    if (fullUsername.length() == 0) {
      fullUsername.append(tdlib.status().chatStatus(chatId));
    }
    this.username = fullUsername;
  }

  private void setUser (TdApi.User user, String highlight) {
    if ((flags & FLAG_SELF) != 0) {
      this.avatarPlaceholderMetadata = tdlib.cache().selfPlaceholderMetadata();
      this.title = Strings.highlightWords(Lang.getString(R.string.SavedMessages), highlight, 0, InlineResultEmojiSuggestion.SPECIAL_SPLITTERS);
    } else {
      this.avatarPlaceholderMetadata = tdlib.cache().userPlaceholderMetadata(user, true);
      this.title = Strings.highlightWords(TD.getUserName(user), highlight, 0, InlineResultEmojiSuggestion.SPECIAL_SPLITTERS);
    }
    setPhoto(user.profilePhoto != null ? user.profilePhoto.small : null);
  }

  private void updateUser (TdApi.User user) {
    if (!isSelfChat()) {
      this.avatarPlaceholderMetadata = tdlib.cache().userPlaceholderMetadata(user, true);
      this.title = Strings.highlightWords(TD.getUserName(user), highlight, 0, InlineResultEmojiSuggestion.SPECIAL_SPLITTERS);
      setPhoto(user.profilePhoto != null ? user.profilePhoto.small : null);
    }
  }

  public @Nullable TdApi.Chat getChat () {
    return chat;
  }

  public long getChatId () {
    return chatId;
  }

  public long getUserId () {
    return userId;
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

  private boolean needMuteIcon, notificationsEnabled;

  public void updateChat () {
    if (chatId != 0) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat != null) {
        updateChat(chat);
      }
    } else {
      TdApi.User user = tdlib.cache().user(userId);
      if (user != null) {
        updateUser(user);
      }
    }
  }

  private void updateChat (TdApi.Chat chat) {
    updateUsername(chat);
    boolean isSelfChat = (flags & FLAG_SELF) != 0;
    setTitleImpl(tdlib.chatTitle(chat), chat);
    this.needMuteIcon = tdlib.chatNeedsMuteIcon(chatId);
    this.notificationsEnabled = tdlib.chatNotificationsEnabled(chatId);
    this.avatarPlaceholderMetadata = isSelfChat ? tdlib.cache().selfPlaceholderMetadata() : tdlib.chatPlaceholderMetadata(chat, true);
    setPhoto(!isSelfChat && chat.photo != null ? chat.photo.small : null);
  }

  public void updateMuted () {
    this.needMuteIcon = tdlib.chatNeedsMuteIcon(chatId);
    this.notificationsEnabled = tdlib.chatNotificationsEnabled(chatId);
  }

  public boolean needMuteIcon () {
    return needMuteIcon;
  }

  public boolean notificationsEnabled () {
    return notificationsEnabled;
  }

  public int getUnreadCount () {
    return (flags & FLAG_NO_UNREAD) != 0 ? 0 : chat != null ? (chat.unreadCount > 0 ? chat.unreadCount : chat.isMarkedAsUnread ? Tdlib.CHAT_MARKED_AS_UNREAD : 0) : 0;
  }

  public void setPhoto (TdApi.File photo) {
    if (photo != null) {
      this.avatar = new ImageFile(tdlib, photo);
      this.avatar.setSize(ChatView.getDefaultAvatarCacheSize());
    } else {
      this.avatar = null;
    }
  }

  private void setTitleImpl (String title, @Nullable TdApi.Chat chat) {
    if (!isGlobal && !StringUtils.isEmpty(highlight) && !StringUtils.equalsOrBothEmpty(this.title, title)) {
      this.title = Strings.highlightWords(title, highlight, 0, InlineResultEmojiSuggestion.SPECIAL_SPLITTERS);
      this.chatTitleSource = null;
    } else {
      this.title = title;
      this.chatTitleSource = (flags & FLAG_SELF) == 0 ? chat : null;
    }
    this.avatarPlaceholderMetadata = (flags & FLAG_SELF) != 0 ? tdlib.cache().selfPlaceholderMetadata() : chat != null ? tdlib.chatPlaceholderMetadata(chat, true) : null;
    if ((flags & FLAG_SELF) != 0) {
      this.singleLineTitle = Lang.getString(R.string.Saved);
    } else if (chat != null) {
      TdApi.User user = tdlib.chatUser(chat);
      if (user != null && user.type.getConstructor() == TdApi.UserTypeRegular.CONSTRUCTOR) {
        this.singleLineTitle = user.firstName;
      }
    }
  }

  private CharSequence username;

  public CharSequence getUsername () {
    return username;
  }

  public boolean isGlobal () {
    return isGlobal;
  }

  private long createdChatId;

  public void setCreatedChatId (long chatId) {
    this.createdChatId = chatId;
  }

  public long getCreatedChatId () {
    return createdChatId;
  }

  private ThreadInfo messageThread;

  public void setMessageThread (ThreadInfo messageThread) {
    this.messageThread = messageThread;
  }

  public long getMessageThreadId () {
    return messageThread != null ? messageThread.getMessageThreadId() : 0;
  }

  public ThreadInfo getMessageThread () {
    return messageThread;
  }

  public CharSequence getTitle () {
    return chatTitleSource != null ? tdlib.chatTitle(chatTitleSource) : title;
  }

  public String getFullTitle () {
    return (flags & FLAG_SELF) != 0 ? Lang.getString(R.string.SavedMessages) : title.toString();
  }

  public CharSequence getSingleLineTitle () {
    return StringUtils.isEmpty(singleLineTitle) ? title : singleLineTitle;
  }

  public ImageFile getAvatar () {
    return avatar;
  }

  public boolean isSecret () {
    return (flags & FLAG_SECRET) != 0;
  }

  public boolean isSelfChat () {
    return (flags & FLAG_SELF) != 0;
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    return avatarPlaceholderMetadata;
  }
}
