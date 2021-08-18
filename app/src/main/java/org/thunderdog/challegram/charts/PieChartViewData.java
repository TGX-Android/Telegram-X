package org.thunderdog.challegram.charts;

import android.animation.Animator;

import org.thunderdog.challegram.charts.data.ChartData;
import org.thunderdog.challegram.charts.view_data.StackLinearViewData;

public class PieChartViewData extends StackLinearViewData {

    float selectionA;
    float drawingPart;
    Animator animator;

    public PieChartViewData(ChartData.Line line) {
        super(line);
    }
}
