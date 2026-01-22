/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.media3.transformer.mh.analysis;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.VideoEncoderSettings.NO_VALUE;
import static androidx.media3.transformer.VideoEncoderSettings.RATE_UNSET;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.AndroidTestUtil.AssetInfo;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Instrumentation tests for analyzing encoder performance settings. */
@RunWith(Parameterized.class)
@Ignore(
    "Analysis tests are not used for confirming Transformer is running properly, and not configured"
        + " for this use as they're missing skip checks for unsupported devices.")
public class EncoderPerformanceAnalysisTest {

  private static final ImmutableList<AssetInfo> INPUT_ASSETS =
      ImmutableList.of(
          AndroidTestUtil.MP4_ASSET_H264_4K_10SEC_VIDEO,
          AndroidTestUtil.MP4_ASSET_H264_1080P_10SEC_VIDEO,
          AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10);

  private static final ImmutableList<Integer> OPERATING_RATE_AND_PRIORITIES =
      ImmutableList.of(RATE_UNSET, NO_VALUE);
  private static final ImmutableList<Boolean> ENABLE_FALLBACK = ImmutableList.of(true, false);

  @Parameter(0)
  public @MonotonicNonNull TestConfig config;

  @Parameters(name = "analyzePerformance_{0}")
  public static ImmutableList<Object[]> parameters() {
    ImmutableList.Builder<Object[]> parametersBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < INPUT_ASSETS.size(); i++) {
      for (int operatingRateAndPriority : OPERATING_RATE_AND_PRIORITIES) {
        for (boolean enableFallback : ENABLE_FALLBACK) {
          TestConfig.Builder configBuilder =
              new TestConfig.Builder(INPUT_ASSETS.get(i))
                  .setOperatingRate(operatingRateAndPriority)
                  .setPriority(operatingRateAndPriority)
                  .setEnableFallback(enableFallback);
          parametersBuilder.add(new Object[] {configBuilder.build()});
        }
      }
    }
    return parametersBuilder.build();
  }

  @Test
  public void analyzeEncoderPerformance() throws Exception {
    checkNotNull(config.assetInfo.uri);
    Context context = ApplicationProvider.getApplicationContext();

    VideoEncoderSettings.Builder settingsBuilder = new VideoEncoderSettings.Builder();
    settingsBuilder.setEncoderPerformanceParameters(config.operatingRate, config.priority);

    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new AndroidTestUtil.ForceEncodeEncoderFactory(
                    /* wrappedEncoderFactory= */ new DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(settingsBuilder.build())
                        .setEnableFallback(config.enableFallback)
                        .build()))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(config.assetInfo.uri))).build();

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setInputValues(config.getInputValues())
        .build()
        .run(config.getTestId(), editedMediaItem);
  }

  /** Wrapper class storing values that can be used to test. */
  private static class TestConfig {

    public static class Builder {

      private final AssetInfo assetInfo;
      private boolean enableFallback;
      private int operatingRate;
      private int priority;
      private int profile;
      private int level;

      public Builder(AssetInfo assetInfo) {
        this.assetInfo = assetInfo;
        enableFallback = true;
        operatingRate = NO_VALUE;
        priority = NO_VALUE;
        profile = NO_VALUE;
        level = NO_VALUE;
      }

      @CanIgnoreReturnValue
      public Builder setEnableFallback(boolean enableFallback) {
        this.enableFallback = enableFallback;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder setOperatingRate(int operatingRate) {
        this.operatingRate = operatingRate;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder setPriority(int priority) {
        this.priority = priority;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder setProfile(int profile) {
        this.profile = profile;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder setLevel(int level) {
        this.level = level;
        return this;
      }

      public TestConfig build() {
        return new TestConfig(assetInfo, enableFallback, operatingRate, priority, profile, level);
      }
    }

    public final AssetInfo assetInfo;
    public final boolean enableFallback;
    public final int operatingRate;
    public final int priority;
    public final int profile;
    public final int level;

    private TestConfig(
        AssetInfo assetInfo,
        boolean enableFallback,
        int operatingRate,
        int priority,
        int profile,
        int level) {
      this.assetInfo = assetInfo;
      this.enableFallback = enableFallback;
      this.operatingRate = operatingRate;
      this.priority = priority;
      this.profile = profile;
      this.level = level;
    }

    public String getTestId() {
      StringBuilder testIdBuilder = new StringBuilder();
      testIdBuilder.append(
          String.format(
              "analyzePerformance_%s_Fallback_%d_OpRate_%d_Priority_%d_Profile_%d_Level_%d",
              getFilename(), enableFallback ? 1 : 0, operatingRate, priority, profile, level));
      return testIdBuilder.toString();
    }

    public Map<String, Object> getInputValues() {
      Map<String, Object> inputValues = new HashMap<>();
      inputValues.put("inputFilename", getFilename());
      inputValues.put("enableFallback", enableFallback);
      inputValues.put("operatingRate", operatingRate);
      inputValues.put("priority", priority);
      inputValues.put("profile", profile);
      inputValues.put("level", level);
      return inputValues;
    }

    private String getFilename() {
      return checkNotNull(Uri.parse(assetInfo.uri).getLastPathSegment());
    }

    @Override
    public String toString() {
      return getTestId();
    }
  }
}
