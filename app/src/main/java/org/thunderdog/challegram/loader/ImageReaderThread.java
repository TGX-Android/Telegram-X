/**
 * File created on 06/05/15 at 18:45
 * Copyright Vyacheslav Krylov, 2014
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
