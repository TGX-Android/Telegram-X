package org.thunderdog.challegram.util.text;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;

public interface TextColorSetThemed extends TextColorSet {
  @Nullable
  default ThemeDelegate forcedTheme () {
    return null;
  }

  @NonNull
  default ThemeDelegate colorTheme () {
    ThemeDelegate forcedTheme = forcedTheme();
    return forcedTheme != null ? forcedTheme : ThemeManager.instance().currentTheme();
  }

  @ThemeColorId
  int defaultTextColorId ();
  @ThemeColorId
  default int iconColorId () {
    return defaultTextColorId();
  }
  @ThemeColorId
  default int clickableTextColorId (boolean isPressed) {
    return defaultTextColorId();
  }
  @ThemeColorId
  default int pressedBackgroundColorId () {
    return 0;
  }
  @ThemeColorId
  default int staticBackgroundColorId () {
    return 0;
  }
  @ThemeColorId
  default int backgroundColorId (boolean isPressed) {
    return isPressed ? pressedBackgroundColorId() : staticBackgroundColorId();
  }
  @ThemeColorId
  default int outlineColorId (boolean isPressed) {
    return 0;
  }
  @ColorInt
  default int defaultTextColor () {
    int colorId = defaultTextColorId();
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @Override
  default int iconColor () {
    int colorId = iconColorId();
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @ColorInt
  default int clickableTextColor (boolean isPressed) {
    int colorId = clickableTextColorId(isPressed);
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @ColorInt
  default int backgroundColor (boolean isPressed) {
    int colorId = backgroundColorId(isPressed);
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @ColorInt
  default int outlineColor (boolean isPressed) {
    int colorId = outlineColorId(isPressed);
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
}
