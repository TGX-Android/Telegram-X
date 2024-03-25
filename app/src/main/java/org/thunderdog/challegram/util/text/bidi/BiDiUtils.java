package org.thunderdog.challegram.util.text.bidi;

import me.vkryl.core.BitwiseUtils;

public class BiDiUtils {
  private static final int IS_RTL_FLAG = 1;
  private static final int IS_PARAGRAPH_RTL_FLAG = 1 << 8;
  private static final int IS_VALID_FLAG = 1 << 9;
  
  public static @BiDiEntity int create (int level, int paragraphLevel) {
    return (level & 0xFF) | ((paragraphLevel & 1) << 8) | IS_VALID_FLAG;
  }

  public static boolean isValid (@BiDiEntity int flags) {
    return BitwiseUtils.hasFlag(flags, IS_VALID_FLAG);
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
}
