/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.transformer.AndroidTestUtil.DelayEffect;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Tests for the force end of stream handling in {@code ExternalTextureManager}.
 *
 * <p>This test only applies to API29+, as it introduces {@link MediaFormat#KEY_ALLOW_FRAME_DROP},
 * and hence we allow decoder to retain more than one frames in its output. See {@link
 * Util#getMaxPendingFramesCountForMediaCodecDecoders}.
 */
@RunWith(AndroidJUnit4.class)
public class ForceEndOfStreamTest {

  private final Context context = ApplicationProvider.getApplicationContext();
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void transcode_decoderDroppingLastFourFrames_exportSucceeds() throws Exception {
    // TODO: b/370050055 - Do we need API 29+, or the device list from
    //  Util.isFrameDropAllowedOnSurfaceInput?
    assumeTrue(Util.SDK_INT >= 29);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);
    int framesToSkip = 4;

    ExportTestResult testResult =
        new TransformerAndroidTestRunner.Builder(context, buildTransformer(context, framesToSkip))
            .build()
            .run(testId, createComposition(MediaItem.fromUri(MP4_ASSET.uri)));

    assertThat(testResult.exportResult.videoFrameCount)
        .isEqualTo(MP4_ASSET.videoFrameCount - framesToSkip);
    assertThat(new File(testResult.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void transcode_decoderDroppingNoFrame_exportSucceeds() throws Exception {
    // TODO: b/370050055 - Do we need API 29+, or the device list from
    //  Util.isFrameDropAllowedOnSurfaceInput?
    assumeTrue(Util.SDK_INT >= 29);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);

    ExportTestResult testResult =
        new TransformerAndroidTestRunner.Builder(
                context, buildTransformer(context, /* framesToSkip= */ 0))
            .build()
            .run(testId, createComposition(MediaItem.fromUri(MP4_ASSET.uri)));

    assertThat(testResult.exportResult.videoFrameCount).isEqualTo(MP4_ASSET.videoFrameCount);
    assertThat(new File(testResult.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void transcode_withSlowVideoEffect_exportSucceedsWithCorrectNumberOfFrames()
      throws Exception {
    // TODO: b/370050055 - Do we need API 29+, or the device list from
    //  Util.isFrameDropAllowedOnSurfaceInput?
    assumeTrue(Util.SDK_INT >= 29);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat,
        /* outputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.videoFormat);
    // Use MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S because it's widely supported.
    // Clip to 30 frames, because we need a DelayEffect(200ms) to be applied for each frame.
    // Processing too many frames would make this test unnecessarily slow.
    MediaItem mediaItemClippedTo30Frames =
        new MediaItem.Builder()
            .setUri(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.uri)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(495).build())
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder()
                    .addItem(
                        new EditedMediaItem.Builder(mediaItemClippedTo30Frames)
                            .setRemoveAudio(true)
                            .setEffects(
                                new Effects(
                                    /* audioProcessors= */ ImmutableList.of(),
                                    /* videoEffects= */ ImmutableList.of(
                                        new DelayEffect(/* delayMs= */ 200))))
                            .build())
                    .build())
            .build();
    Transformer transformer = new Transformer.Builder(context).build();

    ExportTestResult testResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(
            new Mp4Extractor(new DefaultSubtitleParserFactory()), testResult.filePath);
    fakeExtractorOutput.track(0, C.TRACK_TYPE_VIDEO).assertSampleCount(30);
  }

  private static Transformer buildTransformer(Context context, int framesToSkip) {
    return new Transformer.Builder(context)
        .setAssetLoaderFactory(
            new DefaultAssetLoaderFactory(
                context,
                new FrameDroppingDecoderFactory(context, MP4_ASSET.videoFrameCount, framesToSkip),
                Clock.DEFAULT))
        .build();
  }

  private static Composition createComposition(MediaItem mediaItem) {
    return new Composition.Builder(
            new EditedMediaItemSequence.Builder(
                    new EditedMediaItem.Builder(mediaItem)
                        .setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS)
                        .build())
                .build())
        .build();
  }

  private static final class FrameDroppingDecoderFactory implements Codec.DecoderFactory {
    private final DefaultDecoderFactory defaultDecoderFactory;
    private final int sourceFrameCount;
    private final int framesToDrop;

    private FrameDroppingDecoderFactory(Context context, int sourceFrameCount, int framesToDrop) {
      defaultDecoderFactory = new DefaultDecoderFactory.Builder(context).build();
      this.sourceFrameCount = sourceFrameCount;
      this.framesToDrop = framesToDrop;
    }

    @Override
    public Codec createForAudioDecoding(Format format) throws ExportException {
      return defaultDecoderFactory.createForAudioDecoding(format);
    }

    @Override
    public Codec createForVideoDecoding(
        Format format, Surface outputSurface, boolean requestSdrToneMapping)
        throws ExportException {
      return new FrameDroppingDecoder(
          defaultDecoderFactory.createForVideoDecoding(
              format, outputSurface, requestSdrToneMapping),
          sourceFrameCount,
          framesToDrop);
    }

    public static final class FrameDroppingDecoder implements Codec {

      private final DefaultCodec wrappedDecoder;
      private final int sourceFrameCount;
      private final int framesToDrop;

      private int framesReceived;

      public FrameDroppingDecoder(DefaultCodec decoder, int sourceFrameCount, int framesToDrop)
          throws ExportException {
        wrappedDecoder = decoder;
        this.sourceFrameCount = sourceFrameCount;
        this.framesToDrop = framesToDrop;
      }

      @Override
      public Format getConfigurationFormat() {
        return wrappedDecoder.getConfigurationFormat();
      }

      @Override
      public String getName() {
        return wrappedDecoder.getName();
      }

      @Override
      public Surface getInputSurface() {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getMaxPendingFrameCount() {
        return wrappedDecoder.getMaxPendingFrameCount();
      }

      @Override
      public boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer)
          throws ExportException {
        return wrappedDecoder.maybeDequeueInputBuffer(inputBuffer);
      }

      @Override
      public void queueInputBuffer(DecoderInputBuffer inputBuffer) throws ExportException {
        wrappedDecoder.queueInputBuffer(inputBuffer);
      }

      @Override
      public void signalEndOfInputStream() throws ExportException {
        wrappedDecoder.signalEndOfInputStream();
      }

      @Override
      public Format getInputFormat() throws ExportException {
        return wrappedDecoder.getInputFormat();
      }

      @Nullable
      @Override
      public Format getOutputFormat() throws ExportException {
        return wrappedDecoder.getOutputFormat();
      }

      @Override
      public ByteBuffer getOutputBuffer() throws ExportException {
        throw new UnsupportedOperationException();
      }

      @Nullable
      @Override
      public MediaCodec.BufferInfo getOutputBufferInfo() throws ExportException {
        return wrappedDecoder.getOutputBufferInfo();
      }

      @Override
      public void releaseOutputBuffer(boolean render) throws ExportException {
        wrappedDecoder.releaseOutputBuffer(render);
      }

      @Override
      public void releaseOutputBuffer(long renderPresentationTimeUs) throws ExportException {
        framesReceived++;
        wrappedDecoder.releaseOutputBuffer(
            /* render= */ sourceFrameCount - framesReceived >= framesToDrop,
            renderPresentationTimeUs);
      }

      @Override
      public boolean isEnded() {
        return wrappedDecoder.isEnded();
      }

      @Override
      public void release() {
        wrappedDecoder.release();
      }
    }
  }
}
