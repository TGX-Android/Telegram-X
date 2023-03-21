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
 * File created on 31/08/2022, 20:48.
 */

package org.thunderdog.challegram.util;

import android.text.InputFilter;
import android.text.NoCopySpan;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;

import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.tool.Strings;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.BitwiseUtils;

public class CharacterStyleFilter implements InputFilter {
  private final boolean allowTelegramEntities;

  public CharacterStyleFilter () {
    this(false);
  }

  public CharacterStyleFilter (boolean allowStyleEntities) {
    this.allowTelegramEntities = allowStyleEntities;
  }

  @Override
  public CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    if (source instanceof Spanned) {
      Spanned spanned = (Spanned) source;
      CharacterStyle[] spans = spanned.getSpans(start, end, CharacterStyle.class);

      List<CharacterStyle> spansToRemove = null;
      if (spans != null) {
        for (CharacterStyle span : spans) {
          if (shouldRemoveSpan(spanned, span)) {
            if (spansToRemove == null) {
              spansToRemove = new ArrayList<>();
            }
            spansToRemove.add(span);
          }
        }
      }
      if (spansToRemove != null) {
        SpannableStringBuilder b = new SpannableStringBuilder(source, start, end);
        for (CharacterStyle span : spansToRemove) {
          b.removeSpan(span);
        }
        return b;
      }
    }
    return null;
  }

  protected boolean isServiceSpan (Spanned spanned, CharacterStyle span) {
    return
      span instanceof SuggestionSpan ||
      span instanceof NoCopySpan ||
      span instanceof EmojiSpan ||
      BitwiseUtils.hasFlag(spanned.getSpanFlags(span), Spanned.SPAN_COMPOSING);
  }

  protected boolean shouldRemoveSpan (Spanned spanned, CharacterStyle span) {
    return !isServiceSpan(spanned, span) && !(allowTelegramEntities && canConvertToTelegramEntity(spanned, span));
  }

  private static boolean canConvertToTelegramEntity (Spanned spanned, CharacterStyle span) {
    if (TD.canConvertToEntityType(span)) {
      if (span instanceof URLSpan) {
        int start = spanned.getSpanStart(span);
        int end = spanned.getSpanEnd(span);
        String text = spanned.subSequence(start, end).toString();
        String url = ((URLSpan) span).getURL();
        if (text.equals(url)) // <a href="example.com">example.com</a>
          return false;
        if (Strings.isValidLink(text)) {
          if (Strings.hostsEqual(url, text))
            return true;
          // Hosts are different. Most likely some <a href="https://youtube.com/redirect?v=${real_url}">${real_url}</a>
          // TODO lookup for this domain in GET arguments? Decision for now: no, because redirects could be like t.co/${id} without real url
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
