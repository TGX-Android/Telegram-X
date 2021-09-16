package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.MiniChart;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.MessagePreviewView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.ArrayUtils;
import me.vkryl.td.Td;

/**
 * Date: 2019-04-21
 * Author: default
 */
public class ChatStatisticsController extends RecyclerViewController<ChatStatisticsController.Args> implements View.OnClickListener {
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
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_viewMemberMessages:
        TGUser user = ((UserView) v).getUser();
        if (user != null) {
          HashtagChatController c = new HashtagChatController(context(), tdlib);
          c.setArguments(new HashtagChatController.Arguments(null, getArgumentsStrict().chatId, null, new TdApi.MessageSenderUser(user.getId()), false));
          navigateTo(c);

          tdlib.ui().openPrivateChat(this, user.getId(), new TdlibUi.ChatOpenParameters().keepStack());
        }
        break;
      case R.id.btn_openInviterProfile:
        TGUser user2 = ((UserView) v).getUser();
        if (user2 != null) {
          tdlib.ui().openPrivateChat(this, user2.getId(), new TdlibUi.ChatOpenParameters().keepStack());
        }
        break;
      case R.id.btn_viewAdminActions:
        long chatId = getArgumentsStrict().chatId;
        TGUser user3 = ((UserView) v).getUser();
        if (user3 != null) {
          long userId = user3.getId();
          tdlib.client().send(new TdApi.GetChatMember(chatId, new TdApi.MessageSenderUser((int) userId)), result -> {
            if (result.getConstructor() != TdApi.ChatMember.CONSTRUCTOR) return;
            TdApi.ChatMember member = (TdApi.ChatMember) result;
            TdApi.ChatMemberStatus myStatus = tdlib.chatStatus(chatId);
            if (myStatus != null) {
              runOnUiThreadOptional(() -> {
                EditRightsController c = new EditRightsController(context, tdlib);
                c.setArguments(new EditRightsController.Args(getArgumentsStrict().chatId, user3.getId(), false, myStatus, member));
                navigateTo(c);
              });
            }
          });
        }
        break;
      case R.id.btn_showAdvanced:
        showAllUsers();
        break;
      case R.id.btn_messageMore:
        MessageInteractionInfoContainer container = (MessageInteractionInfoContainer) v.getTag();
        MessageStatisticsController msc = new MessageStatisticsController(context, tdlib);
        List<TdApi.Message> album = interactionMessageAlbums.get(container.message.mediaAlbumId);

        if (album != null) {
          msc.setArguments(new MessageStatisticsController.Args(getArgumentsStrict().chatId, album));
        } else {
          msc.setArguments(new MessageStatisticsController.Args(getArgumentsStrict().chatId, container.message));
        }

        navigateTo(msc);
        break;
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    final long chatId = getArgumentsStrict().chatId;

