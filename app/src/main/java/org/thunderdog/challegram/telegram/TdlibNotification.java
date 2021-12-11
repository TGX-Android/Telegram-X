package org.thunderdog.challegram.telegram;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

/**
 * Date: 26/12/2018
 * Author: default
 */
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
    return BitwiseUtils.getFlag(this.flags, FLAG_EDITED);
  }

  public boolean isEditedVisible () {
    return BitwiseUtils.getFlag(this.flags, FLAG_EDITED_VISIBLE);
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
    }
    throw new UnsupportedOperationException(notification.type.toString());
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
        return ((TdApi.NotificationTypeNewMessage) notification.type).message.content.getConstructor() == TdApi.MessagePinMessage.CONSTRUCTOR;
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return TD.isPinnedMessagePushType(((TdApi.NotificationTypeNewPushMessage) notification.type).content);
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
        return !TD.isSecret(message) && ((TdApi.NotificationTypeNewMessage) notification.type).message.ttl == 0;
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
        return ((TdApi.NotificationTypeNewMessage) notification.type).message.content.getConstructor() == TdApi.MessageSticker.CONSTRUCTOR;
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return ((TdApi.NotificationTypeNewPushMessage) notification.type).content.getConstructor() == TdApi.PushMessageContentSticker.CONSTRUCTOR;
      case TdApi.NotificationTypeNewCall.CONSTRUCTOR:
      case TdApi.NotificationTypeNewSecretChat.CONSTRUCTOR:
        break;
    }
    return false;
  }

  public String getContentText () {
    switch (notification.type.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR:
        return TD.getTextFromMessage(((TdApi.NotificationTypeNewMessage) notification.type).message);
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR:
        return TD.getTextFromMessage(((TdApi.NotificationTypeNewPushMessage) notification.type).content);
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
      if (ChatId.isSecret(group.getChatId()) && message.ttl != 0) {
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

        if (ChatId.isSecret(group.getChatId()) && message.ttl != 0) {
          return Lang.getString(R.string.YouHaveNewMessage);
        }

        // TODO move this to TD.getNotificationPreview?
        switch (message.content.getConstructor()) {
          case TdApi.MessagePinMessage.CONSTRUCTOR: {
            long messageId = ((TdApi.MessagePinMessage) message.content).messageId;
            TdApi.Message pinnedMessage = messageId != 0 ? tdlib.getMessageLocally(message.chatId, messageId) : null;
            if (onlyPinned) {
              if (pinnedMessage != null)
                message = pinnedMessage;
            } else {
              return wrapEdited(Lang.getPinnedMessageText(tdlib, message.senderId, pinnedMessage, false));
            }
            break;
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
          throw new UnsupportedOperationException(Integer.toString(push.content.getConstructor()));
        if (hasCustomText != null && !content.isTranslatable) {
          hasCustomText[0] = true;
        }
        return wrapEdited(getPreview(content));
      }
    }
    return null;
  }

  private CharSequence getPreview (TD.ContentPreview content) {
    CharSequence text = TD.toCharSequence(content.buildFormattedText(false), false);
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
            if (b == null)
              b = new SpannableStringBuilder(text);
            ForegroundColorSpan colorSpan = new ForegroundColorSpan(tdlib.getColor(R.id.theme_color_notificationLink));
            b.setSpan(colorSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          }
        }
        if (b != null)
          return b;
      }
    }
    return text;
  }
}
