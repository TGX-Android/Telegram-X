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
