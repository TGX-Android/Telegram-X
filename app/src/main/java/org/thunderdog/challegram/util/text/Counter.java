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
 * File created on 06/03/2018
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.DrawableProvider;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.BounceAnimator;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public final class Counter implements FactorAnimator.Target, CounterAnimator.Callback<Text>, TextColorSet  {
  public static Callback newCallback (View view) {
    return new Callback() {
      @Override
      public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
        view.invalidate();
      }

      @Override
      public boolean needAnimateChanges (Counter counter) {
        return view.getParent() != null;
      }
    };
  }

  private static final int FLAG_ALL_BOLD = 1;
  private static final int FLAG_NEED_BACKGROUND = 1 << 1;
  private static final int FLAG_OUTLINE_AFFECTS_BACKGROUND_SIZE = 1 << 2;

  public static class Builder {
    public Builder () { }

    private @Dimension(unit = Dimension.DP) float textSize = 13f;
    private @Dimension(unit = Dimension.DP) float backgroundPadding = 3f;
    private int flags = FLAG_ALL_BOLD | FLAG_NEED_BACKGROUND | FLAG_OUTLINE_AFFECTS_BACKGROUND_SIZE;
    private Callback callback;

    private int drawableRes;
    private Drawable extendedDrawable;
    private float drawableWidthDp, drawableMarginDp;
    private int drawableGravity = Gravity.NO_GRAVITY;

    private int textColorId = ColorId.badgeText;
    private int mutedTextColorId = ColorId.badgeMutedText;
    private int failedTextColorId = ColorId.badgeFailedText;
    private int outlineColorId;
    private boolean visibleIfZero;

    private TextColorSet colorSet;

    public Builder allBold (boolean allBold) {
      this.flags = BitwiseUtils.setFlag(flags, FLAG_ALL_BOLD, allBold);
      return this;
    }

    public Builder noBackground () {
      this.flags = BitwiseUtils.setFlag(flags, FLAG_NEED_BACKGROUND, false);
      return this;
    }

    public Builder outlineAffectsBackgroundSize (boolean outlineAffectsBackgroundSize) {
      this.flags = BitwiseUtils.setFlag(flags, FLAG_OUTLINE_AFFECTS_BACKGROUND_SIZE, outlineAffectsBackgroundSize);
      return this;
    }

    public Builder textSize (@Dimension(unit = Dimension.DP) float textSize) {
      this.textSize = textSize;
      return this;
    }

    public Builder callback (View view) {
      return callback(view != null ? newCallback(view) : (Callback) null);
    }

    public Builder callback (Callback callback) {
      this.callback = callback;
      return this;
    }

    public Builder drawable (int drawableRes, float drawableWidthDp, float drawableMarginDp, int gravity) {
      this.drawableRes = drawableRes;
      this.drawableWidthDp = drawableWidthDp;
      this.drawableMarginDp = drawableMarginDp;
      this.drawableGravity = gravity;
      return this;
    }

    public Builder drawable (Drawable drawable, float drawableMarginDp, int gravity) {
      this.extendedDrawable = drawable;
      this.drawableMarginDp = drawableMarginDp;
      this.drawableGravity = gravity;
      return this;
    }

    public Builder textColor (@ColorId int textColorId) {
      this.textColorId = textColorId;
      return this;
    }

    public Builder textColor (@ColorId int textColorId, @ColorId int mutedTextColorId, @ColorId int failedTextColorId) {
      this.textColorId = textColorId;
      this.mutedTextColorId = mutedTextColorId;
      this.failedTextColorId = failedTextColorId;
      return this;
    }

    public Builder colorSet (TextColorSet colorSet) {
      this.colorSet = colorSet;
      return this;
    }

    public Builder outlineColor (@ColorId int colorId) {
      this.outlineColorId = colorId;
      return this;
    }

    public Builder visibleIfZero () {
      this.visibleIfZero = true;
      return this;
    }

    public Builder backgroundPadding (@Dimension(unit = Dimension.DP) float backgroundPadding) {
      this.backgroundPadding = backgroundPadding;
      return this;
    }

    public Counter build () {
      return new Counter(textSize, callback, flags,
        textColorId, mutedTextColorId, failedTextColorId, outlineColorId,
        drawableRes, drawableWidthDp, drawableMarginDp, drawableGravity,
        colorSet, extendedDrawable, visibleIfZero, backgroundPadding
      );
    }
  }

  private final CounterAnimator<Text> counter = new CounterAnimator<>(this);
  private final BounceAnimator isVisible = new BounceAnimator(this);
  private final BoolAnimator isMuted = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
  private final BoolAnimator isFailed = new BoolAnimator(2, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
  private boolean isVisibleTarget = false;

  public interface Callback {
    void onCounterAppearanceChanged (Counter counter, boolean sizeChanged);
    default boolean needAnimateChanges (Counter counter) { return true; }
  }

  private final Callback callback;
  private final int flags;
  private final float textSize;

  private final @DrawableRes int drawableRes;
  private final Drawable extendedDrawable;
  private final float drawableWidthDp, drawableMarginDp;
  private final int drawableGravity;
  private final boolean visibleIfZero;
  private final @Dimension(unit = Dimension.DP) float backgroundPadding;

  @ColorId
  private final int textColorId, mutedTextColorId, failedTextColorId, outlineColorId;
  
  private final TextColorSet colorSet;

  private Counter (float textSize, Callback callback, int flags,
                   @ColorId int textColorId, @ColorId int mutedTextColorId, @ColorId int failedTextColorId, @ColorId int outlineColorId,
                   @DrawableRes int drawableRes, float drawableWidthDp, float drawableMarginDp, int drawableGravity,
                   @Nullable TextColorSet colorSet, Drawable counterDrawable, boolean visibleIfZero, @Dimension(unit = Dimension.DP) float backgroundPadding) {
    this.textSize = textSize;
    this.callback = callback;
    this.flags = flags;
    this.textColorId = textColorId;
    this.mutedTextColorId = mutedTextColorId;
    this.failedTextColorId = failedTextColorId;
    this.outlineColorId = outlineColorId;
    this.drawableRes = drawableRes;
    this.drawableWidthDp = drawableWidthDp;
    this.drawableMarginDp = drawableMarginDp;
    this.drawableGravity = drawableGravity;
    this.colorSet = colorSet;
    this.extendedDrawable = counterDrawable;
    this.visibleIfZero = visibleIfZero;
    this.backgroundPadding = backgroundPadding;
  }

  public int getColor (float muteFactor, int mainColorId, int mutedColorId, int failedColorId) {
    return ColorUtils.fromToArgb(
      ColorUtils.fromToArgb(
        Theme.getColor(mainColorId),
        Theme.getColor(mutedColorId),
        muteFactor
      ),
      Theme.getColor(failedColorId),
      isFailed.getFloatValue()
    );
  }

  public int getColor (float muteFactor, int mainColorId, int mutedColorId) {
    return ColorUtils.fromToArgb(
      Theme.getColor(mainColorId),
      Theme.getColor(mutedColorId),
      muteFactor
    );
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate(id == 0);
  }

  public void setMuted (boolean isMuted, boolean animated) {
    this.isMuted.setValue(isMuted, animated);
  }

  public void setCount (int count, boolean animated) {
    setCount(count, isMuted(), animated);
  }

  public void hide (boolean animated) {
    setCount(0, isMuted(), animated);
  }

  public void show (boolean animated) {
    setCount(Tdlib.CHAT_MARKED_AS_UNREAD, isMuted(), animated);
  }

  public void showHide (boolean show, boolean animated) {
    if (show) {
      show(animated);
    } else {
      hide(animated);
    }
  }

  public boolean getVisibilityTarget () {
    return isVisibleTarget;
  }

  public void setCount (long count, boolean muted, boolean animated) {
    setCount(count, muted, null, animated);
  }

  public void setCount (long count, boolean muted, @Nullable String textRepresentation, boolean animated) {
    if (animated && (callback == null || !callback.needAnimateChanges(this)))
      animated = false;
    if (animated && !UI.inUiThread())
      throw new AssertionError();
    boolean animateChanges = animated && getVisibility() > 0f;
    isMuted.setValue(muted, animateChanges);
    isFailed.setValue(count == Tdlib.CHAT_FAILED, animateChanges);
    boolean hasCounter = count > 0 || count == Tdlib.CHAT_MARKED_AS_UNREAD || count == Tdlib.CHAT_FAILED || count == Tdlib.CHAT_LOADING || (visibleIfZero && count == 0);
    if (count == Tdlib.CHAT_LOADING) {
      counter.setCounter(count, "?", animateChanges);
    } else if (count == Tdlib.CHAT_FAILED && drawableRes == 0) {
      counter.setCounter(count, "!", animateChanges);
    } else if (count > 0 || (visibleIfZero && count == 0)) {
      counter.setCounter(count, textRepresentation != null ? textRepresentation : Strings.buildCounter(count), animateChanges);
    } else {
      counter.hideCounter(animateChanges);
    }
    isVisible.setValue(hasCounter, animated);
    isVisibleTarget = hasCounter;
  }

  public void invalidate (boolean sizeChanged) {
    if (callback != null)
      callback.onCounterAppearanceChanged(this, sizeChanged);
  }

  private int getDrawableWidth () {
    if (drawableRes != 0) {
      return Screen.dp(drawableWidthDp) + Screen.dp(drawableMarginDp);
    }

    if (extendedDrawable != null) {
      return extendedDrawable.getMinimumWidth();
    }

    return 0;
  }

  private Drawable getDrawable (DrawableProvider drawableProvider, @PorterDuffColorId int drawableColorId) {
    if (drawableRes != 0) {
      return drawableProvider.getSparseDrawable(drawableRes, drawableColorId);
    }

    return extendedDrawable;
  }

  public float getWidth () {
    return DrawAlgorithms.getCounterWidth(textSize, BitwiseUtils.hasFlag(flags, FLAG_NEED_BACKGROUND), counter, getDrawableWidth(), Screen.dp(backgroundPadding));
  }

  public float getTargetWidth () {
    float targetTotalWidth = 0;
    for (ListAnimator.Entry<CounterAnimator.Part<Text>> entry : counter) {
      targetTotalWidth += entry.isAffectingList() ? entry.item.getWidth() : 0f;
    }

    return DrawAlgorithms.getCounterWidth(textSize, BitwiseUtils.hasFlag(flags, FLAG_NEED_BACKGROUND), targetTotalWidth, getDrawableWidth(), Screen.dp(backgroundPadding));
  }

  public float getScaledWidth (int addWidth) {
    return (getWidth() + addWidth) * getVisibility();
  }

  public float getScaledOrTargetWidth (int addWidth, boolean isTarget) {
    return isTarget ? (isVisibleTarget ? getTargetWidth() + addWidth : 0) : getScaledWidth(addWidth);
  }

  public float getVisibility () {
    return MathUtils.clamp(isVisible.getFloatValue());
  }

  public boolean isMuted () {
    return isMuted.getValue();
  }

  public float getMuteFactor () {
    return isMuted.getFloatValue();
  }

  public void draw (Canvas c, float cx, float cy, int gravity, float alpha) {
    draw(c, cx, cy, gravity, alpha, null, 0);
  }

  public void draw (Canvas c, float cx, float cy, int gravity, float alpha, @Nullable RectF outDrawRect) {
    draw(c, cx, cy, gravity, alpha, alpha, alpha, null, 0, outDrawRect);
  }

  public void draw (Canvas c, float cx, float cy, int gravity, float alpha, DrawableProvider drawableProvider, @PorterDuffColorId int drawableColorId) {
    draw(c, cx, cy, gravity, alpha, alpha, alpha, drawableProvider, drawableColorId);
  }

  public void draw (Canvas c, float cx, float cy, int gravity, float alpha, DrawableProvider drawableProvider, @PorterDuffColorId int drawableColorId, @Nullable RectF outputDrawRect) {
    draw(c, cx, cy, gravity, alpha, alpha, alpha, drawableProvider, drawableColorId, outputDrawRect);
  }

  public void draw (Canvas c, float cx, float cy, int gravity, float textAlpha, float backgroundAlpha, float drawableAlpha, DrawableProvider drawableProvider, @PorterDuffColorId int drawableColorId) {
    draw(c, cx, cy, gravity, textAlpha, backgroundAlpha, drawableAlpha, drawableProvider, drawableColorId, null);
  }

  public void draw (Canvas c, float cx, float cy, int gravity, float textAlpha, float backgroundAlpha, float drawableAlpha, DrawableProvider drawableProvider, @PorterDuffColorId int drawableColorId, @Nullable RectF outputDrawRect) {
    boolean needBackground = BitwiseUtils.hasFlag(flags, FLAG_NEED_BACKGROUND);
    boolean outlineAffectsBackgroundSize = needBackground && BitwiseUtils.hasFlag(flags, FLAG_OUTLINE_AFFECTS_BACKGROUND_SIZE);
    if (textAlpha * getVisibility() > 0f || (needBackground && backgroundAlpha * getVisibility() > 0f)) {
      Drawable drawable = getDrawable(drawableProvider, drawableColorId);
      DrawAlgorithms.drawCounter(c, cx, cy, gravity, counter, textSize, textAlpha * getVisibility(), needBackground, outlineAffectsBackgroundSize, Screen.dp(backgroundPadding), this, drawable, drawableGravity, drawableColorId, Screen.dp(drawableMarginDp), backgroundAlpha * getVisibility(), drawableAlpha * getVisibility(), isVisible.getFloatValue(), outputDrawRect);
    }
  }

  // interfaces

  private float lastWidth;

  @Override
  public void onItemsChanged (CounterAnimator<?> animator) {
    float width = getWidth();
    boolean changed = lastWidth != width;
    lastWidth = width;
    invalidate(changed);
  }

  @Override
  public Text onCreateTextDrawable (String text) {
    return new Text.Builder(text, Integer.MAX_VALUE, Paints.robotoStyleProvider(textSize), this).noSpacing().allBold(BitwiseUtils.hasFlag(flags, FLAG_ALL_BOLD)).build();
  }

  @Override
  public int defaultTextColor () {
    return colorSet != null ? colorSet.defaultTextColor() : getColor(getMuteFactor(), textColorId, mutedTextColorId, failedTextColorId);
  }

  @Override
  public int backgroundColor (boolean isPressed) {
    return colorSet != null ? colorSet.backgroundColor(isPressed) : getColor(getMuteFactor(), ColorId.badge, ColorId.badgeMuted, ColorId.badgeFailed);
  }

  @Override
  public int outlineColor (boolean isPressed) {
    return colorSet != null ? colorSet.outlineColor(isPressed) : (outlineColorId != 0 ? Theme.getColor(outlineColorId) : 0);
  }

  @Override
  public int outlineColorId (boolean isPressed) {
    return 0;
  }

  @Override
  public int backgroundColorId (boolean isPressed) {
    return 0;
  }
}
