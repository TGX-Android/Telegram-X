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
 * File created on 24/02/2018
 */
package org.thunderdog.challegram.telegram;

import org.thunderdog.challegram.BaseActivity;

public class TdlibContext implements TdlibDelegate {
  private final BaseActivity context;
  private final Tdlib tdlib;

  public TdlibContext (BaseActivity context, Tdlib tdlib) {
    this.context = context;
    this.tdlib = tdlib;
  }

  @Override
  public BaseActivity context () {
    return context;
  }

  @Override
  public Tdlib tdlib () {
    return tdlib;
  }
}
