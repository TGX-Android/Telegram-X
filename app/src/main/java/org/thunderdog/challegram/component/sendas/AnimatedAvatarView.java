package org.thunderdog.challegram.component.sendas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class AnimatedAvatarView extends View {

  private @Nullable Drawable image;
  private @Nullable Drawable prevImage;

  private final Paint imagePaint;
  private final Paint prevImagePaint;

  public AnimatedAvatarView (Context context) {
    super(context);

    imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    imagePaint.setStyle(Paint.Style.FILL);
    imagePaint.setColorFilter(Paints.createColorFilter(Theme.iconColor()));

    prevImagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    prevImagePaint.setStyle(Paint.Style.FILL);
    prevImagePaint.setColorFilter(Paints.createColorFilter(Theme.iconColor()));
  }

  private FactorAnimator animator;
  private float factor = 1f;
  private boolean up = true;

  public void setAvatar(Drawable avatar, boolean animated, boolean up) {
    this.up = up;

    if (animated) {
      this.prevImage = this.image;
      this.image = avatar;

      if (animator == null) {
        animator = new FactorAnimator(1, new FactorAnimator.Target() {
          @Override
          public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
            AnimatedAvatarView.this.factor = factor;
            invalidate();
          }

          @Override
          public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
            prevImage = null;
          }
        }, AnimatorUtils.DECELERATE_INTERPOLATOR, 150L);
      }

      animator.forceFactor(0f, false);
      animator.animateTo(1f);
    } else {
      this.factor = 1f;
      this.prevImage = null;
      this.image = avatar;
      invalidate();
    }
  }

  public void setNewAvatar(Drawable avatar, boolean up) {
    this.prevImage = this.image;
    this.image = avatar;

    this.factor = 0f;
    this.up = up;
  }

  public void setFactor(float factor) {
    this.factor = factor;
    if (factor == 1f) {
      this.prevImage = null;
    }
    invalidate();
  }

  public void setColorFilter (int color) {
    imagePaint.setColorFilter(Paints.createColorFilter(color));
    prevImagePaint.setColorFilter(Paints.createColorFilter(color));
    invalidate();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    // TODO: paddings
    int cx = getWidth() / 2;
    int cy = getHeight() / 2;

    int dy = (int) (getHeight() * factor);

    if (prevImage != null) {
      final var y = up ? cy - dy : cy + dy;
      prevImagePaint.setAlpha((int) (255f * (1f - factor)));
      Drawables.drawCentered(canvas, prevImage, cx, y, prevImagePaint);
    }
    if (image != null) {
      final var y = up ? getHeight() + cy - dy : -cy + dy;
      imagePaint.setAlpha((int) (255f * factor));
      Drawables.drawCentered(canvas, image, cx, y, imagePaint);
    }
  }
}
