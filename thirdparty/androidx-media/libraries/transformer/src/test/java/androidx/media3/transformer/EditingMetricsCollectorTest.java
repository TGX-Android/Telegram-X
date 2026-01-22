/*
 * Copyright 2025 The Android Open Source Project
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

import static androidx.media3.common.util.Util.usToMs;
import static com.google.common.truth.Truth.assertThat;

import android.hardware.DataSpace;
import android.media.metrics.EditingEndedEvent;
import android.media.metrics.MediaItemInfo;
import android.util.Size;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Unit tests for {@link EditingMetricsCollector}. */
@Config(minSdk = 35)
@RunWith(AndroidJUnit4.class)
public final class EditingMetricsCollectorTest {

  private static final int MEDIA_DURATION_US = 1_000_000_000;
  private static final String EXPORTER_NAME =
      "androidx.media3:media3-transformer:" + MediaLibraryInfo.VERSION;
  private static final String MUXER_NAME =
      "androidx.media3:media3-muxer:" + MediaLibraryInfo.VERSION;
  private static final String VIDEO_MIME_TYPE = "video/hevc";
  private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
  private static final String VIDEO_ENCODER_NAME = "c2.android.hevc.encoder";
  private static final String AUDIO_ENCODER_NAME = "c2.android.aac.encoder";
  private static final String VIDEO_DECODER_NAME = "c2.android.hevc.decoder";
  private static final String AUDIO_DECODER_NAME = "c2.android.aac.decoder";
  private static final float VIDEO_FRAME_RATE = 30.0f;
  private static final Size VIDEO_SIZE = new Size(/* width= */ 1920, /* height= */ 1080);
  private static final ColorInfo VIDEO_COLOR_INFO =
      new ColorInfo.Builder()
          .setColorSpace(C.COLOR_SPACE_BT2020)
          .setColorTransfer(C.COLOR_TRANSFER_ST2084)
          .setColorRange(C.COLOR_RANGE_LIMITED)
          .build();
  private static final int AUDIO_SAMPLE_RATE = 48_000;
  private static final int AUDIO_CHANNEL_COUNT = 2;
  private static final Format FAKE_AUDIO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(AUDIO_MIME_TYPE)
          .setSampleRate(AUDIO_SAMPLE_RATE)
          .setChannelCount(AUDIO_CHANNEL_COUNT)
          .setPcmEncoding(C.ENCODING_PCM_16BIT)
          .build();
  private static final Format FAKE_VIDEO_FORMAT =
      new Format.Builder()
          .setContainerMimeType(VIDEO_MIME_TYPE)
          .setSampleMimeType(VIDEO_MIME_TYPE)
          .setFrameRate(VIDEO_FRAME_RATE)
          .setWidth(VIDEO_SIZE.getWidth())
          .setHeight(VIDEO_SIZE.getHeight())
          .setColorInfo(VIDEO_COLOR_INFO)
          .build();

