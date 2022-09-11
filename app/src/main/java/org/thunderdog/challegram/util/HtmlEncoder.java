/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 11/09/2022, 01:23.
 */

package org.thunderdog.challegram.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.inputmethod.BaseInputConnection;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

public class HtmlEncoder {
  public interface SpanHandler {
    HtmlTag[] toHtmlTag (Object span);
  }

  public static class EncodeResult {
    public final String htmlText;
    public final int tagCount;

    public EncodeResult (String htmlText, int tagCount) {
      this.htmlText = htmlText;
      this.tagCount = tagCount;
    }
  }

  public static EncodeResult toHtml (CharSequence charSequence, SpanHandler spanHandler) {
    return toHtml(charSequence, 0, charSequence.length(), spanHandler);
  }

  public static EncodeResult toHtml (CharSequence charSequence, int start, int end, SpanHandler spanHandler) {
    if (!(charSequence instanceof Spanned)) {
      String text = (start != 0 || end != charSequence.length()) ?
        charSequence.subSequence(start, end).toString() :
        charSequence.toString();
      return new EncodeResult(text, 0);
    }
    SpannableStringBuilder text = new SpannableStringBuilder(charSequence);
    BaseInputConnection.removeComposingSpans(text);

    int tagCount = 0;
    StringBuilder out = new StringBuilder();
    int next;
    for (int i = start; i < end; i = next) {
      next = text.nextSpanTransition(i, end, Object.class);
      Object[] spans = text.getSpans(i, next, Object.class);
      if (spans == null || spans.length == 0) {
        withinStyle(out, text, i, next);
        continue;
      }
      List<HtmlTag> tagsToClose = new ArrayList<>();
      for (Object span : spans) {
        HtmlTag[] tags = spanHandler.toHtmlTag(span);
        if (tags != null) {
          for (HtmlTag tag : tags) {
            tagCount++;
            out.append(tag.openTag);
            if (!StringUtils.isEmpty(tag.closeTag)) {
              tagsToClose.add(tag);
            }
          }
        }
      }
      withinStyle(out, text, i, next);
      for (int tagIndex = tagsToClose.size() - 1; tagIndex >= 0; tagIndex--) {
        out.append(tagsToClose.get(tagIndex).closeTag);
      }
    }
    return new EncodeResult(out.toString(), tagCount);
  }

  // Copy-paste from:
  // https://android.googlesource.com/platform/frameworks/base/+/f63f20af/core/java/android/text/Html.java#636
  private static void withinStyle (StringBuilder out, CharSequence text,
                                   int start, int end) {
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);

      if (c == '<') {
        out.append("&lt;");
      } else if (c == '>') {
        out.append("&gt;");
      } else if (c == '&') {
        out.append("&amp;");
      } else if (c >= 0xD800 && c <= 0xDFFF) {
        if (c < 0xDC00 && i + 1 < end) {
          char d = text.charAt(i + 1);
          if (d >= 0xDC00 && d <= 0xDFFF) {
            i++;
            int codepoint = 0x010000 | (int) c - 0xD800 << 10 | (int) d - 0xDC00;
            out.append("&#").append(codepoint).append(";");
          }
        }
      } else if (c > 0x7E || c < ' ') {
        out.append("&#").append((int) c).append(";");
      } else if (c == ' ') {
        while (i + 1 < end && text.charAt(i + 1) == ' ') {
          out.append("&nbsp;");
          i++;
        }

        out.append(' ');
      } else {
        out.append(c);
      }
    }
  }
}
