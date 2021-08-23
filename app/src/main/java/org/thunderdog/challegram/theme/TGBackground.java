package org.thunderdog.challegram.theme;

import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageFileRemote;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.HashMap;
import java.util.Map;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.leveldb.LevelDB;

/**
 * Date: 01/04/2017
 * Author: default
 */

public class TGBackground {
  private final int accountId;
  private final String name;
  private final TdApi.BackgroundType type;
  private final String customPath;

  private int legacyWallpaperId;
  private String legacyRemoteId;

  private ImageFile target;
  private ImageFile preview;
  private ImageFile miniThumbnail;

  public static TGBackground newEmptyWallpaper (Tdlib tdlib) {
    return new TGBackground(tdlib, (String) null);
  }

  public static TGBackground newUnknownWallpaper (Tdlib tdlib, String name) {
    int legacyWallpaperId = resolveLegacyWallpaperId(name, null);
    if (legacyWallpaperId != 0)
      return newLegacyWallpaper(tdlib, legacyWallpaperId);
    return new TGBackground(tdlib, name, null, 0);
  }

  public static TGBackground newLegacyWallpaper (Tdlib tdlib, int legacyWallpaperId) {
    final TGBackground background;
    if (legacyWallpaperId == ID_SOLID_BLUE) {
      background = new TGBackground(tdlib, COLOR_SOLID_BLUE, legacyWallpaperId);
    } else {
      String name = getBackgroundForLegacyWallpaperId(legacyWallpaperId);
      if (name == null)
        return null;
      background = new TGBackground(tdlib, name, new TdApi.BackgroundTypeWallpaper(false, false), legacyWallpaperId);
    }
    return background;
  }

  public TGBackground (Tdlib tdlib, @Nullable String localPath) {
    this.accountId = tdlib.id();
    this.name = null;
    this.type = null;
    this.legacyWallpaperId = 0;
    this.customPath = localPath;
    if (!StringUtils.isEmpty(localPath)) {
      setTarget(new ImageFileLocal(localPath));
      ImageFileLocal preview = new ImageFileLocal(localPath);
      preview.setSize(Screen.dp(105f));
      setPreview(preview);
    }
  }

  private TGBackground (Tdlib tdlib, String name, TdApi.BackgroundType type, int legacyWallpaperId) {
    this(tdlib, name, type, legacyWallpaperId, null);
  }

  private TGBackground (Tdlib tdlib, String name, TdApi.BackgroundType type, int legacyWallpaperId, String legacyRemoteIdKey) {
    this.accountId = tdlib.id();
    this.name = name;
    this.type = type;
    this.customPath = null;
    this.legacyWallpaperId = legacyWallpaperId;
    this.legacyRemoteId = StringUtils.isEmpty(legacyRemoteIdKey) ? null : Settings.instance().getString(legacyRemoteIdKey, null);

    boolean needImages;
    if (type != null) {
      switch (type.getConstructor()) {
        case TdApi.BackgroundTypePattern.CONSTRUCTOR:
        case TdApi.BackgroundTypeWallpaper.CONSTRUCTOR:
          needImages = true;
          break;
        case TdApi.BackgroundTypeFill.CONSTRUCTOR:
          needImages = false;
          break;
        default:
          throw new UnsupportedOperationException(type.toString());
      }
    } else {
      needImages = true;
    }
    if (needImages) {
      setTarget(new ImageFileRemote(tdlib, null, "background_" + name) {
        @Override
        public void extractFile (Client.ResultHandler handler) {
          Runnable onFail = () -> this.tdlib().client().send(new TdApi.SearchBackground(name), result -> {
            if (result.getConstructor() == TdApi.Background.CONSTRUCTOR) {
              TdApi.Background background = (TdApi.Background) result;
              if (background.document != null && !StringUtils.isEmpty(background.document.document.remote.id)) {
                Settings.instance().putString("wallpaper_" + name, background.document.document.remote.id);
              }
              handler.onResult(background.document != null ? background.document.document : new TdApi.Error(-1, "Document is inaccessible"));
            } else {
              handler.onResult(result);
            }
          });
          if (!StringUtils.isEmpty(legacyRemoteId)) {
            this.tdlib().client().send(new TdApi.GetRemoteFile(legacyRemoteId, new TdApi.FileTypeWallpaper()), result -> {
              if (result.getConstructor() == TdApi.File.CONSTRUCTOR && TD.isFileLoadedAndExists((TdApi.File) result)) {
                handler.onResult(result);
              } else {
                Settings.instance().remove(legacyRemoteIdKey);
                legacyRemoteId = null;
                onFail.run();
              }
            });
          } else {
            onFail.run();
          }
        }
      });
      setPreview(new ImageFileRemote(tdlib, null, "background_preview_" + name) {
        @Override
        public void extractFile (Client.ResultHandler handler) {
          this.tdlib().client().send(new TdApi.SearchBackground(name), result -> {
            if (result.getConstructor() == TdApi.Background.CONSTRUCTOR) {
              TdApi.Background background = (TdApi.Background) result;
              handler.onResult(background.document != null && background.document.thumbnail != null ? background.document.thumbnail.file : new TdApi.Error(-1, "Document preview is inaccessible"));
            } else {
              handler.onResult(result);
            }
          });
        }
      });
    }
  }

  public TGBackground (Tdlib tdlib, int solidColor) {
    this(tdlib, solidColor, resolveLegacyWallpaperId(solidColor));
  }

