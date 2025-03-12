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
 * File created on 12/06/2024
 */

package org.thunderdog.challegram.helper.editable;

import android.text.Editable;
import android.text.Spanned;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.util.text.quotes.QuoteSpan;

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
}
