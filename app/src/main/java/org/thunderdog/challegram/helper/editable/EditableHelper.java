package org.thunderdog.challegram.helper.editable;

import android.text.Editable;
import android.text.Spanned;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.util.text.quotes.QuoteSpan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class EditableHelper {
  public static void removeSpan (Editable editable, Object span) {
    QuoteSpan.removeSpan(editable, span);
  }

  public static boolean startsWithQuote (@Nullable Spanned spanned) {
    if (spanned == null) {
      return false;
    }

    QuoteSpan[] spans = spanned.getSpans(0, 0, QuoteSpan.class);
    return spans != null && spans.length > 0;
  }

/*
  @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Flags {}

  public static class Utils {
    public static @Flags int make (int insertedBefore, int insertedAfter) {
      return ((insertedBefore & 0xFF) << 24) | ((insertedAfter & 0xFF) << 16);
    }

    public static int getInsertedBefore (@Flags int flags) {
      return (flags >> 24) & 0xFF;
    }

    public static int getInsertedAfter (@Flags int flags) {
      return (flags >> 16) & 0xFF;
    }

    public static int getInsertedTotal (@Flags int flags) {
      return getInsertedAfter(flags) + getInsertedBefore(flags);
    }
  }
*/
}
