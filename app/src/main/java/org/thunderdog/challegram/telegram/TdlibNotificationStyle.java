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
 * File created on 25/12/2018
 */
package org.thunderdog.challegram.telegram;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.filegen.GenerationInfo;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.receiver.TGBaseReplyReceiver;
import org.thunderdog.challegram.receiver.TGMessageReceiver;
import org.thunderdog.challegram.receiver.TGRemoveAllReceiver;
import org.thunderdog.challegram.receiver.TGRemoveReceiver;
import org.thunderdog.challegram.receiver.TGWearReplyReceiver;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Letters;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import me.leolin.shortcutbadger.ShortcutBadger;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;

public class TdlibNotificationStyle implements TdlibNotificationStyleDelegate, FileUpdateListener {
  private static final boolean USE_GROUPS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH;

  static final long MEDIA_LOAD_TIMEOUT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? 15000 : 7500;
  static final long SUMMARY_MEDIA_LOAD_TIMEOUT = 100;
  static final long SYNC_FILE_TIMEOUT = 100;

  private final TdlibNotificationManager context;
  private final Tdlib tdlib;

  public TdlibNotificationStyle (TdlibNotificationHelper context, Tdlib tdlib) {
    this.context = context.context();
    this.tdlib = tdlib;
  }

  @Override
  public final void displayNotificationGroup (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group, @Nullable TdlibNotificationSettings settings) {
    NotificationManagerCompat manager = helper.manager();
    if (USE_GROUPS) {
      if (displayChildNotification(manager, context, helper, badgeCount, allowPreview, group, settings, false) == DISPLAY_STATE_FAIL)
        return;
    }
    displaySummaryNotification(manager, context, helper, badgeCount, allowPreview, settings, group.getCategory(), false);
  }

  public final void rebuildNotificationsSilently (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, TdApi.NotificationSettingsScope scope, long specificChatId, int specificGroupId) {
    NotificationManagerCompat manager = helper.manager();
    SparseIntArray categories = new SparseIntArray(3);
    if (USE_GROUPS) {
      if (specificGroupId != 0) {
        TdlibNotificationGroup group = helper.findGroup(specificGroupId);
        if (group != null && displayChildNotification(manager, context, helper, badgeCount, allowPreview, group, null, true) != DISPLAY_STATE_FAIL) {
          categories.put(group.getCategory(), 1);
        }
      } else if (specificChatId != 0) {
        for (TdlibNotificationGroup group : helper) {
          if (group.getChatId() == specificChatId) {
            if (displayChildNotification(manager, context, helper, badgeCount, allowPreview, group, null, true) != DISPLAY_STATE_FAIL) {
              categories.put(group.getCategory(), 1);
            }
          }
        }
      } else if (scope != null) {
        for (TdlibNotificationGroup group : helper) {
          boolean matches;
          switch (scope.getConstructor()) {
            case TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR:
              matches = ChatId.isUserChat(group.getChatId());
              break;
            case TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR:
              matches = ChatId.isBasicGroup(group.getChatId()) || (ChatId.isSupergroup(group.getChatId()) && !tdlib.isChannelFast(group.getChatId()));
              break;
            case TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR:
              matches = tdlib.isChannelFast(group.getChatId());
              break;
            default:
              throw new UnsupportedOperationException(scope.toString());
          }
          if (matches) {
            if (displayChildNotification(manager, context, helper, badgeCount, allowPreview, group, null, true) != DISPLAY_STATE_FAIL) {
              categories.put(group.getCategory(), 1);
            }
          }
        }
      } else {
        for (TdlibNotificationGroup group : helper) {
          if (displayChildNotification(manager, context, helper, badgeCount, allowPreview, group, null, true) != DISPLAY_STATE_FAIL) {
            categories.put(group.getCategory(), 1);
          }
        }
      }
    } else {
      for (TdlibNotificationGroup group : helper) {
        categories.put(group.getCategory(), 1);
      }
    }
    hideExtraSummaryNotifications(manager, helper, categories);
    if (categories.size() > 0) {
      for (int i = 0; i < categories.size(); i++) {
        int category = categories.keyAt(i);
        displaySummaryNotification(manager, context, helper, badgeCount, allowPreview, null, category, true);
      }
    }
  }

  @Override
  public final void hideNotificationGroup (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group) {
    NotificationManagerCompat manager = helper.manager();
    if (USE_GROUPS) {
      displayChildNotification(manager, context, helper, badgeCount, false, group, null, false);
    }
    displaySummaryNotification(manager, context, helper, badgeCount, allowPreview, null, group.getCategory(), true); // rebuild, because we need to hide notification asap
  }

  @Override
  public final void hideAllNotifications (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount) {
    NotificationManagerCompat manager = helper.manager();
    SparseIntArray categories = new SparseIntArray(5);
    if (USE_GROUPS) {
      for (TdlibNotificationGroup group : helper) {
        displayChildNotification(manager, context, helper, badgeCount, false, group, null, false);
        categories.put(group.getCategory(), 1);
      }
    }
    for (int i = 0; i < categories.size(); i++) {
      int category = categories.keyAt(i);
      displaySummaryNotification(manager, context, helper, badgeCount, false, null, category, true); // rebuild, because we need to hide notification asap
    }
  }

  // Grouped notification

  private static void styleIntent (String action, Intent intent, Tdlib tdlib, TdlibNotificationGroup group, boolean needReply, long[] messageIds, long[] userIds) {
    Intents.secureIntent(intent, true);
    intent.setAction(action);
    TdlibNotificationExtras.put(intent, tdlib, group, needReply, messageIds, userIds);
  }

  private static boolean canReplyTo (TdlibNotificationGroup group) {
    if (group.isEmpty())
      return false;
    if (group.isOnlyScheduled())
      return false;
    if (group.isMention())
      return true;
    TdlibNotification prevNotification = null;
    for (TdlibNotification notification : group) {
      if (prevNotification != null && !prevNotification.isSameSender(notification))
        return false;
      prevNotification = notification;
    }
    return false;
  }

