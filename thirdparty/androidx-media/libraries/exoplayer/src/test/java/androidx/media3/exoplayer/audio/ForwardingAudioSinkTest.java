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

import static androidx.media3.test.utils.TestUtil.assertForwardingClassForwardsAllMethods;
import static androidx.media3.test.utils.TestUtil.assertSubclassOverridesAllMethods;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ForwardingAudioSink}. */
@RunWith(AndroidJUnit4.class)
public final class ForwardingAudioSinkTest {
  @Test
  public void overridesAllMethods() throws NoSuchMethodException {
    assertSubclassOverridesAllMethods(AudioSink.class, ForwardingAudioSink.class);
  }

  @Test
  public void forwardsAllMethods() throws Exception {
    assertForwardingClassForwardsAllMethods(AudioSink.class, ForwardingAudioSink::new);
  }
}
