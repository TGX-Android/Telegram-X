package org.thunderdog.challegram.charts.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONException;
import org.json.JSONObject;

public class ChartDataUtil {
  public static final int TYPE_LINEAR = 0;
  public static final int TYPE_DOUBLE_LINEAR = 1;
  public static final int TYPE_STACK_BAR = 2;
  public static final int TYPE_STACK_PIE = 4;

  public static ChartData create (TdApi.StatisticalGraphData data, int type) throws JSONException {
    JSONObject json = new JSONObject(data.jsonData);
    switch (type) {
      case TYPE_LINEAR:
        return new ChartData(json);
      case TYPE_DOUBLE_LINEAR:
        return new DoubleLinearChartData(json);
      case TYPE_STACK_BAR:
        return new StackBarChartData(json);
      case TYPE_STACK_PIE:
        return new StackLinearChartData(json);
    }
    throw new IllegalArgumentException("type == " + type);
  }
}
