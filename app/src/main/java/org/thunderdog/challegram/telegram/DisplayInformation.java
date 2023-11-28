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
 * File created on 08/07/2023
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.util.Blob;
import me.vkryl.leveldb.LevelDB;
import me.vkryl.td.Td;

public class DisplayInformation {
  private static final long FLAG_PREMIUM = 1;
  private static final long FLAG_VERIFIED = 1 << 1;

  private static long buildFlags (@NonNull TdApi.User user) {
    long flags = 0;
    if (user.isPremium) {
      flags |= FLAG_PREMIUM;
    }
    if (user.isVerified) {
      flags |= FLAG_VERIFIED;
    }
    return flags;
  }

  public final String prefix;

  private long userId;
  private long flags;
  private String firstName;
  private String lastName;
  private TdApi.Usernames usernames;
  private String phoneNumber;
  private String profilePhotoSmallPath, profilePhotoBigPath;
  private EmojiStatusCache emojiStatusCache;
  private int accentColorId;
  private TdApi.AccentColor accentColor;

  DisplayInformation (String prefix) {
    this.prefix = prefix;
  }

  DisplayInformation (String prefix, TdApi.User user, @Nullable TdApi.AccentColor accentColor, @Nullable TdApi.Sticker emojiStatusFile, boolean isUpdate) {
    this.prefix = prefix;
    this.flags = buildFlags(user);
    this.userId = user.id;
    this.firstName = user.firstName;
    this.lastName = user.lastName;
    this.usernames = user.usernames;
    this.phoneNumber = user.phoneNumber;
    this.accentColorId = user.accentColorId;
    this.accentColor = accentColor;
    if (user.profilePhoto != null) {
      this.profilePhotoSmallPath = TD.isFileLoaded(user.profilePhoto.small) ?
        user.profilePhoto.small.local.path :
        isUpdate ? getUserProfilePhotoPath(prefix, false) :
        null;
      this.profilePhotoBigPath = TD.isFileLoaded(user.profilePhoto.big) ?
        user.profilePhoto.big.local.path :
        isUpdate ? getUserProfilePhotoPath(prefix, true) :
        null;
    } else {
      this.profilePhotoSmallPath = this.profilePhotoBigPath = null;
    }
    TdApi.EmojiStatus status = user.emojiStatus;
    if (status != null) {
      this.emojiStatusCache = EmojiStatusCache.restore(prefix, user, emojiStatusFile, isUpdate);
    } else {
      this.emojiStatusCache = null;
    }

    saveAll();
  }

  public boolean isPremium () {
    return BitwiseUtils.hasFlag(flags, FLAG_PREMIUM);
  }

  public boolean isVerified () {
    return BitwiseUtils.hasFlag(flags, FLAG_VERIFIED);
  }

  public long getUserId () {
    return userId;
  }

  public String getFirstName () {
    return firstName;
  }

  public String getLastName () {
    return lastName;
  }

  public int getAccentColorId () {
    return accentColorId;
  }

  public TdlibAccentColor getAccentColor () {
    if (accentColorId < TdlibAccentColor.BUILT_IN_COLOR_COUNT) {
      return new TdlibAccentColor(accentColorId);
    }
    if (accentColor != null) {
      return new TdlibAccentColor(accentColor);
    }
    return new TdlibAccentColor(TdlibAccentColor.BuiltInId.RED);
  }

  @Nullable
  public TdApi.Usernames getUsernames () {
    return usernames;
  }

  @Nullable
  public String getUsername () {
    return Td.primaryUsername(usernames);
  }

  public String getPhoneNumber () {
    return phoneNumber;
  }

  public String getProfilePhotoPath (boolean big) {
    return big ? profilePhotoBigPath : profilePhotoSmallPath;
  }

  void storeUserProfilePhotoPath (boolean big, String absolutePath) {
    if (big) {
      profilePhotoBigPath = absolutePath;
    } else {
      profilePhotoSmallPath = absolutePath;
    }
    storeUserProfilePhotoPath(prefix, big, absolutePath);
  }

  static void storeUserProfilePhotoPath (String prefix, boolean big, String absolutePath) {
    String key;
    if (big) {
      key = prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL;
    } else {
      key = prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO;
    }
    if (StringUtils.isEmpty(absolutePath)) {
      Settings.instance().remove(key);
    } else {
      Settings.instance().putString(key, toRelativePath(absolutePath));
    }
  }

  private static String getUserProfilePhotoPath (String prefix, boolean big) {
    String key;
    if (big) {
      key = prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL;
    } else {
      key = prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO;
    }
    return toAbsolutePath(Settings.instance().getString(key, null));
  }

