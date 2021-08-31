package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.MiniChart;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.td.MessageId;

public class MessageStatisticsController extends RecyclerViewController<MessageStatisticsController.Args> implements View.OnClickListener {
  public static class Args {
    public final long chatId;
    public final long messageId;
    public final TdApi.MessageInteractionInfo interactionInfo;

    public Args (long chatId, long messageId, TdApi.MessageInteractionInfo interactionInfo) {
      this.chatId = chatId;
      this.messageId = messageId;
      this.interactionInfo = interactionInfo;
    }
  }

  public MessageStatisticsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private SettingsAdapter adapter;
  private TdApi.FoundMessages publicShares;

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.StatsMessageInfo);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.chat) {
      TGUser user = ((UserView) v).getUser();
      if (user != null) {
        tdlib.ui().openChat(this, user.getChatId(), new TdlibUi.ChatOpenParameters().highlightMessage(new MessageId(user.getChatId(), (long) v.getTag())).keepStack());
      }
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_statsViewCount:
          case R.id.btn_statsPrivateShares:
          case R.id.btn_statsPublicShares: {
            int value = (int) item.getData();
            view.setIgnoreEnabled(true);
            view.setEnabled(false);
            view.setTextColorId(0);
            view.setName(Strings.buildCounter((long) value));
            view.setData(item.getString());
            break;
          }
        }
      }

      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        TdApi.Message msg = (TdApi.Message) item.getData();
        TdApi.Chat chat = tdlib.chat(msg.chatId);

        TGUser user = new TGUser(tdlib, chat);
        user.setChat(msg.chatId, chat);
        user.setCustomStatus(Lang.plural(R.string.xViews, msg.interactionInfo.viewCount));
        user.chatTitleAsUserName();

        userView.setUser(user);
        userView.setTag(msg.id);
        userView.setPreviewChatId(null, msg.chatId, null, new MessageId(msg.chatId, msg.id), null);
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.client().send(new TdApi.GetMessageStatistics(getArgumentsStrict().chatId, getArgumentsStrict().messageId, Theme.isDark()), result -> {
      switch (result.getConstructor()) {
        case TdApi.MessageStatistics.CONSTRUCTOR:
          tdlib.client().send(new TdApi.GetMessagePublicForwards(getArgumentsStrict().chatId, getArgumentsStrict().messageId, "", 20), result2 -> {
            if (result2.getConstructor() == TdApi.FoundMessages.CONSTRUCTOR) {
              publicShares = (TdApi.FoundMessages) result2;
            }

            runOnUiThreadOptional(() -> {
              setStatistics((TdApi.MessageStatistics) result);
            });
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
    return R.id.controller_stats_message;
  }

  private TdApi.MessageStatistics statistics;
  private int pendingRequests;

  private void setStatistics (TdApi.MessageStatistics statistics) {
    this.statistics = statistics;

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsViewCount, 0, R.string.StatsMessageViewCount, false).setData(getArgumentsStrict().interactionInfo.viewCount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsPrivateShares, 0, R.string.StatsMessageSharesPrivate, false).setData(getArgumentsStrict().interactionInfo.forwardCount));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    List<Chart> charts = Collections.singletonList(
            new Chart(R.id.stats_messageInteraction, tdlib, getArgumentsStrict().chatId, R.string.StatsChartInteractions, ChartDataUtil.TYPE_LINEAR, statistics.messageInteractionGraph, 0)
    );

    setCharts(items, charts, this::setPublicShares);
  }

  private void setPublicShares () {
    if (publicShares == null || publicShares.messages.length == 0) return;
    final int index = adapter.indexOfViewById(R.id.btn_statsPrivateShares) + 1;
    adapter.getItems().add(index, new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsPublicShares, 0, R.string.StatsMessageSharesPublic, false).setData(publicShares.totalCount));
    adapter.getItems().add(index, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    adapter.notifyItemRangeInserted(index, 2);

    final int initialListSize = adapter.getItemCount();

    adapter.getItems().add(new ListItem(ListItem.TYPE_CHART_HEADER_DETACHED).setData(new MiniChart(R.string.StatsMessageSharesPublic, null)));
    adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < publicShares.messages.length; i++) {
      adapter.getItems().add(new ListItem(ListItem.TYPE_USER, R.id.chat).setData(publicShares.messages[i]));
      if (i != publicShares.messages.length - 1) adapter.getItems().add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.notifyItemRangeInserted(initialListSize, adapter.getItems().size());
  }

  private void setCharts (List<ListItem> items, List<Chart> charts, Runnable onChartsLoaded) {
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
      executeScheduledAnimation();
      onChartsLoaded.run();
    }
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return statistics == null || pendingRequests > 0;
  }
}
