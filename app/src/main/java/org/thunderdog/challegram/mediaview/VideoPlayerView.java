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
 * File created on 12/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.video.VideoSize;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.io.File;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.CancellableRunnable;

public class VideoPlayerView implements Player.Listener, CallManager.CurrentCallListener, Runnable {
  private static class SeekHandler extends Handler {
    @Override
    public void handleMessage (Message msg) {
      ((VideoPlayerView) msg.obj).updateTimes();
    }
  }
  private final Context context;
  private final SeekHandler seekHandler;
  // private final TrackSelector selector;
  // private final LoadControl loadControl;
  private @Nullable ExoPlayer player;
  private View targetView;

  private boolean noProgressUpdates;
  private boolean isLooping, forceLooping;
  private boolean inForceTouchMode;

  private final ViewGroup parentView;
  private final int addIndex;

  public VideoPlayerView (Context context, ViewGroup parentView, int addIndex) {
    this.context = context;
    this.parentView = parentView;
    this.addIndex = addIndex;
    this.seekHandler = new SeekHandler();
  }

  public View prepareTextureView (boolean allowSurface) {
    if (targetView == null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && allowSurface && false) {
        this.targetView = new SurfaceView(context);
      } else {
        this.targetView = new TextureView(context);
      }
      this.targetView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    return targetView;
  }

  public View getTargetView () {
    return targetView;
  }

  public void setInForceTouch () {
    inForceTouchMode = true;
  }

  public void forceLooping (boolean force) {
    this.forceLooping = force;
  }

  private void setLooping (boolean isLooping) {
    this.isLooping = isLooping;
  }

  public void setNoProgressUpdates (boolean noProgressUpdates) {
    this.noProgressUpdates = noProgressUpdates;
  }

  // ExoPlayer stuff

  private @Nullable MediaItem currentItem;

  public void setVideo (@Nullable MediaItem mediaItem) {
    if (!Config.VIDEO_PLAYER_AVAILABLE) {
      if (mediaItem != null && mediaItem.isLoaded()) {
        U.openFile(UI.getContext(context).navigation().getCurrentStackItem(), mediaItem.getSourceVideo());
      }
      return;
    }
    prepareTextureView(mediaItem == null || mediaItem.getSourceGalleryFile() == null);
    setMuted(mediaItem != null && mediaItem.needMute());
    setLooping(forceLooping || (mediaItem != null && mediaItem.isSecret()));
    setNoProgressUpdates(mediaItem != null && mediaItem.isSecret());
    if (mediaItem != null) {
      TdlibManager.instance().calls().addCurrentCallListener(this);
    } else {
      TdlibManager.instance().calls().removeCurrentCallListener(this);
    }
    if (this.currentItem == mediaItem) {
      return;
    }
    this.currentItem = mediaItem;
    if (isDetached) {
      return;
    }
    if (mediaItem == null) {
      if (player != null) {
        player.setVideoTextureView(null);
        Views.removeFromParent(targetView);
        player.release();
        player = null;
      }
      setLongStreamingAlertHandler(false);
      setPlaying(false);
      return;
    }

    // String path;
    MediaSource source;

    boolean forcePlay = false;

    switch (mediaItem.getType()) {
      case MediaItem.TYPE_VIDEO: {
        source = U.newMediaSource(mediaItem.tdlib().id(), mediaItem.getTargetFile());
        break;
      }
      case MediaItem.TYPE_GIF: {
        source = new LoopingMediaSource(U.newMediaSource(mediaItem.tdlib().id(), mediaItem.getTargetFile()));
        forcePlay = true;
        break;
      }
      case MediaItem.TYPE_GALLERY_VIDEO: {
        String path = mediaItem.getSourceGalleryFile().getFilePath();
        source = U.newMediaSource(new File(path));
        break;
      }
      default: {
        setVideo(null);
        return;
      }
    }

    if (player == null) {
      this.player = U.newExoPlayer(context, preferExtensions);
      this.player.addListener(this);
      checkMuted();
      if (targetView instanceof SurfaceView) {
        this.player.setVideoSurfaceView((SurfaceView) targetView);
      } else {
        this.player.setVideoTextureView((TextureView) targetView);
      }

      // FIXME: Must not be just a timeout alert.
      //        It should be shown once a significant part of file is downloaded and
      //        estimated remaining download time (when can be determined) is over 2 seconds.
      //        Uncomment line below once implemented properly.
      // setLongStreamingAlertHandler(true);
      this.player.addAnalyticsListener(new AnalyticsListener() {
        @Override
        public void onRenderedFirstFrame (EventTime eventTime, Object output, long renderTimeMs) {
          setLongStreamingAlertHandler(false);
        }
      });

      this.player.setPlayWhenReady(isPlaying);
      if (targetView.getParent() == null) {
        this.parentView.addView(targetView, addIndex);
      }
    }
    this.originalSource = source;
    this.trimStartUs = this.trimEndUs = -1;
    if (mediaItem.needTrim()) {
      setTrim(mediaItem.getTrimStartUs(), mediaItem.getTrimEndUs());
    } else {
      setDataSource(source);
    }
    if (seekToSavedPosition) {
      seekToSavedPosition = false;
      player.seekTo(savedPosition);
    }
    if (forcePlay) {
      setPlaying(true);
    }
  }

