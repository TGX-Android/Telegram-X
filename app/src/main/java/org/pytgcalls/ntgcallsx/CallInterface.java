package org.pytgcalls.ntgcallsx;

import org.drinkless.tdlib.TdApi;
import org.pytgcalls.ntgcalls.FrameCallback;
import org.pytgcalls.ntgcalls.RemoteSourceChangeCallback;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;

public interface CallInterface {
  boolean isVideoSupported();

  void setFrameCallback(FrameCallback callback);

  void setRemoteSourceChangeCallback(RemoteSourceChangeCallback callback);

  long getCallDuration();

  void setAudioOutputGainControlEnabled(boolean enabled);

  void handleIncomingSignalingData(byte[] data);

  void setEchoCancellationStrength(int strength);

  void setMicDisabled(boolean disabled);

  void setCameraEnabled(boolean enabled, boolean front);

  void setScreenShareEnabled(boolean enabled);

  long getConnectionId();

  void setNetworkType(@CallNetworkType int type);

  void getNetworkStats(NetworkStats stats);

  void performDestroy();

  CharSequence collectDebugLog();

  TdApi.Call getCall();

  Tdlib tdlib();

  boolean isInitiated();

  String getLibraryName();

  String getLibraryVersion();
}
