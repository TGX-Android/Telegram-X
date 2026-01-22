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

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableBitArray;
import com.google.common.math.IntMath;
import com.google.common.math.LongMath;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Utility methods for parsing MPEG-H frames, which are access units in MPEG-H bitstreams. */
/* package */ final class MpeghUtil {

  /** See ISO_IEC_23003-8;2022, 14.4.4. */
  private static final int MHAS_SYNC_WORD = 0xC001A5;

  /**
   * Returns whether the lower 3 bytes of the given integer matches an MHAS sync word. See
   * ISO_IEC_23008-3;2022, 14.4.4.
   */
  public static boolean isSyncWord(int word) {
    return (word & 0xFFFFFF) == MHAS_SYNC_WORD;
  }

  /**
   * Parses an MHAS packet header. See ISO_IEC_23008-3;2022, 14.2.1, Table 222. The reading position
   * of {@code data} will be modified to be just after the end of the MHAS packet header.
   *
   * @param data The data to parse, positioned at the start of the MHAS packet header. Must be
   *     byte-aligned.
   * @param header An instance of {@link MhasPacketHeader} that will be updated with the parsed
   *     information.
   * @return {@code true} if the parsing is successful, {@code false} otherwise.
   * @throws ParserException if an error occurred during parsing {@link MhasPacketHeader}.
   */
  public static boolean parseMhasPacketHeader(ParsableBitArray data, MhasPacketHeader header)
      throws ParserException {
    int dataStartPos = data.getBytePosition();
    header.packetType = readEscapedIntValue(data, 3, 8, 8);
    if (header.packetType == -1) {
      return false;
    }

    header.packetLabel = readEscapedLongValue(data, 2, 8, 32);
    if (header.packetLabel == -1) {
      return false;
    }

    if (header.packetLabel > 0x10) {
      throw ParserException.createForUnsupportedContainerFeature(
          "Contains sub-stream with an invalid packet label " + header.packetLabel);
    }

    if (header.packetLabel == 0) {
      switch (header.packetType) {
        case MhasPacketHeader.PACTYP_MPEGH3DACFG:
          throw ParserException.createForMalformedContainer(
              "Mpegh3daConfig packet with invalid packet label 0", /* cause= */ null);
        case MhasPacketHeader.PACTYP_AUDIOTRUNCATION:
          throw ParserException.createForMalformedContainer(
              "AudioTruncation packet with invalid packet label 0", /* cause= */ null);
        case MhasPacketHeader.PACTYP_MPEGH3DAFRAME:
          throw ParserException.createForMalformedContainer(
              "Mpegh3daFrame packet with invalid packet label 0", /* cause= */ null);
        default:
          break;
      }
    }

    header.packetLength = readEscapedIntValue(data, 11, 24, 24);
    return header.packetLength != -1;
  }

  /**
   * Obtains the output frame length of the current MPEG-H frame. See ISO_IEC_23003-3;2020, 6.1.1.1,
   * Table 75.
   *
   * @param index The coreSbrFrameLengthIndex which determines the output frame length.
   * @return The output frame length.
   * @throws ParserException if output frame length could not be obtained.
   */
  private static int getOutputFrameLength(int index) throws ParserException {
    switch (index) {
      case 0:
        return 768;
      case 1:
        return 1_024;
      case 2:
      case 3:
        return 2_048;
      case 4:
        return 4_096;
      default:
        throw ParserException.createForUnsupportedContainerFeature(
            "Unsupported coreSbrFrameLengthIndex " + index);
    }
  }

  /**
   * Obtains the sbrRatioIndex of the current MPEG-H frame. See ISO_IEC_23003-3;2020, 6.1.1.1, Table
   * 75.
   *
   * @param index The coreSbrFrameLengthIndex which determines the output frame length.
   * @return The sbrRatioIndex.
   * @throws ParserException if sbrRatioIndex could not be obtained.
   */
  private static int getSbrRatioIndex(int index) throws ParserException {
    switch (index) {
      case 0:
      case 1:
        return 0;
      case 2:
        return 2;
      case 3:
        return 3;
      case 4:
        return 1;
      default:
        throw ParserException.createForUnsupportedContainerFeature(
            "Unsupported coreSbrFrameLengthIndex " + index);
    }
  }

  /**
   * Obtains the resampling ratio according to the provided sampling frequency. See
   * ISO_IEC_23008-3;2022, 4.8.2, Table 10.
   *
   * @param usacSamplingFrequency The USAC sampling frequency.
   * @return The resampling ratio.
   * @throws ParserException if USAC sampling frequency is not supported.
   */
  private static double getResamplingRatio(int usacSamplingFrequency) throws ParserException {
    switch (usacSamplingFrequency) {
      case 96_000:
      case 88_200:
      case 48_000:
      case 44_100:
        return 1;
      case 64_000:
      case 58_800:
      case 32_000:
      case 29_400:
        return 1.5;
      case 24_000:
      case 22_050:
        return 2;
      case 16_000:
      case 14_700:
        return 3;
      default:
        throw ParserException.createForUnsupportedContainerFeature(
            "Unsupported sampling rate " + usacSamplingFrequency);
    }
  }

  /**
   * Obtains the necessary info of the Mpegh3daConfig from an MPEG-H bit stream. See
   * ISO_IEC_23008-3;2022, 5.2.2.1, Table 15. The reading position of {@code data} will be modified
   * to be just after the end of the payload of an Mpegh3daConfig packet.
   *
   * @param data The data to parse, positioned at the start of the payload of an Mpegh3daConfig
   *     packet. Must be byte-aligned.
   * @return The {@link Mpegh3daConfig}.
   * @throws ParserException if a valid {@link Mpegh3daConfig} cannot be parsed.
   */
  public static Mpegh3daConfig parseMpegh3daConfig(ParsableBitArray data) throws ParserException {
    @Nullable byte[] compatibleProfileLevelSet = null;
    int profileLevelIndication = data.readBits(8);

    int usacSamplingFrequency;
    int samplingFrequencyIndex = data.readBits(5);
    if (samplingFrequencyIndex == 0x1F) {
      usacSamplingFrequency = data.readBits(24);
    } else {
      usacSamplingFrequency = getSamplingFrequency(samplingFrequencyIndex);
    }

    int coreSbrFrameLengthIndex = data.readBits(3);
    int outputFrameLength = getOutputFrameLength(coreSbrFrameLengthIndex);
    int sbrRatioIndex = getSbrRatioIndex(coreSbrFrameLengthIndex);

    data.skipBits(2); // cfg_reserved(1), receiverDelayCompensation(1)

    skipSpeakerConfig3d(data); // referenceLayout
    int numSignals = parseSignals3d(data); // frameworkConfig3d
    skipMpegh3daDecoderConfig(data, numSignals, sbrRatioIndex); // decoderConfig

    if (data.readBit()) { // usacConfigExtensionPresent
      // Mpegh3daConfigExtension
      int numConfigExtensions = readEscapedIntValue(data, 2, 4, 8) + 1;
      for (int confExtIdx = 0; confExtIdx < numConfigExtensions; confExtIdx++) {
        int usacConfigExtType = readEscapedIntValue(data, 4, 8, 16);
        int usacConfigExtLength = readEscapedIntValue(data, 4, 8, 16);

        if (usacConfigExtType == 7 /* ID_CONFIG_EXT_COMPATIBLE_PROFILELVL_SET */) {
          int numCompatibleSets = data.readBits(4) + 1;
          data.skipBits(4); // reserved
          compatibleProfileLevelSet = new byte[numCompatibleSets];
          for (int idx = 0; idx < numCompatibleSets; idx++) {
            compatibleProfileLevelSet[idx] = (byte) data.readBits(8);
          }
        } else {
          data.skipBits(C.BITS_PER_BYTE * usacConfigExtLength);
        }
      }
    }

    // Get the resampling ratio and adjust the samplingFrequency and the standardFrameSamples
    // accordingly.
    double resamplingRatio = getResamplingRatio(usacSamplingFrequency);
    int samplingFrequency = (int) (usacSamplingFrequency * resamplingRatio);
    int standardFrameLength = (int) (outputFrameLength * resamplingRatio);

    return new Mpegh3daConfig(
        profileLevelIndication, samplingFrequency, standardFrameLength, compatibleProfileLevelSet);
  }

  /**
   * Obtains the sampling rate of the current MPEG-H frame. See ISO_IEC_23003-3;2020, 6.1.1.1, Table
   * 72.
   *
   * @param index The samplingFrequencyIndex which determines the sampling frequency.
   * @return The sampling frequency.
   * @throws ParserException if sampling frequency could not be obtained.
   */
  private static int getSamplingFrequency(int index) throws ParserException {
    switch (index) {
      case 0:
        return 96_000;
      case 1:
        return 88_200;
      case 2:
        return 64_000;
      case 3:
        return 48_000;
      case 4:
        return 44_100;
      case 5:
        return 32_000;
      case 6:
        return 24_000;
      case 7:
        return 22_050;
      case 8:
        return 16_000;
      case 9:
        return 12_000;
      case 10:
        return 11_025;
      case 11:
        return 8_000;
      case 12:
        return 7350;
      case 15:
        return 57_600;
      case 16:
        return 51_200;
      case 17:
        return 40_000;
      case 18:
        return 38_400;
      case 19:
        return 34_150;
      case 20:
        return 28_800;
      case 21:
        return 25_600;
      case 22:
        return 20_000;
      case 23:
        return 19_200;
      case 24:
        return 17_075;
      case 25:
        return 14_400;
      case 26:
        return 12_800;
      case 27:
        return 9_600;
      default:
        throw ParserException.createForUnsupportedContainerFeature(
            "Unsupported sampling rate index " + index);
    }
  }

  /**
   * Obtains the number of truncated samples of the AudioTruncationInfo from an MPEG-H bit stream.
   * See ISO_IEC_23008-3;2022, 14.2.2, Table 225. The reading position of {@code data} will be
   * modified to be just after the end of the AudioTruncation packet payload.
   *
   * @param data The data to parse, positioned at the start of the payload of an AudioTruncation
   *     packet.
   * @return The number of truncated samples.
   */
  public static int parseAudioTruncationInfo(ParsableBitArray data) {
    if (data.readBit()) { // isActive
      data.skipBits(2); // reserved(1), truncFromBegin(1)
      return data.readBits(13);
    }
    return 0;
  }

  /**
   * Skips the SpeakerConfig3d from an MPEG-H bit stream. See ISO_IEC_23008-3;2022, 5.2.2.2, Table
   * 18. The reading position of {@code data} will be modified to be just after the end of the
   * SpeakerConfig3d field.
   *
   * @param data The data to parse, positioned at the start of the SpeakerConfig3d field.
   */
  private static void skipSpeakerConfig3d(ParsableBitArray data) {
    int speakerLayoutType = data.readBits(2);
    if (speakerLayoutType == 0) {
      data.skipBits(6); // cicpSpeakerLayoutIdx
      return;
    }

    int numberOfSpeakers = readEscapedIntValue(data, 5, 8, 16) + 1;
    if (speakerLayoutType == 1) {
      data.skipBits(7 * numberOfSpeakers); // cicpSpeakerIdx per speaker
    } else if (speakerLayoutType == 2) {
      skipMpegh3daFlexibleSpeakerConfig(data, numberOfSpeakers);
    }
  }

  /**
   * Skips the mpegh3daFlexibleSpeakerConfig from an MPEG-H bit stream. See ISO_IEC_23008-3;2022,
   * 5.2.2.2, Table 19. The reading position of {@code data} will be modified to be just after the
   * end of the Mpegh3daFlexibleSpeakerConfig field.
   *
   * @param data The data to parse, positioned at the start of the Mpegh3daFlexibleSpeakerConfig
   *     field.
   */
  private static void skipMpegh3daFlexibleSpeakerConfig(
      ParsableBitArray data, int numberOfSpeakers) {
    boolean angularPrecision = data.readBit();
    int angularPrecisionDegrees = angularPrecision ? 1 : 5;
    int elevationAngleBits = angularPrecision ? 7 : 5;
    int azimuthAngleBits = angularPrecision ? 8 : 6;

    // Mpegh3daSpeakerDescription array
    for (int i = 0; i < numberOfSpeakers; i++) {
      int azimuthAngle = 0;
      if (data.readBit()) { // isCICPspeakerIdx
        data.skipBits(7); // cicpSpeakerIdx
      } else {
        int elevationClass = data.readBits(2);
        if (elevationClass == 3) {
          int elevationAngleIdx = data.readBits(elevationAngleBits);
          int elevationAngle = elevationAngleIdx * angularPrecisionDegrees;
          if (elevationAngle != 0) {
            data.skipBit(); // elevationDirection
          }
        }
        int azimuthAngleIdx = data.readBits(azimuthAngleBits);
        azimuthAngle = azimuthAngleIdx * angularPrecisionDegrees;
        if ((azimuthAngle != 0) && (azimuthAngle != 180)) {
          data.skipBit(); // azimuthDirection
        }
        data.skipBit(); // isLFE
      }

      if ((azimuthAngle != 0) && (azimuthAngle != 180)) {
        if (data.readBit()) { // alsoAddSymmetricPair
          i++;
        }
      }
    }
  }

  /**
   * Obtains the necessary info of Signals3d from an MPEG-H bit stream. See ISO_IEC_23008-3;2022,
   * 5.2.2.1, Table 17. The reading position of {@code data} will be modified to be just after the
   * end of the Signals3d field.
   *
   * @param data The data to parse, positioned at the start of the Signals3d field.
   * @return The number of overall signals in the bit stream.
   */
  private static int parseSignals3d(ParsableBitArray data) {
    int numberOfSignals = 0;
    int numberOfSignalGroupsInBitstream = data.readBits(5);

    for (int grp = 0; grp < numberOfSignalGroupsInBitstream + 1; grp++) {
      int signalGroupType = data.readBits(3);
      int bsNumberOfSignals = readEscapedIntValue(data, 5, 8, 16);

      numberOfSignals += bsNumberOfSignals + 1;
      if (signalGroupType == 0 /*SignalGroupTypeChannels*/
          || signalGroupType == 2 /*SignalGroupTypeSAOC*/) {
        if (data.readBit()) { // differsFromReferenceLayout OR saocDmxLayoutPresent
          skipSpeakerConfig3d(data); // audioChannelLayout[grp] OR saocDmxChannelLayout
        }
      }
    }
    return numberOfSignals;
  }

  /**
   * Skips the Mpegh3daDecoderConfig from an MPEG-H bit stream. See ISO_IEC_23008-3;2022, 5.2.2.3,
   * Table 21. The reading position of {@code data} will be modified to be just after the end of the
   * Mpegh3daDecoderConfig field.
   *
   * @param data The data to parse, positioned at the start of the Mpegh3daDecoderConfig field.
   * @param numSignals The number of overall signals.
   * @param sbrRatioIndex The SBR ration index.
   */
  private static void skipMpegh3daDecoderConfig(
      ParsableBitArray data, int numSignals, int sbrRatioIndex) {

    int numElements = readEscapedIntValue(data, 4, 8, 16) + 1;
    data.skipBit(); // elementLengthPresent

    for (int elemIdx = 0; elemIdx < numElements; elemIdx++) {
      int usacElementType = data.readBits(2);

      switch (usacElementType) {
        case 0 /*ID_USAC_SCE*/:
          parseMpegh3daCoreConfig(data); // coreConfig
          if (sbrRatioIndex > 0) {
            skipSbrConfig(data); // sbrConfig
          }
          break;
        case 1 /*ID_USAC_CPE*/:
          boolean enhancedNoiseFilling = parseMpegh3daCoreConfig(data); // coreConfig
          if (enhancedNoiseFilling) {
            data.skipBit(); // igfIndependentTiling
          }
          int stereoConfigIndex = 0;
          if (sbrRatioIndex > 0) {
            skipSbrConfig(data); // sbrConfig
            stereoConfigIndex = data.readBits(2);
          }
          if (stereoConfigIndex > 0) {
            // mps212Config
            data.skipBits(6); // bsFreqRes(3), bsFixedGainDMX(3),
            int bsTempShapeConfig = data.readBits(2);
            data.skipBits(4); // bsDecorrConfig(2), bsHighRateMode(1), bsPhaseCoding(1)
            if (data.readBit()) { // bsOttBandsPhasePresent
              data.skipBits(5); // bsOttBandsPhase
            }
            if (stereoConfigIndex == 2 || stereoConfigIndex == 3) {
              data.skipBits(6); // bsResidualBands(5), bsPseudoLr(1)
            }
            if (bsTempShapeConfig == 2) {
              data.skipBit(); // bsEnvQuantMode
            }
          }

          int nBits = (int) Math.floor(Math.log(numSignals - 1) / Math.log(2.0)) + 1;
          int qceIndex = data.readBits(2);
          if (qceIndex > 0) {
            if (data.readBit()) { // shiftIndex0
              data.skipBits(nBits); // shiftChannel0
            }
          }
          if (data.readBit()) { // shiftIndex1
            data.skipBits(nBits); // shiftChannel1
          }
          if (sbrRatioIndex == 0 && qceIndex == 0) {
            data.skipBit(); // lpdStereoIndex
          }
          break;
        case 3 /*ID_USAC_EXT*/:
          readEscapedIntValue(data, 4, 8, 16); // usacExtElementType
          int usacExtElementConfigLength = readEscapedIntValue(data, 4, 8, 16);

          if (data.readBit()) { // usacExtElementDefaultLengthPresent
            readEscapedIntValue(data, 8, 16, 0) /* +1 */; // usacExtElementDefaultLength
          }
          data.skipBit(); // usacExtElementPayloadFrag

          if (usacExtElementConfigLength > 0) {
            data.skipBits(8 * usacExtElementConfigLength);
          }
          break;
        default:
          break;
      }
    }
  }

  /**
   * Obtains the necessary info of the Mpegh3daCoreConfig from an MPEG-H bit stream. See
   * ISO_IEC_23008-3;2022, 5.2.2.3, Table 24. The reading position of {@code data} will be modified
   * to be just after the end of the Mpegh3daCoreConfig field.
   *
   * @param data The data to parse, positioned at the start of the Mpegh3daCoreConfig field.
   * @return The enhanced noise filling flag.
   */
  private static boolean parseMpegh3daCoreConfig(ParsableBitArray data) {
    data.skipBits(3); // tw_mdct(1), fullbandLpd(1), noiseFilling(1)
    boolean enhancedNoiseFilling = data.readBit();
    if (enhancedNoiseFilling) {
      // igfUseEnf(1), igfUseHighRes(1), igfUseWhitening(1), igfAfterTnsSynth(1), igfStartIndex(5),
      // igfStopIndex(4)
      data.skipBits(13);
    }
    return enhancedNoiseFilling;
  }

  /**
   * Skips the SbrConfig from an MPEG-H bit stream. See ISO_IEC_23003-3;2020, 5.2, Table 14. The
   * reading position of {@code data} will be modified.
   *
   * @param data The data to parse, positioned at the start of the SbrConfig field.
   */
  private static void skipSbrConfig(ParsableBitArray data) {
    data.skipBits(3); // harmonicSBR(1), bs_interTes(1), bs_pvc(1)
    data.skipBits(8); // dflt_start_freq(4), dflt_stop_freq(4)
    boolean dfltHeaderExtra1 = data.readBit();
    boolean dfltHeaderExtra2 = data.readBit();
    if (dfltHeaderExtra1) {
      data.skipBits(5); // dflt_freq_scale(2), dflt_alter_scale(1), dflt_noise_bands(2)
    }
    if (dfltHeaderExtra2) {
      // dflt_limiter_bands(2), dflt_limiter_gains(2), dflt_interpol_freq(1), dflt_smoothing_mode(1)
      data.skipBits(6);
    }
  }

  /**
   * Obtains an escaped value (up to {@link Integer#MAX_VALUE}) from an MPEG-H bit stream.
   *
   * <p>See ISO_IEC_23003-3;2020, 5.2, Table 19.
   *
   * <p>The reading position of {@code data} will be modified to be just after the end of the
   * escaped value.
   *
   * @param data The data to parse, positioned at the start of the escaped value.
   * @param bits1 number of bits to be parsed.
   * @param bits2 number of bits to be parsed.
   * @param bits3 number of bits to be parsed.
   * @return The escaped integer value or -1 if end of the {@code data} is reached before fully
   *     reading the value.
   * @throws IllegalArgumentException if {@code bits1}, {@code bits2} and {@code bits3} could result
   *     in reading a value greater than {@link Integer#MAX_VALUE}.
   */
  private static int readEscapedIntValue(ParsableBitArray data, int bits1, int bits2, int bits3) {
    // Ensure that the calculated value will fit within the range of a Java {@code int}.
    int maxBitCount = Math.max(Math.max(bits1, bits2), bits3);
    checkArgument(maxBitCount <= Integer.SIZE - 1);
    // Result is intentionally unused, checking if the operation causes overflow
    int unused =
        IntMath.checkedAdd(IntMath.checkedAdd((1 << bits1) - 1, (1 << bits2) - 1), (1 << bits3));

    if (data.bitsLeft() < bits1) {
      return -1;
    }

    int value = data.readBits(bits1);
    if (value == (1 << bits1) - 1) {
      if (data.bitsLeft() < bits2) {
        return -1;
      }
      int valueAdd = data.readBits(bits2);
      value += valueAdd;

      if (valueAdd == (1 << bits2) - 1) {
        if (data.bitsLeft() < bits3) {
          return -1;
        }
        valueAdd = data.readBits(bits3);
        value += valueAdd;
      }
    }
    return value;
  }

  /**
   * Obtains an escaped value (up to {@link Long#MAX_VALUE}) from an MPEG-H bit stream.
   *
   * <p>See ISO_IEC_23003-3;2020, 5.2, Table 19.
   *
   * <p>The reading position of {@code data} will be modified to be just after the end of the
   * escaped value.
   *
   * @param data The data to parse, positioned at the start of the escaped value.
   * @param bits1 number of bits to be parsed.
   * @param bits2 number of bits to be parsed.
   * @param bits3 number of bits to be parsed.
   * @return The escaped long value or -1 if end of the {@code data} is reached before fully reading
   *     the value.
   * @throws IllegalArgumentException if {@code bits1}, {@code bits2} and {@code bits3} could result
   *     in reading a value greater than {@link Long#MAX_VALUE}.
   */
  private static long readEscapedLongValue(ParsableBitArray data, int bits1, int bits2, int bits3) {
    // Ensure that the calculated value will fit within the range of a Java {@code long}.
    int maxBitCount = Math.max(Math.max(bits1, bits2), bits3);
    checkArgument(maxBitCount <= Long.SIZE - 1);
    // Result is intentionally unused, checking if the operation causes overflow
    long unused =
        LongMath.checkedAdd(
            LongMath.checkedAdd((1L << bits1) - 1, (1L << bits2) - 1), (1L << bits3));

    if (data.bitsLeft() < bits1) {
      return -1;
    }

    long value = data.readBitsToLong(bits1);
    if (value == (1L << bits1) - 1) {
      if (data.bitsLeft() < bits2) {
        return -1;
      }

      long valueAdd = data.readBitsToLong(bits2);
      value += valueAdd;

      if (valueAdd == (1L << bits2) - 1) {
        if (data.bitsLeft() < bits3) {
          return -1;
        }

        valueAdd = data.readBitsToLong(bits3);
        value += valueAdd;
      }
    }
    return value;
  }

  private MpeghUtil() {}

  /**
   * Represents the header of an MHAS (MPEG-H 3D Audio System) packet. This header provides
   * essential information to identify and parse the packet's contents.
   */
  public static class MhasPacketHeader {

    /** MHAS packet types. See ISO_IEC_23008-3;2022, 14.4. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({
      PACTYP_FILLDATA,
      PACTYP_MPEGH3DACFG,
      PACTYP_MPEGH3DAFRAME,
      PACTYP_AUDIOSCENEINFO,
      PACTYP_SYNC,
      PACTYP_SYNCGAP,
      PACTYP_MARKER,
      PACTYP_CRC16,
      PACTYP_CRC32,
      PACTYP_DESCRIPTOR,
      PACTYP_USERINTERACTION,
      PACTYP_LOUDNESS_DRC,
      PACTYP_BUFFERINFO,
      PACTYP_GLOBAL_CRC16,
      PACTYP_GLOBAL_CRC32,
      PACTYP_AUDIOTRUNCATION,
      PACTYP_GENDATA,
      PACTYPE_EARCON,
      PACTYPE_PCMCONFIG,
      PACTYPE_PCMDATA,
      PACTYP_LOUDNESS
    })
    public @interface Type {}

    public static final int PACTYP_FILLDATA = 0;
    public static final int PACTYP_MPEGH3DACFG = 1;
    public static final int PACTYP_MPEGH3DAFRAME = 2;
    public static final int PACTYP_AUDIOSCENEINFO = 3;
    public static final int PACTYP_SYNC = 6;
    public static final int PACTYP_SYNCGAP = 7;
    public static final int PACTYP_MARKER = 8;
    public static final int PACTYP_CRC16 = 9;
    public static final int PACTYP_CRC32 = 10;
    public static final int PACTYP_DESCRIPTOR = 11;
    public static final int PACTYP_USERINTERACTION = 12;
    public static final int PACTYP_LOUDNESS_DRC = 13;
    public static final int PACTYP_BUFFERINFO = 14;
    public static final int PACTYP_GLOBAL_CRC16 = 15;
    public static final int PACTYP_GLOBAL_CRC32 = 16;
    public static final int PACTYP_AUDIOTRUNCATION = 17;
    public static final int PACTYP_GENDATA = 18;
    public static final int PACTYPE_EARCON = 19;
    public static final int PACTYPE_PCMCONFIG = 20;
    public static final int PACTYPE_PCMDATA = 21;
    public static final int PACTYP_LOUDNESS = 22;

    /** The payload type in the actual packet. */
    public @Type int packetType;

    /** A label indicating which packets belong together. */
    public long packetLabel;

    /** The length of MHAS packet payload in bytes. */
    public int packetLength;
  }

  /** Represents an MPEG-H 3D audio configuration. */
  public static class Mpegh3daConfig {

    /** The MPEG-H 3D audio profile and level indication. */
    public final int profileLevelIndication;

    /** The sampling frequency of the MPEG-H 3D Audio stream. */
    public final int samplingFrequency;

    /** The standard frame length in samples. */
    public final int standardFrameLength;

    /**
     * A list of MPEG-H 3D audio profile-level set that are compatible with the current
     * configuration.
     */
    @Nullable public final byte[] compatibleProfileLevelSet;

    private Mpegh3daConfig(
        int profileLevelIndication,
        int samplingFrequency,
        int standardFrameLength,
        @Nullable byte[] compatibleProfileLevelSet) {
      this.profileLevelIndication = profileLevelIndication;
      this.samplingFrequency = samplingFrequency;
      this.standardFrameLength = standardFrameLength;
      this.compatibleProfileLevelSet = compatibleProfileLevelSet;
    }
  }
}