  private TGBackground (Tdlib tdlib, int solidColor, int legacyWallpaperId) {
    this(tdlib, getNameForColor(solidColor), new TdApi.BackgroundTypeFill(new TdApi.BackgroundFillSolid(solidColor)), legacyWallpaperId);
  }

  private TGBackground (Tdlib tdlib, int topColor, int bottomColor, int rotationAngle, int legacyWallpaperId) {
    this(tdlib, getNameForColor(topColor, bottomColor, rotationAngle), new TdApi.BackgroundTypeFill(new TdApi.BackgroundFillGradient(topColor, bottomColor, rotationAngle)), legacyWallpaperId);
  }

  public TGBackground (Tdlib tdlib, TdApi.Background background) {
    this.accountId = tdlib.id();
    this.name = background.name;
    this.type = background.type;
    this.customPath = null;
    this.legacyWallpaperId = resolveLegacyWallpaperId(background.name, background.type);

    switch (type.getConstructor()) {
      case TdApi.BackgroundTypePattern.CONSTRUCTOR:
      case TdApi.BackgroundTypeWallpaper.CONSTRUCTOR: {
        if (background.document != null) {
          setTarget(new ImageFile(tdlib, background.document.document));
          if (background.document.thumbnail != null)
            setPreview(TD.toImageFile(tdlib, background.document.thumbnail));
          if (background.document.minithumbnail != null)
            setMiniThumbnail(new ImageFileLocal(background.document.minithumbnail));
        }
        break;
      }
      case TdApi.BackgroundTypeFill.CONSTRUCTOR:
        // Nothing to do
        break;
    }
  }

  public static int getDefaultWallpaperId (final @ThemeId int themeId) {
    switch (themeId) {
      case ThemeId.TEMPORARY:
      case ThemeId.CUSTOM:
      case ThemeId.NONE:
        break;

      case ThemeId.BLACK_WHITE:
      case ThemeId.NIGHT_BLACK:
      case ThemeId.NIGHT_BLUE:
        return 0; // Disabled by default

      case ThemeId.BLUE:
      case ThemeId.CLASSIC:
      case ThemeId.CYAN:
      case ThemeId.WHITE_BLACK:
        return ID_CATS_BLUE;
      case ThemeId.GREEN:
        return ID_CATS_GREEN;
      case ThemeId.PINK:
        return ID_CATS_PINK;
      case ThemeId.RED:
      case ThemeId.ORANGE:
        return ID_CATS_BEIGE;
    }
    throw Theme.newError(themeId, "themeId");
  }

  private void setTarget (ImageFile target) {
    this.target = target;
    if (target != null) {
      target.setNeedPalette(true);
      target.setScaleType(ImageFile.CENTER_CROP);
      target.setForceArgb8888();

      target.setSize(Math.min(1480, Screen.widestSide()));
      target.setNoBlur();
    }
  }

  private void setPreview (ImageFile preview) {
    this.preview = preview;
    if (preview != null) {
      preview.setNeedPalette(true);
      preview.setScaleType(ImageFile.CENTER_CROP);
      // preview.setSize(Screen.dp(90f));
    }
  }

  private void setMiniThumbnail (ImageFile miniThumbnail) {
    this.miniThumbnail = miniThumbnail;
    if (miniThumbnail != null) {
      miniThumbnail.setScaleType(ImageFile.CENTER_CROP);
      // miniThumbnail.setSize(Screen.dp(90f));
    }
  }

  public boolean isEmpty () {
    return StringUtils.isEmpty(name) && StringUtils.isEmpty(customPath);
  }

  public static boolean compare (TGBackground a, TGBackground b) {
    if ((a == null || a.isEmpty()) && (b == null || b.isEmpty()))
      return true;
    if (a == null || b == null || a.isEmpty() != b.isEmpty() || a.isCustom() != b.isCustom() || a.isFill() != b.isFill() || a.isFillGradient() != b.isFillGradient() || a.isFillSolid() != b.isFillSolid())
      return false;
    if (a.isCustom())
      return StringUtils.equalsOrBothEmpty(a.getCustomPath(), b.getCustomPath());
    if (a.isFillSolid())
      return a.getBackgroundColor() == b.getBackgroundColor();
    if (a.isFillGradient())
      return a.getTopColor() == b.getTopColor() && a.getBottomColor() == b.getBottomColor() && a.getRotationAngle() == b.getRotationAngle();
    return StringUtils.equalsOrBothEmpty(a.getName(), b.getName());
  }

  public ImageFile getPreview (boolean mini) {
    return mini ? miniThumbnail : preview != null ? preview : target;
  }

  public String getName () {
    return name;
  }

  public boolean isFill () {
    return type != null && type.getConstructor() == TdApi.BackgroundTypeFill.CONSTRUCTOR;
  }

  public boolean isFillSolid () {
    return isFill() && ((TdApi.BackgroundTypeFill) type).fill.getConstructor() == TdApi.BackgroundFillSolid.CONSTRUCTOR;
  }

  public boolean isFillGradient () {
    return isFill() && ((TdApi.BackgroundTypeFill) type).fill.getConstructor() == TdApi.BackgroundFillGradient.CONSTRUCTOR;
  }

  public boolean isFillFreeformGradient () {
    return isFill() && ((TdApi.BackgroundTypeFill) type).fill.getConstructor() == TdApi.BackgroundFillFreeformGradient.CONSTRUCTOR;
  }

