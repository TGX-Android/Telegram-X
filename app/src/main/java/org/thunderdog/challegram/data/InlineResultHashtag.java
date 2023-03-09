/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/07/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.TextUtils;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;

public class InlineResultHashtag extends InlineResult<String> {
  private final CharSequence hashtag;

  public InlineResultHashtag (BaseActivity context, Tdlib tdlib, String hashtag, String query) {
    super(context, tdlib, TYPE_HASHTAG, null, '#' + hashtag);
    this.hashtag = Strings.highlightWords(data, query, 1, null);
  }

  @Override
  public void setForceDarkMode (boolean forceDarkMode) {
    super.setForceDarkMode(forceDarkMode);
    Strings.forceHighlightSpansThemeId(hashtag, forceDarkMode ? ThemeId.NIGHT_BLACK : ThemeId.NONE);
  }

  private CharSequence trimmedHashtag;
  private Layout hashtagLayout;

  @Override
  protected void layoutInternal (int contentWidth) {
    int availWidth = contentWidth - Screen.dp(12f) * 2;
    trimmedHashtag = TextUtils.ellipsize(hashtag, Paints.getCommandPaint(), availWidth, TextUtils.TruncateAt.END);
    if (trimmedHashtag instanceof String) {
      hashtagLayout = null;
    } else {
      hashtagLayout = U.createLayout(trimmedHashtag, availWidth, Paints.getCommandPaint());
    }
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(4f) * 2 + Screen.dp(14f) * 2;
  }

  @Override
  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) {
    if (trimmedHashtag != null) {
      int startX = Screen.dp(12f);
      startY += Screen.dp(4f) + Screen.dp(14f) + Screen.dp(5f);
      final int textColor = forceDarkMode ? Theme.getColor(R.id.theme_color_text, ThemeId.NIGHT_BLACK) : Theme.textAccentColor();
      if (hashtagLayout != null) {
        int savedColor = Paints.getCommandPaint().getColor();
        Paints.getCommandPaint().setColor(textColor);
        c.save();
        c.translate(startX, startY - Screen.dp(13f));
        hashtagLayout.draw(c);
        c.restore();
        Paints.getCommandPaint().setColor(savedColor);
      } else {
        c.drawText((String) trimmedHashtag, startX, startY, Paints.getCommandPaint(textColor));
      }
    }
  }
}