  public long getEmojiStatusCustomEmojiId () {
    return emojiStatusCache != null ? emojiStatusCache.emojiStatusId : 0;
  }

  public TdApi.Sticker getEmojiStatusSticker () {
    return emojiStatusCache != null ? emojiStatusCache.sticker : null;
  }

  void storeEmojiStatusMetadata (long customEmojiId, @Nullable TdApi.Sticker sticker) {
    // Called when metadata about emoji status was loaded
    LevelDB pmc = Settings.instance().pmc();
    if (sticker != null) {
      this.emojiStatusCache = new EmojiStatusCache(customEmojiId, sticker, false);
      this.emojiStatusCache.saveAll(pmc, prefix);
    } else {
      this.emojiStatusCache = null;
      EmojiStatusCache.removeAll(pmc, prefix);
    }
  }

  static void storeEmojiStatusMetadata (String prefix, long customEmojiId, @Nullable TdApi.Sticker sticker) {
    LevelDB pmc = Settings.instance().pmc();
    if (sticker != null) {
      EmojiStatusCache cache = new EmojiStatusCache(customEmojiId, sticker, false);
      cache.saveAll(pmc, prefix);
    } else {
      EmojiStatusCache.removeAll(pmc, prefix);
    }
  }

  void storeEmojiStatusPath (long customEmojiId, @NonNull TdApi.Sticker sticker, boolean isThumbnail, String filePath) {
    // Called when emoji status file was loaded, it means only downloaded file path must be refreshed
    if (emojiStatusCache != null) {
      if (emojiStatusCache.emojiStatusId != customEmojiId) {
        return;
      }
      if (isThumbnail) {
        emojiStatusCache.sticker.thumbnail = sticker.thumbnail;
      } else {
        emojiStatusCache.sticker.sticker = sticker.sticker;
      }
    }
    storeEmojiStatusPath(prefix, customEmojiId, sticker, isThumbnail, filePath);
  }

  static void storeEmojiStatusPath (String prefix, long customEmojiId, @NonNull TdApi.Sticker sticker, boolean isThumbnail, String filePath)  {
    if (isThumbnail) {
      EmojiStatusCache.saveThumbnail(Settings.instance().pmc(), prefix, customEmojiId, sticker.thumbnail);
    } else {
      EmojiStatusCache.saveStickerFile(Settings.instance().pmc(), prefix, customEmojiId, sticker.sticker);
    }
  }

