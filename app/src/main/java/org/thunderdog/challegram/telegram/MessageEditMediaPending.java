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
import org.thunderdog.challegram.component.attach.MediaToReplacePickerManager;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.BitwiseUtils;
import tgx.td.ChatId;
import tgx.td.Td;

public class MessageEditMediaPending implements Tdlib.UploadFutureSimple.Callback {
  public final long chatId, messageId;

  public final TdApi.InputFile inputFile;
  public final TdApi.InputFile inputFileThumbnail;
  public final TdApi.InputMessageContent content;
  private final Tdlib.UploadFutureSimple inputFileFuture;
  private final @Nullable Tdlib.UploadFutureSimple inputFileThumbnailFuture;
  private final @NonNull MediaToReplacePickerManager.LocalPickedFile pickedFile;

  MessageEditMediaPending (Tdlib tdlib, long chatId, long messageId, TdApi.InputMessageContent content, @NonNull MediaToReplacePickerManager.LocalPickedFile pickedFile) {
    this.chatId = chatId;
    this.messageId = messageId;
    this.content = content;
    this.inputFile = TD.getInputFile(content);
    this.inputFileThumbnail = TD.getInputFileThumbnail(content);
    this.pickedFile = pickedFile;

    final boolean isSecret = ChatId.isSecret(chatId);

    this.inputFileFuture = new Tdlib.UploadFutureSimple(tdlib, inputFile, isSecret ? new TdApi.FileTypeSecret() : TD.toFileType(content), this);
    //if (content.getConstructor() == TdApi.InputMessagePhoto.CONSTRUCTOR || content.getConstructor() == TdApi.InputMessageVideo.CONSTRUCTOR) {
      this.inputFileThumbnailFuture = (inputFileThumbnail != null) ? new Tdlib.UploadFutureSimple(tdlib, inputFileThumbnail, isSecret ? new TdApi.FileTypeSecretThumbnail() : new TdApi.FileTypeThumbnail(), this) : null;
    //} else {
    //  this.inputFileThumbnailFuture = null;
    //}
  }

