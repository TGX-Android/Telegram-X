/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 04/04/2017
 */
package org.thunderdog.challegram.filegen;

public class SimpleGenerationInfo extends GenerationInfo {
  public SimpleGenerationInfo (long generationId, String originalPath, String destinationPath, String conversion) {
    super(generationId, originalPath, destinationPath, conversion, false);
  }

  public static String makeConversion (String path) {
    return GenerationInfo.TYPE_AVATAR;
  }
}
