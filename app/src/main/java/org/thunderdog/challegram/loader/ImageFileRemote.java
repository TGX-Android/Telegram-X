/**
 * File created on 07/05/15 at 16:35
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.TdlibProvider;

public class ImageFileRemote extends ImageFile {
  private TdApi.Function getterFunction;
  private TdApi.FileType fileType;
  private boolean fileUpdated;
  private String forceRemoteId;

  public ImageFileRemote (TdlibProvider tdlib, String persistentId, TdApi.FileType fileType) {
    super(tdlib, TD.newFile(0, persistentId, "", 0));
    this.fileType = fileType;
    setNoBlur();
  }

  public ImageFileRemote (TdlibProvider tdlib, TdApi.Function function, String remoteId) {
    super(tdlib, TD.newFile(0, "", "", 0));
    this.getterFunction = function;
    this.forceRemoteId = remoteId;
  }

  protected final String getForceRemoteId () {
    return forceRemoteId;
  }

  public void extractFile (Client.ResultHandler handler) {
    TdApi.Function function = getterFunction != null ? getterFunction : new TdApi.GetRemoteFile(file.remote.id, getFileType());
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
