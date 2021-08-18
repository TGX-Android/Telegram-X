/**
 * File created on 23/04/15 at 18:54
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.unsorted;

import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.MathUtils;

@SuppressWarnings(value = "SpellCheckingInspection")
@Deprecated
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
    return MathUtils.clamp(getHeaderSizeDifference(true) * scaleFactor / (float) getHeaderSizeDifference(false));
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
