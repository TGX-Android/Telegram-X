package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;

import me.vkryl.td.Td;

/**
 * Date: 04/01/2019
 * Author: default
 */
public class TdlibNotificationMediaFile {
  public static final int TYPE_IMAGE = 0;
  public static final int TYPE_STICKER = 1;
  public static final int TYPE_ANIMATED_STICKER = 2;
  public final TdApi.File file;
  public final int type;
  public final boolean needBlur;
  public final int width, height;

  public TdlibNotificationMediaFile (TdApi.File file, int type, boolean needBlur, int width, int height) {
    this.file = file;
    this.type = type;
    this.needBlur = needBlur;
    this.width = width;
    this.height = height;
  }

  public boolean isSticker () {
    return type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER;
  }

  public static @Nullable
  TdlibNotificationMediaFile newFile (Tdlib tdlib, TdApi.Chat chat, TdApi.NotificationType notificationType) {
    if (notificationType == null) {
      return null;
    }
    int type = TYPE_IMAGE;
    boolean needBlur = false;
    TdApi.File photoFile = null;
    int width = 0, height = 0;
    switch (notificationType.getConstructor()) {
      case TdApi.NotificationTypeNewMessage.CONSTRUCTOR: {
        TdApi.Message message = ((TdApi.NotificationTypeNewMessage) notificationType).message;
        switch (message.content.getConstructor()) {
          case TdApi.MessagePhoto.CONSTRUCTOR: {
            TdApi.MessagePhoto photo = (TdApi.MessagePhoto) message.content;
            if (!photo.isSecret) {
              TdApi.PhotoSize target = MediaWrapper.buildTargetFile(photo.photo);
              if (target != null && (TD.isFileLoaded(target.photo) || tdlib.files().canAutomaticallyDownload(target.photo, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, chat.type))) {
                photoFile = target.photo;
                width = target.width;
                height = target.height;
              }
              needBlur = true;
            }
            break;
          }
          case TdApi.MessageSticker.CONSTRUCTOR: {
            TdApi.MessageSticker sticker = (TdApi.MessageSticker) message.content;
            photoFile = sticker.sticker.sticker;
            type = Td.isAnimated(sticker.sticker.type) ? TYPE_ANIMATED_STICKER : TYPE_STICKER;
            width = sticker.sticker.width;
            height = sticker.sticker.height;
            break;
          }
          default:
            return null;
        }
        break;
      }
      case TdApi.NotificationTypeNewPushMessage.CONSTRUCTOR: {
        TdApi.PushMessageContent push = ((TdApi.NotificationTypeNewPushMessage) notificationType).content;
        switch (push.getConstructor()) {
          case TdApi.PushMessageContentPhoto.CONSTRUCTOR: {
            TdApi.PushMessageContentPhoto photo = (TdApi.PushMessageContentPhoto) push;
            if (!photo.isSecret && photo.photo != null) {
              TdApi.PhotoSize target = MediaWrapper.buildTargetFile(photo.photo);
              if (target != null && (TD.isFileLoaded(target.photo) || tdlib.files().canAutomaticallyDownload(target.photo, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, chat.type))) {
                photoFile = target.photo;
                width = target.width;
                height = target.height;
              }
              needBlur = true;
            }
            break;
          }
          case TdApi.PushMessageContentSticker.CONSTRUCTOR: {
            TdApi.PushMessageContentSticker sticker = (TdApi.PushMessageContentSticker) push;
            if (sticker.sticker != null) {
              photoFile = sticker.sticker.sticker;
              type = Td.isAnimated(sticker.sticker.type) ? TYPE_ANIMATED_STICKER : TYPE_STICKER;
              width = sticker.sticker.width;
              height = sticker.sticker.height;
            }
            break;
          }
        }
        break;
      }
    }
    if (photoFile != null) {
      return new TdlibNotificationMediaFile(photoFile, type, needBlur, width, height);
    }
    return null;
  }
}
