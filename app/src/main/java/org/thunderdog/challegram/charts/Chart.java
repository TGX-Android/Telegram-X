package org.thunderdog.challegram.charts;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONException;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.charts.data.ChartData;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;

import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.unit.BitwiseUtils;

public class Chart implements BaseChartView.DateChangeListener {
  public interface Listener {
    void onChartDataChanged (Chart chart, ChartData newData);
  }

  public interface DateListener {
    void onChartDateChanged (long startTimeMs, long endTimeMs);
  }

  public static final int FLAG_NO_DATE = 1;

  private final int id;
  private final Tdlib tdlib;
  private final long chatId;
  private final int flags;

  private int type;
  @StringRes
  private int titleRes;
  private final TdApi.StatisticalGraph graph;
  private ChartData baseData;
  private String errorText;

  public Chart (@IdRes int id, Tdlib tdlib, long chatId, @StringRes int titleRes, int type, TdApi.StatisticalGraph graph, int flags) {
    this.id = id;
    this.tdlib = tdlib;
    this.chatId = chatId;
    this.flags = flags;
    this.type = type;
    this.titleRes = titleRes;
    this.graph = graph;
    setGraph(graph);
  }

  @Nullable
  public ChartData getBaseData () {
    return baseData;
  }

  public boolean hasData () {
    return errorText != null || baseData != null;
  }

  public boolean isError () {
    return graph.getConstructor() == TdApi.StatisticalGraphError.CONSTRUCTOR;
  }

  public boolean isAsync () {
    return graph.getConstructor() == TdApi.StatisticalGraphAsync.CONSTRUCTOR;
  }

  @StringRes
  public int getTitle () {
    return titleRes;
  }

  public int getViewType () {
    switch (type) {
      case ChartDataUtil.TYPE_LINEAR:
        return ListItem.TYPE_CHART_LINEAR;
      case ChartDataUtil.TYPE_DOUBLE_LINEAR:
        return ListItem.TYPE_CHART_DOUBLE_LINEAR;
      case ChartDataUtil.TYPE_STACK_BAR:
        return ListItem.TYPE_CHART_STACK_BAR;
      case ChartDataUtil.TYPE_STACK_PIE:
        return ListItem.TYPE_CHART_STACK_PIE;
    }
    throw new IllegalStateException("type == " + type);
  }

  private void setGraph (TdApi.StatisticalGraph graph) {
    switch (graph.getConstructor()) {
      case TdApi.StatisticalGraphData.CONSTRUCTOR: {
        boolean success = false;
        try {
          this.baseData = ChartDataUtil.create((TdApi.StatisticalGraphData) graph, type);
          this.errorText = null;
          success = true;
        } catch (JSONException e) {
          Log.e("Unable to parse statistics: %s", e, graph);
        }
        if (!success) {
          return;
        }
        break;
      }
      case TdApi.StatisticalGraphError.CONSTRUCTOR: {
        this.errorText = ((TdApi.StatisticalGraphError) graph).errorMessage;
        this.baseData = null;
        break;
      }
      case TdApi.StatisticalGraphAsync.CONSTRUCTOR: {
        return;
      }
    }
    for (Listener listener : listeners) {
      listener.onChartDataChanged(this, this.baseData);
    }
  }

  // Triggers

  private long startTime = -1, endTime = -1;

  public boolean hasTimePeriod () {
    return startTime > 0 && endTime > 0;
  }

  public long getStartTime () {
    return startTime;
  }

  public long getEndTime () {
    return endTime;
  }

  @Override
  public void onDateChanged (BaseChartView chartView, long startTimeMs, long endTimeMs) {
    this.startTime = startTimeMs;
    this.endTime = endTimeMs;
    for (DateListener listener : dateListeners) {
      listener.onChartDateChanged(startTimeMs, endTimeMs);
    }
  }

  private boolean isLoading;
  private final ReferenceList<Listener> listeners = new ReferenceList<>();
  private final ReferenceList<DateListener> dateListeners = new ReferenceList<>();

  public void attach (Listener listener) {
    listeners.add(listener);
    if (isAsync()) {
      load(null);
    }
  }

  public int getId () {
    return id;
  }

  public void load (RunnableBool callback) {
    if (!isLoading) {
      isLoading = true;
      tdlib.client().send(new TdApi.GetStatisticalGraph(chatId, ((TdApi.StatisticalGraphAsync) graph).token, 0), result -> {
        UI.post((() -> {
          if (result.getConstructor() != TdApi.Error.CONSTRUCTOR) {
            setGraph((TdApi.StatisticalGraph) result);
            if (callback != null)
              callback.runWithBool(result.getConstructor() != TdApi.StatisticalGraphError.CONSTRUCTOR);
          } else {
            setGraph(new TdApi.StatisticalGraphError(TD.toErrorString(result)));
            if (callback != null)
              callback.runWithBool(false);
          }
        }));
      });
    }
  }

  public void detach (Listener listener) {
    listeners.remove(listener);
  }

  public boolean isNoDate () {
    return BitwiseUtils.getFlag(flags, FLAG_NO_DATE);
  }

  public void attach (DateListener listener) {
    if (!isNoDate()) {
      dateListeners.add(listener);
    }
  }

  public void detach (DateListener listener) {
    if (!isNoDate()) {
      dateListeners.remove(listener);
    }
  }
}
