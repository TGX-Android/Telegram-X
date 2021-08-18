package org.thunderdog.challegram.charts;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.charts.data.ChartData;
import org.thunderdog.challegram.charts.view_data.ChartBottomSignatureData;
import org.thunderdog.challegram.charts.view_data.ChartHorizontalLinesData;
import org.thunderdog.challegram.charts.view_data.LegendSignatureView;
import org.thunderdog.challegram.charts.view_data.LineViewData;
import org.thunderdog.challegram.charts.view_data.TransitionParams;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class BaseChartView<T extends ChartData, L extends LineViewData> extends View implements ChartPickerDelegate.Listener, ThemeInvalidateListener {

    public SharedUiComponents sharedUiComponents;
    ArrayList<ChartHorizontalLinesData> horizontalLines = new ArrayList<>(10);
    ArrayList<ChartBottomSignatureData> bottomSignatureDate = new ArrayList<>(25);

    public ArrayList<L> lines = new ArrayList<>();

    private final int ANIM_DURATION = 400;
    private final static float LINE_WIDTH = 1;
    private final float SELECTED_LINE_WIDTH = Screen.dpf(1.5f);
    private final float SIGNATURE_TEXT_SIZE = Screen.dpf(12f);
    private final int BOTTOM_SIGNATURE_TEXT_HEIGHT = Screen.dp(14f);
    private final int PICKER_CAPTURE_WIDTH = Screen.dp(24);
    private final int BOTTOM_SIGNATURE_OFFSET = Screen.dp(10);

    private final int DP_12 = Screen.dp(12);
    private final int DP_6 = Screen.dp(6);
    private final int DP_5 = Screen.dp(5);
    private final int DP_2 = Screen.dp(2);
    private final int DP_1 = Screen.dp(1);

    protected boolean drawPointOnSelection = true;
    float signaturePaintAlpha;
    float bottomSignaturePaintAlpha;
    int hintLinePaintAlpha;
    int chartActiveLineAlpha;

    public final static boolean USE_LINES = Build.VERSION.SDK_INT < Build.VERSION_CODES.P;
    protected final static boolean ANIMATE_PICKER_SIZES = Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP;
    public static FastOutSlowInInterpolator INTERPOLATOR = new FastOutSlowInInterpolator();

    int chartBottom;
    public float currentMaxHeight = 250;
    public float currentMinHeight = 0;

    float animateToMaxHeight = 0;
    float animateToMinHeight = 0;


    float thresholdMaxHeight = 0;

    int startXIndex;
    int endXIndex;
    boolean invalidatePickerChart = true;

    public boolean enabled = true;


    Paint emptyPaint = new Paint();

    Paint linePaint = new Paint();
    Paint selectedLinePaint = new Paint();
    Paint signaturePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    Paint signaturePaint2 = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    Paint bottomSignaturePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    Paint pickerSelectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint unactiveBottomChartPaint = new Paint();
    Paint selectionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint whiteLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Rect pickerRect = new Rect();
    Path pathTmp = new Path();

    Animator maxValueAnimator;

    ValueAnimator alphaAnimator;
    ValueAnimator alphaBottomAnimator;
    Animator pickerAnimator;
    ValueAnimator selectionAnimator;
    boolean postTransition = false;

    public ChartPickerDelegate pickerDelegate = new ChartPickerDelegate(this);
    T chartData;

    ChartBottomSignatureData currentBottomSignatures;
    protected float pickerMaxHeight;
    protected float pickerMinHeight;
    protected float animatedToPickerMaxHeight;
    protected float animatedToPickerMinHeight;
    protected int tmpN;
    protected int tmpI;
    protected int bottomSignatureOffset;

    private Bitmap bottomChartBitmap;
    private Canvas bottomChartCanvas;

    protected boolean chartCaptured = false;
    protected int selectedIndex = -1;
    protected float selectedCoordinate = -1;

    public LegendSignatureView legendSignatureView;
    public boolean legendShowing = false;

    public float selectionA = 0f;

    boolean superDraw = false;
    boolean useAlphaSignature = false;

    public int transitionMode = TRANSITION_MODE_NONE;
    public TransitionParams transitionParams;

    public final static int TRANSITION_MODE_CHILD = 1;
    public final static int TRANSITION_MODE_PARENT = 2;
    public final static int TRANSITION_MODE_ALPHA_ENTER = 3;
    public final static int TRANSITION_MODE_NONE = 0;

    private final int touchSlop;

    public int pikerHeight = Screen.dp(46);
    public int pickerWidth;
    public int chartStart;
    public int chartEnd;
    public int chartWidth;
    public float chartFullWidth;
    public Rect chartArea = new Rect();

    private ValueAnimator.AnimatorUpdateListener pickerHeightUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            pickerMaxHeight = (float) animation.getAnimatedValue();
            invalidatePickerChart = true;
            invalidate();
        }
    };

    private ValueAnimator.AnimatorUpdateListener pickerMinHeightUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            pickerMinHeight = (float) animation.getAnimatedValue();
            invalidatePickerChart = true;
            invalidate();
        }
    };

    private ValueAnimator.AnimatorUpdateListener heightUpdateListener = animation -> {
        currentMaxHeight = ((float) animation.getAnimatedValue());
        invalidate();
    };

    private ValueAnimator.AnimatorUpdateListener minHeightUpdateListener = animation -> {
        currentMinHeight = ((float) animation.getAnimatedValue());
        invalidate();
    };
    private ValueAnimator.AnimatorUpdateListener selectionAnimatorListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            selectionA = (float) animation.getAnimatedValue();
            legendSignatureView.setAlpha(selectionA);
            invalidate();
        }
    };
    private Animator.AnimatorListener selectorAnimatorEndListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            if (!animateLegentTo) {
                legendShowing = false;
                legendSignatureView.setVisibility(GONE);
                invalidate();
            }

            postTransition = false;

        }
    };
    protected boolean useMinHeight = false;
    protected DateSelectionListener dateSelectionListener;
    private float startFromMax;
    private float startFromMin;
    private float startFromMaxH;
    private float startFromMinH;
    private float minMaxUpdateStep;

    public BaseChartView(Context context) {
        super(context);
        init();
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public static int getHorizontalPadding () {
        return Screen.dp(16f);
    }

    public static int getSignatureTextHeight () {
        return Screen.dp(18f);
    }

    public static int getBottomSignatureStartAlpha () {
        return Screen.dp(10f);
    }

    public static int getPickerPadding () {
        return Screen.dp(16f);
    }

    protected void init() {
        linePaint.setStrokeWidth(LINE_WIDTH);
        selectedLinePaint.setStrokeWidth(SELECTED_LINE_WIDTH);

        signaturePaint.setTextSize(SIGNATURE_TEXT_SIZE);
        signaturePaint2.setTextSize(SIGNATURE_TEXT_SIZE);
        signaturePaint2.setTextAlign(Paint.Align.RIGHT);
        bottomSignaturePaint.setTextSize(SIGNATURE_TEXT_SIZE);
        bottomSignaturePaint.setTextAlign(Paint.Align.CENTER);

        selectionBackgroundPaint.setStrokeWidth(Screen.dpf(6f));
        selectionBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        setLayerType(LAYER_TYPE_HARDWARE, null);
        setWillNotDraw(false);

        legendSignatureView = createLegendView();


        legendSignatureView.setVisibility(GONE);

        whiteLinePaint.setColor(Color.WHITE);
        whiteLinePaint.setStrokeWidth(Screen.dpf(3));
        whiteLinePaint.setStrokeCap(Paint.Cap.ROUND);

        updateColors();
    }

    protected LegendSignatureView createLegendView() {
        return new LegendSignatureView(getContext());
    }

    @Override
    public void onThemeInvalidate (boolean isTempUpdate) {
        updateColors();
        invalidate();
    }

    public void updateColors() {
        if (useAlphaSignature) {
            signaturePaint.setColor(Theme.textDecentColor()); // TODO key_statisticChartSignatureAlpha
        } else {
            signaturePaint.setColor(Theme.textDecentColor()); // Theme.key_statisticChartSignature
        }

        if (sharedUiComponents != null) {
            sharedUiComponents.invalidate();
        }

        bottomSignaturePaint.setColor(Theme.textDecentColor()); // Theme.key_statisticChartSignature
        linePaint.setColor(Theme.separatorColor()); // Theme.key_statisticChartHintLine
        selectedLinePaint.setColor(Theme.separatorColor()); // TODO key_statisticChartActiveLine
        pickerSelectorPaint.setColor(Theme.getColor(R.id.theme_color_fillingPositive)); // TODO key_statisticChartActivePickerChart
        unactiveBottomChartPaint.setColor(me.vkryl.core.ColorUtils.alphaColor(.5f, ColorUtils.blendARGB(Theme.getColor(R.id.theme_color_fillingPositive), Theme.fillingColor(), .6f))); // TODO key_statisticChartInactivePickerChart
        selectionBackgroundPaint.setColor(Theme.fillingColor()); // Theme.key_windowBackgroundWhite
        ripplePaint.setColor(me.vkryl.core.ColorUtils.alphaColor(.2f, Theme.getColor(R.id.theme_color_fillingPositive))); // Theme.key_statisticChartRipple
        legendSignatureView.recolor();

        hintLinePaintAlpha = linePaint.getAlpha();
        chartActiveLineAlpha = selectedLinePaint.getAlpha();
        signaturePaintAlpha = signaturePaint.getAlpha() / 255f;
        bottomSignaturePaintAlpha = bottomSignaturePaint.getAlpha() / 255f;


        for (LineViewData l : lines) {
            l.updateColors();
        }

        if (legendShowing && selectedIndex < chartData.x.length) {
            legendSignatureView.setData(selectedIndex, chartData.x[selectedIndex], (ArrayList<LineViewData>) lines, false);
        }

        invalidatePickerChart = true;
    }

    int lastW = 0;
    int lastH = 0;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
          MeasureSpec.getSize(widthMeasureSpec),
          MeasureSpec.getSize(heightMeasureSpec)// FIXME? AndroidUtilities.displaySize.y - Screen.dp(56)
        );


        if (getMeasuredWidth() != lastW || getMeasuredHeight() != lastH) {
            lastW = getMeasuredWidth();
            lastH = getMeasuredHeight();
            bottomChartBitmap = Bitmap.createBitmap(getMeasuredWidth() - (getHorizontalPadding() << 1), pikerHeight, Bitmap.Config.ARGB_4444);
            bottomChartCanvas = new Canvas(bottomChartBitmap);

            sharedUiComponents.getPickerMaskBitmap(pikerHeight, getMeasuredWidth() - getHorizontalPadding() * 2);
            measureSizes();

            if (legendShowing)
                moveLegend(chartFullWidth * (pickerDelegate.pickerStart) - getHorizontalPadding());

            onPickerDataChanged(false, true, false);
        }
    }


    private void measureSizes() {
        if (getMeasuredHeight() <= 0 || getMeasuredWidth() <= 0) {
            return;
        }
        pickerWidth = getMeasuredWidth() - (getHorizontalPadding() * 2);
        chartStart = getHorizontalPadding();
        chartEnd = getMeasuredWidth() - getHorizontalPadding();
        chartWidth = chartEnd - chartStart;
        chartFullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));

        updateLineSignature();
        chartBottom = Screen.dp(100f);
        chartArea.set(chartStart - getHorizontalPadding(), 0, chartEnd + getHorizontalPadding(), getMeasuredHeight() - chartBottom);

        if (chartData != null) {
            bottomSignatureOffset = (int) (Screen.dp(20) / ((float) pickerWidth / chartData.x.length));
        }
        measureHeightThreshold();
    }

    private void measureHeightThreshold() {
        int chartHeight = getMeasuredHeight() - chartBottom;
        if (animateToMaxHeight == 0 || chartHeight == 0) return;
        thresholdMaxHeight = ((float) animateToMaxHeight / chartHeight) * SIGNATURE_TEXT_SIZE;
    }


    protected void drawPickerChart(Canvas canvas) {

    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (superDraw) {
            super.onDraw(canvas);
            return;
        }
        tick();
        int count = canvas.save();
        canvas.clipRect(0, chartArea.top, getMeasuredWidth(), chartArea.bottom);

        drawBottomLine(canvas);
        tmpN = horizontalLines.size();
        for (tmpI = 0; tmpI < tmpN; tmpI++) {
            drawHorizontalLines(canvas, horizontalLines.get(tmpI));
        }

        drawChart(canvas);

        for (tmpI = 0; tmpI < tmpN; tmpI++) {
            drawSignaturesToHorizontalLines(canvas, horizontalLines.get(tmpI));
        }

        canvas.restoreToCount(count);
        drawBottomSignature(canvas);

        drawPicker(canvas);
        drawSelection(canvas);

        super.onDraw(canvas);
    }

    protected void tick() {
        if (minMaxUpdateStep == 0) {
            return;
        }
        if (currentMaxHeight != animateToMaxHeight) {
            startFromMax += minMaxUpdateStep;
            if (startFromMax > 1) {
                startFromMax = 1;
                currentMaxHeight = animateToMaxHeight;
            } else {
                currentMaxHeight = startFromMaxH + (animateToMaxHeight - startFromMaxH) * CubicBezierInterpolator.EASE_OUT.getInterpolation(startFromMax);
            }
            invalidate();
        }
        if (useMinHeight) {
            if (currentMinHeight != animateToMinHeight) {
                startFromMin += minMaxUpdateStep;
                if (startFromMin > 1) {
                    startFromMin = 1;
                    currentMinHeight = animateToMinHeight;
                } else {
                    currentMinHeight = startFromMinH + (animateToMinHeight - startFromMinH) * CubicBezierInterpolator.EASE_OUT.getInterpolation(startFromMin);
                }
                invalidate();
            }
        }
    }


    void drawBottomSignature(Canvas canvas) {
        if (chartData == null) return;

        tmpN = bottomSignatureDate.size();

        float transitionAlpha = 1f;
        if (transitionMode == TRANSITION_MODE_PARENT) {
            transitionAlpha = 1f - transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_CHILD) {
            transitionAlpha = transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
            transitionAlpha = transitionParams.progress;
        }

        for (tmpI = 0; tmpI < tmpN; tmpI++) {
            int resultAlpha = bottomSignatureDate.get(tmpI).alpha;
            int step = bottomSignatureDate.get(tmpI).step;
            if (step == 0) step = 1;

            int start = startXIndex - bottomSignatureOffset;
            while (start % step != 0) {
                start--;
            }

            int end = endXIndex - bottomSignatureOffset;
            while (end % step != 0 || end < chartData.x.length - 1) {
                end++;
            }

            start += bottomSignatureOffset;
            end += bottomSignatureOffset;


            float offset = chartFullWidth * (pickerDelegate.pickerStart) - getHorizontalPadding();

            for (int i = start; i < end; i += step) {
                if (i < 0 || i >= chartData.x.length - 1) continue;
                float xPercentage = (float) (chartData.x[i] - chartData.x[0]) /
                        (float) ((chartData.x[chartData.x.length - 1] - chartData.x[0]));
                float xPoint = xPercentage * chartFullWidth - offset;
                float xPointOffset = xPoint - BOTTOM_SIGNATURE_OFFSET;
                if (xPointOffset > 0 &&
                        xPointOffset <= chartWidth + getHorizontalPadding()) {
                    if (xPointOffset < getBottomSignatureStartAlpha()) {
                        float a = 1f - (getBottomSignatureStartAlpha() - xPointOffset) / getBottomSignatureStartAlpha();
                        bottomSignaturePaint.setAlpha((int) (resultAlpha * a * bottomSignaturePaintAlpha * transitionAlpha));
                    } else if (xPointOffset > chartWidth) {
                        float a = 1f - (xPointOffset - chartWidth) / getHorizontalPadding();
                        bottomSignaturePaint.setAlpha((int) (resultAlpha * a * bottomSignaturePaintAlpha * transitionAlpha));
                    } else {
                        bottomSignaturePaint.setAlpha((int) (resultAlpha * bottomSignaturePaintAlpha * transitionAlpha));
                    }
                    canvas.drawText(chartData.getDayString(i), xPoint, getMeasuredHeight() - chartBottom + BOTTOM_SIGNATURE_TEXT_HEIGHT + Screen.dp(3), bottomSignaturePaint);
                }
            }
        }
    }

    protected void drawBottomLine(Canvas canvas) {
        if (chartData == null) {
            return;
        }
        float transitionAlpha = 1f;
        if (transitionMode == TRANSITION_MODE_PARENT) {
            transitionAlpha = 1f - transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_CHILD) {
            transitionAlpha = transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
            transitionAlpha = transitionParams.progress;
        }

        linePaint.setAlpha((int) (hintLinePaintAlpha * transitionAlpha));
        signaturePaint.setAlpha((int) (255 * signaturePaintAlpha * transitionAlpha));
        int textOffset = (int) (getSignatureTextHeight() - signaturePaint.getTextSize());
        int y = (getMeasuredHeight() - chartBottom - 1);
        canvas.drawLine(
                chartStart,
                y,
                chartEnd,
                y,
                linePaint);
        if (useMinHeight) return;

        canvas.drawText("0", getHorizontalPadding(), y - textOffset, signaturePaint);
    }

    protected void drawSelection(Canvas canvas) {
        if (selectedIndex < 0 || !legendShowing || chartData == null) return;

        int alpha = (int) (chartActiveLineAlpha * selectionA);


        float fullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));
        float offset = fullWidth * (pickerDelegate.pickerStart) - getHorizontalPadding();

        float xPoint;
        if (selectedIndex < chartData.xPercentage.length) {
            xPoint = chartData.xPercentage[selectedIndex] * fullWidth - offset;
        } else {
            return;
        }

        selectedLinePaint.setAlpha(alpha);
        canvas.drawLine(xPoint, 0, xPoint, chartArea.bottom, selectedLinePaint);

        if (drawPointOnSelection) {
            tmpN = lines.size();
            for (tmpI = 0; tmpI < tmpN; tmpI++) {
                LineViewData line = lines.get(tmpI);
                if (!line.enabled && line.alpha == 0) continue;
                float yPercentage = (line.line.y[selectedIndex] - currentMinHeight) / (currentMaxHeight - currentMinHeight);
                float yPoint = getMeasuredHeight() - chartBottom - (yPercentage) * (getMeasuredHeight() - chartBottom - getSignatureTextHeight());

                line.selectionPaint.setAlpha((int) (255 * line.alpha * selectionA));
                selectionBackgroundPaint.setAlpha((int) (255 * line.alpha * selectionA));

                canvas.drawPoint(xPoint, yPoint, line.selectionPaint);
                canvas.drawPoint(xPoint, yPoint, selectionBackgroundPaint);
            }
        }
    }

    protected void drawChart(Canvas canvas) {
    }

    protected void drawHorizontalLines(Canvas canvas, ChartHorizontalLinesData a) {
        int n = a.values.length;

        float additionalOutAlpha = 1f;
        if (n > 2) {
            float v = (a.values[1] - a.values[0]) / (float) (currentMaxHeight - currentMinHeight);
            if (v < 0.1) {
                additionalOutAlpha = v / 0.1f;
            }
        }

        float transitionAlpha = 1f;
        if (transitionMode == TRANSITION_MODE_PARENT) {
            transitionAlpha = 1f - transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_CHILD) {
            transitionAlpha = transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
            transitionAlpha = transitionParams.progress;
        }
        linePaint.setAlpha((int) (a.alpha * (hintLinePaintAlpha / 255f) * transitionAlpha * additionalOutAlpha));
        signaturePaint.setAlpha((int) (a.alpha * signaturePaintAlpha * transitionAlpha * additionalOutAlpha));
        int chartHeight = getMeasuredHeight() - chartBottom - getSignatureTextHeight();
        for (int i = useMinHeight ? 0 : 1; i < n; i++) {
            int y = (int) ((getMeasuredHeight() - chartBottom) - chartHeight * ((a.values[i] - currentMinHeight) / (currentMaxHeight - currentMinHeight)));
            canvas.drawRect(chartStart, y, chartEnd, y + 1, linePaint);
        }
    }

    protected void drawSignaturesToHorizontalLines(Canvas canvas, ChartHorizontalLinesData a) {
        int n = a.values.length;

        float additionalOutAlpha = 1f;
        if (n > 2) {
            float v = (a.values[1] - a.values[0]) / (float) (currentMaxHeight - currentMinHeight);
            if (v < 0.1) {
                additionalOutAlpha = v / 0.1f;
            }
        }

        float transitionAlpha = 1f;
        if (transitionMode == TRANSITION_MODE_PARENT) {
            transitionAlpha = 1f - transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_CHILD) {
            transitionAlpha = transitionParams.progress;
        } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
            transitionAlpha = transitionParams.progress;
        }
        linePaint.setAlpha((int) (a.alpha * (hintLinePaintAlpha / 255f) * transitionAlpha * additionalOutAlpha));
        signaturePaint.setAlpha((int) (a.alpha * signaturePaintAlpha * transitionAlpha * additionalOutAlpha));
        int chartHeight = getMeasuredHeight() - chartBottom - getSignatureTextHeight();

        int textOffset = (int) (getSignatureTextHeight() - signaturePaint.getTextSize());
        for (int i = useMinHeight ? 0 : 1; i < n; i++) {
            int y = (int) ((getMeasuredHeight() - chartBottom) - chartHeight * ((a.values[i] - currentMinHeight) / (currentMaxHeight - currentMinHeight)));
            canvas.drawText(a.valuesStr[i], getHorizontalPadding(), y - textOffset, signaturePaint);
        }
    }

    void drawPicker(Canvas canvas) {
        if (chartData == null) {
            return;
        }
        pickerDelegate.pickerWidth = pickerWidth;
        int bottom = getMeasuredHeight() - getPickerPadding();
        int top = getMeasuredHeight() - pikerHeight - getPickerPadding();

        int start = (int) (getHorizontalPadding() + pickerWidth * pickerDelegate.pickerStart);
        int end = (int) (getHorizontalPadding() + pickerWidth * pickerDelegate.pickerEnd);

        float transitionAlpha = 1f;
        if (transitionMode == TRANSITION_MODE_CHILD) {
            int startParent = (int) (getHorizontalPadding() + pickerWidth * transitionParams.pickerStartOut);
            int endParent = (int) (getHorizontalPadding() + pickerWidth * transitionParams.pickerEndOut);

            start += (startParent - start) * (1f - transitionParams.progress);
            end += (endParent - end) * (1f - transitionParams.progress);
        } else if (transitionMode == TRANSITION_MODE_ALPHA_ENTER) {
            transitionAlpha = transitionParams.progress;
        }

        if (chartData != null) {
            boolean instantDraw = false;
            if (transitionMode == TRANSITION_MODE_NONE) {
                for (int i = 0; i < lines.size(); i++) {
                    L l = lines.get(i);
                    if ((l.animatorIn != null && l.animatorIn.isRunning()) || (l.animatorOut != null && l.animatorOut.isRunning())) {
                        instantDraw = true;
                        break;
                    }
                }
            }
            if (instantDraw) {
                canvas.save();
                canvas.clipRect(
                  getHorizontalPadding(), getMeasuredHeight() - getPickerPadding() - pikerHeight,
                        getMeasuredWidth() - getHorizontalPadding(), getMeasuredHeight() - getPickerPadding()
                );
                canvas.translate(getHorizontalPadding(), getMeasuredHeight() - getPickerPadding() - pikerHeight);
                drawPickerChart(canvas);
                canvas.restore();
            } else if (invalidatePickerChart) {
                bottomChartBitmap.eraseColor(0);
                drawPickerChart(bottomChartCanvas);
                invalidatePickerChart = false;
            }
            if (!instantDraw) {
                if (transitionMode == TRANSITION_MODE_PARENT) {

                    float pY = top + (bottom - top) >> 1;
                    float pX = getHorizontalPadding() + pickerWidth * transitionParams.xPercentage;

                    emptyPaint.setAlpha((int) ((1f - transitionParams.progress) * 255));

                    canvas.save();
                    canvas.clipRect(getHorizontalPadding(), top, getMeasuredWidth() - getHorizontalPadding(), bottom);
                    canvas.scale(1 + 2 * transitionParams.progress, 1f, pX, pY);
                    canvas.drawBitmap(bottomChartBitmap, getHorizontalPadding(), getMeasuredHeight() - getPickerPadding() - pikerHeight, emptyPaint);
                    canvas.restore();


                } else if (transitionMode == TRANSITION_MODE_CHILD) {
                    float pY = top + (bottom - top) >> 1;
                    float pX = getHorizontalPadding() + pickerWidth * transitionParams.xPercentage;

                    float dX = (transitionParams.xPercentage > 0.5f ? pickerWidth * transitionParams.xPercentage : pickerWidth * (1f - transitionParams.xPercentage)) * transitionParams.progress;

                    canvas.save();
                    canvas.clipRect(pX - dX, top, pX + dX, bottom);

                    emptyPaint.setAlpha((int) (transitionParams.progress * 255));
                    canvas.scale(transitionParams.progress, 1f, pX, pY);
                    canvas.drawBitmap(bottomChartBitmap, getHorizontalPadding(), getMeasuredHeight() - getPickerPadding() - pikerHeight, emptyPaint);
                    canvas.restore();

                } else {
                    emptyPaint.setAlpha((int) ((transitionAlpha) * 255));
                    canvas.drawBitmap(bottomChartBitmap, getHorizontalPadding(), getMeasuredHeight() - getPickerPadding() - pikerHeight, emptyPaint);
                }
            }


            if (transitionMode == TRANSITION_MODE_PARENT) {
                return;
            }

            canvas.drawRect(getHorizontalPadding(),
                    top,
                    start + DP_12,
                    bottom, unactiveBottomChartPaint);

            canvas.drawRect(end - DP_12,
                    top,
                    getMeasuredWidth() - getHorizontalPadding(),
                    bottom, unactiveBottomChartPaint);
        } else {
            canvas.drawRect(getHorizontalPadding(),
                    top,
                    getMeasuredWidth() - getHorizontalPadding(),
                    bottom, unactiveBottomChartPaint);
        }

        canvas.drawBitmap(
                sharedUiComponents.getPickerMaskBitmap(pikerHeight, getMeasuredWidth() - getHorizontalPadding() * 2),
          getHorizontalPadding(), getMeasuredHeight() - getPickerPadding() - pikerHeight, emptyPaint);

        if (chartData != null) {
            pickerRect.set(start,
                    top,
                    end,
                    bottom);


            pickerDelegate.middlePickerArea.set(pickerRect);


            canvas.drawPath(RoundedRect(pathTmp, pickerRect.left,
                    pickerRect.top - DP_1,
                    pickerRect.left + DP_12,
                    pickerRect.bottom + DP_1, DP_6, DP_6,
                    true, false, false, true), pickerSelectorPaint);


            canvas.drawPath(RoundedRect(pathTmp, pickerRect.right - DP_12,
                    pickerRect.top - DP_1, pickerRect.right,
                    pickerRect.bottom + DP_1, DP_6, DP_6,
                    false, true, true, false), pickerSelectorPaint);

            canvas.drawRect(pickerRect.left + DP_12,
                    pickerRect.bottom, pickerRect.right - DP_12,
                    pickerRect.bottom + DP_1, pickerSelectorPaint);

            canvas.drawRect(pickerRect.left + DP_12,
                    pickerRect.top - DP_1, pickerRect.right - DP_12,
                    pickerRect.top, pickerSelectorPaint);


            canvas.drawLine(pickerRect.left + DP_6, pickerRect.centerY() - DP_6,
                    pickerRect.left + DP_6, pickerRect.centerY() + DP_6, whiteLinePaint);

            canvas.drawLine(pickerRect.right - DP_6, pickerRect.centerY() - DP_6,
                    pickerRect.right - DP_6, pickerRect.centerY() + DP_6, whiteLinePaint);


            ChartPickerDelegate.CapturesData middleCap = pickerDelegate.getMiddleCaptured();

            int r = ((pickerRect.bottom - pickerRect.top) >> 1);
            int cY = pickerRect.top + r;

            if (middleCap != null) {
               // canvas.drawCircle(pickerRect.left + ((pickerRect.right - pickerRect.left) >> 1), cY, r * middleCap.aValue + HORIZONTAL_PADDING, ripplePaint);
            } else {
                ChartPickerDelegate.CapturesData lCap = pickerDelegate.getLeftCaptured();
                ChartPickerDelegate.CapturesData rCap = pickerDelegate.getRightCaptured();

                if (lCap != null)
                    canvas.drawCircle(pickerRect.left + DP_5, cY, r * lCap.aValue - DP_2, ripplePaint);
                if (rCap != null)
                    canvas.drawCircle(pickerRect.right - DP_5, cY, r * rCap.aValue - DP_2, ripplePaint);
            }

            int cX = start;
            pickerDelegate.leftPickerArea.set(
                    cX - PICKER_CAPTURE_WIDTH,
                    top,
                    cX + (PICKER_CAPTURE_WIDTH >> 1),
                    bottom
            );

            cX = end;
            pickerDelegate.rightPickerArea.set(
                    cX - (PICKER_CAPTURE_WIDTH >> 1),
                    top,
                    cX + PICKER_CAPTURE_WIDTH,
                    bottom
            );
        }
    }


    long lastTime = 0;

    private void setMaxMinValue(int newMaxHeight, int newMinHeight, boolean animated) {
        setMaxMinValue(newMaxHeight, newMinHeight, animated, false, false);
    }

    protected void setMaxMinValue(int newMaxHeight, int newMinHeight, boolean animated, boolean force, boolean useAnimator) {
        boolean heightChanged = true;
        if ((Math.abs(ChartHorizontalLinesData.lookupHeight(newMaxHeight) - animateToMaxHeight) < thresholdMaxHeight) || newMaxHeight == 0) {
            heightChanged = false;
        }

        if (!heightChanged && newMaxHeight == animateToMinHeight) return;
        final ChartHorizontalLinesData newData = createHorizontalLinesData(newMaxHeight, newMinHeight);
        newMaxHeight = newData.values[newData.values.length - 1];
        newMinHeight = newData.values[0];


        if (!useAnimator) {
            float k = (currentMaxHeight - currentMinHeight) / (newMaxHeight - newMinHeight);
            if (k > 1f) {
                k = (newMaxHeight - newMinHeight) / (currentMaxHeight - currentMinHeight);
            }
            float s = 0.045f;
            if (k > 0.7) {
                s = 0.1f;
            } else if (k < 0.1) {
                s = 0.03f;
            }

            boolean update = false;
            if (newMaxHeight != animateToMaxHeight) {
                update = true;
            }
            if (useMinHeight && newMinHeight != animateToMinHeight) {
                update = true;
            }
            if (update) {
                if (maxValueAnimator != null) {
                    maxValueAnimator.removeAllListeners();
                    maxValueAnimator.cancel();
                }
                startFromMaxH = currentMaxHeight;
                startFromMinH = currentMinHeight;
                startFromMax = 0;
                startFromMin = 0;
                minMaxUpdateStep = s;
            }
        }

        animateToMaxHeight = newMaxHeight;
        animateToMinHeight = newMinHeight;
        measureHeightThreshold();

        long t = System.currentTimeMillis();
        //  debounce
        if (t - lastTime < 320 && !force) {
            return;
        }
        lastTime = t;

        if (alphaAnimator != null) {
            alphaAnimator.removeAllListeners();
            alphaAnimator.cancel();
        }

        if (!animated) {
            currentMaxHeight = newMaxHeight;
            currentMinHeight = newMinHeight;
            horizontalLines.clear();
            horizontalLines.add(newData);
            newData.alpha = 255;
            return;
        }


        horizontalLines.add(newData);

        if (useAnimator) {
            if (maxValueAnimator != null) {
                maxValueAnimator.removeAllListeners();
                maxValueAnimator.cancel();
            }
            minMaxUpdateStep = 0;

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(createAnimator(currentMaxHeight, newMaxHeight, heightUpdateListener));

            if (useMinHeight) {
                animatorSet.playTogether(createAnimator(currentMinHeight, newMinHeight, minHeightUpdateListener));
            }

            maxValueAnimator = animatorSet;
            maxValueAnimator.start();
        }

        int n = horizontalLines.size();
        for (int i = 0; i < n; i++) {
            ChartHorizontalLinesData a = horizontalLines.get(i);
            if (a != newData) a.fixedAlpha = a.alpha;
        }

        alphaAnimator = createAnimator(0, 255, animation -> {
            newData.alpha = (int) ((float) animation.getAnimatedValue());
            for (ChartHorizontalLinesData a : horizontalLines) {
                if (a != newData)
                    a.alpha = (int) ((a.fixedAlpha / 255f) * (255 - newData.alpha));
            }
            invalidate();
        });
        alphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                horizontalLines.clear();
                horizontalLines.add(newData);
            }
        });

        alphaAnimator.start();
    }

    protected ChartHorizontalLinesData createHorizontalLinesData(int newMaxHeight, int newMinHeight) {
        return new ChartHorizontalLinesData(newMaxHeight, newMinHeight, useMinHeight);
    }

    ValueAnimator createAnimator(float f1, float f2, ValueAnimator.AnimatorUpdateListener l) {
        ValueAnimator a = ValueAnimator.ofFloat(f1, f2);
        a.setDuration(ANIM_DURATION);
        a.setInterpolator(INTERPOLATOR);
        a.addUpdateListener(l);
        return a;
    }

    int lastX;
    int lastY;
    int capturedX;
    int capturedY;
    long capturedTime;
    protected boolean canCaptureChartSelection;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (chartData == null) {
            return false;
        }
        if (!enabled) {
            pickerDelegate.uncapture(event, event.getActionIndex());
            getParent().requestDisallowInterceptTouchEvent(false);
            chartCaptured = false;
            return false;
        }


        int x = (int) event.getX(event.getActionIndex());
        int y = (int) event.getY(event.getActionIndex());

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                capturedTime = System.currentTimeMillis();
                getParent().requestDisallowInterceptTouchEvent(true);
                boolean captured = pickerDelegate.capture(x, y, event.getActionIndex());
                if (captured) {
                    return true;
                }

                capturedX = lastX = x;
                capturedY = lastY = y;

                if (chartArea.contains(x, y)) {
                    if (selectedIndex < 0 || !animateLegentTo) {
                        chartCaptured = true;
                        selectXOnChart(x, y);
                    }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                return pickerDelegate.capture(x, y, event.getActionIndex());
            case MotionEvent.ACTION_MOVE:
                int dx = x - lastX;
                int dy = y - lastY;

                if (pickerDelegate.captured()) {
                    boolean rez = pickerDelegate.move(x, y, event.getActionIndex());
                    if (event.getPointerCount() > 1) {
                        x = (int) event.getX(1);
                        y = (int) event.getY(1);
                        pickerDelegate.move(x, y, 1);
                    }

                    getParent().requestDisallowInterceptTouchEvent(rez);

                    return true;
                }

                if (chartCaptured) {
                    boolean disable;
                    if (canCaptureChartSelection && System.currentTimeMillis() - capturedTime > 200) {
                        disable = true;
                    } else {
                        disable = Math.abs(dx) > Math.abs(dy) || Math.abs(dy) < touchSlop;
                    }
                    lastX = x;
                    lastY = y;

                    getParent().requestDisallowInterceptTouchEvent(disable);
                    selectXOnChart(x, y);
                } else if (chartArea.contains(capturedX, capturedY)){
                    int dxCaptured = capturedX - x;
                    int dyCaptured = capturedY - y;
                    if (Math.sqrt(dxCaptured * dxCaptured + dyCaptured * dyCaptured) > touchSlop || System.currentTimeMillis() - capturedTime > 200) {
                        chartCaptured = true;
                        selectXOnChart(x, y);
                    }
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                pickerDelegate.uncapture(event, event.getActionIndex());
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (pickerDelegate.uncapture(event, event.getActionIndex())) {
                    return true;
                }
                if (chartArea.contains(capturedX, capturedY) && !chartCaptured) {
                    animateLegend(false);
                }
                pickerDelegate.uncapture();
                updateLineSignature();
                getParent().requestDisallowInterceptTouchEvent(false);
                chartCaptured = false;
                onActionUp();
                invalidate();
                int min = 0;
                if (useMinHeight) min = findMinValue(startXIndex, endXIndex);
                setMaxMinValue(findMaxValue(startXIndex, endXIndex), min, true, true, false);
                return true;


        }

        return false;
    }

    protected void onActionUp() {

    }

    protected void selectXOnChart(int x, int y) {
        int oldSelectedX = selectedIndex;
        if (chartData == null) return;
        float offset = chartFullWidth * (pickerDelegate.pickerStart) - getHorizontalPadding();
        float xP = (offset + x) / chartFullWidth;
        selectedCoordinate = xP;
        if (xP < 0) {
            selectedIndex = 0;
            selectedCoordinate = 0f;
        } else if (xP > 1) {
            selectedIndex = chartData.x.length - 1;
            selectedCoordinate = 1f;
        } else {
            selectedIndex = chartData.findIndex(startXIndex, endXIndex, xP);
            if (selectedIndex + 1 < chartData.xPercentage.length) {
                float dx = Math.abs(chartData.xPercentage[selectedIndex] - xP);
                float dx2 = Math.abs(chartData.xPercentage[selectedIndex + 1] - xP);
                if (dx2 < dx) {
                    selectedIndex++;
                }
            }
        }

        if (selectedIndex > endXIndex) selectedIndex = endXIndex;
        if (selectedIndex < startXIndex) selectedIndex = startXIndex;

        legendShowing = true;
        animateLegend(true);
        moveLegend(offset);
        if (dateSelectionListener != null) {
            dateSelectionListener.onDateSelected(getSelectedDate());
        }
        invalidate();
    }

    public boolean animateLegentTo = false;

    public void animateLegend(boolean show) {
        moveLegend();
        if (animateLegentTo == show) return;
        animateLegentTo = show;
        if (selectionAnimator != null) {
            selectionAnimator.removeAllListeners();
            selectionAnimator.cancel();
        }
        selectionAnimator = createAnimator(selectionA, show ? 1f : 0f, selectionAnimatorListener)
                .setDuration(200);

        selectionAnimator.addListener(selectorAnimatorEndListener);


        selectionAnimator.start();
    }

    public void moveLegend(float offset) {
        if (chartData == null || selectedIndex == -1 || !legendShowing) return;
        legendSignatureView.setData(selectedIndex, chartData.x[selectedIndex], (ArrayList<LineViewData>) lines, false);
        legendSignatureView.setVisibility(VISIBLE);
        legendSignatureView.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST)
        );
        float lXPoint = chartData.xPercentage[selectedIndex] * chartFullWidth - offset;
        if (lXPoint > (chartStart + chartWidth) >> 1) {
            lXPoint -= (legendSignatureView.getWidth() + DP_5);
        } else {
            lXPoint += DP_5;
        }
        if (lXPoint < 0) {
            lXPoint = 0;
        } else if (lXPoint + legendSignatureView.getMeasuredWidth() > getMeasuredWidth()) {
            lXPoint = getMeasuredWidth() - legendSignatureView.getMeasuredWidth();
        }
        legendSignatureView.setTranslationX(
                lXPoint
        );
    }

    public int findMaxValue(int startXIndex, int endXIndex) {
        int linesSize = lines.size();
        int maxValue = 0;
        for (int j = 0; j < linesSize; j++) {
            if (!lines.get(j).enabled) continue;
            int lineMax = lines.get(j).line.segmentTree.rMaxQ(startXIndex, endXIndex);
            if (lineMax > maxValue)
                maxValue = lineMax;
        }
        return maxValue;
    }


    public int findMinValue(int startXIndex, int endXIndex) {
        int linesSize = lines.size();
        int minValue = Integer.MAX_VALUE;
        for (int j = 0; j < linesSize; j++) {
            if (!lines.get(j).enabled) continue;
            int lineMin = lines.get(j).line.segmentTree.rMinQ(startXIndex, endXIndex);
            if (lineMin < minValue)
                minValue = lineMin;
        }
        return minValue;
    }

    public void setData(T chartData) {
        if (this.chartData != chartData) {
            invalidate();
            lines.clear();
            if (chartData != null && chartData.lines != null) {
                for (int i = 0; i < chartData.lines.size(); i++) {
                    lines.add(createLineViewData(chartData.lines.get(i)));
                }
            }
            clearSelection();
            this.chartData = chartData;
            if (chartData != null) {
                if (chartData.x[0] == 0) {
                    pickerDelegate.pickerStart = 0f;
                    pickerDelegate.pickerEnd = 1f;
                } else {
                    pickerDelegate.minDistance = getMinDistance();
                    if (pickerDelegate.pickerEnd - pickerDelegate.pickerStart < pickerDelegate.minDistance) {
                        pickerDelegate.pickerStart = pickerDelegate.pickerEnd - pickerDelegate.minDistance;
                        if (pickerDelegate.pickerStart < 0) {
                            pickerDelegate.pickerStart = 0f;
                            pickerDelegate.pickerEnd = 1f;
                        }
                    }
                }
            }
        }
        measureSizes();

        if (chartData != null) {
            updateIndexes();
            int min = useMinHeight ? findMinValue(startXIndex, endXIndex) : 0;
            setMaxMinValue(findMaxValue(startXIndex, endXIndex), min, false);
            pickerMaxHeight = 0;
            pickerMinHeight = Integer.MAX_VALUE;
            initPickerMaxHeight();
            legendSignatureView.setSize(lines.size());

            invalidatePickerChart = true;
            updateLineSignature();
        } else {

            pickerDelegate.pickerStart = 0.7f;
            pickerDelegate.pickerEnd = 1f;

            pickerMaxHeight = pickerMinHeight = 0;
            horizontalLines.clear();

            if (maxValueAnimator != null) {
                maxValueAnimator.cancel();
            }

            if (alphaAnimator != null) {
                alphaAnimator.removeAllListeners();
                alphaAnimator.cancel();
            }
        }
    }

    protected float getMinDistance() {
        if (chartData == null) {
            return 0.1f;
        }

        int n = chartData.x.length;
        if (n < 5) {
            return 1f;
        }
        float r = 5f / n;
        if (r < 0.1f) {
            return 0.1f;
        }
        return r;
    }

    protected void initPickerMaxHeight() {
        for (LineViewData l : lines) {
            if (l.enabled && l.line.maxValue > pickerMaxHeight) pickerMaxHeight = l.line.maxValue;
            if (l.enabled && l.line.minValue < pickerMinHeight) pickerMinHeight = l.line.minValue;
            if (pickerMaxHeight == pickerMinHeight) {
                pickerMaxHeight++;
                pickerMinHeight--;
            }
        }
    }

    public abstract L createLineViewData(ChartData.Line line);

    public void onPickerDataChanged() {
        onPickerDataChanged(true, false, false);
    }

    public void onPickerDataChanged(boolean animated, boolean force, boolean useAniamtor) {
        if (chartData == null) return;
        chartFullWidth = (chartWidth / (pickerDelegate.pickerEnd - pickerDelegate.pickerStart));

        updateIndexes();
        int min = useMinHeight ? findMinValue(startXIndex, endXIndex) : 0;
        setMaxMinValue(findMaxValue(startXIndex, endXIndex), min, animated, force, useAniamtor);

        if (legendShowing && !force) {
            animateLegend(false);
            moveLegend(chartFullWidth * (pickerDelegate.pickerStart) - getHorizontalPadding());
        }
        invalidate();
    }

    public void onPickerJumpTo(float start, float end, boolean force) {
        if (chartData == null) return;
        if (force) {
            int startXIndex = chartData.findStartIndex(Math.max(
                    start, 0f
            ));
            int endXIndex = chartData.findEndIndex(startXIndex, Math.min(
                    end, 1f
            ));
            setMaxMinValue(findMaxValue(startXIndex, endXIndex), findMinValue(startXIndex, endXIndex), true, true, false);
            animateLegend(false);
        } else {
            updateIndexes();
            invalidate();
        }
    }

    protected void updateIndexes() {
        if (chartData == null) return;
        startXIndex = chartData.findStartIndex(Math.max(
                pickerDelegate.pickerStart, 0f
        ));
        endXIndex = chartData.findEndIndex(startXIndex, Math.min(
                pickerDelegate.pickerEnd, 1f
        ));
        if (chartListener != null) {
            chartListener.onDateChanged(this, chartData.x[startXIndex], chartData.x[endXIndex]);
        }
        updateLineSignature();
    }

    private final static int BOTTOM_SIGNATURE_COUNT = 6;

    private void updateLineSignature() {
        if (chartData == null || chartWidth == 0) return;
        float d = chartFullWidth * chartData.oneDayPercentage;

        float k = chartWidth / d;
        int step = (int) (k / BOTTOM_SIGNATURE_COUNT);
        updateDates(step);
    }


    private void updateDates(int step) {
        if (currentBottomSignatures == null || step >= currentBottomSignatures.stepMax || step <= currentBottomSignatures.stepMin) {
            step = Integer.highestOneBit(step) << 1;
            if (currentBottomSignatures != null && currentBottomSignatures.step == step) {
                return;
            }

            if (alphaBottomAnimator != null) {
                alphaBottomAnimator.removeAllListeners();
                alphaBottomAnimator.cancel();
            }

            int stepMax = (int) (step + step * 0.2);
            int stepMin = (int) (step - step * 0.2);


            final ChartBottomSignatureData data = new ChartBottomSignatureData(step, stepMax, stepMin);
            data.alpha = 255;

            if (currentBottomSignatures == null) {
                currentBottomSignatures = data;
                data.alpha = 255;
                bottomSignatureDate.add(data);
                return;
            }

            currentBottomSignatures = data;


            tmpN = bottomSignatureDate.size();
            for (int i = 0; i < tmpN; i++) {
                ChartBottomSignatureData a = bottomSignatureDate.get(i);
                a.fixedAlpha = a.alpha;
            }

            bottomSignatureDate.add(data);
            if (bottomSignatureDate.size() > 2) {
                bottomSignatureDate.remove(0);
            }

            alphaBottomAnimator = createAnimator(0f, 1f, animation -> {
                float alpha = (float) animation.getAnimatedValue();
                for (ChartBottomSignatureData a : bottomSignatureDate) {
                    if (a == data) {
                        data.alpha = (int) (255 * alpha);
                    } else {
                        a.alpha = (int) ((1f - alpha) * (a.fixedAlpha));
                    }
                }
                invalidate();
            }).setDuration(200);
            alphaBottomAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    bottomSignatureDate.clear();
                    bottomSignatureDate.add(data);
                }
            });

            alphaBottomAnimator.start();
        }
    }

    public void onCheckChanged() {
        onPickerDataChanged(true, true, true);
        tmpN = lines.size();
        for (tmpI = 0; tmpI < tmpN; tmpI++) {
            final LineViewData lineViewData = lines.get(tmpI);

            if (lineViewData.enabled && lineViewData.animatorOut != null) {
                lineViewData.animatorOut.cancel();
            }

            if (!lineViewData.enabled && lineViewData.animatorIn != null) {
                lineViewData.animatorIn.cancel();
            }

            if (lineViewData.enabled && lineViewData.alpha != 1f) {
                if (lineViewData.animatorIn != null && lineViewData.animatorIn.isRunning()) {
                    continue;
                }
                lineViewData.animatorIn = createAnimator(lineViewData.alpha, 1f, animation -> {
                    lineViewData.alpha = ((float) animation.getAnimatedValue());
                    invalidatePickerChart = true;
                    invalidate();
                });
                lineViewData.animatorIn.start();
            }

            if (!lineViewData.enabled && lineViewData.alpha != 0) {
                if (lineViewData.animatorOut != null && lineViewData.animatorOut.isRunning()) {
                    continue;
                }
                lineViewData.animatorOut = createAnimator(lineViewData.alpha, 0f, animation -> {
                    lineViewData.alpha = ((float) animation.getAnimatedValue());
                    invalidatePickerChart = true;
                    invalidate();
                });
                lineViewData.animatorOut.start();
            }
        }

        updatePickerMinMaxHeight();
        if (legendShowing)
            legendSignatureView.setData(selectedIndex, chartData.x[selectedIndex], (ArrayList<LineViewData>) lines, true);
    }

    protected void updatePickerMinMaxHeight() {
        if (!ANIMATE_PICKER_SIZES) return;
        int max = 0;
        int min = Integer.MAX_VALUE;
        for (LineViewData l : lines) {
            if (l.enabled && l.line.maxValue > max) max = l.line.maxValue;
            if (l.enabled && l.line.minValue < min) min = l.line.minValue;
        }

        if ((min != Integer.MAX_VALUE && min != animatedToPickerMinHeight) || (max > 0 && max != animatedToPickerMaxHeight)) {
            animatedToPickerMaxHeight = max;
            if (pickerAnimator != null) pickerAnimator.cancel();
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    createAnimator(pickerMaxHeight, animatedToPickerMaxHeight, pickerHeightUpdateListener),
                    createAnimator(pickerMinHeight, animatedToPickerMinHeight, pickerMinHeightUpdateListener)
            );
            pickerAnimator = animatorSet;
            pickerAnimator.start();
        }
    }

    public void saveState(Bundle outState) {
        if (outState == null) return;

        outState.putFloat("chart_start", pickerDelegate.pickerStart);
        outState.putFloat("chart_end", pickerDelegate.pickerEnd);


        if (lines != null) {
            int n = lines.size();
            boolean[] bArray = new boolean[n];
            for (int i = 0; i < n; i++) {
                bArray[i] = lines.get(i).enabled;
            }
            outState.putBooleanArray("chart_line_enabled", bArray);

        }
    }

    public interface DateChangeListener {
        void onDateChanged (BaseChartView<?,?> chartView, long startTimeMs, long endTimeMs);
    }

    @Nullable
    private DateChangeListener chartListener;

    public void setListener(DateChangeListener listener) {
        this.chartListener = listener;
    }

    public long getSelectedDate() {
        if (selectedIndex < 0) {
            return -1;
        }
        return chartData.x[selectedIndex];
    }

    public void clearSelection() {
        selectedIndex = -1;
        legendShowing = false;
        animateLegentTo = false;
        legendSignatureView.setVisibility(GONE);
        selectionA = 0f;
    }

    public void selectDate(long activeZoom) {
        selectedIndex = Arrays.binarySearch(chartData.x, activeZoom);
        legendShowing = true;
        legendSignatureView.setVisibility(VISIBLE);
        selectionA = 1f;
        moveLegend(chartFullWidth * (pickerDelegate.pickerStart) - getHorizontalPadding());
    }

    public long getStartDate() {
        return chartData.x[startXIndex];
    }

    public long getEndDate() {
        return chartData.x[endXIndex];
    }

    public void updatePicker(ChartData chartData, long d) {
        int n = chartData.x.length;
        long startOfDay = d - d % 86400000L;
        long endOfDay = startOfDay + 86400000L - 1;
        int startIndex = 0;
        int endIndex = 0;

        for (int i = 0; i < n; i++) {
            if (startOfDay > chartData.x[i]) startIndex = i;
            if (endOfDay > chartData.x[i]) endIndex = i;
        }
        pickerDelegate.pickerStart = chartData.xPercentage[startIndex];
        pickerDelegate.pickerEnd = chartData.xPercentage[endIndex];
    }

    public void moveLegend() {
        moveLegend(chartFullWidth * (pickerDelegate.pickerStart) - getHorizontalPadding());
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    public static Path RoundedRect(
            Path path,
            float left, float top, float right, float bottom, float rx, float ry,
            boolean tl, boolean tr, boolean br, boolean bl
    ) {
        path.reset();
        if (rx < 0) rx = 0;
        if (ry < 0) ry = 0;
        float width = right - left;
        float height = bottom - top;
        if (rx > width / 2) rx = width / 2;
        if (ry > height / 2) ry = height / 2;
        float widthMinusCorners = (width - (2 * rx));
        float heightMinusCorners = (height - (2 * ry));

        path.moveTo(right, top + ry);
        if (tr)
            path.rQuadTo(0, -ry, -rx, -ry);
        else {
            path.rLineTo(0, -ry);
            path.rLineTo(-rx, 0);
        }
        path.rLineTo(-widthMinusCorners, 0);
        if (tl)
            path.rQuadTo(-rx, 0, -rx, ry);
        else {
            path.rLineTo(-rx, 0);
            path.rLineTo(0, ry);
        }
        path.rLineTo(0, heightMinusCorners);

        if (bl)
            path.rQuadTo(0, ry, rx, ry);
        else {
            path.rLineTo(0, ry);
            path.rLineTo(rx, 0);
        }

        path.rLineTo(widthMinusCorners, 0);
        if (br)
            path.rQuadTo(rx, 0, rx, -ry);
        else {
            path.rLineTo(rx, 0);
            path.rLineTo(0, -ry);
        }

        path.rLineTo(0, -heightMinusCorners);

        path.close();
        return path;
    }

    public void setDateSelectionListener(DateSelectionListener dateSelectionListener) {
        this.dateSelectionListener = dateSelectionListener;
    }

    public interface DateSelectionListener {
        void onDateSelected (long date);
    }

    public static class SharedUiComponents {

        private Bitmap pickerRoundBitmap;
        private Canvas canvas;


        private RectF rectF = new RectF();
        private Paint xRefP = new Paint(Paint.ANTI_ALIAS_FLAG);

        public SharedUiComponents() {
            xRefP.setColor(0);
            xRefP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        int k = 0;
        private boolean invalidate = true;

        Bitmap getPickerMaskBitmap(int h, int w) {
            if (h + w << 10 != k || invalidate) {
                invalidate = false;
                k = h + w << 10;
                if (!U.isValidBitmap(pickerRoundBitmap) || pickerRoundBitmap.getWidth() != w || pickerRoundBitmap.getHeight() != h) {
                    pickerRoundBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    canvas = new Canvas(pickerRoundBitmap);
                    rectF.set(0, 0, w, h);
                }

                canvas.drawColor(Theme.fillingColor()); // Theme.key_windowBackgroundWhite
                canvas.drawRoundRect(rectF, Screen.dp(4), Screen.dp(4), xRefP);
            }


            return pickerRoundBitmap;
        }

        public void invalidate(){
            invalidate = true;
        }
    }
}
