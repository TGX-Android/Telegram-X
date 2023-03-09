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
 * File created on 25/02/2016 at 23:23
 */
package org.thunderdog.challegram.component.preview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.util.ColorChanger;

public class YouTubePlayerControls extends View {
  public static final int SEEK_COLOR = 0xffffffff; // 0xff6bade0
  private static final int LOADED_COLOR = 0xffe8e7e7;
  private static final int REMAIN_COLOR = 0x99e8e7e7;
  private static final ColorChanger SEEK_CHANGER = new ColorChanger(0xffffffff, 0xff6bade0);
  private static final ColorChanger REMAIN_CHANGER = new ColorChanger(REMAIN_COLOR, 0x00e8e7e7);

  private static final int FLAG_FULLSCREEN = 0x01;
  private static final int FLAG_HIDDEN = 0x02;
  private static final int FLAG_PLAYING = 0x04;
  private static final int FLAG_MINIMIZED = 0x08;
  private static final int FLAG_NEED_CLIP = 0x10;
  private static final int FLAG_ANIMATING = 0x20;

  private boolean canMinimize;

  // Resources

  private Drawable minimizeIcon, fullscreenIcon, fullscreenExitIcon, playIcon;

  private int seekLeft, seekRight, seekBottom;
  private int seekHeight, seekHeightSmall, seekHeightDiff;
  private int seekRadius, seekRadiusDiff;

  private int minTop, minRight;

  private int fullRight, fullBottom, fullSize;

  private int curCenter, remainCenter;
  private int timeBottom;

  private int playPauseSize;

  private int gradientHeight;
  private RectF gradientRect;

  // private Paint textPaint;
  private Paint topGradient;
  private Paint bottomGradient;

  // Values

  private int viewWidth, viewHeight, viewOffset;
  private int flags;

  public YouTubePlayerControls (Context context) {
    super(context);

    // Icons

    minimizeIcon = Drawables.get(getResources(), R.drawable.baseline_picture_in_picture_24);
    fullscreenIcon = Drawables.get(getResources(), R.drawable.baseline_fullscreen_24);
    fullscreenExitIcon = Drawables.get(getResources(), R.drawable.baseline_fullscreen_exit_24);
    playIcon = Drawables.get(getResources(), R.drawable.baseline_play_circle_outline_48);

    // Sizes

    seekLeft = Screen.dp(62f);
    seekRight = Screen.dp(103f);
    seekBottom = Screen.dp(19f);
    seekHeight = Screen.dp(4f);
    seekHeightSmall = Screen.dp(1.5f);
    seekHeightDiff = seekHeight - seekHeightSmall;
    // seekEdge = Screen.dp(29f + 10f); // Top edge where seek must be invalidated. 10f is just for safety
    seekRadius = Screen.dp(6f);
    seekRadiusDiff = Screen.dp(3f);

    minTop = Screen.dp(11f);
    minRight = Screen.dp(17f);
    // minEdge = Screen.dp(52f);

    fullRight = Screen.dp(22f);
    fullBottom = Screen.dp(14f);
    fullSize = Screen.dp(19f);

    playPauseSize = Screen.dp(48f);

    curCenter = Screen.dp(31f);
    remainCenter = Screen.dp(72f);
    timeBottom = Screen.dp(17f);

    gradientHeight = Screen.dp(46f);
    gradientRect = new RectF();
    gradientRect.bottom = gradientHeight;

    // Paints

    LinearGradient shader;
    shader = new LinearGradient(0, 0, 0, gradientHeight, 0x00000000, 0x90000000, Shader.TileMode.CLAMP);
    bottomGradient = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    bottomGradient.setShader(shader);
    shader = new LinearGradient(0, 0, 0, gradientHeight, 0x90000000, 0x00000000, Shader.TileMode.CLAMP);
    topGradient = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    topGradient.setShader(shader);

    // Прочая херня

    factor = 1f;
  }

  public void setCanMinimize (boolean canMinimize) {
    this.canMinimize = canMinimize;
  }

  // View height