  @Test
  public void onExportSuccess_populatesEditingEndedEvent() {
    List<ExportResult.ProcessedInput> processedInputs = new ArrayList<>();
    processedInputs.add(
        new ExportResult.ProcessedInput(
            MediaItem.EMPTY,
            MEDIA_DURATION_US,
            FAKE_AUDIO_FORMAT,
            FAKE_VIDEO_FORMAT,
            AUDIO_DECODER_NAME,
            VIDEO_DECODER_NAME));
    ExportResult exportResult =
        new ExportResult.Builder()
            .setDurationMs(usToMs(MEDIA_DURATION_US))
            .setAudioMimeType(AUDIO_MIME_TYPE)
            .setVideoMimeType(VIDEO_MIME_TYPE)
            .setChannelCount(AUDIO_CHANNEL_COUNT)
            .setSampleRate(AUDIO_SAMPLE_RATE)
            .setAudioEncoderName(AUDIO_ENCODER_NAME)
            .setVideoEncoderName(VIDEO_ENCODER_NAME)
            .setVideoFrameCount(2400)
            .setWidth(VIDEO_SIZE.getWidth())
            .setHeight(VIDEO_SIZE.getHeight())
            .setColorInfo(VIDEO_COLOR_INFO)
            .addProcessedInputs(processedInputs)
            .build();
    AtomicReference<EditingEndedEvent> editingEndedEventAtomicReference = new AtomicReference<>();
    EditingMetricsCollector editingMetricsCollector =
        new EditingMetricsCollector(
            new EditingMetricsCollector.MetricsReporter() {
              @Override
              public void reportMetrics(EditingEndedEvent editingEndedEvent) {
                editingEndedEventAtomicReference.set(editingEndedEvent);
              }

              @Override
              public void close() {}
            },
            EXPORTER_NAME,
            MUXER_NAME);

    editingMetricsCollector.onExportSuccess(exportResult);

    EditingEndedEvent editingEndedEvent = editingEndedEventAtomicReference.get();
    assertThat(editingEndedEvent.getFinalState())
        .isEqualTo(EditingEndedEvent.FINAL_STATE_SUCCEEDED);
    assertThat(editingEndedEvent.getTimeSinceCreatedMillis()).isAtLeast(0);
    assertThat(editingEndedEvent.getExporterName()).isEqualTo(EXPORTER_NAME);
    assertThat(editingEndedEvent.getMuxerName()).isEqualTo(MUXER_NAME);
    assertThat(editingEndedEvent.getFinalProgressPercent()).isEqualTo(100);
    assertThat(editingEndedEvent.getInputMediaItemInfos()).hasSize(1);
    // Assert input media items information
    MediaItemInfo inputMediaItemInfo = editingEndedEvent.getInputMediaItemInfos().get(0);
    assertThat(inputMediaItemInfo.getClipDurationMillis())
        .isEqualTo(usToMs(processedInputs.get(0).durationUs));
    assertThat(inputMediaItemInfo.getCodecNames().get(0)).isEqualTo(VIDEO_DECODER_NAME);
    assertThat(inputMediaItemInfo.getCodecNames().get(1)).isEqualTo(AUDIO_DECODER_NAME);
    assertThat(inputMediaItemInfo.getContainerMimeType()).isEqualTo(VIDEO_MIME_TYPE);
    assertThat(inputMediaItemInfo.getSampleMimeTypes()).hasSize(2);
    assertThat(inputMediaItemInfo.getSampleMimeTypes().get(0))
        .isAnyOf(AUDIO_MIME_TYPE, VIDEO_MIME_TYPE);
    assertThat(inputMediaItemInfo.getSampleMimeTypes().get(1))
        .isAnyOf(AUDIO_MIME_TYPE, VIDEO_MIME_TYPE);
    assertThat(inputMediaItemInfo.getDataTypes())
        .isEqualTo(MediaItemInfo.DATA_TYPE_VIDEO | MediaItemInfo.DATA_TYPE_AUDIO);
    assertThat(inputMediaItemInfo.getVideoFrameRate()).isEqualTo(VIDEO_FRAME_RATE);
    assertThat(inputMediaItemInfo.getVideoSize()).isEqualTo(VIDEO_SIZE);
    assertThat(inputMediaItemInfo.getVideoDataSpace())
        .isEqualTo(
            DataSpace.pack(
                DataSpace.STANDARD_BT2020, DataSpace.TRANSFER_ST2084, DataSpace.RANGE_LIMITED));
    assertThat(inputMediaItemInfo.getAudioChannelCount()).isEqualTo(AUDIO_CHANNEL_COUNT);
    assertThat(inputMediaItemInfo.getAudioSampleRateHz()).isEqualTo(AUDIO_SAMPLE_RATE);
    // Assert output media item information
    MediaItemInfo outputMediaItemInfo = editingEndedEvent.getOutputMediaItemInfo();
    assertThat(outputMediaItemInfo).isNotNull();
    assertThat(outputMediaItemInfo.getDurationMillis()).isEqualTo(exportResult.durationMs);
    assertThat(outputMediaItemInfo.getSampleMimeTypes()).hasSize(2);
    assertThat(outputMediaItemInfo.getSampleMimeTypes().get(0))
        .isAnyOf(exportResult.audioMimeType, exportResult.videoMimeType);
    assertThat(outputMediaItemInfo.getSampleMimeTypes().get(1))
        .isAnyOf(exportResult.audioMimeType, exportResult.videoMimeType);
    assertThat(outputMediaItemInfo.getAudioChannelCount()).isEqualTo(exportResult.channelCount);
    assertThat(outputMediaItemInfo.getAudioSampleRateHz()).isEqualTo(exportResult.sampleRate);
    assertThat(outputMediaItemInfo.getCodecNames()).hasSize(2);
    assertThat(outputMediaItemInfo.getCodecNames().get(0))
        .isAnyOf(exportResult.audioEncoderName, exportResult.videoEncoderName);
    assertThat(outputMediaItemInfo.getCodecNames().get(1))
        .isAnyOf(exportResult.audioEncoderName, exportResult.videoEncoderName);
    assertThat(outputMediaItemInfo.getVideoSampleCount()).isEqualTo(exportResult.videoFrameCount);
    assertThat(outputMediaItemInfo.getVideoSize())
        .isEqualTo(new Size(exportResult.width, exportResult.height));
    assertThat(inputMediaItemInfo.getVideoDataSpace())
        .isEqualTo(
            DataSpace.pack(
                DataSpace.STANDARD_BT2020, DataSpace.TRANSFER_ST2084, DataSpace.RANGE_LIMITED));
  }

