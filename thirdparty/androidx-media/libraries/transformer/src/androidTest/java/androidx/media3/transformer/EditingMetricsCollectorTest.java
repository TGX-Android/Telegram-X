/*
 * Copyright 2025 The Android Open Source Project
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

import static androidx.media3.common.util.Util.usToMs;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.MediaCodec;
import android.media.metrics.EditingEndedEvent;
import android.media.metrics.LogSessionId;
import android.media.metrics.MediaItemInfo;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Util;
import androidx.media3.muxer.MuxerException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Instrumentation tests for metrics reporting using {@link EditingMetricsCollector} */
@RunWith(AndroidJUnit4.class)
public class EditingMetricsCollectorTest {
  private static final String EXPORTER_NAME =
      "androidx.media3:media3-transformer:" + MediaLibraryInfo.VERSION;
  private static final int DEFAULT_TIMEOUT_SECONDS = 120;
  private static final long PRESENTATION_TIME_US_TO_BLOCK_FRAME = 50_000L;

  @Rule public final TestName testName = new TestName();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final Context context = InstrumentationRegistry.getInstrumentation().getContext();
  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void export_usePlatformDiagnosticsDisabled_doesNotCollectMetrics() throws Exception {
    if (Util.SDK_INT < 35) {
      String reason = "Metrics collection is unsupported below API 35.";
      recordTestSkipped(context, testId, reason);
      throw new AssumptionViolatedException(reason);
    }
    AtomicReference<EditingEndedEvent> editingEndedEventAtomicReference = new AtomicReference<>();
    Transformer transformer =
        new Transformer.Builder(context)
            .setUsePlatformDiagnostics(false)
            .setMetricsReporterFactory(
                new TestMetricsReporterFactory(
                    context,
                    new TestMetricsReporter.Listener() {

                      @Override
                      public void onMetricsReported(EditingEndedEvent editingEndedEvent) {
                        editingEndedEventAtomicReference.set(editingEndedEvent);
                      }
                    }))
            .build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri)).build();

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(testId, audioVideoItem);

    assertThat(editingEndedEventAtomicReference.get()).isNull();
  }

  @Test
  public void exportSuccess_populatesEditingEndedEvent() throws Exception {
    assumeTrue("Reporting metrics requires API 35", Util.SDK_INT >= 35);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);
    AtomicReference<EditingEndedEvent> editingEndedEventAtomicReference = new AtomicReference<>();
    Transformer transformer =
        new Transformer.Builder(context)
            .setUsePlatformDiagnostics(true)
            .setMetricsReporterFactory(
                new TestMetricsReporterFactory(
                    context,
                    new TestMetricsReporter.Listener() {

                      @Override
                      public void onMetricsReported(EditingEndedEvent editingEndedEvent) {
                        editingEndedEventAtomicReference.set(editingEndedEvent);
                      }
                    }))
            .build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri)).build();
    EditedMediaItem imageItem =
        new EditedMediaItem.Builder(
                new MediaItem.Builder().setUri(JPG_ASSET.uri).setImageDurationMs(1500).build())
            .setFrameRate(30)
            .build();
    EditedMediaItemSequence videoImageSequence =
        new EditedMediaItemSequence.Builder(audioVideoItem, imageItem).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence.Builder(
                new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
                    .setRemoveVideo(true)
                    .build())
            .build();
    Composition composition = new Composition.Builder(videoImageSequence, audioSequence).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    EditingEndedEvent editingEndedEvent = editingEndedEventAtomicReference.get();
    assertThat(editingEndedEvent.getFinalState())
        .isEqualTo(EditingEndedEvent.FINAL_STATE_SUCCEEDED);
    assertThat(editingEndedEvent.getTimeSinceCreatedMillis()).isAtLeast(0);
    assertThat(editingEndedEvent.getExporterName()).isEqualTo(EXPORTER_NAME);
    assertThat(editingEndedEvent.getMuxerName()).isEqualTo(DefaultMuxer.MUXER_NAME);
    assertThat(editingEndedEvent.getFinalProgressPercent()).isEqualTo(100);
    // Assert video input media item information
    MediaItemInfo firstMediaItemInfo = editingEndedEvent.getInputMediaItemInfos().get(0);
    ExportResult.ProcessedInput firstProcessedInput =
        exportTestResult.exportResult.processedInputs.get(0);
    assertThat(firstMediaItemInfo.getClipDurationMillis())
        .isEqualTo(usToMs(firstProcessedInput.durationUs));
    assertThat(firstMediaItemInfo.getDataTypes())
        .isEqualTo(MediaItemInfo.DATA_TYPE_VIDEO | MediaItemInfo.DATA_TYPE_AUDIO);
    assertThat(firstMediaItemInfo.getContainerMimeType())
        .isEqualTo(firstProcessedInput.videoFormat.containerMimeType);
    assertThat(firstMediaItemInfo.getSampleMimeTypes().get(0))
        .isEqualTo(firstProcessedInput.videoFormat.sampleMimeType);
    assertThat(firstMediaItemInfo.getVideoFrameRate())
        .isEqualTo(firstProcessedInput.videoFormat.frameRate);
    assertThat(firstMediaItemInfo.getVideoSize())
        .isEqualTo(
            new Size(
                firstProcessedInput.videoFormat.width, firstProcessedInput.videoFormat.height));
    assertThat(firstMediaItemInfo.getSampleMimeTypes().get(1))
        .isEqualTo(firstProcessedInput.audioFormat.sampleMimeType);
    assertThat(firstMediaItemInfo.getAudioChannelCount())
        .isEqualTo(firstProcessedInput.audioFormat.channelCount);
    assertThat(firstMediaItemInfo.getAudioSampleRateHz())
        .isEqualTo(firstProcessedInput.audioFormat.sampleRate);
    // Assert image input media item and its silent audio
    MediaItemInfo secondMediaItemInfo = editingEndedEvent.getInputMediaItemInfos().get(1);
    ExportResult.ProcessedInput secondProcessedInput =
        exportTestResult.exportResult.processedInputs.get(1);
    assertThat(secondMediaItemInfo.getClipDurationMillis())
        .isEqualTo(usToMs(secondProcessedInput.durationUs));
    assertThat(secondMediaItemInfo.getDataTypes())
        .isEqualTo(MediaItemInfo.DATA_TYPE_IMAGE | MediaItemInfo.DATA_TYPE_AUDIO);
    assertThat(secondMediaItemInfo.getContainerMimeType())
        .isEqualTo(secondProcessedInput.videoFormat.containerMimeType);
    assertThat(secondMediaItemInfo.getSampleMimeTypes().get(0))
        .isEqualTo(secondProcessedInput.videoFormat.sampleMimeType);
    assertThat(secondMediaItemInfo.getVideoFrameRate())
        .isEqualTo(secondProcessedInput.videoFormat.frameRate);
    assertThat(secondMediaItemInfo.getVideoSize())
        .isEqualTo(
            new Size(
                secondProcessedInput.videoFormat.width, secondProcessedInput.videoFormat.height));
    assertThat(secondMediaItemInfo.getSampleMimeTypes().get(1))
        .isEqualTo(secondProcessedInput.audioFormat.sampleMimeType);
    assertThat(secondMediaItemInfo.getAudioChannelCount())
        .isEqualTo(secondProcessedInput.audioFormat.channelCount);
    assertThat(secondMediaItemInfo.getAudioSampleRateHz())
        .isEqualTo(secondProcessedInput.audioFormat.sampleRate);
    // Assert audio input media item information
    MediaItemInfo thirdMediaItemInfo = editingEndedEvent.getInputMediaItemInfos().get(2);
    ExportResult.ProcessedInput thirdProcessedInput =
        exportTestResult.exportResult.processedInputs.get(2);
    assertThat(thirdMediaItemInfo.getClipDurationMillis())
        .isEqualTo(usToMs(thirdProcessedInput.durationUs));
    assertThat(thirdMediaItemInfo.getDataTypes()).isEqualTo(MediaItemInfo.DATA_TYPE_AUDIO);
    assertThat(thirdMediaItemInfo.getSampleMimeTypes().get(0))
        .isEqualTo(thirdProcessedInput.audioFormat.sampleMimeType);
    assertThat(thirdMediaItemInfo.getAudioChannelCount())
        .isEqualTo(thirdProcessedInput.audioFormat.channelCount);
    assertThat(thirdMediaItemInfo.getAudioSampleRateHz())
        .isEqualTo(thirdProcessedInput.audioFormat.sampleRate);
    // Assert output media item information
    MediaItemInfo outputMediaItemInfo = editingEndedEvent.getOutputMediaItemInfo();
    assertThat(outputMediaItemInfo).isNotNull();
    assertThat(outputMediaItemInfo.getDurationMillis())
        .isEqualTo(exportTestResult.exportResult.durationMs);
    assertThat(outputMediaItemInfo.getSampleMimeTypes()).isNotEmpty();
    assertThat(outputMediaItemInfo.getAudioChannelCount())
        .isEqualTo(exportTestResult.exportResult.channelCount);
    assertThat(outputMediaItemInfo.getAudioSampleRateHz())
        .isEqualTo(exportTestResult.exportResult.sampleRate);
    assertThat(outputMediaItemInfo.getCodecNames().get(0))
        .isEqualTo(exportTestResult.exportResult.audioEncoderName);
    assertThat(outputMediaItemInfo.getCodecNames().get(1))
        .isEqualTo(exportTestResult.exportResult.videoEncoderName);
    assertThat(outputMediaItemInfo.getVideoSampleCount())
        .isEqualTo(exportTestResult.exportResult.videoFrameCount);
    assertThat(outputMediaItemInfo.getVideoSize())
        .isEqualTo(
            new Size(exportTestResult.exportResult.width, exportTestResult.exportResult.height));
  }

  @Test
  public void exportError_populatesEditingEndedEvent() throws Exception {
    assumeTrue("Reporting metrics requires API 35", Util.SDK_INT >= 35);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);
    AtomicReference<EditingEndedEvent> editingEndedEventAtomicReference = new AtomicReference<>();
    Transformer transformer =
        new Transformer.Builder(context)
            .setUsePlatformDiagnostics(true)
            .setMetricsReporterFactory(
                new TestMetricsReporterFactory(
                    context,
                    new TestMetricsReporter.Listener() {

                      @Override
                      public void onMetricsReported(EditingEndedEvent editingEndedEvent) {
                        editingEndedEventAtomicReference.set(editingEndedEvent);
                      }
                    }))
            .setMuxerFactory(new FailingMuxerFactory())
            .build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri)).build();

    assertThrows(
        ExportException.class,
        () ->
            new TransformerAndroidTestRunner.Builder(context, transformer)
                .build()
                .run(testId, audioVideoItem));

    EditingEndedEvent editingEndedEvent = editingEndedEventAtomicReference.get();
    assertThat(editingEndedEvent.getFinalState()).isEqualTo(EditingEndedEvent.FINAL_STATE_ERROR);
    assertThat(editingEndedEvent.getTimeSinceCreatedMillis()).isAtLeast(0);
    assertThat(editingEndedEvent.getExporterName()).isEqualTo(EXPORTER_NAME);
    assertThat(editingEndedEvent.getFinalProgressPercent()).isIn(Range.closed(0f, 100f));
    assertThat(editingEndedEvent.getErrorCode())
        .isEqualTo(EditingEndedEvent.ERROR_CODE_MUXING_FAILED);
  }

  @Test
  public void exportCancelled_populatesEditingEndedEvent() throws Exception {
    assumeTrue("Reporting metrics requires API 35", Util.SDK_INT >= 35);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);
    AtomicReference<EditingEndedEvent> editingEndedEventAtomicReference = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Transformer transformer =
        new Transformer.Builder(context)
            .setUsePlatformDiagnostics(true)
            .setMetricsReporterFactory(
                new TestMetricsReporterFactory(
                    context,
                    new TestMetricsReporter.Listener() {

                      @Override
                      public void onMetricsReported(EditingEndedEvent editingEndedEvent) {
                        editingEndedEventAtomicReference.set(editingEndedEvent);
                      }
                    }))
            .setMuxerFactory(
                new AndroidTestUtil.FrameBlockingMuxerFactory(
                    PRESENTATION_TIME_US_TO_BLOCK_FRAME, countDownLatch::countDown))
            .build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri)).build();
    String outputPath = temporaryFolder.newFile("output.mp4").getAbsolutePath();

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> transformer.start(audioVideoItem, outputPath));
    if (!countDownLatch.await(DEFAULT_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException(
          "Transformer timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds.");
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(transformer::cancel);

    EditingEndedEvent editingEndedEvent = editingEndedEventAtomicReference.get();
    assertThat(editingEndedEvent.getFinalState()).isEqualTo(EditingEndedEvent.FINAL_STATE_CANCELED);
    assertThat(editingEndedEvent.getTimeSinceCreatedMillis()).isAtLeast(0);
    assertThat(editingEndedEvent.getExporterName()).isEqualTo(EXPORTER_NAME);
    assertThat(editingEndedEvent.getFinalProgressPercent()).isIn(Range.closed(0f, 100f));
  }

  @Test
  public void exportTwice_createsUniqueSessions() throws Exception {
    assumeTrue("Reporting metrics requires API 35", Util.SDK_INT >= 35);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);
    AtomicReference<LogSessionId> logSessionIdAtomicReference = new AtomicReference<>();
    Transformer transformer =
        new Transformer.Builder(context)
            .setUsePlatformDiagnostics(true)
            .setMetricsReporterFactory(
                new TestMetricsReporterFactory(
                    context,
                    new TestMetricsReporter.Listener() {
                      @Override
                      public void onMetricsReporterCreated(LogSessionId logSessionId) {
                        logSessionIdAtomicReference.set(logSessionId);
                      }
                    }))
            .build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri)).build();

    TransformerAndroidTestRunner transformerAndroidTestRunner =
        new TransformerAndroidTestRunner.Builder(context, transformer).build();
    transformerAndroidTestRunner.run(testId, audioVideoItem);
    LogSessionId firstLogSessionId = logSessionIdAtomicReference.get();
    transformerAndroidTestRunner.run(testId, audioVideoItem);
    LogSessionId secondLogSessionId = logSessionIdAtomicReference.get();

    assertThat(firstLogSessionId.getStringId()).isNotEqualTo(secondLogSessionId.getStringId());
  }

  private static final class TestMetricsReporterFactory
      implements EditingMetricsCollector.MetricsReporter.Factory {

    private final EditingMetricsCollector.MetricsReporter.Factory wrappedMetricsReporterFactory;
    private final TestMetricsReporter.Listener listener;

    public TestMetricsReporterFactory(Context context, TestMetricsReporter.Listener listener) {
      this.wrappedMetricsReporterFactory =
          new EditingMetricsCollector.DefaultMetricsReporter.Factory(context);
      this.listener = listener;
    }

    @Override
    public EditingMetricsCollector.MetricsReporter create() {
      return new TestMetricsReporter(wrappedMetricsReporterFactory.create(), listener);
    }
  }

  private static final class TestMetricsReporter
      implements EditingMetricsCollector.MetricsReporter {
    public interface Listener {
      default void onMetricsReporterCreated(LogSessionId logSessionId) {}

      default void onMetricsReported(EditingEndedEvent editingEndedEvent) {}
    }

    private final EditingMetricsCollector.MetricsReporter wrappedMetricsReporter;
    private final TestMetricsReporter.Listener listener;

    private TestMetricsReporter(
        EditingMetricsCollector.MetricsReporter metricsReporter,
        TestMetricsReporter.Listener listener) {
      this.wrappedMetricsReporter = metricsReporter;
      this.listener = listener;
      if (wrappedMetricsReporter instanceof EditingMetricsCollector.DefaultMetricsReporter) {
        @Nullable
        LogSessionId logSessionId =
            ((EditingMetricsCollector.DefaultMetricsReporter) wrappedMetricsReporter)
                .getLogSessionId();
        if (logSessionId != null) {
          listener.onMetricsReporterCreated(logSessionId);
        }
      }
    }

    @Override
    public void reportMetrics(EditingEndedEvent editingEndedEvent) {
      listener.onMetricsReported(editingEndedEvent);
      wrappedMetricsReporter.reportMetrics(editingEndedEvent);
    }

    @Override
    public void close() throws Exception {
      wrappedMetricsReporter.close();
    }
  }

  private static final class FailingMuxerFactory implements Muxer.Factory {

    private final Muxer.Factory wrappedMuxerFactory;

    public FailingMuxerFactory() {
      this.wrappedMuxerFactory = new DefaultMuxer.Factory();
    }

    @Override
    public Muxer create(String path) throws MuxerException {
      return new FailingMuxer(wrappedMuxerFactory.create(path));
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return wrappedMuxerFactory.getSupportedSampleMimeTypes(trackType);
    }
  }

  private static final class FailingMuxer implements Muxer {
    private final Muxer wrappedMuxer;
    private boolean firstSampleWritten;

    public FailingMuxer(Muxer wrappedMuxer) {
      this.wrappedMuxer = wrappedMuxer;
    }

    @Override
    public int addTrack(Format format) throws MuxerException {
      return wrappedMuxer.addTrack(format);
    }

    @Override
    public void writeSampleData(
        int trackId, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo)
        throws MuxerException {
      if (firstSampleWritten) {
        throw new MuxerException("Failed to write sample data", new RuntimeException());
      }
      wrappedMuxer.writeSampleData(trackId, byteBuffer, bufferInfo);
      firstSampleWritten = true;
    }

    @Override
    public void addMetadataEntry(Metadata.Entry metadataEntry) {
      wrappedMuxer.addMetadataEntry(metadataEntry);
    }

    @Override
    public void close() throws MuxerException {
      wrappedMuxer.close();
    }
  }
}