  public void setCurrentHeight (int width, int height, int safetyOffset) {
    this.viewWidth = width;
    this.viewHeight = height;
    this.viewOffset = safetyOffset;
    invalidateRegion(true);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY));
  }

  // Show-hide factor

  private float factor;

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidateRegion(true);
    }
  }

  public float getFactor () {
    return factor;
  }

  // Minimize factor

  public void setMinimized (boolean minimized) {
    if (minimized) {
      flags |= FLAG_MINIMIZED;
      minimize = 1f;
    } else {
      flags &= ~FLAG_MINIMIZED;
      minimize = 0f;
    }
    invalidateRegion(true);
  }

  private float minimize;

  public void setMinimize (float factor) {
    if (this.minimize != factor) {
      this.minimize = factor;
      invalidateRegion(true);
    }
  }

  public float getMinimize () {
    return minimize;
  }

  public void hideIfVisible () {
    if ((flags & FLAG_HIDDEN) == 0) {
      showHide(false);
    }
  }

  public void showIfHidden () {
    if ((flags & FLAG_HIDDEN) != 0) {
      showHide(true);
    }
  }

  public void showHide (boolean show) {
    if (show) {
      flags &= ~FLAG_HIDDEN;
    } else {
      flags |= FLAG_HIDDEN;
    }

    final float startFactor = getMinimize();
    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    if (show) {
      obj.addUpdateListener(animation -> setMinimize(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
    } else {
      final float diffFactor = 1f - startFactor;
      obj.addUpdateListener(animation -> setMinimize(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    }
    obj.setDuration(200l);
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.start();

    if (callback != null) {
      callback.onShowHideControls(show);
    }
  }

  // Duration

  private int duration, seek;
  private int remainWidth, seekWidth;
  private String remainStr, seekStr;

  public void setDuration (int durationInMillis) {
    this.duration = durationInMillis;
    buildDuration();
    invalidateRegion(false);
  }

  public void setCurrentSeek (int seekInMillis) {
    if (this.seek != seekInMillis) {
      this.seek = seekInMillis;
      buildDuration();
      invalidateRegion(false);
    }
  }

  private void buildDuration () {
    int prevCurrent = seekStr == null ? 0 : seekStr.length();
    int prevRemain = remainStr == null ? 0 : remainStr.length();

    int seek = this.seek / 1000;
    int duration = this.duration / 1000;

    seekStr = YouTube.buildDuration(seek);
    remainStr = YouTube.buildDuration(duration - seek);

    if (seekStr.length() != prevCurrent) {
      seekWidth = (int) U.measureText(seekStr, Paints.getRegularTextPaint(13f));
    }

    if (remainStr.length() != prevRemain) {
      remainWidth = (int) U.measureText(remainStr, Paints.getRegularTextPaint(13f));
    }
  }

  // Drawing

  public void invalidateRegion () {
    invalidateRegion(true);
  }

  @Override
  public void setAlpha (float alpha) {
    if (getAlpha() != alpha) {
      super.setAlpha(alpha);
      invalidateRegion(true);
    }
  }

  private void invalidateRegion (boolean withControls) {
    invalidate(0, -viewHeight - viewOffset, getMeasuredWidth(), getMeasuredHeight());
  }

  private void drawGradient (Canvas c) {
    if (minimize != 1f) {
      if (minimize != 0f) {
        int alpha = (int) (255f * (1f - minimize));
        topGradient.setAlpha(alpha);
        bottomGradient.setAlpha(alpha);
      }
      gradientRect.right = viewWidth;
      c.drawRect(gradientRect, topGradient);
      c.save();
      c.translate(0, viewHeight - gradientHeight); // 0, height
      c.drawRect(gradientRect, bottomGradient);
      c.restore();
      if (minimize != 0) {
        topGradient.setAlpha(255);
        bottomGradient.setAlpha(255);
      }
    }
  }

  private void drawSeek (Canvas c) {
    float seek = duration == 0 ? 0f : (float) this.seek / (float) duration;
    int seekWidth = viewWidth - (int) ((float) (seekLeft + seekRight) * (1f - minimize));
    int seekHeight = this.seekHeight - (int) ((float) seekHeightDiff * minimize);
    int seekBottom = (int) ((float) this.seekBottom * (1f - minimize));

    int top = viewHeight - seekBottom - seekHeight;
    int bottom = viewHeight - seekBottom;

    int left = (int) ((float) this.seekLeft * (1f - minimize));
    int right = left + (int) ((float) seekWidth * seek);
    int fullRight = left + seekWidth;

    c.drawRect(right, top, fullRight, bottom, Paints.fillingPaint(REMAIN_CHANGER.getColor(minimize)));
    c.drawRect(left, top, right, bottom, Paints.fillingPaint(SEEK_CHANGER.getColor(minimize)));

    int seekRadius = this.seekRadius + (int) ((float) seekRadiusDiff * seekFactor);

    c.drawCircle(touchSeek != -1 ? touchSeek : right, top + (int) ((float) seekHeight * .5f), (int) ((float) seekRadius * (1f - minimize)), Paints.reuseFillingPaint());
  }

  private void drawControls (Canvas c) {
    final Drawable icon = (flags & FLAG_FULLSCREEN) != 0 ? fullscreenExitIcon : fullscreenIcon;
    Paint bitmapPaint = Paints.getPorterDuffPaint(0xffffffff);


    if (minimize == 0f) {
      if (canMinimize) {
        Drawables.draw(c, minimizeIcon, viewWidth - minRight - minimizeIcon.getMinimumWidth(), minTop, bitmapPaint);
      }
      Drawables.draw(c, icon, viewWidth - fullRight - fullSize, viewHeight - fullBottom - fullSize, bitmapPaint);
    } else {
      if (canMinimize) {
        bitmapPaint.setAlpha((int) (255f * (1f - minimize)));
        Drawables.draw(c, minimizeIcon, viewWidth - minRight - minimizeIcon.getMinimumWidth(), minTop, bitmapPaint);
        bitmapPaint.setAlpha(255);
      }
      Drawables.draw(c, icon, viewWidth - fullRight - fullSize + (int) ((float) seekRight * minimize), viewHeight - fullBottom - fullSize + (int) ((float) (seekBottom + seekHeightDiff) * minimize), bitmapPaint);
    }

    if (pauseFactor != 0f) {
      int cx = (int) ((float) viewWidth * .5f);
      int cy = (int) ((float) viewHeight * .5f);

      if (pauseFactor != 1f) {
        float scale = 1f + .25f * (1f - pauseFactor);

        c.save();
        c.scale(scale, scale, cx, cy);

        bitmapPaint.setAlpha((int) (255f * pauseFactor));
      }

      Drawables.draw(c, playIcon, cx - (int) ((float) playIcon.getMinimumWidth() * .5f), cy - (int) ((float) playIcon.getMinimumHeight() * .5f), bitmapPaint);

      if (pauseFactor != 1f) {
        bitmapPaint.setAlpha(255);
        c.restore();
      }
    }
  }

  private void drawTime (Canvas c) {
    Paint textPaint = Paints.getRegularTextPaint(13f, 0xffffffff);
    if (minimize == 0f) {
      if (seekStr != null)
        c.drawText(seekStr, curCenter - (int) ((float) seekWidth * .5f), viewHeight - timeBottom, textPaint);
      if (remainStr != null)
        c.drawText(remainStr, viewWidth - remainCenter - (int) ((float) remainWidth * .5f), viewHeight - timeBottom, textPaint);
    } else {
      if (seekStr != null)
        c.drawText(seekStr, curCenter - (int) ((float) seekWidth * .5f) - (int) ((float) seekLeft * minimize), viewHeight - timeBottom + (int) ((float) (seekBottom + seekHeightDiff) * minimize), textPaint);
      if (remainStr != null)
        c.drawText(remainStr, viewWidth - remainCenter - (int) ((float) remainWidth * .5f) + (int) ((float) seekRight * minimize), viewHeight - timeBottom + (int) ((float) (seekBottom + seekHeightDiff) * minimize), textPaint);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    int alpha = (int) (255f * Math.max(factor, minimize));
    if (alpha != 0) {
      c.save();
      c.translate(0, -viewHeight - viewOffset);

      if ((flags & FLAG_NEED_CLIP) != 0) {
        c.clipRect(0, 0, viewWidth, viewHeight);
      }

      if (seekStr != null && remainStr != null) {
        drawGradient(c);
        drawSeek(c);
        drawTime(c);
        drawControls(c);
      }

      c.restore();
    }
  }

  // Clipping

  public void setNeedClip (boolean clip) {
    if (clip) {
      flags |= FLAG_NEED_CLIP;
    } else {
      flags &= ~FLAG_NEED_CLIP;
    }
  }

  // Fullscreen

  public void setFullscreen (boolean fullscreen) {
    if (fullscreen) {
      if ((flags & FLAG_FULLSCREEN) != 0) {
        return;
      }
      flags |= FLAG_FULLSCREEN;
    } else {
      if ((flags & FLAG_FULLSCREEN) == 0) {
        return;
      }
      flags &= ~FLAG_FULLSCREEN;
    }
    invalidateRegion(true);
  }

  // PlayPause

  private float pauseFactor;

  public void setPause (float factor) {
    if (pauseFactor != factor) {
      pauseFactor = factor;
      invalidateRegion(true);
    }
  }

  public float getPause () {
    return pauseFactor;
  }

  public void onFinished () {
    onPlayPause(false, true);
    setCurrentSeek(0);
    setSeekFactor(0f);
    if (callback != null) {
      callback.onStopped();
    }
  }

  public void onRestarted () {
    if (callback != null) {
      callback.onRestarted();
    }
  }

  public boolean isPlaying () {
    return (flags & FLAG_PLAYING) != 0;
  }

  public void onPlayPause (boolean isPlaying, boolean finished) {
    if (callback != null && !finished && callback.shouldIgnorePlayPause()) {
      return;
    }

    if (isPlaying) {
      if ((flags & FLAG_PLAYING) != 0) {
        return;
      }
      flags |= FLAG_PLAYING;
    } else {
      if ((flags & FLAG_PLAYING) == 0) {
        return;
      }
      flags &= ~FLAG_PLAYING;
    }

    final float startFactor = getPause();
    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    if (isPlaying) {
      obj.addUpdateListener(animation -> setPause(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
    } else {
      final float diffFactor = 1f - startFactor;
      obj.addUpdateListener(animation -> setPause(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    }
    obj.setDuration(200l);
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.start();
  }

  // Controls

  private float seekFactor;

  public void setSeekFactor (float factor) {
    if (this.seekFactor != factor) {
      this.seekFactor = factor;
      invalidateRegion(false);
    }
  }

  public float getSeekFactor () {
    return seekFactor;
  }

  private static final int SEEK_DURATION = 160;

  private void startSeek () {
    ValueAnimator obj;
    final float startFactor = getSeekFactor();
    final float diffFactor = 1f - startFactor;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setSeekFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    obj.setDuration(SEEK_DURATION);
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.start();
  }

  private void stopSeek (boolean apply) {
    if (apply) {
      final int timeInMillis = (int) ((float) duration * (((float) (touchSeek - seekLeft) / (float) (viewWidth - seekLeft - seekRight))));

      final float startFactor = getSeekFactor();
      ValueAnimator obj;
      obj = AnimatorUtils.simpleValueAnimator();
      obj.addUpdateListener(animation -> setSeekFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
      obj.setDuration(SEEK_DURATION);
      obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      obj.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          setCurrentSeek(timeInMillis);
          touchSeek = -1;
          if (callback != null) {
            callback.onSeekTo(timeInMillis);
          }
        }
      });
      obj.start();
    } else {
      touchSeek = -1;
      invalidateRegion(false);
    }
  }

  private int calculateDx (float x, float y) {
    float factor = Math.max(Math.min((float) viewHeight - gradientHeight, y), 0f) / (viewHeight - gradientHeight) * .75f + .25f;
    int dx = (int) ((x - lastX) * factor);
    lastX = x;
    return dx;
  }

  private void moveSeek (float x, float y) {
    touchSeek = Math.min(Math.max(touchSeek + calculateDx(x, y), seekLeft), viewWidth - seekRight);
    invalidateRegion(false);
  }

  public interface Callback {
    void onPlayPause ();
    boolean shouldIgnorePlayPause ();
    void onSeekTo (int timeInMillis);
    void onRequestMinimize ();
    void onRequestExpand ();
    void onRequestFullscreen (boolean inFullscreen);
    void onStartClose ();
    void onStopped ();
    void onRestarted ();
    void onShowHideControls (boolean show);
  }

  private Callback callback;

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  // touch handler

  private static final int FLAG_SEEKING = 0x01;
  private static final int FLAG_CAUGHT_MINIMIZE = 0x02;
  private static final int FLAG_CAUGHT_FULLSCREEN = 0x04;
  private static final int FLAG_CAUGHT_SHOW_HIDE = 0x08;
  private static final int FLAG_CAUGHT_PLAY_PAUSE = 0x10;
  private static final int FLAG_CAUGHT_EXPAND = 0x20;

  private int touchFlags;
  private int touchSeek = -1;
  private float lastX, startX, startY;

  public boolean processTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        lastX = startX = e.getX();
        startY = e.getY();

        if ((flags & FLAG_ANIMATING) != 0) {
          touchFlags = 0;
          break;
        }

        if (minimize == 0f) {
          if (canMinimize && startX >= viewWidth - minRight - minimizeIcon.getMinimumWidth() - minRight && startX <= viewWidth && startY <= minTop + minimizeIcon.getMinimumHeight() + minTop && startY >= 0) {
            touchFlags = FLAG_CAUGHT_MINIMIZE;
            break;
          } else if (startX >= viewWidth - fullRight - fullSize - fullSize && startX <= viewWidth && startY >= viewHeight - fullBottom - fullSize - fullSize && startY <= viewHeight) {
            touchFlags = FLAG_CAUGHT_FULLSCREEN;
            break;
          } else {
            float seek = duration == 0 ? 0f : (float) this.seek / (float) duration;
            int seekWidth = viewWidth - seekLeft - seekRight;
            int x = seekLeft + (int) ((float) seekWidth * seek);
            int y = viewHeight - seekBottom - (int) ((float) seekHeight * .5f);
            int radius = seekRadius * 4;
            if (startX >= x - radius && startX <= x + radius && startY >= y - radius && startY <= y + radius) {
              touchSeek = x;
              touchFlags = FLAG_SEEKING;
              startSeek();
              break;
            }
          }
        }

        int cx = (int) ((float) viewWidth * .5f);
        int cy = (int) ((float) viewHeight * .5f);

        if ((flags & FLAG_MINIMIZED) != 0) {
          int bound = (int) ((float) playIcon.getMinimumWidth() * .5f);
          if (startX >= cx - bound && startX <= cx + bound && startY >= cy - bound && startY <= cy + bound) {
            touchFlags = FLAG_CAUGHT_PLAY_PAUSE;
          } else if (startX >= 0 && startX <= viewWidth && startY >= 0 && startY <= viewHeight) {
            touchFlags = FLAG_CAUGHT_EXPAND;
          }
        } else {
          if (startX >= cx - playPauseSize && startX <= cx + playPauseSize && startY >= cy - playPauseSize && startY <= cy + playPauseSize) {
            touchFlags = FLAG_CAUGHT_PLAY_PAUSE;
          } else {
            touchFlags = FLAG_CAUGHT_SHOW_HIDE;
          }
        }

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if ((touchFlags & FLAG_SEEKING) != 0) {
          stopSeek(false);
        }
        touchFlags = 0;
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (touchFlags != 0) {
          if ((touchFlags & FLAG_SEEKING) != 0) {
            moveSeek(e.getX(), e.getY());
            break;
          }
          float diffX = Math.abs(startX - e.getX());
          float diffY = Math.abs(startY - e.getY());
          if (Math.max(diffX, diffY) >= Screen.getTouchSlopY()) {
            if ((flags & FLAG_MINIMIZED) != 0) {
              if (((touchFlags & FLAG_CAUGHT_EXPAND) != 0 || (touchFlags & FLAG_CAUGHT_PLAY_PAUSE) != 0) && diffX > diffY && callback != null){
                callback.onStartClose();
              }
            }
            touchFlags = 0;
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (touchFlags != 0) {
          if ((touchFlags & FLAG_SEEKING) != 0) {
            stopSeek(true);
          } else if ((touchFlags & FLAG_CAUGHT_FULLSCREEN) != 0) {
            if (callback != null) {
              callback.onRequestFullscreen((flags & FLAG_FULLSCREEN) == 0);
            }
          } else if (canMinimize && (touchFlags & FLAG_CAUGHT_MINIMIZE) != 0) {
            if (callback != null) {
              callback.onRequestMinimize();
            }
          } else if ((touchFlags & FLAG_CAUGHT_PLAY_PAUSE) != 0) {
            if (callback != null) {
              callback.onPlayPause();
            }
          } else if ((touchFlags & FLAG_CAUGHT_SHOW_HIDE) != 0) {
            showHide((flags & FLAG_HIDDEN) != 0);
          } else if ((touchFlags & FLAG_CAUGHT_EXPAND) != 0) {
            if (callback != null) {
              callback.onRequestExpand();
            }
          }
          touchFlags = 0;
        }
        break;
      }
    }
    return true;
  }
}
