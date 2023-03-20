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
 * File created on 20/02/2018
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.voip.gui.CallSettings;

import me.vkryl.core.reference.ReferenceList;

public class TdlibListenersGlobal {
  private final TdlibManager context;

  private final ReferenceList<GlobalConnectionListener> connectionListeners = new ReferenceList<>(true);
  private final ReferenceList<GlobalMessageListener> messageListeners = new ReferenceList<>(true);
  private final ReferenceList<GlobalAccountListener> accountListeners = new ReferenceList<>(true);
  private final ReferenceList<GlobalCallListener> callListeners = new ReferenceList<>(true);
  private final ReferenceList<GlobalCountersListener> countersListeners = new ReferenceList<>();
  private final ReferenceList<GlobalTokenStateListener> tokenStateListeners = new ReferenceList<>(true);
  private final ReferenceList<GlobalProxyPingListener> proxyPingListeners = new ReferenceList<>();

  /*package*/ TdlibListenersGlobal (TdlibManager context) {
    this.context = context;
  }

  // Counters

  public void addCountersListener (GlobalCountersListener listener) {
    countersListeners.add(listener);
  }

  public void removeCountersListener (GlobalCountersListener listener) {
    countersListeners.remove(listener);
  }

  // Token State

  public void addTokenStateListener (GlobalTokenStateListener listener) {
    tokenStateListeners.add(listener);
  }

  public void removeTokenStateListener (GlobalTokenStateListener listener) {
    tokenStateListeners.remove(listener);
  }

  void notifyTokenStateChanged (int newState, @Nullable String error, @Nullable Throwable fullError) {
    for (GlobalTokenStateListener listener : tokenStateListeners) {
      listener.onTokenStateChanged(newState, error, fullError);
    }
  }

  // Messages

  public void addMessageListener (GlobalMessageListener listener) {
    messageListeners.add(listener);
  }

  public void removeMessageListener (GlobalMessageListener listener) {
    messageListeners.remove(listener);
  }

  void notifyUpdateNewMessage (Tdlib tdlib, TdApi.UpdateNewMessage update) {
    for (GlobalMessageListener listener : messageListeners) {
      listener.onNewMessage(tdlib, update.message);
    }
  }

  void notifyUpdateNewMessages (Tdlib tdlib, TdApi.Message[] messages) {
    for (GlobalMessageListener listener : messageListeners) {
      listener.onNewMessages(tdlib, messages);
    }
  }

  void notifyUpdateMessageSendSucceeded (Tdlib tdlib, TdApi.UpdateMessageSendSucceeded update) {
    for (GlobalMessageListener listener : messageListeners) {
      listener.onMessageSendSucceeded(tdlib, update.message, update.oldMessageId);
    }
  }

  void notifyUpdateMessageSendFailed (Tdlib tdlib, TdApi.UpdateMessageSendFailed update) {
    for (GlobalMessageListener listener : messageListeners) {
      listener.onMessageSendFailed(tdlib, update.message, update.oldMessageId, update.errorCode, update.errorMessage);
    }
  }

  void notifyUpdateMessagesDeleted (Tdlib tdlib, TdApi.UpdateDeleteMessages update) {
    for (GlobalMessageListener listener : messageListeners) {
      listener.onMessagesDeleted(tdlib, update.chatId, update.messageIds);
    }
  }

  // Counters

  void notifyCountersChanged (Tdlib tdlib, @NonNull TdApi.ChatList chatList, int count, boolean isMuted) {
    for (GlobalCountersListener listener : countersListeners) {
      listener.onUnreadCountersChanged(tdlib, chatList, count, isMuted);
    }
  }

  void notifyTotalCounterChanged (@NonNull TdApi.ChatList chatList, boolean isReset) {
    for (GlobalCountersListener listener : countersListeners) {
      listener.onTotalUnreadCounterChanged(chatList, isReset);
    }
  }

  // Self user

  /*public void addSelfUserListener (GlobalSelfUserListener listener) {
    selfListeners.add(listener);
  }

  public void removeSelfUserListener (GlobalSelfUserListener listener) {
    selfListeners.remove(listener);
  }

  void notifySelfUserChanged (@Nullable Tdlib tdlib, @Nullable TdApi.User user) {
    for (GlobalSelfUserListener listener : selfListeners) {
      listener.onSelfUserChanged(tdlib, user);
    }
  }*/

  // Connection

