package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.voip.annotation.CallNetworkType;

@SuppressWarnings("JavaJniMissingFunction")
public class TgCallsController extends VoIPInstance {
  private final String version;
  private long nativePtr;
  public TgCallsController (@NonNull CallConfiguration configuration, @NonNull CallOptions options, @NonNull ConnectionStateListener stateListener, String version) {
    super(configuration, options, stateListener);
    if (configuration.state.encryptionKey.length != 256)
      throw new IllegalArgumentException(Integer.toString(configuration.state.encryptionKey.length));
    this.version = version;
    this.nativePtr = newInstance(version, configuration, options);
  }

  private long nativePtr () {
    long ptr = nativePtr;
    if (ptr == 0)
      throw new IllegalStateException();
    return ptr;
  }

  private native long newInstance (
    @NonNull String version,
    @NonNull CallConfiguration configuration,
    @NonNull CallOptions options
  );

  private native long preferredConnectionId (long ptr);
  private native @Nullable String lastError (long ptr);
  private native @Nullable String debugLog (long ptr);
  private native void fetchNetworkStats (long ptr, NetworkStats out);

  private native void processIncomingSignalingData (long ptr, byte[] buffer);

  private native void updateNetworkType (long ptr, @CallNetworkType int newType);
  private native void updateMicrophoneDisabled (long ptr, boolean isDisabled);
  private native void updateEchoCancellationStrength (long ptr, int strength);
  private native void updateAudioOutputGainControlEnabled (long ptr, boolean isEnabled);
  private native void destroyInstance (long ptr);

  @Override
  public String getLibraryName () {
    return "tgcalls";
  }

  @Override
  public String getLibraryVersion () {
    return version;
  }

  @Override
  public void initializeAndConnect () {
    // Nothing to do?
  }

  @Override
  protected void handleAudioOutputGainControlEnabled (boolean isEnabled) {
    updateAudioOutputGainControlEnabled(nativePtr(), isEnabled);
  }

  @Override
  protected void handleEchoCancellationStrengthChange (int strength) {
    updateEchoCancellationStrength(nativePtr(), strength);
  }

  @Override
  protected void handleMicDisabled (boolean isDisabled) {
    updateMicrophoneDisabled(nativePtr(), isDisabled);
  }

  @Override
  protected void handleNetworkTypeChange (@CallNetworkType int type) {
    updateNetworkType(nativePtr(), type);
  }

  @Override
  public long getConnectionId () {
    return preferredConnectionId(nativePtr());
  }

  @Nullable
  public String getLastError () {
    return lastError(nativePtr());
  }

  @Override
  public CharSequence collectDebugLog () {
    return debugLog(nativePtr());
  }

  @Override
  public void getNetworkStats (NetworkStats out) {
    fetchNetworkStats(nativePtr(), out);
  }

  @Override
  public void performDestroy () {
    if (nativePtr != 0) {
      destroyInstance(nativePtr);
      nativePtr = 0;
    }
  }

  // Called from tgvoip.cpp

  @Keep
  protected final void handleRemoteMediaStateChange (boolean isRemoteAudioMuted) {
    connectionStateListener.onRemoteMediaStateChanged(this, isRemoteAudioMuted);
  }

  @Keep
  protected final void handleAudioLevelChange (float audioLevel) {
    // TODO
  }
  @Keep
  protected final void handleStop (@NonNull NetworkStats totalStats, @Nullable String debugLog) {
    connectionStateListener.onStopped(this, totalStats, debugLog);
  }

  // Called from TDLib

  @Override
  public void handleIncomingSignalingData (byte[] buffer) {
    processIncomingSignalingData(nativePtr(), buffer);
  }
}
