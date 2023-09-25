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
 * File created on 27/04/2015 at 18:40
 */
package org.thunderdog.challegram.data;

import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.animator.BounceAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;

public class TGChat implements TdlibStatusManager.HelperTarget, TD.ContentPreview.RefreshCallback, Counter.Callback, Destroyable {
  private static final int FLAG_HAS_PREFIX = 1;
  private static final int FLAG_TEXT_DRAFT = 1 << 4;
  private static final int FLAG_SHOW_VERIFY = 1 << 5;
  private static final int FLAG_SELF_CHAT = 1 << 7;
  private static final int FLAG_ATTACHED = 1 << 8;
  private static final int FLAG_CONTENT_HIDDEN = 1 << 9;
  private static final int FLAG_CONTENT_STRING = 1 << 10;
  private static final int FLAG_ONLINE = 1 << 12;
  private static final int FLAG_ARCHIVE = 1 << 14;
  private static final int FLAG_SHOW_SCAM = 1 << 15;
  private static final int FLAG_SHOW_FAKE = 1 << 16;

  private int flags, listMode;

  private final ViewController<?> context;
  private final Tdlib tdlib;
  @Nullable
  private final TdApi.Chat chat;
  private final TdApi.ChatList chatList;
  @Nullable
  private final TdlibChatList archive;
  private long dataId;
  private int dataType;

  private int currentWidth;

  private long viewedMessageId;

  private AvatarPlaceholder.Metadata avatarPlaceholder;

  private String title;
  private Text trimmedTitle;

  private @Nullable EmojiStatusHelper.EmojiStatusDrawable emojiStatusDrawable;

  private String time;
  private int timeWidth;

  private Text prefix;

  private String text;
  private TextEntity[] entities;
  private Text trimmedText;

  private TD.ContentPreview currentPreview;
  private IntList textIconIds;
  private @PorterDuffColorId int textIconColorId;

  private int textLeft;
  private int timeLeft;
  private int muteLeft, verifyLeft;
  private int emojiStatusLeft;
  private int checkRight;

  private Text chatMark;

  private final MultipleViewProvider currentViews = new MultipleViewProvider();

  private final BounceAnimator scheduleAnimator;
  private final Counter counter, mentionCounter, reactionsCounter, viewCounter;

  public TGChat (ViewController<?> context, TdApi.ChatList chatList, TdApi.Chat chat, boolean makeMeasures) {
    this.context = context;
    this.statusHelper = new TdlibStatusManager.Helper(context.context(), context.tdlib(), this, context);
    this.tdlib = context.tdlib();
    this.chatList = chatList;
    this.chat = Td.copyOf(chat);
    this.archive = null;
    this.listMode = Settings.instance().getChatListMode();
    this.dataType = chat.type.getConstructor();
    switch (dataType) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        this.dataId = ((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId;
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        this.dataId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
        break;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        this.dataId = TD.getUserId(chat.type);
        break;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        this.dataId = ((TdApi.ChatTypePrivate) chat.type).userId;
        if (dataId != 0 && tdlib.isSelfUserId(dataId)) {
          flags |= FLAG_SELF_CHAT;
        }
        break;
      }
    }
    this.scheduleAnimator = new BounceAnimator(currentViews);
    this.counter = new Counter.Builder().callback(this).build();
    this.mentionCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_at_16, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    this.reactionsCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_favorite_14, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    this.viewCounter = new Counter.Builder()
      .textSize(11f)
      .callback(this)
      .noBackground()
      .allBold(false)
      .textColor(ColorId.ticksRead)
      .drawable(R.drawable.baseline_visibility_14, 14f, 3f, Gravity.RIGHT)
      .build();
    setCounter(false);
    setViews();
    this.scheduleAnimator.setValue(hasScheduledMessages(), false);
    checkOnline();
    if (makeMeasures) {
      buildLayout(Screen.currentWidth());
    }
  }

  public TGChat (ViewController<?> context, TdlibChatList list, boolean makeMeasures) {
    this.context = context;
    this.statusHelper = null;
    this.tdlib = context.tdlib();
    this.chat = null;
    this.chatList = null;
    this.archive = list;
    this.listMode = Settings.instance().getChatListMode();
    this.scheduleAnimator = new BounceAnimator(currentViews);
    this.counter = new Counter.Builder().callback(this).build();
    this.mentionCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_at_16, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    this.reactionsCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_favorite_14, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    this.viewCounter = null;
    this.flags |= FLAG_ARCHIVE;
    setCounter(false);
    this.scheduleAnimator.setValue(hasScheduledMessages(), false);
    if (makeMeasures) {
      buildLayout(Screen.currentWidth());
    }
  }

  private int lastCounterAddWidth;

  private void layoutContent () {
    if (listMode == Settings.CHAT_MODE_2LINE) {
      layoutText();
    } else {
      setPrefix();
    }
  }

