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
 * File created on 04/11/2018
 */
package org.thunderdog.challegram.theme;

import androidx.annotation.IntDef;

import org.thunderdog.challegram.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  ThemeProperty.PARENT_THEME, // Determines which theme is used, when color is not overriden by theme set
  ThemeProperty.DARK, // Determines whether theme is dark or not. Values: 0 or 1
  ThemeProperty.REPLACE_SHADOWS_WITH_SEPARATORS, // When enabled, instead of shadows, thin solid separators will be used. 0 or 1
  ThemeProperty.BUBBLE_CORNER_MERGED, // Determines bubble corner radius on the specified theme. Values: 0..18
  ThemeProperty.BUBBLE_CORNER, // Determines bubble corner radius on the specified theme. Values: 0..18
  ThemeProperty.BUBBLE_CORNER_LEGACY, // Determines bubble corner radius used on Android 4.x versions. Values: 0..6
  ThemeProperty.BUBBLE_OUTER_MARGIN, // Determines default margin from the edge of the screen. Ignored on Samsung Galaxy S8. Values: 0..12,
  ThemeProperty.WALLPAPER_ID, // Determines default wallpaper. To know wallpaper id, hold wallpaper in the selection. 0 means wallpaper is disabled by default.
  ThemeProperty.SHADOW_DEPTH, // Determines shadow transparency. 0 – no shadow, 0.5 used in light built-in themes, 1.0 used in built-in dark themes. 0..1
  ThemeProperty.BUBBLE_OUTLINE, // When set to 1, all bubbles will have thin outline around bubble. 0 or 1
  ThemeProperty.BUBBLE_OUTLINE_SIZE, // By default, .5f
  ThemeProperty.BUBBLE_DATE_CORNER,
  ThemeProperty.BUBBLE_UNREAD_SHADOW,
  ThemeProperty.DATE_CORNER,
  ThemeProperty.WALLPAPER_USAGE_ID,
  ThemeProperty.SUBTITLE_ALPHA,
  ThemeProperty.IMAGE_CORNER,
  ThemeProperty.LIGHT_STATUS_BAR,
  ThemeProperty.AVATAR_RADIUS,
  ThemeProperty.AVATAR_RADIUS_FORUM,
  ThemeProperty.AVATAR_RADIUS_CHAT_LIST,
  ThemeProperty.AVATAR_RADIUS_CHAT_LIST_FORUM,
  ThemeProperty.WALLPAPER_OVERRIDE_DATE,
  ThemeProperty.WALLPAPER_OVERRIDE_UNREAD,
  ThemeProperty.WALLPAPER_OVERRIDE_MEDIA_REPLY,
  ThemeProperty.WALLPAPER_OVERRIDE_TIME,
  ThemeProperty.WALLPAPER_OVERRIDE_BUTTON,
  ThemeProperty.WALLPAPER_OVERRIDE_OVERLAY,
})
public @interface ThemeProperty {
  int PARENT_THEME = R.id.theme_property_parentTheme;
  int DARK = R.id.theme_property_dark;
  int REPLACE_SHADOWS_WITH_SEPARATORS = R.id.theme_property_replaceShadowsWithSeparators;
  int BUBBLE_CORNER_MERGED = R.id.theme_property_bubbleCornerMerged;
  int BUBBLE_CORNER = R.id.theme_property_bubbleCorner;
  int BUBBLE_CORNER_LEGACY = R.id.theme_property_bubbleCornerLegacy;
  int BUBBLE_OUTER_MARGIN = R.id.theme_property_bubbleOuterMargin;
  int BUBBLE_OUTLINE = R.id.theme_property_bubbleOutline;
  int BUBBLE_OUTLINE_SIZE = R.id.theme_property_bubbleOutlineSize;
  int BUBBLE_UNREAD_SHADOW = R.id.theme_property_bubbleUnreadShadow;
  int IMAGE_CORNER = R.id.theme_property_imageCorner;
  int WALLPAPER_ID = R.id.theme_property_wallpaperId;
  int SHADOW_DEPTH = R.id.theme_property_shadowDepth;
  int WALLPAPER_USAGE_ID = R.id.theme_property_wallpaperUsageId;
  int SUBTITLE_ALPHA = R.id.theme_property_subtitleAlpha;
  int BUBBLE_DATE_CORNER = R.id.theme_property_bubbleDateCorner;
  int DATE_CORNER = R.id.theme_property_dateCorner;
  int LIGHT_STATUS_BAR = R.id.theme_property_lightStatusBar;

  int AVATAR_RADIUS = R.id.theme_property_avatarRadius;
  int AVATAR_RADIUS_FORUM = R.id.theme_property_avatarRadiusForum;

  int AVATAR_RADIUS_CHAT_LIST = R.id.theme_property_avatarRadiusChatList;
  int AVATAR_RADIUS_CHAT_LIST_FORUM = R.id.theme_property_avatarRadiusChatListForum;

  int WALLPAPER_OVERRIDE_DATE = R.id.theme_property_wallpaperOverrideDate;
  int WALLPAPER_OVERRIDE_UNREAD = R.id.theme_property_wallpaperOverrideUnread;
  int WALLPAPER_OVERRIDE_MEDIA_REPLY = R.id.theme_property_wallpaperOverrideMediaReply;
  int WALLPAPER_OVERRIDE_TIME = R.id.theme_property_wallpaperOverrideTime;
  int WALLPAPER_OVERRIDE_BUTTON = R.id.theme_property_wallpaperOverrideButton;
  int WALLPAPER_OVERRIDE_OVERLAY = R.id.theme_property_wallpaperOverrideOverlay;
}
