package org.thunderdog.challegram.voip;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

@SuppressWarnings("JavaJniMissingFunction")
public final class VoIPController extends VoIPInstance {
  public static final int PEER_CAP_GROUP_CALLS = 1;

  private long nativeInst;

  public VoIPController (
    @NonNull CallConfiguration configuration,
    @NonNull CallOptions options,
    @NonNull ConnectionStateListener stateListener
  ) {
    super(configuration, options, stateListener);
    nativeInst = nativeInit(configuration.persistentStateFilePath);
  }

  public void start () {
    ensureNativeInstance();
    nativeStart(nativeInst);
  }

  public void connect () {
    ensureNativeInstance();
    nativeConnect(nativeInst);
  }

  public void setRemoteEndpoints (TdApi.CallServer[] endpoints, boolean allowP2p, boolean tcp, int connectionMaxLayer) {
    if (endpoints.length == 0) {
      throw new IllegalArgumentException("endpoints size is 0");
    }
    boolean needFilter = false;
    for (TdApi.CallServer endpoint : endpoints) {
      if (endpoint.type.getConstructor() != TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR) {
        needFilter = true;
        continue;
      }
      if (StringUtils.isEmpty(endpoint.ipAddress) && StringUtils.isEmpty(endpoint.ipv6Address)) {
        throw new IllegalArgumentException("endpoint "+endpoint+" has empty/null ipv4");
      }
      byte[] peerTag = ((TdApi.CallServerTypeTelegramReflector) endpoint.type).peerTag;
      if (peerTag != null && peerTag.length != 16) {
        throw new IllegalArgumentException("endpoint "+endpoint + " has peer_tag of wrong length");
      }
    }
    if (needFilter) {
      List<TdApi.CallServer> callServers = new ArrayList<>();
      for (TdApi.CallServer endpoint : endpoints) {
        if (endpoint.type.getConstructor() == TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR) {
          callServers.add(endpoint);
        }
      }
      endpoints = callServers.toArray(new TdApi.CallServer[0]);
      if (endpoints.length == 0)
        throw new IllegalArgumentException("no CallServerTypeTelegramReflector found");
    }
    ensureNativeInstance();
    nativeSetRemoteEndpoints(nativeInst, endpoints, allowP2p, tcp, connectionMaxLayer);
  }

