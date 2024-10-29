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
 * File created on 16/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.AvatarPickerManager;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexRecyclerView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ConnectionListener;
import org.thunderdog.challegram.telegram.GlobalTokenStateListener;
import org.thunderdog.challegram.telegram.SessionListener;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.AppBuildInfo;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.PullRequest;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AppInstallationUtil;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.reference.ReferenceList;
import tgx.td.ChatId;
import tgx.td.Td;

public class SettingsController extends ViewController<Void> implements
  View.OnClickListener, ComplexHeaderView.Callback,
  Menu, MoreDelegate, OptionDelegate,
  TdlibCache.MyUserDataChangeListener, ConnectionListener, StickersListener, MediaLayout.MediaGalleryCallback,
  ActivityResultHandler, View.OnLongClickListener, SessionListener, GlobalTokenStateListener, TdlibCache.UserDataChangeListener,
  TdlibOptionListener {

  private final AvatarPickerManager avatarPickerManager;
  private ComplexHeaderView headerCell;
  private ComplexRecyclerView contentView;
  private SettingsAdapter adapter;

  public SettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    avatarPickerManager = new AvatarPickerManager(this);
  }

  @Override
  public int getId () {
    return R.id.controller_settings;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    contentView.setFactorLocked(false);
    stickerSetsPreloader.preloadStickers();
    emojiPacksPreloader.preloadStickers();
    if (!oneShot) {
      oneShot = true;
      tdlib.listeners().subscribeToStickerUpdates(this);
    }
  }

  private CancellableRunnable supportOpen;

  private void cancelSupportOpen () {
    if (supportOpen != null) {
      supportOpen.cancel();
      supportOpen = null;
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    contentView.setFactorLocked(true);
    cancelSupportOpen();
  }

  @Override
  protected int getHeaderIconColorId () {
    return headerCell != null && !headerCell.isCollapsed() ? ColorId.white : ColorId.headerIcon;
  }

  @Override
  protected int getHeaderHeight () {
    return (int) (Size.getHeaderPortraitSize() + Size.getHeaderSizeDifference(true) * contentView.getScrollFactor());
  }

  @Override
  protected int getMaximumHeaderHeight () {
    return Size.getHeaderBigPortraitSize(true);
  }

  @Override
  protected int getFloatingButtonId () {
    return R.drawable.baseline_edit_24;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more_settings;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more_settings) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      showMore(new int[] {R.id.more_btn_logout}, new String[] {Lang.getString(R.string.LogOut)}, 0);
    }
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    return tdlib.ui().handleProfileOption(this, id, tdlib.myUser());
  }

  @Override
  protected void onFloatingButtonPressed () {
    EditNameController editNameController = new EditNameController(context, tdlib);
    editNameController.setMode(EditNameController.Mode.RENAME_SELF);
    navigateTo(editNameController);
  }

  // https://bitbucket.org/challegram/challegram/issues/416
  private float textSize;
  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (adapter != null) {
      float textSize = Settings.instance().getChatFontSize();
      if (this.textSize != 0 && this.textSize != textSize) {
        int i = adapter.indexOfViewById(R.id.btn_bio);
        if (i != -1) {
          View view = contentView.getLayoutManager().findViewByPosition(i);
          if (view != null) {
            view.requestLayout();
          } else {
            adapter.notifyItemChanged(i);
          }
        }
      }
      this.textSize = textSize;
    }
    checkErrors(true);
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.menu_btn_more) {
      tdlib.ui().logOut(this, true);
    } else {
      tdlib.ui().handleProfileMore(this, id, tdlib.myUser(), null);
    }
  }

  @Override
  protected void attachNavigationController (NavigationController navigationController) {
    super.attachNavigationController(navigationController);
    contentView.setFloatingButton(navigationController.getFloatingButton());
  }

  @Override
  protected void detachNavigationController () {
    super.detachNavigationController();
    contentView.setFloatingButton(null);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  private void changeProfilePhoto () {
    avatarPickerManager.showMenuForProfile(headerCell, false);
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    avatarPickerManager.handleActivityResult(requestCode, resultCode, data, AvatarPickerManager.MODE_PROFILE, null, null);
  }

  private boolean hasNotificationError;
  private int notificationErrorDescriptionRes;
  private long problematicChatId;

  private int getNotificationErrorDescription () {
    this.problematicChatId = 0;
    @TdlibNotificationManager.Status int status = tdlib.notifications().getNotificationBlockStatus();
    switch (status) {
      case TdlibNotificationManager.Status.BLOCKED_ALL:
        return R.string.NotificationsErrorBlocked;
      case TdlibNotificationManager.Status.MISSING_PERMISSION:
        return R.string.NotificationsErrorPermission;
      case TdlibNotificationManager.Status.BLOCKED_CATEGORY:
        return R.string.NotificationsErrorBlockedCategory;
      case TdlibNotificationManager.Status.DISABLED_SYNC:
      case TdlibNotificationManager.Status.DISABLED_APP_SYNC:
      case TdlibNotificationManager.Status.FIREBASE_MISSING:
      case TdlibNotificationManager.Status.FIREBASE_ERROR:
        return R.string.NotificationsErrorBackground;
      case TdlibNotificationManager.Status.INTERNAL_ERROR: {
        this.problematicChatId = tdlib.settings().getLastNotificationProblematicChat();
        if (problematicChatId != 0) {
          TdApi.Chat chat = tdlib.chatSync(problematicChatId);
          if (chat != null) {
            return R.string.NotificationsErrorErrorChat;
          }
        }
        return R.string.NotificationsErrorError;
      }
      case TdlibNotificationManager.Status.ACCOUNT_NOT_SELECTED:
        return R.string.NotificationsErrorUnselected;
      case TdlibNotificationManager.Status.NOT_BLOCKED:
        break;
    }

    boolean blockedPrivate = tdlib.notifications().areNotificationsBlocked(tdlib.notifications().scopePrivate());
    boolean blockedGroup = tdlib.notifications().areNotificationsBlocked(tdlib.notifications().scopeGroup());
    boolean blockedChannel = tdlib.notifications().areNotificationsBlocked(tdlib.notifications().scopeChannel());

    int scopeCount = 0;
    if (blockedPrivate)
      scopeCount++;
    if (blockedGroup)
      scopeCount++;
    if (blockedChannel)
      scopeCount++;

    if (scopeCount == 1) {
      if (blockedPrivate) {
        return R.string.NotificationsErrorBlockedPrivate;
      } else if (blockedGroup) {
        return R.string.NotificationsErrorBlockedGroup;
      } else if (blockedChannel) {
        return R.string.NotificationsErrorBlockedChannel;
      }
      throw new RuntimeException();
    } else if (scopeCount > 1) {
      return R.string.NotificationsErrorBlockedMixed;
    }

    return 0;
  }

  private void checkErrors (boolean updateList) {
    long oldProblematicChatId = this.problematicChatId;
    boolean hasNotificationError = tdlib.notifications().hasLocalNotificationProblem();
    int notificationErrorDescriptionRes = hasNotificationError ? getNotificationErrorDescription() : 0;
    boolean hadDescription = this.notificationErrorDescriptionRes != 0;
    boolean hasDescription = notificationErrorDescriptionRes != 0;

    if (this.hasNotificationError != hasNotificationError || (hasNotificationError && (this.notificationErrorDescriptionRes != notificationErrorDescriptionRes || this.problematicChatId != oldProblematicChatId))) {
      this.hasNotificationError = hasNotificationError;
      this.notificationErrorDescriptionRes = notificationErrorDescriptionRes;
      if (updateList && adapter != null) {
        int position = adapter.indexOfViewById(R.id.btn_notificationSettings);
        if (position != -1) {
          final ListItem item = adapter.getItems().get(position);
          if (hadDescription != hasDescription) {
            item.setViewType(hasDescription ? ListItem.TYPE_VALUED_SETTING_COMPACT : ListItem.TYPE_SETTING);
            adapter.notifyItemChanged(position);
          } else {
            adapter.updateValuedSettingByPosition(position);
          }
        }
      }
    } else {
      this.notificationErrorDescriptionRes = notificationErrorDescriptionRes;
    }
  }

  @Override
  public void onTokenStateChanged (int newState, @Nullable String error, @Nullable Throwable fullError) {
    runOnUiThreadOptional(() -> {
      checkErrors(true);
    });
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    checkErrors(true);
  }

  private void updateButtonsColor () {
    if (headerView != null) {
      headerView.getBackButton().setColor(ColorUtils.fromToArgb(Theme.headerBackColor(), Color.WHITE, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f));
      headerView.updateButtonsTransform(getMenuId(), this, getTransformFactor());
    }
  }

  @Override
  protected void applyHeaderMenuTransform (LinearLayout menu, float factor) {
    super.applyHeaderMenuTransform(menu, factor);
    for (int i = 0; i < menu.getChildCount(); i++ ){
      View v = menu.getChildAt(i);
      if (v instanceof HeaderButton) {
        ((HeaderButton) v).setThemeColorId(ColorId.headerIcon, ColorId.white, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f);
      }
    }
  }

  private AppBuildInfo previousBuildInfo;

  @Override
  protected View onCreateView (Context context) {
    this.headerCell = new ComplexHeaderView(context, tdlib, this);
    this.headerCell.setAvatarExpandListener((headerView1, expandFactor, byCollapse, allowanceFactor, collapseFactor) -> updateButtonsColor());
    this.headerCell.setAllowEmptyClick();
    this.headerCell.initWithController(this, true);
    this.headerCell.setInnerMargins(Screen.dp(56f), Screen.dp(49f));
    this.headerCell.setPhotoOpenCallback(this);
    this.headerCell.setOnEmojiStatusClickListener(v -> {
      EmojiStatusSelectorEmojiPage.Wrapper c = new EmojiStatusSelectorEmojiPage.Wrapper(context, tdlib, SettingsController.this, new EmojiStatusSelectorEmojiPage.AnimationsEmojiStatusSetDelegate() {
        @Override
        public void onAnimationStart () {
          headerCell.setIgnoreDrawEmojiStatus(true);
        }

        @Override
        public void onAnimationEnd () {
          headerCell.setIgnoreDrawEmojiStatus(false);
        }

        @Override
        public int getDestX () {
          return headerCell.getEmojiStatusLastDrawX() + Screen.dp(12);
        }

        @Override
        public int getDestY () {
          return headerCell.getEmojiStatusLastDrawY() + Screen.dp(12);
        }
      });
      c.show();
    });
    updateHeader();

    initMyUser();

    this.contentView = new ComplexRecyclerView(context, this);
    this.contentView.setHasFixedSize(true);
    this.contentView.setHeaderView(headerCell, this);
    this.contentView.setItemAnimator(null);
    ViewSupport.setThemedBackground(contentView, ColorId.background, this);
    this.contentView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    this.contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    this.adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        boolean hasError = false;
        final int itemId = item.getId();
        if (itemId == R.id.btn_notificationSettings) {
          checkErrors(false);
          hasError = hasNotificationError;
        } else if (itemId == R.id.btn_devices) {
          hasError = sessions != null && sessions.incompleteLoginAttempts.length > 0;
        }
        view.setUnreadCounter(hasError ? Tdlib.CHAT_FAILED : 0, false, isUpdate);
        if (itemId == R.id.btn_sourceCode) {
          PullRequest specificPullRequest = (PullRequest) item.getData();
          CharSequence buildInfoShort;
          if (specificPullRequest != null) {
            buildInfoShort = Lang.getString(R.string.CommitInfo,
              (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 0 ? Lang.newCodeSpan(needFakeBold) : null,
              specificPullRequest.getCommit(),
              Lang.getUTCTimestamp(specificPullRequest.getCommitDate(), TimeUnit.SECONDS)
            );
          } else {
            buildInfoShort = Lang.getString(R.string.CommitInfo,
              (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 0 ? Lang.newCodeSpan(needFakeBold) : null,
              BuildConfig.COMMIT,
              Lang.getUTCTimestamp(BuildConfig.COMMIT_DATE, TimeUnit.SECONDS)
            );
            if (BuildConfig.PULL_REQUEST_ID.length > 0) {
              SpannableStringBuilder b = new SpannableStringBuilder(buildInfoShort).append(" + ");
              if (BuildConfig.PULL_REQUEST_ID.length > 1) {
                b.append(Lang.plural(R.string.xPRs, BuildConfig.PULL_REQUEST_ID.length));
              } else {
                b.append(Lang.getString(R.string.PR, BuildConfig.PULL_REQUEST_ID[0]));
              }
              buildInfoShort = b;
            }
          }
          view.setData(buildInfoShort);
        } else if (itemId == R.id.btn_tdlib) {
          view.setData(TdlibUi.getTdlibVersionSignature());
        } else if (itemId == R.id.btn_sourceCodeChanges) {
          String previousVersionName = previousBuildInfo.getVersionName();
          int index = previousVersionName.indexOf('-');
          if (index != -1) {
            previousVersionName = previousVersionName.substring(0, index);
          }
          int dotCount = 0;
          for (int i = 0; i < previousVersionName.length(); i++) {
            if (previousVersionName.charAt(i) == '.') {
              dotCount++;
            }
          }
          if (dotCount == 2) { // Missing build no: 0.24.9
            previousVersionName = previousVersionName + "." + previousBuildInfo.getVersionCode();
          }
          view.setData(Lang.getString(R.string.ViewSourceCodeChangesSince, Lang.codeCreator(), previousVersionName, previousBuildInfo.getCommit()));
        } else if (itemId == R.id.btn_copyDebug) {
          view.setData(R.string.CopyReportDataInfo);
        } else if (itemId == R.id.btn_devices) {
          if (sessions == null) {
            view.setData(R.string.LoadingInformation);
          } else if (sessions.incompleteLoginAttempts.length > 0) {
            view.setData(Lang.pluralBold(R.string.XSignInAttempts, sessions.incompleteLoginAttempts.length));
          } else if (sessions.otherActiveSessions.length == 0) {
            view.setData(R.string.SignedInNoOtherSessions);
          } else if (sessions.otherActiveSessions.length == 1) {
            TdApi.Session otherSession = sessions.otherActiveSessions[0];
            if (sessions.otherDevicesCount == 1 && !StringUtils.isEmpty(otherSession.deviceModel)) {
              view.setData(Lang.getStringBold(R.string.SignedInOtherDevice, otherSession.deviceModel));
            } else {
              view.setData(Lang.getStringBold(R.string.SignedInOtherSession, otherSession.applicationName));
            }
          } else if (sessions.otherDevicesCount == 0) {
            view.setData(Lang.pluralBold(R.string.SignedInXOtherApps, sessions.otherActiveSessions.length));
          } else if (sessions.sessionCountOnCurrentDevice == 1) { // All sessions on other devices
            if (sessions.otherActiveSessions.length == sessions.otherDevicesCount) {
              view.setData(Lang.pluralBold(R.string.SignedInXOtherDevices, sessions.otherDevicesCount));
            } else {
              view.setData(Lang.getCharSequence(R.string.format_signedInAppsOnDevices, Lang.pluralBold(R.string.part_SignedInXApps, sessions.otherActiveSessions.length), Lang.pluralBold(R.string.part_SignedInXOtherDevices, sessions.otherDevicesCount)));
            }
          } else {
            view.setData(Lang.getCharSequence(R.string.format_signedInAppsOnDevices, Lang.pluralBold(R.string.part_SignedInXOtherApps, sessions.otherActiveSessions.length), Lang.pluralBold(R.string.part_SignedInXDevices, sessions.otherDevicesCount + 1)));
          }
        } else if (itemId == R.id.btn_notificationSettings) {
          if (notificationErrorDescriptionRes != 0) {
            if (notificationErrorDescriptionRes == R.string.NotificationsErrorErrorChat) {
              view.setData(Lang.getStringBold(notificationErrorDescriptionRes, tdlib.chatTitle(problematicChatId)));
            } else {
              view.setData(notificationErrorDescriptionRes);
            }
          }
        } else if (itemId == R.id.btn_suggestion) {
          TdApi.SuggestedAction action = (TdApi.SuggestedAction) item.getData();
          switch (action.getConstructor()) {
            case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR:
              view.setText(obtainWrapper(Lang.getStringBold(R.string.ReminderCheckPhoneNumberText, originalPhoneNumber != null ? myPhone : Strings.ELLIPSIS), action.getConstructor()));
              break;
            case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR:
              view.setText(obtainWrapper(Lang.getString(R.string.ReminderCheckTfaPasswordText), action.getConstructor()));
              break;
            case TdApi.SuggestedActionSetBirthdate.CONSTRUCTOR:
              view.setText(obtainWrapper(Lang.getString(R.string.ReminderSetBirthdateText), action.getConstructor()));
              break;
            default:
              Td.assertSuggestedAction_5c4efa90();
              throw Td.unsupported(action);
          }
        } else if (itemId == R.id.btn_birthdate) {
          if (userFull == null) {
            view.setData(R.string.LoadingInformation);
          } else if (userFull.birthdate == null) {
            view.setData(R.string.SetBirthdate);
          } else {
            view.setData(Lang.getBirthdate(userFull.birthdate, true, true));
          }
        } else if (itemId == R.id.btn_username) {
          if (myUsernames == null) {
            view.setData(R.string.LoadingUsername);
          } else if (StringUtils.isEmpty(myUsernames.editableUsername)) {
            view.setData(R.string.SetUpUsername);
          } else {
            int collectibleCount = Td.secondaryUsernamesCount(myUsernames);
            view.setData("@" + myUsernames.editableUsername + (collectibleCount != 0 ? " + " + Lang.pluralBold(R.string.xOtherUsernames, collectibleCount) : "")); // TODO multi-username support
          }
        } else if (itemId == R.id.btn_peer_id) {
          view.setData(Strings.buildCounter(tdlib.myUserId(true)));
        } else if (itemId == R.id.btn_phone) {
          view.setData(myPhone);
        } else if (itemId == R.id.btn_bio) {
          TdApi.FormattedText text;
          if (about == null) {
            text = TD.toFormattedText(Lang.getString(R.string.LoadingInformation), false);
          } else {
            TdApi.FormattedText about = SettingsController.this.about;
            if (Td.isEmpty(about)) {
              text = TD.toFormattedText(Lang.getString(R.string.BioNone), false);
            } else {
              text = about;
            }
          }
          view.setText(obtainWrapper(text, TdApi.SetBio.CONSTRUCTOR));
        }
      }
    };
    this.adapter.setOnLongClickListener(this);

    List<ListItem> items = adapter.getItems();
    ArrayUtils.ensureCapacity(items, 27);

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));
    if (Settings.instance().showPeerIds()) {
      items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_peer_id, R.drawable.baseline_identifier_24, R.string.UserId).setContentStrings(R.string.LoadingInformation, R.string.LoadingInformation));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_username, R.drawable.baseline_alternate_email_24, R.string.Username).setContentStrings(R.string.LoadingUsername, R.string.SetUpUsername));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_phone, R.drawable.baseline_phone_24, R.string.Phone));
    if (userFull != null && userFull.birthdate != null) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(newBirthdateItem());
    }
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_bio, R.drawable.baseline_info_24, R.string.UserBio).setContentStrings(R.string.LoadingInformation, R.string.BioNone));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    TdApi.SuggestedAction[] actions = tdlib.getSuggestedActions();
    int addedActionItems = 0;
    for (TdApi.SuggestedAction action : actions) {
      if (!tdlib.isSettingSuggestion(action)) {
        continue;
      }
      items.add(new ListItem(addedActionItems == 0 ? ListItem.TYPE_SHADOW_TOP : ListItem.TYPE_SEPARATOR));
      items.add(newSuggestionItem(action));
      addedActionItems++;
    }
    if (addedActionItems > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_devices, R.drawable.baseline_devices_other_24, R.string.Devices));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));

    checkErrors(false);

    items.add(new ListItem(notificationErrorDescriptionRes != 0 ? ListItem.TYPE_VALUED_SETTING_COMPACT : ListItem.TYPE_SETTING, R.id.btn_notificationSettings, R.drawable.baseline_notifications_24, R.string.Notifications));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_chatSettings, R.drawable.baseline_data_usage_24, R.string.DataSettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_privacySettings, R.drawable.baseline_lock_24, R.string.PrivacySettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_stickerSettingsAndEmoji, R.drawable.deproko_baseline_stickers_filled_24, R.string.StickersAndEmoji));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_themeSettings, R.drawable.baseline_palette_24, R.string.ThemeSettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tweakSettings, R.drawable.baseline_extension_24, R.string.TweakSettings));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    if (Settings.instance().chatFoldersEnabled()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_chatFolders, R.drawable.baseline_folder_copy_24, R.string.ChatFolders));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_languageSettings, R.drawable.baseline_language_24, R.string.Language));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_help, R.drawable.baseline_live_help_24, R.string.AskAQuestion));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_faq, R.drawable.baseline_help_24, R.string.TelegramFAQ));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_privacyPolicy, R.drawable.baseline_policy_24, R.string.PrivacyPolicy));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    AppInstallationUtil.DownloadUrl downloadUrl = AppUpdater.getDownloadUrl(null);
    @DrawableRes int downloadIconRes;
    @StringRes int downloadStringRes = R.string.CheckForUpdates;
    if (tdlib.hasUrgentInAppUpdate() && tdlib.isProduction()) {
      downloadIconRes = R.drawable.baseline_warning_24;
      downloadUrl = new AppInstallationUtil.DownloadUrl(downloadUrl.installerId, tdlib.tMeUrl(BuildConfig.TELEGRAM_UPDATES_CHANNEL));
    } else {
      switch (downloadUrl.installerId) {
        case AppInstallationUtil.InstallerId.UNKNOWN:
        case AppInstallationUtil.InstallerId.MEMU_EMULATOR: {
          if (!StringUtils.isEmpty(BuildConfig.GOOGLE_PLAY_URL)) {
            downloadUrl = new AppInstallationUtil.DownloadUrl(AppInstallationUtil.InstallerId.GOOGLE_PLAY, BuildConfig.GOOGLE_PLAY_URL);
            downloadIconRes = R.drawable.baseline_google_play_24;
            downloadStringRes = R.string.AppOnGooglePlay;
          } else {
            downloadIconRes = R.drawable.baseline_update_24;
          }
          break;
        }
        case AppInstallationUtil.InstallerId.GOOGLE_PLAY: {
          downloadIconRes = R.drawable.baseline_google_play_24;
          break;
        }
        case AppInstallationUtil.InstallerId.GALAXY_STORE: {
          downloadIconRes = R.drawable.baseline_galaxy_store_24;
          break;
        }
        case AppInstallationUtil.InstallerId.HUAWEI_APPGALLERY: {
          downloadIconRes = R.drawable.baseline_huawei_24;
          break;
        }
        case AppInstallationUtil.InstallerId.AMAZON_APPSTORE: {
          downloadIconRes = R.drawable.baseline_amazon_24;
          break;
        }
        default:
          throw new UnsupportedOperationException();
      }
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_checkUpdates, downloadIconRes, downloadStringRes)
      .setData(downloadUrl));
    if (downloadUrl.installerId == AppInstallationUtil.InstallerId.GOOGLE_PLAY) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_subscribeToBeta, R.drawable.templarian_baseline_flask_24, R.string.SubscribeToBeta));
    }
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sourceCode, R.drawable.baseline_github_24, R.string.ViewSourceCode));
    this.previousBuildInfo = Settings.instance().getPreviousBuildInformation();
    if (this.previousBuildInfo != null) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sourceCodeChanges, R.drawable.baseline_code_24, R.string.ViewSourceCodeChanges));
    }
    AppBuildInfo currentBuildInfo = Settings.instance().getCurrentBuildInformation();
    if (!currentBuildInfo.getPullRequests().isEmpty()) {
      for (PullRequest pullRequest : currentBuildInfo.getPullRequests()) {
        String title = Lang.getString(R.string.PullRequestCommit, pullRequest.getId());
        if (!pullRequest.getCommitAuthor().isEmpty()) {
          title = Lang.getString(R.string.format_PRMadeBy, title, pullRequest.getCommitAuthor());
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sourceCode, R.drawable.templarian_baseline_source_merge_24, title, false).setData(pullRequest));
      }
    }
    if (Config.SHOW_COPY_REPORT_DETAILS_IN_SETTINGS) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_copyDebug, R.drawable.baseline_bug_report_24, R.string.CopyReportData));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_BUILD_NO, R.id.btn_build, 0, Lang.getAppBuildAndVersion(tdlib), false));

    processUserFull(tdlib.myUserFull());

    this.contentView.setAdapter(adapter);

    tdlib.cache().addMyUserListener(this);
    setMyUserId(tdlib.myUserId());
    tdlib.listeners().subscribeToConnectivityUpdates(this);
    tdlib.listeners().subscribeToSessionUpdates(this);
    tdlib.listeners().addOptionsListener(this);
    TGLegacyManager.instance().addEmojiListener(adapter);
    TdlibManager.instance().global().addTokenStateListener(this);

    loadActiveSessions();

    return contentView;
  }

  @Override
  public void onSessionListChanged (Tdlib tdlib, boolean isWeakGuess) {
    runOnUiThreadOptional(this::loadActiveSessions);
  }

  private boolean loadingActiveSessions;
  private Tdlib.SessionsInfo sessions;

  private void loadActiveSessions () {
    if (loadingActiveSessions)
      return;
    loadingActiveSessions = true;
    tdlib.getSessions(false, sessionsInfo ->
      runOnUiThreadOptional(() -> {
        this.sessions = sessionsInfo;
        this.loadingActiveSessions = false;
        adapter.updateValuedSettingById(R.id.btn_devices);
        executeScheduledAnimation();
      })
    );
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return sessions == null;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 400l;
  }

  @Override
  public boolean onLongClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_build) {
      showBuildOptions(true);
      return true;
    }
    return false;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    for (int i = 0; i < textWrappers.size(); i++) {
      textWrappers.valueAt(i).setTextFlagEnabled(Text.FLAG_ALIGN_RIGHT, Lang.rtl());
      adapter.updateValuedSettingById(textWrappers.keyAt(i));
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    if (adapter != null) {
      switch (event) {
        case Lang.EVENT_PACK_CHANGED:
          adapter.notifyAllStringsChanged();
          if (headerCell != null) {
            headerCell.setSubtitle(getSubtext());
          }
          break;
        case Lang.EVENT_DIRECTION_CHANGED:
          adapter.notifyAllStringsChanged();
          break;
        case Lang.EVENT_STRING_CHANGED:
          adapter.notifyStringChanged(arg1);
          break;
        case Lang.EVENT_DATE_FORMAT_CHANGED:
          // Nothing to change
          break;
      }
    }
  }

  private final SparseArrayCompat<TextWrapper> textWrappers = new SparseArrayCompat<>();
  private final SparseArrayCompat<TdApi.FormattedText> currentTexts = new SparseArrayCompat<>();

  private TextWrapper obtainWrapper (CharSequence text, int id) {
    return obtainWrapper(TD.toFormattedText(text, false), id);
  }

  private TextWrapper obtainWrapper (TdApi.FormattedText text, int id) {
    TextWrapper textWrapper = textWrappers.get(id);
    if (textWrapper == null || !Td.equalsTo(currentTexts.get(id), text)) {
      currentTexts.put(id, text);
      // TODO: custom emoji support
      textWrapper = new TextWrapper(tdlib, text, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, null, null);
      textWrapper.addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0));
      textWrappers.put(id, textWrapper);
    }
    return textWrapper;
  }

  private @Nullable TdApi.UserFullInfo userFull;

  private void processUserFull (final TdApi.UserFullInfo userFull) {
    if (userFull == null) {
      return;
    }
    executeOnUiThreadOptional(() -> {
      this.userFull = userFull;
      checkBirthdate();
      setBio(userFull.bio);
    });
  }

  private static ListItem newBirthdateItem () {
    return new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_birthdate, R.drawable.baseline_cake_variant_24, R.string.Birthdate).setContentStrings(R.string.LoadingInformation, R.string.SetBirthdate);
  }

  private static ListItem newSuggestionItem (TdApi.SuggestedAction action) {
    ListItem item;
    switch (action.getConstructor()) {
      case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR:
        item = new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_suggestion, R.drawable.baseline_sim_card_alert_24, R.string.ReminderCheckPhoneNumber);
        break;
      case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR:
        item = new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_suggestion, R.drawable.baseline_gpp_maybe_24, R.string.ReminderCheckTfaPassword);
        break;
      case TdApi.SuggestedActionSetBirthdate.CONSTRUCTOR:
        item = new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_suggestion, R.drawable.baseline_cake_variant_24, R.string.ReminderSetBirthdate);
        break;
      default:
        Td.assertSuggestedAction_5c4efa90();
        throw Td.unsupported(action);
    }
    item
      .setData(action)
      .setLongId(action.getConstructor());
    return item;
  }

  private void checkBirthdate () {
    int position = adapter.indexOfViewById(R.id.btn_birthdate);
    boolean hadBirthdate = position != -1;
    boolean hasBirthdate = userFull != null && userFull.birthdate != null;
    if (hadBirthdate != hasBirthdate) {
      if (hasBirthdate) {
        position = adapter.indexOfViewById(R.id.btn_phone);
        adapter.addItems(position + 1,
          new ListItem(ListItem.TYPE_SEPARATOR),
          newBirthdateItem()
        );
      } else {
        adapter.removeRange(position - 1, 2);
      }
    } else if (hasBirthdate) {
      adapter.updateValuedSettingByPosition(position);
    }
  }

  private void setBio (@Nullable TdApi.FormattedText about) {
    if (about == null) {
      about = new TdApi.FormattedText("", new TdApi.TextEntity[0]);
    }
    if (this.about == null || !Td.equalsTo(this.about, about)) {
      this.about = about;
      adapter.updateValuedSettingById(R.id.btn_bio);
    }
  }

  @Override
  public void onSuggestedActionsChanged (TdApi.SuggestedAction[] addedActions, TdApi.SuggestedAction[] removedActions) {
    executeOnUiThreadOptional(() -> {
      for (TdApi.SuggestedAction action : addedActions) {
        addSuggestionToList(action);
      }
      for (TdApi.SuggestedAction action : removedActions) {
        removeSuggestionFromList(action);
      }
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().removeMyUserListener(this);
    setMyUserId(0);
    tdlib.listeners().unsubscribeFromConnectivityUpdates(this);
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
    tdlib.listeners().unsubscribeFromSessionUpdates(this);
    tdlib.listeners().removeOptionListener(this);
    TGLegacyManager.instance().removeEmojiListener(adapter);
    TdlibManager.instance().global().removeTokenStateListener(this);
    headerCell.performDestroy();
  }

  private void updateHeader () {
    TdApi.User user = tdlib.myUser();
    if (headerCell != null) {
      long chatId = user != null ? ChatId.fromUserId(user.id) : 0;
      headerCell.getAvatarReceiver().requestUser(tdlib, tdlib.myUserId(), AvatarReceiver.Options.FULL_SIZE);
      headerCell.setText(user != null ? TD.getUserName(user) : Lang.getString(R.string.LoadingUser), getSubtext());
      headerCell.setEmojiStatus(user);
      headerCell.setAllowTitleClick(chatId);
      headerCell.invalidate();
    }
  }

  private String getSubtext () {
    if (tdlib.isConnected()) {
      return Lang.lowercase(Lang.getString(tdlib.myUser() != null ? R.string.status_Online : R.string.network_Connecting));
    } else {
      return Lang.lowercase(tdlib.connectionStateText());
    }
  }

  // Callbacks

  private long myUserId;

  private void setMyUserId (long myUserId) {
    long oldMyUserId = this.myUserId;
    if (oldMyUserId != myUserId) {
      this.myUserId = myUserId;
      if (oldMyUserId != 0) {
        tdlib.cache().removeUserDataListener(oldMyUserId, this);
      }
      if (myUserId != 0) {
        tdlib.cache().addUserDataListener(myUserId, this);
      }
    }
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    runOnUiThreadOptional(() -> {
      if (myUserId == userId) {
        processUserFull(userFull);
      }
    });
  }

  @Override
  public void onMyUserUpdated (final TdApi.User myUser) {
    if (myUser == null) { // Ignoring log-out update
      return;
    }
    runOnUiThreadOptional(() -> {
      setMyUserId(myUser.id);
      updateHeader();
      if (setUsername(myUser)) {
        adapter.updateValuedSettingById(R.id.btn_username);
      }
      if (setPhoneNumber(myUser)) {
        adapter.updateValuedSettingById(R.id.btn_phone);
        adapter.updateValuedSettingById(R.id.btn_changePhoneNumber);
      }
    });
  }

  @Override
  public void onMyUserBioUpdated (@Nullable TdApi.FormattedText newBio) {
    setBio(newBio);
  }

  private @Nullable TdApi.Usernames myUsernames;
  private String myPhone, originalPhoneNumber;
  private @Nullable TdApi.FormattedText about;

  private void initMyUser () {
    TdApi.User user = tdlib.myUser();
    setUsername(user);
    setPhoneNumber(user);
  }

  private boolean setUsername (@Nullable TdApi.User myUser) {
    TdApi.Usernames usernames = myUser != null ? myUser.usernames : null;
    if (myUser != null && usernames == null) {
      usernames = new TdApi.Usernames(new String[0], new String[0], "");
    }
    if ((myUsernames == null && usernames != null) || (myUsernames != null && !Td.equalsTo(myUsernames, usernames))) {
      this.myUsernames = usernames;
      return true;
    }
    return false;
  }

  private boolean setPhoneNumber (@Nullable TdApi.User user) {
    String displayPhoneNumber;
    if (user != null) {
      displayPhoneNumber = originalPhoneNumber = Strings.formatPhone(user.phoneNumber);
      if (Settings.instance().needHidePhoneNumber()) {
        displayPhoneNumber = Strings.replaceNumbers(displayPhoneNumber);
      }
    } else {
      displayPhoneNumber = Lang.getString(R.string.LoadingPhone);
      originalPhoneNumber = null;
    }
    if (!StringUtils.equalsOrBothEmpty(myPhone, displayPhoneNumber)) {
      this.myPhone = displayPhoneNumber;
      return true;
    }
    return false;
  }

  @Override
  public void onConnectionDisplayStatusChanged () {
    runOnUiThreadOptional(() -> {
      if (headerCell != null) {
        headerCell.setSubtitle(getSubtext());
      }
    });
  }

  @Override
  public void performComplexPhotoOpen () {
    TdApi.User user = tdlib.myUser();
    if (user != null) {
      changeProfilePhoto();
    }
  }

  @Override
  public void onSendVideo (ImageGalleryFile file, boolean isFirst) { }

  @Override
  public void onSendPhoto (ImageGalleryFile file, boolean isFirst) {
    
  }

  private void openInstallerPage (@Nullable AppInstallationUtil.DownloadUrl downloadUrl) {
    String url = downloadUrl != null ? downloadUrl.url : BuildConfig.DOWNLOAD_URL;
    tdlib.ui().openUrl(this, url, new TdlibUi.UrlOpenParameters().disableInstantView());
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    SourceCodeType.TELEGRAM_X,
    SourceCodeType.TDLIB,
    SourceCodeType.TGCALLS,
    SourceCodeType.WEBRTC,
    SourceCodeType.FFMPEG,
    SourceCodeType.WEBP
  })
  private @interface SourceCodeType {
    int TELEGRAM_X = 0, TDLIB = 1, TGCALLS = 2, WEBRTC = 3, FFMPEG = 4, WEBP = 5;
  }

  private void viewSourceCode (@SourceCodeType int sourceCodeType) {
    String url;
    switch (sourceCodeType) {
      case SourceCodeType.TELEGRAM_X: {
        AppBuildInfo appBuildInfo = Settings.instance().getCurrentBuildInformation();
        url = appBuildInfo.commitUrl();
        break;
      }
      case SourceCodeType.TDLIB: {
        String tdlibCommitHash = Td.tdlibCommitHashFull();
        url = AppBuildInfo.tdlibCommitUrl(tdlibCommitHash);
        break;
      }
      case SourceCodeType.TGCALLS:
        url = BuildConfig.TGCALLS_COMMIT_URL;
        break;
      case SourceCodeType.WEBRTC:
        url = BuildConfig.WEBRTC_COMMIT_URL;
        break;
      case SourceCodeType.FFMPEG:
        url = BuildConfig.FFMPEG_COMMIT_URL;
        break;
      case SourceCodeType.WEBP:
        url = BuildConfig.WEBP_COMMIT_URL;
        break;
      default:
        throw new IllegalArgumentException(Integer.toString(sourceCodeType));
    }
    if (!StringUtils.isEmpty(url)) {
      tdlib.ui().openUrl(this,
        url,
        new TdlibUi.UrlOpenParameters().disableInstantView()
      );
    }
  }

  @Override
  public void onClick (View v) {
    cancelSupportOpen();
    if (tdlib.ui().handleProfileClick(this, v, v.getId(), tdlib.myUser(), true)) {
      return;
    }
    final int viewId = v.getId();
    if (viewId == R.id.btn_bio) {
      EditBioController c = new EditBioController(context, tdlib);
      c.setArguments(new EditBioController.Arguments(about != null ? about.text : "", 0));
      navigateTo(c);
    } else if (viewId == R.id.btn_birthdate) {
      tdlib.ui().openBirthdateEditor(this, v, TdlibUi.BirthdateOpenOrigin.PROFILE);
    } else if (viewId == R.id.btn_peer_id) {
      long selfId = tdlib.myUserId(true);
      if (selfId == 0) return;

      showOptions(Long.toString(selfId), new int[]{R.id.btn_peer_id_copy}, new String[]{Lang.getString(R.string.Copy)}, null, new int[]{R.drawable.baseline_content_copy_24}, (itemView, id) -> {
        if (id == R.id.btn_peer_id_copy) {
          UI.copyText(Long.toString(selfId), R.string.CopiedMyUserId);
        }
        return true;
      });
    } else if (viewId == R.id.btn_languageSettings) {
      navigateTo(new SettingsLanguageController(context, tdlib));
    } else if (viewId == R.id.btn_notificationSettings) {
      navigateTo(new SettingsNotificationController(context, tdlib));
    } else if (viewId == R.id.btn_devices) {
      navigateTo(new SettingsSessionsController(context, tdlib));
    } else if (viewId == R.id.btn_checkUpdates) {
      openInstallerPage(((AppInstallationUtil.DownloadUrl) ((ListItem) v.getTag()).getData()));
    } else if (viewId == R.id.btn_subscribeToBeta) {
      tdlib.ui().subscribeToBeta(this);
    } else if (viewId == R.id.btn_sourceCodeChanges) {// TODO provide an ability to view changes in PRs if they are present in both builds
      AppBuildInfo appBuildInfo = Settings.instance().getCurrentBuildInformation();
      tdlib.ui().openUrl(this, appBuildInfo.changesUrlFrom(previousBuildInfo), new TdlibUi.UrlOpenParameters().disableInstantView());
    } else if (viewId == R.id.btn_tdlib) {
      viewSourceCode(SourceCodeType.TDLIB);
    } else if (viewId == R.id.btn_sourceCode) {
      AppBuildInfo appBuildInfo = Settings.instance().getCurrentBuildInformation();
      PullRequest specificPullRequest = (PullRequest) ((ListItem) v.getTag()).getData();
      if (specificPullRequest != null) {
        tdlib.ui().openUrl(this, specificPullRequest.getCommitUrl(), new TdlibUi.UrlOpenParameters().disableInstantView());
      } else if (!appBuildInfo.getPullRequests().isEmpty() || appBuildInfo.getTdlibCommitFull() != null) {
        Options.Builder b = new Options.Builder();
        SpannableStringBuilder hint = new SpannableStringBuilder(Lang.getMarkdownString(this, R.string.OpenSourceGuide));
        if (!appBuildInfo.getPullRequests().isEmpty()) {
          hint.append("\n\n");
          hint.append(Lang.pluralBold(R.string.PullRequestsInfo, appBuildInfo.getPullRequests().size()));
        }
        b.info(hint);
        b.item(new OptionItem(R.id.btn_sourceCode, Lang.getCharSequence(R.string.format_commit, BuildConfig.PROJECT_NAME, appBuildInfo.getCommit()), OptionColor.NORMAL, R.drawable.baseline_logo_telegram_24));
        if (appBuildInfo.getTdlibCommitFull() != null) {
          b.item(new OptionItem(R.id.btn_tdlib, Lang.getCharSequence(R.string.format_commit, "TDLib " + Td.tdlibVersion(), Td.tdlibCommitHash()), OptionColor.NORMAL, R.drawable.baseline_tdlib_24));
        }
        b.item(new OptionItem(R.id.btn_tgcalls, Lang.getCharSequence(R.string.format_commit, "tgcalls", BuildConfig.TGCALLS_COMMIT), OptionColor.NORMAL, R.drawable.baseline_phone_in_talk_24));
        b.item(new OptionItem(R.id.btn_webrtc, Lang.getCharSequence(R.string.format_commit, "WebRTC", BuildConfig.WEBRTC_COMMIT), OptionColor.NORMAL, R.drawable.baseline_webrtc_24));
        b.item(new OptionItem(R.id.btn_ffmpeg, Lang.getCharSequence(R.string.format_commit, "FFmpeg", BuildConfig.FFMPEG_COMMIT), OptionColor.NORMAL, R.drawable.baseline_ffmpeg_24));
        if (BuildConfig.WEBP_ENABLED && N.hasBuiltInWebpSupport()) {
          b.item(new OptionItem(R.id.btn_webp, Lang.getCharSequence(R.string.format_commit, "WebP", BuildConfig.WEBP_COMMIT), OptionColor.NORMAL, R.drawable.dotvhs_baseline_webp_24));
        }
        int i = 0;
        for (PullRequest pullRequest : appBuildInfo.getPullRequests()) {
          b.item(new OptionItem(i++, Lang.getString(R.string.format_commit, Lang.getString(R.string.PullRequestCommit, pullRequest.getId()), pullRequest.getCommit()), OptionColor.NORMAL, R.drawable.templarian_baseline_source_merge_24));
        }
        showOptions(b.build(), (view, id) -> {
          if (id == R.id.btn_sourceCode) {
            viewSourceCode(SourceCodeType.TELEGRAM_X);
          } else if (id == R.id.btn_tdlib) {
            viewSourceCode(SourceCodeType.TDLIB);
          } else if (id == R.id.btn_webrtc) {
            viewSourceCode(SourceCodeType.WEBRTC);
          } else if (id == R.id.btn_ffmpeg) {
            viewSourceCode(SourceCodeType.FFMPEG);
          } else if (id == R.id.btn_webp) {
            viewSourceCode(SourceCodeType.WEBP);
          } else if (id == R.id.btn_tgcalls) {
            viewSourceCode(SourceCodeType.TGCALLS);
          } else if (id >= 0 && id < appBuildInfo.getPullRequests().size()) {
            PullRequest pullRequest = appBuildInfo.getPullRequests().get(id);
            tdlib.ui().openUrl(this, pullRequest.getCommitUrl(), new TdlibUi.UrlOpenParameters().disableInstantView());
          }
          return true;
        });
      }
    } else if (viewId == R.id.btn_copyDebug) {
      UI.copyText(U.getUsefulMetadata(tdlib), R.string.CopiedText);
    } else if (viewId == R.id.btn_themeSettings) {
      navigateTo(new SettingsThemeController(context, tdlib));
    } else if (viewId == R.id.btn_tweakSettings) {
      SettingsThemeController c = new SettingsThemeController(context, tdlib);
      c.setArguments(new SettingsThemeController.Args(SettingsThemeController.MODE_INTERFACE_OPTIONS));
      navigateTo(c);
    } else if (viewId == R.id.btn_chatSettings) {
      navigateTo(new SettingsDataController(context, tdlib));
    } else if (viewId == R.id.btn_privacySettings) {
      navigateTo(new SettingsPrivacyController(context, tdlib));
    } else if (viewId == R.id.btn_help) {
      supportOpen = tdlib.ui().openSupport(this);
    } else if (viewId == R.id.btn_stickerSettingsAndEmoji) {
      SettingsStickersAndEmojiController c = new SettingsStickersAndEmojiController(context, tdlib);
      c.setArguments(this);
      navigateTo(c);
    } else if (viewId == R.id.btn_chatFolders) {
      navigateTo(new SettingsFoldersController(context, tdlib));
    } else if (viewId == R.id.btn_faq) {
      tdlib.ui().openUrl(this, Lang.getString(R.string.url_faq), new TdlibUi.UrlOpenParameters().forceInstantView());
    } else if (viewId == R.id.btn_privacyPolicy) {
      tdlib.ui().openUrl(this, Lang.getStringSecure(R.string.url_privacyPolicy), new TdlibUi.UrlOpenParameters().forceInstantView());
    } else if (viewId == R.id.btn_suggestion) {
      ListItem listItem = (ListItem) v.getTag();
      showSuggestionPopup(v, (TdApi.SuggestedAction) listItem.getData());
    } else if (viewId == R.id.btn_build) {
      if (Settings.instance().hasLogsEnabled()) {
        showBuildOptions(true);
      } else {
        tdlib.getTesterLevel(testerLevel -> runOnUiThreadOptional(() ->
          showBuildOptions(testerLevel >= Tdlib.TesterLevel.TESTER)
        ));
      }
    }
  }

  public void showSuggestionPopup (View suggestionView, TdApi.SuggestedAction suggestedAction) {
    if (!tdlib.isSettingSuggestion(suggestedAction)) {
      return;
    }
    CharSequence info = null;
    IntList ids = new IntList(3);
    StringList titles = new StringList(3);
    IntList colors = new IntList(3);
    IntList icons = new IntList(3);

    switch (suggestedAction.getConstructor()) {
      case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR: {
        info = Lang.getString(R.string.ReminderCheckPhoneNumberDescription);

        ids.append(R.id.btn_changePhoneNumber);
        titles.append(R.string.ReminderActionChangePhoneNumber);
        colors.append(OptionColor.NORMAL);
        icons.append(R.drawable.baseline_edit_24);

        ids.append(R.id.btn_cancel);
        titles.append(Lang.getString(R.string.ReminderCheckPhoneNumberHide, originalPhoneNumber));
        colors.append(OptionColor.NORMAL);
        icons.append(R.drawable.baseline_check_24);

        ids.append(R.id.btn_info);
        titles.append(R.string.ReminderActionLearnMore);
        colors.append(OptionColor.NORMAL);
        icons.append(R.drawable.baseline_info_24);

        break;
      }
      case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR: {
        info = Lang.getString(R.string.ReminderCheckTfaPasswordDescription);

        ids.append(R.id.btn_2fa);
        titles.append(R.string.ReminderActionVerifyPassword);
        colors.append(OptionColor.BLUE);
        icons.append(R.drawable.mrgrigri_baseline_textbox_password_24);

        ids.append(R.id.btn_cancel);
        titles.append(R.string.ReminderCheckTfaPasswordHide);
        colors.append(OptionColor.NORMAL);
        icons.append(R.drawable.baseline_cancel_24);

        break;
      }
      case TdApi.SuggestedActionSetBirthdate.CONSTRUCTOR: {
        tdlib.ui().openBirthdateEditor(this, suggestionView, TdlibUi.BirthdateOpenOrigin.SUGGESTED_ACTION);
        return;
      }
      default: {
        Td.assertSuggestedAction_5c4efa90();
        throw Td.unsupported(suggestedAction);
      }
    }

    showOptions(info, ids.get(), titles.get(), colors.get(), icons.get(), (view, id) -> {
      if (id == R.id.btn_changePhoneNumber) {
        navigateTo(new SettingsPhoneController(context, tdlib));
      } else if (id == R.id.btn_2fa) {
        tdlib.client().send(new TdApi.GetPasswordState(), result -> {
          if (result.getConstructor() == TdApi.PasswordState.CONSTRUCTOR) {
            runOnUiThreadOptional(() -> {
              PasswordController controller = new PasswordController(context, tdlib);
              controller.setArguments(new PasswordController.Args(PasswordController.MODE_CONFIRM, (TdApi.PasswordState) result).setSuccessListener((pwd) -> {
                dismissSuggestion(suggestedAction);
              }));
              navigateTo(controller);
            });
          } else {
            UI.showError(result);
          }
        });
      } else if (id == R.id.btn_info) {
        tdlib.ui().openUrl(this, Lang.getStringSecure(R.string.url_faqPhoneNumber), new TdlibUi.UrlOpenParameters().forceInstantView());
      } else if (id == R.id.btn_cancel) {
        dismissSuggestion(suggestedAction);
      }

      return true;
    });
  }

  private void addSuggestionToList (TdApi.SuggestedAction suggestedAction) {
    if (!tdlib.isSettingSuggestion(suggestedAction))
      return;
    int index = adapter.indexOfViewByIdReverse(R.id.btn_suggestion);
    if (index != -1) {
      // add separator & item
      adapter.addItems(index + 1,
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        newSuggestionItem(suggestedAction)
      );
    } else {
      index = adapter.indexOfViewById(R.id.btn_bio);
      adapter.addItems(index + 2,
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        newSuggestionItem(suggestedAction),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      );
    }
  }

  private void removeSuggestionFromList (TdApi.SuggestedAction suggestedAction) {
    if (!tdlib.isSettingSuggestion(suggestedAction))
      return;

    int removalIndex = adapter.indexOfViewByLongId(suggestedAction.getConstructor());
    if (removalIndex == -1)
      return;
    ListItem previousItem = adapter.getItem(removalIndex - 1);
    ListItem nextItem = adapter.getItem(removalIndex + 1);
    if (nextItem == null || previousItem == null)
      return;
    int previousViewType = previousItem.getViewType();
    int nextViewType = nextItem.getViewType();

    switch (nextViewType) {
      case ListItem.TYPE_SHADOW_BOTTOM: {
        if (previousViewType == ListItem.TYPE_SHADOW_TOP) {
          adapter.removeRange(removalIndex - 1, 3);
        } else if (previousViewType == ListItem.TYPE_SEPARATOR) {
          adapter.removeRange(removalIndex - 1, 2);
        }
        break;
      }

      case ListItem.TYPE_SEPARATOR: {
        adapter.removeRange(removalIndex, 2);
        break;
      }
    }
  }

  private void dismissSuggestion (TdApi.SuggestedAction suggestedAction) {
    tdlib.client().send(new TdApi.HideSuggestedAction(suggestedAction), tdlib.okHandler());
  }

  private void showBuildOptions (boolean allowDebug) {
    final int size = allowDebug ? 3 : 2;
    IntList ids = new IntList(size);
    IntList icons = new IntList(size);
    IntList colors = new IntList(size);
    StringList strings = new StringList(size);

    ids.append(R.id.btn_copyText);
    strings.append(R.string.CopyVersion);
    icons.append(R.drawable.baseline_content_copy_24);
    colors.append(OptionColor.NORMAL);

    if (!Config.SHOW_COPY_REPORT_DETAILS_IN_SETTINGS) {
      ids.append(R.id.btn_copyDebug);
      strings.append(R.string.CopyReportData);
      icons.append(R.drawable.baseline_bug_report_24);
      colors.append(OptionColor.NORMAL);
    }

    boolean notificationError = tdlib.context().getTokenState() == TdlibManager.TokenState.ERROR;
    if (allowDebug || notificationError) {
      ids.append(R.id.btn_pushService);
      strings.append(R.string.PushServices);
      icons.append(notificationError ? R.drawable.baseline_sync_problem_24 : R.drawable.baseline_sync_24);
      colors.append(notificationError ? OptionColor.RED : OptionColor.NORMAL);
    }

    if (allowDebug) {
      ids.append(R.id.btn_tdlib);
      strings.append(R.string.TdlibLogs);
      icons.append(R.drawable.baseline_build_24);
      colors.append(OptionColor.NORMAL);

      ids.append(R.id.btn_build);
      strings.append(R.string.AppLogs);
      icons.append(R.drawable.baseline_build_24);
      colors.append(OptionColor.NORMAL);
      
      ids.append(R.id.btn_experiment);
      strings.append(R.string.ExperimentalSettings);
      icons.append(R.drawable.templarian_baseline_flask_24);
      colors.append(OptionColor.NORMAL);
    }

    SpannableStringBuilder b = new SpannableStringBuilder();
    b.append(Lang.getMarkdownStringSecure(this, R.string.AppSignature, BuildConfig.VERSION_NAME));

    showOptions(b, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_copyText) {
        UI.copyText(Lang.getAppBuildAndVersion(tdlib), R.string.CopiedText);
      } else if (id == R.id.btn_copyDebug) {
        UI.copyText(U.getUsefulMetadata(tdlib), R.string.CopiedText);
      } else if (id == R.id.btn_pushService) {
        SettingsBugController c = new SettingsBugController(context, tdlib);
        c.setArguments(new SettingsBugController.Args(SettingsBugController.Section.PUSH));
        navigateTo(c);
      } else if (id == R.id.btn_build) {
        navigateTo(new SettingsBugController(context, tdlib));
      } else if (id == R.id.btn_tdlib || id == R.id.btn_experiment) {
        boolean isTdlib = id == R.id.btn_tdlib;
        tdlib.getTesterLevel(level -> runOnUiThreadOptional(() -> {
          if (isTdlib) {
            openTdlibLogs(level, null);
          } else {
            openExperimentalSettings(level);
          }
        }));
      }
      return true;
    });
  }

  @Override
  public void onInstalledStickerSetsUpdated (long[] stickerSetIds, TdApi.StickerType stickerType) {
    if (stickerType.getConstructor() == TdApi.StickerTypeRegular.CONSTRUCTOR || stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR) {
      runOnUiThreadOptional(() -> {
        stickerSetsPreloader.reloadStickersIfEqualTypes(stickerType);
        emojiPacksPreloader.reloadStickersIfEqualTypes(stickerType);
      });
    }
  }

  // Stickers preloading

  public interface StickerSetLoadListener {
    void onStickerSetsLoaded (ArrayList<TGStickerSetInfo> stickerSets, TdApi.StickerType type);
  }


  private final StickerSetsPreloader stickerSetsPreloader = new StickerSetsPreloader(this, new TdApi.StickerTypeRegular());
  private final StickerSetsPreloader emojiPacksPreloader = new StickerSetsPreloader(this, new TdApi.StickerTypeCustomEmoji());

  public void addStickerSetListener (boolean isEmoji, StickerSetLoadListener listener) {
    (isEmoji ? emojiPacksPreloader : stickerSetsPreloader).addStickerSetListener(listener);
  }

  public void removeStickerSetListener (boolean isEmoji, StickerSetLoadListener listener) {
    (isEmoji ? emojiPacksPreloader : stickerSetsPreloader).removeStickerSetListener(listener);
  }

  public @Nullable ArrayList<TGStickerSetInfo> getStickerSets (boolean isEmoji) {
    return (isEmoji ? emojiPacksPreloader : stickerSetsPreloader).getStickerSets();
  }

  public int getStickerSetsCount (boolean isEmoji) {
    ArrayList<TGStickerSetInfo> sets = getStickerSets(isEmoji);
    return sets != null ? sets.size() : -1;
  }

  private static class StickerSetsPreloader {
    private final ReferenceList<StickerSetLoadListener> listeners = new ReferenceList<>(false);

    private final ViewController<?> context;
    private final Tdlib tdlib;
    private final TdApi.StickerType type;

    private @Nullable ArrayList<TGStickerSetInfo> allStickerSets;
    private boolean hasPreloadedStickers;

    public StickerSetsPreloader (ViewController<?> context, TdApi.StickerType type) {
      this.context = context;
      this.tdlib = context.tdlib();
      this.type = type;
    }

    public @Nullable ArrayList<TGStickerSetInfo> getStickerSets () {
      return allStickerSets;
    }

    public void reloadStickersIfEqualTypes (TdApi.StickerType type) {
      if (this.type.getConstructor() == type.getConstructor()) {
        allStickerSets = null;
        hasPreloadedStickers = false;
        preloadStickers();
      }
    }

    public void preloadStickers () {
      if (hasPreloadedStickers) {
        return;
      }
      hasPreloadedStickers = true;
      tdlib.client().send(new TdApi.GetInstalledStickerSets(type), object -> {
        if (!context.isDestroyed()) {
          switch (object.getConstructor()) {
            case TdApi.StickerSets.CONSTRUCTOR: {
              TdApi.StickerSetInfo[] stickerSets = ((TdApi.StickerSets) object).sets;
              final ArrayList<TGStickerSetInfo> parsedStickerSets = new ArrayList<>(stickerSets.length);
              for (TdApi.StickerSetInfo stickerSet : stickerSets) {
                parsedStickerSets.add(new TGStickerSetInfo(tdlib, stickerSet));
              }
              parsedStickerSets.trimToSize();
              context.runOnUiThreadOptional(() ->
                setStickerSets(parsedStickerSets)
              );
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(object);
              break;
            }
          }
        }
      });
    }

    private void setStickerSets (@Nullable ArrayList<TGStickerSetInfo> stickerSets) {
      this.allStickerSets = stickerSets;
      for (StickerSetLoadListener listener : listeners) {
        listener.onStickerSetsLoaded(stickerSets, type);
      }
    }

    public void addStickerSetListener (StickerSetLoadListener listener) {
      this.listeners.add(listener);
    }

    public void removeStickerSetListener (StickerSetLoadListener listener) {
      this.listeners.remove(listener);
    }
  }
}
