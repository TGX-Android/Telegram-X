package org.thunderdog.challegram.component.chat;

import android.annotation.SuppressLint;

import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Highlight;

import java.util.ArrayList;
import java.util.Objects;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class ChatMembersSearcher {
  public static final int FILTER_TYPE_CONTACTS = 1;
  public static final int FILTER_TYPE_RECENT = 2;
  public static final int FILTER_TYPE_DEFAULT = 3;
  public static final int FILTER_TYPE_GLOBAL_LOCAL = 4;
  public static final int FILTER_TYPE_GLOBAL_GLOBAL = 5;

  private static final int LIMIT = 30;

  private final Tdlib tdlib;
  private String currentContextId = null;

  private final ArrayList<Long> foundMembers;
  private long currentChatId;
  private String currentQuery;
  private Handler currentHandler;
  private int currentFilter;
  private int currentOffset;

  public ChatMembersSearcher (Tdlib tdlib) {
    this.tdlib = tdlib;
    this.foundMembers = new ArrayList<>();
  }

  @UiThread
  public boolean search (long chatId, String query, Handler handler) {
    String contextId = makeContextId(chatId, query);
    boolean needReset = checkContextId(contextId);
    this.currentChatId = chatId;
    this.currentQuery = query;
    this.currentHandler = handler;

    performRequest(contextId, chatId, query, handler);

    return needReset;
  }

  @UiThread
  public void next (Handler handler) {
    search(currentChatId, currentQuery, handler);
  }

  private void performRequest (String contextId, long chatId, String query, Handler handler) {
    if (tdlib.isChannel(chatId)) {
      return;
    }

    if (tdlib.isUserChat(chatId)) {
      performRequestForUserChat(chatId, query, handler);
      return;
    }

    long supergroupId = ChatId.toSupergroupId(chatId);
    boolean isSupergroup = supergroupId != 0;

    if (currentFilter == FILTER_TYPE_GLOBAL_GLOBAL) {
      tdlib.client().send(new TdApi.SearchPublicChats(query), o -> onResult(contextId, o));
    } else if (currentFilter == FILTER_TYPE_GLOBAL_LOCAL) {
      tdlib.client().send(new TdApi.SearchChatsOnServer(query, 50), o -> onResult(contextId, o));
    } else if (isSupergroup) {
      tdlib.client().send(new TdApi.GetSupergroupMembers(supergroupId, makeSupergroupFilter(query, currentFilter), currentOffset, LIMIT), o -> onResult(contextId, o));
    } else {
      tdlib.client().send(new TdApi.SearchChatMembers(chatId, query, LIMIT, makeBasicGroupFilter(currentFilter)), o -> onResult(contextId, o));
    }
  }

  @TdlibThread
  private void onResult (String contextId, TdApi.Object object) {
    UI.post(() -> {
      if (!StringUtils.equalsTo(contextId, currentContextId)) {
        return;
      }

      final int usedFilter = currentFilter;
      boolean needSetNextFilter = false;
      boolean isFinish = false;
      boolean isEmpty = true;
      TdApi.Object result = null;

      switch (object.getConstructor()) {
        case TdApi.Chats.CONSTRUCTOR: {
          TdApi.Chats chats = (TdApi.Chats) object;
          ArrayList<Long> items = new ArrayList<>();
          for (long id : chats.chatIds) {
            if (checkIfNotFoundBefore(id)) {
              items.add(id);
            }
          }
          needSetNextFilter = true;
          long[] chatIds = new long[items.size()];
          ArrayUtils.toArray(items, chatIds);
          result = new TdApi.Chats(items.size(), chatIds);
          isEmpty = items.isEmpty();
          break;
        }
        case TdApi.ChatMembers.CONSTRUCTOR: {
          TdApi.ChatMembers members = (TdApi.ChatMembers) object;
          ArrayList<TdApi.ChatMember> items = new ArrayList<>();
          for (TdApi.ChatMember member : members.members) {
            if (checkIfNotFoundBefore(Td.getSenderId(member.memberId))) {
              items.add(member);
            }
          }
          currentOffset += members.members.length;
          needSetNextFilter = members.totalCount <= currentOffset;
          result = new TdApi.ChatMembers(items.size(), items.toArray(new TdApi.ChatMember[0]));
          isEmpty = items.isEmpty();
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
      }

      if (needSetNextFilter) {
        if (currentFilter == FILTER_TYPE_CONTACTS) {
          if (StringUtils.isEmpty(currentQuery)) {
            if (tdlib.isSupergroup(currentChatId)) {
              currentFilter = FILTER_TYPE_RECENT;
            } else {
              currentFilter = FILTER_TYPE_DEFAULT;
            }
          } else {
            currentFilter = FILTER_TYPE_DEFAULT;
          }
        } else if (currentFilter == FILTER_TYPE_RECENT) {
          currentFilter = FILTER_TYPE_DEFAULT;
        } else if (currentFilter == FILTER_TYPE_DEFAULT) {
          currentFilter = FILTER_TYPE_GLOBAL_LOCAL;
        } else if (currentFilter == FILTER_TYPE_GLOBAL_LOCAL) {
          currentFilter = FILTER_TYPE_GLOBAL_GLOBAL;
        } else if (currentFilter == FILTER_TYPE_GLOBAL_GLOBAL) {
          isFinish = true;
        }
        currentOffset = 0;
      }

      if (usedFilter != currentFilter && currentFilter == FILTER_TYPE_GLOBAL_LOCAL && !foundMembers.isEmpty()) {
        isFinish = true;
      }

      if (isEmpty && !isFinish) {
        UI.post(() -> {
          if (currentHandler != null) {
            next(currentHandler);
          }
        });
      } else if (currentHandler != null) {
        currentHandler.onResult(new Result(result, usedFilter, !isFinish));
      }
    });
  }

  private boolean checkIfNotFoundBefore (long id) {
    if (!foundMembers.contains(id)) {
      foundMembers.add(id);
      return true;
    }

    return false;
  }

  private TdApi.SupergroupMembersFilter makeSupergroupFilter (String query, int mode) {
    if (mode == FILTER_TYPE_CONTACTS) {
      return new TdApi.SupergroupMembersFilterContacts(query);
    } else if (mode == FILTER_TYPE_RECENT) {
      return new TdApi.SupergroupMembersFilterRecent();
    } else if (mode == FILTER_TYPE_DEFAULT) {
      return new TdApi.SupergroupMembersFilterSearch(query);
    }
    return null;
  }

  private TdApi.ChatMembersFilter makeBasicGroupFilter (int mode) {
    if (mode == FILTER_TYPE_CONTACTS) {
      return new TdApi.ChatMembersFilterContacts();
    }
    return null;
  }

  private boolean queryUserCheck (long userId, String query) {
    if (StringUtils.isEmpty(query)) return true;

    String userName = tdlib.cache().userName(userId);
    Highlight highlight = Highlight.valueOf(userName, query);

    return (highlight != null && !highlight.isEmpty());
  }

  private void performRequestForUserChat (long userId, String query, Handler handler) {
    long otherUserId = ChatId.isSecret(userId) ? tdlib.chatUserId(userId): userId;
    long myUserId = tdlib.myUserId();

    boolean otherUserOk = queryUserCheck(otherUserId, query);
    boolean myUserOk = queryUserCheck(myUserId, query);

    final TdApi.Users users;
    if (myUserOk && otherUserOk) {
      users = new TdApi.Users(2, new long[]{ myUserId, otherUserId });
    } else if (myUserOk) {
      users = new TdApi.Users(1, new long[]{ myUserId });
    } else if (otherUserOk) {
      users = new TdApi.Users(1, new long[]{ otherUserId });
    } else {
      users = new TdApi.Users(0, new long[]{});
    }

    tdlib.runOnTdlibThread(() -> handler.onResult(new Result(users, FILTER_TYPE_DEFAULT, false)));
  }

  private boolean checkContextId (String contextId) {
    if (!Objects.equals(contextId, currentContextId)) {
      currentContextId = contextId;
      reset();
      return true;
    }
    return false;
  }

  public void reset () {
    foundMembers.clear();
    currentFilter = FILTER_TYPE_CONTACTS;
    currentOffset = 0;
    currentChatId = 0;
    currentQuery = null;
    currentHandler = null;
  }

  @SuppressLint("DefaultLocale")
  private static String makeContextId (long chatId, String query) {
    return String.format("chat_members_%d_%s", chatId, query);
  }

  public interface Handler {
    void onResult (Result result);
  }

  public static class Result {
    public final boolean canLoadMore;
    public final TdApi.Object result;
    public final int type;

    public Result (TdApi.Object result, int type, boolean canLoadMore) {
      this.canLoadMore = canLoadMore;
      this.result = result;
      this.type = type;
    }
  }
}
