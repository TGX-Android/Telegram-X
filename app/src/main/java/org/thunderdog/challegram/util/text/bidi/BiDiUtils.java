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
 * File created on 25/04/2024 (PR #563)
 */
package org.thunderdog.challegram.util.text.bidi;

import android.graphics.Paint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.U;

import me.vkryl.core.BitwiseUtils;

public class BiDiUtils {
  private static final int IS_RTL_FLAG = 1;
  private static final int IS_PARAGRAPH_RTL_FLAG = 1 << 8;
  private static final int IS_VALID_FLAG = 1 << 9;

  private static final int INDEX_POSITION = 12;
  private static final int INDEX_MASK = 0xFFFFF;

  public static @BiDiEntity int create (int level, int paragraphLevel, int index) {
    return (level & 0xFF) | ((paragraphLevel & 1) << 8) | IS_VALID_FLAG | ((index & INDEX_MASK) << INDEX_POSITION);
  }

  public static boolean isValid (@BiDiEntity int flags) {
    return BitwiseUtils.hasFlag(flags, IS_VALID_FLAG);
  }

  public static int getIndex (@BiDiEntity int flags) {
    return (flags >> INDEX_POSITION) & INDEX_MASK;
  }

  public static int getLevel (@BiDiEntity int flags) {
    return (flags & 0xFF);
  }
  
  public static boolean isRtl (@BiDiEntity int flags) {
    return BitwiseUtils.hasFlag(flags, IS_RTL_FLAG);
  }
  
  public static boolean isParagraphRtl (@BiDiEntity int flags) {
    return BitwiseUtils.hasFlag(flags, IS_PARAGRAPH_RTL_FLAG);
  }

  public static float measureTextRun (@BiDiEntity int flags, @Nullable CharSequence in, @NonNull Paint p) {
    return isValid(flags) ? U.measureTextRun(in, p, isRtl(flags)) : U.measureText(in, p);
  }

  public static float measureTextRun (@BiDiEntity int flags, @Nullable CharSequence in, int start, int end, @NonNull Paint p) {
    return isValid(flags) ? U.measureTextRun(in, start, end, p, isRtl(flags)) : U.measureText(in, start, end, p);
  }

  public static boolean requiresBidi (CharSequence text, int start, int end) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      // Source: android.icu.text.Bidi.requiresBidi
      // but uses CharSequence instead of char[],
      // and uses code point instead of char.

      final int RTLMask = (1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_ARABIC_NUMBER);

      for (int i = start; i < end; ) {
        int codePoint = Character.codePointAt(text, i);
        if (((1 << android.icu.lang.UCharacter.getDirection(codePoint)) & RTLMask) != 0) {
          return true;
        }
        i += Character.charCount(codePoint);
      }
      return false;
    } else {    // todo: test difference between UCharacter and Character
      final int RTLMask = (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT |
        1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC |
        1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING |
        1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE |
        1 << Character.DIRECTIONALITY_ARABIC_NUMBER);

      for (int i = start; i < end; ) {
        int codePoint = Character.codePointAt(text, i);
        if (((1 << Character.getDirectionality(codePoint)) & RTLMask) != 0) {
          return true;
        }
        i += Character.charCount(codePoint);
      }
      return false;
    }
  }

  public static boolean requiresBidi (char[] text, int start, int end) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      // Source: android.icu.text.Bidi.requiresBidi

      final int RTLMask = (1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE |
        1 << android.icu.lang.UCharacter.DIRECTIONALITY_ARABIC_NUMBER);

      for (int i = start; i < end; ) {
        int codePoint = Character.codePointAt(text, i, end);
        if (((1 << android.icu.lang.UCharacter.getDirection(codePoint)) & RTLMask) != 0) {
          return true;
        }
        i += Character.charCount(codePoint);
      }
      return false;
    } else {
      final int RTLMask = (1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT |
        1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC |
        1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING |
        1 << Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE |
        1 << Character.DIRECTIONALITY_ARABIC_NUMBER);

      for (int i = start; i < end; ++i) {
        int codePoint = Character.codePointAt(text, i, end);
        if (((1 << Character.getDirectionality(codePoint)) & RTLMask) != 0) {
          return true;
        }
        i += Character.charCount(codePoint);
      }
      return false;
    }
  }
}
