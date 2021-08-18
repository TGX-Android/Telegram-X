package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.util.ColorChanger;

/**
 * Date: 10/12/2016
 * Author: default
 */
public class EditButton extends View implements FactorAnimator.Target {
  private static final ColorChanger changer = new ColorChanger(0xffffffff, 0xff63cefd);

  private int currentIcon;
  private boolean willBeActive;

  private Drawable icon;
  private int iconRes;
  private Bitmap specialIcon;
  private Canvas specialIconCanvas;

  public EditButton (Context context) {
    super(context);
    setBackgroundResource(R.drawable.bg_btn_header_light);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return Views.isValid(this) && getAlpha() == 1f && super.onTouchEvent(event);
  }

  public void setIcon (int iconRes, boolean animated, boolean willBeActive) {
    if (this.currentIcon != iconRes) {
      this.currentIcon = iconRes;
      this.willBeActive = willBeActive;
      if (animated) {
        animateIcon();
      } else {
        forceIcon();
      }
    } else {
      setActive(willBeActive, animated);
    }
  }

  private boolean useFastAnimations;

  public void setUseFastAnimations () {
    this.useFastAnimations = true;
  }

  private static final int ACTIVE_ANIMATOR = 0;
  private static final int CHANGE_ANIMATOR = 1;
  private static final int EDITED_ANIMATOR = 2;

  private float editedFactor;
  private FactorAnimator editedAnimator;
  private boolean isEdited;

  private static final int EDITED_COLOR = 0xff64CEFD;

  public void setEdited (boolean isEdited, boolean animated) {
    if (this.isEdited != isEdited) {
      this.isEdited = isEdited;
      if (animated) {
        animateEditedFactor(isEdited ? 1f : 0f);
      } else {
        forceEditedFactor(isEdited ? 1f : 0f);
      }
    }
  }

  private void forceEditedFactor (float factor) {
    if (editedAnimator != null) {
      editedAnimator.forceFactor(factor);
    }
    setEditedFactor(factor);
  }

  private void animateEditedFactor (float toFactor) {
    if (editedAnimator == null) {
      editedAnimator = new FactorAnimator(EDITED_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 110, editedFactor);
    }
    editedAnimator.animateTo(toFactor);
  }

  private void setEditedFactor (float factor) {
    if (this.editedFactor != factor) {
      this.editedFactor = factor;
      invalidate();
    }
  }

  private FactorAnimator iconAnimator;
  private int pendingIcon;

  private void animateIcon () {
    if (iconAnimator == null) {
      iconAnimator = new FactorAnimator(CHANGE_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, useFastAnimations ? 180l : 380l);
    } else if (iconAnimator.getFactor() >= .5f) {
      iconAnimator.forceFactor(iconAnimator.getFactor() == 1f ? 0f : iconAnimator.getFactor() - .5f);
    }
    this.pendingIcon = currentIcon;
    iconAnimator.animateTo(1f);
  }

  private void forceIcon () {
    if (iconAnimator != null) {
      iconAnimator.forceFactor(0f);
    }
    this.pendingIcon = 0;
    setIconInternal(currentIcon);
    invalidate();
  }

  private float iconFactor;

  private void setIconInternal (int iconRes) {
    this.icon = Drawables.get(getResources(), iconRes);
    this.iconRes = iconRes;
    this.isActive = willBeActive;
    this.activeFactor = isActive ? 1f : 0f;
    if (icon != null) {
      switch (iconRes) {
        case R.drawable.baseline_volume_up_24: {
          if (specialIcon != null && (specialIcon.getWidth() != icon.getMinimumWidth() || specialIcon.getHeight() != icon.getMinimumHeight())) {
            specialIcon = null;
          }
          if (specialIcon == null || specialIcon.isRecycled()) {
            specialIcon = Bitmap.createBitmap(icon.getMinimumWidth(), icon.getMinimumHeight(), Bitmap.Config.ARGB_8888);
            specialIconCanvas = new Canvas(specialIcon);
          }
          drawSoundOn();
          break;
        }
      }
    }
  }

  private void drawSoundOn () {
    Canvas c = specialIconCanvas;
    specialIcon.eraseColor(0);
    Drawables.draw(c, icon, 0, 0, Paints.getPorterDuffPaint(0xffffffff));
    if (activeFactor != 0f) {
      int width = Screen.dp(2f);
      int height = Screen.dp(24f);
      int cx = icon.getMinimumWidth() / 2;
      int cy = Screen.dp(.5f);
      c.save();
      c.rotate(-45f, icon.getMinimumWidth() / 2, icon.getMinimumHeight() / 2);
      c.drawRect(cx, cy, cx + width, cy + (int) ((float) height * activeFactor), Paints.getErasePaint());
      c.drawRect(cx - width, cy, cx, cy + (int) ((float) height * activeFactor), Paints.fillingPaint(0xffffffff));
      c.restore();
    }
  }

