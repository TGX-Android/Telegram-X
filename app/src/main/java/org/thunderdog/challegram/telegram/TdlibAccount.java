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
 * File created on 20/02/2018
 */
package org.thunderdog.challegram.telegram;

import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.StringUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.leveldb.LevelDB;
import me.vkryl.td.Td;

public class TdlibAccount implements Comparable<TdlibAccount>, TdlibProvider {
  public static final int NO_ID = -1;
  public static final int ID_MAX = 0xffff;

  public static final int VERSION_1 = 1;
  public static final int VERSION_2 = 2;
  public static final int VERSION = VERSION_2;

  private static final int FLAG_UNAUTHORIZED = 1;
  private static final int FLAG_DEBUG = 1 << 1;
  private static final int FLAG_NO_KEEP_ALIVE = 1 << 2;
  private static final int FLAG_HAS_UNPROCESSED_PUSHES = 1 << 3;
  private static final int FLAG_DEVICE_REGISTERED = 1 << 4;
  private static final int FLAG_LOGGING_OUT = 1 << 5;
  private static final int FLAG_NO_PRIVATE_DATA = 1 << 6;
  private static final int FLAG_FORCE_DISABLE_NOTIFICATIONS = 1 << 7;
  private static final int FLAG_NO_PENDING_NOTIFICATIONS = 1 << 8;
  private static final int FLAG_SERVICE = 1 << 9;
  private static final int FLAG_HAVE_UNFINISHED_SERVICE_WORK = 1 << 10;

  final TdlibManager context;

  public final int id;

  private int flags;
  private long knownUserId;
  private long modificationTime;
  private int order;

  Tdlib tdlib;
  private final Object sync = new Object();

  private long lastUsageTime;

  TdlibAccount (TdlibManager context, int id, @Tdlib.Mode int instanceMode) {
    this.context = context;
    this.id = id;
    this.order = -1;
    this.modificationTime = System.currentTimeMillis();
    this.flags = FLAG_UNAUTHORIZED;
    Settings.instance().setAllowSpecialTdlibInstanceMode(id, instanceMode);
    if (instanceMode == Tdlib.Mode.DEBUG) {
      this.flags |= Tdlib.Mode.DEBUG;
    } else if (instanceMode == Tdlib.Mode.SERVICE) {
      this.flags |= FLAG_SERVICE | FLAG_NO_PRIVATE_DATA;
    }
  }

  TdlibAccount (TdlibManager context, int id, RandomAccessFile r, int version, boolean allowIntegrityChecks) throws IOException {
    this.context = context;
    this.id = id;
    restore(r, version, allowIntegrityChecks);
  }

  void markAsUsed () {
    lastUsageTime = SystemClock.uptimeMillis();
    context.increaseModCount(this);
  }

  long lastUsageTime () {
    return this == context.currentAccount() ? Long.MAX_VALUE : lastUsageTime;
  }

  public boolean isSameAs (TdlibAccount o) {
    return id == o.id;
  }

  @Override
  public final int compareTo (@NonNull TdlibAccount o) {
    if (this.order != o.order) {
      int x = this.order != -1 ? this.order : Integer.MAX_VALUE;
      int y = o.order != -1 ? o.order : Integer.MAX_VALUE;
      return Integer.compare(x, y);
    }
    if (this.modificationTime != o.modificationTime) {
      return Long.compare(this.modificationTime, o.modificationTime);
    }
    return Integer.compare(this.id, o.id);
  }

  private void restore (RandomAccessFile r, int version, boolean allowIntegrityChecks) throws IOException {
    this.flags            = r.readByte();
    this.knownUserId      = version == VERSION_2 ? r.readLong() : r.readInt();
    this.modificationTime = r.readLong();
    this.order            = r.readInt();
    boolean integrityCheckFailed = false;
    if (allowIntegrityChecks) {
      if (BitwiseUtils.getFlag(flags, FLAG_SERVICE | FLAG_DEBUG) && !Settings.instance().allowSpecialTdlibInstanceMode(id)) {
        int flags = this.flags & ~FLAG_DEBUG;
        flags &= ~FLAG_SERVICE;
        this.flags = flags;
        integrityCheckFailed = true;
      }
    }
    Log.i(Log.TAG_ACCOUNTS, "restored accountId:%d flags:%d userId:%d time:%d order:%d integrity_check_failed:%b", id, flags, knownUserId, modificationTime, order, integrityCheckFailed);
  }

