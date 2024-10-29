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
 * File created on 14/02/2018
 */
package org.thunderdog.challegram.telegram;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.core.WatchDog;
import org.thunderdog.challegram.core.WatchDogContext;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.player.AudioController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.AppBuildInfo;
import org.thunderdog.challegram.util.Crash;
import org.thunderdog.challegram.util.DeviceStorageError;
import org.thunderdog.challegram.util.TokenRetriever;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.leolin.shortcutbadger.ShortcutBadger;
import me.vkryl.android.LocaleUtils;
import me.vkryl.android.SdkVersion;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.util.FilteredIterator;
import tgx.td.JSON;
import tgx.td.Td;

public class TdlibManager implements Iterable<TdlibAccount>, UI.StateListener {
  // Util

  public static final int SYNC_CAUSE_WORK_MANAGER = 0;
  public static final int SYNC_CAUSE_BOOT = 1;
  public static final int SYNC_CAUSE_SYSTEM_SYNC = 2;
  public static final int SYNC_CAUSE_DELETED_MESSAGES = 3;

  public static boolean makeSync (Context context, int accountId, int cause, long causePushId, boolean sync, long timeout) {
    final long ms = SystemClock.uptimeMillis();
    UI.initApp(context);
    final long taskId = causePushId == 0 ? Settings.instance().newPushId() : causePushId;
    final AtomicBoolean success = sync ? new AtomicBoolean(false) : null;
    final CountDownLatch latch = sync ? new CountDownLatch(1) : null;
    if (accountId == TdlibAccount.NO_ID) {
      TDLib.Tag.notifications(taskId, accountId, "Performing sync for all accounts, cause: %d, synchronized: %b, timeout: %d, initialized in: %d", cause, sync, timeout, SystemClock.uptimeMillis() - ms);
    } else {
      TDLib.Tag.notifications(taskId, accountId, "Performing sync for account, cause: %d, synchronized: %b, timeout: %d, initialized in: %d", cause, sync, timeout, SystemClock.uptimeMillis() - ms);
    }
    TdlibManager.instanceForAccountId(accountId).sync(taskId, accountId, () -> {
      if (sync) {
        synchronized (success) {
          success.set(true);
          latch.countDown();
        }
      }
      TDLib.Tag.notifications(taskId, accountId, "Finished sync in %dms", SystemClock.uptimeMillis() - ms);
    }, true, true, Config.MAX_RUNNING_TDLIBS, null);
    if (sync) {
      try {
        if (timeout > 0) {
          latch.await(timeout, TimeUnit.MILLISECONDS);
        } else {
          latch.await();
        }
      } catch (InterruptedException e) {
        TDLib.Tag.notifications(taskId, accountId, "Sync was interrupted, elapsed: %dms", SystemClock.uptimeMillis() - ms);
      }
      synchronized (success) {
        return success.get();
      }
    } else {
      return true;
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ExternalAction.MARK_AS_HIDDEN,
    ExternalAction.MARK_ALL_AS_HIDDEN,
    ExternalAction.MARK_AS_READ,
    ExternalAction.MUTE,
    ExternalAction.UNMUTE
  })
  public @interface ExternalAction {
    int MARK_AS_HIDDEN = 0,
      MARK_ALL_AS_HIDDEN = 1,
      MARK_AS_READ = 2,
      MUTE = 3,
      UNMUTE = 4;
  }

  private interface NotificationTask {
    void onPerformTask (Tdlib tdlib, Runnable onDone);
  }

  private static void performSyncTask (Context context, int accountId, String tag, @NonNull NotificationTask task, @Nullable Filter<TdlibAccount> filter) {
    long startTimeMs = SystemClock.uptimeMillis();
    UI.initApp(context);
    TdlibManager manager = TdlibManager.instanceForAccountId(accountId);
    manager.runWithLatch(latch -> {
      Runnable after = () -> {
        if (latch != null)
          latch.countDown();
        Log.i(Log.TAG_ACCOUNTS, "[TASK] END %dms, accountId:%d, isBackground:%b, tag:%s", SystemClock.uptimeMillis() - startTimeMs, accountId, latch != null, tag);
      };
      manager.performTask(accountId, (account, onDone) -> {
        account.tdlib().incrementNotificationReferenceCount();
        account.tdlib().awaitNotificationInitialization(() ->
          task.onPerformTask(account.tdlib(), () -> {
            if (onDone != null)
              onDone.run();
            account.tdlib().decrementNotificationReferenceCount();
          })
        );
      }, Config.MAX_RUNNING_TDLIBS, filter, after);
      if (latch != null)
        U.awaitLatch(latch);
    });
  }

  public static void performExternalAction (Context context, int action, TdlibNotificationExtras extras) {
    if (extras == null)
      return;
    performSyncTask(context, extras.accountId, "external:" + action, (tdlib, onDone) -> {
      tdlib.incrementNotificationReferenceCount();
      switch (action) {
        case ExternalAction.MARK_AS_HIDDEN:
          tdlib.notifications().onHide(extras);
          break;
        case ExternalAction.MARK_ALL_AS_HIDDEN:
          tdlib.notifications().onHideAll(extras.category);
          break;
        case ExternalAction.MARK_AS_READ:
          extras.read(tdlib);
          break;
        case ExternalAction.MUTE:
          extras.mute(tdlib);
          break;
        case ExternalAction.UNMUTE:
          extras.unmute(tdlib);
          break;
      }
      tdlib.notifications().releaseTdlibReference(onDone);
    }, null);
  }

  public static void performExternalReply (Context context, CharSequence text, TdlibNotificationExtras extras) {
    if (extras == null || extras.messageIds == null)
      return;
    if (StringUtils.isEmpty(text))
      return;
    performSyncTask(context, extras.accountId, "reply", (tdlib, onDone) -> {
      TdApi.InputMessageReplyTo replyTo;
      if (extras.needReply) {
        long messageId = extras.messageIds[extras.messageIds.length - 1];
        if (extras.forceExternalReply) {
          replyTo = new TdApi.InputMessageReplyToExternalMessage(extras.chatId, messageId, null);
        } else {
          replyTo = new TdApi.InputMessageReplyToMessage(messageId, null);
        }
      } else {
        replyTo = null;
      }
      tdlib.sendMessage(extras.chatId, extras.messageThreadId, replyTo, Td.newSendOptions(), new TdApi.InputMessageText(new TdApi.FormattedText(text.toString(), null), null, false), sendingMessage -> {
        if (sendingMessage == null) {
          UI.showToast(R.string.NotificationReplyFailed, Toast.LENGTH_SHORT);
          if (onDone != null) {
            onDone.run();
          }
          return;
        }
        tdlib.awaitMessageSent(sendingMessage, () -> {
          extras.read(tdlib);
          if (onDone != null) {
            onDone.run();
          }
        });
      });
    }, null);
  }

  // Singleton

  private static final AtomicBoolean hasInstance = new AtomicBoolean(false);

  private static TdlibManager instance;

  private static TdlibManager instance (int firstAccountId, boolean forceService) {
    if (instance == null) {
      synchronized (TdlibManager.class) {
        if (instance == null) {
          if (hasInstance.getAndSet(true))
            throw new AssertionError();
          instance = new TdlibManager(firstAccountId, forceService);
        }
      }
    }
    return instance;
  }

  public static TdlibManager instance () {
    return instanceForAccountId(TdlibAccount.NO_ID);
  }

  public static TdlibManager instanceForAccountId (int firstAccountId) {
    return instance(firstAccountId, false);
  }

  public static TdlibManager serviceInstance () {
    return instance(TdlibAccount.NO_ID, true);
  }

  public static Tdlib getTdlib (int accountId) {
    return instanceForAccountId(accountId).tdlib(accountId);
  }

  public static Tdlib getServiceTdlib () {
    return serviceInstance().serviceTdlib();
  }

  // Handler

  private static class ManagerHandler extends Handler {
    private final TdlibManager context;

    public ManagerHandler (TdlibManager context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      context.handleUiMessage(msg);
    }
  }

  // Impl

  private final ArrayList<TdlibAccount> accounts = new ArrayList<>();

  private final Object counterLock = new Object();

  private int preferredAccountId = TdlibAccount.NO_ID;
  private TdlibAccount currentAccount;

  private final TdlibListenersGlobal global = new TdlibListenersGlobal(this);
  private final ManagerHandler handler = new ManagerHandler(this);
  private final LiveLocationManager liveLocationManager = new LiveLocationManager(this);
  private final TdlibNotificationManager.NotificationQueue notificationQueue = new TdlibNotificationManager.NotificationQueue("NotificationQueue", this);
  private final CallManager calls = new CallManager(this);
  private final Settings.ProxyChangeListener proxyChangeListener = new Settings.ProxyChangeListener() {
    @Override
    public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.InternalLinkTypeProxy proxy, String description, boolean isCurrent, boolean isNewAdd) {
      if (isCurrent) {
        for (TdlibAccount account : TdlibManager.this) {
          if (account.tdlib != null) {
            account.tdlib.setProxy(proxyId, proxy);
          }
        }
      }
    }

    @Override
    public void onProxyAvailabilityChanged (boolean isAvailable) { }

