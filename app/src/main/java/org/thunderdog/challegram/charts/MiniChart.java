package org.thunderdog.challegram.charts;

import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;

public class MiniChart {
    @StringRes public final int titleRes;
    public final TdApi.DateRange dateRange;

    public MiniChart (@StringRes int titleRes, TdApi.DateRange dateRange) {
        this.titleRes = titleRes;
        this.dateRange = dateRange;
    }
}
