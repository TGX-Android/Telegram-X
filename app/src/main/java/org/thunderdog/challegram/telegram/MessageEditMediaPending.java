package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;

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
  private final @Nullable ImageFile preview;

  MessageEditMediaPending (Tdlib tdlib, long chatId, long messageId, TdApi.InputMessageContent content, @Nullable ImageFile preview) {
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

  public boolean isVideo () {
    return content.getConstructor() == TdApi.InputMessageVideo.CONSTRUCTOR;
  }

  public int width () {
    switch (content.getConstructor()) {
      case TdApi.InputMessagePhoto.CONSTRUCTOR:
        return ((TdApi.InputMessagePhoto) content).width;
      case TdApi.InputMessageVideo.CONSTRUCTOR:
        return ((TdApi.InputMessageVideo) content).width;
      case TdApi.InputMessageAnimation.CONSTRUCTOR:
        return ((TdApi.InputMessageAnimation) content).width;
      default:
        return 0;
    }
  }

  public int height () {
    switch (content.getConstructor()) {
      case TdApi.InputMessagePhoto.CONSTRUCTOR:
        return ((TdApi.InputMessagePhoto) content).height;
      case TdApi.InputMessageVideo.CONSTRUCTOR:
        return ((TdApi.InputMessageVideo) content).height;
      case TdApi.InputMessageAnimation.CONSTRUCTOR:
        return ((TdApi.InputMessageAnimation) content).height;
      default:
        return 0;
    }
  }

  public void cancel () {
    inputFileFuture.cancel();
    if (inputFileThumbnailFuture != null) {
      inputFileThumbnailFuture.cancel();
    }
  }

  private TdApi.File file;

  @Override
  public void onFileUpdate (int id, long fileId, TdApi.File file) {
    if (id == FUTURE_ID_MAIN) {
      if (this.file == null) {
        callback.onMediaPreliminaryUploadStart(this, file);
      }
      this.file = file;
    }
    // ?
  }

  @Override
  public void onComplete (int id, long fileId, TdApi.File file) {
    checkStatus();
  }

  @Override
  public void onFail (int id, boolean isCanceled) {
    cancel();
    callback.onMediaPreliminaryUploadFailed(this, isCanceled);
  }

  public boolean isCompleted () {
    return inputFileFuture.isCompleted() && (inputFileThumbnailFuture == null || inputFileThumbnailFuture.isCompleted());
  }

  public TdApi.File getFile () {
    return inputFileFuture.file;
  }

  @Nullable
  public ImageFile getPreviewFile () {
    return preview;
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
