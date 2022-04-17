package org.thunderdog.challegram.util.text;

import androidx.annotation.ColorInt;

import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.BitwiseUtils;

public interface TextColorSet {
  @ColorInt
  int defaultTextColor ();
  @ColorInt
  default int iconColor () {
    return defaultTextColor();
  }

  @ColorInt
  default int clickableTextColor (boolean isPressed) {
    return defaultTextColor();
  }
  @ColorInt
  default int backgroundColor (boolean isPressed) {
    return 0;
  }
  @ColorInt
  default int outlineColor (boolean isPressed) {
    return 0;
  }

  @ColorInt
  default int overlayColor (boolean isPressed) {
    return 0;
  }
  @ColorInt
  default int overlayOutlineColor (boolean isPressed) {
    return 0;
  }

  default int backgroundColorId (boolean isPressed) {
    return backgroundColor(isPressed);
  }
  default int outlineColorId (boolean isPressed) {
    return outlineColor(isPressed);
  }
  default long backgroundId (boolean isPressed) {
    return BitwiseUtils.mergeLong(backgroundColorId(isPressed), outlineColorId(isPressed));
  }

  default int overlayColorId (boolean isPressed) {
    return overlayColor(isPressed);
  }
  default int overlayOutlineColorId (boolean isPressed) {
    return overlayOutlineColor(isPressed);
  }
  default long overlayId (boolean isPressed) {
    return BitwiseUtils.mergeLong(overlayColorId(isPressed), overlayOutlineColorId(isPressed));
  }

  default int backgroundPadding () {
    return Screen.dp(3f);
  }
}
