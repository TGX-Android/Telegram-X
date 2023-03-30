package org.thunderdog.challegram.voip;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.webrtc.ContextUtils;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;

public class VoIP {
  public static TdApi.CallProtocol getProtocol () {
    Set<String> versions = new LinkedHashSet<>();
    versions.add(VoIPController.getVersion());
    Collections.addAll(versions, N.getTgCallsVersions());
    return new TdApi.CallProtocol(
      true,
      true,
      Config.VOIP_CONNECTION_MIN_LAYER,
      VoIPController.getConnectionMaxLayer(),
      versions.toArray(new String[0])
   );
  }

  private static int getNativeBufferSize (Context context) {
    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) != null) {
      int outFramesPerBuffer = StringUtils.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
      if (outFramesPerBuffer != 0) {
        return outFramesPerBuffer;
      }
    }
    return AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2;
  }

  public static void initialize (Context context) {
    ContextUtils.initialize(context);
    int bufferSize = getNativeBufferSize(context);
    VoIPController.setNativeBufferSize(bufferSize);
  }

  public static VoIPInstance instantiateAndConnect (
    Tdlib tdlib,
    TdApi.Call call,
    TdApi.CallStateReady stateReady,
    ConnectionStateListener connectionStateListener,
    boolean forceTcp,
    @Nullable Socks5Proxy proxy,
    @CallNetworkType int networkType,
    boolean audioGainControlEnabled,
    int echoCancellationStrength,
    boolean isMicDisabled
  ) throws IllegalArgumentException {
    final String libtgvoipVersion = VoIPController.getVersion();
    final String[] tgCallsVersions = N.getTgCallsVersions();

    final VoIPLogs.Pair logFiles = VoIPLogs.getNewFile(true);

    final File persistentStateFile = VoIPPersistentConfig.getVoipConfigFile();

    // These do not change during the call
    final CallConfiguration configuration = new CallConfiguration(
      stateReady,
      call.isOutgoing,

      persistentStateFile,
      logFiles != null ? logFiles.logFile : null,
      logFiles != null ? logFiles.statsLogFile : null,

      tdlib.callPacketTimeoutMs(),
      tdlib.callConnectTimeoutMs(),
      tdlib.files().getEffectiveVoipDataSavingOption(),
      forceTcp,
      proxy,

      VoIPServerConfig.getBoolean("use_system_aec", true),
      VoIPServerConfig.getBoolean("use_system_ns", true),
      VoIPServerConfig.getBoolean("voip_enable_stun_marking", false),
      VoIPServerConfig.getBoolean("enable_h265_encoder", true),
      VoIPServerConfig.getBoolean("enable_h265_decoder", true),
      VoIPServerConfig.getBoolean("enable_h264_encoder", true),
      VoIPServerConfig.getBoolean("enable_h264_decoder", true)
    );

    // These options may change during call
    final CallOptions options = new CallOptions(
      networkType,
      audioGainControlEnabled,
      echoCancellationStrength,
      isMicDisabled
    );

    VoIPInstance tgcalls = null;
    for (String version : stateReady.protocol.libraryVersions) {
      if (StringUtils.isEmpty(version)) {
        continue;
      }
      if (version.equals(libtgvoipVersion)) {
        tgcalls = new VoIPController(
          configuration,
          options,
          connectionStateListener
        );
      } else if (ArrayUtils.contains(tgCallsVersions, version)) {
        try {
          tgcalls = new TgCallsController(
            configuration,
            options,
            connectionStateListener,
            version
          );
        } catch (Throwable t) {
          Log.i("Unknown tgcalls %s", t, version);
        }
      }
      if (tgcalls != null) {
        break;
      }
    }
    if (tgcalls != null) {
      try {
        tgcalls.initializeAndConnect();
        return tgcalls;
      } catch (Throwable t) {
        Log.e("%s %s initialization failed", t,
          tgcalls.getLibraryName(),
          tgcalls.getLibraryVersion()
        );
      }
      // Make sure resources are released,
      // when call initialization has failed
      tgcalls.performDestroy();
    }
    return null;
  }
}
