package org.thunderdog.challegram.theme;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Date: 19/01/2017
 * Author: default
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
  ThemeId.NONE,
  ThemeId.TEMPORARY,
  ThemeId.CUSTOM,

  ThemeId.BLUE,
  ThemeId.NIGHT_BLACK,
  ThemeId.BLACK_WHITE,
  ThemeId.WHITE_BLACK,
  ThemeId.RED,
  ThemeId.ORANGE,
  ThemeId.GREEN,
  ThemeId.PINK,
  ThemeId.CYAN,
  ThemeId.NIGHT_BLUE,
  ThemeId.CLASSIC
})
public @interface ThemeId {
  int NONE = 0;
  int TEMPORARY = -1;
  int CUSTOM = -2;

  int BLUE = 1;
  int NIGHT_BLACK = 2;
  int BLACK_WHITE = 3;
  int WHITE_BLACK = 4;
  int RED = 5;
  int ORANGE = 6;
  int GREEN = 7;
  int PINK = 8;
  int CYAN = 9;
  int NIGHT_BLUE = 10;
  int CLASSIC = 11;

  int ID_MIN = ThemeId.BLUE;
  int ID_MAX = ThemeId.CLASSIC;
  int COUNT = ID_MAX - ID_MIN + 1;
}