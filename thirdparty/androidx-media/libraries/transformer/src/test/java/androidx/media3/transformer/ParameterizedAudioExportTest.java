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

package androidx.media3.transformer;

import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static androidx.media3.transformer.TestUtil.addAudioDecoders;
import static androidx.media3.transformer.TestUtil.addAudioEncoders;
import static androidx.media3.transformer.TestUtil.createAudioEffects;
import static androidx.media3.transformer.TestUtil.createPitchChangingAudioProcessor;
import static androidx.media3.transformer.TestUtil.getSequenceDumpFilePath;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Parameterized audio end-to-end export test. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ParameterizedAudioExportTest {
  public static final String AUDIO_44100_MONO = ASSET_URI_PREFIX + FILE_AUDIO_RAW;
  public static final String AUDIO_48000_STEREO_VIDEO = ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO;

  @Parameters(name = "{0}")
  public static List<SequenceConfig> params() {
    return new ImmutableList.Builder<SequenceConfig>()
        .addAll(getAllPermutationsOfAllCombinations(AUDIO_ITEMS))
        .addAll(getAllPermutationsOfAllCombinations(AUDIO_VIDEO_ITEMS))
        .build();
  }

  private static List<SequenceConfig> getAllPermutationsOfAllCombinations(Set<ItemConfig> items) {
    return Sets.powerSet(items).stream()
        .filter(s -> !s.isEmpty())
        .flatMap(s -> Collections2.permutations(s).stream())
        .filter(permutation -> permutation.size() < 4)
        .map(SequenceConfig::new)
        .collect(toList());
  }

  private static final ImmutableSet<ItemConfig> AUDIO_ITEMS =
      ImmutableSet.of(
          new ItemConfig(
              AUDIO_44100_MONO,
              /* audioEffects= */ false,
              /* withSilentAudio= */ false,
              /* removeVideo= */ true),
          new ItemConfig(
              AUDIO_44100_MONO,
              /* audioEffects= */ true,
              /* withSilentAudio= */ false,
              /* removeVideo= */ true),
          new ItemConfig(
              AUDIO_48000_STEREO_VIDEO,
              /* audioEffects= */ false,
              /* withSilentAudio= */ false,
              /* removeVideo= */ true),
          new ItemConfig(
              AUDIO_48000_STEREO_VIDEO,
              /* audioEffects= */ true,
              /* withSilentAudio= */ false,
              /* removeVideo= */ true));

  private static final ImmutableSet<ItemConfig> AUDIO_VIDEO_ITEMS =
      ImmutableSet.of(
          new ItemConfig(
              AUDIO_48000_STEREO_VIDEO,
              /* audioEffects= */ false,
              /* withSilentAudio= */ false,
              /* removeVideo= */ false),
          new ItemConfig(
              AUDIO_48000_STEREO_VIDEO,
              /* audioEffects= */ true,
              /* withSilentAudio= */ false,
              /* removeVideo= */ false),
          new ItemConfig(
              AUDIO_48000_STEREO_VIDEO,
              /* audioEffects= */ false,
              /* withSilentAudio= */ true,
              /* removeVideo= */ false),
          new ItemConfig(
              AUDIO_48000_STEREO_VIDEO,
              /* audioEffects= */ true,
              /* withSilentAudio= */ true,
              /* removeVideo= */ false));

  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();

  @Parameter public SequenceConfig sequence;

  private final Context context = ApplicationProvider.getApplicationContext();

  private final CapturingMuxer.Factory muxerFactory =
      new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);

  @Before
  public void setUp() {
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    addAudioEncoders(MimeTypes.AUDIO_AAC);
  }

  @After
  public void tearDown() {
    removeEncodersAndDecoders();
  }

  @Test
  public void export() throws Exception {
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();

    transformer.start(sequence.asComposition(), outputDir.newFile().getPath());

    ExportResult result = TransformerTestRunner.runLooper(transformer);
    assertThat(result.processedInputs).hasSize(sequence.getSize());
    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        muxerFactory.getCreatedMuxer(),
        sequence.getDumpFilePath());
  }

  private static class SequenceConfig {
    private final List<ItemConfig> itemConfigs;

    public SequenceConfig(List<ItemConfig> itemConfigs) {
      this.itemConfigs = itemConfigs;
    }

    public Composition asComposition() {
      ImmutableList.Builder<EditedMediaItem> items = new ImmutableList.Builder<>();
      for (ItemConfig itemConfig : itemConfigs) {
        items.add(itemConfig.asItem());
      }

      return new Composition.Builder(new EditedMediaItemSequence.Builder(items.build()).build())
          .setTransmuxVideo(true)
          .experimentalSetForceAudioTrack(true)
          .build();
    }

    public String getDumpFilePath() {
      List<String> itemDumpNames = new ArrayList<>();
      for (ItemConfig itemConfig : itemConfigs) {
        itemDumpNames.add(itemConfig.getFilenameCompatibleItemSummary());
      }
      return getSequenceDumpFilePath(itemDumpNames);
    }

    public int getSize() {
      return itemConfigs.size();
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("Seq{");
      for (ItemConfig itemConfig : itemConfigs) {
        stringBuilder.append(itemConfig).append(", ");
      }
      stringBuilder.replace(stringBuilder.length() - 2, stringBuilder.length(), "}");
      return stringBuilder.toString();
    }
  }

  private static class ItemConfig {
    private final String uri;
    private final boolean mediaHasVideo;
    private final boolean audioEffects;
    private final boolean withSilentAudio;
    private final boolean removeVideo;

    public ItemConfig(
        String uri, boolean audioEffects, boolean withSilentAudio, boolean removeVideo) {
      this.uri = uri;
      this.mediaHasVideo = Objects.equals(uri, AUDIO_48000_STEREO_VIDEO);
      this.audioEffects = audioEffects;
      this.withSilentAudio = withSilentAudio;
      this.removeVideo = removeVideo;
    }

    public EditedMediaItem asItem() {
      EditedMediaItem.Builder editedMediaItem =
          new EditedMediaItem.Builder(MediaItem.fromUri(uri))
              .setRemoveAudio(withSilentAudio)
              .setRemoveVideo(removeVideo);
      if (audioEffects) {
        editedMediaItem.setEffects(createAudioEffects(createPitchChangingAudioProcessor(0.5f)));
      }

      return editedMediaItem.build();
    }

    @Override
    public String toString() {
      String itemName = "audio(";
      if (withSilentAudio) {
        itemName += "silence";
      } else if (uri.equals(AUDIO_44100_MONO)) {
        itemName += "mono_44.1kHz";
      } else if (uri.equals(AUDIO_48000_STEREO_VIDEO)) {
        itemName += "stereo_48kHz";
      } else {
        throw new IllegalArgumentException();
      }
      itemName += audioEffects ? "+effects" : "";
      itemName += mediaHasVideo && removeVideo ? "_removeVideo" : "";

      return itemName;
    }

    private String getFilenameCompatibleItemSummary() {
      // This descriptor is more specific than the toString, which is meant to be more human
      // readable.
      String dumpName = Iterables.getLast(Arrays.asList(Util.split(uri, "/")));
      dumpName += withSilentAudio ? "_silence" : "";
      dumpName += audioEffects ? "_halfPitch" : "";
      dumpName += mediaHasVideo && removeVideo ? "_removeVideo" : "";
      return dumpName;
    }
  }
}
