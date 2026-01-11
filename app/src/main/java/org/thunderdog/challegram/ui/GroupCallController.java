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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import io.github.pytgcalls.NetworkInfo;
import org.pytgcalls.ntgcallsx.NTgCallsGroupInterface;
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
  private CircleButton leaveButton;
  private android.widget.TextView titleView;
  private android.widget.TextView subtitleView;
  private RecyclerView participantsList;
  private ParticipantsAdapter participantsAdapter;

  // State
  private boolean isMuted = true;
  private boolean isCameraEnabled = false;
  private boolean isScreenSharing = false;
  private List<TdApi.GroupCallParticipant> participants = new ArrayList<>();

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
    }

    // Subscribe to group call updates
    if (groupCall != null) {
      tdlib.listeners().subscribeToGroupCallUpdates(groupCall.id, this);
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

    // Participants list
    participantsList = new RecyclerView(context);
    participantsList.setLayoutManager(new LinearLayoutManager(context));
    participantsAdapter = new ParticipantsAdapter();
    participantsList.setAdapter(participantsAdapter);

    FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    listParams.topMargin = Screen.dp(120);
    listParams.bottomMargin = Screen.dp(100);
    contentView.addView(participantsList, listParams);

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

    // Leave button (red)
    leaveButton = createControlButton(context, R.id.btn_leaveCall, R.drawable.baseline_call_end_24);
    leaveButton.setBackgroundColor(Theme.getColor(ColorId.fillingNegative));
    controlsLayout.addView(leaveButton, createButtonParams());

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
        groupInterface.setCameraEnabled(isCameraEnabled, true);
      }
      updateCameraButton();

    } else if (id == R.id.btn_screenShare) {
      isScreenSharing = !isScreenSharing;
      isCameraEnabled = false;
      if (groupInterface != null) {
        groupInterface.setScreenShareEnabled(isScreenSharing);
      }
      updateScreenShareButton();

    } else if (id == R.id.btn_leaveCall) {
      leaveCall();
    }
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
    }
    // Unsubscribe from group call updates
    if (groupCall != null) {
      tdlib.listeners().unsubscribeFromGroupCallUpdates(groupCall.id, this);
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
    // TODO: Handle video changes
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
}
