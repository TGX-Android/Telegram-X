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
 * File created on 10/10/2023
 */
package org.thunderdog.challegram.telegram;

import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.FutureBool;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class TdlibMessageViewer {
  private static final long TRACK_MESSAGE_TIMEOUT_MS = 1000;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION,
    Flags.NO_SCREENSHOT_NOTIFICATION,
    Flags.REFRESH_INTERACTION_INFO
  }, flag = true)
  public @interface Flags {
    int
      NO_SENSITIVE_SCREENSHOT_NOTIFICATION = 1, // No sensitive content is visible & no need to send MessageSourceScreenshot
      NO_SCREENSHOT_NOTIFICATION = 1 << 1, // Completely ignore screenshots of the given message
      REFRESH_INTERACTION_INFO = 1 << 2; // Periodically refresh interaction info based on message date
  }

  public static class VisibilityState {
    public long openTimeMs, hideTimeMs;

    public VisibilityState (boolean isVisible) {
      // TODO consider switching to tdlib.currentTimeMillis()?
      if (isVisible) {
        this.openTimeMs = System.currentTimeMillis();
      } else {
        this.hideTimeMs = System.currentTimeMillis();
      }
    }

    public boolean isVisible () {
      return hideTimeMs == 0;
    }

    public boolean isHidden () {
      return hideTimeMs != 0;
    }

    public boolean markAsHidden () {
      if (isVisible()) {
        this.hideTimeMs = System.currentTimeMillis();
        return true;
      }
      return false;
    }

    public boolean markAsVisible () {
      if (isHidden()) {
        this.openTimeMs = System.currentTimeMillis();
        this.hideTimeMs = 0;
        return true;
      }
      return false;
    }
  }

  public static class VisibleMessage {
    public final long chatId;
    public final TdApi.Message message;
    public final TdApi.SponsoredMessage sponsoredMessage;
    public final VisibilityState visibility = new VisibilityState(true);
    public @Flags long flags;
    public long viewId;
    public boolean isRecentlyViewed;

    public VisibleMessage (@NonNull TdApi.Message message, @Flags long flags, long viewId) {
      this.chatId = message.chatId;
      this.message = message;
      this.sponsoredMessage = null;
      this.flags = flags;
      this.viewId = viewId;
    }

    public VisibleMessage (long chatId, @NonNull TdApi.SponsoredMessage sponsoredMessage, long flags, long viewId) {
      this.chatId = chatId;
      this.message = null;
      this.sponsoredMessage = sponsoredMessage;
      this.flags = flags;
      this.viewId = viewId;
    }

    public long getChatId () {
      return chatId;
    }

    public long getMessageId () {
      return sponsoredMessage != null ? sponsoredMessage.messageId : message != null ? message.id : 0;
    }

    public int getMessageDate () {
      if (message == null)
        throw new IllegalStateException();
      return message.date;
    }

    public boolean isSponsored () {
      return sponsoredMessage != null;
    }

    public boolean needRefreshInteractionInfo () {
      if (message != null && message.sendingState == null) {
        return BitwiseUtils.hasFlag(flags, Flags.REFRESH_INTERACTION_INFO);
      }
      return false;
    }

    public boolean needRestrictScreenshots () {
      return message != null && !message.canBeSaved;
    }

    public boolean needScreenshotNotification () {
      if (message == null || needRestrictScreenshots()) {
        return false;
      }
      if (BitwiseUtils.hasFlag(flags, Flags.NO_SCREENSHOT_NOTIFICATION)) {
        return false;
      }
      if (BitwiseUtils.hasFlag(flags, Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION) && TD.isScreenshotSensitive(message)) {
        return false;
      }
      return true;
    }
  }

  private static class ChatState {
    LongSet visibleMessageIds = new LongSet();
    LongSet visibleProtectedMessageIds = new LongSet();
    LongSet refreshMessageIds = new LongSet();
    boolean haveRecentlyViewedMessages;
    int maxRefreshDate;
  }

  public static class VisibleChat implements Destroyable {
    private final Viewport viewport;
    public final long chatId;
    public final LongSparseArray<VisibleMessage> visibleMessages = new LongSparseArray<>();
    private final ChatState state = new ChatState();
    private final VisibilityState visibility = new VisibilityState(false);

    public VisibleChat (Viewport viewport, long chatId) {
      this.viewport = viewport;
      this.chatId = chatId;
    }

    private void assertChat (long chatId) {
      if (this.chatId != chatId) {
        throw new IllegalArgumentException();
      }
    }

    public long[] getMessageIds (boolean onlyRecentlyViewed, boolean updateRecentState) {
      if (onlyRecentlyViewed) {
        LongSet messageIdsSet = new LongSet();
        for (int index = 0; index < visibleMessages.size(); index++) {
          VisibleMessage visibleMessage = visibleMessages.valueAt(index);
          if (visibleMessage.isRecentlyViewed) {
            messageIdsSet.add(visibleMessage.getMessageId());
            if (updateRecentState) {
              visibleMessage.isRecentlyViewed = false;
            }
          }
        }
        if (updateRecentState) {
          state.haveRecentlyViewedMessages = false;
        }
        return messageIdsSet.toArray();
      } else {
        return state.visibleMessageIds.toArray();
      }
    }

    public @Nullable VisibleMessage find (long messageId) {
      return visibleMessages.get(messageId);
    }

    /**
     * @return True, if flags were changed or message wasn't previously visible
     */
    public boolean addVisibleMessage (TdApi.Object rawMessage, @Flags long flags, long viewId) {
      VisibleMessage visibleMessage;
      boolean isSponsored = rawMessage.getConstructor() == TdApi.SponsoredMessage.CONSTRUCTOR;
      if (isSponsored) {
        TdApi.SponsoredMessage sponsoredMessage = (TdApi.SponsoredMessage) rawMessage;
        visibleMessage = find(sponsoredMessage.messageId);
      } else {
        TdApi.Message message = (TdApi.Message) rawMessage;
        assertChat(message.chatId);
        visibleMessage = find(message.id);
      }
      if (visibleMessage != null) {
        visibleMessage.viewId = viewId;
        if (visibleMessage.flags != flags || visibleMessage.visibility.isHidden()) {
          long oldFlags = visibleMessage.flags;
          visibleMessage.flags = flags;
          visibleMessage.isRecentlyViewed = true;
          if (visibleMessage.visibility.markAsVisible()) {
            trackMessage(visibleMessage, true);
          } else if (oldFlags != flags) {
            handleFlagsChange(visibleMessage, oldFlags);
          }
          return true;
        }
        return false;
      }
      if (isSponsored) {
        visibleMessage = new VisibleMessage(chatId, (TdApi.SponsoredMessage) rawMessage, flags, viewId);
      } else {
        visibleMessage = new VisibleMessage((TdApi.Message) rawMessage, flags, viewId);
      }
      visibleMessage.isRecentlyViewed = true;
      trackMessage(visibleMessage, true);
      return true;
    }

    @Nullable
    public VisibleMessage removeVisibleMessage (long chatId, long messageId) {
      assertChat(chatId);
      VisibleMessage visibleMessage = find(messageId);
      if (visibleMessage != null && visibleMessage.visibility.markAsHidden()) {
        trackMessage(visibleMessage, false);
        return visibleMessage;
      }
      return null;
    }

    public boolean removeOtherVisibleMessages (LongSet messageIds) {
      int hiddenMessageCount = 0;
      for (int index = visibleMessages.size() - 1; index >= 0; index--) {
        VisibleMessage visibleMessage = visibleMessages.valueAt(index);
        if ((messageIds == null || !messageIds.has(visibleMessage.getMessageId())) && visibleMessage.visibility.markAsHidden()) {
          trackMessage(visibleMessage, false);
          hiddenMessageCount++;
        }
      }
      return hiddenMessageCount > 0;
    }

    public boolean removeOtherVisibleMessagesByViewId (long viewId) {
      int hiddenMessageCount = 0;
      for (int index = visibleMessages.size() - 1; index >= 0; index--) {
        VisibleMessage visibleMessage = visibleMessages.valueAt(index);
        if (visibleMessage.viewId != viewId && visibleMessage.visibility.markAsHidden()) {
          trackMessage(visibleMessage, false);
          hiddenMessageCount++;
        }
      }
      return hiddenMessageCount > 0;
    }

    public boolean clear () {
      return removeOtherVisibleMessages(null);
    }

    public boolean isEmpty () {
      return state.visibleMessageIds.isEmpty();
    }

    private void trackMessage (VisibleMessage visibleMessage, boolean isVisible) {
      final Long messageId = visibleMessage.getMessageId();
      if (isVisible) {
        if (!state.visibleMessageIds.add(messageId))
          throw new IllegalStateException();
        if (visibleMessage.needRestrictScreenshots()) {
          if (!state.visibleProtectedMessageIds.add(messageId))
            throw new IllegalStateException();
        }
        visibleMessages.put(visibleMessage.getMessageId(), visibleMessage);
        if (visibleMessage.needRefreshInteractionInfo() && state.refreshMessageIds.add(visibleMessage.getMessageId())) {
          checkRefreshInteractionInfo();
        }
      } else {
        if (!state.visibleMessageIds.remove(messageId))
          throw new IllegalStateException();
        state.visibleProtectedMessageIds.remove(messageId);
        visibleMessages.remove(visibleMessage.getMessageId());
        viewport.trackRecentlyViewedMessage(this, visibleMessage);
        if (state.refreshMessageIds.remove(messageId)) {
          checkRefreshInteractionInfo();
        }
      }
    }

    private void handleFlagsChange (VisibleMessage visibleMessage, long oldFlags) {
      if (BitwiseUtils.flagChanged(visibleMessage.flags, oldFlags, Flags.REFRESH_INTERACTION_INFO)) {
        boolean needRefreshInteractionInfo = visibleMessage.needRefreshInteractionInfo();
        boolean needCheck;
        if (needRefreshInteractionInfo) {
          needCheck = state.refreshMessageIds.add(visibleMessage.getMessageId());
        } else {
          needCheck = state.refreshMessageIds.remove(visibleMessage.getMessageId());
        }
        if (needCheck) {
          checkRefreshInteractionInfo();
        }
      }
    }

    private void checkRefreshInteractionInfo () {
      boolean needRefreshInteractionInfo = state.refreshMessageIds.size() > 0;
      long maxMessageId = needRefreshInteractionInfo ? state.refreshMessageIds.max() : 0;
      int maxDate;
      if (needRefreshInteractionInfo) {
        VisibleMessage visibleMessage = find(maxMessageId);
        if (visibleMessage == null)
          throw new IllegalStateException();
        maxDate = visibleMessage.getMessageDate();
      } else {
        maxDate = 0;
      }
      if (state.maxRefreshDate != maxDate) {
        cancelRefresh();
        state.maxRefreshDate = maxDate;
        if (maxDate != 0) {
          long refreshTimeout = timeTillNextRefresh(viewport.context.tdlib.currentTimeMillis() - TimeUnit.SECONDS.toMillis(maxDate));
          scheduleRefresh(refreshTimeout);
        }
      }
    }

    private final Runnable refreshAct = this::refreshInteractionInfo;

    private void cancelRefresh () {
      if (state.maxRefreshDate != 0) {
        viewport.context.tdlib.ui().removeCallbacks(refreshAct);
        state.maxRefreshDate = 0;
      }
    }

    private void scheduleRefresh (long timeoutMs) {
      viewport.context.tdlib.ui().postDelayed(refreshAct, timeoutMs);
    }

    private void refreshInteractionInfo () {
      if (!state.refreshMessageIds.isEmpty() && !viewport.isDestroyed() && !viewport.needIgnore()) {
        if (BuildConfig.DEBUG) {
          UI.showToast("refresh views for" + state.refreshMessageIds.size() + " message(s)", Toast.LENGTH_SHORT);
        }
        viewport.refreshMessageInteractionInfo(chatId, state.refreshMessageIds.toArray(), null);
      }
    }

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

    @Override
    public void performDestroy () {
      cancelRefresh();
    }
  }

  private static class ViewportState {
    LongSet visibleChatIds = new LongSet();
    LongSet visibleProtectedChatIds = new LongSet();
    boolean needRestrictScreenshots;
    boolean isDestroyed;
  }

  public static class Viewport implements Destroyable {
    private TdlibMessageViewer context;

    public final TdApi.MessageSource messageSource;
    public final List<VisibleChat> visibleChats = new ArrayList<>();
    private final ViewportState state = new ViewportState();
    private final List<FutureBool> ignoreLocks = new ArrayList<>();
    private final List<Runnable> destroyListeners = new ArrayList<>();
    private final ChatListener chatListener = new ChatListener() {
      @Override
      public void onChatHasProtectedContentChanged (long chatId, boolean hasProtectedContent) {
        context.tdlib.ui().post(() -> {
          if (state.visibleChatIds.has(chatId) && state.visibleProtectedChatIds.add(chatId)) {
            context.checkNeedRestrictScreenshots();
          }
        });
      }
    };
    private final MessageListener messageListener = new MessageListener() {
      @Override
      public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
        runOnUiThreadOptional(() ->
          replaceMessage(message, oldMessageId, null)
        );
      }

      @Override
      public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
        runOnUiThreadOptional(() ->
          replaceMessage(message, oldMessageId, error)
        );
      }
    };

    private void runOnUiThreadOptional (Runnable act) {
      context.tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          act.run();
        }
      });
    }

    public Viewport (TdlibMessageViewer context, TdApi.MessageSource messageSource) {
      this.context = context;
      this.messageSource = messageSource;
    }

    public void addIgnoreLock (FutureBool act) {
      this.ignoreLocks.add(act);
    }

    public void addOnDestroyListener (Runnable act) {
      destroyListeners.add(act);
    }

    public void notifyLockValueChanged () {
      context.checkNeedRestrictScreenshots();
    }

    private boolean needIgnore () {
      if (!ignoreLocks.isEmpty()) {
        for (FutureBool ignoreLock : ignoreLocks) {
          if (ignoreLock.getBoolValue()) {
            return true;
          }
        }
      }
      return false;
    }

    public int indexOf (long chatId) {
      int index = 0;
      for (VisibleChat visibleChat : visibleChats) {
        if (visibleChat.chatId == chatId) {
          return index;
        }
        index++;
      }
      return -1;
    }

    public @Nullable VisibleChat find (long chatId) {
      int index = indexOf(chatId);
      return index != -1 ? visibleChats.get(index) : null;
    }

    private boolean replaceMessage (TdApi.Message message, long oldMessageId, @Nullable TdApi.Error error) {
      if (isDestroyed()) {
        return false;
      }
      VisibleChat visibleChat = find(message.chatId);
      if (visibleChat == null) {
        return false;
      }
      VisibleMessage visibleMessage = visibleChat.removeVisibleMessage(message.chatId, oldMessageId);
      if (visibleMessage != null) {
        return addVisibleMessage(message, visibleMessage.flags, visibleMessage.viewId);
      }
      return false;
    }

    public boolean addVisibleMessage (TdApi.Message message, @Flags long flags, long viewId) {
      return addVisibleMessageImpl(message.chatId, message, flags, viewId);
    }

    public boolean addVisibleMessage (long chatId, TdApi.SponsoredMessage sponsoredMessage, @Flags long flags, long viewId) {
      return addVisibleMessageImpl(chatId, sponsoredMessage, flags, viewId);
    }

    private boolean addVisibleMessageImpl (long chatId, TdApi.Object rawMessage, @Flags long flags, long viewId) {
      if (isDestroyed()) {
        return false;
      }
      VisibleChat visibleChat = find(chatId);
      if (visibleChat == null) {
        visibleChat = new VisibleChat(this, chatId);
      }
      if (visibleChat.addVisibleMessage(rawMessage, flags, viewId)) {
        if (visibleChat.visibility.markAsVisible()) {
          trackChat(visibleChat, true);
        }
        visibleChat.state.haveRecentlyViewedMessages = true;
        updateState();
        return true;
      }
      return false;
    }

    public boolean removeVisibleMessage (long chatId, long messageId) {
      VisibleChat visibleChat = find(chatId);
      if (visibleChat != null && visibleChat.removeVisibleMessage(chatId, messageId) != null) {
        if (visibleChat.isEmpty() && visibleChat.visibility.markAsHidden()) {
          trackChat(visibleChat, false);
        }
        updateState();
        return true;
      }
      return false;
    }

    public boolean clear () {
      return removeOtherVisibleChats(null);
    }

    @Override
    public void performDestroy () {
      clear();
      if (!state.isDestroyed) {
        state.isDestroyed = true;
        for (Runnable runnable : destroyListeners) {
          runnable.run();
        }
      }
      context.viewports.remove(this);
    }

    public boolean removeOtherVisibleChats (@Nullable LongSet visibleChatIds) {
      int clearedChatsCount = 0;
      for (int i = visibleChats.size() - 1; i >= 0; i--) {
        VisibleChat visibleChat = visibleChats.get(i);
        if ((visibleChatIds == null || !visibleChatIds.has(visibleChat.chatId)) && visibleChat.clear()) {
          if (visibleChat.isEmpty() && visibleChat.visibility.markAsHidden()) {
            trackChat(visibleChat, false);
          }
          clearedChatsCount++;
        }
      }
      if (clearedChatsCount > 0) {
        updateState();
        return true;
      }
      return false;
    }

    public boolean removeOtherVisibleMessages (long chatId, LongSet messageIds) {
      VisibleChat visibleChat = find(chatId);
      if (visibleChat != null && visibleChat.removeOtherVisibleMessages(messageIds)) {
        if (visibleChat.isEmpty() && visibleChat.visibility.markAsHidden()) {
          trackChat(visibleChat, false);
        }
        updateState();
        return true;
      }
      return false;
    }

    public boolean removeOtherVisibleMessagesByViewId (long viewId) {
      int updatedChatsCount = 0;
      for (int index = visibleChats.size() - 1; index >= 0; index--) {
        VisibleChat visibleChat = visibleChats.get(index);
        if (visibleChat.removeOtherVisibleMessagesByViewId(viewId)) {
          if (visibleChat.isEmpty() && visibleChat.visibility.markAsHidden()) {
            trackChat(visibleChat, false);
          }
          updatedChatsCount++;
        }
      }
      if (updatedChatsCount > 0) {
        updateState();
        return true;
      }
      return false;
    }

    private boolean checkNeedRestrictScreenshots () {
      if (!state.visibleProtectedChatIds.isEmpty()) {
        return true;
      }
      for (VisibleChat visibleChat : visibleChats) {
        if (!visibleChat.state.visibleProtectedMessageIds.isEmpty()) {
          return true;
        }
      }
      return false;
    }

    public boolean needRestrictScreenshots () {
      return state.needRestrictScreenshots && !needIgnore();
    }

    private void updateState () {
      boolean needRestrictScreenshots = checkNeedRestrictScreenshots();
      if (state.needRestrictScreenshots != needRestrictScreenshots) {
        state.needRestrictScreenshots = needRestrictScreenshots;
        context.onViewportNeedRestrictScreenshotsChanged(this, needRestrictScreenshots);
      }
    }

    private void trackChat (VisibleChat visibleChat, boolean isVisible) {
      if (isVisible) {
        if (!state.visibleChatIds.add(visibleChat.chatId))
          throw new IllegalStateException();
        boolean hasProtectedContent = ChatId.isSecret(visibleChat.chatId);
        if (!hasProtectedContent) {
          TdApi.Chat chat = context.tdlib.chatStrict(visibleChat.chatId);
          hasProtectedContent = chat.hasProtectedContent;
        }
        if (hasProtectedContent) {
          if (!state.visibleProtectedChatIds.add(visibleChat.chatId))
            throw new IllegalStateException();
        }
        visibleChats.add(visibleChat);
        context.tdlib.listeners().subscribeToChatUpdates(visibleChat.chatId, chatListener);
        context.tdlib.listeners().subscribeToMessageUpdates(visibleChat.chatId, messageListener);
      } else {
        if (!state.visibleChatIds.remove(visibleChat.chatId))
          throw new IllegalStateException();
        state.visibleProtectedChatIds.remove(visibleChat.chatId);
        visibleChats.remove(visibleChat);
        visibleChat.performDestroy();
        context.tdlib.listeners().unsubscribeFromChatUpdates(visibleChat.chatId, chatListener);
        context.tdlib.listeners().unsubscribeFromMessageUpdates(visibleChat.chatId, messageListener);
      }
    }

    private void trackRecentlyViewedMessage (VisibleChat visibleChat, VisibleMessage visibleMessage) {
      if (!isDestroyed()) {
        context.trackRecentlyViewedMessage(this, visibleChat, visibleMessage);
      }
    }

    private void viewMessagesImpl (long chatId, long[] messageIds, TdApi.MessageSource messageSource, boolean forceRead, @Nullable RunnableBool after) {
      if (messageIds.length > 0) {
        context.tdlib.send(new TdApi.ViewMessages(chatId, messageIds, messageSource, forceRead), (ok, error) -> {
          if (after != null) {
            after.runWithBool(error == null);
          }
          if (error != null) {
            TDLib.w("Unable to view %d messages in chat %d, source: %s, error: %s", messageIds.length, chatId, messageSource, TD.toErrorString(error));
          } else if (BuildConfig.DEBUG) {
            Log.i("Viewed %d messages in chat %d, source: %s", messageIds.length, chatId, messageSource);
          }
        });
      }
    }

    public void refreshMessageInteractionInfo (long chatId, long[] messageIds, @Nullable RunnableBool after) {
      if (isDestroyed()) {
        return;
      }
      TdApi.MessageSource messageSource;
      switch (this.messageSource.getConstructor()) {
        case TdApi.MessageSourceHistoryPreview.CONSTRUCTOR:
        case TdApi.MessageSourceSearch.CONSTRUCTOR:
        case TdApi.MessageSourceChatList.CONSTRUCTOR:
        case TdApi.MessageSourceChatEventLog.CONSTRUCTOR:
          messageSource = this.messageSource;
          break;
        default:
          Td.assertMessageSource_eeb3e95();
          messageSource = new TdApi.MessageSourceHistoryPreview();
          break;
      }
      viewMessagesImpl(chatId, messageIds, messageSource, false, after);
    }

    public boolean isDestroyed () {
      return state.isDestroyed;
    }

    public boolean haveRecentlyViewedMessages () {
      if (isDestroyed()) {
        return false;
      }
      for (VisibleChat visibleChat : visibleChats) {
        if (visibleChat.state.haveRecentlyViewedMessages) {
          return true;
        }
      }
      return false;
    }

    public void viewMessages (boolean onlyRecent, boolean forceRead, @Nullable RunnableBool after) {
      if (isDestroyed()) {
        return;
      }
      for (VisibleChat visibleChat : visibleChats) {
        if (onlyRecent && !visibleChat.state.haveRecentlyViewedMessages) {
          continue;
        }
        final long chatId = visibleChat.chatId;
        final long[] messageIds = visibleChat.getMessageIds(true, true);
        viewMessagesImpl(chatId, messageIds, messageSource, forceRead, after);
      }
    }
  }

  public interface Listener {
    void onNeedRestrictScreenshots (TdlibMessageViewer manager, boolean needRestrictScreenshots);
  }

  private final Tdlib tdlib;
  private final Set<Viewport> restrictScreenshotsReasons = new HashSet<>();
  private final ReferenceList<Listener> listeners = new ReferenceList<>(true);

  public TdlibMessageViewer (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public void addListener (Listener listener) {
    listeners.add(listener);
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private final ViewController.AttachListener attachListener = (context, navigation, isAttached) ->
    checkNeedRestrictScreenshots();

  private final List<Viewport> viewports = new ArrayList<>();

  public Viewport createViewport (TdApi.MessageSource source, @Nullable ViewController<?> controller) {
    Viewport viewport = new Viewport(this, source);
    if (controller != null) {
      ViewController<?> target = controller.getParentOrSelf();
      viewport.addIgnoreLock(() ->
        !target.getParentOrSelf().getAttachState()
      );
      target.addDisallowScreenshotReason(viewport::needRestrictScreenshots);
      target.addAttachStateListener(attachListener);
      target.addDestroyListener(() -> target.removeAttachStateListener(attachListener));
    }
    viewports.add(viewport);
    return viewport;
  }

  private boolean needRestrictScreenshots;

  private void checkNeedRestrictScreenshots () {
    boolean needRestrictScreenshots = false;
    for (Viewport viewport : restrictScreenshotsReasons) {
      if (viewport.needRestrictScreenshots()) {
        needRestrictScreenshots = true;
        break;
      }
    }
    if (this.needRestrictScreenshots != needRestrictScreenshots) {
      this.needRestrictScreenshots = needRestrictScreenshots;
      if (BuildConfig.DEBUG) {
        UI.showToast("update restrictScreenshots to " + needRestrictScreenshots, Toast.LENGTH_SHORT);
      }
      for (Listener listener : listeners) {
        listener.onNeedRestrictScreenshots(this, needRestrictScreenshots);
      }
    }
  }

  public boolean needRestrictScreenshots () {
    return needRestrictScreenshots;
  }

  private void onViewportNeedRestrictScreenshotsChanged (@NonNull Viewport viewport, boolean needRestrictScreenshots) {
    if (needRestrictScreenshots) {
      restrictScreenshotsReasons.add(viewport);
    } else {
      restrictScreenshotsReasons.remove(viewport);
    }
    checkNeedRestrictScreenshots();
  }

  private final Set<VisibleMessage> recentlyViewedMessages = new HashSet<>();

  private void trackRecentlyViewedMessage (Viewport viewport, VisibleChat recentlyViewedChat, VisibleMessage recentlyViewedMessage) {
    if (viewport.state.needRestrictScreenshots || !recentlyViewedMessage.needScreenshotNotification()) {
      return;
    }
    long expiresAtMs = recentlyViewedMessage.visibility.hideTimeMs + TRACK_MESSAGE_TIMEOUT_MS;
    long timeoutMs = expiresAtMs - System.currentTimeMillis();
    if (timeoutMs <= 0) {
      return;
    }
    recentlyViewedMessages.add(recentlyViewedMessage);
    tdlib.ui().postDelayed(() -> recentlyViewedMessages.remove(recentlyViewedMessage), timeoutMs);
  }

  public boolean hasPotentiallyVisibleMessages () {
    if (!recentlyViewedMessages.isEmpty()) {
      return true;
    }
    for (Viewport viewport : viewports) {
      if (viewport.state.visibleChatIds.size() > viewport.state.visibleProtectedChatIds.size()) {
        return true;
      }
      if (!viewport.state.needRestrictScreenshots) {
        return true;
      }
    }
    return false;
  }

  private LongSparseArray<LongSet> screenshotMessages = null;

  private void addScreenshotMessage (long chatId, long messageId) {
    if (screenshotMessages == null) {
      screenshotMessages = new LongSparseArray<>();
    }
    LongSet messageIds = screenshotMessages.get(chatId);
    if (messageIds == null) {
      messageIds = new LongSet();
      screenshotMessages.put(chatId, messageIds);
    }
    messageIds.add(messageId);
  }

  public void onScreenshotTaken (int timeSeconds) {
    if (screenshotMessages != null) {
      screenshotMessages.clear();
    }
    long timeMs = TimeUnit.SECONDS.toMillis(timeSeconds);
    for (VisibleMessage recentlyViewedMessage : recentlyViewedMessages) {
      if (recentlyViewedMessage.visibility.openTimeMs <= timeMs) {
        addScreenshotMessage(recentlyViewedMessage.getChatId(), recentlyViewedMessage.getMessageId());
      }
    }
    for (Viewport viewport : viewports) {
      for (VisibleChat visibleChat : viewport.visibleChats) {
        if (visibleChat.visibility.openTimeMs > timeMs)
          continue;
        for (int index = 0; index < visibleChat.visibleMessages.size(); index++) {
          VisibleMessage visibleMessage = visibleChat.visibleMessages.valueAt(index);
          if (visibleMessage.visibility.openTimeMs > timeMs)
            continue;
          if (visibleMessage.needScreenshotNotification()) {
            addScreenshotMessage(visibleMessage.getChatId(), visibleMessage.getMessageId());
          }
        }
      }
    }
    if (!screenshotMessages.isEmpty()) {
      for (int i = 0; i < screenshotMessages.size(); i++) {
        long chatId = screenshotMessages.keyAt(i);
        long[] messageIds = screenshotMessages.valueAt(i).toArray();
        tdlib.send(new TdApi.ViewMessages(chatId, messageIds, new TdApi.MessageSourceScreenshot(), false), (ok, error) -> {
          if (error != null) {
            TDLib.w("Error notifying about screenshot of %d messages in chat %d: %s", messageIds.length, chatId, TD.toErrorString(error));
          }
        });
      }
      screenshotMessages.clear();
    }
  }
}
