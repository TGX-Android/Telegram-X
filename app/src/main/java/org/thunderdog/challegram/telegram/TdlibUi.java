package org.thunderdog.challegram.telegram;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.dialogs.ChatView;
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
import org.thunderdog.challegram.filegen.SimpleGenerationInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.EditHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColors;
import org.thunderdog.challegram.theme.ThemeCustom;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperties;
import org.thunderdog.challegram.theme.ThemeProperty;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ChatLinkMembersController;
import org.thunderdog.challegram.ui.ChatLinksController;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.EditChatLinkController;
import org.thunderdog.challegram.ui.EditNameController;
import org.thunderdog.challegram.ui.EditProxyController;
import org.thunderdog.challegram.ui.EditRightsController;
import org.thunderdog.challegram.ui.EditUsernameController;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.ui.MapGoogleController;
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
import org.thunderdog.challegram.ui.SettingsLanguageController;
import org.thunderdog.challegram.ui.SettingsLogOutController;
import org.thunderdog.challegram.ui.SettingsNotificationController;
import org.thunderdog.challegram.ui.SettingsPhoneController;
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
import org.thunderdog.challegram.widget.CheckBox;
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
import java.util.ArrayList;
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
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.FutureBool;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.unit.ByteUnit;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

/**
 * Date: 2/18/18
 * Author: default
 */
public class TdlibUi extends Handler {
  private final Tdlib tdlib;

  /*package*/ TdlibUi (Tdlib tdlib) {
    super(Looper.getMainLooper());
    this.tdlib = tdlib;
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
      return new int[] {
        android.app.Notification.PRIORITY_MAX,
        android.app.Notification.PRIORITY_HIGH, // (default)
        android.app.Notification.PRIORITY_LOW,
      };
    }
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

