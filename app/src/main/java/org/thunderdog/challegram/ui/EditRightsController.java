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
 * File created on 19/08/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class EditRightsController extends EditBaseController<EditRightsController.Args> implements View.OnClickListener, TdlibCache.BasicGroupDataChangeListener {
  public static final int MODE_ADMIN_PROMOTION = 1;
  public static final int MODE_RESTRICTION = 2;
  public static final int MODE_CHAT_PERMISSIONS = 3;

  public static class Args {
    public long chatId;
    public final TdApi.MessageSender senderId;
    public final TdApi.ChatMemberStatus myStatus;
    public final TdApi.ChatMember member;
    public final int mode;
    public int forwardLimit;
    public boolean noFocusLock;

    public Args (long chatId, TdApi.MessageSender senderId, boolean isRestrict, @NonNull TdApi.ChatMemberStatus myStatus, @Nullable TdApi.ChatMember member) {
      this.chatId = chatId;
      this.senderId = senderId;
      this.mode = isRestrict ? MODE_RESTRICTION : MODE_ADMIN_PROMOTION;
      this.myStatus = myStatus;
      this.member = member;
    }

    public Args (long chatId) {
      this.chatId = chatId;
      this.senderId = null;
      this.mode = MODE_CHAT_PERMISSIONS;
      this.myStatus = null;
      this.member = null;
    }

    public Args forwardLimit (int forwardLimit) {
      this.forwardLimit = forwardLimit;
      return this;
    }

    public Args noFocusLock () {
      this.noFocusLock = true;
      return this;
    }
  }

  public EditRightsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.ChatMemberStatusAdministrator targetAdmin;
  private TdApi.ChatMemberStatusRestricted targetRestrict;
  private boolean canViewMessages, isForum;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);

    canViewMessages = tdlib.isChannel(args.chatId);
    isForum = tdlib.isForum(args.chatId);

    switch (args.mode) {
      case MODE_RESTRICTION: {
        if (args.member != null && args.member.status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR) {
          TdApi.ChatMemberStatusRestricted currentRestrict = (TdApi.ChatMemberStatusRestricted) args.member.status;
          canViewMessages = true;
          targetRestrict = new TdApi.ChatMemberStatusRestricted(true, currentRestrict.restrictedUntilDate, Td.copyOf(currentRestrict.permissions));
        } else {
          canViewMessages = false;
          targetRestrict = new TdApi.ChatMemberStatusRestricted(false, 0, new TdApi.ChatPermissions());
          if (args.member != null && args.member.status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR) {
            targetRestrict.restrictedUntilDate = ((TdApi.ChatMemberStatusBanned) args.member.status).bannedUntilDate;
          }
        }
        break;
      }
      case MODE_ADMIN_PROMOTION: {
        if (args.member != null) {
          if (args.member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
            TdApi.ChatMemberStatusCreator creator = (TdApi.ChatMemberStatusCreator) args.member.status;
            targetAdmin = new TdApi.ChatMemberStatusAdministrator(
              creator.customTitle,
              true,
              new TdApi.ChatAdministratorRights(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                creator.isAnonymous
              )
            );
          } else if (args.member.status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
            TdApi.ChatMemberStatusAdministrator admin = (TdApi.ChatMemberStatusAdministrator) args.member.status;
            targetAdmin = (TdApi.ChatMemberStatusAdministrator) Td.copyOf(admin);
          } else {
            targetAdmin = newTargetAdmin();
          }
        } else {
          targetAdmin = newTargetAdmin();
        }
        break;
      }
      case MODE_CHAT_PERMISSIONS: {
        canViewMessages = true;
        targetRestrict = new TdApi.ChatMemberStatusRestricted(true, 0, Td.copyOf(tdlib.chatPermissions(args.chatId)));
        break;
      }
    }
  }

  private TdApi.ChatMemberStatusAdministrator newTargetAdmin () {
    Args args = getArgumentsStrict();
    switch (args.myStatus.getConstructor()) {
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR: {
        TdApi.ChatMemberStatusAdministrator me = (TdApi.ChatMemberStatusAdministrator) args.myStatus;
        return new TdApi.ChatMemberStatusAdministrator(null, true, Td.copyOf(me.rights));
      }
    }
    // TODO bot defaults
    return new TdApi.ChatMemberStatusAdministrator(
      null,
      true,
      new TdApi.ChatAdministratorRights(
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        isForum,
        true,
        true,
        true,
        true,
        true,
        false
      )
    );
  }

  @Override
  public int getId () {
    return R.id.controller_memberRights;
  }

  private SettingsAdapter adapter;

  private void setRestrictUntilDate (int date) {
    if (targetRestrict.restrictedUntilDate != date) {
      targetRestrict.restrictedUntilDate = date;
      adapter.updateValuedSettingById(R.id.btn_date);
      checkDoneButton();
    }
  }

  private void setBlockFor (int duration) {
    setRestrictUntilDate(duration != 0 ? (int) (tdlib.currentTimeMillis() / 1000l + duration) : 0);
  }

  private ListItem customTitle;

  @Override
  @SuppressWarnings("WrongConstant")
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();

    if (isDoneInProgress()) {
      return;
    }

    final int viewType = item.getViewType();
    final int viewId = item.getId();

    if (viewType == ListItem.TYPE_CHAT_BETTER) {
      TGFoundChat chat = (TGFoundChat) item.getData();
      long userId = chat.getUserId();
      TdlibUi.UrlOpenParameters urlOpenParameters = new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(view).controller(this));
      if (userId != 0) {
        tdlib.ui().openPrivateProfile(this, userId, urlOpenParameters);
      } else {
        tdlib.ui().openChat(this, chat.getChatId(), new TdlibUi.ChatOpenParameters().keepStack().urlOpenParameters(urlOpenParameters));
      }
    } else if (viewId == R.id.btn_togglePermission) {
      @RightId final int rightId = item.getIntValue();
      boolean canEdit = hasAccessToEditRight(item);
      if (canEdit) {
        toggleValueForRightId(rightId);
      } else {
        CharSequence text = getHintForToggleUnavailability(item);
        if (text != null) {
          context().tooltipManager()
            .builder(((SettingView) view).getToggler())
            .show(this, tdlib, R.drawable.baseline_info_24, text);
        }
      }
    } else if (viewId == R.id.btn_togglePermissionGroup) {
      RightOption option = (RightOption) item.getData();
      if (option != null && option.groupRightIds != null) {
        toggleRightsGroupVisibility(option.groupRightIds);
      }
    } else if (viewId == R.id.btn_transferOwnership) {
      if (ChatId.isBasicGroup(getArgumentsStrict().chatId)) {
        showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), this::onTransferOwnershipClick);
      } else {
        onTransferOwnershipClick();
      }
    } else if (viewId == R.id.btn_unblockSender) {
      final Runnable unblockRunnable = () -> {
        setCanViewMessages(true);
        Td.copyTo(tdlib.chatPermissions(getArgumentsStrict().chatId), targetRestrict.permissions);
        updateValues();
        setDoneInProgress(true);
        setDoneVisible(true);
        performRequest(true);
      };


      Args args = getArgumentsStrict();
      targetRestrict.isMember = TD.isMember(args.member.status);

      if (targetRestrict.isMember || args.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
        showOptions(Lang.getStringBold(R.string.QUnblockX, tdlib.senderName(args.senderId)), new int[] {R.id.btn_blockSender, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RemoveRestrictions), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_blockSender) {
            unblockRunnable.run();
          }
          return true;
        });
      } else {
        showSettings(new SettingsWrapBuilder(R.id.btn_unblockSender)
          .setHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, Lang.getStringBold(R.string.QUnblockX, tdlib.senderName(args.senderId)), false))
          .setIntDelegate((id, result) -> {
            boolean addBackToGroup = result.get(RightId.READ_MESSAGES) != 0;
            if (addBackToGroup) {
              targetRestrict.isMember = true;
            }
            unblockRunnable.run();
          })
          .setRawItems(new ListItem[] {
            new ListItem(ListItem.TYPE_CHECKBOX_OPTION, RightId.READ_MESSAGES, 0, tdlib.isChannel(args.chatId) ? R.string.InviteBackToChannel : R.string.InviteBackToGroup, false)
          })
          .setSaveStr(R.string.Unban)
          .setSaveColorId(ColorId.textNegative)
        );
      }
    } else if (viewId == R.id.btn_dismissAdmin) {
      showOptions(null, new int[] {R.id.btn_dismissAdmin, R.id.btn_cancel}, new String[] {Lang.getString(R.string.DismissAdmin), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_dismissAdmin && !isDoneInProgress()) {
          Td.setAllAdministratorRights(targetAdmin.rights, false);
          updateValues();
          setDoneInProgress(true);
          setDoneVisible(true);
          performRequest(true);
        }
        return true;
      });
    } else if (viewId == R.id.btn_date) {
      if (getArgumentsStrict().mode == MODE_RESTRICTION && getArgumentsStrict().senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
        context.tooltipManager().builder(view).show(tdlib, tdlib.isChannel(Td.getSenderId(getArgumentsStrict().senderId)) ? R.string.BanChannelHint : R.string.BanChatHint).hideDelayed();
        return;
      }
      showOptions(null,
        new int[] {R.id.btn_1day, R.id.btn_1week, R.id.btn_1month, R.id.btn_forever, R.id.btn_custom},
        new String[] {Lang.plural(R.string.xDays, 1), Lang.plural(R.string.xWeeks, 1), Lang.plural(R.string.xMonths, 1), Lang.getString(R.string.UserRestrictionsUntilForever), Lang.getString(R.string.CustomDate)}, null, null, (itemView, id) -> {
          if (id == R.id.btn_1day) {
            setBlockFor(60 * 60 * 24 + 60 * 2);
          } else if (id == R.id.btn_1week) {
            setBlockFor(60 * 60 * 24 * 7 + 60 * 2);
          } else if (id == R.id.btn_1month) {
            setBlockFor(60 * 60 * 24 * 30 + 60 * 2);
          } else if (id == R.id.btn_custom) {
            if (canViewMessages) {
              showDateTimePicker(Lang.getString(R.string.RestrictUser), R.string.RestrictUntilToday, R.string.RestrictUntilTomorrow, R.string.RestrictUntilFuture, millis -> setRestrictUntilDate((int) (millis / 1000l)), null);
            } else {
              showDateTimePicker(Lang.getString(R.string.BlockUser), R.string.BlockUntilToday, R.string.BlockUntilTomorrow, R.string.BlockUntilFuture, millis -> setRestrictUntilDate((int) (millis / 1000l)), null);
            }
          } else if (id == R.id.btn_forever) {
            setBlockFor(0);
          }
          return true;
        });
    }
  }

  @Override
  protected boolean onDoneClick () {
    if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
      targetAdmin.rights.canManageChat = true;
    }
    performRequest(false);
    return true;
  }

  private void performRequest (boolean force) {
    if (isDoneInProgress() && !force) {
      return;
    }
    final Args args = getArgumentsStrict();
    final TdApi.ChatMemberStatus newStatus;
    if (args.mode == MODE_CHAT_PERMISSIONS) {
      if (Td.equalsTo(targetRestrict.permissions, tdlib.chatPermissions(args.chatId))) {
        navigateBack();
        return;
      }

      setDoneInProgress(true);
      setStackLocked(true);

      tdlib.setChatPermissions(args.chatId, targetRestrict.permissions, isOk -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          setStackLocked(false);
          setDoneInProgress(false);
          if (isOk) {
            onSaveCompleted();
          }
        }
      }));
      return;
    }

    TdApi.ChatPermissions chatPermissions = tdlib.chatPermissions(args.chatId);
    if (args.mode == MODE_RESTRICTION) {
      if (canViewMessages) {
        if (!TD.hasRestrictions(targetRestrict.permissions, chatPermissions)) {
          // newStatus = targetRestrict.isMember ? new TdApi.ChatMemberStatusMember() : new TdApi.ChatMemberStatusLeft();
          if (args.member == null || !TD.isRestricted(args.member.status)) {
            UI.showToast(R.string.NoRestrictionsHint, Toast.LENGTH_SHORT);
            return;
          }
          newStatus = targetRestrict.isMember ? new TdApi.ChatMemberStatusMember() : new TdApi.ChatMemberStatusLeft();
        } else {
          newStatus = targetRestrict;
        }
      } else {
        newStatus = new TdApi.ChatMemberStatusBanned(targetRestrict.restrictedUntilDate);
      }
    } else if (args.member != null && args.member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
      TdApi.ChatMemberStatusCreator creator = (TdApi.ChatMemberStatusCreator) args.member.status;
      newStatus = new TdApi.ChatMemberStatusCreator(targetAdmin.customTitle, targetAdmin.rights.isAnonymous, creator.isMember);
    } else if (Td.isEmpty(targetAdmin, chatPermissions)) {
      newStatus = new TdApi.ChatMemberStatusMember();
    } else {
      newStatus = targetAdmin;
    }

    String newCustomTitle = Td.getCustomTitle(newStatus);
    if (!StringUtils.isEmpty(newCustomTitle) && newCustomTitle.length() > TdConstants.MAX_CUSTOM_TITLE_LENGTH) {
      UI.showToast(R.string.CustomTitleTooBig, Toast.LENGTH_SHORT);
      return;
    }

    Runnable act = () -> {
      setDoneInProgress(true);
      setStackLocked(true);
      tdlib.setChatMemberStatus(args.chatId, args.senderId, newStatus, args.forwardLimit, args.member != null ? args.member.status : null, (success, error) -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          setStackLocked(false);
          setDoneInProgress(false);
          if (success) {
            ViewController<?> c = previousStackItem();
            if (c instanceof ContactsController) {
              destroyStackItemAt(stackSize() - 2);
            }
            onSaveCompleted();
          } else {
            showError(error != null && TD.ERROR_USER_PRIVACY.equals(error.message) ?
              Lang.getString(R.string.errorPrivacyAddMember) :
              TD.toErrorString(error)
            );
          }
        }
      }));
    };

    if (ChatId.isBasicGroup(args.chatId) && TD.needUpgradeToSupergroup(newStatus)) {
      showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), act);
    } else {
      act.run();
    }
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      public void onTextChanged (MaterialEditTextGroup v, CharSequence charSequence) {
        String title = charSequence.toString();
        if (customTitle.setStringValueIfChanged(title)) {
          if (targetAdmin != null)
            targetAdmin.customTitle = title;
          checkDoneButton();
        }
      }

      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        Args args = getArgumentsStrict();
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setEmptyHint(args.member != null && TD.isCreator(args.member.status) ? R.string.message_ownerSign : R.string.message_adminSignPlain);
        editText.setText(item.getStringValue());
        editText.setInputEnabled(TD.isCreator(args.myStatus) || isNewRuleSet() || canDismissAdmin());
        editText.setMaxLength(TdConstants.MAX_CUSTOM_TITLE_LENGTH);
        if (parent.getBackground() == null) {
          ViewSupport.setThemedBackground(parent, ColorId.filling, EditRightsController.this);
        }
      }

      @Override
      @SuppressWarnings("WrongConstant")
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int viewId = item.getId();
        boolean needRotateIcon = false;
        if (viewId == R.id.btn_togglePermission) {
          @RightId int rightId = item.getIntValue();
          boolean canEdit = hasAccessToEditRight(item);
          view.setIgnoreEnabled(true);
          view.setEnabled(canEdit || getHintForToggleUnavailability(item) != null);
          view.setVisuallyEnabled(canEdit, isUpdate);

          boolean isToggler = item.getViewType() == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER;
          boolean needAddData = getArgumentsStrict().mode == MODE_CHAT_PERMISSIONS;

          if (isToggler) {
            view.getToggler().setUseNegativeState(true);
          }
          view.getToggler().setRadioEnabled(item.getBoolValue(), isUpdate);
          view.getToggler().setShowLock(!canEdit);
          if (needAddData) {
            view.setData(item.getBoolValue() ? R.string.AllMembers : (isCommonRight(rightId) || rightId == RightId.MANAGE_OR_CREATE_TOPICS) ? R.string.OnlyAdminsSpecific : R.string.OnlyAdmins);
          }
        } else if (viewId == R.id.btn_togglePermissionGroup) {
          final RightOption option = (RightOption) item.getData();
          if (option != null && option.groupRightIds != null) {
            final boolean canEdit = hasAccessToEditRightsGroup(option.groupRightIds);
            final int count = getRightsGroupEnabledCount(option.groupRightIds);
            view.setIgnoreEnabled(true);
            view.setEnabled(true /*canEdit || getHintForToggleUnavailability(item) != null*/);
            view.setVisuallyEnabled(canEdit, isUpdate);
            view.getToggler().setUseNegativeState(true);
            view.getToggler().setRadioEnabled(item.getBoolValue(), isUpdate);
            view.getToggler().setShowLock(!canEdit);
            view.setData(Lang.pluralBold(R.string.xPermissionsSendMediaAllowed, count, option.groupRightIds.length));
            needRotateIcon = adapter.indexOfViewByIdAndValue(R.id.btn_togglePermission, option.groupRightIds[0]) != -1;
          }
        } else if (viewId == R.id.btn_date) {
          boolean canEdit = hasAccessToEditRight(item);
          view.setIgnoreEnabled(true);
          view.setEnabled(canEdit || getHintForToggleUnavailability(item) != null);
          view.setVisuallyEnabled(canEdit, isUpdate);
          boolean isDate;
          if (targetRestrict.restrictedUntilDate == 0) {
            view.setData(R.string.UserRestrictionsUntilForever);
            isDate = true;
          } else {
            int diff = targetRestrict.restrictedUntilDate - (int) (System.currentTimeMillis() / 1000l);
            if (Lang.preferTimeForDuration(diff)) {
              view.setData(Lang.dateYearShortTime(targetRestrict.restrictedUntilDate, TimeUnit.SECONDS));
              isDate = true;
            } else {
              view.setData(Lang.getDuration(diff, 0, targetRestrict.restrictedUntilDate, false));
              isDate = false;
            }
          }
          if (isDate) {
            view.setName(canViewMessages ? R.string.RestrictUntil : R.string.BlockUntil);
          } else {
            view.setName(canViewMessages ? R.string.RestrictFor : R.string.BlockFor);
          }
        }
        if (view.getToggler() != null) {
          if (viewId == R.id.btn_togglePermissionGroup) {
            view.getToggler().setOnClickListener(v -> {
              ListItem i = (ListItem) view.getTag();
              if (i.getId() == R.id.btn_togglePermissionGroup) {
                final RightOption option = (RightOption) item.getData();
                if (option != null && option.groupRightIds != null) {
                  toggleRightsGroup(view, option.groupRightIds);
                }
              } else {
                view.performClick();
              }
            });
            view.getToggler().setClickable(true);
          } else {
            view.getToggler().setOnClickListener(null);
            view.getToggler().setClickable(false);
          }
        }
        view.setIconRotated(needRotateIcon, isUpdate);
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
      }

      @Override
      public void modifySettingView (int viewType, SettingView settingView) {
        super.modifySettingView(viewType, settingView);
        if (viewType == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER || viewType == ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE) {
          settingView.forcePadding(Screen.dp(73), 0);
        }
      }
    };

    buildCells();

    recyclerView.setAdapter(adapter);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.bottom = ((ListItem) view.getTag()).getViewType() == ListItem.TYPE_ZERO_VIEW ? Screen.dp(56f) + Screen.dp(16f) * 2 : 0;
      }
    });

    if (isNewRuleSet()) {
      setDoneVisible(true);
    }
    setDoneIcon(R.drawable.baseline_check_24);

    if (ChatId.isBasicGroup(getArgumentsStrict().chatId)) {
      tdlib.cache().subscribeToGroupUpdates(ChatId.toBasicGroupId(getArgumentsStrict().chatId), this);
    }
  }

  @Override
  protected void setDoneVisible (boolean isVisible) {
    if (isVisible != isDoneVisible()) {
      super.setDoneVisible(isVisible);
      recyclerView.invalidateItemDecorations();
      adapter.notifyItemChanged(adapter.getItemCount() - 1);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (ChatId.isBasicGroup(getArgumentsStrict().chatId)) {
      tdlib.cache().unsubscribeFromGroupUpdates(ChatId.toBasicGroupId(getArgumentsStrict().chatId), this);
    }
  }

  @Override
  public void onBasicGroupUpdated (TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    if (migratedToSupergroup) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          tdlib.cache().unsubscribeFromGroupUpdates(basicGroup.id, this);
          getArgumentsStrict().chatId = ChatId.fromSupergroupId(basicGroup.upgradedToSupergroupId);
        }
      });
    }
  }

  private boolean isNewRuleSet () {
    Args args = getArgumentsStrict();
    if (args.mode == MODE_CHAT_PERMISSIONS)
      return false;
    if (args.member == null) {
      return true;
    }
    if (args.mode == MODE_RESTRICTION) {
      switch (args.member.status.getConstructor()) {
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
          return false;
      }
    } else {
      switch (args.member.status.getConstructor()) {
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return false;
      }
    }
    return true;
  }

  private CharSequence getHintForToggleUnavailability (ListItem item) {
    final int rightId;
    if (item.getId() == R.id.btn_togglePermission) {
      rightId = item.getIntValue();
    } else {
      rightId = -1;
    }
    boolean currentValue = item.getBoolValue();
    Args args = getArgumentsStrict();
    switch (args.mode) {
      case MODE_CHAT_PERMISSIONS: {
        if (!tdlib.canRestrictMembers(args.chatId)) {
          return null; // No need to explain
        }
        if (tdlib.isBroadcastGroup(args.chatId)) {
          return Lang.getMarkdownString(this, R.string.BroadcastWriteHint);
        }
        if (currentValue)
          return null;
        TdApi.Chat chat = tdlib.chatStrict(args.chatId);
        switch (rightId) {
          case RightId.CHANGE_CHAT_INFO: {
            if (!tdlib.canChangeInfo(chat)) {
              return Lang.getMarkdownString(this, R.string.NoRightAllowChangeInfo);
            }
            if (tdlib.chatPublic(args.chatId)) {
              return Lang.getMarkdownString(this, R.string.NoRightAllowChangeInfoPublic);
            }
            break;
          }
          case RightId.PIN_MESSAGES: {
            if (!tdlib.canPinMessages(chat)) {
              return Lang.getMarkdownString(this, R.string.NoRightAllowPin);
            }
            if (tdlib.chatPublic(args.chatId)) {
              return Lang.getMarkdownString(this, R.string.NoRightAllowPinPublic);
            }
            break;
          }
        }
        break;
      }
      case MODE_ADMIN_PROMOTION: {
        if (!tdlib.cache().senderBot(args.senderId) && isCommonRight(rightId) && TD.checkRight(tdlib.chatPermissions(args.chatId), rightId) && currentValue) {
          int promoteMode = args.member == null ? TD.PROMOTE_MODE_NEW : TD.canPromoteAdmin(args.myStatus, args.member.status);
          if (promoteMode != TD.PROMOTE_MODE_NEW && promoteMode != TD.PROMOTE_MODE_EDIT)
            return null;
          switch (rightId) {
            case RightId.INVITE_USERS:
              return Lang.getMarkdownString(this, R.string.NoRightDisallowInvite);
            case RightId.CHANGE_CHAT_INFO:
              return Lang.getMarkdownString(this, R.string.NoRightDisallowChangeInfo);
            case RightId.PIN_MESSAGES:
              return Lang.getMarkdownString(this, R.string.NoRightDisallowPin);
          }
        }
        break;
      }
      case MODE_RESTRICTION: {
        if (args.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
          return Lang.getString(tdlib.isChannel(Td.getSenderId(args.senderId)) ? R.string.BanChannelHint : R.string.BanChatHint);
        }
        /*if (item.getId() == R.id.btn_date && !TD.checkRight(tdlib.chatPermissions(args.chatId), rightId)) {
          return Lang.getString(R.string.ChatPermissionsRestrictHint);
        }*/
        break;
      }
    }
    return null;
  }

  private static boolean isCommonRight (@RightId int rightId) {
    //noinspection SwitchIntDef
    switch (rightId) {
      case RightId.INVITE_USERS:
      case RightId.CHANGE_CHAT_INFO:
      case RightId.PIN_MESSAGES:
        return true;
    }
    return false;
  }

  private boolean hasAccessToEditRight (ListItem item) {
    Args args = getArgumentsStrict();
    @RightId final int id;
    if (item.getId() == R.id.btn_date) {
      if (args.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
        return false;
      }
      id = -1;
    } else if (item.getId() == R.id.btn_togglePermission) {
      id = item.getIntValue();
    } else {
      throw new UnsupportedOperationException();
    }

    return hasAccessToEditRight(id);
  }

  private boolean hasAccessToEditRight (int id) {
    Args args = getArgumentsStrict();
    if (args.mode == MODE_CHAT_PERMISSIONS) {
      if (tdlib.canRestrictMembers(args.chatId)) {
        TdApi.Chat chat = tdlib.chatStrict(args.chatId);
        switch (id) {
          case RightId.CHANGE_CHAT_INFO:
            if (!tdlib.canChangeInfo(chat) || tdlib.chatPublic(args.chatId)) {
              return false;
            }
            break;
          case RightId.PIN_MESSAGES:
            if (!tdlib.canPinMessages(tdlib.chatStrict(args.chatId)) || tdlib.chatPublic(args.chatId)) {
              return false;
            }
            break;
        }
        return !tdlib.isBroadcastGroup(args.chatId);
      }
      return false;
    }
    if (args.mode == MODE_RESTRICTION && TD.isValidRight(id)) {
      if (args.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR || !TD.checkRight(tdlib.chatPermissions(args.chatId), id))
        return false;
    }
    if (args.mode == MODE_ADMIN_PROMOTION && !tdlib.cache().senderBot(args.senderId) && (id == RightId.INVITE_USERS || id == RightId.CHANGE_CHAT_INFO || id == RightId.PIN_MESSAGES) && TD.checkRight(tdlib.chatPermissions(args.chatId), id)) {
      return false;
    }
    if (!isNewRuleSet()) {
      switch (args.member.status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR: {
          return id == RightId.REMAIN_ANONYMOUS && args.myStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR;
        }
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR: {
          TdApi.ChatMemberStatusAdministrator admin = (TdApi.ChatMemberStatusAdministrator) args.member.status;
          if (!admin.canBeEdited) {
            return false;
          }
          break;
        }
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
          switch (args.myStatus.getConstructor()) {
            case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
              return true;
            case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
              return ((TdApi.ChatMemberStatusAdministrator) args.myStatus).rights.canRestrictMembers;
          }
          break;
        default: {
          return true;
        }
      }
    }
    switch (args.myStatus.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR: {
        TdApi.ChatMemberStatusAdministrator me = (TdApi.ChatMemberStatusAdministrator) args.myStatus;
        if (args.mode == MODE_RESTRICTION) {
          return me.rights.canRestrictMembers;
        } else {
          switch (id) {
            case RightId.ADD_NEW_ADMINS:
              return me.rights.canPromoteMembers;
            case RightId.BAN_USERS:
              return me.rights.canRestrictMembers;
            case RightId.CHANGE_CHAT_INFO:
              return me.rights.canChangeInfo;
            case RightId.DELETE_MESSAGES:
              return me.rights.canDeleteMessages;
            case RightId.EDIT_MESSAGES:
              return me.rights.canEditMessages;
            case RightId.INVITE_USERS:
              return me.rights.canInviteUsers;
            case RightId.PIN_MESSAGES:
              return me.rights.canPinMessages;
            case RightId.MANAGE_VIDEO_CHATS:
              return me.rights.canManageVideoChats;
            case RightId.MANAGE_OR_CREATE_TOPICS:
              return me.rights.canManageTopics;
            case RightId.POST_STORIES:
              return me.rights.canPostStories;
            case RightId.EDIT_STORIES:
              return me.rights.canEditStories;
            case RightId.DELETE_STORIES:
              return me.rights.canDeleteStories;
            case RightId.REMAIN_ANONYMOUS:
              return me.rights.isAnonymous;
            case RightId.SEND_BASIC_MESSAGES:
            case RightId.SEND_AUDIO:
            case RightId.SEND_DOCS:
            case RightId.SEND_PHOTOS:
            case RightId.SEND_VIDEOS:
            case RightId.SEND_VIDEO_NOTES:
            case RightId.SEND_VOICE_NOTES:
            case RightId.SEND_OTHER_MESSAGES:
            case RightId.EMBED_LINKS:
            case RightId.SEND_POLLS:
              return me.rights.canPostMessages;
            case RightId.READ_MESSAGES:
              return true;
            default:
              throw new UnsupportedOperationException(Lang.getResourceEntryName(id));
          }
        }
      }
    }
    return false;
  }

  private int indexOfViewByRightId (@RightId int rightId) {
    return adapter.indexOfViewByIdAndValue(R.id.btn_togglePermission, rightId);
  }

  @StringRes
  private int getDescriptionStringRes () {
    Args args = getArgumentsStrict();
    if (args.mode == MODE_RESTRICTION) {
      if (tdlib.isChannel(args.chatId)) {
        return canViewMessages ? R.string.RestrictXChannel : R.string.BanXChannel;
      } else {
        return canViewMessages ? R.string.RestrictXGroup : R.string.BanXGroup;
      }
    } else {
      boolean value;
      if (targetAdmin != null) {
        value = targetAdmin.rights.canPromoteMembers;
      } else {
        int i = indexOfViewByRightId(RightId.ADD_NEW_ADMINS);
        value = i != -1 && adapter.getItems().get(i).getBoolValue();
      }
      return value ? R.string.XCanAssignAdmins : R.string.XCannotAssignAdmins;
    }
  }

  private void updateDescriptionHint () {
    int i = adapter.indexOfViewById(R.id.description);
    if (i != -1) {
      adapter.getItems().get(i).setString(Lang.getStringBold(getDescriptionStringRes(), tdlib.senderName(getArgumentsStrict().senderId)));
      adapter.notifyItemChanged(i);
    }
  }

  private void showError (CharSequence text) {
    context.tooltipManager()
      .builder(getDoneButton())
      .show(EditRightsController.this,
        tdlib,
        R.drawable.baseline_error_24,
        text
      );
  }

  private void onTransferOwnershipClick () {
    if (isDoneInProgress())
      return;

    long chatId = getArgumentsStrict().chatId;
    TdApi.MessageSender senderId = getArgumentsStrict().senderId;
    final long senderUserId = Td.getSenderUserId(senderId);
    if (senderUserId == 0)
      return;

    boolean isChannel = tdlib.isChannel(chatId);
    CharSequence text;

    Lang.SpanCreator spanCreator = (target, argStart, argEnd, argIndex, needFakeBold) -> {
      if (argIndex == 1) {
        return Lang.newUserSpan(this, senderUserId);
      }
      return null;
    };

    if (isChannel) {
      text = Lang.getMarkdownString(this, R.string.TransferOwnershipAlertChannel, spanCreator, tdlib.chatTitle(chatId), tdlib.senderName(senderId));
    } else {
      text = Lang.getMarkdownString(this, R.string.TransferOwnershipAlertGroup, spanCreator, tdlib.chatTitle(chatId), tdlib.senderName(senderId));
    }

    setDoneInProgress(true);
    
    tdlib.ui().requestTransferOwnership(this, text, new TdlibUi.OwnershipTransferListener() {
      @Override
      public void onOwnershipTransferAbilityChecked (TdApi.Object result) {
        setDoneInProgress(false);
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          showError(TD.toErrorString(result));
        }
      }

      @Override
      public void onOwnershipTransferConfirmed (String password) {
        if (isDoneInProgress())
          return;
        setDoneInProgress(true);
        Runnable act = () -> tdlib.transferOwnership(getArgumentsStrict().chatId, senderUserId, password, (success, error) -> runOnUiThreadOptional(() -> {
          if (success) {
            setDoneInProgress(false);
            navigateBack();
          } else if (error != null) {
            CharSequence errorMessage;
            switch (error.message) {
              case TD.ERROR_USER_CHANNELS_TOO_MUCH:
                errorMessage = Lang.getString(R.string.TransferOwnershipTooMuch);
                break;
              case TD.ERROR_CHANNELS_ADMIN_PUBLIC_TOO_MUCH:
                errorMessage = Lang.getString(R.string.TransferOwnershipTooMuchPublic);
                break;
              case TD.ERROR_CHANNELS_ADMIN_LOCATED_TOO_MUCH:
                errorMessage = Lang.getString(R.string.TransferOwnershipTooMuchLocated);
                break;
              default:
                errorMessage = TD.toErrorString(error);
                break;
            }
            showError(errorMessage);
          }
        }));
        if (ChatId.isBasicGroup(getArgumentsStrict().chatId)) {
          tdlib.upgradeToSupergroup(getArgumentsStrict().chatId, (fromChatId, toChatId, error) -> {
            if (toChatId != 0) {
              getArgumentsStrict().chatId = toChatId;
              act.run();
            } else {
              runOnUiThreadOptional(() -> {
                setDoneInProgress(false);
                if (error != null) {
                  showError(TD.toErrorString(error));
                }
              });
            }
          });
        } else {
          act.run();
        }
      }
    });
  }

  private boolean canTransferOwnership () {
    if (targetAdmin == null || tdlib.cache().senderBot(getArgumentsStrict().senderId) || !targetAdmin.canBeEdited || getArgumentsStrict().mode != MODE_ADMIN_PROMOTION || getArgumentsStrict().myStatus.getConstructor() != TdApi.ChatMemberStatusCreator.CONSTRUCTOR)
      return false;
    final boolean isChannel = tdlib.isChannel(getArgumentsStrict().chatId);
    final TdApi.ChatAdministratorRights rights = targetAdmin.rights;
    if (isChannel) {
      return rights.canChangeInfo && rights.canPostMessages && rights.canEditMessages && rights.canDeleteMessages && rights.canInviteUsers && rights.canManageVideoChats && rights.canPromoteMembers;
    } else {
      return rights.canChangeInfo && rights.canDeleteMessages && rights.canRestrictMembers && rights.canInviteUsers && rights.canPinMessages && rights.canManageVideoChats && rights.canPromoteMembers;
    }
  }

  private void checkTransferOwnership () {
    boolean isChannel = tdlib.isChannel(getArgumentsStrict().chatId);
    boolean canTransfer = canTransferOwnership();
    if (canTransfer) {
      int transferOwnershipIdx = adapter.indexOfViewById(R.id.btn_transferOwnership);
      int i = adapter.indexOfViewById(R.id.btn_dismissAdmin);
      if (i != -1 && transferOwnershipIdx == -1) {
        adapter.getItems().add(i, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.getItems().add(i, newTransferOwnershipItem(isChannel));
        adapter.notifyItemRangeInserted(i, 2);
      } else if (transferOwnershipIdx == -1) {
        int startIndex = adapter.getItemCount() - 1;
        adapter.getItems().addAll(startIndex, Arrays.asList(
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          newTransferOwnershipItem(isChannel),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
        ));
        adapter.notifyItemRangeInserted(startIndex, 3);
      }
    } else {
      int i = adapter.indexOfViewById(R.id.btn_transferOwnership);
      if (i != -1) {
        ListItem nextItem = adapter.getItem(i + 2);
        if (nextItem != null && nextItem.getId() == R.id.btn_dismissAdmin) {
          adapter.removeRange(i, 2);
        } else {
          adapter.removeRange(i - 1, 3);
        }
      }
    }
  }

  private void buildCells () {
    Args args = getArgumentsStrict();

    ArrayList<ListItem> items = new ArrayList<>();

    if (args.senderId != null) {
      TGFoundChat chat;
      if (args.senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
        chat = new TGFoundChat(tdlib, Td.getSenderUserId(args.senderId));
      } else {
        chat = new TGFoundChat(tdlib, null, Td.getSenderId(args.senderId), true);
      }
      chat.setForcedSubtitle(args.member != null && TD.isCreator(args.member.status) ? Lang.getString(R.string.ChannelOwner) : null);
      
      items.add(new ListItem(ListItem.TYPE_CHAT_BETTER).setData(chat));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    items.add(new ListItem(args.senderId != null ? ListItem.TYPE_HEADER : ListItem.TYPE_HEADER_PADDED, 0, 0, args.mode == MODE_CHAT_PERMISSIONS ? R.string.WhatMembersCanDo :
      tdlib.cache().senderBot(args.senderId) ? R.string.WhatThisBotCanDo :
      args.mode == MODE_RESTRICTION ? args.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (tdlib.isChannel(((TdApi.MessageSenderChat) args.senderId).chatId) ? R.string.WhatThisChannelCanDo : R.string.WhatThisGroupCanDo) : R.string.WhatThisUserCanDo : R.string.WhatThisAdminCanDo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    final boolean isChannel = tdlib.isChannel(args.chatId);
    final ArrayList<RightOption> rightIdOptions = new ArrayList<>(12);
    if (args.mode == MODE_CHAT_PERMISSIONS) {
      rightIdOptions.add(new RightOption(RightId.SEND_BASIC_MESSAGES));
      rightIdOptions.add(new RightOption(R.string.RightSendMedia, SEND_MEDIA_RIGHT_IDS));
      rightIdOptions.add(new RightOption(RightId.INVITE_USERS));
      rightIdOptions.add(new RightOption(RightId.PIN_MESSAGES));
      rightIdOptions.add(new RightOption(RightId.CHANGE_CHAT_INFO));
      if (isForum || getValueForId(RightId.MANAGE_OR_CREATE_TOPICS)) {
        rightIdOptions.add(new RightOption(RightId.MANAGE_OR_CREATE_TOPICS));
      }
    } else if (args.mode == MODE_RESTRICTION) {
      if (args.senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
        rightIdOptions.add(new RightOption(RightId.READ_MESSAGES));
      }
      rightIdOptions.add(new RightOption(RightId.SEND_BASIC_MESSAGES));
      rightIdOptions.add(new RightOption(R.string.RightSendMedia, SEND_MEDIA_RIGHT_IDS));
      rightIdOptions.add(new RightOption(RightId.INVITE_USERS));
      rightIdOptions.add(new RightOption(RightId.PIN_MESSAGES));
      rightIdOptions.add(new RightOption(RightId.CHANGE_CHAT_INFO));
      if (isForum || getValueForId(RightId.MANAGE_OR_CREATE_TOPICS)) {
        rightIdOptions.add(new RightOption(RightId.MANAGE_OR_CREATE_TOPICS));
      }
    } else /*args.mode == MODE_ADMIN_PROMOTION*/ if (isChannel) {
      rightIdOptions.add(new RightOption(RightId.CHANGE_CHAT_INFO));
      rightIdOptions.add(new RightOption(R.string.RightMessages, MANAGE_CHANNEL_POSTS_IDS));
      rightIdOptions.add(new RightOption(RightId.INVITE_USERS));
      rightIdOptions.add(new RightOption(RightId.MANAGE_VIDEO_CHATS));
      rightIdOptions.add(new RightOption(RightId.ADD_NEW_ADMINS));
      rightIdOptions.add(new RightOption(R.string.RightStories, MANAGE_STORIES_RIGHT_IDS));
    } else {
      rightIdOptions.add(new RightOption(RightId.CHANGE_CHAT_INFO));
      rightIdOptions.add(new RightOption(RightId.DELETE_MESSAGES));
      rightIdOptions.add(new RightOption(RightId.BAN_USERS));
      rightIdOptions.add(new RightOption(RightId.INVITE_USERS));
      rightIdOptions.add(new RightOption(RightId.PIN_MESSAGES));
      rightIdOptions.add(new RightOption(RightId.MANAGE_VIDEO_CHATS));
      if (isForum || getValueForId(RightId.MANAGE_OR_CREATE_TOPICS)) {
        rightIdOptions.add(new RightOption(RightId.MANAGE_OR_CREATE_TOPICS));
      }
      rightIdOptions.add(new RightOption(RightId.REMAIN_ANONYMOUS));
      rightIdOptions.add(new RightOption(RightId.ADD_NEW_ADMINS));
    }

    boolean first = true;
    int viewType = args.mode == MODE_CHAT_PERMISSIONS ? ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER : ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE;
    for (RightOption option: rightIdOptions) {
      if (first) {
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      if (option.groupRightIds == null) {
        final @RightId int rightId = option.singleRightId;
        items.add(new ListItem(viewType, R.id.btn_togglePermission, iconForRightId(rightId), stringForRightId(rightId, isChannel))
          .setIntValue(rightId).setBoolValue(getValueForId(rightId)));
      } else {
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER,
          R.id.btn_togglePermissionGroup, R.drawable.round_keyboard_arrow_right_24, option.name
        ).setIntValue(option.groupRightIds[0]).setData(option).setBoolValue(getRightsGroupEnabledCount(option.groupRightIds) > 0));
      }
    }

    if (args.mode == MODE_RESTRICTION) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_date, 0, R.string.BlockFor));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (args.mode != MODE_CHAT_PERMISSIONS) {
      if (!isNewRuleSet()) {
        if (!TD.isCreator(args.member.status)) {
          switch (args.member.status.getConstructor()) {
            case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
            case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
              CharSequence desc = TD.getMemberDescription(this, args.member, true);
              if (!StringUtils.isEmpty(desc)) {
                items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, desc, false));
              }
              break;
          }
        }
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, Lang.getStringBold(getDescriptionStringRes(), tdlib.senderName(args.senderId)), false));
      }
    }

    if (canViewOrEditCustomTitle()) {
      addEditTitleCells(items);
    }

    boolean canTransferOwnership = canTransferOwnership();
    boolean canDismissAdmin = canDismissAdmin();

    if (canTransferOwnership && canDismissAdmin) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(newTransferOwnershipItem(isChannel));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_dismissAdmin, 0, R.string.DismissAdmin).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else if (canTransferOwnership) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(newTransferOwnershipItem(isChannel));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else if (canDismissAdmin) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_dismissAdmin, 0, R.string.DismissAdmin).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (canUnbanUser()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_unblockSender, 0, args.member.status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR ?
          tdlib.cache().senderBot(args.member.memberId) ? R.string.UnbanMemberBot :
          args.member.memberId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (tdlib.isChannel(Td.getSenderId(args.member.memberId)) ? R.string.UnbanMemberChannel : R.string.UnbanMemberGroup) :
          R.string.UnbanMember :
          R.string.RemoveRestrictions
        ).setTextColorId(ColorId.textNegative)
      );
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_ZERO_VIEW));

    adapter.setItems(items, false);
  }

  private static ListItem newTransferOwnershipItem (boolean isChannel) {
    return new ListItem(ListItem.TYPE_SETTING, R.id.btn_transferOwnership, 0, isChannel ? R.string.TransferOwnershipChannel : R.string.TransferOwnershipGroup).setTextColorId(ColorId.textNegative);
  }

  private void addEditTitleCells (List<ListItem> items) {
    Args args = getArgumentsStrict();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.CustomTitle));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(customTitle = new ListItem(ListItem.TYPE_EDITTEXT_POLL_OPTION, R.id.input_customTitle, 0, 0, false).setStringValue(args.member != null ? Td.getCustomTitle(args.member.status) : null));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(R.string.CustomTitleHint, Lang.getString(args.member != null && TD.isCreator(args.member.status) ? R.string.message_ownerSign : R.string.message_adminSignPlain)), false));
    adapter.setLockFocusOn(this, !args.noFocusLock && args.member != null && TD.isCreator(args.member.status) && TD.isCreator(args.myStatus));
  }

  private boolean canUnbanUser () {
    Args args = getArgumentsStrict();

    if (isNewRuleSet()) {
      return false;
    }

    if (args.mode != MODE_RESTRICTION || args.member == null || (args.member.status.getConstructor() != TdApi.ChatMemberStatusBanned.CONSTRUCTOR && args.member.status.getConstructor() != TdApi.ChatMemberStatusRestricted.CONSTRUCTOR)) {
      return false;
    }

    TdApi.ChatMemberStatus status = args.myStatus;
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canRestrictMembers;
    }
    return false;
  }

  private boolean canDismissAdmin () {
    Args args = getArgumentsStrict();
    if (isNewRuleSet()) {
      return false;
    }

    if (args.mode != MODE_ADMIN_PROMOTION || args.member == null || args.member.status.getConstructor() != TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR || !((TdApi.ChatMemberStatusAdministrator) args.member.status).canBeEdited) {
      return false;
    }
    TdApi.ChatMemberStatus status = args.myStatus;
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        return true;
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.ChatMemberStatusAdministrator) status).rights.canPromoteMembers;
    }
    return false;
  }

  @Override
  protected boolean needShowAnimationDelay () {
    return false;
  }

  private boolean hasAnyChanges () {
    Args args = getArgumentsStrict();
    if (isNewRuleSet()) {
      return false;
    }
    if (args.mode == MODE_CHAT_PERMISSIONS) {
      return !Td.equalsTo(tdlib.chatPermissions(args.chatId), targetRestrict.permissions);
    } else if (args.mode == MODE_RESTRICTION) {
      boolean couldViewMessages = args.member.status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR;
      if (canViewMessages != couldViewMessages) {
        return true;
      } else if (!couldViewMessages) {
        return ((TdApi.ChatMemberStatusBanned) args.member.status).bannedUntilDate != targetRestrict.restrictedUntilDate;
      }

      TdApi.ChatMemberStatusRestricted old = (TdApi.ChatMemberStatusRestricted) args.member.status;
      return old.restrictedUntilDate != targetRestrict.restrictedUntilDate  || !Td.equalsTo(targetRestrict.permissions, old.permissions, tdlib.chatPermissions(args.chatId));
    } else if (customTitle != null && !StringUtils.equalsOrBothEmpty(Td.getCustomTitle(args.member.status), customTitle.getStringValue())) {
      return true;
    } else if (args.member.status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
      TdApi.ChatMemberStatusAdministrator old = (TdApi.ChatMemberStatusAdministrator) args.member.status;
      return !Td.equalsTo(old, targetAdmin);
    } else if (args.member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
      return ((TdApi.ChatMemberStatusCreator) args.member.status).isAnonymous != targetAdmin.rights.isAnonymous;
    }
    return false;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasAnyChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }
    return false;
  }

  private void setCanViewMessages (boolean value) {
    if (canViewMessages != value) {
      canViewMessages = value;
      updateDescriptionHint();
      adapter.updateValuedSettingById(R.id.btn_date);
    }
  }

  /*private static @IdRes int idResForRightId (@RightId int id) {
    switch (id) {
      case RightId.READ_MESSAGES: return R.id.right_readMessages;
      case RightId.SEND_BASIC_MESSAGES: return R.id.right_sendMessages;
      case RightId.SEND_AUDIO: return R.id.right_sendAudio;
      case RightId.SEND_DOCS: return R.id.right_sendDocument;
      case RightId.SEND_PHOTOS: return R.id.right_sendPhoto;
      case RightId.SEND_VIDEOS: return R.id.right_sendVideo;
      case RightId.SEND_VOICE_NOTES: return R.id.right_sendVoiceNote;
      case RightId.SEND_VIDEO_NOTES: return R.id.right_sendVideoNote;
      case RightId.SEND_OTHER_MESSAGES: return R.id.right_sendStickersAndGifs;
      case RightId.SEND_POLLS: return R.id.right_sendPolls;
      case RightId.EMBED_LINKS: return R.id.right_embedLinks;
      case RightId.CHANGE_CHAT_INFO: return R.id.right_changeChatInfo;
      case RightId.EDIT_MESSAGES: return R.id.right_editMessages;
      case RightId.DELETE_MESSAGES: return R.id.right_deleteMessages;
      case RightId.BAN_USERS: return R.id.right_banUsers;
      case RightId.INVITE_USERS: return R.id.right_inviteUsers;
      case RightId.PIN_MESSAGES: return R.id.right_pinMessages;
      case RightId.MANAGE_VIDEO_CHATS: return R.id.right_manageVideoChats;
      case RightId.ADD_NEW_ADMINS: return R.id.right_addNewAdmins;
      case RightId.REMAIN_ANONYMOUS: return R.id.right_remainAnonymous;
    }
    throw new IllegalArgumentException(Integer.toString(id));
  }
  
  private static @RightId int rightIdForIdRes (@IdRes int id) {
    if (id == R.id.right_readMessages) {
      return RightId.READ_MESSAGES;
    } else if (id == R.id.right_sendMessages) {
      return RightId.SEND_BASIC_MESSAGES;
    } else if (id == R.id.right_sendAudio) {
      return RightId.SEND_AUDIO;
    } else if (id == R.id.right_sendDocument) {
      return RightId.SEND_DOCS;
    } else if (id == R.id.right_sendPhoto) {
      return RightId.SEND_PHOTOS;
    } else if (id == R.id.right_sendVideo) {
      return RightId.SEND_VIDEOS;
    } else if (id == R.id.right_sendVoiceNote) {
      return RightId.SEND_VOICE_NOTES;
    } else if (id == R.id.right_sendVideoNote) {
      return RightId.SEND_VIDEO_NOTES;
    } else if (id == R.id.right_sendStickersAndGifs) {
      return RightId.SEND_OTHER_MESSAGES;
    } else if (id == R.id.right_sendPolls) {
      return RightId.SEND_POLLS;
    } else if (id == R.id.right_embedLinks) {
      return RightId.EMBED_LINKS;
    } else if (id == R.id.right_changeChatInfo) {
      return RightId.CHANGE_CHAT_INFO;
    } else if (id == R.id.right_editMessages) {
      return RightId.EDIT_MESSAGES;
    } else if (id == R.id.right_deleteMessages) {
      return RightId.DELETE_MESSAGES;
    } else if (id == R.id.right_banUsers) {
      return RightId.BAN_USERS;
    } else if (id == R.id.right_inviteUsers) {
      return RightId.INVITE_USERS;
    } else if (id == R.id.right_pinMessages) {
      return RightId.PIN_MESSAGES;
    } else if (id == R.id.right_manageVideoChats) {
      return RightId.MANAGE_VIDEO_CHATS;
    } else if (id == R.id.right_addNewAdmins) {
      return RightId.ADD_NEW_ADMINS;
    } else if (id == R.id.right_remainAnonymous) {
      return RightId.REMAIN_ANONYMOUS;
    }
    throw new IllegalArgumentException(Integer.toString(id));
  }*/

  private void toggleValueForRightId (@RightId int id) {
    final boolean newValue = !getValueForId(id);
    setValueForRightId(id, newValue);
  }

  private void setValueForRightId (@RightId int id, boolean newValue) {
    if (getValueForId(id) == newValue) {
      return;
    }

    switch (id) {
      case RightId.READ_MESSAGES: {
        setCanViewMessages(newValue);
        break;
      }
      case RightId.SEND_BASIC_MESSAGES: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.rights.canPostMessages = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          targetRestrict.permissions.canSendBasicMessages = newValue;
        }
        break;
      }
      case RightId.SEND_AUDIO:
      case RightId.SEND_DOCS:
      case RightId.SEND_PHOTOS:
      case RightId.SEND_VIDEOS:
      case RightId.SEND_POLLS:
      case RightId.SEND_VOICE_NOTES:
      case RightId.SEND_VIDEO_NOTES:
      case RightId.SEND_OTHER_MESSAGES: {
        setCanViewMessages(canViewMessages || newValue);
        // targetRestrict.permissions.canSendBasicMessages = targetRestrict.permissions.canSendBasicMessages || newValue;
        //noinspection SwitchIntDef
        switch (id) {
          case RightId.SEND_AUDIO:
            targetRestrict.permissions.canSendAudios = newValue;
            break;
          case RightId.SEND_DOCS:
            targetRestrict.permissions.canSendDocuments = newValue;
            break;
          case RightId.SEND_PHOTOS:
            targetRestrict.permissions.canSendPhotos = newValue;
            break;
          case RightId.SEND_VIDEOS:
            targetRestrict.permissions.canSendVideos = newValue;
            break;
          case RightId.SEND_POLLS:
            targetRestrict.permissions.canSendPolls = newValue;
            break;
          case RightId.SEND_VOICE_NOTES:
            targetRestrict.permissions.canSendVoiceNotes = newValue;
            break;
          case RightId.SEND_VIDEO_NOTES:
            targetRestrict.permissions.canSendVideoNotes = newValue;
            break;
          case RightId.SEND_OTHER_MESSAGES:
            targetRestrict.permissions.canSendOtherMessages = newValue;
            break;
          default:
            throw new UnsupportedOperationException(Lang.getResourceEntryName(id));
        }
        break;
      }
      case RightId.EMBED_LINKS: {
        setCanViewMessages(canViewMessages || newValue);
        targetRestrict.permissions.canSendBasicMessages = targetRestrict.permissions.canSendBasicMessages || newValue;
        targetRestrict.permissions.canAddWebPagePreviews = newValue;
        break;
      }
      case RightId.CHANGE_CHAT_INFO: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.rights.canChangeInfo = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          targetRestrict.permissions.canChangeInfo = newValue;
        }
        break;
      }
      case RightId.INVITE_USERS: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.rights.canInviteUsers = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          targetRestrict.permissions.canInviteUsers = newValue;
        }
        break;
      }
      case RightId.PIN_MESSAGES: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.rights.canPinMessages = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          // FIXME? targetRestrict.permissions.canSendBasicMessages = targetRestrict.permissions.canSendBasicMessages || newValue;
          targetRestrict.permissions.canPinMessages = newValue;
        }
        break;
      }
      case RightId.ADD_NEW_ADMINS:
        targetAdmin.rights.canPromoteMembers = newValue;
        updateDescriptionHint();
        break;
      case RightId.BAN_USERS:
        targetAdmin.rights.canRestrictMembers = newValue;
        break;
      case RightId.MANAGE_VIDEO_CHATS:
        targetAdmin.rights.canManageVideoChats = newValue;
        break;
      case RightId.MANAGE_OR_CREATE_TOPICS:
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.rights.canManageTopics = newValue;
        } else {
          targetRestrict.permissions.canManageTopics = newValue;
        }
        break;
      case RightId.POST_STORIES:
        targetAdmin.rights.canPostStories = newValue;
        break;
      case RightId.EDIT_STORIES:
        targetAdmin.rights.canEditStories = newValue;
        break;
      case RightId.DELETE_STORIES:
        targetAdmin.rights.canDeleteStories = newValue;
        break;
      case RightId.REMAIN_ANONYMOUS:
        targetAdmin.rights.isAnonymous = newValue;
        break;
      case RightId.DELETE_MESSAGES:
        targetAdmin.rights.canDeleteMessages = newValue;
        break;
      case RightId.EDIT_MESSAGES:
        targetAdmin.rights.canEditMessages = newValue;
        break;
      default: // Unreachable
        throw new IllegalArgumentException(Lang.getResourceEntryName(id));
    }
    if (getArgumentsStrict().mode == MODE_CHAT_PERMISSIONS || getArgumentsStrict().mode == MODE_RESTRICTION) {
      targetRestrict.isMember = canViewMessages;
      targetRestrict.permissions.canSendBasicMessages = getValueForId(RightId.SEND_BASIC_MESSAGES);
      targetRestrict.permissions.canAddWebPagePreviews = getValueForId(RightId.EMBED_LINKS);
      targetRestrict.permissions.canSendAudios = getValueForId(RightId.SEND_AUDIO);
      targetRestrict.permissions.canSendDocuments = getValueForId(RightId.SEND_DOCS);
      targetRestrict.permissions.canSendPhotos = getValueForId(RightId.SEND_PHOTOS);
      targetRestrict.permissions.canSendVideos = getValueForId(RightId.SEND_VIDEOS);
      targetRestrict.permissions.canSendVoiceNotes = getValueForId(RightId.SEND_VOICE_NOTES);
      targetRestrict.permissions.canSendVideoNotes = getValueForId(RightId.SEND_VIDEO_NOTES);
      targetRestrict.permissions.canSendOtherMessages = getValueForId(RightId.SEND_OTHER_MESSAGES);
      targetRestrict.permissions.canSendPolls = getValueForId(RightId.SEND_POLLS);
    }
    updateValues();
    checkDoneButton();
    checkTransferOwnership();
  }

  private void checkDoneButton () {
    if (!isNewRuleSet()) {
      setDoneVisible(hasAnyChanges());
    }
  }

  @SuppressWarnings("WrongConstant")
  private void updateValues () {
    int i = 0;
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.btn_togglePermission) {
        @RightId int rightId = item.getIntValue();
        boolean value = getValueForId(rightId);
        if (value != item.getBoolValue()) {
          item.setBoolValue(value);
          adapter.updateValuedSettingByPosition(i);
        }
      } else if (item.getId() == R.id.btn_togglePermissionGroup) {
        final RightOption option = (RightOption) item.getData();
        if (option != null && option.groupRightIds != null) {
          final boolean value = getRightsGroupEnabledCount(option.groupRightIds) > 0;
          if (value != item.getBoolValue()) {
            item.setBoolValue(value);
            adapter.updateValuedSettingByPosition(i);
          }
        }
      }
      i++;
    }
    adapter.updateAllValuedSettingsById(R.id.btn_togglePermissionGroup);
  }

  private boolean checkDefaultRight (@RightId int id) {
    return !tdlib.cache().senderBot(getArgumentsStrict().senderId) && TD.checkRight(tdlib.chatPermissions(getArgumentsStrict().chatId), id);
  }

  private boolean getValueForId (@RightId int id) {
    if (getArgumentsStrict().mode == MODE_RESTRICTION) {
      if (getArgumentsStrict().senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR && id == RightId.READ_MESSAGES) {
        return true;
      }
      if (!TD.checkRight(tdlib.chatPermissions(getArgumentsStrict().chatId), id)) {
        return false;
      }
    }
    switch (id) {
      case RightId.READ_MESSAGES:
        return canViewMessages;
      case RightId.SEND_BASIC_MESSAGES:
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          return targetAdmin.rights.canPostMessages;
        } else {
          return canViewMessages && targetRestrict.permissions.canSendBasicMessages;
        }
      case RightId.EMBED_LINKS:
        return canViewMessages && targetRestrict.permissions.canSendBasicMessages && targetRestrict.permissions.canAddWebPagePreviews;
      case RightId.SEND_AUDIO:
        return canViewMessages && targetRestrict.permissions.canSendAudios;
      case RightId.SEND_DOCS:
        return canViewMessages && targetRestrict.permissions.canSendDocuments;
      case RightId.SEND_PHOTOS:
        return canViewMessages && targetRestrict.permissions.canSendPhotos;
      case RightId.SEND_VIDEOS:
        return canViewMessages && targetRestrict.permissions.canSendVideos;
      case RightId.SEND_VOICE_NOTES:
        return canViewMessages && targetRestrict.permissions.canSendVoiceNotes;
      case RightId.SEND_VIDEO_NOTES:
        return canViewMessages && targetRestrict.permissions.canSendVideoNotes;
      case RightId.SEND_OTHER_MESSAGES:
        return canViewMessages && targetRestrict.permissions.canSendOtherMessages;
      case RightId.SEND_POLLS:
        return canViewMessages && targetRestrict.permissions.canSendPolls;
      case RightId.CHANGE_CHAT_INFO:
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          return targetAdmin.rights.canChangeInfo || checkDefaultRight(id);
        } else {
          return canViewMessages && targetRestrict.permissions.canChangeInfo;
        }
      case RightId.INVITE_USERS:
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          return targetAdmin.rights.canInviteUsers || checkDefaultRight(id);
        } else {
          return canViewMessages && targetRestrict.permissions.canInviteUsers;
        }
      case RightId.PIN_MESSAGES:
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          return targetAdmin.rights.canPinMessages || checkDefaultRight(id);
        } else {
          return canViewMessages && targetRestrict.permissions.canPinMessages; // FIXME? targetRestrict.permissions.canSendBasicMessages
        }
      case RightId.DELETE_MESSAGES:
        return targetAdmin.rights.canDeleteMessages;
      case RightId.BAN_USERS:
        return targetAdmin.rights.canRestrictMembers;
      case RightId.ADD_NEW_ADMINS:
        return targetAdmin.rights.canPromoteMembers;
      case RightId.MANAGE_VIDEO_CHATS:
        return targetAdmin.rights.canManageVideoChats;
      case RightId.MANAGE_OR_CREATE_TOPICS:
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          return targetAdmin.rights.canManageTopics;
        } else {
          return canViewMessages && targetRestrict.permissions.canManageTopics;
        }
      case RightId.POST_STORIES:
        return targetAdmin.rights.canPostStories;
      case RightId.EDIT_STORIES:
        return targetAdmin.rights.canEditStories;
      case RightId.DELETE_STORIES:
        return targetAdmin.rights.canDeleteStories;
      case RightId.REMAIN_ANONYMOUS:
        return targetAdmin.rights.isAnonymous;
      case RightId.EDIT_MESSAGES:
        return targetAdmin.rights.canEditMessages;
    }
    throw new IllegalArgumentException(Lang.getResourceEntryName(id));
  }

  private @StringRes int stringForRightId (@RightId int id, boolean isChannel) {
    switch (id) {
      case RightId.READ_MESSAGES:
        return R.string.UserRestrictionsRead;
      case RightId.SEND_BASIC_MESSAGES:
        return getArgumentsStrict().mode == MODE_ADMIN_PROMOTION ? R.string.EditAdminPostMessages : R.string.UserRestrictionsSend;
      case RightId.SEND_AUDIO:
        return R.string.RightSendAudio;
      case RightId.SEND_DOCS:
        return R.string.RightSendDocs;
      case RightId.SEND_PHOTOS:
        return R.string.RightSendPhoto;
      case RightId.SEND_VIDEOS:
        return R.string.RightSendVideo;
      case RightId.SEND_VOICE_NOTES:
        return R.string.RightSendVoiceNote;
      case RightId.SEND_VIDEO_NOTES:
        return R.string.RightSendVideoNote;
      case RightId.SEND_OTHER_MESSAGES:
        return R.string.UserRestrictionsSendStickers;
      case RightId.SEND_POLLS:
        return R.string.UserRestrictionsSendPolls;
      case RightId.EMBED_LINKS:
        return R.string.UserRestrictionsEmbedLinks;
      case RightId.CHANGE_CHAT_INFO:
        return isChannel ? R.string.RightChangeChannelInfo : R.string.RightChangeGroupInfo;
      case RightId.DELETE_MESSAGES:
        return R.string.EditAdminGroupDeleteMessages;
      case RightId.BAN_USERS:
        return R.string.RightBanUsers;
      case RightId.INVITE_USERS:
        return R.string.RightInviteViaLink;
      case RightId.PIN_MESSAGES:
        return R.string.RightPinMessages;
      case RightId.ADD_NEW_ADMINS:
        return R.string.RightAddNewAdmins;
      case RightId.EDIT_MESSAGES:
        return R.string.RightEditMessages;
      case RightId.MANAGE_VIDEO_CHATS:
        return isChannel ? R.string.RightLiveStreams : R.string.RightVoiceChats;
      case RightId.MANAGE_OR_CREATE_TOPICS:
        return getArgumentsStrict().mode == MODE_ADMIN_PROMOTION ? R.string.RightTopics : R.string.RightTopicsCreate;
      case RightId.POST_STORIES:
        return R.string.RightStoriesPost;
      case RightId.EDIT_STORIES:
        return R.string.RightStoriesEdit;
      case RightId.DELETE_STORIES:
        return R.string.RightStoriesDelete;
      case RightId.REMAIN_ANONYMOUS:
        return R.string.RightAnonymous;
    }
    throw new UnsupportedOperationException(Lang.getResourceEntryName(id));
  }

  private @DrawableRes int iconForRightId (@RightId int id) {
    switch (id) {
      case RightId.READ_MESSAGES:
        return R.drawable.baseline_eye_off_24;
      case RightId.SEND_BASIC_MESSAGES:
        return R.drawable.baseline_format_text_24;
      case RightId.CHANGE_CHAT_INFO:
      case RightId.EDIT_MESSAGES:
      case RightId.EDIT_STORIES:
        return R.drawable.baseline_edit_24;
      case RightId.DELETE_STORIES:
      case RightId.DELETE_MESSAGES:
        return R.drawable.baseline_delete_24;
      case RightId.BAN_USERS:
        return R.drawable.baseline_block_24;
      case RightId.INVITE_USERS:
        return R.drawable.baseline_person_add_24;
      case RightId.ADD_NEW_ADMINS:
        return R.drawable.baseline_stars_24;
      case RightId.PIN_MESSAGES:
        return R.drawable.deproko_baseline_pin_24;
      case RightId.REMAIN_ANONYMOUS:
        return R.drawable.dot_baseline_acc_anon_24;

      case RightId.MANAGE_VIDEO_CHATS:
        return R.drawable.baseline_video_chat_24;
      case RightId.MANAGE_OR_CREATE_TOPICS:
        return R.drawable.baseline_format_list_bulleted_type_24;

      case RightId.POST_STORIES:  // todo


      case RightId.SEND_AUDIO:
      case RightId.SEND_DOCS:
      case RightId.SEND_PHOTOS:
      case RightId.SEND_VIDEOS:
      case RightId.SEND_VOICE_NOTES:
      case RightId.SEND_VIDEO_NOTES:
      case RightId.SEND_OTHER_MESSAGES:
      case RightId.SEND_POLLS:
      case RightId.EMBED_LINKS:
      default:
        return 0;
    }
  }

  private boolean canViewOrEditCustomTitle () {
    Args args = getArgumentsStrict();
    if (tdlib.isChannel(args.chatId))
      return false;
    if (args.mode == MODE_ADMIN_PROMOTION) {
      int promoteMode = args.member == null ? TD.PROMOTE_MODE_NEW : TD.canPromoteAdmin(args.myStatus, args.member.status);
      switch (promoteMode) {
        case TD.PROMOTE_MODE_NEW:
        case TD.PROMOTE_MODE_EDIT:
          return true;
        default:
          return !StringUtils.isEmpty(Td.getCustomTitle(args.member.status)) || (TD.isCreator(args.myStatus)) || (Config.CAN_CHANGE_SELF_ADMIN_CUSTOM_TITLE && tdlib.isSelfSender(args.member.memberId));
      }
    }
    return false;
  }

  @Override
  public CharSequence getName () {
    Args args = getArgumentsStrict();
    switch (args.mode) {
      case MODE_RESTRICTION:
        if (getArgumentsStrict().senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
          return Lang.getString(tdlib.isChannel(Td.getSenderId(getArgumentsStrict().senderId)) ? R.string.ChannelRestrictions : R.string.GroupRestrictions);
        } else {
          return Lang.getString(R.string.UserRestrictions);
        }
      case MODE_ADMIN_PROMOTION: {
        int promoteMode = args.member == null ? TD.PROMOTE_MODE_NEW : TD.canPromoteAdmin(args.myStatus, args.member.status);
        switch (promoteMode) {
          case TD.PROMOTE_MODE_NEW:
            return Lang.getString(R.string.SetAsAdmin);
          case TD.PROMOTE_MODE_EDIT:
            return Lang.getString(R.string.EditAdmin);
          default:
            return Lang.getString(R.string.AdminRights);
        }
      }
      case MODE_CHAT_PERMISSIONS:
        return Lang.getString(R.string.ChatPermissions);
    }
    throw new AssertionError();
  }

  private boolean hasAccessToEditRightsGroup (int[] rights) {
    for (int right : rights) {
      if (hasAccessToEditRight(right)) {
        return true;
      }
    }
    return false;
  }

  private void toggleRightsGroup (SettingView view, int[] rights) {
    final int index = adapter.indexOfViewByIdAndValue(R.id.btn_togglePermissionGroup, rights[0]);
    final ListItem item = adapter.getItem(index);
    if (item == null) {
      return;
    }

    Set<CharSequence> errorHints = null;
    int editedCount = 0;
    boolean newValue = !item.getBoolValue();
    for (int rightId : rights) {
      boolean canEdit = hasAccessToEditRight(rightId);
      if (canEdit) {
        if (getValueForId(rightId) != newValue) {
          editedCount++;
        }
        setValueForRightId(rightId, newValue);
      } else {
        CharSequence text = getHintForToggleUnavailability(item);
        if (text != null) {
          if (errorHints == null) {
            errorHints = new HashSet<>();
          }
          errorHints.add(text);
        }
      }
    }
    if (editedCount == 0 && errorHints != null) {
      CharSequence[] hints = errorHints.toArray(new CharSequence[0]);
      CharSequence hint = TextUtils.join("\n", hints);
      context().tooltipManager()
        .builder(((SettingView) view).getToggler())
        .show(this, tdlib, R.drawable.baseline_info_24, hint);
    }
  }

  private int getRightsGroupEnabledCount (int[] rights) {
    int res = 0;
    for (int right : rights) {
      res += getValueForId(right) ? 1 : 0;
    }

    return res;
  }

  private void toggleRightsGroupVisibility (int[] rights) {
    final int index = adapter.indexOfViewByIdAndValue(R.id.btn_togglePermissionGroup, rights[0]);
    if (index == -1) return;

    final boolean sendMediaGroupIsVisible = indexOfViewByRightId(rights[0]) != -1;

    if (!sendMediaGroupIsVisible) {
      final Args args = getArgumentsStrict();
      final boolean isChannel = tdlib.isChannel(args.chatId);
      final int viewType = args.mode == MODE_CHAT_PERMISSIONS ?
        ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER :
        ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE;

      ArrayList<ListItem> items = new ArrayList<>(rights.length * 2);
      for (@RightId int rightId : rights) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(viewType,
            R.id.btn_togglePermission, 0,
            stringForRightId(rightId, isChannel)
          ).setIntValue(rightId)
            .setBoolValue(getValueForId(rightId))
        );
      }
      adapter.addItems(index + 1, items.toArray(new ListItem[0]));
    } else {
      adapter.removeRange(index + 1, rights.length * 2);
    }
    adapter.updateValuedSettingByPosition(index);
  }

  public static final int[] SEND_MEDIA_RIGHT_IDS = {
    RightId.SEND_PHOTOS,
    RightId.SEND_VIDEOS,
    RightId.SEND_AUDIO,
    RightId.SEND_DOCS,
    RightId.SEND_VOICE_NOTES,
    RightId.SEND_VIDEO_NOTES,
    RightId.SEND_OTHER_MESSAGES,
    RightId.SEND_POLLS,
    RightId.EMBED_LINKS,
  };

  private static final int[] MANAGE_CHANNEL_POSTS_IDS = {
    RightId.SEND_BASIC_MESSAGES,
    RightId.EDIT_MESSAGES,
    RightId.DELETE_MESSAGES
  };

  private static final int[] MANAGE_STORIES_RIGHT_IDS = {
    RightId.POST_STORIES,
    RightId.EDIT_STORIES,
    RightId.DELETE_STORIES
  };

  private static class RightOption {
    public final @RightId int singleRightId;
    public final @Nullable @RightId int[] groupRightIds;
    public final @StringRes int name;

    public RightOption (@RightId int singleRightId) {
      this.singleRightId = singleRightId;
      this.groupRightIds = null;
      this.name = -1;
    }

    public RightOption (@StringRes int name, @NonNull @RightId int[] groupRightIds) {
      this.groupRightIds = groupRightIds;
      this.singleRightId = groupRightIds[0];
      this.name = name;
    }
  }
}
