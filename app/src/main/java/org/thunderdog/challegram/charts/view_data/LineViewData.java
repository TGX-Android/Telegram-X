package org.thunderdog.challegram.charts.view_data;

import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.core.graphics.ColorUtils;

import org.thunderdog.challegram.charts.BaseChartView;
import org.thunderdog.challegram.charts.data.ChartData;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;


public class LineViewData {

    public final ChartData.Line line;
    public final Paint bottomLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public final Path bottomLinePath = new Path();
    public final Path chartPath = new Path();
    public final Path chartPathPicker = new Path();
    public ValueAnimator animatorIn;
    public ValueAnimator animatorOut;
    public int linesPathBottomSize;

    public float[] linesPath;
    public float[] linesPathBottom;

    public int lineColor;

    public boolean enabled = true;

    public float alpha = 1f;

    public LineViewData(ChartData.Line line) {
        this.line = line;

        paint.setStrokeWidth(Screen.dpf(2));
        paint.setStyle(Paint.Style.STROKE);
        if (!BaseChartView.USE_LINES) {
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        paint.setColor(line.color);

        bottomLinePaint.setStrokeWidth(Screen.dpf(1));
        bottomLinePaint.setStyle(Paint.Style.STROKE);
        bottomLinePaint.setColor(line.color);

        selectionPaint.setStrokeWidth(Screen.dpf(10));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeCap(Paint.Cap.ROUND);
        selectionPaint.setColor(line.color);


        linesPath = new float[line.y.length << 2];
        linesPathBottom = new float[line.y.length << 2];
    }

    public void updateColors() {
        /*TODO if (line.colorKey != null && Theme.hasThemeKey(line.colorKey)) {
            lineColor = Theme.getColor(line.colorKey);
        } else {*/
            int color = Theme.fillingColor(); // key_windowBackgroundWhite
            boolean darkBackground = ColorUtils.calculateLuminance(color) < 0.5f;
            lineColor = darkBackground ? line.colorDark : line.color;
        //}
        paint.setColor(lineColor);
        bottomLinePaint.setColor(lineColor);
        selectionPaint.setColor(lineColor);
    }
}
