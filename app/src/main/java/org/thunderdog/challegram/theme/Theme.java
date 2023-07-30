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
 * File created on 17/01/2017
 */
package org.thunderdog.challegram.theme;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.util.StateSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.CircleDrawable;
import org.thunderdog.challegram.support.RectDrawable;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CustomStateListDrawable;

import java.util.ArrayList;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.ColorUtils;

public class Theme {
  public static final int EXPORT_FLAG_INCLUDE_DEFAULT_VALUES = 1;
  public static final int EXPORT_FLAG_JAVA = 1 << 1;

  public static int getIdResourceIdentifier (@NonNull String key) {
    try {
      Context context = UI.getAppContext();
      return context.getResources().getIdentifier(key, "id", context.getPackageName());
    } catch (Throwable ignored) {
      return 0;
    }
  }

  public static int avatarBigToSmall (@ColorId int avatarColorId) {
    switch (avatarColorId) {
      case ColorId.avatarInactive_big:
        return ColorId.avatarInactive;
      case ColorId.avatarReplies_big:
        return ColorId.avatarReplies;
      case ColorId.avatarPink_big:
        return ColorId.avatarPink;
      case ColorId.avatarRed_big:
        return ColorId.avatarRed;
      case ColorId.avatarOrange_big:
        return ColorId.avatarOrange;
      case ColorId.avatarYellow_big:
        return ColorId.avatarYellow;
      case ColorId.avatarGreen_big:
        return ColorId.avatarGreen;
      case ColorId.avatarBlue_big:
        return ColorId.avatarBlue;
      case ColorId.avatarCyan_big:
        return  ColorId.avatarCyan;
      case ColorId.avatarViolet_big:
        return ColorId.avatarViolet;
    }
    return 0;
  }
  
  public static int avatarSmallToBig (@ColorId int avatarColorId) {
    switch (avatarColorId) {
      case ColorId.avatarInactive:
        return ColorId.avatarInactive_big;
      case ColorId.avatarSavedMessages:
        return ColorId.avatarSavedMessages_big;
      case ColorId.avatarReplies:
        return ColorId.avatarReplies_big;
      case ColorId.avatarPink:
        return ColorId.avatarPink_big;
      case ColorId.avatarRed:
        return ColorId.avatarRed_big;
      case ColorId.avatarOrange:
        return ColorId.avatarOrange_big;
      case ColorId.avatarYellow:
        return ColorId.avatarYellow_big;
      case ColorId.avatarGreen:
        return ColorId.avatarGreen_big;
      case ColorId.avatarBlue:
        return ColorId.avatarBlue_big;
      case ColorId.avatarCyan:
        return  ColorId.avatarCyan_big;
      case ColorId.avatarViolet:
        return ColorId.avatarViolet_big;
    }
    return 0;
  }

  public static float getSubtitleAlpha () {
    return getProperty(PropertyId.SUBTITLE_ALPHA);
  }

  public static String getColorName (@ColorId int colorId) {
    return ThemeColors.getName(colorId);
  }

  public static String getPropertyName (@PropertyId int propertyId) {
    return ThemeProperties.getName(propertyId);
  }

  // Properties

  public static boolean isDark () {
    return ThemeManager.instance().isCurrentThemeDark();
  }

  public static int resolveCustomWallpaperIdentifier (int customThemeId) {
    return 2 + customThemeId;
  }

  private static int getCustomWallpaperIdentifier (int themeId) {
    return resolveCustomWallpaperIdentifier(ThemeManager.resolveCustomThemeId(themeId));
  }

  public static int getWallpaperIdentifier (@ThemeId int themeId) {
    int usageId = (int) getProperty(PropertyId.WALLPAPER_USAGE_ID, themeId);
    if (usageId == 2) {
      return getCustomWallpaperIdentifier(themeId);
    }
    return usageId;
  }

  public static int getWallpaperIdentifier () {
    int usageId = (int) getProperty(PropertyId.WALLPAPER_USAGE_ID);
    if (usageId == 2) {
      return getCustomWallpaperIdentifier(ThemeManager.instance().currentThemeId());
    }
    return usageId;
  }

