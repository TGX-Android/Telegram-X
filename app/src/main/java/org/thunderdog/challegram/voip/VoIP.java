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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.voip.annotation.CallNetworkType;
import org.webrtc.ContextUtils;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Filter;
import tgx.td.Td;

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
    if (forceDisabledVersions != null) {
      return forceDisabledVersions.contains(version);
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      return !version.equals(VoIPController.getVersion());
    } else {
      return false;
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    DebugOption.DISABLE_ACOUSTIC_ECHO_CANCELLATION,
    DebugOption.DISABLE_NOISE_SUPPRESSOR,
    DebugOption.DISABLE_AUTOMATIC_GAIN_CONTROL,
    DebugOption.DISABLE_P2P,
    DebugOption.DISABLE_IPV4,
    DebugOption.ONLY_TELEGRAM_REFLECTOR_SERVERS,
    DebugOption.ONLY_WEBRTC_SERVERS,
    DebugOption.ONLY_WEBRTC_TURN_SERVERS,
    DebugOption.ONLY_WEBRTC_NON_TURN_SERVERS,
    DebugOption.TEST_INVALID_SERVERS
  }, flag = true)
  public @interface DebugOption {
    int
      DISABLE_ACOUSTIC_ECHO_CANCELLATION = 1,
      DISABLE_NOISE_SUPPRESSOR = 1 << 1,
      DISABLE_AUTOMATIC_GAIN_CONTROL = 1 << 2,
      DISABLE_P2P = 1 << 3,
      DISABLE_IPV4 = 1 << 4,
      ONLY_TELEGRAM_REFLECTOR_SERVERS = 1 << 5,
      ONLY_WEBRTC_SERVERS = 1 << 6,
      ONLY_WEBRTC_TURN_SERVERS = 1 << 7,
      ONLY_WEBRTC_NON_TURN_SERVERS = 1 << 8,
      TEST_INVALID_SERVERS = 1 << 9;

    @DebugOption int SERVER_FILTERS_MASK =
      ONLY_TELEGRAM_REFLECTOR_SERVERS |
      ONLY_WEBRTC_SERVERS |
      ONLY_WEBRTC_TURN_SERVERS |
      ONLY_WEBRTC_NON_TURN_SERVERS;
  }
  private static @DebugOption int debugOptions;

  public static boolean isDebugOptionEnabled (@DebugOption int options) {
    return BitwiseUtils.hasFlag(debugOptions, options);
  }

  public static void setDebugOptionEnabled (@DebugOption int option, boolean isEnabled) {
    debugOptions = BitwiseUtils.setFlag(debugOptions, option, isEnabled);
  }

  public static int[] getAllDebugOptions () {
    return new int[] {
      DebugOption.DISABLE_ACOUSTIC_ECHO_CANCELLATION,
      DebugOption.DISABLE_NOISE_SUPPRESSOR,
      DebugOption.DISABLE_AUTOMATIC_GAIN_CONTROL,
      DebugOption.DISABLE_IPV4,
      DebugOption.DISABLE_P2P,
      DebugOption.ONLY_TELEGRAM_REFLECTOR_SERVERS,
      DebugOption.ONLY_WEBRTC_SERVERS,
      DebugOption.ONLY_WEBRTC_TURN_SERVERS,
      DebugOption.ONLY_WEBRTC_NON_TURN_SERVERS,
      DebugOption.TEST_INVALID_SERVERS
    };
  }

  public static String getDebugOptionName (@DebugOption int option) {
    switch (option) {
      case DebugOption.DISABLE_ACOUSTIC_ECHO_CANCELLATION:
        return "Disable AEC";
      case DebugOption.DISABLE_NOISE_SUPPRESSOR:
        return "Disable NS";
      case DebugOption.DISABLE_AUTOMATIC_GAIN_CONTROL:
        return "Disable AGC";
      case DebugOption.DISABLE_IPV4:
        return "Disable ipv4";
      case DebugOption.DISABLE_P2P:
        return "Disable P2P";
      case DebugOption.ONLY_TELEGRAM_REFLECTOR_SERVERS:
        return "Only TelegramReflector servers (crash if none)";
      case DebugOption.ONLY_WEBRTC_SERVERS:
        return "Only WebRTC servers (crash if none)";
      case DebugOption.ONLY_WEBRTC_TURN_SERVERS:
        return "Only TURN servers (crash if none)";
      case DebugOption.ONLY_WEBRTC_NON_TURN_SERVERS:
        return "Only non-TURN servers (crash if none)";
      case DebugOption.TEST_INVALID_SERVERS:
        return "Test handling non-existing servers";
    }
    return null;
  }

  private static String toHexString (byte[] array) {
    StringBuilder result = new StringBuilder(array.length * 2);
    for (byte b : array) {
      result
        .append(Character.forDigit((b >> 4) & 0xF, 16))
        .append(Character.forDigit(b & 0xF, 16));
    }
    return result.toString();
  }

  private static void validateModifiedCallServer (TdApi.CallServer server) {
    if (server.type.getConstructor() == TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR) {
      TdApi.CallServerTypeTelegramReflector telegramReflector = (TdApi.CallServerTypeTelegramReflector) server.type;
      String myHex = N.toHexString(telegramReflector.peerTag);
      String validHex = toHexString(telegramReflector.peerTag);
      if (!myHex.equals(validHex))
        throw new UnsupportedOperationException(myHex + " != " + validHex);
    }
  }

  public static boolean needModifyCallServers () {
    return isDebugOptionEnabled(
      DebugOption.DISABLE_IPV4 |
        DebugOption.TEST_INVALID_SERVERS |
        DebugOption.SERVER_FILTERS_MASK
    );
  }

  public static TdApi.CallServer[] modifyCallServers (TdApi.CallServer[] servers) {
    if (!needModifyCallServers()) {
      return servers;
    }
    final boolean testInvalidServers = isDebugOptionEnabled(DebugOption.TEST_INVALID_SERVERS);
    final boolean disableIpv4 = isDebugOptionEnabled(DebugOption.DISABLE_IPV4);
    final int filterOption = debugOptions & DebugOption.SERVER_FILTERS_MASK;
    final Filter<TdApi.CallServer> filter;
    switch (filterOption) {
      case DebugOption.ONLY_TELEGRAM_REFLECTOR_SERVERS:
        filter = server -> server.type.getConstructor() == TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR;
        break;
      case DebugOption.ONLY_WEBRTC_SERVERS:
        filter = server -> server.type.getConstructor() == TdApi.CallServerTypeWebrtc.CONSTRUCTOR;
        break;
      case DebugOption.ONLY_WEBRTC_TURN_SERVERS:
        filter = server ->
          server.type.getConstructor() == TdApi.CallServerTypeWebrtc.CONSTRUCTOR &&
          ((TdApi.CallServerTypeWebrtc) server.type).supportsTurn;
        break;
      case DebugOption.ONLY_WEBRTC_NON_TURN_SERVERS:
        filter = server ->
          server.type.getConstructor() == TdApi.CallServerTypeWebrtc.CONSTRUCTOR &&
            !((TdApi.CallServerTypeWebrtc) server.type).supportsTurn;
        break;
      default:
        Td.assertCallServerType_569fa9f7();
        filter = null;
        break;
    }
    int telegramReflectorCount = 0;
    int webrtcReflectorCount = 0;
    List<TdApi.CallServer> filteredCallServers = new ArrayList<>();
    for (TdApi.CallServer server : servers) {
      if (filter != null && !filter.accept(server)) {
        continue;
      }
      if (disableIpv4) {
        if (StringUtils.isEmpty(server.ipv6Address))
          continue;
        server = new TdApi.CallServer(server.id, "", server.ipv6Address, server.port, server.type);
      }
      validateModifiedCallServer(server);
      switch (server.type.getConstructor()) {
        case TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR:
          telegramReflectorCount++;
          break;
        case TdApi.CallServerTypeWebrtc.CONSTRUCTOR:
          webrtcReflectorCount++;
          break;
        default:
          Td.assertCallServerType_569fa9f7();
          throw Td.unsupported(server.type);
      }
      filteredCallServers.add(server);
    }
    if (filteredCallServers.isEmpty()) {
      throw new IllegalStateException();
    }
    if (testInvalidServers) {
      int remainingWebrtcReflectors = webrtcReflectorCount;
      int remainingTelegramReflectors = telegramReflectorCount;
      for (int i = 0; i < filteredCallServers.size(); i++) {
        TdApi.CallServer server = filteredCallServers.get(i);
        boolean makeInvalid;
        switch (server.type.getConstructor()) {
          case TdApi.CallServerTypeTelegramReflector.CONSTRUCTOR:
            makeInvalid = --remainingTelegramReflectors > 0;
            break;
          case TdApi.CallServerTypeWebrtc.CONSTRUCTOR:
            makeInvalid = --remainingWebrtcReflectors > 0;
            break;
          default:
            Td.assertCallServerType_569fa9f7();
            throw Td.unsupported(server.type);
        }
        if (makeInvalid) {
          server = new TdApi.CallServer(server.id,
            "192.168.0.0",
            "0:0:0:0:0:ffff:c0a8:0",
            server.port,
            server.type
          );
          filteredCallServers.set(i, server);
        }
      }
    }
    return filteredCallServers.toArray(new TdApi.CallServer[0]);
  }

  public static String[] getAvailableVersions (boolean allowFilter) {
    String tgVoipVersion = VoIPController.getVersion();
    String[] tgCallsVersions = N.getTgCallsVersions();

    Set<String> versions = new LinkedHashSet<>();
    if (!allowFilter || !isForceDisabled(tgVoipVersion)) {
      versions.add(tgVoipVersion);
    }
    Set<String> restrictedTgCallsVersions = new LinkedHashSet<>() {{
      add("11.0.0");
    }};
    for (String tgCallsVersion : tgCallsVersions) {
      if (restrictedTgCallsVersions.contains(tgCallsVersion))
        continue;
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
    tdlib.storeCallLogInformation(call, logFiles);

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
          tdlib,
          call,
          configuration,
          options,
          connectionStateListener
        );
      } else if (ArrayUtils.contains(tgCallsVersions, version)) {
        try {
          tgcalls = new TgCallsController(
            tdlib,
            call,
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
