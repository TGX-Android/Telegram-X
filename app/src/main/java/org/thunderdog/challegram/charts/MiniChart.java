package org.thunderdog.challegram.charts;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;

public class MiniChart {
  public final @StringRes int titleRes;
  public final @Nullable TdApi.DateRange dateRange;

  public MiniChart (@StringRes int titleRes, @Nullable TdApi.DateRange dateRange) {
    this.titleRes = titleRes;
    this.dateRange = dateRange;
  }
}
