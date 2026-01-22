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
package androidx.media3.extractor.ts;

import static androidx.media3.extractor.ts.TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR;
import static androidx.media3.extractor.ts.TsPayloadReader.FLAG_RANDOM_ACCESS_INDICATOR;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Parses a continuous MPEG-H audio byte stream and extracts MPEG-H frames. */
@UnstableApi
public final class MpeghReader implements ElementaryStreamReader {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_FINDING_SYNC, STATE_READING_PACKET_HEADER, STATE_READING_PACKET_PAYLOAD})
  private @interface State {}

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_PACKET_HEADER = 1;
  private static final int STATE_READING_PACKET_PAYLOAD = 2;

  private static final int MHAS_SYNC_WORD_LENGTH = 3;
  private static final int MIN_MHAS_PACKET_HEADER_SIZE = 2;
  private static final int MAX_MHAS_PACKET_HEADER_SIZE = 15;

  private final String containerMimeType;
  private final ParsableByteArray headerScratchBytes;
  private final ParsableBitArray headerScratchBits;
  private final ParsableByteArray dataScratchBytes;

  private @State int state;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;

  // The timestamp to attach to the next sample in the current packet.
  private double timeUs;
  private double timeUsPending;
  private boolean dataPending;
  private boolean rapPending;
  private @TsPayloadReader.Flags int flags;

  private int syncBytes;

  private boolean headerDataFinished;
  private int payloadBytesRead;
  private int frameBytes;

  private MpeghUtil.MhasPacketHeader header;
  private int samplingRate;
  private int standardFrameLength;
  private int truncationSamples;
  private long mainStreamLabel;
  private boolean configFound;

  /**
   * Constructs a new reader for MPEG-H elementary streams.
   *
   * @param containerMimeType The MIME type of the container holding the stream.
   */
  public MpeghReader(String containerMimeType) {
    this.containerMimeType = containerMimeType;
    state = STATE_FINDING_SYNC;
    headerScratchBytes =
        new ParsableByteArray(new byte[MAX_MHAS_PACKET_HEADER_SIZE], MIN_MHAS_PACKET_HEADER_SIZE);
    headerScratchBits = new ParsableBitArray();
    dataScratchBytes = new ParsableByteArray();
    header = new MpeghUtil.MhasPacketHeader();
    samplingRate = C.RATE_UNSET_INT;
    standardFrameLength = C.LENGTH_UNSET;
    mainStreamLabel = C.INDEX_UNSET;
    rapPending = true;
    headerDataFinished = true;
    timeUs = C.TIME_UNSET;
    timeUsPending = C.TIME_UNSET;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    syncBytes = 0;
    headerScratchBytes.reset(MIN_MHAS_PACKET_HEADER_SIZE);
    payloadBytesRead = 0;
    frameBytes = 0;
    samplingRate = C.RATE_UNSET_INT;
    standardFrameLength = C.LENGTH_UNSET;
    truncationSamples = 0;
    mainStreamLabel = C.INDEX_UNSET;
    configFound = false;
    dataPending = false;
    headerDataFinished = true;
    rapPending = true;
    timeUs = C.TIME_UNSET;
    timeUsPending = C.TIME_UNSET;
  }

  @Override
  public void createTracks(
      ExtractorOutput extractorOutput, TsPayloadReader.TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    this.flags = flags;

    // check if data is pending (an MPEG-H frame could not be completed)
    if (!rapPending && (frameBytes != 0 || !headerDataFinished)) {
      dataPending = true;
    }

    if (pesTimeUs != C.TIME_UNSET) {
      if (dataPending) {
        timeUsPending = pesTimeUs;
      } else {
        timeUs = pesTimeUs;
      }
    }
  }

  @Override
  public void consume(ParsableByteArray data) throws ParserException {
    Assertions.checkStateNotNull(output); // Asserts that createTracks has been called.

    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSync(data)) {
            state = STATE_READING_PACKET_HEADER;
          }
          break;
        case STATE_READING_PACKET_HEADER:
          copyData(data, headerScratchBytes, /* resetSourcePosition= */ false);
          if (headerScratchBytes.bytesLeft() == 0) {
            if (parseHeader()) {
              // write the MHAS packet header to output
              headerScratchBytes.setPosition(0);
              output.sampleData(headerScratchBytes, headerScratchBytes.limit());

              // Prepare headerScratchBytes to read next header in the stream
              headerScratchBytes.reset(MIN_MHAS_PACKET_HEADER_SIZE);

              // Prepare dataScratchBytes to read new MHAS packet
              dataScratchBytes.reset(header.packetLength);

              headerDataFinished = true;

              // MHAS packet header finished -> obtain the packet payload
              state = STATE_READING_PACKET_PAYLOAD;
            } else if (headerScratchBytes.limit() < MAX_MHAS_PACKET_HEADER_SIZE) {
              headerScratchBytes.setLimit(headerScratchBytes.limit() + 1);
              headerDataFinished = false;
            }
          } else {
            headerDataFinished = false;
          }
          break;
        case STATE_READING_PACKET_PAYLOAD:
          if (shouldParsePacket(header.packetType)) {
            copyData(data, dataScratchBytes, /* resetSourcePosition= */ true);
          }
          writeSampleData(data);
          if (payloadBytesRead == header.packetLength) {
            if (header.packetType == MpeghUtil.MhasPacketHeader.PACTYP_MPEGH3DACFG) {
              parseConfig(new ParsableBitArray(dataScratchBytes.getData()));
            } else if (header.packetType == MpeghUtil.MhasPacketHeader.PACTYP_AUDIOTRUNCATION) {
              truncationSamples =
                  MpeghUtil.parseAudioTruncationInfo(
                      new ParsableBitArray(dataScratchBytes.getData()));
            } else if (header.packetType == MpeghUtil.MhasPacketHeader.PACTYP_MPEGH3DAFRAME) {
              finalizeFrame();
            }
            // MHAS packet payload finished -> obtain a new packet header
            state = STATE_READING_PACKET_HEADER;
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void packetFinished(boolean isEndOfInput) {
    // Do nothing.
  }

  /**
   * Copies data from the provided {@code source} into a given {@code target}, attempting to fill
   * the target buffer up to its limit.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read.
   * @param resetSourcePosition Whether to reset the source position to its original value
   */
  private void copyData(
      ParsableByteArray source, ParsableByteArray target, boolean resetSourcePosition) {
    int sourcePosition = source.getPosition();
    int bytesToRead = min(source.bytesLeft(), target.bytesLeft());
    source.readBytes(target.getData(), target.getPosition(), bytesToRead);
    target.skipBytes(bytesToRead);
    if (resetSourcePosition) {
      source.setPosition(sourcePosition);
    }
  }

  /**
   * Locates the next SYNC value in the buffer, advancing the position to the byte starting with the
   * SYNC value. If SYNC was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return Whether SYNC was found.
   */
  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    if ((flags & FLAG_RANDOM_ACCESS_INDICATOR) == 0) {
      // RAI is not signalled -> drop the PES data
      pesBuffer.setPosition(pesBuffer.limit());
      return false;
    }

    if ((flags & FLAG_DATA_ALIGNMENT_INDICATOR) == 0) {
      // if RAI is signalled but the data is not aligned we need to find the sync packet
      while (pesBuffer.bytesLeft() > 0) {
        syncBytes <<= C.BITS_PER_BYTE;
        syncBytes |= pesBuffer.readUnsignedByte();
        if (MpeghUtil.isSyncWord(syncBytes)) {
          pesBuffer.setPosition(pesBuffer.getPosition() - MHAS_SYNC_WORD_LENGTH);
          syncBytes = 0;
          return true;
        }
      }
    } else {
      return true;
    }
    return false;
  }

  /**
   * Parses the MHAS packet header.
   *
   * @return {@code true} if the parsing is successful, {@code false} otherwise.
   * @throws ParserException if an error occurred during parsing {@link MpeghUtil.MhasPacketHeader}.
   */
  private boolean parseHeader() throws ParserException {
    int headerLength = headerScratchBytes.limit();
    headerScratchBits.reset(headerScratchBytes.getData(), headerLength);

    // parse the MHAS packet header
    boolean result = MpeghUtil.parseMhasPacketHeader(headerScratchBits, header);

    if (result) {
      payloadBytesRead = 0;
      frameBytes += header.packetLength + headerLength;
    }

    return result;
  }

  /**
   * Determines whether a packet should be parsed based on its type.
   *
   * @param packetType The {@link MpeghUtil.MhasPacketHeader.Type} of the MHAS packet header.
   * @return {@code true} if the packet type is either {@link
   *     MpeghUtil.MhasPacketHeader#PACTYP_MPEGH3DACFG} or {@link
   *     MpeghUtil.MhasPacketHeader#PACTYP_AUDIOTRUNCATION}, {@code false} otherwise.
   */
  private boolean shouldParsePacket(@MpeghUtil.MhasPacketHeader.Type int packetType) {
    return packetType == MpeghUtil.MhasPacketHeader.PACTYP_MPEGH3DACFG
        || packetType == MpeghUtil.MhasPacketHeader.PACTYP_AUDIOTRUNCATION;
  }

  /**
   * Writes sample data to the output.
   *
   * @param data A {@link ParsableByteArray} from which to read the sample data.
   */
  @RequiresNonNull("output")
  private void writeSampleData(ParsableByteArray data) {
    // read bytes from input data and write them into the output
    int bytesToRead = min(data.bytesLeft(), header.packetLength - payloadBytesRead);
    output.sampleData(data, bytesToRead);
    payloadBytesRead += bytesToRead;
  }

  /**
   * Parses the config and sets the output format.
   *
   * @param bitArray The data to parse, positioned at the start of the {@link
   *     MpeghUtil.Mpegh3daConfig} field. Must be byte-aligned.
   * @throws ParserException if a valid {@link MpeghUtil.Mpegh3daConfig} cannot be parsed.
   */
  @RequiresNonNull("output")
  private void parseConfig(ParsableBitArray bitArray) throws ParserException {
    MpeghUtil.Mpegh3daConfig config = MpeghUtil.parseMpegh3daConfig(bitArray);
    samplingRate = config.samplingFrequency;
    standardFrameLength = config.standardFrameLength;
    if (mainStreamLabel != header.packetLabel) {
      mainStreamLabel = header.packetLabel;
      // set the output format
      String codecs = "mhm1";
      if (config.profileLevelIndication != C.INDEX_UNSET) {
        codecs += String.format(".%02X", config.profileLevelIndication);
      }
      @Nullable List<byte[]> initializationData = null;
      if (config.compatibleProfileLevelSet != null && config.compatibleProfileLevelSet.length > 0) {
        // The first entry in initializationData is reserved for the audio specific
        // config.
        initializationData =
            ImmutableList.of(Util.EMPTY_BYTE_ARRAY, config.compatibleProfileLevelSet);
      }
      Format format =
          new Format.Builder()
              .setId(formatId)
              .setContainerMimeType(containerMimeType)
              .setSampleMimeType(MimeTypes.AUDIO_MPEGH_MHM1)
              .setSampleRate(samplingRate)
              .setCodecs(codecs)
              .setInitializationData(initializationData)
              .build();
      output.format(format);
    }
    configFound = true;
  }

  /** Finalizes an MPEG-H frame. */
  @RequiresNonNull("output")
  private void finalizeFrame() {
    @C.BufferFlags int flag = 0;
    // if we have a frame with an mpegh3daConfig, set the obtained AU to a key frame
    if (configFound) {
      flag = C.BUFFER_FLAG_KEY_FRAME;
      rapPending = false;
    }
    double sampleDurationUs =
        (double) C.MICROS_PER_SECOND * (standardFrameLength - truncationSamples) / samplingRate;
    long pts = Math.round(timeUs);
    if (dataPending) {
      dataPending = false;
      timeUs = timeUsPending;
    } else {
      timeUs += sampleDurationUs;
    }
    output.sampleMetadata(pts, flag, frameBytes, 0, null);
    configFound = false;
    truncationSamples = 0;
    frameBytes = 0;
  }
}
