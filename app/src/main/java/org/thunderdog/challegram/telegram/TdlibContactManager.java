package org.thunderdog.challegram.telegram;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Letters;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceUtils;

/**
 * Date: 12/9/17
 * Author: default
 */

public class TdlibContactManager implements CleanupStartupDelegate {
  /*
  * hide_option -> OPTION_NO_HIDE, OPTION_ASK_LATER, OPTION_ASK_NEVER

  * 1. Ожидание авторизации / Открытие Contacts в списке
  * 2. Отображение окна:
  *
  *     - При запуске, если не нажимали на NOT NOW
  *     - При нажатии на Contacts, если нажимали
  *
  *     To help you connect with friends and family, allow Challegram access to your contacts.
  *
  *     CONTINUE -> запросить доступ к контактам -?> если несколько источников контактов, дать выбрать нужные -> запомнить настройки -> синхронизировать

  *     NOT NOW -> запомнить что окно отложили на "потом" -> скрыть окно
  *     NEVER -> запомнить что окно никогда не надо показывать -> скрыть
  *
  *
  *
  * */

  private static final boolean USE_FULL_NAME_STYLES = true;

  private static final String _ASK_LATER_KEY = "sync_later";

  private static final String _HIDE_OPTION_KEY = "sync_hidden";
  public static final int HIDE_OPTION_DEFAULT = 0;
  public static final int HIDE_OPTION_LATER = 1;
  public static final int HIDE_OPTION_NEVER = 2;

  private static final String _STATUS_KEY = "sync_state";
  public static final int STATUS_INACTIVE = 0;
  public static final int STATUS_IN_FIRST_PROGRESS = 1;
  public static final int STATUS_ENABLED = 2;

  private static final int STATE_NOT_STARTED = 0;
  private static final int STATE_IN_PROGRESS = 1;
  private static final int STATE_FINISHED = 2;

  @Nullable
  private Integer _hideOption,  _status;
  @Nullable
  private Long _askLaterDate;

  private int state;

  private long maxModificationDate;
  private long lastRetrievedContactCount;

  private List<Reference<Object>> avatarExpectors;

  private final Tdlib tdlib;

  private String key (String key) {
    return tdlib.id() != 0 ? key + "_" + tdlib.id() : key;
  }

  TdlibContactManager (Tdlib tdlib) {
    this.tdlib = tdlib;
    this.handler = new ContactHandler(this);
    tdlib.listeners().addCleanupListener(this);
  }

  private long getAskLaterDate () {
    if (_askLaterDate == null) {
      _askLaterDate = Settings.instance().getLong(key(_ASK_LATER_KEY), 0);
    }
    return _askLaterDate;
  }

  private int getHideOption () {
    if (_hideOption == null) {
      _hideOption = Settings.instance().getInt(key(_HIDE_OPTION_KEY), HIDE_OPTION_DEFAULT);
    }
    return _hideOption;
  }

  private int getStatus () {
    if (_status == null) {
      _status = Settings.instance().getInt(key(_STATUS_KEY), STATUS_INACTIVE);
    }
    return _status;
  }

  @Override
  public void onPerformUserCleanup () {
    reset(false, null);
  }

  @Override
  public void onPerformStartup (boolean isAfterRestart) {
    checkRegisteredCount();
  }

  @Override
  public void onPerformRestart () {

  }

  public void addAvatarExpector (Object obj) {
    synchronized (this) {
      if (avatarExpectors == null) {
        avatarExpectors = new ArrayList<>();
      }
      ReferenceUtils.addReference(avatarExpectors, obj);
    }
  }

  public void removeAvatarExpector (Object obj) {
    synchronized (this) {
      if (avatarExpectors != null) {
        ReferenceUtils.removeReference(avatarExpectors, obj);
      }
    }
  }

  private boolean hasAvatarExpectors () {
    synchronized (this) {
      if (avatarExpectors != null) {
        final int size = avatarExpectors.size();
        boolean has = false;
        for (int i = size - 1; i >= 0; i--) {
          Object obj = avatarExpectors.get(i).get();
          if (obj != null) {
            has = true;
          } else {
            avatarExpectors.remove(i);
          }
        }
        return has;
      }
    }
    return false;
  }