  public boolean isPattern () {
    return type != null && type.getConstructor() == TdApi.BackgroundTypePattern.CONSTRUCTOR;
  }

  public boolean isPatternBackgroundGradient () {
    return isPattern() && ((TdApi.BackgroundTypePattern) type).fill.getConstructor() == TdApi.BackgroundFillGradient.CONSTRUCTOR;
  }

  public boolean isPatternBackgroundFreeformGradient () {
    return isPattern() && ((TdApi.BackgroundTypePattern) type).fill.getConstructor() == TdApi.BackgroundFillFreeformGradient.CONSTRUCTOR;
  }

  public void setLegacyWallpaperId (int wallpaperId) {
    this.legacyWallpaperId = wallpaperId;
  }

  public int getLegacyWallpaperId () {
    return legacyWallpaperId;
  }

  public boolean isLegacy () {
    return legacyWallpaperId != 0;
  }

  public boolean isCat () {
    return legacyWallpaperId != 0 && isCatId(legacyWallpaperId);
  }

  public boolean isBuiltIn () {
    return legacyWallpaperId == ID_CATS_BLUE;
  }

  public boolean isCustom () {
    return !StringUtils.isEmpty(customPath);
  }

  public String getCustomPath () {
    return customPath;
  }

  private static int getBackgroundColor (TdApi.BackgroundFill fill, int defaultColor) {
    switch (fill.getConstructor()) {
      case TdApi.BackgroundFillGradient.CONSTRUCTOR: {
        TdApi.BackgroundFillGradient gradient = (TdApi.BackgroundFillGradient) fill;
        return ColorUtils.fromToArgb(ColorUtils.color(255, gradient.topColor), ColorUtils.color(255, gradient.bottomColor), .5f);
      }
      case TdApi.BackgroundFillFreeformGradient.CONSTRUCTOR:
        // There can't be less then 2 colors in freeform gradient
        TdApi.BackgroundFillFreeformGradient gradient = (TdApi.BackgroundFillFreeformGradient) fill;
        return ColorUtils.color(255, gradient.colors.length >= 3 ? gradient.colors[2] : gradient.colors[1]);
      case TdApi.BackgroundFillSolid.CONSTRUCTOR:
        return ColorUtils.color(255, ((TdApi.BackgroundFillSolid) fill).color);
    }
    return defaultColor;
  }

  public int getBackgroundColor () {
    return getBackgroundColor(Color.TRANSPARENT);
  }

  public int getBackgroundColor (int defaultColor) {
    if (type != null) {
      switch (type.getConstructor()) {
        case TdApi.BackgroundTypeFill.CONSTRUCTOR:
          return getBackgroundColor(((TdApi.BackgroundTypeFill) type).fill, defaultColor);
        case TdApi.BackgroundTypePattern.CONSTRUCTOR:
          return getBackgroundColor(((TdApi.BackgroundTypePattern) type).fill, defaultColor);
        case TdApi.BackgroundTypeWallpaper.CONSTRUCTOR:
          break;
      }
    }
    return defaultColor;
  }

  public int getTopColor () {
    if (isFillGradient()) {
      return ((TdApi.BackgroundFillGradient) ((TdApi.BackgroundTypeFill) type).fill).topColor;
    } else if (isPatternBackgroundGradient()) {
      return ((TdApi.BackgroundFillGradient) ((TdApi.BackgroundTypePattern) type).fill).topColor;
    } else if (isFillFreeformGradient()) {
      return ((TdApi.BackgroundFillFreeformGradient) ((TdApi.BackgroundTypeFill) type).fill).colors[0];
    } else if (isPatternBackgroundFreeformGradient()) {
      return ((TdApi.BackgroundFillFreeformGradient) ((TdApi.BackgroundTypePattern) type).fill).colors[0];
    }
    return 0;
  }

  public int[] getFreeformColors() {
    if (isFillFreeformGradient()) {
      return ((TdApi.BackgroundFillFreeformGradient) ((TdApi.BackgroundTypeFill) type).fill).colors;
    } else if (isPatternBackgroundFreeformGradient()) {
      return ((TdApi.BackgroundFillFreeformGradient) ((TdApi.BackgroundTypePattern) type).fill).colors;
    }
    return new int[0];
  }

  public int getBottomColor () {
    if (isFillGradient()) {
      return ((TdApi.BackgroundFillGradient) ((TdApi.BackgroundTypeFill) type).fill).bottomColor;
    } else if (isPatternBackgroundGradient()) {
      return ((TdApi.BackgroundFillGradient) ((TdApi.BackgroundTypePattern) type).fill).bottomColor;
    } else if (isFillFreeformGradient()) {
      int[] colors = ((TdApi.BackgroundFillFreeformGradient) ((TdApi.BackgroundTypeFill) type).fill).colors;
      return colors[colors.length - 1];
    } else if (isPatternBackgroundFreeformGradient()) {
      int[] colors = ((TdApi.BackgroundFillFreeformGradient) ((TdApi.BackgroundTypePattern) type).fill).colors;
      return colors[colors.length - 1];
    }
    return 0;
  }

  public int getRotationAngle () {
    if (isFillGradient()) {
      return ((TdApi.BackgroundFillGradient) ((TdApi.BackgroundTypeFill) type).fill).rotationAngle;
    } else if (isPatternBackgroundGradient()) {
      return ((TdApi.BackgroundFillGradient) ((TdApi.BackgroundTypePattern) type).fill).rotationAngle;
    }
    return 0;
  }

