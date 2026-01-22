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

package androidx.media3.transformer;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Bitmap.Config.RGBA_1010102;
import static android.graphics.ColorSpace.Named.BT2020_HLG;
import static androidx.media3.common.C.COLOR_TRANSFER_HLG;
import static androidx.media3.common.ColorInfo.SDR_BT709_LIMITED;
import static androidx.media3.common.ColorInfo.isTransferHdr;
import static androidx.media3.common.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK;
import static androidx.media3.common.PlaybackException.ERROR_CODE_INVALID_STATE;
import static androidx.media3.common.PlaybackException.ERROR_CODE_SETUP_REQUIRED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.GlUtil.createRgb10A2Texture;
import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.common.util.Util.usToMs;
import static com.google.common.util.concurrent.Futures.immediateCancelledFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.widget.ImageView;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import androidx.media3.effect.MatrixTransformation;
import androidx.media3.effect.PassthroughShaderProgram;
import androidx.media3.effect.RgbMatrix;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.extractor.DefaultExtractorsFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Extracts decoded frames from {@link MediaItem}.
 *
 * <p>This class is experimental and will be renamed or removed in a future release.
 *
 * <p>Frame extractor instances must be accessed from a single application thread.
 *
 * <p>This class may produce incorrect or washed out colors, or images that have too high contrast
 * for inputs not covered by <a
 * href="https://cs.android.com/android/_/android/platform/cts/+/aaa242e5c26466cf245fa85ff8a7750378de9d72:tests/media/src/android/mediav2/cts/DecodeGlAccuracyTest.java;drc=d0d5ff338f8b84adf9066358bac435b1be3bbe61;l=534">testDecodeGlAccuracyRGB
 * CTS test</a>. That is:
 *
 * <ul>
 *   <li>Inputs of BT.601 limited range are likely to produce accurate output with either
 *       {@linkplain MediaCodecSelector#PREFER_SOFTWARE software} or {@linkplain
 *       MediaCodecSelector#DEFAULT hardware} decoders across a wide range of devices.
 *   <li>Other inputs are likely to produce accurate output when using {@linkplain
 *       MediaCodecSelector#DEFAULT hardware} decoders on devices that are launched with API 13 or
 *       later.
 *   <li>HDR inputs will produce a {@link Bitmap} with {@link ColorSpace.Named#BT2020_HLG}. There
 *       are no guarantees that an HLG {@link Bitmap} displayed in {@link ImageView} and an HLG
 *       video displayed in {@link SurfaceView} will look the same.
 *   <li>Depending on the device and input video, color inaccuracies can be mitigated with an
 *       appropriate {@link RgbMatrix} effect.
 * </ul>
 */
@UnstableApi
public final class ExperimentalFrameExtractor {

  /** Configuration for the frame extractor. */
  public static final class Configuration {

    /** A builder for {@link Configuration} instances. */
    public static final class Builder {
      private SeekParameters seekParameters;
      private MediaCodecSelector mediaCodecSelector;
      private boolean extractHdrFrames;

      /** Creates a new instance with default values. */
      public Builder() {
        seekParameters = SeekParameters.DEFAULT;
        // TODO: b/350498258 - Consider a switch to MediaCodecSelector.DEFAULT. Some hardware
        // MediaCodec decoders crash when flushing (seeking) and setVideoEffects is used. See also
        // b/362904942.
        mediaCodecSelector = MediaCodecSelector.PREFER_SOFTWARE;
        extractHdrFrames = false;
      }

      /**
       * Sets the parameters that control how seek operations are performed. Defaults to {@link
       * SeekParameters#DEFAULT}.
       *
       * @param seekParameters The {@link SeekParameters}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSeekParameters(SeekParameters seekParameters) {
        this.seekParameters = seekParameters;
        return this;
      }

      /**
       * Sets the {@linkplain MediaCodecSelector selector} of {@link MediaCodec} instances. Defaults
       * to {@link MediaCodecSelector#PREFER_SOFTWARE}.
       *
       * @param mediaCodecSelector The {@link MediaCodecSelector}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMediaCodecSelector(MediaCodecSelector mediaCodecSelector) {
        this.mediaCodecSelector = mediaCodecSelector;
        return this;
      }

      /**
       * Sets whether HDR {@link Frame#bitmap} should be extracted from HDR videos.
       *
       * <p>When set to {@code false}, extracted HDR frames will be tone-mapped to {@link
       * ColorSpace.Named#BT709}.
       *
       * <p>When set to {@code true}, extracted HDR frames will have {@link
       * Bitmap.Config#RGBA_1010102} and {@link ColorSpace.Named#BT2020_HLG}. Extracting HDR frames
       * is only supported on API 34+.
       *
       * <p>This flag has no effect when the input is SDR.
       *
       * <p>Defaults to {@code false}.
       *
       * @param extractHdrFrames Whether HDR frames should be returned.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      @RequiresApi(34)
      public Builder setExtractHdrFrames(boolean extractHdrFrames) {
        this.extractHdrFrames = extractHdrFrames;
        return this;
      }

      /** Builds a new {@link Configuration} instance. */
      public Configuration build() {
        return new Configuration(seekParameters, mediaCodecSelector, extractHdrFrames);
      }
    }

    /** The {@link SeekParameters}. */
    public final SeekParameters seekParameters;

    /** The {@link MediaCodecSelector}. */
    public final MediaCodecSelector mediaCodecSelector;

    /** Whether extracting HDR frames is requested. */
    public final boolean extractHdrFrames;

    private Configuration(
        SeekParameters seekParameters,
        MediaCodecSelector mediaCodecSelector,
        boolean extractHdrFrames) {
      this.seekParameters = seekParameters;
      this.mediaCodecSelector = mediaCodecSelector;
      this.extractHdrFrames = extractHdrFrames;
    }
  }

  /** Stores an extracted and decoded video frame. */
  public static final class Frame {

    /** The presentation timestamp of the extracted frame, in milliseconds. */
    public final long presentationTimeMs;

    /** The extracted frame contents. */
    public final Bitmap bitmap;

    private Frame(long presentationTimeMs, Bitmap bitmap) {
      this.presentationTimeMs = presentationTimeMs;
      this.bitmap = bitmap;
    }
  }

  private final ExoPlayer player;
  private final Handler playerApplicationThreadHandler;

  /**
   * An {@link AtomicBoolean} that indicates whether the frame being extracted requires decoding and
   * rendering, or if the new seek position resolves to the last extracted frame. Accessed on both
   * the {@linkplain ExoPlayer#getApplicationLooper() ExoPlayer application thread}, and the
   * ExoPlayer playback thread.
   */
  private final AtomicBoolean extractedFrameNeedsRendering;

  /**
   * A {@link CallbackToFutureAdapter.Completer} corresponding to the frame currently being
   * extracted. Accessed on both the {@linkplain ExoPlayer#getApplicationLooper() ExoPlayer
   * application thread}, and the video effects GL thread.
   */
  private final AtomicReference<CallbackToFutureAdapter.@NullableType Completer<Frame>>
      frameBeingExtractedCompleterAtomicReference;

  /**
   * A {@link ListenableFuture} that completes when all previous {@link #getFrame(long)} requests
   * complete. Upon completion, the result corresponds to the last request to {@link
   * #getFrame(long)}.
   */
  private ListenableFuture<Frame> lastRequestedFrameFuture;

  /**
   * The last {@link Frame} that was extracted successfully. Accessed on the {@linkplain
   * ExoPlayer#getApplicationLooper() ExoPlayer application thread}.
   */
  @Nullable private Frame lastExtractedFrame;

  /**
   * Creates an instance.
   *
   * @param context {@link Context}.
   * @param configuration The {@link Configuration} for this frame extractor.
   */
  public ExperimentalFrameExtractor(Context context, Configuration configuration) {
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory())
            .experimentalSetCodecsToParseWithinGopSampleDependencies(
                C.VIDEO_CODEC_FLAG_H264 | C.VIDEO_CODEC_FLAG_H265);
    player =
        new ExoPlayer.Builder(
                context,
                /* renderersFactory= */ (eventHandler,
                    videoRendererEventListener,
                    audioRendererEventListener,
                    textRendererOutput,
                    metadataRendererOutput) ->
                    new Renderer[] {
                      new FrameExtractorRenderer(
                          context,
                          configuration.mediaCodecSelector,
                          videoRendererEventListener,
                          /* toneMapHdrToSdr= */ !configuration.extractHdrFrames)
                    },
                mediaSourceFactory)
            .setSeekParameters(configuration.seekParameters)
            .build();
    player.addAnalyticsListener(new PlayerListener());
    playerApplicationThreadHandler = new Handler(player.getApplicationLooper());
    extractedFrameNeedsRendering = new AtomicBoolean();
    frameBeingExtractedCompleterAtomicReference = new AtomicReference<>(null);
    lastRequestedFrameFuture = immediateCancelledFuture();
  }

  /**
   * Sets a new {@link MediaItem}.
   *
   * <p>Changing between SDR and HDR {@link MediaItem}s is not supported when {@link
   * Configuration#extractHdrFrames} is true.
   *
   * @param mediaItem The {@link MediaItem} from which frames will be extracted.
   * @param effects The {@link List} of {@linkplain Effect video effects} to apply to the extracted
   *     video frames.
   */
  public void setMediaItem(MediaItem mediaItem, List<Effect> effects) {
    ListenableFuture<Frame> previousRequestedFrame = lastRequestedFrameFuture;
    // TODO: b/350498258 - Extracting the first frame is a workaround for ExoPlayer.setVideoEffects
    // returning incorrect timestamps if we seek the player before rendering starts from zero.
    lastRequestedFrameFuture =
        CallbackToFutureAdapter.getFuture(
            completer -> {
              previousRequestedFrame.addListener(
                  () -> {
                    frameBeingExtractedCompleterAtomicReference.set(completer);
                    lastExtractedFrame = null;
                    player.setVideoEffects(buildVideoEffects(effects));
                    player.setMediaItem(mediaItem);
                    player.setPlayWhenReady(false);
                    player.prepare();
                  },
                  playerApplicationThreadHandler::post);
              return "ExperimentalFrameExtractor.setMediaItem";
            });
  }

  /**
   * Extracts a representative {@link Frame} for the specified video position.
   *
   * @param positionMs The time position in the {@link MediaItem} for which a frame is extracted.
   * @return A {@link ListenableFuture} of the result.
   */
  public ListenableFuture<Frame> getFrame(long positionMs) {
    ListenableFuture<Frame> previousRequestedFrame = lastRequestedFrameFuture;
    ListenableFuture<Frame> frameListenableFuture =
        CallbackToFutureAdapter.getFuture(
            completer -> {
              Futures.addCallback(
                  previousRequestedFrame,
                  new FutureCallback<Frame>() {
                    @Override
                    public void onSuccess(Frame result) {
                      lastExtractedFrame = result;
                      processNext(positionMs, completer);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                      processNext(positionMs, completer);
                    }
                  },
                  playerApplicationThreadHandler::post);
              return "ExperimentalFrameExtractor.getFrame";
            });
    lastRequestedFrameFuture =
        Futures.whenAllComplete(lastRequestedFrameFuture, frameListenableFuture)
            .call(() -> Futures.getDone(frameListenableFuture), directExecutor());
    return frameListenableFuture;
  }

  private void processNext(long positionMs, CallbackToFutureAdapter.Completer<Frame> completer) {
    // Cancellation listener is invoked instantaneously if the returned future is already cancelled.
    AtomicBoolean cancelled = new AtomicBoolean(false);
    completer.addCancellationListener(() -> cancelled.set(true), directExecutor());
    if (cancelled.get()) {
      return;
    }
    @Nullable PlaybackException playerError;
    if (player.isReleased()) {
      playerError =
          new PlaybackException(
              "The player is already released", null, ERROR_CODE_FAILED_RUNTIME_CHECK);
    } else {
      playerError = player.getPlayerError();
    }
    if (playerError != null) {
      completer.setException(playerError);
    } else if (player.getCurrentMediaItem() == null) {
      completer.setException(
          new PlaybackException(
              "Player has no current item. Call setMediaItem before getFrame.",
              null,
              ERROR_CODE_SETUP_REQUIRED));
    } else {
      checkState(frameBeingExtractedCompleterAtomicReference.compareAndSet(null, completer));
      extractedFrameNeedsRendering.set(false);
      player.seekTo(positionMs);
    }
  }

  /**
   * Releases the underlying resources. This method must be called when the frame extractor is no
   * longer required. The frame extractor must not be used after calling this method.
   */
  public void release() {
    if (player.getApplicationLooper() == Looper.myLooper()) {
      player.release();
      return;
    }
    ConditionVariable waitForRelease = new ConditionVariable();
    playerApplicationThreadHandler.removeCallbacksAndMessages(null);
    playerApplicationThreadHandler.post(
        () -> {
          player.release();
          waitForRelease.open();
        });
    waitForRelease.blockUninterruptible();
  }

  @VisibleForTesting
  /* package */ ListenableFuture<@NullableType DecoderCounters> getDecoderCounters() {
    SettableFuture<@NullableType DecoderCounters> decoderCountersSettableFuture =
        SettableFuture.create();
    playerApplicationThreadHandler.post(
        () -> decoderCountersSettableFuture.set(player.getVideoDecoderCounters()));
    return decoderCountersSettableFuture;
  }

  private ImmutableList<Effect> buildVideoEffects(List<Effect> effects) {
    ImmutableList.Builder<Effect> listBuilder = new ImmutableList.Builder<>();
    listBuilder.addAll(effects);
    listBuilder.add(
        (MatrixTransformation)
            presentationTimeUs -> {
              Matrix mirrorY = new Matrix();
              mirrorY.setScale(/* sx= */ 1, /* sy= */ -1);
              return mirrorY;
            });
    listBuilder.add(new FrameReader());
    return listBuilder.build();
  }

  private final class PlayerListener implements AnalyticsListener {
    @Override
    public void onPlayerError(EventTime eventTime, PlaybackException error) {
      // Fail the next frame to be extracted. Errors will propagate to later pending requests via
      // Future callbacks.
      @Nullable
      CallbackToFutureAdapter.Completer<Frame> frameBeingExtractedCompleter =
          frameBeingExtractedCompleterAtomicReference.getAndSet(null);
      if (frameBeingExtractedCompleter != null) {
        frameBeingExtractedCompleter.setException(error);
      }
    }

    @Override
    public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
      // The player enters STATE_BUFFERING at the start of a seek.
      // At the end of a seek, the player enters STATE_READY after the video renderer position has
      // been reset, and the renderer reports that it's ready.
      if (state == Player.STATE_READY && !extractedFrameNeedsRendering.get()) {
        // If the seek resolves to the current position, the renderer position will not be reset
        // and extractedFrameNeedsRendering remains false. No frames are rendered. Repeat the
        // previously returned frame.
        CallbackToFutureAdapter.Completer<Frame> frameBeingExtractedCompleter =
            checkNotNull(frameBeingExtractedCompleterAtomicReference.getAndSet(null));
        frameBeingExtractedCompleter.set(checkNotNull(lastExtractedFrame));
      }
    }
  }

  private final class FrameReader implements GlEffect {
    @Override
    public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
        throws VideoFrameProcessingException {
      return new FrameReadingGlShaderProgram(context, useHdr);
    }
  }

  private final class FrameReadingGlShaderProgram extends PassthroughShaderProgram {
    private static final int BYTES_PER_PIXEL = 4;

    private final boolean useHdr;

    /** The visible portion of the frame. */
    private final ImmutableList<float[]> visiblePolygon =
        ImmutableList.of(
            new float[] {-1, -1, 0, 1},
            new float[] {-1, 1, 0, 1},
            new float[] {1, 1, 0, 1},
            new float[] {1, -1, 0, 1});

    private @MonotonicNonNull GlTextureInfo hlgTextureInfo;
    private @MonotonicNonNull GlProgram glProgram;

    private ByteBuffer byteBuffer;

    public FrameReadingGlShaderProgram(Context context, boolean useHdr)
        throws VideoFrameProcessingException {
      byteBuffer = ByteBuffer.allocateDirect(0);
      this.useHdr = useHdr;
      if (useHdr) {
        checkState(SDK_INT >= 34);
        String vertexShaderFilePath = "shaders/vertex_shader_transformation_es3.glsl";
        String fragmentShaderFilePath = "shaders/fragment_shader_oetf_es3.glsl";
        try {
          glProgram = new GlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);
        } catch (IOException | GlUtil.GlException e) {
          throw new VideoFrameProcessingException(e);
        }
        glProgram.setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix());
        glProgram.setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix());
        glProgram.setFloatsUniform("uRgbMatrix", GlUtil.create4x4IdentityMatrix());
        glProgram.setIntUniform("uOutputColorTransfer", COLOR_TRANSFER_HLG);
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.createVertexBuffer(visiblePolygon),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
      }
    }

    @Override
    public void queueInputFrame(
        GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
      ensureConfigured(glObjectsProvider, inputTexture.width, inputTexture.height);
      Bitmap bitmap;
      if (useHdr) {
        if (SDK_INT < 34 || hlgTextureInfo == null) {
          onError(
              ExoPlaybackException.createForUnexpected(
                  new IllegalArgumentException(), ERROR_CODE_INVALID_STATE));
          return;
        }
        try {
          GlUtil.focusFramebufferUsingCurrentContext(
              hlgTextureInfo.fboId, hlgTextureInfo.width, hlgTextureInfo.height);
          GlUtil.checkGlError();
          checkNotNull(glProgram).use();
          glProgram.setSamplerTexIdUniform(
              "uTexSampler", inputTexture.texId, /* texUnitIndex= */ 0);
          glProgram.bindAttributesAndUniforms();
          GLES20.glDrawArrays(
              GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* count= */ visiblePolygon.size());
          GlUtil.checkGlError();
          // For OpenGL format, internalFormat, type see the docs:
          // https://registry.khronos.org/OpenGL-Refpages/es3/html/glReadPixels.xhtml
          // https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glTexImage2D.xhtml
          GLES20.glReadPixels(
              /* x= */ 0,
              /* y= */ 0,
              hlgTextureInfo.width,
              hlgTextureInfo.height,
              /* format= */ GLES20.GL_RGBA,
              /* type= */ GLES30.GL_UNSIGNED_INT_2_10_10_10_REV,
              byteBuffer);
          GlUtil.checkGlError();
        } catch (GlUtil.GlException e) {
          onError(e);
          return;
        }
        bitmap =
            Bitmap.createBitmap(
                /* display= */ null,
                hlgTextureInfo.width,
                hlgTextureInfo.height,
                RGBA_1010102,
                /* hasAlpha= */ false,
                ColorSpace.get(BT2020_HLG));
      } else {
        try {
          GlUtil.focusFramebufferUsingCurrentContext(
              inputTexture.fboId, inputTexture.width, inputTexture.height);
          GlUtil.checkGlError();
          GLES20.glReadPixels(
              /* x= */ 0,
              /* y= */ 0,
              inputTexture.width,
              inputTexture.height,
              GLES20.GL_RGBA,
              GLES20.GL_UNSIGNED_BYTE,
              byteBuffer);
          GlUtil.checkGlError();
        } catch (GlUtil.GlException e) {
          onError(e);
          return;
        }
        // According to https://www.khronos.org/opengl/wiki/Pixel_Transfer#Endian_issues,
        // the colors will have the order RGBA in client memory. This is what the bitmap expects:
        // https://developer.android.com/reference/android/graphics/Bitmap.Config.
        bitmap = Bitmap.createBitmap(inputTexture.width, inputTexture.height, ARGB_8888);
      }
      bitmap.copyPixelsFromBuffer(byteBuffer);

      CallbackToFutureAdapter.Completer<Frame> frameBeingExtractedCompleter =
          checkNotNull(frameBeingExtractedCompleterAtomicReference.getAndSet(null));
      frameBeingExtractedCompleter.set(new Frame(usToMs(presentationTimeUs), bitmap));
      // Drop frame: do not call outputListener.onOutputFrameAvailable().
      // Block effects pipeline: do not call inputListener.onReadyToAcceptInputFrame().
      // The effects pipeline will unblock and receive new frames when flushed after a seek.
      getInputListener().onInputFrameProcessed(inputTexture);
    }

    private void ensureConfigured(GlObjectsProvider glObjectsProvider, int width, int height) {
      int pixelBufferSize = width * height * BYTES_PER_PIXEL;
      if (byteBuffer.capacity() != pixelBufferSize) {
        byteBuffer = ByteBuffer.allocateDirect(pixelBufferSize);
      }
      byteBuffer.clear();

      if (useHdr) {
        if (hlgTextureInfo == null
            || hlgTextureInfo.width != width
            || hlgTextureInfo.height != height) {
          try {
            if (hlgTextureInfo != null) {
              hlgTextureInfo.release();
            }
            int texId = createRgb10A2Texture(width, height);
            hlgTextureInfo = glObjectsProvider.createBuffersForTexture(texId, width, height);
          } catch (GlUtil.GlException e) {
            onError(e);
          }
        }
      }
    }
  }

  /** A custom MediaCodecVideoRenderer that renders only one frame per position reset. */
  private final class FrameExtractorRenderer extends MediaCodecVideoRenderer {
    private final boolean toneMapHdrToSdr;

    private boolean frameRenderedSinceLastPositionReset;
    private List<Effect> effectsFromPlayer;
    @Nullable private Effect rotation;

    public FrameExtractorRenderer(
        Context context,
        MediaCodecSelector mediaCodecSelector,
        VideoRendererEventListener videoRendererEventListener,
        boolean toneMapHdrToSdr) {
      super(
          new Builder(context)
              .setMediaCodecSelector(mediaCodecSelector)
              .setAllowedJoiningTimeMs(0)
              .setEventHandler(Util.createHandlerForCurrentOrMainLooper())
              .setEventListener(videoRendererEventListener)
              .setMaxDroppedFramesToNotify(0));
      this.toneMapHdrToSdr = toneMapHdrToSdr;
      effectsFromPlayer = ImmutableList.of();
    }

    @Override
    protected void onStreamChanged(
        Format[] formats,
        long startPositionUs,
        long offsetUs,
        MediaSource.MediaPeriodId mediaPeriodId)
        throws ExoPlaybackException {
      super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
      frameRenderedSinceLastPositionReset = false;
      setRotation(null);
    }

    @Override
    public void setVideoEffects(List<Effect> effects) {
      effectsFromPlayer = effects;
      setEffectsWithRotation();
    }

    @CallSuper
    @Override
    protected boolean maybeInitializeProcessingPipeline(Format format) throws ExoPlaybackException {
      if (isTransferHdr(format.colorInfo) && toneMapHdrToSdr) {
        // Setting the VideoSink format to SDR_BT709_LIMITED tone maps to SDR.
        format = format.buildUpon().setColorInfo(SDR_BT709_LIMITED).build();
      }
      return super.maybeInitializeProcessingPipeline(format);
    }

    @Override
    @Nullable
    protected DecoderReuseEvaluation onInputFormatChanged(FormatHolder formatHolder)
        throws ExoPlaybackException {
      if (formatHolder.format != null) {
        Format format = formatHolder.format;
        if (format.rotationDegrees != 0) {
          // Some decoders do not apply rotation. It's no extra cost to rotate with a GL matrix
          // transformation effect instead.
          // https://developer.android.com/reference/android/media/MediaCodec#transformations-when-rendering-onto-surface
          setRotation(
              new ScaleAndRotateTransformation.Builder()
                  .setRotationDegrees(360 - format.rotationDegrees)
                  .build());
          formatHolder.format = format.buildUpon().setRotationDegrees(0).build();
        }
      }
      return super.onInputFormatChanged(formatHolder);
    }

    private void setRotation(@Nullable Effect rotation) {
      this.rotation = rotation;
      setEffectsWithRotation();
    }

    private void setEffectsWithRotation() {
      ImmutableList.Builder<Effect> effectBuilder = new ImmutableList.Builder<>();
      if (rotation != null) {
        effectBuilder.add(rotation);
      }
      effectBuilder.addAll(effectsFromPlayer);
      super.setVideoEffects(effectBuilder.build());
    }

    @Override
    public boolean isReady() {
      // When using FrameReadingGlShaderProgram, frames will not be rendered to the output surface,
      // and VideoFrameRenderControl.onFrameAvailableForRendering will not be called. The base class
      // never becomes ready. Treat this renderer as ready if a frame has been rendered into the
      // effects pipeline. The renderer needs to become ready for ExoPlayer to enter STATE_READY.
      return frameRenderedSinceLastPositionReset;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
      if (!frameRenderedSinceLastPositionReset) {
        super.render(positionUs, elapsedRealtimeUs);
      }
    }

    @Override
    protected boolean processOutputBuffer(
        long positionUs,
        long elapsedRealtimeUs,
        @Nullable MediaCodecAdapter codec,
        @Nullable ByteBuffer buffer,
        int bufferIndex,
        int bufferFlags,
        int sampleCount,
        long bufferPresentationTimeUs,
        boolean isDecodeOnlyBuffer,
        boolean isLastBuffer,
        Format format)
        throws ExoPlaybackException {
      if (frameRenderedSinceLastPositionReset) {
        return false;
      }
      return super.processOutputBuffer(
          positionUs,
          elapsedRealtimeUs,
          codec,
          buffer,
          bufferIndex,
          bufferFlags,
          sampleCount,
          bufferPresentationTimeUs,
          isDecodeOnlyBuffer,
          isLastBuffer,
          format);
    }

    @Override
    protected void renderOutputBufferV21(
        MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
      if (frameRenderedSinceLastPositionReset) {
        // Do not skip this buffer to prevent the decoder from making more progress.
        return;
      }
      frameRenderedSinceLastPositionReset = true;
      super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
      frameRenderedSinceLastPositionReset = false;
      extractedFrameNeedsRendering.set(true);
      super.onPositionReset(positionUs, joining);
    }
  }
}