  public void deleteContacts () {
    tdlib.client().send(new TdApi.ClearImportedContacts(), new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object clearImportResult) {
        switch (clearImportResult.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR:
            tdlib.client().send(new TdApi.ChangeImportedContacts(new TdApi.Contact[0]), this);
            break;
          case TdApi.ImportedContacts.CONSTRUCTOR:
            tdlib.searchContacts(null, 1000, new Client.ResultHandler() {
              @Override
              public void onResult (TdApi.Object object) {
                switch (object.getConstructor()) {
                  case TdApi.Ok.CONSTRUCTOR: {
                    tdlib.searchContacts(null, 1000, this);
                    break;
                  }
                  case TdApi.Users.CONSTRUCTOR:
                    long[] userIds = ((TdApi.Users) object).userIds;
                    if (userIds.length > 0) {
                      tdlib.client().send(new TdApi.RemoveContacts(userIds), this);
                    }
                    break;
                  case TdApi.Error.CONSTRUCTOR:
                    UI.showError(object);
                    break;
                }
              }
            });
            break;
          case TdApi.Error.CONSTRUCTOR:
            UI.showError(clearImportResult);
            break;
        }
      }
    });
  }

  public void reset (boolean includeServer, final Runnable after) {
    cancelPendingImportTask();
    setHideOption(HIDE_OPTION_DEFAULT);
    setStatus(STATUS_INACTIVE);
    setState(STATE_NOT_STARTED);
    setRegisteredContactsImpl(null, 0, false);
    maxModificationDate = 0;
    if (includeServer) {
      tdlib.client().send(new TdApi.ClearImportedContacts(), tdlib.okHandler());
      tdlib.client().send(new TdApi.ChangeImportedContacts(new TdApi.Contact[0]), object -> {
        switch (object.getConstructor()) {
          case TdApi.ImportedContacts.CONSTRUCTOR: {
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.e(Log.TAG_CONTACT, "changeImportedContacts contact[0]: %s", TD.toErrorString(object));
            break;
          }
        }
        deleteServerImportedContacts(after);
      });
    }
  }

  private void deleteServerImportedContacts (final Runnable after) {
    tdlib.client().send(new TdApi.ClearImportedContacts(), object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.w(Log.TAG_CONTACT, "Failed to reset contacts: %s", TD.toErrorString(object));
          break;
        }
      }
      U.run(after);
    });
  }

  // Listeners

  public interface Listener {
    void onRegisteredContactsChanged (long[] userIds, int totalCount, boolean newArrival);
    void onUnregisteredContactsChanged (int oldTotalCount, ArrayList<UnregisteredContact> contacts, int totalCount);
  }

  private final List<Reference<Listener>> listeners = new ArrayList<>();

  public void addListener (Listener listener) {
    ReferenceUtils.addReference(listeners, listener);
  }

  public void removeListener (Listener listener) {
    ReferenceUtils.removeReference(listeners, listener);
  }

  // Counters

  private int registeredCount;
  private long[] registeredUserIds;
  private ArrayList<UnregisteredContact> unregisteredContacts;

  public static int getCount (long[] array, int totalCount) {
    if (array != null) {
      if (array.length < 5) {
        return array.length;
      } else {
        return totalCount;
      }
    } else {
      return 0;
    }
  }

  public int getAvailableRegisteredCount () {
    return getCount(registeredUserIds, registeredCount);
  }

  public int getRegisteredCount () {
    return registeredCount;
  }

  public ArrayList<UnregisteredContact> getUnregisteredContacts () {
    return unregisteredContacts;
  }

  public long[] getRegisteredUserIds () {
    return registeredUserIds;
  }

  private void setRegisteredContacts (long[] userIds, int registeredCount, boolean increaseOnly) {
    if (userIds != null && userIds.length > 1) {
      List<Long> sorted = new ArrayList<>(userIds.length);
      for (long userId : userIds) {
        sorted.add(userId);
      }
      Collections.sort(sorted, (a, b) -> {
        TdApi.User u1 = tdlib.cache().user(a);
        TdApi.User u2 = tdlib.cache().user(b);
        return tdlib.userComparator().compare(u1, u2);
      });
      ArrayUtils.toArray(sorted, userIds);
      if (userIds.length > 5) {
        long[] result = new long[5];
        System.arraycopy(userIds, 0, result, 0, 5);
        userIds = result;
      }
    }
    handler.sendMessage(Message.obtain(handler, ACTION_SET_REGISTERED_CONTACTS, registeredCount, increaseOnly ? 1 : 0, userIds));
  }

  private void setRegisteredContactsImpl (long[] userIds, int registeredCount, boolean increaseOnly) {
    if (increaseOnly && registeredCount <= this.registeredCount) {
      return;
    }
    boolean newArrival = this.registeredCount == 0 && registeredCount > 0;
    boolean changed = false;
    if (this.registeredCount != registeredCount) {
      this.registeredCount = registeredCount;
      Log.i(Log.TAG_CONTACT, "registeredCount -> %d", registeredCount);
      changed = true;
    }
    if (!ArrayUtils.equalsSorted(userIds, this.registeredUserIds)) {
      this.registeredUserIds = userIds;
      Log.i(Log.TAG_CONTACT, "userIds[] -> %d", userIds != null ? userIds.length : 0);
      changed = true;
    }
    if (changed) {
      registeredCount = getCount(userIds, registeredCount);
      final int size = listeners.size();
      for (int i = size - 1; i >= 0; i--) {
        Listener listener = listeners.get(i).get();
        if (listener != null) {
          listener.onRegisteredContactsChanged(userIds, registeredCount, newArrival);
        } else {
          listeners.remove(i);
        }
      }
    }
  }

  private void setUnregisteredContacts (final ArrayList<UnregisteredContact> contacts) {
    handler.sendMessage(Message.obtain(handler, ACTION_SET_UNREGISTERED_CONTACTS, contacts));
  }

  private void setUnregisteredContactsImpl (final ArrayList<UnregisteredContact> contacts) {
    int prevUnregisteredCount = unregisteredContacts != null ? unregisteredContacts.size() : 0;
    int newUnregisteredCount = contacts != null ? contacts.size() : 0;
    if (prevUnregisteredCount != newUnregisteredCount || newUnregisteredCount > 0) {
      Log.i(Log.TAG_CONTACT, "unregisteredContacts %d -> %d", prevUnregisteredCount, newUnregisteredCount);
      unregisteredContacts = contacts;

      final int size = listeners.size();
      for (int i = size - 1; i >= 0; i--) {
        Listener listener = listeners.get(i).get();
        if (listener != null) {
          listener.onUnregisteredContactsChanged(prevUnregisteredCount, contacts, newUnregisteredCount);
        } else {
          listeners.remove(i);
        }
      }
    }
  }

  private void checkRegisteredCount () {
    tdlib.searchContacts(null, 5, newHandler());
  }

  private Client.ResultHandler newHandler () {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Users.CONSTRUCTOR: {
          final TdApi.Users users = (TdApi.Users) object;
          final int totalCount = users.totalCount;
          long[] userIds = users.userIds;
          if (userIds.length > 0) {
            ArrayList<TdApi.User> result = tdlib.cache().users(userIds);
            Collections.sort(result, tdlib.userComparator());
            userIds = new long[Math.min(5, result.size())];
            boolean needDownload = hasAvatarExpectors();
            for (int i = 0; i < userIds.length; i++) {
              TdApi.User user = result.get(i);
              userIds[i] = user.id;
              if (user.profilePhoto != null && needDownload) {
                if (!Config.DEBUG_DISABLE_DOWNLOAD) {
                  tdlib.client().send(new TdApi.DownloadFile(user.profilePhoto.small.id, 1, 0, 0, false), tdlib.silentHandler());
                }
              }
            }
          }
          setRegisteredContacts(userIds, totalCount, false);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.e(Log.TAG_CONTACT, "Received error: %s", TD.toErrorString(object));
          break;
        }
      }
    };
  }

  // Setters

  public void forceEnable (boolean isEnabled) {
    if (isEnabled) {
      setHideOption(HIDE_OPTION_DEFAULT);
      setStatus(STATUS_ENABLED);
    } else {
      setHideOption(HIDE_OPTION_NEVER);
      setStatus(STATUS_INACTIVE);
    }
  }

  private void setHideOption (int option) {
    Log.i(Log.TAG_CONTACT, "setHideOption %d -> %d", getHideOption(), option);
    SharedPreferences.Editor editor = null;
    if (getHideOption() != option) {
      this._hideOption = option;
      editor = Settings.instance().edit();
      editor.putInt(key(_HIDE_OPTION_KEY), option);
    }
    long date = option == HIDE_OPTION_LATER ? DateUtils.getStartOfToday() : 0;
    if (getAskLaterDate() != date) {
      _askLaterDate = date;
      if (editor == null) {
        editor = Settings.instance().edit();
      }
      editor.putLong(key(_ASK_LATER_KEY), date);
    }
    if (editor != null) {
      editor.apply();
    }
  }

  public interface StatusChangeListener {
    void onContactSyncEnabled (Tdlib tdlib, boolean isEnabled);
  }

  private ReferenceList<StatusChangeListener> statusListeners;

  public void addStatusListener (StatusChangeListener listener) {
    if (statusListeners == null)
      statusListeners = new ReferenceList<>();
    statusListeners.add(listener);
  }

  public void removeStatusListener (StatusChangeListener listener) {
    if (statusListeners != null)
      statusListeners.remove(listener);
  }

  private void setStatus (int status) {
    if (getStatus() != status) {
      this._status = status;
      Settings.instance().putInt(key(_STATUS_KEY), status);
      Log.i(Log.TAG_CONTACT, "setStatus -> %d", status);
      if (statusListeners != null) {
        for (StatusChangeListener listener : statusListeners) {
          listener.onContactSyncEnabled(tdlib, status != STATUS_INACTIVE);
        }
      }
    }
  }

  // Entrance

  private boolean canShowAlert (boolean force) {
    int hideOption = getHideOption();
    if (hideOption == HIDE_OPTION_NEVER) {
      return false;
    }
    if (hideOption == HIDE_OPTION_LATER) {
      return force || DateUtils.getStartOfToday() != getAskLaterDate();
    }
    return true;
  }

  public boolean isSyncEnabled () {
    return getStatus() != STATUS_INACTIVE;
  }

  public void disableSync () {
    cancelPendingImportTask();
    setHideOption(HIDE_OPTION_NEVER);
    setStatus(STATUS_INACTIVE);
    setState(STATE_NOT_STARTED);
  }

  public void enableSync (BaseActivity context) {
    if (getHideOption() == HIDE_OPTION_NEVER) {
      setHideOption(HIDE_OPTION_DEFAULT);
    }
    if (getStatus() == STATUS_INACTIVE) {
      setStatus(STATUS_IN_FIRST_PROGRESS);
    }
    startSyncIfNeeded(context, true, null);
  }

  private static void addRobots (ArrayList<TdApi.Contact> out) {
    out.ensureCapacity(out.size() + Config.MAX_ROBOT_ID);
    for (int robotId = 1; robotId <= Config.MAX_ROBOT_ID; robotId++) {
      out.add(new TdApi.Contact("99966173" + (50 + robotId), "Robot #" + robotId, "(imported)", null, 0));
    }
  }

  public void startSyncIfNeeded (final BaseActivity context, final boolean force, @Nullable final Runnable callback) {
    if (UI.TEST_MODE == UI.TEST_MODE_USER) {
      ArrayList<TdApi.Contact> contacts = new ArrayList<>(Config.MAX_ROBOT_ID);
      addRobots(contacts);
      TdApi.Contact[] contactsArray = new TdApi.Contact[contacts.size()];
      contacts.toArray(contactsArray);
      importContacts(new CancellableRunnable() {
        @Override
        public void act () {

        }
      }, contactsArray);
      if (callback != null) {
        callback.run();
      }
      return;
    }

    if ((getStatus() != STATUS_INACTIVE /*|| (!force && canShowAlert(false))*/) && U.needsPermissionRequest(Manifest.permission.READ_CONTACTS)) {
      U.requestPermissions(new String[] {Manifest.permission.READ_CONTACTS}, result -> {
        if (result) {
          if (getStatus() == STATUS_INACTIVE) {
            setStatus(STATUS_IN_FIRST_PROGRESS);
          }
          startSyncIfNeeded(context, force, callback);
        } else {
          setStatus(STATUS_INACTIVE);
          if (canShowAlert(force)) {
            if (U.shouldShowPermissionRationale(Manifest.permission.READ_CONTACTS)) {
              showAlert(context, (force ? FLAG_NEED_NEVER : 0), callback);
            } else {
              showAlert(context, FLAG_NEED_NEVER | FLAG_PERMISSION_DISABLED, callback);
            }
          } else {
            if (callback != null) {
              callback.run();
            }
          }
        }
      });
      return;
    }

    if (force && (getStatus() == STATUS_IN_FIRST_PROGRESS || getStatus() == STATUS_ENABLED) && state == STATE_FINISHED) {
      U.run(callback);
      return;
    }

    switch (getStatus()) {
      case STATUS_INACTIVE: {
        if (canShowAlert(force)) {
          showAlert(context, (force ? FLAG_NOT_FIRST_TIME | FLAG_NEED_NEVER : 0), callback);
        } else {
          U.run(callback);
        }
        break;
      }
      case STATUS_IN_FIRST_PROGRESS:
      case STATUS_ENABLED: {
        U.run(callback);
        startContactsSync();
        break;
      }
    }
  }

  private CancellableRunnable pendingImportTask;

  private void cancelPendingImportTask () {
    if (pendingImportTask != null) {
      pendingImportTask.cancel();
      pendingImportTask = null;
    }
  }

  private void startContactsSync () {
    if ((getStatus() == STATUS_IN_FIRST_PROGRESS || getStatus() == STATUS_ENABLED) && (state != STATE_IN_PROGRESS)) {
      final boolean ignoreIfNoChanges = state == STATE_FINISHED;
      Log.i(Log.TAG_CONTACT, "Starting contacts synchronization, ignoreIfNoChanges:%b, status:%d", ignoreIfNoChanges, getStatus());
      state = STATE_IN_PROGRESS;
      cancelPendingImportTask();
      Background.instance().post(pendingImportTask = new CancellableRunnable() {
        @Override
        public void act () {
          importContactsImpl(this, ignoreIfNoChanges);
        }
      });
    }
  }

  public void makeSilentPermissionCheck (BaseActivity context) {
    boolean hasPermission = !U.needsPermissionRequest(Manifest.permission.READ_CONTACTS);
    if (pendingRetryDialog != null && pendingRetryDialog.getContext() == context && hasPermission) {
      DialogInterface dialog = pendingRetryDialog;
      Runnable callback = pendingRetryCallback;
      pendingRetryDialog = null;
      pendingRetryCallback = null;
      if (callback != null) {
        callback.run();
      }
      dialog.dismiss();
    }
    if (hasPermission) {
      startContactsSync();
    }
  }

  private static final int FLAG_PERMISSION_DISABLED = 1;
  private static final int FLAG_NOT_FIRST_TIME = 1 << 1;
  private static final int FLAG_NEED_NEVER = 1 << 2;

  private AlertDialog pendingRetryDialog;
  private Runnable pendingRetryCallback;

  private void clearPendingRetryDialog (DialogInterface dialog) {
    if (pendingRetryDialog == dialog) {
      pendingRetryDialog = null;
      pendingRetryCallback = null;
    }
  }

  private void showAlert (final BaseActivity context, final int flags, final @Nullable Runnable callback) {
    if (state != STATE_NOT_STARTED || getHideOption() == HIDE_OPTION_NEVER) {
      if (callback != null) {
        callback.run();
      }
      return;
    }
    final boolean isFirstTime = (flags & FLAG_NOT_FIRST_TIME) == 0;
    final boolean isRetry = (flags & FLAG_PERMISSION_DISABLED) != 0;
    final boolean allowNever = (flags & FLAG_NEED_NEVER) != 0;
    final boolean needNever = true; // allowNever; //  && hideOption == HIDE_OPTION_LATER && !isFirstTime;

    int title = R.string.SyncHintTitle;
    String message;
    if (isRetry) {
      if (isFirstTime) {
        message = Lang.getString(R.string.SyncHintRetry);
      } else {
        message = Lang.getString(R.string.SyncHintUnavailable);
      }
    } else {
      message = Lang.getString(R.string.SyncHint, Lang.getString(R.string.AppName));
    }

    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(title));
    b.setMessage(message);
    b.setPositiveButton(Lang.getString(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && U.needsPermissionRequest(Manifest.permission.READ_CONTACTS) ? (isRetry ? R.string.Settings : R.string.Continue) : R.string.Allow), (dialog, which) -> {
      if (isRetry) {
        Intents.openPermissionSettings();
        return;
      }
      dialog.dismiss();
      setHideOption(HIDE_OPTION_DEFAULT);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        U.requestPermissions(new String[] {Manifest.permission.READ_CONTACTS}, result -> {
          if (result) {
            if (getStatus() == STATUS_INACTIVE) {
              setStatus(STATUS_IN_FIRST_PROGRESS);
            }
            startSyncIfNeeded(context, allowNever, callback);
          } else if (U.shouldShowPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            showAlert(context, flags | FLAG_NOT_FIRST_TIME, callback);
          } else {
            // User has checked "Never ask again" or something like that
            showAlert(context, flags | FLAG_NOT_FIRST_TIME | FLAG_PERMISSION_DISABLED, callback);
          }
        });
      } else {
        setStatus(STATUS_IN_FIRST_PROGRESS);
        startSyncIfNeeded(context, allowNever, callback);
      }
    });
    b.setNegativeButton(Lang.getString(needNever ? R.string.Never : R.string.NotNow), (dialog, which) -> {
      if (isRetry) {
        clearPendingRetryDialog(dialog);
      }
      dialog.dismiss();
      if (callback != null) {
        callback.run();
      }
      if (needNever) {
        setHideOption(HIDE_OPTION_NEVER);
        setStatus(STATUS_INACTIVE);
      } else {
        setHideOption(HIDE_OPTION_LATER);
      }
    });
    if (!needNever) {
      b.setCancelable(false);
    }
    if (!context.isFinishing()) {
      AlertDialog dialog = context.showAlert(b);
      if (isRetry) {
        pendingRetryDialog = dialog;
        pendingRetryCallback = callback;
      }
    }
  }

  // Impl (runs in Background thread)

  private void setState (int state) {
    handler.sendMessage(Message.obtain(handler, ACTION_SET_STATE, state, 0));
  }

  private static String formatPhoneNumber (String number) {
    if (StringUtils.isEmpty(number)) {
      return number;
    }
    int resultLength = number.length();
    if (resultLength > 1) {
      if (number.charAt(0) == '+') {
        boolean ok = true;
        for (int i = 1; i < resultLength; ) {
          int codePoint = number.codePointAt(i);
          int count = Character.charCount(codePoint);
          if (count != 1 || !StringUtils.isNumeric((char) codePoint)) {
            ok = false;
            break;
          }
          i += count;
        }
        if (ok) {
          return Strings.formatPhone(number);
        }
      } else {
        return Strings.systemFormat(number);
      }
    }
    return number;
  }

  private static String cleanPhoneNumber (String number) {
    if (StringUtils.isEmpty(number)) {
      return number;
    }
    final int length = number.length();
    StringBuilder b = new StringBuilder(length);
    for (int i = 0; i < length;) {
      int codePoint = number.codePointAt(i);
      int codePointType = Character.getType(codePoint);
      int count = Character.charCount(codePoint);
      boolean ok = codePointType != Character.SPACE_SEPARATOR;
      if (ok && count == 1) {
        if (codePoint == '-' || codePoint == '(' || codePoint == ')') {
          ok = false;
        } else if ((codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z')) {
          b.append(Strings.translateLatinToNumber((char) codePoint));
          ok = false;
        }
      }
      if (ok) {
        b.append(number, i, i + count);
      }
      i += count;
    }
    /*String result = b.toString();
    int resultLength = b.length();
    if (resultLength > 1) {
      if (b.charAt(0) == '+') {
        boolean ok = true;
        for (int i = 1; i < resultLength; ) {
          int codePoint = b.codePointAt(i);
          int count = Character.charCount(codePoint);
          if (count != 1 || !Strings.isNumeric((char) codePoint)) {
            ok = false;
            break;
          }
          i += count;
        }
        if (ok) {
          return Strings.formatPhone(result);
        }
      } else {
        return Strings.systemFormat(result);
      }
    }*/
    return b.toString();
  }

  private void importContactsImpl (CancellableRunnable cancellationSignal, boolean ignoreIfNoChanges) {
    Cursor c = null;
    int count;
    Context context = UI.getAppContext();
    TdApi.Contact[] result = null;
    long maxModificationDate = 0;
    try {
      ContentResolver resolver = context.getContentResolver();

      if (ignoreIfNoChanges) {
        boolean ok = false;
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (this.maxModificationDate == 0) {
              ok = true;
            } else {
              c = resolver.query(ContactsContract.Contacts.CONTENT_URI,
                new String[]{
                  ContactsContract.Contacts._ID
                },
                ContactsContract.Contacts.HAS_PHONE_NUMBER + "<>0 AND " + ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + " > " + this.maxModificationDate,
                null,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + " DESC LIMIT 1"
              );
              if (c != null) {
                ok = c.getCount() > 0;
                U.closeCursor(c); c = null;
                if (ok) {
                  Log.i(Log.TAG_CONTACT, "Found newer contact modifications, starting sync process");
                }
              }
            }
          }
          if (!ok) {
            c = resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[] {
                  ContactsContract.Contacts._ID
                },
              ContactsContract.Contacts.HAS_PHONE_NUMBER + "<>0",
              null,
              null
              );
            if (c != null) {
              long totalContactCount = c.getCount();
              ok = totalContactCount != lastRetrievedContactCount;
              if (ok) {
                Log.i(Log.TAG_CONTACT, "Contact list size changed, starting sync process: prev_size:%d, new_size: %d", lastRetrievedContactCount, totalContactCount);
              }
            }
            U.closeCursor(c); c = null;
          }
        } catch (Throwable t) {
          Log.critical(Log.TAG_CONTACT, "Contact changes check failed", t);
        }
        if (!ok) {
          Log.i(Log.TAG_CONTACT, "No contact changes has been found, aborting.");
          setState(STATE_FINISHED);

          U.closeCursor(c); c = null;

          return;
        }
      }

      String[] projection;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        projection = new String[] {
          ContactsContract.Contacts._ID,
          ContactsContract.Contacts.DISPLAY_NAME,
          ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
        };
      } else {
        projection = new String[] {
          ContactsContract.Contacts._ID,
          ContactsContract.Contacts.DISPLAY_NAME
        };
      }
      c = resolver.query(ContactsContract.Contacts.CONTENT_URI,
        projection,
        ContactsContract.Contacts.HAS_PHONE_NUMBER + "<>0",
        null,
        null
      );
      if (c == null) {
        throw new NullPointerException("Contacts.CONTENT_URI query failed");
      }

      count = c.getCount();
      lastRetrievedContactCount = count;
      ArrayList<ContactData> contacts = new ArrayList<>(count);

      while (c.moveToNext()) {
        long _id = c.getLong(0);
        String displayName = StringUtils.trim(c.getString(1));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
          maxModificationDate = Math.max(maxModificationDate, c.getLong(2));
        }
        contacts.add(new ContactData(_id, displayName));
      }
      U.closeCursor(c); c = null;

      if (!contacts.isEmpty()) {
        final HashMap<Long, ContactData> contactMap = new HashMap<>(contacts.size());
        for (ContactData contactData : contacts) {
          contactMap.put(contactData.contactId, contactData);
        }
        c = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
          new String[] {
            ContactsContract.Data._ID,
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
          },
          null,
          null,
          null);
        if (c == null) {
          throw new NullPointerException("Phone.CONTENT_URI query failed");
        }

        int validContactCount = 0;
        ArrayList<String[]> unknownPhoneNumbers = null;
        while (c.moveToNext()) {
          long _id = c.getLong(0);
          long contactId = c.getLong(1);
          String rawPhoneNumber = c.getString(2);
          String phoneNumber = cleanPhoneNumber(rawPhoneNumber);
          if (!StringUtils.isEmpty(phoneNumber)) {
            ContactData data = contactMap.get(contactId);
            if (data != null) {
              if (!data.phoneNumbers.contains(phoneNumber)) {
                data.phoneNumbers.add(phoneNumber);
                data.rawPhoneNumbers.add(phoneNumber);
                validContactCount++;
              }
            } else {
              if (unknownPhoneNumbers == null) {
                unknownPhoneNumbers = new ArrayList<>();
              }
              unknownPhoneNumbers.add(new String[] {phoneNumber, rawPhoneNumber});
            }
          }
        }
        U.closeCursor(c); c = null;
        if (unknownPhoneNumbers != null) {
          Log.w(Log.TAG_CONTACT, "%d phone numbers do not belong to any known contact_id", unknownPhoneNumbers.size());
          // TODO ?
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && USE_FULL_NAME_STYLES) {
          projection = new String[] {
            ContactsContract.Data._ID,
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
            ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FULL_NAME_STYLE
          };
        } else {
          projection = new String[] {
            ContactsContract.Data._ID,
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
            ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
          };
        }
        c = resolver.query(ContactsContract.Data.CONTENT_URI,
          projection,
          ContactsContract.Data.MIMETYPE + "=?",
          new String[] {
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
          },
          null
        );
        if (c == null) {
          throw new NullPointerException("Data.CONTENT_URI query failed");
        }
        while (c.moveToNext()) {
          long _id = c.getLong(0);
          long contactId = c.getLong(1);
          ContactData contact = contactMap.get(contactId);
          if (contact == null) {
            continue;
          }
          String firstName = StringUtils.trim(c.getString(2));
          String lastName = StringUtils.trim(c.getString(3));
          String middleName = StringUtils.trim(c.getString(4));
          long fullNameStyle = -1;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && USE_FULL_NAME_STYLES) {
            fullNameStyle = c.getLong(5);
            if (!StringUtils.isEmpty(middleName)) {
              if (StringUtils.isEmpty(lastName)) {
                lastName = middleName;
              } else {
                lastName = middleName + ' ' + lastName;
              }
            }
          }
          contact.addVariation(new NameVariation(contact.phoneNumbers, firstName, lastName, middleName, fullNameStyle));
        }
        U.closeCursor(c); c = null;
        ArrayList<TdApi.Contact> futureResult = new ArrayList<>(validContactCount);
        for (ContactData contact : contacts) {
          contact.convertToContact(futureResult);
        }
        if (UI.inTestMode()) {
          addRobots(futureResult);
        }
        result = new TdApi.Contact[futureResult.size()];
        futureResult.toArray(result);
      } else {
        result = new TdApi.Contact[0];
      }
    } catch (Throwable t) {
      U.closeCursor(c);
      Log.critical(Log.TAG_CONTACT, "Contacts synchronization failed", t);
    }

    if (cancellationSignal.isPending()) {
      if (result != null) {
        this.maxModificationDate = maxModificationDate;
        importContacts(cancellationSignal, result);
      } else {
        setState(STATE_NOT_STARTED);
      }
    }
  }

  private void importContacts (final CancellableRunnable cancellationSingal, final TdApi.Contact[] contacts) {
    if (Log.isEnabled(Log.TAG_CONTACT)) {
      if (Log.checkLogLevel(Log.LEVEL_VERBOSE)) {
        Log.v(Log.TAG_CONTACT, "Importing %d contacts...\n%s", contacts.length, TextUtils.join("\n", contacts));
      } else {
        Log.i(Log.TAG_CONTACT, "Found %d contacts, importing...", contacts.length);
      }
    }
    tdlib.client().send(new TdApi.ChangeImportedContacts(contacts), object -> {
      switch (object.getConstructor()) {
        case TdApi.ImportedContacts.CONSTRUCTOR: {
          TdApi.ImportedContacts imported = (TdApi.ImportedContacts) object;
          ArrayList<UnregisteredContact> unregisteredContacts = null;
          int i = 0;
          for (long userId : imported.userIds) {
            if (userId == 0) {
              TdApi.Contact contact = contacts[i];
              int importerCount = imported.importerCount[i];
              if (unregisteredContacts == null) {
                unregisteredContacts = new ArrayList<>();
              }
              unregisteredContacts.add(new UnregisteredContact(contact, formatPhoneNumber(contact.phoneNumber), importerCount));
            }
            i++;
          }
          if (cancellationSingal.isPending()) {
            if (unregisteredContacts != null) {
              unregisteredContacts.trimToSize();
              Collections.sort(unregisteredContacts, (o1, o2) -> {
                int c;
                c = Integer.compare(o2.importerCount, o1.importerCount);
                if (c != 0) {
                  return c;
                }
                String n1 = TD.getUserName(o1.contact.firstName, o1.contact.lastName).toLowerCase();
                String n2 = TD.getUserName(o2.contact.firstName, o2.contact.lastName).toLowerCase();
                c = n1.compareTo(n2);
                if (c != 0) {
                  return c;
                }
                return o1.contact.phoneNumber.compareTo(o2.contact.phoneNumber);
              });
              setUnregisteredContacts(unregisteredContacts);
            } else {
              setUnregisteredContacts(null);
            }
            checkRegisteredCount();
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.e(Log.TAG_CONTACT, "changeImportedContacts: %s", TD.toErrorString(object));
          break;
        }
      }
      setState(STATE_FINISHED);
    });
  }

  public static class UnregisteredContact {
    public final TdApi.Contact contact;
    public final int importerCount;
    public final String displayPhoneNumber;

    public final Letters letters;

    private UnregisteredContact (TdApi.Contact contact, String displayPhoneNumber, int importerCount) {
      this.contact = contact;
      this.displayPhoneNumber = displayPhoneNumber;
      this.importerCount = importerCount > 1 ? importerCount : 0;
      this.letters = TD.getLetters(contact.firstName, contact.lastName);
    }

    public String getName () {
      return TD.getUserName(contact.firstName, contact.lastName);
    }

    public String getStatus () {
      if (importerCount > 1) {
        if (importerCount == 1000) {
          return Lang.getString(R.string.ManyContactsJoined);
        } else {
          return Lang.plural(R.string.xContactsJoined, importerCount);
        }
      } else {
        return displayPhoneNumber;
      }
    }
  }

  private static class NameVariation implements Comparable<NameVariation> {
    public final String firstName;
    public final String lastName;
    public final String middleName;
    public final long fullNameStyle;

    public NameVariation (ArrayList<String> phoneNumbers, String firstName, String lastName, String middleName, long fullNameStyle) {
      this.firstName = ContactData.filterName(firstName, phoneNumbers);
      this.lastName = ContactData.filterName(lastName, phoneNumbers);
      this.middleName = ContactData.filterName(middleName, phoneNumbers);
      this.fullNameStyle = fullNameStyle;
    }

    public String signature () {
      StringBuilder b = null;
      if (!StringUtils.isEmpty(firstName)) {
        b = new StringBuilder(firstName);
        b.append(' ');
      }
      if (!StringUtils.isEmpty(lastName)) {
        if (b == null) {
          b = new StringBuilder(lastName);
        } else {
          b.append(lastName);
        }
        b.append(' ');
      }
      if (!StringUtils.isEmpty(middleName)) {
        if (b == null) {
          b = new StringBuilder(middleName);
        } else {
          b.append(middleName);
        }
        b.append(' ');
      }
      return b != null ? b.toString() : null;
    }

    public boolean compare (NameVariation v) {
      return StringUtils.equalsOrBothEmpty(signature(), v.signature());
    }

    public int getGoodRating () {
      int rating = 0;
      if (!StringUtils.isEmpty(firstName)) {
        rating++;
      }
      if (!StringUtils.isEmpty(lastName)) {
        rating++;
      }
      if (!StringUtils.isEmpty(middleName)) {
        rating++;
      }
      return rating;
    }

    public boolean isEmpty () {
      return StringUtils.isEmpty(firstName) && StringUtils.isEmpty(lastName);
    }

    @Override
    public int compareTo (@NonNull NameVariation o) {
      return Integer.compare(o.getGoodRating(), getGoodRating());
    }
  }

  private static class ContactData {
    public final long contactId;
    public final String displayName;
    public final ArrayList<String> phoneNumbers = new ArrayList<>();
    public final ArrayList<String> rawPhoneNumbers = new ArrayList<>();
    public ArrayList<NameVariation> nameVariations;

    public ContactData (long contactId, String displayName) {
      this.contactId = contactId;
      this.displayName = displayName;
    }

    public static @Nullable String filterName (String name, List<String> phoneNumbers) {
      if (!phoneNumbers.isEmpty()) {
        if (phoneNumbers.indexOf(name) != -1)
          return null;
        String number = Strings.getNumber(name);
        if (!StringUtils.equalsOrBothEmpty(number, name) && phoneNumbers.indexOf(number) != -1)
          return null;
      }
      return name;
    }

    public @Nullable String getDisplayName () {
      return filterName(displayName, phoneNumbers);
    }

    public void addVariation (NameVariation variation) {
      if (variation.getGoodRating() <= 0) {
        return;
      }
      if (nameVariations == null) {
        nameVariations = new ArrayList<>();
        nameVariations.add(variation);
        return;
      }
      boolean add = true;
      for (NameVariation existing : nameVariations) {
        if (existing.compare(variation)) {
          add = false;
          break;
        }
      }
      if (add) {
        nameVariations.add(variation);
      }
    }

    public void convertToContact (ArrayList<TdApi.Contact> out) {
      String displayName = getDisplayName();
      String firstName = displayName;
      String lastName = null;

      if (nameVariations != null && !nameVariations.isEmpty()) {
        NameVariation nameVariation = Collections.max(nameVariations);
        String firstNameAttempt = nameVariation.firstName;
        String lastNameAttempt = nameVariation.lastName;
        boolean ok;
        if (displayName == null) {
          ok = true;
        } else if (StringUtils.isEmpty(firstNameAttempt)) {
          ok = StringUtils.equalsOrEmptyIgnoreCase(lastNameAttempt, displayName, Lang.locale());
        } else if (StringUtils.isEmpty(lastNameAttempt)) {
          ok = StringUtils.equalsOrEmptyIgnoreCase(firstNameAttempt, displayName, Lang.locale());
        } else {
          ok = displayName.toLowerCase().contains(firstNameAttempt.toLowerCase()) && displayName.contains(lastNameAttempt.toLowerCase()) && displayName.length() == firstNameAttempt.length() + lastNameAttempt.length() + 1;
        }
        if (ok) {
          firstName = firstNameAttempt;
          lastName = lastNameAttempt;
        }
      }

      if (StringUtils.length(displayName) > (StringUtils.length(firstName) + StringUtils.length(lastName) + 1)) {
        firstName = displayName;
        lastName = null;
      }

      if (StringUtils.isEmpty(firstName) && StringUtils.isEmpty(lastName)) {
        Log.e(Log.TAG_CONTACT, "Contact name not found for %s", TextUtils.join(", ", rawPhoneNumbers));
      } else if (StringUtils.isEmpty(firstName)) {
        for (String phoneNumber : rawPhoneNumbers) {
          out.add(new TdApi.Contact(phoneNumber, lastName, null, null, 0));
        }
      } else {
        for (String phoneNumber : rawPhoneNumbers) {
          out.add(new TdApi.Contact(phoneNumber, firstName, lastName, null, 0));
        }
      }
    }

    @Override
    public String toString () {
      return "ContactData{" +
        "contactId=" + contactId +
        ", displayName='" + displayName + '\'' +
        ", phoneNumbers=" + phoneNumbers +
        ", nameVariations=" + nameVariations +
        '}';
    }
  }

  // Handler

  private static final int ACTION_SET_STATE = 0;
  private static final int ACTION_SET_UNREGISTERED_CONTACTS = 1;
  private static final int ACTION_SET_REGISTERED_CONTACTS = 2;

  private final ContactHandler handler;

  private static class ContactHandler extends Handler {
    private final TdlibContactManager context;
    public ContactHandler (TdlibContactManager context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case ACTION_SET_STATE: {
          context.state = msg.arg1;
          break;
        }
        case ACTION_SET_UNREGISTERED_CONTACTS: {
          //noinspection unchecked
          context.setUnregisteredContactsImpl((ArrayList<UnregisteredContact>) msg.obj);
          break;
        }
        case ACTION_SET_REGISTERED_CONTACTS: {
          context.setRegisteredContactsImpl((long[]) msg.obj, msg.arg1, msg.arg2 == 1);
          break;
        }
      }
    }
  }
}