  public void addConnectionListener (GlobalConnectionListener listener) {
    connectionListeners.add(listener);
  }

  public void removeConnectionListener (GlobalConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  void notifySystemDataSaverStateChanged (boolean isEnabled) {
    for (GlobalConnectionListener listener : connectionListeners) {
      listener.onSystemDataSaverStateChanged(isEnabled);
    }
  }

  void notifyConnectionTypeChanged (int oldType, int newType) {
    for (GlobalConnectionListener listener : connectionListeners) {
      listener.onConnectionTypeChanged(oldType, newType);
    }
  }

  void notifyConnectionStateChanged (Tdlib tdlib, @ConnectionState int newState, boolean isCurrent) {
    for (GlobalConnectionListener listener : connectionListeners) {
      listener.onConnectionStateChanged(tdlib, newState, isCurrent);
    }
  }

  void notifyConnectionDisplayStatusChanged (Tdlib tdlib, boolean isCurrent) {
    for (GlobalConnectionListener listener : connectionListeners) {
      listener.onConnectionDisplayStatusChanged(tdlib, isCurrent);
    }
  }

  // Proxy

  public void addProxyListener (GlobalProxyPingListener listener) {
    proxyPingListeners.add(listener);
  }

  public void removeProxyListener (GlobalProxyPingListener listener) {
    proxyPingListeners.remove(listener);
  }

  void notifyProxyPingChanged (@NonNull Settings.Proxy proxy, long pingMs) {
    for (GlobalProxyPingListener listener : proxyPingListeners) {
      listener.onProxyPingChanged(proxy, pingMs);
    }
  }

  // Account

  public void addAccountListener (GlobalAccountListener listener) {
    accountListeners.add(listener);
  }

  public void removeAccountListener (GlobalAccountListener listener) {
    accountListeners.remove(listener);
  }

  void notifyAccountSwitched (TdlibAccount account, @Nullable TdApi.User profile, @AccountSwitchReason int reason, @Nullable TdlibAccount oldAccount) {
    for (GlobalAccountListener listener : accountListeners) {
      listener.onAccountSwitched(account, profile, reason, oldAccount);
    }
  }

  void notifyAccountProfileChanged (TdlibAccount account, @Nullable TdApi.User profile, boolean isCurrent, boolean isLoaded) {
    for (GlobalAccountListener listener : accountListeners) {
      listener.onAccountProfileChanged(account, profile, isCurrent, isLoaded);
    }
  }

  void notifyAccountProfilePhotoChanged (TdlibAccount account, boolean big, boolean isCurrent) {
    for (GlobalAccountListener listener : accountListeners) {
      listener.onAccountProfilePhotoChanged(account, big, isCurrent);
    }
  }

  void notifyAuthorizationStateChanged (TdlibAccount account, TdApi.AuthorizationState authorizationState, int status) {
    for (GlobalAccountListener listener : accountListeners) {
      listener.onAuthorizationStateChanged(account, authorizationState, status);
    }
  }

  void notifyOptimizing (Tdlib tdlib, boolean isOptimizing) {
    for (GlobalAccountListener listener : accountListeners) {
      listener.onTdlibOptimizing(tdlib, isOptimizing);
    }
  }

  void notifyAccountAddedOrRemoved (TdlibAccount account, int position, boolean isAdded) {
    if (isAdded) {
      for (GlobalAccountListener listener : accountListeners) {
        listener.onActiveAccountAdded(account, position);
      }
    } else {
      for (GlobalAccountListener listener : accountListeners) {
        listener.onActiveAccountRemoved(account, position);
      }
    }
  }

  void notifyAccountMoved (TdlibAccount account, int fromPosition, int toPosition) {
    for (GlobalAccountListener listener : accountListeners) {
      listener.onActiveAccountMoved(account, fromPosition, toPosition);
    }
  }

  // Calls

  public void addCallListener (GlobalCallListener listener) {
    callListeners.add(listener);
  }

  public void removeCallListener (GlobalCallListener listener) {
    callListeners.remove(listener);
  }

  void notifyCallUpdated (Tdlib tdlib, TdApi.Call call) {
    for (GlobalCallListener listener : callListeners) {
      listener.onCallUpdated(tdlib, call);
    }
  }

  void notifyCallSettingsChanged (Tdlib tdlib, int callId, CallSettings settings) {
    for (GlobalCallListener listener : callListeners) {
      listener.onCallSettingsChanged(tdlib, callId, settings);
    }
  }
}
