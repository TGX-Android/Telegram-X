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
 * File created on 02/09/2022, 17:21.
 */

package org.thunderdog.challegram.util;

import android.text.InputFilter;
import android.text.Spanned;

/**
 * Filter that prevents new lines at the end of text based on {@link Callback#needRemoveFinalNewLine(FinalNewLineFilter)}
 * and invokes {@link Callback#onFinalNewLineBeingRemoved(FinalNewLineFilter)} when it does so.
 */
public class FinalNewLineFilter implements InputFilter {
  public interface Callback {
    boolean needRemoveFinalNewLine (FinalNewLineFilter filter);
    void onFinalNewLineBeingRemoved (FinalNewLineFilter filter);
  }

  private final Callback callback;

  public FinalNewLineFilter (Callback callback) {
    this.callback = callback;
  }

  private boolean isBlank (CharSequence cs, int start, int end) {
    if (end - start <= 0) {
      return true;
    }
    int i = start;
    while (i < end) {
      int codePoint = Character.codePointAt(cs, i);
      if (!Character.isWhitespace(codePoint)) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  @Override
  public CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    int length = end - start;
    if (length > 0 && dend == dest.length()) {
      int newLineCount = 0;
      for (int i = end - 1; i >= start; i--) {
        if (source.charAt(i) == '\n') {
          newLineCount++;
        } else {
          break;
        }
      }
      if (newLineCount > 0 && callback.needRemoveFinalNewLine(this)) {
        if (!isBlank(source, start, end - newLineCount) || !isBlank(dest, 0, start) || isBlank(dest, dend, dest.length())) {
          callback.onFinalNewLineBeingRemoved(this);
          return source.subSequence(start, end - newLineCount);
        }
      }
    }
    return null;
  }
}
