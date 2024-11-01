package org.pytgcalls.ntgcallsx;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import io.github.pytgcalls.FrameCallback;
import io.github.pytgcalls.RemoteSourceChangeCallback;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.voip.ConnectionStateListener;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.Socks5Proxy;
import org.thunderdog.challegram.voip.VoIP;
import org.thunderdog.challegram.voip.VoIPInstance;

public class TgCallsInterface implements CallInterface{
  private final VoIPInstance voIPInstance;

  public TgCallsInterface (Tdlib tdlib, TdApi.Call call, TdApi.CallStateReady state, ConnectionStateListener stateListener, boolean forceTcp, @Nullable Socks5Proxy callProxy, int lastNetworkType, boolean audioGainControlEnabled, int echoCancellationStrength, boolean isMicDisabled) {
    voIPInstance = VoIP.instantiateAndConnect(
      tdlib,
      call,
      state,
      stateListener,
      forceTcp,
      callProxy,
      lastNetworkType,
      audioGainControlEnabled,
      echoCancellationStrength,
      isMicDisabled
    );
    if (voIPInstance == null) {
      throw new RuntimeException("Failed to instantiate VoIPInstance");
    }
  }

  @Override
  public boolean isVideoSupported () {
    return false;
  }

  @Override
  public void setFrameCallback (FrameCallback callback) {

  }

  @Override
  public void setRemoteSourceChangeCallback (RemoteSourceChangeCallback callback) {

  }

  @Override
  public long getCallDuration () {
    return voIPInstance.getCallDuration();
  }

  @Override
  public void setAudioOutputGainControlEnabled (boolean enabled) {
    voIPInstance.setAudioOutputGainControlEnabled(enabled);
  }

  @Override
  public void handleIncomingSignalingData (byte[] data) {
    voIPInstance.handleIncomingSignalingData(data);
  }

  @Override
  public void setEchoCancellationStrength (int strength) {
    voIPInstance.setEchoCancellationStrength(strength);
  }

  @Override
  public void setMicDisabled (boolean disabled) {
    voIPInstance.setMicDisabled(disabled);
  }

  @Override
  public void setCameraEnabled (boolean enabled, boolean front) {

  }

  @Override
  public void setScreenShareEnabled (boolean enabled) {

  }

  @Override
  public long getConnectionId () {
    return voIPInstance.getConnectionId();
  }

  @Override
  public void setNetworkType (int type) {
    voIPInstance.setNetworkType(type);
  }

  @Override
  public void getNetworkStats (NetworkStats stats) {
    voIPInstance.getNetworkStats(stats);
  }

  @Override
  public void performDestroy () {
    voIPInstance.performDestroy();
  }

  @Override
  public CharSequence collectDebugLog () {
    return voIPInstance.collectDebugLog();
  }

  @Override
  public TdApi.Call getCall () {
    return voIPInstance.getCall();
  }

  @Override
  public Tdlib tdlib () {
    return voIPInstance.tdlib();
  }

  @Override
  public boolean isInitiated () {
    return true;
  }

  @Override
  public String getLibraryName () {
    return voIPInstance.getLibraryName();
  }

  @Override
  public String getLibraryVersion () {
    return voIPInstance.getLibraryVersion();
  }
}
