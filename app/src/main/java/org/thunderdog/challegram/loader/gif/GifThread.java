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
 * File created on 01/03/2016 at 19:45
 */
package org.thunderdog.challegram.loader.gif;

import android.os.Message;

import org.thunderdog.challegram.core.BaseThread;

public class GifThread extends BaseThread {
  private static final int START_DECODING = 0;
  private static final int PREPARE_NEXT_FRAME = 1;
  private static final int PREPARE_START_FRAME = 2;
  private static final int DESTROY = 3;

  public GifThread (int index) {
    super("GifThread#" + index);
  }

  @Override
  protected void process (Message msg) {
    switch (msg.what) {
      case START_DECODING: {
        Object[] obj = (Object[]) msg.obj;

        ((GifActor) obj[0]).startDecoding((String) obj[1]);

        obj[0] = null;
        obj[1] = null;

        break;
      }
      case PREPARE_NEXT_FRAME: {
        ((GifActor) msg.obj).prepareNextFrame();
        break;
      }
      case PREPARE_START_FRAME: {
        ((GifActor) msg.obj).prepareStartFrame();
        break;
      }
      case DESTROY: {
        ((GifActor) msg.obj).onDestroy();
        break;
      }
    }
  }

  // GifBridge thread
  public void startDecoding (final GifActor actor, final String path) {
    sendMessage(Message.obtain(getHandler(), START_DECODING, new Object[] {actor, path}), 0);
  }

  // GifBridge thread
  public void prepareStartFrame (final GifActor actor) {
    sendMessage(Message.obtain(getHandler(), PREPARE_START_FRAME, actor), 0);
  }

  // GifBridge thread
  public void prepareNextFrame (final GifActor actor) {
    sendMessage(Message.obtain(getHandler(), PREPARE_NEXT_FRAME, actor), 0);
  }

  // GifBridge thread
  public void onDestroy (final GifActor actor) {
    sendMessage(Message.obtain(getHandler(), DESTROY, actor), 0);
  }
}
