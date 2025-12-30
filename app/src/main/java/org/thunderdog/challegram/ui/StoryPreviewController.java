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
import android.util.Log;

import org.thunderdog.challegram.BuildConfig;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.BubbleView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.UserPickerMultiDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.LongList;

public class StoryPreviewController extends RecyclerViewController<StoryPreviewController.Args>
    implements View.OnClickListener, Menu, UserPickerMultiDelegate {

  public static class Args {
    public final String filePath;
    public final boolean isVideo;
    public final double videoDuration;
    public final long targetChatId;  // 0 for personal stories, channel chat ID for channel stories

    public Args (String filePath, boolean isVideo) {
      this(filePath, isVideo, 0, 0);
    }

    public Args (String filePath, boolean isVideo, double videoDuration) {
      this(filePath, isVideo, videoDuration, 0);
    }

    public Args (String filePath, boolean isVideo, double videoDuration, long targetChatId) {
      this.filePath = filePath;
      this.isVideo = isVideo;
      this.videoDuration = videoDuration;
      this.targetChatId = targetChatId;
    }
  }

  // Privacy options
  private static final int PRIVACY_EVERYONE = 0;
  private static final int PRIVACY_CONTACTS = 1;
  private static final int PRIVACY_CLOSE_FRIENDS = 2;
  private static final int PRIVACY_SELECTED = 3;

  // Duration options (in seconds)
  private static final int DURATION_6H = 21600;
  private static final int DURATION_12H = 43200;
  private static final int DURATION_24H = 86400;
  private static final int DURATION_48H = 172800;

  private int selectedPrivacy = PRIVACY_EVERYONE;
  private int selectedDuration = DURATION_24H;
  private boolean saveToProfile = false;
  private String captionText = "";
  private long[] selectedUserIds = new long[0];

  private SettingsAdapter adapter;

  public StoryPreviewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean isChannelStory () {
    Args args = getArguments();
    return args != null && args.targetChatId != 0 && tdlib.isChannel(args.targetChatId);
  }

  @Override
  public int getId () {
    return R.id.controller_storyCompose;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.CreateStory);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_done) {
      header.addDoneButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_done) {
      postStory();
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int itemId = item.getId();
        if (itemId == R.id.btn_storyPrivacy) {
          view.setData(getPrivacyText());
        } else if (itemId == R.id.btn_storyDuration) {
          view.setData(getDurationText());
        } else if (itemId == R.id.btn_storySaveToProfile) {
          if (view.getToggler() != null) {
            view.getToggler().setRadioEnabled(saveToProfile, isUpdate);
          }
        }
      }
    };

    buildCells();
    recyclerView.setAdapter(adapter);
  }

  private void buildCells () {
    List<ListItem> items = new ArrayList<>();
    boolean isChannel = isChannelStory();

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.StoryPosting));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    // Privacy setting - only for personal stories (channels don't have privacy settings)
    if (!isChannel) {
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_storyPrivacy,
          R.drawable.baseline_visibility_24, R.string.StoryPrivacy));

      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    // Duration setting
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_storyDuration,
        R.drawable.baseline_schedule_24, R.string.StoryDuration));

    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

    // Save to profile toggle
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_storySaveToProfile,
        R.drawable.baseline_bookmark_24, R.string.SaveToProfile));

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_storyPrivacy) {
      showPrivacyPicker();
    } else if (id == R.id.btn_storyDuration) {
      showDurationPicker();
    } else if (id == R.id.btn_storySaveToProfile) {
      saveToProfile = adapter.toggleView(v);
    }
  }

  private void showPrivacyPicker () {
    showSettings(new SettingsWrapBuilder(R.id.btn_storyPrivacy)
        .setRawItems(new ListItem[] {
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_privacyEveryone, 0, R.string.StoryPrivacyEveryone, R.id.btn_storyPrivacy, selectedPrivacy == PRIVACY_EVERYONE),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_privacyContacts, 0, R.string.StoryPrivacyContacts, R.id.btn_storyPrivacy, selectedPrivacy == PRIVACY_CONTACTS),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_privacyCloseFriends, 0, R.string.StoryPrivacyCloseFriends, R.id.btn_storyPrivacy, selectedPrivacy == PRIVACY_CLOSE_FRIENDS),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_privacySelected, 0, R.string.StoryPrivacySelectedUsers, R.id.btn_storyPrivacy, selectedPrivacy == PRIVACY_SELECTED)
        })
        .setIntDelegate((id, result) -> {
          int newPrivacy = result.get(R.id.btn_storyPrivacy);
          if (newPrivacy == R.id.btn_privacyEveryone) {
            selectedPrivacy = PRIVACY_EVERYONE;
          } else if (newPrivacy == R.id.btn_privacyContacts) {
            selectedPrivacy = PRIVACY_CONTACTS;
          } else if (newPrivacy == R.id.btn_privacyCloseFriends) {
            selectedPrivacy = PRIVACY_CLOSE_FRIENDS;
          } else if (newPrivacy == R.id.btn_privacySelected) {
            selectedPrivacy = PRIVACY_SELECTED;
            // Open contact picker to select users
            openContactPicker();
          }
          adapter.updateValuedSettingById(R.id.btn_storyPrivacy);
        })
        .setSaveStr(Lang.getString(R.string.Done))
        .setAllowResize(false)
    );
  }

  private void openContactPicker () {
    ContactsController c = new ContactsController(context, tdlib);
    c.setArguments(new ContactsController.Args(this));
    navigateTo(c);
  }

  // UserPickerMultiDelegate implementation
  @Override
  public long[] getAlreadySelectedChatIds () {
    // Return already selected user IDs as chat IDs
    return selectedUserIds;
  }

  @Override
  public void onAlreadyPickedChatsChanged (List<BubbleView.Entry> bubbles) {
    LongList userIds = new LongList(bubbles.size());
    for (BubbleView.Entry entry : bubbles) {
      if (entry.senderId == null) {
        continue;
      }
      switch (entry.senderId.getConstructor()) {
        case TdApi.MessageSenderUser.CONSTRUCTOR:
          userIds.append(((TdApi.MessageSenderUser) entry.senderId).userId);
          break;
        case TdApi.MessageSenderChat.CONSTRUCTOR:
          // For chats, get the user ID if it's a private chat
          long chatId = ((TdApi.MessageSenderChat) entry.senderId).chatId;
          long userId = tdlib.chatUserId(chatId);
          if (userId != 0) {
            userIds.append(userId);
          }
          break;
      }
    }
    selectedUserIds = userIds.get();
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "Selected " + selectedUserIds.length + " users for story privacy");
    }
    // Update the privacy text to show count
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_storyPrivacy);
    }
  }

  @Override
  public int provideMultiUserPickerHint () {
    return R.string.StorySelectPeopleHint;
  }

  private void showDurationPicker () {
    boolean isPremium = tdlib.hasPremium();

    // Build duration options - non-premium users only get 24h option
    java.util.List<ListItem> durationItems = new java.util.ArrayList<>();
    if (isPremium) {
      durationItems.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_duration6h, 0, R.string.StoryDuration6h, R.id.btn_storyDuration, selectedDuration == DURATION_6H));
      durationItems.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_duration12h, 0, R.string.StoryDuration12h, R.id.btn_storyDuration, selectedDuration == DURATION_12H));
    }
    durationItems.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_duration24h, 0, R.string.StoryDuration24h, R.id.btn_storyDuration, selectedDuration == DURATION_24H));
    if (isPremium) {
      durationItems.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_duration48h, 0, R.string.StoryDuration48h, R.id.btn_storyDuration, selectedDuration == DURATION_48H));
    }

    showSettings(new SettingsWrapBuilder(R.id.btn_storyDuration)
        .setRawItems(durationItems.toArray(new ListItem[0]))
        .setIntDelegate((id, result) -> {
          int selection = result.get(R.id.btn_storyDuration);
          if (selection == R.id.btn_duration6h) {
            selectedDuration = DURATION_6H;
          } else if (selection == R.id.btn_duration12h) {
            selectedDuration = DURATION_12H;
          } else if (selection == R.id.btn_duration24h) {
            selectedDuration = DURATION_24H;
          } else if (selection == R.id.btn_duration48h) {
            selectedDuration = DURATION_48H;
          }
          adapter.updateValuedSettingById(R.id.btn_storyDuration);
        })
        .setSaveStr(Lang.getString(R.string.Done))
        .setAllowResize(false)
    );
  }

  private String getPrivacyText () {
    switch (selectedPrivacy) {
      case PRIVACY_CONTACTS:
        return Lang.getString(R.string.StoryPrivacyContacts);
      case PRIVACY_CLOSE_FRIENDS:
        return Lang.getString(R.string.StoryPrivacyCloseFriends);
      case PRIVACY_SELECTED:
        if (selectedUserIds.length > 0) {
          return Lang.getString(R.string.xPeopleSelected, selectedUserIds.length);
        }
        return Lang.getString(R.string.StoryPrivacySelectedUsers);
      case PRIVACY_EVERYONE:
      default:
        return Lang.getString(R.string.StoryPrivacyEveryone);
    }
  }

  private String getDurationText () {
    switch (selectedDuration) {
      case DURATION_6H:
        return Lang.getString(R.string.StoryDuration6h);
      case DURATION_12H:
        return Lang.getString(R.string.StoryDuration12h);
      case DURATION_48H:
        return Lang.getString(R.string.StoryDuration48h);
      case DURATION_24H:
      default:
        return Lang.getString(R.string.StoryDuration24h);
    }
  }

  private TdApi.StoryPrivacySettings buildPrivacySettings () {
    switch (selectedPrivacy) {
      case PRIVACY_CONTACTS:
        return new TdApi.StoryPrivacySettingsContacts();
      case PRIVACY_CLOSE_FRIENDS:
        return new TdApi.StoryPrivacySettingsCloseFriends();
      case PRIVACY_SELECTED:
        if (selectedUserIds.length > 0) {
          return new TdApi.StoryPrivacySettingsSelectedUsers(selectedUserIds);
        }
        // Fall through to everyone if no users selected
        return new TdApi.StoryPrivacySettingsEveryone();
      case PRIVACY_EVERYONE:
      default:
        return new TdApi.StoryPrivacySettingsEveryone();
    }
  }

  private static final String TAG = "StoryPreview";

  private void postStory () {
    Args args = getArgumentsStrict();
    String filePath = args.filePath;
    boolean isVideo = args.isVideo;

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "postStory: filePath=" + filePath + ", isVideo=" + isVideo);
    }

    // Validate selected users if PRIVACY_SELECTED is chosen
    if (selectedPrivacy == PRIVACY_SELECTED && selectedUserIds.length == 0) {
      UI.showToast(R.string.StorySelectUsersRequired, Toast.LENGTH_SHORT);
      openContactPicker();
      return;
    }

    // Check if file exists
    java.io.File file = new java.io.File(filePath);
    if (!file.exists()) {
      if (BuildConfig.DEBUG) {
        Log.e(TAG, "postStory: File does not exist: " + filePath);
      }
      UI.showToast(Lang.getString(R.string.StoryFileNotFound, filePath), Toast.LENGTH_LONG);
      return;
    }
    if (BuildConfig.DEBUG) {
      Log.d(TAG, "postStory: File exists, size=" + file.length() + " bytes");
    }

    TdApi.InputStoryContent content;

    if (isVideo) {
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "postStory: Creating video content, duration=" + args.videoDuration);
      }
      TdApi.InputFile inputFile = new TdApi.InputFileLocal(filePath);
      content = new TdApi.InputStoryContentVideo(inputFile, new int[0], args.videoDuration, 0.0, false);
    } else {
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "postStory: Creating photo content with processing");
      }
      // Use PhotoGenerationInfo to trigger proper image processing/resizing
      // Stories require 1080x1920 (9:16), so use 1920 as resolution limit
      long lastModified = file.lastModified();
      TdApi.InputFile inputFile = PhotoGenerationInfo.newFile(filePath, 0, lastModified, false, 1920);
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "postStory: Created InputFileGenerated for photo processing");
      }
      content = new TdApi.InputStoryContentPhoto(inputFile, new int[0]);
    }

    // Use targetChatId for channel stories, otherwise selfChatId for personal stories
    long chatId = args.targetChatId != 0 ? args.targetChatId : tdlib.selfChatId();
    // Privacy settings only apply to personal stories, not channel stories
    TdApi.StoryPrivacySettings privacy = isChannelStory() ? null : buildPrivacySettings();

    // For non-premium users, force 24h duration
    int duration = selectedDuration;
    if (!tdlib.hasPremium() && duration != DURATION_24H) {
      if (BuildConfig.DEBUG) {
        Log.w(TAG, "postStory: Non-premium user, forcing 24h duration");
      }
      duration = DURATION_24H;
    }

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "postStory: chatId=" + chatId + ", duration=" + duration + ", saveToProfile=" + saveToProfile);
      if (selectedPrivacy == PRIVACY_SELECTED) {
        Log.d(TAG, "postStory: Privacy=SELECTED, userIds=" + java.util.Arrays.toString(selectedUserIds));
      }
    }

    UI.showToast(R.string.StoryPosting, Toast.LENGTH_SHORT);

    final int finalDuration = duration;
    tdlib.client().send(new TdApi.PostStory(
      chatId,
      content,
      null,  // areas
      null,  // caption
      privacy,
      new int[0],  // albumIds
      finalDuration,
      null,  // fromStoryFullId
      saveToProfile,
      false  // protectContent
    ), result -> {
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "postStory: Result constructor=" + result.getConstructor());
      }
      runOnUiThreadOptional(() -> {
        if (result.getConstructor() == TdApi.Story.CONSTRUCTOR) {
          TdApi.Story story = (TdApi.Story) result;
          if (BuildConfig.DEBUG) {
            Log.d(TAG, "postStory: Success! storyId=" + story.id);
          }
          UI.showToast(R.string.StoryPosted, Toast.LENGTH_SHORT);
          navigateBack();
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          if (BuildConfig.DEBUG) {
            Log.e(TAG, "postStory: Error code=" + error.code + ", message=" + error.message);
          }
          UI.showToast(Lang.getString(R.string.StoryPostError) + ": " + error.message, Toast.LENGTH_LONG);
        } else {
          if (BuildConfig.DEBUG) {
            Log.e(TAG, "postStory: Unexpected result type: " + result.getConstructor());
          }
          UI.showToast(R.string.StoryUnexpectedError, Toast.LENGTH_LONG);
        }
      });
    });
  }
}
