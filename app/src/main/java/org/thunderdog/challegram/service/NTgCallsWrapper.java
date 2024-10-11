package org.thunderdog.challegram.service;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.pytgcalls.ntgcalls.NTgCalls;
import org.pytgcalls.ntgcalls.exceptions.ConnectionException;
import org.pytgcalls.ntgcalls.exceptions.ConnectionNotFoundException;
import org.pytgcalls.ntgcalls.media.AudioDescription;
import org.pytgcalls.ntgcalls.media.MediaDescription;
import org.pytgcalls.ntgcalls.media.MediaSource;
import org.pytgcalls.ntgcalls.media.StreamMode;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.VoIPInstance;
import org.thunderdog.challegram.voip.gui.CallSettings;

import java.util.Arrays;

public class NTgCallsWrapper implements CallInterface {
  private static @Nullable NTgCalls call;
  private final long CALL_ID = 0;

  public NTgCallsWrapper () {
    if (call == null) {
      call = new NTgCalls();
    }
    try {
      call.createP2PCall(
        CALL_ID,
        new MediaDescription(
          new AudioDescription(
            MediaSource.DEVICE,
            NTgCalls.getMediaDevices().audio.get(0).metadata,
            96000,
            16,
            2
          ),
          null,
          null,
          null
        )
      );
      call.setStreamSources(
        CALL_ID,
        StreamMode.PLAYBACK,
        new MediaDescription(
          new AudioDescription(
            MediaSource.DEVICE,
            NTgCalls.getMediaDevices().audio.get(1).metadata,
            96000,
            16,
            2
          ),
          null,
          null,
          null
        )
      );
      call.skipExchange(CALL_ID, state.encryptionKey, tgCall.isOutgoing);
      var rtcServers = Arrays.stream(state.servers)
        .map(server -> {
          if (server.type instanceof TdApi.CallServerTypeWebrtc) {
            var webrtc = (TdApi.CallServerTypeWebrtc) server.type;
            return new RTCServer(
              server.id,
              server.ipAddress,
              server.ipv6Address,
              server.port,
              webrtc.username,
              webrtc.password,
              webrtc.supportsTurn,
              webrtc.supportsStun,
              false,
              null
            );
          } else {
            var reflector = (TdApi.CallServerTypeTelegramReflector) server.type;
            return new RTCServer(
              server.id,
              server.ipAddress,
              server.ipv6Address,
              server.port,
              null,
              null,
              true,
              false,
              reflector.isTcp,
              reflector.peerTag
            );
          }
        }).collect(Collectors.toList());
      ntgcalls.connectP2P(CALL_ID, rtcServers, List.of(state.protocol.libraryVersions), state.allowP2p);
    } catch (java.lang.Exception e) {
      throw new RuntimeException(e);
    }
  }

  public long getCallDuration () {
    try {
      return call != null ? call.time(CALL_ID, StreamMode.PLAYBACK) * 1000 : VoIPInstance.DURATION_UNKNOWN;
    } catch (ConnectionNotFoundException e) {
      return VoIPInstance.DURATION_UNKNOWN;
    }
  }

  public String getLibraryNameAndVersion () {
    return "ntgcalls";
  }

  public String getDebugString () {
    return "";
  }

  public void handleIncomingSignalingData (byte[] data) {
    if (call != null) {
      try {
        call.sendSignalingData(CALL_ID, data);
      } catch (ConnectionException e) {
        Log.e(Log.TAG_VOIP, "Error sending signaling data", e);
      }
    }
  }

  public long getConnectionId () {
    return 0;
  }

  public void getNetworkStats (NetworkStats stats) {}

  public void getMicrophoneStatus (CallSettings settings) {
    try {
      if (call != null) {
        if (settings != null && settings.isMicMuted()) {
          call.mute(CALL_ID);
        } else {
          call.unmute(CALL_ID);
        }
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting call settings", e);
    }
  }

  public boolean isInitiated () {
    return call != null && call.calls().containsKey(CALL_ID);
  }

  public void releaseTgCalls () {
    if (call != null && !call.calls().isEmpty()) {
      try {
        call.stop(CALL_ID);
      } catch (ConnectionNotFoundException e) {
        Log.e(Log.TAG_VOIP, "Error releasing tg calls", e);
      }
    }
  }

  public void callAudioControl (boolean audioGainControlEnabled, int echoCancellationStrength) {}

  public void setNetworkType (boolean dispatchToTgCalls, int type) {}

  public CharSequence collectDebugLog () {
    return null;
  }

  public void stop () {
    if (call != null && !call.calls().isEmpty()) {
      try {
        call.stop(CALL_ID);
      } catch (ConnectionNotFoundException e) {
        Log.e(Log.TAG_VOIP, "Error releasing tg calls", e);
      }
    }
  }

  public void setSignalingDataCallback(byte[] o) {

  }

  public void createCall() {

  }
}
