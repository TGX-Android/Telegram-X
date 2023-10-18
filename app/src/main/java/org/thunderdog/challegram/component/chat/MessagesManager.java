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
 * File created on 10/09/2015 at 16:42
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
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
import org.thunderdog.challegram.telegram.MessageListManager;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.MessageThreadListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.FeatureToggles;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CancellableResultHandler;
import org.thunderdog.challegram.util.ScrollJumpCompensator;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class MessagesManager implements Client.ResultHandler, MessagesSearchManager.Delegate,
  MessageListener, MessageEditListener, MessageThreadListener, Comparator<TGMessage>, TGPlayerController.PlayListBuilder, BaseActivity.PasscodeListener, TdlibCache.ChatMemberStatusChangeListener, TdlibSettingsManager.DismissMessageListener {
  private final MessagesController controller;
  private final Tdlib tdlib;
  private MessagesAdapter adapter;
  private LinearLayoutManager manager;
  private final RecyclerView.OnScrollListener listener;
  private final MessagesSearchManagerMiddleware searchMiddleware;

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
    this.searchMiddleware = new MessagesSearchManagerMiddleware(tdlib);
    this.loader = new MessagesLoader(this, searchMiddleware);
    this.listener = new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          controller.collapsePinnedMessagesBar(true);
          if (Settings.instance().needHideChatKeyboardOnScroll()) {
            controller.hideAllKeyboards();
          }
          wasScrollByUser = true;
        }
        boolean isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
        if (MessagesManager.this.isScrolling != isScrolling) {
          MessagesManager.this.isScrolling = isScrolling;
          if (!isScrolling) {
            viewMessages(true);
          }
        }
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          saveScrollPosition();
        }
        ((MessagesRecyclerView) recyclerView).setIsScrolling(newState != RecyclerView.SCROLL_STATE_IDLE);
      }

      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        viewMessages(true);
        if (dy == 0) {
          saveScrollPosition();
          ((MessagesRecyclerView) recyclerView).showDateForcely();
        }
        if (dy != 0 && !hasScrolled && loader.getChatId() != 0) {
          hasScrolled = true;
          controller.onInteractedWithContent();
          controller.onFirstChatScroll();
        }
        controller.context().reactionsOverlayManager().addOffset(0, -dy);
      }
    };

    this.useReactionBubblesValue = checkReactionBubbles();
    this.usedTranslateStyleMode = checkTranslateStyleMode();
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
        if (message != null && !message.canBeSaved() && !message.isSponsoredMessage()) {
          hasVisibleProtectedContent = true;
          break;
        }
      }
    }

    setHasVisibleProtectedContent(hasVisibleProtectedContent);
  }

  public void viewMessages (boolean byScroll) {
    if (!byScroll && messageViewer != null) {
      messageViewer.run();
    }
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
        checkMessageThreadUnreadCounter(first);
      }
      checkScrollButton(first, last);
      checkMessageThreadHeaderPreview(last);

      controller.checkRoundVideo(first, last, true);
    }
  }

  public void onViewportMeasure () {
    viewMessages(false);
    saveScrollPosition();
  }

  private long lastViewedMentionMessageId;

  private boolean viewDisplayedMessages (int first, int last) {
    if (first == -1 || last == -1) {
      return false;
    }

    boolean headerVisible = false;
    boolean hasProtectedContent = false;

    for (int viewIndex = first; viewIndex <= last; viewIndex++) {
      View view = manager.findViewByPosition(viewIndex);
      TGMessage msg = view instanceof MessageProvider ? ((MessageProvider) view).getMessage() : null;
      if (msg != null && msg.getChatId() == loader.getChatId()) {
        if (!msg.canBeSaved() && !msg.isSponsoredMessage()) {
          hasProtectedContent = true;
        }
        if (isHeaderMessage(msg)) {
          headerVisible = true;
        }
      }
    }

    setHasVisibleProtectedContent(hasProtectedContent);
    setHeaderVisible(headerVisible);

    return true;
  }

  public interface MessageProvider extends TdlibUi.MessageProvider {
    TGMessage getMessage ();


    // MessageProvider

    @Override
    default boolean isSponsoredMessage () {
      TGMessage msg = getMessage();
      return msg != null && !msg.isFakeMessage() && msg.isSponsoredMessage();
    }

    @Override
    default TdApi.SponsoredMessage getVisibleSponsoredMessage () {
      TGMessage msg = getMessage();
      return msg != null && !msg.isFakeMessage() ? msg.getSponsoredMessage() : null;
    }

    @Override
    default boolean isMediaGroup () {
      TGMessage msg = getMessage();
      return msg != null && !msg.isFakeMessage() && msg.getCombinedMessageCount() > 1;
    }

    @Override
    default List<TdApi.Message> getVisibleMediaGroup () {
      TGMessage msg = getMessage();
      if (msg != null && !msg.isFakeMessage()) {
        return Arrays.asList(msg.getAllMessages());
      }
      return null;
    }

    @Override
    default TdApi.Message getVisibleMessage () {
      TGMessage msg = getMessage();
      return msg != null && !msg.isFakeMessage() ? msg.getMessage() : null;
    }

    @Override
    default long getVisibleChatId () {
      TGMessage msg = getMessage();
      return msg != null && !msg.isFakeMessage() ? msg.getChatId() : 0;
    }

    @Override
    default int getVisibleMessageFlags () {
      TGMessage msg = getMessage();
      if (msg != null && !msg.isFakeMessage()) {
        int flags;
        if (msg.isHot()) {
          flags = TdlibMessageViewer.Flags.NO_SCREENSHOT_NOTIFICATION;
        } else {
          flags = TdlibMessageViewer.Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION;
        }
        if (msg.needRefreshViewCount()) {
          flags |= TdlibMessageViewer.Flags.REFRESH_INTERACTION_INFO;
        }
        return flags;
      }
      return 0;
    }
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

  public void checkMessageThreadHeaderPreview (int last) {
    if (last == -1)
      return;
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread == null || !shouldShowThreadHeaderPreview())
      return;
    if (getActiveMessageCount() == 0) {
      controller.showHidePinnedMessage(false, null);
      return;
    }
    int messagePreviewHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
    View view = manager.findViewByPosition(last);
    if (view == null || view.getTop() > messagePreviewHeight) {
      controller.showHidePinnedMessage(false, null);
      return;
    }
    TGMessage msg = view instanceof MessageProvider ? ((MessageProvider) view).getMessage() : null;
    int headerBottom = isHeaderMessage(msg) ? view.getBottom() : Integer.MIN_VALUE;
    boolean showHeaderPreview = headerBottom <= messagePreviewHeight;
    controller.showHidePinnedMessage(showHeaderPreview, messageThread.getOldestMessage());
  }

  public boolean shouldShowThreadHeaderPreview () {
    return loader.getMessageThread() != null && !inSpecialMode();
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
    long chatId = loader.getChatId();
    setPinnedMessagesAvailable(pinnedMessages != null && pinnedMessages.isAvailable() &&
      !(tdlib.settings().isMessageDismissed(chatId, pinnedMessages.getMaxMessageId()) || tdlib.chatRestricted(chatId))
    );
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
    returnToMessageIds = null;
    highlightMode = 0;
    tdlib.settings().removePinnedMessageDismissListener(this);
    highlightMessageId = null;
    hasScrolled = false;
    lastViewedMentionMessageId = 0;
    lastViewedReactionMessageId = 0;
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
    messageViewer = null;
    adapter.clear(true);
    clearHeaderMessage();
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
    if (filter != null && Td.isPinnedFilter(filter)) {
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
        long chatId = loader.getChatId();
        setPinnedMessagesAvailable(!(tdlib.settings().isMessageDismissed(chatId, maxMessageId) || tdlib.chatRestricted(chatId)));
      } else if (!list.isAvailable()) {
        setPinnedMessagesAvailable(false);
      }
    }
  };
  private final MessageListManager.ChangeListener pinnedMessageListener = new MessageListManager.ChangeListener() {
    @Override
    public void onAvailabilityChanged (ListManager<TdApi.Message> list, boolean isAvailable) {
      long chatId = loader.getChatId();
      if (!isAvailable || !(tdlib.settings().hasDismissedMessages(chatId) || tdlib.chatRestricted(chatId))) {
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
    messageViewer = tdlib.ui().attachViewportToRecyclerView(loader.viewport(), controller.getMessagesView(), new TdlibUi.MessageViewCallback() {
      @Override
      public void onSponsoredMessageViewed (TdlibMessageViewer.Viewport viewport, View view, TdApi.SponsoredMessage sponsoredMessage, long flags, long viewId, boolean allowRequest) {
        MessageProvider provider = (MessageProvider) view;
        provider.getMessage().markAsViewed();
      }

      @Override
      public boolean isMessageContentVisible (TdlibMessageViewer.Viewport viewport, View view) {
        // TODO return false when "FOLLOW" bar overlaps the message entirely
        return true;
      }

      @Override
      public void onMessageViewed (TdlibMessageViewer.Viewport viewport, View view, TdApi.Message message, long flags, long viewId, boolean allowRequest) {
        if (inSpecialMode() || !allowRequest)
          return;
        MessageProvider provider = (MessageProvider) view;
        TGMessage msg = provider.getMessage();
        if (msg.markAsViewed() || msg.containsUnreadReactions()) {
          long messageId = msg.getBiggestId();
          if (msg.containsUnreadMention() && messageId > lastViewedMentionMessageId) {
            lastViewedMentionMessageId = messageId;
          }
          if (msg.containsUnreadReactions() && messageId > lastViewedReactionMessageId) {
            lastViewedReactionMessageId = messageId;
          }
        }
      }

      @Override
      public boolean needForceRead (TdlibMessageViewer.Viewport viewport) {
        return canRead();
      }

      @Override
      public boolean allowViewRequest (TdlibMessageViewer.Viewport viewport) {
        return isFocused;
      }
    });
    subscribeForUpdates();
    this.useReactionBubblesValue = checkReactionBubbles();
    this.usedTranslateStyleMode = checkTranslateStyleMode();
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
      if (adapter.getBottomMessage() != null && adapter.getBottomMessage().isSponsoredMessage()) {
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

  private int usedTranslateStyleMode;
  private int checkTranslateStyleMode () {
    return Settings.instance().getChatTranslateMode();
  }

  public int getUsedTranslateStyleMode () {
    return usedTranslateStyleMode;
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
    viewMessages(false);
  }

  private void loadFromMessage (MessageId messageId, int highlightMode, boolean force) {
    if (force) {
      adapter.reset(null);
    }
    loader.loadFromMessage(messageId, highlightMode, force);
    viewMessages(false);
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

  public void displayMessages (ArrayList<TGMessage> items, int mode, int scrollPosition, TGMessage scrollMessage, MessageId scrollMessageId, int highlightMode, boolean willRepeat, boolean canLoadTop) {
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
        checkTopEndReached(items, willRepeat, canLoadTop);
        if (scrollMessage == null) {
          if (scrollMessageId != null && scrollMessageId.isHistoryStart() && !items.isEmpty()) {
            scrollToHistoryStart();
          } else if (scrollMessageId != null && isHeaderMessage(scrollMessageId) && !adapter.isEmpty()) {
            scrollToMessage(adapter.getItemCount() - 1, adapter.getTopMessage(), highlightMode == HIGHLIGHT_MODE_NONE ? HIGHLIGHT_MODE_START : highlightMode, false, false);
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
        viewMessages(false);
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
    TGMessage topMessage = adapter.getTopMessage();
    if (loader.getMessageThread() != null) {
      return topMessage == null || !topMessage.isThreadHeader();
    }
    if (headerMessage != null) {
      return topMessage == null || topMessage != headerMessage;
    }
    return false;
  }

  private boolean isHeaderMessage (MessageId messageId) {
    TGMessage topMessage = adapter.getTopMessage();
    return isHeaderMessage(topMessage) &&
      topMessage.getChatId() == messageId.getChatId() &&
      topMessage.isDescendantOrSelf(messageId.getMessageId());
  }

  private boolean isHeaderMessage (@Nullable TGMessage message) {
    return message != null && (headerMessage == message || message.isThreadHeader());
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
    insertHeaderMessageIfNeeded();
    checkBotStart();
    onChatAwaitFinish();
    ensureContentHeight();
    saveScrollPosition();
    checkMessageThreadReplyCounter();
  }

  public void onBottomEndLoaded () {
    onChatAwaitFinish();
    onCanLoadMoreBottomChanged();
    checkMessageThreadReplyCounter();
    checkMessageThreadUnreadCounter();
  }

  public void onBottomEndChecked () {
    requestSponsoredMessage();
  }

  private void checkTopEndReached (List<TGMessage> items, boolean willRepeat, boolean canLoadTop) {
    if (shouldInsertHeaderMessage()) {
      boolean topEndReached;
      if (!canLoadTop) {
        topEndReached = true;
      } else if (!items.isEmpty()) {
        final int accountId = tdlib.id();
        Settings.SavedMessageId messageId = Settings.instance().getScrollMessageId(accountId, loader.getChatId(), loader.getMessageThreadId());
        if (messageId != null && messageId.topEndMessageId != 0) {
          TGMessage topMessage = items.get(items.size() - 1);
          topEndReached = topMessage.isDescendantOrSelf(messageId.topEndMessageId);
        } else {
          topEndReached = false;
        }
      } else {
        topEndReached = !willRepeat;
      }
      if (topEndReached) {
        insertHeaderMessageIfNeeded();
      }
    }
  }

  private void insertHeaderMessageIfNeeded () {
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null && shouldInsertHeaderMessage()) {
      TGMessage threadHeaderMessage = messageThread.buildHeaderMessage(this);
      if (threadHeaderMessage != null) {
        adapter.addMessage(threadHeaderMessage, true, false);
      }
    } else if (headerMessage != null && shouldInsertHeaderMessage()) {
      if (headerMessage instanceof TGMessageBotInfo) {
        ((TGMessageBotInfo) headerMessage).setBoundAdapter(adapter);
      }
      adapter.addMessage(headerMessage, true, false);
    } else {
      insertMessageOnLoad = true;
    }
  }

  private void requestSponsoredMessage () {
    // TODO rework this crap
    synchronized (controller) {
      if (controller.sponsoredMessageLoaded) {
        return;
      }

      loader.requestSponsoredMessage(loader.getChatId(), sponsoredMessages -> {
        if (sponsoredMessages == null || sponsoredMessages.messages.length == 0) {
          controller.sponsoredMessageLoaded = true;
          return;
        }

        RunnableData<TGMessage> action = (lastMessage) -> {
          if (lastMessage == null) return;
          controller.sponsoredMessageLoaded = true;
          boolean isFirstItemVisible = manager.findFirstCompletelyVisibleItemPosition() == 0;
          adapter.addMessage(SponsoredMessageUtils.sponsoredToTgx(this, loader.getChatId(), sponsoredMessages.messages[0]), false, false);
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
    if (messageCount > 0 && isHeaderMessage(adapter.getTopMessage())) {
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
    final int top = view.getTop();
    final int bottom = view.getBottom();
    if (bottom > recyclerHeight) {
      if (top > 0) {
        scrollCompensation(view, heightDiff);
      } else {
        int topHidden = -top;
        int bottomHidden = bottom - recyclerHeight;
        float compensationScale = ((float) bottomHidden) / ((float)(topHidden + bottomHidden));
        scrollCompensation(view, (int) (heightDiff * compensationScale));
      }
      return;
    }

    if (!isWasScrollByUser()) {
      final int unreadBadgeIndex = adapter.indexOfMessageWithUnreadSeparator();
      final int index = adapter.indexOfMessageContainer(messageId);
      if (unreadBadgeIndex != -1 && index != -1 && index <= unreadBadgeIndex) {
        View unreadBadgeView = manager.findViewByPosition(unreadBadgeIndex);
        if (unreadBadgeView != null && unreadBadgeView.getTop() <= controller.getTopOffset()) {
          scrollCompensation(view, heightDiff);
          return;
        }
      }
    }
  }

  private void scrollCompensation (View view, int offset) {
    ScrollJumpCompensator listener = new ScrollJumpCompensator(controller.getMessagesView(), view, offset);
    listener.add();
  }

  public void modifyRecycler (Context context, RecyclerView recyclerView, LinearLayoutManager manager) {
    this.manager = manager;
    this.adapter = new MessagesAdapter(context, this, this.controller);

    recyclerView.removeOnScrollListener(listener);
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
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateNewMessage(message);
    }
    if (!message.isOutgoing()) {
      controller.checkSwitchPm(message.getMessage());
    }
    if (!loader.canLoadBottom()) {
      boolean atBottom = manager.findFirstVisibleItemPosition() == 0;
      TGMessage bottomMessage = adapter.getBottomMessage();
      if (bottomMessage != null && bottomMessage.combineWith(message.getMessage(), true)) {
        if (!atBottom) {
          bottomMessage.markAsUnread();
        }
        bottomMessage.invalidateContentReceiver(message.getId(), -1);
        message.onDestroy();
        return;
      }
      boolean scrollToBottom = (message.isSending() || (atBottom && (!message.isOld() || message.isChatMember()))) && !message.isSponsoredMessage();
      // message.mergeWith(bottomMessage, true);
      if (scrollToBottom) {
        boolean hasScrolled = adapter.addMessage(message, false, scrollToBottom);
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
          if (message.isSponsoredMessage() && bottomFullyVisible) {
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
      ThreadInfo messageThread = loader.getMessageThread();
      if (messageThread != null) {
        messageThread.updateReadInbox(message);
      }
      viewMessages(false);
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

      ThreadInfo messageThread = loader.getMessageThread();
      if (messageThread != null) {
        messageThread.updateReadInbox(message);
      }
    }
  }

  private void replaceMessage (TGMessage msg, int index, long messageId, TdApi.Message message) {
    switch (msg.removeMessage(messageId)) {
      case TGMessage.REMOVE_COMPLETELY: {
        TGMessage replace = TGMessage.valueOf(this, message, msg.getChat(), msg.messagesController().getMessageThread(), chatAdmins);
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

  private void updateMessageContent (long chatId, long messageId, TdApi.MessageContent content) {
    controller.onMessageChanged(chatId, messageId, content);
    ArrayList<TGMessage> items = adapter.getItems();
    if (!adapter.isEmpty() && items != null) {
      int i = 0;
      for (TGMessage item : items) {
        if (item.isDescendantOrSelf(messageId)) {
          replaceMessageContent(item, i, messageId, content);
        } else {
          item.replaceReplyContent(chatId, messageId, content);
        }
        i++;
      }
    }
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateMessageContent(messageId, content);
    }
  }

  public void updateMessageTranslation (long chatId, long messageId, TdApi.FormattedText translatedText) {
    tdlib.ui().post(() -> {
      if (loader.getChatId() == chatId) {
        controller.onInlineTranslationChanged(chatId, messageId, translatedText);
        ArrayList<TGMessage> items = adapter.getItems();
        if (!adapter.isEmpty() && items != null) {
          for (TGMessage item : items) {
            item.replaceReplyTranslation(chatId, messageId, translatedText);
          }
        }
      }
    });
  }

  private void updateMessageEdited (long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1) {
      TGMessage message = adapter.getItem(index);
      switch (message.setMessageEdited(messageId, editDate, replyMarkup)) {
        case TGMessage.MESSAGE_INVALIDATED: {
          invalidateViewAt(index);
          break;
        }
        case TGMessage.MESSAGE_CHANGED: {
          getAdapter().notifyItemChanged(index);
          break;
        }
      }
      message.stopTranslated();
    }
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateMessageEdited(messageId, editDate, replyMarkup);
    }
  }

  private void updateMessageOpened (long messageId) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1) {
      adapter.getItem(index).setMessageOpened(messageId);
      invalidateViewAt(index);
    }
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateMessageOpened(messageId);
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
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateMessageMentionRead(messageId);
    }
  }

  public void updateMessageUnreadReactions (long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1 && adapter.getItem(index).setMessageUnreadReactions(messageId, unreadReactions)) {
      invalidateViewAt(index);
      viewMessages(false);
    }
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateMessageUnreadReactions(messageId, unreadReactions);
    }
  }

  public void updateMessageInteractionInfo (long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1 && adapter.getItem(index).setMessageInteractionInfo(messageId, interactionInfo)) {
      invalidateViewAt(index);
    }
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateMessageInteractionInfo(messageId, interactionInfo);
    }
  }

  public void updateMessageIsPinned (long messageId, boolean isPinned) {
    int index = adapter.indexOfMessageContainer(messageId);
    if (index != -1 && adapter.getItem(index).setIsPinned(messageId, isPinned)) {
      invalidateViewAt(index);
    }
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      messageThread.updateMessageIsPinned(messageId, isPinned);
    }
  }

  public void updateMessagesDeleted (long chatId, long[] messageIds) {
    controller.removeReply(messageIds);
    controller.onMessagesDeleted(chatId, messageIds);

    int removedCount = 0;
    int removedUnreadCount = 0;
    int selectedCount = controller.getSelectedMessageCount();
    boolean unselectedSomeMessages = false;

    TdApi.Chat chat = tdlib.chatStrict(chatId);
    ThreadInfo messageThread = loader.getMessageThread();
    long lastReadInboxMessageId = messageThread != null ? messageThread.getLastReadInboxMessageId() : chat.lastReadInboxMessageId;

    int i = 0;
    main: while (i < adapter.getMessageCount()) {
      TGMessage item = adapter.getMessage(i);

      for (long messageId : messageIds) {
        switch (item.removeMessage(messageId)) {
          case TGMessage.REMOVE_NOTHING: {
            item.removeReply(chatId, messageId);
            break;
          }
          case TGMessage.REMOVE_COMBINATION: {
            if (controller.unselectMessage(messageId, item)) {
              selectedCount--;
              unselectedSomeMessages = true;
            }
            if (!item.isOutgoing() && messageId > lastReadInboxMessageId) {
              removedUnreadCount++;
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
            if (!item.isOutgoing() && messageId > lastReadInboxMessageId) {
              removedUnreadCount++;
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
    if (messageThread != null) {
      if (areScheduled()) {
        messageThread.updateMessagesDeleted(messageIds, /* removedCount */ 0, /* removedUnreadCount */ 0);
      } else {
        messageThread.updateMessagesDeleted(messageIds, removedCount, removedUnreadCount);
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

    AtomicBoolean found = new AtomicBoolean();
    AtomicInteger addedAfter = new AtomicInteger();

    RunnableData<TdApi.Message> callback = message -> {
      if (Td.isSecret(message.content))
        return;
      boolean matchesFilter = filter == null || Td.matchesFilter(message, filter);
      MediaItem item = matchesFilter ? MediaItem.valueOf(controller.context(), tdlib, message) : null;
      if (item != null) {
        result.add(0, item);
        if (found.get()) {
          addedAfter.incrementAndGet();
        }
        if (message.id == fromMessageId) {
          found.set(true);
        }
      }
    };

    for (TGMessage parsedMessage : items) {
      parsedMessage.iterate(callback, true);
    }

    if (!found.get()) {
      return null;
    }

    MediaStack stack;
    stack = new MediaStack(controller.context(), tdlib);
    stack.set(addedAfter.get(), result);
    stack.setReverseModeHint(false);
    stack.setForceThumbsHint(false);

    return stack;
  }

  // Reading messages

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
    }
  }

  public boolean isFocused () {
    return isFocused;
  }

  private void onBlur () {
    saveScrollPosition();
  }

  public boolean readMessagesDisabled () {
    return controller.isInForceTouchMode() || Settings.instance().dontReadMessages();
  }

  private boolean canRead () {
    return !(inSpecialMode() || readMessagesDisabled());
  }

  private void saveScrollPosition () {
    if (!canRead()) {
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
      boolean isBottomSponsored = adapter.getBottomMessage() != null && adapter.getBottomMessage().isSponsoredMessage() && adapter.getMessageCount() > 1;

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
          if (message.isSponsoredMessage()) {
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
    viewMessages(false);
    saveScrollPosition();
  }

  // Highlight message id

  public static final int HIGHLIGHT_MODE_WITHOUT_HIGHLIGHT = -2;
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
    if (lastViewedMentionMessageId != 0) {
      fromMessageId = lastViewedMentionMessageId;
    } else {
      TGMessage message = adapter.getTopMessage();
      if (message == null) {
        return;
      }
      fromMessageId = message.getBiggestId();
    }
    final long chatId = loader.getChatId();
    final long messageThreadId = loader.getMessageThreadId();
    final AtomicBoolean isRetry = new AtomicBoolean();
    mentionsHandler = new CancellableResultHandler() {
      @Override
      public void processResult (final TdApi.Object object) {
        if (object.getConstructor() == TdApi.FoundChatMessages.CONSTRUCTOR) {
          TdApi.FoundChatMessages messages = (TdApi.FoundChatMessages) object;
          if (messages.totalCount > 0 && messages.messages.length == 0 && isRetry.getAndSet(true)) {
            tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, null, 0, 0, 10, new TdApi.SearchMessagesFilterUnreadMention(), messageThreadId), this);
          } else {
            setMentions(this, messages, isRetry.get() ? 0 : fromMessageId);
          }
        } else {
          setMentions(this, null, fromMessageId);
        }
      }
    };
    tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, null, fromMessageId, -9, 10, new TdApi.SearchMessagesFilterUnreadMention(), messageThreadId), mentionsHandler);
  }

  private void setMentions (final CancellableResultHandler handler, final TdApi.FoundChatMessages messages, final long fromMessageId) {
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
  private long lastViewedReactionMessageId = 0;
  private Runnable messageViewer;

  private void setUnreadReactions (final CancellableResultHandler handler, final TdApi.FoundChatMessages messages, final long fromMessageId) {
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
    if (lastViewedReactionMessageId != 0) {
      fromMessageId = lastViewedReactionMessageId;
    } else {
      TGMessage message = adapter.getTopMessage();
      if (message == null) {
        return;
      }
      fromMessageId = message.getBiggestId();
    }
    final long chatId = loader.getChatId();
    final long messageThreadId = loader.getMessageThreadId();
    final AtomicBoolean isRetry = new AtomicBoolean();
    reactionsHandler = new CancellableResultHandler() {
      @Override
      public void processResult (final TdApi.Object object) {
        if (object.getConstructor() == TdApi.FoundChatMessages.CONSTRUCTOR) {
          TdApi.FoundChatMessages messages = (TdApi.FoundChatMessages) object;
          if (messages.totalCount > 0 && messages.messages.length == 0 && !isRetry.getAndSet(true)) {
            tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, null, 0, 0, 10, new TdApi.SearchMessagesFilterUnreadReaction(), messageThreadId), this);
          } else {
            setUnreadReactions(this, messages, isRetry.get() ? 0 : fromMessageId);
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

  public void searchMoveToMessage (MessageId message) {
    if (searchManager == null || message == null) {
      return;
    }
    searchManager.moveToMessage(message);
  }

  public void onPrepareToSearch () {
    if (searchManager == null) {
      searchManager = new MessagesSearchManager(tdlib, this, searchMiddleware);
    }
    searchManager.onPrepare();
  }

  public void search (long chatId, @Nullable ThreadInfo messageThread, TdApi.MessageSender sender, TdApi.SearchMessagesFilter filter, boolean isSecret, String input, MessageId foundMessageId) {
    if (isEventLog()) {
      applyEventLogFilters(eventLogFilters, input, eventLogUserIds);
    } else {
      searchManager.search(messageThread != null ? messageThread.getChatId() : chatId, messageThread != null ? messageThread.getMessageThreadId() : 0, sender, filter, isSecret, input, foundMessageId);
    }
  }

  public boolean isMessageFound (TdApi.Message message) {
    return (message != null && searchManager != null && controller.inSearchMode() && (searchManager.isMessageFound(message) || controller.inSearchFilteredShowMode()));
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

  private void scrollToHistoryStart () {
    int messageCount = adapter.getMessageCount();
    if (messageCount == 0)
      return;
    boolean isTopMessageHeader = isHeaderMessage(adapter.getTopMessage());
    int scrollIndex = isTopMessageHeader ? messageCount - 2 : messageCount - 1;
    if (scrollIndex < 0)
      return;
    TGMessage scrollMessage = adapter.getMessage(scrollIndex);
    if (scrollMessage == null)
      return;
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread != null) {
      int targetHeight = getTargetHeight();
      if (FeatureToggles.SCROLL_TO_HEADER_MESSAGE_ON_THREAD_FIRST_OPEN && isTopMessageHeader) {
        manager.scrollToPositionWithOffset(messageCount - 1, targetHeight / 2);
        return;
      }
      if (shouldShowThreadHeaderPreview() && isTopMessageHeader) {
        int previewHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
        int sumHeight = previewHeight;
        for (int index = scrollIndex; index >= 0; index--) {
          TGMessage message = adapter.getMessage(index);
          if (message == null)
            break;
          sumHeight += message.getHeight();
          if (targetHeight <= sumHeight)
            break;
        }
        if (targetHeight <= sumHeight) {
          controller.showHidePinnedMessage(true, messageThread.getOldestMessage());
          targetHeight -= previewHeight;
        }
      }
      manager.scrollToPositionWithOffset(scrollIndex, targetHeight - scrollMessage.getHeight());
    } else {
      manager.scrollToPositionWithOffset(scrollIndex, -scrollMessage.getHeight());
    }
  }

  private void scrollToMessage (int index, TGMessage scrollMessage, int highlightMode, boolean smooth, boolean blocking) {
    if (highlightMode == HIGHLIGHT_MODE_START) {
      scrollToPositionWithOffset(index, 0, false);
      return;
    }
    if (highlightMode == HIGHLIGHT_MODE_POSITION_RESTORE) {
      final int accountId = tdlib.id();
      Settings.SavedMessageId messageId = Settings.instance().getScrollMessageId(accountId, loader.getChatId(), loader.getMessageThreadId());
      int offset = messageId != null ? messageId.offsetPixels : 0;
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

  private String buildSearchResultsCounter (int index, int totalCount, boolean knownIndex, boolean knownTotal) {
    if (knownIndex && knownTotal) {
      return Lang.getXofY(index + 1, totalCount);
    } else if (knownIndex) {
      return Lang.getXofApproximateY(index + 1, totalCount);
    } else if (knownTotal) {
      return Lang.plural(R.string.SearchExactlyResults, totalCount);
    } else {
      return Lang.plural(R.string.SearchApproximateResults, totalCount);
    }
  }

  @Override
  public void showSearchResult (int index, int totalCount, boolean knownIndex, boolean knownTotalCount, MessageId messageId) {
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
        controller.onChatSearchFinished(buildSearchResultsCounter(index, totalCount, knownIndex, knownTotalCount), index, totalCount);
        if (messageId != null) {
          highlightMessage(messageId, HIGHLIGHT_MODE_NORMAL, null, true);
        }
        break;
      }
    }
  }

  @Override
  public void onSearchUpdateTotalCount (int index, int totalCount, boolean knownIndex, boolean knownTotalCount) {
    controller.onChatSearchFinished(buildSearchResultsCounter(index, totalCount, knownIndex, knownTotalCount), index, totalCount);
  }

  @Override
  public void onAwaitNext (boolean next) {
    controller.onChatSearchAwaitNext(next);
  }

  @Override
  public void onTryToLoadPrevious () {
    // UI.showToast("Animate try to load previous", Toast.LENGTH_SHORT);
  }

  @Override
  public void onTryToLoadNext () {
    // UI.showToast("Animate try to load next", Toast.LENGTH_SHORT);
  }

  public boolean canSearchNext () {
    return searchManager.canMoveToNext();
  }

  public boolean canSearchPrev () {
    return searchManager.canMoveToPrev();
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
      return threadInfo.hasUnreadMessages(chat) && threadInfo.getLastReadInboxMessageId() != 0;
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
    if (threadInfo != null) {
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
          return new MessageId(threadInfo.getChatId(), threadInfo.getLastReadInboxMessageId() == 0 ? MessageId.MIN_VALID_ID : threadInfo.getLastReadInboxMessageId());
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
  private @Nullable ThreadInfo lastSubscribedMessageThread;

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
    ThreadInfo messageThread = loader.getMessageThread();
    if (lastSubscribedMessageThread != messageThread) {
      if (lastSubscribedMessageThread != null) {
        lastSubscribedMessageThread.removeListener(this);
      }
      lastSubscribedMessageThread = messageThread;
      if (messageThread != null) {
        messageThread.addListener(this);
      }
    }
  }

  private void unsubscribeFromUpdates () {
    if (lastSubscribedChatId != 0) {
      controller.unsubscribeFromUpdates(lastSubscribedChatId);
      tdlib.listeners().unsubscribeFromMessageUpdates(lastSubscribedChatId, this);
      lastSubscribedChatId = 0;
    }
    if (lastSubscribedMessageThread != null) {
      lastSubscribedMessageThread.removeListener(this);
      lastSubscribedMessageThread = null;
    }
  }

  // Updates

  private boolean areScheduled () {
    return loader.getSpecialMode() == MessagesLoader.SPECIAL_MODE_SCHEDULED;
  }

  @Override
  public void onNewMessage (final TdApi.Message message) {
    final ThreadInfo messageThread = loader.getMessageThread();
    if (TD.isScheduled(message) == areScheduled()) {
      if (indexOfSentMessage(message.chatId, message.id) != -1)
        return;
      final TdApi.Chat chat = tdlib.chatStrict(message.chatId);
      final TGMessage parsedMessage = TGMessage.valueOf(this, message, chat, messageThread, chatAdmins);
      showMessage(chat.id, parsedMessage);
    } else if (isFocused() && TD.isScheduled(message) && messageThread == null) {
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
    final ThreadInfo messageThread = loader.getMessageThread();
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
      cur = TGMessage.valueOf(this, message, chat, messageThread, chatAdmins);
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
  public void onMessageSendFailed (final TdApi.Message message, final long oldMessageId, TdApi.Error error) {
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
        updateMessageContent(chatId, messageId, newContent);
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

  @Override
  public void onMessageThreadReadOutbox (long chatId, long messageThreadId, long lastReadOutboxMessageId) {
    if (chatId == loader.getChatId() && messageThreadId == loader.getMessageThreadId()) {
      TdApi.Chat chat = tdlib.chat(chatId);
      long lastGlobalReadOutboxMessageId = chat != null ? chat.lastReadOutboxMessageId : 0;
      if (lastReadOutboxMessageId > lastGlobalReadOutboxMessageId) {
        updateChatReadOutbox(lastReadOutboxMessageId);
      }
    }
  }

  private void checkMessageThreadReplyCounter () {
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread == null || inSpecialMode() || loader.canLoadTop() || loader.canLoadBottom())
      return;
    int messageCount = getActiveMessageCount();
    if (messageCount > 10000)
      return;
    int replyCount = 0;
    for (int index = 0; index < messageCount; index++) {
      replyCount += adapter.getItem(index).getMessageCount();
    }
    messageThread.updateReplyCount(replyCount);
  }

  private void checkMessageThreadUnreadCounter () {
    if (manager != null) {
      checkMessageThreadUnreadCounter(manager.findFirstVisibleItemPosition());
    }
  }

  private void checkMessageThreadUnreadCounter (int firstVisibleItemPosition) {
    ThreadInfo messageThread = loader.getMessageThread();
    if (messageThread == null || inSpecialMode() || !isFocused || firstVisibleItemPosition == -1)
      return;
    if (loader.canLoadBottom()) {
      if (messageThread.getUnreadMessageCount() == ThreadInfo.UNKNOWN_UNREAD_MESSAGE_COUNT) {
        View view = manager.findViewByPosition(firstVisibleItemPosition);
        if (view instanceof MessageProvider) {
          TGMessage message = ((MessageProvider) view).getMessage();
          messageThread.updateReadInbox(message.getBiggestId());
        }
        return;
      }
      long lastReadInboxMessageId = messageThread.getLastReadInboxMessageId();
      long maxReadInboxMessageId = lastReadInboxMessageId;
      int readMessageCount = 0;
      for (int index = firstVisibleItemPosition; index < manager.getItemCount(); index++) {
        View view = manager.findViewByPosition(index);
        if (!(view instanceof MessageProvider))
          continue;
        TGMessage message = ((MessageProvider) view).getMessage();
        if (message.isOutgoing())
          continue;
        if (message.isThreadHeader())
          break;
        long inboxMessageId = message.getBiggestId();
        if (inboxMessageId <= lastReadInboxMessageId)
          break;
        maxReadInboxMessageId = Math.max(maxReadInboxMessageId, inboxMessageId);
        readMessageCount += 1 + message.getMessageCountBetween(lastReadInboxMessageId, inboxMessageId);
      }
      if (maxReadInboxMessageId > lastReadInboxMessageId) {
        int unreadMessageCount;
        if (messageThread.getUnreadMessageCount() > readMessageCount) {
          unreadMessageCount = messageThread.getUnreadMessageCount() - readMessageCount;
        } else {
          unreadMessageCount = ThreadInfo.UNKNOWN_UNREAD_MESSAGE_COUNT;
        }
        messageThread.updateReadInbox(maxReadInboxMessageId, unreadMessageCount);
      }
    } else {
      if (firstVisibleItemPosition == 0) {
        messageThread.markAsRead();
        return;
      }
      long lastReadInboxMessageId = messageThread.getLastReadInboxMessageId();
      View firstVisibleItemView = manager.findViewByPosition(firstVisibleItemPosition);
      if (firstVisibleItemView instanceof MessageProvider) {
        TGMessage firstVisibleMessage = ((MessageProvider) firstVisibleItemView).getMessage();
        lastReadInboxMessageId = Math.max(lastReadInboxMessageId, firstVisibleMessage.getBiggestId());
      }
      int unreadMessageCount = 0;
      int messageCount = getActiveMessageCount();
      for (int index = 0; index < messageCount; index++) {
        TGMessage message = adapter.getItem(index);
        if (message.isOutgoing())
          continue;
        if (message.isThreadHeader())
          break;
        long inboxMessageId = message.getBiggestId();
        if (inboxMessageId <= lastReadInboxMessageId)
          break;
        unreadMessageCount += 1 + message.getMessageCountBetween(lastReadInboxMessageId, inboxMessageId);
      }
      messageThread.updateReadInbox(lastReadInboxMessageId, unreadMessageCount);
    }
  }

  // Colors

  public final int getOverlayColor (@ColorId int plainModeColorId, @ColorId int bubbleColorId, @ColorId int bubbleNoWallpaperColorId, @PropertyId int overridePropertyId) {
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

  public final int getColor (@ColorId int plainModeColorId, @ColorId int bubbleColorId, @ColorId int bubbleNoWallpaperColorId, @PropertyId int overridePropertyId) {
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
