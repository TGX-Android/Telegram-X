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
 * File created on 09/02/2024 at 18:34
 */
package org.thunderdog.challegram.component.attach;

import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.Permissions;

import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;

public abstract class MediaLayoutManager {
  protected MediaLayout currentMediaLayout;
  protected boolean openingMediaLayout;

  protected final ViewController<?> context;
  protected final Tdlib tdlib;

  public MediaLayoutManager (ViewController<?> context) {
    this.context = context;
    this.tdlib = context.tdlib();
  }

  protected final void waitPermissionsForOpen (RunnableBool onOpen) {
    waitPermissionsForOpen(false, false, onOpen);
  }

  private void waitPermissionsForOpen (boolean ignorePermissionRequest, boolean noMedia, RunnableBool onOpen) {
    if (openingMediaLayout || currentMediaLayout != null && currentMediaLayout.isVisible()) {
      return;
    }

    if (!ignorePermissionRequest && context.context().permissions().requestReadExternalStorage(Permissions.ReadType.IMAGES_AND_VIDEOS, grantType -> waitPermissionsForOpen(true, grantType == Permissions.GrantResult.NONE, onOpen))) {
      return;
    }

    onOpen.runWithBool(!noMedia);
  }

  public static MediaLayout.MediaGalleryCallback singleMediaCallback (RunnableData<ImageGalleryFile> callback) {
    return new MediaLayout.MediaGalleryCallback() {
      @Override
      public void onSendVideo (ImageGalleryFile file, boolean isFirst) {
        if (!isFirst) return;
        callback.runWithData(file);
      }

      @Override
      public void onSendPhoto (ImageGalleryFile file, boolean isFirst) {
        if (!isFirst) return;
        callback.runWithData(file);
      }
    };
  }
}
