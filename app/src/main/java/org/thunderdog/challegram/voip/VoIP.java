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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;

public class VoIP {
  public static class Version implements Comparable<Version> {
    public final int major, minor, patch;

    public Version (String version) {
      int firstDot = version.indexOf('.');
      if (firstDot == -1) {
        this.major = StringUtils.parseInt(version);
        this.minor = this.patch = 0;
        return;
      }
      this.major = StringUtils.parseInt(version.substring(0, firstDot));
      int secondDot = version.indexOf('.', firstDot + 1);
      if (secondDot == -1) {
        this.minor = StringUtils.parseInt(version.substring(firstDot + 1));
        this.patch = 0;
      } else {
        this.minor = StringUtils.parseInt(version.substring(firstDot + 1, secondDot));
        this.patch = StringUtils.parseInt(version.substring(secondDot + 1));
      }
    }

    @Override
    public int compareTo (Version o) {
      return
        this.major != o.major ? Integer.compare(this.major, o.major) :
        this.minor != o.minor ? Integer.compare(this.minor, o.minor) :
        Integer.compare(this.patch, o.patch);
    }

    public Version (int major, int minor, int patch) {
      this.major = major;
      this.minor = minor;
      this.patch = patch;
    }
  }

  private static Set<String> forceDisabledVersions;

  public static void setForceDisableVersion (String version, boolean isForceDisabled) {
    if (isForceDisabled) {
      if (forceDisabledVersions == null) {
        forceDisabledVersions = new HashSet<>();
      }
      forceDisabledVersions.add(version);
    } else if (forceDisabledVersions != null) {
      forceDisabledVersions.remove(version);
    }
  }

  public static boolean isForceDisabled (String version) {
    return forceDisabledVersions != null && forceDisabledVersions.contains(version);
  }

  private static boolean forceDisableAcousticEchoCancellation, forceDisableNoiseSuppressor, forceDisableAutomaticGainControl;

  public static void setForceDisableAcousticEchoCancellation (boolean forceDisableAcousticEchoCancellation) {
    VoIP.forceDisableAcousticEchoCancellation = forceDisableAcousticEchoCancellation;
  }

  public static void setForceDisableNoiseSuppressor (boolean forceDisableNoiseSuppressor) {
    VoIP.forceDisableNoiseSuppressor = forceDisableNoiseSuppressor;
  }

  public static void setForceDisableAutomaticGainControl (boolean forceDisableAutomaticGainControl) {
    VoIP.forceDisableAutomaticGainControl = forceDisableAutomaticGainControl;
  }

  public static boolean needDisableAcousticEchoCancellation () {
    return forceDisableAcousticEchoCancellation;
  }

  public static boolean needDisableNoiseSuppressor () {
    return forceDisableNoiseSuppressor;
  }

  public static boolean needDisableAutomaticGainControl () {
    return forceDisableAutomaticGainControl;
  }

  public static String[] getAvailableVersions (boolean allowFilter) {
    String tgVoipVersion = VoIPController.getVersion();
    String[] tgCallsVersions = N.getTgCallsVersions();

    Set<String> versions = new LinkedHashSet<>();
    if (!allowFilter || !isForceDisabled(tgVoipVersion)) {
      versions.add(tgVoipVersion);
    }
    for (String tgCallsVersion : tgCallsVersions) {
      if (!allowFilter || !isForceDisabled(tgCallsVersion)) {
        versions.add(tgCallsVersion);
      }
    }
    if (versions.isEmpty()) {
      versions.add(tgVoipVersion);
    }
    return versions.toArray(new String[0]);
  }

  public static TdApi.CallProtocol getProtocol () {
    return new TdApi.CallProtocol(
      true,
      true,
      Config.VOIP_CONNECTION_MIN_LAYER,
      VoIPController.getConnectionMaxLayer(),
      getAvailableVersions(true)
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

    final boolean preferSystemAcousticEchoCanceler = VoIPServerConfig.getBoolean("use_system_aec", true);
    final boolean preferSystemNoiseSuppressor = VoIPServerConfig.getBoolean("use_system_ns", true);

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

      !preferSystemAcousticEchoCanceler,
      !preferSystemNoiseSuppressor,
      true,
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
      if (version.equals(libtgvoipVersion) && (Config.FORCE_DIRECT_TGVOIP || !ArrayUtils.contains(tgCallsVersions, version) || isForceDisabled(version))) {
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
