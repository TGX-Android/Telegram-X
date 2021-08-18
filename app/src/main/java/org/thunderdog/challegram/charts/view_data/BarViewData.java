package org.thunderdog.challegram.charts.view_data;

import android.graphics.Paint;

import androidx.core.graphics.ColorUtils;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.charts.data.ChartData;
import org.thunderdog.challegram.theme.Theme;

public class BarViewData extends LineViewData {


    public final Paint unselectedPaint = new Paint();

    public int blendColor = 0;

    public BarViewData(ChartData.Line line) {
        super(line);
        paint.setStyle(Paint.Style.STROKE);
        unselectedPaint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(false);
    }

    public void updateColors() {
        super.updateColors();
        blendColor = ColorUtils.blendARGB(Theme.fillingColor(),lineColor,0.3f); // key_windowBackgroundWhite
    }

    public boolean canBlend () {
        if (blendColor == 0) {
            updateColors();
            if (blendColor == 0)
                Log.e("blendColor is empty", Log.generateException());
        }
        return blendColor != 0;
    }
}
