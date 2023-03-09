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
 * File created on 08/03/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.Waveform;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageMp3File;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.ImageVideoThumbFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.TGLegacyAudioManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.io.File;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.unit.ByteUnit;

public class FileComponent extends BaseComponent implements FileProgressComponent.SimpleListener, TGLegacyAudioManager.PlayListener, TGPlayerController.TrackListener {
  private @Nullable TdApi.Document doc;
  private @Nullable TdApi.Audio audio;
  private @Nullable TdApi.VoiceNote voice;
  private FileProgressComponent progress;
  private boolean needOpenIn;

  private boolean isPlaying;
  private long playDuration = -1, playPosition = -1;
  private int playSeconds;

  public boolean hasPreview;
  public ImageFile miniThumbnail, preview, fullPreview;
  private boolean mayBeTransparent;

  private @Nullable String title;
  private boolean needFakeTitle;
  private @Nullable String subtitle, subtitleMeasure;
  private @Nullable Waveform waveform;

  private @Nullable Text trimmedTitle, trimmedSubtitle;
  private float sizeWidth;

  private final TGMessage context;
  private final TdApi.Message message;

  // DOCUMENT

  public boolean needOpenIn () {
    return needOpenIn;
  }

  public FileComponent (@NonNull TGMessage context, @NonNull TdApi.Message message, @NonNull TdApi.Document doc) {
    this.context = context;
    this.message = message;
    if (this.needOpenIn = TD.isSupportedMusic(doc)) {
      // TdApi.VoiceNote fakeAudio = new TdApi.VoiceNote(0, null, doc.mimeType, doc.document);
      // setVoice(fakeAudio, null, null);
      TdApi.Audio fakeAudio = TD.newFakeAudio(doc);
      setAudio(fakeAudio, null, null);
      if (fakeAudio.audio.size < ByteUnit.KIB.toBytes(128)) {
        progress.setNoCloud();
      }
    } else {
      setDoc(doc);
    }
  }

  public void setDoc (@NonNull TdApi.Document doc) {
    this.doc = doc;
    this.title = doc.fileName;
    if (this.title == null || this.title.length() == 0) {
      this.title = getTitleFromMime(doc.mimeType);
    }
    this.needFakeTitle = Text.needFakeBold(title);
    initSubtitle();
    if (doc.thumbnail == null && context.isSending() && TGMimeType.isImageMimeType(U.resolveMimeType(doc.document.local.path))) {
      hasPreview = true;

      preview = new ImageFileLocal(doc.document.local.path);
      preview.setProbablyRotated();
      preview.setDecodeSquare(true);
      preview.setSize(Screen.dp(80f, 3f));
      preview.setScaleType(ImageFile.CENTER_CROP);
    } else if (doc.thumbnail != null) {
      hasPreview = true;

      if (doc.minithumbnail != null) {
        miniThumbnail = new ImageFileLocal(doc.minithumbnail);
        miniThumbnail.setScaleType(ImageFile.CENTER_CROP);
        miniThumbnail.setDecodeSquare(true);
      } else {
        miniThumbnail = null;
      }

      preview = TD.toImageFile(context.tdlib(), doc.thumbnail);
      if (preview != null) {
        preview.setDecodeSquare(true);
        preview.setSize(Screen.dp(80f, 3f));
        preview.setScaleType(ImageFile.CENTER_CROP);
        preview.setNoBlur();
      }

      if (TGMimeType.isImageMimeType(doc.mimeType)) {
        createFullPreview();
      }
    }

    this.progress = new FileProgressComponent(context.context(), context.tdlib(), TdlibFilesManager.DOWNLOAD_FLAG_FILE, hasPreview && TGMimeType.isImageMimeType(doc.mimeType), message != null ? message.chatId : context.getChatId(), message != null ? message.id : context.getId());
    this.progress.setBackgroundColorProvider(context);
    this.progress.setSimpleListener(this);
    this.progress.setDocumentMetadata(doc, !hasPreview);
    if (hasPreview) {
      this.progress.setBackgroundColor(0x44000000);
    } else {
      this.progress.setBackgroundColorId(TD.getFileColorId(doc, context.isOutgoingBubble()));
    }
    this.progress.setFile(doc.document, context.getMessage());
    if (viewProvider != null) {
      this.progress.setViewProvider(viewProvider);
    }
  }

