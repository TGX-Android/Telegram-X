/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/09/2015 at 16:42
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.MessageListManager;
import org.thunderdog.challegram.data.SponsoredMessageUtils;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.telegram.MessageEditListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeProperty;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CancellableResultHandler;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class MessagesManager implements Client.ResultHandler, MessagesSearchManager.Delegate,
  MessageListener, MessageEditListener, Comparator<TGMessage>, TGPlayerController.PlayListBuilder, BaseActivity.PasscodeListener, TdlibCache.ChatMemberStatusChangeListener, TdlibSettingsManager.DismissMessageListener {
  private final MessagesController controller;
  private final Tdlib tdlib;
  private MessagesAdapter adapter;
  private LinearLayoutManager manager;
  private final RecyclerView.OnScrollListener listener;

  private final MessagesLoader loader;

  private String eventLogQuery;
  private long[] eventLogUserIds;
  private TdApi.ChatEventLogFilters eventLogFilters;

  private LongSparseArray<TdApi.ChatAdministrator> chatAdmins;

  private static final int TOP_PRELOAD_COUNT = 10;
  private static final int BOTTOM_PRELOAD_COUNT = 7;

  private boolean isScrolling;
  private boolean wasScrollByUser;

  public MessagesManager (final MessagesController controller) {
    this.controller = controller;
    controller.context().addPasscodeListener(this);
    this.tdlib = controller.tdlib();
    this.loader = new MessagesLoader(this);
    this.listener = new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          controller.collapsePinnedMessagesBar(true);
          if (Settings.instance().needHideChatKeyboardOnScroll() && controller != null) {
            controller.hideAllKeyboards();
          }
          wasScrollByUser = true;
        }
        boolean isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
        if (MessagesManager.this.isScrolling != isScrolling) {
          MessagesManager.this.isScrolling = isScrolling;
          if (!isScrolling) {
            viewMessages();
          }
        }
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          saveScrollPosition();
        }
        ((MessagesRecyclerView) recyclerView).setIsScrolling(newState != RecyclerView.SCROLL_STATE_IDLE);
      }

      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        viewMessages();
        if (dy == 0) {
          saveScrollPosition();
          ((MessagesRecyclerView) recyclerView).showDateForcely();
        }
        if (dy != 0  && !hasScrolled && loader.getChatId() != 0) {
          hasScrolled = true;
          controller.onInteractedWithContent();
          controller.onFirstChatScroll();
        }
        controller.context().reactionsOverlayManager().addOffset(0, -dy);
      }
    };

    this.useReactionBubblesValue = checkReactionBubbles();
  }

  public int getKnownTotalMessageCount () {
    return loader.getKnownTotalMessageCount();
  }

  @Override
  public void onChatMemberStatusChange (long chatId, TdApi.ChatMember member) {
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId && chatAdmins != null && member.memberId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
        final long userId = ((TdApi.MessageSenderUser) member.memberId).userId;
        int existingIndex = chatAdmins.indexOfKey(userId);
        TdApi.ChatAdministrator existingAdmin = existingIndex >= 0 ? chatAdmins.valueAt(existingIndex) : null;
        boolean changed = false;
        if (TD.isAdmin(member.status)) {
          if (existingIndex >= 0) {
            // Just update the title, if needed
            if (!StringUtils.equalsOrBothEmpty(existingAdmin.customTitle, Td.getCustomTitle(member.status))) {
              existingAdmin.customTitle = Td.getCustomTitle(member.status);
              changed = true;
            }
          } else {
            // Add admin
            chatAdmins.put(userId, new TdApi.ChatAdministrator(userId, Td.getCustomTitle(member.status), TD.isCreator(member.status)));
            changed = true;
          }
        } else {
          if (existingIndex >= 0) {
            // Remove admin
            chatAdmins.removeAt(existingIndex);
            changed = true;
          }
        }
        if (changed) {
          updateAdministratorSigns();
        }
      }
    });
  }

  public void setChatAdmins (TdApi.ChatAdministrators administrators) {
    LongSparseArray<TdApi.ChatAdministrator> chatAdmins = new LongSparseArray<>(administrators.administrators.length);
    for (TdApi.ChatAdministrator admin : administrators.administrators) {
      chatAdmins.put(admin.userId, admin);
    }
    this.chatAdmins = chatAdmins;
    updateAdministratorSigns();
  }

  private void updateAdministratorSigns () {
    long adminUserId = 0;
    TdApi.ChatAdministrator administrator = null;
    for (int i = 0; i < adapter.getMessageCount(); i++) {
      TGMessage msg = adapter.getMessage(i);
      if (msg == null) {
        continue;
      }
      long userId = Td.getSenderUserId(msg.getMessage());
      if (adminUserId != userId) {
        administrator = chatAdmins != null ? chatAdmins.get(adminUserId = userId) : null;
      }
      msg.setAdministratorSign(administrator);
    }
  }

  // Utils used by TGMessage

  public final boolean isMessageSelected (long chatId, long messageId, TGMessage container) {
    return controller.isMessageSelected(chatId, messageId, container);
  }

  public float getSelectableFactor () {
    return controller.getSelectableFactor();
  }

  // Implementation

  public LongSparseArray<TdApi.ChatAdministrator> getChatAdmins () {
    return chatAdmins;
  }

  private int firstVisibleProtectedPosition = -1, lastVisibleProtectedPosition = -1;

  private void checkVisibleContentProtection (int first, int last) {
    switch (loader.getSpecialMode()) {
      case MessagesLoader.SPECIAL_MODE_NONE:
      case MessagesLoader.SPECIAL_MODE_SEARCH:
      case MessagesLoader.SPECIAL_MODE_SCHEDULED:
        break;
      case MessagesLoader.SPECIAL_MODE_EVENT_LOG:
      case MessagesLoader.SPECIAL_MODE_RESTRICTED:
        return;
    }

    if (firstVisibleProtectedPosition == first && lastVisibleProtectedPosition == last) {
      return;
    }

    firstVisibleProtectedPosition = first;
    lastVisibleProtectedPosition = last;

    boolean hasVisibleProtectedContent = false;

    for (int i = first; i <= last; i++) {
      View view = manager.findViewByPosition(i);
      if (view instanceof MessageProvider) {
        TGMessage message = ((MessageProvider) view).getMessage();
        if (message != null && !message.canBeSaved() && !message.isSponsored()) {
          hasVisibleProtectedContent = true;
          break;
        }
      }
    }

    setHasVisibleProtectedContent(hasVisibleProtectedContent);
  }

  public void viewMessages () {
    if (manager != null) {
      int first = manager.findFirstVisibleItemPosition();
      int last = manager.findLastVisibleItemPosition();

      if (first != -1 && last != -1) {
        boolean checkedMessages = viewDisplayedMessages(first, last);
        if (checkedMessages) {
          firstVisibleProtectedPosition = lastVisibleProtectedPosition = -1;
        } else {
          checkVisibleContentProtection(first, last);
        }
        if (isFocused && !(first - BOTTOM_PRELOAD_COUNT <= 0 && loader.loadMore(false)) && last + TOP_PRELOAD_COUNT >= adapter.getItemCount()) {
          loader.loadMore(true);
        }
      }
      checkScrollButton(first, last);

      controller.checkRoundVideo(first, last, true);
    }
  }

  public void onViewportMeasure () {
    viewMessages();
    saveScrollPosition();
  }

  private long lastCheckedTopId;
  private long lastCheckedBottomId;
  private long lastViewedMention;
  private int lastCheckedCount;

  private boolean viewDisplayedMessages (int first, int last) {
    if (first == -1 || last == -1 || inSpecialMode() || !isFocused) {
      return false;
    }

    TGMessage topEdge = adapter.getMessage(first);
    TGMessage bottomEdge = adapter.getMessage(last);

    if (topEdge == null || bottomEdge == null) {
      return false;
    }

    long topId = topEdge.getSmallestId();
    long bottomId = bottomEdge.getBiggestId();
    final int count = last - first + 1;

    if (lastCheckedTopId == topId && lastCheckedBottomId == bottomId && lastCheckedCount == count) {
      return false;
    }

    boolean success = true;

    LongSet list = null;
    LongSparseArray<LongSet> refreshMap = null;
    int maxDate = 0;
    boolean headerVisible = false;
    boolean hasProtectedContent = false;

    for (int viewIndex = first; viewIndex <= last; viewIndex++) {
      View view = manager.findViewByPosition(viewIndex);
      if (!(view instanceof MessageProvider)) {
        success = false;
      }
      TGMessage msg = view instanceof MessageProvider ? ((MessageProvider) view).getMessage() : null;
      if (msg != null) {
        if (!msg.canBeSaved() && !msg.isSponsored()) {
          hasProtectedContent = true;
        }
        if (msg == headerMessage) {
          headerVisible = true;
        }
        maxDate = Math.max(msg.getDate(), maxDate);
        if (msg.markAsViewed() || msg.containsUnreadReactions()) {
          long id = msg.getBiggestId();
          if (msg.containsUnreadMention() && id > lastViewedMention) {
            lastViewedMention = id;
          }
          if (msg.containsUnreadReactions() && id > lastViewedReaction) {
            lastViewedReaction = id;
          }
          if (list == null) {
            list = new LongSet(last - first);
          } else {
            list.ensureCapacity(last - first);
          }
          msg.getIds(list);
        }
        if (msg.needRefreshViewCount()) {
          if (refreshMap == null) {
            refreshMap = new LongSparseArray<>();
          }
          LongSet refreshList = refreshMap.get(msg.getChatId());
          if (refreshList == null) {
            refreshList = new LongSet(last - first);
            refreshMap.put(msg.getChatId(), refreshList);
          } else {
            refreshList.ensureCapacity(last - first);
          }
          msg.getIds(refreshList);
        }
      }
    }

    LongSparseArray<long[]> viewedMap;
    if (refreshMap != null) {
      viewedMap = new LongSparseArray<>(refreshMap.size());
      for (int i = 0; i < refreshMap.size(); i++) {
        viewedMap.append(refreshMap.keyAt(i), refreshMap.valueAt(i).toArray());
      }
    } else {
      viewedMap = null;
    }

    if (success) {
      lastCheckedTopId = topId;
      lastCheckedBottomId = bottomId;
      lastCheckedCount = count;
    } else {
      lastCheckedTopId = lastCheckedBottomId = lastCheckedCount = 0;
    }

    setHasVisibleProtectedContent(hasProtectedContent);
    setRefreshMessages(loader.getChatId(), loader.getMessageThreadId(), viewedMap, maxDate);
    setHeaderVisible(headerVisible);

    if (list != null) {
      viewMessagesInternal(loader.getChatId(), loader.getMessageThreadId(), list, true);
    }

    return true;
  }

  public interface MessageProvider {
    TGMessage getMessage ();
  }

  private boolean lastScrollToBottomVisible;

  private void checkScrollButton (int first, int last) {
    if (controller().inWallpaperPreviewMode())
      return;
    boolean hasMessages = getActiveMessageCount() > 0;
    boolean isVisible = hasMessages && first != -1 && last != -1 && adapter.getMessageCount() >= last;
    if (isVisible) {
      isVisible = first >= 2;
      if (!isVisible) {
        int recyclerHeight = getRecyclerHeight();
        int checkHeight = recyclerHeight / 2;

        long sumHeight = 0;
        for (int i = 0; i < first; i++) {
          TGMessage msg = adapter.getMessage(i);
          if (msg != null) {
            sumHeight += msg.getHeight();
            if (sumHeight >= checkHeight) {
              break;
            }
          }
        }
        View view = manager.findViewByPosition(first);
        if (view != null) {
          sumHeight += view.getBottom() - recyclerHeight;
        }
        isVisible = sumHeight >= checkHeight;
      }
    }
    lastScrollToBottomVisible = isVisible;
    checkScrollToBottomButton();
  }

  public void checkScrollToBottomButton () {
    controller.setScrollToBottomVisible(getActiveMessageCount() > 0 && (lastScrollToBottomVisible || loader.canLoadBottom() || hasReturnMessage()), isReturnAbove());
  }

  public void onCanLoadMoreBottomChanged () {
    checkScrollToBottomButton();
  }

  public void release () {
    controller.context().removePasscodeListener(this);
  }

  public void dismissPinnedMessage () {
    if (tdlib.isSelfChat(loader.getChatId()))
      return;
    long maxMessageId = maxPinnedMessageId();
    if (maxMessageId != 0) {
      tdlib.settings().dismissMessage(loader.getChatId(), maxMessageId);
      setPinnedMessagesAvailable(false);
    }
  }

  public long maxPinnedMessageId () {
    return pinnedMessages != null ? pinnedMessages.getMaxMessageId() : 0;
  }

  public int getPinnedMessageCount () {
    return pinnedMessages != null ? pinnedMessages.getTotalCount() : ListManager.COUNT_UNKNOWN;
  }

  private void checkPinnedMessages () {
    setPinnedMessagesAvailable(pinnedMessages != null && pinnedMessages.isAvailable() && !tdlib.settings().isMessageDismissed(loader.getChatId(), pinnedMessages.getMaxMessageId()));
  }

  @Override
  public void onPinnedMessageDismissed (long chatId, long messageId) {
    if (loader.getChatId() == chatId) {
      checkPinnedMessages();
    }
  }

  @Override
  public void onPinnedMessageRestored (long chatId) {
    if (loader.getChatId() == chatId) {
      checkPinnedMessages();
    }
  }

  public void restorePinnedMessage () {
    tdlib.settings().restorePinnedMessages(loader.getChatId());
    setPinnedMessagesAvailable(pinnedMessages != null && pinnedMessages.isAvailable());
  }

  public boolean canRestorePinnedMessage () {
    return pinnedMessages != null && pinnedMessages.isAvailable() && !pinnedMessagesAvailable;
  }

  public void destroy (ViewController<?> context) {
    resetScroll();
    cancelRefresh();
    returnToMessageIds = null;
    highlightMode = 0;
    tdlib.settings().removePinnedMessageDismissListener(this);
    highlightMessageId = null;
    hasScrolled = false;
    lastViewedMention = 0;
    lastViewedReaction = 0;
    chatAdmins = null;
    if (pinnedMessages != null) {
      pinnedMessages.performDestroy();
      pinnedMessages = null;
    }
    pinnedMessagesAvailable = false;
    hasVisibleProtectedContent = false;
    unsubscribeFromUpdates();
    mentionsHandler = null;
    reactionsHandler = null;
    closestMentions = null;
    closestUnreadReactions = null;
    final long chatId = loader.getChatId();
    if (chatId != 0) {
      if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
        Log.i(Log.TAG_MESSAGES_LOADER, "[DESTROY] chatId:%d", chatId);
      }
      tdlib.closeChat(chatId, context, true);
    }
    loader.reuse();
    adapter.clear(true);
    clearHeaderMessage();
    lastCheckedBottomId = lastCheckedTopId = 0;
    awaitingForPinnedMessages = false;
    wasScrollByUser = false;
  }

  public void checkItemAnimatorEnabled () {
    controller.getMessagesView().setMessageAnimatorEnabled(adapter.getMessageCount() > 0);
  }

  public MessagesAdapter getAdapter () {
    return adapter;
  }

  public LinearLayoutManager getLayoutManager () {
    return manager;
  }

  public boolean isTotallyEmpty () {
    return !loader.canLoadBottom() && !loader.canLoadTop() && !loader.isLoading() && (adapter.getMessageCount() == 0); // FIXME bot startMessage
  }

  private boolean hasScrolled;

  public boolean hasScrolledSinceOpen () {
    return hasScrolled;
  }

  // Search

  public void openSearch (TdApi.Chat chat, String query, TdApi.MessageSender sender, TdApi.SearchMessagesFilter filter) {
    loader.setChat(chat, null, MessagesLoader.SPECIAL_MODE_SEARCH, filter);
    loader.setSearchParameters(query, sender, filter);
    adapter.setChatType(chat.type);
    if (filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterPinned.CONSTRUCTOR) {
      initPinned(chat.id, 1, 1);
    }
    if (highlightMessageId != null) {
      loadFromMessage(highlightMessageId, highlightMode, true);
    } else {
      loadFromStart();
    }
    subscribeForUpdates();
  }

  private boolean inSpecialMode () {
    return loader.getSpecialMode() != MessagesLoader.SPECIAL_MODE_NONE;
  }

  // Event log loader

  public boolean isEventLog () {
    return loader.getSpecialMode() == MessagesLoader.SPECIAL_MODE_EVENT_LOG;
  }

  public boolean isSearchPreview () {
    return loader.getSpecialMode() == MessagesLoader.SPECIAL_MODE_SEARCH;
  }

  public void openEventLog (TdApi.Chat chat) {
    loader.setChat(chat, null, MessagesLoader.SPECIAL_MODE_EVENT_LOG, null);
    adapter.setChatType(chat.type);
    loadFromStart();
  }

  public void applyEventLogFilters (TdApi.ChatEventLogFilters filters, long[] userIds) {
    applyEventLogFilters(filters, eventLogQuery, userIds);
  }

  public void applyEventLogFilters (TdApi.ChatEventLogFilters filters, String query, long[] userIds) {
    if (!StringUtils.equalsOrBothEmpty(eventLogQuery, query) || !Td.equalsTo(eventLogFilters, filters) || !ArrayUtils.equalsSorted(this.eventLogUserIds, userIds)) {
      this.eventLogQuery = query;
      this.eventLogFilters = filters;
      this.eventLogUserIds = userIds;
      controller.checkEventLogSubtitle(!StringUtils.isEmpty(eventLogQuery) || userIds != null || !TD.isAll(filters));
      loadFromStart();
    }
  }

  public String getEventLogQuery () {
    return eventLogQuery;
  }

  public long[] getEventLogUserIds () {
    return eventLogUserIds;
  }

  public TdApi.ChatEventLogFilters getEventLogFilters () {
    return eventLogFilters;
  }

  // Chat messages loader

  private MessageListManager pinnedMessages;
  private final MessageListManager.MaxMessageIdListener pinnedMessageAvailabilityChangeListener = new MessageListManager.MaxMessageIdListener() {
    @Override
    public void onMaxMessageIdChanged (ListManager<TdApi.Message> list, long maxMessageId) {
      if (maxMessageId != 0) {
        setPinnedMessagesAvailable(!tdlib.settings().isMessageDismissed(loader.getChatId(), maxMessageId));
      } else if (!list.isAvailable()) {
        setPinnedMessagesAvailable(false);
      }
    }
  };
  private final MessageListManager.ChangeListener pinnedMessageListener = new MessageListManager.ChangeListener() {
    @Override
    public void onAvailabilityChanged (ListManager<TdApi.Message> list, boolean isAvailable) {
      if (!isAvailable || !tdlib.settings().hasDismissedMessages(loader.getChatId())) {
        // Either list became unavailable,
        // or it has no dismissed pinned messages
        setPinnedMessagesAvailable(isAvailable);
      }
    }
  };

  private boolean pinnedMessagesAvailable;

  private void setPinnedMessagesAvailable (boolean areAvailable) {
    if (this.pinnedMessagesAvailable != areAvailable) {
      this.pinnedMessagesAvailable = areAvailable;
      controller.showHidePinnedMessages(areAvailable, pinnedMessages);
    }
  }

  private void initPinned (long chatId, int initialLoadCount, int loadCount) {
    this.pinnedMessages = new MessageListManager(tdlib, initialLoadCount, loadCount, pinnedMessageListener, chatId, 0, null, null, new TdApi.SearchMessagesFilterPinned(), 0);
    this.pinnedMessages.addMaxMessageIdListener(pinnedMessageAvailabilityChangeListener);
    this.pinnedMessages.addChangeListener(new MessageListManager.ChangeListener() {
      @Override
      public void onInitialChunkLoaded (ListManager<TdApi.Message> list) {
        tdlib.ui().post(() -> {
          if (pinnedMessages == list && awaitingForPinnedMessages) {
            awaitingForPinnedMessages = false;
            controller.executeScheduledAnimation();
          }
        });
      }
    });
    tdlib.settings().addPinnedMessageDismissListener(this);
  }

  public void openChat (TdApi.Chat chat, @Nullable ThreadInfo messageThread, TdApi.SearchMessagesFilter filter, MessagesController context, boolean areScheduled, boolean needPinnedMessages) {
    if (chat.id != 0) {
      if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
        Log.i(Log.TAG_MESSAGES_LOADER, "[CREATE] chatId:%d", chat.id);
      }
      tdlib.openChat(chat.id, context);
      // readOneShot = true;
    }
    if (chat.id != 0 && messageThread == null && !areScheduled && needPinnedMessages) {
      initPinned(chat.id, 10, 50);
    } else {
      this.pinnedMessages = null;
    }
    if (!areScheduled && tdlib.chatRestricted(chat)) {
      loader.setChat(chat, messageThread, MessagesLoader.SPECIAL_MODE_RESTRICTED, null);
      clearHeaderMessage();
      adapter.setChatType(chat.type);
      loadFromStart();
    } else {
      loader.setChat(chat, messageThread, areScheduled ? MessagesLoader.SPECIAL_MODE_SCHEDULED : MessagesLoader.SPECIAL_MODE_NONE, filter);
      clearHeaderMessage();
      adapter.setChatType(chat.type);
      if (highlightMessageId != null) {
        loadFromMessage(highlightMessageId, highlightMode, true);
      } else {
        loadFromStart();
      }
    }
    subscribeForUpdates();
    this.useReactionBubblesValue = checkReactionBubbles();
    this.wasScrollByUser = false;
  }

  public void loadPreview () {
    loadPreviewMessages();
  }

  public void resetByMessage (MessageId highlightMessageId, int highlightMode) {
    clearHeaderMessage();
    this.highlightMessageId = highlightMessageId;
    this.highlightMode = highlightMode;
    loadFromMessage(highlightMessageId, highlightMode, false);
  }

  public void loadFromStart () {
    loadFromStart(new MessageId(loader.getChatId(), 0), null);
  }

  public boolean hasReturnMessage () {
    return highlightMessageId != null && returnToMessageIds != null && returnToMessageIds.length > 0;
  }

  private void revokeReturnMessages () {
    if (hasReturnMessage()) {
      returnToMessageIds = null;
      saveScrollPosition();
    }
  }

  private boolean isReturnAbove () {
    if (!hasReturnMessage())
      return false;
    long messageId = returnToMessageIds[returnToMessageIds.length - 1];
    int index = adapter.indexOfMessageContainer(messageId);
    if (index == -1) {
      TGMessage message = findTopMessage();
      return message != null && message.getSmallestId() > messageId;
    }

    TGMessage scrollMessage = adapter.getMessage(index);

    final int width = getRecyclerWidth();
    int height = getTargetHeight();

    scrollMessage.buildLayout(width);
    int fullHeight = scrollMessage.getHeight();

    /*if (fullHeight > height - offset) {
      height -= offset;
    }*/

    int actualOffset = height / 2 - fullHeight / 2 + scrollMessage.findTopEdge();

    int scrollBy = calculateScrollBy(index, actualOffset);

    return scrollBy < 0;
  }

  public void scrollToStart (boolean force) {
    if (inSpecialMode()) {
      stopScroll();
      scrollToBottom(false);
      return;
    }
    if (hasReturnMessage() && !force) {
      highlightMessage(new MessageId(highlightMessageId.getChatId(), returnToMessageIds[returnToMessageIds.length - 1]),
        HIGHLIGHT_MODE_NORMAL,
        returnToMessageIds.length == 1 ? null : ArrayUtils.removeElement(returnToMessageIds, returnToMessageIds.length - 1),
        true
      );
      return;
    }
    if (force) {
      revokeReturnMessages();
    }

    stopScroll();
    if (loader.canLoadBottom()) {
      loadFromStart();
    } else {
      scrollToBottom(true);
    }
    wasScrollByUser = false;
  }

  public TGMessage findTopMessage () {
    int i = manager.findLastVisibleItemPosition();
    if (i != -1) {
      return adapter.getMessage(i);
    }
    return null;
  }

  public TGMessage findBottomMessage () {
    int i = manager.findFirstCompletelyVisibleItemPosition();
    if (i == -1) {
      i = manager.findFirstVisibleItemPosition();
    }
    if (i != -1) {
      return adapter.getMessage(i);
    }
    return null;
  }

  public int indexOfFirstUnreadMessage () {
    if (adapter.getMessageCount() > 0) {
      for (int i = 0; i < adapter.getMessageCount(); i++) {
        TGMessage message = adapter.getMessage(i);
        if (message.hasUnreadBadge()) {
          return i;
        }
      }
    }
    return -1;
  }

  public void resetScroll () {
    manager.scrollToPositionWithOffset(0, 0);
    stopScroll();
  }

  public long calculateScrollingDistance () {
    int firstVisibleItemPosition = manager.findFirstVisibleItemPosition();
    long totalScrollBottom = 0;
    View view = manager.findViewByPosition(firstVisibleItemPosition);
    if (view != null) {
      totalScrollBottom = view.getBottom() - manager.getHeight();
    }

    int i = 0;
    while (i < firstVisibleItemPosition) {
      TGMessage msg = adapter.getMessage(i);
      if (msg != null) {
        int messageHeight = msg.getHeight();
        totalScrollBottom += messageHeight;
      }
      i++;
    }
    return totalScrollBottom;
  }

  private void scrollToBottom (boolean smooth) {
    stopScroll();

    if (!controller.sponsoredMessageLoaded) {
      requestSponsoredMessage();
    }

    if (!Config.SMOOTH_SCROLL_TO_BOTTOM_ENABLED || !smooth) {
      if (adapter.getBottomMessage() != null && adapter.getBottomMessage().isSponsored()) {
        controller.setScrollToBottomVisible(false, false, false);
        if (controller.canWriteMessages()) {
          manager.scrollToPosition(1);
        } else {
          manager.scrollToPositionWithOffset(1, Screen.dp(48f));
        }
      } else {
        manager.scrollToPositionWithOffset(0, 0);
      }
    } else {
      boolean needScrollBy = false;
      if (adapter.getMessage(0) != null) {
        TGMessage msg = adapter.getMessage(0);
        msg.buildLayout(getRecyclerWidth());
        needScrollBy = msg.getHeight() > getRecyclerHeight(); // FIXME this is needed, because smoothScrollToPosition does not take to the bottom of the last message.
      }
      if (needScrollBy) {
        // scrollToPositionWithOffset(0, 0, true);
        manager.scrollToPositionWithOffset(0, 0);
      } else {
        controller.getMessagesView().smoothScrollToPosition(0);
      }
    }
  }

  public MessagesController controller () {
    return controller;
  }

  private boolean isDemoGroupChat;
  private LongSparseArray<TdApi.User> demoParticipants;

  public void setDemoParticipants (LongSparseArray<TdApi.User> participants, boolean isGroupChat) {
    this.demoParticipants = participants;
    this.isDemoGroupChat = isGroupChat;
  }

  public TdApi.User demoParticipant (long userId) {
    return demoParticipants != null ? demoParticipants.get(userId) : null;
  }

  public boolean isDemoGroupChat () {
    return isDemoGroupChat;
  }

  public boolean useBubbles () {
    TdlibSettingsManager settings = controller().tdlib().settings();
    return (loader.isChannel() ? !settings.forcePlainModeInChannels() : settings.useBubbles()) || controller.inWallpaperMode();
  }

  private boolean useReactionBubblesValue;
  private boolean checkReactionBubbles () {
    if (tdlib.isUserChat(loader.getChatId())) {
      return false;
    }

    if (loader.isChannel() && Settings.instance().getBigReactionsInChannels()) {
      return true;
    }

    if (!loader.isChannel() && Settings.instance().getBigReactionsInChats()) {
      return true;
    }

    return false;
  }

  public boolean useReactionBubbles () {
    return useReactionBubblesValue;
  }



  @Nullable
  @Override
  public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
    if (loader.getChatId() != fromMessage.chatId) {
      return null;
    }

    int position = adapter.indexOfMessageContainer(fromMessage.id);
    if (position == -1) {
      return null;
    }
    TGMessage mainContainer = adapter.getMessage(position);

    @TdApi.MessageContent.Constructors final int contentType = fromMessage.content.getConstructor();
    ArrayList<TdApi.Message> out = new ArrayList<>();

    final int count = adapter.getMessageCount();

    RunnableData<TdApi.Message> messageCallback = message -> {
      if (filterMedia(message, contentType)) {
        out.add(message);
      }
    };

    // Grab all previous songs
    for (int i = count - 1; i > position; i--) {
      TGMessage container = adapter.getMessage(i);
      if (container != null) {
        container.iterate(messageCallback, false);
      }
    }

    // Grab current songs
    AtomicInteger originIndex = new AtomicInteger(-1);
    mainContainer.iterate(message -> {
      if (message.id == fromMessage.id) {
        originIndex.set(out.size());
      }
      messageCallback.runWithData(message);
    }, false);
    if (originIndex.get() == -1)
      throw new IllegalStateException();

    // Grab all next songs

    for (int i = position - 1; i >= 0; i--) {
      TGMessage container = adapter.getMessage(i);
      if (container != null) {
        container.iterate(messageCallback, false);
      }
    }

    if (out.size() == 1) {
      return null;
    }

    return new TGPlayerController.PlayList(out, originIndex.get());
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    return playListChatId != 0 && playListChatId == fromMessage.chatId && !isReverse;
  }

  private boolean filterMedia (TdApi.Message message, int contentType) {
    return !tdlib.messageSending(message) && contentType == message.content.getConstructor();
  }

  @Override
  public void onResult (final TdApi.Object object) {
    if (object.getConstructor() != TdApi.Ok.CONSTRUCTOR) {
      tdlib.ui().post(() -> {
        if (UI.getCurrentStackItem() == controller) {
          UI.showToast("open/close chat failed " + object.toString(), Toast.LENGTH_LONG);
        }
      });
    }
  }

  // Private Loaders

  private void loadFromStart (MessageId fromId, TGMessage startMessage) {
    adapter.reset(startMessage);
    manager.scrollToPositionWithOffset(0, 0);
    loader.loadFromStart(fromId);
    viewMessages();
  }

  private void loadFromMessage (MessageId messageId, int highlightMode, boolean force) {
    if (force) {
      adapter.reset(null);
    }
    loader.loadFromMessage(messageId, highlightMode, force);
    viewMessages();
  }

  private void loadPreviewMessages () {
    loader.loadPreviewMessages();
  }

  // Displays

  public void onNetworkRequestSent () {
    onChatAwaitFinish();
  }

  public void ensureContentHeight () {
    final int count = adapter.getMessageCount();
    if (count > 0 && (loader.canLoadTop() || loader.canLoadBottom())) {
      long messagesHeight = 0;
      for (int i = 0; i < count; i++) {
        messagesHeight += adapter.getMessage(i).getHeight();
      }
      if (messagesHeight < getRecyclerHeight()) {
        loader.loadMoreInAnyDirection();
      }
    }
  }

  private List<TdApi.Message> sentMessages;

  public void setSentMessages (List<TdApi.Message> sentMessages) {
    this.sentMessages = sentMessages;
  }

  @UiThread
  public void addSentMessages (List<TGMessage> messages) {
    if (messages != null) {
      for (TGMessage msg : messages) {
        if (msg != null)
          updateNewMessage(msg);
      }
    }
  }

  @WorkerThread
  private int indexOfSentMessage (long chatId, long messageId) {
    if (sentMessages != null && !sentMessages.isEmpty()) {
      int i = 0;
      for (TdApi.Message message : sentMessages) {
        if (message.chatId == chatId && message.id == messageId)
          return i;
        i++;
      }
    }
    return -1;
  }

  private boolean awaitingForPinnedMessages;

  private void onChatAwaitFinish () {
    // FIXME once TDLib will have onlyLocal flag in SearchChatMessages query
    boolean pinnedReceived = true; // pinnedMessages == null || pinnedMessages.hasReceivedInitialChunk();
    if (pinnedReceived) {
      controller.executeScheduledAnimation();
    } else {
      awaitingForPinnedMessages = true;
    }
  }

  public void updateAlbum (List<TdApi.Message> prevItems, Tdlib.Album album) {
    TdApi.Message prevNewestMessage = prevItems.get(0);
    if (loader.getChatId() != prevNewestMessage.chatId) {
      return;
    }
    TdApi.Message prevOldestMessage = prevItems.get(prevItems.size() - 1);
    int containerIndex = adapter.indexOfMessageContainer(prevNewestMessage.id);
    if (containerIndex != -1) {
      TGMessage targetAlbum = adapter.getMessage(containerIndex);
      int newerCount = 0;
      int olderCount = 0;
      for (TdApi.Message message : album.messages) {
        if (message.id > prevNewestMessage.id) {
          newerCount++;
        } else if (message.id < prevOldestMessage.id) {
          olderCount++;
        }
      }
      for (int i = newerCount - 1; i >= 0; i--) {
        targetAlbum.combineWith(album.messages.get(i), true);
      }
      for (int i = album.messages.size() - olderCount; i < album.messages.size(); i++) {
        targetAlbum.combineWith(album.messages.get(i), false);
      }
      targetAlbum.invalidateContent(targetAlbum);
    }
  }

  public void displayMessages (ArrayList<TGMessage> items, int mode, int scrollPosition, TGMessage scrollMessage, MessageId scrollMessageId, int highlightMode, boolean willRepeat) {
    int size = items.size();
    if (size == 0 && mode != MessagesLoader.MODE_INITIAL && mode != MessagesLoader.MODE_REPEAT_INITIAL) {
      if (!willRepeat) {
        onChatAwaitFinish();
      }
      return;
    }
    LongSparseArray<TdApi.ChatAdministrator> chatAdmins = this.chatAdmins;
    if (chatAdmins != null) {
      long adminUserId = 0;
      TdApi.ChatAdministrator administrator = null;
      for (TGMessage message : items) {
        if (message.getAdministrator() != null)
          continue;
        long userId = message.getSender().getUserId();
        if (adminUserId != userId) {
          adminUserId = userId;
          administrator = chatAdmins.get(adminUserId);
        }
        message.setAdministratorSign(administrator);
      }
    }
    switch (mode) {
      case MessagesLoader.MODE_INITIAL:
      case MessagesLoader.MODE_REPEAT_INITIAL: {
        if (mode == MessagesLoader.MODE_REPEAT_INITIAL) {
          adapter.resetMessages(items);
        } else {
          adapter.addMessages(items, true);
        }
        if (scrollMessage == null) {
          if (scrollMessageId != null && scrollMessageId.isHistoryStart() && !items.isEmpty()) {
            manager.scrollToPositionWithOffset(items.size() - 1, -items.get(items.size() - 1).getHeight());
          } else if (scrollPosition == 0) {
            manager.scrollToPositionWithOffset(0, 0);
          } else {
            manager.scrollToPosition(scrollPosition);
          }
        } else {
          scrollToMessage(scrollPosition, scrollMessage, highlightMode == HIGHLIGHT_MODE_NONE ? HIGHLIGHT_MODE_START : highlightMode, false, false);
        }
        if (!willRepeat) {
          onChatAwaitFinish();
        }
        viewMessages();
        break;
      }
      case MessagesLoader.MODE_MORE_TOP: {
        adapter.addMessages(items, true);
        onChatAwaitFinish();
        break;
      }
      case MessagesLoader.MODE_MORE_BOTTOM: {
        int firstIndex = manager.findFirstVisibleItemPosition();
        View view = manager.findViewByPosition(firstIndex);
        int currentOffset = view == null ? 0 : manager.getHeight() - view.getBottom();
        adapter.addMessages(items, false);
        manager.scrollToPositionWithOffset(firstIndex + items.size(), currentOffset);
        break;
      }
    }
  }

  // Bot info

  private TGMessage headerMessage;
  private boolean insertMessageOnLoad;

  private boolean shouldInsertHeaderMessage () {
    TGMessage message = adapter.getTopMessage();
    return message == null || message != headerMessage;
  }

  public void clearHeaderMessage () {
    if (this.headerMessage != null) {
      this.headerMessage.onDestroy();
    }
    this.headerMessage = null;
    this.insertMessageOnLoad = false;
  }

  public void setHeaderMessage (TGMessage headerMessage) {
    this.headerMessage = headerMessage;
    if (insertMessageOnLoad && shouldInsertHeaderMessage()) {
      if (headerMessage instanceof TGMessageBotInfo) {
        ((TGMessageBotInfo) headerMessage).setBoundAdapter(adapter);
      }
      adapter.addMessage(this.headerMessage, true, false);
      checkBotStart();
    }
  }

  public void onTopEndLoaded () {
    if (headerMessage != null && shouldInsertHeaderMessage()) {
      if (headerMessage instanceof TGMessageBotInfo) {
        ((TGMessageBotInfo) headerMessage).setBoundAdapter(adapter);
      }
      adapter.addMessage(headerMessage, true, false);
    } else {
      insertMessageOnLoad = true;
    }
    checkBotStart();
    onChatAwaitFinish();
    ensureContentHeight();
    saveScrollPosition();
  }

  public void onBottomEndLoaded () {
    onChatAwaitFinish();
    onCanLoadMoreBottomChanged();
  }

  public void onBottomEndChecked () {
    requestSponsoredMessage();
  }

  private void requestSponsoredMessage () {
    synchronized (controller) {
      if (controller.sponsoredMessageLoaded) {
        return;
      }

      loader.requestSponsoredMessage(loader.getChatId(), message -> {
        if (message == null) {
          controller.sponsoredMessageLoaded = true;
          return;
        }

        RunnableData<TGMessage> action = (lastMessage) -> {
          if (lastMessage == null) return;
          controller.sponsoredMessageLoaded = true;
          boolean isFirstItemVisible = manager.findFirstCompletelyVisibleItemPosition() == 0;
          adapter.addMessage(SponsoredMessageUtils.sponsoredToTgx(this, loader.getChatId(), lastMessage.getDate(), message), false, false);
          if (isFirstItemVisible && !isScrolling && !controller.canWriteMessages()) {
            manager.scrollToPositionWithOffset(1, Screen.dp(48f));
          }
        };

        TGMessage bottomMessage = findBottomMessage();
        if (bottomMessage != null) {
          action.runWithData(bottomMessage);
        } else {
          UI.post(() -> action.runWithData(findBottomMessage()), 1000L);
        }
      });
    }
  }

  private int getActiveMessageCount () {
    int messageCount = adapter.getMessageCount();
    if (messageCount > 0 && headerMessage != null && adapter.getMessage(messageCount - 1) == headerMessage) {
      messageCount--;
    }
    return messageCount;
  }

  public void checkBotStart () {
    if (getActiveMessageCount() == 0 && !loader.canLoadTop() && !loader.canLoadBottom() && !loader.isLoading()) {
      controller.showActionBotButton();
    }
  }

  public boolean isWasScrollByUser () {
    return wasScrollByUser;
  }

  // Utils

  public void clear () {
    adapter.clear(false);
    onTopEndLoaded();
  }

  public void invalidateViewAt (int index) {
    if (manager == null) {
      return;
    }
    View view = manager.findViewByPosition(index);
    if (view != null) {
      if (view instanceof MessageViewGroup) {
        Views.invalidateChildren((MessageViewGroup) view);
      } else {
        view.invalidate();
      }
    } else {
      adapter.notifyItemChanged(index);
    }
  }

  public View findMessageView (long chatId, long messageId) {
    if (loader.getChatId() == chatId) {
      int i = adapter.indexOfMessageContainer(messageId);
      if (i != -1) {
        return manager.findViewByPosition(i);
      }
    }
    return null;
  }

  // View utils

  private View findTopView () {
    int count = adapter.getMessageCount();
    return count == 0 ? manager.findViewByPosition(0) : manager.findViewByPosition(count - 1);
  }

  private View findBottomView () {
    return manager.findViewByPosition(0);
  }

  public void rebuildFirstItem (boolean byLayout, boolean changed) {
    if (adapter.getMessageCount() == 0) {
      return;
    }

    final TGMessage rawMsg = adapter.getTopMessage();
    if (rawMsg == null || !(rawMsg instanceof TGMessageBotInfo)) {
      return;
    }

    final TGMessageBotInfo msg = (TGMessageBotInfo) rawMsg;

    final View view = findTopView();
    if (view == null) {
      adapter.notifyItemChanged(adapter.getMessageCount() - 1);
      return;
    }

    final int oldHeight = msg.getCachedHeight();
    view.post(() -> {
      if (oldHeight != msg.getHeight()) {
        adapter.notifyItemChanged(adapter.getMessageCount() - 1);
      }
    });
  }

  public void rebuildLastItem () {
    if (adapter.getMessageCount() == 0) {
      return;
    }
    final TGMessage rawMsg = adapter.getBottomMessage();
    if (rawMsg != null) {
      rawMsg.rebuildLayout();
      adapter.notifyItemChanged(0);
    }
  }

  public void rebuildLayouts () {
    ArrayList<TGMessage> items = adapter.getItems();
    if (items != null) {
      for (TGMessage m : items) {
        int height = m.getHeight();
        m.rebuildLayout();
        if (height != m.getHeight() && !useBubbles()) {
          m.requestLayout();
        } else {
          m.invalidate();
        }
      }
    }
  }

  public void onUpdateTextSize () {
    ArrayList<TGMessage> items = adapter.getItems();
    if (items != null) {
      for (TGMessage m : items) {
        m.onUpdateTextSize();
      }
    }
  }

  public void onMessageHeightChanged (final long chatId, final long messageId, final int oldHeight, final int newHeight) {
    final int heightDiff = oldHeight - newHeight;
    final int recyclerHeight = getRecyclerHeight();
    final View view = findMessageView(chatId, messageId);
    if (view == null) {
      return;
    }

    final int bottom = view.getBottom();
    if (bottom > recyclerHeight) {
      final int index = adapter.indexOfMessageContainer(messageId);
      scrollCompensation(view, heightDiff, index, recyclerHeight, bottom);
      return;
    }

    if (!isWasScrollByUser()) {
      final int unreadBadgeIndex = adapter.indexOfMessageWithUnreadSeparator();
      final int index = adapter.indexOfMessageContainer(messageId);
      if (unreadBadgeIndex != -1 && index != -1 && index <= unreadBadgeIndex) {
        View unreadBadgeView = manager.findViewByPosition(unreadBadgeIndex);
        if (unreadBadgeView != null && unreadBadgeView.getTop() <= controller.getTopOffset()) {
          scrollCompensation(view, heightDiff, index, recyclerHeight, bottom);
          return;
        }
      }
    }
  }

  private void scrollCompensation (View view, int heightDiff, int index, int recyclerHeight, int bottom) {
    OnGlobalLayoutListener listener = new OnGlobalLayoutListener(controller.getMessagesView(), view, heightDiff);
    listener.add();

    //tdlib.ui().post(() -> controller.getMessagesView().scrollBy(0, heightDiff));
    // controller.getMessagesView().scrollBy(0, heightDiff);
    // manager.scrollToPositionWithOffset(index, recyclerHeight - bottom + heightDiff);
  }

  private static class OnGlobalLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
    private MessagesRecyclerView recyclerView;
    private ViewTreeObserver observer;
    private int offset;

    OnGlobalLayoutListener (MessagesRecyclerView r, View v, int offset) {
      this.recyclerView = r;
      this.observer = v.getViewTreeObserver();
      this.offset = offset;
    }

    public void add () {
      add(observer, this);
    }

    @Override
    public void onGlobalLayout () {
      if (offset != 0) {
        recyclerView.scrollBy(0, offset);
        offset = 0;
      }

      remove(observer, this);
    }

    public static void add (ViewTreeObserver v, OnGlobalLayoutListener listener) {
      v.addOnGlobalLayoutListener(listener);
    }

    public static boolean remove (ViewTreeObserver v, OnGlobalLayoutListener listener) {
      if (v.isAlive()) {
        v.removeOnGlobalLayoutListener(listener);
        return true;
      }
      return false;
    }
  }

  public void modifyRecycler (Context context, RecyclerView recyclerView, LinearLayoutManager manager) {
    this.manager = manager;
    this.adapter = new MessagesAdapter(context, this, this.controller);

    recyclerView.clearOnScrollListeners();
    recyclerView.addOnScrollListener(listener);
    recyclerView.setAdapter(adapter);
  }

  public boolean isSecretChat () {
    return loader.isSecretChat();
  }

  public boolean isChannel () {
    return loader.isChannel();
  }

  // Updates

  private void updateNewMessage (TGMessage message) {
    switch (loader.getSpecialMode()) {
      case MessagesLoader.SPECIAL_MODE_RESTRICTED:
      case MessagesLoader.SPECIAL_MODE_SEARCH:
        return;
    }
    long messageThreadId = loader.getMessageThreadId();
    if (messageThreadId != 0 && message.getMessageThreadId() != messageThreadId) {
      return;
    }
    if (!message.isOutgoing()) {
      controller.checkSwitchPm(message.getMessage());
    }
    if (!loader.canLoadBottom()) {
      boolean atBottom = manager.findFirstVisibleItemPosition() == 0;
      TGMessage bottomMessage = adapter.getBottomMessage();
      if (bottomMessage != null && bottomMessage.combineWith(message.getMessage(), true)) {
        if (atBottom) {
          if (Config.VIEW_MESSAGES_BEFORE_SCROLL && message.markAsViewed()) {
            viewMessageInternal(message.getChatId(), messageThreadId, message.getId());
          }
        } else {
          bottomMessage.markAsUnread();
        }
        bottomMessage.invalidateContentReceiver(message.getId(), -1);
        message.onDestroy();
        return;
      }
      boolean scrollToBottom = (message.isSending() || (atBottom && (!message.isOld() || message.isChatMember()))) && !message.isSponsored();
      // message.mergeWith(bottomMessage, true);
      if (scrollToBottom) {
        boolean hasScrolled = adapter.addMessage(message, false, scrollToBottom);
        if (Config.VIEW_MESSAGES_BEFORE_SCROLL && message.markAsViewed()) {
          viewMessageInternal(message.getChatId(), messageThreadId, message.getId());
        }
        if (!hasScrolled)
          scrollToBottom(false);
        if (message.isSending()) {
          revokeReturnMessages();
        }
      } else if (atBottom) {
        int scrollOffsetInPixels = 0;
        View view = manager.findViewByPosition(0);
        if (view != null && view.getParent() != null) {
          scrollOffsetInPixels = ((View) view.getParent()).getBottom() - view.getBottom();
        }

        boolean bottomFullyVisible = manager.findFirstCompletelyVisibleItemPosition() == 0;
        if (!adapter.addMessage(message, false, scrollToBottom)) {
          if (message.isSponsored() && bottomFullyVisible) {
            if (controller.canWriteMessages()) {
              manager.scrollToPosition(1);
            } else {
              manager.scrollToPositionWithOffset(1, Screen.dp(48f));
            }
          } else {
            manager.scrollToPositionWithOffset(0, scrollOffsetInPixels);
          }
        }
      } else {
        adapter.addMessage(message, false, scrollToBottom);
      }
    } else if (message.isSending()) {
      loadFromStart();
    }
  }

  @Override
  public void onMessageSendAcknowledged (final long chatId, final long messageId) {
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        int index = adapter.indexOfMessageContainer(messageId);
        if (index != -1 && adapter.getMessage(index).onMessageSendAcknowledged(messageId)) {
          invalidateViewAt(index);
        }
      }
    });
  }

  @Override
  public void onMessagePendingContentChanged (long chatId, long messageId) {
    tdlib.uiExecute(() -> {
      if (loader.getChatId() == chatId) {
        int index = adapter.indexOfMessageContainer(messageId);
        if (index != -1) {
          TGMessage msg = adapter.getItem(index);
          switch (msg.setMessagePendingContentChanged(chatId, messageId)) {
            case TGMessage.MESSAGE_INVALIDATED: {
              invalidateViewAt(index);
              break;
            }
            case TGMessage.MESSAGE_CHANGED: {
              getAdapter().notifyItemChanged(index);
              break;
            }
            case TGMessage.MESSAGE_REPLACE_REQUIRED: {
              TdApi.Message message = msg.getMessage(messageId);
              replaceMessage(msg, index, messageId, message);
              break;
            }
          }
        }
      }
    });
  }

  private void updateReturnToMessageId (long oldMessageId, long newMessageId) {
    if (hasReturnMessage()) {
      int index = ArrayUtils.indexOf(returnToMessageIds, oldMessageId);
      if (index >= 0) {
        this.returnToMessageIds[index] = newMessageId;
        Arrays.sort(this.returnToMessageIds);
        saveScrollPosition();
      }
    }
  }

  private void updateMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    updateReturnToMessageId(oldMessageId, message.id);
    int index = adapter.indexOfMessageContainer(oldMessageId);
    if (index != -1) {
      TGMessage msg = adapter.getItem(index);
      switch (msg.setSendSucceeded(message, oldMessageId)) {
        case TGMessage.MESSAGE_INVALIDATED: {
          invalidateViewAt(index);
          break;
        }
        case TGMessage.MESSAGE_CHANGED: {
          getAdapter().notifyItemChanged(index);
          break;
        }
        case TGMessage.MESSAGE_REPLACE_REQUIRED: {
          replaceMessage(msg, index, message.id, message);
          break;
        }
      }
      checkPositionInList(msg, index, message.id);
      viewMessages();
    }
  }

  private void checkPositionInList (TGMessage msg, int index, long newMessageId) {
    if (true) {
      return;
    }
    ArrayList<TGMessage> items = adapter.getItems();
    if (items != null) {
      items.remove(index);
      int i = Collections.binarySearch(items, msg, this);
      if (i >= 0) {
        items.add(index, msg);
        return;
      }
      int newIndex = i * -1 - 1;
      items.add(index, msg);
      if (newIndex != index) {
        int firstCompletelyVisibleItemPosition = manager.findFirstCompletelyVisibleItemPosition();
        adapter.moveItem(index, newIndex);
        if (firstCompletelyVisibleItemPosition == 0) {
          manager.scrollToPositionWithOffset(0, 0);
        }
      } else {
        items.add(newIndex, msg);
      }
    }
  }

  @Override
  public int compare (TGMessage o1, TGMessage o2) {
    return Long.compare(o2.getId(), o1.getId());
  }

  private void updateMessageSendFailed (TdApi.Message message, long oldMessageId) {
    updateReturnToMessageId(oldMessageId, message.id);
    int index = adapter.indexOfMessageContainer(oldMessageId);
    if (index != -1 && adapter.getItem(index).setSendFailed(message, oldMessageId)) {
      invalidateViewAt(index);
    }
  }

  private void replaceMessage (TGMessage msg, int index, long messageId, TdApi.Message message) {
    switch (msg.removeMessage(messageId)) {
      case TGMessage.REMOVE_COMPLETELY: {
        TGMessage replace = TGMessage.valueOf(this, message, msg.getChat(), chatAdmins);
        adapter.replaceItem(index, replace);
        break;
      }
      case TGMessage.REMOVE_COMBINATION: {
        // TODO
        Log.w("Warning: message combination breaking is not supported");
        break;
      }
    }
  }

  private void replaceMessageContent (TGMessage msg, int index, long messageId, TdApi.MessageContent content) {
    switch (msg.setMessageContent(messageId, content)) {
      case TGMessage.MESSAGE_INVALIDATED: {
        invalidateViewAt(index);
        break;
      }
      case TGMessage.MESSAGE_CHANGED: {
        getAdapter().notifyItemChanged(index);
        break;
      }
      case TGMessage.MESSAGE_NOT_CHANGED: {
        // Nothing to do
        break;
      }
      case TGMessage.MESSAGE_REPLACE_REQUIRED: {
        TdApi.Message message = msg.getMessage(messageId);
        message.content = content;
        replaceMessage(msg, index, messageId, message);
        break;
      }
    }
  }

  private void updateMessageContentChanged (long chatId, long messageId, TdApi.MessageContent content) {
    controller.onMessageChanged(chatId, messageId, content);
    if (adapter.isEmpty()) {
      return;
    }

    ArrayList<TGMessage> items = adapter.getItems();
    if (items == null || items.isEmpty()) {
      return;
    }

    int i = 0;
    for (TGMessage item : items) {
      TdApi.Message msg = item.getMessage();
      if (item.isDescendantOrSelf(messageId)) {
        replaceMessageContent(item, i, messageId, content);
      } else if (msg.replyToMessageId == messageId) {
        item.replaceReplyContent(messageId, content);
      }
      i++;
    }
  }

  private void updateMessageEdited (long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1) {
      switch (adapter.getItem(index).setMessageEdited(messageId, editDate, replyMarkup)) {
        case TGMessage.MESSAGE_INVALIDATED: {
          invalidateViewAt(index);
          break;
        }
        case TGMessage.MESSAGE_CHANGED: {
          getAdapter().notifyItemChanged(index);
          break;
        }
      }
    }
  }

  private void updateMessageOpened (long messageId) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1) {
      adapter.getItem(index).setMessageOpened(messageId);
      invalidateViewAt(index);
    }
  }

  public void updateMessageMentionRead (long messageId) {
    if (closestMentions != null && !closestMentions.isEmpty()) {
      int i = 0;
      for (TdApi.Message message : closestMentions) {
        if (message.id == messageId) {
          closestMentions.remove(i);
          break;
        }
        i++;
      }
    }
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1) {
      adapter.getItem(index).readMention(messageId);
      // TODO nothing?
    }
  }

  public void updateMessageReactionRead (long messageId) {
    if (closestUnreadReactions != null && !closestUnreadReactions.isEmpty()) {
      int i = 0;
      for (TdApi.Message message : closestUnreadReactions) {
        if (message.id == messageId) {
          closestUnreadReactions.remove(i);
          break;
        }
        i++;
      }
    }
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1) {
      adapter.getItem(index).readReaction(messageId);
      // TODO nothing?
    }
  }

  public void updateMessageUnreadReactions (long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1 && adapter.getItem(index).setMessageUnreadReactions(messageId, unreadReactions)) {
      invalidateViewAt(index);

      lastCheckedCount = 0;
      viewMessages();
    }
  }

  public void updateMessageInteractionInfo (long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1 && adapter.getItem(index).setMessageInteractionInfo(messageId, interactionInfo)) {
      invalidateViewAt(index);
    }
  }

  public void updateMessageIsPinned (long messageId, boolean isPinned) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1 && adapter.getItem(index).setIsPinned(messageId, isPinned)) {
      invalidateViewAt(index);
    }
  }

  public void updateMessagesDeleted (long chatId, long[] messageIds) {
    controller.removeReply(messageIds);
    controller.onMessagesDeleted(chatId, messageIds);

    int removedCount = 0;
    int selectedCount = controller.getSelectedMessageCount();
    boolean unselectedSomeMessages = false;

    int i = 0;
    main: while (i < adapter.getMessageCount()) {
      TGMessage item = adapter.getMessage(i);

      for (long messageId : messageIds) {
        switch (item.removeMessage(messageId)) {
          case TGMessage.REMOVE_NOTHING: {
            if (item.getMessage().replyToMessageId == messageId) {
              item.removeReply(messageId);
            }
            break;
          }
          case TGMessage.REMOVE_COMBINATION: {
            if (controller.unselectMessage(messageId, item)) {
              selectedCount--;
              unselectedSomeMessages = true;
            }
            if (++removedCount == messageIds.length) {
              break main;
            } else {
              continue main;
            }
          }
          case TGMessage.REMOVE_COMPLETELY: {
            TGMessage removed = adapter.removeItem(i);
            if (controller.unselectMessage(messageId, removed)) {
              selectedCount--;
              unselectedSomeMessages = true;
            }
            if (++removedCount == messageIds.length) {
              break main;
            } else {
              continue main;
            }
          }
        }
      }

      i++;
    }

    if (unselectedSomeMessages) {
      if (selectedCount == 0) {
        controller.closeSelectMode();
      } else {
        controller.setSelectedCount(selectedCount);
      }
    }

    if (removedCount > 0) {
      int messageCount = adapter.getMessageCount();
      if (messageCount == 0) {
        if (loader.canLoadTop() || loader.canLoadBottom()) {
          controller.reloadData();
        } else {
          clear();
        }
      } else if (messageCount == 1 && adapter.getMessage(0) instanceof TGMessageBotInfo) {
        rebuildFirstItem(false, false);
        controller.showActionBotButton();
      }
    }
  }

  public void updateChatReadOutbox (long lastReadOutboxMessageId) {
    TGMessage item, prevItem = null;
    for (int i = adapter.getMessageCount() - 1; i >= 0; i--) {
      item = adapter.getMessage(i);
      if (item.isOutgoing() && item.updateUnread(lastReadOutboxMessageId, prevItem)) {
        invalidateViewAt(i);
      }
      prevItem = item;
    }
  }

  // Collectors

  public MediaStack collectMedias (final long fromMessageId, @Nullable TdApi.SearchMessagesFilter filter) {
    ArrayList<TGMessage> items = adapter.getItems();
    if (items == null) {
      return null;
    }

    final ArrayList<MediaItem> result = new ArrayList<>();
    final boolean needGifs = filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR;

    final boolean[] found = new boolean[1];
    final int[] addedAfter = new int[1];

    RunnableData<TdApi.Message> callback = message -> {
      if (TD.isSecret(message))
        return;
      MediaItem item;
      if (needGifs) {
        item = message.content.getConstructor() == TdApi.MessageAnimation.CONSTRUCTOR ? MediaItem.valueOf(controller.context(), tdlib, message) : null;
      } else {
        item = message.content.getConstructor() == TdApi.MessagePhoto.CONSTRUCTOR || message.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR ? MediaItem.valueOf(controller.context(), tdlib, message) : null;
      }
      if (item != null) {
        result.add(0, item);
        if (found[0]) {
          addedAfter[0]++;
        }
      }
      if (!found[0] && message.id == fromMessageId) {
        found[0] = true;
      }
    };

    for (TGMessage parsedMessage : items) {
      parsedMessage.iterate(callback, true);
    }

    if (!found[0]) {
      return null;
    }

    MediaStack stack;
    stack = new MediaStack(controller.context(), tdlib);
    stack.set(addedAfter[0], result);

    return stack;
  }

  // Reading messages

  boolean viewMessageInternal (final long chatId, final long messageThreadId, final long messageId) {
    LongSet set = new LongSet(1);
    set.add(messageId);
    return viewMessagesInternal(chatId, messageThreadId, set, true);
  }

  boolean viewMessagesInternal (final long chatId, final long messageThreadId, final LongSet viewed, boolean append) {
    if (controller.isInForceTouchMode()) {
      return false;
    }

    final boolean isOpen = tdlib.isChatOpen(chatId);
    final long[] messageIds = viewed.toArray();

    if (isFocused && isOpen) {
      if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
        Log.i(Log.TAG_MESSAGES_LOADER, "Reading %d messages: %s", messageIds.length, Arrays.toString(messageIds));
      }
      if (Log.isEnabled(Log.TAG_FCM)) {
        Log.i(Log.TAG_FCM, "Reading %d messages from MessagesManager: %s", messageIds.length, Arrays.toString(messageIds));
      }

      if (BuildConfig.DEBUG && Settings.instance().dontReadMessages()) {

      } else {
        tdlib.client().send(new TdApi.ViewMessages(chatId, messageThreadId, messageIds, true), loader);
      }
      return true;
    }

    if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
      Log.i(Log.TAG_MESSAGES_LOADER, "Scheduling messages read. isFocused: %b, isOpen: %b, append: %b", isFocused, isOpen, append);
    }
    if (Config.READ_MESSAGES_BEFORE_FOCUS && append) {
      if (viewedChatId != chatId || viewedMessages == null) {
        viewedChatId = chatId;
        viewedMessageThreadId = messageThreadId;
        if (viewedMessages == null) {
          viewedMessages = new LongSet();
        } else {
          viewedMessages.clear();
        }
      }
      viewedMessages.addAll(viewed);
    }
    return false;
  }

  private boolean parentPaused, parentFocused, parentHidden;

  public void setParentHidden (boolean isHidden) {
    if (this.parentHidden != isHidden) {
      this.parentHidden = isHidden;
      checkFocus();
    }
  }

  public void setParentFocused (boolean isFocused) {
    if (this.parentFocused != isFocused) {
      this.parentFocused = isFocused;
      checkFocus();
    }
  }

  public void setParentPaused (boolean isPaused) {
    if (this.parentPaused != isPaused) {
      this.parentPaused = isPaused;
      checkFocus();
    }
  }

  private void checkFocus () {
    boolean passcodeShowing = controller.context().isPasscodeShowing();
    if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
      Log.i(Log.TAG_MESSAGES_LOADER, "MessagesManager checkFocus parentFocused:%b, parentPaused:%b, isFocused:%b, passcodeShowing:%b", parentFocused, parentPaused, isFocused, passcodeShowing);
    }
    setFocused(parentFocused && !parentPaused && !passcodeShowing);
  }

  public boolean needRemoveDuplicates () {
    switch (loader.getSpecialMode()) {
      case MessagesLoader.SPECIAL_MODE_EVENT_LOG:
      case MessagesLoader.SPECIAL_MODE_RESTRICTED:
        return false;
    }
    return true;
  }

  public boolean needSort () {
    return loader.getSpecialMode() == MessagesLoader.SPECIAL_MODE_SCHEDULED;
  }

  @Override
  public void onPasscodeShowing (BaseActivity context, boolean isShowing) {
    checkFocus();
  }

  private boolean isFocused;

  private long viewedChatId, viewedMessageThreadId;
  private LongSet viewedMessages;

  private void setFocused (boolean isFocused) {
    if (this.isFocused != isFocused) {
      if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
        Log.i(Log.TAG_MESSAGES_LOADER, "MessagesManager isFocused -> %b", isFocused);
      }
      this.isFocused = isFocused;
      if (isFocused) {
        onFocus();
      } else {
        onBlur();
      }
    }
  }

  private long refreshChatId;
  private long refreshMessageThreadId;
  private LongSparseArray<long[]> refreshMessageIds;
  private int refreshMaxDate;
  private CancellableRunnable refreshViewsRunnable;

  private static long timeTillNextRefresh (long millis) {
    long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
    if (seconds < 15) {
      return millis % 3000; // once per 3 seconds for the first 15 seconds
    }
    if (seconds < 60) {
      return millis % 5000; // once per 5 seconds for 15-60 seconds
    }
    long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
    if (minutes < 30) {
      return millis % 15000; // once per 15 seconds for 1-30 minutes
    }
    if (minutes < 60) {
      return millis % 30000; // once per 30 seconds for 30-60 minutes
    }
    return millis % 60000; // once per minute
  }

  private void cancelRefresh () {
    if (refreshViewsRunnable != null) {
      refreshViewsRunnable.cancel();
      refreshViewsRunnable = null;
    }
  }

  private void scheduleRefresh () {
    cancelRefresh();
    if (refreshChatId != 0 && refreshMessageIds != null && refreshMessageIds.size() > 0) {
      long ms = refreshMaxDate != 0 ? timeTillNextRefresh(tdlib.currentTimeMillis() - TimeUnit.SECONDS.toMillis(refreshMaxDate)) : 60000;
      refreshViewsRunnable = new CancellableRunnable() {
        @Override
        public void act () {
          ArrayList<TdApi.Function<?>> functions = new ArrayList<>();
          for (int i = 0; i < refreshMessageIds.size(); i++) {
            long chatId = refreshMessageIds.keyAt(i);
            long[] messageIds = refreshMessageIds.valueAt(i);
            functions.add(new TdApi.ViewMessages(chatId, chatId == refreshChatId ? refreshMessageThreadId : 0, messageIds, false));
          }
          tdlib.sendAll(functions.toArray(new TdApi.Function<?>[0]), tdlib.okHandler(), () -> tdlib.ui().post(MessagesManager.this::scheduleRefresh));
        }
      };
      refreshViewsRunnable.removeOnCancel(tdlib.ui());
      tdlib.ui().postDelayed(refreshViewsRunnable, ms);
    }
  }

  private void setRefreshMessages (long chatId, long messageThreadId, LongSparseArray<long[]> messageIds, int maxDate) {
    if (this.refreshChatId != chatId || this.refreshMessageThreadId != messageThreadId || refreshMaxDate != maxDate || !ArrayUtils.contentEquals(refreshMessageIds, messageIds)) {
      this.refreshChatId = chatId;
      this.refreshMessageThreadId = messageThreadId;
      this.refreshMessageIds = messageIds;
      this.refreshMaxDate = maxDate;
      scheduleRefresh();
    }
  }

  private boolean hasVisibleProtectedContent;

  public boolean hasVisibleProtectedContent () {
    return hasVisibleProtectedContent;
  }

  private void setHasVisibleProtectedContent (boolean hasProtectedContent) {
    if (this.hasVisibleProtectedContent != hasProtectedContent) {
      this.hasVisibleProtectedContent = hasProtectedContent;
      controller.context().checkDisallowScreenshots();
    }
  }

  private boolean isHeaderVisible;

  public boolean isHeaderVisible () {
    return isHeaderVisible;
  }

  public void setHeaderVisible (boolean headerVisible) {
    if (this.isHeaderVisible != headerVisible) {
      this.isHeaderVisible = headerVisible;
      TGMessage message = adapter.getMessage(getActiveMessageCount() - 1);
      if (message != null) {
        message.updateDate();
        controller.getMessagesView().invalidate();
      }
      // TODO show/hide pinned message
    }
  }

  public boolean isFocused () {
    return isFocused;
  }

  private void onBlur () {
    saveScrollPosition();
  }

  private void saveScrollPosition () {
    if (controller.isInForceTouchMode() || inSpecialMode() || Settings.instance().dontReadMessages()) {
      return;
    }

    if (!isFocused) {
      return;
    }

    int i = manager.findFirstVisibleItemPosition();

    long scrollChatId = 0;
    long scrollMessageId = 0, scrollMessageChatId = 0;
    long[] returnToMessageIds = this.returnToMessageIds;
    long[] scrollMessageOtherIds = null;
    int scrollOffsetInPixels = 0;
    boolean readFully = false;
    long topEndMessageId = 0;

    if (i != -1 && MessagesHolder.isMessageType(adapter.getItemViewType(i))) {
      TGMessage message = adapter.getMessage(i);
      boolean isBottomSponsored = adapter.getBottomMessage() != null && adapter.getBottomMessage().isSponsored() && adapter.getMessageCount() > 1;

      ThreadInfo threadInfo = loader.getMessageThread();
      if (message != null && message.getChatId() != 0) {
        scrollMessageChatId = message.getChatId();
        scrollMessageId = message.getBiggestId();
        scrollMessageOtherIds = message.getOtherMessageIds(scrollMessageId);
        scrollChatId = message.getChatId();
        if (threadInfo != null) {
          readFully = scrollChatId == loader.getChatId() && threadInfo.getLastMessageId() == scrollMessageId;
        } else {
          TdApi.Chat chat = tdlib.chat(scrollChatId);
          readFully = chat != null && chat.lastMessage != null && chat.lastMessage.id == scrollMessageId;
        }
        View view = manager.findViewByPosition(i);
        if (view != null && view.getParent() != null) {
          scrollOffsetInPixels = ((View) view.getParent()).getBottom() - view.getBottom();
        }
        if (readFully && scrollOffsetInPixels == 0) {
          scrollMessageId = scrollMessageChatId = 0;
          scrollMessageOtherIds = null;
        } else if (isBottomSponsored) {
          if (message.isSponsored()) {
            // the bottom VISIBLE message is sponsored - no need to save that data
            scrollMessageId = scrollMessageChatId = scrollOffsetInPixels = 0;
            scrollMessageOtherIds = null;
            readFully = true;
          }
        }
      }

      //if (adapter.getBottomMessage())
    } else if (isTotallyEmpty()) {
      scrollChatId = loader != null ? loader.getChatId() : 0;
    } else {
      return;
    }

    if (!loader.canLoadTop()) {
      TGMessage topMessage = adapter.getMessage(getActiveMessageCount() - 1);
      if (topMessage != null && topMessage.getChatId() == loader.getChatId()) {
        topEndMessageId = topMessage.getSmallestId();
      }
    }

    if (scrollChatId != 0) {
      final int accountId = tdlib.id();
      final long chatId = loader.getChatId();
      final long messageThreadId = loader.getMessageThreadId();
      Settings.instance().setScrollMessageId(accountId,
        chatId, messageThreadId,
        new Settings.SavedMessageId(
          new MessageId(scrollMessageChatId, scrollMessageId, scrollMessageOtherIds),
          scrollOffsetInPixels,
          returnToMessageIds,
          readFully, topEndMessageId
        ));

      if (pinnedMessages != null && chatId == scrollMessageChatId) {
        pinnedMessages.ensureMessageAvailability(scrollMessageId);
      }
    }
  }

  public void onMissedMessagesHintReceived () {
    loader.setCanLoadBottom();
    onCanLoadMoreBottomChanged();
  }

  private void onFocus () {
    if (Config.READ_MESSAGES_BEFORE_FOCUS && viewedChatId != 0l && viewMessagesInternal(viewedChatId, viewedMessageThreadId, viewedMessages, false)) {
      viewedChatId = 0l;
      viewedMessageThreadId = 0l;
      viewedMessages = null;
    }
    viewMessages();
    saveScrollPosition();
  }

  // Highlight message id

  public static final int HIGHLIGHT_MODE_START  = -1;
  public static final int HIGHLIGHT_MODE_NONE   = 0;
  public static final int HIGHLIGHT_MODE_NORMAL = 1;
  public static final int HIGHLIGHT_MODE_UNREAD = 2;
  public static final int HIGHLIGHT_MODE_POSITION_RESTORE = 3;
  public static final int HIGHLIGHT_MODE_UNREAD_NEXT = 4;
  public static final int HIGHLIGHT_MODE_NORMAL_NEXT = 5;

  private MessageId highlightMessageId;
  private int highlightMode;
  public void setHighlightMessageId (MessageId messageId, int highlightMode) {
    this.highlightMessageId = messageId;
    this.highlightMode = highlightMode;
  }

  // Empty text

  public void setEmptyText (TextView view, boolean isLoaded) {
    if (loader.getSpecialMode() == MessagesLoader.SPECIAL_MODE_RESTRICTED) {
      String restrictionReason = tdlib.chatRestrictionReason(loader.getChatId());
      if (restrictionReason != null) {
        view.setText(restrictionReason);
        return;
      }
    }
    if (!isEventLog()) {
      view.setText(Lang.getString(isLoaded ? R.string.NoMessages : R.string.LoadingMessages));
      return;
    }

    if (!isLoaded) {
      view.setText(Lang.getString(R.string.LoadingActions));
      return;
    }

    if (StringUtils.isEmpty(eventLogQuery) && eventLogUserIds == null && eventLogFilters == null) {
      view.setText(Strings.replaceBoldTokens(Lang.getString(isChannel() ? R.string.EventLogEmptyChannel : R.string.EventLogEmpty)));
    } else if (StringUtils.isEmpty(eventLogQuery)) {
      view.setText(Strings.replaceBoldTokens(Lang.getString(R.string.EventLogEmptySearch)));
    } else {
      view.setText(Lang.getStringBold(R.string.EventLogEmptyTextSearch, eventLogQuery));
    }
  }

  // Mentions

  private ArrayList<TdApi.Message> closestMentions;
  private CancellableResultHandler mentionsHandler;

  public int getMinimumDate () {
    if (!loader.canLoadTop()) {
      TGMessage msg = adapter.getTopMessage();
      if (msg != null) {
        return msg.getDate();
      }
    }
    return -1;
  }

  public int getMaximumDate () {
    if (!loader.canLoadBottom()) {
      TGMessage msg = adapter.getBottomMessage();
      if (msg != null) {
        return msg.getDate();
      }
    }
    return -1;
  }

  public void scrollToNextMention () {
    if (closestMentions != null && !closestMentions.isEmpty()) {
      TdApi.Message message = closestMentions.remove(0);
      highlightMessage(new MessageId(message.chatId, message.id), HIGHLIGHT_MODE_NORMAL, null, true);
      return;
    }
    if (mentionsHandler != null) {
      return;
    }
    final long fromMessageId;
    if (lastViewedMention != 0)  {
      fromMessageId = lastViewedMention;
    } else {
      TGMessage message = adapter.getTopMessage();
      if (message == null) {
        return;
      }
      fromMessageId = message.getBiggestId();
    }
    final long chatId = loader.getChatId();
    final long messageThreadId = loader.getMessageThreadId();
    final boolean[] isRetry = new boolean[1];
    mentionsHandler = new CancellableResultHandler() {
      @Override
      public void processResult (final TdApi.Object object) {
        if (object.getConstructor() == TdApi.Messages.CONSTRUCTOR) {
          TdApi.Messages messages = (TdApi.Messages) object;
          if (messages.totalCount > 0 && messages.messages.length == 0 && !isRetry[0]) {
            isRetry[0] = true;
            tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, null, 0, 0, 10, new TdApi.SearchMessagesFilterUnreadMention(), messageThreadId), this);
          } else {
            setMentions(this, messages, isRetry[0] ? 0 : fromMessageId);
          }
        } else {
          setMentions(this, null, fromMessageId);
        }
      }
    };
    tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, null, fromMessageId, -9, 10, new TdApi.SearchMessagesFilterUnreadMention(), messageThreadId), mentionsHandler);
  }

  private void setMentions (final CancellableResultHandler handler, final TdApi.Messages messages, final long fromMessageId) {
    tdlib.ui().post(() -> {
      if (handler == mentionsHandler) {
        mentionsHandler = null;
        if (messages != null && messages.messages.length > 0) {
          setMentionsImpl(messages.messages, fromMessageId);
        }
      }
    });
  }

  private void setMentionsImpl (final TdApi.Message[] messages, final long fromMessageId) {
    ArrayList<TdApi.Message> mentions = new ArrayList<>(messages.length);
    Collections.addAll(mentions, messages);
    this.closestMentions = mentions;
    if (!mentions.isEmpty()) {
      scrollToNextMention();
    }
  }



  // Reactions

  private ArrayList<TdApi.Message> closestUnreadReactions;
  private CancellableResultHandler reactionsHandler;
  private long lastViewedReaction = 0;

  private void setUnreadReactions (final CancellableResultHandler handler, final TdApi.Messages messages, final long fromMessageId) {
    tdlib.ui().post(() -> {
      if (handler == reactionsHandler) {
        reactionsHandler = null;
        if (messages != null && messages.messages.length > 0) {
          setUnreadReactionsImpl(messages.messages, fromMessageId);
        }
      }
    });
  }

  private void setUnreadReactionsImpl (final TdApi.Message[] messages, final long fromMessageId) {
    ArrayList<TdApi.Message> reactions = new ArrayList<>(messages.length);
    Collections.addAll(reactions, messages);
    this.closestUnreadReactions = reactions;
    if (!reactions.isEmpty()) {
      scrollToNextUnreadReaction();
    }
  }

  public void scrollToNextUnreadReaction () {
    if (closestUnreadReactions != null && !closestUnreadReactions.isEmpty()) {
      TdApi.Message message = closestUnreadReactions.remove(0);
      highlightMessage(new MessageId(message.chatId, message.id), HIGHLIGHT_MODE_NORMAL, null, true);
      return;
    }
    if (reactionsHandler != null) {
      return;
    }
    final long fromMessageId;
    if (lastViewedReaction != 0)  {
      fromMessageId = lastViewedReaction;
    } else {
      TGMessage message = adapter.getTopMessage();
      if (message == null) {
        return;
      }
      fromMessageId = message.getBiggestId();
    }
    final long chatId = loader.getChatId();
    final long messageThreadId = loader.getMessageThreadId();
    final boolean[] isRetry = new boolean[1];
    reactionsHandler = new CancellableResultHandler() {
      @Override
      public void processResult (final TdApi.Object object) {
        if (object.getConstructor() == TdApi.Messages.CONSTRUCTOR) {
          TdApi.Messages messages = (TdApi.Messages) object;
          if (messages.totalCount > 0 && messages.messages.length == 0 && !isRetry[0]) {
            isRetry[0] = true;
            tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, null, 0, 0, 10, new TdApi.SearchMessagesFilterUnreadReaction(), messageThreadId), this);
          } else {
            setUnreadReactions(this, messages, isRetry[0] ? 0 : fromMessageId);
          }
        } else {
          setUnreadReactions(this, null, fromMessageId);
        }
      }
    };
    tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, null, fromMessageId, -9, 10, new TdApi.SearchMessagesFilterUnreadReaction(), messageThreadId), reactionsHandler);
  }



  // Search

  public boolean isReadyToSearch () {
    return !adapter.isEmpty();
  }

  private MessagesSearchManager searchManager;

  public void onPrepareToSearch () {
    if (searchManager == null) {
      searchManager = new MessagesSearchManager(tdlib, this);
    }
    searchManager.onPrepare();
  }

  public void search (long chatId, @Nullable ThreadInfo messageThread, TdApi.MessageSender sender, boolean isSecret, String input) {
    if (isEventLog()) {
      applyEventLogFilters(eventLogFilters, input, eventLogUserIds);
    } else {
      searchManager.search(messageThread != null ? messageThread.getChatId() : chatId, messageThread != null ? messageThread.getMessageThreadId() : 0, sender, isSecret, input);
    }
  }

  public void onDestroySearch () {
    searchManager.onDismiss();
    if (isEventLog()) {
      applyEventLogFilters(eventLogFilters, "", eventLogUserIds);
    }
  }

  public void moveToNextResult (boolean next) {
    searchManager.moveToNext(next);
  }

  // Delegate

  public int getRecyclerHeight () {
    int height = manager.getHeight();
    if (height != 0 && controller.isFocused()) {
      return height;
    }
    return controller.makeGuessAboutHeight();
  }

  public int getTopOverlayOffset () {
    return controller.getTopOffset() + controller.getBottomOffset();
  }

  public int getTargetHeight () {
    return getRecyclerHeight() - controller.getTopOffset();
  }

  public int getRecyclerWidth () {
    final int width = manager.getWidth();
    if (width != 0 && controller.isFocused()) {
      return width;
    }
    return controller.makeGuessAboutWidth();
  }

  private void stopScroll () {
    RecyclerView recyclerView = controller.getMessagesView();
    if (recyclerView != null) {
      recyclerView.stopScroll();
    }
  }

  private int calculateScrollBy (int index, int offset) {
    int firstVisibleItemPosition = manager.findFirstVisibleItemPosition();

    long totalScrollBottom = 0;
    View view = manager.findViewByPosition(firstVisibleItemPosition);
    if (view != null) {
      totalScrollBottom = view.getBottom() - manager.getHeight();
    }

    long desiredScrollBottom = -offset;
    long maxScrollBottom = -manager.getHeight();

    int i = 0;
    int messageCount = adapter.getMessageCount();
    while (i < messageCount) {
      int messageHeight = adapter.getMessage(i).getHeight();
      if (i < firstVisibleItemPosition) {
        totalScrollBottom += messageHeight;
      }
      if (i < index) {
        desiredScrollBottom += messageHeight;
      }
      maxScrollBottom += messageHeight;
      i++;
    }

    if (maxScrollBottom > 0) {
      desiredScrollBottom = Math.max(0, Math.min(maxScrollBottom, desiredScrollBottom));
      if (desiredScrollBottom != totalScrollBottom) {
        return (int) (totalScrollBottom - desiredScrollBottom);
      }
    }

    return 0;
  }

  private void scrollToPositionWithOffset (final int index, final int offset, boolean smooth) {
    stopScroll();

    if (smooth) {
      int scrollBy = calculateScrollBy(index, offset);
      if (Math.abs(scrollBy) < controller.getMessagesView().getMeasuredHeight()) {
        controller.getMessagesView().smoothScrollBy(0, scrollBy);
      } else {
        manager.scrollToPositionWithOffset(index, offset);
      }
    } else {
      manager.scrollToPositionWithOffset(index, offset);
    }


  }

  private void scrollToMessage (int index, TGMessage scrollMessage, int highlightMode, boolean smooth, boolean blocking) {
    if (highlightMode == HIGHLIGHT_MODE_START) {
      scrollToPositionWithOffset(index, 0, false);
      return;
    }
    final int accountId = tdlib.id();
    if (highlightMode == HIGHLIGHT_MODE_POSITION_RESTORE) {
      Settings.SavedMessageId messageId = Settings.instance().getScrollMessageId(accountId, loader.getChatId(), loader.getMessageThreadId());
      int offset;
      if (messageId != null) {
        this.returnToMessageIds = messageId.returnToMessageIds;
        offset = messageId.offsetPixels;
      } else {
        offset = 0;
      }
      this.returnToMessageIds = messageId != null ? messageId.returnToMessageIds : null;
      scrollToPositionWithOffset(index, offset, false);
      checkScrollToBottomButton();
      return;
    }

    final int offset = 0; // controller.isPinnedMessageVisible() ? controller.getPinnedMessageHeight() : 0;
    final int width = getRecyclerWidth();
    int height = getTargetHeight();

    scrollMessage.buildLayout(width);
    int fullHeight = scrollMessage.getHeight();

    if (fullHeight > height - offset) {
      height -= offset;
    }

    if (highlightMode == HIGHLIGHT_MODE_UNREAD || highlightMode == HIGHLIGHT_MODE_UNREAD_NEXT || fullHeight + scrollMessage.findTopEdge() >= height) {
      scrollToPositionWithOffset(index, height - fullHeight, smooth);
      wasScrollByUser = false;
    } else {
      scrollToPositionWithOffset(index, height / 2 - fullHeight / 2 + scrollMessage.findTopEdge(), smooth);
    }

    // manager.scrollToPositionWithOffset(index, 0);

    /*int padding = scrollMessage.findTopEdge();
    if ((fullHeight - padding) > height) {
      manager.scrollToPositionWithOffset(index, height - fullHeight + padding);
    } else {
      int itemHeight = (int) ((float) (fullHeight - padding) * .5f);
      manager.scrollToPositionWithOffset(index, (int) ((float) height * .5f - itemHeight));
    }
    context.showScrollButton(animateScrollButton);*/
  }

  public boolean isAtVeryBottom () {
    View view = findBottomView();
    return view != null && view.getBottom() == ((View) view.getParent()).getMeasuredHeight();
  }

  /*public boolean canApplyRecyclerOffsets () {
    return (highlightMode != HIGHLIGHT_MODE_POSITION_RESTORE && !isAtVeryBottom()) || (highlightMode == HIGHLIGHT_MODE_UNREAD && unreadBadgeIsUnderTopMessage());
  }

  private boolean unreadBadgeIsUnderTopMessage () {
    if (highlightMessageId == null) {
      return false;
    }
    int i = adapter.indexOfMessageContainer(highlightMessageId);
    if (i != -1) {
      View view = manager.findViewByPosition(i);
      TGMessage msg = adapter.getMessage(i);
      return view != null && view.getTop() <= controller.getPinnedMessageHeight() && msg != null && msg.hasUnreadBadge();
    }
    return false;
  }*/

  private long[] returnToMessageIds;

  public long[] extendReturnToMessageIdStack (MessageId addMessageId) {
    if (!hasReturnMessage() || !highlightMessageId.compareTo(addMessageId)) {
      return new long[] {addMessageId.getMessageId()};
    }
    return ArrayUtils.addElement(returnToMessageIds, addMessageId.getMessageId());
  }

  public void highlightMessage (MessageId messageId, int highlightMode, long[] returnToMessageIds, boolean allowSmooth) {
    if (isEventLog()) {
      return;
    }

    this.highlightMessageId = messageId;
    this.highlightMode = highlightMode;
    if (!Arrays.equals(this.returnToMessageIds, returnToMessageIds)) {
      this.returnToMessageIds = returnToMessageIds;
      saveScrollPosition();
      checkScrollToBottomButton();
    }
    int index = adapter.indexOfMessageContainer(messageId);
    if (index == -1) {
      resetByMessage(messageId, highlightMode);
    } else {
      if ((highlightMode == HIGHLIGHT_MODE_UNREAD_NEXT || highlightMode == HIGHLIGHT_MODE_NORMAL_NEXT) && index > 0) {
        index--;
      }
      TGMessage msg = adapter.getMessage(index);
      msg.highlight(true);
      scrollToMessage(index, msg, highlightMode, allowSmooth, false);
    }
  }

  @Override
  public void showSearchResult (int index, int totalCount, MessageId messageId) {
    switch (index) {
      case MessagesSearchManager.STATE_LOADING: {
        controller.onChatSearchStarted();
        break;
      }
      case MessagesSearchManager.STATE_NO_INPUT: {
        controller.onChatSearchFinished("", -1, -1);
        break;
      }
      case MessagesSearchManager.STATE_NO_RESULTS: {
        controller.onChatSearchFinished(Lang.getXofY(0, 0), 0, 0);
        break;
      }
      default: {
        controller.onChatSearchFinished(Lang.getXofY(index + 1, totalCount), index, totalCount);
        highlightMessage(messageId, HIGHLIGHT_MODE_NORMAL, null, true);
        break;
      }
    }
  }

  @Override
  public void onAwaitNext () {
    // UI.showToast("Loading next", Toast.LENGTH_SHORT);
  }

  @Override
  public void onTryToLoadPrevious () {
    // UI.showToast("Animate try to load previous", Toast.LENGTH_SHORT);
  }

  @Override
  public void onTryToLoadNext () {
    // UI.showToast("Animate try to load next", Toast.LENGTH_SHORT);
  }

  public boolean centerMessage (final long chatId, final long messageId, boolean delayed, boolean centered) {
    // TODO if !centered, then make the view just visible, if it's hidden or partially visible
    if (loader.getChatId() != chatId) {
      return false;
    }
    final int i = adapter.indexOfMessageContainer(messageId);
    if (i == -1) {
      return false;
    }

    if (delayed) {
      tdlib.ui().postDelayed(() -> {
        if (loader.getChatId() == chatId) {
          int newIndex = adapter.indexOfMessageContainer(messageId);
          if (newIndex != -1) {
            scrollToMessage(newIndex, adapter.getMessage(newIndex), HIGHLIGHT_MODE_NONE, true, false);
          }
        }
      }, Config.SLOW_VIDEO_SWITCH ? 120 : 40);
    } else {
      scrollToMessage(i, adapter.getMessage(i), HIGHLIGHT_MODE_NONE, true, false);
    }

    // asdf

    return true;
  }

  // Unread

  public static final int CHATS_THRESHOLD = 1;

  public static boolean canGoUnread (TdApi.Chat chat, @Nullable ThreadInfo threadInfo) {
    if (threadInfo != null) {
      return threadInfo.hasUnreadMessages();
    }
    return chat.unreadCount >= CHATS_THRESHOLD &&
            chat.lastReadInboxMessageId != 0 && chat.lastReadInboxMessageId != MessageId.MAX_VALID_ID &&
            chat.lastMessage != null && chat.lastMessage.id > chat.lastReadInboxMessageId &&
            !chat.lastMessage.isOutgoing;
  }

  public static int getAnchorHighlightMode (int accountId, TdApi.Chat chat, @Nullable ThreadInfo threadInfo) {
    if (chat == null) {
      return HIGHLIGHT_MODE_NONE;
    }
    boolean canGoUnread = canGoUnread(chat, threadInfo);
    long messageThreadId = threadInfo != null ? threadInfo.getMessageThreadId() : 0;
    Settings.SavedMessageId messageId = Settings.instance().getScrollMessageId(accountId, chat.id, messageThreadId);
    boolean preferUnreadFirst = messageId == null || messageId.readFully;
    if (preferUnreadFirst) {
      if (canGoUnread)
        return HIGHLIGHT_MODE_UNREAD;
      if (messageId != null && messageId.id.getMessageId() != 0)
        return HIGHLIGHT_MODE_POSITION_RESTORE;
    } else {
      if (messageId != null && messageId.id.getMessageId() != 0)
        return HIGHLIGHT_MODE_POSITION_RESTORE;
      if (canGoUnread)
        return HIGHLIGHT_MODE_UNREAD;
    }
    return HIGHLIGHT_MODE_NONE;
  }

  public static MessageId getAnchorMessageId (int accountId, TdApi.Chat chat, @Nullable ThreadInfo threadInfo, int anchorMode) {
    switch (anchorMode) {
      case HIGHLIGHT_MODE_POSITION_RESTORE: {
        Settings.SavedMessageId messageId = Settings.instance().getScrollMessageId(
          accountId, chat.id, threadInfo != null ? threadInfo.getMessageThreadId() : 0
        );
        return messageId != null && messageId.id.getMessageId() != 0 ? messageId.id : null;
      }
      case HIGHLIGHT_MODE_UNREAD:
      case HIGHLIGHT_MODE_UNREAD_NEXT: {
        if (threadInfo != null) {
          return new MessageId(threadInfo.getChatId(), threadInfo.getLastReadMessageId() == 0 ? MessageId.MIN_VALID_ID : threadInfo.getLastReadMessageId());
        } else if (chat.lastReadOutboxMessageId == MessageId.MAX_VALID_ID || ChatId.isMultiChat(chat.id)) {
          return new MessageId(chat.id, chat.lastReadInboxMessageId);
        } else {
          return new MessageId(chat.id, Math.max(chat.lastReadOutboxMessageId, chat.lastReadInboxMessageId));
        }
      }
      case HIGHLIGHT_MODE_NORMAL:
      case HIGHLIGHT_MODE_NORMAL_NEXT:
      default: {
        return null;
      }
    }
  }

  // Subscriber

  private long lastSubscribedChatId;

  private void subscribeForUpdates () {
    long chatId = loader.getChatId();
    if (lastSubscribedChatId != chatId) {
      if (lastSubscribedChatId != 0) {
        controller.unsubscribeFromUpdates(lastSubscribedChatId);
        tdlib.listeners().unsubscribeFromMessageUpdates(lastSubscribedChatId, this);
        tdlib.listeners().unsubscribeFromMessageEditUpdates(lastSubscribedChatId, this);
        tdlib.cache().removeChatMemberStatusListener(lastSubscribedChatId, this);
      }
      lastSubscribedChatId = chatId;
      if (chatId != 0) {
        controller.subscribeToUpdates(chatId);
        tdlib.listeners().subscribeToMessageUpdates(chatId, this);
        tdlib.listeners().subscribeToMessageEditUpdates(chatId, this);
        tdlib.cache().addChatMemberStatusListener(lastSubscribedChatId, this);
      }
    }
  }

  private void unsubscribeFromUpdates () {
    if (lastSubscribedChatId != 0) {
      controller.unsubscribeFromUpdates(lastSubscribedChatId);
      tdlib.listeners().unsubscribeFromMessageUpdates(lastSubscribedChatId, this);
      lastSubscribedChatId = 0;
    }
  }

  // Updates

  private boolean areScheduled () {
    return loader.getSpecialMode() == MessagesLoader.SPECIAL_MODE_SCHEDULED;
  }

  @Override
  public void onNewMessage (final TdApi.Message message) {
    if ((message.schedulingState != null) == areScheduled()) {
      if (indexOfSentMessage(message.chatId, message.id) != -1)
        return;
      final TdApi.Chat chat = tdlib.chatStrict(message.chatId);
      final TGMessage parsedMessage = TGMessage.valueOf(this, message, chat, chatAdmins);
      showMessage(chat.id, parsedMessage);
    } else if (isFocused() && (message.schedulingState != null)) {
      controller.viewScheduledMessages(true);
    }
  }

  private void showMessage (final long chatId, final TGMessage message) {
    message.prepareLayout();
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateNewMessage(message);
      }
    });
  }

  public List<TGMessage> parseMessages (List<TdApi.Message> messages) {
    final List<TGMessage> parsedMessages = new ArrayList<>(messages.size());
    final TdApi.Chat chat = tdlib.chatStrict(messages.get(0).chatId);
    TGMessage cur = null;
    LongSparseArray<TdApi.ChatAdministrator> chatAdmins = this.chatAdmins;
    for (TdApi.Message message : messages) {
      if (cur != null) {
        if (cur.combineWith(message, true)) {
          continue;
        }
        cur.prepareLayout();
        parsedMessages.add(cur);
      }
      cur = TGMessage.valueOf(this, message, chat, chatAdmins);
    }
    if (cur != null) {
      cur.prepareLayout();
      parsedMessages.add(cur);
    }
    return parsedMessages;
  }

  /*@Override
  public void __onNewMessages (TdApi.Message[] messages) {
    final TdApi.Chat chat = tdlib.chatStrict(messages[0].chatId);
    TGMessage cur = null;
    SparseArrayCompat<TdApi.ChatAdministrator> chatAdmins = this.chatAdmins;
    for (TdApi.Message message : messages) {
      if (cur != null) {
        if (cur.combineWith(message, true)) {
          continue;
        }
        showMessage(chat.id, cur);
      }
      cur = TGMessage.valueOf(this, message, chat, chatAdmins);
    }
    if (cur != null) {
      showMessage(chat.id, cur);
    }
  }*/

  @Override
  public void onMessageSendSucceeded (final TdApi.Message message, final long oldMessageId) {
    int sentMessageIndex = indexOfSentMessage(message.chatId, oldMessageId);
    if (sentMessageIndex != -1) {
      sentMessages.set(sentMessageIndex, message);
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == message.chatId) {
        updateMessageSendSucceeded(message, oldMessageId);
      }
    });
  }

  @Override
  public void onMessageSendFailed (final TdApi.Message message, final long oldMessageId, int errorCode, String errorMessage) {
    int sentMessageIndex = indexOfSentMessage(message.chatId, oldMessageId);
    if (sentMessageIndex != -1) {
      sentMessages.set(sentMessageIndex, message);
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == message.chatId) {
        updateMessageSendFailed(message, oldMessageId);
      }
    });
  }

  @Override
  public void onMessageContentChanged (final long chatId, final long messageId, final TdApi.MessageContent newContent) {
    int sentMessageIndex = indexOfSentMessage(chatId, messageId);
    if (sentMessageIndex != -1) {
      sentMessages.get(sentMessageIndex).content = newContent;
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessageContentChanged(chatId, messageId, newContent);
      }
    });
  }

  @Override
  public void onMessageEdited (final long chatId, final long messageId, final int editDate, @Nullable final TdApi.ReplyMarkup replyMarkup) {
    int sentMessageIndex = indexOfSentMessage(chatId, messageId);
    if (sentMessageIndex != -1) {
      TdApi.Message msg = sentMessages.get(sentMessageIndex);
      msg.editDate = editDate;
      msg.replyMarkup = replyMarkup;
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessageEdited(messageId, editDate, replyMarkup);
      }
    });
  }

  @Override
  public void onMessageOpened (final long chatId, final long messageId) {
    int sentMessageIndex = indexOfSentMessage(chatId, messageId);
    if (sentMessageIndex != -1) {
      TD.setMessageOpened(sentMessages.get(sentMessageIndex));
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessageOpened(messageId);
      }
    });
  }

  @Override
  public void onMessageUnreadReactionsChanged (long chatId, long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions, int unreadReactionCount) {
    int sentMessageIndex = indexOfSentMessage(chatId, messageId);
    if (sentMessageIndex != -1) {
      sentMessages.get(sentMessageIndex).unreadReactions = unreadReactions;
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessageUnreadReactions(messageId, unreadReactions);
      }
    });
  }

  @Override
  public void onMessageInteractionInfoChanged (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    int sentMessageIndex = indexOfSentMessage(chatId, messageId);
    if (sentMessageIndex != -1) {
      sentMessages.get(sentMessageIndex).interactionInfo = interactionInfo;
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessageInteractionInfo(messageId, interactionInfo);
      }
    });
  }

  @Override
  public void onMessagePinned (long chatId, long messageId, boolean isPinned) {
    int sentMessageIndex = indexOfSentMessage(chatId, messageId);
    if (sentMessageIndex != -1) {
      sentMessages.get(sentMessageIndex).isPinned = isPinned;
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessageIsPinned(messageId, isPinned);
      }
    });
  }

  @Override
  public void onMessageMentionRead (final long chatId, final long messageId) {
    int sentMessageIndex = indexOfSentMessage(chatId, messageId);
    if (sentMessageIndex != -1) {
      sentMessages.get(sentMessageIndex).containsUnreadMention = false;
      return;
    }
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessageMentionRead(messageId);
      }
    });
  }

  @Override
  public void onMessagesDeleted (final long chatId, final long[] messageIds) {
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        updateMessagesDeleted(chatId, messageIds);
      }
    });
  }

  // Colors

  public final int getOverlayColor (@ThemeColorId int plainModeColorId, @ThemeColorId int bubbleColorId, @ThemeColorId int bubbleNoWallpaperColorId, @ThemeProperty int overridePropertyId) {
    if (!useBubbles())
      return Theme.getColor(plainModeColorId);
    float transparency = controller().wallpaper().getBackgroundTransparency();
    if (transparency == 1f) {
      return Theme.getColor(bubbleNoWallpaperColorId);
    } else {
      int color = Theme.getColor(bubbleColorId);
      float override = Theme.getOverrideProperty(overridePropertyId);
      if (override > 0f)
        color = ColorUtils.fromToArgb(color, controller().wallpaper().getDefaultOverlayColor(color, false), override);
      if (transparency > 0f)
        color = ColorUtils.fromToArgb(color, Theme.getColor(bubbleNoWallpaperColorId), transparency);
      return color;
    }
  }

  public final int getColor (@ThemeColorId int plainModeColorId, @ThemeColorId int bubbleColorId, @ThemeColorId int bubbleNoWallpaperColorId, @ThemeProperty int overridePropertyId) {
    if (!useBubbles())
      return Theme.getColor(plainModeColorId);
    float transparency = controller().wallpaper().getBackgroundTransparency();
    if (transparency == 1f) {
      return Theme.getColor(bubbleNoWallpaperColorId);
    } else {
      int color = Theme.getColor(bubbleColorId);
      float override = Theme.getOverrideProperty(overridePropertyId);
      if (override > 0f)
        color = ColorUtils.fromToArgb(color, controller().wallpaper().getDefaultOverlayColor(color, true), override);
      if (transparency > 0f)
        color = ColorUtils.fromToArgb(color, Theme.getColor(bubbleNoWallpaperColorId), transparency);
      return color;
    }
  }
}
