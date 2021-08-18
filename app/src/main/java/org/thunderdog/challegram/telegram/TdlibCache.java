package org.thunderdog.challegram.telegram;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.SparseIntArray;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.voip.VoIPController;
import org.thunderdog.challegram.voip.gui.CallSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceIntMap;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceLongMap;
import me.vkryl.core.reference.ReferenceMap;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

/**
 * Date: 30/10/2016
 * Author: default
 */

public class TdlibCache implements LiveLocationManager.OutputDelegate, CleanupStartupDelegate, UI.StateListener {
  public interface UserDataChangeListener {
    void onUserUpdated (TdApi.User user);
    default void onUserFullUpdated (int userId, TdApi.UserFullInfo userFull) { }
  }

  public interface UserStatusChangeListener {
    @UiThread
    void onUserStatusChanged (int userId, TdApi.UserStatus status, boolean uiOnly);
    default boolean needUserStatusUiUpdates () { return false; }
  }

  public interface MyUserDataChangeListener {
    void onMyUserUpdated (TdApi.User myUser);
    void onMyUserBioUpdated (String newBio);
  }

  public interface BasicGroupDataChangeListener {
    default void onBasicGroupUpdated (TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) { }
    default void onBasicGroupFullUpdated (int basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) { }
  }

  public interface SupergroupDataChangeListener {
    default void onSupergroupUpdated (TdApi.Supergroup supergroup) { }
    default void onSupergroupFullUpdated (int supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) { }
  }

  public interface SecretChatDataChangeListener {
    void onSecretChatUpdated (TdApi.SecretChat secretChat);
  }

  public interface CallStateChangeListener {
    void onCallUpdated (TdApi.Call call);
    void onCallStateChanged (int callId, int newState);
    void onCallSettingsChanged (int callId, CallSettings settings);
    void onCallBarsCountChanged (int callId, int barsCount);
  }

  public interface ChatMemberStatusChangeListener {
    void onChatMemberStatusChange (long chatId, TdApi.ChatMember member);
  }

  private final Tdlib tdlib;
  private volatile int myUserId;

  private final HashMap<Integer, TdApi.User> users = new HashMap<>();
  private final HashMap<Integer, TdApi.UserFullInfo> userFulls = new HashMap<>();
  private final ReferenceIntMap<UserDataChangeListener> userListeners = new ReferenceIntMap<>(true);

  private final ReferenceMap.FullnessListener<Integer, UserStatusChangeListener> statusFullnessListener = (list, isFull) -> setRefreshNeeded(isFull);
  private final ReferenceIntMap<UserStatusChangeListener> statusListeners = new ReferenceIntMap<>(true, statusFullnessListener);
  private final ReferenceIntMap<UserStatusChangeListener> simpleStatusListeners = new ReferenceIntMap<>(true, null);
  private final ReferenceList<MyUserDataChangeListener> myUserListeners = new ReferenceList<>(true);

  private final HashMap<Integer, TdApi.BasicGroup> basicGroup = new HashMap<>();
  private final HashMap<Integer, TdApi.BasicGroupFullInfo> basicGroupFull = new HashMap<>();
  private final ReferenceList<BasicGroupDataChangeListener> groupsGlobalListeners = new ReferenceList<>(true);
  private final ReferenceIntMap<BasicGroupDataChangeListener> groupListeners = new ReferenceIntMap<>(true);

  private final HashMap<Integer, TdApi.Supergroup> supergroups = new HashMap<>();
  private final HashMap<Integer, TdApi.SupergroupFullInfo> supergroupsFulls = new HashMap<>();
  private final ReferenceList<SupergroupDataChangeListener> supergroupsGlobalListeners = new ReferenceList<>();
  private final ReferenceIntMap<SupergroupDataChangeListener> supergroupListeners = new ReferenceIntMap<>();

  private final HashMap<Integer, TdApi.SecretChat> secretChats = new HashMap<>();
  private final ReferenceList<SecretChatDataChangeListener> secretChatsGlobalListeners = new ReferenceList<>();
  private final ReferenceIntMap<SecretChatDataChangeListener> secretChatListeners = new ReferenceIntMap<>();

  private final SparseArrayCompat<TdApi.Call> calls = new SparseArrayCompat<>();
  private final SparseArrayCompat<CallSettings> callSettings = new SparseArrayCompat<>();
  private final ReferenceList<CallStateChangeListener> callsGlobalListeners = new ReferenceList<>();
  private final ReferenceIntMap<CallStateChangeListener> callListeners = new ReferenceIntMap<>();

  private final ReferenceList<ChatMemberStatusChangeListener> chatMemberStatusGlobalListeners = new ReferenceList<>(true);
  private final ReferenceLongMap<ChatMemberStatusChangeListener> chatMemberStatusListeners = new ReferenceLongMap<>(true);

  private final ArrayList<TdApi.Message> outputLocations = new ArrayList<>();

  private boolean loadingMyUser;
  private final Client.ResultHandler meHandler, dataHandler;

  private final SparseIntArray pendingStatusRefresh = new SparseIntArray();
  private final Handler onlineHandler;

  private final Client.ResultHandler locationListHandler = object -> {
    switch (object.getConstructor()) {
      case TdApi.Messages.CONSTRUCTOR:
        replaceOutputLocationList(((TdApi.Messages) object).messages);
        break;
      case TdApi.Error.CONSTRUCTOR:
        Log.i("Unable to load active live locations: %s", TD.toErrorString(object));
        break;
    }
  };

  private final Object dataLock = new Object();

  private TdApi.HttpUrl downloadUrl;

  private static class OnlineHandler extends Handler {
    private final TdlibCache context;

    public OnlineHandler (TdlibCache context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      final int userId = msg.what;
      final int wasOnline = msg.arg1;
      final TdApi.User user = context.user(userId);
      context.onUserStatusUpdate(userId, wasOnline, user);
    }
  }

  @UiThread
  private void onUserStatusUpdate (int userId, int wasOnline, @Nullable TdApi.User user) {
    if (user == null) {
      return;
    }
    synchronized (onlineMutex) {
      if (user.status == null) {
        return;
      }
      if (user.status.getConstructor() != TdApi.UserStatusOffline.CONSTRUCTOR) {
        return;
      }
      if (((TdApi.UserStatusOffline) user.status).wasOnline != wasOnline) {
        return;
      }
      pendingStatusRefresh.delete(userId);
    }
    notifyUserStatusChanged(userId, user.status, true);
    synchronized (onlineMutex) {
      checkUserStatus(user, user.status, false);
    }
  }

