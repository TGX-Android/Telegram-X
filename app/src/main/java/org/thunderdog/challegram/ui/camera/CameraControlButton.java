package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Date: 9/24/17
 * Author: default
 */

public class CameraControlButton extends View implements FactorAnimator.Target {
  private int alignGravity;

  private Drawable icon, toIcon;

  private boolean isSmall;

  public CameraControlButton (Context context) {
    super(context);

    setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f)));
  }

  public void setIsSmall () {
    isSmall = true;
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  public void setAlignGravity (int alignGravity) {
    if (this.alignGravity != alignGravity) {
      this.alignGravity = alignGravity;
      checkTranslation();
    }
  }

  private @DrawableRes int res;

  public void setIconRes (@DrawableRes int res) {
    cancelIconChange();
    this.res = res;
    this.icon = Drawables.get(getResources(), res);
    invalidate();
  }

  public void setCameraIconRes (boolean isFrontFace) {
    setIconRes(isFrontFace ? R.drawable.baseline_camera_rear_24 : R.drawable.baseline_camera_front_24);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return getAlpha() == 1f && getVisibility() == View.VISIBLE && super.onTouchEvent(event);
  }

  private float changeFactor;
  private FactorAnimator changeAnimator;
  private static final int ANIMATOR_CHANGE = 0;

  private void cancelIconChange () {
    if (changeAnimator != null) {
      changeAnimator.forceFactor(0f);
      changeFactor = 0f;
      toIcon = null;
    }
  }

  public void changeIcon (@DrawableRes int res) {
    if (this.res != res) {
      this.res = res;
      if (changeAnimator == null) {
        changeAnimator = new FactorAnimator(ANIMATOR_CHANGE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l);
      } else if (changeAnimator.isAnimating() && changeFactor < 1f) {
        changeAnimator.cancel();
        if (changeFactor > .5f) { // alpha = (1f - changeFactor) / .5f
          changeFactor = (1f - ((1f - changeFactor) / .5f)) * .5f;
          icon = toIcon;
        }
      } else {
        changeAnimator.forceFactor(0f);
        changeFactor = 0f;
      }
      this.toIcon = Drawables.get(getResources(), res);
      changeAnimator.animateTo(1f);
    }
  }

  private void setChangeFactor (float factor) {
    if (this.changeFactor != factor) {
      this.changeFactor = factor;
      invalidate();
    }
  }

  private float spinFactor;
  private FactorAnimator spinAnimator;
  private static final int ANIMATOR_SPIN = 1;

  private boolean spinForward, toFrontFace;

  public void forceSpin (boolean toFrontFace) {
    final float toFactor = toFrontFace ? 1f : 0f;
    if (spinAnimator != null) {
      spinAnimator.forceFactor(toFactor);
    }
    this.spinFactor = toFactor;
  }

  public void spinAround (boolean forward, boolean toFrontFace) {
    if (spinAnimator == null) {
      spinAnimator = new FactorAnimator(ANIMATOR_SPIN, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 360l);
    } else if (spinForward == forward && spinAnimator.isAnimating()) {
      return;
    }
    final float fromFactor = forward ? 0f : 1f;
    final float toFactor = forward ? 1f : 0f;

    if (spinFactor == 1f || spinFactor == 0f) {
      spinAnimator.forceFactor(fromFactor);
      spinFactor = fromFactor;
    }

    this.spinForward = forward;
    this.toFrontFace = toFrontFace;

    spinAnimator.animateTo(toFactor);
  }

  public interface SpinCallback {
    void onSpin (CameraControlButton v, float rotate, float scale);
  }

  private SpinCallback spinCallback;

  public void setSpinCallback (SpinCallback spinCallback) {
    this.spinCallback = spinCallback;
  }

  private void setSpinFactor (float factor) {
    this.spinFactor = factor;

    float rotationY = 180f * factor;
    setRotationY(rotationY);

    float scaleFactor = factor <= .5f ? factor / .5f : (1f - (factor - .5f) / .5f);
    float scale = 1f + .15f * scaleFactor;
    setScaleX(scale);
    setScaleY(scale);

    if ((spinForward && factor >= .5f) || (!spinForward && factor <= .5f)) {
      setCameraIconRes(toFrontFace);
    }

    if (spinCallback != null) {
      float rotate = factor <= .5f ? 180f * factor : -90f * (1f - (factor - .5f) / .5f);
      spinCallback.onSpin(this, rotate, scaleFactor);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CHANGE: {
        setChangeFactor(factor);
        break;
      }
      case ANIMATOR_SPIN: {
        setSpinFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CHANGE: {
        icon = toIcon;
        toIcon = null;
        changeAnimator.forceFactor(0f);
        changeFactor = 0f;
        break;
      }
    }
  }

  private boolean needTranslation;

  public void setNeedParentTranslation (boolean needTranslation) {
    this.needTranslation = needTranslation;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    checkTranslation();
  }

  private void checkTranslation () {
    if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
      applyTranslation(needTranslation ? (View) getParent() : this);
    }
  }

  public void applyTranslation (View view) {
    if (isSmall) {
      return;
    }
    ViewParent parent = view.getParent();
    if (parent != null) {
      int gravity = ((FrameLayoutFix.LayoutParams) view.getLayoutParams()).gravity;
      float dx, dy;
      if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.CENTER_HORIZONTAL) {
        dy = (((View) parent).getMeasuredHeight() / 4 + view.getMeasuredHeight() / 2) * (alignGravity == Gravity.RIGHT ? 1 : -1);
        dx = 0;
      } else {
        dx = (((View) parent).getMeasuredWidth() / 4 + view.getMeasuredWidth() / 2) * (alignGravity == Gravity.RIGHT ? 1 : -1);
        dy = 0;
      }
      view.setTranslationX(dx);
      view.setTranslationY(dy);
    }
  }

  private float componentRotation;
  public void setComponentRotation (float rotation) {
    if (this.componentRotation != rotation) {
      this.componentRotation = rotation;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (icon == null) {
      return;
    }

    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();

    float scale = Math.max(.8f, (float) viewWidth / (float) Screen.dp(56f));

    int cx = viewWidth / 2;
    int cy = viewHeight / 2;

    final float rotation = componentRotation;
    final boolean saved = rotation != 0f || scale != 1f;
    if (saved) {
      c.save();
      if (scale != 1f) {
        c.scale(scale, scale, cx, cy);
      }
      if (rotation != 0f) {
        c.rotate(rotation, cx, cy);
      }
    }

    Paint paint = isSmall ? Paints.getIconGrayPorterDuffPaint() : Paints.getPorterDuffPaint(0xffffffff);
    if (changeFactor == 0f) {
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, paint);
    } else {
      int offset = viewWidth / 2;
      int alpha = paint.getAlpha();
      paint.setAlpha((int) (255f * (1f - changeFactor)));
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2 + offset * changeFactor, paint);
      paint.setAlpha((int) (255f * changeFactor));
      Drawables.draw(c, toIcon, cx - toIcon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2 - offset * (1f - changeFactor), paint);
      paint.setAlpha(alpha);
    }

    if (saved) {
      c.restore();
    }
  }
}
