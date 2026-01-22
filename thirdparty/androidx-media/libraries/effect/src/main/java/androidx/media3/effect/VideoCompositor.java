/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.effect;

import androidx.media3.common.ColorInfo;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;

/**
 * Interface for a video compositor that combines frames from multiple input sources to produce
 * output frames.
 *
 * <p>Input and output are provided via OpenGL textures.
 *
 * <p>Methods may be called from any thread.
 */
@UnstableApi
public interface VideoCompositor extends GlTextureProducer {

  /** Listener for errors. */
  interface Listener {
    /**
     * Called when an exception occurs during asynchronous frame compositing.
     *
     * <p>If this is called, the calling {@link VideoCompositor} must immediately be {@linkplain
     * VideoCompositor#release() released}.
     */
    void onError(VideoFrameProcessingException exception);

    /** Called after {@link VideoCompositor} has output its final output frame. */
    void onEnded();
  }

  /**
   * Registers a new input source.
   *
   * @param inputIndex The index of the input source which could be used to determine the order of
   *     the input sources. The same index should to be used in {@link #queueInputTexture}. All
   *     inputs must be registered before {@linkplain #queueInputTexture(int, GlTextureProducer,
   *     GlTextureInfo, ColorInfo, long) queueing} textures.
   */
  void registerInputSource(int inputIndex);

  /**
   * Signals that no more frames will come from the upstream {@link GlTextureProducer.Listener}.
   *
   * @param inputIndex The index of the input source.
   */
  void signalEndOfInputSource(int inputIndex);

  /**
   * Queues an input texture to be composited.
   *
   * @param inputIndex The index of the input source, the same index used when {@linkplain
   *     #registerInputSource(int) registering the input source}.
   * @param textureProducer The source from where the {@code inputTexture} is produced.
   * @param inputTexture The {@link GlTextureInfo} to composite.
   * @param colorInfo The {@link ColorInfo} of {@code inputTexture}.
   * @param presentationTimeUs The presentation time of {@code inputTexture}, in microseconds.
   */
  void queueInputTexture(
      int inputIndex,
      GlTextureProducer textureProducer,
      GlTextureInfo inputTexture,
      ColorInfo colorInfo,
      long presentationTimeUs);

  /**
   * Releases all resources.
   *
   * <p>This {@link VideoCompositor} instance must not be used after this method is called.
   */
  void release();
}
