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
package androidx.media3.session.legacy;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.media.VolumeProvider;
import android.os.Build;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Handles requests to adjust or set the volume on a session. This is also used to push volume
 * updates back to the session after a request has been handled. You can set a volume provider on a
 * session by calling {@link MediaSessionCompat#setPlaybackToRemote}.
 */
@UnstableApi
@RestrictTo(LIBRARY)
public abstract class VolumeProviderCompat {

  /** */
  @IntDef({VOLUME_CONTROL_FIXED, VOLUME_CONTROL_RELATIVE, VOLUME_CONTROL_ABSOLUTE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ControlType {}

  /** The volume is fixed and can not be modified. Requests to change volume should be ignored. */
  public static final int VOLUME_CONTROL_FIXED = 0;

  /**
   * The volume control uses relative adjustment via {@link #onAdjustVolume(int)}. Attempts to set
   * the volume to a specific value should be ignored.
   */
  public static final int VOLUME_CONTROL_RELATIVE = 1;

  /**
   * The volume control uses an absolute value. It may be adjusted using {@link
   * #onAdjustVolume(int)} or set directly using {@link #onSetVolumeTo(int)}.
   */
  public static final int VOLUME_CONTROL_ABSOLUTE = 2;

  private final int mControlType;
  private final int mMaxVolume;
  @Nullable private final String mControlId;
  private int mCurrentVolume;
  @Nullable private Callback mCallback;

  @Nullable private VolumeProvider mVolumeProviderFwk;

  /**
   * Create a new volume provider for handling volume events. You must specify the type of volume
   * control and the maximum volume that can be used.
   *
   * @param volumeControl The method for controlling volume that is used by this provider.
   * @param maxVolume The maximum allowed volume.
   * @param currentVolume The current volume.
   */
  public VolumeProviderCompat(@ControlType int volumeControl, int maxVolume, int currentVolume) {
    this(volumeControl, maxVolume, currentVolume, null);
  }

  /**
   * Create a new volume provider for handling volume events. You must specify the type of volume
   * control and the maximum volume that can be used.
   *
   * @param volumeControl The method for controlling volume that is used by this provider.
   * @param maxVolume The maximum allowed volume.
   * @param currentVolume The current volume.
   * @param volumeControlId The volume control ID of this provider.
   */
  public VolumeProviderCompat(
      @ControlType int volumeControl,
      int maxVolume,
      int currentVolume,
      @Nullable String volumeControlId) {
    mControlType = volumeControl;
    mMaxVolume = maxVolume;
    mCurrentVolume = currentVolume;
    mControlId = volumeControlId;
  }

  /**
   * Get the current volume of the provider.
   *
   * @return The current volume.
   */
  public final int getCurrentVolume() {
    return mCurrentVolume;
  }

  /**
   * Get the volume control type that this volume provider uses.
   *
   * @return The volume control type for this volume provider
   */
  @ControlType
  public final int getVolumeControl() {
    return mControlType;
  }

  /**
   * Get the maximum volume this provider allows.
   *
   * @return The max allowed volume.
   */
  public final int getMaxVolume() {
    return mMaxVolume;
  }

  /**
   * Set the current volume and notify the system that the volume has been changed.
   *
   * @param currentVolume The current volume of the output.
   */
  public final void setCurrentVolume(int currentVolume) {
    mCurrentVolume = currentVolume;
    if (Build.VERSION.SDK_INT >= 21) {
      VolumeProvider volumeProviderFwk = (VolumeProvider) getVolumeProvider();
      Api21Impl.setCurrentVolume(volumeProviderFwk, currentVolume);
    }
    if (mCallback != null) {
      mCallback.onVolumeChanged(this);
    }
  }

  /**
   * Gets the volume control ID. It can be used to identify which volume provider is used by the
   * session.
   *
   * @return the volume control ID or {@code null} if it isn't set.
   */
  @Nullable
  public final String getVolumeControlId() {
    return mControlId;
  }

  /**
   * Override to handle requests to set the volume of the current output.
   *
   * @param volume The volume to set the output to.
   */
  public void onSetVolumeTo(int volume) {}

  /**
   * Override to handle requests to adjust the volume of the current output.
   *
   * @param direction The direction to adjust the volume in.
   */
  public void onAdjustVolume(int direction) {}

  /**
   * Sets a callback to receive volume changes.
   *
   * <p>Used internally by the support library.
   */
  public void setCallback(@Nullable Callback callback) {
    mCallback = callback;
  }

  /**
   * Gets the underlying framework {@link android.media.VolumeProvider} object.
   *
   * <p>This method is only supported on API 21+.
   *
   * @return An equivalent {@link android.media.VolumeProvider} object, or null if none.
   */
  @RequiresApi(21)
  public Object getVolumeProvider() {
    if (mVolumeProviderFwk == null) {
      if (Build.VERSION.SDK_INT >= 30) {
        mVolumeProviderFwk =
            new VolumeProvider(mControlType, mMaxVolume, mCurrentVolume, mControlId) {
              @Override
              public void onSetVolumeTo(int volume) {
                VolumeProviderCompat.this.onSetVolumeTo(volume);
              }

              @Override
              public void onAdjustVolume(int direction) {
                VolumeProviderCompat.this.onAdjustVolume(direction);
              }
            };
      } else {
        mVolumeProviderFwk =
            new VolumeProvider(mControlType, mMaxVolume, mCurrentVolume) {
              @Override
              public void onSetVolumeTo(int volume) {
                VolumeProviderCompat.this.onSetVolumeTo(volume);
              }

              @Override
              public void onAdjustVolume(int direction) {
                VolumeProviderCompat.this.onAdjustVolume(direction);
              }
            };
      }
    }
    return mVolumeProviderFwk;
  }

  /** Listens for changes to the volume. */
  public abstract static class Callback {
    public abstract void onVolumeChanged(VolumeProviderCompat volumeProvider);
  }

  @RequiresApi(21)
  private static class Api21Impl {
    private Api21Impl() {}

    static void setCurrentVolume(VolumeProvider volumeProvider, int currentVolume) {
      volumeProvider.setCurrentVolume(currentVolume);
    }
  }
}
