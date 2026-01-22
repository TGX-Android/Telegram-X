/*
 * Copyright 2025 The Android Open Source Project
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
package androidx.media3.extractor.text.vobsub;

import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Format.CueReplacementBehavior;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link SubtitleParser} for Vobsub subtitles. */
@UnstableApi
public final class VobsubParser implements SubtitleParser {

  /**
   * The {@link CueReplacementBehavior} for consecutive {@link CuesWithTiming} emitted by this
   * implementation.
   */
  public static final @CueReplacementBehavior int CUE_REPLACEMENT_BEHAVIOR =
      Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE;

  private static final String TAG = "VobsubParser";
  private static final int DEFAULT_DURATION_US = 5_000_000;

  private final ParsableByteArray scratch;
  private final ParsableByteArray inflatedScratch;
  private final CueBuilder cueBuilder;
  @Nullable private Inflater inflater;

  public VobsubParser(List<byte[]> initializationData) {
    scratch = new ParsableByteArray();
    inflatedScratch = new ParsableByteArray();
    cueBuilder = new CueBuilder();
    cueBuilder.parseIdx(new String(initializationData.get(0), UTF_8));
  }

  @Override
  public @CueReplacementBehavior int getCueReplacementBehavior() {
    return CUE_REPLACEMENT_BEHAVIOR;
  }

  @Override
  public void parse(
      byte[] data,
      int offset,
      int length,
      OutputOptions outputOptions,
      Consumer<CuesWithTiming> output) {
    scratch.reset(data, offset + length);
    scratch.setPosition(offset);
    @Nullable Cue cue = parse();
    output.accept(
        new CuesWithTiming(
            cue != null ? ImmutableList.of(cue) : ImmutableList.of(),
            /* startTimeUs= */ C.TIME_UNSET,
            /* durationUs= */ DEFAULT_DURATION_US));
  }

  @Nullable
  private Cue parse() {
    if (inflater == null) {
      inflater = new Inflater();
    }
    if (Util.maybeInflate(scratch, inflatedScratch, inflater)) {
      scratch.reset(inflatedScratch.getData(), inflatedScratch.limit());
    }
    cueBuilder.reset();
    int bytesLeft = scratch.bytesLeft();
    if (bytesLeft < 2 || scratch.readUnsignedShort() != bytesLeft) {
      return null;
    }
    cueBuilder.parseSpu(scratch);
    return cueBuilder.build(scratch);
  }

  private static final class CueBuilder {

    private static final int CMD_FORCE_START = 0;
    private static final int CMD_START = 1;
    private static final int CMD_STOP = 2;
    private static final int CMD_COLORS = 3;
    private static final int CMD_ALPHA = 4;
    private static final int CMD_AREA = 5;
    private static final int CMD_OFFSETS = 6;
    private static final int CMD_END = 255;

    private final int[] colors;

    private boolean hasPlane;
    private boolean hasColors;
    private int @MonotonicNonNull [] palette;
    private int planeWidth;
    private int planeHeight;
    @Nullable private Rect boundingBox;
    private int dataOffset0;
    private int dataOffset1;

    public CueBuilder() {
      colors = new int[4];
      dataOffset0 = C.INDEX_UNSET;
      dataOffset1 = C.INDEX_UNSET;
    }

    public void parseIdx(String idx) {
      for (String line : Util.split(idx.trim(), "\\r?\\n")) {
        if (line.startsWith("palette: ")) {
          String[] values = Util.split(line.substring("palette: ".length()), ",");
          palette = new int[values.length];

          for (int i = 0; i < values.length; i++) {
            palette[i] = parseColor(values[i].trim());
          }
        } else if (line.startsWith("size: ")) {
          // We need this line to calculate the relative positions and size required when building
          // the Cue below.
          String[] sizes = Util.split(line.substring("size: ".length()).trim(), "x");

          if (sizes.length == 2) {
            try {
              planeWidth = Integer.parseInt(sizes[0]);
              planeHeight = Integer.parseInt(sizes[1]);
              hasPlane = true;
            } catch (RuntimeException e) {
              Log.w(TAG, "Parsing IDX failed", e);
            }
          }
        }
      }
    }

    private static int parseColor(String value) {
      try {
        return Integer.parseInt(value, 16);
      } catch (RuntimeException e) {
        return 0;
      }
    }

    public void parseSpu(ParsableByteArray buffer) {
      if (palette == null || !hasPlane) {
        // Give up if we don't have the color palette or the video size.
        return;
      }
      int[] palette = this.palette;
      buffer.skipBytes(buffer.readUnsignedShort() - 2);
      int end = buffer.readUnsignedShort();
      parseControl(palette, buffer, end);
    }

    private void parseControl(int[] palette, ParsableByteArray buffer, int end) {
      while (buffer.getPosition() < end && buffer.bytesLeft() > 0) {
        switch (buffer.readUnsignedByte()) {
          case CMD_COLORS:
            if (!parseControlColors(palette, buffer)) {
              return;
            }
            break;
          case CMD_ALPHA:
            if (!parseControlAlpha(buffer)) {
              return;
            }
            break;
          case CMD_AREA:
            if (!parseControlArea(buffer)) {
              return;
            }
            break;
          case CMD_OFFSETS:
            if (!parseControlOffsets(buffer)) {
              return;
            }
            break;
          case CMD_FORCE_START:
          case CMD_START:
          case CMD_STOP:
            // ignore unused commands without arguments
            break;
          case CMD_END:
          default:
            return;
        }
      }
    }

    private boolean parseControlColors(int[] palette, ParsableByteArray buffer) {
      if (buffer.bytesLeft() < 2) {
        return false;
      }

      int byte0 = buffer.readUnsignedByte();
      int byte1 = buffer.readUnsignedByte();

      colors[3] = getColor(palette, byte0 >> 4);
      colors[2] = getColor(palette, byte0 & 0xf);
      colors[1] = getColor(palette, byte1 >> 4);
      colors[0] = getColor(palette, byte1 & 0xf);
      hasColors = true;

      return true;
    }

