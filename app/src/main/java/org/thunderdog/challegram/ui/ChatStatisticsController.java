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
 * File created on 21/04/2019
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.MiniChart;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.MessagePreviewView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.SeparatorView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.IntList;
import tgx.td.Td;

public class ChatStatisticsController extends RecyclerViewController<ChatStatisticsController.Args> implements View.OnClickListener, View.OnLongClickListener {
  public static class Args {
    public final long chatId;

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  public ChatStatisticsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private SettingsAdapter adapter;

  private DoubleHeaderView headerCell;

  private TdApi.ChatStatisticsMessageSenderInfo[] messageSenderInfos;
  private long currentMediaAlbumId;
  private final ArrayList<MessageInteractionInfoContainer> interactionMessages = new ArrayList<>();
  private final HashMap<Long, List<TdApi.Message>> interactionMessageAlbums = new HashMap<>();

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public boolean onLongClick (View v) {
    final ListItem item = (ListItem) v.getTag();
    if (item == null || !(item.getData() instanceof DoubleTextWrapper))
      return false;

    openMemberMenu((DoubleTextWrapper) item.getData());
    return true;
  }

  @Override
  public void onClick (View v) {
    long userId;
    ListItem item = (ListItem) v.getTag();

    if (item != null && item.getData() instanceof DoubleTextWrapper) {
      userId = ((DoubleTextWrapper) item.getData()).getUserId();
    } else {
      userId = 0;
    }

    final int viewId = v.getId();
    if (viewId == R.id.btn_viewMemberMessages) {
      if (userId != 0) {
        HashtagChatController c = new HashtagChatController(context(), tdlib);
        c.setArguments(new HashtagChatController.Arguments(null, getArgumentsStrict().chatId, null, new TdApi.MessageSenderUser(userId), false));
        navigateTo(c);
        tdlib.ui().openPrivateChat(this, userId, new TdlibUi.ChatOpenParameters().keepStack());
      }
    } else if (viewId == R.id.btn_openInviterProfile) {
      if (userId != 0) {
        tdlib.ui().openPrivateChat(this, userId, new TdlibUi.ChatOpenParameters().keepStack());
      }
    } else if (viewId == R.id.btn_viewAdminActions) {
      MessagesController c = new MessagesController(context, tdlib);
      c.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_EVENT_LOG, null, tdlib.chat(getArgumentsStrict().chatId)).eventLogUserId(userId));
      navigateTo(c);
    } else if (viewId == R.id.btn_showAdvanced) {
      showAllUsers();
    } else if (viewId == R.id.btn_messageMore) {
      MessageInteractionInfoContainer container = (MessageInteractionInfoContainer) item.getData();
      MessageStatisticsController msc = new MessageStatisticsController(context, tdlib);
      List<TdApi.Message> album = interactionMessageAlbums.get(container.message.mediaAlbumId);

      if (album != null) {
        msc.setArguments(new MessageStatisticsController.Args(getArgumentsStrict().chatId, album));
      } else {
        msc.setArguments(new MessageStatisticsController.Args(getArgumentsStrict().chatId, container.message));
      }

      navigateTo(msc);
    }
  }

  private TdlibMessageViewer.Viewport messageViewport;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    final long chatId = getArgumentsStrict().chatId;

    DoubleHeaderView headerCell = new DoubleHeaderView(context);
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(12f), true);
    headerCell.setTitle(tdlib.chatTitle(chatId));
    headerCell.setSubtitle(R.string.Stats);
    this.headerCell = headerCell;

    this.messageViewport = tdlib.messageViewer().createViewport(new TdApi.MessageSourceSearch(), this);
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setSeparatorOptions (ListItem item, int position, SeparatorView separatorView) {
        if (item.getId() == R.id.separator) {
          separatorView.setOffsets(Screen.dp(8f) * 2 + Screen.dp(40f), 0);
        } else {
          super.setSeparatorOptions(item, position, separatorView);
        }
      }

      @Override
      protected void setText (ListItem item, CustomTextView view, boolean isUpdate) {
        super.setText(item, view, isUpdate);

        if (view.getId() == R.id.text_title) {
          view.setTextSize(16f);
          view.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
          view.setTextColorId(ColorId.text);
          ViewSupport.setThemedBackground(view, ColorId.filling, ChatStatisticsController.this);
        }
      }

      @Override
      protected void setMessagePreview (ListItem item, int position, MessagePreviewView previewView) {
        StringBuilder statString = new StringBuilder();
        MessageInteractionInfoContainer container = (MessageInteractionInfoContainer) item.getData();

        statString.append(Lang.plural(R.string.xViews, container.messageInteractionInfo.viewCount));
        if (container.messageInteractionInfo.forwardCount > 0) {
          statString.append(", ").append(Lang.plural(R.string.StatsXShared, container.messageInteractionInfo.forwardCount));
        }

        RippleSupport.setSimpleWhiteBackground(previewView);
        previewView.setMessage(container.message, null, statString.toString(), MessagePreviewView.Options.NONE);
        previewView.setContentInset(Screen.dp(8));
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_members ||
          itemId == R.id.btn_membersReading ||
          itemId == R.id.btn_membersWriting ||
          itemId == R.id.btn_messages ||
          itemId == R.id.btn_share ||
          itemId == R.id.btn_view) {
          TdApi.StatisticalValue value = (TdApi.StatisticalValue) item.getData();
          view.setIgnoreEnabled(true);
          view.setEnabled(false);
          if (value.value == value.previousValue || value.previousValue == 0) {
            view.setTextColorId(ColorId.NONE);
            view.setName(Strings.buildCounter((long) value.value));
          } else {
            view.setTextColorId(value.value > value.previousValue ? ColorId.textSecure : ColorId.textNegative);
            view.setName(Lang.getString(value.value > value.previousValue ? R.string.StatsValueGrowth : R.string.StatsValueFall, Strings.buildCounter((long) value.value), Strings.buildCounter((long) Math.abs(value.value - value.previousValue)), Lang.beautifyDouble(value.growthRatePercentage)));
          }
          view.setData(item.getString());
        } else if (itemId == R.id.btn_notifications) {
          view.setIgnoreEnabled(true);
          view.setEnabled(false);
          view.setTextColorId(ColorId.NONE);
          view.setName(Lang.beautifyDouble(item.getDoubleValue()) + "%");
          view.setData(item.getString());
        }
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.ui().attachViewportToRecyclerView(messageViewport, recyclerView);
    tdlib.send(new TdApi.GetChatStatistics(chatId, Theme.isDark()), (chatStatistics, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        switch (chatStatistics.getConstructor()) {
          case TdApi.ChatStatisticsChannel.CONSTRUCTOR:
            setStatistics((TdApi.ChatStatisticsChannel) chatStatistics);
            break;
          case TdApi.ChatStatisticsSupergroup.CONSTRUCTOR:
            setStatistics((TdApi.ChatStatisticsSupergroup) chatStatistics);
            break;
          default:
            Td.assertChatStatistics_6744ad70();
            throw Td.unsupported(chatStatistics);
        }
      }
    }));
  }

  @Override
  public void destroy () {
    super.destroy();
    if (messageViewport != null) {
      messageViewport.performDestroy();
    }
  }

  @Override
  public int getId () {
    return R.id.controller_stats;
  }

  /*@Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return y < Size.getHeaderPortraitSize() || x <= Screen.dp(15f);
  }*/

  private TdApi.ChatStatistics statistics;
  private int pendingRequests;

  private void setStatistics (TdApi.ChatStatisticsSupergroup statistics) {
    this.statistics = statistics;

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_members, 0, R.string.StatsMembers, false).setData(statistics.memberCount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_messages, 0, R.string.StatsMessages, false).setData(statistics.messageCount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_membersReading, 0, R.string.StatsMembersReading, false).setData(statistics.viewerCount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_membersWriting, 0, R.string.StatsMembersWriting, false).setData(statistics.senderCount));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(R.string.StatsRange, Lang.getDateRange(statistics.period.startDate, statistics.period.endDate, TimeUnit.SECONDS, true)), false));

    if (statistics.topAdministrators.length > 0) {
      addTopUsers(items, statistics.period, statistics.topAdministrators, R.string.StatsTopAdmins, R.id.btn_viewAdminActions);
    }

    if (statistics.topSenders.length > 0) {
      messageSenderInfos = statistics.topSenders;
      addTopUsers(items, statistics.period, statistics.topSenders, R.string.StatsTopMembers, R.id.btn_viewMemberMessages);
    }

    if (statistics.topInviters.length > 0) {
      addTopUsers(items, statistics.period, statistics.topInviters, R.string.StatsTopInviters, R.id.btn_openInviterProfile);
    }

    final long chatId = getArgumentsStrict().chatId;
    List<Chart> charts = Arrays.asList(
      new Chart(R.id.stats_memberCount, tdlib, chatId, R.string.StatsChartGrowth, ChartDataUtil.TYPE_LINEAR, statistics.memberCountGraph, 0),
      new Chart(R.id.stats_join, tdlib, chatId, R.string.StatsChartMembers, ChartDataUtil.TYPE_LINEAR, statistics.joinGraph, 0),
      new Chart(R.id.stats_joinBySource, tdlib, chatId, R.string.StatsChartMembersBySource, ChartDataUtil.TYPE_STACK_BAR, statistics.joinBySourceGraph, 0),
      new Chart(R.id.stats_language, tdlib, chatId, R.string.StatsChartMembersLanguage, ChartDataUtil.TYPE_STACK_PIE, statistics.languageGraph, 0),
      new Chart(R.id.stats_messages, tdlib, chatId, R.string.StatsChartMessages, ChartDataUtil.TYPE_STACK_BAR, statistics.messageContentGraph, 0),
      new Chart(R.id.stats_actions, tdlib, chatId, R.string.StatsChartActions, ChartDataUtil.TYPE_LINEAR, statistics.actionGraph, 0),
      new Chart(R.id.stats_topHours, tdlib, chatId, R.string.StatsChartTopHours, ChartDataUtil.TYPE_LINEAR, statistics.dayGraph, 0),
      new Chart(R.id.stats_topDays, tdlib, chatId, R.string.StatsChartTopDays, ChartDataUtil.TYPE_STACK_PIE, statistics.weekGraph, 0)
    );

    setCharts(items, charts, this::executeScheduledAnimation);
  }

  private void addTopUsers (List<ListItem> items, TdApi.DateRange range, TdApi.Object[] users, @StringRes int header, @IdRes int id) {
    items.add(new ListItem(ListItem.TYPE_CHART_HEADER_DETACHED).setData(new MiniChart(header, range)));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    int maxLength;
    if (users[0] instanceof TdApi.ChatStatisticsMessageSenderInfo) {
      maxLength = Math.min(10, users.length);
    } else {
      maxLength = users.length;
    }

    for (int i = 0; i < maxLength; i++) {
      DoubleTextWrapper wrapper = null;
      TdApi.Object object = users[i];

      if (id == R.id.btn_viewMemberMessages) {
        TdApi.ChatStatisticsMessageSenderInfo sender = (TdApi.ChatStatisticsMessageSenderInfo) object;
        SpannableStringBuilder customStatus2 = new SpannableStringBuilder();
        wrapper = new DoubleTextWrapper(tdlib, sender.userId, true);

        if (sender.sentMessageCount > 0) {
          customStatus2.append(Lang.pluralBold(R.string.xMessages, sender.sentMessageCount));
        }

        if (sender.averageCharacterCount > 0) {
          if (customStatus2.length() > 0) {
            customStatus2.append(", ");
          }

          customStatus2.append(Lang.pluralBold(R.string.StatsXCharacters, sender.averageCharacterCount));
        }

        wrapper.setSubtitle(customStatus2);
      } else if (id == R.id.btn_openInviterProfile) {
        TdApi.ChatStatisticsInviterInfo inviter = (TdApi.ChatStatisticsInviterInfo) object;
        wrapper = new DoubleTextWrapper(tdlib, inviter.userId, true);
        wrapper.setSubtitle(Lang.pluralBold(R.string.StatsXInvitations, inviter.addedMemberCount));
      } else if (id == R.id.btn_viewAdminActions) {
        TdApi.ChatStatisticsAdministratorActionsInfo admin = (TdApi.ChatStatisticsAdministratorActionsInfo) object;
        wrapper = new DoubleTextWrapper(tdlib, admin.userId, true);

        SpannableStringBuilder customStatus = new SpannableStringBuilder();

        if (admin.deletedMessageCount > 0) {
          customStatus.append(Lang.pluralBold(R.string.StatsXDeletions, admin.deletedMessageCount));
          if (admin.bannedUserCount > 0 || admin.restrictedUserCount > 0)
            customStatus.append(", ");
        }

        if (admin.bannedUserCount > 0) {
          customStatus.append(Lang.pluralBold(R.string.StatsXBans, admin.bannedUserCount));
          if (admin.restrictedUserCount > 0) customStatus.append(", ");
        }

        if (admin.restrictedUserCount > 0) {
          customStatus.append(Lang.pluralBold(R.string.StatsXRestrictions, admin.restrictedUserCount));
        }

        wrapper.setSubtitle(customStatus);
      }

      if (wrapper != null) {
        wrapper.setIgnoreOnline(true);
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, id).setData(wrapper));
        if (i != maxLength - 1) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
    }

    if (maxLength < users.length) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, R.drawable.baseline_direction_arrow_down_24, Lang.plural(R.string.StatsXShowMore, users.length - 10), false));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  private void showAllUsers () {
    final int index = adapter.indexOfViewById(R.id.btn_showAdvanced);

    if (index != -1 && messageSenderInfos != null) {
      List<ListItem> items = adapter.getItems();

      items.remove(index);

      ArrayList<ListItem> advancedItems = new ArrayList<>();

      for (int i = 10; i < messageSenderInfos.length; i++) {
        TdApi.ChatStatisticsMessageSenderInfo sender = messageSenderInfos[i];
        DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, sender.userId, true);
        wrapper.setSubtitle(Lang.plural(R.string.xMessages, sender.sentMessageCount) + ", " + Lang.plural(R.string.StatsXCharacters, sender.averageCharacterCount));
        wrapper.setIgnoreOnline(true);
        advancedItems.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_viewMemberMessages).setData(wrapper));
        if (i != messageSenderInfos.length - 1)
          advancedItems.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }

      ArrayUtils.ensureCapacity(items, items.size() + advancedItems.size());
      int i = index;
      for (ListItem item : advancedItems) {
        items.add(i++, item);
      }

      adapter.notifyItemRangeRemoved(index, 1);
      adapter.notifyItemRangeInserted(index, advancedItems.size());
    }
  }

  private void setStatistics (TdApi.ChatStatisticsChannel statistics) {
    this.statistics = statistics;

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_members, 0, R.string.StatsMembers, false).setData(statistics.memberCount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_notifications, 0, R.string.StatsNotifications, false).setDoubleValue(statistics.enabledNotificationsPercentage));
    if (!Td.isEmpty(statistics.meanMessageViewCount)) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_view, 0, R.string.StatsViews, false).setData(statistics.meanMessageViewCount));
    }
    if (!Td.isEmpty(statistics.meanMessageShareCount)) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_share, 0, R.string.StatsShares, false).setData(statistics.meanMessageShareCount));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(R.string.StatsRange, Lang.getDateRange(statistics.period.startDate, statistics.period.endDate, TimeUnit.SECONDS, true)), false));

    final long chatId = getArgumentsStrict().chatId;
    List<Chart> charts = Arrays.asList(
      new Chart(R.id.stats_memberCount, tdlib, chatId, R.string.StatsChartGrowth, ChartDataUtil.TYPE_LINEAR, statistics.memberCountGraph, 0),
      new Chart(R.id.stats_join, tdlib, chatId, R.string.StatsChartFollowers, ChartDataUtil.TYPE_LINEAR, statistics.joinGraph, 0),
      new Chart(R.id.stats_mute, tdlib, chatId, R.string.StatsChartNotifications, ChartDataUtil.TYPE_LINEAR, statistics.muteGraph, 0),
      new Chart(R.id.stats_viewCountByHour, tdlib, chatId, R.string.StatsChartViewsByHour, ChartDataUtil.TYPE_LINEAR, statistics.viewCountByHourGraph, Chart.FLAG_NO_DATE),

      new Chart(R.id.stats_viewCountBySource, tdlib, chatId, R.string.StatsChartViewsBySource, ChartDataUtil.TYPE_STACK_BAR, statistics.viewCountBySourceGraph, 0),
      new Chart(R.id.stats_joinBySource, tdlib, chatId, R.string.StatsChartFollowersBySource, ChartDataUtil.TYPE_STACK_BAR, statistics.joinBySourceGraph, 0),

      new Chart(R.id.stats_language, tdlib, chatId, R.string.StatsChartLanguage, ChartDataUtil.TYPE_STACK_PIE, statistics.languageGraph, 0),

      new Chart(R.id.stats_messageInteraction, tdlib, chatId, R.string.StatsChartInteractions, ChartDataUtil.TYPE_DOUBLE_LINEAR, statistics.messageInteractionGraph, 0),
      new Chart(R.id.stats_instantViewInteraction, tdlib, chatId, R.string.StatsChartIv, ChartDataUtil.TYPE_DOUBLE_LINEAR, statistics.instantViewInteractionGraph, 0)
    );

    setCharts(items, charts, () -> {
      TdApi.ChatStatisticsInteractionInfo[] recentMessageInteractions = ArrayUtils.filter(
        Arrays.asList(statistics.recentInteractions),
        item -> item.objectType.getConstructor() == TdApi.ChatStatisticsObjectTypeMessage.CONSTRUCTOR
      ).toArray(new TdApi.ChatStatisticsInteractionInfo[0]);
      // TODO: stories
      if (recentMessageInteractions.length > 0) {
        setRecentMessageInteractions(statistics.period, recentMessageInteractions, R.string.StatsRecentPosts);
      } else {
        executeScheduledAnimation();
      }
    });
  }

  private void setRecentMessageInteractions (TdApi.DateRange range, TdApi.ChatStatisticsInteractionInfo[] interactions, @StringRes int header) {
    loadInteractionMessages(interactions, () -> {
      int currentSize = adapter.getItems().size();
      adapter.getItems().add(new ListItem(ListItem.TYPE_CHART_HEADER_DETACHED).setData(new MiniChart(header, range)));

      for (int i = 0; i < interactionMessages.size(); i++) {
        adapter.getItems().add(new ListItem(i == 0 ? ListItem.TYPE_SHADOW_TOP : ListItem.TYPE_SEPARATOR_FULL, i != 0 ? R.id.separator : 0));
        adapter.getItems().add(new ListItem(ListItem.TYPE_STATS_MESSAGE_PREVIEW, R.id.btn_messageMore).setData(interactionMessages.get(i)));
      }

      adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.notifyItemRangeInserted(currentSize, adapter.getItems().size());

      executeScheduledAnimation();
    });
  }

  private void loadInteractionMessages (TdApi.ChatStatisticsInteractionInfo[] interactions, Runnable onMessagesLoaded) {
    AtomicInteger remaining = new AtomicInteger(interactions.length);

    Runnable after = () -> {
      if (remaining.decrementAndGet() == 0) {
        runOnUiThreadOptional(onMessagesLoaded);
      }
    };
    Tdlib.ResultHandler<TdApi.Message> messageHandler = (message, error) -> {
      if (message != null) {
        tdlib.getMessageProperties(message, properties -> {
          if (message.mediaAlbumId != 0) {
            if (!interactionMessageAlbums.containsKey(message.mediaAlbumId)) {
              interactionMessageAlbums.put(message.mediaAlbumId, new ArrayList<>());
            }
            interactionMessageAlbums.get(message.mediaAlbumId).add(message);
          }

          if (properties.canGetStatistics && (message.mediaAlbumId == 0 || currentMediaAlbumId != message.mediaAlbumId)) {
            currentMediaAlbumId = message.mediaAlbumId;
            // interactionMessageAlbums should already be loaded at this moment
            TdApi.Message suitableMessage = message.mediaAlbumId == 0 ? message : sortInteractions(message.mediaAlbumId);
            interactionMessages.add(new MessageInteractionInfoContainer(
              suitableMessage,
              interactions[interactions.length - remaining.get()]
            ));
          }
          after.run();
        });
      } else {
        after.run();
      }
    };

    for (TdApi.ChatStatisticsInteractionInfo interaction : interactions) {
      switch (interaction.objectType.getConstructor()) {
        case TdApi.ChatStatisticsObjectTypeMessage.CONSTRUCTOR: {
          TdApi.ChatStatisticsObjectTypeMessage objectType = (TdApi.ChatStatisticsObjectTypeMessage) interaction.objectType;
          tdlib.send(new TdApi.GetMessageLocally(getArgumentsStrict().chatId, objectType.messageId), messageHandler);
          break;
        }
        case TdApi.ChatStatisticsObjectTypeStory.CONSTRUCTOR: {
          TdApi.ChatStatisticsObjectTypeStory objectType = (TdApi.ChatStatisticsObjectTypeStory) interaction.objectType;
          // TODO: stories
          after.run();
          break;
        }
        default:
          Td.assertChatStatisticsObjectType_5cb871fe();
          throw Td.unsupported(interaction.objectType);
      }
    }
  }

  private TdApi.Message sortInteractions (long mediaAlbumId) {
    List<TdApi.Message> messages = Td.sortByViewCount(interactionMessageAlbums.get(mediaAlbumId));
    if (messages == null) return null;
    return messages.get(0);
  }

  private void setCharts (List<ListItem> items, List<Chart> charts, Runnable onChartsLoaded) {
    if (charts != null) {
      for (Chart chart : charts) {
        if (chart.isAsync()) {
          pendingRequests++;
          chart.load(hasData -> {
            if (isDestroyed())
              return;
            --pendingRequests;
            if (hasData) {
              int startIndex = -1;
              final int originalIndex = charts.indexOf(chart);
              int index = originalIndex;
              while (startIndex == -1 && --index >= 0) {
                startIndex = adapter.indexOfViewById(charts.get(index).getId());
              }
              if (startIndex != -1) {
                startIndex += 2;
              } else {
                index = originalIndex;
                while (startIndex == -1 && ++index < charts.size()) {
                  startIndex = adapter.indexOfViewById(charts.get(index).getId());
                }
                if (startIndex != -1) {
                  startIndex -= 2;
                }
              }
              startIndex = startIndex != -1 ? startIndex : adapter.getItemCount();
              adapter.addItems(startIndex,
                new ListItem(ListItem.TYPE_CHART_HEADER).setData(chart),
                new ListItem(ListItem.TYPE_SHADOW_TOP),
                new ListItem(chart.getViewType(), chart.getId()).setData(chart),
                new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
              );
            }
            if (pendingRequests == 0) {
              onChartsLoaded.run();
            }
          });
        } else if (!chart.isError()) {
          items.add(new ListItem(ListItem.TYPE_CHART_HEADER).setData(chart));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(chart.getViewType(), chart.getId()).setData(chart));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }
      }
    }

    adapter.setItems(items, false);

    if (pendingRequests == 0) {
      onChartsLoaded.run();
    }
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return statistics == null || pendingRequests > 0;
  }

  public static class MessageInteractionInfoContainer {
    public final TdApi.Message message;
    public final TdApi.ChatStatisticsInteractionInfo messageInteractionInfo;

    public MessageInteractionInfoContainer (TdApi.Message message, TdApi.ChatStatisticsInteractionInfo messageInteractionInfo) {
      this.message = message;
      this.messageInteractionInfo = messageInteractionInfo;
    }
  }

  // TODO: migrate this to TdlibUi to not copypaste from SharedMembersController
  private void kickMember (final DoubleTextWrapper content) {
    final long chatId = getArgumentsStrict().chatId;
    final ListItem headerItem = new ListItem(ListItem.TYPE_INFO, 0, 0, Lang.getStringBold(R.string.MemberCannotJoinGroup, tdlib.cache().userName(content.getUserId())), false);
    showSettings(new SettingsWrapBuilder(R.id.btn_blockSender)
      .addHeaderItem(headerItem)
      .setIntDelegate((id, result) -> {
        boolean blockUser = result.get(R.id.btn_restrictMember) != 0;
        if (content.getMember().status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR && !blockUser) {
          TdApi.ChatMemberStatusRestricted now = (TdApi.ChatMemberStatusRestricted) content.getMember().status;
          tdlib.setChatMemberStatus(chatId, content.getSenderId(), new TdApi.ChatMemberStatusRestricted(false, now.restrictedUntilDate, now.permissions), content.getMember().status, null);
        } else {
          tdlib.setChatMemberStatus(chatId, content.getSenderId(), new TdApi.ChatMemberStatusBanned(), content.getMember().status, null);
          if (!blockUser) {
            tdlib.setChatMemberStatus(chatId, content.getSenderId(), new TdApi.ChatMemberStatusLeft(), content.getMember().status, null);
          }
        }
      })
      .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> {
        headerItem.setString(Lang.getStringBold(settingsAdapter.getCheckIntResults().get(R.id.btn_restrictMember) != 0 ? R.string.MemberCannotJoinGroup : R.string.MemberCanJoinGroup, tdlib.cache().userName(content.getUserId())));
        settingsAdapter.updateValuedSettingByPosition(settingsAdapter.indexOfView(headerItem));
      })
      .setRawItems(new ListItem[]{
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_restrictMember, 0, R.string.BanMember, true)
      }).setSaveStr(R.string.RemoveMember).setSaveColorId(ColorId.textNegative));
  }

  private void editMember (DoubleTextWrapper content, boolean restrict, TdApi.ChatMemberStatus myStatus, TdApi.ChatMember member) {
    if (restrict) {
      int mode = TD.canRestrictMember(myStatus, member.status);
      if (mode == TD.RESTRICT_MODE_NEW) {
        member = null;
      }
    } else {
      int mode = TD.canPromoteAdmin(myStatus, member.status);
      if (mode == TD.RESTRICT_MODE_NEW) {
        member = null;
      }
    }

    EditRightsController c = new EditRightsController(context, tdlib);
    c.setArguments(new EditRightsController.Args(getArgumentsStrict().chatId, content.getSenderId(), restrict, myStatus, member).noFocusLock());
    navigateTo(c);
  }

  private void openMemberMenu (DoubleTextWrapper content) {
    IntList ids = new IntList(4);
    IntList colors = new IntList(4);
    IntList icons = new IntList(4);
    StringList strings = new StringList(4);

    tdlib.send(new TdApi.GetChatMember(getArgumentsStrict().chatId, content.getSenderId()), (member, error) -> {
      if (error != null) return;

      TdApi.ChatMemberStatus myStatus = tdlib.chatStatus(getArgumentsStrict().chatId);

      if (myStatus != null) {
        if (TD.isCreator(member.status)) {
          if (TD.isCreator(myStatus)) {
            ids.append(R.id.btn_editRights);
            colors.append(OptionColor.NORMAL);
            icons.append(R.drawable.baseline_edit_24);
            strings.append(R.string.EditAdminTitle);
          }
        } else {
          int promoteMode = TD.canPromoteAdmin(myStatus, member.status);
          if (promoteMode != TD.PROMOTE_MODE_NONE) {
            ids.append(R.id.btn_editRights);
            colors.append(OptionColor.NORMAL);
            icons.append(R.drawable.baseline_stars_24);
            switch (promoteMode) {
              case TD.PROMOTE_MODE_EDIT:
                strings.append(R.string.EditAdminRights);
                break;
              case TD.PROMOTE_MODE_NEW:
                strings.append(R.string.SetAsAdmin);
                break;
              case TD.PROMOTE_MODE_VIEW:
                strings.append(R.string.ViewAdminRights);
                break;
              default:
                throw new IllegalStateException();
            }
          }
        }

        int restrictMode = TD.canRestrictMember(myStatus, member.status);
        if (restrictMode != TD.RESTRICT_MODE_NONE) {
          ids.append(R.id.btn_restrictMember);
          colors.append(OptionColor.NORMAL);
          icons.append(R.drawable.baseline_block_24);

          switch (restrictMode) {
            case TD.RESTRICT_MODE_EDIT:
              strings.append(content.getSenderId().getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (tdlib.isChannel(Td.getSenderId(content.getSenderId())) ? R.string.EditChannelRestrictions : R.string.EditGroupRestrictions) : R.string.EditUserRestrictions);
              break;
            case TD.RESTRICT_MODE_NEW:
              strings.append(content.getSenderId().getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (tdlib.isChannel(Td.getSenderId(content.getSenderId())) ? R.string.BanChannel : R.string.BanChat) : R.string.RestrictUser);
              break;
            case TD.RESTRICT_MODE_VIEW:
              strings.append(R.string.ViewRestrictions);
              break;
            default:
              throw new IllegalStateException();
          }

          if (restrictMode != TD.RESTRICT_MODE_VIEW && TD.isMember(member.status)) {
            ids.append(R.id.btn_blockSender);
            colors.append(OptionColor.NORMAL);
            icons.append(R.drawable.baseline_remove_circle_24);
            strings.append(R.string.RemoveFromGroup);
          }
        }
      }

      ids.append(R.id.btn_messageViewList);
      if (tdlib.isSelfUserId(content.getUserId())) {
        strings.append(R.string.ViewMessagesFromYou);
      } else {
        strings.append(Lang.getString(R.string.ViewMessagesFromUser, tdlib.cache().userFirstName(content.getUserId())));
      }
      icons.append(R.drawable.baseline_person_24);
      colors.append(OptionColor.NORMAL);

      runOnUiThreadOptional(() -> showOptions("", ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
        if (id == R.id.btn_messageViewList) {
          HashtagChatController c = new HashtagChatController(context, tdlib);
          c.setArguments(new HashtagChatController.Arguments(null, getArgumentsStrict().chatId, null, new TdApi.MessageSenderUser(content.getUserId()), false));
          navigateTo(c);
        } else if (id == R.id.btn_editRights) {
          editMember(content, false, myStatus, member);
        } else if (id == R.id.btn_restrictMember) {
          editMember(content, true, myStatus, member);
        } else if (id == R.id.btn_blockSender) {
          kickMember(content);
        }

        return true;
      }));
    });
  }

}
