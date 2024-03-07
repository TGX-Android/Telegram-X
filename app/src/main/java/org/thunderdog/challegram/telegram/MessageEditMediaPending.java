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
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.chat.MediaPreviewSimple;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.tool.Screen;

import java.io.File;

import me.vkryl.core.StringUtils;
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
  private final @NonNull LocalPickedFile pickedFile;

  MessageEditMediaPending (Tdlib tdlib, long chatId, long messageId, TdApi.InputMessageContent content, @NonNull LocalPickedFile pickedFile) {
    this.chatId = chatId;
    this.messageId = messageId;
    this.content = content;
    this.inputFile = TD.getInputFile(content);
    this.inputFileThumbnail = TD.getInputFileThumbnail(content);
    this.pickedFile = pickedFile;

    final boolean isSecret = ChatId.isSecret(chatId);

    this.inputFileFuture = new TdlibEditMediaManager.UploadFuture(tdlib, FUTURE_ID_MAIN, inputFile, isSecret ? new TdApi.FileTypeSecret() : TD.toFileType(content), this);
    if (content.getConstructor() == TdApi.InputMessagePhoto.CONSTRUCTOR || content.getConstructor() == TdApi.InputMessageVideo.CONSTRUCTOR) {
      this.inputFileThumbnailFuture = (inputFileThumbnail != null) ? new TdlibEditMediaManager.UploadFuture(tdlib, FUTURE_ID_THUMB, inputFileThumbnail, isSecret ? new TdApi.FileTypeSecretThumbnail() : new TdApi.FileTypeThumbnail(), this) : null;
    } else {
      this.inputFileThumbnailFuture = null;
    }
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

    final TdApi.InputMessageAnimation animation = (TdApi.InputMessageAnimation) content;
    return new TdApi.Animation(animation.duration, animation.width, animation.height, pickedFile.getFileName(""),
      pickedFile.getMimeType("video/mp4"), animation.addedStickerFileIds != null && animation.addedStickerFileIds.length > 0, null,
      inputFileThumbnailFuture != null ?
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

    return new TdApi.Document(pickedFile.getFileName(""), pickedFile.getMimeType(""), null, null, inputFileFuture.file);
  }

  public boolean isAudio () {
    return content.getConstructor() == TdApi.InputMessageAudio.CONSTRUCTOR;
  }

  public TdApi.Audio getAudio () {
    if (content.getConstructor() != TdApi.InputMessageAudio.CONSTRUCTOR) {
      throw new IllegalStateException();
    }

    final TdApi.InputMessageAudio audio = (TdApi.InputMessageAudio) content;
    return new TdApi.Audio(audio.duration, audio.title, audio.performer, pickedFile.getFileName(""), pickedFile.getMimeType(""), null, null, new TdApi.Thumbnail[0], inputFileFuture.file);
  }

  public TdApi.MessagePhoto getMessagePhoto () {
    return new TdApi.MessagePhoto(getPhoto(), getCaption(), hasSpoiler(), false);
  }

  public TdApi.MessageVideo getMessageVideo () {
    return new TdApi.MessageVideo(getVideo(), getCaption(), hasSpoiler(), false);
  }

  public TdApi.MessageAnimation getMessageAnimation () {
    return new TdApi.MessageAnimation(getAnimation(), getCaption(), hasSpoiler(), false);
  }

  public TdApi.MessageAudio getMessageAudio () {
    return new TdApi.MessageAudio(getAudio(), getCaption());
  }

  public TdApi.MessageDocument getMessageDocument () {
    return new TdApi.MessageDocument(getDocument(), getCaption());
  }

  public TdApi.FormattedText getCaption () {
    return TD.textOrCaption(content);
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

  public static class LocalPickedFile {
    public final ImageGalleryFile imageGalleryFile;
    public final InlineResult<?> inlineResult;

    public LocalPickedFile (ImageGalleryFile imageGalleryFile, InlineResult<?> inlineResult) {
      this.imageGalleryFile = imageGalleryFile;
      this.inlineResult = inlineResult;
    }

    public String getFileName (String defaultName) {
      if (inlineResult instanceof InlineResultCommon) {
        return ((InlineResultCommon) inlineResult).getTrackTitle();
      }
      return defaultName;
    }

    public String getMimeType (String defaultMimeType) {
      String mimeType = null;

      if (inlineResult instanceof InlineResultCommon) {
        mimeType = ((InlineResultCommon) inlineResult).getMimeType();
      } else if (imageGalleryFile != null && imageGalleryFile.isVideo()) {
        mimeType = imageGalleryFile.getVideoMimeType();
      }

      return !StringUtils.isEmpty(mimeType) ? mimeType : defaultMimeType;
    }

    public boolean isMusic () {
      return inlineResult != null && inlineResult.getType() == InlineResult.TYPE_AUDIO;
    }

    @Nullable
    public MediaPreview buildMediaPreview (Tdlib tdlib, int size, int cornerRadius) {
      if (imageGalleryFile != null) {
        return new MediaPreviewSimple(size, 0, imageGalleryFile);
      } else if (inlineResult instanceof InlineResultCommon) {
        final TdApi.File file = ((InlineResultCommon) inlineResult).getTrackFile();
        final String path = TD.getFilePath(file);
        final String mimeType = getMimeType(null);

        if (!StringUtils.isEmpty(path) && !StringUtils.isEmpty(mimeType)) {
          return MediaPreview.valueOf(tdlib, new File(path), mimeType, size, cornerRadius);
        }
      }

      return null;
    }

    @Nullable
    public ContentPreview buildContentPreview () {
      if (imageGalleryFile != null) {
        if (imageGalleryFile.isVideo()) {
          return new ContentPreview(ContentPreview.EMOJI_VIDEO, R.string.ChatContentVideo);
        } else {
          return new ContentPreview(ContentPreview.EMOJI_PHOTO, R.string.ChatContentPhoto);
        }
      } else if (inlineResult != null) {
        final String fileName = getFileName(null);
        final boolean isMusic = isMusic();
        if (StringUtils.isEmpty(fileName)) {
          return new ContentPreview(isMusic ? ContentPreview.EMOJI_AUDIO : ContentPreview.EMOJI_FILE, R.string.ChatContentFile);
        } else {
          return new ContentPreview(isMusic ? ContentPreview.EMOJI_AUDIO : ContentPreview.EMOJI_FILE, 0, fileName, false);
        }
      }
      return null;
    }
  }
}
