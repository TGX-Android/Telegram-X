package org.thunderdog.challegram.voip;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.telegram.TdlibManager;

import java.io.File;

public class VoIPPersistentConfig {
  static @NonNull File getVoipConfigFile () {
    return new File(TdlibManager.getTgvoipDirectory(), "voip_persistent_state.json");
  }

  public static long getVoipConfigFileSize () {
    File file = getVoipConfigFile();
    return file.exists() ? file.length() : 0;
  }
}
