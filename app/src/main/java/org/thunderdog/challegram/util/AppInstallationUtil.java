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
 * File created on 13/11/2023
 */
package org.thunderdog.challegram.util;

import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.core.StringUtils;

public class AppInstallationUtil {
  public static final String VENDOR_GOOGLE_PLAY = "com.android.vending";
  public static final String VENDOR_GALAXY_STORE = "com.sec.android.app.samsungapps";
  public static final String VENDOR_HUAWEI_APPGALLERY = "com.huawei.appmarket";
  public static final String VENDOR_AMAZON_APPSTORE = "com.amazon.venezia";

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    InstallerId.UNKNOWN,
    InstallerId.GOOGLE_PLAY,
    InstallerId.GALAXY_STORE,
    InstallerId.HUAWEI_APPGALLERY,
    InstallerId.AMAZON_APPSTORE
  })
  public @interface InstallerId {
    int
      UNKNOWN = 0,
      GOOGLE_PLAY = 1,
      GALAXY_STORE = 2,
      HUAWEI_APPGALLERY = 3,
      AMAZON_APPSTORE = 4;
  }

  private static Integer installerId;

  public static synchronized @InstallerId int getInstallerId () {
    if (installerId == null) {
      installerId = getInstallerIdImpl();
    }
    return installerId;
  }

  private static @InstallerId int getInstallerIdImpl () {
    final String installerPackageName = getInstallerPackageName();
    if (!StringUtils.isEmpty(installerPackageName)) {
      //noinspection ConstantConditions
      switch (installerPackageName) {
        case VENDOR_GOOGLE_PLAY:
          return InstallerId.GOOGLE_PLAY;
        case VENDOR_GALAXY_STORE:
          return InstallerId.GALAXY_STORE;
        case VENDOR_HUAWEI_APPGALLERY:
          return InstallerId.HUAWEI_APPGALLERY;
        case VENDOR_AMAZON_APPSTORE:
          return InstallerId.AMAZON_APPSTORE;
      }
    }
    return InstallerId.UNKNOWN;
  }

  // Checks installer id for current app installation

  @Nullable
  public static String getInstallerPackageName () {
    try {
      String packageName = UI.getAppContext().getPackageName();
      String installerPackageName = UI.getAppContext().getPackageManager().getInstallerPackageName(packageName);
      if (StringUtils.isEmpty(installerPackageName)) {
        return null;
      }
      return installerPackageName;
    } catch (Throwable t) {
      Log.v("Unable to determine installer package", t);
      return null;
    }
  }

  // Checks whether app is installed from unofficial source (e.g. directly via an APK)

  public static boolean isAppSideLoaded () {
    return getInstallerId() == InstallerId.UNKNOWN;
  }

  // Do not allow in-app updates from Google Play, if we are installed from market that doesn't allow it

  public static boolean allowInAppGooglePlayUpdates () {
    switch (getInstallerId()) {
      case InstallerId.UNKNOWN:
      case InstallerId.GOOGLE_PLAY: {
        //noinspection ObsoleteSdkInt
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !BuildConfig.SIDE_LOAD_ONLY;
      }
      case InstallerId.GALAXY_STORE:
      case InstallerId.HUAWEI_APPGALLERY:
      case InstallerId.AMAZON_APPSTORE:
        return false;
      default:
        throw new UnsupportedOperationException();
    }
  }

  // Do not allow in-app updates via Telegram channel, unless it's a direct APK installation

  public static boolean allowInAppTelegramUpdates () {
    return !BuildConfig.EXPERIMENTAL && getInstallerId() == InstallerId.UNKNOWN;
  }

  // Do not allow non-store URLs for compliance

  @SuppressWarnings("ConstantConditions")
  public static @NonNull String getDownloadUrl (@Nullable String remoteDownloadUrl) {
    switch (getInstallerId()) {
      case InstallerId.UNKNOWN:
      case InstallerId.GOOGLE_PLAY: // primary distribution channel, no need to force URL.
        break;

      case InstallerId.GALAXY_STORE:
        if (!StringUtils.isEmpty(BuildConfig.GALAXY_STORE_URL)) {
          return BuildConfig.GALAXY_STORE_URL;
        }
        break;
      case InstallerId.HUAWEI_APPGALLERY:
        if (!StringUtils.isEmpty(BuildConfig.HUAWEI_APPGALLERY_URL)) {
          return BuildConfig.HUAWEI_APPGALLERY_URL;
        }
        break;
      case InstallerId.AMAZON_APPSTORE:
        if (!StringUtils.isEmpty(BuildConfig.AMAZON_APPSTORE_URL)) {
          return BuildConfig.AMAZON_APPSTORE_URL;
        }
        break;
    }
    if (remoteDownloadUrl != null) {
      return remoteDownloadUrl;
    }
    if (StringUtils.isEmpty(BuildConfig.DOWNLOAD_URL)) {
      throw new UnsupportedOperationException();
    }
    return BuildConfig.DOWNLOAD_URL;
  }
}
