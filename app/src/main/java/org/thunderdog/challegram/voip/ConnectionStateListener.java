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
