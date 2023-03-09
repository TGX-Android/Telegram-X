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
 * File created on 28/08/2015 at 17:52
 */
package org.thunderdog.challegram.core;

public class Background {
  private static Background instance;

  public static Background instance () {
    if (instance == null) {
      instance = new Background();
    }
    return instance;
  }

  private BaseThread thread;

  private Background () {
    thread = new BaseThread("ChallegramThread");
  }

  public void post (Runnable run) {
    thread.post(run, 0);
  }

  public void post (Runnable run, int delay) {
    thread.post(run, delay);
  }

  public BaseThread thread () {
    return thread;
  }
}
