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

import static androidx.media3.transformer.AndroidTestUtil.BT601_MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET;
import static androidx.media3.transformer.ParameterizedAndroidTestUtil.assumeSequenceFormatsSupported;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.util.Log;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.ParameterizedAndroidTestUtil.SdrImageItemConfig;
import androidx.media3.transformer.ParameterizedAndroidTestUtil.SequenceConfig;
import androidx.media3.transformer.ParameterizedAndroidTestUtil.VideoItemConfig;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// TODO: b/345483531 - Add tests to assert enough silence is generated.
// TODO: b/346289922 - Consider checking frame counts with extractors.
// TODO: b/345483531 - Add support for asserting on duration for image only sequences.
// TODO: b/345483531 - Generate all permutations of all combinations of input files.

/** Parameterized end-to-end test for exporting a {@link EditedMediaItemSequence}. */
@RunWith(Parameterized.class)
public final class ParameterizedInputSequenceExportTest {
  private static final String TAG = "ParameterizedTest";

  private static final SdrImageItemConfig PNG_ITEM =
      new SdrImageItemConfig(PNG_ASSET, /* frameCount= */ 34);
  private static final SdrImageItemConfig JPG_ITEM =
      new SdrImageItemConfig(JPG_ASSET, /* frameCount= */ 41);
  private static final VideoItemConfig BT709_ITEM =
      new VideoItemConfig(
          MP4_ASSET,
          new Effects(
              /* audioProcessors= */ ImmutableList.of(),
              ImmutableList.of(Presentation.createForHeight(360))));
  private static final VideoItemConfig BT601_ITEM =
      new VideoItemConfig(
          BT601_MP4_ASSET,
          new Effects(
              /* audioProcessors= */ ImmutableList.of(),
              ImmutableList.of(Presentation.createForHeight(360))));

  @Parameters
  public static ImmutableList<SequenceConfig> params() {
    return ImmutableList.of(
        new SequenceConfig(PNG_ITEM, PNG_ITEM),
        new SequenceConfig(PNG_ITEM, JPG_ITEM),
        new SequenceConfig(PNG_ITEM, BT601_ITEM),
        new SequenceConfig(PNG_ITEM, BT709_ITEM),
        new SequenceConfig(JPG_ITEM, PNG_ITEM),
        new SequenceConfig(JPG_ITEM, JPG_ITEM),
        new SequenceConfig(JPG_ITEM, BT601_ITEM),
        new SequenceConfig(JPG_ITEM, BT709_ITEM),
        new SequenceConfig(BT601_ITEM, PNG_ITEM),
        new SequenceConfig(BT601_ITEM, JPG_ITEM),
        new SequenceConfig(BT601_ITEM, BT601_ITEM),
        new SequenceConfig(BT601_ITEM, BT709_ITEM),
        new SequenceConfig(BT709_ITEM, PNG_ITEM),
        new SequenceConfig(BT709_ITEM, JPG_ITEM),
        new SequenceConfig(BT709_ITEM, BT601_ITEM),
        new SequenceConfig(BT709_ITEM, BT709_ITEM),
        new SequenceConfig(
            BT709_ITEM, BT709_ITEM, PNG_ITEM, JPG_ITEM, BT709_ITEM, PNG_ITEM, BT709_ITEM),
        new SequenceConfig(
            PNG_ITEM, BT709_ITEM, BT709_ITEM, PNG_ITEM, PNG_ITEM, BT709_ITEM, PNG_ITEM),
        new SequenceConfig(
            PNG_ITEM, BT709_ITEM, BT601_ITEM, PNG_ITEM, PNG_ITEM, BT601_ITEM, PNG_ITEM),
        new SequenceConfig(
            PNG_ITEM, JPG_ITEM, BT709_ITEM, BT601_ITEM, BT709_ITEM, PNG_ITEM, BT601_ITEM),
        new SequenceConfig(
            BT601_ITEM, BT709_ITEM, PNG_ITEM, JPG_ITEM, BT709_ITEM, PNG_ITEM, BT601_ITEM));
  }

  @Rule public final TestName testName = new TestName();

  @Parameter public SequenceConfig sequence;

  @Test
  public void export_completesWithCorrectFrameCount() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String testId = testName.getMethodName();
    Log.w(TAG, sequence.toString());
    assumeSequenceFormatsSupported(context, testId, sequence);
    Transformer transformer =
        new Transformer.Builder(context)
            .experimentalSetMaxFramesInEncoder(16)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, sequence.buildComposition(Effects.EMPTY));

    assertThat(result.exportResult.videoFrameCount).isEqualTo(sequence.totalExpectedFrameCount);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void export_withCompositionEffect_completesWithCorrectFrameCount() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String testId = testName.getMethodName();
    Log.w(TAG, sequence.toString());
    assumeSequenceFormatsSupported(context, testId, sequence);
    Transformer transformer =
        new Transformer.Builder(context)
            .experimentalSetMaxFramesInEncoder(16)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                testId,
                sequence.buildComposition(
                    // Presentation of 480Wx360H is used to ensure software encoders can encode.
                    new Effects(
                        /* audioProcessors= */ ImmutableList.of(),
                        ImmutableList.of(
                            Presentation.createForWidthAndHeight(
                                /* width= */ 480,
                                /* height= */ 360,
                                Presentation.LAYOUT_SCALE_TO_FIT)))));

    assertThat(result.exportResult.videoFrameCount).isEqualTo(sequence.totalExpectedFrameCount);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }
}
