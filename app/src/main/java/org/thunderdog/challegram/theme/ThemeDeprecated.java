/**
 * File created on 18/04/16 at 21:43
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.theme;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;

@Deprecated
public class ThemeDeprecated {
  /*public static @DrawableRes int transparentSelector () {
    return *//*TGTheme.isDark() ? R.drawable.selector_dark :*//* R.drawable.item_selector;
  }

  public static @DrawableRes int blackSelector () {
    return R.drawable.selector_black; // FIXME
  }*/

  public static @DrawableRes int headerSelector () {
    return /*TGTheme.isDark() ? R.drawable.bg_btn_header_dark :*/ R.drawable.bg_btn_header;
  }

  public static @DrawableRes int headerLightSelector () {
    return /*TGTheme.isDark() ? R.drawable.bg_btn_header_light_dark :*/ R.drawable.bg_btn_header_light;
  }
}
