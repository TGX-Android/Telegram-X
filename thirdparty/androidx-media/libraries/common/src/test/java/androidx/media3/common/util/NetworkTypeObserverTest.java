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
package androidx.media3.common.util;

import static android.net.NetworkInfo.State.CONNECTED;
import static android.net.NetworkInfo.State.DISCONNECTED;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import androidx.media3.common.C;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowTelephonyManager;

/** Unit test for {@link NetworkTypeObserver}. */
@RunWith(AndroidJUnit4.class)
@Config(sdk = Config.ALL_SDKS) // Test all SDKs because network detection logic changed over time.
public class NetworkTypeObserverTest {

  private HandlerThread backgroundThread;

  @Before
  public void setUp() {
    NetworkTypeObserver.resetForTests();
    backgroundThread = new HandlerThread("NetworkTypeObserverTest");
    backgroundThread.start();
    BackgroundExecutor.set(new Handler(backgroundThread.getLooper())::post);
  }

  @After
  public void tearDown() {
    backgroundThread.quit();
  }

  @Test
  public void register_immediatelyAfterObtainingStaticInstance_callsOnNetworkTypeChanged() {
    setActiveNetworkInfo(getWifiNetworkInfo());
    NetworkTypeObserver.Listener listener = mock(NetworkTypeObserver.Listener.class);
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    // Do not wait for pending operations here.

    networkTypeObserver.register(listener, directExecutor());
    waitForPendingOperations();

    verify(listener).onNetworkTypeChanged(C.NETWORK_TYPE_WIFI);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void register_afterStaticInstanceIsInitialized_callsOnNetworkTypeChanged() {
    setActiveNetworkInfo(getWifiNetworkInfo());
    NetworkTypeObserver.Listener listener = mock(NetworkTypeObserver.Listener.class);
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());

    waitForPendingOperations();
    networkTypeObserver.register(listener, directExecutor());
    waitForPendingOperations();

    verify(listener).onNetworkTypeChanged(C.NETWORK_TYPE_WIFI);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void register_withCustomExecutor_callsOnNetworkTypeChangedOnExecutor() {
    setActiveNetworkInfo(getWifiNetworkInfo());
    AtomicReference<Looper> actualListenerLooper = new AtomicReference<>();
    NetworkTypeObserver.Listener listener =
        networkType -> actualListenerLooper.set(Looper.myLooper());
    HandlerThread listenerThread = new HandlerThread("CustomListenerThread");
    listenerThread.start();
    Looper listenerThreadLooper = listenerThread.getLooper();
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());

    networkTypeObserver.register(listener, new Handler(listenerThreadLooper)::post);
    waitForPendingOperations();
    shadowOf(listenerThreadLooper).idle();
    listenerThread.quit();

    assertThat(actualListenerLooper.get()).isEqualTo(listenerThreadLooper);
  }

  @Test
  public void register_withChangeInNetworkType_callsOnNetworkTypeChangedAgain() {
    setActiveNetworkInfo(getWifiNetworkInfo());
    NetworkTypeObserver.Listener listener = mock(NetworkTypeObserver.Listener.class);
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());

    networkTypeObserver.register(listener, directExecutor());
    waitForPendingOperations();
    setActiveNetworkInfo(get4gNetworkInfo());
    waitForPendingOperations();
    setActiveNetworkInfo(get4gNetworkInfo()); // Check same network type isn't reported twice.
    waitForPendingOperations();
    setActiveNetworkInfo(get3gNetworkInfo());
    waitForPendingOperations();

