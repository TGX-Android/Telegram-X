/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.effect;

import android.content.Context;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Applies a list of {@link TextureOverlay}s to a frame in FIFO order (the last overlay in the list
 * is displayed on top).
 *
 * <p>This effect assumes a non-{@linkplain DefaultVideoFrameProcessor#WORKING_COLOR_SPACE_LINEAR
 * linear} working color space for SDR input and a {@linkplain
 * DefaultVideoFrameProcessor#WORKING_COLOR_SPACE_LINEAR linear} working color space or HDR input.
 */
@UnstableApi
public final class OverlayEffect implements GlEffect {

  private final ImmutableList<TextureOverlay> overlays;

  /**
   * Creates a new instance for the given list of {@link TextureOverlay}s.
   *
   * @param textureOverlays The {@link TextureOverlay}s to be blended into the frame. To modify the
   *     list of {@link TextureOverlay TextureOverlays}, one must recreate a new {@code
   *     OverlayEffect} with the updated list.
   */
  public OverlayEffect(List<TextureOverlay> textureOverlays) {
    this.overlays = ImmutableList.copyOf(textureOverlays);
  }

  @Override
  public BaseGlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new OverlayShaderProgram(context, useHdr, overlays);
  }
}