  private void saveAll () {
    LevelDB editor = Settings.instance().edit();
    editor.putLong(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_ID, userId);
    editor.putLong(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_FLAGS, flags);
    editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME1, firstName);
    editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME2, lastName);
    editor.putInt(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_ACCENT_COLOR_ID, accentColorId);
    if (accentColor != null) {
      if (accentColor.id != accentColorId)
        throw new IllegalStateException(accentColor.id + " != " + accentColorId);
      editor.putInt(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_ACCENT_BUILT_IN_ACCENT_COLOR_ID, accentColor.builtInAccentColorId);
      editor.putIntArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_LIGHT_THEME_COLORS, accentColor.lightThemeColors);
      editor.putIntArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_DARK_THEME_COLORS, accentColor.darkThemeColors);
    } else {
      editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_ACCENT_BUILT_IN_ACCENT_COLOR_ID);
      editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_LIGHT_THEME_COLORS);
      editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_DARK_THEME_COLORS);
    }
    if (usernames != null) {
      editor
        .putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAME, usernames.editableUsername);
    } else {
      editor
        .remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAME);
    }
    if (usernames != null && usernames.activeUsernames != null && usernames.activeUsernames.length > 0) {
      editor.putStringArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_ACTIVE, usernames.activeUsernames);
    } else {
      editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_ACTIVE);
    }
    if (usernames != null && usernames.disabledUsernames != null && usernames.disabledUsernames.length > 0) {
      editor.putStringArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_DISABLED, usernames.disabledUsernames);
    } else {
      editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_USERNAMES_DISABLED);
    }
    editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHONE, phoneNumber);
    if (!StringUtils.isEmpty(profilePhotoSmallPath)) {
      editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO, toRelativePath(profilePhotoSmallPath));
    } else {
      editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO);
    }
    if (!StringUtils.isEmpty(profilePhotoBigPath)) {
      editor.putString(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL, toRelativePath(profilePhotoBigPath));
    } else {
      editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL);
    }
    if (emojiStatusCache != null) {
      emojiStatusCache.saveAll(editor, prefix);
    } else {
      EmojiStatusCache.removeAll(editor, prefix);
    }
    editor.apply();
  }

  static DisplayInformation fullRestore (String prefix, long expectedUserId) {
    DisplayInformation info = null;

    long emojiStatusId = 0;
    byte[] emojiStatusMetadata = null, emojiStatusFile = null, emojiStatusThumbnail = null;

    for (LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
      /*if (entry.key().length() == prefix.length()) {
        long userId = entry.asLong();
        if (userId != expectedUserId)
          return null;
        info = new DisplayInformation(prefix);
      }*/
      if (info == null)
        info = new DisplayInformation(prefix);
      final String suffix = entry.key().substring(prefix.length());
      switch (suffix) {
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_ID:
          info.userId = entry.asLong();
          if (info.userId != expectedUserId)
            return null;
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_FLAGS:
          info.flags = entry.asLong();
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME1:
          info.firstName = entry.asString();
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_NAME2:
          info.lastName = entry.asString();
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_PHONE:
          info.phoneNumber = entry.asString();
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO:
          info.profilePhotoSmallPath = toAbsolutePath(entry.asString());
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_PHOTO_FULL:
          info.profilePhotoBigPath = toAbsolutePath(entry.asString());
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_ACCENT_COLOR_ID:
          info.accentColorId = entry.asInt();
          if (info.accentColor != null) {
            info.accentColor.id = info.accentColorId;
          }
          break;
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_ACCENT_BUILT_IN_ACCENT_COLOR_ID:
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_LIGHT_THEME_COLORS:
        case Settings.KEY_ACCOUNT_INFO_SUFFIX_DARK_THEME_COLORS:
          if (info.accentColor == null) {
            info.accentColor = new TdApi.AccentColor(info.accentColorId, 0, null, null);
          }
          switch (suffix) {
            case Settings.KEY_ACCOUNT_INFO_SUFFIX_ACCENT_BUILT_IN_ACCENT_COLOR_ID:
              info.accentColor.builtInAccentColorId = entry.asInt();
              break;
            case Settings.KEY_ACCOUNT_INFO_SUFFIX_LIGHT_THEME_COLORS:
              info.accentColor.lightThemeColors = entry.asIntArray();
              break;
            case Settings.KEY_ACCOUNT_INFO_SUFFIX_DARK_THEME_COLORS:
              info.accentColor.darkThemeColors = entry.asIntArray();
              break;
          }
          break;
        default:
          if (suffix.startsWith(Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX)) {
            final String subKey = suffix.substring(Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX.length());
            try {
              switch (subKey) {
                case Settings.KEY_EMOJI_STATUS_SUFFIX_ID:
                  emojiStatusId = entry.asLong();
                  break;
                case Settings.KEY_EMOJI_STATUS_SUFFIX_METADATA:
                  emojiStatusMetadata = entry.asByteArray();
                  break;
                case Settings.KEY_EMOJI_STATUS_SUFFIX_STICKER:
                  emojiStatusFile = entry.asByteArray();
                  break;
                case Settings.KEY_EMOJI_STATUS_SUFFIX_THUMBNAIL:
                  emojiStatusThumbnail = entry.asByteArray();
                  break;
              }
            } catch (Throwable ignored) { }
          }
          break;
      }
    }
    if (emojiStatusId != 0 && emojiStatusMetadata != null) {
      info.emojiStatusCache = EmojiStatusCache.deserialize(emojiStatusId, emojiStatusMetadata, emojiStatusFile, emojiStatusThumbnail);
    }
    return info != null && info.userId == expectedUserId ? info : null;
  }

  private static final String EXTERNAL_PREFIX = "external://";
  private static final String INTERNAL_PREFIX = "internal://";

  private static String toAbsolutePath (String relativePath) {
    if (!StringUtils.isEmpty(relativePath) && !relativePath.startsWith("/")) {
      if (relativePath.startsWith(EXTERNAL_PREFIX)) {
        String externalRelativePath = relativePath.substring(EXTERNAL_PREFIX.length());
        File parentDir = UI.getAppContext().getExternalFilesDir(null);
        if (parentDir == null) {
          // Assuming that external storage path was the same as internal one
          parentDir = UI.getAppContext().getFilesDir();
        }
        if (parentDir != null) {
          File file = new File(parentDir, externalRelativePath);
          try {
            return file.getCanonicalPath();
          } catch (IOException | SecurityException ignored) {
            return file.getAbsolutePath();
          }
        }
      }
      if (relativePath.startsWith(INTERNAL_PREFIX)) {
        String internalRelativePath = relativePath.substring(INTERNAL_PREFIX.length());
        File parentDir = UI.getAppContext().getFilesDir();
        if (parentDir != null) {
          File file = new File(parentDir, internalRelativePath);
          try {
            return file.getCanonicalPath();
          } catch (IOException | SecurityException ignored) {
            return file.getAbsolutePath();
          }
        }
      }
    }
    return relativePath;
  }

  private static String toRelativePath (String absoluteFilePath) {
    if (!StringUtils.isEmpty(absoluteFilePath) && absoluteFilePath.startsWith("/") && !absoluteFilePath.contains("://")) {
      File externalDir = UI.getAppContext().getExternalFilesDir(null);
      if (externalDir != null) {
        final String prefix = externalDir.getAbsolutePath() + "/";
        if (absoluteFilePath.startsWith(prefix)) {
          String externalRelativePath = absoluteFilePath.substring(prefix.length());
          return EXTERNAL_PREFIX + externalRelativePath;
        }
      }
      File internalDir = UI.getAppContext().getFilesDir();
      if (internalDir != null) {
        final String prefix = internalDir.getAbsolutePath() + "/";
        if (absoluteFilePath.startsWith(prefix)) {
          String internalRelativePath = absoluteFilePath.substring(prefix.length());
          return INTERNAL_PREFIX + internalRelativePath;
        }
      }
      if (externalDir != null) {
        try {
          final String prefix = externalDir.getCanonicalPath() + "/";
          if (absoluteFilePath.startsWith(prefix)) {
            String externalRelativePath = absoluteFilePath.substring(prefix.length());
            return EXTERNAL_PREFIX + externalRelativePath;
          }
        } catch (IOException | SecurityException ignored) { }
      }
      if (internalDir != null) {
        try {
          final String prefix = internalDir.getCanonicalPath() + "/";
          if (absoluteFilePath.startsWith(prefix)) {
            String internalRelativePath = absoluteFilePath.substring(prefix.length());
            return INTERNAL_PREFIX + internalRelativePath;
          }
        } catch (IOException | SecurityException ignored) { }
      }
    }
    return absoluteFilePath;
  }

  private static class EmojiStatusCache {
    public final long emojiStatusId;
    public final TdApi.Sticker sticker;

    public static EmojiStatusCache restore (String prefix, @NonNull TdApi.User user, @Nullable TdApi.Sticker remoteEmojiStatus, boolean isUpdate) {
      TdApi.EmojiStatus emojiStatus = user.emojiStatus;
      long remoteEmojiStatusId = remoteEmojiStatus != null ? remoteEmojiStatus.id : emojiStatus != null ? emojiStatus.customEmojiId : 0;
      if (remoteEmojiStatusId == 0) {
        // Drop emoji status cache, as user doesn't have custom status anymore
        return null;
      }
      if (remoteEmojiStatus != null && TD.isFileLoaded(remoteEmojiStatus.sticker)) {
        // Use remote status, if main file is loaded
        return new EmojiStatusCache(remoteEmojiStatusId, remoteEmojiStatus, false);
      }

      LevelDB pmc = Settings.instance().pmc();
      long cachedEmojiStatusId = pmc.getLong(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX +
        Settings.KEY_EMOJI_STATUS_SUFFIX_ID,
        0
      );
      if (cachedEmojiStatusId != remoteEmojiStatusId) {
        // Cached emoji status doesn't match the remote one, dropping cache
        return null;
      }

      TdApi.Sticker cachedSticker = restoreAll(cachedEmojiStatusId, prefix);
      if (cachedSticker != null) {
        return new EmojiStatusCache(cachedEmojiStatusId, cachedSticker, true);
      }

      return null;
    }

    public EmojiStatusCache (long emojiStatusId, TdApi.Sticker loadedSticker, boolean isCached) {
      this.emojiStatusId = emojiStatusId;
      this.sticker = isCached ? loadedSticker : new TdApi.Sticker(
        loadedSticker.id,
        loadedSticker.setId,
        loadedSticker.width, loadedSticker.height,
        loadedSticker.emoji,
        loadedSticker.format,
        loadedSticker.fullType,
        loadedSticker.outline,
        loadedSticker.thumbnail != null && TD.isFileLoaded(loadedSticker.thumbnail.file) ? loadedSticker.thumbnail : null,
        TD.isFileLoaded(loadedSticker.sticker) ? loadedSticker.sticker : null
      );
    }

    private static final int CACHE_VERSION = 1;

    // Cache: sticker.format

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      StickerFormat.WEBP, StickerFormat.TGS, StickerFormat.WEBM
    })
    private @interface StickerFormat {
      int WEBP = 0, TGS = 1, WEBM = 2;
    }

    private static int sizeOfFormat (TdApi.StickerFormat format) {
      return 1;
    }

    private static void writeFormat (Blob blob, TdApi.StickerFormat format) {
      switch (format.getConstructor()) {
        case TdApi.StickerFormatWebp.CONSTRUCTOR:
          blob.writeByte((byte) StickerFormat.WEBP);
          break;
        case TdApi.StickerFormatTgs.CONSTRUCTOR:
          blob.writeByte((byte) StickerFormat.TGS);
          break;
        case TdApi.StickerFormatWebm.CONSTRUCTOR:
          blob.writeByte((byte) StickerFormat.WEBM);
          break;
        default:
          throw new UnsupportedOperationException(format.toString());
      }
    }

    private static TdApi.StickerFormat deserializeFormat (Blob blob) {
      @StickerFormat int type = blob.readByte();
      switch (type) {
        case StickerFormat.WEBP:
          return new TdApi.StickerFormatWebp();
        case StickerFormat.TGS:
          return new TdApi.StickerFormatTgs();
        case StickerFormat.WEBM:
          return new TdApi.StickerFormatWebm();
      }
      return null;
    }

    // Cache: sticker.fullType

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      StickerFullType.REGULAR, StickerFullType.MASK, StickerFullType.CUSTOM_EMOJI
    })
    private @interface StickerFullType {
      int REGULAR = 0, MASK = 1, CUSTOM_EMOJI = 2;
    }

    private static int sizeOfFullType (TdApi.StickerFullType fullType) {
      switch (fullType.getConstructor()) {
        case TdApi.StickerFullTypeRegular.CONSTRUCTOR:
        case TdApi.StickerFullTypeMask.CONSTRUCTOR:
          break;
        case TdApi.StickerFullTypeCustomEmoji.CONSTRUCTOR: {
          return 1 /*type*/ + 8 /*customEmojiId*/ + 1 /*needRepainting*/;
        }
      }
      return 1 /*type*/;
    }

    private static void writeFullType (Blob blob, TdApi.StickerFullType fullType) {
      switch (fullType.getConstructor()) {
        case TdApi.StickerFullTypeRegular.CONSTRUCTOR:
          blob.writeByte((byte) StickerFullType.REGULAR);
          break;
        case TdApi.StickerFullTypeMask.CONSTRUCTOR:
          blob.writeByte((byte) StickerFullType.MASK);
          break;
        case TdApi.StickerFullTypeCustomEmoji.CONSTRUCTOR: {
          TdApi.StickerFullTypeCustomEmoji customEmoji = (TdApi.StickerFullTypeCustomEmoji) fullType;
          blob.writeByte((byte) StickerFullType.CUSTOM_EMOJI);
          blob.writeLong(customEmoji.customEmojiId);
          blob.writeBoolean(customEmoji.needsRepainting);
          break;
        }
        default:
          throw new UnsupportedOperationException(fullType.toString());
      }
    }

    private static TdApi.StickerFullType deserializeFullType (Blob blob) {
      @StickerFullType int type = blob.readByte();
      switch (type) {
        case StickerFullType.REGULAR:
        case StickerFullType.MASK:
          // intentionally unsupported, as it's not possible to set them as an emoji status
          return null;
        case StickerFullType.CUSTOM_EMOJI:
          return new TdApi.StickerFullTypeCustomEmoji(blob.readLong(), blob.readBoolean());
      }
      return null;
    }

    // Cache: sticker.outline

    private static int sizeOfOutline (TdApi.ClosedVectorPath[] outline) {
      int size = 4 /*outline.length*/;
      for (TdApi.ClosedVectorPath path : outline) {
        size += 4 /*path.commands.length*/;
        for (TdApi.VectorPathCommand command : path.commands) {
          switch (command.getConstructor()) {
            case TdApi.VectorPathCommandLine.CONSTRUCTOR:
              size += 1 + 8 + 8;
              break;
            case TdApi.VectorPathCommandCubicBezierCurve.CONSTRUCTOR:
              size += 1 + (8 + 8) + (8 + 8) + (8 + 8);
              break;
            default:
              throw new UnsupportedOperationException(command.toString());
          }
        }
      }
      return size;
    }

    private static void writeOutline (Blob blob, TdApi.ClosedVectorPath[] outline) {
      blob.writeInt(outline.length);
      for (TdApi.ClosedVectorPath path : outline) {
        blob.writeInt(path.commands.length);
        for (TdApi.VectorPathCommand command : path.commands) {
          switch (command.getConstructor()) {
            case TdApi.VectorPathCommandLine.CONSTRUCTOR: {
              TdApi.VectorPathCommandLine line = (TdApi.VectorPathCommandLine) command;
              blob.writeByte((byte) VectorPathCommandType.LINE);
              blob.writeDouble(line.endPoint.x);
              blob.writeDouble(line.endPoint.y);
              break;
            }
            case TdApi.VectorPathCommandCubicBezierCurve.CONSTRUCTOR: {
              TdApi.VectorPathCommandCubicBezierCurve cubicBezierCurve = (TdApi.VectorPathCommandCubicBezierCurve) command;
              blob.writeByte((byte) VectorPathCommandType.CUBIC_BEZIER_CURVE);
              blob.writeDouble(cubicBezierCurve.startControlPoint.x);
              blob.writeDouble(cubicBezierCurve.startControlPoint.y);
              blob.writeDouble(cubicBezierCurve.endControlPoint.x);
              blob.writeDouble(cubicBezierCurve.endControlPoint.y);
              blob.writeDouble(cubicBezierCurve.endPoint.x);
              blob.writeDouble(cubicBezierCurve.endPoint.y);
              break;
            }
            default:
              throw new UnsupportedOperationException(command.toString());
          }
        }
      }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      VectorPathCommandType.LINE, VectorPathCommandType.CUBIC_BEZIER_CURVE
    })
    private @interface VectorPathCommandType {
      int LINE = 0, CUBIC_BEZIER_CURVE = 1;
    }

    private static TdApi.VectorPathCommand restoreVectorPathCommand (Blob blob) {
      @VectorPathCommandType int type = blob.readByte();
      switch (type) {
        case VectorPathCommandType.LINE: {
          return new TdApi.VectorPathCommandLine(
            new TdApi.Point(blob.readDouble(), blob.readDouble())
          );
        }
        case VectorPathCommandType.CUBIC_BEZIER_CURVE: {
          return new TdApi.VectorPathCommandCubicBezierCurve(
            new TdApi.Point(blob.readDouble(), blob.readDouble()),
            new TdApi.Point(blob.readDouble(), blob.readDouble()),
            new TdApi.Point(blob.readDouble(), blob.readDouble())
          );
        }
      }
      return null;
    }

    private static TdApi.ClosedVectorPath restoreVectorPath (Blob blob) {
      int size = blob.readInt();
      if (size <= 0) {
        return null;
      }
      TdApi.VectorPathCommand[] commands = new TdApi.VectorPathCommand[size];
      for (int i = 0; i < size; i++) {
        TdApi.VectorPathCommand command = restoreVectorPathCommand(blob);
        if (command == null) {
          return null;
        }
        commands[i] = command;
      }
      return new TdApi.ClosedVectorPath(commands);
    }

    private static TdApi.ClosedVectorPath[] deserializeOutline (Blob blob) {
      int size = blob.readInt();
      if (size <= 0) {
        return new TdApi.ClosedVectorPath[0];
      }
      TdApi.ClosedVectorPath[] result = new TdApi.ClosedVectorPath[size];
      for (int i = 0; i < size; i++) {
        TdApi.ClosedVectorPath path = restoreVectorPath(blob);
        if (path == null) {
          return null;
        }
        result[i] = path;
      }
      return result;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
      ThumbnailFormat.JPEG,
      ThumbnailFormat.GIF,
      ThumbnailFormat.MPEG4,
      ThumbnailFormat.PNG,
      ThumbnailFormat.TGS,
      ThumbnailFormat.WEBM,
      ThumbnailFormat.WEBP
    })
    private @interface ThumbnailFormat {
      int JPEG = 0, GIF = 1, MPEG4 = 2, PNG = 3, TGS = 4, WEBM = 5, WEBP = 6;
    }

    private static int sizeOfThumbnailFormat (TdApi.ThumbnailFormat format) {
      return 1;
    }

    private static void writeThumbnailFormat (Blob blob, TdApi.ThumbnailFormat format) {
      switch (format.getConstructor()) {
        case TdApi.ThumbnailFormatJpeg.CONSTRUCTOR:
          blob.writeByte((byte) ThumbnailFormat.JPEG);
          break;
        case TdApi.ThumbnailFormatGif.CONSTRUCTOR:
          blob.writeByte((byte) ThumbnailFormat.GIF);
          break;
        case TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR:
          blob.writeByte((byte) ThumbnailFormat.MPEG4);
          break;
        case TdApi.ThumbnailFormatPng.CONSTRUCTOR:
          blob.writeByte((byte) ThumbnailFormat.PNG);
          break;
        case TdApi.ThumbnailFormatTgs.CONSTRUCTOR:
          blob.writeByte((byte) ThumbnailFormat.TGS);
          break;
        case TdApi.ThumbnailFormatWebm.CONSTRUCTOR:
          blob.writeByte((byte) ThumbnailFormat.WEBM);
          break;
        case TdApi.ThumbnailFormatWebp.CONSTRUCTOR:
          blob.writeByte((byte) ThumbnailFormat.WEBP);
          break;
        default:
          throw new UnsupportedOperationException(format.toString());
      }
    }

    private static TdApi.ThumbnailFormat restoreThumbnailFormat (Blob blob) {
      @ThumbnailFormat int type = blob.readByte();
      switch (type) {
        case ThumbnailFormat.JPEG:
          return new TdApi.ThumbnailFormatJpeg();
        case ThumbnailFormat.GIF:
          return new TdApi.ThumbnailFormatGif();
        case ThumbnailFormat.MPEG4:
          return new TdApi.ThumbnailFormatMpeg4();
        case ThumbnailFormat.PNG:
          return new TdApi.ThumbnailFormatPng();
        case ThumbnailFormat.TGS:
          return new TdApi.ThumbnailFormatTgs();
        case ThumbnailFormat.WEBM:
          return new TdApi.ThumbnailFormatWebm();
        case ThumbnailFormat.WEBP:
          return new TdApi.ThumbnailFormatWebp();
      }
      return null;
    }

    private static TdApi.Thumbnail deserializeThumbnail (long customEmojiId, byte[] data) {
      if (data == null || data.length == 0) {
        return null;
      }
      Blob blob = new Blob(data);
      if (blob.readLong() != customEmojiId) {
        return null;
      }
      TdApi.ThumbnailFormat format = restoreThumbnailFormat(blob);
      if (format == null) {
        return null;
      }
      int width = blob.readInt();
      int height = blob.readInt();
      String absolutePath = toAbsolutePath(blob.readString());
      return new TdApi.Thumbnail(
        format,
        width, height,
        ImageFileLocal.newFakeLocalFile(absolutePath, false)
      );
    }

    private static TdApi.File deserializeStickerFile (long customEmojiId, byte[] data) {
      if (data == null || data.length == 0) {
        return null;
      }
      Blob blob = new Blob(data);
      if (blob.readLong() != customEmojiId) {
        return null;
      }
      String relativePath = blob.readString();
      return ImageFileLocal.newFakeLocalFile(toAbsolutePath(relativePath), false);
    }

    public static EmojiStatusCache deserialize (long customEmojiId, byte[] metadata, byte[] fileData, byte[] thumbnailData) {
      TdApi.Sticker sticker = deserializeEmojiStatusMetadata(customEmojiId, metadata);
      if (sticker == null) {
        return null;
      }
      TdApi.Thumbnail thumbnail = deserializeThumbnail(customEmojiId, thumbnailData);
      TdApi.File file = deserializeStickerFile(customEmojiId, fileData);
      sticker.thumbnail = thumbnail;
      sticker.sticker = file;
      return new EmojiStatusCache(customEmojiId, sticker, true);
    }

    private static TdApi.Sticker deserializeEmojiStatusMetadata (long customEmojiId, byte[] metadata) {
      Blob blob = new Blob(metadata);
      int version = blob.readByte(); // 1: version
      if (version != CACHE_VERSION) {
        return null;
      }
      long id = blob.readLong(); // 2: id:long
      long setId = blob.readLong(); // 3: setId:long
      int width = blob.readInt(); // 4: width:int
      int height = blob.readInt(); // 5: height:int
      String emoji = blob.readString(); // 6: emoji:string
      TdApi.StickerFormat format = deserializeFormat(blob); // 7: format:StickerFormat
      if (format == null) {
        return null;
      }
      TdApi.StickerFullType fullType = deserializeFullType(blob); // 8: fullType:
      if (fullType == null) {
        return null;
      }
      if (fullType.getConstructor() != TdApi.StickerFullTypeCustomEmoji.CONSTRUCTOR || ((TdApi.StickerFullTypeCustomEmoji) fullType).customEmojiId != customEmojiId) {
        // Custom emoji mismatch
        return null;
      }
      TdApi.ClosedVectorPath[] outline = deserializeOutline(blob);
      return new TdApi.Sticker(
        id,
        setId,
        width, height,
        emoji,
        format,
        fullType,
        outline,
        // stored separately
        null,
        null
      );
    }

    private static TdApi.Sticker restoreAll (long customEmojiId, String prefix) {
      try {
        prefix = prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX;

        LevelDB pmc = Settings.instance().pmc();
        byte[] metadata = pmc.getByteArray(prefix + Settings.KEY_EMOJI_STATUS_SUFFIX_METADATA);
        TdApi.Sticker emojiStatus = deserializeEmojiStatusMetadata(customEmojiId, metadata);
        if (emojiStatus == null) {
          return null;
        }
        byte[] thumbnailData = pmc.getByteArray(prefix + Settings.KEY_EMOJI_STATUS_SUFFIX_THUMBNAIL);
        TdApi.Thumbnail thumbnail = thumbnailData != null ? deserializeThumbnail(customEmojiId, thumbnailData) : null;
        byte[] stickerData = pmc.getByteArray(prefix + Settings.KEY_EMOJI_STATUS_SUFFIX_STICKER);
        TdApi.File sticker = stickerData != null ? deserializeStickerFile(customEmojiId, stickerData) : null;
        emojiStatus.thumbnail = thumbnail;
        emojiStatus.sticker = sticker;
        return emojiStatus;
      } catch (Throwable ignored) { }
      return null;
    }

    private static byte[] serializeStickerMetadata (TdApi.Sticker sticker) {
      Blob metadata = new Blob(
        1 /*version*/ +
          8 /*id*/ +
          8 /*setId*/ +
          4 /*width*/ + 4 /*height*/ +
          Blob.sizeOf(sticker.emoji, true) +
          sizeOfFormat(sticker.format) /*format*/ +
          sizeOfFullType(sticker.fullType) /*fullType*/ +
          sizeOfOutline(sticker.outline) /*outline*/
      );
      metadata.writeByte((byte) CACHE_VERSION);
      metadata.writeLong(sticker.id);
      metadata.writeLong(sticker.setId);
      metadata.writeInt(sticker.width);
      metadata.writeInt(sticker.height);
      metadata.writeString(sticker.emoji);
      writeFormat(metadata, sticker.format);
      writeFullType(metadata, sticker.fullType);
      writeOutline(metadata, sticker.outline);
      return metadata.toByteArray();
    }

    private static byte[] serializeStickerFile (long customEmojiId, TdApi.File file) {
      if (file == null || !TD.isFileLoaded(file)) {
        return null;
      }
      String relativePath = toRelativePath(file.local.path);
      Blob blob = new Blob(
        8 /*customEmojiId*/ +
        Blob.sizeOf(relativePath, true)
      );
      blob.writeLong(customEmojiId);
      blob.writeString(relativePath);
      return blob.toByteArray();
    }

    private static byte[] serializeThumbnail (long customEmojiId, TdApi.Thumbnail thumbnail) {
      if (thumbnail == null || !TD.isFileLoaded(thumbnail.file)) {
        return null;
      }
      String relativePath = toRelativePath(thumbnail.file.local.path);
      Blob blob = new Blob(
        8 /*customEmojiId*/ +
        sizeOfThumbnailFormat(thumbnail.format) /*thumbnail.format*/ +
        4 /*thumbnail.width*/ +
        4 /*thumbnail.height*/ +
        Blob.sizeOf(relativePath, true)
      );
      blob.writeLong(customEmojiId);
      writeThumbnailFormat(blob, thumbnail.format);
      blob.writeInt(thumbnail.width);
      blob.writeInt(thumbnail.height);
      blob.writeString(relativePath);
      return blob.toByteArray();
    }

    public static void saveStickerFile (LevelDB editor, String prefix, long customEmojiId, TdApi.File sticker) {
      byte[] fileData = serializeStickerFile(customEmojiId, sticker);
      if (fileData != null) {
        editor.putByteArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX +
            Settings.KEY_EMOJI_STATUS_SUFFIX_STICKER,
          fileData
        );
      } else {
        editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX +
          Settings.KEY_EMOJI_STATUS_SUFFIX_STICKER
        );
      }
    }

    public static void saveThumbnail (LevelDB editor, String prefix, long customEmojiId, TdApi.Thumbnail thumbnail) {
      byte[] thumbnailData = serializeThumbnail(customEmojiId, thumbnail);
      if (thumbnailData != null) {
        editor.putByteArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX +
            Settings.KEY_EMOJI_STATUS_SUFFIX_THUMBNAIL,
          thumbnailData
        );
      } else {
        editor.remove(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX +
          Settings.KEY_EMOJI_STATUS_SUFFIX_THUMBNAIL
        );
      }
    }

    public void saveAll (LevelDB editor, String prefix) {
      editor.putLong(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX +
        Settings.KEY_EMOJI_STATUS_SUFFIX_ID,
        emojiStatusId
      );

      byte[] metadata = serializeStickerMetadata(sticker);
      editor.putByteArray(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX +
          Settings.KEY_EMOJI_STATUS_SUFFIX_METADATA,
        metadata
      );

      saveStickerFile(editor, prefix, emojiStatusId, sticker.sticker);
      saveThumbnail(editor, prefix, emojiStatusId, sticker.thumbnail);
    }

    public static void removeAll (LevelDB editor, String prefix) {
      editor.removeByPrefix(prefix + Settings.KEY_ACCOUNT_INFO_SUFFIX_EMOJI_STATUS_PREFIX);
    }
  }
}
