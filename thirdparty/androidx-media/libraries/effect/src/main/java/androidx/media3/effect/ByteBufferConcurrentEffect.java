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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.SDK_INT;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import android.opengl.GLES20;
import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlRect;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.SettableFuture;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link QueuingGlShaderProgram.ConcurrentEffect} implementation which wraps a {@link
 * ByteBufferGlEffect.Processor}.
 *
 * <p>This class is responsible for asynchronously transferring texture frame data to a
 * CPU-accessible {@link ByteBuffer} that will be used by the wrapped {@link
 * ByteBufferGlEffect.Processor}.
 */
/* package */ class ByteBufferConcurrentEffect<T>
    implements QueuingGlShaderProgram.ConcurrentEffect<T> {

  private static final int BYTES_PER_PIXEL = 4;

  private final ByteBufferGlEffect.Processor<T> processor;
  private final int pendingPixelBufferQueueSize;
  private final Queue<TexturePixelBuffer> unmappedPixelBuffers;
  private final Queue<TexturePixelBuffer> mappedPixelBuffers;
  private final PixelBufferObjectProvider pixelBufferObjectProvider;

  private int inputWidth;
  private int inputHeight;
  private @MonotonicNonNull GlTextureInfo effectInputTexture;

  /**
   * Creates an instance.
   *
   * @param pendingPixelBufferQueueSize The maximum number of scheduled but not yet completed
   *     texture to {@linkplain ByteBuffer pixel buffer} transfers.
   * @param processor The {@linkplain ByteBufferGlEffect.Processor effect}.
   */
  public ByteBufferConcurrentEffect(
      int pendingPixelBufferQueueSize, ByteBufferGlEffect.Processor<T> processor) {
    this.processor = processor;
    this.pendingPixelBufferQueueSize = pendingPixelBufferQueueSize;
    unmappedPixelBuffers = new ArrayDeque<>();
    mappedPixelBuffers = new ArrayDeque<>();
    pixelBufferObjectProvider = new PixelBufferObjectProvider();
    inputWidth = C.LENGTH_UNSET;
    inputHeight = C.LENGTH_UNSET;
  }

  @Override
  public Future<T> queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo textureInfo, long presentationTimeUs) {
    try {
      while (unmappedPixelBuffers.size() >= pendingPixelBufferQueueSize) {
        checkState(mapOnePixelBuffer());
      }

      if (effectInputTexture == null
          || textureInfo.width != inputWidth
          || textureInfo.height != inputHeight) {
        while (mapOnePixelBuffer()) {}
        inputWidth = textureInfo.width;
        inputHeight = textureInfo.height;
        Size effectInputSize = processor.configure(inputWidth, inputHeight);
        if (effectInputTexture != null) {
          effectInputTexture.release();
        }
        int texId =
            GlUtil.createTexture(
                effectInputSize.getWidth(),
                effectInputSize.getHeight(),
                /* useHighPrecisionColorComponents= */ false);
        effectInputTexture =
            glObjectsProvider.createBuffersForTexture(
                texId, effectInputSize.getWidth(), effectInputSize.getHeight());
      }

      GlUtil.blitFrameBuffer(
          textureInfo.fboId,
          processor.getScaledRegion(presentationTimeUs),
          effectInputTexture.fboId,
          new GlRect(effectInputTexture.width, effectInputTexture.height));

      TexturePixelBuffer texturePixelBuffer = new TexturePixelBuffer(effectInputTexture);
      texturePixelBuffer.schedulePixelBufferRead(pixelBufferObjectProvider);
      unmappedPixelBuffers.add(texturePixelBuffer);
      return Util.transformFutureAsync(
          texturePixelBuffer.imageSettableFuture,
          (image) -> processor.processImage(image, presentationTimeUs));
    } catch (GlUtil.GlException | VideoFrameProcessingException e) {
      return immediateFailedFuture(e);
    }
  }

  @Override
  public void finishProcessingAndBlend(GlTextureInfo textureInfo, long presentationTimeUs, T result)
      throws VideoFrameProcessingException {
    try {
      TexturePixelBuffer oldestRunningFrame = checkNotNull(mappedPixelBuffers.poll());
      oldestRunningFrame.unmapAndRecycle(pixelBufferObjectProvider);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }

    processor.finishProcessingAndBlend(textureInfo, presentationTimeUs, result);
  }

  @Override
  public void signalEndOfCurrentInputStream() throws VideoFrameProcessingException {
    try {
      while (mapOnePixelBuffer()) {}
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  @Override
  public void flush() throws VideoFrameProcessingException {
    try {
      unmapAndRecyclePixelBuffers();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    try {
      unmapAndRecyclePixelBuffers();
      pixelBufferObjectProvider.release();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private static int texturePixelBufferSize(GlTextureInfo textureInfo) {
    return textureInfo.width * textureInfo.height * BYTES_PER_PIXEL;
  }

  private void unmapAndRecyclePixelBuffers() throws GlUtil.GlException {
    TexturePixelBuffer texturePixelBuffer;
    while ((texturePixelBuffer = unmappedPixelBuffers.poll()) != null) {
      texturePixelBuffer.unmapAndRecycle(pixelBufferObjectProvider);
    }
    while ((texturePixelBuffer = mappedPixelBuffers.poll()) != null) {
      texturePixelBuffer.unmapAndRecycle(pixelBufferObjectProvider);
    }
  }

  private boolean mapOnePixelBuffer() throws GlUtil.GlException {
    TexturePixelBuffer texturePixelBuffer = unmappedPixelBuffers.poll();
    if (texturePixelBuffer == null) {
      return false;
    }
    texturePixelBuffer.map();
    mappedPixelBuffers.add(texturePixelBuffer);
    return true;
  }

  /**
   * Manages the lifecycle of a {@link PixelBufferObjectInfo} which is mapped to a {@link
   * GlTextureInfo}.
   */
  private static final class TexturePixelBuffer {
    public final SettableFuture<ByteBufferGlEffect.Image> imageSettableFuture;

    private final GlTextureInfo textureInfo;

    private @MonotonicNonNull PixelBufferObjectInfo pixelBufferObjectInfo;
    private boolean mapped;

    public TexturePixelBuffer(GlTextureInfo textureInfo) {
      this.textureInfo = textureInfo;
      imageSettableFuture = SettableFuture.create();
    }

    public void schedulePixelBufferRead(PixelBufferObjectProvider pixelBufferObjectProvider)
        throws GlUtil.GlException {
      int pixelBufferSize = texturePixelBufferSize(textureInfo);
      pixelBufferObjectInfo = pixelBufferObjectProvider.getPixelBufferObject(pixelBufferSize);
      if (SDK_INT >= 24) {
        GlUtil.schedulePixelBufferRead(
            textureInfo.fboId, textureInfo.width, textureInfo.height, pixelBufferObjectInfo.id);
      }
    }

    public void map() throws GlUtil.GlException {
      checkNotNull(pixelBufferObjectInfo);
      ByteBuffer byteBuffer;
      if (SDK_INT >= 24) {
        byteBuffer =
            GlUtil.mapPixelBufferObject(pixelBufferObjectInfo.id, pixelBufferObjectInfo.size);
      } else {
        // Asynchronous OpenGL reading isn't supported. Fall back to blocking glReadPixels.
        int pixelBufferSize = texturePixelBufferSize(textureInfo);
        byteBuffer = ByteBuffer.allocateDirect(pixelBufferSize);
        GlUtil.focusFramebufferUsingCurrentContext(
            textureInfo.fboId, textureInfo.width, textureInfo.height);
        GlUtil.checkGlError();
        GLES20.glReadPixels(
            /* x= */ 0,
            /* y= */ 0,
            textureInfo.width,
            textureInfo.height,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            byteBuffer);
        GlUtil.checkGlError();
      }
      imageSettableFuture.set(
          new ByteBufferGlEffect.Image(textureInfo.width, textureInfo.height, byteBuffer));
      mapped = true;
    }

    public void unmapAndRecycle(PixelBufferObjectProvider pixelBufferObjectProvider)
        throws GlUtil.GlException {
      checkNotNull(pixelBufferObjectInfo);
      if (mapped && SDK_INT >= 24) {
        GlUtil.unmapPixelBufferObject(pixelBufferObjectInfo.id);
      }
      pixelBufferObjectProvider.recycle(pixelBufferObjectInfo);
    }
  }

  /** One pixel buffer object with a data store. */
  private static final class PixelBufferObjectInfo {
    public final int id;
    public final int size;

    public PixelBufferObjectInfo(int size) throws GlUtil.GlException {
      this.size = size;
      id = GlUtil.createPixelBufferObject(size);
    }

    public void release() throws GlUtil.GlException {
      GlUtil.deleteBuffer(id);
    }
  }

  /** Provider for {@link PixelBufferObjectInfo} objects. */
  private static final class PixelBufferObjectProvider {
    private final Queue<PixelBufferObjectInfo> availablePixelBufferObjects;

    public PixelBufferObjectProvider() {
      availablePixelBufferObjects = new ArrayDeque<>();
    }

    private PixelBufferObjectInfo getPixelBufferObject(int pixelBufferSize)
        throws GlUtil.GlException {
      PixelBufferObjectInfo pixelBufferObjectInfo;
      while ((pixelBufferObjectInfo = availablePixelBufferObjects.poll()) != null) {
        if (pixelBufferObjectInfo.size == pixelBufferSize) {
          return pixelBufferObjectInfo;
        }
        GlUtil.deleteBuffer(pixelBufferObjectInfo.id);
      }
      return new PixelBufferObjectInfo(pixelBufferSize);
    }

    private void recycle(PixelBufferObjectInfo pixelBufferObjectInfo) {
      availablePixelBufferObjects.add(pixelBufferObjectInfo);
    }

    public void release() throws GlUtil.GlException {
      PixelBufferObjectInfo pixelBufferObjectInfo;
      while ((pixelBufferObjectInfo = availablePixelBufferObjects.poll()) != null) {
        pixelBufferObjectInfo.release();
      }
    }
  }
}
