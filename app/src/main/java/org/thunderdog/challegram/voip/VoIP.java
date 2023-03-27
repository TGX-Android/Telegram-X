package org.thunderdog.challegram.voip;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.config.Config;

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

  public static String getPrimaryVersion () {
    // libtgvoip 2.4.4 - VoIPController.h:43
    return "libtgvoip " + VoIPController.getVersion();
  }

  public static void initialize (Context context) {
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
}