  static final int SIZE_PER_ENTRY = 1 /*flags*/ + 8 /*knownUserId*/ + 8 /*modification_time*/ + 4 /*order*/;

  void save (RandomAccessFile r) throws IOException {
    r.write(flags);
    r.writeLong(knownUserId);
    r.writeLong(modificationTime);
    r.writeInt(order);
  }

  int saveOrder (RandomAccessFile r, final int position) throws IOException {
    int skipSize =
        1 /*flags*/
      + 8 /*knownUserId*/
      + 8 /*modificationTime*/;
    r.seek(position + skipSize);
    r.writeInt(order);
    return position + SIZE_PER_ENTRY;
  }

  int saveFlags (RandomAccessFile r, final int position) throws IOException {
    r.seek(position);
    r.write(flags);
    return position + SIZE_PER_ENTRY;
  }

  private boolean changeFlag (int flag, boolean enabled) {
    return setFlags(BitwiseUtils.setFlag(flags, flag, enabled));
  }

  private boolean setFlags (int flags) {
    if (this.flags != flags) {
      this.flags = flags;
      return true;
    }
    return false;
  }

  // keep_alive algorithm

  boolean setKeepAlive (boolean keepAlive) {
    return changeFlag(FLAG_NO_KEEP_ALIVE, !keepAlive);
  }

  boolean keepAlive () {
    if (isLoggingOut())
      return true;
    if (isService())
      return BitwiseUtils.getFlag(flags, FLAG_HAVE_UNFINISHED_SERVICE_WORK);
    if (isUnauthorized())
      return false;
    return !BitwiseUtils.getFlag(flags, FLAG_NO_KEEP_ALIVE) || hasUnprocessedPushes() || !hasUserInformation() /*|| !isDeviceRegistered()*/;
  }

  boolean setHasUnprocessedPushes (boolean hasUnprocessedPushes) {
    return changeFlag(FLAG_HAS_UNPROCESSED_PUSHES, hasUnprocessedPushes);
  }

  boolean hasUnprocessedPushes () {
    return BitwiseUtils.getFlag(flags, FLAG_HAS_UNPROCESSED_PUSHES);
  }

  boolean setLoggingOut (boolean isLoggingOut) {
    if (changeFlag(FLAG_LOGGING_OUT, isLoggingOut)) {
      if (Config.NEED_TDLIB_CLEANUP) {
        if (isUnauthorized()) {
          changeFlag(FLAG_NO_PRIVATE_DATA, !isLoggingOut);
        }
      }
      return true;
    }
    return false;
  }

  boolean isLoggingOut () {
    return BitwiseUtils.getFlag(flags, FLAG_LOGGING_OUT);
  }

  boolean markNoPrivateData () {
    return isUnauthorized() && !isLoggingOut() && changeFlag(FLAG_NO_PRIVATE_DATA, true);
  }

  boolean hasPrivateData () {
    return !BitwiseUtils.getFlag(flags, FLAG_NO_PRIVATE_DATA);
  }

  // notifications

  boolean setForceEnableNotifications (boolean enableNotifications) {
    return changeFlag(FLAG_FORCE_DISABLE_NOTIFICATIONS, !enableNotifications);
  }

  public boolean forceEnableNotifications () {
    return !BitwiseUtils.getFlag(flags, FLAG_FORCE_DISABLE_NOTIFICATIONS);
  }

