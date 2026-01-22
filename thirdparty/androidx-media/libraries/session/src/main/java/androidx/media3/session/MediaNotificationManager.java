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
 * limitations under the License
 */
package androidx.media3.session;

import static android.app.Service.STOP_FOREGROUND_DETACH;
import static android.app.Service.STOP_FOREGROUND_REMOVE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Manages media notifications for a {@link MediaSessionService} and sets the service as
 * foreground/background according to the player state.
 *
 * <p>All methods must be called on the main thread.
 */
/* package */ final class MediaNotificationManager implements Handler.Callback {

  private static final String TAG = "MediaNtfMng";
  private static final int MSG_USER_ENGAGED_TIMEOUT = 1;

  private final MediaSessionService mediaSessionService;

  private final MediaNotification.ActionFactory actionFactory;
  private final NotificationManagerCompat notificationManagerCompat;
  private final Handler mainHandler;
  private final Executor mainExecutor;
  private final Intent startSelfIntent;
  private final Map<MediaSession, ControllerInfo> controllerMap;

  private MediaNotification.Provider mediaNotificationProvider;
  private int totalNotificationCount;
  @Nullable private MediaNotification mediaNotification;
  private boolean startedInForeground;
  private boolean isUserEngaged;
  private boolean isUserEngagedTimeoutEnabled;
  private long userEngagedTimeoutMs;

  public MediaNotificationManager(
      MediaSessionService mediaSessionService,
      MediaNotification.Provider mediaNotificationProvider,
      MediaNotification.ActionFactory actionFactory) {
    this.mediaSessionService = mediaSessionService;
    this.mediaNotificationProvider = mediaNotificationProvider;
    this.actionFactory = actionFactory;
    notificationManagerCompat = NotificationManagerCompat.from(mediaSessionService);
    mainHandler = Util.createHandler(Looper.getMainLooper(), /* callback= */ this);
    mainExecutor = (runnable) -> Util.postOrRun(mainHandler, runnable);
    startSelfIntent = new Intent(mediaSessionService, mediaSessionService.getClass());
    controllerMap = new HashMap<>();
    startedInForeground = false;
    isUserEngagedTimeoutEnabled = true;
    userEngagedTimeoutMs = MediaSessionService.DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS;
  }

  public void addSession(MediaSession session) {
    if (controllerMap.containsKey(session)) {
      return;
    }
    MediaControllerListener listener = new MediaControllerListener(mediaSessionService, session);
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    ListenableFuture<MediaController> controllerFuture =
        new MediaController.Builder(mediaSessionService, session.getToken())
            .setConnectionHints(connectionHints)
            .setListener(listener)
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync();
    controllerMap.put(session, new ControllerInfo(controllerFuture));
    controllerFuture.addListener(
        () -> {
          try {
            MediaController controller = controllerFuture.get(/* time= */ 0, MILLISECONDS);
            listener.onConnected(shouldShowNotification(session));
            controller.addListener(listener);
          } catch (CancellationException
              | ExecutionException
              | InterruptedException
              | TimeoutException e) {
            // MediaSession or MediaController is released too early. Stop monitoring the session.
            mediaSessionService.removeSession(session);
          }
        },
        mainExecutor);
  }

  public void removeSession(MediaSession session) {
    @Nullable ControllerInfo controllerInfo = controllerMap.remove(session);
    if (controllerInfo != null) {
      MediaController.releaseFuture(controllerInfo.controllerFuture);
    }
  }

  public void onCustomAction(MediaSession session, String action, Bundle extras) {
    @Nullable MediaController mediaController = getConnectedControllerForSession(session);
    if (mediaController == null) {
      return;
    }
    // Let the notification provider handle the command first before forwarding it directly.
    Util.postOrRun(
        new Handler(session.getPlayer().getApplicationLooper()),
        () -> {
          if (!mediaNotificationProvider.handleCustomCommand(session, action, extras)) {
            mainExecutor.execute(
                () -> sendCustomCommandIfCommandIsAvailable(mediaController, action, extras));
          }
        });
  }

  /**
   * Updates the media notification provider.
   *
   * @param mediaNotificationProvider The {@link MediaNotification.Provider}.
   */
  public void setMediaNotificationProvider(MediaNotification.Provider mediaNotificationProvider) {
    this.mediaNotificationProvider = mediaNotificationProvider;
  }

  /**
   * Updates the notification.
   *
   * @param session A session that needs notification update.
   * @param startInForegroundRequired Whether the service is required to start in the foreground.
   */
  public void updateNotification(MediaSession session, boolean startInForegroundRequired) {
    if (!mediaSessionService.isSessionAdded(session) || !shouldShowNotification(session)) {
      removeNotification();
      return;
    }

    int notificationSequence = ++totalNotificationCount;
    ImmutableList<CommandButton> mediaButtonPreferences =
        checkNotNull(getConnectedControllerForSession(session)).getMediaButtonPreferences();
    MediaNotification.Provider.Callback callback =
        notification ->
            mainExecutor.execute(
                () -> onNotificationUpdated(notificationSequence, session, notification));
    Util.postOrRun(
        new Handler(session.getPlayer().getApplicationLooper()),
        () -> {
          MediaNotification mediaNotification =
              this.mediaNotificationProvider.createNotification(
                  session, mediaButtonPreferences, actionFactory, callback);
          mainExecutor.execute(
              () ->
                  updateNotificationInternal(
                      session, mediaNotification, startInForegroundRequired));
        });
  }

  public boolean isStartedInForeground() {
    return startedInForeground;
  }

  public void setUserEngagedTimeoutMs(long userEngagedTimeoutMs) {
    this.userEngagedTimeoutMs = userEngagedTimeoutMs;
  }

  @Override
  public boolean handleMessage(Message msg) {
    if (msg.what == MSG_USER_ENGAGED_TIMEOUT) {
      List<MediaSession> sessions = mediaSessionService.getSessions();
      for (int i = 0; i < sessions.size(); i++) {
        mediaSessionService.onUpdateNotificationInternal(
            sessions.get(i), /* startInForegroundWhenPaused= */ false);
      }
      return true;
    }
    return false;
  }

  /* package */ boolean shouldRunInForeground(boolean startInForegroundWhenPaused) {
    boolean isUserEngaged = isAnySessionUserEngaged(startInForegroundWhenPaused);
    boolean useTimeout = isUserEngagedTimeoutEnabled && userEngagedTimeoutMs > 0;
    if (this.isUserEngaged && !isUserEngaged && useTimeout) {
      mainHandler.sendEmptyMessageDelayed(MSG_USER_ENGAGED_TIMEOUT, userEngagedTimeoutMs);
    } else if (isUserEngaged) {
      mainHandler.removeMessages(MSG_USER_ENGAGED_TIMEOUT);
    }
    this.isUserEngaged = isUserEngaged;
    boolean hasPendingTimeout = mainHandler.hasMessages(MSG_USER_ENGAGED_TIMEOUT);
    return isUserEngaged || hasPendingTimeout;
  }

  private boolean isAnySessionUserEngaged(boolean startInForegroundWhenPaused) {
    List<MediaSession> sessions = mediaSessionService.getSessions();
    for (int i = 0; i < sessions.size(); i++) {
      @Nullable MediaController controller = getConnectedControllerForSession(sessions.get(i));
      if (controller != null
          && (controller.getPlayWhenReady() || startInForegroundWhenPaused)
          && (controller.getPlaybackState() == Player.STATE_READY
              || controller.getPlaybackState() == Player.STATE_BUFFERING)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Permanently disable the user engaged timeout, which is needed to immediately stop the
   * foreground service.
   */
  /* package */ void disableUserEngagedTimeout() {
    isUserEngagedTimeoutEnabled = false;
    if (mainHandler.hasMessages(MSG_USER_ENGAGED_TIMEOUT)) {
      mainHandler.removeMessages(MSG_USER_ENGAGED_TIMEOUT);
      List<MediaSession> sessions = mediaSessionService.getSessions();
      for (int i = 0; i < sessions.size(); i++) {
        mediaSessionService.onUpdateNotificationInternal(
            sessions.get(i), /* startInForegroundWhenPaused= */ false);
      }
    }
  }

  private void onNotificationUpdated(
      int notificationSequence, MediaSession session, MediaNotification mediaNotification) {
    if (notificationSequence == totalNotificationCount) {
      boolean startInForegroundRequired =
          shouldRunInForeground(/* startInForegroundWhenPaused= */ false);
      updateNotificationInternal(session, mediaNotification, startInForegroundRequired);
    }
  }

  private void onNotificationDismissed(MediaSession session) {
    @Nullable ControllerInfo controllerInfo = controllerMap.get(session);
    if (controllerInfo != null) {
      controllerInfo.wasNotificationDismissed = true;
    }
  }

  // POST_NOTIFICATIONS permission is not required for media session related notifications.
  // https://developer.android.com/develop/ui/views/notifications/notification-permission#exemptions-media-sessions
  @SuppressLint("MissingPermission")
  private void updateNotificationInternal(
      MediaSession session,
      MediaNotification mediaNotification,
      boolean startInForegroundRequired) {
    // Call Notification.MediaStyle#setMediaSession() indirectly.
    android.media.session.MediaSession.Token fwkToken =
        (android.media.session.MediaSession.Token)
            session.getSessionCompat().getSessionToken().getToken();
    mediaNotification.notification.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, fwkToken);
    this.mediaNotification = mediaNotification;
    if (startInForegroundRequired) {
      startForeground(mediaNotification);
    } else {
      // Notification manager has to be updated first to avoid missing updates
      // (https://github.com/androidx/media/issues/192).
      notificationManagerCompat.notify(
          mediaNotification.notificationId, mediaNotification.notification);
      stopForeground(/* removeNotifications= */ false);
    }
  }

  /** Removes the notification and stops the foreground service if running. */
  private void removeNotification() {
    // To hide the notification on all API levels, we need to call both Service.stopForeground(true)
    // and notificationManagerCompat.cancel(notificationId).
    stopForeground(/* removeNotifications= */ true);
    if (mediaNotification != null) {
      notificationManagerCompat.cancel(mediaNotification.notificationId);
      // Update the notification count so that if a pending notification callback arrives (e.g., a
      // bitmap is loaded), we don't show the notification.
      totalNotificationCount++;
      mediaNotification = null;
    }
  }

  private boolean shouldShowNotification(MediaSession session) {
    MediaController controller = getConnectedControllerForSession(session);
    if (controller == null || controller.getCurrentTimeline().isEmpty()) {
      return false;
    }
    ControllerInfo controllerInfo = checkNotNull(controllerMap.get(session));
    if (controller.getPlaybackState() != Player.STATE_IDLE) {
      // Playback restarted, reset previous notification dismissed flag.
      controllerInfo.wasNotificationDismissed = false;
    }
    return !controllerInfo.wasNotificationDismissed;
  }

  @Nullable
  private MediaController getConnectedControllerForSession(MediaSession session) {
    @Nullable ControllerInfo controllerInfo = controllerMap.get(session);
    if (controllerInfo == null || !controllerInfo.controllerFuture.isDone()) {
      return null;
    }
    try {
      return Futures.getDone(controllerInfo.controllerFuture);
    } catch (ExecutionException exception) {
      // We should never reach this.
      throw new IllegalStateException(exception);
    }
  }

  private void sendCustomCommandIfCommandIsAvailable(
      MediaController mediaController, String action, Bundle extras) {
    @Nullable SessionCommand customCommand = null;
    for (SessionCommand command : mediaController.getAvailableSessionCommands().commands) {
      if (command.commandCode == SessionCommand.COMMAND_CODE_CUSTOM
          && command.customAction.equals(action)) {
        customCommand = command;
        break;
      }
    }
    if (customCommand != null
        && mediaController.getAvailableSessionCommands().contains(customCommand)) {
      ListenableFuture<SessionResult> future =
          mediaController.sendCustomCommand(
              new SessionCommand(action, extras), /* args= */ Bundle.EMPTY);
      Futures.addCallback(
          future,
          new FutureCallback<SessionResult>() {
            @Override
            public void onSuccess(SessionResult result) {
              // Do nothing.
            }

            @Override
            public void onFailure(Throwable t) {
              Log.w(TAG, "custom command " + action + " produced an error: " + t.getMessage(), t);
            }
          },
          MoreExecutors.directExecutor());
    }
  }

  private final class MediaControllerListener implements MediaController.Listener, Player.Listener {
    private final MediaSessionService mediaSessionService;
    private final MediaSession session;

    public MediaControllerListener(MediaSessionService mediaSessionService, MediaSession session) {
      this.mediaSessionService = mediaSessionService;
      this.session = session;
    }

    public void onConnected(boolean shouldShowNotification) {
      if (shouldShowNotification) {
        mediaSessionService.onUpdateNotificationInternal(
            session, /* startInForegroundWhenPaused= */ false);
      }
    }

    @Override
    public void onMediaButtonPreferencesChanged(
        MediaController controller, List<CommandButton> mediaButtonPreferences) {
      mediaSessionService.onUpdateNotificationInternal(
          session, /* startInForegroundWhenPaused= */ false);
    }

    @Override
    public void onAvailableSessionCommandsChanged(
        MediaController controller, SessionCommands commands) {
      mediaSessionService.onUpdateNotificationInternal(
          session, /* startInForegroundWhenPaused= */ false);
    }

    @Override
    public ListenableFuture<SessionResult> onCustomCommand(
        MediaController controller, SessionCommand command, Bundle args) {
      @SessionResult.Code int resultCode = SessionError.ERROR_NOT_SUPPORTED;
      if (command.customAction.equals(MediaNotification.NOTIFICATION_DISMISSED_EVENT_KEY)) {
        onNotificationDismissed(session);
        resultCode = SessionResult.RESULT_SUCCESS;
      }
      return Futures.immediateFuture(new SessionResult(resultCode));
    }

    @Override
    public void onDisconnected(MediaController controller) {
      if (mediaSessionService.isSessionAdded(session)) {
        mediaSessionService.removeSession(session);
      }
      // We may need to hide the notification.
      mediaSessionService.onUpdateNotificationInternal(
          session, /* startInForegroundWhenPaused= */ false);
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
      // We must limit the frequency of notification updates, otherwise the system may suppress
      // them.
      if (events.containsAny(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          Player.EVENT_MEDIA_METADATA_CHANGED,
          Player.EVENT_TIMELINE_CHANGED)) {
        mediaSessionService.onUpdateNotificationInternal(
            session, /* startInForegroundWhenPaused= */ false);
      }
    }
  }

  @SuppressLint("InlinedApi") // Using compile time constant FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
  private void startForeground(MediaNotification mediaNotification) {
    ContextCompat.startForegroundService(mediaSessionService, startSelfIntent);
    Util.setForegroundServiceNotification(
        mediaSessionService,
        mediaNotification.notificationId,
        mediaNotification.notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        "mediaPlayback");
    startedInForeground = true;
  }

  private void stopForeground(boolean removeNotifications) {
    if (Util.SDK_INT >= 24) {
      Api24.stopForeground(mediaSessionService, removeNotifications);
    } else {
      mediaSessionService.stopForeground(removeNotifications);
    }
    startedInForeground = false;
  }

  private static final class ControllerInfo {

    public final ListenableFuture<MediaController> controllerFuture;

    /** Indicates whether the user actively dismissed the notification. */
    public boolean wasNotificationDismissed;

    public ControllerInfo(ListenableFuture<MediaController> controllerFuture) {
      this.controllerFuture = controllerFuture;
    }
  }

  @RequiresApi(24)
  private static class Api24 {

    public static void stopForeground(MediaSessionService service, boolean removeNotification) {
      service.stopForeground(removeNotification ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
    }

    private Api24() {}
  }
}
