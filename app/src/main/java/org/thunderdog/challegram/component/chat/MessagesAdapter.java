/**
 * File created on 10/09/15 at 16:45
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.data.TGMessageChat;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.data.TGMessagePoll;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.td.MessageId;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesHolder> {
  private static final int INITIAL_CAPACITY = 15;

  private final Context context;
  private final MessagesManager manager;
  private @Nullable TdApi.ChatType chatType;
  private @Nullable ArrayList<TGMessage> items;

  private final @Nullable ViewController<?> themeProvider;

  public MessagesAdapter (Context context, MessagesManager manager, @Nullable ViewController<?> themeProvider) {
    this.context = context;
    this.manager = manager;
    this.themeProvider = themeProvider;
  }

  public void invalidateAllMessages () {
    if (items != null) {
      for (TGMessage message : items) {
        message.invalidate();
      }
    }
    MessagesRecyclerView recyclerView = manager.controller().getMessagesView();
    if (recyclerView != null) {
      recyclerView.invalidate();
    }
  }

  public void invalidateServiceMessages () {
    if (items != null) {
      for (TGMessage message : items) {
        if (message instanceof TGMessageChat) {
          message.invalidate();
        }
      }
    }
    MessagesRecyclerView recyclerView = manager.controller().getMessagesView();
    if (recyclerView != null) {
      recyclerView.invalidate();
    }
  }

  public void setChatType (@Nullable TdApi.ChatType info) {
    this.chatType = info;
  }

  @Override
  public @NonNull MessagesHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return MessagesHolder.create(context, manager, viewType, themeProvider);
  }

  @Override
  public void onBindViewHolder (MessagesHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case MessagesHolder.TYPE_EMPTY: {
        manager.setEmptyText((TextView) holder.itemView, items != null);
        break;
      }
      case MessagesHolder.TYPE_SECRET_CHAT_INFO:
      case MessagesHolder.TYPE_SECRET_CHAT_INFO_BUBBLE: {
        break;
      }
      default: {
        if (items != null) {
          TGMessage msg = items.get(position);
          if (chatType != null) {
            msg.autoDownloadContent(chatType);
          }
          holder.setMessage(msg);
        }
        break;
      }
    }
  }

  public int findMessageByMediaItem (MediaItem item) {
    if (items != null && !items.isEmpty()) {
      switch (item.getType()) {
        case MediaItem.TYPE_PHOTO:
        case MediaItem.TYPE_VIDEO:
        case MediaItem.TYPE_CHAT_PROFILE:
        case MediaItem.TYPE_GIF: {
          int i = 0;
          for (TGMessage msg : items) {
            if (msg.isDescendantOrSelf(item.getSourceMessageId()) || item.getSourceMessage() == msg) {
              return i;
            }
            i++;
          }
          break;
        }
      }
    }
    return -1;
  }

  @Override
  public int getItemCount () {
    return items == null || items.isEmpty() ? 1 : items.size();
  }

  public boolean updateLocale () {
    final int firstItemViewType = getItemViewType(0);
    if (firstItemViewType == MessagesHolder.TYPE_EMPTY || firstItemViewType == MessagesHolder.TYPE_SECRET_CHAT_INFO) {
      notifyItemChanged(0);
      return true;
    }
    if (items == null) {
      return true;
    }
    boolean updated = false;
    for (TGMessage msg : items) {
      if (msg.updateLocale()) {
        updated = true;
      }
    }
    return updated;
  }

  @Override
  public int getItemViewType (int position) {
    if (items == null || items.isEmpty() || position >= items.size() || position < 0) {
      return items != null && manager.isSecretChat() ? (manager.useBubbles() ? MessagesHolder.TYPE_SECRET_CHAT_INFO_BUBBLE : MessagesHolder.TYPE_SECRET_CHAT_INFO) : MessagesHolder.TYPE_EMPTY;
    } else {
      int viewType;
      TGMessage msg = items.get(position);
      if (msg.needComplexReceiver() || msg instanceof TGMessageMedia || (msg instanceof TGMessagePoll && !((TGMessagePoll) msg).isAnonymous())) {
        viewType = /*msg.hasReply() ? MessagesHolder.TYPE_MESSAGE_COMPLEX_MEDIA_REPLY :*/ MessagesHolder.TYPE_MESSAGE_COMPLEX_MEDIA;
      } else {
        boolean isMedia = msg.needGifReceiver() || msg.needImageReceiver();
        viewType = isMedia ? (/*msg.hasReply() ? MessagesHolder.TYPE_MESSAGE_MEDIA_REPLY :*/ MessagesHolder.TYPE_MESSAGE_MEDIA) : (/*msg.hasReply() ? MessagesHolder.TYPE_MESSAGE_REPLY :*/ MessagesHolder.TYPE_MESSAGE);
      }
      if (msg.needViewGroup()) {
        viewType += MessagesHolder.TYPE_MESSAGE_VIEW_GROUP;
      }
      return viewType;
    }
  }

  // View manipulation

  @Override
  public void onViewAttachedToWindow (MessagesHolder holder) {
    holder.attach();
  }

  @Override
  public void onViewDetachedFromWindow (MessagesHolder holder) {
    holder.detach();
  }

  // Data getters

  public TGMessage getTopMessage () {
    if (items == null)
      return null;
    // FIXME #thread-safe Replace this dumb crash fix with proper thread safety
    try {
      return getMessage(items.size() - 1);
    } catch (IndexOutOfBoundsException e) {
      return getTopMessage();
    }
  }

  public TGMessage getBottomMessage () {
    return getMessage(0);
  }

  public TGMessage getMessage (int index) {
    return items == null || index < 0 || index >= items.size() ? null : items.get(index);
  }

  public TGMessage getItem (int index) {
    return items == null ? null : items.get(index);
  }

  public void replaceItem (int index, TGMessage msg) {
    if (items != null) {
      items.get(index).onDestroy();
      items.set(index, msg);
      boolean includeTop = index > 0;
      if (includeTop) {
        TGMessage bottomMessage = items.get(index - 1);
        bottomMessage.mergeWith(msg, index == 1);
        bottomMessage.rebuildLayout();
      }
      if (index + 1 < items.size()) {
        msg.mergeWith(items.get(index + 1), index == 0);
        msg.rebuildLayout();
        notifyItemRangeChanged(includeTop ? index - 1 : index, includeTop ? 3 : 2);
      } else {
        msg.mergeWith(null, index == 0);
        msg.rebuildLayout();
        notifyItemRangeChanged(includeTop ? index - 1 : index, includeTop ? 2 : 1);
      }
    }
  }

  public int getMessageCount () {
    return items == null ? 0 : items.size();
  }

  public TdApi.Message tryFindMessage (long chatId, long messageId) {
    if (items == null)
      return null;
    try {
      for (TGMessage msg : items) {
        if (msg.getChatId() == chatId) {
          TdApi.Message message = msg.getMessage(messageId);
          if (message != null) {
            return message;
          }
        }
      }
    } catch (Throwable t) {
      Log.i("Error occured during message lookup", t);
    }
    return null;
  }

  public TGMessage findMessageById (long messageId) {
    if (items == null) {
      return null;
    }
    for (TGMessage msg : items) {
      if (msg.getId() == messageId) {
        return msg;
      }
    }
    return null;
  }

  public @Nullable ArrayList<TGMessage> getItems () {
    return items;
  }

  // Index getters

  public int indexOfMessageContainer (MessageId messageId) {
    if (items != null) {
      int i = 0;
      for (TGMessage item : items) {
        if (item.getChatId() == messageId.getChatId() && item.isDescendantOrSelf(messageId.getMessageId(), messageId.getOtherMessageIds())) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  public int indexOfMessageContainer (long messageId) {
    if (items == null) {
      return -1;
    }
    int i = 0;
    for (TGMessage item : items) {
      if (item.isDescendantOrSelf(messageId)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  // Data deletion

  public void clear (boolean reset) {
    int oldItemCount = getItemCount();
    if (reset) {
      if (items != null && items.size() > 0) {
        for (TGMessage msg : items) {
          msg.onDestroy();
        }
      }
      items = null;
    } else if (items != null) {
      for (TGMessage msg : items) {
        msg.onDestroy();
      }
      items.clear();
    }
    U.notifyItemsReplaced(this, oldItemCount);
  }

  public void moveItem (int fromIndex, int toIndex) {
    if (items == null || fromIndex == toIndex) {
      return;
    }
    TGMessage msg = items.remove(fromIndex);
    items.add(toIndex, msg);

    TGMessage topMessage = getMessage(toIndex + 1);

    msg.mergeWith(topMessage, toIndex == 0);
    msg.rebuildLayout();

    if (topMessage != null) {
      topMessage.mergeWith(getMessage(toIndex + 2), false);
      topMessage.rebuildLayout();
    }

    notifyItemMoved(fromIndex, toIndex);
    if (topMessage != null) {
      notifyItemRangeChanged(toIndex, 2);
    } else {
      notifyItemChanged(toIndex);
    }

    int lookupIndex = fromIndex < toIndex ? fromIndex - 1 : toIndex;

    TGMessage currentMessage = getMessage(lookupIndex);
    TGMessage bottomMessage = getMessage(lookupIndex - 1);

    if (bottomMessage != null) {
      bottomMessage.mergeWith(currentMessage, lookupIndex == 1);
      bottomMessage.rebuildLayout();
      notifyItemChanged(lookupIndex - 1);
    } else if (currentMessage != null) {
      currentMessage.mergeWith(getMessage(lookupIndex + 1), lookupIndex == 0);
      currentMessage.rebuildLayout();
      notifyItemChanged(lookupIndex);
    }
  }

  public TGMessage removeItem (int index) {
    if (items == null) {
      return null;
    }
    TGMessage msg = items.remove(index);
    msg.onDestroy();
    notifyItemRemoved(index);
    if (items.size() != 0) {
      TGMessage currentMessage = getMessage(index);
      TGMessage bottomMessage = getMessage(index - 1);

      if (bottomMessage != null) {
        bottomMessage.mergeWith(currentMessage, index == 1);
        bottomMessage.rebuildLayout();
        notifyItemChanged(index - 1);
      } else if (currentMessage != null) {
        currentMessage.mergeWith(getMessage(index + 1), index == 0);
        currentMessage.rebuildLayout();
        notifyItemChanged(index);
      }
    } else {
      notifyItemRangeInserted(0, getItemCount());
    }
    return msg;
  }

  // Data insertion

  public void reset (TGMessage message) {
    int oldItemCount = getItemCount();
    if (items != null) {
      for (TGMessage msg : items) {
        msg.onDestroy();
      }
      items.clear();
    }
    if (message == null) {
      if (items != null) {
        items = null;
        U.notifyItemsReplaced(this, oldItemCount);
      }
    } else {
      if (items == null) {
        items = new ArrayList<>(INITIAL_CAPACITY);
      }
      items.add(message);
      U.notifyItemsReplaced(this, oldItemCount);
    }
  }

  public boolean hasUnreadSeparator () {
    if (items != null) {
      for (TGMessage item : items) {
        if (item.hasUnreadBadge()) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean addMessage (TGMessage message, boolean top, boolean needScrollToBottom) {
    if (manager.needRemoveDuplicates() && indexOfMessageContainer(message.getId()) != -1)
      return false;

    if (manager.needSort() && items != null && !items.isEmpty()) {
      int index = Collections.binarySearch(items, message);
      if (index < 0) {
        index = (-index) - 1;
        TGMessage topMessage = index < items.size() ? items.get(index) : null;
        TGMessage bottomMessage = index > 0 && index - 1 < items.size() ? items.get(index - 1) : null;

        message.mergeWith(topMessage, index == 0);
        message.prepareLayout();
        if (topMessage != null) {
          notifyItemChanged(index);
        }

        items.add(index, message);
        notifyItemInserted(index);

        // FIXME for some reason bubble merging on bottom side does not work

        if (bottomMessage != null) {
          bottomMessage.mergeWith(message, index - 1 == 0);
          bottomMessage.rebuildLayout();
          notifyItemChanged(index - 1);
        }

        if (message.isScheduled() && message.isNotSent() && index != 0) {
          manager.controller().highlightMessage(message.toMessageId());
          return true;
        }

        return false;
      }
    }

    TGMessage bottomMessage = top ? null : getMessage(0);
    TGMessage topMessage = top ? getMessage(getMessageCount() - 1) : null;
    boolean sponsoredFlag = bottomMessage != null && bottomMessage.isSponsored();

    if (sponsoredFlag && items != null) {
      for (TGMessage msg : items) {
        if (!msg.isSponsored()) {
          bottomMessage = msg;
          break;
        }
      }
    }

    if (top) {
      if (topMessage != null) {
        topMessage.mergeWith(message, getMessageCount() == 1);
        topMessage.rebuildLayout();
      }
    } else {
      if (!message.isOld()) {
        message.markAsBeingAdded(true);
      }
      if ((!needScrollToBottom || message.isOld()) && !message.isOutgoing() && message.checkIsUnread(false) && !hasUnreadSeparator()) {
        TdApi.Chat chat = message.tdlib().chat(message.getChatId());
        if (chat != null) {
          message.setShowUnreadBadge(chat.unreadCount > 0);
        }
      }
    }
    message.mergeWith(bottomMessage, !top || this.items == null || this.items.isEmpty());
    if (bottomMessage != null && getBottomMessage().isSponsored() && !message.isSponsored()) {
      bottomMessage.setNeedExtraPresponsoredPadding(false);
      bottomMessage.setNeedExtraPadding(false);
      message.setNeedExtraPresponsoredPadding(true);
    }
    message.prepareLayout();
    if (items == null) {
      items = new ArrayList<>(INITIAL_CAPACITY);
    }
    int prevSize = items.size();
    if (top) {
      items.add(message);
      if (prevSize == 0) {
        notifyItemChanged(0);
      } else {
        notifyItemInserted(items.size() - 1);
        notifyItemChanged(items.size() - 2);
      }
    } else {
      int newIndex = sponsoredFlag ? 1 : 0;
      if (bottomMessage != null) {
        notifyItemChanged(newIndex);
      }
      items.add(newIndex, message);
      if (prevSize == 0) {
        notifyItemChanged(0);
      } else {
        notifyItemInserted(newIndex);
        notifyItemRangeChanged(0, items.size());
      }
    }

    return false;
  }

  public void resetMessages (ArrayList<TGMessage> items) {
    int oldItemCount = getItemCount();

    if (this.items == null) {
      this.items = new ArrayList<>(INITIAL_CAPACITY);
    } else {
      this.items.clear();
    }
    this.items.addAll(items);
    U.notifyItemsReplaced(this, oldItemCount);
  }

  public void addMessages (List<TGMessage> items, boolean fromTop) {
    if (this.items == null) {
      this.items = new ArrayList<>(INITIAL_CAPACITY);
    }

    final int count = this.items.size();

    if (count > 0 && !items.isEmpty() && manager.needRemoveDuplicates()) {
      if (items.size() > count) {
        for (int i = items.size() - 1; i >= 0; i--) {
          TGMessage msg = items.get(i);
          if (indexOfMessageContainer(msg.getId()) != -1) {
            items.remove(i);
            if (i > 0) {
              items.get(i - 1).mergeWith(items.size() > i ? items.get(i) : null, !fromTop && i - 1 == 0);
              items.get(i - 1).rebuildLayout();
            }
          }
        }
      } else {
        for (TGMessage item : this.items) {
          for (int i = items.size() - 1; i >= 0; i--) {
            TGMessage newItem = items.get(i);
            if (item.isDescendantOrSelf(newItem.getId())) {
              items.remove(i);
              if (i > 0) {
                items.get(i - 1).mergeWith(items.size() > i ? items.get(i) : null, !fromTop && i - 1 == 0);
                items.get(i - 1).rebuildLayout();
              }
              break;
            }
          }
          if (items.isEmpty())
            break;
        }
      }
    }

    final int newCount = items.size();
    final int oldItemCount = getItemCount();

    if (fromTop) {
      if (newCount > 0 && count > 0) {
        TGMessage combine = items.get(0);
        TGMessage msg = this.items.get(count - 1);
        msg.mergeWith(combine, false);
        msg.rebuildLayout();
        notifyItemChanged(count - 1);
      }
      this.items.addAll(items);
      if (count == 0) {
        U.notifyItemsReplaced(this, oldItemCount);
      } else {
        notifyItemRangeInserted(count, items.size());
      }
    } else {
      if (!hasUnreadSeparator()) {
        for (int i = items.size() - 1; i >= 0; i--) {
          TGMessage message = items.get(i);
          if (!message.isOutgoing() /*&& (message.isOld() || )*/ && message.checkIsUnread(false)) {
            TdApi.Chat chat = message.tdlib().chat(message.getChatId());
            if (chat != null) {
              message.setShowUnreadBadge(chat.unreadCount > 0);
            }
            break;
          }
        }
      }
      if (newCount > 0 && count > 0) {
        TGMessage combine = this.items.get(0);
        TGMessage msg = items.get(newCount - 1);
        msg.mergeWith(combine, false);
        msg.rebuildLayout();
        notifyItemChanged(0);
      }
      this.items.addAll(0, items);
      if (count == 0) {
        U.notifyItemsReplaced(this, oldItemCount);
      } else {
        notifyItemRangeInserted(0, items.size());
      }
    }
  }

  public boolean isEmpty () {
    return items == null || items.size() == 0 || (items.size() == 1 && items.get(0) instanceof TGMessageBotInfo);
  }

  /*@Override
  public void onViewRecycled (MessagesHolder holder) {
    int type = holder.getItemViewType();
    if (type == MessagesHolder.TYPE_MESSAGE_MEDIA_REPLY || type == MessagesHolder.TYPE_MESSAGE)
  }*/
}
