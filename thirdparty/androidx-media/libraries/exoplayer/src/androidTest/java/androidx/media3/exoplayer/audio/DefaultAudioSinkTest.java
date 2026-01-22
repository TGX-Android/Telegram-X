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
package androidx.media3.exoplayer.audio;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.collect.Iterables.getLast;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumentation unit tests for {@link DefaultAudioSink}. */
@RunWith(AndroidJUnit4.class)
public class DefaultAudioSinkTest {

  @Test
  @SdkSuppress(minSdkVersion = 22) // TODO: b/399130330 - Debug why this fails on API 21.
  public void audioTrackExceedsSharedMemory_retriesUntilOngoingReleasesAreDone() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              // Create audio sinks in parallel until we exceed the device's shared audio memory.
              ArrayList<DefaultAudioSink> audioSinks = new ArrayList<>();
              while (true) {
                DefaultAudioSink audioSink = new DefaultAudioSink.Builder(context).build();
                audioSinks.add(audioSink);
                try {
                  configureAudioSinkAndFeedData(audioSink);
                } catch (Exception e) {
                  // Expected to happen once we reached the shared audio memory limit of the device.
                  break;
                }
              }

              // Trigger release of one sink and immediately try the failed sink again. This should
              // now succeed even if the sink is released asynchronously.
              audioSinks.get(0).flush();
              audioSinks.get(0).release();
              try {
                configureAudioSinkAndFeedData(getLast(audioSinks));
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }

              // Clean-up
              for (int i = 1; i < audioSinks.size(); i++) {
                audioSinks.get(i).flush();
                audioSinks.get(i).release();
              }
            });
  }

  private void configureAudioSinkAndFeedData(DefaultAudioSink audioSink) throws Exception {
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setChannelCount(2)
            .setSampleRate(44_100)
            .build();
    audioSink.configure(format, /* specifiedBufferSize= */ 2_000_000, /* outputChannels= */ null);
    audioSink.play();
    ByteBuffer buffer = ByteBuffer.allocateDirect(8000).order(ByteOrder.nativeOrder());
    boolean handledBuffer = false;
    while (!handledBuffer) {
      handledBuffer =
          audioSink.handleBuffer(
              buffer, /* presentationTimeUs= */ 0, /* encodedAccessUnitCount= */ 1);
    }
  }
}
