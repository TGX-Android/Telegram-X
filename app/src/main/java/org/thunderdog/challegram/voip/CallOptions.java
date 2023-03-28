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
