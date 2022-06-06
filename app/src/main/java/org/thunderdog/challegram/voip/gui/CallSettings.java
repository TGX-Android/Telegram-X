/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.voip.gui;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.telegram.Tdlib;

public class CallSettings {
  public static final int SPEAKER_MODE_NONE = 0;
  public static final int SPEAKER_MODE_SPEAKER_DEFAULT = 1;
  public static final int SPEAKER_MODE_BLUETOOTH = 2;
  public static final int SPEAKER_MODE_SPEAKER = 3;

  private final Tdlib tdlib;
  private final int callId;

  private boolean micMuted;
  private int speakerMode;

  public CallSettings (Tdlib tdlib, int callId) {
    this.tdlib = tdlib;
    this.callId = callId;
  }

  public boolean isEmpty () {
    return !micMuted && speakerMode == 0;
  }

  public void setMicMuted (boolean isMuted) {
    if (this.micMuted != isMuted) {
      this.micMuted = isMuted;
      tdlib.cache().onUpdateCallSettings(callId, this);
    }
  }

  public boolean isMicMuted () {
    return micMuted;
  }

  private boolean isCallActive () {
    TdApi.Call call = tdlib.cache().getCall(callId);
    return call != null && !TD.isFinished(call);
  }

  public void setSpeakerMode (int mode) {
    if (this.speakerMode != mode && isCallActive()) {
      this.speakerMode = mode;
      tdlib.cache().onUpdateCallSettings(callId, this);
    }
  }

  public int getSpeakerMode () {
    return speakerMode;
  }

  public boolean isSpeakerModeEnabled () {
    return speakerMode != SPEAKER_MODE_NONE;
  }

  public void toggleSpeakerMode (ViewController<?> controller) {
    TGCallService voip = TGCallService.currentInstance();
    if (voip == null) {
      return;
    }

    if (voip.isBluetoothHeadsetConnected() && voip.hasEarpiece()) {
      controller.showOptions(null, new int[]{R.id.btn_routingBluetooth, R.id.btn_routingEarpiece, R.id.btn_routingSpeaker}, new String[] {Lang.getString(R.string.VoipAudioRoutingBluetooth), Lang.getString(R.string.VoipAudioRoutingEarpiece), Lang.getString(R.string.VoipAudioRoutingSpeaker)}, null, new int[] {R.drawable.baseline_bluetooth_24, R.drawable.baseline_phone_in_talk_24, R.drawable.baseline_volume_up_24}, (itemView, id) -> {
        switch (id) {
          case R.id.btn_routingBluetooth:
            setSpeakerMode(SPEAKER_MODE_BLUETOOTH);
            break;
          case R.id.btn_routingEarpiece:
            setSpeakerMode(SPEAKER_MODE_NONE);
            break;
          case R.id.btn_routingSpeaker:
            setSpeakerMode(SPEAKER_MODE_SPEAKER);
            break;
        }
        return true;
      });
      return;
    }
    setSpeakerMode(isSpeakerModeEnabled() ? SPEAKER_MODE_NONE : SPEAKER_MODE_SPEAKER_DEFAULT);
  }
}