  public void onAttachToView () {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.onAppear();
    }
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    if (sizeChanged) {
      if (counter == viewCounter) {
        layoutTitle(false);
      } else {
        int newWidth = getCounterAddWidth();
        if (lastCounterAddWidth != newWidth) {
          layoutContent();
        }
      }
    }
    currentViews.invalidate();
  }

  @Override
  public boolean needAnimateChanges (Counter counter) {
    return needAnimateChanges();
  }

  public void onArchiveCounterChanged () {
    setCounter(true);
    scheduleAnimator.setValue(hasScheduledMessages(), true);
  }

  public void onArchiveMessageChanged () {
    setTime();
  }

  public void onArchiveChanged () {
    setCounter(true);
    setTime();
    setText();
  }

  public int getListMode () {
    return listMode;
  }

  public void checkChatListMode () {
    int newMode = Settings.instance().getChatListMode();
    if (listMode != newMode) {
      listMode = newMode;
      currentWidth = 0;
      if (avatarPlaceholder != null) {
        setAvatar();
      }
      currentViews.requestLayout();
      currentViews.invalidate();
    }
  }

  private long lastSyncTime;

  public void syncCounter () {
    if (chat != null && chat.lastMessage != null && isChannel() && showViews()) {
      long time = SystemClock.uptimeMillis();
      if (viewedMessageId != chat.lastMessage.id || time - lastSyncTime > 60000 * 5 + (1f - MathUtils.clamp((float) TD.getViewCount(chat.lastMessage.interactionInfo) / 1000.0f)) * 1800000 ) {
        lastSyncTime = time;
        viewedMessageId = chat.lastMessage.id;
        tdlib.client().send(new TdApi.ViewMessages(chat.id, new long[] {viewedMessageId}, new TdApi.MessageSourceChatList(), false), tdlib.okHandler());
      }
    }
  }

  public boolean checkOnline () {
    boolean isOnline = !isArchive() && ChatId.isUserChat(chat.id) && tdlib.status().isOnline(TD.getUserId(chat));
    int newFlags = BitwiseUtils.setFlag(this.flags, FLAG_ONLINE, isOnline);
    if (this.flags != newFlags) {
      this.flags = newFlags;
      return true;
    }
    return false;
  }

  public void makeMeasures () {
    buildLayout(Screen.currentWidth());
  }

  private void buildLayout (int width) {
    if (width > 0) {
      currentWidth = width;
      setCounter(true);
      setTime();
      setTitle();
      setText();
      setAvatar();
    }
  }

  public boolean isOnline () {
    return (flags & FLAG_ONLINE) != 0;
  }

  public boolean checkLayout (int width) {
    if (currentWidth == 0) {
      boolean changed = width > 0;
      buildLayout(width);
      return changed;
    } else {
      if (currentWidth != width && width > 0) {
        currentWidth = width;
        layoutTime();
        layoutTitle(false);
        layoutContent();
        return true;
      }
    }
    return false;
  }

  public boolean isArchive () {
    return (flags & FLAG_ARCHIVE) != 0;
  }

  public TdApi.Chat getChat () {
    return tdlib.chat(getChatId());
  }

  public long getChatUserId () {
    return TD.getUserId(this.chat);
  }

  public long getChatId () {
    return chat != null ? chat.id : 0;
  }

  public TdApi.ChatList getChatList () {
    return chatList;
  }

  public long getChatOrder () {
    return isArchive() ? Long.MAX_VALUE : ChatPosition.getOrder(chat, chatList);
  }

  private int getTotalUnreadCount () {
    if (isArchive()) {
      TdlibCounter counter = tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE);
      int unreadCount = counter.chatCount;
      int markedAsUnreadCount = counter.markedAsUnreadCount;
      unreadCount -= markedAsUnreadCount;
      return unreadCount > 0 ? unreadCount : markedAsUnreadCount > 0 ? Tdlib.CHAT_MARKED_AS_UNREAD : 0;
    } else {
      return chat.unreadCount > 0 ? chat.unreadCount : chat.isMarkedAsUnread ? Tdlib.CHAT_MARKED_AS_UNREAD : 0;
    }
  }

  public long getPrivateId () {
    return dataType == TdApi.ChatTypePrivate.CONSTRUCTOR ? dataId : 0;
  }

  public long getGroupId () {
    return dataType == TdApi.ChatTypeBasicGroup.CONSTRUCTOR ? dataId : 0;
  }

  public long getChannelId () {
    return dataType == TdApi.ChatTypeSupergroup.CONSTRUCTOR ? dataId : 0;
  }

  public int getSecretChatId () {
    return TD.getSecretChatId(chat);
  }

  public boolean updateDraftMessage (long chatId, TdApi.DraftMessage draftMessage) {
    if (getChatId() == chatId) {
      chat.draftMessage = draftMessage;
      setText();
      setTime();
      layoutTitle(false);
      return true;
    }
    return false;
  }

  public boolean updateTopMessage (long chatId, @Nullable TdApi.Message message) {
    if (getChatId() == chatId) {
      TdApi.Message oldMessage = chat.lastMessage;
      chat.lastMessage = message;
      if ((oldMessage == null && message == null) || (oldMessage != null && message != null && oldMessage.id == message.id))
        return false;
      setCounter(true);
      setTime();
      setText();
      layoutTitle(false);
      return true;
    }
    return false;
  }

  public boolean updateChatPermissions (long chatId, TdApi.ChatPermissions permissions) {
    if (getChatId() == chatId) {
      chat.permissions = permissions;
    }
    return false;
  }

  public boolean updateChatClientData (long chatId, String clientData) {
    if (getChatId() == chatId) {
      boolean hadPasscode = tdlib.hasPasscode(chat);
      chat.clientData = clientData;
      boolean hasPasscode = tdlib.hasPasscode(chat);
      if (hadPasscode != hasPasscode) {
        setText();
        return true;
      }
    }
    return false;
  }

  public boolean updateChatPosition (long chatId, TdApi.ChatPosition position, final boolean sourceChanged, final boolean pinStateChanged) {
    if (getChatId() == chatId) {
      TdApi.ChatPosition prevPosition = ChatPosition.findPosition(chat, chatList);
      if (Tdlib.updateChatPosition(chat, position) && Td.equalsTo(chatList, position.list)) {
        if (sourceChanged) {
          setTime();
          setCounter(true);
          if (position.source instanceof TdApi.ChatSourcePublicServiceAnnouncement || (prevPosition != null && prevPosition.source instanceof TdApi.ChatSourcePublicServiceAnnouncement)) {
            setText();
          }
          layoutTitle(false);
        }
        return true;
      }
    }
    return false;
  }

  public TdApi.ChatPosition getPosition () {
    return ChatPosition.findPosition(chat, chatList);
  }

  public boolean isPinned () {
    TdApi.ChatPosition position = getPosition();
    return position != null && position.isPinned;
  }

  public boolean isPinnedOrSpecial () {
    TdApi.ChatPosition position = getPosition();
    return position != null && (position.isPinned || position.source != null);
  }

  public TdApi.ChatSource getSource () {
    TdApi.ChatPosition position = getPosition();
    return position != null ? position.source : null;
  }

  public boolean checkLastMessageId (long messageId) {
    if (chat != null && chat.lastMessage != null && chat.lastMessage.id == messageId) {
      return true;
    }
    if (currentPreview != null) {
      Tdlib.Album album = currentPreview.getAlbum();
      if (album != null) {
        for (TdApi.Message message : album.messages) {
          if (message.id == messageId) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean updateMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    if (checkLastMessageId(oldMessageId)) {
      return updateTopMessage(message.chatId, message);
    }
    return false;
  }

  public boolean updateMessageInteractionInfo (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    if (getChatId() == chatId && checkLastMessageId(messageId)) {
      chat.lastMessage.interactionInfo = interactionInfo;
      setViews();
      return showViews();
    }
    return false;
  }

  public boolean updateMessageContent (long chatId, long messageId, TdApi.MessageContent newContent) {
    if (getChatId() == chatId && chat.lastMessage != null) {
      if (currentPreview != null) {
        Tdlib.Album album = currentPreview.getAlbum();
        if (album != null) {
          boolean updatedAlbum = false;
          for (TdApi.Message message : album.messages) {
            if (message.id == messageId) {
              message.content = newContent;
              updatedAlbum = true;
              break;
            }
          }
          if (updatedAlbum) {
            setContentPreview(TD.getAlbumPreview(tdlib, chat.lastMessage, album, true));
            return true;
          }
        }
      }
      if (chat.lastMessage.id == messageId) {
        chat.lastMessage.content = newContent;
        setText();
        return true;
      }
    }
    return false;
  }

  public boolean updateMessagesDeleted (long chatId, long[] messageIds) {
    if (chat.id == chatId && chat.lastMessage != null) {
      if (ArrayUtils.indexOf(messageIds, chat.lastMessage.id) >= 0) {
        chat.lastMessage = null;
        setText();
        return true;
      }

      if (currentPreview != null) {
        Tdlib.Album album = currentPreview.getAlbum();
        if (album != null) {
          boolean albumChanged = false;
          for (int index = album.messages.size() - 1; index >= 0; index--) {
            if (ArrayUtils.indexOf(messageIds, album.messages.get(index).id) >= 0) {
              album.messages.remove(index);
              albumChanged = true;
            }
          }
          if (album.messages.size() <= 1) {
            setText();
            return true;
          }
          if (albumChanged) {
            setContentPreview(TD.getAlbumPreview(tdlib, chat.lastMessage, album, true));
          }
        }
      }
      return true;
    }
    return false;
  }

  public boolean updateChatReadOutbox (long chatId, final long lastReadOutboxMessageId) {
    if (chat.id == chatId) {
      chat.lastReadOutboxMessageId = lastReadOutboxMessageId;
      return chat.lastMessage != null && TD.isOut(chat.lastMessage);
    }
    return false;
  }

  public boolean updateChatReadInbox (long chatId, final long lastReadInboxMessageId, final int unreadCount) {
    if (chat.id == chatId) {
      boolean showDraft = showDraft();
      chat.lastReadInboxMessageId = lastReadInboxMessageId;
      chat.unreadCount = unreadCount;
      boolean newShowDraft = showDraft();
      setCounter(true);
      if (showDraft != newShowDraft) {
        setText();
      }
      return true;
    }
    return false;
  }

  public boolean updateChatUnreadReactionCount (long chatId, int unreadReactionCount) {
    if (chat.id == chatId) {
      boolean hadBadge = chat.unreadReactionCount > 0;
      chat.unreadReactionCount = unreadReactionCount;
      boolean hasBadge = unreadReactionCount > 0;
      if (hasBadge != hadBadge) {
        setCounter(needAnimateChanges());
        return true;
      }
    }
    return false;
  }

  public boolean updateChatUnreadMentionCount (long chatId, int unreadMentionCount) {
    if (chat.id == chatId) {
      boolean hadBadge = chat.unreadMentionCount > 0;
      chat.unreadMentionCount = unreadMentionCount;
      boolean hasBadge = unreadMentionCount > 0;
      if (hasBadge != hadBadge) {
        setCounter(needAnimateChanges());
        return true;
      }
    }
    return false;
  }

  public boolean updateChatHasScheduledMessages (long chatId, boolean hasScheduledMessages) {
    if (chat.id == chatId && chat.hasScheduledMessages != hasScheduledMessages) {
      chat.hasScheduledMessages = hasScheduledMessages;
      scheduleAnimator.setValue(hasScheduledMessages, needAnimateChanges());
      return true;
    }
    return false;
  }

  public boolean updateUser (TdApi.User user) {
    if (chat == null)
      return false;
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        if (getPrivateId() == user.id) {
          setTitle();
          setAvatar();
          return true;
        }
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        if (Td.getSenderUserId(chat.lastMessage) == user.id) {
          setText();
          return true;
        }
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (tdlib.isSupergroupChat(chat) && Td.getSenderId(chat.lastMessage) == user.id) {
          setText();
          return true;
        }
        break;
      }
    }
    return false;
  }

  public boolean updateChatTitle (long chatId, String newTitle) {
    if (getChatId() == chatId) {
      chat.title = newTitle;
      setTitleImpl(tdlib.chatTitle(chat));
      layoutTitle(true);
      if (tdlib.isMultiChat(chat) && !ChatId.isUserChat(chatId) && Td.getSenderId(chat.lastMessage) == chatId) {
        setText();
      }
      return true;
    }
    return false;
  }

  public boolean updateMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    if (getChatId() == chatId) {
      chat.isMarkedAsUnread = isMarkedAsUnread;
      setCounter(true);
      return true;
    }
    return false;
  }

  public boolean updateChatPhoto (long chatId, TdApi.ChatPhotoInfo photo) {
    if (getChatId() == chatId) {
      chat.photo = photo;
      setAvatar();
      return true;
    }
    return false;
  }

  public boolean updateChatSettings (long chatId, final TdApi.ChatNotificationSettings settings) {
    if (getChatId() == chatId) {
      chat.notificationSettings = settings;
      layoutTitle(false);
      setCounter(true);
      return true;
    }
    return false;
  }

  private void setAvatar () {
    if (isArchive()) {
      avatarPlaceholder = new AvatarPlaceholder.Metadata(ColorId.avatarArchive, R.drawable.baseline_archive_24);
    } else {
      avatarPlaceholder = null;
    }
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholder () {
    return avatarPlaceholder;
  }

  public boolean isOutgoing () {
    return !isArchive() && !showDraft() && chat.lastMessage != null && chat.lastMessage.isOutgoing;
  }

  public boolean showDraft () {
    return !isArchive() && chat.unreadCount == 0 && chat.draftMessage != null && chat.draftMessage.inputMessageText.getConstructor() == TdApi.InputMessageText.CONSTRUCTOR;
  }

  public boolean isUnread () {
    if (!isArchive() && chat.lastMessage != null && !tdlib.isChannelChat(chat)) {
      if (isOutgoing()) {
        return chat.lastMessage.id > chat.lastReadOutboxMessageId;
      } else {
        return chat.lastMessage.id > chat.lastReadInboxMessageId;
      }
    }
    return false;
  }

  public boolean isPrivate () {
    return ChatId.isPrivate(getChatId());
  }

  public boolean isSecretChat () {
    return ChatId.isSecret(getChatId());
  }

  public boolean isGroup () {
    return ChatId.isBasicGroup(getChatId());
  }

  public boolean isChannel () {
    return ChatId.isSupergroup(getChatId()) && !tdlib.isSupergroupChat(chat);
  }

  public boolean showViews () {
    return !isSending() && isOutgoing() && isChannel() && chat.lastMessage != null && TD.getViewCount(chat.lastMessage.interactionInfo) > 0;
  }

  public boolean isSupergroup () {
    return ChatId.isSupergroup(getChatId()) && tdlib.isSupergroupChat(chat);
  }

  public boolean showMute () {
    return tdlib.chatNeedsMuteIcon(chat);
  }

  public boolean showVerify () {
    return (flags & FLAG_SHOW_VERIFY) != 0;
  }

  public boolean showScam () {
    return (flags & FLAG_SHOW_SCAM) != 0;
  }

  public boolean showFake () {
    return (flags & FLAG_SHOW_FAKE) != 0;
  }

  public boolean isSelfChat () {
    return (flags & FLAG_SELF_CHAT) != 0;
  }

  public int getCurrentWidth () {
    return currentWidth;
  }

  private void setCounter (boolean allowAnimation) {
    boolean hasReactions = hasUnreadReactions();
    boolean hasMentions = hasUnreadMentions();
    int unreadCount = getUnreadCount();

    reactionsCounter.setCount(hasReactions ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, !notificationsEnabled(), allowAnimation && needAnimateChanges());
    mentionCounter.setCount(hasMentions ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, false, allowAnimation && needAnimateChanges());
    counter.setCount(hasMentions && unreadCount == 1 ? 0 : unreadCount, !notificationsEnabled(), allowAnimation && needAnimateChanges());
  }

  public boolean needAnimateChanges () {
    View view = currentViews.findAnyTarget();
    ViewController<?> c = ViewController.findRoot(view);
    return view != null && (c == null || c.isAttachedToNavigationController());
  }

  public int getUnreadCount () {
    if (isFailed()) {
      return Tdlib.CHAT_FAILED;
    } else if (isArchive()) {
      return getTotalUnreadCount();
    } else if (getSource() != null) {
      return 0;
    } else {
      return chat.unreadCount > 0 ? chat.unreadCount : chat.isMarkedAsUnread ? Tdlib.CHAT_MARKED_AS_UNREAD : 0;
    }
  }

  public boolean hasUnreadReactions () {
    if (isArchive()) {
      return archive != null && archive.hasUnreadReactions();
    } else {
      return chat != null && chat.unreadReactionCount > 0;
    }
  }

  public boolean hasUnreadMentions () {
    if (isArchive()) {
      return archive.hasUnreadMentions();
    } else {
      return chat.unreadMentionCount > 0;
    }
  }

  public boolean hasScheduledMessages () {
    if (isArchive()) {
      return archive.hasScheduledMessages();
    } else {
      return chat.hasScheduledMessages;
    }
  }

  public boolean isFailed () {
    if (isArchive()) {
      return archive.hasFailedMessages();
    } else {
      return TD.isFailed(chat.lastMessage);
    }
  }

  public boolean isSending () {
    return chat != null && (tdlib.messageSending(chat.lastMessage) || tdlib.messageBeingEdited(chat.lastMessage) || tdlib.albumBeingEdited(currentPreview != null ? currentPreview.getAlbum() : null));
  }

  public boolean notificationsEnabled () {
    if (isArchive()) {
      TdlibCounter counter = tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE);
      return counter.chatUnmutedCount > 0;
    } else {
      return tdlib.chatNotificationsEnabled(chat);
    }
  }

  public boolean updateSecretChat (TdApi.SecretChat secretChat) {
    if (isSecretChat() && TD.getSecretChatId(chat) == secretChat.id) {
      if (chat.lastMessage == null && !showDraft()) {
        setText();
        return true;
      }
    }
    return false;
  }

  private int lastAvailWidth = -1;

  public void layoutTitle (boolean changed) {
    int avail = currentWidth - ChatView.getLeftPadding(listMode) - timeWidth - ChatView.getTimePaddingRight() - ChatView.getTimePaddingLeft();
    final boolean isSecret = isSecretChat();
    if (showMute()) {
      avail = avail - ChatView.getMuteOffset() - Icons.getChatMuteDrawableWidth();
    }
    boolean showVerify = tdlib.chatVerified(chat);
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_VERIFY, showVerify);
    if (showVerify) {
      avail = avail - Screen.dp(20f);
    }
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_SCAM, tdlib.chatScam(chat));
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_FAKE, tdlib.chatFake(chat));
    boolean showChatMark = showFake() || showScam();
    if (showChatMark) {
      chatMark = new Text.Builder(Lang.getString(showFake() ? R.string.FakeMark : R.string.ScamMark), avail, Paints.robotoStyleProvider(12f), TextColorSets.Regular.NEGATIVE)
        .singleLine()
        .allBold()
        .clipTextArea()
        .build();
      avail -= chatMark.getWidth() + (Screen.dp(4f) * 2);
    }
    if (showViews()) {
      avail -= viewCounter.getScaledWidth(Screen.dp(3f));
    } else if (isSending() || isOutgoing()) {
      avail = avail - ChatView.getTimePaddingLeft() - Screen.dp(20f);
    }
    if (isSecret) {
      avail = avail - Screen.dp(14f);
    }

    if (!tdlib.isSelfChat(chat)) {
      emojiStatusDrawable = EmojiStatusHelper.makeDrawable(null, tdlib, chat != null ? tdlib.chatUser(chat) : null, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
        @Override
        public int emojiStatusColor () {
          return Theme.getColor(ColorId.iconActive);
        }
      }, this::invalidateEmojiStatusReceiver);
      emojiStatusDrawable.invalidateTextMedia();
      avail -= emojiStatusDrawable.getWidth(Screen.dp(6));
    }

    if (changed || lastAvailWidth != avail) {
      lastAvailWidth = avail;
      if (StringUtils.isEmpty(title)) {
        trimmedTitle = null;
      } else {
        trimmedTitle = new Text.Builder(title, avail, getTitleStyleProvider(listMode), isSecretChat() ? TextColorSets.Regular.SECURE : TextColorSets.Regular.NORMAL)
          .singleLine()
          .allBold()
          .clipTextArea()
          .build();
      }
    }
    int titleWidth = getTitleWidth();
    verifyLeft = ChatView.getLeftPadding(listMode) + titleWidth;
    muteLeft = ChatView.getLeftPadding(listMode) + titleWidth + ChatView.getMutePadding();
    emojiStatusLeft = ChatView.getLeftPadding(listMode) + titleWidth + Screen.dp(3);
    if (emojiStatusDrawable != null) {
      muteLeft += emojiStatusDrawable.getWidth(Screen.dp(6));
      verifyLeft += emojiStatusDrawable.getWidth(Screen.dp(6));
    }
    if (showVerify) {
      muteLeft += Screen.dp(20f);
    }
    if (showChatMark) {
      muteLeft += chatMark.getWidth() + Screen.dp(14f);
    }
    if (isSecret) {
      verifyLeft += Screen.dp(14f);
      muteLeft += Screen.dp(14f);
    }
    if (changed && avatarPlaceholder != null) {
      setAvatar();
    }
  }

  public @Nullable EmojiStatusHelper.EmojiStatusDrawable getEmojiStatus () {
    return emojiStatusDrawable;
  }

  public void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia) {
    currentViews.performWithViews(view -> {
      if (view instanceof EmojiStatusHelper.EmojiStatusReceiverInvalidateDelegate) {
        ((EmojiStatusHelper.EmojiStatusReceiverInvalidateDelegate) view).invalidateEmojiStatusReceiver(text, specificMedia);
      }
    });
  }

  private void setTitleImpl (String title) {
    this.title = title;
  }

  public void setTitle () {
    setTitleImpl(isArchive() ? Lang.getString(R.string.ArchiveTitleList) : tdlib.chatTitle(chat));
    layoutTitle(true);
  }

  public Text getTitle () {
    return trimmedTitle;
  }

  public void setTime () {
    if (isArchive()) {
      int maxDate = archive.maxDate();
      time = maxDate != 0 ? Lang.timeOrDateShort(maxDate, TimeUnit.SECONDS) : "";
    } else {
      TdApi.ChatSource source = getSource();
      if (source != null) {
        switch (source.getConstructor()) {
          case TdApi.ChatSourceMtprotoProxy.CONSTRUCTOR:
            time = Lang.getString(R.string.ProxySponsor);
            break;
          case TdApi.ChatSourcePublicServiceAnnouncement.CONSTRUCTOR: {
            time = Lang.getPsaType(((TdApi.ChatSourcePublicServiceAnnouncement) source));
            break;
          }
        }
      } else {
        int date = chat.draftMessage != null && showDraft() ? chat.draftMessage.date : chat.lastMessage != null ? chat.lastMessage.date : 0;
        time = date != 0 ? Lang.timeOrDateShort(date, TimeUnit.SECONDS) : "";
      }
    }
    timeWidth = (int) U.measureText(time, ChatView.getTimePaint());
    layoutTime();
    setViews();
  }

  private int getViewCount () {
    return chat == null || !TD.isOut(chat.lastMessage) ? 0 : TD.getViewCount(chat.lastMessage.interactionInfo);
  }

  private void setViews () {
    if (viewCounter != null) {
      int count = getViewCount();
      viewCounter.setCount(count, needAnimateChanges());
    }
  }

  public void updateLocale (boolean forceText) {
    setTime();
    setCounter(true);
    if (forceText || (flags & FLAG_CONTENT_STRING) != 0 || (flags & FLAG_TEXT_DRAFT) != 0) {
      setText();
    }
    if ((flags & FLAG_SELF_CHAT) != 0) {
      setTitle();
    } else {
      layoutTitle(false);
    }
  }

  private void layoutTime () {
    timeLeft = currentWidth - ChatView.getTimePaddingRight() - timeWidth;
    checkRight = timeLeft - ChatView.getTimePaddingLeft();
  }

  public String getTime () {
    return time;
  }

  public int getTimeLeft () {
    return timeLeft;
  }

  public int getTitleWidth () {
    return trimmedTitle != null ? trimmedTitle.getWidth() : 0;
  }

  public int getTextWidth () {
    return trimmedText != null ? trimmedText.getWidth() : 0;
  }

  public int getTimeWidth () {
    return timeWidth;
  }

  public Counter getViewCounter () {
    return viewCounter;
  }

  public int getMuteLeft() {
    return muteLeft;
  }

  public int getVerifyLeft () {
    return verifyLeft;
  }

  public int getEmojiStatusLeft () {
    return emojiStatusLeft + (isSecretChat() ? Screen.dp(12) : 0);
  }

  public int getChecksRight () {
    return checkRight;
  }

  private int getCounterAddWidth () {
    return Math.round(
      counter.getScaledWidth(ChatView.getTimePaddingLeft()) +
      mentionCounter.getScaledWidth(ChatView.getTimePaddingLeft()) +
      reactionsCounter.getScaledWidth(ChatView.getTimePaddingLeft())
    );
  }

  public Counter getCounter () {
    return counter;
  }

  public Counter getMentionCounter () {
    return mentionCounter;
  }

  public Counter getReactionsCounter () {
    return reactionsCounter;
  }

  public BounceAnimator getScheduleAnimator () {
    return scheduleAnimator;
  }

  private void layoutText () {
    int avail = currentWidth - ChatView.getLeftPadding(listMode) - ChatView.getRightPadding() - (lastCounterAddWidth = getCounterAddWidth());
    textLeft = ChatView.getLeftPadding(listMode);
    if ((flags & FLAG_HAS_PREFIX) != 0 && prefix != null && listMode == Settings.CHAT_MODE_2LINE) {
      int prefixWidth = prefix.getWidth();
      avail -= prefixWidth;
      textLeft += prefixWidth;
    }

    this.textIconsPadding = textIconIds != null && textIconIds.size() > 0 ? Screen.dp(2f) + Screen.dp(18f) * textIconIds.size() : 0;

    if (avail > 0 && !StringUtils.isEmpty(text)) {
      trimmedText = new Text.Builder(text, avail, getTextStyleProvider(listMode), TextColorSets.Regular.LIGHT)
        .maxLineCount(isSingleLine() ? 1 : 2)
        .textFlags(Text.FLAG_ELLIPSIZE_NEWLINE)
        .ignoreContinuousNewLines()
        .ignoreNewLines(isSingleLine())
        .lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? textIconsPadding : 0)
        .viewProvider(currentViews)
        .entities(entities, (text, specificMedia) -> {
          if (this.trimmedText == text) {
            for (View view : currentViews) {
              ComplexReceiver receiver = ((ChatView) view).getTextMediaReceiver();
              if (!text.invalidateMediaContent(receiver, specificMedia)) {
                text.requestMedia(receiver);
              }
            }
          }
        })
        .noClickable()
        .build();
      currentViews.invalidateContent(this);
    } else {
      trimmedText = null;
    }

    layoutChatAction();
  }

  private int textIconsPadding;

  private static TextStyleProvider getTextStyleProvider (int chatListMode) {
    return Paints.robotoStyleProvider(chatListMode == Settings.CHAT_MODE_3LINE ? 15 : 16);
  }

  private static TextStyleProvider getTitleStyleProvider (int chatListMode) {
    return Paints.robotoStyleProvider(chatListMode == Settings.CHAT_MODE_3LINE ? 16 : 17);
  }

  public @Nullable IntList getTextIconIds () {
    return textIconIds;
  }

  public @PorterDuffColorId int getTextIconColorId () {
    return textIconColorId;
  }

  private void setPrefix () {
    if ((flags & FLAG_HAS_PREFIX) != 0) {
      final String prefix;
      boolean needSuffix = true;
      if (showDraft()) {
        TdApi.DraftMessage draftMessage = chat.draftMessage;
        needSuffix = draftMessage != null && draftMessage.inputMessageText.getConstructor() == TdApi.InputMessageText.CONSTRUCTOR && !Td.isEmpty(((TdApi.InputMessageText) chat.draftMessage.inputMessageText).text);
        prefix = Lang.getString(R.string.Draft);
        flags |= FLAG_CONTENT_STRING;
      } else if (isOutgoing()) {
        prefix = Lang.getString(listMode != Settings.CHAT_MODE_2LINE && tdlib.isMultiChat(chat) && Td.getSenderId(chat.lastMessage) == chat.id ? R.string.FromYouAnonymous : R.string.FromYou);
        flags |= FLAG_CONTENT_STRING;
      } else if (chat.lastMessage != null && chat.lastMessage.content.getConstructor() != TdApi.MessageProximityAlertTriggered.CONSTRUCTOR) {
        prefix = listMode == Settings.CHAT_MODE_2LINE && Td.getMessageAuthorId(chat.lastMessage) == chat.lastMessage.chatId && StringUtils.isEmpty(chat.lastMessage.authorSignature) ?
          Lang.getString(R.string.FromAnonymous) :
          tdlib.senderName(chat.lastMessage, false, listMode == Settings.CHAT_MODE_2LINE);
      } else {
        prefix = null;
      }

      if (!StringUtils.isEmpty(prefix)) {
        int avail;
        if (listMode != Settings.CHAT_MODE_2LINE) {
          avail = currentWidth - ChatView.getLeftPadding(listMode) - ChatView.getRightPadding() - getCounterAddWidth();
        } else {
          avail = Screen.dp(120f);
        }
        if (avail > 0) {
          Text.Builder b = new Text.Builder(prefix, avail, getTextStyleProvider(listMode), BitwiseUtils.hasFlag(flags, FLAG_TEXT_DRAFT) ? TextColorSets.Regular.NEGATIVE : TextColorSets.Regular.NORMAL)
            .singleLine()
            .textFlags(Text.FLAG_ELLIPSIZE_NO_FILL);
          if (needSuffix && listMode == Settings.CHAT_MODE_2LINE) {
            b.suffix(": ");
          }
          this.prefix = b.build();
        } else {
          this.prefix = null;
        }
      } else {
        this.prefix = null;
      }
    } else {
      this.prefix = null;
    }
    layoutText();
  }

  private void setTextValue (@StringRes int resId) {
    setTextValue(Lang.getString(resId), true);
  }

  private void setTextValue (String text, boolean isTranslatable) {
    setTextValue(text, (TextEntity[]) null, isTranslatable);
  }

  private boolean isSingleLine () {
    return listMode == Settings.CHAT_MODE_2LINE || (flags & FLAG_HAS_PREFIX) != 0;
  }

  private void setTextValue (String text, TextEntity[] entities, boolean isTranslatable) {
    this.text = text;
    this.entities = entities != null && entities.length > 0 ? entities : null;;
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_CONTENT_STRING, isTranslatable);
  }

  private void setTextValue (String text, TdApi.TextEntity[] entities, boolean isTranslatable) {
    setTextValue(text, TextEntity.valueOf(tdlib, text, entities, null), isTranslatable);
  }

  public void setText () {
    flags &= ~FLAG_HAS_PREFIX;
    flags &= ~FLAG_TEXT_DRAFT;
    flags &= ~FLAG_CONTENT_HIDDEN;
    flags &= ~FLAG_CONTENT_STRING;
    if (textIconIds != null) {
      textIconIds.clear();
    }
    currentPreview = null;

    if (tdlib.hasPasscode(chat)) {
      flags |= FLAG_CONTENT_HIDDEN;
      setContentPreview(new TD.ContentPreview(TD.EMOJI_LOCK, R.string.ChatContentProtected));
      return;
    }

    String restrictionReason = tdlib.chatRestrictionReason(chat);
    if (restrictionReason != null) {
      setContentPreview(new TD.ContentPreview(TD.EMOJI_ERROR, 0, restrictionReason, false));
      return;
    }

    if (isArchive()) {
      List<TextEntity> entities = new ArrayList<>();
      StringBuilder b = new StringBuilder();
      archive.iterate(chat -> {
        if (b.length() > 0) {
          b.append(Lang.getConcatSeparator());
        }
        int startIndex = b.length();
        b.append(tdlib.chatTitle(chat));
        if (chat.unreadCount > 0) {
          entities.add(new TextEntityCustom(context, tdlib, null, startIndex, b.length(), 0, null)
            .setCustomColorSet(TextColorSets.Regular.NORMAL)
          );
        }
      });
      if (b.length() == 0) {
        b.append(Lang.pluralBold(R.string.xChats, archive.totalCount()));
      }
      setTextValue(b.toString(), !entities.isEmpty() ? entities.toArray(new TextEntity[0]) : null, false);
      setPrefix();
      return;
    }

    if (chat.draftMessage != null && showDraft()) {
      flags |= FLAG_TEXT_DRAFT | FLAG_HAS_PREFIX;
      TdApi.FormattedText text = ((TdApi.InputMessageText) chat.draftMessage.inputMessageText).text;
      setTextValue(text.text, text.entities, false);
      setPrefix();
      return;
    }

    TdApi.ChatSource source = getSource();
    String psaText = source instanceof TdApi.ChatSourcePublicServiceAnnouncement ? ((TdApi.ChatSourcePublicServiceAnnouncement) source).text : null;
    if (!StringUtils.isEmpty(psaText)) {
      setContentPreview(new TD.ContentPreview(TD.EMOJI_INFO, 0, psaText, false));
      return;
    }

    if (chat.lastMessage == null && ChatId.isSecret(chat.id)) {
      boolean specialState = true;
      TdApi.SecretChat secretChat = tdlib.chatToSecretChat(chat.id);
      if (secretChat != null) {
        switch (secretChat.state.getConstructor()) {
          case TdApi.SecretChatStatePending.CONSTRUCTOR: { // not yet started
            setTextValue(secretChat.isOutbound ? Lang.getString(R.string.AwaitingEncryption, tdlib.cache().userFirstName(secretChat.userId)) : Lang.getString(R.string.VoipExchangingKeys), true);
            break;
          }
          case TdApi.SecretChatStateReady.CONSTRUCTOR: { // active
            if (secretChat.isOutbound)
              setTextValue(Lang.getString(R.string.XJoinedSecretChat, tdlib.cache().userFirstName(secretChat.userId)), true);
            else
              setTextValue(Lang.getString(R.string.YouJoinedSecretChat), true);
            break;
          }
          case TdApi.SecretChatStateClosed.CONSTRUCTOR: {
            setTextValue(R.string.SecretChatCancelled);
            break;
          }
          default:
            throw new RuntimeException();
        }
      } else {
        specialState = false;
      }

      if (specialState) {
        setPrefix();
        return;
      }
    }

    TdApi.Message msg = chat.lastMessage;
    if (msg != null) {
      // No need to check tdlib.chatRestrictionReason, because it's already handled above
      TD.ContentPreview preview = TD.getChatListPreview(tdlib, msg.chatId, msg, false);
      setContentPreview(preview);
    } else {
      setTextValue(R.string.DeletedMessage);
      setPrefix();
    }
  }

  private void addIcon (int icon) {
    if (icon != 0) {
      if (textIconIds == null)
        textIconIds = new IntList(2);
      textIconIds.append(icon);
    }
  }

  private void setContentPreview (TD.ContentPreview preview) {
    if (textIconIds != null)
      textIconIds.clear();
    setTextValue(preview.buildText(true), preview.formattedText != null ? preview.formattedText.entities : null, preview.isTranslatable);
    this.currentPreview = preview;
    if (preview.parentEmoji != null) {
      addIcon(preview.parentEmoji.iconRepresentation);
    } else if (chat.lastMessage != null && chat.lastMessage.forwardInfo != null && (chat.lastMessage.isChannelPost || getPrefixIconCount() == 0)) {
      TdApi.MessageForwardInfo forwardInfo = chat.lastMessage.forwardInfo;
      switch (forwardInfo.origin.getConstructor()) {
        case TdApi.MessageForwardOriginChannel.CONSTRUCTOR:
          if (chat.id != ((TdApi.MessageForwardOriginChannel) forwardInfo.origin).chatId) {
            addIcon(R.drawable.baseline_share_arrow_16);
          }
          break;
        case TdApi.MessageForwardOriginHiddenUser.CONSTRUCTOR:
          addIcon(R.drawable.baseline_share_arrow_16);
          break;
        case TdApi.MessageForwardOriginUser.CONSTRUCTOR:
          if (Td.getSenderUserId(chat.lastMessage) != ((TdApi.MessageForwardOriginUser) forwardInfo.origin).senderUserId)
            addIcon(R.drawable.baseline_share_arrow_16);
          break;
        case TdApi.MessageForwardOriginChat.CONSTRUCTOR:
          if (Td.getSenderId(chat.lastMessage) != ((TdApi.MessageForwardOriginChat) forwardInfo.origin).senderChatId)
            addIcon(R.drawable.baseline_share_arrow_16);
          break;
        case TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR:
          addIcon(R.drawable.templarian_baseline_import_16);
          break;
      }
    }
    if (preview.emoji != null) {
      addIcon(preview.emoji.iconRepresentation);
    }
    this.textIconColorId = ColorId.chatListIcon;

    if ((isGroup() || isSupergroup()) && !preview.hideAuthor) {
      flags |= FLAG_HAS_PREFIX;
    } else if (chat.lastMessage != null && chat.lastMessage.content.getConstructor() == TdApi.MessageCall.CONSTRUCTOR) {
      if (textIconIds != null)
        textIconIds.clear();
      addIcon(CallItem.getSubtitleIcon((TdApi.MessageCall) chat.lastMessage.content, TD.isOut(chat.lastMessage)));
      textIconColorId = CallItem.getSubtitleIconColorId((TdApi.MessageCall) chat.lastMessage.content);
    }

    setPrefix();

    if (preview.hasRefresher()) {
      preview.refreshContent(this);
    }
  }

  @Override
  public void onContentPreviewChanged (long chatId, long messageId, TD.ContentPreview newPreview, TD.ContentPreview oldPreview) {
    tdlib.ui().post(() -> {
      if (currentPreview == oldPreview) {
        setContentPreview(newPreview);
        currentViews.invalidate();
      }
    });
  }

  public int getPrefixIconCount () {
    return textIconIds != null ? textIconIds.size() : 0;
  }

  public Text getPrefix () {
    return (flags & FLAG_HAS_PREFIX) != 0 ? prefix : null;
  }

  @Nullable
  public Text getText () {
    return trimmedText;
  }

  @Nullable
  public Text getChatMark () {
    return chatMark;
  }

  public int getTextLeft () {
    return textLeft;
  }

  // Typing utils

  @Nullable
  private final TdlibStatusManager.Helper statusHelper;

  public void attachToView (View view) {
    if (currentViews.attachToView(view)) {
      setViewAttached(true);
    }
  }

  public void detachFromView (View view) {
    if (currentViews.detachFromView(view)) {
      setViewAttached(currentViews.hasAnyTargetToInvalidate());
    }
  }

  private void setViewAttached (boolean isAttached) {
    boolean nowIsAttached = (flags & FLAG_ATTACHED) != 0;
    if (isAttached != nowIsAttached) {
      flags = BitwiseUtils.setFlag(flags, FLAG_ATTACHED, isAttached);
      if (statusHelper != null) {
        if (isAttached) {
          statusHelper.attachToChat(chat.id, 0);
        } else {
          statusHelper.detachFromAnyChat();
        }
      }
    }
  }

  @Nullable
  public final TdlibStatusManager.Helper statusHelper () {
    return statusHelper;
  }

  @Override
  public void layoutChatAction () {
    if (statusHelper == null)
      return;
    String chatActionText = statusHelper.fullText();
    Text trimmedChatAction;
    if (StringUtils.isEmpty(chatActionText)) {
      trimmedChatAction = null;
    } else {
      int avail = currentWidth - ChatView.getLeftPadding(listMode) - ChatView.getRightPadding() - getCounterAddWidth();
      int iconWidth = statusHelper.actionIconWidth();
      if (avail > 0) {
        Text.Builder b = new Text.Builder(chatActionText, avail, getTextStyleProvider(listMode), TextColorSets.Regular.LIGHT).maxLineCount(listMode == Settings.CHAT_MODE_2LINE ? 1 : 2);
        if (iconWidth > 0) {
          b.lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? iconWidth : 0);
        }
        trimmedChatAction = b.build();
      } else {
        trimmedChatAction = null;
      }
    }
    statusHelper.setDrawingText(trimmedChatAction);
  }

  @Override
  public boolean canLoop () {
    return BitwiseUtils.hasFlag(flags, FLAG_ATTACHED);
  }

  @Override
  public void invalidateTypingPart (boolean onlyIcon) {
    ReferenceList<View> views = currentViews.getViewsList();
    for (View view : views) {
      ((ChatView) view).invalidateTypingPart(onlyIcon);
    }
  }

  @Override
  public boolean canAnimate () {
    return ((flags & FLAG_ATTACHED) != 0) && currentViews.hasAnyTargetToInvalidate() && context.getParentOrSelf().getAttachState();
  }

  @Override
  public void performDestroy () {
    currentViews.detachFromAllViews();
    setViewAttached(false);
  }
}
