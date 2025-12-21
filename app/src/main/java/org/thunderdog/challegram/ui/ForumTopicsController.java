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
 * File created for forum topics support
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.ChatHeaderView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.support.ViewSupport;
import tgx.td.ChatId;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.ListInfoView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.util.TopicIconModifier;
import android.util.SparseIntArray;

import tgx.td.MessageId;

public class ForumTopicsController extends TelegramViewController<ForumTopicsController.Arguments> implements
  Menu, MoreDelegate, View.OnClickListener, View.OnLongClickListener,
  ChatListener, TdlibCache.SupergroupDataChangeListener, ChatHeaderView.Callback {

  public static class Arguments {
    public final long chatId;
    public final TdApi.Chat chat;

    public Arguments (long chatId, @Nullable TdApi.Chat chat) {
      this.chatId = chatId;
      this.chat = chat;
    }

    public Arguments (TdApi.Chat chat) {
      this.chatId = chat.id;
      this.chat = chat;
    }
  }

  public ForumTopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long chatId;
  private TdApi.Chat chat;

  private FrameLayoutFix contentView;
  private CustomRecyclerView recyclerView;
  private ForumTopicsAdapter adapter;
  private ListInfoView emptyView;
  private CircleButton createTopicButton;
  private ChatHeaderView headerCell;

  private List<TdApi.ForumTopic> topics;
  private List<TdApi.ForumTopic> allTopics; // For restoring after search
  private boolean isLoading;
  private boolean canLoadMore;
  private String currentSearchQuery;
  private boolean searchInMessages = true; // Toggle between topic name search and message search (default: messages)
  private List<TopicMessageSearchResult> messageSearchResults = new ArrayList<>();
  private List<TopicMessageSearchResult> unfilteredMessageResults = new ArrayList<>(); // Store original unfiltered results
  private boolean isSearchingMessages = false;
  private long lastSearchMessageId = 0; // For message search pagination
  private boolean canLoadMoreMessages = false;
  private java.util.Set<Long> selectedFilterTopicIds = new java.util.HashSet<>(); // Empty = all topics
  private CircleButton filterTopicButton;
  private boolean navigatedFromSearch = false; // Track if we navigated to a topic from search results

  // Multi-page search loading
  private static final int PAGES_PER_LOAD = 10;  // Load 10 pages at a time for better topic filtering
  private static final int RESULTS_PER_PAGE = 100;
  private static final int MAX_AUTO_RETRY = 3;  // Max auto-retry when filtered results empty
  private int pendingPageLoads = 0;  // Track remaining pages to load
  private List<TdApi.Message> pendingMessages = new ArrayList<>();  // Collect results from all pages
  private String pendingSearchQuery = null;  // Query for current batch
  private int filterAutoRetryCount = 0;  // Track auto-retry attempts
  private java.util.Set<Long> pendingFilterTopicIds = null;  // Filter to apply after loading more

  @Override
  protected boolean allowLeavingSearchMode () {
    // Prevent leaving search mode if we navigated to a search result and are returning
    if (navigatedFromSearch && searchInMessages && !messageSearchResults.isEmpty()) {
      return false;
    }
    return true;
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.chatId = args.chatId;
    this.chat = args.chat;
  }

  @Override
  public int getId () {
    return R.id.controller_forumTopics;
  }

  @Override
  public CharSequence getName () {
    if (chat != null) {
      return chat.title;
    }
    return Lang.getString(R.string.Topics);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addMoreButton(menu, this);
      header.addSearchButton(menu, this);
    } else if (id == R.id.menu_clear) {
      // Add toggle button before clear button (left of clear)
      // baseline_chat_bubble_24 for messages mode, baseline_forum_24 for topics mode
      header.addButton(menu, R.id.btn_searchModeToggle,
        searchInMessages ? R.drawable.baseline_chat_bubble_24 : R.drawable.baseline_forum_24,
        getHeaderIconColorId(), this, Screen.dp(49f));
      header.addClearButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      openSearchMode();
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else if (id == R.id.btn_searchModeToggle) {
      toggleSearchMode();
    } else if (id == R.id.menu_btn_more) {
      IntList ids = new IntList(1);
      IntList icons = new IntList(1);
      StringList strings = new StringList(1);

      ids.append(R.id.btn_viewAsChat);
      icons.append(R.drawable.baseline_chat_bubble_24);
      strings.append(R.string.ViewAsChat);

      showMore(ids.get(), strings.get(), icons.get(), 0);
    }
  }

  @Override
  protected void onLeaveSearchMode () {
    navigatedFromSearch = false; // Reset navigation flag when leaving search
    currentSearchQuery = null;
    searchInMessages = true; // Reset to default (messages mode)
    messageSearchResults.clear();
    unfilteredMessageResults.clear();
    selectedFilterTopicIds.clear();
    updateFilterFabVisibility();
    if (allTopics != null) {
      topics.clear();
      topics.addAll(allTopics);
      adapter.setTopics(topics, null);
      updateEmptyView();
    }
  }

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    String cleanQuery = query != null ? query.trim() : "";
    if (StringUtils.equalsOrBothEmpty(cleanQuery, currentSearchQuery)) {
      return;
    }
    currentSearchQuery = cleanQuery;
    if (searchInMessages) {
      searchMessages(cleanQuery);
    } else {
      searchTopics(cleanQuery);
    }
  }

  private void toggleSearchMode () {
    searchInMessages = !searchInMessages;
    // Update toggle button icon
    if (headerView != null) {
      headerView.updateButton(R.id.menu_clear, R.id.btn_searchModeToggle, View.VISIBLE,
        searchInMessages ? R.drawable.baseline_chat_bubble_24 : R.drawable.baseline_forum_24);
    }

    // Reset filter when switching modes
    selectedFilterTopicIds.clear();
    unfilteredMessageResults.clear();

    // Re-search with the same query
    if (!StringUtils.isEmpty(currentSearchQuery)) {
      if (searchInMessages) {
        searchMessages(currentSearchQuery);
      } else {
        searchTopics(currentSearchQuery);
      }
    } else {
      // No query - restore original list if in topic mode, or show empty in message mode
      if (!searchInMessages && allTopics != null) {
        topics.clear();
        topics.addAll(allTopics);
        adapter.setTopics(topics, null);
        updateEmptyView();
      } else if (searchInMessages) {
        messageSearchResults.clear();
        adapter.setMessageSearchResults(messageSearchResults, null);
        updateEmptyView();
      }
    }

    // Update filter FAB visibility based on new mode
    updateFilterFabVisibility();
  }

  private void searchTopics (String query) {
    if (StringUtils.isEmpty(query)) {
      // Empty query - restore all topics
      if (allTopics != null) {
        topics.clear();
        topics.addAll(allTopics);
        adapter.setTopics(topics, null);
        updateEmptyView();
      }
      return;
    }

    // Save current topics before search if not already saved
    if (allTopics == null || allTopics.isEmpty()) {
      allTopics = new ArrayList<>(topics);
    }

    // Client-side filtering by topic name (case-insensitive)
    String lowerQuery = query.toLowerCase();
    List<TdApi.ForumTopic> filteredTopics = new ArrayList<>();
    for (TdApi.ForumTopic topic : allTopics) {
      if (topic.info.name.toLowerCase().contains(lowerQuery)) {
        filteredTopics.add(topic);
      }
    }

    topics.clear();
    topics.addAll(filteredTopics);
    adapter.setTopics(topics, query);
    updateEmptyView();
  }

  private void searchMessages (String query) {
    if (StringUtils.isEmpty(query)) {
      messageSearchResults.clear();
      lastSearchMessageId = 0;
      canLoadMoreMessages = false;
      adapter.setMessageSearchResults(messageSearchResults, null);
      updateEmptyView();
      return;
    }

    if (isSearchingMessages) {
      return; // Already searching
    }
    isSearchingMessages = true;

    // Reset pagination for new search
    lastSearchMessageId = 0;
    canLoadMoreMessages = false;
    messageSearchResults.clear();

    // Reset multi-page loading state
    pendingMessages.clear();
    pendingPageLoads = PAGES_PER_LOAD;
    pendingSearchQuery = query;

    // Show loading state
    emptyView.setVisibility(View.VISIBLE);
    emptyView.showInfo(Lang.getString(R.string.LoadingTopics));
    setClearButtonSearchInProgress(true);

    // Start loading first page (will chain-load remaining pages)
    loadMessagePage(query, 0, false);
  }

  private void loadMessagePage (String query, long fromMessageId, boolean isAppending) {
    tdlib.client().send(new TdApi.SearchChatMessages(
      chatId,
      null, // topicId - null to search all topics
      query,
      null, // senderId
      fromMessageId, // fromMessageId for pagination
      0, // offset
      RESULTS_PER_PAGE, // limit
      null // filter
    ), result -> {
      if (result.getConstructor() == TdApi.FoundChatMessages.CONSTRUCTOR) {
        TdApi.FoundChatMessages foundMessages = (TdApi.FoundChatMessages) result;

        // Collect messages from this page
        synchronized (pendingMessages) {
          java.util.Collections.addAll(pendingMessages, foundMessages.messages);
        }

        pendingPageLoads--;

        // Store pagination cursor for later "load more"
        lastSearchMessageId = foundMessages.nextFromMessageId;

        // Continue loading more pages if available and within limit
        if (foundMessages.nextFromMessageId != 0 && pendingPageLoads > 0) {
          loadMessagePage(query, foundMessages.nextFromMessageId, isAppending);
        } else {
          // All pages loaded (or no more results)
          canLoadMoreMessages = foundMessages.nextFromMessageId != 0;
          finalizeBatchSearch(query, isAppending);
        }
      } else {
        // Error - finalize with what we have
        canLoadMoreMessages = false;
        finalizeBatchSearch(query, isAppending);
      }
    });
  }

  private void finalizeBatchSearch (String query, boolean isAppending) {
    // Convert collected messages to array and process
    TdApi.Message[] messages;
    synchronized (pendingMessages) {
      messages = pendingMessages.toArray(new TdApi.Message[0]);
      pendingMessages.clear();
    }

    // Check if there's a pending filter to apply after processing
    final java.util.Set<Long> filterToApply = pendingFilterTopicIds;

    processMessageSearchResults(messages, query, isAppending);

    // Re-apply pending filter if any (for auto-retry scenario)
    if (filterToApply != null && !filterToApply.isEmpty()) {
      UI.post(() -> applyTopicFilter(filterToApply, true));
    }
  }

  private void loadMoreMessages () {
    if (isSearchingMessages || !canLoadMoreMessages || StringUtils.isEmpty(currentSearchQuery)) {
      return;
    }
    isSearchingMessages = true;

    // Reset multi-page loading state for batch load
    pendingMessages.clear();
    pendingPageLoads = PAGES_PER_LOAD;
    pendingSearchQuery = currentSearchQuery;

    // Start loading from last position (will chain-load remaining pages)
    loadMessagePage(currentSearchQuery, lastSearchMessageId, true);
  }

  private void processMessageSearchResults (TdApi.Message[] messages, String query, boolean append) {
    // Flat list: show ALL messages, not grouped by topic
    if (messages.length == 0) {
      UI.post(() -> {
        isSearchingMessages = false;
        setClearButtonSearchInProgress(false);
        if (!append) {
          messageSearchResults.clear();
          adapter.setMessageSearchResults(messageSearchResults, query);
        }
        updateEmptyViewForMessageSearch();
      });
      return;
    }

    // Collect unique topic IDs to fetch
    Map<Long, TdApi.ForumTopic> topicCache = new HashMap<>();
    java.util.Set<Long> topicIdsToFetch = new java.util.HashSet<>();

    // First pass: check cache and collect IDs to fetch
    for (TdApi.Message message : messages) {
      long topicId = 0;
      if (message.topicId != null && message.topicId instanceof TdApi.MessageTopicForum) {
        topicId = ((TdApi.MessageTopicForum) message.topicId).forumTopicId;
      }
      if (topicId != 0 && !topicCache.containsKey(topicId)) {
        // Check cached allTopics first
        TdApi.ForumTopic cachedTopic = null;
        if (allTopics != null) {
          for (TdApi.ForumTopic t : allTopics) {
            if (t.info.forumTopicId == topicId) {
              cachedTopic = t;
              break;
            }
          }
        }
        if (cachedTopic != null) {
          topicCache.put(topicId, cachedTopic);
        } else {
          topicIdsToFetch.add(topicId);
        }
      }
    }

    // If all topics are cached, build results directly
    if (topicIdsToFetch.isEmpty()) {
      buildFlatMessageResults(messages, topicCache, query, append);
      return;
    }

    // Fetch missing topics
    int[] pending = {topicIdsToFetch.size()};
    boolean finalAppend = append;
    for (Long topicId : topicIdsToFetch) {
      tdlib.client().send(new TdApi.GetForumTopic(chatId, topicId.intValue()), topicResult -> {
        if (topicResult.getConstructor() == TdApi.ForumTopic.CONSTRUCTOR) {
          TdApi.ForumTopic topic = (TdApi.ForumTopic) topicResult;
          synchronized (topicCache) {
            topicCache.put(topicId, topic);
          }
        }
        pending[0]--;
        if (pending[0] == 0) {
          buildFlatMessageResults(messages, topicCache, query, finalAppend);
        }
      });
    }
  }

  private void buildFlatMessageResults (TdApi.Message[] messages, Map<Long, TdApi.ForumTopic> topicCache, String query, boolean append) {
    List<TopicMessageSearchResult> results = new ArrayList<>();
    for (TdApi.Message message : messages) {
      long topicId = 0;
      if (message.topicId != null && message.topicId instanceof TdApi.MessageTopicForum) {
        topicId = ((TdApi.MessageTopicForum) message.topicId).forumTopicId;
      }
      TdApi.ForumTopic topic = topicCache.get(topicId);
      if (topic != null) {
        results.add(new TopicMessageSearchResult(topic, message, query));
      }
    }
    finalizeMessageSearchResults(results, query, append);
  }

  private void finalizeMessageSearchResults (List<TopicMessageSearchResult> results, String query, boolean append) {
    UI.post(() -> {
      isSearchingMessages = false;
      setClearButtonSearchInProgress(false);
      if (!append) {
        // New search - store in unfiltered list and reset filter
        unfilteredMessageResults.clear();
        unfilteredMessageResults.addAll(results);
        selectedFilterTopicIds.clear();
        messageSearchResults.clear();
        messageSearchResults.addAll(results);
      } else {
        // Pagination - add to unfiltered list
        unfilteredMessageResults.addAll(results);
        // If filter is active, only add results that match
        if (!selectedFilterTopicIds.isEmpty()) {
          for (TopicMessageSearchResult result : results) {
            long topicId = result.topic.info.forumTopicId;
            if (selectedFilterTopicIds.contains(topicId)) {
              messageSearchResults.add(result);
            }
          }
        } else {
          messageSearchResults.addAll(results);
        }
      }

      int startPosition = append ? messageSearchResults.size() - results.size() : 0;
      if (append && !results.isEmpty()) {
        // Notify only about new items for better performance
        int insertedCount = (!selectedFilterTopicIds.isEmpty())
          ? (int) results.stream().filter(r -> selectedFilterTopicIds.contains((long) r.topic.info.forumTopicId)).count()
          : results.size();
        if (insertedCount > 0) {
          adapter.notifyItemRangeInserted(messageSearchResults.size() - insertedCount, insertedCount);
        }
      } else {
        adapter.setMessageSearchResults(messageSearchResults, query);
      }
      updateEmptyViewForMessageSearch();

      // Show filter FAB when there are results
      updateFilterFabVisibility();
    });
  }

  private void updateEmptyViewForMessageSearch () {
    if (messageSearchResults.isEmpty()) {
      emptyView.setVisibility(View.VISIBLE);
      emptyView.showInfo(Lang.getString(R.string.NoMessagesFound));
    } else {
      emptyView.setVisibility(View.GONE);
    }
  }

  private void updateFilterFabVisibility () {
    if (filterTopicButton == null) return;

    // Show filter FAB only when in message search mode with results
    boolean shouldShow = searchInMessages && !unfilteredMessageResults.isEmpty() && inSearchMode();
    filterTopicButton.setVisibility(shouldShow ? View.VISIBLE : View.GONE);

    // Reset FAB color if filter was cleared
    if (!shouldShow || selectedFilterTopicIds.isEmpty()) {
      filterTopicButton.init(R.drawable.baseline_tune_24, 56f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
    }
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (inSearchMode()) {
      if (commit) {
        closeSearchMode(null);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(contentView, ColorId.filling);

    // Create header view for clickable chat header
    headerCell = new ChatHeaderView(context, tdlib, this);
    headerCell.setCallback(this);
    if (chat != null) {
      headerCell.setChat(tdlib, chat, null, null);
    }

    topics = new ArrayList<>();
    adapter = new ForumTopicsAdapter(this);

    recyclerView = new CustomRecyclerView(context);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
    recyclerView.setAdapter(adapter);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager == null) return;

        int lastVisible = manager.findLastVisibleItemPosition();

        // Handle message search pagination
        if (searchInMessages && canLoadMoreMessages && !isSearchingMessages) {
          if (lastVisible >= messageSearchResults.size() - 5) {
            loadMoreMessages();
          }
        }
        // Handle topic list pagination
        else if (!searchInMessages && canLoadMore && !isLoading) {
          if (lastVisible >= topics.size() - 5) {
            loadMoreTopics();
          }
        }
      }
    });

    emptyView = new ListInfoView(context);
    emptyView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    emptyView.showInfo(Lang.getString(R.string.LoadingTopics));

    contentView.addView(emptyView);
    contentView.addView(recyclerView);

    // Create topic FAB button
    int padding = Screen.dp(4f);
    FrameLayoutFix.LayoutParams fabParams = FrameLayoutFix.newParams(
      Screen.dp(56f) + padding * 2,
      Screen.dp(56f) + padding * 2,
      Gravity.RIGHT | Gravity.BOTTOM
    );
    fabParams.rightMargin = fabParams.bottomMargin = Screen.dp(16f) - padding;

    createTopicButton = new CircleButton(context);
    createTopicButton.setId(R.id.btn_createTopic);
    createTopicButton.setOnClickListener(this);
    createTopicButton.init(R.drawable.baseline_add_24, 56f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
    createTopicButton.setLayoutParams(fabParams);
    addThemeInvalidateListener(createTopicButton);
    contentView.addView(createTopicButton);
    // Hide FAB if user can't create topics
    createTopicButton.setVisibility(canCreateTopics() ? View.VISIBLE : View.GONE);

    // Filter topic FAB button (positioned above create button)
    FrameLayoutFix.LayoutParams filterFabParams = FrameLayoutFix.newParams(
      Screen.dp(56f) + padding * 2,
      Screen.dp(56f) + padding * 2,
      Gravity.RIGHT | Gravity.BOTTOM
    );
    filterFabParams.rightMargin = Screen.dp(16f) - padding;
    filterFabParams.bottomMargin = Screen.dp(80f) - padding; // Above create button

    filterTopicButton = new CircleButton(context);
    filterTopicButton.setId(R.id.btn_filterTopic);
    filterTopicButton.setOnClickListener(this);
    filterTopicButton.init(R.drawable.baseline_tune_24, 56f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
    filterTopicButton.setLayoutParams(filterFabParams);
    addThemeInvalidateListener(filterTopicButton);
    contentView.addView(filterTopicButton);
    // Initially hidden - show only during message search mode
    filterTopicButton.setVisibility(View.GONE);

    loadTopics();

    return contentView;
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_viewAsChat) {
      // Set viewAsTopics to false and open as unified chat
      tdlib.client().send(new TdApi.ToggleChatViewAsTopics(chatId, false), result -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          tdlib.ui().post(() -> {
            // Navigate back and open chat in unified mode
            navigateBack();
            MessagesController c = new MessagesController(context, tdlib);
            c.setArguments(new MessagesController.Arguments(tdlib, null, chat, null, null, null));
            navigateTo(c);
          });
        }
      });
    }
  }

  @Override
  public void onChatHeaderClick () {
    if (chat != null) {
      ProfileController controller = new ProfileController(context, tdlib);
      controller.setShareCustomHeaderView(true);
      controller.setArguments(new ProfileController.Args(chat, null, false));
      navigateTo(controller);
    }
  }

  private void loadTopics () {
    if (isLoading) return;
    isLoading = true;

    tdlib.listeners().subscribeToChatUpdates(chatId, this);

    tdlib.client().send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), result -> {
      if (result.getConstructor() == TdApi.ForumTopics.CONSTRUCTOR) {
        TdApi.ForumTopics forumTopics = (TdApi.ForumTopics) result;
        UI.post(() -> {
          topics.clear();
          for (TdApi.ForumTopic topic : forumTopics.topics) {
            topics.add(topic);
          }
          // Save all topics for search restore
          allTopics = new ArrayList<>(topics);
          canLoadMore = forumTopics.topics.length > 0 &&
                        forumTopics.nextOffsetMessageId != 0;
          adapter.setTopics(topics, null);
          isLoading = false;
          updateEmptyView();
        });
      } else {
        UI.post(() -> {
          isLoading = false;
          updateEmptyView();
        });
      }
    });
  }

  private void loadMoreTopics () {
    if (isLoading || !canLoadMore || topics.isEmpty()) return;
    isLoading = true;

    TdApi.ForumTopic lastTopic = topics.get(topics.size() - 1);
    long lastMessageId = lastTopic.lastMessage != null ? lastTopic.lastMessage.id : 0;
    int lastDate = lastTopic.lastMessage != null ? lastTopic.lastMessage.date : 0;

    tdlib.client().send(new TdApi.GetForumTopics(chatId, "", lastDate, lastMessageId, lastTopic.info.forumTopicId, 100), result -> {
      if (result.getConstructor() == TdApi.ForumTopics.CONSTRUCTOR) {
        TdApi.ForumTopics forumTopics = (TdApi.ForumTopics) result;
        UI.post(() -> {
          for (TdApi.ForumTopic topic : forumTopics.topics) {
            topics.add(topic);
          }
          canLoadMore = forumTopics.topics.length > 0 &&
                        forumTopics.nextOffsetMessageId != 0;
          adapter.setTopics(topics, null);
          isLoading = false;
        });
      } else {
        UI.post(() -> {
          isLoading = false;
        });
      }
    });
  }

  private void updateEmptyView () {
    if (topics.isEmpty()) {
      emptyView.setVisibility(View.VISIBLE);
      emptyView.showInfo(Lang.getString(R.string.NoTopics));
    } else {
      emptyView.setVisibility(View.GONE);
    }
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_createTopic) {
      showCreateTopicDialog();
      return;
    }
    if (id == R.id.btn_filterTopic) {
      showTopicFilterOptions();
      return;
    }
    Object tag = v.getTag();
    if (tag instanceof TopicMessageSearchResult) {
      TopicMessageSearchResult result = (TopicMessageSearchResult) tag;
      openTopicAtMessage(result.topic, result.foundMessage);
    } else if (tag instanceof TdApi.ForumTopic) {
      TdApi.ForumTopic topic = (TdApi.ForumTopic) tag;
      openTopic(topic);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    Object tag = v.getTag();
    if (tag instanceof TdApi.ForumTopic) {
      TdApi.ForumTopic topic = (TdApi.ForumTopic) tag;
      showTopicOptions(topic);
      return true;
    }
    return false;
  }

  private void openTopic (TdApi.ForumTopic topic) {
    MessagesController controller = new MessagesController(context, tdlib);

    // Calculate highlight position based on topic's last read message
    MessageId highlightMessageId = null;
    int highlightMode = MessagesManager.HIGHLIGHT_MODE_NONE;

    if (topic.unreadCount > 0 && topic.lastReadInboxMessageId != 0) {
      // There are unread messages - scroll to first unread
      highlightMessageId = new MessageId(chatId, topic.lastReadInboxMessageId);
      highlightMode = MessagesManager.HIGHLIGHT_MODE_UNREAD;
    } else if (topic.lastReadInboxMessageId == 0 && topic.unreadCount > 0) {
      // No messages have been read yet - scroll to beginning
      highlightMessageId = new MessageId(chatId, MessageId.MIN_VALID_ID);
      highlightMode = MessagesManager.HIGHLIGHT_MODE_UNREAD;
    }
    // If all messages are read (unreadCount == 0), highlightMessageId stays null
    // which will open at the bottom (most recent messages)

    MessagesController.Arguments args = new MessagesController.Arguments(
      null, // chatList
      chat,
      null, // threadInfo - will use topicId instead
      new TdApi.MessageTopicForum(topic.info.forumTopicId),
      highlightMessageId,
      highlightMode,
      null // filter
    );
    args.setForumTopic(topic);
    controller.setArguments(args);
    navigateTo(controller);
  }

  private void openTopicAtMessage (TdApi.ForumTopic topic, TdApi.Message message) {
    MessagesController controller = new MessagesController(context, tdlib);

    // Navigate to the specific found message with highlight
    MessageId highlightMessageId = new MessageId(chatId, message.id);
    int highlightMode = MessagesManager.HIGHLIGHT_MODE_NORMAL;

    MessagesController.Arguments args = new MessagesController.Arguments(
      null, // chatList
      chat,
      null, // threadInfo - will use topicId instead
      new TdApi.MessageTopicForum(topic.info.forumTopicId),
      highlightMessageId,
      highlightMode,
      null // filter
    );
    args.setForumTopic(topic);
    controller.setArguments(args);
    navigatedFromSearch = true; // Mark that we're navigating from search results
    navigateTo(controller);
  }

  private void showTopicOptions (TdApi.ForumTopic topic) {
    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    IntList colors = new IntList(5);
    ArrayList<String> strings = new ArrayList<>();

    boolean canManage = canManageTopics();

    // Admin-only: Pin/Unpin
    if (canManage) {
      if (topic.isPinned) {
        ids.append(R.id.btn_unpinTopic);
        icons.append(R.drawable.deproko_baseline_pin_undo_24);
        colors.append(OptionColor.NORMAL);
        strings.add(Lang.getString(R.string.UnpinTopic));
      } else {
        ids.append(R.id.btn_pinTopic);
        icons.append(R.drawable.deproko_baseline_pin_24);
        colors.append(OptionColor.NORMAL);
        strings.add(Lang.getString(R.string.PinTopic));
      }

      // Admin-only: Close/Reopen
      if (topic.info.isClosed) {
        ids.append(R.id.btn_reopenTopic);
        icons.append(R.drawable.baseline_lock_24);
        colors.append(OptionColor.NORMAL);
        strings.add(Lang.getString(R.string.ReopenTopic));
      } else {
        ids.append(R.id.btn_closeTopic);
        icons.append(R.drawable.baseline_lock_24);
        colors.append(OptionColor.NORMAL);
        strings.add(Lang.getString(R.string.CloseTopic));
      }
    }

    // Notifications (always available for members)
    boolean isMuted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0;
    ids.append(R.id.btn_notifications);
    icons.append(isMuted ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
    colors.append(OptionColor.NORMAL);
    strings.add(Lang.getString(isMuted ? R.string.Unmute : R.string.Mute));

    // Admin-only: Edit
    if (canManage) {
      ids.append(R.id.btn_editTopic);
      icons.append(R.drawable.baseline_edit_24);
      colors.append(OptionColor.NORMAL);
      strings.add(Lang.getString(R.string.EditTopic));

      // Admin-only: Change Icon (not for General topic)
      if (!topic.info.isGeneral) {
        ids.append(R.id.btn_editTopicIcon);
        icons.append(R.drawable.baseline_palette_24);
        colors.append(OptionColor.NORMAL);
        strings.add(Lang.getString(R.string.ChangeTopicIcon));
      }

      // Admin-only: Delete
      if (!topic.info.isGeneral) {
        ids.append(R.id.btn_deleteTopic);
        icons.append(R.drawable.baseline_delete_24);
        colors.append(OptionColor.RED);
        strings.add(Lang.getString(R.string.DeleteTopic));
      }
    }

    showOptions(topic.info.name, ids.get(), strings.toArray(new String[0]), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_pinTopic) {
        toggleTopicPinned(topic, true);
      } else if (id == R.id.btn_unpinTopic) {
        toggleTopicPinned(topic, false);
      } else if (id == R.id.btn_closeTopic) {
        toggleTopicClosed(topic, true);
      } else if (id == R.id.btn_reopenTopic) {
        toggleTopicClosed(topic, false);
      } else if (id == R.id.btn_notifications) {
        showTopicMuteOptions(topic);
      } else if (id == R.id.btn_editTopic) {
        editTopic(topic);
      } else if (id == R.id.btn_editTopicIcon) {
        showTopicIconPicker(topic);
      } else if (id == R.id.btn_deleteTopic) {
        deleteTopic(topic);
      }
      return true;
    });
  }

  private void toggleTopicPinned (TdApi.ForumTopic topic, boolean pinned) {
    tdlib.client().send(new TdApi.ToggleForumTopicIsPinned(topic.info.chatId, topic.info.forumTopicId, pinned), result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.post(() -> UI.showError(result));
      }
    });
  }

  private void toggleTopicClosed (TdApi.ForumTopic topic, boolean closed) {
    tdlib.client().send(new TdApi.ToggleForumTopicIsClosed(topic.info.chatId, topic.info.forumTopicId, closed), result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.post(() -> UI.showError(result));
      }
    });
  }

  private void editTopic (TdApi.ForumTopic topic) {
    openInputAlert(
      Lang.getString(R.string.EditTopic),
      Lang.getString(R.string.TopicNameHint),
      R.string.Done,
      R.string.Cancel,
      topic.info.name,
      (inputView, result) -> {
        String newName = result.trim();
        if (newName.isEmpty()) {
          inputView.setInErrorState(true);
          return false;
        }
        if (newName.length() > 128) {
          inputView.setInErrorState(true);
          return false;
        }
        // Edit the topic with new name, keep existing icon
        tdlib.client().send(new TdApi.EditForumTopic(
          topic.info.chatId,
          topic.info.forumTopicId,
          newName,
          false, // editIconCustomEmoji
          0 // iconCustomEmojiId (not changing)
        ), result1 -> {
          UI.post(() -> {
            if (result1.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
              // Topic edited successfully, will be updated via listener
            } else if (result1.getConstructor() == TdApi.Error.CONSTRUCTOR) {
              UI.showError(result1);
            }
          });
        });
        return true;
      },
      true
    );
  }

  private void showTopicIconPicker (TdApi.ForumTopic topic) {
    // First, load available default topic icons from TDLib
    tdlib.client().send(new TdApi.GetForumTopicDefaultIcons(), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.Stickers.CONSTRUCTOR) {
          TdApi.Stickers stickers = (TdApi.Stickers) result;
          showTopicIconPickerWithStickers(topic, stickers.stickers);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  private void showTopicIconPickerWithStickers (TdApi.ForumTopic topic, TdApi.Sticker[] stickers) {
    // Build options list
    // First option: Reset to colored circle (if topic has custom emoji)
    boolean hasCustomEmoji = topic.info.icon != null && topic.info.icon.customEmojiId != 0;

    IntList ids = new IntList(15);
    ArrayList<String> strings = new ArrayList<>();
    IntList iconsList = new IntList(15);

    if (hasCustomEmoji) {
      ids.append(R.id.btn_resetIcon);
      strings.add(Lang.getString(R.string.ResetTopicIcon));
      iconsList.append(R.drawable.baseline_undo_24);
    }

    // Add sticker options (limit to reasonable number)
    int maxStickers = Math.min(stickers.length, 12);
    for (int i = 0; i < maxStickers; i++) {
      ids.append(R.id.btn_stickerIcon0 + i);
      // Use emoji as label if available
      strings.add(stickers[i].emoji != null ? stickers[i].emoji : "Icon " + (i + 1));
      iconsList.append(0); // No drawable icon, uses text
    }

    showOptions(
      Lang.getString(R.string.ChangeTopicIcon),
      ids.get(),
      strings.toArray(new String[0]),
      null,
      iconsList.get(),
      (itemView, id) -> {
        if (id == R.id.btn_resetIcon) {
          // Reset to colored circle
          setTopicIcon(topic, 0);
        } else {
          // Set custom emoji icon
          int stickerIndex = id - R.id.btn_stickerIcon0;
          if (stickerIndex >= 0 && stickerIndex < stickers.length) {
            // The sticker id is the custom emoji identifier for custom emoji stickers
            setTopicIcon(topic, stickers[stickerIndex].id);
          }
        }
        return true;
      }
    );
  }

  private void setTopicIcon (TdApi.ForumTopic topic, long customEmojiId) {
    tdlib.client().send(new TdApi.EditForumTopic(
      topic.info.chatId,
      topic.info.forumTopicId,
      topic.info.name,
      true, // editIconCustomEmoji
      customEmojiId // 0 = reset to colored circle, non-zero = custom emoji
    ), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  private void deleteTopic (TdApi.ForumTopic topic) {
    showConfirm(Lang.getStringBold(R.string.DeleteTopicConfirm, topic.info.name), Lang.getString(R.string.Delete), R.drawable.baseline_delete_24, OptionColor.RED, () -> {
      tdlib.client().send(new TdApi.DeleteForumTopic(topic.info.chatId, topic.info.forumTopicId), result -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          // Remove from local list immediately
          UI.post(() -> {
            for (int i = 0; i < topics.size(); i++) {
              if (topics.get(i).info.forumTopicId == topic.info.forumTopicId) {
                topics.remove(i);
                adapter.notifyItemRemoved(i);
                updateEmptyView();
                break;
              }
            }
          });
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.post(() -> UI.showError(result));
        }
      });
    });
  }

  private void showTopicFilterOptions () {
    // Use all topics from allTopics list
    if (allTopics == null || allTopics.isEmpty()) {
      return; // No topics to filter by
    }

    List<TdApi.ForumTopic> availableTopics = allTopics;

    // Track current selections - start with all topics if no filter, or current filter
    final java.util.Set<Long> currentSelections = new java.util.HashSet<>();
    if (selectedFilterTopicIds.isEmpty()) {
      // No filter = all topics selected
      for (TdApi.ForumTopic topic : availableTopics) {
        long topicId = topic.info.forumTopicId;
        currentSelections.add(topicId);
      }
    } else {
      currentSelections.addAll(selectedFilterTopicIds);
    }

    // Build checkbox items for multi-select
    List<ListItem> items = new ArrayList<>(availableTopics.size() + 2);
    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)).setBoolValue(true));

    // Add each topic as a checkbox option with topic icon
    for (TdApi.ForumTopic topic : availableTopics) {
      long topicId = topic.info.forumTopicId;
      boolean isSelected = currentSelections.contains(topicId);
      // Use TopicIconModifier to draw the actual topic icon (colored circle or custom emoji)
      TopicIconModifier iconModifier = new TopicIconModifier(tdlib, topic.info.icon);
      // Icon is drawn on the left by the modifier, add padding for icon space
      String displayName = "        " + topic.info.name;
      items.add(new ListItem(
        ListItem.TYPE_CHECKBOX_OPTION,
        (int) topicId, // id
        0, // icon
        displayName, // string with space prefix for icon
        (int) topicId, // checkId (same as id for multi-select)
        isSelected
      ).setLongValue(topicId).setDrawModifier(iconModifier));
    }

    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)).setBoolValue(true));

    final int totalTopicCount = availableTopics.size();

    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_filterTopic)
      .addHeaderItem(Lang.getString(R.string.FilterByTopic))
      .setRawItems(items)
      .setSaveStr(Lang.getString(R.string.Done))
      .setNeedSeparators(false)
      .setSettingProcessor((item, view, isUpdate) -> {
        // Apply the DrawModifier to render topic icons
        view.setDrawModifier(item.getDrawModifier());
      })
      .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> {
        // Update our tracked selections when checkbox is toggled
        if (item.getViewType() == ListItem.TYPE_CHECKBOX_OPTION) {
          long topicId = item.getLongValue();
          if (currentSelections.contains(topicId)) {
            currentSelections.remove(topicId);
          } else {
            currentSelections.add(topicId);
          }
        }
      })
      .setIntDelegate((id, result) -> {
        // Use our tracked selections instead of the result array
        if (currentSelections.size() == totalTopicCount) {
          // All selected = no filter
          applyTopicFilter(new java.util.HashSet<>());
        } else {
          applyTopicFilter(new java.util.HashSet<>(currentSelections));
        }
      });

    showSettings(b);
  }

  // Map topic color to a Unicode circle emoji for display in filter dialog
  private String getTopicColorEmoji (int colorValue) {
    // Telegram topic default colors mapped to emoji circles
    // Blue 0x6FB9F0, Yellow 0xFFD67E, Purple 0xCB86DB, Green 0x8EEE98, Pink 0xFF93B2, Red 0xFB6F5F

    // Normalize color (remove alpha if present)
    int color = colorValue & 0x00FFFFFF;

    // Check for custom emoji (iconCustomEmojiId != 0) - use white circle as default
    if (colorValue == 0) {
      return "\u26AA"; // White circle
    }

    // Map based on hue - determine closest match
    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;

    // Simple color matching based on dominant channel
    if (b > r && b > g) {
      return "\uD83D\uDD35"; // Blue circle
    } else if (r > g && r > b && g > b * 0.8) {
      // Yellow/Orange (high red and green, low blue)
      return "\uD83D\uDFE1"; // Yellow circle
    } else if (r > b && g > b && Math.abs(r - g) < 50) {
      // Could be yellow or green - check green dominance
      if (g > r) {
        return "\uD83D\uDFE2"; // Green circle
      }
      return "\uD83D\uDFE1"; // Yellow circle
    } else if (g > r && g > b) {
      return "\uD83D\uDFE2"; // Green circle
    } else if (r > g && b > g * 0.5) {
      // Purple/Pink (high red and blue)
      if (b > r * 0.7) {
        return "\uD83D\uDFE3"; // Purple circle
      }
      return "\uD83D\uDD34"; // Red circle (for pink)
    } else if (r > g && r > b) {
      return "\uD83D\uDD34"; // Red circle
    }

    return "\u26AA"; // White circle as fallback
  }

  private void applyTopicFilter (java.util.Set<Long> topicIds) {
    applyTopicFilter(topicIds, false);
  }

  private void applyTopicFilter (java.util.Set<Long> topicIds, boolean isAutoRetry) {
    selectedFilterTopicIds = topicIds;

    // Reset retry counter on manual filter change
    if (!isAutoRetry) {
      filterAutoRetryCount = 0;
    }

    // Update FAB appearance based on filter state
    if (filterTopicButton != null) {
      if (!topicIds.isEmpty()) {
        // Filter is active - use accent color
        filterTopicButton.init(R.drawable.baseline_tune_24, 56f, 4f, ColorId.circleButtonActive, ColorId.circleButtonActiveIcon);
      } else {
        // No filter - regular color
        filterTopicButton.init(R.drawable.baseline_tune_24, 56f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
      }
    }

    if (topicIds.isEmpty()) {
      // Show all results
      messageSearchResults.clear();
      messageSearchResults.addAll(unfilteredMessageResults);
    } else {
      // Filter to only show messages from selected topics
      messageSearchResults.clear();
      for (TopicMessageSearchResult result : unfilteredMessageResults) {
        long topicId = result.topic.info.forumTopicId; // Cast int to long for Set<Long> contains check
        if (topicIds.contains(topicId)) {
          messageSearchResults.add(result);
        }
      }
    }

    // Auto-retry: if filtered results are empty but more messages available, load more
    if (messageSearchResults.isEmpty() && !topicIds.isEmpty() && canLoadMoreMessages && filterAutoRetryCount < MAX_AUTO_RETRY) {
      filterAutoRetryCount++;
      pendingFilterTopicIds = new java.util.HashSet<>(topicIds);

      // Show "searching deeper" message
      emptyView.setVisibility(View.VISIBLE);
      emptyView.showInfo(Lang.getString(R.string.LoadingTopics) + "...");

      // Load more pages
      loadMoreMessages();
      return;
    }

    // Clear pending filter
    pendingFilterTopicIds = null;

    adapter.setMessageSearchResults(messageSearchResults, currentSearchQuery);
    updateEmptyViewForMessageSearch();
  }

  private void showTopicMuteOptions (TdApi.ForumTopic topic) {
    boolean isMuted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0;

    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    ArrayList<String> strings = new ArrayList<>();

    if (isMuted) {
      // Currently muted - show unmute option
      ids.append(R.id.btn_menu_enable);
      icons.append(R.drawable.baseline_notifications_24);
      strings.add(Lang.getString(R.string.EnableNotifications));
    } else {
      // Currently unmuted - show mute options
      ids.append(R.id.btn_menu_1hour);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXHours, 1));

      ids.append(R.id.btn_menu_8hours);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXHours, 8));

      ids.append(R.id.btn_menu_2days);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXDays, 2));

      ids.append(R.id.btn_menu_disable);
      icons.append(R.drawable.baseline_notifications_off_24);
      strings.add(Lang.getString(R.string.MuteForever));
    }

    showOptions(topic.info.name, ids.get(), strings.toArray(new String[0]), null, icons.get(), (itemView, id) -> {
      int muteFor = 0;
      if (id == R.id.btn_menu_enable) {
        muteFor = 0; // Unmute
      } else if (id == R.id.btn_menu_1hour) {
        muteFor = (int) java.util.concurrent.TimeUnit.HOURS.toSeconds(1);
      } else if (id == R.id.btn_menu_8hours) {
        muteFor = (int) java.util.concurrent.TimeUnit.HOURS.toSeconds(8);
      } else if (id == R.id.btn_menu_2days) {
        muteFor = (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(2);
      } else if (id == R.id.btn_menu_disable) {
        muteFor = Integer.MAX_VALUE; // Mute forever
      }
      setForumTopicMuteFor(topic, muteFor);
      return true;
    });
  }

  private void setForumTopicMuteFor (TdApi.ForumTopic topic, int muteFor) {
    TdApi.ChatNotificationSettings settings = new TdApi.ChatNotificationSettings();
    settings.useDefaultMuteFor = (muteFor == 0);
    settings.muteFor = muteFor;
    settings.useDefaultSound = true;
    settings.useDefaultShowPreview = true;
    settings.useDefaultMuteStories = true;
    settings.useDefaultStorySound = true;
    settings.useDefaultDisablePinnedMessageNotifications = true;
    settings.useDefaultDisableMentionNotifications = true;

    tdlib.client().send(new TdApi.SetForumTopicNotificationSettings(
      topic.info.chatId,
      topic.info.forumTopicId,
      settings
    ), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          // Update local state and refresh UI
          if (topic.notificationSettings == null) {
            topic.notificationSettings = new TdApi.ChatNotificationSettings();
          }
          topic.notificationSettings.muteFor = muteFor;
          topic.notificationSettings.useDefaultMuteFor = (muteFor == 0);
          // Refresh the topic in the list
          for (int i = 0; i < topics.size(); i++) {
            if (topics.get(i).info.forumTopicId == topic.info.forumTopicId) {
              adapter.notifyItemChanged(i);
              break;
            }
          }
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  private void showCreateTopicDialog () {
    openInputAlert(
      Lang.getString(R.string.NewTopic),
      Lang.getString(R.string.TopicNameHint),
      R.string.Done,
      R.string.Cancel,
      null,
      (inputView, result) -> {
        String name = result.trim();
        if (name.isEmpty()) {
          inputView.setInErrorState(true);
          return false;
        }
        if (name.length() > 128) {
          inputView.setInErrorState(true);
          return false;
        }
        createTopic(name);
        return true;
      },
      true
    );
  }

  private void createTopic (String name) {
    // Standard topic colors from Telegram
    int[] topicColors = {
      0x6FB9F0, // Blue
      0xFFD67E, // Yellow
      0xCB86DB, // Purple
      0x8EEE98, // Green
      0xFF93B2, // Pink
      0xFB6F5F  // Red
    };
    // Pick a random color
    int color = topicColors[(int) (Math.random() * topicColors.length)];

    TdApi.ForumTopicIcon icon = new TdApi.ForumTopicIcon(color, 0);

    tdlib.client().send(new TdApi.CreateForumTopic(chatId, name, false, icon), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.ForumTopicInfo.CONSTRUCTOR) {
          TdApi.ForumTopicInfo info = (TdApi.ForumTopicInfo) result;
          // Reload topics to show the new one
          loadTopics();
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  // ChatListener (ForumTopicInfoListener) implementation
  @Override
  public void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) {
    if (info.chatId != chatId) return;
    UI.post(() -> {
      for (int i = 0; i < topics.size(); i++) {
        if (topics.get(i).info.forumTopicId == info.forumTopicId) {
          topics.get(i).info = info;
          adapter.notifyItemChanged(i);
          break;
        }
      }
    });
  }

  @Override
  public void onForumTopicUpdated (long chatId, long messageThreadId, boolean isPinned, long lastReadInboxMessageId, long lastReadOutboxMessageId, int unreadMentionCount, int unreadReactionCount, TdApi.ChatNotificationSettings notificationSettings) {
    if (chatId != this.chatId) return;

    // Find if read state changed - if so, we need to fetch fresh unread count
    boolean needFetchUnreadCount = false;
    for (int i = 0; i < topics.size(); i++) {
      TdApi.ForumTopic topic = topics.get(i);
      if (topic.info.forumTopicId == messageThreadId) {
        if (topic.lastReadInboxMessageId != lastReadInboxMessageId) {
          needFetchUnreadCount = true;
        }
        break;
      }
    }

    if (needFetchUnreadCount) {
      // Fetch fresh topic info to get accurate unread count
      tdlib.client().send(new TdApi.GetForumTopic(chatId, (int) messageThreadId), result -> {
        if (result.getConstructor() == TdApi.ForumTopic.CONSTRUCTOR) {
          TdApi.ForumTopic freshTopic = (TdApi.ForumTopic) result;
          UI.post(() -> {
            for (int i = 0; i < topics.size(); i++) {
              if (topics.get(i).info.forumTopicId == messageThreadId) {
                topics.set(i, freshTopic);
                adapter.notifyItemChanged(i);
                break;
              }
            }
          });
        }
      });
    } else {
      // Just update local state without fetching
      UI.post(() -> {
        for (int i = 0; i < topics.size(); i++) {
          TdApi.ForumTopic topic = topics.get(i);
          if (topic.info.forumTopicId == messageThreadId) {
            topic.isPinned = isPinned;
            topic.lastReadInboxMessageId = lastReadInboxMessageId;
            topic.lastReadOutboxMessageId = lastReadOutboxMessageId;
            topic.unreadMentionCount = unreadMentionCount;
            topic.unreadReactionCount = unreadReactionCount;
            if (notificationSettings != null) {
              topic.notificationSettings = notificationSettings;
            }
            adapter.notifyItemChanged(i);
            break;
          }
        }
      });
    }
  }

  // Permission checks for topic actions
  private boolean canCreateTopics () {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    if (status == null) return false;

    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageTopics;
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        // Check chat-level permissions
        return chat != null && chat.permissions != null && chat.permissions.canCreateTopics;
      default:
        return false;
    }
  }

  private boolean canManageTopics () {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    if (status == null) return false;

    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageTopics;
      default:
        return false;
    }
  }

  // TdlibCache.SupergroupDataChangeListener implementation
  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    tdlib.ui().post(() -> {
      if (ChatId.toSupergroupId(chatId) == supergroup.id) {
        // Check if forum mode was disabled externally or tabs layout changed
        if (!supergroup.isForum && chat != null) {
          // Forum mode was disabled - navigate to regular chat view
          navigateBack();
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              tdlib.ui().openChat(this, chat.id, new TdlibUi.ChatOpenParameters().keepStack());
            }
          });
        } else if (supergroup.isForum && supergroup.hasForumTabs && chat != null) {
          // Tabs layout was enabled - switch to tabs controller
          navigateBack();
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              tdlib.ui().openChat(this, chat.id, new TdlibUi.ChatOpenParameters().keepStack());
            }
          });
        }
      }
    });
  }

  @Override
  public void onSupergroupFullUpdated (long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    // Not used
  }

  // Message search result data class
  public static class TopicMessageSearchResult {
    public final TdApi.ForumTopic topic;
    public final TdApi.Message foundMessage;
    public final String highlightQuery;

    public TopicMessageSearchResult (TdApi.ForumTopic topic, TdApi.Message foundMessage, String query) {
      this.topic = topic;
      this.foundMessage = foundMessage;
      this.highlightQuery = query;
    }
  }

  // Inner adapter class
  private static class ForumTopicsAdapter extends RecyclerView.Adapter<ForumTopicViewHolder> {
    private final ForumTopicsController controller;
    private List<TdApi.ForumTopic> topics = new ArrayList<>();
    private List<TopicMessageSearchResult> messageSearchResults = new ArrayList<>();
    private boolean isMessageSearchMode = false;
    private String highlightQuery;

    ForumTopicsAdapter (ForumTopicsController controller) {
      this.controller = controller;
    }

    void setTopics (List<TdApi.ForumTopic> topics, @Nullable String highlightQuery) {
      this.topics = topics;
      this.highlightQuery = highlightQuery;
      this.isMessageSearchMode = false;
      notifyDataSetChanged();
    }

    void setMessageSearchResults (List<TopicMessageSearchResult> results, @Nullable String highlightQuery) {
      this.messageSearchResults = results != null ? results : new ArrayList<>();
      this.highlightQuery = highlightQuery;
      this.isMessageSearchMode = true;
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ForumTopicViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      ForumTopicView view = new ForumTopicView(parent.getContext());
      view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(72f)));
      view.setOnClickListener(controller);
      view.setOnLongClickListener(controller);
      return new ForumTopicViewHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull ForumTopicViewHolder holder, int position) {
      if (isMessageSearchMode) {
        TopicMessageSearchResult result = messageSearchResults.get(position);
        holder.bindMessageSearchResult(controller.tdlib, result);
      } else {
        TdApi.ForumTopic topic = topics.get(position);
        holder.bind(controller.tdlib, topic, highlightQuery);
      }
    }

    @Override
    public void onViewAttachedToWindow (@NonNull ForumTopicViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull ForumTopicViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).detach();
      }
    }

    @Override
    public void onViewRecycled (@NonNull ForumTopicViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).destroy();
      }
    }

    @Override
    public int getItemCount () {
      return isMessageSearchMode ? messageSearchResults.size() : topics.size();
    }
  }

  private static class ForumTopicViewHolder extends RecyclerView.ViewHolder {
    ForumTopicViewHolder (@NonNull View itemView) {
      super(itemView);
    }

    void bind (Tdlib tdlib, TdApi.ForumTopic topic, @Nullable String highlightQuery) {
      if (itemView instanceof ForumTopicView) {
        ((ForumTopicView) itemView).setTopic(tdlib, topic, highlightQuery);
        itemView.setTag(topic);
      }
    }

    void bindMessageSearchResult (Tdlib tdlib, TopicMessageSearchResult result) {
      if (itemView instanceof ForumTopicView) {
        ((ForumTopicView) itemView).setMessageSearchResult(tdlib, result.topic, result.foundMessage, result.highlightQuery);
        itemView.setTag(result);
      }
    }
  }
}
