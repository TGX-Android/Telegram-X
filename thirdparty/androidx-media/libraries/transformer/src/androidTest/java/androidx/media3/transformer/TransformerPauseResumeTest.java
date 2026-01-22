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
package androidx.media3.transformer;

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.os.Build;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.Util;
import androidx.media3.effect.RgbFilter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** End-to-end instrumentation tests for {@link Transformer} pause and resume scenarios. */
@RunWith(AndroidJUnit4.class)
public class TransformerPauseResumeTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final long PRESENTATION_TIME_US_TO_BLOCK_FRAME = 5_000_000L;
  private static final int DEFAULT_TIMEOUT_SECONDS = 120;
  private static final int MP4_ASSET_FRAME_COUNT = 932;

  private final Context context = getApplicationContext();
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void resume_withSingleMediaItem_outputMatchesExpected() throws Exception {
    assumeFalse(shouldSkipDevice());
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat);
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 1);
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);

    // Resume the export.
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition, firstOutputPath);

    ExportResult exportResult = result.exportResult;
    assertThat(exportResult.processedInputs).hasSize(4);
    assertThat(exportResult.videoFrameCount).isEqualTo(MP4_ASSET_FRAME_COUNT);
    // The first processed media item corresponds to remuxing previous output video.
    assertThat(exportResult.processedInputs.get(0).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(0).videoDecoderName).isNull();
    // The second processed media item corresponds to processing remaining video.
    assertThat(exportResult.processedInputs.get(1).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(1).videoDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(1).mediaItem.clippingConfiguration.startPositionMs)
        .isGreaterThan(0);
    // The third processed media item corresponds to processing audio.
    assertThat(exportResult.processedInputs.get(2).audioDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(2).videoDecoderName).isNull();
    // The fourth processed media item corresponds to transmuxing processed video.
    assertThat(exportResult.processedInputs.get(3).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(3).videoDecoderName).isNull();
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void resume_withSingleMediaItemAfterImmediateCancellation_restartsExport()
      throws Exception {
    assumeFalse(shouldSkipDevice());
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat);
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 1);
    Transformer transformer = new Transformer.Builder(context).build();
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              transformer.start(composition, firstOutputPath);
              transformer.cancel();
            });

    // Resume the export.
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition, firstOutputPath);

    ExportResult exportResult = result.exportResult;
    // The first export did not progress because of the immediate cancellation hence resuming
    // actually restarts the export.
    assertThat(exportResult.processedInputs).hasSize(1);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void resume_withSingleMediaItem_outputMatchesWithoutResume() throws Exception {
    assumeFalse(shouldSkipDevice());
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat);
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 1);
    // Export without resume.
    ExportResult exportResultWithoutResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition)
            .exportResult;
    // Export with resume.
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);

    // Resume the export.
    ExportTestResult resultWithResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition, firstOutputPath);

    ExportResult exportResultWithResume = resultWithResume.exportResult;
    assertThat(exportResultWithResume.processedInputs).hasSize(4);
    assertThat(exportResultWithResume.audioEncoderName)
        .isEqualTo(exportResultWithoutResume.audioEncoderName);
    assertThat(exportResultWithResume.videoEncoderName)
        .isEqualTo(exportResultWithoutResume.videoEncoderName);
    assertThat(exportResultWithResume.videoFrameCount)
        .isEqualTo(exportResultWithoutResume.videoFrameCount);
    // TODO: b/306595508 - Remove this expected difference once inconsistent behaviour of audio
    //  encoder is fixed.
    int maxDiffExpectedInDurationMs = 2;
    assertThat(exportResultWithResume.durationMs - exportResultWithoutResume.durationMs)
        .isLessThan(maxDiffExpectedInDurationMs);
    assertThat(new File(resultWithResume.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void resume_withSingleMediaItemHavingClippingConfig_outputMatchesWithoutResume()
      throws Exception {
    assumeFalse(shouldSkipDevice());
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat);
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 2_000L,
            /* clippingEndPositionMs= */ 13_000L,
            /* mediaItemsInSequence= */ 1);
    // Export without resume.
    ExportResult exportResultWithoutResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition)
            .exportResult;
    // Export with resume.
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);

    // Resume the export.
    ExportTestResult resultWithResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition, firstOutputPath);

    ExportResult exportResultWithResume = resultWithResume.exportResult;
    assertThat(exportResultWithResume.processedInputs).hasSize(4);
    assertThat(exportResultWithResume.audioEncoderName)
        .isEqualTo(exportResultWithoutResume.audioEncoderName);
    assertThat(exportResultWithResume.videoEncoderName)
        .isEqualTo(exportResultWithoutResume.videoEncoderName);
    assertThat(exportResultWithResume.videoFrameCount)
        .isEqualTo(exportResultWithoutResume.videoFrameCount);
    int maxDiffExpectedInDurationMs = 2;
    assertThat(exportResultWithResume.durationMs - exportResultWithoutResume.durationMs)
        .isLessThan(maxDiffExpectedInDurationMs);
    assertThat(new File(resultWithResume.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void resume_withTwoMediaItems_outputMatchesExpected() throws Exception {
    assumeFalse(shouldSkipDevice());
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat);
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 2);
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition, firstOutputPath);

    ExportResult exportResult = result.exportResult;
    assertThat(exportResult.processedInputs).hasSize(6);
    int expectedVideoFrameCount = 2 * MP4_ASSET_FRAME_COUNT;
    assertThat(exportResult.videoFrameCount).isEqualTo(expectedVideoFrameCount);
    // The first processed media item corresponds to remuxing previous output video.
    assertThat(exportResult.processedInputs.get(0).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(0).videoDecoderName).isNull();
    // The next two processed media item corresponds to processing remaining video.
    assertThat(exportResult.processedInputs.get(1).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(1).videoDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(1).mediaItem.clippingConfiguration.startPositionMs)
        .isGreaterThan(0);
    assertThat(exportResult.processedInputs.get(2).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(2).videoDecoderName).isNotEmpty();
    // The next two processed media item corresponds to processing audio.
    assertThat(exportResult.processedInputs.get(3).audioDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(3).videoDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(4).audioDecoderName).isNotEmpty();
    assertThat(exportResult.processedInputs.get(4).videoDecoderName).isNull();
    // The last processed media item corresponds to transmuxing processed video.
    assertThat(exportResult.processedInputs.get(5).audioDecoderName).isNull();
    assertThat(exportResult.processedInputs.get(5).videoDecoderName).isNull();
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void resume_withTwoMediaItems_outputMatchesWithoutResume() throws Exception {
    assumeFalse(shouldSkipDevice());
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat);
    Composition composition =
        buildSingleSequenceComposition(
            /* clippingStartPositionMs= */ 0,
            /* clippingEndPositionMs= */ C.TIME_END_OF_SOURCE,
            /* mediaItemsInSequence= */ 2);
    // Export without resume.
    ExportResult exportResultWithoutResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition)
            .exportResult;
    // Export with resume.
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer blockingTransformer = buildBlockingTransformer(countDownLatch::countDown);
    String firstOutputPath = temporaryFolder.newFile("FirstOutput.mp4").getAbsolutePath();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> blockingTransformer.start(composition, firstOutputPath));
    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(blockingTransformer::cancel);

    ExportTestResult resultWithResume =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition, firstOutputPath);

    ExportResult exportResultWithResume = resultWithResume.exportResult;
    assertThat(exportResultWithResume.processedInputs).hasSize(6);
    assertThat(exportResultWithResume.audioEncoderName)
        .isEqualTo(exportResultWithoutResume.audioEncoderName);
    assertThat(exportResultWithResume.videoEncoderName)
        .isEqualTo(exportResultWithoutResume.videoEncoderName);
    assertThat(exportResultWithResume.videoFrameCount)
        .isEqualTo(exportResultWithoutResume.videoFrameCount);
    int maxDiffExpectedInDurationMs = 2;
    assertThat(exportResultWithResume.durationMs - exportResultWithoutResume.durationMs)
        .isLessThan(maxDiffExpectedInDurationMs);
    assertThat(new File(resultWithResume.filePath).length()).isGreaterThan(0);
  }

  private static Composition buildSingleSequenceComposition(
      long clippingStartPositionMs, long clippingEndPositionMs, int mediaItemsInSequence) {
    SonicAudioProcessor sonic = new SonicAudioProcessor();
    sonic.setPitch(/* pitch= */ 2f);
    ImmutableList<AudioProcessor> audioEffects = ImmutableList.of(sonic);

    ImmutableList<Effect> videoEffects = ImmutableList.of(RgbFilter.createInvertedFilter());

    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.uri)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clippingStartPositionMs)
                            .setEndPositionMs(clippingEndPositionMs)
                            .build())
                    .build())
            .setEffects(new Effects(audioEffects, videoEffects))
            .build();

    List<EditedMediaItem> editedMediaItemList = new ArrayList<>();
    while (mediaItemsInSequence-- > 0) {
      editedMediaItemList.add(editedMediaItem);
    }

    return new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItemList).build())
        .build();
  }

  private static Transformer buildBlockingTransformer(
      AndroidTestUtil.FrameBlockingMuxer.Listener listener) {
    return new Transformer.Builder(getApplicationContext())
        .setMuxerFactory(
            new AndroidTestUtil.FrameBlockingMuxerFactory(
                PRESENTATION_TIME_US_TO_BLOCK_FRAME, listener))
        .build();
  }

  private static boolean shouldSkipDevice() {
    // v26 emulators are not producing I-frames, due to which resuming export does not work as
    // expected.
    // On vivo 1820 and vivo 1906, the process crashes unexpectedly (see b/310566201).
    return (Util.SDK_INT == 26 && Util.isRunningOnEmulator())
        || (Util.SDK_INT == 27 && Ascii.equalsIgnoreCase(Build.MODEL, "vivo 1820"))
        || (Util.SDK_INT == 28 && Ascii.equalsIgnoreCase(Build.MODEL, "vivo 1901"))
        || (Util.SDK_INT == 28 && Ascii.equalsIgnoreCase(Build.MODEL, "vivo 1906"));
  }
}
