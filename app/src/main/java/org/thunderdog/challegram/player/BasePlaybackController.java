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
 * File created on 15/01/2018
 */
package org.thunderdog.challegram.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;

public abstract class BasePlaybackController implements TGPlayerController.TrackChangeListener, Player.Listener {
  private boolean isDestroyed;

  @Override
  public final void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUserRequest) {
    boolean isPlaying = state == TGPlayerController.STATE_PLAYING;
    TdApi.Message newMessage;
    if (newTrack != null && state != TGPlayerController.STATE_NONE && isSupported(newTrack) && !isDestroyed) {
      newMessage = newTrack;
    } else {
      newMessage = null;
    }
    setPlaybackObject(tdlib, newMessage, isPlaying, byUserRequest);
  }

  protected @Nullable Tdlib tdlib;
  protected @Nullable TdApi.Message object;

  public final boolean comparePlayingObject (@NonNull Tdlib tdlib, @NonNull TdApi.Message object) {
    return this.tdlib != null && this.tdlib == tdlib && this.object != null && this.object.chatId == object.chatId && this.object.id == object.id && TD.getFileId(this.object) == TD.getFileId(object);
  }

  protected final void destroy () {
    isDestroyed = true;
    if (object != null) {
      TdlibManager.instance().player().stopPlayback(false);
    }
  }

  private void stopPlayback () {
    if (object != null) {
      TdApi.Message oldObject = this.object;
      this.object = null;
      finishPlayback(tdlib, oldObject, false);
    }
  }

  public final long getPlayingChatId () {
    return object != null ? object.chatId : 0;
  }

  public final long getPlayingMessageId () {
    return object != null ? object.id : 0;
  }

  private void setPlaybackObject (Tdlib tdlib, TdApi.Message object, boolean isPlaying, boolean byUserRequest) {
    if (this.object == null && object == null) { // Nothing to do.
      return;
    }

    if (object == null) { // We need to stop any playback & release resources
      Tdlib oldTdlib = this.tdlib;
      TdApi.Message oldObject = this.object;
      this.tdlib = tdlib;
      this.object = null;
      finishPlayback(oldTdlib, oldObject, byUserRequest);
      return;
    }

    boolean hadObject = this.tdlib == tdlib && this.object != null && this.object.chatId == object.id;
    int previousFileId = this.object != null ? TD.getFileId(this.object) : -1;
    Tdlib previousTdlib = this.tdlib;
    boolean equals = comparePlayingObject(tdlib, object);

    this.tdlib = tdlib;
    this.object = object;

    if (equals) { // Playback object not changed, simply play/pause.
      playPause(isPlaying);
    } else { // So, we actually need to initialize a new playback
      startPlayback(tdlib, object, byUserRequest, hadObject, previousTdlib, previousFileId);
    }
  }

  protected abstract boolean isSupported (TdApi.Message message);
  protected abstract void finishPlayback (Tdlib tdlib, TdApi.Message oldMessage, boolean byUserRequest);
  protected abstract void playPause (boolean isPlaying);
  protected abstract void startPlayback (Tdlib tdlib, TdApi.Message message, boolean byUserRequest, boolean hadObject, Tdlib previousTdlib, int previousFileId);
  protected abstract void displayPlaybackError (PlaybackException e);

  @Override
  public void onPlayerError (@NonNull PlaybackException e) {
    Log.e(Log.TAG_PLAYER, "onPlayerError", e);
    if (object != null) {
      displayPlaybackError(e);
      stopPlayback();
    }
  }
}
