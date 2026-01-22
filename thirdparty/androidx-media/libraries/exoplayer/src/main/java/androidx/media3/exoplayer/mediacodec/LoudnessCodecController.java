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
package androidx.media3.exoplayer.mediacodec;

import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.media.LoudnessCodecController.OnLoudnessCodecUpdateListener;
import android.media.MediaCodec;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.UnstableApi;
import java.util.HashSet;
import java.util.Iterator;

/** Wrapper class for the platform {@link android.media.LoudnessCodecController}. */
@RequiresApi(35)
@UnstableApi
public final class LoudnessCodecController {

  /** Interface to intercept and modify loudness parameters before applying them to the codec. */
  public interface LoudnessParameterUpdateListener {

    /** The default update listener returning an unmodified set of parameters. */
    LoudnessParameterUpdateListener DEFAULT = bundle -> bundle;

    /**
     * Returns the updated loudness parameters to be applied to the codec.
     *
     * @param parameters The suggested loudness parameters.
     * @return The updated loudness parameters.
     */
    Bundle onLoudnessParameterUpdate(Bundle parameters);
  }

  private final HashSet<MediaCodec> mediaCodecs;
  private final LoudnessParameterUpdateListener updateListener;

  @Nullable private android.media.LoudnessCodecController loudnessCodecController;

  /** Creates the loudness controller. */
  public LoudnessCodecController() {
    this(LoudnessParameterUpdateListener.DEFAULT);
  }

  /**
   * Creates the loudness controller.
   *
   * @param updateListener The {@link LoudnessParameterUpdateListener} to intercept and modify
   *     parameters.
   */
  public LoudnessCodecController(LoudnessParameterUpdateListener updateListener) {
    this.mediaCodecs = new HashSet<>();
    this.updateListener = updateListener;
  }

  /**
   * Configures the loudness controller with an audio session id.
   *
   * @param audioSessionId The audio session ID.
   */
  public void setAudioSessionId(int audioSessionId) {
    if (loudnessCodecController != null) {
      loudnessCodecController.close();
      loudnessCodecController = null;
    }
    android.media.LoudnessCodecController loudnessCodecController =
        android.media.LoudnessCodecController.create(
            audioSessionId,
            directExecutor(),
            new OnLoudnessCodecUpdateListener() {
              @Override
              public Bundle onLoudnessCodecUpdate(MediaCodec codec, Bundle parameters) {
                return updateListener.onLoudnessParameterUpdate(parameters);
              }
            });
    this.loudnessCodecController = loudnessCodecController;
    for (Iterator<MediaCodec> it = mediaCodecs.iterator(); it.hasNext(); ) {
      boolean registered = loudnessCodecController.addMediaCodec(it.next());
      if (!registered) {
        it.remove();
      }
    }
  }

  /**
   * Adds a codec to be configured by the loudness controller.
   *
   * @param mediaCodec A {@link MediaCodec}.
   */
  public void addMediaCodec(MediaCodec mediaCodec) {
    if (loudnessCodecController != null && !loudnessCodecController.addMediaCodec(mediaCodec)) {
      // Don't add codec if the existing loudness controller can't handle it.
      return;
    }
    checkState(mediaCodecs.add(mediaCodec));
  }

  /**
   * Removes a codec from being configured by the loudness controller.
   *
   * @param mediaCodec A {@link MediaCodec}.
   */
  public void removeMediaCodec(MediaCodec mediaCodec) {
    boolean removedCodec = mediaCodecs.remove(mediaCodec);
    if (removedCodec && loudnessCodecController != null) {
      loudnessCodecController.removeMediaCodec(mediaCodec);
    }
  }

  /** Releases the loudness controller. */
  public void release() {
    mediaCodecs.clear();
    if (loudnessCodecController != null) {
      loudnessCodecController.close();
    }
  }
}