  // AUDIO

  public FileComponent (@NonNull TGMessage context, @NonNull TdApi.Message message, @NonNull TdApi.Audio audio, TdApi.Message playPauseFile, TGPlayerController.PlayListBuilder playListBuilder) {
    this.context = context;
    this.message = message;

    setAudio(audio, playPauseFile, playListBuilder);
  }

  public void setAudio (TdApi.Audio audio, TdApi.Message playPauseFile, TGPlayerController.PlayListBuilder playListBuilder) {
    this.audio = audio;
    this.title = TD.getTitle(audio);
    this.needFakeTitle = Text.needFakeBold(title);
    initSubtitle();

    this.hasPreview = audio.albumCoverThumbnail != null && (Config.ALLOW_BOT_COVERS || context.getMessage().viaBotUserId == 0); // preventing shit covers from @music and other bots
    if (hasPreview) {
      if (audio.albumCoverMinithumbnail != null) {
        miniThumbnail = new ImageFileLocal(audio.albumCoverMinithumbnail);
        miniThumbnail.setScaleType(ImageFile.CENTER_CROP);
        miniThumbnail.setDecodeSquare(true);
      } else {
        miniThumbnail = null;
      }

      preview = TD.toImageFile(context.tdlib(), audio.albumCoverThumbnail);
      if (preview != null) {
        preview.setDecodeSquare(true);
        preview.setScaleType(ImageFile.CENTER_CROP);
        preview.setNoBlur();
      }

      if (TD.isFileLoaded(audio.audio)) { // TODO: check why there was albumCoverThumbnail before
        createFullPreview();
      }
    }

    this.progress = new FileProgressComponent(context.context(), context.tdlib(), TdlibFilesManager.DOWNLOAD_FLAG_MUSIC, preview != null, message != null ? message.chatId : context.getChatId(), message != null ? message.id : context.getId());
    this.progress.setBackgroundColorProvider(context);
    this.progress.setSimpleListener(this);
    if (hasPreview) {
      progress.setBackgroundColor(Config.COVER_OVERLAY);
    } else {
      progress.setBackgroundColorId(context.isOutgoingBubble() ? R.id.theme_color_bubbleOut_file : R.id.theme_color_file);
    }
    this.progress.setPlayPauseFile(playPauseFile != null ? playPauseFile : TD.newFakeMessage(audio), playListBuilder);
    if (viewProvider != null) {
      this.progress.setViewProvider(viewProvider);
    }
  }

  // VOICE

  private float unreadFactor;

  public FileComponent (@NonNull TGMessage context, @NonNull TdApi.Message message, @NonNull TdApi.VoiceNote voice, TdApi.Message playPauseFile, TGPlayerController.PlayListBuilder playListBuilder) {
    this.context = context;
    this.message = message;

    setVoice(voice, playPauseFile, playListBuilder);
  }

  public void setVoice (TdApi.VoiceNote voice, TdApi.Message playPauseFile, TGPlayerController.PlayListBuilder playListBuilder) {
    this.voice = voice;

    initSubtitle();
    this.waveform = new Waveform(voice.waveform, Waveform.MODE_BITMAP, context.isOutgoingBubble());
    this.unreadFactor = playPauseFile != context.getMessage() || context.isContentRead() ? 0f : 1f;

    this.progress = new FileProgressComponent(context.context(), context.tdlib(), TdlibFilesManager.DOWNLOAD_FLAG_VOICE, false,message != null ? message.chatId : context.getChatId(), message != null ? message.id : context.getId());
    this.progress.setBackgroundColorProvider(context);
    this.progress.setSimpleListener(this);
    this.progress.setBackgroundColorId(context.isOutgoingBubble() ? R.id.theme_color_bubbleOut_file : R.id.theme_color_file);
    this.progress.setPlayPauseFile(playPauseFile != null ? playPauseFile : TD.newFakeMessage(voice), playListBuilder, this);
    if (this.viewProvider != null) {
      this.progress.setViewProvider(viewProvider);
    }

    if (context.getChatId() == 0) { // Preview mode
      this.progress.setCurrentState(TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED, false);
      this.progress.setDownloadedIconRes(R.drawable.baseline_pause_24);
    }
  }

