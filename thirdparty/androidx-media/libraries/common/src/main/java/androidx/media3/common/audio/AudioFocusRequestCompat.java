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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;

/**
 * Compatibility version of an {@link AudioFocusRequest} with fallbacks for older Android versions.
 */
@UnstableApi
public final class AudioFocusRequestCompat {

  private final @AudioManagerCompat.AudioFocusGain int focusGain;
  private final AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;
  private final Handler focusChangeHandler;
  private final AudioAttributes audioAttributes;
  private final boolean pauseOnDuck;

  @Nullable private final Object frameworkAudioFocusRequest;

  /* package */ AudioFocusRequestCompat(
      int focusGain,
      AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener,
      Handler focusChangeHandler,
      AudioAttributes audioFocusRequestCompat,
      boolean pauseOnDuck) {
    this.focusGain = focusGain;
    this.focusChangeHandler = focusChangeHandler;
    this.audioAttributes = audioFocusRequestCompat;
    this.pauseOnDuck = pauseOnDuck;

    if (Util.SDK_INT < 26) {
      this.onAudioFocusChangeListener =
          new OnAudioFocusChangeListenerHandlerCompat(
              onAudioFocusChangeListener, focusChangeHandler);
    } else {
      this.onAudioFocusChangeListener = onAudioFocusChangeListener;
    }

    if (Util.SDK_INT >= 26) {
      this.frameworkAudioFocusRequest =
          new AudioFocusRequest.Builder(focusGain)
              .setAudioAttributes(audioAttributes.getAudioAttributesV21().audioAttributes)
              .setWillPauseWhenDucked(pauseOnDuck)
              .setOnAudioFocusChangeListener(onAudioFocusChangeListener, focusChangeHandler)
              .build();
    } else {
      this.frameworkAudioFocusRequest = null;
    }
  }

  /**
   * Returns the type of {@link AudioManagerCompat.AudioFocusGain} configured for this {@code
   * AudioFocusRequestCompat}.
   */
  public @AudioManagerCompat.AudioFocusGain int getFocusGain() {
    return focusGain;
  }

  /**
   * Returns the {@link AudioAttributes} set for this {@code AudioFocusRequestCompat}, or the
   * default attributes if none were set.
   */
  public AudioAttributes getAudioAttributes() {
    return audioAttributes;
  }

  /**
   * Returns whether the application that would use this {@code AudioFocusRequestCompat} would pause
   * when it is requested to duck. This value is only applicable on {@link
   * android.os.Build.VERSION_CODES#O} and later.
   */
  public boolean willPauseWhenDucked() {
    return pauseOnDuck;
  }

  /**
   * Returns the {@link AudioManager.OnAudioFocusChangeListener} set for this {@code
   * AudioFocusRequestCompat}.
   *
   * @return The {@link AudioManager.OnAudioFocusChangeListener} that was set.
   */
  public AudioManager.OnAudioFocusChangeListener getOnAudioFocusChangeListener() {
    return onAudioFocusChangeListener;
  }

  /**
   * Returns the {@link Handler} to be used for the {@link AudioManager.OnAudioFocusChangeListener}.
   */
  public Handler getFocusChangeHandler() {
    return focusChangeHandler;
  }

  /** Returns new {@link Builder} with all values of this instance pre-populated. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AudioFocusRequestCompat)) {
      return false;
    }
    AudioFocusRequestCompat that = (AudioFocusRequestCompat) o;
    return focusGain == that.focusGain
        && pauseOnDuck == that.pauseOnDuck
        && Objects.equals(onAudioFocusChangeListener, that.onAudioFocusChangeListener)
        && Objects.equals(focusChangeHandler, that.focusChangeHandler)
        && Objects.equals(audioAttributes, that.audioAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        focusGain, onAudioFocusChangeListener, focusChangeHandler, audioAttributes, pauseOnDuck);
  }

  @RequiresApi(26)
  /* package */ AudioFocusRequest getAudioFocusRequest() {
    return (AudioFocusRequest) checkNotNull(frameworkAudioFocusRequest);
  }

  /**
   * Builder class for {@link AudioFocusRequestCompat} objects.
   *
   * <p>The default values are:
   *
   * <ul>
   *   <li>focus listener and handler: none
   *   <li>audio attributes: {@link AudioAttributes#DEFAULT}
   *   <li>pauses on duck: false
   *   <li>supports delayed focus grant: false
   * </ul>
   *
   * <p>In contrast to a {@link AudioFocusRequest}, attempting to {@link #build()} an {@link
   * AudioFocusRequestCompat} without an {@link AudioManager.OnAudioFocusChangeListener} will throw
   * an {@link IllegalArgumentException}, because the listener is required for all API levels up to
   * API 26.
   */
  public static final class Builder {
    private @AudioManagerCompat.AudioFocusGain int focusGain;
    @Nullable private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;
    @Nullable private Handler focusChangeHandler;
    private AudioAttributes audioAttributes;
    private boolean pauseOnDuck;

    /**
     * Constructs a new {@code Builder}, and specifies how audio focus will be requested.
     *
     * <p>By default there is no focus change listener, delayed focus is not supported, ducking is
     * suitable for the application, and the {@link AudioAttributes} are set to {@link
     * AudioAttributes#DEFAULT}.
     *
     * @param focusGain The type of {@link AudioManagerCompat.AudioFocusGain} that will be
     *     requested.
     */
    public Builder(@AudioManagerCompat.AudioFocusGain int focusGain) {
      this.audioAttributes = AudioAttributes.DEFAULT;
      this.focusGain = focusGain;
    }

