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
 * File created on 25/12/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CancellableResultHandler;
import org.thunderdog.challegram.util.MessageSourceProvider;
import org.thunderdog.challegram.v.MediaRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.EmptySmartView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public abstract class SharedBaseController <T extends MessageSourceProvider> extends ViewController<SharedBaseController.Args> implements View.OnClickListener, View.OnLongClickListener, FactorAnimator.Target, MessageListener {
  public static class Args {
    public long chatId, messageThreadId;

    public Args (long chatId, long messageThreadId) {
      this.chatId = chatId;
      this.messageThreadId = messageThreadId;
    }
  }

  public SharedBaseController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static boolean isMediaController (SharedBaseController<?> c) {
    return c instanceof SharedCommonController || c instanceof SharedMediaController;
  }

  @Override
  public long getChatId () {
    return chatId;
  }

  public static SharedBaseController<?> valueOf (Context context, Tdlib tdlib, TdApi.SearchMessagesFilter filter) {
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        return new SharedMediaController(context, tdlib).setFilter(filter);
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR:
        return new SharedCommonController(context, tdlib).setFilter(filter);
    }
    throw new IllegalArgumentException("unsupported filter: " + filter);
  }

  protected long chatId, messageThreadId;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    chatId = args.chatId;
    messageThreadId = args.messageThreadId;
  }

  private boolean isPrepared;

  public boolean isPrepared () {
    return isPrepared;
  }

  public void setPrepared () {
    isPrepared = true;
  }

  @Override
  public int getId () {
    return R.id.controller_media__new;
  }

  protected @Nullable ProfileController parent;
  protected @Nullable MessagesController alternateParent;

  protected MediaRecyclerView recyclerView;
  protected SettingsAdapter adapter;

  public void setParent (@NonNull ProfileController parent) {
    this.parent = parent;
  }

  public void setParent (@NonNull MessagesController parent) {
    this.alternateParent = parent;
    tdlib.listeners().subscribeToMessageUpdates(chatId, this);
  }

  @SuppressLint("InflateParams")
  @Override
  protected final View onCreateView (Context context) {
    recyclerView = (MediaRecyclerView) Views.inflate(context(), R.layout.recycler_sharedmedia, null);
    recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);
    addThemeInvalidateListener(recyclerView);
    if (alternateParent != null) {
      recyclerView.setBackgroundColor(Theme.backgroundColor());
      addThemeBackgroundColorListener(recyclerView, R.id.theme_color_background);
    }
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setItemAnimator(null); // new CustomItemAnimator(Anim.DECELERATE_INTERPOLATOR, 180l));
    adapter = new SettingsAdapter(this, needsDefaultOnClick() ? this : null, this) {
      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        if (isLoading || canLoadMore()) {
          infoView.showProgress();
        } else {
          infoView.showInfo(buildTotalCount(isSearching() ? searchData : data));
        }
      }

      @Override
      protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
        modifyChatViewIfNeeded(item, chatView, checkBox, isUpdate);
      }
    };
    if (probablyHasEmoji()) {
      TGLegacyManager.instance().addEmojiListener(adapter);
    }
    if (needsDefaultLongPress()) {
      adapter.setOnLongClickListener(this);
    }
    if (parent != null) {
      RecyclerView.ItemDecoration decoration = parent.newContentDecoration(this);
      if (decoration != null) {
        recyclerView.addItemDecoration(decoration);
      }
      parent.addOnScrollListener(recyclerView);
    }
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        loadMoreIfNeeded();
        List<ListItem> items = adapter.getItems();
        if (items.size() == 1 && items.get(0).getViewType() == ListItem.TYPE_SMART_EMPTY) {
          View view = recyclerView.getLayoutManager().findViewByPosition(0);
          if (view != null) {
            view.invalidate();
          }
        }
      }
    });
    onCreateView(context, recyclerView, adapter);
    buildCells();
    recyclerView.setAdapter(adapter);
    loadInitialChunk();
    return recyclerView;
  }

  public RecyclerView getRecyclerView () {
    return recyclerView;
  }

  public final void stopScroll () {
    if (recyclerView != null) {
      recyclerView.stopScroll();
    }
  }

  public final void onItemsHeightProbablyChanged () {
    if (recyclerView != null && parent != null) {
      parent.setIgnoreAnyPagerScrollEvents(true);
      recyclerView.invalidateItemDecorations();
      parent.checkContentScrollY(this);
      parent.setIgnoreAnyPagerScrollEvents(false);
    }
  }

  public final void ensureMaxScrollY (int scrollY, int maxScrollY) {
    if (recyclerView != null) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

      if (scrollY < maxScrollY) {
        manager.scrollToPositionWithOffset(0, -scrollY);
        return;
      }

      int firstVisiblePosition = manager.findFirstVisibleItemPosition();

      if (firstVisiblePosition == 0 || firstVisiblePosition == -1) {
        View view = manager.findViewByPosition(0);
        if (view != null) {
          int top = view.getTop();
          if (parent != null) {
            top -= parent.getItemsBound();
          }
          if (top > 0) {
            manager.scrollToPositionWithOffset(0, -maxScrollY);
          }
        } else {
          manager.scrollToPositionWithOffset(0, -maxScrollY);
        }
      }
    }
  }

  protected void onCreateView (Context context, MediaRecyclerView recyclerView, SettingsAdapter adapter) {
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
  }

  protected int getItemCellHeight () {
    return Screen.dp(72f);
  }

  protected int calculateInitialLoadCount () { // override if otherwise needed
    return Screen.calculateLoadingItems(getItemCellHeight(), 10);
  }

  protected abstract CharSequence buildTotalCount (ArrayList<T> data);

  @Override
  public abstract CharSequence getName ();
  // public abstract @DrawableRes int getIcon ();

  public final @Nullable String getCurrentQuery () {
    return currentQuery;
  }

  public void search (String query) {
    if (!StringUtils.equalsOrBothEmpty(currentQuery, query)) {
      setInMediaSelectMode(false);
      loadMessages(query, 0, calculateInitialLoadCount());
    }
  }

  public final void onGlobalHeightChanged () {
    onItemsHeightProbablyChanged();
  }

  protected int calculateScrollY (int position) {
    if (position == 0) {
      return 0;
    }

    int scrollY = 0;
    int i = 0;

    for (ListItem item : adapter.getItems()) {
      switch (item.getViewType()) {
        case ListItem.TYPE_CUSTOM_INLINE: {
          final InlineResult<?> result = (InlineResult<?>) item.getData();
          final int width = recyclerView.getMeasuredWidth();
          if (width != 0) {
            result.prepare(width);
          }
          scrollY += result.getHeight();
          break;
        }
        case ListItem.TYPE_SMART_EMPTY:
        case ListItem.TYPE_SMART_PROGRESS: {
          scrollY += measureBaseItemHeight(item.getViewType());
          break;
        }
        default: {
          scrollY += SettingHolder.measureHeightForType(item.getViewType());
          break;
        }
      }
      i++;
      if (i == position) {
        break;
      }
    }

    return scrollY;
  }

  public final int calculateItemsHeight () {
    return calculateScrollY(adapter.getItems().size());
  }

  protected final int calculateScrollY () {
    if (recyclerView == null) {
      return 0;
    }

    int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i == -1) {
      return 0;
    }

    int scrollY = calculateScrollY(i);
    View view = recyclerView.getLayoutManager().findViewByPosition(i);
    if (view != null) {
      scrollY -= view.getTop();
    }

    return scrollY;
  }

  protected int getHeaderItemCount () {
    int i = adapter.indexOfViewById(R.id.shadowTop);
    return i != -1 ? i + 1 : 0;
  }

  protected static final int FLAG_NEED_SPLITTING = 1;
  protected static final int FLAG_USE_FIRST_HEADER_SPACING = 1 << 1;

  protected static <T extends MessageSourceProvider> boolean addItems (List<ListItem> reuse, int viewType, ArrayList<T> dateItems, int offset, List<ListItem> out, @Nullable SettingsAdapter adapter, SharedBaseController<?> controller, int flags) {
    if (dateItems.isEmpty()) {
      return false;
    }

    boolean needSplitting = (flags & FLAG_NEED_SPLITTING) != 0;
    boolean useFirstHeaderSpacing = (flags & FLAG_USE_FIRST_HEADER_SPACING) != 0;

    final int currentEndIndex = out.size();

    reuse.clear();
    ArrayUtils.ensureCapacity(reuse, dateItems.size());

    int lastDate = offset == 0 ? -1 : dateItems.get(offset - 1).getSourceDate();
    int lastAnchorMode;
    if (offset == 0) {
      lastAnchorMode = -1;
    } else {
      lastAnchorMode = TD.getAnchorMode(lastDate);
    }

    boolean inlineResults = dateItems.get(0) instanceof InlineResult;
    final int size = dateItems.size();
    for (int i = offset; i < size; i++) {
      T item = dateItems.get(i);
      if (needSplitting) {
        int sourceDate = item.getSourceDate();
        int anchorMode = TD.getAnchorMode(sourceDate);
        boolean forceSeparator = true;
        if (anchorMode != lastAnchorMode || lastDate == -1 || TD.shouldSplitDatesByMonth(anchorMode, lastDate, sourceDate)) {
          if (i != offset || offset != 0) {
            reuse.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          }
          reuse.add(new ListItem(offset == 0 && useFirstHeaderSpacing ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, Lang.getRelativeMonth(sourceDate, TimeUnit.SECONDS, true), false));
          reuse.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

          lastDate = sourceDate;
          lastAnchorMode = anchorMode;
          forceSeparator = false;
        }
        if (inlineResults) {
          ((InlineResult<?>) item).setForceSeparator(forceSeparator);
        }
      } else {
        if (offset == 0 && i == 0) {
          String explainedTitle = controller.getExplainedTitle();
          if (!StringUtils.isEmpty(explainedTitle)) {
            reuse.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, explainedTitle, false));
          }
          reuse.add(new ListItem(ListItem.TYPE_SHADOW_TOP, R.id.shadowTop));
        } else {
          reuse.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
      }
      reuse.add(new ListItem(viewType).setData(item).setLongId(item.getSourceMessageId()));
    }

    if (offset == 0) {
      reuse.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      reuse.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW, R.id.search_counter).setIntValue(-1));
      out.addAll(reuse);
      if (adapter != null) {
        adapter.notifyItemRangeInserted(currentEndIndex, reuse.size());
        controller.onItemsHeightProbablyChanged();
      }
    } else {
      out.addAll(currentEndIndex - 2, reuse);
      if (adapter != null) {
        adapter.notifyItemRangeInserted(currentEndIndex - 2, reuse.size());
        controller.onItemsHeightProbablyChanged();
      }
    }

    return !reuse.isEmpty();
  }

  // Data load

  private void loadInitialChunk () {
    loadMessages(null, 0, calculateInitialLoadCount());
  }

  protected int getLoadColumnCount () {
    return 0;
  }

  private void loadMoreIfNeeded () {
    if (canLoadMore()) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      int lastVisibleItemPosition = manager.findLastVisibleItemPosition();
      if (lastVisibleItemPosition != -1 && lastVisibleItemPosition + 6 >= adapter.getItems().size()) {
        long offsetMessageId = getCurrentOffset(-1);
        if (offsetMessageId != -1) {
          int loadCount = 40;
          int columnCount = getLoadColumnCount();
          if (columnCount > 1) {
            int remaining = loadCount % columnCount;
            if (remaining != 0) {
              loadCount += columnCount - remaining - 1;
            }
          }
          loadMessages(currentQuery, offsetMessageId, loadCount);
        }
      }
    }
  }

  protected static long getOffsetMessageId (ArrayList<? extends MessageSourceProvider> data, long emptyValue) {
    return data == null || data.isEmpty() ? emptyValue : data.get(data.size() - 1).getSourceMessageId();
  }

  protected final boolean isSearching () {
    return (currentQuery != null && !currentQuery.isEmpty());
  }

  private boolean canLoadMore () {
    return isSearching() ? canLoadMoreSearch : canLoadMoreData;
  }

  private void setCanLoadMore (boolean canLoadMore, boolean notify) {
    boolean prevCanLoadMore = canLoadMore();
    if (prevCanLoadMore != canLoadMore) {
      if (isSearching()) {
        this.canLoadMoreSearch = canLoadMore;
      } else {
        this.canLoadMoreData = canLoadMore;
      }
      if (notify) {
        adapter.notifyItemChanged(adapter.getItems().size() - 1);
      }
    }
  }

  private boolean isLoading;
  private boolean canLoadMoreData, canLoadMoreSearch;
  private @Nullable String currentQuery;
  private CancellableRunnable searchTask;
  private CancellableResultHandler baseHandler;

  protected TdApi.Function<?> buildRequest (final long chatId, final long messageThreadId, final String query, final long offset, final String secretOffset, final int limit) {
    if (StringUtils.isEmpty(query) || !ChatId.isSecret(chatId)) {
      return new TdApi.SearchChatMessages(chatId, query, null, offset, 0, limit, provideSearchFilter(), messageThreadId);
    } else {
      return new TdApi.SearchSecretMessages(chatId, query, secretOffset, limit, provideSearchFilter());
    }
  }

  protected final void performRequest (final long chatId, final long messageThreadId, final String query, final long offset, final String secretOffset, final int limit) {
    TdApi.Function<?> function = buildRequest(chatId, messageThreadId, query, offset, secretOffset, limit);
    if (function == null) {
      return;
    }
    tdlib.client().send(function, object -> processData(query, offset, object, limit));
  }

  private void loadMessages (final String query, final long fromMessageId, final int limit) {
    if (!this.isLoading || !StringUtils.equalsOrBothEmpty(query, currentQuery)) {
      this.isLoading = true;
      boolean processingSearch = !StringUtils.equalsOrBothEmpty(query, currentQuery);
      this.currentQuery = query;

      if (searchTask != null) {
        searchTask.cancel();
        searchTask = null;
      }

      if (baseHandler != null) {
        baseHandler.cancel();
        baseHandler = null;
      }

      if (processingSearch) {
        this.searchData = null;
        int scrollY = calculateScrollY();
        buildCells();
        recyclerView.invalidateItemDecorations();

        /*if (scrollY > 0) {
          // TODO
        }*/
        ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, parent != null ? -parent.maxItemsScrollYOffset() : 0);
      }

      if (currentQuery == null || currentQuery.isEmpty()) {
        if (!processingSearch) {
          performRequest(chatId, messageThreadId, query, fromMessageId, nextSecretSearchOffset, limit);
          if (fromMessageId == 0) {
            baseHandler = null;
          }
        } else {
          isLoading = false;
        }
      } else {
        if (fromMessageId == 0) {
          searchTask = new CancellableRunnable() {
            @Override
            public void act () {
              performRequest(chatId, messageThreadId, query, fromMessageId, nextSecretSearchOffset, limit);
            }
          };
          searchTask.removeOnCancel(UI.getAppHandler());
          UI.post(searchTask, 300l);
        } else {
          performRequest(chatId, messageThreadId, query, fromMessageId, nextSecretSearchOffset, limit);
        }
      }
    }
  }

  protected abstract T parseObject (TdApi.Object object);

  protected final long getCurrentOffset (long emptyValue) {
    return getCurrentOffset(isSearching() ? this.searchData : this.data, emptyValue);
  }

  protected long getCurrentOffset (ArrayList<T> data, long emptyValue) {
    return getOffsetMessageId(data, emptyValue);
  }

  protected final void processData (final String query, final long offset, final TdApi.Object object, int limit) {
    final ArrayList<T> items;
    String nextSearchOffset = null;
    long nextOffset = 0;
    switch (object.getConstructor()) {
      case TdApi.FoundMessages.CONSTRUCTOR: {
        TdApi.FoundMessages messages = (TdApi.FoundMessages) object;
        items = new ArrayList<>(messages.messages.length);
        for (TdApi.Message message : messages.messages) {
          if (message == null) {
            continue;
          }
          nextOffset = message.id;
          T parsedItem = parseObject(message);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        nextSearchOffset = messages.nextOffset;
        modifyResultIfNeeded(items, true);
        break;
      }
      case TdApi.Messages.CONSTRUCTOR: {
        TdApi.Messages messages = (TdApi.Messages) object;
        items = new ArrayList<>(messages.messages.length);
        for (TdApi.Message message : messages.messages) {
          if (message == null) {
            continue;
          }
          nextOffset = message.id;
          T parsedItem = parseObject(message);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        modifyResultIfNeeded(items, true);
        break;
      }
      case TdApi.Chats.CONSTRUCTOR: {
        long[] chatsIds = ((TdApi.Chats) object).chatIds;
        List<TdApi.Chat> chats = tdlib.chats(chatsIds);
        items = new ArrayList<>(chats.size());
        for (TdApi.Chat chat : chats) {
          T parsedItem = parseObject(chat);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        modifyResultIfNeeded(items, true);
        break;
      }
      case TdApi.Users.CONSTRUCTOR: {
        long[] userIds = ((TdApi.Users) object).userIds;
        ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
        items = new ArrayList<>(users.size());
        for (TdApi.User user : users) {
          T parsedItem = parseObject(user);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        modifyResultIfNeeded(items, true);
        nextOffset = userIds.length;
        break;
      }
      case TdApi.BasicGroupFullInfo.CONSTRUCTOR: {
        TdApi.BasicGroupFullInfo groupFull = (TdApi.BasicGroupFullInfo) object;
        items = new ArrayList<>(groupFull.members.length);
        for (TdApi.ChatMember member : groupFull.members) {
          T parsedItem = parseObject(member);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        modifyResultIfNeeded(items, true);
        break;
      }
      case TdApi.ChatMembers.CONSTRUCTOR: {
        TdApi.ChatMembers members = (TdApi.ChatMembers) object;
        items = new ArrayList<>(members.members.length);
        for (TdApi.ChatMember member : members.members) {
          T parsedItem = parseObject(member);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        modifyResultIfNeeded(items, true);
        nextOffset = members.members.length;
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        items = new ArrayList<>(0);
        break;
      }
      default: {
        Log.unexpectedTdlibResponse(object, TdApi.GetChats.class, TdApi.Chats.class);
        return;
      }
    }
    final long nextOffsetFinal = nextOffset;
    final String nextSearchOffsetFinal = nextSearchOffset;
    tdlib.uiExecute(() -> {
      if (!isDestroyed()) {
        modifyResultIfNeeded(items, false);
        long currentOffset = getCurrentOffset(0);
        if (currentOffset == offset && StringUtils.equalsOrBothEmpty(query, currentQuery)) {
          addItems(items, nextSearchOffsetFinal, offset == 0);
        } else if (StringUtils.isEmpty(query) && isSearching()) {
          if (data == null || data.isEmpty()) {
            data = items;
          } else {
            data.addAll(items);
          }
          canLoadMoreData = !items.isEmpty() && supportsLoadingMore(!StringUtils.isEmpty(query));
        }
      }
    });
  }

  @Override
  public boolean needsTempUpdates () {
    return parent != null && parent.needsTempUpdates();
  }

  // UI data

  protected ArrayList<T> data, searchData;
  protected String nextSecretSearchOffset;

  private void setData (ArrayList<T> data, String nextSecretSearchOffset) {
    if (isSearching()) {
      this.searchData = data;
      this.nextSecretSearchOffset = nextSecretSearchOffset;
    } else {
      this.data = data;
    }
  }

  private void addItems (ArrayList<T> newData, String nextSearchOffset, boolean reset) {
    ArrayList<T> target = isSearching() ? searchData : data;

    this.isLoading = false;

    if (target == null || target.isEmpty() || reset) {
      setCanLoadMore(!newData.isEmpty() && supportsLoadingMore(isSearching()), false);
      if (target != null && target.isEmpty() && newData.isEmpty()) { // Nothing changed
        return;
      }
      setData(newData, nextSearchOffset);
      buildCells();
    } else if (!newData.isEmpty()) {
      setCanLoadMore(supportsLoadingMore(isSearching()), false);

      Comparator<T> comparator = provideItemComparator();
      if (comparator != null) {
        for (T item : newData) {
          int newIndex = Collections.binarySearch(target, item, comparator);
          if (newIndex < 0) {
            newIndex = newIndex * -1 - 1;
            target.add(newIndex, item);

            ListItem separatorItem = new ListItem(ListItem.TYPE_SEPARATOR);
            ListItem contentItem = new ListItem(provideViewType()).setData(item).setLongId(item.getSourceMessageId());

            if (newIndex == 0) {
              adapter.getItems().add(2, separatorItem);
              adapter.getItems().add(2, contentItem);
              adapter.notifyItemRangeInserted(2, 2);
            } else {
              int startIndex = 2 + newIndex * 2 - 1;
              adapter.getItems().add(startIndex, contentItem);
              adapter.getItems().add(startIndex, separatorItem);
              adapter.notifyItemRangeInserted(startIndex, 2);
            }
          }
        }
      } else {
        int startIndex = target.size();
        target.addAll(newData);
        addItems(reuse, provideViewType(), target, startIndex, adapter.getItems(), adapter, this, buildFlags());
      }

      if (!canLoadMore()) {
        adapter.notifyItemChanged(adapter.getItems().size());
      }
    } else {
      setCanLoadMore(false, true);
    }
  }

  private int buildFlags () {
    int flags = 0;
    if (needDateSectionSplitting()) {
      flags |= FLAG_NEED_SPLITTING;
    }
    if (alternateParent != null) {
      flags |= FLAG_USE_FIRST_HEADER_SPACING;
    }
    return flags;
  }

  private ArrayList<ListItem> reuse = new ArrayList<>();

  protected String getEmptySmartArgument () {
    return null;
  }

  protected int getEmptySmartMode () {
    TdApi.SearchMessagesFilter filter = provideSearchFilter();
    if (filter != null) {
      switch (filter.getConstructor()) {
        case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_MEDIA;
        }
        case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_PHOTO;
        }
        case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_VIDEO;
        }
        case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_GIFS;
        }
        case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_VOICE;
        }
        case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_VIDEO_MESSAGES;
        }
        case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_FILES;
        }
        case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_MUSIC;
        }
        case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR: {
          return EmptySmartView.MODE_EMPTY_LINKS;
        }
      }
    }
    return 0;
  }

  protected boolean isAlwaysEmpty () {
    return false;
  }

  protected final void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    ArrayList<T> target = isSearching() ? searchData : data;

    if (isAlwaysEmpty() || (target != null && target.isEmpty())) {
      if (adapter.getItems().size() == 1 && adapter.getItems().get(0).getViewType() == ListItem.TYPE_SMART_EMPTY) {
        return;
      }
      recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      items.add(new ListItem(ListItem.TYPE_SMART_EMPTY).setIntValue(getEmptySmartMode()).setStringValue(getEmptySmartArgument()).setBoolValue(tdlib.isChannel(chatId))); // FIXME TD.isFollowedChannel(chatId)
    } else if (target == null) {
      recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
      if (adapter.getItems().size() == 1 && adapter.getItems().get(0).getViewType() == ListItem.TYPE_SMART_PROGRESS) {
        return;
      }
      items.add(new ListItem(ListItem.TYPE_SMART_PROGRESS));
    } else {
      // recyclerView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
      addItems(reuse, provideViewType(), target, 0, items, null, this, buildFlags());
    }
    adapter.replaceItems(items);
    onItemsHeightProbablyChanged();
  }

  protected final int measureBaseItemHeight (int viewType) {
    switch (viewType) {
      case ListItem.TYPE_SMART_EMPTY:
      case ListItem.TYPE_SMART_PROGRESS: {
        return recyclerView.getMeasuredHeight();
      }
    }
    return SettingHolder.measureHeightForType(viewType);
  }

  protected abstract int provideViewType ();

  // Selection

  protected boolean onLongClick (View v, ListItem item) {
    return false;
  }

  @Override
  public final boolean onLongClick (View v) {
    RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(v);
    if (holder != null && holder instanceof SettingHolder) {
      Object tag = v.getTag();
      if (tag != null && tag instanceof ListItem) {
        ListItem item = (ListItem) tag;
        if (needsCustomLongClickListener()) {
          return onLongClick(v, item);
        } else {
          toggleSelected(item);
          return true;
        }
      }
    }
    return false;
  }

  private boolean inSelectMode;
  private Map<String, TdApi.Message> selectedMessages;

  public final int getSelectedMediaCount () {
    return selectedMessages != null ? selectedMessages.size() : 0;
  }

  public final @StringRes int getSelectedMediaSuffixRes () {
    if (getSelectedMediaCount() == 0)
      return R.string.SelectedSuffix;
    SparseIntArray map = TD.calculateCounters(selectedMessages);
    int size = map.size();
    if (size == 2 && map.indexOfKey(TdApi.MessagePhoto.CONSTRUCTOR) >= 0 && map.indexOfKey(TdApi.MessageVideo.CONSTRUCTOR) >= 0) {
      return R.string.AttachMediasSuffix;
    } else if (size == 1) {
      @TdApi.MessageContent.Constructors int constructor = map.keyAt(0);
      switch (constructor) {
        case TdApi.MessagePhoto.CONSTRUCTOR:
          return R.string.SelectedPhotoSuffix;
        case TdApi.MessageVideo.CONSTRUCTOR:
          return R.string.SelectedVideoSuffix;
        case TdApi.MessageAudio.CONSTRUCTOR:
          return R.string.SelectedAudioSuffix;
        case TdApi.MessageDocument.CONSTRUCTOR:
          return R.string.SelectedFileSuffix;
        case TdApi.MessageText.CONSTRUCTOR:
          return R.string.SelectedLinkSuffix;
        case TdApi.MessageAnimation.CONSTRUCTOR:
          return R.string.SelectedGifSuffix;
        case TdApi.MessageVoiceNote.CONSTRUCTOR:
          return R.string.SelectedVoiceSuffix;
        case TdApi.MessageVideoNote.CONSTRUCTOR:
          return R.string.SelectedRoundVideoSuffix;
      }
    }
    return R.string.SelectedSuffix;
  }

  public boolean setInMediaSelectMode (boolean inSelectMode) {
    if (this.inSelectMode != inSelectMode) {
      this.inSelectMode = inSelectMode;
      if (parent != null) {
        parent.setInMediaSelectMode(inSelectMode, getSelectedMediaSuffixRes());
      } else if (alternateParent != null) {
        if (inSelectMode) {
          alternateParent.openSelectMode(1);
        } else {
          alternateParent.closeSelectMode();
        }
      }
      if (selectedMessages != null && selectedMessages.size() > 0 && !inSelectMode) {
        adapter.clearSelectedItems();
        selectedMessages.clear();
      }
      adapter.setInSelectMode(inSelectMode, needSelectableAnimation(), this);
      return true;
    }
    return false;
  }

  @Override
  public final void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (parent != null) {
      parent.setSelectFactor(factor);
    }
  }

  @Override
  public final void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  public boolean isInMediaSelectMode () {
    return inSelectMode;
  }

  protected final void toggleSelected (ListItem item) {
    final long messageId = item.getLongId();

    //noinspection unchecked
    final T data = (T) item.getData();

    if (data == null || messageId == 0 || (item.getViewType() != ListItem.TYPE_SMALL_MEDIA && item.getViewType() != ListItem.TYPE_CUSTOM_INLINE)) {
      return;
    }

    TdApi.Message message = data.getMessage();
    final boolean isSelected;
    final String key = message.chatId + "_" + message.id;
    if (selectedMessages == null) {
      isSelected = false;
      selectedMessages = new HashMap<>();
    } else {
      isSelected = selectedMessages.containsKey(key);
    }
    if (isSelected) {
      selectedMessages.remove(key);
    } else {
      selectedMessages.put(key, data.getMessage());
    }
    if (setInMediaSelectMode(selectedMessages.size() > 0)) {
      if (inSelectMode) {
        if (parent != null) {
          parent.updateItemsAbility(canCopyMessages(), canDeleteMessages(), canShareMessages(), canClearMessages(), selectedMessages.size() == 1);
        } else if (alternateParent != null) {
          alternateParent.updateSelectButtons();
        }
      }
    } else {
      if (parent != null) {
        parent.setSelectedMediaCount(getSelectedMediaCount(), getSelectedMediaSuffixRes());
        parent.updateItemsAbility(canCopyMessages(), canDeleteMessages(), canShareMessages(), canClearMessages(), selectedMessages.size() == 1);
      } else if (alternateParent != null) {
        alternateParent.setSelectedCount(selectedMessages.size());
        alternateParent.updateSelectButtons();
      }
    }
    item.setSelected(!isSelected);

    int i = adapter.indexOfViewByLongId(messageId);
    if (i != -1) {
      adapter.setIsSelected(i, !isSelected, -1);
    }
  }

  protected boolean supportsMessageClearing () {
    return true;
  }

  public boolean canShareMessages () {
    if (selectedMessages != null && !selectedMessages.isEmpty()) {
      for (TdApi.Message message : selectedMessages.values()) {
        if (!message.canBeForwarded)
          return false;
      }
      return true;
    }
    return false;
  }

  public boolean canDeleteMessages () {
    if (selectedMessages != null && !selectedMessages.isEmpty()) {
      for (TdApi.Message message : selectedMessages.values()) {
        if (!message.canBeDeletedOnlyForSelf && !message.canBeDeletedForAllUsers)
          return false;
      }
      return true;
    }
    return false;
  }

  public void deleteMessages () {
    if (canDeleteMessages()) {
      tdlib.ui().showDeleteOptions(this, selectedMessages.values().toArray(new TdApi.Message[0]), () -> setInMediaSelectMode(false));
    }
  }

  public boolean canCopyMessages () {
    return selectedMessages != null && selectedMessages.size() == 1 && selectedMessages.values().iterator().next().canBeSaved && provideSearchFilter().getConstructor() == TdApi.SearchMessagesFilterUrl.CONSTRUCTOR;
  }

  public void copyMessages () {
    if (selectedMessages != null && selectedMessages.size() == 1) {
      for (TdApi.Message message : selectedMessages.values()) {
        TdApi.FormattedText text = Td.textOrCaption(message.content);
        String link = TD.findLink(text);
        if (!StringUtils.isEmpty(link)) {
          UI.copyText(link, R.string.CopiedLink);
          setInMediaSelectMode(false);
        }
      }
    }
  }

  public boolean canClearMessages () {
    if (selectedMessages != null && supportsMessageClearing()) {
      if (!selectedMessages.isEmpty()) {
        for (TdApi.Message message : selectedMessages.values()) {
          TdApi.File file = TD.getFile(message);
          if (file == null || !file.local.canBeDeleted || file.local.downloadedSize == 0) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  public void clearMessages () {
    if (canClearMessages()) {
      final SparseArrayCompat<TdApi.File> files = new SparseArrayCompat<>(selectedMessages.size());
      for (TdApi.Message message : selectedMessages.values()) {
        TdApi.File file = TD.getFile(message);
        if (file != null && file.local.canBeDeleted && file.local.downloadedSize > 0) {
          files.put(file.id, file);
        }
      }
      TD.deleteFiles(this, ArrayUtils.asArray(files, new TdApi.File[files.size()]), () -> setInMediaSelectMode(false));
    }
  }

  @Nullable
  public MessageId getSingularMessageId () {
    if (selectedMessages != null && selectedMessages.size() == 1) {
      for (TdApi.Message message : selectedMessages.values()) {
        return new MessageId(message.chatId, message.id);
      }
    }
    return null;
  }

  public void viewMessages () {
    MessageId messageId = getSingularMessageId();
    if (messageId != null) {
      tdlib.ui().openChat(this, getChatId(), new TdlibUi.ChatOpenParameters().passcodeUnlocked().highlightMessage(messageId).ensureHighlightAvailable());
    }
  }

  public void shareMessages () {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat != null && selectedMessages != null && selectedMessages.size() > 0) {
      ShareController c = new ShareController(context, tdlib);
      TdApi.Message[] messages = selectedMessages.values().toArray(new TdApi.Message[0]);
      Arrays.sort(messages, (a, b) -> Long.compare(a.id, b.id));
      c.setArguments(new ShareController.Args(messages).setAfter(() -> {
        if (parent != null) {
          parent.clearSelectMode();
        }
      }).setAllowCopyLink(true));
      c.show();
    }
  }

  // Messages

  private int findBestIndexForId (long messageId) {
    if (data == null) {
      return -1;
    }
    if (data.isEmpty()) {
      return 0;
    }
    int i = 0;
    for (T item : data) {
      if (messageId > item.getSourceMessageId()) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public void addMessage (TdApi.Message message) {
    if (!ProfileController.filterMediaMessage(message) || chatId != message.chatId || !supportsMessageContent()) {
      return;
    }

    TdApi.SearchMessagesFilter filter = provideSearchFilter();

    if (filter == null || !Td.matchesFilter(message, filter)) {
      return;
    }

    if (data == null) {
      return;
    }

    final int alreadyFoundIndex = indexOfMessage(message.id);
    if (alreadyFoundIndex != -1) {
      return;
    }

    final T addedItem = parseObject(message);
    if (addedItem == null) {
      return;
    }

    final int bestIndex = findBestIndexForId(message.id);
    if (bestIndex == -1) {
      return;
    }

    if (isSearching()) {
      data.add(bestIndex, addedItem);
      return;
    }

    final T nextItem = bestIndex < data.size() ? data.get(bestIndex) : null;
    final T previousItem = bestIndex > 0 ? data.get(bestIndex - 1) : null;

    final int addedDate = addedItem.getSourceDate();
    final int addedAnchorMode = TD.getAnchorMode(addedDate);

    final boolean needAddGroup =
      (previousItem == null || TD.getAnchorMode(previousItem.getSourceDate()) != addedAnchorMode || TD.shouldSplitDatesByMonth(addedAnchorMode, previousItem.getSourceDate(), addedDate)) &&
      (nextItem == null || TD.getAnchorMode(nextItem.getSourceDate()) != addedAnchorMode || TD.shouldSplitDatesByMonth(addedAnchorMode, addedDate, nextItem.getSourceDate()));


    if (data.isEmpty()) {
      data.add(bestIndex, addedItem);
      buildCells();
      return;
    }

    final List<ListItem> items = adapter.getItems();
    final ListItem itemToInsert = new ListItem(provideViewType()).setData(addedItem).setLongId(addedItem.getSourceMessageId());
    if (previousItem != null) {
      int previousItemIndex = adapter.indexOfViewByLongId(previousItem.getSourceMessageId());
      if (previousItemIndex == -1) {
        return;
      }

      data.add(bestIndex, addedItem);

      if (needAddGroup) {
        previousItemIndex++; // skipping shadow_bottom of the previous section

        items.add(previousItemIndex, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(previousItemIndex, itemToInsert);
        items.add(previousItemIndex, new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(previousItemIndex, new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.getRelativeMonth(addedDate, TimeUnit.SECONDS, true), false));

        adapter.notifyItemRangeInserted(previousItemIndex, 4);
      } else {
        previousItemIndex++; // skipping previous item

        items.add(previousItemIndex, itemToInsert);
        adapter.notifyItemInserted(previousItemIndex);
      }

      onItemsHeightProbablyChanged();
    } else if (nextItem != null) {
      int nextItemIndex = adapter.indexOfViewByLongId(nextItem.getSourceMessageId());
      if (nextItemIndex == -1) {
        return;
      }

      if (needAddGroup) {
        nextItemIndex--; // shadow_top
        nextItemIndex--; // header

        if (nextItemIndex < 0) {
          return;
        }

        data.add(bestIndex, addedItem);

        items.add(nextItemIndex, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(nextItemIndex, itemToInsert);
        items.add(nextItemIndex, new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(nextItemIndex, new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.getRelativeMonth(addedDate, TimeUnit.SECONDS, true), false));

        adapter.notifyItemRangeInserted(nextItemIndex, 4);
      } else {
        data.add(bestIndex, addedItem);
        items.add(nextItemIndex, itemToInsert);
        adapter.notifyItemInserted(nextItemIndex);
      }

      onItemsHeightProbablyChanged();
    }
  }

  public final void removeMessages (long[] messageIds) {
    if (supportsMessageContent()) {
      for (long messageId : messageIds) {
        removeMessage(messageId);
      }
    }
  }

  private int indexOfMessage (long messageId) {
    if (data == null || !supportsMessageContent()) {
      return -1;
    }
    int foundDataIndex = -1;
    int i = 0;
    for (T item : data) {
      if (item.getSourceMessageId() == messageId) {
        foundDataIndex = i;
        break;
      }
      i++;
    }
    return foundDataIndex;
  }


  private void removeMessage (long messageId) {
    if (data == null || !supportsMessageContent()) {
      return;
    }

    int foundDataIndex = indexOfMessage(messageId);
    if (foundDataIndex == -1) {
      return;
    }

    if (isSearching()) {
      data.remove(foundDataIndex);
      return;
    }

    final T removingItem = data.get(foundDataIndex); // Will remove later
    final T nextItem = foundDataIndex + 1 < data.size() ? data.get(foundDataIndex + 1) : null;
    final T previousItem = foundDataIndex > 0 ? data.get(foundDataIndex - 1) : null;

    final int removedDate = removingItem.getSourceDate();
    final int removedAnchorMode = TD.getAnchorMode(removedDate);

    final boolean needRemoveGroup =
      (previousItem == null || TD.getAnchorMode(previousItem.getSourceDate()) != removedAnchorMode || TD.shouldSplitDatesByMonth(removedAnchorMode, previousItem.getSourceDate(), removedDate)) &&
      (nextItem == null || TD.getAnchorMode(nextItem.getSourceDate()) != removedAnchorMode || TD.shouldSplitDatesByMonth(removedAnchorMode, removedDate, nextItem.getSourceDate()));

    final int itemIndex = adapter.indexOfViewByLongId(messageId);
    if (itemIndex == -1) {
      return;
    }

    data.remove(foundDataIndex);

    final List<ListItem> items = adapter.getItems();

    if (data.isEmpty()) {
      buildCells();
    } else if (needRemoveGroup) {
      items.remove(itemIndex + 1); // shadow_bottom
      items.remove(itemIndex); // item
      items.remove(itemIndex - 1); // shadow_top
      items.remove(itemIndex - 2); // header
      adapter.notifyItemRangeRemoved(itemIndex - 2, 4);
      adapter.updateValuedSettingById(R.id.search_counter);
      onItemsHeightProbablyChanged();
    } else {
      items.remove(itemIndex);
      adapter.notifyItemRemoved(itemIndex);
      adapter.updateValuedSettingById(R.id.search_counter);
      onItemsHeightProbablyChanged();
    }
  }

  public final void editMessage (long messageId, TdApi.MessageContent content) {
    if (data == null || !supportsMessageContent()) {
      return;
    }

    int index = indexOfMessage(messageId);
    if (index != -1) {
      data.get(index).getMessage().content = content;
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (alternateParent != null) {
      tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
    }
    TGLegacyManager.instance().removeEmojiListener(adapter);
    Views.destroyRecyclerView(recyclerView);
  }

  // API

  protected boolean needSelectableAnimation () {
    return false;
  }

  protected boolean canSearch () {
    return true;
  }

  protected boolean needsDefaultOnClick () {
    return true;
  }

  protected boolean needsDefaultLongPress () {
    return true;
  }

  protected boolean probablyHasEmoji () {
    return false;
  }

  protected String getExplainedTitle () {
    throw new RuntimeException("Stub!");
  }

  protected abstract boolean supportsMessageContent ();

  protected boolean needDateSectionSplitting () {
    return true;
  }

  protected boolean supportsLoadingMore (boolean isMore) {
    return true;
  }

  protected void modifyResultIfNeeded (ArrayList<T> data, boolean preparation) {
    // override
  }

  protected boolean needsCustomLongClickListener () {
    return false;
  }

  protected Comparator<T> provideItemComparator () {
    return null;
  }

  protected void modifyChatViewIfNeeded (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
    // override
  }

  protected TdApi.SearchMessagesFilter provideSearchFilter () {
    throw new RuntimeException("Stub!");
  }

  // Independent updates handler

  @Override
  public final void onNewMessage (final TdApi.Message message) {
    if (ProfileController.filterMediaMessage(message)) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          addMessage(message);
        }
      });
    }
  }

  /*@Override
  public final void __onNewMessages (final TdApi.Message[] messages) {
    boolean found = false;
    for (TdApi.Message message : messages) {
      if (filter(message)) {
        found = true;
        break;
      }
    }
    if (found) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          for (TdApi.Message message : messages) {
            if (filter(message)) {
              addMessage(message);
            }
          }
        }
      });
    }
  }*/

  @Override
  public final void onMessageSendSucceeded (final TdApi.Message message, final long oldMessageId) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        addMessage(message);
      }
    });
  }

  @Override
  public final void onMessageContentChanged (final long chatId, final long messageId, final TdApi.MessageContent newContent) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && SharedBaseController.this.chatId == chatId) {
        editMessage(messageId, newContent);
      }
    });
  }

  @Override
  public final void onMessagesDeleted (final long chatId, final long[] messageIds) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && SharedBaseController.this.chatId == chatId) {
        removeMessages(messageIds);
      }
    });
  }

  // Language


  @Override
  protected void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (adapter != null) {
      adapter.onLanguagePackEvent(event, arg1);
    }
  }
}