    verify(listener).onNetworkTypeChanged(C.NETWORK_TYPE_WIFI);
    verify(listener).onNetworkTypeChanged(C.NETWORK_TYPE_4G);
    verify(listener).onNetworkTypeChanged(C.NETWORK_TYPE_3G);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void getNetworkType_withWifiNetwork_returnsNetworkTypeWifi() {
    setActiveNetworkInfo(getWifiNetworkInfo());
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_WIFI);
  }

  @Test
  public void getNetworkType_with2gNetwork_returnsNetworkType2g() {
    setActiveNetworkInfo(get2gNetworkInfo());
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_2G);
  }

  @Test
  public void getNetworkType_with3gNetwork_returnsNetworkType3g() {
    setActiveNetworkInfo(get3gNetworkInfo());
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_3G);
  }

  @Test
  public void getNetworkType_with4gNetwork_returnsNetworkType4g() {
    setActiveNetworkInfo(get4gNetworkInfo());
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_4G);
  }

  @Test
  @Config(minSdk = 31) // 5G-NSA detection is supported from API 31.
  public void getNetworkType_with5gNsaNetwork_returnsNetworkType5gNsa() {
    setActiveNetworkInfo(get4gNetworkInfo(), TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA);
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_5G_NSA);
  }

  @Test
  @Config(minSdk = 29) // 5G-SA detection is supported from API 29.
  public void getNetworkType_with5gSaNetwork_returnsNetworkType5gSa() {
    setActiveNetworkInfo(get5gSaNetworkInfo());
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_5G_SA);
  }

  @Test
  public void getNetworkType_withEthernetNetwork_returnsNetworkTypeEthernet() {
    setActiveNetworkInfo(getEthernetNetworkInfo());
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_ETHERNET);
  }

  @Test
  public void getNetworkType_withOfflineNetwork_returnsNetworkTypeOffline() {
    setActiveNetworkInfo(getOfflineNetworkInfo());
    NetworkTypeObserver networkTypeObserver =
        NetworkTypeObserver.getInstance(ApplicationProvider.getApplicationContext());
    waitForPendingOperations();

    assertThat(networkTypeObserver.getNetworkType()).isEqualTo(C.NETWORK_TYPE_OFFLINE);
  }

  private void waitForPendingOperations() {
    ShadowLooper.idleMainLooper();
    shadowOf(backgroundThread.getLooper()).idle();
  }

  private static void setActiveNetworkInfo(NetworkInfo networkInfo) {
    setActiveNetworkInfo(networkInfo, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE);
  }

  // Adding the permission to the test AndroidManifest.xml doesn't work to appease lint.
  @SuppressWarnings({"StickyBroadcast", "MissingPermission"})
  private static void setActiveNetworkInfo(NetworkInfo networkInfo, int networkTypeOverride) {
    // Set network info in ConnectivityManager and TelephonyDisplayInfo in TelephonyManager.
    Context context = ApplicationProvider.getApplicationContext();
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    Shadows.shadowOf(connectivityManager).setActiveNetworkInfo(networkInfo);
    if (Util.SDK_INT >= 31) {
      TelephonyManager telephonyManager =
          (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
      Object displayInfo =
          ShadowTelephonyManager.createTelephonyDisplayInfo(
              networkInfo.getType(), networkTypeOverride);
      Shadows.shadowOf(telephonyManager).setTelephonyDisplayInfo(displayInfo);
    }
    // Create a sticky broadcast for the connectivity action because Robolectric isn't replying with
    // the current network state if a receiver for this intent is registered.
    context.sendStickyBroadcast(new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
  }

  private static NetworkInfo getWifiNetworkInfo() {
    return ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_WIFI,
        /* subType= */ 0,
        /* isAvailable= */ true,
        CONNECTED);
  }

  private static NetworkInfo get2gNetworkInfo() {
    return ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_MOBILE,
        TelephonyManager.NETWORK_TYPE_GPRS,
        /* isAvailable= */ true,
        CONNECTED);
  }

  private static NetworkInfo get3gNetworkInfo() {
    return ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_MOBILE,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        /* isAvailable= */ true,
        CONNECTED);
  }

  private static NetworkInfo get4gNetworkInfo() {
    return ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_MOBILE,
        TelephonyManager.NETWORK_TYPE_LTE,
        /* isAvailable= */ true,
        CONNECTED);
  }

  private static NetworkInfo get5gSaNetworkInfo() {
    return ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_MOBILE,
        TelephonyManager.NETWORK_TYPE_NR,
        /* isAvailable= */ true,
        CONNECTED);
  }

  private static NetworkInfo getEthernetNetworkInfo() {
    return ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_ETHERNET,
        /* subType= */ 0,
        /* isAvailable= */ true,
        CONNECTED);
  }

  private static NetworkInfo getOfflineNetworkInfo() {
    return ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.DISCONNECTED,
        ConnectivityManager.TYPE_WIFI,
        /* subType= */ 0,
        /* isAvailable= */ false,
        DISCONNECTED);
  }
}