  public void init (Callback callback) {
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
    return pickedFile.imageGalleryFile != null && pickedFile.imageGalleryFile.isWebp();
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

    final TdApi.InputMessageVideo video = (TdApi.InputMessageVideo) content;
    return new TdApi.Video(video.duration, video.width, video.height, pickedFile.getFileName(""), pickedFile.getMimeType("video/mp4"),
      video.addedStickerFileIds != null && video.addedStickerFileIds.length > 0, video.supportsStreaming, null,
      inputFileThumbnailFuture != null && inputFileThumbnailFuture.file != null ?
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

    final TdApi.InputMessageAnimation animation = (TdApi.InputMessageAnimation) content;
    return new TdApi.Animation(animation.duration, animation.width, animation.height, pickedFile.getFileName(""),
      pickedFile.getMimeType("video/mp4"), animation.addedStickerFileIds != null && animation.addedStickerFileIds.length > 0, null,
      inputFileThumbnailFuture != null && inputFileThumbnailFuture.file != null ?
        new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), animation.thumbnail.width, animation.thumbnail.height, inputFileThumbnailFuture.file) : null,
      inputFileFuture.file);
  }

  public boolean isDocument () {
    return content.getConstructor() == TdApi.InputMessageDocument.CONSTRUCTOR;
  }

  public TdApi.Document getDocument () {
    if (content.getConstructor() != TdApi.InputMessageDocument.CONSTRUCTOR) {
      throw new IllegalStateException();
    }

    final TdApi.InputMessageDocument document = (TdApi.InputMessageDocument) content;
    return new TdApi.Document(pickedFile.getFileName(""), pickedFile.getMimeType(""), null,
      inputFileThumbnailFuture != null && inputFileThumbnailFuture.file != null ?
        new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), document.thumbnail.width, document.thumbnail.height, inputFileThumbnailFuture.file) : null,
      inputFileFuture.file);
  }

  public boolean isAudio () {
    return content.getConstructor() == TdApi.InputMessageAudio.CONSTRUCTOR;
  }

  public TdApi.Audio getAudio () {
    if (content.getConstructor() != TdApi.InputMessageAudio.CONSTRUCTOR) {
      throw new IllegalStateException();
    }

    final TdApi.InputMessageAudio audio = (TdApi.InputMessageAudio) content;
    return new TdApi.Audio(audio.duration, audio.title, audio.performer, pickedFile.getFileName(""), pickedFile.getMimeType(""), null,
      inputFileThumbnailFuture != null && inputFileThumbnailFuture.file != null ?
        new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), audio.albumCoverThumbnail.width, audio.albumCoverThumbnail.height, inputFileThumbnailFuture.file) : null,
      new TdApi.Thumbnail[0], inputFileFuture.file);
  }

  public TdApi.MessagePhoto getMessagePhoto () {
    return new TdApi.MessagePhoto(getPhoto(), getCaption(), showCaptionAboveMedia(), hasSpoiler(), false);
  }

  public TdApi.MessageVideo getMessageVideo () {
    return new TdApi.MessageVideo(getVideo(), new TdApi.AlternativeVideo[0], new TdApi.VideoStoryboard[0], getVideoCover(), 0, getCaption(), showCaptionAboveMedia(), hasSpoiler(), false);
  }

  public TdApi.MessageAnimation getMessageAnimation () {
    return new TdApi.MessageAnimation(getAnimation(), getCaption(), showCaptionAboveMedia(), hasSpoiler(), false);
  }

  public TdApi.MessageAudio getMessageAudio () {
    return new TdApi.MessageAudio(getAudio(), getCaption());
  }

  public TdApi.MessageDocument getMessageDocument () {
    return new TdApi.MessageDocument(getDocument(), getCaption());
  }

  public TdApi.FormattedText getCaption () {
    return Td.textOrCaption(content);
  }

  public boolean showCaptionAboveMedia () {
    return Td.showCaptionAboveMedia(content);
  }

  public TdApi.Photo getVideoCover () {
    return null; // TODO
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

  public interface Callback {
    void onMediaUploadStart (MessageEditMediaPending pending);
    void onMediaUploadComplete (MessageEditMediaPending pending);
    // void onMediaEditFinished (MessageEditMediaPending pending);
    void onMediaUploadFailed (MessageEditMediaPending pending);
  }

  private static final int FLAG_UPLOAD_STARTED = 1;
  private static final int FLAG_UPLOAD_COMPLETED = 1 << 1;
  private static final int FLAG_FAILED = 1 << 2;

  private Callback callback;
  private int flags;

  private void fail (TdApi.Error error, boolean isCanceled) {
    if (!BitwiseUtils.hasFlag(flags, FLAG_UPLOAD_COMPLETED | FLAG_FAILED)) {
      inputFileFuture.cancel(false);
      if (inputFileThumbnailFuture != null) {
        inputFileThumbnailFuture.cancel(false);
      }

      if (error != null) {
        UI.showError(error);
      }

      flags = BitwiseUtils.setFlag(flags, FLAG_FAILED, true);
      callback.onMediaUploadFailed(this);
    }
  }

  public TdApi.File getFile () {
    return inputFileFuture.file;
  }

  public void cancel () {
    fail(null, true);
  }

  private void start () {
    if (!BitwiseUtils.hasFlag(flags, FLAG_UPLOAD_COMPLETED | FLAG_FAILED | FLAG_UPLOAD_STARTED)) {
      flags = BitwiseUtils.setFlag(flags, FLAG_UPLOAD_STARTED, true);
      callback.onMediaUploadStart(this);
    }
  }

  private void complete () {
    if (!BitwiseUtils.hasFlag(flags, FLAG_UPLOAD_COMPLETED | FLAG_FAILED)) {
      flags = BitwiseUtils.setFlag(flags, FLAG_UPLOAD_COMPLETED, true);
      callback.onMediaUploadComplete(this);
    }
  }

  @Override
  public void onUploadStart (Tdlib.UploadFutureSimple future, TdApi.File file, @Nullable TdApi.Error error) {
    if (error != null) {
      fail(error, false);
    }
    checkFuturesStarted();
  }

  @Override
  public void onUploadFinish (Tdlib.UploadFutureSimple future) {
    checkFuturesFinished();
  }

  private void checkFuturesStarted () {
    if (!BitwiseUtils.hasFlag(flags, FLAG_UPLOAD_COMPLETED | FLAG_FAILED | FLAG_UPLOAD_STARTED)) {
      final boolean isStarted = inputFileFuture.isStarted() && (inputFileThumbnailFuture == null || inputFileThumbnailFuture.isStarted());
      if (isStarted) {
        start();
      }
    }
  }

  private void checkFuturesFinished () {
    if (!BitwiseUtils.hasFlag(flags, FLAG_UPLOAD_COMPLETED | FLAG_FAILED)) {
      final boolean isFinished = inputFileFuture.isFinished() && (inputFileThumbnailFuture == null || inputFileThumbnailFuture.isFinished());
      if (isFinished) {
        final boolean isSuccess = inputFileFuture.isCompleted();
        if (isSuccess) {
          TD.setInputFile(content, new TdApi.InputFileId(inputFileFuture.file.id));
          if (inputFileThumbnailFuture != null) {
            if (inputFileThumbnailFuture.isCompleted()) {
              TD.setInputFileThumbnail(content, new TdApi.InputFileId(inputFileThumbnailFuture.file.id));
            } else {
              TD.setInputFileThumbnail(content, null);
            }
          }
          complete();
        } else {
          fail(null, false);
        }
      }
    }
  }
}