  @Nullable
  private Integer cachedColor;

  public int getPatternColor () {
    if (isPattern()) {
      if (cachedColor == null)
        cachedColor = getPatternColor(((TdApi.BackgroundTypePattern) type).fill);
      return cachedColor;
    }
    return 0;
  }

  public int getSolidOverlayColor () {
    if (isFill()) {
      if (cachedColor == null)
        cachedColor = getPatternColor(((TdApi.BackgroundTypeFill) type).fill);
      return cachedColor;
    }
    return 0;
  }

  public float getPatternIntensity () {
    return isPattern() ? (float) ((TdApi.BackgroundTypePattern) type).intensity / 100f : 1f;
  }

  public boolean isMoving () {
    if (type != null) {
      switch (type.getConstructor()) {
        case TdApi.BackgroundTypeFill.CONSTRUCTOR:
          break;
        case TdApi.BackgroundTypePattern.CONSTRUCTOR:
          return ((TdApi.BackgroundTypePattern) type).isMoving;
        case TdApi.BackgroundTypeWallpaper.CONSTRUCTOR:
          return ((TdApi.BackgroundTypeWallpaper) type).isMoving;
      }
    }
    return false;
  }

  public boolean isBlurred () {
    return type != null && type.getConstructor() == TdApi.BackgroundTypeWallpaper.CONSTRUCTOR && ((TdApi.BackgroundTypeWallpaper) type).isBlurred;
  }

  public void requestFiles (DoubleImageReceiver receiver, boolean needPreview) {
    if (target != null) {
      receiver.requestFile(needPreview ? this.preview : null, target);
    } else {
      receiver.requestFile(null, null);
    }
  }

  public void load (Tdlib tdlib) {
    if (target instanceof ImageFileRemote) {
      ((ImageFileRemote) target).extractFile(result -> {
        if (result instanceof TdApi.File) {
          tdlib.client().send(new TdApi.DownloadFile(((TdApi.File) result).id, 32, 0, 0, false), tdlib.okHandler());
        }
      });
    }
  }

  private static final int BACKGROUND_TYPE_FILL = 1;
  private static final int BACKGROUND_TYPE_WALLPAPER = 2;
  private static final int BACKGROUND_TYPE_PATTERN = 3;

  private static final int FILL_TYPE_SOLID = 1;
  private static final int FILL_TYPE_GRADIENT = 2;
  private static final int FILL_TYPE_FREEFORM_GRADIENT = 3;

  private static void putFill (SharedPreferences.Editor editor, String key, TdApi.BackgroundFill fill) {
    switch (fill.getConstructor()) {
      case TdApi.BackgroundFillSolid.CONSTRUCTOR: {
        editor.putInt(key + "_fill", FILL_TYPE_SOLID);

        TdApi.BackgroundFillSolid solid = (TdApi.BackgroundFillSolid) fill;
        editor.putInt(key + "_color", solid.color);

        editor
          .remove(key + "_color_top")
          .remove(key + "_color_bottom")
          .remove(key + "_colors")
          .remove(key + "_rotation_angle");
        break;
      }
      case TdApi.BackgroundFillGradient.CONSTRUCTOR: {
        editor.putInt(key + "_fill", FILL_TYPE_GRADIENT);

        TdApi.BackgroundFillGradient gradient = (TdApi.BackgroundFillGradient) fill;
        editor.putInt(key + "_color_top", gradient.topColor);
        editor.putInt(key + "_color_bottom", gradient.bottomColor);
        editor.putInt(key + "_rotation_angle", gradient.rotationAngle);

        editor
          .remove(key + "_color")
          .remove(key + "_colors");
        break;
      }
      case TdApi.BackgroundFillFreeformGradient.CONSTRUCTOR: {
        editor.putInt(key + "_fill", FILL_TYPE_FREEFORM_GRADIENT);

        TdApi.BackgroundFillFreeformGradient gradient = (TdApi.BackgroundFillFreeformGradient) fill;
        ((LevelDB) editor).putIntArray(key + "_colors", gradient.colors);

        editor
          .remove(key + "_color")
          .remove(key + "_color_top")
          .remove(key + "_color_bottom")
          .remove(key + "_rotation_angle");
        break;
      }
      default:
        throw new UnsupportedOperationException(fill.toString());
    }
  }

