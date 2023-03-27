package org.thunderdog.challegram.voip;

import org.thunderdog.challegram.voip.annotation.CallState;

public interface ConnectionStateListener {
  default void onConnectionStateChanged (@CallState int newState) { }

  default void onSignalBarCountChanged (int newCount) { }

  default void onGroupCallKeyReceived (byte[] key) { }

  default void onGroupCallKeySent () { }

  default void onCallUpgradeRequestReceived () { }
}
