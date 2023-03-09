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
 * File created on 26/08/2015 at 01:42
 */
package org.thunderdog.challegram.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;

public class CheckView extends View {
  // public static final int MODE_MEDIA = 0;
  // public static final int MODE_AUDIO = 1;
  // public static final int MODE_ATTACH = 2;
  //public static final int MODE_USER = 3;
  public static final int MODE_LOCATION = 4;
  public static final int MODE_GALLERY = 5;

  private static final int CHECKABLE_TRANSITION_IN_DURATION = 200;
  private static final int CHECKABLE_TRANSITION_OUT_DURATION = 200;

  private static final int CHECK_TRANSITION_IN_DURATION = 200;
  private static final int CHECK_TRANSITION_OUT_DURATION = 200;

  private static int padding;
  private static int mediaRadius, audioRadius, attachRadius, locationRadius, galleryTotalRadius, galleryInnerRadius;
  private static int ringRadius;
  private static int elevation;

  private Paint ringBmpPaint;

  private boolean isChecked;
  private float checkFactor;

  private boolean isCheckable;
  private float checkableFactor;

  private Bitmap ringBitmap;
  private Canvas ringCanvas;

  private Bitmap checkBitmap;
  private Canvas checkCanvas;

  private int mode;

  private Callback callback;

  public static void reset () {
    if (mediaRadius != 0) {
      initSizes();
    }
  }

  public CheckView (Context context) {
    super(context);
    if (mediaRadius == 0) {
      initSizes();
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi (Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setOval(padding + getPaddingLeft(), padding + getPaddingTop(), mediaRadius * 2, mediaRadius * 2);
        }
      });
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return Views.isValid(this) && super.onTouchEvent(event);
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  public void initWithMode (int mode) {
    this.mode = mode;

    /*if (mode == MODE_AUDIO || mode == MODE_MEDIA) {
      ringBmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
      ringBmpPaint.setColor(0xffffffff);

      switch (mode) {
        case MODE_AUDIO: {
          ringBitmap = Bitmap.createBitmap(padding * 2 + audioRadius * 2, padding * 2 + audioRadius * 2, Bitmap.Config.ARGB_4444);
          break;
        }
        *//*case MODE_MEDIA: {
          ringBitmap = Bitmap.createBitmap(padding * 2 + mediaRadius * 2, padding * 2 + mediaRadius * 2, Bitmap.Config.ARGB_4444);
          break;
        }*//*
      }
      ringCanvas = new Canvas(ringBitmap);
    } else {
    }*/
    forceSetCheckable(true);

    prepareCheckItems();

    if (mode == MODE_GALLERY) {
      setCheckFactor(0f, true);
    }
  }

  public void forceSetChecked (boolean isChecked) {
    this.isChecked = isChecked;
    if (checkAnimator != null) {
      checkAnimator.cancel();
      checkAnimator = null;
    }
    setCheckFactor(isChecked ? 1f : 0f);
  }

  public void forceSetCheckable (boolean isCheckable) {
    this.isCheckable = isCheckable;
    if (checkableAnimator != null) {
      checkableAnimator.cancel();
      checkableAnimator = null;
    }
    setFactor(isCheckable ? 1f : 0f);
  }

  private ValueAnimator checkableAnimator;

