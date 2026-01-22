/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_OFF;
import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.common.util.Util.usToMs;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.NullableType;
import androidx.media3.effect.GlEffect;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback tests for {@link CompositionPlayer} */
@RunWith(AndroidJUnit4.class)
public class CompositionPlaybackTest {

  private static final long TEST_TIMEOUT_MS = isRunningOnEmulator() ? 20_000 : 10_000;
  private static final MediaItem VIDEO_MEDIA_ITEM = MediaItem.fromUri(MP4_ASSET.uri);
  private static final long VIDEO_DURATION_US = MP4_ASSET.videoDurationUs;
  private static final ImmutableList<Long> VIDEO_TIMESTAMPS_US = MP4_ASSET.videoTimestampsUs;
  private static final long IMAGE_DURATION_US = 200_000;
  private static final MediaItem IMAGE_MEDIA_ITEM =
      new MediaItem.Builder()
          .setUri(PNG_ASSET.uri)
          .setImageDurationMs(usToMs(IMAGE_DURATION_US))
          .build();
  // 200 ms at 30 fps (default frame rate)
  private static final ImmutableList<Long> IMAGE_TIMESTAMPS_US =
      ImmutableList.of(0L, 33_333L, 66_667L, 100_000L, 133_333L, 166_667L);

  private final Context context = getInstrumentation().getContext().getApplicationContext();
  private final PlayerTestListener playerTestListener = new PlayerTestListener(TEST_TIMEOUT_MS);

  private @MonotonicNonNull CompositionPlayer player;

