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
package androidx.media3.exoplayer.audio;

import static androidx.media3.test.utils.TestUtil.createByteBuffer;
import static androidx.media3.test.utils.TestUtil.createFloatArray;
import static androidx.media3.test.utils.TestUtil.createShortArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ChannelMappingAudioProcessor} */
@RunWith(AndroidJUnit4.class)
public class ChannelMappingAudioProcessorTest {

  private static final AudioFormat PCM_FLOAT_LCR_FORMAT =
      new AudioFormat(
          /* sampleRate= */ 44100, /* channelCount= */ 3, /* encoding= */ C.ENCODING_PCM_FLOAT);

  private static final AudioFormat PCM_16BIT_STEREO_FORMAT =
      new AudioFormat(
          /* sampleRate= */ 44100, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);

  @Test
  public void channelMap_withPcmFloatSamples_mapsOutputCorrectly()
      throws AudioProcessor.UnhandledAudioFormatException {
    ChannelMappingAudioProcessor processor = new ChannelMappingAudioProcessor();
    processor.setChannelMap(new int[] {2, 1, 0});
    processor.configure(PCM_FLOAT_LCR_FORMAT);
    processor.flush();

    processor.queueInput(createByteBuffer(new float[] {1f, 2f, 3f, 4f, 5f, 6f}));
    float[] output = createFloatArray(processor.getOutput());
    assertThat(output).isEqualTo(new float[] {3f, 2f, 1f, 6f, 5f, 4f});
  }

  @Test
  public void channelMap_withPcm16Samples_mapsOutputCorrectly()
      throws AudioProcessor.UnhandledAudioFormatException {
    ChannelMappingAudioProcessor processor = new ChannelMappingAudioProcessor();
    processor.setChannelMap(new int[] {1, 0});
    processor.configure(PCM_16BIT_STEREO_FORMAT);
    processor.flush();

    processor.queueInput(createByteBuffer(new short[] {1, 2, 3, 4, 5, 6}));
    short[] output = createShortArray(processor.getOutput());
    assertThat(output).isEqualTo(new short[] {2, 1, 4, 3, 6, 5});
  }

  @Test
  public void channelMap_withMoreOutputChannels_duplicatesSamples()
      throws AudioProcessor.UnhandledAudioFormatException {
    ChannelMappingAudioProcessor processor = new ChannelMappingAudioProcessor();
    processor.setChannelMap(new int[] {1, 0, 1});
    processor.configure(PCM_16BIT_STEREO_FORMAT);
    processor.flush();

    processor.queueInput(createByteBuffer(new short[] {1, 2, 3, 4}));
    short[] output = createShortArray(processor.getOutput());
    assertThat(output).isEqualTo(new short[] {2, 1, 2, 4, 3, 4});
  }

  @Test
  public void channelMap_withLessOutputChannels_ignoresSamples()
      throws AudioProcessor.UnhandledAudioFormatException {
    ChannelMappingAudioProcessor processor = new ChannelMappingAudioProcessor();
    processor.setChannelMap(new int[] {0, 1});
    processor.configure(PCM_FLOAT_LCR_FORMAT);
    processor.flush();

    processor.queueInput(createByteBuffer(new float[] {1f, 2f, 3f, 4f, 5f, 6f}));
    float[] output = createFloatArray(processor.getOutput());
    assertThat(output).isEqualTo(new float[] {1f, 2f, 4f, 5f});
  }

  @Test
  public void setChannelMap_withNonExistentInputChannels_throwsInConfigure()
      throws AudioProcessor.UnhandledAudioFormatException {
    ChannelMappingAudioProcessor processor = new ChannelMappingAudioProcessor();
    processor.setChannelMap(new int[] {1, 0, 2});
    Assert.assertThrows(
        AudioProcessor.UnhandledAudioFormatException.class,
        () -> processor.configure(PCM_16BIT_STEREO_FORMAT));
  }

  @Test
  public void configure_withoutChannelMapSet_returnNotSet()
      throws AudioProcessor.UnhandledAudioFormatException {
    ChannelMappingAudioProcessor processor = new ChannelMappingAudioProcessor();
    assertThat(processor.configure(PCM_16BIT_STEREO_FORMAT)).isEqualTo(AudioFormat.NOT_SET);
  }

  @Test
  public void configure_withDifferentInputAndOutputChannelCounts_returnsOutputChannelCount()
      throws AudioProcessor.UnhandledAudioFormatException {
    ChannelMappingAudioProcessor processor = new ChannelMappingAudioProcessor();
    processor.setChannelMap(new int[] {0});
    assertThat(processor.configure(PCM_FLOAT_LCR_FORMAT).channelCount).isEqualTo(1);
  }
}
