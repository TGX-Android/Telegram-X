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
 * File created for forum topics support
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.ChatHeaderView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.ListInfoView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import org.thunderdog.challegram.util.StringList;

import tgx.td.MessageId;

public class ForumTopicsController extends TelegramViewController<ForumTopicsController.Arguments> implements
  Menu, MoreDelegate, View.OnClickListener, View.OnLongClickListener,
  ChatListener, TdlibCache.SupergroupDataChangeListener, ChatHeaderView.Callback {

  public static class Arguments {
    public final long chatId;
    public final TdApi.Chat chat;

    public Arguments (long chatId, @Nullable TdApi.Chat chat) {
      this.chatId = chatId;
      this.chat = chat;
    }

    public Arguments (TdApi.Chat chat) {
      this.chatId = chat.id;
      this.chat = chat;
    }
  }

  public ForumTopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long chatId;
  private TdApi.Chat chat;

  private FrameLayoutFix contentView;
  private CustomRecyclerView recyclerView;
  private ForumTopicsAdapter adapter;
  private ListInfoView emptyView;
  private CircleButton createTopicButton;
  private ChatHeaderView headerCell;

  private List<TdApi.ForumTopic> topics;
  private List<TdApi.ForumTopic> allTopics; // For restoring after search
  private boolean isLoading;
  private boolean canLoadMore;
  private String currentSearchQuery;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.chatId = args.chatId;
    this.chat = args.chat;
  }

  @Override
  public int getId () {
    return R.id.controller_forumTopics;
  }

  @Override
  public CharSequence getName () {
    if (chat != null) {
      return chat.title;
    }
    return Lang.getString(R.string.Topics);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addMoreButton(menu, this);
      header.addSearchButton(menu, this);
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      openSearchMode();
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else if (id == R.id.menu_btn_more) {
      IntList ids = new IntList(1);
      IntList icons = new IntList(1);
      StringList strings = new StringList(1);

      ids.append(R.id.btn_viewAsChat);
      icons.append(R.drawable.baseline_chat_bubble_24);
      strings.append(R.string.ViewAsChat);

      showMore(ids.get(), strings.get(), icons.get(), 0);
    }
  }

  @Override
  protected void onLeaveSearchMode () {
    currentSearchQuery = null;
    if (allTopics != null) {
      topics.clear();
      topics.addAll(allTopics);
      adapter.setTopics(topics, null);
      updateEmptyView();
    }
  }

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    String cleanQuery = query != null ? query.trim() : "";
    if (StringUtils.equalsOrBothEmpty(cleanQuery, currentSearchQuery)) {
      return;
    }
    currentSearchQuery = cleanQuery;
    searchTopics(cleanQuery);
  }

  private void searchTopics (String query) {
    if (StringUtils.isEmpty(query)) {
      // Empty query - restore all topics
      if (allTopics != null) {
        topics.clear();
        topics.addAll(allTopics);
        adapter.setTopics(topics, null);
        updateEmptyView();
      }
      return;
    }

    // Save current topics before search if not already saved
    if (allTopics == null || allTopics.isEmpty()) {
      allTopics = new ArrayList<>(topics);
    }

    // Client-side filtering by topic name (case-insensitive)
    String lowerQuery = query.toLowerCase();
    List<TdApi.ForumTopic> filteredTopics = new ArrayList<>();
    for (TdApi.ForumTopic topic : allTopics) {
      if (topic.info.name.toLowerCase().contains(lowerQuery)) {
        filteredTopics.add(topic);
      }
    }

    topics.clear();
    topics.addAll(filteredTopics);
    adapter.setTopics(topics, query);
    updateEmptyView();
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (inSearchMode()) {
      if (commit) {
        closeSearchMode(null);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(contentView, ColorId.filling);

    // Create header view for clickable chat header
    headerCell = new ChatHeaderView(context, tdlib, this);
    headerCell.setCallback(this);
    if (chat != null) {
      headerCell.setChat(tdlib, chat, null, null);
    }

    topics = new ArrayList<>();
    adapter = new ForumTopicsAdapter(this);

    recyclerView = new CustomRecyclerView(context);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
    recyclerView.setAdapter(adapter);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (canLoadMore && !isLoading) {
          LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          if (manager != null) {
            int lastVisible = manager.findLastVisibleItemPosition();
            if (lastVisible >= topics.size() - 5) {
              loadMoreTopics();
            }
          }
        }
      }
    });

    emptyView = new ListInfoView(context);
    emptyView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    emptyView.showInfo(Lang.getString(R.string.LoadingTopics));

    contentView.addView(emptyView);
    contentView.addView(recyclerView);

    // Create topic FAB button
    int padding = Screen.dp(4f);
    FrameLayoutFix.LayoutParams fabParams = FrameLayoutFix.newParams(
      Screen.dp(56f) + padding * 2,
      Screen.dp(56f) + padding * 2,
      Gravity.RIGHT | Gravity.BOTTOM
    );
    fabParams.rightMargin = fabParams.bottomMargin = Screen.dp(16f) - padding;

    createTopicButton = new CircleButton(context);
    createTopicButton.setId(R.id.btn_createTopic);
    createTopicButton.setOnClickListener(this);
    createTopicButton.init(R.drawable.baseline_add_24, 56f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
    createTopicButton.setLayoutParams(fabParams);
    addThemeInvalidateListener(createTopicButton);
    contentView.addView(createTopicButton);

    loadTopics();

    return contentView;
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_viewAsChat) {
      // Set viewAsTopics to false and open as unified chat
      tdlib.client().send(new TdApi.ToggleChatViewAsTopics(chatId, false), result -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          tdlib.ui().post(() -> {
            // Navigate back and open chat in unified mode
            navigateBack();
            MessagesController c = new MessagesController(context, tdlib);
            c.setArguments(new MessagesController.Arguments(tdlib, null, chat, null, null, null));
            navigateTo(c);
          });
        }
      });
    }
  }

  @Override
  public void onChatHeaderClick () {
    if (chat != null) {
      ProfileController controller = new ProfileController(context, tdlib);
      controller.setShareCustomHeaderView(true);
      controller.setArguments(new ProfileController.Args(chat, null, false));
      navigateTo(controller);
    }
  }

  private void loadTopics () {
    if (isLoading) return;
    isLoading = true;

    tdlib.listeners().subscribeToChatUpdates(chatId, this);

    tdlib.client().send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), result -> {
      if (result.getConstructor() == TdApi.ForumTopics.CONSTRUCTOR) {
        TdApi.ForumTopics forumTopics = (TdApi.ForumTopics) result;
        UI.post(() -> {
          topics.clear();
          for (TdApi.ForumTopic topic : forumTopics.topics) {
            topics.add(topic);
          }
          // Save all topics for search restore
          allTopics = new ArrayList<>(topics);
          canLoadMore = forumTopics.topics.length > 0 &&
                        forumTopics.nextOffsetMessageId != 0;
          adapter.setTopics(topics, null);
          isLoading = false;
          updateEmptyView();
        });
      } else {
        UI.post(() -> {
          isLoading = false;
          updateEmptyView();
        });
      }
    });
  }

  private void loadMoreTopics () {
    if (isLoading || !canLoadMore || topics.isEmpty()) return;
    isLoading = true;

    TdApi.ForumTopic lastTopic = topics.get(topics.size() - 1);
    long lastMessageId = lastTopic.lastMessage != null ? lastTopic.lastMessage.id : 0;
    int lastDate = lastTopic.lastMessage != null ? lastTopic.lastMessage.date : 0;

    tdlib.client().send(new TdApi.GetForumTopics(chatId, "", lastDate, lastMessageId, lastTopic.info.forumTopicId, 100), result -> {
      if (result.getConstructor() == TdApi.ForumTopics.CONSTRUCTOR) {
        TdApi.ForumTopics forumTopics = (TdApi.ForumTopics) result;
        UI.post(() -> {
          for (TdApi.ForumTopic topic : forumTopics.topics) {
            topics.add(topic);
          }
          canLoadMore = forumTopics.topics.length > 0 &&
                        forumTopics.nextOffsetMessageId != 0;
          adapter.setTopics(topics, null);
          isLoading = false;
        });
      } else {
        UI.post(() -> {
          isLoading = false;
        });
      }
    });
  }

  private void updateEmptyView () {
    if (topics.isEmpty()) {
      emptyView.setVisibility(View.VISIBLE);
      emptyView.showInfo(Lang.getString(R.string.NoTopics));
    } else {
      emptyView.setVisibility(View.GONE);
    }
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_createTopic) {
      showCreateTopicDialog();
      return;
    }
    Object tag = v.getTag();
    if (tag instanceof TdApi.ForumTopic) {
      TdApi.ForumTopic topic = (TdApi.ForumTopic) tag;
      openTopic(topic);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    Object tag = v.getTag();
    if (tag instanceof TdApi.ForumTopic) {
      TdApi.ForumTopic topic = (TdApi.ForumTopic) tag;
      showTopicOptions(topic);
      return true;
    }
    return false;
  }

  private void openTopic (TdApi.ForumTopic topic) {
    MessagesController controller = new MessagesController(context, tdlib);

    // Calculate highlight position based on topic's last read message
    MessageId highlightMessageId = null;
    int highlightMode = MessagesManager.HIGHLIGHT_MODE_NONE;

    if (topic.unreadCount > 0 && topic.lastReadInboxMessageId != 0) {
      // There are unread messages - scroll to first unread
      highlightMessageId = new MessageId(chatId, topic.lastReadInboxMessageId);
      highlightMode = MessagesManager.HIGHLIGHT_MODE_UNREAD;
    } else if (topic.lastReadInboxMessageId == 0 && topic.unreadCount > 0) {
      // No messages have been read yet - scroll to beginning
      highlightMessageId = new MessageId(chatId, MessageId.MIN_VALID_ID);
      highlightMode = MessagesManager.HIGHLIGHT_MODE_UNREAD;
    }
    // If all messages are read (unreadCount == 0), highlightMessageId stays null
    // which will open at the bottom (most recent messages)

    MessagesController.Arguments args = new MessagesController.Arguments(
      null, // chatList
      chat,
      null, // threadInfo - will use topicId instead
      new TdApi.MessageTopicForum(topic.info.forumTopicId),
      highlightMessageId,
      highlightMode,
      null // filter
    );
    args.setForumTopic(topic);
    controller.setArguments(args);
    navigateTo(controller);
  }

  private void showTopicOptions (TdApi.ForumTopic topic) {
    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    IntList colors = new IntList(5);
    ArrayList<String> strings = new ArrayList<>();

    // Pin/Unpin
    if (topic.isPinned) {
      ids.append(R.id.btn_unpinTopic);
      icons.append(R.drawable.deproko_baseline_pin_undo_24);
      colors.append(OptionColor.NORMAL);
      strings.add(Lang.getString(R.string.UnpinTopic));
    } else {
      ids.append(R.id.btn_pinTopic);
      icons.append(R.drawable.deproko_baseline_pin_24);
      colors.append(OptionColor.NORMAL);
      strings.add(Lang.getString(R.string.PinTopic));
    }

    // Close/Reopen
    if (topic.info.isClosed) {
      ids.append(R.id.btn_reopenTopic);
      icons.append(R.drawable.baseline_lock_24);
      colors.append(OptionColor.NORMAL);
      strings.add(Lang.getString(R.string.ReopenTopic));
    } else {
      ids.append(R.id.btn_closeTopic);
      icons.append(R.drawable.baseline_lock_24);
      colors.append(OptionColor.NORMAL);
      strings.add(Lang.getString(R.string.CloseTopic));
    }

    // Notifications
    boolean isMuted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0;
    ids.append(R.id.btn_notifications);
    icons.append(isMuted ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24);
    colors.append(OptionColor.NORMAL);
    strings.add(Lang.getString(isMuted ? R.string.Unmute : R.string.Mute));

    // Edit
    ids.append(R.id.btn_editTopic);
    icons.append(R.drawable.baseline_edit_24);
    colors.append(OptionColor.NORMAL);
    strings.add(Lang.getString(R.string.EditTopic));

    // Delete
    if (!topic.info.isGeneral) {
      ids.append(R.id.btn_deleteTopic);
      icons.append(R.drawable.baseline_delete_24);
      colors.append(OptionColor.RED);
      strings.add(Lang.getString(R.string.DeleteTopic));
    }

    showOptions(topic.info.name, ids.get(), strings.toArray(new String[0]), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_pinTopic) {
        toggleTopicPinned(topic, true);
      } else if (id == R.id.btn_unpinTopic) {
        toggleTopicPinned(topic, false);
      } else if (id == R.id.btn_closeTopic) {
        toggleTopicClosed(topic, true);
      } else if (id == R.id.btn_reopenTopic) {
        toggleTopicClosed(topic, false);
      } else if (id == R.id.btn_notifications) {
        showTopicMuteOptions(topic);
      } else if (id == R.id.btn_editTopic) {
        editTopic(topic);
      } else if (id == R.id.btn_deleteTopic) {
        deleteTopic(topic);
      }
      return true;
    });
  }

  private void toggleTopicPinned (TdApi.ForumTopic topic, boolean pinned) {
    tdlib.client().send(new TdApi.ToggleForumTopicIsPinned(topic.info.chatId, topic.info.forumTopicId, pinned), result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.post(() -> UI.showError(result));
      }
    });
  }

  private void toggleTopicClosed (TdApi.ForumTopic topic, boolean closed) {
    tdlib.client().send(new TdApi.ToggleForumTopicIsClosed(topic.info.chatId, topic.info.forumTopicId, closed), result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.post(() -> UI.showError(result));
      }
    });
  }

  private void editTopic (TdApi.ForumTopic topic) {
    openInputAlert(
      Lang.getString(R.string.EditTopic),
      Lang.getString(R.string.TopicNameHint),
      R.string.Done,
      R.string.Cancel,
      topic.info.name,
      (inputView, result) -> {
        String newName = result.trim();
        if (newName.isEmpty()) {
          inputView.setInErrorState(true);
          return false;
        }
        if (newName.length() > 128) {
          inputView.setInErrorState(true);
          return false;
        }
        // Edit the topic with new name, keep existing icon
        tdlib.client().send(new TdApi.EditForumTopic(
          topic.info.chatId,
          topic.info.forumTopicId,
          newName,
          false, // editIconCustomEmoji
          0 // iconCustomEmojiId (not changing)
        ), result1 -> {
          UI.post(() -> {
            if (result1.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
              // Topic edited successfully, will be updated via listener
            } else if (result1.getConstructor() == TdApi.Error.CONSTRUCTOR) {
              UI.showError(result1);
            }
          });
        });
        return true;
      },
      true
    );
  }

  private void deleteTopic (TdApi.ForumTopic topic) {
    showConfirm(Lang.getStringBold(R.string.DeleteTopicConfirm, topic.info.name), Lang.getString(R.string.Delete), R.drawable.baseline_delete_24, OptionColor.RED, () -> {
      tdlib.client().send(new TdApi.DeleteForumTopic(topic.info.chatId, topic.info.forumTopicId), result -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          // Remove from local list immediately
          UI.post(() -> {
            for (int i = 0; i < topics.size(); i++) {
              if (topics.get(i).info.forumTopicId == topic.info.forumTopicId) {
                topics.remove(i);
                adapter.notifyItemRemoved(i);
                updateEmptyView();
                break;
              }
            }
          });
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.post(() -> UI.showError(result));
        }
      });
    });
  }

  private void showTopicMuteOptions (TdApi.ForumTopic topic) {
    boolean isMuted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0;

    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    ArrayList<String> strings = new ArrayList<>();

    if (isMuted) {
      // Currently muted - show unmute option
      ids.append(R.id.btn_menu_enable);
      icons.append(R.drawable.baseline_notifications_24);
      strings.add(Lang.getString(R.string.EnableNotifications));
    } else {
      // Currently unmuted - show mute options
      ids.append(R.id.btn_menu_1hour);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXHours, 1));

      ids.append(R.id.btn_menu_8hours);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXHours, 8));

      ids.append(R.id.btn_menu_2days);
      icons.append(R.drawable.baseline_notifications_paused_24);
      strings.add(Lang.plural(R.string.MuteForXDays, 2));

      ids.append(R.id.btn_menu_disable);
      icons.append(R.drawable.baseline_notifications_off_24);
      strings.add(Lang.getString(R.string.MuteForever));
    }

    showOptions(topic.info.name, ids.get(), strings.toArray(new String[0]), null, icons.get(), (itemView, id) -> {
      int muteFor = 0;
      if (id == R.id.btn_menu_enable) {
        muteFor = 0; // Unmute
      } else if (id == R.id.btn_menu_1hour) {
        muteFor = (int) java.util.concurrent.TimeUnit.HOURS.toSeconds(1);
      } else if (id == R.id.btn_menu_8hours) {
        muteFor = (int) java.util.concurrent.TimeUnit.HOURS.toSeconds(8);
      } else if (id == R.id.btn_menu_2days) {
        muteFor = (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(2);
      } else if (id == R.id.btn_menu_disable) {
        muteFor = Integer.MAX_VALUE; // Mute forever
      }
      setForumTopicMuteFor(topic, muteFor);
      return true;
    });
  }

  private void setForumTopicMuteFor (TdApi.ForumTopic topic, int muteFor) {
    TdApi.ChatNotificationSettings settings = new TdApi.ChatNotificationSettings();
    settings.useDefaultMuteFor = (muteFor == 0);
    settings.muteFor = muteFor;
    settings.useDefaultSound = true;
    settings.useDefaultShowPreview = true;
    settings.useDefaultMuteStories = true;
    settings.useDefaultStorySound = true;
    settings.useDefaultDisablePinnedMessageNotifications = true;
    settings.useDefaultDisableMentionNotifications = true;

    tdlib.client().send(new TdApi.SetForumTopicNotificationSettings(
      topic.info.chatId,
      topic.info.forumTopicId,
      settings
    ), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
          // Update local state and refresh UI
          if (topic.notificationSettings == null) {
            topic.notificationSettings = new TdApi.ChatNotificationSettings();
          }
          topic.notificationSettings.muteFor = muteFor;
          topic.notificationSettings.useDefaultMuteFor = (muteFor == 0);
          // Refresh the topic in the list
          for (int i = 0; i < topics.size(); i++) {
            if (topics.get(i).info.forumTopicId == topic.info.forumTopicId) {
              adapter.notifyItemChanged(i);
              break;
            }
          }
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  private void showCreateTopicDialog () {
    openInputAlert(
      Lang.getString(R.string.NewTopic),
      Lang.getString(R.string.TopicNameHint),
      R.string.Done,
      R.string.Cancel,
      null,
      (inputView, result) -> {
        String name = result.trim();
        if (name.isEmpty()) {
          inputView.setInErrorState(true);
          return false;
        }
        if (name.length() > 128) {
          inputView.setInErrorState(true);
          return false;
        }
        createTopic(name);
        return true;
      },
      true
    );
  }

  private void createTopic (String name) {
    // Standard topic colors from Telegram
    int[] topicColors = {
      0x6FB9F0, // Blue
      0xFFD67E, // Yellow
      0xCB86DB, // Purple
      0x8EEE98, // Green
      0xFF93B2, // Pink
      0xFB6F5F  // Red
    };
    // Pick a random color
    int color = topicColors[(int) (Math.random() * topicColors.length)];

    TdApi.ForumTopicIcon icon = new TdApi.ForumTopicIcon(color, 0);

    tdlib.client().send(new TdApi.CreateForumTopic(chatId, name, false, icon), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.ForumTopicInfo.CONSTRUCTOR) {
          TdApi.ForumTopicInfo info = (TdApi.ForumTopicInfo) result;
          // Reload topics to show the new one
          loadTopics();
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(result);
        }
      });
    });
  }

  // ChatListener (ForumTopicInfoListener) implementation
  @Override
  public void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) {
    if (info.chatId != chatId) return;
    UI.post(() -> {
      for (int i = 0; i < topics.size(); i++) {
        if (topics.get(i).info.forumTopicId == info.forumTopicId) {
          topics.get(i).info = info;
          adapter.notifyItemChanged(i);
          break;
        }
      }
    });
  }

  @Override
  public void onForumTopicUpdated (long chatId, long messageThreadId, boolean isPinned, long lastReadInboxMessageId, long lastReadOutboxMessageId, int unreadMentionCount, int unreadReactionCount, TdApi.ChatNotificationSettings notificationSettings) {
    if (chatId != this.chatId) return;

    // Find if read state changed - if so, we need to fetch fresh unread count
    boolean needFetchUnreadCount = false;
    for (int i = 0; i < topics.size(); i++) {
      TdApi.ForumTopic topic = topics.get(i);
      if (topic.info.forumTopicId == messageThreadId) {
        if (topic.lastReadInboxMessageId != lastReadInboxMessageId) {
          needFetchUnreadCount = true;
        }
        break;
      }
    }

    if (needFetchUnreadCount) {
      // Fetch fresh topic info to get accurate unread count
      tdlib.client().send(new TdApi.GetForumTopic(chatId, (int) messageThreadId), result -> {
        if (result.getConstructor() == TdApi.ForumTopic.CONSTRUCTOR) {
          TdApi.ForumTopic freshTopic = (TdApi.ForumTopic) result;
          UI.post(() -> {
            for (int i = 0; i < topics.size(); i++) {
              if (topics.get(i).info.forumTopicId == messageThreadId) {
                topics.set(i, freshTopic);
                adapter.notifyItemChanged(i);
                break;
              }
            }
          });
        }
      });
    } else {
      // Just update local state without fetching
      UI.post(() -> {
        for (int i = 0; i < topics.size(); i++) {
          TdApi.ForumTopic topic = topics.get(i);
          if (topic.info.forumTopicId == messageThreadId) {
            topic.isPinned = isPinned;
            topic.lastReadInboxMessageId = lastReadInboxMessageId;
            topic.lastReadOutboxMessageId = lastReadOutboxMessageId;
            topic.unreadMentionCount = unreadMentionCount;
            topic.unreadReactionCount = unreadReactionCount;
            if (notificationSettings != null) {
              topic.notificationSettings = notificationSettings;
            }
            adapter.notifyItemChanged(i);
            break;
          }
        }
      });
    }
  }

  // Inner adapter class
  private static class ForumTopicsAdapter extends RecyclerView.Adapter<ForumTopicViewHolder> {
    private final ForumTopicsController controller;
    private List<TdApi.ForumTopic> topics = new ArrayList<>();
    private String highlightQuery;

    ForumTopicsAdapter (ForumTopicsController controller) {
      this.controller = controller;
    }

    void setTopics (List<TdApi.ForumTopic> topics, @Nullable String highlightQuery) {
      this.topics = topics;
      this.highlightQuery = highlightQuery;
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ForumTopicViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      ForumTopicView view = new ForumTopicView(parent.getContext());
      view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(72f)));
      view.setOnClickListener(controller);
      view.setOnLongClickListener(controller);
      return new ForumTopicViewHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull ForumTopicViewHolder holder, int position) {
      TdApi.ForumTopic topic = topics.get(position);
      holder.bind(controller.tdlib, topic, highlightQuery);
    }

    @Override
    public void onViewAttachedToWindow (@NonNull ForumTopicViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (@NonNull ForumTopicViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).detach();
      }
    }

    @Override
    public void onViewRecycled (@NonNull ForumTopicViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).destroy();
      }
    }

    @Override
    public int getItemCount () {
      return topics.size();
    }
  }

  private static class ForumTopicViewHolder extends RecyclerView.ViewHolder {
    ForumTopicViewHolder (@NonNull View itemView) {
      super(itemView);
    }

    void bind (Tdlib tdlib, TdApi.ForumTopic topic, @Nullable String highlightQuery) {
      if (itemView instanceof ForumTopicView) {
        ((ForumTopicView) itemView).setTopic(tdlib, topic, highlightQuery);
        itemView.setTag(topic);
      }
    }
  }
}
