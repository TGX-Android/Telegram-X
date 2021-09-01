package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
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
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

/**
 * Date: 8/19/17
 * Author: default
 */

public class EditRightsController extends EditBaseController<EditRightsController.Args> implements View.OnClickListener, TdlibCache.BasicGroupDataChangeListener {
  public static final int MODE_ADMIN_PROMOTION = 1;
  public static final int MODE_RESTRICTION = 2;
  public static final int MODE_CHAT_PERMISSIONS = 3;

  public static class Args {
    public long chatId;
    public final int userId;
    public final TdApi.ChatMemberStatus myStatus;
    public final TdApi.ChatMember member;
    public final int mode;
    public int forwardLimit;

    public Args (long chatId, int userId, TdApi.ChatMemberStatus myStatus, TdApi.ChatMember member, int mode, int forwardLimit) {
      this.chatId = chatId;
      this.userId = userId;
      this.myStatus = myStatus;
      this.member = member;
      this.mode = mode;
      this.forwardLimit = forwardLimit;
    }

    public Args (long chatId, int userId, boolean isRestrict, @NonNull TdApi.ChatMemberStatus myStatus, @Nullable TdApi.ChatMember member) {
      this.chatId = chatId;
      this.userId = userId;
      this.mode = isRestrict ? MODE_RESTRICTION : MODE_ADMIN_PROMOTION;
      this.myStatus = myStatus;
      this.member = member;
    }

    public Args (long chatId) {
      this.chatId = chatId;
      this.userId = 0;
      this.mode = MODE_CHAT_PERMISSIONS;
      this.myStatus = null;
      this.member = null;
    }

    public Args forwardLimit (int forwardLimit) {
      this.forwardLimit = forwardLimit;
      return this;
    }
  }

