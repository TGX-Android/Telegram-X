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
package androidx.media3.exoplayer;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import androidx.media3.common.util.Clock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link WifiLockManager}. */
@RunWith(AndroidJUnit4.class)
public class WifiLockManagerTest {

  private Context context;
  private HandlerThread handlerThread;
  private WifiManager wifiManager;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    handlerThread = new HandlerThread("wifiLockManagerTest");
    handlerThread.start();
    wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
  }

  @After
  public void tearDown() {
    handlerThread.quit();
  }

  @Test
  public void stayAwakeFalse_wifiLockIsNeverHeld() {
    int initialLockCount = shadowOf(wifiManager).getActiveLockCount();
    WifiLockManager wifiLockManager =
        new WifiLockManager(context, handlerThread.getLooper(), Clock.DEFAULT);

    wifiLockManager.setEnabled(true);
    wifiLockManager.setStayAwake(false);
    shadowOf(handlerThread.getLooper()).idle();
    int lockCountWhenEnabled = shadowOf(wifiManager).getActiveLockCount();
    wifiLockManager.setEnabled(false);
    shadowOf(handlerThread.getLooper()).idle();
    int lockCountAfterDisable = shadowOf(wifiManager).getActiveLockCount();

    assertThat(lockCountWhenEnabled).isAtMost(initialLockCount);
    assertThat(lockCountAfterDisable).isAtMost(initialLockCount);
  }

  @Test
  public void stayAwakeTrue_wifiLockIsOnlyHeldWhenEnabled() {
    WifiLockManager wifiLockManager =
        new WifiLockManager(context, handlerThread.getLooper(), Clock.DEFAULT);
    wifiLockManager.setEnabled(true);
    shadowOf(handlerThread.getLooper()).idle();

    int initialLockCount = shadowOf(wifiManager).getActiveLockCount();
    wifiLockManager.setStayAwake(true);
    shadowOf(handlerThread.getLooper()).idle();
    int lockCountWhenStayAwake = shadowOf(wifiManager).getActiveLockCount();
    wifiLockManager.setEnabled(false);
    shadowOf(handlerThread.getLooper()).idle();
    int lockCountAfterDisable = shadowOf(wifiManager).getActiveLockCount();

    assertThat(lockCountWhenStayAwake).isGreaterThan(initialLockCount);
    assertThat(lockCountAfterDisable).isLessThan(lockCountWhenStayAwake);
  }
}