  public void setCheckable (boolean isCheckable) {
    if (this.isCheckable != isCheckable) {
      this.isCheckable = isCheckable;
      if (checkableAnimator != null) {
        checkableAnimator.cancel();
      }
      final float startFactor = getFactor();
      checkableAnimator = AnimatorUtils.simpleValueAnimator();
      if (isCheckable) {
        final float diffFactor = 1f - startFactor;
        checkableAnimator.addUpdateListener(animation -> setFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
      } else {
        checkableAnimator.addUpdateListener(animation -> setFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
      }
      checkableAnimator.setDuration(isCheckable ? CHECKABLE_TRANSITION_IN_DURATION : CHECKABLE_TRANSITION_OUT_DURATION);
      checkableAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);

      if (callback != null) {
        checkableAnimator.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (Animator animation) {
            if (!CheckView.this.isCheckable) {
              forceSetChecked(false);
            }
            if (callback != null) {
              callback.onCheckableFinished(CheckView.this.isCheckable);
            }
          }
        });
      }

      checkableAnimator.start();
    }
  }

  public void setFactor (float factor) {
    /*if (this.checkableFactor != factor && (mode == MODE_AUDIO *//*|| mode == MODE_MEDIA*//*)) {
      this.checkableFactor = factor;

      if (factor != 0f) {
        ringBitmap.eraseColor(0);
        switch (mode) {
          case MODE_AUDIO: {
            ringCanvas.drawCircle(padding + audioRadius, padding + audioRadius, audioRadius, ringBigPaint);
            ringCanvas.drawCircle(padding + audioRadius, padding + audioRadius, audioRadius - (ringRadius * checkableFactor), Paints.getErasePaint());

            break;
          }
          *//*case MODE_MEDIA: {
            ringCanvas.drawCircle(padding + mediaRadius, padding + mediaRadius, mediaRadius, ringPaint);
            ringCanvas.drawCircle(padding + mediaRadius, padding + mediaRadius, mediaRadius - (ringRadius * checkableFactor), Paints.getErasePaint());

            break;
          }*//*
        }
        ringBmpPaint.setAlpha((int) (factor * RING_ALPHA_FACTOR));
      }

      invalidate();

      if (callback != null) {
        callback.onCheckableFactor(factor);
      }
    }*/
  }

  public float getFactor () {
    return checkableFactor;
  }

  private void prepareCheckItems () {
    switch (mode) {
      /*case MODE_ATTACH: {
        if (attachIcon == null) {
          attachIcon = BitmapFactory.decodeResource(UI.getResources(), R.drawable.ic_attach_check);
        }
        break;
      }
      case MODE_AUDIO: {
        if (audioIcon == null) {
          audioIcon = BitmapFactory.decodeResource(UI.getResources(), R.drawable.ic_checkbig);
        }
        break;
      }*/
      case MODE_LOCATION:
      case MODE_GALLERY: {
        // TODO do we need any items?
        break;
      }
      /*case MODE_MEDIA:
      case MODE_USER: {
        if (mediaIcon == null) {
          mediaIcon = BitmapFactory.decodeResource(UI.getResources(), R.drawable.ic_checksm);
        }
        break;
      }*/
    }
    if (checkBitmap == null) {
      switch (mode) {
        /*case MODE_ATTACH: {
          checkBitmap = Bitmap.createBitmap(padding * 2 + attachRadius * 2, padding * 2 + attachRadius * 2, Bitmap.Config.ARGB_8888);
          break;
        }
        case MODE_AUDIO: {
          checkBitmap = Bitmap.createBitmap(padding * 2 + audioRadius * 2, padding * 2 + audioRadius * 2, Bitmap.Config.ARGB_8888);
          break;
        }*/
        case MODE_LOCATION: {
          checkBitmap = Bitmap.createBitmap(padding * 2 + locationRadius * 2, padding * 2 + locationRadius * 2, Bitmap.Config.ARGB_8888);
          break;
        }
        case MODE_GALLERY: {
          checkBitmap = Bitmap.createBitmap(padding * 2 + galleryTotalRadius * 2, padding * 2 + galleryTotalRadius * 2, Bitmap.Config.ARGB_8888);
          break;
        }
        /*case MODE_MEDIA:
        case MODE_USER: {
          checkBitmap = Bitmap.createBitmap(padding * 2 + mediaRadius * 2, padding * 2 + mediaRadius * 2, Bitmap.Config.ARGB_8888);
          break;
        }*/
      }
      if (checkBitmap == null) { // should never reach here
        throw new IllegalStateException("couldn't load bitmap. abort");
      }
      checkCanvas = new Canvas(checkBitmap);
    }
  }

  private ValueAnimator checkAnimator;

  public void setChecked (boolean isChecked) {
    if (this.isChecked != isChecked) {
      if (getAlpha() == 0f || getVisibility() != View.VISIBLE) {
        forceSetChecked(isChecked);
        return;
      }
      this.isChecked = isChecked;
      if (checkAnimator != null) {
        checkAnimator.cancel();
      }

      final float startFactor = getCheckFactor();
      checkAnimator = AnimatorUtils.simpleValueAnimator();
      if (isChecked) {
        final float diffFactor = 1f - startFactor;
        checkAnimator.addUpdateListener(animation -> setCheckFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
      } else {
        checkAnimator.addUpdateListener(animation -> setCheckFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
      }
      checkAnimator.setDuration(isChecked ? CHECK_TRANSITION_IN_DURATION : CHECK_TRANSITION_OUT_DURATION);
      checkAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      if (isChecked) {
        checkAnimator.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (Animator animation) {
            forceSetCheckable(true);
            if (callback != null) {
              callback.onCheckFinished(CheckView.this.isChecked);
            }
          }
        });
      } else if (callback != null) {
        checkAnimator.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (Animator animation) {
            if (callback != null) {
              callback.onCheckFinished(CheckView.this.isChecked);
            }
          }
        });
      }
      checkAnimator.start();
    }
  }

  public void setCheckFactor (float factor) {
    setCheckFactor(factor, false);
  }

  public void setCheckFactor (float factor, boolean force) {
    if (this.checkFactor != factor || force) {
      boolean byForce = this.checkFactor == factor;
      this.checkFactor = factor;

      if (checkFactor != 0f || mode == MODE_GALLERY) {
        checkBitmap.eraseColor(0);
        switch (mode) {
          /*case MODE_ATTACH: {
            if (attachIcon != null) {
              checkCanvas.drawBitmap(attachIcon, 0, 0, paint);
              if (checkFactor != 1f) {
                checkCanvas.drawCircle(padding + attachRadius, padding + attachRadius, attachRadius - (attachRadius * checkFactor), Paints.getErasePaint());
              }
            }
            break;
          }
          case MODE_AUDIO: {
            if (audioIcon != null) {
              checkCanvas.drawBitmap(audioIcon, 0, 0, paint);
              if (checkFactor != 1f) {
                checkCanvas.drawCircle(padding + audioRadius, padding + audioRadius, audioRadius - (audioRadius * checkFactor), Paints.getErasePaint());
              }
            }
            break;
          }*/
          case MODE_LOCATION:
          case MODE_GALLERY: {
            int cx, cy;

            int checkColor = 0xffffffff;

            if (mode == MODE_LOCATION) {
              cx = padding + locationRadius;
              cy = padding + locationRadius;
              checkCanvas.drawCircle(cx, cy, locationRadius, Paints.fillingPaint(Theme.getColor(R.id.theme_color_fileAttach))); // theme_color_checkFilling
              // checkColor = Theme.getColor(R.id.theme_color_checkCheck);
            } else {
              cx = padding + galleryTotalRadius;
              cy = padding + galleryTotalRadius;

              checkCanvas.drawCircle(cx, cy, galleryTotalRadius, Paints.fillingPaint(0xffffffff));
              checkCanvas.drawCircle(cx, cy, galleryInnerRadius, Paints.fillingPaint(Theme.getColor(R.id.theme_color_checkActive)));
              checkColor = Theme.getColor(R.id.theme_color_checkContent);
            }

            final float fx;

            if (mode == MODE_LOCATION) {
              fx = checkFactor <= .2f ? 0f : (checkFactor - .2f) / .8f;
            } else {
              fx = checkFactor <= .75f ? 0f : (checkFactor - .75f) / .25f;
            }

            if (fx > 0) {
              final float t1;
              final float f1, f2;

              t1 = mode == MODE_LOCATION ? .3f : .45f;
              f1 = fx <= t1 ? fx / t1 : 1f;
              f2 = fx <= t1 ? 0f : (fx - t1) / (1f - t1);

              // check
              checkCanvas.save();

              if (mode == MODE_LOCATION) {
                checkCanvas.translate(cx / 2 - Screen.dp(2.5f), cy + Screen.dp(1f));
              } else {
                checkCanvas.translate(cx / 2 - Screen.dp(2.5f), cy + Screen.dp(2f));
              }
              checkCanvas.rotate(-45f);

              final int w2max, h1max;

              if (mode == MODE_LOCATION) {
                w2max = Screen.dp(14f);
                h1max = Screen.dp(7f);
              } else {
                h1max = Screen.dp(8f);
                w2max = Screen.dp(15f);
              }

              final int w2 = (int) ((float) w2max * f2);
              final int h1 = (int) ((float) h1max * f1);

              final int x1, y1;

              x1 = Screen.dp(4f);
              y1 = Screen.dp(11f);

              int lineSize = mode == MODE_LOCATION ? Screen.dp(1.5f) : Screen.dp(2.5f);
              checkCanvas.drawRect(x1, y1 - h1max, x1 + lineSize, y1 - h1max + h1, Paints.fillingPaint(checkColor));
              checkCanvas.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(checkColor));

              checkCanvas.restore();
            }

            // erase
            if (checkFactor != 1f) {
              if (mode == MODE_LOCATION) {
                checkCanvas.drawCircle(cx, cy, locationRadius - (locationRadius * checkFactor), Paints.getErasePaint());
              } else {
                checkCanvas.drawCircle(cx, cy, galleryInnerRadius - (galleryInnerRadius * checkFactor), Paints.getErasePaint());
              }
            }
            break;
          }
          /*case MODE_MEDIA:
          case MODE_USER: {
            if (mediaIcon != null) {
              checkCanvas.drawBitmap(mediaIcon, 0, 0, paint);
              if (checkFactor != 1f) {
                checkCanvas.drawCircle(padding + mediaRadius, padding + mediaRadius, mediaRadius - (mediaRadius * checkFactor), Paints.getErasePaint());
              }
            }
            break;
          }*/
        }
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        /*if (mode == MODE_MEDIA || mode == MODE_USER) {
          setElevation(elevation * factor);
          setTranslationZ(2f * factor);
        }*/
      }

      invalidate();

      if (callback != null && !byForce) {
        callback.onCheckFactor(factor);
      }
    }
  }

  public float getCheckFactor () {
    return checkFactor;
  }

  public boolean toggleChecked () {
    setChecked(!isChecked);
    return isChecked;
  }

  public boolean isChecked () {
    return isChecked;
  }

  public boolean isCheckable () {
    return isCheckable;
  }

  public boolean forceToggle () {
    isChecked = !isChecked;
    return isChecked;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (checkableFactor == 0f && checkFactor == 0f && mode != MODE_GALLERY) {
      return;
    }

    if (mode == MODE_GALLERY) {
      c.drawCircle(getPaddingLeft() + padding + galleryTotalRadius, getPaddingTop() + padding + galleryTotalRadius, galleryTotalRadius, Paints.fillingPaint(0xa0000000));
    }

    if (checkableFactor != 0f) {
      float checkableRadius, checkRadius;
      switch (mode) {
       /* case MODE_AUDIO: {
          checkableRadius = audioRadius - (ringRadius * checkableFactor);
          checkRadius = audioRadius - (audioRadius * checkFactor);
          break;
        }*/
        /*case MODE_MEDIA:
        case MODE_USER: {
          checkableRadius = mediaRadius - (ringRadius * checkableFactor);
          checkRadius = mediaRadius - (mediaRadius * checkFactor);
          break;
        }*/
        default: {
          checkableRadius = 0f;
          checkRadius = 0f;
        }
      }
      if (checkRadius > checkableRadius) {
        c.drawBitmap(ringBitmap, getPaddingLeft(), getPaddingTop(), ringBmpPaint);
      }
    }

    if (checkBitmap != null && (checkFactor != 0f || mode == MODE_GALLERY)) {
      c.drawBitmap(checkBitmap, getPaddingLeft(), getPaddingTop(), Paints.getBitmapPaint());
    }
  }

  private static void initSizes () {
    padding = Screen.dp(1f);
    mediaRadius = Screen.dp(10f);
    audioRadius = Screen.dp(19f);
    locationRadius = Screen.dp(20f);
    galleryTotalRadius = Screen.dp(20f);
    galleryInnerRadius = galleryTotalRadius - Screen.dp(2f);
    attachRadius = Screen.dp(15f);
    ringRadius = Screen.dp(2f);
    elevation = Screen.dp(2f);
  }

  public interface Callback {
    void onCheckFactor (float factor);
    void onCheckableFactor (float factor);
    void onCheckFinished (boolean isChecked);
    void onCheckableFinished (boolean isCheckable);
  }
}
