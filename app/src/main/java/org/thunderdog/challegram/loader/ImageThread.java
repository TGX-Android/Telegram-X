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
 * File created on 06/05/2015 at 14:19
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;
import android.os.Message;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.core.BaseThread;

public class ImageThread extends BaseThread {
  private static final int REQUEST = 0;
  private static final int REMOVE_WATCHER = 1;
  private static final int LOAD_COMPLETE = 2;
  private static final int ACTOR_RESULT = 3;
  private static final int CLEAR = 4;
  private static final int DOWNLOAD_FILE_PERSISTENT = 5;

  public ImageThread () {
    super("ImageThread");
  }

  public void request (ImageFile file, WatcherReference reference) {
    sendMessage(Message.obtain(getHandler(), REQUEST, new Object[] {file, reference}), 0);
  }

  public void removeWatcher (WatcherReference reference) {
    sendMessage(Message.obtain(getHandler(), REMOVE_WATCHER, reference), 0);
  }

  public void downloadFilePersistent (ImageFileRemote persistentFile, TdApi.File file) {
    sendMessage(Message.obtain(getHandler(), DOWNLOAD_FILE_PERSISTENT, new Object[] {persistentFile, file}), 0);
  }

  public void onLoad (ImageActor actor, TdApi.File file) {
    sendMessage(Message.obtain(getHandler(), LOAD_COMPLETE, new Object[] {actor, file}), 0);
  }

  public void onResult (ImageFile file, boolean success, Bitmap bitmap) {
    sendMessage(Message.obtain(getHandler(), ACTOR_RESULT, success ? 1 : 0, 0, new Object[] {file, bitmap}), 0);
  }

  public void clear (int accountId, boolean withMemcache) {
    sendMessage(Message.obtain(getHandler(), CLEAR, accountId, withMemcache ? 1 : 0), 0);
  }

  @Override
  protected void process (Message msg) {
    switch (msg.what) {
      case REQUEST: {
        Object[] data = (Object[]) msg.obj;

        ImageLoader.instance().requestFile((ImageFile) data[0], (WatcherReference) data[1]);

        data[0] = null;
        data[1] = null;

        break;
      }
      case REMOVE_WATCHER: {
        ImageLoader.instance().removeWatcher((WatcherReference) msg.obj);
        break;
      }
      case LOAD_COMPLETE: {
        Object[] data = (Object[]) msg.obj;

        ((ImageActor) data[0]).onLoad((TdApi.File) data[1]);

        data[0] = null;
        data[1] = null;

        break;
      }
      case ACTOR_RESULT: {
        Object[] data = (Object[]) msg.obj;

        ImageLoader.instance().onResult((ImageFile) data[0], msg.arg1 == 1, (Bitmap) data[1]);

        data[0] = null;
        data[1] = null;

        break;
      }
      case DOWNLOAD_FILE_PERSISTENT: {
        Object[] data = (Object[]) msg.obj;

        ImageLoader.instance().downloadFilePersistent((ImageFileRemote) data[0], (TdApi.File) data[1]);

        data[0] = null;
        data[1] = null;
        break;
      }
      case CLEAR: {
        ImageLoader.instance().clear(msg.arg1, msg.arg2 == 1);
        break;
      }
    }
  }
}
