package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.MiniChart;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.MessagePreviewView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.td.MessageId;

public class MessageStatisticsController extends RecyclerViewController<MessageStatisticsController.Args> implements View.OnClickListener {
  public static class Args {
    public final long chatId;
    public @Nullable TdApi.Message message;
    public @Nullable List<TdApi.Message> album;

    public Args (long chatId, TdApi.Message message) {
      this.chatId = chatId;
      this.message = message;
    }

    public Args (long chatId, @NonNull List<TdApi.Message> album) {
      this.chatId = chatId;
      this.album = album;
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
    } else if (v.getId() == R.id.btn_messageMore) {
      TdApi.Message message = (TdApi.Message) v.getTag();
      MessageStatisticsController msc = new MessageStatisticsController(context, tdlib);
      msc.setArguments(new MessageStatisticsController.Args(getArgumentsStrict().chatId, message));
      navigateTo(msc);
    } else if (v.getId() == R.id.btn_openChat) {
      tdlib.ui().openMessage(this, (TdApi.Message) v.getTag(), null);
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
          case R.id.btn_statsPublishDate:
          case R.id.btn_statsPublicShares: {
            view.setIgnoreEnabled(true);
            view.setEnabled(false);
            view.setTextColorId(0);
            if (item.getData() instanceof String) {
              view.setName(item.getData().toString());
            } else {
              view.setName(Strings.buildCounter((int) item.getData()));
            }
            view.setData(item.getString());
            break;
          }
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        TdApi.Message msg = (TdApi.Message) item.getData();
        chatView.setMessage(new TGFoundMessage(tdlib, new TdApi.ChatListMain(), tdlib.chat(getArgumentsStrict().chatId), msg, ""));
        chatView.setTag(msg);
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


      @Override
      protected void setMessagePreview (ListItem item, int position, MessagePreviewView previewView) {
        StringBuilder statString = new StringBuilder();
        TdApi.Message message = (TdApi.Message) item.getData();

        statString.append(Lang.plural(R.string.xViews, message.interactionInfo.viewCount));
        if (message.interactionInfo.forwardCount > 0) {
          statString.append(", ").append(Lang.plural(R.string.StatsXShared, message.interactionInfo.forwardCount));
        }

        RippleSupport.setSimpleWhiteBackground(previewView);
        previewView.setMessage(message, null, statString.toString(), true);
        previewView.setContentInset(Screen.dp(8));
        previewView.setTag(message);
      }
    };
    recyclerView.setAdapter(adapter);

    if (getArgumentsStrict().album != null) {
      setAlbum(getArgumentsStrict().album);
    } else {
      tdlib.client().send(new TdApi.GetMessageStatistics(getArgumentsStrict().chatId, getArgumentsStrict().message.id, Theme.isDark()), result -> {
        switch (result.getConstructor()) {
          case TdApi.MessageStatistics.CONSTRUCTOR:
            tdlib.client().send(new TdApi.GetMessagePublicForwards(getArgumentsStrict().chatId, getArgumentsStrict().message.id, "", 20), result2 -> {
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
  }

  @Override
  public int getId () {
    return R.id.controller_stats_message;
  }

  private TdApi.MessageStatistics statistics;
  private int pendingRequests;

  private void setAlbum (List<TdApi.Message> album) {
    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.general_Messages));

    boolean first = true;
    for (TdApi.Message albumMessage : album) {
      if (first) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_STATS_MESSAGE_PREVIEW, R.id.btn_messageMore).setData(albumMessage));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    executeScheduledAnimation();
  }

  private void setStatistics (TdApi.MessageStatistics statistics) {
    this.statistics = statistics;

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.btn_openChat).setData(getArgumentsStrict().message));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsViewCount, 0, R.string.StatsMessageViewCount, false).setData(getArgumentsStrict().message.interactionInfo.viewCount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsPrivateShares, 0, R.string.StatsMessageSharesPrivate, false).setData(getArgumentsStrict().message.interactionInfo.forwardCount));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsPublishDate, 0, R.string.StatsMessagePublishDate, false).setData(Lang.getTimestamp(getArgumentsStrict().message.date, TimeUnit.SECONDS)));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    List<Chart> charts = Collections.singletonList(
      new Chart(R.id.stats_messageInteraction, tdlib, getArgumentsStrict().chatId, R.string.StatsChartInteractions, ChartDataUtil.TYPE_LINEAR, statistics.messageInteractionGraph, 0)
    );

    setCharts(items, charts, this::setPublicShares);
  }

  private void setPublicShares () {
    if (publicShares == null || publicShares.messages.length == 0) return;
    final int index = adapter.indexOfViewById(R.id.btn_statsPublishDate) + 1;
    adapter.getItems().add(index, new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsPublicShares, 0, R.string.StatsMessageSharesPublic, false).setData(publicShares.totalCount));
    adapter.getItems().add(index, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    adapter.notifyItemRangeInserted(index, 2);

    final int initialListSize = adapter.getItemCount();

    adapter.getItems().add(new ListItem(ListItem.TYPE_CHART_HEADER_DETACHED).setData(new MiniChart(R.string.StatsMessageSharesPublic, null)));
    adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < publicShares.messages.length; i++) {
      adapter.getItems().add(new ListItem(ListItem.TYPE_USER, R.id.chat).setData(publicShares.messages[i]));
      if (i != publicShares.messages.length - 1)
        adapter.getItems().add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
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
