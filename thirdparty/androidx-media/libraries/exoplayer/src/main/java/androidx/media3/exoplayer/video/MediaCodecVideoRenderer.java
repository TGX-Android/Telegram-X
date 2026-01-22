/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer.video;

import static android.view.Display.DEFAULT_DISPLAY;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.DISCARD_REASON_VIDEO_MAX_RESOLUTION_EXCEEDED;
import static androidx.media3.exoplayer.DecoderReuseEvaluation.REUSE_RESULT_NO;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Pair;
import android.view.Display;
import android.view.Surface;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Timeline;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.container.ObuParser;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.DecoderReuseEvaluation.DecoderDiscardReasons;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.PlayerMessage.Target;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.video.VideoRendererEventListener.EventDispatcher;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.PriorityQueue;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Decodes and renders video using {@link MediaCodec}.
 *
 * <p>This renderer accepts the following messages sent via {@link ExoPlayer#createMessage(Target)}
 * on the playback thread:
 *
 * <ul>
 *   <li>Message with type {@link #MSG_SET_VIDEO_OUTPUT} to set the output. The message payload
 *       should be the target {@link Surface}, or null to clear the output. Other non-null payloads
 *       have the effect of clearing the output.
 *   <li>Message with type {@link #MSG_SET_VIDEO_OUTPUT_RESOLUTION} to set the output resolution.
 *       The message payload should be the output resolution in {@link Size}.
 *   <li>Message with type {@link #MSG_SET_SCALING_MODE} to set the video scaling mode. The message
 *       payload should be one of the integer scaling modes in {@link C.VideoScalingMode}. Note that
 *       the scaling mode only applies if the {@link Surface} targeted by this renderer is owned by
 *       a {@link android.view.SurfaceView}.
 *   <li>Message with type {@link #MSG_SET_CHANGE_FRAME_RATE_STRATEGY} to set the strategy used to
 *       call {@link Surface#setFrameRate}.
 *   <li>Message with type {@link #MSG_SET_VIDEO_FRAME_METADATA_LISTENER} to set a listener for
 *       metadata associated with frames being rendered. The message payload should be the {@link
 *       VideoFrameMetadataListener}, or null.
 * </ul>
 */
@UnstableApi
public class MediaCodecVideoRenderer extends MediaCodecRenderer
    implements VideoFrameReleaseControl.FrameTimingEvaluator {

  private static final String TAG = "MediaCodecVideoRenderer";
  private static final String KEY_CROP_LEFT = "crop-left";
  private static final String KEY_CROP_RIGHT = "crop-right";
  private static final String KEY_CROP_BOTTOM = "crop-bottom";
  private static final String KEY_CROP_TOP = "crop-top";

  // Long edge length in pixels for standard video formats, in decreasing in order.
  private static final int[] STANDARD_LONG_EDGE_VIDEO_PX =
      new int[] {1920, 1600, 1440, 1280, 960, 854, 640, 540, 480};

  /**
   * Scale factor for the initial maximum input size used to configure the codec in non-adaptive
   * playbacks. See {@link #getCodecMaxValues(MediaCodecInfo, Format, Format[])}.
   */
  private static final float INITIAL_FORMAT_MAX_INPUT_SIZE_SCALE_FACTOR = 1.5f;

  /** Magic frame render timestamp that indicates the EOS in tunneling mode. */
  private static final long TUNNELING_EOS_PRESENTATION_TIME_US = Long.MAX_VALUE;

  /** The minimum input buffer size for HEVC. */
  private static final int HEVC_MAX_INPUT_SIZE_THRESHOLD = 2 * 1024 * 1024;

  /** The earliest time threshold, in microseconds, after which a frame is considered late. */
  private static final long MIN_EARLY_US_LATE_THRESHOLD = -30_000L;

  /** The earliest time threshold, in microseconds, after which a frame is considered very late. */
  private static final long MIN_EARLY_US_VERY_LATE_THRESHOLD = -500_000L;

  /**
   * The offset from the {@link Timeline.Period} end duration in microseconds, after which input
   * buffers will be treated as if they are last.
   */
  private static final long OFFSET_FROM_PERIOD_END_TO_TREAT_AS_LAST_US = 100_000L;

  /**
   * The offset from {@link #getLastResetPositionUs()} in microseconds, before which input buffers
   * are not allowed to be dropped.
   *
   * <p>This value must be greater than the pre-roll distance used by common audio codecs, such as
   * 80ms used by Opus <a
   * href="https://opus-codec.org/docs/opus_in_isobmff.html#4.3.6.2">Encapsulation of Opus in ISO
   * Base Media File Format</a>
   */
  private static final long OFFSET_FROM_RESET_POSITION_TO_ALLOW_INPUT_BUFFER_DROPPING_US = 200_000L;

  /**
   * The maximum number of consecutive dropped input buffers that allow discarding frame headers.
   *
   * <p>Discarding input buffers of type {@link ObuParser#OBU_FRAME_HEADER} speeds up decoding by
   * not showing already-decoded frames. This is less beneficial than discarding {@link
   * ObuParser#OBU_FRAME} which reduces the total number of decoded frames.
   *
   * <p>Dropping too many consecutive input buffers reduces the update frequency of {@link
   * #shouldDropDecoderInputBuffers}, and can harm user experience.
   */
  private static final int MAX_CONSECUTIVE_DROPPED_INPUT_BUFFERS_COUNT_TO_DISCARD_HEADER = 0;

  private static boolean evaluatedDeviceNeedsSetOutputSurfaceWorkaround;
  private static boolean deviceNeedsSetOutputSurfaceWorkaround;

  private final Context context;
  private final boolean ownsVideoSink;
  private final EventDispatcher eventDispatcher;
  private final int maxDroppedFramesToNotify;
  private final boolean deviceNeedsNoPostProcessWorkaround;
  private final VideoFrameReleaseControl videoFrameReleaseControl;
  private final VideoFrameReleaseControl.FrameReleaseInfo videoFrameReleaseInfo;
  @Nullable private final Av1SampleDependencyParser av1SampleDependencyParser;

  /**
   * The earliest time threshold, in microseconds, after which decoder input buffers may be dropped.
   */
  private final long minEarlyUsToDropDecoderInput;

  private final PriorityQueue<Long> droppedDecoderInputBufferTimestamps;

  private @MonotonicNonNull CodecMaxValues codecMaxValues;
  private boolean codecNeedsSetOutputSurfaceWorkaround;
  private boolean codecHandlesHdr10PlusOutOfBandMetadata;
  private @MonotonicNonNull VideoSink videoSink;
  private boolean hasSetVideoSink;
  private @MonotonicNonNull List<Effect> videoEffects;
  @Nullable private Surface displaySurface;
  @Nullable private PlaceholderSurface placeholderSurface;
  private Size outputResolution;
  private boolean haveReportedFirstFrameRenderedForCurrentSurface;
  private @C.VideoScalingMode int scalingMode;
  private @C.VideoChangeFrameRateStrategy int changeFrameRateStrategy;
  private long droppedFrameAccumulationStartTimeMs;
  private int droppedFrames;
  private int consecutiveDroppedFrameCount;
  private int buffersInCodecCount;
  private long totalVideoFrameProcessingOffsetUs;
  private int videoFrameProcessingOffsetCount;
  private long lastFrameReleaseTimeNs;
  private VideoSize decodedVideoSize;
  @Nullable private VideoSize reportedVideoSize;
  private int rendererPriority;

  private boolean tunneling;
  private int tunnelingAudioSessionId;
  /* package */ @Nullable OnFrameRenderedListenerV23 tunnelingOnFrameRenderedListener;
  @Nullable private VideoFrameMetadataListener frameMetadataListener;
  private long startPositionUs;
  private long periodDurationUs;
  private boolean pendingVideoSinkInputStreamChange;

  private boolean shouldDropDecoderInputBuffers;
  private int consecutiveDroppedInputBufferCount;

  /** A builder to create {@link MediaCodecVideoRenderer} instances. */
  public static final class Builder {
    private final Context context;
    private boolean buildCalled;
    private MediaCodecSelector mediaCodecSelector;
    private MediaCodecAdapter.Factory codecAdapterFactory;
    private long allowedJoiningTimeMs;
    private boolean enableDecoderFallback;
    @Nullable private Handler eventHandler;
    @Nullable private VideoRendererEventListener eventListener;
    private int maxDroppedFramesToNotify;
    private float assumedMinimumCodecOperatingRate;
    @Nullable private VideoSink videoSink;
    private boolean parseAv1SampleDependencies;
    private long lateThresholdToDropDecoderInputUs;

    /**
     * Creates a new builder.
     *
     * @param context A context.
     */
    public Builder(Context context) {
      this.context = context;
      this.mediaCodecSelector = MediaCodecSelector.DEFAULT;
      this.codecAdapterFactory = MediaCodecAdapter.Factory.getDefault(context);
      this.assumedMinimumCodecOperatingRate = 30;
      this.lateThresholdToDropDecoderInputUs = C.TIME_UNSET;
    }

    /** Sets the {@link MediaCodecSelector decoder selector}. */
    @CanIgnoreReturnValue
    public Builder setMediaCodecSelector(MediaCodecSelector mediaCodecSelector) {
      this.mediaCodecSelector = mediaCodecSelector;
      return this;
    }

    /**
     * Sets the {@link MediaCodecAdapter.Factory} used to create {@link MediaCodecAdapter}
     * instances.
     */
    @CanIgnoreReturnValue
    public Builder setCodecAdapterFactory(MediaCodecAdapter.Factory codecAdapterFactory) {
      this.codecAdapterFactory = codecAdapterFactory;
      return this;
    }

    /**
     * Sets the maximum duration in milliseconds for which this video renderer can attempt to
     * seamlessly join an ongoing playback.
     */
    @CanIgnoreReturnValue
    public Builder setAllowedJoiningTimeMs(long allowedJoiningTimeMs) {
      this.allowedJoiningTimeMs = allowedJoiningTimeMs;
      return this;
    }

    /**
     * Sets whether to enable fallback to lower-priority decoders if decoder initialization fails.
     * This may result in using a decoder that is slower/less efficient than the primary decoder.
     */
    @CanIgnoreReturnValue
    public Builder setEnableDecoderFallback(boolean enableDecoderFallback) {
      this.enableDecoderFallback = enableDecoderFallback;
      return this;
    }

    /**
     * Sets a handler to use when delivering events to {@code eventListener}.
     *
     * <p>The {@link #setEventHandler event handler} and {@link #setEventListener event listener}
     * are linked in that both should be set to either {@code null} or non-{@code null} values.
     */
    @CanIgnoreReturnValue
    public Builder setEventHandler(@Nullable Handler eventHandler) {
      this.eventHandler = eventHandler;
      return this;
    }

    /**
     * Sets a listener for {@link VideoRendererEventListener} events.
     *
     * <p>The {@link #setEventHandler event handler} and {@link #setEventListener event listener}
     * are linked in that both should be set to either {@code null} or non-{@code null} values.
     */
    @CanIgnoreReturnValue
    public Builder setEventListener(@Nullable VideoRendererEventListener eventListener) {
      this.eventListener = eventListener;
      return this;
    }

    /**
     * Sets the maximum number of frames that can be dropped between invocations of {@link
     * VideoRendererEventListener#onDroppedFrames(int, long)}.
     */
    @CanIgnoreReturnValue
    public Builder setMaxDroppedFramesToNotify(int maxDroppedFramesToNotify) {
      this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
      return this;
    }

    /**
     * Sets a codec operating rate that all codecs instantiated by this renderer are assumed to meet
     * implicitly (i.e. without the operating rate being set explicitly using {@link
     * MediaFormat#KEY_OPERATING_RATE}).
     */
    @CanIgnoreReturnValue
    public Builder setAssumedMinimumCodecOperatingRate(float assumedMinimumCodecOperatingRate) {
      this.assumedMinimumCodecOperatingRate = assumedMinimumCodecOperatingRate;
      return this;
    }

    /**
     * Sets a {@link VideoSink} to consume the frames.
     *
     * <p>If a {@link VideoSink} is not set and effects are {@linkplain #MSG_SET_VIDEO_EFFECTS set},
     * a {@link VideoSink} produced by a {@link PlaybackVideoGraphWrapper} with its default
     * configuration will be used to apply effects and render the frames on the output.
     */
    @CanIgnoreReturnValue
    public Builder setVideoSink(@Nullable VideoSink videoSink) {
      this.videoSink = videoSink;
      return this;
    }

    /**
     * Sets whether {@link MimeTypes#VIDEO_AV1} bitstream parsing for sample dependency information
     * is enabled. Knowing which input frames are not depended on can speed up seeking and reduce
     * dropped frames.
     *
     * <p>Defaults to {@code false}.
     *
     * <p>This method is experimental and will be renamed or removed in a future release.
     */
    @CanIgnoreReturnValue
    public Builder experimentalSetParseAv1SampleDependencies(boolean parseAv1SampleDependencies) {
      this.parseAv1SampleDependencies = parseAv1SampleDependencies;
      return this;
    }

    /**
     * Sets the late threshold for rendered output buffers, in microseconds, after which decoder
     * input buffers may be dropped.
     *
     * <p>The default value is {@link C#TIME_UNSET} and therefore no input buffers will be dropped
     * due to this logic.
     *
     * <p>This method is experimental and will be renamed or removed in a future release.
     */
    @CanIgnoreReturnValue
    public Builder experimentalSetLateThresholdToDropDecoderInputUs(
        long lateThresholdToDropDecoderInputUs) {
      this.lateThresholdToDropDecoderInputUs = lateThresholdToDropDecoderInputUs;
      return this;
    }

    /**
     * Builds the {@link MediaCodecVideoRenderer}. Must only be called once per Builder instance.
     *
     * <p>Throws {@link IllegalStateException} if the {@link #setEventHandler event handler} and the
     * {@link #setEventListener event listener} are neither both {@code null} nor both non-{@code
     * null}.
     */
    public MediaCodecVideoRenderer build() {
      checkState(!buildCalled);
      checkState(
          (eventHandler == null && eventListener == null)
              || (eventHandler != null && eventListener != null));
      buildCalled = true;
      return new MediaCodecVideoRenderer(this);
    }
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
    this(new Builder(context).setMediaCodecSelector(mediaCodecSelector));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(
      Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs) {
    this(
        new Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedJoiningTimeMs));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        new Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(maxDroppedFramesToNotify));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        new Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(maxDroppedFramesToNotify));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecAdapter.Factory codecAdapterFactory,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        new Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(maxDroppedFramesToNotify));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecAdapter.Factory codecAdapterFactory,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      float assumedMinimumCodecOperatingRate) {
    this(
        new Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(maxDroppedFramesToNotify)
            .setAssumedMinimumCodecOperatingRate(assumedMinimumCodecOperatingRate));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecAdapter.Factory codecAdapterFactory,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      float assumedMinimumCodecOperatingRate,
      @Nullable VideoSinkProvider videoSinkProvider) {
    this(
        new Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(maxDroppedFramesToNotify)
            .setAssumedMinimumCodecOperatingRate(assumedMinimumCodecOperatingRate)
            .setVideoSink(
                videoSinkProvider == null ? null : videoSinkProvider.getSink(/* inputIndex= */ 0)));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public MediaCodecVideoRenderer(
      Context context,
      MediaCodecAdapter.Factory codecAdapterFactory,
      MediaCodecSelector mediaCodecSelector,
      long allowedJoiningTimeMs,
      boolean enableDecoderFallback,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      float assumedMinimumCodecOperatingRate,
      @Nullable VideoSink videoSink) {
    this(
        new Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setCodecAdapterFactory(codecAdapterFactory)
            .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(maxDroppedFramesToNotify)
            .setAssumedMinimumCodecOperatingRate(assumedMinimumCodecOperatingRate)
            .setVideoSink(videoSink));
  }

  /**
   * @param builder The {@link Builder} containing construction parameters.
   */
  protected MediaCodecVideoRenderer(Builder builder) {
    super(
        C.TRACK_TYPE_VIDEO,
        builder.codecAdapterFactory,
        builder.mediaCodecSelector,
        builder.enableDecoderFallback,
        builder.assumedMinimumCodecOperatingRate);
    this.context = builder.context.getApplicationContext();
    this.maxDroppedFramesToNotify = builder.maxDroppedFramesToNotify;
    this.videoSink = builder.videoSink;
    eventDispatcher = new EventDispatcher(builder.eventHandler, builder.eventListener);
    ownsVideoSink = videoSink == null;
    @SuppressWarnings("nullness:assignment")
    VideoFrameReleaseControl.@Initialized FrameTimingEvaluator thisRef = this;
    videoFrameReleaseControl =
        new VideoFrameReleaseControl(
            this.context, /* frameTimingEvaluator= */ thisRef, builder.allowedJoiningTimeMs);
    videoFrameReleaseInfo = new VideoFrameReleaseControl.FrameReleaseInfo();
    deviceNeedsNoPostProcessWorkaround = deviceNeedsNoPostProcessWorkaround();
    outputResolution = Size.UNKNOWN;
    scalingMode = C.VIDEO_SCALING_MODE_DEFAULT;
    changeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS;
    decodedVideoSize = VideoSize.UNKNOWN;
    tunnelingAudioSessionId = C.AUDIO_SESSION_ID_UNSET;
    reportedVideoSize = null;
    rendererPriority = C.PRIORITY_PLAYBACK;
    startPositionUs = C.TIME_UNSET;
    periodDurationUs = C.TIME_UNSET;
    av1SampleDependencyParser =
        builder.parseAv1SampleDependencies ? new Av1SampleDependencyParser() : null;
    droppedDecoderInputBufferTimestamps = new PriorityQueue<>();
    minEarlyUsToDropDecoderInput =
        builder.lateThresholdToDropDecoderInputUs != C.TIME_UNSET
            ? -builder.lateThresholdToDropDecoderInputUs
            : C.TIME_UNSET;
  }

  // FrameTimingEvaluator methods

  @Override
  public boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs) {
    return shouldForceRenderOutputBuffer(earlyUs, elapsedSinceLastReleaseUs);
  }

  @Override
  public boolean shouldDropFrame(long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
    return shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs, isLastFrame);
  }

  @Override
  public boolean shouldIgnoreFrame(
      long earlyUs,
      long positionUs,
      long elapsedRealtimeUs,
      boolean isLastFrame,
      boolean treatDroppedBuffersAsSkipped)
      throws ExoPlaybackException {
    if (minEarlyUsToDropDecoderInput != C.TIME_UNSET) {
      // TODO: b/161996553 - Remove the isAwayFromLastResetPosition check when audio pre-rolling
      // is implemented correctly. Audio codecs such as Opus require pre-roll samples to be decoded
      // and discarded on a seek. Depending on the audio decoder, the positionUs may jump forward
      // by the pre-roll duration. Do not drop more frames than necessary when this happens.
      boolean isAwayFromLastResetPosition =
          positionUs
              > getLastResetPositionUs()
                  + OFFSET_FROM_RESET_POSITION_TO_ALLOW_INPUT_BUFFER_DROPPING_US;
      shouldDropDecoderInputBuffers =
          isAwayFromLastResetPosition && earlyUs < minEarlyUsToDropDecoderInput;
    }
    return shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs, isLastFrame)
        && maybeDropBuffersToKeyframe(positionUs, treatDroppedBuffersAsSkipped);
  }

  // Renderer methods

  @Override
  public String getName() {
    return TAG;
  }

  /**
   * Returns the {@link Capabilities} of MediaCodecVideoRenderer for a given {@link Format}.
   *
   * @param context A context.
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format} for which to check the {@code MediaCodecVideoRenderer}'s
   *     support.
   * @return The {@link Capabilities} for this format.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  public static @Capabilities int supportsFormat(
      Context context, MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    return supportsFormatInternal(context, mediaCodecSelector, format);
  }

  @Override
  protected @Capabilities int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    return supportsFormatInternal(context, mediaCodecSelector, format);
  }

  private static @Capabilities int supportsFormatInternal(
      Context context, MediaCodecSelector mediaCodecSelector, Format format)
      throws DecoderQueryException {
    String mimeType = format.sampleMimeType;
    if (!MimeTypes.isVideo(mimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }
    @Nullable DrmInitData drmInitData = format.drmInitData;
    // Assume encrypted content requires secure decoders.
    boolean requiresSecureDecryption = drmInitData != null;
    List<MediaCodecInfo> decoderInfos =
        getDecoderInfos(
            context,
            mediaCodecSelector,
            format,
            requiresSecureDecryption,
            /* requiresTunnelingDecoder= */ false);
    if (requiresSecureDecryption && decoderInfos.isEmpty()) {
      // No secure decoders are available. Fall back to non-secure decoders.
      decoderInfos =
          getDecoderInfos(
              context,
              mediaCodecSelector,
              format,
              /* requiresSecureDecoder= */ false,
              /* requiresTunnelingDecoder= */ false);
    }
    if (decoderInfos.isEmpty()) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    }
    if (!supportsFormatDrm(format)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM);
    }
    // Check whether the first decoder supports the format. This is the preferred decoder for the
    // format's MIME type, according to the MediaCodecSelector.
    MediaCodecInfo decoderInfo = decoderInfos.get(0);
    boolean isFormatSupported = decoderInfo.isFormatSupported(format);
    boolean isPreferredDecoder = true;
    if (!isFormatSupported) {
      // Check whether any of the other decoders support the format.
      for (int i = 1; i < decoderInfos.size(); i++) {
        MediaCodecInfo otherDecoderInfo = decoderInfos.get(i);
        if (otherDecoderInfo.isFormatSupported(format)) {
          decoderInfo = otherDecoderInfo;
          isFormatSupported = true;
          isPreferredDecoder = false;
          break;
        }
      }
    }
    @C.FormatSupport
    int formatSupport = isFormatSupported ? C.FORMAT_HANDLED : C.FORMAT_EXCEEDS_CAPABILITIES;
    @AdaptiveSupport
    int adaptiveSupport =
        decoderInfo.isSeamlessAdaptationSupported(format)
            ? ADAPTIVE_SEAMLESS
            : ADAPTIVE_NOT_SEAMLESS;
    @HardwareAccelerationSupport
    int hardwareAccelerationSupport =
        decoderInfo.hardwareAccelerated
            ? HARDWARE_ACCELERATION_SUPPORTED
            : HARDWARE_ACCELERATION_NOT_SUPPORTED;
    @DecoderSupport
    int decoderSupport = isPreferredDecoder ? DECODER_SUPPORT_PRIMARY : DECODER_SUPPORT_FALLBACK;

    if (Util.SDK_INT >= 26
        && MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)
        && !Api26.doesDisplaySupportDolbyVision(context)) {
      decoderSupport = DECODER_SUPPORT_FALLBACK_MIMETYPE;
    }

    @TunnelingSupport int tunnelingSupport = TUNNELING_NOT_SUPPORTED;
    if (isFormatSupported) {
      List<MediaCodecInfo> tunnelingDecoderInfos =
          getDecoderInfos(
              context,
              mediaCodecSelector,
              format,
              requiresSecureDecryption,
              /* requiresTunnelingDecoder= */ true);
      if (!tunnelingDecoderInfos.isEmpty()) {
        MediaCodecInfo tunnelingDecoderInfo =
            MediaCodecUtil.getDecoderInfosSortedByFormatSupport(tunnelingDecoderInfos, format)
                .get(0);
        if (tunnelingDecoderInfo.isFormatSupported(format)
            && tunnelingDecoderInfo.isSeamlessAdaptationSupported(format)) {
          tunnelingSupport = TUNNELING_SUPPORTED;
        }
      }
    }

    return RendererCapabilities.create(
        formatSupport,
        adaptiveSupport,
        tunnelingSupport,
        hardwareAccelerationSupport,
        decoderSupport);
  }

  @Override
  protected List<MediaCodecInfo> getDecoderInfos(
      MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder)
      throws DecoderQueryException {
    return MediaCodecUtil.getDecoderInfosSortedByFormatSupport(
        getDecoderInfos(context, mediaCodecSelector, format, requiresSecureDecoder, tunneling),
        format);
  }

  // Other methods

  /**
   * Returns a list of decoders that can decode media in the specified format, in the priority order
   * specified by the {@link MediaCodecSelector}. Note that since the {@link MediaCodecSelector}
   * only has access to {@link Format#sampleMimeType}, the list is not ordered to account for
   * whether each decoder supports the details of the format (e.g., taking into account the format's
   * profile, level, resolution and so on). {@link
   * MediaCodecUtil#getDecoderInfosSortedByFormatSupport} can be used to further sort the list into
   * an order where decoders that fully support the format come first.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format} for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @param requiresTunnelingDecoder Whether a tunneling decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  private static List<MediaCodecInfo> getDecoderInfos(
      Context context,
      MediaCodecSelector mediaCodecSelector,
      Format format,
      boolean requiresSecureDecoder,
      boolean requiresTunnelingDecoder)
      throws DecoderQueryException {
    if (format.sampleMimeType == null) {
      return ImmutableList.of();
    }
    if (Util.SDK_INT >= 26
        && MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)
        && !Api26.doesDisplaySupportDolbyVision(context)) {
      List<MediaCodecInfo> alternativeDecoderInfos =
          MediaCodecUtil.getAlternativeDecoderInfos(
              mediaCodecSelector, format, requiresSecureDecoder, requiresTunnelingDecoder);
      if (!alternativeDecoderInfos.isEmpty()) {
        return alternativeDecoderInfos;
      }
    }
    return MediaCodecUtil.getDecoderInfosSoftMatch(
        mediaCodecSelector, format, requiresSecureDecoder, requiresTunnelingDecoder);
  }

  @RequiresApi(26)
  private static final class Api26 {
    public static boolean doesDisplaySupportDolbyVision(Context context) {
      boolean supportsDolbyVision = false;
      DisplayManager displayManager =
          (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
      Display display =
          (displayManager != null) ? displayManager.getDisplay(DEFAULT_DISPLAY) : null;
      if (display != null && display.isHdr()) {
        int[] supportedHdrTypes = display.getHdrCapabilities().getSupportedHdrTypes();
        for (int hdrType : supportedHdrTypes) {
          if (hdrType == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
            supportsDolbyVision = true;
            break;
          }
        }
      }
      return supportsDolbyVision;
    }
  }

  @Override
  protected void onInit() {
    super.onInit();
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream)
      throws ExoPlaybackException {
    super.onEnabled(joining, mayRenderStartOfStream);
    boolean tunneling = getConfiguration().tunneling;
    checkState(!tunneling || tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET);
    if (this.tunneling != tunneling) {
      this.tunneling = tunneling;
      releaseCodec();
    }
    eventDispatcher.enabled(decoderCounters);
    // The video sink can only be enabled the first time the renderer is enabled, or after it has
    // been reset.
    if (!hasSetVideoSink) {
      if (videoEffects != null && videoSink == null) {
        PlaybackVideoGraphWrapper playbackVideoGraphWrapper =
            new PlaybackVideoGraphWrapper.Builder(context, videoFrameReleaseControl)
                .setClock(getClock())
                .build();
        playbackVideoGraphWrapper.setTotalVideoInputCount(1);
        videoSink = playbackVideoGraphWrapper.getSink(/* inputIndex= */ 0);
      }
      hasSetVideoSink = true;
    }
    if (videoSink != null) {
      // Configure the VideoSink every time the renderer is enabled, in case the parameters have
      // been overridden by another renderer. Also configure the VideoSink with the parameters that
      // have been set on the renderer before creating the VideoSink.
      videoSink.setListener(
          new VideoSink.Listener() {
            @Override
            public void onFirstFrameRendered(VideoSink videoSink) {
              if (displaySurface != null) {
                notifyRenderedFirstFrame();
              }
            }

            @Override
            public void onFrameDropped(VideoSink videoSink) {
              if (displaySurface != null) {
                updateDroppedBufferCounters(
                    /* droppedInputBufferCount= */ 0, /* droppedDecoderBufferCount= */ 1);
              }
            }

            @Override
            public void onVideoSizeChanged(VideoSink videoSink, VideoSize videoSize) {
              // TODO: b/292111083 - Report video size change to app. Video size reporting is
              //  removed at the moment to ensure the first frame is rendered, and the video is
              //  rendered after switching on/off the screen.
            }

            @Override
            public void onError(
                VideoSink videoSink, VideoSink.VideoSinkException videoSinkException) {
              setPendingPlaybackException(
                  createRendererException(
                      videoSinkException,
                      videoSinkException.format,
                      PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED));
            }
          },
          // Pass a direct executor since the callback handling involves posting on the app looper
          // again, so there's no need to do two hops.
          directExecutor());
      if (frameMetadataListener != null) {
        videoSink.setVideoFrameMetadataListener(frameMetadataListener);
      }
      if (displaySurface != null && !outputResolution.equals(Size.UNKNOWN)) {
        videoSink.setOutputSurfaceInfo(displaySurface, outputResolution);
      }
      videoSink.setChangeFrameRateStrategy(changeFrameRateStrategy);
      videoSink.setPlaybackSpeed(getPlaybackSpeed());
      if (videoEffects != null) {
        videoSink.setVideoEffects(videoEffects);
      }
      videoSink.onRendererEnabled(mayRenderStartOfStream);
      @Nullable WakeupListener wakeupListener = getWakeupListener();
      if (wakeupListener != null) {
        videoSink.setWakeupListener(wakeupListener);
      }
    } else {
      videoFrameReleaseControl.setClock(getClock());
      videoFrameReleaseControl.onEnabled(mayRenderStartOfStream);
    }
  }

  @Override
  public void enableMayRenderStartOfStream() {
    if (videoSink != null) {
      videoSink.enableMayRenderStartOfStream();
    } else {
      videoFrameReleaseControl.allowReleaseFirstFrameBeforeStarted();
    }
  }

  @Override
  protected void onStreamChanged(
      Format[] formats,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId);
    if (this.startPositionUs == C.TIME_UNSET) {
      this.startPositionUs = startPositionUs;
    }
    updatePeriodDurationUs(mediaPeriodId);
  }

  private void updatePeriodDurationUs(MediaSource.MediaPeriodId mediaPeriodId) {
    Timeline timeline = getTimeline();
    if (timeline.isEmpty()) {
      periodDurationUs = C.TIME_UNSET;
      return;
    }
    periodDurationUs =
        timeline
            .getPeriodByUid(checkNotNull(mediaPeriodId).periodUid, new Timeline.Period())
            .getDurationUs();
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
    if (videoSink != null) {
      if (!joining) {
        // Flush the video sink first to ensure it stops reading textures that will be owned by
        // MediaCodec once the codec is flushed.
        videoSink.flush(/* resetPosition= */ true);
      }
      videoSink.setStreamTimestampInfo(
          getOutputStreamStartPositionUs(), getBufferTimestampAdjustmentUs());
      pendingVideoSinkInputStreamChange = true;
    }
    super.onPositionReset(positionUs, joining);
    if (videoSink == null) {
      videoFrameReleaseControl.reset();
    }
    if (joining) {
      // Don't render next frame immediately to let the codec catch up with the playback position
      // first. This prevents a stuttering effect caused by showing the first frame and then
      // dropping many of the subsequent frames during the catch up phase.
      if (videoSink != null) {
        videoSink.join(/* renderNextFrameImmediately= */ false);
      } else {
        videoFrameReleaseControl.join(/* renderNextFrameImmediately= */ false);
      }
    }
    maybeSetupTunnelingForFirstFrame();
    consecutiveDroppedFrameCount = 0;
  }

  @Override
  public boolean isEnded() {
    return super.isEnded() && (videoSink == null || videoSink.isEnded());
  }

  @Override
  public boolean isReady() {
    boolean rendererOtherwiseReady = super.isReady();
    if (videoSink != null) {
      return videoSink.isReady(rendererOtherwiseReady);
    }
    if (rendererOtherwiseReady && (getCodec() == null || tunneling)) {
      // Not releasing frames.
      return true;
    }
    return videoFrameReleaseControl.isReady(rendererOtherwiseReady);
  }

  @Override
  protected void onStarted() {
    super.onStarted();
    droppedFrames = 0;
    long elapsedRealtimeMs = getClock().elapsedRealtime();
    droppedFrameAccumulationStartTimeMs = elapsedRealtimeMs;
    totalVideoFrameProcessingOffsetUs = 0;
    videoFrameProcessingOffsetCount = 0;
    if (videoSink != null) {
      videoSink.onRendererStarted();
    } else {
      videoFrameReleaseControl.onStarted();
    }
  }

  @Override
  protected void onStopped() {
    maybeNotifyDroppedFrames();
    maybeNotifyVideoFrameProcessingOffset();
    if (videoSink != null) {
      videoSink.onRendererStopped();
    } else {
      videoFrameReleaseControl.onStopped();
    }
    super.onStopped();
  }

  @Override
  protected void onDisabled() {
    reportedVideoSize = null;
    periodDurationUs = C.TIME_UNSET;
    if (videoSink != null) {
      videoSink.onRendererDisabled();
    } else {
      videoFrameReleaseControl.onDisabled();
    }
    maybeSetupTunnelingForFirstFrame();
    haveReportedFirstFrameRenderedForCurrentSurface = false;
    tunnelingOnFrameRenderedListener = null;
    try {
      super.onDisabled();
    } finally {
      eventDispatcher.disabled(decoderCounters);
      eventDispatcher.videoSizeChanged(VideoSize.UNKNOWN);
    }
  }

  @Override
  protected void onReset() {
    try {
      super.onReset();
    } finally {
      hasSetVideoSink = false;
      startPositionUs = C.TIME_UNSET;
      releasePlaceholderSurface();
    }
  }

  @Override
  protected void onRelease() {
    super.onRelease();
    if (videoSink != null && ownsVideoSink) {
      videoSink.release();
    }
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    switch (messageType) {
      case MSG_SET_VIDEO_OUTPUT:
        setOutput(message);
        break;
      case MSG_SET_SCALING_MODE:
        scalingMode = (int) checkNotNull(message);
        @Nullable MediaCodecAdapter codec = getCodec();
        if (codec != null) {
          codec.setVideoScalingMode(scalingMode);
        }
        break;
      case MSG_SET_CHANGE_FRAME_RATE_STRATEGY:
        changeFrameRateStrategy = (int) checkNotNull(message);
        if (videoSink != null) {
          videoSink.setChangeFrameRateStrategy(changeFrameRateStrategy);
        } else {
          videoFrameReleaseControl.setChangeFrameRateStrategy(changeFrameRateStrategy);
        }
        break;
      case MSG_SET_VIDEO_FRAME_METADATA_LISTENER:
        frameMetadataListener = (VideoFrameMetadataListener) checkNotNull(message);
        if (videoSink != null) {
          videoSink.setVideoFrameMetadataListener(frameMetadataListener);
        }
        break;
      case MSG_SET_AUDIO_SESSION_ID:
        int tunnelingAudioSessionId = (int) checkNotNull(message);
        if (this.tunnelingAudioSessionId != tunnelingAudioSessionId) {
          this.tunnelingAudioSessionId = tunnelingAudioSessionId;
          if (tunneling) {
            releaseCodec();
          }
        }
        break;
      case MSG_SET_VIDEO_EFFECTS:
        @SuppressWarnings("unchecked")
        List<Effect> videoEffects = (List<Effect>) checkNotNull(message);
        setVideoEffects(videoEffects);
        break;
      case MSG_SET_VIDEO_OUTPUT_RESOLUTION:
        Size outputResolution = (Size) checkNotNull(message);
        if (outputResolution.getWidth() != 0 && outputResolution.getHeight() != 0) {
          this.outputResolution = outputResolution;
          if (videoSink != null) {
            videoSink.setOutputSurfaceInfo(checkStateNotNull(displaySurface), outputResolution);
          }
        }
        break;
      case MSG_SET_PRIORITY:
        rendererPriority = (int) checkNotNull(message);
        updateCodecImportance();
        break;
      case MSG_TRANSFER_RESOURCES:
        {
          Surface surface = this.displaySurface;
          setOutput(null);
          ((MediaCodecVideoRenderer) checkNotNull(message))
              .handleMessage(MSG_SET_VIDEO_OUTPUT, surface);
        }
        break;
      default:
        super.handleMessage(messageType, message);
    }
  }

  private void setOutput(@Nullable Object output) throws ExoPlaybackException {
    // Handle unsupported (i.e., non-Surface) outputs by clearing the display surface.
    @Nullable Surface displaySurface = output instanceof Surface ? (Surface) output : null;

    if (this.displaySurface != displaySurface) {
      this.displaySurface = displaySurface;
      if (videoSink == null) {
        videoFrameReleaseControl.setOutputSurface(displaySurface);
      }
      haveReportedFirstFrameRenderedForCurrentSurface = false;

      @State int state = getState();
      @Nullable MediaCodecAdapter codec = getCodec();
      if (codec != null && videoSink == null) {
        MediaCodecInfo codecInfo = checkNotNull(getCodecInfo());
        boolean canUpdateSurface = hasSurfaceForCodec(codecInfo);
        if (Util.SDK_INT >= 23 && canUpdateSurface && !codecNeedsSetOutputSurfaceWorkaround) {
          setOutputSurface(codec, getSurfaceForCodec(codecInfo));
        } else {
          releaseCodec();
          maybeInitCodecOrBypass();
        }
      }
      if (displaySurface != null) {
        // If we know the video size, report it again immediately.
        maybeRenotifyVideoSizeChanged();
      } else {
        // The display surface has been removed.
        reportedVideoSize = null;
        if (videoSink != null) {
          videoSink.clearOutputSurfaceInfo();
        }
      }
      if (state == STATE_STARTED) {
        // We want to "join" playback to prevent an intermediate buffering state in the player
        // before we rendered the new first frame. Since there is no reason to believe the next
        // frame is delayed and the renderer needs to catch up, we still request to render the
        // next frame as soon as possible.
        if (videoSink != null) {
          videoSink.join(/* renderNextFrameImmediately= */ true);
        } else {
          videoFrameReleaseControl.join(/* renderNextFrameImmediately= */ true);
        }
      }
      maybeSetupTunnelingForFirstFrame();
    } else if (displaySurface != null) {
      // The display surface is set and unchanged. If we know the video size and/or have already
      // rendered to the display surface, report these again immediately.
      maybeRenotifyVideoSizeChanged();
      maybeRenotifyRenderedFirstFrame();
    }
  }

  @Override
  protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
    return hasSurfaceForCodec(codecInfo);
  }

  @Override
  protected boolean getCodecNeedsEosPropagation() {
    // Since API 23, onFrameRenderedListener allows for detection of the renderer EOS.
    return tunneling && Util.SDK_INT < 23;
  }

  @Override
  protected MediaCodecAdapter.Configuration getMediaCodecConfiguration(
      MediaCodecInfo codecInfo,
      Format format,
      @Nullable MediaCrypto crypto,
      float codecOperatingRate) {
    String codecMimeType = codecInfo.codecMimeType;
    codecMaxValues = getCodecMaxValues(codecInfo, format, getStreamFormats());
    MediaFormat mediaFormat =
        getMediaFormat(
            format,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunneling ? tunnelingAudioSessionId : C.AUDIO_SESSION_ID_UNSET);
    Surface codecSurface = getSurfaceForCodec(codecInfo);
    maybeSetKeyAllowFrameDrop(mediaFormat);
    return MediaCodecAdapter.Configuration.createForVideoDecoding(
        codecInfo, mediaFormat, format, codecSurface, crypto);
  }

  @SuppressWarnings("InlinedApi") // The API level is checked in the utility method
  private void maybeSetKeyAllowFrameDrop(MediaFormat mediaFormat) {
    if (videoSink != null && !Util.isFrameDropAllowedOnSurfaceInput(context)) {
      mediaFormat.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 0);
    }
  }

  @Override
  protected DecoderReuseEvaluation canReuseCodec(
      MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
    DecoderReuseEvaluation evaluation = codecInfo.canReuseCodec(oldFormat, newFormat);

    @DecoderDiscardReasons int discardReasons = evaluation.discardReasons;
    CodecMaxValues codecMaxValues = checkNotNull(this.codecMaxValues);
    if (newFormat.width > codecMaxValues.width || newFormat.height > codecMaxValues.height) {
      discardReasons |= DISCARD_REASON_VIDEO_MAX_RESOLUTION_EXCEEDED;
    }
    if (getMaxInputSize(codecInfo, newFormat) > codecMaxValues.inputSize) {
      discardReasons |= DISCARD_REASON_MAX_INPUT_SIZE_EXCEEDED;
    }

    return new DecoderReuseEvaluation(
        codecInfo.name,
        oldFormat,
        newFormat,
        discardReasons != 0 ? REUSE_RESULT_NO : evaluation.result,
        discardReasons);
  }

  @CallSuper
  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (videoSink != null) {
      try {
        // Drain the sink to make room for a new input frame.
        videoSink.render(positionUs, elapsedRealtimeUs);
      } catch (VideoSink.VideoSinkException e) {
        throw createRendererException(
            e, e.format, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED);
      }
    }
    super.render(positionUs, elapsedRealtimeUs);
  }

  @CallSuper
  @Override
  protected void resetCodecStateForFlush() {
    super.resetCodecStateForFlush();
    droppedDecoderInputBufferTimestamps.clear();
    shouldDropDecoderInputBuffers = false;
    buffersInCodecCount = 0;
    consecutiveDroppedInputBufferCount = 0;
    if (av1SampleDependencyParser != null) {
      av1SampleDependencyParser.reset();
    }
  }

  @Override
  public void setPlaybackSpeed(float currentPlaybackSpeed, float targetPlaybackSpeed)
      throws ExoPlaybackException {
    super.setPlaybackSpeed(currentPlaybackSpeed, targetPlaybackSpeed);
    if (videoSink != null) {
      videoSink.setPlaybackSpeed(currentPlaybackSpeed);
    } else {
      videoFrameReleaseControl.setPlaybackSpeed(currentPlaybackSpeed);
    }
  }

  /**
   * Returns a maximum input size for a given codec and format.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The format.
   * @return A maximum input size in bytes, or {@link Format#NO_VALUE} if a maximum could not be
   *     determined.
   */
  public static int getCodecMaxInputSize(MediaCodecInfo codecInfo, Format format) {
    int width = format.width;
    int height = format.height;
    if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
      // We can't infer a maximum input size without video dimensions.
      return Format.NO_VALUE;
    }

    String sampleMimeType = checkNotNull(format.sampleMimeType);
    if (MimeTypes.VIDEO_DOLBY_VISION.equals(sampleMimeType)) {
      // Dolby vision can be a wrapper around H.264, H.265 or AV1. We assume it's wrapping H.265 by
      // default because it's the common case, and because some devices may fail to allocate the
      // codec when the larger buffer size required for H.264/AV1 is requested. We size buffers
      // for H.264/AV1 only if the format contains sufficient information for us to determine
      // unambiguously that it's a H.264/AV1 based profile.
      sampleMimeType = MimeTypes.VIDEO_H265;
      @Nullable
      Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
      if (codecProfileAndLevel != null) {
        int profile = codecProfileAndLevel.first;
        if (profile == CodecProfileLevel.DolbyVisionProfileDvavSe
            || profile == CodecProfileLevel.DolbyVisionProfileDvavPer
            || profile == CodecProfileLevel.DolbyVisionProfileDvavPen) {
          sampleMimeType = MimeTypes.VIDEO_H264;
        } else if (profile == CodecProfileLevel.DolbyVisionProfileDvav110) {
          sampleMimeType = MimeTypes.VIDEO_AV1;
        }
      }
    }

    // Attempt to infer a maximum input size from the format.
    switch (sampleMimeType) {
      case MimeTypes.VIDEO_H263:
      case MimeTypes.VIDEO_MP4V:
      case MimeTypes.VIDEO_AV1:
      // Assume a min compression of 2 similar to the platform's C2SoftAomDec.cpp.
      case MimeTypes.VIDEO_VP8:
        // Assume a min compression of 2 similar to the platform's SoftVPX.cpp.
        return getMaxSampleSize(/* pixelCount= */ width * height, /* minCompressionRatio= */ 2);
      case MimeTypes.VIDEO_H265:
        // Assume a min compression of 2 similar to the platform's C2SoftHevcDec.cpp, but restrict
        // the minimum size.
        return max(
            HEVC_MAX_INPUT_SIZE_THRESHOLD,
            getMaxSampleSize(/* pixelCount= */ width * height, /* minCompressionRatio= */ 2));
      case MimeTypes.VIDEO_H264:
        if ("BRAVIA 4K 2015".equals(Build.MODEL) // Sony Bravia 4K
            || ("Amazon".equals(Build.MANUFACTURER)
                && ("KFSOWI".equals(Build.MODEL) // Kindle Soho
                    || ("AFTS".equals(Build.MODEL) && codecInfo.secure)))) { // Fire TV Gen 2
          // Use the default value for cases where platform limitations may prevent buffers of the
          // calculated maximum input size from being allocated.
          return Format.NO_VALUE;
        }
        // Round up width/height to an integer number of macroblocks.
        int maxPixels = Util.ceilDivide(width, 16) * Util.ceilDivide(height, 16) * 16 * 16;
        return getMaxSampleSize(maxPixels, /* minCompressionRatio= */ 2);
      case MimeTypes.VIDEO_VP9:
        return getMaxSampleSize(/* pixelCount= */ width * height, /* minCompressionRatio= */ 4);
      default:
        // Leave the default max input size.
        return Format.NO_VALUE;
    }
  }

  @Override
  protected float getCodecOperatingRateV23(
      float targetPlaybackSpeed, Format format, Format[] streamFormats) {
    // Use the highest known stream frame-rate up front, to avoid having to reconfigure the codec
    // should an adaptive switch to that stream occur.
    float maxFrameRate = -1;
    for (Format streamFormat : streamFormats) {
      float streamFrameRate = streamFormat.frameRate;
      if (streamFrameRate != Format.NO_VALUE) {
        maxFrameRate = max(maxFrameRate, streamFrameRate);
      }
    }
    return maxFrameRate == -1 ? CODEC_OPERATING_RATE_UNSET : (maxFrameRate * targetPlaybackSpeed);
  }

  @CallSuper
  @Override
  protected boolean maybeInitializeProcessingPipeline(Format format) throws ExoPlaybackException {
    if (videoSink != null && !videoSink.isInitialized()) {
      try {
        return videoSink.initialize(format);
      } catch (VideoSink.VideoSinkException e) {
        throw createRendererException(
            e, format, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED);
      }
    }
    return true;
  }

  /** Sets the {@linkplain Effect video effects} to apply. */
  public void setVideoEffects(List<Effect> effects) {
    videoEffects = effects;
    if (videoSink != null) {
      videoSink.setVideoEffects(effects);
    }
  }

  @Override
  protected void onCodecInitialized(
      String name,
      MediaCodecAdapter.Configuration configuration,
      long initializedTimestampMs,
      long initializationDurationMs) {
    eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
    codecNeedsSetOutputSurfaceWorkaround = codecNeedsSetOutputSurfaceWorkaround(name);
    codecHandlesHdr10PlusOutOfBandMetadata =
        checkNotNull(getCodecInfo()).isHdr10PlusOutOfBandMetadataSupported();
    maybeSetupTunnelingForFirstFrame();
  }

  @Override
  protected void onCodecReleased(String name) {
    eventDispatcher.decoderReleased(name);
  }

  @Override
  protected void onCodecError(Exception codecError) {
    Log.e(TAG, "Video codec error", codecError);
    eventDispatcher.videoCodecError(codecError);
  }

  @Override
  protected void onWakeupListenerSet(WakeupListener wakeupListener) {
    if (videoSink != null) {
      videoSink.setWakeupListener(wakeupListener);
    }
  }

  @Override
  @Nullable
  protected DecoderReuseEvaluation onInputFormatChanged(FormatHolder formatHolder)
      throws ExoPlaybackException {
    @Nullable DecoderReuseEvaluation evaluation = super.onInputFormatChanged(formatHolder);
    eventDispatcher.inputFormatChanged(checkNotNull(formatHolder.format), evaluation);
    return evaluation;
  }

  /**
   * Called immediately before an input buffer is queued into the codec.
   *
   * <p>In tunneling mode for pre Marshmallow, the buffer is treated as if immediately output.
   *
   * @param buffer The buffer to be queued.
   * @throws ExoPlaybackException Thrown if an error occurs handling the input buffer.
   */
  @CallSuper
  @Override
  protected void onQueueInputBuffer(DecoderInputBuffer buffer) throws ExoPlaybackException {
    if (av1SampleDependencyParser != null
        && checkNotNull(getCodecInfo()).mimeType.equals(MimeTypes.VIDEO_AV1)
        && buffer.data != null) {
      av1SampleDependencyParser.queueInputBuffer(buffer.data);
    }
    consecutiveDroppedInputBufferCount = 0;
    // In tunneling mode the device may do frame rate conversion, so in general we can't keep track
    // of the number of buffers in the codec.
    if (!tunneling) {
      buffersInCodecCount++;
    }
    if (Util.SDK_INT < 23 && tunneling) {
      // In tunneled mode before API 23 we don't have a way to know when the buffer is output, so
      // treat it as if it were output immediately.
      onProcessedTunneledBuffer(buffer.timeUs);
    }
  }

  @Override
  protected int getCodecBufferFlags(DecoderInputBuffer buffer) {
    if (Util.SDK_INT >= 34 && tunneling && isBufferBeforeStartTime(buffer)) {
      // The buffer likely needs to be dropped because its timestamp is less than the start time.
      // We can't decide to do this after decoding because we won't get the buffer back from the
      // codec in tunneling mode. This may not work perfectly, e.g. when the codec is doing frame
      // rate conversion, but it's still better than not dropping the buffers at all.
      return MediaCodec.BUFFER_FLAG_DECODE_ONLY;
    }
    return 0;
  }

  @Override
  protected boolean shouldDiscardDecoderInputBuffer(DecoderInputBuffer buffer) {
    if (isBufferProbablyLastSample(buffer)) {
      // Make sure to decode and render the last frame.
      return false;
    }
    if (buffer.isEncrypted()) {
      // Commonly used decryption algorithms require updating the initialization vector for each
      // block processed. Skipping input buffers before the decoder is not allowed.
      return false;
    }
    boolean shouldSkipDecoderInputBuffer = isBufferBeforeStartTime(buffer);
    if (!shouldSkipDecoderInputBuffer && !shouldDropDecoderInputBuffers) {
      return false;
    }
    if (buffer.hasSupplementalData()) {
      return false;
    }
    if (buffer.notDependedOn()) {
      buffer.clear();
      if (shouldSkipDecoderInputBuffer) {
        decoderCounters.skippedInputBufferCount += 1;
      } else if (shouldDropDecoderInputBuffers) {
        droppedDecoderInputBufferTimestamps.add(buffer.timeUs);
        consecutiveDroppedInputBufferCount += 1;
      }
      return true;
    }
    if (av1SampleDependencyParser != null
        && checkNotNull(getCodecInfo()).mimeType.equals(MimeTypes.VIDEO_AV1)
        && buffer.data != null) {
      boolean skipFrameHeaders =
          shouldSkipDecoderInputBuffer
              || consecutiveDroppedInputBufferCount
                  <= MAX_CONSECUTIVE_DROPPED_INPUT_BUFFERS_COUNT_TO_DISCARD_HEADER;
      ByteBuffer readOnlySample = buffer.data.asReadOnlyBuffer();
      readOnlySample.flip();
      int sampleLimitAfterSkippingNonReferenceFrames =
          av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
              readOnlySample, skipFrameHeaders);
      boolean hasSpaceForNextFrame =
          sampleLimitAfterSkippingNonReferenceFrames + checkNotNull(codecMaxValues).inputSize
              < readOnlySample.capacity();
      if (sampleLimitAfterSkippingNonReferenceFrames != readOnlySample.limit()
          && hasSpaceForNextFrame) {
        checkNotNull(buffer.data).position(sampleLimitAfterSkippingNonReferenceFrames);
        if (shouldSkipDecoderInputBuffer) {
          decoderCounters.skippedInputBufferCount += 1;
        } else if (shouldDropDecoderInputBuffers) {
          droppedDecoderInputBufferTimestamps.add(buffer.timeUs);
          consecutiveDroppedInputBufferCount += 1;
        }
        return true;
      }
      return false;
    }
    return false;
  }

  private boolean isBufferProbablyLastSample(DecoderInputBuffer buffer) {
    if (hasReadStreamToEnd() || buffer.isLastSample()) {
      return true;
    }
    // TODO: b/352276461 - improve buffer.isLastSample() logic.
    // This is a temporary workaround: do not skip buffers close to the period end.
    if (periodDurationUs == C.TIME_UNSET) {
      // Duration unknown: probably last sample.
      return true;
    }
    long presentationTimeUs = buffer.timeUs - getOutputStreamOffsetUs();
    return periodDurationUs - presentationTimeUs <= OFFSET_FROM_PERIOD_END_TO_TREAT_AS_LAST_US;
  }

  private boolean isBufferBeforeStartTime(DecoderInputBuffer buffer) {
    return buffer.timeUs < getLastResetPositionUs();
  }

  @Override
  protected void onOutputFormatChanged(Format format, @Nullable MediaFormat mediaFormat) {
    @Nullable MediaCodecAdapter codec = getCodec();
    if (codec != null) {
      // Must be applied each time the output format changes.
      codec.setVideoScalingMode(scalingMode);
    }
    int width;
    int height;
    float pixelWidthHeightRatio;

    if (tunneling) {
      width = format.width;
      height = format.height;
    } else {
      checkNotNull(mediaFormat);
      boolean hasCrop =
          mediaFormat.containsKey(KEY_CROP_RIGHT)
              && mediaFormat.containsKey(KEY_CROP_LEFT)
              && mediaFormat.containsKey(KEY_CROP_BOTTOM)
              && mediaFormat.containsKey(KEY_CROP_TOP);
      width =
          hasCrop
              ? mediaFormat.getInteger(KEY_CROP_RIGHT) - mediaFormat.getInteger(KEY_CROP_LEFT) + 1
              : mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
      height =
          hasCrop
              ? mediaFormat.getInteger(KEY_CROP_BOTTOM) - mediaFormat.getInteger(KEY_CROP_TOP) + 1
              : mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
    }
    pixelWidthHeightRatio = format.pixelWidthHeightRatio;
    // The decoder applies the rotation when rendering to the surface. For 90 and 270 degree
    // rotations, we need to flip the width, height and pixel aspect ratio to reflect the rotation
    // that was applied.
    if (format.rotationDegrees == 90 || format.rotationDegrees == 270) {
      int rotatedHeight = width;
      width = height;
      height = rotatedHeight;
      pixelWidthHeightRatio = 1 / pixelWidthHeightRatio;
    }
    decodedVideoSize = new VideoSize(width, height, pixelWidthHeightRatio);

    if (videoSink != null && pendingVideoSinkInputStreamChange) {
      changeVideoSinkInputStream(
          videoSink,
          /* inputType= */ VideoSink.INPUT_TYPE_SURFACE,
          format
              .buildUpon()
              .setWidth(width)
              .setHeight(height)
              .setPixelWidthHeightRatio(pixelWidthHeightRatio)
              .build());
    } else {
      videoFrameReleaseControl.setFrameRate(format.frameRate);
    }
    pendingVideoSinkInputStreamChange = false;
  }

  /**
   * Called when ready to {@linkplain VideoSink#onInputStreamChanged(int, Format, List<Effect>)
   * change} the input stream.
   *
   * <p>The default implementation applies this renderer's video effects.
   */
  protected void changeVideoSinkInputStream(
      VideoSink videoSink, @VideoSink.InputType int inputType, Format format) {
    List<Effect> videoEffectsToApply = videoEffects != null ? videoEffects : ImmutableList.of();
    videoSink.onInputStreamChanged(inputType, format, videoEffectsToApply);
  }

  @Override
  @TargetApi(29) // codecHandlesHdr10PlusOutOfBandMetadata is false if Util.SDK_INT < 29
  protected void handleInputBufferSupplementalData(DecoderInputBuffer buffer)
      throws ExoPlaybackException {
    if (!codecHandlesHdr10PlusOutOfBandMetadata) {
      return;
    }
    ByteBuffer data = checkNotNull(buffer.supplementalData);
    if (data.remaining() >= 7) {
      // Check for HDR10+ out-of-band metadata. See User_data_registered_itu_t_t35 in ST 2094-40.
      byte ituTT35CountryCode = data.get();
      int ituTT35TerminalProviderCode = data.getShort();
      int ituTT35TerminalProviderOrientedCode = data.getShort();
      byte applicationIdentifier = data.get();
      byte applicationVersion = data.get();
      data.position(0);
      if (ituTT35CountryCode == (byte) 0xB5
          && ituTT35TerminalProviderCode == 0x003C
          && ituTT35TerminalProviderOrientedCode == 0x0001
          && applicationIdentifier == 4
          && (applicationVersion == 0 || applicationVersion == 1)) {
        // The metadata size may vary so allocate a new array every time. This is not too
        // inefficient because the metadata is only a few tens of bytes.
        byte[] hdr10PlusInfo = new byte[data.remaining()];
        data.get(hdr10PlusInfo);
        data.position(0);
        setHdr10PlusInfoV29(checkNotNull(getCodec()), hdr10PlusInfo);
      }
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
    checkNotNull(codec); // Can not render video without codec

    long outputStreamOffsetUs = getOutputStreamOffsetUs();
    long presentationTimeUs = bufferPresentationTimeUs - outputStreamOffsetUs;
    updateDroppedBufferCountersWithInputBuffers(bufferPresentationTimeUs);

    if (videoSink != null) {
      // Skip decode-only buffers, e.g. after seeking, immediately.
      if (isDecodeOnlyBuffer && !isLastBuffer) {
        skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
        return true;
      }
      long framePresentationTimeUs = bufferPresentationTimeUs + getBufferTimestampAdjustmentUs();
      return videoSink.handleInputFrame(
          framePresentationTimeUs,
          isLastBuffer,
          new VideoSink.VideoFrameHandler() {
            @Override
            public void render(long renderTimestampNs) {
              renderOutputBuffer(codec, bufferIndex, presentationTimeUs, renderTimestampNs);
            }

            @Override
            public void skip() {
              skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
            }
          });
    }

    @VideoFrameReleaseControl.FrameReleaseAction
    int frameReleaseAction =
        videoFrameReleaseControl.getFrameReleaseAction(
            bufferPresentationTimeUs,
            positionUs,
            elapsedRealtimeUs,
            getOutputStreamStartPositionUs(),
            isDecodeOnlyBuffer,
            isLastBuffer,
            videoFrameReleaseInfo);
    switch (frameReleaseAction) {
      case VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY:
        long releaseTimeNs = getClock().nanoTime();
        notifyFrameMetadataListener(presentationTimeUs, releaseTimeNs, format);
        renderOutputBuffer(codec, bufferIndex, presentationTimeUs, releaseTimeNs);
        updateVideoFrameProcessingOffsetCounters(videoFrameReleaseInfo.getEarlyUs());
        return true;
      case VideoFrameReleaseControl.FRAME_RELEASE_SKIP:
        skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
        updateVideoFrameProcessingOffsetCounters(videoFrameReleaseInfo.getEarlyUs());
        return true;
      case VideoFrameReleaseControl.FRAME_RELEASE_DROP:
        dropOutputBuffer(codec, bufferIndex, presentationTimeUs);
        updateVideoFrameProcessingOffsetCounters(videoFrameReleaseInfo.getEarlyUs());
        return true;
      case VideoFrameReleaseControl.FRAME_RELEASE_TRY_AGAIN_LATER:
      case VideoFrameReleaseControl.FRAME_RELEASE_IGNORE:
        return false;
      case VideoFrameReleaseControl.FRAME_RELEASE_SCHEDULED:
        releaseFrame(checkStateNotNull(codec), bufferIndex, presentationTimeUs, format);
        return true;
      default:
        throw new IllegalStateException(String.valueOf(frameReleaseAction));
    }
  }

  @Override
  protected void renderToEndOfStream() {
    if (videoSink != null) {
      videoSink.signalEndOfCurrentInputStream();
    }
  }

  /**
   * Returns the timestamp that is added to the buffer presentation time (the player decoding
   * position) to get the frame presentation time, in microseconds.
   */
  protected long getBufferTimestampAdjustmentUs() {
    return -startPositionUs;
  }

  private void releaseFrame(
      MediaCodecAdapter codec, int bufferIndex, long presentationTimeUs, Format format) {
    long releaseTimeNs = videoFrameReleaseInfo.getReleaseTimeNs();
    long earlyUs = videoFrameReleaseInfo.getEarlyUs();
    if (shouldSkipBuffersWithIdenticalReleaseTime() && releaseTimeNs == lastFrameReleaseTimeNs) {
      // This frame should be displayed on the same vsync with the previous released frame. We
      // are likely rendering frames at a rate higher than the screen refresh rate. Skip
      // this buffer so that it's returned to MediaCodec sooner otherwise MediaCodec may not
      // be able to keep decoding with this rate [b/263454203].
      skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
    } else {
      notifyFrameMetadataListener(presentationTimeUs, releaseTimeNs, format);
      renderOutputBufferV21(codec, bufferIndex, presentationTimeUs, releaseTimeNs);
    }
    updateVideoFrameProcessingOffsetCounters(earlyUs);
    lastFrameReleaseTimeNs = releaseTimeNs;
  }

  private void notifyFrameMetadataListener(
      long presentationTimeUs, long releaseTimeNs, Format format) {
    if (frameMetadataListener != null) {
      frameMetadataListener.onVideoFrameAboutToBeRendered(
          presentationTimeUs, releaseTimeNs, format, getCodecOutputMediaFormat());
    }
  }

  /** Called when a buffer was processed in tunneling mode. */
  protected void onProcessedTunneledBuffer(long presentationTimeUs) throws ExoPlaybackException {
    updateOutputFormatForTime(presentationTimeUs);
    maybeNotifyVideoSizeChanged(decodedVideoSize);
    decoderCounters.renderedOutputBufferCount++;
    maybeNotifyRenderedFirstFrame();
    onProcessedOutputBuffer(presentationTimeUs);
  }

  /** Called when a output EOS was received in tunneling mode. */
  private void onProcessedTunneledEndOfStream() {
    setPendingOutputEndOfStream();
  }

  @CallSuper
  @Override
  protected void onProcessedOutputBuffer(long presentationTimeUs) {
    super.onProcessedOutputBuffer(presentationTimeUs);
    if (!tunneling) {
      buffersInCodecCount--;
    }
  }

  @Override
  protected void onProcessedStreamChange() {
    super.onProcessedStreamChange();
    if (videoSink != null) {
      // Signaling end of the previous stream.
      videoSink.signalEndOfCurrentInputStream();
      videoSink.setStreamTimestampInfo(
          getOutputStreamStartPositionUs(), getBufferTimestampAdjustmentUs());
    } else {
      videoFrameReleaseControl.onProcessedStreamChange();
    }
    pendingVideoSinkInputStreamChange = true;
    maybeSetupTunnelingForFirstFrame();
  }

  /**
   * Returns whether the buffer being processed should be dropped.
   *
   * @param earlyUs The time until the buffer should be presented in microseconds. A negative value
   *     indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param isLastBuffer Whether the buffer is the last buffer in the current stream.
   */
  protected boolean shouldDropOutputBuffer(
      long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
    return earlyUs < MIN_EARLY_US_LATE_THRESHOLD && !isLastBuffer;
  }

  /**
   * Returns whether to drop all buffers from the buffer being processed to the keyframe at or after
   * the current playback position, if possible.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     measured at the start of the current iteration of the rendering loop.
   * @param isLastBuffer Whether the buffer is the last buffer in the current stream.
   */
  protected boolean shouldDropBuffersToKeyframe(
      long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
    return earlyUs < MIN_EARLY_US_VERY_LATE_THRESHOLD && !isLastBuffer;
  }

  /**
   * Returns whether to skip buffers that have an identical release time as the previous released
   * buffer.
   */
  protected boolean shouldSkipBuffersWithIdenticalReleaseTime() {
    return true;
  }

  /**
   * Returns whether to force rendering an output buffer.
   *
   * @param earlyUs The time until the current buffer should be presented in microseconds. A
   *     negative value indicates that the buffer is late.
   * @param elapsedSinceLastRenderUs The elapsed time since the last output buffer was rendered, in
   *     microseconds.
   * @return Returns whether to force rendering an output buffer.
   */
  protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
    return earlyUs < MIN_EARLY_US_LATE_THRESHOLD && elapsedSinceLastRenderUs > 100_000;
  }

  /**
   * Skips the output buffer with the specified index.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to skip.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   */
  protected void skipOutputBuffer(MediaCodecAdapter codec, int index, long presentationTimeUs) {
    TraceUtil.beginSection("skipVideoBuffer");
    codec.releaseOutputBuffer(index, false);
    TraceUtil.endSection();
    decoderCounters.skippedOutputBufferCount++;
  }

  /**
   * Drops the output buffer with the specified index.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to drop.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   */
  protected void dropOutputBuffer(MediaCodecAdapter codec, int index, long presentationTimeUs) {
    TraceUtil.beginSection("dropVideoBuffer");
    codec.releaseOutputBuffer(index, false);
    TraceUtil.endSection();
    updateDroppedBufferCounters(
        /* droppedInputBufferCount= */ 0, /* droppedDecoderBufferCount= */ 1);
  }

  /**
   * Drops frames from the current output buffer to the next keyframe at or before the playback
   * position. If no such keyframe exists, as the playback position is inside the same group of
   * pictures as the buffer being processed, returns {@code false}. Returns {@code true} otherwise.
   *
   * @param positionUs The current playback position, in microseconds.
   * @param treatDroppedBuffersAsSkipped Whether dropped buffers should be treated as intentionally
   *     skipped.
   * @return Whether any buffers were dropped.
   * @throws ExoPlaybackException If an error occurs flushing the codec.
   */
  protected boolean maybeDropBuffersToKeyframe(
      long positionUs, boolean treatDroppedBuffersAsSkipped) throws ExoPlaybackException {
    int droppedSourceBufferCount = skipSource(positionUs);
    if (droppedSourceBufferCount == 0) {
      return false;
    }
    // We dropped some buffers to catch up, so update the decoder counters and flush the codec,
    // which releases all pending buffers buffers including the current output buffer.
    if (treatDroppedBuffersAsSkipped) {
      decoderCounters.skippedInputBufferCount += droppedSourceBufferCount;
      decoderCounters.skippedOutputBufferCount += buffersInCodecCount;
      decoderCounters.skippedInputBufferCount += droppedDecoderInputBufferTimestamps.size();
    } else {
      decoderCounters.droppedToKeyframeCount++;
      updateDroppedBufferCounters(
          /* droppedInputBufferCount= */ droppedSourceBufferCount
              + droppedDecoderInputBufferTimestamps.size(),
          /* droppedDecoderBufferCount= */ buffersInCodecCount);
    }
    flushOrReinitializeCodec();
    if (videoSink != null) {
      videoSink.flush(/* resetPosition= */ false);
    }
    return true;
  }

  /**
   * Updates local counters and {@link #decoderCounters} to reflect that buffers were dropped.
   *
   * @param droppedInputBufferCount The number of buffers dropped from the source before being
   *     passed to the decoder.
   * @param droppedDecoderBufferCount The number of buffers dropped after being passed to the
   *     decoder.
   */
  protected void updateDroppedBufferCounters(
      int droppedInputBufferCount, int droppedDecoderBufferCount) {
    decoderCounters.droppedInputBufferCount += droppedInputBufferCount;
    int totalDroppedBufferCount = droppedInputBufferCount + droppedDecoderBufferCount;
    decoderCounters.droppedBufferCount += totalDroppedBufferCount;
    droppedFrames += totalDroppedBufferCount;
    consecutiveDroppedFrameCount += totalDroppedBufferCount;
    decoderCounters.maxConsecutiveDroppedBufferCount =
        max(consecutiveDroppedFrameCount, decoderCounters.maxConsecutiveDroppedBufferCount);
    if (maxDroppedFramesToNotify > 0 && droppedFrames >= maxDroppedFramesToNotify) {
      maybeNotifyDroppedFrames();
    }
  }

  /**
   * Updates counters to reflect dropped input buffers prior to {@code bufferPresentationTimeUs}.
   *
   * @param bufferPresentationTimeUs The presentation timestamp of the last processed output buffer,
   *     in microseconds.
   */
  private void updateDroppedBufferCountersWithInputBuffers(long bufferPresentationTimeUs) {
    int droppedInputBufferCount = 0;
    Long minDroppedDecoderBufferTimeUs;
    while ((minDroppedDecoderBufferTimeUs = droppedDecoderInputBufferTimestamps.peek()) != null
        && minDroppedDecoderBufferTimeUs < bufferPresentationTimeUs) {
      droppedInputBufferCount++;
      droppedDecoderInputBufferTimestamps.poll();
    }
    updateDroppedBufferCounters(droppedInputBufferCount, /* droppedDecoderBufferCount= */ 0);
  }

  /**
   * Updates local counters and {@link DecoderCounters} with a new video frame processing offset.
   *
   * @param processingOffsetUs The video frame processing offset.
   */
  protected void updateVideoFrameProcessingOffsetCounters(long processingOffsetUs) {
    decoderCounters.addVideoFrameProcessingOffset(processingOffsetUs);
    totalVideoFrameProcessingOffsetUs += processingOffsetUs;
    videoFrameProcessingOffsetCount++;
  }

  /**
   * Renders the output buffer with the specified index now.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to drop.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   * @param releaseTimeNs The release timestamp that needs to be associated with this buffer, in
   *     nanoseconds.
   */
  private void renderOutputBuffer(
      MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
    renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
  }

  /**
   * @deprecated Override {@link #renderOutputBufferV21} instead. The library has min SDK 21, so
   *     this method is never called.
   */
  @Deprecated
  protected void renderOutputBuffer(MediaCodecAdapter codec, int index, long presentationTimeUs) {
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(index, true);
    TraceUtil.endSection();
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    if (videoSink == null) {
      maybeNotifyVideoSizeChanged(decodedVideoSize);
      maybeNotifyRenderedFirstFrame();
    }
  }

  /**
   * Renders the output buffer with the specified index.
   *
   * @param codec The codec that owns the output buffer.
   * @param index The index of the output buffer to drop.
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   * @param releaseTimeNs The wallclock time at which the frame should be displayed, in nanoseconds.
   */
  protected void renderOutputBufferV21(
      MediaCodecAdapter codec, int index, long presentationTimeUs, long releaseTimeNs) {
    TraceUtil.beginSection("releaseOutputBuffer");
    codec.releaseOutputBuffer(index, releaseTimeNs);
    TraceUtil.endSection();
    decoderCounters.renderedOutputBufferCount++;
    consecutiveDroppedFrameCount = 0;
    if (videoSink == null) {
      maybeNotifyVideoSizeChanged(decodedVideoSize);
      maybeNotifyRenderedFirstFrame();
    }
  }

  private boolean hasSurfaceForCodec(MediaCodecInfo codecInfo) {
    return videoSink != null
        || (displaySurface != null && displaySurface.isValid())
        || shouldUseDetachedSurface(codecInfo)
        || shouldUsePlaceholderSurface(codecInfo);
  }

  /**
   * Returns surface to be set on the codec, or null to use detached surface mode.
   *
   * <p>Must only be called if {@link #hasSurfaceForCodec(MediaCodecInfo)} returns true.
   */
  @Nullable
  private Surface getSurfaceForCodec(MediaCodecInfo codecInfo) {
    if (videoSink != null) {
      return videoSink.getInputSurface();
    } else if (displaySurface != null) {
      return displaySurface;
    } else if (shouldUseDetachedSurface(codecInfo)) {
      return null;
    } else {
      checkState(shouldUsePlaceholderSurface(codecInfo));
      if (placeholderSurface != null && placeholderSurface.secure != codecInfo.secure) {
        // We can't re-use the current placeholder surface instance with the new decoder.
        releasePlaceholderSurface();
      }
      if (placeholderSurface == null) {
        placeholderSurface = PlaceholderSurface.newInstance(context, codecInfo.secure);
      }
      return placeholderSurface;
    }
  }

  protected boolean shouldUseDetachedSurface(MediaCodecInfo codecInfo) {
    return Util.SDK_INT >= 35 && codecInfo.detachedSurfaceSupported;
  }

  protected boolean shouldUsePlaceholderSurface(MediaCodecInfo codecInfo) {
    return Util.SDK_INT >= 23
        && !tunneling
        && !codecNeedsSetOutputSurfaceWorkaround(codecInfo.name)
        && (!codecInfo.secure || PlaceholderSurface.isSecureSupported(context));
  }

  private void releasePlaceholderSurface() {
    if (placeholderSurface != null) {
      placeholderSurface.release();
      placeholderSurface = null;
    }
  }

  private void maybeSetupTunnelingForFirstFrame() {
    if (!tunneling || Util.SDK_INT < 23) {
      // The first frame notification for tunneling is triggered by onQueueInputBuffer prior to API
      // level 23 and no setup is needed here.
      return;
    }
    @Nullable MediaCodecAdapter codec = getCodec();
    if (codec == null) {
      // If codec is null, then the setup will be triggered again in onCodecInitialized.
      return;
    }
    tunnelingOnFrameRenderedListener = new OnFrameRenderedListenerV23(codec);
    if (Util.SDK_INT >= 33) {
      // This should be the default anyway according to the API contract, but some devices are known
      // to not adhere to this contract and need to get the parameter explicitly. See
      // https://github.com/androidx/media/issues/1169.
      Bundle codecParameters = new Bundle();
      codecParameters.putInt(MediaCodec.PARAMETER_KEY_TUNNEL_PEEK, 1);
      codec.setParameters(codecParameters);
    }
  }

  private void updateCodecImportance() {
    @Nullable MediaCodecAdapter codec = getCodec();
    if (codec == null) {
      // If codec is null, then the importance will be set when initializing the codec.
      return;
    }
    if (Util.SDK_INT >= 35) {
      Bundle codecParameters = new Bundle();
      codecParameters.putInt(MediaFormat.KEY_IMPORTANCE, max(0, -rendererPriority));
      codec.setParameters(codecParameters);
    }
  }

  private void maybeNotifyRenderedFirstFrame() {
    if (videoFrameReleaseControl.onFrameReleasedIsFirstFrame() && displaySurface != null) {
      notifyRenderedFirstFrame();
    }
  }

  @RequiresNonNull("displaySurface")
  private void notifyRenderedFirstFrame() {
    eventDispatcher.renderedFirstFrame(displaySurface);
    haveReportedFirstFrameRenderedForCurrentSurface = true;
  }

  private void maybeRenotifyRenderedFirstFrame() {
    if (displaySurface != null && haveReportedFirstFrameRenderedForCurrentSurface) {
      eventDispatcher.renderedFirstFrame(displaySurface);
    }
  }

  /** Notifies the new video size. */
  private void maybeNotifyVideoSizeChanged(VideoSize newOutputSize) {
    if (!newOutputSize.equals(VideoSize.UNKNOWN) && !newOutputSize.equals(reportedVideoSize)) {
      reportedVideoSize = newOutputSize;
      eventDispatcher.videoSizeChanged(reportedVideoSize);
    }
  }

  private void maybeRenotifyVideoSizeChanged() {
    if (reportedVideoSize != null) {
      eventDispatcher.videoSizeChanged(reportedVideoSize);
    }
  }

  private void maybeNotifyDroppedFrames() {
    if (droppedFrames > 0) {
      long now = getClock().elapsedRealtime();
      long elapsedMs = now - droppedFrameAccumulationStartTimeMs;
      eventDispatcher.droppedFrames(droppedFrames, elapsedMs);
      droppedFrames = 0;
      droppedFrameAccumulationStartTimeMs = now;
    }
  }

  private void maybeNotifyVideoFrameProcessingOffset() {
    if (videoFrameProcessingOffsetCount != 0) {
      eventDispatcher.reportVideoFrameProcessingOffset(
          totalVideoFrameProcessingOffsetUs, videoFrameProcessingOffsetCount);
      totalVideoFrameProcessingOffsetUs = 0;
      videoFrameProcessingOffsetCount = 0;
    }
  }

  @RequiresApi(29)
  private static void setHdr10PlusInfoV29(MediaCodecAdapter codec, byte[] hdr10PlusInfo) {
    Bundle codecParameters = new Bundle();
    codecParameters.putByteArray(MediaCodec.PARAMETER_KEY_HDR10_PLUS_INFO, hdr10PlusInfo);
    codec.setParameters(codecParameters);
  }

  private void setOutputSurface(MediaCodecAdapter codec, @Nullable Surface surface) {
    if (Util.SDK_INT >= 23 && surface != null) {
      setOutputSurfaceV23(codec, surface);
    } else if (Util.SDK_INT >= 35) {
      detachOutputSurfaceV35(codec);
    } else {
      throw new IllegalStateException();
    }
  }

  @RequiresApi(23)
  protected void setOutputSurfaceV23(MediaCodecAdapter codec, Surface surface) {
    codec.setOutputSurface(surface);
  }

  @RequiresApi(35)
  protected void detachOutputSurfaceV35(MediaCodecAdapter codec) {
    codec.detachOutputSurface();
  }

  /**
   * Returns the framework {@link MediaFormat} that should be used to configure the decoder.
   *
   * @param format The {@link Format} of media.
   * @param codecMimeType The MIME type handled by the codec.
   * @param codecMaxValues Codec max values that should be used when configuring the decoder.
   * @param codecOperatingRate The codec operating rate, or {@link #CODEC_OPERATING_RATE_UNSET} if
   *     no codec operating rate should be set.
   * @param deviceNeedsNoPostProcessWorkaround Whether the device is known to do post processing by
   *     default that isn't compatible with ExoPlayer.
   * @param tunnelingAudioSessionId The audio session id to use for tunneling, or {@link
   *     C#AUDIO_SESSION_ID_UNSET} if tunneling should not be enabled.
   * @return The framework {@link MediaFormat} that should be used to configure the decoder.
   */
  @SuppressLint("InlinedApi")
  protected MediaFormat getMediaFormat(
      Format format,
      String codecMimeType,
      CodecMaxValues codecMaxValues,
      float codecOperatingRate,
      boolean deviceNeedsNoPostProcessWorkaround,
      int tunnelingAudioSessionId) {
    MediaFormat mediaFormat = new MediaFormat();
    // Set format parameters that should always be set.
    mediaFormat.setString(MediaFormat.KEY_MIME, codecMimeType);
    mediaFormat.setInteger(MediaFormat.KEY_WIDTH, format.width);
    mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, format.height);
    MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
    // Set format parameters that may be unset.
    MediaFormatUtil.maybeSetFloat(mediaFormat, MediaFormat.KEY_FRAME_RATE, format.frameRate);
    MediaFormatUtil.maybeSetInteger(mediaFormat, MediaFormat.KEY_ROTATION, format.rotationDegrees);
    MediaFormatUtil.maybeSetColorInfo(mediaFormat, format.colorInfo);
    if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) {
      // Some phones require the profile to be set on the codec.
      // See https://github.com/google/ExoPlayer/pull/5438.
      Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
      if (codecProfileAndLevel != null) {
        MediaFormatUtil.maybeSetInteger(
            mediaFormat, MediaFormat.KEY_PROFILE, codecProfileAndLevel.first);
      }
    }
    // Set codec max values.
    mediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, codecMaxValues.width);
    mediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, codecMaxValues.height);
    MediaFormatUtil.maybeSetInteger(
        mediaFormat, MediaFormat.KEY_MAX_INPUT_SIZE, codecMaxValues.inputSize);
    // Set codec configuration values.
    if (Util.SDK_INT >= 23) {
      mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0 /* realtime priority */);
      if (codecOperatingRate != CODEC_OPERATING_RATE_UNSET) {
        mediaFormat.setFloat(MediaFormat.KEY_OPERATING_RATE, codecOperatingRate);
      }
    }
    if (deviceNeedsNoPostProcessWorkaround) {
      mediaFormat.setInteger("no-post-process", 1);
      mediaFormat.setInteger("auto-frc", 0);
    }
    if (tunnelingAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
      mediaFormat.setFeatureEnabled(CodecCapabilities.FEATURE_TunneledPlayback, true);
      mediaFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, tunnelingAudioSessionId);
    }
    if (Util.SDK_INT >= 35) {
      mediaFormat.setInteger(MediaFormat.KEY_IMPORTANCE, max(0, -rendererPriority));
    }
    return mediaFormat;
  }

  /**
   * Returns {@link CodecMaxValues} suitable for configuring a codec for {@code format} in a way
   * that will allow possible adaptation to other compatible formats in {@code streamFormats}.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The {@link Format} for which the codec is being configured.
   * @param streamFormats The possible stream formats.
   * @return Suitable {@link CodecMaxValues}.
   */
  protected CodecMaxValues getCodecMaxValues(
      MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
    int maxWidth = format.width;
    int maxHeight = format.height;
    int maxInputSize = getMaxInputSize(codecInfo, format);
    if (streamFormats.length == 1) {
      // The single entry in streamFormats must correspond to the format for which the codec is
      // being configured.
      if (maxInputSize != Format.NO_VALUE) {
        int codecMaxInputSize = getCodecMaxInputSize(codecInfo, format);
        if (codecMaxInputSize != Format.NO_VALUE) {
          // Scale up the initial video decoder maximum input size so playlist item transitions with
          // small increases in maximum sample size don't require reinitialization. This only makes
          // a difference if the exact maximum sample sizes are known from the container.
          int scaledMaxInputSize =
              (int) (maxInputSize * INITIAL_FORMAT_MAX_INPUT_SIZE_SCALE_FACTOR);
          // Avoid exceeding the maximum expected for the codec.
          maxInputSize = min(scaledMaxInputSize, codecMaxInputSize);
        }
      }
      return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
    }
    boolean haveUnknownDimensions = false;
    for (Format streamFormat : streamFormats) {
      if (format.colorInfo != null && streamFormat.colorInfo == null) {
        // streamFormat likely has incomplete color information. Copy the complete color information
        // from format to avoid codec re-use being ruled out for only this reason.
        streamFormat = streamFormat.buildUpon().setColorInfo(format.colorInfo).build();
      }
      if (codecInfo.canReuseCodec(format, streamFormat).result != REUSE_RESULT_NO) {
        haveUnknownDimensions |=
            (streamFormat.width == Format.NO_VALUE || streamFormat.height == Format.NO_VALUE);
        maxWidth = max(maxWidth, streamFormat.width);
        maxHeight = max(maxHeight, streamFormat.height);
        maxInputSize = max(maxInputSize, getMaxInputSize(codecInfo, streamFormat));
      }
    }
    if (haveUnknownDimensions) {
      Log.w(TAG, "Resolutions unknown. Codec max resolution: " + maxWidth + "x" + maxHeight);
      @Nullable Point codecMaxSize = getCodecMaxSize(codecInfo, format);
      if (codecMaxSize != null) {
        maxWidth = max(maxWidth, codecMaxSize.x);
        maxHeight = max(maxHeight, codecMaxSize.y);
        maxInputSize =
            max(
                maxInputSize,
                getCodecMaxInputSize(
                    codecInfo, format.buildUpon().setWidth(maxWidth).setHeight(maxHeight).build()));
        Log.w(TAG, "Codec max resolution adjusted to: " + maxWidth + "x" + maxHeight);
      }
    }
    return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
  }

  @Override
  protected MediaCodecDecoderException createDecoderException(
      Throwable cause, @Nullable MediaCodecInfo codecInfo) {
    return new MediaCodecVideoDecoderException(cause, codecInfo, displaySurface);
  }

  /**
   * Returns a maximum video size to use when configuring a codec for {@code format} in a way that
   * will allow possible adaptation to other compatible formats that are expected to have the same
   * aspect ratio, but whose sizes are unknown.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The {@link Format} for which the codec is being configured.
   * @return The maximum video size to use, or {@code null} if the size of {@code format} should be
   *     used.
   */
  @Nullable
  private static Point getCodecMaxSize(MediaCodecInfo codecInfo, Format format) {
    boolean isVerticalVideo = format.height > format.width;
    int formatLongEdgePx = isVerticalVideo ? format.height : format.width;
    int formatShortEdgePx = isVerticalVideo ? format.width : format.height;
    float aspectRatio = (float) formatShortEdgePx / formatLongEdgePx;
    for (int longEdgePx : STANDARD_LONG_EDGE_VIDEO_PX) {
      int shortEdgePx = (int) (longEdgePx * aspectRatio);
      if (longEdgePx <= formatLongEdgePx || shortEdgePx <= formatShortEdgePx) {
        // Don't return a size not larger than the format for which the codec is being configured.
        return null;
      } else {
        Point alignedSize =
            codecInfo.alignVideoSizeV21(
                isVerticalVideo ? shortEdgePx : longEdgePx,
                isVerticalVideo ? longEdgePx : shortEdgePx);
        float frameRate = format.frameRate;
        if (alignedSize != null
            && codecInfo.isVideoSizeAndRateSupportedV21(alignedSize.x, alignedSize.y, frameRate)) {
          return alignedSize;
        }
      }
    }
    return null;
  }

  /**
   * Returns a maximum input buffer size for a given {@link MediaCodec} and {@link Format}.
   *
   * @param codecInfo Information about the {@link MediaCodec} being configured.
   * @param format The format.
   * @return A maximum input buffer size in bytes, or {@link Format#NO_VALUE} if a maximum could not
   *     be determined.
   */
  protected static int getMaxInputSize(MediaCodecInfo codecInfo, Format format) {
    if (format.maxInputSize != Format.NO_VALUE) {
      // The format defines an explicit maximum input size. Add the total size of initialization
      // data buffers, as they may need to be queued in the same input buffer as the largest sample.
      int totalInitializationDataSize = 0;
      int initializationDataCount = format.initializationData.size();
      for (int i = 0; i < initializationDataCount; i++) {
        totalInitializationDataSize += format.initializationData.get(i).length;
      }
      return format.maxInputSize + totalInitializationDataSize;
    } else {
      return getCodecMaxInputSize(codecInfo, format);
    }
  }

  /**
   * Returns whether the device is known to do post processing by default that isn't compatible with
   * ExoPlayer.
   *
   * @return Whether the device is known to do post processing by default that isn't compatible with
   *     ExoPlayer.
   */
  private static boolean deviceNeedsNoPostProcessWorkaround() {
    // Nvidia devices prior to M try to adjust the playback rate to better map the frame-rate of
    // content to the refresh rate of the display. For example playback of 23.976fps content is
    // adjusted to play at 1.001x speed when the output display is 60Hz. Unfortunately the
    // implementation causes ExoPlayer's reported playback position to drift out of sync. Captions
    // also lose sync [Internal: b/26453592]. Even after M, the devices may apply post processing
    // operations that can modify frame output timestamps, which is incompatible with ExoPlayer's
    // logic for skipping decode-only frames.
    return "NVIDIA".equals(Build.MANUFACTURER);
  }

  /*
   * TODO:
   *
   * 1. Validate that Android device certification now ensures correct behavior, and add a
   *    corresponding SDK_INT upper bound for applying the workaround (probably SDK_INT < 26).
   * 2. Determine a complete list of affected devices.
   * 3. Some of the devices in this list only fail to support setOutputSurface when switching from
   *    a SurfaceView provided Surface to a Surface of another type (e.g. TextureView/DummySurface),
   *    and vice versa. One hypothesis is that setOutputSurface fails when the surfaces have
   *    different pixel formats. If we can find a way to query the Surface instances to determine
   *    whether this case applies, then we'll be able to provide a more targeted workaround.
   */
  /**
   * Returns whether the codec is known to implement {@link MediaCodec#setOutputSurface(Surface)}
   * incorrectly.
   *
   * <p>If true is returned then we fall back to releasing and re-instantiating the codec instead.
   *
   * @param name The name of the codec.
   * @return True if the device is known to implement {@link MediaCodec#setOutputSurface(Surface)}
   *     incorrectly.
   */
  protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
    if (name.startsWith("OMX.google")) {
      // Google OMX decoders are not known to have this issue on any API level.
      return false;
    }
    synchronized (MediaCodecVideoRenderer.class) {
      if (!evaluatedDeviceNeedsSetOutputSurfaceWorkaround) {
        deviceNeedsSetOutputSurfaceWorkaround = evaluateDeviceNeedsSetOutputSurfaceWorkaround();
        evaluatedDeviceNeedsSetOutputSurfaceWorkaround = true;
      }
    }
    return deviceNeedsSetOutputSurfaceWorkaround;
  }

  /** Returns the output surface. */
  @Nullable
  protected Surface getSurface() {
    // TODO(b/260702159) Consider renaming the method to getOutputSurface().
    return displaySurface;
  }

  protected static final class CodecMaxValues {

    public final int width;
    public final int height;
    public final int inputSize;

    public CodecMaxValues(int width, int height, int inputSize) {
      this.width = width;
      this.height = height;
      this.inputSize = inputSize;
    }
  }

  /**
   * Returns the maximum sample size assuming three channel 4:2:0 subsampled input frames with the
   * specified {@code minCompressionRatio}
   *
   * @param pixelCount The number of pixels
   * @param minCompressionRatio The minimum compression ratio
   */
  private static int getMaxSampleSize(int pixelCount, int minCompressionRatio) {
    return (pixelCount * 3) / (2 * minCompressionRatio);
  }

  private static boolean evaluateDeviceNeedsSetOutputSurfaceWorkaround() {
    if (Util.SDK_INT <= 28) {
      // Workaround for MiTV and MiBox devices which have been observed broken up to API 28.
      // https://github.com/google/ExoPlayer/issues/5169,
      // https://github.com/google/ExoPlayer/issues/6899.
      // https://github.com/google/ExoPlayer/issues/8014.
      // https://github.com/google/ExoPlayer/issues/8329.
      // https://github.com/google/ExoPlayer/issues/9710.
      switch (Build.DEVICE) {
        case "aquaman":
        case "dangal":
        case "dangalUHD":
        case "dangalFHD":
        case "magnolia":
        case "machuca":
        case "once":
        case "oneday":
          return true;
        default:
          break; // Do nothing.
      }
    }
    if (Util.SDK_INT <= 27 && "HWEML".equals(Build.DEVICE)) {
      // Workaround for Huawei P20:
      // https://github.com/google/ExoPlayer/issues/4468#issuecomment-459291645.
      return true;
    }
    switch (Build.MODEL) {
      // Workaround for some Fire OS devices.
      case "AFTA":
      case "AFTN":
      case "AFTR":
      case "AFTEU011":
      case "AFTEU014":
      case "AFTEUFF014":
      case "AFTJMST12":
      case "AFTKMST12":
      case "AFTSO001":
        return true;
      default:
        break; // Do nothing.
    }
    if (Util.SDK_INT <= 26) {
      // In general, devices running API level 27 or later should be unaffected unless observed
      // otherwise. Enable the workaround on a per-device basis. Works around:
      // https://github.com/google/ExoPlayer/issues/3236,
      // https://github.com/google/ExoPlayer/issues/3355,
      // https://github.com/google/ExoPlayer/issues/3439,
      // https://github.com/google/ExoPlayer/issues/3724,
      // https://github.com/google/ExoPlayer/issues/3835,
      // https://github.com/google/ExoPlayer/issues/4006,
      // https://github.com/google/ExoPlayer/issues/4084,
      // https://github.com/google/ExoPlayer/issues/4104,
      // https://github.com/google/ExoPlayer/issues/4134,
      // https://github.com/google/ExoPlayer/issues/4315,
      // https://github.com/google/ExoPlayer/issues/4419,
      // https://github.com/google/ExoPlayer/issues/4460,
      // https://github.com/google/ExoPlayer/issues/4468,
      // https://github.com/google/ExoPlayer/issues/5312,
      // https://github.com/google/ExoPlayer/issues/6503.
      // https://github.com/google/ExoPlayer/issues/8014,
      // https://github.com/google/ExoPlayer/pull/8030.
      switch (Build.DEVICE) {
        case "1601":
        case "1713":
        case "1714":
        case "601LV":
        case "602LV":
        case "A10-70F":
        case "A10-70L":
        case "A1601":
        case "A2016a40":
        case "A7000-a":
        case "A7000plus":
        case "A7010a48":
        case "A7020a48":
        case "AquaPowerM":
        case "ASUS_X00AD_2":
        case "Aura_Note_2":
        case "b5":
        case "BLACK-1X":
        case "BRAVIA_ATV2":
        case "BRAVIA_ATV3_4K":
        case "C1":
        case "ComioS1":
        case "CP8676_I02":
        case "CPH1609":
        case "CPH1715":
        case "CPY83_I00":
        case "cv1":
        case "cv3":
        case "deb":
        case "DM-01K":
        case "E5643":
        case "ELUGA_A3_Pro":
        case "ELUGA_Note":
        case "ELUGA_Prim":
        case "ELUGA_Ray_X":
        case "EverStar_S":
        case "F01H":
        case "F01J":
        case "F02H":
        case "F03H":
        case "F04H":
        case "F04J":
        case "F3111":
        case "F3113":
        case "F3116":
        case "F3211":
        case "F3213":
        case "F3215":
        case "F3311":
        case "flo":
        case "fugu":
        case "GiONEE_CBL7513":
        case "GiONEE_GBL7319":
        case "GIONEE_GBL7360":
        case "GIONEE_SWW1609":
        case "GIONEE_SWW1627":
        case "GIONEE_SWW1631":
        case "GIONEE_WBL5708":
        case "GIONEE_WBL7365":
        case "GIONEE_WBL7519":
        case "griffin":
        case "htc_e56ml_dtul":
        case "hwALE-H":
        case "HWBLN-H":
        case "HWCAM-H":
        case "HWVNS-H":
        case "HWWAS-H":
        case "i9031":
        case "iball8735_9806":
        case "Infinix-X572":
        case "iris60":
        case "itel_S41":
        case "j2xlteins":
        case "JGZ":
        case "K50a40":
        case "kate":
        case "l5460":
        case "le_x6":
        case "LS-5017":
        case "M04":
        case "M5c":
        case "manning":
        case "marino_f":
        case "MEIZU_M5":
        case "mh":
        case "mido":
        case "MX6":
        case "namath":
        case "nicklaus_f":
        case "NX541J":
        case "NX573J":
        case "OnePlus5T":
        case "p212":
        case "P681":
        case "P85":
        case "pacificrim":
        case "panell_d":
        case "panell_dl":
        case "panell_ds":
        case "panell_dt":
        case "PB2-670M":
        case "PGN528":
        case "PGN610":
        case "PGN611":
        case "Phantom6":
        case "Pixi4-7_3G":
        case "Pixi5-10_4G":
        case "PLE":
        case "PRO7S":
        case "Q350":
        case "Q4260":
        case "Q427":
        case "Q4310":
        case "Q5":
        case "QM16XE_U":
        case "QX1":
        case "RAIJIN":
        case "santoni":
        case "Slate_Pro":
        case "SVP-DTV15":
        case "s905x018":
        case "taido_row":
        case "TB3-730F":
        case "TB3-730X":
        case "TB3-850F":
        case "TB3-850M":
        case "tcl_eu":
        case "V1":
        case "V23GB":
        case "V5":
        case "vernee_M5":
        case "watson":
        case "whyred":
        case "woods_f":
        case "woods_fn":
        case "X3_HK":
        case "XE2X":
        case "XT1663":
        case "Z12_PRO":
        case "Z80":
          return true;
        default:
          break; // Do nothing.
      }
      switch (Build.MODEL) {
        case "JSN-L21":
          return true;
        default:
          break; // Do nothing.
      }
    }
    return false;
  }

  @RequiresApi(23)
  private final class OnFrameRenderedListenerV23
      implements MediaCodecAdapter.OnFrameRenderedListener, Handler.Callback {

    private static final int HANDLE_FRAME_RENDERED = 0;

    private final Handler handler;

    public OnFrameRenderedListenerV23(MediaCodecAdapter codec) {
      handler = Util.createHandlerForCurrentLooper(/* callback= */ this);
      codec.setOnFrameRenderedListener(/* listener= */ this, handler);
    }

    @Override
    public void onFrameRendered(MediaCodecAdapter codec, long presentationTimeUs, long nanoTime) {
      // Workaround bug in MediaCodec that causes deadlock if you call directly back into the
      // MediaCodec from this listener method.
      // Deadlock occurs because MediaCodec calls this listener method holding a lock,
      // which may also be required by calls made back into the MediaCodec.
      // This was fixed in https://android-review.googlesource.com/1156807.
      //
      // The workaround queues the event for subsequent processing, where the lock will not be held.
      if (Util.SDK_INT < 30) {
        Message message =
            Message.obtain(
                handler,
                /* what= */ HANDLE_FRAME_RENDERED,
                /* arg1= */ (int) (presentationTimeUs >> 32),
                /* arg2= */ (int) presentationTimeUs);
        handler.sendMessageAtFrontOfQueue(message);
      } else {
        handleFrameRendered(presentationTimeUs);
      }
    }

    @Override
    public boolean handleMessage(Message message) {
      switch (message.what) {
        case HANDLE_FRAME_RENDERED:
          handleFrameRendered(Util.toLong(message.arg1, message.arg2));
          return true;
        default:
          return false;
      }
    }

    private void handleFrameRendered(long presentationTimeUs) {
      if (this != tunnelingOnFrameRenderedListener || getCodec() == null) {
        // Stale event.
        return;
      }
      if (presentationTimeUs == TUNNELING_EOS_PRESENTATION_TIME_US) {
        onProcessedTunneledEndOfStream();
      } else {
        try {
          onProcessedTunneledBuffer(presentationTimeUs);
        } catch (ExoPlaybackException e) {
          setPendingPlaybackException(e);
        }
      }
    }
  }
}
