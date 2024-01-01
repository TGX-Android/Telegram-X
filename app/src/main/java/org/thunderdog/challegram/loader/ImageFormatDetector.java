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
 * File created on 31/12/2023
 */
package org.thunderdog.challegram.loader;

import org.thunderdog.challegram.Log;

import java.io.FileInputStream;

public class ImageFormatDetector {
  public static String getImageFormat (String filePath) {
    byte[] buffer = new byte[8];
    boolean isRead = false;
    try (FileInputStream fis = new FileInputStream(filePath)) {
      if (fis.read(buffer, 0, 8) == 8) {
        isRead = true;
      }
    } catch (Throwable t) {
      Log.i("Unable to detect image format", t);
    }
    if (isRead) {
      if (isJPEG(buffer)) {
        return "image/jpeg";
      } else if (isPNG(buffer)) {
        return "image/png";
      } else if (isGIF(buffer)) {
        return "image/gif";
      } else if (isWebP(buffer)) {
        return "image/webp";
      } else if (isBMP(buffer)) {
        return "image/bmp";
      }
    }
    return null;
  }

  private static boolean isJPEG (byte[] buffer) {
    return buffer[0] == (byte) 0xFF && buffer[1] == (byte) 0xD8;
  }

  private static boolean isPNG (byte[] buffer) {
    return buffer[0] == (byte) 0x89 && buffer[1] == (byte) 0x50 && buffer[2] == (byte) 0x4E && buffer[3] == (byte) 0x47
      && buffer[4] == (byte) 0x0D && buffer[5] == (byte) 0x0A && buffer[6] == (byte) 0x1A && buffer[7] == (byte) 0x0A;
  }

  private static boolean isGIF (byte[] buffer) {
    return buffer[0] == (byte) 0x47 && buffer[1] == (byte) 0x49 && buffer[2] == (byte) 0x46 && buffer[3] == (byte) 0x38
      && (buffer[4] == (byte) 0x37 || buffer[4] == (byte) 0x39) && buffer[5] == (byte) 0x61;
  }

  private static boolean isWebP (byte[] buffer) {
    return buffer[0] == (byte) 0x52 && buffer[1] == (byte) 0x49 && buffer[2] == (byte) 0x46 && buffer[3] == (byte) 0x46
      && buffer[8] == (byte) 0x57 && buffer[9] == (byte) 0x45 && buffer[10] == (byte) 0x42 && buffer[11] == (byte) 0x50;
  }

  private static boolean isBMP (byte[] buffer) {
    return buffer[0] == (byte) 0x42 && buffer[1] == (byte) 0x4D;
  }
}
