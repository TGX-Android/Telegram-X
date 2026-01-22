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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link TextOverlay} that is backed by a {@link Canvas}.
 *
 * <p>Use this class when the size of the drawing {@link Canvas} is known, or when drawing to the
 * entire video size is desied.
 */
@UnstableApi
public abstract class CanvasOverlay extends BitmapOverlay {
  private final boolean useInputFrameSize;

  private @MonotonicNonNull Bitmap lastBitmap;
  private @MonotonicNonNull Canvas lastCanvas;
  private volatile int width;
  private volatile int height;

  /**
   * Creates a new {@code CanvasOverlay}.
   *
   * @param useInputFrameSize Whether to create the {@link Canvas} to match the input frame size, if
   *     {@code false}, {@link #setCanvasSize(int, int)} must be set before the first invocation to
   *     {@link #onDraw}.
   */
  public CanvasOverlay(boolean useInputFrameSize) {
    this.useInputFrameSize = useInputFrameSize;
  }

  /**
   * Perform custom drawing onto the {@link Canvas}.
   *
   * @param canvas The {@link Canvas} to draw onto.
   * @param presentationTimeUs The presentation timestamp, in microseconds.
   */
  public abstract void onDraw(Canvas canvas, long presentationTimeUs);

  /**
   * Sets the size of the {@link Canvas}.
   *
   * <p>The default canvas size will be of the same size as the video frame.
   *
   * <p>The size will be applied on the next invocation of {@link #onDraw}.
   */
  public void setCanvasSize(int width, int height) {
    this.width = width;
    this.height = height;
  }

  @Override
  public void configure(Size videoSize) {
    super.configure(videoSize);
    if (useInputFrameSize) {
      setCanvasSize(videoSize.getWidth(), videoSize.getHeight());
    }
  }

  @Override
  public Bitmap getBitmap(long presentationTimeUs) {
    if (lastBitmap == null || lastBitmap.getWidth() != width || lastBitmap.getHeight() != height) {
      lastBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      lastCanvas = new Canvas(lastBitmap);
    }
    onDraw(checkNotNull(lastCanvas), presentationTimeUs);
    return lastBitmap;
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    if (lastBitmap != null) {
      lastBitmap.recycle();
    }
  }
}