    DoubleHeaderView headerCell = new DoubleHeaderView(context);
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(12f), true);
    headerCell.setTitle(tdlib.chatTitle(chatId));
    headerCell.setSubtitle(R.string.Stats);
    this.headerCell = headerCell;

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setText(ListItem item, CustomTextView view, boolean isUpdate) {
        super.setText(item, view, isUpdate);

        switch (view.getId()) {
          case R.id.text_title: {
            view.setTextSize(16f);
            view.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
            view.setTextColorId(R.id.theme_color_text);
            ViewSupport.setThemedBackground(view, R.id.theme_color_filling, ChatStatisticsController.this);
            break;
          }
        }
      }

      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        TGUser user;

        if (item.getId() == R.id.btn_viewMemberMessages) {
          TdApi.ChatStatisticsMessageSenderInfo sender = (TdApi.ChatStatisticsMessageSenderInfo) item.getData();
          user = new TGUser(tdlib, tdlib.cache().user(sender.userId));
          user.setCustomStatus(Lang.plural(R.string.xMessages, sender.sentMessageCount) + ", " + Lang.plural(R.string.StatsXCharacters, sender.averageCharacterCount));
        } else if (item.getId() == R.id.btn_openInviterProfile) {
          TdApi.ChatStatisticsInviterInfo sender = (TdApi.ChatStatisticsInviterInfo) item.getData();
          user = new TGUser(tdlib, tdlib.cache().user(sender.userId));
          user.setCustomStatus(Lang.plural(R.string.StatsXInvitations, sender.addedMemberCount));
        } else if (item.getId() == R.id.btn_viewAdminActions) {
          TdApi.ChatStatisticsAdministratorActionsInfo sender = (TdApi.ChatStatisticsAdministratorActionsInfo) item.getData();
          user = new TGUser(tdlib, tdlib.cache().user(sender.userId));
          StringBuilder customStatus = new StringBuilder();

          if (sender.deletedMessageCount > 0) {
            customStatus.append(Lang.plural(R.string.StatsXDeletions, sender.deletedMessageCount));
            if (sender.bannedUserCount > 0 || sender.restrictedUserCount > 0) customStatus.append(", ");
          }

          if (sender.bannedUserCount > 0) {
            customStatus.append(Lang.plural(R.string.StatsXBans, sender.bannedUserCount));
            if (sender.restrictedUserCount > 0) customStatus.append(", ");
          }

          if (sender.restrictedUserCount > 0) {
            customStatus.append(Lang.plural(R.string.StatsXRestrictions, sender.restrictedUserCount));
          }

          user.setCustomStatus(customStatus.toString());
        } else {
          throw new IllegalArgumentException("data = "+item.getData());
        }

        userView.setUser(user);
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
        previewView.setMessage(container.message, null, statString.toString(), false);
        previewView.setContentInset(Screen.dp(8));
        previewView.setTag(container);
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_members:
          case R.id.btn_membersReading:
          case R.id.btn_membersWriting:
          case R.id.btn_messages:
          case R.id.btn_share:
          case R.id.btn_view: {
            TdApi.StatisticalValue value = (TdApi.StatisticalValue) item.getData();
            view.setIgnoreEnabled(true);
            view.setEnabled(false);
            if (value.value == value.previousValue || value.previousValue == 0) {
              view.setTextColorId(0);
              view.setName(Strings.buildCounter((long) value.value));
            } else {
              view.setTextColorId(value.value > value.previousValue ? R.id.theme_color_textSecure : R.id.theme_color_textNegative);
              view.setName(Lang.getString(value.value > value.previousValue ? R.string.StatsValueGrowth : R.string.StatsValueFall, Strings.buildCounter((long) value.value), Strings.buildCounter((long) Math.abs(value.value - value.previousValue)), Lang.beautifyDouble(value.growthRatePercentage)));
            }
            view.setData(item.getString());
            break;
          }
          case R.id.btn_notifications: {
            view.setIgnoreEnabled(true);
            view.setEnabled(false);
            view.setTextColorId(0);
            view.setName(Lang.beautifyDouble(item.getDoubleValue()) + "%");
            view.setData(item.getString());
            break;
          }
        }
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.client().send(new TdApi.GetChatStatistics(chatId, Theme.isDark()), result -> {
      switch (result.getConstructor()) {
        case TdApi.ChatStatisticsChannel.CONSTRUCTOR:
          runOnUiThreadOptional(() -> {
            setStatistics((TdApi.ChatStatisticsChannel) result);
          });
          break;
        case TdApi.ChatStatisticsSupergroup.CONSTRUCTOR:
          runOnUiThreadOptional(() -> {
            setStatistics((TdApi.ChatStatisticsSupergroup) result);
          });
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
      }
    });
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

    setCharts(items, charts, () -> {
      if (statistics.topAdministrators.length > 0) {
        setTopUsers(statistics.period, statistics.topAdministrators, R.string.StatsTopAdmins, R.id.btn_viewAdminActions);
      }

      if (statistics.topSenders.length > 0) {
        messageSenderInfos = statistics.topSenders;
        setTopUsers(statistics.period, statistics.topSenders, R.string.StatsTopMembers, R.id.btn_viewMemberMessages);
      }

      if (statistics.topInviters.length > 0) {
        setTopUsers(statistics.period, statistics.topInviters, R.string.StatsTopInviters, R.id.btn_openInviterProfile);
      }

      executeScheduledAnimation();
    });
  }

  private void setTopUsers (TdApi.DateRange range, TdApi.Object[] users, @StringRes int header, @IdRes int id) {
    int currentSize = adapter.getItems().size();
    adapter.getItems().add(new ListItem(ListItem.TYPE_CHART_HEADER_DETACHED).setData(new MiniChart(header, range)));
    adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    int maxLength;
    if (users[0] instanceof TdApi.ChatStatisticsMessageSenderInfo) {
      maxLength = Math.min(10, users.length);
    } else {
      maxLength = users.length;
    }

    for (int i = 0; i < maxLength; i++) {
      adapter.getItems().add(new ListItem(ListItem.TYPE_USER, id).setData(users[i]));
      if (i != maxLength - 1) adapter.getItems().add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    if (maxLength < users.length) {
      adapter.getItems().add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      adapter.getItems().add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, 0, Lang.plural(R.string.StatsXShowMore, users.length - 10), false));
    }

    adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.notifyItemRangeInserted(currentSize, adapter.getItems().size());
  }

  private void showAllUsers () {
    final int index = adapter.indexOfViewById(R.id.btn_showAdvanced);

    if (index != -1 && messageSenderInfos != null) {
      List<ListItem> items = adapter.getItems();

      items.remove(index);

      ArrayList<ListItem> advancedItems = new ArrayList<>();

      for (int i = 10; i < messageSenderInfos.length; i++) {
        advancedItems.add(new ListItem(ListItem.TYPE_USER, R.id.btn_viewMemberMessages).setData(messageSenderInfos[i]));
        if (i != messageSenderInfos.length - 1) advancedItems.add(new ListItem(ListItem.TYPE_SEPARATOR));
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
    if (!Td.isEmpty(statistics.meanViewCount)) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_view, 0, R.string.StatsViews, false).setData(statistics.meanViewCount));
    }
    if (!Td.isEmpty(statistics.meanShareCount)) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_share, 0, R.string.StatsShares, false).setData(statistics.meanShareCount));
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
      if (statistics.recentMessageInteractions.length > 0) {
        setRecentMessageInteractions(statistics.period, statistics.recentMessageInteractions, R.string.StatsRecentPosts);
      } else {
        executeScheduledAnimation();
      }
    });
  }

  private void setRecentMessageInteractions (TdApi.DateRange range, TdApi.ChatStatisticsMessageInteractionInfo[] interactions, @StringRes int header) {
    loadInteractionMessages(interactions, () -> {
      int currentSize = adapter.getItems().size();
      adapter.getItems().add(new ListItem(ListItem.TYPE_CHART_HEADER_DETACHED).setData(new MiniChart(header, range)));
      adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      for (int i = 0; i < interactionMessages.size(); i++) {
        adapter.getItems().add(new ListItem(ListItem.TYPE_STATS_MESSAGE_PREVIEW, R.id.btn_messageMore).setData(interactionMessages.get(i)));
        if (i != interactionMessages.size() - 1) adapter.getItems().add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }

      adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.notifyItemRangeInserted(currentSize, adapter.getItems().size());

      executeScheduledAnimation();
    });
  }

  private void loadInteractionMessages (TdApi.ChatStatisticsMessageInteractionInfo[] interactions, Runnable onMessagesLoaded) {
    AtomicInteger remaining = new AtomicInteger(interactions.length);

    Client.ResultHandler handler = result -> {
      if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        TdApi.Message message = (TdApi.Message) result;

        if (message.mediaAlbumId != 0) {
          if (!interactionMessageAlbums.containsKey(message.mediaAlbumId)) {
            interactionMessageAlbums.put(message.mediaAlbumId, new ArrayList<>());
          }

          interactionMessageAlbums.get(message.mediaAlbumId).add(message);
        }

        if (message.canGetStatistics && (message.mediaAlbumId == 0 || currentMediaAlbumId != message.mediaAlbumId)) {
          currentMediaAlbumId = message.mediaAlbumId;
          interactionMessages.add(new MessageInteractionInfoContainer(
                  message,
                  interactions[interactions.length - remaining.get()]
          ));
        }
      }

      if (remaining.decrementAndGet() == 0) {
        runOnUiThreadOptional(onMessagesLoaded);
      }
    };

    for (TdApi.ChatStatisticsMessageInteractionInfo interaction : interactions) {
      tdlib.client().send(new TdApi.GetMessageLocally(getArgumentsStrict().chatId, interaction.messageId), handler);
    }
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
    public final TdApi.ChatStatisticsMessageInteractionInfo messageInteractionInfo;

    public MessageInteractionInfoContainer (TdApi.Message message, TdApi.ChatStatisticsMessageInteractionInfo messageInteractionInfo) {
      this.message = message;
      this.messageInteractionInfo = messageInteractionInfo;
    }
  }
}
