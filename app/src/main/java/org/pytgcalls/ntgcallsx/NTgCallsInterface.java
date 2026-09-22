package org.pytgcalls.ntgcallsx;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.drinkless.tdlib.TdApi;
import io.github.pytgcalls.ConnectionInfo;
import io.github.pytgcalls.ConnectionState;
import io.github.pytgcalls.FramesCallback;
import io.github.pytgcalls.NTgCalls;
import io.github.pytgcalls.RemoteSourceChangeCallback;
import io.github.pytgcalls.exceptions.ConnectionException;
import io.github.pytgcalls.exceptions.ConnectionNotFoundException;
import io.github.pytgcalls.media.AudioDescription;
import io.github.pytgcalls.media.MediaDescription;
import io.github.pytgcalls.media.MediaSource;
import io.github.pytgcalls.media.StreamMode;
import io.github.pytgcalls.media.VideoDescription;
import io.github.pytgcalls.p2p.RTCServer;

import io.github.pytgcalls.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.ConnectionStateListener;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.VoIPInstance;
import org.thunderdog.challegram.voip.annotation.CallState;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NTgCallsInterface implements CallInterface {
  private static final long CALL_ID = 0;
  private final NTgCalls ntgcalls;
  private final @Nullable AudioDescription micDescription;
  private static final int CAPTURE_WIDTH = 1920;
  private static final int CAPTURE_HEIGHT = 1080;
  private static final int AUTO_DETECT = -1;
  private final Tdlib tdlib;
  private final TdApi.Call call;

  public NTgCallsInterface (Tdlib tdlib, TdApi.Call call, TdApi.CallStateReady state, ConnectionStateListener listener) throws ConnectionException, FileNotFoundException {
    this.tdlib = tdlib;
    this.call = call;
    ntgcalls = new NTgCalls();
    ntgcalls.onSignalingData((callId, data) -> listener.onSignallingDataEmitted(data));
    ntgcalls.onConnectionChange((chatId, callNetworkState) -> {
      if (callNetworkState.state == ConnectionState.CONNECTED) {
        listener.onConnectionStateChanged(null, CallState.ESTABLISHED);
      } else if (callNetworkState.state != ConnectionState.CONNECTING) {
        listener.onConnectionStateChanged(null, CallState.FAILED);
      }
    });
    micDescription = new AudioDescription(
      MediaSource.DEVICE,
      48000,
      2,
      NTgCalls.getMediaDevices().microphone.get(0).metadata,
      true
    );
    ntgcalls.createP2pCall(CALL_ID);
    if (ContextCompat.checkSelfPermission(UI.getAppContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      throw new SecurityException("No microphone permission");
    }
    if (ContextCompat.checkSelfPermission(UI.getAppContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      throw new SecurityException("No microphone permission");
    }
    ntgcalls.setStreamSources(
      CALL_ID,
      StreamMode.CAPTURE,
      new MediaDescription(
        micDescription,
        null,
        null,
        null
      )
    );
    ntgcalls.setStreamSources(
      CALL_ID,
      StreamMode.PLAYBACK,
      new MediaDescription(
        new AudioDescription(
          MediaSource.DEVICE,
          48000,
          2,
          NTgCalls.getMediaDevices().speaker.get(0).metadata,
          true
        ),
        null,
        new VideoDescription(
          MediaSource.EXTERNAL,
          AUTO_DETECT,
          AUTO_DETECT,
          30,
          "",
          true
        ),
        null
      )
    );
    ntgcalls.skipExchange(CALL_ID, state.encryptionKey, call.isOutgoing);
    var rtcServers = Arrays.stream(state.servers)
      .map(server -> {
        if (server.type instanceof TdApi.CallServerTypeWebrtc webrtc) {
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
    ntgcalls.connectP2p(CALL_ID, rtcServers, List.of(state.protocol.libraryVersions), state.allowP2p, null);
  }

  @Override
  public boolean isVideoSupported () {
    return true;
  }

  @Override
  public void setFrameCallback (FramesCallback callback) {
    ntgcalls.onFrames(callback);
  }

  @Override
  public void setRemoteSourceChangeCallback (RemoteSourceChangeCallback callback) {
    ntgcalls.onRemoteSourceChange(callback);
  }

  @Override
  public long getCallDuration () {
    try {
      return ntgcalls.time(CALL_ID, StreamMode.PLAYBACK) * 1000;
    } catch (ConnectionNotFoundException e) {
      return VoIPInstance.DURATION_UNKNOWN;
    }
  }

  @Override
  public void setAudioOutputGainControlEnabled (boolean enabled) {

  }

  @Override
  public void handleIncomingSignalingData (byte[] data) {
    try {
      ntgcalls.sendSignalingData(CALL_ID, data);
    } catch (ConnectionException e) {
      Log.e(Log.TAG_VOIP, "Error sending signaling data", e);
    }
  }

  @Override
  public void setEchoCancellationStrength (int strength) {

  }

  @Override
  public void setMicDisabled (boolean disabled) {
    try {
      if (disabled) {
        ntgcalls.mute(CALL_ID);
      } else {
        ntgcalls.unmute(CALL_ID);
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting call settings", e);
    }
  }

  @Override
  public void setCameraEnabled (boolean enabled, boolean front) {
    try {
      if (ContextCompat.checkSelfPermission(UI.getAppContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        throw new SecurityException("No microphone permission");
      }
      if (ContextCompat.checkSelfPermission(UI.getAppContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        throw new SecurityException("No microphone permission");
      }
      if (enabled) {
        String cameraId = NTgCalls.getMediaDevices().camera.get(front ? 1 : 0).metadata;
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            new VideoDescription(
              MediaSource.DEVICE,
              CAPTURE_WIDTH,
              CAPTURE_HEIGHT,
              30,
              cameraId,
              true
            ),
            null
          )
        );
      } else {
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            null,
            null
          )
        );
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting camera", e);
    }
  }

  @Override
  public void setScreenShareEnabled (boolean enabled) {
    var size = getScreenCaptureSize();
    try {
      if (ContextCompat.checkSelfPermission(UI.getAppContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        throw new SecurityException("No microphone permission");
      }
      if (ContextCompat.checkSelfPermission(UI.getAppContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        throw new SecurityException("No microphone permission");
      }
      if (enabled) {
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            null,
            new VideoDescription(
              MediaSource.DESKTOP,
              size.x,
              size.y,
              30,
              NTgCalls.getMediaDevices().screen.get(0).metadata,
              true
            )
          )
        );
      } else {
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            null,
            null
          )
        );
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting screen share", e);
    }
  }

  @Override
  public long getConnectionId () {
    return 0;
  }

  @Override
  public void setNetworkType (int type) {

  }

  @Override
  public void getNetworkStats (NetworkStats stats) {

  }

  @Override
  public void performDestroy () {
    try {
      ntgcalls.stop(CALL_ID);
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error stopping call", e);
    }
  }

  @Override
  public CharSequence collectDebugLog () {
    return null;
  }

  @Override
  public TdApi.Call getCall () {
    return call;
  }

  @Override
  public Tdlib tdlib () {
    return tdlib;
  }

  @Override
  public boolean isInitiated () {
    return ntgcalls.calls().containsKey(CALL_ID);
  }

  @Override
  public String getLibraryName () {
    return "ntgcalls";
  }

  @Override
  public String getLibraryVersion () {
    return BuildConfig.VERSION_NAME;
  }

  private static Point getScreenCaptureSize() {
    WindowManager wm = (WindowManager) UI.getAppContext().getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    Point size = new Point();
    display.getRealSize(size);
    float aspect;
    if (size.x > size.y) {
      aspect = size.y / (float) size.x;
    } else {
      aspect = size.x / (float) size.y;
    }
    int dx = -1;
    int dy = -1;
    for (int a = 1; a <= 100; a++) {
      float val = a * aspect;
      if (val == (int) val) {
        if (size.x > size.y) {
          dx = a;
          dy = (int) (a * aspect);
        } else {
          dy = a;
          dx = (int) (a * aspect);
        }
        break;
      }
    }
    if (dx != -1 && aspect != 1) {
      while (size.x > 1000 || size.y > 1000 || size.x % 4 != 0 || size.y % 4 != 0) {
        size.x -= dx;
        size.y -= dy;
        if (size.x < 800 && size.y < 800) {
          dx = -1;
          break;
        }
      }
    }
    if (dx == -1 || aspect == 1) {
      float scale = Math.max(size.x / 970.0f, size.y / 970.0f);
      size.x = (int) Math.ceil((size.x / scale) / 4.0f) * 4;
      size.y = (int) Math.ceil((size.y / scale) / 4.0f) * 4;
    }
    return size;
  }
}
