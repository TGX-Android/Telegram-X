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

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.MultipleInputVideoGraph;
import androidx.media3.effect.PreviewingMultipleInputVideoGraph;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback test of {@link CompositionPlayer} using {@link MultipleInputVideoGraph}. */
@RunWith(AndroidJUnit4.class)
public class CompositionMultipleSequencePlaybackTest {
  private static final long TEST_TIMEOUT_MS = 20_000;
  private static final MediaItem VIDEO_MEDIA_ITEM = MediaItem.fromUri(MP4_ASSET.uri);
  private static final long VIDEO_DURATION_US = MP4_ASSET.videoDurationUs;
  private static final EditedMediaItem VIDEO_EDITED_MEDIA_ITEM =
      new EditedMediaItem.Builder(VIDEO_MEDIA_ITEM).setDurationUs(VIDEO_DURATION_US).build();
  private static final ImmutableList<Long> VIDEO_TIMESTAMPS_US = MP4_ASSET.videoTimestampsUs;

  private static final MediaItem IMAGE_MEDIA_ITEM =
      new MediaItem.Builder().setUri(PNG_ASSET.uri).setImageDurationMs(200).build();
  private static final long IMAGE_DURATION_US = 200_000;
  private static final EditedMediaItem IMAGE_EDITED_MEDIA_ITEM =
      new EditedMediaItem.Builder(IMAGE_MEDIA_ITEM).setDurationUs(IMAGE_DURATION_US).build();
  // 200 ms at 30 fps (default frame rate)
  private static final ImmutableList<Long> IMAGE_TIMESTAMPS_US =
      ImmutableList.of(0L, 33_333L, 66_667L, 100_000L, 133_333L, 166_667L);

  private final Context context = getInstrumentation().getContext().getApplicationContext();
  private final PlayerTestListener playerTestListener = new PlayerTestListener(TEST_TIMEOUT_MS);

  private @MonotonicNonNull InputTimestampRecordingShaderProgram
      inputTimestampRecordingShaderProgram;
  private @MonotonicNonNull CompositionPlayer player;

  @Before
  public void setUp() {
    inputTimestampRecordingShaderProgram = new InputTimestampRecordingShaderProgram();
  }

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
  public void playback_singleSequenceOfVideos_effectsReceiveCorrectTimestamps() throws Exception {
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        VIDEO_EDITED_MEDIA_ITEM, VIDEO_EDITED_MEDIA_ITEM)
                    .build())
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> VIDEO_DURATION_US + timestampUs))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_singleSequenceOfImages_effectsReceiveCorrectTimestamps() throws Exception {
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        IMAGE_EDITED_MEDIA_ITEM, IMAGE_EDITED_MEDIA_ITEM)
                    .build())
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(IMAGE_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    IMAGE_TIMESTAMPS_US, timestampUs -> IMAGE_DURATION_US + timestampUs))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  @Ignore("TODO: b/391349011 - Re-enable after propagating an EOS signal after each MediaItem")
  public void playback_sequencesOfVideos_effectsReceiveCorrectTimestamps() throws Exception {
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        VIDEO_EDITED_MEDIA_ITEM, VIDEO_EDITED_MEDIA_ITEM)
                    .build(),
                new EditedMediaItemSequence.Builder(
                        VIDEO_EDITED_MEDIA_ITEM, VIDEO_EDITED_MEDIA_ITEM)
                    .build())
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(VIDEO_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    VIDEO_TIMESTAMPS_US, timestampUs -> VIDEO_DURATION_US + timestampUs))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  @Test
  public void playback_sequencesOfImages_effectsReceiveCorrectTimestamps() throws Exception {
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        IMAGE_EDITED_MEDIA_ITEM, IMAGE_EDITED_MEDIA_ITEM, IMAGE_EDITED_MEDIA_ITEM)
                    .build(),
                new EditedMediaItemSequence.Builder(
                        IMAGE_EDITED_MEDIA_ITEM, IMAGE_EDITED_MEDIA_ITEM, IMAGE_EDITED_MEDIA_ITEM)
                    .build())
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        (GlEffect) (context, useHdr) -> inputTimestampRecordingShaderProgram)))
            .build();
    ImmutableList<Long> expectedTimestampsUs =
        new ImmutableList.Builder<Long>()
            .addAll(IMAGE_TIMESTAMPS_US)
            .addAll(
                Iterables.transform(
                    IMAGE_TIMESTAMPS_US, timestampUs -> IMAGE_DURATION_US + timestampUs))
            .addAll(
                Iterables.transform(
                    IMAGE_TIMESTAMPS_US, timestampUs -> 2 * IMAGE_DURATION_US + timestampUs))
            .build();

    runCompositionPlayer(composition);

    assertThat(inputTimestampRecordingShaderProgram.getInputTimestampsUs())
        .isEqualTo(expectedTimestampsUs);
  }

  private void runCompositionPlayer(Composition composition)
      throws PlaybackException, TimeoutException {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player =
                  new CompositionPlayer.Builder(context)
                      .setPreviewingVideoGraphFactory(
                          new PreviewingMultipleInputVideoGraph.Factory())
                      .build();
              player.addListener(playerTestListener);
              player.setComposition(composition);
              player.prepare();
              player.play();
            });
    playerTestListener.waitUntilPlayerEnded();
  }
}