  public void save (int usageIdentifier) {
    final String key = (accountId != 0 ? "wallpaper_" + accountId : "wallpaper") + Settings.getWallpaperIdentifierSuffix(usageIdentifier);

    SharedPreferences.Editor editor = Settings.instance().edit();

    if (StringUtils.isEmpty(legacyRemoteId))
      editor.remove(key + "_remote_id");
    else
      editor.putString(key + "_remote_id", legacyRemoteId);
    if (isEmpty()) {
      editor.putBoolean(key + "_empty", true);
    } else {
      editor.remove(key + "_empty");
    }
    if (isCustom()) {
      editor.putBoolean(key + "_custom", true).putString(key + "_path", customPath);
    } else {
      editor.remove(key + "_custom").remove(key + "_path");
    }
    if (!StringUtils.isEmpty(name) && !(isFill() && name.equals(getNameForColor(getBackgroundColor())))) {
      editor.putString(key + "_name", name);
    } else {
      editor.remove(key + "_name");
    }
    if (type != null) {
      switch (type.getConstructor()) {
        case TdApi.BackgroundTypeFill.CONSTRUCTOR: {
          TdApi.BackgroundTypeFill fill = (TdApi.BackgroundTypeFill) type;
          editor
            .putInt(key + "_type", BACKGROUND_TYPE_FILL)
            .remove(key + "_intensity")
            .remove(key + "_moving")
            .remove(key + "_blurred");
          putFill(editor, key, fill.fill);
          break;
        }
        case TdApi.BackgroundTypeWallpaper.CONSTRUCTOR: {
          TdApi.BackgroundTypeWallpaper wallpaper = (TdApi.BackgroundTypeWallpaper) type;
          editor
            .putInt(key + "_type", BACKGROUND_TYPE_WALLPAPER)
            .putBoolean(key + "_moving", wallpaper.isMoving)
            .putBoolean(key + "_blurred", wallpaper.isBlurred)

            .remove(key + "_color")
            .remove(key + "_intensity");
          break;
        }
        case TdApi.BackgroundTypePattern.CONSTRUCTOR: {
          TdApi.BackgroundTypePattern pattern = (TdApi.BackgroundTypePattern) type;
          editor
            .putInt(key + "_type", BACKGROUND_TYPE_PATTERN)
            .putInt(key + "_intensity", pattern.intensity)
            .putBoolean(key + "_moving", pattern.isMoving)

            .remove(key + "_blurred");
          putFill(editor, key, pattern.fill);
          break;
        }
        default:
          throw new UnsupportedOperationException(type.toString());
      }
    } else {
      editor
        .remove(key + "_type")
        .remove(key + "_color")
        .remove(key + "_intensity")
        .remove(key + "_moving")
        .remove(key + "_blurred")
        .remove(key + "_fill")
        .remove(key + "_color_top")
        .remove(key + "_color_bottom")
        .remove(key + "_colors")
        .remove(key + "_rotation_angle");
    }
    editor.apply();
  }

  private static TdApi.BackgroundFill restoreFill (SharedPreferences prefs, String key) {
    int fillType = prefs.getInt(key + "_fill", FILL_TYPE_SOLID);
    switch (fillType) {
      case FILL_TYPE_GRADIENT: {
        int topColor = prefs.getInt(key + "_color_top", 0);
        int bottomColor = prefs.getInt(key + "_color_bottom", 0);
        int rotationAngle = prefs.getInt(key + "_rotation_angle", 0);
        return new TdApi.BackgroundFillGradient(topColor, bottomColor, rotationAngle);
      }
      case FILL_TYPE_FREEFORM_GRADIENT: {
        int[] colors = ((LevelDB) prefs).getIntArray(key + "_colors");
        return new TdApi.BackgroundFillFreeformGradient(colors);
      }
      case FILL_TYPE_SOLID:
      default: {
        int color = prefs.getInt(key + "_color", 0);
        return new TdApi.BackgroundFillSolid(color);
      }
    }
  }

  public static TGBackground restore (Tdlib tdlib, int usageIdentifier) {
    final int accountId = tdlib.accountId();
    final String key = (accountId != 0 ? "wallpaper_" + accountId : "wallpaper") + Settings.getWallpaperIdentifierSuffix(usageIdentifier);
    final SharedPreferences prefs = Settings.instance().pmc();
    if (prefs.getBoolean(key + "_empty", false))
      return newEmptyWallpaper(tdlib);
    if (prefs.getBoolean(key + "_custom", false))
      return new TGBackground(tdlib, prefs.getString(key + "_path", null));
    String name = prefs.getString(key + "_name", null);
    TdApi.BackgroundType type;
    switch (prefs.getInt(key + "_type", 0)) {
      case BACKGROUND_TYPE_FILL: {
        TdApi.BackgroundFill fill = restoreFill(prefs, key);
        type = new TdApi.BackgroundTypeFill(fill);
        if (StringUtils.isEmpty(name)) {
          name = getNameForFill(fill);
        }
        break;
      }
      case BACKGROUND_TYPE_WALLPAPER:
        type = new TdApi.BackgroundTypeWallpaper(prefs.getBoolean(key + "_blurred", false), prefs.getBoolean(key + "_moving", false));
        break;
      case BACKGROUND_TYPE_PATTERN:
        type = new TdApi.BackgroundTypePattern(restoreFill(prefs, key), prefs.getInt(key + "_intensity", 0), prefs.getBoolean(key + "_moving", false));
        break;
      default:
        return null;
    }
    return new TGBackground(tdlib, name, type, resolveLegacyWallpaperId(name, type), key + "_legacy_id");
  }

  public static void migrateLegacyWallpaper (SharedPreferences.Editor editor, String prefix, int legacyWallpaperId, int color, String persistentId) {
    String name = getBackgroundForLegacyWallpaperId(legacyWallpaperId);
    if (!StringUtils.isEmpty(name)) {
      editor.putString(prefix + "_name", name);
      editor.putInt(prefix + "_type", BACKGROUND_TYPE_WALLPAPER);
      if (!StringUtils.isEmpty(persistentId)) {
        editor.putString(prefix + "_remote_id", persistentId);
      }
    } else if (legacyWallpaperId == ID_SOLID_BLUE) {
      // editor.putString(prefix + "_name", getNameForColor(COLOR_SOLID_BLUE));
      editor.putInt(prefix + "_type", BACKGROUND_TYPE_FILL);
      editor.putInt(prefix + "_fill", FILL_TYPE_SOLID);
      editor.putInt(prefix + "_color", getLegacyWallpaperColor(legacyWallpaperId));
    } else if (legacyWallpaperId == -1 && color != 0) {
      editor.putInt(prefix + "_type", BACKGROUND_TYPE_FILL);
      editor.putInt(prefix + "_fill", FILL_TYPE_SOLID);
      editor.putInt(prefix + "_color", color);
    }
  }

