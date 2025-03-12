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
 * File created on 18/05/2015 at 00:46
 */
package org.thunderdog.challegram.loader;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class ImageAnimatorHandler extends Handler {

  public ImageAnimatorHandler () {
    super(Looper.getMainLooper());
  }

  @Override
  public void handleMessage (Message msg) {
    ((ImageAnimator) msg.obj).onFrame(msg.what);
  }
}
