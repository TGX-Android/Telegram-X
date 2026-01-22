/*
 * Copyright 2023 The Android Open Source Project
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
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Applies a {@link TimestampWrapper} to apply a wrapped {@link GlEffect} on certain timestamps. */
/* package */ final class TimestampWrapperShaderProgram
    implements GlShaderProgram, GlShaderProgram.InputListener {

  private final long startTimeUs;
  private final long endTimeUs;
  private final WrappedShaderProgramInputListener wrappedShaderProgramInputListener;
  private final GlShaderProgram wrappedShaderProgram;
  private final GlShaderProgram copyShaderProgram;

  private int pendingWrappedGlShaderProgramFrames;
  private int pendingCopyGlShaderProgramFrames;

  /**
   * Creates a {@code TimestampWrapperShaderProgram} instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param timestampWrapper The {@link TimestampWrapper} to apply to each frame.
   */
  public TimestampWrapperShaderProgram(
      Context context, boolean useHdr, TimestampWrapper timestampWrapper)
      throws VideoFrameProcessingException {
    startTimeUs = timestampWrapper.startTimeUs;
    endTimeUs = timestampWrapper.endTimeUs;
    wrappedShaderProgram = timestampWrapper.glEffect.toGlShaderProgram(context, useHdr);
    wrappedShaderProgramInputListener = new WrappedShaderProgramInputListener();
    wrappedShaderProgram.setInputListener(wrappedShaderProgramInputListener);
    copyShaderProgram =
        new FrameCache(/* capacity= */ wrappedShaderProgramInputListener.readyFrameCount)
            .toGlShaderProgram(context, useHdr);
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    wrappedShaderProgramInputListener.setListener(inputListener);
    wrappedShaderProgramInputListener.setToForwardingMode(true);
    copyShaderProgram.setInputListener(inputListener);
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    wrappedShaderProgram.setOutputListener(outputListener);
    copyShaderProgram.setOutputListener(outputListener);
  }

  @Override
  public void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    wrappedShaderProgram.setErrorListener(errorListenerExecutor, errorListener);
    copyShaderProgram.setErrorListener(errorListenerExecutor, errorListener);
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    if (startTimeUs <= presentationTimeUs && presentationTimeUs <= endTimeUs) {
      pendingWrappedGlShaderProgramFrames++;
      wrappedShaderProgram.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeUs);
    } else {
      pendingCopyGlShaderProgramFrames++;
      copyShaderProgram.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeUs);
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    if (pendingCopyGlShaderProgramFrames > 0) {
      copyShaderProgram.releaseOutputFrame(outputTexture);
      pendingCopyGlShaderProgramFrames--;
    } else if (pendingWrappedGlShaderProgramFrames > 0) {
      wrappedShaderProgram.releaseOutputFrame(outputTexture);
      pendingWrappedGlShaderProgramFrames--;
    } else {
      throw new IllegalArgumentException("Output texture not contained in either shader.");
    }
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    // The copy shader program does not need special EOS handling, so only EOS signal along the
    // wrapped GL shader program.
    wrappedShaderProgram.signalEndOfCurrentInputStream();
  }

  @Override
  public void flush() {
    wrappedShaderProgramInputListener.setToForwardingMode(false);
    wrappedShaderProgram.flush();
    wrappedShaderProgramInputListener.setToForwardingMode(true);
    copyShaderProgram.flush();
    pendingCopyGlShaderProgramFrames = 0;
    pendingWrappedGlShaderProgramFrames = 0;
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    copyShaderProgram.release();
    wrappedShaderProgram.release();
  }

  private static final class WrappedShaderProgramInputListener
      implements GlShaderProgram.InputListener {
    public int readyFrameCount;

    private boolean forwardCalls;
    private @MonotonicNonNull InputListener listener;

    @Override
    public void onReadyToAcceptInputFrame() {
      if (listener == null) {
        readyFrameCount++;
      }
      if (forwardCalls) {
        checkNotNull(listener).onReadyToAcceptInputFrame();
      }
    }

    @Override
    public void onInputFrameProcessed(GlTextureInfo inputTexture) {
      checkNotNull(listener).onInputFrameProcessed(inputTexture);
    }

    @Override
    public void onFlush() {
      // The listener is flushed from the copy shader program.
    }

    public void setListener(InputListener listener) {
      this.listener = listener;
    }

    public void setToForwardingMode(boolean forwardingMode) {
      checkState(!forwardingMode || listener != null);
      this.forwardCalls = forwardingMode;
    }
  }
}
