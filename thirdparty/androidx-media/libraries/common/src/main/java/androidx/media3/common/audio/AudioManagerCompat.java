/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.common.audio;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.BackgroundExecutor;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Compatibility layer for {@link AudioManager} with fallbacks for older Android versions. */
@UnstableApi
public final class AudioManagerCompat {

  private static final String TAG = "AudioManagerCompat";

  /**
   * Audio focus gain types. One of {@link #AUDIOFOCUS_NONE}, {@link #AUDIOFOCUS_GAIN}, {@link
   * #AUDIOFOCUS_GAIN_TRANSIENT}, {@link #AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK} or {@link
   * #AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    AUDIOFOCUS_NONE,
    AUDIOFOCUS_GAIN,
    AUDIOFOCUS_GAIN_TRANSIENT,
    AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
    AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
  })
  public @interface AudioFocusGain {}

  /** Used to indicate no audio focus has been gained or lost, or requested. */
  @SuppressWarnings("InlinedApi")
  public static final int AUDIOFOCUS_NONE = AudioManager.AUDIOFOCUS_NONE;

  /** Used to indicate a gain of audio focus, or a request of audio focus, of unknown duration. */
  public static final int AUDIOFOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN;

  /**
   * Used to indicate a temporary gain or request of audio focus, anticipated to last a short amount
   * of time. Examples of temporary changes are the playback of driving directions, or an event
   * notification.
   */
  public static final int AUDIOFOCUS_GAIN_TRANSIENT = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;

  /**
   * Used to indicate a temporary request of audio focus, anticipated to last a short amount of
   * time, and where it is acceptable for other audio applications to keep playing after having
   * lowered their output level (also referred to as "ducking"). Examples of temporary changes are
   * the playback of driving directions where playback of music in the background is acceptable.
   */
  public static final int AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK =
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;

  /**
   * Used to indicate a temporary request of audio focus, anticipated to last a short amount of
   * time, during which no other applications, or system components, should play anything. Examples
   * of exclusive and transient audio focus requests are voice memo recording and speech
   * recognition, during which the system shouldn't play any notifications, and media playback
   * should have paused.
   */
  public static final int AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE =
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;

  @SuppressWarnings("NonFinalStaticField") // Lazily initialized under class lock
  @Nullable
  private static AudioManager audioManager;

  @SuppressWarnings("NonFinalStaticField") // Lazily initialized under class lock
  private static @MonotonicNonNull Context applicationContext;

