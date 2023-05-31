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
 * File created on 04/01/2019
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.unsorted.Settings;

public class TdlibNotificationMediaFile {
  public static final int TYPE_IMAGE = 0;
  public static final int TYPE_STICKER = 1;
  public static final int TYPE_LOTTIE_STICKER = 2;
  public static final int TYPE_WEBM_STICKER = 3;
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
    switch (type) {
      case TYPE_STICKER:
      case TYPE_LOTTIE_STICKER:
      case TYPE_WEBM_STICKER:
        return true;
    }
    return false;
  }

  private static int toType (TdApi.StickerFormat format) {
    switch (format.getConstructor()) {
      case TdApi.StickerFormatWebm.CONSTRUCTOR:
        return TYPE_WEBM_STICKER;
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
        return TYPE_LOTTIE_STICKER;
      case TdApi.StickerFormatWebp.CONSTRUCTOR:
        return TYPE_STICKER;
    }
    throw new UnsupportedOperationException(format.toString());
  }

  @Nullable
  public static TdlibNotificationMediaFile newFile (Tdlib tdlib, TdApi.Chat chat, TdApi.NotificationType notificationType) {
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
            if (!photo.isSecret && !photo.hasSpoiler) {
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
          case TdApi.MessageAnimatedEmoji.CONSTRUCTOR: {
            TdApi.MessageAnimatedEmoji animatedEmoji = (TdApi.MessageAnimatedEmoji) message.content;
            if (animatedEmoji.animatedEmoji.sticker != null && !Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI)) {
              TdApi.Sticker sticker = animatedEmoji.animatedEmoji.sticker;
              photoFile = animatedEmoji.animatedEmoji.sticker.sticker;
              type = toType(sticker.format);
              width = sticker.width;
              height = sticker.height;
            }
            break;
          }
          case TdApi.MessageSticker.CONSTRUCTOR: {
            TdApi.Sticker sticker = ((TdApi.MessageSticker) message.content).sticker;
            photoFile = sticker.sticker;
            type = toType(sticker.format);
            width = sticker.width;
            height = sticker.height;
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
              type = toType(sticker.sticker.format);
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