  public void onContentOpened () {
    if (voice == null) {
      return;
    }
    if (unreadFactor == 1f) {
      if (!context.hasAnyTargetToInvalidate()) {
        unreadFactor = 0f;
      } else {
        FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
          @Override
          public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
            unreadFactor = factor;
            context.invalidate();
          }

          @Override
          public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

          }
        }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.unreadFactor);
        animator.animateTo(0f);
      }
    }
    context.invalidate();
  }

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    setPlaying(state == TGPlayerController.STATE_PLAYING || state == TGPlayerController.STATE_PAUSED);
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long position, long totalDuration, boolean isBuffering) {
    this.playPosition = position;
    this.playDuration = totalDuration;
    int newSeconds = position > 0 ? U.calculateRemainingSeconds(position) : 0;
    boolean secondsChanged = newSeconds != playSeconds;
    playSeconds = newSeconds;
    if (isPlaying) {
      if (secondsChanged) {
        setSubtitle(buildSubtitle());
      }
      context.invalidate();
    }
  }

  private void setPlaying (boolean isPlaying) {
    if (this.isPlaying != isPlaying) {
      this.isPlaying = isPlaying;
      if (!isPlaying) {
        dropSeek();
      }
      setSubtitle(buildSubtitle());
      context.invalidate();
    }
  }

  // COMMON

  public boolean isDocument () {
    return doc != null;
  }

  public boolean isAudio () {
    return audio != null;
  }

  public boolean isVoice () {
    return voice != null;
  }

  public FileProgressComponent getFileProgress () {
    return progress;
  }

  public void buildLayout (int maxWidth) {
    lastMaxWidth = maxWidth;
    if (waveform != null) {
      waveform.layout(Math.min(Screen.dp(420f), Math.min(TGMessage.getEstimatedContentMaxWidth(), maxWidth) - Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS) * 2 - getPreviewOffset() - (int) sizeWidth - Screen.dp(12f)));
    }
    if (title != null || subtitle != null) {
      buildTitles(maxWidth - (getPreviewSize() + getPreviewOffset()));
    }
  }

  private void buildTitles (int maxWidth) {
    trimmedTitle = title != null ? new Text.Builder(title, maxWidth, Paints.getTitleStyleProvider(), context.getTextColorSet()).textFlags(doc != null ? Text.FLAG_ELLIPSIZE_MIDDLE : 0).singleLine().allBold().highlight(context.getHighlightedText(Highlight.Pool.KEY_FILE_TITLE, title)).build() : null;

    float oldWidth = sizeWidth;
    trimSubtitle(maxWidth);
    if (sizeWidth != oldWidth && oldWidth != 0) {
      context.buildBubble(false);
    }
  }

  private @Nullable ViewProvider viewProvider;

  public void setViewProvider (@Nullable ViewProvider provider) {
    this.viewProvider = provider;
    progress.setViewProvider(provider);
  }

  private String buildProgressSubtitle (TdApi.File file, boolean forMeasure) {
    return buildProgressSubtitle(file, forMeasure, (progress != null && progress.isLoading()), context.disableBubble());
  }

  public static String buildProgressSubtitle (TdApi.File file, boolean isLoading, boolean needDownloading) {
    return buildProgressSubtitle(file, false, isLoading, needDownloading);
  }

  private static String buildProgressSubtitle (TdApi.File file, boolean forMeasure, boolean isLoading, boolean needDownloading) {
    if (file != null && file.local != null && file.remote != null && (forMeasure || (file.local.isDownloadingActive) || isLoading) && file.expectedSize != 0) {
      return Lang.getDownloadProgress(forMeasure ? file.expectedSize : file.remote.isUploadingActive ? file.remote.uploadedSize : file.local.downloadedSize, file.expectedSize, needDownloading);
    }
    return null;
  }

  private String buildSubtitle () {
    return buildSubtitle(null, false);
  }

  private void trimSubtitle (int maxWidth) {
    trimmedSubtitle = !StringUtils.isEmpty(subtitle) ? new Text.Builder(subtitle, maxWidth, Paints.getSubtitleStyleProvider(), context.getDecentColorSet()).singleLine().highlight(context.getHighlightedText(Highlight.Pool.KEY_FILE_SUBTITLE, subtitle)).build() : null;
    if (!StringUtils.isEmpty(subtitleMeasure)) {
      Text trimmedBigSubtitle = new Text.Builder(subtitleMeasure, maxWidth, Paints.getSubtitleStyleProvider(), context.getDecentColorSet()).singleLine().highlight(context.getHighlightedText(Highlight.Pool.KEY_FILE_SUBTITLE, subtitleMeasure)).build();
      sizeWidth = Math.max(Math.max(sizeWidth, getSubtitleWidth()), trimmedBigSubtitle.getWidth());
    } else {
      sizeWidth = Math.max(sizeWidth, getSubtitleWidth());
    }
  }

  private void initSubtitle () {
    this.subtitle = buildSubtitle(null, true);
    this.subtitleMeasure = buildSubtitle(subtitle, false);
  }

  private String buildSubtitle (String measure, boolean init) {
    if (measure != null) {
      String progress = buildProgressSubtitle(doc != null ? doc.document : audio != null ? audio.audio : null, true);
      return progress != null && !progress.equals(measure) ? progress : null;
    }
    if (doc != null) {
      final TdApi.File file = doc.document;
      if (file.remote.isUploadingActive && file.expectedSize == 0) {
        return Lang.getString(R.string.ProcessingFile);
      } else {
        String progress = buildProgressSubtitle(file, false);
        if (progress != null) {
          return progress;
        }
        String extension = null;
        if (!StringUtils.isEmpty(doc.fileName)) {
          extension = U.getExtension(doc.fileName);
        }
        if (StringUtils.isEmpty(extension) && !StringUtils.isEmpty(doc.mimeType)) {
          extension = TGMimeType.extensionForMimeType(doc.mimeType);
        }
        if (BuildConfig.THEME_FILE_EXTENSION.equalsIgnoreCase(extension)) {
          if (init && !StringUtils.isEmpty(title) && title.toLowerCase().endsWith("." + BuildConfig.THEME_FILE_EXTENSION)) {
            title = title.substring(0, title.length() - 1 - BuildConfig.THEME_FILE_EXTENSION.length());
          }
          return Lang.getString(R.string.ThemeFile, Strings.buildSize(file.expectedSize));
        }
        if (!StringUtils.isEmpty(extension) && extension.length() <= 7) {
          extension = extension.toUpperCase();
          if (init && !StringUtils.isEmpty(title) && title.toUpperCase().endsWith("." + extension)) {
            title = title.substring(0, title.length() - 1 - extension.length());
          }
          return Lang.getString(R.string.format_fileSizeAndExtension, Strings.buildSize(file.expectedSize), extension);
        }
        return Strings.buildSize(file.expectedSize);
      }
    } else if (voice != null) {
      if (isPlaying) {
        return Strings.buildDuration(playSeconds);
      }
      return Strings.buildDuration(voice.duration);
    } else if (audio != null) {
      TdApi.File file = audio.audio;
      String progress = buildProgressSubtitle(file, false);
      return progress != null ? progress : TD.getSubtitle(audio);
    }
    return null;
  }

  private ImageFile createFullPreview () {
    if (fullPreview == null) {
      if (doc != null) {
        mayBeTransparent = TGMimeType.isTransparentImageMimeType(doc.mimeType);
        fullPreview = createFullPreview(context.tdlib(), doc);
      } else if (audio != null && TD.isFileLoaded(audio.audio) && (audio.albumCoverThumbnail == null || preview == null || Math.max(audio.albumCoverThumbnail.width, audio.albumCoverThumbnail.height) < 90)) {
        fullPreview = new ImageMp3File(audio.audio.local.path);
        fullPreview.setSize(Screen.dp(80f, 2f));
        fullPreview.setScaleType(ImageFile.CENTER_CROP);
      }
    }
    return fullPreview;
  }

  public boolean onLocaleChange () {
    return !progress.isLoading() && setSubtitle(buildSubtitle());
  }

  private int lastPreviewLeft, lastPreviewTop, lastPreviewRight, lastPreviewBottom;

  private int lastMaxWidth;

  private void layoutSize () {
    int maxWidth = lastMaxWidth - getPreviewOffset() - getPreviewSize();
    if (maxWidth <= 0) {
      return;
    }
    final float oldWidth = sizeWidth;
    trimSubtitle(maxWidth);
    if (sizeWidth != oldWidth && oldWidth != 0) {
      context.buildBubble(false);
    }
  }

  public void requestContent (ImageReceiver receiver) {
    if (hasPreview) {
      if ((doc != null && TD.isFileLoaded(doc.document) && TGMimeType.isImageMimeType(doc.mimeType)) ||
        (audio != null && TD.isFileLoaded(audio.audio))) {
        // receiver.setBounds(startX, startY, startX + getPreviewSize(), startY + getPreviewSize());
        receiver.requestFile(createFullPreview());
      } else {
        receiver.requestFile(null);
      }
    } else {
      receiver.requestFile(null);
    }
  }

  private int getTitleWidth () {
    return trimmedTitle != null ? trimmedTitle.getWidth() : 0;
  }

  private int getSubtitleWidth () {
    return trimmedSubtitle != null ? trimmedSubtitle.getWidth() : 0;
  }

  public int getWidth () {
    int contentWidth = getPreviewSize() + getPreviewOffset();
    if (waveform != null) {
      contentWidth += waveform.getWidth() + sizeWidth + Screen.dp(12f);
    } else {
      contentWidth += Math.max(getTitleWidth(), sizeWidth) + Screen.dp(6f);
    }
    return contentWidth;
  }

  public int getHeight () {
    return getDocHeight();
  }

  private boolean loadCaught;
  private ViewParent seekCaught;
  private float seekStartX, desiredSeek;
  private long seekDesireTime;
  private boolean isSeeking;
  private boolean disallowBoundTouch;

  public void setDisallowBoundTouch () {
    disallowBoundTouch = true;
  }

  private void dropSeek () {
    if (seekCaught != null) {
      seekCaught.requestDisallowInterceptTouchEvent(false);
      seekCaught = null;
      isSeeking = false;
      context.invalidate();
    }
  }

  public boolean onTouchEvent (View view, MotionEvent event) {
    int startX = lastStartX;
    int startY = lastStartY;

    if (progress.onTouchEvent(view, event)) {
      return true;
    }
    if (disallowBoundTouch) {
      return false;
    }

    float x = event.getX();
    float y = event.getY();

    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        clearTouch();
        if (waveform != null && isPlaying && playDuration > 0 && playPosition >= 0) {
          int radius = Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS);
          int waveformLeft = startX + radius * 2 + getPreviewOffset();
          int cy = startY + radius;
          if (y >= cy - radius && y <= cy + radius && x >= waveformLeft && x <= waveformLeft + waveform.getWidth()) {
            seekStartX = startX;
            seekCaught = view.getParent();
            if (seekCaught != null) {
              seekCaught.requestDisallowInterceptTouchEvent(true);
              return true;
            }
            return false;
          }
        }
        if (title == null && subtitle == null) {
          return false;
        }
        float bound = progress.getRadius() * 1.6f;
        float cx = startX + progress.getRadius();
        float cy = startY + progress.getRadius();

        if (x >= cx - bound && x <= startX + getPreviewSize() + getPreviewOffset() + Math.max(getTitleWidth(), sizeWidth) + bound && y >= cy - bound && y <= cy + bound) {
          loadCaught = true;
        }
        return loadCaught;
      }
      case MotionEvent.ACTION_MOVE: {
        if (!(loadCaught || seekCaught != null))
          return false;
        if (waveform != null) {
          if (seekCaught != null && !isSeeking && Math.abs(seekStartX - x) >= Screen.getTouchSlop()) {
            isSeeking = true;
            seekStartX = x;
          }
          if (isSeeking) {
            int waveformLeft = startX + Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS) * 2 + getPreviewOffset();
            int waveformWidth = waveform.getWidth();
            float seek = MathUtils.clamp((x - waveformLeft) / (float) waveformWidth);
            boolean needInvalidate = desiredSeek == -1 || (int) ((float) waveformWidth * seek) != (int) ((float) waveformWidth * desiredSeek);
            desiredSeek = seek;
            if (needInvalidate) {
              context.invalidate();
            }
          }
          return seekCaught != null;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (!(loadCaught || seekCaught != null))
          return false;
        if (isSeeking && desiredSeek != -1 && isPlaying && playDuration > 0) {
          seekDesireTime = SystemClock.uptimeMillis();
          TdlibManager.instance().audio().seekTo((long) ((double) playDuration * desiredSeek), playDuration);
        }
        dropSeek();
        if (loadCaught && progress.performClick(view)) {
          context.performClickSoundFeedback();
        }

        clearTouch();
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (!(loadCaught || seekCaught != null))
          return false;
        dropSeek();
        clearTouch();
        break;
      }
    }

    return loadCaught || seekCaught != null;
  }

  public void clearTouch () {
    loadCaught = false;
  }

  // private static final boolean USE_ROUND_SMOOTHING = false;

  public int getContentRadius (int defaultValue) {
    return hasPreview ? getPreviewSize() / 2 : defaultValue;
  }

  public void requestPreview (DoubleImageReceiver receiver) {
    if (hasPreview) {
      receiver.requestFile(miniThumbnail, preview);
    } else {
      receiver.clear();
    }
  }

  private int lastStartX, lastStartY;

  public MediaViewThumbLocation getMediaThumbLocation (View view, int viewTop, int viewBottom, int top) {
    if (hasPreview) {
      MediaViewThumbLocation thumbLocation = new MediaViewThumbLocation();
      thumbLocation.setNoBounce();

      final int previewSize = getPreviewSize();

      int actualTop = lastStartY + viewTop;
      int actualBottom = (view.getMeasuredHeight() - (lastStartY + previewSize)) + viewBottom;

      thumbLocation.set(lastStartX, lastStartY + top, lastStartX + previewSize, lastStartY + previewSize + top);
      thumbLocation.setClip(0, actualTop < 0 ? -actualTop : 0, 0, actualBottom < 0 ? -actualBottom : 0);
      thumbLocation.setRoundings(previewSize / 2);

      return thumbLocation;
    }
    return null;
  }

  public <T extends View & DrawableProvider> void draw (T view, Canvas c, int startX, int startY, Receiver preview, Receiver receiver, @ColorInt int backgroundColor, int contentReplaceColor, float alpha, float checkFactor) {
    this.lastStartX = startX;
    this.lastStartY = startY;

    final int previewSize = getPreviewSize();
    preview.setBounds(startX, startY, startX + previewSize, startY + previewSize);
    if (checkFactor != 0f) {
      c.save();
      float scale = 1f - .1f * checkFactor;
      c.scale(scale, scale, preview.centerX(), preview.centerY());
    }

    if (hasPreview) {
      /*int padding = USE_ROUND_SMOOTHING && path != null ? Screen.dp(1f) : 0;
      if (path != null) {
        layoutPath(startX - padding, startY - padding, startX + previewSize + padding, startY + previewSize + padding);
        ViewSupport.clipPath(c, path);
      }*/
      preview.setPaintAlpha(alpha * preview.getAlpha());
      receiver.setPaintAlpha(alpha * receiver.getAlpha());
      if (mayBeTransparent) {
        c.drawCircle(startX + previewSize / 2f, startY + previewSize / 2f, previewSize / 2f, Paints.fillingPaint(ColorUtils.alphaColor(alpha, Color.WHITE)));
      }
      DrawAlgorithms.drawReceiver(c, preview, receiver, true, true, startX, startY, startX + previewSize, startY + previewSize);
      receiver.restorePaintAlpha();
      preview.restorePaintAlpha();
      /*ViewSupport.restoreClipPath(c, path);
      if (USE_ROUND_SMOOTHING && path != null && backgroundColor != 0) {
        c.drawCircle(startX + previewSize / 2, startY + previewSize / 2, previewSize / 2 + padding, Paints.strokeBigPaint(backgroundColor));
      }*/
    }

    progress.setRequestedAlpha(alpha);
    progress.setBounds(startX, startY, startX + previewSize, startY + previewSize);
    progress.draw(view, c);

    if (checkFactor != 0f) {
      c.restore();
    }

    DrawAlgorithms.drawSimplestCheckBox(c, preview, checkFactor, contentReplaceColor);

    if (waveform == null) {
      int textLeft = startX + previewSize + getPreviewOffset();
      if (trimmedTitle != null) {
        trimmedTitle.draw(c, textLeft, textLeft + trimmedTitle.getWidth(), 0, startY + Screen.dp(8f), null, alpha);
      }
      if (trimmedSubtitle != null) {
        trimmedSubtitle.draw(c, textLeft, textLeft + trimmedSubtitle.getWidth(), 0, startY + Screen.dp(29f), null, alpha);
      }
    } else {
      // TODO alpha parameter support for voice messages
      float seek;
      if (context.getChatId() != 0) {
        if (isPlaying) {
          if (desiredSeek != -1 && (isSeeking || (seekDesireTime != 0 && SystemClock.uptimeMillis() - seekDesireTime < 100))) {
            seek = desiredSeek;
          } else if (playDuration <= 0 || playPosition <= 0) {
            seek = 0f;
          } else {
            seek = (float) ((double) playPosition / (double) playDuration);
          }
        } else {
          seek = context.isContentRead() ? 0f : 1f;
        }
      } else { // Chat Preview
        seek = .68f;
      }
      int waveformLeft = startX + Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS) * 2 + getPreviewOffset();
      int cy = startY + Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS);
      waveform.draw(c, seek, waveformLeft, cy);
      boolean align = context.isOutgoingBubble();
      if (unreadFactor != 0f) {
        int cx = startX + Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS);
        int fileRadius = Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS);
        float innerRadius = Screen.dp(3f);
        float outerRadius = innerRadius + Screen.dp(2f);
        double radians = Math.toRadians(45f);
        float x = cx + (float) ((double) fileRadius * Math.sin(radians)) + Screen.dp(22f);
        float y = cy + (float) ((double) fileRadius * Math.cos(radians));

        // c.drawCircle(x, y, outerRadius * unreadFactor, Paints.fillingPaint(context.getContentReplaceColor()));
        c.drawCircle(x, y, innerRadius * unreadFactor, Paints.fillingPaint(ColorUtils.alphaColor(unreadFactor, Theme.getColor(align ? R.id.theme_color_bubbleOut_waveformActive : R.id.theme_color_waveformActive))));
      }
      if (trimmedSubtitle != null) {
        int textX = startX + previewSize + getPreviewOffset() + waveform.getWidth() + Screen.dp(12f);
        trimmedSubtitle.draw(c, textX, textX + trimmedSubtitle.getWidth(), 0, startY + Screen.dp(18f), null, alpha);
      }
    }
  }

  private boolean setSubtitle (@Nullable String subtitle) {
    String measure = buildSubtitle(subtitle, false);
    if (!StringUtils.equalsOrBothEmpty(this.subtitle, subtitle) || !StringUtils.equalsOrBothEmpty(this.subtitleMeasure, measure)) {
      this.subtitle = subtitle;
      this.subtitleMeasure = measure;
      layoutSize();
      context.invalidate();
      return true;
    }
    return false;
  }

  public int getLastLineWidth () {
    return waveform != null ? TGMessage.BOTTOM_LINE_KEEP_WIDTH : (int) sizeWidth + getPreviewSize() + getPreviewOffset();
  }

  public void open () {
    if (doc != null) {
      progress.openFile(this.context.controller(), () ->
        UI.openFile(this.context.controller(), StringUtils.isEmpty(doc.fileName) ? null : doc.fileName, new File(doc.document.local.path), doc.mimeType, TD.getViewCount(this.context.getMessage().interactionInfo)));
      this.context.readContent();
    }
  }

  @Override
  public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    if (doc != null) {
      this.context.tdlib().ui().readCustomLanguage(this.context.controller(), doc, langPack ->
        this.context.controller().showOptions(Lang.getString(R.string.LanguageWarning), new int[] {R.id.btn_messageApplyLocalization, R.id.btn_open}, new String[] {Lang.getString(R.string.LanguageInstall), Lang.getString(R.string.Open)}, null, new int[] {R.drawable.baseline_language_24, R.drawable.baseline_open_in_browser_24}, (itemView, id) -> {
          switch (id) {
            case R.id.btn_messageApplyLocalization:
              this.context.tdlib().ui().showLanguageInstallPrompt(this.context.controller(), langPack, this.context.getMessage());
              break;
            case R.id.btn_open:
              open();
              break;
          }
          this.context.readContent();
          return true;
        }), this::open);
      return true;
    }
    return false;
  }

  @Override
  public void onPlayPause (int fileId, boolean isPlaying, boolean isUpdate) { }

  @Override
  public boolean needPlayProgress (int fileId) {
    return isVoice() || isAudio();
  }

  @Override
  public void onPlayProgress (int fileId, float progress, boolean isUpdate) {
    context.invalidate();
  }

  private boolean needProgress () {
    return waveform == null; // && (isAudio() || !context.useBubble());
  }

  @Override
  public void onStateChanged (TdApi.File file, @TdlibFilesManager.FileDownloadState int state) {
    if (needProgress()) {
      setSubtitle(buildSubtitle());
    }
  }

  @Override
  public void onProgress (TdApi.File file, float progress) {
    if (needProgress()) {
      setSubtitle(buildSubtitle());
    }
  }

  // STATIC

  private static int getDocHeight () {
    return Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS) * 2;
  }

  private static int getPreviewSize () {
    return Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS) * 2;
  }

  private static int getPreviewOffset () {
    return Screen.dp(12f);
  }

  public static ImageFile createFullPreview (Tdlib tdlib, TdApi.Document doc) {
    if (TGMimeType.isImageMimeType(doc.mimeType)) {
      ImageFile file = new ImageFile(tdlib, doc.document);
      file.setProbablyRotated();
      return createFullPreview(file, doc.mimeType);
    } else if (TGMimeType.isVideoMimeType(doc.mimeType)) {
      return createFullPreview(new ImageVideoThumbFile(tdlib, doc.document), doc.mimeType);
    }
    return null;
  }

  public static ImageFile createFullPreview (ImageFile preview, String mimeType) {
    preview.setSize(Screen.dp(80f, 3f));
    preview.setDecodeSquare(true);
    /*if (!"image/png".equals(mimeType)) {
      preview.setProbablyRotated();
    }*/
    preview.setNoBlur();
    preview.setScaleType(ImageFile.CENTER_CROP);
    return preview;
  }

  public static String getTitleFromMime (String mimeType) {
    String ext = TGMimeType.extensionForMimeType(mimeType);
    if (ext != null) {
      return ext.toUpperCase() + " " + Lang.getString(R.string.File);
    }
    return mimeType;
  }

  @Nullable
  @Override
  public TdApi.File getFile () {
    return getFileProgress().getFile();
  }

  @Override
  public void performDestroy () {
    if (progress != null) {
      progress.performDestroy();
    }
  }
}
