/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;

import java.util.Locale;

public final class MiniAppTheme {
  private MiniAppTheme () { }

  public static TdApi.ThemeParameters parameters () {
    return new TdApi.ThemeParameters(
      Theme.getColor(ColorId.filling), Theme.getColor(ColorId.background), Theme.getColor(ColorId.headerBackground),
      Theme.getColor(ColorId.filling), Theme.getColor(ColorId.filling), Theme.getColor(ColorId.separator),
      Theme.getColor(ColorId.text), Theme.getColor(ColorId.textLink), Theme.getColor(ColorId.textLink),
      Theme.getColor(ColorId.textLight), Theme.getColor(ColorId.textNegative), Theme.getColor(ColorId.textLight),
      Theme.getColor(ColorId.textLink), Theme.getColor(ColorId.fillingPositive), Theme.getColor(ColorId.fillingPositiveContent));
  }

  public static String hex (int color) {
    return String.format(Locale.US, "#%06x", color & 0xffffff);
  }

  public static JSONObject json () {
    TdApi.ThemeParameters t = parameters();
    return object("bg_color", hex(t.backgroundColor), "secondary_bg_color", hex(t.secondaryBackgroundColor),
      "header_bg_color", hex(t.headerBackgroundColor), "bottom_bar_bg_color", hex(t.bottomBarBackgroundColor),
      "section_bg_color", hex(t.sectionBackgroundColor), "section_separator_color", hex(t.sectionSeparatorColor),
      "text_color", hex(t.textColor), "accent_text_color", hex(t.accentTextColor), "section_header_text_color", hex(t.sectionHeaderTextColor),
      "subtitle_text_color", hex(t.subtitleTextColor), "destructive_text_color", hex(t.destructiveTextColor),
      "hint_color", hex(t.hintColor), "link_color", hex(t.linkColor), "button_color", hex(t.buttonColor), "button_text_color", hex(t.buttonTextColor));
  }

  public static JSONObject object (Object... pairs) {
    JSONObject result = new JSONObject();
    try {
      for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    return result;
  }
}
