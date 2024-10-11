package org.thunderdog.challegram.service;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.voip.ConnectionStateListener;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.Socks5Proxy;
import org.thunderdog.challegram.voip.VoIP;
import org.thunderdog.challegram.voip.VoIPInstance;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.gui.CallSettings;

import java.text.MessageFormat;

public class TgCallsWrapper implements CallInterface {
  final VoIPInstance call;

  public TgCallsWrapper (Tdlib tdlib, TdApi.Call tgCall, TdApi.CallStateReady state, ConnectionStateListener stateListener, boolean forceTcp, @Nullable Socks5Proxy callProxy, @CallNetworkType int lastNetworkType, boolean audioGainControlEnabled, int echoCancellationStrength, boolean isMicDisabled) {
    call = VoIP.instantiateAndConnect(
      tdlib,
      tgCall,
      state,
      stateListener,
      forceTcp,
      callProxy,
      lastNetworkType,
      audioGainControlEnabled,
      echoCancellationStrength,
      isMicDisabled
    );
  }

  public long getCallDuration () {
    return call.getCallDuration();
  }

  public String getLibraryNameAndVersion () {
    return call != null ?
      MessageFormat.format("{0} {1}", call.getLibraryName(), call.getLibraryVersion()) : "unknown";
  }

  public String getDebugString () {
    return "";
  }

  public void handleIncomingSignalingData (byte[] data) {
    call.handleIncomingSignalingData(data);
  }

  public long getConnectionId () {
    return call != null ? call.getConnectionId() : 0;
  }

  public void getNetworkStats (NetworkStats stats) {
    call.getNetworkStats(stats);
  }

  public void getMicrophoneStatus (CallSettings settings) {
    if (call != null) {
      call.setMicDisabled(settings != null && settings.isMicMuted());
    }
  }

  public void releaseTgCalls() {

  }

  public boolean isInitiated () {
    return call != null;
  }

  public void callAudioControl (boolean audioGainControlEnabled, int echoCancellationStrength) {
    if (call != null) {
      call.setAudioOutputGainControlEnabled(audioGainControlEnabled);
      call.setEchoCancellationStrength(echoCancellationStrength);
    }
  }

  public void setNetworkType (boolean dispatchToTgCalls, int type) {
    if (dispatchToTgCalls && call != null) {
      call.setNetworkType(type);
    }
  }

  public CharSequence collectDebugLog() {
    return null;
  }

  public void stop() {

  }

  public void setSignalingDataCallback(byte[] o) {

  }

  public void createCall() {

  }

  public void setSignalingDataCallback (SignalingDataCallback callback) {
  }
}
