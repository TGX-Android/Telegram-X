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
 * File created on 09/02/2024 at 18:34
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;

import me.vkryl.td.ChatId;

public class MessageEditMediaPending implements TdlibEditMediaManager.UploadFuture.Callback {
  public static final int FUTURE_ID_MAIN = 0;
  public static final int FUTURE_ID_THUMB = 1;

  public MessageEditMediaUploadCallback callback;
  public final long chatId, messageId;

  public final TdApi.InputFile inputFile;
  public final TdApi.InputFile inputFileThumbnail;
  public final TdApi.InputMessageContent content;
  private final TdlibEditMediaManager.UploadFuture inputFileFuture;
  private final @Nullable TdlibEditMediaManager.UploadFuture inputFileThumbnailFuture;
  private final @NonNull ImageFile preview;

  MessageEditMediaPending (Tdlib tdlib, long chatId, long messageId, TdApi.InputMessageContent content, @NonNull ImageFile preview) {
    this.chatId = chatId;
    this.messageId = messageId;
    this.content = content;
    this.inputFile = TD.getInputFile(content);
    this.inputFileThumbnail = TD.getInputFileThumbnail(content);
    this.preview = preview;

    final boolean isSecret = ChatId.isSecret(chatId);

    this.inputFileFuture = new TdlibEditMediaManager.UploadFuture(tdlib, FUTURE_ID_MAIN, inputFile, isSecret ? new TdApi.FileTypeSecret() : TD.toFileType(content), this);
    this.inputFileThumbnailFuture = (inputFileThumbnail != null) ? new TdlibEditMediaManager.UploadFuture(tdlib, FUTURE_ID_THUMB, inputFileThumbnail, isSecret ? new TdApi.FileTypeSecretThumbnail() : new TdApi.FileTypeThumbnail(), this) : null;
  }

  public void init (MessageEditMediaUploadCallback callback) {
    this.callback = callback;
    inputFileFuture.init();
    if (inputFileThumbnailFuture != null) {
      inputFileThumbnailFuture.init();
    }
  }

  public boolean isPhoto () {
    return content.getConstructor() == TdApi.InputMessagePhoto.CONSTRUCTOR;
  }

  public boolean isWebp () {
    return preview.isWebp();
  }

  public TdApi.Photo getPhoto () {
    if (content.getConstructor() != TdApi.InputMessagePhoto.CONSTRUCTOR) {
      throw new IllegalStateException();
    }
    TdApi.InputMessagePhoto photo = (TdApi.InputMessagePhoto) content;

    return new TdApi.Photo(photo.addedStickerFileIds != null && photo.addedStickerFileIds.length > 0, null, new TdApi.PhotoSize[]{
      new TdApi.PhotoSize("i", inputFileFuture.file, photo.width, photo.height, null)
    });
  }

  public boolean isVideo () {
    return content.getConstructor() == TdApi.InputMessageVideo.CONSTRUCTOR;
  }

  public TdApi.Video getVideo () {
    if (content.getConstructor() != TdApi.InputMessageVideo.CONSTRUCTOR) {
      throw new IllegalStateException();
    }
    TdApi.InputMessageVideo video = (TdApi.InputMessageVideo) content;

    return new TdApi.Video(video.duration, video.width, video.height, "",
      ((ImageGalleryFile) preview).getVideoMimeType(),
      video.addedStickerFileIds != null && video.addedStickerFileIds.length > 0, video.supportsStreaming, null,
      inputFileThumbnailFuture != null ?
        new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), video.thumbnail.width, video.thumbnail.height, inputFileThumbnailFuture.file) : null,
      inputFileFuture.file);
  }

  public boolean isAnimation () {
    return content.getConstructor() == TdApi.InputMessageAnimation.CONSTRUCTOR;
  }

  public TdApi.Animation getAnimation () {
    if (content.getConstructor() != TdApi.InputMessageAnimation.CONSTRUCTOR) {
      throw new IllegalStateException();
    }
    TdApi.InputMessageAnimation animation = (TdApi.InputMessageAnimation) content;

    return new TdApi.Animation(animation.duration, animation.width, animation.height, "",
      ((ImageGalleryFile) preview).getVideoMimeType(), animation.addedStickerFileIds != null && animation.addedStickerFileIds.length > 0, null,
      inputFileThumbnailFuture != null ?
        new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), animation.thumbnail.width, animation.thumbnail.height, inputFileThumbnailFuture.file) : null,
      inputFileFuture.file);
  }

  public boolean hasSpoiler () {
    switch (content.getConstructor()) {
      case TdApi.InputMessagePhoto.CONSTRUCTOR:
        return ((TdApi.InputMessagePhoto) content).hasSpoiler;
      case TdApi.InputMessageVideo.CONSTRUCTOR:
        return ((TdApi.InputMessageVideo) content).hasSpoiler;
      case TdApi.InputMessageAnimation.CONSTRUCTOR:
        return ((TdApi.InputMessageAnimation) content).hasSpoiler;
      default:
        return false;
    }
  }

  public void cancel () {
    inputFileFuture.cancel();
    if (inputFileThumbnailFuture != null) {
      inputFileThumbnailFuture.cancel();
    }
  }

  private boolean fileCreated;
  private boolean thumbCreated;

  @Override
  public void onFileUpdate (int id, long fileId, TdApi.File file) {
    boolean needUpdate = false;

    if (id == FUTURE_ID_MAIN && !fileCreated) {
      fileCreated = true;
      needUpdate = true;
    }

    if (id == FUTURE_ID_THUMB && !thumbCreated) {
      thumbCreated = true;
      needUpdate = true;
    }

    if (needUpdate && inputFileFuture.file != null && (inputFileThumbnailFuture == null || inputFileThumbnailFuture.file != null)) {
      callback.onMediaPreliminaryUploadStart(this, inputFileFuture.file);
    }
  }

  @Override
  public void onComplete (int id, long fileId, TdApi.File file) {
    checkStatus();
  }

  private boolean failed;

  @Override
  public void onFail (int id, boolean isCanceled) {
    if (failed) {
      return;
    }
    failed = true;
    cancel();
    callback.onMediaPreliminaryUploadFailed(this, isCanceled);
  }

  public boolean isCompleted () {
    return inputFileFuture.isCompleted() && (inputFileThumbnailFuture == null || inputFileThumbnailFuture.isCompleted());
  }

  public TdApi.File getFile () {
    return inputFileFuture.file;
  }

  private void checkStatus () {
    if (isCompleted()) {
      TD.setInputFile(content, new TdApi.InputFileId(inputFileFuture.file.id));
      if (inputFileThumbnailFuture != null) {
        TD.setInputFileThumbnail(content, new TdApi.InputFileId(inputFileThumbnailFuture.file.id));
      }
      callback.onMediaPreliminaryUploadComplete(this, content);
    }
  }
}
