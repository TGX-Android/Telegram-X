package org.thunderdog.challegram.voip;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.N;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;

public class TgCallsController extends VoIPInstance {
  private final String version;
  private long nativePtr;
  public TgCallsController (@NonNull CallConfiguration configuration, @NonNull CallOptions options, @NonNull ConnectionStateListener stateListener, String version) {
    super(configuration, options, stateListener);
    this.version = version;
    this.nativePtr = N.newTgCallsInstance(version, configuration, options);
  }

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
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleAudioOutputGainControlEnabled (boolean isEnabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleEchoCancellationStrengthChange (int strength) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleMicDisabled (boolean isDisabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void handleNetworkTypeChange (@CallNetworkType int type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CharSequence collectDebugLog () {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getConnectionId () {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getNetworkStats (NetworkStats out) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void performDestroy () {
    throw new UnsupportedOperationException();
  }
}
