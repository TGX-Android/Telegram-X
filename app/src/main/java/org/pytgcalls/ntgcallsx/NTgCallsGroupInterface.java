package org.pytgcalls.ntgcallsx;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import io.github.pytgcalls.FrameCallback;
import io.github.pytgcalls.NetworkInfo;
import io.github.pytgcalls.NTgCalls;
import io.github.pytgcalls.RemoteSourceChangeCallback;
import io.github.pytgcalls.exceptions.ConnectionException;
import io.github.pytgcalls.exceptions.ConnectionNotFoundException;
import io.github.pytgcalls.media.AudioDescription;
import io.github.pytgcalls.media.MediaDescription;
import io.github.pytgcalls.media.MediaSource;
import io.github.pytgcalls.media.SsrcGroup;
import io.github.pytgcalls.media.StreamMode;
import io.github.pytgcalls.media.VideoDescription;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * NTgCalls interface for group calls (video chats).
 *
 * Flow:
 * 1. Create instance with createCall() to get join parameters
 * 2. Send join parameters to TDLib via JoinGroupCall
 * 3. Receive joinPayload from TDLib and call connect()
 * 4. Set up media streams with setStreamSources()
 */
public class NTgCallsGroupInterface {
  private static final int CAPTURE_WIDTH = 1280;
  private static final int CAPTURE_HEIGHT = 720;
  private static final int AUTO_DETECT = -1;

  private final NTgCalls ntgcalls;
  private final long chatId;
  private final Tdlib tdlib;
  private final int groupCallId;
  private @Nullable AudioDescription micDescription;

  private boolean isConnected = false;
  private boolean isMuted = true;
  private boolean isVideoEnabled = false;
  private boolean isScreenSharing = false;

  public interface GroupCallStateListener {
    void onConnectionStateChanged (NetworkInfo.State state);
    void onParticipantVideoChanged (String odpoint, boolean hasVideo);
  }

  private @Nullable GroupCallStateListener stateListener;

  /**
   * Creates a new group call interface.
   * After creation, call getJoinParameters() to get the data needed for TDLib JoinGroupCall.
   */
  public NTgCallsGroupInterface (Tdlib tdlib, long chatId, int groupCallId) throws ConnectionException, FileNotFoundException {
    this.tdlib = tdlib;
    this.chatId = chatId;
    this.groupCallId = groupCallId;
    this.ntgcalls = new NTgCalls();

    // Set up callbacks
    ntgcalls.setConnectionChangeCallback((callChatId, networkInfo) -> {
      if (callChatId == chatId && stateListener != null) {
        if (networkInfo.state == NetworkInfo.State.CONNECTED) {
          isConnected = true;
        } else if (networkInfo.state == NetworkInfo.State.CLOSED ||
                   networkInfo.state == NetworkInfo.State.FAILED) {
          isConnected = false;
        }
        stateListener.onConnectionStateChanged(networkInfo.state);
      }
    });

    // Initialize microphone description
    var devices = NTgCalls.getMediaDevices();
    if (devices.microphone != null && !devices.microphone.isEmpty()) {
      micDescription = new AudioDescription(
        MediaSource.DEVICE,
        devices.microphone.get(0).metadata,
        48000,
        2
      );
    }

    // Create the call - this prepares NTgCalls for group call connection
    ntgcalls.createCall(chatId);
  }

  public void setStateListener (GroupCallStateListener listener) {
    this.stateListener = listener;
  }

  public void setFrameCallback (FrameCallback callback) {
    ntgcalls.setFrameCallback(callback);
  }

  public void setRemoteSourceChangeCallback (RemoteSourceChangeCallback callback) {
    ntgcalls.setRemoteSourceChangeCallback(callback);
  }

  /**
   * Gets the join parameters needed for TDLib JoinGroupCall.
   * Returns a JoinParams object with audioSourceId and payload.
   */
  public JoinParams getJoinParameters () {
    try {
      String jsonParams = ntgcalls.createCall(chatId);
      JSONObject json = new JSONObject(jsonParams);
      int audioSourceId = json.optInt("audioSourceId", json.optInt("ssrc", 0));
      String payload = json.toString();
      return new JoinParams(audioSourceId, payload);
    } catch (JSONException | ConnectionException | FileNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error getting join parameters", e);
      return null;
    }
  }

