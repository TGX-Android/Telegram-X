/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_SESSION_SERVICE;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.TestHandler;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaBrowserCompat} with {@link MediaSessionService}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserCompatWithMediaSessionServiceTest {

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaBrowserCompatTestWithMediaSessionService");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  Context context;
  TestHandler handler;
  @Nullable MediaBrowserCompat browserCompat;
  @Nullable MediaController serviceStartController;
  @Nullable MediaControllerCompat controllerCompat;
  TestConnectionCallback connectionCallback;
  List<PlaybackStateCompat> reportedPlaybackStatesCompat;
  @Nullable CountDownLatch firstPlaybackStateCompatReported;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    handler = threadTestRule.getHandler();
    connectionCallback = new TestConnectionCallback();
    firstPlaybackStateCompatReported = new CountDownLatch(1);
    reportedPlaybackStatesCompat = new ArrayList<>();
  }

  @After
  public void cleanUp() throws Exception {
    if (browserCompat != null) {
      browserCompat.disconnect();
      browserCompat = null;
    }
    if (serviceStartController != null) {
      handler.postAndSync(() -> serviceStartController.release());
    }
  }

  ComponentName getServiceComponent() {
    return MOCK_MEDIA3_SESSION_SERVICE;
  }

  /**
   * Starts the service by connecting a Media3 controller and passing connection hints. The service
   * can then use the connection hints to build the session for instance with specific settings.
   *
   * <p>Note that a media1 {@link MediaBrowserCompat} can't send connection hints. The root hints of
   * the legacy browser end up in the {@link MediaLibraryService.LibraryParams} passed to {@link
   * MediaLibrarySession.Callback#onGetLibraryRoot(MediaLibraryService.MediaLibrarySession,
   * MediaSession.ControllerInfo, MediaLibraryService.LibraryParams)} as they aren't available
   * earlier.
   */
  void connectForServiceStartWithConnectionHints(Bundle connectionHints) throws Exception {
    CountDownLatch latch = new CountDownLatch(/* count= */ 1);
    handler.postAndSync(
        () -> {
          ListenableFuture<MediaController> future =
              new MediaController.Builder(
                      ApplicationProvider.getApplicationContext(),
                      new SessionToken(
                          ApplicationProvider.getApplicationContext(), getServiceComponent()))
                  .setConnectionHints(connectionHints)
                  .buildAsync();
          future.addListener(
              () -> {
                try {
                  if (future.isDone()) {
                    latch.countDown();
                  }
                  serviceStartController = future.get();
                } catch (ExecutionException | InterruptedException e) {
                  throw new RuntimeException(e);
                }
              },
              MoreExecutors.directExecutor());
        });
    assertThat(latch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  void connectAndWait(Bundle rootHints) throws Exception {
    handler.postAndSync(
        () -> {
          // Make browser's internal handler to be initialized with test thread.
          browserCompat =
              new MediaBrowserCompat(context, getServiceComponent(), connectionCallback, rootHints);
        });
    browserCompat.connect();
    assertThat(connectionCallback.connectedLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS))
        .isTrue();
  }

  @Test
  public void connect() throws Exception {
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    assertThat(connectionCallback.failedLatch.getCount()).isNotEqualTo(0);
  }

  @Ignore
  @Test
  public void connect_rejected() throws InterruptedException {
    // TODO: Connect the browser to the session service whose onConnect() returns null.
    assertThat(connectionCallback.failedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(connectionCallback.connectedLatch.getCount()).isNotEqualTo(0);
  }

  @Test
  public void getSessionToken() throws Exception {
    connectAndWait(/* rootHints= */ Bundle.EMPTY);
    MediaControllerCompat controller =
        new MediaControllerCompat(context, browserCompat.getSessionToken());
    assertThat(controller.getPackageName())
        .isEqualTo(browserCompat.getServiceComponent().getPackageName());
  }

  class TestConnectionCallback extends MediaBrowserCompat.ConnectionCallback {
    public final CountDownLatch connectedLatch = new CountDownLatch(1);
    public final CountDownLatch suspendedLatch = new CountDownLatch(1);
    public final CountDownLatch failedLatch = new CountDownLatch(1);

    @Nullable MediaControllerCompat.Callback controllerCompatCallback;

    TestConnectionCallback() {
      super();
    }

    @Override
    public void onConnected() {
      super.onConnected();
      controllerCompat = new MediaControllerCompat(context, browserCompat.getSessionToken());
      controllerCompatCallback =
          new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
              reportedPlaybackStatesCompat.add(state);
              firstPlaybackStateCompatReported.countDown();
            }
          };
      controllerCompat.registerCallback(controllerCompatCallback);
      connectedLatch.countDown();
    }

    @Override
    public void onConnectionSuspended() {
      super.onConnectionSuspended();
      unregisterControllerCallback();
      suspendedLatch.countDown();
    }

    @Override
    public void onConnectionFailed() {
      super.onConnectionFailed();
      unregisterControllerCallback();
      failedLatch.countDown();
    }

    private void unregisterControllerCallback() {
      if (controllerCompat != null && controllerCompatCallback != null) {
        controllerCompat.unregisterCallback(controllerCompatCallback);
      }
      controllerCompatCallback = null;
    }
  }
}
