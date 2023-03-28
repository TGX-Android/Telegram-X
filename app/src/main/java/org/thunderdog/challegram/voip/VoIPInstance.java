package org.thunderdog.challegram.voip;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.annotation.CallState;

import me.vkryl.core.lambda.Destroyable;

public abstract class VoIPInstance implements Destroyable {
  protected final CallConfiguration configuration;
  protected final CallOptions options;
  protected final @NonNull ConnectionStateListener connectionStateListener;

  public VoIPInstance (@NonNull CallConfiguration configuration,
                       @NonNull CallOptions options,
                       @NonNull ConnectionStateListener stateListener) {
    this.configuration = configuration;
    this.options = options;
    this.connectionStateListener = stateListener;
  }

  public abstract void initializeAndConnect ();

  // Getters

  public final @NonNull CallConfiguration getConfiguration () {
    return configuration;
  }

  public final @NonNull CallOptions getOptions () {
    return options;
  }

  // Connection state

  private long callStartTime;

  protected final void dispatchCallStateChanged (@CallState int state) {
    // this.callState = state;
    if (state == CallState.ESTABLISHED && callStartTime == 0) {
      callStartTime = SystemClock.elapsedRealtime();
    }
    connectionStateListener.onConnectionStateChanged(state);
  }

  public static long DURATION_UNKNOWN = -1;

  public final long getCallDuration () {
    return callStartTime != 0 ? SystemClock.elapsedRealtime() - callStartTime : DURATION_UNKNOWN;
  }

  // Setters

  public final void setAudioOutputGainControlEnabled (boolean isEnabled) {
    options.audioGainControlEnabled = isEnabled;
    handleAudioOutputGainControlEnabled(isEnabled);
  }
  protected abstract void handleAudioOutputGainControlEnabled (boolean isEnabled);

  public final void setEchoCancellationStrength (int strength) {
    options.echoCancellationStrength = strength;
    handleEchoCancellationStrengthChange(strength);
  }
  protected abstract void handleEchoCancellationStrengthChange (int strength);

  public final void setMicDisabled (boolean isDisabled) {
    options.isMicDisabled = isDisabled;
    handleMicDisabled(isDisabled);
  }
  protected abstract void handleMicDisabled (boolean isDisabled);

  public void setNetworkType (@CallNetworkType int type) {
    options.networkType = type;
    handleNetworkTypeChange(type);
  }

  protected abstract void handleNetworkTypeChange (@CallNetworkType int type);

  // Getters

  public abstract CharSequence collectDebugLog ();
  public abstract long getConnectionId ();
  public abstract void getNetworkStats (NetworkStats out);

  public abstract String getLibraryName ();
  public abstract String getLibraryVersion ();
}
