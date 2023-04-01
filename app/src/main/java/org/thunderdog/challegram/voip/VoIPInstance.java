/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 28/03/2023
 */
package org.thunderdog.challegram.voip;

import android.os.SystemClock;

import androidx.annotation.Keep;
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
    connectionStateListener.onConnectionStateChanged(this, state);
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

  // called from native code

  @Keep
  protected final void handleStateChange (@CallState int state) {
    dispatchCallStateChanged(state);
  }

  @Keep
  protected final void handleSignalBarsChange (int count) {
    connectionStateListener.onSignalBarCountChanged(count);
  }

  @Keep
  protected final void handleEmittedSignalingData (byte[] buffer) {
    connectionStateListener.onSignallingDataEmitted(buffer);
  }

  // called from TDLib

  public abstract void handleIncomingSignalingData (byte[] buffer);
}
