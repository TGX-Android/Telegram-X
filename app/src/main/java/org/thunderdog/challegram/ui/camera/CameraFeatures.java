/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 24/09/2017
 */
package org.thunderdog.challegram.ui.camera;

public class CameraFeatures {
  public static final int FEATURE_FACING_FRONT = 1;

  public static final int FEATURE_AREA_FOCUS = 1 << 1;
  public static final int FEATURE_AREA_METERING = 1 << 2;

  public static final int FEATURE_FOCUS_AUTO = 1 << 5;
  public static final int FEATURE_FOCUS_CONTINUOUS_PICTURE = 1 << 6;
  public static final int FEATURE_FOCUS_CONTINUOUS_VIDEO = 1 << 7;
  public static final int FEATURE_FOCUS_ANY = FEATURE_FOCUS_AUTO | FEATURE_FOCUS_CONTINUOUS_PICTURE | FEATURE_FOCUS_CONTINUOUS_VIDEO;


  public static final int FEATURE_FLASH_OFF = 1 << 10;
  public static final int FEATURE_FLASH_ON = 1 << 11;
  public static final int FEATURE_FLASH_AUTO = 1 << 12;
  public static final int FEATURE_FLASH_TORCH = 1 << 13;
  public static final int FEATURE_FLASH_FAKE = 1 << 14;

  public static final int FEATURE_ZOOM = 1 << 20;
  public static final int FEATURE_ZOOM_SMOOTH = 1 << 21;

  private final boolean isCamera2Api;
  private int features;

  public static final int MAX_ZOOM_UNKNOWN = -1;
  private int maxZoom = MAX_ZOOM_UNKNOWN;

  public CameraFeatures (boolean isCamera2Api) {
    this.isCamera2Api = isCamera2Api;
  }

  public boolean has (int feature) {
    return (features & feature) != 0;
  }

  public void add (int feature) {
    // TODO blacklist any features here on specific devices
    this.features |= feature;
  }

  public void setMaxZoom (int zoom) {
    this.maxZoom = zoom;
  }

  public int getMaxZoom () {
    return maxZoom;
  }

  // Specific

  public boolean canFocus () {
    return has(FEATURE_FOCUS_ANY);
  }

  public boolean canFocusByTap () {
    return has(FEATURE_AREA_FOCUS | FEATURE_AREA_METERING);
  }

  public boolean canZoom () {
    return maxZoom > 0 && has(FEATURE_ZOOM | FEATURE_ZOOM_SMOOTH);
  }

  public boolean canFlash (boolean allowFake) {
    int flashModes = FEATURE_FLASH_ON | FEATURE_FLASH_AUTO;
    if (allowFake) {
      flashModes |= FEATURE_FACING_FRONT;
    }
    return has(flashModes);
  }
}
