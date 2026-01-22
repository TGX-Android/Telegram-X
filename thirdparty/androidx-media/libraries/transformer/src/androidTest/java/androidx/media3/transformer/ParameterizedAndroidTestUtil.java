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
 *
 */
package androidx.media3.transformer;

import static androidx.media3.common.MimeTypes.VIDEO_H265;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.AndroidTestUtil.AssetInfo;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.math.RoundingMode;
import java.util.Objects;

/** Test utilities for parameterized instrumentation tests. */
/* package */ final class ParameterizedAndroidTestUtil {

  /**
   * Assume that the device supports the inputs and output of the sequence.
   *
   * <p>See {@link AndroidTestUtil#assumeFormatsSupported(Context, String, Format, Format)}.
   *
   * @param context The {@link Context context}.
   * @param testId The test ID.
   * @param sequence The {@link SequenceConfig}.
   * @throws Exception If an error occurs checking device support.
   */
  public static void assumeSequenceFormatsSupported(
      Context context, String testId, SequenceConfig sequence) throws Exception {
    checkState(!sequence.itemConfigs.isEmpty());
    Format outputFormat = checkNotNull(sequence.itemConfigs.get(0).outputFormat);
    for (ItemConfig item : sequence.itemConfigs) {
      assumeFormatsSupported(context, testId, item.format, outputFormat);
    }
  }

  /** Test parameters for an {@link EditedMediaItemSequence}. */
  public static final class SequenceConfig {
    public final int totalExpectedFrameCount;
    public final ImmutableList<ItemConfig> itemConfigs;

    public SequenceConfig(ItemConfig... itemConfigs) {
      this.itemConfigs = ImmutableList.copyOf(itemConfigs);
      int frameCountSum = 0;
      for (ItemConfig item : itemConfigs) {
        frameCountSum += item.frameCount;
      }
      this.totalExpectedFrameCount = frameCountSum;
    }

    /** Builds a {@link Composition} from the sequence configuration. */
    public Composition buildComposition(Effects compositionEffects) {
      ImmutableList.Builder<EditedMediaItem> editedMediaItems = new ImmutableList.Builder<>();
      for (ItemConfig itemConfig : itemConfigs) {
        editedMediaItems.add(itemConfig.build());
      }

      return new Composition.Builder(
              new EditedMediaItemSequence.Builder(editedMediaItems.build()).build())
          .setEffects(compositionEffects)
          .build();
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("Seq{");
      for (ItemConfig itemConfig : itemConfigs) {
        stringBuilder.append(itemConfig).append(",");
      }
      stringBuilder.replace(stringBuilder.length() - 1, stringBuilder.length(), "}");
      return stringBuilder.toString();
    }
  }

  /** Test parameters for an {@link EditedMediaItem}. */
  public abstract static class ItemConfig {
    public final int frameCount;
    @Nullable public final Format format;
    // TODO: b/345483531 - Modify output format to account for effects (such as presentation).
    @Nullable public final Format outputFormat;

    protected final Effects effects;

    protected final String uri;

    public ItemConfig(
        String uri,
        int frameCount,
        @Nullable Format format,
        @Nullable Format outputFormat,
        Effects effects) {
      this.uri = uri;
      this.frameCount = frameCount;
      this.format = format;
      this.outputFormat = outputFormat;
      this.effects = effects;
    }

    protected abstract EditedMediaItem build();

    @Override
    public String toString() {
      return Iterables.getLast(Splitter.on("/").splitToList(uri))
          + (Objects.equals(effects, Effects.EMPTY) ? "" : "-effects");
    }
  }

  /** {@link ItemConfig} for an SDR image {@link EditedMediaItem}. */
  public static final class SdrImageItemConfig extends ItemConfig {

    private final int frameRate;
    private final long durationUs;

    public SdrImageItemConfig(AssetInfo assetInfo, int frameCount) {
      this(assetInfo, frameCount, C.MICROS_PER_SECOND);
    }

    public SdrImageItemConfig(AssetInfo assetInfo, int frameRate, long durationUs) {
      this(assetInfo, frameRate, durationUs, Effects.EMPTY);
    }

    public SdrImageItemConfig(
        AssetInfo assetInfo, int frameRate, long durationUs, Effects effects) {
      super(
          assetInfo.uri,
          /* frameCount= */ (int)
              Util.scaleLargeValue(
                  frameRate, durationUs, C.MICROS_PER_SECOND, RoundingMode.CEILING),
          /* format= */ assetInfo.videoFormat,
          /* outputFormat= */ assetInfo
              .videoFormat
              .buildUpon()
              // Image by default are encoded in H265 and BT709 SDR.
              .setSampleMimeType(VIDEO_H265)
              .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
              .setFrameRate(frameRate)
              .build(),
          effects);
      this.frameRate = frameRate;
      this.durationUs = durationUs;
    }

    @Override
    protected EditedMediaItem build() {
      MediaItem mediaItem =
          new MediaItem.Builder().setUri(uri).setImageDurationMs(Util.usToMs(durationUs)).build();
      return new EditedMediaItem.Builder(mediaItem)
          .setEffects(effects)
          .setFrameRate(frameRate)
          .build();
    }
  }

  /**
   * {@link ItemConfig} for a video {@link EditedMediaItem}.
   *
   * <p>Audio is removed.
   */
  public static final class VideoItemConfig extends ItemConfig {
    public VideoItemConfig(AssetInfo asset, Effects effects) {
      super(asset.uri, asset.videoFrameCount, asset.videoFormat, asset.videoFormat, effects);
    }

    @Override
    protected EditedMediaItem build() {
      return new EditedMediaItem.Builder(MediaItem.fromUri(uri))
          .setEffects(effects)
          .setRemoveAudio(true)
          .build();
    }
  }

  private ParameterizedAndroidTestUtil() {}
}
