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
 * File created on 28/03/2023
 */
package org.thunderdog.challegram.voip;

import androidx.annotation.NonNull;

public class NetworkStats {
  public long bytesSentWifi;
  public long bytesRecvdWifi;
  public long bytesSentMobile;
  public long bytesRecvdMobile;

  public NetworkStats () { }

  public NetworkStats (long bytesSentWifi, long bytesRecvdWifi, long bytesSentMobile, long bytesRecvdMobile) {
    this.bytesSentWifi = bytesSentWifi;
    this.bytesRecvdWifi = bytesRecvdWifi;
    this.bytesSentMobile = bytesSentMobile;
    this.bytesRecvdMobile = bytesRecvdMobile;
  }

  @Override
  @NonNull
  public String toString () {
    return "Stats{" +
      "bytesRecvdMobile=" + bytesRecvdMobile +
      ", bytesSentWifi=" + bytesSentWifi +
      ", bytesRecvdWifi=" + bytesRecvdWifi +
      ", bytesSentMobile=" + bytesSentMobile +
      '}';
  }
}
