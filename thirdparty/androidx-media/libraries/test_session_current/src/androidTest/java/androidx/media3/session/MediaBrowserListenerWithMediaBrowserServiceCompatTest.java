/*
 * Copyright 2020 The Android Open Source Project
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

import static androidx.media3.session.LibraryResult.RESULT_SUCCESS;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;
import static androidx.media3.session.MediaConstants.EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY;
import static androidx.media3.session.MockMediaBrowserServiceCompat.EXTRAS_KEY_SEND_ROOT_HINTS_AS_SESSION_EXTRAS;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA_BROWSER_SERVICE_COMPAT;
import static androidx.media3.test.session.common.MediaBrowserConstants.PARENT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_KEY;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS_VALUE;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_CONNECT_REJECTED;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_FATAL_AUTHENTICATION_ERROR;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_INCREASE_NUMBER_OF_CHILDREN_WITH_EACH_CALL;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_NON_FATAL_AUTHENTICATION_ERROR;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_WITH_NULL_LIST;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_LIBRARY_ROOT;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_SEND_CUSTOM_COMMAND;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_SUBSCRIBE_THEN_REJECT_ON_LOAD_CHILDREN;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MediaBrowserConstants;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaBrowser.Listener} with {@link MediaBrowserServiceCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaBrowserListenerWithMediaBrowserServiceCompatTest {

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaBrowserListenerTestWithMediaBrowserServiceCompat");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaBrowserServiceCompat remoteService;

  private MediaBrowser createBrowser(@Nullable MediaBrowser.Listener listener) throws Exception {
    return createBrowser(
        /* connectionHints= */ Bundle.EMPTY, /* maxCommandsForMediaItems= */ 0, listener);
  }

  private MediaBrowser createBrowser(
      Bundle connectionHints,
      int maxCommandsForMediaItems,
      @Nullable MediaBrowser.Listener listener)
      throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA_BROWSER_SERVICE_COMPAT);
    return (MediaBrowser)
        controllerTestRule.createController(
            token,
            connectionHints,
            listener,
            /* controllerCreationListener= */ null,
            maxCommandsForMediaItems);
  }

  @Before
  public void setUp() {
    controllerTestRule.setControllerType(MediaBrowser.class);
    context = ApplicationProvider.getApplicationContext();
    remoteService = new RemoteMediaBrowserServiceCompat(context);
  }

  @After
  public void cleanUp() throws Exception {
    remoteService.release();
  }

  @Test
  public void connect() throws Exception {
    createBrowser(/* listener= */ null);
    // If connection failed, exception will be thrown inside of #createBrowser().
  }

  @Test
  public void connect_rejected() throws Exception {
    remoteService.setProxyForTest(TEST_CONNECT_REJECTED);

    ExecutionException thrown =
        assertThrows(ExecutionException.class, () -> createBrowser(/* listener= */ null));
    assertThat(thrown).hasCauseThat().isInstanceOf(SecurityException.class);
  }

  @Test
  public void connect_useConnectionHints_connectionHintsPassedToLegacyServerOnGetRootAsRootHints()
      throws Exception {
    remoteService.setProxyForTest(TEST_GET_CHILDREN);
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(EXTRAS_KEY_SEND_ROOT_HINTS_AS_SESSION_EXTRAS, true);
    CountDownLatch latch = new CountDownLatch(/* count= */ 1);
    AtomicReference<Bundle> extrasRef = new AtomicReference<>();
    createBrowser(
        connectionHints,
        /* maxCommandsForMediaItems= */ 0,
        /* listener= */ new MediaBrowser.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            extrasRef.set(extras);
            latch.countDown();
          }
        });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(
            extrasRef
                .get()
                .getBoolean(
                    EXTRAS_KEY_SEND_ROOT_HINTS_AS_SESSION_EXTRAS, /* defaultValue= */ false))
        .isTrue();
  }

  @Test
  public void getLibraryRoot_browseActionsAvailable() throws Exception {
    remoteService.setProxyForTest(TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS);
    CommandButton playlistAddButton =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Add to playlist")
            .setIconUri(Uri.parse("content://playlist_add"))
            .setSessionCommand(
                new SessionCommand(MediaBrowserConstants.COMMAND_PLAYLIST_ADD, Bundle.EMPTY))
            .build();
    CommandButton radioButton =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Radio station")
            .setIconUri(Uri.parse("content://radio"))
            .setSessionCommand(
                new SessionCommand(MediaBrowserConstants.COMMAND_RADIO, Bundle.EMPTY))
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaId("mediaId")
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setSupportedCommands(
                        ImmutableList.of(
                            MediaBrowserConstants.COMMAND_PLAYLIST_ADD,
                            MediaBrowserConstants.COMMAND_RADIO,
                            "invalid"))
                    .build())
            .build();
    MediaBrowser mediaBrowser =
        createBrowser(Bundle.EMPTY, /* maxCommandsForMediaItems= */ 2, /* listener= */ null);
    // When connected to a legacy browser service, the library root needs to be requested
    // before media item commands are available.
    LibraryResult<MediaItem> libraryResult =
        threadTestRule
            .getHandler()
            .postAndSync(() -> mediaBrowser.getLibraryRoot(new LibraryParams.Builder().build()))
            .get();
    assertThat(libraryResult.resultCode).isEqualTo(RESULT_SUCCESS);

    ImmutableList<CommandButton> commandButtons =
        mediaBrowser.getCommandButtonsForMediaItem(mediaItem);

    assertThat(commandButtons).containsExactly(playlistAddButton, radioButton).inOrder();
    assertThat(commandButtons.get(0).extras.getString("key-1")).isEqualTo("playlist_add");
    assertThat(commandButtons.get(1).extras.getString("key-1")).isEqualTo("radio");
  }

  @Test
  public void getItem_supportedCommandActions_convertedCorrectly() throws Exception {
    remoteService.setProxyForTest(TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS);
    MediaBrowser mediaBrowser =
        createBrowser(Bundle.EMPTY, /* maxCommandsForMediaItems= */ 1, /* listener= */ null);
    CommandButton playlistAddButton =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Add to playlist")
            .setIconUri(Uri.parse("content://playlist_add"))
            .setSessionCommand(
                new SessionCommand(MediaBrowserConstants.COMMAND_PLAYLIST_ADD, Bundle.EMPTY))
            .build();
    // When connected to a legacy browser service, the library root needs to be requested
    // before media item commands are available.
    LibraryResult<MediaItem> libraryResult =
        threadTestRule
            .getHandler()
            .postAndSync(() -> mediaBrowser.getLibraryRoot(new LibraryParams.Builder().build()))
            .get();
    assertThat(libraryResult.resultCode).isEqualTo(RESULT_SUCCESS);
    MediaItem mediaItem =
        threadTestRule.getHandler().postAndSync(() -> mediaBrowser.getItem("mediaId")).get().value;

    ImmutableList<CommandButton> commandButtons =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> mediaBrowser.getCommandButtonsForMediaItem(requireNonNull(mediaItem)));

    assertThat(commandButtons).containsExactly(playlistAddButton);
    assertThat(commandButtons.get(0).extras.getString("key-1")).isEqualTo("playlist_add");
  }

  @Test
  public void sendCustomCommandWithMediaItem_mediaItemIdConvertedCorrectly() throws Exception {
    remoteService.setProxyForTest(TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS);
    MediaBrowser mediaBrowser =
        createBrowser(
            /* connectionHints= */ Bundle.EMPTY,
            /* maxCommandsForMediaItems= */ 2,
            /* listener= */ null);
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaIdFromCommand").build();
    // When connected to a legacy browser service, the library root needs to be requested
    // before media item commands are available.
    LibraryResult<MediaItem> libraryRootResult =
        threadTestRule
            .getHandler()
            .postAndSync(() -> mediaBrowser.getLibraryRoot(new LibraryParams.Builder().build()))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(libraryRootResult.resultCode).isEqualTo(RESULT_SUCCESS);

    SessionResult sessionResult =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    mediaBrowser.sendCustomCommand(
                        new SessionCommand(MediaBrowserConstants.COMMAND_RADIO, Bundle.EMPTY),
                        mediaItem,
                        /* args= */ Bundle.EMPTY))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(sessionResult.extras.getString(MediaConstants.EXTRA_KEY_MEDIA_ID))
        .isEqualTo("mediaIdFromCommand");
  }

  @Test
  public void sendCustomCommandWithMediaItem_commandButtonNotAvailable_permissionDenied()
      throws Exception {
    remoteService.setProxyForTest(TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS);
    MediaBrowser mediaBrowser =
        createBrowser(
            /* connectionHints= */ Bundle.EMPTY,
            /* maxCommandsForMediaItems= */ 0,
            /* listener= */ null);
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaIdFromCommand").build();
    // When connected to a legacy browser service, the library root needs to be requested
    // before media item commands are available.
    LibraryResult<MediaItem> libraryRootResult =
        threadTestRule
            .getHandler()
            .postAndSync(() -> mediaBrowser.getLibraryRoot(new LibraryParams.Builder().build()))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(libraryRootResult.resultCode).isEqualTo(RESULT_SUCCESS);

    SessionResult sessionResult =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    mediaBrowser.sendCustomCommand(
                        new SessionCommand(MediaBrowserConstants.COMMAND_RADIO, Bundle.EMPTY),
                        mediaItem,
                        /* args= */ Bundle.EMPTY))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(sessionResult.resultCode).isEqualTo(SessionResult.RESULT_ERROR_PERMISSION_DENIED);
  }

  @Test
  public void onChildrenChanged_subscribeAndUnsubscribe() throws Exception {
    String testParentId = "testOnChildrenChanged";
    CountDownLatch latch = new CountDownLatch(2);
    MediaBrowser.Listener listener =
        new MediaBrowser.Listener() {
          @Override
          public void onChildrenChanged(
              MediaBrowser browser,
              String parentId,
              int itemCount,
              @Nullable LibraryParams params) {
            // Triggered by both subscribe and notifyChildrenChanged().
            // Shouldn't be called after the unsubscribe().
            assertThat(latch.getCount()).isNotEqualTo(0);
            assertThat(parentId).isEqualTo(testParentId);
            assertThat(params).isNull();
            latch.countDown();
          }
        };

    remoteService.setProxyForTest(TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE);
    MediaBrowser browser = createBrowser(listener);

    LibraryResult<Void> resultForSubscribe =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.subscribe(testParentId, null))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(resultForSubscribe.resultCode).isEqualTo(RESULT_SUCCESS);
    remoteService.notifyChildrenChanged(testParentId);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    LibraryResult<Void> resultForUnsubscribe =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.unsubscribe(testParentId))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(resultForUnsubscribe.resultCode).isEqualTo(RESULT_SUCCESS);
    // Unsubscribe takes some time. Wait for some time.
    Thread.sleep(TIMEOUT_MS);
    remoteService.notifyChildrenChanged(testParentId);
    // This shouldn't trigger browser's onChildrenChanged().
    // Wait for some time. Exception will be thrown in the listener if error happens.
    Thread.sleep(TIMEOUT_MS);
  }

  @Test
  public void onChildrenChanged_withNullChildrenListInLegacyService_convertedToSessionError()
      throws Exception {
    String testParentId = TEST_GET_CHILDREN_WITH_NULL_LIST;
    remoteService.setProxyForTest(TEST_GET_CHILDREN_WITH_NULL_LIST);
    MediaBrowser browser = createBrowser(/* listener= */ null);

    LibraryResult<Void> resultForSubscribe =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.subscribe(testParentId, null))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(resultForSubscribe.resultCode).isEqualTo(SessionError.ERROR_UNKNOWN);
    assertThat(resultForSubscribe.sessionError.code).isEqualTo(SessionError.ERROR_UNKNOWN);
    assertThat(resultForSubscribe.sessionError.message)
        .isEqualTo(SessionError.DEFAULT_ERROR_MESSAGE);
  }

  @Test
  public void onChildrenChanged_cacheChildrenOfSubscribeCall_serviceCalledOnceOnly()
      throws Exception {
    String testParentId = TEST_GET_CHILDREN_INCREASE_NUMBER_OF_CHILDREN_WITH_EACH_CALL;
    remoteService.setProxyForTest(TEST_GET_CHILDREN_INCREASE_NUMBER_OF_CHILDREN_WITH_EACH_CALL);
    CountDownLatch latch = new CountDownLatch(/* count= */ 1);
    MediaBrowser browser =
        createBrowser(
            new MediaBrowser.Listener() {
              @Override
              public void onChildrenChanged(
                  MediaBrowser browser,
                  String parentId,
                  int itemCount,
                  @Nullable LibraryParams params) {
                latch.countDown();
              }
            });
    // Subscribing causes the first call to the legacy `onLoadChildren()` that we want to cache.
    LibraryResult<Void> resultForSubscribe =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.subscribe(testParentId, null))
            .get(TIMEOUT_MS, MILLISECONDS);
    assertThat(resultForSubscribe.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    LibraryResult<ImmutableList<MediaItem>> resultGetChildren =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    browser.getChildren(
                        testParentId, /* page= */ 0, /* pageSize= */ 12, /* params= */ null))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(resultGetChildren.resultCode).isEqualTo(RESULT_SUCCESS);
    // If caching in `MediaBrowserImplLegacy` doesn't work, the children would be delivered by a
    // second call to the service which would have two items.
    assertThat(resultGetChildren.value).hasSize(1);

    // Cache is cleared after delivery. We call the service again that now delivers two items.
    LibraryResult<ImmutableList<MediaItem>> resultGetChildrenAgain =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    browser.getChildren(
                        testParentId, /* page= */ 0, /* pageSize= */ 12, /* params= */ null))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(resultGetChildrenAgain.value).hasSize(2);
  }

  @Test
  public void getLibraryRoot_correctExtraKeyAndValue() throws Exception {
    remoteService.setProxyForTest(TEST_GET_LIBRARY_ROOT);
    MediaBrowser browser = createBrowser(/* listener= */ null);

    LibraryResult<MediaItem> resultForLibraryRoot =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.getLibraryRoot(new LibraryParams.Builder().build()))
            .get(TIMEOUT_MS, MILLISECONDS);

    Bundle extras = resultForLibraryRoot.params.extras;
    assertThat(extras.getInt(ROOT_EXTRAS_KEY, ROOT_EXTRAS_VALUE + 1)).isEqualTo(ROOT_EXTRAS_VALUE);
  }

  @Test
  public void getLibraryRoot_browsableRootChildrenOnly_receivesRootWithBrowsableChildrenOnly()
      throws Exception {
    remoteService.setProxyForTest(TEST_GET_LIBRARY_ROOT);
    MediaBrowser browser = createBrowser(/* listener= */ null);

    LibraryResult<MediaItem> resultForLibraryRoot =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  Bundle extras = new Bundle();
                  extras.putBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, true);
                  return browser.getLibraryRoot(
                      new LibraryParams.Builder().setExtras(extras).build());
                })
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(resultForLibraryRoot.value.mediaId)
        .isEqualTo(ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY);
  }

  @Test
  public void getLibraryRoot_browsableRootChildrenOnlyFalse_receivesDefaultRoot() throws Exception {
    remoteService.setProxyForTest(TEST_GET_LIBRARY_ROOT);
    MediaBrowser browser = createBrowser(/* listener= */ null);

    LibraryResult<MediaItem> resultForLibraryRoot =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  Bundle extras = new Bundle();
                  extras.putBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, false);
                  return browser.getLibraryRoot(
                      new LibraryParams.Builder().setExtras(extras).build());
                })
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(resultForLibraryRoot.value.mediaId).isEqualTo(ROOT_ID);
  }

  @Test
  public void getChildren_correctMetadataExtras() throws Exception {
    LibraryParams params = MediaTestUtils.createLibraryParams();
    remoteService.setProxyForTest(TEST_GET_CHILDREN);
    MediaBrowser browser = createBrowser(/* listener= */ null);

    LibraryResult<ImmutableList<MediaItem>> libraryResult =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> browser.getChildren(PARENT_ID, /* page= */ 4, /* pageSize= */ 10, params))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(libraryResult.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(libraryResult.value).hasSize(MockMediaBrowserServiceCompat.MEDIA_ITEMS.size());
    for (int i = 0; i < libraryResult.value.size(); i++) {
      int status =
          libraryResult
              .value
              .get(i)
              .mediaMetadata
              .extras
              .getInt(
                  EXTRAS_KEY_COMPLETION_STATUS,
                  /* defaultValue= */ EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED + 1);
      int expectedStatus =
          MockMediaBrowserServiceCompat.MEDIA_ITEMS
              .get(i)
              .getDescription()
              .getExtras()
              .getInt(
                  EXTRAS_KEY_COMPLETION_STATUS,
                  /* defaultValue= */ EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED + 1);
      assertThat(status).isEqualTo(expectedStatus);
    }
  }

  @Test
  public void getChildren_fatalAuthenticationErrorOfLegacySessionApp_receivesPlaybackException()
      throws Exception {
    remoteService.setProxyForTest(TEST_GET_CHILDREN_FATAL_AUTHENTICATION_ERROR);
    MediaBrowser browser = createBrowser(/* listener= */ null);
    List<PlaybackException> playbackExceptions = new ArrayList<>();
    CountDownLatch playbackErrorLatch = new CountDownLatch(1);
    browser.addListener(
        new Player.Listener() {
          @Override
          public void onPlayerError(PlaybackException error) {
            playbackExceptions.add(error);
            playbackErrorLatch.countDown();
          }
        });

    LibraryResult<ImmutableList<MediaItem>> libraryResult =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    browser.getChildren(
                        PARENT_ID, /* page= */ 4, /* pageSize= */ 10, /* params= */ null))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(playbackErrorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(libraryResult.sessionError.code).isEqualTo(SessionError.ERROR_UNKNOWN);
    assertThat(playbackExceptions).hasSize(1);
    assertThat(playbackExceptions.get(0).errorCode)
        .isEqualTo(PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED);
  }

  @Test
  public void getChildren_nonFatalAuthenticationErrorOfLegacySessionApp_receivesSessionError()
      throws Exception {
    remoteService.setProxyForTest(TEST_GET_CHILDREN_NON_FATAL_AUTHENTICATION_ERROR);
    List<SessionError> sessionErrors = new ArrayList<>();
    CountDownLatch sessionErrorLatch = new CountDownLatch(1);
    MediaBrowser browser =
        createBrowser(
            /* listener= */ new MediaBrowser.Listener() {
              @Override
              public void onError(MediaController controller, SessionError sessionError) {
                sessionErrors.add(sessionError);
                sessionErrorLatch.countDown();
              }
            });

    LibraryResult<ImmutableList<MediaItem>> libraryResult =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    browser.getChildren(
                        PARENT_ID, /* page= */ 4, /* pageSize= */ 10, /* params= */ null))
            .get(TIMEOUT_MS, MILLISECONDS);

    assertThat(sessionErrorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(libraryResult.sessionError).isNull();
    assertThat(libraryResult.value).hasSize(1);
    assertThat(libraryResult.value.get(0).mediaId).isEqualTo("mediaId");
    assertThat(sessionErrors).hasSize(1);
    assertThat(sessionErrors.get(0).code)
        .isEqualTo(PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED);
  }

  @Test
  public void sendCustomCommand_success_correctAsyncResult() throws Exception {
    remoteService.setProxyForTest(TEST_SEND_CUSTOM_COMMAND);
    MediaBrowser browser = createBrowser(/* listener= */ null);
    CountDownLatch latch = new CountDownLatch(/* count= */ 1);
    AtomicReference<SessionResult> sessionResultRef = new AtomicReference<>();

    ListenableFuture<SessionResult> resultFuture =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    browser.sendCustomCommand(
                        new SessionCommand(
                            MediaBrowserConstants.COMMAND_PLAYLIST_ADD, /* extras= */ Bundle.EMPTY),
                        /* args= */ Bundle.EMPTY));
    Futures.addCallback(
        resultFuture,
        new FutureCallback<SessionResult>() {
          @Override
          public void onSuccess(SessionResult result) {
            sessionResultRef.set(result);
            latch.countDown();
          }

          @Override
          public void onFailure(Throwable t) {}
        },
        directExecutor());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(sessionResultRef.get()).isNotNull();
    assertThat(sessionResultRef.get().resultCode).isEqualTo(SessionResult.RESULT_SUCCESS);
    assertThat(sessionResultRef.get().extras.getString("key-1")).isEqualTo("success-from-service");
  }

  @Test
  public void sendCustomCommand_failure_correctAsyncResult() throws Exception {
    remoteService.setProxyForTest(TEST_SEND_CUSTOM_COMMAND);
    MediaBrowser browser = createBrowser(/* listener= */ null);
    CountDownLatch latch = new CountDownLatch(/* count= */ 1);
    AtomicReference<SessionResult> sessionResultRef = new AtomicReference<>();
    Bundle args = new Bundle();
    args.putBoolean("request_error", true);

    ListenableFuture<SessionResult> resultFuture =
        threadTestRule
            .getHandler()
            .postAndSync(
                () ->
                    browser.sendCustomCommand(
                        new SessionCommand(
                            MediaBrowserConstants.COMMAND_PLAYLIST_ADD, /* extras= */ Bundle.EMPTY),
                        args));
    Futures.addCallback(
        resultFuture,
        new FutureCallback<SessionResult>() {
          @Override
          public void onSuccess(SessionResult result) {
            sessionResultRef.set(result);
            latch.countDown();
          }

          @Override
          public void onFailure(Throwable t) {}
        },
        directExecutor());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(sessionResultRef.get()).isNotNull();
    assertThat(sessionResultRef.get().resultCode).isEqualTo(SessionResult.RESULT_ERROR_UNKNOWN);
    assertThat(sessionResultRef.get().extras.getString("key-1")).isEqualTo("error-from-service");
  }

  @Test
  public void
      subscribe_thenNullListOnLoadChildren_exceptionConvertedToOnChildrenChangedIntegerMaxValue()
          throws Exception {
    remoteService.setProxyForTest(TEST_SUBSCRIBE_THEN_REJECT_ON_LOAD_CHILDREN);
    CountDownLatch onChildrenChangedLatch = new CountDownLatch(2);
    AtomicBoolean onErrorCalled = new AtomicBoolean();
    List<String> changedParentIds = new ArrayList<>();
    List<Integer> changedItemCounts = new ArrayList<>();
    MediaBrowser browser =
        createBrowser(
            new MediaBrowser.Listener() {
              @Override
              public void onChildrenChanged(
                  MediaBrowser browser,
                  String parentId,
                  int itemCount,
                  @Nullable LibraryParams params) {
                changedParentIds.add(parentId);
                changedItemCounts.add(itemCount);
                onChildrenChangedLatch.countDown();
              }

              @Override
              public void onError(MediaController controller, SessionError sessionError) {
                onErrorCalled.set(true);
              }
            });

    LibraryResult<Void> result =
        threadTestRule
            .getHandler()
            .postAndSync(() -> browser.subscribe("parentId", new LibraryParams.Builder().build()))
            .get();
    // Trigger calling onLoadChildren that is rejected.
    remoteService.notifyChildrenChanged("parentId");

    assertThat(result.resultCode).isEqualTo(RESULT_SUCCESS);
    assertThat(onChildrenChangedLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(changedParentIds).containsExactly("parentId", "parentId");
    assertThat(changedItemCounts).containsExactly(2, Integer.MAX_VALUE).inOrder();
    assertThat(onErrorCalled.get()).isFalse();
  }
}