    @Override
    public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) { }
  };

  private @Nullable Crash crashInfo;
  private final WatchDogContext watchDog;

  private final String languageDatabasePath;

  private final AudioController audio;
  private final TGPlayerController player;

  private boolean hasUi;

  private @Nullable String tdlibCommitHash, tdlibVersion;

  private final DateManager dateManager = new DateManager(this);

  private TdlibManager (int firstInstanceId, boolean forceService) {
    Client.setLogMessageHandler(0, (verbosityLevel, errorMessage) -> {
      if (verbosityLevel == 0) {
        Crash.Builder b = new Crash.Builder()
          .message(StringUtils.isEmpty(errorMessage) ? "empty" : errorMessage)
          .flags(Crash.Flags.SOURCE_TDLIB | Crash.Flags.SAVE_APPLICATION_LOG_EVENT);
        Settings.instance().storeCrash(b);
      }
    });

    this.languageDatabasePath = getLanguageDatabasePath();
    this.watchDog = new WatchDogContext(UI.getAppContext(), this);

    this.player = new TGPlayerController(this);
    this.audio = new AudioController(this, player);

    this.crashInfo = Settings.instance().findRecoveryCrash();
    load(firstInstanceId, forceService);
    Settings.instance().addProxyListener(proxyChangeListener);
    notificationQueue().init();

    watchDog.register();
    watchDog.get().letsHelpDoge();

    UI.addStateListener(this);
    onUiStateChanged(UI.getUiState());

    checkDeviceToken();
    saveCrashes();
  }

  public DateManager dateManager () {
    return dateManager;
  }

  void setTdlibCommitHash (@NonNull String commitHash) {
    // called by children Tdlib instances
    this.tdlibCommitHash = commitHash;
  }

  void setTdlibVersion (@NonNull String version) {
    // called by children Tdlib instances
    this.tdlibVersion = version;
  }

  public String tdlibCommitHash () {
    if (!StringUtils.isEmpty(tdlibCommitHash)) {
      return StringUtils.limit(this.tdlibCommitHash, 7);
    }
    return Td.tdlibCommitHash();
  }

  public String tdlibCommitHashFull () {
    if (!StringUtils.isEmpty(tdlibCommitHash)) {
      return this.tdlibCommitHash;
    }
    return Td.tdlibCommitHashFull();
  }

  public String tdlibVersion () {
    if (!StringUtils.isEmpty(tdlibVersion)) {
      return this.tdlibVersion;
    }
    return Td.tdlibVersion();
  }

  private int findAccountIdByClient (Client client) {
    if (client != null) {
      for (TdlibAccount account : accounts) {
        if (account.ownsClient(client))
          return account.id;
      }
    }
    return TdlibAccount.NO_ID;
  }

  @Override
  @UiThread
  public void onUiStateChanged (int newState) {
    boolean hasUi = newState != UI.State.DESTROYED && newState != UI.State.UNKNOWN;
    if (this.hasUi != hasUi) {
      this.hasUi = hasUi;
      for (TdlibAccount account : accounts) {
        if (account.hasTdlib(true)) {
          account.tdlib().checkPauseTimeout();
        }
      }
    }
  }

  public boolean hasUi () {
    return hasUi;
  }

  public AudioController audio () {
    return audio;
  }

  public TGPlayerController player () {
    return player;
  }

  public WatchDog watchDog () {
    return watchDog.get();
  }

  public String languageDatabasePath () {
    return languageDatabasePath;
  }

  public static void setTestLabConfig () {
    try {
      Client.execute(new TdApi.SetLogVerbosityLevel(5));
      Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamDefault()));
    } catch (Client.ExecutionException ignored) { }
    Log.setLogLevel(Log.LEVEL_VERBOSE);
  }

  @Override
  protected void finalize () throws Throwable {
    watchDog.unregister();
    super.finalize();
  }

  public boolean inRecoveryMode () {
    return crashInfo != null;
  }

  public Crash getRecoveryCrashInfo () {
    return crashInfo;
  }

  public boolean exitRecoveryMode () {
    if (crashInfo != null) {
      Settings.instance().markCrashAsResolved(crashInfo);
      crashInfo = null;
      for (TdlibAccount account : accounts) {
        if (account.hasTdlib(true)) {
          account.tdlib().initializeIfWaiting();
        }
      }
      return true;
    }
    return false;
  }

  public TdlibListenersGlobal global () {
    return global;
  }

  public CallManager calls () {
    return calls;
  }

  public LiveLocationManager liveLocation () {
    return liveLocationManager;
  }

  TdlibNotificationManager.NotificationQueue notificationQueue () {
    return notificationQueue;
  }

  private void load (int specificAccountId, boolean forceService) {
    readAccountConfig();
    TdlibAccount selectedAccount = specificAccountId != TdlibAccount.NO_ID ? accounts.get(specificAccountId) : currentAccount;
    if (forceService && !selectedAccount.isService()) {
      selectedAccount = accounts.get(serviceAccountId());
    }
    final TdlibAccount firstAccount = selectedAccount;
    if (firstAccount.launch(specificAccountId != TdlibAccount.NO_ID)) {
      firstAccount.tdlib().awaitInitialization(() -> {
        for (TdlibAccount account : accounts) {
          if (account != firstAccount) {
            account.launch(false);
          }
        }
        if (Config.NEED_TDLIB_CLEANUP) {
          cleanupLoggedOutAccounts();
        }
      });
    } else {
      for (TdlibAccount account : accounts) {
        if (account != firstAccount) {
          account.launch(false);
        }
      }
      if (Config.NEED_TDLIB_CLEANUP) {
        cleanupLoggedOutAccounts();
      }
    }
  }

  // Emulator

  private boolean isEmulator;

  public void setIsEmulator (boolean isEmulator) {
    if (this.isEmulator != isEmulator) {
      this.isEmulator = isEmulator;
      for (TdlibAccount account : accounts) {
        Tdlib tdlib = account.tdlib;
        if (tdlib != null) {
          tdlib.setIsEmulator(isEmulator);
        }
      }
    }
  }

  // Client modification

  void modifyClient (Tdlib tdlib, Client client) {
    if (isEmulator) {
      client.send(new TdApi.SetOption("is_emulator", new TdApi.OptionValueBoolean(true)), tdlib.okHandler());
    }
    if (networkType != null) {
      client.send(new TdApi.SetNetworkType(networkType), tdlib.okHandler());
    } else if (Settings.instance().forceDisableNetwork()) {
      client.send(new TdApi.SetNetworkType(new TdApi.NetworkTypeNone()), tdlib.okHandler());
    }
  }

  // Counters

  public void incrementBadgeCounters (@NonNull TdApi.ChatList chatList, int unreadCountDelta, int unreadUnmutedCountDelta, boolean areChats) {
    synchronized (counterLock) {
      /*if (areChats) {
        this.totalCounter.chatCount += unreadCountDelta;
        this.totalCounter.chatUnmutedCount += unreadUnmutedCountDelta;
      } else {
        this.totalCounter.messageCount += unreadCountDelta;
        this.totalCounter.messageUnmutedCount += unreadUnmutedCountDelta;
      }*/
      boolean counterChanged = unreadCountDelta != 0 || unreadUnmutedCountDelta != 0;
      updateBadgeInternal(false, counterChanged);
      if (counterChanged) {
        dispatchUnreadCount(false);
      }
    }
  }

  private void dispatchUnreadCount (boolean isReset) {
    handler.sendMessage(Message.obtain(handler, isReset ? ACTION_RESET_UNREAD_COUNTERS : ACTION_DISPATCH_TOTAL_UNREAD_COUNT));
  }

  public TdlibBadgeCounter getTotalUnreadBadgeCounter (int excludeAccountId) {
    TdlibBadgeCounter counter = new TdlibBadgeCounter();
    if (excludeAccountId == TdlibAccount.NO_ID) {
      boolean onlyActive = Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT);
      boolean onlySelected = Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS);
      for (TdlibAccount account : this) {
        if (onlyActive && account.id != preferredAccountId)
          continue;
        if (onlySelected && !account.forceEnableNotifications())
          continue;
        counter.add(account.getUnreadBadge());
      }
    } else {
      for (TdlibAccount account : this) {
        if (account.id != excludeAccountId) {
          counter.add(account.getUnreadBadge());
        }
      }
    }
    return counter;
  }

  public TdlibBadgeCounter getTotalUnreadBadgeCounter () {
    return getTotalUnreadBadgeCounter(TdlibAccount.NO_ID);
  }

  public void resetBadge (boolean settingsChanged) {
    synchronized (counterLock) {
      updateBadgeInternal(true, false);
      dispatchUnreadCount(true);
    }
    if (settingsChanged) {
      global.notifyBadgeSettingsChanged();
    }
  }

  private boolean logged;
  private BaseThread badgeUpdaterThread;

  private void updateBadgeInternal (boolean force, boolean counterChanged) {
    if (badgeUpdaterThread == null) {
      badgeUpdaterThread = new BaseThread("ShortcutBadgerThread") {
        @Override
        protected void process (Message msg) {
          int count = msg.arg1;
          try {
            ShortcutBadger.applyCountOrThrow(UI.getAppContext(), count);
            logged = false;
          } catch (Throwable t) {
            if (!logged) {
              logged = true;
              Log.v("Could not update app badge", t);
            }
          }
        }
      };
    }
    TdlibBadgeCounter badge = getTotalUnreadBadgeCounter();
    final int count = badge.getCount();
    badgeUpdaterThread.sendMessage(Message.obtain(badgeUpdaterThread.getHandler(), 0, count, 0), 0);
  }

  public void onUpdateAllNotifications () {
    onUpdateNotifications(null, null);
  }

  public void onUpdateSecretChatNotifications () {
    onUpdateNotifications(new TdApi.NotificationSettingsScopePrivateChats(), null);
  }

  public void onUpdateNotifications (@Nullable TdApi.NotificationSettingsScope scope, @Nullable Filter<TdlibAccount> filter) {
    for (TdlibAccount account : this) {
      if ((filter == null || filter.accept(account)) && account.haveVisibleNotifications()) {
        account.tdlib().notifications().onUpdateNotifications(scope);
      }
    }
  }

  // UI

  private static final int ACTION_DISPATCH_NETWORK_STATE = 0;
  private static final int ACTION_DISPATCH_NETWORK_TYPE = 1;
  private static final int ACTION_DISPATCH_NETWORK_DATA_SAVER = 2;
  private static final int ACTION_DISPATCH_ACCOUNT_PROFILE = 4;
  private static final int ACTION_DISPATCH_ACCOUNT_PROFILE_PHOTO = 5;
  private static final int ACTION_DISPATCH_TOTAL_UNREAD_COUNT = 6;
  private static final int ACTION_RESET_UNREAD_COUNTERS = 7;
  private static final int ACTION_DISPATCH_NETWORK_DISPLAY_STATUS_CHANGED = 8;
  private static final int ACTION_DISPATCH_ACCOUNT_EMOJI_STATUS = 9;

  private void handleUiMessage (Message msg) {
    switch (msg.what) {
      case ACTION_DISPATCH_NETWORK_STATE:
        global().notifyConnectionStateChanged((Tdlib) msg.obj, msg.arg2, currentAccount.id == msg.arg1);
        break;
      case ACTION_DISPATCH_NETWORK_DISPLAY_STATUS_CHANGED:
        global().notifyConnectionDisplayStatusChanged((Tdlib) msg.obj, currentAccount.id == msg.arg1);
        break;
      case ACTION_DISPATCH_NETWORK_TYPE:
        global().notifyConnectionTypeChanged(msg.arg1, msg.arg2);
        break;
      case ACTION_DISPATCH_NETWORK_DATA_SAVER:
        global().notifySystemDataSaverStateChanged(msg.arg1 == 1);
        break;
      case ACTION_DISPATCH_ACCOUNT_PROFILE: {
        onAccountProfileChanged(account(msg.arg1), (TdApi.User) msg.obj, msg.arg1 == currentAccount.id, msg.arg2 == 1);
        break;
      }
      case ACTION_DISPATCH_ACCOUNT_PROFILE_PHOTO: {
        onAccountProfilePhotoChanged(account(msg.arg1), msg.arg2 == 1,msg.arg1 == currentAccount.id);
        break;
      }
      case ACTION_DISPATCH_ACCOUNT_EMOJI_STATUS: {
        onAccountProfileEmojiStatusChanged(account(msg.arg1), msg.arg1 == currentAccount.id);
        break;
      }
      case ACTION_DISPATCH_TOTAL_UNREAD_COUNT:
      case ACTION_RESET_UNREAD_COUNTERS: {
        global().notifyTotalCounterChanged((TdApi.ChatList) msg.obj, msg.what == ACTION_RESET_UNREAD_COUNTERS);
        break;
      }
    }
  }

  @NonNull
  @Override
  public Iterator<TdlibAccount> iterator () {
    List<TdlibAccount> accounts = new ArrayList<>(this.accounts);
    Collections.sort(accounts, (a, b) -> {
      boolean aCurrent = a == currentAccount;
      boolean bCurrent = b == currentAccount;
      if (aCurrent != bCurrent) {
        return Boolean.compare(bCurrent, aCurrent);
      }
      return a.compareTo(b);
    });
    return new FilteredIterator<>(accounts.iterator(), account -> !account.isUnauthorized() && account.tdlibInstanceMode() != Tdlib.Mode.SERVICE);
  }

  // Network type

  private TdApi.NetworkType networkType;

  public void setNetworkType (TdApi.NetworkType networkType) {
    this.networkType = networkType;
    for (TdlibAccount account : accounts) {
      if (account.hasTdlib(false)) {
        account.tdlib.setNetworkType(networkType);
      }
    }
  }

  void onConnectionStateChanged (Tdlib tdlib, @ConnectionState int newState) {
    if (newState != ConnectionState.UNKNOWN) {
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_NETWORK_STATE, tdlib.id(), newState, tdlib));
    }
  }

  void onConnectionDisplayStatusChanged (Tdlib tdlib) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_NETWORK_DISPLAY_STATUS_CHANGED, tdlib.id(), 0, tdlib));
  }

  public void onConnectionTypeChanged (int oldConnectionType, int newConnectionType) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_NETWORK_TYPE, oldConnectionType, newConnectionType));
  }

  public void onSystemDataSaverStateChanged (boolean isEnabled) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_NETWORK_DATA_SAVER, isEnabled ? 1 : 0, 0));
  }

  public void onConnectionAwake () {
    for (TdlibAccount account : accounts) {
      if (account.tdlib != null) {
        account.tdlib.resendNetworkTypeIfNeeded(networkType);
      }
    }
  }

  int getActiveAccountsNum () {
    return activeAccounts.size();
  }

  private final Comparator<TdlibAccount> lastUsageComparator = (a, b) -> a.isUnauthorized() != b.isUnauthorized() ?
    Boolean.compare(a.isUnauthorized(), b.isUnauthorized()) :
    Long.compare(b.lastUsageTime(), a.lastUsageTime());
  private List<TdlibAccount> accountsSorted;
  private int accountsModCount, accountsMod;

  void increaseModCount (TdlibAccount cause) {
    accountsModCount++;
  }

  public void setForceTdlibRestarts (boolean value) {
    Settings.instance().setForceTdlibRestart(value);
    checkPauseTimeouts(currentAccount);
  }

  void checkPauseTimeouts (TdlibAccount exclude) {
    for (TdlibAccount account : accounts) {
      if (account != exclude && account.hasTdlib(true)) {
        account.tdlib().checkPauseTimeout();
      }
    }
  }

  synchronized int getAccountUsageIndex (int accountId, long limitUsageTimeMs) {
    if (accountsSorted == null)
      accountsSorted = new ArrayList<>(activeAccounts.size());
    if (accountsSorted.size() != activeAccounts.size() || accountsMod != accountsModCount) {
      accountsSorted.clear();
      accountsSorted.addAll(activeAccounts);
      Collections.sort(accountsSorted, lastUsageComparator);
      accountsMod = accountsModCount;
    }
    TdlibAccount account = account(accountId);
    int index = accountsSorted.indexOf(account);
    if (index != -1) {
      long usageTime = account.lastUsageTime();
      long now = SystemClock.uptimeMillis();
      if (usageTime != 0 && (usageTime > now || now - usageTime <= limitUsageTimeMs))
        return index;
    }
    return -1;
  }

  // Proxy

  /*private void readProxyConfig (boolean debug) {
    this.proxy = null;
    this.migratingProxy = false;

    File file = getProxyConfigFile(debug);
    if (!file.exists()) {
      File tdlibDir = new File(getTdlibDirectory(debug, 0, false));
      if (tdlibDir.exists()) {
        migratingProxy = true;
        final boolean wasDebug = isDebug;
        account(0).tdlib().client().send(new TdApi.GetProxy(), new Client.ResultHandler() {
          @Override
          public void onResult (TdApi.Object object) {
            if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
              Log.e("Proxy get error: %s", TD.getErrorString(object));
              return;
            }
            final TdApi.Proxy proxy = (TdApi.Proxy) object;
            UI.post(new Runnable() {
              @Override
              public void run () {
                if (isDebug == wasDebug) {
                  migrateProxy(proxy);
                }
              }
            });
          }
        });
      } else {
        U.createNewFile(file);
      }
      return;
    }
    if (file.length() == 0) {
      this.proxy = new TdApi.ProxyEmpty();
      global.notifyProxyChanged(proxy);
      return;
    }
    RandomAccessFile r = null;
    try {
      r = new RandomAccessFile(file, MODE_R);
      this.proxy = readProxy(r);
    } catch (IOException e) {
      Log.e(e);
    }
    U.close(r);
    if (proxy != null) {
      global.notifyProxyChanged(proxy);
      return;
    }
    final boolean wasDebug = isDebug;
    account(0).tdlib().client().send(new TdApi.GetProxy(), new Client.ResultHandler() {
      @Override
      public void onResult (final TdApi.Object object) {
        if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          Log.e("Proxy get error: %d", TD.getErrorString(object));
          return;
        }
        UI.post(new Runnable() {
          @Override
          public void run () {
            if (isDebug == wasDebug) {
              proxy = ((TdApi.Proxy) object);
              global.notifyProxyChanged(proxy);
            }
          }
        });
      }
    });
  }*/

  /*private void migrateProxy (TdApi.Proxy proxy) {
    if (this.migratingProxy) {
      this.migratingProxy = false;
      setProxy(proxy, 0);
    }
  }*/

  /*public TdApi.Proxy proxy () {
    return proxy;
  }*/

  /*private void saveProxyConfig () {
    if (proxy == null) {
      return;
    }
    RandomAccessFile r = null;
    try {
      File file = getProxyConfigFile(isDebug);
      if (!file.exists() && !file.createNewFile()) {
        return;
      }
      r = new RandomAccessFile(file, MODE_RW);
      switch (proxy.getConstructor()) {
        case TdApi.ProxyEmpty.CONSTRUCTOR:
          r.setLength(0);
          break;
        case TdApi.ProxySocks5.CONSTRUCTOR: {
          TdApi.ProxySocks5 proxy = (TdApi.ProxySocks5) this.proxy;
          final int constructor = proxy.getConstructor();

          Blob blob = new Blob(Blob.sizeOf(constructor) + Blob.sizeOf(proxy.server, true) + Blob.sizeOf(proxy.port) + 2 + Blob.sizeOf(proxy.username, false) + Blob.sizeOf(proxy.password, false));
          blob.writeVarint(constructor);
          blob.writeString(proxy.server);
          blob.writeVarint(proxy.port);
          boolean hasUsername = !Strings.isEmpty(proxy.username);
          boolean hasPassword = !Strings.isEmpty(proxy.password);
          int flags = 0;
          if (hasUsername) flags |= 1;
          if (hasPassword) flags |= 2;
          blob.writeByte((byte) (flags));
          if (hasUsername)
            blob.writeString(proxy.username);
          if (hasPassword)
            blob.writeString(proxy.password);
          byte[] result = blob.toByteArray();
          r.setLength(result.length);
          r.write(result);
          break;
        }
      }
    } catch (IOException e) {
      Log.e(e);
    }
    U.close(r);
  }*/

  /*private void dispatchProxyConfig (int excludeAccountId) {
    if (accounts.isEmpty()) {
      return;
    }
    for (TdlibAccount account : this) {
      if (account.tdlib != null && account.id != excludeAccountId) {
        account.tdlib.client().send(new TdApi.SetProxy(proxy), account.tdlib.okHandler());
      }
    }
  }

  public void setProxy (TdApi.Proxy proxy, int excludeAccountId) {
    if (proxy == null) {
      return;
    }
    this.proxy = proxy;
    saveProxyConfig();
    dispatchProxyConfig(excludeAccountId);
    global.notifyProxyChanged(proxy);
  }*/

  // Account list

  private static final int BINLOG_PREFIX_SIZE = 4 /*account_num*/ + 4 /*preferred_id*/;

  public static int readAccountNum () {
    File file = getAccountConfigFile();
    if (!file.exists()) {
      return 1;
    }
    try (RandomAccessFile r = new RandomAccessFile(file, MODE_R)) {
      return Math.max(1, r.readInt());
    } catch (Throwable ignored) {
      return 1;
    }
  }

  public static int readPreferredAccountId () {
    File file = getAccountConfigFile();
    if (!file.exists()) {
      return 0;
    }
    try (RandomAccessFile r = new RandomAccessFile(file, MODE_R)) {
      int accountNum = Math.max(1, r.readInt());
      int preferredAccountId = r.readInt();
      return preferredAccountId >= 0 && preferredAccountId < accountNum ? preferredAccountId : 0;
    } catch (Throwable ignored) {
      return 0;
    }
  }

  private void readAccountConfig () {
    this.currentAccount = null;
    this.preferredAccountId = 0;
    File file = getAccountConfigFile();
    AccountConfig config = null;
    if (file.exists()) {
      try (RandomAccessFile r = new RandomAccessFile(file, MODE_R)) {
        config = readAccountConfig(this, r, TdlibAccount.VERSION, true);
      } catch (IOException e) {
        Log.e(e);
      }
    } else {
      try {
        if (!file.createNewFile())
          Log.e("Unable to create TDLib config file: %s", file.getPath());
      } catch (IOException e) {
        Log.e(e);
      }
    }

    if (config != null) {
      this.preferredAccountId = config.preferredAccountId;
      this.currentAccount = config.currentAccount;
      this.accounts.addAll(config.accounts);
      if (this.currentAccount != null)
        this.currentAccount.markAsUsed();
      for (TdlibAccount account : config.accounts) {
        checkAliveAccount(account);
      }
    }
    if (accounts.isEmpty()) {
      TdlibAccount account = new TdlibAccount(this, 0, Tdlib.Mode.NORMAL);
      accounts.add(account);
      checkAliveAccount(account);
    }
    if (currentAccount == null) {
      if (preferredAccountId >= accounts.size() || preferredAccountId < 0) {
        Log.e("preferredAccountId=%d is not in range 0..%d", preferredAccountId, accounts.size());
        preferredAccountId = 0;
      }
      currentAccount = accounts.get(preferredAccountId);
      currentAccount.markAsUsed();
    }
  }

  public static class AccountConfig {
    public TdlibAccount currentAccount;
    public List<TdlibAccount> accounts;
    public int preferredAccountId;

    public AccountConfig (TdlibAccount currentAccount, List<TdlibAccount> accounts, int preferredAccountId) {
      this.currentAccount = currentAccount;
      this.accounts = accounts;
      this.preferredAccountId = preferredAccountId;
    }
  }

  public static AccountConfig readAccountConfig (@Nullable TdlibManager context, RandomAccessFile r, int version, boolean allowIntegrityChecks) throws IOException {
    long ms = SystemClock.uptimeMillis();

    long binlogSize = r.length();
    Log.i("readAccountConfig binlogSize:%d", binlogSize);

    final int accountNum = binlogSize >= 4 ? r.readInt() : 0;
    if (accountNum <= 0 || accountNum > TdlibAccount.ID_MAX) {
      Log.i("readAccountConfig accountNum:%d accounts in %dms", accountNum, SystemClock.uptimeMillis() - ms);
      return null;
    }

    TdlibAccount currentAccount = null;
    final int preferredAccountId = r.readInt();
    final List<TdlibAccount> accounts = new ArrayList<>(accountNum);
    for (int accountId = 0; accountId < accountNum; accountId++) {
      TdlibAccount account = new TdlibAccount(context, accountId, r, version, allowIntegrityChecks);
      if (!account.isUnauthorized()) {
        if (accountId == preferredAccountId || currentAccount == null || currentAccount.id < preferredAccountId) {
          currentAccount = account;
        }
      }
      accounts.add(account);
    }

    Log.i("readAccountConfig finished, accountNum:%d in %dms, preferredAccountId:%d", accounts.size(), SystemClock.uptimeMillis() - ms, preferredAccountId);

    return new AccountConfig(currentAccount, accounts, preferredAccountId);
  }

  public static int binlogSize (int accountsNum) {
    return BINLOG_PREFIX_SIZE + accountsNum * TdlibAccount.SIZE_PER_ENTRY;
  }

  private int writeAccountConfig (RandomAccessFile r, int mode, int accountId) throws IOException {
    int saveCount = 0;
    final int accountNum = accounts.size();

    final int binlogSize = binlogSize(accountNum);
    final long currentLen = r.length();

    final boolean canOptimize;
    switch (mode) {
      case WRITE_MODE_FULL:
        canOptimize = accountId != TdlibAccount.NO_ID && currentLen == binlogSize;
        break;
      case WRITE_MODE_ADD_ENTRY:
        canOptimize = accountId != TdlibAccount.NO_ID && currentLen + TdlibAccount.SIZE_PER_ENTRY == binlogSize;
        break;
      default:
        canOptimize = currentLen == binlogSize;
        break;
    }

    Log.i(Log.TAG_ACCOUNTS, "Writing account configuration, accountNum:%d, preferredAccountId:%d, mode:%d, canOptimize:%b, accountId:%d, binlogSize:%d, currentLen:%d", accountNum, preferredAccountId, mode, canOptimize, accountId, binlogSize, currentLen);

    if (canOptimize) {
      switch (mode) {
        case WRITE_MODE_ADD_ENTRY: {
          r.setLength(binlogSize);
          r.writeInt(accountNum);
          r.seek(currentLen);
          accounts.get(accountId).save(r);
          saveCount = 1;
          break;
        }
        case WRITE_MODE_PREFERRED_ID: {
          r.seek(4 /*account_num*/);
          r.writeInt(accountId);
          break;
        }
        case WRITE_MODE_FULL: {
          int position = BINLOG_PREFIX_SIZE + TdlibAccount.SIZE_PER_ENTRY * accountId;
          r.seek(position);
          accounts.get(accountId).save(r);
          saveCount = 1;
          break;
        }
        case WRITE_MODE_ORDERS: {
          int position = BINLOG_PREFIX_SIZE;
          for (TdlibAccount account : accounts) {
            position = account.saveOrder(r, position);
            saveCount++;
          }
          break;
        }
        case WRITE_MODE_FLAGS: {
          if (accountId != TdlibAccount.NO_ID) {
            int position = BINLOG_PREFIX_SIZE + TdlibAccount.SIZE_PER_ENTRY * accountId;
            accounts.get(accountId).saveFlags(r, position);
            saveCount = 1;
          } else {
            int position = BINLOG_PREFIX_SIZE;
            for (TdlibAccount account : accounts) {
              position = account.saveFlags(r, position);
              saveCount++;
            }
          }
          break;
        }
        default:
          throw new IllegalArgumentException(Integer.toString(mode));
      }
      return saveCount;
    }

    return writeAccountConfigFully(r, new AccountConfig(currentAccount, accounts, preferredAccountId));
  }

  public static int writeAccountConfigFully (RandomAccessFile r, AccountConfig config) throws IOException {
    int accountsNum = config.accounts.size();
    r.setLength(binlogSize(accountsNum));
    r.writeInt(accountsNum);
    r.writeInt(config.preferredAccountId);
    int saveCount = 0;
    for (TdlibAccount account : config.accounts) {
      account.save(r);
      saveCount++;
    }
    return saveCount;
  }

  private static final int WRITE_MODE_FULL = 0;
  private static final int WRITE_MODE_ADD_ENTRY = 1;
  private static final int WRITE_MODE_PREFERRED_ID = 2;
  private static final int WRITE_MODE_ORDERS = 3;
  private static final int WRITE_MODE_FLAGS = 4;

  public static final String MODE_RW = "rw";
  public static final String MODE_R = "r";

  private synchronized void saveAccountConfig (int mode, int accountId) {
    long ms = SystemClock.uptimeMillis();
    File file = getAccountConfigFile();
    try {
      if (!file.exists() && !file.createNewFile())
        throw new DeviceStorageError("Cannot save config file");
    } catch (IOException e) {
      throw new DeviceStorageError(e);
    }

    int accountNum;
    try (RandomAccessFile r = new RandomAccessFile(file, MODE_RW)) {
      accountNum = writeAccountConfig(r, mode, accountId);
    } catch (IOException e) {
      Tracer.onLaunchError(e);
      throw new DeviceStorageError(e);
    }
    try (RandomAccessFile ignored = new RandomAccessFile(file, MODE_RW)) {
      Log.i(Log.TAG_ACCOUNTS, "Saved %d accounts in %dms, mode:%d", accountNum, SystemClock.uptimeMillis() - ms, mode);
    } catch (IOException e) {
      Tracer.onLaunchError(e);
      throw new DeviceStorageError(e);
    }
  }

  /*private static final int SAVE_MODE_FULL = 0;
  private static final int SAVE_MODE_USER_ID = 1;
  private static final int SAVE_MODE_ORDER = 2;*/

  private static final int ACCOUNT_AUTHORIZATION_CHANGED = 0;
  private static final int ACCOUNT_USER_CHANGED = 1;

  private void saveAccount (TdlibAccount account, int cause) {
    saveAccountConfig(WRITE_MODE_FULL, account.id);
  }

  private void savePreferredAccountId (int accountId) {
    saveAccountConfig(WRITE_MODE_PREFERRED_ID, accountId);
  }

  private void saveNewAccount (TdlibAccount account) {
    saveAccountConfig(WRITE_MODE_ADD_ENTRY, account.id);
  }

  public void saveAccountOrders () {
    saveAccountConfig(WRITE_MODE_ORDERS, TdlibAccount.NO_ID);
  }

  public static final int SWITCH_REASON_UNAUTHORIZED = 0;
  public static final int SWITCH_REASON_NAVIGATION = 1;
  public static final int SWITCH_REASON_USER_CLICK = 2;
  public static final int SWITCH_REASON_CHAT_OPEN = 3;
  public static final int SWITCH_REASON_CHAT_FOCUS = 4;
  public static final int SWITCH_REASON_EXISTING_NUMBER = 5;

  public int preferredAccountId () {
    return preferredAccountId;
  }

  public int hasAccountWithPhoneNumber (String phoneNumber, boolean isDebug) {
    final int targetInstanceMode = isDebug ? Tdlib.Mode.DEBUG : Tdlib.Mode.NORMAL;
    for (TdlibAccount account : accounts) {
      if (account.tdlibInstanceMode() == targetInstanceMode && account.comparePhoneNumber(phoneNumber) && !account.isUnauthorized() && !account.isLoggingOut()) {
        return account.id;
      }
    }
    return TdlibAccount.NO_ID;
  }

  public int hasAccountWithName (String firstName, String lastName, int excludeAccountId) {
    final int size = accounts.size();
    for (int i = size - 1; i >= 0; i--) {
      TdlibAccount account = accounts.get(i);
      if (account.id != excludeAccountId && !account.isUnauthorized()) {
        if (StringUtils.equalsOrBothEmpty(firstName, account.getFirstName()) && StringUtils.equalsOrBothEmpty(lastName, account.getLastName())) {
          return account.id;
        }
      }
    }
    return TdlibAccount.NO_ID;
  }

  public int hasAccountWithFirstName (String firstName, int excludeAccountId) {
    final int size = accounts.size();
    for (int i = size - 1; i >= 0; i--) {
      TdlibAccount account = accounts.get(i);
      if (account.id != excludeAccountId && !account.isUnauthorized()) {
        if (StringUtils.equalsOrBothEmpty(firstName, account.getFirstName())) {
          return account.id;
        }
      }
    }
    return TdlibAccount.NO_ID;
  }

  @UiThread
  public void changePreferredAccountId (int accountId, @AccountSwitchReason int reason) {
    changePreferredAccountId(accountId, reason, null);
  }

  @UiThread
  public void changePreferredAccountId (int accountId, @AccountSwitchReason int reason, @Nullable RunnableBool after) {
    if (this.preferredAccountId == accountId) {
      if (after != null) after.runWithBool(false);
      return;
    }
    if (accountId < 0 || accountId >= accounts.size()) {
      throw new IllegalArgumentException("accountId == " + accountId);
    }
    TdlibAccount newPreferredAccount = accounts.get(accountId);
    if (newPreferredAccount.isUnauthorized() || newPreferredAccount.tdlibInstanceMode() == Tdlib.Mode.SERVICE) {
      if (after != null) after.runWithBool(false);
      return;
    }
    accounts.get(accountId).tdlib().awaitInitialization(() -> {
      if (accounts.get(accountId).isUnauthorized()) {
        if (after != null) after.runWithBool(false);
        return;
      }
      runOnUiThread(() -> {
        Log.i(Log.TAG_ACCOUNTS, "Switching preferred account %d -> %d, reason:%d", this.preferredAccountId, accountId, reason);
        int oldAccountId = this.preferredAccountId;
        this.preferredAccountId = accountId;
        onAccountSwitched(accounts.get(accountId), reason, oldAccountId >= 0 && oldAccountId < accounts.size() ? accounts.get(oldAccountId) : null);
        savePreferredAccountId(accountId);
        if (after != null) after.runWithBool(true);
      });
    });
  }

  @UiThread
  private void onAccountSwitched (TdlibAccount account, @AccountSwitchReason int reason, TdlibAccount oldAccount) {
    this.currentAccount = account;
    if (oldAccount != null)
      oldAccount.markAsUsed();
    account.markAsUsed();
    Tdlib tdlib = account.tdlib();
    global().notifyAccountSwitched(account, tdlib.myUser(), reason, oldAccount);
    global().notifyResolvableProblemAvailabilityMightHaveChanged();
    onConnectionStateChanged(tdlib, tdlib.connectionState());
    onConnectionDisplayStatusChanged(tdlib);
    if (Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT)) {
      onUpdateNotifications(null, notificationAccount -> notificationAccount.id == account.id || (oldAccount != null && notificationAccount.id == oldAccount.id));
    }
  }

  void onUpdateAccountProfile (int accountId, @NonNull TdApi.User user, boolean isFirstTimeLoaded) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_ACCOUNT_PROFILE, accountId, isFirstTimeLoaded ? 1 : 0, user));
  }

  void onUpdateAccountProfilePhoto (int accountId, boolean big) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_ACCOUNT_PROFILE_PHOTO, accountId, big ? 1 : 0));
  }

  void onUpdateEmojiStatus (int accountId, boolean isThumbnail) {
    handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_ACCOUNT_EMOJI_STATUS, accountId, isThumbnail ? 1 : 0));
  }

  public TdlibAccount currentAccount () {
    return currentAccount;
  }

  public Tdlib current () {
    return currentAccount.tdlib();
  }

  public Tdlib currentNoWakeup () {
    return currentAccount.tdlibNoWakeup();
  }

  public boolean hasAccount (int accountId) {
    return accountId >= 0 && accountId < accounts.size();
  }

  public Tdlib tdlib (int accountId) {
    return account(accountId).tdlib();
  }

  public Tdlib serviceTdlib () {
    return account(serviceAccountId()).tdlib();
  }

  public TdlibAccount account (int accountId) {
    if (accountId == TdlibAccount.NO_ID)
      throw new IllegalArgumentException();
    return accounts.get(accountId);
  }

  public int accountIdForUserId (long userId, int startIndex) {
    for (int i = startIndex; i < accounts.size(); i++) {
      if (accounts.get(i).getKnownUserId() == userId) {
        return i;
      }
    }
    return -1;
  }

  private void saveAccountFlags (TdlibAccount account) {
    saveAccountConfig(WRITE_MODE_FLAGS, account.id);
    account.launch(false);
  }

  void setKeepAlive (int accountId, boolean keepAlive) {
    TdlibAccount account = account(accountId);
    if (account.setKeepAlive(keepAlive)) {
      saveAccountFlags(account);
    }
  }

  void setDeviceRegistered (int accountId, boolean isRegistered) {
    TdlibAccount account = account(accountId);
    if (account.setDeviceRegistered(isRegistered)) {
      saveAccountFlags(account);
    }
    if (!isRegistered) {
      TdlibSettingsManager.unregisterDevice(accountId);
    }
  }

  void unregisterDevices (@Tdlib.Mode int instanceMode, int excludeAccountId, long[] excludeUserIds) {
    for (TdlibAccount account : this) {
      if (account.tdlibInstanceMode() != instanceMode || account.id == excludeAccountId)
        continue;
      long knownUserId = account.getKnownUserId();
      if (knownUserId == 0 || Arrays.binarySearch(excludeUserIds, knownUserId) < 0) {
        Log.i(Log.TAG_FCM, "Unregistered accountId:%d userId:%d", account.id, knownUserId);
        setDeviceRegistered(account.id, false);
        // TODO? account.tdlib().checkDeviceToken();
      }
    }
  }

  void setHasUnprocessedPushes (int accountId, boolean hasUnprocessedPushes) {
    TdlibAccount account = account(accountId);
    if (account.setHasUnprocessedPushes(hasUnprocessedPushes)) {
      saveAccountFlags(account);
    }
  }

  public void setForceEnableNotifications (int accountId, boolean forceEnable) {
    TdlibAccount account = account(accountId);
    if (account.setForceEnableNotifications(forceEnable)) {
      saveAccountFlags(account);
    }
  }

  public void setHavePendingNotifications (int accountId, boolean havePendingNotifications) {
    TdlibAccount account = account(accountId);
    if (account.setHaveVisibleNotifications(havePendingNotifications)) {
      saveAccountFlags(account);
    }
  }

  void setLoggingOut (int accountId, boolean isLoggingOut) {
    TdlibAccount account = account(accountId);
    if (account.setLoggingOut(isLoggingOut)) {
      saveAccountFlags(account);
    }
  }

  void markNoPrivateData (int accountId) {
    TdlibAccount account = account(accountId);
    if (account.markNoPrivateData()) {
      saveAccountFlags(account);
    }
  }

  public int newAccount (boolean isDebug) {
    return newAccount(isDebug ? Tdlib.Mode.DEBUG : Tdlib.Mode.NORMAL);
  }

  public int serviceAccountId () {
    return newAccount(Tdlib.Mode.SERVICE);
  }

  public int newAccount (@Tdlib.Mode int instanceMode) {
    for (TdlibAccount account : accounts) {
      if (account.id != currentAccount.id && account.isUnauthorized() && !account.isLoggingOut()) {
        if ((instanceMode == Tdlib.Mode.SERVICE) != (account.tdlibInstanceMode() == Tdlib.Mode.SERVICE)) {
          // ignore all other types of TdlibAccount, if we are looking for Mode.SERVICE account
          // and ignore service TdlibAccount, if we are looking for non-serivce TdlibAccount
          continue;
        }
        if (account.setInstanceMode(instanceMode)) {
          saveAccountFlags(account);
        }
        return account.id;
      }
    }
    final int tdlibId = accounts.size();
    if (tdlibId >= TdlibAccount.ID_MAX) {
      return TdlibAccount.NO_ID;
    }
    TdlibAccount newAccount = new TdlibAccount(this, accounts.size(), instanceMode);
    accounts.add(newAccount);
    newAccount.markAsUsed();
    newAccount.tdlib();
    saveNewAccount(newAccount);
    if (checkAliveAccount(newAccount)) {
      checkPauseTimeouts(newAccount);
    }
    return newAccount.id;
  }

  // PUSH

  private interface TdlibTask {
    void onPerformTask (TdlibAccount account, @Nullable Runnable onDone);
  }

  private void performTdlibTask (long pushId, int accountId, TdlibTask task, int limit, Filter<TdlibAccount> filter, @Nullable Runnable after) {
    performTask(accountId, (account, onDone) -> {
      try {
        account.tdlib();
      } catch (Throwable t) {
        Log.e("Unable to create TDLib instance", t);
        if (onDone != null)
          onDone.run();
        return;
      }
      task.onPerformTask(account, onDone);
    }, limit, filter, after);
  }

  private void performTask (int accountId, TdlibTask task, int limit, Filter<TdlibAccount> filter, @Nullable Runnable after) {
    if (accountId != TdlibAccount.NO_ID) {
      task.onPerformTask(account(accountId), after);
    } else {
      final LinkedList<TdlibAccount> accounts = accountsQueue(filter);
      if (accounts.isEmpty()) { // No accounts available
        if (after != null)
          after.run();
      } else if (accounts.size() == 1) { // Single account optimization
        task.onPerformTask(accounts.removeFirst(), after);
      } else { // Multiple accounts
        if (limit <= 0)
          throw new AssertionError(limit);
        final AtomicInteger pending = new AtomicInteger(1);
        Runnable runNextTask = new Runnable() {
          @Override
          public void run () {
            List<Runnable> todo = null;
            boolean done;
            synchronized (accounts) {
              pending.decrementAndGet();
              while (!accounts.isEmpty() && pending.get() < limit) {
                pending.incrementAndGet();
                if (todo == null)
                  todo = new ArrayList<>();
                TdlibAccount nextAccount = accounts.removeFirst();
                boolean hadTdlib = nextAccount.hasTdlib(true);
                todo.add(() -> task.onPerformTask(nextAccount, () -> {
                  if (hadTdlib) {
                    this.run();
                  } else {
                    nextAccount.closeTdlib(this);
                  }
                }));
              }
              done = pending.get() == 0 && accounts.isEmpty();
            }
            if (todo != null) {
              for (Runnable task : todo) {
                task.run();
              }
            }
            if (done && after != null) {
              after.run();
            }
          }
        };
        runNextTask.run();
      }
    }
  }

  public void sync (long pushId, int accountId, Runnable after, boolean needNotifications, boolean needNetworkRequest, int limit, RunnableData<Tdlib> callback) {
    performTdlibTask(pushId, accountId, (account, onDone) -> {
      Tdlib tdlib = account.tdlib();
      if (callback != null) {
        tdlib.sync(pushId, () -> {
          callback.runWithData(tdlib);
          if (onDone != null)
            onDone.run();
        }, needNotifications, needNetworkRequest);
      } else {
        tdlib.sync(pushId, onDone, needNotifications, needNetworkRequest);
      }
    }, limit, null, after);
  }

  public boolean notifyPushProcessingTakesTooLong (int accountId, long pushId) {
    TDLib.Tag.notifications(pushId, accountId, "Trying to speed up notification displaying by aborting some of operations");
    if (accountId != TdlibAccount.NO_ID) {
      Tdlib tdlib = account(accountId).activeTdlib();
      return tdlib == null || tdlib.notifyPushProcessingTakesTooLong(pushId);
    }
    int failureCount = 0;
    for (TdlibAccount account : this) {
      Tdlib tdlib = account.activeTdlib();
      if (tdlib != null && !tdlib.notifyPushProcessingTakesTooLong(pushId)) {
        failureCount++;
      }
    }
    return failureCount == 0;
  }

  public void processPushOrSync (long pushId, int accountId, String payload, @Nullable Runnable after) {
    performTdlibTask(pushId, accountId, (account, onDone) -> account.tdlib().processPushOrSync(pushId, payload, onDone), Config.MAX_RUNNING_TDLIBS, null, after);
  }

  public void cleanupLoggedOutAccounts () {
    long ms = SystemClock.uptimeMillis();
    AtomicInteger accountsNum = new AtomicInteger(0);
    performTask(TdlibAccount.NO_ID, (account, onDone) -> {
      accountsNum.incrementAndGet();
      account.tdlib().cleanupUnauthorizedData(onDone);
    }, 1, loggedOutFilter(), () -> {
      Log.i("Cleared unauthorized data in %d accounts in %dms", accountsNum.get(), SystemClock.uptimeMillis() - ms);
    });
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TokenState.NONE,
    TokenState.ERROR,
    TokenState.INITIALIZING,
    TokenState.OK
  })
  public @interface TokenState {
    int NONE = 0,
      ERROR = 1,
      INITIALIZING = 2,
      OK = 3;
  }

  private @TokenState int tokenState = TokenState.NONE;
  private String tokenError;
  private Throwable tokenFullError;

  private static Filter<TdlibAccount> loggedOutFilter () {
    return account -> account.isUnauthorized() && account.hasPrivateData() && !account.isService();
  }

  private synchronized void setTokenState (@TokenState int newState) {
    setTokenState(newState, null, null);
  }

  private synchronized void setTokenState (@TokenState int newState, @Nullable String error, @Nullable Throwable fullError) {
    if (this.tokenState != TokenState.OK || newState == TokenState.OK) {
      this.tokenState = newState;
      this.tokenError = error;
      this.tokenFullError = fullError;
      for (TdlibAccount account : accountsQueue(loggedOutFilter())) {
        if (account.launch(false)) {
          account.tdlib().checkConnectionParams();
        }
      }
      global().notifyTokenStateChanged(newState, error, fullError);
      if (newState == TokenState.ERROR && !StringUtils.isEmpty(error)) {
        String reportedError = Settings.instance().getReportedPushServiceError();
        if (reportedError == null || !reportedError.equals(error)) {
          Settings.instance().setReportedPushServiceError(error);
          reportPushServiceError(error, fullError);
        }
      } else if (newState == TokenState.OK) {
        String reportedError = Settings.instance().getReportedPushServiceError();
        if (!StringUtils.isEmpty(reportedError)) {
          reportPushServiceRestored(reportedError, Settings.instance().getReportedPushServiceErrorDate());
          // forget the error, so it would be reported again when it happens
          Settings.instance().setReportedPushServiceError(null);
        }
      }
    }
  }

  public boolean hasTokenError () {
    return tokenState == TokenState.ERROR;
  }

  @TokenState
  public int getTokenState () {
    return tokenState;
  }

  @Nullable
  public String getTokenError () {
    return tokenError;
  }

  @Nullable
  public Throwable getTokenFullError () {
    return tokenFullError;
  }

  private TdApi.DeviceToken token;

  public TdApi.DeviceToken getToken () {
    return token;
  }

  public synchronized void setDeviceToken (TdApi.DeviceToken token) {
    if (!Td.equalsTo(this.token, token)) {
      Settings.instance().setDeviceToken(token);
      this.token = token;
      setTokenState(TokenState.OK);
      dispatchDeviceToken(token);
    }
  }

  private static final String EXPERIMENTAL_BUILD_ERROR = "EXPERIMENTAL_BUILD_DETECTED";

  public void checkDeviceToken () {
    checkDeviceToken(null);
  }

  public void checkDeviceToken (@Nullable RunnableBool after) {
    checkDeviceToken(3, after);
  }

  public synchronized void checkDeviceToken (int retryCount, @Nullable RunnableBool after) {
    if (this.tokenState == TokenState.OK) {
      if (after != null) {
        after.runWithBool(true);
      }
      return;
    }
    if (BuildConfig.EXPERIMENTAL) {
      setTokenState(TokenState.ERROR, EXPERIMENTAL_BUILD_ERROR, null);
      if (after != null) {
        after.runWithBool(false);
      }
      return;
    }
    setTokenState(TokenState.INITIALIZING);
    TdlibNotificationUtils.getDeviceToken(retryCount, new TokenRetriever.RegisterCallback() {
      @Override
      public void onSuccess (@NonNull TdApi.DeviceToken token) {
        setDeviceToken(token);
        if (after != null) {
          after.runWithBool(true);
        }
      }

      @Override
      public void onError (@NonNull String errorKey, @Nullable Throwable e) {
        Log.e(Log.TAG_FCM, "Failed to retrieve push token", e);
        setTokenState(TokenState.ERROR, errorKey, e);
        if (after != null) {
          after.runWithBool(false);
        }
      }
    });
  }

  private void dispatchDeviceToken (TdApi.DeviceToken token) {
    long[] debugUserIds = null, productionUserIds = null;
    boolean hasNonRegistered = false;
    for (TdlibAccount account : this) {
      final @Tdlib.Mode int mode = account.tdlibInstanceMode();
      if (mode != Tdlib.Mode.NORMAL && mode != Tdlib.Mode.DEBUG) {
        continue;
      }
      if (!account.isUnauthorized() || account.hasTdlib(false)) {
        long[] otherUserIds;
        long myUserId = account.getKnownUserId();
        if (mode == Tdlib.Mode.DEBUG) {
          if (debugUserIds == null)
            debugUserIds = availableUserIds(mode);
          otherUserIds = ArrayUtils.removeElement(debugUserIds, ArrayUtils.indexOf(debugUserIds, myUserId));
        } else {
          if (productionUserIds == null)
            productionUserIds = availableUserIds(mode);
          otherUserIds = ArrayUtils.removeElement(productionUserIds, ArrayUtils.indexOf(productionUserIds, myUserId));
        }
        boolean needRegister = !TdlibSettingsManager.checkRegisteredDeviceToken(account.id, account.getKnownUserId(), token, otherUserIds, true);
        if (needRegister) {
          setDeviceRegistered(account.id, false);
          hasNonRegistered = true;
        }
        if (account.hasTdlib(true)) {
          account.tdlib().checkDeviceTokenImpl(null);
        }
      }
    }
    if (hasNonRegistered) {
      performTask(TdlibAccount.NO_ID, (account, onDone) -> {
        account.tdlib().checkDeviceTokenImpl(onDone);
      }, Config.MAX_RUNNING_TDLIBS, account -> !account.isUnauthorized() || account.hasTdlib(false), null);
    }
  }

  // Service reporting

  public void reportPushServiceError (@Nullable String error, @Nullable Throwable fullError) {
    if (EXPERIMENTAL_BUILD_ERROR.equals(error)) {
      return;
    }
    Map<String, Object> event = new LinkedHashMap<>();
    if (!StringUtils.isEmpty(error)) {
      event.put("error", error);
    }
    if (fullError != null) {
      event.put("stack_trace", Log.toString(fullError));
    }
    reportEvent("PUSH_SERVICE_ERROR", event);
  }

  public void reportPushServiceRestored (String reportedErrorType, long reportedDate) {
    Map<String, Object> event = new LinkedHashMap<>();
    if (!StringUtils.isEmpty(reportedErrorType)) {
      event.put("error", reportedErrorType);
    }
    if (reportedDate != 0) {
      event.put("recovered_within", System.currentTimeMillis() - reportedDate);
    }
    reportEvent("PUSH_SERVICE_RECOVERED", event);
  }

  public static Map<String, Object> deviceInformation () {
    Map<String, Object> device = new LinkedHashMap<>();
    device.put("manufacturer", Build.MANUFACTURER);
    device.put("brand", Build.BRAND);
    device.put("model", Build.MODEL);
    device.put("display", Build.DISPLAY);
    device.put("release", Build.VERSION.RELEASE);
    return device;
  }

  private void reportEvent (String type, Map<String, Object> event) {
    AppBuildInfo appBuildInfo = Settings.instance().getCurrentBuildInformation();

    event.put("sdk", Build.VERSION.SDK_INT);
    event.put("app", appBuildInfo.toMap());
    event.put("cpu", U.getCpuArchitecture());
    event.put("package_id", UI.getAppContext().getPackageName());
    event.put("device", deviceInformation());
    event.put("fingerprint", U.getApkFingerprint("SHA1"));
    event.put("device_id", Settings.instance().crashDeviceId());

    Tdlib tdlib = serviceTdlib();
    tdlib.incrementJobReferenceCount();
    tdlib.client().send(new TdApi.SaveApplicationLogEvent(type, appBuildInfo.maxCommitDate(), JSON.toObject(event)), result -> {
      tdlib.decrementJobReferenceCount();
    });
  }

  public void saveCrashes () {
    final List<Crash> savingCrashes = Settings.instance().getCrashesToSave();
    if (savingCrashes == null || savingCrashes.isEmpty())
      return;
    Background.instance().post(() -> {
      final Tdlib tdlib = serviceTdlib();
      final String deviceId = Settings.instance().crashDeviceId();
      tdlib.awaitConnection(() -> {
        AtomicInteger pendingRequests = new AtomicInteger();
        Runnable check = () -> {
          synchronized (pendingRequests) {
            if (pendingRequests.get() != 0)
              return;
          }
          tdlib.decrementJobReferenceCount();
        };
        boolean isEmpty = true;
        for (Crash crash : savingCrashes) {
          TdApi.SaveApplicationLogEvent saveFunction = crash.toSaveFunction(deviceId);
          if (saveFunction != null) {
            if (isEmpty) {
              isEmpty = false;
              tdlib.incrementJobReferenceCount();
            }
            synchronized (pendingRequests) {
              pendingRequests.incrementAndGet();
            }
            TDLib.Tag.td_init("Reporting crash %d: %s", crash.id, saveFunction);
            tdlib.send(saveFunction, (ok, error) -> {
              if (error != null) {
                TDLib.Tag.td_init("Can't report crash %d: %s", crash.id, TD.toErrorString(error));
              } else {
                Settings.instance().markCrashAsSaved(crash);
              }
              synchronized (pendingRequests) {
                pendingRequests.decrementAndGet();
              }
              check.run();
            });
          } else {
            Settings.instance().markCrashAsSaved(crash);
          }
        }
        if (!isEmpty) {
          check.run();
        }
      });
    });
  }

  // Account list

  private final ArrayList<TdlibAccount> activeAccounts = new ArrayList<>();

  private boolean checkAliveAccount (TdlibAccount account) {
    boolean needAdd = !account.isUnauthorized() && account.hasDisplayInfo();
    int position = activeAccounts.indexOf(account);
    if (position == -1 && needAdd) {
      position = Collections.binarySearch(activeAccounts, account);
    }
    if (needAdd) {
      if (position >= 0) {
        return false;
      }
      position = (-position) - 1;
      activeAccounts.add(position, account);
    } else {
      if (position < 0) {
        return false;
      }
      activeAccounts.remove(position);
    }
    global().notifyAccountAddedOrRemoved(account, position, needAdd);
    resetBadge(false);
    increaseModCount(account);
    return true;
  }

  public boolean hasActiveAccounts () {
    return getActiveAccountsNum() > 0;
  }

  public void moveAccount (int fromPosition, int toPosition) {
    if (fromPosition == toPosition) {
      return;
    }
    TdlibAccount account = activeAccounts.get(fromPosition);
    ArrayUtils.move(activeAccounts, fromPosition, toPosition);
    setAccountPositions();
    global().notifyAccountMoved(account, fromPosition, toPosition);
  }

  private void setAccountPositions () {
    int i = 0;
    for (TdlibAccount account : activeAccounts) {
      account.setOrder(i);
      i++;
    }
  }

  public ArrayList<TdlibAccount> getActiveAccounts () {
    return activeAccounts;
  }

  /**
   * @param excludeAccountId Account identifier, which neighbor should be found
   * @return Next account in the list to be switched to
   */
  public int findNextAccountId (int excludeAccountId) {
    TdlibAccount account = account(excludeAccountId);
    int sourceIndex = Collections.binarySearch(activeAccounts, account);
    if (sourceIndex >= 0) {
      if (activeAccounts.size() > sourceIndex + 1)
        return activeAccounts.get(sourceIndex + 1).id;
      else if (sourceIndex > 0)
        return activeAccounts.get(0).id;
    } else {
      sourceIndex = (-sourceIndex) - 1;
      if (sourceIndex < activeAccounts.size())
        return activeAccounts.get(sourceIndex).id;
      int nextAccountId = TdlibAccount.NO_ID;
      int index = 0, indexDiff = 0;
      for (TdlibAccount nextAccount : activeAccounts) {
        if (nextAccountId == TdlibAccount.NO_ID || Math.abs(index - sourceIndex) <= indexDiff) {
          nextAccountId = nextAccount.id;
          indexDiff = Math.abs(index - sourceIndex);
        }
        index++;
      }
      return nextAccountId;
    }
    return TdlibAccount.NO_ID;
  }

  public TdlibAccount findNextAccount (int excludeAccountId) {
    int accountId = findNextAccountId(excludeAccountId);
    return accountId != TdlibAccount.NO_ID ? account(accountId) : null;
  }

  public int getNumberOfAccountsWithEnabledNotifications () {
    int selectedCount = 0;
    for (TdlibAccount account : this) {
      if (account.forceEnableNotifications()) {
        selectedCount++;
      }
    }
    return selectedCount;
  }

  public LinkedList<TdlibAccount> accountsQueueReversed () {
    LinkedList<TdlibAccount> accounts = accountsQueue();
    Collections.reverse(accounts);
    return accounts;
  }

  public LinkedList<TdlibAccount> accountsQueue () {
    return accountsQueue(null);
  }

  private LinkedList<TdlibAccount> accountsQueue (Filter<TdlibAccount> filter) {
    LinkedList<TdlibAccount> accounts = new LinkedList<>();
    if (filter != null) {
      final int size = this.accounts.size();
      for (int accountId = size - 1; accountId >= 0; accountId--) {
        TdlibAccount account = account(accountId);
        if (filter.accept(account)) {
          accounts.add(account);
        }
      }
    } else {
      for (TdlibAccount tdlib : this) {
        accounts.add(tdlib);
      }
    }
    return accounts;
  }

  public void replaceThemeId (int oldThemeId, int newThemeId) {
    for (TdlibAccount account : accounts) {
      if (account.hasTdlib(false)) {
        account.tdlib().settings().replaceThemeId(oldThemeId, newThemeId);
      } else {
        TdlibSettingsManager.replaceThemeId(account.id, oldThemeId, newThemeId);
      }
    }
  }

  public void deleteWallpaper (int usageId) {
    for (TdlibAccount account : accounts) {
      if (account.hasTdlib(false)) {
        account.tdlib().settings().deleteWallpaper(usageId);
      } else {
        TdlibSettingsManager.deleteWallpaper(account.id, usageId);
      }
    }
  }

  public void checkThemeId (int themeId, boolean isDark, int parentThemeId) {
    for (TdlibAccount account : accounts) {
      if (account.hasTdlib(false)) {
        account.tdlib().settings().fixThemeId(themeId, isDark, parentThemeId);
      } else {
        TdlibSettingsManager.fixThemeId(account.id, themeId, isDark, parentThemeId);
      }
    }
  }

  public boolean isMultiUser () {
    return activeAccounts.size() > 1;
  }

  private void onAccountProfileChanged (TdlibAccount account, TdApi.User user, boolean isCurrent, boolean isFirstTimeLoaded) {
    if (account.isUnauthorized())
      return;
    global().notifyAccountProfileChanged(account, user, isCurrent, isFirstTimeLoaded);
    if (isFirstTimeLoaded || user == null) {
      if (checkAliveAccount(account)) {
        checkPauseTimeouts(null);
      }
    }
  }

  private void onAccountProfilePhotoChanged (TdlibAccount account, boolean big, boolean isCurrent) {
    if (account.isUnauthorized())
      return;
    global().notifyAccountProfilePhotoChanged(account, big, isCurrent);
  }

  private void onAccountProfileEmojiStatusChanged (TdlibAccount account, boolean isCurrent) {
    if (account.isUnauthorized())
      return;
    global().notifyAccountProfileEmojiStatusChanged(account, isCurrent);
  }

  // Event managements

  @NonNull
  long[] availableUserIds (boolean isDebug) {
    return availableUserIds(isDebug ? Tdlib.Mode.DEBUG : Tdlib.Mode.NORMAL);
  }

  @NonNull
  Set<Long> availableUserIdsSet (boolean isDebug) {
    return availableUserIdsSet(isDebug ? Tdlib.Mode.DEBUG : Tdlib.Mode.NORMAL);
  }

  @NonNull
  Set<Long> availableUserIdsSet (@Tdlib.Mode int instanceMode) {
    SortedSet<Long> userIds = new TreeSet<>();
    for (TdlibAccount account : accounts) {
      if (!account.isUnauthorized() && account.tdlibInstanceMode() == instanceMode) {
        long knownUserId = account.getKnownUserId();
        if (knownUserId != 0)
          userIds.add(knownUserId);
      }
    }
    return userIds;
  }

  @NonNull
  long[] availableUserIds (@Tdlib.Mode int instanceMode) {
    Set<Long> userIds = availableUserIdsSet(instanceMode);
    if (userIds.isEmpty())
      return new long[0];
    long[] array = new long[userIds.size()];
    int index = 0;
    for (long userId : userIds)
      array[index++] = userId;
    return array;
  }

  @TdlibThread
  void onKnownUserIdChanged (int accountId, long userId) {
    TdlibAccount account = accounts.get(accountId);
    if (/*!account.isUnauthorized() &&*/ account.setKnownUserId(userId)) {
      saveAccount(account, ACCOUNT_USER_CHANGED);
      if (userId != 0) {
        account.tdlib().checkDeviceTokenImpl(null);
      }
    }
    /*handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_KNOWN_USER_ID, accountId, userId));*/
  }

  @TdlibThread
  void onAuthStateChanged (Tdlib tdlib, TdApi.AuthorizationState authState, @Tdlib.Status int status, long userId) {
    if (status == Tdlib.Status.UNKNOWN) {
      return;
    }
    int accountId = tdlib.id();
    boolean isUnauthorized = status == Tdlib.Status.UNAUTHORIZED;
    TdlibAccount account = accounts.get(accountId);
    boolean changed = account.isUnauthorized() != isUnauthorized;
    if (account.setUnauthorized(isUnauthorized, userId)) {
      saveAccount(account, ACCOUNT_AUTHORIZATION_CHANGED);
    }
    runOnUiThread(() -> {
      if (changed) {
        if (checkAliveAccount(account)) {
          checkPauseTimeouts(null);
        }
        if (isUnauthorized && accountId == preferredAccountId) {
          int newAccountId = findNextAccountId(accountId);
          if (newAccountId != TdlibAccount.NO_ID) {
            changePreferredAccountId(newAccountId, SWITCH_REASON_UNAUTHORIZED);
          }
        }
      }
      global().notifyAuthorizationStateChanged(account, authState, status);
    });
  }

  // Config

  public static long getAccountConfigFileSize () {
    return getAccountConfigFile().length();
  }

  public static File getAccountConfigFile () {
    File parent = UI.getAppContext().getFilesDir();
    return new File(parent, "tdlib_accounts.bin");
  }

  public static final String LOG_FILE = "tdlib_log.txt";

  public static long getLogFileSize (boolean old) {
    File tdlibLog = getLogFile(old);
    return tdlibLog != null && tdlibLog.exists() && tdlibLog.isFile() ? tdlibLog.length() : 0;
  }

  @Nullable
  public static File getLogFile (boolean old) {
    String fileName = old ? LOG_FILE + ".old" : LOG_FILE;
    File logDir = Log.getLogDir();
    if (logDir != null) {
      return new File(logDir, fileName);
    }
    return null;
  }

  public static File getLegacyLogFile (boolean old) {
    return new File(getLegacyLogFilePath(old));
  }

  private static final String LEGACY_LOG_FILE_NAME = "log";
  public static String getLegacyLogFilePath (boolean old) {
    StringBuilder b = new StringBuilder(getTdlibDirectory(0, false)).append(LEGACY_LOG_FILE_NAME);
    if (old) {
      b.append(".old");
    }
    return b.toString();
  }

  public static String getDeviceModel () {
    return U.getManufacturer() + " " + Build.MODEL;
  }

  public static String getSystemVersion () {
    return SdkVersion.getPrettyName() + " (" + Build.VERSION.SDK_INT + ")";
  }

  public static String getSystemLanguageCode () {
    String languageCode = "en-US";
    try {
      languageCode = LocaleUtils.toBcp47Language(UI.getConfigurationLocale());
    } catch (Throwable ignored) { }
    return languageCode;
  }

  public static String getTdlibDirectory (int accountId, boolean allowExternal) {
    return getTdlibDirectory(accountId, allowExternal, true);
  }

  private static String[] getTdlibDirectories (boolean internal, boolean onlyPublic) {
    if (internal) {
      if (onlyPublic) {
        return new String[] {
          "profile_photos",
          "secret",
          "thumbnails",
          "wallpapers",
          "stickers"
        };
      } else {
        return new String[] {
          "passport",
          "profile_photos",
          "secret",
          "secret_thumbnails",
          "temp",
          "thumbnails",
          "wallpapers",
          "stickers",
        };
      }
    } else {
      if (onlyPublic) {
        return new String[] {
          "animations",
          "documents",
          "music",
          "photos",
          "videos"
        };
      } else {
        return new String[] {
          "animations",
          "documents",
          "music",
          "photos",
          "temp",
          "video_notes",
          "videos",
          "voice"
        };
      }
    }
  }

  private static Set<String> tdlibDirsInternalPublic, tdlibDirsExternalPublic;
  public static @NonNull Set<String> getPublicTdlibDirectories (boolean internal) {
    Set<String> tdlibDirectories = internal ? tdlibDirsInternalPublic : tdlibDirsExternalPublic;
    if (tdlibDirectories != null)
      return tdlibDirectories;
    final String[] array = getTdlibDirectories(internal, true);
    tdlibDirectories = new HashSet<>(array.length);
    Collections.addAll(tdlibDirectories, array);
    if (internal)
      tdlibDirsInternalPublic = tdlibDirectories;
    else
      tdlibDirsExternalPublic = tdlibDirectories;
    return tdlibDirectories;
  }

  private static Set<String> tdlibDirsInternal, tdlibDirsExternal;
  public static @NonNull Set<String> getAllTdlibDirectories (boolean internal) {
    Set<String> tdlibDirectories = internal ? tdlibDirsInternal : tdlibDirsExternal;
    if (tdlibDirectories != null)
      return tdlibDirectories;
    String[] array = getTdlibDirectories(internal, false);
    tdlibDirectories = new HashSet<>(array.length);
    Collections.addAll(tdlibDirectories, array);
    if (internal)
      tdlibDirsInternal = tdlibDirectories;
    else
      tdlibDirsExternal = tdlibDirectories;
    return tdlibDirectories;
  }

  public static long deleteLogFile (boolean old) {
    return deleteLogFiles(old ? DELETE_LOG_MODE_OLD : DELETE_LOG_MODE_CURRENT);
  }

  public static long deleteAllLogFiles () {
    return deleteLogFiles(DELETE_LOG_MODE_ALL);
  }

  private static final int DELETE_LOG_MODE_ALL = 0;
  private static final int DELETE_LOG_MODE_OLD = 1;
  private static final int DELETE_LOG_MODE_CURRENT = 2;

  private static long deleteLogFiles (int mode) {
    if (UI.TEST_MODE != UI.TEST_MODE_AUTO) {
      try {
        Client.execute(new TdApi.SetLogVerbosityLevel(0));
        Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamEmpty()));
      } catch (Client.ExecutionException ignored) { }
    }

    long removedSize;
    switch (mode) {
      case DELETE_LOG_MODE_ALL:
        removedSize = deleteLogFileImpl(false);
        if (removedSize != -1) {
          long size = deleteLogFileImpl(true);
          removedSize = size != -1 ? removedSize + size : -1;
        }
        if (removedSize != -1) {
          long size = deleteLogFileImpl(false);
          removedSize = size != -1 ? removedSize + size : -1;
        }
        if (removedSize != -1) {
          long size = deleteLogFileImpl(true);
          removedSize = size != -1 ? removedSize + size : -1;
        }
        break;
      case DELETE_LOG_MODE_CURRENT:
        removedSize = deleteLogFileImpl(false);
        break;
      case DELETE_LOG_MODE_OLD:
        removedSize = deleteLogFileImpl(true);
        break;
      default:
        return -1;
    }

    Settings.instance().applyLogSettings(false);

    return removedSize;
  }

  private static long deleteLogFileImpl (boolean old) {
    final File tdlibLogFile = getLogFile(old);
    final long fileSize = tdlibLogFile != null ? tdlibLogFile.length() : 0;
    long removedSize;
    if (fileSize > 0) {
      try (RandomAccessFile f = new RandomAccessFile(tdlibLogFile, "rw")) {
        f.setLength(0);
        removedSize = fileSize;
      } catch (IOException e) {
        removedSize = -1;
      }
    } else {
      removedSize = 0;
    }
    return removedSize;
  }

  public static String getTdlibDirectory (int accountId, boolean allowExternal, boolean createIfNotFound) {
    File file = allowExternal ? UI.getAppContext().getExternalFilesDir(null) : null;
    if (file != null) {
      try {
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        if (externalStorageDirectory != null && file.getAbsolutePath().startsWith(externalStorageDirectory.getAbsolutePath())) {
          String state = Environment.getExternalStorageState();
          if (!Environment.MEDIA_MOUNTED.equals(state)) {
            file = null;
          }
        }
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
    if (file != null) {
      try {
        if (!FileUtils.createDirectory(file) || !file.canWrite()) {
          file = null;
        }
      } catch (SecurityException e) {
        e.printStackTrace();
        file = null;
      }
    }
    if (file != null) {
      if (accountId != 0) {
        file = new File(file, "x_account" + accountId);
        if (!file.exists()) {
          if (createIfNotFound) {
            if (!FileUtils.mkdirs(file))
              throw new DeviceStorageError("Could not create external working directory: " + file.getPath());
          } else {
            return null;
          }
        }
      }
      // FIXME maybe move somewhere better for accountId == 0?
    } else {
      if (allowExternal && !createIfNotFound)
        return null;
      file = new File(UI.getContext().getFilesDir(), accountId != 0 ? "tdlib" + accountId : "tdlib");
      if (!file.exists()) {
        if (createIfNotFound) {
          if (!FileUtils.mkdirs(file))
            throw new DeviceStorageError("Cannot create working directory: " + file.getPath());
        } else {
          return null;
        }
      }
    }
    return TD.normalizePath(file.getPath());
  }

  public static File getTgvoipDirectory () {
    File tgvoipDir = new File(UI.getContext().getFilesDir(), "tgvoip");
    if (!FileUtils.createDirectory(tgvoipDir)) {
      throw new IllegalStateException("Cannot create working directory: " + tgvoipDir.getPath());
    }
    return tgvoipDir;
  }

  // Lang pack

  public static String getLanguageDatabasePath () {
    File languageDatabaseDir = new File(UI.getContext().getFilesDir(), "langpack");
    if (!FileUtils.createDirectory(languageDatabaseDir)) {
      throw new IllegalStateException("Cannot create working directory: " + languageDatabaseDir.getPath());
    }
    return new File(languageDatabaseDir, "main").getPath();
  }

  // Wake lock

  private final Object wakeLockSync = new Object();
  private PowerManager.WakeLock wakeLock;
  private int wakeLockReferenceCount;

  @SuppressWarnings("WakelockTimeout")
  private boolean addWakeLockReference () {
    synchronized (wakeLockSync) {
      if (wakeLock == null) {
        try {
          PowerManager powerManager = (PowerManager) UI.getAppContext().getSystemService(Context.POWER_SERVICE);
          if (powerManager == null)
            return false;
          wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tgx:main");
          if (wakeLock == null)
            return false;
          wakeLock.setReferenceCounted(true);
        } catch (Throwable t) {
          Log.e("Cannot create wake lock", t);
          return false;
        }
      }
      try {
        wakeLock.acquire();
        wakeLockReferenceCount++;
        return true;
      } catch (Throwable t) {
        Log.e("Cannot acquire wake lock", t);
        return false;
      }
    }
  }

  private boolean removeWakeLockReference () {
    synchronized (wakeLockSync) {
      if (wakeLockReferenceCount <= 0)
        throw new IllegalStateException();
      if (wakeLock == null)
        throw new NullPointerException();
      try {
        wakeLock.release();
        wakeLockReferenceCount--;
        return true;
      } catch (Throwable t) {
        Log.e("Cannot release wake lock", t);
        return false;
      }
    }
  }

  public void runWithWakeLock (@NonNull RunnableData<TdlibManager> runnable) {
    boolean locked = addWakeLockReference();
    try {
      runnable.runWithData(this);
    } finally {
      if (locked) {
        removeWakeLockReference();
      }
    }
  }

  public static boolean inUiThread () {
    return Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper();
  }

  public static boolean inBackgroundThread () {
    return !inUiThread();
  }

  public void runWithLatch (@NonNull RunnableData<CountDownLatch> runnable) {
    boolean isUi = inUiThread();
    CountDownLatch latch = isUi ? null : new CountDownLatch(1);
    runWithWakeLock(manager -> runnable.runWithData(latch));
  }

  public void runOnUiThread (Runnable runnable) {
    UI.post(runnable); // TODO special handler
  }

  // Language

  public static TdApi.LanguagePackStringValue getString (String languageDatabasePath, String key, @NonNull String languagePackId) {
    if (StringUtils.isEmpty(key))
      return null;
    final TdApi.LanguagePackStringValue value;
    try {
      value = Client.execute(new TdApi.GetLanguagePackString(languageDatabasePath, BuildConfig.LANGUAGE_PACK, languagePackId, key));
    } catch (Client.ExecutionException error) {
      if (error.error.code != 404) {
        Log.e("getString %s error:%s, languagePackId:%s", key, TD.toErrorString(error.error), languagePackId);
      }
      return null;
    }
    switch (value.getConstructor()) {
      case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR:
      case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR:
        return value;
      case TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR:
        return null;
      default:
        Td.assertLanguagePackStringValue_11536986();
        throw Td.unsupported(value);
    }
  }

  private TdApi.LanguagePackStringValue getString (String key, @NonNull String languagePackId) {
    return getString(languageDatabasePath, key, languagePackId);
  }

  @Nullable
  public TdApi.LanguagePackStringValueOrdinary getStringValue (String key, @NonNull String languageCode) {
    TdApi.LanguagePackStringValue string = getString(key, languageCode);
    if (string instanceof TdApi.LanguagePackStringValueOrdinary) {
      return (TdApi.LanguagePackStringValueOrdinary) string;
    } else if (string != null) {
      if (BuildConfig.DEBUG) {
        throw new IllegalStateException("Expected stringValue, received: " + string);
      }
      Log.e("Expected stringValue: %s", string);
    }
    return null;
  }

  @Nullable
  public TdApi.LanguagePackStringValuePluralized getStringPluralized (String key, @NonNull String languageCode) {
    TdApi.LanguagePackStringValue string = getString(key, languageCode);
    if (string instanceof TdApi.LanguagePackStringValuePluralized) {
      return (TdApi.LanguagePackStringValuePluralized) string;
    } else if (string != null) {
      if (BuildConfig.DEBUG) {
        throw new IllegalStateException("Expected stringPluralized, received: " + string);
      }
      Log.e("Expected stringPluralized: %s", string);
    }
    return null;
  }
}
