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
 * File created on 18/02/2018
 */
package org.thunderdog.challegram.telegram;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;
import androidx.core.os.CancellationSignal;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.popups.ModernActionedLayout;
import org.thunderdog.challegram.component.preview.PreviewLayout;
import org.thunderdog.challegram.component.sticker.StickerSetWrap;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.LangUtils;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGBotStart;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGSwitchInline;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColors;
import org.thunderdog.challegram.theme.ThemeCustom;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperties;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ChatFolderInviteLinkController;
import org.thunderdog.challegram.ui.ChatJoinRequestsController;
import org.thunderdog.challegram.ui.ChatLinkMembersController;
import org.thunderdog.challegram.ui.ChatLinksController;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.EditChatFolderController;
import org.thunderdog.challegram.ui.EditChatLinkController;
import org.thunderdog.challegram.ui.EditDeleteAccountReasonController;
import org.thunderdog.challegram.ui.EditNameController;
import org.thunderdog.challegram.ui.EditProxyController;
import org.thunderdog.challegram.ui.EditRightsController;
import org.thunderdog.challegram.ui.EditUsernameController;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.ui.MapControllerFactory;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.PasscodeController;
import org.thunderdog.challegram.ui.PasscodeSetupController;
import org.thunderdog.challegram.ui.PasswordController;
import org.thunderdog.challegram.ui.PhoneController;
import org.thunderdog.challegram.ui.ProfileController;
import org.thunderdog.challegram.ui.RequestController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.Settings2FAController;
import org.thunderdog.challegram.ui.SettingsController;
import org.thunderdog.challegram.ui.SettingsFoldersController;
import org.thunderdog.challegram.ui.SettingsLanguageController;
import org.thunderdog.challegram.ui.SettingsLogOutController;
import org.thunderdog.challegram.ui.SettingsNotificationController;
import org.thunderdog.challegram.ui.SettingsPhoneController;
import org.thunderdog.challegram.ui.SettingsPrivacyController;
import org.thunderdog.challegram.ui.SettingsPrivacyKeyController;
import org.thunderdog.challegram.ui.SettingsProxyController;
import org.thunderdog.challegram.ui.SettingsSessionsController;
import org.thunderdog.challegram.ui.SettingsThemeController;
import org.thunderdog.challegram.ui.SettingsWebsitesController;
import org.thunderdog.challegram.ui.ShareController;
import org.thunderdog.challegram.ui.SimpleViewPagerController;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.voip.VoIPLogs;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.InfiniteRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.FutureBool;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.unit.ByteUnit;
import me.vkryl.core.util.ConditionalExecutor;
import tgx.td.ChatId;
import tgx.td.ChatPosition;
import tgx.td.MessageId;
import tgx.td.Td;
import tgx.td.TdConstants;
import tgx.td.TdExt;
import tgx.td.data.MessageWithProperties;

public class TdlibUi extends Handler {
  private final Tdlib tdlib;

  /*package*/ TdlibUi (Tdlib tdlib) {
    super(Looper.getMainLooper());
    this.tdlib = tdlib;
  }

  public void execute (Runnable runnable) {
    if (UI.inUiThread()) {
      runnable.run();
    } else {
      post(runnable);
    }
  }

