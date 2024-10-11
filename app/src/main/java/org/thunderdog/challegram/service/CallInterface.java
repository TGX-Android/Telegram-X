package org.thunderdog.challegram.service;

import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.gui.CallSettings;

public interface CallInterface {
  long getCallDuration();
  String getLibraryNameAndVersion();
  String getDebugString();
  void handleIncomingSignalingData(byte[] data);
  long getConnectionId();
  void getNetworkStats(NetworkStats stats);
  boolean isInitiated();
  void getMicrophoneStatus (CallSettings settings);
  void releaseTgCalls ();
  void callAudioControl (boolean audioGainControlEnabled, int echoCancellationStrength);
  void setNetworkType (boolean dispatchToTgCalls, int type);
  CharSequence collectDebugLog ();
  void stop ();
  void createCall ();
  void setSignalingDataCallback(SignalingDataCallback callback);
  interface SignalingDataCallback {
    void onSignalingDataReceived(long callId, byte[] data);
  }
}