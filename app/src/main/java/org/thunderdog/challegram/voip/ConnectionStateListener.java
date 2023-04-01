package org.thunderdog.challegram.voip;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.voip.annotation.AudioState;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.annotation.VideoState;

public interface ConnectionStateListener {
  default void onConnectionStateChanged (VoIPInstance context, @CallState int newState) { }

  default void onSignalBarCountChanged (int newCount) { }

  default void onStopped (VoIPInstance releasedContext, NetworkStats finalStats, @Nullable String debugLog) { }

  default void onRemoteMediaStateChanged (VoIPInstance context, @AudioState int audioState, @VideoState int videoState) { }

  default void onSignallingDataEmitted (byte[] data) { }

  default void onGroupCallKeyReceived (byte[] key) { }

  default void onGroupCallKeySent () { }

  default void onCallUpgradeRequestReceived () { }
}
