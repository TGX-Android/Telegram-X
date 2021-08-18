package me.vkryl.android.text;

import android.text.Spannable;
import android.text.Spanned;

final class TextUtils {
  private TextUtils () { }

  public static void copySpans (Spanned source, int start, int end,
                                Class<?> kind,
                                Spannable dest, int destoff) {
    if (kind == null) {
      kind = Object.class;
    }

    Object[] spans = source.getSpans(start, end, kind);

    if (spans == null || spans.length == 0)
      return;

    for (Object span : spans) {
      int st = source.getSpanStart(span);
      int en = source.getSpanEnd(span);
      int fl = source.getSpanFlags(span);

      if (st < start)
        st = start;
      if (en > end)
        en = end;
      try {
        dest.setSpan(span, st - start + destoff, en - start + destoff,
          fl);
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }
  }
}
