/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.util.text;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import me.vkryl.core.ColorUtils;

/** Resolves article button colors at draw time so theme changes do not retain stale colors. */
final class ArticleButtonColors implements TextColorSet {
  private final int style;
  ArticleButtonColors (TdApi.ButtonStyle style) { this.style = style == null ? TdApi.ButtonStyleDefault.CONSTRUCTOR : style.getConstructor(); }
  private int accent () {
    if (style == TdApi.ButtonStyleDanger.CONSTRUCTOR) return Theme.getColor(ColorId.textNegative);
    if (style == TdApi.ButtonStyleSuccess.CONSTRUCTOR) return Theme.getColor(ColorId.fillingPositive);
    return Theme.textLinkColor();
  }
  @Override public int defaultTextColor () { return accent(); }
  @Override public int clickableTextColor (boolean pressed) { return accent(); }
  @Override public int backgroundColor (boolean pressed) { return style == TdApi.ButtonStyleLink.CONSTRUCTOR ? 0 : ColorUtils.alphaColor(pressed ? .28f : style == TdApi.ButtonStylePrimary.CONSTRUCTOR ? .22f : .12f, accent()); }
}
