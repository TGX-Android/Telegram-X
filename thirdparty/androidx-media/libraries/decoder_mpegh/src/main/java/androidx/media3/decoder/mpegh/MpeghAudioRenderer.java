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
package androidx.media3.decoder.mpegh;

import android.os.Handler;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;
import java.util.Objects;

/** Decodes and renders audio using the native MPEG-H decoder. */
@UnstableApi
public final class MpeghAudioRenderer extends DecoderAudioRenderer<MpeghDecoder> {

  private static final String TAG = "MpeghAudioRenderer";

  /** The number of input and output buffers. */
  private static final int NUM_BUFFERS = 16;

  /*  Creates a new instance. */
  public MpeghAudioRenderer() {
    this(/* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public MpeghAudioRenderer(
      Handler eventHandler,
      AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, audioProcessors);
  }

  /**
   * Creates a new instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public MpeghAudioRenderer(
      Handler eventHandler, AudioRendererEventListener eventListener, AudioSink audioSink) {
    super(eventHandler, eventListener, audioSink);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected @C.FormatSupport int supportsFormatInternal(Format format) {
    // Check if JNI library is available.
    if (!MpeghLibrary.isAvailable()) {
      return C.FORMAT_UNSUPPORTED_TYPE;
    }

    // Check if MIME type is supported.
    if (!(Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_MPEGH_MHM1)
        || Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_MPEGH_MHA1))) {
      return C.FORMAT_UNSUPPORTED_TYPE;
    }
    return C.FORMAT_HANDLED;
  }

  @Override
  protected DecoderReuseEvaluation canReuseDecoder(
      String decoderName, Format oldFormat, Format newFormat) {
    if (Objects.equals(oldFormat.sampleMimeType, newFormat.sampleMimeType)
        && Objects.equals(oldFormat.sampleMimeType, MimeTypes.AUDIO_MPEGH_MHM1)) {
      return new DecoderReuseEvaluation(
          decoderName,
          oldFormat,
          newFormat,
          DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION,
          /* discardReasons= */ 0);
    }
    return super.canReuseDecoder(decoderName, oldFormat, newFormat);
  }

  @Override
  protected MpeghDecoder createDecoder(Format format, CryptoConfig cryptoConfig)
      throws MpeghDecoderException {
    TraceUtil.beginSection("createMpeghDecoder");
    MpeghDecoder decoder = new MpeghDecoder(format, NUM_BUFFERS, NUM_BUFFERS);
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  protected Format getOutputFormat(MpeghDecoder decoder) {
    return new Format.Builder()
        .setChannelCount(decoder.getChannelCount())
        .setSampleRate(decoder.getSampleRate())
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .build();
  }
}
