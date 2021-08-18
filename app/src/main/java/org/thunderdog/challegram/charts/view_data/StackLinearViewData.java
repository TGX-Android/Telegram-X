package org.thunderdog.challegram.charts.view_data;

import android.graphics.Paint;

import org.thunderdog.challegram.charts.BaseChartView;
import org.thunderdog.challegram.charts.data.ChartData;

public class StackLinearViewData extends LineViewData {

    public StackLinearViewData(ChartData.Line line) {
        super(line);
        paint.setStyle(Paint.Style.FILL);
        if (BaseChartView.USE_LINES) {
            paint.setAntiAlias(false);
        }
    }
}
