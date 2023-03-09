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
 * File created on 11/12/2016
 */
package org.thunderdog.challegram.loader;

import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.mediaview.data.FiltersState;

public class ImageFilteredFile extends ImageFile {
  private ImageFile sourceFile;
  private long filterSessionId;
  private boolean isPrivate;

  public ImageFilteredFile (ImageFile sourceFile, boolean filterSessionIsPrivate, long filterSessionId) {
    super(sourceFile.tdlib, sourceFile.file);
    setNoCache();
    this.sourceFile = sourceFile;
    this.filterSessionId = filterSessionId;
    this.isPrivate = filterSessionIsPrivate;
  }

  public ImageFile getSourceFile () {
    return sourceFile;
  }

  @Override
  public void setRotation (int degrees) {
    sourceFile.setRotation(degrees);
  }

  @Override
  public int getRotation () {
    return sourceFile.getRotation();
  }

  @Override
  public int getVisualRotation () {
    return sourceFile.getVisualRotation();
  }

  @Override
  public boolean isProbablyRotated () {
    return sourceFile.isProbablyRotated();
  }

  @Override
  public String getFilePath () {
    return getPath(isPrivate, filterSessionId);
  }

  public static String getPath (FiltersState filtersState) {
    return getPath(filtersState.isPrivateSession(), filtersState.getSessionId());
  }

  public static String getPath (boolean isPrivate, long filterSessionId) {
    return TD.getCacheDir(!isPrivate).getPath() + "/temp_" + filterSessionId + ".jpg";
  }

  @Override
  protected String buildImageKey () {
    return "filtered_" + filterSessionId;
  }
}
