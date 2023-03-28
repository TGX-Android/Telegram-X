package org.thunderdog.challegram.voip;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.voip.annotation.DataSavingOption;

import java.io.File;

public class CallConfiguration {
  public final TdApi.CallStateReady state;
  public final boolean isOutgoing;

  public final @NonNull String persistentStateFilePath;
  public final @Nullable String callLogFilePath;

  public final long packetTimeoutMs;
  public final long connectTimeoutMs;
  public final @DataSavingOption int dataSavingOption;

  public final boolean forceTcp;
  public final @Nullable Socks5Proxy proxy;

  public final boolean useSystemAcousticEchoCanceler;
  public final boolean useSystemNoiseSuppressor;

  public CallConfiguration (
    @NonNull TdApi.CallStateReady state,
    boolean isOutgoing,
    @NonNull File persistentStateFile,
    @Nullable File callLogFile,
    long packetTimeoutMs, long connectTimeoutMs, int dataSavingOption,
    boolean forceTcp,
    @Nullable Socks5Proxy proxy,
    boolean useSystemAcousticEchoCanceler,
    boolean useSystemNoiseSuppressor
  ) {
    this.state = state;
    this.isOutgoing = isOutgoing;

    this.persistentStateFilePath = persistentStateFile.getAbsolutePath();
    this.callLogFilePath = callLogFile != null ? callLogFile.getAbsolutePath() : null;

    this.packetTimeoutMs = packetTimeoutMs;
    this.connectTimeoutMs = connectTimeoutMs;
    this.dataSavingOption = dataSavingOption;
    this.forceTcp = forceTcp;
    this.proxy = proxy;

    this.useSystemAcousticEchoCanceler = useSystemAcousticEchoCanceler && isSystemAcousticEchoCancelerAvailable();
    this.useSystemNoiseSuppressor = useSystemNoiseSuppressor && isSystemNoiseSuppressorAvailable();
  }

  public static boolean isSystemAcousticEchoCancelerAvailable () {
    try {
      return AcousticEchoCanceler.isAvailable();
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static boolean isSystemNoiseSuppressorAvailable () {
    try {
      return NoiseSuppressor.isAvailable();
    } catch (Throwable ignored) {
      return false;
    }
  }
}
