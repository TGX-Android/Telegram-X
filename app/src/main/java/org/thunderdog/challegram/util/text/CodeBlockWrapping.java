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
 * File created on 26/10/2026
 */
package org.thunderdog.challegram.util.text;

import org.thunderdog.challegram.config.Config;

/**
 * Code blocks wrap their lines, except long ones, which are easier to read by scrolling
 */
public final class CodeBlockWrapping {
  // About as many lines of code as a message on the screen of a phone shows
  public static final int MAX_WRAPPED_LINE_COUNT = 24;
  private static final int MAX_PRESERVED_INDENT_PERCENT = 60;

  private CodeBlockWrapping () { }

  public static boolean isLong (String text, int start, int end, float charWidth, int width) {
    return isLong(text, start, end, charWidth, width, 0, 0, 0);
  }

  public static boolean isLong (
    String text,
    int start,
    int end,
    float charWidth,
    int width,
    int lineHeight,
    int extraHeight,
    int listHeight
  ) {
    int lineCount = 0, wrappedLineCount = 0;
    boolean hasWideLine = false;
    // Line break at the end of code doesn't start a line
    for (int lineStart = start; lineStart < end; ) {
      final int lineEnd = findLineEnd(text, lineStart, end);
      final float lineWidth = (lineEnd - lineStart) * charWidth;
      hasWideLine |= lineWidth > width;
      wrappedLineCount += Math.max(1, (int) Math.ceil(lineWidth / width));
      lineCount++;
      lineStart = lineEnd + 1;
    }
    if (lineCount <= 1 || !hasWideLine) {
      return false;
    }
    if (listHeight > 0) {
      final long wrappedHeight = (long) wrappedLineCount * lineHeight + extraHeight;
      return wrappedHeight * 100 > (long) listHeight * Config.CODE_BLOCK_MAX_WRAPPED_HEIGHT_PERCENT;
    }
    return wrappedLineCount > MAX_WRAPPED_LINE_COUNT;
  }

  private static int findLineEnd (String text, int lineStart, int end) {
    // Not by indexOf, which would read past the block
    int lineEnd = lineStart;
    while (lineEnd < end && text.charAt(lineEnd) != '\n') {
      lineEnd++;
    }
    return lineEnd;
  }

  public static boolean hasWideLine (String text, int start, int end, float charWidth, int width) {
    for (int lineStart = start; lineStart < end; ) {
      final int lineEnd = findLineEnd(text, lineStart, end);
      if ((lineEnd - lineStart) * charWidth > width) {
        return true;
      }
      lineStart = lineEnd + 1;
    }
    return false;
  }

  public static float getContinuationX (String text, int lineStart, float charWidth, int width) {
    // One character after the indent, so that continuations don't merge with indents of lines
    int spaceCount = 0;
    while (lineStart + spaceCount < text.length() && text.charAt(lineStart + spaceCount) == ' ') {
      spaceCount++;
    }
    final float indent = spaceCount * charWidth;
    // 0.6 isn't exact in floats, so the indent is compared in whole percents
    return indent * 100 < width * MAX_PRESERVED_INDENT_PERCENT ? indent + charWidth : 0;
  }
}
