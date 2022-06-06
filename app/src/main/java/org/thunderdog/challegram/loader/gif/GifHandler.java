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
 * File created on 01/03/2016 at 13:27
 */
package org.thunderdog.challegram.loader.gif;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class GifHandler extends Handler {
  private static final int DISPATCH_PROGRESS = 1;
  private static final int DISPATCH_LOAD = 2;
  private static final int DISPATCH_FRAME = 3;

  public GifHandler () {
    super(Looper.getMainLooper());
  }

  public void onProgress (GifReceiver receiver, GifFile file, float progress) {
    sendMessage(Message.obtain(this, DISPATCH_PROGRESS, Float.floatToIntBits(progress), 0, new Object[] {receiver, file}));
  }

  public void onLoad (GifReceiver receiver, GifFile file, GifState gif) {
    sendMessage(Message.obtain(this, DISPATCH_LOAD, new Object[] {receiver, file, gif}));
  }

  public void onFrame (GifFile file, GifState gif) {
    sendMessage(Message.obtain(this, DISPATCH_FRAME, new Object[] {file, gif}));
  }

  @Override
  public void handleMessage (Message msg) {
    switch (msg.what) {
      case DISPATCH_PROGRESS: {
        Object[] obj = (Object[]) msg.obj;

        ((GifReceiver) obj[0]).onProgress((GifFile) obj[1], Float.intBitsToFloat(msg.arg1));

        obj[0] = null;
        obj[1] = null;

        break;
      }
      case DISPATCH_LOAD: {
        Object[] obj = (Object[]) msg.obj;

        ((GifReceiver) obj[0]).onLoad((GifFile) obj[1], (GifState) obj[2]);

        obj[0] = null;
        obj[1] = null;
        obj[2] = null;

        break;
      }
      case DISPATCH_FRAME: {
        Object[] obj = (Object[]) msg.obj;

        GifBridge.instance().onGifFrameChanged((GifFile) obj[0], (GifState) obj[1]);

        obj[0] = null;
        obj[1] = null;

        break;
      }
    }
  }
}