  @Test
  public void onExportError_populatesEditingEndedEvent() {
    List<ExportResult.ProcessedInput> processedInputs = new ArrayList<>();
    processedInputs.add(
        new ExportResult.ProcessedInput(
            MediaItem.EMPTY,
            MEDIA_DURATION_US,
            FAKE_AUDIO_FORMAT,
            FAKE_VIDEO_FORMAT,
            AUDIO_DECODER_NAME,
            VIDEO_DECODER_NAME));
    ExportResult exportResult =
        new ExportResult.Builder()
            .setDurationMs(usToMs(MEDIA_DURATION_US))
            .setAudioMimeType(AUDIO_MIME_TYPE)
            .setVideoMimeType(VIDEO_MIME_TYPE)
            .setChannelCount(AUDIO_CHANNEL_COUNT)
            .setSampleRate(AUDIO_SAMPLE_RATE)
            .setAudioEncoderName(AUDIO_ENCODER_NAME)
            .setVideoEncoderName(VIDEO_ENCODER_NAME)
            .setVideoFrameCount(2400)
            .setWidth(VIDEO_SIZE.getWidth())
            .setHeight(VIDEO_SIZE.getHeight())
            .setColorInfo(VIDEO_COLOR_INFO)
            .addProcessedInputs(processedInputs)
            .build();
    ExportException exception =
        ExportException.createForMuxer(
            new RuntimeException(), ExportException.ERROR_CODE_MUXING_FAILED);
    AtomicReference<EditingEndedEvent> editingEndedEventAtomicReference = new AtomicReference<>();
    EditingMetricsCollector editingMetricsCollector =
        new EditingMetricsCollector(
            new EditingMetricsCollector.MetricsReporter() {
              @Override
              public void reportMetrics(EditingEndedEvent editingEndedEvent) {
                editingEndedEventAtomicReference.set(editingEndedEvent);
              }

              @Override
              public void close() {}
            },
            EXPORTER_NAME,
            MUXER_NAME);
    int progressPercentage = 10;

    editingMetricsCollector.onExportError(progressPercentage, exception, exportResult);

    EditingEndedEvent editingEndedEvent = editingEndedEventAtomicReference.get();
    assertThat(editingEndedEvent.getFinalState()).isEqualTo(EditingEndedEvent.FINAL_STATE_ERROR);
    assertThat(editingEndedEvent.getTimeSinceCreatedMillis()).isAtLeast(0);
    assertThat(editingEndedEvent.getExporterName()).isEqualTo(EXPORTER_NAME);
    assertThat(editingEndedEvent.getMuxerName()).isEqualTo(MUXER_NAME);
    assertThat(editingEndedEvent.getFinalProgressPercent()).isEqualTo(progressPercentage);
    assertThat(editingEndedEvent.getErrorCode())
        .isEqualTo(EditingEndedEvent.ERROR_CODE_MUXING_FAILED);
    // Assert input media items information
    assertThat(editingEndedEvent.getInputMediaItemInfos()).hasSize(1);
    MediaItemInfo inputMediaItemInfo = editingEndedEvent.getInputMediaItemInfos().get(0);
    assertThat(inputMediaItemInfo.getClipDurationMillis())
        .isEqualTo(usToMs(processedInputs.get(0).durationUs));
    assertThat(inputMediaItemInfo.getCodecNames().get(0)).isEqualTo(VIDEO_DECODER_NAME);
    assertThat(inputMediaItemInfo.getCodecNames().get(1)).isEqualTo(AUDIO_DECODER_NAME);
    assertThat(inputMediaItemInfo.getContainerMimeType()).isEqualTo(VIDEO_MIME_TYPE);
    assertThat(inputMediaItemInfo.getSampleMimeTypes()).hasSize(2);
    assertThat(inputMediaItemInfo.getSampleMimeTypes().get(0))
        .isAnyOf(AUDIO_MIME_TYPE, VIDEO_MIME_TYPE);
    assertThat(inputMediaItemInfo.getSampleMimeTypes().get(1))
        .isAnyOf(AUDIO_MIME_TYPE, VIDEO_MIME_TYPE);
    assertThat(inputMediaItemInfo.getDataTypes())
        .isEqualTo(MediaItemInfo.DATA_TYPE_VIDEO | MediaItemInfo.DATA_TYPE_AUDIO);
    assertThat(inputMediaItemInfo.getVideoFrameRate()).isEqualTo(VIDEO_FRAME_RATE);
    assertThat(inputMediaItemInfo.getVideoSize()).isEqualTo(VIDEO_SIZE);
    assertThat(inputMediaItemInfo.getVideoDataSpace())
        .isEqualTo(
            DataSpace.pack(
                DataSpace.STANDARD_BT2020, DataSpace.TRANSFER_ST2084, DataSpace.RANGE_LIMITED));
    assertThat(inputMediaItemInfo.getAudioChannelCount()).isEqualTo(AUDIO_CHANNEL_COUNT);
    assertThat(inputMediaItemInfo.getAudioSampleRateHz()).isEqualTo(AUDIO_SAMPLE_RATE);
    // Assert output media item information
    MediaItemInfo outputMediaItemInfo = editingEndedEvent.getOutputMediaItemInfo();
    assertThat(outputMediaItemInfo).isNotNull();
    assertThat(outputMediaItemInfo.getDurationMillis()).isEqualTo(exportResult.durationMs);
    assertThat(outputMediaItemInfo.getSampleMimeTypes()).hasSize(2);
    assertThat(outputMediaItemInfo.getSampleMimeTypes().get(0))
        .isAnyOf(exportResult.audioMimeType, exportResult.videoMimeType);
    assertThat(outputMediaItemInfo.getSampleMimeTypes().get(1))
        .isAnyOf(exportResult.audioMimeType, exportResult.videoMimeType);
    assertThat(outputMediaItemInfo.getAudioChannelCount()).isEqualTo(exportResult.channelCount);
    assertThat(outputMediaItemInfo.getAudioSampleRateHz()).isEqualTo(exportResult.sampleRate);
    assertThat(outputMediaItemInfo.getCodecNames()).hasSize(2);
    assertThat(outputMediaItemInfo.getCodecNames().get(0))
        .isAnyOf(exportResult.audioEncoderName, exportResult.videoEncoderName);
    assertThat(outputMediaItemInfo.getCodecNames().get(1))
        .isAnyOf(exportResult.audioEncoderName, exportResult.videoEncoderName);
    assertThat(outputMediaItemInfo.getVideoSampleCount()).isEqualTo(exportResult.videoFrameCount);
    assertThat(outputMediaItemInfo.getVideoSize())
        .isEqualTo(new Size(exportResult.width, exportResult.height));
    assertThat(inputMediaItemInfo.getVideoDataSpace())
        .isEqualTo(
            DataSpace.pack(
                DataSpace.STANDARD_BT2020, DataSpace.TRANSFER_ST2084, DataSpace.RANGE_LIMITED));
  }