  public EditRightsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.ChatMemberStatusAdministrator targetAdmin;
  private TdApi.ChatMemberStatusRestricted targetRestrict;
  private boolean canViewMessages;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);

    canViewMessages = tdlib.isChannel(args.chatId);

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
            targetAdmin = new TdApi.ChatMemberStatusAdministrator(creator.customTitle, false, true, true, true, true, true, true, true, true, true, true, creator.isAnonymous);
          } else if (args.member.status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
            TdApi.ChatMemberStatusAdministrator admin = (TdApi.ChatMemberStatusAdministrator) args.member.status;
            targetAdmin = new TdApi.ChatMemberStatusAdministrator(admin.customTitle, admin.canBeEdited, admin.canManageChat, admin.canChangeInfo, admin.canPostMessages, admin.canEditMessages, admin.canDeleteMessages, admin.canInviteUsers, admin.canRestrictMembers, admin.canPinMessages, admin.canPromoteMembers, admin.canManageVoiceChats, admin.isAnonymous);
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
        return new TdApi.ChatMemberStatusAdministrator(null, true, me.canManageChat, me.canChangeInfo, me.canPostMessages, me.canEditMessages, me.canDeleteMessages, me.canInviteUsers, me.canRestrictMembers, me.canPinMessages, false, me.canManageVoiceChats, me.isAnonymous);
      }
    }
    return new TdApi.ChatMemberStatusAdministrator(null, true, true, true, true, true, true, true, true, true, false, true, false);
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

    switch (item.getViewType()) {
      case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER: {
        @RightId final int rightId = item.getId();
        boolean canEdit = hasAccessToEditRight(rightId);
        if (canEdit) {
          toggleValueForId(rightId);
        } else {
          CharSequence text = getHintForToggleUnavailability(rightId, item.getBoolValue());
          if (text != null) {
            context().tooltipManager()
              .builder(((SettingView) view).getToggler())
              .show(this, tdlib, R.drawable.baseline_info_24, text);
          }
        }
        break;
      }
      case ListItem.TYPE_CHAT_BETTER: {
        tdlib.ui().openPrivateProfile(this, ((TGFoundChat) item.getData()).getUserId(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(view)));
        break;
      }
      default: {
        switch (item.getId()) {
          case R.id.btn_transferOwnership: {
            if (ChatId.isBasicGroup(getArgumentsStrict().chatId)) {
              showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), this::onTransferOwnershipClick);
            } else {
              onTransferOwnershipClick();
            }
            break;
          }
          case R.id.btn_unblockUser: {

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

            if (targetRestrict.isMember) {
              showOptions(Lang.getStringBold(R.string.QUnblockX, tdlib.cache().userName(args.userId)), new int[]{R.id.btn_blockUser, R.id.btn_cancel}, new String[]{Lang.getString(R.string.RemoveRestrictions), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                if (id == R.id.btn_blockUser) {
                  unblockRunnable.run();
                }
                return true;
              });
            } else {
              showSettings(new SettingsWrapBuilder(R.id.btn_unblockUser)
                .setHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, Lang.getString(R.string.QUnblockX, tdlib.cache().userName(args.userId)), false))
                .setIntDelegate((id, result) -> {
                  boolean addBackToGroup = result.get(R.id.right_readMessages) != 0;
                  if (addBackToGroup) {
                    targetRestrict.isMember = true;
                  }
                  unblockRunnable.run();
                })
                .setRawItems(new ListItem[] {
                  new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.right_readMessages, 0, tdlib.isChannel(args.chatId) ? R.string.InviteBackToChannel : R.string.InviteBackToGroup, false)
                })
                .setSaveStr(R.string.Unban)
                .setSaveColorId(R.id.theme_color_textNegative)
              );
            }

            break;
          }
          case R.id.btn_dismissAdmin: {
            showOptions(null, new int[]{R.id.btn_dismissAdmin, R.id.btn_cancel}, new String[]{Lang.getString(R.string.DismissAdmin), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
              if (id == R.id.btn_dismissAdmin && !isDoneInProgress()) {
                targetAdmin.canChangeInfo = false;
                targetAdmin.canManageChat = false;
                targetAdmin.canPostMessages = false;
                targetAdmin.canEditMessages = false;
                targetAdmin.canDeleteMessages = false;
                targetAdmin.canInviteUsers = false;
                targetAdmin.canRestrictMembers = false;
                targetAdmin.canPinMessages = false;
                targetAdmin.canManageVoiceChats = false;
                targetAdmin.isAnonymous = false;
                targetAdmin.canPromoteMembers = false;
                updateValues();
                setDoneInProgress(true);
                setDoneVisible(true);
                performRequest(true);
              }
              return true;
            });
            break;
          }
          case R.id.btn_date: {
            showOptions(null,
              new int[]{R.id.btn_1day, R.id.btn_1week, R.id.btn_1month, R.id.btn_forever, R.id.btn_custom},
              new String[]{Lang.plural(R.string.xDays, 1), Lang.plural(R.string.xWeeks, 1), Lang.plural(R.string.xMonths, 1), Lang.getString(R.string.UserRestrictionsUntilForever), Lang.getString(R.string.CustomDate)}, null, null, (itemView, id) -> {
                switch (id) {
                  case R.id.btn_1day:
                    setBlockFor(60 * 60 * 24 + 60 * 2);
                    break;
                  case R.id.btn_1week:
                    setBlockFor(60 * 60 * 24 * 7 + 60 * 2);
                    break;
                  case R.id.btn_1month:
                    setBlockFor(60 * 60 * 24 * 30 + 60 * 2);
                    break;
                  case R.id.btn_custom: {
                    if (canViewMessages) {
                      showDateTimePicker(Lang.getString(R.string.RestrictUser), R.string.RestrictUntilToday, R.string.RestrictUntilTomorrow, R.string.RestrictUntilFuture, millis -> setRestrictUntilDate((int) (millis / 1000l)), null);
                    } else {
                      showDateTimePicker(Lang.getString(R.string.BlockUser), R.string.BlockUntilToday, R.string.BlockUntilTomorrow, R.string.BlockUntilFuture, millis -> setRestrictUntilDate((int) (millis / 1000l)), null);
                    }
                    break;
                  }
                  case R.id.btn_forever:
                    setBlockFor(0);
                    break;
                }
                return true;
              });
            break;
          }
        }
      }
    }
  }

  @Override
  protected boolean onDoneClick () {
    if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
      targetAdmin.canManageChat = true;
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

    if (args.mode == MODE_RESTRICTION) {
      if (canViewMessages) {
        if (!TD.hasRestrictions(targetRestrict.permissions, tdlib.chatPermissions(args.chatId))) {
          // newStatus = targetRestrict.isMember ? new TdApi.ChatMemberStatusMember() : new TdApi.ChatMemberStatusLeft();
          if (args.member == null || !TD.isRestricted(args.member.status)) {
            UI.showToast(R.string.NoRestrictionsHint, Toast.LENGTH_SHORT);
            return;
          }
          newStatus = new TdApi.ChatMemberStatusMember();
        } else {
          newStatus = targetRestrict;
        }
      } else {
        newStatus = new TdApi.ChatMemberStatusBanned(targetRestrict.restrictedUntilDate);
      }
    } else if (args.member != null && args.member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
      TdApi.ChatMemberStatusCreator creator = (TdApi.ChatMemberStatusCreator) args.member.status;
      newStatus = new TdApi.ChatMemberStatusCreator(targetAdmin.customTitle, targetAdmin.isAnonymous, creator.isMember);
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
      tdlib.setChatMemberStatus(args.chatId, new TdApi.MessageSenderUser(args.userId), newStatus, args.forwardLimit, args.member != null ? args.member.status : null, (success, error) -> tdlib.ui().post(() -> {
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
    return R.id.theme_color_background;
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
          ViewSupport.setThemedBackground(parent, R.id.theme_color_filling, EditRightsController.this);
        }
      }

      @Override
      @SuppressWarnings("WrongConstant")
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getViewType()) {
          case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE: {
            boolean canEdit = hasAccessToEditRight(item.getId());
            view.setIgnoreEnabled(true);
            view.setEnabled(canEdit || getHintForToggleUnavailability(item.getId(), item.getBoolValue()) != null);
            view.setVisuallyEnabled(canEdit, isUpdate);
            view.getToggler().setRadioEnabled(item.getBoolValue(), isUpdate);
            view.getToggler().setShowLock(!canEdit);
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER: {
            int id = item.getId();
            boolean canEdit = hasAccessToEditRight(item.getId());
            view.setIgnoreEnabled(true);
            view.setEnabled(canEdit || getHintForToggleUnavailability(item.getId(), item.getBoolValue()) != null);
            view.setVisuallyEnabled(canEdit, isUpdate);
            view.getToggler().setUseNegativeState(true);
            view.getToggler().setRadioEnabled(item.getBoolValue(), isUpdate);
            view.getToggler().setShowLock(!canEdit);
            view.setData(item.getBoolValue() ? R.string.AllMembers : (id == R.id.right_inviteUsers || id == R.id.right_changeChatInfo || id == R.id.right_pinMessages) ? R.string.OnlyAdminsSpecific : R.string.OnlyAdmins);
            break;
          }
          default: {
            switch (item.getId()) {
              case R.id.btn_date: {
                view.setIgnoreEnabled(false);
                view.setEnabled(hasAccessToEditRight(item.getId()));
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
                break;
              }
            }
          }
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
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

  @Override
  public void onBasicGroupFullUpdated (int basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) { }

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

  private CharSequence getHintForToggleUnavailability (@RightId int id, boolean currentValue) {
    Args args = getArgumentsStrict();
    if (args.mode == MODE_CHAT_PERMISSIONS) {
      if (!tdlib.canRestrictMembers(args.chatId)) {
        return null; // No need to explain
      }
      if (currentValue)
        return null;
      TdApi.Chat chat = tdlib.chatStrict(args.chatId);
      switch (id) {
        case R.id.right_changeChatInfo: {
          if (!tdlib.canChangeInfo(chat)) {
            return Lang.getMarkdownString(this, R.string.NoRightAllowChangeInfo);
          }
          if (tdlib.chatPublic(args.chatId)) {
            return Lang.getMarkdownString(this, R.string.NoRightAllowChangeInfoPublic);
          }
          break;
        }
        case R.id.right_pinMessages: {
          if (!tdlib.canPinMessages(chat)) {
            return Lang.getMarkdownString(this, R.string.NoRightAllowPin);
          }
          if (tdlib.chatPublic(args.chatId)) {
            return Lang.getMarkdownString(this, R.string.NoRightAllowPinPublic);
          }
          break;
        }
      }
      return null;
    }
    if (args.mode == MODE_ADMIN_PROMOTION && !tdlib.cache().userBot(args.userId) && (
        id == R.id.right_inviteUsers ||
        id == R.id.right_changeChatInfo ||
        id == R.id.right_pinMessages
      ) && TD.checkRight(tdlib.chatPermissions(args.chatId), id) && currentValue) {
      int promoteMode = args.member == null ? TD.PROMOTE_MODE_NEW : TD.canPromoteAdmin(args.myStatus, args.member.status);
      if (promoteMode != TD.PROMOTE_MODE_NEW && promoteMode != TD.PROMOTE_MODE_EDIT)
        return null;
      switch (id) {
        case R.id.right_inviteUsers:
          return Lang.getMarkdownString(this, R.string.NoRightDisallowInvite);
        case R.id.right_changeChatInfo:
          return Lang.getMarkdownString(this, R.string.NoRightDisallowChangeInfo);
        case R.id.right_pinMessages:
          return Lang.getMarkdownString(this, R.string.NoRightDisallowPin);
      }
    }
    return null;
  }

  private boolean hasAccessToEditRight (@RightId int id) {
    Args args = getArgumentsStrict();
    if (args.mode == MODE_CHAT_PERMISSIONS) {
      if (tdlib.canRestrictMembers(args.chatId)) {
        TdApi.Chat chat = tdlib.chatStrict(args.chatId);
        switch (id) {
          case R.id.right_changeChatInfo:
            if (!tdlib.canChangeInfo(chat) || tdlib.chatPublic(args.chatId)) {
              return false;
            }
            break;
          case R.id.right_pinMessages:
            if (!tdlib.canPinMessages(tdlib.chatStrict(args.chatId)) || tdlib.chatPublic(args.chatId)) {
              return false;
            }
            break;
        }
        return true;
      }
      return false;
    }
    if (args.mode == MODE_RESTRICTION && TD.isValidRight(id) && !TD.checkRight(tdlib.chatPermissions(args.chatId), id)) {
      return false;
    }
    if (args.mode == MODE_ADMIN_PROMOTION && !tdlib.cache().userBot(args.userId) && (id == R.id.right_inviteUsers || id == R.id.right_changeChatInfo || id == R.id.right_pinMessages) && TD.checkRight(tdlib.chatPermissions(args.chatId), id)) {
      return false;
    }
    if (!isNewRuleSet()) {
      switch (args.member.status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR: {
          return id == R.id.right_remainAnonymous && args.myStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR;
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
              return ((TdApi.ChatMemberStatusAdministrator) args.myStatus).canRestrictMembers;
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
          return me.canRestrictMembers;
        } else {
          switch (id) {
            case R.id.right_addNewAdmins:
              return me.canPromoteMembers;
            case R.id.right_banUsers:
              return me.canRestrictMembers;
            case R.id.right_changeChatInfo:
              return me.canChangeInfo;
            case R.id.right_deleteMessages:
              return me.canDeleteMessages;
            case R.id.right_editMessages:
              return me.canEditMessages;
            case R.id.right_inviteUsers:
              return me.canInviteUsers;
            case R.id.right_pinMessages:
              return me.canPinMessages;
            case R.id.right_manageVoiceChats:
              return me.canManageVoiceChats;
            case R.id.right_remainAnonymous:
              return me.isAnonymous;
            case R.id.right_sendMessages:
              return me.canPostMessages;

            case R.id.right_embedLinks:
            case R.id.right_readMessages:
            case R.id.right_sendMedia:
            case R.id.right_sendStickersAndGifs:
            case R.id.right_sendPolls:
              return true;
          }
        }
      }
    }
    return false;
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
        value = targetAdmin.canPromoteMembers;
      } else {
        int i = adapter.indexOfViewById(R.id.right_addNewAdmins);
        value = i != -1 && adapter.getItems().get(i).getBoolValue();
      }
      return value ? R.string.XCanAssignAdmins : R.string.XCannotAssignAdmins;
    }
  }

  private void updateDescriptionHint () {
    int i = adapter.indexOfViewById(R.id.description);
    if (i != -1) {
      adapter.getItems().get(i).setString(Lang.getStringBold(getDescriptionStringRes(), tdlib.cache().userName(getArgumentsStrict().userId)));
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
    int userId = getArgumentsStrict().userId;

    boolean isChannel = tdlib.isChannel(chatId);
    CharSequence text;

    Lang.SpanCreator spanCreator = (target, argStart, argEnd, argIndex, needFakeBold) -> {
      if (argIndex == 1) {
        return Lang.newUserSpan(this, userId);
      }
      return null;
    };

    if (isChannel) {
      text = Lang.getMarkdownString(this, R.string.TransferOwnershipAlertChannel, spanCreator, tdlib.chatTitle(chatId), tdlib.cache().userName(userId));
    } else {
      text = Lang.getMarkdownString(this, R.string.TransferOwnershipAlertGroup, spanCreator, tdlib.chatTitle(chatId), tdlib.cache().userName(userId));
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
        Runnable act = () -> tdlib.transferOwnership(getArgumentsStrict().chatId, userId, password, (success, error) -> runOnUiThreadOptional(() -> {
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
    if (targetAdmin == null || !targetAdmin.canBeEdited || getArgumentsStrict().mode != MODE_ADMIN_PROMOTION || getArgumentsStrict().myStatus.getConstructor() != TdApi.ChatMemberStatusCreator.CONSTRUCTOR)
      return false;
    boolean isChannel = tdlib.isChannel(getArgumentsStrict().chatId);
    if (isChannel) {
      return targetAdmin.canChangeInfo && targetAdmin.canPostMessages && targetAdmin.canEditMessages && targetAdmin.canDeleteMessages && targetAdmin.canInviteUsers && targetAdmin.canManageVoiceChats && targetAdmin.canPromoteMembers;
    } else {
      return targetAdmin.canChangeInfo && targetAdmin.canDeleteMessages && targetAdmin.canRestrictMembers && targetAdmin.canInviteUsers && targetAdmin.canPinMessages && targetAdmin.canManageVoiceChats && targetAdmin.canPromoteMembers;
    }
  }

  private void checkTransferOwnership () {
    boolean isChannel = tdlib.isChannel(getArgumentsStrict().chatId);
    boolean canTransfer = canTransferOwnership();
    if (canTransfer) {
      int i = adapter.indexOfViewById(R.id.btn_dismissAdmin);
      if (i != -1) {
        adapter.getItems().add(i, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.getItems().add(i, newTransferOwnershipItem(isChannel));
        adapter.notifyItemRangeInserted(i, 2);
      } else {
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

    if (args.userId != 0) {
      items.add(new ListItem(ListItem.TYPE_CHAT_BETTER).setData(new TGFoundChat(tdlib, args.userId).setForcedSubtitle(args.member != null && TD.isCreator(args.member.status) ? Lang.getString(R.string.ChannelOwner) : null)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    items.add(new ListItem(args.userId != 0 ? ListItem.TYPE_HEADER : ListItem.TYPE_HEADER_PADDED, 0, 0, args.mode == MODE_CHAT_PERMISSIONS ? R.string.WhatMembersCanDo : tdlib.cache().userBot(args.userId) ? R.string.WhatThisBotCanDo : args.mode == MODE_RESTRICTION ? R.string.WhatThisUserCanDo : R.string.WhatThisAdminCanDo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    boolean isChannel = tdlib.isChannel(args.chatId);

    int[] rightIds;
    if (args.mode == MODE_CHAT_PERMISSIONS) {
      rightIds = new int[] {
        R.id.right_sendMessages,
        R.id.right_sendMedia,
        R.id.right_sendStickersAndGifs,
        R.id.right_sendPolls,
        R.id.right_embedLinks,
        R.id.right_inviteUsers,
        R.id.right_pinMessages,
        R.id.right_changeChatInfo,
      };
    } else if (args.mode == MODE_RESTRICTION) {
      rightIds = new int[] {
        R.id.right_readMessages,
        R.id.right_sendMessages,
        R.id.right_sendMedia,
        R.id.right_sendStickersAndGifs,
        R.id.right_sendPolls,
        R.id.right_embedLinks,
        R.id.right_inviteUsers,
        R.id.right_pinMessages,
        R.id.right_changeChatInfo,
      };
    } else if (isChannel) {
      rightIds = new int[] {
        R.id.right_changeChatInfo,
        R.id.right_sendMessages,
        R.id.right_editMessages,
        R.id.right_deleteMessages,
        R.id.right_inviteUsers,
        R.id.right_manageVoiceChats,
        R.id.right_addNewAdmins,
      };
    } else {
      rightIds = new int[] {
        R.id.right_changeChatInfo,
        R.id.right_deleteMessages,
        R.id.right_banUsers,
        R.id.right_inviteUsers,
        R.id.right_pinMessages,
        R.id.right_manageVoiceChats,
        R.id.right_remainAnonymous,
        R.id.right_addNewAdmins
      };
    }

    boolean first = true;
    int viewType = args.mode == MODE_CHAT_PERMISSIONS ? ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER : ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE;
    for (int id : rightIds) {
      if (first) {
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(viewType, id, 0, stringForRightId(id, isChannel)).setBoolValue(getValueForId(id)));
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
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, Lang.getStringBold(getDescriptionStringRes(), tdlib.cache().userName(args.userId)), false));
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
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_dismissAdmin, 0, R.string.DismissAdmin).setTextColorId(R.id.theme_color_textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else if (canTransferOwnership) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(newTransferOwnershipItem(isChannel));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else if (canDismissAdmin) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_dismissAdmin, 0, R.string.DismissAdmin).setTextColorId(R.id.theme_color_textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (canUnbanUser()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_unblockUser, 0, args.member.status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR ? (args.member.memberId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR && tdlib.cache().userBot(((TdApi.MessageSenderUser) args.member.memberId).userId)) ? R.string.UnbanMemberBot : R.string.UnbanMember : R.string.RemoveRestrictions).setTextColorId(R.id.theme_color_textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_ZERO_VIEW));

    adapter.setItems(items, false);
  }

  private static ListItem newTransferOwnershipItem (boolean isChannel) {
    return new ListItem(ListItem.TYPE_SETTING, R.id.btn_transferOwnership, 0, isChannel ? R.string.TransferOwnershipChannel : R.string.TransferOwnershipGroup).setTextColorId(R.id.theme_color_textNegative);
  }

  private void addEditTitleCells (List<ListItem> items) {
    Args args = getArgumentsStrict();
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.CustomTitle));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(customTitle = new ListItem(ListItem.TYPE_EDITTEXT_POLL_OPTION, R.id.input_customTitle, 0, 0, false).setStringValue(args.member != null ? Td.getCustomTitle(args.member.status) : null));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(R.string.CustomTitleHint, Lang.getString(args.member != null && TD.isCreator(args.member.status) ? R.string.message_ownerSign : R.string.message_adminSignPlain)), false));
    adapter.setLockFocusOn(this, args.member != null && TD.isCreator(args.member.status) && TD.isCreator(args.myStatus));
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
        return ((TdApi.ChatMemberStatusAdministrator) status).canRestrictMembers;
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
        return ((TdApi.ChatMemberStatusAdministrator) status).canPromoteMembers;
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
        return false;
      }

      TdApi.ChatMemberStatusRestricted old = (TdApi.ChatMemberStatusRestricted) args.member.status;
      return old.restrictedUntilDate != targetRestrict.restrictedUntilDate  || !Td.equalsTo(targetRestrict.permissions, old.permissions, tdlib.chatPermissions(args.chatId));
    } else if (customTitle != null && !StringUtils.equalsOrBothEmpty(Td.getCustomTitle(args.member.status), customTitle.getStringValue())) {
      return true;
    } else if (args.member.status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
      TdApi.ChatMemberStatusAdministrator old = (TdApi.ChatMemberStatusAdministrator) args.member.status;
      return !Td.equalsTo(old, targetAdmin);
    } else if (args.member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
      return ((TdApi.ChatMemberStatusCreator) args.member.status).isAnonymous != targetAdmin.isAnonymous;
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

  private void toggleValueForId (@RightId int id) {
    final boolean newValue = !getValueForId(id);
    switch (id) {
      case R.id.right_readMessages: {
        setCanViewMessages(newValue);
        break;
      }
      case R.id.right_sendMessages: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.canPostMessages = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          targetRestrict.permissions.canSendMessages = newValue;
        }
        break;
      }
      case R.id.right_sendMedia: {
        setCanViewMessages(canViewMessages || newValue);
        targetRestrict.permissions.canSendMessages = targetRestrict.permissions.canSendMessages || newValue;
        targetRestrict.permissions.canSendMediaMessages = newValue;
        break;
      }
      case R.id.right_sendStickersAndGifs: {
        setCanViewMessages(canViewMessages || newValue);
        targetRestrict.permissions.canSendMessages = targetRestrict.permissions.canSendMessages || newValue;
        targetRestrict.permissions.canSendOtherMessages = newValue;
        break;
      }
      case R.id.right_sendPolls: {
        setCanViewMessages(canViewMessages || newValue);
        targetRestrict.permissions.canSendMessages = targetRestrict.permissions.canSendMessages || newValue;
        targetRestrict.permissions.canSendPolls = newValue;
        break;
      }
      case R.id.right_embedLinks: {
        setCanViewMessages(canViewMessages || newValue);
        targetRestrict.permissions.canSendMessages = targetRestrict.permissions.canSendMessages || newValue;
        targetRestrict.permissions.canAddWebPagePreviews = newValue;
        break;
      }
      case R.id.right_changeChatInfo: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.canChangeInfo = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          targetRestrict.permissions.canChangeInfo = newValue;
        }
        break;
      }
      case R.id.right_inviteUsers: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.canInviteUsers = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          targetRestrict.permissions.canInviteUsers = newValue;
        }
        break;
      }
      case R.id.right_pinMessages: {
        if (getArgumentsStrict().mode == MODE_ADMIN_PROMOTION) {
          targetAdmin.canPinMessages = newValue;
        } else {
          setCanViewMessages(canViewMessages || newValue);
          // FIXME? targetRestrict.permissions.canSendMessages = targetRestrict.permissions.canSendMessages || newValue;
          targetRestrict.permissions.canPinMessages = newValue;
        }
        break;
      }
      case R.id.right_addNewAdmins:
        targetAdmin.canPromoteMembers = newValue;
        updateDescriptionHint();
        break;
      case R.id.right_banUsers:
        targetAdmin.canRestrictMembers = newValue;
        break;
      case R.id.right_manageVoiceChats:
        targetAdmin.canManageVoiceChats = newValue;
        break;
      case R.id.right_remainAnonymous:
        targetAdmin.isAnonymous = newValue;
        break;
      case R.id.right_deleteMessages:
        targetAdmin.canDeleteMessages = newValue;
        break;
      case R.id.right_editMessages:
        targetAdmin.canEditMessages = newValue;
        break;
    }
    if (getArgumentsStrict().mode == MODE_CHAT_PERMISSIONS || getArgumentsStrict().mode == MODE_RESTRICTION) {
      targetRestrict.isMember = canViewMessages;
      targetRestrict.permissions.canSendMessages = getValueForId(R.id.right_sendMessages);
      targetRestrict.permissions.canSendMediaMessages = getValueForId(R.id.right_sendMedia);
      targetRestrict.permissions.canSendOtherMessages = getValueForId(R.id.right_sendStickersAndGifs);
      targetRestrict.permissions.canSendPolls = getValueForId(R.id.right_sendPolls);
      targetRestrict.permissions.canAddWebPagePreviews = getValueForId(R.id.right_embedLinks);
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
      switch (item.getViewType()) {
        case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE:
        case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
          boolean value = getValueForId(item.getId());
          if (value != item.getBoolValue()) {
            item.setBoolValue(value);
            adapter.updateValuedSettingByPosition(i);
          }
          break;
      }
      i++;
    }
  }

  private boolean checkDefaultRight (@RightId int id) {
    return !tdlib.cache().userBot(getArgumentsStrict().userId) && TD.checkRight(tdlib.chatPermissions(getArgumentsStrict().chatId), id);
  }

  private boolean getValueForId (@RightId int id) {
    if (getArgumentsStrict().mode == MODE_RESTRICTION && !TD.checkRight(tdlib.chatPermissions(getArgumentsStrict().chatId), id)) {
      return false;
    }
    switch (id) {
      case R.id.right_readMessages:
        return canViewMessages;
      case R.id.right_sendMessages:
        return getArgumentsStrict().mode == MODE_ADMIN_PROMOTION ? targetAdmin.canPostMessages : canViewMessages && targetRestrict.permissions.canSendMessages;
      case R.id.right_sendMedia:
        return canViewMessages && targetRestrict.permissions.canSendMessages && targetRestrict.permissions.canSendMediaMessages;
      case R.id.right_sendStickersAndGifs:
        return canViewMessages && targetRestrict.permissions.canSendMessages && targetRestrict.permissions.canSendOtherMessages;
      case R.id.right_sendPolls:
        return canViewMessages && targetRestrict.permissions.canSendMessages && targetRestrict.permissions.canSendPolls;
      case R.id.right_embedLinks:
        return canViewMessages && targetRestrict.permissions.canSendMessages && targetRestrict.permissions.canAddWebPagePreviews;
      case R.id.right_changeChatInfo:
        return getArgumentsStrict().mode == MODE_ADMIN_PROMOTION ? targetAdmin.canChangeInfo || checkDefaultRight(id) : canViewMessages && targetRestrict.permissions.canChangeInfo;
      case R.id.right_inviteUsers:
        return getArgumentsStrict().mode == MODE_ADMIN_PROMOTION ? targetAdmin.canInviteUsers || checkDefaultRight(id) : canViewMessages && targetRestrict.permissions.canInviteUsers;
      case R.id.right_pinMessages:
        return getArgumentsStrict().mode == MODE_ADMIN_PROMOTION ? targetAdmin.canPinMessages || checkDefaultRight(id) : canViewMessages && targetRestrict.permissions.canPinMessages; // FIXME? targetRestrict.permissions.canSendMessages
      case R.id.right_deleteMessages:
        return targetAdmin.canDeleteMessages;
      case R.id.right_banUsers:
        return targetAdmin.canRestrictMembers;
      case R.id.right_addNewAdmins:
        return targetAdmin.canPromoteMembers;
      case R.id.right_manageVoiceChats:
        return targetAdmin.canManageVoiceChats;
      case R.id.right_remainAnonymous:
        return targetAdmin.isAnonymous;
      case R.id.right_editMessages:
        return targetAdmin.canEditMessages;
    }
    throw new IllegalArgumentException("id == " + UI.getResources().getResourceName(id));
  }

  private @StringRes int stringForRightId (@RightId int id, boolean isChannel) {
    switch (id) {
      case R.id.right_readMessages:
        return R.string.UserRestrictionsRead;
      case R.id.right_sendMessages:
        return getArgumentsStrict().mode == MODE_ADMIN_PROMOTION ? R.string.EditAdminPostMessages : R.string.UserRestrictionsSend;
      case R.id.right_sendMedia:
        return R.string.RightSendMedia;
      case R.id.right_sendStickersAndGifs:
        return R.string.UserRestrictionsSendStickers;
      case R.id.right_sendPolls:
        return R.string.UserRestrictionsSendPolls;
      case R.id.right_embedLinks:
        return R.string.UserRestrictionsEmbedLinks;
      case R.id.right_changeChatInfo:
        return isChannel ? R.string.RightChangeChannelInfo : R.string.RightChangeGroupInfo;
      case R.id.right_deleteMessages:
        return R.string.EditAdminGroupDeleteMessages;
      case R.id.right_banUsers:
        return R.string.RightBanUsers;
      case R.id.right_inviteUsers:
        return R.string.RightInviteViaLink;
      case R.id.right_pinMessages:
        return R.string.RightPinMessages;
      case R.id.right_addNewAdmins:
        return R.string.RightAddNewAdmins;
      case R.id.right_editMessages:
        return R.string.RightEditMessages;
      case R.id.right_manageVoiceChats:
        return R.string.RightVoiceChats;
      case R.id.right_remainAnonymous:
        return R.string.RightAnonymous;
    }
    throw new IllegalArgumentException("id == " + UI.getResources().getResourceName(id));
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
        return Lang.getString(R.string.UserRestrictions);
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

}
