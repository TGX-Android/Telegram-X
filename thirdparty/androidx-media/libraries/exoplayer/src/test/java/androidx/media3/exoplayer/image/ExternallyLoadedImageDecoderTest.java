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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateCancelledFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.util.concurrent.SettableFuture;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ExternallyLoadedImageDecoder}. */
@RunWith(AndroidJUnit4.class)
public class ExternallyLoadedImageDecoderTest {

  @Test
  public void factorySupportsFormat_externallyLoadedImage_returnsFormatSupported() {
    ExternallyLoadedImageDecoder.Factory factory =
        new ExternallyLoadedImageDecoder.Factory(request -> immediateCancelledFuture());
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE)
            .build();

    assertThat(factory.supportsFormat(format))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_HANDLED));
  }

  @Test
  public void factorySupportsFormat_noSampleMimeType_returnsUnsupportedType() {
    ExternallyLoadedImageDecoder.Factory factory =
        new ExternallyLoadedImageDecoder.Factory(request -> immediateCancelledFuture());
    Format format = new Format.Builder().build();

    assertThat(factory.supportsFormat(format))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE));
  }

  @Test
  public void factorySupportsFormat_nonImageMimeType_returnsUnsupportedType() {
    ExternallyLoadedImageDecoder.Factory factory =
        new ExternallyLoadedImageDecoder.Factory(request -> immediateCancelledFuture());
    Format format = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_AV1).build();

    assertThat(factory.supportsFormat(format))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE));
  }

  @Test
  public void factorySupportsFormat_unsupportedImageMimeType_returnsUnsupportedSubType() {
    ExternallyLoadedImageDecoder.Factory factory =
        new ExternallyLoadedImageDecoder.Factory(request -> immediateCancelledFuture());
    Format format = new Format.Builder().setSampleMimeType("image/custom").build();

    assertThat(factory.supportsFormat(format))
        .isEqualTo(RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE));
  }

  @Test
  public void decoding_withMultipleBuffersAndEndOfStream_producesExpectedOutput() throws Exception {
    Uri uri1 = Uri.parse("https://image1_longer_name_than_image2.test");
    Uri uri2 = Uri.parse("https://image2.test");
    byte[] uri1Bytes = uri1.toString().getBytes(StandardCharsets.UTF_8);
    byte[] uri2Bytes = uri2.toString().getBytes(StandardCharsets.UTF_8);
    Bitmap bitmap1 = Bitmap.createBitmap(/* width= */ 5, /* height= */ 5, Bitmap.Config.ARGB_8888);
    Bitmap bitmap2 = Bitmap.createBitmap(/* width= */ 7, /* height= */ 7, Bitmap.Config.ARGB_8888);
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(
                request -> immediateFuture(request.uri.equals(uri1) ? bitmap1 : bitmap2))
            .createImageDecoder();

    DecoderInputBuffer inputBuffer1 = decoder.dequeueInputBuffer();
    inputBuffer1.timeUs = 555;
    inputBuffer1.ensureSpaceForWrite(uri1Bytes.length);
    inputBuffer1.data.put(uri1Bytes);
    inputBuffer1.data.flip();
    decoder.queueInputBuffer(inputBuffer1);
    ImageOutputBuffer outputBuffer1 = decoder.dequeueOutputBuffer();

    assertThat(outputBuffer1.timeUs).isEqualTo(555);
    assertThat(outputBuffer1.isEndOfStream()).isFalse();
    assertThat(outputBuffer1.bitmap).isEqualTo(bitmap1);

    outputBuffer1.release();
    DecoderInputBuffer inputBuffer2 = decoder.dequeueInputBuffer();
    inputBuffer2.timeUs = 777;
    inputBuffer2.ensureSpaceForWrite(uri2Bytes.length);
    inputBuffer2.data.put(uri2Bytes);
    inputBuffer2.data.flip();
    decoder.queueInputBuffer(inputBuffer2);
    ImageOutputBuffer outputBuffer2 = decoder.dequeueOutputBuffer();

    assertThat(outputBuffer2.timeUs).isEqualTo(777);
    assertThat(outputBuffer2.isEndOfStream()).isFalse();
    assertThat(outputBuffer2.bitmap).isEqualTo(bitmap2);

    outputBuffer2.release();
    DecoderInputBuffer inputBufferEos = decoder.dequeueInputBuffer();
    inputBufferEos.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    decoder.queueInputBuffer(inputBufferEos);
    ImageOutputBuffer outputBufferEos = decoder.dequeueOutputBuffer();

    assertThat(outputBufferEos.isEndOfStream()).isTrue();
  }

  @Test
  public void dequeueOutputBuffer_withDelayedBitmap_onlyReturnsOutputWhenReady() throws Exception {
    Uri uri = Uri.parse("https://image.test");
    byte[] uriBytes = uri.toString().getBytes(StandardCharsets.UTF_8);
    Bitmap bitmap = Bitmap.createBitmap(/* width= */ 5, /* height= */ 5, Bitmap.Config.ARGB_8888);
    SettableFuture<Bitmap> settableFuture = SettableFuture.create();
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(request -> settableFuture).createImageDecoder();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.timeUs = 555;
    inputBuffer.ensureSpaceForWrite(uriBytes.length);
    inputBuffer.data.put(uriBytes);
    inputBuffer.data.flip();
    decoder.queueInputBuffer(inputBuffer);

    assertThat(decoder.dequeueOutputBuffer()).isNull();
    settableFuture.set(bitmap);
    assertThat(decoder.dequeueOutputBuffer()).isNotNull();
  }

  @Test
  public void dequeueOutputBuffer_withFailedFuture_throwsExceptionWithOriginalCause()
      throws Exception {
    Uri uri = Uri.parse("https://image.test");
    byte[] uriBytes = uri.toString().getBytes(StandardCharsets.UTF_8);
    Throwable testThrowable = new Throwable();
    SettableFuture<Bitmap> settableFuture = SettableFuture.create();
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(request -> settableFuture).createImageDecoder();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.timeUs = 555;
    inputBuffer.ensureSpaceForWrite(uriBytes.length);
    inputBuffer.data.put(uriBytes);
    inputBuffer.data.flip();
    decoder.queueInputBuffer(inputBuffer);

    assertThat(decoder.dequeueOutputBuffer()).isNull();
    settableFuture.setException(testThrowable);
    ImageDecoderException exception =
        assertThrows(ImageDecoderException.class, decoder::dequeueOutputBuffer);
    assertThat(exception).hasCauseThat().isEqualTo(testThrowable);
  }

  @Test
  public void
      dequeueOutputBuffer_withCancelledFuture_throwsExceptionWithCancellationExceptionCause()
          throws Exception {
    Uri uri = Uri.parse("https://image.test");
    byte[] uriBytes = uri.toString().getBytes(StandardCharsets.UTF_8);
    SettableFuture<Bitmap> settableFuture = SettableFuture.create();
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(request -> settableFuture).createImageDecoder();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.timeUs = 555;
    inputBuffer.ensureSpaceForWrite(uriBytes.length);
    inputBuffer.data.put(uriBytes);
    inputBuffer.data.flip();
    decoder.queueInputBuffer(inputBuffer);

    assertThat(decoder.dequeueOutputBuffer()).isNull();
    settableFuture.cancel(/* mayInterruptIfRunning= */ false);
    ImageDecoderException exception =
        assertThrows(ImageDecoderException.class, decoder::dequeueOutputBuffer);
    assertThat(exception).hasCauseThat().isInstanceOf(CancellationException.class);
  }

  @Test
  public void flush_beforeFirstBuffer_allowsToQueueNextBuffer() throws Exception {
    Uri uri = Uri.parse("https://image.test");
    byte[] uriBytes = uri.toString().getBytes(StandardCharsets.UTF_8);
    Bitmap bitmap = Bitmap.createBitmap(/* width= */ 5, /* height= */ 5, Bitmap.Config.ARGB_8888);
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(request -> immediateFuture(bitmap))
            .createImageDecoder();

    decoder.flush();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.timeUs = 555;
    inputBuffer.ensureSpaceForWrite(uriBytes.length);
    inputBuffer.data.put(uriBytes);
    inputBuffer.data.flip();
    decoder.queueInputBuffer(inputBuffer);
    ImageOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();

    assertThat(outputBuffer.timeUs).isEqualTo(555);
    assertThat(outputBuffer.isEndOfStream()).isFalse();
    assertThat(outputBuffer.bitmap).isEqualTo(bitmap);
  }

  @Test
  public void flush_duringDecoding_cancelsPendingDecodeAndAllowsToQueueNextBuffer()
      throws Exception {
    Uri uri1 = Uri.parse("https://image1.test");
    Uri uri2 = Uri.parse("https://image2.test");
    byte[] uri1Bytes = uri1.toString().getBytes(StandardCharsets.UTF_8);
    byte[] uri2Bytes = uri2.toString().getBytes(StandardCharsets.UTF_8);
    Bitmap bitmap2 = Bitmap.createBitmap(/* width= */ 7, /* height= */ 7, Bitmap.Config.ARGB_8888);
    SettableFuture<Bitmap> settableFuture = SettableFuture.create();
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(
                request -> request.uri.equals(uri1) ? settableFuture : immediateFuture(bitmap2))
            .createImageDecoder();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.timeUs = 111;
    inputBuffer.ensureSpaceForWrite(uri1Bytes.length);
    inputBuffer.data.put(uri1Bytes);
    inputBuffer.data.flip();
    decoder.queueInputBuffer(inputBuffer);

    decoder.flush();
    DecoderInputBuffer newInputBuffer = decoder.dequeueInputBuffer();
    newInputBuffer.timeUs = 555;
    newInputBuffer.ensureSpaceForWrite(uri2Bytes.length);
    newInputBuffer.data.put(uri2Bytes);
    newInputBuffer.data.flip();
    decoder.queueInputBuffer(newInputBuffer);
    ImageOutputBuffer newOutputBuffer = decoder.dequeueOutputBuffer();

    assertThat(settableFuture.isCancelled()).isTrue();
    assertThat(newOutputBuffer.timeUs).isEqualTo(555);
    assertThat(newOutputBuffer.isEndOfStream()).isFalse();
    assertThat(newOutputBuffer.bitmap).isEqualTo(bitmap2);
  }

  @Test
  public void flush_afterDecoding_allowsToQueueNextBuffer() throws Exception {
    Uri uri1 = Uri.parse("https://image1.test");
    Uri uri2 = Uri.parse("https://image2.test");
    byte[] uri1Bytes = uri1.toString().getBytes(StandardCharsets.UTF_8);
    byte[] uri2Bytes = uri2.toString().getBytes(StandardCharsets.UTF_8);
    Bitmap bitmap1 = Bitmap.createBitmap(/* width= */ 5, /* height= */ 5, Bitmap.Config.ARGB_8888);
    Bitmap bitmap2 = Bitmap.createBitmap(/* width= */ 7, /* height= */ 7, Bitmap.Config.ARGB_8888);
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(
                request -> immediateFuture(request.uri.equals(uri1) ? bitmap1 : bitmap2))
            .createImageDecoder();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.timeUs = 111;
    inputBuffer.ensureSpaceForWrite(uri1Bytes.length);
    inputBuffer.data.put(uri1Bytes);
    inputBuffer.data.flip();
    decoder.queueInputBuffer(inputBuffer);
    ImageOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();
    outputBuffer.release();

    decoder.flush();
    DecoderInputBuffer newInputBuffer = decoder.dequeueInputBuffer();
    newInputBuffer.timeUs = 555;
    newInputBuffer.ensureSpaceForWrite(uri2Bytes.length);
    newInputBuffer.data.put(uri2Bytes);
    newInputBuffer.data.flip();
    decoder.queueInputBuffer(newInputBuffer);
    ImageOutputBuffer newOutputBuffer = decoder.dequeueOutputBuffer();

    assertThat(newOutputBuffer.timeUs).isEqualTo(555);
    assertThat(newOutputBuffer.isEndOfStream()).isFalse();
    assertThat(newOutputBuffer.bitmap).isEqualTo(bitmap2);
  }

  @Test
  public void flush_duringEndOfStreamSample_allowsToQueueNextBuffer() throws Exception {
    Uri uri = Uri.parse("https://image1.test");
    byte[] uriBytes = uri.toString().getBytes(StandardCharsets.UTF_8);
    Bitmap bitmap = Bitmap.createBitmap(/* width= */ 5, /* height= */ 5, Bitmap.Config.ARGB_8888);
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(request -> immediateFuture(bitmap))
            .createImageDecoder();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    decoder.queueInputBuffer(inputBuffer);

    decoder.flush();
    DecoderInputBuffer newInputBuffer = decoder.dequeueInputBuffer();
    newInputBuffer.timeUs = 555;
    newInputBuffer.ensureSpaceForWrite(uriBytes.length);
    newInputBuffer.data.put(uriBytes);
    newInputBuffer.data.flip();
    decoder.queueInputBuffer(newInputBuffer);
    ImageOutputBuffer newOutputBuffer = decoder.dequeueOutputBuffer();

    assertThat(newOutputBuffer.timeUs).isEqualTo(555);
    assertThat(newOutputBuffer.isEndOfStream()).isFalse();
    assertThat(newOutputBuffer.bitmap).isEqualTo(bitmap);
  }

  @Test
  public void flush_afterEndOfStreamSample_allowsToQueueNextBuffer() throws Exception {
    Uri uri = Uri.parse("https://image1.test");
    byte[] uriBytes = uri.toString().getBytes(StandardCharsets.UTF_8);
    Bitmap bitmap = Bitmap.createBitmap(/* width= */ 5, /* height= */ 5, Bitmap.Config.ARGB_8888);
    ExternallyLoadedImageDecoder decoder =
        new ExternallyLoadedImageDecoder.Factory(request -> immediateFuture(bitmap))
            .createImageDecoder();
    DecoderInputBuffer inputBuffer = decoder.dequeueInputBuffer();
    inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
    decoder.queueInputBuffer(inputBuffer);
    ImageOutputBuffer outputBuffer = decoder.dequeueOutputBuffer();
    outputBuffer.release();

    decoder.flush();
    DecoderInputBuffer newInputBuffer = decoder.dequeueInputBuffer();
    newInputBuffer.timeUs = 555;
    newInputBuffer.ensureSpaceForWrite(uriBytes.length);
    newInputBuffer.data.put(uriBytes);
    newInputBuffer.data.flip();
    decoder.queueInputBuffer(newInputBuffer);
    ImageOutputBuffer newOutputBuffer = decoder.dequeueOutputBuffer();

    assertThat(newOutputBuffer.timeUs).isEqualTo(555);
    assertThat(newOutputBuffer.isEndOfStream()).isFalse();
    assertThat(newOutputBuffer.bitmap).isEqualTo(bitmap);
  }
}
