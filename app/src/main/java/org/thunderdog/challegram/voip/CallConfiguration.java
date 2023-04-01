package org.thunderdog.challegram.voip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.voip.annotation.DataSavingOption;

import java.io.File;

public class CallConfiguration {
  public final TdApi.CallStateReady state;
  public final boolean isOutgoing;

  public final @NonNull String persistentStateFilePath;
  public final @Nullable String logFilePath;
  public final @Nullable String statsLogFilePath;

  public final long packetTimeoutMs;
  public final long connectTimeoutMs;
  public final @DataSavingOption int dataSavingOption;

  public final boolean forceTcp;
  public final @Nullable Socks5Proxy proxy;

  public final boolean enableAcousticEchoCanceler;
  public final boolean enableNoiseSuppressor;
  public final boolean enableAutomaticGainControl;

  public final boolean enableStunMarking;
  public final boolean enableH265Encoder, enableH265Decoder;
  public final boolean enableH264Encoder, enableH264Decoder;

  public CallConfiguration (
    @NonNull TdApi.CallStateReady state,
    boolean isOutgoing,
    @NonNull File persistentStateFile,
    @Nullable File logFile,
    @Nullable File statsLogFile,
    long packetTimeoutMs, long connectTimeoutMs, int dataSavingOption,
    boolean forceTcp,
    @Nullable Socks5Proxy proxy,
    boolean enableAcousticEchoCanceler,
    boolean enableNoiseSuppressor,
    boolean enableAutomaticGainControl,
    boolean enableStunMarking,
    boolean enableH265Encoder,
    boolean enableH265Decoder,
    boolean enableH264Encoder,
    boolean enableH264Decoder
  ) {
    this.state = state;
    this.isOutgoing = isOutgoing;

    this.persistentStateFilePath = persistentStateFile.getAbsolutePath();
    this.logFilePath = logFile != null ? logFile.getAbsolutePath() : null;
    this.statsLogFilePath = statsLogFile != null ? statsLogFile.getAbsolutePath() : null;

    this.packetTimeoutMs = packetTimeoutMs;
    this.connectTimeoutMs = connectTimeoutMs;
    this.dataSavingOption = dataSavingOption;
    this.forceTcp = forceTcp;
    this.proxy = proxy;

    this.enableAcousticEchoCanceler = enableAcousticEchoCanceler;
    this.enableNoiseSuppressor = enableNoiseSuppressor;
    this.enableAutomaticGainControl = enableAutomaticGainControl;

    this.enableStunMarking = enableStunMarking;
    this.enableH265Encoder = enableH265Encoder;
    this.enableH265Decoder = enableH265Decoder;
    this.enableH264Encoder = enableH264Encoder;
    this.enableH264Decoder = enableH264Decoder;
  }
}
