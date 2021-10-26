package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.PorterDuffThemeColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
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
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.unit.BitwiseUtils;

/**
 * Date: 3/6/18
 * Author: default
 */

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

  public static class Builder {
    public Builder () { }

    private float textSize = 13f;
    private int flags = FLAG_ALL_BOLD | FLAG_NEED_BACKGROUND;
    private Callback callback;

    private int drawableRes;
    private float drawableWidthDp, drawableMarginDp;
    private int drawableGravity = Gravity.NO_GRAVITY;

    private int textColorId = R.id.theme_color_badgeText;
    private int mutedTextColorId = R.id.theme_color_badgeMutedText;
    private int failedTextColorId = R.id.theme_color_badgeFailedText;
    private int outlineColorId;

    private TextColorSet colorSet;

    public Builder allBold (boolean allBold) {
      this.flags = BitwiseUtils.setFlag(flags, FLAG_ALL_BOLD, allBold);
      return this;
    }

    public Builder noBackground () {
      this.flags = BitwiseUtils.setFlag(flags, FLAG_NEED_BACKGROUND, false);
      return this;
    }

    public Builder textSize (float textSize) {
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

    public Builder textColor (@ThemeColorId int textColorId) {
      this.textColorId = textColorId;
      return this;
    }

    public Builder textColor (@ThemeColorId int textColorId, @ThemeColorId int mutedTextColorId, @ThemeColorId int failedTextColorId) {
      this.textColorId = textColorId;
      this.mutedTextColorId = mutedTextColorId;
      this.failedTextColorId = failedTextColorId;
      return this;
    }

    public Builder colorSet (TextColorSet colorSet) {
      this.colorSet = colorSet;
      return this;
    }

    public Builder outlineColor (@ThemeColorId int colorId) {
      this.outlineColorId = colorId;
      return this;
    }

    public Counter build () {
      return new Counter(textSize, callback, flags,
        textColorId, mutedTextColorId, failedTextColorId, outlineColorId,
        drawableRes, drawableWidthDp, drawableMarginDp, drawableGravity,
        colorSet
      );
    }
  }

  private final CounterAnimator<Text> counter = new CounterAnimator<>(this);
  private final BounceAnimator isVisible = new BounceAnimator(this);
  private final BoolAnimator isMuted = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
  private final BoolAnimator isFailed = new BoolAnimator(2, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);

  public interface Callback {
    void onCounterAppearanceChanged (Counter counter, boolean sizeChanged);
    default boolean needAnimateChanges (Counter counter) { return true; }
  }

  private final Callback callback;
  private final int flags;
  private final float textSize;

  private final @DrawableRes int drawableRes;
  private final float drawableWidthDp, drawableMarginDp;
  private final int drawableGravity;

  @ThemeColorId
  private final int textColorId, mutedTextColorId, failedTextColorId, outlineColorId;
  
  private final TextColorSet colorSet;

  private Counter (float textSize, Callback callback, int flags,
                   @ThemeColorId int textColorId, @ThemeColorId int mutedTextColorId, @ThemeColorId int failedTextColorId, @ThemeColorId int outlineColorId,
                   @DrawableRes int drawableRes, float drawableWidthDp, float drawableMarginDp, int drawableGravity,
                   @Nullable TextColorSet colorSet) {
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
  }

  private int getColor (float muteFactor, int mainColorId, int mutedColorId, int failedColorId) {
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

  public void setCount (int count, boolean muted, boolean animated) {
    if (animated && (callback == null || !callback.needAnimateChanges(this)))
      animated = false;
    if (animated && !UI.inUiThread())
      throw new AssertionError();
    boolean animateChanges = animated && getVisibility() > 0f;
    isMuted.setValue(muted, animateChanges);
    isFailed.setValue(count == Tdlib.CHAT_FAILED, animateChanges);
    boolean hasCounter = count > 0 || count == Tdlib.CHAT_MARKED_AS_UNREAD || count == Tdlib.CHAT_FAILED;
    if (count == Tdlib.CHAT_FAILED && drawableRes == 0) {
      counter.setCounter(count, "!", animateChanges);
    } else if (count > 0) {
      counter.setCounter(count, Strings.buildCounter(count), animateChanges);
    } else {
      counter.hideCounter(animateChanges);
    }
    isVisible.setValue(hasCounter, animated);
  }

  public void invalidate (boolean sizeChanged) {
    if (callback != null)
      callback.onCounterAppearanceChanged(this, sizeChanged);
  }

  private float getWidth () {
    return DrawAlgorithms.getCounterWidth(textSize, BitwiseUtils.getFlag(flags, FLAG_NEED_BACKGROUND), counter, drawableRes != 0 ? Screen.dp(drawableWidthDp) + Screen.dp(drawableMarginDp) : 0);
  }

  public float getScaledWidth (int addWidth) {
    return (getWidth() + addWidth) * getVisibility();
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

  public void draw (Canvas c, float cx, float cy, int gravity, float alpha, DrawableProvider drawableProvider, @PorterDuffThemeColorId int drawableColorId) {
    if (alpha * getVisibility() > 0f) {
      Drawable drawable = drawableRes != 0 ? drawableProvider.getSparseDrawable(drawableRes, drawableColorId) : null;
      DrawAlgorithms.drawCounter(c, cx, cy, gravity, counter, textSize, BitwiseUtils.getFlag(flags, FLAG_NEED_BACKGROUND), this, drawable, drawableGravity, drawableColorId, Screen.dp(drawableMarginDp), alpha * getVisibility(), isVisible.getFloatValue());
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
    return new Text.Builder(text, Integer.MAX_VALUE, Paints.robotoStyleProvider(textSize), this).noSpacing().allBold(BitwiseUtils.getFlag(flags, FLAG_ALL_BOLD)).build();
  }

  @Override
  public int defaultTextColor () {
    return colorSet != null ? colorSet.defaultTextColor() : getColor(getMuteFactor(), textColorId, mutedTextColorId, failedTextColorId);
  }

  @Override
  public int backgroundColor (boolean isPressed) {
    return colorSet != null ? colorSet.backgroundColor(isPressed) : getColor(getMuteFactor(), R.id.theme_color_badge, R.id.theme_color_badgeMuted, R.id.theme_color_badgeFailed);
  }

  @Override
  public int outlineColor (boolean isPressed) {
    return outlineColorId != 0 ? Theme.getColor(outlineColorId) : 0;
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
