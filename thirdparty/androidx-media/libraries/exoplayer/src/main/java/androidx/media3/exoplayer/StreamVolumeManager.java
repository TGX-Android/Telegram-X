/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioManagerCompat;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.BackgroundThreadStateHandler;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A manager that wraps {@link AudioManager} to control/listen audio stream volume. */
/* package */ final class StreamVolumeManager {

  /** A listener for changes in the manager. */
  public interface Listener {

    /** Called when the audio stream type is changed. */
    void onStreamTypeChanged(@C.StreamType int streamType);

    /** Called when the audio stream volume or mute state is changed. */
    void onStreamVolumeChanged(int streamVolume, boolean streamMuted);
  }

  private static final String TAG = "StreamVolumeManager";

  // TODO(b/151280453): Replace the hidden intent action with an official one.
  // Copied from AudioManager#VOLUME_CHANGED_ACTION
  private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

  private final Context applicationContext;
  private final Listener listener;
  private final BackgroundThreadStateHandler<StreamVolumeState> stateHandler;

  private @MonotonicNonNull AudioManager audioManager;
  @Nullable private VolumeChangeReceiver receiver;
  private int volumeBeforeMute;

  /**
   * Creates a manager.
   *
   * @param context A {@link Context}.
   * @param listener A {@link Listener} for volume changes.
   * @param streamType The initial {@link C.StreamType}.
   * @param audioManagerLooper The background {@link Looper} to run {@link AudioManager} calls on.
   * @param listenerLooper The {@link Looper} to call {@code listener} methods on.
   * @param clock The {@link Clock}.
   */
  @SuppressWarnings("initialization:methodref.receiver.bound.invalid") // this::method reference
  public StreamVolumeManager(
      Context context,
      Listener listener,
      @C.StreamType int streamType,
      Looper audioManagerLooper,
      Looper listenerLooper,
      Clock clock) {
    this.applicationContext = context.getApplicationContext();
    this.listener = listener;
    StreamVolumeState initialState =
        new StreamVolumeState(
            streamType,
            /* volume= */ 0,
            /* muted= */ false,
            /* minVolume= */ 0,
            /* maxVolume= */ 0);
    stateHandler =
        new BackgroundThreadStateHandler<>(
            initialState,
            audioManagerLooper,
            listenerLooper,
            clock,
            this::onStreamVolumeStateChanged);
    stateHandler.runInBackground(
        () -> {
          audioManager =
              Assertions.checkStateNotNull(
                  (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE));
          VolumeChangeReceiver receiver = new VolumeChangeReceiver();
          IntentFilter filter = new IntentFilter(VOLUME_CHANGED_ACTION);
          try {
            applicationContext.registerReceiver(receiver, filter);
            this.receiver = receiver;
          } catch (RuntimeException e) {
            Log.w(TAG, "Error registering stream volume receiver", e);
          }
          stateHandler.setStateInBackground(generateState(streamType));
        });
  }

  /** Sets the audio stream type. */
  public void setStreamType(@C.StreamType int streamType) {
    stateHandler.updateStateAsync(
        /* placeholderState= */ state ->
            new StreamVolumeState(
                streamType, state.volume, state.muted, state.minVolume, state.maxVolume),
        /* backgroundStateUpdate= */ state ->
            state.streamType == streamType ? state : generateState(streamType));
  }

  /**
   * Gets the minimum volume for the current audio stream. It can be changed if {@link
   * #setStreamType(int)} is called.
   */
  public int getMinVolume() {
    return stateHandler.get().minVolume;
  }

  /**
   * Gets the maximum volume for the current audio stream. It can be changed if {@link
   * #setStreamType(int)} is called.
   */
  public int getMaxVolume() {
    return stateHandler.get().maxVolume;
  }

  /** Gets the current volume for the current audio stream. */
  public int getVolume() {
    return stateHandler.get().volume;
  }

  /** Gets whether the current audio stream is muted or not. */
  public boolean isMuted() {
    return stateHandler.get().muted;
  }

  /**
   * Sets the volume with the given value for the current audio stream with specified volume flags.
   *
   * @param volume The value should be between {@link #getMinVolume()} and {@link #getMaxVolume()},
   *     otherwise the volume will not be changed.
   * @param flags Either 0 or a bitwise combination of one or more {@link C.VolumeFlags}.
   */
  @SuppressLint("WrongConstant") // Setting C.VolumeFlags as audio system volume flags.
  public void setVolume(int volume, @C.VolumeFlags int flags) {
    stateHandler.updateStateAsync(
        /* placeholderState= */ state ->
            new StreamVolumeState(
                state.streamType,
                volume >= state.minVolume && volume <= state.maxVolume ? volume : state.volume,
                /* muted= */ volume == 0,
                state.minVolume,
                state.maxVolume),
        /* backgroundStateUpdate= */ state -> {
          if (volume == state.volume || volume < state.minVolume || volume > state.maxVolume) {
            return state;
          }
          checkNotNull(audioManager).setStreamVolume(state.streamType, volume, flags);
          return generateState(state.streamType);
        });
  }

  /**
   * Increases the volume by one for the current audio stream with specified volume flags. If the
   * current volume is equal to {@link #getMaxVolume()}, it will not be increased.
   *
   * @param flags Either 0 or a bitwise combination of one or more {@link C.VolumeFlags}.
   */
  @SuppressLint("WrongConstant") // Setting C.VolumeFlags as audio system volume flags.
  public void increaseVolume(@C.VolumeFlags int flags) {
    stateHandler.updateStateAsync(
        /* placeholderState= */ state ->
            new StreamVolumeState(
                state.streamType,
                state.volume < state.maxVolume ? state.volume + 1 : state.maxVolume,
                /* muted= */ false,
                state.minVolume,
                state.maxVolume),
        /* backgroundStateUpdate= */ state -> {
          if (state.volume >= state.maxVolume) {
            return state;
          }
          checkNotNull(audioManager)
              .adjustStreamVolume(state.streamType, AudioManager.ADJUST_RAISE, flags);
          return generateState(state.streamType);
        });
  }

  /**
   * Decreases the volume by one for the current audio stream with specified volume flags. If the
   * current volume is equal to {@link #getMinVolume()}, it will be be decreased.
   *
   * @param flags Either 0 or a bitwise combination of one or more {@link C.VolumeFlags}.
   */
  @SuppressLint("WrongConstant") // Setting C.VolumeFlags as audio system volume flags.
  public void decreaseVolume(@C.VolumeFlags int flags) {
    stateHandler.updateStateAsync(
        /* placeholderState= */ state ->
            new StreamVolumeState(
                state.streamType,
                state.volume > state.minVolume ? state.volume - 1 : state.minVolume,
                /* muted= */ state.volume <= 1,
                state.minVolume,
                state.maxVolume),
        /* backgroundStateUpdate= */ state -> {
          if (state.volume <= state.minVolume) {
            return state;
          }
          checkNotNull(audioManager)
              .adjustStreamVolume(state.streamType, AudioManager.ADJUST_LOWER, flags);
          return generateState(state.streamType);
        });
  }

  /**
   * Sets the mute state of the current audio stream with specified volume flags.
   *
   * @param muted Whether to mute or to unmute the stream.
   * @param flags Either 0 or a bitwise combination of one or more {@link C.VolumeFlags}.
   */
  @SuppressLint("WrongConstant") // Setting C.VolumeFlags as audio system volume flags.
  public void setMuted(boolean muted, @C.VolumeFlags int flags) {
    stateHandler.updateStateAsync(
        /* placeholderState= */ state ->
            new StreamVolumeState(
                state.streamType,
                state.muted == muted ? state.volume : (muted ? 0 : volumeBeforeMute),
                muted,
                state.minVolume,
                state.maxVolume),
        /* backgroundStateUpdate= */ state -> {
          if (state.muted == muted) {
            return state;
          }
          checkNotNull(audioManager);
          if (Util.SDK_INT >= 23) {
            audioManager.adjustStreamVolume(
                state.streamType,
                muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                flags);
          } else {
            audioManager.setStreamMute(state.streamType, muted);
          }
          return generateState(state.streamType);
        });
  }

  /** Releases the manager. It must be called when the manager is no longer required. */
  public void release() {
    stateHandler.updateStateAsync(
        /* placeholderState= */ state -> state,
        /* backgroundStateUpdate= */ state -> {
          if (receiver != null) {
            try {
              applicationContext.unregisterReceiver(receiver);
            } catch (RuntimeException e) {
              Log.w(TAG, "Error unregistering stream volume receiver", e);
            }
            receiver = null;
          }
          return state;
        });
  }

  private void onStreamVolumeStateChanged(StreamVolumeState oldState, StreamVolumeState newState) {
    if (!oldState.muted && newState.muted) {
      volumeBeforeMute = oldState.volume;
    }
    if (oldState.volume != newState.volume || oldState.muted != newState.muted) {
      listener.onStreamVolumeChanged(newState.volume, newState.muted);
    }
    if (oldState.streamType != newState.streamType
        || oldState.minVolume != newState.minVolume
        || oldState.maxVolume != newState.maxVolume) {
      listener.onStreamTypeChanged(newState.streamType);
    }
  }

  private StreamVolumeState generateState(@C.StreamType int streamType) {
    checkNotNull(audioManager);
    int volume = AudioManagerCompat.getStreamVolume(audioManager, streamType);
    boolean muted = AudioManagerCompat.isStreamMute(audioManager, streamType);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, streamType);
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, streamType);
    return new StreamVolumeState(streamType, volume, muted, minVolume, maxVolume);
  }

  private static final class StreamVolumeState {

    public final @C.StreamType int streamType;
    public final int volume;
    public final boolean muted;
    public final int minVolume;
    public final int maxVolume;

    public StreamVolumeState(
        @C.StreamType int streamType, int volume, boolean muted, int minVolume, int maxVolume) {
      this.streamType = streamType;
      this.volume = volume;
      this.muted = muted;
      this.minVolume = minVolume;
      this.maxVolume = maxVolume;
    }
  }

  private final class VolumeChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      // BroadcastReceivers are called on the main thread.
      stateHandler.runInBackground(
          () -> {
            if (receiver == null) {
              // Stale event. StreamVolumeManager is already released.
              return;
            }
            int streamType = stateHandler.get().streamType;
            stateHandler.setStateInBackground(generateState(streamType));
          });
    }
  }
}
