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
 * File created on 08/01/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;

import me.vkryl.core.StringUtils;

public class InlineResultEmojiSuggestion extends InlineResult<N.Suggestion> {
  public static final char[] SPECIAL_SPLITTERS = {'_', '{', '}', '/', '(', ')', ':', ';'};

  private CharSequence text;
  private CharSequence emoji;
  private float textWidth;

  public InlineResultEmojiSuggestion (BaseActivity context, Tdlib tdlib, N.Suggestion suggestion, @Nullable String query) {
    super(context, tdlib, TYPE_EMOJI_SUGGESTION, null, suggestion);
    this.emoji = Emoji.instance().replaceEmoji(suggestion.emoji);
    this.text = Strings.highlightWords(suggestion.label, query, 1, SPECIAL_SPLITTERS);
    this.textWidth = U.measureText(suggestion.label, Paints.getTextPaint15());
  }

  @Override
  public void setForceDarkMode (boolean forceDarkMode) {
    super.setForceDarkMode(forceDarkMode);
    Strings.forceHighlightSpansThemeId(text, forceDarkMode ? ThemeId.NIGHT_BLACK : ThemeId.NONE);
  }

  public String getEmoji () {
    return data.emoji;
  }

  private Layout emojiLayout;

  private CharSequence trimmedText;
  private Layout textLayout;

  @Override
  protected void layoutInternal (int contentWidth) {
    int availWidth = contentWidth - Screen.dp(12f) - Screen.dp(55f) - Screen.dp(24f);
    trimmedText = StringUtils.isEmpty(text) ? text : textWidth > availWidth ? TextUtils.ellipsize(text, Paints.getTextPaint15(), availWidth, TextUtils.TruncateAt.END) : text;
    if (StringUtils.isEmpty(trimmedText) || trimmedText instanceof String) {
      textLayout = null;
    } else {
      textLayout = U.createLayout(trimmedText, availWidth, Paints.getTextPaint15());
    }
    emojiLayout = U.createLayout(emoji, availWidth, Paints.getTitlePaint(false));
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(4f) * 2 + Screen.dp(14f) * 2;
  }

  @Override
  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) {
    int startX = Screen.dp(55f);
    startY += Screen.dp(4f) + Screen.dp(14f) + Screen.dp(5f);
    if (emojiLayout != null) {
      c.save();
      c.translate(startX, startY - Screen.dp(13f));
      emojiLayout.draw(c);
      c.restore();
      startX += Screen.dp(24f);
    }
    if (trimmedText != null) {
      final int color = forceDarkMode ? Theme.getColor(R.id.theme_color_text, ThemeId.NIGHT_BLACK) : Theme.textAccentColor();
      if (textLayout != null) {
        c.save();
        c.translate(startX, startY - Screen.dp(13f));
        Paint paint = Paints.getTextPaint15(color);
        textLayout.draw(c);
        paint.setColor(color);
        c.restore();
      } else if (!StringUtils.isEmpty(trimmedText)) {
        c.drawText((String) trimmedText, startX, startY, Paints.getTextPaint15(color));
      }
    }
  }
}
