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
 * File created on 13/11/2016
 */
package org.thunderdog.challegram.tool;

import android.text.Spanned;

import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiSpan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.StringUtils;

public class EmojiData {
  private static EmojiData instance;

  public static EmojiData instance () {
    if (instance == null) {
      instance = new EmojiData();
    }
    return instance;
  }

  public static final String[] emojiColors = {
    null,
    EmojiCodeColored.COLOR_1,
    EmojiCodeColored.COLOR_2,
    EmojiCodeColored.COLOR_3,
    EmojiCodeColored.COLOR_4,
    EmojiCodeColored.COLOR_5
  };

  public static final char[] dataChars = {
    0x262E, 0x271D, 0x262A, 0x2638, 0x2721, 0x262F, 0x2626, 0x26CE, 0x2648, 0x2649,
    0x264A, 0x264B, 0x264C, 0x264D, 0x264E, 0x264F, 0x2650, 0x2651, 0x2652, 0x2653,
    0x269B, 0x2622, 0x2623, 0x2734, 0x3299, 0x3297, 0x26D4, 0x274C, 0x2B55, 0x2668,
    0x2757, 0x2755, 0x2753, 0x2754, 0x203C, 0x2049, 0x269C, 0x303D, 0x26A0, 0x267B,
    0x2747, 0x2733, 0x274E, 0x2705, 0x27BF, 0x24C2, 0x267F, 0x25B6, 0x23F8, 0x23EF,
    0x23F9, 0x23FA, 0x23ED, 0x23EE, 0x23E9, 0x23EA, 0x25C0, 0x23EB, 0x23EC, 0x27A1,
    0x2B05, 0x2B06, 0x2B07, 0x2197, 0x2198, 0x2199, 0x2196, 0x2195, 0x2194, 0x21AA,
    0x21A9, 0x2934, 0x2935, 0x2139, 0x3030, 0x27B0, 0x2714, 0x2795, 0x2796, 0x2797,
    0x2716, 0x00A9, 0x00AE, 0x2122, 0x2611, 0x26AA, 0x26AB, 0x25AA, 0x25AB, 0x2B1B,
    0x2B1C, 0x25FC, 0x25FB, 0x25FE, 0x25FD, 0x2660, 0x2663, 0x2665, 0x2666, 0x263A,
    0x2639, 0x270A, 0x270C, 0x270B, 0x261D, 0x270D, 0x26D1, 0x2764, 0x2763, 0x2615,
    0x26BD, 0x26BE, 0x26F3, 0x26F7, 0x26F8, 0x26F9, 0x231A, 0x2328, 0x260E, 0x23F1,
    0x23F2, 0x23F0, 0x23F3, 0x231B, 0x2696, 0x2692, 0x26CF, 0x2699, 0x26D3, 0x2694,
    0x2620, 0x26B0, 0x26B1, 0x2697, 0x26F1, 0x2709, 0x2702, 0x2712, 0x270F, 0x2708,
    0x26F5, 0x26F4, 0x2693, 0x26FD, 0x26F2, 0x26F0, 0x26FA, 0x26EA, 0x26E9, 0x2618,
    0x2B50, 0x2728, 0x2604, 0x2600, 0x26C5, 0x2601, 0x26C8, 0x26A1, 0x2744, 0x2603,
    0x26C4, 0x2602, 0x2614, 0x267E, 0x265F, 0x26A7
  };

  public static final String[][] data = EmojiCode.DATA;
  public static final String[][] dataColored = EmojiCodeColored.DATA_COLORED;

  public static int getTotalDataCount () {
    int count = 0;
    for (String[] data : EmojiData.data) {
      count += data.length;
    }
    return count;
  }

  private final HashSet<Character> dataCharsMap;
  private final Set<String> emojiColoredSet, emojiColored2dMap;
  private final HashMap<CharSequence, CharSequence> emojiAliasMap;

