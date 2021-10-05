/**
 * File created on 26/04/15 at 12:52
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.dialogs.ChatsAdapter;
import org.thunderdog.challegram.helper.LiveLocationHelper;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ChatsController;

import me.vkryl.android.util.ClickHelper;

public class ChatsRecyclerView extends CustomRecyclerView implements ClickHelper.Delegate {
  private static final int PRELOAD_SIZE = 15;

  private int initialLoadCount;
  private int loadCount;

  private ChatsController controller;
  private ChatsAdapter adapter;
  private LinearLayoutManager manager;

  public ChatsRecyclerView (Context context) {
    super(context);
    init(context);
  }

  public ChatsRecyclerView (Context context, AttributeSet set) {
    super(context, set);
    init(context);
  }

  public ChatsRecyclerView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  private LoadMoreCallback loadCallback;

  public interface LoadMoreCallback {
    boolean ableToLoadMore ();
    void requestLoadMore ();
  }

  private void init (Context context) {
    initialLoadCount = Screen.calculateLoadingItems(Screen.dp(72f), 5) + 5;
    loadCount = Screen.calculateLoadingItems(Screen.dp(72f), 25);

    // setItemAnimator(null);

    setLayoutManager(manager = new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy > 0) {
          if (controller != null && controller.isInForceTouchMode() && !isVerticalScrollBarEnabled()) {
            setVerticalScrollBarEnabled(true);
            controller.onInteractedWithContent();
          }
          if (loadCallback != null && loadCallback.ableToLoadMore() && manager.findLastVisibleItemPosition() + PRELOAD_SIZE >= adapter.getItemCount()) {
            loadCallback.requestLoadMore();
          }
        }
      }
    });
  }

  private final ClickHelper helper = new ClickHelper(this);

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res = super.onTouchEvent(e);
    if (controller.needLiveLocationClick()) {
      helper.onTouchEvent(this, e);
    }
    return res;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    int i = controller.getLiveLocationPosition();
    View boundView = getLayoutManager().findViewByPosition(i);
    if (boundView != null) {
      int decoratedTop = getLayoutManager().getDecoratedTop(boundView);
      return y >= decoratedTop && y < decoratedTop + LiveLocationHelper.height();
    }
    return false;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    controller.onLiveLocationClick(x, y);
  }

  public ChatsAdapter initWithController (ChatsController controller, LoadMoreCallback callback) {
    this.controller = controller;
    this.loadCallback = callback;
    this.adapter = new ChatsAdapter(controller, manager);
    setAdapter(adapter);
    return adapter;
  }

  public void setTotalRes (int totalRes) {
    this.adapter.setTotalRes(totalRes);
  }

  public int getInitialLoadCount () {
    return initialLoadCount;
  }

  public int getLoadCount () {
    return loadCount;
  }

  public void updateMessageInteractionInfo (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    int updated = adapter.updateMessageInteractionInfo(chatId, messageId, interactionInfo);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateMessageContent (long chatId, long messageId, TdApi.MessageContent newContent) {
    int updated = adapter.updateMessageContent(chatId, messageId, newContent);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void refreshLastMessage (long chatId, long messageId, boolean needRebuild) {
    int updated = adapter.refreshLastMessage(chatId, messageId, needRebuild);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateMessagesDeleted (long chatId, long[] messageIds) {
    int updated = adapter.updateMessagesDeleted(chatId, messageIds);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatReadInbox (long chatId, final long lastReadInboxMessageId, final int unreadCount) {
    int updated = adapter.updateChatReadInbox(chatId, lastReadInboxMessageId, unreadCount);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatUnreadMentionCount (long chatId, int unreadMentionCount) {
    int updated = adapter.updateChatUnreadMentionCount(chatId, unreadMentionCount);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatHasScheduledMessages (long chatId, boolean hasScheduledMessages) {
    int updated = adapter.updateChatHasScheduledMessages(chatId, hasScheduledMessages);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    int updated = adapter.updateMessageSendSucceeded(message, oldMessageId);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatDraftMessage (long chatId, TdApi.DraftMessage draftMessage) {
    int updated = adapter.updateChatDraftMessage(chatId, draftMessage);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatReadOutbox (long chatId, final long lastReadOutboxMessageId) {
    int updated = adapter.updateChatReadOutbox(chatId, lastReadOutboxMessageId);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateUser (TdApi.User user) {
    adapter.updateUser(this, user);
  }

  public void updateUserStatus (long userId) {
    int startIndex = 0, updated;
    while (true) {
      updated = adapter.updateUserStatus(userId, startIndex);
      if (updated == -1)
        break;
      View view = manager.findViewByPosition(updated);
      if (view instanceof ChatView && ((ChatView) view).getChatId() == adapter.getChatAt(updated).getChatId()) {
        ((ChatView) view).updateOnline();
        view.invalidate();
      } else {
        adapter.notifyItemChanged(updated);
      }
      startIndex = updated + 1;
    }
  }

  public void updateChatTitle (long chatId, String title) {
    int updated = adapter.updateChatTitle(chatId, title);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
    int updated = adapter.updateChatPermissions(chatId, permissions);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatClientData (long chatId, String clientData) {
    int updated = adapter.updateChatClientData(chatId, clientData);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    int updated = adapter.updateChatMarkedAsUnread(chatId, isMarkedAsUnread);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatTopMessage (long chatId, TdApi.Message topMessage) {
    int updated = adapter.updateChatTopMessage(chatId, topMessage);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateArchive (int updateReason) {
    int updated = adapter.updateArchive(updateReason);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateChatSelectionState (long chatId, boolean isSelected) {
    int index = adapter.indexOfChat(chatId);
    if (index != -1) {
      View view = getLayoutManager().findViewByPosition(index);
      if (view instanceof ChatView && ((ChatView) view).getChatId() == chatId) {
        ((ChatView) view).setIsSelected(isSelected, true);
      } else {
        adapter.notifyItemChanged(index);
      }
    }
  }

  public void updateChatPosition (long chatId, TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged) {
    if (sourceChanged) {
      int i = adapter.indexOfChat(chatId);
      if (i != -1) {
        invalidateViewAt(i);
      }
    }
  }

  public void processChatUpdate (int flags) {
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    int viewTop;
    if (firstVisiblePosition != -1) {
      View view = manager.findViewByPosition(firstVisiblePosition);
      viewTop = view != null ? view.getTop() : 0;
    } else {
      viewTop = 0;
    }
    if ((flags & ChatsAdapter.ORDER_REMAIN_SCROLL) != 0 && firstVisiblePosition != -1) {
      manager.scrollToPositionWithOffset(firstVisiblePosition, viewTop);
    }
    if ((flags & ChatsAdapter.ORDER_INVALIDATE_DECORATIONS) != 0) {
      adapter.invalidateAttachedItemDecorations();
    }
  }

  public void updateChatPhoto (long chatId, TdApi.ChatPhotoInfo photo) {
    int updated = adapter.updateChatPhoto(chatId, photo);
    if (updated != -1) {
      View view = manager.findViewByPosition(updated);
      if (view != null && view instanceof ChatView) {
        ((ChatView) view).invalidateContentReceiver();
      } else {
        adapter.notifyItemChanged(updated);
      }
    }
  }

  public void updateNotificationSettings (long chatId, final TdApi.ChatNotificationSettings settings) {
    int updated = adapter.updateChatSettings(chatId, settings);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateNotificationSettings (final TdApi.NotificationSettingsScope scope, final TdApi.ScopeNotificationSettings settings) {
    adapter.updateNotificationSettings(scope, settings);
  }

  public void updateSecretChat (TdApi.SecretChat secretChat) {
    int updated = adapter.updateSecretChat(secretChat);
    if (updated != -1) {
      invalidateViewAt(updated);
    }
  }

  public void updateLocale (boolean forceText) {
    adapter.updateLocale(forceText);
    invalidateAll();
  }
}