  public static int getWallpaperIdentifier (ThemeDelegate theme) {
    final int usageId = (int) theme.getProperty(PropertyId.WALLPAPER_USAGE_ID);
    if (usageId == 2) {
      return getCustomWallpaperIdentifier(theme.getId());
    }
    return usageId;
  }

  public static String getDefaultWallpaper (@ThemeId int themeId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null && themeId == currentTheme.getId())
      return currentTheme.getDefaultWallpaper();
    return ThemeSet.getDefaultWallpaper(themeId);
  }

  public static String getDefaultWallpaper () {
    return ThemeManager.instance().currentTheme().getDefaultWallpaper();
  }

  // Base

  public static float getProperty (final @PropertyId int propertyId) {
    return ThemeManager.instance().currentTheme().getProperty(propertyId);
  }

  @ColorInt
  public static int getColor (final @ColorId int colorId) {
    return ThemeManager.instance().currentTheme().getColor(colorId);
  }

  public static int getColorFast (final @ColorId int colorId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null)
      return currentTheme.getColor(colorId);
    return ThemeSet.getColor(ThemeId.BLUE, colorId);
  }

  public static float getProperty (final @PropertyId int propertyId, final @ThemeId int themeId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null && themeId == currentTheme.getId())
      return currentTheme.getProperty(propertyId);
    return ThemeSet.getProperty(themeId, propertyId);
  }

  public static float getProperty (final Settings prefs, final @PropertyId int propertyId, final @ThemeId int themeId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null && themeId == currentTheme.getId())
      return currentTheme.getProperty(propertyId);
    return ThemeSet.getProperty(prefs, themeId, propertyId);
  }

  @ColorInt
  public static int getColor (final @ColorId int colorId, final @ThemeId int themeId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null && themeId == currentTheme.getId())
      return currentTheme.getColor(colorId);
    return ThemeSet.getColor(themeId, colorId);
  }

  //
  @ColorId
  public static int getCircleColorId (@ThemeId int themeId) {
    switch (themeId) {
      case ThemeId.NONE:
      case ThemeId.TEMPORARY:
      case ThemeId.CUSTOM:
        break;
      case ThemeId.BLACK_WHITE:
        return ColorId.themeBlackWhite;
      case ThemeId.BLUE:
        return ColorId.themeBlue;
      case ThemeId.CLASSIC:
        return ColorId.themeClassic;
      case ThemeId.CYAN:
        return ColorId.themeCyan;
      case ThemeId.GREEN:
        return ColorId.themeGreen;
      case ThemeId.NIGHT_BLACK:
        return ColorId.themeNightBlack;
      case ThemeId.NIGHT_BLUE:
        return ColorId.themeNightBlue;
      case ThemeId.ORANGE:
        return ColorId.themeOrange;
      case ThemeId.PINK:
        return ColorId.themePink;
      case ThemeId.RED:
        return ColorId.themeRed;
      case ThemeId.WHITE_BLACK:
        return ColorId.themeWhiteBlack;
    }
    throw newError(themeId, "themeId");
  }

  private static final float DARK_OVERLAY_ALPHA = .55f;
  private static final float LIGHT_OVERLAY_ALPHA = .3f;

  public static float getPopupOverlayAlpha () {
    return LIGHT_OVERLAY_ALPHA + (DARK_OVERLAY_ALPHA - LIGHT_OVERLAY_ALPHA) * getDarkFactor();
  }

  // == Solid colors ==

  public static int backgroundColor () {
    return getColor(ColorId.background);
  }

  public static int fillingColor () {
    return getColor(ColorId.filling);
  }

  public static int pressedFillingColor () {
    return getColor(ColorId.fillingPressed);
  }

  /*public static int pressedFillingTransparentColor () {
    return getColor(ColorId.fillingPressedTransparent);
  }*/

  public static int fillingTextSelectionColor () {
    return getColor(ColorId.textSelectionHighlight);
  }

  public static int separatorColor () {
    return getColor(ColorId.separator);
  }

  public static int progressColor () {
    return getColor(ColorId.progress);
  }

  public static int placeholderColor () {
    return getColor(ColorId.placeholder);
  }

  public static int headerPlaceholderColor () {
    return getColor(ColorId.placeholder);
  }

  public static int overlayColor () {
    return getColor(ColorId.previewBackground);
  }

  public static int iconColor () {
    return getColor(ColorId.icon);
  }

  public static int backgroundIconColor () {
    return getColor(ColorId.background_icon);
  }

  public static int iconLightColor () {
    return getColor(ColorId.iconLight);
  }

  public static int ticksColor () {
    return getColor(ColorId.ticks);
  }

  public static int chatSendButtonColor () {
    return getColor(ColorId.chatSendButton);
  }

  public static int checkFillingColor () {
    return getColor(ColorId.checkActive);
  }

  public static int checkCheckColor () {
    return getColor(ColorId.checkContent);
  }

  public static float HEADER_TEXT_DECENT_ALPHA = .6f;

  // Text

  public static int textAccentColor () {
    return getColor(ColorId.text);
  }

  public static int textDecentColor () {
    return getColor(ColorId.textLight);
  }

  public static int textAccent2Color () {
    return getColor(ColorId.background_text);
  }

  public static int textDecent2Color () {
    return getColor(ColorId.background_textLight);
  }

  public static int textSecureColor () {
    return getColor(ColorId.textSecure);
  }

  public static int textRedColor () {
    return getColor(ColorId.textNegative);
  }

  public static int textPlaceholderColor () {
    return getColor(ColorId.textPlaceholder);
  }

  public static int textLinkColor () {
    return getColor(ColorId.textLink);
  }

  public static int textLinkHighlightColor () {
    return getColor(ColorId.textLinkPressHighlight);
  }

  // == Navigation colors ==

  // Header

  public static int headerColor () {
    return getColor(ColorId.headerBackground);
  }

  public static int headerTextColor () {
    return getColor(ColorId.headerText);
  }

  public static int headerSubtitleColor () {
    return ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.headerTextColor());
  }

  public static int passcodeSubtitleColor () {
    return ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(ColorId.passcodeText));
  }

  public static int headerBackColor () {
    return getColor(ColorId.headerIcon);
  }

  // Common header stuff

  public static int headerFloatBackgroundColor () {
    return getColor(ColorId.overlayFilling);
  }

  public static int headerFloatIconColor () {
    return getColor(ColorId.headerButtonIcon);
  }

  public static int chatSelectionColor () {
    return getColor(ColorId.messageSelection);
  }

  /*public static int chatMessageSelectionColor () {
    return getColor(ColorId.chatMessageSelection);
  }*/

  public static int chatAuthorColor (boolean isOutBubble) {
    return getColor(ColorId.messageAuthor);
  }

  public static int chatAuthorDeadColor () {
    return getColor(ColorId.nameInactive);
  }

  public static int chatUnreadSeparatorBackgroundColor () {
    return getColor(ColorId.unread);
  }

  public static int chatUnreadSeparatorTextColor () {
    return getColor(ColorId.unreadText);
  }

  public static int chatQuickActionColor () {
    return getColor(ColorId.messageSwipeBackground);
  }

  public static int chatQuickActionTextColor () {
    return getColor(ColorId.messageSwipeContent);
  }

  public static int chatVerticalLineColor () {
    return getColor(ColorId.messageVerticalLine);
  }

  public static int badgeColor () {
    return getColor(ColorId.badge);
  }

  public static int badgeTextColor () {
    return getColor(ColorId.badgeText);
  }

  public static int badgeMutedColor () {
    return getColor(ColorId.badgeMuted);
  }

  public static int badgeFailedColor () {
    return getColor(ColorId.badgeFailed);
  }

  public static int chatListMuteColor () {
    return getColor(ColorId.chatListMute);
  }

  public static int chatListActionColor () {
    return getColor(ColorId.chatListAction);
  }

  public static int togglerActiveColor () {
    return getColor(ColorId.togglerActive);
  }

  public static int togglerInactiveColor () {
    return getColor(ColorId.togglerInactive);
  }

  public static int togglerActiveFillingColor () {
    return getColor(ColorId.togglerActiveBackground);
  }

  public static int togglerInactiveFillingColor () {
    return getColor(ColorId.togglerInactiveBackground);
  }

  public static int radioOutlineColor () {
    return getColor(ColorId.controlInactive);
  }

  public static int radioCheckColor () {
    return getColor(ColorId.controlContent);
  }

  public static int radioFillingColor () {
    return getColor(ColorId.controlActive);
  }

  public static int profileSelectionColor () {
    return getColor(ColorId.profileSectionActive);
  }

  public static int profileSelectionTextColor () {
    return getColor(ColorId.profileSectionActiveContent);
  }

  public static int inlineTextColor (boolean isOutBubble) {
    return getColor(isOutBubble ? ColorId.bubbleOut_inlineText : ColorId.inlineText);
  }

  public static int inlineTextActiveColor () {
    return getColor(ColorId.inlineContentActive);
  }

  public static int inlineOutlineColor (boolean isOutBubble) {
    return getColor(isOutBubble ? ColorId.bubbleOut_inlineOutline : ColorId.inlineOutline);
  }

  public static int inlineIconColor (boolean isOutBubble) {
    return getColor(isOutBubble ? ColorId.bubbleOut_inlineIcon : ColorId.inlineIcon);
  }

  public static int introDotActiveColor () {
    return getColor(ColorId.introSectionActive);
  }

  public static int introDotInactiveColor () {
    return getColor(ColorId.introSection);
  }

  public static int textInputActiveColor () {
    return getColor(ColorId.inputActive);
  }

  // == Background drawables ==

  // Filled drawable

  public static Drawable fillingSelector () {
    return fillingSelector(ColorId.filling);
  }

  public static Drawable fillingSelector (@ColorId int backgroundColorId) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
      return fillingRippleSelector(backgroundColorId);
    } else {
      return fillingSimpleSelector(backgroundColorId);
    }
  }

  public static Drawable fillingSelector (@ColorId int backgroundColorId, float radius) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
      return fillingRippleSelector(backgroundColorId, radius);
    } else {
      return fillingSimpleSelector(backgroundColorId, radius);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable fillingRippleSelector (@ColorId int backgroundColorId, float radius) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {RIPPLE_COLOR}),
      new FillingDrawable(backgroundColorId, radius), createRoundRectDrawable(0xFFFFFFFF, radius)
    );
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable fillingRippleSelector (@ColorId int backgroundColorId) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {RIPPLE_COLOR}),
      new FillingDrawable(backgroundColorId),
      new ColorDrawable(0xffffffff)
    );
  }

  public static int getBackgroundColorId (View view) {
    if (view == null)
      return 0;
    Drawable drawable = view.getBackground();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (drawable instanceof android.graphics.drawable.RippleDrawable) {
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) drawable;
        int count = ripple.getNumberOfLayers();
        for (int i = 0; i < count; i++) {
          Drawable d = ripple.getDrawable(i);
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
            return ((FillingDrawable) d).getColorId();
          }
        }
        return 0;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
          return ((FillingDrawable) d).getColorId();
        }
      }
      return 0;
    }
    if (drawable instanceof FillingDrawable) {
      return ((FillingDrawable) drawable).getColorId();
    }
    return 0;
  }

  public static @Nullable FillingDrawable findFillingDrawable (View view) {
    if (view == null)
      return null;
    Drawable drawable = view.getBackground();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (drawable instanceof android.graphics.drawable.RippleDrawable) {
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) drawable;
        int count = ripple.getNumberOfLayers();
        for (int i = 0; i < count; i++) {
          Drawable d = ripple.getDrawable(i);
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
            return (FillingDrawable) d;
          }
        }
        return null;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
          return (FillingDrawable) d;
        }
      }
      return null;
    }
    if (drawable instanceof FillingDrawable) {
      return (FillingDrawable) drawable;
    }
    return null;
  }

  public static void changeBackgroundColorId (View view, @ColorId int backgroundColorId) {
    if (view == null)
      return;
    Drawable drawable = view.getBackground();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (drawable instanceof android.graphics.drawable.RippleDrawable) {
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) drawable;
        int count = ripple.getNumberOfLayers();
        for (int i = 0; i < count; i++) {
          Drawable d = ripple.getDrawable(i);
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
            ((FillingDrawable) d).setColorId(backgroundColorId);
          }
        }
        return;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
          ((FillingDrawable) d).setColorId(backgroundColorId);
        }
      }
      return;
    }
    if (drawable instanceof FillingDrawable) {
      ((FillingDrawable) drawable).setColorId(backgroundColorId);
      return;
    }
  }

  public static void forceTheme (View view, @Nullable ThemeDelegate forcedTheme) {
    if (view == null)
      return;
    Drawable drawable = view.getBackground();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (drawable instanceof android.graphics.drawable.RippleDrawable) {
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) drawable;
        int count = ripple.getNumberOfLayers();
        for (int i = 0; i < count; i++) {
          Drawable d = ripple.getDrawable(i);
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
            ((FillingDrawable) d).setForcedTheme(forcedTheme);
          }
        }
        return;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != ColorId.fillingPressed) {
          ((FillingDrawable) d).setForcedTheme(forcedTheme);
        }
      }
      return;
    }
    if (drawable instanceof FillingDrawable) {
      ((FillingDrawable) drawable).setForcedTheme(forcedTheme);
      return;
    }
  }

  public static void changeSelector (View view, boolean forceLegacy, @ColorId int backgroundColorId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (view == null)
        return;
      Drawable d = view.getBackground();
      if (d instanceof android.graphics.drawable.RippleDrawable) {
        if (forceLegacy) {
          ViewUtils.setBackground(view, fillingSimpleSelector(backgroundColorId));
        }
      } else if (d instanceof CustomStateListDrawable) {
        if (!forceLegacy) {
          ViewUtils.setBackground(view, fillingRippleSelector(backgroundColorId));
        }
      }
    }
  }

  public static final int RIPPLE_COLOR = 0x50a0a0a0;
  public static final int HALF_RIPPLE_COLOR = 0x28a0a0a0;

  private static Drawable fillingSimpleSelector (@ColorId int backgroundColorId) {
    return fillingSimpleSelector(backgroundColorId, 0);
  }

  private static Drawable fillingSimpleSelector (@ColorId int backgroundColorId, float radius) {
    return Drawables.getColorSelector(new FillingDrawable(backgroundColorId, radius), new FillingDrawable(ColorId.fillingPressed, radius));
  }

  // Transparent drawable

  public static Drawable filteredDrawable (@DrawableRes int res, @ColorId int colorId, @Nullable ViewController<?> themeProvider) {
    Drawable drawable = ViewSupport.getDrawableFilter(UI.getAppContext(), res, new PorterDuffColorFilter(Theme.getColor(colorId), PorterDuff.Mode.MULTIPLY));
    if (themeProvider != null) {
      themeProvider.addThemeSpecialFilterListener(drawable, colorId);
    }
    return drawable;
  }

  public static Drawable transparentSelector () {
    return transparentSelector(RIPPLE_COLOR);
  }

  public static Drawable transparentWhiteSelector () {
    return transparentSelector(0x30ffffff);
  }

  public static Drawable transparentBlackSelector () {
    return transparentSelector(0x40a0a0a0);
  }

  public static Drawable transparentRoundSelector (float radius) {
    return transparentSelector(RIPPLE_COLOR, radius);
  }

  private static Drawable transparentSelector (final int color, final float radius) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return transparentRippleSelector(color, radius);
    } else {
      return transparentSimpleSelector(color, radius);
    }
  }

  private static Drawable transparentSelector (final int color) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return transparentRippleSelector(color);
    } else {
      return transparentSimpleSelector(color);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable transparentRippleSelector (final int color) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {color}),
      null,
      new ColorDrawable(0xffffffff)
    );
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable transparentRippleSelector (final int color, final float radius) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {color}),
      null,
      createRoundRectDrawable(color, radius)
    );
  }

  private static Drawable transparentSimpleSelector (final int color) {
    return Drawables.getColorSelector(null, new ColorDrawable(color));
  }

  private static Drawable transparentSimpleSelector (final int color, final float radius) {
    return Drawables.getColorSelector(null, createRoundRectDrawable(color, radius));
  }

  // Custom selector

  public static Drawable customSelector (Drawable drawable, Drawable legacyPressedDrawable) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return customRippleSelector(drawable);
    } else {
      return customSimpleSelector(drawable, legacyPressedDrawable);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable customRippleSelector (Drawable drawable) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {0x40a0a0a0}),
      drawable,
      null
    );
  }

  private static Drawable customSimpleSelector (Drawable drawable, Drawable pressedDrawable) {
    return Drawables.getColorSelector(drawable, pressedDrawable);
  }

  // Circle selector

  public static Drawable circleSelector (final float size, final @ColorId int colorId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return circleRippleSelector(size, colorId);
    } else {
      return circleSimpleSelector(size, colorId);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable circleRippleSelector (final float size, final @ColorId int colorId) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {0x40a0a0a0}),
      new CircleDrawable(colorId, size, false),
      null
    );
  }

  private static Drawable circleSimpleSelector (final float size, final @ColorId int colorId) {
    return Drawables.getColorSelector(new CircleDrawable(colorId, size, false), new CircleDrawable(colorId, size, true));
  }

  // Circle selector

  public static Drawable rectSelector (final float size, final float padding, final @ColorId int color) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return rectRippleSelector(size, padding, color);
    } else {
      return rectSimpleSelector(size, padding, color);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable rectRippleSelector (final float size, final float padding, final @ColorId int colorId) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {0x60a0a0a0}),
      new RectDrawable(colorId, size, padding, false),
      null
    );
  }

  private static Drawable rectSimpleSelector (final float size, final float padding, final @ColorId int colorId) {
    return Drawables.getColorSelector(new RectDrawable(colorId, size, padding, false), new RectDrawable(colorId, size, padding, true));
  }

  // Themes

  public static int dialogTheme () {
    return isDark() ? R.style.DialogThemeDark : R.style.DialogTheme;
  }

  public static RuntimeException newError (int resId, String name) {
    return new IllegalArgumentException(name + " == " + resId + " / 0x" + Integer.toHexString(resId) + " (" + Lang.getResourceEntryName(resId) + ")");
  }

  @SuppressWarnings("SwitchIntDef")
  public static boolean isDarkTheme (@ThemeId int themeId) {
    return getProperty(PropertyId.DARK, themeId) == 1;
  }

  @SuppressWarnings("SwitchIntDef")
  public static boolean isDarkTheme (Settings prefs, @ThemeId int themeId) {
    return getProperty(prefs, PropertyId.DARK, themeId) == 1;
  }

  public static boolean needLightStatusBar () {
    return ThemeManager.instance().needLightStatusBar();
  }

  public static boolean needLightStatusBar (@ThemeId int themeId) {
    return (int) getProperty(PropertyId.LIGHT_STATUS_BAR, themeId) == 1;
  }

  public static float getOverrideProperty (@PropertyId int propertyId) {
    // TODO validate propertyId?
    return getProperty(propertyId);
  }

  public static float getDarkFactor () {
    return getProperty(PropertyId.DARK);
  }

  public static float getShadowDepth () {
    return Theme.getProperty(PropertyId.SHADOW_DEPTH);
  }

  public static float getSeparatorReplacement () {
    return Theme.getProperty(PropertyId.REPLACE_SHADOWS_WITH_SEPARATORS);
  }

  public static float avatarRadiusDefault () {
    return Theme.getProperty(PropertyId.AVATAR_RADIUS);
  }

  public static float getBubbleOutlineFactor () {
    return Theme.getProperty(PropertyId.BUBBLE_OUTLINE);
  }

  public static float getBubbleOutlineSize () {
    return Theme.getProperty(PropertyId.BUBBLE_OUTLINE_SIZE);
  }

  public static float getBubbleDateRadius () {
    return Theme.getProperty(PropertyId.BUBBLE_DATE_CORNER);
  }

  public static float getDateRadius () {
    return Theme.getProperty(PropertyId.DATE_CORNER);
  }

  public static float getImageRadius () {
    return Theme.getProperty(PropertyId.IMAGE_CORNER);
  }

  private static final boolean BUBBLE_BIG_RADIUS_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

  public static float getBubbleDefaultRadius () {
    return Theme.getProperty(BUBBLE_BIG_RADIUS_AVAILABLE ? PropertyId.BUBBLE_CORNER : PropertyId.BUBBLE_CORNER_LEGACY);
  }

  public static float getBubbleMergeRadius () {
    return Theme.getProperty(BUBBLE_BIG_RADIUS_AVAILABLE ? PropertyId.BUBBLE_CORNER_MERGED : PropertyId.BUBBLE_CORNER_LEGACY);
  }

  public static boolean isBubbleRadiusBig (float radius) {
    return BUBBLE_BIG_RADIUS_AVAILABLE && radius == Screen.dp(18f);
  }

  public static int subtitleColor (int color) {
    return ColorUtils.alphaColor(getSubtitleAlpha(), color);
  }

  public static float getBubbleUnreadShadow () {
    return Theme.getProperty(PropertyId.BUBBLE_UNREAD_SHADOW);
  }

  private static Drawable createRoundRectDrawable (int color, float radius) {
    final int rad = Screen.dp(radius);
    ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
    defaultDrawable.getPaint().setColor(color);
    return defaultDrawable;
  }

  // TODO REMOVE

  public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor) {
    return createSimpleSelectorRoundRectDrawable(rad, defaultColor, pressedColor, pressedColor);
  }

  public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor, int maskColor) {
    ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
    defaultDrawable.getPaint().setColor(defaultColor);
    ShapeDrawable pressedDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
    pressedDrawable.getPaint().setColor(maskColor);
    if (Build.VERSION.SDK_INT >= 21) {
      ColorStateList colorStateList = new ColorStateList(
        new int[][]{StateSet.WILD_CARD},
        new int[]{pressedColor}
      );
      return new RippleDrawable(colorStateList, defaultDrawable, pressedDrawable);
    } else {
      StateListDrawable stateListDrawable = new StateListDrawable();
      stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
      stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
      stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
      return stateListDrawable;
    }
  }

  public static Drawable createRoundRectDrawable(int rad, int defaultColor) {
    ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
    defaultDrawable.getPaint().setColor(defaultColor);
    return defaultDrawable;
  }

  public static Drawable getRoundRectSelectorDrawable(int color) {
    if (Build.VERSION.SDK_INT >= 21) {
      Drawable maskDrawable = createRoundRectDrawable(Screen.dp(3), 0xffffffff);
      ColorStateList colorStateList = new ColorStateList(
        new int[][]{StateSet.WILD_CARD},
        new int[]{(color & 0x00ffffff) | 0x19000000}
      );
      return new RippleDrawable(colorStateList, null, maskDrawable);
    } else {
      StateListDrawable stateListDrawable = new StateListDrawable();
      stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, createRoundRectDrawable(Screen.dp(3), (color & 0x00ffffff) | 0x19000000));
      stateListDrawable.addState(new int[]{android.R.attr.state_selected}, createRoundRectDrawable(Screen.dp(3), (color & 0x00ffffff) | 0x19000000));
      stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
      return stateListDrawable;
    }
  }
}
