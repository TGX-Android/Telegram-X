/*
 * Copyright (C) 2019 The Android Open Source Project
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
package androidx.media3.extractor;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;

/** Utility methods for parsing AC-4 frames, which are access units in AC-4 bitstreams. */
@UnstableApi
public final class Ac4Util {

  /** Holds sample format information as presented by a syncframe header. */
  public static final class SyncFrameInfo {

    /** The bitstream version. */
    public final int bitstreamVersion;

    /** The audio sampling rate in Hz. */
    public final int sampleRate;

    /** The number of audio channels */
    public final int channelCount;

    /** The size of the frame. */
    public final int frameSize;

    /** Number of audio samples in the frame. */
    public final int sampleCount;

    private SyncFrameInfo(
        int bitstreamVersion, int channelCount, int sampleRate, int frameSize, int sampleCount) {
      this.bitstreamVersion = bitstreamVersion;
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.frameSize = frameSize;
      this.sampleCount = sampleCount;
    }
  }

  /**
   * Types of channel modes for AC-4 audio as per ETSI TS 103 190-2 V1.1.1 (2015-09), Section
   * 6.3.2.7.2, Table 79.
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    CHANNEL_MODE_UNKNOWN,
    CHANNEL_MODE_MONO,
    CHANNEL_MODE_STEREO,
    CHANNEL_MODE_3_0,
    CHANNEL_MODE_5_0,
    CHANNEL_MODE_5_1,
    CHANNEL_MODE_7_0_34,
    CHANNEL_MODE_7_1_34,
    CHANNEL_MODE_7_0_52,
    CHANNEL_MODE_7_1_52,
    CHANNEL_MODE_7_0_322,
    CHANNEL_MODE_7_1_322,
    CHANNEL_MODE_7_0_4,
    CHANNEL_MODE_7_1_4,
    CHANNEL_MODE_9_0_4,
    CHANNEL_MODE_9_1_4,
    CHANNEL_MODE_22_2
  })
  private @interface ChannelMode {}

  private static final int CHANNEL_MODE_UNKNOWN = -1;
  private static final int CHANNEL_MODE_MONO = 0;
  private static final int CHANNEL_MODE_STEREO = 1;
  private static final int CHANNEL_MODE_3_0 = 2;
  private static final int CHANNEL_MODE_5_0 = 3;
  private static final int CHANNEL_MODE_5_1 = 4;
  private static final int CHANNEL_MODE_7_0_34 = 5;
  private static final int CHANNEL_MODE_7_1_34 = 6;
  private static final int CHANNEL_MODE_7_0_52 = 7;
  private static final int CHANNEL_MODE_7_1_52 = 8;
  private static final int CHANNEL_MODE_7_0_322 = 9;
  private static final int CHANNEL_MODE_7_1_322 = 10;
  private static final int CHANNEL_MODE_7_0_4 = 11;
  private static final int CHANNEL_MODE_7_1_4 = 12;
  private static final int CHANNEL_MODE_9_0_4 = 13;
  private static final int CHANNEL_MODE_9_1_4 = 14;
  private static final int CHANNEL_MODE_22_2 = 15;

  public static final int AC40_SYNCWORD = 0xAC40;
  public static final int AC41_SYNCWORD = 0xAC41;

  /** Maximum rate for an AC-4 audio stream, in bytes per second. */
  public static final int MAX_RATE_BYTES_PER_SECOND = 2688 * 1000 / 8;

  /** The channel count of AC-4 stream. */
  // TODO: Parse AC-4 stream channel count.
  private static final int CHANNEL_COUNT_2 = 2;

  /**
   * The AC-4 sync frame header size for extractor. The seven bytes are 0xAC, 0x40, 0xFF, 0xFF,
   * sizeByte1, sizeByte2, sizeByte3. See ETSI TS 103 190-1 V1.3.1, Annex G
   */
  public static final int SAMPLE_HEADER_SIZE = 7;

  /**
   * The header size for AC-4 parser. Only needs to be as big as we need to read, not the full
   * header size.
   */
  public static final int HEADER_SIZE_FOR_PARSER = 16;