    private Builder(AudioFocusRequestCompat other) {
      focusGain = other.getFocusGain();
      onAudioFocusChangeListener = other.getOnAudioFocusChangeListener();
      focusChangeHandler = other.getFocusChangeHandler();
      audioAttributes = other.getAudioAttributes();
      pauseOnDuck = other.willPauseWhenDucked();
    }

    /**
     * Sets the type of {@link AudioManagerCompat.AudioFocusGain} that will be requested.
     *
     * @param focusGain The type of {@link AudioManagerCompat.AudioFocusGain} that will be
     *     requested.
     * @return This {@code Builder} instance.
     */
    @CanIgnoreReturnValue
    public Builder setFocusGain(@AudioManagerCompat.AudioFocusGain int focusGain) {
      checkArgument(isValidFocusGain(focusGain));
      this.focusGain = focusGain;
      return this;
    }

    /**
     * Sets the listener called when audio focus changes after being requested with {@link
     * AudioManagerCompat#requestAudioFocus(AudioManager, AudioFocusRequestCompat)}, and until being
     * abandoned with {@link AudioManagerCompat#abandonAudioFocusRequest(AudioManager,
     * AudioFocusRequestCompat)}. Note that only focus changes (gains and losses) affecting the
     * focus owner are reported, not gains and losses of other focus requesters in the system. <br>
     * Notifications are delivered on the main thread.
     *
     * @param listener The {@link AudioManager.OnAudioFocusChangeListener} receiving the focus
     *     change notifications.
     * @return This {@code Builder} instance.
     */
    @CanIgnoreReturnValue
    public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener listener) {
      return setOnAudioFocusChangeListener(listener, new Handler(Looper.getMainLooper()));
    }

    /**
     * Sets the listener called when audio focus changes after being requested with {@link
     * AudioManagerCompat#requestAudioFocus(AudioManager, AudioFocusRequestCompat)}, and until being
     * abandoned with {@link AudioManagerCompat#abandonAudioFocusRequest(AudioManager,
     * AudioFocusRequestCompat)}. Note that only focus changes (gains and losses) affecting the
     * focus owner are reported, not gains and losses of other focus requesters in the system.
     *
     * @param listener The {@link AudioManager.OnAudioFocusChangeListener} receiving the focus
     *     change notifications.
     * @param handler The {@link Handler} for the thread on which to execute the notifications.
     * @return This {@code Builder} instance.
     */
    @CanIgnoreReturnValue
    public Builder setOnAudioFocusChangeListener(
        AudioManager.OnAudioFocusChangeListener listener, Handler handler) {
      checkNotNull(listener);
      checkNotNull(handler);
      onAudioFocusChangeListener = listener;
      focusChangeHandler = handler;
      return this;
    }

    /**
     * Sets the {@link AudioAttributes} to be associated with the focus request, and which describe
     * the use case for which focus is requested. As the focus requests typically precede audio
     * playback, this information is used on certain platforms to declare the subsequent playback
     * use case. It is therefore good practice to use in this method the same {@code
     * AudioAttributes} as used for playback, see for example {@code
     * ExoPlayer.Builder.setAudioAttributes()}.
     *
     * @param attributes The {@link AudioAttributes} for the focus request.
     * @return This {@code Builder} instance.
     */
    @CanIgnoreReturnValue
    public Builder setAudioAttributes(AudioAttributes attributes) {
      checkNotNull(attributes);
      audioAttributes = attributes;
      return this;
    }

    /**
     * Declares the intended behavior of the application with regards to audio ducking. See more
     * details in the {@link AudioFocusRequest} class documentation. Setting {@code pauseOnDuck} to
     * true will only have an effect on {@link android.os.Build.VERSION_CODES#O} and later.
     *
     * @param pauseOnDuck Use {@code true} if the application intends to pause audio playback when
     *     losing focus with {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}.
     * @return This {@code Builder} instance.
     */
    @CanIgnoreReturnValue
    public Builder setWillPauseWhenDucked(boolean pauseOnDuck) {
      this.pauseOnDuck = pauseOnDuck;
      return this;
    }

    /**
     * Builds a new {@code AudioFocusRequestCompat} instance combining all the information gathered
     * by this builder's configuration methods.
     *
     * @return The {@code AudioFocusRequestCompat}.
     */
    public AudioFocusRequestCompat build() {
      if (onAudioFocusChangeListener == null) {
        throw new IllegalStateException(
            "Can't build an AudioFocusRequestCompat instance without a listener");
      }
      return new AudioFocusRequestCompat(
          focusGain,
          onAudioFocusChangeListener,
          checkNotNull(focusChangeHandler),
          audioAttributes,
          pauseOnDuck);
    }

    /**
     * Checks whether a focus gain constant is a valid value for an audio focus request.
     *
     * @param focusGain value to check
     * @return true if focusGain is a valid value for an audio focus request.
     */
    private static boolean isValidFocusGain(@AudioManagerCompat.AudioFocusGain int focusGain) {
      switch (focusGain) {
        case AudioManagerCompat.AUDIOFOCUS_GAIN:
        case AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT:
        case AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
        case AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
          return true;
        default:
          return false;
      }
    }
  }

  /**
   * Class to allow {@link AudioManager.OnAudioFocusChangeListener#onAudioFocusChange(int)} calls on
   * a specific thread prior to API 26.
   */
  private static class OnAudioFocusChangeListenerHandlerCompat
      implements AudioManager.OnAudioFocusChangeListener {

    private final Handler handler;
    private final AudioManager.OnAudioFocusChangeListener listener;

    /* package */ OnAudioFocusChangeListenerHandlerCompat(
        AudioManager.OnAudioFocusChangeListener listener, Handler handler) {
      this.listener = listener;
      this.handler = Util.createHandler(handler.getLooper(), /* callback= */ null);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
      Util.postOrRun(handler, () -> listener.onAudioFocusChange(focusChange));
    }
  }
}
