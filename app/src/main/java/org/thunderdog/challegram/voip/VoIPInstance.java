package org.thunderdog.challegram.voip;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.annotation.DataSavingOption;

import me.vkryl.core.lambda.Destroyable;

public abstract class VoIPInstance implements Destroyable {
  // Network type

  private int netType = CallNetworkType.UNKNOWN;

  public final @CallNetworkType int getNetworkType () {
    return netType;
  }

  public void setNetworkType (@CallNetworkType int type) {
    this.netType = type;
    handleNetworkTypeChange(type);
  }

  // Configuration

  public final void setConfiguration (long packetTimeoutMs, long connectTimeoutMs, @DataSavingOption int dataSavingOption) {
    if (dataSavingOption == DataSavingOption.ROAMING) {
      dataSavingOption = U.isRoaming() ? DataSavingOption.MOBILE : DataSavingOption.NEVER;
    }
    handleConfigurationChange(packetTimeoutMs, connectTimeoutMs, dataSavingOption);
  }

  // Connection state

  protected @Nullable ConnectionStateListener connectionStateListener;

  public final void setConnectionStateListener (@Nullable ConnectionStateListener connectionStateListener){
    this.connectionStateListener = connectionStateListener;
  }

  private long callStartTime;

  protected final void dispatchCallStateChanged (@CallState int state) {
    // this.callState = state;
    if (state == CallState.ESTABLISHED && callStartTime == 0)
      callStartTime = SystemClock.elapsedRealtime();
    if (connectionStateListener != null) {
      connectionStateListener.onConnectionStateChanged(state);
    }
  }

  public static long DURATION_UNKNOWN = -1;

  public final long getCallDuration () {
    return callStartTime != 0 ? SystemClock.elapsedRealtime() - callStartTime : DURATION_UNKNOWN;
  }

  // Implementation-specific

  public abstract void initialize (
    TdApi.CallStateReady state, boolean isOutgoing,
    boolean forceTcp,
    @Nullable TdApi.InternalLinkTypeProxy proxy) throws IllegalArgumentException;

  public abstract void startAndConnect (@CallNetworkType int networkType);

  // Setters

  public abstract void setAudioOutputGainControlEnabled (boolean enabled);
  public abstract void setEchoCancellationStrength (int strength);
  public abstract void setMicDisabled (boolean isDisabled);
  protected abstract void handleNetworkTypeChange (@CallNetworkType int type);
  protected abstract void handleConfigurationChange (long packetTimeoutMs, long connectTimeoutMs, @DataSavingOption int dataSavingOption);

  // Getters

  public abstract CharSequence collectDebugLog ();
  public abstract long getConnectionId ();
  public abstract void getNetworkStats (NetworkStats out);

  public abstract String getLibraryName ();
  public abstract String getLibraryVersion ();
}