  private static boolean isSystemAcousticEchoCancelerAvailable () {
    try {
      return AcousticEchoCanceler.isAvailable();
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean isSystemNoiseSuppressorAvailable () {
    try {
      return NoiseSuppressor.isAvailable();
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void setConfiguration (CallConfiguration configuration) {
    ensureNativeInstance();
    nativeSetConfig(nativeInst,
      configuration.packetTimeoutMs,
      configuration.connectTimeoutMs,
      configuration.dataSavingOption,
      configuration.enableAcousticEchoCanceler || (!isSystemAcousticEchoCancelerAvailable() && !VoIP.needDisableAcousticEchoCancellation()),
      configuration.enableNoiseSuppressor || (!isSystemNoiseSuppressorAvailable() && !VoIP.needDisableNoiseSuppressor()),
      configuration.enableAutomaticGainControl && !VoIP.needDisableAutomaticGainControl(),
      configuration.logFilePath,
      null,
      false
    );
  }

  @Override
  public void initializeAndConnect () throws IllegalArgumentException {
    setConfiguration(configuration);
    handleAudioOutputGainControlEnabled(options.audioGainControlEnabled);
    handleEchoCancellationStrengthChange(options.echoCancellationStrength);
    handleMicDisabled(options.isMicDisabled);
    setEncryptionKey(configuration.state.encryptionKey, configuration.isOutgoing);
    setRemoteEndpoints(
      configuration.state.servers,
      configuration.state.protocol.udpP2p && configuration.state.allowP2p,
      configuration.forceTcp,
      configuration.state.protocol.maxLayer
    );
    if (configuration.proxy != null) {
      setProxy(
        configuration.proxy.host,
        configuration.proxy.port,
        configuration.proxy.username,
        configuration.proxy.password
      );
    }
    start();
    setNetworkType(options.networkType);
    connect();
  }

  public void setEncryptionKey (byte[] key, boolean isOutgoing) {
    if (key.length != 256) {
      throw new IllegalArgumentException("key length must be exactly 256 bytes but is "+key.length);
    }
    ensureNativeInstance();
    nativeSetEncryptionKey(nativeInst, key, isOutgoing);
  }

  public static void setNativeBufferSize (int size) {
    nativeSetNativeBufferSize(size);
  }

  public void release () {
    ensureNativeInstance();
    nativeRelease(nativeInst);
    nativeInst = 0;
  }

  @Override
  public void performDestroy () {
    release();
  }

  @Override
  public CharSequence collectDebugLog () {
    ensureNativeInstance();
    return nativeGetDebugString(nativeInst);
  }

  private void ensureNativeInstance () {
    if (nativeInst == 0) {
      throw new IllegalStateException("Native instance is not valid");
    }
  }

  // called from native code
  @Keep
  private void groupCallKeyReceived (byte[] key) {
    connectionStateListener.onGroupCallKeyReceived(key);
  }

  // called from native code
  @Keep
  private void groupCallKeySent () {
    connectionStateListener.onGroupCallKeySent();
  }

  // called from native code
  @Keep
  private void callUpgradeRequestReceived () {
    connectionStateListener.onCallUpgradeRequestReceived();
  }

  @Override
  protected void handleNetworkTypeChange (@CallNetworkType int type) {
    ensureNativeInstance();
    nativeSetNetworkType(nativeInst, type);
  }

  @Override
  public void handleMicDisabled (boolean isDisabled) {
    ensureNativeInstance();
    nativeSetMicMute(nativeInst, isDisabled);
  }

  public void debugCtl (int request, int param) {
    ensureNativeInstance();
    nativeDebugCtl(nativeInst, request, param);
  }

  public long getPreferredRelayID () {
    ensureNativeInstance();
    return nativeGetPreferredRelayID(nativeInst);
  }

  @Override
  public long getConnectionId () {
    return getPreferredRelayID();
  }

  public int getLastError () {
    ensureNativeInstance();
    return nativeGetLastError(nativeInst);
  }

  public void getNetworkStats (NetworkStats stats) {
    if(stats == null)
      throw new NullPointerException("You're not supposed to pass null here");
    ensureNativeInstance();
    nativeGetStats(nativeInst, stats);
  }

  @Override
  public String getLibraryName () {
    return "libtgvoip";
  }

  @Override
  public String getLibraryVersion () {
    return getVersion();
  }

  public static String getVersion () {
    return nativeGetVersion();
  }

  @Override
  public void handleIncomingSignalingData (byte[] buffer) {
    // Not implemented
  }

  public String getDebugLog () {
    ensureNativeInstance();
    return nativeGetDebugLog(nativeInst);
  }

  public void setProxy (String address, int port, String username, String password) {
    ensureNativeInstance();
    if (address == null)
      throw new NullPointerException("address can't be null");
    nativeSetProxy(nativeInst, address, port, username, password);
  }

  @Override
  public void handleAudioOutputGainControlEnabled (boolean enabled) {
    ensureNativeInstance();
    nativeSetAudioOutputGainControlEnabled(nativeInst, enabled);
  }

  public int getPeerCapabilities () {
    ensureNativeInstance();
    return nativeGetPeerCapabilities(nativeInst);
  }

  public void sendGroupCallKey (byte[] key) {
    if (key == null)
      throw new NullPointerException("key can not be null");
    if (key.length != 256)
      throw new IllegalArgumentException("key must be 256 bytes long, got "+key.length);
    ensureNativeInstance();
    nativeSendGroupCallKey(nativeInst, key);
  }

  public void requestCallUpgrade () {
    ensureNativeInstance();
    nativeRequestCallUpgrade(nativeInst);
  }

  @Override
  public void handleEchoCancellationStrengthChange (int strength) {
    ensureNativeInstance();
    nativeSetEchoCancellationStrength(nativeInst, strength);
  }

  public boolean needRate () {
    ensureNativeInstance();
    return nativeNeedRate(nativeInst);
  }

  private native long nativeInit (String persistentStateFile);
  private native void nativeStart (long inst);
  private native void nativeConnect (long inst);
  private static native void nativeSetNativeBufferSize (int size);
  private native void nativeSetRemoteEndpoints (long inst, TdApi.CallServer[] endpoints, boolean allowP2p, boolean tcp, int connectionMaxLayer);
  private native void nativeRelease (long inst);
  private native void nativeSetNetworkType (long inst, int type);
  private native void nativeSetMicMute (long inst, boolean mute);
  private native void nativeDebugCtl (long inst, int request, int param);
  private native void nativeGetStats (long inst, NetworkStats stats);
  private native void nativeSetConfig (long inst, double recvTimeout, double initTimeout, int dataSavingOption, boolean enableAEC, boolean enableNS, boolean enableAGC, String logFilePath, String statsDumpPath, boolean logPacketStats);
  private native void nativeSetEncryptionKey (long inst, byte[] key, boolean isOutgoing);
  private native void nativeSetProxy (long inst, String address, int port, String username, String password);
  private native long nativeGetPreferredRelayID (long inst);
  private native int nativeGetLastError (long inst);
  private native String nativeGetDebugString (long inst);
  private static native String nativeGetVersion ();
  private native void nativeSetAudioOutputGainControlEnabled (long inst, boolean enabled);
  private native void nativeSetEchoCancellationStrength (long inst, int strength);
  private native String nativeGetDebugLog (long inst);
  private native int nativeGetPeerCapabilities (long inst);
  private native void nativeSendGroupCallKey (long inst, byte[] key);
  private native void nativeRequestCallUpgrade (long inst);
  private static native boolean nativeNeedRate (long inst);

  public static native int getConnectionMaxLayer ();

}
