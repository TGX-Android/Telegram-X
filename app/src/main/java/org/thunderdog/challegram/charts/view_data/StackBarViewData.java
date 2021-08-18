package org.thunderdog.challegram.charts.view_data;

import android.graphics.Paint;

import androidx.core.graphics.ColorUtils;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.charts.data.ChartData;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

public class StackBarViewData extends LineViewData {

    public final Paint unselectedPaint = new Paint();
    public int blendColor = 0;

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

    public StackBarViewData(ChartData.Line line) {
        super(line);
        paint.setStrokeWidth(Screen.dpf(1));
        paint.setStyle(Paint.Style.STROKE);
        unselectedPaint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(false);
    }
}
