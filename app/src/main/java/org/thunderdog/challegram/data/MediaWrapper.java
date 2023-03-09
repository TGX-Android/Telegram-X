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
 * File created on 28/01/2017
 */
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
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.ImageVideoThumbFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifActor;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.widget.FileProgressComponent;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class MediaWrapper implements FileProgressComponent.SimpleListener, FileProgressComponent.FallbackFileProvider {
  public interface OnClickListener {
    boolean onClick (View view, MediaWrapper wrapper);
  }

  private int lastLeft, lastTop, lastRadius;
  protected int cellRight;
  protected int cellBottom;

  protected int cellWidth;
  protected int cellHeight;

  private final Tdlib tdlib;

  private @Nullable TdApi.Photo photo;
  private boolean isPhotoWebp;
  private @Nullable TdApi.PhotoSize previewSize, targetSize;

  private @Nullable TdApi.Animation animation;

  private @Nullable TdApi.Video video;

  private @Nullable TdApi.File targetFile;
  private @Nullable ImageFile targetImageFile;
  private @Nullable GifFile targetGifFile;

  private @Nullable ImageFile miniThumbnail;
  private @Nullable ImageFile previewFile;

  protected int contentWidth;
  protected int contentHeight;

  protected Path path;
  protected final FileProgressComponent fileProgress;
  protected final RectF durationRect = new RectF();

  private long sourceMessageId;
  private @Nullable TGMessage source;

  private @Nullable OnClickListener onClickListener;
  private final boolean useHotStuff;
  private boolean revealOnTap;
  private final BoolAnimator spoilerOverlayVisible = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    if (source != null) {
      source.postInvalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private boolean hideLoader;

  private final BoolAnimator downloadedAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    if (source != null) {
      source.postInvalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 230l);

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.MessagePhoto photo, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff) {
    this(context, tdlib, photo.photo, chatId, messageId, source, useHotStuff, false);
    setRevealOnTap(photo.hasSpoiler);
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.Photo photo, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff) {
    this(context, tdlib, photo, chatId, messageId, source, useHotStuff, false);
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.Photo photo, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff, boolean isWebp) {
    this(context, tdlib, photo, chatId, messageId, source, useHotStuff, isWebp, null);
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.Photo photo, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff, boolean isWebp, @Nullable EmbeddedService nativeEmbed) {
    this.tdlib = tdlib;
    this.source = source;
    this.sourceMessageId = messageId;
    this.useHotStuff = useHotStuff;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      this.path = new Path();
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, !isHot(), chatId, messageId);
    this.fileProgress.setSimpleListener(this);
    this.fileProgress.setFallbackFileProvider(this);

    setNativeEmbed(nativeEmbed, false);

    if (isHot() && source != null) {
      fileProgress.setDownloadedIconRes(source.isHotDone() ? R.drawable.baseline_check_24 : R.drawable.deproko_baseline_whatshot_24);
      if (source.isHotTimerStarted() && !source.isOutgoing()) {
        fileProgress.setHideDownloadedIcon(true);
      }
    }

    setPhoto(messageId, photo, isWebp);
  }

  public void setNativeEmbed (EmbeddedService nativeEmbed, boolean animated) {
    if (nativeEmbed != null) {
      this.nativeEmbed = nativeEmbed;
      int iconRes = nativeEmbed.getIcon();
      if (iconRes != 0) {
        this.embedIcon = ResourcesCompat.getDrawable(UI.getResources(), iconRes, null);
      }
      this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
      this.fileProgress.setPausedIconRes(FileProgressComponent.PLAY_ICON);
      this.fileProgress.setIgnoreLoaderClicks(true);
    }
  }

  public long getSourceMessageId () {
    return sourceMessageId;
  }

  public void setHideLoader (boolean hideLoader) {
    this.hideLoader = hideLoader;
    fileProgress.setIgnoreLoaderClicks(hideLoader);
  }

  private boolean isSafeToStream (TGMessage source) {
    if (source == null) return false; // TODO: PageBlockMedia support&rendering
    return !source.isSecretChat() && !source.isHot();
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.Document document, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff) {
    this(context, tdlib, new TdApi.Video(0, document.thumbnail.width, document.thumbnail.height, document.fileName, document.mimeType, false, true, document.minithumbnail, document.thumbnail, document.document), chatId, messageId, source, useHotStuff);
  }

  private void setVideoStreamingUi (boolean value) {
    boolean oldValue = this.fileProgress.isVideoStreaming();

    if (oldValue != value) {
      this.fileProgress.setIgnoreLoaderClicks(value);
      this.fileProgress.setVideoStreaming(value);

      if (value) {
        this.fileProgress.setHideDownloadedIcon(true);
        this.fileProgress.setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
        this.fileProgress.setPausedIconRes(R.drawable.baseline_cloud_download_24);
      } else {
        this.fileProgress.setHideDownloadedIcon(source != null && source.isHotTimerStarted() && !source.isOutgoing());
        this.fileProgress.setDownloadedIconRes(isHot() ? (source != null && source.isHotDone() ? R.drawable.baseline_check_24 : R.drawable.deproko_baseline_whatshot_24) : FileProgressComponent.PLAY_ICON);
        this.fileProgress.setPausedIconRes(R.drawable.baseline_file_download_24);
      }

      if (source != null && !source.isSecretChat() && Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) {
        this.downloadedAnimator.setValue(this.fileProgress.isDownloaded(), false);
      }

      this.fileProgress.vsLayout();
    }
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.MessageVideo video, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff) {
    this(context, tdlib, video.video, chatId, messageId, source, useHotStuff);
    setRevealOnTap(video.hasSpoiler);
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.Video video, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff) {
    this.tdlib = tdlib;
    this.source = source;
    this.sourceMessageId = messageId;
    this.useHotStuff = useHotStuff;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      this.path = new Path();
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, !isHot(), chatId, messageId);
    this.fileProgress.setDownloadedIconRes(isHot() ? (source != null && source.isHotDone() ? R.drawable.baseline_check_24 : R.drawable.deproko_baseline_whatshot_24) : FileProgressComponent.PLAY_ICON);
    this.fileProgress.setSimpleListener(this);

    if (source != null && source.isHotTimerStarted() && !source.isOutgoing()) {
      fileProgress.setHideDownloadedIcon(true);
    }

    setVideo(messageId, video);
  }

  private void setVideo (long messageId, TdApi.Video video) {
    this.video = video;

    if ((video.width == 0 || video.height == 0) && video.thumbnail != null) {
      video.width = video.thumbnail.width;
      video.height = video.thumbnail.height;
    }

    /*if (video.thumbnail != null && (video.width <= video.height) != (video.thumbnail.width <= video.thumbnail.height)) {
      int temp = video.width;
      video.width = video.height;
      video.height = temp;
    }*/

    setPreviewFile(video.minithumbnail, video.thumbnail);

    this.targetFile = video.video;

    this.targetImageFile = createThumbFile(tdlib, video.video);
    this.targetImageFile.setScaleType(ImageFile.CENTER_CROP);

    this.contentWidth = video.width;
    this.contentHeight = video.height;

    if (contentWidth == 0 || contentHeight == 0) {
      contentWidth = contentHeight = Screen.dp(100f);
    }

    updateVideoStreamingState();

    this.fileProgress.setFile(video.video, source != null ? source.getMessage(messageId) : null);

    if (source != null && !source.isSecretChat() && Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) {
      this.downloadedAnimator.setValue(this.fileProgress.isDownloaded(), false);
    }

    updateDuration();
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.MessageAnimation animation, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff) {
    this(context, tdlib, animation.animation, chatId, messageId, source, useHotStuff, false, false, null);
    setRevealOnTap(animation.hasSpoiler);
  }

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.Animation animation, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff) {
    this(context, tdlib, animation, chatId, messageId, source, useHotStuff, false, false, null);
  }

  private boolean forceNoAutoPlay;

  public MediaWrapper (BaseActivity context, Tdlib tdlib, @NonNull TdApi.Animation animation, long chatId, long messageId, @Nullable TGMessage source, boolean useHotStuff, boolean customAutoplay, boolean noAutoplay, EmbeddedService nativeEmbed) {
    this.tdlib = tdlib;
    this.useHotStuff = useHotStuff;
    this.source = source;
    this.sourceMessageId = messageId;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      this.path = new Path();
    }

    this.fileProgress = new FileProgressComponent(context, tdlib, TdlibFilesManager.DOWNLOAD_FLAG_GIF, true, chatId, messageId);
    this.fileProgress.setSimpleListener(this);
    setNativeEmbed(nativeEmbed, true);
    if (isHot()) {
      fileProgress.setDownloadedIconRes(R.drawable.deproko_baseline_whatshot_24);
    } else if ((customAutoplay && noAutoplay) || (!customAutoplay && !Settings.instance().needAutoplayGIFs())) {
      this.forceNoAutoPlay = true;
      this.fileProgress.setDownloadedIconRes(R.drawable.deproko_baseline_gif_24);
    }

    setAnimation(messageId, animation);
  }

  private void setAnimation (long messageId, TdApi.Animation animation) {
    this.animation = animation;

    setPreviewFile(animation.minithumbnail, animation.thumbnail);
    this.targetFile = animation.animation;

    this.targetGifFile = new GifFile(tdlib, animation);
    this.targetGifFile.setScaleType(ImageFile.CENTER_CROP);
    if (Math.max(animation.width, animation.height) > 1280) {
      this.targetGifFile.setRequestedSize(1280);
    }
    if (forceNoAutoPlay) {
      this.targetGifFile.setIsStill(true);
    }

    this.contentWidth = animation.width;
    this.contentHeight = animation.height;

    if (contentWidth == 0 || contentHeight == 0) {
      contentWidth = contentHeight = Screen.dp(100f);
    }
    this.fileProgress.setFile(targetFile, source != null ? source.getMessage(messageId) : null);
    updateDuration();
  }

  public void setOnClickListener (@Nullable OnClickListener onClickListener) {
    this.onClickListener = onClickListener;
  }

  public void resetState () {
    spoilerOverlayVisible.forceValue(revealOnTap, revealOnTap ? 1f : 0f);
  }

  public void setRevealOnTap (boolean revealOnTap) {
    if (this.revealOnTap != revealOnTap) {
      this.revealOnTap = revealOnTap;
      updateRevealOnTap();
    }
  }

  private void updateRevealOnTap () {
    spoilerOverlayVisible.setValue(this.revealOnTap && !(source != null && source.isNotSent()), source != null && source.needAnimateChanges());
    updateIgnoreLoaderClicks();
  }

  private void updateIgnoreLoaderClicks () {
    fileProgress.setIgnoreLoaderClicks(spoilerOverlayVisible.getValue() || this.fileProgress.isVideoStreaming() || hideLoader);
  }

  public void setNoRoundedCorners () {
    this.path = null;
  }

  public boolean isVideo () {
    return video != null;
  }

  public boolean isPhoto () {
    return photo != null;
  }

  public boolean isGif () {
    return animation != null;
  }

  // Stub

  // private static final boolean USE_STATIC_STUB = false;

  // private static TdApi.Photo stubPhoto;

  /*public static TdApi.Photo getStubPhoto () {
    return stubPhoto;
  }*/

  public void loadStubPhoto (final String query) {
   /* if (USE_STATIC_STUB) {
      String previewPersistentFile = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/66/Agasthiyamalai_range_and_Tirunelveli_rainshadow.jpg/193px-Agasthiyamalai_range_and_Tirunelveli_rainshadow.jpg";
      String persistentFile = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/66/Agasthiyamalai_range_and_Tirunelveli_rainshadow.jpg/640px-Agasthiyamalai_range_and_Tirunelveli_rainshadow.jpg";

      this.previewFile = new ImageFilePersistent(previewPersistentFile, new TdApi.FileTypeThumb());
      this.previewFile.setScaleType(ImageFile.CENTER_CROP);

      this.targetImageFile = new ImageFilePersistent(persistentFile, new TdApi.FileTypePhoto());
      this.targetImageFile.setScaleType(ImageFile.CENTER_CROP);

      if (!fileProgress.isLoaded()) {
        fileProgress.setCurrentState(TGDownloadManager.STATE_IN_PROGRESS, false);
      }
      TG.getClientInstance().send(new TdApi.GetFilePersistent(persistentFile, new TdApi.FileTypePhoto()), new Client.ResultHandler() {
        @Override
        public void onResult (TdApi.TLObject object) {
          if (object.getConstructor() == TdApi.File.CONSTRUCTOR) {
            final TdApi.File file = (TdApi.File) object;
            TGDataManager.runOnUiThread(new Runnable() {
              @Override
              public void run () {
                if (!destroyed) {
                  fileProgress.setFile(targetFile = file);
                  fileProgress.downloadIfNeeded();
                }
              }
            });
          }
        }
      });
    } else {*/
      fileProgress.setCurrentState(TdlibFilesManager.STATE_IN_PROGRESS, false);
      getStubPhoto(tdlib.getPhotoSearchBotUsername(), query);
    // }
  }

  private void getStubPhoto (String username, String query) {
    tdlib.client().send(new TdApi.SearchPublicChat(username), object -> {
      if (object.getConstructor() == TdApi.Chat.CONSTRUCTOR && !destroyed) {
        TdApi.Chat chat = tdlib.objectToChat(object);
        tdlib.client().send(new TdApi.GetInlineQueryResults(TD.getUserId(chat.type), chat.id, null, query, null), object1 -> {
          if (object1.getConstructor() == TdApi.InlineQueryResults.CONSTRUCTOR && !destroyed) {
            TdApi.InlineQueryResults results = (TdApi.InlineQueryResults) object1;
            if (results.results.length > 0) {
              int random = (int) (Math.random() * (double) (results.results.length - 1));
              TdApi.InlineQueryResult result = results.results[random];
              if (result.getConstructor() == TdApi.InlineQueryResultPhoto.CONSTRUCTOR) {
                final TdApi.InlineQueryResultPhoto photo = (TdApi.InlineQueryResultPhoto) result;
                tdlib.ui().post(() -> {
                  if (!destroyed) {
                    setPhoto(0, photo.photo, false);
                    fileProgress.invalidateContent();
                    fileProgress.downloadIfNeeded();
                  }
                });
              }
            } else if (!StringUtils.equalsOrBothEmpty(username, "pic")) {
              getStubPhoto("pic", query);
            }
          }
        });
      }
    });
  }

  // Photo

  private static final float MAX_HEIGHT_FACTOR = 1.7f;
  public static final int MAX_BITMAP_SIZE = 1024;

  public static boolean applyMaxSize (ImageFile file, TdApi.PhotoSize size) {
    return applyMaxSize(file, size.width, size.height);
  }

  public static boolean applyMaxSize (ImageFile file, int width, int height) {
    if (file != null) {
      if (Math.max(width, height) > MAX_BITMAP_SIZE) {
        file.setSize(MAX_BITMAP_SIZE);
        return true;
      }
    }
    return false;
  }

  public static @Nullable TdApi.PhotoSize pickDisplaySize (Tdlib tdlib, TdApi.PhotoSize[] sizes, long chatId) {
    TdApi.PhotoSize previewSize = MediaWrapper.buildPreviewSize(sizes);
    TdApi.PhotoSize targetSize = MediaWrapper.buildTargetFile(sizes, previewSize);
    if (targetSize != null && (TD.isFileLoaded(targetSize.photo) || tdlib.files().canAutomaticallyDownload(targetSize.photo, TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, tdlib.chatType(chatId)))) {
      return targetSize;
    } else {
      return previewSize;
    }
  }

  public static @Nullable TdApi.File pickDisplaySizeFile (Tdlib tdlib, TdApi.Photo photo, long chatId) {
    TdApi.PhotoSize size = pickDisplaySize(tdlib, photo.sizes, chatId);
    return size != null ? size.photo : null;
  }

  public static @Nullable TdApi.PhotoSize buildTargetFile (TdApi.Photo photo) {
    TdApi.PhotoSize smallest = buildPreviewSize(photo.sizes);
    return buildTargetFile(photo.sizes, smallest);
  }

  public static int getWidth (TdApi.Photo photo, TdApi.PhotoSize targetSize) {
    if (targetSize != null)
      return targetSize.width;
    TdApi.PhotoSize biggestSize = Td.findBiggest(photo);
    if (biggestSize != null)
      return biggestSize.width;
    return 0;
  }

  public static int getHeight (TdApi.Photo photo, TdApi.PhotoSize targetSize) {
    if (targetSize != null)
      return targetSize.height;
    TdApi.PhotoSize biggestSize = Td.findBiggest(photo);
    if (biggestSize != null)
      return biggestSize.height;
    return 0;
  }

  public static @Nullable TdApi.PhotoSize buildTargetFile (TdApi.Photo photo, TdApi.PhotoSize previewSize) {
    return photo != null ? buildTargetFile(photo.sizes, previewSize) : null;
  }

  public static @Nullable TdApi.PhotoSize buildTargetFile (TdApi.PhotoSize[] sizes, TdApi.PhotoSize previewSize) {
    if (sizes == null)
      return null;
    if (sizes.length == 1) {
      return previewSize == null || previewSize.photo.id != sizes[0].photo.id ? sizes[0] : null;
    }
    TdApi.PhotoSize size = TD.findPhotoSize(sizes, "i");
    if (size != null && (size.photo.local.canBeDownloaded || TD.isFileLoadedAndExists(size.photo)) && (previewSize == null || previewSize.photo.id != size.photo.id)) {
      return size;
    }
    TdApi.PhotoSize biggestLoadedSize = null;
    for (TdApi.PhotoSize photoSize : sizes) {
      if (previewSize != null && photoSize.photo.id == previewSize.photo.id)
        continue;
      if (biggestLoadedSize != null && Math.max(biggestLoadedSize.width, biggestLoadedSize.height) > Math.max(photoSize.width, photoSize.height))
        continue;
      if (TD.isFileLoadedAndExists(photoSize.photo))
        biggestLoadedSize = photoSize;
    }
    if (biggestLoadedSize != null && Td.findSmallest(sizes).photo.id != biggestLoadedSize.photo.id)
      return biggestLoadedSize;
    int maxWidth = TGMessage.getEstimatedContentMaxWidth();
    int maxHeight = (int) ((float) maxWidth * MAX_HEIGHT_FACTOR);
    return TD.findPhotoSize(sizes, maxWidth, maxHeight, previewSize != null ? previewSize.photo.id : 0, "i");
  }

  public static TdApi.PhotoSize buildPreviewSize (TdApi.Photo photo) {
    return photo  != null ? buildPreviewSize(photo.sizes) : null;
  }

  public static TdApi.PhotoSize buildPreviewSize (TdApi.PhotoSize[] sizes) {
    TdApi.PhotoSize size = TD.findPhotoSize(sizes, "t");
    if (size != null && (size.photo.local.canBeDownloaded || TD.isFileLoadedAndExists(size.photo))) {
      return size;
    }
    TdApi.PhotoSize smallest = null;
    boolean hasOtherSizes = false;
    for (TdApi.PhotoSize photoSize : sizes) {
      if (smallest == null || Math.max(photoSize.width, photoSize.height) < Math.max(smallest.width, smallest.height)) {
        smallest = photoSize;
      } else if (!hasOtherSizes && smallest.photo.id != photoSize.photo.id) {
        hasOtherSizes = true;
      }
    }
    if (hasOtherSizes && Math.max(smallest.width, smallest.height) <= 90) {
      return smallest;
    }
    return null;
  }

  public static ImageVideoThumbFile createThumbFile (Tdlib tdlib, TdApi.File file) {
    int maxWidth = TGMessage.getEstimatedContentMaxWidth();
    int maxHeight = (int) ((float) maxWidth * MAX_HEIGHT_FACTOR);
    ImageVideoThumbFile thumb = new ImageVideoThumbFile(tdlib, file);
    thumb.setMaxSize(maxWidth, maxHeight);
    thumb.setFrameTimeUs(0);
    return thumb;
  }

  private void setPhotoSilently (TdApi.Photo photo) {
    int i = 0;
    boolean updatedTarget = targetSize == null;
    boolean updatedPreview = previewSize == null;
    for (TdApi.PhotoSize size : photo.sizes) {
      if (previewSize != null && previewSize.photo.id == size.photo.id) {
        previewSize.width = size.width;
        previewSize.height = size.height;
        photo.sizes[i] = previewSize;
        updatedPreview = true;
      }
      if (targetSize != null && targetSize.photo.id == size.photo.id) {
        targetSize.width = size.width;
        targetSize.height = size.height;
        photo.sizes[i] = targetSize;
        updatedTarget = true;
      }
      i++;
    }
    if (updatedTarget && updatedPreview) {
      this.photo = photo;
    }
  }

  private void setPhoto (long messageId, @NonNull TdApi.Photo photo, boolean isWebp) {
    this.photo = photo;
    this.isPhotoWebp = isWebp;

    TdApi.PhotoSize previewSize = buildPreviewSize(photo.sizes);
    TdApi.PhotoSize targetSize = buildTargetFile(photo.sizes, previewSize);
    this.contentWidth = getWidth(photo, targetSize);
    this.contentHeight = getHeight(photo, targetSize);
    if (contentWidth == 0 || contentHeight == 0) {
      contentWidth = contentHeight = Screen.dp(100f);
    }

    if (this.previewSize != null && previewSize != null && isSameContent(this.previewSize.photo, previewSize.photo)) {
      this.previewSize.width = previewSize.width;
      this.previewSize.height = previewSize.height;
      previewSize = this.previewSize;
    }
    if (this.targetSize != null & targetSize != null && isSameContent(this.targetSize.photo, targetSize.photo)) {
      this.targetSize.width = targetSize.width;
      this.targetSize.height = targetSize.height;
      targetSize = this.targetSize;
    }

    setPreviewSize(photo.minithumbnail, previewSize);
    if (setTargetSize(targetSize)) {
      this.fileProgress.setFile(targetSize != null ? targetSize.photo : null, source != null ? source.getMessage(messageId) : null);
    }
  }

  private boolean setTargetSize (@Nullable TdApi.PhotoSize targetSize) {
    if (isSameContent(this.targetFile, targetSize != null ? targetSize.photo : null)) {
      return false;
    }
    if (targetSize != null) {
      this.targetFile = targetSize.photo;
      this.targetImageFile = new ImageFile(tdlib, targetSize.photo);
      this.targetImageFile.setScaleType(ImageFile.CENTER_CROP);
      this.targetImageFile.setNoBlur();
      if (isPhotoWebp) {
        this.targetImageFile.setWebp();
      }
      MediaWrapper.applyMaxSize(targetImageFile, targetSize);
    } else {
      this.targetImageFile = null;
    }
    return true;
  }

  private static boolean isSameContent (TdApi.File a, TdApi.File b) {
    return (a == null && b == null) || (a != null && b != null && ((a.id == b.id) || (a.local != null && b.local != null && StringUtils.equalsOrBothEmpty(a.local.path, b.local.path) && !StringUtils.isEmpty(a.local.path))));
  }

  private void setPreviewSize (TdApi.Minithumbnail minithumbnail,  @Nullable TdApi.PhotoSize previewSize) {
    if (!isSameContent(this.previewSize != null ? this.previewSize.photo : null, previewSize != null ? previewSize.photo : null)) {
      this.previewSize = previewSize;
      setPreviewFile(minithumbnail, TD.toThumbnail(previewSize));
    } else if (this.miniThumbnail == null && minithumbnail != null) {
      miniThumbnail = new ImageFileLocal(minithumbnail);
    }
  }

  private void setPreviewFile (TdApi.Minithumbnail minithumbnail, TdApi.Thumbnail thumbnail) {
    if (minithumbnail != null) {
      miniThumbnail = new ImageFileLocal(minithumbnail);
    } else {
      miniThumbnail = null;
    }
    final ImageFile previewFile = TD.toImageFile(tdlib, thumbnail);
    if (previewFile != null) {
      previewFile.setScaleType(ImageFile.CENTER_CROP);
      if (isPhotoWebp) {
        previewFile.setWebp();
      }
      if (isHot()) {
        previewFile.setNeedBlur();
        previewFile.setSize(90);
        previewFile.setIsPrivate();
      }
    }
    this.previewFile = previewFile;
  }

  @Override
  public TdApi.File provideFallbackFile (TdApi.File file) {
    if (targetImageFile != null && targetSize != null && StringUtils.equalsOrBothEmpty(targetSize.type, "i") && file.id == targetSize.photo.id) {
      TdApi.PhotoSize previewSize = MediaWrapper.buildPreviewSize(photo);
      if (this.previewSize != null && previewSize != null && this.previewSize.photo.id != previewSize.photo.id) {
        setPreviewSize(photo.minithumbnail, previewSize);
      }
      TdApi.PhotoSize targetSize = MediaWrapper.buildTargetFile(photo, previewSize);
      if (targetSize != null && targetSize.photo.id != file.id) {
        setTargetSize(targetSize);
        return targetSize.photo;
      }

    }
    return null;
  }

  public @NonNull FileProgressComponent getFileProgress () {
    return fileProgress;
  }

  public void updateMessageId (long oldMessageId, long newMessageId, boolean success) {
    if (this.sourceMessageId == oldMessageId) {
      this.sourceMessageId = newMessageId;
    }
    getFileProgress().updateMessageId(oldMessageId, newMessageId, success);
    updateDuration();
    updateRevealOnTap();
  }

  public @Nullable TdApi.Photo getPhoto () {
    return photo;
  }

  public @Nullable TdApi.Video getVideo () {
    return video;
  }

  public @Nullable TdApi.Animation getAnimation () {
    return animation;
  }

  @Nullable
  public TdApi.File getTargetFile () {
    return targetFile;
  }

  @Nullable
  public ImageFile getTargetImageFile () {
    return targetImageFile;
  }

  @Nullable
  public GifFile getTargetGifFile () {
    return targetGifFile;
  }

  private boolean isHot () {
    return useHotStuff && source != null && source.isHot();
  }

  public boolean showPreview () {
    return true;
  }

  public void requestPreview (DoubleImageReceiver receiver) {
    if (showPreview()) {
      receiver.requestFile(this.miniThumbnail, this.previewFile);
    } else {
      receiver.clear();
    }
  }

  private boolean showImage () {
    return targetImageFile != null && TD.isFileLoaded(targetFile) && (fileProgress == null || fileProgress.isDownloaded()) && !isHot();
  }

  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(showImage() ? targetImageFile : null);
  }

  public void requestGif (GifReceiver receiver) {
    receiver.requestFile(targetGifFile != null && TD.isFileLoaded(targetFile) && (fileProgress == null || fileProgress.isDownloaded()) && !isHot() ? targetGifFile : null);
  }

  public boolean needGif () {
    return targetGifFile != null;
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return fileProgress.onTouchEvent(view, e);
  }

  public boolean performLongPress (View view) {
    return fileProgress.performLongPress(view);
  }

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    fileProgress.setViewProvider(viewProvider);
  }

  private FactorAnimator selectionAnimator;

  public void setSelectionAnimator (FactorAnimator selectionAnimator) {
    this.selectionAnimator = selectionAnimator;
  }

  private static final int ROUND_TOP_LEFT = 1;
  private static final int ROUND_TOP_RIGHT = 1 << 1;
  private static final int ROUND_BOTTOM_LEFT = 1 << 2;
  private static final int ROUND_BOTTOM_RIGHT = 1 << 3;

  private static final int ROUND_ALL_CORNERS = ROUND_TOP_LEFT | ROUND_TOP_RIGHT | ROUND_BOTTOM_LEFT | ROUND_BOTTOM_RIGHT;

  private int roundings = ROUND_ALL_CORNERS;

  public void setNeedRound (boolean needTopLeft, boolean needTopRight, boolean needBottomRight, boolean needBottomLeft) {
    int roundings = 0;
    if (needTopLeft) {
      roundings |= ROUND_TOP_LEFT;
    }
    if (needTopRight) {
      roundings |= ROUND_TOP_RIGHT;
    }
    if (needBottomLeft) {
      roundings |= ROUND_BOTTOM_LEFT;
    }
    if (needBottomRight) {
      roundings |= ROUND_BOTTOM_RIGHT;
    }
    if (this.roundings != roundings) {
      this.roundings = roundings;
      layoutPath(lastLeft, lastTop, lastRadius);
    }
  }

  private boolean useBubbles () {
    return source != null ? source.useBubbles() : tdlib.settings().useBubbles();
  }

  private void layoutPath (int startX, int startY, int radius) {
    cellRight = startX + cellWidth;
    cellBottom = startY + cellHeight;

    lastLeft = startX;
    lastTop = startY;

    lastRadius = radius;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null) {
      path.reset();
      RectF rectF = Paints.getRectF();
      rectF.set(lastLeft, lastTop, cellRight, cellBottom);
      int halfRadius = radius / 2;
      DrawAlgorithms.buildPath(path, rectF,
        (roundings & ROUND_TOP_LEFT) != 0 ? halfRadius : 0,
        (roundings & ROUND_TOP_RIGHT) != 0 ? halfRadius : 0,
        (roundings & ROUND_BOTTOM_RIGHT) != 0 ? halfRadius : 0,
        (roundings & ROUND_BOTTOM_LEFT) != 0 ? halfRadius : 0
      );
    }
  }

  private int getRadius () {
    return Screen.dp(useBubbles() ? Theme.getBubbleMergeRadius() : Theme.getImageRadius());
  }

  public int getContentWidth () {
    return contentWidth;
  }

  public int getCellWidth () {
    return cellWidth;
  }

  public int getContentHeight () {
    return contentHeight;
  }

  public int getCellHeight () {
    return cellHeight;
  }

  public int getCellRight () {
    return cellRight;
  }

  public int getCellBottom () {
    return cellBottom;
  }

  public int getCellLeft () {
    return lastLeft;
  }

  public int getCellTop () {
    return lastTop;
  }

  public int getCenterX () {
    return (lastLeft + cellRight) >> 1;
  }

  public int getCenterY () {
    return (lastTop + cellBottom) >> 1;
  }

  public void buildContent (int width, int height) {
    boolean widthChanged = cellWidth != width;
    if (widthChanged || this.cellHeight != height) {
      this.cellWidth = width;
      this.cellHeight = height;
      this.lastLeft = this.lastTop = -1;
      if (widthChanged && !StringUtils.isEmpty(duration)) {
        if (!(isVideo() && !getFileProgress().isLoaded() && getFileProgress().isVideoStreaming())) {
          trimDuration();
        } else {
          trimDoubleDuration(durationAlternative, durationAlternativeWidth);
        }
      }
    }
  }

  public boolean setImageScaling (int size) {
    size = Math.min(MAX_BITMAP_SIZE, size);
    if (targetImageFile != null && targetImageFile.getSize() != size) {
      targetImageFile.setSize(size);
      return showImage();
    }
    return false;
  }

  public <T extends View & DrawableProvider> void draw (T view, Canvas c, int startX, int startY, Receiver preview, Receiver receiver, float alpha) {
    final float selectionFactor = selectionAnimator != null && source != null ? source.getSelectionFactor(selectionAnimator) : 0f;
    final boolean clipped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null && !(source != null && source.useFullWidth());
    final int saveCount;
    if (clipped) {
      int radius = getRadius();
      if (lastLeft != startX || lastTop != startY || lastRadius != radius) {
        layoutPath(startX, startY, radius);
      }
      saveCount = ViewSupport.clipPath(c, path);
    } else {
      saveCount = Integer.MIN_VALUE;
    }

    int selectionPadding = (int) ((float) Screen.dp(12f) * selectionFactor);

    int cellLeft = lastLeft = startX;
    int cellTop = lastTop = startY;

    int cellRight = cellLeft + cellWidth;
    int cellBottom = cellTop + cellHeight;

    int selectionColor = selectionFactor > 0f ? Theme.chatSelectionColor() : 0;
    int overlayColor = selectionFactor > 0f ? ColorUtils.alphaColor(selectionFactor < .5f ? selectionFactor / .5f : 1f, ColorUtils.compositeColor(source != null ? source.getContentBackgroundColor() : Theme.fillingColor(), selectionColor)) : 0;
    if (selectionPadding > 0) {
      c.drawRect(cellLeft, cellTop, cellRight, cellBottom, Paints.fillingPaint(ColorUtils.alphaColor(alpha, selectionColor)));
      cellLeft += selectionPadding;
      cellTop += selectionPadding;
      cellRight -= selectionPadding;
      cellBottom -= selectionPadding;
    }

    fileProgress.setPaddingCompensation(selectionPadding);
    fileProgress.setBounds(cellLeft, cellTop, cellRight, cellBottom);

    DrawAlgorithms.drawReceiver(c, preview, receiver, false, true, cellLeft, cellTop, cellRight, cellBottom);
    float spoilerFactor = spoilerOverlayVisible.getFloatValue();
    if (spoilerFactor > 0f) {
      Receiver spoilerReceiver;
      if (preview instanceof DoubleImageReceiver && (animation != null || video != null)) {
        spoilerReceiver = ((DoubleImageReceiver) preview).getPreview();
      } else {
        spoilerReceiver = preview;
      }
      spoilerReceiver.setPaintAlpha(spoilerFactor);
      spoilerReceiver.draw(c);
      spoilerReceiver.restorePaintAlpha();
      int radius = getRadius();
      DrawAlgorithms.drawRoundRect(c,
        BitwiseUtils.getFlag(roundings, ROUND_TOP_LEFT) ? radius : 0,
        BitwiseUtils.getFlag(roundings, ROUND_TOP_RIGHT) ? radius : 0,
        BitwiseUtils.getFlag(roundings, ROUND_BOTTOM_RIGHT) ? radius : 0,
        BitwiseUtils.getFlag(roundings, ROUND_BOTTOM_LEFT) ? radius : 0,
        cellLeft, cellTop, cellRight, cellBottom,
        Paints.fillingPaint(ColorUtils.alphaColor(spoilerFactor, Theme.getColor(R.id.theme_color_spoilerMediaOverlay)))
      );
      DrawAlgorithms.drawParticles(c,
        BitwiseUtils.getFlag(roundings, ROUND_TOP_LEFT) ? radius : 0,
        BitwiseUtils.getFlag(roundings, ROUND_TOP_RIGHT) ? radius : 0,
        BitwiseUtils.getFlag(roundings, ROUND_BOTTOM_RIGHT) ? radius : 0,
        BitwiseUtils.getFlag(roundings, ROUND_BOTTOM_LEFT) ? radius : 0,
        cellLeft, cellTop, cellRight, cellBottom,
        spoilerFactor
      );
    }

    if (selectionPadding > 0) {
      cellLeft -= selectionPadding;
      cellTop -= selectionPadding;
      cellRight += selectionPadding;
      cellBottom += selectionPadding;
    }

    if (selectionFactor > 0f) {
      int margin = Screen.dp(14f);
      int x = cellRight - margin;
      int y = cellTop + margin;
      SimplestCheckBox.draw(c, x, y, selectionFactor, null);

      RectF rectF = Paints.getRectF();
      int radius = Screen.dp(11f);
      rectF.set(x - radius, y - radius, x + radius, y + radius);

      float add = 45f;
      c.drawArc(rectF, 225f + add, -(170f + add) * selectionFactor, false, Paints.getOuterCheckPaint(ColorUtils.alphaColor(alpha, overlayColor)));
    }

    if (clipped) {
      ViewSupport.restoreClipPath(c, saveCount);

      if (source != null && roundings != 0) {
        int radius = getRadius();
        TGMessage.drawCornerFixes(c, source, 1f, cellLeft, cellTop, cellRight, cellBottom,
          (roundings & ROUND_TOP_LEFT) != 0 ? radius : 0,
          (roundings & ROUND_TOP_RIGHT) != 0 ? radius : 0,
          (roundings & ROUND_BOTTOM_RIGHT) != 0 ? radius : 0,
          (roundings & ROUND_BOTTOM_LEFT) != 0 ? radius : 0
        );
      }
    }

    float dlFactor = 1f - downloadedAnimator.getFloatValue();
    float durationDx = 0f;
    boolean isStreamingUI = fileProgress.isVideoStreaming() && isVideo();
    boolean isDoubleLine = isStreamingUI && duration != null && durationShort != null;
    if (source != null && source.getCombinedMessageCount() > 2) isDoubleLine = false; // 3+ combines can cause size issues
    boolean isSmallStreamingUI = isStreamingUI && !isDoubleLine;
    boolean needTopOffset = source != null && source.useFullWidth() && source.hasHeader() && source.isChannel() && isVideo() && (source instanceof TGMessageMedia && ((TGMessageMedia) source).isVideoFirstInMosaic(video.video.id)) && source.replyData == null;

    // Log.d("%s %s => [%s, %s, trim: %s] <%s>", isStreamingUI, isDoubleLine, duration, durationShort, durationTrimmed, dlFactor);

    RectF actionButtonRect = Paints.getRectF();
    boolean showDuration = !StringUtils.isEmpty(durationTrimmed) && selectionFactor < 1f;

    if (showDuration) {
      // Only if: channel + single item in stack + bubble-less mode

      int fpRadius = !isVideo() || !isDoubleLine ? 0 : downloadedAnimator.isAnimating() ? Screen.dp(FileProgressComponent.DEFAULT_STREAMING_RADIUS) : getFileProgress().getRadius();
      int doubleFpRadius = fpRadius * 2;

      int pDurationCorners = isDoubleLine ? MathUtils.fromTo(Screen.dp(4f), Screen.dp(12f), dlFactor) : Screen.dp(4f);
      int pDurationTop = cellTop + Screen.dp(8f) + (needTopOffset ? Screen.dp(16f) : 0);
      int pDurationLeft = cellLeft + Screen.dp(12f);
      int pDurationRight = (int) (pDurationLeft + ((durationTrimmed != null && fileProgress.isUploading()) ? durationWidth : MathUtils.fromTo(durationWidthShort, durationWidth, dlFactor)) + ((doubleFpRadius) * (isStreamingUI ? dlFactor : 1f)) + ((isStreamingUI) ? MathUtils.fromTo(Screen.dp(4f), Screen.dp(isSmallStreamingUI ? 26f : 16f), dlFactor) : Screen.dp(4f)));
      int pDurationBottom = pDurationTop + (isDoubleLine ? MathUtils.fromTo(durationHeight(), (doubleFpRadius) + Screen.dp(8f), dlFactor) : durationHeight());

      float cellCenterX = cellLeft + cellWidth / 2f;
      float cellCenterY = cellTop + cellHeight / 2f;

      int radius = Screen.dp(28f);
      int radiusCloud = Screen.dp(14f);

      actionButtonRect.set(cellCenterX - radius, cellCenterY - radius + (isSmallStreamingUI ? 0 : Screen.dp(6f)), cellCenterX + radius, cellCenterY + radius);
      durationRect.set(pDurationLeft - Screen.dp(4f), pDurationTop, pDurationRight, pDurationBottom);

      //c.drawRect(durationRect, Paints.getPorterDuffPaint(Color.GREEN));
      //c.drawRect(actionButtonRect, Paints.getPorterDuffPaint(Color.RED));

      //if (durationRect.intersects(actionButtonRect.left, actionButtonRect.top, actionButtonRect.right, actionButtonRect.bottom)) {
      //  getFileProgress().setVideoStreamingOptions(needTopOffset, false, FileProgressComponent.STREAMING_UI_MODE_EXTRA_SMALL, durationRect, downloadedAnimator);
      //} else {
        getFileProgress().setVideoStreamingOptions(needTopOffset, false, isSmallStreamingUI ? FileProgressComponent.STREAMING_UI_MODE_SMALL : FileProgressComponent.STREAMING_UI_MODE_LARGE, durationRect, downloadedAnimator);
      //}

      c.drawRoundRect(durationRect, pDurationCorners, pDurationCorners, Paints.fillingPaint(ColorUtils.alphaColor(alpha * (1f - selectionFactor), 0x4c000000)));

      // This is set to fix text visible outside the rounded rect during the animation
      durationRect.right -= pDurationCorners / 2f;

      Paint paint;

      if (isDoubleLine) {
        paint = Paints.getRegularTextPaint(13f, Color.WHITE);
      } else {
        paint = Paints.whiteMediumPaint(13f, false, false);
      }
      int existingAlpha = paint.getAlpha();

      paint.setAlpha((int) (255f * alpha * (1f - selectionFactor)));

      c.save();
      c.clipRect(durationRect);
      if (isDoubleLine) {
        int textBaseline = pDurationLeft + (int) MathUtils.fromTo(0, (durationDx = doubleFpRadius + Screen.dp(6f)), dlFactor);
        int textYBaseline = (int) MathUtils.fromTo(pDurationTop + durationOffset(), pDurationTop + (((pDurationTop + doubleFpRadius + Screen.dp(8f)) - pDurationTop) / 2f), dlFactor);
        Paint mediumPaint = Paints.whiteMediumPaint(13f, false, false);
        mediumPaint.setAlpha(paint.getAlpha());
        paint.setAlpha((int) (paint.getAlpha() * dlFactor));
        c.drawText(durationShort, textBaseline, textYBaseline - Screen.dp(4f), mediumPaint);
        c.drawText(duration, textBaseline, textYBaseline + Screen.dp(13f), paint);
      } else {
        float textX = pDurationLeft + (isStreamingUI ? Screen.dp(20f) * dlFactor : 0);
        float textY = pDurationTop - Screen.dp(4f) + durationOffset();
        durationDx = isStreamingUI ? (Screen.dp(20f)) : 0;
        if (!fileProgress.isUploading() && durationShort != null) {
          int paintAlpha = paint.getAlpha();
          paint.setAlpha((int) (paintAlpha * dlFactor));
          c.drawText(duration != null ? duration : durationTrimmed, textX, textY, paint);
          paint.setAlpha((int) (paintAlpha * downloadedAnimator.getFloatValue()));
          c.drawText(durationShort, textX, textY, paint);
        } else if (durationTrimmed != null) {
          c.drawText(durationTrimmed, textX, textY, paint);
        }
      }

      c.restore();
      paint.setAlpha(existingAlpha);
    } else if (isVideo() && Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) {
      getFileProgress().setVideoStreamingOptions(needTopOffset, true, isSmallStreamingUI ? FileProgressComponent.STREAMING_UI_MODE_SMALL : FileProgressComponent.STREAMING_UI_MODE_LARGE, durationRect, downloadedAnimator);
    }

    if (isSmallStreamingUI) {
      getFileProgress().setPausedIconRes(R.drawable.baseline_cloud_download_16);
    } else if (isStreamingUI) {
      getFileProgress().setPausedIconRes(R.drawable.baseline_cloud_download_24);
    }

    if (!hideLoader) {
      getFileProgress().setRequestedAlpha(alpha * (1f - selectionFactor) * (1f - spoilerFactor), alpha * (1f - selectionFactor));
      if (isStreamingUI) {
        fileProgress.drawClipped(view, c, durationRect, (-durationDx) * downloadedAnimator.getFloatValue());
      } else {
        fileProgress.draw(view, c);
      }
    }

    if (nativeEmbed != null && embedIcon != null) {
      // Drawables.draw(c, embedIcon, receiver.centerX() - embedIcon.getMinimumWidth() / 2, receiver.centerY() - embedIcon.getMinimumHeight() / 2, Paints.getBitmapPaint());
      RectF rectF = Paints.getRectF();
      int marginRight = Screen.dp(8f);
      int marginBottom = Screen.dp(8f);
      int paddingHorizontal = Screen.dp(nativeEmbed.getPaddingHorizontal());
      int paddingTop = Screen.dp(nativeEmbed.getPaddingTop());
      int paddingBottom = Screen.dp(nativeEmbed.getPaddingBottom());
      rectF.set(receiver.getRight() - marginRight - paddingHorizontal - embedIcon.getMinimumWidth() - paddingHorizontal, receiver.getBottom() - marginBottom - paddingTop - embedIcon.getMinimumHeight() - paddingBottom, receiver.getRight() - marginRight, receiver.getBottom() - marginBottom);
      c.drawRoundRect(rectF, Screen.dp(3f), Screen.dp(3f), Paints.fillingPaint(ColorUtils.alphaColor(alpha, 0x66000000)));
      Paint paint = Paints.getBitmapPaint();
      if (alpha != 1f) {
        paint.setAlpha((int) (255f * alpha));
      }
      Drawables.draw(c, embedIcon, receiver.getRight() - marginRight - paddingHorizontal - embedIcon.getMinimumWidth(), receiver.getBottom() - marginBottom - paddingBottom - embedIcon.getMinimumHeight(), paint);
      if (alpha != 1f) {
        paint.setAlpha(255);
      }
    }
  }

  private EmbeddedService nativeEmbed;
  private Drawable embedIcon;

  private MediaViewThumbLocation thumbLocation;
  public MediaViewThumbLocation getMediaThumbLocation (View view, int viewTop, int viewBottom, int top) {
    if (thumbLocation == null) {
      thumbLocation = new MediaViewThumbLocation();
      thumbLocation.setNoBounce();
    }

    int radius1 = Screen.dp(Theme.getBubbleMergeRadius()); // TGMessage.IMAGE_CONTENT_DEFAULT_RADIUS;
    int radius2 = useBubbles() ? (int) ((float) Screen.dp(Theme.getBubbleDefaultRadius()) / 1.5f) : 0;
    thumbLocation.setRoundings(
      (roundings & ROUND_TOP_LEFT) != 0 ? radius1 : radius2,
      (roundings & ROUND_TOP_RIGHT) != 0 ? radius1 : radius2,
      (roundings & ROUND_BOTTOM_RIGHT) != 0 ? radius1 : radius2,
      (roundings & ROUND_BOTTOM_LEFT) != 0 ? radius1 : radius2
    );

    int actualTop = lastTop + viewTop;
    int actualBottom = (view.getMeasuredHeight() - cellBottom) + viewBottom;

    thumbLocation.set(lastLeft, lastTop + top, lastLeft + cellWidth, lastTop + cellHeight + top);
    thumbLocation.setClip(0, actualTop < 0 ? -actualTop : 0, 0, actualBottom < 0 ? -actualBottom : 0);
    return thumbLocation;
  }

  // File progress

  public boolean isNativeEmbed () {
    return nativeEmbed != null;
  }

  @Override
  public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    if (nativeEmbed != null) {
      nativeEmbed.open(context.context());
      return true;
    }
    if (onClickListener != null && onClickListener.onClick(view, this)) {
      return true;
    }
    if (revealOnTap && spoilerOverlayVisible.getValue()) {
      AtomicBoolean startedPlaying = new AtomicBoolean(false);
      Runnable revealSpoiler = () -> {
        if (!startedPlaying.getAndSet(true)) {
          spoilerOverlayVisible.setValue(false, true);
          updateIgnoreLoaderClicks();
        }
      };
      if (source != null && animation != null && targetGifFile != null && !targetGifFile.isStill()) {
        GifActor.restartGif(targetGifFile, () -> {
          source.runOnUiThreadOptional(revealSpoiler);
        });
        source.runOnUiThread(revealSpoiler, 750);
      } else {
        revealSpoiler.run();
      }
      return true;
    }
    if (source != null) {
      if (source instanceof TGMessageMedia) {
        MediaViewController.openFromMessage((TGMessageMedia) source, messageId);
      } else if (source instanceof TGMessageText) {
        MediaViewController.openFromMessage((TGMessageText) source);
      }
    }
    return true;
  }

  @Override
  public void onStateChanged (TdApi.File file, @TdlibFilesManager.FileDownloadState int state) {
    updateVideoStreamingState();

    if ((video != null || animation != null) && updateDuration()) {
      if (source != null) {
        source.postInvalidate();
      }
    }
  }

  @Override
  public void onProgress (TdApi.File file, float progress) {
    if ((video != null || animation != null) && updateDuration()) {
      if (source != null) {
        source.postInvalidate();
      }
    }
  }

  // Stuff

  public boolean updatePhoto (long sourceMessageId, TdApi.MessagePhoto newPhoto) {
    if (this.sourceMessageId != sourceMessageId) {
      return false;
    }
    setPhoto(sourceMessageId, newPhoto.photo, isPhotoWebp);
    setRevealOnTap(newPhoto.hasSpoiler);
    return true;
  }

  public boolean updateVideo (long sourceMessageId, TdApi.MessageVideo newVideo) {
    if (this.sourceMessageId != sourceMessageId) {
      return false;
    }
    setVideo(sourceMessageId, newVideo.video);
    setRevealOnTap(newVideo.hasSpoiler);
    return true;
  }

  public boolean updateAnimation (long sourceMessageId, TdApi.MessageAnimation newAnimation) {
    if (this.sourceMessageId != sourceMessageId) {
      return false;
    }
    setAnimation(sourceMessageId, newAnimation.animation);
    setRevealOnTap(newAnimation.hasSpoiler);
    return true;
  }

  private boolean destroyed;

  public void destroy () {
    fileProgress.performDestroy();
    destroyed = true;
  }

  // Video stuff

  private String duration;
  private String durationShort;
  private int durationWidthFull, durationWidthShort, durationAlternativeWidth;
  private int durationWidth;
  private String durationTrimmed, durationAlternative;

  private void trimDuration () {
    int width = cellWidth - Screen.dp(4f) * 3;
    this.durationTrimmed = null;
    this.durationWidth = 0;
    if (width > 0) {
      if (durationWidthFull > width) {
        if (durationShort == null || durationWidthShort > width) {
          return;
        }
        durationTrimmed = durationShort;
        durationWidth = durationWidthShort;
      } else {
        durationTrimmed = duration;
        durationWidth = durationWidthFull;
      }
    }
  }

  private void trimDoubleDuration (String alternative, int alternativeWidth) {
    int width = cellWidth - Screen.dp(8f) * 2 - (Screen.dp(FileProgressComponent.DEFAULT_STREAMING_RADIUS) * 2);
    if (width > 0 && (durationWidthFull > width || durationWidthShort > width)) {
      durationTrimmed = duration;
      durationWidth = durationWidthFull;

      if (durationWidthFull > width && (durationShort != null && durationWidthShort < width)) {
        durationTrimmed = alternative != null ? alternative : durationShort;
        durationWidth = alternative != null ? alternativeWidth : durationWidthShort;
        duration = null;
        return;
      } else if (durationShort == null || durationWidthShort > width) {
        durationTrimmed = null;
        durationWidth = 0;
      }

      duration = null;
      durationShort = null;
    }
  }

  private void updateVideoStreamingState () {
    if (Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE && video != null && isSafeToStream(source)) {
      setVideoStreamingUi(!video.video.remote.isUploadingActive);
    }
  }

  private boolean updateDuration () {
    if (video == null && animation == null) {
      return false;
    }

    String textShort = null;
    String text = "";

    String twLineHeader = null;
    String twLineSubheader = null;
    String twAlternativeHeader = null;
    boolean shouldHaveTwoLines = false;

    if (fileProgress.isFailed()) {
      text = textShort = Lang.getString(R.string.failed);
    } else if (!(fileProgress.isUploadFinished() || (source != null && !source.isSending())) || !fileProgress.isLoaded() || (isVideo() && fileProgress.isDownloaded())) {
      shouldHaveTwoLines = fileProgress.isVideoStreaming();
      textShort = Strings.buildSize(fileProgress.getTotalSize());
      if (fileProgress.isLoading() || !fileProgress.isUploadFinished() || (isVideo() && fileProgress.isDownloaded())) {
        if (fileProgress.isProcessing()) {
          twAlternativeHeader = textShort;
          text = Lang.getString(R.string.ProcessingMedia, textShort);
        } else if (shouldHaveTwoLines || (fileProgress.isLoading() || !fileProgress.isUploadFinished())) {
          long progressSize = fileProgress.getProgressSize();
          long totalSize = fileProgress.getTotalSize();
          if (progressSize <= totalSize) {
            float ratio = (float) ((double) progressSize / (double) totalSize);
            text = Lang.getString(R.string.format_uploadStatus, (int) Math.floor(100f * ratio), textShort);
            twAlternativeHeader = Lang.getString(R.string.format_percentage, (int) Math.floor(100f * ratio));
          } else {
            text = Strings.buildSize(progressSize) + " / " + textShort;
            twAlternativeHeader = Strings.buildSize(progressSize);
          }
        } else {
          textShort = null;
        }
      }
    }

    if (!StringUtils.isEmpty(textShort) && StringUtils.isEmpty(text)) {
      text = textShort;
      textShort = null;
    }

    if (video != null) {
      textShort = Strings.buildDuration(video.duration);

      if (shouldHaveTwoLines) {
        twLineHeader = text;
        twLineSubheader = textShort;
      }

      if (!text.isEmpty()) {
        text = text + ", " + textShort;
      } else if (fileProgress.isVideoStreaming()) {
        text = twLineHeader = Strings.buildSize(fileProgress.getTotalSize());
        twLineSubheader = textShort;
      } else {
        text = textShort;
      }
    }

    if (twLineHeader != null && (!StringUtils.equalsOrBothEmpty(this.duration, twLineHeader) || !StringUtils.equalsOrBothEmpty(this.durationShort, twLineSubheader))) {
      this.duration = twLineHeader;
      this.durationShort = twLineSubheader;
      this.durationAlternative = twAlternativeHeader;
      TextPaint paint = Paints.getRegularTextPaint(13f);
      this.durationWidthFull = (int) U.measureText(twLineHeader, paint);
      this.durationWidthShort = (int) U.measureText(twLineSubheader, paint);
      this.durationAlternativeWidth = (int) U.measureText(twAlternativeHeader, paint);
      this.durationWidth = Math.max(this.durationWidthFull, this.durationWidthShort);
      this.durationTrimmed = this.duration;
      trimDoubleDuration(this.durationAlternative, this.durationAlternativeWidth);
      return true;
    }

    if (twLineHeader == null && !StringUtils.equalsOrBothEmpty(this.duration, text)) {
      this.duration = text;
      this.durationShort = textShort;
      TextPaint paint = Paints.whiteMediumPaint(13f, false, true);
      this.durationWidthFull = (int) U.measureText(text, paint);
      this.durationWidthShort = (int) U.measureText(textShort, paint);
      trimDuration();
      return true;
    }

    return false;
  }

  // Mosaic

  public int getReceiverKey () {
    return fileProgress != null && fileProgress.getFile() != null ? fileProgress.getFile().id : previewSize != null ? previewSize.photo.id : 0;
  }

  private DoubleImageReceiver previewReceiverReference;
  private Receiver targetReceiverReference;

  public DoubleImageReceiver getPreviewReceiverReference () {
    return previewReceiverReference;
  }

  public void setPreviewReceiverReference (DoubleImageReceiver previewReceiverReference) {
    this.previewReceiverReference = previewReceiverReference;
  }

  public Receiver getTargetReceiverReference () {
    return targetReceiverReference;
  }

  public void setTargetReceiverReference (Receiver targetReceiverReference) {
    this.targetReceiverReference = targetReceiverReference;
  }

  // Video resources

  private static int circleRadius () {
    return Screen.dp(19f);
  }

  private static int durationHeight () {
    return Screen.dp(20f);
  }

  private static int durationOffset () {
    return Screen.dp(19f);
  }
}
