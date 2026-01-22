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

import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.min;
import static java.lang.Math.round;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link RawAssetLoader}. */
@RunWith(AndroidJUnit4.class)
public class RawAssetLoaderTest {
  private static final Format FAKE_AUDIO_FORMAT =
      new Format.Builder()
          .setSampleRate(48000)
          .setChannelCount(2)
          .setPcmEncoding(C.ENCODING_PCM_16BIT)
          .build();

  private static final Format FAKE_VIDEO_FORMAT =
      new Format.Builder().setWidth(10).setHeight(10).build();

  private static final byte[] FAKE_AUDIO_DATA = TestUtil.createByteArray(1, 2, 3, 4);

  @Test
  public void rawAssetLoader_withOnlyAudioData_successfullyQueuesAudioData() {
    long audioDurationUs = 1_000;
    FakeAudioSampleConsumer fakeAudioSampleConsumer = new FakeAudioSampleConsumer();
    AssetLoader.Listener fakeAssetLoaderListener =
        new FakeAssetLoaderListener(fakeAudioSampleConsumer, /* videoSampleConsumer= */ null);

    RawAssetLoader rawAssetLoader =
        new RawAssetLoader(
            getEditedMediaItem(audioDurationUs),
            fakeAssetLoaderListener,
            FAKE_AUDIO_FORMAT,
            /* videoFormat= */ null,
            /* frameProcessedListener= */ null);
    rawAssetLoader.start();
    boolean queuedAudioData =
        rawAssetLoader.queueAudioData(
            ByteBuffer.wrap(FAKE_AUDIO_DATA), /* presentationTimeUs= */ 100, /* isLast= */ false);

    assertThat(queuedAudioData).isTrue();
    assertThat(fakeAudioSampleConsumer.inputBufferQueued).isTrue();
  }

  @Test
  public void rawAssetLoader_withOnlyVideoData_successfullyQueuesInputTexture() {
    long videoDurationUs = 1_000;
    FakeVideoSampleConsumer fakeVideoSampleConsumer = new FakeVideoSampleConsumer();
    AssetLoader.Listener fakeAssetLoaderListener =
        new FakeAssetLoaderListener(/* audioSampleConsumer= */ null, fakeVideoSampleConsumer);

    RawAssetLoader rawAssetLoader =
        new RawAssetLoader(
            getEditedMediaItem(videoDurationUs),
            fakeAssetLoaderListener,
            /* audioFormat= */ null,
            FAKE_VIDEO_FORMAT,
            /* frameProcessedListener= */ (unused, unused2) -> {});
    rawAssetLoader.start();
    boolean queuedInputTexture =
        rawAssetLoader.queueInputTexture(/* texId= */ 0, /* presentationTimeUs= */ 0);
    rawAssetLoader.signalEndOfVideoInput();

    assertThat(queuedInputTexture).isTrue();
    assertThat(fakeVideoSampleConsumer.inputTextureQueued).isTrue();
  }

  @Test
  public void getProgress_withOnlyAudioData_returnsExpectedProgress() {
    long audioDurationUs = 1_000;
    long audioSamplePresentationTimeUs = 100;
    AssetLoader.Listener fakeAssetLoaderListener =
        new FakeAssetLoaderListener(new FakeAudioSampleConsumer(), /* videoSampleConsumer= */ null);
    ProgressHolder progressHolder = new ProgressHolder();

    RawAssetLoader rawAssetLoader =
        new RawAssetLoader(
            getEditedMediaItem(audioDurationUs),
            fakeAssetLoaderListener,
            FAKE_AUDIO_FORMAT,
            /* videoFormat= */ null,
            /* frameProcessedListener= */ null);
    rawAssetLoader.start();
    boolean queuedAudioData =
        rawAssetLoader.queueAudioData(
            ByteBuffer.wrap(FAKE_AUDIO_DATA), audioSamplePresentationTimeUs, /* isLast= */ false);
    @Transformer.ProgressState int progressState = rawAssetLoader.getProgress(progressHolder);

    assertThat(queuedAudioData).isTrue();
    assertThat(progressState).isEqualTo(PROGRESS_STATE_AVAILABLE);
    assertThat(progressHolder.progress)
        .isEqualTo(round(audioSamplePresentationTimeUs * 100 / (float) audioDurationUs));
  }

  @Test
  public void getProgress_withOnlyAudioDataAndUnsetDuration_returnsUnavailableProgress() {
    long audioSamplePresentationTimeUs = 100;
    AssetLoader.Listener fakeAssetLoaderListener =
        new FakeAssetLoaderListener(new FakeAudioSampleConsumer(), /* videoSampleConsumer= */ null);
    ProgressHolder progressHolder = new ProgressHolder();

    RawAssetLoader rawAssetLoader =
        new RawAssetLoader(
            new EditedMediaItem.Builder(new MediaItem.Builder().build()).build(),
            fakeAssetLoaderListener,
            FAKE_AUDIO_FORMAT,
            /* videoFormat= */ null,
            /* frameProcessedListener= */ null);
    rawAssetLoader.start();
    boolean queuedAudioData =
        rawAssetLoader.queueAudioData(
            ByteBuffer.wrap(FAKE_AUDIO_DATA), audioSamplePresentationTimeUs, /* isLast= */ false);
    @Transformer.ProgressState int progressState = rawAssetLoader.getProgress(progressHolder);

    assertThat(queuedAudioData).isTrue();
    assertThat(progressState).isEqualTo(PROGRESS_STATE_UNAVAILABLE);
  }