  /**
   * Returns the {@link AudioManager}.
   *
   * <p>This method avoids potential threading issues where AudioManager keeps access to the thread
   * it was created on until after this thread is stopped.
   *
   * <p>It is recommended to use this method from a background thread.
   *
   * @param context A {@link Context}.
   * @return The {@link AudioManager}.
   */
  public static synchronized AudioManager getAudioManager(Context context) {
    Context applicationContext = context.getApplicationContext();
    if (AudioManagerCompat.applicationContext != applicationContext) {
      // Reset cached instance if the application context changed. This should only happen in tests.
      audioManager = null;
    }
    if (audioManager != null) {
      return audioManager;
    }
    @Nullable Looper myLooper = Looper.myLooper();
    if (myLooper == null || myLooper == Looper.getMainLooper()) {
      // The AudioManager will assume the main looper as default callback anyway, so create the
      // instance here without using BackgroundExecutor.
      audioManager = (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
      return checkNotNull(audioManager);
    }
    // Create the audio manager on the BackgroundExecutor to avoid running the potentially blocking
    // command on the main thread but still use a thread that is guaranteed to exist for the
    // lifetime of the app.
    ConditionVariable audioManagerSetCondition = new ConditionVariable();
    BackgroundExecutor.get()
        .execute(
            () -> {
              audioManager =
                  (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
              audioManagerSetCondition.open();
            });
    audioManagerSetCondition.blockUninterruptible();
    return checkNotNull(audioManager);
  }

  /**
   * Requests audio focus. See the {@link AudioFocusRequestCompat} for information about the options
   * available to configure your request, and notification of focus gain and loss.
   *
   * @param audioManager The {@link AudioManager}.
   * @param focusRequest An {@link AudioFocusRequestCompat} instance used to configure how focus is
   *     requested.
   * @return {@link AudioManager#AUDIOFOCUS_REQUEST_FAILED} or {@link
   *     AudioManager#AUDIOFOCUS_REQUEST_GRANTED}.
   */
  @SuppressWarnings("deprecation")
  public static int requestAudioFocus(
      AudioManager audioManager, AudioFocusRequestCompat focusRequest) {
    if (Util.SDK_INT >= 26) {
      return audioManager.requestAudioFocus(focusRequest.getAudioFocusRequest());
    } else {
      return audioManager.requestAudioFocus(
          focusRequest.getOnAudioFocusChangeListener(),
          focusRequest.getAudioAttributes().getStreamType(),
          focusRequest.getFocusGain());
    }
  }

  /**
   * Abandon audio focus. Causes the previous focus owner, if any, to receive focus.
   *
   * @param audioManager The {@link AudioManager}.
   * @param focusRequest The {@link AudioFocusRequestCompat} that was used when requesting focus
   *     with {@link #requestAudioFocus(AudioManager, AudioFocusRequestCompat)}.
   * @return {@link AudioManager#AUDIOFOCUS_REQUEST_FAILED} or {@link
   *     AudioManager#AUDIOFOCUS_REQUEST_GRANTED}
   */
  @SuppressWarnings("deprecation")
  public static int abandonAudioFocusRequest(
      AudioManager audioManager, AudioFocusRequestCompat focusRequest) {
    if (Util.SDK_INT >= 26) {
      return audioManager.abandonAudioFocusRequest(focusRequest.getAudioFocusRequest());
    } else {
      return audioManager.abandonAudioFocus(focusRequest.getOnAudioFocusChangeListener());
    }
  }

  /**
   * Returns the maximum volume index for a particular stream.
   *
   * @param audioManager The {@link AudioManager}.
   * @param streamType The {@link C.StreamType} whose maximum volume index is returned.
   * @return The maximum valid volume index for the stream.
   */
  @IntRange(from = 0)
  public static int getStreamMaxVolume(AudioManager audioManager, @C.StreamType int streamType) {
    return audioManager.getStreamMaxVolume(streamType);
  }

  /**
   * Returns the minimum volume index for a particular stream.
   *
   * @param audioManager The {@link AudioManager}.
   * @param streamType The {@link C.StreamType} whose minimum volume index is returned.
   * @return The minimum valid volume index for the stream.
   */
  @IntRange(from = 0)
  public static int getStreamMinVolume(AudioManager audioManager, @C.StreamType int streamType) {
    return Util.SDK_INT >= 28 ? audioManager.getStreamMinVolume(streamType) : 0;
  }

  /**
   * Returns the current volume for a particular stream.
   *
   * @param audioManager The {@link AudioManager}.
   * @param streamType The {@link C.StreamType} whose volume is returned.
   * @return The current volume of the stream.
   */
  public static int getStreamVolume(AudioManager audioManager, @C.StreamType int streamType) {
    // AudioManager#getStreamVolume(int) throws an exception on some devices. See
    // https://github.com/google/ExoPlayer/issues/8191.
    try {
      return audioManager.getStreamVolume(streamType);
    } catch (RuntimeException e) {
      Log.w(TAG, "Could not retrieve stream volume for stream type " + streamType, e);
      return audioManager.getStreamMaxVolume(streamType);
    }
  }

  /**
   * Returns whether the given stream is muted.
   *
   * @param audioManager The {@link AudioManager}.
   * @param streamType The {@link C.StreamType} to check.
   * @return Whether the stream is muted.
   */
  public static boolean isStreamMute(AudioManager audioManager, @C.StreamType int streamType) {
    if (Util.SDK_INT >= 23) {
      return audioManager.isStreamMute(streamType);
    } else {
      return getStreamVolume(audioManager, streamType) == 0;
    }
  }

  private AudioManagerCompat() {}
}
