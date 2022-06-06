/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 05/04/2015 at 08:53
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