  private static void styleNotification (Tdlib tdlib, NotificationCompat.Builder builder, long chatId, @Nullable TdApi.Chat chat, boolean allowPreview) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
      builder.setColor(tdlib.accountColor(chatId));
      if (allowPreview && chat != null && chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR) {
        TdApi.User user = tdlib.chatUser(chat);
        if (user != null && !StringUtils.isEmpty(user.phoneNumber)) {
          builder.addPerson("tel:+" + user.phoneNumber);
        }
      }
    }
  }

  public static final int DISPLAY_STATE_FAIL = 0;
  public static final int DISPLAY_STATE_HIDDEN = 1;
  public static final int DISPLAY_STATE_POSTPONED = 2;
  public static final int DISPLAY_STATE_OK = 3;

  protected final int displayChildNotification (NotificationManagerCompat manager, Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group, TdlibNotificationSettings settings, boolean isRebuild) {
    return displayChildNotification(manager, context, helper, badgeCount, allowPreview, group, settings, helper.getNotificationIdForGroup(group.getId()), false, isRebuild);
  }

  private static final long CHAT_MAX_DELAY = 200;

  protected final int displayChildNotification (NotificationManagerCompat manager, Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group, TdlibNotificationSettings settings, int notificationId, boolean isSummary, boolean isRebuild) {
    if (!allowPreview || group.isEmpty()) {
      manager.cancel(notificationId);
      return DISPLAY_STATE_HIDDEN;
    }

    int visualSize = group.visualSize();
    if (visualSize == 0) {
      manager.cancel(notificationId);
      return DISPLAY_STATE_HIDDEN;
    }

    if (!tdlib.account().allowNotifications()) {
      manager.cancel(notificationId);
      return DISPLAY_STATE_POSTPONED;
    }

    final long chatId = group.getChatId();
    final TdApi.Chat chat = tdlib.chatSync(chatId, CHAT_MAX_DELAY);
    if (chat == null) {
      Log.e(Log.TAG_FCM, "Doing nothing with the notification for chat %d, because it is no longer accessible");
      // manager.cancel(notificationId);
      return DISPLAY_STATE_FAIL;
    }

    /*if (Settings.instance().needRestrictContent()) {
      String restrictionReason = tdlib.chatRestrictionReason(chat);
      if (!Strings.isEmpty(restrictionReason)) {
        group.markAsHidden(TdlibNotificationGroup.HIDE_REASON_RESTRICTED);
        return DISPLAY_STATE_FAIL;
      }
    }*/

    final int category = group.getCategory();

    String channelId;
    String rShortcutId = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = (android.app.NotificationChannel) tdlib.notifications().getSystemChannel(group);
      if (channel == null) {
        group.markAsHidden(TdlibNotificationGroup.HIDE_REASON_DISABLED_CHANNEL);
        return DISPLAY_STATE_FAIL;
      }
      channelId = channel.getId();
    } else {
      channelId = null;
    }

    final TdlibNotification singleNotification = visualSize == 1 ? group.lastNotification() : null;
    //

    final TdlibNotification lastNotification = group.lastNotification();

    final TdApi.User user = tdlib.chatUser(chat);
    final String chatTitle = tdlib.chatTitle(chat);
    final boolean onlyPinned = group.isOnlyPinned();
    final boolean onlyScheduled = group.isOnlyScheduled();
    final boolean onlySilent = group.isOnlyInitiallySilent();
    final boolean isChannel = tdlib.isChannelChat(chat);
    final CharSequence visualChatTitle = Lang.getNotificationTitle(chat.id, chatTitle, group.getTotalCount(), tdlib.isSelfChat(group.getChatId()), tdlib.isMultiChat(chat), isChannel, group.isMention(), onlyPinned, onlyScheduled, onlySilent);

    // Content preview download
    List<TdApi.File> cloudReferences = null;
    if (Config.NEED_NOTIFICATION_CONTENT_PREVIEW && helper.needContentPreview(group, singleNotification)) {
      TdlibNotificationMediaFile photo = TdlibNotificationMediaFile.newFile(tdlib, chat, singleNotification.getNotificationContent());
      TdApi.File file = photo != null ? photo.file : null;
      if (file != null) {
        if (!TD.isFileLoaded(file)) {
          tdlib.files().syncFile(file, null, SYNC_FILE_TIMEOUT);
        }
        if (!TD.isFileLoaded(file)) {
          cloudReferences = new ArrayList<>(1);
          cloudReferences.add(file);
          tdlib.files().addCloudReference(file, this, true);
        }
      }
    }

    // Notification itself

    final boolean needPreview = helper.needPreview(group);
    final boolean needReply = !Passcode.instance().isLocked() && needPreview && (!isSummary || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) && !isChannel && tdlib.hasWritePermission(chat) && !group.isOnlyScheduled();
    final boolean needReplyToMessage = needReply && canReplyTo(group) && (!ChatId.isPrivate(chatId) || (singleNotification != null && chat.unreadCount > 1));
    final long[] allMessageIds = group.getAllMessageIds();
    final long[] allUserIds = group.isMention() ? group.getAllUserIds() : null;
    final boolean[] hasCustomText = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new boolean[1] : null;

    NotificationCompat.CarExtender.UnreadConversation.Builder conversationBuilder = new NotificationCompat.CarExtender.UnreadConversation.Builder(visualChatTitle != null ? visualChatTitle.toString() : null).setLatestTimestamp(TimeUnit.SECONDS.toMillis(lastNotification.getDate()));

    Intent msgHeardIntent = new Intent();
    styleIntent(Intents.ACTION_MESSAGE_HEARD, msgHeardIntent, tdlib, group, needReplyToMessage, allMessageIds, allUserIds);

    PendingIntent msgHeardPendingIntent = PendingIntent.getBroadcast(UI.getAppContext(), notificationId, msgHeardIntent, Intents.mutabilityFlags(true));
    conversationBuilder.setReadPendingIntent(msgHeardPendingIntent);

    NotificationCompat.Action replyAction = null, muteAction = null, readAction = null;

    if (needReply) {
      // reply
      Intent msgReplyIntent = new Intent();
      styleIntent(Intents.ACTION_MESSAGE_REPLY, msgReplyIntent, tdlib, group, needReplyToMessage, allMessageIds, allUserIds);
      PendingIntent msgReplyPendingIntent = PendingIntent.getBroadcast(UI.getAppContext(), notificationId, msgReplyIntent, Intents.mutabilityFlags(true));
      RemoteInput remoteInputAuto = new RemoteInput.Builder(TGBaseReplyReceiver.EXTRA_VOICE_REPLY).setLabel(Lang.getString(R.string.Reply)).build();
      conversationBuilder.setReplyAction(msgReplyPendingIntent, remoteInputAuto);

      Intent replyIntent = new Intent(UI.getAppContext(), TGWearReplyReceiver.class);
      Intents.secureIntent(replyIntent, true);
      TdlibNotificationExtras.put(replyIntent, tdlib, group, needReplyToMessage, allMessageIds, allUserIds);
      PendingIntent replyPendingIntent = PendingIntent.getBroadcast(UI.getAppContext(), notificationId, replyIntent, Intents.mutabilityFlags(true));
      RemoteInput remoteInput = new RemoteInput.Builder(TGBaseReplyReceiver.EXTRA_VOICE_REPLY).setLabel(Lang.getString(R.string.Reply)).build();
      String replyToString = Lang.getString(R.string.Reply);
      replyAction = new NotificationCompat.Action.Builder(R.drawable.baseline_reply_24_white, replyToString, replyPendingIntent).setAllowGeneratedReplies(true).addRemoteInput(remoteInput).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build();
    }

    if (needPreview) {
      // mark as read
      Intent intent = new Intent(UI.getAppContext(), TGMessageReceiver.class);
      styleIntent(Intents.ACTION_MESSAGE_READ, intent, tdlib, group, needReplyToMessage, allMessageIds, allUserIds);
      try {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(UI.getAppContext(), notificationId, intent, Intents.mutabilityFlags(true));
        readAction = new NotificationCompat.Action.Builder(R.drawable.baseline_done_all_24_white, Lang.getString(R.string.ActionRead), pendingIntent).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build();
      } catch (Throwable t) {
        Log.e("Unable to add read intent", t);
      }
    }

    if (true) {
      // mute for 1h
      Intent intent = new Intent(UI.getAppContext(), TGMessageReceiver.class);
      styleIntent(Intents.ACTION_MESSAGE_MUTE, intent, tdlib, group, needReplyToMessage, allMessageIds, allUserIds);
      try {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(UI.getAppContext(), notificationId, intent, Intents.mutabilityFlags(true));
        String text;
        if (group.isMention()) {
          long singleSenderId = group.singleSenderId();
          if (singleSenderId != 0) {
            String firstName = tdlib.chatTitleShort(singleSenderId);
            // text = Lang.plural(R.string.ActionMutePersonHours, 1, firstName);
            text = Lang.getString(R.string.ActionMutePerson, firstName);
          } else {
            // text = Lang.plural(R.string.ActionMuteAll, 1);
            text = Lang.getString(R.string.ActionMuteEveryone);
          }
        } else {
          // text = Lang.plural(R.string.ActionMuteHours, 1);
          text = Lang.getString(R.string.ActionMute);
        }
        muteAction = new NotificationCompat.Action.Builder(R.drawable.baseline_volume_off_24_white, text, pendingIntent).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE).build();
      } catch (Throwable t) {
        Log.e("Unable to add read intent", t);
      }
    }

    Intent hideIntent = new Intent(UI.getAppContext(), TGRemoveReceiver.class);
    Intents.secureIntent(hideIntent, true);
    TdlibNotificationExtras.put(hideIntent, tdlib, group, needReplyToMessage, allMessageIds, allUserIds);
    PendingIntent hidePendingIntent = PendingIntent.getBroadcast(UI.getAppContext(), notificationId, hideIntent, Intents.mutabilityFlags(true));

    NotificationCompat.Style style = null;

    NotificationCompat.MessagingStyle messagingStyle = newMessagingStyle(this.context, chat, group.getTotalCount(), group.isMention(), onlyPinned, onlyScheduled, onlySilent, !isRebuild);

    final List<List<TdlibNotification>> mergedNotifications = new ArrayList<>();
    TdlibNotification prevNotification = null;
    for (TdlibNotification notification : group) {
      if (!notification.canMergeWith(prevNotification)) {
        mergedNotifications.add(new ArrayList<>());
      }
      mergedNotifications.get(mergedNotifications.size() - 1).add(notification);
      prevNotification = notification;
    }

    final StringBuilder textBuilder = new StringBuilder();
    for (List<TdlibNotification> mergedList : mergedNotifications) {
      if (textBuilder.length() > 0) {
        textBuilder.append("\n\n");
      }

      Person p = buildPerson(this.context, chat, mergedList.get(0), onlyScheduled, onlySilent, !isRebuild);
      final Person person;

      if (p.getKey() != null && chat != null && p.getKey().equals(Long.toString(chat.id))) {
        person = p.toBuilder().setName(visualChatTitle).build();
      } else {
        person = p;
      }

      if (mergedList.size() == 1) {
        TdlibNotification notification = mergedList.get(0);
        final CharSequence messageText;
        if (needPreview) {
          CharSequence content = notification.getTextRepresentation(tdlib, group.isMention() && onlyPinned, true, hasCustomText);
          messageText = Config.USE_GROUP_NAMES || tdlib.isUserChat(chat) || tdlib.isChannelChat(chat) ? content : Lang.getString(R.string.format_notificationContentGroup, notification.findSenderName(), content);
        } else {
          messageText = Lang.getString(R.string.YouHaveNewMessage);
        }
        textBuilder.append(messageText);
        conversationBuilder.addMessage(messageText != null ? messageText.toString() : null);
        addMessage(messagingStyle, messageText, person, tdlib, chat, notification, isSummary ? SUMMARY_MEDIA_LOAD_TIMEOUT : MEDIA_LOAD_TIMEOUT, isRebuild, !onlyScheduled && notification.isScheduled(), !onlySilent && notification.isVisuallySilent(), onlyPinned);
      } else {
        final CharSequence messageText;
        boolean isScheduled = true;
        boolean isVisuallySilent = true;
        boolean isEdited = false;
        boolean isEditedVisible = false;
        for (TdlibNotification notification : mergedList) {
          if (!notification.isScheduled()) {
            isScheduled = false;
          }
          if (!notification.isVisuallySilent()) {
            isVisuallySilent = false;
          }
          if (notification.isEdited()) {
            isEdited = true;
          }
          if (notification.isEditedVisible()) {
            isEditedVisible = true;
          }
        }
        if (needPreview) {
          CharSequence content = mergedList.get(0).getTextRepresentation(tdlib, group.isMention() && onlyPinned, true, mergedList, isEdited, isEditedVisible, hasCustomText);
          messageText = Config.USE_GROUP_NAMES || tdlib.isUserChat(chat) || tdlib.isChannelChat(chat) ? content : Lang.getCharSequence(R.string.format_notificationContentGroup, mergedList.get(0).findSenderName(), content);
        } else {
          messageText = Lang.plural(R.string.xNewMessages, mergedList.size());
        }
        textBuilder.append(messageText);
        conversationBuilder.addMessage(messageText != null ? messageText.toString() : null);
        addMessage(messagingStyle, messageText, person, tdlib, chat, mergedList, isSummary ? SUMMARY_MEDIA_LOAD_TIMEOUT : MEDIA_LOAD_TIMEOUT, isRebuild, !onlyScheduled && isScheduled, !onlySilent && isVisuallySilent);
      }
    }

    if (Config.NEED_NOTIFICATION_CONTENT_PREVIEW && Build.VERSION.SDK_INT < Build.VERSION_CODES.P && helper.needContentPreview(group, singleNotification)) {
      TdlibNotificationMediaFile photo = TdlibNotificationMediaFile.newFile(tdlib, chat, singleNotification.getNotificationContent());
      TdApi.File photoFile = photo != null ? photo.file : null;

      if (photoFile != null) {
        if (!isRebuild) {
          tdlib.files().downloadFileSync(photoFile, TdlibNotificationStyle.MEDIA_LOAD_TIMEOUT, null, null);
        }
        if (TD.isFileLoaded(photoFile)) {
          Bitmap result = null;
          try {
            switch (photo.type) {
              case TdlibNotificationMediaFile.TYPE_WEBM_STICKER:
                result = ImageReader.decodeVideoFrame(photoFile.local.path, photo.width, photo.height, 512);
                break;
              case TdlibNotificationMediaFile.TYPE_LOTTIE_STICKER:
                result = ImageReader.decodeLottieFrame(photoFile.local.path, photo.width, photo.height, 512);
                break;
              case TdlibNotificationMediaFile.TYPE_STICKER:
              case TdlibNotificationMediaFile.TYPE_IMAGE: {
                BitmapFactory.Options opts = ImageReader.getImageSize(photoFile.local.path);
                opts.inSampleSize = ImageReader.calculateInSampleSize(opts, 512, 512);
                opts.inJustDecodeBounds = false;
                result = ImageReader.decodeFile(photoFile.local.path, opts.inSampleSize != 0 ? opts : null);
                break;
              }
              default:
                throw new UnsupportedOperationException(Integer.toString(photo.type));
            }
            if (U.isValidBitmap(result)) {
              int width = result.getWidth();
              int height = result.getHeight();
              float ratio = (float) width / (float) height;
              if (ratio != 2f) {
                int desiredWidth = Math.min(Math.max(result.getWidth(), result.getHeight()), 512);
                desiredWidth = desiredWidth - desiredWidth % 2;
                int desiredHeight = desiredWidth / 2;
                Bitmap scaledBitmap = Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(scaledBitmap);
                Paint paint = new Paint();
                paint.setFilterBitmap(true);
                if (photo.needBlur) {
                  float scale = Math.min(90f / (float) result.getWidth(), 90f / (float) result.getHeight());
                  Bitmap blurredBitmap = Bitmap.createScaledBitmap(result, (int) ((float) result.getWidth() * scale), (int) ((float) result.getHeight() * scale), true);
                  if (U.blurBitmap(blurredBitmap, 3, 1)) {
                    paint.setAlpha((int) (255f * .75f));
                    c.drawColor(0xffffffff);
                    DrawAlgorithms.drawBitmapCentered(scaledBitmap.getWidth(), scaledBitmap.getHeight(), c, blurredBitmap, true, paint);
                    paint.setAlpha(255);
                  }
                }

                float scale = Math.min((float) desiredWidth / (float) width, (float) desiredHeight / (float) height);
                Rect resultRect = new Rect();
                resultRect.right = (int) ((float) width * scale);
                resultRect.bottom = (int) ((float) height * scale);
                if (!photo.isSticker()) {
                  resultRect.offset(desiredWidth / 2 - resultRect.right / 2, desiredHeight / 2 - resultRect.bottom / 2);
                }
                c.drawBitmap(result, null, resultRect, paint);
                Bitmap oldBitmap = result;
                result = scaledBitmap;
                oldBitmap.recycle();
                U.recycle(c);
              }
            }
          } catch (Throwable t) {
            Log.i(t);
          }
          if (U.isValidBitmap(result)) {
            style = new NotificationCompat.BigPictureStyle().bigPicture(result);
          }
        }
      }
    }

    if (style == null) {
      style = messagingStyle;
    }
    final String textContent = textBuilder.toString();
    final CharSequence tickerText = getTickerText(tdlib, helper, allowPreview, chat, lastNotification, true, group.singleSenderId() != 0, hasCustomText);

    final PendingIntent contentIntent = TdlibNotificationUtils.newIntent(tdlib.id(), tdlib.settings().getLocalChatId(chatId), group.findTargetMessageId());

    NotificationCompat.CarExtender carExtender = new NotificationCompat.CarExtender().setUnreadConversation(conversationBuilder.build());

    boolean needGroupLogic = true; // !isSummary || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && settings == null);

    NotificationCompat.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder = new NotificationCompat.Builder(UI.getAppContext(), channelId);
      boolean needNotification = settings != null;
      builder.setOnlyAlertOnce(!needNotification);
      if (needGroupLogic) {
        int behavior;
        if (isSummary) {
          behavior = allowPreview || settings == null ? NotificationCompat.GROUP_ALERT_CHILDREN : NotificationCompat.GROUP_ALERT_SUMMARY;
        } else {
          behavior = needNotification ? NotificationCompat.GROUP_ALERT_CHILDREN : NotificationCompat.GROUP_ALERT_SUMMARY;
        }
        builder.setGroupAlertBehavior(behavior);
        Log.i(Log.TAG_FCM, "displaying notification with behavior:%d", behavior);
      }
    } else {
      //noinspection deprecation
      builder = new NotificationCompat.Builder(UI.getAppContext()/*, notificationChannel*/);
    }

    builder
      .setContentTitle(visualChatTitle)
      .setSmallIcon(R.mipmap.app_notification)
      .setContentText(textContent)
      .setTicker(tickerText)
      .setAutoCancel(Config.NOTIFICATION_AUTO_CANCEL)
      .setSortKey(makeSortKey(lastNotification, isSummary))
      .setWhen(TimeUnit.SECONDS.toMillis(lastNotification.getDate()))
      .setStyle(style)
      .setContentIntent(contentIntent);

    if (ChatId.isSecret(group.getChatId()) && (Settings.instance().needHideSecretChats() || tdlib.notifications().isShowPreviewEnabled(group.getChatId(), group.isMention()))) {
      builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
    }

    if (needGroupLogic) {
      builder.setGroup(makeGroupKey(tdlib, group.getCategory()));
      builder.setGroupSummary(isSummary);
    }

    // builder.setNumber(badgeCount)

    builder.setDeleteIntent(hidePendingIntent);

    if (tdlib.context().isMultiUser()) {
      builder.setSubText(tdlib.accountShortName(category));
    } else if (category != TdlibNotificationGroup.CATEGORY_DEFAULT) {
      builder.setSubText(Lang.getNotificationCategory(category));
    }

    if (!Passcode.instance().isLocked()) {
      /*if (muteAction != null)
        builder.addAction(muteAction);*/
      if (replyAction != null)
        builder.addAction(replyAction);
      if (readAction != null)
        builder.addAction(readAction);
    }
    try { builder.extend(carExtender); } catch (Throwable t) { Log.w(t); }

    styleNotification(tdlib, builder, chatId, chat, allowPreview);

    boolean hasIcon = false;
    final Bitmap bitmap = TdlibNotificationUtils.buildLargeIcon(tdlib, chat, !isRebuild);
    if (U.isValidBitmap(bitmap)) {
      builder.setLargeIcon(bitmap);
      hasIcon = true;
    }

    if (user != null && !StringUtils.isEmpty(user.phoneNumber)) {
      builder.addPerson('+' + user.phoneNumber);
    }

    if (isSummary && settings != null) {
      settings.apply(builder, isSummary);
    } else {
      TdlibNotificationSettings.applyStatic(builder, tdlib, group, isSummary);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasCustomText != null) {
      builder.setAllowSystemGeneratedContextualActions(allowPreview && needPreview && hasCustomText[0]);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      rShortcutId = createShortcut(builder, context, group, visualChatTitle, chatId, bitmap);
    }

    int state;
    Notification notification;
    try {
      notification = builder.build();
    } catch (Throwable t) {
      Log.e("Unable to build notification", t);
      tdlib.settings().trackNotificationProblem(t, false, group.getChatId());
      group.markAsHidden(TdlibNotificationGroup.HIDE_REASON_BUILD_ERROR);
      notification = null;
    }
    if (notification != null) {
      if (Device.MANUFACTURER == Device.XIAOMI && hasIcon) {
        try {
          Field field = notification.getClass().getDeclaredField("extraNotification");
          Object extraNotification = field.get(notification);
          Class<?> c = extraNotification.getClass();
          if (hasIcon) {
            Method setCustomizedIcon = c.getDeclaredMethod("setCustomizedIcon", boolean.class);
            setCustomizedIcon.setAccessible(true);
            setCustomizedIcon.invoke(extraNotification, true);
          }
        } catch (Throwable t) {
          Log.i(t);
        }
      }

      if (isSummary) {
        ShortcutBadger.applyNotification(UI.getAppContext(), notification, badgeCount);
      }

      try {
        if (Config.TEST_NOTIFICATION_PROBLEM_RESOLUTION)
          throw new RuntimeException();
        manager.notify(notificationId, notification);
        state = DISPLAY_STATE_OK;
      } catch (Throwable t) {
        Log.e("Cannot display notification", t);
        tdlib.settings().trackNotificationProblem(t, true, group.getChatId());
        group.markAsHidden(t instanceof SecurityException ? TdlibNotificationGroup.HIDE_REASON_SECURITY_ERROR : TdlibNotificationGroup.HIDE_REASON_DISPLAY_ERROR);
        state = DISPLAY_STATE_FAIL;
      }
    } else {
      state = DISPLAY_STATE_FAIL;
    }
    // FIXME 7.0-7.1 android.os.FileUriExposedException:
    // Cleanup

    if (rShortcutId != null) {
      ShortcutManagerCompat.removeDynamicShortcuts(context, Collections.singletonList(rShortcutId));
    }

    if (cloudReferences != null) {
      for (TdApi.File file : cloudReferences) {
        tdlib.files().removeCloudReference(file, this);
      }
    }

    return state;
  }

  // Common notification

  @RequiresApi(api = Build.VERSION_CODES.R)
  protected final String createShortcut (NotificationCompat.Builder builder, Context context, @NonNull TdlibNotificationGroup group, CharSequence visualChatTitle, long chatId, Bitmap icon) {
    long localChatId = tdlib.settings().getLocalChatId(chatId);
    String localKey = "tgx_ns_" + tdlib.id() + "_" + localChatId;
    IconCompat chatIcon = U.isValidBitmap(icon) ? IconCompat.createWithBitmap(icon) : null;

    Person groupPerson = new Person.Builder()
      .setName(visualChatTitle)
      .setIcon(chatIcon)
      .setKey(localKey)
      .build();

    ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, localKey)
      .setPerson(groupPerson)
      .setIsConversation()
      .setLongLived(true)
      .setShortLabel(visualChatTitle)
      .setIntent(TdlibNotificationUtils.newCoreIntent(tdlib.id(), localChatId, group.findTargetMessageId()))
      .setIcon(chatIcon)
      .build();

    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut);
    builder.setShortcutInfo(shortcut);

    // TODO: Uncomment to support bubbles. Not usable at the moment.
    // builder.setBubbleMetadata(new NotificationCompat.BubbleMetadata.Builder(si.getId().build());

    return shortcut.getId();
  }

  protected final void hideExtraSummaryNotifications (NotificationManagerCompat manager, @NonNull TdlibNotificationHelper helper, SparseIntArray displayedCategories) {
    for (int category = TdlibNotificationGroup.CATEGORY_DEFAULT; category <= TdlibNotificationGroup.MAX_CATEGORY; category++) {
      if (displayedCategories.indexOfKey(category) < 0)
        manager.cancel(helper.getBaseNotificationId(category));
    }
  }

  protected final void displaySummaryNotification (NotificationManagerCompat manager, Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, TdlibNotificationSettings settings, int category, boolean isRebuild) {
    int notificationId = helper.getBaseNotificationId(category);
    if (helper.isEmpty() || !tdlib.account().allowNotifications()) {
      manager.cancel(notificationId);
      return;
    }
    List<TdlibNotification> notifications = helper.getVisibleNotifications(category);
    if (notifications.isEmpty()) {
      manager.cancel(notificationId);
      return;
    }
    if (allowPreview) {
      TdlibNotificationGroup singleGroup = null;
      for (TdlibNotification notification : notifications) {
        if (singleGroup == null) {
          singleGroup = notification.group();
        } else if (singleGroup != notification.group()) {
          singleGroup = null;
          break;
        }
      }
      if (singleGroup != null) {
        // manager.cancel(helper.getNotificationIdForGroup(singleGroup.getId()));
        if (displayChildNotification(manager, context, helper, badgeCount, allowPreview, singleGroup, settings, notificationId, true, isRebuild) != DISPLAY_STATE_FAIL) {
          tdlib.settings().forgetNotificationProblems();
        }
        return;
      }
    }

    NotificationCompat.Builder b = buildCommonNotification(context, helper, badgeCount, allowPreview, settings, notifications, category, isRebuild);
    if (b != null) {
      Notification notification;
      try {
        notification = b.build();
      } catch (Throwable t) {
        Log.e("Unable to build common notification", t);
        tdlib.settings().trackNotificationProblem(t, false, 0);
        return;
      }
      ShortcutBadger.applyNotification(UI.getAppContext(), notification, badgeCount);
      try {
        if (Config.TEST_NOTIFICATION_PROBLEM_RESOLUTION)
          throw new RuntimeException();
        manager.notify(notificationId, notification);
        tdlib.settings().forgetNotificationProblems();
      } catch (Throwable t) {
        Log.e("Unable to display common notification", t);
        tdlib.settings().trackNotificationProblem(t, true, 0);
      }
    }
  }

  protected static String makeGroupKey (Tdlib tdlib, int category) {
    return "messages" + tdlib.id() + "_" + category;
  }

  protected static String makeSortKey (TdlibNotification notification, boolean isSummary) {
    if (isSummary) {
      return StringUtils.makeSortId(notification.getDate());
    } else {
      return StringUtils.makeSortId(Integer.MAX_VALUE - notification.getId());
    }
  }

  static Person buildPerson (TdlibNotificationManager context, boolean isSelfChat, boolean isGroupChat, boolean isChannel, TdApi.User user, @Nullable String id, boolean isScheduled, boolean isSilent, boolean allowDownload) {
    if (user == null)
      return new Person.Builder().setName("").build();
    if (context.isSelfUserId(user.id))
      id = "0";
    else if (id == null)
      id = Long.toString(user.id);
    return buildPerson(context, isSelfChat, isGroupChat, isChannel, id, TD.isBot(user), TD.getUserName(user), TD.getLetters(user), TD.getAvatarColorId(user.id, context.myUserId()), user.profilePhoto != null ? user.profilePhoto.small : null, isScheduled, isSilent, allowDownload);
  }

  public static Person buildPerson (TdlibNotificationManager context, TdApi.Chat chat, TdlibNotification notification, boolean isScheduled, boolean isSilent, boolean allowDownload) {
    Tdlib tdlib = context.tdlib();
    long senderChatId = notification.findSenderId();
    long userId = tdlib.chatUserId(chat);
    if (userId == 0 && ChatId.isUserChat(senderChatId) && notification.isSynced()) {
      userId = ChatId.toUserId(senderChatId);
    }
    if (userId != 0) {
      TdApi.User user = tdlib.cache().user(userId);
      return buildPerson(context, notification.isSelfChat(), TD.isMultiChat(chat), tdlib.isChannelChat(chat), user, ChatId.isSecret(chat.id) ? Long.toString(chat.id) : Long.toString(ChatId.fromUserId(userId)), isScheduled, isSilent, allowDownload);
    }
    if (TD.isMultiChat(chat)) {
      String senderName = notification.findSenderName();
      TdApi.Chat senderChat = tdlib.chat(senderChatId);
      return buildPerson(context, notification.isSelfChat(), TD.isMultiChat(chat), tdlib.isChannelChat(chat), Long.toString(senderChatId), tdlib.isBotChat(senderChatId) || tdlib.isChannel(senderChatId), senderName, TD.getLetters(senderName), tdlib.chatAvatarColorId(senderChatId), senderChat != null && senderChat.photo != null ? senderChat.photo.small : null, isScheduled, isSilent, allowDownload);
    }
    return buildPerson(context, notification.isSelfChat(), TD.isMultiChat(chat), tdlib.isChannelChat(chat), Long.toString(chat.id), tdlib.isBotChat(chat) || tdlib.isChannelChat(chat), chat.title, tdlib.chatLetters(chat), tdlib.chatAvatarColorId(chat), chat.photo != null ? chat.photo.small : null, isScheduled, isSilent, allowDownload);
  }

  public static Person buildPerson (TdlibNotificationManager context, boolean isSelfChat, boolean isGroupChat, boolean isChannel, String id, boolean isBot, String name, Letters letters, @ThemeColorId int colorId, TdApi.File photo, boolean isScheduled, boolean isSilent, boolean allowDownload) {
    Person.Builder b = new Person.Builder();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      b.setKey(id);
      b.setBot(isBot);
      b.setName(Lang.getSilentNotificationTitle(name, true, isSelfChat, isGroupChat, isChannel, isScheduled, isSilent));
      Bitmap bitmap = null; // TODO load from cache
      if (!U.isValidBitmap(bitmap)) {
        bitmap = isSelfChat ? TdlibNotificationUtils.buildSelfIcon(context.tdlib()) : TdlibNotificationUtils.buildLargeIcon(context.tdlib(), photo, colorId, letters, true, allowDownload);
      }
      if (U.isValidBitmap(bitmap)) {
        b.setIcon(IconCompat.createWithBitmap(bitmap));
      }
    } else if (Config.USE_GROUP_NAMES && isGroupChat) {
      b.setName(Lang.getSilentNotificationTitle(name, true, isSelfChat, isGroupChat, isChannel, isScheduled, isSilent));
    } else {
      b.setName("");
    }
    return b.build();
  }

  private static void addMessage (NotificationCompat.MessagingStyle style, CharSequence messageText, Person person, Tdlib tdlib, TdApi.Chat chat, List<TdlibNotification> notifications, long loadTimeout, boolean isRebuild, boolean isExclusivelyScheduled, boolean isExclusivelySilent) {
    TdlibNotification notification = notifications.get(0);
    long chatId = chat.id;
    boolean isMention = notification.group().isMention();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && tdlib.notifications().needContentPreview(chatId, isMention)) {

    }
    style.addMessage(new NotificationCompat.MessagingStyle.Message(Lang.getSilentNotificationTitle(messageText, false, tdlib.isSelfChat(chat), tdlib.isMultiChat(chat), tdlib.isChannelChat(chat), isExclusivelyScheduled, isExclusivelySilent), TimeUnit.SECONDS.toMillis(notification.getDate()), person));
  }

  private static void addMessage (NotificationCompat.MessagingStyle style, CharSequence messageText, Person person, Tdlib tdlib, TdApi.Chat chat, TdlibNotification notification, long loadTimeout, boolean isRebuild, boolean isExclusivelyScheduled, boolean isExclusivelySilent, boolean isOnlyPinned) {
    long chatId = chat.id;
    boolean isMention = notification.group().isMention();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && tdlib.notifications().needContentPreview(chatId, isMention)) {
      final long date = notification.getDate() * 1000l;
      TdlibNotificationMediaFile file = TdlibNotificationMediaFile.newFile(tdlib, chat, notification.getNotificationContent());
      if (file != null) {
        if (!isRebuild) {
          tdlib.files().downloadFileSync(file.file, loadTimeout, null, null);
        }
        if (TD.isFileLoaded(file.file)) {
          Uri uri = null;
          if (file.type == TdlibNotificationMediaFile.TYPE_LOTTIE_STICKER ||
              file.type == TdlibNotificationMediaFile.TYPE_WEBM_STICKER) {
            AtomicReference<TdApi.File> generatedFile = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            tdlib.client().send(new TdApi.PreliminaryUploadFile(new TdApi.InputFileGenerated(
              file.file.local.path,
              file.type == TdlibNotificationMediaFile.TYPE_LOTTIE_STICKER ?
                GenerationInfo.TYPE_LOTTIE_STICKER_PREVIEW :
                GenerationInfo.TYPE_VIDEO_STICKER_PREVIEW, 0),
              new TdApi.FileTypeSticker(),
              32
            ), result -> {
              switch (result.getConstructor()) {
                case TdApi.File.CONSTRUCTOR: {
                  TdApi.File uploadingFile = (TdApi.File) result;
                  tdlib.client().send(new TdApi.CancelPreliminaryUploadFile(uploadingFile.id), tdlib.okHandler());
                  tdlib.client().send(new TdApi.DownloadFile(uploadingFile.id, 32, 0, 0, true), downloadedFile -> {
                    switch (downloadedFile.getConstructor()) {
                      case TdApi.File.CONSTRUCTOR: {
                        generatedFile.set((TdApi.File) downloadedFile);
                        latch.countDown();
                        break;
                      }
                      case TdApi.Error.CONSTRUCTOR: {
                        Log.e("Cannot generate sticker preview (2): %s, path: %s", TD.toErrorString(downloadedFile), file.file.local.path);
                        break;
                      }
                    }
                  });
                  break;
                }
                case TdApi.Error.CONSTRUCTOR: {
                  Log.e("Cannot generate sticker preview: %s, path: %s", TD.toErrorString(result), file.file.local.path);
                  break;
                }
              }
            });
            try {
              latch.await(loadTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) { }
            TdApi.File resultFile = generatedFile.get();
            if (TD.isFileLoaded(resultFile)) {
              uri = U.contentUriFromFile(new File(resultFile.local.path));
            }
          } else {
            uri = U.contentUriFromFile(new File(file.file.local.path));
          }
          if (uri != null) {
            style.addMessage(new NotificationCompat.MessagingStyle.Message(messageText, date, person).setData("image/", uri));
            if (notification.isStickerContent()) {
              if (!StringUtils.isEmpty(messageText)) {
                style.addMessage(new NotificationCompat.MessagingStyle.Message(messageText, date + 1, person));
              }
            } else {
              CharSequence caption = notification.getTextRepresentation(tdlib, isOnlyPinned, true, null);
              if (!StringUtils.isEmpty(caption)) {
                style.addMessage(new NotificationCompat.MessagingStyle.Message(caption, date - 1, person));
              }
            }
            return;
          }
        }
      }
    }
    style.addMessage(new NotificationCompat.MessagingStyle.Message(Lang.getSilentNotificationTitle(messageText, false, tdlib.isSelfChat(chat), tdlib.isMultiChat(chat), tdlib.isChannelChat(chat), isExclusivelyScheduled, isExclusivelySilent), TimeUnit.SECONDS.toMillis(notification.getDate()), person));
  }

  public static NotificationCompat.MessagingStyle newMessagingStyle (TdlibNotificationManager context, TdApi.Chat chat, int messageCount, boolean areMentions, boolean arePinned, boolean areOnlyScheduled, boolean areOnlySilent, boolean allowDownload) {
    Tdlib tdlib = context.tdlib();
    TdApi.User user = context.myUser();
    NotificationCompat.MessagingStyle style;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && user != null) {
      style = new NotificationCompat.MessagingStyle(buildPerson(context, tdlib.isSelfChat(chat), tdlib.isMultiChat(chat), tdlib.isChannelChat(chat), user, null, false, false, allowDownload));
    } else {
      //noinspection deprecation
      style = new NotificationCompat.MessagingStyle("");
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      boolean isGroupConversation = !tdlib.isUserChat(chat) && !tdlib.isChannelChat(chat);
      if (isGroupConversation) {
        style.setConversationTitle(Lang.getNotificationTitle(chat.id, tdlib.chatTitle(chat), messageCount, tdlib.isSelfChat(chat), tdlib.isMultiChat(chat), tdlib.isChannelChat(chat), areMentions, arePinned, areOnlyScheduled, areOnlySilent));
      }
      style.setGroupConversation(isGroupConversation);
    } else {
      style.setConversationTitle(Lang.getNotificationTitle(chat.id, tdlib.chatTitle(chat), messageCount, tdlib.isSelfChat(chat), tdlib.isMultiChat(chat), tdlib.isChannelChat(chat), areMentions, arePinned, areOnlyScheduled, areOnlySilent));
      style.setGroupConversation(true);
    }
    return style;
  }

  private NotificationCompat.Builder buildCommonNotification (Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @Nullable TdlibNotificationSettings settings, final List<TdlibNotification> notifications, final int category, boolean isRebuild) {
    Tdlib tdlib = helper.tdlib();
    TdlibNotification lastNotification = notifications.get(notifications.size() - 1);
    long singleChatId = helper.findSingleChatId();
    long singleTargetMessageId = singleChatId != 0 ? helper.findSingleMessageId(singleChatId) : 0;
    long chatId = singleChatId != 0 ? singleChatId : lastNotification.group().getChatId();
    TdApi.Chat chat = tdlib.chatSync(chatId, CHAT_MAX_DELAY);
    if (chat == null) {
      Log.e(Log.TAG_FCM, "Not displaying notification, because chat is inaccessible: %d", chatId);
      return null;
    }
    int displayingChatsCount = singleChatId != 0 ? 1 : helper.calculateChatsCount(category);
    int displayingMessageCount = helper.calculateMessageCount(category);
    long timeMs = TimeUnit.SECONDS.toMillis(lastNotification.notification().date);

    NotificationCompat.Builder b = new NotificationCompat.Builder(context, helper.findCommonChannelId(category));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (allowPreview) {
        b.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);
      } else {
        b.setOnlyAlertOnce(settings == null);
      }
    }

    if (category == TdlibNotificationGroup.CATEGORY_SECRET && (Settings.instance().needHideSecretChats() || (singleChatId != 0 && tdlib.notifications().isShowPreviewEnabled(singleChatId, lastNotification.group().isMention())))) {
      b.setVisibility(NotificationCompat.VISIBILITY_SECRET);
    }

    b.setAutoCancel(Config.NOTIFICATION_AUTO_CANCEL_SPECIFIC);
    b.setSmallIcon(R.mipmap.app_notification);
    b.setWhen(timeMs);
    if (!Device.FLYME) {
      b.setNumber(badgeCount);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
      if (allowPreview) {
        b.setGroup(makeGroupKey(tdlib, category));
        b.setGroupSummary(true);
      }
      b.setSortKey(makeSortKey(lastNotification, true));
    }
    b.setContentIntent(TdlibNotificationUtils.newIntent(tdlib.id(), tdlib.settings().getLocalChatId(singleChatId), singleTargetMessageId));

    styleNotification(tdlib, b, singleChatId, displayingChatsCount == 1 ? chat : null, allowPreview);

    final boolean[] hasCustomText = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new boolean[1] : null;

    if (allowPreview) {
      Intent hideIntent = new Intent(UI.getAppContext(), TGRemoveAllReceiver.class);
      Intents.secureIntent(hideIntent, true);
      hideIntent.putExtra("account_id", tdlib.id());
      hideIntent.putExtra("category", category);
      PendingIntent hidePendingIntent = PendingIntent.getBroadcast(UI.getAppContext(), helper.getBaseNotificationId(category), hideIntent, Intents.mutabilityFlags(true));
      b.setDeleteIntent(hidePendingIntent);

      if (displayingChatsCount == 1) {
        Bitmap bitmap = TdlibNotificationUtils.buildLargeIcon(tdlib, chat, !isRebuild);
        if (U.isValidBitmap(bitmap)) {
          b.setLargeIcon(bitmap);
        }
      }

      boolean singleSender = false;
      String contentTitle;

      if (displayingChatsCount == 1) {
        contentTitle = null;

        if (ChatId.isUserChat(chat.id)) {
          singleSender = true;
        } else if (ChatId.isBasicGroup(chat.id) || TD.isSupergroup(chat.type)) {
          String singleSenderName = null;
          TdlibNotification prevNotification = null;
          for (TdlibNotification notification : notifications) {
            if (prevNotification != null && !prevNotification.isSameSender(notification)) {
              singleSenderName = null;
              break;
            }
            singleSenderName = notification.findSenderName();
            prevNotification = notification;
          }
          if (!StringUtils.isEmpty(singleSenderName)) {
            singleSender = true;
            contentTitle = Lang.getString(R.string.format_notificationGroupSender, singleSenderName, chat.title);
          }
        }
        if (contentTitle == null) {
          contentTitle = chat.title;
        }
      } else {
        contentTitle = BuildConfig.PROJECT_NAME;
      }
      String commonText = Lang.getUnreadBadge(displayingMessageCount, displayingChatsCount);
      CharSequence tickerText = getTickerText(tdlib, helper, allowPreview, chat, lastNotification, true, singleSender, hasCustomText);

      b.setContentTitle(contentTitle);
      b.setTicker(tickerText);
      b.setContentText(commonText);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && displayingChatsCount == 1) {
        if (notifications.size() > 1) {
          NotificationCompat.MessagingStyle style;
          final boolean isMention = lastNotification.group().isMention();
          final boolean onlyPinned = lastNotification.group().isOnlyPinned();
          final boolean onlyScheduled = lastNotification.group().isOnlyScheduled();
          final boolean onlySilent = lastNotification.group().isOnlyInitiallySilent();
          style = newMessagingStyle(tdlib.notifications(), chat, notifications.size(), isMention, onlyPinned, onlyScheduled, onlySilent, !isRebuild);
          boolean usePreview = allowPreview && tdlib.notifications().isShowPreviewEnabled(chat.id, lastNotification.group().isMention());
          for (TdlibNotification notification : notifications) {
            CharSequence preview;
            if (usePreview) {
              preview = notification.getTextRepresentation(tdlib, isMention && onlyPinned, true, hasCustomText);
            } else {
              preview = Lang.getString(R.string.YouHaveNewMessage);
            }
            addMessage(style, preview, buildPerson(tdlib.notifications(), chat, notification, onlyScheduled, onlySilent, !isRebuild), tdlib, chat, notification, MEDIA_LOAD_TIMEOUT, isRebuild, !onlyScheduled && notification.isScheduled(), !onlySilent && notification.isVisuallySilent(), onlyPinned);
          }
          b.setStyle(style);
        } else {
          b.setContentText(getTickerText(tdlib, helper, allowPreview, chat, lastNotification, false, singleSender, hasCustomText));
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        NotificationCompat.InboxStyle style;
        style = new NotificationCompat.InboxStyle();
        style.setBigContentTitle(contentTitle);
        for (int i = notifications.size() - 1, addedCount = 0; i >= 0 && addedCount < 7; i--, addedCount++) {
          TdlibNotification notification = notifications.get(i);
          style.addLine(getTickerText(tdlib, helper, allowPreview, tdlib.chatStrict(notification.group().getChatId()), notification, !singleSender, singleSender, hasCustomText));
        }
        boolean multi = tdlib.context().isMultiUser();
        if (multi && displayingChatsCount > 1) {
          style.setSummaryText(tdlib.accountShortName(category));
        } else {
          if (multi) {
            style.setSummaryText(commonText + Strings.DOT_SEPARATOR + tdlib.accountShortName(category));
          } else {
            style.setSummaryText(commonText);
          }
        }
        b.setStyle(style);
      }
    } else {
      String text = Lang.getString(R.string.NotificationLocked);
      b.setTicker(text);
      b.setContentText(text);
      b.setContentTitle(Lang.getString(R.string.NotificationLockedTitle));
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasCustomText != null) {
      b.setAllowSystemGeneratedContextualActions(allowPreview && hasCustomText[0]);
    }

    if (settings != null) {
      settings.apply(b, true);
    } else {
      TdlibNotificationSettings.applyStatic(b, tdlib, lastNotification.group(), true);
    }

    return b;
  }

  private static CharSequence getTickerText (Tdlib tdlib, TdlibNotificationHelper helper, boolean allowPreview, TdApi.Chat chat, TdlibNotification notification, boolean needPrefix, boolean singleSender, @Nullable boolean[] hasCustomText) {
    if (chat == null || !allowPreview) {
      return Lang.getString(R.string.YouHaveNewMessage);
    }
    boolean needPreview = helper.needPreview(notification.group());
    String prefix;
    if (needPrefix) {
      if (ChatId.isUserChat(chat.id)) {
        TdApi.User user = tdlib.chatUser(chat);
        prefix = user != null ? TD.getUserName(user.firstName, user.lastName) : null;
      } else if (TD.isChannel(chat.type)) {
        prefix = chat.title;
      } else {
        prefix = notification.findSenderName();
        if (!singleSender) {
          prefix = Lang.getString(R.string.format_notificationGroupSender, prefix, chat.title);
        }
      }
    } else {
      prefix = null;
    }
    CharSequence preview = notification.getTextRepresentation(tdlib, notification.group().isMention() && notification.group().isOnlyPinned(), needPreview, hasCustomText);
    return prefix != null ? Lang.getCharSequence(R.string.format_notificationTicker, prefix, preview) : preview;
  }

  // File referencing

  @Override
  public void onUpdateFile (TdApi.UpdateFile updateFile) { }
}