  /**
   * Number of audio samples in the frame. Defined in IEC61937-14:2017 table 5 and 6. This table
   * provides the number of samples per frame at the playback sampling frequency of 48 kHz. For 44.1
   * kHz, only frame_rate_index(13) is valid and corresponding sample count is 2048.
   */
  private static final int[] SAMPLE_COUNT =
      new int[] {
        /* [ 0]  23.976 fps */ 2002,
        /* [ 1]  24     fps */ 2000,
        /* [ 2]  25     fps */ 1920,
        /* [ 3]  29.97  fps */ 1601, // 1601 | 1602 | 1601 | 1602 | 1602
        /* [ 4]  30     fps */ 1600,
        /* [ 5]  47.95  fps */ 1001,
        /* [ 6]  48     fps */ 1000,
        /* [ 7]  50     fps */ 960,
        /* [ 8]  59.94  fps */ 800, //  800 |  801 |  801 |  801 |  801
        /* [ 9]  60     fps */ 800,
        /* [10] 100     fps */ 480,
        /* [11] 119.88  fps */ 400, //  400 |  400 |  401 |  400 |  401
        /* [12] 120     fps */ 400,
        /* [13]  23.438 fps */ 2048
      };

  /**
   * Returns the AC-4 format given {@code data} containing the AC4SpecificBox according to ETSI TS
   * 103 190-1 Annex E.4 (ac4_dsi) and TS 103 190-2 section E.6 (ac4_dsi_v1). The reading position
   * of {@code data} will be modified.
   *
   * @param data The AC4SpecificBox to parse.
   * @param trackId The track identifier to set on the format.
   * @param language The language to set on the format, or {@code null} if unset.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The AC-4 format parsed from data in the header.
   * @throws ParserException If an unsupported container feature is encountered while parsing AC-4
   *     Annex E.
   */
  public static Format parseAc4AnnexEFormat(
      ParsableByteArray data,
      String trackId,
      @Nullable String language,
      @Nullable DrmInitData drmInitData)
      throws ParserException {
    ParsableBitArray dataBitArray = new ParsableBitArray();
    dataBitArray.reset(data);

    int dsiSize = dataBitArray.bitsLeft();
    int ac4DsiVersion = dataBitArray.readBits(3); // ac4_dsi_version
    if (ac4DsiVersion > 1) {
      throw ParserException.createForUnsupportedContainerFeature(
          "Unsupported AC-4 DSI version: " + ac4DsiVersion);
    }

    int bitstreamVersion = dataBitArray.readBits(7); // bitstream_version
    int sampleRate = dataBitArray.readBit() ? 48000 : 44100; // fs_index
    dataBitArray.skipBits(4); // frame_rate_index
    int numberOfPresentations = dataBitArray.readBits(9); // n_presentations

    if (bitstreamVersion > 1) {
      if (ac4DsiVersion == 0) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Invalid AC-4 DSI version: " + ac4DsiVersion);
      }
      if (dataBitArray.readBit()) { // b_program_id
        dataBitArray.skipBits(16); // short_program_id
        if (dataBitArray.readBit()) { // b_uuid
          dataBitArray.skipBits(16 * 8); // program_uuid
        }
      }
    }

    if (ac4DsiVersion == 1) {
      if (!skipDsiBitrate(dataBitArray)) {
        throw ParserException.createForUnsupportedContainerFeature("Invalid AC-4 DSI bitrate.");
      }
      dataBitArray.byteAlign();
    }