  @After
  public void tearDown() {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              if (player != null) {
                player.release();
              }
            });
  }

  @Test
  public void playback_sequenceOfVideos_effectsReceiveCorrectTimestamps() throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    Effect videoEffect = (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (VIDEO_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);
    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_singleSequenceOfImages_effectsReceiveCorrectTimestamps() throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    Effect videoEffect = (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(IMAGE_MEDIA_ITEM)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(IMAGE_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    IMAGE_TIMESTAMPS_US, timestampUs -> (IMAGE_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_singleSequenceOfImages_compositionEffectsReceiveCorrectTimestamps()
      throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    Effect videoEffect = (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram;
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(IMAGE_MEDIA_ITEM).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(IMAGE_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    IMAGE_TIMESTAMPS_US, timestampUs -> (IMAGE_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_sequenceOfVideoAndImage_effectsReceiveCorrectTimestamps() throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    Effect videoEffect = (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram;
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    EditedMediaItem imageEditedMediaItem =
        new EditedMediaItem.Builder(IMAGE_MEDIA_ITEM)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(videoEditedMediaItem, imageEditedMediaItem)
                    .build())
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    IMAGE_TIMESTAMPS_US, timestampUs -> (VIDEO_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_sequenceOfImageAndVideo_effectsReceiveCorrectTimestamps() throws Exception {
    // The MediaCodec decoder's output surface is sometimes dropping frames on emulator despite
    // using MediaFormat.KEY_ALLOW_FRAME_DROP.
    assumeFalse("Skipped on emulator due to surface dropping frames", isRunningOnEmulator());
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    Effect videoEffect = (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram;
    EditedMediaItem imageEditedMediaItem =
        new EditedMediaItem.Builder(IMAGE_MEDIA_ITEM)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(imageEditedMediaItem, videoEditedMediaItem)
                    .build())
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(IMAGE_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (IMAGE_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void
      playback_sequenceOfThreeVideosWithRemovingFirstAndLastAudio_effectsReceiveCorrectTimestamps()
          throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    EditedMediaItem videoEditedMediaItemRemoveAudio =
        videoEditedMediaItem.buildUpon().setRemoveAudio(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        videoEditedMediaItemRemoveAudio,
                        videoEditedMediaItem,
                        videoEditedMediaItemRemoveAudio)
                    .build())
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (VIDEO_DURATION_US + timestampUs)))
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (2 * VIDEO_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void
      playback_sequenceOfThreeVideosWithRemovingMiddleAudio_effectsReceiveCorrectTimestamps()
          throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    EditedMediaItem videoEditedMediaItemRemoveAudio =
        videoEditedMediaItem.buildUpon().setRemoveAudio(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        videoEditedMediaItem, videoEditedMediaItemRemoveAudio, videoEditedMediaItem)
                    .build())
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (VIDEO_DURATION_US + timestampUs)))
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (2 * VIDEO_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_sequenceOfThreeVideosRemovingMiddleVideo_noFrameIsRendered()
      throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();

    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    EditedMediaItem videoEditedMediaItemRemoveVideo =
        videoEditedMediaItem.buildUpon().setRemoveVideo(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        videoEditedMediaItem, videoEditedMediaItemRemoveVideo, videoEditedMediaItem)
                    .build())
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs()).isEmpty();
  }

  @Test
  public void playback_compositionWithSecondSequenceRemoveVideo_rendersVideoFromFirstSequence()
      throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();

    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    EditedMediaItem videoEditedMediaItemRemoveVideo =
        videoEditedMediaItem.buildUpon().setRemoveVideo(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(videoEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder(videoEditedMediaItemRemoveVideo).build())
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(VIDEO_TIMESTAMPS_US);
  }

  @Test
  public void playback_withCompositionEffect_effectIsApplied() throws Exception {
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM).setDurationUs(VIDEO_DURATION_US).build();
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    Effect videoEffect = (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram;
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (VIDEO_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_withRepeatModeSet_succeeds() throws Exception {
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM).setDurationUs(VIDEO_DURATION_US).build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .build();
    CountDownLatch repetitionEndedLatch = new CountDownLatch(2);
    AtomicReference<@NullableType PlaybackException> playbackException = new AtomicReference<>();

    getInstrumentation()
        .runOnMainSync(
            () -> {
              player = new CompositionPlayer.Builder(context).build();
              player.addListener(playerTestListener);
              player.addListener(
                  new Player.Listener() {
                    @Override
                    public void onPositionDiscontinuity(
                        Player.PositionInfo oldPosition,
                        Player.PositionInfo newPosition,
                        int reason) {
                      if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
                        repetitionEndedLatch.countDown();
                      }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                      playbackException.set(error);
                      while (repetitionEndedLatch.getCount() > 0) {
                        repetitionEndedLatch.countDown();
                      }
                    }
                  });
              player.setComposition(composition);
              player.setRepeatMode(REPEAT_MODE_ALL);
              player.prepare();
              player.play();
            });
    boolean latchTimedOut = !repetitionEndedLatch.await(TEST_TIMEOUT_MS, MILLISECONDS);

    assertThat(playbackException.get()).isNull();
    assertThat(latchTimedOut).isFalse();
    getInstrumentation().runOnMainSync(() -> player.setRepeatMode(REPEAT_MODE_OFF));
    playerTestListener.waitUntilPlayerEnded();
  }

  @Test
  public void playback_sequenceOfVideosWithPrewarmingDisabled_effectsReceiveCorrectTimestamps()
      throws Exception {
    InputTimestampRecordingShaderProgram inputTimestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    Effect videoEffect = (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM)
            .setDurationUs(VIDEO_DURATION_US)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(videoEffect)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        editedMediaItem, editedMediaItem, editedMediaItem)
                    .build())
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (VIDEO_DURATION_US + timestampUs)))
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> (2 * VIDEO_DURATION_US + timestampUs)))
            .build();

    runCompositionPlayer(composition, /* videoPrewarmingEnabled= */ false);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  private void runCompositionPlayer(Composition composition)
      throws PlaybackException, TimeoutException {
    runCompositionPlayer(composition, /* videoPrewarmingEnabled= */ true);
  }

  private void runCompositionPlayer(Composition composition, boolean videoPrewarmingEnabled)
      throws PlaybackException, TimeoutException {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player =
                  new CompositionPlayer.Builder(context)
                      .setVideoPrewarmingEnabled(videoPrewarmingEnabled)
                      .build();
              player.addListener(playerTestListener);
              player.setComposition(composition);
              player.prepare();
              player.play();
            });
    playerTestListener.waitUntilPlayerEnded();
  }
}