  @Test
  public void onExportCancelled_populatesEditingEndedEvent() {
    AtomicReference<EditingEndedEvent> editingEndedEventAtomicReference = new AtomicReference<>();
    EditingMetricsCollector editingMetricsCollector =
        new EditingMetricsCollector(
            new EditingMetricsCollector.MetricsReporter() {
              @Override
              public void reportMetrics(EditingEndedEvent editingEndedEvent) {
                editingEndedEventAtomicReference.set(editingEndedEvent);
              }

              @Override
              public void close() {}
            },
            EXPORTER_NAME,
            MUXER_NAME);
    int progressPercentage = 70;

    editingMetricsCollector.onExportCancelled(progressPercentage);

    EditingEndedEvent editingEndedEvent = editingEndedEventAtomicReference.get();
    assertThat(editingEndedEvent.getFinalState()).isEqualTo(EditingEndedEvent.FINAL_STATE_CANCELED);
    assertThat(editingEndedEvent.getTimeSinceCreatedMillis()).isAtLeast(0);
    assertThat(editingEndedEvent.getExporterName()).isEqualTo(EXPORTER_NAME);
    assertThat(editingEndedEvent.getMuxerName()).isEqualTo(MUXER_NAME);
    assertThat(editingEndedEvent.getFinalProgressPercent()).isEqualTo(progressPercentage);
  }
}
