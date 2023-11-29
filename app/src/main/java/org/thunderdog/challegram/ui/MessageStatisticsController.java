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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
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
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.SeparatorView;

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
    ListItem item = (ListItem) v.getTag();
    if (v.getId() == R.id.chat) {
      TGUser user = ((UserView) v).getUser();
      if (user != null) {
        tdlib.ui().openChat(this, user.getChatId(), new TdlibUi.ChatOpenParameters().highlightMessage(new MessageId(user.getChatId(), ((TdApi.Message) item.getData()).id)).keepStack());
      }
    } else if (v.getId() == R.id.btn_messageMore) {
      TdApi.Message message = (TdApi.Message) item.getData();
      MessageStatisticsController msc = new MessageStatisticsController(context, tdlib);
      msc.setArguments(new MessageStatisticsController.Args(getArgumentsStrict().chatId, message));
      navigateTo(msc);
    } else if (v.getId() == R.id.btn_openChat) {
      tdlib.ui().openMessage(this, (TdApi.Message) item.getData(), null);
    }
  }

  private TdlibMessageViewer.Viewport messageViewport;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    messageViewport = tdlib.messageViewer().createViewport(new TdApi.MessageSourceSearch(), this);
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
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_statsViewCount ||
          itemId == R.id.btn_statsPrivateShares ||
          itemId == R.id.btn_statsPublishDate ||
          itemId == R.id.btn_statsSignature ||
          itemId == R.id.btn_statsPublicShares) {
          view.setIgnoreEnabled(true);
          view.setEnabled(false);
          view.setTextColorId(ColorId.NONE);
          if (item.getData() instanceof String) {
            view.setName(item.getData().toString());
          } else {
            view.setName(Strings.buildCounter((int) item.getData()));
          }
          view.setData(item.getString());
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        TdApi.Message msg = (TdApi.Message) item.getData();
        chatView.setMessage(new TGFoundMessage(tdlib, new TdApi.ChatListMain(), tdlib.chat(getArgumentsStrict().chatId), msg, ""));
      }

      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        TdApi.Message msg = (TdApi.Message) item.getData();
        TdApi.Chat chat = tdlib.chat(msg.chatId);

        TGUser user = new TGUser(tdlib, chat);
        user.setChat(msg.chatId, chat);
        if (msg.interactionInfo != null) {
          user.setCustomStatus(Lang.plural(R.string.xViews, msg.interactionInfo.viewCount));
        } else {
          user.setCustomStatus("");
        }
        
        user.chatTitleAsUserName();

        userView.setUser(user);
        userView.setPreviewChatId(null, msg.chatId, null, new MessageId(msg.chatId, msg.id), null);
      }


      @Override
      protected void setMessagePreview (ListItem item, int position, MessagePreviewView previewView) {
        TdApi.Message message = (TdApi.Message) item.getData();

        if (message.interactionInfo != null) {
          StringBuilder statString = new StringBuilder();
          statString.append(Lang.plural(R.string.xViews, message.interactionInfo.viewCount));
          if (message.interactionInfo.forwardCount > 0) {
            statString.append(", ").append(Lang.plural(R.string.StatsXShared, message.interactionInfo.forwardCount));
          }
          previewView.setMessage(message, null, statString.toString(), MessagePreviewView.Options.IGNORE_ALBUM_REFRESHERS);

        } else {
          previewView.setMessage(message, null, null, MessagePreviewView.Options.NONE);
        }

        RippleSupport.setSimpleWhiteBackground(previewView);
        previewView.setContentInset(Screen.dp(8));
      }
    };
    tdlib.ui().attachViewportToRecyclerView(messageViewport, recyclerView);
    recyclerView.setAdapter(adapter);

    if (getArgumentsStrict().album != null) {
      setAlbum(getArgumentsStrict().album);
    } else {
      long chatId = getArgumentsStrict().chatId;
      long messageId = getArgumentsStrict().message.id;
      tdlib.send(new TdApi.GetMessageStatistics(chatId, messageId, Theme.isDark()), (messageStatistics, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showError(error);
        } else {
          tdlib.send(new TdApi.GetMessagePublicForwards(chatId, messageId, null, 20), (foundMessages, error1) -> runOnUiThreadOptional(() -> {
            if (foundMessages != null) {
              publicShares = foundMessages;
            }
            setStatistics(messageStatistics);
          }));
        }
      }));
    }
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
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL, R.id.separator));
      }
      items.add(new ListItem(ListItem.TYPE_STATS_MESSAGE_PREVIEW, R.id.btn_messageMore).setData(albumMessage));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    executeScheduledAnimation();
  }

  private void setStatistics (TdApi.MessageStatistics statistics) {
    this.statistics = statistics;

    int privateShareCount = getArgumentsStrict().message.interactionInfo.forwardCount;
    if (publicShares != null) {
      privateShareCount -= publicShares.totalCount;
    }

    TdApi.Message message = getArgumentsStrict().message;
    if (message == null) {
      throw new NullPointerException();
    }

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.btn_openChat).setData(getArgumentsStrict().message));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    if (message.interactionInfo != null) {
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsViewCount, 0, R.string.StatsMessageViewCount, false).setData(message.interactionInfo.viewCount));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    if (message.authorSignature.length() > 0) {
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsSignature, 0, R.string.StatsMessageSignature, false).setData(message.authorSignature));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsPublishDate, 0, R.string.StatsMessagePublishDate, false).setData(Lang.getTimestamp(message.date, TimeUnit.SECONDS)));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_statsPrivateShares, 0, R.string.StatsMessageSharesPrivate, false).setData(privateShareCount));
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
