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
 * File created on 09/05/2024
 */
package org.thunderdog.challegram.util;

import androidx.annotation.LongDef;

import org.thunderdog.challegram.BuildConfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class FeatureAvailability {
  /**
   * List of available features that require attention after an app update (i.e. some special prompt).
   */
  @Retention(RetentionPolicy.SOURCE)
  @LongDef(value = {
    Feature.CHAT_FOLDERS
  }, flag = true)
  public @interface Feature {
    long CHAT_FOLDERS = 1;
  }

  /**
   * Constants indicating in which version the specific feature became available,
   * or {@link Integer.MAX_VALUE} if not yet released.
   */
  @Retention(RetentionPolicy.SOURCE)
  public @interface ReleaseVersionCode {
    int CHAT_FOLDERS = 1725;
  }

  /**
   * Compile-time constants to check if specific feature is available.
   */
  @Retention(RetentionPolicy.SOURCE)
  @SuppressWarnings("ConstantConditions")
  public @interface Released {
    boolean CHAT_FOLDERS = BuildConfig.ORIGINAL_VERSION_CODE >= ReleaseVersionCode.CHAT_FOLDERS;
  }

  public static long recoverAvailableFeaturesForAppVersionCode (int versionCode) {
    @Feature long features = 0;
    if (versionCode >= ReleaseVersionCode.CHAT_FOLDERS) {
      features |= Feature.CHAT_FOLDERS;
    }
    return features;
  }

  public static long currentlyAvailableFeatures () {
    return recoverAvailableFeaturesForAppVersionCode(BuildConfig.ORIGINAL_VERSION_CODE);
  }
}
