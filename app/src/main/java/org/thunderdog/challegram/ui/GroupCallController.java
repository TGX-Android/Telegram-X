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
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import io.github.pytgcalls.NetworkInfo;
import io.github.pytgcalls.devices.JavaVideoCapturerModule;
import io.github.pytgcalls.media.Frame;
import io.github.pytgcalls.media.FrameData;
import io.github.pytgcalls.media.StreamDevice;
import io.github.pytgcalls.media.StreamMode;
import io.github.pytgcalls.media.StreamStatus;
import org.pytgcalls.ntgcallsx.NTgCallsGroupInterface;
import org.pytgcalls.ntgcallsx.VoIPFloatingLayout;
import org.pytgcalls.ntgcallsx.VoIPTextureView;
import org.webrtc.JavaI420Buffer;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.GroupCallListener;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.CircleButton;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

/**
 * Controller for group voice/video calls (video chats).
 */
public class GroupCallController extends ViewController<GroupCallController.Arguments>
  implements View.OnClickListener, GroupCallListener, NTgCallsGroupInterface.GroupCallStateListener {

  public static class Arguments {
    public final NTgCallsGroupInterface groupInterface;
    public final TdApi.GroupCall groupCall;
    public final String title;

    public Arguments (NTgCallsGroupInterface groupInterface, TdApi.GroupCall groupCall, String title) {
      this.groupInterface = groupInterface;
      this.groupCall = groupCall;
      this.title = title;
    }
  }

  private NTgCallsGroupInterface groupInterface;
  private TdApi.GroupCall groupCall;
  private String callTitle;

  // UI elements
  private FrameLayoutFix contentView;
  private LinearLayout controlsLayout;
  private CircleButton muteButton;
  private CircleButton cameraButton;
  private CircleButton screenShareButton;
  private CircleButton raiseHandButton;
  private CircleButton inviteButton;
  private CircleButton leaveButton;
  private android.widget.TextView titleView;
  private android.widget.TextView subtitleView;
  private RecyclerView participantsList;
  private ParticipantsAdapter participantsAdapter;

  // Video UI elements
  private VoIPFloatingLayout localCameraFloatingLayout;
  private VoIPTextureView localCameraTextureView;
  private FrameLayout videoGridContainer;
  private RecyclerView videoGrid;
  private VideoParticipantsAdapter videoAdapter;

  // State
  private boolean isMuted = true;
  private boolean isCameraEnabled = false;
  private boolean isScreenSharing = false;
  private boolean isFrontCamera = true;
  private boolean isHandRaised = false;
  private List<TdApi.GroupCallParticipant> participants = new ArrayList<>();
  private List<VideoParticipant> videoParticipants = new ArrayList<>();

  // Video participant tracking
  private static class VideoParticipant {
    String endpoint;
    TdApi.GroupCallParticipant participant;
    VoIPTextureView textureView;
    boolean hasVideo;

    VideoParticipant (String endpoint, TdApi.GroupCallParticipant participant) {
      this.endpoint = endpoint;
      this.participant = participant;
      this.hasVideo = false;
    }
  }

  public GroupCallController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_groupCall;
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.groupInterface = args.groupInterface;
    this.groupCall = args.groupCall;
    this.callTitle = args.title != null ? args.title : "Voice Chat";

    if (groupInterface != null) {
      groupInterface.setStateListener(this);
      setupVideoCallbacks();
    }

    // Subscribe to group call updates
    if (groupCall != null) {
      tdlib.listeners().subscribeToGroupCallUpdates(groupCall.id, this);
    }
  }

  private void setupVideoCallbacks () {
    if (groupInterface == null) return;

    // Set up remote source change callback to detect when participants enable/disable video
    groupInterface.setRemoteSourceChangeCallback((chatId, remoteSource) -> {
      UI.post(() -> {
        if (remoteSource.device == StreamDevice.CAMERA || remoteSource.device == StreamDevice.SCREEN) {
          String endpoint = remoteSource.ssrc > 0 ? String.valueOf(remoteSource.ssrc) : "";
          boolean hasVideo = remoteSource.state == StreamStatus.ACTIVE;

          if (hasVideo) {
            onParticipantVideoEnabled(endpoint);
          } else {
            onParticipantVideoDisabled(endpoint);
          }
        }
      });
    });

    // Set up frame callback to receive video frames
    groupInterface.setFrameCallback((chatId, streamMode, streamDevice, frames) -> {
      boolean isVideo = streamDevice == StreamDevice.CAMERA || streamDevice == StreamDevice.SCREEN;
      if (!isVideo) return;

      for (Frame frame : frames) {
        byte[] bytes = frame.data;
        FrameData frameData = frame.frameData;
        int ySize = frameData.width * frameData.height;
        int uvSize = ySize / 4;
        var i420Buffer = JavaI420Buffer.allocate(frameData.width, frameData.height);
        i420Buffer.getDataY().put(bytes, 0, ySize).flip();
        i420Buffer.getDataU().put(bytes, ySize, uvSize).flip();
        i420Buffer.getDataV().put(bytes, ySize + uvSize, uvSize).flip();
        VideoFrame videoFrame = new VideoFrame(i420Buffer, frameData.rotation, System.nanoTime());

        switch (streamMode) {
          case CAPTURE:
            // Local camera preview
            if (localCameraTextureView != null && isCameraEnabled) {
              localCameraTextureView.onFrame(videoFrame);
            }
            break;
          case PLAYBACK:
            // Remote participant video - route to appropriate texture view
            // For now, this would need endpoint info to route correctly
            // The frame callback doesn't provide endpoint, so we handle this via
            // onParticipantVideoChanged for visibility, and frames are routed by endpoint
            break;
        }
        i420Buffer.release();
      }
    });
  }

  private void onParticipantVideoEnabled (String endpoint) {
    // Find or create video participant entry
    for (VideoParticipant vp : videoParticipants) {
      if (vp.endpoint.equals(endpoint)) {
        vp.hasVideo = true;
        updateVideoGrid();
        return;
      }
    }
    // New video participant
    VideoParticipant vp = new VideoParticipant(endpoint, null);
    vp.hasVideo = true;
    videoParticipants.add(vp);
    updateVideoGrid();
  }

  private void onParticipantVideoDisabled (String endpoint) {
    for (int i = 0; i < videoParticipants.size(); i++) {
      if (videoParticipants.get(i).endpoint.equals(endpoint)) {
        videoParticipants.get(i).hasVideo = false;
        updateVideoGrid();
        return;
      }
    }
  }

  private void updateVideoGrid () {
    if (videoGridContainer == null) return;

    // Count active video participants
    int activeCount = 0;
    for (VideoParticipant vp : videoParticipants) {
      if (vp.hasVideo) activeCount++;
    }

    // Show/hide video grid based on whether anyone has video enabled
    boolean showVideoGrid = activeCount > 0 || isCameraEnabled;
    videoGridContainer.setVisibility(showVideoGrid ? View.VISIBLE : View.GONE);
    participantsList.setVisibility(showVideoGrid ? View.GONE : View.VISIBLE);

    if (videoAdapter != null) {
      videoAdapter.notifyDataSetChanged();
    }
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.setBackgroundColor(Theme.getColor(ColorId.filling));

    // Title area
    LinearLayout headerLayout = new LinearLayout(context);
    headerLayout.setOrientation(LinearLayout.VERTICAL);
    headerLayout.setGravity(Gravity.CENTER_HORIZONTAL);
    headerLayout.setPadding(Screen.dp(16), Screen.dp(48), Screen.dp(16), Screen.dp(16));

    titleView = new android.widget.TextView(context);
    titleView.setTextSize(20);
    titleView.setTextColor(Theme.getColor(ColorId.text));
    titleView.setText(callTitle);
    titleView.setGravity(Gravity.CENTER);
    headerLayout.addView(titleView);

    subtitleView = new android.widget.TextView(context);
    subtitleView.setTextSize(14);
    subtitleView.setTextColor(Theme.getColor(ColorId.textLight));
    subtitleView.setText(getSubtitleText());
    subtitleView.setGravity(Gravity.CENTER);
    subtitleView.setPadding(0, Screen.dp(4), 0, 0);
    headerLayout.addView(subtitleView);

    FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    headerParams.gravity = Gravity.TOP;
    contentView.addView(headerLayout, headerParams);

    // Video grid container (shown when video is active)
    videoGridContainer = new FrameLayout(context);
    videoGridContainer.setBackgroundColor(0xff1b1f23);
    videoGridContainer.setVisibility(View.GONE);

    videoGrid = new RecyclerView(context);
    videoGrid.setLayoutManager(new GridLayoutManager(context, 2));
    videoAdapter = new VideoParticipantsAdapter();
    videoGrid.setAdapter(videoAdapter);
    videoGridContainer.addView(videoGrid, new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    FrameLayout.LayoutParams videoGridParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    videoGridParams.topMargin = Screen.dp(120);
    videoGridParams.bottomMargin = Screen.dp(100);
    contentView.addView(videoGridContainer, videoGridParams);

    // Participants list (shown when no video)
    participantsList = new RecyclerView(context);
    participantsList.setLayoutManager(new LinearLayoutManager(context));
    participantsAdapter = new ParticipantsAdapter();
    participantsList.setAdapter(participantsAdapter);

    FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    listParams.topMargin = Screen.dp(120);
    listParams.bottomMargin = Screen.dp(100);
    contentView.addView(participantsList, listParams);

    // Local camera floating layout
    localCameraFloatingLayout = new VoIPFloatingLayout(context);
    localCameraFloatingLayout.setDelegate((progress, value) -> {
      if (localCameraTextureView != null) {
        localCameraTextureView.setScreenShareMiniProgress(progress, value);
      }
    });
    localCameraFloatingLayout.setRelativePosition(1f, 1f);
    localCameraFloatingLayout.setVisibility(View.GONE);
    localCameraFloatingLayout.setTag(VoIPFloatingLayout.STATE_GONE);

    localCameraTextureView = new VoIPTextureView(context, true, false);
    localCameraTextureView.renderer.setUseCameraRotation(true);
    localCameraTextureView.renderer.setMirror(isFrontCamera);
    localCameraFloatingLayout.addView(localCameraTextureView);

    contentView.addView(localCameraFloatingLayout, FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // Controls at bottom
    controlsLayout = new LinearLayout(context);
    controlsLayout.setOrientation(LinearLayout.HORIZONTAL);
    controlsLayout.setGravity(Gravity.CENTER);
    controlsLayout.setPadding(Screen.dp(16), Screen.dp(16), Screen.dp(16), Screen.dp(32));
    controlsLayout.setBackgroundColor(ColorUtils.alphaColor(0.9f, Theme.getColor(ColorId.filling)));

    // Mute button
    muteButton = createControlButton(context, R.id.btn_mute,
      isMuted ? R.drawable.baseline_mic_off_24 : R.drawable.baseline_mic_24);
    controlsLayout.addView(muteButton, createButtonParams());

    // Camera button
    cameraButton = createControlButton(context, R.id.btn_camera, R.drawable.baseline_videocam_24);
    controlsLayout.addView(cameraButton, createButtonParams());

    // Screen share button
    screenShareButton = createControlButton(context, R.id.btn_screenShare, R.drawable.baseline_screen_share_24);
    controlsLayout.addView(screenShareButton, createButtonParams());

    // Raise hand button
    raiseHandButton = createControlButton(context, R.id.btn_raiseHand, R.drawable.baseline_arrow_upward_24);
    controlsLayout.addView(raiseHandButton, createButtonParams());

    // Invite button
    inviteButton = createControlButton(context, R.id.btn_inviteToCall, R.drawable.baseline_person_add_24);
    controlsLayout.addView(inviteButton, createButtonParams());

    // Leave button (red)
    leaveButton = createControlButton(context, R.id.btn_leaveCall, R.drawable.baseline_call_end_24);
    leaveButton.setBackgroundColor(Theme.getColor(ColorId.fillingNegative));
    controlsLayout.addView(leaveButton, createButtonParams());

    // End call button (only visible for admins) - long press on leave button
    leaveButton.setOnLongClickListener(v -> {
      if (groupCall != null && groupCall.canBeManaged) {
        showEndCallConfirmation();
        return true;
      }
      return false;
    });

    FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    controlsParams.gravity = Gravity.BOTTOM;
    contentView.addView(controlsLayout, controlsParams);

    // Load participants
    loadParticipants();

    return contentView;
  }

  private CircleButton createControlButton (Context context, int id, @DrawableRes int iconRes) {
    CircleButton button = new CircleButton(context);
    button.setId(id);
    button.setOnClickListener(this);
    button.init(iconRes, 52f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
    return button;
  }

  private LinearLayout.LayoutParams createButtonParams () {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Screen.dp(56), Screen.dp(56));
    params.setMargins(Screen.dp(12), 0, Screen.dp(12), 0);
    return params;
  }

  private String getSubtitleText () {
    if (groupCall != null) {
      int count = groupCall.participantCount;
      if (count == 0) {
        return "No participants";
      } else if (count == 1) {
        return "1 participant";
      } else {
        return count + " participants";
      }
    }
    return "Connecting...";
  }

  private void updateSubtitle () {
    if (subtitleView != null) {
      UI.post(() -> subtitleView.setText(getSubtitleText()));
    }
  }

  private void loadParticipants () {
    if (groupCall == null) return;

    // For joined group calls, use LoadGroupCallParticipants
    // Participants will be received through onGroupCallParticipantUpdated callback
    tdlib.send(new TdApi.LoadGroupCallParticipants(groupCall.id, 50), (ok, error) -> {
      if (error != null) {
        Log.e(Log.TAG_VOIP, "Failed to load group call participants: %s", error.message);
      }
    });
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();

    if (id == R.id.btn_mute) {
      isMuted = !isMuted;
      if (groupInterface != null) {
        groupInterface.setMuted(isMuted);
      }
      muteButton.replaceIcon(isMuted ? R.drawable.baseline_mic_off_24 : R.drawable.baseline_mic_24);
      UI.showToast(isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT);

    } else if (id == R.id.btn_camera) {
      isCameraEnabled = !isCameraEnabled;
      isScreenSharing = false;
      if (groupInterface != null) {
        groupInterface.setCameraEnabled(isCameraEnabled, isFrontCamera);
      }
      updateCameraButton();
      showLocalCameraPreview(isCameraEnabled);
      updateVideoGrid();

    } else if (id == R.id.btn_screenShare) {
      isScreenSharing = !isScreenSharing;
      isCameraEnabled = false;
      if (groupInterface != null) {
        groupInterface.setScreenShareEnabled(isScreenSharing);
      }
      updateScreenShareButton();

    } else if (id == R.id.btn_raiseHand) {
      toggleRaiseHand();

    } else if (id == R.id.btn_leaveCall) {
      leaveCall();
    }
  }

  private void shareInviteLink () {
    if (groupCall == null) return;

    tdlib.send(new TdApi.GetVideoChatInviteLink(groupCall.id, groupCall.canBeManaged), (result, error) -> {
      if (error != null) {
        UI.showError(error);
        return;
      }

      String inviteLink = result.url;
      UI.post(() -> {
        // Copy to clipboard
        android.content.ClipboardManager clipboard =
          (android.content.ClipboardManager) context().getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Video Chat Invite", inviteLink);
        clipboard.setPrimaryClip(clip);
        UI.showToast(R.string.InviteLinkCopied, Toast.LENGTH_SHORT);

        // Also offer to share
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, inviteLink);
        context().startActivity(android.content.Intent.createChooser(shareIntent, Lang.getString(R.string.InviteToVideoChat)));
      });
    });
  }

  private void toggleRaiseHand () {
    if (groupCall == null) return;

    isHandRaised = !isHandRaised;
    tdlib.send(new TdApi.ToggleGroupCallParticipantIsHandRaised(
      groupCall.id,
      new TdApi.MessageSenderUser(tdlib.myUserId()),
      isHandRaised
    ), (ok, error) -> {
      if (error != null) {
        UI.showError(error);
        isHandRaised = !isHandRaised; // Revert on error
      } else {
        UI.post(() -> {
          updateRaiseHandButton();
          UI.showToast(isHandRaised ? R.string.RaiseHand : R.string.LowerHand, Toast.LENGTH_SHORT);
        });
      }
    });
  }

  private void updateRaiseHandButton () {
    if (raiseHandButton != null) {
      raiseHandButton.setBackgroundColor(isHandRaised ?
        Theme.getColor(ColorId.fillingPositive) : Theme.getColor(ColorId.circleButtonRegular));
    }
  }

  private void showEndCallConfirmation () {
    showOptions(
      Lang.getString(R.string.EndVideoChat),
      new int[] {R.id.btn_endVideoChat, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.EndVideoChat), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_call_end_24, R.drawable.baseline_cancel_24},
      (itemView, optionId) -> {
        if (optionId == R.id.btn_endVideoChat && groupCall != null) {
          tdlib.ui().endVideoChat(groupCall.id);
          navigateBack();
        }
        return true;
      }
    );
  }

  private void updateCameraButton () {
    if (cameraButton != null) {
      cameraButton.setBackgroundColor(isCameraEnabled ?
        Theme.getColor(ColorId.fillingPositive) : Theme.getColor(ColorId.circleButtonRegular));
    }
  }

  private void updateScreenShareButton () {
    if (screenShareButton != null) {
      screenShareButton.setBackgroundColor(isScreenSharing ?
        Theme.getColor(ColorId.fillingPositive) : Theme.getColor(ColorId.circleButtonRegular));
    }
  }

  private void showLocalCameraPreview (boolean show) {
    if (localCameraFloatingLayout == null || localCameraTextureView == null) return;

    if (show) {
      // Initialize renderer if needed
      localCameraTextureView.renderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
      localCameraTextureView.renderer.setMirror(isFrontCamera);
      localCameraTextureView.setIsCamera(true);
      localCameraTextureView.setIsScreencast(false);

      // Show floating layout
      localCameraFloatingLayout.setVisibility(View.VISIBLE);
      localCameraFloatingLayout.setTag(VoIPFloatingLayout.STATE_FLOATING);
      localCameraFloatingLayout.setFloatingMode(true, true);
    } else {
      // Hide and release
      localCameraFloatingLayout.setVisibility(View.GONE);
      localCameraFloatingLayout.setTag(VoIPFloatingLayout.STATE_GONE);
      localCameraTextureView.stopCapturing();
    }
  }

  private void leaveCall () {
    if (groupInterface != null) {
      groupInterface.stop();
    }

    if (groupCall != null) {
      tdlib.send(new TdApi.LeaveGroupCall(groupCall.id), (ok, error) -> {
        UI.post(() -> {
          UI.showToast("Left voice chat", Toast.LENGTH_SHORT);
          navigateBack();
        });
      });
    } else {
      navigateBack();
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (groupInterface != null) {
      groupInterface.setStateListener(null);
      groupInterface.setFrameCallback(null);
      groupInterface.setRemoteSourceChangeCallback(null);
    }
    // Unsubscribe from group call updates
    if (groupCall != null) {
      tdlib.listeners().unsubscribeFromGroupCallUpdates(groupCall.id, this);
    }
    // Clean up video resources
    if (localCameraTextureView != null) {
      localCameraTextureView.stopCapturing();
    }
    for (VideoParticipant vp : videoParticipants) {
      if (vp.textureView != null) {
        vp.textureView.stopCapturing();
      }
    }
  }

  // GroupCallStateListener implementation
  @Override
  public void onConnectionStateChanged (NetworkInfo.State state) {
    UI.post(() -> {
      switch (state) {
        case CONNECTED:
          UI.showToast("Connected to voice chat", Toast.LENGTH_SHORT);
          break;
        case FAILED:
        case TIMEOUT:
          UI.showToast("Connection failed", Toast.LENGTH_SHORT);
          break;
        case CLOSED:
          UI.showToast("Disconnected", Toast.LENGTH_SHORT);
          navigateBack();
          break;
      }
    });
  }

  @Override
  public void onParticipantVideoChanged (String endpoint, boolean hasVideo) {
    UI.post(() -> {
      if (hasVideo) {
        onParticipantVideoEnabled(endpoint);
      } else {
        onParticipantVideoDisabled(endpoint);
      }
    });
  }

  // GroupCallListener implementation
  @Override
  public void onGroupCallUpdated (TdApi.GroupCall updatedGroupCall) {
    this.groupCall = updatedGroupCall;
    updateSubtitle();
  }

  @Override
  public void onGroupCallParticipantUpdated (int groupCallId, TdApi.GroupCallParticipant participant) {
    if (groupCall != null && groupCall.id == groupCallId) {
      UI.post(() -> {
        // Update participant in list
        for (int i = 0; i < participants.size(); i++) {
          if (participants.get(i).participantId.equals(participant.participantId)) {
            participants.set(i, participant);
            participantsAdapter.notifyItemChanged(i);
            return;
          }
        }
        // Add new participant
        participants.add(participant);
        participantsAdapter.notifyItemInserted(participants.size() - 1);
        updateSubtitle();
      });
    }
  }

  // Simple adapter for participants list
  private class ParticipantsAdapter extends RecyclerView.Adapter<ParticipantViewHolder> {
    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      ParticipantItemView view = new ParticipantItemView(parent.getContext());
      view.setLayoutParams(new RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56)));
      return new ParticipantViewHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull ParticipantViewHolder holder, int position) {
      TdApi.GroupCallParticipant participant = participants.get(position);
      holder.bind(participant);
    }

    @Override
    public int getItemCount () {
      return participants.size();
    }
  }

  private class ParticipantViewHolder extends RecyclerView.ViewHolder {
    private final ParticipantItemView itemView;

    public ParticipantViewHolder (@NonNull View itemView) {
      super(itemView);
      this.itemView = (ParticipantItemView) itemView;
    }

    public void bind (TdApi.GroupCallParticipant participant) {
      itemView.setParticipant(participant);
    }
  }

  // Simple participant item view
  private class ParticipantItemView extends View {
    private TdApi.GroupCallParticipant participant;
    private String name = "";
    private boolean isSpeaking = false;
    private boolean isMuted = false;

    public ParticipantItemView (Context context) {
      super(context);
    }

    public void setParticipant (TdApi.GroupCallParticipant participant) {
      this.participant = participant;
      this.isSpeaking = participant.isSpeaking;
      this.isMuted = participant.isMutedForAllUsers || participant.isMutedForCurrentUser;

      // Get participant name
      if (participant.participantId instanceof TdApi.MessageSenderUser) {
        long userId = ((TdApi.MessageSenderUser) participant.participantId).userId;
        TdApi.User user = tdlib.cache().user(userId);
        if (user != null) {
          name = user.firstName + (user.lastName != null ? " " + user.lastName : "");
        } else {
          name = "User " + userId;
        }
      } else if (participant.participantId instanceof TdApi.MessageSenderChat) {
        long chatId = ((TdApi.MessageSenderChat) participant.participantId).chatId;
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat != null) {
          name = chat.title;
        } else {
          name = "Chat " + chatId;
        }
      }
      invalidate();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      int width = getWidth();
      int height = getHeight();

      // Draw speaking indicator
      if (isSpeaking) {
        canvas.drawRect(0, 0, Screen.dp(4), height, Paints.fillingPaint(Theme.getColor(ColorId.fillingPositive)));
      }

      // Draw name
      canvas.drawText(name, Screen.dp(16), height / 2f + Screen.dp(5),
        Paints.getRegularTextPaint(15, Theme.getColor(ColorId.text)));

      // Draw mute icon if muted
      if (isMuted) {
        Drawable muteIcon = Drawables.get(R.drawable.baseline_mic_off_24);
        if (muteIcon != null) {
          int iconSize = Screen.dp(20);
          int iconX = width - Screen.dp(16) - iconSize;
          int iconY = (height - iconSize) / 2;
          muteIcon.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize);
          muteIcon.setTint(Theme.getColor(ColorId.iconLight));
          muteIcon.draw(canvas);
        }
      }
    }
  }

  // Video participants adapter for grid layout
  private class VideoParticipantsAdapter extends RecyclerView.Adapter<VideoParticipantViewHolder> {
    @NonNull
    @Override
    public VideoParticipantViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      VoIPTextureView textureView = new VoIPTextureView(parent.getContext(), false, true, false);
      textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
      textureView.renderer.setEnableHardwareScaler(true);
      textureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;

      FrameLayout container = new FrameLayout(parent.getContext());
      container.setBackgroundColor(0xff1b1f23);
      container.addView(textureView, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      int itemHeight = Screen.dp(200);
      container.setLayoutParams(new RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, itemHeight));

      return new VideoParticipantViewHolder(container, textureView);
    }

    @Override
    public void onBindViewHolder (@NonNull VideoParticipantViewHolder holder, int position) {
      // Get active video participants
      List<VideoParticipant> activeParticipants = new ArrayList<>();
      for (VideoParticipant vp : videoParticipants) {
        if (vp.hasVideo) activeParticipants.add(vp);
      }

      if (position < activeParticipants.size()) {
        VideoParticipant vp = activeParticipants.get(position);
        holder.bind(vp);
      }
    }

    @Override
    public int getItemCount () {
      int count = 0;
      for (VideoParticipant vp : videoParticipants) {
        if (vp.hasVideo) count++;
      }
      return count;
    }
  }

  private class VideoParticipantViewHolder extends RecyclerView.ViewHolder {
    private final VoIPTextureView textureView;
    private VideoParticipant boundParticipant;

    public VideoParticipantViewHolder (@NonNull View itemView, VoIPTextureView textureView) {
      super(itemView);
      this.textureView = textureView;
    }

    public void bind (VideoParticipant participant) {
      this.boundParticipant = participant;
      participant.textureView = textureView;

      // Initialize renderer for this participant
      textureView.renderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
    }

    public VoIPTextureView getTextureView () {
      return textureView;
    }
  }
}
