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
 * File created on 27/10/2026
 */
package org.thunderdog.challegram.util.text;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Words of a new text matched with an old one by their text: in their order, then the rest
 * wherever they moved
 */
public final class TextMatching {
  // Words are matched in order by their longest common subsequence, greedily for a bigger table
  private static final int MAX_TABLE_SIZE = 250_000;

  // Ordered by their start in the new text
  private final int[] oldStarts, newStarts, lengths;

  private TextMatching (int[] oldStarts, int[] newStarts, int[] lengths) {
    this.oldStarts = oldStarts;
    this.newStarts = newStarts;
    this.lengths = lengths;
  }

  public int getRangeCount () {
    return lengths.length;
  }

  public int getOldStart (int index) {
    return oldStarts[index];
  }

  public int getNewStart (int index) {
    return newStarts[index];
  }

  public int getLength (int index) {
    return lengths[index];
  }

  public int findRange (int newOffset) {
    int index = Arrays.binarySearch(newStarts, newOffset);
    if (index < 0) {
      index = -index - 2;
    }
    return index >= 0 && newOffset < newStarts[index] + lengths[index] ? index : -1;
  }

  public int findOldOffset (int newOffset) {
    final int range = findRange(newOffset);
    return range != -1 ? oldStarts[range] + newOffset - newStarts[range] : -1;
  }

  public static TextMatching match (String oldText, String newText) {
    if (oldText.equals(newText)) {
      final int[] starts = {0};
      return new TextMatching(starts, starts, new int[] {newText.length()});
    }
    return new Matcher(oldText, newText).match();
  }

  private static final class Matcher {
    private final String oldText, newText;
    // Starts and ends of words, one after another
    private final int[] oldWords, newWords;
    private final int[] matches;
    private final boolean[] isTaken;

    Matcher (String oldText, String newText) {
      this.oldText = oldText;
      this.newText = newText;
      this.oldWords = findWords(oldText);
      this.newWords = findWords(newText);
      this.matches = new int[newWords.length / 2];
      Arrays.fill(matches, -1);
      this.isTaken = new boolean[oldWords.length / 2];
    }

    TextMatching match () {
      final int oldCount = isTaken.length, newCount = matches.length;
      int prefix = 0;
      while (prefix < oldCount && prefix < newCount && sameWords(prefix, prefix)) {
        take(prefix, prefix);
        prefix++;
      }
      int suffix = 0;
      while (suffix < oldCount - prefix && suffix < newCount - prefix &&
        sameWords(oldCount - 1 - suffix, newCount - 1 - suffix)) {
        take(oldCount - 1 - suffix, newCount - 1 - suffix);
        suffix++;
      }
      matchInOrder(prefix, oldCount - suffix, prefix, newCount - suffix);
      matchMoved();

      int rangeCount = 0;
      for (int match : matches) {
        if (match != -1) {
          rangeCount++;
        }
      }
      final int[] oldStarts = new int[rangeCount], newStarts = new int[rangeCount];
      final int[] lengths = new int[rangeCount];
      for (int i = 0, range = 0; i < newCount; i++) {
        if (matches[i] != -1) {
          oldStarts[range] = oldWords[matches[i] * 2];
          newStarts[range] = newWords[i * 2];
          lengths[range] = newWords[i * 2 + 1] - newWords[i * 2];
          range++;
        }
      }
      return new TextMatching(oldStarts, newStarts, lengths);
    }

    private static int[] findWords (String text) {
      int count = 0;
      for (int i = 0; i < text.length(); i++) {
        final boolean startsWord = i == 0 || Character.isWhitespace(text.charAt(i - 1));
        if (!Character.isWhitespace(text.charAt(i)) && startsWord) {
          count++;
        }
      }
      final int[] words = new int[count * 2];
      int index = 0;
      for (int i = 0; i < text.length(); ) {
        if (Character.isWhitespace(text.charAt(i))) {
          i++;
          continue;
        }
        words[index++] = i;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
          i++;
        }
        words[index++] = i;
      }
      return words;
    }

    private boolean sameWords (int oldIndex, int newIndex) {
      final int oldStart = oldWords[oldIndex * 2], length = oldWords[oldIndex * 2 + 1] - oldStart;
      final int newStart = newWords[newIndex * 2];
      return newWords[newIndex * 2 + 1] - newStart == length &&
        oldText.regionMatches(oldStart, newText, newStart, length);
    }

    private void take (int oldIndex, int newIndex) {
      matches[newIndex] = oldIndex;
      isTaken[oldIndex] = true;
    }

    private void matchInOrder (int oldStart, int oldEnd, int newStart, int newEnd) {
      final int oldCount = oldEnd - oldStart, newCount = newEnd - newStart;
      if (oldCount == 0 || newCount == 0) {
        return;
      }
      if ((long) (oldCount + 1) * (newCount + 1) > MAX_TABLE_SIZE) {
        int oldIndex = oldStart;
        for (int newIndex = newStart; newIndex < newEnd && oldIndex < oldEnd; newIndex++) {
          for (int i = oldIndex; i < oldEnd; i++) {
            if (sameWords(i, newIndex)) {
              take(i, newIndex);
              oldIndex = i + 1;
              break;
            }
          }
        }
        return;
      }
      // Lengths of common subsequences of the ends of both ranges
      final int rowLength = newCount + 1;
      final int[] table = new int[(oldCount + 1) * rowLength];
      for (int i = oldCount - 1; i >= 0; i--) {
        for (int j = newCount - 1; j >= 0; j--) {
          final int cell = i * rowLength + j;
          if (sameWords(oldStart + i, newStart + j)) {
            table[cell] = table[cell + rowLength + 1] + 1;
          } else {
            table[cell] = Math.max(table[cell + rowLength], table[cell + 1]);
          }
        }
      }
      for (int i = 0, j = 0; i < oldCount && j < newCount; ) {
        if (sameWords(oldStart + i, newStart + j)) {
          take(oldStart + i, newStart + j);
          i++;
          j++;
        } else if (table[(i + 1) * rowLength + j] >= table[i * rowLength + j + 1]) {
          i++;
        } else {
          j++;
        }
      }
    }

    private void matchMoved () {
      HashMap<String, ArrayDeque<Integer>> oldWordsByText = null;
      for (int newIndex = 0; newIndex < matches.length; newIndex++) {
        if (matches[newIndex] != -1) {
          continue;
        }
        if (oldWordsByText == null) {
          oldWordsByText = new HashMap<>();
          for (int i = 0; i < isTaken.length; i++) {
            if (!isTaken[i]) {
              final String word = oldText.substring(oldWords[i * 2], oldWords[i * 2 + 1]);
              ArrayDeque<Integer> indexes = oldWordsByText.get(word);
              if (indexes == null) {
                indexes = new ArrayDeque<>();
                oldWordsByText.put(word, indexes);
              }
              indexes.add(i);
            }
          }
        }
        final String word = newText.substring(newWords[newIndex * 2], newWords[newIndex * 2 + 1]);
        final ArrayDeque<Integer> indexes = oldWordsByText.get(word);
        if (indexes != null && !indexes.isEmpty()) {
          take(indexes.poll(), newIndex);
        }
      }
    }
  }
}
