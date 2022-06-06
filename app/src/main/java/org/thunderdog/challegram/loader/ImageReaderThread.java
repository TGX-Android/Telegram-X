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
 * File created on 06/05/2015 at 18:45
 */
package org.thunderdog.challegram.loader;

import android.os.Message;

import org.thunderdog.challegram.core.BaseThread;

public class ImageReaderThread extends BaseThread {
  private static final int READ_IMAGE = 0;

  public ImageReaderThread () {
    super("ImageReaderThread");
  }

  public void readImage (ImageActor actor, ImageFile file, String path, ImageReader.Listener listener) {
    sendMessage(Message.obtain(getHandler(), READ_IMAGE, new Object[] {actor, file, path, listener}), 0);
  }

  @Override
  protected void process (Message msg) {
    switch (msg.what) {
      case READ_IMAGE: {
        Object[] data = (Object[]) msg.obj;

        ImageReader.instance().readImage((ImageActor) data[0], (ImageFile) data[1], (String) data[2], (ImageReader.Listener) data[3]);

        data[0] = null;
        data[1] = null;
        data[2] = null;
        data[3] = null;

        break;
      }
    }
  }
}
