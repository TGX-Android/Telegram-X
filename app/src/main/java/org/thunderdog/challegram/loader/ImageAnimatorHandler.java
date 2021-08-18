/**
 * File created on 18/05/15 at 00:46
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader;

import android.os.Handler;
import android.os.Message;

public class ImageAnimatorHandler extends Handler {
  @Override
  public void handleMessage (Message msg) {
    ((ImageAnimator) msg.obj).onFrame(msg.what);
  }
}