  @Test
  public void getProgress_withOnlyVideoData_returnsExpectedProgress() throws ExportException {
    long videoDurationUs = 1_000;
    long videoSamplePresentationTimeUs = 100;
    AssetLoader.Listener fakeAssetLoaderListener =
        new FakeAssetLoaderListener(/* audioSampleConsumer= */ null, new FakeVideoSampleConsumer());
    ProgressHolder progressHolder = new ProgressHolder();

    RawAssetLoader rawAssetLoader =
        new RawAssetLoader(
            getEditedMediaItem(videoDurationUs),
            fakeAssetLoaderListener,
            /* audioFormat= */ null,
            FAKE_VIDEO_FORMAT,
            /* frameProcessedListener= */ (unused, unused2) -> {});
    rawAssetLoader.start();
    boolean queuedInputTexture =
        rawAssetLoader.queueInputTexture(/* texId= */ 0, videoSamplePresentationTimeUs);
    @Transformer.ProgressState int progressState = rawAssetLoader.getProgress(progressHolder);

    assertThat(queuedInputTexture).isTrue();
    assertThat(progressState).isEqualTo(PROGRESS_STATE_AVAILABLE);
    assertThat(progressHolder.progress)
        .isEqualTo(round(videoSamplePresentationTimeUs * 100 / (float) videoDurationUs));
  }

  @Test
  public void getProgress_withBothAudioAndVideoData_returnsMinimumProgress() {
    long mediaDurationUs = 1_000;
    long audioSamplePresentationTimeUs = 100;
    long videoSamplePresentationTimeUs = 500;
    AssetLoader.Listener fakeAssetLoaderListener =
        new FakeAssetLoaderListener(new FakeAudioSampleConsumer(), new FakeVideoSampleConsumer());
    ProgressHolder progressHolder = new ProgressHolder();

    RawAssetLoader rawAssetLoader =
        new RawAssetLoader(
            getEditedMediaItem(mediaDurationUs),
            fakeAssetLoaderListener,
            FAKE_AUDIO_FORMAT,
            FAKE_VIDEO_FORMAT,
            /* frameProcessedListener= */ (unused, unused2) -> {});
    rawAssetLoader.start();
    boolean queuedAudioData =
        rawAssetLoader.queueAudioData(
            ByteBuffer.wrap(FAKE_AUDIO_DATA), audioSamplePresentationTimeUs, /* isLast= */ false);
    boolean queuedInputTexture =
        rawAssetLoader.queueInputTexture(/* texId= */ 0, videoSamplePresentationTimeUs);
    @Transformer.ProgressState int progressState = rawAssetLoader.getProgress(progressHolder);

    assertThat(queuedAudioData).isTrue();
    assertThat(queuedInputTexture).isTrue();
    assertThat(progressState).isEqualTo(PROGRESS_STATE_AVAILABLE);
    assertThat(progressHolder.progress)
        .isEqualTo(
            round(
                min(audioSamplePresentationTimeUs, videoSamplePresentationTimeUs)
                    * 100
                    / (float) mediaDurationUs));
  }

  private static EditedMediaItem getEditedMediaItem(long mediaDurationUs) {
    return new EditedMediaItem.Builder(new MediaItem.Builder().build())
        .setDurationUs(mediaDurationUs)
        .build();
  }

  private static class FakeAssetLoaderListener implements AssetLoader.Listener {
    @Nullable private final SampleConsumer audioSampleConsumer;
    @Nullable private final SampleConsumer videoSampleConsumer;

    public FakeAssetLoaderListener(
        @Nullable SampleConsumer audioSampleConsumer,
        @Nullable SampleConsumer videoSampleConsumer) {
      this.audioSampleConsumer = audioSampleConsumer;
      this.videoSampleConsumer = videoSampleConsumer;
    }

    @Override
    public void onDurationUs(long durationUs) {}

    @Override
    public void onTrackCount(int trackCount) {}

    @Override
    public boolean onTrackAdded(
        Format inputFormat, @AssetLoader.SupportedOutputTypes int supportedOutputTypes) {
      return true;
    }

    @Nullable
    @Override
    public SampleConsumer onOutputFormat(Format format) {
      return MimeTypes.isVideo(format.sampleMimeType) ? videoSampleConsumer : audioSampleConsumer;
    }

    @Override
    public void onError(ExportException exportException) {}
  }

  private static class FakeAudioSampleConsumer implements SampleConsumer {
    public boolean inputBufferQueued;

    @Override
    public DecoderInputBuffer getInputBuffer() {
      return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    }

    @Override
    public boolean queueInputBuffer() {
      inputBufferQueued = true;
      return true;
    }
  }

  private static class FakeVideoSampleConsumer implements SampleConsumer {
    public boolean inputTextureQueued;

    @Override
    public @InputResult int queueInputTexture(int texId, long presentationTimeUs) {
      inputTextureQueued = true;
      return INPUT_RESULT_SUCCESS;
    }

    @Override
    public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {}
  }
}
