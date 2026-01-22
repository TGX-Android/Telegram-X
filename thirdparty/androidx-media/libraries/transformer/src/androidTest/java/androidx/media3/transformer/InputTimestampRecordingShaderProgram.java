/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.effect.PassthroughShaderProgram;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/** A {@link PassthroughShaderProgram} that records the input timestamps. */
public class InputTimestampRecordingShaderProgram extends PassthroughShaderProgram {
  private final List<Long> inputTimestampsUs;

  /** Creates an instance. */
  public InputTimestampRecordingShaderProgram() {
    inputTimestampsUs = new ArrayList<>();
  }

  /** Returns the captured timestamps, in microseconds. */
  public ImmutableList<Long> getInputTimestampsUs() {
    return ImmutableList.copyOf(inputTimestampsUs);
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    super.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeUs);
    inputTimestampsUs.add(presentationTimeUs);
  }
}