  // LEGACY

  private static final int ID_SOLID_BLUE = 1000000;
  public static final int ID_CATS_BLUE = 1000001;
  public static final int ID_CATS_PINK = 36;
  public static final int ID_CATS_GREEN = 35;
  private static final int ID_CATS_ORANGE = 33;
  public static final int ID_CATS_BEIGE = 32;
  private static final int ID_BLUE_CIRCLES = 114;
  private static final int ID_GRAY_FOREST = 104;
  private static final int ID_GALAXY = 10;
  private static final int ID_POLYGONS = 5;
  private static final int ID_PAINT = 11;
  private static final int ID_CITY = 107;
  private static final int ID_CITY_BW = 112;

  private static final int COLOR_SOLID_BLUE = 0xd6e4ef;

  public static boolean isCatId (int id) {
    switch (id) {
      case ID_CATS_BLUE:
      case ID_CATS_PINK:
      case ID_CATS_GREEN:
      case ID_CATS_ORANGE:
      case ID_CATS_BEIGE:
        return true;
    }
    return false;
  }

  public static int getScore (int wallpaperId, boolean isDark) {
    if (isDark) {
      switch (wallpaperId) {
        case ID_GRAY_FOREST:
          return 3;
        case ID_CITY:
        case ID_GALAXY:
          return 2;
        case ID_POLYGONS:
        case ID_PAINT:
        case ID_CITY_BW:
          return 1;
      }
      return 0;
    } else {
      switch (wallpaperId) {
        case ID_CATS_BLUE:
          return 1;
        case ID_BLUE_CIRCLES:
          return -2;
      }
      return 0;
    }
  }

  private static float[] RGBtoHSB(int r, int g, int b) {
    float hue, saturation, brightness;
    float[] hsbvals = new float[3];
    int cmax = (r > g) ? r : g;
    if (b > cmax) {
      cmax = b;
    }
    int cmin = (r < g) ? r : g;
    if (b < cmin) {
      cmin = b;
    }

    brightness = ((float) cmax) / 255.0f;
    if (cmax != 0) {
      saturation = ((float) (cmax - cmin)) / ((float) cmax);
    } else {
      saturation = 0;
    }
    if (saturation == 0) {
      hue = 0;
    } else {
      float redc = ((float) (cmax - r)) / ((float) (cmax - cmin));
      float greenc = ((float) (cmax - g)) / ((float) (cmax - cmin));
      float bluec = ((float) (cmax - b)) / ((float) (cmax - cmin));
      if (r == cmax) {
        hue = bluec - greenc;
      } else if (g == cmax) {
        hue = 2.0f + redc - bluec;
      } else {
        hue = 4.0f + greenc - redc;
      }
      hue = hue / 6.0f;
      if (hue < 0) {
        hue = hue + 1.0f;
      }
    }
    hsbvals[0] = hue;
    hsbvals[1] = saturation;
    hsbvals[2] = brightness;
    return hsbvals;
  }

  private static int HSBtoRGB(float hue, float saturation, float brightness) {
    int r = 0, g = 0, b = 0;
    if (saturation == 0) {
      r = g = b = (int) (brightness * 255.0f + 0.5f);
    } else {
      float h = (hue - (float) Math.floor(hue)) * 6.0f;
      float f = h - (float) java.lang.Math.floor(h);
      float p = brightness * (1.0f - saturation);
      float q = brightness * (1.0f - saturation * f);
      float t = brightness * (1.0f - (saturation * (1.0f - f)));
      switch ((int) h) {
        case 0:
          r = (int) (brightness * 255.0f + 0.5f);
          g = (int) (t * 255.0f + 0.5f);
          b = (int) (p * 255.0f + 0.5f);
          break;
        case 1:
          r = (int) (q * 255.0f + 0.5f);
          g = (int) (brightness * 255.0f + 0.5f);
          b = (int) (p * 255.0f + 0.5f);
          break;
        case 2:
          r = (int) (p * 255.0f + 0.5f);
          g = (int) (brightness * 255.0f + 0.5f);
          b = (int) (t * 255.0f + 0.5f);
          break;
        case 3:
          r = (int) (p * 255.0f + 0.5f);
          g = (int) (q * 255.0f + 0.5f);
          b = (int) (brightness * 255.0f + 0.5f);
          break;
        case 4:
          r = (int) (t * 255.0f + 0.5f);
          g = (int) (p * 255.0f + 0.5f);
          b = (int) (brightness * 255.0f + 0.5f);
          break;
        case 5:
          r = (int) (brightness * 255.0f + 0.5f);
          g = (int) (p * 255.0f + 0.5f);
          b = (int) (q * 255.0f + 0.5f);
          break;
      }
    }
    return 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
  }

