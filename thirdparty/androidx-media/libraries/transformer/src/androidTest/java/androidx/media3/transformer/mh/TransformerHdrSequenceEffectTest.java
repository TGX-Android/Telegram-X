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
 *
 */

package androidx.media3.transformer.mh;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_AV1_2_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_PORTRAIT_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static androidx.media3.transformer.SequenceEffectTestUtil.NO_EFFECT;
import static androidx.media3.transformer.SequenceEffectTestUtil.PSNR_THRESHOLD_HD;
import static androidx.media3.transformer.SequenceEffectTestUtil.SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS;
import static androidx.media3.transformer.SequenceEffectTestUtil.assertBitmapsMatchExpectedAndSave;
import static androidx.media3.transformer.SequenceEffectTestUtil.assertFramesMatchExpectedPsnrAndSave;
import static androidx.media3.transformer.SequenceEffectTestUtil.clippedVideo;
import static androidx.media3.transformer.SequenceEffectTestUtil.createComposition;
import static androidx.media3.transformer.SequenceEffectTestUtil.tryToExportCompositionWithDecoder;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceDoesNotSupportHdrEditing;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsHdrEditing;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsOpenGlToneMapping;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.effect.Crop;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Tests for using different {@linkplain Effect effects} for {@link MediaItem MediaItems} in one
 * {@link EditedMediaItemSequence}, with HDR assets.
 */
@RunWith(AndroidJUnit4.class)
public final class TransformerHdrSequenceEffectTest {

  private static final int EXPORT_HEIGHT = 240;
  @Rule public final TestName testName = new TestName();

  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUp() {
    testId = testName.getMethodName();
  }

  @Test
  public void export_withSdrThenHdr() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_PORTRAIT_ASSET.uri,
                ImmutableList.of(
                    new Crop(/* left= */ -1, /* right= */ 0, /* bottom= */ -1, /* top= */ 0)),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10.uri,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
    // Expected bitmaps were generated on the Pixel 7, because emulators don't support decoding HDR.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  /**
   * If the first asset in a sequence is HDR, then Transformer will output HDR. However, because SDR
   * to HDR tone-mapping is not implemented, VideoFrameProcessor cannot take a later SDR input asset
   * after already being configured for HDR output.
   */
  @Test
  public void export_withHdrThenSdr_whenHdrEditingSupported_throws() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10.videoFormat,
        /* outputFormat= */ null);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10.uri,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_PORTRAIT_ASSET.uri,
                ImmutableList.of(
                    new Crop(/* left= */ -1, /* right= */ 0, /* bottom= */ -1, /* top= */ 0)),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    @Nullable ExportException expectedException = null;
    try {
      new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
          .build()
          .run(testId, composition);
    } catch (ExportException e) {
      expectedException = e;
    }
    assertThat(expectedException).isNotNull();
    assertThat(checkNotNull(checkNotNull(expectedException).getMessage()))
        .isEqualTo("Video frame processing error");
  }

  /**
   * If the first asset in a sequence is HDR, but HDR editing is not supported, then the first asset
   * will fallback to OpenGL tone-mapping, and configure VideoFrameProcessor for SDR output.
   */
  @Test
  public void export_withHdrThenSdr_whenHdrEditingUnsupported() throws Exception {
    assumeDeviceDoesNotSupportHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeDeviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10.videoFormat,
        /* outputFormat= */ null);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10.uri,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_PORTRAIT_ASSET.uri,
                ImmutableList.of(
                    new Crop(/* left= */ -1, /* right= */ 0, /* bottom= */ -1, /* top= */ 0)),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
    // Expected bitmaps were generated on the Pixel 3a, because emulators don't support decoding
    // HDR, and the Pixel 7 does support HDR editing.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withHdr10ThenHdr10_whenHdrEditingSupported_producesExpectedFrame()
      throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10.videoFormat,
        /* outputFormat= */ null);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10.uri,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10.uri,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);
    assertThat(result.filePath).isNotNull();
    // Expected bitmaps were generated on the Pixel 7 Pro, because emulators don't support decoding
    // HDR.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withHlg10ThenHdr10_whenHdrEditingSupported_producesExpectedFrame()
      throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat,
        /* outputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10.videoFormat,
        /* outputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_1080P_5_SECOND_HLG10.uri,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10.uri,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                /* endPositionMs= */ SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    // Expected bitmaps were generated on the Pixel 7 Pro, because emulators don't support decoding
    // HDR.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export1920x1080Hlg_withAllAvailableDecoders_doesNotStretchOutputOnAny()
      throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat,
        /* outputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
    List<MediaCodecInfo> mediaCodecInfoList =
        MediaCodecSelector.DEFAULT.getDecoderInfos(
            checkNotNull(MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat.sampleMimeType),
            /* requiresSecureDecoder= */ false,
            /* requiresTunnelingDecoder= */ false);
    Composition composition =
        createComposition(
            /* presentation= */ null,
            clippedVideo(
                MP4_ASSET_1080P_5_SECOND_HLG10.uri,
                NO_EFFECT,
                /* endPositionMs= */ C.MILLIS_PER_SECOND / 4));

    boolean atLeastOneDecoderSucceeds = false;
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      @Nullable
      ExportTestResult result =
          tryToExportCompositionWithDecoder(testId, context, mediaCodecInfo, composition);
      if (result == null) {
        continue;
      }
      atLeastOneDecoderSucceeds = true;

      assertThat(checkNotNull(result).filePath).isNotNull();
      assertFramesMatchExpectedPsnrAndSave(
          context, testId, checkNotNull(result.filePath), PSNR_THRESHOLD_HD, /* frameCount= */ 1);
    }
    assertThat(atLeastOneDecoderSucceeds).isTrue();
  }

  @Test
  public void export720x1280Av1Hdr10_withAllAvailableDecoders_doesNotStretchOutputOnAny()
      throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_AV1_2_SECOND_HDR10.videoFormat);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_AV1_2_SECOND_HDR10.videoFormat,
        /* outputFormat= */ MP4_ASSET_AV1_2_SECOND_HDR10.videoFormat);
    List<MediaCodecInfo> mediaCodecInfoList =
        MediaCodecSelector.DEFAULT.getDecoderInfos(
            checkNotNull(MP4_ASSET_AV1_2_SECOND_HDR10.videoFormat.sampleMimeType),
            /* requiresSecureDecoder= */ false,
            /* requiresTunnelingDecoder= */ false);
    Composition composition =
        createComposition(
            /* presentation= */ null,
            clippedVideo(MP4_ASSET_AV1_2_SECOND_HDR10.uri, NO_EFFECT, C.MILLIS_PER_SECOND / 4));

    boolean atLeastOneDecoderSucceeds = false;
    for (MediaCodecInfo mediaCodecInfo : mediaCodecInfoList) {
      @Nullable
      ExportTestResult result =
          tryToExportCompositionWithDecoder(testId, context, mediaCodecInfo, composition);
      if (result == null) {
        continue;
      }
      atLeastOneDecoderSucceeds = true;

      assertThat(checkNotNull(result).filePath).isNotNull();
      assertFramesMatchExpectedPsnrAndSave(
          context, testId, checkNotNull(result.filePath), PSNR_THRESHOLD_HD, /* frameCount= */ 1);
    }
    assertThat(atLeastOneDecoderSucceeds).isTrue();
  }
}
