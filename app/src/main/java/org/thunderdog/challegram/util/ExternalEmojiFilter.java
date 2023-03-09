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
 * File created on 31/08/2022, 21:42.
 */

package org.thunderdog.challegram.util;

import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;

import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiSpan;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

public class ExternalEmojiFilter implements InputFilter {
  private static class Replacement {
    private final ImageSpan originalSpan;
    private final int start, end;
    private final String text;
    private final EmojiSpan span;

    public Replacement (ImageSpan originalSpan, int start, int end, String text, EmojiSpan span) {
      this.originalSpan = originalSpan;
      this.start = start;
      this.end = end;
      this.text = text;
      this.span = span;
    }
  }
  @Override
  public CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    if (source instanceof Spanned) {
      Spanned spanned = (Spanned) source;
      ImageSpan[] imageSpans = spanned.getSpans(start, end, ImageSpan.class);
      if (imageSpans != null) {
        List<Replacement> replacements = null;
        for (ImageSpan span : imageSpans) {
          final int imageStart = spanned.getSpanStart(span);
          final int imageEnd = spanned.getSpanEnd(span);
          final String imageSource = span.getSource();
          String emojiCode = parseEmojiCode(imageSource);
          EmojiSpan newSpan = Emoji.instance().newSpan(emojiCode, null);
          if (newSpan != null) {
            if (replacements == null) {
              replacements = new ArrayList<>();
            }
            replacements.add(new Replacement(span, imageStart, imageEnd, emojiCode, newSpan));
          }
        }
        if (replacements != null) {
          SpannableStringBuilder b = new SpannableStringBuilder(source, start, end);
          int alteredIndex = -1;
          for (int i = replacements.size() - 1; i >= 0; i--) {
            Replacement replacement = replacements.get(i);
            if (alteredIndex != -1 && replacement.end > alteredIndex)
              throw new IllegalStateException("Intersecting ImageSpan");
            b.removeSpan(replacement.originalSpan);
            b.replace(replacement.start, replacement.end, replacement.text);
            b.setSpan(replacement.span, replacement.start, replacement.text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            alteredIndex = replacement.start;
          }
          return b;
        }
      }
    }
    return null;
  }

  private static String parseEmojiCode (String source) {
    if (StringUtils.isEmpty(source))
      return null;

    int i;

    // https://abs.twimg.com/emoji/v2/72x72/1f600.png
    i = source.indexOf("twimg.com/emoji/v2/");
    if (i != -1) {
      i = source.lastIndexOf('/');
      int j = source.lastIndexOf('.');
      if (j <= i) {
        j = -1;
      }
      if (j != -1) {
        source = source.substring(i + 1, j);
      } else {
        source = source.substring(i + 1);
      }
      source = fillZero(source, 8);
      return Emoji.parseCode(source, "UTF-32BE");
    }

    // https://m.vk.com/images/emoji/D83DDE0C_2x.png
    i = source.indexOf("vk.com/images/emoji/");
    if (i != -1) {
      i += "vk.com/images/emoji/".length();
      int sourceEnd = source.length();
      if (source.endsWith("_2x.png")) {
        sourceEnd -= "_2x.png".length();
      } else if (source.endsWith(".png")) {
        sourceEnd -= ".png".length();
      } else {
        sourceEnd = -1;
      }
      if (i < sourceEnd) {
        source = fillZero(source.substring(i, sourceEnd), 8);
        return Emoji.parseCode(source, "UTF-16");
      }
    }
    // https://static.xx.fbcdn.net/images/emoji.php/v9/ffb/1/24/1f61a.png
    // do nothing

    return null;
  }

  private static String fillZero (String source, int count) {
    int remaining = count - source.length() % count;
    if (remaining != 0) {
      StringBuilder b = new StringBuilder(source.length() + remaining);
      for (int j = 0; j < remaining; j++) {
        b.append('0');
      }
      b.append(source);
      return b.toString();
    }
    return source;
  }
}
