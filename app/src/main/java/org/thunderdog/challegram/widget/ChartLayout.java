/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.charts.BarChartView;
import org.thunderdog.challegram.charts.BaseChartView;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.DoubleLinearChartView;
import org.thunderdog.challegram.charts.LinearChartView;
import org.thunderdog.challegram.charts.StackBarChartView;
import org.thunderdog.challegram.charts.data.ChartData;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.charts.data.DoubleLinearChartData;
import org.thunderdog.challegram.charts.data.StackBarChartData;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class ChartLayout extends FrameLayout implements FactorAnimator.Target, AttachDelegate, Chart.Listener {
  public interface Delegate {
    BaseChartView.SharedUiComponents provideSharedComponents ();
  }

  private final GifReceiver progressReceiver;

  public ChartLayout (@NonNull Context context) {
    super(context);

    this.progressReceiver = new GifReceiver(this);
    this.progressReceiver.detach();

    setWillNotDraw(false);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private Tdlib tdlib;
  private Delegate delegate;
  private Chart chart;

  public void setChart (Chart chart) {
    if (this.chart != chart) {
      if (this.chart != null) {
        this.chart.detach(this);
      }
      this.chart = chart;
      if (chart != null) {
        chartView.legendSignatureView.isTopHourChart = chart.isNoDate();
        // zoomedChartView.legendSignatureView.isTopHourChart = chart.isNoDate();
        updateChart(false);
        chart.attach(this);
      }
    }
  }

  private void updateChart (boolean isUpdate) {
    if (chart != null) {
      chartView.setListener(chart);
      ChartData baseData = chart.getBaseData();
      switch (chartType) {
        case ChartDataUtil.TYPE_DOUBLE_LINEAR: {
          ((DoubleLinearChartView) chartView).setData((DoubleLinearChartData) baseData);
          break;
        }
        case ChartDataUtil.TYPE_STACK_BAR: {
          ((StackBarChartView) chartView).setData((StackBarChartData) baseData);
          break;
        }
        case ChartDataUtil.TYPE_STACK_PIE: {
          ((BarChartView) chartView).setData(baseData);
          break;
        }
        case ChartDataUtil.TYPE_LINEAR:
        default: {
          ((LinearChartView) chartView).setData(baseData);
          break;
        }
      }
      chartView.legendSignatureView.showProgress(!chart.hasData(), !isUpdate);
      // isVisible.setValue(chart.hasData(), isUpdate);
    } else {
      chartView.setListener(null);
    }
  }

  @Override
  public void onChartDataChanged (Chart chart, ChartData newData) {
    updateChart(true);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    super.onMeasure(widthMeasureSpec, height > width ? widthMeasureSpec : MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    layoutBounds();
  }

  private void layoutBounds () {
    if (placeholderSticker != null) {
      int cx = getMeasuredWidth() / 2;
      int cy = getMeasuredHeight() / 2;
      if (cx != 0 && cy != 0) {
        int stickerWidth = Math.max(Screen.dp(100f), (int) Screen.px(placeholderSticker.width));
        int stickerHeight = Math.max(Screen.dp(100f), (int) Screen.px(placeholderSticker.height));
        cx -= stickerWidth / 2;
        cy -= stickerHeight / 2;
        progressReceiver.setBounds(cx, cy, cx + stickerWidth, cy + stickerHeight);
      }
    }
  }

  private boolean isAttached, progressAttached;

  private void checkProgressAttached () {
    boolean needAttach = isAttached && isVisible.getFloatValue() < 1f;
    if (this.progressAttached != needAttach) {
      this.progressAttached = needAttach;
      if (needAttach) {
        progressReceiver.attach();
      } else {
        progressReceiver.detach();
      }
    }
  }

  @Override
  public void attach () {
    isAttached = true;
    checkProgressAttached();
  }

  @Override
  public void detach () {
    isAttached = false;
    checkProgressAttached();
  }

  // Anim

  private static final int ANIMATOR_PROGRESS = 0;
  private final BoolAnimator isVisible = new BoolAnimator(ANIMATOR_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l, true);

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_PROGRESS: {
        /*chartView.setAlpha(factor);
        chartView.legendSignatureView.setAlpha(factor);
        zoomedChartView.setAlpha(factor);
        zoomedChartView.legendSignatureView.setAlpha(factor);*/
        invalidate();
        // TODO error visibility
        checkProgressAttached();
        break;
      }
    }
  }

  // Impl


  private int chartType;
  private BaseChartView<?, ?> chartView;
  private TdApi.Sticker placeholderSticker;

  public void initWithType (Tdlib tdlib, int type, Delegate delegate, @Nullable ViewController<?> themeProvider) {
    this.tdlib = tdlib;
    this.delegate = delegate;
    this.chartType = type;
    ViewSupport.setThemedBackground(this, ColorId.filling, themeProvider);

    tdlib.client().send(new TdApi.GetAnimatedEmoji(ContentPreview.EMOJI_ABACUS.textRepresentation), result -> {
      if (result.getConstructor() == TdApi.AnimatedEmoji.CONSTRUCTOR) {
        TdApi.AnimatedEmoji emoji = (TdApi.AnimatedEmoji) result;
        tdlib.runOnUiThread(() -> {
          placeholderSticker = emoji.sticker;
          GifFile file = new GifFile(tdlib, emoji.sticker.sticker, GifFile.TYPE_TG_LOTTIE);
          file.setScaleType(GifFile.FIT_CENTER);
          this.progressReceiver.requestFile(file);
          layoutBounds();
        });
      }
    });

    switch (type) {
      case ChartDataUtil.TYPE_DOUBLE_LINEAR: {
        chartView = new DoubleLinearChartView(getContext());
        /*zoomedChartView = new DoubleLinearChartView(getContext());
        zoomedChartView.legendSignatureView.useHour = true;*/
        break;
      }
      case ChartDataUtil.TYPE_STACK_BAR: {
        chartView = new StackBarChartView(getContext());
        /*zoomedChartView = new StackBarChartView(getContext());
        zoomedChartView.legendSignatureView.useHour = true;*/
        break;
      }
      case ChartDataUtil.TYPE_STACK_PIE: {
        chartView = new BarChartView(getContext());
        /*zoomedChartView = new LinearChartView(getContext());
        zoomedChartView.legendSignatureView.useHour = true;
        setClipChildren(false);
        setClipToPadding(false);*/
        break;
      }
      case ChartDataUtil.TYPE_LINEAR:
      default: {
        chartView = new LinearChartView(getContext());
        /*zoomedChartView = new LinearChartView(getContext());
        zoomedChartView.legendSignatureView.useHour = true;*/
        break;
      }
    }

    chartView.sharedUiComponents = delegate.provideSharedComponents();
    // zoomedChartView.sharedUiComponents = sharedUiComponents;

    addView(chartView);
    addView(chartView.legendSignatureView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    /*addView(zoomedChartView);
    addView(zoomedChartView.legendSignatureView, WRAP_CONTENT, WRAP_CONTENT);*/

    chartView.legendSignatureView.showProgress(false, true);

    chartView.updateColors();
    // zoomedChartView.updateColors();

    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(chartView);
      // themeProvider.addThemeInvalidateListener(zoomedChartView);
    }

    // TODO show sticker emoji until stats is loaed
  }

  @Override
  protected void onDraw (Canvas c) {
    progressReceiver.setAlpha(1f - isVisible.getFloatValue());
    progressReceiver.draw(c);
  }
}
