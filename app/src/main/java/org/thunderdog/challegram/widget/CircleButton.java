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
 * File created on 02/02/2016 at 19:20
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ReactionLoadListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class CircleButton extends View implements FactorAnimator.Target, ReactionLoadListener, Destroyable {
  private Drawable icon;
  private int offsetLeft;

  private int iconColor;
  private boolean iconColorIsId;
  private int backgroundColorId;

  private int crossBackgroundColorId, crossIconColorId;

  private final @Nullable Tdlib tdlib;
  private final @Nullable ImageReceiver imageReceiver;
  private final @Nullable GifReceiver gifReceiver;

  public CircleButton (Context context) {
    super(context);
    Views.setClickable(this);

    this.tdlib = null;
    this.imageReceiver = null;
    this.gifReceiver = null;
  }

  public CircleButton (Context context, @NonNull Tdlib tdlib) {
    super(context);
    Views.setClickable(this);

    this.tdlib = tdlib;
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
  }

  public void setIconColorId (int colorId) {
    if (!this.iconColorIsId || this.iconColor != colorId) {
      this.iconColorIsId = true;
      this.iconColor = colorId;
      if (this.imageReceiver != null) {
        this.imageReceiver.setThemedPorterDuffColorId(colorId);
      }
      if (this.gifReceiver != null) {
        this.gifReceiver.setThemedPorterDuffColorId(colorId);
      }
      invalidate();
    }
  }

  public void setCustomIconColor (int color) {
    if (this.iconColorIsId || this.iconColor != color) {
      this.iconColorIsId = false;
      this.iconColor = color;
      if (this.imageReceiver != null) {
        this.imageReceiver.setPorterDuffColorFilter(color);
      }
      if (this.gifReceiver != null) {
        this.gifReceiver.setPorterDuffColorFilter(color);
      }
      invalidate();
    }
  }

  private String bottomText;
  private float bottomTextWidth;

  public void setBottomText (String bottomText) {
    if (bottomText != null && bottomText.isEmpty()) {
      bottomText = null;
    }
    if (!StringUtils.equalsOrBothEmpty(this.bottomText, bottomText)) {
      this.bottomText = bottomText;
      this.bottomTextWidth = U.measureText(bottomText, Paints.getRegularTextPaint(14f));
      invalidate();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return !(e.getAction() == MotionEvent.ACTION_DOWN && (getAlpha() == 0f || !isEnabled() || (getParent() != null && ((View) getParent()).getAlpha() == 0f))) && super.onTouchEvent(e);
  }

  public final void init (@DrawableRes int icon, float size, float padding, @ColorId int backgroundColorId, @ColorId int iconColorId) {
    init(icon, 0, size, padding, backgroundColorId, iconColorId);
  }

  public void init (@DrawableRes int icon, int offsetLeft, float size, float padding, @ColorId int backgroundColorId, @ColorId int iconColorId) {
    init(icon, offsetLeft, size, padding, backgroundColorId, iconColorId, true);
  }

  public void init (@DrawableRes int icon, int offsetLeft, float size, float padding, @ColorId int backgroundColorId, @ColorId int iconColorId, boolean needShadow) {
    this.icon = Drawables.get(icon);
    this.offsetLeft = offsetLeft;
    this.backgroundColorId = backgroundColorId;
    this.iconColorIsId = true;
    this.iconColor = iconColorId;
    RippleSupport.setCircleBackground(this, size, padding, backgroundColorId, needShadow, null);
  }

  public void setCrossColorId (@ColorId int backgroundColorId, @ColorId int iconColorId) {
    this.crossBackgroundColorId = backgroundColorId;
    this.crossIconColorId = iconColorId;
  }

  private boolean isCounterClockwise;
  private float factor;

  public void setRotationFactor (boolean isCounterClockwise, float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      this.isCounterClockwise = isCounterClockwise;
      RippleSupport.changeViewColor(this, backgroundColorId, crossBackgroundColorId, factor);
      invalidate();
    }
  }

  private float crossFactor;

  public void setCrossFactor (float factor) {
    if (this.crossFactor != factor) {
      this.crossFactor = factor;
      invalidate();
    }
  }

  private Drawable replaceIcon;
  private float replaceFactor;
  private static final int REPLACE_ANIMATOR = 0;
  private int replaceOffsetLeft;
  private FactorAnimator replaceAnimator;

  public void replaceIcon (@DrawableRes int iconRes) {
    replaceIcon(iconRes, 0);
  }
  public void replaceIcon (@DrawableRes int iconRes, int replaceOffset) {
    this.replaceIcon = Drawables.get(iconRes);
    this.replaceOffsetLeft = replaceOffset;
    if (replaceAnimator == null) {
      replaceAnimator = new FactorAnimator(REPLACE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l);
    }
    if (!replaceAnimator.isAnimating()) {
      replaceAnimator.forceFactor(replaceFactor = 0f);
    } else if (replaceAnimator.getFactor() >= .5f) {
      replaceAnimator.forceFactor(replaceFactor = replaceAnimator.getFactor() - .5f);
    }
    replaceAnimator.animateTo(1f);
  }

  public void setIcon (@DrawableRes int iconRes) {
    setIcon(iconRes, 0);
  }

  public void setIcon (@DrawableRes int iconRes, int offsetLeft) {
    this.icon = Drawables.get(iconRes);
    this.offsetLeft = offsetLeft;
    invalidate();
  }

  private float hideFactor;
  private boolean isHidden;
  private FactorAnimator hideAnimator;
  private static final int HIDE_ANIMATOR_ID = 2;

  public void setIsHidden (boolean isHidden, boolean animated) {
    if (this.isHidden != isHidden) {
      this.isHidden = isHidden;
      final float toFactor = isHidden ? 1f : 0f;
      if (animated) {
        if (hideAnimator == null) {
          hideAnimator = new FactorAnimator(HIDE_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 100l, this.hideFactor);
        }
        if (toFactor == 0f && hideFactor == 1f) {
          hideAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          hideAnimator.setDuration(290l);
        } else {
          hideAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          hideAnimator.setDuration(140l);
        }
        hideAnimator.animateTo(toFactor);
      } else {
        if (hideAnimator != null) {
          hideAnimator.forceFactor(toFactor);
        }
        setHideFactor(toFactor);
      }
    }
  }

  public void setHideFactor (float factor) {
    if (this.hideFactor != factor) {
      this.hideFactor = factor;
      updateButtonScale();
      setAlpha(MathUtils.clamp(1f - factor));
    }
  }

  private float baseScale = 1f;

  public void setBaseScale (float baseScale) {
    if (this.baseScale != baseScale) {
      this.baseScale = baseScale;
      updateButtonScale();
    }
  }

  private void updateButtonScale () {
    float scale = (.6f + .4f * (1f - hideFactor) + .2f * dragFactor * (1f - hideFactor)) * baseScale;
    setScaleX(scale);
    setScaleY(scale);
  }

  private float dragFactor;
  private boolean isDragging;
  private FactorAnimator dragAnimator;
  private static final int DRAG_ANIMATOR_ID = 3;

  public void setIsDragging (boolean isDragging) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      final float toFactor = isDragging ? 1f : 0f;
      if (dragAnimator == null) {
        dragAnimator = new FactorAnimator(DRAG_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.dragFactor);
      }
      dragAnimator.animateTo(toFactor);
    }
  }

  private void setDragFactor (float factor) {
    if (this.dragFactor != factor) {
      this.dragFactor = factor;
      updateButtonScale();
    }
  }

  private void applyIcon () {
    if (replaceIcon != null) {
      icon = replaceIcon;
      offsetLeft = replaceOffsetLeft;
      replaceIcon = null;
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case REPLACE_ANIMATOR: {
        if (this.replaceFactor != factor) {
          this.replaceFactor = factor;
          if (factor > .5f) {
            applyIcon();
          }
          invalidate();
        }
        break;
      }
      case PROGRESS_ANIMATOR: {
        if (this.progressFactor != factor) {
          this.progressFactor = factor;
          progress.setAlpha(MathUtils.clamp(factor < .5f ? 0f : (factor - .5f) / .5f));
          invalidate();
        }
        break;
      }
      case HIDE_ANIMATOR_ID: {
        setHideFactor(factor);
        break;
      }
      case DRAG_ANIMATOR_ID: {
        setDragFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case REPLACE_ANIMATOR: {
        applyIcon();
        break;
      }
    }
  }

  private ProgressComponent progress;
  private FactorAnimator progressAnimator;
  private float progressFactor;
  private boolean inProgress;

  private static final int PROGRESS_ANIMATOR = 1;

  public boolean isInProgress () {
    return inProgress;
  }

  public void setInProgress (boolean inProgress) {
    if (this.inProgress == inProgress) {
      return;
    }
    this.inProgress = inProgress;
    if (progress == null) {
      progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(8f));
      progress.setUseLargerPaint();
      progress.setSlowerDurations();
      progress.forceColor(0xffffffff);
      progress.setAlpha(0f);
      progress.setBounds(0, 0, getLayoutParams().width, getLayoutParams().height);
      progress.attachToView(this);
    }
    if (progressAnimator == null) {
      progressAnimator = new FactorAnimator(PROGRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l);
    }
    progressAnimator.animateTo(inProgress ? 1f : 0f);
  }

  public void destroy () {
    if (progress != null) {
      progress.detachFromView(this);
    }
  }

  private float iconRotation;
  private FactorAnimator iconRotationAnimator;
  private static final int ROTATION_ANIMATOR = 3;

  public void setIconRotation (float rotation, boolean animated) {
    if (animated) {
      if (iconRotationAnimator == null) {
        if (this.iconRotation == rotation) {
          return;
        }
        iconRotationAnimator = new FactorAnimator(ROTATION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.iconRotation);
      }
      iconRotationAnimator.animateTo(rotation);
    } else {
      if (iconRotationAnimator != null) {
        iconRotationAnimator.forceFactor(rotation);
      }
      setIconRotationInternal(rotation);
    }
  }

  public void setFromToColor (@ColorId int fromColorId, @ColorId int toColorId, float factor) {
    RippleSupport.changeViewColor(this, fromColorId, toColorId, factor);
  }

  private void setIconRotationInternal (float rotation) {
    rotation = rotation % 360f;
    if (this.iconRotation != rotation) {
      this.iconRotation = rotation;
      invalidate();
    }
  }

  private static final float STEP_FACTOR = .85f;
  private static final float STEP_FACTOR_2 = .15f;

  private Paint iconPaint;
  private Paint getIconPaint (int color) {
    if (iconPaint == null || iconPaint.getColor() != color)
      iconPaint = Paints.createPorterDuffPaint(iconPaint, color);
    return iconPaint;
  }

  private float iconAlpha = 1f;

  public void setIconAlpha (float iconAlpha) {
    this.iconAlpha = iconAlpha;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas c) {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    int cx = (int) (width * .5f) + offsetLeft;
    int cy = (int) (height * .5f);

    boolean hasText = !StringUtils.isEmpty(bottomText);

    if (sticker != null && imageReceiver != null && gifReceiver != null) {
      imageReceiver.setBounds(cx - width / 3, cy - height / 3, cx + width / 3, cy + height / 3);
      gifReceiver.setBounds(cx - width / 3, cy - height / 3, cx + width / 3, cy + height / 3);

      final float MIN_SCALE = .82f;
      final boolean isAnimation = sticker.isAnimated();
      float originalScale = sticker.getDisplayScale();
      boolean saved = originalScale != 1f || factor != 0f;
      int restoreToCount = -1;
      if (saved) {
        restoreToCount = Views.save(c);
        float scale = originalScale * (MIN_SCALE + (1f - MIN_SCALE) * (1f - factor));
        c.scale(scale, scale, cx, cy);
      }
      if (isAnimation) {
        if (gifReceiver.needPlaceholder()) {
          //if (imageReceiver.needPlaceholder()) {
          //  imageReceiver.drawPlaceholderContour(c, contour);
          //}
          imageReceiver.draw(c);
        }
        gifReceiver.draw(c);
      } else {
        //if (imageReceiver.needPlaceholder()) {
        //  imageReceiver.drawPlaceholderContour(c, contour);
        //}
        imageReceiver.draw(c);
      }
      if (saved) {
        Views.restore(c, restoreToCount);
      }
    } else if (icon != null) {
      final int iconColor = iconColorIsId ? Theme.getColor(this.iconColor) : this.iconColor;
      final int crossIconColor = ColorUtils.fromToArgb(iconColor, crossIconColorId != 0 ? Theme.getColor(crossIconColorId) : iconColor, factor);
      final Paint bitmapPaint = getIconPaint(iconColor);
      final int sourceAlpha = bitmapPaint.getAlpha();

      if (factor == 0f) {
        final float scaleFactor = (1f - (replaceFactor <= .5f ? replaceFactor / .5f : 1f - ((replaceFactor - .5f) / .5f))) * (1f - progressFactor);
        final boolean savedScale = scaleFactor != 1f;
        if (savedScale) {
          c.save();
          final float scale = .6f + .4f * scaleFactor;
          c.scale(scale, scale, cx, cy);
          bitmapPaint.setAlpha((int) ((float) sourceAlpha * scaleFactor));
        }

        if (iconAlpha != 1f) {
          bitmapPaint.setAlpha((int) ((float) sourceAlpha * scaleFactor * iconAlpha));
        }

        final boolean savedRotation = iconRotation != 0;
        if (savedRotation) {
          c.save();
          c.rotate(iconRotation, cx, cy);
        }
        if (hasText) {
          // TODO scale text down if it does not fit
          c.drawText(bottomText, cx - bottomTextWidth / 2, cy + Screen.dp(17f), Paints.getRegularTextPaint(14f, ColorUtils.alphaColor(scaleFactor, iconColor)));
        }
        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f - (hasText ? Screen.dp(8f) : 0), bitmapPaint);
        if (savedRotation) {
          c.restore();
        }

        if (iconAlpha != 1f) {
          bitmapPaint.setAlpha(sourceAlpha);
        }

        if (savedScale) {
          bitmapPaint.setAlpha(sourceAlpha);
          c.restore();
        }

        if (progress != null && progressFactor >= .5f) {
          progress.forceColor(iconColor);
          progress.draw(c);
        }
      } else {
        final float rotation = isCounterClockwise ? (-90 - 45f) * factor : (90f + 45f) * factor;
        final boolean saved = rotation != 0f;
        if (saved) {
          c.save();
          c.rotate(rotation, cx, cy);
        }

        if (factor <= STEP_FACTOR) {
          final float factor = this.factor / STEP_FACTOR;

          bitmapPaint.setAlpha((int) ((float) sourceAlpha * (1f - factor)));
          Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, bitmapPaint);
          bitmapPaint.setAlpha(sourceAlpha);
        }

        if (factor >= STEP_FACTOR_2) {
          final float factor = (this.factor - STEP_FACTOR_2) / (1f - STEP_FACTOR_2);
          final int lineWidth = Screen.dp(1f);
          final int lineHeight = (int) ((float) Screen.dp(7f) * factor);

          final Paint paint = Paints.fillingPaint(ColorUtils.alphaColor(factor, crossIconColor));

          c.drawRect(cx - lineWidth, cy - lineHeight, cx + lineWidth, cy + lineHeight, paint);
          c.drawRect(cx - lineHeight, cy - lineWidth, cx + lineHeight, cy + lineWidth, paint);
        }

        if (saved) {
          c.restore();
        }
      }

      if (crossFactor > 0f) {
        DrawAlgorithms.drawCross(c, cx, cy - Screen.dp(6f), crossFactor, iconColor, RippleSupport.getCurrentViewColor(this), Screen.dp(27f));
      }
    }
  }

  @Nullable
  private TdApi.UnreadReaction unreadReactionRaw;
  private TGReaction reaction;
  private TGStickerObj sticker;

  public void setUnreadReaction (TdApi.UnreadReaction unreadReaction) {
    if (tdlib == null || imageReceiver == null || gifReceiver == null || reaction != null && unreadReaction != null && unreadReactionRaw != null && StringUtils.equalsOrBothEmpty(TD.makeReactionKey(unreadReaction.type), TD.makeReactionKey(unreadReactionRaw.type))) {
      return;
    }

    if (unreadReactionRaw != null) {
      tdlib.listeners().removeReactionLoadListener(TD.makeReactionKey(unreadReactionRaw.type), this);
    }

    unreadReactionRaw = unreadReaction;
    reaction = unreadReaction != null ? tdlib.getReaction(unreadReaction.type) : null;
    sticker = reaction != null ? reaction.newCenterAnimationSicker() : null;
    if (unreadReactionRaw != null && reaction == null) {
      tdlib.listeners().addReactionLoadListener(TD.makeReactionKey(unreadReactionRaw.type), this);
    }
    if (sticker != null) {
      if (sticker.getPreviewAnimation() != null && sticker.isEmojiReaction()) {
        sticker.getPreviewAnimation().setPlayOnce(true);
        sticker.getPreviewAnimation().setLooped(true);
      }
      gifReceiver.requestFile(sticker.getPreviewAnimation());
      imageReceiver.requestFile(sticker.getImage());
      if (sticker.needThemedColorFilter()) {
        this.gifReceiver.setThemedPorterDuffColorId(ColorId.iconActive);
        this.imageReceiver.setThemedPorterDuffColorId(ColorId.iconActive);
      } else {
        this.gifReceiver.disablePorterDuffColorFilter();
        this.imageReceiver.disablePorterDuffColorFilter();
      }
    } else {
      gifReceiver.clear();
      imageReceiver.clear();
    }
    invalidate();
  }

  @Override
  public void onReactionLoaded (String reactionKey) {
    UI.execute(() -> {
      if (unreadReactionRaw != null && StringUtils.equalsOrBothEmpty(reactionKey, TD.makeReactionKey(unreadReactionRaw.type))) {
        setUnreadReaction(unreadReactionRaw);
      }
    });
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (gifReceiver != null) {
      gifReceiver.attach();
    }
    if (imageReceiver != null) {
      imageReceiver.attach();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (gifReceiver != null) {
      gifReceiver.detach();
    }
    if (imageReceiver != null) {
      imageReceiver.detach();
    }
  }

  @Override
  public void performDestroy () {
    if (imageReceiver != null) {
      imageReceiver.destroy();
    }
    if (gifReceiver != null) {
      gifReceiver.destroy();
    }
    if (unreadReactionRaw != null && tdlib != null) {
      tdlib.listeners().removeReactionLoadListener(TD.makeReactionKey(unreadReactionRaw.type), this);
    }
    unreadReactionRaw = null;
    reaction = null;
    sticker = null;
  }
}
