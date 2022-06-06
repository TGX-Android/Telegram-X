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
 *
 * File created on 07/05/2015 at 16:35
 */
package org.thunderdog.challegram.loader;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.TdlibProvider;

public class ImageFileRemote extends ImageFile {
  private TdApi.Function<?> getterFunction;
  private TdApi.FileType fileType;
  private boolean fileUpdated;
  private String forceRemoteId;

  public ImageFileRemote (TdlibProvider tdlib, String persistentId, TdApi.FileType fileType) {
    super(tdlib, TD.newFile(0, persistentId, "", 0));
    this.fileType = fileType;
    setNoBlur();
  }

  public ImageFileRemote (TdlibProvider tdlib, TdApi.Function<?> function, String remoteId) {
    super(tdlib, TD.newFile(0, "", "", 0));
    this.getterFunction = function;
    this.forceRemoteId = remoteId;
  }

  protected final String getForceRemoteId () {
    return forceRemoteId;
  }

  public void extractFile (Client.ResultHandler handler) {
    TdApi.Function<?> function = getterFunction != null ? getterFunction : new TdApi.GetRemoteFile(file.remote.id, getFileType());
    tdlib.tdlib().client().send(function, handler);
  }

  public TdApi.FileType getFileType () {
    return fileType != null ? fileType : new TdApi.FileTypeUnknown();
  }

  public void updateRemoteFile (TdApi.File file) {
    updateFile(file);
    fileUpdated = true;
  }

  @Override
  public final String getFileLoadKey () {
    return ImageFile.getFileLoadKey(accountId(), forceRemoteId != null ? forceRemoteId : file.remote.id);
  }

  public boolean isRemoteFileReady () {
    return fileUpdated;
  }

  @Override
  protected String buildImageKey () {
    return getFileLoadKey() + (needDecodeSquare() ? "_square" : "");
  }

  @Override
  public byte getType () {
    return TYPE_PERSISTENT;
  }
}
