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
 * File created on 01/03/2016 at 11:14
 */
package org.thunderdog.challegram.loader.gif;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.BaseThread;

public class GifBridgeThread extends BaseThread {
  private static final int REQUEST_FILE = 1;
  private static final int REMOVE_WATCHER = 2;
  private static final int LOAD_COMPLETE = 3;
  private static final int GIF_LOADED = 4;

  public GifBridgeThread () {
    super("GifThread");
  }

  public void requestFile (GifFile file, GifWatcherReference reference) {
    sendMessage(Message.obtain(getHandler(), REQUEST_FILE, new Object[] {file, reference}), 0);
  }

  public void removeWatcher (GifWatcherReference reference) {
    sendMessage(Message.obtain(getHandler(), REMOVE_WATCHER, reference), 0);
  }

  public void onLoad (GifActor actor, TdApi.File file) {
    sendMessage(Message.obtain(getHandler(), LOAD_COMPLETE, new Object[] {actor, file}), 0);
  }

  public void onGifLoad (GifFile file, GifState gif) {
    sendMessage(Message.obtain(getHandler(), GIF_LOADED, new Object[] {file, gif}), 0);
  }

  public boolean canScheduleNextFrame (GifActor actor, int fileId) {
    return !getCustomHandler().hasMessages(fileId, actor);
  }

  public boolean scheduleNextFrame (GifActor actor, int fileId, long delay, boolean force) {
    final Handler handler = getCustomHandler();
    if (force) {
      handler.removeMessages(fileId, actor);
    } else if (handler.hasMessages(fileId, actor)) {
      return false;
    }
    handler.sendMessageDelayed(Message.obtain(handler, fileId, 0, 0, actor), delay);
    return true;
  }

  @Override
  protected Handler createCustomHandler () {
    return new Handler(Looper.myLooper(), message -> {
      ((GifActor) message.obj).onNextFrame(true, false);
      return true;
    });
  }

  public void nextFrameReady (GifActor actor, boolean restarted) {
    actor.nextFrameReady(restarted);
  }

  @Override
  protected void process (Message msg) {
    switch (msg.what) {
      case REQUEST_FILE: {
        Object[] obj = (Object[]) msg.obj;
        GifBridge.instance().requestFile((GifFile) obj[0], (GifWatcherReference) obj[1]);
        obj[0] = null;
        obj[1] = null;
        break;
      }
      case REMOVE_WATCHER: {
        GifBridge.instance().removeWatcher((GifWatcherReference) msg.obj);
        break;
      }
      case LOAD_COMPLETE: {
        Object[] data = (Object[]) msg.obj;

        ((GifActor) data[0]).onLoad((TdApi.File) data[1]);

        data[0] = null;
        data[1] = null;

        break;
      }
      case GIF_LOADED: {
        Object[] obj = (Object[]) msg.obj;

        GifBridge.instance().onGifLoaded((GifFile) obj[0], (GifState) obj[1]);

        obj[0] = null;
        obj[1] = null;

        break;
      }
    }
  }
}
