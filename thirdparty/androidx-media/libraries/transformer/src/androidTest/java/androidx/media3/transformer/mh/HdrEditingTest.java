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
package androidx.media3.transformer.mh;

import static androidx.media3.effect.DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL;
import static androidx.media3.test.utils.DecodeOneFrameUtil.decodeOneMediaItemFrame;
import static androidx.media3.test.utils.TestUtil.retrieveTrackFormat;
import static androidx.media3.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_AV1_2_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceDoesNotSupportHdrEditing;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsHdrEditing;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.max;

import android.content.Context;
import android.media.MediaFormat;
import android.net.Uri;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.exoplayer.video.PlaceholderSurface;
import androidx.media3.test.utils.DecodeOneFrameUtil;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EncoderUtil;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * Composition#HDR_MODE_KEEP_HDR HDR frame edit}.
 */
@RunWith(AndroidJUnit4.class)
public final class HdrEditingTest {

  @Rule public final TestName testName = new TestName();

  private String testId;
  @Nullable private Surface placeholderSurface;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @After
  public void tearDown() {
    if (placeholderSurface != null) {
      placeholderSurface.release();
    }
  }

  @Test
  public void export_transmuxHdr10File() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    if (Util.SDK_INT < 24) {
      // TODO: b/285543404 - Remove suppression once we can transmux H.265/HEVC before API 24.
      recordTestSkipped(context, testId, /* reason= */ "Can't transmux H.265/HEVC before API 24");
      return;
    }

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10.videoFormat,
        /* outputFormat= */ null);

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10.uri));

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);
    @C.ColorTransfer
    int actualColorTransfer =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
            .colorInfo
            .colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_ST2084);
  }

  @Test
  public void export_transmuxHlg10File() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();

    if (Util.SDK_INT < 24) {
      // TODO: b/285543404 - Remove suppression once we can transmux H.265/HEVC before API 24.
      recordTestSkipped(context, testId, /* reason= */ "Can't transmux H.265/HEVC before API 24");
      return;
    }

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat,
        /* outputFormat= */ null);

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10.uri));

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);
    @C.ColorTransfer
    int actualColorTransfer =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
            .colorInfo
            .colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportAndTranscode_hdr10File_whenHdrEditingIsSupported() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);

    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ format);

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    MediaFormat mediaFormat = getVideoMediaFormatFromDecoder(context, exportTestResult.filePath);
    ByteBuffer hdrStaticInfo = mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);

    assertThat(max(byteList(hdrStaticInfo))).isAtLeast((byte) 1);
    assertThat(mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER))
        .isEqualTo(MediaFormat.COLOR_TRANSFER_ST2084);
  }

  @Test
  public void exportAndTranscode_hlg10File_whenHdrEditingIsSupported() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);

    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ format);

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    @C.ColorTransfer
    int actualColorTransfer =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
            .colorInfo
            .colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportAndTranscode_hlg10VideoToDolbyVision_whenDolbyVisionSupported()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat,
        /* outputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10
            .videoFormat
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs(null)
            .build());
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10.uri));
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Transformer transformer =
        new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_DOLBY_VISION).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    ExportResult exportResult = exportTestResult.exportResult;

    assertThat(exportResult.videoMimeType).isEqualTo(MimeTypes.VIDEO_DOLBY_VISION);
    Format videoTrackFormat =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO);
    assertThat(videoTrackFormat.sampleMimeType).isEqualTo(MimeTypes.VIDEO_DOLBY_VISION);
    int actualColorTransfer = videoTrackFormat.colorInfo.colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportAndTranscode_dolbyVisionFileToDolbyVision_whenDolbyVisionSupported()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_DOLBY_VISION_HDR.videoFormat;
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ format);
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_DOLBY_VISION_HDR.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    ExportResult exportResult = exportTestResult.exportResult;

    assertThat(exportResult.videoMimeType).isEqualTo(MimeTypes.VIDEO_DOLBY_VISION);
    Format videoTrackFormat =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO);
    assertThat(videoTrackFormat.sampleMimeType).isEqualTo(MimeTypes.VIDEO_DOLBY_VISION);
    int actualColorTransfer = videoTrackFormat.colorInfo.colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void
      exportAndTranscode_dolbyVisionFileToHlg_whenDolbyVisionIsNotSupportedAndHlgIsSupported()
          throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_DOLBY_VISION_HDR.videoFormat;
    assumeDeviceDoesNotSupportHdrEditing(testId, format);
    assumeDeviceSupportsHdrEditing(
        testId, format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).build());
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_DOLBY_VISION_HDR.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    ExportResult exportResult = exportTestResult.exportResult;

    assertThat(exportResult.videoMimeType).isEqualTo(MimeTypes.VIDEO_H265);
    Format videoTrackFormat =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO);
    assertThat(videoTrackFormat.sampleMimeType).isEqualTo(MimeTypes.VIDEO_H265);
    int actualColorTransfer = videoTrackFormat.colorInfo.colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void
      exportAndTranscode_av1FileWithAv1HdrEditingUnsupportedAndHevcHdrEditingSupported_fallsBackToH265()
          throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_AV1_2_SECOND_HDR10.videoFormat;
    if (EncoderUtil.getSupportedEncodersForHdrEditing(MimeTypes.VIDEO_H265, format.colorInfo)
        .isEmpty()) {
      String skipReason = "No H265 HDR editing support for " + format.colorInfo;
      recordTestSkipped(getApplicationContext(), testId, skipReason);
      throw new AssumptionViolatedException(skipReason);
    }
    if (!EncoderUtil.getSupportedEncodersForHdrEditing(MimeTypes.VIDEO_AV1, format.colorInfo)
        .isEmpty()) {
      String skipReason = "AV1 HDR editing support for " + format.colorInfo;
      recordTestSkipped(getApplicationContext(), testId, skipReason);
      throw new AssumptionViolatedException(skipReason);
    }

    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ format,
        /* outputFormat= */ format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).build());

    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_AV1_2_SECOND_HDR10.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    MediaFormat mediaFormat = getVideoMediaFormatFromDecoder(context, exportTestResult.filePath);
    ByteBuffer hdrStaticInfo = mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);

    Format outputFormat =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO);
    assertThat(outputFormat.colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_ST2084);
    assertThat(outputFormat.sampleMimeType).isEqualTo(MimeTypes.VIDEO_H265);
    assertThat(max(byteList(hdrStaticInfo))).isAtLeast((byte) 1);
  }

  @Test
  public void exportAndTranscodeHdr_ignoringSdrWorkingColorSpace_whenHdrEditingIsSupported()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);

    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ format);

    Transformer transformer =
        new Transformer.Builder(context)
            .setVideoFrameProcessorFactory(
                new DefaultVideoFrameProcessor.Factory.Builder()
                    .setSdrWorkingColorSpace(WORKING_COLOR_SPACE_ORIGINAL)
                    .build())
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10.uri)))
            .setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS)
            .build();

    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    @C.ColorTransfer
    int actualColorTransfer =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
            .colorInfo
            .colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportAndTranscode_hdr10File_whenHdrEditingUnsupported_toneMapsOrThrows()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceDoesNotSupportHdrEditing(testId, format);

    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);

    AtomicBoolean isFallbackListenerInvoked = new AtomicBoolean();
    AtomicBoolean isToneMappingFallbackApplied = new AtomicBoolean();
    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      Composition composition,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    isFallbackListenerInvoked.set(true);
                    assertThat(originalTransformationRequest.hdrMode).isEqualTo(HDR_MODE_KEEP_HDR);
                    isToneMappingFallbackApplied.set(
                        fallbackTransformationRequest.hdrMode
                            == HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, editedMediaItem);
      assertThat(isToneMappingFallbackApplied.get()).isTrue();
      @C.ColorTransfer
      int actualColorTransfer =
          retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
              .colorInfo
              .colorTransfer;
      assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      if (exception.getCause() != null) {
        @Nullable String message = exception.getCause().getMessage();
        if (message != null
            && (Objects.equals(message, "Decoding HDR is not supported on this device.")
                || message.contains(
                    "OpenGL ES 3.0 context support is required for HDR input or output.")
                || Objects.equals(message, "Device lacks YUV extension support."))) {
          return;
        }
      }
      throw exception;
    }
  }

  @Test
  public void exportAndTranscode_hlg10File_whenHdrEditingUnsupported_toneMapsOrThrows()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceDoesNotSupportHdrEditing(testId, format);

    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);

    AtomicBoolean isToneMappingFallbackApplied = new AtomicBoolean();
    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      Composition composition,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    assertThat(originalTransformationRequest.hdrMode).isEqualTo(HDR_MODE_KEEP_HDR);
                    isToneMappingFallbackApplied.set(
                        fallbackTransformationRequest.hdrMode
                            == HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, editedMediaItem);
      assertThat(isToneMappingFallbackApplied.get()).isTrue();
      @C.ColorTransfer
      int actualColorTransfer =
          retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
              .colorInfo
              .colorTransfer;
      assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      if (exception.getCause() != null) {
        @Nullable String message = exception.getCause().getMessage();
        if (message != null
            && (Objects.equals(message, "Decoding HDR is not supported on this device.")
                || message.contains(
                    "OpenGL ES 3.0 context support is required for HDR input or output.")
                || Objects.equals(message, "Device lacks YUV extension support."))) {
          return;
        }
      }
      throw exception;
    }
  }

  @Test
  public void exportAndTranscode_dolbyVisionFile_whenHdrEditingUnsupported_toneMapsOrThrows()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Format format = MP4_ASSET_DOLBY_VISION_HDR.videoFormat;
    // Check HDR support for both VIDEO_DOLBY_VISION and VIDEO_H265 mime types.
    assumeDeviceDoesNotSupportHdrEditing(testId, format);
    assumeDeviceDoesNotSupportHdrEditing(
        testId, format.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H265).build());
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    AtomicBoolean isFallbackListenerInvoked = new AtomicBoolean();
    AtomicBoolean isToneMappingFallbackApplied = new AtomicBoolean();
    Transformer transformer =
        new Transformer.Builder(context)
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onFallbackApplied(
                      Composition composition,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    isFallbackListenerInvoked.set(true);
                    assertThat(originalTransformationRequest.hdrMode).isEqualTo(HDR_MODE_KEEP_HDR);
                    isToneMappingFallbackApplied.set(
                        fallbackTransformationRequest.hdrMode
                            == HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL);
                  }
                })
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_DOLBY_VISION_HDR.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    try {
      ExportTestResult exportTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, editedMediaItem);
      assertThat(isToneMappingFallbackApplied.get()).isTrue();
      @C.ColorTransfer
      int actualColorTransfer =
          retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
              .colorInfo
              .colorTransfer;
      assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
    } catch (ExportException exception) {
      if (exception.getCause() != null) {
        @Nullable String message = exception.getCause().getMessage();
        if (message != null
            && (Objects.equals(message, "Decoding HDR is not supported on this device.")
                || message.contains(
                    "OpenGL ES 3.0 context support is required for HDR input or output.")
                || Objects.equals(message, "Device lacks YUV extension support."))) {
          return;
        }
      }
      throw exception;
    }
  }

  private static List<Byte> byteList(ByteBuffer buffer) {
    ArrayList<Byte> outputBytes = new ArrayList<>();
    while (buffer.hasRemaining()) {
      outputBytes.add(buffer.get());
    }
    return outputBytes;
  }

  /**
   * Returns the {@link MediaFormat} corresponding to the video track in {@code filePath}.
   *
   * <p>HDR metadata is optional in both the container and bitstream. Return the {@link MediaFormat}
   * produced by the decoder which should include any metadata from either container or bitstream.
   */
  private MediaFormat getVideoMediaFormatFromDecoder(Context context, String filePath)
      throws Exception {
    AtomicReference<MediaFormat> decodedFrameFormat = new AtomicReference<>();
    if (placeholderSurface == null) {
      placeholderSurface = PlaceholderSurface.newInstance(context, false);
    }
    decodeOneMediaItemFrame(
        MediaItem.fromUri(filePath),
        new DecodeOneFrameUtil.Listener() {
          @Override
          public void onContainerExtracted(MediaFormat mediaFormat) {}

          @Override
          public void onFrameDecoded(MediaFormat mediaFormat) {
            decodedFrameFormat.set(mediaFormat);
          }
        },
        placeholderSurface);
    return decodedFrameFormat.get();
  }
}
