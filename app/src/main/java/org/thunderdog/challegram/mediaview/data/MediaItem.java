/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 09/12/2016
 */
package org.thunderdog.challegram.mediaview.data;

import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.sharedmedia.MediaSmallView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.data.TdApiExt;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageFilteredFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.loader.ImageVideoThumbFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifFileLocal;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.MessageSourceProvider;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class MediaItem implements MessageSourceProvider, InvalidateContentProvider {
  public static final int TYPE_PHOTO = 0;
  public static final int TYPE_VIDEO = 1;
  public static final int TYPE_GIF = 2;
  public static final int TYPE_GALLERY_PHOTO = 3;
  public static final int TYPE_GALLERY_VIDEO = 4;
  public static final int TYPE_GALLERY_GIF = 5;
  public static final int TYPE_USER_PROFILE = 6;
  public static final int TYPE_CHAT_PROFILE = 7;
  public static final int TYPE_VIDEO_MESSAGE = 8;

  private ImageFile miniThumbnail;

  // Remote stuff
  private ImageFile previewImageFile, blurredPreviewImageFile;
  private TdApi.File targetFile;
  private FileProgressComponent fileProgress;
  private boolean needCreateGalleryFileProgress;

  // Source data
  private long sourceChatId, sourceMessageId;
  private TdApi.MessageSender sourceSender;
  private int sourceDate;
  private TdApi.Photo sourcePhoto;
  private TdApi.Video sourceVideo;
  private TdApi.Document sourceDocument;
  private TdApi.VideoNote sourceVideoNote;
  private TdApi.Animation sourceAnimation;
  private ImageGalleryFile sourceGalleryFile;

  // Common stuff
  private final BaseActivity context;
  private final Tdlib tdlib;
  private final int type;
  private int width, height;
  private TdApi.FormattedText caption;
  private ImageFile targetImage;
  private GifFile targetGif;

  private boolean hasSpoiler;

  public static MediaItem copyOf (MediaItem item) {
    return copyOf(item, true);
  }

  public static MediaItem copyOf (MediaItem item, boolean allowIcon) {
    if (item == null) {
      return null;
    }
    switch (item.type) {
      case TYPE_PHOTO: {
        MediaItem copy = new MediaItem(item.context, item.tdlib, item.sourceChatId, item.sourceMessageId, item.sourcePhoto);
        copy.sourceSender = item.sourceSender;
        copy.sourceDate = item.sourceDate;
        copy.caption = item.caption;
        copy.msg = item.msg;
        copy.setHasSpoiler(item.hasSpoiler);
        return copy;
      }
      case TYPE_GIF: {
        MediaItem copy = new MediaItem(item.context, item.tdlib, item.sourceChatId, item.sourceMessageId, item.sourceSender, item.sourceDate, item.sourceAnimation, item.caption).setMessage(item.msg);
        copy.setHasSpoiler(item.hasSpoiler);
        return copy;
      }
      case TYPE_VIDEO: {
        MediaItem copy = new MediaItem(item.context, item.tdlib, item.sourceChatId, item.sourceMessageId, item.sourceSender, item.sourceDate, item.sourceVideo, item.caption, allowIcon).setMessage(item.msg);
        copy.setHasSpoiler(item.hasSpoiler);
        return copy;
      }
      case TYPE_USER_PROFILE: {
        return new MediaItem(item.context, item.tdlib, ((TdApi.MessageSenderUser) item.sourceSender).userId, item.profilePhoto);
      }
      case TYPE_CHAT_PROFILE: {
        return new MediaItem(item.context, item.tdlib, item.sourceChatId, item.sourceMessageId, item.chatPhoto, item.isFullPhoto);
      }
    }
    return null;
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, TdApi.Photo photo, TdApi.FormattedText caption) {
    this(context, tdlib, 0, 0, photo, false, false);
    this.caption = caption;
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.Photo photo) {
    this(context, tdlib, sourceChatId, sourceMessageId, photo, false, false);
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.Document document, @Nullable BitmapFactory.Options options, boolean isRotated, U.MediaMetadata mediaMetadata) {
    this.context = context;
    this.tdlib = tdlib;
    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;

    int documentType = getMediaDocumentType(document);
    switch (documentType) {
      case DocumentType.IMAGE: {
        TdApi.Photo photo = TD.convertToPhoto(document, options, isRotated);
        this.type = TYPE_PHOTO;
        setPhoto(photo, "image/webp".equals(document.mimeType), true);
        break;
      }
      case DocumentType.GIF: {
        TdApi.Animation animation = TD.convertToAnimation(document, options, isRotated, mediaMetadata);
        this.type = TYPE_GIF;
        setAnimation(animation, true);
        break;
      }
      case DocumentType.VIDEO: {
        TdApi.Video video = TD.convertToVideo(document, options, isRotated, mediaMetadata);
        this.type = TYPE_VIDEO;
        setVideo(video, true, true);
        break;
      }
      case DocumentType.UNKNOWN:
      default: {
        throw new UnsupportedOperationException(document.mimeType);
      }
    }
    this.sourceDocument = document;
    this.targetFile = document.document;
  }

  @IntDef({
    DocumentType.UNKNOWN, DocumentType.IMAGE, DocumentType.VIDEO, DocumentType.GIF
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface DocumentType {
    int UNKNOWN = 0, IMAGE = 1, VIDEO = 2, GIF = 3;
  }

  @DocumentType
  public static int getMediaDocumentType (@Nullable TdApi.Document document) {
    if (document != null) {
      @DocumentType int documentType = getMediaDocumentType(document.mimeType);
      if (documentType != DocumentType.UNKNOWN) {
        return documentType;
      }
      String extension = U.getExtension(document.fileName);
      if (!StringUtils.isEmpty(extension)) {
        extension = extension.toLowerCase();
      }
      String alternateMimeType = TGMimeType.mimeTypeForExtension(extension);
      if (!StringUtils.isEmpty(alternateMimeType) && !alternateMimeType.equals(document.mimeType)) {
        documentType = getMediaDocumentType(alternateMimeType);
        if (documentType != DocumentType.UNKNOWN) {
          return documentType;
        }
      }
    }
    return DocumentType.UNKNOWN;
  }

  @DocumentType
  public static int getMediaDocumentType (@NonNull String mimeType) {
    if (isImageDocument(mimeType)) {
      if ("image/gif".equals(mimeType)) {
        return DocumentType.GIF;
      } else {
        return DocumentType.IMAGE;
      }
    }
    if (isVideoDocument(mimeType)) {
      return DocumentType.VIDEO;
    }
    return DocumentType.UNKNOWN;
  }

  public static boolean isMediaDocument (@Nullable TdApi.Document document) {
    return getMediaDocumentType(document) != DocumentType.UNKNOWN;
  }

  public static boolean isVideoDocument (@NonNull String mimeType) {
    return TGMimeType.isVideoMimeType(mimeType) || mimeType.startsWith("video/");
  }

  public static boolean isImageDocument (@NonNull String mimeType) {
    if ("image/svg+xml".equals(mimeType)) {
      // Unsupported image types
      return false;
    }
    return TGMimeType.isImageMimeType(mimeType) || mimeType.startsWith("image/");
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.Photo photo, boolean isWebp, boolean isDocument) {
    this.type = TYPE_PHOTO;
    this.context = context;
    this.tdlib = tdlib;

    this.sourcePhoto = photo;
    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;

    setPhoto(photo, isWebp, isDocument);
  }

  private void setPhoto (TdApi.Photo photo, boolean isWebp, boolean isDocument) {
    this.sourcePhoto = photo;
    TdApi.PhotoSize previewSize, targetSize;
    if (isDocument) {
      if (photo.sizes.length == 2) {
        previewSize = photo.sizes[0];
        targetSize = photo.sizes[1];
      } else {
        previewSize = Td.findSmallest(photo);
        targetSize = Td.findBiggest(photo);
      }
      if (targetSize == previewSize) {
        previewSize = null;
      }
    } else {
      previewSize = MediaWrapper.buildPreviewSize(photo.sizes);
      targetSize = MediaWrapper.buildTargetFile(photo.sizes, previewSize);
      if (previewSize == null) {
        TdApi.PhotoSize smallestSize = Td.findSmallest(photo.sizes);
        if (smallestSize != null && targetSize != null && smallestSize != targetSize && smallestSize.width <= targetSize.width && smallestSize.height <= targetSize.height) {
          previewSize = smallestSize;
        }
      }
    }

    if (isDocument && targetSize == null && previewSize != null) {
      targetSize = previewSize;
      previewSize = null;
    }

    if (targetSize != null) {
      width = targetSize.width;
      height = targetSize.height;
    } else if (previewSize != null) {
      width = previewSize.width;
      height = previewSize.height;
    } else {
      width = 0;
      height = 0;
    }

    if (width == 0 || height == 0) {
      width = height = Screen.dp(100f);
    }

    setMiniThumbnail(photo.minithumbnail);
    if (previewSize != null) {
      this.previewImageFile = new ImageFile(tdlib, previewSize.photo);
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
      this.previewImageFile.setNeedCancellation(true);
      if (isWebp) {
        this.previewImageFile.setWebp();
      }
    } else {
      this.previewImageFile = null;
    }
    this.targetFile = targetSize != null ? targetSize.photo : null;
    if (targetSize != null) {
      this.targetImage = new ImageFile(tdlib, targetSize.photo);
      this.targetImage.setScaleType(ImageFile.FIT_CENTER);
      this.targetImage.setNoBlur();
      this.targetImage.setNeedCancellation(true);
      if (isWebp) {
        this.targetImage.setWebp();
      }
      MediaWrapper.applyMaxSize(targetImage, targetSize);
    } else {
      this.targetImage = null;
    }

    if (targetSize != null) {
      this.fileProgress = new FileProgressComponent(context, tdlib, isDocument ? TdlibFilesManager.DOWNLOAD_FLAG_FILE : TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, true, sourceChatId, sourceMessageId);
      this.fileProgress.setUseStupidInvalidate();
      this.fileProgress.setFile(targetSize.photo);
    }
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.MessagePhoto photo) {
    this(context, tdlib, sourceChatId, sourceMessageId, photo.photo);
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;
    this.caption = photo.caption;
    setHasSpoiler(photo.hasSpoiler);
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.MessageDocument document) {
    this(context, tdlib, sourceChatId, sourceMessageId, document.document, null, false, null);
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;
    this.caption = document.caption;
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.MessageAnimation animation) {
    this(context, tdlib, sourceChatId, sourceMessageId, sourceSender, sourceDate, animation.animation, animation.caption);
    setHasSpoiler(animation.hasSpoiler);
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, TdApi.Animation animation, TdApi.FormattedText caption) {
    this(context, tdlib, 0, 0, null, 0, animation, caption);
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.Animation animation, TdApi.FormattedText caption) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_GIF;
    this.caption = caption;
    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;
    setAnimation(animation, false);
  }

  private void setThumbnail (TdApi.Thumbnail thumbnail) {
    this.previewImageFile = TD.toImageFile(tdlib, thumbnail); // TODO MPEG4 thumbnail support
    if (previewImageFile != null) {
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
      this.previewImageFile.setNeedCancellation(true);
    }
    this.blurredPreviewImageFile = TD.toImageFile(tdlib, thumbnail);
    if (this.blurredPreviewImageFile != null) {
      this.blurredPreviewImageFile.setScaleType(ImageFile.FIT_CENTER);
      this.blurredPreviewImageFile.setNeedCancellation(true);
      this.blurredPreviewImageFile.setIsPrivate();
    }
  }

  private void setAnimation (TdApi.Animation animation, boolean isDocument) {
    this.sourceAnimation = animation;
    this.targetFile = animation.animation;

    setMiniThumbnail(animation.minithumbnail);
    setThumbnail(animation.thumbnail);

    this.targetGif = new GifFile(tdlib, animation);
    this.targetGif.setScaleType(GifFile.FIT_CENTER);
    if (sourceChatId != 0 && sourceMessageId != 0 && (!Settings.instance().needAutoplayGIFs() && !isDocument)) {
      this.targetGif.setIsStill(true);
    }

    this.width = animation.width;
    this.height = animation.height;

    if (width == 0 || height == 0) {
      width = height = Screen.dp(100f);
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, isDocument ? TdlibFilesManager.DOWNLOAD_FLAG_FILE : TdlibFilesManager.DOWNLOAD_FLAG_GIF, true, sourceChatId, sourceMessageId);
    this.fileProgress.setUseStupidInvalidate();
    this.fileProgress.setFile(targetFile);
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.MessageVideoNote videoNote) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_VIDEO_MESSAGE;
    this.sourceVideoNote = videoNote.videoNote;
    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;

    setMiniThumbnail(videoNote.videoNote.minithumbnail);
    if (videoNote.videoNote.thumbnail != null) {
      this.previewImageFile = TD.toImageFile(tdlib, videoNote.videoNote.thumbnail);
      if (this.previewImageFile != null) {
        this.previewImageFile.setNeedCancellation(true);
        this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
      }

      this.width = videoNote.videoNote.thumbnail.width;
      this.height = videoNote.videoNote.thumbnail.height;
    }

    this.targetFile = videoNote.videoNote.video;

    this.targetGif = new GifFile(tdlib, videoNote.videoNote.video, GifFile.TYPE_MPEG4);
    this.targetGif.setScaleType(GifFile.FIT_CENTER);
    if (sourceChatId != 0 && sourceMessageId != 0 && !Settings.instance().needAutoplayGIFs()) {
      this.targetGif.setIsStill(true);
    }

    if (width == 0 || height == 0) {
      width = height = Screen.dp(100f);
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE, true, sourceChatId, sourceMessageId);
    this.fileProgress.setUseStupidInvalidate();
    this.fileProgress.setFile(targetFile);
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, TdApi.Video video, TdApi.FormattedText caption, boolean allowIcon) {
    this(context, tdlib, 0, 0, null, 0, video, caption, allowIcon);
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.Video video, TdApi.FormattedText caption, boolean allowIcon) {
    this.type = TYPE_VIDEO;
    this.context = context;
    this.tdlib = tdlib;
    this.caption = caption;

    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;

    setVideo(video, allowIcon, false);
  }

  private void setVideo (TdApi.Video video, boolean allowIcon, boolean isDocument) {
    this.sourceVideo = video;

    setMiniThumbnail(video.minithumbnail);
    setThumbnail(video.thumbnail);
    this.targetFile = video.video;

    // TODO: remove this targetImage at all, when video.thumbnail is available?
    // if (previewImageFile == null) {
      this.targetImage = MediaWrapper.createThumbFile(tdlib, video.video);
      this.targetImage.setScaleType(ImageFile.FIT_CENTER);
    // }

    this.width = video.width;
    this.height = video.height;

    if (width == 0 || height == 0) {
      width = height = Screen.dp(100f);
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, isDocument ? TdlibFilesManager.DOWNLOAD_FLAG_FILE : TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, true, sourceChatId, sourceMessageId);
    this.fileProgress.setUseStupidInvalidate();

    if (allowIcon) {
      this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
    }

    if (Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) {
      this.fileProgress.setIgnoreLoaderClicks(true);
      this.fileProgress.setVideoStreamingProgressHidden(true);
      this.fileProgress.setPausedIconRes(FileProgressComponent.PLAY_ICON);
    }

    this.fileProgress.setFile(targetFile);
  }

  public void setScaleType (int scaleType) {
    if (miniThumbnail != null) {
      miniThumbnail.setScaleType(scaleType);
    }
    if (previewImageFile != null) {
      previewImageFile.setScaleType(scaleType);
    }
    if (blurredPreviewImageFile != null) {
      blurredPreviewImageFile.setScaleType(scaleType);
    }
    if (targetImage != null) {
      targetImage.setScaleType(scaleType);
    }
    if (targetGif != null) {
      targetGif.setScaleType(scaleType);
    }
  }

  public void setNeedSquare (boolean needSquare) {
    if (miniThumbnail != null) {
      miniThumbnail.setDecodeSquare(needSquare);
    }
    if (previewImageFile != null) {
      previewImageFile.setDecodeSquare(needSquare);
    }
    if (targetImage != null) {
      targetImage.setDecodeSquare(needSquare);
    }
  }

  public void setSize (int size) {
    if (targetImage != null) {
      targetImage.setSize(size);
    }
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.MessageVideo video, boolean allowIcon) {
    this(context, tdlib, sourceChatId, sourceMessageId, sourceSender, sourceDate, video.video, video.caption, allowIcon);
    setHasSpoiler(video.hasSpoiler);
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long userId, TdApi.ProfilePhoto profilePhoto) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_USER_PROFILE;
    this.sourceSender = new TdApi.MessageSenderUser(userId);
    this.profilePhoto = profilePhoto;

    this.width = this.height = Screen.dp(640f);

    setMiniThumbnail(profilePhoto.minithumbnail);

    if (profilePhoto.small != null) {
      this.previewImageFile = new ImageFile(tdlib, profilePhoto.small);
      this.previewImageFile.setNeedCancellation(true);
      this.previewImageFile.setSize(ChatView.getDefaultAvatarCacheSize());
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
    }

    if (profilePhoto.big != null) {
      this.targetFile = profilePhoto.big;
      this.targetImage = new ImageFile(tdlib, profilePhoto.big);
      this.targetImage.setNeedCancellation(true);
      this.targetImage.setScaleType(ImageFile.FIT_CENTER);

      this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, true, 0, 0);
      this.fileProgress.setUseStupidInvalidate();
      this.fileProgress.setFile(profilePhoto.big);
    }
  }

  private TdApi.ProfilePhoto profilePhoto;
  private TdApi.ChatPhoto chatPhoto;
  private boolean isFullPhoto;

  public MediaItem setSourceDate (int sourceDate) {
    this.sourceDate = sourceDate;
    return this;
  }

  public MediaItem setChatPhoto (TdApi.ChatPhoto chatPhoto) {
    this.chatPhoto = chatPhoto;
    this.isFullPhoto = true;
    setSourceDate(chatPhoto.addedDate);
    return this;
  }

  public MediaItem setSourceSender (TdApi.MessageSender sender) {
    this.sourceSender = sender;
    return this;
  }

  private TGMessage sourceMessage;

  public MediaItem setSourceMessage (TGMessage message) {
    this.sourceMessage = message;
    return this;
  }

  @Nullable
  public TGMessage getSourceMessage () {
    return sourceMessage;
  }

  public long getPhotoId () {
    switch (type) {
      case TYPE_CHAT_PROFILE:
        return chatPhoto.id;
      case TYPE_USER_PROFILE:
        return profilePhoto.id;
    }
    throw new UnsupportedOperationException();
  }
  public int getBigFileId () {
    return targetFile != null ? targetFile.id : 0;
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long chatId, TdApi.ChatPhotoInfo chatPhoto) {
    this(context, tdlib, chatId, 0, new TdApi.ChatPhoto(
      0,
      0,
      chatPhoto.minithumbnail,
      new TdApi.PhotoSize[] {
        new TdApi.PhotoSize("s", chatPhoto.small, 160, 160, null),
        new TdApi.PhotoSize("m", chatPhoto.big, 640, 640, null)
      },
      null,
      null,
      null
    ), false);
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long chatId, long messageId, TdApi.ChatPhoto photo) {
    this(context, tdlib, chatId, messageId, photo, true);
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long chatId, long messageId, TdApi.ChatPhoto photo, boolean isFullPhoto) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_CHAT_PROFILE;
    this.isFullPhoto = isFullPhoto;
    this.sourceChatId = chatId;
    this.sourceMessageId = messageId;
    this.sourceSender = ChatId.isUserChat(chatId) ? new TdApi.MessageSenderUser(tdlib.chatUserId(chatId)) : new TdApi.MessageSenderChat(chatId);
    this.sourceDate = photo.addedDate;
    this.chatPhoto = photo;

    setMiniThumbnail(photo.minithumbnail);

    TdApi.PhotoSize small = Td.findSmallest(photo.sizes);
    TdApi.PhotoSize big = Td.findBiggest(photo.sizes);

    if (small != null) {
      this.previewImageFile = new ImageFile(tdlib, small.photo);
      this.previewImageFile.setNeedCancellation(true);
      this.previewImageFile.setSize(ChatView.getDefaultAvatarCacheSize());
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
    }

    if (big != null) {
      this.targetImage = new ImageFile(tdlib, big.photo);
      this.targetImage.setNeedCancellation(true);
      this.targetImage.setScaleType(ImageFile.FIT_CENTER);

      this.targetFile = photo.animation != null ? photo.animation.file : photo.smallAnimation != null ? photo.smallAnimation.file : big.photo;
      this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, true, sourceChatId, messageId);
      this.fileProgress.setUseStupidInvalidate();
      this.fileProgress.setFile(targetFile);
    }

    this.width = this.height = 640;

    setSourceSender(tdlib.sender(chatId));
  }

  public TdApi.ChatPhoto getChatPhoto () {
    return chatPhoto;
  }

  public static int maxDisplaySize () {
    return PhotoGenerationInfo.SIZE_LIMIT; // Math.min(1280, Screen.smallestSide());
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, ImageGalleryFile imageFile) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = imageFile.isVideo() ? TYPE_GALLERY_VIDEO /*: Strings.compare(Utils.getExtension(imageFile.getFilePath().toLowerCase()), "gif") ? TYPE_GALLERY_GIF*/ : TYPE_GALLERY_PHOTO;
    this.width = imageFile.getWidth();
    this.height = imageFile.getHeight();

    this.sourceGalleryFile = imageFile;
    if (!imageFile.isFromCamera()) {
      this.previewImageFile = new ImageGalleryFile(imageFile);
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
      ((ImageGalleryFile) this.previewImageFile).setPostRotate(imageFile.getPostRotate());
    }

    int maxSize = maxDisplaySize();

    if (imageFile.isVideo()) {
      if (this.targetImage == null) {
        this.targetImage = new ImageVideoThumbFile(tdlib, imageFile.getFile());
        this.targetImage.setScaleType(ImageFile.FIT_CENTER);
        this.targetImage.setRotation(imageFile.getPostRotate());
        ((ImageVideoThumbFile) this.targetImage).setMaxSize(maxSize);
        ((ImageVideoThumbFile) this.targetImage).setFrameTimeUs(imageFile.getStartTimeUs() > 0 ? imageFile.getStartTimeUs() : 0);
      }
      this.needCreateGalleryFileProgress = true;
    } else if (!imageFile.isFromCamera()) {
      if (type == TYPE_GALLERY_GIF) {
        this.targetGif = new GifFileLocal(tdlib, imageFile.getFilePath());
        this.targetGif.setScaleType(GifFile.FIT_CENTER);
      } else {
        this.targetImage = new ImageGalleryFile(imageFile);
        this.targetImage.setScaleType(ImageFile.FIT_CENTER);
        ((ImageGalleryFile) this.targetImage).setNeedThumb(false);
      }
    } else {
      this.targetImage = new ImageGalleryFile(imageFile);
      this.targetImage.setScaleType(ImageFile.FIT_CENTER);
      this.targetImage.setRotation(imageFile.getRotation());
    }

    if (targetImage != null) {
      this.targetImage.setSize(maxSize);
      this.targetImage.setNoCache();
      this.targetImage.setForceArgb8888();
      this.targetImage.setNeedHiRes();
    }
    setFiltersState(sourceGalleryFile.getFiltersState());
    setCropState(sourceGalleryFile.getCropState());
    setPaintState(sourceGalleryFile.getPaintState(), true);
  }

  public boolean checkTrim () {
    if (targetImage instanceof ImageVideoThumbFile && sourceGalleryFile.getStartTimeUs() != ((ImageVideoThumbFile) targetImage).getFrameTimeUs()) {
      this.targetImage = new ImageVideoThumbFile(tdlib, sourceGalleryFile.getFile());
      this.targetImage.setScaleType(ImageFile.FIT_CENTER);
      this.targetImage.setRotation(sourceGalleryFile.getPostRotate());
      ((ImageVideoThumbFile) this.targetImage).setMaxSize(maxDisplaySize());
      ((ImageVideoThumbFile) this.targetImage).setFrameTimeUs(sourceGalleryFile.getStartTimeUs() > 0 ? sourceGalleryFile.getStartTimeUs() : 0);
      if (!sourceGalleryFile.isFromCamera()) {
        this.previewImageFile = new ImageGalleryFile(sourceGalleryFile);
        this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
        ((ImageGalleryFile) this.previewImageFile).setPostRotate(sourceGalleryFile.getPostRotate());
      }
      return true;
    }
    return false;
  }

  // thumbs

  private ImageFile thumbImageMiniThumb, thumbImageFile, thumbImageFileNoScale;

  public ImageFile getThumbImageMiniThumb () {
    if (miniThumbnail == null)
      return null;
    if (thumbImageMiniThumb == null) {
      thumbImageMiniThumb = new ImageFileLocal(miniThumbnail.getBytes(), true);
      thumbImageMiniThumb.setScaleType(ImageFile.CENTER_CROP);
    }
    return thumbImageMiniThumb;
  }

  public @Nullable ImageFile getThumbImageFile (int thumbHeight, boolean noScale) {
    /*if (file == null && sourcePhoto != null) {
      TdApi.PhotoSize smallest = TD.findSmallest(sourcePhoto);
      file = smallest != null ? smallest.photo : null;
    }*/
    TdApi.File file = previewImageFile != null ? previewImageFile.getFile() : null;
    if (file == null && (thumbImageFile == null || thumbImageFile instanceof ImageFileLocal)) {
      file = getFileProgress() != null && fileProgress.isLoaded() ? fileProgress.getFile() : file;
      if (fileProgress != null && !fileProgress.isLoaded()) {
         fileProgress.downloadAutomatically(sourceChatId);
      }
    }
    if (thumbImageFile == null || (file != null && thumbImageFile instanceof ImageFileLocal)) {
      if (file == null) {
        if (miniThumbnail != null) {
          thumbImageFile = new ImageFileLocal(miniThumbnail.getBytes(), true);
          thumbImageFile.setScaleType(ImageFile.CENTER_CROP);

          thumbImageFileNoScale = new ImageFileLocal(miniThumbnail.getBytes(), false);
          thumbImageFileNoScale.setScaleType(ImageFile.FIT_CENTER);

          return noScale ? thumbImageFileNoScale : thumbImageFile;
        }
        TdApi.PhotoSize smallestSize = sourcePhoto != null ? Td.findSmallest(sourcePhoto) : null;
        file = smallestSize != null ? smallestSize.photo : targetImage != null ? targetImage.getFile() : null;
      }
      if (file == null)
        return null;
      thumbImageFile = new ImageFile(tdlib, file);
      thumbImageFile.setNeedCancellation(true);
      thumbImageFile.setScaleType(ImageFile.CENTER_CROP);
      thumbImageFile.setNoBlur();
      int size = previewImageFile != null && previewImageFile.getSize() == ChatView.getDefaultAvatarCacheSize() ? ChatView.getDefaultAvatarCacheSize() : thumbHeight;
      thumbImageFile.setSize(size);

      thumbImageFileNoScale = new ImageFile(tdlib, file);
      thumbImageFileNoScale.setNeedCancellation(true);
      thumbImageFileNoScale.setScaleType(ImageFile.FIT_CENTER);
      thumbImageFileNoScale.setNoBlur();
      thumbImageFileNoScale.setSize(size);
    }
    return noScale ? thumbImageFileNoScale : thumbImageFile;
  }

  private MultipleViewProvider thumbViewHolder;

  public interface ThumbExpandChangeListener {
    void onThumbExpandFactorChanged (MediaItem item);
  }

  public void attachToThumbView (View view) {
    if (thumbViewHolder == null) {
      thumbViewHolder = new MultipleViewProvider();
    }
    thumbViewHolder.attachToView(view);
  }

  public void detachFromThumbView (View view) {
    if (thumbViewHolder != null) {
      thumbViewHolder.detachFromView(view);
    }
  }

  public void notifyThumbExpandFactorChanged () {
    if (thumbViewHolder == null) {
      return;
    }
    ReferenceList<View> views = thumbViewHolder.getViewsList();
    for (View view : views) {
      if (view instanceof ThumbExpandChangeListener) {
        ((ThumbExpandChangeListener) view).onThumbExpandFactorChanged(this);
      }
    }
  }

  // common

  private TdApi.Message msg;

  private MediaItem setMessage (TdApi.Message msg) {
    this.msg = msg;
    return this;
  }

  @Override
  public TdApi.Message getMessage () {
    return msg;
  }

  public static MediaItem valueOf (BaseActivity context, Tdlib tdlib, TdApi.Photo photo, TdApi.FormattedText caption) {
    return new MediaItem(context, tdlib, photo, caption);
  }

  public static MediaItem valueOf (BaseActivity context, Tdlib tdlib, TdApi.Video video, TdApi.FormattedText caption) {
    return new MediaItem(context, tdlib, video, caption, true);
  }

  public static MediaItem valueOf (BaseActivity context, Tdlib tdlib, TdApi.Animation animation, TdApi.FormattedText caption) {
    return new MediaItem(context, tdlib, animation, caption);
  }

  public static MediaItem valueOf (BaseActivity context, Tdlib tdlib, TdApi.Document document, TdApi.FormattedText caption) {
    @DocumentType int documentType = getMediaDocumentType(document);
    if (documentType != DocumentType.UNKNOWN) {
      BitmapFactory.Options options = null;
      U.MediaMetadata mediaMetadata = null;
      boolean isRotated = false;
      int width, height;
      if (TD.isFileLoaded(document.document) && document.thumbnail == null) {
        switch (documentType) {
          case DocumentType.IMAGE:
          case DocumentType.GIF: {
            String filePath = document.document.local.path;
            options = ImageReader.getImageSize(filePath);
            isRotated = U.isExifRotated(filePath);
            width = isRotated ? options.outHeight : options.outWidth;
            height = isRotated ? options.outWidth : options.outHeight;
            if (width <= 0 || height <= 0) {
              return null;
            }
            break;
          }
          case DocumentType.VIDEO: {
            mediaMetadata = U.getMediaMetadata(document.document.local.path);
            if (mediaMetadata == null || !mediaMetadata.hasVideo) {
              return null;
            }
            isRotated = mediaMetadata.isRotated();
            width = isRotated ? mediaMetadata.height : mediaMetadata.width;
            height = isRotated ? mediaMetadata.width : mediaMetadata.height;
            if (width <= 0 || height <= 0) {
              return null;
            }
            break;
          }
          case DocumentType.UNKNOWN:
          default:
            throw new UnsupportedOperationException();
        }
      }
      return new MediaItem(context, tdlib, 0, 0, document, options, isRotated, mediaMetadata).setCaption(caption);
    }
    return null;
  }

  public static MediaItem valueOf (BaseActivity context, Tdlib tdlib, TdApi.Message msg) {
    if (msg == null) {
      return null;
    }
    switch (msg.content.getConstructor()) {
      case TdApiExt.MessageChatEvent.CONSTRUCTOR: {
        TdApiExt.MessageChatEvent event = ((TdApiExt.MessageChatEvent) msg.content);
        switch (event.event.action.getConstructor()) {
          case TdApi.ChatEventPhotoChanged.CONSTRUCTOR: {
            TdApi.ChatEventPhotoChanged changedPhoto = (TdApi.ChatEventPhotoChanged) event.event.action;
            if (changedPhoto.oldPhoto != null || changedPhoto.newPhoto != null) {
              return new MediaItem(context, tdlib, msg.chatId, 0, changedPhoto.newPhoto != null ? changedPhoto.newPhoto : changedPhoto.oldPhoto).setSourceSender(event.event.memberId).setSourceDate(event.event.date);
            }
          }
        }
        break;
      }
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        return new MediaItem(context, tdlib, msg.chatId, msg.id, msg.senderId, msg.date, (TdApi.MessagePhoto) msg.content).setMessage(msg);
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document document = ((TdApi.MessageDocument) msg.content).document;
        if (isMediaDocument(document)) {
          return new MediaItem(context, tdlib, msg.chatId, msg.id, msg.senderId, msg.date, (TdApi.MessageDocument) msg.content).setMessage(msg);
        }
        break;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        return new MediaItem(context, tdlib, msg.chatId, msg.id, msg.senderId, msg.date, (TdApi.MessageVideo) msg.content, true).setMessage(msg);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        return new MediaItem(context, tdlib, msg.chatId, msg.id, msg.senderId, msg.date, (TdApi.MessageAnimation) msg.content).setMessage(msg);
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
        return new MediaItem(context, tdlib, msg.chatId, msg.id, ((TdApi.MessageChatChangePhoto) msg.content).photo).setMessage(msg);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        return new MediaItem(context, tdlib, msg.chatId, msg.id, msg.senderId, msg.date, (TdApi.MessageVideoNote) msg.content).setMessage(msg);
      }
    }
    return null;
  }

  public boolean isRotated () {
    return isGalleryType(type) && U.isRotated(sourceGalleryFile.getRotation());
  }

  public boolean isPostRotated () {
    return isGalleryType(type) && U.isRotated(getPostRotation());
  }

  public boolean isFinallyRotated () {
    return isGalleryType(type) && U.isRotated(sourceGalleryFile.getRotation() + getPostRotation());
  }

  public int getFileId () {
    return targetFile != null ? targetFile.id : 0;
  }

  public void pauseAbandonedDownload () {
    if (getFileProgress() != null && (currentViews == null || !currentViews.hasAnyTargetToInvalidate())) {
      fileProgress.pauseDownload(false);
    }
  }

  public FileProgressComponent getFileProgress () {
    if (fileProgress == null && needCreateGalleryFileProgress) {
      this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, false, 0, 0);
      this.fileProgress.setUseStupidInvalidate();
      this.fileProgress.setIsLocal();
      this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
      this.fileProgress.setFile(sourceGalleryFile.getFile());
      if (currentViews != null) {
        fileProgress.setViewProvider(currentViews);
      }
      needCreateGalleryFileProgress = false;
    }
    return fileProgress;
  }

  /*public void pauseDownload () {
    if (fileProgress != null) {
      fileProgress.pauseDownload();
    }
  }*/

  public static boolean isInvalid (TdApi.Photo photo) {
    return photo == null || photo.sizes == null || photo.sizes.length == 0;
  }

  public void setRotationMetadataListener (ImageFile.RotationListener listener) {
    switch (type) {
      case TYPE_GALLERY_VIDEO: {
        sourceGalleryFile.setRotationMetadataListener(listener);
        break;
      }
    }
  }

  /*Filters*/

  public void deleteFiles () {
    if (filteredFile != null) {
      File file = new File(filteredFile.getFilePath());
      if (file.exists() && !file.delete()) {
        Log.w("Unable to delete filtered file");
      }
    }
    U.deleteGalleyFile(new File(sourceGalleryFile.getFilePath()));
    List<File> extraFiles = sourceGalleryFile.copies();
    if (extraFiles != null) {
      for (File file : extraFiles) {
        U.deleteGalleyFile(file);
      }
      extraFiles.clear();
    }
  }

  private ImageFilteredFile filteredFile;

  public ImageFilteredFile getFilteredFile () {
    return filteredFile;
  }

  public ImageFilteredFile setFiltersState (FiltersState state) {
    sourceGalleryFile.setFiltersState(state);
    if (state == null || state.isEmpty()) {
      filteredFile = null;
    } else {
      state.setSessionId(U.isPrivateFile(sourceGalleryFile.getFilePath()), sourceGalleryFile.getGalleryId());
      filteredFile = new ImageFilteredFile(targetImage, state.isPrivateSession(), state.getSessionId());
      filteredFile.setScaleType(ImageFile.FIT_CENTER);
      filteredFile.setRotation(sourceGalleryFile.getVisualRotation());
      filteredFile.setCropState(getCropState());
      filteredFile.setPaintState(getPaintState());
    }
    return filteredFile;
  }

  public FiltersState getFiltersState () {
    return sourceGalleryFile.getFiltersState();
  }

  /*Crop*/

  public CropState getCropState () {
    return sourceGalleryFile != null ? sourceGalleryFile.getCropState() : null;
  }

  public void setCropState (CropState state) {
    sourceGalleryFile.setCropState(state);
    if (filteredFile != null) {
      filteredFile.setCropState(state);
    }
    if (targetImage != null) {
      targetImage.setCropState(state);
    }
    if (previewImageFile != null) {
      previewImageFile.setCropState(state);
    }
  }

  /*Paint*/

  private PaintState actualPaintState;

  public PaintState getPaintState () {
    return actualPaintState != null && !actualPaintState.isEmpty() ? actualPaintState : null;
  }

  public boolean setPaintState (PaintState state, boolean apply) {
    if (state != null && state.isEmpty()) {
      state = null;
    }
    boolean changed = sourceGalleryFile.setPaintState(state);
    if (filteredFile != null) {
      filteredFile.setPaintState(state);
    }
    if (targetImage != null) {
      targetImage.setPaintState(state);
    }
    if (previewImageFile != null) {
      previewImageFile.setPaintState(state);
    }
    if (apply) {
      this.actualPaintState = state;
    }
    return changed;
  }

  /*Video rotation*/

  public float getPostRotation () {
    return sourceGalleryFile != null ? sourceGalleryFile.getPostRotate() : 0f;
  }

  public int postRotateBy90Degrees () {
    int rotation = sourceGalleryFile.rotateBy90Degrees();
    setPostRotate(rotation);
    return rotation;
  }

  public void setPostRotate (int rotation) {
    if (targetImage instanceof ImageGalleryFile) {
      ((ImageGalleryFile) targetImage).setPostRotate(rotation);
    } else {
      targetImage.setRotation(rotation);
    }
    if (previewImageFile instanceof ImageGalleryFile) {
      ((ImageGalleryFile) previewImageFile).setPostRotate(rotation);
    }
  }

  /*Image Crop*/

  public boolean toggleMute () {
    return sourceGalleryFile.toggleMuteVideo();
  }

  public boolean needMute () {
    return (sourceGalleryFile != null && sourceGalleryFile.shouldMuteVideo()) || (type == TYPE_GIF);
  }

  public boolean needTrim () {
    return sourceGalleryFile != null && sourceGalleryFile.hasTrim();
  }

  public long getTrimStartUs () {
    if (needTrim()) {
      return sourceGalleryFile.getStartTimeUs();
    }
    return -1;
  }

  public long getTrimEndUs () {
    if (needTrim()) {
      return sourceGalleryFile.getEndTimeUs();
    }
    return -1;
  }

  public long getTotalDurationUs () {
    if (needTrim()) {
      return sourceGalleryFile.getTotalDurationUs();
    }
    return -1;
  }

  public int getTTL () {
    return sourceGalleryFile != null ? sourceGalleryFile.getTTL() : 0;
  }

  public void setTTL (int ttl) {
    if (sourceGalleryFile != null) {
      sourceGalleryFile.setTTL(ttl);
    }
  }

  public int getType () {
    return type;
  }

  public static boolean isGalleryType (int type) {
    switch (type) {
      case TYPE_GALLERY_GIF:
      case TYPE_GALLERY_VIDEO:
      case TYPE_GALLERY_PHOTO:
        return true;
    }
    return false;
  }

  public boolean isPhoto () {
    switch (type) {
      case TYPE_PHOTO:
      case TYPE_GALLERY_PHOTO: {
        return true;
      }
    }
    return false;
  }

  public boolean isGifType () {
    switch (type) {
      case TYPE_GIF:
      case TYPE_GALLERY_GIF:
        return true;
    }
    return false;
  }

  public boolean isAutoplay () {
    return isGifType() && isVideo();
  }

  public boolean isAnimatedAvatar () {
    switch (type) {
      case TYPE_CHAT_PROFILE:
      case TYPE_USER_PROFILE:
        return chatPhoto != null && (chatPhoto.animation != null || chatPhoto.smallAnimation != null);
    }
    return false;
  }

  public boolean isAvatar () {
    switch (type) {
      case TYPE_CHAT_PROFILE:
        return chatPhoto != null && (isFullPhoto || Td.getSenderId(sourceSender) != 0);
      case TYPE_USER_PROFILE:
        return (chatPhoto != null && isFullPhoto) || Td.getSenderUserId(sourceSender) != 0;
    }
    return false;
  }

  public void requestAvatar (AvatarReceiver avatarReceiver, boolean fullSize) {
    switch (type) {
      case TYPE_CHAT_PROFILE:
      case TYPE_USER_PROFILE: {
        @AvatarReceiver.Options int options = AvatarReceiver.Options.FORCE_ANIMATION | BitwiseUtils.optional(AvatarReceiver.Options.FULL_SIZE, fullSize);
        if (chatPhoto != null && isFullPhoto) {
          avatarReceiver.requestSpecific(tdlib, new AvatarReceiver.FullChatPhoto(chatPhoto, Td.getSenderId(sourceSender)), options);
        } else {
          avatarReceiver.requestMessageSender(tdlib, sourceSender, options | AvatarReceiver.Options.NO_UPDATES);
        }
        break;
      }
      default: {
        avatarReceiver.clear();
        break;
      }
    }
  }

  public boolean isGif () {
    switch (type) {
      case TYPE_GIF:
      case TYPE_GALLERY_GIF: {
        return !isVideo();
      }
    }
    return false;
  }

  public boolean canSeekVideo () {
    return isVideo() && (Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE || getFileProgress().isDownloaded());
  }

  public boolean isVideo () {
    switch (type) {
      case TYPE_VIDEO:
      case TYPE_GALLERY_VIDEO: {
        return true;
      }
      case TYPE_GIF: {
        return StringUtils.equalsOrBothEmpty(sourceAnimation.mimeType, "video/mp4");
      }
    }
    return false;
  }

  public boolean mayBeTransparent () {
    return sourceDocument != null && TGMimeType.isTransparentImageMimeType(sourceDocument.mimeType);
  }

  public boolean isRemoteVideo () {
    switch (type) {
      case TYPE_VIDEO:
        return true;
      case TYPE_GIF:
        return isVideo();
    }
    return false;
  }

  public long getVideoDuration (boolean trimmed, TimeUnit unit) {
    switch (type) {
      case TYPE_VIDEO: {
        return unit.convert(sourceVideo.duration, TimeUnit.SECONDS);
      }
      case TYPE_GIF: {
        return unit.convert(sourceAnimation.duration, TimeUnit.SECONDS);
      }
      case TYPE_GALLERY_VIDEO: {
        return sourceGalleryFile.getVideoDuration(trimmed, unit);
      }
      case TYPE_VIDEO_MESSAGE: {
        return unit.convert(sourceVideoNote.duration, TimeUnit.SECONDS);
      }
    }
    return 0;
  }

  public boolean performClick (View view) {
    return getFileProgress() != null && fileProgress.performClick(view);
  }

  public boolean performClick (View view, float x, float y) {
    return getFileProgress() != null && fileProgress.performClick(view, x, y);
  }

  public boolean onClick (View view, float x, float y) {
    if (getFileProgress() != null) {
      if (isLoaded()) {
        int centerX = fileProgress.centerX();
        int centerY = fileProgress.centerY();
        int bound = Screen.dp(FileProgressComponent.DEFAULT_RADIUS);
        if (x >= centerX - bound && x <= centerX + bound && y >= centerY - bound && y <= centerY + bound) {
          return fileProgress.performClick(view, x, y);
        }
      } else {
        return fileProgress.performClick(view, x, y);
      }
    }
    return false;
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return getFileProgress() != null && fileProgress.onTouchEvent(view, e);
  }

  // View-related stuff

  private MultipleViewProvider currentViews;

  public void attachToView (View view) {
    attachToView(view, null, null);
  }

  public void attachToView (View view, FileProgressComponent.SimpleListener listener, ImageFile.RotationListener rotationListener) {
    final FileProgressComponent fileProgress = view != null ? getFileProgress() : this.fileProgress;
    if (currentViews == null) {
      currentViews = new MultipleViewProvider();
      currentViews.setContentProvider(this);
      if (fileProgress != null) {
        fileProgress.setViewProvider(currentViews);
      }
    }
    if (currentViews.attachToView(view) && fileProgress != null) {
      fileProgress.notifyInvalidateTargetsChanged();
    }
    if (listener != null && fileProgress != null) {
      fileProgress.setSimpleListener(listener);
    }
    setRotationMetadataListener(rotationListener);
  }

  public void detachFromView (View view) {
    if (currentViews != null && currentViews.detachFromView(view) && fileProgress != null) {
      fileProgress.notifyInvalidateTargetsChanged();
    }
  }

  public void setSimpleListener (FileProgressComponent.SimpleListener listener) {
    if (fileProgress != null) {
      fileProgress.setSimpleListener(listener);
    }
  }

  public void setComponentsAlpha (float alpha) {
    if (fileProgress != null) {
      fileProgress.setRequestedAlpha(alpha);
    }
  }

  public float getComponentsAlpha () {
    return fileProgress != null ? fileProgress.getRequestedAlpha() : 0f;
  }

  public <T extends View & DrawableProvider> void drawComponents (T view, Canvas c, int left, int top, int right, int bottom) {
    if (fileProgress != null) {
      fileProgress.setBounds(left, top, right, bottom);
      if (hasSpoiler) {
        fileProgress.setRequestedAlpha(0f, 1f);
      }
      fileProgress.draw(view, c);
    }
  }

  public MediaItem setCaption (TdApi.FormattedText caption) {
    if (isGalleryType(type)) {
      sourceGalleryFile.setCaption(caption);
    } else {
      this.caption = caption;
    }
    return this;
  }

  public TdApi.FormattedText getCaption () {
    if (isGalleryType(type)) {
      return sourceGalleryFile.getCaption(false, false);
    } else {
      return caption != null && !StringUtils.isEmpty(caption.text) ? caption : null;
    }
  }

  private TextEntity[] captionEntities;

  public TextEntity[] getCaptionEntities () {
    if (captionEntities == null) {
      captionEntities = TextEntity.valueOf(tdlib, caption, null);
    }
    return captionEntities;
  }

  // Secret stuff

  private TGMessageMedia secretPhoto;

  public void setSecretPhoto (TGMessageMedia secretPhoto) {
    this.secretPhoto = secretPhoto;
    if (previewImageFile != null) {
      this.previewImageFile.setNeedBlur();
      // this.previewImageFile.setSize(90);
      this.previewImageFile.setIsPrivate();
    }
  }

  public boolean isSecretOutgoing () {
    return secretPhoto != null && secretPhoto.isOutgoing();
  }

  public TGMessageMedia getSecretPhoto () {
    return secretPhoto;
  }

  public boolean isSecret () {
    return secretPhoto != null;
  }

  public void viewSecretContent () {
    if (secretPhoto != null) {
      secretPhoto.readContent();
    }
  }

  // Source-related stuff

  public ImageGalleryFile getSourceGalleryFile () {
    return sourceGalleryFile;
  }

  public long getSourceMessageId () {
    return sourceMessageId;
  }

  public MediaItem setSourceMessage (TdApi.Message msg) {
    this.msg = msg;
    this.sourceChatId = msg.chatId;
    this.sourceMessageId = msg.id;
    return this;
  }

  public MediaItem setSourceMessageId (long chatId, long messageId) {
    this.sourceChatId = chatId;
    this.sourceMessageId = messageId;
    return this;
  }

  public long getSourceChatId () {
    return sourceChatId;
  }

  public TdApi.MessageSender getSourceSender () {
    return sourceSender;
  }

  public int getSourceDate () {
    return sourceDate;
  }

  public TdApi.Video getSourceVideo () {
    return sourceVideo;
  }

  public TdApi.Document getSourceDocument () {
    return sourceDocument;
  }

  // Files-related stuff

  public void download (boolean force) {
    if (fileProgress != null) {
      switch (type) {
        case TYPE_GALLERY_VIDEO: {
          break;
        }
        default: {
          if (sourceChatId != 0 && !force) {
            if (!TD.isFileLoaded(targetFile)) {
              fileProgress.downloadAutomatically(sourceChatId);
            }
          } else {
            fileProgress.downloadIfNeeded();
          }
          break;
        }
      }
    }
  }

  public ImageFile getMiniThumbnail () {
    return miniThumbnail;
  }

  private void setMiniThumbnail (TdApi.Minithumbnail miniThumbnail) {
    if (miniThumbnail != null) {
      this.miniThumbnail = new ImageFileLocal(miniThumbnail);
      this.miniThumbnail.setScaleType(ImageFile.FIT_CENTER);
      this.miniThumbnail.setNeedCancellation(true);
    } else {
      this.miniThumbnail = null;
    }
  }

  public @Nullable ImageFile getPreviewImageFile () {
    return previewImageFile;
  }

  public @Nullable ImageFile getBlurredPreviewImageFile () {
    return blurredPreviewImageFile;
  }

  public TdApi.File getTargetFile () {
    return targetFile;
  }

  public Tdlib tdlib () {
    return tdlib;
  }

  public ImageFile getTargetImageFile (boolean allowFilter) {
    return filteredFile != null && allowFilter ? filteredFile : targetImage;
  }

  public ImageFile getTargetImage () {
    return targetImage;
  }

  public void setPreviewImageFile (ImageFile file) {
    this.previewImageFile = file;
  }

  public GifFile getTargetGifFile () {
    return targetGif;
  }

  public boolean isLoaded () {
    return targetFile == null || TD.isFileLoaded(targetFile) || (fileProgress != null && fileProgress.isLoaded());
  }

  public boolean canBeSaved () {
    if (msg != null) {
      return msg.canBeSaved;
    }
    if (type == TYPE_CHAT_PROFILE) {
      TdApi.Chat chat = tdlib.chat(sourceChatId);
      return chat != null && !chat.hasProtectedContent;
    } else if (type == TYPE_GALLERY_PHOTO || type == TYPE_GALLERY_VIDEO || type == TYPE_GALLERY_GIF) {
      return true;
    }
    return getShareFile() != null;
  }

  public boolean canBeReported () {
    if (msg != null) {
      return tdlib.canReportMessage(msg);
    }
    switch (type) {
      case TYPE_CHAT_PROFILE: {
        return sourceChatId != 0 && tdlib.canReportChatSpam(sourceChatId);
      }
      case TYPE_USER_PROFILE: {
        long userId = ((TdApi.MessageSenderUser) sourceSender).userId;
        long chatId = ChatId.fromUserId(userId);
        return tdlib.canReportChatSpam(chatId) || tdlib.cache().userBot(userId);
      }
    }
    return false;
  }

  public boolean canBeShared () {
    if (msg != null)
      return msg.canBeForwarded;
    return getShareFile() != null;
  }

  public TdApi.File getShareFile () {
    if (getShareMimeType() == null)
      return null;
    return TD.isFileLoaded(targetFile) ? targetFile : fileProgress != null && fileProgress.isLoaded() ? fileProgress.getFile() : null;
  }

  public String getShareMimeType () {
    switch (type) {
      case TYPE_CHAT_PROFILE:
      case TYPE_USER_PROFILE:
      case TYPE_PHOTO:
        return "image/jpeg";
      case TYPE_VIDEO: {
        if (sourceVideo == null)
          return null;
        String mimeType = sourceVideo.mimeType;
        if (StringUtils.isEmpty(mimeType) || !mimeType.startsWith("video/"))
          mimeType = "video/*";
        return mimeType;
      }
      case TYPE_GIF: {
        if (sourceAnimation == null)
          return null;
        String mimeType = sourceAnimation.mimeType;
        if (StringUtils.isEmpty(mimeType) || !(mimeType.startsWith("video/") || mimeType.equals("image/gif")))
          mimeType = "video/*";
        return mimeType;
      }
    }
    return null;
  }

  public TdApi.InputMessageContent createShareContent (TdApi.FormattedText caption) {
    TdApi.InputFile file;
    if (type == TYPE_CHAT_PROFILE || type == TYPE_USER_PROFILE || (sourceChatId != 0 && ChatId.isSecret(sourceChatId))) {
      file = TD.createFileCopy(targetFile);
    } else {
      file = new TdApi.InputFileId(targetFile.id);
    }
    switch (type) {
      case TYPE_CHAT_PROFILE:
      case TYPE_USER_PROFILE:
        if (isAnimatedAvatar()) {
          TdApi.AnimatedChatPhoto targetFile = chatPhoto.animation != null ? chatPhoto.animation : chatPhoto.smallAnimation;
          if (targetFile != null) {
            return new TdApi.InputMessageAnimation(file, null, null, 3, targetFile.length, targetFile.length, null, false);
          }
        }
        return new TdApi.InputMessagePhoto(file, null, null, 640, 640, caption, 0, false);
      case TYPE_PHOTO:
        return new TdApi.InputMessagePhoto(file, null, null, width, height, caption, 0, false);
      case TYPE_VIDEO:
        return new TdApi.InputMessageVideo(file, null, null, sourceVideo.duration, sourceVideo.width, sourceVideo.height, sourceVideo.supportsStreaming, caption, 0, false);
      case TYPE_GIF:
        return new TdApi.InputMessageAnimation(file, null, null, sourceAnimation.duration, sourceAnimation.width, sourceAnimation.height, caption, false);
    }
    return null;
  }

  public boolean isLoading () {
    return fileProgress != null && fileProgress.isLoading();
  }

  @Override
  public boolean invalidateContent (Object cause) {
    int successCount = 0;
    if (currentViews != null) {
      ReferenceList<View> views = currentViews.getViewsList();
      for (View view : views) {
        if (view instanceof MediaSmallView) {
          ((MediaSmallView) view).invalidateContent(this);
          successCount++;
        } else if (view.getParent() instanceof MediaCellView) {
          ((MediaCellView) view.getParent()).invalidateContent(this);
          successCount++;
        }
      }
    }
    if (thumbViewHolder != null) {
      ReferenceList<View> views = thumbViewHolder.getViewsList();
      for (View view : views) {
        if (view instanceof InvalidateContentProvider) {
          ((InvalidateContentProvider) view).invalidateContent(cause);
          successCount++;
        }
      }
    }
    return successCount > 0;
  }

  // Image-related stuff

  public boolean setDimensions (int width, int height) {
    boolean rotated;
    if (sourceGalleryFile != null) {
      rotated = U.isRotated(sourceGalleryFile.getRotation());
    } else if (targetImage instanceof ImageVideoThumbFile) {
      rotated = U.isRotated(((ImageVideoThumbFile) targetImage).getVideoRotation());
    } else {
      rotated = false;
    }
    if (rotated) {
      int temp = width;
      width = height;
      height = temp;
    }
    if (this.width != width || this.height != height) {
      this.width = width;
      this.height = height;
      // TODO
      return true;
    }
    return false;
  }

  public int getWidth () {
    if (sourceGalleryFile != null && sourceGalleryFile.getCropState() != null) {
      CropState cropState = sourceGalleryFile.getCropState();
      if (U.isRotated(cropState.getRotateBy())) {
        return (int) (height * cropState.getRegionWidth());
      } else {
        return (int) (width * cropState.getRegionHeight());
      }
    }
    return width;
  }

  public int getHeight () {
    if (sourceGalleryFile != null && sourceGalleryFile.getCropState() != null) {
      CropState cropState = sourceGalleryFile.getCropState();
      if (U.isRotated(cropState.getRotateBy())) {
        return (int) (width * cropState.getRegionWidth());
      } else {
        return (int) (height * cropState.getRegionHeight());
      }
    }
    return height;
  }

  public int getCropRotateBy () {
    return sourceGalleryFile != null && sourceGalleryFile.getCropState() != null ? sourceGalleryFile.getCropState().getRotateBy() : 0;
  }

  public void setHasSpoiler (boolean hasSpoiler) {
    if (this.hasSpoiler != hasSpoiler) {
      this.hasSpoiler = hasSpoiler;
      // TODO need change any imageFile?
    }
  }

  public boolean hasSpoiler () {
    return hasSpoiler;
  }
}