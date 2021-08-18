/**
 * File created on 05/04/15 at 08:53
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;
import androidx.multidex.MultiDexApplication;
import androidx.work.Configuration;

import org.thunderdog.challegram.tool.UI;

public final class BaseApplication extends MultiDexApplication implements Configuration.Provider, CameraXConfig.Provider {
  @Override
  public void onCreate () {
    super.onCreate();
    UI.initApp(getApplicationContext());
  }

  @NonNull
  @Override
  public Configuration getWorkManagerConfiguration () {
    return new Configuration.Builder().build();
  }

  @NonNull
  @Override
  public CameraXConfig getCameraXConfig () {
    return Camera2Config.defaultConfig();
  }
}
