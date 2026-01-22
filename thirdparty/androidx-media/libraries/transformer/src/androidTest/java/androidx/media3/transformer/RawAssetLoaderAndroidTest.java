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

import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.createOpenGlObjects;
import static androidx.media3.transformer.AndroidTestUtil.generateTextureFromBitmap;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.EGLContext;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.effect.DefaultGlObjectsProvider;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.Presentation;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** End to end instrumentation test for {@link RawAssetLoader} using {@link Transformer}. */
@RunWith(AndroidJUnit4.class)
public class RawAssetLoaderAndroidTest {
  @Rule public final TestName testName = new TestName();

  private static final Format AUDIO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_RAW)
          .setSampleRate(44_100)
          .setChannelCount(1)
          .setPcmEncoding(C.ENCODING_PCM_16BIT)
          .build();

  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void audioTranscoding_withRawAudio_completesWithCorrectDuration() throws Exception {
    SettableFuture<RawAssetLoader> rawAssetLoaderFuture = SettableFuture.create();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new TestRawAssetLoaderFactory(
                    AUDIO_FORMAT, /* videoFormat= */ null, rawAssetLoaderFuture))
            .build();
    long mediaDurationUs = C.MICROS_PER_SECOND;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.EMPTY))
            .setDurationUs(mediaDurationUs)
            .build();
    ListenableFuture<ExportResult> exportCompletionFuture =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .runAsync(testId, editedMediaItem);

    RawAssetLoader rawAssetLoader = rawAssetLoaderFuture.get();
    feedRawAudioDataToAssetLoader(rawAssetLoader, AUDIO_FORMAT, mediaDurationUs);

    ExportResult exportResult = exportCompletionFuture.get();
    // The durationMs is the timestamp of the last sample and not the total duration.
    // See b/324245196.
    // Audio encoders on different API versions seems to output slightly different durations, so add
    // 50ms tolerance.
    assertThat(exportResult.durationMs).isWithin(25).of(1000);
  }

  @Test
  public void audioTranscoding_withRawAudioAndUnsetDuration_completesWithCorrectDuration()
      throws Exception {
    SettableFuture<RawAssetLoader> rawAssetLoaderFuture = SettableFuture.create();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new TestRawAssetLoaderFactory(
                    AUDIO_FORMAT, /* videoFormat= */ null, rawAssetLoaderFuture))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.EMPTY)).build();
    ListenableFuture<ExportResult> exportCompletionFuture =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .runAsync(testId, editedMediaItem);

    RawAssetLoader rawAssetLoader = rawAssetLoaderFuture.get();
    feedRawAudioDataToAssetLoader(
        rawAssetLoader, AUDIO_FORMAT, /* durationUs= */ C.MICROS_PER_SECOND);

    ExportResult exportResult = exportCompletionFuture.get();
    // The durationMs is the timestamp of the last sample and not the total duration.
    // See b/324245196.
    // Audio encoders on different API versions seems to output slightly different durations, so add
    // 50ms tolerance.
    assertThat(exportResult.durationMs).isWithin(25).of(1000);
  }

  @Test
  public void videoTranscoding_withTextureInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    Bitmap bitmap = new DataSourceBitmapLoader(context).loadBitmap(Uri.parse(PNG_ASSET.uri)).get();
    DefaultVideoFrameProcessor.Factory videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setGlObjectsProvider(new DefaultGlObjectsProvider(createOpenGlObjects()))
            .build();
    Format videoFormat =
        new Format.Builder().setWidth(bitmap.getWidth()).setHeight(bitmap.getHeight()).build();
    SettableFuture<RawAssetLoader> rawAssetLoaderFuture = SettableFuture.create();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new TestRawAssetLoaderFactory(
                    /* audioFormat= */ null, videoFormat, rawAssetLoaderFuture))
            .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
            .build();
    long mediaDurationUs = C.MICROS_PER_SECOND;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.EMPTY))
            .setDurationUs(mediaDurationUs)
            .build();
    ListenableFuture<ExportResult> exportCompletionFuture =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .runAsync(testId, editedMediaItem);

    RawAssetLoader rawAssetLoader = rawAssetLoaderFuture.get();
    int firstTextureId = generateTextureFromBitmap(bitmap);
    int secondTextureId = generateTextureFromBitmap(bitmap);
    long lastSampleTimestampUs = mediaDurationUs / 2;
    while (!rawAssetLoader.queueInputTexture(firstTextureId, /* presentationTimeUs= */ 0)) {}
    while (!rawAssetLoader.queueInputTexture(secondTextureId, lastSampleTimestampUs)) {}
    rawAssetLoader.signalEndOfVideoInput();

    ExportResult exportResult = exportCompletionFuture.get();
    assertThat(exportResult.videoFrameCount).isEqualTo(2);
    // The durationMs is the timestamp of the last sample and not the total duration.
    // See b/324245196.
    assertThat(exportResult.durationMs).isEqualTo(lastSampleTimestampUs / 1_000);
  }

  @Test
  public void videoEditing_withTextureInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    Bitmap bitmap = new DataSourceBitmapLoader(context).loadBitmap(Uri.parse(PNG_ASSET.uri)).get();
    EGLContext currentContext = createOpenGlObjects();
    DefaultVideoFrameProcessor.Factory videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setGlObjectsProvider(new DefaultGlObjectsProvider(currentContext))
            .build();
    Format videoFormat =
        new Format.Builder().setWidth(bitmap.getWidth()).setHeight(bitmap.getHeight()).build();
    SettableFuture<RawAssetLoader> rawAssetLoaderFuture = SettableFuture.create();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new TestRawAssetLoaderFactory(
                    /* audioFormat= */ null, videoFormat, rawAssetLoaderFuture))
            .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
            .build();
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    long mediaDurationUs = C.MICROS_PER_SECOND;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.EMPTY))
            .setDurationUs(mediaDurationUs)
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .build();
    ListenableFuture<ExportResult> exportCompletionFuture =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .runAsync(testId, editedMediaItem);

    RawAssetLoader rawAssetLoader = rawAssetLoaderFuture.get();
    int firstTextureId = generateTextureFromBitmap(bitmap);
    int secondTextureId = generateTextureFromBitmap(bitmap);
    long lastSampleTimestampUs = mediaDurationUs / 2;
    while (!rawAssetLoader.queueInputTexture(firstTextureId, /* presentationTimeUs= */ 0)) {}
    while (!rawAssetLoader.queueInputTexture(secondTextureId, lastSampleTimestampUs)) {}
    rawAssetLoader.signalEndOfVideoInput();

    ExportResult exportResult = exportCompletionFuture.get();
    assertThat(exportResult.videoFrameCount).isEqualTo(2);
    // The durationMs is the timestamp of the last sample and not the total duration.
    // See b/324245196.
    assertThat(exportResult.durationMs).isEqualTo(lastSampleTimestampUs / 1_000);
  }

  @Test
  public void audioAndVideoTranscoding_withRawData_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    Bitmap bitmap = new DataSourceBitmapLoader(context).loadBitmap(Uri.parse(PNG_ASSET.uri)).get();
    DefaultVideoFrameProcessor.Factory videoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setGlObjectsProvider(new DefaultGlObjectsProvider(createOpenGlObjects()))
            .build();
    Format videoFormat =
        new Format.Builder().setWidth(bitmap.getWidth()).setHeight(bitmap.getHeight()).build();
    SettableFuture<RawAssetLoader> rawAssetLoaderFuture = SettableFuture.create();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new TestRawAssetLoaderFactory(AUDIO_FORMAT, videoFormat, rawAssetLoaderFuture))
            .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
            .build();
    long mediaDurationUs = C.MICROS_PER_SECOND;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.EMPTY))
            .setDurationUs(mediaDurationUs)
            .build();
    ListenableFuture<ExportResult> exportCompletionFuture =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .runAsync(testId, editedMediaItem);

    RawAssetLoader rawAssetLoader = rawAssetLoaderFuture.get();
    int firstTextureId = generateTextureFromBitmap(bitmap);
    int secondTextureId = generateTextureFromBitmap(bitmap);
    // Feed audio and video data in parallel so that export is not blocked waiting for all the
    // tracks.
    new Thread(
            () -> // Queue raw audio data.
            feedRawAudioDataToAssetLoader(rawAssetLoader, AUDIO_FORMAT, mediaDurationUs))
        .start();
    // Queue raw video data.
    while (!rawAssetLoader.queueInputTexture(firstTextureId, /* presentationTimeUs= */ 0)) {}
    while (!rawAssetLoader.queueInputTexture(
        secondTextureId, /* presentationTimeUs= */ mediaDurationUs / 2)) {}
    rawAssetLoader.signalEndOfVideoInput();

    ExportResult exportResult = exportCompletionFuture.get();
    assertThat(exportResult.videoFrameCount).isEqualTo(2);
    // The durationMs is the timestamp of the last audio sample and not the total duration.
    // See b/324245196.
    // Audio encoders on different API versions seems to output slightly different durations, so add
    // 50ms tolerance.
    assertThat(exportResult.durationMs).isWithin(25).of(1000);
  }

  private void feedRawAudioDataToAssetLoader(
      RawAssetLoader rawAssetLoader, Format rawAudioFormat, long durationUs) {
    AudioFormat audioFormat = new AudioFormat(rawAudioFormat);
    SilentAudioGenerator silentAudioGenerator = new SilentAudioGenerator(audioFormat);
    silentAudioGenerator.addSilence(durationUs);
    int bytesWritten = 0;
    while (silentAudioGenerator.hasRemaining()) {
      ByteBuffer byteBuffer = silentAudioGenerator.getBuffer();
      int byteBufferSize = byteBuffer.remaining();
      while (!rawAssetLoader.queueAudioData(
          byteBuffer,
          /* presentationTimeUs= */ Util.sampleCountToDurationUs(
              bytesWritten / audioFormat.bytesPerFrame, audioFormat.sampleRate),
          /* isLast= */ false)) {}
      bytesWritten += byteBufferSize;
    }
    while (!rawAssetLoader.queueAudioData(
        ByteBuffer.allocate(0),
        /* presentationTimeUs= */ Util.sampleCountToDurationUs(
            bytesWritten / audioFormat.bytesPerFrame, audioFormat.sampleRate),
        /* isLast= */ true)) {}
  }

  private static final class TestRawAssetLoaderFactory implements AssetLoader.Factory {
    private final Format audioFormat;
    private final Format videoFormat;
    private final SettableFuture<RawAssetLoader> assetLoaderSettableFuture;

    public TestRawAssetLoaderFactory(
        @Nullable Format audioFormat,
        @Nullable Format videoFormat,
        SettableFuture<RawAssetLoader> assetLoaderSettableFuture) {
      this.audioFormat = audioFormat;
      this.videoFormat = videoFormat;
      this.assetLoaderSettableFuture = assetLoaderSettableFuture;
    }

    @Override
    public RawAssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem,
        Looper looper,
        AssetLoader.Listener listener,
        AssetLoader.CompositionSettings compositionSettings) {
      OnInputFrameProcessedListener frameProcessedListener =
          (texId, syncObject) -> {
            try {
              GlUtil.deleteTexture(texId);
              GlUtil.deleteSyncObject(syncObject);
            } catch (GlUtil.GlException e) {
              throw new VideoFrameProcessingException(e);
            }
          };
      RawAssetLoader rawAssetLoader =
          new RawAssetLoader(
              editedMediaItem, listener, audioFormat, videoFormat, frameProcessedListener);
      assetLoaderSettableFuture.set(rawAssetLoader);
      return rawAssetLoader;
    }
  }
}
