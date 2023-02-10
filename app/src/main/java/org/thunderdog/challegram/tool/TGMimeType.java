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
 */
package org.thunderdog.challegram.tool;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;

import me.vkryl.core.StringUtils;
import me.vkryl.td.TdConstants;

@SuppressWarnings("SpellCheckingInspection")
public class TGMimeType {
   public static @Nullable String mimeTypeForExtension (@Nullable String extension) {
     if (StringUtils.isEmpty(extension)) {
       return null;
     }
     if (BuildConfig.THEME_FILE_EXTENSION.equals(extension))
       return "text/plain";
     TdApi.Object object = Client.execute(new TdApi.GetFileMimeType("file." + extension));
     if (object != null && object.getConstructor() == TdApi.Text.CONSTRUCTOR) {
       TdApi.Text text = (TdApi.Text) object;
       if (!StringUtils.isEmpty(text.text)) {
         return text.text;
       }
     }
     if ("heic".equals(extension)) {
       return "image/heic";
     }
     if ("tgs".equals(extension)) {
       return TdConstants.ANIMATED_STICKER_MIME_TYPE;
     }

     // credits: https://github.com/angryziber/gnome-raw-thumbnailer/blob/master/data/raw-thumbnailer.xml
     switch (extension) {
       case "dng": return "image/x-adobe-dng";
       case "arw": return "image/x-sony-arw";
       case "cr2": return "image/x-canon-cr2";
       case "crw": return "image/x-canon-crw";
       case "dcr": return "image/x-kodak-dcr";
       case "erf": return "image/x-epson-erf";
       case "k25": return "image/x-kodak-k25";
       case "kdc": return "image/x-kodak-kdc";
       case "mrw": return "image/x-minolta-mrw";
       case "nef": return "image/x-nikon-nef";
       case "orf": return "image/x-olympus-orf";
       case "pef": return "image/x-pentax-pef";
       case "raf": return "image/x-fuji-raf";
       case "raw": return "image/x-panasonic-raw";
       case "sr2": return "image/x-sony-sr2";
       case "srf": return "image/x-sony-srf";
       case "x3f": return "image/x-sigma-x3f";
     }

     return null;
  }
  public static @Nullable String extensionForMimeType (@Nullable String mimeType) {
    if (StringUtils.isEmpty(mimeType)) {
      return null;
    }
    TdApi.Object object = Client.execute(new TdApi.GetFileExtension(mimeType));
    if (object != null && object.getConstructor() == TdApi.Text.CONSTRUCTOR) {
      TdApi.Text text = (TdApi.Text) object;
      if (!StringUtils.isEmpty(text.text)) {
        return text.text;
      }
    }
    if ("image/heic".equals(mimeType)) {
      return "heic";
    }
    if (TdConstants.ANIMATED_STICKER_MIME_TYPE.equals(mimeType)) {
      return "tgs";
    }
    return null;
  }

  public static boolean isPlainTextExtension (@Nullable String extension) {
    if (StringUtils.isEmpty(extension)) {
      return false;
    }
    switch (extension) {
      case "s": case "par": case "c":
      case "css": case "csv": case "curl":
      case "dcurl": case "mcurl": case "scurl":
      case "flx": case "f": case "gv":
      case "html": case "ics": case "3dml":
      case "spot": case "jad": case "java":
      case "fly": case "n3": case "p":
      case "dsc": case "rtx": case "etx":
      case "sgml": case "tsv": case "txt":
      case "log": case "t": case "ttl":
      case "uri": case "uu": case "vcs":
      case "vcf": case "wml": case "wmls":
      case "yaml": case "js": case "sh":
      case "es": case "json": case "xml":
      case "php": case "py": case "lua":
        return true;
    }
    return false;
  }

  public static boolean isPlainTextMimeType (@Nullable String mimeType) {
    if (StringUtils.isEmpty(mimeType)) {
      return false;
    }
    switch (mimeType) {
      case "text/x-asm": case "text/plain-bas": case "text/x-c":
      case "text/css": case "text/csv": case "text/vnd.curl":
      case "text/vnd.curl.dcurl": case "text/vnd.curl.mcurl": case "text/vnd.curl.scurl":
      case "text/vnd.fmi.flexstor": case "text/x-fortran": case "text/vnd.graphviz":
      case "text/html": case "text/calendar": case "text/vnd.in3d.3dml":
      case "text/vnd.in3d.spot": case "text/vnd.sun.j2me.app-descriptor": case "text/x-java-source":
      case "text/vnd.fly": case "text/n3": case "text/x-pascal":
      case "text/prs.lines.tag": case "text/richtext": case "text/x-setext":
      case "text/sgml": case "text/tab-separated-values": case "text/plain":
      case "text/troff": case "text/turtle": case "text/uri-list":
      case "text/x-uuencode": case "text/x-vcalendar": case "text/x-vcard":
      case "text/vnd.wap.wml": case "text/vnd.wap.wmlscript": case "text/yaml":
      case "application/javascript": case "application/x-sh": case "application/ecmascript":
      case "application/json": case "application/xml":

      case "text/php": case "text/x-php": case "application/php":case "application/x-php":case "application/x-httpd-php":case "application/x-httpd-php-source":
        return true;
    }
    return false;
  }

  public static boolean isAudioMimeType (@Nullable String mimeType) {
    if (StringUtils.isEmpty(mimeType)) {
      return false;
    }
    switch (mimeType) {
      case "audio/mpeg":
        return true;
    }
    return false;
  }

  public static boolean isImageMimeType (@Nullable String mimeType) {
    if (StringUtils.isEmpty(mimeType) || !mimeType.startsWith("image/")) {
      return false;
    }
    switch (mimeType) {
      case "image/png":
      case "image/gif":
      case "image/jpeg":
      case "image/jpg":
      case "image/heic":
        return true;
    }
    return false;
  }

  public static boolean isVideoMimeType (@Nullable String mimeType) {
    if (StringUtils.isEmpty(mimeType) || (!mimeType.startsWith("video/") && !mimeType.startsWith("application/"))) {
      return false;
    }
    switch (mimeType) {
      case "video/mov":
      case "video/mp4":
      case "application/mp4":
        return true;
    }
    return false;
  }

}