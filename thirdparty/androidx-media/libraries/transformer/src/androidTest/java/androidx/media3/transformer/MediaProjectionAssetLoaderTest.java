/*
 * Copyright 2025 The Android Open Source Project
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

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.SparseArray;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Util;
import androidx.media3.effect.Presentation;
import androidx.media3.muxer.MuxerException;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import androidx.window.layout.WindowMetricsCalculator;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for {@link MediaProjectionAssetLoader}. */
@RunWith(AndroidJUnit4.class)
public final class MediaProjectionAssetLoaderTest {

  private static final long TIMEOUT_MS = 25_000;
  private static final int MINIMUM_EXPECTED_SAMPLE_COUNT = 5;
  private static final String ACCEPT_RESOURCE_ID = "android:id/button1";

  @Rule public final TestName testName = new TestName();

  private MediaProjection mediaProjection;
  private AssetLoader.Factory mediaProjectionAssetLoaderFactory;

  @BeforeClass
  public static void staticSetUp() {
    // Restrict the test to devices that reliably support media projection. This avoids testing on:
    // - Old (API 23-27) devices where virtual display fails with "getLayerReleaseFence: Invalid
    //   display", with GL errors or where frames are produced extremely slowly.
    // - Newer (eg., API 31) devices with non-standard media projection UIs.
    // - Wearables, where we'd otherwise have to scroll the UI to enable media projection.
    // The test does pass on some earlier builds (back to API 23) but not on all devices.
    assumeTrue(
        Util.isRunningOnEmulator()
            || (Util.SDK_INT >= 29
                && !Util.isWear(getInstrumentation().getContext())
                && Build.MODEL.startsWith("Pixel")));
  }

  @Before
  public void setUp() throws Exception {
    // Launch the activity and foreground service for screen recording.
    AtomicReference<MediaProjectionActivity> mediaProjectionActivityAtomicReference =
        new AtomicReference<>();
    ActivityScenario.launch(
            new Intent(getInstrumentation().getContext(), MediaProjectionActivity.class))
        .onActivity(
            (activity) ->
                mediaProjectionActivityAtomicReference.set((MediaProjectionActivity) activity));
    MediaProjectionActivity mediaProjectionActivity = mediaProjectionActivityAtomicReference.get();
    Future<Intent> mediaProjectionIntentFuture =
        mediaProjectionActivity.getScreenCaptureTokenFuture();
    Context context = getInstrumentation().getContext();
    ConditionVariable startConditionVariable = new ConditionVariable();
    Util.registerReceiverNotExported(
        context,
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String action = checkNotNull(intent.getAction());
            if (action.equals(
                MediaProjectionActivity.MediaProjectionService.ACTION_EVENT_STARTED)) {
              context.unregisterReceiver(/* receiver= */ this);
              startConditionVariable.open();
            }
          }
        },
        new IntentFilter(MediaProjectionActivity.MediaProjectionService.ACTION_EVENT_STARTED));

    // Press the button to start screen capture.
    UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
    uiDevice.wait(Until.findObject(By.res(ACCEPT_RESOURCE_ID)), TIMEOUT_MS).click();
    startConditionVariable.block();

    Intent screenCaptureToken = mediaProjectionIntentFuture.get();
    MediaProjectionManager mediaProjectionManager =
        (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);
    mediaProjection =
        mediaProjectionManager.getMediaProjection(RESULT_OK, checkNotNull(screenCaptureToken));
    Rect bounds =
        WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(mediaProjectionActivity)
            .getBounds();
    int densityDpi = mediaProjectionActivity.getResources().getConfiguration().densityDpi;
    mediaProjectionAssetLoaderFactory =
        new MediaProjectionAssetLoader.Factory(mediaProjection, bounds, densityDpi);
  }

  @After
  public void tearDown() {
    Context context = getInstrumentation().getContext();
    Intent stopIntent = new Intent(context, MediaProjectionActivity.MediaProjectionService.class);
    stopIntent.setAction(MediaProjectionActivity.MediaProjectionService.ACTION_STOP);
    Util.startForegroundService(context, stopIntent);
  }

  @Test
  public void export_producesVideo() throws Exception {
    Context context = getInstrumentation().getContext();
    CountDownLatch videoSamplesWrittenCountDownLatch =
        new CountDownLatch(MINIMUM_EXPECTED_SAMPLE_COUNT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setMuxerFactory(
                new InterceptingMuxerFactory(
                    (format, byteBuffer, bufferInfo) -> {
                      if (MimeTypes.isVideo(format.sampleMimeType)) {
                        videoSamplesWrittenCountDownLatch.countDown();
                      }
                    }))
            .setAssetLoaderFactory(mediaProjectionAssetLoaderFactory)
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(SurfaceAssetLoader.MEDIA_ITEM_URI_SCHEME + ":test"))
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    ImmutableList.of(
                        Presentation.createForWidthAndHeight(
                            320, 240, Presentation.LAYOUT_STRETCH_TO_FIT))))
            .build();

    // Run the capture operation then stop transformer.
    ListenableFuture<ExportResult> exportResultListenableFuture =
        new TransformerAndroidTestRunner.Builder(getInstrumentation().getContext(), transformer)
            .build()
            .runAsync(testName.getMethodName(), editedMediaItem);
    assertThat(videoSamplesWrittenCountDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> mediaProjection.stop());

    ExportResult exportResult = exportResultListenableFuture.get();
    assertThat(exportResult.videoFrameCount).isAtLeast(MINIMUM_EXPECTED_SAMPLE_COUNT);
  }

  /** Factory for in-app muxers that supports getting a callback when a sample is written. */
  private static final class InterceptingMuxerFactory implements Muxer.Factory {

    public interface WriteSampleDataCallback {
      void onWriteSampleData(
          Format format, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo);
    }

    private final WriteSampleDataCallback writeSampleDataCallback;
    private final Muxer.Factory wrappedMuxerFactory;

    public InterceptingMuxerFactory(WriteSampleDataCallback writeSampleDataCallback) {
      this.writeSampleDataCallback = writeSampleDataCallback;
      wrappedMuxerFactory = new InAppMp4Muxer.Factory();
    }

    @Override
    public Muxer create(String path) throws MuxerException {
      Muxer wrappedMuxer = wrappedMuxerFactory.create(path);
      SparseArray<Format> formatByTrackId = new SparseArray<>();
      return new Muxer() {
        @Override
        public int addTrack(Format format) throws MuxerException {
          int trackId = wrappedMuxer.addTrack(format);
          formatByTrackId.put(trackId, format);
          return trackId;
        }

        @Override
        public void writeSampleData(
            int trackId, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo)
            throws MuxerException {
          writeSampleDataCallback.onWriteSampleData(
              formatByTrackId.get(trackId), byteBuffer, bufferInfo);
          wrappedMuxer.writeSampleData(trackId, byteBuffer, bufferInfo);
        }

        @Override
        public void addMetadataEntry(Metadata.Entry metadataEntry) {
          wrappedMuxer.addMetadataEntry(metadataEntry);
        }

        @Override
        public void close() throws MuxerException {
          wrappedMuxer.close();
        }
      };
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return wrappedMuxerFactory.getSupportedSampleMimeTypes(trackType);
    }
  }
}
