package org.thunderdog.challegram.voip;

import androidx.annotation.NonNull;

public class NetworkStats {
  public long bytesSentWifi;
  public long bytesRecvdWifi;
  public long bytesSentMobile;
  public long bytesRecvdMobile;

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
