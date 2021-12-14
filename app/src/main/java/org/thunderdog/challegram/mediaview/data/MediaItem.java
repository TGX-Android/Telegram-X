package org.thunderdog.challegram.mediaview.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.sharedmedia.MediaSmallView;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.data.TdApiExt;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageFilteredFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageVideoThumbFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifFileLocal;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.MessageSourceProvider;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.io.File;
import java.lang.ref.Reference;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;

/**
 * Date: 09/12/2016
 * Author: default
 */

public class MediaItem implements MessageSourceProvider, MultipleViewProvider.InvalidateContentProvider {
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
  private ImageFile previewImageFile;
  private TdApi.File targetFile;
  private FileProgressComponent fileProgress;

  // Source data
  private long sourceChatId, sourceMessageId;
  private TdApi.MessageSender sourceSender;
  private int sourceDate;
  private TdApi.Photo sourcePhoto;
  private TdApi.Video sourceVideo;
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
        return copy;
      }
      case TYPE_GIF: {
        return new MediaItem(item.context, item.tdlib, item.sourceChatId, item.sourceMessageId, item.sourceSender, item.sourceDate, item.sourceAnimation, item.caption).setMessage(item.msg);
      }
      case TYPE_VIDEO: {
        return new MediaItem(item.context, item.tdlib, item.sourceChatId, item.sourceMessageId, item.sourceSender, item.sourceDate, item.sourceVideo, item.caption, allowIcon).setMessage(item.msg);
      }
      case TYPE_USER_PROFILE: {
        return new MediaItem(item.context, item.tdlib, ((TdApi.MessageSenderUser) item.sourceSender).userId, item.profilePhoto);
      }
      case TYPE_CHAT_PROFILE: {
        return new MediaItem(item.context, item.tdlib, item.sourceChatId, item.sourceMessageId, item.chatPhoto);
      }
    }
    return null;
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, TdApi.Photo photo, TdApi.FormattedText caption) {
    this(context, tdlib, 0, 0, photo, false);
    this.caption = caption;
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.Photo photo) {
    this(context, tdlib, sourceChatId, sourceMessageId, photo, false);
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.Photo photo, boolean isWebp) {
    this.type = TYPE_PHOTO;
    this.context = context;
    this.tdlib = tdlib;

    this.sourcePhoto = photo;
    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;

    final TdApi.PhotoSize previewSize = MediaWrapper.buildPreviewSize(photo.sizes);
    final TdApi.PhotoSize targetSize = MediaWrapper.buildTargetFile(photo.sizes, previewSize);

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
      this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, true, sourceChatId, sourceMessageId);
      this.fileProgress.setUseStupidInvalidate();
      this.fileProgress.setFile(targetSize.photo);
    }
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.MessagePhoto photo) {
    this(context, tdlib, sourceChatId, sourceMessageId, photo.photo);
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;
    this.caption = photo.caption;
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.MessageAnimation animation) {
    this(context, tdlib, sourceChatId, sourceMessageId, sourceSender, sourceDate, animation.animation, animation.caption);
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, TdApi.Animation animation, TdApi.FormattedText caption) {
    this(context, tdlib, 0, 0, null, 0, animation, caption);
  }

  private MediaItem (BaseActivity context, Tdlib tdlib, long sourceChatId, long sourceMessageId, TdApi.MessageSender sourceSender, int sourceDate, TdApi.Animation animation, TdApi.FormattedText caption) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_GIF;
    this.caption = caption;
    this.sourceAnimation = animation;
    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;

    setMiniThumbnail(animation.minithumbnail);
    this.previewImageFile = TD.toImageFile(tdlib, animation.thumbnail); // TODO MPEG4 thumbnail support
    if (previewImageFile != null) {
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
      this.previewImageFile.setNeedCancellation(true);
    }
    this.targetFile = animation.animation;

    this.targetGif = new GifFile(tdlib, animation);
    this.targetGif.setScaleType(GifFile.FIT_CENTER);
    if (sourceChatId != 0 && sourceMessageId != 0 && !Settings.instance().needAutoplayGIFs()) {
      this.targetGif.setIsStill(true);
    }

    this.width = animation.width;
    this.height = animation.height;

    if (width == 0 || height == 0) {
      width = height = Screen.dp(100f);
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_GIF, true, sourceChatId, sourceMessageId);
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
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_VIDEO;
    this.caption = caption;
    this.sourceVideo = video;
    this.sourceChatId = sourceChatId;
    this.sourceMessageId = sourceMessageId;
    this.sourceSender = sourceSender;
    this.sourceDate = sourceDate;

    setMiniThumbnail(video.minithumbnail);
    this.previewImageFile = TD.toImageFile(tdlib, video.thumbnail); // TODO MPEG4 support
    if (previewImageFile != null) {
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
      this.previewImageFile.setNeedCancellation(true);
    }
    this.targetFile = video.video;

    this.targetImage = MediaWrapper.createThumbFile(tdlib, video.video);
    this.targetImage.setScaleType(ImageFile.FIT_CENTER);

    this.width = video.width;
    this.height = video.height;

    if (width == 0 || height == 0) {
      width = height = Screen.dp(100f);
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, true, sourceChatId, sourceMessageId);
    this.fileProgress.setUseStupidInvalidate();
    if (allowIcon) {
      this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
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
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long userId, TdApi.ProfilePhoto profilePhoto) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_USER_PROFILE;
    this.sourceSender = new TdApi.MessageSenderUser(userId);
    this.profilePhoto = profilePhoto;

    this.width = this.height = Screen.dp(640f);

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

  public MediaItem setSourceDate (int sourceDate) {
    this.sourceDate = sourceDate;
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
    this(context, tdlib, chatId, 0, new TdApi.ChatPhoto(0, 0, chatPhoto.minithumbnail, new TdApi.PhotoSize[] {new TdApi.PhotoSize("s", chatPhoto.small, 160, 160, null), new TdApi.PhotoSize("m", chatPhoto.big, 640, 640, null)}, null));
  }

  public MediaItem (BaseActivity context, Tdlib tdlib, long chatId, long messageId, TdApi.ChatPhoto photo) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = TYPE_CHAT_PROFILE;
    this.sourceChatId = chatId;
    this.sourceMessageId = messageId;
    this.sourceSender = ChatId.isUserChat(chatId) ? new TdApi.MessageSenderUser(tdlib.chatUserId(chatId)) : new TdApi.MessageSenderChat(chatId);
    this.sourceDate = photo.addedDate;
    this.chatPhoto = photo;

    setMiniThumbnail(photo.minithumbnail);

    TdApi.PhotoSize small = TD.findSmallest(photo.sizes);
    TdApi.PhotoSize big = TD.findBiggest(photo.sizes);

    if (small != null) {
      this.previewImageFile = new ImageFile(tdlib, small.photo);
      this.previewImageFile.setNeedCancellation(true);
      this.previewImageFile.setSize(ChatView.getDefaultAvatarCacheSize());
      this.previewImageFile.setScaleType(ImageFile.FIT_CENTER);
    }

    if (big != null) {
      this.targetFile = big.photo;
      this.targetImage = new ImageFile(tdlib, big.photo);
      this.targetImage.setNeedCancellation(true);
      this.targetImage.setScaleType(ImageFile.FIT_CENTER);
      this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, true, sourceChatId, messageId);
      this.fileProgress.setUseStupidInvalidate();
      this.fileProgress.setFile(big.photo);
    }

    this.width = this.height = 640;

    setSourceSender(tdlib.sender(chatId));
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

      this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, false, 0, 0);
      this.fileProgress.setUseStupidInvalidate();
      this.fileProgress.setIsLocal();
      this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
      this.fileProgress.setFile(imageFile.getFile());
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
      file = fileProgress != null && fileProgress.isLoaded() ? fileProgress.getFile() : file;
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
        TdApi.PhotoSize smallestSize = sourcePhoto != null ? TD.findSmallest(sourcePhoto) : null;
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
    List<Reference<View>> views = thumbViewHolder.getViewsList();
    if (views != null && !views.isEmpty()) {
      final int size = views.size();
      for (int i = size - 1; i >= 0; i--) {
        View view = views.get(i).get();
        if (view != null) {
          if (view instanceof ThumbExpandChangeListener) {
            ((ThumbExpandChangeListener) view).onThumbExpandFactorChanged(this);
          }
        } else {
          views.remove(i);
        }
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

  public static MediaItem valueOf (BaseActivity context, Tdlib tdlib, TdApi.Message msg) {
    if (msg == null) {
      return null;
    }
    switch (msg.content.getConstructor()) {
      case TdApiExt.MessageChatEvent.CONSTRUCTOR: {
        TdApiExt.MessageChatEvent event = ((TdApiExt.MessageChatEvent) msg.content);
        if (event.isFull) {
          switch (event.event.action.getConstructor()) {
            case TdApi.ChatEventPhotoChanged.CONSTRUCTOR: {
              TdApi.ChatEventPhotoChanged changedPhoto = (TdApi.ChatEventPhotoChanged) event.event.action;
              if (changedPhoto.oldPhoto != null || changedPhoto.newPhoto != null) {
                return new MediaItem(context, tdlib, msg.chatId, 0, changedPhoto.newPhoto != null ? changedPhoto.newPhoto : changedPhoto.oldPhoto).setSourceSender(new TdApi.MessageSenderUser(event.event.userId)).setSourceDate(event.event.date);
              }
            }
          }
        }
        break;
      }
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        return new MediaItem(context, tdlib, msg.chatId, msg.id, msg.senderId, msg.date, (TdApi.MessagePhoto) msg.content).setMessage(msg);
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
    if (fileProgress != null && (currentViews == null || !currentViews.hasAnyTargetToInvalidate())) {
      fileProgress.pauseDownload(false);
    }
  }

  public FileProgressComponent getFileProgress () {
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

  public boolean isGif () {
    switch (type) {
      case TYPE_GIF:
      case TYPE_GALLERY_GIF: {
        return !isVideo();
      }
    }
    return false;
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
    return fileProgress != null && fileProgress.performClick(view);
  }

  public boolean onClick (View view, float x, float y) {
    if (fileProgress != null) {
      if (isLoaded()) {
        int centerX = fileProgress.centerX();
        int centerY = fileProgress.centerY();
        int bound = Screen.dp(FileProgressComponent.DEFAULT_RADIUS);
        if (x >= centerX - bound && x <= centerX + bound && y >= centerY - bound && y <= centerY + bound) {
          return fileProgress.performClick(view);
        }
      } else {
        return fileProgress.performClick(view);
      }
    }
    return false;
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return fileProgress != null && fileProgress.onTouchEvent(view, e);
  }

  // View-related stuff

  private MultipleViewProvider currentViews;

  public void attachToView (View view) {
    attachToView(view, null, null);
  }

  public void attachToView (View view, FileProgressComponent.SimpleListener listener, ImageFile.RotationListener rotationListener) {
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
        return new TdApi.InputMessagePhoto(file, null, null, 640, 640, caption, 0);
      case TYPE_PHOTO:
        return new TdApi.InputMessagePhoto(file, null, null, width, height, caption, 0);
      case TYPE_VIDEO:
        return new TdApi.InputMessageVideo(file, null, null, sourceVideo.duration, sourceVideo.width, sourceVideo.height, sourceVideo.supportsStreaming, caption, 0);
      case TYPE_GIF:
        return new TdApi.InputMessageAnimation(file, null, null, sourceAnimation.duration, sourceAnimation.width, sourceAnimation.height, caption);
    }
    return null;
  }

  public boolean isLoading () {
    return fileProgress != null && fileProgress.isLoading();
  }

  @Override
  public void invalidateContent () {
    if (currentViews != null) {
      List<Reference<View>> views = currentViews.getViewsList();
      if (views != null) {
        for (Reference<View> reference : views) {
          View view = reference.get();
          if (view != null) {
            if (view instanceof MediaSmallView) {
              ((MediaSmallView) view).invalidateContent(this);
            } else if (view.getParent() instanceof MediaCellView) {
              ((MediaCellView) view.getParent()).invalidateContent(this);
            }
          }
        }
      }
    }
    if (thumbViewHolder != null) {
      List<Reference<View>> views = thumbViewHolder.getViewsList();
      if (views != null) {
        for (Reference<View> reference : views) {
          View view = reference.get();
          if (view instanceof MultipleViewProvider.InvalidateContentProvider) {
            ((MultipleViewProvider.InvalidateContentProvider) view).invalidateContent();
          }
        }
      }
    }
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
}