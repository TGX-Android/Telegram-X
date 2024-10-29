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
 * File created on 23/04/2015 at 18:54
 */
package org.thunderdog.challegram.unsorted;

import org.thunderdog.challegram.tool.Screen;

@SuppressWarnings(value = "SpellCheckingInspection")
public class Size {
  //Raw sizes

  private static final float RAW_HEADER_PORTRAIT_SIZE = 56f;
  private static final float RAW_HEADER_BIG_PORTRAIT_SIZE = 234f;
  private static final float RAW_HEADER_BIG_PORTRAIT_SIZE_NOEXPAND = 144f;
  // private static final float RAW_HEADER_BIG_OFFSET_SIZE = ;
  private static final float RAW_HEADER_DRAWER_SIZE = 46f;

  private static final float RAW_HEADER_PLAYER_SIZE = 36f;

  private static final float RAW_NAVIGATION_SHADOW_SIZE = 7f;
  private static final float RAW_NAVIGATION_SHADOWVERT_SIZE = 6f;

  //Factors

  public static final float NAVIGATION_DROP_FACTOR = 0.67f;
  public static final float NAVIGATION_PREVIEW_TRANSLATE_FACTOR = 3.5f;

  public static float convertExpandedFactor (float scaleFactor) {
    return getHeaderSizeDifference(true) * scaleFactor / (float) getHeaderSizeDifference(false);
  }

  public static int getHeaderPortraitSize () {
    return Screen.dp(RAW_HEADER_PORTRAIT_SIZE);
  }

  public static int getHeaderBigPortraitSize (boolean needExpand) {
    return Screen.dp(needExpand ? RAW_HEADER_BIG_PORTRAIT_SIZE : RAW_HEADER_BIG_PORTRAIT_SIZE_NOEXPAND);
  }

  public static int getHeaderSizeDifference (boolean needExpand) {
    return getHeaderBigPortraitSize(needExpand) - getHeaderPortraitSize();
  }

  public static int getMaximumHeaderSize () {
    return getHeaderBigPortraitSize(true);
  }

  public static int getMaximumHeaderSizeDifference () {
    return getMaximumHeaderSize() - getHeaderPortraitSize();
  }

  public static int getHeaderDrawerSize () {
    return Screen.dp(RAW_HEADER_DRAWER_SIZE);
  }

  public static int getHeaderPlayerSize () {
    return Screen.dp(RAW_HEADER_PLAYER_SIZE);
  }

  public static int getNavigationShadowSize () {
    return Screen.dp(RAW_NAVIGATION_SHADOW_SIZE);
  }

  public static int getNavigationShadowvertSize () {
    return Screen.dp(RAW_NAVIGATION_SHADOWVERT_SIZE);
  }
}