  public static int[] getAvailablePriorityOrImportanceList () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return new int[] {
        android.app.NotificationManager.IMPORTANCE_HIGH, // Sound and pop-up (default)
        android.app.NotificationManager.IMPORTANCE_DEFAULT, // Sound
        android.app.NotificationManager.IMPORTANCE_LOW, // Silent
        android.app.NotificationManager.IMPORTANCE_MIN, // Silent and minimized
      };
    } else {
      return getAvailablePriorityListLegacy();
    }
  }

  @SuppressWarnings("deprecation")
  private static int[] getAvailablePriorityListLegacy () {
    return new int[] {
      android.app.Notification.PRIORITY_MAX,
      android.app.Notification.PRIORITY_HIGH, // (default)
      android.app.Notification.PRIORITY_LOW,
    };
  }

  @IdRes
  public static int getPriorityOrImportanceId (int priorityOrImportance) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      switch (priorityOrImportance) {
        case android.app.NotificationManager.IMPORTANCE_HIGH: // Sound and pop-up (default)
          return R.id.btn_importanceHigh;
        case android.app.NotificationManager.IMPORTANCE_DEFAULT: // Sound
          return R.id.btn_importanceDefault;
        case android.app.NotificationManager.IMPORTANCE_LOW: // Silent
          return R.id.btn_importanceLow;
        case android.app.NotificationManager.IMPORTANCE_MIN: // Silent and minimized
          return R.id.btn_importanceMin;
      }
    } else {
      switch (priorityOrImportance) {
        case android.app.Notification.PRIORITY_MAX:
          return R.id.btn_priorityMax;
        case android.app.Notification.PRIORITY_HIGH: // (default)
          return R.id.btn_priorityHigh;
        case android.app.Notification.PRIORITY_LOW:
          return R.id.btn_priorityLow;
      }
    }
    throw new IllegalArgumentException("priorityOrImportance == " + priorityOrImportance);
  }

  @StringRes
  public static int getPriorityOrImportanceString (int priorityOrImportance, boolean hasSound, boolean hasVibration) {
    if (priorityOrImportance == TdlibNotificationManager.PRIORITY_OR_IMPORTANCE_UNSET)
      return R.string.Default;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      switch (priorityOrImportance) {
        case android.app.NotificationManager.IMPORTANCE_MAX:
        case android.app.NotificationManager.IMPORTANCE_HIGH:
          return hasSound ? R.string.NotificationImportanceHigh : hasVibration ? R.string.NotificationImportanceHighNoSound : R.string.NotificationImportanceHighMuted;
        case android.app.NotificationManager.IMPORTANCE_DEFAULT:
          return hasSound ? R.string.NotificationImportanceDefault : hasVibration ? R.string.NotificationImportanceDefaultNoSound : R.string.NotificationImportanceDefaultMuted;
        case android.app.NotificationManager.IMPORTANCE_LOW:
          return hasSound || hasVibration ? R.string.NotificationImportanceLow : R.string.NotificationImportanceLowMuted;
        case android.app.NotificationManager.IMPORTANCE_MIN:
          return R.string.NotificationImportanceMin;
        case android.app.NotificationManager.IMPORTANCE_NONE:
          return R.string.NotificationImportanceNone;
        default:
          throw new IllegalArgumentException("priorityOrImportance == " + priorityOrImportance);
      }
    } else {
      switch (priorityOrImportance) {
        case android.app.Notification.PRIORITY_LOW:
          return R.string.PriorityLow;
        case android.app.Notification.PRIORITY_MAX:
          return R.string.PriorityUrgent;
        case android.app.Notification.PRIORITY_HIGH:
        default:
          return R.string.PriorityRegular;
      }
    }
  }

  @Override
  public void handleMessage (Message msg) {
    if (msg.what >= 0) {
      tdlib.handleUiMessage(msg);
    }
  }

  // Unsorted UI-related common stuff

  private static boolean deleteSuperGroupMessages (final ViewController<?> context, final MessageWithProperties[] deletingMessages, final @Nullable Runnable after) {
    final Tdlib tdlib = context.tdlib();
    if (deletingMessages == null || deletingMessages.length == 0) {
      return false;
    }
    final long chatId = TdExt.findUniqueChatId(deletingMessages);
    if (chatId == 0 || !context.tdlib().isSupergroup(chatId)) {
      // Chat is not supergroup
      return false;
    }
    final TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    if (status == null || !TD.isAdmin(status) || (status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR && !((TdApi.ChatMemberStatusAdministrator) status).rights.canDeleteMessages)) {
      // User is not a creator or admin with canDeleteMessages right
      return false;
    }
    final TdApi.MessageSender senderId = TdExt.findUniqueSenderId(deletingMessages);
    if (senderId == null || context.tdlib().isSelfSender(senderId)) {
      // No need in "delete all" for outgoing messages
      return false;
    }
    for (MessageWithProperties deletingMessage : deletingMessages) {
      // No need in "delete all" for outgoing messages
      // or some of the passed messages can't be deleted at all
      if (deletingMessage.message.isOutgoing ||
        !(deletingMessage.properties.canBeDeletedForAllUsers || deletingMessage.properties.canBeDeletedOnlyForSelf)
      ) {
        return false;
      }
    }

    final String name = tdlib.senderName(senderId, true);
    final CharSequence text = Lang.pluralBold(R.string.QDeleteXMessagesFromY, deletingMessages.length, name);

    SettingsWrap wrap = context.showSettings(new SettingsWrapBuilder(R.id.btn_deleteSupergroupMessages).setHeaderItem(
      new ListItem(ListItem.TYPE_INFO, R.id.text_title, 0, text, false)).setRawItems(
      new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_banMember, 0, senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR ? R.string.RestrictUser : tdlib.isChannel(((TdApi.MessageSenderChat) senderId).chatId) ? R.string.BanChannel : R.string.BanChat, false),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_reportSpam, 0, R.string.ReportSpam, false),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_deleteAll, 0, Lang.getStringBold(R.string.DeleteAllFrom, name), false)
      }).setIntDelegate((id, result) -> {
        if (id == R.id.btn_deleteSupergroupMessages) {
          boolean banUser = result.get(R.id.btn_banMember) != 0;
          boolean reportSpam = result.get(R.id.btn_reportSpam) != 0;
          boolean deleteAll = result.get(R.id.btn_deleteAll) != 0;

          final long[] messageIds = TdExt.toMessageIdsMap(deletingMessages).valueAt(0);

          if (banUser) {
            tdlib.send(new TdApi.GetChatMember(chatId, senderId), (member, error) -> {
              if (error != null) {
                UI.showError(error);
              } else {
                context.runOnUiThreadOptional(() -> {
                  TdApi.ChatMemberStatus myStatus = tdlib.chatStatus(chatId);
                  if (myStatus != null) {
                    EditRightsController editController = new EditRightsController(context.context(), context.tdlib());
                    editController.setArguments(new EditRightsController.Args(chatId, senderId, true, myStatus, member));
                    context.navigateTo(editController);
                  }
                });
              }
            });
          }

          if (reportSpam) {
            tdlib.client().send(new TdApi.ReportSupergroupSpam(ChatId.toSupergroupId(chatId), messageIds), tdlib.okHandler());
          }

          if (deleteAll) {
            tdlib.client().send(new TdApi.DeleteChatMessagesBySender(chatId, senderId), tdlib.okHandler());
          } else {
            tdlib.deleteMessages(chatId, messageIds, true);
          }

          if (after != null) {
            after.run();
          }
        }
      }).setSaveStr(R.string.Delete).setSaveColorId(ColorId.textNegative));
    if (wrap != null) {
      tdlib.client().send(new TdApi.GetChatMember(chatId, senderId), result -> {
        if (result.getConstructor() == TdApi.ChatMember.CONSTRUCTOR) {
          TdApi.ChatMember member = (TdApi.ChatMember) result;
          tdlib.ui().post(() -> {
            CharSequence role = null, newText = null;
            if (member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
              role = Lang.getString(R.string.RoleOwner);
            } else if (member.status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR) {
              role = Lang.getString(R.string.RoleBanned);
            } else if (!TD.isMember(member.status, false) && member.memberId.getConstructor() != TdApi.MessageSenderChat.CONSTRUCTOR) {
              role = Lang.getString(R.string.RoleLeft);
            } else if (member.joinedChatDate != 0) {
              role = Lang.getRelativeDate(member.joinedChatDate, TimeUnit.SECONDS, tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS, true, 60, R.string.RoleMember, true);
            } else if (member.memberId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
              role = Lang.getString(tdlib.isChannel(Td.getSenderId(member.memberId)) ? R.string.RoleChannel : R.string.RoleGroup);
            } else {
              return;
            }
            if (newText == null) {
              newText = Lang.plural(R.string.QDeleteXMessagesFromYRole, deletingMessages.length, (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex < 2 ? Lang.newBoldSpan(needFakeBold) : null, name, role);
            }
            int i = wrap.adapter.indexOfViewById(R.id.text_title);
            if (i != -1 && wrap.adapter.getItem(i).setStringIfChanged(newText)) {
              wrap.adapter.notifyItemChanged(i);
            }
          });
        }
      });
      // TODO TDLib / server: ability to get totalCount with limit=0
      tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, senderId, 0, 0, 1, null, 0, 0), result -> {
        if (result.getConstructor() == TdApi.FoundChatMessages.CONSTRUCTOR) {
          int moreCount = ((TdApi.FoundChatMessages) result).totalCount - deletingMessages.length;
          if (moreCount > 0) {
            tdlib.ui().post(() -> {
              int i = wrap.adapter.indexOfViewById(R.id.btn_deleteAll);
              if (i != -1 && wrap.adapter.getItem(i).setStringIfChanged(Lang.pluralBold(R.string.DeleteXMoreFrom, moreCount, name))) {
                wrap.adapter.notifyItemChanged(i);
              }
            });
          }
        }
      });
    }

    return true;
  }

  public void unblockMember (ViewController<?> c, long chatId, final TdApi.MessageSender senderId, TdApi.ChatMemberStatus currentStatus) {
    if (senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
      tdlib.setChatMemberStatus(chatId, senderId, new TdApi.ChatMemberStatusLeft(), currentStatus, null);
      return;
    }
    if (currentStatus.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR) {
      tdlib.setChatMemberStatus(chatId, senderId, new TdApi.ChatMemberStatusMember(), currentStatus, null);
      return;
    }

    c.showSettings(new SettingsWrapBuilder(R.id.btn_unblockSender)
      .addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, Lang.getString(R.string.QUnblockX, tdlib.senderName(senderId)), false))
      .setIntDelegate((id, result) -> {
        boolean addBackToGroup = result.get(R.id.btn_inviteBack) != 0;
        tdlib.setChatMemberStatus(chatId, senderId, addBackToGroup ? new TdApi.ChatMemberStatusMember() : new TdApi.ChatMemberStatusLeft(), currentStatus, null);
      })
      .setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_inviteBack, 0, tdlib.isChannel(chatId) ? R.string.InviteBackToChannel : R.string.InviteBackToGroup, false)
      })
      .setSaveStr(R.string.Unban)
      .setSaveColorId(ColorId.textNegative)
    );
  }

  private CharSequence getBlockString (long chatId, TdApi.MessageSender senderId, boolean willBeBlocked) {
    if (tdlib.isChannel(chatId)) {
      return Lang.getStringBold(willBeBlocked ? R.string.MemberCannotJoinChannel : R.string.MemberCanJoinChannel, tdlib.senderName(senderId));
    } else {
      return Lang.getStringBold(willBeBlocked ? R.string.MemberCannotJoinGroup : R.string.MemberCanJoinGroup, tdlib.senderName(senderId));
    }
  }

  public void kickMember (ViewController<?> c, final long chatId, final TdApi.MessageSender senderId, TdApi.ChatMemberStatus currentStatus) {
    if (senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR)
      return;
    if (ChatId.getType(chatId) == TdApi.ChatTypeBasicGroup.CONSTRUCTOR) {
      c.showOptions(Lang.getStringBold(R.string.MemberCannotJoinRegularGroup, tdlib.senderName(senderId, true)), new int[] {R.id.btn_blockSender, R.id.btn_cancel}, new String[]{Lang.getString(R.string.RemoveFromGroup), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[]{R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_blockSender) {
          tdlib.setChatMemberStatus(chatId, senderId, new TdApi.ChatMemberStatusLeft(), currentStatus, null);
        }
        return true;
      });
      return;
    }
    final ListItem headerItem = new ListItem(ListItem.TYPE_INFO, 0, 0, getBlockString(chatId, senderId, true), false);
    c.showSettings(new SettingsWrapBuilder(R.id.btn_blockSender)
      .addHeaderItem(headerItem)
      .setIntDelegate((id, result) -> {
        boolean blockUser = result.get(R.id.btn_restrictMember) != 0;
        if (currentStatus.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR && !blockUser) {
          TdApi.ChatMemberStatusRestricted now = (TdApi.ChatMemberStatusRestricted) currentStatus;
          tdlib.setChatMemberStatus(chatId, senderId, new TdApi.ChatMemberStatusRestricted(false, now.restrictedUntilDate, now.permissions), currentStatus, null);
        } else {
          tdlib.setChatMemberStatus(chatId, senderId, new TdApi.ChatMemberStatusBanned(), currentStatus, null);
          if (!blockUser) {
            tdlib.setChatMemberStatus(chatId, senderId, new TdApi.ChatMemberStatusLeft(), currentStatus, null);
          }
        }
      })
      .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> {
        headerItem.setString(getBlockString(chatId, senderId, settingsAdapter.getCheckIntResults().get(R.id.btn_restrictMember) != 0));
        settingsAdapter.updateValuedSettingByPosition(settingsAdapter.indexOfView(headerItem));
      })
      .setRawItems(new ListItem[]{
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_restrictMember, 0, R.string.BanMember, true)
      }).setSaveStr(R.string.RemoveMember).setSaveColorId(ColorId.textNegative));
  }

  private static boolean deleteWithRevoke (final ViewController<?> context, final MessageWithProperties[] deletingMessages, final @Nullable Runnable after) {
    if (deletingMessages == null || deletingMessages.length == 0)
      return false;

    final Tdlib tdlib = context.tdlib();
    final long singleChatId = TdExt.findUniqueChatId(deletingMessages);
    if (tdlib.isSelfChat(singleChatId)) {
      return false;
    }

    int totalCount = deletingMessages.length;
    int optionalCount = 0;
    int outgoingMessageCount = 0;
    int noRevokeCount = 0;
    for (MessageWithProperties message : deletingMessages) {
      if (message.properties.canBeDeletedForAllUsers && message.properties.canBeDeletedOnlyForSelf) {
        optionalCount++;
        if (message.message.isOutgoing)
          outgoingMessageCount++;
      }
      if (!message.properties.canBeDeletedForAllUsers && message.properties.canBeDeletedOnlyForSelf) {
        noRevokeCount++;
      }
    }
    boolean revokeByDefault = outgoingMessageCount > 0;
    if (optionalCount == 0) {
      return false;
    }
    SpannableStringBuilder title = new SpannableStringBuilder(Lang.pluralBold(R.string.QDeleteXMessages, deletingMessages.length));
    if (noRevokeCount > 0) {
      title.append("\n").append(Lang.pluralBold(R.string.DeleteXForMeWarning, noRevokeCount));
    }
    CharSequence revokeFor;
    if (optionalCount == totalCount) {
      if (ChatId.isMultiChat(singleChatId)) {
        revokeFor = Lang.getString(R.string.DeleteForEveryone);
      } else {
        revokeFor = Lang.getStringBold(R.string.DeleteForUser, tdlib.cache().userFirstName(tdlib.chatUserId(singleChatId)));
      }
    } else {
      if (ChatId.isMultiChat(singleChatId)) {
        revokeFor = Lang.pluralBold(R.string.DeleteXForEveryone, optionalCount);
      } else {
        revokeFor = Lang.pluralBold(R.string.DeleteXForUser, optionalCount, tdlib.cache().userFirstName(tdlib.chatUserId(singleChatId)));
      }
    }

    final int noRevokeCountFinal = noRevokeCount;

    context.showSettings(new SettingsWrapBuilder(R.id.btn_deleteMessagesWithRevoke)
      .addHeaderItem(title)
      .setRawItems(
      new ListItem[]{
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_revokeMessages, 0, revokeFor, revokeByDefault)
      }).setIntDelegate((id, result) -> {
        if (id == R.id.btn_deleteMessagesWithRevoke) {
          boolean revoke = result.get(R.id.btn_revokeMessages) != 0;
          if (revoke && noRevokeCountFinal > 0) {
            TdApi.Message[] revokeMessages = new TdApi.Message[deletingMessages.length - noRevokeCountFinal];
            TdApi.Message[] noRevokeMessages = new TdApi.Message[noRevokeCountFinal];
            int revokeIndex = 0;
            int noRevokeIndex = 0;
            for (MessageWithProperties message : deletingMessages) {
              if (message.properties.canBeDeletedForAllUsers) {
                revokeMessages[revokeIndex++] = message.message;
              } else {
                noRevokeMessages[noRevokeIndex++] = message.message;
              }
            }
            LongSparseArray<long[]> messageIds = TD.getMessageIds(revokeMessages);
            for (int i = 0; i < messageIds.size(); i++) {
              tdlib.deleteMessages(messageIds.keyAt(i), messageIds.valueAt(i), true);
            }
            messageIds = TD.getMessageIds(noRevokeMessages);
            for (int i = 0; i < messageIds.size(); i++) {
              tdlib.deleteMessages(messageIds.keyAt(i), messageIds.valueAt(i), false);
            }
          } else {
            final LongSparseArray<long[]> messageIds = TdExt.toMessageIdsMap(deletingMessages);
            for (int i = 0; i < messageIds.size(); i++) {
              tdlib.deleteMessages(messageIds.keyAt(i), messageIds.valueAt(i), revoke);
            }
          }
          if (after != null) {
            after.run();
          }
        }
      }).setSaveStr(R.string.Delete).setSaveColorId(ColorId.textNegative).setAllowResize(false));
    return true;
  }

  public void showDeleteOptions (ViewController<?> context, TdApi.Message message) {
    tdlib.getMessageProperties(message, properties -> {
      context.runOnUiThreadOptional(() -> {
        showDeleteOptions(context, new MessageWithProperties(message, properties));
      });
    });
  }

  public static void showDeleteOptions (ViewController<?> context, MessageWithProperties message) {
    showDeleteOptions(context, new MessageWithProperties[] {message}, null);
  }

  public static void showDeleteOptions (final ViewController<?> context, final MessageWithProperties[] messages, final @Nullable Runnable after) {
    if (context != null && messages != null && messages.length > 0) {
      if (deleteSuperGroupMessages(context, messages, after)) {
        return;
      }
      if (deleteWithRevoke(context, messages, after)) {
        return;
      }

      final Tdlib tdlib = context.tdlib();
      final long chatId = TdExt.findUniqueChatId(messages);

      boolean allScheduled = true;
      for (MessageWithProperties msg : messages) {
        if (!TD.isScheduled(msg.message)) {
          allScheduled = false;
          break;
        }
      }

      String deleteActionMsg = messages.length == 1 ?
              Lang.getString(
                allScheduled ? (tdlib.isSelfChat(chatId) ? R.string.DeleteReminder : R.string.DeleteScheduled) :
                  (tdlib.isSelfChat(chatId) ? R.string.DeleteMessage : R.string.DeleteForMe)) :
              Lang.plural(
                allScheduled ? (tdlib.isSelfChat(chatId) ? R.string.DeleteXReminders : R.string.DeleteXScheduled) :
                  (tdlib.isSelfChat(chatId) ? R.string.DeleteXMessages : R.string.DeleteXForMe), messages.length);

      if (!allScheduled) {
        for (MessageWithProperties msg : messages) {
          if (!msg.properties.canBeDeletedOnlyForSelf) {
            if (ChatId.isUserChat(chatId)) {
              deleteActionMsg = Lang.getString(R.string.DeleteForMeAndX, tdlib.cache().userFirstName(tdlib.chatUserId(chatId)));
            } else {
              deleteActionMsg = Lang.getString(R.string.DeleteForEveryone);
            }
            break;
          }
        }
      }

      context.showOptions(null,
        new int[] {R.id.menu_btn_delete, R.id.btn_cancel},
        new String[] {deleteActionMsg, Lang.getString(R.string.Cancel)},
        new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.menu_btn_delete) {
            LongSparseArray<long[]> messageIds = TdExt.toMessageIdsMap(messages);
            for (int i = 0; i < messageIds.size(); i++) {
              tdlib.deleteMessages(messageIds.keyAt(i), messageIds.valueAt(i), false);
            }
            if (after != null) {
              after.run();
            }
          }
          return true;
        }
      );
    }
  }

  public static final int DURATION_MODE_FULL = 0;
  public static final int DURATION_MODE_SHORT = 1;
  public static final int DURATION_MODE_RELATIVE = 2;

  public static String getDuration (long unixTime, TimeUnit timeUnit, boolean isFull) {
    return getDuration(unixTime, timeUnit, isFull ? DURATION_MODE_FULL : DURATION_MODE_SHORT);
  }

  public static String getDuration (long unixTime, TimeUnit timeUnit, int mode) {
    int time = (int) timeUnit.toSeconds(unixTime);
    if (time < 0)
      time = 0;
    int resId;
    if (time < 60) {
      switch (mode) {
        case DURATION_MODE_FULL: resId = R.string.xSeconds; break;
        case DURATION_MODE_SHORT: resId = R.string.xSecondsShort; break;
        case DURATION_MODE_RELATIVE: resId = R.string.xSecondsRelative; break;
        default: throw new IllegalArgumentException("mode == " + mode);
      }
      return Lang.plural(resId, time);
    }
    time /= 60;
    if (time < 60) {
      switch (mode) {
        case DURATION_MODE_FULL: resId = R.string.xMinutes; break;
        case DURATION_MODE_SHORT: resId = R.string.xMinutesShort; break;
        case DURATION_MODE_RELATIVE: resId = R.string.xMinutesRelative; break;
        default: throw new IllegalArgumentException("mode == " + mode);
      }
      return Lang.plural(resId, time);
    }
    time /= 60;
    if (time < 24) {
      switch (mode) {
        case DURATION_MODE_FULL: resId = R.string.xHours; break;
        case DURATION_MODE_SHORT: resId = R.string.xHoursShort; break;
        case DURATION_MODE_RELATIVE: resId = R.string.xHoursRelative; break;
        default: throw new IllegalArgumentException("mode == " + mode);
      }
      return Lang.plural(resId, time);
    }
    time /= 24;
    if (time < 7) {
      switch (mode) {
        case DURATION_MODE_FULL: resId = R.string.xDays; break;
        case DURATION_MODE_SHORT: resId = R.string.xDaysShort; break;
        case DURATION_MODE_RELATIVE: resId = R.string.xDaysRelative; break;
        default: throw new IllegalArgumentException("mode == " + mode);
      }
      return Lang.plural(resId, time);
    }
    time /= 7;
    switch (mode) {
      case DURATION_MODE_FULL: resId = R.string.xWeeks; break;
      case DURATION_MODE_SHORT: resId = R.string.xWeeks; break;
      case DURATION_MODE_RELATIVE: resId = R.string.xWeeksRelative; break;
      default: throw new IllegalArgumentException("mode == " + mode);
    }
    return Lang.plural(resId, time);
  }

  public @Nullable String getTTLShort (long chatId) {
    int ttl = tdlib.chatTTL(chatId);
    return ttl > 0 ? getDuration(ttl, TimeUnit.SECONDS, false) : null;
  }

  public CancellableRunnable openSupport (final ViewController<?> context) {
    final TdApi.Chat[] chat = new TdApi.Chat[1];
    final TdApi.Error[] error = new TdApi.Error[1];

    final CancellableRunnable runnable = new CancellableRunnable() {
      @Override
      public void act () {
        if (chat[0] != null) {
          openChat(context, chat[0], null);
        } else if (error[0] != null) {
          UI.showError(error[0]);
        }
      }
    };

    tdlib.client().send(new TdApi.GetSupportUser(), tdlib.silentHandler());

    PopupLayout[] popupFinal = new PopupLayout[1];
    popupFinal[0] = context.showOptions(Strings.buildMarkdown(context, Lang.getString(R.string.AskAQuestionInfo), (view, span, clickedText) -> {
      if (popupFinal[0] != null) {
        popupFinal[0].hideWindow(true);
      }
      return false;
    }), new int[] {R.id.btn_openChat, R.id.btn_cancel}, StringList.asArray(R.string.AskButton, R.string.Cancel), new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_help_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_openChat) {
        tdlib.client().send(new TdApi.GetSupportUser(), object -> {
          switch (object.getConstructor()) {
            case TdApi.User.CONSTRUCTOR: {
              TdApi.User supportUser = (TdApi.User) object;
              tdlib.client().send(new TdApi.CreatePrivateChat(supportUser.id, false), object1 -> {
                switch (object1.getConstructor()) {
                  case TdApi.Chat.CONSTRUCTOR: {
                    chat[0] = (TdApi.Chat) object1;
                    tdlib.ui().post(runnable);
                    break;
                  }
                  case TdApi.Error.CONSTRUCTOR: {
                    error[0] = (TdApi.Error) object1;
                    tdlib.ui().post(runnable);
                    break;
                  }
                }
              });
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              error[0] = (TdApi.Error) object;
              tdlib.ui().post(runnable);
              break;
            }
          }
        });
      }
      return true;
    });
    return runnable;
  }

  boolean shouldSendScreenshotHint (TdApi.Chat chat) {
    BaseActivity context = UI.getUiContext();
    if (context != null) {
      PopupLayout popupLayout = context.getCurrentPopupWindow();
      return popupLayout != null && popupLayout.getBoundController() instanceof MediaViewController && ((MediaViewController) popupLayout.getBoundController()).getArgumentsStrict().mode == MediaViewController.MODE_SECRET;
    }
    return false;
  }

  // TTL

  public static class TTLOption {
    public static final int IMMEDIATE = -1;

    private final int ttlTime;
    private final String ttlString;

    public TTLOption (int ttlTime, String ttlString) {
      this.ttlTime = ttlTime;
      this.ttlString = ttlString;
    }

    public int getTtlTime () {
      return ttlTime;
    }

    public boolean isOff () {
      return ttlTime == 0;
    }

    public boolean isImmediate () {
      return ttlTime == IMMEDIATE;
    }

    public String getTtlString () {
      return ttlString;
    }

    @Override
    public String toString () {
      return ttlString;
    }
  }

  public boolean updateTTLButton (int menuId, HeaderView headerView, TdApi.Chat chat, boolean force) {
    boolean isVisible = false;
    if (headerView != null) {
      headerView.updateMenuStopwatch(menuId, R.id.menu_btn_stopwatch, getTTLShort(chat != null ? chat.id : 0), isVisible = tdlib.canChangeMessageAutoDeleteTime(chat != null ? chat.id : 0), force);
    }
    return isVisible;
  }

  private void setTTL (TdApi.Chat chat, int newTtl) {
    if (chat == null) {
      return;
    }
    int oldTtl = tdlib.chatTTL(chat.id);
    if (oldTtl != newTtl) {
      tdlib.setChatMessageAutoDeleteTime(chat.id, newTtl);
    }
  }

  public void showTTLPicker (final Context context, final TdApi.Chat chat) {
    int ttl = tdlib.chatTTL(chat.id);
    TdApi.MessageSelfDestructType selfDestructType = ttl != 0 ? new TdApi.MessageSelfDestructTypeTimer(ttl) : null;
    showTTLPicker(context, selfDestructType, !ChatId.isSecret(chat.id), false, false, 0, result -> setTTL(chat, result.ttlTime));
  }

  public static void showTTLPicker (final Context context, @Nullable TdApi.MessageSelfDestructType currentSelfDestructType, boolean allowInstant, boolean useDarkMode, boolean precise, @StringRes int message, final RunnableData<TTLOption> callback) {
    final ArrayList<TTLOption> ttlOptions = new ArrayList<>(21);
    ttlOptions.add(new TTLOption(0, Lang.getString(R.string.Off)));
    if (allowInstant) {
      ttlOptions.add(new TTLOption(-1, Lang.getString(R.string.TimerInstant)));
    }
    final int secondsCount = precise ? 20 : 15;
    for (int i = 1; i <= secondsCount; i++) {
      ttlOptions.add(new TTLOption(i, Lang.plural(R.string.xSeconds, i)));
    }

    if (precise) {
      for (int i = secondsCount + 5; i < 60; i += 5) {
        ttlOptions.add(new TTLOption(i, Lang.plural(R.string.xSeconds, i)));
      }
      ttlOptions.add(new TTLOption(60, Lang.plural(R.string.xMinutes, 1)));
    } else {
      ttlOptions.add(new TTLOption(30, Lang.plural(R.string.xSeconds, 30)));
      ttlOptions.add(new TTLOption(60, Lang.plural(R.string.xMinutes, 1)));
      ttlOptions.add(new TTLOption(3600, Lang.plural(R.string.xHours, 1)));
      ttlOptions.add(new TTLOption(86400, Lang.plural(R.string.xDays, 1)));
      ttlOptions.add(new TTLOption(604800, Lang.plural(R.string.xWeeks, 1)));
    }

    int i = 0, foundIndex = 0;
    for (TTLOption option : ttlOptions) {
      boolean isValid;
      if (currentSelfDestructType == null) {
        isValid = option.isOff();
      } else {
        switch (currentSelfDestructType.getConstructor()) {
          case TdApi.MessageSelfDestructTypeImmediately.CONSTRUCTOR:
            isValid = option.isImmediate();
            break;
          case TdApi.MessageSelfDestructTypeTimer.CONSTRUCTOR:
            isValid = option.ttlTime == ((TdApi.MessageSelfDestructTypeTimer) currentSelfDestructType).selfDestructTime;
            break;
          default:
            Td.assertMessageSelfDestructType_58882d8c();
            throw Td.unsupported(currentSelfDestructType);
        }
      }
      if (isValid) {
        foundIndex = i;
        break;
      }
      i++;
    }

    final InfiniteRecyclerView<TTLOption> infiniteView = new InfiniteRecyclerView<>(context, true);
    if (useDarkMode) {
      infiniteView.forceDarkMode();
    }
    infiniteView.initWithItems(ttlOptions, foundIndex);

    final AlertDialog.Builder builder = new AlertDialog.Builder(context, useDarkMode ? R.style.DialogThemeDark : Theme.dialogTheme());
    builder.setTitle(Lang.getString(R.string.MessageLifetime));
    if (message != 0) {
      builder.setMessage(Lang.getString(message));
    }
    builder.setPositiveButton(Lang.getString(R.string.Done), (dialog, which) -> {
      TTLOption option = infiniteView.getCurrentItem();
      if (option != null && callback != null) {
        callback.runWithData(option);
      }
    });
    builder.setView(infiniteView);
    UI.getContext(context).showAlert(builder, useDarkMode ? ThemeSet.getBuiltinTheme(ThemeId.NIGHT_BLACK) : null);
  }

  public static final int MUTE_ENABLE = 0;
  public static final int MUTE_1HOUR = 3600;
  public static final int MUTE_8HOURS = 28800;
  public static final int MUTE_2DAYS = 172800;
  public static final int MUTE_FOREVER = Integer.MAX_VALUE;
  private static final int MUTE_MAX = 60 * 60 * 24 * 4; // 4 days

  /*public static void showMuteMore (ViewController<?> c, int buttonIndex) {
    String[] strings = {
      Lang.getString(R.string.Enable),
      Lang.plural(R.string.MuteForXHours, 1),
      Lang.plural(R.string.MuteForXHours, 8),
      Lang.plural(R.string.MuteForXDays, 2),
      Lang.getString(R.string.Disable)
    };
    c.showMore(new int[] {R.id.btn_menu_enable, R.id.btn_menu_1hour, R.id.btn_menu_8hours, R.id.btn_menu_2days, R.id.btn_menu_disable}, strings, buttonIndex);
  }*/

  public void toggleMute (final ViewController<?> context, final long chatId, boolean allowCustomize, @Nullable Runnable after) {
    TdApi.Chat chat = tdlib.chat(chatId);
    TdApi.ScopeNotificationSettings scopeSettings = tdlib.scopeNotificationSettings(chatId);
    if (chat == null || scopeSettings == null)
      return;
    if (scopeSettings.muteFor == 0 && !chat.notificationSettings.useDefaultMuteFor && chat.notificationSettings.muteFor > 0 && !allowCustomize) {
      // Unmuting chat while notifications are enabled globally
      tdlib.setMuteFor(chat.id, 0);
      U.run(after);
    } else {
      showMuteOptions(context, chatId, allowCustomize, after);
    }
  }

  public static void fillMuteOptions (IntList ids, IntList icons, StringList strings, @Nullable IntList colors, boolean needEnable, boolean needDisable, boolean needTemporary, boolean allowCustomize, boolean enableDefault, String defaultInfo, boolean prioritizeCustomization) {
    if (needEnable) {
      if (enableDefault) {
        strings.append(Lang.getString(R.string.NotificationsDefaultValue, Lang.getString(R.string.EnableNotifications)));
      } else {
        strings.append(R.string.EnableNotifications);
      }
      icons.append(R.drawable.baseline_notifications_24);
      ids.append(R.id.btn_menu_enable);
      if (colors != null) {
        colors.append(ViewController.OptionColor.NORMAL);
      }
    }

    if (defaultInfo != null) {
      strings.append(Lang.getString(R.string.NotificationsDefault, Lang.lowercase(defaultInfo)));
      icons.append(R.drawable.baseline_notifications_off_24);
      ids.append(R.id.btn_menu_resetToDefault);
      if (colors != null) {
        colors.append(ViewController.OptionColor.NORMAL);
      }
    }

    if (needDisable) {
      strings.append(R.string.MuteForever);
      icons.append(R.drawable.baseline_notifications_off_24);
      ids.append(R.id.btn_menu_disable);
      if (colors != null) {
        colors.append(ViewController.OptionColor.NORMAL);
      }
    }

    if (needTemporary) {
      strings.append(Lang.plural(R.string.MuteForXHours, 1));
      strings.append(Lang.plural(R.string.MuteForXHours, 8));
      strings.append(Lang.plural(R.string.MuteForXDays, 2));
      icons.append(R.drawable.baseline_notifications_paused_24);
      icons.append(R.drawable.baseline_notifications_paused_24);
      icons.append(R.drawable.baseline_notifications_paused_24);
      ids.append(R.id.btn_menu_1hour);
      ids.append(R.id.btn_menu_8hours);
      ids.append(R.id.btn_menu_2days);
      if (colors != null) {
        colors.append(ViewController.OptionColor.NORMAL);
        colors.append(ViewController.OptionColor.NORMAL);
        colors.append(ViewController.OptionColor.NORMAL);
      }
    }

    if (allowCustomize) {
      ids.append(R.id.btn_menu_customize);
      strings.append(R.string.NotificationsCustomize);
      icons.append(R.drawable.baseline_settings_24);
      if (colors != null) {
        colors.append(prioritizeCustomization ? ViewController.OptionColor.RED : ViewController.OptionColor.NORMAL);
      }
    }
  }

  public void showMuteOptions (final ViewController<?> context, final long chatId, final boolean allowCustomize, @Nullable Runnable after) {
    showMuteOptions(context, null, chatId, allowCustomize, after);
  }

  public void showMuteOptions (final ViewController<?> context, final TdApi.NotificationSettingsScope scope, final boolean allowCustomize) {
    showMuteOptions(context, scope, 0, allowCustomize, null);
  }

  private void showMuteOptions (final ViewController<?> context, final TdApi.NotificationSettingsScope scope, final long chatId, final boolean allowCustomize, @Nullable Runnable after) {
    if ((scope == null && chatId == 0) || (scope != null && chatId != 0))
      throw new IllegalArgumentException();

    final int capacity = 5;
    StringList strings = new StringList(capacity);
    IntList icons = new IntList(capacity);
    IntList ids = new IntList(capacity);
    IntList colors = null;
    String info = null;
    boolean areBlocked;

    if (scope != null) {
      areBlocked = tdlib.notifications().areNotificationsBlocked(scope);
      if (areBlocked)
        colors = new IntList(capacity);
      int muteFor = tdlib.scopeMuteFor(scope);
      boolean mutedForever = TD.isMutedForever(muteFor);
      fillMuteOptions(ids, icons, strings, colors, muteFor > 0, !mutedForever, !mutedForever, allowCustomize, false, null, areBlocked);
    } else {
      TdApi.Chat chat = tdlib.chat(chatId);
      TdApi.ScopeNotificationSettings scopeSettings = tdlib.scopeNotificationSettings(chatId);
      if (chat == null || scopeSettings == null) {
        Log.e("Can't open chat settings: hasChat:%b hasScope:%b", chat != null, scopeSettings != null);
        return;
      }
      areBlocked = tdlib.notifications().areNotificationsBlocked(chatId, true);
      if (areBlocked)
        colors = new IntList(capacity);
      if (chat.notificationSettings.useDefaultMuteFor) {
        if (scopeSettings.muteFor == 0) { // Muting chat while notifications are enabled globally
          fillMuteOptions(ids, icons, strings, colors, false, true, true, allowCustomize, false, null, areBlocked);
        } else { // Unmuting chat while notifications are disabled globally
          fillMuteOptions(ids, icons, strings, colors, true, false, false, allowCustomize, false, null, areBlocked);
          info = Lang.getString(R.string.NotificationsEnableOverride, Lang.lowercase(getValueForSettings(scopeSettings.muteFor)));
        }
      } else if (chat.notificationSettings.muteFor > 0) {
        if (scopeSettings.muteFor == 0) { // Unmuting chat while notifications are enabled globally
          fillMuteOptions(ids, icons, strings, colors, true, false, true, allowCustomize, false, null, areBlocked);
        } else { // Unmuting chat while notifications are disabled globally
          fillMuteOptions(ids, icons, strings, colors, true, false, false, allowCustomize, false, null, areBlocked);
          info = Lang.getString(R.string.NotificationsEnableOverride, Lang.lowercase(getValueForSettings(scopeSettings.muteFor)));
        }
      } else {
        if (scopeSettings.muteFor == 0) { // Muting chat while notifications are enabled globally
          fillMuteOptions(ids, icons, strings, colors, true, true, true, allowCustomize, true, null, areBlocked);
          // getValueForSettings(scopeSettings.muteFor).toLowerCase()
          info = Lang.getString(R.string.NotificationsDefaultInfo);
        } else { // Muting chat while notifications are disabled globally
          fillMuteOptions(ids, icons, strings, colors, false, !TD.isMutedForever(scopeSettings.muteFor), true, allowCustomize, false, getValueForSettings(scopeSettings.muteFor, true), areBlocked);
          info = Lang.getString(R.string.NotificationsDefaultInfo);
        }
      }
    }
    context.showOptions(info, ids.get(), strings.get(), colors != null ? colors.get() : null, icons.get(), (itemView, id) -> {
      if (id == R.id.btn_menu_customize) {
        SettingsNotificationController notificationController = new SettingsNotificationController(context.context(), tdlib);
        if (scope != null) {
          notificationController.setArguments(new SettingsNotificationController.Args(scope));
        } else {
          notificationController.setArguments(new SettingsNotificationController.Args(chatId));
        }
        context.navigateTo(notificationController);
        U.run(after);
        return true;
      }
      if (chatId != 0) {
        if (id == R.id.btn_menu_resetToDefault) {
          TdApi.Chat chat = tdlib.chatStrict(chatId);
          chat.notificationSettings.useDefaultMuteFor = true;
          tdlib.setChatNotificationSettings(chatId, chat.notificationSettings);
          U.run(after);
          return true;
        }
      }
      final int muteFor = getMuteDurationForId(id);
      if (scope != null) {
        tdlib.setScopeMuteFor(scope, muteFor);
      } else {
        tdlib.setMuteFor(chatId, muteFor);
      }
      U.run(after);
      return true;
    });
  }

  public int getIconForSetting (@Nullable TdApi.Chat chat) {
    final int muteFor = tdlib.chatMuteFor(chat);
    return muteFor >= MUTE_MAX ? R.drawable.baseline_notifications_off_24 : muteFor > 0 ? R.drawable.baseline_notifications_paused_24 : R.drawable.baseline_notifications_24;
  }

  public static String getValueForSettings (int muteFor) {
    return getValueForSettings(muteFor, false);
  }

  public void setValueForSetting (SettingView view, TdApi.NotificationSettingsScope scope, boolean allowStyle) {
    boolean areBlocked = tdlib.notifications().areNotificationsBlocked(scope);
    if (areBlocked) {
      view.setDataColorId(ColorId.textNegative);
      view.setData(R.string.NotificationsBlocked);
    } else {
      TdApi.ScopeNotificationSettings settings = tdlib.notifications().getScopeNotificationSettings(scope);
      view.setDataColorId(0);
      if (settings == null) {
        view.setData(Lang.getString(R.string.LoadingInformation));
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && allowStyle && settings.muteFor == 0) {
          int importance = tdlib.notifications().getDefaultPriorityOrImportance(scope);
          if (importance < android.app.NotificationManager.IMPORTANCE_DEFAULT) {
            int stringRes = getPriorityOrImportanceString(importance, tdlib.notifications().isDefaultSoundEnabled(scope), tdlib.notifications().isDefaultVibrateModeEnabled(scope));
            view.setData(Lang.getString(R.string.NotificationsEnabledHint, Lang.lowercase(Lang.getString(stringRes))));
            return;
          }
        }
        view.setData(getValueForSettings(settings.muteFor));
      }
    }
  }

  public void setValueForSetting (SettingView view, long chatId) {
    boolean areBlocked = tdlib.notifications().areNotificationsBlocked(chatId, true);
    if (areBlocked) {
      view.setDataColorId(ColorId.textNegative);
      view.setData(R.string.NotificationsBlocked);
    } else {
      TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
      view.setDataColorId(0);
      if (settings != null) {
        view.setData(getValueForSettings(tdlib.chatMuteFor(chatId)));
      } else {
        view.setData(R.string.LoadingInformation);
      }
    }
  }

  public static String getValueForSettings (int muteFor, boolean isDefault) {
    if (muteFor > 0) {
      if (muteFor >= MUTE_MAX) {
        return Lang.getString(isDefault ? R.string.NotificationsDefaultDisabled : R.string.NotificationsDisabled);
      }

      String text;
      int minutes = Math.round((float) muteFor / 60f);
      int hours = Math.round((float) muteFor / (60f * 60f));
      int days = Math.round((float) muteFor / (60f * 60f * 24f));
      if (days > 0) {
        text = Lang.plural(R.string.xDays, days);
      } else if (hours > 0) {
        text = Lang.plural(R.string.xHours, hours);
      } else {
        text = Lang.plural(R.string.xMinutes, Math.max(0, minutes));
      }
      return Lang.getString(isDefault ? R.string.NotificationsDefaultUnmutesIn : R.string.UnmutesInX, text);
    } else {
      return Lang.getString(isDefault ? R.string.NotificationsDefaultEnabled : R.string.NotificationsEnabled);
    }
  }

  public static int getMuteDurationForId (@IdRes int id) {
    if (id == R.id.btn_menu_enable) {
      return TdlibUi.MUTE_ENABLE;
    } else if (id == R.id.btn_menu_1hour) {
      return TdlibUi.MUTE_1HOUR;
    } else if (id == R.id.btn_menu_8hours) {
      return TdlibUi.MUTE_8HOURS;
    } else if (id == R.id.btn_menu_2days) {
      return TdlibUi.MUTE_2DAYS;
    } else if (id == R.id.btn_menu_disable) {
      return TdlibUi.MUTE_FOREVER;
    }
    return -1;
  }

  public void unmute (long chatId) {
    tdlib.setMuteFor(chatId, 0);
  }

  // Profile helper

  public boolean handleProfileClick (ViewController<?> context, @Nullable View view, final @IdRes int id, TdApi.User user, boolean allowChangeNumber) {
    if (user == null) {
      return false;
    }
    if (id == R.id.btn_username) {
      IntList ids = new IntList(4);
      IntList icons = new IntList(4);
      StringList strings = new StringList(4);

      boolean canEdit;

      if (tdlib.isSelfUserId(user.id)) {
        if (!Td.hasUsername(user)) {
          context.navigationController().navigateTo(new EditUsernameController(context.context(), context.tdlib()));
          return true;
        }

        ids.append(R.id.btn_username_edit);
        icons.append(R.drawable.baseline_edit_24);
        strings.append(R.string.EditUsername);

        canEdit = true;
      } else {
        canEdit = false;
      }

        /*ids.append(R.id.btn_username_copy);
        icons.append(R.drawable.ic_copy_gray);
        strings.append(R.string.Copy);*/

      ids.append(R.id.btn_username_copy_link);
      icons.append(R.drawable.baseline_link_24);
      strings.append(R.string.CopyLink);

      ids.append(R.id.btn_username_share);
      icons.append(R.drawable.baseline_forward_24);
      strings.append(R.string.ShareLink);

      context.showOptions(canEdit ? null : "@" + Td.primaryUsername(user), ids.get(), strings.get(), null, icons.get());

      return true;
    } else if (id == R.id.btn_phone) {
      if (!StringUtils.isEmpty(user.phoneNumber)) {
        int size = allowChangeNumber ? 4 : 3;
        IntList ids = new IntList(size);
        IntList icons = new IntList(size);
        StringList strings = new StringList(size);

        if (allowChangeNumber) {
          ids.append(R.id.btn_changePhoneNumber);
          icons.append(R.drawable.baseline_edit_24);
          strings.append(R.string.PhoneNumberChange);
        }

        ids.append(R.id.btn_phone_call);
        icons.append(R.drawable.baseline_phone_24);
        strings.append(R.string.Call);

        ids.append(R.id.btn_phone_copy);
        icons.append(R.drawable.baseline_content_copy_24);
        strings.append(R.string.Copy);

        ids.append(R.id.btn_phone_share);
        icons.append(R.drawable.baseline_forward_24);
        strings.append(R.string.ShareContact);

        String phoneNumber = Strings.formatPhone(user.phoneNumber);
        CharSequence message;
        if (allowChangeNumber) {
          message = phoneNumber;
        } else {
          message = getLinkText(user);
          if (message instanceof SpannableStringBuilder) {
            ((SpannableStringBuilder) message).append("\n\n").append(phoneNumber);
          } else if (!StringUtils.isEmpty(message)) {
            message = message + "\n\n" + phoneNumber;
          } else {
            message = phoneNumber;
          }
        }
        context.showOptions(message, ids.get(), strings.get(), null, icons.get());
      } else {
        if (!allowChangeNumber) {
          CharSequence text = getLinkText(user);
          if (text != null) {
            if (view != null) {
              context.context().tooltipManager().builder(view).show(context, tdlib, R.drawable.baseline_info_24, text);
            } else {
              UI.showToast(text, Toast.LENGTH_SHORT);
            }
          }
        }
      }
      return true;
    } else if (id == R.id.btn_addToGroup) {
      addToGroup(context, user.id);
      return true;
    }
    return false;
  }

  private CharSequence getLinkText (TdApi.User user) {
    if (user.isMutualContact) {
      return Lang.getStringBold(TD.hasPhoneNumber(user) ? R.string.ContactStateMutual : R.string.ContactStateMutualNoPhone, TD.getUserName(user));
    } else if (user.isContact) {
      return Lang.getStringBold(TD.hasPhoneNumber(user) ? R.string.ContactStateOutgoing : R.string.ContactStateOutgoingNoPhone, TD.getUserName(user));
    } else {
      return null;
    }
  }

  public boolean handleProfileOption (ViewController<?> context, final @IdRes int id, TdApi.User user) {
    if (user == null) {
      return false;
    }
    if (id == R.id.btn_username_edit) {
      context.navigationController().navigateTo(new EditUsernameController(context.context(), context.tdlib()));
      return true;
    } else if (id == R.id.btn_username_copy) {
      UI.copyText('@' + Td.primaryUsername(user), R.string.CopiedUsername);
      return true;
    } else if (id == R.id.btn_username_copy_link) {
      UI.copyText(context.tdlib().tMeUrl(user.usernames), R.string.CopiedLink);
      return true;
    } else if (id == R.id.btn_username_share) {
      shareUsername(context, user);
      return true;
    } else if (id == R.id.btn_phone_share) {
      shareUser(context, user);
      return true;
    } else if (id == R.id.btn_phone_copy) {
      UI.copyText('+' + user.phoneNumber, R.string.copied_phone);
      return true;
    } else if (id == R.id.btn_phone_call) {
      Intents.openNumber('+' + user.phoneNumber);
      return true;
    } else if (id == R.id.btn_changePhoneNumber) {
      context.navigationController().navigateTo(new SettingsPhoneController(context.context(), context.tdlib()));
      return true;
    }
    return false;
  }

  public boolean handleProfileMore (ViewController<?> context, final @IdRes int id, TdApi.User user, TdApi.UserFullInfo userFull) {
    if (id == R.id.more_btn_edit) {
      if (user != null) {
        EditNameController c = new EditNameController(context.context(), context.tdlib());
        if (context.tdlib().isSelfUserId(user.id)) {
          c.setMode(EditNameController.Mode.RENAME_SELF);
        } else {
          if (TD.canEditBot(user)) {
            c.setMode(EditNameController.Mode.RENAME_BOT);
          } else {
            c.setMode(EditNameController.Mode.RENAME_CONTACT);
          }
          c.setUser(user);
        }
        context.navigationController().navigateTo(c);
      }
      return true;
    } else if (id == R.id.more_btn_addToContacts) {
      if (user != null) {
        addContact(context, user);
      }
      return true;
    } else if (id == R.id.more_btn_logout) {
      logOut(context, true);
      return true;
    } else if (id == R.id.more_btn_addToGroup) {
      addToGroup(context, user.id);
      return true;
    } else if (id == R.id.more_btn_share) {
      shareUser(context, user);
      return true;
    }
    return false;
  }

  public void addContact (ViewController<?> context, TdApi.Contact contact) {
    if (contact == null) {
      return;
    }
    TdApi.User user = contact.userId != 0 ? tdlib.cache().user(contact.userId) : null;
    if (user != null) {
      addContact(context, user, contact.phoneNumber);
    } else {
      PhoneController controller = new PhoneController(context.context(), context.tdlib());
      controller.setMode(PhoneController.MODE_ADD_CONTACT);
      controller.setInitialData(contact.phoneNumber, contact.firstName, contact.lastName);
      context.navigationController().navigateTo(controller);
    }
  }

  public void addContact (ViewController<?> context, TdApi.User user ) {
    if (user != null) {
      addContact(context, user, StringUtils.isEmpty(user.phoneNumber) ? null : user.phoneNumber);
    }
  }

  public void addContact (ViewController<?> context, TdApi.User user, @Nullable String knownPhoneNumber) {
    if (user == null) {
      return;
    }
    EditNameController controller = new EditNameController(context.context(), context.tdlib());
    controller.setMode(EditNameController.Mode.ADD_CONTACT);
    controller.setUser(user);
    controller.setKnownPhoneNumber(knownPhoneNumber);
    context.navigationController().navigateTo(controller);

    /*if (!TD.hasPhoneNumber(user)) {

    } else {
      PhoneController controller = new PhoneController(context.context(), context.tdlib());
      controller.setMode(PhoneController.MODE_ADD_CONTACT);
      controller.setInitialData(user.id, user.phoneNumber, user.firstName, user.lastName);
      context.navigationController().navigateTo(controller);
    }*/
  }

  public void addAccount (BaseActivity context, boolean allowConfirm, boolean isDebug) {
    final int accountId = TdlibManager.instance().newAccount(isDebug);
    if (accountId == TdlibAccount.NO_ID) {
      return;
    }
    PhoneController c = new PhoneController(context, TdlibManager.getTdlib(accountId));
    c.setIsAccountAdd(true);
    context.navigation().navigateTo(c);
  }

  private void shareUsername (ViewController<?> context, TdApi.User user) {
    shareUsername(context, user, Td.primaryUsername(user));
  }

  private void shareUsername (ViewController<?> context, TdApi.User user, String username) {
    if (StringUtils.isEmpty(username))
      return;
    String link = tdlib.tMeUrl(username);
    String text, export;
    if (tdlib.isSelfUserId(user.id)) {
      text = Lang.getString(R.string.ShareTextMyLink2, link);
      export = Lang.getString(R.string.ShareTextMyLink, link);
    } else {
      String name = tdlib.cache().userName(user.id);
      text = Lang.getString(R.string.ShareTextProfileLink2, name, link);
      export = Lang.getString(R.string.ShareTextProfileLink, name, link);
    }
    ShareController c = new ShareController(context.context(), context.tdlib());
    c.setArguments(new ShareController.Args(text).setShare(export, Lang.getString(R.string.ShareLink)));
    c.show();
  }

  private void shareUser (ViewController<?> context, TdApi.User user) {
    String username = tdlib.cache().userUsername(user.id);
    String name = tdlib.cache().userName(user.id);
    String url = StringUtils.isEmpty(username) ? null : tdlib.tMeUrl(username);
    if (TD.isBot(user)) {
      String text = Lang.getString(R.string.ShareTextLink, name, url);
      ShareController c = new ShareController(context.context(), context.tdlib());
      c.setArguments(new ShareController.Args(text).setShare(Lang.getString(R.string.ShareTextBotLink, url), Lang.getString(R.string.ShareBtnBot)));
      c.show();
    } else {
      ShareController c = new ShareController(context.context(), context.tdlib());
      ShareController.Args args = new ShareController.Args(user);
      c.setArguments(args);
      c.show();
    }
  }

  public void shareUrl (TdlibDelegate context, String url) {
    shareUrl(context, url, null, url, null);
  }

  public void shareUrl (TdlibDelegate context, String url, String internalShareText, String externalShareText, String externalShareButton) {
    if (!StringUtils.isEmpty(url)) {
      ShareController c = new ShareController(context.context(), context.tdlib());
      c.setArguments(new ShareController.Args(!StringUtils.isEmpty(internalShareText) ? internalShareText : url)
        .setShare(externalShareText, externalShareButton)
      );
      c.show();
    }
  }

  public void shareProxyUrl (TdlibDelegate context, String url) {
    shareUrl(context, url,
      Lang.getString(R.string.ShareTextProxyLink2, url),
      Lang.getString(R.string.ShareTextProxyLink, url),
      Lang.getString(R.string.ShareBtnProxy)
    );
  }

  public void shareLanguageUrl (TdlibDelegate context, TdApi.LanguagePackInfo languagePackInfo) {
    String url = context.tdlib().tMeLanguageUrl(languagePackInfo.id);
    String text = Lang.getString(R.string.ShareTextLanguageLink, languagePackInfo.name, url);
    ShareController c = new ShareController(context.context(), context.tdlib());
    c.setArguments(new ShareController.Args(text).setShare(text, Lang.getString(R.string.ShareBtnLanguage)));
    c.show();
  }

  public void shareStickerSetUrl (TdlibDelegate context, TdApi.StickerSetInfo stickerSet) {
    String link = tdlib.tMeStickerSetUrl(stickerSet.name);
    String title = stickerSet.title;
    ShareController c = new ShareController(context.context(), context.tdlib());
    c.setArguments(new ShareController.Args(Lang.getString(R.string.ShareTextStickerLink2, title, link)).setShare(Lang.getString(R.string.ShareTextStickerLink, title, link), Lang.getString(R.string.ShareBtnStickerSet)));
    c.show();
  }

  public void shareText (TdlibDelegate context, String text) {
    ShareController c = new ShareController(context.context(), context.tdlib());
    c.setArguments(new ShareController.Args(text).setExport(text));
    c.show();
  }

  // Picker users

  private void addToGroup (final TdlibDelegate context, long userId) {
    if (TdlibManager.inBackgroundThread()) {
      tdlib.runOnUiThread(() -> addToGroup(context, userId));
      return;
    }
    ChatsController c = new ChatsController(context.context(), context.tdlib());
    c.setArguments(new ChatsController.Arguments(ChatFilter.groupsInviteFilter(tdlib), new ChatsController.PickerDelegate() {
      @Override
      public boolean onChatPicked (TdApi.Chat chat, Runnable onDone) {
        if (!tdlib.canInviteUsers(chat)) {
          UI.showToast(R.string.YouCantInviteMembers, Toast.LENGTH_SHORT);
          return false;
        }
        if (chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR) {
          tdlib.client().send(new TdApi.AddChatMember(chat.id, userId, 0), tdlib.okHandler());
        } else if (chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
          tdlib.client().send(new TdApi.AddChatMembers(chat.id, new long[] {userId}), tdlib.okHandler());
        } else {
          return false;
        }
        return true;
      }

      @Override
      public int getTitleStringRes () {
        return R.string.BotInvite;
      }
    }));
    context.context().navigation().navigateTo(c);
  }

  public void addToGroup (TdlibDelegate context, final TGBotStart start, boolean isGame) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      post(() -> addToGroup(context, start, isGame));
      return;
    }
    ChatsController c = new ChatsController(context.context(), context.tdlib());
    c.setArguments(new ChatsController.Arguments(isGame ? ChatFilter.gamesFilter(tdlib) : ChatFilter.groupsInviteFilter(tdlib), new ChatsController.PickerDelegate() {
      @Override
      public boolean onChatPicked (final TdApi.Chat chat, final Runnable onDone) {
        if (!tdlib.canSendBasicMessage(chat)) {
          UI.showToast(R.string.YouCantSendMessages, Toast.LENGTH_SHORT);
          return false;
        }
        if (!isGame && !tdlib.canInviteUsers(chat)) {
          tdlib.client().send(new TdApi.GetChatMember(chat.id, new TdApi.MessageSenderUser(start.getUserId())), object -> {
            if (object.getConstructor() == TdApi.ChatMember.CONSTRUCTOR) {
              TdApi.ChatMember member = (TdApi.ChatMember) object;
              if (TD.isMember(member.status)) {
                tdlib.ui().post(onDone);
                return;
              }
            }
            UI.showToast(R.string.YouCantInviteMembers, Toast.LENGTH_SHORT);
          });
          return false;
        }
        return true;
      }

      @Override
      public int getTitleStringRes () {
        return isGame ? R.string.GameInvite : R.string.BotInvite;
      }

      @Override
      public Object getShareItem () {
        return start;
      }
    }));
    context.context().navigation().navigateTo(c);
  }

  // Logs

  public static void sendTdlibLogs (final ViewController<?> context, final boolean old, final boolean export) {
    File tdlibLogFile = TdlibManager.getLogFile(old);
    if (tdlibLogFile == null || !tdlibLogFile.exists()) {
      UI.showToast("Log does not exist", Toast.LENGTH_SHORT);
      return;
    }
    long size = tdlibLogFile.length();
    if (size == 0) {
      UI.showToast("Log is empty", Toast.LENGTH_SHORT);
      return;
    }

    ShareController share = new ShareController(context.context(), export ? null : context.tdlib());
    share.setArguments(new ShareController.Args(tdlibLogFile.getPath(), "text/plain"));
    share.show();
  }

  public static void clearLogs (final boolean old, final RunnableLong after) {
    Background.instance().post(() -> {
      try {
        long removedSize = TdlibManager.deleteLogFile(old);
        final String msg;
        if (removedSize == -1) {
          msg = Lang.getString(R.string.TdlibLogClearFail);
        } else if (removedSize == 0) {
          msg = Lang.getString(R.string.TdlibLogClearEmpty);
        } else {
          msg = Lang.getString(R.string.TdlibLogClearOk, Strings.buildSize(removedSize));
        }
        UI.post(() -> {
          if (!StringUtils.isEmpty(msg)) {
            UI.showToast(msg, Toast.LENGTH_SHORT);
          }
          if (after != null) {
            after.runWithLong(TdlibManager.getLogFileSize(old));
          }
        });
      } catch (Throwable t) {
        Log.w("Cannot work with TG log", t);
      }
    });
  }

  // Stickers

  private Client.ResultHandler newStickerSetHandler (final TdlibDelegate context, @Nullable UrlOpenParameters openParameters) {
    final ViewController<?> currentController = context.context().navigation().getCurrentStackItem();
    return object -> context.tdlib().ui().post(() -> {
      if (currentController == null || currentController.isDestroyed()) {
        return;
      }
      switch (object.getConstructor()) {
        case TdApi.StickerSet.CONSTRUCTOR: {
          TdApi.StickerSet stickerSet = (TdApi.StickerSet) object;
          StickerSetWrap.showStickerSet(context, stickerSet);
          break;
        }
        case TdApi.StickerSetInfo.CONSTRUCTOR: {
          TdApi.StickerSetInfo stickerSetInfo = (TdApi.StickerSetInfo) object;
          StickerSetWrap.showStickerSet(context, stickerSetInfo);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, TD.toErrorString(object), openParameters);
          break;
        }
      }
    });
  }

  public void showStickerSet (TdlibDelegate context, String name, @Nullable UrlOpenParameters openParameters) {
    // TODO progress
    tdlib.client().send(new TdApi.SearchStickerSet(name, false), newStickerSetHandler(context, openParameters));
  }

  public void showStickerSet (TdlibDelegate context, long setId, @Nullable UrlOpenParameters openParameters) {
    // TODO progress
    tdlib.client().send(new TdApi.GetStickerSet(setId), newStickerSetHandler(context, openParameters));
  }

  public void showStickerSets (TdlibDelegate context, long[] setIds, boolean isEmojiPacks, @Nullable UrlOpenParameters openParameters) {
    if (setIds.length == 1) {
      showStickerSet(context, setIds[0], openParameters);
    } else {
      StickerSetWrap.showStickerSets(context, setIds, isEmojiPacks);
    }
  }

  // Confirm phone

  public void confirmPhone (TdlibDelegate context, TdApi.AuthenticationCodeInfo info, String phoneNumber) {
    PasswordController c = new PasswordController(context.context(), context.tdlib());
    c.setArguments(new PasswordController.Args(PasswordController.MODE_CODE_PHONE_CONFIRM, info, Strings.formatPhone(phoneNumber)));
    context.context().navigation().navigateTo(c);
  }

  // Open chat

  private static void showAccessError (TdlibDelegate context, @Nullable UrlOpenParameters urlOpenParameters, int error, boolean isChannel) {
    int res;
    switch (error) {
      case Tdlib.CHAT_ACCESS_BANNED:
        res = isChannel ? R.string.ChatAccessRestrictedChannel : R.string.ChatAccessRestrictedGroup;
        break;
      case Tdlib.CHAT_ACCESS_PRIVATE:
        res = isChannel ? R.string.ChatAccessPrivateChannel : R.string.ChatAccessPrivateGroup;
        break;
      case Tdlib.CHAT_ACCESS_FAIL:
      default:
        res = R.string.ChatAccessFailed;
        break;
    }
    showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, Lang.getString(res), urlOpenParameters);
  }

  private static final int CHAT_OPTION_KEEP_STACK = 1;
  private static final int CHAT_OPTION_NO_OPEN = 1 << 1;
  private static final int CHAT_OPTION_NEED_PRIVATE_PROFILE = 1 << 2;
  private static final int CHAT_OPTION_ENSURE_HIGHLIGHT_AVAILABILITY = 1 << 3;
  private static final int CHAT_OPTION_PASSCODE_UNLOCKED = 1 << 4;
  private static final int CHAT_OPTION_REMOVE_DUPLICATES = 1 << 5;
  private static final int CHAT_OPTION_SCHEDULED_MESSAGES = 1 << 6;
  private static final int CHAT_OPTION_OPEN_PROFILE_IF_DUPLICATE = 1 << 7;

  public static class ChatOpenParameters {
    public int options;
    public RunnableLong after;
    public Runnable onDone;
    public Object shareItem;

    public boolean highlightSet;
    public int highlightMode;
    public MessageId highlightMessageId;

    public String searchQuery;
    public MessageId foundMessage;

    public @Nullable UrlOpenParameters urlOpenParameters;

    public TdApi.ChatList chatList;
    public String inviteLink;
    public TdApi.ChatInviteLinkInfo inviteLinkInfo;
    public ThreadInfo threadInfo;
    public TdApi.SearchMessagesFilter filter;
    public TdApi.InternalLinkTypeVideoChat videoChatOrLiveStreamInvitation;
    public TdApi.FormattedText fillDraft;

    private void onDone () {
      if (onDone != null) {
        onDone.run();
        onDone = null;
      }
    }

    public boolean saveInstanceState (Bundle outState, String keyPrefix) {
      if (after != null || onDone != null || shareItem != null)
        return false;
      if (options != 0)
        outState.putInt(keyPrefix + "cp_options", options);
      if (highlightSet)
        outState.putBoolean(keyPrefix + "cp_highlightSet", highlightSet);
      if (highlightMode != 0)
        outState.putInt(keyPrefix + "cp_highlightMode", highlightMode);
      if (highlightMessageId != null)
        TD.saveMessageId(outState, keyPrefix + "cp_highlightMessageId", highlightMessageId);
      if (chatList != null)
        outState.putString(keyPrefix + "cp_chatList", TD.makeChatListKey(chatList));
      if (threadInfo != null)
        threadInfo.saveTo(outState, keyPrefix + "cp_messageThread");
      if (filter != null)
        Td.put(outState, keyPrefix + "cp_filter", filter);
      return true;
    }

    public static @Nullable ChatOpenParameters restoreInstanceState (Tdlib tdlib, Bundle in, String keyPrefix) {
      ChatOpenParameters params = new ChatOpenParameters();
      params.options = in.getInt(keyPrefix + "cp_options", 0);
      params.highlightSet = in.getBoolean(keyPrefix + "cp_highlightSet", false);
      params.highlightMode = in.getInt(keyPrefix + "cp_highlightMode", 0);
      params.highlightMessageId = TD.restoreMessageId(in, keyPrefix + "cp_highlightMessageId");
      params.chatList = TD.chatListFromKey(in.getString(keyPrefix + "cp_chatList", null));
      ThreadInfo threadInfo = ThreadInfo.restoreFrom(tdlib, in, keyPrefix + "cp_messageThread");
      if (threadInfo == ThreadInfo.INVALID)
        return null;
      params.threadInfo = threadInfo;
      params.filter = Td.restoreSearchMessagesFilter(in, keyPrefix + "cp_filter");
      return params;
    }

    public ChatOpenParameters () { }

    public ChatOpenParameters noOpen () {
      this.options |= CHAT_OPTION_NO_OPEN;
      return this;
    }

    public ChatOpenParameters urlOpenParameters (UrlOpenParameters urlOpenParameters) {
      this.urlOpenParameters = urlOpenParameters;
      return this;
    }

    public ChatOpenParameters inviteLink (String inviteLink, TdApi.ChatInviteLinkInfo inviteLinkInfo) {
      this.inviteLink = inviteLink;
      this.inviteLinkInfo = inviteLinkInfo;
      return this;
    }

    public ChatOpenParameters fillDraft (TdApi.FormattedText fillDraft) {
      this.fillDraft = fillDraft;
      return this;
    }

    public ChatOpenParameters chatList (TdApi.ChatList chatList) {
      this.chatList = chatList;
      return this;
    }

    public ChatOpenParameters messageThread (ThreadInfo threadInfo) {
      if (threadInfo != null && BitwiseUtils.hasFlag(options, CHAT_OPTION_SCHEDULED_MESSAGES)) {
        throw new IllegalArgumentException();
      }
      this.threadInfo = threadInfo;
      return this;
    }

    public ChatOpenParameters searchFilter (TdApi.SearchMessagesFilter filter) {
      this.filter = filter;
      return this;
    }

    public ChatOpenParameters passcodeUnlocked () {
      this.options |= CHAT_OPTION_PASSCODE_UNLOCKED;
      return this;
    }

    public ChatOpenParameters videoChatOrLiveStreamInvitation (TdApi.InternalLinkTypeVideoChat videoChatOrLiveStreamInvitation) {
      this.videoChatOrLiveStreamInvitation = videoChatOrLiveStreamInvitation;
      return this;
    }

    public ChatOpenParameters keepStack () {
      this.options |= CHAT_OPTION_KEEP_STACK;
      return this;
    }

    public ChatOpenParameters scheduledOnly () {
      if (this.threadInfo != null) {
        throw new IllegalArgumentException();
      }
      this.options |= CHAT_OPTION_SCHEDULED_MESSAGES;
      return this;
    }

    public ChatOpenParameters removeDuplicates () {
      this.options |= CHAT_OPTION_REMOVE_DUPLICATES;
      return this;
    }

    public ChatOpenParameters openProfileInCaseOfPrivateChat () {
      this.options |= CHAT_OPTION_NEED_PRIVATE_PROFILE;
      return this;
    }

    public ChatOpenParameters openProfileInCaseOfDuplicateChat () {
      this.options |= CHAT_OPTION_OPEN_PROFILE_IF_DUPLICATE;
      return this;
    }

    public ChatOpenParameters after (RunnableLong after) {
      this.after = after;
      return this;
    }

    public ChatOpenParameters onDone (Runnable onDone) {
      this.onDone = onDone;
      return this;
    }

    public ChatOpenParameters shareItem (Object shareItem) {
      this.shareItem = shareItem;
      return this;
    }

    public ChatOpenParameters ensureHighlightAvailable () {
      this.options |= CHAT_OPTION_ENSURE_HIGHLIGHT_AVAILABILITY;
      return this;
    }

    public ChatOpenParameters highlightMessage (TdApi.Message message) {
      return highlightMessage(new MessageId(message.chatId, message.id));
    }

    public ChatOpenParameters foundMessage (String query, TdApi.Message message) {
      this.foundMessage = new MessageId(message.chatId, message.id);
      this.searchQuery = query;
      return highlightMessage(foundMessage);
    }

    public ChatOpenParameters highlightMessage (MessageId highlightMessageId) {
      this.highlightSet = true;
      this.highlightMode = MessagesManager.HIGHLIGHT_MODE_NORMAL;
      this.highlightMessageId = highlightMessageId;
      return this;
    }

    public ChatOpenParameters highlightMessage (int highlightMode, MessageId highlightMessageId) {
      this.highlightSet = true;
      this.highlightMode = highlightMode;
      this.highlightMessageId = highlightMessageId;
      return this;
    }
  }

  private void showChatOpenError (TdApi.Function<?> createRequest, TdApi.Error error, @Nullable ChatOpenParameters parameters) {
    if (!UI.inUiThread()) {
      tdlib.ui().post(() -> {
        showChatOpenError(createRequest, error, parameters);
      });
      return;
    }
    CharSequence message = TD.toErrorString(error);
    if (!StringUtils.isEmpty(message)) {
      switch (error.message) {
        case "USERNAME_NOT_OCCUPIED": {
          if (createRequest.getConstructor() == TdApi.SearchPublicChat.CONSTRUCTOR) {
            message = Lang.getStringBold(R.string.UsernameNotOccupied, ((TdApi.SearchPublicChat) createRequest).username);
          }
          break;
        }
        case "INVITE_REQUEST_SENT": {
          if (parameters != null && parameters.inviteLinkInfo != null && parameters.inviteLinkInfo.createsJoinRequest) {
            message = Lang.getStringBold(TD.isChannel(parameters.inviteLinkInfo.type) ? R.string.RequestJoinChannelSent : R.string.RequestJoinGroupSent, parameters.inviteLinkInfo.title);
          }
          break;
        }
      }
      showLinkTooltip(tdlib, R.drawable.baseline_error_24, message, parameters != null ? parameters.urlOpenParameters : null);
    }
  }

  private static void showLinkTooltip (Tdlib tdlib, int iconRes, CharSequence message, UrlOpenParameters urlOpenParameters) {
    if (message == null)
      throw new IllegalArgumentException();
    if (!UI.inUiThread()) {
      tdlib.ui().post(() -> {
        showLinkTooltip(tdlib, iconRes, message, urlOpenParameters);
      });
      return;
    }
    if (urlOpenParameters != null && urlOpenParameters.tooltip != null && urlOpenParameters.tooltip.hasVisibleTarget()) {
      if (iconRes == 0) {
        urlOpenParameters.tooltip.show(tdlib, message).hideDelayed(3500, TimeUnit.MILLISECONDS);
      } else {
        new TooltipOverlayView.TooltipBuilder(urlOpenParameters.tooltip).icon(iconRes).show(tdlib, message).hideDelayed(3500, TimeUnit.MILLISECONDS);
      }
    } else {
      UI.showToast(message, Toast.LENGTH_SHORT);
    }
  }

  public void openChat (final TdlibDelegate context, final TdApi.MessageSender senderId, @Nullable ChatOpenParameters openParameters) {
    long chatId = Td.getSenderId(senderId);
    final TdApi.Function<TdApi.Chat> function;
    switch (senderId.getConstructor()) {
      case TdApi.MessageSenderUser.CONSTRUCTOR:
        function = new TdApi.CreatePrivateChat(((TdApi.MessageSenderUser) senderId).userId, false);
        break;
      case TdApi.MessageSenderChat.CONSTRUCTOR:
        function = new TdApi.GetChat(((TdApi.MessageSenderChat) senderId).chatId);
        break;
      default:
        Td.assertMessageSender_439d4c9c();
        throw Td.unsupported(senderId);
    }
    openChat(context, chatId, function, openParameters);
  }

  private void openChat (final TdlibDelegate context, final long chatId, final TdApi.Function<?> createRequest, final @Nullable ChatOpenParameters params) {
    if (chatId != 0) {
      final TdApi.Chat chat = tdlib.chat(chatId);
      if (chat != null) {
        openChat(context, chat, params);
        return;
      }
    }
    // TODO progress
    tdlib.client().send(createRequest, object -> {
      switch (object.getConstructor()) {
        case TdApi.Chat.CONSTRUCTOR:
          openChat(context, tdlib.objectToChat(object), params);
          break;
        case TdApi.SupergroupFullInfo.CONSTRUCTOR:
          openChat(context, ((TdApi.SupergroupFullInfo) object).linkedChatId, params);
          break;
        case TdApi.Error.CONSTRUCTOR:
          showChatOpenError(createRequest, (TdApi.Error) object, params);
          if (params != null)
            tdlib.ui().post(params::onDone);
          break;
      }
    });
  }

  public void openChat (final TdlibDelegate context, final @NonNull TdApi.Chat chatFinal, final @Nullable ChatOpenParameters params) {
    if (params != null && params.highlightMessageId != null) {
      params.highlightMessageId = new MessageId(chatFinal.id, params.highlightMessageId.getMessageId(), params.highlightMessageId.getOtherMessageIds());
    }
    if (params != null && params.highlightSet && params.highlightMessageId != null && (params.options & CHAT_OPTION_ENSURE_HIGHLIGHT_AVAILABILITY) != 0) {
      // Checking if post is available
      TdApi.Function<?> function;
      if (params.highlightMessageId.getOtherMessageIds() != null && params.highlightMessageId.getOtherMessageIds().length > 0) {
        function = new TdApi.GetMessages(params.highlightMessageId.getChatId(), ArrayUtils.addElement(params.highlightMessageId.getOtherMessageIds(), params.highlightMessageId.getMessageId()));
      } else {
        function = new TdApi.GetMessage(params.highlightMessageId.getChatId(), params.highlightMessageId.getMessageId());
      }
      tdlib.client().send(function, object -> {
        boolean error = true;
        switch (object.getConstructor()) {
          case TdApi.Message.CONSTRUCTOR: {
            error = false;
            break;
          }
          case TdApi.Messages.CONSTRUCTOR: {
            TdApi.Message[] messages = ((TdApi.Messages) object).messages;
            for (TdApi.Message message : messages) {
              if (message != null) {
                error = false;
                break;
              }
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.i("Message not found: %s", TD.toErrorString(object));
            break;
          }
        }
        if (error) {
          if (!(context instanceof MessagesController && ((MessagesController) context).compareChat(chatFinal.id, params != null ? params.threadInfo : null))) {
            UI.showToast(TD.isChannel(chatFinal.type) ? R.string.PostNotFound : R.string.MessageNotFound, Toast.LENGTH_SHORT);
            params.options &= ~CHAT_OPTION_ENSURE_HIGHLIGHT_AVAILABILITY;
            params.highlightSet = false;
            openChat(context, chatFinal, params);
          } else {
            tdlib.ui().post(() -> {
              showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, Lang.getString(TD.isChannel(chatFinal.type) ? R.string.PostNotFound : R.string.MessageNotFound), params.urlOpenParameters);
              params.onDone();
            });
          }
        } else {
          params.options &= ~CHAT_OPTION_ENSURE_HIGHLIGHT_AVAILABILITY;
          openChat(context, chatFinal, params);
        }
      });
      return;
    }

    if (!UI.inUiThread()) {
      tdlib.runOnUiThread(() -> openChat(context, chatFinal, params));
      return;
    }

    final TdApi.Chat chat = tdlib.syncChat(chatFinal);
    if (chat == null) {
      Log.e("Unable to open chat %d: chat is no longer available in memory", chatFinal.id);
      if (params != null)
        params.onDone();
      return;
    }

    NavigationController navigation = context.context().navigation();

    if ((params == null || (params.options & CHAT_OPTION_PASSCODE_UNLOCKED) == 0) && tdlib.hasPasscode(chat)) {
      PasscodeController c = new PasscodeController(context.context(), context.tdlib());
      c.setArguments(new PasscodeController.Args(chat, tdlib.chatPasscode(chat), params));
      c.setPasscodeMode(PasscodeController.MODE_UNLOCK);
      if (navigation.isEmpty()) {
        navigation.initController(c);
        MainController main = new MainController(context.context(), context.tdlib());
        main.getValue();
        navigation.getStack().insert(main, 0);
      } else {
        navigation.navigateTo(c);
      }
      if (params != null)
        params.onDone();
      return;
    }

    final UrlOpenParameters urlOpenParameters = params != null ? params.urlOpenParameters : null;

    int accessState = tdlib.chatAccessState(chat);
    if (accessState < Tdlib.CHAT_ACCESS_OK) {
      if (params != null && params.inviteLinkInfo != null && params.inviteLinkInfo.createsJoinRequest) {
        showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, Lang.getString(TD.isChannel(params.inviteLinkInfo.type) ? R.string.RequestJoinChannelSent : R.string.RequestJoinGroupSent, params.inviteLinkInfo.title), urlOpenParameters);
      } else {
        showAccessError(context, urlOpenParameters, accessState, tdlib.isChannel(chat.id));
      }
      if (params != null)
        params.onDone();
      return;
    }

    final RunnableLong after = params != null ? params.after : null;
    final Object shareItem = params != null ? params.shareItem : null;
    final TdApi.ChatList chatList = params != null ? params.chatList : null;
    final int options = params != null ? params.options : 0;
    final boolean onlyScheduled = (options & CHAT_OPTION_SCHEDULED_MESSAGES) != 0;
    final TdApi.InternalLinkTypeVideoChat voiceChatInvitation = params != null ? params.videoChatOrLiveStreamInvitation : null;
    final ThreadInfo messageThread = params != null ? params.threadInfo : null;
    final TdApi.SearchMessagesFilter filter = params != null ? params.filter : null;
    final MessagesController.Referrer referrer = params != null && !StringUtils.isEmpty(params.inviteLink) ? new MessagesController.Referrer(params.inviteLink) : null;
    final TdApi.FormattedText forceDraft = params != null && !Td.isEmpty(params.fillDraft) ? params.fillDraft : null;

    if ((options & CHAT_OPTION_NEED_PRIVATE_PROFILE) != 0 && TD.isPrivateChat(chat.type)) {
      openChatProfile(context, chat, messageThread, urlOpenParameters);
      params.onDone();
      return;
    }

    if ((options & CHAT_OPTION_NO_OPEN) != 0) {
      if (after != null) {
        after.runWithLong(chat.id);
      }
      params.onDone();
      return;
    }

    final int highlightMode;
    final MessageId highlightMessageId;
    if (params != null && params.highlightSet) {
      highlightMode = params.highlightMode;
      highlightMessageId = params.highlightMessageId;
    } else {
      highlightMode = MessagesManager.getAnchorHighlightMode(tdlib.id(), chat, messageThread);
      highlightMessageId = MessagesManager.getAnchorMessageId(tdlib.id(), chat, messageThread, highlightMode);
    }

    final boolean isSelfChat = tdlib.isSelfChat(chat.id);

    boolean doneOpen = false;
    if (context instanceof MessagesController && !((MessagesController) context).inPreviewMode() && ((MessagesController) context).compareChat(chat.id, messageThread, onlyScheduled)) {
      boolean doneSomething = false;
      if (highlightMode == MessagesManager.HIGHLIGHT_MODE_NORMAL && highlightMessageId != null) {
        ((MessagesController) context).highlightMessage(highlightMessageId, urlOpenParameters);
        doneSomething = true;
      }
      if (shareItem != null) {
        ((MessagesController) context).shareItem(shareItem);
        doneSomething = true;
      }
      if (voiceChatInvitation != null) {
        ((MessagesController) context).openVoiceChatInvitation(voiceChatInvitation);
        doneSomething = true;
      }
      if (forceDraft != null) {
        ((MessagesController) context).fillDraft(forceDraft, true);
        doneSomething = true;
      }

      if (BitwiseUtils.hasFlag(options, CHAT_OPTION_OPEN_PROFILE_IF_DUPLICATE)) {
        openChatProfile(context, chat, messageThread, urlOpenParameters);
        doneSomething = true;
      }

      if (!doneSomething) {
        // TODO animate header
        UI.forceVibrateError(context.context().getContentView());
      }
      doneOpen = true;
    }
    if (!doneOpen && highlightMode == MessagesManager.HIGHLIGHT_MODE_NORMAL && highlightMessageId != null) {
      ViewController<?> c = context.context().navigation().getCurrentStackItem();
      if (c != null && c != context && c.tdlib() == context.tdlib() && c instanceof MessagesController && !((MessagesController) c).inPreviewMode() && ((MessagesController) c).compareChat(chat.id, messageThread, onlyScheduled)) {
        ((MessagesController) c).highlightMessage(highlightMessageId, urlOpenParameters);
        doneOpen = true;
      }
    }
    if (doneOpen) {
      if (after != null) {
        after.runWithLong(chat.id);
      }
      if (params != null) {
        params.onDone();
      }
      return;
    }

    final MessagesController controller;
    // boolean reused = false;

    ViewController<?> current = context.context().navigation().getCurrentStackItem();

    if (!(context.context() instanceof MainActivity) || context instanceof MessagesController || isSelfChat || current == ((MainActivity) context.context()).getMessagesController(tdlib, false)) {
      controller = new MessagesController(context.context(), context.tdlib());
    } else {
      MessagesController m = ((MainActivity) context.context()).getMessagesController(tdlib, true);
      m.finishSelectMode(-2);
      int i = 0;
      if (navigation != null) {
        final NavigationStack stack = navigation.getStack();
        for (ViewController<?> c : stack.getAll()) {
          if (c instanceof MessagesController && c == m) {
            if ((options & CHAT_OPTION_KEEP_STACK) != 0) {
              m = new MessagesController(context.context(), context.tdlib());
            } else {
              stack.remove(i);
              m.destroy();
            }
            break;
          }
          i++;
        }
      }
      controller = m;
    }

    controller.setShareItem(shareItem);
    if (params != null && (params.options & CHAT_OPTION_PASSCODE_UNLOCKED) != 0) {
      controller.addOneShotFocusListener(() -> {
        ViewController<?> prevStackItem = controller.stackItemAt(controller.stackSize() - 2);
        if (prevStackItem instanceof PasscodeController && prevStackItem.getChatId() == controller.getChatId()) {
          controller.destroyStackItemAt(controller.stackSize() - 2);
        }
      });
    }

    final Runnable actor = () -> {
      if (after != null) {
        after.runWithLong(chat.id);
      }
      context.tdlib().context().changePreferredAccountId(context.tdlib().id(), TdlibManager.SWITCH_REASON_CHAT_OPEN, success -> {
        if (success) {
          controller.setResetOnFocus();
        }
      });
    };
    controller.postOnAnimationReady(actor);

    final MessagesController.Arguments arguments;
    if (params != null && !StringUtils.isEmpty(params.searchQuery) && params.foundMessage != null) {
      arguments = new MessagesController.Arguments(chatList, chat, messageThread, highlightMessageId, highlightMode, filter, params.foundMessage, params.searchQuery);
    } else if (highlightMessageId != null) {
      arguments = new MessagesController.Arguments(chatList, chat, messageThread, highlightMessageId, highlightMode, filter);
    } else {
      arguments = new MessagesController.Arguments(tdlib, chatList, chat, messageThread, filter);
    }
    controller.setArguments(arguments
      .setScheduled(onlyScheduled)
      .referrer(referrer)
      .voiceChatInvitation(voiceChatInvitation)
      .fillDraft(forceDraft)
    );

    View view = controller.getValue();
    if (controller.context().isNavigationBusy()) {
      if (params != null) {
        params.onDone();
      }
      return;
    }
    if (view.getParent() != null) {
      ((ViewGroup) view.getParent()).removeView(controller.getValue());
    }

    if (navigation.isEmpty()) {
      navigation.initController(controller);
      MainController c = new MainController(context.context(), context.tdlib());
      c.getValue();
      navigation.getStack().insert(c, 0);
    } else if (navigation.getStackSize() > 1 && (options & CHAT_OPTION_KEEP_STACK) == 0) {
      navigation.setControllerAnimated(controller, true, true);
    } else {
      navigation.navigateTo(controller);
    }
    if (params != null) {
      params.onDone();
    }
  }

  public void openChatProfile (final TdlibDelegate context, final @NonNull TdApi.Chat chat, final @Nullable ThreadInfo threadInfo, final @Nullable UrlOpenParameters openParameters) {
    if (TdlibManager.inBackgroundThread()) {
      tdlib.runOnUiThread(() -> openChatProfile(context, chat, threadInfo, openParameters));
      return;
    }
    int accessState = tdlib.chatAccessState(chat);
    if (accessState < Tdlib.CHAT_ACCESS_OK) {
      showAccessError(context, openParameters, accessState, tdlib.isChannel(chat.id));
      return;
    }
    if (context.context().isNavigationBusy()) {
      return;
    }
    NavigationController navigation = context.context().navigation();
    ProfileController profileController = new ProfileController(context.context(), context.tdlib());
    try {
      profileController.setArguments(new ProfileController.Args(chat, threadInfo, false));
    } catch (Throwable t) {
      Log.e("Unable to open profile", t);
      return;
    }

    if (navigation.isEmpty()) {
      navigation.initController(profileController);
      MainController c = new MainController(context.context(), context.tdlib());
      c.getValue();
      navigation.getStack().insert(c, 0);
    } else {
      ViewController<?> c = navigation.getCurrentStackItem();
      if (c instanceof MessagesController && !tdlib.isSelfChat(chat) && ((MessagesController) c).getHeaderChatId() == chat.id && !((MessagesController) c).inPreviewMode()) {
        profileController.setShareCustomHeaderView(true);
      } else if (c instanceof ProfileController && ((ProfileController) c).isSameProfile(profileController)) {
        profileController.getValue();
        profileController.destroy();
        return;
      }
      navigation.navigateTo(profileController);
    }
  }

  private void openChatProfile (final TdlibDelegate context, final long chatId, @Nullable ThreadInfo messageThread, TdApi.Function<?> createRequest, final @Nullable UrlOpenParameters openParameters) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat != null) {
      openChatProfile(context, chat, messageThread, openParameters);
      return;
    }
    // TODO progress
    tdlib.client().send(createRequest, object -> {
      switch (object.getConstructor()) {
        case TdApi.Chat.CONSTRUCTOR:
          openChatProfile(context, tdlib.objectToChat(object), messageThread, openParameters);
          break;
        case TdApi.User.CONSTRUCTOR: {
          final long userId = ((TdApi.User) object).id;
          openChatProfile(context, ChatId.fromUserId(userId), messageThread, new TdApi.CreatePrivateChat(userId, false), openParameters);
          break;
        }
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    });
  }

  public void openChat (final TdlibDelegate context, final long chatId, final @Nullable ChatOpenParameters params) {
    openChat(context, chatId, new TdApi.GetChat(chatId), params);
  }

  public void openChatProfile (final TdlibDelegate context, final long chatId, final @Nullable ThreadInfo messageThread, final @Nullable UrlOpenParameters openParameters) {
    openChatProfile(context, chatId, messageThread, new TdApi.GetChat(chatId), openParameters);
  }

  public void openPublicChat (final TdlibDelegate context, final @NonNull String username, final @Nullable UrlOpenParameters openParameters) {
    openChat(context, 0, new TdApi.SearchPublicChat(username), new ChatOpenParameters().urlOpenParameters(openParameters).keepStack().openProfileInCaseOfPrivateChat());
  }

  public void openVideoChatOrLiveStream (final TdlibDelegate context, final @NonNull TdApi.InternalLinkTypeVideoChat videoChatOrLiveStreamInvitation, final @Nullable UrlOpenParameters openParameters) {
    openChat(context, 0, new TdApi.SearchPublicChat(videoChatOrLiveStreamInvitation.chatUsername), new ChatOpenParameters().urlOpenParameters(openParameters).videoChatOrLiveStreamInvitation(videoChatOrLiveStreamInvitation).keepStack().openProfileInCaseOfPrivateChat());
  }

  private static final int BOT_MODE_START = 0;
  private static final int BOT_MODE_START_IN_GROUP = 1;
  private static final int BOT_MODE_START_GAME = 2;

  private void startBot (final TdlibDelegate context, final String botUsername, final String startArgument, final int botMode, final @Nullable UrlOpenParameters openParameters) {
    // TODO progress
    tdlib.client().send(new TdApi.SearchPublicChat(botUsername), object -> {
      switch (object.getConstructor()) {
        case TdApi.Chat.CONSTRUCTOR:
          TdApi.Chat chat = tdlib.objectToChat(object);
          if (!tdlib.isBotChat(chat)) {
            showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, Lang.getStringBold(R.string.BotNotFound, botUsername), openParameters);
            return;
          }
          TdApi.User user = tdlib.chatUser(chat);
          if (user == null || user.type.getConstructor() != TdApi.UserTypeBot.CONSTRUCTOR) {
            showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, Lang.getStringBold(R.string.BotNotFound, botUsername), openParameters);
            return;
          }
          /* commented out because it's possible that bot is a member of the selected group
          if (addToGroup && !((TdApi.UserTypeBot) user.type).canJoinGroups) {
            UI.showToast(UI.getString(R.string.BotNotFound, botUsername), Toast.LENGTH_SHORT);
            return;
          }*/
          switch (botMode) {
            case BOT_MODE_START: {
              openChat(context, chat, new ChatOpenParameters().urlOpenParameters(openParameters).shareItem(new TGBotStart(user.id, startArgument, false, openParameters != null && openParameters.ignoreExplicitUserInteraction)).keepStack());
              break;
            }
            case BOT_MODE_START_IN_GROUP:
            case BOT_MODE_START_GAME: {
              final boolean isGame = botMode == BOT_MODE_START_GAME;
              addToGroup(context, new TGBotStart(user.id, startArgument, isGame, openParameters != null && openParameters.ignoreExplicitUserInteraction), isGame);
              break;
            }
          }
          break;
        case TdApi.Error.CONSTRUCTOR:
          showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, Lang.getStringBold(R.string.BotNotFound, botUsername), openParameters);
          break;
      }
    });
  }

  public void openPublicMessage (final TdlibDelegate context, final @NonNull String username, final MessageId messageId, @Nullable UrlOpenParameters urlOpenParameters) {
    openChat(context, 0, new TdApi.SearchPublicChat(username), new ChatOpenParameters().keepStack().highlightMessage(messageId).ensureHighlightAvailable().urlOpenParameters(urlOpenParameters));
  }

  public void openSupergroupMessage (final TdlibDelegate context, final long supergroupId, final MessageId messageId, @Nullable UrlOpenParameters urlOpenParameters) {
    if (messageId != null) {
      openChat(context, 0, new TdApi.CreateSupergroupChat(supergroupId, false), new ChatOpenParameters().keepStack().highlightMessage(messageId).ensureHighlightAvailable().urlOpenParameters(urlOpenParameters));
    } else {
      openChat(context, 0, new TdApi.CreateSupergroupChat(supergroupId, false), new ChatOpenParameters().keepStack().urlOpenParameters(urlOpenParameters));
    }
  }

  public void openMessage (final TdlibDelegate context, final TdApi.Message message, final @Nullable UrlOpenParameters openParameters) {
    openMessage(context, message.chatId, new MessageId(message.chatId, message.id), openParameters);
  }

  public void openMessage (final TdlibDelegate context, final long chatId, final MessageId messageId, final @Nullable UrlOpenParameters openParameters) {
    openMessage(context, chatId, messageId, /* messageThread */ null, openParameters);}

  public void openMessage (final TdlibDelegate context, final long chatId, final MessageId messageId, final @Nullable ThreadInfo messageThread, final @Nullable UrlOpenParameters openParameters) {
    openChat(context, chatId, new ChatOpenParameters().keepStack().highlightMessage(messageId).ensureHighlightAvailable().messageThread(messageThread).urlOpenParameters(openParameters));
  }

  public void openMessage (final TdlibDelegate context, final TdApi.MessageLinkInfo messageLink, final @Nullable UrlOpenParameters openParameters) {
    if (messageLink.message != null) {
      // TODO support for album, media timestamp, etc
      MessageId messageId = new MessageId(messageLink.message.chatId, messageLink.message.id);
      if (messageLink.messageThreadId != 0) {
        // FIXME TDLib/Server: need GetMessageThread alternative that accepts (chatId, messageThreadId)
        context.tdlib().send(new TdApi.GetMessageThread(messageId.getChatId(), messageId.getMessageId()), (messageThreadInfo, error) -> {
          if (error != null) {
            openMessage(context, messageLink.chatId, messageId, openParameters);
          } else {
            ThreadInfo messageThread = ThreadInfo.openedFromMessage(context.tdlib(), messageThreadInfo, openParameters != null ? openParameters.messageId : null);
            if (Config.SHOW_CHANNEL_POST_REPLY_INFO_IN_COMMENTS) {
              TdApi.Message message = messageThread.getOldestMessage();
              if (message != null && message.replyTo == null && message.forwardInfo != null && tdlib.isChannelAutoForward(message)) {
                tdlib.send(new TdApi.GetRepliedMessage(message.forwardInfo.source.chatId, message.forwardInfo.source.messageId), (repliedMessage, repliedMessageError) -> {
                  if (repliedMessage != null) {
                    message.replyTo = new TdApi.MessageReplyToMessage(repliedMessage.chatId, repliedMessage.id, null, null, repliedMessage.date, repliedMessage.content);
                  }
                  openMessage(context, messageThread.getChatId(), messageId, messageThread, openParameters);
                });
                return;
              }
            }
            openMessage(context, messageThread.getChatId(), messageId, messageThread, openParameters);
          }
        });
      } else {
        openMessage(context, messageLink.chatId, messageId, openParameters);
      }
    } else {
      if (tdlib.chat(messageLink.chatId) != null) {
        UI.showToast(tdlib.isChannel(messageLink.chatId) ? R.string.PostNotFound : R.string.MessageNotFound, Toast.LENGTH_SHORT);
      }
      openChat(context, messageLink.chatId, new ChatOpenParameters().keepStack().urlOpenParameters(openParameters));
    }
  }

  public void openScheduledMessage (final TdlibDelegate context, final TdApi.Message message) {
    openScheduledMessage(context, message.chatId, new MessageId(message.chatId, message.id));
  }

  public void openScheduledMessage (final TdlibDelegate context, final long chatId, final MessageId messageId) {
    openChat(context, chatId, new ChatOpenParameters().keepStack().scheduledOnly().highlightMessage(messageId).ensureHighlightAvailable());
  }

  public void openPrivateChat (final TdlibDelegate context, final long userId, final @Nullable ChatOpenParameters openParameters) {
    openChat(context, ChatId.fromUserId(userId), new TdApi.CreatePrivateChat(userId, false), openParameters);
  }

  public void openPrivateProfile (final TdlibDelegate context, final long userId, final UrlOpenParameters openParameters) {
    openChatProfile(context, ChatId.fromUserId(userId), null, new TdApi.CreatePrivateChat(userId, false), openParameters);
  }

  public void openSenderProfile (final TdlibDelegate context, final TdApi.MessageSender senderId, final UrlOpenParameters openParameters) {
    switch (senderId.getConstructor()) {
      case TdApi.MessageSenderUser.CONSTRUCTOR:
        openPrivateProfile(context, ((TdApi.MessageSenderUser) senderId).userId, openParameters);
        break;
      case TdApi.MessageSenderChat.CONSTRUCTOR:
        openChatProfile(context, ((TdApi.MessageSenderChat) senderId).chatId, null, openParameters);
        break;
      default:
        Td.assertMessageSender_439d4c9c();
        throw Td.unsupported(senderId);
    }
  }

  public void startSecretChat (final TdlibDelegate context, final long userId, final boolean allowExisting, final @Nullable ChatOpenParameters params) {
    // TODO open existing active secret chat if allowExisting == true
    // TODO progress
    tdlib.client().send(new TdApi.CreateNewSecretChat(userId), object -> {
      switch (object.getConstructor()) {
        case TdApi.Chat.CONSTRUCTOR:
          openChat(context, tdlib.objectToChat(object), params);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    });
  }

  public void openSupergroupChat (final TdlibDelegate context, final long supergroupId, final @Nullable ChatOpenParameters params) {
    openChat(context, ChatId.fromSupergroupId(supergroupId), new TdApi.CreateSupergroupChat(supergroupId, false), params);
  }

  public void openLinkedChat (final TdlibDelegate context, final long supergroupId, final @Nullable ChatOpenParameters params) {
    openChat(context, 0, new TdApi.GetSupergroupFullInfo(supergroupId), params);
  }

  public void openSupergroupProfile (final TdlibDelegate context, final long supergroupId, final @Nullable UrlOpenParameters openParameters) {
    openChatProfile(context, ChatId.fromSupergroupId(supergroupId), null, new TdApi.CreateSupergroupChat(supergroupId, false), openParameters);
  }

  public void openBasicGroupChat (final TdlibDelegate context, final long basicGroupId, final @Nullable ChatOpenParameters params) {
    openChat(context, ChatId.fromSupergroupId(basicGroupId), new TdApi.CreateBasicGroupChat(basicGroupId, false), params);
  }

  public void openBasicGroupProfile (final TdlibDelegate context, final long supergroupId, final @Nullable UrlOpenParameters openParameters) {
    openChatProfile(context, ChatId.fromSupergroupId(supergroupId), null, new TdApi.CreateSupergroupChat(supergroupId, false), openParameters);
  }

  public void upgradeBasicGroupChat (final TdlibDelegate context, final long chatId) {
    openChat(context, 0, new TdApi.UpgradeBasicGroupChatToSupergroupChat(chatId), null);
  }

  // Open link

  private void openJoinDialog (final TdlibDelegate context, final String inviteLink, TdApi.ChatInviteLinkInfo inviteLinkInfo, final @Nullable UrlOpenParameters openParameters) {
    if (TdlibManager.inBackgroundThread()) {
      tdlib.runOnUiThread(() -> openJoinDialog(context, inviteLink, inviteLinkInfo, openParameters));
      return;
    }
    ViewController<?> c = context.context().navigation().getCurrentStackItem();
    if (c != null) {
      ModernActionedLayout.showJoinDialog(c, inviteLinkInfo, () -> joinChatByInviteLink(context, inviteLink, inviteLinkInfo, openParameters));
    }
    return;
  }

  private void checkInviteLink (final TdlibDelegate context, final String inviteLink, final @Nullable UrlOpenParameters openParameters) {
    // TODO progress
    tdlib.client().send(new TdApi.CheckChatInviteLink(inviteLink), object -> {
      switch (object.getConstructor()) {
        case TdApi.ChatInviteLinkInfo.CONSTRUCTOR: {
          final TdApi.ChatInviteLinkInfo inviteLinkInfo = (TdApi.ChatInviteLinkInfo) object;
          tdlib.traceInviteLink(inviteLinkInfo);
          int accessState = tdlib.chatAccessState(inviteLinkInfo.chatId);
          if (inviteLinkInfo.chatId == 0 || accessState == Tdlib.CHAT_ACCESS_PRIVATE) {
            openJoinDialog(context, inviteLink, inviteLinkInfo, openParameters);
          } else {
            openChat(context, inviteLinkInfo.chatId, new ChatOpenParameters().urlOpenParameters(openParameters).inviteLink(inviteLink, inviteLinkInfo).keepStack().removeDuplicates());
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          showLinkTooltip(tdlib, R.drawable.baseline_error_24, TD.toErrorString(object), openParameters);
          break;
        }
      }
    });
  }

  private static void checkChatFolderInviteLink(TdlibDelegate context, String inviteLinkUrl, TdApi.InternalLinkTypeChatFolderInvite invite, @Nullable UrlOpenParameters openParameters) {
    context.tdlib().send(new TdApi.CheckChatFolderInviteLink(invite.inviteLink), (result, error) -> {
      if (error != null) {
        showLinkTooltip(context.tdlib(), R.drawable.baseline_error_24, TD.toErrorString(error), openParameters);
      } else {
        showChatFolderInviteLinkInfo(context, inviteLinkUrl, result);
      }
    });
  }

  private static void showChatFolderInviteLinkInfo (TdlibDelegate context, String inviteLinkUrl, TdApi.ChatFolderInviteLinkInfo inviteLinkInfo) {
    if (TdlibManager.inBackgroundThread()) {
      context.tdlib().ui().post(() -> {
        showChatFolderInviteLinkInfo(context, inviteLinkUrl, inviteLinkInfo);
      });
      return;
    }
    ChatFolderInviteLinkController controller = new ChatFolderInviteLinkController(context.context(), context.tdlib());
    controller.setArguments(new ChatFolderInviteLinkController.Arguments(inviteLinkUrl, inviteLinkInfo));
    controller.show();
  }

  private boolean installLanguage (final TdlibDelegate context, final String languagePackId, @Nullable UrlOpenParameters openParameters) {
    if (TD.isLocalLanguagePackId(languagePackId)) {
      Log.e("Attempt to install custom local languagePackId:%s", languagePackId);
      return true;
    }
    // TODO progress
    tdlib.send(new TdApi.GetLanguagePackInfo(languagePackId), (info, error) -> {
      if (error != null) {
        showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, TD.toErrorString(error), openParameters);
      } else {
        tdlib.ui().post(() -> {
          if (context.context().getActivityState() != UI.State.DESTROYED) {
            showLanguageInstallPrompt(context, info);
          }
        });
      }
    });
    return true;
  }

  private void joinChatByInviteLink (final TdlibDelegate context, final String inviteLink, TdApi.ChatInviteLinkInfo inviteLinkInfo, final @Nullable UrlOpenParameters urlOpenParameters) {
    openChat(context, 0, new TdApi.JoinChatByInviteLink(inviteLink), new ChatOpenParameters().urlOpenParameters(urlOpenParameters).inviteLink(inviteLink, inviteLinkInfo).keepStack().removeDuplicates());
  }

  public static final int INSTANT_VIEW_UNSPECIFIED = -1;
  public static final int INSTANT_VIEW_DISABLED = 0;
  public static final int INSTANT_VIEW_ENABLED = 1;

  public static final int EMBED_VIEW_UNSPECIFIED = -1;
  public static final int EMBED_VIEW_ENABLED = 0;
  public static final int EMBED_VIEW_DISABLED = 1;

  public static class UrlOpenParameters implements TGMessage.MessageIdChangeListener {
    public int instantViewMode = INSTANT_VIEW_UNSPECIFIED;
    public int embedViewMode = EMBED_VIEW_UNSPECIFIED;
    public TdApi.LinkPreview sourceLinkPreview;

    public MessageId messageId;
    public String refererUrl, instantViewFallbackUrl, originalUrl;
    public TooltipOverlayView.TooltipBuilder tooltip;
    public boolean requireOpenPrompt, ignoreExplicitUserInteraction;
    public String displayUrl;

    private ViewController<?> parentController;
    private TGMessage sourceMessage;

    public UrlOpenParameters () { }

    public UrlOpenParameters (@Nullable UrlOpenParameters options) {
      if (options != null) {
        this.instantViewMode = options.instantViewMode;
        this.embedViewMode = options.embedViewMode;
        this.messageId = options.messageId;
        this.refererUrl = options.refererUrl;
        this.instantViewFallbackUrl = options.instantViewFallbackUrl;
        this.tooltip = options.tooltip;
        this.requireOpenPrompt = options.requireOpenPrompt;
        this.ignoreExplicitUserInteraction = options.ignoreExplicitUserInteraction;
        this.displayUrl = options.displayUrl;
        this.parentController = options.parentController;
        this.originalUrl = options.originalUrl;
        this.sourceLinkPreview = options.sourceLinkPreview;
        if (options.sourceMessage != null) {
          sourceMessage(options.sourceMessage);
        }
      }
    }

    public UrlOpenParameters tooltip (@Nullable TooltipOverlayView.TooltipBuilder b) {
      this.tooltip = b;
      if (b != null && parentController != null && !b.hasController()) {
        b.controller(parentController);
      }
      return this;
    }

    public UrlOpenParameters controller (@Nullable ViewController<?> controller) {
      this.parentController = controller;
      return this;
    }

    public UrlOpenParameters sourceChat (long chatId) {
      if (this.sourceMessage != null) {
        if (this.sourceMessage.getChatId() != chatId)
          throw new IllegalStateException();
        return this;
      }
      return sourceMessage(new MessageId(chatId, 0l));
    }

    public UrlOpenParameters displayUrl (String displayUrl) {
      this.displayUrl = displayUrl;
      return this;
    }

    public UrlOpenParameters sourceMessage (MessageId messageId) {
      this.messageId = messageId;
      return this;
    }

    public UrlOpenParameters sourceMessage (TGMessage message) {
      if (message != null) {
        controller(message.controller());
        if (message.isSponsoredMessage()) {
          return this;
        } else if (message.isSending()) {
          this.sourceMessage = message;
          message.getMessageIdChangeListeners().add(this);
        } else {
          this.sourceMessage = null;
        }
        return sourceMessage(new MessageId(message.getChatId(), message.getId(), message.getOtherMessageIds(message.getId())));
      } else {
        this.sourceMessage = null;
        return sourceMessage((MessageId) null);
      }
    }

    @Override
    public void onMessageIdChanged (TGMessage msg, long oldMessageId, long newMessageId, boolean success) {
      if (messageId != null && messageId.compareTo(msg.getChatId(), oldMessageId)) {
        messageId = messageId.replaceMessageId(oldMessageId, newMessageId);
        msg.getMessageIdChangeListeners().remove(this);
        this.sourceMessage = null;
      }
    }

    public UrlOpenParameters requireOpenPrompt () {
      return requireOpenPrompt(true);
    }

    public UrlOpenParameters requireOpenPrompt (boolean requirePrompt) {
      this.requireOpenPrompt = requirePrompt;
      return this;
    }

    public UrlOpenParameters ignoreExplicitUserInteraction (boolean ignoreExplicitUserInteraction) {
      this.ignoreExplicitUserInteraction = ignoreExplicitUserInteraction;
      return this;
    }

    public UrlOpenParameters disableOpenPrompt () {
      this.requireOpenPrompt = false;
      return this;
    }

    public UrlOpenParameters instantViewMode (int instantViewMode) {
      this.instantViewMode = instantViewMode;
      return this;
    }

    public UrlOpenParameters forceInstantView () {
      return instantViewMode(TdlibUi.INSTANT_VIEW_ENABLED);
    }

    public UrlOpenParameters disableInstantView () {
      return instantViewMode(TdlibUi.INSTANT_VIEW_DISABLED);
    }

    public UrlOpenParameters embedViewMode (int embedViewMode) {
      this.embedViewMode = embedViewMode;
      return this;
    }

    public UrlOpenParameters forceEmbedView () {
      return embedViewMode(TdlibUi.EMBED_VIEW_ENABLED);
    }

    public UrlOpenParameters disableEmbedView () {
      return embedViewMode(TdlibUi.EMBED_VIEW_DISABLED);
    }

    public UrlOpenParameters sourceLinkPreview (TdApi.LinkPreview linkPreview) {
      this.sourceLinkPreview = linkPreview;
      return this;
    }

    public UrlOpenParameters referer (String refererUrl) {
      this.refererUrl = refererUrl;
      return this;
    }

    public UrlOpenParameters originalUrl (String originalUrl) {
      this.originalUrl = originalUrl;
      return this;
    }

    public UrlOpenParameters instantViewFallbackUrl (String fallbackUrl) {
      this.instantViewFallbackUrl = fallbackUrl;
      return this;
    }
  }

  public void openUrlOptions (final ViewController<?> context, final String url, @Nullable UrlOpenParameters options) {
    context.showOptions(url, new int[] {R.id.btn_open, R.id.btn_copyLink}, new String[] {Lang.getString(R.string.Open), Lang.getString(R.string.CopyLink)}, null, new int[] {R.drawable.baseline_open_in_browser_24, R.drawable.baseline_content_copy_24}, (v, optionId) -> {
      if (optionId == R.id.btn_open) {
        openUrl(context, url, options);
      } else if (optionId == R.id.btn_copyLink) {
        UI.copyText(url, R.string.CopiedLink);
      }
      return true;
    });
  }

  private void openExternalUrl (final TdlibDelegate context, final String originalUrl, @Nullable UrlOpenParameters options, @Nullable RunnableBool after) {
    if (options != null && options.messageId != null && ChatId.isSecret(options.messageId.getChatId())) {
      openUrlImpl(context, originalUrl, options, after);
      return;
    }
    tdlib.send(new TdApi.GetExternalLinkInfo(originalUrl), (loginUrlInfo, error) -> {
      if (error != null) {
        openUrlImpl(context, originalUrl, options, after);
        return;
      }
      switch (loginUrlInfo.getConstructor()) {
        case TdApi.LoginUrlInfoOpen.CONSTRUCTOR: {
          TdApi.LoginUrlInfoOpen open = (TdApi.LoginUrlInfoOpen) loginUrlInfo;
          if (options != null) {
            if (open.skipConfirmation) {
              options.disableOpenPrompt();
            }
            options.displayUrl(originalUrl);
          }
          openUrlImpl(context, open.url, options, after);
          break;
        }
        case TdApi.LoginUrlInfoRequestConfirmation.CONSTRUCTOR: {
          TdApi.LoginUrlInfoRequestConfirmation confirm = (TdApi.LoginUrlInfoRequestConfirmation) loginUrlInfo;
          List<ListItem> items = new ArrayList<>();
          items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_MULTILINE,
            R.id.btn_signIn, 0,
            Lang.getString(R.string.LogInAsOn,
              (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 1 ?
                new CustomTypefaceSpan(null, ColorId.textLink) :
                Lang.newBoldSpan(needFakeBold),
              context.tdlib().accountName(),
              confirm.domain),
            true
          ));
          if (confirm.requestWriteAccess) {
            items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_MULTILINE,
              R.id.btn_allowWriteAccess,
              0,
              Lang.getString(R.string.AllowWriteAccess, Lang.boldCreator(), context.tdlib().cache().userName(confirm.botUserId)),
              true
            ));
          }

          ViewController<?> controller = context instanceof ViewController<?> ? (ViewController<?>) context : context.context().navigation().getCurrentStackItem();
          if (controller != null && !controller.isDestroyed()) {
            controller.showSettings(
              new SettingsWrapBuilder(R.id.btn_open)
                .addHeaderItem(Lang.getString(R.string.OpenLinkConfirm, (target, argStart, argEnd, spanIndex, needFakeBold) -> new CustomTypefaceSpan(null, ColorId.textLink), confirm.url))
                .setRawItems(items)
                .setIntDelegate((id, result) -> {
                  boolean needSignIn = items.get(0).isSelected();
                  boolean needWriteAccess = items.size() > 1 && items.get(1).isSelected();
                  if (needSignIn) {
                    context.tdlib().send(
                      new TdApi.GetExternalLink(originalUrl, needWriteAccess), (httpUrl, error1) -> {
                        String destinationUrl = error1 != null ? originalUrl : httpUrl.url;
                        openUrlImpl(context, destinationUrl, options != null ? options.disableOpenPrompt() : null, after);
                      });
                  } else {
                    openUrlImpl(context, originalUrl, options != null ? options.disableOpenPrompt() : null, after);
                  }
                })
                .setSettingProcessor((item, itemView, isUpdate) -> {
                  switch (item.getViewType()) {
                    case ListItem.TYPE_CHECKBOX_OPTION:
                    case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
                    case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
                      ((CheckBoxView) itemView.getChildAt(0)).setChecked(item.isSelected(), isUpdate);
                      break;
                  }
                })
                .setOnSettingItemClick(confirm.requestWriteAccess ? (itemView, settingsId, item, doneButton, settingsAdapter, window) -> {
                  final int itemId = item.getId();
                  if (itemId == R.id.btn_signIn) {
                    boolean needSignIn = settingsAdapter.getCheckIntResults().get(R.id.btn_signIn) == R.id.btn_signIn;
                    if (!needSignIn) {
                      items.get(1).setSelected(false);
                      settingsAdapter.updateValuedSettingById(R.id.btn_allowWriteAccess);
                    }
                  } else if (itemId == R.id.btn_allowWriteAccess) {
                    boolean needWriteAccess = settingsAdapter.getCheckIntResults().get(R.id.btn_allowWriteAccess) == R.id.btn_allowWriteAccess;
                    if (needWriteAccess) {
                      items.get(0).setSelected(true);
                      settingsAdapter.updateValuedSettingById(R.id.btn_signIn);
                    }
                  }
                } : null)
                .setSaveStr(R.string.Open)
                .setRawItems(items)
            );
          } else {
            if (after != null) {
              after.runWithBool(false);
            }
          }
          break;
        }
        default: {
          Td.assertLoginUrlInfo_7af29c11();
          throw Td.unsupported(loginUrlInfo);
        }
      }
    });
  }

  private void openUrlImpl (final TdlibDelegate context, final String url, @Nullable UrlOpenParameters options, @Nullable RunnableBool after) {
    if (!UI.inUiThread()) {
      tdlib.ui().post(() -> openUrlImpl(context, url, options, after));
      return;
    }

    Uri uri = Strings.wrapHttps(url);

    if (options != null && options.requireOpenPrompt && (uri == null || !tdlib.isTrustedHost(url, true))) {
      ViewController<?> c = context instanceof ViewController<?> ? (ViewController<?>) context : context.context().navigation().getCurrentStackItem();
      if (c != null && !c.isDestroyed()) {
        AlertDialog.Builder b = new AlertDialog.Builder(context.context(), Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.AppName));
        b.setMessage(Lang.getString(R.string.OpenThisLink, !StringUtils.isEmpty(options.displayUrl) ? options.displayUrl : url));
        b.setPositiveButton(Lang.getString(R.string.Open), (dialog, which) ->
          tdlib.ui()
            .openExternalUrl(context, url, options.disableOpenPrompt(), after)
        );
        b.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        c.showAlert(b);
      }
      return;
    }

    if (uri == null) {
      boolean result = Intents.openLink(url);
      if (after != null) {
        after.runWithBool(result);
      }
      return;
    }

    final boolean isFromSecretChat = options != null && options.sourceMessage != null && ChatId.isSecret(options.sourceMessage.getChatId());

    final int instantViewMode;
    final int embedViewMode;
    if (isFromSecretChat && !Settings.instance().needSecretLinkPreviews()) {
      instantViewMode = INSTANT_VIEW_DISABLED;
      embedViewMode = EMBED_VIEW_DISABLED;
    } else {
      if (options == null || options.instantViewMode == INSTANT_VIEW_UNSPECIFIED) {
        boolean ok = false;
        try {
          String host = uri.getHost();
          String path = uri.getPath();
          if (!StringUtils.isEmpty(host) && path != null && path.length() > 1) {
            switch (Settings.instance().getInstantViewMode()) {
              case Settings.INSTANT_VIEW_MODE_INTERNAL:
                ok = tdlib.isKnownHost(host, true);
                break;
              case Settings.INSTANT_VIEW_MODE_ALL:
                ok = true;
                break;
            }
          }
        } catch (Throwable t) {
          Log.i(t);
        }
        instantViewMode = ok ? INSTANT_VIEW_ENABLED : INSTANT_VIEW_DISABLED;
      } else {
        instantViewMode = options.instantViewMode;
      }
      if (options == null || options.embedViewMode == EMBED_VIEW_UNSPECIFIED) {
        embedViewMode = Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_EMBEDS) ? EMBED_VIEW_DISABLED : EMBED_VIEW_ENABLED;
      } else {
        embedViewMode = options.embedViewMode;
      }
    }

    final String externalUrl = options == null || StringUtils.isEmpty(options.instantViewFallbackUrl) ? url : options.instantViewFallbackUrl;
    final Uri uriFinal = uri;
    if (instantViewMode == INSTANT_VIEW_DISABLED && embedViewMode == EMBED_VIEW_DISABLED) {
      boolean result = Intents.openLink(url);
      if (after != null) {
        after.runWithBool(result);
      }
      return;
    }

    if (embedViewMode == EMBED_VIEW_ENABLED && context instanceof ViewController<?>) {
      TdApi.LinkPreview linkPreview = options != null ? options.sourceLinkPreview : null;
      if (
        (linkPreview != null && PreviewLayout.show((ViewController<?>) context, linkPreview, isFromSecretChat)) ||
        (linkPreview == null && PreviewLayout.show((ViewController<?>) context, url, isFromSecretChat))
      ) {
        if (after != null) {
          after.runWithBool(true);
        }
        return;
      }
    }

    final AtomicBoolean signal = new AtomicBoolean();
    final AtomicReference<TdApi.LinkPreview> foundWebPage = new AtomicReference<>();
    CancellableRunnable[] runnable = new CancellableRunnable[1];

    tdlib.send(new TdApi.GetLinkPreview(new TdApi.FormattedText(url, null), null), (linkPreview, error) -> {
      if (error != null) {
        post(runnable[0]);
        return;
      }
      foundWebPage.set(linkPreview);
      if (instantViewMode == INSTANT_VIEW_DISABLED || !TD.hasInstantView(linkPreview.instantViewVersion)) {
        post(runnable[0]);
        return;
      }
      tdlib.send(new TdApi.GetWebPageInstantView(url, false), (instantView, error1) -> {
        if (error1 != null) {
          post(runnable[0]);
          return;
        }
        if (!TD.hasInstantView(instantView.version)) {
          post(runnable[0]);
          return;
        }
        post(() -> {
          if (!signal.getAndSet(true)) {
            runnable[0].cancel();

            InstantViewController controller = new InstantViewController(context.context(), context.tdlib());
            try {
              controller.setArguments(new InstantViewController.Args(linkPreview, instantView, Uri.parse(url).getEncodedFragment()));
              controller.show();
              if (after != null) {
                after.runWithBool(true);
              }
            } catch (Throwable t) {
              Log.e("Unable to open instantView, url:%s", t, url);
              UI.showToast(R.string.InstantViewUnsupported, Toast.LENGTH_SHORT);
              UI.openUrl(externalUrl);
            }
          }
        });
      });
    });
    runnable[0] = new CancellableRunnable() {
      @Override
      public void act () {
        if (!signal.getAndSet(true)) {
          if (options != null && !StringUtils.isEmpty(options.instantViewFallbackUrl) && !options.instantViewFallbackUrl.equals(url) && !options.instantViewFallbackUrl.equals(options.originalUrl)) {
            openUrl(context, options.instantViewFallbackUrl, new UrlOpenParameters(options).instantViewMode(INSTANT_VIEW_UNSPECIFIED), after);
            return;
          }
          if (tdlib.isKnownHost(uriFinal.getHost(), false)) {
            List<String> segments = uriFinal.getPathSegments();
            if (segments != null && segments.size() == 1 && "iv".equals(segments.get(0))) {
              String originalUrl = uriFinal.getQueryParameter("url");
              if (Strings.isValidLink(originalUrl)) {
                openUrl(context, originalUrl, new UrlOpenParameters(options).disableInstantView(), after);
                return;
              }
            }
          }
          if (embedViewMode == EMBED_VIEW_ENABLED) {
            TdApi.LinkPreview linkPreview = foundWebPage.get();
            if (context instanceof ViewController<?> && linkPreview != null && PreviewLayout.show((ViewController<?>) context, linkPreview, isFromSecretChat)) {
              if (after != null) {
                after.runWithBool(true);
              }
              return;
            }
          }
          if (!externalUrl.equals(url) && !(options != null && externalUrl.equals(options.originalUrl))) {
            openUrl(context, externalUrl, new UrlOpenParameters(options).instantViewMode(INSTANT_VIEW_UNSPECIFIED), after);
          } else {
            boolean result = Intents.openLink(externalUrl);
            if (after != null) {
              after.runWithBool(result);
            }
          }
        }
      }
    };
    runnable[0].removeOnCancel(UI.getAppHandler());
    UI.post(runnable[0], 2000);
  }

  public void openUrl (final TdlibDelegate context, final String url, @Nullable UrlOpenParameters options) {
    openUrl(context, url, options, null);
  }
  
  public void openUrl (final TdlibDelegate context, final String url, @Nullable UrlOpenParameters options, @Nullable RunnableBool after) {
    openTelegramUrl(context, url, options, processed -> {
      if (!processed) {
        openExternalUrl(context, url, options, after);
      } else {
        if (after != null) {
          after.runWithBool(true);
        }
      }
    });
  }

  private String parseTgScheme (TdlibDelegate context, String originalUrl, Uri uri) {
    String command = uri.getHost();
    if (StringUtils.isEmpty(command)) {
      String schemeSpecificPart = uri.getSchemeSpecificPart();
      int i = schemeSpecificPart.indexOf('?');
      command = i == -1 ? schemeSpecificPart : schemeSpecificPart.substring(0, i);
    }

    final String tMeUrl = tdlib.tMeUrl();

    switch (command) {
      case "join": {
        String inviteHash = uri.getQueryParameter("invite");
        if (StringUtils.isEmpty(inviteHash)) {
          return null;
        }
        return tMeUrl + "joinchat/" + inviteHash;
      }
      case "resolve": {
        String domain = uri.getQueryParameter("domain");
        if (StringUtils.isEmpty(domain)) {
          return null;
        }
        long postId = StringUtils.parseLong(uri.getQueryParameter("post"));
        if (postId != 0) {
          return tMeUrl + domain + "/" + postId;
        }
        String start = uri.getQueryParameter("start");
        if (!StringUtils.isEmpty(start)) {
          return tMeUrl + domain + "?start=" + start;
        }
        String startGroup = uri.getQueryParameter("startgroup");
        if (!StringUtils.isEmpty(startGroup)) {
          return tMeUrl + domain + "?startgroup=" + startGroup;
        }
        return tMeUrl + domain;
      }
      case "addstickers": {
        String set = uri.getQueryParameter("set");
        if (!StringUtils.isEmpty(set)) {
          return tMeUrl + "addstickers/" + set;
        }
        break;
      }
      case "bg": {
        // TODO

        /*https://t.me/bg/dedcd9 // solid
          tg://bg?color=dedcd9

          https://t.me/bg/Qe9IiLLfiVIBAAAAn_BDUKSYaCs // still
          tg://bg?slug=Qe9IiLLfiVIBAAAAn_BDUKSYaCs

          https://t.me/bg/fqv01SQemVIBAAAApND8LDRUhRU?intensity=47&bg_color=47e304 // pattern
          tg://bg?slug=fqv01SQemVIBAAAApND8LDRUhRU&bg_color=47e304&intensity=47

          https://t.me/bg/00c2ed-9200ed?rotation=45
          tg://bg?gradient=00c2ed-9200ed

          https://t.me/bg/fqv01SQemVIBAAAApND8LDRUhRU?intensity=99&bg_color=abcdef-000000&mode=motion&rotation=45
          tg://bg?slug=fqv01SQemVIBAAAApND8LDRUhRU&mode=motion&bg_color=abcdef-000000&intensity=99*/
        String slug = uri.getQueryParameter("slug");
        String bgColor = uri.getQueryParameter("bg_color");
        String intensity = uri.getQueryParameter("intensity");

        break;
      }
      case "privatepost": {
        long supergroupId = StringUtils.parseLong(uri.getQueryParameter("channel"));
        long messageId = StringUtils.parseLong(uri.getQueryParameter("msg_id"));
        if (supergroupId != 0) {
          if (messageId != 0) {
            return tMeUrl + "c/" + supergroupId + "/" + messageId;
          } else {
            return tMeUrl + "c/" + supergroupId;
          }
        }
        break;
      }
      case "login": {
        String code = uri.getQueryParameter("code");
        if (!StringUtils.isEmpty(code)) {
          return tMeUrl + "login/" + code;
        }
        break;
      }
      case "setlanguage": {
        String lang = uri.getQueryParameter("lang");
        if (!StringUtils.isEmpty(lang)) {
          return tMeUrl + "setlanguage/" + lang;
        }
        break;
      }
      case "confirmphone": {
        String phone = uri.getQueryParameter("phone");
        String hash = uri.getQueryParameter("hash");
        if (!StringUtils.isEmpty(phone) && !StringUtils.isEmpty(hash)) {
          return tMeUrl + "confirmphone?phone=" + phone + "&hash=" + hash;
        }
        break;
      }
      case "msg_url": {
        String url = uri.getQueryParameter("url");
        if (StringUtils.isEmpty(url)) {
          return null;
        }
        String text = uri.getQueryParameter("text");
        StringBuilder b = new StringBuilder(tMeUrl);
        b.append("share/url?url=").append(Uri.encode(url, "UTF-8"));
        if (!StringUtils.isEmpty(text)) {
          b.append("&text=").append(Uri.encode(text, "UTF-8"));
        }
        return b.toString();
      }
      case "socks": {
        String server = uri.getQueryParameter("server");
        if (StringUtils.isEmpty(server)) {
          return null;
        }
        int port = StringUtils.parseInt(uri.getQueryParameter("port"));
        String user = uri.getQueryParameter("user");
        String pass = uri.getQueryParameter("pass");
        StringBuilder b = new StringBuilder(tMeUrl);
        b.append("socks?server=").append(Uri.encode(server, "UTF-8"));
        b.append("&port=").append(port);
        if (!StringUtils.isEmpty(user)) {
          b.append("&user=").append(Uri.encode(user, "UTF-8"));
        }
        if (!StringUtils.isEmpty(pass)) {
          b.append("&pass=").append(Uri.encode(pass, "UTF-8"));
        }
        return b.toString();
      }
      case "proxy": {
        String server = uri.getQueryParameter("server");
        if (StringUtils.isEmpty(server)) {
          return null;
        }
        int port = StringUtils.parseInt(uri.getQueryParameter("port"));
        String secret = uri.getQueryParameter("secret");
        StringBuilder b = new StringBuilder(tMeUrl);
        b.append("proxy?server=").append(Uri.encode(server, "UTF-8"));
        b.append("&port=").append(port);
        if (!StringUtils.isEmpty(secret)) {
          b.append("&secret=").append(Uri.encode(secret, "UTF-8"));
        }
        return b.toString();
      }
    }
    if (context != null && originalUrl != null) {
      tdlib.client().send(new TdApi.GetDeepLinkInfo(originalUrl), result -> {
        switch (result.getConstructor()) {
          case TdApi.DeepLinkInfo.CONSTRUCTOR: {
            TdApi.DeepLinkInfo info = (TdApi.DeepLinkInfo) result;
            tdlib.ui().post(() -> {
              ViewController<?> c = context.context().navigation().getCurrentStackItem();
              if (c != null) {
                c.processDeepLinkInfo(info);
              }
            });
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showToast(R.string.DeepLinkUnsupported, Toast.LENGTH_SHORT);
            break;
          }
        }
      });
      return TME_LINK_PROCESSED;
    }
    return null;
  }

  public static final int TME_URL_NONE = 0;
  public static final int TME_URL_MESSAGE = 1;

  public int getTelegramLinkType (String url) {
    if (StringUtils.isEmpty(url)) {
      return TME_URL_NONE;
    }

    url = url.replace("tg://", "tg:");
    if (url.startsWith("tg:") && !url.startsWith("tg://")) {
      url = "tg://" + url.substring("tg:".length());
    }

    Uri uri = Uri.parse(url);
    String scheme = uri.getScheme();
    if ("tg".equals(scheme)) {
      url = parseTgScheme(null, null, uri);
      if (StringUtils.isEmpty(url)) {
        return TME_URL_NONE;
      }
      uri = Uri.parse(url);
    } else if (StringUtils.isEmpty(scheme)) {
      url = "https://" + url;
      uri = Uri.parse(url);
    }

    if (!tdlib.isKnownHost(url, false)) {
      return TME_URL_NONE;
    }

    List<String> segments = uri.getPathSegments();
    if (segments == null || segments.isEmpty()) {
      return TME_URL_NONE;
    }
    String command = segments.get(0);
    String pathArg = segments.size() > 1 ? segments.get(1) : null;

    long postId = StringUtils.parseLong(pathArg);

    if (!Strings.isValidLink(command) && postId != 0) {
      return TME_URL_MESSAGE;
    }

    return TME_URL_NONE;
  }

  private static final String TME_LINK_PROCESSED = "done";

  public boolean needViewInBrowser (String url) {
    if (StringUtils.isEmpty(url))
      return false;

    if (TdlibManager.instance().inRecoveryMode())
      return true;

    url = url.replace("tg://", "tg:");
    if (url.startsWith("tg:") && !url.startsWith("tg://")) {
      url = "tg://" + url.substring("tg:".length());
    }

    Uri uri = Uri.parse(url);

    String scheme = uri.getScheme();
    if ("tg".equals(scheme))
      return false;

    if (StringUtils.isEmpty(scheme))
      uri = Uri.parse("https://" + uri.toString());

    if (!tdlib.isKnownHost(url, false)) {
      return false;
    }

    List<String> segments = uri.getPathSegments();
    if (segments != null && !segments.isEmpty()) {
      String command = segments.get(0);
      String pathArg1 = segments.size() > 1 ? segments.get(1) : null;
      switch (command) {
        case "s":
          return !StringUtils.isEmpty(pathArg1);
      }
    }

    return false;
  }

  @NonNull
  private String preProcessTelegramUrl (@NonNull String url) {
    try {
      //noinspection UnsafeOptInUsageError
      Uri uri = StringUtils.wrapHttps(url);
      if (uri == null) {
        return url;
      }
      String host = uri.getHost();
      // convert username.t.me/path?query to t.me/username/path?query
      int firstIndex = host.indexOf('.');
      if (firstIndex == -1) {
        return url;
      }
      String subdomain = host.substring(0, firstIndex);
      host = host.substring(firstIndex + 1);
      if (!tdlib.isKnownHost(host, false)) {
        return url;
      }
      String path = uri.getPath();
      String newPath = "/" + subdomain + (!StringUtils.isEmpty(path) && !path.equals("/") ? path : "");
      Uri newUri = uri.buildUpon()
        .authority(host)
        .path(newPath)
        .build();
      return newUri.toString();
    } catch (Throwable t) {
      Log.i("Unable to pre process url: %s", t, url);
    }
    return url;
  }

  @Nullable
  private TdApi.InternalLinkType parseTelegramUrl (String url) {
    return null;
  }

  public void openTelegramUrl (final TdlibDelegate context, final String rawUrl, @Nullable UrlOpenParameters openParameters, @Nullable RunnableBool after) {
    if (StringUtils.isEmpty(rawUrl) || tdlib.context().inRecoveryMode()) {
      if (after != null)
        after.runWithBool(false);
      return;
    }
    AtomicReference<String> url = new AtomicReference<>(preProcessTelegramUrl(rawUrl));
    tdlib.send(new TdApi.GetInternalLinkType(url.get()), new Tdlib.ResultHandler<>() {
      @Override
      public void onResult (TdApi.InternalLinkType internalLinkType, @Nullable TdApi.Error error) {
        final String currentUrl = url.get();
        TdApi.InternalLinkType linkType;
        if (error != null) {
          linkType = parseTelegramUrl(rawUrl);
        } else if (internalLinkType instanceof TdApi.InternalLinkTypeUnknownDeepLink) {
          TdApi.InternalLinkType parsedType = parseTelegramUrl(rawUrl);
          linkType = parsedType != null ? parsedType : internalLinkType;
        } else {
          linkType = internalLinkType;
        }
        if ((linkType == null || internalLinkType instanceof TdApi.InternalLinkTypeUnknownDeepLink) && !url.get().equals(rawUrl)) {
          url.set(rawUrl);
          tdlib.send(new TdApi.GetInternalLinkType(url.get()), this);
          return;
        }
        if (linkType == null) {
          if (after != null) {
            post(() -> after.runWithBool(false));
          }
          return;
        }
        post(() ->
          openInternalLinkType(context, currentUrl, linkType, openParameters, after)
        );
      }
    });
  }

  public void openInternalLinkType (TdlibDelegate context, @Nullable String originalUrl, @NonNull TdApi.InternalLinkType linkType, @Nullable UrlOpenParameters openParameters, @Nullable RunnableBool after) {
    if (!UI.inUiThread()) {
      post(() ->
        openInternalLinkType(context, originalUrl, linkType, openParameters, after)
      );
      return;
    }
    if (context.context().navigation().isDestroyed()) {
      if (after != null) {
        after.runWithBool(false);
      }
      return;
    }
    boolean ok = true;
    switch (linkType.getConstructor()) {
      case TdApi.InternalLinkTypeStickerSet.CONSTRUCTOR: {
        TdApi.InternalLinkTypeStickerSet stickerSet = (TdApi.InternalLinkTypeStickerSet) linkType;
        showStickerSet(context, stickerSet.stickerSetName, openParameters);
        break;
      }
      case TdApi.InternalLinkTypeAuthenticationCode.CONSTRUCTOR: {
        TdApi.InternalLinkTypeAuthenticationCode authCode = (TdApi.InternalLinkTypeAuthenticationCode) linkType;
        tdlib.listeners().updateAuthorizationCodeReceived(authCode.code);
        break;
      }
      case TdApi.InternalLinkTypeLanguagePack.CONSTRUCTOR: {
        TdApi.InternalLinkTypeLanguagePack languagePack = (TdApi.InternalLinkTypeLanguagePack) linkType;
        ok = installLanguage(context, languagePack.languagePackId, openParameters);
        break;
      }
      case TdApi.InternalLinkTypeChatInvite.CONSTRUCTOR: {
        TdApi.InternalLinkTypeChatInvite chatInvite = (TdApi.InternalLinkTypeChatInvite) linkType;
        checkInviteLink(context, chatInvite.inviteLink, openParameters);
        break;
      }
      case TdApi.InternalLinkTypeMessageDraft.CONSTRUCTOR: {
        TdApi.InternalLinkTypeMessageDraft messageDraft = (TdApi.InternalLinkTypeMessageDraft) linkType;
        ShareController c = new ShareController(context.context(), context.tdlib());
        c.setArguments(new ShareController.Args(messageDraft.text));
        c.show();
        break;
      }
      case TdApi.InternalLinkTypePhoneNumberConfirmation.CONSTRUCTOR: {
        TdApi.InternalLinkTypePhoneNumberConfirmation confirmPhone = (TdApi.InternalLinkTypePhoneNumberConfirmation) linkType;
        TdApi.PhoneNumberAuthenticationSettings authenticationSettings = context.tdlib().phoneNumberAuthenticationSettings(context.context());
        // TODO progress?
        ViewController<?> currentController = context.context().navigation().getCurrentStackItem();
        tdlib.send(new TdApi.SendPhoneNumberCode(confirmPhone.phoneNumber, authenticationSettings, new TdApi.PhoneNumberCodeTypeConfirmOwnership(confirmPhone.hash)), (authernticationCodeInfo, error) -> {
          if (error != null) {
            showLinkTooltip(tdlib, R.drawable.baseline_warning_24, TD.toErrorString(error), openParameters);
          } else {
            post(() -> {
              if (currentController != null && !currentController.isDestroyed()) {
                confirmPhone(context, authernticationCodeInfo, confirmPhone.phoneNumber);
              }
            });
          }
        });
        break;
      }
      case TdApi.InternalLinkTypeProxy.CONSTRUCTOR: {
        TdApi.InternalLinkTypeProxy proxy = (TdApi.InternalLinkTypeProxy) linkType;
        openProxyAlert(context, proxy.server, proxy.port, proxy.type, newProxyDescription(proxy.server, Integer.toString(proxy.port)).toString());
        break;
      }
      case TdApi.InternalLinkTypeUnsupportedProxy.CONSTRUCTOR: {
        showLinkTooltip(tdlib, R.drawable.baseline_warning_24, Lang.getString(R.string.ProxyLinkUnsupported), openParameters);
        break;
      }
      case TdApi.InternalLinkTypeUserPhoneNumber.CONSTRUCTOR: {
        final String phoneNumber = ((TdApi.InternalLinkTypeUserPhoneNumber) linkType).phoneNumber;
        openChatProfile(context, 0, null, new TdApi.SearchUserByPhoneNumber(phoneNumber, false), openParameters);
        break;
      }

      case TdApi.InternalLinkTypeUserToken.CONSTRUCTOR: {
        final String token = ((TdApi.InternalLinkTypeUserToken) linkType).token;
        openChatProfile(context, 0, null, new TdApi.SearchUserByToken(token), openParameters);
        break;
      }
      case TdApi.InternalLinkTypeVideoChat.CONSTRUCTOR: {
        TdApi.InternalLinkTypeVideoChat voiceChatInvitation = (TdApi.InternalLinkTypeVideoChat) linkType;
        openVideoChatOrLiveStream(context, voiceChatInvitation, openParameters);
        break;
      }
      case TdApi.InternalLinkTypeMessage.CONSTRUCTOR: {
        TdApi.InternalLinkTypeMessage messageLink = (TdApi.InternalLinkTypeMessage) linkType;
        // TODO show progress?
        tdlib.client().send(new TdApi.GetMessageLinkInfo(messageLink.url), messageLinkResult -> {
          switch (messageLinkResult.getConstructor()) {
            case TdApi.MessageLinkInfo.CONSTRUCTOR: {
              TdApi.MessageLinkInfo messageLinkInfo = (TdApi.MessageLinkInfo) messageLinkResult;
              post(() -> {
                openMessage(context, messageLinkInfo, openParameters);
                if (after != null) {
                  after.runWithBool(true);
                }
              });
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              if (after != null) {
                post(() -> after.runWithBool(false));
              }
              break;
            }
          }
        });
        return; // async
      }
      case TdApi.InternalLinkTypeBotStart.CONSTRUCTOR: {
        TdApi.InternalLinkTypeBotStart startBot = (TdApi.InternalLinkTypeBotStart) linkType;
        startBot(context, startBot.botUsername, startBot.startParameter, BOT_MODE_START, openParameters);
        break;
      }
      case TdApi.InternalLinkTypeBotStartInGroup.CONSTRUCTOR: {
        TdApi.InternalLinkTypeBotStartInGroup startBot = (TdApi.InternalLinkTypeBotStartInGroup) linkType;
        startBot(context, startBot.botUsername, startBot.startParameter, BOT_MODE_START_IN_GROUP, openParameters);
        break;
      }
      case TdApi.InternalLinkTypeBotAddToChannel.CONSTRUCTOR: {
        TdApi.InternalLinkTypeBotAddToChannel addToChannel = (TdApi.InternalLinkTypeBotAddToChannel) linkType;
        // TODO add to channel flow
        showLinkTooltip(tdlib, R.drawable.baseline_warning_24, Lang.getString(R.string.InternalUrlUnsupported), openParameters);
        break;
      }
      case TdApi.InternalLinkTypeGame.CONSTRUCTOR: {
        TdApi.InternalLinkTypeGame game = (TdApi.InternalLinkTypeGame) linkType;
        startBot(context, game.botUsername, game.gameShortName, BOT_MODE_START_GAME, openParameters);
        break;
      }
      case TdApi.InternalLinkTypeSettings.CONSTRUCTOR:
      case TdApi.InternalLinkTypeEditProfileSettings.CONSTRUCTOR: {
        SettingsController c = new SettingsController(context.context(), context.tdlib());
        context.context().navigation().navigateTo(c);
        break;
      }
      case TdApi.InternalLinkTypeLanguageSettings.CONSTRUCTOR: {
        SettingsLanguageController c = new SettingsLanguageController(context.context(), context.tdlib());
        context.context().navigation().navigateTo(c);
        break;
      }
      case TdApi.InternalLinkTypePrivacyAndSecuritySettings.CONSTRUCTOR: {
        SettingsPrivacyController c = new SettingsPrivacyController(context.context(), context.tdlib());
        context.context().navigation().navigateTo(c);
        break;
      }
      case TdApi.InternalLinkTypeThemeSettings.CONSTRUCTOR: {
        SettingsThemeController c = new SettingsThemeController(context.context(), context.tdlib());
        c.setArguments(new SettingsThemeController.Args(SettingsThemeController.MODE_THEMES));
        context.context().navigation().navigateTo(c);
        break;
      }
      case TdApi.InternalLinkTypePublicChat.CONSTRUCTOR: {
        TdApi.InternalLinkTypePublicChat publicChat = (TdApi.InternalLinkTypePublicChat) linkType;
        if (TdConstants.IV_PREVIEW_USERNAME.equals(publicChat.chatUsername) & !StringUtils.isEmpty(originalUrl)) {
          openExternalUrl(context, originalUrl, new UrlOpenParameters(openParameters).forceInstantView(), after);
        } else {
          openPublicChat(context, publicChat.chatUsername, openParameters);
        }
        break;
      }
      case TdApi.InternalLinkTypeInstantView.CONSTRUCTOR: {
        TdApi.InternalLinkTypeInstantView instantView = (TdApi.InternalLinkTypeInstantView) linkType;
        UrlOpenParameters instantViewOpenParameters = new UrlOpenParameters(openParameters)
          .forceInstantView()
          .instantViewFallbackUrl(instantView.fallbackUrl);
        if (!StringUtils.isEmpty(originalUrl)) {
          instantViewOpenParameters.originalUrl(originalUrl);
        }
        openExternalUrl(context, instantView.url, instantViewOpenParameters, after);
        break;
      }
      case TdApi.InternalLinkTypeActiveSessions.CONSTRUCTOR: {
        SettingsSessionsController sessions = new SettingsSessionsController(context.context(), context.tdlib());
        SettingsWebsitesController websites = new SettingsWebsitesController(context.context(), context.tdlib());
        ViewController<?> c = new SimpleViewPagerController(context.context(), context.tdlib(), new ViewController[] {sessions, websites}, new String[] {Lang.getString(R.string.Devices).toUpperCase(), Lang.getString(R.string.Websites).toUpperCase()}, false);
        context.context().navigation().navigateTo(c);
        break;
      }

      case TdApi.InternalLinkTypeStory.CONSTRUCTOR:
      case TdApi.InternalLinkTypeDefaultMessageAutoDeleteTimerSettings.CONSTRUCTOR:

      case TdApi.InternalLinkTypeAttachmentMenuBot.CONSTRUCTOR:
      case TdApi.InternalLinkTypeWebApp.CONSTRUCTOR:
      case TdApi.InternalLinkTypeMainWebApp.CONSTRUCTOR:

      case TdApi.InternalLinkTypeInvoice.CONSTRUCTOR:

      case TdApi.InternalLinkTypePremiumFeatures.CONSTRUCTOR:
      case TdApi.InternalLinkTypeRestorePurchases.CONSTRUCTOR:
      case TdApi.InternalLinkTypeBuyStars.CONSTRUCTOR:
      case TdApi.InternalLinkTypeChatBoost.CONSTRUCTOR:
      case TdApi.InternalLinkTypePremiumGift.CONSTRUCTOR:

      case TdApi.InternalLinkTypePassportDataRequest.CONSTRUCTOR: {
        showLinkTooltip(tdlib, R.drawable.baseline_warning_24, Lang.getString(R.string.InternalUrlUnsupported), openParameters);
        break;
      }

      case TdApi.InternalLinkTypePremiumGiftCode.CONSTRUCTOR: {
        final String code = ((TdApi.InternalLinkTypePremiumGiftCode) linkType).code;

        // TODO progress
        tdlib.send(new TdApi.CheckPremiumGiftCode(code), (info, error) -> {
          if (error != null) {
            if (after != null) {
              post(() -> after.runWithBool(false));
            }
          } else {
            post(() -> {
              ModernActionedLayout.showGiftCode(context.context().navigation().getCurrentStackItem(), code, null, info);
              if (after != null) {
                after.runWithBool(true);
              }
            });
          }
        });
        break;
      }

      case TdApi.InternalLinkTypeChangePhoneNumber.CONSTRUCTOR: {
        SettingsPhoneController c = new SettingsPhoneController(context.context(), context.tdlib());
        context.context().navigation().navigateTo(c);
        break;
      }
      case TdApi.InternalLinkTypeQrCodeAuthentication.CONSTRUCTOR: {
        showLinkTooltip(tdlib, R.drawable.baseline_warning_24, Lang.getString(R.string.ScanQRLinkHint), openParameters);
        break;
      }
      case TdApi.InternalLinkTypeTheme.CONSTRUCTOR: {
        TdApi.InternalLinkTypeTheme theme = (TdApi.InternalLinkTypeTheme) linkType;
        // TODO tdlib
        showLinkTooltip(tdlib, R.drawable.baseline_info_24, Lang.getMarkdownString(context, R.string.NoCloudThemeSupport), openParameters);
        break;
      }
      case TdApi.InternalLinkTypeBackground.CONSTRUCTOR: {
        TdApi.InternalLinkTypeBackground background = (TdApi.InternalLinkTypeBackground) linkType;
        // TODO show progress?
        tdlib.send(new TdApi.SearchBackground(background.backgroundName), (wallpaper, error) -> {
          if (error != null) {
            if (after != null) {
              post(() -> after.runWithBool(false));
            }
          } else {
            post(() -> {
              MessagesController c = new MessagesController(context.context(), context.tdlib());
              c.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_WALLPAPER_OBJECT, null, null).setWallpaperObject(wallpaper));
              context.context().navigation().navigateTo(c);

              if (after != null) {
                after.runWithBool(true);
              }
            });
          }
        });
        return;
      }
      case TdApi.InternalLinkTypeChatFolderSettings.CONSTRUCTOR: {
        if (Settings.instance().chatFoldersEnabled()) {
          SettingsFoldersController chatFolders = new SettingsFoldersController(context.context(), context.tdlib());
          context.context().navigation().navigateTo(chatFolders);
        } else {
          showLinkTooltip(tdlib, R.drawable.baseline_warning_24, Lang.getString(R.string.InternalUrlUnsupported), openParameters);
        }
        break;
      }
      case TdApi.InternalLinkTypeChatFolderInvite.CONSTRUCTOR: {
        if (Settings.instance().chatFoldersEnabled()) {
          TdApi.InternalLinkTypeChatFolderInvite invite = (TdApi.InternalLinkTypeChatFolderInvite) linkType;
          checkChatFolderInviteLink(context, originalUrl, invite, openParameters);
        } else {
          showLinkTooltip(tdlib, R.drawable.baseline_warning_24, Lang.getString(R.string.InternalUrlUnsupported), openParameters);
        }
        break;
      }
      case TdApi.InternalLinkTypeBusinessChat.CONSTRUCTOR: {
        TdApi.InternalLinkTypeBusinessChat businessChatLink = (TdApi.InternalLinkTypeBusinessChat) linkType;
        tdlib.send(new TdApi.GetBusinessChatLinkInfo(businessChatLink.linkName), (businessChatLinkInfo, error) -> {
          if (error != null) {
            post(() -> {
              showLinkTooltip(tdlib, R.drawable.baseline_warning_24, TD.toErrorString(error), openParameters);
              if (after != null) {
                after.runWithBool(false);
              }
            });
          } else {
            post(() -> {
              openChat(context, businessChatLinkInfo.chatId, new ChatOpenParameters()
                .keepStack()
                .fillDraft(businessChatLinkInfo.text)
              );
              if (after != null) {
                after.runWithBool(true);
              }
            });
          }
        });
        return; // async
      }
      case TdApi.InternalLinkTypeUnknownDeepLink.CONSTRUCTOR: {
        // TODO progress
        TdApi.InternalLinkTypeUnknownDeepLink unknownDeepLink = (TdApi.InternalLinkTypeUnknownDeepLink) linkType;
        tdlib.send(new TdApi.GetDeepLinkInfo(unknownDeepLink.link), (deepLink, error) -> {
          if (error != null) {
            if (after != null) {
              post(() -> after.runWithBool(false));
            }
          } else {
            post(() -> {
              ViewController<?> c = context.context().navigation().getCurrentStackItem();
              if (c != null) {
                c.processDeepLinkInfo(deepLink);
              }
              if (after != null) {
                after.runWithBool(true);
              }
            });
          }
        });
        return; // async
      }
      default: {
        Td.assertInternalLinkType_ff0c4471();
        throw Td.unsupported(linkType);
      }
    }
    if (after != null) {
      after.runWithBool(ok);
    }
  }

  public static StringBuilder newProxyDescription (String server, String port) {
    StringBuilder desc = new StringBuilder("<b>");
    desc.append(Lang.getString(R.string.UseProxyServer));
    desc.append(":</b> ");
    desc.append(server);
    desc.append("<br/><b>");
    desc.append(Lang.getString(R.string.UseProxyPort));
    desc.append(":</b> ");
    desc.append(port);
    return desc;
  }

  private static void addProxyField (StringBuilder desc, String name, String value) {
    desc.append("<br/><b>");
    desc.append(name);
    desc.append(":</b> ");
    desc.append(value);
  }

  public void showUrlOptions (TdlibDelegate context, String url, Future<UrlOpenParameters> openParametersFuture) {
    ViewController<?> c = context.context().navigation().getCurrentStackItem();
    if (c == null)
      return;

    c.showOptions(url, new int[] {
      R.id.btn_open,
      R.id.btn_copyLink,
      R.id.btn_share
    }, new String[] {
      Lang.getString(R.string.Open),
      Lang.getString(R.string.Copy),
      Lang.getString(R.string.Share)
    }, null, new int[] {
      R.drawable.baseline_open_in_browser_24,
      R.drawable.baseline_content_copy_24,
      R.drawable.baseline_forward_24
    }, (optionItemView, id) -> {
      if (id == R.id.btn_open) {
        openUrl(context, url, openParametersFuture != null ? openParametersFuture.getValue() : null);
      } else if (id == R.id.btn_copyLink) {
        UI.copyText(url, R.string.CopiedLink);
      } else if (id == R.id.btn_share) {
        shareUrl(context, url);
      }
      return true;
    });
  }

  public void openProxyAlert (TdlibDelegate context, String server, int port, TdApi.ProxyType type, String proxyDescription) {
    ViewController<?> c = context.context().navigation().getCurrentStackItem();
    if (c == null)
      return;

    final SpannableStringBuilder msg = Strings.buildHtml(proxyDescription);
    msg.insert(0, "\n\n");
    String title = Lang.getString(R.string.EnableProxyAlertTitle);
    msg.insert(0, title);
    msg.setSpan(TD.newBoldSpan(title), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    msg.append("\n\n");
    msg.append(Lang.getString(R.string.EnableProxyAlertHint));
    if (type.getConstructor() == TdApi.ProxyTypeMtproto.CONSTRUCTOR) {
      msg.append("\n\n");
      msg.append(Lang.getString(R.string.EnableProxyAlertHintMtproto));
    }
    IntList ids = new IntList(3);
    StringList strings = new StringList(3);
    IntList icons = new IntList(3);
    IntList colors = new IntList(3);

    ids.append(R.id.btn_addProxy);
    strings.append(R.string.ProxyEnable);
    icons.append(R.drawable.baseline_security_24);
    colors.append(ViewController.OptionColor.BLUE);

    ids.append(R.id.btn_save);
    strings.append(R.string.ProxySaveForLater);
    icons.append(R.drawable.baseline_playlist_add_24);
    colors.append(ViewController.OptionColor.NORMAL);

    ids.append(R.id.btn_cancel);
    strings.append(R.string.Cancel);
    icons.append(R.drawable.baseline_cancel_24);
    colors.append(ViewController.OptionColor.NORMAL);

    c.showOptions(msg, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_addProxy) {
        Settings.instance().addOrUpdateProxy(new TdApi.InternalLinkTypeProxy(server, port, type), null, true);
      } else if (id == R.id.btn_save) {
        Settings.instance().addOrUpdateProxy(new TdApi.InternalLinkTypeProxy(server, port, type), null, false);
      }
      return true;
    });
  }

  // Delete account on server

  public void permanentlyDeleteAccount (ViewController<?> context, boolean showAlternatives) {
    boolean needShowAlternatives = tdlib.isAuthorized() && showAlternatives;
    context.showOptions(
      Lang.getMarkdownString(context, needShowAlternatives ? R.string.DeleteAccountConfirmFirst : R.string.DeleteAccountConfirm),
      new int[] {R.id.btn_deleteAccount, R.id.btn_cancel},
      new String[] {Lang.getString(needShowAlternatives ? R.string.DeleteAccountConfirmFirstBtn : R.string.DeleteAccountConfirmBtn), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_alert_24, R.drawable.baseline_cancel_24},
      (optionItemView, id) -> {
        if (id == R.id.btn_deleteAccount) {
          if (needShowAlternatives) {
            SettingsLogOutController c = new SettingsLogOutController(context.context(), tdlib);
            c.setArguments(SettingsLogOutController.Type.DELETE_ACCOUNT);
            context.navigateTo(c);
            return true;
          }

          tdlib.send(new TdApi.GetPasswordState(), (passwordState, error) -> context.runOnUiThreadOptional(() -> {
            if (error != null) {
              UI.showError(error);
              return;
            }
            context.runOnUiThreadOptional(() -> {
              if (!passwordState.hasPassword) {
                context.navigateTo(new EditDeleteAccountReasonController(context.context(), tdlib));
                return;
              }
              promptPassword(context, passwordState, new PasswordController.CustomConfirmDelegate() {
                @Override
                public CharSequence getName () {
                  return Lang.getString(R.string.DeleteAccount);
                }

                @Override
                public boolean needNext () {
                  return true;
                }

                @Override
                public void onPasswordConfirmed (ViewController<?> c, String password) {
                  EditDeleteAccountReasonController target = new EditDeleteAccountReasonController(context.context(), tdlib);
                  target.setArguments(password);
                  c.navigateTo(target);
                }
              });
            });
          }));
        }
        return true;
      }
    );
  }

  private void promptPassword (ViewController<?> context, TdApi.PasswordState passwordState, @NonNull PasswordController.CustomConfirmDelegate confirmDelegate) {
    PasswordController controller = new PasswordController(context.context(), context.tdlib());
    controller.setArguments(new PasswordController.Args(PasswordController.MODE_CUSTOM_CONFIRM, passwordState)
      .setConfirmDelegate(confirmDelegate)
    );
    context.navigateTo(controller);
  }

  // Log out

  public void logOut (ViewController<?> context, boolean showAlternatives) {
    if (tdlib.myUserId() == 0) {
      return;
    }
    if (showAlternatives) {
      SettingsLogOutController c = new SettingsLogOutController(context.context(), tdlib);
      context.navigateTo(c);
      return;
    }
    removeAccount(context, tdlib.account(), true);
    /*context.showOptions(new int[]{R.id.btn_logout, R.id.btn_cancel}, new String[]{Lang.getString(R.string.LogOut), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, id -> {
      if (id == R.id.btn_logout) {
        tdlib.client().send(new TdApi.LogOut(), tdlib.okHandler());
        // TD.clearAll(tdlib.id(), true);
        ImageLoader.instance().clear(tdlib.id(), true);
        *//* TODO reset prefs

        Prefs.instance().clear();
        ContactManager.instance().reset(false, null);*//*

        // TGDataManager.instance().logOut();
        // proceedToLogin();
      }
      return true;
    });*/
  }

  public static int stringForConnectionState (@ConnectionState int state) {
    switch (state) {
      case ConnectionState.CONNECTED:
        return R.string.Connected;
      case ConnectionState.CONNECTING:
      case ConnectionState.UNKNOWN:
        return R.string.network_Connecting;
      case ConnectionState.CONNECTING_TO_PROXY:
        return R.string.ConnectingToProxy;
      case ConnectionState.WAITING_FOR_NETWORK:
        return R.string.network_WaitingForNetwork;
      case ConnectionState.UPDATING:
        return R.string.network_Updating;
    }
    throw new RuntimeException();
  }

  // Map

  public boolean openMap (TdlibDelegate context, MapController.Args args) {
    if (!U.isGooglePlayServicesAvailable(context.context())) {
      return Intents.openMap(args.latitude, args.longitude, args.title, args.address);
    }
    MapController<?,?> c = MapControllerFactory.newMapController(context.context(), context.tdlib());
    c.setArguments(args);
    context.context().navigation().navigateTo(c);
    return true;
  }

  // Proxy

  /**
   * Opens available proxy list.
   *
   * If no proxies available, opens proxy add screen.
   */
  public void openProxySettings (TdlibDelegate context, boolean needProxyHint) {
    if (Settings.instance().getAvailableProxyCount() == 0) {
      addNewProxy(context, needProxyHint);
    } else {
      context.context().navigation().navigateTo(new SettingsProxyController(context.context(), context.tdlib()));
    }
  }

  /**
   * Asks which type of proxy should be added,
   * then opens appropriate screen
   */
  public void addNewProxy (TdlibDelegate context, boolean needProxyHint) {
    ViewController<?> c = context.context().navigation().getCurrentStackItem();
    if (c == null) {
      return;
    }

    /*boolean needTor = !checkTorAvailability;
    if (checkTorAvailability) {
      TdApi.ProxySocks5 torProxy = new TdApi.ProxySocks5("127.0.0.1", 9050, null, null);
      int proxyId = Settings.instance().getExistingProxyId(torProxy);
      needTor = proxyId == Settings.PROXY_ID_NONE;
    }
    boolean needInstallTor = needTor && !U.isAppInstalled(U.PACKAGE_TOR);*/

    boolean showShowQr = tdlib.allowQrLoginCamera();

    IntList ids = new IntList(showShowQr ? 4 : 3);
    StringList strings = new StringList(showShowQr ? 4 : 3);

    ids.append(R.id.btn_proxyTelegram);
    ids.append(R.id.btn_proxySocks5);
    ids.append(R.id.btn_proxyHttp);

    if (needProxyHint) {
      strings.append(R.string.AddMtprotoProxy);
      strings.append(R.string.AddSocks5Proxy);
      strings.append(R.string.AddHttpProxy);
    } else {
      strings.append(R.string.MtprotoProxy);
      strings.append(R.string.Socks5Proxy);
      strings.append(R.string.HttpProxy);
    }

    if (showShowQr) {
      ids.append(R.id.btn_proxyQr);
      strings.append(R.string.ScanQR);
    }

    OptionDelegate callback = (itemView, id) -> {
      if (id == R.id.btn_proxySocks5) {
        EditProxyController e = new EditProxyController(context.context(), context.tdlib());
        e.setArguments(new EditProxyController.Args(EditProxyController.MODE_SOCKS5));
        c.navigateTo(e);
      } else if (id == R.id.btn_proxyTelegram) {
        EditProxyController e = new EditProxyController(context.context(), context.tdlib());
        e.setArguments(new EditProxyController.Args(EditProxyController.MODE_MTPROTO));
        c.navigateTo(e);
      } else if (id == R.id.btn_proxyHttp) {
        EditProxyController e = new EditProxyController(context.context(), context.tdlib());
        e.setArguments(new EditProxyController.Args(EditProxyController.MODE_HTTP));
        c.navigateTo(e);
      } else if (id == R.id.btn_proxyQr) {
        postDelayed(() -> c.openInAppCamera(new ViewController.CameraOpenOptions().ignoreAnchor(true).noTrace(true).allowSystem(false).optionalMicrophone(true).qrModeSubtitle(R.string.ScanQRFullSubtitleProxy).mode(CameraController.MODE_QR).qrCodeListener((qrCode) -> {
          context.tdlib().client().send(new TdApi.GetInternalLinkType(qrCode), result -> {
            if (result.getConstructor() == TdApi.InternalLinkTypeProxy.CONSTRUCTOR) {
              post(() -> {
                TdApi.InternalLinkTypeProxy proxy = (TdApi.InternalLinkTypeProxy) result;
                openProxyAlert(context, proxy.server, proxy.port, proxy.type, TdlibUi.newProxyDescription(proxy.server, Integer.toString(proxy.port)).toString());
              });
            }
          });
        })), 250L);
        /*case R.id.btn_proxyTor: {
          if (needInstallTor) {
            UI.showToast(R.string.ProxyTorUnavailable, Toast.LENGTH_SHORT);
            Intents.openGooglePlay(U.PACKAGE_TOR);
            return false;
          } else {
            Settings.instance().addOrUpdateProxy(new TdApi.ProxySocks5("127.0.0.1", 9050, null, null), "Tor network", false);
          }
          break;
        }*/
      }
      return true;
    };

    if (ids.size() == 1) {
      callback.onOptionItemPressed(null, ids.get(0));
    } else {
      c.showOptions(needProxyHint ? Lang.getString(R.string.ProxyInfo) : null, ids.get(), strings.get(), callback);
    }
  }

  public void handleTermsOfService (TdApi.UpdateTermsOfService update) {
    // TODO handle updateTermsOfService
    // Log.i("%s", update);
  }

  // Leave/join chat

  public boolean processLeaveButton (ViewController<?> context, TdApi.ChatList chatList, long chatId, int actionId, @Nullable Runnable after) {
    if (actionId == R.id.btn_returnToChat) {
      leaveJoinChat(context, chatId, true, after);
      return true;
    } else if (actionId == R.id.btn_removePsaChatFromList) {
      showHidePsaConfirm(context, chatList, chatId, after);
      return true;
    } else if (actionId == R.id.btn_removeChatFromList || actionId == R.id.btn_removeChatFromListAndStop) {
      showDeleteChatConfirm(context, chatId, false, actionId == R.id.btn_removeChatFromListAndStop, after);
      return true;
    } else if (actionId == R.id.btn_removeChatFromListOrClearHistory) {
      showDeleteChatConfirm(context, chatId, !tdlib.isChannel(chatId), tdlib.suggestStopBot(chatId), after);
      return true;
    } else if (actionId == R.id.btn_clearChatHistory) {
      showClearHistoryConfirm(context, chatId, after);
      return true;
    } else if (actionId == R.id.btn_setPasscode) {
      showPasscodeOptions(context, chatId);
      return true;
    }
    return false;
  }

  public void addDeleteChatOptions (long chatId, IntList ids, StringList strings, boolean allowClearHistory, boolean forceJoin) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null)
      return;
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        if (allowClearHistory && tdlib.canClearHistory(chatId)) {
          ids.append(R.id.btn_clearChatHistory);
          strings.append(R.string.ClearHistory);
        }

        if (tdlib.suggestStopBot(chat)) {
          ids.append(R.id.btn_removeChatFromListAndStop);
          strings.append(R.string.DeleteAndStop);
        } else {
          ids.append(R.id.btn_removeChatFromList);
          strings.append(R.string.DeleteChat);
        }
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        if (allowClearHistory && tdlib.canClearHistory(chat)) {
          ids.append(R.id.btn_clearChatHistory);
          strings.append(R.string.ClearHistory);
        }
        TdApi.BasicGroup basicGroup = tdlib.chatToBasicGroup(chatId);
        TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
        if (tdlib.chatAvailable(chat) && status != null) {
          if (TD.isMember(status, false) || (basicGroup != null && !basicGroup.isActive)) {
            ids.append(R.id.btn_removeChatFromList);
            strings.append(R.string.LeaveMegaMenu);
          } else {
            ids.append(R.id.btn_removeChatFromList);
            strings.append(R.string.DeleteChat);
          }
          if (TD.canReturnToChat(status) && (basicGroup == null || basicGroup.isActive)) {
            ids.append(R.id.btn_returnToChat);
            strings.append(R.string.returnToGroup);
          }
        }
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (allowClearHistory && tdlib.canClearHistory(chat)) {
          ids.append(R.id.btn_clearChatHistory);
          strings.append(R.string.ClearHistory);
        }
        TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
        if (tdlib.chatAvailable(chat)) {
          ids.append(R.id.btn_removeChatFromList);
          strings.append(tdlib.isChannel(chatId) ? R.string.LeaveChannel : R.string.LeaveMegaMenu);
        } else if (TD.canReturnToChat(status)) {
          if (status.getConstructor() == TdApi.ChatMemberStatusLeft.CONSTRUCTOR && (tdlib.isPublicChat(chatId) || tdlib.isTemporaryAccessible(chatId))) {
            if (forceJoin) {
              ids.append(R.id.btn_returnToChat);
              strings.append(tdlib.isChannel(chatId) ? R.string.JoinChannel : R.string.JoinChat);
            }
          } else {
            ids.append(R.id.btn_returnToChat);
            strings.append(tdlib.isChannel(chatId) ? R.string.returnToChannel : R.string.returnToGroup);
          }
        }
        break;
      }
    }
  }

  private void leaveJoinChat (ViewController<?> context, long chatId, boolean join, @Nullable Runnable after) {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    if (status == null) {
      return;
    }
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }
    int confirmButtonRes = 0;
    String informationStr = null;
    boolean forceAdd = false;
    boolean hasPublicLink = !StringUtils.isEmpty(tdlib.chatUsername(chatId));
    boolean isChannel = tdlib.isChannel(chatId);
    boolean canReturnAfterLeave = ChatId.isBasicGroup(chatId) || hasPublicLink;
    final TdApi.ChatMemberStatus newStatus;
    switch (status.getConstructor()) {
      case TdApi.ChatMemberStatusCreator.CONSTRUCTOR: {
        forceAdd = !((TdApi.ChatMemberStatusCreator) status).isMember;
        if (join != forceAdd) {
          return;
        }
        TdApi.ChatMemberStatusCreator oldStatus = ((TdApi.ChatMemberStatusCreator) status);
        newStatus = new TdApi.ChatMemberStatusCreator(oldStatus.customTitle, oldStatus.isAnonymous, forceAdd);
        if (!forceAdd) {
          informationStr = Lang.getString(hasPublicLink ? (isChannel ? R.string.LeaveReturnPublicLinkHintChannel : R.string.LeaveReturnPublicLinkHintGroup) : (isChannel ? R.string.LeaveCreatorHintChannel : R.string.LeaveCreatorHintGroup));
          confirmButtonRes = isChannel ? R.string.LeaveChannel : R.string.LeaveMegaMenu;
        }
        break;
      }
      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        if (join) {
          return;
        }
        informationStr = Lang.getString(canReturnAfterLeave ? R.string.LeaveChatAdminHint : (isChannel ? R.string.LeaveAdminNoReturnHintChannel : R.string.LeaveAdminNoReturnHintGroup));
        confirmButtonRes = isChannel ? R.string.LeaveChannel : R.string.LeaveMegaMenu;
        newStatus = new TdApi.ChatMemberStatusLeft();
        break;
      case TdApi.ChatMemberStatusMember.CONSTRUCTOR: {
        if (join) {
          return;
        }
        confirmButtonRes = isChannel ? R.string.LeaveChannel : R.string.LeaveMegaMenu;
        if (canReturnAfterLeave)
          informationStr = hasPublicLink ? Lang.getString(isChannel ? R.string.LeaveReturnPublicLinkHintChannel : R.string.LeaveReturnPublicLinkHintGroup) : null;
        else
          informationStr = Lang.getString(isChannel ? R.string.LeaveNoReturnHintChannel : R.string.LeaveNoReturnHintGroup);
        newStatus = new TdApi.ChatMemberStatusLeft();
        break;
      }
      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR: {
        TdApi.ChatMemberStatusRestricted restricted = (TdApi.ChatMemberStatusRestricted) status;
        if (join == restricted.isMember) {
          return;
        }
        if (restricted.isMember) {
          newStatus = new TdApi.ChatMemberStatusLeft();
          if (canReturnAfterLeave)
            informationStr = hasPublicLink ? Lang.getString(isChannel ? R.string.LeaveReturnPublicLinkHintChannel : R.string.LeaveReturnPublicLinkHintGroup) : null;
          else
            informationStr = Lang.getString(isChannel ? R.string.LeaveNoReturnHintGroup : R.string.LeaveNoReturnHintChannel);
          confirmButtonRes = isChannel ? R.string.LeaveChannel : R.string.LeaveMegaMenu;
        } else {
          newStatus = new TdApi.ChatMemberStatusMember();
          forceAdd = true;
        }
        break;
      }
      case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
        if (!join)
          return;
        newStatus = new TdApi.ChatMemberStatusMember();
        break;
      case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
      default:
        newStatus = null;
        break;
    }
    if (newStatus == null) {
      if (join) {
        UI.showToast(R.string.NoReturnToChat, Toast.LENGTH_SHORT);
      }
      return;
    }
    boolean needAdd = forceAdd || (ChatId.isBasicGroup(chatId) && status.getConstructor() == TdApi.ChatMemberStatusLeft.CONSTRUCTOR && newStatus.getConstructor() == TdApi.ChatMemberStatusMember.CONSTRUCTOR);
    RunnableBool act = (deleteChat) -> {
      long myUserId = tdlib.myUserId();
      if (myUserId != 0) {
        if (needAdd) {
          tdlib.client().send(new TdApi.AddChatMember(chatId, myUserId, 0), tdlib.okHandler());
        } else {
          Client.ResultHandler handler = tdlib.okHandler();
          if (deleteChat) {
            handler = result -> {
              switch (result.getConstructor()) {
                case TdApi.Ok.CONSTRUCTOR: {
                  if (ChatId.isBasicGroup(chatId)) {
                    tdlib.client().send(new TdApi.DeleteChatHistory(chatId, true, false), tdlib.okHandler());
                  }
                  break;
                }
                case TdApi.Error.CONSTRUCTOR: {
                  UI.showError(result);
                  Log.e("setChatMemberStatus chatId:%d, status:%s error:%s", chatId, newStatus, TD.toErrorString(result));
                  break;
                }
              }
            };
          }
          tdlib.client().send(new TdApi.SetChatMemberStatus(chatId, new TdApi.MessageSenderUser(myUserId), newStatus), handler);
          if (!TD.isMember(newStatus, false)) {
            exitToChatScreen(context, chatId);
          }
        }
      }
    };
    if (confirmButtonRes != 0) {
      int checkId = join ? R.id.btn_returnToChat : R.id.btn_removeChatFromList;
      if (!join && ChatId.isBasicGroup(chatId)) {
        SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_removeChatFromList).setSaveStr(R.string.LeaveDoneGroup).setSaveColorId(ColorId.textNegative);
        if (informationStr != null) {
          b.addHeaderItem(informationStr);
        }
        b.setRawItems(new ListItem[] {
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_removeChatFromList, 0, R.string.LeaveRemoveFromList, R.id.btn_removeChatFromList, true)
        });
        b.setIntDelegate((id, result) -> {
          boolean removeFromChatsList = result.get(R.id.btn_removeChatFromList) == R.id.btn_removeChatFromList;
          act.runWithBool(removeFromChatsList);
        });
        context.showSettings(b);
      } else {
        context.showOptions(informationStr, new int[]{checkId, R.id.btn_cancel}, new String[]{Lang.getString(confirmButtonRes), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == checkId) {
            act.runWithBool(false);
          }
          return true;
        });
      }
    } else {
      act.runWithBool(false);
    }
  }

  public void exitToChatScreen (ViewController<?> context, long chatId) {
    NavigationStack stack = context.context().navigation().getStack();
    ViewController<?> current = stack.getCurrent();
    if (stack.size() > 1 && context.getChatId() == chatId && (current == context || (current != null && current.getChatId() == chatId))) {
      for (int i = stack.size() - 2; i >= 1; i--) {
        if (stack.get(i) instanceof ChatsController)
          break;
        stack.destroy(i);
      }
      current.setShareCustomHeaderView(false);
      current.navigateBack();
    }
  }


  // Delete chats

  private @StringRes int getDeleteChatStringRes (long chatId, boolean allowBlock) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null)
      return R.string.DeleteChat;
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        return allowBlock && tdlib.suggestStopBot(chat) ? R.string.DeleteAndStop : R.string.DeleteChat;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        TdApi.BasicGroup basicGroup = tdlib.chatToBasicGroup(chatId);
        TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
        if (tdlib.chatAvailable(chat) && status != null) {
          if (TD.isMember(status, false) || (basicGroup != null && !basicGroup.isActive)) {
            return R.string.LeaveMegaMenu;
          } else {
            return R.string.DeleteChat;
          }
        }
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (tdlib.chatAvailable(chat)) {
          return tdlib.isChannel(chatId) ? R.string.LeaveChannel : R.string.LeaveMegaMenu;
        }
        break;
      }
    }

    return R.string.DeleteChat;
  }

  private void showClearHistoryConfirm (ViewController<?> context, final long chatId, @Nullable Runnable after) {
    showClearHistoryConfirm(context, chatId, after, false);
  }

  private void showClearHistoryConfirm (ViewController<?> context, final long chatId, @Nullable Runnable after, boolean isSecondaryConfirm) {
    if (tdlib.canRevokeChat(chatId) || (tdlib.canClearHistoryForAllUsers(chatId) && tdlib.canClearHistoryOnlyForSelf(chatId))) {
      context.showSettings(new SettingsWrapBuilder(R.id.btn_removeChatFromList)
        .setAllowResize(false)
        .addHeaderItem(tdlib.isSelfChat(chatId) ? Lang.getMarkdownString(context, R.string.ClearSavedMessagesConfirm) : Lang.getString(R.string.ClearHistoryConfirm))
        .setSaveColorId(ColorId.textNegative)
        .setSaveStr(R.string.Delete)
        .setRawItems(new ListItem[] {
          new ListItem(
            ListItem.TYPE_CHECKBOX_OPTION,
            R.id.btn_clearChatHistory,
            0,
            ChatId.isUserChat(chatId) ? Lang.getStringBold(R.string.DeleteSecretChatHistoryForOtherParty, tdlib.cache().userFirstName(tdlib.chatUserId(chatId))) : Lang.getString(R.string.DeleteChatHistoryForAllUsers),
            R.id.btn_clearChatHistory,
            tdlib.canRevokeChat(chatId) && tdlib.isUserChat(chatId) && tdlib.cache().userDeleted(tdlib.chatUserId(chatId))
          )
        })
        .setIntDelegate((id, result) -> {
          boolean clearHistory = result.get(R.id.btn_clearChatHistory) == R.id.btn_clearChatHistory;
          tdlib.client().send(new TdApi.DeleteChatHistory(chatId, false, clearHistory), tdlib.okHandler());
          U.run(after);
        }));
    } else {
      final boolean revoke = !tdlib.canClearHistoryOnlyForSelf(chatId);
      final boolean needSecondaryConfirm;
      final CharSequence info;
      final String confirmButton;
      @DrawableRes int confirmButtonIcon = isSecondaryConfirm ? R.drawable.baseline_delete_forever_24 : R.drawable.templarian_baseline_broom_24;
      if (tdlib.isSelfChat(chatId)) {
        needSecondaryConfirm = true;
        info = Lang.getMarkdownString(context, isSecondaryConfirm ? R.string.ClearSavedMessagesSecondaryConfirm : R.string.ClearSavedMessagesConfirm);
        confirmButton = Lang.getString(isSecondaryConfirm ? R.string.ClearSavedMessages : R.string.ClearHistory);
      } else if (tdlib.isChannel(chatId)) {
        needSecondaryConfirm = true;
        info = Lang.getMarkdownString(context, isSecondaryConfirm ? R.string.ClearChannelSecondaryConfirm : R.string.ClearChannelConfirm);
        confirmButton = Lang.getString(isSecondaryConfirm ? R.string.ClearChannel : R.string.ClearHistoryAll);
      } else {
        needSecondaryConfirm = false;
        info = Lang.getString(revoke ? R.string.ClearHistoryAllConfirm : R.string.ClearHistoryConfirm);
        confirmButton = Lang.getString(revoke ? R.string.ClearHistoryAll : R.string.ClearHistory);
      }
      context.showOptions(
        info,
        new int[]{R.id.btn_clearChatHistory, R.id.btn_cancel},
        new String[]{confirmButton, Lang.getString(R.string.Cancel)},
        new int[]{ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
        new int[]{confirmButtonIcon, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_clearChatHistory) {
            if (needSecondaryConfirm && !isSecondaryConfirm) {
              showClearHistoryConfirm(context, chatId, after, true);
            } else {
              tdlib.client().send(new TdApi.DeleteChatHistory(chatId, false, revoke), tdlib.okHandler());
              U.run(after);
            }
          }
          return true;
        });
    }
  }

  public void showDeleteChatConfirm (final ViewController<?> context, final long chatId) {
    showDeleteChatConfirm(context, chatId, false, tdlib.suggestStopBot(chatId), null);
  }

  private void showDeleteOrClearHistory (final ViewController<?> context, final long chatId, final CharSequence chatName, final Runnable onDelete, final boolean allowClearHistory, final boolean allowBlock, Runnable after) {
    if (!allowClearHistory || !tdlib.canClearHistory(chatId)) {
      onDelete.run();
      return;
    }
    context.showOptions(chatName, new int[] {R.id.btn_removeChatFromList, R.id.btn_clearChatHistory}, new String[] {Lang.getString(getDeleteChatStringRes(chatId, allowBlock)), Lang.getString(R.string.ClearHistory)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.templarian_baseline_broom_24}, (itemView, id) -> {
      if (id == R.id.btn_removeChatFromList) {
        onDelete.run();
      } else if (id == R.id.btn_clearChatHistory) {
        showClearHistoryConfirm(context, chatId, after);
      }
      return true;
    });
  }

  private void showHidePsaConfirm (final ViewController<?> context, final TdApi.ChatList chatList, final long chatId, @Nullable Runnable after) {
    Runnable deleter = () -> {
      tdlib.deleteChat(chatId, false, null);
      exitToChatScreen(context, chatId);
      U.run(after);
    };
    TdApi.ChatSource source = tdlib.chatSource(chatList, chatId);
    if (!(source instanceof TdApi.ChatSourcePublicServiceAnnouncement))
      return;
    context.showOptions(Lang.getPsaHideConfirm((TdApi.ChatSourcePublicServiceAnnouncement) source, tdlib.chatTitle(chatId)),
      new int[] {R.id.btn_delete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.PsaHideDone), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_sweep_24, R.drawable.baseline_cancel_24},
    (optionItemView, id) -> {
      if (id == R.id.btn_delete) {
        deleter.run();
      }
      return true;
    });
  }

  private void showDeleteChatConfirm (final ViewController<?> context, final long chatId, boolean allowClearHistory, boolean blockUser, @Nullable Runnable after) {
    RunnableBool deleter = revoke -> {
      tdlib.deleteChat(chatId, revoke, null);
      UI.post(() -> {
        exitToChatScreen(context, chatId);
        U.run(after);
      });
    };
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        long userId = ChatId.toUserId(chatId);
        String userName = tdlib.cache().userFirstName(userId);
        boolean deleteAndStop = blockUser && tdlib.isBotChat(chatId);
        showDeleteOrClearHistory(context, chatId,
          tdlib.isSelfUserId(userId) ? Lang.getString(R.string.SavedMessages) :
          tdlib.isRepliesChat(ChatId.fromUserId(userId)) ? Lang.getString(R.string.RepliesBot) :
          Lang.getString(R.string.ChatWithUser, userName), () -> {
          final CharSequence info;
          if (tdlib.isSelfUserId(userId)) {
            info = Lang.getMarkdownString(context, R.string.DeleteSavedMessagesConfirm);
          } else if (deleteAndStop) {
            if (tdlib.isRepliesChat(ChatId.fromUserId(userId))) {
              info = Lang.getMarkdownString(context, R.string.DeleteAndStopRepliesConfirm);
            } else {
              info = Lang.getStringBold(R.string.DeleteAndStopBotConfirm, userName);
            }
          } else {
            if (tdlib.isRepliesChat(ChatId.fromUserId(userId))) {
              info = Lang.getMarkdownString(context, R.string.DeleteRepliesConfirm);
            } else {
              info = Lang.getStringBold(R.string.DeleteUserChatConfirm, userName);
            }
          }
          if (tdlib.canRevokeChat(chatId)) {
            context.showSettings(new SettingsWrapBuilder(R.id.btn_removeChatFromList)
              .setAllowResize(false)
              .addHeaderItem(info)
              .setRawItems(new ListItem[]{
                new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_clearChatHistory, 0, Lang.getString(R.string.DeleteSecretChatHistoryForOtherParty, userName), R.id.btn_clearChatHistory, false)
              })
              .setSaveColorId(ColorId.textNegative)
              .setSaveStr(R.string.Delete)
              .setIntDelegate((id, result) -> {
                boolean clearHistory = result.get(R.id.btn_clearChatHistory) == R.id.btn_clearChatHistory;
                if (blockUser) {
                  tdlib.blockSender(new TdApi.MessageSenderUser(userId), new TdApi.BlockListMain(), blockResult -> deleter.runWithBool(clearHistory));
                } else {
                  deleter.runWithBool(clearHistory);
                }
              })
            );
          } else {
            context.showOptions(info, new int[] {R.id.btn_removeChatFromList, R.id.btn_cancel}, new String[] {Lang.getString(deleteAndStop ? R.string.DeleteAndStop : R.string.DeleteChat), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, resultId) -> {
              if (resultId == R.id.btn_removeChatFromList) {
                if (blockUser) {
                  tdlib.blockSender(new TdApi.MessageSenderUser(userId), new TdApi.BlockListMain(), blockResult -> deleter.runWithBool(false));
                } else {
                  deleter.runWithBool(false);
                }
              }
              return true;
            });
          }
        }, allowClearHistory, deleteAndStop, after);
        break;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        String userName = tdlib.cache().userFirstName(tdlib.chatUserId(chatId));
        showDeleteOrClearHistory(context, chatId, Lang.getStringBold(R.string.SecretChatWithUser, userName), () -> {
          TdApi.SecretChat secretChat = tdlib.chatToSecretChat(chatId);
          if (secretChat == null) {
            return;
          }

          if (secretChat.state.getConstructor() == TdApi.SecretChatStateReady.CONSTRUCTOR && tdlib.canClearHistory(chatId) && (context instanceof MessagesController || !tdlib.hasPasscode(chatId))) {
            CharSequence info = Lang.getStringBold(R.string.DeleteSecretChatConfirm, userName);
            context.showSettings(new SettingsWrapBuilder(R.id.btn_removeChatFromList)
              .setAllowResize(false)
              .addHeaderItem(info)
              .setRawItems(new ListItem[] {
                new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_clearChatHistory, 0, Lang.getString(R.string.DeleteSecretChatHistoryForOtherParty, userName), R.id.btn_clearChatHistory, false)
              })
              .setSaveColorId(ColorId.textNegative)
              .setSaveStr(R.string.Delete)
              .setIntDelegate((id, result) -> {
                boolean clearHistory = result.get(R.id.btn_clearChatHistory) == R.id.btn_clearChatHistory;
                if (clearHistory) {
                  tdlib.client().send(new TdApi.DeleteChatHistory(chatId, true, true), object -> {
                    if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
                      Log.e("Cannot clear secret chat history, secretChatId:%d, error: %s", ChatId.toSecretChatId(chatId), TD.toErrorString(object));
                    }
                    deleter.runWithBool(true);
                  });
                } else {
                  deleter.runWithBool(false);
                }
              })
            );
          } else {
            context.showOptions(Lang.getStringBold(secretChat.state.getConstructor() == TdApi.SecretChatStatePending.CONSTRUCTOR ? R.string.DeleteSecretChatPendingConfirm : R.string.DeleteSecretChatClosedConfirm, userName), new int[] {R.id.btn_removeChatFromList, R.id.btn_cancel}, new String[]{Lang.getString(R.string.DeleteChat), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
              if (id == R.id.btn_removeChatFromList) {
                if (blockUser) {
                  tdlib.blockSender(new TdApi.MessageSenderUser(secretChat.userId), new TdApi.BlockListMain(), blockResult -> deleter.runWithBool(false));
                } else {
                  deleter.runWithBool(false);
                }
              }
              return true;
            });
          }
        }, allowClearHistory, blockUser, after);
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          showDeleteOrClearHistory(context, chatId, tdlib.chatTitle(chatId), () -> {
            TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
            if (status != null && TD.isMember(status, false)) {
              leaveJoinChat(context, chatId, false, after);
            } else {
              context.showOptions(Lang.getString(R.string.AreYouSureDeleteThisChat), new int[] {R.id.btn_removeChatFromList, R.id.btn_cancel}, new String[] {Lang.getString(R.string.DeleteChat), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                if (id == R.id.btn_removeChatFromList) {
                  deleter.runWithBool(false);
                }
                return true;
              });
            }
          }, allowClearHistory, blockUser, after);
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        showDeleteOrClearHistory(context, chatId, tdlib.chatTitle(chatId), () -> leaveJoinChat(context, chatId, false, after), allowClearHistory, blockUser, after);
        break;
      }
    }
  }

  public void showArchiveOptions (ViewController<?> context, TdlibChatList archive) {
    boolean needMarkAsRead = tdlib.hasUnreadChats(ChatPosition.CHAT_LIST_ARCHIVE);
    final int size = needMarkAsRead ? 2 : 1;

    IntList ids = new IntList(size);
    StringList strings = new StringList(size);
    IntList icons = new IntList(size);

    if (needMarkAsRead) {
      ids.append(R.id.btn_markChatAsRead);
      strings.append(R.string.ArchiveRead);
      icons.append(Config.ICON_MARK_AS_READ);
    }

    final boolean needHide = tdlib.settings().needHideArchive();

    ids.append(R.id.btn_pinUnpinChat);
    if (needHide) {
      icons.append(R.drawable.deproko_baseline_pin_24);
      strings.append(R.string.ArchivePin);
    } else {
      icons.append(R.drawable.baseline_arrow_upward_24);
      strings.append(R.string.ArchiveHide);
    }

    context.showOptions(Lang.pluralBold(R.string.xArchivedChats, archive.totalCount()), ids.get(), strings.get(), null, icons.get(), (v, optionId) -> {
      if (optionId == R.id.btn_markChatAsRead) {
        tdlib.readAllChats(new TdApi.ChatListArchive(), readCount -> UI.showToast(Lang.plural(R.string.ReadAllChatsDone, readCount), Toast.LENGTH_SHORT));
      } else if (optionId == R.id.btn_pinUnpinChat) {
        tdlib.settings().toggleUserPreference(TdlibSettingsManager.PREFERENCE_HIDE_ARCHIVE);
      }
      return true;
    });
  }

  public void showChatOptions (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, final ThreadInfo messageThread, final TdApi.MessageSource source, boolean canSelect, boolean isSelected, @Nullable Runnable onSelect) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null)
      return;

    boolean available = tdlib.chatAvailable(chat);
    TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList);
    if (!available) {
      if (position != null && position.source instanceof TdApi.ChatSourcePublicServiceAnnouncement) {
        showHidePsaConfirm(context, chatList, chatId, null);
      }
      return;
    }

    final boolean hasNotifications = tdlib.chatNotificationsEnabled(chat);

    final int size = canSelect && onSelect != null ? 8 : 7;

    IntList ids = new IntList(size);
    StringList strings = new StringList(size);
    IntList colors = new IntList(size);
    IntList icons = new IntList(size);

    if (canSelect && onSelect != null) {
      ids.append(R.id.btn_selectChat);
      strings.append(isSelected ? R.string.Unselect : R.string.Select);
      colors.append(ViewController.OptionColor.NORMAL);
      icons.append(R.drawable.baseline_playlist_add_check_24);
    }

    if (!tdlib.isSelfChat(chatId)) {
      ids.append(R.id.btn_notifications);
      strings.append(hasNotifications ? R.string.MuteNotifications : R.string.EnableNotifications);
      colors.append(ViewController.OptionColor.NORMAL);
      icons.append(hasNotifications ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
    }

    if (position != null) {
      ids.append(position.isPinned ? R.id.btn_unpinChat : R.id.btn_pinChat);
      strings.append(position.isPinned ? R.string.UnpinFromTop : R.string.PinToTop);
      colors.append(ViewController.OptionColor.NORMAL);
      icons.append(position.isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
    }

    if (tdlib.canArchiveOrUnarchiveChat(chat)) {
      boolean isArchived = tdlib.chatArchived(chat);
      ids.append(isArchived ? R.id.btn_unarchiveChat : R.id.btn_archiveChat);
      strings.append(isArchived ? R.string.UnarchiveChat : R.string.ArchiveChat);
      colors.append(ViewController.OptionColor.NORMAL);
      icons.append(isArchived ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24);
    }

    if (Settings.instance().chatFoldersEnabled()) {
      if (TD.isChatListMain(chatList) || TD.isChatListArchive(chatList)) {
        ids.append(R.id.btn_addChatToFolder);
        strings.append(R.string.AddToFolder);
        colors.append(ViewController.OptionColor.NORMAL);
        icons.append(R.drawable.templarian_baseline_folder_plus_24);
      } else if (TD.isChatListFolder(chatList)) {
        ids.append(R.id.btn_removeChatFromFolder);
        strings.append(R.string.RemoveFromFolder);
        colors.append(ViewController.OptionColor.NORMAL);
        icons.append(R.drawable.templarian_baseline_folder_remove_24);
      }
    }

    boolean hasPasscode = tdlib.hasPasscode(chat);

    boolean canRead = tdlib.canMarkAsRead(chat);
    if (!canRead || chat.unreadCount == 0 || !hasPasscode) { // when passcode is set, "mark as read" is unavailable, when there are some unread messages
      ids.append(canRead ? R.id.btn_markChatAsRead : R.id.btn_markChatAsUnread);
      strings.append(canRead ? R.string.MarkAsRead : R.string.MarkAsUnread);
      colors.append(ViewController.OptionColor.NORMAL);
      icons.append(canRead ? Config.ICON_MARK_AS_READ : Config.ICON_MARK_AS_UNREAD);
    }

    if (!hasPasscode && tdlib.canClearHistory(chat)) {
      ids.append(R.id.btn_clearChatHistory);
      strings.append(R.string.ClearHistory);
      colors.append(ViewController.OptionColor.NORMAL);
      icons.append(R.drawable.templarian_baseline_broom_24);
    }

    colors.append(ViewController.OptionColor.RED);
    icons.append(R.drawable.baseline_delete_24);
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        strings.append(tdlib.isBotChat(chat) ? R.string.DeleteAndStop : R.string.DeleteChat);
        break;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        strings.append(R.string.DeleteChat);
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
        strings.append(status != null && TD.isMember(status, false) ? R.string.LeaveMegaMenu : R.string.DeleteChat);
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        strings.append(tdlib.isChannel(chatId) ? R.string.LeaveChannel : R.string.LeaveMegaMenu);
        break;
      }
    }
    ids.append(R.id.btn_removeChatFromList);

    CharSequence chatName;
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        String userFirstName = tdlib.cache().userFirstName(tdlib.chatUserId(chat));
        chatName = tdlib.isSelfChat(chatId) ? Lang.getString(R.string.ChatWithYourself) : Lang.getStringBold(R.string.ChatWithUser, userFirstName);
        break;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        String userFirstName = tdlib.cache().userFirstName(tdlib.chatUserId(chat));
        chatName = Lang.getStringBold(R.string.SecretChatWithUser, userFirstName);
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        chatName = chat.title;
        break;
      default:
        throw new IllegalArgumentException();
    }

    context.showOptions(chatName, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_selectChat) {
        onSelect.run();
        return true;
      }
      return processChatAction(context, chatList, chatId, messageThread, source, id, null);
    });
  }

  private void showPinUnpinConfirm (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, TdApi.MessageSource source, @Nullable Runnable after) {
    boolean isPinned = tdlib.chatPinned(chatList, chatId);
    context.showOptions(tdlib.chatTitle(chatId), new int[] {isPinned ? R.id.btn_unpinChat : R.id.btn_pinChat, R.id.btn_cancel}, new String[] {Lang.getString(isPinned ? R.string.UnpinFromTop : R.string.PinToTop), Lang.getString(R.string.Cancel)}, null, new int[] {isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_unpinChat || id == R.id.btn_pinChat) {
        processChatAction(context, chatList, chatId, null, source, id, after);
      }
      return true;
    });
  }

  private void showArchiveUnarchiveChat (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, TdApi.MessageSource source, @Nullable Runnable after) {
    boolean isUnarchive = tdlib.chatArchived(chatId);
    String title = tdlib.chatTitleShort(chatId);
    boolean isUserChat = tdlib.isUserChat(chatId);
    checkNeedArchiveInFolderHint(chatList, isUnarchive, needHint -> {
      CharSequence hint;
      if (needHint) {
        hint = Lang.getStringBold(isUnarchive ?
          (isUserChat ? R.string.UnarchiveXInFolder_user : R.string.UnarchiveXInFolder_chat) :
          (isUserChat ? R.string.ArchiveXInFolder_user : R.string.ArchiveXInFolder_chat),
          title
        );
      } else {
        hint = Lang.getStringBold(isUnarchive ?
          (isUserChat ? R.string.UnarchiveX_user : R.string.UnarchiveX_chat) :
          (isUserChat ? R.string.ArchiveX_user : R.string.ArchiveX_chat),
          title
        );
      }
      context.showOptions(hint, new int[] {isUnarchive ? R.id.btn_unarchiveChat : R.id.btn_archiveChat, R.id.btn_cancel}, new String[] {Lang.getString(isUnarchive ? R.string.UnarchiveChat : R.string.ArchiveChat), Lang.getString(R.string.Cancel)}, null, new int[] {isUnarchive ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_unarchiveChat || id == R.id.btn_archiveChat) {
          processChatAction(context, chatList, chatId, null, source, id, after);
        }
        return true;
      });
    });
  }

  public void showInviteLinkOptionsPreload (ViewController<?> context, final TdApi.ChatInviteLink link, final long chatId, final boolean showNavigatingToLinks, @Nullable Runnable onLinkDeleted, @Nullable RunnableData<TdApi.ChatInviteLinks> onLinkRevoked) {
    context.tdlib().send(new TdApi.GetChatInviteLink(chatId, link.inviteLink), (inviteLink, error) -> {
      context.runOnUiThreadOptional(() -> {
        if (error != null) {
          showInviteLinkOptions(context, link, chatId, showNavigatingToLinks, true, onLinkDeleted, onLinkRevoked);
        } else {
          showInviteLinkOptions(context, inviteLink, chatId, showNavigatingToLinks, false, onLinkDeleted, onLinkRevoked);
        }
      });
    });
  }

  public void showInviteLinkOptions (ViewController<?> context, final TdApi.ChatInviteLink link, final long chatId, final boolean showNavigatingToLinks, final boolean deleted, @Nullable Runnable onLinkDeleted, @Nullable RunnableData<TdApi.ChatInviteLinks> onLinkRevoked) {
    TdApi.Chat chat = tdlib.chat(chatId);

    StringList strings = new StringList(6);
    IntList icons = new IntList(6);
    IntList ids = new IntList(6);
    IntList colors = new IntList(6);

    if (!deleted && link.memberCount > 0) {
      ids.append(R.id.btn_viewInviteLinkMembers);
      strings.append(R.string.InviteLinkViewMembers);
      icons.append(R.drawable.baseline_visibility_24);
      colors.append(ViewController.OptionColor.NORMAL);
    }

    if (!deleted && link.createsJoinRequest && link.pendingJoinRequestCount > 0) {
      ids.append(R.id.btn_manageJoinRequests);
      strings.append(R.string.InviteLinkViewRequests);
      icons.append(R.drawable.baseline_pending_24);
      colors.append(ViewController.OptionColor.NORMAL);
    }

    if (showNavigatingToLinks && tdlib.canManageInviteLinks(chat)) {
      ids.append(R.id.btn_manageInviteLinks);
      strings.append(R.string.InviteLinkManage);
      icons.append(R.drawable.baseline_add_link_24);
      colors.append(ViewController.OptionColor.NORMAL);
    }

    if (!deleted && !link.isRevoked) {
      if (!link.isPrimary && context instanceof ChatLinksController) {
        ids.append(R.id.btn_edit);
        strings.append(R.string.InviteLinkEdit);
        icons.append(R.drawable.baseline_edit_24);
        colors.append(ViewController.OptionColor.NORMAL);
      }

      ids.append(R.id.btn_copyLink);
      strings.append(R.string.InviteLinkCopy);
      icons.append(R.drawable.baseline_content_copy_24);
      colors.append(ViewController.OptionColor.NORMAL);

      ids.append(R.id.btn_shareLink);
      strings.append(R.string.ShareLink);
      icons.append(R.drawable.baseline_forward_24);
      colors.append(ViewController.OptionColor.NORMAL);

      icons.append(R.drawable.baseline_link_off_24);
      ids.append(R.id.btn_revokeLink);
      strings.append(R.string.RevokeLink);
      colors.append(ViewController.OptionColor.RED);
    } else {
      ids.append(R.id.btn_copyLink);
      strings.append(R.string.InviteLinkCopy);
      icons.append(R.drawable.baseline_content_copy_24);
      colors.append(ViewController.OptionColor.NORMAL);

      if (!deleted) {
        icons.append(R.drawable.baseline_delete_24);
        ids.append(R.id.btn_deleteLink);
        strings.append(R.string.InviteLinkDelete);
        colors.append(ViewController.OptionColor.RED);
      }
    }

    CharSequence info = TD.makeClickable(Lang.getString(R.string.CreatedByXOnDate, ((target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newUserSpan(new TdlibContext(context.context(), context.tdlib()), link.creatorUserId) : null), context.tdlib().cache().userName(link.creatorUserId), Lang.getRelativeTimestamp(link.date, TimeUnit.SECONDS)));
    Lang.SpanCreator firstBoldCreator = (target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newBoldSpan(needFakeBold) : null;
    CharSequence desc;

    if (link.name != null && !link.name.isEmpty()) {
      desc = Lang.getString(R.string.format_nameAndSubtitleAndStatus, firstBoldCreator, link.inviteLink, link.name, info);
    } else {
      desc = Lang.getString(R.string.format_nameAndStatus, firstBoldCreator, link.inviteLink, info);
    }

    context.showOptions(desc, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_viewInviteLinkMembers) {
        ChatLinkMembersController c2 = new ChatLinkMembersController(context.context(), context.tdlib());
        c2.setArguments(new ChatLinkMembersController.Args(chatId, link.inviteLink));
        context.navigateTo(c2);
      } else if (id == R.id.btn_manageJoinRequests) {
        ChatJoinRequestsController c3 = new ChatJoinRequestsController(context.context(), context.tdlib());
        c3.setArguments(new ChatJoinRequestsController.Args(chatId, link.inviteLink, context));
        context.navigateTo(c3);
      } else if (id == R.id.btn_edit) {
        EditChatLinkController c = new EditChatLinkController(context.context(), context.tdlib());
        c.setArguments(new EditChatLinkController.Args(link, chatId, (ChatLinksController) context));
        context.navigateTo(c);
      } else if (id == R.id.btn_manageInviteLinks) {
        ChatLinksController cc = new ChatLinksController(context.context(), context.tdlib());
        cc.setArguments(new ChatLinksController.Args(chatId, context.tdlib().myUserId(), null, null, tdlib.chatStatus(chatId).getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR));
        context.navigateTo(cc);
      } else if (id == R.id.btn_copyLink) {
        UI.copyText(link.inviteLink, R.string.CopiedLink);
      } else if (id == R.id.btn_shareLink) {
        String chatName = context.tdlib().chatTitle(chatId);
        String exportText = Lang.getString(context.tdlib().isChannel(chatId) ? R.string.ShareTextChannelLink : R.string.ShareTextChatLink, chatName, link.inviteLink);
        String text = Lang.getString(R.string.ShareTextLink, chatName, link.inviteLink);
        ShareController sc = new ShareController(context.context(), context.tdlib());
        sc.setArguments(new ShareController.Args(text).setShare(exportText, null));
        sc.show();
      } else if (id == R.id.btn_deleteLink) {
        context.showOptions(Lang.getString(R.string.AreYouSureDeleteInviteLink), new int[] {R.id.btn_deleteLink, R.id.btn_cancel}, new String[] {Lang.getString(R.string.InviteLinkDelete), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
          if (id2 == R.id.btn_deleteLink) {
            if (onLinkDeleted != null) onLinkDeleted.run();
            context.tdlib().client().send(new TdApi.DeleteRevokedChatInviteLink(chatId, link.inviteLink), tdlib.okHandler());
          }

          return true;
        });
      } else if (id == R.id.btn_revokeLink) {
        context.showOptions(Lang.getString(context.tdlib().isChannel(chatId) ? R.string.AreYouSureRevokeInviteLinkChannel : R.string.AreYouSureRevokeInviteLinkGroup), new int[] {R.id.btn_revokeLink, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RevokeLink), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_link_off_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
          if (id2 == R.id.btn_revokeLink) {
            context.tdlib().client().send(new TdApi.RevokeChatInviteLink(chatId, link.inviteLink), result -> {
              if (result.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR && onLinkRevoked != null) {
                context.runOnUiThreadOptional(() -> onLinkRevoked.runWithData((TdApi.ChatInviteLinks) result));
              }
            });
          }

          return true;
        });
      }

      return true;
    });
  }

  public void showAddChatToFolderOptions (ViewController<?> context, long chatId, @Nullable Runnable after) {
    showAddChatsToFolderOptions(context, new long[] {chatId}, after);
  }

  public void showAddChatsToFolderOptions (ViewController<?> context, long[] chatIds, @Nullable Runnable after) {
    if (chatIds.length == 0)
      return;

    TdApi.ChatFolderInfo[] chatFolders = tdlib.chatFolders();
    List<ListItem> items = new ArrayList<>(chatFolders.length + 1);
    for (TdApi.ChatFolderInfo chatFolderInfo : chatFolders) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.chatFolder, TD.findFolderIcon(chatFolderInfo.icon, R.drawable.baseline_folder_24), chatFolderInfo.title).setIntValue(chatFolderInfo.id));
    }
    if (tdlib.canCreateChatFolder()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createNewFolder, R.drawable.baseline_create_new_folder_24, R.string.CreateNewFolder).setTextColorId(ColorId.textNeutral));
    }
    SettingsWrap[] settings = new SettingsWrap[1];
    settings[0] = context.showSettings(new SettingsWrapBuilder(R.id.btn_addChatToFolder)
      .addHeaderItem(Lang.getString(R.string.ChooseFolder))
      .setRawItems(items)
      .setNeedSeparators(false)
      .setDisableFooter(true)
      .setNeedRootInsets(true)
      .setSettingProcessor((item, view, isUpdate) -> {
        view.setIconColorId(item.getId() == R.id.btn_createNewFolder ? ColorId.inlineIcon : ColorId.NONE);
      })
      .setOnSettingItemClick((view, id, item, done, adapter, window) -> {
        settings[0].window.hideWindow(true);
        if (item.getId() == R.id.btn_createNewFolder) {
          TdApi.ChatFolder chatFolder = TD.newChatFolder(chatIds);
          context.context().navigation().navigateTo(EditChatFolderController.newFolder(context.context(), tdlib, chatFolder));
        } else {
          int chatFolderId = item.getIntValue();
          addChatsToChatFolder(context, chatFolderId, chatIds);
        }
        if (after != null) {
          after.run();
        }
      }));
  }

  public void showDeleteChatFolderOrLeaveChats (ViewController<?> context, int chatFolderId) {
    TdApi.ChatFolderInfo info = tdlib.chatFolderInfo(chatFolderId);
    if (info.isShareable) {
      tdlib.send(new TdApi.GetChatFolderChatsToLeave(chatFolderId), (result, error) -> post(() -> {
        if (error != null) {
          UI.showError(error);
        } else if (result.totalCount > 0) {
          ChatFolderInviteLinkController controller = new ChatFolderInviteLinkController(context.context(), tdlib);
          controller.setArguments(ChatFolderInviteLinkController.Arguments.deleteFolder(info, result.chatIds));
          controller.show();
        } else {
          showDeleteChatFolderConfirm(context, chatFolderId, info.hasMyInviteLinks);
        }
      }));
    } else {
      showDeleteChatFolderConfirm(context, chatFolderId, info.hasMyInviteLinks);
    }
  }

  private void showDeleteChatFolderConfirm (ViewController<?> context, int chatFolderId, boolean hasMyInviteLinks) {
    tdlib.ui().showDeleteChatFolderConfirm(context, hasMyInviteLinks, () -> {
      tdlib.deleteChatFolder(chatFolderId, null, null);
    });
  }

  public void showDeleteChatFolderConfirm (ViewController<?> context, boolean hasMyInviteLinks, Runnable after) {
    // TODO(nikita-toropov) wording
    int infoRes = hasMyInviteLinks ? R.string.DeleteFolderWithInviteLinksConfirm : R.string.RemoveFolderConfirm;
    int actionRes = hasMyInviteLinks ? R.string.Delete : R.string.Remove;
    context.showConfirm(Lang.getMarkdownString(context, infoRes), Lang.getString(actionRes), R.drawable.baseline_delete_24, ViewController.OptionColor.RED, after);
  }

  public void addChatsToChatFolder (TdlibDelegate delegate, int chatFolderId, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> {
      if (error != null) {
        UI.showError(chatFolder);
      } else {
        addChatsToChatFolderImpl(delegate, chatFolderId, chatFolder, chatIds);
      }
    });
  }

  public void addChatsToChatFolderImpl (TdlibDelegate delegate, int chatFolderId, TdApi.ChatFolder chatFolder, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    LongSet pinnedChatIds = new LongSet(chatFolder.pinnedChatIds);
    LongSet includedChatIds = new LongSet(chatFolder.includedChatIds);
    for (long chatId : chatIds) {
      if (pinnedChatIds.has(chatId) || includedChatIds.has(chatId)) {
        continue;
      }
      includedChatIds.add(chatId);
    }
    if (includedChatIds.size() == chatFolder.includedChatIds.length) {
      return;
    }
    int chatCount = pinnedChatIds.size() + includedChatIds.size();
    int secretChatCount = 0;
    for (long pinnedChatId : pinnedChatIds) {
      if (ChatId.isSecret(pinnedChatId)) secretChatCount++;
    }
    for (long includedChatId : includedChatIds) {
      if (ChatId.isSecret(includedChatId)) secretChatCount++;
    }
    int nonSecretChatCount = chatCount - secretChatCount;
    long chosenChatCountMax = tdlib.chatFolderChosenChatCountMax();
    if (secretChatCount > chosenChatCountMax || nonSecretChatCount > chosenChatCountMax) {
      checkPremiumLimit(new TdApi.PremiumLimitTypeChatFolderChosenChatCount(), (currentLimit, premiumLimit) -> {
        // FIXME: use tdlib.ui().showPremiumAlert()?
        CharSequence text;
        if (currentLimit < premiumLimit) {
          text = Lang.getMarkdownPlural(delegate, R.string.PremiumLimitChatsInFolder, currentLimit, Lang.boldCreator(), Strings.buildCounter(premiumLimit));
        } else {
          text = Lang.getMarkdownPlural(delegate, R.string.LimitChatsInFolder, currentLimit, Lang.boldCreator());
        }
        UI.showCustomToast(text, Toast.LENGTH_LONG, 0);
      });
      return;
    }
    chatFolder.includedChatIds = includedChatIds.toArray();
    chatFolder.excludedChatIds = ArrayUtils.removeAll(chatFolder.excludedChatIds, chatIds);
    tdlib.send(new TdApi.EditChatFolder(chatFolderId, chatFolder), (chatFolderInfo, error) -> {
      if (error != null) {
        UI.showError(error);
      }
    });
  }

  public void showArchiveHint (TdApi.ChatList chatList, int chatsCount, boolean isUnarchive) {
    if (chatList.getConstructor() != TdApi.ChatListFolder.CONSTRUCTOR) return;
    UI.showToast(Lang.pluralBold(isUnarchive ? R.string.UnarchivedXChats : R.string.ArchivedXChats, chatsCount), Toast.LENGTH_SHORT);
  }

  public void checkNeedArchiveInFolderHint (TdApi.ChatList chatList, boolean isUnarchive, RunnableBool after) {
    if (chatList.getConstructor() != TdApi.ChatListFolder.CONSTRUCTOR) {
      after.runWithBool(false);
      return;
    }
    if (isUnarchive) {
      after.runWithBool(true);
      return;
    }
    tdlib.send(new TdApi.GetChatFolder(((TdApi.ChatListFolder) chatList).chatFolderId), (chatFolder, error) -> {
      if (chatFolder != null) {
        post(() -> {
          after.runWithBool(!chatFolder.excludeArchived);
        });
      }
    });
  }

  public boolean processChatAction (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, final @Nullable ThreadInfo messageThread, final TdApi.MessageSource source, final int actionId, @Nullable Runnable after) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null)
      return false;
    if (actionId == R.id.btn_notifications) {
      tdlib.ui().toggleMute(context, chatId, false, after);
      return true;
    } else if (actionId == R.id.btn_pinUnpinChat) {
      showPinUnpinConfirm(context, chatList, chatId, source, after);
      return true;
    } else if (actionId == R.id.btn_unpinChat) {
      tdlib.client().send(new TdApi.ToggleChatIsPinned(chatList, chatId, false), tdlib.okHandler(after));
      return true;
    } else if (actionId == R.id.btn_pinChat) {
      tdlib.client().send(new TdApi.ToggleChatIsPinned(chatList, chatId, true), tdlib.okHandler(after));
      return true;
    } else if (actionId == R.id.btn_archiveUnarchiveChat) {
      showArchiveUnarchiveChat(context, chatList, chatId, source, after);
      return true;
    } else if (actionId == R.id.btn_archiveChat || actionId == R.id.btn_unarchiveChat) {
      boolean isUnarchive = actionId == R.id.btn_unarchiveChat;
      TdApi.ChatList targetChatList = isUnarchive ? ChatPosition.CHAT_LIST_MAIN : ChatPosition.CHAT_LIST_ARCHIVE;
      tdlib.send(new TdApi.AddChatToList(chatId, targetChatList), tdlib.typedOkHandler(() -> {
        showArchiveHint(chatList, 1, isUnarchive);
        if (after != null) {
          after.run();
        }
      }));
      return true;
    } else if (actionId == R.id.btn_markChatAsRead) {
      if (messageThread != null) {
        tdlib.markChatAsRead(messageThread.getChatId(), source, false, after);
      } else {
        tdlib.markChatAsRead(chat.id, source, true, after);
      }
      return true;
    } else if (actionId == R.id.btn_markChatAsUnread) {
      tdlib.markChatAsUnread(chat, after);
      return true;
    } else if (actionId == R.id.btn_phone_call) {
      tdlib.context().calls().makeCallDelayed(context, TD.getUserId(chat), null, true);
      return true;
    } else if (actionId == R.id.btn_addChatToFolder) {
      showAddChatToFolderOptions(context, chatId, /* after */ null);
      return true;
    } else if (actionId == R.id.btn_removeChatFromFolder) {
      if (TD.isChatListFolder(chatList)) {
        int chatFolderId = ((TdApi.ChatListFolder) chatList).chatFolderId;
        removeChatFromChatFolder(chatFolderId, chatId);
      }
      return true;
    }
    return processLeaveButton(context, chatList, chatId, actionId, after);
  }

  public void removeChatFromChatFolder (int chatFolderId, long chatId) {
    removeChatsFromChatFolder(chatFolderId, new long[] {chatId});
  }

  public void removeChatsFromChatFolder (int chatFolderId, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        removeChatsFromChatFolderImpl(chatFolderId, chatFolder, chatIds);
      }
    });
  }

  private void removeChatsFromChatFolderImpl (int chatFolderId, TdApi.ChatFolder chatFolder, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    LongList pinnedChatIds = new LongList(chatFolder.pinnedChatIds);
    LongSet includedChatIds = new LongSet(chatFolder.includedChatIds);
    LongSet excludedChatIds = new LongSet(chatFolder.excludedChatIds);
    for (long chatId : chatIds) {
      boolean removed = pinnedChatIds.remove(chatId) | includedChatIds.remove(chatId);
      if (removed && Config.CHAT_FOLDERS_SMART_CHAT_DELETION_ENABLED) {
        TdApi.Chat chat = tdlib.chat(chatId);
        boolean isBotChat = tdlib.isBotChat(chat);
        boolean isUserChat = tdlib.isUserChat(chat) && !isBotChat;
        boolean isContactChat = isUserChat && tdlib.isContactChat(chat);
        if (!chatFolder.includeContacts && isUserChat && isContactChat) continue;
        if (!chatFolder.includeNonContacts && isUserChat && !isContactChat) continue;
        if (!chatFolder.includeGroups && TD.isMultiChat(chat)) continue;
        if (!chatFolder.includeChannels && tdlib.isChannelChat(chat)) continue;
        if (!chatFolder.includeBots && isBotChat) continue;
      }
      excludedChatIds.add(chatId);
    }
    chatFolder.pinnedChatIds = pinnedChatIds.get();
    chatFolder.includedChatIds = includedChatIds.toArray();
    chatFolder.excludedChatIds = excludedChatIds.toArray();
    tdlib.send(new TdApi.EditChatFolder(chatFolderId, chatFolder), (chatFolderInfo, error) -> {
      if (error != null) {
        UI.showError(error);
      }
    });
  }

  public final ForceTouchView.ActionListener createSimpleChatActions (final ViewController<?> context, final TdApi.ChatList chatList, final long chatId, final @Nullable ThreadInfo messageThread, final TdApi.MessageSource source, IntList ids, IntList icons, StringList strings, final boolean allowInteractions, final boolean canSelect, final boolean isSelected, @Nullable Runnable onSelect) {
    final TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      return null;
    }
    final TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList);
    final boolean hasSelect = canSelect && onSelect != null;
    if (allowInteractions) {
      if (tdlib.chatAvailable(chat)) {
        long userId = TD.getUserId(chat);
        if (!tdlib.isSelfUserId(userId)) {
          if (userId != 0) {
            if (Config.CALL_FROM_PREVIEW && tdlib.cache().userGeneral(userId)) {
              ids.append(R.id.btn_phone_call);
              strings.append(R.string.Call);
              icons.append(R.drawable.baseline_call_24);
            }
          }
          final boolean hasNotifications = tdlib.chatNotificationsEnabled(chat.id);
          ids.append(R.id.btn_notifications);
          strings.append(hasNotifications ? R.string.Mute : R.string.Unmute);
          icons.append(hasNotifications ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
        }

        if (!hasSelect && position != null) {
          ids.append(R.id.btn_pinUnpinChat);
          strings.append(position.isPinned ? R.string.Unpin : R.string.Pin);
          icons.append(position.isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
        }

        boolean canRead = tdlib.canMarkAsRead(chat);
        ids.append(canRead ? R.id.btn_markChatAsRead : R.id.btn_markChatAsUnread);
        strings.append(canRead ? R.string.MarkAsRead : R.string.MarkAsUnread);
        icons.append(canRead ? Config.ICON_MARK_AS_READ : Config.ICON_MARK_AS_UNREAD);

        if (tdlib.canArchiveOrUnarchiveChat(chat)) {
          boolean isArchived = tdlib.chatArchived(chat);
          ids.append(R.id.btn_archiveUnarchiveChat);
          strings.append(isArchived ? R.string.Unarchive : R.string.Archive);
          icons.append(isArchived ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24);
        }

        ids.append(R.id.btn_removeChatFromListOrClearHistory);
        strings.append(R.string.Delete);
        icons.append(R.drawable.baseline_delete_24);
      } else if (position != null && position.source instanceof TdApi.ChatSourcePublicServiceAnnouncement) {
        ids.append(R.id.btn_removePsaChatFromList);
        strings.append(R.string.PsaHide);
        icons.append(R.drawable.baseline_delete_sweep_24);
      }
    }
    if (hasSelect) {
      ids.append(R.id.btn_selectChat);
      if (ids.size() > 1) {
        strings.append(R.string.MoreChatOptions);
        icons.append(R.drawable.baseline_more_horiz_24);
      } else {
        strings.append(isSelected ? R.string.Unselect : R.string.Select);
        icons.append(R.drawable.baseline_playlist_add_check_24);
      }
    }

    return new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext c, int actionId, Object arg) {
        if (actionId == R.id.btn_selectChat) {
          onSelect.run();
        } else {
          processChatAction(context, chatList, chatId, messageThread, source, actionId, null);
        }
      }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext c, int actionId, Object arg) { }
    };
  }

  // Passcode

  private void showPasscodeOptions (ViewController<?> controller, long chatId) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null || !tdlib.canSetPasscode(chat)) {
      return;
    }
    PasscodeSetupController c = new PasscodeSetupController(controller.context(), controller.tdlib());
    c.setArguments(new PasscodeSetupController.Args(chat, tdlib.chatPasscode(chat)));
    controller.navigateTo(c);
  }

  // Map provider settings

  private PopupLayout currentMapProviderWrap;
  private List<Runnable> currentMapProviderCallbacks;

  public static final int MAP_PROVIDER_MODE_SECRET_TUTORIAL = 0;
  public static final int MAP_PROVIDER_MODE_SECRET = 1;
  public static final int MAP_PROVIDER_MODE_CLOUD = 2;

  public void showMapProviderSettings (ViewController<?> c, int mode, Runnable after) {
    if (!c.isFocused()) {
      c.addFocusListener(new ViewController.FocusStateListener() {
        @Override
        public void onFocusStateChanged (ViewController<?> c, boolean isFocused) {
          if (isFocused) {
            c.removeFocusListener(this);
            showMapProviderSettings(c, mode, after);
          }
        }
      });
      return;
    }
    if (mode == MAP_PROVIDER_MODE_SECRET_TUTORIAL) {
      if (currentMapProviderWrap != null && currentMapProviderWrap.getContext() == c.context()) {
        if (after != null) {
          currentMapProviderCallbacks.add(after);
        }
        return;
      }
    }
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_mapProvider);
    if (mode == MAP_PROVIDER_MODE_SECRET_TUTORIAL) {
      b.addHeaderItem(Lang.getString(R.string.MapPreviewProviderHint));
      b.setDismissListener(popupLayout -> {
        if (currentMapProviderWrap != null) {
          currentMapProviderWrap = null;
          currentMapProviderCallbacks.clear();
          currentMapProviderCallbacks = null;
        }
      });
    }
    int type = Settings.instance().getMapProviderType(mode == MAP_PROVIDER_MODE_CLOUD);
    if (mode == MAP_PROVIDER_MODE_CLOUD)  {
      b.setRawItems(new ListItem[] {
        // new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_mapProviderGoogle, 0, R.string.MapPreviewProviderGoogle, R.id.btn_mapProvider, type == Settings.MAP_PROVIDER_GOOGLE),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_mapProviderTelegram, 0, R.string.MapPreviewProviderTelegram, R.id.btn_mapProvider, type == Settings.MAP_PROVIDER_TELEGRAM),
      });
    } else {
      b.setRawItems(new ListItem[] {
        // new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_mapProviderGoogle, 0, R.string.MapPreviewProviderGoogle, R.id.btn_mapProvider, type == Settings.MAP_PROVIDER_GOOGLE),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_mapProviderTelegram, 0, R.string.MapPreviewProviderTelegram, R.id.btn_mapProvider, type == Settings.MAP_PROVIDER_TELEGRAM || (type == Settings.MAP_PROVIDER_UNSET && mode == MAP_PROVIDER_MODE_SECRET_TUTORIAL)),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_mapProviderNone, 0, R.string.MapPreviewProviderNone, R.id.btn_mapProvider, type == Settings.MAP_PROVIDER_NONE),
      });
    }
    SettingsWrap wrap = c.showSettings(b.setSaveStr(R.string.Save).setIntDelegate((id, result) -> {
      int resultType;
      @IdRes int resultId = result.get(R.id.btn_mapProvider);
      if (resultId == R.id.btn_mapProviderGoogle) {
        resultType = Settings.MAP_PROVIDER_GOOGLE;
      } else if (resultId == R.id.btn_mapProviderTelegram) {
        resultType = Settings.MAP_PROVIDER_TELEGRAM;
      } else if (resultId == R.id.btn_mapProviderNone) {
        resultType = Settings.MAP_PROVIDER_NONE;
      } else {
        return;
      }
      Settings.instance().setMapProviderType(resultType, mode == MAP_PROVIDER_MODE_CLOUD);
      if (mode == MAP_PROVIDER_MODE_SECRET_TUTORIAL && currentMapProviderWrap != null) {
        List<Runnable> callbacks = currentMapProviderCallbacks;
        currentMapProviderWrap = null;
        currentMapProviderCallbacks = null;
        for (Runnable runnable : callbacks) {
          runnable.run();
        }
      } else if (after != null) {
        after.run();
      }
    }));
    if (mode == MAP_PROVIDER_MODE_SECRET_TUTORIAL) {
      if (wrap != null) {
        currentMapProviderWrap = wrap.window;
        currentMapProviderCallbacks = new ArrayList<>();
        currentMapProviderCallbacks.add(after);
      } else {
        currentMapProviderWrap = null;
        currentMapProviderCallbacks = null;
      }
    }
  }

  public static class CustomLangPackResult {
    public String[][] builtinStrings;
    public final Map<String, TdApi.LanguagePackStringValue> strings = new HashMap<>();
    public int unknownStringsCount;
    public final String fileName;

    public CustomLangPackResult (TdApi.Document document) {
      this.fileName = document.fileName;
    }

    public void prepare () {
      builtinStrings = LangUtils.getAllKeys();
    }

    public boolean canBeInstalled () {
      return !strings.isEmpty() && unknownStringsCount < strings.size() && hasRequiredStrings();
    }

    public boolean hasRequiredStrings () {
      int[] requiredStrings = Lang.getRequiredKeys();
      boolean hasRequiredStrings = true;
      for (int resId : requiredStrings) {
        TdApi.LanguagePackStringValue string = getString(resId);
        if (!(string instanceof TdApi.LanguagePackStringValueOrdinary)) {
          hasRequiredStrings = false;
          Log.e("Language Pack is missing required string: %s", Lang.getResourceEntryName(resId));
          continue;
        }
        TdApi.LanguagePackStringValueOrdinary value = (TdApi.LanguagePackStringValueOrdinary) string;
        if (StringUtils.isEmpty(value.value.trim())) {
          hasRequiredStrings = false;
          Log.e("Language Pack required string is empty: %s", Lang.getResourceEntryName(resId));
          continue;
        }
        if (resId == R.string.language_code && value.value.charAt(0) == 'X') {
          hasRequiredStrings = false;
          Log.e("Language Pack language_code starts with 'X': %s", value.value);
          continue;
        }
      }
      return hasRequiredStrings;
    }

    public TdApi.LanguagePackStringValue getString (@StringRes int resId) {
      String key = Lang.getResourceEntryName(resId);
      return strings.get(key);
    }

    public String getStringValue (@StringRes int resId) {
      String key = Lang.getResourceEntryName(resId);
      TdApi.LanguagePackStringValue string = strings.get(key);
      return string instanceof TdApi.LanguagePackStringValueOrdinary ? ((TdApi.LanguagePackStringValueOrdinary) string).value : null;
    }

    public String getLanguageCode () {
      String code = getStringValue(R.string.language_code);
      return "X" + code;
    }

    private String sourceName;

    public CustomLangPackResult setSourceName (String sourceName) {
      this.sourceName = sourceName;
      return this;
    }

    public String getLanguageNameInEnglish () {
      String name = getStringValue(R.string.language_nameInEnglish);
      if (StringUtils.isEmpty(fileName))
        return name;
      StringBuilder b = new StringBuilder(name).append(" [");
      if (fileName.endsWith(".xml"))
        b.append(fileName, 0, fileName.length() - ".xml".length());
      else
        b.append(fileName);
      /*if (!Strings.isEmpty(sourceName))
        b.append(", source: ").append(sourceName);*/
      return b.append("]").toString();
    }

    public TdApi.LanguagePackString[] getStrings () {
      TdApi.LanguagePackString[] strings = new TdApi.LanguagePackString[this.strings.size()];
      int i = 0;
      for (Map.Entry<String, TdApi.LanguagePackStringValue> string : this.strings.entrySet()) {
        strings[i++] = new TdApi.LanguagePackString(string.getKey(), string.getValue());
      }
      return strings;
    }

    public int getStringsCount () {
      return strings.size() - unknownStringsCount;
    }

    public int getBuiltinStringsCount () {
      return builtinStrings[0].length + builtinStrings[1].length;
    }

    public int getCompletenessPercentage () {
      return (int) Math.floor((float) getStringsCount() / (float) getBuiltinStringsCount() * 100f);
    }

    public int getMissingStringsCount () {
      int missingCount = getBuiltinStringsCount() - getStringsCount();
      if (missingCount > 0) {
        List<String> missingStrings = new ArrayList<>();
        for (String key : builtinStrings[0]) {
          if (strings.get(key) == null) {
            missingStrings.add(key);
          }
        }
        for (String key : builtinStrings[1]) {
          if (strings.get(key) == null) {
            missingStrings.add(key);
          }
        }
        if (!missingStrings.isEmpty()) {
          Log.e("Language pack %s misses following strings: %s", fileName, TextUtils.join(", ", missingStrings));
        }
      }
      return missingCount;
    }

    public boolean isComplete () {
      return getStringsCount() == getBuiltinStringsCount();
    }
  }

  public void readCustomLanguage (ViewController<?> c, TdApi.Document document, @NonNull RunnableData<CustomLangPackResult> onDone, @Nullable Runnable onError) {
    if (!canInstallLanguage(document)) {
      if (onError != null) {
        onError.run();
      }
      return;
    }
    Background.instance().post(() -> {
      try (FileInputStream is = new FileInputStream(document.document.local.path)) {
        XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = parserFactory.newPullParser();
        parser.setInput(is, "UTF-8");
        CustomLangPackResult out = new CustomLangPackResult(document);
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
          if (parser.getEventType() != XmlPullParser.START_TAG)
            continue;
          String name = parser.getName();
          if ("resources".equals(name)) {
            readStrings(parser, out);
          } else {
            throw new IllegalArgumentException("Unknown tag: " + name); // skip(parser);
          }
        }
        if (out.canBeInstalled()) {
          out.prepare();
          tdlib.ui().post(() -> onDone.runWithData(out));
          return;
        }
      } catch (Throwable t) {
        Log.e("Cannot install custom language", t);
      }
      tdlib.ui().post(onError != null ? onError : () -> UI.showToast(R.string.InvalidLocalisation, Toast.LENGTH_SHORT));
    });
  }

  private static void readStrings (XmlPullParser parser, CustomLangPackResult out) throws XmlPullParserException, IOException {
    parser.require(XmlPullParser.START_TAG, null, "resources");
    while (parser.next() != XmlPullParser.END_TAG) {
      if (parser.getEventType() != XmlPullParser.START_TAG)
        continue;
      String name = parser.getName();
      if ("string".equals(name)) {
        readString(parser, out);
      } else {
        throw new IllegalArgumentException("Unknown tag: " + name); // skip(parser);
      }
    }
  }

  private static void readString (XmlPullParser parser, CustomLangPackResult out) throws XmlPullParserException, IOException {
    parser.require(XmlPullParser.START_TAG, null, "string");
    String key = parser.getAttributeValue(null, "name").trim();
    if (StringUtils.isEmpty(key))
      throw new IllegalArgumentException();
    StringBuilder b = new StringBuilder();
    while (parser.next() != XmlPullParser.END_TAG) {
      switch (parser.getEventType()) {
        case XmlPullParser.START_TAG:
          skip(parser);
          break;
        case XmlPullParser.TEXT:
          b.append(parser.getText());
          break;
        case XmlPullParser.ENTITY_REF:
          b.append(parser.getText());
          break;
        default:
          throw new IllegalArgumentException("eventType == " + parser.getEventType());
      }
    }
    String value = Strings.unwrap(key, b.toString());
    int i = key.lastIndexOf('_');
    if (i == -1) {
      out.strings.put(key, new TdApi.LanguagePackStringValueOrdinary(value));
    } else {
      String suffix = key.substring(i + 1);
      TdApi.LanguagePackStringValuePluralized pluralized;
      switch (suffix) {
        case "zero":
          pluralized = getPlural(key, suffix, out.strings);
          pluralized.zeroValue = value;
          break;
        case "one":
          pluralized = getPlural(key, suffix, out.strings);
          pluralized.oneValue = value;
          break;
        case "two":
          pluralized = getPlural(key, suffix, out.strings);
          pluralized.twoValue = value;
          break;
        case "few":
          pluralized = getPlural(key, suffix, out.strings);
          pluralized.fewValue = value;
          break;
        case "many":
          pluralized = getPlural(key, suffix, out.strings);
          pluralized.manyValue = value;
          break;
        case "other":
          pluralized = getPlural(key, suffix, out.strings);
          pluralized.otherValue = value;
          break;
        default:
          out.strings.put(key, new TdApi.LanguagePackStringValueOrdinary(value));
          break;
      }
    }
    int resourceId = Lang.getStringResourceIdentifier(key);
    if (resourceId == 0) {
      out.unknownStringsCount++;
    }
  }

  private static TdApi.LanguagePackStringValuePluralized getPlural (String key, String suffix, Map<String, TdApi.LanguagePackStringValue> map) {
    key = key.substring(0, key.length() - suffix.length() - 1);
    TdApi.LanguagePackStringValue string = map.get(key);
    if (string instanceof TdApi.LanguagePackStringValuePluralized) {
      return (TdApi.LanguagePackStringValuePluralized) string;
    }
    TdApi.LanguagePackStringValuePluralized pluralized = new TdApi.LanguagePackStringValuePluralized();
    map.put(key, pluralized);
    return pluralized;
  }

  private static void skip (XmlPullParser parser) throws XmlPullParserException, IOException {
    if (parser.getEventType() != XmlPullParser.START_TAG) {
      throw new IllegalStateException();
    }
    int depth = 1;
    while (depth != 0) {
      switch (parser.next()) {
        case XmlPullParser.END_TAG:
          depth--;
          break;
        case XmlPullParser.START_TAG:
          depth++;
          break;
      }
    }
  }

  public static boolean canInstallLanguage (TdApi.Document document) {
    if ((!StringUtils.isEmpty(document.fileName) && document.fileName.endsWith(".xml")) || (!StringUtils.isEmpty(document.mimeType) && document.mimeType.equals("application/xml"))) {
      // TODO quick check if file starts with <resources>?
      return document.document.size <= ByteUnit.MIB.toBytes(1) && TD.isFileLoadedAndExists(document.document);
    }
    return false;
  }

  public void showLanguageInstallPrompt (TdlibDelegate c, TdApi.LanguagePackInfo info) {
    ViewController<?> context = c.context().navigation().getCurrentStackItem();
    if (context == null || context.isDestroyed())
      return;
    if (Lang.packId().equals(info.id)) {
      CharSequence text = Strings.buildMarkdown(c, Lang.getString(R.string.LanguageSame, info.name), null);
      context.showOptions(text, new int[] {R.id.btn_done, R.id.btn_settings}, new String[] {Lang.getString(R.string.OK), Lang.getString(R.string.Settings)}, null, new int[] {R.drawable.baseline_check_circle_24, R.drawable.baseline_settings_24}, (itemView, id) -> {
        if (id == R.id.btn_done) {
          // Do nothing
        } else if (id == R.id.btn_settings) {
          context.navigateTo(new SettingsLanguageController(c.context(), c.tdlib()));
        }
        return true;
      });
      return;
    }
    CharSequence text = Strings.buildMarkdown(c, Lang.getString(info.isOfficial ? R.string.LanguageAlert : R.string.LanguageCustomAlert, info.name, (int) Math.floor((float) info.translatedStringCount / (float) info.totalStringCount * 100f), info.translationUrl), null);
    context.showOptions(text, new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.LanguageChange), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_language_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_done) {
        c.tdlib().client().send(new TdApi.AddCustomServerLanguagePack(info.id), result -> {
          switch (result.getConstructor()) {
            case TdApi.Ok.CONSTRUCTOR: {
              c.tdlib().applyLanguage(info, boolResult -> {
                if (boolResult) {
                  UI.showToast(R.string.LanguageChangeSuccess, Toast.LENGTH_SHORT);
                }
              }, true);
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(result);
              break;
            }
          }
        });
      }
      return true;
    });
  }

  public void showLanguageInstallPrompt (ViewController<?> c, CustomLangPackResult out, TdApi.Message sourceMessage) {
    if (sourceMessage != null) {
      TdApi.Chat sourceChat;
      if (sourceMessage.forwardInfo != null && sourceMessage.forwardInfo.origin.getConstructor() == TdApi.MessageOriginChannel.CONSTRUCTOR) {
        sourceChat = tdlib.chat(((TdApi.MessageOriginChannel) sourceMessage.forwardInfo.origin).chatId);
      } else {
        sourceChat = (!sourceMessage.isOutgoing || sourceMessage.isChannelPost) ? tdlib.chat(sourceMessage.chatId) : null;
      }
      if (sourceChat != null) {
        String username = tdlib.chatUsername(sourceChat.id);
        if (!StringUtils.isEmpty(username)) {
          out.setSourceName("@" + username);
        } else {
          out.setSourceName(tdlib.chatTitle(sourceChat));
        }
      }
    }

    c.showOptions(Lang.getStringBold(R.string.LanguageInfo,
      out.getStringValue(R.string.language_name),
      out.getStringValue(R.string.language_nameInEnglish),
      out.getStringValue(R.string.language_code),
      out.getStringValue(R.string.language_dateFormatLocale),
      out.getCompletenessPercentage(),
      out.isComplete() ? Lang.plural(R.string.xStrings, out.getStringsCount()) : Lang.plural(R.string.xStrings, out.getStringsCount()) + ", " + Lang.plural(R.string.TranslationsMissing, out.getMissingStringsCount())
      ),
      new int[] {R.id.btn_messageApplyLocalization, R.id.btn_cancel}, new String[] {Lang.getString(R.string.LanguageInstall), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_language_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_messageApplyLocalization) {
        applyLocalisation(c, out);
      }
      return true;
      });
  }

  private void applyLocalisation (ViewController<?> c, CustomLangPackResult out) {
    TdApi.LanguagePackString[] strings = out.getStrings();
    String code = out.getLanguageCode();
    TdApi.LanguagePackInfo info = new TdApi.LanguagePackInfo(code, Lang.getBuiltinLanguagePackId(), out.getLanguageNameInEnglish(), out.getStringValue(R.string.language_name), Lang.cleanLanguageCode(code), false, "1".equals(out.getStringValue(R.string.language_rtl)), false, true, strings.length, strings.length, strings.length, null);
    c.tdlib().client().send(new TdApi.SetCustomLanguagePack(info, strings), result -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          c.tdlib().applyLanguage(info, boolResult -> {
            if (boolResult) {
              UI.showToast(R.string.LocalisationApplied, Toast.LENGTH_SHORT);
              // exitToChatScreen(c, c.getChatId());
            }
          }, true);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
      }
    });
  }

  public static void removeAccount (ViewController<?> context, final TdlibAccount account) {
    removeAccount(context, account, false);
  }

  private static void removeAccount (ViewController<?> context, final TdlibAccount account, boolean isSignOut) {
    context.showOptions(Lang.getStringBold(isSignOut ? R.string.SignOutHint2 : R.string.RemoveAccountHint2, account.getName()), new int[]{R.id.btn_removeAccount, R.id.btn_cancel}, new String[]{Lang.getString(R.string.LogOut), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_logout_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_removeAccount) {
        account.tdlib().signOut();
      }
      return true;
    });
  }

  public void switchInline (ViewController<?> context, String username, String query, boolean keepStack) {
    ChatsController c = new ChatsController(context.context(), context.tdlib());
    c.setArguments(new ChatsController.Arguments(new ChatsController.PickerDelegate() {
      @Override
      public boolean onChatPicked (TdApi.Chat chat, Runnable onDone) {
        if (!tdlib.canSendBasicMessage(chat)) {
          UI.showToast(R.string.YouCantSendMessages, Toast.LENGTH_SHORT);
          return false;
        }
        return true;
      }

      @Override
      public Object getShareItem () {
        return new TGSwitchInline(username, query);
      }

      @Override
      public void modifyChatOpenParams (ChatOpenParameters params) {
        if (keepStack) {
          params.keepStack();
        }
      }
    }));
    context.navigateTo(c);
  }

  // Custom themes

  public void showDeleteThemeConfirm (ViewController<?> context, ThemeInfo theme, Runnable onDelete) {
    //noinspection WrongConstant
    if (!ThemeManager.isCustomTheme(theme.getId()))
      return;
    context.showOptions(Lang.getString(R.string.ThemeRemoveInfo), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ThemeRemoveConfirm), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_done) {
        ThemeManager.instance().removeCustomTheme(tdlib, theme.getId(), theme.parentThemeId(), onDelete);
      }
      return true;
    });
  }

  public void exportTheme (ViewController<?> context, ThemeInfo theme, boolean includeDefault, boolean asJava) {
    final String themeName = theme.getName();
    final int _customThemeId = ThemeManager.resolveCustomThemeId(theme.getId());
    final String originalAuthor = Settings.instance().getThemeAuthor(_customThemeId);

    if (!theme.isCustom() || !Settings.instance().canEditAuthor(_customThemeId)) {
      exportThemeImpl(context, theme.getId(), themeName, originalAuthor, includeDefault, asJava);
      return;
    }

    RunnableData<String> after = author -> exportThemeImpl(context, theme.getId(), themeName, author, includeDefault, asJava);
    String currentUsername = !StringUtils.isEmpty(originalAuthor) ? originalAuthor : context.tdlib().myUserUsername();
    context.openInputAlert(Lang.getString(R.string.ThemeExportAddAuthorTitle), Lang.getString(R.string.ThemeExportAddAuthorInfo), R.string.ThemeExportDone, R.string.Cancel, currentUsername, null, (v, resultAuthor) -> {
      if (resultAuthor.startsWith("@")) {
        resultAuthor = resultAuthor.substring(1);
      } else if (tdlib.isTmeUrl(resultAuthor)) {
        int i = resultAuthor.indexOf("://");
        if (i != -1)
          resultAuthor = resultAuthor.substring(i + 3);
        i = resultAuthor.indexOf('/');
        if (i == -1)
          return false;
        resultAuthor = resultAuthor.substring(i + 1);
      }
      if (!StringUtils.isEmpty(resultAuthor)) {
        if (!TD.matchUsername(resultAuthor) || resultAuthor.length() > TdConstants.MAX_USERNAME_LENGTH)
          return false;
      } else {
        resultAuthor = null;
      }
      String result = resultAuthor;
      tdlib.ui().postDelayed(() -> after.runWithData(result), 100);
      return true;
    }, true, arg -> {
      TextView textView = SettingHolder.createDescription(context.context(), ListItem.TYPE_DESCRIPTION, ColorId.textLight, null, context);
      textView.setText(Lang.getString(R.string.ThemeExportInfo));
      textView.setPadding(0, Screen.dp(12f), 0, 0);
      arg.addView(textView);
    }, null);


   /* List<SettingItem> items = new ArrayList<>();
    items.add(new SettingItem(SettingItem.TYPE_CHECKBOX_OPTION, R.id.btn_themeExportSetAuthor, 0, R.string.ThemeExportAddAuthor, !Strings.isEmpty(originalAuthor)));
    items.add(new SettingItem(SettingItem.TYPE_CHECKBOX_OPTION, R.id.btn_themeExportNeedDefault, 0, R.string.ThemeExportNeedDefault, false));
    if (BuildConfig.DEBUG) {
      items.add(new SettingItem(SettingItem.TYPE_CHECKBOX_OPTION, R.id.btn_test, 0, "Export as Java", false));
    }
    context.showSettings(new SettingsWrapBuilder(R.id.btn_share)
      .setSaveStr(R.string.ThemeExportDone)
      .setAllowResize(false)
      .setRawItems(items)
      .addHeaderItem(Lang.getStringBold(R.string.ThemeExportInfo, theme.getName()))
      .setIntDelegate((ignoredId, result) -> {
        boolean includeDefault = result.get(R.id.btn_themeExportNeedDefault) == R.id.btn_themeExportNeedDefault;
        boolean includeAuthor = result.get(R.id.btn_themeExportSetAuthor) == R.id.btn_themeExportSetAuthor;
        boolean asJava = result.get(R.id.btn_test) == R.id.btn_test;

        RunnableData<String> after = author -> exportThemeImpl(context, customThemeId, themeName, author, includeDefault, asJava);
        if (!includeAuthor) {
          after.run(null);
          return;
        }
        String currentUsername = !Strings.isEmpty(originalAuthor) ? originalAuthor : context.tdlib().myUserUsername();
        context.openInputAlert(Lang.getString(R.string.ThemeExportAddAuthorTitle), Lang.getString(R.string.ThemeExportAddAuthorInfo), R.string.ThemeExportDone, R.string.Cancel, currentUsername, (v, resultAuthor) -> {
          if (resultAuthor.startsWith("@")) {
            resultAuthor = resultAuthor.substring(1);
          } else if (tdlib.isTmeUrl(resultAuthor)) {
            int i = resultAuthor.indexOf("://");
            if (i != -1)
              resultAuthor = resultAuthor.substring(i + 3);
            i = resultAuthor.indexOf('/');
            if (i == -1)
              return false;
            resultAuthor = resultAuthor.substring(i + 1);
          }
          if (!Strings.isEmpty(resultAuthor)) {
            if (!TD.matchUsername(resultAuthor) || resultAuthor.length() > TD.MAX_USERNAME_LENGTH)
              return false;
          } else {
            resultAuthor = null;
          }
          after.run(resultAuthor);
          return true;
        }, true);
      }));*/
  }

  private void exportThemeImpl (TdlibDelegate context, int themeId, String themeName, @Nullable String author, boolean includeDefault, boolean asJava) {
    int flags = 0;
    if (includeDefault) {
      flags |= Theme.EXPORT_FLAG_INCLUDE_DEFAULT_VALUES;
    }
    if (asJava) {
      flags |= Theme.EXPORT_FLAG_JAVA;
    }

    String fileName;
    if (asJava) {
      fileName = StringUtils.secureFileName(themeName) + ".java";
    } else {
      fileName = StringUtils.secureFileName(themeName) + "." + BuildConfig.THEME_FILE_EXTENSION;
    }

    String conversion = "theme_export_" + System.currentTimeMillis() + "_" + themeId + "," + flags;
    if (!StringUtils.isEmpty(author)) {
      conversion += "," + author;
    }
    TdApi.InputMessageContent content = new TdApi.InputMessageDocument(new TdApi.InputFileGenerated(fileName, conversion, 0), null, false, null);
    ShareController c = new ShareController(context.context(), context.tdlib());
    c.setArguments(new ShareController.Args(content));
    c.show();
  }

  public static class ImportedTheme implements ThemeDelegate {
    public String name;
    public long time;
    public String author;
    public String wallpaper;

    public int parentThemeId = ThemeId.NONE;

    public static class Value implements Comparable<Value> {
      public final String name;
      public final int id;
      public int intValue;
      public float floatValue;

      public Value (String name, int id, int intValue) {
        this.name = name;
        this.id = id;
        this.intValue = intValue;
      }

      public Value (String name, int id, float floatValue) {
        this.name = name;
        this.id = id;
        this.floatValue = floatValue;
      }

      @Override
      public int compareTo (Value o) {
        return name.compareToIgnoreCase(o.name);
      }
    }

    private final Map<Integer, Float> propertiesMap = new HashMap<>();
    private final Map<Integer, Integer> colorsMap = new HashMap<>();
    public final List<Value> propertiesList = new ArrayList<>(), colorsList = new ArrayList<>();

    public ThemeCustom theme;

    @Override
    public int getId () {
      throw new RuntimeException("Stub!");
    }

    @Override
    public int getColor (int colorId) {
      Integer value = colorsMap.get(colorId);
      if (value != null)
        return value;
      return ThemeSet.getBuiltinTheme(parentThemeId).getColor(colorId);
    }

    public void addColor (String name, int id, int value) {
      if (colorsMap.containsKey(id))
        throw new IllegalArgumentException("Duplicate color: " + Theme.getColorName(id));
      colorsMap.put(id, value);
      colorsList.add(new Value(name, id, value));
    }

    @Override
    public float getProperty (int propertyId) {
      Float value = propertiesMap.get(propertyId);
      if (value != null)
        return value;
      return ThemeSet.getBuiltinTheme(parentThemeId).getProperty(propertyId);
    }

    @Override
    public String getDefaultWallpaper () {
      if (!StringUtils.isEmpty(wallpaper))
        return wallpaper;
      return ThemeSet.getBuiltinTheme(parentThemeId).getDefaultWallpaper();
    }

    public void addProperty (String name, int id, float value) {
      if (propertiesMap.containsKey(id))
        throw new IllegalArgumentException("Duplicate property: " + Theme.getPropertyName(id));
      if (!ThemeManager.isValidProperty(id, value))
        throw new IllegalArgumentException("Invalid property: " + Theme.getPropertyName(id) + "=" + value);
      propertiesMap.put(id, value);
      if (id == PropertyId.PARENT_THEME)
        this.parentThemeId = (int) value;
      propertiesList.add(new Value(name, id, value));
    }

    public void checkValidnessAndPrepare () {
      if (parentThemeId == ThemeId.NONE)
        throw new IllegalArgumentException("theme.parentThemeId is missing");
      if (StringUtils.isEmpty(name))
        throw new IllegalArgumentException("theme.name is missing");

      ThemeDelegate defaultTheme = ThemeSet.getOrLoadTheme(parentThemeId, false);
      for (int i = colorsList.size() - 1; i >= 0; i--) {
        Value value = colorsList.get(i);
        if (defaultTheme.getColor(value.id) == value.intValue) {
          colorsList.remove(i);
          colorsMap.remove(value.id);
        }
      }
      for (int i = propertiesList.size() - 1; i >= 0; i--) {
        Value value = propertiesList.get(i);
        if (value.id != PropertyId.PARENT_THEME && defaultTheme.getProperty(value.id) == value.floatValue) {
          propertiesList.remove(i);
          propertiesMap.remove(value.id);
        }
      }

      Comparator<Value> comparator = Value::compareTo;
      Collections.sort(propertiesList, comparator);
      Collections.sort(colorsList, comparator);
    }
  }

  private static final int THEME_CONTEXT_NONE = 0;
  private static final int THEME_CONTEXT_MAIN = 1;
  private static final int THEME_CONTEXT_ATTRIBUTES = 2;
  private static final int THEME_CONTEXT_COLORS = 3;

  public static boolean canInstallTheme (TdApi.Document document) {
    return document != null && !StringUtils.isEmpty(document.fileName) && document.fileName.endsWith(BuildConfig.THEME_FILE_EXTENSION) && TD.isFileLoadedAndExists(document.document);
  }

  public void readCustomTheme (ViewController<?> context, TdApi.Document doc, @Nullable RunnableData<ImportedTheme> onDone, @Nullable Runnable onError) {
    if (canInstallTheme(doc)) {
      readCustomTheme(context, doc.document, onDone, onError);
    } else {
      U.run(onError);
    }
  }

  public void readCustomTheme (ViewController<?> context, TdApi.File doc, @Nullable RunnableData<ImportedTheme> onDone, @Nullable Runnable onError) {
    Background.instance().post(() -> {
      int parse_context = THEME_CONTEXT_NONE;
      Map<String, Integer> propertyMap = ThemeProperties.getMap();
      Map<String, Integer> colorMap = ThemeColors.getMap();
      ImportedTheme theme = new ImportedTheme();
      boolean success = false;
      int lineIndex = 0;
      try (BufferedReader br = new BufferedReader(new FileReader(doc.local.path))) {
        String line;
        while ((line = br.readLine()) != null) {
          lineIndex++;
          line = line.trim();
          if (line.isEmpty())
            continue;
          char firstChar = line.charAt(0);
          switch (firstChar) {
            case '!':
              parse_context = THEME_CONTEXT_MAIN;
              continue;
            case '@':
              parse_context = THEME_CONTEXT_ATTRIBUTES;
              continue;
            case '#':
              parse_context = THEME_CONTEXT_COLORS;
              continue;
          }
          if (parse_context == THEME_CONTEXT_NONE)
            continue;
          int endIndex = line.indexOf("//");
          if (endIndex == 0) {
            continue;
          } else if (endIndex != -1) {
            line = line.substring(0, endIndex).trim();
            if (line.isEmpty()) {
              continue;
            }
          }
          // Trying to parse values variable
          int split = line.indexOf(':');
          if (split == -1)
            continue;
          String[] params = line.substring(0, split).trim().split(",");
          if (params.length == 0)
            continue;
          String valueRaw = line.substring(split + 1).trim();
          switch (parse_context) {
            case THEME_CONTEXT_MAIN: {
              if (params.length > 1)
                throw new IllegalArgumentException("Parse error: multiset unavailable in the main block");
              String param = params[0].trim();
              switch (param) {
                case "name":
                  theme.name = Strings.unwrap(param, valueRaw).trim();
                  if (StringUtils.isEmpty(theme.name))
                    throw new IllegalArgumentException("Invalid value: " + valueRaw);
                  break;
                case "time":
                  theme.time = Long.parseLong(valueRaw);
                  break;
                case "author":
                  theme.author = Strings.unwrap(param, valueRaw).trim();
                  if (StringUtils.isEmpty(theme.author) || !TD.matchUsername(theme.author) || theme.author.length() > TdConstants.MAX_USERNAME_LENGTH)
                    throw new IllegalArgumentException("Invalid value: " + valueRaw);
                  break;
                case "wallpaper":
                  theme.wallpaper = Strings.unwrap(param, valueRaw).trim();
                  if (StringUtils.isEmpty(theme.wallpaper))
                    throw new IllegalArgumentException("Invalid value: " + valueRaw);
                  break;
              }
              break;
            }
            case THEME_CONTEXT_ATTRIBUTES: {
              float value = Float.parseFloat(valueRaw);
              for (String name : params) {
                name = name.trim();
                Integer id = propertyMap.get(name);
                if (id == null) {
                  Log.e("Unknown theme property: %s, line: %d", name, lineIndex);
                  continue;
                }
                theme.addProperty(name, id, value);
              }
              break;
            }
            case THEME_CONTEXT_COLORS: {
              int color = ColorUtils.parseHexColor(valueRaw.startsWith("#") ? valueRaw.substring(1) : valueRaw, true);
              for (String name : params) {
                name = name.trim();
                Integer id = colorMap.get(name);
                if (id == null) {
                  Log.e("Unknown theme color: %s, line: %d", name, lineIndex);
                  continue;
                }
                theme.addColor(name, id, color);
              }
              break;
            }
          }
        }

        theme.checkValidnessAndPrepare();

        success = true;
      } catch (Throwable t) {
        Log.e("Cannot parse custom theme, line:%d", t, lineIndex);
      }
      if (success) {
        if (onDone != null) {
          tdlib.ui().post(() -> onDone.runWithData(theme));
        } else {
          tdlib.ui().post(() -> {
            CharSequence info;
            if (theme.author != null) {
              info = Lang.getString(R.string.ThemeInstallAuthor, (target, argStart, argEnd, argIndex, fakeBold) -> {
                if (argIndex == 1) {
                  CustomTypefaceSpan span = new CustomTypefaceSpan(null, ColorId.textLink);
                  span.setTextEntityType(new TdApi.TextEntityTypeMention());
                  span.setForcedTheme(theme);
                  return span;
                } else {
                  return Lang.newBoldSpan(fakeBold);
                }
              }, theme.name, "@" + theme.author);
            } else {
              info = Lang.getStringBold(R.string.ThemeInstall, theme.name);
            }
            int size = onError != null ? 3 : 2;
            IntList ids = new IntList(size);
            IntList colors = new IntList(size);
            IntList icons = new IntList(size);
            StringList strings = new StringList(size);

            ids.append(R.id.btn_done);
            icons.append(R.drawable.baseline_palette_24);
            colors.append(ViewController.OptionColor.BLUE);
            strings.append(R.string.ThemeInstallDone);

            if (onError != null) {
              ids.append(R.id.btn_open);
              icons.append(R.drawable.baseline_open_in_browser_24);
              colors.append(ViewController.OptionColor.NORMAL);
              strings.append(R.string.Open);
            }

            ids.append(R.id.btn_cancel);
            icons.append(R.drawable.baseline_cancel_24);
            colors.append(ViewController.OptionColor.NORMAL);
            strings.append(R.string.Cancel);

            if (info != null) {
              SpannableStringBuilder b = info instanceof SpannableStringBuilder ? (SpannableStringBuilder) info : new SpannableStringBuilder(info);
              b.append("\n\n");
              b.append(Lang.getString(R.string.ThemeInstallHint));
            }

            context.showOptions(info, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
              if (id == R.id.btn_done) {
                tdlib.wallpaper().loadWallpaper(theme.wallpaper, 1000, () -> {
                  context.tdlib().ui().installTheme(context, theme);
                });
              } else if (id == R.id.btn_open) {
                U.run(onError);
              }
              return true;
            }, theme);
          });
        }
      } else if (onError != null) {
        tdlib.ui().post(onError);
      }
    });
  }

  public void installTheme (TdlibDelegate context, ImportedTheme customTheme) {
    int themeId = ThemeManager.instance().installCustomTheme(customTheme);
    if (themeId != ThemeId.NONE) {
      ThemeManager.instance().changeGlobalTheme(tdlib, customTheme.theme, false, null);
    }
  }

  public static void reportChats (ViewController<?> context, long[] chatIds, Runnable after, @Nullable ThemeDelegate forcedTheme) {
    AtomicInteger remaining = new AtomicInteger(chatIds.length);
    Runnable act = new Runnable() {
      @Override
      public void run () {
        int index = chatIds.length - remaining.getAndDecrement();
        if (index < chatIds.length) {
          long chatId = chatIds[index];
          reportChat(context, chatId, null, forcedTheme, this, chatIds.length == 1);
        } else if (after != null) {
          after.run();
        }
      }
    };
    act.run();
  }

  private static final int REPORT_REASON_COUNT = 7;

  private static void fillReportReasons (IntList ids, StringList strings) {
    ids.append(R.id.btn_reportChatSpam);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.Spam);

    ids.append(R.id.btn_reportChatFake);
    // colors.append(ViewController.OPTION_COLOR_RED);
    strings.append(R.string.Fake);

    ids.append(R.id.btn_reportChatViolence);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.Violence);

    ids.append(R.id.btn_reportChatPornography);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.Pornography);

    ids.append(R.id.btn_reportChatChildAbuse);
    // colors.append(ViewController.OPTION_COLOR_RED);
    strings.append(R.string.ChildAbuse);

    ids.append(R.id.btn_reportChatCopyright);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.Copyright);

    ids.append(R.id.btn_reportChatIllegalDrugs);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.IllegalDrugs);

    ids.append(R.id.btn_reportChatPersonalDetails);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.PersonalDetails);

    ids.append(R.id.btn_reportChatOther);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.Other);
  }

  private static <T extends TdApi.Function<?>> void toReportReasons (ViewController<?> context, int reportReasonId, CharSequence title, T request, boolean forceText, RunnableData<T> reportCallback) {
    final TdApi.ReportReason reason;
    Td.assertReportReason_cf03e541();
    if (reportReasonId == R.id.btn_reportChatSpam) {
      reason = new TdApi.ReportReasonSpam();
    } else if (reportReasonId == R.id.btn_reportChatFake) {
      reason = new TdApi.ReportReasonFake();
    } else if (reportReasonId == R.id.btn_reportChatViolence) {
      reason = new TdApi.ReportReasonViolence();
    } else if (reportReasonId == R.id.btn_reportChatPornography) {
      reason = new TdApi.ReportReasonPornography();
    } else if (reportReasonId == R.id.btn_reportChatCopyright) {
      reason = new TdApi.ReportReasonCopyright();
    } else if (reportReasonId == R.id.btn_reportChatChildAbuse) {
      reason = new TdApi.ReportReasonChildAbuse();
    } else if (reportReasonId == R.id.btn_reportChatIllegalDrugs) {
      reason = new TdApi.ReportReasonIllegalDrugs();
    } else if (reportReasonId == R.id.btn_reportChatPersonalDetails) {
      reason = new TdApi.ReportReasonPersonalDetails();
    } else if (reportReasonId == R.id.btn_reportChatOther) { // TODO replace with openInputAlert
      reason = new TdApi.ReportReasonCustom();
      forceText = true;
    } else {
      throw new IllegalArgumentException(Lang.getResourceEntryName(reportReasonId));
    }
    switch (request.getConstructor()) {
      case TdApi.ReportChatPhoto.CONSTRUCTOR:
        ((TdApi.ReportChatPhoto) request).reason = reason;
        break;
      default:
        throw new UnsupportedOperationException(request.toString());
    }
    if (forceText) {
      RequestController c = new RequestController(context.context(), context.tdlib());
      c.setArguments(new RequestController.Delegate() {
        @Override
        public CharSequence getName () {
          return title;
        }

        @Override
        public int getPlaceholder () {
          return R.string.ReportReasonDescription;
        }

        @Override
        public void performRequest (String input, final RunnableBool callback) {
          if (StringUtils.isEmpty(input)) {
            callback.runWithBool(false);
            return;
          }
          switch (request.getConstructor()) {
            case TdApi.ReportChatPhoto.CONSTRUCTOR:
              ((TdApi.ReportChatPhoto) request).text = input;
              break;
          }
          callback.runWithBool(true);
          reportCallback.runWithData(request);
        }
      });
      context.context().navigation().navigateTo(c);
    } else {
      reportCallback.runWithData(request);
    }
  }

  public static void reportChatPhoto (ViewController<?> context, long chatId, int fileId, Runnable after, ThemeDelegate forcedTheme) {
    Tdlib tdlib = context.tdlib();
    CharSequence title = Lang.getStringBold(R.string.ReportChatPhoto, tdlib.chatTitle(chatId));

    IntList ids = new IntList(REPORT_REASON_COUNT);
    StringList strings = new StringList(REPORT_REASON_COUNT);
    fillReportReasons(ids, strings);

    context.showOptions(title, ids.get(), strings.get(), /*colors.get()*/ null, null, (itemView, id) -> {
      toReportReasons(context, id, title, new TdApi.ReportChatPhoto(chatId, fileId, null, null), false, request -> {
        if (after != null) {
          after.run();
        }
        tdlib.client().send(request, object -> {
          switch (object.getConstructor()) {
            case TdApi.Ok.CONSTRUCTOR:
              UI.showToast(R.string.ReportChatSent, Toast.LENGTH_SHORT);
              break;
            case TdApi.Error.CONSTRUCTOR:
              UI.showError(object);
              break;
          }
        });
      });
      return true;
    }, forcedTheme);
  }

  public static void reportChat (ViewController<?> context, long chatId, @Nullable TdApi.Message[] messages, @Nullable ThemeDelegate forcedTheme, @Nullable Runnable after, boolean needConfirmation) {
    Tdlib tdlib = context.tdlib();
    final long[] messageIds;
    final CharSequence title;
    if (messages != null && messages.length > 0) {
      messageIds = new long[messages.length];

      boolean singleSender = true;
      long senderId = Td.getSenderId(messages[0]);

      int i = 0;
      for (TdApi.Message message : messages) {
        messageIds[i++] = message.id;
        if (singleSender && Td.getSenderId(message) != senderId) {
          singleSender = false;
          senderId = 0;
        }
      }
      if (singleSender) {
        int confirmResId, resId;
        if (ChatId.isUserChat(senderId)) {
          confirmResId = messages.length == 1 ? R.string.QReportMessageUser : R.string.QReportMessagesUser;
          resId = messages.length == 1 ? R.string.ReportMessageUser : R.string.ReportMessagesUser;
        } else {
          confirmResId = messages.length == 1 ? R.string.QReportMessage : R.string.QReportMessages;
          resId = messages.length == 1 ? R.string.ReportMessage : R.string.ReportMessages;
        }
        String name = tdlib.chatTitle(senderId);
        title = Lang.getStringBold(needConfirmation ? confirmResId : resId, name);
      } else {
        title = Lang.plural(needConfirmation ? R.string.QReportXMessages : R.string.ReportXMessages, messages.length, Lang.boldCreator());
      }
    } else {
      messageIds = null;
      String chatTitle = tdlib.chatTitle(chatId);
      title = Lang.getStringBold(needConfirmation ? R.string.QReportChat : R.string.ReportChat, chatTitle);
    }

    if (needConfirmation) {
      context.showOptions(title,
        new int[] {
          R.id.btn_reportChat,
          R.id.btn_cancel
        }, new String[] {
          Lang.getString(R.string.ConfirmReportBtn),
          Lang.getString(R.string.Cancel)
        }, new int[] {
          ViewController.OptionColor.RED,
          ViewController.OptionColor.NORMAL
        },
        new int[] {
          R.drawable.baseline_warning_24,
          R.drawable.baseline_cancel_24
        }, (optionItemView, id) -> {
          if (id == R.id.btn_reportChat) {
            reportChat(context, chatId, messages, forcedTheme, after, false);
          }
          return true;
        }
      );
      return;
    }

    AtomicReference<String> reportText = new AtomicReference<>();

    //TODO(?): catch popup dismissal and call `after.run();`
    tdlib.send(new TdApi.ReportChat(chatId, null, messageIds, null), new Tdlib.ResultHandler<TdApi.ReportChatResult>() {
      @Override
      public void onResult (TdApi.ReportChatResult result, @Nullable TdApi.Error error) {
        if (error != null) {
          UI.showError(error);
          context.runOnUiThreadOptional(after);
          return;
        }
        switch (result.getConstructor()) {
          case TdApi.ReportChatResultOk.CONSTRUCTOR: {
            context.runOnUiThreadOptional(() -> {
              UI.showToast(R.string.ReportChatSent, Toast.LENGTH_SHORT);
              if (after != null) {
                after.run();
              }
            });
            break;
          }
          case TdApi.ReportChatResultOptionRequired.CONSTRUCTOR: {
            context.runOnUiThreadOptional(() -> {
              TdApi.ReportChatResultOptionRequired optionRequired = (TdApi.ReportChatResultOptionRequired) result;
              SparseArrayCompat<byte[]> idToOptionId = new SparseArrayCompat<>(optionRequired.options.length);

              ViewController.Options.Builder b = new ViewController.Options.Builder();
              if (StringUtils.isEmpty(optionRequired.title)) {
                b.info(title);
              } else {
                b.info(optionRequired.title);
              }
              int index = 0;
              for (TdApi.ReportOption option : optionRequired.options) {
                int id = ++index;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                  id = View.generateViewId();
                }
                b.item(new ViewController.OptionItem(id, option.text, ViewController.OptionColor.NORMAL, 0));
                idToOptionId.put(id, option.id);
              }
              PopupLayout popup = context.showOptions(b.build(), (optionItemView, id) -> {
                byte[] optionId = idToOptionId.get(id);
                if (optionId != null) {
                  tdlib.send(new TdApi.ReportChat(chatId, optionId, messageIds, reportText.get()), this);
                  return true;
                }
                return false;
              }, forcedTheme);
              if (popup != null) {
                popup.setDisableCancelOnTouchDown(true);
              }
            });
            break;
          }
          case TdApi.ReportChatResultTextRequired.CONSTRUCTOR: {
            context.runOnUiThreadOptional(() -> {
              TdApi.ReportChatResultTextRequired textRequired = (TdApi.ReportChatResultTextRequired) result;
              CharSequence placeholder = Lang.getMarkdownString(context, textRequired.isOptional ? R.string.ReportChatReasonOptional : R.string.ReportChatReasonRequired);
              context.openInputAlert(title, placeholder, R.string.ReportChatAlertBtn, R.string.Cancel, null, null, (inputView, userInput) -> {
                String text = StringUtils.trim(userInput);
                if (StringUtils.isEmpty(text) && !textRequired.isOptional) {
                  return false;
                } else {
                  reportText.set(text);
                  tdlib.send(new TdApi.ReportChat(chatId, textRequired.optionId, messageIds, text), this);
                  return true;
                }
              }, textRequired.isOptional, null, forcedTheme);
            });
            break;
          }
          case TdApi.ReportChatResultMessagesRequired.CONSTRUCTOR: {
            UI.showToast(R.string.ReportChatMessagesRequired, Toast.LENGTH_SHORT);
            context.runOnUiThreadOptional(after);
            break;
          }
          default: {
            Td.assertReportChatResult_63f241a6();
            throw Td.unsupported(result);
          }
        }
      }
    });
  }

  public interface EraseCallback {
    void onPrepareEraseData ();
    void onEraseDataCompleted ();
  }

  public void eraseLocalData (ViewController<?> context, boolean tdlibAvailable, EraseCallback callback) {
    CharSequence info = Lang.getMarkdownString(context, R.string.EraseDatabaseWarn);
    CharSequence hint = context.tdlib().context().isMultiUser() ? Lang.getMarkdownString(context, R.string.EraseDatabaseMultiUser) : null;
    context.showWarning(hint != null ? TextUtils.concat(info, "\n\n", hint) : info, success -> {
      if (success) {
        context.showWarning(Lang.getMarkdownString(context, R.string.EraseDatabaseWarn2), success2 -> {
          if (success2 && !context.isDestroyed() && context.isFocused() && context.navigationController() != null) {
            UI.showToast(R.string.EraseDatabaseProgress, Toast.LENGTH_SHORT);
            context.navigationController().getStack().setIsLocked(true);
            callback.onPrepareEraseData();

            UI.showToast(R.string.EraseDatabaseProgress, Toast.LENGTH_SHORT);
            Runnable databaseEraser = () -> {
              tdlib.eraseTdlibDatabase(eraseTdlibSuccess -> {
                Runnable after = () -> tdlib.ui().post(() -> {
                  if (!context.isDestroyed() && context.navigationController() != null) {
                    context.navigationController().getStack().setIsLocked(false);
                    callback.onEraseDataCompleted();
                  }
                  UI.showToast(R.string.EraseDatabaseDone, Toast.LENGTH_SHORT);
                });
                if (tdlibAvailable) {
                  tdlib.awaitInitialization(after);
                } else {
                  after.run();
                }
              });
            };
            if (tdlibAvailable) {
              tdlib.deleteAllFiles(deleteFilesSuccess -> databaseEraser.run());
            } else {
              databaseEraser.run();
            }
          }
        });
      }
    });
  }

  public void openCardNumber (TdlibDelegate context, String cardNumber) {
    tdlib.client().send(new TdApi.GetBankCardInfo(cardNumber), result -> {
      switch (result.getConstructor()) {
        case TdApi.BankCardInfo.CONSTRUCTOR:
          TdApi.BankCardInfo bankCardInfo = (TdApi.BankCardInfo) result;
          tdlib.ui().post(() -> {
            ViewController<?> c = context instanceof ViewController<?> ? (ViewController<?>) context : UI.getCurrentStackItem();
            boolean hasAnyActions = bankCardInfo.actions.length > 0;
            if (c != null && !c.isDestroyed()) {
              IntList ids = new IntList(hasAnyActions ? 1 : bankCardInfo.actions.length);
              StringList strings = new StringList(hasAnyActions ? 1 : bankCardInfo.actions.length);
              int[] icons = null;

              if (hasAnyActions) {
                for (TdApi.BankCardActionOpenUrl openUrl : bankCardInfo.actions) {
                  ids.append(R.id.btn_openLink);
                  strings.append(openUrl.text);
                }
              } else {
                ids.append(R.id.btn_copyLink);
                strings.append(R.string.CopyBankCard);
                icons = new int[] { R.drawable.baseline_content_copy_24 };
              }

              c.showOptions(bankCardInfo.title, ids.get(), strings.get(), null, icons, new OptionDelegate() {
                @Override
                public boolean onOptionItemPressed (View optionItemView, int id) {
                  if (id == R.id.btn_openLink) {
                    Intents.openUri((String) optionItemView.getTag());
                  } else if (id == R.id.btn_copyLink) {
                    UI.copyText(cardNumber, R.string.CopiedBankCard);
                  }

                  return true;
                }

                @Override
                public Object getTagForItem (int position) {
                  return hasAnyActions ? bankCardInfo.actions[position].url : null;
                }
              });
            }
          });
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
      }
    });
  }

  public List<HapticMenuHelper.MenuItem> fillDefaultHapticMenu (long chatId, boolean isEdit, boolean canToggleMarkdown, boolean canSendWithoutSound) {
    return fillDefaultHapticMenu(chatId, isEdit, canToggleMarkdown, canSendWithoutSound, false);
  }

  public List<HapticMenuHelper.MenuItem> fillDefaultHapticMenu (long chatId, boolean isEdit, boolean canToggleMarkdown, boolean canSendWithoutSound, boolean isForward) {
    List<HapticMenuHelper.MenuItem> items = new ArrayList<>();
    if (!isEdit && !ChatId.isSecret(chatId)) {
      if (tdlib.isSelfChat(chatId)) {
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendScheduled, Lang.getString(R.string.SendReminder), R.drawable.baseline_date_range_24).bindTutorialFlag(Settings.TUTORIAL_SET_REMINDER));
      } else {
        long userId = tdlib.chatUserId(chatId);
        if (userId != 0) {
          items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendOnceOnline, Lang.getString(R.string.SendOnceOnline), R.drawable.baseline_visibility_24).bindToLastSeenAvailability(tdlib, userId));
        }
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendScheduled, Lang.getString(R.string.SendSchedule), R.drawable.baseline_date_range_24).bindTutorialFlag(isForward ? Settings.TUTORIAL_FORWARD_SCHEDULE : Settings.TUTORIAL_SCHEDULE));
      }
    }
    if (!isEdit && canSendWithoutSound) {
      items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendNoSound, Lang.getString(R.string.SendNoSound), R.drawable.baseline_notifications_off_24));
    }
    if (canToggleMarkdown) {
      items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendNoMarkdown, Lang.getString(isEdit ? R.string.SaveNoMarkdown : R.string.SendNoMarkdown), R.drawable.baseline_code_24).bindTutorialFlag(Settings.TUTORIAL_SEND_WITHOUT_MARKDOWN));
    }
    return items;
  }

  public interface SimpleSendCallback {
    void onSendRequested (TdApi.MessageSendOptions sendOptions, boolean disableMarkdown);
  }

  public HapticMenuHelper createSimpleHapticMenu (ViewController<?> context, long chatId, @Nullable FutureBool availabilityCallback, @Nullable FutureBool canDisableMarkdownCallback, @Nullable FutureBool canHideMedia, RunnableData<List<HapticMenuHelper.MenuItem>> customItemProvider, SimpleSendCallback sendCallback, @Nullable ThemeDelegate forcedTheme) {
    return new HapticMenuHelper(list -> {
      if (availabilityCallback == null || availabilityCallback.getBoolValue()) {
        List<HapticMenuHelper.MenuItem> items = fillDefaultHapticMenu(chatId, false, canDisableMarkdownCallback != null && canDisableMarkdownCallback.getBoolValue(), true);
        if (customItemProvider != null) {
          if (items == null)
            items = new ArrayList<>();
          customItemProvider.runWithData(items);
        }
        return items;
      }
      return null;
    }, (menuView, parentView, item) -> {
      final int menuItemId = menuView.getId();
      if (menuItemId == R.id.btn_sendScheduled) {
        if (context != null) {
          tdlib.ui().pickSchedulingState(context, schedulingState ->
              sendCallback.onSendRequested(Td.newSendOptions(schedulingState), false),
            chatId, false, false, null, forcedTheme
          );
        }
      } else if (menuItemId == R.id.btn_sendNoMarkdown) {
        sendCallback.onSendRequested(Td.newSendOptions(), true);
      } else if (menuItemId == R.id.btn_sendNoSound) {
        sendCallback.onSendRequested(Td.newSendOptions(true), false);
      } else if (menuItemId == R.id.btn_sendOnceOnline) {
        sendCallback.onSendRequested(Td.newSendOptions(new TdApi.MessageSchedulingStateSendWhenOnline()), false);
      }
      return true;
    }, context != null ? context.getThemeListeners() : null, forcedTheme);
  }

  public boolean showScheduleOptions (ViewController<?> context, long chatId, boolean needSendWithoutSound, SimpleSendCallback callback, @Nullable TdApi.MessageSendOptions defaultSendOptions, @Nullable ThemeDelegate forcedTheme) {
    return pickSchedulingState(context,
      initialSendOptions ->
        callback.onSendRequested(initialSendOptions, false),
      chatId,
      tdlib.cache().userLastSeenAvailable(tdlib.chatUserId(chatId)),
      needSendWithoutSound,
      defaultSendOptions,
      forcedTheme
    );
  }

  public boolean pickSchedulingState (ViewController<?> context, RunnableData<TdApi.MessageSendOptions> callback, long chatId, boolean needOnline, boolean needSendWithoutSound, @Nullable TdApi.MessageSendOptions defaultSendOptions, @Nullable ThemeDelegate forcedTheme) {
    if (ChatId.isSecret(chatId)) {
      return false;
    }
    boolean isSelfChat = tdlib.isSelfChat(chatId);
    int size = 4;
    if (needOnline)
      size++;
    if (needSendWithoutSound)
      size++;
    IntList ids = new IntList(size);
    StringList strings = new StringList(size);
    IntList icons = new IntList(size);

    if (needSendWithoutSound) {
      ids.append(R.id.btn_sendNoSound);
      strings.append(R.string.SendNoSound);
      icons.append(R.drawable.baseline_notifications_off_24);
    }

    if (needOnline) {
      ids.append(R.id.btn_sendOnceOnline);
      strings.append(R.string.SendOnceOnline);
      icons.append(R.drawable.baseline_visibility_24);
    }

    ids.append(R.id.btn_sendScheduled30Min);
    strings.append(Lang.plural(isSelfChat ? R.string.RemindInXMinutes : R.string.SendInXMinutes, 30));
    icons.append(R.drawable.dotvhs_baseline_time_30m_24);

    ids.append(R.id.btn_sendScheduled2Hr);
    strings.append(Lang.plural(isSelfChat ? R.string.RemindInXHours : R.string.SendInXHours, 2));
    icons.append(R.drawable.dotvhs_baseline_time_2h_24);

    ids.append(R.id.btn_sendScheduled8Hr);
    strings.append(Lang.plural(isSelfChat ? R.string.RemindInXHours : R.string.SendInXHours, 8));
    icons.append(R.drawable.dotvhs_baseline_time_8h_24);

    ids.append(R.id.btn_sendScheduled1Yr);
    strings.append(Lang.plural(isSelfChat ? R.string.RemindInXYears : R.string.SendInXYears, 1));
    icons.append(R.drawable.dotvhs_baseline_time_1y_24);

    ids.append(R.id.btn_sendScheduledCustom);
    strings.append(Lang.getString(isSelfChat ? R.string.RemindAtCustomTime : R.string.SendAtCustomTime));
    icons.append(R.drawable.baseline_date_range_24);

    context.showOptions(null, ids.get(), strings.get(), null, icons.get(), (v, optionId) -> {
      long seconds = 0;
      if (optionId == R.id.btn_sendNoSound) {
        callback.runWithData(Td.newSendOptions(defaultSendOptions, true));
        return true;
      } else if (optionId == R.id.btn_sendOnceOnline) {
        callback.runWithData(Td.newSendOptions(defaultSendOptions, new TdApi.MessageSchedulingStateSendWhenOnline()));
        return true;
      } else if (optionId == R.id.btn_sendScheduled30Min) {
        seconds = TimeUnit.MINUTES.toSeconds(30);
      } else if (optionId == R.id.btn_sendScheduled2Hr) {
        seconds = TimeUnit.HOURS.toSeconds(2);
      } else if (optionId == R.id.btn_sendScheduled8Hr) {
        seconds = TimeUnit.HOURS.toSeconds(8);
      } else if (optionId == R.id.btn_sendScheduled1Yr) {
        long tdlibTimeMs = tdlib.currentTimeMillis();
        Calendar c = DateUtils.calendarInstance(tdlibTimeMs);
        c.add(Calendar.YEAR, 1);
        long elapsedMs = c.getTimeInMillis() - tdlibTimeMs;
        seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs);
      } else if (optionId == R.id.btn_sendScheduledCustom) {
        int titleRes, todayRes, tomorrowRes, futureRes;
        if (isSelfChat) {
          titleRes = R.string.SendReminder;
          todayRes = R.string.RemindTodayAt;
          tomorrowRes = R.string.RemindTomorrowAt;
          futureRes = R.string.RemindDateAt;
        } else {
          titleRes = R.string.SendSchedule;
          todayRes = R.string.SendTodayAt;
          tomorrowRes = R.string.SendTomorrowAt;
          futureRes = R.string.SendDateAt;
        }
        context.showDateTimePicker(Lang.getString(titleRes), todayRes, tomorrowRes, futureRes, millis -> {
          int sendDate = (int) TimeUnit.MILLISECONDS.toSeconds(millis);
          callback.runWithData(Td.newSendOptions(defaultSendOptions, new TdApi.MessageSchedulingStateSendAtDate(sendDate)));
        }, forcedTheme);
        return true;
      }
      if (seconds > 0) {
        int sendDate = (int) (tdlib.currentTime(TimeUnit.SECONDS) + seconds);
        callback.runWithData(Td.newSendOptions(defaultSendOptions, new TdApi.MessageSchedulingStateSendAtDate(sendDate)));
      }
      return true;
    }, forcedTheme);
    return true;
  }

  public void deleteContact (ViewController<?> context, long userId) {
    if (tdlib.cache().userContact(userId)) {
      context.showOptions(Lang.getStringBold(R.string.DeleteContactConfirm, tdlib.cache().userName(userId)), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id1) -> {
        if (!context.isDestroyed() && id1 == R.id.btn_delete) {
          tdlib.client().send(new TdApi.RemoveContacts(new long[] {userId}), tdlib.okHandler());
        }
        return true;
      });
    }
  }

  public interface OwnershipTransferListener {
    default void onOwnershipTransferAbilityChecked (TdApi.Object result) { }
    void onOwnershipTransferConfirmed (String password);
  }

  public void requestTransferOwnership (ViewController<?> context, CharSequence finalAlertMessageText, OwnershipTransferListener listener) {
    tdlib.send(new TdApi.CanTransferOwnership(), (canTransferOwnership, error) -> post(() -> {
      listener.onOwnershipTransferAbilityChecked(canTransferOwnership != null ? canTransferOwnership : error);
      if (error != null) {
        UI.showError(error);
        return;
      }
      switch (canTransferOwnership.getConstructor()) {
        case TdApi.CanTransferOwnershipResultOk.CONSTRUCTOR: {
          tdlib.send(new TdApi.GetPasswordState(), (passwordState, error1) -> {
            if (error1 != null) {
              UI.showError(error1);
              return;
            }
            post(() -> {
              PasswordController controller = new PasswordController(context.context(), context.tdlib());
              controller.setArguments(new PasswordController.Args(PasswordController.MODE_TRANSFER_OWNERSHIP_CONFIRM, passwordState).setSuccessListener(password -> {
                // Ask if the user REALLY wants to transfer ownership, because this operation is serious
                context.addOneShotFocusListener(() ->
                  context.showOptions(new ViewController.Options.Builder()
                    .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipAlert), finalAlertMessageText))
                    .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.TransferOwnershipConfirm), ViewController.OptionColor.RED, R.drawable.templarian_baseline_account_switch_24))
                    .cancelItem()
                    .build(), (optionView, id) -> {
                    if (id == R.id.btn_next) {
                      listener.onOwnershipTransferConfirmed(password);
                    }
                    return true;
                  })
                );
              }));
              context.navigateTo(controller);
            });
          });
          break;
        }
        case TdApi.CanTransferOwnershipResultPasswordNeeded.CONSTRUCTOR: {
          context.showOptions(new ViewController.Options.Builder()
            .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipSecurityAlert), Lang.getMarkdownString(context, R.string.TransferOwnershipSecurityPasswordNeeded)))
            .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.TransferOwnershipSecurityActionSetPassword), ViewController.OptionColor.BLUE, R.drawable.mrgrigri_baseline_textbox_password_24))
            .cancelItem()
            .build(), (optionView, id) -> {
              if (id == R.id.btn_next) {
                Settings2FAController controller = new Settings2FAController(context.context(), context.tdlib());
                controller.setArguments(new Settings2FAController.Args(null));
                context.navigateTo(controller);
              }
              return true;
            }
          );
          break;
        }
        case TdApi.CanTransferOwnershipResultPasswordTooFresh.CONSTRUCTOR: {
          context.showOptions(new ViewController.Options.Builder()
            .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipSecurityAlert), Lang.getMarkdownString(context, R.string.TransferOwnershipSecurityWaitPassword, Lang.getDuration(((TdApi.CanTransferOwnershipResultPasswordTooFresh) canTransferOwnership).retryAfter))))
            .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.OK), ViewController.OptionColor.NORMAL, R.drawable.baseline_check_circle_24))
            .cancelItem()
            .build(), (optionView, id) -> true
          );
          break;
        }
        case TdApi.CanTransferOwnershipResultSessionTooFresh.CONSTRUCTOR: {
          context.showOptions(new ViewController.Options.Builder()
            .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipSecurityAlert), Lang.getMarkdownString(context, R.string.TransferOwnershipSecurityWaitSession, Lang.getDuration(((TdApi.CanTransferOwnershipResultSessionTooFresh) canTransferOwnership).retryAfter))))
            .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.OK), ViewController.OptionColor.NORMAL, R.drawable.baseline_check_circle_24))
            .cancelItem()
            .build(), (optionView, id) -> true
          );
          break;
        }
        default:
          Td.assertCanTransferOwnershipResult_ac091006();
          throw Td.unsupported(canTransferOwnership);
      }
    }));
  }

  public void saveGifs (List<TD.DownloadedFile> downloadedFiles) {
    AtomicInteger remaining = new AtomicInteger(downloadedFiles.size());
    AtomicInteger successful = new AtomicInteger(0);
    for (TD.DownloadedFile downloadedFile : downloadedFiles) {
      tdlib.client().send(new TdApi.AddSavedAnimation(new TdApi.InputFileId(downloadedFile.getFileId())), object -> {
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR:
            successful.incrementAndGet();
            break;
          case TdApi.Error.CONSTRUCTOR:
            UI.showError(object);
            break;
        }
        if (remaining.decrementAndGet() == 0) {
          if (successful.get() == 1) {
            UI.showToast(R.string.GifSaved, Toast.LENGTH_SHORT);
          } else {
            UI.showToast(Lang.pluralBold(R.string.XGifSaved, downloadedFiles.size()), Toast.LENGTH_SHORT);
          }
        }
      });
    }
  }

  public void saveGif (int fileId) {
    if (fileId == 0) {
      return;
    }
    tdlib.client().send(new TdApi.AddSavedAnimation(new TdApi.InputFileId(fileId)), object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          UI.showToast(R.string.GifSaved, Toast.LENGTH_SHORT);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    });
  }

  public void subscribeToBeta (TdlibDelegate context) {
    openUrl(context, Lang.getStringSecure(R.string.url_betaSubscription), null);
  }

  public static CharSequence getTdlibVersionSignature () {
    return Lang.getStringSecure(R.string.format_commit, Lang.codeCreator(), Td.tdlibVersion(), Td.tdlibCommitHash());
  }

  // Telegram Premium

  public interface PremiumLimitCallback {
    void onPremiumLimitReached (int currentLimit, int premiumLimit);
  }

  @UiThread
  public void checkPremiumLimit (TdApi.PremiumLimitType premiumLimitType, @NonNull PremiumLimitCallback callback) {
    int effectiveLimit;
    switch (premiumLimitType.getConstructor()) {
      case TdApi.PremiumLimitTypeChatFolderCount.CONSTRUCTOR:
        effectiveLimit = tdlib.chatFolderCount();
        break;
      case TdApi.PremiumLimitTypeChatFolderInviteLinkCount.CONSTRUCTOR:
        effectiveLimit = tdlib.chatFolderInviteLinkCountMax();
        break;
      case TdApi.PremiumLimitTypeChatFolderChosenChatCount.CONSTRUCTOR:
        effectiveLimit = tdlib.chatFolderChosenChatCountMax();
        break;
      case TdApi.PremiumLimitTypeShareableChatFolderCount.CONSTRUCTOR:
        effectiveLimit = tdlib.addedShareableChatFolderCountMax();
        break;
      default:
        Td.assertPremiumLimitType_3b3ed738();
        throw Td.unsupported(premiumLimitType);
    }

    if (tdlib.hasPremium()) {
      callback.onPremiumLimitReached(effectiveLimit, effectiveLimit);
      return;
    }
    tdlib.send(new TdApi.GetPremiumLimit(premiumLimitType), (limit, error) -> post(() -> {
      if (limit != null && limit.defaultValue < limit.premiumValue && effectiveLimit < limit.premiumValue) {
        callback.onPremiumLimitReached(effectiveLimit, limit.premiumValue);
      } else {
        // Note: some users cannot purchase Telegram Premium, for such users GetPremiumLimit returns an error
        callback.onPremiumLimitReached(effectiveLimit, effectiveLimit);
      }
    }));
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PremiumFeature.STICKER,
    PremiumFeature.RESTRICT_VOICE_AND_VIDEO_MESSAGES,
    PremiumFeature.CUSTOM_EMOJI,
    PremiumFeature.NEW_CHATS_PRIVACY
  })
  public @interface PremiumFeature {
    int
      STICKER = 1,
      RESTRICT_VOICE_AND_VIDEO_MESSAGES = 2,
      CUSTOM_EMOJI = 3,
      NEW_CHATS_PRIVACY = 4;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    PremiumLimit.SHAREABLE_FOLDER_COUNT,
    PremiumLimit.CHAT_FOLDER_COUNT,
    PremiumLimit.CHAT_FOLDER_INVITE_LINK_COUNT,
  })
  public @interface PremiumLimit {
    int
      SHAREABLE_FOLDER_COUNT = 1,
      CHAT_FOLDER_COUNT = 2,
      CHAT_FOLDER_INVITE_LINK_COUNT = 3;
  }

  public boolean showPremiumAlert (ViewController<?> context, View view, @PremiumFeature int premiumFeature) {
    return showPremiumAlert(context, context.context().tooltipManager(), view, premiumFeature);
  }

  public boolean showPremiumAlert (ViewController<?> context, TooltipOverlayView tooltipManager, View view, @PremiumFeature int premiumFeature) {
    if (tdlib.hasPremium())
      return false;
    int stringRes;
    switch (premiumFeature) {
      case PremiumFeature.STICKER:
        stringRes = R.string.PremiumRequiredSticker;
        break;
      case PremiumFeature.RESTRICT_VOICE_AND_VIDEO_MESSAGES:
        stringRes = R.string.PremiumRequiredVoiceVideo;
        break;
      case PremiumFeature.CUSTOM_EMOJI:
        stringRes = R.string.MessageContainsPremiumFeatures;
        break;
      case PremiumFeature.NEW_CHATS_PRIVACY:
        stringRes = R.string.PremiumRequiredNewChats;
        break;
      default:
        throw new IllegalStateException();
    }
    showPremiumRequiredTooltip(context, tooltipManager, view, Lang.getMarkdownString(context, stringRes));
    return true;
  }

  public void showLimitReachedInfo (ViewController<?> context, View view, @PremiumLimit int premiumLimit) {
    showLimitReachedInfo(context, context.context().tooltipManager(), view, premiumLimit);
  }

  public void showLimitReachedInfo (ViewController<?> context, TooltipOverlayView tooltipManager, View view, @PremiumLimit int premiumLimit) {
    TdApi.PremiumLimitType type;
    int premiumPluralRes, defaultPluralRes;
    switch (premiumLimit) {
      case PremiumLimit.SHAREABLE_FOLDER_COUNT: {
        type = new TdApi.PremiumLimitTypeShareableChatFolderCount();
        premiumPluralRes = R.string.PremiumLimitAddShareableFolder;
        defaultPluralRes = R.string.LimitAddShareableFolder;
        break;
      }
      case PremiumLimit.CHAT_FOLDER_COUNT: {
        type = new TdApi.PremiumLimitTypeChatFolderCount();
        premiumPluralRes = R.string.PremiumLimitCreateFolder;
        defaultPluralRes = R.string.LimitCreateFolder;
        break;
      }
      case PremiumLimit.CHAT_FOLDER_INVITE_LINK_COUNT: {
        type = new TdApi.PremiumLimitTypeChatFolderInviteLinkCount();
        premiumPluralRes = R.string.PremiumLimitChatFolderInviteLink;
        defaultPluralRes = R.string.LimitChatFolderInviteLink;
        break;
      }
      default: {
        throw new IllegalArgumentException(Integer.toString(premiumLimit));
      }
    }
    showPremiumLimitTooltip(context, tooltipManager, view, premiumPluralRes, type, defaultPluralRes);
  }

  private void showPremiumLimitTooltip (ViewController<?> context, TooltipOverlayView tooltipManager, View view, @StringRes int markdownStringRes, TdApi.PremiumLimitType premiumLimitType, @StringRes int defaultMarkdownStringRes) {
    checkPremiumLimit(premiumLimitType, (currentLimit, premiumLimit) -> {
      if (currentLimit < premiumLimit) {
        showLimitReachedTooltip(context, tooltipManager, view, markdownStringRes, currentLimit, Strings.buildCounter(premiumLimit));
      } else {
        showLimitReachedTooltip(context, tooltipManager, view, defaultMarkdownStringRes, currentLimit);
      }
    });
  }

  private void showLimitReachedTooltip (ViewController<?> context, TooltipOverlayView tooltipManager, View view, @StringRes int pluralRes, long num, Object... formatArgs) {
    showPremiumRequiredTooltip(context, tooltipManager, view, Lang.getMarkdownPlural(context, pluralRes, num, Lang.boldCreator(), formatArgs));
  }

  private void showPremiumRequiredTooltip (ViewController<?> context, TooltipOverlayView tooltipManager, View view, CharSequence text) {
    tooltipManager
      .builder(view)
      .icon(R.drawable.baseline_warning_24)
      .controller(context)
      .show(tdlib, text)
      .hideDelayed();
  }

  // Video Chats & Live Streams

  public void openVoiceChatInvitation (ViewController<?> context, TdApi.InternalLinkTypeVideoChat invitation) {
    // TODO some confirmation screen & join voice chat if agreed
  }

  public void openVoiceChat (ViewController<?> context, int groupCallId, @Nullable UrlOpenParameters openParameters) {
    // TODO open voice chat layer
  }

  // Suggestions by emoji

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    StickersType.INSTALLED,
    StickersType.INSTALLED_EXTRA,
    StickersType.RECOMMENDED
  })
  public @interface StickersType {
    int INSTALLED = 0, INSTALLED_EXTRA = 1, RECOMMENDED = 2;
  }

  public static class EmojiStickers {
    private final Tdlib tdlib;
    public final String query;
    public final boolean isComplexQuery;

    private TdApi.Stickers installedStickers;
    private TdApi.Stickers installedExtraStickers;
    private TdApi.Stickers recommendedStickers;

    public EmojiStickers (Tdlib tdlib, TdApi.StickerType stickerType, String query, boolean isComplexQuery, int limit, long chatId, boolean needRecommended) {
      this.tdlib = tdlib;
      this.query = query;
      this.isComplexQuery = isComplexQuery;

      this.haveInstalledStickers = new ConditionalExecutor(() ->
        this.installedStickers != null
      );
      this.haveInstalledExtraStickers  = new ConditionalExecutor(() ->
        this.installedExtraStickers != null
      );
      this.haveRecommendedStickers = new ConditionalExecutor(() ->
        this.recommendedStickers != null
      );

      if (!isComplexQuery) {
        setStickers(noStickers(), StickersType.INSTALLED_EXTRA);
      }
      if (!needRecommended) {
        setStickers(noStickers(), StickersType.RECOMMENDED);
      }
      tdlib.client().send(new TdApi.GetStickers(stickerType, query, limit, chatId), object ->
        setStickers(object, StickersType.INSTALLED)
      );
      if (isComplexQuery) {
        tdlib.send(new TdApi.SearchEmojis(query, U.getInputLanguages()), (keywords, error) -> {
          if (keywords != null && keywords.emojiKeywords.length > 0) {
            String[] emojis = Td.findUniqueEmojis(keywords.emojiKeywords);
            String emojisQuery = TextUtils.join(" ", emojis);
            // Request 2x more than limit for the case all of the stickers returned by GetStickers
            tdlib.client().send(new TdApi.GetStickers(stickerType, emojisQuery, limit * 2, chatId), object ->
              setStickers(object, StickersType.INSTALLED_EXTRA)
            );
            if (needRecommended) {
              tdlib.client().send(new TdApi.SearchStickers(stickerType, emojisQuery, limit * 3), object ->
                setStickers(object, StickersType.RECOMMENDED)
              );
            }
          } else {
            setStickers(noStickers(), StickersType.INSTALLED_EXTRA);
            if (needRecommended) {
              setStickers(noStickers(), StickersType.RECOMMENDED);
            }
          }
        });
      } else {
        if (needRecommended) {
          // Request 2x more than limit for the case all of the stickers returned by GetStickers
          tdlib.client().send(new TdApi.SearchStickers(stickerType, query, limit * 2), object ->
            setStickers(object, StickersType.RECOMMENDED)
          );
        }
      }
    }

    private static TdApi.Stickers noStickers () {
      return new TdApi.Stickers(new TdApi.Sticker[0]);
    }

    private boolean isLoading () {
      return installedStickers == null || installedExtraStickers == null || recommendedStickers == null;
    }

    void setStickers (TdApi.Object stickersRaw, @StickersType int type) {
      TdApi.Stickers stickers = stickersRaw.getConstructor() == TdApi.Stickers.CONSTRUCTOR ? (TdApi.Stickers) stickersRaw : new TdApi.Stickers(new TdApi.Sticker[0]);
      switch (type) {
        case StickersType.INSTALLED:
          this.installedStickers = stickers;
          haveInstalledStickers.notifyConditionChanged();
          break;
        case StickersType.INSTALLED_EXTRA:
          this.installedExtraStickers = stickers;
          haveInstalledExtraStickers.notifyConditionChanged();
          break;
        case StickersType.RECOMMENDED:
          this.recommendedStickers = stickers;
          haveRecommendedStickers.notifyConditionChanged();
          break;
      }
    }

    private final ConditionalExecutor haveInstalledStickers;
    private final ConditionalExecutor haveInstalledExtraStickers;
    private final ConditionalExecutor haveRecommendedStickers;

    private TdApi.Sticker[] getInstalledStickers (boolean onlyExtra) {
      int installedCount = (this.installedStickers != null ? this.installedStickers.stickers.length : 0);
      int maxCount = installedCount + (this.installedExtraStickers != null ? this.installedExtraStickers.stickers.length : 0);
      List<TdApi.Sticker> stickers = new ArrayList<>(maxCount);
      if (this.installedStickers != null && !onlyExtra) {
        Collections.addAll(stickers, this.installedStickers.stickers);
      }
      if (this.installedExtraStickers != null && this.installedExtraStickers.stickers.length > 0) {
        LongSet excludeStickerIds = new LongSet(installedCount);
        if (installedCount > 0) {
          for (TdApi.Sticker sticker : this.installedStickers.stickers) {
            excludeStickerIds.add(sticker.id);
          }
        }
        ArrayUtils.ensureCapacity(stickers, stickers.size() + this.installedExtraStickers.stickers.length);
        for (TdApi.Sticker sticker : this.installedExtraStickers.stickers) {
          if (!excludeStickerIds.has(sticker.id)) {
            stickers.add(sticker);
          }
        }
      }
      return stickers.toArray(new TdApi.Sticker[0]);
    }

    private TdApi.Sticker[] getRecommendedStickers (@Nullable TdApi.Sticker[] excludeStickers) {
      int maxCount = this.recommendedStickers != null ? this.recommendedStickers.stickers.length : 0;
      List<TdApi.Sticker> stickers = new ArrayList<>(maxCount);
      if (this.recommendedStickers != null) {
        if (excludeStickers != null && excludeStickers.length > 0) {
          LongSet excludeStickerIds = new LongSet(excludeStickers.length);
          for (TdApi.Sticker sticker : excludeStickers) {
            excludeStickerIds.add(sticker.id);
          }
          for (TdApi.Sticker sticker : this.recommendedStickers.stickers) {
            if (!excludeStickerIds.has(sticker.id)) {
              stickers.add(sticker);
            }
          }
        } else {
          Collections.addAll(stickers, this.recommendedStickers.stickers);
        }
      }
      return stickers.toArray(new TdApi.Sticker[0]);
    }

    public interface Callback {
      void onStickersLoaded (EmojiStickers context, @NonNull TdApi.Sticker[] installedStickers, @Nullable TdApi.Sticker[] recommendedStickers, boolean expectMoreStickers);

      default void onMoreInstalledStickersLoaded (EmojiStickers context, @NonNull TdApi.Sticker[] moreInstalledStickers) { }
      default void onRecommendedStickersLoaded (EmojiStickers context, @NonNull TdApi.Sticker[] recommendedStickers) { }
      default void onAllStickersFinishedLoading (EmojiStickers context) { }
    }

    public void getStickers (Callback callback, long totalTimeoutMs) {
      if (totalTimeoutMs <= 0) {
        // Lazy path: wait for all methods to finish, invoke callback.onStickersLoaded
        haveInstalledStickers.executeOrPostponeTask(() -> {
          haveInstalledExtraStickers.executeOrPostponeTask(() -> {
            haveRecommendedStickers.executeOrPostponeTask(() -> {
              TdApi.Sticker[] installedStickers = getInstalledStickers(false);
              TdApi.Sticker[] recommendedStickers = getRecommendedStickers(installedStickers);
              tdlib.uiExecute(() ->
                callback.onStickersLoaded(this, installedStickers, recommendedStickers, false)
              );
            });
          });
        });
        return;
      }

      // Async path: wait up to max(totalTimeoutMs, first non-empty result)
      // Then invoke callback.onStickersLoaded with at least one sticker.
      // If `expectMoreStickers` was true:
      // - callback.onMoreInstalledStickersLoaded gets called if more installed stickers were loaded (non-empty)
      // - callback.onRecommendedStickersLoaded gets called if recommended stickers were loaded (might be empty)
      // - callback.onAllStickersFinishedLoading gets called after all requests are complete, no more stickers

      final long startTime = SystemClock.uptimeMillis();

      haveInstalledStickers.executeOrPostponeTask(() -> {
        CancellationSignal timeoutSignal = new CancellationSignal();
        AtomicBoolean timeoutPostponed = new AtomicBoolean(false);
        AtomicBoolean isExpectingMoreStickers = new AtomicBoolean(false);
        Runnable postponeTimeout = () -> {
          if (timeoutPostponed.getAndSet(true)) {
            return;
          }
          final long elapsedMs = SystemClock.uptimeMillis() - startTime;
          final long timeoutMs = Math.max(0, totalTimeoutMs - elapsedMs);
          tdlib.runOnTdlibThread(() -> {
            synchronized (isExpectingMoreStickers) {
              if (timeoutSignal.isCanceled()) {
                // Do nothing, because result was already sent
                return;
              }
              TdApi.Sticker[] installedStickers = getInstalledStickers(false);
              TdApi.Sticker[] recommendedStickers = this.recommendedStickers != null ? getRecommendedStickers(installedStickers) : null;
              boolean expectMoreStickers = isLoading();
              tdlib.ui().post(() ->
                callback.onStickersLoaded(this, installedStickers, recommendedStickers, expectMoreStickers)
              );
              isExpectingMoreStickers.set(expectMoreStickers);
              timeoutSignal.cancel();
            }
          }, (double) timeoutMs / 1000.0, false);
        };
        haveInstalledExtraStickers.executeOrPostponeTask(() -> {
          if (installedExtraStickers.stickers.length > 0) {
            synchronized (isExpectingMoreStickers) {
              if (isExpectingMoreStickers.get()) {
                TdApi.Sticker[] installedStickers = getInstalledStickers(true);
                tdlib.ui().post(() ->
                  callback.onMoreInstalledStickersLoaded(this, installedStickers)
                );
              }
            }
          }

          haveRecommendedStickers.executeOrPostponeTask(() -> {
            TdApi.Sticker[] installedStickers, recommendedStickers;
            boolean callbackExecuted;
            synchronized (isExpectingMoreStickers) {
              timeoutSignal.cancel();
              installedStickers = getInstalledStickers(false);
              recommendedStickers = getRecommendedStickers(installedStickers);
              callbackExecuted = isExpectingMoreStickers.get();
              if (callbackExecuted) {
                tdlib.ui().post(() -> {
                  callback.onRecommendedStickersLoaded(this, recommendedStickers);
                  callback.onAllStickersFinishedLoading(this);
                });
              }
            }
            if (!callbackExecuted) {
              tdlib.uiExecute(() ->
                callback.onStickersLoaded(this, installedStickers, recommendedStickers, false)
              );
            }
          });

          if (installedExtraStickers.stickers.length > 0) {
            postponeTimeout.run();
          }
        });
        if (installedStickers.stickers.length > 0) {
          postponeTimeout.run();
        }
      });
    }
  }

  public EmojiStickers getEmojiStickers (final TdApi.StickerType stickerType, final String query, final boolean isComplexQuery, int limit, long chatId) {
    int mode;
    if (stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR) {
      mode = Settings.instance().getEmojiMode();
    } else {
      mode = Settings.instance().getStickerMode();
    }
    if (tdlib.suggestOnlyApiStickers() && mode == Settings.STICKER_MODE_ALL) {
      mode = Settings.STICKER_MODE_ONLY_INSTALLED;
    }
    return new EmojiStickers(tdlib, stickerType, query, isComplexQuery, limit, chatId, mode == Settings.STICKER_MODE_ALL);
  }

  public interface MessageProvider {
    default boolean isSponsoredMessage () {
      return false;
    }
    default TdApi.SponsoredMessage getVisibleSponsoredMessage () {
      return null;
    }
    default boolean isMediaGroup () {
      return false;
    }
    default List<TdApi.Message> getVisibleMediaGroup () {
      return null;
    }
    TdApi.Message getVisibleMessage ();
    default @TdlibMessageViewer.Flags int getVisibleMessageFlags () {
      return 0;
    }
    default long getVisibleChatId () {
      TdApi.Message message = getVisibleMessage();
      return message != null ? message.chatId : 0;
    }
  }

  public interface MessageViewCallback {
    boolean onMessageViewed (TdlibMessageViewer.Viewport viewport, View view, TdApi.Message message, @TdlibMessageViewer.Flags long flags, long viewId, boolean allowRequest);
    default boolean needForceRead (TdlibMessageViewer.Viewport viewport) {
      return false;
    }
    default boolean allowViewRequest (TdlibMessageViewer.Viewport viewport) {
      return true;
    }

    default void onSponsoredMessageViewed (TdlibMessageViewer.Viewport viewport, View view, TdApi.SponsoredMessage sponsoredMessage, @TdlibMessageViewer.Flags long flags, long viewId, boolean allowRequest) {
      // Do nothing?
    }
    default boolean isMessageContentVisible (TdlibMessageViewer.Viewport viewport, View view) {
      return true;
    }
  }

  public Runnable attachViewportToRecyclerView (TdlibMessageViewer.Viewport viewport, RecyclerView recyclerView) {
    return attachViewportToRecyclerView(viewport, recyclerView, null);
  }

  public Runnable attachViewportToRecyclerView (TdlibMessageViewer.Viewport viewport, RecyclerView recyclerView, @Nullable MessageViewCallback callback) {
    Runnable viewMessages = () -> {
      if (viewport.isDestroyed()) {
        return;
      }
      boolean allowViewRequest = callback == null || callback.allowViewRequest(viewport);
      boolean forceRead = callback != null && callback.needForceRead(viewport);
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      if (manager == null)
        throw new IllegalStateException();
      int startIndex = manager.findFirstVisibleItemPosition();
      int endIndex = manager.findLastVisibleItemPosition();
      final long viewId = SystemClock.uptimeMillis();
      int viewedMessageCount = 0;
      if (startIndex != -1 && endIndex != -1) {
        for (int index = startIndex; index <= endIndex; index++) {
          View view = manager.findViewByPosition(index);
          if (view instanceof MessageProvider) {
            MessageProvider provider = (MessageProvider) view;
            boolean canViewMessage = callback == null || callback.isMessageContentVisible(viewport, view);
            if (!canViewMessage) {
              continue;
            }
            @TdlibMessageViewer.Flags int flags = provider.getVisibleMessageFlags();
            if (provider.isSponsoredMessage()) {
              TdApi.SponsoredMessage sponsoredMessage = provider.getVisibleSponsoredMessage();
              long chatId = provider.getVisibleChatId();
              if (sponsoredMessage != null && viewport.addVisibleMessage(chatId, sponsoredMessage, flags, viewId, false)) {
                if (callback != null) {
                  callback.onSponsoredMessageViewed(viewport, view, sponsoredMessage, flags, viewId, allowViewRequest);
                }
                viewedMessageCount++;
              }
            } else if (provider.isMediaGroup()) {
              List<TdApi.Message> mediaGroup = provider.getVisibleMediaGroup();
              if (mediaGroup != null) {
                for (TdApi.Message message : mediaGroup) {
                  boolean forceMarkAsRecent = callback != null && callback.onMessageViewed(viewport, view, message, flags, viewId, allowViewRequest);
                  if (viewport.addVisibleMessage(message, flags, viewId, forceMarkAsRecent)) {
                    viewedMessageCount++;
                  }
                }
              }
            } else {
              TdApi.Message message = provider.getVisibleMessage();
              if (message != null) {
                boolean forceMarkAsRecent = callback != null && callback.onMessageViewed(viewport, view, message, flags, viewId, allowViewRequest);
                if (viewport.addVisibleMessage(message, flags, viewId, forceMarkAsRecent)) {
                  viewedMessageCount++;
                }
              }
            }
          }
        }
      }
      viewport.removeOtherVisibleMessagesByViewId(viewId);
      if (allowViewRequest && (viewedMessageCount > 0 || viewport.haveRecentlyViewedMessages())) {
        viewport.viewMessages(true, forceRead, null);
      }
    };
    RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        viewMessages.run();
      }

      private boolean isScrolling;

      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        boolean wasScrolling = this.isScrolling;
        this.isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
        if (this.isScrolling != wasScrolling && !this.isScrolling) {
          viewMessages.run();
        }
      }
    };
    viewport.addOnDestroyListener(() ->
      recyclerView.removeOnScrollListener(onScrollListener)
    );
    recyclerView.addOnScrollListener(onScrollListener);
    return viewMessages;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    BirthdateOpenOrigin.SUGGESTED_ACTION,
    BirthdateOpenOrigin.PROFILE,
    BirthdateOpenOrigin.PRIVACY_SETTINGS
  })
  public @interface BirthdateOpenOrigin {
    int
      SUGGESTED_ACTION = 0,
      PROFILE = 1,
      PRIVACY_SETTINGS = 2;
  }
  public void openBirthdateEditor (ViewController<?> context, View view, @BirthdateOpenOrigin int origin) {
    TdApi.UserFullInfo userFull = tdlib.myUserFull();
    if (userFull == null)
      return;
    TdApi.Birthdate currentBirthdate = userFull.birthdate;

    RunnableData<String> act = hint -> {
      ViewController.Options.Builder b = new ViewController.Options.Builder();
      if (origin != BirthdateOpenOrigin.PRIVACY_SETTINGS && !StringUtils.isEmpty(hint)) {
        b.info(hint);
      }
      b.item(new ViewController.OptionItem.Builder()
        .id(R.id.btn_birthdate)
        .icon(R.drawable.baseline_date_range_24)
        .name(currentBirthdate == null ? R.string.MenuBirthdateSet : R.string.MenuBirthdateEdit)
        .color(currentBirthdate == null ? ViewController.OptionColor.BLUE : ViewController.OptionColor.NORMAL)
        .build()
      );
      if (origin != BirthdateOpenOrigin.PRIVACY_SETTINGS) {
        b.item(new ViewController.OptionItem.Builder()
          .id(R.id.btn_privacySettings)
          .icon(R.drawable.baseline_lock_24)
          .name(R.string.MenuBirthdatePrivacy)
          .build());
      }
      if (currentBirthdate != null) {
        b.item(new ViewController.OptionItem.Builder()
          .id(R.id.btn_delete)
          .icon(R.drawable.baseline_cancel_24)
          .color(ViewController.OptionColor.RED)
          .name(R.string.MenuBirthdateRemove)
          .build()
        );
      }
      if (origin == BirthdateOpenOrigin.SUGGESTED_ACTION) {
        b.item(new ViewController.OptionItem.Builder()
          .id(R.id.btn_suggestion)
          .icon(R.drawable.baseline_close_24)
          .name(R.string.ReminderSetBirthdateHide)
          .color(ViewController.OptionColor.RED)
          .build());
      }
      if (b.itemCount() == 1) {
        showBirthdatePicker(context, currentBirthdate);
        return;
      }
      context.showOptions(b.build(), (optionItemView, id) -> {
        if (id == R.id.btn_privacySettings) {
          SettingsPrivacyKeyController c = new SettingsPrivacyKeyController(context.context(), context.tdlib());
          c.setArguments(new SettingsPrivacyKeyController.Args(new TdApi.UserPrivacySettingShowBirthdate()));
          context.navigateTo(c);
        } else if (id == R.id.btn_birthdate) {
          showBirthdatePicker(context, currentBirthdate);
        } else if (id == R.id.btn_delete) {
          context.tdlib().send(new TdApi.SetBirthdate(null), (ok, setError) -> {
            if (setError != null) {
              context.runOnUiThreadOptional(() -> {
                context.showErrorTooltip(view, TD.toErrorString(setError));
              });
            }
          });
        } else if (id == R.id.btn_suggestion) {
          context.tdlib().send(new TdApi.HideSuggestedAction(new TdApi.SuggestedActionSetBirthdate()), tdlib.typedOkHandler());
        }
        return true;
      });
    };

    if (origin == BirthdateOpenOrigin.PRIVACY_SETTINGS) {
      // Skip privacy request
      act.runWithData(null);
      return;
    }

    context.tdlib().send(new TdApi.GetUserPrivacySettingRules(new TdApi.UserPrivacySettingShowBirthdate()), (rules, error) -> context.runOnUiThreadOptional(() -> {
      if (error != null) {
        context.showErrorTooltip(view, TD.toErrorString(error));
        return;
      }
      PrivacySettings privacy = PrivacySettings.valueOf(rules);
      String hint = TD.getPrivacyRulesString(context.tdlib(), TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR, privacy);
      act.runWithData(hint);
    }));
  }

  public void showBirthdatePicker (ViewController<?> controller, @Nullable TdApi.Birthdate currentBirthdate) {
    int day, month, year;
    if (currentBirthdate != null) {
      day = currentBirthdate.day;
      month = currentBirthdate.month - 1;
      year = currentBirthdate.year;
    } else {
      Calendar c = Calendar.getInstance();
      day = c.get(Calendar.DAY_OF_MONTH);
      month = c.get(Calendar.MONTH);
      year = 0;
    }
    controller.showCalendarDatePicker(
      Lang.getString(R.string.BirthdayPopupTitle),
      Lang.getString(R.string.Save),
      day,
      month,
      year,
      true,
      (picker, commitButtonView, newDay, newMonth, newYear) -> {
        RunnableData<TdApi.Birthdate> setBirthdate = birthdate ->
          tdlib.send(new TdApi.SetBirthdate(birthdate), (ok, error) -> controller.runOnUiThreadOptional(() -> {
            if (error != null) {
              if (picker.hasVisiblePopUp()) {
                picker.popupLayout().showErrorTooltip(controller, commitButtonView, TD.toErrorString(error));
              }
            } else {
              picker.dismissPopup(true);
            }
          }));

        TdApi.Birthdate newBirthdate = new TdApi.Birthdate(newDay, newMonth + 1, newYear);
        CharSequence str = Lang.getBirthdate(newBirthdate, false, true);

        ViewController.Options options = controller.getOptions(Lang.getStringBold(R.string.SetBirthdateWarning, str),
          new int[] {R.id.btn_send, R.id.btn_cancel},
          new String[] {Lang.getString(R.string.SetBirthdateOk), Lang.getString(R.string.Cancel)},
          new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
          new int[] {R.drawable.baseline_check_24, R.drawable.baseline_cancel_24}
        );
        options.setIgnoreOtherPopUps(true);
        controller.showOptions(options, (optionItemView, id) -> {
          if (id == R.id.btn_send) {
            setBirthdate.runWithData(newBirthdate);
          }
          return true;
        });
        return false;
      }
    );
  }

  public void shareCallLogs (ViewController<?> context, VoIPLogs.Pair logFiles, boolean needWarning) {
    if (logFiles != null && logFiles.exists()) {
      Runnable act = () -> {
        ShareController c = new ShareController(context.context(), tdlib);
        List<ShareController.FileInfo> list = new ArrayList<>();
        if (logFiles.hasPrimaryLogFile()) {
          list.add(new ShareController.FileInfo(logFiles.logFile.getPath(), "text/plain"));
        }
        if (logFiles.hasStatsLogFile()) {
          list.add(new ShareController.FileInfo(logFiles.statsLogFile.getPath(), "text/plain"));
        }
        ShareController.FileInfo[] array = list.toArray(new ShareController.FileInfo[0]);
        c.setArguments(new ShareController.Args(array));
        c.show();
      };
      if (needWarning) {
        context.showWarning(Lang.getMarkdownStringSecure(context, R.string.CallLogsWarning), accepted -> {
          if (accepted) {
            act.run();
          }
        });
      } else {
        act.run();
      }
    }
  }
}
