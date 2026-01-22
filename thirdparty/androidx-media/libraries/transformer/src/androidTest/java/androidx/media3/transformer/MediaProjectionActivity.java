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

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
import static androidx.media3.common.util.Util.SDK_INT;
import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.Future;

/** Activity for setting up media projection. */
public final class MediaProjectionActivity extends Activity {

  private static final int REQUEST_CODE_SCREEN_CAPTURE = 100;

  private final SettableFuture<Intent> screenCaptureTokenFuture;

  /** Creates a new instance. */
  public MediaProjectionActivity() {
    screenCaptureTokenFuture = SettableFuture.create();
  }

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);

    Context context = this;
    MediaProjectionManager mediaProjectionManager =
        (MediaProjectionManager) context.getSystemService(MEDIA_PROJECTION_SERVICE);
    Intent screenCaptureIntent;
    if (SDK_INT >= 34) {
      // API 34 onwards supports single app capture. Disable this option to simplify UI automation.
      MediaProjectionConfig mediaProjectionConfig =
          MediaProjectionConfig.createConfigForDefaultDisplay();
      screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent(mediaProjectionConfig);
    } else {
      screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
    }
    startActivityForResult(screenCaptureIntent, REQUEST_CODE_SCREEN_CAPTURE);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    assertThat(requestCode).isEqualTo(REQUEST_CODE_SCREEN_CAPTURE);
    assertThat(resultCode).isEqualTo(RESULT_OK);

    super.onActivityResult(requestCode, resultCode, data);

    Context context = this;
    Util.startForegroundService(context, new Intent(context, MediaProjectionService.class));
    screenCaptureTokenFuture.set(data);
  }

  /** Returns a future that will provide the intent to set up screen capture. */
  public Future<Intent> getScreenCaptureTokenFuture() {
    return screenCaptureTokenFuture;
  }

  /** Foreground service that's required by the media projection APIs. */
  public static final class MediaProjectionService extends Service {

    public static final String ACTION_EVENT_STARTED = "started";
    public static final String ACTION_STOP = "stop";

    private static final String CHANNEL_ID = "MediaProjectionService";
    private static final int NOTIFICATION_ID = 1;

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
      return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      Context context = this;
      Notification notification =
          (SDK_INT >= 26
                  ? new Notification.Builder(context, CHANNEL_ID)
                  : new Notification.Builder(context))
              .setContentTitle("Test media projection")
              .setSmallIcon(android.R.drawable.ic_media_play)
              .setOngoing(true)
              .build();
      if (SDK_INT >= 26) {
        NotificationChannel channel =
            new NotificationChannel(
                CHANNEL_ID, "Test media projection", NotificationManager.IMPORTANCE_HIGH);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
      }
      if (SDK_INT >= 29) {
        startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
      } else {
        startForeground(NOTIFICATION_ID, notification);
      }
      context.sendBroadcast(new Intent(ACTION_EVENT_STARTED));
      return START_STICKY;
    }
  }
}
