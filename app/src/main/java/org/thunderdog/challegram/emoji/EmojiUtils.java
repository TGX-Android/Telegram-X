package org.thunderdog.challegram.emoji;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.Editable;
import android.text.Selection;
import android.view.inputmethod.InputConnection;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

/**
 * Date: 2019-05-11
 * Author: default
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class EmojiUtils {
  static boolean handleDeleteSurroundingText(@NonNull final InputConnection inputConnection,
                                             @NonNull final Editable editable, @IntRange(from = 0) final int beforeLength,
                                             @IntRange(from = 0) final int afterLength, final boolean inCodePoints) {
    //noinspection ConstantConditions
    if (editable == null || inputConnection == null) {
      return false;
    }

    if (beforeLength < 0 || afterLength < 0) {
      return false;
    }

    final int selectionStart = Selection.getSelectionStart(editable);
    final int selectionEnd = Selection.getSelectionEnd(editable);

    if (hasInvalidSelection(selectionStart, selectionEnd)) {
      return false;
    }

    int start;
    int end;
    if (inCodePoints) {
      // go backwards in terms of codepoints
      start = CodepointIndexFinder.findIndexBackward(editable, selectionStart,
        Math.max(beforeLength, 0));
      end = CodepointIndexFinder.findIndexForward(editable, selectionEnd,
        Math.max(afterLength, 0));

      if (start == CodepointIndexFinder.INVALID_INDEX
        || end == CodepointIndexFinder.INVALID_INDEX) {
        return false;
      }
    } else {
      start = Math.max(selectionStart - beforeLength, 0);
      end = Math.min(selectionEnd + afterLength, editable.length());
    }

    final EmojiSpan[] spans = editable.getSpans(start, end, EmojiSpan.class);
    if (spans != null && spans.length > 0) {
      for (EmojiSpan span : spans) {
        int spanStart = editable.getSpanStart(span);
        int spanEnd = editable.getSpanEnd(span);
        start = Math.min(spanStart, start);
        end = Math.max(spanEnd, end);
      }

      start = Math.max(start, 0);
      end = Math.min(end, editable.length());

      inputConnection.beginBatchEdit();
      editable.delete(start, end);
      inputConnection.endBatchEdit();
      return true;
    }

    return false;
  }

  private static boolean hasInvalidSelection(final int start, final int end) {
    return start == -1 || end == -1 || start != end;
  }

  private static final class CodepointIndexFinder {
    private static final int INVALID_INDEX = -1;

    /**
     * Find start index of the character in {@code cs} that is {@code numCodePoints} behind
     * starting from {@code from}.
     *
     * @param cs CharSequence to work on
     * @param from the index to start going backwards
     * @param numCodePoints the number of codepoints
     *
     * @return start index of the character
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static int findIndexBackward(final CharSequence cs, final int from,
                                 final int numCodePoints) {
      int currentIndex = from;
      boolean waitingHighSurrogate = false;
      final int length = cs.length();
      if (currentIndex < 0 || length < currentIndex) {
        return INVALID_INDEX;  // The starting point is out of range.
      }
      if (numCodePoints < 0) {
        return INVALID_INDEX;  // Basically this should not happen.
      }
      int remainingCodePoints = numCodePoints;
      while (true) {
        if (remainingCodePoints == 0) {
          return currentIndex;  // Reached to the requested length in code points.
        }

        --currentIndex;
        if (currentIndex < 0) {
          if (waitingHighSurrogate) {
            return INVALID_INDEX;  // An invalid surrogate pair is found.
          }
          return 0;  // Reached to the beginning of the text w/o any invalid surrogate
          // pair.
        }
        final char c = cs.charAt(currentIndex);
        if (waitingHighSurrogate) {
          if (!Character.isHighSurrogate(c)) {
            return INVALID_INDEX;  // An invalid surrogate pair is found.
          }
          waitingHighSurrogate = false;
          --remainingCodePoints;
          continue;
        }
        if (!Character.isSurrogate(c)) {
          --remainingCodePoints;
          continue;
        }
        if (Character.isHighSurrogate(c)) {
          return INVALID_INDEX;  // A invalid surrogate pair is found.
        }
        waitingHighSurrogate = true;
      }
    }

    /**
     * Find start index of the character in {@code cs} that is {@code numCodePoints} ahead
     * starting from {@code from}.
     *
     * @param cs CharSequence to work on
     * @param from the index to start going forward
     * @param numCodePoints the number of codepoints
     *
     * @return start index of the character
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static int findIndexForward(final CharSequence cs, final int from,
                                final int numCodePoints) {
      int currentIndex = from;
      boolean waitingLowSurrogate = false;
      final int length = cs.length();
      if (currentIndex < 0 || length < currentIndex) {
        return INVALID_INDEX;  // The starting point is out of range.
      }
      if (numCodePoints < 0) {
        return INVALID_INDEX;  // Basically this should not happen.
      }
      int remainingCodePoints = numCodePoints;

      while (true) {
        if (remainingCodePoints == 0) {
          return currentIndex;  // Reached to the requested length in code points.
        }

        if (currentIndex >= length) {
          if (waitingLowSurrogate) {
            return INVALID_INDEX;  // An invalid surrogate pair is found.
          }
          return length;  // Reached to the end of the text w/o any invalid surrogate
          // pair.
        }
        final char c = cs.charAt(currentIndex);
        if (waitingLowSurrogate) {
          if (!Character.isLowSurrogate(c)) {
            return INVALID_INDEX;  // An invalid surrogate pair is found.
          }
          --remainingCodePoints;
          waitingLowSurrogate = false;
          ++currentIndex;
          continue;
        }
        if (!Character.isSurrogate(c)) {
          --remainingCodePoints;
          ++currentIndex;
          continue;
        }
        if (Character.isLowSurrogate(c)) {
          return INVALID_INDEX;  // A invalid surrogate pair is found.
        }
        waitingLowSurrogate = true;
        ++currentIndex;
      }
    }
  }
}