  TdlibCache (Tdlib tdlib) {
    this.tdlib = tdlib;

    this.meHandler = object -> {
      switch (object.getConstructor()) {
        case TdApi.User.CONSTRUCTOR: {
          loadingMyUser = false;
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
        default: {
          Log.unexpectedTdlibResponse(object, TdApi.GetMe.class, TdApi.User.class, TdApi.Error.class);
          break;
        }
      }
    };
    this.dataHandler = object -> {
      switch (object.getConstructor()) {
        case TdApi.User.CONSTRUCTOR: {
          TdApi.User user = (TdApi.User) object;
          break;
        }
        case TdApi.UserFullInfo.CONSTRUCTOR: {
          TdApi.UserFullInfo userFull = (TdApi.UserFullInfo) object;
          break;
        }
        case TdApi.BasicGroup.CONSTRUCTOR: {
          TdApi.BasicGroup basicGroup = (TdApi.BasicGroup) object;
          break;
        }
        case TdApi.BasicGroupFullInfo.CONSTRUCTOR: {
          TdApi.BasicGroupFullInfo groupFull = (TdApi.BasicGroupFullInfo) object;
          break;
        }
        case TdApi.Supergroup.CONSTRUCTOR: {
          TdApi.Supergroup supergroup = (TdApi.Supergroup) object;
          break;
        }
        case TdApi.SupergroupFullInfo.CONSTRUCTOR: {
          TdApi.SupergroupFullInfo supergroupFull = (TdApi.SupergroupFullInfo) object;
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
        default: {
          Log.unexpectedTdlibResponse(object, TdApi.GetUserFullInfo.class, TdApi.UserFullInfo.class, TdApi.BasicGroupFullInfo.class, TdApi.SupergroupFullInfo.class, TdApi.Error.class, TdApi.User.class);
          break;
        }
      }
    };

    this.onlineHandler = new OnlineHandler(this);

    tdlib.listeners().addCleanupListener(this);

    UI.addStateListener(this);
    this.refreshUiPaused = UI.getUiState() != UI.STATE_RESUMED;
  }

  @Override
  public void onUiStateChanged (int newState) {
    setPauseStatusRefreshers(newState != UI.STATE_RESUMED);
  }

  public void getInviteText (@Nullable final RunnableData<TdApi.Text> callback) {
    Lang.getString(R.string.InviteText, BuildConfig.PROJECT_NAME, BuildConfig.DOWNLOAD_URL);
  }

  public void getDownloadUrl (@Nullable final RunnableData<TdApi.HttpUrl> callback) {
    if (downloadUrl != null) {
      if (callback != null) {
        callback.runWithData(downloadUrl);
      }
      return;
    }
    CancellableRunnable fallback = new CancellableRunnable() {
      @Override
      public void act () {
        if (callback != null) {
          callback.runWithData(new TdApi.HttpUrl(BuildConfig.DOWNLOAD_URL));
        }
        cancel();
      }
    };
    tdlib.client().send(new TdApi.GetApplicationDownloadLink(), object -> {
      switch (object.getConstructor()) {
        case TdApi.HttpUrl.CONSTRUCTOR: {
          TdApi.HttpUrl httpUrl = (TdApi.HttpUrl) object;
          if (Strings.isValidLink(httpUrl.url)) {
            tdlib.ui().post(() -> {
              downloadUrl = httpUrl;
              if (callback != null && fallback.isPending()) {
                callback.runWithData(httpUrl);
                fallback.cancel();
              }
            });
          } else {
            tdlib.ui().post(fallback);
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          tdlib.ui().post(fallback);
          break;
        }
      }
    });
    if (tdlib.context().watchDog().isOnline()) {
      UI.post(fallback, 800);
    } else {
      fallback.run();
    }
  }

  // === PUBLIC ===

  @Override
  public void onPerformStartup (boolean isAfterRestart) {
    tdlib.client().send(new TdApi.GetActiveLiveLocationMessages(), locationListHandler);
  }

  @Override
  public void onPerformUserCleanup () {
    onlineHandler.removeCallbacksAndMessages(null);
    tdlib.client().send(new TdApi.GetActiveLiveLocationMessages(), locationListHandler);
  }

  @Override
  public void onPerformRestart () {
    clear();
  }

  // Clear

  public void clear () {
    users.clear();
    userFulls.clear();
    basicGroup.clear();
    basicGroupFull.clear();
    supergroups.clear();
    supergroupsFulls.clear();
    secretChats.clear();
    calls.clear();
    callSettings.clear();
    replaceOutputLocationList(null);
    myUserId = 0;
    downloadUrl = null;
  }

  // Other

  private boolean refreshNeeded, refreshUiPaused = true;
  private boolean refreshActive;

  private void setRefreshNeeded (boolean refreshNeeded) {
    synchronized (onlineMutex) {
      if (this.refreshNeeded != refreshNeeded) {
        this.refreshNeeded = refreshNeeded;
        Log.i("accountId:%d refreshNeeded -> %b", tdlib.id(), refreshNeeded);
        checkRefreshActivity();
      }
    }
  }

  private void checkRefreshActivity () {
    boolean refreshActive = refreshNeeded && !refreshUiPaused;
    if (this.refreshActive != refreshActive) {
      this.refreshActive = refreshActive;
      Log.i("accountId:%d refreshActive -> %b, size:%d", tdlib.id(), refreshActive, pendingStatusRefresh.size());
      if (!refreshActive) {
        onlineHandler.removeCallbacksAndMessages(null);
        return;
      }
      long ms = SystemClock.elapsedRealtime();
      final int size = pendingStatusRefresh.size();
      for (int i = size - 1; i >= 0; i--) {
        final int userId = pendingStatusRefresh.keyAt(i);
        final int wasOnline = pendingStatusRefresh.valueAt(i);
        final TdApi.User user = users.get(userId);
        if (user != null) {
          checkUserStatus(user, user.status, true);
        }
      }
      Log.i("%d iterations in %dms", pendingStatusRefresh.size(), SystemClock.elapsedRealtime() - ms);
    }
  }

  private void setPauseStatusRefreshers (boolean pause) {
    synchronized (onlineMutex) {
      if (this.refreshUiPaused != pause) {
        this.refreshUiPaused = pause;
        Log.i("accountId:%d refreshUiPaused -> %b", tdlib.id(), refreshUiPaused);
        checkRefreshActivity();
      }
    }
  }

  // Update

  @UiThread
  public void onUpdateMyUserAbout (final String newAbout) {
    if (myUserId != 0) {
      notifyMyUserBioListeners(myUserListeners.iterator(), newAbout);
    }
  }

  @TdlibThread
  void onUpdateMyUserId (int userId) {
    TdApi.User myUser;
    synchronized (dataLock) {
      if (this.myUserId == userId) {
        return;
      }
      this.myUserId = userId;
      myUser = userId != 0 ? users.get(userId) : null;
    }

    if (userId != 0) {
      if (myUser != null) {
        notifyMyUserListeners(myUserListeners.iterator(), myUser);
        tdlib.downloadMyUser(myUser);
      } else if (!loadingMyUser) {
        loadingMyUser = true;
        tdlib.client().send(new TdApi.GetMe(), meHandler);
      }
    } else {
      notifyMyUserListeners(myUserListeners.iterator(), null);
      tdlib.downloadMyUser(null);
    }

    if (myUser != null) {
      tdlib.context().onUpdateAccountProfile(tdlib.id(), myUser, true);
    }
  }

  @TdlibThread
  void onUpdateUser (TdApi.UpdateUser update) {
    boolean statusChanged;
    boolean isMe;
    boolean hadUser;
    TdApi.User newUser = update.user;
    synchronized (dataLock) {
      TdApi.User oldUser = users.get(newUser.id);
      if (hadUser = oldUser != null) {
        statusChanged = !Td.equalsTo(oldUser.status, newUser.status);
        Td.copyTo(newUser, oldUser);
        synchronized (onlineMutex) {
          oldUser.status = newUser.status;
        }
        newUser = oldUser;
      } else {
        statusChanged = false;
        users.put(newUser.id, newUser);
      }
    }

    notifyUserListeners(newUser);
    if (isMe = (newUser.id == myUserId)) {
      notifyMyUserListeners(myUserListeners.iterator(), newUser);
      tdlib.downloadMyUser(newUser);
      tdlib.notifications().onUpdateMyUser(newUser);
    }

    if (statusChanged) {
      onUpdateUserStatus(new TdApi.UpdateUserStatus(newUser.id, newUser.status));
    } else {
      synchronized (onlineMutex) {
        checkUserStatus(newUser, newUser.status, false);
      }
    }

    if (isMe) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        TdlibNotificationChannelGroup.updateGroup(newUser);
      }
      tdlib.context().onUpdateAccountProfile(tdlib.id(), newUser, !hadUser);
    }
  }

  @TdlibThread
  void onUpdateUserFull (final TdApi.UpdateUserFullInfo update) {
    boolean updated;
    synchronized (dataLock) {
      updated = putUserFull(update.userId, update.userFullInfo);
    }
    if (updated) {
      notifyUserFullListeners(update.userId, update.userFullInfo);
    }
    if (myUserId != 0 && update.userId == myUserId) {
      tdlib.uiExecute(() -> notifyMyUserBioListeners(myUserListeners.iterator(), update.userFullInfo.bio));
    }
  }

  // User statuses

  private final Object onlineMutex = new Object();

  @AnyThread
  private void checkUserStatus (TdApi.User user, TdApi.UserStatus status, boolean force) {
    if (status.getConstructor() == TdApi.UserStatusOffline.CONSTRUCTOR) {
      int wasOnline = ((TdApi.UserStatusOffline) status).wasOnline;
      int pendingWasOnline = pendingStatusRefresh.get(user.id);
      if (pendingWasOnline == wasOnline && !force) {
        return;
      }
      if (pendingWasOnline != 0) {
        onlineHandler.removeMessages(user.id);
        pendingStatusRefresh.delete(user.id);
      }
      long nextRefreshInMs = Lang.getNextRelativeDateUpdateMs(wasOnline, TimeUnit.SECONDS, tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS, true, 60);
      if (nextRefreshInMs != -1) {
        pendingStatusRefresh.put(user.id, wasOnline);
        if (refreshActive) {
          Message msg = Message.obtain(onlineHandler, user.id, wasOnline, 0);
          if (force) {
            onlineHandler.sendMessage(msg);
          } else {
            onlineHandler.sendMessageDelayed(msg, nextRefreshInMs);
          }
        }
      }
    } else {
      int pendingWasOnline = pendingStatusRefresh.get(user.id);
      if (pendingWasOnline != 0) {
        onlineHandler.removeMessages(user.id);
        pendingStatusRefresh.delete(user.id);
      }
    }
  }

  @TdlibThread
  void onUpdateUserStatus (final TdApi.UpdateUserStatus update) {
    synchronized (dataLock) {
      TdApi.User user = users.get(update.userId);
      if (user == null) {
        return;
      }
      synchronized (onlineMutex) {
        user.status = update.status;
      }
    }
    tdlib.dispatchUserStatus(update, false);
  }

  @UiThread
  void onUpdateUserStatusInternal (TdApi.UpdateUserStatus update, boolean uiOnly) {
    TdApi.User user;
    synchronized (dataLock) {
      user = users.get(update.userId);
      if (user == null) {
        return;
      }
    }
    notifyUserStatusChanged(update.userId, user.status, uiOnly);
    synchronized (onlineMutex) {
      checkUserStatus(user, user.status, false);
    }
  }

  // User Blacklist

  @TdlibThread
  void onUpdateBasicGroup (TdApi.UpdateBasicGroup update) {
    boolean updated;
    int migratedToSupergroupId;
    synchronized (dataLock) {
      TdApi.BasicGroup group = update.basicGroup;
      TdApi.BasicGroup oldGroup = basicGroup.get(group.id);
      basicGroup.put(group.id, group);
      updated = oldGroup != null;
      migratedToSupergroupId = updated && oldGroup.upgradedToSupergroupId == 0 ? group.upgradedToSupergroupId : 0;
    }
    if (updated) {
      notifyListeners(groupsGlobalListeners.iterator(), update.basicGroup, migratedToSupergroupId != 0);
      notifyListeners(groupListeners.iterator(update.basicGroup.id), update.basicGroup, migratedToSupergroupId != 0);
    }
  }

  @TdlibThread
  void onUpdateBasicGroupFull (TdApi.UpdateBasicGroupFullInfo update) {
    boolean updated;
    synchronized (dataLock) {
      updated = putGroupFull(update.basicGroupId, update.basicGroupFullInfo);
    }
    if (updated) {
      notifyListeners(groupsGlobalListeners.iterator(), update.basicGroupId, update.basicGroupFullInfo);
      notifyListeners(groupListeners.iterator(update.basicGroupId), update.basicGroupId, update.basicGroupFullInfo);
    }
  }

  @TdlibThread
  void onUpdateSupergroup (TdApi.UpdateSupergroup update, @Nullable TdApi.Chat chat) {
    final TdApi.Supergroup supergroup = update.supergroup;
    int updateMode;
    synchronized (dataLock) {
      updateMode = putSupergroup(supergroup);
    }
    if (updateMode != UPDATE_MODE_NONE) {
      notifyListeners(supergroupsGlobalListeners.iterator(), supergroup);
      notifyListeners(supergroupListeners.iterator(supergroup.id), supergroup);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && updateMode == UPDATE_MODE_IMPORTANT) {
      if (chat != null) {
        TdlibNotificationChannelGroup.updateChat(tdlib, myUserId, chat);
      }
    }
  }

  @TdlibThread
  void onUpdateSupergroupFull (TdApi.UpdateSupergroupFullInfo update) {
    final int supergroupId = update.supergroupId;
    final TdApi.SupergroupFullInfo supergroupFullInfo = update.supergroupFullInfo;
    boolean updated;
    synchronized (dataLock) {
      updated = putSupergroupFull(supergroupId, supergroupFullInfo);
    }
    if (updated) {
      notifyListeners(supergroupsGlobalListeners.iterator(), supergroupId, supergroupFullInfo);
      notifyListeners(supergroupListeners.iterator(supergroupId), supergroupId, supergroupFullInfo);
    }
  }

  @TdlibThread
  public void onUpdateSecretChat (TdApi.UpdateSecretChat update) {
    final TdApi.SecretChat secretChat = update.secretChat;
    boolean updated;
    synchronized (dataLock) {
      updated = putSecretChat(secretChat);
    }
    if (updated) {
      notifyListeners(secretChatsGlobalListeners.iterator(), secretChat);
      notifyListeners(secretChatListeners.iterator(secretChat.id), secretChat);
    }
  }

  // Calls

  @UiThread
  void onCallStateChanged (final int callId, final int newState) {
    if (newState == VoIPController.STATE_ESTABLISHED) {
      notifyCallListeners(callsGlobalListeners.iterator(), callId, newState, false);
      notifyCallListeners(callListeners.iterator(callId), callId, newState, false);
    }
  }

  @UiThread
  void onCallSignalBarsChanged (final int callId, final int barsCount) {
    notifyCallListeners(callsGlobalListeners.iterator(), callId, barsCount, true);
    notifyCallListeners(callListeners.iterator(callId), callId, barsCount, true);
  }

  public CallSettings getCallSettings (int callId) {
    synchronized (dataLock) {
      return callSettings.get(callId);
    }
  }

  public TdApi.Call getPendingCall () {
    synchronized (dataLock) {
      final int size = calls.size();
      for (int i = size - 1; i >= 0; i--) {
        TdApi.Call call = calls.valueAt(i);
        if (!TD.isFinished(call)) {
          return call;
        }
      }
    }
    return null;
  }

  public TdApi.Call getCall (int callId) {
    synchronized (dataLock) {
      return calls.get(callId);
    }
  }

  @UiThread
  public void onUpdateCall (TdApi.UpdateCall update) {
    final TdApi.Call call = update.call;
    int delta;
    synchronized (dataLock) {
      TdApi.Call oldCall = calls.get(call.id);
      boolean wasActive = !TD.isFinished(oldCall);
      calls.put(call.id, call);
      boolean nowActive = !TD.isFinished(call);
      delta = wasActive != nowActive ? (nowActive ? 1 : -1) : 0;
    }
    notifyListeners(callsGlobalListeners.iterator(), call);
    notifyListeners(callListeners.iterator(call.id), call);
    tdlib.context().global().notifyCallUpdated(tdlib, call);
    if (delta == 1) {
      tdlib.incrementCallReferenceCount();
    } else if (delta == -1) {
      tdlib.decrementCallReferenceCount();
    }
  }

  @UiThread
  public void onUpdateCallSettings (int callId, CallSettings settings) {
    synchronized (dataLock) {
      callSettings.put(callId, settings);
    }
    notifyListeners(callsGlobalListeners.iterator(), callId, settings);
    notifyListeners(callListeners.iterator(callId), callId, settings);
    tdlib.context().global().notifyCallSettingsChanged(tdlib, callId, settings);
  }

  // Listeners

  public void subscribeToAnyUpdates (Object any) {
    if (any instanceof UserDataChangeListener) {
      __putGlobalUserDataListener((UserDataChangeListener) any);
    }
    if (any instanceof UserStatusChangeListener) {
      __putGlobalStatusListener((UserStatusChangeListener) any);
    }
    if (any instanceof BasicGroupDataChangeListener) {
      putGlobalBasicGroupListener((BasicGroupDataChangeListener) any);
    }
    if (any instanceof SupergroupDataChangeListener) {
      putGlobalSupergroupListener((SupergroupDataChangeListener) any);
    }
    if (any instanceof SecretChatDataChangeListener) {
      putGlobalSecretChatListener((SecretChatDataChangeListener) any);
    }
    if (any instanceof CallStateChangeListener) {
      putGlobalCallListener((CallStateChangeListener) any);
    }
  }

  public void unsubscribeFromAnyUpdates (Object any) {
    if (any instanceof UserDataChangeListener) {
      __deleteGlobalUserDataListener((UserDataChangeListener) any);
    }
    if (any instanceof UserStatusChangeListener) {
      __deleteGlobalStatusListener((UserStatusChangeListener) any);
    }
    if (any instanceof BasicGroupDataChangeListener) {
      deleteGlobalBasicGroupListener((BasicGroupDataChangeListener) any);
    }
    if (any instanceof SupergroupDataChangeListener) {
      deleteGlobalSupergroupListener((SupergroupDataChangeListener) any);
    }
    if (any instanceof SecretChatDataChangeListener) {
      deleteGlobalSecretChatListener((SecretChatDataChangeListener) any);
    }
    if (any instanceof CallStateChangeListener) {
      deleteGlobalCallListener((CallStateChangeListener) any);
    }
  }

  public void addGlobalCallsListener (CallStateChangeListener listener) {
    putGlobalCallListener(listener);
  }

  public void removeGlobalCallListener (CallStateChangeListener listener) {
    deleteGlobalCallListener(listener);
  }

  public void addGlobalChatMemberStatusListener (ChatMemberStatusChangeListener listener) {
    chatMemberStatusGlobalListeners.add(listener);
  }

  public void removeGlobalChatMemberStatusListener (ChatMemberStatusChangeListener listener) {
    chatMemberStatusGlobalListeners.remove(listener);
  }

  public void addChatMemberStatusListener (long chatId, ChatMemberStatusChangeListener listener) {
    chatMemberStatusListeners.add(chatId, listener);
  }

  public void removeChatMemberStatusListener (long chatId, ChatMemberStatusChangeListener listener) {
    chatMemberStatusListeners.remove(chatId, listener);
  }

  @AnyThread
  void onChatMemberStatusChanged (long chatId, TdApi.ChatMember member) {
    for (ChatMemberStatusChangeListener listener : chatMemberStatusGlobalListeners) {
      listener.onChatMemberStatusChange(chatId, member);
    }
    Iterator<ChatMemberStatusChangeListener> itr = chatMemberStatusListeners.iterator(chatId);
    if (itr != null) {
      while (itr.hasNext()) {
        itr.next().onChatMemberStatusChange(chatId, member);
      }
    }
  }

  public <T extends UserDataChangeListener & UserStatusChangeListener> void subscribeToUserUpdates (int userId, T listener) {
    __putUserListener(userId, listener);
    subscribeToUserStatusChanges(userId, listener);
  }

  public <T extends UserDataChangeListener & UserStatusChangeListener> void unsubscribeFromUserUpdates (int userId, T listener) {
    __deleteUserListener(userId, listener);
    unsubscribeFromUserStatusChanges(userId, listener);
  }

  public <T extends UserDataChangeListener & UserStatusChangeListener> void subscribeToUserUpdates (int[] userIds, T listener) {
    for (int userId : userIds) {
      __putUserListener(userId, listener);
      subscribeToUserStatusChanges(userId, listener);
    }
  }

  public <T extends UserDataChangeListener & UserStatusChangeListener> void unsubscribeFromUserUpdates (int[] userIds, T listener) {
    for (int userId : userIds) {
      __deleteUserListener(userId, listener);
      unsubscribeFromUserStatusChanges(userId, listener);
    }
  }

  public void subscribeToGroupUpdates (int groupId, BasicGroupDataChangeListener listener) {
    putGroupListener(groupId, listener);
  }

  public void unsubscribeFromGroupUpdates (int groupId, BasicGroupDataChangeListener listener) {
    deleteGroupListener(groupId, listener);
  }

  public void subscribeToSupergroupUpdates (int supergroupId, SupergroupDataChangeListener listener) {
    putSupergroupListener(supergroupId, listener);
  }

  public void unsubscribeFromSupergroupUpdates (int supergroupId, SupergroupDataChangeListener listener) {
    deleteSupergroupListener(supergroupId, listener);
  }

  public void subscribeToSecretChatUpdates (int secretChatId, SecretChatDataChangeListener listener) {
    putSecretChatListener(secretChatId, listener);
  }

  public void unsubscribeFromSecretChatUpdates (int secretChatId, SecretChatDataChangeListener listener) {
    deleteSecretChatListener(secretChatId, listener);
  }

  public void subscribeToCallUpdates (int callId, CallStateChangeListener listener) {
    putCallListener(callId, listener);
  }

  public void unsubscribeFromCallUpdates (int callId, CallStateChangeListener listener) {
    putCallListener(callId, listener);
  }

  public void addMyUserListener (MyUserDataChangeListener listener) {
    myUserListeners.add(listener);
  }

  public void removeMyUserListener (MyUserDataChangeListener listener) {
    myUserListeners.remove(listener);
  }

  // Getters

  public ArrayList<TdApi.User> users (int[] userIds) {
    final ArrayList<TdApi.User> out = new ArrayList<>(userIds.length);
    users(userIds, out);
    return out;
  }

  public int users (int[] userIds, ArrayList<TdApi.User> out) {
    int addedCount = 0;
    synchronized (dataLock) {
      for (int userId : userIds) {
        if (userId != 0) {
          TdApi.User user = users.get(userId);
          if (user != null) {
            out.add(user);
            addedCount++;
          } else {
            Log.bug("updateUser missing for userId:%d", userId);
          }
        }
      }
    }
    return addedCount;
  }

  public @Nullable TdApi.User user (int userId) {
    if (userId == 0) {
      Log.bug("getUser for userId=0");
      return null;
    }
    synchronized (dataLock) {
      TdApi.User user = users.get(userId);
      if (user == null)
        Log.bug("updateUser missing for userId:%d", userId);
      return user;
    }
  }

  @NonNull
  public TdApi.User userStrict (int userId) {
    if (userId == 0)
      throw new IllegalArgumentException();
    synchronized (dataLock) {
      TdApi.User user = users.get(userId);
      if (user == null)
        throw new IllegalStateException("id" + userId);
      return user;
    }
  }

  public boolean userLastSeenAvailable (int userId) {
    if (userId == 0)
      return false;
    synchronized (dataLock) {
      TdApi.User user = users.get(userId);
      TdApi.UserStatus status = user != null ? user.status : null;
      return status != null && status.getConstructor() == TdApi.UserStatusOffline.CONSTRUCTOR && ((TdApi.UserStatusOffline) status).wasOnline != 0;
    }
  }

  public boolean userGeneral (int userId) {
    return userId != 0 && TD.isGeneralUser(user(userId));
  }

  public int userAvatarColorId (int userId) {
    return userAvatarColorId(userId != 0 ? user(userId) : null);
  }

  public int userAvatarColorId (TdApi.User user) {
    return TD.getAvatarColorId(user == null || TD.isUserDeleted(user) ? -1 : user.id, myUserId);
  }

  public Letters userLetters (TdApi.User user) {
    return TD.getLetters(user);
  }

  public Letters userLetters (int userId) {
    TdApi.User user = user(userId);
    return user != null ? TD.getLetters(user) : TD.getLetters();
  }

  public AvatarPlaceholder.Metadata selfPlaceholderMetadata () {
    return new AvatarPlaceholder.Metadata(R.id.theme_color_avatarSavedMessages, (String) null, R.drawable.baseline_bookmark_24, 0);
  }

  public AvatarPlaceholder.Metadata userPlaceholderMetadata (@Nullable TdApi.User user, boolean allowSavedMessages) {
    if (user == null) {
      return null;
    }
    Letters avatarLetters = null;
    int avatarColorId;
    int desiredDrawableRes = 0;
    int extraDrawableRes = 0;
    if (allowSavedMessages && tdlib.isSelfUserId(user.id)) {
      avatarColorId = R.id.theme_color_avatarSavedMessages;
      desiredDrawableRes = R.drawable.baseline_bookmark_24;
    } else {
      if (user.id == TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID) {
        desiredDrawableRes = R.drawable.baseline_reply_24;
        avatarColorId = R.id.theme_color_avatarReplies;
      } else {
        avatarLetters = userLetters(user);
        avatarColorId = userAvatarColorId(user);
      }
      extraDrawableRes = tdlib.isSelfUserId(user.id) ? R.drawable.ic_add_a_photo_black_56 :
        user.id == TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID ? R.drawable.baseline_reply_56 :
        TD.isBot(user) ? R.drawable.deproko_baseline_bots_56 :
          R.drawable.baseline_person_56;
    }
    return new AvatarPlaceholder.Metadata(avatarColorId, avatarLetters != null ? avatarLetters.text : null, desiredDrawableRes, extraDrawableRes);
  }

  public AvatarPlaceholder userPlaceholder (int userId, boolean allowSavedMessages, float radius, @Nullable DrawableProvider provider) {
    return userPlaceholder(userId, user(userId), allowSavedMessages, radius, provider);
  }

  public AvatarPlaceholder userPlaceholder (@Nullable TdApi.User user, boolean allowSavedMessages, float radius, @Nullable DrawableProvider provider) {
    return userPlaceholder(user != null ? user.id : 0, user, allowSavedMessages, radius, provider);
  }

  public @Nullable ImageFile userAvatar (int userId) {
    if (userId == 0)
      return null;
    TdApi.User user = user(userId);
    TdApi.ProfilePhoto photo = user != null ? user.profilePhoto : null;
    if (photo == null)
      return null;
    ImageFile avatarFile = new ImageFile(tdlib, photo.small);
    avatarFile.setSize(ChatView.getDefaultAvatarCacheSize());
    return avatarFile;
  }

  public AvatarPlaceholder.Metadata userPlaceholderMetadata (int userId, @Nullable TdApi.User user, boolean allowSavedMessages) {
    if (user != null || userId == 0) {
      return userPlaceholderMetadata(user, allowSavedMessages);
    } else {
      return new AvatarPlaceholder.Metadata(TD.getAvatarColorId(userId, tdlib.myUserId()));
    }
  }

  public AvatarPlaceholder userPlaceholder (int userId, @Nullable TdApi.User user, boolean allowSavedMessage, float radius, @Nullable DrawableProvider provider) {
    return new AvatarPlaceholder(radius, userPlaceholderMetadata(userId, user, allowSavedMessage), provider);
  }

  public String userDisplayName (int userId, boolean allowSavedMessages, boolean shorten) {
    if (allowSavedMessages && tdlib.isSelfUserId(userId)) {
      return Lang.getString(R.string.SavedMessages);
    }
    TdApi.User user = user(userId);
    if (TD.isUserDeleted(user)) {
      return Lang.getString(R.string.HiddenName);
    }
    if (userId == TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID) {
      return Lang.getString(R.string.RepliesBot);
    }
    if (shorten) {
      return TD.getUserSingleName(userId, user);
    }
    return TD.getUserName(userId, user);
  }

  public boolean userDeleted (int userId) {
    return userId != 0 && TD.isUserDeleted(user(userId));
  }

  public boolean userBot (int userId) {
    return userId != 0 && TD.isBot(user(userId));
  }

  public boolean senderBot (TdApi.MessageSender sender) {
    return sender != null && sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR && userBot(((TdApi.MessageSenderUser) sender).userId);
  }

  public boolean userContact (int userId) {
    return userId != 0 && TD.isContact(user(userId));
  }

  public @NonNull String userName (int userId) {
    return userId != 0 ? TD.getUserName(userId, user(userId)) : "VOID";
  }

  public String userFirstName (int userId) {
    return userId != 0 ? TD.getUserSingleName(userId, user(userId)) : "VOID";
  }

  public @Nullable String userUsername (int userId) {
    if (userId != 0) {
      TdApi.User user = user(userId);
      return user != null && !StringUtils.isEmpty(user.username) ? user.username : null;
    }
    return null;
  }

  @Nullable
  public TdApi.UserFullInfo userFull (int userId) {
    return userFull(userId, true);
  }

  @Nullable
  public TdApi.UserFullInfo userFull (int userId, boolean allowRequest) {
    TdApi.UserFullInfo userFull;
    synchronized (dataLock) {
      Integer key = userId;
      userFull = userFulls.get(key);
      if (userFull == null || allowRequest) {
        TdApi.User user = users.get(key);
        if (user != null) {
          tdlib.client().send(new TdApi.GetUserFullInfo(userId), dataHandler);
        } else {
          tdlib.client().send(new TdApi.GetUser(userId), ignored -> tdlib.client().send(new TdApi.GetUserFullInfo(userId), dataHandler));
        }
      }
    }
    return userFull;
  }

  public @Nullable TdApi.User searchUser (String username) {
    TdApi.User result = null;
    synchronized (dataLock) {
      final Set<HashMap.Entry<Integer, TdApi.User>> entries = users.entrySet();
      for (HashMap.Entry<Integer, TdApi.User> entry : entries) {
        TdApi.User user = entry.getValue();
        if (user.username != null && user.username.length() == username.length() && user.username.toLowerCase().equals(username)) {
          result = user;
          break;
        }
      }
    }
    return result;
  }

  @Nullable
  public TdApi.BasicGroup basicGroup (int groupId) {
    synchronized (dataLock) {
      return basicGroup.get(groupId);
    }
  }

  @NonNull
  public TdApi.BasicGroup basicGroupStrict (int groupId) {
    if (groupId == 0)
      throw new IllegalArgumentException();
    synchronized (dataLock) {
      TdApi.BasicGroup group = basicGroup.get(groupId);
      if (group == null)
        throw new IllegalStateException("id:" + groupId);
      return group;
    }
  }

  public boolean basicGroupActive (int groupId) {
    TdApi.BasicGroup basicGroup = basicGroup(groupId);
    return basicGroup != null && basicGroup.isActive;
  }

  @Nullable
  public TdApi.BasicGroupFullInfo basicGroupFull (int groupId) {
    return basicGroupFull(groupId, true);
  }

  @Nullable
  public TdApi.BasicGroupFullInfo basicGroupFull (int groupId, boolean allowRequest) {
    synchronized (dataLock) {
      return basicGroupFullUnsafe(groupId, allowRequest);
    }
  }

  @Nullable
  private TdApi.BasicGroupFullInfo basicGroupFullUnsafe (int groupId, boolean allowRequest) {
    Integer key = groupId;
    TdApi.BasicGroupFullInfo groupFull;
    groupFull = basicGroupFull.get(key);
    if (groupFull == null || allowRequest) {
      TdApi.BasicGroup basicGroup = this.basicGroup.get(key);
      if (basicGroup != null) {
        tdlib.client().send(new TdApi.GetBasicGroupFullInfo(groupId), dataHandler);
      } else {
        tdlib.client().send(new TdApi.GetBasicGroup(groupId), ignored -> tdlib.client().send(new TdApi.GetBasicGroupFullInfo(groupId), dataHandler));
      }
    }
    return groupFull;
  }

  @Nullable
  public TdApi.Supergroup supergroup (int supergroupId) {
    synchronized (dataLock) {
      return supergroups.get(supergroupId);
    }
  }

  @NonNull
  public TdApi.Supergroup supergroupStrict (int supergroupId) {
    if (supergroupId == 0)
      throw new IllegalArgumentException();
    synchronized (dataLock) {
      TdApi.Supergroup supergroup = supergroups.get(supergroupId);
      if (supergroup == null)
        throw new IllegalStateException("id:" + supergroupId);
      return supergroup;
    }
  }

  @Nullable
  public TdApi.SupergroupFullInfo supergroupFull (int supergroupId) {
    return supergroupFull(supergroupId, true);
  }

  @Nullable
  public TdApi.SupergroupFullInfo supergroupFull (int supergroupId, boolean allowRequest) {
    TdApi.SupergroupFullInfo result;
    synchronized (dataLock) {
      Integer key = supergroupId;
      result = supergroupsFulls.get(key);
      if (result == null || allowRequest) {
        TdApi.Supergroup supergroup = supergroups.get(key);
        if (supergroup != null) {
          tdlib.client().send(new TdApi.GetSupergroupFullInfo(supergroupId), dataHandler);
        } else {
          tdlib.client().send(new TdApi.GetSupergroup(supergroupId), ignored -> tdlib.client().send(new TdApi.GetSupergroupFullInfo(supergroupId), dataHandler));
        }
      }
    }
    return result;
  }

  public void supergroupFull (int supergroupId, RunnableData<TdApi.SupergroupFullInfo> callback) {
    if (supergroupId == 0) {
      if (callback != null) {
        callback.runWithData(null);
      }
      return;
    }
    TdApi.SupergroupFullInfo fullInfo = supergroupFull(supergroupId);
    if (callback == null)
      return;
    if (fullInfo != null) {
      callback.runWithData(fullInfo);
      return;
    }
    tdlib.client().send(new TdApi.GetSupergroupFullInfo(supergroupId), result -> {
      switch (result.getConstructor()) {
        case TdApi.SupergroupFullInfo.CONSTRUCTOR:
          tdlib.ui().post(() -> callback.runWithData(supergroupFull(supergroupId)));
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          tdlib.ui().post(() -> callback.runWithData(null));
          break;
      }
    });
  }

  public @Nullable TdApi.SecretChat secretChat (int secretChatId) {
    synchronized (dataLock) {
      return secretChats.get(secretChatId);
    }
  }

  @NonNull
  public TdApi.SecretChat secretChatStrict (int secretChatId) {
    if (secretChatId == 0)
      throw new IllegalArgumentException();
    synchronized (dataLock) {
      TdApi.SecretChat secretChat = secretChats.get(secretChatId);
      if (secretChat == null)
        throw new IllegalStateException("id:" + secretChatId);
      return secretChat;
    }
  }

  @Deprecated
  /*pacakge*/ int myUserId () {
    // TODO move myUserId to TdlibContext
    return myUserId;
  }

  public boolean isMe (int userId) {
    return myUserId == userId;
  }

  @Deprecated
  public @Nullable TdApi.User myUser () {
    // TODO move to TdlibContext
    TdApi.User result;
    synchronized (dataLock) {
      result = myUserId != 0 ? users.get(myUserId) : null;
    }
    return result;
  }

  public boolean isOnline (int userId) {
    if (userId == 0) {
      return false;
    }
    if (userId == myUserId) {
      return true;
    }
    boolean isOnline;
    synchronized (dataLock) {
      if (userId != myUserId) {
        isOnline = TD.isOnline(users.get(userId));
      } else {
        isOnline = true;
      }
    }
    return isOnline;
  }

  /*public int secondsTillOffline (int userId) {
    if (userId == 0) {
      return -1;
    }
    int time;
    synchronized (dataMutex) {
      TdApi.User user = users.get(userId);
      if (user != null && user.status.getConstructor() == TdApi.UserStatusOnline.CONSTRUCTOR) {
        time = (((TdApi.UserStatusOnline) user.status).expires) - (int) (System.currentTimeMillis() / 1000L) + 1;
      } else {
        time = -1;
      }
    }
    return time;
  }*/

  // Locations

  void onScheduledRemove (TdApi.Message outputLocation) {
    synchronized (outputLocations) {
      int i = outputLocations.indexOf(outputLocation);
      if (i != -1) {
        outputLocations.remove(i);
        notifyOutputLocationsChanged(-1);
      }
    }
  }

  void addOutputLocationMessage (TdApi.Message message) {
    if (message.sendingState != null || !message.canBeEdited || !message.isOutgoing || message.content.getConstructor() != TdApi.MessageLocation.CONSTRUCTOR) {
      return;
    }
    TdApi.MessageLocation location = (TdApi.MessageLocation) message.content;
    if (location.livePeriod == 0 || location.expiresIn == 0) {
      return;
    }
    synchronized (outputLocations) {
      this.outputLocations.add(message);
      notifyOutputLocationsChanged(1);
      tdlib.scheduleLocationRemoval(message);
    }
  }

  void deleteOutputMessages (long chatId, long[] messageIds) {
    synchronized (outputLocations) {
      if (this.outputLocations.isEmpty()) {
        return;
      }
      int removedCount = 0;
      for (int i = outputLocations.size() - 1; i >= 0; i--) {
        TdApi.Message message = outputLocations.get(i);
        if (message.chatId == chatId && ArrayUtils.indexOf(messageIds, message.id) != -1) {
          tdlib.cancelLocationRemoval(message);
          outputLocations.remove(i);
          removedCount++;
        }
      }
      if (removedCount > 0) {
        notifyOutputLocationsChanged(removedCount);
      }
    }
  }

  private void replaceOutputLocationList (TdApi.Message[] messages) {
    synchronized (outputLocations) {
      if (outputLocations.isEmpty() && (messages == null || messages.length == 0)) {
        return;
      }
      for (TdApi.Message message : outputLocations) {
        tdlib.cancelLocationRemoval(message);
      }
      int oldSize = outputLocations.size();
      outputLocations.clear();
      if (messages != null) {
        Collections.addAll(outputLocations, messages);
        notifyOutputLocationsChanged(messages.length - oldSize);
        for (TdApi.Message message : outputLocations) {
          tdlib.scheduleLocationRemoval(message);
        }
      } else {
        notifyOutputLocationsChanged(-oldSize);
      }
    }
  }

  private int indexOfOutputLocationInternal (long chatId, long messageId) {
    int i = 0;
    for (TdApi.Message outputLocation : outputLocations) {
      if (outputLocation.chatId == chatId && outputLocation.id == messageId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  void updateLiveLocation (long chatId, long messageId, TdApi.MessageLocation location) {
    if (location.livePeriod == 0) {
      return;
    }
    synchronized (outputLocations) {
      if (outputLocations.isEmpty()) {
        return;
      }
      int foundIndex = indexOfOutputLocationInternal(chatId, messageId);
      if (foundIndex == -1) {
        return;
      }
      TdApi.Message message = outputLocations.get(foundIndex);
      message.content = location;
      boolean removed = location.expiresIn == 0;
      tdlib.cancelLocationRemoval(message);
      if (removed) {
        outputLocations.remove(foundIndex);
        notifyOutputLocationsChanged(-1);
      } else {
        tdlib.scheduleLocationRemoval(message);
      }
    }
  }

  private boolean isLiveDelegateRegistered;

  private void notifyOutputLocationsChanged (int deltaCount) {
    boolean needRegister = !outputLocations.isEmpty();
    if (isLiveDelegateRegistered != needRegister) {
      if (needRegister) {
        tdlib.context().liveLocation().addDelegate(this);
      } else {
        tdlib.context().liveLocation().removeDelegate(this);
      }
      isLiveDelegateRegistered = needRegister;
    }
    tdlib.context().liveLocation().notifyOutputListChanged(tdlib, outputLocations.isEmpty() ? null : new ArrayList<>(outputLocations));
    tdlib.changeLocationReferenceCount(deltaCount);
  }

  @Override
  public void onLiveLocationDataRequest (ArrayList<Tdlib> outTdlibs, ArrayList<ArrayList<TdApi.Message>> outMessages) {
    synchronized (outputLocations) {
      if (!outputLocations.isEmpty()) {
        outTdlibs.add(tdlib);
        outMessages.add(new ArrayList<>(outputLocations));
      }
    }
  }

  public void stopLiveLocations (long chatId) {
    synchronized (outputLocations) {
      final int size = outputLocations.size();
      for (int i = size - 1; i >= 0; i--) {
        TdApi.Message msg = outputLocations.get(i);
        if (chatId == 0 || msg.chatId == chatId) {
          tdlib.client().send(new TdApi.EditMessageLiveLocation(msg.chatId, msg.id, null, null, 0, 0), tdlib.silentHandler());
        }
      }
    }
  }

  public boolean hasOutputLocations () {
    synchronized (outputLocations) {
      return !outputLocations.isEmpty();
    }
  }

  public TdApi.Message findOutputLiveLocationMessage (long chatId) {
    synchronized (outputLocations) {
      for (TdApi.Message message : outputLocations) {
        if (message.chatId == chatId) {
          return message;
        }
      }
      return null;
    }
  }

  private void onLiveLocationChanged (TdApi.Message message) {
    synchronized (outputLocations) {
      if (outputLocations.indexOf(message) == -1) {
        return;
      }
      tdlib.context().liveLocation().notifyOutputMessageEdited(tdlib, message);
    }
  }

  @Override
  public void onLiveLocationBroadcast (@Nullable TdApi.Location location, int heading) {
    synchronized (outputLocations) {
      Log.v("Updating %d live location messages", outputLocations.size());
      for (final TdApi.Message message : outputLocations) {
        tdlib.client().send(new TdApi.EditMessageLiveLocation(message.chatId, message.id, message.replyMarkup, location, heading, 0), object -> {
          switch (object.getConstructor()) {
            case TdApi.Message.CONSTRUCTOR: {
              TdApi.Message resultMessage = (TdApi.Message) object;
              message.editDate = resultMessage.editDate;
              if (resultMessage.content.getConstructor() == TdApi.MessageLocation.CONSTRUCTOR) {
                TdApi.MessageLocation in = (TdApi.MessageLocation) resultMessage.content;
                TdApi.MessageLocation out = (TdApi.MessageLocation) message.content;
                out.expiresIn = in.livePeriod;
                out.location.latitude = in.location.latitude;
                out.location.longitude = in.location.longitude;
                onLiveLocationChanged(message);
              }
              break;
            }
            case TdApi.Error.CONSTRUCTOR:
              Log.e("Error broadcasting location: %s", TD.toErrorString(object));
              break;
          }
        });
      }
    }
  }

  // === PRIVATE ===

  // Listeners

  private static void notifyListeners (@Nullable Iterator<UserDataChangeListener> list, TdApi.User user) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUserUpdated(user);
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<UserDataChangeListener> list, int userId, TdApi.UserFullInfo userFull) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUserFullUpdated(userId, userFull);
      }
    }
  }

