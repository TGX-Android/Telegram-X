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

/**
 * Date: 17/01/2017
 * Author: default
 */

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

  public static int avatarBigToSmall (@ThemeColorId int avatarColorId) {
    switch (avatarColorId) {
      case R.id.theme_color_avatarInactive_big:
        return R.id.theme_color_avatarInactive;
      case R.id.theme_color_avatarReplies_big:
        return R.id.theme_color_avatarReplies;
      case R.id.theme_color_avatarPink_big:
        return R.id.theme_color_avatarPink;
      case R.id.theme_color_avatarRed_big:
        return R.id.theme_color_avatarRed;
      case R.id.theme_color_avatarOrange_big:
        return R.id.theme_color_avatarOrange;
      case R.id.theme_color_avatarYellow_big:
        return R.id.theme_color_avatarYellow;
      case R.id.theme_color_avatarGreen_big:
        return R.id.theme_color_avatarGreen;
      case R.id.theme_color_avatarBlue_big:
        return R.id.theme_color_avatarBlue;
      case R.id.theme_color_avatarCyan_big:
        return  R.id.theme_color_avatarCyan;
      case R.id.theme_color_avatarViolet_big:
        return R.id.theme_color_avatarViolet;
    }
    return 0;
  }
  
  public static int avatarSmallToBig (@ThemeColorId int avatarColorId) {
    switch (avatarColorId) {
      case R.id.theme_color_avatarInactive:
        return R.id.theme_color_avatarInactive_big;
      case R.id.theme_color_avatarSavedMessages:
        return R.id.theme_color_avatarSavedMessages_big;
      case R.id.theme_color_avatarReplies:
        return R.id.theme_color_avatarReplies_big;
      case R.id.theme_color_avatarPink:
        return R.id.theme_color_avatarPink_big;
      case R.id.theme_color_avatarRed:
        return R.id.theme_color_avatarRed_big;
      case R.id.theme_color_avatarOrange:
        return R.id.theme_color_avatarOrange_big;
      case R.id.theme_color_avatarYellow:
        return R.id.theme_color_avatarYellow_big;
      case R.id.theme_color_avatarGreen:
        return R.id.theme_color_avatarGreen_big;
      case R.id.theme_color_avatarBlue:
        return R.id.theme_color_avatarBlue_big;
      case R.id.theme_color_avatarCyan:
        return  R.id.theme_color_avatarCyan_big;
      case R.id.theme_color_avatarViolet:
        return R.id.theme_color_avatarViolet_big;
    }
    return 0;
  }

  public static float getSubtitleAlpha () {
    return getProperty(ThemeProperty.SUBTITLE_ALPHA);
  }

  public static String getColorName (@ThemeColorId int colorId) {
    return ThemeColors.getName(colorId);
  }

  public static String getPropertyName (@ThemeProperty int propertyId) {
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
    int usageId = (int) getProperty(ThemeProperty.WALLPAPER_USAGE_ID, themeId);
    if (usageId == 2) {
      return getCustomWallpaperIdentifier(themeId);
    }
    return usageId;
  }

  public static int getWallpaperIdentifier () {
    int usageId = (int) getProperty(ThemeProperty.WALLPAPER_USAGE_ID);
    if (usageId == 2) {
      return getCustomWallpaperIdentifier(ThemeManager.instance().currentThemeId());
    }
    return usageId;
  }

  public static int getWallpaperIdentifier (ThemeDelegate theme) {
    final int usageId = (int) theme.getProperty(ThemeProperty.WALLPAPER_USAGE_ID);
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

  private static float getProperty (final @ThemeProperty int propertyId) {
    return ThemeManager.instance().currentTheme().getProperty(propertyId);
  }

  @ColorInt
  public static int getColor (final @ThemeColorId int colorId) {
    return ThemeManager.instance().currentTheme().getColor(colorId);
  }

  public static int getColorFast (final @ThemeColorId int colorId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null)
      return currentTheme.getColor(colorId);
    return ThemeSet.getColor(ThemeId.BLUE, colorId);
  }

  public static float getProperty (final @ThemeProperty int propertyId, final @ThemeId int themeId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null && themeId == currentTheme.getId())
      return currentTheme.getProperty(propertyId);
    return ThemeSet.getProperty(themeId, propertyId);
  }

  public static float getProperty (final Settings prefs, final @ThemeProperty int propertyId, final @ThemeId int themeId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null && themeId == currentTheme.getId())
      return currentTheme.getProperty(propertyId);
    return ThemeSet.getProperty(prefs, themeId, propertyId);
  }

  @ColorInt
  public static int getColor (final @ThemeColorId int colorId, final @ThemeId int themeId) {
    ThemeDelegate currentTheme = ThemeManager.currentThemeFast();
    if (currentTheme != null && themeId == currentTheme.getId())
      return currentTheme.getColor(colorId);
    return ThemeSet.getColor(themeId, colorId);
  }

  //
  @ThemeColorId
  public static int getCircleColorId (@ThemeId int themeId) {
    switch (themeId) {
      case ThemeId.NONE:
      case ThemeId.TEMPORARY:
      case ThemeId.CUSTOM:
        break;
      case ThemeId.BLACK_WHITE:
        return R.id.theme_color_themeBlackWhite;
      case ThemeId.BLUE:
        return R.id.theme_color_themeBlue;
      case ThemeId.CLASSIC:
        return R.id.theme_color_themeClassic;
      case ThemeId.CYAN:
        return R.id.theme_color_themeCyan;
      case ThemeId.GREEN:
        return R.id.theme_color_themeGreen;
      case ThemeId.NIGHT_BLACK:
        return R.id.theme_color_themeNightBlack;
      case ThemeId.NIGHT_BLUE:
        return R.id.theme_color_themeNightBlue;
      case ThemeId.ORANGE:
        return R.id.theme_color_themeOrange;
      case ThemeId.PINK:
        return R.id.theme_color_themePink;
      case ThemeId.RED:
        return R.id.theme_color_themeRed;
      case ThemeId.WHITE_BLACK:
        return R.id.theme_color_themeWhiteBlack;
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
    return getColor(R.id.theme_color_background);
  }

  public static int fillingColor () {
    return getColor(R.id.theme_color_filling);
  }

  public static int pressedFillingColor () {
    return getColor(R.id.theme_color_fillingPressed);
  }

  /*public static int pressedFillingTransparentColor () {
    return getColor(R.id.theme_color_fillingPressedTransparent);
  }*/

  public static int fillingTextSelectionColor () {
    return getColor(R.id.theme_color_textSelectionHighlight);
  }

  public static int separatorColor () {
    return getColor(R.id.theme_color_separator);
  }

  public static int progressColor () {
    return getColor(R.id.theme_color_progress);
  }

  public static int placeholderColor () {
    return getColor(R.id.theme_color_placeholder);
  }

  public static int headerPlaceholderColor () {
    return getColor(R.id.theme_color_placeholder);
  }

  public static int overlayColor () {
    return getColor(R.id.theme_color_previewBackground);
  }

  public static int iconColor () {
    return getColor(R.id.theme_color_icon);
  }

  public static int backgroundIconColor () {
    return getColor(R.id.theme_color_background_icon);
  }

  public static int iconLightColor () {
    return getColor(R.id.theme_color_iconLight);
  }

  public static int ticksColor () {
    return getColor(R.id.theme_color_ticks);
  }

  public static int chatSendButtonColor () {
    return getColor(R.id.theme_color_chatSendButton);
  }

  public static int checkFillingColor () {
    return getColor(R.id.theme_color_checkActive);
  }

  public static int checkCheckColor () {
    return getColor(R.id.theme_color_checkContent);
  }

  public static float HEADER_TEXT_DECENT_ALPHA = .6f;

  // Text

  public static int textAccentColor () {
    return getColor(R.id.theme_color_text);
  }

  public static int textDecentColor () {
    return getColor(R.id.theme_color_textLight);
  }

  public static int textAccent2Color () {
    return getColor(R.id.theme_color_background_text);
  }

  public static int textDecent2Color () {
    return getColor(R.id.theme_color_background_textLight);
  }

  public static int textSecureColor () {
    return getColor(R.id.theme_color_textSecure);
  }

  public static int textRedColor () {
    return getColor(R.id.theme_color_textNegative);
  }

  public static int textPlaceholderColor () {
    return getColor(R.id.theme_color_textPlaceholder);
  }

  public static int textLinkColor () {
    return getColor(R.id.theme_color_textLink);
  }

  public static int textLinkHighlightColor () {
    return getColor(R.id.theme_color_textLinkPressHighlight);
  }

  // == Navigation colors ==

  // Header

  public static int headerColor () {
    return getColor(R.id.theme_color_headerBackground);
  }

  public static int headerTextColor () {
    return getColor(R.id.theme_color_headerText);
  }

  public static int headerSubtitleColor () {
    return ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.headerTextColor());
  }

  public static int passcodeSubtitleColor () {
    return ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(R.id.theme_color_passcodeText));
  }

  public static int headerBackColor () {
    return getColor(R.id.theme_color_headerIcon);
  }

  // Common header stuff

  public static int headerFloatBackgroundColor () {
    return getColor(R.id.theme_color_overlayFilling);
  }

  public static int headerFloatIconColor () {
    return getColor(R.id.theme_color_headerButtonIcon);
  }

  public static int chatSelectionColor () {
    return getColor(R.id.theme_color_messageSelection);
  }

  /*public static int chatMessageSelectionColor () {
    return getColor(R.id.theme_color_chatMessageSelection);
  }*/

  public static int chatAuthorColor (boolean isOutBubble) {
    return getColor(R.id.theme_color_messageAuthor);
  }

  public static int chatAuthorDeadColor () {
    return getColor(R.id.theme_color_nameInactive);
  }

  public static int chatUnreadSeparatorBackgroundColor () {
    return getColor(R.id.theme_color_unread);
  }

  public static int chatUnreadSeparatorTextColor () {
    return getColor(R.id.theme_color_unreadText);
  }

  public static int chatQuickActionColor () {
    return getColor(R.id.theme_color_messageSwipeBackground);
  }

  public static int chatQuickActionTextColor () {
    return getColor(R.id.theme_color_messageSwipeContent);
  }

  public static int chatVerticalLineColor () {
    return getColor(R.id.theme_color_messageVerticalLine);
  }

  public static int badgeColor () {
    return getColor(R.id.theme_color_badge);
  }

  public static int badgeTextColor () {
    return getColor(R.id.theme_color_badgeText);
  }

  public static int badgeMutedColor () {
    return getColor(R.id.theme_color_badgeMuted);
  }

  public static int badgeFailedColor () {
    return getColor(R.id.theme_color_badgeFailed);
  }

  public static int chatListMuteColor () {
    return getColor(R.id.theme_color_chatListMute);
  }

  public static int chatListActionColor () {
    return getColor(R.id.theme_color_chatListAction);
  }

  public static int togglerActiveColor () {
    return getColor(R.id.theme_color_togglerActive);
  }

  public static int togglerInactiveColor () {
    return getColor(R.id.theme_color_togglerInactive);
  }

  public static int togglerActiveFillingColor () {
    return getColor(R.id.theme_color_togglerActiveBackground);
  }

  public static int togglerInactiveFillingColor () {
    return getColor(R.id.theme_color_togglerInactiveBackground);
  }

  public static int radioOutlineColor () {
    return getColor(R.id.theme_color_controlInactive);
  }

  public static int radioCheckColor () {
    return getColor(R.id.theme_color_controlContent);
  }

  public static int radioFillingColor () {
    return getColor(R.id.theme_color_controlActive);
  }

  public static int profileSelectionColor () {
    return getColor(R.id.theme_color_profileSectionActive);
  }

  public static int profileSelectionTextColor () {
    return getColor(R.id.theme_color_profileSectionActiveContent);
  }

  public static int inlineTextColor (boolean isOutBubble) {
    return getColor(isOutBubble ? R.id.theme_color_bubbleOut_inlineText : R.id.theme_color_inlineText);
  }

  public static int inlineTextActiveColor () {
    return getColor(R.id.theme_color_inlineContentActive);
  }

  public static int inlineOutlineColor (boolean isOutBubble) {
    return getColor(isOutBubble ? R.id.theme_color_bubbleOut_inlineOutline : R.id.theme_color_inlineOutline);
  }

  public static int inlineIconColor (boolean isOutBubble) {
    return getColor(isOutBubble ? R.id.theme_color_bubbleOut_inlineIcon : R.id.theme_color_inlineIcon);
  }

  public static int introDotActiveColor () {
    return getColor(R.id.theme_color_introSectionActive);
  }

  public static int introDotInactiveColor () {
    return getColor(R.id.theme_color_introSection);
  }

  public static int textInputActiveColor () {
    return getColor(R.id.theme_color_inputActive);
  }

  // == Background drawables ==

  // Filled drawable

  public static Drawable fillingSelector () {
    return fillingSelector(R.id.theme_color_filling);
  }

  public static Drawable fillingSelector (@ThemeColorId int backgroundColorId) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
      return fillingRippleSelector(backgroundColorId);
    } else {
      return fillingSimpleSelector(backgroundColorId);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable fillingRippleSelector (@ThemeColorId int backgroundColorId) {
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
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
            return ((FillingDrawable) d).getColorId();
          }
        }
        return 0;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
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
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
            return (FillingDrawable) d;
          }
        }
        return null;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
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

  public static void changeBackgroundColorId (View view, @ThemeColorId int backgroundColorId) {
    if (view == null)
      return;
    Drawable drawable = view.getBackground();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (drawable instanceof android.graphics.drawable.RippleDrawable) {
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) drawable;
        int count = ripple.getNumberOfLayers();
        for (int i = 0; i < count; i++) {
          Drawable d = ripple.getDrawable(i);
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
            ((FillingDrawable) d).setColorId(backgroundColorId);
          }
        }
        return;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
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
          if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
            ((FillingDrawable) d).setForcedTheme(forcedTheme);
          }
        }
        return;
      }
    }
    if (drawable instanceof CustomStateListDrawable) {
      ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
      for (Drawable d : drawables) {
        if (d instanceof FillingDrawable && ((FillingDrawable) d).getColorId() != R.id.theme_color_fillingPressed) {
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

  public static void changeSelector (View view, boolean forceLegacy, @ThemeColorId int backgroundColorId) {
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

  private static Drawable fillingSimpleSelector (@ThemeColorId int backgroundColorId) {
    return Drawables.getColorSelector(new FillingDrawable(backgroundColorId), new FillingDrawable(R.id.theme_color_fillingPressed));
  }

  // Transparent drawable

  public static Drawable filteredDrawable (@DrawableRes int res, @ThemeColorId int colorId, @Nullable ViewController<?> themeProvider) {
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

  private static Drawable transparentSimpleSelector (final int color) {
    return Drawables.getColorSelector(null, new ColorDrawable(color));
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

  public static Drawable circleSelector (final float size, final @ThemeColorId int colorId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return circleRippleSelector(size, colorId);
    } else {
      return circleSimpleSelector(size, colorId);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable circleRippleSelector (final float size, final @ThemeColorId int colorId) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {0x40a0a0a0}),
      new CircleDrawable(colorId, size, false),
      null
    );
  }

  private static Drawable circleSimpleSelector (final float size, final @ThemeColorId int colorId) {
    return Drawables.getColorSelector(new CircleDrawable(colorId, size, false), new CircleDrawable(colorId, size, true));
  }

  // Circle selector

  public static Drawable rectSelector (final float size, final float padding, final @ThemeColorId int color) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return rectRippleSelector(size, padding, color);
    } else {
      return rectSimpleSelector(size, padding, color);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private static Drawable rectRippleSelector (final float size, final float padding, final @ThemeColorId int colorId) {
    return new android.graphics.drawable.RippleDrawable(
      new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {0x60a0a0a0}),
      new RectDrawable(colorId, size, padding, false),
      null
    );
  }

  private static Drawable rectSimpleSelector (final float size, final float padding, final @ThemeColorId int colorId) {
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
    return getProperty(ThemeProperty.DARK, themeId) == 1;
  }

  @SuppressWarnings("SwitchIntDef")
  public static boolean isDarkTheme (Settings prefs, @ThemeId int themeId) {
    return getProperty(prefs, ThemeProperty.DARK, themeId) == 1;
  }

  public static boolean needLightStatusBar () {
    return ThemeManager.instance().needLightStatusBar();
  }

  public static boolean needLightStatusBar (@ThemeId int themeId) {
    return (int) getProperty(ThemeProperty.LIGHT_STATUS_BAR, themeId) == 1;
  }

  public static float getOverrideProperty (@ThemeProperty int propertyId) {
    // TODO validate propertyId?
    return getProperty(propertyId);
  }

  public static float getDarkFactor () {
    return getProperty(ThemeProperty.DARK);
  }

  public static float getShadowDepth () {
    return Theme.getProperty(ThemeProperty.SHADOW_DEPTH);
  }

  public static float getSeparatorReplacement () {
    return Theme.getProperty(ThemeProperty.REPLACE_SHADOWS_WITH_SEPARATORS);
  }

  public static float getBubbleOutlineFactor () {
    return Theme.getProperty(ThemeProperty.BUBBLE_OUTLINE);
  }

  public static float getBubbleOutlineSize () {
    return Theme.getProperty(ThemeProperty.BUBBLE_OUTLINE_SIZE);
  }

  public static float getBubbleDateRadius () {
    return Theme.getProperty(ThemeProperty.BUBBLE_DATE_CORNER);
  }

  public static float getDateRadius () {
    return Theme.getProperty(ThemeProperty.DATE_CORNER);
  }

  public static float getImageRadius () {
    return Theme.getProperty(ThemeProperty.IMAGE_CORNER);
  }

  private static final boolean BUBBLE_BIG_RADIUS_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

  public static float getBubbleDefaultRadius () {
    return Theme.getProperty(BUBBLE_BIG_RADIUS_AVAILABLE ? ThemeProperty.BUBBLE_CORNER : ThemeProperty.BUBBLE_CORNER_LEGACY);
  }

  public static float getBubbleMergeRadius () {
    return Theme.getProperty(BUBBLE_BIG_RADIUS_AVAILABLE ? ThemeProperty.BUBBLE_CORNER_MERGED : ThemeProperty.BUBBLE_CORNER_LEGACY);
  }

  public static boolean isBubbleRadiusBig (float radius) {
    return BUBBLE_BIG_RADIUS_AVAILABLE && radius == Screen.dp(18f);
  }

  public static int subtitleColor (int color) {
    return ColorUtils.alphaColor(getSubtitleAlpha(), color);
  }

  public static float getBubbleUnreadShadow () {
    return Theme.getProperty(ThemeProperty.BUBBLE_UNREAD_SHADOW);
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
