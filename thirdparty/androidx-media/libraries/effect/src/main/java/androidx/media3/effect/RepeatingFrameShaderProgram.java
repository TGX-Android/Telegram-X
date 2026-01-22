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
package androidx.media3.effect;

/** Interface for a {@link GlShaderProgram} that can repeat an input frame. */
/* package */ interface RepeatingFrameShaderProgram extends GlShaderProgram {

  /**
   * Signals that the frame contents will change in the next call to {@link
   * GlShaderProgram#queueInputFrame}.
   *
   * <p>This class can assume that the input frame contents are unchanged until the next call to
   * this method.
   */
  void signalNewRepeatingFrameSequence();
}
