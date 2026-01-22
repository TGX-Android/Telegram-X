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

package androidx.media3.transformer.mh;

import static androidx.media3.common.util.Util.usToMs;
import static androidx.media3.transformer.AndroidTestUtil.JPG_SINGLE_PIXEL_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.view.SurfaceView;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.CompositionPlayer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.InputTimestampRecordingShaderProgram;
import androidx.media3.transformer.PlayerTestListener;
import androidx.media3.transformer.SurfaceTestActivity;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * A test that guarantees the timestamp is handled identically between {@link CompositionPlayer} and
 * {@link Transformer}.
 */
@RunWith(AndroidJUnit4.class)
public class VideoTimestampConsistencyTest {

  private static final long TEST_TIMEOUT_MS = 10_000;
  private static final ImmutableList<Long> MP4_ASSET_FRAME_TIMESTAMPS_US =
      ImmutableList.of(
          0L, 33366L, 66733L, 100100L, 133466L, 166833L, 200200L, 233566L, 266933L, 300300L,
          333666L, 367033L, 400400L, 433766L, 467133L, 500500L, 533866L, 567233L, 600600L, 633966L,
          667333L, 700700L, 734066L, 767433L, 800800L, 834166L, 867533L, 900900L, 934266L, 967633L);

  private static final ImmutableList<Long> IMAGE_TIMESTAMPS_US_500_MS_30_FPS =
      ImmutableList.of(
          0L, 33333L, 66667L, 100000L, 133333L, 166667L, 200000L, 233333L, 266667L, 300000L,
          333333L, 366667L, 400000L, 433333L, 466667L);

  @Rule public final TestName testName = new TestName();

  @Rule
  public ActivityScenarioRule<SurfaceTestActivity> rule =
      new ActivityScenarioRule<>(SurfaceTestActivity.class);

  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context applicationContext = instrumentation.getContext().getApplicationContext();

  private ExoPlayer exoplayer;
  private CompositionPlayer compositionPlayer;
  private SurfaceView surfaceView;

  @Before
  public void setupSurfaces() {
    rule.getScenario().onActivity(activity -> surfaceView = activity.getSurfaceView());
  }

  @After
  public void closeActivity() {
    rule.getScenario().close();
  }

  @Test
  public void oneImageComposition_timestampsAreConsistent() throws Exception {
    long imageDurationUs = 500_000L;

    EditedMediaItem image =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(JPG_SINGLE_PIXEL_ASSET.uri)
                    .setImageDurationMs(usToMs(imageDurationUs))
                    .build())
            .setFrameRate(30)
            .build();

