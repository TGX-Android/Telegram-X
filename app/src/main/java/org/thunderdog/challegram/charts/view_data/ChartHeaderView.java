package org.thunderdog.challegram.charts.view_data;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.BaseChartView;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.concurrent.TimeUnit;

public class ChartHeaderView extends FrameLayout implements Chart.DateListener, ThemeInvalidateListener {

    private TextView title;
    private TextView dates;
    private TextView datesTmp;
    public TextView back;
    private boolean showDate = true;

    private Drawable zoomIcon;

    int textMargin;

    public ChartHeaderView(Context context) {
        super(context);
        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(14);
        textPaint.setTypeface(Fonts.getRobotoMedium());
        textMargin = (int) textPaint.measureText("00 MMM 0000 - 00 MMM 000");

        title = new TextView(context);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, textMargin, 0));

        back = new TextView(context);
        back.setTextSize(15);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        addView(back, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

        dates = new TextView(context);
        dates.setTextSize(13);
        dates.setTypeface(Fonts.getRobotoMedium());
        dates.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        addView(dates, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

        datesTmp = new TextView(context);
        datesTmp.setTextSize(13);
        datesTmp.setTypeface(Fonts.getRobotoMedium());
        datesTmp.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        addView(datesTmp, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        datesTmp.setVisibility(View.GONE);


        back.setVisibility(View.GONE);
        back.setText(Lang.getString(R.string.ZoomOut));
        zoomIcon = ContextCompat.getDrawable(getContext(), R.drawable.baseline_zoom_in_24);
        back.setCompoundDrawablesWithIntrinsicBounds(zoomIcon, null, null, null);
        back.setCompoundDrawablePadding(Screen.dp(4));
        back.setPadding(Screen.dp(8), Screen.dp(4), Screen.dp(8), Screen.dp(4));
        back.setBackground(Theme.getRoundRectSelectorDrawable(Theme.getColor(R.id.theme_color_textNegative))); // key_featuredStickers_removeButtonText

        datesTmp.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            datesTmp.setPivotX(datesTmp.getMeasuredWidth() * 0.7f);
            dates.setPivotX(dates.getMeasuredWidth() * 0.7f);
        });
        recolor();
    }


    public void recolor() {
        title.setTextColor(Theme.textAccentColor()); // key_dialogTextBlack
        dates.setTextColor(Theme.textAccentColor()); // key_dialogTextBlack
        datesTmp.setTextColor(Theme.textAccentColor()); // key_dialogTextBlack
        back.setTextColor(Theme.getColor(R.id.theme_color_textNeutral)); // key_statisticChartBackZoomColor
        zoomIcon.setColorFilter(Theme.getColor(R.id.theme_color_textNeutral), PorterDuff.Mode.SRC_IN); // key_statisticChartBackZoomColor
    }

    public void setDates(long start, long end) {
        if (!showDate) {
            dates.setVisibility(GONE);
            datesTmp.setVisibility(GONE);
            return;
        }
        final String newText;
        if (end - start >= TimeUnit.DAYS.toMillis(1)) {
            newText = Lang.getDateRange(start, end, TimeUnit.MILLISECONDS, false);
        } else {
            newText = Lang.getDatestamp(end, TimeUnit.MILLISECONDS);
        }

        dates.setText(newText);
        dates.setVisibility(View.VISIBLE);
    }

    public void setTitle(String s) {
        title.setText(s);
    }

    public void zoomTo(BaseChartView<?, ?> chartView, long d, boolean animate) {
        setDates(d, d);
        back.setVisibility(View.VISIBLE);

        if (animate) {
            back.setAlpha(0);
            back.setScaleX(0.3f);
            back.setScaleY(0.3f);
            back.setPivotX(0);
            back.setPivotY(Screen.dp(40));
            back.animate().alpha(1f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(200)
                    .start();

            title.setAlpha(1f);
            title.setTranslationX(0);
            title.setTranslationY(0);
            title.setScaleX(1f);
            title.setScaleY(1f);
            title.setPivotX(0);
            title.setPivotY(0);
            title.animate()
                    .alpha(0f)
                    .scaleY(0.3f)
                    .scaleX(0.3f)
                    .setDuration(200)
                    .start();
        } else {
            back.setAlpha(1f);
            back.setTranslationX(0);
            back.setTranslationY(0);
            title.setAlpha(0f);
        }
    }

    public void zoomOut(BaseChartView<?, ?> chartView, boolean animated) {
        setDates(chartView.getStartDate(), chartView.getEndDate());
        if (animated) {
            title.setAlpha(0);
            title.setScaleX(0.3f);
            title.setScaleY(0.3f);
            title.setPivotX(0);
            title.setPivotY(0);
            title.animate().alpha(1f)
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(200)
                    .start();

            back.setAlpha(1f);
            back.setTranslationX(0);
            back.setTranslationY(0);
            back.setScaleX(1f);
            back.setScaleY(1f);
            back.setPivotY(Screen.dp(40));
            back.animate()
                    .alpha(0f)
                    .scaleY(0.3f)
                    .scaleX(0.3f)
                    .setDuration(200)
                    .start();
        } else {
            title.setAlpha(1f);
            title.setScaleX(1f);
            title.setScaleY(1f);
            back.setAlpha(0);
        }
    }

    public void showDate(boolean b) {
        if (this.showDate == b)
            return;
        showDate = b;
        if (!showDate) {
            datesTmp.setVisibility(GONE);
            dates.setVisibility(GONE);
            title.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
            title.requestLayout();
        }  else {
            title.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, textMargin, 0));
        }
    }

    // MODIFIED PART

    private Chart chart;

    public void setChart (Chart chart) {
        if (this.chart != chart) {
            if (this.chart != null)
                this.chart.detach(this);
            this.chart = chart;
            if (chart != null) {
                chart.attach(this);
                Views.setMediumText(title, Lang.getString(chart.getTitle()));
                if (chart.hasTimePeriod()) {
                    showDate(true);
                    setDates(chart.getStartTime(), chart.getEndTime());
                } else {
                    showDate(false);
                }
            }
        }
    }

    @Override
    public void onChartDateChanged (long startTimeMs, long endTimeMs) {
        if (startTimeMs > 0 && endTimeMs > 0) {
            showDate(true);
            setDates(startTimeMs, endTimeMs);
        } else {
            showDate(false);
        }
    }

    @Override
    public void onThemeInvalidate (boolean isTempUpdate) {
        recolor();
        invalidate();
    }
}
