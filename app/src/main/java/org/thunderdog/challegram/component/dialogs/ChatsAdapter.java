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
 * File created on 26/04/2015 at 12:55
 */
package org.thunderdog.challegram.component.dialogs;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.v.ChatsRecyclerView;
import org.thunderdog.challegram.widget.SuggestedChatsView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import me.vkryl.core.ArrayUtils;
import tgx.td.ChatPosition;

public class ChatsAdapter extends RecyclerView.Adapter<ChatsViewHolder> {

  private static final int ARCHIVE_INDEX = 0;

  private final ChatsController context;

  private final ArrayList<TGChat> chats;
  private int totalRes = R.string.xChats;

  private final LinearLayoutManager layoutManager;

  public ChatsAdapter (ChatsController context, LinearLayoutManager layoutManager) {
    this.context = context;
    this.layoutManager = layoutManager;
    this.chats = new ArrayList<>();
  }

  public void checkChatListMode () {
    for (TGChat chat : chats) {
      chat.checkChatListMode();
    }
  }

  public ArrayList<TGChat> getChats () {
    return chats;
  }

  public void setTotalRes (@StringRes int totalRes) {
    this.totalRes = totalRes;
  }

  @NonNull
  @Override
  public ChatsViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return ChatsViewHolder.create(context.context(), context.tdlib(), viewType, context.isInForceTouchMode() ? null : context, context, context);
  }

  private long[] suggestedChatIds = ArrayUtils.EMPTY_LONGS;

  public void setSuggestedChatIds (long[] chatIds) {
    chatIds = chatIds != null ? chatIds : ArrayUtils.EMPTY_LONGS;
    boolean notifyInserted = !hasSuggestedChats() && chatIds.length > 0;
    boolean notifyRemoved = hasSuggestedChats() && chatIds.length == 0;
    boolean notifyChanged = hasSuggestedChats() && chatIds.length > 0 && !Arrays.equals(suggestedChatIds, chatIds);
    this.suggestedChatIds = chatIds;
    if (notifyInserted) {
      notifyItemInserted(0);
    } else if (notifyRemoved) {
      notifyItemRemoved(0);
    } else if (notifyChanged) {
      notifyItemChanged(0);
    }
  }

  public long[] getSuggestedChatIds () {
    return suggestedChatIds;
  }

  private boolean needArchive, hasArchive;

  public void setNeedArchive (boolean needArchive) {
    if (this.needArchive != needArchive) {
      this.needArchive = needArchive;
      checkArchive();
    }
  }

  public boolean hasArchive () {
    return hasArchive;
  }

  public void checkArchive () {
    boolean hasArchive = this.needArchive && context.isInitialLoadFinished() && (chats.size() - (this.hasArchive ? 1 : 0)) > 0;
    if (this.hasArchive != hasArchive) {
      this.hasArchive = hasArchive;
      if (hasArchive) {
        chats.add(ARCHIVE_INDEX, newArchive());
        notifyChatAppeared(-1, getItemPositionByChatIndex(ARCHIVE_INDEX));
      } else {
        chats.remove(ARCHIVE_INDEX);
        notifyItemRemoved(getItemPositionByChatIndex(ARCHIVE_INDEX));
      }
      invalidateAttachedItemDecorations();
    }
  }

  private TGChat newArchive () {
    return new TGChat(context, context.tdlib().chatList(ChatPosition.CHAT_LIST_ARCHIVE), true);
  }

  public static final int ARCHIVE_UPDATE_ALL = 0;
  public static final int ARCHIVE_UPDATE_COUNTER = 1;
  public static final int ARCHIVE_UPDATE_MESSAGE = 2;

  public int updateArchive (int reason) {
    if (this.hasArchive) {
      TGChat chat = getChatAt(ARCHIVE_INDEX);
      if (chat != null && chat.isArchive()) {
        switch (reason) {
          case ARCHIVE_UPDATE_COUNTER:
            chat.onArchiveCounterChanged();
            break;
          case ARCHIVE_UPDATE_MESSAGE:
            chat.onArchiveMessageChanged();
            break;
          case ARCHIVE_UPDATE_ALL:
          default:
            chat.onArchiveChanged();
            break;
        }
        return getItemPositionByChatIndex(ARCHIVE_INDEX);
      }
    }
    return -1;
  }

  @Override
  public void onViewRecycled (ChatsViewHolder holder) {
    if (holder.getItemViewType() == VIEW_TYPE_CHAT) {
      ((ChatView) holder.itemView).setChat(null);
    } else if (holder.getItemViewType() == VIEW_TYPE_SUGGESTED_CHATS) {
      ((SuggestedChatsView) holder.itemView).setChatIds(ArrayUtils.EMPTY_LONGS, false);
    }
  }

  @Override
  public void onBindViewHolder (@NonNull ChatsViewHolder holder, int position) {
    int viewType = getItemViewType(position);
    switch (viewType) {
      case VIEW_TYPE_CHAT: {
        TGChat currentChat = getChatByItemPosition(position);
        TGChat nextChat = getChatByItemPosition(position + 1);
        holder.setChat(currentChat, false, nextChat != null && currentChat.isPinnedOrSpecial() && !nextChat.isPinnedOrSpecial(), context.isChatSelected(currentChat));
        break;
      }
      case VIEW_TYPE_INFO: {
        if (!context.isEndReached()) {
          holder.setInfo(null);
        } else if (chats.size() == 0) {
          holder.setEmpty(R.string.NoChats);
        } else {
          holder.setInfo(Lang.pluralBold(totalRes != 0 ? totalRes : R.string.xChats, hasArchive ? chats.size() - 1 : chats.size()));
        }
        break;
      }
      case VIEW_TYPE_EMPTY: {
        ((TextView) holder.itemView).setText(context.isEndReached() ? Lang.getString(R.string.NoChats) : "");
        break;
      }
      case VIEW_TYPE_SUGGESTED_CHATS: {
        holder.setChatIds(suggestedChatIds);
        break;
      }
    }
  }

  @Override
  public void onViewAttachedToWindow (ChatsViewHolder holder) {
    final int viewType = holder.getItemViewType();
    switch (viewType) {
      case VIEW_TYPE_CHAT: {
        ((ChatView) holder.itemView).attach();
        break;
      }
      case VIEW_TYPE_INFO: {
        break;
      }
      case VIEW_TYPE_SUGGESTED_CHATS: {
        ((SuggestedChatsView) holder.itemView).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (ChatsViewHolder holder) {
    final int viewType = holder.getItemViewType();
    switch (viewType) {
      case VIEW_TYPE_CHAT: {
        ((ChatView) holder.itemView).detach();
        break;
      }
      case VIEW_TYPE_INFO: {
        break;
      }
      case VIEW_TYPE_SUGGESTED_CHATS: {
        ((SuggestedChatsView) holder.itemView).detach();
        break;
      }
    }
  }

  @Override
  public int getItemCount () {
    int itemCount = 0;
    if (hasSuggestedChats()) {
      itemCount++;
    }
    if (hasChats()) {
      itemCount += chats.size() + 1;
    }
    return itemCount;
  }

  public int getChatCount () {
    return chats.size();
  }

  public boolean hasChats () {
    return !chats.isEmpty();
  }

  public boolean hasSuggestedChats () {
    return suggestedChatIds.length > 0;
  }

  @IntDef({VIEW_TYPE_CHAT, VIEW_TYPE_INFO, VIEW_TYPE_EMPTY, VIEW_TYPE_SUGGESTED_CHATS})
  public @interface ViewType {
  }

  public static final int VIEW_TYPE_CHAT = 0;
  public static final int VIEW_TYPE_INFO = 1;
  public static final int VIEW_TYPE_EMPTY = 2;
  public static final int VIEW_TYPE_SUGGESTED_CHATS = 3;

  @Override
  public int getItemViewType (int position) {
    if (hasSuggestedChats() && position == 0) {
      return VIEW_TYPE_SUGGESTED_CHATS;
    }
    if (hasChats()) {
      int chatIndex = getChatIndexByItemPosition(position);
      if (chatIndex >= 0 && chatIndex < chats.size()) {
        return VIEW_TYPE_CHAT;
      }
      if (chatIndex == chats.size()) {
        return VIEW_TYPE_INFO;
      }
      throw new IllegalArgumentException("position = " + position);
    }
    return VIEW_TYPE_EMPTY;
  }

  public void updateInfo () {
    if (hasChats()) {
      notifyItemChanged(getInfoItemPosition());
    }
  }

  private TGChat getChatAt (int index) {
    return index >= 0 && index < chats.size() ? chats.get(index) : null;
  }

  public int getFirstChatItemPosition () {
    return hasChats() ? getItemPositionByChatIndex(0) : -1;
  }

  public int getLastChatItemPosition () {
    return hasChats() ? getItemPositionByChatIndex(getChatCount() - 1) : -1;
  }

  public int getArchiveItemPosition () {
    return hasChats() && hasArchive() ? getItemPositionByChatIndex(ARCHIVE_INDEX) : -1;
  }

  public int getInfoItemPosition () {
    return hasChats() ? getItemPositionByChatIndex(getChatCount()) : -1;
  }

  public int getItemPositionByChatIndex (int chatIndex) {
    if (chatIndex == -1) {
      return -1;
    }
    return hasSuggestedChats() ? chatIndex + 1 : chatIndex;
  }

  public int getChatIndexByItemPosition (int itemPosition) {
    if (itemPosition == RecyclerView.NO_POSITION) {
      return -1;
    }
    return hasSuggestedChats() ? itemPosition - 1 : itemPosition;
  }

  public int findChatItemPosition (long chatId) {
    return getItemPositionByChatIndex(indexOfChat(chatId));
  }

  public TGChat getChatByItemPosition (int itemPosition) {
    int index = getChatIndexByItemPosition(itemPosition);
    return index != -1 ? getChatAt(index) : null;
  }

  private int indexOfChat (long chatId) {
    if (chatId == 0)
      return -1;
    int i = 0;
    for (TGChat chat : chats) {
      if (chat.getChatId() == chatId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private int indexOfSecretChat (int secretChatId) {
    int i = 0;
    for (TGChat chat : chats) {
      if (chat.isSecretChat() && chat.getSecretChatId() == secretChatId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public void addMore (TGChat[] data) {
    if (data.length == 0)
      return;
    int addedItemCount = 0;
    int atIndex = chats.size();
    TGChat lastChat = getChatAt(atIndex - 1);
    if (atIndex == 0 && needArchive) {
      chats.ensureCapacity(atIndex + data.length + 1);
      chats.add(newArchive());
      this.hasArchive = true;
      addedItemCount++;
    } else {
      chats.ensureCapacity(atIndex + data.length);
    }
    TGChat firstAddedItem = null;
    for (TGChat chat : data) {
      Long id = chat.getChatId();
      if (presentChatIds.add(id)) {
        firstAddedItem = chat;
        chats.add(chat);
        addedItemCount++;
      }
    }
    if (addedItemCount > 0) {
      int positionStart = getItemPositionByChatIndex(atIndex);
      notifyItemRangeInserted(positionStart, addedItemCount);
      if (firstAddedItem != null && lastChat != null && lastChat.isPinnedOrSpecial() != firstAddedItem.isPinnedOrSpecial()) {
        notifyItemChanged(positionStart - 1);
      }
    }
  }

  public int updateMessageInteractionInfo (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMessageInteractionInfo(chatId, messageId, interactionInfo)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateMessageContent (long chatId, long messageId, TdApi.MessageContent newContent) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMessageContent(chatId, messageId, newContent)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateMessagesDeleted (long chatId, long[] messageIds) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMessagesDeleted(chatId, messageIds)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatReadInbox (long chatId, final long lastReadInboxMessageId, final int unreadCount) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatReadInbox(chatId, lastReadInboxMessageId, unreadCount)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatUnreadReactionCount (long chatId, int unreadReactionCount) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatUnreadReactionCount(chatId, unreadReactionCount)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatUnreadMentionCount (long chatId, int unreadMentionCount) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatUnreadMentionCount(chatId, unreadMentionCount)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatHasScheduledMessages (long chatId, boolean hasScheduledMessages) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatHasScheduledMessages(chatId, hasScheduledMessages)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatDraftMessage (long chatId, TdApi.DraftMessage draftMessage) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateDraftMessage(chatId, draftMessage)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatReadOutbox (long chatId, final long lastReadOutboxMessageId) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatReadOutbox(chatId, lastReadOutboxMessageId)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public void updateUser (ChatsRecyclerView recyclerView, TdApi.User user) {
    int i = 0;
    for (TGChat chat : chats) {
      if (chat.updateUser(user) || chat.checkOnline()) {
        recyclerView.invalidateViewAt(getItemPositionByChatIndex(i));
      }
      i++;
    }
  }

  public int updateUserStatus (long userId, int startIndex) {
    if (startIndex == 0) {
      int index = 0;
      for (TGChat chat : chats) {
        if (chat.getChatUserId() == userId && chat.checkOnline())
          return getItemPositionByChatIndex(index);
        index++;
      }
    } else {
      for (int index = startIndex; index < chats.size(); index++) {
        TGChat chat = chats.get(index);
        if (chat.getChatUserId() == userId && chat.checkOnline()) {
          return getItemPositionByChatIndex(index);
        }
      }
    }
    return -1;
  }

  public int updateChatTitle (long chatId, String title) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatTitle(chatId, title)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatPermissions (long chatId, TdApi.ChatPermissions permissions) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatPermissions(chatId, permissions)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatClientData (long chatId, String clientData) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatClientData(chatId, clientData)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMarkedAsUnread(chatId, isMarkedAsUnread)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatTopMessage (long chatId, TdApi.Message topMessage) {
    int index = indexOfChat(chatId);
    if (index != -1) {
      chats.get(index).updateTopMessage(chatId, topMessage);
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public boolean canDragPinnedChats () {
    return !context.isFiltered() && getHeaderChatCount(false, null) >= 2;
  }

  public int getPinnedChatCount (boolean secret) {
    int count = 0;
    for (TGChat chat : chats) {
      if (chat.isPinned()) {
        if (secret == chat.isSecretChat())
          count++;
        continue;
      }
      if (chat.isArchive() || chat.isPinnedOrSpecial())
        continue;
      break;
    }
    return count;
  }

  public int getHeaderChatCount (boolean includeSponsor, @Nullable ArrayList<Long> pinnedChatIds) {
    int count = 0;
    for (TGChat chat : chats) {
      if (chat.isArchive() || chat.isPinnedOrSpecial()) {
        count++;
        if (chat.isPinned() && pinnedChatIds != null)
          pinnedChatIds.add(chat.getChatId());
      } else {
        break;
      }
    }
    return count;
  }

  public void movePinnedChat (int fromPosition, int toPosition) {
    TGChat fromChat = getChatByItemPosition(fromPosition);
    TGChat toChat = getChatByItemPosition(toPosition);
    if (fromChat == null || !fromChat.isPinned() || toChat == null || !toChat.isPinned()) {
      return;
    }
    ArrayList<Long> chatIds = new ArrayList<>();
    int promotedCount = getHeaderChatCount(true, chatIds);
    promotedCount -= chatIds.size();
    int fromIndex = getChatIndexByItemPosition(fromPosition);
    int toIndex = getChatIndexByItemPosition(toPosition);
    ArrayUtils.move(chatIds, fromIndex - promotedCount, toIndex - promotedCount);
    context.tdlib().client().send(new TdApi.SetPinnedChats(context.chatList(), ArrayUtils.asArray(chatIds)), context.tdlib().okHandler());
  }

  public void savePinnedChats () { }

  public int refreshLastMessage (long chatId, long messageId, boolean needRebuild) {
    int index = indexOfChat(chatId);
    if (index != -1) {
      TGChat chat = chats.get(index);
      if (chat.checkLastMessageId(messageId)) {
        if (needRebuild) {
          chat.setText();
        }
        return getItemPositionByChatIndex(index);
      }
    }
    return -1;
  }

  public int updateMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    int index = indexOfChat(message.chatId);
    if (index != -1 && chats.get(index).updateMessageSendSucceeded(message, oldMessageId)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public static final Comparator<TGChat> CHAT_COMPARATOR = (o1, o2) -> {
    long order1 = o1.getChatOrder();
    long order2 = o2.getChatOrder();

    long id1 = o1.getChatId();
    long id2 = o2.getChatId();

    return order1 != order2 ? Long.compare(order2, order1) : Long.compare(id2, id1);
  };

  public static final int ORDER_REMAIN_SCROLL = 0x01;
  public static final int ORDER_INVALIDATE_DECORATIONS = 0x02;

  private final Set<Long> presentChatIds = new HashSet<>();

  private void addChat (int atIndex, TGChat chat) {
    Long id = chat.getChatId();
    if (atIndex == -1) {
      chats.add(chat);
    } else {
      chats.add(atIndex, chat);
    }
    if (!presentChatIds.add(id)) {
      throw new IllegalStateException("Chat is already present in the list");
    }
  }

  private TGChat removeChat (int fromIndex) {
    TGChat removedChat = chats.remove(fromIndex);
    presentChatIds.remove(removedChat.getChatId());
    return removedChat;
  }

  private boolean needShadowDecoration (TGChat a, TGChat b) {
    return a != null && b != null && (a.isArchive() != b.isArchive() || a.isPinnedOrSpecial() != b.isPinnedOrSpecial());
  }

  public int removeChatById (TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    final int chatIndex = hasArchive ? fromIndex + 1 : fromIndex;
    if (chatIndex != -1) {
      TGChat removedChat = removeChat(chatIndex);
      if (removedChat.getChatId() != chat.id)
        throw new IllegalStateException();

      TGChat prevChat = getChatAt(chatIndex - 1);
      TGChat nextChat = getChatAt(chatIndex);

      boolean invalidateDecorations = changeInfo.metadataChanged() ||
        needShadowDecoration(prevChat, removedChat) ||
        needShadowDecoration(nextChat, removedChat);

      removedChat.updateChatPosition(chat.id, changeInfo.position, changeInfo.sourceChanged(), changeInfo.pinStateChanged());
      notifyItemRemoved(getItemPositionByChatIndex(chatIndex));
      notifyItemChanged(getInfoItemPosition());
      context.checkListState();
      return invalidateDecorations || needShadowDecoration(prevChat, nextChat) ? ORDER_INVALIDATE_DECORATIONS : 0;
    }
    return 0;
  }

  public int moveChat (TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    TGChat parsedChat;
    if (hasArchive) {
      fromIndex++;
      toIndex++;
    }
    parsedChat = chats.remove(fromIndex);

    boolean invalidateDecorations = changeInfo.metadataChanged() ||
      needShadowDecoration(getChatAt(fromIndex - 1), parsedChat) ||
      needShadowDecoration(getChatAt(fromIndex), parsedChat) ||
      needShadowDecoration(getChatAt(fromIndex - 1), getChatAt(fromIndex)) ||
      needShadowDecoration(getChatAt(toIndex), getChatAt(toIndex - 1));

    parsedChat.updateChatPosition(chat.id, changeInfo.position, changeInfo.sourceChanged(), changeInfo.pinStateChanged());

    chats.add(toIndex, parsedChat);
    if (!invalidateDecorations) {
      invalidateDecorations = needShadowDecoration(getChatAt(toIndex - 1), parsedChat) ||
        needShadowDecoration(getChatAt(toIndex + 1), parsedChat);
    }
    int flags = invalidateDecorations ? ORDER_INVALIDATE_DECORATIONS : 0;
    if (fromIndex != toIndex) {
      flags |= ORDER_REMAIN_SCROLL;
      int fromPosition = getItemPositionByChatIndex(fromIndex);
      int toPosition = getItemPositionByChatIndex(toIndex);
      notifyChatAppeared(fromPosition, toPosition);
    }
    return flags;
  }

  public int addChat (TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    TGChat newChat = new TGChat(context.getParentOrSelf(), context.chatList(), chat, false);
    int index = hasArchive ? atIndex + 1 : atIndex;
    newChat.makeMeasures();
    int flags = changeInfo.metadataChanged() ? ORDER_INVALIDATE_DECORATIONS : 0;
    addChat(index, newChat);
    notifyChatAppeared(-1, getItemPositionByChatIndex(index));
    notifyItemChanged(getInfoItemPosition());
    context.checkListState();
    return flags;
  }

  public int updateChat (TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) {
    final int chatIndex = hasArchive ? index + 1 : index;
    TGChat parsedChat = chats.get(chatIndex);
    if (parsedChat.getChatId() != chat.id)
      throw new IllegalStateException();
    parsedChat.updateChatPosition(chat.id, changeInfo.position, changeInfo.sourceChanged(), changeInfo.pinStateChanged());
    return changeInfo.metadataChanged() ? ORDER_INVALIDATE_DECORATIONS : 0;
  }

  public int updateChatPhoto (long chatId, TdApi.ChatPhotoInfo photo) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatPhoto(chatId, photo)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public int updateChatSettings (long chatId, final TdApi.ChatNotificationSettings settings) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatSettings(chatId, settings)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public void updateNotificationSettings (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {

  }

  public int updateSecretChat (TdApi.SecretChat secretChat) {
    int index = indexOfSecretChat(secretChat.id);
    if (index != -1 && chats.get(index).updateSecretChat(secretChat)) {
      return getItemPositionByChatIndex(index);
    }
    return -1;
  }

  public void updateRelativeDate () {
    for (TGChat chat : chats) {
      chat.updateDate();
    }
  }

  public void updateLocale (boolean forceText) {
    for (TGChat chat : chats) {
      chat.updateLocale(forceText);
    }
  }

  public TGChat getLastChat () {
    return chats.size() > 0 ? chats.get(chats.size() - 1) : null;
  }

  // Adapter state

  private void notifyChatAppeared (int fromPosition, int toPosition) {
    int firstItem = layoutManager.findFirstVisibleItemPosition();
    int offset;
    if (firstItem != -1) {
      View v = layoutManager.findViewByPosition(firstItem);
      offset = v != null ? layoutManager.getDecoratedTop(v) : 0;
    } else {
      offset = 0;
    }
    if (fromPosition == -1) {
      notifyItemInserted(toPosition);
    } else {
      notifyItemMoved(fromPosition, toPosition);
    }
    if (firstItem != -1) {
      layoutManager.scrollToPositionWithOffset(firstItem, offset);
    }
  }

  public void notifyAllChanged () {
    notifyItemRangeChanged(0, getItemCount());
  }

  public void notifyLastItemChanged () {
    int itemCount = getItemCount();
    if (itemCount > 0) {
      notifyItemChanged(itemCount - 1);
    }
  }

  // Search mode

  private ArrayList<RecyclerView> attachedToRecyclers;

  @Override
  public void onAttachedToRecyclerView (@NonNull RecyclerView recyclerView) {
    if (attachedToRecyclers == null) {
      attachedToRecyclers = new ArrayList<>();
    }
    attachedToRecyclers.add(recyclerView);
  }

  @Override
  public void onDetachedFromRecyclerView (@NonNull RecyclerView recyclerView) {
    if (attachedToRecyclers != null) {
      attachedToRecyclers.remove(recyclerView);
    }
  }

  public void invalidateAttachedItemDecorations () {
    if (attachedToRecyclers != null) {
      for (RecyclerView view : attachedToRecyclers) {
        view.invalidateItemDecorations();
      }
    }
  }
}
