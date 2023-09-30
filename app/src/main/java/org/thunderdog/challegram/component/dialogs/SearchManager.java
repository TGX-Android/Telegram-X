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
 * File created on 16/07/2017
 */
package org.thunderdog.challegram.component.dialogs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGFoundMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class SearchManager {
  public static abstract class Listener implements ListenerInterface {
    @Override
    public void onOpen () {

    }

    @Override
    public void onClose () {

    }

    @Override
    public void onPerformNewSearch (boolean isDefault) {

    }

    @Override
    public boolean onAddTopChats (ArrayList<TGFoundChat> topChats, boolean updateData, boolean isSilent) {
      return false;
    }

    @Override
    public void onRemoveTopChats (boolean updateData, boolean isSilent) {

    }

    @Override
    public void onUpdateTopChats (long[] oldChatIds, long[] newChatIds) {

    }

    @Override
    public void onRemoveTopChat (long chatId) {

    }

    @Override
    public void onAddLocalChats (ArrayList<TGFoundChat> localChats) {

    }

    @Override
    public void onRemoveLocalChats (int oldChatCount) {

    }

    @Override
    public void onUpdateLocalChats (int oldChatCount, ArrayList<TGFoundChat> localChats) {

    }

    @Override
    public void onRemoveLocalChat (long chatId, int position, int totalCount) {

    }

    @Override
    public void onAddLocalChat (TGFoundChat chat) {

    }

    @Override
    public void onMoveLocalChat (TGFoundChat chat, int fromPosition, int totalCount) {

    }

    @Override
    public void onAddGlobalChats (ArrayList<TGFoundChat> globalChats) {

    }

    @Override
    public void onRemoveGlobalChats (int oldChatCount) {

    }

    @Override
    public void onUpdateGlobalChats (int oldChatCount, ArrayList<TGFoundChat> globalChats) {

    }

    @Override
    public void onAddMessages (ArrayList<TGFoundMessage> messages) {

    }

    @Override
    public void onUpdateMessages (int oldMessageCount, ArrayList<TGFoundMessage> messages) {

    }

    @Override
    public void onRemoveMessages (int oldMessageCount) {

    }

    @Override
    public void onAddMoreMessages (int oldMessageCount, ArrayList<TGFoundMessage> messages) {

    }

    @Override
    public int getMessagesHeightOffset () {
      return 0;
    }

    @Override
    public void onHeavyPartReached (int contextId) {

    }

    @Override
    public void onHeavyPartFinished (int contextId) {

    }

    @Override
    public void onEndReached () {

    }
  }

  public interface ListenerInterface {
    void onOpen ();
    void onClose ();

    void onPerformNewSearch (boolean isDefault); // Clear chats, public chats & messages result
    default boolean customFilter (TdApi.Chat chat) { return true; }
    default boolean filterMessageSearchResultSource (TdApi.Chat chat) { return true; }
    default void modifyFoundChat (TGFoundChat foundChat) { }

    boolean onAddTopChats (ArrayList<TGFoundChat> topChats, boolean updateData, boolean isSilent);
    void onRemoveTopChats (boolean updateData, boolean isSilent);
    void onUpdateTopChats (long[] oldChatIds, long[] newChatIds);
    void onRemoveTopChat (long chatId);

    void onAddLocalChats (ArrayList<TGFoundChat> localChats);
    void onAddMoreLocalChats (ArrayList<TGFoundChat> addedLocalChats, int oldChatCount);
    void onRemoveLocalChats (int oldChatCount);
    void onUpdateLocalChats (int oldChatCount, ArrayList<TGFoundChat> localChats);
    void onRemoveLocalChat (long chatId, int position, int totalCount);
    void onAddLocalChat (TGFoundChat chat);
    void onMoveLocalChat (TGFoundChat chat, int fromPosition, int totalCount);

    void onAddGlobalChats (ArrayList<TGFoundChat> globalChats);
    void onRemoveGlobalChats (int oldChatCount);
    void onUpdateGlobalChats (int oldChatCount, ArrayList<TGFoundChat> globalChats);

    void onAddMessages (ArrayList<TGFoundMessage> messages);
    void onUpdateMessages (int oldMessageCount, ArrayList<TGFoundMessage> messages);
    void onRemoveMessages (int oldMessageCount);
    void onAddMoreMessages (int oldMessageCount, ArrayList<TGFoundMessage> messages);

    int getMessagesHeightOffset ();

    void onHeavyPartReached (int contextId);
    void onHeavyPartFinished (int contextId);
    void onEndReached ();
  }

  public static final int FLAG_NEED_TOP_CHATS = 1;
  public static final int FLAG_NEED_MESSAGES = 1 << 1;
  public static final int FLAG_NEED_GLOBAL_SEARCH = 1 << 2;
  public static final int FLAG_ONLY_WRITABLE = 1 << 3;
  public static final int FLAG_ONLY_USERS = 1 << 4;
  public static final int FLAG_NO_CHATS = 1 << 5;
  public static final int FLAG_NO_BOTS = 1 << 6;
  public static final int FLAG_ONLY_CONTACTS = 1 << 7;
  public static final int FLAG_NO_SELF = 1 << 8;
  public static final int FLAG_CUSTOM_FILTER = 1 << 9;
  public static final int FLAG_TOP_SEARCH_CATEGORY_GROUPS = 1 << 10;
  public static final int FLAG_NO_CHANNELS = 1 << 11;
  public static final int FLAG_NO_SUPPORT = 1 << 12;
  public static final int FLAG_NO_SECRET = 1 << 13;
  public static final int FLAG_NO_GROUPS = 1 << 14;

  public static final int FILTER_INVITE = FLAG_NEED_TOP_CHATS | FLAG_NEED_GLOBAL_SEARCH | FLAG_NO_SELF | FLAG_NO_SUPPORT | FLAG_NO_SECRET;
  private static final int FILTER_FLAGS = FLAG_ONLY_WRITABLE | FLAG_ONLY_USERS | FLAG_NO_BOTS | FLAG_ONLY_CONTACTS | FLAG_NO_SELF | FLAG_CUSTOM_FILTER | FLAG_NO_CHANNELS | FLAG_NO_SUPPORT | FLAG_NO_SECRET;

  private final Tdlib tdlib;
  private final ListenerInterface listener;
  private int searchFlags;

  private volatile int contextId;
  private String lastQuery;
  private TdApi.ChatList lastChatList;
  private boolean isOpen;

  public SearchManager (Tdlib tdlib, @NonNull Listener listener) {
    this.tdlib = tdlib;
    this.listener = listener;
  }

  // Public API

  public boolean isOpen () {
    return isOpen;
  }

  public void setSearchFlags (int searchFlags) {
    this.searchFlags = searchFlags;
  }

  public int getSearchFlags () {
    return searchFlags;
  }

  public void onOpen (final @Nullable TdApi.ChatList chatList) {
    if (!isOpen) {
      isOpen = true;
      listener.onOpen();
      performSearch(chatList, "", FORCE_MODE_TOP);
    }
  }

  public void reloadSearchResults (final @Nullable TdApi.ChatList chatList) {
    performSearch(chatList, lastQuery != null ? lastQuery : "", FORCE_MODE_ALL);
  }

  public void onClose (@Nullable TdApi.ChatList chatList) {
    if (isOpen) {
      isOpen = false;
      listener.onClose();
      performSearch(chatList, "", FORCE_MODE_NONE);
      addRecentlyFoundChat(null);
    }
  }

  private boolean isPrepared;

  public void onPrepare (@NonNull TdApi.ChatList chatList, @NonNull String initialQuery) {
    if (!isPrepared) {
      isPrepared = true;
      performSearch(chatList, initialQuery, FORCE_MODE_TOP);
    }
  }

  public void onQueryChanged (@NonNull TdApi.ChatList chatList, @NonNull String query) {
    performSearch(chatList, query, FORCE_MODE_NONE);
  }

  // Implementation

  private int incrementContextId () {
    if (contextId == Integer.MAX_VALUE) {
      contextId = 0;
    } else {
      contextId++;
    }
    return contextId;
  }

  private void reset () {
    this.lastQuery = null;
    this.lastChatList = null;
    this.scheduledShowHideTop = false;
    clearMessages();
    setLoadingMessages(false);
    this.isEndReached = false;
    this.isHeavyPartReached = false;
  }

  private static final int FORCE_MODE_NONE = 0;
  private static final int FORCE_MODE_TOP = 1;
  private static final int FORCE_MODE_ALL = 2;

  private void performSearch (final TdApi.ChatList chatList, final @NonNull String query, int forceMode) {
    if (lastQuery != null && StringUtils.equalsOrBothEmpty(lastQuery, query) && Td.equalsTo(lastChatList, chatList)) {
      if (isEndReached && forceMode != FORCE_MODE_NONE && StringUtils.isEmpty(lastQuery)) {
        if (forceMode == FORCE_MODE_TOP) {
          reloadTopChats();
        } else {
          loadTopChats(contextId, lastChatList, lastQuery, false);
        }
      }
      return;
    }

    final int currentContextId = incrementContextId();
    reset();

    this.lastQuery = query;
    this.lastChatList = chatList;

    listener.onPerformNewSearch(StringUtils.isEmpty(query));

    if ((searchFlags & FLAG_NO_CHATS) != 0) {
      searchMessages(currentContextId, chatList, query, false);
    } else {
      loadTopChats(currentContextId, chatList, query, false);
    }
  }

  // 1. Top Chats

  private ArrayList<TGFoundChat> topChats;
  private long[] topChatIds;

  private void reloadTopChats () {
    if ((searchFlags & FLAG_NEED_TOP_CHATS) != 0 && (searchFlags & FLAG_NO_CHATS) == 0) {
      loadTopChats(contextId, lastChatList, lastQuery, true);
    }
  }

  private boolean scheduledShowHideTop;
  private boolean showHideTop;

  private void loadTopChats (final int currentContextId, final TdApi.ChatList chatList, final String query, final boolean isCheck) {
    if (this.contextId != currentContextId && !isCheck) {
      return;
    }
    boolean passThrough = !StringUtils.isEmpty(query);
    if (!isCheck) {
      if (passThrough) {
        scheduledShowHideTop = true;
        showHideTop = false;
      } else {
        scheduledShowHideTop = true;
        showHideTop = true;
      }
    }
    if (!isCheck && ((searchFlags & FLAG_NEED_TOP_CHATS) == 0 || passThrough)) {
      searchLocalChats(currentContextId, chatList, query);
      return;
    }
    tdlib.send(new TdApi.GetTopChats((searchFlags & FLAG_TOP_SEARCH_CATEGORY_GROUPS) != 0 ? new TdApi.TopChatCategoryGroups() : new TdApi.TopChatCategoryUsers(), 30), (topChats, error) -> {
      if (contextId == currentContextId || isCheck) {
        final ArrayList<TGFoundChat> foundTopChats;
        final long[] foundTopChatIds;
        if (error != null) {
          Log.i("GetTopChats error, displaying no results: %s", TD.toErrorString(error));
          foundTopChats = null;
          foundTopChatIds = null;
        } else {
          long[] chatIds = topChats.chatIds;
          ArrayList<TGFoundChat> foundChats = new ArrayList<>(chatIds.length);
          int resultCount = parseResult(tdlib, listener, searchFlags, foundChats, chatList, chatIds, null, false, null);
          if (resultCount == 0) {
            foundTopChats = null;
            foundTopChatIds = null;
          } else if (resultCount == chatIds.length) {
            foundTopChats = foundChats;
            foundTopChatIds = chatIds;
          } else {
            foundTopChats = foundChats;
            foundTopChatIds = new long[resultCount];
            int i = 0;
            for (TGFoundChat chat : foundChats) {
              foundTopChatIds[i] = chat.getId();
              i++;
            }
          }
        }
        tdlib.ui().post(() -> {
          if (contextId == currentContextId || isCheck) {
            setTopChats(currentContextId, chatList, query, foundTopChats, foundTopChatIds, isCheck);
          }
        });
      }
    });
  }

  /*private ArrayList<TGFoundChat> scheduledTopChats;
  private long[] scheduledTopChatIds;*/

  private void setTopChats (final int currentContextId, final @Nullable TdApi.ChatList chatList, final @Nullable String query, final @Nullable ArrayList<TGFoundChat> topChats, final long[] topChatIds, final boolean isCheck) {
    boolean isSilent = this.contextId != currentContextId || isCheck;
    final int oldChatsCount = this.topChats != null ? this.topChats.size() : 0;
    final int newChatsCount = topChats != null ? topChats.size() : 0;

    if (oldChatsCount == 0 && newChatsCount == 0) {
      if (!isSilent) {
        searchLocalChats(currentContextId, chatList, query);
      }
      return;
    }

    processTopChats(currentContextId, topChats, topChatIds, isSilent);

    if (!isSilent) {
      searchLocalChats(currentContextId, chatList, query);
    }
  }

  private void processTopChats (final int currentContextId, final ArrayList<TGFoundChat> topChats, final long[] topChatIds, boolean isSilent) {
    if (this.contextId != currentContextId) {
      return;
    }

    final int oldChatsCount = this.topChats != null ? this.topChats.size() : 0;
    final int newChatsCount = topChats != null ? topChats.size() : 0;

    if (oldChatsCount != newChatsCount) {
      if (oldChatsCount == 0) {
        this.topChats = topChats;
        this.topChatIds = topChatIds;
        listener.onAddTopChats(topChats, true, isSilent);
      } else if (newChatsCount == 0) {
        this.topChats = topChats;
        this.topChatIds = topChatIds;
        listener.onRemoveTopChats(isSilent, true);
      } else if (!Arrays.equals(this.topChatIds, topChatIds)) {
        long[] oldTopChatIds = this.topChatIds;
        this.topChats = topChats;
        this.topChatIds = topChatIds;
        listener.onUpdateTopChats(oldTopChatIds, topChatIds);
      }
    } else if (newChatsCount > 0 && !Arrays.equals(this.topChatIds, topChatIds)) {
      long[] oldTopChatIds = this.topChatIds;
      this.topChats = topChats;
      this.topChatIds = topChatIds;
      listener.onUpdateTopChats(oldTopChatIds, topChatIds);
    }
  }

  public ArrayList<TGFoundChat> getTopChats () {
    return topChats;
  }

  public void removeTopChat (long chatId) {
    int position = ArrayUtils.indexOf(topChatIds, chatId);
    if (position != -1) {
      topChats.remove(position);
      topChatIds = ArrayUtils.removeElement(topChatIds, position);
      tdlib.client().send(new TdApi.RemoveTopChat(new TdApi.TopChatCategoryUsers(), chatId), tdlib.okHandler());
      if (topChatIds.length == 0) {
        listener.onRemoveTopChats(true, !StringUtils.isEmpty(lastQuery));
      } else {
        listener.onRemoveTopChat(chatId);
        reloadTopChats();
      }
    }
  }

  // 2. Local Chats

  private ArrayList<TGFoundChat> localChats;
  private String localChatsQuery;

  private static int indexOfLocalPrivateChat (ArrayList<TGFoundChat> chats, int userId) {
    int i = 0;
    for (TGFoundChat chat : chats) {
      if (chat.getUserId() == userId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private boolean isFiltered () {
    return isFiltered(searchFlags);
  }

  private static boolean isFiltered (int searchFlags) {
    return (searchFlags & FILTER_FLAGS) != 0;
  }

  public static int parseResult (Tdlib tdlib, ListenerInterface listener, int searchFlags, ArrayList<TGFoundChat> out, final TdApi.ChatList chatList, long[] chatIds, String query, boolean areGlobal, long[] excludeChatIds) {
    List<TdApi.Chat> chats = tdlib.chats(chatIds);
    out.ensureCapacity(chatIds.length);
    final boolean onlyWritable = (searchFlags & FLAG_ONLY_WRITABLE) != 0;
    final boolean onlyUsers = (searchFlags & FLAG_ONLY_USERS) != 0;
    final boolean onlyContacts = (searchFlags & FLAG_ONLY_CONTACTS) != 0;
    final boolean noBots = (searchFlags & FLAG_NO_BOTS) != 0;
    final boolean noSelf = (searchFlags & FLAG_NO_SELF) != 0;
    final boolean noSupport = (searchFlags & FLAG_NO_SUPPORT) != 0;
    final boolean noSecret = (searchFlags & FLAG_NO_SECRET) != 0;
    final boolean noChannels = (searchFlags & FLAG_NO_CHANNELS) != 0;
    final boolean noGroups = (searchFlags & FLAG_NO_GROUPS) != 0;
    final boolean needCustomFilter = (searchFlags & FLAG_CUSTOM_FILTER) != 0 && listener != null;
    if (excludeChatIds != null || isFiltered(searchFlags)) {
      int count = 0;
      for (TdApi.Chat chat : chats) {
        if (excludeChatIds != null && ArrayUtils.indexOf(excludeChatIds, chat.id) != -1) {
          continue;
        }
        if (noSelf && chat.id == tdlib.selfChatId()) {
          continue;
        }
        if (noChannels && tdlib.isChannel(chat.id)) {
          continue;
        }
        if (noGroups && tdlib.isMultiChat(chat.id)) {
          continue;
        }
        if (onlyUsers && (!ChatId.isUserChat(chat.id) || !tdlib.canAddToOtherChat(chat))) {
          continue;
        }
        if (noSecret && ChatId.isSecret(chat.id)) {
          continue;
        }
        if (onlyWritable && !tdlib.canSendBasicMessage(chat)) {
          continue;
        }
        if (noBots && tdlib.isBotChat(chat)) {
          continue;
        }
        if (noSupport && tdlib.isSupportChat(chat)) {
          continue;
        }
        if (onlyContacts && (!ChatId.isUserChat(chat.id) || !tdlib.isContactChat(chat))) {
          continue;
        }
        if (needCustomFilter && !listener.customFilter(chat)) {
          continue;
        }
        TGFoundChat foundChat = new TGFoundChat(tdlib, chatList, chat, areGlobal, query);
        if (listener != null) {
          listener.modifyFoundChat(foundChat);
        }
        out.add(foundChat);
        count++;
      }
      return count;
    } else {
      for (TdApi.Chat chat : chats) {
        TGFoundChat foundChat = new TGFoundChat(tdlib, chatList, chat, areGlobal, query);
        if (listener != null) {
          listener.modifyFoundChat(foundChat);
        }
        out.add(foundChat);
      }
      return chats.size();
    }
  }

  private void searchLocalChats (final int currentContextId, final TdApi.ChatList chatList, final @Nullable String query) {
    if (this.contextId != currentContextId) {
      return;
    }

    final int[] state = new int[2]; // 1 - step, 2 - disallowSelf
    final LongList foundChatIds = new LongList(16);

    tdlib.client().send(new TdApi.SearchChats(query, StringUtils.isEmpty(query) ? 20 : isFiltered() ? 50 : 30), new Client.ResultHandler() {
      @Override
      public void onResult (final TdApi.Object object) {
        if (contextId != currentContextId) {
          return;
        }

        final boolean isFirst = foundChatIds.isEmpty();

        final ArrayList<TGFoundChat> foundChats;
        switch (object.getConstructor()) {
          case TdApi.Chats.CONSTRUCTOR: {
            long[] chatIds = ((TdApi.Chats) object).chatIds;
            foundChats = new ArrayList<>(chatIds.length);
            final long selfUserId = tdlib.myUserId();
            final long selfChatId = ChatId.fromUserId(selfUserId);
            if (selfChatId != 0 && !StringUtils.isEmpty(query) && state[1] == 0) {
              state[1] = 1;
              if (foundChatIds.indexOf(selfChatId) == -1) {
                String savedMessagesStr = Lang.getString(R.string.SavedMessages).toLowerCase();
                String savedMessagesStr2 = Lang.getBuiltinString(R.string.SavedMessages).toLowerCase();
                String check = query.trim().toLowerCase();
                if (!StringUtils.isEmpty(check) && ((!StringUtils.isEmpty(savedMessagesStr) && Strings.anyWordStartsWith(savedMessagesStr, check, null)) || (!StringUtils.isEmpty(savedMessagesStr2) && Strings.anyWordStartsWith(savedMessagesStr2, check, null)))) {
                  TdApi.Chat chat = tdlib.chat(selfChatId);
                  if (chat != null) {
                    parseResult(tdlib, listener, searchFlags, foundChats, chatList, new long[] {selfChatId}, query, false, null);
                    foundChatIds.append(selfChatId);
                  } else {
                    TdApi.User user = tdlib.cache().user(selfUserId);
                    if (user != null) {
                      foundChats.add(new TGFoundChat(tdlib, user, query, true));
                      foundChatIds.append(selfChatId);
                    }
                  }
                }
              }
            }
            parseResult(tdlib, listener, searchFlags, foundChats, chatList, chatIds, query, false, !foundChatIds.isEmpty() ? foundChatIds.get() : null);
            foundChatIds.appendAll(chatIds);
            break;
          }
          case TdApi.Users.CONSTRUCTOR: {
            long[] userIds = ((TdApi.Users) object).userIds;
            if (userIds.length == 0) {
              foundChats = null;
              break;
            }
            ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
            foundChats = new ArrayList<>(userIds.length);
            for (TdApi.User user : users) {
              if (user != null && foundChatIds.indexOf(ChatId.fromUserId(user.id)) == -1) {
                foundChats.add(new TGFoundChat(tdlib, user, query, false));
              }
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.w("Search error: %s", TD.toErrorString(object));
            foundChats = null;
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.SearchChats.class, TdApi.Chats.class, TdApi.Users.class, TdApi.Error.class);
            return;
          }
        }

        boolean sentRequest = false;
        // final boolean isFirst = state[0] == 0;
        if (!StringUtils.isEmpty(query)) {
          switch (++state[0]) {
            case 1:
              if (sentRequest = foundChatIds.size() < 100) {
                tdlib.client().send(new TdApi.SearchChatsOnServer(query, 100 - foundChatIds.size()), this);
              }
              break;
            case 2:
              if (sentRequest = foundChatIds.size() < 50) {
                tdlib.searchContacts(query, 50 - foundChatIds.size(), this);
              }
              break;
          }
        }

        if ((foundChats == null || foundChats.isEmpty()) && sentRequest) {
          return;
        }


        final boolean isFinish = !sentRequest;
        tdlib.ui().post(() -> {
          if (contextId == currentContextId) {
            if (isFirst) {
              setLocalChats(currentContextId, query, foundChats);
            } else {
              addMoreLocalChats(currentContextId, query, foundChats);
            }
            if (isFinish) {
              searchGlobalChats(currentContextId, chatList, query);
            }
          }
        });
      }
    });
  }

  private void setLocalChats (ArrayList<TGFoundChat> chats, String query) {
    this.localChats = chats;
    this.localChatsQuery = query;
  }

  private void addMoreLocalChats (final int currentContextId, final @Nullable String query, final @Nullable ArrayList<TGFoundChat> chats) {
    if (this.contextId != currentContextId) {
      return;
    }

    final int oldLocalChatCount = this.localChats != null ? this.localChats.size() : 0;
    if (oldLocalChatCount == 0) {
      setLocalChats(currentContextId, query, chats);
      return;
    }

    if (chats != null && !chats.isEmpty()) {
      localChats.addAll(chats);
      listener.onAddMoreLocalChats(chats, oldLocalChatCount);
    }
  }

  private void setLocalChats (final int currentContextId, final @Nullable String query, final @Nullable ArrayList<TGFoundChat> chats) {
    if (this.contextId != currentContextId) {
      return;
    }

    final int oldLocalChatCount = this.localChats != null ? this.localChats.size() : 0;
    setLocalChats(chats, query);
    final int newLocalChatCount = chats != null ? chats.size() : 0;

    if (oldLocalChatCount == newLocalChatCount || (oldLocalChatCount > 0 && newLocalChatCount > 0)) {
      if (oldLocalChatCount != 0) {
        listener.onUpdateLocalChats(oldLocalChatCount, chats);
      }
    } else if (newLocalChatCount == 0) {
      listener.onRemoveLocalChats(oldLocalChatCount);
    } else if (oldLocalChatCount == 0) {
      listener.onAddLocalChats(chats);
    }

    if (scheduledShowHideTop) {
      scheduledShowHideTop = false;
      if (showHideTop) {
        if (topChats != null && !topChats.isEmpty()) {
          listener.onAddTopChats(topChats, false, false);
        }
      } else {
        listener.onRemoveTopChats(false, false);
      }
    }
  }

  public ArrayList<TGFoundChat> getLocalChats () {
    return localChats;
  }

  public boolean areLocalChatsRecent () {
    return StringUtils.isEmpty(lastQuery);
  }

  private ArrayList<TGFoundChat> awaitingFoundChats = new ArrayList<>();

  public void addRecentlyFoundChat (TGFoundChat chat) {
    addRecentlyFoundChat(chat, true);
  }

  private void addRecentlyFoundChat (@Nullable TGFoundChat chat, boolean allowAwaitingPool) {
    if (isOpen) {
      if (chat != null) {
        awaitingFoundChats.add(chat);
      }
      return;
    }

    if (allowAwaitingPool && !awaitingFoundChats.isEmpty()) {
      for (TGFoundChat awaitingChat : awaitingFoundChats) {
        addRecentlyFoundChat(awaitingChat, false);
      }
      awaitingFoundChats.clear();
    }

    if (chat == null) {
      return;
    }

    if (StringUtils.isEmpty(lastQuery) && localChats != null && StringUtils.isEmpty(localChatsQuery)) {
      int i = indexOfLocalChat(chat.getId());
      if (i != -1) {
        if (i != 0) {
          TGFoundChat movedChat = localChats.remove(i);
          if (movedChat != chat && movedChat.getAnyId() != chat.getAnyId() && (movedChat.getUserId() != chat.getUserId() || chat.getUserId() == 0)) {
            throw new RuntimeException("Stub!");
          }
          localChats.add(0, movedChat);
          listener.onMoveLocalChat(movedChat, i, localChats.size());
        }
      } else {
        if (localChats == null) {
          localChats = new ArrayList<>();
        }
        localChats.add(0, chat);
        if (localChats.size() == 1) {
          listener.onAddLocalChats(localChats);
        } else {
          listener.onAddLocalChat(chat);
        }
      }
    }
    tdlib.client().send(new TdApi.AddRecentlyFoundChat(chat.getAnyId()), tdlib.okHandler());
  }

  public void clearRecentlyFoundChats () {
    final int oldLocalChatsCount = localChats != null ? localChats.size() : 0;
    if (oldLocalChatsCount > 0) {
      if (StringUtils.isEmpty(lastQuery)) {
        setLocalChats(null, lastQuery);
        listener.onRemoveLocalChats(oldLocalChatsCount);
      }
      tdlib.client().send(new TdApi.ClearRecentlyFoundChats(), tdlib.okHandler());
    }
  }

  private int indexOfLocalChat (long chatId) {
    if (localChats == null || localChats.isEmpty()) {
      return -1;
    }
    int i = 0;
    for (TGFoundChat chat : localChats) {
      if (chat.getId() == chatId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public void removeRecentlyFoundChat (TGFoundChat foundChat) {
    int i = indexOfLocalChat(foundChat.getId());
    if (i != -1) {
      localChats.remove(i);
      if (localChats.isEmpty()) {
        listener.onRemoveLocalChats(1);
      } else {
        listener.onRemoveLocalChat(foundChat.getId(), i, localChats.size() + 1);
      }
      tdlib.client().send(new TdApi.RemoveRecentlyFoundChat(foundChat.getId()), tdlib.okHandler());
    }
  }

  // Global chats

  private void clearGlobalChats () {
    if (globalChats != null && globalChats.size() > 0) {
      listener.onRemoveGlobalChats(globalChats.size());
      globalChats = null;
    }
  }

  private static final int MIN_USERNAME_LENGTH = 2;

  private void searchGlobalChats (final int currentContextId, final TdApi.ChatList chatList, final @Nullable String query) {
    if (contextId != currentContextId) {
      return;
    }

    if ((searchFlags & FLAG_NEED_GLOBAL_SEARCH) == 0) {
      onHeavyPartReached();
      searchMessages(currentContextId, chatList, query, false);
      return;
    }

    final String usernameQuery = StringUtils.isEmpty(query) || query.charAt(0) != '@' ? query : query.substring(1);

    onHeavyPartReached();

    final CancellableRunnable runnable = new CancellableRunnable() {
      @Override
      public void act () {
        if (contextId == currentContextId) {
          clearGlobalChats();
          clearMessages();
        }
      }
    };
    runnable.removeOnCancel(UI.getAppHandler());

    if (StringUtils.isEmpty(query)) {
      clearGlobalChats();
      clearMessages();
      onEndReached();
      return;
    }

    tdlib.ui().post(runnable);

    tdlib.send(new TdApi.SearchPublicChats(usernameQuery), (remoteChats, error) -> {
      if (contextId == currentContextId) {
        runnable.cancel();
        final ArrayList<TGFoundChat> foundChats;
        if (error != null) {
          Log.i("SearchPublicChats error, showing no results: %s", TD.toErrorString(error));
          foundChats = null;
        } else {
          long[] chatIds = remoteChats.chatIds;
          foundChats = new ArrayList<>(chatIds.length);
          parseResult(tdlib, listener, searchFlags & (~FLAG_ONLY_CONTACTS), foundChats, chatList, chatIds, usernameQuery, true, null);
        }
        tdlib.ui().post(() -> {
          if (contextId == currentContextId) {
            setGlobalChats(currentContextId, chatList, query, foundChats);
          }
        });
      }
    });
  }

  private ArrayList<TGFoundChat> globalChats;

  private void setGlobalChats (final int currentContextId, final TdApi.ChatList chatList, final @Nullable String query, ArrayList<TGFoundChat> chats) {
    if (this.contextId != currentContextId) {
      return;
    }

    final int oldGlobalChatCount = this.globalChats != null ? this.globalChats.size() : 0;
    this.globalChats = chats;
    final int newGlobalChatCount = chats != null ? chats.size() : 0;

    if (oldGlobalChatCount == newGlobalChatCount || (oldGlobalChatCount > 0 && newGlobalChatCount > 0)) {
      if (oldGlobalChatCount != 0) {
        listener.onUpdateGlobalChats(oldGlobalChatCount, chats);
      }
    } else if (newGlobalChatCount == 0) {
      listener.onRemoveGlobalChats(oldGlobalChatCount);
    } else if (oldGlobalChatCount == 0) {
      listener.onAddGlobalChats(chats);
    }

    searchMessages(currentContextId, chatList, query, false);
  }

  // Messages

  private static class MessagesList {
    private final ArrayList<TGFoundMessage> messages;
    private String nextOffset;

    public MessagesList (int initialCapacity) {
      this.messages = new ArrayList<>(initialCapacity);
    }

    public boolean canLoadMore () {
      return !StringUtils.isEmpty(nextOffset);
    }

    public boolean isEmpty () {
      return messages.isEmpty();
    }

    public int size () {
      return messages.size();
    }
  }

  private MessagesList messageList;

  private void setLoadingMessages (boolean areLoading) {
    if (this.isLoadingMessages != areLoading) {
      this.isLoadingMessages = areLoading;
    }
  }

  private void clearMessages () {
    if (messageList != null && !messageList.isEmpty()) {
      int size = messageList.size();
      messageList = null;
      listener.onRemoveMessages(size);
    }
  }

  private void searchMessages (final int currentContextId, final TdApi.ChatList chatList, final String query, final boolean isMore) {
    if (this.contextId != currentContextId || isLoadingMessages) {
      return;
    }
    if ((searchFlags & FLAG_NEED_MESSAGES) == 0) {
      onEndReached();
      return;
    }
    setLoadingMessages(true);

    final CancellableRunnable runnable;
    if (isMore) {
      runnable = null;
    } else {
      runnable = new CancellableRunnable() {
        @Override
        public void act () {
          if (contextId == currentContextId) {
            clearMessages();
          }
        }
      };
      runnable.removeOnCancel(UI.getAppHandler());
      tdlib.ui().post(runnable);
    }

    int targetHeight = Screen.widestSide() - listener.getMessagesHeightOffset();

    final int loadCount = isMore ? 30 : Screen.calculateLoadingItems(Screen.dp(72f), 5, targetHeight);
    final Runnable actor = () -> {
      if (contextId != currentContextId) {
        return;
      }
      String offset = null;
      if (isMore) {
        offset = messageList.nextOffset;
      }
      tdlib.send(new TdApi.SearchMessages(chatList, query, offset, loadCount, null, 0, 0), new Tdlib.ResultHandler<>() {
        @Override
        public void onResult (TdApi.FoundMessages foundMessages, @Nullable TdApi.Error error) {
          if (contextId != currentContextId) {
            return;
          }
          if (runnable != null) {
            runnable.cancel();
          }
          final TGFoundMessage[] messages;
          final String nextOffset;
          if (error != null) {
            Log.w("SearchMessages returned error, displaying no results: %s", TD.toErrorString(error));
            messages = null;
            nextOffset = null;
          } else {
            List<TGFoundMessage> foundMessageList = new ArrayList<>(foundMessages.messages.length);
            TdApi.Chat chat = null;
            for (TdApi.Message message : foundMessages.messages) {
              if (chat == null || chat.id != message.chatId)
                chat = tdlib.chat(message.chatId);
              if (listener.filterMessageSearchResultSource(chat)) {
                foundMessageList.add(new TGFoundMessage(tdlib, chatList, chat, message, query));
              }
            }
            if (foundMessageList.isEmpty() && !StringUtils.isEmpty(foundMessages.nextOffset)) {
              tdlib.send(new TdApi.SearchMessages(chatList, query, foundMessages.nextOffset, loadCount, null, 0, 0), this);
              return;
            }
            messages = foundMessageList.toArray(new TGFoundMessage[0]);
            nextOffset = foundMessages.nextOffset;
          }
          tdlib.ui().post(() -> {
            if (contextId == currentContextId) {
              setMessages(currentContextId, query, loadCount, messages, isMore, nextOffset);
            }
          });
        }
      });
    };

    if (isMore) {
      actor.run();
    } else {
      tdlib.ui().postDelayed(actor, 150l);
    }
  }

  private void setMessages (final int currentContextId, final @Nullable String query, final int loadCount, final TGFoundMessage[] foundMessages, final boolean isMore, final String nextOffset) {
    if (this.contextId != currentContextId || !isLoadingMessages) {
      return;
    }
    setLoadingMessages(false);

    final int oldMessagesCount = this.messageList != null ? this.messageList.size() : 0;
    final int addedMessagesCount = foundMessages != null ? foundMessages.length : 0;
    final int newMessagesCount = isMore ? oldMessagesCount + addedMessagesCount : addedMessagesCount;

    if (!isMore) {
      listener.onHeavyPartFinished(contextId);
    }

    if (addedMessagesCount == 0) {
      if (!isMore) {
        clearMessages();
      }
      onEndReached();
      return;
    }

    if (this.messageList == null) {
      this.messageList = new MessagesList(foundMessages.length);
    }
    this.messageList.messages.ensureCapacity(this.messageList.messages.size() + foundMessages.length);
    Collections.addAll(this.messageList.messages, foundMessages);
    this.messageList.nextOffset = nextOffset;

    if (isMore) {
      listener.onAddMoreMessages(oldMessagesCount, messageList.messages);
    } else if (oldMessagesCount == 0) {
      listener.onAddMessages(this.messageList.messages);
    } else if (oldMessagesCount == newMessagesCount || (oldMessagesCount > 0 && newMessagesCount > 0)) {
      listener.onUpdateMessages(oldMessagesCount, this.messageList.messages);
    }

    if (StringUtils.isEmpty(nextOffset)) {
      onEndReached();
    }
  }

  private boolean isLoadingMessages;

  public void loadMoreMessages () {
    if (!isLoadingMessages && messageList != null && !messageList.isEmpty() && !StringUtils.isEmpty(messageList.nextOffset)) {
      searchMessages(contextId, lastChatList, lastQuery, true);
    }
  }

  public int getFoundMessagesCount () {
    return messageList != null ? messageList.size() : 0;
  }

  private boolean isEndReached;

  private void onEndReached () {
    if (!isEndReached) {
      isEndReached = true;
      listener.onEndReached();
    }
  }

  public boolean isEndReached () {
    return isEndReached;
  }

  private boolean isHeavyPartReached;

  private void onHeavyPartReached () {
    if (!isHeavyPartReached) {
      isHeavyPartReached = true;
      listener.onHeavyPartReached(contextId);
    }
  }

  public boolean isHeavyPartReached () {
    return isHeavyPartReached;
  }

  public int getContextId () {
    return contextId;
  }
}