    compareTimestamps(
        ImmutableList.of(image), IMAGE_TIMESTAMPS_US_500_MS_30_FPS, /* containsImage= */ true);
  }

  @Test
  public void oneVideoComposition_timestampsAreConsistent() throws Exception {
    EditedMediaItem video =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    compareTimestamps(
        ImmutableList.of(video), MP4_ASSET_FRAME_TIMESTAMPS_US, /* containsImage= */ false);
  }

  @Test
  public void twoVideosComposition_clippingTheFirst_timestampsAreConsistent() throws Exception {
    long clippedStartUs = 500_000L;
    EditedMediaItem video1 =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(MP4_ASSET.uri)
                    .buildUpon()
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionUs(clippedStartUs)
                            .build())
                    .build())
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    EditedMediaItem video2 =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    ImmutableList<Long> expectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(getClippedTimestamps(MP4_ASSET_FRAME_TIMESTAMPS_US, clippedStartUs))
            .addAll(
                Lists.transform(
                    MP4_ASSET_FRAME_TIMESTAMPS_US,
                    timestampUs -> ((MP4_ASSET.videoDurationUs - clippedStartUs) + timestampUs)))
            .build();

    compareTimestamps(
        ImmutableList.of(video1, video2), expectedTimestamps, /* containsImage= */ false);
  }

  @Test
  public void twoVideosComposition_clippingBoth_timestampsAreConsistent() throws Exception {
    long clippedStartUs1 = 500_000L;
    EditedMediaItem video1 =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(MP4_ASSET.uri)
                    .buildUpon()
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionUs(clippedStartUs1)
                            .build())
                    .build())
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    long clippedStartUs2 = 300_000L;
    long clippedEndUs2 = 600_000L;
    EditedMediaItem video2 =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(MP4_ASSET.uri)
                    .buildUpon()
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionUs(clippedStartUs2)
                            .setEndPositionUs(clippedEndUs2)
                            .build())
                    .build())
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    ImmutableList<Long> expectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(getClippedTimestamps(MP4_ASSET_FRAME_TIMESTAMPS_US, clippedStartUs1))
            .addAll(
                Lists.transform(
                    getClippedTimestamps(
                        MP4_ASSET_FRAME_TIMESTAMPS_US, clippedStartUs2, clippedEndUs2),
                    timestampUs -> ((MP4_ASSET.videoDurationUs - clippedStartUs1) + timestampUs)))
            .build();

    compareTimestamps(
        ImmutableList.of(video1, video2), expectedTimestamps, /* containsImage= */ false);
  }

  @Test
  public void twoVideosComposition_timestampsAreConsistent() throws Exception {
    EditedMediaItem video1 =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    EditedMediaItem video2 =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    ImmutableList<Long> expectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(MP4_ASSET_FRAME_TIMESTAMPS_US)
            .addAll(
                Lists.transform(
                    MP4_ASSET_FRAME_TIMESTAMPS_US,
                    timestampUs -> (MP4_ASSET.videoDurationUs + timestampUs)))
            .build();

    compareTimestamps(
        ImmutableList.of(video1, video2), expectedTimestamps, /* containsImage= */ false);
  }

  @Test
  public void twoImagesComposition_timestampsAreConsistent() throws Exception {
    long imageDurationUs = 500_000L;

    EditedMediaItem image1 =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(JPG_SINGLE_PIXEL_ASSET.uri)
                    .setImageDurationMs(usToMs(imageDurationUs))
                    .build())
            .setFrameRate(30)
            .build();
    EditedMediaItem image2 =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(JPG_SINGLE_PIXEL_ASSET.uri)
                    .setImageDurationMs(usToMs(imageDurationUs))
                    .build())
            .setFrameRate(30)
            .build();

    ImmutableList<Long> expectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(IMAGE_TIMESTAMPS_US_500_MS_30_FPS)
            // The offset timestamps for image2.
            .addAll(
                Lists.transform(
                    IMAGE_TIMESTAMPS_US_500_MS_30_FPS,
                    timestampUs -> (timestampUs + imageDurationUs)))
            .build();

    compareTimestamps(
        ImmutableList.of(image1, image2), expectedTimestamps, /* containsImage= */ true);
  }

  @Test
  public void imageThenVideoComposition_timestampsAreConsistent() throws Exception {
    long imageDurationUs = 500_000L;

    EditedMediaItem image =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(JPG_SINGLE_PIXEL_ASSET.uri)
                    .setImageDurationMs(usToMs(imageDurationUs))
                    .build())
            .setFrameRate(30)
            .build();
    EditedMediaItem video =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();

    ImmutableList<Long> expectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(IMAGE_TIMESTAMPS_US_500_MS_30_FPS)
            .addAll(
                Lists.transform(
                    MP4_ASSET_FRAME_TIMESTAMPS_US, timestampUs -> (timestampUs + imageDurationUs)))
            .build();

    compareTimestamps(
        ImmutableList.of(image, video), expectedTimestamps, /* containsImage= */ true);
  }

  @Test
  public void videoThenImageComposition_timestampsAreConsistent() throws Exception {
    long imageDurationUs = 500_000L;

    EditedMediaItem video =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();
    EditedMediaItem image =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(JPG_SINGLE_PIXEL_ASSET.uri)
                    .setImageDurationMs(usToMs(imageDurationUs))
                    .build())
            .setFrameRate(30)
            .build();

    ImmutableList<Long> expectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(MP4_ASSET_FRAME_TIMESTAMPS_US)
            .addAll(
                Lists.transform(
                    IMAGE_TIMESTAMPS_US_500_MS_30_FPS,
                    timestampUs -> (MP4_ASSET.videoDurationUs + timestampUs)))
            .build();

    compareTimestamps(
        ImmutableList.of(video, image), expectedTimestamps, /* containsImage= */ true);
  }

  @Test
  public void videoThenImageComposition_clippingVideo_timestampsAreConsistent() throws Exception {
    long clippedStartUs = 500_000L;
    long imageDurationUs = 500_000L;

    EditedMediaItem video =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(MP4_ASSET.uri)
                    .buildUpon()
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(usToMs(clippedStartUs))
                            .build())
                    .build())
            .setDurationUs(MP4_ASSET.videoDurationUs)
            .build();
    EditedMediaItem image =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(JPG_SINGLE_PIXEL_ASSET.uri)
                    .setImageDurationMs(usToMs(imageDurationUs))
                    .build())
            .setFrameRate(30)
            .build();

    ImmutableList<Long> expectedTimestamps =
        new ImmutableList.Builder<Long>()
            .addAll(getClippedTimestamps(MP4_ASSET_FRAME_TIMESTAMPS_US, clippedStartUs))
            .addAll(
                Lists.transform(
                    IMAGE_TIMESTAMPS_US_500_MS_30_FPS,
                    timestampUs -> ((MP4_ASSET.videoDurationUs - clippedStartUs) + timestampUs)))
            .build();

    compareTimestamps(
        ImmutableList.of(video, image), expectedTimestamps, /* containsImage= */ true);
  }

  private void compareTimestamps(
      List<EditedMediaItem> mediaItems, List<Long> expectedTimestamps, boolean containsImage)
      throws Exception {
    ImmutableList<Long> timestampsFromTransformer = getTimestampsFromTransformer(mediaItems);
    assertThat(timestampsFromTransformer).isEqualTo(expectedTimestamps);

    ImmutableList<Long> timestampsFromCompositionPlayer =
        getTimestampsFromCompositionPlayer(mediaItems);
    assertThat(timestampsFromCompositionPlayer).isEqualTo(expectedTimestamps);

    if (!containsImage) {
      // ExoPlayer doesn't support image playback with effects.
      ImmutableList<Long> timestampsFromExoPlayer =
          getTimestampsFromExoPlayer(
              Lists.transform(mediaItems, editedMediaItem -> editedMediaItem.mediaItem));
      assertThat(timestampsFromExoPlayer).isEqualTo(expectedTimestamps);
    }
  }

  private ImmutableList<Long> getTimestampsFromTransformer(List<EditedMediaItem> editedMediaItems)
      throws Exception {
    InputTimestampRecordingShaderProgram timestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    ImmutableList<EditedMediaItem> timestampRecordingEditedMediaItems =
        prependVideoEffects(
            editedMediaItems,
            /* effects= */ ImmutableList.of(
                (GlEffect) (context, useHdr) -> timestampRecordingShaderProgram,
                // Use a resolution that all devices should support.
                Presentation.createForWidthAndHeight(
                    /* width= */ 320, /* height= */ 240, Presentation.LAYOUT_SCALE_TO_FIT)));

    @SuppressWarnings("unused")
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(
                applicationContext, new Transformer.Builder(applicationContext).build())
            .build()
            .run(
                /* testId= */ testName.getMethodName(),
                new Composition.Builder(
                        new EditedMediaItemSequence.Builder(timestampRecordingEditedMediaItems)
                            .build())
                    .experimentalSetForceAudioTrack(true)
                    .build());

    return timestampRecordingShaderProgram.getInputTimestampsUs();
  }

  private ImmutableList<Long> getTimestampsFromCompositionPlayer(
      List<EditedMediaItem> editedMediaItems) throws Exception {
    PlayerTestListener compositionPlayerListener = new PlayerTestListener(TEST_TIMEOUT_MS);
    InputTimestampRecordingShaderProgram timestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    ImmutableList<EditedMediaItem> timestampRecordingEditedMediaItems =
        prependVideoEffects(
            editedMediaItems,
            /* effects= */ ImmutableList.of(
                (GlEffect) (context, useHdr) -> timestampRecordingShaderProgram));

    instrumentation.runOnMainSync(
        () -> {
          compositionPlayer = new CompositionPlayer.Builder(applicationContext).build();
          // Set a surface on the player even though there is no UI on this test. We need a surface
          // otherwise the player will skip/drop video frames.
          compositionPlayer.setVideoSurfaceView(surfaceView);
          compositionPlayer.addListener(compositionPlayerListener);
          compositionPlayer.setComposition(
              new Composition.Builder(
                      new EditedMediaItemSequence.Builder(timestampRecordingEditedMediaItems)
                          .build())
                  .experimentalSetForceAudioTrack(true)
                  .build());
          compositionPlayer.prepare();
          compositionPlayer.play();
        });

    compositionPlayerListener.waitUntilPlayerEnded();
    instrumentation.runOnMainSync(() -> compositionPlayer.release());

    return timestampRecordingShaderProgram.getInputTimestampsUs();
  }

  private ImmutableList<Long> getTimestampsFromExoPlayer(List<MediaItem> mediaItems)
      throws Exception {
    PlayerTestListener playerListener = new PlayerTestListener(TEST_TIMEOUT_MS);
    InputTimestampRecordingShaderProgram timestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();

    instrumentation.runOnMainSync(
        () -> {
          exoplayer = new ExoPlayer.Builder(applicationContext).build();
          // Set a surface on the player even though there is no UI on this test. We need a surface
          // otherwise the player will skip/drop video frames.
          exoplayer.setVideoSurfaceView(surfaceView);
          exoplayer.addListener(playerListener);
          exoplayer.setMediaItems(mediaItems);
          exoplayer.setVideoEffects(
              ImmutableList.of((GlEffect) (context, useHdr) -> timestampRecordingShaderProgram));
          exoplayer.prepare();
          exoplayer.play();
        });

    playerListener.waitUntilPlayerEnded();
    instrumentation.runOnMainSync(() -> exoplayer.release());

    return timestampRecordingShaderProgram.getInputTimestampsUs();
  }

  private static ImmutableList<EditedMediaItem> prependVideoEffects(
      List<EditedMediaItem> editedMediaItems, List<Effect> effects) {
    ImmutableList.Builder<EditedMediaItem> prependedItems = new ImmutableList.Builder<>();
    for (EditedMediaItem editedMediaItem : editedMediaItems) {
      prependedItems.add(
          editedMediaItem
              .buildUpon()
              .setEffects(
                  new Effects(
                      editedMediaItem.effects.audioProcessors,
                      new ImmutableList.Builder<Effect>()
                          .addAll(effects)
                          .addAll(editedMediaItem.effects.videoEffects)
                          .build()))
              .build());
    }
    return prependedItems.build();
  }

  private static ImmutableList<Long> getClippedTimestamps(List<Long> timestamps, long clipStartUs) {
    return getClippedTimestamps(timestamps, clipStartUs, /* clipEndUs= */ C.TIME_UNSET);
  }

  private static ImmutableList<Long> getClippedTimestamps(
      List<Long> timestamps, long clipStartUs, long clipEndUs) {
    ImmutableList.Builder<Long> clippedTimestamps = new ImmutableList.Builder<>();
    for (Long timestamp : timestamps) {
      if (timestamp < clipStartUs || (clipEndUs != C.TIME_UNSET && timestamp > clipEndUs)) {
        continue;
      }
      clippedTimestamps.add(timestamp - clipStartUs);
    }
    return clippedTimestamps.build();
  }
}
