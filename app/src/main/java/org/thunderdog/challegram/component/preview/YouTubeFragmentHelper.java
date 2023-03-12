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
 * File created on 18/03/2016 at 15:00
 */
package org.thunderdog.challegram.component.preview;

import android.widget.Toast;

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;

class YouTubeFragmentHelper implements YouTubePlayer.OnInitializedListener, YouTubePlayer.PlaybackEventListener, YouTubePlayer.PlayerStateChangeListener, Runnable, YouTubePlayer.OnFullscreenListener {
  private static final int FLAG_STOPPED = 0x01;
  private static final int FLAG_DESTROYED = 0x02;
  private static final int FLAG_PLAYING = 0x04;
  private static final int FLAG_ENDED = 0x08;

  private int flags;

  private String videoId;
  private Callback callback;
  private YouTubePlayerControls controls;
  private YouTubePlayer player;

  public YouTubeFragmentHelper (String videoId, YouTubePlayerControls controls, Callback callback) {
    this.videoId = videoId;
    this.controls = controls;
    this.callback = callback;
  }

  public void onResume () {
    if ((flags & FLAG_STOPPED) != 0) {
      flags &= ~FLAG_STOPPED;
      processVideo(this.player, true);
    }
  }

  public void onDestroy () {
    flags |= FLAG_DESTROYED;
  }

  private void initPlayer (boolean wasRestored) {
    try {
      this.player.setFullscreen(false);
      this.player.setFullscreenControlFlags(YouTubePlayer.FULLSCREEN_FLAG_CUSTOM_LAYOUT);
      // this.player.setOnFullscreenListener(helper);
      this.player.setPlayerStateChangeListener(this);
      this.player.setPlaybackEventListener(this);

      if (wasRestored) {
        processVideo(this.player, true);
      } else {
        processVideo(this.player, false);
      }
    } catch (Throwable ignored) {
      if (callback != null) {
        callback.onYouTubeFatalError();
      }
    }
  }

  @Override
  public void onInitializationSuccess (YouTubePlayer.Provider provider, YouTubePlayer player, boolean wasRestored) {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "YouTube: onInitializationSuccess restored: %b player: %s, provider: %s", wasRestored, player.toString(), provider.toString());
    }
    this.player = player;
    this.player.setPlayerStyle(YouTubePlayer.PlayerStyle.CHROMELESS);
    initPlayer(wasRestored);
  }

  @Override
  public void onInitializationFailure (YouTubePlayer.Provider provider, YouTubeInitializationResult error) {
    Log.e(Log.TAG_YOUTUBE, "onInitializationError", error.toString(), provider.toString());
    if (this.callback != null) {
      this.callback.onYouTubeError(YouTube.getError(error));
    }
  }

  // State listener

  @Override
  public void onLoading () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onLoading");
    }
  /*if (finished && controls != null) {
    finished = false;
    controls.onRestarted();
  }*/
  }

  @Override
  public void onLoaded (String s) {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onLoaded: %s", s);
    }
    if (controls != null && player != null && (flags & FLAG_DESTROYED) == 0) {
      controls.setDuration(player.getDurationMillis());
      if (callback != null) {
        callback.onYouTubeReady();
      }
    }
  }

  @Override
  public void onAdStarted () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onAdStarted");
    }
  }

  @Override
  public void onVideoStarted () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "YouTube: onVideoStarted");
    }
  }

  @Override
  public void onVideoEnded () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onVideoEnded");
    }
    flags |= FLAG_ENDED;
    lastSeekInMillis = 0;
    if (controls != null) {
      controls.onFinished();
    }
  }

  @Override
  public void onError (YouTubePlayer.ErrorReason errorReason) {
    Log.e(Log.TAG_YOUTUBE, "onError: %s", errorReason.toString());
    UI.showToast(errorReason.name(), Toast.LENGTH_SHORT);
  }

  // Seek

  private static final int SEEK_DELAY = 500;

  @Override
  public void run () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.v(Log.TAG_YOUTUBE, "Updating current seek");
    }
    if (controls != null && (flags & FLAG_DESTROYED) == 0) {
      lastSeekInMillis = player.getCurrentTimeMillis();
      if (player != null && (flags & FLAG_ENDED) == 0) {
        controls.setCurrentSeek(lastSeekInMillis);
      }
      if ((flags & FLAG_PLAYING) != 0) {
        controls.postDelayed(this, SEEK_DELAY);
      }
    }
  }

  // Event listener

  @Override
  public void onPlaying () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onPlaying");
    }
    flags |= FLAG_PLAYING;
    if ((flags & FLAG_ENDED) != 0) {
      flags &= ~FLAG_ENDED;
      if (controls != null) {
        controls.onRestarted();
      }
    }
    if (controls != null) {
      controls.post(this);
      controls.onPlayPause(true, false);
    }
  }

  @Override
  public void onPaused () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onPaused");
    }
    flags &= ~FLAG_PLAYING;
    if (controls != null) {
      controls.onPlayPause(false, false);
    }
  }

  @Override
  public void onStopped () {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onStopped");
    }
    flags &= ~FLAG_PLAYING;
    flags |= FLAG_STOPPED;
  }

  @Override
  public void onBuffering (boolean b) {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onBuffering");
    }
    // nothing
  }

  private int lastSeekInMillis;

  public int lastSeekInMillis () {
    return lastSeekInMillis;
  }

  @Override
  public void onSeekTo (int i) {
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i(Log.TAG_YOUTUBE, "onSeekTo %d", i);
    }
    lastSeekInMillis = i;
    if (controls != null && (flags & FLAG_DESTROYED) == 0) {
      controls.setCurrentSeek(i);
    }
  }

  // Fullscreen

  @Override
  public void onFullscreen (boolean b) {

  }

  public void processVideo (YouTubePlayer player, boolean useSeek) {
    /*if (useSeek) {
      if (Challegram.instance().useAutoPlay()) {
        player.loadVideo(videoId, lastSeekInMillis);
      } else {
        player.cueVideo(videoId, lastSeekInMillis);
      }
    } else {
      if (Challegram.instance().useAutoPlay()) {
        player.loadVideo(videoId);
      } else {
        player.cueVideo(videoId);
      }
    }*/
    try {
      if (useSeek) {
        player.loadVideo(videoId, lastSeekInMillis);
      } else {
        player.loadVideo(videoId);
      }
    } catch (Throwable ignored) {
      if (callback != null) {
        callback.onYouTubeFatalError();
      }
    }
  }

  public boolean isStopped () {
    return (flags & FLAG_DESTROYED) != 0 || (flags & FLAG_STOPPED) != 0;
  }

  public YouTubePlayer getPlayer () {
    return player;
  }

  public interface Callback {
    void onYouTubeReady ();
    void onYouTubeError (String error);
    void onYouTubeFatalError ();
  }
}
