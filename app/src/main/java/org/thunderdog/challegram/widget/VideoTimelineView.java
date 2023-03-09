/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 16/10/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.filegen.VideoData;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;

public class VideoTimelineView extends View implements Destroyable, FactorAnimator.Target {
  private final ArrayList<Frame> frames = new ArrayList<>();
  private static class VideoHandler extends Handler {
    private final VideoTimelineView context;

    public VideoHandler (VideoTimelineView context) {
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case 0:
          context.addFrame(msg.arg1, (Frame) msg.obj);
          break;
      }
    }
  }

  private final VideoHandler handler = new VideoHandler(this);
  private final Rect srcRect = new Rect();

  public VideoTimelineView (Context context) {
    super(context);
  }

  private int frameCount;

  private void setFrameCount (int frameCount) {
    if (this.frameCount != frameCount) {
      this.frameCount = frameCount;
      getFramesIfNeeded();
    }
  }

  private int getTimelineWidth () {
    return getMeasuredWidth() - getPaddingRight() - getPaddingLeft() - getAddPaddingHorizontal() * 2;
  }

  private int getTimelineHeight () {
    return getMeasuredHeight() - getPaddingTop() - getPaddingBottom() - getAddPaddingVertical() * 2;
  }

  private float calculateFrameWidth () {
    return frameCount > 0 ? ((float) getTimelineWidth() / (float) frameCount) : 0;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    setFrameCount((int) Math.ceil((float) getTimelineWidth() / (float) getTimelineHeight() * 1.2f));
  }

  private String videoPath;

  public final void setVideoPath (final String path) {
    setVideoPath(path, 0f, 1f, -1, -1, 0, false);
  }

  @UiThread
  public final void setVideoPath (final String path, float start, float end, double startTime, double endTime, double duration, boolean animated) {
    this.normalizedStart = startTime;
    this.normalizedEnd = endTime;
    if (!StringUtils.equalsOrBothEmpty(videoPath, path)) {
      this.videoPath = path;
      this.totalDuration = duration;
      ImageReader.instance().post(() -> prepareRetriever(path));
      if (StringUtils.isEmpty(path)) {
        clearFrames();
      } else {
        getFramesIfNeeded();
      }
    }
    setStartEnd(start, end, animated);
  }

  private CancellableRunnable scheduledTask;

  private void cancelScheduledTask () {
    if (scheduledTask != null) {
      scheduledTask.cancel();
      scheduledTask = null;
    }
  }

  private void getFramesIfNeeded () {
    cancelScheduledTask();
    if (!StringUtils.isEmpty(videoPath) && frameCount > 0) {
      clearFrames();
      final String path = videoPath;
      final int count = frameCount;
      final int size = (int) ((float) getTimelineHeight() * Math.min(2f, Math.max(1f, Screen.density())));
      scheduledTask = new CancellableRunnable() {
        @Override
        public void act () {
          processVideoPath(this, path, count, size);
        }
      };
      ImageReader.instance().post(scheduledTask);
    }
  }

  @Override
  public void performDestroy () {
    setVideoPath(null);
  }

  @UiThread
  private void clearFrames () {
    for (Frame frame : frames) {
      frame.destroy();
    }
    frames.clear();
    invalidate();
  }

  private VideoData videoData;
  private String retrieverPath;
  private double totalDuration, width, height;
  private int frameRate;
  private long bitrate;
  private double currentTime = -1;

  public void setCurrentTime (double currentTime) {
    if (this.currentTime != currentTime) {
      this.currentTime = currentTime;
      invalidate();
    }
  }

  private void setStartEnd (float start, float end, boolean animated) {
    if (isMoving) {
      setMoving(false, true);
    }
    if (animated) {
      startFactor.animateTo(start);
      endFactor.animateTo(end);
    } else {
      startFactor.forceFactor(start);
      endFactor.forceFactor(end);
    }
  }

  // private static final long MIN_TRIM_DURATION = 2000l;

  private void prepareRetriever (String path) {
    if (!StringUtils.equalsOrBothEmpty(retrieverPath, path)) {
      if (videoData != null) {
        videoData.release();
        retrieverPath = null;
      }
      this.retrieverPath = path;
      if (!StringUtils.isEmpty(path)) {
        try {
          videoData = new VideoData(path);
          totalDuration = videoData.getTotalDuration();
          width = videoData.getWidth(); height = videoData.getHeight();
          frameRate = videoData.getFrameRate();
          bitrate = videoData.getBitrate();
          handler.post(() ->
            delegate.onVideoLoaded(this, totalDuration, width, height, frameRate, bitrate)
          );
        } catch (Throwable ignored) {
          prepareRetriever(null);
        }
      }
    }
  }

  private static final int SLIDE_MODE_NONE = 0;
  private static final int SLIDE_MODE_START = 1;
  private static final int SLIDE_MODE_END = 2;
  private static final int SLIDE_MODE_SEEK = 3;

  public interface TimelineDelegate {
    boolean canTrimTimeline (VideoTimelineView v);
    void onTrimStartEnd (VideoTimelineView v, boolean isStarted);
    default void onVideoLoaded (VideoTimelineView v, double totalDuration, double width, double height, int frameRate, long bitrate) { }
    void onTimelineTrimChanged (VideoTimelineView v, double totalDuration, double startTimeSeconds, double endTimeSeconds);
    void onSeekTo (VideoTimelineView v, float progress);
  }

  private TimelineDelegate delegate;

  public void setDelegate (TimelineDelegate delegate) {
    this.delegate = delegate;
  }

  private boolean setMoving (boolean isMoving, boolean notify) {
    if (this.isMoving != isMoving) {
      this.isMoving = isMoving;
      if (delegate != null && notify) {
        delegate.onTrimStartEnd(this, isMoving);
      }
    }
    return isMoving;
  }

  private double getCurrentStart () {
    return totalDuration * (double) startFactor.getFactor();
  }

  private double getCurrentEnd () {
    return totalDuration * (double) endFactor.getFactor();
  }

  private double normalizedStart = -1, normalizedEnd = -1;

  private void normalizeValues (boolean end) {
    if (videoData != null && totalDuration > 0) {
      double normalizedEnd, normalizedStart;
      if (end) {
        startFactor.cancel();
        normalizedStart = this.normalizedStart;
        if (normalizedStart == -1)
          normalizedStart = 0;
        normalizedEnd = videoData.correctEndTime(normalizedStart, getCurrentEnd(), true);
        endFactor.animateTo((float) (normalizedEnd / totalDuration));
      } else {
        endFactor.cancel();
        normalizedEnd = this.normalizedEnd;
        if (normalizedEnd == -1)
          normalizedEnd = totalDuration;
        normalizedStart = videoData.correctStartTime(getCurrentStart(), normalizedEnd, true);
        startFactor.animateTo((float) (normalizedStart / totalDuration));
      }
      if (this.normalizedStart != normalizedStart || this.normalizedEnd != normalizedEnd) {
        this.normalizedStart = normalizedStart;
        this.normalizedEnd = normalizedEnd;
        if (delegate != null) {
          delegate.onTimelineTrimChanged(this, totalDuration, normalizedStart, normalizedEnd);
        }
      }
      updateTooltip(end);
      TooltipOverlayView.TooltipInfo info = end ? endTooltip : startTooltip;
      if (info != null) {
        info.hideDelayed(true, 1, TimeUnit.SECONDS);
      }
    }
  }

  private int slideMode;
  private void setSlideMode (int slideMode) {
    if (this.slideMode != slideMode) {
      boolean isSliding = slideMode != SLIDE_MODE_NONE;
      this.slideMode = slideMode;
      getParent().requestDisallowInterceptTouchEvent(isSliding);
    }
  }

  private boolean isMoving;
  private float downX;
  private float downFactor;

  private TooltipOverlayView.LocationProvider endProvider, startProvider;

  private TooltipOverlayView.LocationProvider locationProvider (int mode) {
    switch (mode) {
      case SLIDE_MODE_END:
        return endProvider != null ? endProvider : (endProvider = (targetView, outRect) -> {
          final int paddingLeft = getPaddingLeft() + getAddPaddingHorizontal();
          final int timelineWidth = getTimelineWidth();
          outRect.left = (int) (paddingLeft + endFactor.getFactor() * timelineWidth);
          outRect.right = outRect.left + getAddPaddingHorizontal();
          outRect.top = getPaddingTop() - getAddPaddingVertical();
          outRect.bottom = getMeasuredHeight() - getPaddingBottom() + getAddPaddingVertical();
        });
      case SLIDE_MODE_START:
        return startProvider != null ? startProvider : (startProvider = (targetView, outRect) -> {
          final int paddingLeft = getPaddingLeft() + getAddPaddingHorizontal();
          final int timelineWidth = getTimelineWidth();
          outRect.right = (int) (paddingLeft + startFactor.getFactor() * timelineWidth);
          outRect.left = outRect.right - getAddPaddingHorizontal();
          outRect.top = getPaddingTop() - getAddPaddingVertical();
          outRect.bottom = getMeasuredHeight() - getPaddingBottom() + getAddPaddingVertical();
        });
    }
    return null;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    float x = e.getX();
    final int paddingLeft = getPaddingLeft();
    final int timelineWidth = getTimelineWidth();
    final float startX = paddingLeft + startFactor.getFactor() * timelineWidth;
    final float endX = paddingLeft + endFactor.getFactor() * timelineWidth;

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        int slideMode = SLIDE_MODE_NONE;

        if (videoData != null && (delegate == null || delegate.canTrimTimeline(this))) {
          float startCenterX = startX - getAddPaddingHorizontal() / 2f;
          float endCenterX = endX + getAddPaddingHorizontal() / 2f;

          float startDiffX = Math.abs(x - startCenterX);
          float endDiffX = Math.abs(x - endCenterX);

          int padding = Screen.dp(32f);
          if (Math.min(startDiffX, endDiffX) <= padding) {
            slideMode = startDiffX < endDiffX ? SLIDE_MODE_START : SLIDE_MODE_END;
          }
          if (slideEnabled.getValue() && sliderVisible.getFloatValue() > 0f) {
            int realStartX, realEndX;
            if (normalizedStart == -1 || normalizedEnd == -1) {
              realStartX = paddingLeft;
              realEndX = paddingLeft + timelineWidth;
            } else {
              realStartX = paddingLeft + (int) ((double) timelineWidth * (normalizedStart / totalDuration));
              realEndX = paddingLeft + (int) ((double) timelineWidth * (normalizedEnd / totalDuration));
            }
            float slideDiffX = Math.abs(x - Math.max(realStartX, Math.min(realEndX, realStartX + (int) ((realEndX - realStartX) * sliderProgress))));
            if (slideDiffX < Math.min(startDiffX, endDiffX) && slideDiffX < padding) {
              slideMode = SLIDE_MODE_SEEK;
            }
          }
        }

        if (slideMode != SLIDE_MODE_NONE) {
          if ((slideMode == SLIDE_MODE_START || slideMode == SLIDE_MODE_END) && (totalDuration <= 0 || !videoData.canTrim())) {
            BaseActivity context = UI.getContext(getContext());
            context.tooltipManager().builder(this).locate(locationProvider(slideMode)).color(forcedTheme != null ? context.tooltipManager().overrideColorProvider(forcedTheme) : null).show(null, R.string.VideoTrimUnsupported).hideDelayed();
            slideMode = SLIDE_MODE_NONE;
          } else if (videoData == null || (slideMode == SLIDE_MODE_END && startFactor.isAnimating()) || (slideMode == SLIDE_MODE_START && endFactor.isAnimating())) {
            slideMode = SLIDE_MODE_NONE;
          } else {
            if (slideMode == SLIDE_MODE_END)
              endFactor.cancel();
            else
              startFactor.cancel();
          }
        }
        setMoving(false, slideMode != SLIDE_MODE_SEEK);
        setSlideMode(slideMode);
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (slideMode != SLIDE_MODE_NONE) {
          if (isMoving) {
            float diff = (x - downX) * (1f - .9f * MathUtils.clamp((-e.getY() / ((float) getTimelineHeight() * 4f)))) / (float) timelineWidth;
            float result = downFactor;
            boolean changed = false;
            switch (slideMode) {
              case SLIDE_MODE_START: {
                result = Math.max(0f, Math.min(1f, Math.min(endFactor.getFactor(), this.downFactor + diff)));
                if (startFactor.getFactor() != result) {
                  startFactor.forceFactor(result);
                  changed = true;
                }
                break;
              }
              case SLIDE_MODE_END: {
                result = Math.min(1f, Math.max(0f, Math.max(startFactor.getFactor(), this.downFactor + diff)));
                if (endFactor.getFactor() != result) {
                  endFactor.forceFactor(result);
                  changed = true;
                }
                break;
              }
              case SLIDE_MODE_SEEK: {
                result = MathUtils.clamp(this.downFactor + diff, startFactor.getFactor(), endFactor.getFactor());
                float realProgress = (result - startFactor.getFactor()) / (endFactor.getFactor() - startFactor.getFactor());
                if (this.sliderProgress != realProgress) {
                  this.sliderProgress = realProgress;
                  changed = true;
                }
                break;
              }
            }
            downX = x;
            downFactor = result;
            if (changed) {
              updateTooltip(slideMode == SLIDE_MODE_END);
              invalidate();
            }
          } else if (setMoving(Math.abs(downX - x) >= Screen.getTouchSlop(), slideMode != SLIDE_MODE_SEEK)) {
            downX = x;
            switch (slideMode) {
              case SLIDE_MODE_START:
                downFactor = startFactor.getFactor();
                break;
              case SLIDE_MODE_END:
                downFactor = endFactor.getFactor();
                break;
              case SLIDE_MODE_SEEK:
                downFactor = startFactor.getFactor() + (endFactor.getFactor() - startFactor.getFactor()) * sliderProgress;
                break;
            }
            showTooltip();
          }
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        int oldSlideMode = this.slideMode;
        setSlideMode(SLIDE_MODE_NONE);
        if (isMoving) {
          setMoving(false, oldSlideMode != SLIDE_MODE_SEEK);
          switch (oldSlideMode) {
            case SLIDE_MODE_END:
            case SLIDE_MODE_START:
              normalizeValues(oldSlideMode == SLIDE_MODE_END);
              break;
            case SLIDE_MODE_SEEK: {
              if (delegate != null) {
                delegate.onSeekTo(this, sliderProgress);
              }
              break;
            }
          }
        }
        break;
    }
    return true;
  }

  private void showTooltip () {
    if (slideMode != SLIDE_MODE_END && slideMode != SLIDE_MODE_START)
      return;
    boolean isEnd = slideMode == SLIDE_MODE_END;
    TooltipOverlayView.TooltipInfo info = isEnd ? endTooltip : startTooltip;
    if (info == null) {
      BaseActivity context = UI.getContext(getContext());
      info = context.tooltipManager().builder(this).locate(locationProvider(slideMode)).color(forcedTheme != null ? context.tooltipManager().overrideColorProvider(forcedTheme) : null).show(null, buildText(isEnd));
      if (isEnd) {
        endTooltip = info;
      } else {
        startTooltip = info;
      }
    } else {
      info.reset(null, buildText(isEnd), 0);
      info.show();
    }
  }

  private void updateTooltip (boolean isEnd) {
    TooltipOverlayView.TooltipInfo info = isEnd ? endTooltip : startTooltip;
    if (info != null) {
      double time = getTime(isEnd);
      if (Math.round(time * 10000.0) != (isEnd ? endTooltipTime : startTooltipTime)) {
        info.reset(null, buildText(isEnd), 0);
      }
      info.reposition();
    }
  }

  private double getTime (boolean end) {
    return isMoving ? (end ? getCurrentEnd() : getCurrentStart()) : (end ? normalizedEnd : normalizedStart);
  }

  private String buildText (boolean isEnd) {
    double time = getTime(isEnd);
    StringBuilder b = new StringBuilder();
    Strings.buildDuration(Math.round(time * 1000.0), TimeUnit.MILLISECONDS, true, b);
    long checkTime = Math.round(time * 10000.0);
    if (isEnd) {
      endTooltipTime = checkTime;
    } else {
      startTooltipTime = checkTime;
    }
    return b.toString();
  }

  @WorkerThread
  private void processVideoPath (CancellableRunnable context, String path, int count, int size) {
    prepareRetriever(path);
    if (videoData != null) {
      double timePerFrame = totalDuration / (double) count;
      Bitmap prevGoodBitmap = null;
      double prevBitmapTime = 0;
      for (int i = 0; i < count; i++) {
        if (!context.isPending() || !StringUtils.equalsOrBothEmpty(videoPath, path) || frameCount != count) {
          return;
        }
        Bitmap bitmap;
        final double time = videoData.findClosestSync(timePerFrame * i);
        if (prevGoodBitmap != null && prevBitmapTime == time) {
          bitmap = prevGoodBitmap;
        } else {
          bitmap = videoData.getFrameAtTime(time, size);
          if (U.isValidBitmap(bitmap)) {
            prevGoodBitmap = bitmap;
            prevBitmapTime = time;
          } else if (prevGoodBitmap != null) {
            bitmap = prevGoodBitmap;
          }
        }
        if (bitmap != null) {
          Frame frame = new Frame(this, path, bitmap);
          dispatchFrame(count, frame);
        }
      }
    }
  }

  private void dispatchFrame (int frameCount, Frame frame) {
    handler.sendMessage(Message.obtain(handler, 0, frameCount, 0, frame));
  }

  @UiThread
  private void addFrame (int frameCount, Frame frame) {
    if (StringUtils.equalsOrBothEmpty(frame.path, videoPath) && this.frameCount == frameCount) {
      int index = frames.size();
      this.frames.add(frame);
      float chunkWidth = calculateFrameWidth();
      int start = (int) (chunkWidth * index);
      frame.setPosition(start, 0, (int) (start + chunkWidth), getTimelineHeight(), true);
      frame.launchShow();
    }
  }

  private final FactorAnimator
    startFactor = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, 0f),
    endFactor = new FactorAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, 1f);

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    updateTooltip(id == 1);
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f) {
      TooltipOverlayView.TooltipInfo info = id == 1 ? endTooltip : startTooltip;
      if (info != null) {
        info.hideDelayed(true, 1, TimeUnit.SECONDS);
      }
    }
  }

  private TooltipOverlayView.TooltipInfo startTooltip, endTooltip;
  private long startTooltipTime = -1, endTooltipTime = -1;

  private int sliderActiveColorId = R.id.theme_color_sliderActive;
  private int iconColorId = R.id.theme_color_filling;
  private int overlayColorId = R.id.theme_color_previewBackground;

  public void setColors (int sliderActiveColorId, int iconColorId, int overlayColorId) {
    if (this.sliderActiveColorId != sliderActiveColorId || this.iconColorId != iconColorId || this.overlayColorId != overlayColorId) {
      this.sliderActiveColorId = sliderActiveColorId;
      this.iconColorId = iconColorId;
      this.overlayColorId = overlayColorId;
      invalidate();
    }
  }

  private int getAddPaddingHorizontal () {
    return Screen.dp(8f) + Screen.dp(2f);
  }

  private int getAddPaddingVertical () {
    return Screen.dp(2f);
  }

  private ThemeDelegate forcedTheme;

  public void setForcedTheme (ThemeDelegate theme) {
    if (this.forcedTheme != theme) {
      this.forcedTheme = theme;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    final int paddingLeft = getPaddingLeft() + getAddPaddingHorizontal();
    final int paddingTop = getPaddingTop() + getAddPaddingVertical();
    final int timelineWidth = getTimelineWidth();
    final int timelineHeight = getTimelineHeight();
    final float frameWidth = calculateFrameWidth();

    float cx = paddingLeft;
    for (Frame frame : frames) {
      frame.setPosition(Math.max(paddingLeft, (int) cx), paddingTop, Math.min(paddingLeft + timelineWidth, (int) (cx + frameWidth)), paddingTop + timelineHeight, false);
      frame.draw(c, (float) timelineHeight / frameWidth);
      cx += frameWidth;
    }

    RectF rectF = Paints.getRectF();

    int barsColor, cornerColor, overlayColor;
    if (forcedTheme != null) {
      barsColor = forcedTheme.getColor(sliderActiveColorId);
      cornerColor = forcedTheme.getColor(iconColorId);
      overlayColor = forcedTheme.getColor(overlayColorId);
    } else {
      barsColor = Theme.getColor(sliderActiveColorId);
      cornerColor = Theme.getColor(iconColorId);
      overlayColor = Theme.getColor(overlayColorId);
    }

    int startX = paddingLeft + (int) (timelineWidth * startFactor.getFactor());
    int endX = paddingLeft + (int) (timelineWidth * endFactor.getFactor());

    int strokeSize = Screen.dp(2f);
    int insetSize = Screen.dp(8f);

    rectF.set(startX - insetSize, paddingTop - strokeSize / 2f, endX + insetSize, paddingTop + timelineHeight + strokeSize / 2f);

    if (rectF.left > paddingLeft) {
      c.drawRect(paddingLeft, paddingTop, rectF.left, paddingTop + timelineHeight, Paints.fillingPaint(overlayColor));
    }
    if (rectF.right < paddingLeft + timelineWidth) {
      c.drawRect(rectF.right, paddingTop, paddingLeft + timelineWidth, paddingTop + timelineHeight, Paints.fillingPaint(overlayColor));
    }
    c.drawRoundRect(rectF, Screen.dp(2f), Screen.dp(2f), Paints.strokeBigPaint(barsColor));
    c.drawRect(rectF.left + strokeSize / 2f, rectF.top + strokeSize / 2f, rectF.left + insetSize, rectF.bottom - strokeSize / 2f, Paints.fillingPaint(barsColor));
    c.drawRect(rectF.right - insetSize, rectF.top + strokeSize / 2f, rectF.right - strokeSize / 2f, rectF.bottom - strokeSize / 2f, Paints.fillingPaint(barsColor));

    float cy = rectF.centerY();

    float leftX = startX - (insetSize + strokeSize) / 2f;
    c.save();
    c.scale(.6f, .6f, leftX - Screen.dp(5f), cy);
    DrawAlgorithms.drawDirection(c, leftX, cy, cornerColor, Gravity.LEFT);
    c.restore();

    float rightX = endX + (insetSize + strokeSize) / 2f;
    c.save();
    c.scale(.6f, .6f, rightX + Screen.dp(5f), cy);
    DrawAlgorithms.drawDirection(c, rightX, cy, cornerColor, Gravity.RIGHT);
    c.restore();

    float sliderVisibility = this.sliderVisible.getFloatValue();
    if (sliderVisibility > 0) {
      int realStartX, realEndX;
      if (normalizedStart == -1 || normalizedEnd == -1) {
        realStartX = paddingLeft;
        realEndX = paddingLeft + timelineWidth;
      } else {
        realStartX = paddingLeft + (int) ((double) timelineWidth * (normalizedStart / totalDuration));
        realEndX = paddingLeft + (int) ((double) timelineWidth * (normalizedEnd / totalDuration));
      }

      int x = Math.max(realStartX, Math.min(realEndX, realStartX + (int) ((realEndX - realStartX) * sliderProgress)));
      int color = ColorUtils.alphaColor(sliderVisibility, barsColor);
      int strokeWidth = Screen.dp(2f);
      int circleRadius = Math.min(getPaddingBottom() / 2 - Screen.separatorSize(), Screen.dp(3f));

      int startY = paddingTop - getAddPaddingVertical() - Screen.separatorSize();
      int endY = paddingTop + timelineHeight + getAddPaddingVertical();

      int bgColor = ColorUtils.alphaColor(sliderVisibility, Color.BLACK);
      c.drawLine(x, startY, x, endY, Paints.getProgressPaint(bgColor, strokeWidth + Screen.separatorSize()));
      c.drawCircle(x, endY + circleRadius, circleRadius + Screen.separatorSize(), Paints.fillingPaint(bgColor));

      float alpha = .5f + .5f * slideEnabled.getFloatValue();

      c.drawLine(x, startY, x, endY, Paints.getProgressPaint(ColorUtils.alphaColor(alpha, color), strokeWidth));
      c.drawCircle(x, endY + circleRadius, circleRadius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, color)));
    }
  }

  private static class Frame implements FactorAnimator.Target {
    private final VideoTimelineView parent;
    private final String path;
    private final Bitmap bitmap;
    private int left, top, right, bottom;
    private float appearFactor;

    private final FactorAnimator appearAnimator;
    private boolean isShown;

    public Frame (VideoTimelineView parent, String path, Bitmap bitmap) {
      this.parent = parent;
      this.path = path;
      this.bitmap = bitmap;
      this.appearAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
    }

    public void setPosition (int left, int top, int right, int bottom, boolean invalidate) {
      if (this.left != left || this.top != top || this.right != right || this.bottom != bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        if (invalidate && appearFactor > 0f) {
          invalidate();
        }
      }
    }

    public void destroy () {
      if (appearAnimator.isAnimating()) {
        appearAnimator.cancel();
      }
      if (bitmap != null && !bitmap.isRecycled()) {
        bitmap.recycle();
      }
    }

    private void invalidate () {
      if (left != 0 || top != 0 || right != 0 || bottom != 0) {
        parent.invalidate(left, top, right, bottom);
      }
    }

    public void launchShow () {
      if (!isShown) {
        isShown = true;
        appearAnimator.animateTo(1f);
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      if (this.appearFactor != factor) {
        this.appearFactor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    public void draw (Canvas c, float ratio) {
      if (appearFactor > 0f && bitmap != null && !bitmap.isRecycled()) {
        Rect dstRect = Paints.getRect();
        int srcCx = bitmap.getWidth() / 2;
        int srcCy = bitmap.getHeight() / 2;

        int diffX = (int) (srcCx / 2 * Math.max(1f, ratio));
        int diffY = (int) (srcCy / 2 * Math.max(1f, ratio));
        parent.srcRect.set(srcCx - diffX, srcCy - diffY, srcCx + diffX, srcCy + diffY);

        c.save();

        int rectWidth = right - left;
        int rectHeight = bottom - top;
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        float scale = Math.max((float) rectWidth / (float) imageWidth, (float) rectHeight / (float) imageHeight);
        imageWidth *= scale;
        imageHeight *= scale;

        int dstCx = (left + right) / 2;
        int dstCy = (top + bottom) / 2;
        dstRect.set(dstCx - imageWidth / 2, dstCy - imageHeight / 2, dstCx + imageWidth / 2, dstCy + imageHeight / 2);

        c.clipRect(left, top, right, bottom);


        final Paint paint = Paints.getBitmapPaint();
        if (appearFactor < 1f) {
          paint.setAlpha((int) (255f * appearFactor));
          float displayScale = .6f + .4f * appearFactor;
          c.scale(displayScale, displayScale, dstCx, dstCy);
        }
        c.drawBitmap(bitmap, parent.srcRect, dstRect, paint);
        if (appearFactor < 1f) {
          paint.setAlpha(255);
        }
        c.restore();
      }
    }
  }

  // Slider

  private BoolAnimator sliderVisible = new BoolAnimator(1, (id, factor, fraction, callee) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setShowSlider (boolean show, boolean animated) {
    sliderVisible.setValue(show, animated);
  }

  private final BoolAnimator slideEnabled = new BoolAnimator(1, (id, factor, fraction, callee) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setCanSlide (boolean canSlide, boolean animated) {
    this.slideEnabled.setValue(canSlide, animated && sliderVisible.getFloatValue() > 0f);
  }

  private float sliderProgress;

  public void setSliderProgress (float progress) {
    if (this.sliderProgress != progress && !(isMoving && slideMode == SLIDE_MODE_SEEK)) {
      this.sliderProgress = progress;
      invalidate();
    }
  }
}