    Ac4Presentation ac4Presentation = new Ac4Presentation();
    for (int presentationIdx = 0; presentationIdx < numberOfPresentations; presentationIdx++) {
      boolean isSingleSubstream = false;
      boolean isSingleSubstreamGroup = false;
      int presentationConfig;
      int presentationVersion;
      int presentationBytes = 0;
      int start = 0;

      if (ac4DsiVersion == 0) {
        isSingleSubstream = dataBitArray.readBit(); // b_single_substream
        presentationConfig = dataBitArray.readBits(5); // presentation_config
        presentationVersion = dataBitArray.readBits(5); // presentation_version
      } else {
        presentationVersion = dataBitArray.readBits(8); // presentation_version
        presentationBytes = dataBitArray.readBits(8); // pres_bytes
        if (presentationBytes == 0xff) {
          presentationBytes += dataBitArray.readBits(16); // pres_bytes
        }
        if (presentationVersion > 2) {
          dataBitArray.skipBits(presentationBytes * 8);
          continue;
        }
        // record a marker, less the size of the presentation_config
        start = (dsiSize - dataBitArray.bitsLeft()) / 8;
        // ac4_presentation_v0_dsi(), ac4_presentation_v1_dsi() and ac4_presentation_v2_dsi()
        // all start with a presentation_config of 5 bits
        presentationConfig = dataBitArray.readBits(5); // presentation_config
        isSingleSubstreamGroup = (presentationConfig == 0x1f);
      }

      boolean addEmdfSubstreams;
      if (!(isSingleSubstream || isSingleSubstreamGroup) && presentationConfig == 6) {
        addEmdfSubstreams = true;
      } else {
        ac4Presentation.level = dataBitArray.readBits(3); // mdcompat

        if (dataBitArray.readBit()) { // b_presentation_group_index
          dataBitArray.skipBits(5); // group_index
        }

        dataBitArray.skipBits(2); // dsi_frame_rate_multiply_info
        if (ac4DsiVersion == 1 && (presentationVersion == 1 || presentationVersion == 2)) {
          dataBitArray.skipBits(2); // dsi_frame_rate_fraction_info
        }
        dataBitArray.skipBits(5); // presentation_emdf_version
        dataBitArray.skipBits(10); // presentation_key_id

        if (ac4DsiVersion == 1) {
          if (presentationVersion > 0) {
            ac4Presentation.isChannelCoded = dataBitArray.readBit(); // b_presentation_channel_coded
          }

          if (ac4Presentation.isChannelCoded) {
            if (presentationVersion == 1 || presentationVersion == 2) {
              int channelMode = dataBitArray.readBits(5); // dsi_presentation_ch_mode
              if (channelMode >= 0 && channelMode <= 15) {
                ac4Presentation.channelMode = channelMode;
              }

              if (channelMode >= 11 && channelMode <= 14) {
                ac4Presentation.hasBackChannels =
                    dataBitArray.readBit(); // pres_b_4_back_channels_present
                ac4Presentation.topChannelPairs =
                    dataBitArray.readBits(2); // pres_top_channel_pairs
              }
            }
            // presentation_channel_mask in ac4_presentation_v0_dsi()
            dataBitArray.skipBits(24); // presentation_channel_mask_v1
          }

          if (presentationVersion == 1 || presentationVersion == 2) {
            if (dataBitArray.readBit()) { // b_presentation_core_differs
              if (dataBitArray.readBit()) { // b_presentation_core_channel_coded
                dataBitArray.skipBits(2); // dsi_presentation_channel_mode_core
              }
            }
            if (dataBitArray.readBit()) { // b_presentation_filter
              // Ignore b_enable_presentation field since this flag occurs in AC-4 elementary stream
              // TOC and AC-4 decoder doesn't handle it either.
              dataBitArray.skipBit(); // b_enable_presentation
              int filterBytes = dataBitArray.readBits(8); // n_filter_bytes
              for (int i = 0; i < filterBytes; i++) {
                dataBitArray.skipBits(8); // filter_data
              }
            }
          }
        }

        if (isSingleSubstream || isSingleSubstreamGroup) {
          if (presentationVersion == 0) {
            parseDsiSubstream(dataBitArray, ac4Presentation);
          } else {
            parseDsiSubstreamGroup(dataBitArray, ac4Presentation);
          }
        } else {
          // b_hsf_ext for ac4DsiVersion 0 OR b_multi_pid for ac4DsiVersion 1
          dataBitArray.skipBit();
          switch (presentationConfig) {
            case 0:
            case 1:
            case 2:
              if (presentationVersion == 0) {
                for (int substreamId = 0; substreamId < 2; substreamId++) {
                  parseDsiSubstream(dataBitArray, ac4Presentation);
                }
              } else {
                for (int substreamGroupId = 0; substreamGroupId < 2; substreamGroupId++) {
                  parseDsiSubstreamGroup(dataBitArray, ac4Presentation);
                }
              }
              break;
            case 3:
            case 4:
              if (presentationVersion == 0) {
                for (int substreamId = 0; substreamId < 3; substreamId++) {
                  parseDsiSubstream(dataBitArray, ac4Presentation);
                }
              } else {
                for (int substreamGroupId = 0; substreamGroupId < 3; substreamGroupId++) {
                  parseDsiSubstreamGroup(dataBitArray, ac4Presentation);
                }
              }
              break;
            case 5:
              if (presentationVersion == 0) {
                parseDsiSubstream(dataBitArray, ac4Presentation);
              } else {
                int nSubstreamGroupsMinus2 = dataBitArray.readBits(3);
                for (int substreamGroupId = 0;
                    substreamGroupId < nSubstreamGroupsMinus2 + 2;
                    substreamGroupId++) {
                  parseDsiSubstreamGroup(dataBitArray, ac4Presentation);
                }
              }
              break;
            default:
              int nSkipBytes = dataBitArray.readBits(7); // n_skip_bytes
              for (int j = 0; j < nSkipBytes; j++) {
                dataBitArray.skipBits(8);
              }
              break;
          }
        }
        dataBitArray.skipBit(); // b_pre_virtualized
        addEmdfSubstreams = dataBitArray.readBit(); // b_add_emdf_substreams
      }
      if (addEmdfSubstreams) {
        int nAddEmdfSubstreams = dataBitArray.readBits(7); // n_add_emdf_substreams
        for (int j = 0; j < nAddEmdfSubstreams; j++) {
          dataBitArray.skipBits(5 + 10); // substream_emdf_version and substream_key_id
        }
      }

      if (presentationVersion > 0) {
        if (dataBitArray.readBit()) { // b_presentation_bitrate_info
          if (!skipDsiBitrate(dataBitArray)) {
            throw ParserException.createForUnsupportedContainerFeature("Can't parse bitrate DSI.");
          }
        }

        if (dataBitArray.readBit()) { // b_alternative
          dataBitArray.byteAlign();
          int nameLen = dataBitArray.readBits(16); // name_len
          dataBitArray.skipBytes(nameLen); // presentation_name

          int nTargets = dataBitArray.readBits(5); // n_targets
          for (int i = 0; i < nTargets; i++) {
            dataBitArray.skipBits(3); // target_md_compat
            dataBitArray.skipBits(8); // target_device_category
          }
        }
      }

      dataBitArray.byteAlign();

      if (ac4DsiVersion == 1) {
        int end = (dsiSize - dataBitArray.bitsLeft()) / 8;
        int presentationBytesRead = end - start;
        if (presentationBytes < presentationBytesRead) {
          throw ParserException.createForUnsupportedContainerFeature(
              "pres_bytes is smaller than presentation bytes read.");
        }
        int skipBytes = presentationBytes - presentationBytesRead;
        dataBitArray.skipBytes(skipBytes);
      }
      // We should know this or something is probably wrong with the bitstream (or we don't support
      // it)
      if (ac4Presentation.isChannelCoded && ac4Presentation.channelMode == CHANNEL_MODE_UNKNOWN) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Can't determine channel mode of presentation " + presentationIdx);
      }
      break; // Successfully parsed the first presentation with presentation version 0, 1 or 2.
    }

    int channelCount;
    if (ac4Presentation.isChannelCoded) {
      channelCount =
          getAdjustedChannelCount(
              ac4Presentation.channelMode,
              ac4Presentation.hasBackChannels,
              ac4Presentation.topChannelPairs);
    } else {
      // The ETSI TS 103 190-2 V1.2.1 (2018-02) specification defines the parameter
      // n_umx_objects_minus1 in Annex E (E.11.11) to specify the number of fullband objects. While
      // the elementary stream specification (section 6.3.2.8.1 and 6.3.2.10.4) provides information
      // about the presence of an LFE channel within the set of dynamic objects, this detail is not
      // explicitly stated in the ISO Base Media File Format (Annex E). However, current
      // implementation practices consistently include the LFE channel when creating an object-based
      // substream. As a result, it has been decided that when interpreting the ISO Base Media File
      // Format, the LFE channel should always be counted as part of the total channel count.
      int lfeChannelCount = 1;
      channelCount = ac4Presentation.numOfUmxObjects + lfeChannelCount;
      // TODO: There is a bug in ETSI TS 103 190-2 V1.2.1 (2018-02), E.11.11
      // For AC-4 level 4 stream, the intention is to set 19 to n_umx_objects_minus1 but it is
      // equal to 15 based on current specification. Dolby has filed a bug report to ETSI.
      // The following sentence should be deleted after ETSI specification error is fixed.
      if (ac4Presentation.level == 4) {
        channelCount = channelCount == 17 ? 21 : channelCount;
      }
    }

    if (channelCount <= 0) {
      throw ParserException.createForUnsupportedContainerFeature(
          "Can't determine channel count of presentation.");
    }

    return new Format.Builder()
        .setId(trackId)
        .setSampleMimeType(MimeTypes.AUDIO_AC4)
        .setChannelCount(channelCount)
        .setSampleRate(sampleRate)
        .setDrmInitData(drmInitData)
        .setLanguage(language)
        .build();
  }

  /**
   * Parses the AC-4 DSI substream according to TS 103 190-1 v1.2.1 section E.5 and TS 103 190-2
   * v1.1.1 section E.9. Modifies the reading position of {@code data} to be just after the AC-4 DSI
   * substream field.
   *
   * @param data The {@link ParsableBitArray} containing the AC-4 DSI, positioned at the start of
   *     the substream field.
   * @param ac4Presentation A structure to store parsed AC-4 presentation information.
   * @throws ParserException If an unsupported container feature is encountered while parsing the
   *     AC-4 DSI substream.
   */
  private static void parseDsiSubstream(ParsableBitArray data, Ac4Presentation ac4Presentation)
      throws ParserException {
    int channelMode = data.readBits(5); // channel_mode
    data.skipBits(2); // dsi_sf_multiplier

    if (data.readBit()) { // b_bitrate_indicator
      data.skipBits(5); // b_bitrate_indicator
    }
    if (channelMode >= 7 && channelMode <= 10) {
      data.skipBit(); // add_ch_base
    }

    if (data.readBit()) { // b_content_type
      int contentClassifier = data.readBits(3); // content_classifier
      // For streams based on TS 103 190 part 1 the presentation level channel_mode doesn't exist
      // and so we use the channel_mode from either the CM or M&E substream (they are mutually
      // exclusive).
      if (ac4Presentation.channelMode == CHANNEL_MODE_UNKNOWN
          && (channelMode >= 0 && channelMode <= 15)
          && (contentClassifier == 0 || contentClassifier == 1)) {
        ac4Presentation.channelMode = channelMode;
      }

      if (data.readBit()) { // b_language_indicator
        skipDsiLanguage(data);
      }
    }
  }

  /**
   * Parses the AC-4 DSI substream group information according to ETSI TS 103 190-2 v1.1.1 section
   * E.11. Modifies the reading position of {@code data} to be just after the AC-4 DSI substream
   * group field.
   *
   * @param data The {@link ParsableBitArray} containing the AC-4 DSI, positioned at the start of
   *     the substream group field.
   * @param ac4Presentation A structure to store parsed AC-4 presentation information.
   * @throws ParserException If an unsupported container feature is encountered while parsing the
   *     AC-4 DSI substream group.
   */
  private static void parseDsiSubstreamGroup(ParsableBitArray data, Ac4Presentation ac4Presentation)
      throws ParserException {
    data.skipBits(2); // b_substreams_present(1), b_hsf_ext(1)
    boolean channelCoded = data.readBit(); // b_channel_coded
    int numberOfSubstreams = data.readBits(8); // n_substreams

    for (int i = 0; i < numberOfSubstreams; i++) {
      data.skipBits(2); // dsi_sf_multiplier
      if (data.readBit()) { // b_substream_bitrate_indicator
        data.skipBits(5); // substream_bitrate_indicator
      }
      if (channelCoded) {
        data.skipBits(24); // dsi_substream_channel_mask
      } else {
        if (data.readBit()) { // b_ajoc
          if (!data.readBit()) { // b_static_dmx
            data.skipBits(4); // n_dmx_objects_minus1
          }
          ac4Presentation.numOfUmxObjects = data.readBits(6) + 1; // n_umx_objects_minus1
        }
        data.skipBits(4); // objects_assignment_mask
      }
    }

    if (data.readBit()) { // b_content_type
      data.skipBits(3); // content_classifier

      if (data.readBit()) { // b_language_indicator
        skipDsiLanguage(data);
      }
    }
  }

  /**
   * Skips the language information fields in an AC-4 DSI bit stream according to TS 103 190-1
   * section 4.3.3.8.7. Modifies the reading position of {@code data} to be just after the language
   * fields.
   *
   * @param data The {@link ParsableBitArray} containing the AC-4 DSI, positioned at the start of
   *     the language tag field.
   * @throws ParserException If the language tag length is invalid.
   */
  private static void skipDsiLanguage(ParsableBitArray data) throws ParserException {
    int languageTagBytesNumber = data.readBits(6); // n_language_tag_bytes
    if (languageTagBytesNumber < 2 || languageTagBytesNumber > 42) {
      throw ParserException.createForUnsupportedContainerFeature(
          String.format(
              "Invalid language tag bytes number: %d. Must be between 2 and 42.",
              languageTagBytesNumber));
    }
    // Can't use readBytes() since it is not byte-aligned here.
    data.skipBits(languageTagBytesNumber * C.BITS_PER_BYTE);
  }

  /**
   * Skips the bitrate information fields in an AC-4 DSI bit stream. The reading position of {@code
   * data} will be modified to be just after the bitrate fields, if sufficient bits remain.
   *
   * @param data The {@link ParsableBitArray} containing the AC-4 DSI, positioned at the start of
   *     the bitrate information.
   * @return {@code true} if the bitrate fields were successfully skipped or {@code false} if there
   *     were insufficient bits remaining in {@code data}.
   */
  private static boolean skipDsiBitrate(ParsableBitArray data) {
    int totalBitsToSkip = 2 + 32 + 32; // bit_rate_mode, bit_rate, bit_rate_precision
    if (data.bitsLeft() < totalBitsToSkip) {
      return false;
    }
    data.skipBits(totalBitsToSkip);
    return true;
  }

  private static int getAdjustedChannelCount(
      @ChannelMode int channelMode, boolean hasBackChannels, int topChannelPairs) {
    int channelCount = getChannelCountFromChannelMode(channelMode);
    if (channelMode == CHANNEL_MODE_7_0_4
        || channelMode == CHANNEL_MODE_7_1_4
        || channelMode == CHANNEL_MODE_9_0_4
        || channelMode == CHANNEL_MODE_9_1_4) {

      if (!hasBackChannels) {
        channelCount -= 2;
      }

      switch (topChannelPairs) {
        case 0:
          channelCount -= 4;
          break;
        case 1:
          channelCount -= 2;
          break;
        default:
          break;
      }
    }
    return channelCount;
  }

  private static int getChannelCountFromChannelMode(@ChannelMode int channelMode) {
    switch (channelMode) {
      case CHANNEL_MODE_MONO:
        return 1;
      case CHANNEL_MODE_STEREO:
        return 2;
      case CHANNEL_MODE_3_0:
        return 3;
      case CHANNEL_MODE_5_0:
        return 5;
      case CHANNEL_MODE_5_1:
        return 6;
      case CHANNEL_MODE_7_0_34:
      case CHANNEL_MODE_7_0_52:
      case CHANNEL_MODE_7_0_322:
        return 7;
      case CHANNEL_MODE_7_1_34:
      case CHANNEL_MODE_7_1_52:
      case CHANNEL_MODE_7_1_322:
        return 8;
      case CHANNEL_MODE_7_0_4:
        return 11;
      case CHANNEL_MODE_7_1_4:
        return 12;
      case CHANNEL_MODE_9_0_4:
        return 13;
      case CHANNEL_MODE_9_1_4:
        return 14;
      case CHANNEL_MODE_22_2:
        return 24;
      default:
        return -1;
    }
  }

  /**
   * Returns AC-4 format information given {@code data} containing a syncframe. The reading position
   * of {@code data} will be modified.
   *
   * @param data The data to parse, positioned at the start of the syncframe.
   * @return The AC-4 format data parsed from the header.
   */
  public static SyncFrameInfo parseAc4SyncframeInfo(ParsableBitArray data) {
    int headerSize = 0;
    int syncWord = data.readBits(16);
    headerSize += 2;
    int frameSize = data.readBits(16);
    headerSize += 2;
    if (frameSize == 0xFFFF) {
      frameSize = data.readBits(24);
      headerSize += 3; // Extended frame_size
    }
    frameSize += headerSize;
    if (syncWord == AC41_SYNCWORD) {
      frameSize += 2; // crc_word
    }
    int bitstreamVersion = data.readBits(2);
    if (bitstreamVersion == 3) {
      bitstreamVersion += readVariableBits(data, /* bitsPerRead= */ 2);
    }
    int sequenceCounter = data.readBits(10);
    if (data.readBit()) { // b_wait_frames
      if (data.readBits(3) > 0) { // wait_frames
        data.skipBits(2); // reserved
      }
    }
    int sampleRate = data.readBit() ? 48000 : 44100;
    int frameRateIndex = data.readBits(4);
    int sampleCount = 0;
    if (sampleRate == 44100 && frameRateIndex == 13) {
      sampleCount = SAMPLE_COUNT[frameRateIndex];
    } else if (sampleRate == 48000 && frameRateIndex < SAMPLE_COUNT.length) {
      sampleCount = SAMPLE_COUNT[frameRateIndex];
      switch (sequenceCounter % 5) {
        case 1: // fall through
        case 3:
          if (frameRateIndex == 3 || frameRateIndex == 8) {
            sampleCount++;
          }
          break;
        case 2:
          if (frameRateIndex == 8 || frameRateIndex == 11) {
            sampleCount++;
          }
          break;
        case 4:
          if (frameRateIndex == 3 || frameRateIndex == 8 || frameRateIndex == 11) {
            sampleCount++;
          }
          break;
        default:
          break;
      }
    }
    return new SyncFrameInfo(bitstreamVersion, CHANNEL_COUNT_2, sampleRate, frameSize, sampleCount);
  }

  /**
   * Returns the size in bytes of the given AC-4 syncframe.
   *
   * @param data The syncframe to parse.
   * @param syncword The syncword value for the syncframe.
   * @return The syncframe size in bytes, or {@link C#LENGTH_UNSET} if the input is invalid.
   */
  public static int parseAc4SyncframeSize(byte[] data, int syncword) {
    if (data.length < 7) {
      return C.LENGTH_UNSET;
    }
    int headerSize = 2; // syncword
    int frameSize = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
    headerSize += 2;
    if (frameSize == 0xFFFF) {
      frameSize = ((data[4] & 0xFF) << 16) | ((data[5] & 0xFF) << 8) | (data[6] & 0xFF);
      headerSize += 3;
    }
    if (syncword == AC41_SYNCWORD) {
      headerSize += 2;
    }
    frameSize += headerSize;
    return frameSize;
  }

  /**
   * Reads the number of audio samples represented by the given AC-4 syncframe. The buffer's
   * position is not modified.
   *
   * @param buffer The {@link ByteBuffer} from which to read the syncframe.
   * @return The number of audio samples represented by the syncframe.
   */
  public static int parseAc4SyncframeAudioSampleCount(ByteBuffer buffer) {
    byte[] bufferBytes = new byte[HEADER_SIZE_FOR_PARSER];
    int position = buffer.position();
    buffer.get(bufferBytes);
    buffer.position(position);
    return parseAc4SyncframeInfo(new ParsableBitArray(bufferBytes)).sampleCount;
  }

  /** Populates {@code buffer} with an AC-4 sample header for a sample of the specified size. */
  public static void getAc4SampleHeader(int size, ParsableByteArray buffer) {
    // See ETSI TS 103 190-1 V1.3.1, Annex G.
    buffer.reset(SAMPLE_HEADER_SIZE);
    byte[] data = buffer.getData();
    data[0] = (byte) 0xAC;
    data[1] = 0x40;
    data[2] = (byte) 0xFF;
    data[3] = (byte) 0xFF;
    data[4] = (byte) ((size >> 16) & 0xFF);
    data[5] = (byte) ((size >> 8) & 0xFF);
    data[6] = (byte) (size & 0xFF);
  }

  private static int readVariableBits(ParsableBitArray data, int bitsPerRead) {
    int value = 0;
    while (true) {
      value += data.readBits(bitsPerRead);
      if (!data.readBit()) {
        break;
      }
      value++;
      value <<= bitsPerRead;
    }
    return value;
  }

  /** Holds AC-4 presentation information. */
  private static final class Ac4Presentation {
    public boolean isChannelCoded;
    public @ChannelMode int channelMode;
    public int numOfUmxObjects;
    public boolean hasBackChannels;
    public int topChannelPairs;
    public int level;

    private Ac4Presentation() {
      isChannelCoded = true;
      channelMode = CHANNEL_MODE_UNKNOWN;
      numOfUmxObjects = -1;
      hasBackChannels = true;
      topChannelPairs = 2;
      level = 0;
    }
  }

  private Ac4Util() {}
}