  /**
   * Connects to the group call after receiving joinPayload from TDLib.
   * @param joinPayload The joinPayload from TdApi.GroupCallInfo
   * @param isPresentation True if joining as presentation (screen share only)
   */
  public void connect (String joinPayload, boolean isPresentation) throws ConnectionException {
    ntgcalls.connect(chatId, joinPayload, isPresentation);

    // Set up playback (receiving audio/video from others)
    try {
      var devices = NTgCalls.getMediaDevices();
      AudioDescription speakerDescription = null;
      if (devices.speaker != null && !devices.speaker.isEmpty()) {
        speakerDescription = new AudioDescription(
          MediaSource.DEVICE,
          devices.speaker.get(0).metadata,
          48000,
          2
        );
      }

      ntgcalls.setStreamSources(
        chatId,
        StreamMode.PLAYBACK,
        new MediaDescription(
          speakerDescription,
          null,
          new VideoDescription(
            MediaSource.EXTERNAL,
            "",
            AUTO_DETECT,
            AUTO_DETECT,
            30
          ),
          null
        )
      );
    } catch (FileNotFoundException | ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting up playback streams", e);
    }
  }

  /**
   * Adds incoming video from a participant.
   * @param endpoint The participant's endpoint ID
   * @param ssrcGroups The SSRC groups for the video
   */
  public void addIncomingVideo (String endpoint, List<SsrcGroup> ssrcGroups) {
    ntgcalls.addIncomingVideo(chatId, endpoint, ssrcGroups);
  }

  /**
   * Removes incoming video from a participant.
   * @param endpoint The participant's endpoint ID
   */
  public void removeIncomingVideo (String endpoint) {
    ntgcalls.removeIncomingVideo(chatId, endpoint);
  }

  /**
   * Sets whether the microphone is muted.
   */
  public void setMuted (boolean muted) {
    this.isMuted = muted;
    try {
      if (muted) {
        ntgcalls.mute(chatId);
      } else {
        ntgcalls.unmute(chatId);
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting mute state", e);
    }
  }

  public boolean isMuted () {
    return isMuted;
  }

  /**
   * Enables or disables camera video.
   */
  public void setCameraEnabled (boolean enabled, boolean frontCamera) {
    this.isVideoEnabled = enabled;
    this.isScreenSharing = false;

    try {
      if (enabled) {
        var devices = NTgCalls.getMediaDevices();
        if (devices.camera != null && !devices.camera.isEmpty()) {
          int cameraIndex = frontCamera ? Math.min(1, devices.camera.size() - 1) : 0;
          String cameraId = devices.camera.get(cameraIndex).metadata;

          ntgcalls.setStreamSources(
            chatId,
            StreamMode.CAPTURE,
            new MediaDescription(
              micDescription,
              null,
              new VideoDescription(
                MediaSource.DEVICE,
                cameraId,
                CAPTURE_WIDTH,
                CAPTURE_HEIGHT,
                30
              ),
              null
            )
          );
        }
      } else {
        ntgcalls.setStreamSources(
          chatId,
          StreamMode.CAPTURE,
          new MediaDescription(micDescription, null, null, null)
        );
      }
    } catch (FileNotFoundException | ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting camera", e);
    }
  }

  public boolean isVideoEnabled () {
    return isVideoEnabled;
  }

  /**
   * Enables or disables screen sharing.
   */
  public void setScreenShareEnabled (boolean enabled) {
    this.isScreenSharing = enabled;
    this.isVideoEnabled = false;

    try {
      if (enabled) {
        var devices = NTgCalls.getMediaDevices();
        if (devices.screen != null && !devices.screen.isEmpty()) {
          Point size = getScreenCaptureSize();

          ntgcalls.setStreamSources(
            chatId,
            StreamMode.CAPTURE,
            new MediaDescription(
              micDescription,
              null,
              null,
              new VideoDescription(
                MediaSource.DESKTOP,
                devices.screen.get(0).metadata,
                size.x,
                size.y,
                30
              )
            )
          );
        }
      } else {
        ntgcalls.setStreamSources(
          chatId,
          StreamMode.CAPTURE,
          new MediaDescription(micDescription, null, null, null)
        );
      }
    } catch (FileNotFoundException | ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting screen share", e);
    }
  }

  public boolean isScreenSharing () {
    return isScreenSharing;
  }

  public boolean isConnected () {
    return isConnected;
  }

  public int getGroupCallId () {
    return groupCallId;
  }

  public Tdlib tdlib () {
    return tdlib;
  }

  /**
   * Stops the group call and releases resources.
   */
  public void stop () {
    try {
      ntgcalls.stop(chatId);
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error stopping group call", e);
    }
    isConnected = false;
  }

  /**
   * Join parameters to send to TDLib.
   */
  public static class JoinParams {
    public final int audioSourceId;
    public final String payload;

    public JoinParams (int audioSourceId, String payload) {
      this.audioSourceId = audioSourceId;
      this.payload = payload;
    }
  }

  private static Point getScreenCaptureSize () {
    WindowManager wm = (WindowManager) UI.getAppContext().getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    Point size = new Point();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      display.getRealSize(size);
    }

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
