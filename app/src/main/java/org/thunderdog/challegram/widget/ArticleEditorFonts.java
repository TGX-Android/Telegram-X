/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Typeface;

/** The unmodified Merriweather heading face used by Telegram's rich editor. */
public final class ArticleEditorFonts {
  private ArticleEditorFonts () { }
  private static Typeface heading;
  public static synchronized Typeface heading (Context context) {
    if (heading == null) heading = Typeface.createFromAsset(context.getAssets(), "fonts/article_mw_bold.ttf");
    return heading;
  }
}
