package org.thunderdog.challegram.emoji;

import android.os.Build;
import android.text.Editable;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

/**
 * Date: 2019-05-11
 * Author: default
 */
public class EmojiInputConnection extends InputConnectionWrapper {
  private final TextView mTextView;

  public EmojiInputConnection (
    @NonNull final TextView textView,
    @NonNull final InputConnection inputConnection) {
    super(inputConnection, false);
    mTextView = textView;
    // EmojiCompat.get().updateEditorInfoAttrs(outAttrs);
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