  public boolean allowNotifications () {
    if (isLoggingOut()) {
      return false;
    }
    if (Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT)) {
      return context.preferredAccountId() == this.accountId();
    }
    if (Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS)) {
      return forceEnableNotifications();
    }
    return true;
  }

  boolean setHaveVisibleNotifications (boolean havePendingNotifications) {
    return changeFlag(FLAG_NO_PENDING_NOTIFICATIONS, !havePendingNotifications);
  }

  public boolean haveVisibleNotifications () {
    return !BitwiseUtils.getFlag(flags, FLAG_NO_PENDING_NOTIFICATIONS);
  }

  // is_debug

  boolean setInstanceMode (@Tdlib.Mode int instanceMode) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_DEBUG, instanceMode == Tdlib.Mode.DEBUG);
    flags = BitwiseUtils.setFlag(flags, FLAG_SERVICE, instanceMode == Tdlib.Mode.SERVICE);
    if (setFlags(flags)) {
      Settings.instance().setAllowSpecialTdlibInstanceMode(id, instanceMode);
      if (hasTdlib(false)) {
        tdlib.setInstanceMode(instanceMode);
      }
      return true;
    }
    return false;
  }

  public boolean isDebug () {
    return tdlibInstanceMode() == Tdlib.Mode.DEBUG;
  }

  boolean isService () {
    return tdlibInstanceMode() == Tdlib.Mode.SERVICE;
  }

  public int tdlibInstanceMode () {
    if (BitwiseUtils.getFlag(flags, FLAG_SERVICE | FLAG_DEBUG)) {
      if (BitwiseUtils.getFlag(flags, FLAG_SERVICE)) {
        return Tdlib.Mode.SERVICE;
      } else {
        return Tdlib.Mode.DEBUG;
      }
    } else {
      return Tdlib.Mode.NORMAL;
    }
  }

  // is_registered

  boolean setDeviceRegistered (boolean isRegistered) {
    return changeFlag(FLAG_DEVICE_REGISTERED, isRegistered);
  }

  public boolean isDeviceRegistered () {
    return BitwiseUtils.getFlag(flags, FLAG_DEVICE_REGISTERED);
  }

  // user_id

  public boolean setKnownUserId (long knownUserId) {
    if (this.knownUserId != knownUserId) {
      this.knownUserId = knownUserId;
      return true;
    }
    return false;
  }

  public long getKnownUserId () {
    return knownUserId;
  }

  public boolean setOrder (int order) {
    if (this.order != order) {
      this.order = order;
      return true;
    }
    return false;
  }

  public int getOrder () {
    return order;
  }

  public boolean hasTdlib (boolean activeOnly) {
    synchronized (sync) {
      return tdlib != null && !(activeOnly && tdlib.isPaused());
    }
  }

  public Tdlib activeTdlib () {
    synchronized (sync) {
      return tdlib != null && !tdlib.isPaused() ? tdlib : null;
    }
  }

  public void closeTdlib (Runnable after) {
    synchronized (sync) {
      if (tdlib != null && !tdlib.isPaused()) {
        tdlib.pause(after);
        return;
      }
    }
    U.run(after);
  }

  public boolean allowTdlib () {
    return hasTdlib(true) /*|| keepAlive()*/;
  }

  boolean hasDisplayInfo () {
    if (hasTdlib(true)) {
      TdApi.User user = tdlib().myUser();
      if (user != null)
        return true;
    }
    return hasUserInformation();
  }

  private final AtomicBoolean locked = new AtomicBoolean(false);

  @Override
  public int accountId () {
    return id;
  }

  private void createTdlib () {
    if (locked.getAndSet(true))
      throw new AssertionError();
    Throwable error = null;
    try {
      tdlib = new Tdlib(this, tdlibInstanceMode());
    } catch (Throwable t) {
      if (t instanceof InterruptedException || t.getCause() instanceof InterruptedException) {
        throw t;
      }
      error = t;
    } finally {
      locked.set(false);
    }
    if (error != null)
      Tracer.onLaunchError(error);
  }

  @Override
  @NonNull
  public Tdlib tdlib () {
    boolean needWakeup = tdlib != null;
    if (!needWakeup) {
      synchronized (sync) {
        if (tdlib == null) {
          createTdlib();
        } else {
          needWakeup = true;
        }
      }
    }
    if (needWakeup) {
      tdlib.wakeUp();
    }
    return tdlib;
  }

  @NonNull
  public Tdlib tdlibNoWakeup () {
    synchronized (sync) {
      if (tdlib == null) {
        createTdlib();
      }
    }
    return tdlib;
  }

  public boolean ownsClient (Client client) {
    synchronized (sync) {
      if (tdlib == null)
        return false;
    }
    return tdlib.ownsClient(client);
  }

  boolean launch (boolean force) {
    if (force || keepAlive()) {
      tdlib();
      return true;
    }
    return false;
  }

  public TdlibManager context () {
    return context;
  }

  // Convenience

  public boolean isUnauthorized () {
    return BitwiseUtils.getFlag(flags, FLAG_UNAUTHORIZED);
  }

  boolean setUnauthorized (boolean isUnauthorized, long knownUserId) {
    boolean changed = changeFlag(FLAG_UNAUTHORIZED, isUnauthorized);
    if (changed) {
      this.modificationTime = System.currentTimeMillis();
    }
    if (isUnauthorized) {
      changed = setKnownUserId(0) || changed;
      changed = setOrder(-1) || changed;
      deleteDisplayInformation();
    } else {
      changed = setKnownUserId(knownUserId) || changed;
      changed = changeFlag(FLAG_NO_PRIVATE_DATA, false) || changed;
    }
    return changed;
  }

  // Authorization check

  boolean comparePhoneNumber (String phoneNumber) {
    return StringUtils.equalsOrBothEmpty(getPhoneNumber(), phoneNumber);
  }

  // Fake display info

  public static class DisplayInformation {
    public final String prefix;

    private long userId;
    private String firstName;
    private String lastName;
    private TdApi.Usernames usernames;
    private String phoneNumber;
    private String profilePhotoSmallPath, profilePhotoBigPath;

    DisplayInformation (String prefix) {
      this.prefix = prefix;
    }

    DisplayInformation (String prefix, int accountId, TdApi.User user, boolean isUpdate) {
      this.prefix = prefix;
      this.userId = user.id;
      this.firstName = user.firstName;
      this.lastName = user.lastName;
      this.usernames = user.usernames;
      this.phoneNumber = user.phoneNumber;
      if (user.profilePhoto != null) {
        this.profilePhotoSmallPath = TD.isFileLoaded(user.profilePhoto.small) ? user.profilePhoto.small.local.path : isUpdate ? getUserProfilePhotoPath(accountId, false) : null;
        this.profilePhotoBigPath = TD.isFileLoaded(user.profilePhoto.big) ? user.profilePhoto.big.local.path : isUpdate ? getUserProfilePhotoPath(accountId, true) : null;
      } else {
        this.profilePhotoSmallPath = this.profilePhotoBigPath = null;
      }
      saveAll();
    }

    public String getFirstName () {
      return firstName;
    }

    public String getLastName () {
      return lastName;
    }

    @Nullable
    public TdApi.Usernames getUsernames () {
      return usernames;
    }

    @Nullable
    public String getUsername () {
      return Td.primaryUsername(usernames);
    }

    public String getPhoneNumber () {
      return phoneNumber;
    }

    public String getProfilePhotoPath (boolean big) {
      return big ? profilePhotoBigPath : profilePhotoSmallPath;
    }

    void setUserProfilePhotoPath (boolean big, String path) {
      String key;
      if (big) {
        profilePhotoBigPath = path;
        key = prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL;
      } else {
        profilePhotoSmallPath = path;
        key = prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO;
      }
      if (StringUtils.isEmpty(path)) {
        Settings.instance().remove(key);
      } else {
        Settings.instance().putString(key, path);
      }
    }

    static String getUserProfilePhotoPath (int accountId, boolean big) {
      String key;
      if (big)
        key = Settings.accountInfoPrefix(accountId) + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL;
      else
        key = Settings.accountInfoPrefix(accountId) + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO;
      return Settings.instance().getString(key, null);
    }

    static void setUserProfilePhotoPath (int accountId, boolean big, String path) {
      String key;
      if (big)
        key = Settings.accountInfoPrefix(accountId) + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL;
      else
        key = Settings.accountInfoPrefix(accountId) + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO;
      if (StringUtils.isEmpty(path)) {
        Settings.instance().remove(key);
      } else {
        Settings.instance().putString(key, path);
      }
    }

    private void saveAll () {
      LevelDB editor = Settings.instance().edit();
      editor.putLong(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_ID, userId);
      editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME1, firstName);
      editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME2, lastName);
      if (usernames != null) {
        editor
          .putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAME, usernames.editableUsername);
      } else {
        editor
          .remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAME);
      }
      if (usernames != null && usernames.activeUsernames != null && usernames.activeUsernames.length > 0) {
        editor.putStringArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_ACTIVE, usernames.activeUsernames);
      } else {
        editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_ACTIVE);
      }
      if (usernames != null && usernames.disabledUsernames != null && usernames.disabledUsernames.length > 0) {
        editor.putStringArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_DISABLED, usernames.disabledUsernames);
      } else {
        editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_DISABLED);
      }
      editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHONE, phoneNumber);
      if (!StringUtils.isEmpty(profilePhotoSmallPath)) {
        editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO, profilePhotoSmallPath);
      } else {
        editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO);
      }
      if (!StringUtils.isEmpty(profilePhotoBigPath)) {
        editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL, profilePhotoBigPath);
      } else {
        editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL);
      }
      editor.apply();
    }

    static DisplayInformation fullRestore (String prefix, long expectedUserId) {
      DisplayInformation info = null;
      for (LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
        /*if (entry.key().length() == prefix.length()) {
          long userId = entry.asLong();
          if (userId != expectedUserId)
            return null;
          info = new DisplayInformation(prefix);
        }*/
        if (info == null)
          info = new DisplayInformation(prefix);
        switch (entry.key().substring(prefix.length())) {
          case Settings.KEY_ACCOUNT_INFO_SUFFIX_ID:
            info.userId = entry.asLong();
            if (info.userId != expectedUserId)
              return null;
            break;
          case Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME1:
            info.firstName = entry.asString();
            break;
          case Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME2:
            info.lastName = entry.asString();
            break;
          case Settings.KEY_ACCOUNT_INFO_SUFFIX_PHONE:
            info.phoneNumber = entry.asString();
            break;
          case Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO:
            info.profilePhotoSmallPath = entry.asString();
            break;
          case Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL:
            info.profilePhotoBigPath = entry.asString();
            break;
        }
      }
      return info != null && info.userId == expectedUserId ? info : null;
    }
  }

  private ImageFile avatarSmallFile, avatarBigFile;
  private DisplayInformation displayInformation;

  void storeUserInformation (@Nullable TdApi.User user) {
    avatarSmallFile = avatarBigFile = null;
    if (user != null && user.id == knownUserId) {
      String prefix = Settings.accountInfoPrefix(id);
      boolean isUpdate = Settings.instance().getLong(prefix, 0) == user.id;
      displayInformation = new DisplayInformation(prefix, id, user, isUpdate);
    } else {
      deleteDisplayInformation();
      counters.clear();
    }
  }

  void storeUserProfilePhotoPath (boolean big, @Nullable String photoPath) {
    if (displayInformation != null) {
      displayInformation.setUserProfilePhotoPath(big, photoPath);
    } else {
      DisplayInformation.setUserProfilePhotoPath(id, big, photoPath);
    }
  }

  private final Map<String, TdlibCounter> counters = new HashMap<>();
  private final TdlibBadgeCounter unreadCounter = new TdlibBadgeCounter();

  void storeCounter (@NonNull TdApi.ChatList chatList, @NonNull TdlibCounter newCounter, boolean areChats) {
    String key = TD.makeChatListKey(chatList);
    TdlibCounter counter = counters.get(key);
    if (counter == null) {
      counter = new TdlibCounter(newCounter);
      counters.put(key, counter);
    } else {
      counter.reset(newCounter);
    }
    counter.save(Settings.accountInfoPrefix(id) + Settings.KEY_ACCOUNT_INFO_SUFFIX_COUNTER + (chatList instanceof TdApi.ChatListMain ? "" : key + "_"), areChats);
  }

  public TdlibCounter getCounter (@NonNull TdApi.ChatList chatList) {
    String key = TD.makeChatListKey(chatList);
    TdlibCounter counter = counters.get(key);
    if (counter == null) {
      counter = new TdlibCounter();
      counters.put(key, counter);
      counter.restore(Settings.accountInfoPrefix(id) + Settings.KEY_ACCOUNT_INFO_SUFFIX_COUNTER + (chatList instanceof TdApi.ChatListMain ? "" : key + "_"));
    }
    return counter;
  }

  public DisplayInformation getDisplayInformation () {
    if (knownUserId == 0)
      return null;
    if (displayInformation != null && displayInformation.userId == knownUserId)
      return displayInformation;
    return displayInformation = DisplayInformation.fullRestore(Settings.accountInfoPrefix(id), knownUserId); // FIXME replace with singular restore
  }

  private void deleteDisplayInformation () {
    Settings.instance().removeByPrefix(Settings.accountInfoPrefix(id), null);
    displayInformation = null;
  }

  private boolean hasUserInformation () {
    return knownUserId != 0 && (displayInformation != null && displayInformation.userId == knownUserId) ||
      (Settings.instance().pmc().getLong(Settings.accountInfoPrefix(id) + Settings.KEY_ACCOUNT_INFO_SUFFIX_ID, 0) == knownUserId);
  }

  // In-memory

  public TdlibBadgeCounter getUnreadBadge () {
    unreadCounter.reset(this);
    return unreadCounter;
  }

  public TdApi.User getUser () {
    return allowTdlib() ? tdlib().myUser() : null;
  }

  public String getPhoneNumber () {
    TdApi.User user = getUser();
    if (user != null)
      return user.phoneNumber;
    DisplayInformation info = getDisplayInformation();
    return info != null ? info.getPhoneNumber() : "…";
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    TdApi.User user = getUser();
    if (user != null)
      return tdlib.cache().userPlaceholderMetadata(user, false);
    DisplayInformation info = getDisplayInformation();
    if (info != null)
      return new AvatarPlaceholder.Metadata(TD.getAvatarColorId(knownUserId, knownUserId), TD.getLetters(info.getFirstName(), info.getLastName()));
    if (knownUserId != 0)
      return new AvatarPlaceholder.Metadata(TD.getAvatarColorId(knownUserId, knownUserId));
    return null;
  }

  public ImageFile getAvatarFile (boolean big) {
    DisplayInformation info = getDisplayInformation();
    String path = info != null ? info.getProfilePhotoPath(big) : null; // DisplayInformation.getUserProfilePhotoPath(id, big)
    if (!StringUtils.isEmpty(path)) {
      ImageFile avatarFile = big ? avatarBigFile : avatarSmallFile;
      if (!(avatarFile instanceof ImageFileLocal) || !StringUtils.equalsOrBothEmpty(((ImageFileLocal) avatarFile).getPath(), path)) {
        avatarFile = new ImageFileLocal(path);
        if (!big) {
          avatarFile.setSize(ChatView.getDefaultAvatarCacheSize());
        }
        if (big)
          avatarBigFile = avatarFile;
        else
          avatarSmallFile = avatarFile;
      }
      return avatarFile;
    }
    return null;
  }

  /*public ImageFile getRemoteAvatarFile (boolean big) {
    TdApi.User myUser = getUser();
    if (myUser == null) {
      DisplayInformation info = getDisplayInformation();
      String remoteId = info != null ? (big ? info.getProfilePhotoBigId() : info.getProfilePhotoSmallId()) : null;
      if (!Strings.isEmpty(remoteId)) {
        ImageFileRemote file = new ImageFileRemote(this, remoteId, new TdApi.FileTypeProfilePhoto());
        file.setSize(ChatView.getAvatarSize());
        return file;
      }
      return null;
    }
    if (myUser.profilePhoto == null)
      return null;
    String remoteId = big ? myUser.profilePhoto.big.remote.id : myUser.profilePhoto.small.remote.id;
    ImageFileRemote file = new ImageFileRemote(this, remoteId, new TdApi.FileTypeProfilePhoto());
    file.setSize(ChatView.getAvatarSize());
    return file;
  }*/

  public String getName () {
    TdApi.User myUser = getUser();
    if (myUser != null)
      return TD.getUserName(myUser);
    DisplayInformation info = getDisplayInformation();
    return info != null ? TD.getUserName(info.getFirstName(), info.getLastName()) : "User #" + knownUserId;
  }

  public boolean hasUserInfo () {
    return getUser() != null || getDisplayInformation() != null;
  }

  public String getFirstName () {
    TdApi.User myUser = getUser();
    if (myUser != null)
      return myUser.firstName;
    DisplayInformation info = getDisplayInformation();
    return info != null ? info.getFirstName() : "User";
  }

  public String getLastName () {
    TdApi.User myUser = getUser();
    if (myUser != null)
      return myUser.lastName;
    DisplayInformation info = getDisplayInformation();
    return info != null ? info.getLastName() : "#" + knownUserId;
  }

  @Nullable
  public TdApi.Usernames getUsernames () {
    TdApi.User myUser = getUser();
    if (myUser != null)
      return myUser.usernames;
    DisplayInformation info = getDisplayInformation();
    return info != null ? info.getUsernames() : null;
  }

  @Nullable
  public String getUsername () {
    TdApi.Usernames usernames = getUsernames();
    return Td.primaryUsername(usernames);
  }

  public String getLongName () {
    TdApi.User myUser = getUser();
    String firstName, lastName, username, phoneNumber;
    if (myUser != null) {
      firstName = myUser.firstName;
      lastName = myUser.lastName;
      username = Td.primaryUsername(myUser.usernames);
      phoneNumber = myUser.phoneNumber;
    } else {
      DisplayInformation info = getDisplayInformation();
      if (info == null)
        return null;
      firstName = info.getFirstName();
      lastName = info.getLastName();
      username = info.getUsername();
      phoneNumber = info.getPhoneNumber();
    }
    String name = TD.getUserName(firstName, lastName);
    if (context.hasAccountWithName(firstName, lastName, id) != TdlibAccount.NO_ID) {
      if (!StringUtils.isEmpty(username))
        return name + " (@" + username + ")";
      return name + " (" + Strings.formatPhone(phoneNumber) + ")";
    }
    return name;
  }

  public String getShortName () {
    TdApi.User myUser = tdlib().myUser();
    String username;
    String firstName;
    String lastName;
    String phoneNumber;
    if (myUser != null) {
      username = Td.primaryUsername(myUser.usernames);
      firstName = myUser.firstName;
      lastName = myUser.lastName;
      phoneNumber = myUser.phoneNumber;
    } else {
      DisplayInformation info = getDisplayInformation();
      if (info == null)
        return null;
      username = info.getUsername();
      firstName = info.getFirstName();
      lastName = info.getLastName();
      phoneNumber = info.getPhoneNumber();
    }
    if (!StringUtils.isEmpty(username)) {
      return "@" + username;
    }
    if (context.hasAccountWithFirstName(firstName, id) == TdlibAccount.NO_ID) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && firstName.length() < 12 && !StringUtils.isEmpty(lastName)) {
        return TD.getUserName(firstName, lastName);
      } else {
        return firstName;
      }
    }
    if (!StringUtils.isEmpty(lastName)) {
      return TD.getUserName(firstName, lastName);
    }
    return firstName + " " + Strings.formatPhone(phoneNumber);
  }
}