  private EmojiData () {
    this.dataCharsMap = new HashSet<>(dataChars.length);
    for (char dataChar : dataChars) {
      dataCharsMap.add(dataChar);
    }

    this.emojiColored2dMap = Emojis.colored2dSet();
    Set<String> coloredSet = Emojis.colored1dSet();
    this.emojiColoredSet = new HashSet<>(this.emojiColored2dMap.size() + coloredSet.size());
    emojiColoredSet.addAll(coloredSet);
    emojiColoredSet.addAll(emojiColored2dMap);

    if (EmojiCodeLegacy.ALIAS_NEW.length != EmojiCodeLegacy.ALIAS_OLD.length)
      throw new RuntimeException(EmojiCodeLegacy.ALIAS_NEW.length + " vs " + EmojiCodeLegacy.ALIAS_OLD.length);
    this.emojiAliasMap = new HashMap<>(EmojiCodeLegacy.ALIAS_NEW.length);
    for (String emoji : emojiColoredSet) {
      int length = emoji.length();
      if (length > 1) {
        char last = emoji.charAt(length - 1);
        if ((last == '\u2640' || last == '\u2642')) { // (last == '♀' || last == '♂')
          if (length > 2 && emoji.charAt(length - 2) == '\u200D') {
            length -= 2;
          } else {
            length--;
          }
          String rawEmoji = emoji.substring(0, length);
          if (!emojiAliasMap.containsKey(rawEmoji)) {
            emojiAliasMap.put(rawEmoji, emoji);
          }
        }
      }
    }
    for (int a = 0; a < EmojiCodeLegacy.ALIAS_NEW.length; a++) {
      emojiAliasMap.put(EmojiCodeLegacy.ALIAS_OLD[a], EmojiCodeLegacy.ALIAS_NEW[a]);
    }
  }

  public CharSequence getEmojiAlias (CharSequence emoji) {
    return emojiAliasMap.get(emoji);
  }

  public static boolean isEmojiString (CharSequence text) {
    if (StringUtils.isEmpty(text)) {
      return false;
    }
    if (text instanceof Spanned) {
      EmojiSpan[] spans = ((Spanned) text).getSpans(0, text.length(), EmojiSpan.class);
      if (spans != null && spans.length > 0) {
        int start = ((Spanned) text).getSpanStart(spans[0]);
        int end = ((Spanned) text).getSpanEnd(spans[0]);
        return spans.length == 1 && start == 0 && end == text.length();
      }
    }
    final int textLength = text.length();
    if (textLength > 20) // TODO auto-generated value
      return false;
    for (int i = 0; i < textLength; ) {
      int codePoint = Character.codePointAt(text, i);
      i += Character.charCount(codePoint);
      if (Character.isLetterOrDigit(codePoint))
        return false;
      switch (Character.getType(codePoint)) {
        case Character.SPACE_SEPARATOR:
        case Character.LINE_SEPARATOR:
          return false;
      }
    }
    AtomicBoolean a = new AtomicBoolean(false);
    Emoji.instance().replaceEmoji(text, 0, textLength, null, (input, code, info, position, length) -> {
      a.set(position == 0 && length == text.length());
      return true;
    });
    return a.get();
  }

  public static final int STATE_NO_COLORS = 0;
  public static final int STATE_HAS_ONE_COLOR = 1;
  public static final int STATE_HAS_TWO_COLORS = 2;

  public int getEmojiColorState (String emoji) {
    if (emojiColoredSet.contains(emoji)) {
      if (emojiColored2dMap.contains(emoji)) {
        return STATE_HAS_TWO_COLORS;
      } else {
        return STATE_HAS_ONE_COLOR;
      }
    }
    return STATE_NO_COLORS;
  }

  public boolean containsDataChar (Character c) {
    return dataCharsMap.contains(c);
  }

  public String colorize (String code, String color1, String[] otherColors) {
    if (StringUtils.isEmpty(color1)) {
      return code;
    }
    if (otherColors == null || otherColors.length == 0) {
      return colorize(code, color1);
    }
    String colored = colorize(code, color1, otherColors[0]);
    return colored != null ? colored : colorize(code, color1);
  }

  public String colorize (String code, String color1, String color2) {
    return Emojis.colorize(code, color1.charAt(1), color2.charAt(1));
  }

  public String colorize (String code, String color) {
    if (StringUtils.isEmpty(color)) {
      return code;
    }
    String colorized = colorize(code, color, color);
    if (colorized != null) {
      return colorized;
    }
    StringBuilder b = new StringBuilder(code.length() + color.length());
    int i = code.length();
    while (--i >= 0) {
      if (code.charAt(i) == '\u200D') {
        break;
      }
    }
    if (i != -1) {
      b.append(code, 0, i);
      b.append(color);
      b.append(code, i, code.length());
    } else {
      b.append(code);
      b.append(color);
    }
    return b.toString();
  }

  public static String getColor (String emoji) {
    for (String emojiTone : emojiColors) {
      if (emojiTone != null && emoji.endsWith(emojiTone)) {
        return emojiTone;
      }
    }
    return null;
  }
}
