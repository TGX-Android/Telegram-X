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

import static androidx.media3.common.util.Assertions.checkArgument;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.opengl.GLES20;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlRect;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * A {@link GlEffect} implementation that runs an asynchronous {@link Processor} on video frame data
 * passed in as a {@link ByteBufferGlEffect.Image}.
 *
 * <p>This effect can be used to apply CPU-based effects. Or the provided {@link
 * ByteBufferGlEffect.Image} can be passed to other heterogeneous compute components that are
 * available such as another GPU context, FPGAs, or NPUs.
 */
@UnstableApi
public class ByteBufferGlEffect<T> implements GlEffect {

  private static final int DEFAULT_QUEUE_SIZE = 6;
  private static final int DEFAULT_PENDING_PIXEL_BUFFER_QUEUE_SIZE = 1;

  /** A class that represents image data is backed by a {@link ByteBuffer}. */
  public static class Image {
    public final int width;
    public final int height;
    public final ByteBuffer pixelBuffer;

    /**
     * Creates an instance.
     *
     * <p>The first pixel in the pixel buffer is the lower left corner of the image. Pixels are in
     * row order from the lowest to the highest row, left to right in each row.
     *
     * <p>The order of pixels is the same as the output of {@link GLES20#glReadPixels}, and differs
     * from the order of pixels of {@link Bitmap}.
     *
     * <p>For each pixel, the byte order is the same as {@link Bitmap.Config#ARGB_8888}. Each pixel
     * is stored in 4 bytes. Each channel (RGB and alpha for translucency) is stored with 8 bits of
     * precision. Use this formula to pack colors into 32 bits:
     *
     * <pre class="prettyprint">
     * {@code int color = (A & 0xff) << 24 | (B & 0xff) << 16 | (G & 0xff) << 8 | (R & 0xff);}
     * </pre>
     *
     * <p>On a little-endian machine, pixelBuffer.get(0) is the red pixel.
     *
     * @param width The width of the image.
     * @param height The height of the image.
     * @param pixelBuffer The pixel buffer.
     */
    /* package */ Image(int width, int height, ByteBuffer pixelBuffer) {
      checkArgument(pixelBuffer.capacity() == width * height * 4);
      this.width = width;
      this.height = height;
      this.pixelBuffer = pixelBuffer;
    }

    /**
     * Returns a {@link Bitmap} that contains a copy of the pixel buffer.
     *
     * <p>The returned {@link Bitmap} has config {@link Bitmap.Config#ARGB_8888}.
     *
     * <p>This method copies the pixel data and is less efficient than accessing the {@linkplain
     * #pixelBuffer pixel buffer} directly.
     */
    public Bitmap copyToBitmap() {
      // The order of pixels differs between OpenGL and Android Bitmap. The first pixel in OpenGL is
      // in the lower left corner, and the first pixel in Android Bitmap is in the top left corner.
      // Mirror the Bitmap's Y axis to return the correct pixel order.
      Bitmap bitmapInGlPixelLayout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      bitmapInGlPixelLayout.copyPixelsFromBuffer(pixelBuffer);
      Matrix glToAndroidTransformation = new Matrix();
      glToAndroidTransformation.setScale(/* sx= */ 1, /* sy= */ -1);
      return Bitmap.createBitmap(
          bitmapInGlPixelLayout,
          /* x= */ 0,
          /* y= */ 0,
          bitmapInGlPixelLayout.getWidth(),
          bitmapInGlPixelLayout.getHeight(),
          glToAndroidTransformation,
          /* filter= */ true);
    }
  }

  /**
   * A processor that takes in {@link ByteBuffer ByteBuffers} that represent input image data, and
   * produces results of type {@code <T>}.
   *
   * <p>All methods are called on the GL thread.
   *
   * @param <T> The result type of running the processor.
   */
  public interface Processor<T> {

    /**
     * Configures the instance and returns the dimensions of the image required by {@link
     * #processImage}.
     *
     * <p>When the returned dimensions differ from {@code inputWidth} and {@code inputHeight}, the
     * image will be scaled based on {@link #getScaledRegion}.
     *
     * @param inputWidth The input width in pixels.
     * @param inputHeight The input height in pixels.
     * @return The size in pixels of the image data accepted by {@link #processImage}.
     * @throws VideoFrameProcessingException On error.
     */
    Size configure(int inputWidth, int inputHeight) throws VideoFrameProcessingException;

    /**
     * Selects a region of the input texture that will be scaled to fill the image that is given to
     * {@link #processImage}.
     *
     * <p>Called once per input frame.
     *
     * <p>The contents are scaled to fit the image dimensions returned by {@link #configure}.
     *
     * @param presentationTimeUs The presentation time in microseconds.
     * @return The rectangular region of the input image that will be scaled to fill the effect
     *     input image.
     */
    GlRect getScaledRegion(long presentationTimeUs);

    /**
     * Processing the image data in the {@code image}.
     *
     * <p>Accessing {@code image} after the returned future is {@linkplain Future#isDone() done} or
     * {@linkplain Future#isCancelled() cancelled} can lead to undefined behaviour.
     *
     * @param image The image data.
     * @param presentationTimeUs The presentation time in microseconds.
     * @return A {@link ListenableFuture} of the result.
     */
    ListenableFuture<T> processImage(Image image, long presentationTimeUs);

    /**
     * Finishes processing the frame at {@code presentationTimeUs}. Use this method to perform
     * custom drawing on the output frame.
     *
     * <p>The {@linkplain GlTextureInfo outputFrame} contains the image data corresponding to the
     * frame at {@code presentationTimeUs} when this method is invoked.
     *
     * @param outputFrame The texture info of the frame.
     * @param presentationTimeUs The presentation timestamp of the frame, in microseconds.
     * @param result The result of the asynchronous computation in {@link #processImage}.
     */
    void finishProcessingAndBlend(GlTextureInfo outputFrame, long presentationTimeUs, T result)
        throws VideoFrameProcessingException;

    /**
     * Releases all resources.
     *
     * @throws VideoFrameProcessingException If an error occurs while releasing resources.
     */
    void release() throws VideoFrameProcessingException;
  }

  private final Processor<T> processor;

  /**
   * Creates an instance.
   *
   * @param processor The effect to apply.
   */
  public ByteBufferGlEffect(Processor<T> processor) {
    this.processor = processor;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    // TODO: b/361286064 - Implement HDR support.
    checkArgument(!useHdr, "HDR support not yet implemented.");
    return new QueuingGlShaderProgram<>(
        /* useHighPrecisionColorComponents= */ useHdr,
        /* queueSize= */ DEFAULT_QUEUE_SIZE,
        new ByteBufferConcurrentEffect<>(
            /* pendingPixelBufferQueueSize= */ DEFAULT_PENDING_PIXEL_BUFFER_QUEUE_SIZE, processor));
  }
}
