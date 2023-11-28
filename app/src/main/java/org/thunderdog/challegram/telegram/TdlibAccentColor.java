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
 * File created on 04/11/2023
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public final class TdlibAccentColor {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    InternalId.INACTIVE,
    InternalId.SAVED_MESSAGES,
    InternalId.REPLIES,
    InternalId.ARCHIVE,
    InternalId.ARCHIVE_PINNED,
    InternalId.REGULAR,
    InternalId.FILE_REGULAR
  })
  public @interface InternalId {
    int
      INACTIVE = -1,
      SAVED_MESSAGES = -2,
      REPLIES = -3,
      ARCHIVE = -4,
      ARCHIVE_PINNED = -5,
      REGULAR = -6,
      FILE_REGULAR = -7;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    BuiltInId.RED,
    BuiltInId.ORANGE,
    BuiltInId.PURPLE_VIOLET,
    BuiltInId.GREEN,
    BuiltInId.CYAN,
    BuiltInId.BLUE,
    BuiltInId.PINK
  })
  public @interface BuiltInId {
    // Accent color identifier; 0 - red, 1 - orange, 2 - purple/violet, 3 - green, 4 - cyan, 5 - blue, 6 - pink.
    int
      RED = 0,
      ORANGE = 1,
      PURPLE_VIOLET = 2,
      GREEN = 3,
      CYAN = 4,
      BLUE = 5,
      PINK = 6;
  }
  public static final int BUILT_IN_COLOR_COUNT = BuiltInId.PINK + 1;


  private final int id;
  private @Nullable TdApi.AccentColor accentColor;

  public TdlibAccentColor (int id) {
    this.id = id;
  }

  public TdlibAccentColor (@NonNull TdApi.AccentColor accentColor) {
    this(accentColor.id);
    this.accentColor = accentColor;
  }

  public int getId () {
    return id;
  }

  public TdApi.AccentColor getRemoteAccentColor () {
    return accentColor;
  }

  boolean updateColor (@NonNull TdApi.AccentColor updatedAccentColor) {
    if (Td.equalsTo(this.accentColor, updatedAccentColor)) {
      return false;
    }
    if (this.id != updatedAccentColor.id) {
      throw new IllegalArgumentException(this.id + " != " + updatedAccentColor.id);
    }
    if (this.accentColor != null) {
      Td.copyTo(updatedAccentColor, this.accentColor);
    } else {
      this.accentColor = updatedAccentColor;
    }
    return true;
  }

  @Override
  public boolean equals (@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof TdlibAccentColor) {
      TdlibAccentColor other = (TdlibAccentColor) obj;
      return this.id == other.id && Td.equalsTo(this.accentColor, other.accentColor);
    }
    return false;
  }

  // Utilities

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    UseCase.PRIMARY,
    UseCase.PRIMARY_BIG,
    UseCase.NAME,
    UseCase.LINE
  })
  private @interface UseCase {
    int PRIMARY = 0, PRIMARY_BIG = 1, NAME = 2, LINE = 3;
  }

  private static final int[][] builtInColorIds;

  static {
    builtInColorIds = new int[][] {
      // 0 - red
      new int[] {ColorId.avatarRed, ColorId.avatarRed_big, ColorId.nameRed, ColorId.lineRed},
      // 1 - orange
      new int[] {ColorId.avatarOrange, ColorId.avatarOrange_big, ColorId.nameOrange, ColorId.lineOrange},
      // 2 - purple/violet
      new int[] {ColorId.avatarViolet, ColorId.avatarViolet_big, ColorId.nameViolet, ColorId.lineViolet},
      // 3 - green
      new int[] {ColorId.avatarGreen, ColorId.avatarGreen_big, ColorId.nameGreen, ColorId.lineGreen},
      // 4 - cyan
      new int[] {ColorId.avatarCyan, ColorId.avatarCyan_big, ColorId.nameCyan, ColorId.lineCyan},
      // 5 - blue
      new int[] {ColorId.avatarBlue, ColorId.avatarBlue_big, ColorId.nameBlue, ColorId.lineBlue},
      // 6 - pink.
      new int[] {ColorId.avatarPink, ColorId.avatarPink_big, ColorId.namePink, ColorId.linePink},
    };
  }

  private static @ColorId int accentColorIdToAppColorId (int accentColorId, @UseCase int useCase) {
    if (Td.isBuiltInColorId(accentColorId)) {
      @BuiltInId int builtInColorId = accentColorId;
      return builtInColorIds[builtInColorId][useCase];
    }
    if (accentColorId < 0) {
      @InternalId int internalAccentColorId = accentColorId;
      switch (internalAccentColorId) {
        case InternalId.FILE_REGULAR:
          switch (useCase) {
            case UseCase.NAME:
            case UseCase.LINE:
              break; // unsupported
            case UseCase.PRIMARY:
            case UseCase.PRIMARY_BIG:
              return ColorId.file;
          }
        case InternalId.REGULAR:
          switch (useCase) {
            case UseCase.NAME:
              return ColorId.messageAuthor;
            case UseCase.LINE:
              return ColorId.messageVerticalLine;
            case UseCase.PRIMARY:
            case UseCase.PRIMARY_BIG:
              // Unsupported
              break;
          }
        case InternalId.INACTIVE:
          switch (useCase) {
            case UseCase.NAME:
              return ColorId.nameInactive;
            case UseCase.PRIMARY:
              return ColorId.avatarInactive;
            case UseCase.PRIMARY_BIG:
              return ColorId.avatarInactive_big;
            case UseCase.LINE:
              return ColorId.lineInactive;
          }
          break;
        case InternalId.SAVED_MESSAGES:
          switch (useCase) {
            case UseCase.NAME:
            case UseCase.LINE:
              break; // Unsupported
            case UseCase.PRIMARY:
              return ColorId.avatarSavedMessages;
            case UseCase.PRIMARY_BIG:
              return ColorId.avatarSavedMessages_big;
          }
          break;
        case InternalId.REPLIES:
          switch (useCase) {
            case UseCase.NAME:
              return ColorId.messageAuthor;
            case UseCase.LINE:
              return ColorId.messageVerticalLine;
            case UseCase.PRIMARY:
              return ColorId.avatarReplies;
            case UseCase.PRIMARY_BIG:
              return ColorId.avatarReplies_big;
          }
          break;
        case InternalId.ARCHIVE:
          switch (useCase) {
            case UseCase.NAME:
            case UseCase.LINE:
              break; // unsupported
            case UseCase.PRIMARY:
            case UseCase.PRIMARY_BIG:
              return ColorId.avatarArchive;
          }
          break;
        case InternalId.ARCHIVE_PINNED:
          switch (useCase) {
            case UseCase.NAME:
            case UseCase.LINE:
              break; // unsupported
            case UseCase.PRIMARY:
            case UseCase.PRIMARY_BIG:
              return ColorId.avatarArchivePinned;
          }
          break;
      }
      return accentColorIdToAppColorId(BuiltInId.BLUE, useCase);
    }
    return accentColorIdToAppColorId(InternalId.INACTIVE, useCase);
  }

  // Get color

  private boolean isBuiltInOrInternalTheme () {
    return id < 0 || Td.isBuiltInColorId(id);
  }

  private int getTargetBuiltInAccentColorId () {
    if (accentColor != null) {
      return accentColor.builtInAccentColorId;
    }
    if (isBuiltInOrInternalTheme()) {
      return id;
    }
    return InternalId.INACTIVE;
  }

  private long getComplexColor (@UseCase int useCase, boolean forceBuiltInColor) {
    boolean isId;
    int color;
    if (!forceBuiltInColor && accentColor != null) {
      // 2-3 colors are used only for stripe
      isId = false;
      color = ColorUtils.fromToArgb(
        ColorUtils.color(255, accentColor.lightThemeColors[0]),
        ColorUtils.color(255, accentColor.darkThemeColors[0]),
        Theme.getDarkFactor()
      );
    } else {
      int accentColorId = getTargetBuiltInAccentColorId();
      isId = true;
      color = accentColorIdToAppColorId(accentColorId, useCase);
    }
    return Theme.newComplexColor(isId, color);
  }

  @ColorInt
  private int getColor (@UseCase int useCase, boolean forceBuiltInColor) {
    long complexColor = getComplexColor(useCase, forceBuiltInColor);
    return Theme.toColorInt(complexColor);
  }

  public static String getTextRepresentation (@BuiltInId int accentColorId) {
    switch (accentColorId) {
      case BuiltInId.RED:
        return Lang.getString(R.string.AccentColorRed);
      case BuiltInId.ORANGE:
        return Lang.getString(R.string.AccentColorOrange);
      case BuiltInId.PURPLE_VIOLET:
        return Lang.getString(R.string.AccentColorPurple);
      case BuiltInId.GREEN:
        return Lang.getString(R.string.AccentColorGreen);
      case BuiltInId.CYAN:
        return Lang.getString(R.string.AccentColorCyan);
      case BuiltInId.BLUE:
        return Lang.getString(R.string.AccentColorBlue);
      case BuiltInId.PINK:
        return Lang.getString(R.string.AccentColorPink);
    }
    throw new IllegalArgumentException(Integer.toString(accentColorId));
  }

  public String getTextRepresentation () {
    @BuiltInId int accentColorId = getTargetBuiltInAccentColorId();
    if (accentColor != null) {
      int[] colors = Theme.isDark() ? accentColor.darkThemeColors : accentColor.lightThemeColors;
      if (colors.length > 1) {
        // TODO what to display?
      }
    }
    return getTextRepresentation(accentColorId);
  }

  @ColorInt
  public int getPrimaryColor () {
    return getColor(UseCase.PRIMARY, false);
  }

  @ColorInt
  public int getVerticalLineColor () {
    return getColor(UseCase.LINE, true);
  }

  public long getPrimaryComplexColor () {
    return getComplexColor(UseCase.PRIMARY, false);
  }

  @ColorInt
  public int getPrimaryContentColor () {
    return Theme.toColorInt(getPrimaryContentComplexColor());
  }

  public long getPrimaryContentComplexColor () {
    return Theme.newComplexColor(true, ColorId.avatar_content);
  }

  public int getPrimaryBigColor () {
    return getColor(UseCase.PRIMARY_BIG, false);
  }

  public int getNameColor () {
    return getColor(UseCase.NAME, true);
  }

  public long getNameComplexColor () {
    return getComplexColor(UseCase.NAME, true);
  }

  // Utils

  public static TdlibAccentColor defaultAccentColorForUserId (Tdlib tdlib, long possiblyFakeUserId) {
    int accentColorId = defaultAccentColorIdForUserId(possiblyFakeUserId);
    return tdlib.accentColor(accentColorId);
  }

  public static int defaultAccentColorIdForUserId (long userId) {
    if (userId >= 0 && userId < BUILT_IN_COLOR_COUNT) {
      return (int) userId;
    }
    try {
      String str = String.valueOf(userId);
      if (str.length() > 15) {
        str = str.substring(0, 15);
      }
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(str.getBytes(StringUtils.UTF_8));
      int b = digest[(int) Math.abs(userId % 16)];
      if (b < 0) {
        b += 256;
      }
      return Math.abs(b) % BUILT_IN_COLOR_COUNT;
    } catch (Throwable t) {
      Log.e("Cannot calculate default user color", t);
    }
    return (int) Math.abs(userId % BUILT_IN_COLOR_COUNT);
  }

  @ColorId

  public static int getFileColorId (TdApi.Document doc, boolean isOutBubble) {
    return getFileColorId(doc.fileName, doc.mimeType, isOutBubble);
  }

  @ColorId
  public static int getFileColorId (String fileName, @Nullable  String mimeType, boolean isOutBubble) {
    String mime = mimeType != null ? mimeType.toLowerCase() : null;
    int i = fileName.lastIndexOf('.');
    String ext = i != -1 ? fileName.substring(i + 1).toLowerCase() : "";

    // Android APKs
    if ("application/vnd.android.package-archive".equals(mime) || "apk".equals(ext)) {
      return ColorId.fileGreen;
    }

    if (
      "7z".equals(ext) || "application/x-7z-compressed".equals(mime) ||
        "zip".equals(ext) || "application/zip".equals(mime) ||
        "rar".equals(ext) || "application/x-rar-compressed".equals(mime)
    ) {
      return ColorId.fileYellow;
    }

    if (
      "pdf".equals(ext) || "application/pdf".equals(mime)
    ) {
      return ColorId.fileRed;
    }

    return isOutBubble ? ColorId.bubbleOut_file : ColorId.file;
  }

  /*public static int getColorIdForString (String string) {
    switch (Math.abs(string.hashCode()) % 3) {
      case 0: return ColorId.fileYellow;
      case 1: return ColorId.fileRed;
      case 2: return ColorId.fileGreen;
    }
    return ColorId.file;
  }

  public static int getColorIdForName (String name) {
    return color_ids[MathUtils.pickNumber(color_ids.length, name)];
  }

  public static int getNameColorId (@ColorId int avatarColorId) {
    switch (avatarColorId) {
      case ColorId.avatarRed:
        return ColorId.nameRed;
      case ColorId.avatarOrange:
        return ColorId.nameOrange;
      case ColorId.avatarYellow:
        return ColorId.nameYellow;
      case ColorId.avatarGreen:
        return ColorId.nameGreen;
      case ColorId.avatarCyan:
        return ColorId.nameCyan;
      case ColorId.avatarBlue:
        return ColorId.nameBlue;
      case ColorId.avatarViolet:
        return ColorId.nameViolet;
      case ColorId.avatarPink:
        return ColorId.namePink;
      case ColorId.avatarSavedMessages:
        return ColorId.messageAuthor;
      case ColorId.avatarInactive:
        return ColorId.nameInactive;
    }
    return ColorId.messageAuthor;
  }

  public static int calculateTdlibAccentColorId (long userId) {
    int index = calculateLegacyColorIndex(0, userId);
    // Accent color identifier; 0 - red, 1 - orange, 2 - purple/violet, 3 - green, 4 - cyan, 5 - blue, 6 - pink.
    switch (index) {
      case 0: // ColorId.avatarRed
      case 1: // ColorId.avatarOrange
      case 3: // ColorId.avatarGreen
      case 4: // ColorId.avatarCyan
      case 5: // ColorId.avatarBlue
        return index;
      case 6: // ColorId.avatarViolet
        return 2; // purple/violet
      case 2: // ColorId.avatarYellow
        return 1; // orange, as yellow was removed
      case 7: //  ColorId.avatarPink
        return 0;
      default: // unreachable
        throw new IllegalStateException(Integer.toString(index));
    }
  }
  */

  /*private static final int[] color_ids =  {
    ColorId.avatarRed      *//* red 0 *//*,
    ColorId.avatarOrange   *//* orange 1 *//*,
    ColorId.avatarYellow   *//* yellow 2 *//*,
    ColorId.avatarGreen    *//* green 3 *//*,
    ColorId.avatarCyan     *//* cyan 4 *//*,
    ColorId.avatarBlue     *//* blue 5 *//*,
    ColorId.avatarViolet   *//* violet 6 *//*,
    ColorId.avatarPink     *//* pink 7 *//*
  };*/

  /*private static int calculateLegacyColorIndex (long selfUserId, long id) {
    if (id >= 0 && id < color_ids.length) {
      return (int) id;
    }
    try {
      String str;
      if (id >= 0 && selfUserId != 0) {
        str = String.format(Locale.US, "%d%d", id, selfUserId);
      } else {
        str = String.format(Locale.US, "%d", id);
      }
      if (str.length() > 15) {
        str = str.substring(0, 15);
      }
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(str.getBytes(StringUtils.UTF_8));
      int b = digest[(int) Math.abs(id % 16)];
      if (b < 0) {
        b += 256;
      }
      return Math.abs(b) % color_ids.length;
    } catch (Throwable t) {
      Log.e("Cannot calculate user color", t);
    }

    return (int) Math.abs(id % color_ids.length);
  }*/
}
