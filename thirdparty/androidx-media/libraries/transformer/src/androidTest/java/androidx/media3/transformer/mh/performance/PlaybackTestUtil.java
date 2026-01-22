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

package androidx.media3.transformer.mh.performance;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import androidx.media3.common.OverlaySettings;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import com.google.common.collect.ImmutableList;

/** Utilities for playback tests. */
/* package */ final class PlaybackTestUtil {

  private static final int DEFAULT_TEXT_SIZE = 300;

  private PlaybackTestUtil() {}

  /** Creates an {@link OverlayEffect} that draws the timestamp onto frames. */
  public static OverlayEffect createTimestampOverlay() {
    return createTimestampOverlay(DEFAULT_TEXT_SIZE);
  }

  /**
   * Creates an {@link OverlayEffect} that draws the timestamp onto frames with a specified text
   * size.
   */
  public static OverlayEffect createTimestampOverlay(int textSize) {
    return new OverlayEffect(
        ImmutableList.of(
            new TimestampTextOverlay(0, -0.7f, textSize),
            new TimestampTextOverlay(0, 0, textSize),
            new TimestampTextOverlay(0, 0.7f, textSize)));
  }

  private static class TimestampTextOverlay extends TextOverlay {

    private final float x;
    private final float y;
    private final int size;

    public TimestampTextOverlay(float x, float y, int size) {
      this.x = x;
      this.y = y;
      this.size = size;
    }

    @Override
    public SpannableString getText(long presentationTimeUs) {
      SpannableString text = new SpannableString(String.valueOf(presentationTimeUs));
      text.setSpan(
          new ForegroundColorSpan(Color.WHITE),
          /* start= */ 0,
          text.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      text.setSpan(
          new AbsoluteSizeSpan(size),
          /* start= */ 0,
          text.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      text.setSpan(
          new TypefaceSpan(/* family= */ "sans-serif"),
          /* start= */ 0,
          text.length(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      return text;
    }

    @Override
    public OverlaySettings getOverlaySettings(long presentationTimeUs) {
      return new StaticOverlaySettings.Builder().setBackgroundFrameAnchor(x, y).build();
    }
  }
}
