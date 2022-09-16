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
 * File created on 11/05/2019
 */
package org.thunderdog.challegram.emoji;

import android.os.Build;
import android.text.Editable;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

public class EmojiInputConnection extends InputConnectionWrapper {
  private final TextView mTextView;

  public EmojiInputConnection (
    @NonNull final TextView textView,
    @NonNull final InputConnection inputConnection) {
    super(inputConnection, false);
    mTextView = textView;
  }

  @Override
  public boolean deleteSurroundingText(final int beforeLength, final int afterLength) {
    final boolean result = handleDeleteSurroundingText(this, getEditable(),
      beforeLength, afterLength, false /*inCodePoints*/);
    return result || super.deleteSurroundingText(beforeLength, afterLength);
  }

  @Override
  public boolean deleteSurroundingTextInCodePoints(final int beforeLength,
                                                   final int afterLength) {
    final boolean result = handleDeleteSurroundingText(this, getEditable(),
      beforeLength, afterLength, true /*inCodePoints*/);
    return result || super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
  }

  private Editable getEditable() {
    return mTextView.getEditableText();
  }

  private static boolean handleDeleteSurroundingText(
    @NonNull final InputConnection inputConnection, @NonNull final Editable editable,
    @IntRange(from = 0) final int beforeLength, @IntRange(from = 0) final int afterLength,
    final boolean inCodePoints) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return EmojiUtils.handleDeleteSurroundingText(inputConnection, editable,
        beforeLength, afterLength, inCodePoints);
    } else {
      return false;
    }
  }
}
