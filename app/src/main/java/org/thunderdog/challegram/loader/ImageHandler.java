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
 * File created on 06/05/2015 at 13:49
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;

public class ImageHandler extends Handler {
  private static final int INVALIDATE = 0;
  private static final int ANIMATE = 1;
  private static final int DISPLAY = 2;

  public void invalidate (ImageReceiver image) {
    sendMessage(Message.obtain(this, INVALIDATE, image));
  }

  public void animate (ImageReceiver image) {
    sendMessage(Message.obtain(this, ANIMATE, image));
  }

  public void display (ImageReceiver image, ImageFile file, Bitmap bitmap) {
    sendMessage(Message.obtain(this, DISPLAY, new Object[] {image, file, bitmap}));
  }

  @Override
  public void handleMessage (Message msg) {
    switch (msg.what) {
      case INVALIDATE: {
        ((ImageReceiver) msg.obj).invalidate();
        break;
      }
      case ANIMATE: {
        ((ImageReceiver) msg.obj).animate();
        break;
      }
      case DISPLAY: {
        Object[] data = (Object[]) msg.obj;

        ((ImageReceiver) data[0]).setBundleOrIgnore((ImageFile) data[1], (Bitmap) data[2]);

        data[0] = null;
        data[1] = null;
        data[2] = null;

        break;
      }
    }
  }
}