    private static int getColor(int[] palette, int index) {
      return index >= 0 && index < palette.length ? palette[index] : palette[0];
    }

    private boolean parseControlAlpha(ParsableByteArray buffer) {

      if (buffer.bytesLeft() < 2 || !hasColors) {
        return false;
      }

      int byte0 = buffer.readUnsignedByte();
      int byte1 = buffer.readUnsignedByte();

      colors[3] = setAlpha(colors[3], (byte0 >> 4));
      colors[2] = setAlpha(colors[2], (byte0 & 0xf));
      colors[1] = setAlpha(colors[1], (byte1 >> 4));
      colors[0] = setAlpha(colors[0], (byte1 & 0xf));

      return true;
    }

    private static int setAlpha(int color, int alpha) {
      return ((color & 0x00ffffff) | ((alpha * 17) << 24));
    }

    private boolean parseControlArea(ParsableByteArray buffer) {
      if (buffer.bytesLeft() < 6) {
        return false;
      }

      int byte0 = buffer.readUnsignedByte();
      int byte1 = buffer.readUnsignedByte();
      int byte2 = buffer.readUnsignedByte();

      int left = (byte0 << 4) | (byte1 >> 4);
      int right = ((byte1 & 0xf) << 8) | byte2;

      int byte3 = buffer.readUnsignedByte();
      int byte4 = buffer.readUnsignedByte();
      int byte5 = buffer.readUnsignedByte();

      int top = (byte3 << 4) | (byte4 >> 4);
      int bottom = ((byte4 & 0xf) << 8) | byte5;

      boundingBox = new Rect(left, top, right + 1, bottom + 1);

      return true;
    }

    private boolean parseControlOffsets(ParsableByteArray buffer) {
      if (buffer.bytesLeft() < 4) {
        return false;
      }

      dataOffset0 = buffer.readUnsignedShort();
      dataOffset1 = buffer.readUnsignedShort();

      return true;
    }

    @Nullable
    public Cue build(ParsableByteArray buffer) {
      if (palette == null
          || !hasPlane
          || !hasColors
          || boundingBox == null
          || dataOffset0 == C.INDEX_UNSET
          || dataOffset1 == C.INDEX_UNSET
          || boundingBox.width() < 2
          || boundingBox.height() < 2) {
        return null;
      }
      Rect boundingBox = this.boundingBox;
      int[] bitmapData = new int[boundingBox.width() * boundingBox.height()];
      ParsableBitArray bitBuffer = new ParsableBitArray();

      buffer.setPosition(dataOffset0);
      bitBuffer.reset(buffer);
      parseRleData(bitBuffer, /* evenInterlace= */ true, boundingBox, bitmapData);
      buffer.setPosition(dataOffset1);
      bitBuffer.reset(buffer);
      parseRleData(bitBuffer, /* evenInterlace= */ false, boundingBox, bitmapData);

      Bitmap bitmap =
          Bitmap.createBitmap(
              bitmapData, boundingBox.width(), boundingBox.height(), Bitmap.Config.ARGB_8888);

      return new Cue.Builder()
          .setBitmap(bitmap)
          .setPosition((float) boundingBox.left / planeWidth)
          .setPositionAnchor(Cue.ANCHOR_TYPE_START)
          .setLine((float) boundingBox.top / planeHeight, Cue.LINE_TYPE_FRACTION)
          .setLineAnchor(Cue.ANCHOR_TYPE_START)
          .setSize((float) boundingBox.width() / planeWidth)
          .setBitmapHeight((float) boundingBox.height() / planeHeight)
          .build();
    }

    /**
     * Parse run-length encoded data into the {@code bitmapData} array. The subtitle bitmap is
     * encoded in two blocks of interlaced lines, {@code y} gives the index of the starting line (0
     * or 1).
     *
     * @param bitBuffer The RLE encoded data.
     * @param evenInterlace Whether to decode the even or odd interlaced lines.
     * @param bitmapData Output array.
     */
    private void parseRleData(
        ParsableBitArray bitBuffer, boolean evenInterlace, Rect boundingBox, int[] bitmapData) {
      int width = boundingBox.width();
      int height = boundingBox.height();
      int x = 0;
      int y = evenInterlace ? 0 : 1;
      int outIndex = y * width;
      Run run = new Run();

      while (true) {
        parseRun(bitBuffer, width, run);

        int length = min(run.length, width - x);
        if (length > 0) {
          Arrays.fill(bitmapData, outIndex, outIndex + length, colors[run.colorIndex]);
          outIndex += length;
          x += length;
        }
        if (x >= width) {
          y += 2;
          if (y >= height) {
            break;
          }
          x = 0;
          outIndex = y * width;
          bitBuffer.byteAlign();
        }
      }
    }

    private static void parseRun(ParsableBitArray bitBuffer, int width, Run output) {
      int value = 0;
      int test = 1;

      while (value < test && test <= 0x40) {
        if (bitBuffer.bitsLeft() < 4) {
          output.colorIndex = C.INDEX_UNSET;
          output.length = 0;
          return;
        }
        value = (value << 4) | bitBuffer.readBits(4);
        test <<= 2;
      }
      output.colorIndex = value & 3;
      output.length = value < 4 ? width : (value >> 2);
    }

    public void reset() {
      hasColors = false;
      boundingBox = null;
      dataOffset0 = C.INDEX_UNSET;
      dataOffset1 = C.INDEX_UNSET;
    }

    private static final class Run {
      public int colorIndex;
      public int length;
    }
  }
}
