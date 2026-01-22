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

package androidx.media3.transformer.mh;

import static androidx.media3.test.utils.TestUtil.retrieveTrackFormat;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ULTRA_HDR_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.assertSdrColors;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
import static androidx.media3.transformer.SequenceEffectTestUtil.NO_EFFECT;
import static androidx.media3.transformer.SequenceEffectTestUtil.clippedVideo;
import static androidx.media3.transformer.SequenceEffectTestUtil.createComposition;
import static androidx.media3.transformer.SequenceEffectTestUtil.oneFrameFromImage;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsHdrEditing;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsOpenGlToneMapping;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.json.JSONException;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for Ultra HDR support in Transformer that should only run in mobile harness. */
@RunWith(AndroidJUnit4.class)
public final class TransformerMhUltraHdrTest {

  private static final int ONE_FRAME_END_POSITION_MS = 30;

  @Rule public final TestName testName = new TestName();
  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void exportUltraHdrImage_withUltraHdrEnabledOnSupportedDevice_exportsHdr()
      throws Exception {
    assumeDeviceSupportsUltraHdrEditing();
    Composition composition =
        createUltraHdrComposition(
            /* tonemap= */ false, oneFrameFromImage(JPG_ULTRA_HDR_ASSET.uri, NO_EFFECT));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    ColorInfo colorInfo =
        retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO).colorInfo;
    assertThat(colorInfo.colorSpace).isEqualTo(C.COLOR_SPACE_BT2020);
    assertThat(colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportHdrVideoThenUltraHdrImage_exportsHdr() throws Exception {
    assumeDeviceSupportsUltraHdrEditing();
    Composition composition =
        createComposition(
            /* presentation= */ null,
            clippedVideo(MP4_ASSET_1080P_5_SECOND_HLG10.uri, NO_EFFECT, ONE_FRAME_END_POSITION_MS),
            oneFrameFromImage(JPG_ULTRA_HDR_ASSET.uri, NO_EFFECT));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    ColorInfo colorInfo =
        retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO).colorInfo;
    assertThat(colorInfo.colorSpace).isEqualTo(C.COLOR_SPACE_BT2020);
    assertThat(colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportUltraHdrImageThenHdrVideo_exportsHdr() throws Exception {
    assumeDeviceSupportsUltraHdrEditing();
    Composition composition =
        createUltraHdrComposition(
            /* tonemap= */ false,
            oneFrameFromImage(JPG_ULTRA_HDR_ASSET.uri, NO_EFFECT),
            clippedVideo(MP4_ASSET_1080P_5_SECOND_HLG10.uri, NO_EFFECT, ONE_FRAME_END_POSITION_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    ColorInfo colorInfo =
        retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO).colorInfo;
    assertThat(colorInfo.colorSpace).isEqualTo(C.COLOR_SPACE_BT2020);
    assertThat(colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_HLG);
  }

  @Test
  public void exportTonemappedHdrVideoThenUltraHdrImage_exportsSdr() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(testId, MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
    Composition composition =
        createUltraHdrComposition(
            /* tonemap= */ true,
            clippedVideo(MP4_ASSET_1080P_5_SECOND_HLG10.uri, NO_EFFECT, ONE_FRAME_END_POSITION_MS),
            oneFrameFromImage(JPG_ULTRA_HDR_ASSET.uri, NO_EFFECT));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertSdrColors(context, result.filePath);
  }

  private static Composition createUltraHdrComposition(
      boolean tonemap, EditedMediaItem editedMediaItem, EditedMediaItem... editedMediaItems) {
    Composition.Builder builder =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem)
                    .addItems(editedMediaItems)
                    .build())
            .experimentalSetRetainHdrFromUltraHdrImage(true);
    if (tonemap) {
      builder.setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL);
    }
    return builder.build();
  }

  private void assumeDeviceSupportsUltraHdrEditing() throws JSONException, IOException {
    if (Util.SDK_INT < 34) {
      recordTestSkipped(
          getApplicationContext(), testId, "Ultra HDR is not supported on this API level.");
      throw new AssumptionViolatedException("Ultra HDR is not supported on this API level.");
    }
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
  }
}
