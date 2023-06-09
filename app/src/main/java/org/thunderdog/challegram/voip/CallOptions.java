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

import org.thunderdog.challegram.voip.annotation.CallNetworkType;

public class CallOptions {
  public @CallNetworkType int networkType;
  public boolean audioGainControlEnabled;
  public int echoCancellationStrength;
  public boolean isMicDisabled;

  public CallOptions (int networkType, boolean audioGainControlEnabled, int echoCancellationStrength, boolean isMicDisabled) {
    this.networkType = networkType;
    this.audioGainControlEnabled = audioGainControlEnabled;
    this.echoCancellationStrength = echoCancellationStrength;
    this.isMicDisabled = isMicDisabled;
  }
}
