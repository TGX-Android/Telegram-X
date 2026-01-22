/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.effect.DebugTraceUtil.COMPONENT_EXTERNAL_TEXTURE_MANAGER;
import static androidx.media3.effect.DebugTraceUtil.COMPONENT_VFP;
import static androidx.media3.effect.DebugTraceUtil.EVENT_QUEUE_FRAME;
import static androidx.media3.effect.DebugTraceUtil.EVENT_SIGNAL_EOS;
import static androidx.media3.effect.DebugTraceUtil.EVENT_SURFACE_TEXTURE_INPUT;
import static androidx.media3.effect.DebugTraceUtil.EVENT_SURFACE_TEXTURE_TRANSFORM_FIX;
import static java.lang.Math.abs;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Forwards externally produced frames that become available via a {@link SurfaceTexture} to an
 * {@link ExternalShaderProgram} for consumption.
 */
/* package */ final class ExternalTextureManager extends TextureManager {

  private static final String TAG = "ExtTexMgr";
  private static final String TIMER_THREAD_NAME = "ExtTexMgr:Timer";
  private static final int[] TRANSFORMATION_MATRIX_EXPECTED_ZERO_INDICES = {
    2, 3, 6, 7, 8, 9, 11, 14
  };
  // Some devices always allocate 1920x1088 buffers, regardless of video resolution.
  // When working around the implicit SurfaceTexture crop, add 1920 and 1088 to the set of
  // candidate buffer sizes.
  private static final int[] ADDITIONAL_CANDIDATE_BUFFER_SIZE_GUESSES = {1920, 1088};
  // In the worst case, we should be able to differentiate between numbers of the form
  // A / B and (A + 1) / (B + 1) where A and B are around video resolution.
  // For 8K, width = 7680.
  // abs(7679 / 7680 - 7680 / 7681) > 1e-8. We pick EPSILON = 1e-9.
  private static final float EPSILON = 1e-9f;

  /**
   * The time out in milliseconds after calling signalEndOfCurrentInputStream after which the input
   * stream is considered to have ended, even if not all expected frames have been received from the
   * decoder. This has been observed on some decoders.
   *
   * <p>Some emulator decoders are slower, hence using a longer timeout.
   */
  // LINT.IfChange(SURFACE_TEXTURE_TIMEOUT_MS)
  private static final long SURFACE_TEXTURE_TIMEOUT_MS = isRunningOnEmulator() ? 20_000 : 500;

  // LINT.ThenChange(
  // ../../../../../../../transformer/src/main/java/androidx/media3/transformer/Transformer.java:DEFAULT_MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)

  private final GlObjectsProvider glObjectsProvider;
  private @MonotonicNonNull ExternalShaderProgram externalShaderProgram;
  private final int externalTexId;
  private final Surface surface;
  private final SurfaceTexture surfaceTexture;
  private final float[] textureTransformMatrix;
  private final Queue<FrameInfo> pendingFrames;
  private final ScheduledExecutorService scheduledExecutorService;
  private final boolean experimentalAdjustSurfaceTextureTransformationMatrix;

  // Must be accessed on the GL thread.
  private int externalShaderProgramInputCapacity;
  private int availableFrameCount;
  private boolean currentInputStreamEnded;

  // The frame that is sent downstream and is not done processing yet.
  @Nullable private FrameInfo currentFrame;
  @Nullable private FrameInfo lastRegisteredFrame;
  private boolean automaticReregistration;

  @Nullable private Future<?> forceSignalEndOfStreamFuture;
  @Nullable private CountDownLatch releaseAllFramesLatch;

  private volatile boolean shouldRejectIncomingFrames;
  @Nullable private volatile RuntimeException releaseAllFramesException;

  /**
   * Creates a new instance. The caller's thread must have a current GL context.
   *
   * @param glObjectsProvider The {@link GlObjectsProvider} for using EGL and GLES.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor}.
   * @param automaticReregistration If {@code true}, the last {@linkplain
   *     #registerInputFrame(FrameInfo) registered frame} is repeated for subsequent input textures
   *     made available on the {@linkplain #getInputSurface() input Surface}. This means the user
   *     can call {@link #registerInputFrame(FrameInfo)} only once. Else, every input frame needs to
   *     be {@linkplain #registerInputFrame(FrameInfo) registered} before they are made available on
   *     the {@linkplain #getInputSurface() input Surface}.
   * @param experimentalAdjustSurfaceTextureTransformationMatrix if {@code true}, the {@link
   *     SurfaceTexture#getTransformMatrix(float[])} will be adjusted to remove the scale that cuts
   *     off a 1- or 2-texel border around the edge of a crop.
   * @throws VideoFrameProcessingException If a problem occurs while creating the external texture.
   */
  // The onFrameAvailableListener will not be invoked until the constructor returns.
  @SuppressWarnings("nullness:method.invocation.invalid")
  public ExternalTextureManager(
      GlObjectsProvider glObjectsProvider,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      boolean automaticReregistration,
      boolean experimentalAdjustSurfaceTextureTransformationMatrix)
      throws VideoFrameProcessingException {
    super(videoFrameProcessingTaskExecutor);
    this.glObjectsProvider = glObjectsProvider;
    this.automaticReregistration = automaticReregistration;
    this.experimentalAdjustSurfaceTextureTransformationMatrix =
        experimentalAdjustSurfaceTextureTransformationMatrix;
    try {
      externalTexId = GlUtil.createExternalTexture();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
    surfaceTexture = new SurfaceTexture(externalTexId);
    textureTransformMatrix = new float[16];
    pendingFrames = new ConcurrentLinkedQueue<>();
    scheduledExecutorService = Util.newSingleThreadScheduledExecutor(TIMER_THREAD_NAME);
    surfaceTexture.setOnFrameAvailableListener(
        unused ->
            videoFrameProcessingTaskExecutor.submit(
                () -> {
                  DebugTraceUtil.logEvent(COMPONENT_VFP, EVENT_SURFACE_TEXTURE_INPUT, C.TIME_UNSET);
                  if (ExternalTextureManager.this.automaticReregistration) {
                    pendingFrames.add(checkNotNull(lastRegisteredFrame));
                  }
                  if (shouldRejectIncomingFrames) {
                    surfaceTexture.updateTexImage();
                    pendingFrames.poll();
                    if (releaseAllFramesLatch != null && pendingFrames.isEmpty()) {
                      releaseAllFramesLatch.countDown();
                    }
                    return;
                  }

                  if (currentInputStreamEnded) {
                    restartForceSignalEndOfStreamTimer();
                  }
                  availableFrameCount++;
                  maybeQueueFrameToExternalShaderProgram();
                },
                // Ensures the available frame is consumed even if the task executor is flushed
                // before the submitted task is executed.
                /* isCancellable= */ false));
    surface = new Surface(surfaceTexture);
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code glShaderProgram} must be an {@link ExternalShaderProgram}.
   */
  @Override
  public void setSamplingGlShaderProgram(GlShaderProgram samplingGlShaderProgram) {
    checkState(samplingGlShaderProgram instanceof ExternalShaderProgram);
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          externalShaderProgramInputCapacity = 0;
          this.externalShaderProgram = (ExternalShaderProgram) samplingGlShaderProgram;
        });
  }

  @Override
  public void setDefaultBufferSize(int width, int height) {
    surfaceTexture.setDefaultBufferSize(width, height);
  }

  @Override
  public Surface getInputSurface() {
    return surface;
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          externalShaderProgramInputCapacity++;
          maybeQueueFrameToExternalShaderProgram();
        });
  }

  @Override
  public void onInputFrameProcessed(GlTextureInfo inputTexture) {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          currentFrame = null;
          if (currentInputStreamEnded && pendingFrames.isEmpty()) {
            // Reset because there could be further input streams after the current one ends.
            currentInputStreamEnded = false;
            checkNotNull(externalShaderProgram).signalEndOfCurrentInputStream();
            DebugTraceUtil.logEvent(
                COMPONENT_EXTERNAL_TEXTURE_MANAGER, EVENT_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
            cancelForceSignalEndOfStreamTimer();
          } else {
            maybeQueueFrameToExternalShaderProgram();
          }
        });
  }

  @Override
  public void setInputFrameInfo(FrameInfo inputFrameInfo, boolean automaticReregistration) {
    // Ignore inputFrameInfo when not automatically re-registering frames because it's also passed
    // to registerInputFrame.
    this.automaticReregistration = automaticReregistration;
    if (automaticReregistration) {
      lastRegisteredFrame = inputFrameInfo;
      surfaceTexture.setDefaultBufferSize(
          inputFrameInfo.format.width, inputFrameInfo.format.height);
    }
  }

  /**
   * Notifies the {@code ExternalTextureManager} that a frame with the given {@link FrameInfo} will
   * become available via the {@link SurfaceTexture} eventually.
   *
   * <p>Can be called on any thread. The caller must ensure that frames are registered in the
   * correct order.
   */
  @Override
  public void registerInputFrame(FrameInfo frame) {
    lastRegisteredFrame = frame;
    if (!automaticReregistration) {
      pendingFrames.add(frame);
    }
    videoFrameProcessingTaskExecutor.submit(() -> shouldRejectIncomingFrames = false);
  }

  /**
   * Returns the number of {@linkplain #registerInputFrame(FrameInfo) registered} frames that have
   * not been sent to the downstream {@link ExternalShaderProgram} yet.
   *
   * <p>Can be called on any thread.
   */
  @Override
  public int getPendingFrameCount() {
    return pendingFrames.size();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          if (automaticReregistration) {
            // We don't know how many frames are still pending in automatic mode, so reject further
            // input and signal end-of-stream once the current frame (if any) has been handled until
            // the next explicit registration.
            shouldRejectIncomingFrames = true;
          }
          if (pendingFrames.isEmpty() && currentFrame == null) {
            checkNotNull(externalShaderProgram).signalEndOfCurrentInputStream();
            DebugTraceUtil.logEvent(
                COMPONENT_EXTERNAL_TEXTURE_MANAGER, EVENT_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
            cancelForceSignalEndOfStreamTimer();
          } else {
            currentInputStreamEnded = true;
            restartForceSignalEndOfStreamTimer();
          }
        });
  }

  @Override
  public void release() {
    surfaceTexture.release();
    surface.release();
    scheduledExecutorService.shutdownNow();
  }

  @Override
  protected void flush() throws VideoFrameProcessingException {
    externalShaderProgramInputCapacity = 0;
    currentFrame = null;
    pendingFrames.clear();
    lastRegisteredFrame = null;
    super.flush();
  }

  @Override
  public void dropIncomingRegisteredFrames() {
    shouldRejectIncomingFrames = true;
  }

  @Override
  public void releaseAllRegisteredFrames() {
    // Blocks the calling thread until all the registered frames are received and released.
    CountDownLatch releaseAllFramesLatch = new CountDownLatch(1);
    this.releaseAllFramesLatch = releaseAllFramesLatch;
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          try {
            removeAllSurfaceTextureFrames();
          } catch (RuntimeException e) {
            releaseAllFramesException = e;
            Log.e(TAG, "Failed to remove texture frames", e);
            if (this.releaseAllFramesLatch != null) {
              this.releaseAllFramesLatch.countDown();
            }
          }
        });
    try {
      if (!releaseAllFramesLatch.await(SURFACE_TEXTURE_TIMEOUT_MS, MILLISECONDS)) {
        Log.w(TAG, "Timeout reached while waiting for latch to be unblocked.");
      }
    } catch (InterruptedException e) {
      // Not re-thrown to not crash frame processing. Frame process can likely continue even when
      // not all rendered frames arrive.
      Thread.currentThread().interrupt();
      Log.w(TAG, "Interrupted when waiting for MediaCodec frames to arrive.");
    }
    this.releaseAllFramesLatch = null;
    if (releaseAllFramesException != null) {
      throw releaseAllFramesException;
    }
  }

  private void restartForceSignalEndOfStreamTimer() {
    cancelForceSignalEndOfStreamTimer();
    forceSignalEndOfStreamFuture =
        scheduledExecutorService.schedule(
            () -> videoFrameProcessingTaskExecutor.submit(this::forceSignalEndOfStream),
            SURFACE_TEXTURE_TIMEOUT_MS,
            MILLISECONDS);
  }

  private void cancelForceSignalEndOfStreamTimer() {
    if (forceSignalEndOfStreamFuture != null) {
      forceSignalEndOfStreamFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
    forceSignalEndOfStreamFuture = null;
  }

  private void forceSignalEndOfStream() {
    if (availableFrameCount == pendingFrames.size()) {
      // All frames received from decoder. Do not force end of stream.
      return;
    }
    Log.w(
        TAG,
        Util.formatInvariant(
            "Forcing EOS after missing %d frames for %d ms, with available frame count: %d",
            pendingFrames.size(), SURFACE_TEXTURE_TIMEOUT_MS, availableFrameCount));
    // Reset because there could be further input streams after the current one ends.
    currentInputStreamEnded = false;
    currentFrame = null;
    shouldRejectIncomingFrames = true;

    // Frames could be made available while waiting for OpenGL to finish processing. That is,
    // time out is triggered while waiting for the downstream shader programs to process a frame,
    // when there are frames available on the SurfaceTexture. This has only been observed on
    // emulators.
    removeAllSurfaceTextureFrames();
    pendingFrames.clear();
    signalEndOfCurrentInputStream();
  }

  private void removeAllSurfaceTextureFrames() {
    while (availableFrameCount > 0) {
      availableFrameCount--;
      surfaceTexture.updateTexImage();
      pendingFrames.remove();
    }
    if (releaseAllFramesLatch != null && pendingFrames.isEmpty()) {
      releaseAllFramesLatch.countDown();
    }
  }

  private void maybeQueueFrameToExternalShaderProgram() {
    if (externalShaderProgramInputCapacity == 0
        || availableFrameCount == 0
        || currentFrame != null) {
      return;
    }

    surfaceTexture.updateTexImage();
    availableFrameCount--;

    FrameInfo currentFrame = pendingFrames.element();
    this.currentFrame = currentFrame;

    externalShaderProgramInputCapacity--;
    surfaceTexture.getTransformMatrix(textureTransformMatrix);
    long frameTimeNs = surfaceTexture.getTimestamp();
    long offsetToAddUs = currentFrame.offsetToAddUs;
    // Correct presentationTimeUs so that GlShaderPrograms don't see the stream offset.
    long presentationTimeUs = (frameTimeNs / 1000) + offsetToAddUs;
    if (experimentalAdjustSurfaceTextureTransformationMatrix) {
      removeSurfaceTextureScaleFromTransformMatrix(
          textureTransformMatrix,
          presentationTimeUs,
          currentFrame.format.width,
          currentFrame.format.height);
    }

    checkNotNull(externalShaderProgram).setTextureTransformMatrix(textureTransformMatrix);
    checkNotNull(externalShaderProgram)
        .queueInputFrame(
            glObjectsProvider,
            new GlTextureInfo(
                externalTexId,
                /* fboId= */ C.INDEX_UNSET,
                /* rboId= */ C.INDEX_UNSET,
                currentFrame.format.width,
                currentFrame.format.height),
            presentationTimeUs);
    checkStateNotNull(pendingFrames.remove());
    DebugTraceUtil.logEvent(COMPONENT_VFP, EVENT_QUEUE_FRAME, presentationTimeUs);
    // If the queued frame is the last frame, end of stream will be signaled onInputFrameProcessed.
  }

  /**
   * Adjusts textureTransformMatrix inplace to remove any scaling applied by {@link
   * SurfaceTexture#getTransformMatrix(float[])}. This method preserves cropping.
   *
   * <p>This method requires that textureTransformMatrix is a 4x4 column-major matrix that applies a
   * linear scale and transform to OpenGL coordinates of the form (s, t, 0, 1).
   *
   * @param textureTransformMatrix The matrix to be modified inplace.
   * @param presentationTimeUs The presentation time of the frame being processed.
   * @param visibleWidth The expected visible width in pixels of the texture.
   * @param visibleHeight The expected visible height in pixels of the texture.
   */
  private static void removeSurfaceTextureScaleFromTransformMatrix(
      float[] textureTransformMatrix,
      long presentationTimeUs,
      int visibleWidth,
      int visibleHeight) {
    boolean isMatrixUnexpected = false;
    isMatrixUnexpected |= (textureTransformMatrix.length != 16);
    for (int i : TRANSFORMATION_MATRIX_EXPECTED_ZERO_INDICES) {
      isMatrixUnexpected |= (abs(textureTransformMatrix[i]) > EPSILON);
    }
    isMatrixUnexpected |= (abs(textureTransformMatrix[10] - 1f) > EPSILON);
    isMatrixUnexpected |= (abs(textureTransformMatrix[15] - 1f) > EPSILON);
    int widthScaleIndex = C.INDEX_UNSET;
    int widthTranslationIndex = C.INDEX_UNSET;
    int heightScaleIndex = C.INDEX_UNSET;
    int heightTranslationIndex = C.INDEX_UNSET;

    if (abs(textureTransformMatrix[0]) > EPSILON && abs(textureTransformMatrix[5]) > EPSILON) {
      // 0 or 180 degree rotation. T maps width to width.
      widthScaleIndex = 0;
      widthTranslationIndex = 12;
      heightScaleIndex = 5;
      heightTranslationIndex = 13;
      isMatrixUnexpected |= (abs(textureTransformMatrix[1]) > EPSILON);
      isMatrixUnexpected |= (abs(textureTransformMatrix[4]) > EPSILON);
    } else if (abs(textureTransformMatrix[1]) > EPSILON
        && abs(textureTransformMatrix[4]) > EPSILON) {
      // 90 or 270 rotation. T swaps width and height.
      widthScaleIndex = 1;
      widthTranslationIndex = 13;
      heightScaleIndex = 4;
      heightTranslationIndex = 12;
      isMatrixUnexpected |= (abs(textureTransformMatrix[0]) > EPSILON);
      isMatrixUnexpected |= (abs(textureTransformMatrix[5]) > EPSILON);
    } else {
      isMatrixUnexpected = true;
    }
    if (isMatrixUnexpected) {
      DebugTraceUtil.logEvent(
          COMPONENT_EXTERNAL_TEXTURE_MANAGER,
          EVENT_SURFACE_TEXTURE_TRANSFORM_FIX,
          presentationTimeUs,
          /* extraFormat= */ "Unable to apply SurfaceTexture fix");
      return;
    }
    float widthScale = textureTransformMatrix[widthScaleIndex];
    float widthTranslation = textureTransformMatrix[widthTranslationIndex];
    if (abs(widthScale) + EPSILON < 1f) {
      // Applying a scale to the width means that some region of the texture must be cropped.
      // Try to guess what the scale would be if SurfaceTexture didn't trim a few more pixels, in
      // addition to the required crop.
      float adjustedWidthScale =
          Math.copySign(
              guessScaleWithoutSurfaceTextureTrim(abs(widthScale), visibleWidth), widthScale);
      float adjustedWidthTranslation = 0.5f * (widthScale - adjustedWidthScale) + widthTranslation;
      DebugTraceUtil.logEvent(
          COMPONENT_EXTERNAL_TEXTURE_MANAGER,
          EVENT_SURFACE_TEXTURE_TRANSFORM_FIX,
          presentationTimeUs,
          /* extraFormat= */ "Width scale adjusted.");
      textureTransformMatrix[widthScaleIndex] = adjustedWidthScale;
      // Update translation to preserve midpoint. T(0.5, 0, 0, 1) remains fixed.
      textureTransformMatrix[widthTranslationIndex] = adjustedWidthTranslation;
    }

    float heightScale = textureTransformMatrix[heightScaleIndex];
    float heightTranslation = textureTransformMatrix[heightTranslationIndex];
    if (abs(heightScale) + EPSILON < 1f) {
      // Applying a scale to the height means that some region of the texture must be cropped.
      // Try to guess what the scale would be if SurfaceTexture didn't didn't trim a few more
      // pixels, in addition to the required crop.
      float adjustedHeightScale =
          Math.copySign(
              guessScaleWithoutSurfaceTextureTrim(abs(heightScale), visibleHeight), heightScale);
      float adjustedHeightTranslation =
          0.5f * (heightScale - adjustedHeightScale) + heightTranslation;
      DebugTraceUtil.logEvent(
          COMPONENT_EXTERNAL_TEXTURE_MANAGER,
          EVENT_SURFACE_TEXTURE_TRANSFORM_FIX,
          presentationTimeUs,
          /* extraFormat= */ "Height scale adjusted.");
      textureTransformMatrix[heightScaleIndex] = adjustedHeightScale;
      // Update translation to preserve midpoint. T(0, 0.5, 0, 1) remains fixed.
      textureTransformMatrix[heightTranslationIndex] = adjustedHeightTranslation;
    }
  }

  /**
   * Guess what the 1-D texture coordinate scale would be if SurfaceTexture was cropping without
   * trimming a few extra pixels and stretching the image.
   *
   * <p>This method needs to guess:
   *
   * <ul>
   *   <li>bufferSize = texture buffer size in texels. This should be the parameter value {@code
   *       visibleLength}, rounded up to a near multiple of 2.
   *       <p>Maybe it's rounded up to a multiple of 16 because of H.264 macroblock sizes. Maybe
   *       it's rounded up to 128 because of SIMD instructions.
   *       <p>bufferSize cannot be read reliably via {@link
   *       android.opengl.GLES31#glGetTexLevelParameteriv(int, int, int, int[], int)} across
   *       devices.
   *       <p>bufferSize cannot be read reliably from the decoder's {@link
   *       android.media.MediaFormat} across decoder implementations.
   *   <li>trim = number of pixels trimmed by {@link SurfaceTexture} in addition to the cropped
   *       region required for buffer SIMD alignment. As of the time of writing, this will be 0, 1
   *       or 2.
   * </ul>
   *
   * <p>This method will use the guessed bufferSize and trim values that most closely approximate
   * surfaceTextureScale.
   *
   * @param surfaceTextureScale the absolute value of the scaling factor from {@link
   *     SurfaceTexture#getTransformMatrix(float[])}. It has the form {@code (visibleLength - trim)
   *     / bufferSize}.
   * @param visibleLength Expected size in pixels of the visible range.
   * @return Scale without trim, of the form visibleLength / bufferSize.
   */
  private static float guessScaleWithoutSurfaceTextureTrim(
      float surfaceTextureScale, int visibleLength) {
    int bestCandidateBufferSize = visibleLength;

    for (int align = 2; align <= 256; align *= 2) {
      int candidateBufferSize = ((visibleLength + align - 1) / align) * align;
      if (scoreForCandidateBufferSize(candidateBufferSize, surfaceTextureScale, visibleLength)
          < scoreForCandidateBufferSize(
              bestCandidateBufferSize, surfaceTextureScale, visibleLength)) {
        bestCandidateBufferSize = candidateBufferSize;
      }
    }
    for (int candidateBufferSize : ADDITIONAL_CANDIDATE_BUFFER_SIZE_GUESSES) {
      if (candidateBufferSize < visibleLength) {
        continue;
      }
      if (scoreForCandidateBufferSize(candidateBufferSize, surfaceTextureScale, visibleLength)
          < scoreForCandidateBufferSize(
              bestCandidateBufferSize, surfaceTextureScale, visibleLength)) {
        bestCandidateBufferSize = candidateBufferSize;
      }
    }
    if (scoreForCandidateBufferSize(bestCandidateBufferSize, surfaceTextureScale, visibleLength)
        > EPSILON) {
      // Best guess is too far off. Accept that we'll scale.
      return surfaceTextureScale;
    }
    return (float) visibleLength / bestCandidateBufferSize;
  }

  private static float scoreForCandidateBufferSize(
      int candidateBufferSize, float surfaceTextureScale, int visibleLength) {
    float bestScore = 1;
    for (int trimmedPixels = 0; trimmedPixels <= 2; trimmedPixels++) {
      float guess = ((float) visibleLength - trimmedPixels) / candidateBufferSize;
      if (abs(guess - surfaceTextureScale) < bestScore) {
        bestScore = abs(guess - surfaceTextureScale);
      }
    }
    return bestScore;
  }
}
