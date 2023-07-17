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
 *
 * File created on 12/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.VideoTimelineView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class VideoControlView extends FrameLayoutFix implements FactorAnimator.Target, Destroyable {
  private final TextView nowView;
  private final TextView totalView;
  private final SliderView sliderView;
  private final PlayPauseButton playPauseButton;

  public VideoControlView (Context context) {
    super(context);

    setWillNotDraw(false);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM);

    sliderView = new SliderView(context);
    sliderView.setAnchorMode(SliderView.ANCHOR_MODE_START);
    sliderView.setForceBackgroundColorId(ColorId.videoSliderInactive);
    sliderView.setForceSecondaryColorId(ColorId.videoSliderInactive);
    sliderView.setSlideEnabled(true, false);
    sliderView.setColorId(ColorId.videoSliderActive, false);
    sliderView.setPadding(Screen.dp(56f), 0, Screen.dp(56f), 0);
    sliderView.setLayoutParams(params);
    addView(sliderView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.LEFT | Gravity.BOTTOM);
    // params.leftMargin = Screen.dp(2f);

    this.nowView = new NoScrollTextView(context);
    styleText(nowView);
    this.nowView.setLayoutParams(params);
    addView(nowView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.RIGHT | Gravity.BOTTOM);
    // params.rightMargin = Screen.dp(2f);

    this.totalView = new NoScrollTextView(context);
    styleText(totalView);
    this.totalView.setLayoutParams(params);
    addView(totalView);

    playPauseButton = new PlayPauseButton(context);
    playPauseButton.setTranslationX(-Screen.dp(44f));
    playPauseButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(44f), Screen.dp(56f), Gravity.LEFT | Gravity.BOTTOM));
    addView(playPauseButton);
  }

  private @Nullable VideoTimelineView timelineView;

  public void addTrim (VideoTimelineView.TimelineDelegate delegate, ThemeDelegate forcedTheme) {
    setShowPlayPause(true, false);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM);

    timelineView = new VideoTimelineView(getContext());
    timelineView.setShowSlider(true, false);
    timelineView.setColors(ColorId.white, ColorId.black, ColorId.transparentEditor);
    timelineView.setPadding(Screen.dp(54f) + Screen.dp(32f), Screen.dp(6f), Screen.dp(54f), Screen.dp(6f));
    timelineView.setLayoutParams(params);
    timelineView.setDelegate(delegate);
    timelineView.setForcedTheme(forcedTheme);
    addView(timelineView, 0);
    timelineView.setVisibility(View.GONE);
  }

  private boolean timelineVisible;

  private void setTimelineVisible (boolean isVisible) {
    if (this.timelineVisible != isVisible && timelineView != null) {
      this.timelineVisible = isVisible;
      timelineView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
      sliderView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
    }
  }

  @Override
  public void performDestroy () {
    setFile(null);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return !Views.onTouchEvent(this, ev) || super.onInterceptTouchEvent(ev);
  }

  private BoolAnimator showPlayPause = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setShowPlayPause (boolean show, boolean animated) {
    if (timelineView == null) {
      showPlayPause.setValue(show || timelineVisible, animated);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    int dx = (int) ((float) Screen.dp(32f) * factor);
    nowView.setTranslationX(dx);
    sliderView.setAddPaddingLeft(dx);
    playPauseButton.setTranslationX(-Screen.dp(44f) * (1f - factor));
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public void setIsPlaying (boolean isPlaying, boolean animated) {
    playPauseButton.setIsPlaying(isPlaying, animated && showPlayPause.getFloatValue() > 0f);
  }

  public void setOnPlayPauseClick (View.OnClickListener onClickListener) {
    playPauseButton.setOnClickListener(onClickListener);
  }

  public void setSliderListener (SliderView.Listener listener) {
    sliderView.setListener(listener);
  }

  public void setInnerAlpha (float alpha) {
    sliderView.setAlpha(alpha);
    if (timelineView != null) {
      timelineView.setAlpha(alpha);
    }
    nowView.setAlpha(alpha);
    totalView.setAlpha(alpha);
  }

  private static void styleText (TextView textView) {
    textView.setTextColor(0xffffffff);
    textView.setPadding(Screen.dp(2f), 0, Screen.dp(2f), 0);
    textView.setGravity(Gravity.CENTER);
    textView.setSingleLine(true);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
    textView.setText(Strings.buildDuration(0));
  }

  private long totalDurationMs;

  public void resetDuration (long totalDurationMs, long nowDurationMs, boolean canSeek, boolean animated) {
    boolean hasSlider = canSeek && totalDurationMs > 0;
    sliderView.setSlideEnabled(hasSlider, animated);
    if (timelineView != null) {
      timelineView.setCanSlide(hasSlider, animated);
      timelineView.setSliderProgress(totalDurationMs > 0 ? (float) ((double) nowDurationMs / (double) totalDurationMs) : 0f);
      timelineView.invalidate();
    }
    setNow(nowDurationMs, animated);
    setTotalMs(totalDurationMs);
  }

  public void setFile (ImageGalleryFile file) {
    if (timelineView != null) {
      String path = file != null ? file.getFilePath() : null;
      boolean visible = !StringUtils.isEmpty(path);
      double startTime, endTime;
      double duration;
      float newStart, newEnd;
      if (file != null && file.hasTrim()) {
        long durationUs = file.getTotalDurationUs();
        long startUs = file.getStartTimeUs();
        long endUs = file.getEndTimeUs();
        newStart = (float) ((double) startUs / (double) durationUs);
        newEnd = (float) ((double) endUs / (double) durationUs);
        startTime = (double) startUs / 1_000_000.0;
        endTime = (double) endUs / 1_000_000.0;
        duration = (double) durationUs / 1_000_000.0;
      } else {
        newStart = 0f;
        newEnd = 1f;
        startTime = endTime = -1;
        duration = 0;
      }
      timelineView.setVideoPath(path, newStart, newEnd, startTime, endTime, duration, timelineVisible && visible);
      timelineView.setSliderProgress(0f);
      setTimelineVisible(visible);
    }
  }

  private long nowDurationMs;

  public void setNow (long nowMs, boolean animated) {
    setNowMs(nowMs);
    updateSlider(animated);
  }

  private boolean slideEnabled;

  public void setSlideEnabled (boolean isEnabled) {
    if (this.slideEnabled != isEnabled) {
      this.slideEnabled = isEnabled;
      updateSliderAvailability();
    }
  }

  private void updateSliderAvailability () {
    boolean hasSlider = slideEnabled && totalDurationMs > 0;
    sliderView.setSlideEnabled(hasSlider, true);
    if (timelineView != null) {
      timelineView.setCanSlide(hasSlider, true);
    }
  }

  private void updateSlider (boolean animated) {
    float progress = MathUtils.clamp(totalDurationMs > 0 ? (float) ((double) nowDurationMs / (double) totalDurationMs) : 0f);
    sliderView.setValue(progress);
    if (timelineView != null) {
      timelineView.setSliderProgress(progress);
    }
  }

  private void setNowMs (long ms) {
    if (this.nowDurationMs != ms) {
      this.nowDurationMs = ms;
      nowView.setText(Strings.buildDuration(Math.round(ms / 1000.0)));
    }
  }

  private void setTotalMs (long ms) {
    if (this.totalDurationMs != ms) {
      boolean changedState = (ms == 0 || totalDurationMs == 0);
      this.totalDurationMs = ms;
      totalView.setText(Strings.buildDuration(Math.round(ms / 1000.0)));
      if (changedState) {
        updateSliderAvailability();
      }
    }
  }

  public void updateSeek (long nowDurationMs, long durationMs, float progress) {
    setNowMs(nowDurationMs);
    setTotalMs(durationMs);
    progress = MathUtils.clamp(progress);
    if (sliderView != null) {
      sliderView.setValue(progress);
    }
    if (timelineView != null) {
      timelineView.setSliderProgress(progress);
    }
  }

  public void updateSecondarySeek (float offset, float progress) {
    if (sliderView != null) {
      sliderView.setSecondaryValue(offset, progress);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    c.drawRect(0, getMeasuredHeight() - Screen.dp(56f), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.getColor(ColorId.transparentEditor)));
  }
}
