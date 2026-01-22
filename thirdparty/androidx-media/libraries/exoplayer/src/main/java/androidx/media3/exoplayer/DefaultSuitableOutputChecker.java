/*
 * Copyright (C) 2024 The Android Open Source Project
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
package androidx.media3.exoplayer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.ControllerCallback;
import android.media.MediaRouter2.RouteCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.util.BackgroundThreadStateHandler;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Default implementation for {@link SuitableOutputChecker}. */
/* package */ final class DefaultSuitableOutputChecker implements SuitableOutputChecker {

  @Nullable private final SuitableOutputChecker impl;

  /** Creates the default {@link SuitableOutputChecker}. */
  public DefaultSuitableOutputChecker() {
    if (Util.SDK_INT >= 35) {
      impl = new ImplApi35();
    } else if (Util.SDK_INT >= 23) {
      impl = new ImplApi23();
    } else {
      impl = null;
    }
  }

  @Override
  public void enable(
      Callback callback,
      Context context,
      Looper callbackLooper,
      Looper backgroundLooper,
      Clock clock) {
    if (impl != null) {
      impl.enable(callback, context, callbackLooper, backgroundLooper, clock);
    }
  }

  @Override
  public void disable() {
    if (impl != null) {
      impl.disable();
    }
  }

  @Override
  public boolean isSelectedOutputSuitableForPlayback() {
    return impl == null || impl.isSelectedOutputSuitableForPlayback();
  }

  @RequiresApi(35)
  private static final class ImplApi35 implements SuitableOutputChecker {
    private static final RouteDiscoveryPreference EMPTY_DISCOVERY_PREFERENCE =
        new RouteDiscoveryPreference.Builder(
                /* preferredFeatures= */ ImmutableList.of(), /* activeScan= */ false)
            .build();

    private @MonotonicNonNull MediaRouter2 router;
    private @MonotonicNonNull RouteCallback routeCallback;
    @Nullable private ControllerCallback controllerCallback;
    private @MonotonicNonNull BackgroundThreadStateHandler<Boolean> isSuitableForPlaybackState;

    @SuppressLint("ThreadSafe") // Handler is thread-safe, but not annotated.
    @Override
    public void enable(
        Callback callback,
        Context context,
        Looper callbackLooper,
        Looper backgroundLooper,
        Clock clock) {
      isSuitableForPlaybackState =
          new BackgroundThreadStateHandler<>(
              /* initialState= */ true,
              backgroundLooper,
              callbackLooper,
              clock,
              /* onStateChanged= */ (oldState, newState) ->
                  callback.onSelectedOutputSuitabilityChanged(newState));
      isSuitableForPlaybackState.runInBackground(
          () -> {
            checkNotNull(isSuitableForPlaybackState);
            router = MediaRouter2.getInstance(context);
            routeCallback = new RouteCallback() {};
            Executor backgroundExecutor = isSuitableForPlaybackState::runInBackground;
            router.registerRouteCallback(
                backgroundExecutor, routeCallback, EMPTY_DISCOVERY_PREFERENCE);
            controllerCallback =
                new ControllerCallback() {
                  @Override
                  public void onControllerUpdated(RoutingController controller) {
                    isSuitableForPlaybackState.setStateInBackground(
                        isSelectedOutputSuitableForPlayback(router));
                  }
                };
            router.registerControllerCallback(backgroundExecutor, controllerCallback);
            isSuitableForPlaybackState.setStateInBackground(
                isSelectedOutputSuitableForPlayback(router));
          });
    }

    @Override
    public void disable() {
      checkStateNotNull(isSuitableForPlaybackState)
          .runInBackground(
              () -> {
                checkNotNull(router).unregisterControllerCallback(checkNotNull(controllerCallback));
                controllerCallback = null;
                router.unregisterRouteCallback(checkNotNull(routeCallback));
              });
    }

    @Override
    public boolean isSelectedOutputSuitableForPlayback() {
      return isSuitableForPlaybackState == null ? true : isSuitableForPlaybackState.get();
    }

    private static boolean isSelectedOutputSuitableForPlayback(MediaRouter2 router) {
      int transferReason =
          checkNotNull(router).getSystemController().getRoutingSessionInfo().getTransferReason();
      boolean wasTransferInitiatedBySelf =
          router.getSystemController().wasTransferInitiatedBySelf();
      for (MediaRoute2Info routeInfo : router.getSystemController().getSelectedRoutes()) {
        if (isRouteSuitableForMediaPlayback(
            routeInfo, transferReason, wasTransferInitiatedBySelf)) {
          return true;
        }
      }
      return false;
    }

    private static boolean isRouteSuitableForMediaPlayback(
        MediaRoute2Info routeInfo, int transferReason, boolean wasTransferInitiatedBySelf) {
      int suitabilityStatus = routeInfo.getSuitabilityStatus();

      if (suitabilityStatus == MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_MANUAL_TRANSFER) {
        return (transferReason == RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST
                || transferReason == RoutingSessionInfo.TRANSFER_REASON_APP)
            && wasTransferInitiatedBySelf;
      }

      return suitabilityStatus == MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER;
    }
  }

  @RequiresApi(23)
  private static final class ImplApi23 implements SuitableOutputChecker {

    @Nullable private AudioManager audioManager;
    private @MonotonicNonNull AudioDeviceCallback audioDeviceCallback;
    private @MonotonicNonNull BackgroundThreadStateHandler<Boolean> isSuitableForPlaybackState;

    @Override
    public void enable(
        Callback callback,
        Context context,
        Looper callbackLooper,
        Looper backgroundLooper,
        Clock clock) {
      isSuitableForPlaybackState =
          new BackgroundThreadStateHandler<>(
              /* initialState= */ true,
              backgroundLooper,
              callbackLooper,
              clock,
              /* onStateChanged= */ (oldState, newState) ->
                  callback.onSelectedOutputSuitabilityChanged(newState));
      isSuitableForPlaybackState.runInBackground(
          () -> {
            checkNotNull(isSuitableForPlaybackState);
            if (!Util.isWear(context)) {
              return;
            }
            AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
              return;
            }
            this.audioManager = audioManager;
            audioDeviceCallback =
                new AudioDeviceCallback() {
                  @Override
                  public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    isSuitableForPlaybackState.setStateInBackground(hasSupportedAudioOutput());
                  }

                  @Override
                  public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    isSuitableForPlaybackState.setStateInBackground(hasSupportedAudioOutput());
                  }
                };
            audioManager.registerAudioDeviceCallback(
                audioDeviceCallback, new Handler(checkNotNull(Looper.myLooper())));
            isSuitableForPlaybackState.setStateInBackground(hasSupportedAudioOutput());
          });
    }

    @Override
    public void disable() {
      checkNotNull(isSuitableForPlaybackState)
          .runInBackground(
              () -> {
                if (audioManager != null) {
                  audioManager.unregisterAudioDeviceCallback(checkNotNull(audioDeviceCallback));
                }
              });
    }

    @Override
    public boolean isSelectedOutputSuitableForPlayback() {
      return isSuitableForPlaybackState == null ? true : isSuitableForPlaybackState.get();
    }

    private boolean hasSupportedAudioOutput() {
      AudioDeviceInfo[] audioDeviceInfos =
          checkStateNotNull(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
      for (AudioDeviceInfo device : audioDeviceInfos) {
        if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            || device.getType() == AudioDeviceInfo.TYPE_LINE_ANALOG
            || device.getType() == AudioDeviceInfo.TYPE_LINE_DIGITAL
            || device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE
            || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
          return true;
        }
        if (Util.SDK_INT >= 26 && device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
          return true;
        }
        if (Util.SDK_INT >= 28 && device.getType() == AudioDeviceInfo.TYPE_HEARING_AID) {
          return true;
        }
        if (Util.SDK_INT >= 31
            && (device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET
                || device.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
          return true;
        }
        if (Util.SDK_INT >= 33 && device.getType() == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
          return true;
        }
      }
      return false;
    }
  }
}
