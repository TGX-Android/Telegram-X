package org.thunderdog.challegram.util;

import androidx.annotation.ColorInt;

import org.thunderdog.challegram.theme.ThemeColorId;

/**
 * Date: 6/8/17
 * Author: default
 */

public interface ColorChangeAcceptorDelegate {
  void applyColor (@ThemeColorId int fromColorId, @ThemeColorId int toColorId, float factor);
  @ColorInt int getDrawColor ();
}
