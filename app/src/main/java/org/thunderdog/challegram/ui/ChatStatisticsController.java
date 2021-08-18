package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.td.Td;

/**
 * Date: 2019-04-21
 * Author: default
 */
public class ChatStatisticsController extends RecyclerViewController<ChatStatisticsController.Args> {
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

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
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

    setCharts(items, charts);
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

    // TODO statistics.recentMessageInteractions

    setCharts(items, charts);
  }

  private void setCharts (List<ListItem> items, List<Chart> charts) {
    if (charts != null) {
      for (Chart chart : charts) {
        if (chart.isAsync()) {
          pendingRequests++;
          chart.load(hasData -> {
            if (isDestroyed())
              return;
            if (--pendingRequests == 0)
              executeScheduledAnimation();
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
                new ListItem(ListItem.TYPE_SHADOW_TOP),
                new ListItem(ListItem.TYPE_CHART_HEADER).setData(chart),
                new ListItem(chart.getViewType(), chart.getId()).setData(chart),
                new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
              );
            }
          });
        } else if (!chart.isError()) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_CHART_HEADER).setData(chart));
          items.add(new ListItem(chart.getViewType(), chart.getId()).setData(chart));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }
      }
    }

    adapter.setItems(items, false);

    if (pendingRequests == 0)
      executeScheduledAnimation();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return statistics == null || pendingRequests > 0;
  }
}
