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
 * File created on 22/10/2017
 */
package org.thunderdog.challegram.util.text;

public class Letters {
  public final String text;
  public final boolean needFakeBold;

  public Letters (String str) {
    this.text = str != null ? str : "";
    this.needFakeBold = Text.needFakeBold(str);
  }
}
