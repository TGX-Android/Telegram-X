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
package androidx.media3.exoplayer.image;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.RendererCapabilities;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * An {@link ImageDecoder} for externally loaded images.
 *
 * @see MimeTypes#APPLICATION_EXTERNALLY_LOADED_IMAGE
 */
@UnstableApi
public final class ExternallyLoadedImageDecoder implements ImageDecoder {

  /** A data class providing information about the external image request. */
  public static final class ExternalImageRequest {

    /** The {@link Uri} for the external image. */
    public final Uri uri;

    /**
     * Creates an instance.
     *
     * @param uri The {@link Uri} for the external image.
     */
    public ExternalImageRequest(Uri uri) {
      this.uri = uri;
    }
  }

  /** The resolver that resolves an external image request to a Bitmap. */
  public interface BitmapResolver {

    /**
     * Returns a {@link ListenableFuture} for the Bitmap referenced by the given {@link
     * ExternalImageRequest}.
     *
     * @param request The {@link ExternalImageRequest}.
     * @return A {@link ListenableFuture} returning the {@link Bitmap} for the request.
     */
    ListenableFuture<Bitmap> resolve(ExternalImageRequest request);
  }

  /** A {@link ImageDecoder.Factory} for {@link ExternallyLoadedImageDecoder} instances. */
  public static final class Factory implements ImageDecoder.Factory {

    private final BitmapResolver bitmapResolver;

    /**
     * Creates the factory.
     *
     * @param bitmapResolver The {@link BitmapResolver} to resolve the {@link ExternalImageRequest}
     *     to a {@link Bitmap}.
     */
    public Factory(BitmapResolver bitmapResolver) {
      this.bitmapResolver = bitmapResolver;
    }

    @Override
    public @RendererCapabilities.Capabilities int supportsFormat(Format format) {
      boolean isExternallyLoadedImage =
          Objects.equals(format.sampleMimeType, MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE);
      return RendererCapabilities.create(
          isExternallyLoadedImage
              ? C.FORMAT_HANDLED
              : MimeTypes.isImage(format.sampleMimeType)
                  ? C.FORMAT_UNSUPPORTED_SUBTYPE
                  : C.FORMAT_UNSUPPORTED_TYPE);
    }

    @Override
    public ExternallyLoadedImageDecoder createImageDecoder() {
      return new ExternallyLoadedImageDecoder(bitmapResolver);
    }
  }

  private final BitmapResolver bitmapResolver;
  private final DecoderInputBuffer inputBuffer;
  private final ImageOutputBuffer outputBuffer;

  @Nullable private ListenableFuture<Bitmap> pendingDecode;
  private long pendingDecodeTimeUs;
  private boolean pendingEndOfStream;

  private ExternallyLoadedImageDecoder(BitmapResolver bitmapResolver) {
    this.bitmapResolver = bitmapResolver;
    this.inputBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    this.outputBuffer =
        new ImageOutputBuffer() {
          @Override
          public void release() {
            clear();
          }
        };
  }

  @Override
  public String getName() {
    return "externallyLoadedImageDecoder";
  }

  @Override
  public void setOutputStartTimeUs(long outputStartTimeUs) {
    // Intentionally unused to render images that start earlier than the intended start time.
  }

  @Nullable
  @Override
  public DecoderInputBuffer dequeueInputBuffer() {
    return pendingDecode == null ? inputBuffer : null;
  }

  @Override
  public void queueInputBuffer(DecoderInputBuffer inputBuffer) {
    if (inputBuffer.isEndOfStream()) {
      pendingEndOfStream = true;
      inputBuffer.clear();
      return;
    }
    ByteBuffer inputData = checkNotNull(inputBuffer.data);
    checkState(inputData.hasArray());
    Uri imageUri =
        Uri.parse(
            new String(
                inputData.array(),
                inputData.arrayOffset(),
                inputData.remaining(),
                StandardCharsets.UTF_8));
    pendingDecode = bitmapResolver.resolve(new ExternalImageRequest(imageUri));
    pendingDecodeTimeUs = inputBuffer.timeUs;
    inputBuffer.clear();
  }

  @Nullable
  @Override
  public ImageOutputBuffer dequeueOutputBuffer() throws ImageDecoderException {
    if (pendingEndOfStream) {
      outputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      pendingEndOfStream = false;
      return outputBuffer;
    }
    if (pendingDecode == null || !pendingDecode.isDone()) {
      return null;
    }
    try {
      outputBuffer.bitmap = Futures.getDone(pendingDecode);
      outputBuffer.timeUs = pendingDecodeTimeUs;
      return outputBuffer;
    } catch (ExecutionException e) {
      throw new ImageDecoderException(e.getCause());
    } catch (CancellationException e) {
      throw new ImageDecoderException(e);
    } finally {
      pendingDecode = null;
    }
  }

  @Override
  public void flush() {
    resetState();
  }

  @Override
  public void release() {
    resetState();
  }

  private void resetState() {
    if (pendingDecode != null) {
      pendingDecode.cancel(/* mayInterruptIfRunning= */ false);
      pendingDecode = null;
    }
    pendingEndOfStream = false;
    outputBuffer.release();
  }
}