  private void setIconFactor (float factor) {
    if (this.iconFactor != factor) {
      this.iconFactor = factor;
      if (factor >= .5f && pendingIcon != 0) {
        setIconInternal(pendingIcon);
        pendingIcon = 0;
      }
      invalidate();
    }
  }

  private Drawable secondIcon;

  public void setSecondIcon (@DrawableRes int icon) {
    this.secondIcon = Drawables.get(getResources(), icon);
  }

  private float secondFactor;

  public void setSecondFactor (float factor) {
    if (this.secondFactor != factor) {
      this.secondFactor = factor;
      invalidate();
    }
  }

  // active stuff

  private float activeFactor;

  private void setActiveFactor (float factor) {
    if (this.activeFactor != factor) {
      this.activeFactor = factor;

      switch (iconRes) {
        case R.drawable.baseline_volume_up_24: {
          drawSoundOn();
          break;
        }
      }

      invalidate();
    }
  }

  private boolean isActive;
  private FactorAnimator activeAnimator;

  private void animateActiveFactor (float toFactor) {
    if (activeAnimator == null) {
      activeAnimator = new FactorAnimator(ACTIVE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, activeFactor);
    }
    activeAnimator.animateTo(toFactor);
  }

  public boolean isActive () {
    return isActive;
  }

  public void setActive (boolean isActive, boolean animated) {
    if (this.isActive != isActive) {
      this.isActive = isActive;
      if (animated) {
        animateActiveFactor(isActive ? 1f : 0f);
      } else {
        forceActiveFactor(isActive ? 1f : 0f);
      }
    }
  }

  private void forceActiveFactor (float factor) {
    if (activeAnimator != null) {
      activeAnimator.forceFactor(factor);
    }
    setActiveFactor(factor);
  }

  // animator implementation

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ACTIVE_ANIMATOR: {
        setActiveFactor(factor);
        break;
      }
      case CHANGE_ANIMATOR: {
        setIconFactor(factor);
        break;
      }
      case EDITED_ANIMATOR: {
        setEditedFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private static final boolean USE_SCALE = true;
  private static final float MIN_SCALE = USE_SCALE ? .78f : 1f;
  private static final float STEP_FACTOR = USE_SCALE ? .45f : .5f;

  @Override
  protected void onDraw (Canvas c) {
    float activeFactor = iconRes != R.drawable.baseline_volume_up_24 ? this.activeFactor : 0f;
    Paint paint = Paints.getPorterDuffPaint(changer.getColor(activeFactor));

    int centerX = getMeasuredWidth() / 2;
    int centerY = getMeasuredHeight() / 2;

    float alpha;

    if (iconFactor <= STEP_FACTOR) {
      alpha = 1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(iconFactor / STEP_FACTOR);
    } else if (iconFactor < .5f) {
      alpha = 0f;
    } else if (iconFactor > .5f + STEP_FACTOR) {
      alpha = 1f;
    } else {
      alpha = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((iconFactor - .5f) / STEP_FACTOR);
    }

    if (alpha == 0f) {
      return;
    }

    if (alpha != 1f) {
      paint.setAlpha((int) (255f * alpha));
      if (USE_SCALE) {
        c.save();
        final float scale = MIN_SCALE + (1f - MIN_SCALE) * alpha;
        c.scale(scale, scale, centerX, centerY);
      }
    }

    int origAlpha = paint.getAlpha();

    int iconWidth = 0, iconHeight = 0;
    if (secondFactor < 1f) {
      paint.setAlpha((int) ((float) origAlpha * (1f - secondFactor)));
      Bitmap bitmap = iconRes == R.drawable.baseline_volume_up_24 ? this.specialIcon : null;
      if (bitmap != null && !bitmap.isRecycled()) {
        iconWidth = bitmap.getWidth();
        iconHeight = bitmap.getHeight();
        c.drawBitmap(bitmap, centerX - iconWidth / 2, centerY - iconHeight / 2, paint);
      } else if (this.icon != null) {
        iconWidth = this.icon.getMinimumWidth();
        iconHeight = this.icon.getMinimumHeight();
        Drawables.draw(c, this.icon, centerX - iconWidth / 2, centerY - iconHeight / 2, paint);
      }
    }
    if (secondFactor > 0f) {
      paint.setAlpha((int) ((float) origAlpha * secondFactor));
      Drawables.draw(c, secondIcon, centerX - iconWidth / 2, centerY - iconHeight / 2, paint);
    }
    paint.setAlpha(origAlpha);

    if (alpha != 1f) {
      paint.setAlpha(255);
      if (USE_SCALE) {
        c.restore();
      }
    }

    // float editFactor = Anim.OVERSHOOT_INTERPOLATOR.getInterpolation(editedFactor);
    if (editedFactor > 0f) {
      float editAlpha = MathUtils.clamp(editedFactor);
      int color = ColorUtils.color((int) (255f * editAlpha), EDITED_COLOR);

      c.drawCircle(centerX, getMeasuredHeight() - Screen.dp(9.5f), Screen.dp(2f), Paints.fillingPaint(color));
    }
  }
}
