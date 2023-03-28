package org.thunderdog.challegram.voip;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.Tdlib;
import org.webrtc.ContextUtils;

import me.vkryl.core.StringUtils;

public class VoIP {
  public static TdApi.CallProtocol getProtocol () {
    return new TdApi.CallProtocol(
      true,
      true,
      Config.VOIP_CONNECTION_MIN_LAYER,
      VoIPController.getConnectionMaxLayer(),
      new String[] {
        VoIPController.getVersion()
      }
     );
  }

  public static void initialize (Context context) {
    ContextUtils.initialize(context);
    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    boolean success = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) != null) {
      int outFramesPerBuffer = StringUtils.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
      if (outFramesPerBuffer != 0) {
        VoIPController.setNativeBufferSize(outFramesPerBuffer);
        success = true;
      }
    }
    if (!success) {
      VoIPController.setNativeBufferSize(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2);
    }
  }

  public static VoIPInstance newInstance (Tdlib tdlib, TdApi.CallStateReady stateReady, ConnectionStateListener connectionStateListener) {
    VoIPInstance tgcalls = null;
    String libtgvoipVersion = VoIPController.getVersion();
    for (String version : stateReady.protocol.libraryVersions) {
      if (StringUtils.isEmpty(version)) {
        continue;
      }
      switch (version) {
        case "2.4.4":
          tgcalls = new VoIPController();
          // TODO? InstanceImplLegacy.cpp
          break;
        case "2.7.7":
        case "5.0.0":
          // TODO InstanceImpl.cpp
          break;
        case "6.0.0":
          // TODO InstanceV2_4_0_0Impl.cpp
          break;
        case "7.0.0":
        case "8.0.0":
        case "9.0.0":
          // TODO InstanceV2Impl.cpp
          break;
        case "10.0.0":
        case "11.0.0":
          // TODO InstanceV2ReferenceImpl.cpp
          break;
        default:
          if (version.equals(libtgvoipVersion)) {
            tgcalls = new VoIPController();
          } else {
            // Unknown version: version
          }
          break;
      }
    }
    if (tgcalls != null) {
      tgcalls.setConnectionStateListener(connectionStateListener);
      tgcalls.setConfiguration(
        tdlib.callPacketTimeoutMs(),
        tdlib.callConnectTimeoutMs(),
        tdlib.files().getVoipDataSavingOption()
      );
    }
    return tgcalls;
  }
}
