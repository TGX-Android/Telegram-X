package org.thunderdog.challegram.voip;

import org.thunderdog.challegram.telegram.TdlibManager;

import java.io.File;

public class VoIPPersistentConfig {
  static File getVoipConfigFile () {
    return new File(TdlibManager.getTgvoipDirectory(), "voip_persistent_state.json");
  }

  public static long getVoipConfigFileSize () {
    return getVoipConfigFile().length();
  }
}