  private static boolean deleteSuperGroupMessages (final ViewController<?> context, final TdApi.Message[] deletingMessages, final @Nullable Runnable after) {
    final Tdlib tdlib = context.tdlib();
    if (deletingMessages == null || deletingMessages.length == 0) {
      return false;
    }
    final long chatId = TD.getChatId(deletingMessages);
    if (chatId == 0 || !context.tdlib().isSupergroup(chatId)) {
      return false;
    }
    final TdApi.MessageSender sender = TD.getSender(deletingMessages);
    if (Td.getSenderUserId(sender) == 0 || context.tdlib().isSelfSender(sender)) {
      return false;
    }

    String firstName = tdlib.senderName(sender, true);
    CharSequence text = Lang.pluralBold(R.string.QDeleteXMessagesFromY, deletingMessages.length, firstName);

    SettingsWrap wrap = context.showSettings(new SettingsWrapBuilder(R.id.btn_deleteSupergroupMessages).setHeaderItem(
      new ListItem(ListItem.TYPE_INFO, R.id.text_title, 0, text, false)).setRawItems(
      new ListItem[]{
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_banUser, 0, R.string.RestrictUser, false),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_reportSpam, 0, R.string.ReportSpam, false),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_deleteAll, 0, Lang.getStringBold(R.string.DeleteAllFrom, firstName), false)
      }).setIntDelegate((id, result) -> {
        if (id == R.id.btn_deleteSupergroupMessages) {
          boolean banUser = result.get(R.id.btn_banUser) != 0;
          boolean reportSpam = result.get(R.id.btn_reportSpam) != 0;
          boolean deleteAll = result.get(R.id.btn_deleteAll) != 0;

          final long senderUserId1 = Td.getSenderUserId(deletingMessages[0]);
          final long[] messageIds = TD.getMessageIds(deletingMessages).valueAt(0);

          if (banUser) {
            tdlib.client().send(new TdApi.GetChatMember(chatId, deletingMessages[0].sender), object -> {
              switch (object.getConstructor()) {
                case TdApi.ChatMember.CONSTRUCTOR: {
                  final TdApi.ChatMember member = (TdApi.ChatMember) object;
                  tdlib.ui().post(() -> {
                    if (!context.isDestroyed()) {
                      TdApi.ChatMemberStatus myStatus = tdlib.chatStatus(chatId);
                      if (myStatus != null) {
                        EditRightsController editController = new EditRightsController(context.context(), context.tdlib());
                        editController.setArguments(new EditRightsController.Args(chatId, senderUserId1, true, myStatus, member));
                        context.navigateTo(editController);
                      }
                    }
                  });
                  break;
                }
              }
            });
          }

          if (reportSpam) {
            tdlib.client().send(new TdApi.ReportSupergroupSpam(ChatId.toSupergroupId(chatId), senderUserId1, messageIds), tdlib.okHandler());
          }

          if (deleteAll) {
            tdlib.client().send(new TdApi.DeleteChatMessagesFromUser(chatId, senderUserId1), tdlib.okHandler());
          } else {
            tdlib.deleteMessages(chatId, messageIds, true);
          }

          if (after != null) {
            after.run();
          }
        }
      }).setSaveStr(R.string.Delete).setSaveColorId(R.id.theme_color_textNegative));
    if (wrap != null) {
      tdlib.client().send(new TdApi.GetChatMember(deletingMessages[0].chatId, deletingMessages[0].sender), result -> {
        if (result.getConstructor() == TdApi.ChatMember.CONSTRUCTOR) {
          TdApi.ChatMember member = (TdApi.ChatMember) result;
          tdlib.ui().post(() -> {
            CharSequence role = null, newText = null;
            if (member.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
              role = Lang.getString(R.string.RoleOwner);
            } else if (member.status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR) {
              role = Lang.getString(R.string.RoleBanned);
            } else if (!TD.isMember(member.status, false)) {
              role = Lang.getString(R.string.RoleLeft);
            } else if (member.joinedChatDate != 0) {
              role = Lang.getRelativeDate(member.joinedChatDate, TimeUnit.SECONDS, System.currentTimeMillis(), TimeUnit.MILLISECONDS, true, 60, R.string.RoleMember, true);
            } else {
              return;
            }
            if (newText == null) {
              newText = Lang.plural(R.string.QDeleteXMessagesFromYRole, deletingMessages.length, (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex < 2 ? Lang.newBoldSpan(needFakeBold) : null, firstName, role);
            }
            int i = wrap.adapter.indexOfViewById(R.id.text_title);
            if (i != -1 && wrap.adapter.getItem(i).setStringIfChanged(newText)) {
              wrap.adapter.notifyItemChanged(i);
            }
          });
        }
      });
      // TODO TDLib / server: ability to get totalCount with limit=0
      tdlib.client().send(new TdApi.SearchChatMessages(chatId, null, deletingMessages[0].sender, 0, 0, 1, null, 0), result -> {
        if (result.getConstructor() == TdApi.Messages.CONSTRUCTOR) {
          int moreCount = ((TdApi.Messages) result).totalCount - deletingMessages.length;
          if (moreCount > 0) {
            tdlib.ui().post(() -> {
              int i = wrap.adapter.indexOfViewById(R.id.btn_deleteAll);
              if (i != -1 && wrap.adapter.getItem(i).setStringIfChanged(Lang.pluralBold(R.string.DeleteXMoreFrom, moreCount, firstName))) {
                wrap.adapter.notifyItemChanged(i);
              }
            });
          }
        }
      });
    }

    return true;
  }

  private static boolean deleteWithRevoke (final ViewController<?> context, final TdApi.Message[] deletingMessages, final @Nullable Runnable after) {
    if (deletingMessages == null || deletingMessages.length == 0)
      return false;

    final Tdlib tdlib = context.tdlib();
    final long singleChatId = TD.getChatId(deletingMessages);
    if (tdlib.isSelfChat(singleChatId)) {
      return false;
    }

    int totalCount = deletingMessages.length;
    int optionalCount = 0;
    int outgoingMessageCount = 0;
    int noRevokeCount = 0;
    for (TdApi.Message message : deletingMessages) {
      if (message.canBeDeletedForAllUsers && message.canBeDeletedOnlyForSelf) {
        optionalCount++;
        if (message.isOutgoing)
          outgoingMessageCount++;
      }
      if (!message.canBeDeletedForAllUsers && message.canBeDeletedOnlyForSelf) {
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
            for (TdApi.Message message : deletingMessages) {
              if (message.canBeDeletedForAllUsers) {
                revokeMessages[revokeIndex++] = message;
              } else {
                noRevokeMessages[noRevokeIndex++] = message;
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
            final LongSparseArray<long[]> messageIds = TD.getMessageIds(deletingMessages);
            for (int i = 0; i < messageIds.size(); i++) {
              tdlib.deleteMessages(messageIds.keyAt(i), messageIds.valueAt(i), revoke);
            }
          }
          if (after != null) {
            after.run();
          }
        }
      }).setSaveStr(R.string.Delete).setSaveColorId(R.id.theme_color_textNegative).setAllowResize(false));
    return true;
  }

  public static void showDeleteOptions (ViewController<?> context, TdApi.Message message) {
    showDeleteOptions(context, new TdApi.Message[] {message}, null);
  }

  public static void showDeleteOptions (final ViewController<?> context, final TdApi.Message[] messages, final @Nullable Runnable after) {
    if (context != null && messages != null && messages.length > 0) {
      if (deleteSuperGroupMessages(context, messages, after)) {
        return;
      }
      if (deleteWithRevoke(context, messages, after)) {
        return;
      }

      final Tdlib tdlib = context.tdlib();
      final long chatId = TD.getChatId(messages);

      boolean allScheduled = true;
      for (TdApi.Message msg : messages) {
        if (!TD.isScheduled(msg)) {
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
        for (TdApi.Message msg : messages) {
          if (!msg.canBeDeletedOnlyForSelf) {
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
        new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL},
        new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.menu_btn_delete) {
            LongSparseArray<long[]> messageIds = TD.getMessageIds(messages);
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
    popupFinal[0] = context.showOptions(Strings.buildMarkdown(context, Lang.getString(R.string.AskAQuestionInfo), (view, span) -> {
      if (popupFinal[0] != null) {
        popupFinal[0].hideWindow(true);
      }
      return false;
    }), new int[] {R.id.btn_openChat, R.id.btn_cancel}, StringList.asArray(R.string.AskButton, R.string.Cancel), new int[] {ViewController.OPTION_COLOR_BLUE, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_help_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
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
    private final int ttlTime;
    private final String ttlString;

    public TTLOption (int ttlTime, String ttlString) {
      this.ttlTime = ttlTime;
      this.ttlString = ttlString;
    }

    public int getTtlTime () {
      return ttlTime;
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
      headerView.updateMenuStopwatch(menuId, R.id.menu_btn_stopwatch, getTTLShort(chat != null ? chat.id : 0), isVisible = tdlib.hasWritePermission(chat), force);
    }
    return isVisible;
  }

  private void setTTL (TdApi.Chat chat, int newTtl) {
    if (chat == null) {
      return;
    }
    int oldTtl = tdlib.chatTTL(chat.id);
    if (oldTtl != newTtl) {
      tdlib.setChatMessageTtlSetting(chat.id, newTtl);
    }
  }

  public void showTTLPicker (final Context context, final TdApi.Chat chat) {
    showTTLPicker(context, tdlib.chatTTL(chat.id), false, false, 0, result -> setTTL(chat, result.ttlTime));
  }

  public static void showTTLPicker (final Context context, int currentTTL, boolean useDarkMode, boolean precise, @StringRes int message, final RunnableData<TTLOption> callback) {
    final ArrayList<TTLOption> ttlOptions = new ArrayList<>(21);
    ttlOptions.add(new TTLOption(0, Lang.getString(R.string.Off)));
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
      if (option.ttlTime == currentTTL) {
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
        colors.append(ViewController.OPTION_COLOR_NORMAL);
      }
    }

    if (defaultInfo != null) {
      strings.append(Lang.getString(R.string.NotificationsDefault, Lang.lowercase(defaultInfo)));
      icons.append(R.drawable.baseline_notifications_off_24);
      ids.append(R.id.btn_menu_resetToDefault);
      if (colors != null) {
        colors.append(ViewController.OPTION_COLOR_NORMAL);
      }
    }

    if (needDisable) {
      strings.append(R.string.MuteForever);
      icons.append(R.drawable.baseline_notifications_off_24);
      ids.append(R.id.btn_menu_disable);
      if (colors != null) {
        colors.append(ViewController.OPTION_COLOR_NORMAL);
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
        colors.append(ViewController.OPTION_COLOR_NORMAL);
        colors.append(ViewController.OPTION_COLOR_NORMAL);
        colors.append(ViewController.OPTION_COLOR_NORMAL);
      }
    }

    if (allowCustomize) {
      ids.append(R.id.btn_menu_customize);
      strings.append(R.string.NotificationsCustomize);
      icons.append(R.drawable.baseline_settings_24);
      if (colors != null) {
        colors.append(prioritizeCustomization ? ViewController.OPTION_COLOR_RED : ViewController.OPTION_COLOR_NORMAL);
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
      view.setDataColorId(R.id.theme_color_textNegative);
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
      view.setDataColorId(R.id.theme_color_textNegative);
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
    switch (id) {
      case R.id.btn_menu_enable: return TdlibUi.MUTE_ENABLE;
      case R.id.btn_menu_1hour: return TdlibUi.MUTE_1HOUR;
      case R.id.btn_menu_8hours: return TdlibUi.MUTE_8HOURS;
      case R.id.btn_menu_2days: return TdlibUi.MUTE_2DAYS;
      case R.id.btn_menu_disable: return TdlibUi.MUTE_FOREVER;
    }
    return -1;
  }

  public void unmute (long chatId) {
    tdlib.setMuteFor(chatId, 0);
  }

  // Profile helper

  public boolean handleProfileClick (ViewController<?> context, @Nullable View view, int id, TdApi.User user, boolean allowChangeNumber) {
    if (user == null) {
      return false;
    }
    switch (id) {
      case R.id.btn_username: {
        IntList ids = new IntList(4);
        IntList icons = new IntList(4);
        StringList strings = new StringList(4);

        boolean canEdit;

        if (tdlib.isSelfUserId(user.id)) {
          if (StringUtils.isEmpty(user.username)) {
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

        context.showOptions(canEdit ? null : "@" + user.username, ids.get(), strings.get(), null, icons.get());

        return true;
      }
      case R.id.btn_phone: {
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
      }
      case R.id.btn_addToGroup: {
        addToGroup(context, user.id);
        return true;
      }
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

  public boolean handleProfileOption (ViewController<?> context, int id, TdApi.User user) {
    if (user == null) {
      return false;
    }
    switch (id) {
      case R.id.btn_username_edit: {
        context.navigationController().navigateTo(new EditUsernameController(context.context(), context.tdlib()));
        return true;
      }
      case R.id.btn_username_copy: {
        UI.copyText('@' + user.username, R.string.CopiedUsername);
        return true;
      }
      case R.id.btn_username_copy_link: {
        UI.copyText(TD.getLink(user), R.string.CopiedLink);
        return true;
      }
      case R.id.btn_username_share: {
        shareUsername(context, user);
        return true;
      }
      case R.id.btn_phone_share: {
        shareUser(context, user);
        return true;
      }
      case R.id.btn_phone_copy: {
        UI.copyText('+' + user.phoneNumber, R.string.copied_phone);
        return true;
      }
      case R.id.btn_phone_call: {
        Intents.openNumber('+' + user.phoneNumber);
        return true;
      }
      case R.id.btn_changePhoneNumber: {
        context.navigationController().navigateTo(new SettingsPhoneController(context.context(), context.tdlib()));
        return true;
      }
    }
    return false;
  }

  public boolean handleProfileMore (ViewController<?> context, int id, TdApi.User user, TdApi.UserFullInfo userFull) {
    switch (id) {
      case R.id.more_btn_edit: {
        if (user != null) {
          EditNameController c = new EditNameController(context.context(), context.tdlib());
          if (context.tdlib().isSelfUserId(user.id)) {
            c.setMode(EditNameController.MODE_RENAME_SELF);
          } else {
            c.setMode(EditNameController.MODE_RENAME_CONTACT);
            c.setUser(user);
          }
          context.navigationController().navigateTo(c);
        }
        return true;
      }
      case R.id.more_btn_addToContacts: {
        if (user != null) {
          addContact(context, user);
        }
        return true;
      }
      case R.id.more_btn_logout: {
        logOut(context, true);
        return true;
      }
      case R.id.more_btn_addToGroup: {
        addToGroup(context, user.id);
        return true;
      }
      case R.id.more_btn_share: {
        shareUser(context, user);
        return true;
      }
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
    controller.setMode(EditNameController.MODE_ADD_CONTACT);
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
    int accountId = TdlibManager.instance().newAccount(isDebug);
    if (accountId == TdlibAccount.NO_ID) {
      return;
    }
    PhoneController c = new PhoneController(context, TdlibManager.getTdlib(accountId));
    c.setIsAccountAdd(true);
    context.navigation().navigateTo(c);
  }

  private void shareUsername (ViewController<?> context, TdApi.User user) {
    String username = user.username;
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

  public void shareProxyUrl (TdlibDelegate context, String url) {
    if (!StringUtils.isEmpty(url)) {
      ShareController c = new ShareController(context.context(), context.tdlib());
      c.setArguments(new ShareController.Args(Lang.getString(R.string.ShareTextProxyLink2, url)).setShare(Lang.getString(R.string.ShareTextProxyLink, url), Lang.getString(R.string.ShareBtnProxy)));
      c.show();
    }
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
        if (!tdlib.hasWritePermission(chat)) {
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

  // Change photo

  public void showChangePhotoOptions (ViewController<?> context, boolean canDelete) {
    if (canDelete) {
      context.showOptions(null, new int[] {R.id.btn_changePhotoCamera, R.id.btn_changePhotoGallery, R.id.btn_changePhotoDelete}, new String[] {Lang.getString(R.string.takePhoto), Lang.getString(R.string.pickFromGallery), Lang.getString(R.string.DeletePhoto)}, new int[] {ViewController.OPTION_COLOR_NORMAL, ViewController.OPTION_COLOR_NORMAL, ViewController.OPTION_COLOR_RED}, new int[] {R.drawable.baseline_camera_alt_24, R.drawable.baseline_image_24, R.drawable.baseline_remove_circle_24});
    } else {
      context.showOptions(null, new int[] {R.id.btn_changePhotoCamera, R.id.btn_changePhotoGallery}, new String[] {Lang.getString(R.string.takePhoto), Lang.getString(R.string.pickFromGallery)}, null, new int[] {R.drawable.baseline_camera_alt_24, R.drawable.baseline_image_24});
    }
  }

  public boolean handlePhotoOption (Context context, int id, TdApi.User user, EditHeaderView headerView) {
    if (user == null && id == R.id.btn_changePhotoDelete && headerView == null) {
      return false;
    }
    switch (id) {
      case R.id.btn_changePhotoCamera: {
        UI.openCameraDelayed(context);
        return true;
      }
      case R.id.btn_changePhotoGallery: {
        UI.openGalleryDelayed(false);
        return true;
      }
      case R.id.btn_changePhotoDelete: {
        if (user != null && user.profilePhoto != null) {
          deleteProfilePhoto(user.profilePhoto.id);
        } else {
          headerView.setPhoto(null);
        }
        return true;
      }
    }
    return false;
  }

  public void handlePhotoChange (int requestCode, Intent data, EditHeaderView headerView) {
    handlePhotoChange(requestCode, data, headerView, headerView == null);
  }

  public void handlePhotoChange (int requestCode, Intent data, EditHeaderView headerView, boolean isProfile) {
    // TODO show editor
    switch (requestCode) {
      case Intents.ACTIVITY_RESULT_IMAGE_CAPTURE: {
        File image = Intents.takeLastOutputMedia();
        if (image != null) {
          U.addToGallery(image);
          if (isProfile) {
            setProfilePhoto(image.getPath());
          } else {
            setEditPhotoCompressed(image.getPath(), headerView);
          }
        }
        break;
      }
      case Intents.ACTIVITY_RESULT_GALLERY: {
        if (data != null) {
          final Uri image = data.getData();
          if (image != null) {
            String imagePath = U.tryResolveFilePath(image);
            if (imagePath != null) {
              if (imagePath.endsWith(".webp")) {
                UI.showToast("Webp is not supported for profile photos", Toast.LENGTH_LONG);
                return;
              }
              if (isProfile) {
                setProfilePhoto(imagePath);
              } else {
                setEditPhotoCompressed(imagePath, headerView);
              }
              return;
            }
          }
        }
        UI.showToast("Error", Toast.LENGTH_SHORT);
        break;
      }
    }
  }

  private static void setEditPhotoCompressed (final String path, final EditHeaderView headerView) {
    ImageFile file = new ImageFileLocal(path);
    file.setSize(ChatView.getDefaultAvatarCacheSize());
    file.setDecodeSquare(true);
    headerView.setPhoto(file);
  }

  private void setProfilePhoto (String path) {
    UI.showToast(R.string.UploadingPhotoWait, Toast.LENGTH_SHORT);
    tdlib.client().send(new TdApi.SetProfilePhoto(new TdApi.InputChatPhotoStatic(new TdApi.InputFileGenerated(path, SimpleGenerationInfo.makeConversion(path), 0))), tdlib.profilePhotoHandler());
  }

  private void deleteProfilePhoto (long photoId) {
    UI.showToast(R.string.DeletingPhotoWait, Toast.LENGTH_SHORT);
    tdlib.client().send(new TdApi.DeleteProfilePhoto(photoId), tdlib.profilePhotoHandler());
  }

  public static void sendLogs (final ViewController<?> context, final boolean old, final boolean export) {
    String path = TdlibManager.getLogFilePath(old);
    File file = new File(path);
    if (!file.exists()) {
      UI.showToast("Log does not exist", Toast.LENGTH_SHORT);
      return;
    }
    long size = file.length();
    if (size == 0) {
      UI.showToast("Log is empty", Toast.LENGTH_SHORT);
      return;
    }

    ShareController share = new ShareController(context.context(), export ? null : context.tdlib());
    share.setArguments(new ShareController.Args(path, "text/plain"));
    share.show();
  }

  // Logs

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
            after.runWithLong(new File(TdlibManager.getLogFilePath(old)).length());
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
          StickerSetWrap.showStickerSet(context, (TdApi.StickerSet) object);
          break;
        }
        case TdApi.StickerSetInfo.CONSTRUCTOR: {
          StickerSetWrap.showStickerSet(context, (TdApi.StickerSetInfo) object);
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
    tdlib.client().send(new TdApi.SearchStickerSet(name), newStickerSetHandler(context, openParameters));
  }

  public void showStickerSet (TdlibDelegate context, long setId) {
    // TODO progress
    tdlib.client().send(new TdApi.GetStickerSet(setId), newStickerSetHandler(context, null));
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

  public static class ChatOpenParameters {
    public int options;
    public RunnableLong after;
    public Runnable onDone;
    public Object shareItem;

    public boolean highlightSet;
    public int highlightMode;
    public MessageId highlightMessageId;

    public @Nullable UrlOpenParameters urlOpenParameters;

    public TdApi.ChatList chatList;
    public String inviteLink;
    public ThreadInfo threadInfo;
    public TdApi.SearchMessagesFilter filter;
    public TdApi.InternalLinkTypeVoiceChat voiceChatInvitation;

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
        TD.saveFilter(outState, keyPrefix + "cp_filter", filter);
      return true;
    }

    public static ChatOpenParameters restoreInstanceState (Bundle in, String keyPrefix) {
      ChatOpenParameters params = new ChatOpenParameters();
      params.options = in.getInt(keyPrefix + "cp_options", 0);
      params.highlightSet = in.getBoolean(keyPrefix + "cp_highlightSet", false);
      params.highlightMode = in.getInt(keyPrefix + "cp_highlightMode", 0);
      params.highlightMessageId = TD.restoreMessageId(in, keyPrefix + "cp_highlightMessageId");
      params.chatList = TD.chatListFromKey(in.getString(keyPrefix + "cp_chatList", null));
      params.threadInfo = ThreadInfo.restoreFrom(in, keyPrefix + "cp_messageThread");
      params.filter = TD.restoreFilter(in, keyPrefix + "cp_filter");
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

    public ChatOpenParameters inviteLink (String inviteLink) {
      this.inviteLink = inviteLink;
      return this;
    }

    public ChatOpenParameters chatList (TdApi.ChatList chatList) {
      this.chatList = chatList;
      return this;
    }

    public ChatOpenParameters messageThread (ThreadInfo threadInfo) {
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

    public ChatOpenParameters voiceChatInvitation (TdApi.InternalLinkTypeVoiceChat voiceChatInvitation) {
      this.voiceChatInvitation = voiceChatInvitation;
      return this;
    }

    public ChatOpenParameters keepStack () {
      this.options |= CHAT_OPTION_KEEP_STACK;
      return this;
    }

    public ChatOpenParameters scheduledOnly () {
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

  private void showChatOpenError (TdApi.Function createRequest, TdApi.Error error, @Nullable ChatOpenParameters parameters) {
    if (!UI.inUiThread()) {
      tdlib.ui().post(() -> {
        showChatOpenError(createRequest, error, parameters);
      });
      return;
    }
    CharSequence message = TD.toErrorString(error);
    switch (error.message) {
      case "USERNAME_NOT_OCCUPIED":
        if (createRequest.getConstructor() == TdApi.SearchPublicChat.CONSTRUCTOR) {
          message = Lang.getStringBold(R.string.UsernameNotOccupied, ((TdApi.SearchPublicChat) createRequest).username);
        }
        break;
    }
    showLinkTooltip(tdlib, R.drawable.baseline_error_24, message, parameters != null ? parameters.urlOpenParameters : null);
  }

  private static void showLinkTooltip (Tdlib tdlib, int iconRes, CharSequence message, UrlOpenParameters urlOpenParameters) {
    if (!UI.inUiThread()) {
      tdlib.ui().post(() -> {
        showLinkTooltip(tdlib, iconRes, message, urlOpenParameters);
      });
      return;
    }
    if (urlOpenParameters != null && urlOpenParameters.tooltip != null && urlOpenParameters.tooltip.hasVisibleTarget()) {
      if (iconRes == 0) {
        urlOpenParameters.tooltip.show(tdlib, message).hideDelayed();
      } else {
        new TooltipOverlayView.TooltipBuilder(urlOpenParameters.tooltip).icon(iconRes).show(tdlib, message).hideDelayed();
      }
    } else {
      UI.showToast(message, Toast.LENGTH_SHORT);
    }
  }

  private void openChat (final TdlibDelegate context, final long chatId, final TdApi.Function createRequest, final @Nullable ChatOpenParameters params) {
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
      TdApi.Function function;
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
        main.get();
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
      showAccessError(context, urlOpenParameters, accessState, tdlib.isChannel(chat.id));
      if (params != null)
        params.onDone();
      return;
    }

    final RunnableLong after = params != null ? params.after : null;
    final Object shareItem = params != null ? params.shareItem : null;
    final TdApi.ChatList chatList = params != null ? params.chatList : null;
    final int options = params != null ? params.options : 0;
    final boolean onlyScheduled = (options & CHAT_OPTION_SCHEDULED_MESSAGES) != 0;
    final TdApi.InternalLinkTypeVoiceChat voiceChatInvitation = params != null ? params.voiceChatInvitation : null;
    final ThreadInfo messageThread = params != null ? params.threadInfo : null;
    final TdApi.SearchMessagesFilter filter = params != null ? params.filter : null;
    final MessagesController.Referrer referrer = params != null && !StringUtils.isEmpty(params.inviteLink) ? new MessagesController.Referrer(params.inviteLink) : null;

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
    if (highlightMessageId != null) {
      arguments = new MessagesController.Arguments(chatList, chat, messageThread, highlightMessageId, highlightMode, filter);
    } else {
      arguments = new MessagesController.Arguments(tdlib, chatList, chat, messageThread, filter);
    }
    controller.setArguments(arguments
      .setScheduled(onlyScheduled)
      .referrer(referrer)
      .voiceChatInvitation(voiceChatInvitation)
    );

    View view = controller.get();
    if (controller.context().isNavigationBusy()) {
      if (params != null) {
        params.onDone();
      }
      return;
    }
    if (view.getParent() != null) {
      ((ViewGroup) view.getParent()).removeView(controller.get());
    }

    if (navigation.isEmpty()) {
      navigation.initController(controller);
      MainController c = new MainController(context.context(), context.tdlib());
      c.get();
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
      c.get();
      navigation.getStack().insert(c, 0);
    } else {
      ViewController<?> c = navigation.getCurrentStackItem();
      if (c instanceof MessagesController && c.getChatId() == chat.id && !((MessagesController) c).inPreviewMode()) {
        profileController.setShareCustomHeaderView(true);
      }
      navigation.navigateTo(profileController);
    }
  }

  private void openChatProfile (final TdlibDelegate context, final long chatId, @Nullable ThreadInfo messageThread, TdApi.Function createRequest, final @Nullable UrlOpenParameters openParameters) {
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

  public void openVoiceChat (final TdlibDelegate context, final @NonNull TdApi.InternalLinkTypeVoiceChat voiceChatInvitation, final @Nullable UrlOpenParameters openParameters) {
    openChat(context, 0, new TdApi.SearchPublicChat(voiceChatInvitation.chatUsername), new ChatOpenParameters().urlOpenParameters(openParameters).voiceChatInvitation(voiceChatInvitation).keepStack().openProfileInCaseOfPrivateChat());
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
              openChat(context, chat, new ChatOpenParameters().urlOpenParameters(openParameters).shareItem(new TGBotStart(user.id, startArgument, false)).keepStack());
              break;
            }
            case BOT_MODE_START_IN_GROUP:
            case BOT_MODE_START_GAME: {
              final boolean isGame = botMode == BOT_MODE_START_GAME;
              addToGroup(context, new TGBotStart(user.id, startArgument, isGame), isGame);
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
    openChat(context, chatId, new ChatOpenParameters().keepStack().highlightMessage(messageId).ensureHighlightAvailable().urlOpenParameters(openParameters));
  }

  public void openMessage (final TdlibDelegate context, final TdApi.MessageLinkInfo messageLink, final UrlOpenParameters openParameters) {
    if (messageLink.message != null) {
      // TODO support for album, comments, etc
      openMessage(context, messageLink.chatId, new MessageId(messageLink.message.chatId, messageLink.message.id), openParameters);
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

  private void openJoinDialog (final TdlibDelegate context, final String inviteLink, final @Nullable UrlOpenParameters openParameters, TdApi.ChatInviteLinkInfo inviteLinkInfo) {
    if (TdlibManager.inBackgroundThread()) {
      tdlib.runOnUiThread(() -> openJoinDialog(context, inviteLink, openParameters, inviteLinkInfo));
      return;
    }
    final String msg = Lang.getString(TD.isChannel(inviteLinkInfo.type) ? R.string.FollowChannelX : R.string.JoinGroupX, inviteLinkInfo.title);
    ViewController<?> c = context.context().navigation().getCurrentStackItem();
    if (c != null) {
      c.showOptions(msg, new int[]{R.id.btn_join, R.id.btn_cancel}, new String[]{Lang.getOK(), Lang.getString(R.string.Cancel)}, null, null, (itemView, id) -> {
        if (id == R.id.btn_join) {
          joinChatByInviteLink(context, inviteLink, openParameters);
        }
        return true;
      });
    }
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
            openJoinDialog(context, inviteLink, openParameters, inviteLinkInfo);
          } else {
            openChat(context, inviteLinkInfo.chatId, new ChatOpenParameters().urlOpenParameters(openParameters).inviteLink(inviteLink).keepStack().removeDuplicates());
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

  private boolean installLanguage (final TdlibDelegate context, final String languagePackId, @Nullable UrlOpenParameters openParameters) {
    if (TD.isLocalLanguagePackId(languagePackId)) {
      Log.e("Attempt to install custom local languagePackId:%s", languagePackId);
      return true;
    }
    // TODO progress
    tdlib.client().send(new TdApi.GetLanguagePackInfo(languagePackId), result -> {
      switch (result.getConstructor()) {
        case TdApi.LanguagePackInfo.CONSTRUCTOR: {
          TdApi.LanguagePackInfo info = (TdApi.LanguagePackInfo) result;
          tdlib.ui().post(() -> {
            if (context.context().getActivityState() != UI.STATE_DESTROYED) {
              showLanguageInstallPrompt(context, info);
            }
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          showLinkTooltip(context.tdlib(), R.drawable.baseline_warning_24, TD.toErrorString(result), openParameters);
          break;
        }
        default: {
          Log.unexpectedTdlibResponse(result, TdApi.GetLanguagePackInfo.class, TdApi.LanguagePackInfo.class, TdApi.Error.class);
          break;
        }
      }
    });
    return true;
  }

  private void joinChatByInviteLink (final TdlibDelegate context, final String inviteLink, final @Nullable UrlOpenParameters urlOpenParameters) {
    openChat(context, 0, new TdApi.JoinChatByInviteLink(inviteLink), new ChatOpenParameters().urlOpenParameters(urlOpenParameters).keepStack().removeDuplicates());
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

    public MessageId messageId;
    public String refererUrl, instantViewFallbackUrl;
    public TooltipOverlayView.TooltipBuilder tooltip;
    public boolean requireOpenPrompt;
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
        this.displayUrl = options.displayUrl;
        this.parentController = options.parentController;
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
        if (message.isSending()) {
          this.sourceMessage = message;
          message.getMessageIdChangeListeners().add(this);
        } else {
          this.sourceMessage = null;
        }
        controller(message.controller());
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

    public UrlOpenParameters referer (String refererUrl) {
      this.refererUrl = refererUrl;
      return this;
    }

    public UrlOpenParameters instantViewFallbackUrl (String fallbackUrl) {
      this.instantViewFallbackUrl = fallbackUrl;
      return this;
    }
  }

  public void openUrlOptions (final ViewController<?> context, final String url, @Nullable UrlOpenParameters options) {
    context.showOptions(url, new int[] {R.id.btn_open, R.id.btn_copyLink}, new String[] {Lang.getString(R.string.Open), Lang.getString(R.string.CopyLink)}, null, new int[] {R.drawable.baseline_open_in_browser_24, R.drawable.baseline_content_copy_24}, (v, optionId) -> {
      switch (optionId) {
        case R.id.btn_open: {
          openUrl(context, url, options);
          break;
        }
        case R.id.btn_copyLink: {
          UI.copyText(url, R.string.CopiedLink);
          break;
        }
      }
      return true;
    });
  }

  private void openExternalUrl (final TdlibDelegate context, final String originalUrl, @Nullable UrlOpenParameters options) {
    if (options != null && options.messageId != null && ChatId.isSecret(options.messageId.getChatId())) {
      openUrlImpl(context, originalUrl, options);
      return;
    }
    tdlib.client().send(new TdApi.GetExternalLinkInfo(originalUrl), externalLinkInfoResult -> {
      switch (externalLinkInfoResult.getConstructor()) {
        case TdApi.LoginUrlInfoOpen.CONSTRUCTOR: {
          TdApi.LoginUrlInfoOpen open = (TdApi.LoginUrlInfoOpen) externalLinkInfoResult;
          if (options != null) {
            if (open.skipConfirm) {
              options.disableOpenPrompt();
            }
            options.displayUrl(originalUrl);
          }
          openUrlImpl(context, open.url, options);
          break;
        }
        case TdApi.LoginUrlInfoRequestConfirmation.CONSTRUCTOR: {
          TdApi.LoginUrlInfoRequestConfirmation confirm = (TdApi.LoginUrlInfoRequestConfirmation) externalLinkInfoResult;
          List<ListItem> items = new ArrayList<>();
          items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_MULTILINE,
            R.id.btn_signIn, 0,
            Lang.getString(R.string.LogInAsOn,
              (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 1 ?
                new CustomTypefaceSpan(null, R.id.theme_color_textLink) :
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
                .addHeaderItem(Lang.getString(R.string.OpenLinkConfirm, (target, argStart, argEnd, spanIndex, needFakeBold) -> new CustomTypefaceSpan(null, R.id.theme_color_textLink), confirm.url))
                .setRawItems(items)
                .setIntDelegate((id, result) -> {
                  boolean needSignIn = items.get(0).isSelected();
                  boolean needWriteAccess = items.size() > 1 && items.get(1).isSelected();
                  if (needSignIn) {
                    context.tdlib().client().send(
                      new TdApi.GetExternalLink(originalUrl, needWriteAccess), externalLinkResult -> {
                        switch (externalLinkResult.getConstructor()) {
                          case TdApi.HttpUrl.CONSTRUCTOR:
                            openUrlImpl(context, ((TdApi.HttpUrl) externalLinkResult).url, options != null ? options.disableOpenPrompt() : null);
                            break;
                          case TdApi.Error.CONSTRUCTOR:
                            openUrlImpl(context, originalUrl, options != null ? options.disableOpenPrompt() : null);
                            break;
                        }
                      });
                  } else {
                    openUrlImpl(context, originalUrl, options != null ? options.disableOpenPrompt() : null);
                  }
                })
                .setSettingProcessor((item, itemView, isUpdate) -> {
                  switch (item.getViewType()) {
                    case ListItem.TYPE_CHECKBOX_OPTION:
                    case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
                    case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
                      ((CheckBox) itemView.getChildAt(0)).setChecked(item.isSelected(), isUpdate);
                      break;
                  }
                })
                .setOnSettingItemClick(confirm.requestWriteAccess ? (itemView, settingsId, item, doneButton, settingsAdapter) -> {
                  switch (item.getId()) {
                    case R.id.btn_signIn: {
                      boolean needSignIn = settingsAdapter.getCheckIntResults().get(R.id.btn_signIn) == R.id.btn_signIn;
                      if (!needSignIn) {
                        items.get(1).setSelected(false);
                        settingsAdapter.updateValuedSettingById(R.id.btn_allowWriteAccess);
                      }
                      break;
                    }
                    case R.id.btn_allowWriteAccess: {
                      boolean needWriteAccess = settingsAdapter.getCheckIntResults().get(R.id.btn_allowWriteAccess) == R.id.btn_allowWriteAccess;
                      if (needWriteAccess) {
                        items.get(0).setSelected(true);
                        settingsAdapter.updateValuedSettingById(R.id.btn_signIn);
                      }
                      break;
                    }
                  }
                } : null)
                .setSaveStr(R.string.Open)
                .setRawItems(items)
            );
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          openUrlImpl(context, originalUrl, options);
          break;
        }
      }
    });
  }

  private void openUrlImpl (final TdlibDelegate context, final String url, @Nullable UrlOpenParameters options) {
    if (!UI.inUiThread()) {
      tdlib.ui().post(() -> openUrlImpl(context, url, options));
      return;
    }

    if (options != null && options.requireOpenPrompt) {
      ViewController<?> c = context instanceof ViewController<?> ? (ViewController<?>) context : context.context().navigation().getCurrentStackItem();
      if (c != null && !c.isDestroyed()) {
        AlertDialog.Builder b = new AlertDialog.Builder(context.context(), Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.AppName));
        b.setMessage(Lang.getString(R.string.OpenThisLink, !StringUtils.isEmpty(options.displayUrl) ? options.displayUrl : url));
        b.setPositiveButton(Lang.getString(R.string.Open), (dialog, which) ->
          tdlib.ui()
            .openExternalUrl(context, url, options.disableOpenPrompt())
        );
        b.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        c.showAlert(b);
      }
      return;
    }

    Uri uri = Strings.wrapHttps(url);
    if (uri == null) {
      UI.openUrl(url);
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

    final Uri uriFinal = uri;
    if (instantViewMode == INSTANT_VIEW_DISABLED && embedViewMode == EMBED_VIEW_DISABLED) {
      UI.openUrl(url);
      return;
    }

    final AtomicBoolean signal = new AtomicBoolean();
    final AtomicReference<TdApi.WebPage> foundWebPage = new AtomicReference<>();
    CancellableRunnable[] runnable = new CancellableRunnable[1];

    tdlib.client().send(new TdApi.GetWebPagePreview(new TdApi.FormattedText(url, null)), page -> {
      switch (page.getConstructor()) {
        case TdApi.WebPage.CONSTRUCTOR: {
          TdApi.WebPage webPage = (TdApi.WebPage) page;
          foundWebPage.set(webPage);
          if (instantViewMode == INSTANT_VIEW_DISABLED || !TD.hasInstantView(webPage.instantViewVersion) || TD.shouldInlineIv(webPage)) {
            post(runnable[0]);
            return;
          }
          tdlib.client().send(new TdApi.GetWebPageInstantView(url, false), preview -> {
            switch (preview.getConstructor()) {
              case TdApi.WebPageInstantView.CONSTRUCTOR: {
                TdApi.WebPageInstantView instantView = (TdApi.WebPageInstantView) preview;
                if (!TD.hasInstantView(instantView.version)) {
                  post(runnable[0]);
                  return;
                }
                post(() -> {
                  if (!signal.getAndSet(true)) {
                    runnable[0].cancel();

                    InstantViewController controller = new InstantViewController(context.context(), context.tdlib());
                    try {
                      controller.setArguments(new InstantViewController.Args(webPage, instantView, Uri.parse(url).getEncodedFragment()));
                      controller.show();
                    } catch (Throwable t) {
                      Log.e("Unable to open instantView, url:%s", t, url);
                      UI.showToast(R.string.InstantViewUnsupported, Toast.LENGTH_SHORT);
                      UI.openUrl(url);
                    }
                  }
                });
                break;
              }
              case TdApi.Error.CONSTRUCTOR: {
                post(runnable[0]);
                break;
              }
            }
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          post(runnable[0]);
          break;
        }
      }
    });
    runnable[0] = new CancellableRunnable() {
      @Override
      public void act () {
        if (!signal.getAndSet(true)) {
          if (tdlib.isKnownHost(uriFinal.getHost(), false)) {
            List<String> segments = uriFinal.getPathSegments();
            if (segments != null && segments.size() == 1 && "iv".equals(segments.get(0))) {
              String originalUrl = uriFinal.getQueryParameter("url");
              if (Strings.isValidLink(originalUrl)) {
                openUrl(context, originalUrl, new UrlOpenParameters(options).disableInstantView());
                return;
              }
            }
          }
          if (embedViewMode == EMBED_VIEW_ENABLED) {
            TdApi.WebPage webPage = foundWebPage.get();
            if (context instanceof ViewController<?> && webPage != null && PreviewLayout.show((ViewController<?>) context, webPage, isFromSecretChat)) {
              return;
            }
          }
          UI.openUrl(url);
        }
      }
    };
    runnable[0].removeOnCancel(UI.getAppHandler());
    UI.post(runnable[0], 2000);
  }

  public void openUrl (final TdlibDelegate context, final String url, @Nullable UrlOpenParameters options) {
    openTelegramUrl(context, url, options, processed -> {
      if (!processed) {
        openExternalUrl(context, url, options);
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
        int postId = StringUtils.parseInt(uri.getQueryParameter("post"));
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
        long supergroupId = StringUtils.parseInt(uri.getQueryParameter("channel"));
        int messageId = StringUtils.parseInt(uri.getQueryParameter("msg_id"));
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

    int postId = StringUtils.parseInt(pathArg);

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

  public void openTelegramUrl (final TdlibDelegate context, final String url, @Nullable UrlOpenParameters openParameters, @Nullable RunnableBool after) {
    if (StringUtils.isEmpty(url) || tdlib.context().inRecoveryMode()) {
      if (after != null)
        after.runWithBool(false);
      return;
    }
    tdlib.client().send(new TdApi.GetInternalLinkType(url), result -> {
      if (result instanceof TdApi.InternalLinkType) {
        TdApi.InternalLinkType linkType = (TdApi.InternalLinkType) result;
        post(() -> {
          if (context.context().navigation().isDestroyed())
            return;
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
              // TODO progress?
              ViewController<?> currentController = context.context().navigation().getCurrentStackItem();
              tdlib.client().send(new TdApi.SendPhoneNumberConfirmationCode(confirmPhone.hash, confirmPhone.phoneNumber, TD.defaultPhoneNumberAuthenticationSettings()), confirmationResult -> {
                switch (confirmationResult.getConstructor()) {
                  case TdApi.AuthenticationCodeInfo.CONSTRUCTOR: {
                    TdApi.AuthenticationCodeInfo info = (TdApi.AuthenticationCodeInfo) confirmationResult;
                    post(() -> {
                      if (currentController != null && !currentController.isDestroyed()) {
                        confirmPhone(context, info, confirmPhone.phoneNumber);
                      }
                    });
                    break;
                  }
                  case TdApi.Error.CONSTRUCTOR: {
                    showLinkTooltip(tdlib, R.drawable.baseline_warning_24, TD.toErrorString(confirmationResult), openParameters);
                    break;
                  }
                }
              });
              break;
            }
            case TdApi.InternalLinkTypeProxy.CONSTRUCTOR: {
              TdApi.InternalLinkTypeProxy proxy = (TdApi.InternalLinkTypeProxy) linkType;
              openProxyAlert(context, proxy.server, proxy.port, proxy.type, newProxyDescription(proxy.server, Integer.toString(proxy.port)).toString());
              break;
            }
            case TdApi.InternalLinkTypePublicChat.CONSTRUCTOR: {
              TdApi.InternalLinkTypePublicChat publicChat = (TdApi.InternalLinkTypePublicChat) linkType;
              if (TdConstants.IV_PREVIEW_USERNAME.equals(publicChat.chatUsername)) {
                openExternalUrl(context, url, new UrlOpenParameters(openParameters).forceInstantView());
              } else {
                openPublicChat(context, publicChat.chatUsername, openParameters);
              }
              break;
            }
            case TdApi.InternalLinkTypeVoiceChat.CONSTRUCTOR: {
              TdApi.InternalLinkTypeVoiceChat voiceChatInvitation = (TdApi.InternalLinkTypeVoiceChat) linkType;
              openVoiceChat(context, voiceChatInvitation, openParameters);
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
            case TdApi.InternalLinkTypeGame.CONSTRUCTOR: {
              TdApi.InternalLinkTypeGame game = (TdApi.InternalLinkTypeGame) linkType;
              startBot(context, game.botUsername, game.gameShortName, BOT_MODE_START_GAME, openParameters);
              break;
            }
            case TdApi.InternalLinkTypeSettings.CONSTRUCTOR: {
              SettingsController c = new SettingsController(context.context(), context.tdlib());
              context.context().navigation().navigateTo(c);
              break;
            }
            case TdApi.InternalLinkTypeThemeSettings.CONSTRUCTOR: {
              SettingsThemeController c = new SettingsThemeController(context.context(), context.tdlib());
              c.setArguments(new SettingsThemeController.Args(SettingsThemeController.MODE_THEMES));
              context.context().navigation().navigateTo(c);
              break;
            }
            case TdApi.InternalLinkTypeActiveSessions.CONSTRUCTOR: {
              SettingsSessionsController sessions = new SettingsSessionsController(context.context(), context.tdlib());
              SettingsWebsitesController websites = new SettingsWebsitesController(context.context(), context.tdlib());
              ViewController<?> c = new SimpleViewPagerController(context.context(), context.tdlib(), new ViewController[] {sessions, websites}, new String[] {Lang.getString(R.string.Devices).toUpperCase(), Lang.getString(R.string.Websites).toUpperCase()}, false);
              context.context().navigation().navigateTo(c);
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
              tdlib.client().send(new TdApi.SearchBackground(background.backgroundName), backgroundObj -> {
                switch (backgroundObj.getConstructor()) {
                  case TdApi.Background.CONSTRUCTOR: {
                    TdApi.Background wallpaper = (TdApi.Background) backgroundObj;

                    post(() -> {
                      MessagesController c = new MessagesController(context.context(), context.tdlib());
                      c.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_WALLPAPER_OBJECT, null, null).setWallpaperObject(wallpaper));
                      context.context().navigation().navigateTo(c);

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
              return;
            }
            case TdApi.InternalLinkTypeFilterSettings.CONSTRUCTOR: {
              // TODO show chat folders screen
              ok = false;
              break;
            }
            case TdApi.InternalLinkTypePassportDataRequest.CONSTRUCTOR: {
              TdApi.InternalLinkTypePassportDataRequest passportData = (TdApi.InternalLinkTypePassportDataRequest) linkType;
              // TODO ?
              break;
            }
            case TdApi.InternalLinkTypeUnknownDeepLink.CONSTRUCTOR: {
              // TODO progress
              tdlib.client().send(new TdApi.GetDeepLinkInfo(url), deepLinkResult -> {
                switch (deepLinkResult.getConstructor()) {
                  case TdApi.DeepLinkInfo.CONSTRUCTOR: {
                    TdApi.DeepLinkInfo deepLink = (TdApi.DeepLinkInfo) deepLinkResult;
                    post(() -> {
                      ViewController<?> c = context.context().navigation().getCurrentStackItem();
                      if (c != null) {
                        c.processDeepLinkInfo(deepLink);
                      }
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
            default: {
              ok = false;
              break;
            }
          }
          if (after != null) {
            after.runWithBool(ok);
          }
        });
      } else if (after != null) {
        post(() -> after.runWithBool(false));
      }
    });
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
    colors.append(ViewController.OPTION_COLOR_BLUE);

    ids.append(R.id.btn_save);
    strings.append(R.string.ProxySaveForLater);
    icons.append(R.drawable.baseline_playlist_add_24);
    colors.append(ViewController.OPTION_COLOR_NORMAL);

    ids.append(R.id.btn_cancel);
    strings.append(R.string.Cancel);
    icons.append(R.drawable.baseline_cancel_24);
    colors.append(ViewController.OPTION_COLOR_NORMAL);

    c.showOptions(msg, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_addProxy:
          Settings.instance().addOrUpdateProxy(server, port, type, null, true);
          break;
        case R.id.btn_save:
          Settings.instance().addOrUpdateProxy(server, port, type, null, false);
          break;
      }
      return true;
    });
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
      case Tdlib.STATE_CONNECTED:
        return R.string.Connected;
      case Tdlib.STATE_CONNECTING:
      case Tdlib.STATE_UNKNOWN:
        return R.string.network_Connecting;
      case Tdlib.STATE_CONNECTING_TO_PROXY:
        return R.string.ConnectingToProxy;
      case Tdlib.STATE_WAITING:
        return R.string.network_WaitingForNetwork;
      case Tdlib.STATE_UPDATING:
        return R.string.network_Updating;
    }
    throw new RuntimeException();
  }

  // Map

  public boolean openMap (TdlibDelegate context, MapController.Args args) {
    if (!U.isGooglePlayServicesAvailable(context.context())) {
      return Intents.openMap(args.latitude, args.longitude, args.title, args.address);
    }
    MapGoogleController c = new MapGoogleController(context.context(), context.tdlib());
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
      switch (id) {
        case R.id.btn_proxySocks5: {
          EditProxyController e = new EditProxyController(context.context(), context.tdlib());
          e.setArguments(new EditProxyController.Args(EditProxyController.MODE_SOCKS5));
          c.navigateTo(e);
          break;
        }
        case R.id.btn_proxyTelegram: {
          EditProxyController e = new EditProxyController(context.context(), context.tdlib());
          e.setArguments(new EditProxyController.Args(EditProxyController.MODE_MTPROTO));
          c.navigateTo(e);
          break;
        }
        case R.id.btn_proxyHttp: {
          EditProxyController e = new EditProxyController(context.context(), context.tdlib());
          e.setArguments(new EditProxyController.Args(EditProxyController.MODE_HTTP));
          c.navigateTo(e);
          break;
        }
        case R.id.btn_proxyQr: {
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
          break;
        }
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
    switch (actionId) {
      case R.id.btn_returnToChat:
        leaveJoinChat(context, chatId, true, after);
        return true;
      case R.id.btn_removePsaChatFromList:
        showHidePsaConfirm(context, chatList, chatId, after);
        return true;
      case R.id.btn_removeChatFromList:
      case R.id.btn_removeChatFromListAndStop:
        showDeleteChatConfirm(context, chatId, false, actionId == R.id.btn_removeChatFromListAndStop, after);
        return true;
      case R.id.btn_removeChatFromListOrClearHistory:
        showDeleteChatConfirm(context, chatId, true, false, after);
        return true;
      case R.id.btn_clearChatHistory:
        showClearHistoryConfirm(context, chatId, after);
        return true;
      case R.id.btn_setPasscode:
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
        SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_removeChatFromList).setSaveStr(R.string.LeaveDoneGroup).setSaveColorId(R.id.theme_color_textNegative);
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
        context.showOptions(informationStr, new int[]{checkId, R.id.btn_cancel}, new String[]{Lang.getString(confirmButtonRes), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
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

  private @StringRes int getDeleteChatStringRes (long chatId) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null)
      return R.string.DeleteChat;
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        return tdlib.suggestStopBot(chat) ? R.string.DeleteAndStop : R.string.DeleteChat;
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
    if (tdlib.canRevokeChat(chatId)) {
      context.showSettings(new SettingsWrapBuilder(R.id.btn_removeChatFromList)
        .setAllowResize(false)
        .addHeaderItem(tdlib.isSelfChat(chatId) ? Lang.getMarkdownString(context, R.string.ClearSavedMessagesConfirm) : Lang.getString(R.string.ClearHistoryConfirm))
        .setRawItems(new ListItem[] {
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_clearChatHistory, 0, Lang.getString(R.string.DeleteSecretChatHistoryForOtherParty, tdlib.cache().userFirstName(ChatId.toUserId(chatId))), R.id.btn_clearChatHistory, tdlib.isUserChat(chatId) && tdlib.cache().userDeleted(tdlib.chatUserId(chatId)))
        })
        .setSaveColorId(R.id.theme_color_textNegative)
        .setSaveStr(R.string.Delete)
        .setIntDelegate((id, result) -> {
          boolean clearHistory = result.get(R.id.btn_clearChatHistory) == R.id.btn_clearChatHistory;
          tdlib.client().send(new TdApi.DeleteChatHistory(chatId, false, clearHistory), tdlib.okHandler());
          U.run(after);
        })
      );
    } else {
      context.showOptions(
        tdlib.isSelfChat(chatId) ? Lang.getMarkdownString(context, R.string.ClearSavedMessagesConfirm) : Lang.getString(R.string.ClearHistoryConfirm),
        new int[]{R.id.btn_clearChatHistory, R.id.btn_cancel},
        new String[]{Lang.getString(R.string.ClearHistory), Lang.getString(R.string.Cancel)},
        new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL},
        new int[]{R.drawable.templarian_baseline_broom_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_clearChatHistory) {
            tdlib.client().send(new TdApi.DeleteChatHistory(chatId, false, false), tdlib.okHandler());
            U.run(after);
          }
          return true;
        });
    }
  }

  public void showDeleteChatConfirm (final ViewController<?> context, final long chatId) {
    showDeleteChatConfirm(context, chatId, false, tdlib.suggestStopBot(chatId), null);
  }

  private void showDeleteOrClearHistory (final ViewController<?> context, final long chatId, final CharSequence chatName, final Runnable onDelete, final boolean allowClearHistory, Runnable after) {
    if (!allowClearHistory || !tdlib.canClearHistory(chatId)) {
      onDelete.run();
      return;
    }
    context.showOptions(chatName, new int[] {R.id.btn_removeChatFromList, R.id.btn_clearChatHistory}, new String[] {Lang.getString(getDeleteChatStringRes(chatId)), Lang.getString(R.string.ClearHistory)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.templarian_baseline_broom_24}, (itemView, id) -> {
      switch (id) {
        case R.id.btn_removeChatFromList:
          onDelete.run();
          break;
        case R.id.btn_clearChatHistory:
          showClearHistoryConfirm(context, chatId, after);
          break;
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
      new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL},
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
      exitToChatScreen(context, chatId);
      U.run(after);
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
              .setSaveColorId(R.id.theme_color_textNegative)
              .setSaveStr(R.string.Delete)
              .setIntDelegate((id, result) -> {
                boolean clearHistory = result.get(R.id.btn_clearChatHistory) == R.id.btn_clearChatHistory;
                if (blockUser) {
                  tdlib.blockSender(new TdApi.MessageSenderUser(userId), true, blockResult -> deleter.runWithBool(clearHistory));
                } else {
                  deleter.runWithBool(clearHistory);
                }
              })
            );
          } else {
            context.showOptions(info, new int[] {R.id.btn_removeChatFromList, R.id.btn_cancel}, new String[] {Lang.getString(deleteAndStop ? R.string.DeleteAndStop : R.string.DeleteChat), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, resultId) -> {
              if (resultId == R.id.btn_removeChatFromList) {
                if (blockUser) {
                  tdlib.blockSender(new TdApi.MessageSenderUser(userId), true, blockResult -> deleter.runWithBool(false));
                } else {
                  deleter.runWithBool(false);
                }
              }
              return true;
            });
          }
        }, allowClearHistory, after);
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
              .setSaveColorId(R.id.theme_color_textNegative)
              .setSaveStr(R.string.Delete)
              .setIntDelegate((id, result) -> {
                boolean clearHistory = result.get(R.id.btn_clearChatHistory) == R.id.btn_clearChatHistory;
                if (clearHistory) {
                  tdlib.client().send(new TdApi.DeleteChatHistory(chatId, false, false), object -> {
                    if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
                      Log.e("Cannot clear secret chat history, secretChatId:%d, error: %s", ChatId.toSecretChatId(chatId), TD.toErrorString(object));
                    }
                    deleter.runWithBool(false);
                  });
                } else {
                  deleter.runWithBool(false);
                }
              })
            );
          } else {
            context.showOptions(Lang.getStringBold(secretChat.state.getConstructor() == TdApi.SecretChatStatePending.CONSTRUCTOR ? R.string.DeleteSecretChatPendingConfirm : R.string.DeleteSecretChatClosedConfirm, userName), new int[] {R.id.btn_removeChatFromList, R.id.btn_cancel}, new String[]{Lang.getString(R.string.DeleteChat), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
              if (id == R.id.btn_removeChatFromList) {
                if (blockUser) {
                  tdlib.blockSender(new TdApi.MessageSenderUser(secretChat.userId), true, blockResult -> deleter.runWithBool(false));
                } else {
                  deleter.runWithBool(false);
                }
              }
              return true;
            });
          }
        }, allowClearHistory, after);
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          showDeleteOrClearHistory(context, chatId, tdlib.chatTitle(chatId), () -> {
            TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
            if (status != null && TD.isMember(status, false)) {
              leaveJoinChat(context, chatId, false, after);
            } else {
              context.showOptions(Lang.getString(R.string.AreYouSureDeleteThisChat), new int[] {R.id.btn_removeChatFromList, R.id.btn_cancel}, new String[] {Lang.getString(R.string.DeleteChat), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                if (id == R.id.btn_removeChatFromList) {
                  deleter.runWithBool(false);
                }
                return true;
              });
            }
          }, allowClearHistory, after);
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        showDeleteOrClearHistory(context, chatId, tdlib.chatTitle(chatId), () -> leaveJoinChat(context, chatId, false, after), allowClearHistory, after);
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
      switch (optionId) {
        case R.id.btn_markChatAsRead: {
          tdlib.readAllChats(new TdApi.ChatListArchive(), readCount -> UI.showToast(Lang.plural(R.string.ReadAllChatsDone, readCount), Toast.LENGTH_SHORT));
          break;
        }
        case R.id.btn_pinUnpinChat: {
          tdlib.settings().toggleUserPreference(TdlibSettingsManager.PREFERENCE_HIDE_ARCHIVE);
          break;
        }
      }
      return true;
    });
  }

  public void showChatOptions (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, final ThreadInfo messageThread, boolean canSelect, boolean isSelected, @Nullable Runnable onSelect) {
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

    final int size = canSelect && onSelect != null ? 6 : 5;

    IntList ids = new IntList(size);
    StringList strings = new StringList(size);
    IntList colors = new IntList(size);
    IntList icons = new IntList(size);

    if (canSelect && onSelect != null) {
      ids.append(R.id.btn_selectChat);
      strings.append(isSelected ? R.string.Unselect : R.string.Select);
      colors.append(ViewController.OPTION_COLOR_NORMAL);
      icons.append(R.drawable.baseline_playlist_add_check_24);
    }

    if (!tdlib.isSelfChat(chatId)) {
      ids.append(R.id.btn_notifications);
      strings.append(hasNotifications ? R.string.MuteNotifications : R.string.EnableNotifications);
      colors.append(ViewController.OPTION_COLOR_NORMAL);
      icons.append(hasNotifications ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
    }

    if (position != null) {
      ids.append(position.isPinned ? R.id.btn_unpinChat : R.id.btn_pinChat);
      strings.append(position.isPinned ? R.string.UnpinFromTop : R.string.PinToTop);
      colors.append(ViewController.OPTION_COLOR_NORMAL);
      icons.append(position.isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24);
    }

    if (tdlib.canArchiveChat(chatList, chat)) {
      boolean isArchived = chatList instanceof TdApi.ChatListArchive;
      ids.append(isArchived ? R.id.btn_unarchiveChat : R.id.btn_archiveChat);
      strings.append(isArchived ? R.string.UnarchiveChat : R.string.ArchiveChat);
      colors.append(ViewController.OPTION_COLOR_NORMAL);
      icons.append(isArchived ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24);
    }

    boolean hasPasscode = tdlib.hasPasscode(chat);

    boolean canRead = tdlib.canMarkAsRead(chat);
    if (!canRead || chat.unreadCount == 0 || !hasPasscode) { // when passcode is set, "mark as read" is unavailable, when there are some unread messages
      ids.append(canRead ? R.id.btn_markChatAsRead : R.id.btn_markChatAsUnread);
      strings.append(canRead ? R.string.MarkAsRead : R.string.MarkAsUnread);
      colors.append(ViewController.OPTION_COLOR_NORMAL);
      icons.append(canRead ? Config.ICON_MARK_AS_READ : Config.ICON_MARK_AS_UNREAD);
    }

    if (!hasPasscode && tdlib.canClearHistory(chat)) {
      ids.append(R.id.btn_clearChatHistory);
      strings.append(R.string.ClearHistory);
      colors.append(ViewController.OPTION_COLOR_NORMAL);
      icons.append(R.drawable.templarian_baseline_broom_24);
    }

    colors.append(ViewController.OPTION_COLOR_RED);
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
      return processChatAction(context, chatList, chatId, messageThread, id, null);
    });
  }

  private void showPinUnpinConfirm (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, @Nullable Runnable after) {
    boolean isPinned = tdlib.chatPinned(chatList, chatId);
    context.showOptions(tdlib.chatTitle(chatId), new int[] {isPinned ? R.id.btn_unpinChat : R.id.btn_pinChat, R.id.btn_cancel}, new String[] {Lang.getString(isPinned ? R.string.UnpinFromTop : R.string.PinToTop), Lang.getString(R.string.Cancel)}, null, new int[] {isPinned ? R.drawable.deproko_baseline_pin_undo_24 : R.drawable.deproko_baseline_pin_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_unpinChat || id == R.id.btn_pinChat) {
        processChatAction(context, chatList, chatId, null, id, after);
      }
      return true;
    });
  }

  private void showArchiveUnarchiveChat (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, @Nullable Runnable after) {
    boolean isArchived = tdlib.chatArchived(chatId);
    context.showOptions(tdlib.chatTitle(chatId), new int[] {isArchived ? R.id.btn_unarchiveChat : R.id.btn_archiveChat, R.id.btn_cancel}, new String[] {Lang.getString(isArchived ? R.string.UnarchiveChat : R.string.ArchiveChat), Lang.getString(R.string.Cancel)}, null, new int[] {isArchived ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_unarchiveChat || id == R.id.btn_archiveChat) {
        processChatAction(context, chatList, chatId, null, id, after);
      }
      return true;
    });
  }

  public void showInviteLinkOptionsPreload (ViewController<?> context, final TdApi.ChatInviteLink link, final long chatId, final boolean showNavigatingToLinks, @Nullable Runnable onLinkDeleted, @Nullable RunnableData<TdApi.ChatInviteLinks> onLinkRevoked) {
    context.tdlib().send(new TdApi.GetChatInviteLink(chatId, link.inviteLink), result -> {
      context.runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.ChatInviteLink.CONSTRUCTOR) {
          showInviteLinkOptions(context, (TdApi.ChatInviteLink) result, chatId, showNavigatingToLinks, false, onLinkDeleted, onLinkRevoked);
        } else {
          showInviteLinkOptions(context, link, chatId, showNavigatingToLinks, true, onLinkDeleted, onLinkRevoked);
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
      colors.append(ViewController.OPTION_COLOR_NORMAL);
    }

    if (showNavigatingToLinks && tdlib.canManageInviteLinks(chat)) {
      ids.append(R.id.btn_manageInviteLinks);
      strings.append(R.string.InviteLinkManage);
      icons.append(R.drawable.baseline_add_link_24);
      colors.append(ViewController.OPTION_COLOR_NORMAL);
    }

    if (!deleted && !link.isRevoked) {
      if (!link.isPrimary && context instanceof ChatLinksController) {
        ids.append(R.id.btn_edit);
        strings.append(R.string.InviteLinkEdit);
        icons.append(R.drawable.baseline_edit_24);
        colors.append(ViewController.OPTION_COLOR_NORMAL);
      }

      ids.append(R.id.btn_copyLink);
      strings.append(R.string.InviteLinkCopy);
      icons.append(R.drawable.baseline_content_copy_24);
      colors.append(ViewController.OPTION_COLOR_NORMAL);

      ids.append(R.id.btn_shareLink);
      strings.append(R.string.ShareLink);
      icons.append(R.drawable.baseline_forward_24);
      colors.append(ViewController.OPTION_COLOR_NORMAL);

      icons.append(R.drawable.baseline_link_off_24);
      ids.append(R.id.btn_revokeLink);
      strings.append(R.string.RevokeLink);
      colors.append(ViewController.OPTION_COLOR_RED);
    } else {
      ids.append(R.id.btn_copyLink);
      strings.append(R.string.InviteLinkCopy);
      icons.append(R.drawable.baseline_content_copy_24);
      colors.append(ViewController.OPTION_COLOR_NORMAL);

      if (!deleted) {
        icons.append(R.drawable.baseline_delete_24);
        ids.append(R.id.btn_deleteLink);
        strings.append(R.string.InviteLinkDelete);
        colors.append(ViewController.OPTION_COLOR_RED);
      }
    }

    CharSequence info = TD.makeClickable(Lang.getString(R.string.CreatedByXOnDate, ((target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newUserSpan(new TdlibContext(context.context(), context.tdlib()), link.creatorUserId) : null), context.tdlib().cache().userName(link.creatorUserId), Lang.getRelativeTimestamp(link.date, TimeUnit.SECONDS)));
    Lang.SpanCreator firstBoldCreator = (target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newBoldSpan(needFakeBold) : null;
    context.showOptions(Lang.getString(R.string.format_nameAndStatus, firstBoldCreator, link.inviteLink, info), ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_viewInviteLinkMembers:
          ChatLinkMembersController c2 = new ChatLinkMembersController(context.context(), context.tdlib());
          c2.setArguments(new ChatLinkMembersController.Args(chatId, link.inviteLink));
          context.navigateTo(c2);
          break;
        case R.id.btn_edit:
          EditChatLinkController c = new EditChatLinkController(context.context(), context.tdlib());
          c.setArguments(new EditChatLinkController.Args(link, chatId, (ChatLinksController) context));
          context.navigateTo(c);
          break;
        case R.id.btn_manageInviteLinks:
          ChatLinksController cc = new ChatLinksController(context.context(), context.tdlib());
          cc.setArguments(new ChatLinksController.Args(chatId, context.tdlib().myUserId(), null, null, tdlib.chatStatus(chatId).getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR));
          context.navigateTo(cc);
          break;
        case R.id.btn_copyLink:
          UI.copyText(link.inviteLink, R.string.CopiedLink);
          break;
        case R.id.btn_shareLink:
          String chatName = context.tdlib().chatTitle(chatId);
          String exportText = Lang.getString(context.tdlib().isChannel(chatId) ? R.string.ShareTextChannelLink : R.string.ShareTextChatLink, chatName, link.inviteLink);
          String text = Lang.getString(R.string.ShareTextLink, chatName, link.inviteLink);
          ShareController sc = new ShareController(context.context(), context.tdlib());
          sc.setArguments(new ShareController.Args(text).setShare(exportText, null));
          sc.show();
          break;
        case R.id.btn_deleteLink:
          context.showOptions(Lang.getString(R.string.AreYouSureDeleteInviteLink), new int[]{R.id.btn_deleteLink, R.id.btn_cancel}, new String[]{Lang.getString(R.string.InviteLinkDelete), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
            if (id2 == R.id.btn_deleteLink) {
              if (onLinkDeleted != null) onLinkDeleted.run();
              context.tdlib().client().send(new TdApi.DeleteRevokedChatInviteLink(chatId, link.inviteLink), null);
            }

            return true;
          });
          break;
        case R.id.btn_revokeLink:
          context.showOptions(Lang.getString(context.tdlib().isChannel(chatId) ? R.string.AreYouSureRevokeInviteLinkChannel : R.string.AreYouSureRevokeInviteLinkGroup), new int[]{R.id.btn_revokeLink, R.id.btn_cancel}, new String[]{Lang.getString(R.string.RevokeLink), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_link_off_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
            if (id2 == R.id.btn_revokeLink) {
              context.tdlib().client().send(new TdApi.RevokeChatInviteLink(chatId, link.inviteLink), result -> {
                if (result.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR && onLinkRevoked != null) {
                  context.runOnUiThreadOptional(() -> onLinkRevoked.runWithData((TdApi.ChatInviteLinks) result));
                }
              });
            }

            return true;
          });
          break;
      }

      return true;
    });
  }

  public boolean processChatAction (ViewController<?> context, final TdApi.ChatList chatList, final long chatId, final @Nullable ThreadInfo messageThread, final int actionId, @Nullable Runnable after) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null)
      return false;
    switch (actionId) {
      case R.id.btn_notifications:
        tdlib.ui().toggleMute(context, chatId, false, after);
        return true;
      case R.id.btn_pinUnpinChat:
        showPinUnpinConfirm(context, chatList, chatId, after);
        return true;
      case R.id.btn_unpinChat:
        tdlib.client().send(new TdApi.ToggleChatIsPinned(chatList, chatId, false), tdlib.okHandler(after));
        return true;
      case R.id.btn_pinChat:
        tdlib.client().send(new TdApi.ToggleChatIsPinned(chatList, chatId, true), tdlib.okHandler(after));
        return true;
      case R.id.btn_archiveUnarchiveChat:
        showArchiveUnarchiveChat(context, chatList, chatId, after);
        return true;
      case R.id.btn_archiveChat:
        tdlib.client().send(new TdApi.AddChatToList(chatId, ChatPosition.CHAT_LIST_ARCHIVE), tdlib.okHandler(after));
        return true;
      case R.id.btn_unarchiveChat:
        tdlib.client().send(new TdApi.AddChatToList(chatId, ChatPosition.CHAT_LIST_MAIN), tdlib.okHandler(after));
        return true;
      case R.id.btn_markChatAsRead:
        if (messageThread != null) {
          tdlib.markChatAsRead(messageThread.getChatId(), messageThread.getMessageThreadId(), after);
        } else {
          tdlib.markChatAsRead(chat.id, 0, after);
        }
        return true;
      case R.id.btn_markChatAsUnread:
        tdlib.markChatAsUnread(chat, after);
        return true;
      case R.id.btn_phone_call:
        tdlib.context().calls().makeCallDelayed(context, TD.getUserId(chat), null, true);
        return true;
      default:
        return processLeaveButton(context, chatList, chatId, actionId, after);
    }
  }

  public final ForceTouchView.ActionListener createSimpleChatActions (final ViewController<?> context, final TdApi.ChatList chatList, final long chatId, final @Nullable ThreadInfo messageThread, IntList ids, IntList icons, StringList strings, final boolean allowInteractions, final boolean canSelect, final boolean isSelected, @Nullable Runnable onSelect) {
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

        if (tdlib.canArchiveChat(chatList, chat)) {
          ids.append(R.id.btn_archiveUnarchiveChat);
          strings.append(chatList instanceof TdApi.ChatListArchive ? R.string.Unarchive : R.string.Archive);
          icons.append(chatList instanceof TdApi.ChatListArchive ? R.drawable.baseline_unarchive_24 : R.drawable.baseline_archive_24);
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
          processChatAction(context, chatList, chatId, messageThread, actionId, null);
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
      switch (result.get(R.id.btn_mapProvider)) {
        case R.id.btn_mapProviderGoogle:
          resultType = Settings.MAP_PROVIDER_GOOGLE;
          break;
        case R.id.btn_mapProviderTelegram:
          resultType = Settings.MAP_PROVIDER_TELEGRAM;
          break;
        case R.id.btn_mapProviderNone:
          resultType = Settings.MAP_PROVIDER_NONE;
          break;
        default:
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
        switch (id) {
          case R.id.btn_done:
            break;
          case R.id.btn_settings:
            context.navigateTo(new SettingsLanguageController(c.context(), c.tdlib()));
            break;
        }
        return true;
      });
      return;
    }
    CharSequence text = Strings.buildMarkdown(c, Lang.getString(info.isOfficial ? R.string.LanguageAlert : R.string.LanguageCustomAlert, info.name, (int) Math.floor((float) info.translatedStringCount / (float) info.totalStringCount * 100f), info.translationUrl), null);
    context.showOptions(text, new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.LanguageChange), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_BLUE, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_language_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      switch (id) {
        case R.id.btn_done: {
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
          break;
        }
      }
      return true;
    });
  }

  public void showLanguageInstallPrompt (ViewController<?> c, CustomLangPackResult out, TdApi.Message sourceMessage) {
    if (sourceMessage != null) {
      TdApi.Chat sourceChat;
      if (sourceMessage.forwardInfo != null && sourceMessage.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginChannel.CONSTRUCTOR) {
        sourceChat = tdlib.chat(((TdApi.MessageForwardOriginChannel) sourceMessage.forwardInfo.origin).chatId);
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
      new int[] {R.id.btn_messageApplyLocalization, R.id.btn_cancel}, new String[] {Lang.getString(R.string.LanguageInstall), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_BLUE, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_language_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
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
    context.showOptions(Lang.getStringBold(isSignOut ? R.string.SignOutHint2 : R.string.RemoveAccountHint2, account.getName()), new int[]{R.id.btn_removeAccount, R.id.btn_cancel}, new String[]{Lang.getString(R.string.LogOut), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
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
        if (!tdlib.hasWritePermission(chat)) {
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
    if (!ThemeManager.isCustomTheme(theme.getId()))
      return;
    context.showOptions(Lang.getString(R.string.ThemeRemoveInfo), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ThemeRemoveConfirm), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
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
      TextView textView = SettingHolder.createDescription(context.context(), ListItem.TYPE_DESCRIPTION, R.id.theme_color_textLight, null, context);
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
      if (id == R.id.theme_property_parentTheme)
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
        if (value.id != ThemeProperty.PARENT_THEME && defaultTheme.getProperty(value.id) == value.floatValue) {
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
              info = Lang.getString(R.string.ThemeInstallAuthor, (target, argStart, argEnd, argIndex, fakeBold) -> argIndex == 1 ? new CustomTypefaceSpan(null, R.id.theme_color_textLink).setEntityType(new TdApi.TextEntityTypeMention()).setForcedTheme(theme) : Lang.newBoldSpan(fakeBold), theme.name, "@" + theme.author);
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
            colors.append(ViewController.OPTION_COLOR_BLUE);
            strings.append(R.string.ThemeInstallDone);

            if (onError != null) {
              ids.append(R.id.btn_open);
              icons.append(R.drawable.baseline_open_in_browser_24);
              colors.append(ViewController.OPTION_COLOR_NORMAL);
              strings.append(R.string.Open);
            }

            ids.append(R.id.btn_cancel);
            icons.append(R.drawable.baseline_cancel_24);
            colors.append(ViewController.OPTION_COLOR_NORMAL);
            strings.append(R.string.Cancel);

            if (info != null) {
              SpannableStringBuilder b = info instanceof SpannableStringBuilder ? (SpannableStringBuilder) info : new SpannableStringBuilder(info);
              b.append("\n\n");
              b.append(Lang.getString(R.string.ThemeInstallHint));
            }

            context.showOptions(info, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
              switch (id) {
                case R.id.btn_done: {
                  tdlib.wallpaper().loadWallpaper(theme.wallpaper, 1000, () -> {
                    context.tdlib().ui().installTheme(context, theme);
                  });
                  break;
                }
                case R.id.btn_open:
                  U.run(onError);
                  break;
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

  public static void reportChats (ViewController<?> context, long[] chatIds, Runnable after) {
    Tdlib tdlib = context.tdlib();

    IntList ids = new IntList(REPORT_REASON_COUNT);
    StringList strings = new StringList(REPORT_REASON_COUNT);
    fillReportReasons(ids, strings);

    CharSequence title = Lang.pluralBold(R.string.ReportXChats, chatIds.length);
    context.showOptions(title, ids.get(), strings.get(), /*colors.get()*/ null, null, (itemView, id) -> {
      toReportReasons(context, id, title, 0, null, false, request -> {
        AtomicInteger remaining = new AtomicInteger(chatIds.length);
        for (long chatId : chatIds) {
          tdlib.client().send(new TdApi.ReportChat(chatId, null, request.reason, request.text), object -> {
            switch (object.getConstructor()) {
              case TdApi.Ok.CONSTRUCTOR:
                if (remaining.decrementAndGet() == 0) {
                  UI.showToast(Lang.plural(R.string.ReportedXChats, chatIds.length), Toast.LENGTH_SHORT);
                  if (after != null) {
                    tdlib.ui().post(after);
                  }
                }
                break;
              case TdApi.Error.CONSTRUCTOR:
                UI.showError(object);
                break;
            }
          });
        }
      });
      return true;
    }, null);
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

    ids.append(R.id.btn_reportChatOther);
    // colors.append(ViewController.OPTION_COLOR_NORMAL);
    strings.append(R.string.Other);
  }

  private static void toReportReasons (ViewController<?> context, int reportReasonId, CharSequence title, long chatId, long[] messageIds, boolean forceText, RunnableData<TdApi.ReportChat> reportCallback) {
    final TdApi.ChatReportReason reason;
    switch (reportReasonId) {
      case R.id.btn_reportChatSpam:
        reason = new TdApi.ChatReportReasonSpam();
        break;
      case R.id.btn_reportChatFake:
        reason = new TdApi.ChatReportReasonFake();
        break;
      case R.id.btn_reportChatViolence:
        reason = new TdApi.ChatReportReasonViolence();
        break;
      case R.id.btn_reportChatPornography:
        reason = new TdApi.ChatReportReasonPornography();
        break;
      case R.id.btn_reportChatCopyright:
        reason = new TdApi.ChatReportReasonCopyright();
        break;
      case R.id.btn_reportChatChildAbuse:
        reason = new TdApi.ChatReportReasonChildAbuse();
        break;
      case R.id.btn_reportChatOther:
        // TODO replace with openInputAlert
        reason = new TdApi.ChatReportReasonCustom();
        forceText = true;
        break;
      default:
        throw new IllegalArgumentException(Lang.getResourceEntryName(reportReasonId));
    }
    final TdApi.ReportChat request = new TdApi.ReportChat(chatId, messageIds, reason, null);
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
          request.text = input;
          callback.runWithBool(true);
          reportCallback.runWithData(request);
        }
      });
      context.context().navigation().navigateTo(c);
    } else {
      reportCallback.runWithData(request);
    }
  }

  public static void reportChat (ViewController<?> context, long chatId, @Nullable TdApi.Message[] messages, boolean allowOther, Runnable after, ThemeDelegate forcedTheme) {
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
        if (senderId != 0) {
          title = Lang.getStringBold(ChatId.isUserChat(senderId) ? (messages.length == 1 ? R.string.ReportMessageUser : R.string.ReportMessagesUser) : (messages.length == 1 ? R.string.ReportMessage : R.string.ReportMessages), tdlib.chatTitle(senderId));
        } else {
          title = Lang.getStringBold(messages.length == 1 ? R.string.ReportMessage : R.string.ReportMessages, tdlib.chatTitle(messages[0].chatId));
        }
      } else {
        title = Lang.plural(R.string.ReportXMessages, messages.length, Lang.boldCreator());
      }
    } else {
      messageIds = null;
      title = Lang.getStringBold(R.string.ReportChat, tdlib.chatTitle(chatId));
    }

    IntList ids = new IntList(REPORT_REASON_COUNT);
    StringList strings = new StringList(REPORT_REASON_COUNT);
    fillReportReasons(ids, strings);

    context.showOptions(title, ids.get(), strings.get(), /*colors.get()*/ null, null, (itemView, id) -> {
      toReportReasons(context, id, title, chatId, messageIds, false, request -> {
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
    void onSendRequested (boolean forceDisableNotification, TdApi.MessageSchedulingState schedulingState, boolean disableMarkdown);
  }

  public HapticMenuHelper createSimpleHapticMenu (ViewController<?> context, long chatId, @Nullable FutureBool availabilityCallback, @Nullable FutureBool canDisableMarkdownCallback, RunnableData<List<HapticMenuHelper.MenuItem>> customItemProvider, SimpleSendCallback sendCallback, @Nullable ThemeDelegate forcedTheme) {
    return new HapticMenuHelper(list -> {
      if (availabilityCallback == null || availabilityCallback.get()) {
        List<HapticMenuHelper.MenuItem> items = fillDefaultHapticMenu(chatId, false, canDisableMarkdownCallback != null && canDisableMarkdownCallback.get(), true);
        if (customItemProvider != null) {
          if (items == null)
            items = new ArrayList<>();
          customItemProvider.runWithData(items);
        }
        return items;
      }
      return null;
    }, (menuView, parentView) -> {
      switch (menuView.getId()) {
        case R.id.btn_sendScheduled:
          if (context != null)
            tdlib.ui().pickSchedulingState(context, schedulingState -> sendCallback.onSendRequested(false, schedulingState, false), chatId, false, false, forcedTheme);
          break;
        case R.id.btn_sendNoMarkdown:
          sendCallback.onSendRequested(false, null, true);
          break;
        case R.id.btn_sendNoSound:
          sendCallback.onSendRequested(true, null, false);
          break;
        case R.id.btn_sendOnceOnline:
          sendCallback.onSendRequested(false, new TdApi.MessageSchedulingStateSendWhenOnline(), false);
          break;
      }
    }, context != null ? context.getThemeListeners() : null, forcedTheme);
  }

  public boolean showScheduleOptions (ViewController<?> context, long chatId, boolean needSendWithoutSound, SimpleSendCallback callback, @Nullable ThemeDelegate forcedTheme) {
    return pickSchedulingState(context, schedulingState -> {
      if (schedulingState == null)
        callback.onSendRequested(true, null, false);
      else
        callback.onSendRequested(false, schedulingState, false);
    }, chatId, tdlib.cache().userLastSeenAvailable(tdlib.chatUserId(chatId)), needSendWithoutSound, forcedTheme);
  }

  public boolean pickSchedulingState (ViewController<?> context, RunnableData<TdApi.MessageSchedulingState> callback, long chatId, boolean needOnline, boolean needSendWithoutSound, @Nullable ThemeDelegate forcedTheme) {
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
    icons.append(R.drawable.baseline_schedule_24);

    ids.append(R.id.btn_sendScheduled2Hr);
    strings.append(Lang.plural(isSelfChat ? R.string.RemindInXHours : R.string.SendInXHours, 2));
    icons.append(R.drawable.baseline_schedule_24);

    ids.append(R.id.btn_sendScheduled8Hr);
    strings.append(Lang.plural(isSelfChat ? R.string.RemindInXHours : R.string.SendInXHours, 8));
    icons.append(R.drawable.baseline_schedule_24);

    ids.append(R.id.btn_sendScheduledCustom);
    strings.append(Lang.getString(isSelfChat ? R.string.RemindAtCustomTime : R.string.SendAtCustomTime));
    icons.append(R.drawable.baseline_date_range_24);

    context.showOptions(null, ids.get(), strings.get(), null, icons.get(), (v, optionId) -> {
      long seconds = 0;
      switch (optionId) {
        case R.id.btn_sendNoSound:
          callback.runWithData(null);
          return true;
        case R.id.btn_sendOnceOnline:
          callback.runWithData(new TdApi.MessageSchedulingStateSendWhenOnline());
          return true;
        case R.id.btn_sendScheduled30Min:
          seconds = TimeUnit.MINUTES.toSeconds(30);
          break;
        case R.id.btn_sendScheduled2Hr:
          seconds = TimeUnit.HOURS.toSeconds(2);
          break;
        case R.id.btn_sendScheduled8Hr:
          seconds = TimeUnit.HOURS.toSeconds(8);
          break;
        case R.id.btn_sendScheduledCustom: {
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
            int sendDate = (int) (millis / 1000l); // (int) (tdlib.toTdlibTimeMillis(millis) / 1000l);
            callback.runWithData(new TdApi.MessageSchedulingStateSendAtDate(sendDate));
          }, forcedTheme);
          return true;
        }
      }
      if (seconds > 0) {
        int sendDate = (int) (tdlib.currentTimeMillis() / 1000l + seconds);
        callback.runWithData(new TdApi.MessageSchedulingStateSendAtDate(sendDate));
      }
      return true;
    }, forcedTheme);
    return true;
  }

  public void deleteContact (ViewController<?> context, long userId) {
    if (tdlib.cache().userContact(userId)) {
      context.showOptions(Lang.getStringBold(R.string.DeleteContactConfirm, tdlib.cache().userName(userId)), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id1) -> {
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
    tdlib.client().send(new TdApi.CanTransferOwnership(), result -> {
      tdlib.ui().post(() -> {
        listener.onOwnershipTransferAbilityChecked(result);
        switch (result.getConstructor()) {
          case TdApi.CanTransferOwnershipResultOk.CONSTRUCTOR:
            tdlib.client().send(new TdApi.GetPasswordState(), state -> {
              if (state.getConstructor() != TdApi.PasswordState.CONSTRUCTOR) return;
              PasswordController controller = new PasswordController(context.context(), context.tdlib());
              controller.setArguments(new PasswordController.Args(PasswordController.MODE_TRANSFER_OWNERSHIP_CONFIRM, (TdApi.PasswordState) state).setSuccessListener(password -> {
                // Ask if the user REALLY wants to transfer ownership, because this operation is serious
                context.addOneShotFocusListener(() ->
                  context.showOptions(new ViewController.Options.Builder()
                    .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipAlert), finalAlertMessageText))
                    .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.TransferOwnershipConfirm), ViewController.OPTION_COLOR_RED, R.drawable.templarian_baseline_account_switch_24))
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
            break;
          case TdApi.CanTransferOwnershipResultPasswordNeeded.CONSTRUCTOR:
            context.showOptions(new ViewController.Options.Builder()
              .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipSecurityAlert), Lang.getMarkdownString(context, R.string.TransferOwnershipSecurityPasswordNeeded)))
              .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.TransferOwnershipSecurityActionSetPassword), ViewController.OPTION_COLOR_BLUE, R.drawable.mrgrigri_baseline_textbox_password_24))
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
          case TdApi.CanTransferOwnershipResultPasswordTooFresh.CONSTRUCTOR:
            context.showOptions(new ViewController.Options.Builder()
              .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipSecurityAlert), Lang.getMarkdownString(context, R.string.TransferOwnershipSecurityWaitPassword, Lang.getDuration(((TdApi.CanTransferOwnershipResultPasswordTooFresh) result).retryAfter))))
              .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.OK), ViewController.OPTION_COLOR_NORMAL, R.drawable.baseline_check_circle_24))
              .cancelItem()
              .build(), (optionView, id) -> true
            );
            break;
          case TdApi.CanTransferOwnershipResultSessionTooFresh.CONSTRUCTOR:
            context.showOptions(new ViewController.Options.Builder()
              .info(Strings.getTitleAndText(Lang.getString(R.string.TransferOwnershipSecurityAlert), Lang.getMarkdownString(context, R.string.TransferOwnershipSecurityWaitSession, Lang.getDuration(((TdApi.CanTransferOwnershipResultSessionTooFresh) result).retryAfter))))
              .item(new ViewController.OptionItem(R.id.btn_next, Lang.getString(R.string.OK), ViewController.OPTION_COLOR_NORMAL, R.drawable.baseline_check_circle_24))
              .cancelItem()
              .build(), (optionView, id) -> true
            );
            break;
        }
      });
    });
  }

  public void saveGifs (List<TD.DownloadedFile> downloadedFiles) {
    AtomicInteger remaining = new AtomicInteger(downloadedFiles.size());
    AtomicInteger successfull = new AtomicInteger(0);
    for (TD.DownloadedFile downloadedFile : downloadedFiles) {
      tdlib.client().send(new TdApi.AddSavedAnimation(new TdApi.InputFileId(downloadedFile.getFileId())), object -> {
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR:
            successfull.incrementAndGet();
            break;
          case TdApi.Error.CONSTRUCTOR:
            UI.showError(object);
            break;
        }
        if (remaining.decrementAndGet() == 0) {
          if (successfull.get() == 1) {
            UI.showToast(R.string.GifSaved, Toast.LENGTH_SHORT);
          } else {
            UI.showToast(Lang.pluralBold(R.string.GifSaved, downloadedFiles.size()), Toast.LENGTH_SHORT);
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
}
