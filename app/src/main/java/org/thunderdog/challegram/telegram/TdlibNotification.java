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
 * File created on 26/12/2018
 */
package org.thunderdog.challegram.telegram;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.Filter;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class TdlibNotification implements Comparable<TdlibNotification> {
  private static final int FLAG_EDITED = 1;
  private static final int FLAG_EDITED_VISIBLE = 1 << 1;

  private Tdlib tdlib;
  private final int id;

  private TdApi.Notification notification;
  private TdlibNotificationGroup group;

  private int flags;

  public TdlibNotification (int id) {
    this.id = id;
  }

  public TdlibNotification (Tdlib tdlib, TdApi.Notification notification, TdlibNotificationGroup group) {
    this.tdlib = tdlib;
    this.id = notification.id;
    this.notification = notification;
    this.group = group;
    this.flags = tdlib.settings().getNotificationData(id);
  }

  private void setFlags (int flags) {
    if (this.flags != flags) {
      this.flags = flags;
      tdlib.settings().setNotificationData(id, flags);
    }
  }

  public void markAsEdited (boolean needDisplay) {
    int flag = needDisplay ? FLAG_EDITED : FLAG_EDITED_VISIBLE;
    setFlags(this.flags | flag);
  }

  public boolean isEdited () {
    return BitwiseUtils.hasFlag(this.flags, FLAG_EDITED);
  }

  public boolean isEditedVisible () {
    return BitwiseUtils.hasFlag(this.flags, FLAG_EDITED_VISIBLE);
  }

  public static CharSequence wrapEdited (CharSequence content, boolean isEdited, boolean isEditedVisible) {
    if (isEdited) {
      return Lang.getCharSequence(R.string.format_edited, content);
    } else if (isEditedVisible) {
      return Lang.getCharSequence(R.string.format_editedVisible, content);
    } else {
      return content;
    }
  }

  public CharSequence wrapEdited (CharSequence content) {
    return wrapEdited(content, isEdited(), isEditedVisible());
  }

  public boolean isHidden () {
    boolean isHidden = group.isHidden(id);
    return isHidden && group.needRemoveDismissedMessages();
  }

  public int getId () {
    return id;
  }

  public long getChatId () {
    return group.getChatId();
  }

  public boolean isSelfChat () {
    return group.isSelfChat();
  }

  public int getDate () {
    return notification.date;
  }

  public boolean isScheduled () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR:
        return ((TdApi.NotificationTypeNewMessage) notification.type).message.isOutgoing;
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return ((TdApi.NotificationTypeNewPushMessage) notification.type).isOutgoing;
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        return false;
      default: {
        Td.assertNotificationType_dd6d967f();
        throw Td.unsupported(notification.type);
      }
    }
  }

  public boolean isVisuallySilent () { // Display bell icon
    return notification.isSilent;
  }

  public TdApi.Message findMessage () {
    return notification.type.getConstructor() == TdApi.NotificationTypeNewMessage.CONSTRUCTOR ? ((TdApi.NotificationTypeNewMessage) notification.type).message : null;
  }

  public boolean canMergeWith (@Nullable TdlibNotification n) {
    if (n != null && notification.type.getConstructor() == TdApi.NotificationTypeNewMessage.CONSTRUCTOR && n.notification.type.getConstructor() == TdApi.NotificationTypeNewMessage.CONSTRUCTOR) {
      TdApi.Message m = ((TdApi.NotificationTypeNewMessage) notification.type).message;
      TdApi.Message other = ((TdApi.NotificationTypeNewMessage) n.notification.type).message;
      return m.chatId == other.chatId && Td.equalsTo(m.senderId, other.senderId) && (m.mediaAlbumId != 0 && m.mediaAlbumId == other.mediaAlbumId) || (m.forwardInfo != null && other.forwardInfo != null && m.date == other.date);
    }
    return false;
  }

  public boolean isPinnedMessage () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR:
        return Td.isPinned(((TdApi.NotificationTypeNewMessage) notification.type).message.content);
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return Td.isPinned(((TdApi.NotificationTypeNewPushMessage) notification.type).content);
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        break;
    }
    return false;
  }

  public boolean needContentPreview () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR: {
        TdApi.Message message = ((TdApi.NotificationTypeNewMessage) notification.type).message;
        return !Td.isSecret(message.content) && ((TdApi.NotificationTypeNewMessage) notification.type).message.selfDestructType == null;
      }
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR: {
        TdApi.PushMessageContent push = ((TdApi.NotificationTypeNewPushMessage) notification.type).content;
        switch (push.getConstructor()) {
          case TdApi.PushMessageContentPhoto.CONSTRUCTOR: {
            TdApi.PushMessageContentPhoto photo = ((TdApi.PushMessageContentPhoto) push);
            return photo.photo != null && !photo.isSecret;
          }
          case TdApi.PushMessageContentSticker.CONSTRUCTOR: {
            TdApi.PushMessageContentSticker sticker = (TdApi.PushMessageContentSticker) push;
            return sticker.sticker != null;
          }
        }
        return false;
      }
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        break;
    }
    return false;
  }

  public long findMessageId () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR:
        return ((TdApi.NotificationTypeNewMessage) notification.type).message.id;
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return ((TdApi.NotificationTypeNewPushMessage) notification.type).messageId;
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        break;
    }
    return 0;
  }

  public boolean isSameSender (TdlibNotification other) {
    if (this == other)
      return true;
    long senderId = findSenderId();
    long otherSenderId = other.findSenderId();
    return senderId == otherSenderId && senderId != 0;
  }

  public boolean isSynced () {
    return notification.type.getConstructor() != TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR;
  }

  public long findSenderId () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR: {
        TdApi.Message message = ((TdApi.NotificationTypeNewMessage) notification.type).message;
        return Td.getSenderId(message);
      }
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR: {
        return Td.getSenderId(((TdApi.NotificationTypeNewPushMessage) notification.type).senderId);
      }
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        return ChatId.fromUserId(tdlib.chatUserId(getChatId()));
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
        break;
    }
    return 0;
  }

  public String findSenderName () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR: {
        TdApi.Message message = ((TdApi.NotificationTypeNewMessage) notification.type).message;
        return tdlib.senderName(message, true, false);
      }
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR: {
        TdApi.NotificationTypeNewPushMessage push = (TdApi.NotificationTypeNewPushMessage) notification.type;
        if (!StringUtils.isEmpty(push.senderName))
          return push.senderName;
        return tdlib.senderName(push.senderId);
      }
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        return tdlib.cache().userName(tdlib.chatUserId(getChatId()));
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
        break;
    }
    return null;
  }

  public boolean isNewSecretChat () {
    return notification.type.getConstructor() == TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR;
  }

  public TdApi.NotificationType getNotificationContent () {
    return notification.type;
  }

  public boolean isStickerContent () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR:
        return Td.isSticker(((TdApi.NotificationTypeNewMessage) notification.type).message.content);
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return Td.isSticker(((TdApi.NotificationTypeNewPushMessage) notification.type).content);
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        break;
    }
    return false;
  }

  public String getContentText () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR: {
        TdApi.FormattedText text = Td.textOrCaption(((TdApi.NotificationTypeNewMessage) notification.type).message.content);
        return Td.getText(text);
      }
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return Td.getText(((TdApi.NotificationTypeNewPushMessage) notification.type).content);
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        break;
    }
    return null;
  }

  public TdApi.Notification notification () {
    return notification;
  }

  public TdlibNotificationGroup group () {
    return group;
  }

  @Override
  public int compareTo (TdlibNotification o) {
    return Integer.compare(id, o.id);
  }

  public CharSequence getTextRepresentation (Tdlib tdlib, boolean onlyPinned, boolean allowContent, List<TdlibNotification> mergedList, boolean isEdited, boolean isEditedVisible, @Nullable boolean[] hasCustomText) {
    List<TdApi.Message> messages = new ArrayList<>(mergedList.size());
    boolean isForward = false;
    for (TdlibNotification notification : mergedList) {
      TdApi.Message message = notification.findMessage();
      if (ChatId.isSecret(group.getChatId()) && message.selfDestructType != null) {
        return Lang.plural(R.string.xNewMessages, mergedList.size());
      }
      if (message.forwardInfo != null) {
        isForward = true;
      }
      messages.add(message);
    }
    TD.ContentPreview content;
    if (isForward) {
      content = new TD.ContentPreview(TD.EMOJI_FORWARD, 0, Lang.plural(R.string.xForwards, mergedList.size()), true);
    } else {
      Tdlib.Album album = new Tdlib.Album(messages);
      content = TD.getAlbumPreview(tdlib, messages.get(0), album, allowContent);
    }
    if (hasCustomText != null && !content.isTranslatable) {
      hasCustomText[0] = true;
    }
    return wrapEdited(getPreview(content), isEdited, isEditedVisible);
  }

  public CharSequence getTextRepresentation (Tdlib tdlib, boolean onlyPinned, boolean allowContent, @Nullable boolean[] hasCustomText) {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR: {
        TdApi.Message message = ((TdApi.NotificationTypeNewMessage) notification.type).message;

        if (ChatId.isSecret(group.getChatId()) && message.selfDestructType != null) {
          return Lang.getString(R.string.YouHaveNewMessage);
        }

        // TODO move this to TD.getNotificationPreview?
        if (Td.isPinned(message.content)) {
          long messageId = ((TdApi.MessagePinMessage) message.content).messageId;
          TdApi.Message pinnedMessage = messageId != 0 ? tdlib.getMessageLocally(message.chatId, messageId) : null;
          if (onlyPinned) {
            if (pinnedMessage != null)
              message = pinnedMessage;
          } else {
            return wrapEdited(Lang.getPinnedMessageText(tdlib, message.senderId, pinnedMessage, false));
          }
        }

        TD.ContentPreview content = TD.getNotificationPreview(tdlib, getChatId(), message, allowContent);
        if (hasCustomText != null && !content.isTranslatable) {
          hasCustomText[0] = true;
        }
        return wrapEdited(getPreview(content));
      }
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR: {
        return Lang.getString(R.string.YouHaveNewMessage);
      }
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR: {
        TdApi.NotificationTypeNewPushMessage push = (TdApi.NotificationTypeNewPushMessage) notification.type;
        TD.ContentPreview content = TD.getNotificationPreview(tdlib, getChatId(), push, allowContent);
        if (content == null)
          throw Td.unsupported(push.content);
        if (hasCustomText != null && !content.isTranslatable) {
          hasCustomText[0] = true;
        }
        return wrapEdited(getPreview(content));
      }
    }
    return null;
  }

  private CharSequence getPreview (TD.ContentPreview content) {
    TdApi.FormattedText formattedText = content.buildFormattedText(false);
    CharSequence text = TD.toCharSequence(formattedText, false, false);
    if (text instanceof Spanned) {
      Spanned spanned = (Spanned) text;
      URLSpan[] spans = spanned.getSpans(0, text.length(), URLSpan.class);
      if (spans != null && spans.length > 0) {
        SpannableStringBuilder b = null;
        for (int i = 0; i < spans.length; i++) {
          URLSpan span = spans[i];
          int start = spanned.getSpanStart(span);
          int end = spanned.getSpanEnd(span);
          if (start != -1 && end != -1) {
            if (b == null) {
              b = new SpannableStringBuilder(text);
            }
            ForegroundColorSpan colorSpan = new ForegroundColorSpan(tdlib.getColor(ColorId.notificationLink));
            b.setSpan(colorSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          }
        }
        if (b != null) {
          text = b;
        }
      }
    }
    return applyCustomEmoji(text, formattedText);
  }

  private CharSequence applyCustomEmoji (CharSequence text, TdApi.FormattedText formattedText) {
    if (!Config.SYSTEM_SUPPORTS_CUSTOM_IMAGE_SPANS) {
      // No need to wait for files that won't be used.
      return text;
    }
    if (formattedText.entities == null || formattedText.entities.length == 0) {
      return text;
    }
    TdApi.TextEntity lastSpoilerEntity = null;
    LongSparseArray<List<TdApi.TextEntity>> customEmojiEntities = null;
    for (TdApi.TextEntity entity : formattedText.entities) {
      //noinspection SwitchIntDef
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
          lastSpoilerEntity = entity;
          break;
        case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
          if (lastSpoilerEntity == null || entity.offset >= lastSpoilerEntity.offset + lastSpoilerEntity.length) {
            if (customEmojiEntities == null) {
              customEmojiEntities = new LongSparseArray<>();
            }
            long customEmojiId = ((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId;
            List<TdApi.TextEntity> list = customEmojiEntities.get(customEmojiId);
            if (list == null) {
              list = new ArrayList<>();
              customEmojiEntities.put(customEmojiId, list);
            }
            list.add(entity);
          }
          break;
      }
    }
    if (customEmojiEntities == null || customEmojiEntities.isEmpty()) {
      return text;
    }
    TDLib.Tag.notifications("Preparing to fetch info about %d custom emoji", customEmojiEntities.size());
    CountDownLatch customEmojiLatch = new CountDownLatch(customEmojiEntities.size());
    LongSparseArray<TdlibEmojiManager.Entry> customEmojis = new LongSparseArray<>();
    LongSet awaitingCustomEmojiIds = new LongSet();
    Filter<TdlibEmojiManager.Entry> filter = (entry) ->
      !entry.isNotFound() && entry.value != null && entry.value.thumbnail != null;
    TdlibEmojiManager.Watcher watcher = (context, entry) -> {
      synchronized (customEmojis) {
        if (filter.accept(entry)) {
          customEmojis.put(entry.customEmojiId, entry);
        }
        awaitingCustomEmojiIds.remove(entry.customEmojiId);
      }
      customEmojiLatch.countDown();
    };
    for (int i = 0; i < customEmojiEntities.size(); i++) {
      long customEmojiId = customEmojiEntities.keyAt(i);
      TdlibEmojiManager.Entry entry = tdlib.emoji().findOrPostponeRequest(customEmojiId, watcher);
      if (entry != null) {
        if (filter.accept(entry)) {
          customEmojis.put(customEmojiId, entry);
        }
        customEmojiLatch.countDown();
      } else {
        awaitingCustomEmojiIds.add(customEmojiId);
      }
    }
    // TODO: request all custom emojis before getting to this method,
    // e.g. in a separate loop before notification gets to TdlibNotificationStyle
    tdlib.emoji().performPostponedRequests();
    long awaitStartTimeMs = SystemClock.uptimeMillis();
    boolean awaitEmojiSuccess;
    try {
      awaitEmojiSuccess = customEmojiLatch.await(TdlibNotificationStyle.MEDIA_LOAD_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      awaitEmojiSuccess = false;
    }
    SparseArrayCompat<LongSet> fileToCustomEmojiIds = new SparseArrayCompat<>();
    SparseArrayCompat<ImageFile> files = new SparseArrayCompat<>();
    synchronized (customEmojis) {
      TDLib.Tag.notifications(
        "Fetched %d out of %d custom emoji, success: %b, elapsed: %dms",
        customEmojis.size(),
        customEmojiEntities.size(),
        awaitEmojiSuccess,
        SystemClock.uptimeMillis() - awaitStartTimeMs
      );
      if (!awaitEmojiSuccess) {
        for (long customEmojiId : awaitingCustomEmojiIds) {
          tdlib.emoji().forgetWatcher(customEmojiId, watcher);
        }
      }
      for (int i = 0; i < customEmojis.size(); i++) {
        TdlibEmojiManager.Entry entry = customEmojis.valueAt(i);
        //noinspection ConstantConditions
        int thumbnailFileId = entry.value.thumbnail.file.id;
        ImageFile thumbnailFile = files.get(thumbnailFileId);
        if (thumbnailFile == null) {
          thumbnailFile = TD.toImageFile(tdlib, entry.value.thumbnail);
          if (thumbnailFile != null) {
            thumbnailFile.setSize(Screen.dp(15f));
            thumbnailFile.setNoBlur();
            files.put(thumbnailFileId, thumbnailFile);
          }
        }
        if (thumbnailFile != null) {
          LongSet childCustomEmojiIds = fileToCustomEmojiIds.get(thumbnailFileId);
          if (childCustomEmojiIds == null) {
            childCustomEmojiIds = new LongSet();
            fileToCustomEmojiIds.put(thumbnailFileId, childCustomEmojiIds);
          }
          childCustomEmojiIds.add(entry.customEmojiId);
        }
      }
    }
    if (files.isEmpty()) {
      return text;
    }
    TDLib.Tag.notifications("Downloading %d emoji files", files.size());
    CountDownLatch downloadLatch = new CountDownLatch(files.size());
    for (int i = 0; i < files.size(); i++) {
      TdApi.File file = files.valueAt(i).getFile();
      tdlib.client().send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), result -> {
        switch (result.getConstructor()) {
          case TdApi.File.CONSTRUCTOR:
            synchronized (file) {
              Td.copyTo((TdApi.File) result, file);
            }
            break;
          case TdApi.Error.CONSTRUCTOR:
            TDLib.Tag.notifications("Failed to fetch one of emoji files: %s", TD.toErrorString(result));
            break;
        }
        downloadLatch.countDown();
      });
    }
    awaitStartTimeMs = SystemClock.uptimeMillis();
    boolean awaitFilesSuccess;
    try {
      awaitFilesSuccess = downloadLatch.await(TdlibNotificationStyle.MEDIA_LOAD_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      awaitFilesSuccess = false;
    }
    TDLib.Tag.notifications(
      "Downloaded %d emoji files, success: %b, elapsed: %d",
      files.size(),
      awaitFilesSuccess,
      SystemClock.uptimeMillis() - awaitStartTimeMs
    );

    SpannableStringBuilder b = null;
    for (int i = 0; i < files.size(); i++) {
      ImageFile previewFile = files.valueAt(i);
      if (previewFile == null)
        continue;
      int thumbnailFileId = previewFile.getFile().id;
      synchronized (previewFile.getFile()) {
        if (!TD.isFileLoaded(previewFile.getFile())) {
          continue;
        }
      }
      LongSet childCustomEmojiIds = fileToCustomEmojiIds.get(thumbnailFileId);
      if (childCustomEmojiIds == null)
        continue;
      Bitmap bitmap = ImageReader.readImage(previewFile, previewFile.getFilePath());
      if (bitmap != null) {
        for (Long customEmojiId : childCustomEmojiIds) {
          List<TdApi.TextEntity> entities = customEmojiEntities.get(customEmojiId);
          if (entities != null) {
            for (TdApi.TextEntity entity : entities) {
              if (b == null) {
                b = new SpannableStringBuilder(text);
              }
              ImageSpan imageSpan = new ImageSpan(
                UI.getAppContext(),
                bitmap,
                ImageSpan.ALIGN_BASELINE
              );
              b.setSpan(imageSpan,
                entity.offset,
                entity.offset + entity.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
              );
              b.setSpan(new UnderlineSpan(),
                entity.offset,
                entity.offset + entity.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
              );
            }
          }
        }
      }
    }
    return b != null ? b : text;
  }
}