  private static int getPatternColor (TdApi.BackgroundFill fill) {
    switch (fill.getConstructor()) {
      case TdApi.BackgroundFillSolid.CONSTRUCTOR:
        return getPatternColor(((TdApi.BackgroundFillSolid) fill).color);
      case TdApi.BackgroundFillGradient.CONSTRUCTOR: {
        TdApi.BackgroundFillGradient gradient = (TdApi.BackgroundFillGradient) fill;
        return getPatternColor(ColorUtils.fromToArgb(ColorUtils.color(255, gradient.topColor), ColorUtils.color(255, gradient.bottomColor), .5f));
      }
      case TdApi.BackgroundFillFreeformGradient.CONSTRUCTOR: {
        TdApi.BackgroundFillFreeformGradient gradient = (TdApi.BackgroundFillFreeformGradient) fill;
        return getPatternColor(ColorUtils.fromToArgb(ColorUtils.color(255, gradient.colors[0]), ColorUtils.color(255, gradient.colors[gradient.colors.length - 1]), .5f));
      }
    }
    throw new UnsupportedOperationException(fill.toString());
  }

  private static int getPatternColor (int color) {
    float[] hsb = RGBtoHSB(Color.red(color), Color.green(color), Color.blue(color));
    if (hsb[1] > 0.0f || (hsb[2] < 1.0f && hsb[2] > 0.0f)) {
      hsb[1] = Math.min(1.0f, hsb[1] + 0.05f + 0.1f * (1.0f - hsb[1]));
    }
    if (hsb[2] > 0.5f) {
      hsb[2] = Math.max(0.0f, hsb[2] * 0.65f);
    } else {
      hsb[2] = Math.max(0.0f, Math.min(1.0f, 1.0f - hsb[2] * 0.65f));
    }
    return HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0x66ffffff;
  }

  private static int getPatternSideColor (int color) {
    float[] hsb = RGBtoHSB(Color.red(color), Color.green(color), Color.blue(color));
    hsb[1] = Math.min(1.0f, hsb[1] + 0.05f);
    if (hsb[2] > 0.5f) {
      hsb[2] = Math.max(0.0f, hsb[2] * 0.90f);
    } else{
      hsb[2] = Math.max(0.0f, hsb[2] * 0.90f);
    }
    return HSBtoRGB(hsb[0], hsb[1], hsb[2]) | 0xff000000;
  }

  // Wallpaper to Background API conversion

  public static int[] getLegacyWallpaperIds () {
    return new int[] {ID_CATS_BLUE, ID_SOLID_BLUE, 106, 103, 105, ID_CITY, 109, 111, 110, ID_CITY_BW, ID_CATS_BEIGE, 15, 14, ID_POLYGONS, ID_PAINT, ID_CATS_ORANGE, 16, 12, ID_GALAXY, 7, ID_BLUE_CIRCLES, 4, 19, 1, 17, 6, ID_CATS_GREEN, ID_CATS_PINK};
  }

  public static int getLegacyWallpaperColor (int legacyWallpaperId) {
    switch (legacyWallpaperId) {
      case 19:
      case 109:
        return 0x403003;
      case ID_SOLID_BLUE:
        return COLOR_SOLID_BLUE;
      case ID_CATS_BLUE:
      case 103:
        return 0xc3259;
    }
    return 0;
  }

  public static int resolveLegacyWallpaperId (int solidColor) {
    return (solidColor & 0xffffff) == COLOR_SOLID_BLUE ? ID_SOLID_BLUE : 0;
  }

  private static Map<String, Integer> backgroundNameToLegacyWallpaperIdMap;

  public static int resolveLegacyWallpaperId (String name, TdApi.BackgroundType type) {
    if (type != null) {
      switch (type.getConstructor()) {
        case TdApi.BackgroundTypeFill.CONSTRUCTOR: {
          TdApi.BackgroundFill fill = ((TdApi.BackgroundTypeFill) type).fill;
          if (fill.getConstructor() == TdApi.BackgroundFillSolid.CONSTRUCTOR) {
            return resolveLegacyWallpaperId(((TdApi.BackgroundFillSolid) fill).color);
          }
          return 0;
        }
        case TdApi.BackgroundTypePattern.CONSTRUCTOR:
          return 0;
        case TdApi.BackgroundTypeWallpaper.CONSTRUCTOR:
          break;
      }
    }
    if (StringUtils.isEmpty(name)) {
      return 0;
    }
    if (backgroundNameToLegacyWallpaperIdMap == null) {
      int[] wallpaperIds = getLegacyWallpaperIds();
      backgroundNameToLegacyWallpaperIdMap = new HashMap<>(wallpaperIds.length);
      for (int wallpaperId : wallpaperIds) {
        String backgroundName = getBackgroundForLegacyWallpaperId(wallpaperId);
        if (backgroundName != null)
          backgroundNameToLegacyWallpaperIdMap.put(backgroundName, wallpaperId);
      }
    }
    Integer result = backgroundNameToLegacyWallpaperIdMap.get(name);
    return result != null ? result : 0;
  }

