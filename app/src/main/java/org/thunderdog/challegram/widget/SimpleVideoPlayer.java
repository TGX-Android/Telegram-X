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
 * File created on 16/10/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ClippingMediaSource;
import androidx.media3.exoplayer.source.MediaSource;

import org.thunderdog.challegram.U;

import java.io.File;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class SimpleVideoPlayer extends TextureView implements Destroyable, Player.Listener {
  public interface Delegate {
    void onVideoRenderStateChanged (boolean hasFrame);
    void onVideoMuteStateChanged (boolean isMuted);
  }

  private @Nullable ExoPlayer player;
  private Delegate delegate;

  public SimpleVideoPlayer (Context context) {
    super(context);
  }

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  private boolean isRendered;

  private void setRendered (boolean isRendered) {
    if (this.isRendered != isRendered) {
      this.isRendered = isRendered;
      if (delegate != null) {
        delegate.onVideoRenderStateChanged(isRendered);
      }
    }
  }

  public void seekTo (float progress) {
    if (player != null) {

    }
  }

  private String path;
  private double trimStartTime = -1, trimEndTime = -1;
  private MediaSource originalSource;

  public void preparePlayer () {
    if (player == null) {
      player = U.newExoPlayer(getContext(), true);
      player.addListener(this);
      player.setVideoTextureView(this);
      updateSettings();
    }
  }

  public void setVideo (@Nullable String path) {
    if (!StringUtils.equalsOrBothEmpty(this.path, path) || StringUtils.isEmpty(path)) {
      this.path = path;
      this.trimStartTime = this.trimEndTime = -1;
      this.videoDuration = 0;
      if (StringUtils.isEmpty(path)) {
        if (player != null) {
          player.release();
          player = null;
        }
        if (mediaSource != null && originalSource != mediaSource) {
          // mediaSource.releaseSource();
          mediaSource = null;
        }
        if (originalSource != null) {
          // originalSource.releaseSource();
          originalSource = null;
        }
        setRendered(false);
      } else {
        preparePlayer();
        this.originalSource = U.newMediaSource(new File(path));
        setDataSource(originalSource);
      }
    }
  }

  private MediaSource mediaSource;

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

  public boolean canTrimRegion () {
    return player != null && isRendered && videoDuration > 0;
  }

  public void setTrimRegion (double totalDuration, double trimStart, double trimEnd) {
    if (player == null) {
      return;
    }
    if (trimStart == 0 && trimEnd == totalDuration) {
      trimStart = -1;
      trimEnd = -1;
    }
    if (this.trimStartTime != trimStart || this.trimEndTime != trimEnd) {
      this.trimStartTime = trimStart;
      this.trimEndTime = trimEnd;
      if (trimStart != -1 && trimEnd != -1) {
        setDataSource(new ClippingMediaSource(originalSource, (long) (trimStart * 1_000_000), (long) (trimEnd * 1_000_000)));
      } else {
        setDataSource(originalSource);
      }
    }
  }

  private boolean isLooping;

  private boolean isMuted;

  public void setLooping (boolean looping) {
    if (this.isLooping != looping) {
      isLooping = looping;
      updateSettings();
    }
  }

  public void setMuted (boolean muted) {
    if (this.isMuted != muted) {
      isMuted = muted;
      updateSettings();
      if (delegate != null) {
        delegate.onVideoMuteStateChanged(muted);
      }
    }
  }

  public void toggleMuted () {
    setMuted(!isMuted);
  }

  private boolean isPlaying;

  public void setPlaying (boolean playing) {
    if (isPlaying != playing) {
      isPlaying = playing;
      updateSettings();
    }
  }

  private boolean activityPaused;

  public void setActivityPaused (boolean activityPaused) {
    if (this.activityPaused != activityPaused) {
      this.activityPaused = activityPaused;
      updateSettings();
    }
  }

  public void play () {
    setPlaying(true);
  }

  public void pause () {
    setPlaying(false);
  }

  private void updateSettings () {
    if (player != null) {
      player.setRepeatMode(isLooping ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
      player.setVolume(isMuted ? 0f : 1f);
      player.setPlayWhenReady(isPlaying && !activityPaused);
    }
  }

  public boolean hasTrim () {
    return canTrimRegion() && (trimStartTime != -1 && trimEndTime != -1);
  }

  public double getStartTime () {
    return hasTrim() ? trimStartTime : -1;
  }

  public double getEndTime () {
    return hasTrim() ? trimEndTime : -1;
  }

  @Override
  public void performDestroy () {
    setVideo(null);
  }

  // Video

  private long videoDuration;

  @Override
  public void onPlaybackStateChanged (@Player.State int playbackState) {
    switch (playbackState) {
      case Player.STATE_READY: {
        if (videoDuration == 0 && !(mediaSource instanceof ClippingMediaSource)) {
          this.videoDuration = player != null ? player.getDuration() : 0;
        }
        break;
      }
      case Player.STATE_ENDED: {
        if (isLooping && player != null) {
          player.seekTo(0);
        }
        break;
      }
    }
  }

  @Override
  public void onRenderedFirstFrame () {
    setRendered(true);
  }
}
