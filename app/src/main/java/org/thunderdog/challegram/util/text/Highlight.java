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
 * File created on 11/09/2022, 16:31.
 */

package org.thunderdog.challegram.util.text;

import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.util.Transliterator;

public class Highlight {
  public static class Part {
    public final int start, end;
    public final int missingCount;

    public Part (int start, int end, int missingCount) {
      this.start = start;
      this.end = end;
      this.missingCount = missingCount;
    }

    public int length () {
      return end - start;
    }

    public float score () {
      int len = length();
      int originalLength = len + missingCount;
      return (float) len / (float) originalLength;
    }

    public boolean isExactMatch () {
      return missingCount == 0;
    }

    @Override
    public boolean equals (Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Part part = (Part) o;
      return start == part.start && end == part.end && missingCount == part.missingCount;
    }

    @Override
    public int hashCode () {
      int result = start;
      result = 31 * result + end;
      result = 31 * result + missingCount;
      return result;
    }
  }

  public final List<Part> parts = new ArrayList<>();
  private TextColorSet customColorSet = null;

  public Highlight (String text, String highlight) {
    this(text, 0, text.length(), highlight, 0, highlight.length());
  }

  public TextColorSet getColorSet () {
    return customColorSet != null ? customColorSet : TextColorSets.Regular.SEARCH_HIGHLIGHT;
  }

  public void setCustomColorSet (TextColorSet customColorSet) {
    this.customColorSet = customColorSet;
  }

  private static int indexOfWeakCodePoint (String in, int startIndex, int endIndex) {
    if (startIndex >= endIndex)
      return -1;
    for (int i = startIndex; i < endIndex; ) {
      int codePoint = in.codePointAt(i);
      if (isWeakCodePoint(codePoint))
        return i;
      i += Character.charCount(codePoint);
    }
    return -1;
  }

  private static boolean isWeakCodePoint (int codePoint) {
    int codePointType = Character.getType(codePoint);
    if (Text.isSplitterCodePointType(codePoint, codePointType, true)) {
      return true;
    }
    switch (codePointType) {
      case Character.START_PUNCTUATION:
      case Character.END_PUNCTUATION:
      case Character.CONNECTOR_PUNCTUATION:
      case Character.DASH_PUNCTUATION:
      case Character.FINAL_QUOTE_PUNCTUATION:
      case Character.INITIAL_QUOTE_PUNCTUATION:
      case Character.OTHER_PUNCTUATION:
      case Character.SPACE_SEPARATOR:
      case Character.LINE_SEPARATOR:
      case Character.PARAGRAPH_SEPARATOR:
        return true;
    }
    return false;
  }

  public Highlight (String text, int start, int end, String highlight, int highlightStart, int highlightEnd) {
    int highlightLength = highlightEnd - highlightStart;

    int maxMatchingLength = 0;
    int index = start;
    while (index < end) {
      int next = indexOfWeakCodePoint(text, index, end);
      if (next == index) {
        next = index + 1;
      } else if (next == -1) {
        next = end;
      }

      int matchingLength = 0;
      int highlightIndex = 0;
      while (highlightIndex < highlightLength && matchingLength < (end - index)) {
        int highlightCodePoint = highlight.codePointAt(highlightStart + highlightIndex);
        int highlightCodePointType = Character.getType(highlightCodePoint);
        boolean highlightCodePointIsSeparator =
          highlightCodePointType == Character.SPACE_SEPARATOR ||
          highlightCodePointType == Character.LINE_SEPARATOR;
        int contentCodePoint = text.codePointAt(index + matchingLength);
        int contentCodePointType = Character.getType(contentCodePoint);
        boolean contentCodePointIsSeparator =
          contentCodePointType == Character.SPACE_SEPARATOR ||
          contentCodePointType == Character.LINE_SEPARATOR;
        if (highlightCodePoint == contentCodePoint || (highlightCodePointIsSeparator && contentCodePointIsSeparator) || StringUtils.normalizeCodePoint(highlightCodePoint) == StringUtils.normalizeCodePoint(contentCodePoint)) {
          // easy path: code points are equal or similar
          matchingLength += Character.charCount(contentCodePoint);
          highlightIndex += Character.charCount(highlightCodePoint);
        } else {
          // harder path: look if text <-> highlight can be transliterated one way or another
          Transliterator.PrefixResult prefixResult = Transliterator.findPrefix(
            text, index + matchingLength, end,
            highlight, highlightStart + highlightIndex, highlightEnd
          );
          if (prefixResult != null) {
            matchingLength += prefixResult.contentLength;
            highlightIndex += prefixResult.prefixLength;
          } else {
            break;
          }
        }
      }
      if (matchingLength > 0) {
        parts.add(new Part(index, index + matchingLength, highlightLength - highlightIndex));
        next = Math.max(next, index + matchingLength);
        maxMatchingLength = Math.max(maxMatchingLength, matchingLength);
      }

      index = next;
    }

    if (parts.isEmpty()) {
      int foundIndex = text.indexOf(highlight);
      if (foundIndex != -1) {
        maxMatchingLength = highlight.length();
        parts.add(new Part(foundIndex, foundIndex + highlight.length(), 0));
      }
    }

    for (int i = parts.size() - 1; i >= 0; i--) {
      Part part = parts.get(i);
      if (part.length() < maxMatchingLength && !part.isExactMatch()) {
        parts.remove(i);
      }
    }
  }

  public boolean isEmpty () {
    return parts.isEmpty();
  }

  @Nullable
  public static Highlight valueOf (String text, String highlight) {
    return valueOf(text, highlight, null);
  }