  public static int getLegacyOverlayColor (int legacyWallpaperId, int defaultColor) {
    switch (legacyWallpaperId) {
      case ID_SOLID_BLUE: // solid blue
      case ID_CATS_BLUE: // cats
        return Theme.getColorFast(R.id.theme_color_wp_cats);
      case ID_BLUE_CIRCLES:
        return Theme.getColorFast(R.id.theme_color_wp_circlesBlue);
      case ID_CATS_PINK: // pink cats
        return Theme.getColorFast(R.id.theme_color_wp_catsPink);
      case ID_CATS_GREEN: // green cats
        return Theme.getColorFast(R.id.theme_color_wp_catsGreen);
      case ID_CATS_ORANGE: // orange cats
        return Theme.getColorFast(R.id.theme_color_wp_catsOrange);
      case ID_CATS_BEIGE: // beige cats
        return Theme.getColorFast(R.id.theme_color_wp_catsBeige);
    }
    int legacyColor = getLegacyWallpaperColor(legacyWallpaperId);
    if ((legacyColor & 0xffffff) != 0)
      return ColorUtils.color((int) (255f * .2f), legacyColor);
    return defaultColor;
  }

  private static String colorName (int color) {
    return Strings.getHexColor(ColorUtils.color(255, color), false).substring(1).toLowerCase();
  }

  public static String getNameForColor (int color) {
    return colorName(color);
  }

  public static String getNameForColor (int topColor, int bottomColor, int rotationAngle) {
    return colorName(topColor) + "-" + colorName(bottomColor) + (rotationAngle != 0 ? "?rotation=" + rotationAngle : "");
  }

  public static String getNameForColor (int[] colors) {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < colors.length; i++) {
      builder.append(colorName(colors[i]));
      if (i != colors.length - 1) builder.append("~");
    }

    return builder.toString();
  }

  public static String getNameForFill (TdApi.BackgroundFill fill) {
    switch (fill.getConstructor()) {
      case TdApi.BackgroundFillSolid.CONSTRUCTOR:
        return getNameForColor(((TdApi.BackgroundFillSolid) fill).color);
      case TdApi.BackgroundFillGradient.CONSTRUCTOR: {
        TdApi.BackgroundFillGradient gradient = (TdApi.BackgroundFillGradient) fill;
        return getNameForColor(gradient.topColor, gradient.bottomColor, gradient.rotationAngle);
      }
      case TdApi.BackgroundFillFreeformGradient.CONSTRUCTOR: {
        TdApi.BackgroundFillFreeformGradient gradient = (TdApi.BackgroundFillFreeformGradient) fill;
        return getNameForColor(gradient.colors);
      }
    }
    throw new UnsupportedOperationException(fill.toString());
  }

  public static String getBackgroundForLegacyWallpaperId (int wallpaperId) {
    switch (wallpaperId) {
      // default ones, available in any app
      case ID_GRAY_FOREST:
        return "7F-AWfPJgVIBAAAA5NdzN7l5zWM";
      case 105:
        return "Qe9IiLLfiVIBAAAAn_BDUKSYaCs";
      case 13:
        return "vG0wx9kyiVIBAAAAkhlpL_sW9dg";
      case 21:
        return "SJTGO1MxgVIBAAAA-AChMYdDH58";
      case 2:
        return "YRZSyB-VgVIBAAAAsaNJPdNxxpM";

      // uploaded by myself
      case 106:
        return "8vCBxkOtEVEBAAAA5hiHfYHN_8A";
      case 103:
        return "VDfKfArxEFEBAAAAWkmjzkSYtK0";
      case 107:
        return "_18_b7s2GVEDAAAAxtY3yyRnLmk";
      case 1000001:
        return "Z86jxWuHGVECAAAA9XUiUlLRgY0";
      case 109:
        return "ZFubnSx4GFEBAAAAJcREqDYeZc8";
      case 111:
        return "DNHJ7mmeGVEBAAAAeSmU7YZuDQI";
      case 110:
        return "gAAMuM3xEFEDAAAAChFy8V6dHCE";
      case 112:
        return "m3N0O6nVGFEBAAAApdOEjJV8_WE";
      case 32:
        return "r9rsZJd4GVEBAAAAhO9TCoJvZuI";
      case 15:
        return "gvnMKHV4GVECAAAAUXErPvdsu_M";
      case 14:
        return "VWHGDTX6GVECAAAAY2jkcp5eC5g";
      case 5:
        return "axDtyTPwEVECAAAARm9eM8a3QLI";
      case 11:
        return "fUJ1tAoXEVEBAAAARV_-KCYufFw";
      case 33:
        return "4GxoHR-KEVEBAAAApJ2vw7X40ng";
      case 16:
        return "VDopCxj6EFEDAAAAsX0JZu28bgw";
      case 12:
        return "TFZYLbcDEVEBAAAAzrIWPPqFgRs";
      case 10:
        return "cfI-qxRrEVECAAAA_o1jhbsHa14";
      case 7:
        return "fm91uT9iEFEBAAAAY7IRPuCvJNs";
      case 114:
        return "d8H77nPOGFECAAAArdOApK8bYj4";
      case 4:
        return "6goyzlSsEVEDAAAAW-mw5A6C42Q";
      case 19:
        return "ENXuz6t_EFEEAAAASyyprFX01MI";
      case 1:
        return "zLuqruxGEVEBAAAAHmhS93uFDlI";
      case 17:
        return "RB5LhCkREFECAAAA5KTABa4Zrmc";
      case 6:
        return "051BDerTGFECAAAAvsFaINUzGrE";
      case 35:
        return "RoIieAeGGFEBAAAATN-bGmJbmIo";
      case 36:
        return "7_Fl55MMGFECAAAAx_nwn_5oOZ8";
    }
    return null;
  }
}
