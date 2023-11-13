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
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGFoundMessage;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.VerticalChatView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;

public abstract class TelegramViewController<T> extends ViewController<T> {
  public TelegramViewController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  // Search chats

  private CustomRecyclerView chatSearchView;
  private TdlibMessageViewer.Viewport chatSearchViewport;
  private SettingsAdapter chatSearchAdapter;
  private SearchManager chatSearchManager;
  private boolean chatSearchDisallowScreenshots;

  /**
   * @return true, if event is consumed. False, if chat should be handled with default action (open)
   * */
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    return false;
  }

  protected boolean canSelectFoundChat (TGFoundChat chat) {
    return false;
  }

  protected boolean canInteractWithFoundChat (TGFoundChat chat) {
    return true;
  }

  protected static ListItem searchValueOf (@IdRes int id, TGFoundChat chat, boolean canClear) {
    return new ListItem(ListItem.TYPE_CHAT_BETTER, id).setData(chat).setLongId(chat.getId()).setBoolValue(canClear);
  }

  private static ListItem searchValueOf (TGFoundMessage message) {
    return new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.search_message).setData(message).setLongId(message.getId());
  }

  protected TdApi.ChatList getChatMessagesSearchChatList () {
    return null;
  }

  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_TOP_CHATS;
  }

  protected boolean filterChatSearchResult (TdApi.Chat chat) {
    return true;
  }

  protected boolean filterChatMessageSearchResult (TdApi.Chat chat) {
    return true;
  }

  protected void modifyFoundChat (TGFoundChat chat) { }

  protected void modifyFoundChatView (ListItem item, int position, BetterChatView chatView) { }

  protected boolean needChatSearchManagerPreparation () {
    // Disable if it's not needed
    return true;
  }

  private static final boolean DEBUG_CHATS_SEARCH_ADAPTER = false;

  private void removeSuggestedChat (final TGFoundChat chat) {
    showOptions(Lang.getStringBold(R.string.ChatHintsDelete, chat.getTitle()), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_sweep_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_delete) {
        chatSearchManager.removeTopChat(chat.getId());
      }
      return true;
    });
  }

  private void removeRecentChat (final TGFoundChat chat) {
    showOptions(Lang.getStringBold(R.string.DeleteXFromRecents, chat.getTitle()), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_delete) {
        chatSearchManager.removeRecentlyFoundChat(chat);
      }
      return true;
    });
  }

  protected final CustomRecyclerView generateChatSearchView (@Nullable ViewGroup parent) {
    final boolean noChatSearch = (getChatSearchFlags() & SearchManager.FLAG_NO_CHATS) != 0;
    chatSearchViewport = tdlib.messageViewer().createViewport(new TdApi.MessageSourceSearch(), this);
    chatSearchViewport.addIgnoreLock(() -> !this.isSearchContentVisible);
    chatSearchView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, parent);
    Views.setScrollBarPosition(chatSearchView);
    tdlib.ui().attachViewportToRecyclerView(chatSearchViewport, chatSearchView);
    chatSearchView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          hideSoftwareKeyboard();
        }
      }

      private int firstVisiblePosition = -1, lastVisiblePosition = -1;

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy != 0) {
          /*
           * (((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition() > 0) || ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition() < recyclerView.getAdapter().getItemCount() - 1
           * */
          hideSoftwareKeyboard();
        }
        int first = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        int last = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
        if (this.firstVisiblePosition != first || this.lastVisiblePosition != last) {
          this.firstVisiblePosition = first;
          this.lastVisiblePosition = last;

          boolean shouldDisallowScreenshots = false;
          for (int i = first; i <= last; i++) {
            ListItem item = chatSearchAdapter.getItem(i);
            if (item != null && item.getData() instanceof TGFoundMessage) {
              TdApi.Message message = ((TGFoundMessage) item.getData()).getMessage();
              if (!message.canBeSaved) {
                shouldDisallowScreenshots = true;
                break;
              }
            }
          }
          if (chatSearchDisallowScreenshots != shouldDisallowScreenshots) {
            chatSearchDisallowScreenshots = shouldDisallowScreenshots;
            context().checkDisallowScreenshots();
          }
        }
        if (last + 5 >= chatSearchAdapter.getItems().size()) {
          chatSearchManager.loadMoreMessages();
        }
      }
    });
    chatSearchView.setBackgroundColor(Theme.backgroundColor());
    addThemeBackgroundColorListener(chatSearchView, ColorId.background);
    chatSearchView.setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
    if (parent != null) {
      chatSearchView.setAlpha(0f);
      chatSearchView.setScrollDisabled(true);
    } else {
      isSearchContentVisible = true;
    }
    chatSearchView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final BaseView.ActionListProvider previewProvider = (v, context, ids, icons, strings, target) -> {
      final ListItem item = (ListItem) v.getTag();

      final Object data = item.getData();
      final TGFoundChat chat;
      if (data instanceof TGFoundMessage) {
        chat = ((TGFoundMessage) data).getChat();
      } else {
        chat = (TGFoundChat) data;
      }
      boolean canInteractWithFoundChat = canInteractWithFoundChat(chat) && navigationController != null;
      if (navigationController == null) {
        context.setExcludeHeader(true);
      }

      final int itemId = item.getId();
      if (itemId == R.id.search_chat_local || itemId == R.id.search_chat_top) {
        if (Config.CALL_FROM_PREVIEW && canInteractWithFoundChat && tdlib.cache().userGeneral(chat.getUserId())) {
          ids.append(R.id.btn_phone_call);
          strings.append(R.string.Call);
          icons.append(R.drawable.baseline_call_24);
        }
        boolean chatAvailable = tdlib.chatAvailable(chat.getChat());
        if (chatAvailable) {
          ids.append(R.id.btn_notifications);
          boolean hasNotifications = tdlib.chatNotificationsEnabled(chat.getId());
          strings.append(hasNotifications ? R.string.Mute : R.string.Unmute);
          icons.append(hasNotifications ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
          if (canInteractWithFoundChat) {
            if (chat.getList() != null) {
              boolean isPinned = tdlib.chatPinned(chat.getList(), chat.getId());
              ids.append(R.id.btn_pinUnpinChat);
              strings.append(isPinned ? R.string.Unpin : R.string.Pin);
              icons.append(isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
              if (tdlib.canArchiveChat(chat.getList(), chat.getChat())) {
                boolean isArchived = tdlib.chatArchived(chat.getId());
                ids.append(R.id.btn_archiveUnarchiveChat);
                strings.append(isArchived ? R.string.Unarchive : R.string.Archive);
                icons.append(isArchived ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24);
              }
            }
            boolean canRead = tdlib.canMarkAsRead(chat.getChat());
            ids.append(canRead ? R.id.btn_markChatAsRead : R.id.btn_markChatAsUnread);
            strings.append(canRead ? R.string.MarkAsRead : R.string.MarkAsUnread);
            icons.append(canRead ? Config.ICON_MARK_AS_READ : Config.ICON_MARK_AS_UNREAD);
            if (chat.hasHighlight()) {
              ids.append(R.id.btn_removeChatFromListOrClearHistory);
              strings.append(R.string.Delete);
              icons.append(R.drawable.baseline_delete_24);
            }
          }
        }
        if (!chat.hasHighlight()) {
          ids.append(R.id.btn_delete);
          strings.append(R.string.Remove);
          icons.append(R.drawable.baseline_delete_sweep_24);
        }
      } else if (itemId == R.id.search_chat_global) {// Nothing?
      }

      if (canSelectFoundChat(chat)) {
        ids.append(R.id.btn_selectChat);
        strings.append(R.string.Select);
        icons.append(R.drawable.baseline_playlist_add_check_24);
      }

      return new ForceTouchView.ActionListener() {
        @Override
        public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
          if (actionId == R.id.btn_phone_call) {
            tdlib.context().calls().makeCallDelayed(TelegramViewController.this, chat.getUserId(), null, true);
          } else if (actionId == R.id.btn_pinUnpinChat || actionId == R.id.btn_archiveUnarchiveChat || actionId == R.id.btn_notifications || actionId == R.id.btn_markChatAsRead || actionId == R.id.btn_markChatAsUnread || actionId == R.id.btn_removeChatFromListOrClearHistory || actionId == R.id.btn_removePsaChatFromList) {
            tdlib.ui().processChatAction(TelegramViewController.this, chat.getList(), chat.getChatId(), chat.getMessageThread(), new TdApi.MessageSourceSearch(), actionId, null);
          } else if (actionId == R.id.btn_selectChat) {
            onFoundChatClick(v, chat);
          } else if (actionId == R.id.btn_delete) {
            final int itemId = item.getId();
            if (itemId == R.id.search_chat_top) {
              removeSuggestedChat(chat);
            } else if (itemId == R.id.search_chat_local) {
              removeRecentChat(chat);
            }
          }
        }

        @Override
        public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

        }
      };
    };

    final SettingsAdapter topChatsAdapter = new SettingsAdapter(this, v -> {
      ListItem item = (ListItem) v.getTag();
      final int itemId = item.getId();
      if (itemId == R.id.search_chat_top) {
        final TGFoundChat chat = (TGFoundChat) item.getData();
        if (chat.getId() != 0 && !onFoundChatClick(v, chat)) {
          tdlib.ui().openChat(TelegramViewController.this, chat.getId(), null);
        }
      }
    }, this) {
      @Override
      protected void setChatData (ListItem item, VerticalChatView chatView) {
        chatView.setPreviewActionListProvider(previewProvider);
        chatView.setChat((TGFoundChat) item.getData());
      }
    };
    topChatsAdapter.setOnLongClickListener(v -> {
      final ListItem item = (ListItem) v.getTag();
      int itemId = item.getId();
      if (itemId == R.id.search_chat_top) {
        final TGFoundChat chat = (TGFoundChat) item.getData();
        removeSuggestedChat(chat);
        return true;
      }
      return false;
    });

    chatSearchAdapter = new SettingsAdapter(this, v -> {
      ListItem listItem = (ListItem) v.getTag();
      int listItemId = listItem.getId();
      if (listItemId == R.id.search_section_local) {
        if (!chatSearchManager.areLocalChatsRecent()) {
          return;
        }
        showOptions(Lang.getString(R.string.ClearRecentsHint), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Clear), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_delete) {
            chatSearchManager.clearRecentlyFoundChats();
          }
          return true;
        });
      } else if (listItemId == R.id.search_chat_local || listItemId == R.id.search_chat_global) {
        final TGFoundChat chat = (TGFoundChat) listItem.getData();
        if (listItem.getId() == R.id.search_chat_global) {
          preventLeavingSearchMode();
        }
        if (chat.getId() != 0) {
          chatSearchManager.addRecentlyFoundChat(chat);
          if (!onFoundChatClick(v, chat)) {
            tdlib.ui().openChat(TelegramViewController.this, chat.getId(), null);
          }
        } else if (chat.getUserId() != 0) {
          tdlib.ui().openPrivateChat(TelegramViewController.this, chat.getUserId(), new TdlibUi.ChatOpenParameters().noOpen().after(createdChatId -> {
            chat.setCreatedChatId(createdChatId);
            chatSearchManager.addRecentlyFoundChat(chat);
            if (!onFoundChatClick(v, chat)) {
              tdlib.ui().openChat(TelegramViewController.this, createdChatId, null);
            }
          }));
        }
      } else if (listItemId == R.id.search_message) {
        TGFoundMessage message = (TGFoundMessage) listItem.getData();
        TdApi.Message rawMessage = message.getMessage();
        preventLeavingSearchMode();
        String query = getLastSearchInput();
        tdlib.ui().openChat(TelegramViewController.this, rawMessage.chatId, new TdlibUi.ChatOpenParameters().foundMessage(query, rawMessage).keepStack());
      }
    }, this) {
      @Override
      public void onEmojiUpdated (boolean isPackSwitch) {
        for (RecyclerView parentView : parentViews) {
          LinearLayoutManager manager = (LinearLayoutManager) parentView.getLayoutManager();
          final int first = manager.findFirstVisibleItemPosition();
          final int last = manager.findLastVisibleItemPosition();
          for (int i = first; i <= last; i++) {
            View view = manager.findViewByPosition(i);
            if (view instanceof BetterChatView) {
              view.invalidate();
            }
          }
          if (first > 0) {
            notifyItemRangeChanged(0, first);
          }
          if (last < getItemCount() - 1) {
            notifyItemRangeChanged(last, getItemCount() - last);
          }
        }
      }

      @Override
      protected void setRecyclerViewData(ListItem item, RecyclerView recyclerView, boolean isInitialization) {
        int itemId = item.getId();
        if (itemId == R.id.search_top) {
          if (recyclerView.getAdapter() != topChatsAdapter) {
            recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
            recyclerView.setAdapter(topChatsAdapter);
          }
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setPreviewActionListProvider(previewProvider);
        int itemId = item.getId();
        if (itemId == R.id.search_chat_local || itemId == R.id.search_chat_global) {
          chatView.setChat((TGFoundChat) item.getData());
        } else if (itemId == R.id.search_message) {
          chatView.setMessage((TGFoundMessage) item.getData());
        }
        TelegramViewController.this.modifyFoundChatView(item, position, chatView);
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        if (chatSearchManager.isEndReached()) {
          infoView.showInfo(Lang.pluralBold(R.string.xMessages, chatSearchManager.getFoundMessagesCount()));
        } else {
          infoView.showProgress();
        }
      }
    };
    TGLegacyManager.instance().addEmojiListener(chatSearchAdapter);
    chatSearchAdapter.setOnLongClickListener(v -> {
      final int chatViewId = v.getId();
      if (chatViewId == R.id.search_chat_local) {
        ListItem item = (ListItem) v.getTag();
        if (!item.getBoolValue()) {
          return false;
        }
        final TGFoundChat chat = (TGFoundChat) item.getData();
        showOptions(Lang.getStringBold(R.string.DeleteXFromRecents, chat.getTitle()), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_delete) {
            chatSearchManager.removeRecentlyFoundChat(chat);
          }
          return true;
        });
        return true;
      }
      return false;
    });
    if (!noChatSearch) {
      chatSearchAdapter.setItems(new ListItem[]{new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL)}, false);
    }

    final RecyclerView.ItemAnimator chatSearchViewAnimator = chatSearchView.getItemAnimator();
    if (!DEBUG_CHATS_SEARCH_ADAPTER) {
      if (!noChatSearch) {
        chatSearchView.setItemAnimator(null);
      }
    }
    if (parent == null) {
      chatSearchView.setAdapter(chatSearchAdapter);
    }

    chatSearchManager = new SearchManager(tdlib, new SearchManager.Listener() {
      @Override
      public void onOpen () {
        setNeedPreventKeyboardLag(true);
        if (chatSearchView.getAdapter() != null) {
          chatSearchAdapter.resetRecyclerScrollById(R.id.search_top);
          ((LinearLayoutManager) chatSearchView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        }
      }

      @Override
      public boolean customFilter (TdApi.Chat chat) {
        return filterChatSearchResult(chat);
      }

      @Override
      public boolean filterMessageSearchResultSource (TdApi.Chat chat) {
        return TelegramViewController.this.filterChatMessageSearchResult(chat);
      }

      @Override
      public void modifyFoundChat (TGFoundChat foundChat) {
        TelegramViewController.this.modifyFoundChat(foundChat);
      }

      @Override
      public void onClose () {
        setNeedPreventKeyboardLag(false);
        if (chatSearchView.getAdapter() != null) {
          Views.destroyRecyclerView(chatSearchView);
          chatSearchView.setAdapter(null);
        }
      }

      @Override
      public void onPerformNewSearch (boolean isDefault) {
        if (!DEBUG_CHATS_SEARCH_ADAPTER) {
          if (!noChatSearch) {
            chatSearchView.setItemAnimator(null);
          }
        }
      }

      private void updateTopChatsAdapter (ArrayList<TGFoundChat> topChats) {
        ArrayList<ListItem> chatItems = new ArrayList<>(topChats.size());
        for (TGFoundChat chat : topChats) {
          chatItems.add(new ListItem(ListItem.TYPE_CHAT_VERTICAL, R.id.search_chat_top).setData(chat).setLongId(chat.getId()));
        }
        topChatsAdapter.replaceItems(chatItems);
      }

      @Override
      public boolean onAddTopChats (ArrayList<TGFoundChat> topChats, boolean updateData, boolean isSilent) {
        if (updateData) {
          updateTopChatsAdapter(topChats);
        }
        if (!isSilent && chatSearchAdapter.indexOfViewById(R.id.search_section_top) == -1) {
          int startIndex = 1; // chatSearchAdapter.getItems().size();
          int addedCount = generateTopCells(chatSearchAdapter.getItems(), startIndex);
          chatSearchAdapter.notifyItemRangeInserted(startIndex, addedCount);
          return true;
        }
        return false;
      }

      @Override
      public void onRemoveTopChats (boolean updateData, boolean isSilent) {
        if (updateData) {
          topChatsAdapter.replaceItems(null);
        }
        if (!isSilent) {
          int i = chatSearchAdapter.indexOfViewById(R.id.search_section_top);
          if (i != -1) {
            chatSearchAdapter.removeRange(i, 4);
          }
        }
      }

      @Override
      public void onRemoveTopChat (long chatId) {
        topChatsAdapter.removeItemByLongId(chatId);
      }

      @Override
      public void onUpdateTopChats (long[] oldChatIds, long[] newChatIds) {
        updateTopChatsAdapter(chatSearchManager.getTopChats());
      }

      @Override
      public void onAddLocalChats (ArrayList<TGFoundChat> localChats) {
        if (chatSearchAdapter.indexOfViewById(R.id.search_section_local) == -1) {
          int startIndex = chatSearchAdapter.indexOfViewById(R.id.search_section_messages);
          if (startIndex == -1) {
            startIndex = chatSearchAdapter.indexOfViewById(R.id.search_section_global);
            if (startIndex == -1) {
              startIndex = chatSearchAdapter.getItems().size();
            }
          }
          int addedCount = generateChatsCells(startIndex, chatSearchAdapter.getItems(), localChats, R.id.search_section_local, R.id.search_chat_local, chatSearchManager.areLocalChatsRecent() ? R.string.Recent : R.string.ChatsAndContacts, chatSearchManager.areLocalChatsRecent());
          chatSearchAdapter.notifyItemRangeInserted(startIndex, addedCount);
        }
      }

      @Override
      public void onAddMoreLocalChats (ArrayList<TGFoundChat> addedLocalChats, int oldChatCount) {
        int startIndex = chatSearchAdapter.indexOfViewById(R.id.search_section_local);
        if (startIndex != -1) {
          startIndex = startIndex + oldChatCount * 2 + 1;
          int addedCount = 0;
          List<ListItem> items = chatSearchAdapter.getItems();
          ArrayUtils.ensureCapacity(items, items.size() + addedLocalChats.size() * 2);
          for (TGFoundChat chat : addedLocalChats) {
            items.add(startIndex + addedCount++, new ListItem(ListItem.TYPE_SEPARATOR));
            items.add(startIndex + addedCount++, searchValueOf(R.id.search_chat_local, chat, chatSearchManager.areLocalChatsRecent()));
          }
          chatSearchAdapter.notifyItemRangeInserted(startIndex, addedCount);
        }
      }

      @Override
      public void onRemoveLocalChats (int oldChatCount) {
        int i = chatSearchAdapter.indexOfViewById(R.id.search_section_local);
        if (i != -1) {
          chatSearchAdapter.removeRange(i, 3 + (oldChatCount - 1) * 2 + 1);
        }
      }

      @Override
      public void onUpdateLocalChats (final int oldChatCount, ArrayList<TGFoundChat> localChats) {
        int startIndex = chatSearchAdapter.indexOfViewById(R.id.search_section_local);
        if (startIndex == -1) {
          return;
        }

        ListItem headerItem = chatSearchAdapter.getItems().get(startIndex);
        final boolean canClear = chatSearchManager.areLocalChatsRecent();
        if (canClear != (headerItem.getViewType() == ListItem.TYPE_HEADER_WITH_ACTION)) {
          headerItem.setViewType(canClear ? ListItem.TYPE_HEADER_WITH_ACTION : ListItem.TYPE_HEADER);
          headerItem.setString(canClear ? R.string.Recent : R.string.ChatsAndContacts);
          chatSearchAdapter.notifyItemChanged(startIndex);
        }

        startIndex += 2;

        final int newChatCount = localChats.size();
        final int changedChatCount = Math.min(oldChatCount, newChatCount);
        for (int i = 0; i < changedChatCount; i++) {
          final int position = startIndex + i * 2;
          ListItem item = chatSearchAdapter.getItems().get(position);
          if (item.getViewType() != ListItem.TYPE_CHAT_BETTER) {
            throw new IllegalStateException("Bug, viewType: " + item.getViewType());
          }
          TGFoundChat chat = localChats.get(i);
          item.setData(chat).setLongId(chat.getId()).setBoolValue(canClear);
        }
        chatSearchAdapter.notifyItemRangeChanged(startIndex, 1 + (changedChatCount - 1) * 2);

        startIndex += 1 + (changedChatCount - 1) * 2;

        if (newChatCount > oldChatCount) {
          int index = startIndex;
          final int addedChatCount = newChatCount - oldChatCount;
          for (int i = 0; i < addedChatCount; i++) {
            TGFoundChat chat = localChats.get(i + changedChatCount);
            chatSearchAdapter.getItems().add(index++, new ListItem(ListItem.TYPE_SEPARATOR));
            chatSearchAdapter.getItems().add(index++, searchValueOf(R.id.search_chat_local, chat, canClear));
          }
          chatSearchAdapter.notifyItemRangeInserted(startIndex, index - startIndex);
        } else if (newChatCount < oldChatCount) {
          final int removedChatCount = oldChatCount - newChatCount;
          chatSearchAdapter.removeRange(startIndex, removedChatCount * 2);
        }
      }

      @Override
      public void onRemoveLocalChat (long chatId, int position, int totalCount) {
        int i = chatSearchAdapter.indexOfViewById(R.id.search_section_local);
        if (i != -1) {
          i += 2;
          if (position == 0) {
            chatSearchAdapter.removeRange(i, 2);
          } else {
            chatSearchAdapter.removeRange(i + position * 2 - 1, 2);
          }
        }
      }

      @Override
      public void onAddLocalChat (TGFoundChat chat) {
        int i = chatSearchAdapter.indexOfViewById(R.id.search_section_local);
        if (i != -1) {
          i += 2;
          chatSearchAdapter.getItems().add(i, new ListItem(ListItem.TYPE_SEPARATOR));
          chatSearchAdapter.getItems().add(i, searchValueOf(R.id.search_chat_local, chat, true));
          chatSearchAdapter.notifyItemRangeInserted(i, 2);
        }
      }

      @Override
      public void onMoveLocalChat (TGFoundChat chat, int fromPosition, int totalCount) {
        int i = chatSearchAdapter.indexOfViewById(R.id.search_section_local);
        if (i != -1) {
          i += 2;
          int adapterPosition = i + fromPosition * 2;
          ListItem chatItem = chatSearchAdapter.getItems().remove(adapterPosition);
          ListItem shadowItem;
          if (fromPosition != totalCount - 1) {
            shadowItem = chatSearchAdapter.getItems().remove(adapterPosition);
            chatSearchAdapter.notifyItemRangeRemoved(adapterPosition, 2);
          } else {
            shadowItem = chatSearchAdapter.getItems().remove(adapterPosition - 1);
            chatSearchAdapter.notifyItemRangeRemoved(adapterPosition - 1, 2);
          }
          chatSearchAdapter.getItems().add(i, shadowItem);
          chatSearchAdapter.getItems().add(i, chatItem);
          chatSearchAdapter.notifyItemRangeInserted(i, 2);
        }
      }

      @Override
      public void onAddGlobalChats (ArrayList<TGFoundChat> globalChats) {
        if (chatSearchAdapter.indexOfViewById(R.id.search_section_global) == -1) {
          int startIndex = chatSearchAdapter.indexOfViewById(R.id.search_section_messages);
          if (startIndex == -1) {
            startIndex = chatSearchAdapter.getItems().size();
          }
          int addedCount = generateChatsCells(startIndex, chatSearchAdapter.getItems(), globalChats, R.id.search_section_global, R.id.search_chat_global, R.string.GlobalSearch, false);
          chatSearchAdapter.notifyItemRangeInserted(startIndex, addedCount);
        }
      }

      @Override
      public void onRemoveGlobalChats (int oldChatCount) {
        int startPosition = chatSearchAdapter.indexOfViewById(R.id.search_section_global);
        if (startPosition != -1) {
          chatSearchAdapter.removeRange(startPosition, 3 + (oldChatCount - 1) * 2 + 1);
        }
      }

      @Override
      public void onUpdateGlobalChats (int oldChatCount, ArrayList<TGFoundChat> globalChats) {
        int startIndex = chatSearchAdapter.indexOfViewById(R.id.search_section_global);
        if (startIndex == -1) {
          return;
        }

        startIndex += 2;

        final int newChatCount = globalChats.size();
        final int changedChatCount = Math.min(oldChatCount, newChatCount);
        for (int i = 0; i < changedChatCount; i++) {
          final int position = startIndex + i * 2;
          ListItem item = chatSearchAdapter.getItems().get(position);
          if (item.getViewType() != ListItem.TYPE_CHAT_BETTER) {
            throw new IllegalStateException("Bug, viewType: " + item.getViewType());
          }
          TGFoundChat chat = globalChats.get(i);
          item.setData(chat).setLongId(chat.getId()).setBoolValue(false);
        }
        chatSearchAdapter.notifyItemRangeChanged(startIndex, 1 + (changedChatCount - 1) * 2);

        startIndex += 1 + (changedChatCount - 1) * 2;

        if (newChatCount > oldChatCount) {
          int index = startIndex;
          final int addedChatCount = newChatCount - oldChatCount;
          for (int i = 0; i < addedChatCount; i++) {
            TGFoundChat chat = globalChats.get(i + changedChatCount);
            chatSearchAdapter.getItems().add(index++, new ListItem(ListItem.TYPE_SEPARATOR));
            chatSearchAdapter.getItems().add(index++, new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.search_chat_global).setData(chat).setLongId(chat.getId()));
          }
          chatSearchAdapter.notifyItemRangeInserted(startIndex, index - startIndex);
        } else if (newChatCount < oldChatCount) {
          final int removedChatCount = oldChatCount - newChatCount;
          chatSearchAdapter.removeRange(startIndex, removedChatCount * 2);
        }
      }

      @Override
      public void onAddMessages (ArrayList<TGFoundMessage> messages) {
        if (chatSearchAdapter.indexOfViewById(R.id.search_section_messages) == -1) {
          generateMessagesCells(chatSearchAdapter.getItems(), messages, 0, messages.size());
        }
      }

      @Override
      public void onUpdateMessages (int oldMessageCount, ArrayList<TGFoundMessage> messages) {
        int startIndex = chatSearchAdapter.indexOfViewById(R.id.search_section_messages);
        if (startIndex == -1) {
          return;
        }

        startIndex += 2;

        final int newMessageCount = messages.size();
        final int changedChatCount = Math.min(oldMessageCount, newMessageCount);
        for (int i = 0; i < changedChatCount; i++) {
          final int position = startIndex + i * 2;
          ListItem item = chatSearchAdapter.getItems().get(position);
          if (item.getViewType() != ListItem.TYPE_CHAT_BETTER) {
            throw new IllegalStateException("Bug, viewType: " + item.getViewType());
          }
          TGFoundMessage message = messages.get(i);
          item.setData(message).setLongId(message.getId()).setBoolValue(false);
        }
        chatSearchAdapter.notifyItemRangeChanged(startIndex, 1 + (changedChatCount - 1) * 2);

        startIndex += 1 + (changedChatCount - 1) * 2;

        if (newMessageCount > oldMessageCount) {
          int index = startIndex;
          final int addedChatCount = newMessageCount - oldMessageCount;
          for (int i = 0; i < addedChatCount; i++) {
            TGFoundMessage message = messages.get(i + changedChatCount);
            chatSearchAdapter.getItems().add(index++, new ListItem(ListItem.TYPE_SEPARATOR));
            chatSearchAdapter.getItems().add(index++, searchValueOf(message));
          }
          chatSearchAdapter.notifyItemRangeInserted(startIndex, index - startIndex);
        } else if (newMessageCount < oldMessageCount) {
          final int removedChatCount = oldMessageCount - newMessageCount;
          chatSearchAdapter.removeRange(startIndex, removedChatCount * 2);
        }
      }

      @Override
      public void onRemoveMessages (int oldMessageCount) {
        int startPosition = chatSearchAdapter.indexOfViewById(R.id.search_section_messages);
        if (startPosition != -1) {
          chatSearchAdapter.removeRange(startPosition, 4 + (oldMessageCount - 1) * 2 + 1);
        }
      }

      @Override
      public void onAddMoreMessages (int oldMessageCount, ArrayList<TGFoundMessage> messages) {
        int i = chatSearchAdapter.indexOfViewById(R.id.search_section_messages);
        if (i != -1) {
          final int startIndex = i + 2 + (oldMessageCount - 1) * 2 + 1;
          i = startIndex;
          for (int j = oldMessageCount; j < messages.size(); j++) {
            chatSearchAdapter.getItems().add(i++, new ListItem(ListItem.TYPE_SEPARATOR));
            chatSearchAdapter.getItems().add(i++, searchValueOf(messages.get(j)));
          }
          chatSearchAdapter.notifyItemRangeInserted(startIndex, i - startIndex);
        }
      }

      @Override
      public int getMessagesHeightOffset () {
        int totalHeight = 0;
        for (ListItem item : chatSearchAdapter.getItems()) {
          if (item.getId() == R.id.search_section_messages) {
            break;
          }
          totalHeight += SettingHolder.measureHeightForType(item.getViewType());
        }
        return totalHeight;
      }

      @Override
      public void onHeavyPartReached (final int currentContextId) {
        if (!DEBUG_CHATS_SEARCH_ADAPTER) {
          if (!noChatSearch) {
            tdlib.ui().post(() -> chatSearchView.setItemAnimator(chatSearchViewAnimator));
          }
        }
      }

      @Override
      public void onHeavyPartFinished (final int currentContextId) {
        /*TGDataManager.runOnUiThread(new Runnable() {
          @Override
          public void run () {
            if (currentContextId == chatSearchManager.getContextId()) {
              chatSearchView.setItemAnimator(null);
            }
          }
        }, 360);*/
      }

      @Override
      public void onEndReached () {
        chatSearchAdapter.updateValuedSettingById(R.id.search_counter);
        if (chatSearchAdapter.getItems().isEmpty()) {
          chatSearchAdapter.setItems(new ListItem[] {
            new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.NothingFound)
          }, false);
        }
      }
    });
    chatSearchManager.setSearchFlags(getChatSearchFlags());

    RemoveHelper.attach(chatSearchView, new RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        return item != null && item.getViewType() == ListItem.TYPE_CHAT_BETTER && item.getBoolValue();
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        final TGFoundChat chat = (TGFoundChat) item.getData();
        removeRecentChat(chat);
      }
    });

    if (parent != null) {
      parent.addView(chatSearchView);
    }
    if (needChatSearchManagerPreparation()) {
      chatSearchManager.onPrepare(getChatMessagesSearchChatList(), getChatSearchInitialQuery());
    }
    return chatSearchView;
  }

  protected final RecyclerView getChatSearchView () {
    return chatSearchView;
  }

  protected String getChatSearchInitialQuery () {
    return "";
  }

  private boolean isSearchAntagonistHidden;

  protected View getSearchAntagonistView () {
    throw new RuntimeException("Stub!");
  }

  private void setSearchAntagonistHidden (boolean isHidden) {
    if (this.isSearchAntagonistHidden != isHidden) {
      this.isSearchAntagonistHidden = isHidden;
      getSearchAntagonistView().setVisibility(isHidden ? View.INVISIBLE : View.VISIBLE);
    }
  }

  protected final boolean isSearchAntagonistHidden () {
    return isSearchAntagonistHidden;
  }

  private boolean isSearchContentVisible;

  private void setSearchContentVisible (boolean isVisible) {
    if (this.isSearchContentVisible != isVisible) {
      this.isSearchContentVisible = isVisible;
      chatSearchView.setScrollDisabled(!isVisible);
      chatSearchViewport.notifyLockValueChanged();
      context().checkDisallowScreenshots();
    }
  }

  private int generateTopCells (List<ListItem> items, int index) {
    final int startCount = items.size();
    ArrayList<TGFoundChat> topChats = chatSearchManager.getTopChats();
    if (topChats != null && topChats.size() > 0) {
      ArrayUtils.ensureCapacity(items, items.size() + 5);
      if (items.isEmpty()) {
        items.add(index++, new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      }
      items.add(index++, new ListItem(ListItem.TYPE_HEADER, R.id.search_section_top, 0, (getChatSearchFlags() & SearchManager.FLAG_TOP_SEARCH_CATEGORY_GROUPS) != 0 ? R.string.Groups : R.string.People));
      items.add(index++, new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(index++, new ListItem(ListItem.TYPE_RECYCLER_HORIZONTAL, R.id.search_top));
      items.add(index++, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    return items.size() - startCount;
  }

  private int generateChatsCells (int index, List<ListItem> items, ArrayList<TGFoundChat> chats, @IdRes int headerId, @IdRes int itemId, @StringRes int headerRes, boolean canClear) {
    final int startSize = items.size();
    if (chats != null && chats.size() > 0) {
      ArrayUtils.ensureCapacity(items, items.size() + (chats.size() - 1) * 2 + 5);
      if (items.isEmpty()) {
        items.add(index++, new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      }
      items.add(index++, new ListItem(canClear ? ListItem.TYPE_HEADER_WITH_ACTION : ListItem.TYPE_HEADER, headerId, R.drawable.baseline_clear_all_24, headerRes));
      items.add(index++, new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean isFirst = true;
      for (TGFoundChat chat : chats) {
        if (isFirst) {
          isFirst = false;
        } else {
          items.add(index++, new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(index++, searchValueOf(itemId, chat, canClear));
      }
      items.add(index++, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    return items.size() - startSize;
  }

  private void generateMessagesCells (List<ListItem> items, ArrayList<TGFoundMessage> messages, int dataStartIndex, int dataCount) {
    final boolean isInitialChunk = dataStartIndex == 0;
    final int startIndex = isInitialChunk ? items.size() : items.size() - 2;
    int index = startIndex;
    boolean addedInitialCell = false;

    if (messages != null && messages.size() > 0) {
      if (isInitialChunk) {
        ArrayUtils.ensureCapacity(items, items.size() + (messages.size() - 1) * 2 + 6);
        if (items.isEmpty()) {
          items.add(index++, new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
          addedInitialCell = true;
        }
      } else {
        ArrayUtils.ensureCapacity(items, items.size() + (messages.size() - 1) + 1);
      }
      if (isInitialChunk) {
        items.add(index++, new ListItem(ListItem.TYPE_HEADER, R.id.search_section_messages, 0, getChatMessagesSearchTitle()));
        items.add(index++, new ListItem(ListItem.TYPE_SHADOW_TOP));
      }
      boolean isFirst = true;
      for (int i = dataStartIndex; i < dataStartIndex + dataCount; i++) {
        TGFoundMessage message = messages.get(i);
        if (isFirst) {
          isFirst = false;
        } else {
          items.add(index++, new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(index++, new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.search_message).setData(message));
      }
    }

    if (isInitialChunk) {
      items.add(index++, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(index++, new ListItem(ListItem.TYPE_LIST_INFO_VIEW, R.id.search_counter));
    }

    final int addedCount = index - startIndex;

    if (addedInitialCell) {
      chatSearchAdapter.notifyItemRemoved(0);
    }
    chatSearchAdapter.notifyItemRangeInserted(startIndex, addedCount);
  }

  protected @StringRes int getChatMessagesSearchTitle () {
    return R.string.general_Messages;
  }

  protected final void forceOpenChatSearch (String query) {
    // enterSearchMode();
    if (chatSearchView.getAdapter() == null)
      chatSearchView.setAdapter(chatSearchAdapter);
    setSearchTransformFactor(1f, true);
    chatSearchManager.onOpen(getChatMessagesSearchChatList());
    // onEnterSearchMode();
    // updateSearchMode(true);
    forceSearchChats(query);
  }

  protected final boolean isChatSearchOpen () {
    return chatSearchManager != null && chatSearchManager.isOpen();
  }

  protected final void forceCloseChatSearch () {
    // updateSearchMode(false);
    // onLeaveSearchMode();
    setSearchTransformFactor(0f, false);
    // leaveSearchMode();
    chatSearchManager.onClose(getChatMessagesSearchChatList());
    chatSearchView.setAdapter(null);
  }

  protected final void forceSearchChats (String query) {
    chatSearchManager.onQueryChanged(getChatMessagesSearchChatList(), query);
  }

  private boolean needPreventKeyboardLag;

  private void setNeedPreventKeyboardLag (boolean needPreventKeyboardLag) {
    if (this.needPreventKeyboardLag != needPreventKeyboardLag) {
      this.needPreventKeyboardLag = needPreventKeyboardLag;
      UI.setSoftInputMode(context, needPreventKeyboardLag ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN : Config.DEFAULT_WINDOW_PARAMS);
    }
  }

  @Override
  @CallSuper
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    Views.setScrollBarPosition(chatSearchView);
  }

  @Override
  @CallSuper
  public void onLanguagePackEvent (int event, int arg1) {
    super.onLanguagePackEvent(event, arg1);
    if (chatSearchAdapter != null) {
      chatSearchAdapter.onLanguagePackEvent(event, arg1);
    }
  }

  @Override
  @CallSuper
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    if (chatSearchManager != null) {
      chatSearchManager.onOpen(getChatMessagesSearchChatList());
    }
  }

  @Override
  protected void startHeaderTransformAnimator (ValueAnimator animator, int mode, boolean open) {
    if (chatSearchView != null && chatSearchView.getAdapter() == null && mode == HeaderView.TRANSFORM_MODE_SEARCH && open) {
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart (Animator animation) {
          onChatSearchOpenStarted();
        }
      });
      AnimatorUtils.startAnimator(chatSearchView, animator, true);
      chatSearchView.setAdapter(chatSearchAdapter);
      return;
    }
    super.startHeaderTransformAnimator(animator, mode, open);
  }

  protected void onChatSearchOpenStarted () { }

  @Override
  @CallSuper
  protected void applySearchTransformFactor (float factor, boolean isOpening) {
    super.applySearchTransformFactor(factor, isOpening);
    if (chatSearchManager != null) {
      chatSearchView.setAlpha(factor);
      setSearchAntagonistHidden(factor == 1f);
      setSearchContentVisible(factor != 0f);
      // setNeedPreventKeyboardLag(factor != 0f && factor != 1f);
    }
  }

  @Override
  public int getRootColorId () {
    return (getSearchTransformFactor() != 0f && chatSearchManager != null) ? ColorId.background : super.getRootColorId();
  }

  @Override
  @CallSuper
  public boolean shouldDisallowScreenshots () {
    return (isSearchContentVisible && chatSearchDisallowScreenshots) || super.shouldDisallowScreenshots();
  }

  protected final void invalidateChatSearchResults () {
    if (chatSearchManager != null) {
      chatSearchManager.reloadSearchResults(getChatMessagesSearchChatList());
    }
  }

  protected final boolean inChatSearchMode () {
    return chatSearchManager != null;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return chatSearchManager != null || super.useGraySearchHeader();
  }

  @Override
  @CallSuper
  protected void onSearchInputChanged (String input) {
    super.onSearchInputChanged(input);
    if (chatSearchManager != null) {
      chatSearchManager.onQueryChanged(getChatMessagesSearchChatList(), input);
    }
  }

  @Override
  @CallSuper
  protected final boolean needPreventiveKeyboardHide () {
    return inSearchMode() && inChatSearchMode();
  }

  @Override
  protected void onAfterLeaveSearchMode () {
    super.onAfterLeaveSearchMode();
    if (chatSearchManager != null) {
      chatSearchManager.onClose(getChatMessagesSearchChatList());
      clearSearchInput();
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (chatSearchManager != null) {
      TGLegacyManager.instance().removeEmojiListener(chatSearchAdapter);
      Views.destroyRecyclerView(chatSearchView);
    }
    if (chatSearchViewport != null) {
      chatSearchViewport.performDestroy();
    }
  }
}
