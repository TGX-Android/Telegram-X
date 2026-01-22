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

package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.MediaFormatUtil.createMediaFormatFromFormat;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.test.utils.TestUtil.buildAssetUri;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Utilities for decoding a video frame for tests. */
@UnstableApi
public final class DecodeOneFrameUtil {
  /** Listener for decoding events. */
  public interface Listener {
    /** Called when the video {@link MediaFormat} is extracted from the container. */
    void onContainerExtracted(MediaFormat mediaFormat);

    /**
     * Called when the video {@link MediaFormat} is read by the decoder from the byte stream, after
     * a frame is decoded.
     */
    void onFrameDecoded(MediaFormat mediaFormat);
  }

  /** Timeout for reading, decoding and rendering a video frame, in milliseconds. */
  private static final int TIMEOUT_MS = 5_000;

  /**
   * Reads and decodes one frame synchronously from the {@code assetFilePath} and renders it to the
   * {@code surface}.
   *
   * <p>This method blocks until the frame has been rendered to the {@code surface}.
   *
   * @param assetFilePath The path to the file in the asset directory.
   * @param listener A {@link Listener} implementation.
   * @param surface The {@link Surface} to render the decoded frame to.
   */
  @SuppressWarnings("CatchingUnchecked")
  public static void decodeOneAssetFileFrame(
      String assetFilePath, Listener listener, Surface surface) throws Exception {
    decodeOneMediaItemFrame(MediaItem.fromUri(buildAssetUri(assetFilePath)), listener, surface);
  }

  /**
   * Reads and decodes one frame synchronously from the {@code mediaItem} and renders it to the
   * {@code surface}.
   *
   * <p>This method blocks until the frame has been rendered to the {@code surface}.
   *
   * @param mediaItem The {@link MediaItem} from which to decode a frame.
   * @param listener A {@link Listener} implementation.
   * @param surface The {@link Surface} to render the decoded frame to.
   */
  public static void decodeOneMediaItemFrame(
      MediaItem mediaItem, Listener listener, Surface surface) throws Exception {
    Context context = getApplicationContext();
    AtomicReference<@NullableType Exception> unexpectedExceptionReference = new AtomicReference<>();
    AtomicReference<@NullableType PlaybackException> playbackExceptionReference =
        new AtomicReference<>();
    ConditionVariable firstFrameRenderedOrError = new ConditionVariable();

    ExoPlayer exoPlayer = new ExoPlayer.Builder(context).build();
    postOrRun(
        new Handler(exoPlayer.getApplicationLooper()),
        () ->
            exoPlayer.setVideoFrameMetadataListener(
                (presentationTimeUs, releaseTimeNs, format, mediaFormat) ->
                    listener.onFrameDecoded(checkNotNull(mediaFormat))));
    Handler handler = new Handler(exoPlayer.getApplicationLooper());
    AnalyticsListener analyticsListener =
        new AnalyticsListener() {
          @Override
          public void onVideoInputFormatChanged(
              EventTime eventTime,
              Format format,
              @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
            listener.onContainerExtracted(createMediaFormatFromFormat(format));
          }

          @Override
          public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
            if (exoPlayer.isReleased()) {
              return;
            }
            firstFrameRenderedOrError.open();
          }

          @Override
          public void onEvents(Player player, Events events) {
            if (exoPlayer.isReleased()) {
              return;
            }
            if (events.contains(EVENT_PLAYER_ERROR)) {
              playbackExceptionReference.set(checkNotNull(player.getPlayerError()));
              firstFrameRenderedOrError.open();
            }
          }
        };

    handler.post(
        () -> {
          try {
            exoPlayer.setVideoSurface(surface);
            exoPlayer.addAnalyticsListener(analyticsListener);
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.prepare();
            // Catch all exceptions to report. Exceptions thrown here and not caught will not
            // propagate.
          } catch (Exception e) {
            unexpectedExceptionReference.set(e);
            firstFrameRenderedOrError.open();
          }
        });

    if (!firstFrameRenderedOrError.block(TIMEOUT_MS)) {
      throw new TimeoutException(
          "DecodeOneFrameUtil timed out after " + TIMEOUT_MS + " milliseconds.");
    }
    handler.post(exoPlayer::release);
    @Nullable PlaybackException playbackException = playbackExceptionReference.get();
    if (playbackException != null) {
      throw playbackException;
    }
    @Nullable Exception unexpectedException = unexpectedExceptionReference.get();
    if (unexpectedException != null) {
      throw new IllegalStateException(
          "Unexpected exception starting the player.", unexpectedException);
    }
  }

  private DecodeOneFrameUtil() {}
}
