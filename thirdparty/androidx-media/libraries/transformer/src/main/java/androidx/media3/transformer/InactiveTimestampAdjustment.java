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

import android.content.Context;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.common.util.SpeedProviderUtil;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import androidx.media3.effect.PassthroughShaderProgram;

/* package */ class InactiveTimestampAdjustment implements GlEffect {
  public final SpeedProvider speedProvider;

  public InactiveTimestampAdjustment(SpeedProvider speedProvider) {
    this.speedProvider = speedProvider;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
    return new PassthroughShaderProgram();
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return true;
  }

  @Override
  public long getDurationAfterEffectApplied(long durationUs) {
    return SpeedProviderUtil.getDurationAfterSpeedProviderApplied(speedProvider, durationUs);
  }
}