  private final static int LONG_STREAMING_PRELOAD_DURATION = 5 * 1000;
  private CancellableRunnable longStreamingAlertRunnable;
  private AlertDialog pendingLsAlert;

  private void setLongStreamingAlertHandler (boolean state) {
    if (state) {
      if (longStreamingAlertRunnable == null) {
        longStreamingAlertRunnable = new CancellableRunnable() {
          @Override
          public void act () {
            if (UI.getContext(context).currentTdlib().isConnected()) { // we don't want to show this without internet connection
              showLongStreamingAlert();
            }
          }
        };

        UI.post(longStreamingAlertRunnable, LONG_STREAMING_PRELOAD_DURATION);
      }
    } else if (longStreamingAlertRunnable != null) {
      if (pendingLsAlert != null) {
        pendingLsAlert.dismiss();
      }

      longStreamingAlertRunnable.cancel();
      longStreamingAlertRunnable = null;
      pendingLsAlert = null;
    }
  }

  private void showLongStreamingAlert () {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.Warning));
    b.setMessage(Lang.getString(R.string.LongStreamingPreloadAlert));
    b.setPositiveButton(Lang.getString(R.string.LongStreamingPreloadAlertClose), (dialog, which) -> {
      pendingLsAlert = null;
      dialog.dismiss();
    });
    b.setOnCancelListener(dialog -> {
      pendingLsAlert = null;
    });
    pendingLsAlert = UI.getContext(context).showAlert(b);
  }

  public boolean checkTrim () {
    if (currentItem != null && !isDetached) {
      if (currentItem.needTrim()) {
        return setTrim(currentItem.getTrimStartUs(), currentItem.getTrimEndUs());
      } else {
        return setTrim(-1, -1);
      }
    }
    return false;
  }

  private MediaSource originalSource;
  private long trimStartUs = -1, trimEndUs = -1;
  private MediaSource mediaSource;

  public boolean setTrim (long trimStartUs, long trimEndUs) {
    if (this.trimStartUs != trimStartUs || this.trimEndUs != trimEndUs) {
      this.trimStartUs = trimStartUs;
      this.trimEndUs = trimEndUs;
      if (trimStartUs != -1 && trimEndUs != -1) {
        setDataSource(new ClippingMediaSource(originalSource, trimStartUs, trimEndUs));
      } else {
        setDataSource(originalSource);
      }
      return true;
    }
    return false;
  }

  private void setDataSource (MediaSource mediaSource) {
    if (player != null && this.mediaSource != mediaSource) {
      if (this.mediaSource != null && this.mediaSource instanceof ClippingMediaSource) {
        // this.mediaSource.releaseSource();
      }
      this.mediaSource = mediaSource;
      player.setMediaSource(mediaSource);
      player.prepare();
    }
  }

  @Override
  public void onVideoSizeChanged (@NonNull VideoSize videoSize) {
    if (player == null || currentItem == null || videoSize.width == 0 || videoSize.height == 0)
      return;
    if (currentItem.setDimensions(videoSize.width, videoSize.height) && targetView != null) {
      targetView.requestLayout();
    }
  }

  private void reset () {
    setPlaying(false);
    if (player != null) {
      player.seekTo(0);
    }
    updateTimes();
    // asdf
  }

  private boolean destroyed;

  public void destroy () {
    destroyed = true;
    setVideo(null);
  }

  // instance state

  private boolean isDetached;
  private MediaItem detachedItem;
  private long savedPosition;
  private boolean seekToSavedPosition;

  public void attach () {
    if (isDetached) {
      isDetached = false;
      seekToSavedPosition = detachedItem != null;
      setVideo(detachedItem);
      detachedItem = null;
    }
  }

  public void detach () {
    if (!isDetached) {
      detachedItem = currentItem;
      savedPosition = lastPosition;
      if (player != null) {
        player.setPlayWhenReady(false);
      }
      setPlaying(false);
      setVideo(null);
      isDetached = true;
    }
  }

  /*@Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    detach();
  }*/

  // extractor listener

  /*@Override
  public void onLoadError (IOException error) {
    if (!destroyed && currentItem != null && !inForceTouchMode) {
      UI.showToast("Error playing video", Toast.LENGTH_SHORT);
    }
  }*/

  // utils

  public void layoutInner (int width, int height, int offsetHorizontal, int offsetTop, int offsetBottom) {
    /*int availWidth = receiver.getWidth();
    int availHeight = receiver.getHeight();



    float scale = Math.min((float) receiver.getWidth() / (float) getMeasuredWidth(), (float) receiver.getHeight() / (float) getMeasuredHeight());

    playerView.setPivotX(receiver.getWidth() / 2);
    playerView.setPivotY(receiver.getHeight() / 2);
    playerView.setScaleX(scale);
    playerView.setScaleY(scale);*/
  }

  private MediaCellView boundCell;

  public void setBoundCell (MediaCellView boundCell) {
    this.boundCell = boundCell;
  }

  // ExoPlayer listener

  @Override
  public void onPlaybackStateChanged (@Player.State int playbackState) {
    if (callback != null) {
      if (playbackState == Player.STATE_READY) {
        callback.onPlayReady();
      }

      callback.onBufferingStateChanged(playbackState == Player.STATE_BUFFERING);
    }

    switch (playbackState) {
      case Player.STATE_ENDED: {
        if (isLooping && player != null) {
          player.seekTo(0);
        } else {
          reset();
        }
        break;
      }
    }
  }

  private boolean preferExtensions = Config.PREFER_RENDER_EXTENSIONS;

  @Override
  public void onPlayerError (PlaybackException error) {
    if (U.isRenderError(error) && preferExtensions == Config.PREFER_RENDER_EXTENSIONS) {
      Log.w(Log.TAG_VIDEO, "Unable to play video, but trying to retry, preferExtensions:%b", error, preferExtensions);
      preferExtensions = !preferExtensions;
      boolean isPlaying = this.isPlaying;
      MediaItem currentItem = this.currentItem;
      setVideo(null);
      setVideo(currentItem);
      setPlaying(isPlaying);
    } else {
      Log.e(Log.TAG_VIDEO, "Unable to play video", error);
      boolean isGif = currentItem != null && currentItem.isGifType();
      UI.showToast(U.isUnsupportedFormat(error) ? (isGif ? R.string.GifPlaybackUnsupported : R.string.VideoPlaybackUnsupported) : (isGif ? R.string.GifPlaybackError : R.string.VideoPlaybackError), Toast.LENGTH_SHORT);
      setVideo(null);
      if (callback != null) {
        callback.onPlayError();
      }
    }
  }

  @Override
  public void onShuffleModeEnabledChanged (boolean b) { }

  @Override
  public void onPositionDiscontinuity (int i) { }

  @Override
  public void onSeekProcessed () { }

  public interface Callback {
    void onPlayReady ();
    void onPlayPause (boolean isPlaying);
    void onPlayProgress (long totalDuration, long now);
    default void onBufferingStateChanged (boolean isBuffering) {}
    default void onPlayError() {}
  }

  private Callback callback;

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  private static final int SEEK_UPDATE_MS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? 24 : 42;
  private long lastPosition;

  private void updateTimes () {
    if (callback != null && player != null && player.getDuration() != C.TIME_UNSET) {
      lastPosition = player.getCurrentPosition();
      callback.onPlayProgress(player.getDuration(), lastPosition);
    }
    if (isPlaying && !playNeeded && !noProgressUpdates) {
      seekHandler.sendMessageDelayed(Message.obtain(seekHandler, 0, this), SEEK_UPDATE_MS);
    }
  }

  private boolean isPlaying;

  @Override
  public void run () {

  }

  private void setPlaying (boolean isPlaying) {
    if (this.isPlaying != isPlaying) {
      this.isPlaying = isPlaying;
      if (isPlaying) {
        updateTimes();
      } else {
        seekHandler.removeMessages(0);
      }
      if (player != null) {
        player.setPlayWhenReady(isPlaying);
      }
      if (callback != null) {
        callback.onPlayPause(isPlaying);
      }
      UI.getContext(context).setScreenFlagEnabled(BaseActivity.SCREEN_FLAG_PLAYING_REGULAR_VIDEO, isPlaying);
    }
  }

  public void requestLayout () {
    if (targetView != null) {
      targetView.requestLayout();
    }
  }

  public boolean isPlaying () {
    return isPlaying;
  }

  public void playPause () {
    setPlaying(!isPlaying);
  }

  private boolean isMuted;

  public void setMuted (boolean isMuted) {
    if (this.isMuted != isMuted) {
      this.isMuted = isMuted;
      checkMuted();
    }
  }

  @Override
  public void onCurrentCallChanged (Tdlib tdlib, @Nullable final TdApi.Call call) {
    setForcelyMuted(call != null);
  }

  private boolean forcelyMuted;

  private void setForcelyMuted (boolean muted) {
    if (this.forcelyMuted != muted) {
      this.forcelyMuted = muted;
      checkMuted();
    }
  }

  private void checkMuted () {
    boolean muted = isMuted || forcelyMuted;
    if (player != null) {
      player.setVolume(muted ? 0f : 1f);
    }
  }

  private boolean playNeeded;

  public void pauseIfPlaying () {
    playNeeded = isPlaying && player != null;
    if (playNeeded) {
      player.setPlayWhenReady(false);
    }
  }

  public void setSeekProgress (float progress) {
    if (player != null) {
      player.seekTo((long) ((double) player.getDuration() * (double) progress));
      // player.setPlayWhenReady(false);
      updateTimes();
    }
  }

  public void resumeIfNeeded (float progress) {
    if (player != null) {
      player.seekTo((long) ((double) player.getDuration() * (double) progress));
      if (playNeeded) {
        playNeeded = false;
        player.setPlayWhenReady(true);
      }
      updateTimes();
    }
  }
}