  @Nullable
  public static Highlight valueOf (String text, String highlight, TextColorSet customColorSet) {
    if (StringUtils.isEmpty(text) || StringUtils.isEmpty(highlight)) {
      return null;
    }
    Highlight result = new Highlight(text, highlight);
    if (result.isEmpty()) {
      int start = 0;
      int end = highlight.length();
      while (start < end) {
        int codePoint = highlight.codePointAt(start);
        if (isWeakCodePoint(codePoint)) {
          start += Character.charCount(codePoint);
        } else {
          break;
        }
      }
      while (end - 1 > start) {
        int codePoint = highlight.codePointAt(end - 1);
        if (isWeakCodePoint(codePoint)) {
          end -= Character.charCount(codePoint);
        } else {
          break;
        }
      }
      if ((end - start) > 0 && (start > 0 || end < highlight.length())) {
        result = new Highlight(text, 0, text.length(), highlight, start, end);
      }
    }
    if (result.isEmpty()) {
      Highlight latinLookup = null, cyrillicLookup = null;
      if (Transliterator.hasCyrillicLetters(highlight) && Transliterator.hasLatinLetters(text)) {
        // Convert highlight to latin & repeat the search
        String latin = Transliterator.cyrillicToLatin(highlight);
        latinLookup = new Highlight(text, 0, text.length(), latin, 0, latin.length());
      }
      if (Transliterator.hasLatinLetters(highlight) && Transliterator.hasCyrillicLetters(text)) {
        // Convert highlight to cyrillic & repeat the search
        String cyrillic = Transliterator.latinToCyrillic(highlight);
        cyrillicLookup = new Highlight(text, 0, text.length(), cyrillic, 0, cyrillic.length());
      }
      if ((latinLookup != null && !latinLookup.isEmpty()) &&
          (cyrillicLookup != null && !cyrillicLookup.isEmpty())) {
        boolean prioritizeLatin = latinLookup.getMaxSize() >= cyrillicLookup.getMaxSize();
        Highlight priorityHighlight = prioritizeLatin ? latinLookup : cyrillicLookup;
        priorityHighlight.addNonIntersectingParts(prioritizeLatin ? cyrillicLookup : latinLookup);
        result = priorityHighlight;
      } else if (latinLookup != null && !latinLookup.isEmpty()) {
        result = latinLookup;
      } else if (cyrillicLookup != null && !cyrillicLookup.isEmpty()) {
        result = cyrillicLookup;
      }
    }
    if (result.isEmpty()) {
      // try to highlight strong parts separately
      int start = 0;
      for (int i = start; i < highlight.length(); ) {
        int end = start;
        do {
          int codePoint = highlight.codePointAt(i);
          int codePointSize = Character.charCount(codePoint);
          i += codePointSize;
          if (isWeakCodePoint(codePoint)) {
            break;
          } else {
            end += codePointSize;
          }
        } while (i < highlight.length());
        if (end - start > 0) {
          result.addNonIntersectingParts(new Highlight(text, 0, text.length(), highlight, start, end));
        }
      }
    }
    if (result.isEmpty()) {
      return null;
    }
    result.setCustomColorSet(customColorSet);
    return result;
  }

  public static boolean isExactMatch (@Nullable Highlight highlight) {
    if (highlight == null) {
      return false;
    }

    for (Part part : highlight.parts) {
      if (part.isExactMatch()) {
        return true;
      }
    }

    return false;
  }

  public void addNonIntersectingParts (Highlight other) {
    for (Part part : other.parts) {
      addNonIntersectingPart(part);
    }
  }

  public void addNonIntersectingPart (Part other) {
    int bestIndex = 0;
    for (Part part : parts) {
      if ((other.start >= part.start && other.start < part.end) ||
          (other.end > part.start && other.end < part.end)) {
        // Other entity intersects with an existing entity
        return;
      }
      if (part.start >= other.end) {
        // Place other part before existing part
        break;
      }
      bestIndex++;
    }
    if (bestIndex == parts.size()) {
      parts.add(other);
    } else {
      parts.add(bestIndex, other);
    }
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Highlight highlight = (Highlight) o;
    return parts.equals(highlight.parts);
  }

  public int getMaxSize () {
    int size = 0;
    for (Part part : parts) {
      size = Math.max(size, part.length());
    }
    return size;
  }

  @Override
  public int hashCode () {
    return parts.hashCode();
  }

  public static boolean equals (Highlight a, Highlight b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  public static class Pool {
    public static final int KEY_NONE = -1;
    public static final int KEY_TEXT = 1;
    public static final int KEY_MEDIA_CAPTION = 2;
    public static final int KEY_SITE_NAME = 3;
    public static final int KEY_SITE_TITLE = 4;
    public static final int KEY_SITE_TEXT = 5;
    public static final int KEY_FILE_TITLE = 6;
    public static final int KEY_FILE_SUBTITLE = 7;
    public static final int KEY_FILE_CAPTION = 8;

    private final SparseArray<Highlight> highlights;
    private Highlight mostRelevantHighlight;
    private int mostRelevantHighlightKey;

    public Pool () {
      highlights = new SparseArray<>();
    }

    public void add (int key, Highlight highlight) {
      if (mostRelevantHighlight == null || mostRelevantHighlight.getMaxSize() < highlight.getMaxSize()) {
        mostRelevantHighlightKey = key;
        mostRelevantHighlight = highlight;
      }
      highlights.append(key, highlight);
    }

    public Highlight get (int key) {
      return highlights.get(key);
    }

    public int getMostRelevantHighlightKey () {
      return mostRelevantHighlightKey;
    }

    public boolean isMostRelevant (int key) {
      return mostRelevantHighlightKey == key;
    }

    public void clear () {
      mostRelevantHighlight = null;
      mostRelevantHighlightKey = KEY_NONE;
      highlights.clear();
    }
  }
}
