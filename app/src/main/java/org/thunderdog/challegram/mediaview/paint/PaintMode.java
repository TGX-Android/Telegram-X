package org.thunderdog.challegram.mediaview.paint;

/**
 * Date: 11/6/17
 * Author: default
 */

public class PaintMode {
  public static final int NONE = -1;

  public static final int PATH = 1;
  public static final int ARROW = 2;
  public static final int RECTANGLE = 3;

  public static final int FREE_MOVEMENT = 100;

  public static void save (int mode) {
    /*if (mode != FREE_MOVEMENT) {
      mode = clean(mode);
      if (mode == PATH) {
        Prefs.instance().remove("paint_mode");
      } else {
        Prefs.instance().putInt("paint_mode", mode);
      }
    }*/
  }

  public static int restore () {
    /*int mode = Prefs.instance().getInt("paint_mode", PATH);
    return clean(mode);*/
    return PATH;
  }

  public static int clean (int mode) {
    switch (mode) {
      case PATH:
      case ARROW:
      case RECTANGLE:
        return mode;
    }
    return PATH;
  }
}
