/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.core;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

public class WatchDogObserver {
  // private static final Uri INTERNAL_CONTENT_URI = MediaStore.Files.getContentUri("internal");
  // private static final Uri EXTERNAL_CONTENT_URI = MediaStore.Files.getContentUri("external");

  private static class Observer extends ContentObserver {
    private final WatchDogObserver context;
    private final boolean isProbablyImage;
    private final boolean isExternal;

    public Observer(WatchDogObserver context, boolean isProbablyImage, boolean isExternal) {
      super(null);
      this.context = context;
      this.isProbablyImage = isProbablyImage;
      this.isExternal = isExternal;
    }

    @Override
    public void onChange(boolean selfChange) {
      super.onChange(selfChange);
      context.onChange(isProbablyImage, isExternal);
    }
  }

  private final Observer imageObserverInternal, videoObserverInternal;
  private final Observer imageObserverExternal, videoObserverExternal;

  private final PhoneStateListener phoneStateListener;

  private boolean isRegistered;

  WatchDogObserver () {
    imageObserverInternal = new Observer(this, true, false);
    imageObserverExternal = new Observer(this, true, true);

    videoObserverInternal = new Observer(this, false, false);
    videoObserverExternal = new Observer(this, false, true);

    PhoneStateListener phoneStateListener = null;

    try {
      phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged (int state, String incomingNumber) {
          switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
            case TelephonyManager.CALL_STATE_OFFHOOK: {
              setPhoneCallInProgress(true);
              break;
            }
            case TelephonyManager.CALL_STATE_IDLE: {
              setPhoneCallInProgress(false);
              break;
            }
          }
        }
      };
    } catch (Throwable ignored) { }

    this.phoneStateListener = phoneStateListener;
  }

  private boolean phoneCallInProgress;

  private void setPhoneCallInProgress (boolean inProgress) {
    if (this.phoneCallInProgress != inProgress) {
      this.phoneCallInProgress = inProgress;
      TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_CALL, inProgress);
      if (inProgress) {
        UI.post(() -> {
          BaseActivity context = UI.getUiContext();
          if (context != null && context.getRecordAudioVideoController().isOpen()) {
            context.getRecordAudioVideoController().finishRecording(true);
          }
        });
      }
    }
  }

  private static void register (ContentResolver resolver, Uri uri, Observer observer) {
    try {
      resolver.registerContentObserver(uri, false, observer);
    } catch (Throwable ignored) { }
  }

  private static void unregister (ContentResolver resolver, Observer observer) {
    try {
      resolver.unregisterContentObserver(observer);
    } catch (Throwable ignored) { }
  }

  public void register () {
    if (!isRegistered) {
      isRegistered = true;

      Context context = UI.getAppContext();
      ContentResolver resolver = context.getContentResolver();

      register(resolver, MediaStore.Images.Media.INTERNAL_CONTENT_URI, imageObserverInternal);
      register(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageObserverExternal);
      register(resolver, MediaStore.Video.Media.INTERNAL_CONTENT_URI, videoObserverInternal);
      register(resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoObserverExternal);

      if (phoneStateListener != null) {
        try {
          TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
          if (manager != null) {
            manager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
          }
        } catch (Throwable ignored) { }
      }
    }
  }

  public void unregister () {
    if (isRegistered) {
      isRegistered = false;

      Context context = UI.getAppContext();
      ContentResolver resolver = context.getContentResolver();

      unregister(resolver, imageObserverInternal);
      unregister(resolver, imageObserverExternal);
      unregister(resolver, videoObserverInternal);
      unregister(resolver, videoObserverExternal);

      if (phoneStateListener != null) {
        try {
          TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
          if (manager != null) {
            manager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
          }
        } catch (Throwable ignored) { }
      }
    }
  }

  // Processors

  private void onChange (boolean isProbablyImage, boolean isExternal) {
    synchronized (this) {
      if (isProbablyImage && UI.getUiState() == UI.STATE_RESUMED) {
        checkScreenshots(isExternal);
      }
    }
  }

  private void checkScreenshots (final boolean isExternal) {
    // UI.showToast("Check screenshots", Toast.LENGTH_SHORT);
    final Tdlib tdlib = TdlibManager.instance().current();
    if (!tdlib.hasOpenChats() || !UI.wasResumedRecently(1000)) {
      return;
    }

    Media.instance().post(() -> {
      boolean hasScreenshot = false;
      int screenshotDate = 0;
      try {
        final Uri uri = isExternal ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.INTERNAL_CONTENT_URI;
        final String[] projection = new String[] {
          MediaStore.Images.Media.DATA,
          Media.DATE_COLUMN,
          MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
          MediaStore.Images.Media.TITLE
        };
        Cursor c = UI.getAppContext().getContentResolver().query(uri, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1");

        if (c != null) {
          if (c.getCount() > 0) {
            c.moveToFirst();

            String path = c.getString(0);
            String bucketName = c.getString(2);
            String title = c.getString(3);

            if (U.isScreenshotFolder(path) || U.isScreenshotFolder(bucketName) || U.isScreenshotFolder(title)) {
              screenshotDate = (int) (c.getLong(1) / 1000L);
              hasScreenshot = true;
            }
          }

          c.close();
        }
      } catch (Throwable ignored) { }

      if (hasScreenshot) {
        tdlib.onScreenshotTaken(screenshotDate);
      }
    });
  }
}
