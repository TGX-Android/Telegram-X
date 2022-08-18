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
 * File created on 20/11/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.sync.SyncAdapter;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.RingtoneItem;
import org.thunderdog.challegram.util.SimpleStringItem;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.InfiniteRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RadioView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.td.ChatId;

public class SettingsNotificationController extends RecyclerViewController<SettingsNotificationController.Args> implements
  View.OnClickListener, View.OnLongClickListener,
  ViewController.SettingsIntDelegate,
  ViewController.SettingsStringDelegate,
  NotificationSettingsListener, TdlibOptionListener, ActivityResultHandler,
  PopupLayout.DismissListener, MoreDelegate, TdlibSettingsManager.NotificationProblemListener {
  public static class Args {
    public final long chatId;
    public final TdApi.NotificationSettingsScope scope;

    public Args (long chatId) {
      this.chatId = chatId;
      this.scope = null;
    }

    public Args (TdApi.NotificationSettingsScope scope) {
      this.scope = scope;
      this.chatId = 0;
    }
  }

  public SettingsNotificationController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_notificationSettings;
  }

  @Override
  protected int getMenuId () {
    return isCommonScreen() ? R.id.menu_more : 0;
  }

  @Override
  protected void openMoreMenu () {
    IntList ids = new IntList(1);
    StringList strings = new StringList(1);

    ids.append(R.id.btn_resetNotifications);
    strings.append(R.string.ResetNotifications);

    showMore(ids.get(), strings.get(), 0);
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_resetNotifications: {
        showResetNotificationsConfirm();
        break;
      }
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putLong(keyPrefix + "chat_id", customChatId);
    outState.putInt(keyPrefix + "scope", scope != null ? scope.getConstructor() : 0);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    setCustomChatId(in.getLong(keyPrefix + "chat_id", 0));
    setScope(in.getInt(keyPrefix + "scope", 0));
    return true;
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  private ListItem pinnedMessagesInfo, mentionsInfo, mergeInfo, dismissedHint, notificationModeHint, enabledInfo, secretInfo, errorHint, errorButton;

  @Override
  public CharSequence getName () {
    if (customChatId != 0)
      return Lang.getString(R.string.CustomNotifications);
    if (scope != null) {
      switch (scope.getConstructor()) {
        case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
          return Lang.getString(R.string.PrivateChats);
        case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
          return Lang.getString(R.string.Groups);
        case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
          return Lang.getString(R.string.Channels);
      }
    }
    return Lang.getString(R.string.Notifications);
  }

  private SettingsAdapter adapter;
  private ArrayList<RingtoneItem> notificationSounds;
  private ArrayList<RingtoneItem> voiceRingtones;

  private long customChatId, callChatId;
  private boolean customHasMore;
  private TdApi.NotificationSettingsScope scope;

  private void setCustomChatId (long chatId) {
    this.customChatId = chatId;
    long callChatId = 0;
    if (chatId != 0 && ChatId.isUserChat(chatId)) {
      long userId = tdlib.chatUserId(chatId);
      if (userId != 0) {
        TdApi.User user = tdlib.cache().user(userId);
        if (user != null && user.type.getConstructor() == TdApi.UserTypeRegular.CONSTRUCTOR) {
          callChatId = ChatId.fromUserId(userId);
        }
      }
    }
    this.callChatId = callChatId;
  }

  private void setScope (int scopeConstructor) {
    switch (scopeConstructor) {
      case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
        setScope(new TdApi.NotificationSettingsScopePrivateChats());
        break;
      case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
        setScope(new TdApi.NotificationSettingsScopeGroupChats());
        break;
      case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
        setScope(new TdApi.NotificationSettingsScopeChannelChats());
        break;
      case 0:
        setScope(null);
        break;
      default:
        throw new UnsupportedOperationException("constructor == " + scopeConstructor);
    }
  }

  private void setScope (TdApi.NotificationSettingsScope scope) {
    this.scope = scope;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    setCustomChatId(args.chatId);
    setScope(args.scope);
  }

  private ArrayList<RingtoneItem> getCallRingtones () {
    if (voiceRingtones == null || voiceRingtones.isEmpty()) {
      voiceRingtones = getRingtones(context(), RingtoneManager.TYPE_RINGTONE, customChatId != 0 ? tdlib.notifications().getCallRingtone() : null);
    }
    return voiceRingtones;
  }

  private ArrayList<RingtoneItem> getNotificationSounds () {
    if (notificationSounds == null || notificationSounds.isEmpty()) {
      String customDefaultUri = customChatId != 0 ? tdlib.notifications().getDefaultSound(tdlib.notifications().scope(customChatId)) : null;
      notificationSounds = getRingtones(context(), RingtoneManager.TYPE_NOTIFICATION, customDefaultUri);
    }
    return notificationSounds;
  }

  private static ListItem newPrioritySetting () {
    return new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications_priorityOrImportance, 0, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? R.string.NotificationImportance : R.string.NotificationsPriority);
  }

  private boolean hasPrioritySetting;

  private boolean needPriorityOrImportanceSetting () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (scope != null) {
        if (scope.getConstructor() != TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR)
          return true;
        int importance = tdlib.notifications().getDefaultPriorityOrImportance(scope);
        return importance != TdlibNotificationManager.DEFAULT_PRIORITY_OR_IMPORTANCE;
      }
      return customChatId != 0;
    }
    return true;
  }

  private void checkPrioritySetting () {
    if (hasPrioritySetting) {
      adapter.updateValuedSettingById(R.id.btn_notifications_priorityOrImportance);
      return;
    }
    if (customChatId != 0 || scope == null)
      return;
    boolean need = needPriorityOrImportanceSetting();
    if (hasPrioritySetting != need) {
      saveRecyclerPosition();
      if (need) {
        int i = adapter.indexOfViewById(R.id.btn_notifications_led);
        if (i == -1)
          return;
        adapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.getItems().add(i + 2, newPrioritySetting());
        adapter.notifyItemRangeInserted(i + 1, 2);
      } else {
        int i = adapter.indexOfViewById(R.id.btn_notifications_priorityOrImportance);
        if (i == -1)
          return;
        adapter.removeRange(i, 2);
      }
      this.hasPrioritySetting = need;
      applySavedRecyclerPosition();
    }
  }

  private boolean hasVibrateAndSound;

  private void checkVibrateAndSoundSettings () {
    boolean need = needVibrateAndSoundSettings();
    if (this.hasVibrateAndSound != need) {
      int vibrateId, soundId, nextId;
      if (customChatId != 0) {
        nextId = R.id.btn_customChat_priorityOrImportance;
        vibrateId = R.id.btn_customChat_vibrate;
        soundId = R.id.btn_customChat_sound;
      } else {
        nextId = R.id.btn_notifications_led;
        vibrateId = R.id.btn_notifications_vibrate;
        soundId = R.id.btn_notifications_sound;
      }
      saveRecyclerPosition();
      if (need) {
        int i = adapter.indexOfViewById(nextId);
        if (i == -1)
          return;
        adapter.getItems().add(i, new ListItem(ListItem.TYPE_VALUED_SETTING, vibrateId, 0, R.string.Vibrate));
        adapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.getItems().add(i + 2, new ListItem(ListItem.TYPE_VALUED_SETTING, soundId, 0, R.string.Sound));
        adapter.getItems().add(i + 3, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.notifyItemRangeInserted(i, 4);
      } else {
        int i = adapter.indexOfViewById(vibrateId);
        if (i == -1)
          return;
        adapter.removeRange(i, 4);
      }
      this.hasVibrateAndSound = need;
      applySavedRecyclerPosition();
    }
  }

  private boolean needVibrateAndSoundSettings () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (customChatId != 0) {
        return tdlib.notifications().getEffectivePriorityOrImportance(customChatId) >= android.app.NotificationManager.IMPORTANCE_DEFAULT;
      } else if (scope != null) {
        return tdlib.notifications().getDefaultPriorityOrImportance(scope) >= android.app.NotificationManager.IMPORTANCE_DEFAULT;
      } else {
        return false;
      }
    }
    return true;
  }

  private boolean inErrorMode;

  private @DrawableRes int getErrorIcon (@TdlibNotificationManager.Status int status) {
    switch (status) {
      case TdlibNotificationManager.Status.DISABLED_APP_SYNC:
      case TdlibNotificationManager.Status.DISABLED_SYNC:
        return R.drawable.baseline_sync_problem_24;
      case TdlibNotificationManager.Status.FIREBASE_MISSING:
        return R.drawable.baseline_system_update_24;
      case TdlibNotificationManager.Status.FIREBASE_ERROR:
        return R.drawable.baseline_bug_report_24;
    }
    return R.drawable.baseline_notification_important_24;
  }

  private @StringRes int getErrorText (@TdlibNotificationManager.Status int status) {
    switch (status) {
      case TdlibNotificationManager.Status.DISABLED_SYNC:
        return R.string.TurnSyncOnSystem;
      case TdlibNotificationManager.Status.DISABLED_APP_SYNC:
        return R.string.TurnSyncOnApp;
      case TdlibNotificationManager.Status.FIREBASE_MISSING:
        return R.string.InstallGooglePlayServices;
      case TdlibNotificationManager.Status.INTERNAL_ERROR:
      case TdlibNotificationManager.Status.FIREBASE_ERROR:
        return R.string.ShareNotificationError;
    }
    return R.string.SystemNotificationSettings;
  }

  private CharSequence makeErrorDescription (@TdlibNotificationManager.Status int status) {
    int guideRes = R.string.NotificationsGuideBlockedApp;
    switch (status) {
      case TdlibNotificationManager.Status.BLOCKED_ALL:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          guideRes = R.string.NotificationsGuideBlockedAll;
        }
        break;
      case TdlibNotificationManager.Status.BLOCKED_CATEGORY:
        guideRes = R.string.NotificationsGuideBlockedCategory;
        break;
      case TdlibNotificationManager.Status.DISABLED_SYNC:
        guideRes = R.string.NotificationsGuideSyncGlobalOff;
        break;
      case TdlibNotificationManager.Status.DISABLED_APP_SYNC:
        guideRes = R.string.NotificationsGuideSyncAppOff;
        break;
      case TdlibNotificationManager.Status.FIREBASE_MISSING:
        guideRes = R.string.NotificationsGuideFirebaseUnavailable;
        break;
      case TdlibNotificationManager.Status.FIREBASE_ERROR:
        guideRes = R.string.NotificationsGuideFirebaseError;
        break;
      case TdlibNotificationManager.Status.INTERNAL_ERROR: {
        long chatId = tdlib.settings().getLastNotificationProblematicChat();
        if (chatId != 0) {
          TdApi.Chat chat = tdlib.chatSync(chatId);
          if (chat != null) {
            return Lang.getMarkdownString(this, R.string.NotificationsGuideErrorChat, Lang.boldCreator(), tdlib.chatTitle(chat));
          }
        }
        guideRes = R.string.NotificationsGuideError;
        break;
      }
    }
    CharSequence text = Lang.getMarkdownString(this, guideRes);
    if (guideRes == R.string.NotificationsGuideBlockedCategory) {
      String name = tdlib.accountName();
      if (text instanceof String) {
        return Lang.getStringBold(guideRes, name);
      }
      if (text instanceof SpannableStringBuilder) {
        SpannableStringBuilder spannable = (SpannableStringBuilder) text;
        int i = StringUtils.indexOf(text, "%1$s", 0);
        if (i != -1) {
          spannable.replace(i, i + "%1$s".length(), name);
          spannable.setSpan(Lang.newBoldSpan(Text.needFakeBold(name)), i, i + name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          return spannable;
        }
      }
      return Lang.getString(R.string.NotificationsGuideBlockedApp);
    }
    return text;
  }

  private void checkInErrorMode () {
    boolean need = needErrorMode();
    if (this.inErrorMode != need) {
      saveRecyclerPosition();
      if (need) {
        int i = adapter.indexOfViewById(R.id.btn_showAdvanced);
        if (i == -1)
          return;
        adapter.removeRange(i - 1, 3); // Removing from the original place

        int viewType = adapter.getItems().get(0).getViewType();

        @TdlibNotificationManager.Status int status = tdlib.notifications().getNotificationBlockStatus();

        List<ListItem> itemsToAdd = new ArrayList<>();
        itemsToAdd.add(errorButton = new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, getErrorIcon(status), getErrorText(status)).setTextColorId(R.id.theme_color_textNegative));
        itemsToAdd.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        itemsToAdd.add(errorHint = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, makeErrorDescription(status), false));
        if (viewType != ListItem.TYPE_HEADER_PADDED) {
          itemsToAdd.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        }

        adapter.getItems().addAll(0, itemsToAdd);
        adapter.notifyItemRangeInserted(0, itemsToAdd.size());
        if (viewType == ListItem.TYPE_HEADER_PADDED) {
          final int index = itemsToAdd.size();
          adapter.getItems().get(index).setViewType(ListItem.TYPE_HEADER);
          adapter.notifyItemChanged(index);
        }
      } else {
        boolean keepHeader = notificationModeHint != null;
        adapter.removeRange(0, keepHeader ? 3 : 4);

        int index = adapter.getItems().size();
        adapter.getItems().addAll(Arrays.asList(
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, 0, R.string.SystemNotificationSettings),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
        ));
        adapter.notifyItemRangeInserted(index, 3);
        if (keepHeader) {
          adapter.getItems().get(0).setViewType(ListItem.TYPE_HEADER_PADDED);
          adapter.notifyItemChanged(0);
        }
      }
      this.inErrorMode = need;
      if (need) {
        ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(0, 0);
      } else {
        applySavedRecyclerPosition();
      }
    } else if (need && (errorHint != null || errorButton != null)) {
      @TdlibNotificationManager.Status int status = tdlib.notifications().getNotificationBlockStatus();
      if (errorHint != null && errorHint.setStringIfChanged(makeErrorDescription(status))) {
        adapter.updateValuedSetting(errorHint);
      }
      if (errorButton != null) {
        boolean update = errorButton.setStringIfChanged(getErrorText(status));
        update = errorButton.setIconRes(getErrorIcon(status)) || update;
        if (update) {
          adapter.updateValuedSetting(errorButton);
        }
      }
    }
  }

  private boolean needErrorMode () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && customChatId == 0 && scope == null && tdlib.notifications().areNotificationsBlockedGlobally();
  }

  private static ArrayList<RingtoneItem> getRingtones (Context context, int type, @Nullable String customDefaultRingtoneUri) {
    ArrayList<RingtoneItem> ringtoneItems = new ArrayList<>();
    Uri defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, type);

    Cursor cursor = null;
    try {
      RingtoneManager manager = new RingtoneManager(context);

      manager.setType(type);
      cursor = manager.getCursor();
      ringtoneItems.ensureCapacity(cursor.getCount());
      while (cursor.moveToNext()) {
        int id = cursor.getInt(RingtoneManager.ID_COLUMN_INDEX);
        String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
        Uri ringtoneURI = manager.getRingtoneUri(cursor.getPosition());
        if (title != null && !title.isEmpty() && ringtoneURI != null) {
          boolean isDefault = customDefaultRingtoneUri != null ? customDefaultRingtoneUri.equals(ringtoneURI.toString()) : defaultRingtoneUri != null && defaultRingtoneUri.equals(ringtoneURI);
          ringtoneItems.add(new RingtoneItem(id, title, ringtoneURI, isDefault));
        }
      }
    } catch (Throwable t) {
      Log.e(t);
    }

    if (!ringtoneItems.isEmpty()) {
      Collections.sort(ringtoneItems, (o1, o2) -> {
        boolean d1 = o1.isDefault();
        boolean d2 = o2.isDefault();
        return d1 && !d2 ? -1 : !d1 && d2 ? 1 : 0;
      });
      ringtoneItems.add(1, new RingtoneItem(-1, Lang.getString(type == RingtoneManager.TYPE_RINGTONE ? R.string.RingtoneDisabled : R.string.SoundDisabled), Uri.EMPTY, customDefaultRingtoneUri != null && customDefaultRingtoneUri.isEmpty()));
    }

    // Java in a nutshell
    if (cursor != null) { try { cursor.close(); } catch (Throwable ignored) { } }

    return ringtoneItems;
  }

  private TdApi.NotificationSettingsScope getScope (ListItem item) {
    return item != null && item.getData() instanceof TdApi.NotificationSettingsScope ? (TdApi.NotificationSettingsScope) item.getData() : scope;
  }

  private TdApi.NotificationSettingsScope getScope (ListItem item, TdApi.NotificationSettingsScope defaultScope) {
    TdApi.NotificationSettingsScope scope = getScope(item);
    return scope != null ? scope : defaultScope;
  }

  private int getSnoozeViewType () {
    int muteFor = tdlib.scopeMuteFor(scope);
    boolean needValue = (muteFor != 0 && !TD.isMutedForever(muteFor)) || (muteFor == 0 && tdlib.notifications().areNotificationsBlocked(scope));
    return needValue ? ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER : ListItem.TYPE_RADIO_SETTING;
  }

  private void checkSnoozeStyle () {
    if (scope != null) {
      int res = tdlib.scopeMuteFor(scope) == 0 ? (tdlib.notifications().areNotificationsBlocked(scope) ? R.string.NotificationsSettingBlocked : R.string.NotificationsSettingOn) : R.string.NotificationsSettingOff;
      if (enabledInfo != null && enabledInfo.setStringIfChanged(res)) {
        adapter.updateValuedSetting(enabledInfo);
      }

      int i = adapter.indexOfViewById(R.id.btn_notifications_snooze);
      if (i != -1) {
        int viewType = getSnoozeViewType();
        if (adapter.getItems().get(i).setViewType(viewType)) {
          adapter.notifyItemChanged(i);
          return;
        }
      }
      adapter.updateValuedSettingById(R.id.btn_notifications_snooze);
    }
  }

  private static final int NOTIFICATION_MODE_ALL = 0;
  private static final int NOTIFICATION_MODE_ACTIVE = 1;
  private static final int NOTIFICATION_MODE_SELECTED = 2;

  private int getNotificationMode () {
    if (Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT))
      return NOTIFICATION_MODE_ACTIVE;
    if (Settings.instance().checkNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS))
      return NOTIFICATION_MODE_SELECTED;
    return NOTIFICATION_MODE_ALL;
  }

  private void updateNotificationModeHint (boolean isUpdate) {
    if (notificationModeHint == null)
      return;
    int mode = getNotificationMode();
    boolean changed;
    switch (mode) {
      case NOTIFICATION_MODE_ALL:
        changed = notificationModeHint.setStringIfChanged(R.string.NotificationsModeAllHint);
        break;
      case NOTIFICATION_MODE_ACTIVE:
        changed = notificationModeHint.setStringIfChanged(Lang.getStringBold(R.string.NotificationsModeActiveHint, tdlib.accountName()));
        break;
      case NOTIFICATION_MODE_SELECTED:
        changed = notificationModeHint.setStringIfChanged(Lang.pluralBold(R.string.NotificationsModeSelectedHint, tdlib.context().getNumberOfAccountsWithEnabledNotifications()));
        break;
      default:
        throw new IllegalArgumentException(Integer.toString(mode));
    }
    if (changed) {
      int position = adapter.indexOfView(notificationModeHint);
      if (position != -1) {
        if (isUpdate) {
          adapter.updateValuedSettingByPosition(position);
        } else {
          adapter.notifyItemChanged(position);
        }
      }
    }
  }

  private static int notificationModeToId (int mode) {
    switch (mode) {
      case NOTIFICATION_MODE_ACTIVE:
        return R.id.btn_notificationMode_active;
      case NOTIFICATION_MODE_SELECTED:
        return R.id.btn_notificationMode_selected;
      case NOTIFICATION_MODE_ALL:
        return R.id.btn_notificationMode_all;
    }
    throw new IllegalArgumentException(Integer.toString(mode));
  }

  private List<TdlibAccount> displayingAccounts;
  private int collapsedAccountsCount;

  private boolean updateNotificationMode (int oldMode, int newMode, boolean updateToggle) {
    if (updateToggle) {
      int oldIndex = adapter.indexOfViewById(notificationModeToId(oldMode));
      int newIndex = adapter.indexOfViewById(notificationModeToId(newMode));
      if (oldIndex != newIndex) {
        adapter.processToggle(null, adapter.getItems().get(newIndex), true);
      }
    }
    if (oldMode != newMode && newMode != -1) {
      switch (newMode) {
        case NOTIFICATION_MODE_ALL: {
          Settings.instance().setNotificationFlag(oldMode == NOTIFICATION_MODE_SELECTED ? Settings.NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS : Settings.NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT, false);
          break;
        }
        case NOTIFICATION_MODE_ACTIVE: {
          Settings.instance().setNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_ACTIVE_ACCOUNT, true);
          break;
        }
        case NOTIFICATION_MODE_SELECTED: {
          Settings.instance().setNotificationFlag(Settings.NOTIFICATION_FLAG_ONLY_SELECTED_ACCOUNTS, true);
          break;
        }
      }

      updateNotificationModeHint(false);

      boolean hadAccountsList = oldMode == NOTIFICATION_MODE_SELECTED;
      boolean hasAccountsList = newMode == NOTIFICATION_MODE_SELECTED;

      if (hadAccountsList != hasAccountsList) {
        int notificationModeHintPosition = adapter.indexOfView(notificationModeHint);;
        if (notificationModeHintPosition == -1)
          throw new IllegalStateException();
        if (hasAccountsList) {
          List<ListItem> newItems = generateAccountsList();
          adapter.getItems().addAll(notificationModeHintPosition + 1, newItems);
          adapter.notifyItemRangeInserted(notificationModeHintPosition + 1, newItems.size());
        } else if (displayingAccounts != null) {
          adapter.removeRange(notificationModeHintPosition + 1, 1 + (displayingAccounts.size() - collapsedAccountsCount + (collapsedAccountsCount > 0 ? 1 : 0)) * 2);
          displayingAccounts = null;
          collapsedAccountsCount = 0;
        }
        adapter.resetCheckedItems();
      }

      return true;
    }

    return false;
  }

  private List<ListItem> generateAccountsList () {
    displayingAccounts = getDisplayingAccounts();
    collapsedAccountsCount = 0;
    int addedCount = 0;
    List<ListItem> newItems = new ArrayList<>(1 + displayingAccounts.size() * 2);
    for (TdlibAccount account : displayingAccounts) {
      if (newItems.isEmpty()) {
        newItems.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      } else {
        newItems.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      if (addedCount >= 1 && displayingAccounts.size() - addedCount > 1 && (!account.forceEnableNotifications() || addedCount >= MAXIMUM_DISPLAY_ACCOUNTS_NUM)) {
        collapsedAccountsCount = displayingAccounts.size() - addedCount;
        newItems.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showMore, R.drawable.baseline_direction_arrow_down_24, Lang.pluralBold(account.forceEnableNotifications() ? R.string.NotificationsModeSelectedMore : R.string.NotificationsModeSelectedMoreMuted, collapsedAccountsCount), false));
        break;
      } else {
        newItems.add(newAccountItem(account));
      }
      addedCount++;
    }
    newItems.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return newItems;
  }

  private static final int MAXIMUM_DISPLAY_ACCOUNTS_NUM = 3;

  private List<TdlibAccount> getDisplayingAccounts () {
    List<TdlibAccount> accounts = tdlib.context().accountsQueue();
    Collections.sort(accounts, (a, b) -> {
      int currentAccountId = tdlib.context().preferredAccountId();
      return (a.id == currentAccountId) != (b.id == currentAccountId) ? Boolean.compare((b.id == currentAccountId), (a.id == currentAccountId)) :
      a.forceEnableNotifications() != b.forceEnableNotifications() ? Boolean.compare(b.forceEnableNotifications(), a.forceEnableNotifications()) :
      0;
    });
    return accounts;
  }

  private ListItem newAccountItem (TdlibAccount account) {
    CharSequence name = Emoji.instance().replaceEmoji(account.getLongName());
    return new ListItem(
      ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR,
      account.id + 1, 0,
      account.id == tdlib.id() ? Lang.getCharSequence(R.string.CurrentAccount, name) : name,
      account.id + 1,
      account.forceEnableNotifications()
    ).setData(account)
     .setLongValue(account.getKnownUserId());
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int dataColorId = 0;
        if (item.getViewType() == ListItem.TYPE_SETTING) {
          view.setIconColorId(item.getId() == R.id.btn_showAdvanced ? R.id.theme_color_iconNegative : 0);
        }
        switch (item.getId()) {
          case R.id.btn_notifications_preview:
            view.getToggler().setRadioEnabled(tdlib.notifications().defaultShowPreview(getScope(item)), isUpdate);
            break;
          case R.id.btn_notifications_contentPreview:
            view.getToggler().setRadioEnabled(tdlib.notifications().isContentPreviewEnabled(getScope(item)), isUpdate);
            break;
          case R.id.btn_notifications_includeDismissed:
            view.getToggler().setRadioEnabled(Settings.instance().checkNotificationFlag(item.getIntValue()), isUpdate);
            break;
          case R.id.btn_notifications_snooze: {
            TdApi.NotificationSettingsScope scope = getScope(item);
            TdApi.ScopeNotificationSettings settings = tdlib.notifications().getScopeNotificationSettings(scope);

            TogglerView togglerView = view.getToggler();
            if (togglerView != null) {
              togglerView.setRadioEnabled(settings == null || settings.muteFor == 0, isUpdate);
            }
            if (SettingsNotificationController.this.scope == null || item.getViewType() == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER) {
              tdlib.ui().setValueForSetting(view, scope, SettingsNotificationController.this.scope == null);
            }
            break;
          }
          case R.id.btn_hideSecretChats: {
            view.getToggler().setRadioEnabled(!Settings.instance().needHideSecretChats(), isUpdate);
            break;
          }
          case R.id.btn_notifications_priorityOrImportance: {
            TdApi.NotificationSettingsScope scope = getScope(item);
            int priorityOrImportance = tdlib.notifications().getDefaultPriorityOrImportance(scope);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              boolean hasSound = tdlib.notifications().isDefaultSoundEnabled(scope);
              boolean hasVibration = tdlib.notifications().isDefaultVibrateModeEnabled(scope);
              view.setData(TdlibUi.getPriorityOrImportanceString(priorityOrImportance, hasSound, hasVibration));
              view.setDataColorId(priorityOrImportance == android.app.NotificationManager.IMPORTANCE_NONE ? R.id.theme_color_textNegative : 0);
            } else {
              view.setData(TdlibUi.getPriorityOrImportanceString(priorityOrImportance, true, true));
            }
            break;
          }
          case R.id.btn_customChat_priorityOrImportance: {
            int priorityOrImportance = tdlib.notifications().getCustomPriorityOrImportance(customChatId, TdlibNotificationManager.PRIORITY_OR_IMPORTANCE_UNSET);
            int defaultPriorityOrImportance = tdlib.notifications().getDefaultPriorityOrImportance(tdlib.notifications().scope(customChatId));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              boolean hasSound = tdlib.notifications().isSoundEnabled(customChatId);
              boolean hasVibration = tdlib.notifications().isVibrateModeEnabled(customChatId);
              boolean isBlockedDefault = priorityOrImportance == TdlibNotificationManager.PRIORITY_OR_IMPORTANCE_UNSET && tdlib.notifications().areNotificationsBlocked(tdlib.notifications().scope(customChatId));
              boolean isBlocked = priorityOrImportance == android.app.NotificationManager.IMPORTANCE_NONE || isBlockedDefault;
              if (isBlockedDefault) {
                view.setData(Lang.getString(R.string.IsDefault, Lang.getString(R.string.NotificationImportanceNone)));
              } else {
                view.setData(priorityOrImportance == defaultPriorityOrImportance ? R.string.Default : TdlibUi.getPriorityOrImportanceString(priorityOrImportance, hasSound, hasVibration));
              }
              view.setDataColorId(isBlocked ? R.id.theme_color_textNegative : 0);
            } else {
              view.setData(priorityOrImportance == defaultPriorityOrImportance ? R.string.Default : TdlibUi.getPriorityOrImportanceString(priorityOrImportance, true, true));
            }
            break;
          }
          /*case R.id.btn_appBadge:
            view.getToggler().setRadioEnabled(Settings.instance().needBadgeCounter(), isUpdate);
            break;*/
          case R.id.btn_appBadgeCountMessages:
          case R.id.btn_appBadgeCountMuted:
          case R.id.btn_appBadgeCountArchive:
            view.getToggler().setRadioEnabled((Settings.instance().getBadgeFlags() & item.getIntValue()) != 0, isUpdate);
            break;

          case R.id.btn_customChat_preview: {
            view.getToggler().setRadioEnabled(tdlib.notifications().isShowPreviewEnabled(customChatId, false), isUpdate);
            if (!ChatId.isSecret(customChatId)) {
              TdApi.ChatNotificationSettings settings = tdlib.chatSettings(customChatId);
              boolean defaultShowPreview = tdlib.notifications().defaultShowPreview(customChatId);
              boolean showPreview = settings == null || settings.useDefaultShowPreview ? defaultShowPreview : settings.showPreview;
              String info = Lang.getString(settings == null || settings.useDefaultShowPreview ? R.string.MessagePreviewDefault : showPreview ? R.string.MessagePreviewEnabled : R.string.MessagePreviewDisabled);
              view.setData(info);
            }
            break;
          }
          case R.id.btn_notifications_vibrate:
          case R.id.btn_calls_vibrate:
          case R.id.btn_customChat_vibrate:
          case R.id.btn_customChat_calls_vibrate: {
            final int mode;
            final boolean onlyIfSilent;

            switch (item.getId()) {
              case R.id.btn_notifications_vibrate: {
                TdApi.NotificationSettingsScope scope = getScope(item);
                mode = tdlib.notifications().getDefaultVibrateMode(scope);
                onlyIfSilent = tdlib.notifications().getDefaultVibrateOnlyIfSilent(scope);
                break;
              }
              case R.id.btn_calls_vibrate: {
                mode = tdlib.notifications().getCallVibrateMode();
                onlyIfSilent = tdlib.notifications().getCallVibrateOnlyIfSilent();
                break;
              }
              case R.id.btn_customChat_vibrate: {
                mode = tdlib.notifications().getCustomVibrateMode(customChatId, TdlibNotificationManager.VIBRATE_MODE_DEFAULT);
                onlyIfSilent = mode != TdlibNotificationManager.VIBRATE_MODE_DEFAULT && tdlib.notifications().getCustomVibrateOnlyIfSilent(customChatId);
                break;
              }
              case R.id.btn_customChat_calls_vibrate: {
                mode = tdlib.notifications().getCustomCallVibrateModeForChat(callChatId);
                onlyIfSilent = mode != TdlibNotificationManager.VIBRATE_MODE_DEFAULT && tdlib.notifications().getCustomCallVibrateOnlyIfSilentForChat(callChatId);
                break;
              }
              default:
                throw new RuntimeException();
            }

            switch (mode) {
              case TdlibNotificationManager.VIBRATE_MODE_DISABLED: {
                view.setData(R.string.VibrateDisabled);
                break;
              }
              case TdlibNotificationManager.VIBRATE_MODE_LONG: {
                if (onlyIfSilent) {
                  view.setData(Lang.getString(R.string.XOnlyIfSilent, Lang.getString(R.string.Long)));
                } else {
                  view.setData(R.string.Long);
                }
                break;
              }
              case TdlibNotificationManager.VIBRATE_MODE_SHORT: {
                if (onlyIfSilent) {
                  view.setData(Lang.getString(R.string.XOnlyIfSilent, Lang.getString(R.string.DoubleShort)));
                } else {
                  view.setData(R.string.DoubleShort);
                }
                break;
              }
              case TdlibNotificationManager.VIBRATE_MODE_DEFAULT: {
                if (onlyIfSilent) {
                  view.setData(Lang.getString(R.string.XButOnlyIfSilent, Lang.getString(R.string.Default)));
                } else {
                  view.setData(R.string.Default);
                }
                break;
              }
            }
            break;
          }
          case R.id.btn_notifications_led:
          case R.id.btn_customChat_led: {
            final int id = view.getId();
            final int color;
            switch (id) {
              case R.id.btn_notifications_led:
                color = tdlib.notifications().getDefaultLedColor(getScope(item));
                break;
              case R.id.btn_customChat_led:
                color = tdlib.notifications().getCustomLedColor(customChatId, TdlibNotificationManager.LED_COLOR_UNSET);
                if (color == TdlibNotificationManager.LED_COLOR_UNSET || color == tdlib.notifications().getDefaultLedColor(tdlib.notifications().scope(customChatId))) {
                  view.setColorDataId(ThemeColorId.NONE);
                  view.setData(R.string.LedDefault);
                  return;
                }
                break;
              default:
                throw new RuntimeException();
            }
            int i = color != TdlibNotificationManager.LED_COLOR_UNSET ? ArrayUtils.indexOf(TdlibNotificationManager.LED_COLORS, color) : -1;
            dataColorId = i != -1 ? TdlibNotificationManager.LED_COLORS_IDS[i] : 0;
            view.setData(i != -1 ? TdlibNotificationManager.LED_COLORS_STRINGS[i] : R.string.LedDisabled);
            break;
          }
          case R.id.btn_notifications_sound:
          case R.id.btn_calls_ringtone:
          case R.id.btn_customChat_sound:
          case R.id.btn_customChat_calls_ringtone: {
            // view.setEnabled(notificationRingtones != null && !notificationRingtones.isEmpty());
            String sound;
            switch (view.getId()) {
              case R.id.btn_notifications_sound:
                sound = tdlib.notifications().getDefaultSoundName(getScope(item));
                break;
              case R.id.btn_calls_ringtone:
                sound = tdlib.notifications().getCallRingtoneName();
                break;
              case R.id.btn_customChat_sound: {
                String customSound = tdlib.notifications().getCustomSound(customChatId, null);
                if (customSound != null && customSound.equals(tdlib.notifications().getDefaultSound(tdlib.notifications().scope(customChatId)))) {
                  sound = customSound.isEmpty() ? Lang.getString(R.string.IsDefault, Lang.lowercase(Lang.getString(R.string.SoundDisabled))) : null;
                } else {
                  sound = tdlib.notifications().getCustomSoundName(customChatId);
                }
                break;
              }
              case R.id.btn_customChat_calls_ringtone: {
                sound = tdlib.notifications().getCustomCallRingtoneName(callChatId);
                break;
              }
              default: {
                throw new IllegalStateException("Stub");
              }
            }
            if (sound != null) {
              view.setData(sound.isEmpty() ? Lang.getString(isRingtone(item.getId()) ? R.string.RingtoneDisabled : R.string.SoundDisabled) : sound);
            } else {
              view.setData(R.string.Default);
            }
            break;
          }
          case R.id.btn_inApp_chatSounds: {
            view.getToggler().setRadioEnabled(tdlib.notifications().areInAppChatSoundsEnabled(), isUpdate);
            break;
          }

          case R.id.btn_customChat_pinnedMessages: {
            view.getToggler().setRadioEnabled(!tdlib.notifications().arePinnedMessagesDisabled(customChatId), isUpdate);
            TdApi.ChatNotificationSettings settings = tdlib.chatSettings(customChatId);
            boolean defaultDisablePinned = tdlib.notifications().defaultDisablePinnedMessages(customChatId);
            boolean useDefault = settings == null || settings.useDefaultDisablePinnedMessageNotifications;
            boolean disablePinned = useDefault ? defaultDisablePinned : settings.disablePinnedMessageNotifications;
            String info = Lang.getString(useDefault ? R.string.PinnedDefault : disablePinned ? R.string.PinnedDisabled : R.string.PinnedEnabled);
            view.setData(info);
            break;
          }
          case R.id.btn_mergeCategories: {
            view.getToggler().setRadioEnabled(!Settings.instance().needSplitNotificationCategories(), isUpdate);
            break;
          }
          case R.id.btn_customChat_mentions: {
            view.getToggler().setRadioEnabled(!tdlib.notifications().areMentionsDisabled(customChatId), isUpdate);
            TdApi.ChatNotificationSettings settings = tdlib.chatSettings(customChatId);
            boolean defaultDisableMentions = tdlib.notifications().defaultDisableMentions(customChatId);
            boolean useDefault = settings == null || settings.useDefaultDisableMentionNotifications;
            boolean disableMentions = useDefault ? defaultDisableMentions : settings.disableMentionNotifications;
            String info = Lang.getString(useDefault ? R.string.MentionsDefault : disableMentions ? R.string.MentionsDisabled : R.string.MentionsEnabled);
            view.setData(info);
            break;
          }
          case R.id.btn_events_mentions: {
            view.getToggler().setRadioEnabled(!tdlib.notifications().defaultDisableMentions(getScope(item, tdlib.notifications().scopeGroup())), isUpdate);
            break;
          }
          case R.id.btn_events_pinnedMessages: {
            view.getToggler().setRadioEnabled(!tdlib.notifications().defaultDisablePinnedMessages(getScope(item, tdlib.notifications().scopeGroup())), isUpdate);
            break;
          }
          case R.id.btn_events_contactJoined: {
            view.getToggler().setRadioEnabled(!tdlib.disableContactRegisteredNotifications(false), isUpdate);
            break;
          }
          case R.id.btn_events_sentScheduled: {
            view.getToggler().setRadioEnabled(!tdlib.areSentScheduledMessageNotificationsDisabled(), isUpdate);
            break;
          }
          case R.id.btn_silenceNonContacts: {
            view.getToggler().setRadioEnabled(tdlib.settings().needMuteNonContacts(), isUpdate);
            break;
          }
          case R.id.btn_archiveMuteNonContacts: {
            view.getToggler().setRadioEnabled(tdlib.autoArchiveEnabled(), isUpdate);
            break;
          }

          case R.id.btn_repeatNotifications: {
            int minutes = tdlib.notifications().getRepeatNotificationMinutes();
            if (minutes == 0) {
              view.setData(R.string.RepeatNotificationsDisabled);
            } else if (minutes >= 60) {
              minutes /= 60;
              view.setData(Lang.plural(R.string.xHours, minutes));
            } else {
              view.setData(Lang.plural(R.string.xMinutes, minutes));
            }
            break;
          }
        }
        view.setColorDataId(dataColorId);
      }
    };
    adapter.setOnLongClickListener(this);

    ArrayList<ListItem> items = new ArrayList<>();
    boolean hasOptions = false;

    if (customChatId != 0) {
      if (hasVibrateAndSound = needVibrateAndSoundSettings()) {
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_customChat_vibrate, 0, R.string.Vibrate));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_customChat_sound, 0, R.string.Sound));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_customChat_priorityOrImportance, 0, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? R.string.NotificationImportance : R.string.NotificationsPriority));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_customChat_led, 0, R.string.NotificationsLed));
      if (Config.SECRET_PREVIEWS_AVAILABLE || !ChatId.isSecret(customChatId)) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ChatId.isSecret(customChatId) ? ListItem.TYPE_RADIO_SETTING : ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_customChat_preview, 0, R.string.MessagePreview));
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (customHasMore = tdlib.notifications().hasCustomChatSettings(customChatId))) {
        customHasMore = true;
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_customChat_channel, 0, R.string.NotificationChannelMore));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      if (callChatId != 0) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.VoiceCalls));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_customChat_calls_vibrate, 0, R.string.Vibrate));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_customChat_calls_ringtone, 0, R.string.Ringtone));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.CustomNotificationsHint), false));

      boolean isChannel = tdlib.isChannel(customChatId);
      boolean canDisablePinned = tdlib.canDisablePinnedMessageNotifications(customChatId);
      if (isChannel && canDisablePinned) {
        canDisablePinned = tdlib.notifications().arePinnedMessagesDisabled(customChatId); // This is needed, because in beta version it was possible for a while
      }
      boolean canDisableMentions = tdlib.canDisableMentions(customChatId);
      if (canDisablePinned || canDisableMentions) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Prioritize));
      }
      if (canDisableMentions) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_customChat_mentions, 0, R.string.Mentions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(mentionsInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, !tdlib.notifications().areMentionsDisabled(customChatId) ? R.string.MentionsOff : R.string.MentionsOn));
      }
      if (canDisablePinned) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_customChat_pinnedMessages, 0, R.string.PinnedMessages));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(pinnedMessagesInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, !tdlib.notifications().arePinnedMessagesDisabled(customChatId) ? R.string.PinnedMessagesOff : R.string.PinnedMessagesOn));
      }
    } else {
      if (scope != null) {
        items.add(new ListItem(getSnoozeViewType(), R.id.btn_notifications_snooze, 0, R.string.NotificationsSetting));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(enabledInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, tdlib.scopeMuteFor(scope) == 0 ? R.string.NotificationsSettingOn : R.string.NotificationsSettingOff));

        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_notifications_preview, 0, R.string.MessagePreview));
        if (Config.NEED_NOTIFICATION_CONTENT_PREVIEW && tdlib.notifications().defaultShowPreview(scope)) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_notifications_contentPreview, 0, R.string.MessageContentPreview));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        if (hasVibrateAndSound = needVibrateAndSoundSettings()) {
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications_vibrate, 0, R.string.Vibrate));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications_sound, 0, R.string.Sound));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications_led, 0, R.string.NotificationsLed));
        if (hasPrioritySetting = needPriorityOrImportanceSetting()) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(newPrioritySetting());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_notifications_channel, 0, R.string.NotificationChannelMore));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        int descRes, includeFlag;
        switch (scope.getConstructor()) {
          case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
            descRes = R.string.NotificationsPrivateInfo;
            includeFlag = Settings.NOTIFICATION_FLAG_INCLUDE_PRIVATE;
            break;
          case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
            descRes = R.string.NotificationsGroupInfo;
            includeFlag = Settings.NOTIFICATION_FLAG_INCLUDE_GROUPS;
            break;
          case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
            descRes = R.string.NotificationsChannelInfo;
            includeFlag = Settings.NOTIFICATION_FLAG_INCLUDE_CHANNELS;
            break;
          default:
            throw new RuntimeException();
        }
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, descRes));

        switch (scope.getConstructor()) {
          case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR: {
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideSecretChats, 0, R.string.ShowSecret));
            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            items.add(secretInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Settings.instance().needHideSecretChats() ? R.string.ShowSecretOff : R.string.ShowSecretOn));
            break;
          }
          case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR: {
            // items.add(new SettingItem(SettingItem.TYPE_HEADER, 0, 0, R.string.Prioritize));
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_events_mentions, 0, R.string.Mentions).setData(tdlib.notifications().scopeGroup()));
            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            items.add(mentionsInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, !tdlib.notifications().defaultDisableMentions(tdlib.notifications().scopeGroup()) ? R.string.MentionsOff : R.string.MentionsOn));

            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_events_pinnedMessages, 0, R.string.PinnedMessages).setData(tdlib.notifications().scopeGroup()));
            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            items.add(pinnedMessagesInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, !tdlib.notifications().defaultDisablePinnedMessages(tdlib.notifications().scopeGroup()) ? R.string.PinnedMessagesOff : R.string.PinnedMessagesOn));
            // items.add(new SettingItem(SettingItem.TYPE_HEADER, 0, 0, R.string.NotificationAdvanced));
            break;
          }
        }

        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_notifications_includeDismissed, 0, R.string.IncludeDismissed).setIntValue(includeFlag));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(dismissedHint = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Settings.instance().checkNotificationFlag(includeFlag) ? R.string.IncludeDismissedHintOff : R.string.IncludeDismissedHintOn));
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          if (inErrorMode = needErrorMode()) {
            @TdlibNotificationManager.Status int status = tdlib.notifications().getNotificationBlockStatus();

            items.add(errorButton = new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, getErrorIcon(status), getErrorText(status)).setTextColorId(R.id.theme_color_textNegative));
            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            items.add(errorHint = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, makeErrorDescription(status), false));
          }
        }

        final int notificationMode = getNotificationMode();
        if (notificationMode == NOTIFICATION_MODE_SELECTED || tdlib.context().isMultiUser()) {
          hasOptions = true;

          items.add(new ListItem(items.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, R.string.NotificationsMode));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_notificationMode_all, 0, R.string.NotificationsModeAll, R.id.btn_notificationMode, notificationMode == NOTIFICATION_MODE_ALL));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_notificationMode_active, 0, R.string.NotificationsModeActive, R.id.btn_notificationMode, notificationMode == NOTIFICATION_MODE_ACTIVE));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_notificationMode_selected, 0, R.string.NotificationsModeSelected, R.id.btn_notificationMode, notificationMode == NOTIFICATION_MODE_SELECTED));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

          switch (notificationMode) {
            case NOTIFICATION_MODE_ALL: {
              items.add(notificationModeHint = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.NotificationsModeAllHint));
              break;
            }
            case NOTIFICATION_MODE_ACTIVE: {
              items.add(notificationModeHint = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(R.string.NotificationsModeActiveHint, tdlib.accountName()), false));
              break;
            }
            case NOTIFICATION_MODE_SELECTED: {
              items.add(notificationModeHint = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.pluralBold(R.string.NotificationsModeSelectedHint, tdlib.context().getNumberOfAccountsWithEnabledNotifications()), false));
              break;
            }
          }

          if (notificationMode == NOTIFICATION_MODE_SELECTED) {
            items.addAll(generateAccountsList());
          }
        }

        if (!items.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.NotificationSettings));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        }
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications_snooze, R.drawable.baseline_person_24, R.string.PrivateChats).setData(tdlib.notifications().scopePrivate()));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications_snooze, R.drawable.baseline_group_24, R.string.Groups).setData(tdlib.notifications().scopeGroup()));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications_snooze, R.drawable.baseline_bullhorn_24, R.string.Channels).setData(tdlib.notifications().scopeChannel()));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_inApp_chatSounds, 0, R.string.InChatSound));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InChatSoundInfo));

        // items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
        // items.add(new SettingItem(SettingItem.TYPE_SETTING, R.id.btn_resetNotifications, 0, R.string.ResetNotifications).setTextColorId(R.id.theme_color_textNegative));
        // items.add(new SettingItem(SettingItem.TYPE_DESCRIPTION, 0, 0, R.string.UndoAllCustom));
      }

      if (callChatId != 0) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.VoiceCalls));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_calls_vibrate, 0, R.string.Vibrate));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_calls_ringtone, 0, R.string.Ringtone));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      if (scope == null) {
        // items.add(new SettingItem(SettingItem.TYPE_HEADER, 0, 0, R.string.Events));

        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_events_contactJoined, 0, R.string.EventNewContact2));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.EventNewContactInfo));

        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_events_sentScheduled, 0, R.string.EventScheduledMessage));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.EventScheduledMessageDesc));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.UnknownChats));

        if (tdlib.autoArchiveAvailable() || tdlib.autoArchiveEnabled()) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_archiveMuteNonContacts, 0, R.string.ArchiveNonContacts));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ArchiveNonContactsInfo));
        }

        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_silenceNonContacts, 0, R.string.MuteNonContacts));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MuteNonContactsInfo));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BadgeCounter));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_appBadgeCountMuted, 0, R.string.BadgeCounterMuted).setIntValue(Settings.BADGE_FLAG_MUTED));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_appBadgeCountArchive, 0, R.string.BadgeCounterArchive).setIntValue(Settings.BADGE_FLAG_ARCHIVED));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_appBadgeCountMessages, 0, R.string.BadgeCounterMessages).setIntValue(Settings.BADGE_FLAG_MESSAGES));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_appBadgeCountMessagesInfo, 0, (Settings.instance().getBadgeFlags() & Settings.BADGE_FLAG_MESSAGES) != 0 ? R.string.BadgeCounterMessagesOff : R.string.BadgeCounterMessagesOn));
        /*items.add(new SettingItem(SettingItem.TYPE_SHADOW_TOP));
        items.add(new SettingItem(SettingItem.TYPE_RADIO_SETTING, R.id.btn_appBadge, 0, R.string.BadgeCounterSetting));
        items.add(new SettingItem(SettingItem.TYPE_SHADOW_BOTTOM));
        items.add(new SettingItem(SettingItem.TYPE_DESCRIPTION, 0, 0, R.string.AppBadgeHint));*/

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.NotificationAdvanced));
        boolean split = Settings.instance().needSplitNotificationCategories();
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_mergeCategories, 0, R.string.NotificationMerge));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(mergeInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, split ? R.string.NotificationMergeOn : R.string.NotificationMergeOff));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          if (!inErrorMode) {
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, 0, R.string.SystemNotificationSettings));
            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          }
        }
      }
    }

    adapter.setItems(items, hasOptions);
    recyclerView.setAdapter(adapter);

    tdlib.listeners().subscribeToSettingsUpdates(this);
    tdlib.settings().addNotificationProblemAvailabilityChangeListener(this);

    if (isCommonScreen()) {
      tdlib.listeners().addOptionsListener(this);
      tdlib.disableContactRegisteredNotifications(true);
    }
  }

  @Override
  public void onContactRegisteredNotificationsDisabled (boolean areDisabled) {
    runOnUiThreadOptional(() -> adapter.updateValuedSettingById(R.id.btn_events_contactJoined), this::isCommonScreen);
  }

  @Override
  public void onSentScheduledMessageNotificationsDisabled (boolean areDisabled) {
    runOnUiThreadOptional(() -> adapter.updateValuedSettingById(R.id.btn_events_sentScheduled), this::isCommonScreen);
  }

  @Override
  public void onNotificationGlobalSettingsChanged () {
    runOnUiThreadOptional(() -> adapter.updateValuedSettingById(R.id.btn_archiveMuteNonContacts), this::isCommonScreen);
  }

  private void checkContentPreview (boolean showPreview, int afterId, int id) {
    int i = adapter.indexOfViewById(id);
    if (showPreview) {
      if (i == -1) {
        i = adapter.indexOfViewById(afterId);
        if (i != -1) {
          adapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          adapter.getItems().add(i + 2, new ListItem(ListItem.TYPE_RADIO_SETTING, id, 0, R.string.MessageContentPreview));
          adapter.notifyItemRangeInserted(i + 1, 2);
        }
      }
    } else {
      if (i != -1) {
        adapter.removeRange(i - 1, 2);
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    final ListItem item = (ListItem) v.getTag();
    if (item == null)
      return false;
    switch (v.getId()) {
      case R.id.btn_notifications_snooze: {
        if (scope == null) {
          tdlib.ui().showMuteOptions(this, getScope(item), true);
          return true;
        }
        break;
      }
    }
    return false;
  }

  @Override
  public void onClick (View v) {
    final ListItem item = (ListItem) v.getTag();
    if (item == null)
      return;
    final int viewId = v.getId();
    if (item.getViewType() == ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR) {
      TdlibAccount selectedAccount = (TdlibAccount) item.getData();
      if (item.isSelected() && tdlib.context().getNumberOfAccountsWithEnabledNotifications() == 1) {
        context.tooltipManager()
          .builder(((SettingView) v).findCheckBox())
          .show(this, tdlib, R.drawable.baseline_error_24,
            Lang.getString(R.string.TooManySelectedAccounts)
          );
        return;
      }
      boolean toggleValue = adapter.toggleView(v);
      adapter.processToggle(v, item, toggleValue);
      tdlib.context().setForceEnableNotifications(selectedAccount.id, toggleValue);
      if (selectedAccount.id == tdlib.id()) {
        context.getDrawer().checkSettingsError(); // FIXME re-work to listeners
      }
      updateNotificationModeHint(true);
      tdlib.context().onUpdateNotifications(null, notificationAccount -> notificationAccount.id == selectedAccount.id);
      return;
    }
    switch (viewId) {
      case R.id.btn_notifications_preview: {
        TdApi.NotificationSettingsScope scope = getScope(item);
        tdlib.notifications().toggleDefaultShowPreview(scope);
        adapter.updateValuedSetting(item);
        checkContentPreview(tdlib.notifications().defaultShowPreview(scope), R.id.btn_notifications_preview, R.id.btn_notifications_contentPreview);
        break;
      }
      case R.id.btn_notifications_contentPreview: {
        TdApi.NotificationSettingsScope scope = getScope(item);
        tdlib.notifications().toggleContentPreview(scope);
        adapter.updateValuedSetting(item);
        break;
      }
      case R.id.btn_hideSecretChats: {
        boolean hide = !adapter.toggleView(v);
        boolean updated = Settings.instance().setNeedHideSecretChats(hide);
        if (secretInfo != null) {
          secretInfo.setString(hide ? R.string.ShowSecretOff : R.string.ShowSecretOn);
          adapter.updateValuedSetting(secretInfo);
        }
        if (updated) {
          TdlibManager.instance().onUpdateSecretChatNotifications();
        }
        break;
      }
      case R.id.btn_customChat_preview: {
        tdlib.notifications().toggleShowPreview(customChatId);
        adapter.updateValuedSettingById(R.id.btn_customChat_preview);
        break;
      }
      case R.id.btn_inApp_chatSounds: {
        tdlib.notifications().toggleInAppChatSoundsEnabled();
        adapter.updateValuedSettingById(R.id.btn_inApp_chatSounds);
        break;
      }
      case R.id.btn_notifications_snooze: {
        if (scope != null) {
          TdApi.ScopeNotificationSettings settings = tdlib.notifications().getScopeNotificationSettings(scope);
          if (settings != null) {
            if (settings.muteFor > 0) {
              tdlib.setScopeMuteFor(scope, 0);
            } else {
              tdlib.ui().showMuteOptions(this, scope, false);
            }
          }
        } else {
          SettingsNotificationController c = new SettingsNotificationController(context, tdlib);
          c.setArguments(new Args(getScope(item)));
          navigateTo(c);
        }
        break;
      }
      case R.id.btn_showAdvanced: {
        @TdlibNotificationManager.Status int status = tdlib.notifications().getNotificationBlockStatus();
        switch (status) {
          case TdlibNotificationManager.Status.DISABLED_APP_SYNC:
          case TdlibNotificationManager.Status.DISABLED_SYNC: {
            SyncAdapter.turnOnSync(context, tdlib, status == TdlibNotificationManager.Status.DISABLED_SYNC);
            checkInErrorMode();
            break;
          }
          case TdlibNotificationManager.Status.INTERNAL_ERROR: {
            String report = tdlib.settings().buildNotificationReport();
            if (!StringUtils.isEmpty(report)) {
              tdlib.ui().shareText(this, report);
            }
            break;
          }
          case TdlibNotificationManager.Status.FIREBASE_ERROR: {
            Throwable fullError = tdlib.context().getTokenFullError();
            String report;
            if (fullError != null) {
              report = "#firebase_error\n" + fullError.getClass().getSimpleName() + ": " + tdlib.context().getTokenError() + "\n" + Log.toString(fullError);
            } else {
              report = "#firebase_error " + tdlib.context().getTokenError();
            }
            if (!StringUtils.isEmpty(report)) {
              tdlib.ui().shareText(this, report);
            }
            break;
          }
          case TdlibNotificationManager.Status.FIREBASE_MISSING: {
            Intents.openLink("https://play.google.com/store/apps/details?id=com.google.android.gms");
            break;
          }
          default: {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
              intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
              intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
              intent.putExtra("app_package", context.getPackageName());
              intent.putExtra("app_uid", context.getApplicationInfo().uid);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
              intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
              intent.addCategory(Intent.CATEGORY_DEFAULT);
              intent.setData(Uri.parse("package:" + context.getPackageName()));
            } else {
              return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
              context().startActivity(intent);
            } catch (Throwable t) {
              Log.e("Unable to launch system activity", t);
              UI.showToast(R.string.NoAppToOpen, Toast.LENGTH_SHORT);
            }
            break;
          }
        }
        break;
      }
      /*case R.id.btn_appBadge: {
        boolean enabled = adapter.toggleView(v);
        if (Settings.instance().setNeedBadgeCounter(enabled)) {
          TdlibManager.instance().resetBadge();
        }
        break;
      }*/
      case R.id.btn_notifications_includeDismissed: {
        boolean enabled = adapter.toggleView(v);
        int flag = item.getIntValue();
        if (Settings.instance().setNotificationFlag(flag, enabled)) {
          TdApi.NotificationSettingsScope scope = getScope(item);
          TdlibManager.instance().onUpdateNotifications(scope, null);
          if (dismissedHint != null) {
            dismissedHint.setString(enabled ? R.string.IncludeDismissedHintOff : R.string.IncludeDismissedHintOn);
            adapter.updateValuedSetting(dismissedHint);
          }
        }
        break;
      }
      case R.id.btn_notificationMode_all:
      case R.id.btn_notificationMode_active:
      case R.id.btn_notificationMode_selected: {
        boolean enabled = adapter.processToggle(v);
        int oldMode = getNotificationMode();
        int newMode =
          (viewId == R.id.btn_notificationMode_active && enabled) ? NOTIFICATION_MODE_ACTIVE :
          (viewId == R.id.btn_notificationMode_selected && enabled) ? NOTIFICATION_MODE_SELECTED :
          (viewId == R.id.btn_notificationMode_all && enabled) ? NOTIFICATION_MODE_ALL : -1;
        if (updateNotificationMode(oldMode, newMode, false)) {
          TdlibManager.instance().onUpdateAllNotifications();
        }
        break;
      }
      case R.id.btn_showMore: {
        if (getNotificationMode() == NOTIFICATION_MODE_SELECTED && collapsedAccountsCount > 0) {
          int position = adapter.indexOfView(notificationModeHint);
          if (position != -1) {
            position += 2 + (displayingAccounts.size() - collapsedAccountsCount) * 2;
            List<ListItem> items = new ArrayList<>(collapsedAccountsCount * 2);
            for (int i = displayingAccounts.size() - collapsedAccountsCount; i < displayingAccounts.size(); i++) {
              TdlibAccount account = displayingAccounts.get(i);
              if (!items.isEmpty()) {
                items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
              }
              items.add(newAccountItem(account));
            }
            adapter.getItems().remove(position); // "show more" button
            adapter.getItems().addAll(position, items);
            adapter.notifyItemChanged(position);
            if (items.size() > 1) {
              adapter.notifyItemRangeInserted(position + 1, items.size() - 1);
            }
            adapter.resetCheckedItems();
            collapsedAccountsCount = 0;
          }
        }
        break;
      }
      case R.id.btn_appBadgeCountMuted:
      case R.id.btn_appBadgeCountArchive:
      case R.id.btn_appBadgeCountMessages: {
        boolean enabled = adapter.toggleView(v);
        int flag = item.getIntValue();
        int flags = Settings.instance().getBadgeFlags();
        int newFlags = BitwiseUtils.setFlag(flags, flag, enabled);
        switch (item.getIntValue()) {
          case Settings.BADGE_FLAG_MUTED:
            if (!enabled) {
              newFlags = BitwiseUtils.setFlag(newFlags, Settings.BADGE_FLAG_ARCHIVED, false);
            }
            break;
          case Settings.BADGE_FLAG_ARCHIVED:
            if (enabled) {
              newFlags = BitwiseUtils.setFlag(newFlags, Settings.BADGE_FLAG_MUTED, true);
            }
            break;
        }
        if (Settings.instance().setBadgeFlags(newFlags)) {
          TdlibManager.instance().resetBadge();
          switch (flag) {
            case Settings.BADGE_FLAG_MESSAGES: {
              int i = adapter.indexOfViewById(R.id.btn_appBadgeCountMessagesInfo);
              if (i != -1) {
                adapter.getItems().get(i).setString((Settings.instance().getBadgeFlags() & Settings.BADGE_FLAG_MESSAGES) != 0 ? R.string.BadgeCounterMessagesOff : R.string.BadgeCounterMessagesOn);
                adapter.updateValuedSettingByPosition(i);
              }
              break;
            }
            case Settings.BADGE_FLAG_MUTED: {
              adapter.updateValuedSettingById(R.id.btn_appBadgeCountArchive);
              break;
            }
            case Settings.BADGE_FLAG_ARCHIVED: {
              adapter.updateValuedSettingById(R.id.btn_appBadgeCountMuted);
              break;
            }
          }
        }
        break;
      }

      case R.id.btn_customChat_led:
      case R.id.btn_notifications_led: {
        final int currentLedColor, defaultLedColor;
        switch (viewId) {
          case R.id.btn_notifications_led:
            currentLedColor = tdlib.notifications().getDefaultLedColor(getScope(item));
            defaultLedColor = TdlibNotificationManager.LED_COLOR_DEFAULT;
            break;
          case R.id.btn_customChat_led:
            currentLedColor = tdlib.notifications().getEffectiveLedColor(customChatId);
            defaultLedColor = tdlib.notifications().getDefaultLedColor(tdlib.notifications().scope(customChatId));
            break;
          default:
            throw new RuntimeException();
        }

        boolean isCustom = viewId == R.id.btn_customChat_led;

        ListItem disabledItem = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_ledDisabled, 0, R.string.LedDisabled, viewId, currentLedColor == 0);
        if (isCustom && defaultLedColor == TdlibNotificationManager.LED_COLOR_UNSET)
          disabledItem.setString(Lang.getString(R.string.IsDefault, disabledItem.getString()));

        int suffixSize = 0;
        int prefixSize = 1;
        ListItem[] rawItems = new ListItem[TdlibNotificationManager.LED_COLORS.length + prefixSize + suffixSize];
        rawItems[0] = disabledItem;

        for (int i = 0; i < TdlibNotificationManager.LED_COLORS_IDS.length; i++) {
          int id = TdlibNotificationManager.LED_COLORS_IDS[i];
          int color = TdlibNotificationManager.LED_COLORS[i];
          int string = TdlibNotificationManager.LED_COLORS_STRINGS[i];
          ListItem colorItem = new ListItem(ListItem.TYPE_RADIO_OPTION, id, 0, string, viewId, currentLedColor == color).setRadioColorId(id);
          if (isCustom && defaultLedColor == color)
            colorItem.setString(Lang.getString(R.string.IsDefault, colorItem.getString()));
          rawItems[i + prefixSize] = colorItem;
        }

        showSettings(
          new SettingsWrapBuilder(viewId)
          .setRawItems(rawItems)
          .setSettingProcessor((settingItem, view, isUpdate) -> ((RadioView) view.getChildAt(0)).setApplyColor(true))
          .setIntDelegate((id, result) -> {
            int colorId = result.get(id);
            int resultColor = TdlibNotificationManager.LED_COLOR_UNSET;
            if (colorId != R.id.btn_ledDisabled) {
              int foundIndex = -1;
              int i = 0;
              for (int existingColorId : TdlibNotificationManager.LED_COLORS_IDS) {
                if (existingColorId == colorId) {
                  foundIndex = i;
                  resultColor = TdlibNotificationManager.LED_COLORS[i];
                  break;
                }
                i++;
              }
              if (foundIndex == -1) {
                throw new RuntimeException();
              }
            }
            if (isCustom) {
              tdlib.notifications().setCustomLedColor(customChatId, resultColor == defaultLedColor ? TdlibNotificationManager.LED_COLOR_UNSET : resultColor);
            } else {
              tdlib.notifications().setDefaultLedColor(getScope(item), resultColor);
            }
            adapter.updateValuedSettingById(viewId);
            onNotificationSettingsChanged();
          })
        );

        break;
      }

      case R.id.btn_notifications_vibrate:
      case R.id.btn_calls_vibrate:
      case R.id.btn_customChat_vibrate:
      case R.id.btn_customChat_calls_vibrate: {
        final int currentVibrateOption;
        final boolean currentSilentOnly;

        switch (v.getId()) {
          case R.id.btn_notifications_vibrate: {
            TdApi.NotificationSettingsScope scope = getScope(item);
            currentVibrateOption = tdlib.notifications().getDefaultVibrateMode(scope);
            currentSilentOnly = tdlib.notifications().getDefaultVibrateOnlyIfSilent(scope);
            break;
          }
          case R.id.btn_calls_vibrate: {
            currentVibrateOption = tdlib.notifications().getCallVibrateMode();
            currentSilentOnly = tdlib.notifications().getCallVibrateOnlyIfSilent();
            break;
          }
          case R.id.btn_customChat_vibrate: {
            currentVibrateOption = tdlib.notifications().getCustomVibrateMode(customChatId, TdlibNotificationManager.VIBRATE_MODE_DEFAULT);
            currentSilentOnly = currentVibrateOption != TdlibNotificationManager.VIBRATE_MODE_DEFAULT && tdlib.notifications().getCustomVibrateOnlyIfSilent(customChatId);
            break;
          }
          case R.id.btn_customChat_calls_vibrate: {
            currentVibrateOption = tdlib.notifications().getCustomCallVibrateModeForChat(callChatId);
            currentSilentOnly = currentVibrateOption != TdlibNotificationManager.VIBRATE_MODE_DEFAULT && tdlib.notifications().getCustomCallVibrateOnlyIfSilentForChat(callChatId);
            break;
          }
          default: {
            throw new RuntimeException();
          }
        }
        final int id = v.getId();

        ListItem[] rawItems = new ListItem[Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE ? 5 : 4];
        rawItems[0] = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_default, 0, R.string.Default, id, currentVibrateOption == TdlibNotificationManager.VIBRATE_MODE_DEFAULT);
        rawItems[1] = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_disabled, 0, Lang.getString(R.string.VibrateDisabled), id, currentVibrateOption == TdlibNotificationManager.VIBRATE_MODE_DISABLED);
        rawItems[2] = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_short, 0, R.string.DoubleShort, id, currentVibrateOption == TdlibNotificationManager.VIBRATE_MODE_SHORT);
        rawItems[3] = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_long, 0, R.string.Long, id, currentVibrateOption == TdlibNotificationManager.VIBRATE_MODE_LONG);
        if (Config.VIBRATE_ONLY_IF_SILENT_AVAILABLE) {
          rawItems[4] = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_vibrateOnlyIfSilent, 0, R.string.OnlyIfSilent, R.id.btn_vibrateOnlyIfSilent, currentSilentOnly);
        }

        showSettings(new SettingsWrapBuilder(id).setRawItems(rawItems).setIntDelegate(this).setOnSettingItemClick((view, settingId, settingItem, doneButton, settingsAdapter) -> {
          switch (settingItem.getId()) {
            case R.id.btn_short: {
              try {
                Vibrator v1 = (Vibrator) UI.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (v1 != null) {
                  if (isRingtone(id)) {
                    v1.vibrate(TdlibNotificationManager.VIBRATE_CALL_SHORT_PATTERN, 0);
                  } else {
                    v1.vibrate(TdlibNotificationManager.VIBRATE_SHORT_PATTERN, -1);
                  }
                }
              } catch (Throwable t) {
                Log.w("Cannot vibrate", t);
              }
              break;
            }
            case R.id.btn_long: {
              try {
                Vibrator v1 = (Vibrator) UI.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (v1 != null) {
                  if (isRingtone(id)) {
                    v1.vibrate(TdlibNotificationManager.VIBRATE_CALL_LONG_PATTERN, 0);
                  } else {
                    v1.vibrate(TdlibNotificationManager.VIBRATE_LONG_PATTERN, -1);
                  }
                }
              } catch (Throwable t) {
                Log.w("Cannot vibrate", t);
              }
              break;
            }
          }
        }).setDismissListener(this));
        break;
      }

      case R.id.btn_notifications_channel:
      case R.id.btn_customChat_channel: {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          String channelId = tdlib.notifications().getSystemChannelId(getScope(item), customChatId);
          if (customChatId == 0) {
            tdlib.notifications().createChannels();
          }
          Intent intent = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
          intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId);
          intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          context().startActivity(intent);
        }
        break;
      }

      case R.id.btn_notifications_priorityOrImportance:
      case R.id.btn_customChat_priorityOrImportance: {
        int currentPriorityOrImportance, defaultPriorityOrImportance;
        switch (v.getId()) {
          case R.id.btn_notifications_priorityOrImportance:
            currentPriorityOrImportance = defaultPriorityOrImportance = tdlib.notifications().getDefaultPriorityOrImportance(getScope(item));
            break;
          case R.id.btn_customChat_priorityOrImportance:
            currentPriorityOrImportance = tdlib.notifications().getCustomPriorityOrImportance(customChatId, TdlibNotificationManager.PRIORITY_OR_IMPORTANCE_UNSET);
            defaultPriorityOrImportance = tdlib.notifications().getDefaultPriorityOrImportance(tdlib.notifications().scope(customChatId));
            if (currentPriorityOrImportance == TdlibNotificationManager.PRIORITY_OR_IMPORTANCE_UNSET) {
              currentPriorityOrImportance = defaultPriorityOrImportance;
            }
            break;
          default:
            throw new IllegalStateException();
        }
        boolean hasSound, hasVibration;
        ListItem descriptionItem;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          descriptionItem = null;
          if (customChatId != 0) {
            hasSound = tdlib.notifications().isSoundEnabled(customChatId);
            hasVibration = tdlib.notifications().isVibrateModeEnabled(customChatId);
          } else {
            hasSound = tdlib.notifications().isDefaultSoundEnabled(getScope(item));
            hasVibration = tdlib.notifications().isDefaultVibrateModeEnabled(getScope(item));
          }
        } else {
          descriptionItem = new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.PriorityAboutUrgentAndLow);
          hasSound = hasVibration = true;
        }
        int baseId = v.getId();
        int[] availableList = TdlibUi.getAvailablePriorityOrImportanceList();
        ListItem[] items = new ListItem[availableList.length + (descriptionItem != null ? 1 : 0)];
        int i = 0;
        if (descriptionItem != null) {
          items[i++] = descriptionItem;
        }
        for (int priorityOrImportance : availableList) {
          int id = TdlibUi.getPriorityOrImportanceId(priorityOrImportance);
          int stringRes = TdlibUi.getPriorityOrImportanceString(priorityOrImportance, hasSound, hasVibration);
          boolean isDefault = (customChatId != 0 && priorityOrImportance == defaultPriorityOrImportance) || (customChatId == 0 && priorityOrImportance == TdlibNotificationManager.DEFAULT_PRIORITY_OR_IMPORTANCE);
          boolean isCurrent = priorityOrImportance == currentPriorityOrImportance;
          if (isDefault) {
            items[i++] = new ListItem(ListItem.TYPE_RADIO_OPTION, id, 0, Lang.getString(R.string.IsDefault, Lang.getString(stringRes)), baseId, isCurrent);
          } else {
            items[i++] = new ListItem(ListItem.TYPE_RADIO_OPTION, id, 0, stringRes, baseId, isCurrent);
          }
        }
        showSettings(v.getId(), items, this, false);
        break;
      }

      case R.id.btn_notifications_sound:
      case R.id.btn_calls_ringtone:
      case R.id.btn_customChat_sound:
      case R.id.btn_customChat_calls_ringtone: {
        final ArrayList<RingtoneItem> ringtoneItems = isRingtone(v.getId()) ? getCallRingtones() : getNotificationSounds();
        int ringtoneType;

        String uri, savedUri, originalUri;
        switch (v.getId()) {
          case R.id.btn_notifications_sound:
            ringtoneType = RingtoneManager.TYPE_NOTIFICATION;
            TdApi.NotificationSettingsScope scope = getScope(item);
            uri = tdlib.notifications().getDefaultSound(scope);
            savedUri = tdlib.notifications().getSavedDefaultSound(scope);
            originalUri = tdlib.notifications().getDefaultSoundPath(scope);
            break;
          case R.id.btn_calls_ringtone:
            ringtoneType = RingtoneManager.TYPE_RINGTONE;
            uri = savedUri = tdlib.notifications().getCallRingtone();
            originalUri = tdlib.notifications().getCallRingtonePath();
            break;
          case R.id.btn_customChat_sound:
            ringtoneType = RingtoneManager.TYPE_NOTIFICATION;
            uri = tdlib.notifications().getCustomSound(customChatId, null);
            savedUri = tdlib.notifications().getSavedCustomSound(customChatId, null);
            originalUri = tdlib.notifications().getCustomSoundPath(customChatId);
            break;
          case R.id.btn_customChat_calls_ringtone:
            ringtoneType = RingtoneManager.TYPE_RINGTONE;
            uri = savedUri = tdlib.notifications().getCustomCallRingtone(callChatId);
            originalUri = tdlib.notifications().getCustomCallRingtonePath(callChatId);
            break;
          default: {
            throw new IllegalStateException("Stub");
          }
        }

        // check against user changes through system channel
        if (savedUri != null && !savedUri.isEmpty() && savedUri.equals(uri)) {
          if (originalUri != null) {
            // if it's the one user picked, use original uri to highlight selected ringtone
            uri = originalUri;
          }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          try {
            String title;
            Uri systemDefaultRingtone;
            int requestCode;
            if (ringtoneType == RingtoneManager.TYPE_RINGTONE) {
              title = Lang.getString(R.string.Ringtone);
              systemDefaultRingtone = android.provider.Settings.System.DEFAULT_RINGTONE_URI;
              requestCode = Intents.ACTIVITY_RESULT_RINGTONE;
            } else {
              title = Lang.getString(R.string.Sound);
              systemDefaultRingtone = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
              requestCode = Intents.ACTIVITY_RESULT_RINGTONE_NOTIFICATION;
            }

            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri != null ? (uri.isEmpty() ? null : Uri.parse(uri)) : systemDefaultRingtone);
            context.startActivityForResult(intent, requestCode);
            return;
          } catch (Throwable t) {
            Log.e("Couldn't start system sound picker");
          }
        }

        ListItem[] items = new ListItem[ringtoneItems.size()];
        int i = 0;
        for (RingtoneItem ringtoneItem : ringtoneItems) {
          boolean isCurrent = uri == null ? ringtoneItem.isDefault() : uri.equals(ringtoneItem.getUri().toString());
          items[i++] = new ListItem(ListItem.TYPE_RADIO_OPTION, 0, 0, ringtoneItem.isDefault() ? Lang.getString(R.string.IsDefault, ringtoneItem.getName()) : ringtoneItem.getName(), v.getId(), isCurrent).setStringKey(ringtoneItem.getUri().toString());
        }

        showSettings(new SettingsWrapBuilder(v.getId()).setRawItems(items).setStringDelegate(this).setOnSettingItemClick((view, settingId, settingItem, doneButton, settingsAdapter) -> {
          String path = settingItem.getStringCheckResult();
          if (path != null) {
            for (RingtoneItem ringtoneItem : ringtoneItems) {
              if (path.equals(ringtoneItem.getUri().toString())) {
                playRingtone(ringtoneItem);
                return;
              }
            }
          }
        }).setDismissListener(this));

        break;
      }
      case R.id.btn_events_contactJoined: {
        tdlib.setDisableContactRegisteredNotifications(!adapter.toggleView(v));
        break;
      }
      case R.id.btn_events_sentScheduled: {
        tdlib.setDisableSentScheduledMessageNotifications(!adapter.toggleView(v));
        break;
      }
      case R.id.btn_silenceNonContacts: {
        tdlib.settings().setUserPreference(TdlibSettingsManager.PREFERENCE_MUTE_NON_CONTACTS, adapter.toggleView(v));
        break;
      }
      case R.id.btn_archiveMuteNonContacts: {
        tdlib.setAutoArchiveEnabled(adapter.toggleView(v));
        break;
      }
      case R.id.btn_events_pinnedMessages: {
        boolean disabled = !adapter.toggleView(v);
        tdlib.notifications().setDefaultDisablePinnedMessages(tdlib.notifications().scopeGroup(), disabled);
        if (pinnedMessagesInfo != null) {
          pinnedMessagesInfo.setString(!disabled ? R.string.PinnedMessagesOff : R.string.PinnedMessagesOn);
          adapter.updateValuedSetting(pinnedMessagesInfo);
        }
        break;
      }
      case R.id.btn_customChat_pinnedMessages: {
        boolean disabled = !adapter.toggleView(v);
        tdlib.notifications().setDisablePinnedMessages(customChatId, disabled);
        adapter.updateValuedSettingById(v.getId());
        if (pinnedMessagesInfo != null) {
          pinnedMessagesInfo.setString(!disabled ? R.string.PinnedMessagesOff : R.string.PinnedMessagesOn);
          adapter.updateValuedSetting(pinnedMessagesInfo);
        }
        break;
      }
      case R.id.btn_events_mentions: {
        boolean disabled = !adapter.toggleView(v);
        tdlib.notifications().setDefaultDisableMentions(getScope(item, tdlib.notifications().scopeGroup()), disabled);
        if (mentionsInfo != null) {
          mentionsInfo.setString(!disabled ? R.string.MentionsOff : R.string.MentionsOn);
          adapter.updateValuedSetting(mentionsInfo);
        }
        break;
      }
      case R.id.btn_customChat_mentions: {
        boolean disabled = !adapter.toggleView(v);
        tdlib.notifications().setMentionsDisabled(customChatId, disabled);
        adapter.updateValuedSettingById(v.getId());
        if (mentionsInfo != null) {
          mentionsInfo.setString(!disabled ? R.string.MentionsOff : R.string.MentionsOn);
          adapter.updateValuedSetting(mentionsInfo);
        }
        break;
      }
      case R.id.btn_mergeCategories: {
        boolean enabled = adapter.toggleView(v);
        boolean updated = Settings.instance().setNeedSplitNotificationCategories(!enabled);
        if (mergeInfo != null) {
          mergeInfo.setString(enabled ? R.string.NotificationMergeOff : R.string.NotificationMergeOn);
          adapter.updateValuedSetting(mergeInfo);
        }
        if (updated) {
          TdlibManager.instance().onUpdateAllNotifications();
        }
        break;
      }
      case R.id.btn_repeatNotifications: {
        final ArrayList<SimpleStringItem> items = new ArrayList<>();
        items.add(new SimpleStringItem(R.id.btn_disabled, R.string.RepeatNotificationsDisabled));
        items.add(new SimpleStringItem(R.id.btn_5minutes, Lang.plural(R.string.xMinutes, 5)).setArg1(5));
        items.add(new SimpleStringItem(R.id.btn_10minutes, Lang.plural(R.string.xMinutes, 10)).setArg1(10));
        items.add(new SimpleStringItem(R.id.btn_15minutes, Lang.plural(R.string.xMinutes, 15)).setArg1(15));
        items.add(new SimpleStringItem(R.id.btn_30minutes, Lang.plural(R.string.xMinutes, 30)).setArg1(30));
        items.add(new SimpleStringItem(R.id.btn_1hour, Lang.plural(R.string.xHours, 1)).setArg1(60));
        items.add(new SimpleStringItem(R.id.btn_2hours, Lang.plural(R.string.xHours, 2)).setArg1(180));
        items.add(new SimpleStringItem(R.id.btn_4hours, Lang.plural(R.string.xHours, 4)).setArg1(360));

        int foundIndex = 0;
        int index = 0;
        for (SimpleStringItem stringItem : items) {
          if (stringItem.getArg1() == tdlib.notifications().getRepeatNotificationMinutes()) {
            foundIndex = index;
            break;
          }
          index++;
        }

        final InfiniteRecyclerView<SimpleStringItem> infiniteView = new InfiniteRecyclerView<>(context(), true);
        infiniteView.addThemeListeners(this);
        infiniteView.initWithItems(items, foundIndex);

        AlertDialog.Builder b = new AlertDialog.Builder(context(), Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.RepeatNotifications));
        b.setPositiveButton(Lang.getOK(), (dialog, which) -> {
          SimpleStringItem currentItem = infiniteView.getCurrentItem();
          if (currentItem != null && tdlib.notifications().setRepeatNotificationMinuted((int) currentItem.getArg1())) {
            adapter.updateValuedSettingById(R.id.btn_repeatNotifications);
          }
        });
        b.setView(infiniteView);
        showAlert(b);

        break;
      }
      case R.id.btn_resetNotifications: {
        showResetNotificationsConfirm();
        break;
      }
    }
  }

  private static boolean isRingtone (int id) {
    return id == R.id.btn_calls_ringtone || id == R.id.btn_calls_vibrate || id == R.id.btn_customChat_calls_ringtone || id == R.id.btn_customChat_calls_vibrate;
  }

  private void showResetNotificationsConfirm () {
    showOptions(Lang.getString(R.string.ResetNotificationsConfirm), new int[] {R.id.btn_resetNotifications, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ResetNotifications), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_resetNotifications) {
        resetNotificationSettings();
      }
      return true;
    });
  }

  private void resetNotificationSettings () {
    tdlib.notifications().resetNotificationSettings(false);
    tdlib.setDisableContactRegisteredNotifications(false);
    tdlib.client().send(new TdApi.ResetAllNotificationSettings(), tdlib.okHandler());
    int oldMode = getNotificationMode();
    boolean update = Settings.instance().resetNotificationFlags();
    if (update) {
      int newMode = getNotificationMode();
      if (oldMode != newMode && updateNotificationMode(oldMode, newMode, true)) {
        TdlibManager.instance().onUpdateAllNotifications();
      }
    }
    adapter.updateAllValuedSettings();
  }

  private boolean oneShot;

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && adapter != null) {
      makeChannelChecks();
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (oneShot && adapter != null) {
        makeChannelChecks();
      } else {
        oneShot = true;
      }
    }
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK && (requestCode == Intents.ACTIVITY_RESULT_RINGTONE || requestCode == Intents.ACTIVITY_RESULT_RINGTONE_NOTIFICATION)) {
      final Uri originalUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

      String ringtoneUri;
      String name;
      if (originalUri == null) {
        ringtoneUri = ""; // disabled
        name = null;
      } else {
        name = U.getRingtoneName(originalUri, null);
        String forcedFileName;
        if (customChatId != 0) {
          forcedFileName = null;
        } else if (requestCode == Intents.ACTIVITY_RESULT_RINGTONE) {
          forcedFileName = tdlib.id() + "_ringtone.ogg";
        } else {
          switch (scope.getConstructor()) {
            case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
              forcedFileName = tdlib.id() + "_private.ogg";
              break;
            case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
              forcedFileName = tdlib.id() + "_group.ogg";
              break;
            case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
              forcedFileName = tdlib.id() + "_channel.ogg";
              break;
            default:
              throw new UnsupportedOperationException(scope.toString());
          }
        }
        Uri uri = TdlibNotificationManager.fixSoundUri(originalUri, true, forcedFileName);
        ringtoneUri = uri != null ? uri.toString() : null;
        if (name == null && uri != null) {
          name = uri.getLastPathSegment();
        } else if (StringUtils.isEmpty(ringtoneUri)) {
          name = null;
        }
      }
      int viewId;
      switch (requestCode) {
        case Intents.ACTIVITY_RESULT_RINGTONE:
          viewId = customChatId != 0 ? R.id.btn_customChat_calls_ringtone : R.id.btn_calls_ringtone;
          break;
        case Intents.ACTIVITY_RESULT_RINGTONE_NOTIFICATION:
          viewId = customChatId != 0 ? R.id.btn_customChat_sound : R.id.btn_notifications_sound;
          break;
          default:
            throw new RuntimeException();
      }
      setRingtone(viewId, ringtoneUri, name, !StringUtils.isEmpty(ringtoneUri) && originalUri != null && !originalUri.toString().equals(ringtoneUri) ? originalUri.toString() : null);
    }
  }

  @TargetApi(Build.VERSION_CODES.O)
  private void makeChannelChecks () {
    adapter.updateAllValuedSettings(); // TODO optimize
    onNotificationSettingsChanged();
  }

  private void onNotificationSettingsChanged () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (customChatId != 0) {
        boolean hasCustomSettings = tdlib.notifications().hasCustomChatSettings(customChatId);
        if (hasCustomSettings != this.customHasMore) {
          this.customHasMore = hasCustomSettings;
          if (hasCustomSettings) {
            int i;
            i = adapter.indexOfViewById(ChatId.isSecret(customChatId) ? R.id.btn_customChat_led : R.id.btn_customChat_preview);
            if (i == -1) {
              throw new IllegalStateException();
            }
            adapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            adapter.getItems().add(i + 2, new ListItem(ListItem.TYPE_SETTING, R.id.btn_customChat_channel, 0, R.string.NotificationChannelMore));
            adapter.notifyItemRangeInserted(i + 1, 2);
          } else {
            int i = adapter.indexOfViewById(R.id.btn_customChat_channel);
            if (i == -1) {
              throw new IllegalStateException();
            }
            adapter.removeRange(i - 1, 2);
          }
        }
      } else {
        checkPrioritySetting();
      }
      checkVibrateAndSoundSettings();
      checkInErrorMode();
      checkSnoozeStyle();
    }
  }

  private long lastPlayTime;
  private HashMap<String, Ringtone> ringtoneMap;

  private void playRingtone (RingtoneItem item) {
    if (item.getUri().equals(Uri.EMPTY)) {
      return;
    }

    if (System.currentTimeMillis() - lastPlayTime <= 100) {
      return;
    }
    lastPlayTime = System.currentTimeMillis();

    Ringtone ringtone = null;
    if (ringtoneMap == null) {
      ringtoneMap = new HashMap<>();
    } else {
      ringtone = ringtoneMap.get(item.getUri().toString());
    }

    try {
      if (ringtone == null) {
        ringtone = RingtoneManager.getRingtone(context(), item.getUri());
        if (ringtone != null) {
          ringtoneMap.put(item.getUri().toString(), ringtone);
        }
      } else {
        ringtone.stop();
      }
      stopSounds();
    } catch (Throwable t) {
      Log.w(t);
    }
    if (ringtone != null) {
      try {
        ringtone.play();
      } catch (Throwable t) {
        Log.w(t);
      }
    }
  }

  @Override
  public void onPopupDismiss (PopupLayout popup) {
    try {
      Vibrator v = (Vibrator) UI.getContext().getSystemService(Context.VIBRATOR_SERVICE);
      if (v != null) {
        v.cancel();
      }
    } catch (Throwable t) {
      Log.w("Cannot vibrate", t);
    }
    stopSounds();
  }

  private void stopSounds () {
    if (ringtoneMap != null) {
      for (Ringtone ringtoneSound : ringtoneMap.values()) {
        try { ringtoneSound.stop(); } catch (Throwable ignored) { }
      }
    }
  }

  private boolean isCommonScreen () {
    return scope == null && customChatId == 0;
  }

  @Override
  public void destroy () {
    super.destroy();
    stopSounds();
    tdlib.listeners().unsubscribeFromSettingsUpdates(this);
    tdlib.settings().removeNotificationProblemAvailabilityChangeListener(this);
    if (isCommonScreen()) {
      tdlib.listeners().removeOptionListener(this);
    }
  }

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    invalidateNotificationSettings(scope, false);
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    invalidateNotificationSettings(chatId, false);
  }

  private void invalidateNotificationSettings (TdApi.NotificationSettingsScope scope, boolean channelChanged) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        TdApi.NotificationSettingsScope currentScope = this.scope != null ? this.scope : this.customChatId != 0 ? tdlib.notifications().scope(customChatId) : null;
        if (currentScope == null) {
          adapter.updateAllValuedSettingsById(R.id.btn_notifications_snooze);
        } else if (currentScope.getConstructor() == scope.getConstructor()) {
          if (customChatId != 0) {
            invalidateNotificationSettings(customChatId, channelChanged);
          } else {
            checkSnoozeStyle();
            if (channelChanged) {
              makeChannelChecks();
            } else {
              adapter.updateValuedSettingById(R.id.btn_notifications_preview);
              checkContentPreview(tdlib.notifications().defaultShowPreview(scope), R.id.btn_notifications_preview, R.id.btn_notifications_contentPreview);
            }
          }
        }
      }
    });
  }

  private void invalidateNotificationSettings (long chatId, boolean channelChanged) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        if (chatId != 0 && customChatId == chatId) {
          if (channelChanged) {
            makeChannelChecks();
          } else {
            adapter.updateValuedSettingById(R.id.btn_customChat_preview);
          }
        }
      }
    });
  }

  @Override
  public void onNotificationChannelChanged (TdApi.NotificationSettingsScope scope) {
    invalidateNotificationSettings(scope, true);
  }

  @Override
  public void onNotificationChannelChanged (long chatId) {
    invalidateNotificationSettings(chatId, true);
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseArrayCompat<String> result) {
    switch (id) {
      case R.id.btn_notifications_sound:
      case R.id.btn_calls_ringtone:
      case R.id.btn_customChat_sound:
      case R.id.btn_customChat_calls_ringtone: {
        if (result.size() == 1) {
          ArrayList<RingtoneItem> ringtoneItems = isRingtone(id) ? getCallRingtones() : getNotificationSounds();
          final String ringtonePath = result.valueAt(0);
          for (RingtoneItem item : ringtoneItems) {
            if (ringtonePath.equals(item.getUri().toString())) {
              boolean isDefault = item.isDefault();
              String ringtone = isDefault ? null : item.getUri().toString();
              String name = isDefault ? null : item.getName();
              setRingtone(id, ringtone, name, null);
              break;
            }
          }
        }
        break;
      }
    }
  }

  private void setRingtone (int id, @Nullable String ringtone, @Nullable String name, @Nullable String path) {
    boolean ok;
    switch (id) {
      case R.id.btn_notifications_sound:
        ok = tdlib.notifications().setDefaultSound(scope, ringtone, name, path);
        break;
      case R.id.btn_calls_ringtone:
        ok = tdlib.notifications().setCallRingtone(ringtone, name, path);
        break;
      case R.id.btn_customChat_sound:
        tdlib.notifications().setCustomSound(customChatId, ringtone, name, path);
        ok = true;
        break;
      case R.id.btn_customChat_calls_ringtone:
        tdlib.notifications().setCustomCallRingtone(callChatId, ringtone, name, path);
        ok = true;
        break;
      default:
        throw new IllegalStateException("Stub");
    }
    if (ok) {
      adapter.updateValuedSettingById(id);
      onNotificationSettingsChanged();
    }
  }

  private static int convertIdToPriorityOrImportance (@IdRes int id) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      switch (id) {
        case R.id.btn_importanceHigh:
          return NotificationManager.IMPORTANCE_HIGH;
        case R.id.btn_importanceDefault:
          return NotificationManager.IMPORTANCE_DEFAULT;
        case R.id.btn_importanceLow:
          return NotificationManager.IMPORTANCE_LOW;
        case R.id.btn_importanceMin:
          return NotificationManager.IMPORTANCE_MIN;
      }
    } else {
      switch (id) {
        case R.id.btn_priorityLow:
          return Notification.PRIORITY_LOW;
        case R.id.btn_priorityMax:
          return Notification.PRIORITY_MAX;
        case R.id.btn_priorityHigh:
          return Notification.PRIORITY_HIGH;
      }
    }
    throw new IllegalArgumentException("id == " + Lang.getResourceEntryName(id));
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseIntArray result) {
    switch (id) {
      case R.id.btn_notifications_priorityOrImportance:
      case R.id.btn_customChat_priorityOrImportance: {
        boolean ok;
        int resultId = result.get(id);
        if (resultId == 0)
          break;
        int priorityOrImportance = convertIdToPriorityOrImportance(resultId);
        switch (id) {
          case R.id.btn_notifications_priorityOrImportance:
            ok = tdlib.notifications().setDefaultPriorityOrImportance(scope, priorityOrImportance);
            break;
          case R.id.btn_customChat_priorityOrImportance: {
            int defaultPriorityOrImportance = tdlib.notifications().getDefaultPriorityOrImportance(tdlib.notifications().scope(customChatId));
            tdlib.notifications().setCustomPriorityOrImportance(customChatId, priorityOrImportance == defaultPriorityOrImportance ? TdlibNotificationManager.PRIORITY_OR_IMPORTANCE_UNSET : priorityOrImportance);
            ok = true;
            break;
          }
          default:
            throw new IllegalStateException();
        }
        if (ok) {
          adapter.updateValuedSettingById(id);
          onNotificationSettingsChanged();
        }
        break;
      }

      case R.id.btn_notifications_vibrate:
      case R.id.btn_customChat_vibrate:
      case R.id.btn_calls_vibrate:
      case R.id.btn_customChat_calls_vibrate: {
        boolean vibrateOnlyIfSilent = false;
        if (result.get(R.id.btn_vibrateOnlyIfSilent) != 0) {
          result.delete(R.id.btn_vibrateOnlyIfSilent);
          vibrateOnlyIfSilent = true;
        }
        if (result.size() == 1) {
          final int vibrateType;
          switch (result.valueAt(0)) {
            case R.id.btn_long: vibrateType = TdlibNotificationManager.VIBRATE_MODE_LONG; break;
            case R.id.btn_short: vibrateType = TdlibNotificationManager.VIBRATE_MODE_SHORT; break;
            case R.id.btn_disabled: vibrateType = TdlibNotificationManager.VIBRATE_MODE_DISABLED; break;
            case R.id.btn_default: default: vibrateType = TdlibNotificationManager.VIBRATE_MODE_DEFAULT; break;
          }
          boolean ok;
          switch (id) {
            case R.id.btn_notifications_vibrate:
              ok = tdlib.notifications().setDefaultVibrateMode(scope, vibrateType, vibrateOnlyIfSilent);
              break;
            case R.id.btn_calls_vibrate:
              ok = tdlib.notifications().setCallVibrate(vibrateType, vibrateOnlyIfSilent);
              break;
            case R.id.btn_customChat_vibrate:
              tdlib.notifications().setCustomVibrateMode(customChatId, vibrateType, vibrateOnlyIfSilent);
              ok = true;
              break;
            case R.id.btn_customChat_calls_vibrate:
              tdlib.notifications().setCustomCallVibrate(callChatId, vibrateType, vibrateOnlyIfSilent);
              ok = true;
              break;
            default:
              throw new IllegalStateException();
          }
          if (ok) {
            adapter.updateValuedSettingById(id);
            onNotificationSettingsChanged();
          }
        }
        break;
      }
    }
  }

  @Override
  public void onNotificationProblemsAvailabilityChanged (Tdlib tdlib, boolean available) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        checkInErrorMode();
      }
    });
  }
}
