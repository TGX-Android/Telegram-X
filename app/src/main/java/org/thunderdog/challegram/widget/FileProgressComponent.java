/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 24/11/2016
 */
package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.filegen.VideoGen;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.MediaDownloadType;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DrawableProvider;

import java.io.File;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class FileProgressComponent implements TdlibFilesManager.FileListener, FactorAnimator.Target, TGPlayerController.TrackListener {
  public static final float DEFAULT_RADIUS = 28f;
  public static final float DEFAULT_STREAMING_RADIUS = 18f;
  public static final float DEFAULT_SMALL_STREAMING_RADIUS = 12f;
  public static final float DEFAULT_FILE_RADIUS = 25f;

  public static final int STREAMING_UI_MODE_LARGE = 0;
  public static final int STREAMING_UI_MODE_SMALL = 1;
  public static final int STREAMING_UI_MODE_EXTRA_SMALL = 2;

  private static final int INVALIDATE_CONTENT_RECEIVER = 0;
  private static final int CHANGE_CURRENT_STATE = 1;
  private static final int SET_PROGRESS = 2;
  private static final Handler handler = new Handler(Looper.getMainLooper()) {
    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case INVALIDATE_CONTENT_RECEIVER: {
          ((FileProgressComponent) msg.obj).invalidateContent();
          break;
        }
        case CHANGE_CURRENT_STATE: {
          ((FileProgressComponent) msg.obj).setCurrentState(msg.arg1, true);
          break;
        }
        case SET_PROGRESS: {
          ((FileProgressComponent) msg.obj).setProgress(Float.intBitsToFloat(msg.arg1), Float.intBitsToFloat(msg.arg2));
          break;
        }
      }
    }
  };

  public static final @DrawableRes int PLAY_ICON = R.drawable.baseline_play_arrow_36_white;

  public interface SimpleListener {
    boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId);
    void onStateChanged (TdApi.File file, @TdlibFilesManager.FileDownloadState int state);
    void onProgress (TdApi.File file, float progress);
  }

  private static final int FLAG_THEME = 1;

  private final BaseActivity context;
  private final Tdlib tdlib;
  private final @MediaDownloadType int fileType;
  private @Nullable TdApi.File file;
  private @Nullable String mimeType;
  private boolean invalidateContentReceiver;
  private boolean useGenerationProgress;
  private boolean isDownloaded;
  private int flags;

  private int backgroundColor;
  private int downloadedIconRes;
  private int pausedIconRes;

  private long chatId;
  private long messageId;
  private boolean isLocal;

  private boolean ignoreLoaderClicks;
  private boolean noCloud;

  private final Rect vsDownloadRect = new Rect();
  private final RectF vsDownloadClickRect = new RectF();
  private final RectF vsClipRect = new RectF();
  private float vsTranslateDx;
  private boolean isVideoStreaming;
  private boolean isVideoStreamingOffsetNeeded;
  private boolean isVideoStreamingProgressHidden;
  private boolean isVideoStreamingCloudNeeded;
  private int videoStreamingUiMode;
  private int vsPadding;
  private BoolAnimator vsOnDownloadedAnimator;
  private Drawable vsUniqueIconRef;
  private int vsUniqueIconRefId;

  private float requestedAlpha;

  private @Nullable SimpleListener listener;
  private @Nullable FallbackFileProvider fallbackFileProvider;

  private TdApi.Message playPauseFile;
  private TGPlayerController.PlayListBuilder playListBuilder;
  private TGPlayerController.TrackListener additionalTrackListener;

  private boolean hideDownloadedIcon;

  public FileProgressComponent (BaseActivity context, Tdlib tdlib, @MediaDownloadType int fileType, boolean invalidateContentReceiver, long chatId, long messageId) {
    this.context = context;
    this.tdlib = tdlib;
    this.fileType = fileType;
    this.pausedIconRes = R.drawable.baseline_file_download_24;
    this.invalidateContentReceiver = invalidateContentReceiver;
    this.backgroundColor = 0x66000000; // 40% of black
    this.alpha = 1f;
    this.requestedAlpha = 1f;
    this.chatId = chatId;
    this.messageId = messageId;
  }

  public void setNoCloud () {
    this.noCloud = true;
  }

  public void setFallbackFileProvider (@Nullable FallbackFileProvider fallbackFileProvider) {
    this.fallbackFileProvider = fallbackFileProvider;
  }

  public void setIgnoreLoaderClicks (boolean ignoreLoaderClicks) {
    this.ignoreLoaderClicks = ignoreLoaderClicks;
  }

  public void setVideoStreaming (boolean isVideoStreaming) {
    boolean isUpdated = this.isVideoStreaming != isVideoStreaming;
    this.isVideoStreaming = isVideoStreaming;
    this.playPausePath = new Path();
    setIsPlaying(false, false);
    if (isUpdated) DrawAlgorithms.buildPlayPause(playPausePath, Screen.dp(18f), -1f, playPauseDrawFactor = this.playPauseFactor);
  }

  public void vsLayout () {
    checkProgressStyles();
    layoutProgress();
  }

  public void setVideoStreamingProgressHidden (boolean isVideoStreamingProgressHidden) {
    this.isVideoStreamingProgressHidden = isVideoStreamingProgressHidden;
  }

  public void setVideoStreamingClippingRect (RectF clipRect) {
    vsClipRect.set(clipRect);
  }

  public void setVideoStreamingOptions (boolean topOffsetNeeded, boolean cloudNeeded, int uiMode, RectF videoStreamingRect, BoolAnimator onDownloadedAnimator) {
    int prevUiMode = videoStreamingUiMode;
    boolean needProgressLayout = isVideoStreamingOffsetNeeded != topOffsetNeeded || !vsDownloadClickRect.equals(videoStreamingRect);

    isVideoStreamingOffsetNeeded = topOffsetNeeded;
    isVideoStreamingCloudNeeded = cloudNeeded;
    vsOnDownloadedAnimator = onDownloadedAnimator;
    videoStreamingUiMode = uiMode;
    vsDownloadClickRect.set(videoStreamingRect);
    updateVsRect();

    if (prevUiMode != uiMode) {
      checkProgressStyles();
      DrawAlgorithms.buildPlayPause(playPausePath, Screen.dp(uiMode == STREAMING_UI_MODE_SMALL ? 15f : 18f), -1f, playPauseDrawFactor = this.playPauseFactor);
      if (currentState == TdlibFilesManager.STATE_IN_PROGRESS) {
        setIcon(getCancelIcon(), false);
      }
    }

    if (needProgressLayout) {
      layoutProgress();
    }
  }

  public boolean isVideoStreaming () {
    return isVideoStreaming; //&& !isLoaded();
  }

  private boolean isVideoStreamingSmallUi () {
    return videoStreamingUiMode != STREAMING_UI_MODE_LARGE;
  }

  public TdApi.File getFile () {
    return file;
  }

  public BaseActivity context () {
    return context;
  }

  public long getMessageId () {
    return messageId;
  }

  public void setRequestedAlpha (float alpha) {
    if (this.requestedAlpha != alpha) {
      this.requestedAlpha = alpha;
      invalidate();
    }
  }

  public float getRequestedAlpha () {
    return requestedAlpha;
  }

  public boolean isLoading () {
    return currentState == TdlibFilesManager.STATE_IN_PROGRESS;
  }

  public boolean isLoaded () {
    return currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED;
  }

  public boolean isDownloaded () {
    return currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED || (file != null && TD.isFileLoaded(file));
  }

  public boolean isUploadFinished () {
    return TD.isFileUploaded(file);
  }

  public boolean isFailed () {
    return currentState == TdlibFilesManager.STATE_FAILED;
  }

  public int getTotalSize () {
    return file != null ? file.expectedSize : 0;
  }

  public int getProgressSize () {
    return file != null ? file.remote.isUploadingActive ? file.remote.uploadedSize : file.local.downloadedSize : 0;
  }

  public boolean isInGenerationProgress () {
    return file != null && currentProgress == 0f && useGenerationProgress && file.local.isDownloadingActive;
  }

  public boolean isProcessing () {
    return file != null && !file.local.isDownloadingCompleted && !file.remote.isUploadingCompleted && file.remote.uploadedSize == 0;
  }

  public boolean isUploading () {
    return file != null && !file.remote.isUploadingCompleted;
  }

  public void setIsLocal () {
    this.isLocal = true;
    // setCurrentState(TGDownloadManager.STATE_DOWNLOADED_OR_UPLOADED, shouldAnimate());
  }

  public void setBackgroundColor (int color) {
    this.backgroundColorIsId = false;
    this.backgroundColor = color;
  }

  private boolean backgroundColorIsId;

  public void setBackgroundColorId (@ThemeColorId int colorId) {
    this.backgroundColorIsId = true;
    this.backgroundColor = colorId;
  }

  public void updateMessageId (long oldMessageId, long newMessageId, boolean success) {
    if (this.messageId == oldMessageId) {
      this.messageId = newMessageId;
      this.isSendingMessage = false;
      setCurrentState(success ? (TD.isFileLoadedAndExists(file) ? TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED : TdlibFilesManager.STATE_PAUSED) : TdlibFilesManager.STATE_FAILED, shouldAnimate());
    }
    if (this.playPauseFile != null && this.playPauseFile.id == oldMessageId) {
      this.playPauseFile.id = newMessageId;
    }
  }

  public void replaceFile (TdApi.File file, @Nullable TdApi.Message message) {
    setFile(file, message);
  }

  public void setPausedIconRes (@DrawableRes int icon) {
    if (this.pausedIconRes != icon) {
      this.pausedIconRes = icon;
      if (currentState == TdlibFilesManager.STATE_PAUSED) {
        setIcon(icon, false);
        setAlpha(icon != 0 ? 1f : 0f, false);
      }
    }
  }

  public void setSimpleListener (@Nullable SimpleListener listener) {
    this.listener = listener;
  }

  public void setDownloadedIconRes (@DrawableRes int icon) {
    setDownloadedIconRes(icon, shouldAnimate());
  }

  public void setDownloadedIconRes (TdApi.Document doc) {
    boolean isTheme = Config.isThemeDoc(doc);
    setDownloadedIconRes(isTheme ? R.drawable.baseline_palette_24 : R.drawable.baseline_insert_drive_file_24);
    flags = BitwiseUtils.setFlag(flags, FLAG_THEME, isTheme);
  }

  public void setHideDownloadedIcon (boolean hide) {
    if (this.hideDownloadedIcon != hide) {
      this.hideDownloadedIcon = hide;
      invalidate();
    }
  }

  public void setDownloadedIconRes (@DrawableRes int icon, boolean animated) {
    if (this.downloadedIconRes != icon) {
      this.downloadedIconRes = icon;
      if (currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
        setIcon(icon, animated);
        setAlpha(icon != 0 ? 1f : 0f, animated);
      }
    }
  }

  public void setMimeType (@Nullable String mimeType) {
    this.mimeType = mimeType;
  }

  public void setFile (@Nullable TdApi.File file) {
    setFile(file, null);
  }

  private boolean isSendingMessage;

  public void setFile (@Nullable TdApi.File file, @Nullable TdApi.Message message) {
    if (this.file != null && !isLocal) {
      tdlib.files().unsubscribe(this.file.id, this);
    }
    this.file = file;
    if (file != null && file.local != null) {
      this.isDownloaded = file.local.isDownloadingCompleted;
      this.useGenerationProgress = !file.local.isDownloadingCompleted && !file.remote.isUploadingCompleted && message != null && message.content.getConstructor() != TdApi.MessagePhoto.CONSTRUCTOR;
    } else {
      this.isDownloaded = this.useGenerationProgress = false;
    }
    if (message != null && tdlib.messageSending(message)) {
      isSendingMessage = true;
    }
    if (file != null) {
      if (isSendingMessage) {
        setCurrentState(TdlibFilesManager.STATE_IN_PROGRESS, shouldAnimate());
        if (isUploadFinished()) {
          setProgress(1f, 1f);
        }
      } else if (message != null && TD.isFailed(message)) {
        setCurrentState(TdlibFilesManager.STATE_FAILED, shouldAnimate());
      } else if (TD.isFileLoading(file)) {
        setCurrentState(TdlibFilesManager.STATE_IN_PROGRESS, shouldAnimate());
      } else {
        setCurrentState(TD.isFileLoaded(file) ? TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED : TdlibFilesManager.STATE_PAUSED, shouldAnimate());
      }
    } else {
      setCurrentState(TdlibFilesManager.STATE_PAUSED, shouldAnimate());
    }
    if (file != null && !isLocal) {
      if (currentState == TdlibFilesManager.STATE_IN_PROGRESS && file.local != null && file.local.isDownloadingActive && !tdlib.files().hasPendingOperation(file.id)) {
        tdlib.files().downloadFile(file);
      }
      tdlib.files().subscribe(file, this);
    }
  }

  // Modern play/pause API

  private FactorAnimator playPauseAnimator;
  private float playPauseFactor;
  private Path playPausePath;
  private float playPauseDrawFactor;
  private static final int ANIMATOR_PLAY_PAUSE = 3;

  private boolean isPlaying;

  public void setIsPlaying (boolean isPlaying, boolean allowAnimation) {
    if (this.isPlaying != isPlaying) {
      this.isPlaying = isPlaying;
      boolean animated = allowAnimation && shouldAnimate();
      setPlayPauseFactor(isPlaying ? 1f : 0f, animated);
    }
  }

  private void setPlayPauseFactor (float factor, boolean animate) {
    if (animate) {
      if (playPauseAnimator == null) {
        playPauseAnimator = new FactorAnimator(ANIMATOR_PLAY_PAUSE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 138l, this.playPauseFactor);
      }
      playPauseAnimator.animateTo(factor);
    } else {
      if (playPauseAnimator != null) {
        playPauseAnimator.forceFactor(factor);
      }
      setPlayPauseFactor(factor);
    }
  }

  private void setPlayPauseFactor (float factor) {
    if (this.playPauseFactor != factor) {
      this.playPauseFactor = factor;
      layoutProgress(); // FIXME remove
      invalidate();
    }
  }

  public void setPlayPauseFile (@NonNull TdApi.Message playPauseFile, @Nullable TGPlayerController.PlayListBuilder playListBuilder) {
    setPlayPauseFile(playPauseFile, playListBuilder, null);
  }

  public void setPlayPauseFile (@NonNull TdApi.Message playPauseFile, @Nullable TGPlayerController.PlayListBuilder playListBuilder, @Nullable TGPlayerController.TrackListener additionalTrackListener) {
    if (!Config.USE_NEW_PLAYER) {
      throw new RuntimeException("Unsupported API");
    }
    if (file != null) {
      throw new IllegalStateException("setPlayPauseObject called after setFile");
    }
    setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
    setFile(TD.getFile(playPauseFile), playPauseFile);
    this.playPauseFile = playPauseFile;
    this.playListBuilder = playListBuilder;
    this.additionalTrackListener = additionalTrackListener;
    this.playPausePath = new Path();
    checkProgressStyles();
    TdlibManager.instance().player().addTrackListener(tdlib, playPauseFile, this);
    DrawAlgorithms.buildPlayPause(playPausePath, Screen.dp(13f), -1f, playPauseDrawFactor = this.playPauseFactor);
  }

  public TdApi.Message getPlayPauseFile () {
    return playPauseFile;
  }

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    if (this.tdlib == tdlib && this.file != null && this.file.id == fileId) {
      setIsPlaying(state == TGPlayerController.STATE_PLAYING, true);
      if (additionalTrackListener != null) {
        additionalTrackListener.onTrackStateChanged(tdlib, chatId, messageId, fileId, state);
      }
    }
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    if (this.tdlib == tdlib && this.file != null && this.file.id == fileId) {
      if (additionalTrackListener != null) {
        additionalTrackListener.onTrackPlayProgress(tdlib, chatId, messageId, fileId, progress, playPosition, playDuration, isBuffering);
      }
    }
  }

  // File opening

  private boolean openFile () {
    if (mimeType == null)
      return false;
    ViewController<?> c = UI.getCurrentStackItem(context);
    if (c == null)
      return false;
    Runnable after = () -> U.openFile(c, U.getFileName(file.local.path), new File(file.local.path), mimeType, 0);
    return openFile(c, after);
  }

  public boolean openFile (ViewController<?> c, Runnable defaultOpen) {
    if (file != null && fileType == TdlibFilesManager.DOWNLOAD_FLAG_FILE) {
      if (c != null && c.tdlib() == tdlib) {
        if ((flags & FLAG_THEME) != 0) {
          c.tdlib().ui().readCustomTheme(c, file, null, defaultOpen);
        } else {
          defaultOpen.run();
        }
      }
      return true;
    }
    return false;
  }

  // Layout

  private int left, top, right, bottom;

  public void setBounds (int left, int top, int right, int bottom) {
    if (this.left != left || this.top != top || this.right != right || this.bottom != bottom) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      layoutProgress();
    }
  }

  private void checkProgressStyles () {
    if (progress != null) {
      boolean isCloud = (Config.useCloudPlayback(playPauseFile) && !noCloud) || (isVideoStreaming() && isVideoStreamingCloudNeeded);
      progress.setUseLargerPaint((isVideoStreaming() && isVideoStreamingSmallUi()) ? Screen.dp(1.5f) : !isSendingMessage && isCloud ? Screen.dp(isTrack ? 2f : 1.5f) : Screen.dp(3f));
    }
  }

  private void layoutProgress () {
    if (progress != null) {
      if ((playPauseFile != null && Config.useCloudPlayback(playPauseFile) && !noCloud) || (isVideoStreaming() && isVideoStreamingCloudNeeded)) {
        int centerX = centerX();
        int centerY = centerY();

        int x = centerX;
        int y = centerY;
        int originRadius;

        if (isTrack) {
          int toRadius = Screen.dp(11f);
          originRadius = Screen.dp(7f);
          originRadius += (toRadius - originRadius) * progressFactor;
          progress.setRadius(originRadius);
        } else if (isVideoStreaming()) {
          int radius = Screen.dp(20f); // 28f full
          x += radius;
          y += radius;
          originRadius = Screen.dp(5f);
          progress.setRadius(originRadius - Screen.dp(2f));
        }  else {
          int radius = (right - left) / 2;
          double radians = Math.toRadians(45f);

          x += (int) ((double) radius * Math.sin(radians));
          y += (int) ((double) radius * Math.cos(radians));

          originRadius = Screen.dp(5f);

          progress.setRadius(originRadius - Screen.dp(2f));
        }
        progress.setBounds(x - originRadius, y - originRadius, x + originRadius, y + originRadius);
      } else if (isVideoStreaming()) {
        updateVsRect();
        progress.setRadius(getRadius() - Screen.dp(4f));
        progress.setBounds(vsDownloadRect.left, vsDownloadRect.top, vsDownloadRect.right, vsDownloadRect.bottom);
      } else {
        progress.setRadius(getRadius() - Screen.dp(4f));
        progress.setBounds(left, top, right, bottom);
      }
    }
  }

  public boolean isInside (float x, float y, int padding) {
    return x >= left - padding && x <= right + padding && y >= top - padding && y <= bottom + padding;
  }

  public void toRect (Rect rect) {
    int centerX = centerX();
    int centerY = centerY();
    int radius = getRadius();
    rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
  }

  public int centerX () {
    return (left + right) >> 1;
  }

  public int centerY () {
    return (top + bottom) >> 1;
  }

  public int getRadius () {
    if (isVideoStreaming()) {
      return Math.min(Screen.dp(videoStreamingUiMode == STREAMING_UI_MODE_LARGE ? DEFAULT_STREAMING_RADIUS : DEFAULT_SMALL_STREAMING_RADIUS), Math.min(right - left, bottom - top) / 2);
    }

    return Math.min(Screen.dp(fileType == TdlibFilesManager.DOWNLOAD_FLAG_FILE || fileType == TdlibFilesManager.DOWNLOAD_FLAG_MUSIC || fileType == TdlibFilesManager.DOWNLOAD_FLAG_VOICE ? DEFAULT_FILE_RADIUS : DEFAULT_RADIUS), Math.min(right - left, bottom - top) / 2);
  }

  // Current view

  private @Nullable ViewProvider viewProvider;

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    if (this.progress != null) {
      this.progress.setViewProvider(viewProvider);
    }
  }

  /*private @Nullable View currentView;

  public void setCurrentView (@Nullable View currentView) {
    if (this.currentView != currentView) {
      this.currentView = currentView;
      if (progress != null) {
        progress.setCurrentView(currentView);
      }
    }
  }*/

  // Touch events

  private boolean isTouchCaught;
  private int startX, startY;

  public boolean onTouchEvent (View view, MotionEvent e) {
    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        this.startX = (int) x;
        this.startY = (int) y;
        return isTouchCaught = x >= left && x <= right && y >= top && y <= bottom && (!isTrack || currentState != TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED);
      }
      case MotionEvent.ACTION_MOVE: {
        if (isTouchCaught && Math.max(Math.abs(startX - x), Math.abs(startY - y)) > Screen.getTouchSlop()) {
          isTouchCaught = false;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isTouchCaught) {
          isTouchCaught = false;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (isTouchCaught) {
          if (performClick(view) && viewProvider != null) {
            viewProvider.performClickSoundFeedback();
          }
          return true;
        }
        break;
      }
    }

    return isTouchCaught /*|| (x >= left && x <= right && y >= top && y <= bottom)*/;
  }

  public boolean performLongPress (View view) {
    // TODO 3D touch
    isTouchCaught = false;

    return false;
  }

  public boolean performClick (View view) {
    return performClick(view, false);
  }

  public boolean performClick (View view, float x, float y) {
    this.startX = (int) x;
    this.startY = (int) y;
    return performClick(view, false);
  }

  public boolean performClick (View view, boolean ignoreListener) {
    if (ignoreLoaderClicks && !(isVideoStreaming() && vsDownloadClickRect.contains(startX, startY))) {
      return !ignoreListener && listener != null && listener.onClick(this, view, file, messageId);
    }
    if (!isTrack && file != null && playPauseFile != null && ((Config.useCloudPlayback(playPauseFile) && !noCloud) || currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED)) {
      /*if (currentState == TGDownloadManager.STATE_PAUSED) {
        TGDownloadManager.instance().downloadFile(file);
      }*/
      if (file.remote.isUploadingCompleted || file.id == -1) {
        TdlibManager.instance().player().playPauseMessage(tdlib, playPauseFile, playListBuilder);
      }
      return true;
    }
    switch (currentState) {
      case TdlibFilesManager.STATE_PAUSED: {
        if (file != null) {
          tdlib.files().downloadFile(file);
          return true;
        }
        break;
      }
      case TdlibFilesManager.STATE_IN_PROGRESS: {
        if (file != null) {
          if (file.remote.isUploadingActive || isSendingMessage) {
            tdlib.deleteMessagesIfOk(chatId, new long[] {messageId}, true);
          } else {
            tdlib.files().cancelDownloadOrUploadFile(file.id, false, true);
          }
          return true;
        }
        break;
      }
      case TdlibFilesManager.STATE_FAILED: {
        // TODO retry send message
        break;
      }
      case TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED: {
        if (!ignoreListener && listener != null && listener.onClick(this, view, file, messageId)) {
          return true;
        }
        if (openFile()) {
          return true;
        }
        break;
      }
    }
    return false;
  }

  // Factor animation

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BITMAP: {
        setBitmapChangeFactor(factor);
        break;
      }
      case ANIMATOR_ALPHA: {
        setAlpha(factor);
        break;
      }
      case ANIMATOR_PROGRESS: {
        setProgressFactor(factor);
        break;
      }
      case ANIMATOR_PLAY_PAUSE: {
        setPlayPauseFactor(factor);
        break;
      }
      case ANIMATOR_CLOUD_HIDE: {
        setCloudReverseHideFactor(1f - factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BITMAP: {
        if (finalFactor == 1f) {
          if (bitmapAnimator != null) {
            bitmapAnimator.forceFactor(0f);
          }
          bitmapChangeFactor = 0f;
        }
        break;
      }
    }
  }


  // Icon animation

  private int currentBitmapRes;
  // private Bitmap currentBitmap;

  private void setBitmapChangeFactor (float factor) {
    if (this.bitmapChangeFactor != factor) {
      this.bitmapChangeFactor = factor;
      if (pendingBitmapRes != 0 && factor >= .5f) {
        currentBitmapRes = pendingBitmapRes;
        pendingBitmapRes = 0;
      }
    }
    invalidate();
  }

  private static final int ANIMATOR_BITMAP = 0;
  // private Bitmap pendingBitmap;
  private int pendingBitmapRes;
  private float bitmapChangeFactor;
  private FactorAnimator bitmapAnimator;

  private static final long CHANGE_DURATION = 210l;
  private static final long ALPHA_DURATION = 240l;
  private static final long PROGRESS_DURATION = 210l;

  private static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator(.72f);

  private boolean ignoreAnimations;

  public void setIgnoreAnimations (boolean ignoreAnimations) {
    this.ignoreAnimations = ignoreAnimations;
  }
  
  private boolean shouldAnimate () {
    return viewProvider != null && viewProvider.hasAnyTargetToInvalidate() && !ignoreAnimations;
  }

  private void setIcon (int iconRes, boolean animated) {
    if (animated && shouldAnimate() && currentBitmapRes != iconRes && alpha == 1f) {
      this.pendingBitmapRes = iconRes;

      if (bitmapAnimator == null) {
        bitmapAnimator = new FactorAnimator(ANIMATOR_BITMAP, this, DECELERATE_INTERPOLATOR, CHANGE_DURATION, this.bitmapChangeFactor);
      } else if (bitmapAnimator.getFactor() > .5f) {
        bitmapAnimator.forceFactor(0f);
        this.bitmapChangeFactor = 0f;
      }
      bitmapAnimator.animateTo(1f);
    } else {
      if (bitmapAnimator != null) {
        bitmapAnimator.forceFactor(0f);
      }
      pendingBitmapRes = 0;
      bitmapChangeFactor = 0f;
      this.currentBitmapRes = iconRes;
      invalidate();
    }
  }

  private static final int ANIMATOR_ALPHA = 1;
  private float alpha;
  private FactorAnimator alphaAnimator;

  private void setAlpha (float alpha, boolean animated) {
    if (animated && shouldAnimate() && (this.alpha != alpha || alphaAnimator != null)) {
      if (alphaAnimator == null) {
        alphaAnimator = new FactorAnimator(ANIMATOR_ALPHA, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ALPHA_DURATION, this.alpha);
      }
      alphaAnimator.animateTo(alpha);
    } else {
      if (alphaAnimator != null) {
        alphaAnimator.forceFactor(alpha);
      }
      setAlpha(alpha);
    }
  }

  private void setAlpha (float alpha) {
    if (this.alpha != alpha) {
      this.alpha = alpha;
      invalidate();
    }
  }

  private static final int ANIMATOR_PROGRESS = 2;
  private float progressFactor;
  private FactorAnimator progressAnimator;
  private boolean isInProgress;
  private @Nullable ProgressComponent progress;

  private void setProgressFactor (float factor) {
    if (progressFactor != factor) {
      progressFactor = factor;
      if (isTrack) {
        layoutProgress();
      }
      invalidate();
    }
  }

  private int getProgressColor () {
    if (isTrack) {
      return ColorUtils.alphaColor(progressFactor * alpha, Theme.getColor(backgroundColor));
    } else {
      return ((int) (255f * progressFactor * alpha) << 24) | 0xffffff;
    }
  }

  private void setInProgress (boolean inProgress, boolean animated) {
    if (this.isInProgress != inProgress) {
      this.isInProgress = inProgress;

      if (progress == null) {
        progress = new ProgressComponent(context, Screen.dp(22f));
        progress.setProgress(currentProgress, false);
        progress.setIsPrecise();
        progress.forceColor(getProgressColor());
        checkProgressStyles();
        layoutProgress();
        if (useStupidInvalidate) {
          progress.setUseStupidInvalidate();
        }
        progress.setViewProvider(viewProvider);
      }

      if (animated && alpha > 0f) {
        if (progressAnimator == null) {
          progressAnimator = new FactorAnimator(ANIMATOR_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, PROGRESS_DURATION, progressFactor);
        }
        progressAnimator.animateTo(inProgress ? 1f : 0f);
      } else {
        if (progressAnimator != null) {
          progressAnimator.forceFactor(inProgress ? 1f : 0f);
        }
        setProgressFactor(inProgress ? 1f : 0f);
      }
    }
  }

  public void notifyInvalidateTargetsChanged () {
    if (progress != null) {
      progress.restartAnimationIfNeeded();
    }
  }

  private float currentProgress, visualProgress;

  public void setProgress (float progress, float visualProgress) {
    if (this.currentProgress != progress || this.visualProgress != visualProgress) {
      if (Log.isEnabled(Log.TAG_TDLIB_FILES) && Log.checkLogLevel(Log.LEVEL_INFO)) {
        Log.i(Log.TAG_TDLIB_FILES, "setProgress id=%d done=%d->%d%% visual=%d->%d%%", file != null ? file.id : 0, (int) (currentProgress * 100f), (int) (progress * 100f), (int) (this.visualProgress * 100f), (int) (visualProgress * 100f));
      }
      this.currentProgress = progress;
      this.visualProgress = visualProgress;
      if (this.progress != null) {
        this.progress.setProgress(visualProgress, shouldAnimate() && alpha > 0f);
      }
      if (listener != null && currentState == TdlibFilesManager.STATE_IN_PROGRESS) {
        listener.onProgress(file, progress);
      }
    }
  }

  public interface FallbackFileProvider {
    TdApi.File provideFallbackFile (TdApi.File file);
  }

  private @TdlibFilesManager.FileDownloadState int currentState;

  public void setCurrentState (@TdlibFilesManager.FileDownloadState int state, boolean animated) {
    boolean needResetFile = false;
    if (isVideoStreaming() && vsOnDownloadedAnimator != null && (state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED || state == TdlibFilesManager.STATE_PAUSED)) {
      vsOnDownloadedAnimator.setValue(state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED, animated && !isVideoStreamingCloudNeeded);
    }
    if (this.currentState == TdlibFilesManager.STATE_IN_PROGRESS && state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
      setProgress(1f, 1f);
    } else if (
        (this.currentState != TdlibFilesManager.STATE_IN_PROGRESS && state == TdlibFilesManager.STATE_IN_PROGRESS) ||
        (this.currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED && state == TdlibFilesManager.STATE_PAUSED)
      ) {
      float progress = TD.getFileProgress(file);
      float fullProgress = TD.getFileProgress(file, true);
      setProgress(progress, progress);
      if (fullProgress != progress) {
        setProgress(fullProgress, fullProgress);
      }
      needResetFile = state == TdlibFilesManager.STATE_PAUSED;
    }
    int oldState = currentState;
    this.currentState = state;
    if (this.listener != null) {
      this.listener.onStateChanged(file, state);
    }
    animated = animated && shouldAnimate();
    switch (state) {
      case TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED: {
        completeCloud(animated);
        if (downloadedIconRes != 0) {
          setIcon(downloadedIconRes, animated);
          setInProgress(false, animated);
        } else {
          setAlpha(0f, animated);
        }
        if (animated) {
          invalidateContent();
        }
        break;
      }
      case TdlibFilesManager.STATE_FAILED: {
        completeCloud(false);
        setInProgress(false, animated);
        setIcon(R.drawable.baseline_replay_24, animated);
        // TODO setIcon();
        break;
      }
      case TdlibFilesManager.STATE_IN_PROGRESS: {
        completeCloud(false);
        setIcon(getCancelIcon(), animated);
        setInProgress(true, animated);
        setAlpha(1f, animated);
        break;
      }
      case TdlibFilesManager.STATE_PAUSED: {
        completeCloud(false);
        if (oldState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
          invalidateContent();
        }
        setIcon(pausedIconRes, animated);
        setInProgress(false, animated);
        setAlpha(1f, animated);
        break;
      }
    }
    if (needResetFile && fallbackFileProvider != null) {
      TdApi.File newFile = fallbackFileProvider.provideFallbackFile(file);
      if (newFile != null) {
        setFile(newFile);
      }
    }
  }

  private int getCancelIcon() {
    if (isVideoStreaming()) {
      if (isVideoStreamingSmallUi()) {
        return R.drawable.deproko_baseline_close_10;
      } else {
        return R.drawable.deproko_baseline_close_18;
      }
    } else {
      return R.drawable.deproko_baseline_close_24;
    }
  }

  // Drawing

  private boolean useStupidInvalidate;

  public void setUseStupidInvalidate () {
    useStupidInvalidate = true;
    if (progress != null) {
      progress.setUseStupidInvalidate();
    }
  }

  public void invalidate () {
    if (viewProvider != null) {
      if (useStupidInvalidate) {
        viewProvider.invalidate();
      } else {
        int radius = getRadius();
        int cx = centerX();
        int cy = centerY();
        viewProvider.invalidate(cx - radius / 2, cy - radius / 2, cx + radius / 2, cy + radius / 2);
      }
    }
  }

  public void invalidateContent () {
    if (invalidateContentReceiver) {
      if ((viewProvider == null || !viewProvider.invalidateContent())) {
        Log.i("Warning: FileProgressComponent.invalidateContent ignored");
      }
    }
  }

  private static final float MIN_SCALE = .65f;

  public float getBackgroundAlpha () {
    return this.alpha * requestedAlpha;
  }

  private @Nullable TGMessage backgroundColorProvider;

  public void setBackgroundColorProvider (@NonNull TGMessage msg) {
    this.backgroundColorProvider = msg;
  }

  private float calculateCloudHideFactor () {
    if (currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
      return 1f * (1f - cloudReverseHideFactor);
    } else {
      return 0f;
    }
  }

  private float calculateCloudDoneFactor () {
    if (currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
      if (bitmapAnimator != null && bitmapAnimator.isAnimating()) {
        return bitmapChangeFactor <= .5f ? 0f : (bitmapChangeFactor - .5f) / .5f;
      }
      return 1f;
    }
    return 0f;
  }

  private float cloudReverseHideFactor;
  private FactorAnimator cloudHideAnimator;
  private static final int ANIMATOR_CLOUD_HIDE = 4;

  private void setCloudReverseHideFactor (float factor) {
    if (this.cloudReverseHideFactor != factor) {
      this.cloudReverseHideFactor = factor;
      invalidate();
    }
  }

  private void completeCloud (boolean animated) {
    if (playPauseFile == null) {
      return;
    }
    if (animated) {
      if (cloudHideAnimator == null) {
        cloudHideAnimator = new FactorAnimator(ANIMATOR_CLOUD_HIDE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
        cloudHideAnimator.setStartDelay(2000l);
      } else {
        cloudHideAnimator.forceFactor(0f);
      }
      cloudReverseHideFactor = 1f;
      cloudHideAnimator.animateTo(1f);
    } else {
      if (cloudHideAnimator != null) {
        cloudHideAnimator.forceFactor(0f);
      }
      cloudReverseHideFactor = 0f;
    }
  }

  private float calculateCloudFactor () {
    if (currentState == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
      return 1f - cloudReverseHideFactor;
    } else {
      return progressFactor;
    }
  }

  private static Path triangleSmall, triangleBig;

  private void drawCloudState (Canvas c, float alpha) {
    float hideFactor = calculateCloudHideFactor();
    if (hideFactor == 1f) {
      return;
    }

    // alpha *= (1f - hideFactor);

    // int radius = (right - left) / 2;
    int centerX = centerX();
    int centerY = centerY();

    // float cloudFactor = calculateCloudFactor();

    int x = centerX;
    int y = centerY;

    int originRadius;
    int fillingPadding;

    if (isTrack) {
      int toRadius = Screen.dp(12f);
      originRadius = Screen.dp(7f);
      originRadius += (toRadius - originRadius) * progressFactor;
      fillingPadding = 0;
    } else if (isVideoStreaming()) {
      int radius = Screen.dp(20f); // 28f full
      x += radius;
      y += radius;
      originRadius = Screen.dp(5f);
      fillingPadding = (int) (Screen.dp(1.5f) * alpha * (1f - hideFactor));
    } else {
      double radians = Math.toRadians(45f);
      int radius = (right - left) / 2;

      x += (int) ((double) radius * Math.sin(radians));
      y += (int) ((double) radius * Math.cos(radians));

      originRadius = Screen.dp(5f);
      fillingPadding = (int) (Screen.dp(1.5f) * alpha * (1f - hideFactor));
    }

    int cloudColor;
    if (backgroundColorIsId) {
      cloudColor = Theme.getColor(backgroundColor);
    } else {
      float colorFactor = playPauseFactor;
      int activeColor = colorFactor != 0f ? Theme.getColor(R.id.theme_color_file) : 0;
      int inactiveColor = colorFactor != 1f ? (backgroundColorProvider != null ? backgroundColorProvider.getDecentIconColor() : Theme.getColor(R.id.theme_color_iconLight)) : 0;
      cloudColor = ColorUtils.fromToArgb(inactiveColor, activeColor, colorFactor);
    }
    int fillingColor;
    if (backgroundColorProvider != null) {
      fillingColor = backgroundColorProvider.getContentReplaceColor();
    } else {
      fillingColor = ColorUtils.alphaColor(alpha, Theme.fillingColor());
    }

    if (!isTrack || progressFactor <= 1f) {
      float circleFactor = 1f - (alpha * (1f - hideFactor)) * (isTrack ? 1f - progressFactor : 1f);
      int radius = (int) (originRadius * (1f - hideFactor));
      DrawAlgorithms.drawCloud(c, x, y, radius, circleFactor, ColorUtils.alphaColor(alpha, cloudColor), fillingPadding, fillingColor, isVideoStreaming());

      if (isTrack) {
        int color = Theme.fillingColor();
        c.drawCircle(x, y, (radius + Screen.dp(1f)) * progressFactor, Paints.fillingPaint(color));
        // c.drawCircle(x, y, (radius - Screen.dp(1f)), Paints.strokeBigPaint(U.alphaColor(progressFactor, color)));
      }
    }

    if (isTrack) {
      int size = (int) (Screen.dp(6.5f) * (progressFactor));
      int left = x - size / 2;
      int top = y - size / 2;
      c.drawLine(left, top, left + size, top + size, Paints.getProgressPaint(cloudColor, Screen.dp(2f)));
      c.drawLine(left, top + size, left + size, top, Paints.getProgressPaint(cloudColor, Screen.dp(2f)));
    }

    alpha *= (1f - progressFactor) * (1f - hideFactor);

    if (alpha == 0f) {
      return;
    }

    switch (currentState) {
      case TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED: {
        float factor = calculateCloudDoneFactor();
        if (factor == 0f) {
          break;
        }

        int size, h1max, w2max;

        if (isTrack) {
          size = Screen.dp(2f);
          h1max = Screen.dp(2.5f) + size;
          w2max = Screen.dp(5.5f);
        } else {
          size = Screen.dp(2f);
          h1max = Screen.dp(2f) + size;
          w2max = Screen.dp(4.5f);
        }

        int totalDistance = (int) ((h1max + w2max) * factor);

        int h = totalDistance < h1max ? totalDistance : h1max;
        int w = totalDistance >= h1max ? totalDistance - h1max : 0;

        int cx = x; int cy = y;

        int progressColor = isTrack ? ColorUtils.alphaColor(alpha, Theme.fillingColor()) : ((int) (255f * alpha) << 24) | 0xffffff;

        if (totalDistance > 0) {
          c.save();
          int tx = -Screen.dp(2f);
          int ty = isTrack ? Screen.dp(2.25f) : Screen.dp(2.15f);
          float scale = 1f - hideFactor;
          if (scale != 1f) {
            c.scale(scale, scale, x, y);
          }
          c.translate(tx, ty);
          c.rotate(-45f, cx + tx, cy + ty);
          c.drawRect(cx, cy, cx + size, cy + h, Paints.fillingPaint(progressColor));
          if (w > 0) {
            c.drawRect(cx + size, cy + h - size, cx + size + w, cy + h, Paints.fillingPaint(progressColor));
          }
          c.restore();
        }

        break;
      }
      default: {
        int color;
        Path path;

        int length, cy;
        if (isTrack) {
          color = ColorUtils.alphaColor(alpha, Theme.fillingColor());
          if (triangleBig == null) {
            int width = Screen.dp(9f);
            int height = Screen.dp(4f);
            triangleBig = new Path();
            triangleBig.setFillType(Path.FillType.EVEN_ODD);
            triangleBig.moveTo(-width / 2, 0);
            triangleBig.lineTo(width / 2, 0);
            triangleBig.lineTo(0, height);
            triangleBig.close();
          }
          path = triangleBig;

          length = Screen.dp(3.5f);
          cy = y - (length / 2 + Screen.dp(1f)) + Screen.dp(.2f);
        } else {
          color = ColorUtils.color((int) (255f * alpha), 0xffffffff);
          if (triangleSmall == null) {
            int width = Screen.dp(7f);
            int height = Screen.dp(3f);
            triangleSmall = new Path();
            triangleSmall.setFillType(Path.FillType.EVEN_ODD);
            triangleSmall.moveTo(-width / 2, 0);
            triangleSmall.lineTo(width / 2, 0);
            triangleSmall.lineTo(0, height);
            triangleSmall.close();
          }
          path = triangleSmall;

          length = Screen.dp(2.5f);
          cy = y - (length / 2 + Screen.dp(1f)) + Screen.dp(.2f);
        }

        c.drawRect(x - length / 2, cy, x + length / 2, cy + length, Paints.fillingPaint(color));

        c.save();
        c.translate(x, cy + length);
        c.drawPath(path, Paints.fillingPaint(color));
        c.restore();
        break;
      }
    }

  }

  private boolean isTrack, isTrackCurrent;

  public void setIsTrackCurrent (boolean isCurrent) {
    if (this.isTrackCurrent != isCurrent) {
      this.isTrackCurrent = isCurrent;
      if (isTrackCurrent) {
        setPlayPauseFactor(isPlaying ? 1f : 0f, false);
      }
    }
  }

  public void setIsTrack (boolean detachListener) {
    isTrack = true;
    checkProgressStyles();
    if (detachListener) {
      TdlibManager.instance().player().removeTrackListener(tdlib, playPauseFile, this);
    }
  }

  public boolean isTrack () {
    return isTrack;
  }

  public void drawPlayPause (Canvas c, int cx, int cy, float alpha, boolean applyNewFactor) {
    float drawFactor = playPauseDrawFactor;
    float factor;
    if (applyNewFactor || (playPauseFactor > 0f && playPauseFactor < 1f)) {
      factor = this.playPauseDrawFactor = this.playPauseFactor;
    } else if (drawFactor == -1f) {
      factor = this.playPauseDrawFactor = 1f;
    } else {
      factor = drawFactor;
    }
    DrawAlgorithms.drawPlayPause(c, cx, cy, Screen.dp(13f), playPausePath, drawFactor, factor, progressFactor, ColorUtils.alphaColor(alpha, 0xffffffff));
  }

  public <T extends View & DrawableProvider> void drawClipped (T view, final Canvas c, RectF clipRect, float translateDx) {
    vsTranslateDx = translateDx;
    vsClipRect.set(clipRect);
    draw(view, c);
  }

  public void setPaddingCompensation (int selectionPadding) {
    vsPadding = selectionPadding;
  }

  public <T extends View & DrawableProvider> void draw (T view, final Canvas c) {
    final boolean cloudPlayback = Config.useCloudPlayback(playPauseFile) && !noCloud;
    final float alpha = this.alpha * requestedAlpha;
    final boolean drawContent = file != null && alpha != 0f && !isTrack;
    boolean isCanvasAltered = false;
    if (drawContent) {
      int cx = centerX();
      int cy = centerY();

      if (isVideoStreaming()) {
        if (vsDownloadRect.width() == 0) {
          updateVsRect();
        }

        cx = vsDownloadRect.centerX();
        cy = vsDownloadRect.centerY();
      }

      final int fillingColor;
      if (alpha == 1f) {
        fillingColor = backgroundColorIsId ? Theme.getColor(backgroundColor) : backgroundColor;
      } else {
        fillingColor = ColorUtils.alphaColor(alpha, backgroundColorIsId ? Theme.getColor(backgroundColor) : backgroundColor);
      }

      if (isVideoStreaming()) {
        c.drawCircle(centerX(), centerY(), Screen.dp(videoStreamingUiMode == STREAMING_UI_MODE_LARGE ? DEFAULT_RADIUS : DEFAULT_STREAMING_RADIUS), Paints.fillingPaint(fillingColor));
        drawPlayPause(c, centerX(), centerY(), alpha, true);

        if (!vsClipRect.isEmpty() && !isVideoStreamingCloudNeeded) {
          c.save();
          c.clipRect(vsClipRect);
          c.translate(vsTranslateDx, 0);
          isCanvasAltered = true;
        }

        if (isVideoStreamingCloudNeeded) {
          drawCloudState(c, alpha);
        }
      } else {
        c.drawCircle(cx, cy, getRadius(), Paints.fillingPaint(fillingColor));
      }

      if (cloudPlayback) {
        drawPlayPause(c, cx, cy, alpha, true);
      } else if (currentBitmapRes != 0 && (currentBitmapRes != downloadedIconRes || !hideDownloadedIcon) && !(isVideoStreaming() && isVideoStreamingCloudNeeded)) {
        boolean ignoreScale = isVideoStreaming() && !isVideoStreamingSmallUi() && vsOnDownloadedAnimator != null && vsOnDownloadedAnimator.isAnimating();
        Paint bitmapPaint = Paints.getPorterDuffPaint(0xffffffff);

        final float initScaleFactor = bitmapChangeFactor <= .5f ? (bitmapChangeFactor / .5f) : (1f - (bitmapChangeFactor - .5f) / .5f);
        final float scaleFactor = (ignoreScale) ? 0f : initScaleFactor;
        final float bitmapAlpha = alpha * (1f - initScaleFactor);

        if (bitmapAlpha != 1f) {
          bitmapPaint.setAlpha((int) (255f * bitmapAlpha));
        }
        int saveCount = -1;

        final boolean needRestore = scaleFactor != 0f;
        if (needRestore) {
          saveCount = Views.save(c);
          final float scale = MIN_SCALE + (1f - MIN_SCALE) * (1f - scaleFactor);
          c.scale(scale, scale, cx, cy);
        }

        if (currentBitmapRes == FileProgressComponent.PLAY_ICON && playPauseFile != null) {
          DrawAlgorithms.drawPlayPause(c, cx, cy, Screen.dp(13f), playPausePath, playPauseDrawFactor, playPauseDrawFactor = this.playPauseFactor, progressFactor, ColorUtils.alphaColor(bitmapAlpha, 0xffffffff));
        } else {
          Drawable drawable;

          // need unique icon ref because of mutate() workaround
          if (bitmapAlpha != 1f) {
            if (vsUniqueIconRef != null && vsUniqueIconRefId == currentBitmapRes) {
              drawable = vsUniqueIconRef;
            } else {
              vsUniqueIconRefId = currentBitmapRes;
              drawable = vsUniqueIconRef = Drawables.get(currentBitmapRes);
            }
          } else {
            // clear ref
            vsUniqueIconRefId = 0;
            vsUniqueIconRef = null;
            drawable = view.getSparseDrawable(currentBitmapRes, 0);
          }

          Drawables.draw(c, drawable, cx - drawable.getMinimumWidth() / 2f, cy - drawable.getMinimumHeight() / 2f, bitmapPaint);
        }

        if (bitmapAlpha != 1f) {
          bitmapPaint.setAlpha(255);
        }

        if (needRestore) {
          Views.restore(c, saveCount);
        }
      }
    }
    if (cloudPlayback) {
      drawCloudState(c, alpha); 
    }
    if (progress != null && !isVideoStreamingProgressHidden) {
      progress.setAlpha(alpha);
      progress.forceColor(getProgressColor());
      progress.draw(c);
    }
    if (isCanvasAltered) {
      c.restore();
    }
  }

  // Video streaming UI stuff

  private void updateVsRect () {
    int startX = left + Screen.dp(videoStreamingUiMode == STREAMING_UI_MODE_EXTRA_SMALL ? 4f : videoStreamingUiMode == STREAMING_UI_MODE_SMALL ? 8f : 14f) - vsPadding;
    int startY = top + Screen.dp(videoStreamingUiMode == STREAMING_UI_MODE_EXTRA_SMALL ? 3f : videoStreamingUiMode == STREAMING_UI_MODE_SMALL ? 6f : 12f) + (isVideoStreamingOffsetNeeded ? Screen.dp(16f) : 0) - vsPadding;
    vsDownloadRect.set(
      startX, startY, startX + (getRadius() * 2), startY + (getRadius() * 2)
    );
  }

  // Download stuff

  public boolean downloadAutomatically (long chatId) {
    if (file != null) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat != null) {
        return downloadAutomatically(chat.type);
      }
    }
    return false;
  }

  public void downloadIfNeeded () {
    if (file != null && (currentState == TdlibFilesManager.STATE_PAUSED || !tdlib.files().hasPendingOperation(file.id))) {
      tdlib.files().downloadFile(file);
    }
  }

  public boolean downloadAutomatically () {
    return downloadAutomatically(null);
  }

  public boolean downloadAutomatically (@Nullable TdApi.ChatType chatType) {
    return file != null && tdlib.files().downloadAutomatically(file, chatType, fileType, currentState == TdlibFilesManager.STATE_PAUSED);
  }

  public void pauseDownload (boolean weak) {
    if (file != null && currentState == TdlibFilesManager.STATE_IN_PROGRESS) {
      tdlib.files().cancelDownloadOrUploadFile(file.id, weak, false);
    }
  }

  public void destroy () {
    if (file != null) {
      tdlib.files().unsubscribe(file.id, this);
    }
    if (playPauseFile != null) {
      TdlibManager.instance().player().removeTrackListener(tdlib, playPauseFile, this);
    }
    /*setCurrentView(null);*/
  }

  private static final float GENERATION_PROGRESS_PART = .35f;

  private float getVisualProgress (float progress) {
    if (useGenerationProgress) {
      if (progress > 0f) {
        return GENERATION_PROGRESS_PART + progress * (1f - GENERATION_PROGRESS_PART);
      } else if (!StringUtils.isEmpty(file.local.path)) {
        VideoGen.Entry entry = tdlib.filegen().getVideoProgress(file.local.path);
        float localProgress = entry != null ? (float) entry.getProgress() : 1f;
        return localProgress == 1f ? GENERATION_PROGRESS_PART + progress * (1f - GENERATION_PROGRESS_PART) : localProgress * GENERATION_PROGRESS_PART;
      } else {
        return 0f;
      }
    }
    return progress;
  }

  @Override
  public void onFileLoadProgress (TdApi.File file) {
    boolean triggerGenerationFinish = !isDownloaded && file.local.isDownloadingCompleted;
    Td.copyTo(file, this.file);
    final float progress = TD.getFileProgress(file);
    float visualProgress = getVisualProgress(progress);
    handler.sendMessage(Message.obtain(handler, SET_PROGRESS, Float.floatToIntBits(progress), Float.floatToIntBits(visualProgress), this));
    if (triggerGenerationFinish) {
      isDownloaded = true;
      if (invalidateContentReceiver) {
        handler.sendMessage(Message.obtain(handler, INVALIDATE_CONTENT_RECEIVER, this));
      }
    }
  }

  @Override
  public void onFileLoadStateChanged (Tdlib tdlib, int fileId, @TdlibFilesManager.FileDownloadState int state, @Nullable TdApi.File downloadedFile) {
    boolean isUI = Looper.getMainLooper() == Looper.myLooper();
    if (state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
      if (downloadedFile != null) {
        if (file != null) {
          Td.copyTo(downloadedFile, file);
        }
        if (invalidateContentReceiver) {
          handler.sendMessage(Message.obtain(handler, INVALIDATE_CONTENT_RECEIVER, this));
        }
      }
    } else if (downloadedFile != null && file != null) {
      Td.copyTo(downloadedFile, file);
    }
    if (isUI) {
      setCurrentState(state, shouldAnimate());
    } else {
      handler.sendMessage(Message.obtain(handler, CHANGE_CURRENT_STATE, state, 0, this));
    }
  }

  /*@Override
  public void onFileGenerationProgress (int fileId, int ready, int size) {
    if (file != null && file.id == fileId) {
      file.local.downloadedSize = ready; // .localSize = ready;
      file.size = size;
      // if (useGenerationProgress) {
        onFileLoadProgress(file);
      // }
    }
  }*/

  @Override
  public void onFileGenerationFinished (@NonNull TdApi.File file) {
    if (this.file != null) {
      Td.copyTo(file, this.file);
      if (invalidateContentReceiver) {
        handler.sendMessage(Message.obtain(handler, INVALIDATE_CONTENT_RECEIVER, this));
      }
    }
  }
}