  private static void notifyMyUserListeners (@Nullable Iterator<MyUserDataChangeListener> list, TdApi.User myUser) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMyUserUpdated(myUser);
      }
    }
  }

  private static void notifyMyUserBioListeners (@Nullable Iterator<MyUserDataChangeListener> list, String newBio) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMyUserBioUpdated(newBio);
      }
    }
  }

  private static void notifyUserStatusListeners (@Nullable Iterator<UserStatusChangeListener> list, int userId, TdApi.UserStatus status, boolean uiOnly) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUserStatusChanged(userId, status, uiOnly);
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<BasicGroupDataChangeListener> list, TdApi.BasicGroup group, boolean migratedToSupergroup) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onBasicGroupUpdated(group, migratedToSupergroup);
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<SupergroupDataChangeListener> list, TdApi.Supergroup supergroup) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onSupergroupUpdated(supergroup);
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<SecretChatDataChangeListener> list, TdApi.SecretChat secretChat) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onSecretChatUpdated(secretChat);
      }
    }
  }

  private static void notifyCallListeners (@Nullable Iterator<CallStateChangeListener> list, int callId, int arg1, boolean isStrength) {
    if (list != null) {
      if (isStrength) {
        while (list.hasNext()) {
          list.next().onCallBarsCountChanged(callId, arg1);
        }
      } else {
        while (list.hasNext()) {
          list.next().onCallStateChanged(callId, arg1);
        }
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<CallStateChangeListener> list, int callId, CallSettings settings) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onCallSettingsChanged(callId, settings);
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<CallStateChangeListener> list, TdApi.Call call) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onCallUpdated(call);
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<BasicGroupDataChangeListener> list, int groupId, TdApi.BasicGroupFullInfo groupFull) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onBasicGroupFullUpdated(groupId, groupFull);
      }
    }
  }

  private static void notifyListeners (@Nullable Iterator<SupergroupDataChangeListener> list, int supergroupId, TdApi.SupergroupFullInfo supergroupFull) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onSupergroupFullUpdated(supergroupId, supergroupFull);
      }
    }
  }

  // User listeners

  // data
  private void __putGlobalUserDataListener (UserDataChangeListener listener) {
    userListeners.add(0, listener);
  }
  private void __deleteGlobalUserDataListener (UserDataChangeListener listener) {
    userListeners.remove(0, listener);
  }
  private void __putUserListener (int userId, UserDataChangeListener listener) {
    if (userId == 0) {
      throw new IllegalArgumentException("userId == " + userId);
    }
    userListeners.add(userId, listener);
  }
  private void __deleteUserListener (int userId, UserDataChangeListener listener) {
    if (userId == 0) {
      throw new IllegalArgumentException("userId == " + userId);
    }
    userListeners.remove(userId, listener);
  }
  // status
  private void __putGlobalStatusListener (UserStatusChangeListener listener) {
    if (listener.needUserStatusUiUpdates()) {
      statusListeners.add(0, listener);
    } else {
      simpleStatusListeners.add(0, listener);
    }
  }
  private void __deleteGlobalStatusListener (UserStatusChangeListener listener) {
    if (listener.needUserStatusUiUpdates()) {
      statusListeners.remove(0, listener);
    } else {
      simpleStatusListeners.remove(0, listener);
    }
  }
  public void subscribeToUserStatusChanges (int userId, UserStatusChangeListener listener) {
    if (userId == 0)
      throw new IllegalArgumentException("userId == " + userId);
    if (listener.needUserStatusUiUpdates()) {
      statusListeners.add(userId, listener);
    } else {
      simpleStatusListeners.add(userId, listener);
    }
  }
  public void unsubscribeFromUserStatusChanges (int userId, UserStatusChangeListener listener) {
    if (userId == 0)
      throw new IllegalArgumentException("userId == " + userId);
    if (listener.needUserStatusUiUpdates()) {
      statusListeners.remove(userId, listener);
    } else {
      simpleStatusListeners.remove(userId, listener);
    }
  }
  // public
  public void putGlobalUserDataListener (UserDataChangeListener listener) {
    __putGlobalUserDataListener(listener);
  }
  public void deleteGlobalUserDataListener (UserDataChangeListener listener) {
    __deleteGlobalUserDataListener(listener);
  }

  public void addUserDataListener (int userId, UserDataChangeListener listener) {
    __putUserListener(userId, listener);
  }
  public void removeUserDataListener (int userId, UserDataChangeListener listener) {
    __deleteUserListener(userId, listener);
  }

  public <T extends UserDataChangeListener & UserStatusChangeListener> void addGlobalUsersListener (T listener) {
    __putGlobalUserDataListener(listener);
    __putGlobalStatusListener(listener);
  }
  public <T extends UserDataChangeListener & UserStatusChangeListener> void removeGlobalUsersListener (T listener) {
    __deleteGlobalUserDataListener(listener);
    __deleteGlobalStatusListener(listener);
  }

  private void notifyUserListeners (TdApi.User user) {
    notifyListeners(userListeners.iterator(0), user);
    notifyListeners(userListeners.iterator(user.id), user);
  }

  private void notifyUserFullListeners (int userId, TdApi.UserFullInfo userFull) {
    notifyListeners(userListeners.iterator(0), userId, userFull);
    notifyListeners(userListeners.iterator(userId), userId, userFull);
  }

  private void notifyUserStatusChanged (int userId, TdApi.UserStatus status, boolean uiOnly) {
    notifyUserStatusListeners(statusListeners.iterator(0), userId, status, uiOnly);
    notifyUserStatusListeners(statusListeners.iterator(userId), userId, status, uiOnly);
    notifyUserStatusListeners(simpleStatusListeners.iterator(0), userId, status, uiOnly);
    notifyUserStatusListeners(simpleStatusListeners.iterator(userId), userId, status, uiOnly);
  }

  // Group listeners

  private void putGlobalBasicGroupListener (BasicGroupDataChangeListener listener) {
    groupsGlobalListeners.add(listener);
  }

  private void deleteGlobalBasicGroupListener (BasicGroupDataChangeListener listener) {
    groupsGlobalListeners.remove(listener);
  }

  private void putGroupListener (int groupId, BasicGroupDataChangeListener listener) {
    groupListeners.add(groupId, listener);
  }

  private void deleteGroupListener (int groupId, BasicGroupDataChangeListener listener) {
    groupListeners.remove(groupId, listener);
  }

  // Supergroup

  private void putGlobalSupergroupListener (SupergroupDataChangeListener listener) {
    supergroupsGlobalListeners.add(listener);
  }

  private void deleteGlobalSupergroupListener (SupergroupDataChangeListener listener) {
    supergroupsGlobalListeners.remove(listener);
  }

  private void putSupergroupListener (int supergroupId, SupergroupDataChangeListener listener) {
    supergroupListeners.add(supergroupId, listener);
  }

  private void deleteSupergroupListener (int supergroupId, SupergroupDataChangeListener listener) {
    supergroupListeners.remove(supergroupId, listener);
  }

  // Secret Chats

  private void putGlobalSecretChatListener (SecretChatDataChangeListener listener) {
    secretChatsGlobalListeners.add(listener);
  }

  private void deleteGlobalSecretChatListener (SecretChatDataChangeListener listener) {
    secretChatsGlobalListeners.remove(listener);
  }

  private void putSecretChatListener (int secretChatId, SecretChatDataChangeListener listener) {
    secretChatListeners.add(secretChatId, listener);
  }

  private void deleteSecretChatListener (int secretChatId, SecretChatDataChangeListener listener) {
    secretChatListeners.remove(secretChatId, listener);
  }

  private void putGlobalCallListener (CallStateChangeListener listener) {
    callsGlobalListeners.add(listener);
  }

  private void deleteGlobalCallListener (CallStateChangeListener listener) {
    callsGlobalListeners.remove(listener);
  }

  private void putCallListener (int callId, CallStateChangeListener listener) {
    callListeners.add(callId, listener);
  }

  private void deleteCallListener (int callId, CallStateChangeListener listener) {
    callListeners.remove(callId, listener);
  }

  // Setters (internal)

  private boolean putUserFull (int userId, TdApi.UserFullInfo userFull) {
    userFulls.put(userId, userFull);
    return true;
  }

  private boolean putGroup (TdApi.BasicGroup group) {
    boolean updated = basicGroup.get(group.id) != null;
    basicGroup.put(group.id, group);
    return updated;
  }

  private boolean putGroupFull (int groupId, TdApi.BasicGroupFullInfo groupFull) {
    basicGroupFull.put(groupId, groupFull);
    return true;
  }

  public static final int UPDATE_MODE_NONE = 0;
  public static final int UPDATE_MODE_UPDATE = 1;
  public static final int UPDATE_MODE_IMPORTANT = 2;

  private int putSupergroup (TdApi.Supergroup supergroup) {
    TdApi.Supergroup oldSupergroup = supergroups.get(supergroup.id);
    final int mode;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      mode = oldSupergroup == null ? UPDATE_MODE_NONE : oldSupergroup.isChannel != supergroup.isChannel || !StringUtils.equalsOrBothEmpty(oldSupergroup.username, supergroup.username) ? UPDATE_MODE_IMPORTANT : UPDATE_MODE_UPDATE;
    } else {
      mode = oldSupergroup == null ? UPDATE_MODE_NONE : UPDATE_MODE_UPDATE;
    }
    supergroups.put(supergroup.id, supergroup);
    return mode;
  }

  private boolean putSupergroupFull (int supergroupId, TdApi.SupergroupFullInfo supergroupFull) {
    supergroupsFulls.put(supergroupId, supergroupFull);
    return true;
  }

  private boolean putSecretChat (TdApi.SecretChat secretChat) {
    boolean updated = secretChats.get(secretChat.id) != null;
    secretChats.put(secretChat.id, secretChat);
    return updated;
  }
}
