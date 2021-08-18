/**
 * File created on 26/04/15 at 12:55
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.dialogs;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.v.ChatsRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import me.vkryl.core.ArrayUtils;

public class ChatsAdapter extends RecyclerView.Adapter<ChatsViewHolder> {
  private ChatsController context;

  private final ArrayList<TGChat> chats;
  private boolean canLoadMore;
  private boolean finishReached;
  private int totalRes = R.string.xChats;

  private LinearLayoutManager layoutManager;

  public ChatsAdapter (ChatsController context, LinearLayoutManager layoutManager) {
    this.context = context;
    this.layoutManager = layoutManager;
    this.chats = new ArrayList<>();
    canLoadMore = true;
  }

  public void checkChatListMode () {
    for (TGChat chat : chats) {
      chat.checkChatListMode();
    }
  }

  public ArrayList<TGChat> getChats () {
    return chats;
  }

  public boolean isAbleToLoadMore () {
    return canLoadMore;
  }

  public void setTotalRes (@StringRes int totalRes) {
    this.totalRes = totalRes;
  }

  @Override
  public ChatsViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return ChatsViewHolder.create(context.context(), context.tdlib(), viewType, context.isInForceTouchMode() ? null : context, context, context);
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
    boolean hasArchive = this.needArchive && !context.inInitialLoading() && (chats.size() - (this.hasArchive ? 1 : 0)) > 0;
    if (this.hasArchive != hasArchive) {
      this.hasArchive = hasArchive;
      if (hasArchive) {
        chats.add(0, newArchive());
        notifyChatAppeared(-1);
        invalidateAttachedItemDecorations();
      } else {
        chats.remove(0);
        notifyItemRemoved(0);
        invalidateAttachedItemDecorations();
      }
    }
  }

  private TGChat newArchive () {
    return new TGChat(context, context.archiveManager(), true);
  }

  public static final int ARCHIVE_UPDATE_ALL = 0;
  public static final int ARCHIVE_UPDATE_COUNTER = 1;
  public static final int ARCHIVE_UPDATE_MESSAGE = 2;

  public int updateArchive (int archiveUpdateReason) {
    if (this.hasArchive) {
      TGChat chat = getChatAt(0);
      if (chat != null && chat.isArchive()) {
        switch (archiveUpdateReason) {
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
        return 0;
      }
    }
    return -1;
  }

  @Override
  public void onViewRecycled (ChatsViewHolder holder) {
    if (holder.getItemViewType() == VIEW_TYPE_CHAT) {
      ((ChatView) holder.itemView).setChat(null);
    }
  }

  @Override
  public void onBindViewHolder (ChatsViewHolder holder, int position) {
    int viewType = getItemViewType(position);
    switch (viewType) {
      case VIEW_TYPE_CHAT: {
        TGChat currentChat = chats.get(position);
        TGChat nextChat = position + 1 < chats.size() ? chats.get(position + 1) : null;
        holder.setChat(currentChat, false, nextChat != null && currentChat.isPinnedOrSpecial() && !nextChat.isPinnedOrSpecial(), context.isChatSelected(currentChat));
        break;
      }
      case VIEW_TYPE_INFO: {
        if (canLoadMore) {
          holder.setInfo(null);
        } else if (chats.size() == 0) {
          holder.setEmpty(R.string.NoChats);
        } else {
          holder.setInfo(Lang.pluralBold(totalRes != 0 ? totalRes : R.string.xChats, hasArchive ? chats.size() - 1 : chats.size()));
        }
        break;
      }
      case VIEW_TYPE_EMPTY: {
        ((TextView) holder.itemView).setText(canLoadMore ? "" : Lang.getString(R.string.NoChats));
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
    }
  }

  @Override
  public int getItemCount () {
    return chats.isEmpty()/* && canLoadMore*/ ? 0 : chats.size() + 1;
  }

  public boolean hasChats () {
    return !chats.isEmpty();
  }

  public static final int VIEW_TYPE_CHAT = 0;
  public static final int VIEW_TYPE_INFO = 1;
  public static final int VIEW_TYPE_EMPTY = 2;

  @Override
  public int getItemViewType (int position) {
    return chats.isEmpty() ? VIEW_TYPE_EMPTY : position == chats.size() ? VIEW_TYPE_INFO : VIEW_TYPE_CHAT;
  }

  public void setCanLoadMore (boolean canLoadMore) {
    if (this.canLoadMore != canLoadMore) {
      this.canLoadMore = canLoadMore;
      if (getItemCount() > 0) {
        notifyChatItemChanged(chats.size());
      }
    }
  }

  public void setFinishReached () {
    finishReached = true;
  }

  public boolean isFinishReached () {
    return finishReached;
  }

  public TGChat getChatAt (int index) {
    return index >= 0 && index < chats.size() ? chats.get(index) : null;
  }

  public int indexOfChat (long chatId) {
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

  public int indexOfChat (TGChat chat) {
    return chats.indexOf(chat);
  }

  public int indexOfSecretChat (int secretChatId) {
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
    TGChat lastChat = !this.chats.isEmpty() ? this.chats.get(atIndex - 1) : null;
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
      notifyChatsInserted(atIndex, addedItemCount);
      if (firstAddedItem != null && lastChat != null && lastChat.isPinnedOrSpecial() != firstAddedItem.isPinnedOrSpecial()) {
        notifyItemChanged(atIndex - 1);
      }
    }
  }

  public int updateMessageInteractionInfo (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMessageInteractionInfo(chatId, messageId, interactionInfo)) {
      return index;
    }
    return -1;
  }

  public int updateMessageContent (long chatId, long messageId, TdApi.MessageContent newContent) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMessageContent(chatId, messageId, newContent)) {
      return index;
    }
    return -1;
  }

  public int updateMessagesDeleted (long chatId, long[] messageIds) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMessagesDeleted(chatId, messageIds)) {
      return index;
    }
    return -1;
  }

  public int updateChatReadInbox (long chatId, final long lastReadInboxMessageId, final int unreadCount) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatReadInbox(chatId, lastReadInboxMessageId, unreadCount)) {
      return index;
    }
    return -1;
  }

  public int updateChatUnreadMentionCount (long chatId, int unreadMentionCount) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatUnreadMentionCount(chatId, unreadMentionCount)) {
      return index;
    }
    return -1;
  }

  public int updateChatHasScheduledMessages (long chatId, boolean hasScheduledMessages) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatHasScheduledMessages(chatId, hasScheduledMessages)) {
      return index;
    }
    return -1;
  }

  public int updateChatDraftMessage (long chatId, TdApi.DraftMessage draftMessage) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateDraftMessage(chatId, draftMessage)) {
      return index;
    }
    return -1;
  }

  public int updateChatReadOutbox (long chatId, final long lastReadOutboxMessageId) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatReadOutbox(chatId, lastReadOutboxMessageId)) {
      return index;
    }
    return -1;
  }

  public void updateUser (ChatsRecyclerView recyclerView, TdApi.User user) {
    int i = 0;
    for (TGChat chat : chats) {
      if (chat.updateUser(user) || chat.checkOnline()) {
        recyclerView.invalidateViewAt(i);
      }
      i++;
    }
  }

  public int updateUserStatus (int userId, int startIndex) {
    if (startIndex == 0) {
      int index = 0;
      for (TGChat chat : chats) {
        if (chat.getChatUserId() == userId && chat.checkOnline())
          return index;
        index++;
      }
    } else {
      for (int index = startIndex; index < chats.size(); index++) {
        TGChat chat = chats.get(index);
        if (chat.getChatUserId() == userId && chat.checkOnline()) {
          return index;
        }
      }
    }
    return -1;
  }

  public int updateChatTitle (long chatId, String title) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatTitle(chatId, title)) {
      return index;
    }
    return -1;
  }

  public int updateChatPermissions (long chatId, TdApi.ChatPermissions permissions) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatPermissions(chatId, permissions)) {
      return index;
    }
    return -1;
  }

  public int updateChatClientData (long chatId, String clientData) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatClientData(chatId, clientData)) {
      return index;
    }
    return -1;
  }

  public int updateChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateMarkedAsUnread(chatId, isMarkedAsUnread)) {
      return index;
    }
    return -1;
  }

  public int updateChatTopMessage (long chatId, TdApi.Message topMessage) {
    int index = indexOfChat(chatId);
    if (index != -1) {
      chats.get(index).updateTopMessage(chatId, topMessage);
      return index;
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
    TGChat fromChat = getChatAt(fromPosition);
    TGChat toChat = getChatAt(toPosition);
    if (fromChat == null || !fromChat.isPinned() || toChat == null || !toChat.isPinned()) {
      return;
    }
    ArrayList<Long> chatIds = new ArrayList<>();
    int promotedCount = getHeaderChatCount(true, chatIds);
    promotedCount -= chatIds.size();
    ArrayUtils.move(chatIds, fromPosition - promotedCount, toPosition - promotedCount);
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
        return index;
      }
    }
    return -1;
  }

  public int updateMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    int index = indexOfChat(message.chatId);
    if (index != -1 && chats.get(index).updateMessageSendSucceeded(message, oldMessageId)) {
      return index;
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

  public int updateChatPosition (final boolean matchesList, final long chatId, final TdApi.ChatPosition position, final boolean orderChanged, final boolean sourceChanged, final boolean pinStateChanged) {
    final int oldIndex = indexOfChat(chatId);
    final long newOrder = position.order;

    if (!matchesList) {
      if (oldIndex != -1) {
        chats.get(oldIndex).updateChatPosition(chatId, position, sourceChanged, pinStateChanged);
      }
      return 0;
    }

    int flags = sourceChanged ? ORDER_INVALIDATE_DECORATIONS : 0;

    if (newOrder == 0) {
      if (oldIndex != -1) {
        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.i(Log.TAG_MESSAGES_LOADER, "updateChatOrder -> 0, chatId: %d, oldIndex: %d", chatId, oldIndex);
        }
        TGChat chat = removeChat(oldIndex);
        chat.updateChatPosition(chatId, position, sourceChanged, pinStateChanged);
        notifyItemRemoved(oldIndex);
        notifyItemChanged(chats.size());
        context.checkDisplayNoChats();
      }
      return flags;
    }

    if (oldIndex != -1) { // Chat is in current list
      final TGChat movedChat = chats.get(oldIndex);
      final long prevOrder = movedChat.getChatOrder();

      if (!movedChat.updateChatPosition(chatId, position, sourceChanged, pinStateChanged) || !orderChanged) { // Order not changed
        return flags;
      }

      Collections.sort(chats, CHAT_COMPARATOR);
      int newIndex = indexOfChat(chatId);
      if (newOrder < prevOrder && newIndex == chats.size() - 1 && !finishReached) {
        removeChat(newIndex);
        notifyItemRemoved(oldIndex);
        notifyItemChanged(chats.size());
        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.i(Log.TAG_MESSAGES_LOADER, "Removing chat, because it took last possible position, chatId: %d", chatId);
        }
      } else if (newIndex != oldIndex) {
        notifyItemMoved(oldIndex, newIndex);
        flags |= ORDER_REMAIN_SCROLL;
      } else if (pinStateChanged) {
        flags |= ORDER_INVALIDATE_DECORATIONS;
      }
      return flags;
    }

    final TdApi.Chat chat = context.filter(context.tdlib().chatStrict(chatId));

    if ((chats.size() == 0 && !finishReached) || chat == null) {
      return 0;
    }

    TGChat newChat = new TGChat(context.getParentOrSelf(), context.chatList(), chat, false);
    newChat.updateChatPosition(chatId, position, sourceChanged, pinStateChanged);
    if (sourceChanged) {
      flags |= ORDER_INVALIDATE_DECORATIONS;
    }

    int newIndex = Collections.binarySearch(chats, newChat, CHAT_COMPARATOR);

    if (newIndex >= 0) {
      Log.w(Log.TAG_MESSAGES_LOADER, "Chat seems to be already presented in the list, chatId: %d", newChat.getChatId());
      return 0;
    }

    newIndex = newIndex * -1 - 1;

    if (newIndex == chats.size() && !finishReached) {
      return 0;
    }

    if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
      Log.i(Log.TAG_MESSAGES_LOADER, "Adding chat to the list, chatId: %d, newIndex: %d", chatId, newIndex);
    }

    newChat.makeMeasures();
    addChat(newIndex, newChat);

    if (chats.size() == 1) {
      notifyItemRangeInserted(0, 2);
    } else {
      notifyItemInserted(newIndex);
      notifyItemChanged(chats.size());
    }

    flags |= ORDER_REMAIN_SCROLL;

    context.checkDisplayNoChats();

    return flags;
  }

  public int updateChatPhoto (long chatId, TdApi.ChatPhotoInfo photo) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatPhoto(chatId, photo)) {
      return index;
    }
    return -1;
  }

  public int updateChatSettings (long chatId, final TdApi.ChatNotificationSettings settings) {
    int index = indexOfChat(chatId);
    if (index != -1 && chats.get(index).updateChatSettings(chatId, settings)) {
      return index;
    }
    return -1;
  }

  public void updateNotificationSettings (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {

  }

  public int updateSecretChat (TdApi.SecretChat secretChat) {
    int index = indexOfSecretChat(secretChat.id);
    if (index != -1 && chats.get(index).updateSecretChat(secretChat)) {
      return index;
    }
    return -1;
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

  private void notifyChatsInserted (int fromIndex, int count) {
    notifyItemRangeInserted(fromIndex, count);
  }

  private void notifyChatItemChanged (int index) {
    notifyItemChanged(index);
  }

  private void notifyChatAppeared (int fromIndex) {
    int firstItem = layoutManager.findFirstVisibleItemPosition();
    int offset;
    if (firstItem != -1) {
      View v = layoutManager.findViewByPosition(firstItem);
      offset = v != null ? v.getTop() : 0;
    } else {
      offset = 0;
    }
    if (fromIndex == -1) {
      notifyItemInserted(0);
    } else {
      notifyItemMoved(fromIndex, 0);
    }
    if (firstItem != -1) {
      layoutManager.scrollToPositionWithOffset(firstItem, offset);
    }
  }

  private void notifyChatRemoved (int index) {
    notifyItemRemoved(index);
  }

  private void notifyChatMoved (int fromIndex, int toIndex) {
    notifyItemMoved(fromIndex, toIndex);
  }

  public void notifyAllChanged () {
    notifyItemRangeChanged(0, getItemCount());
  }

  // Search mode

  private ArrayList<RecyclerView> attachedToRecyclers;

  @Override
  public void onAttachedToRecyclerView (RecyclerView recyclerView) {
    if (attachedToRecyclers == null) {
      attachedToRecyclers = new ArrayList<>();
    }
    attachedToRecyclers.add(recyclerView);
  }

  @Override
  public void onDetachedFromRecyclerView (RecyclerView recyclerView) {
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
