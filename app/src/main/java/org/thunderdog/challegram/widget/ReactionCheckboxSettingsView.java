/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class ReactionCheckboxSettingsView extends LinearLayout implements ThemeInvalidateListener, FactorAnimator.Target, Destroyable, TooltipOverlayView.LocationProvider {
  private final StickerSmallView stickerSmallView;
  private final GifReceiver gifReceiver;
  private final TextView captionTextView;
  private final Counter counter;
  private final BoolAnimator checkedFactor;
  private int number = -2;
  private TGReaction reaction;

  private TGStickerObj activateAnimationSticker;

  private final float lineSize;

  public ReactionCheckboxSettingsView (Context context) {
    this(context, null, 0);
  }

  public ReactionCheckboxSettingsView (Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ReactionCheckboxSettingsView (Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setOrientation(LinearLayout.VERTICAL);
    setWillNotDraw(false);

    lineSize = Screen.dp(1.5f);

    stickerSmallView = new StickerSmallView(context, Screen.dp(0));
    stickerSmallView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 3));
    stickerSmallView.setIsSuggestion();
    addView(stickerSmallView);

    gifReceiver = new GifReceiver(this);

    captionTextView = new TextView(context);
    captionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    captionTextView.setTextSize(11);
    captionTextView.setMaxLines(2);
    captionTextView.setEllipsize(TextUtils.TruncateAt.END);
    addView(captionTextView, LayoutHelper.createLinear(74, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

    counter = new Counter.Builder()
      .callback(this)
      .noBackground()
      .allBold(true)
      .textSize(9f)
      .build();

    recolor();

    checkedFactor = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165L);
    checkedFactor.setValue(true, false);
  }

  public StickerSmallView getStickerSmallView () {
    return stickerSmallView;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    final int bound = Screen.dp(2f);
    outRect.set(
      getMeasuredWidth() / 2 - bound,
      Screen.dp(20) - bound,
      getMeasuredWidth() / 2 + bound,
      Screen.dp(20) + bound);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    if (stickerSmallView.onTouchEvent(event)) {
      return true;
    }
    return super.onTouchEvent(event);
  }

  public void recolor () {
    captionTextView.setTextColor(Theme.getColor(R.id.theme_color_text));
    captionTextView.setHighlightColor(Theme.getColor(R.id.theme_color_text));
  }

  public void attach () {
    stickerSmallView.attach();
  }

  public void detach () {
    stickerSmallView.detach();
  }

  public void setCaptionText (CharSequence text) {
    captionTextView.setText(text);
  }

  public void init (Tdlib tdlib) {
    stickerSmallView.init(tdlib);
  }

  public void setReaction (TGReaction reaction) {
    this.reaction = reaction;
    activateAnimationSticker = reaction.centerAnimationSicker();
    if (activateAnimationSticker.getPreviewAnimation() != null) {
      if (!activateAnimationSticker.isCustomReaction()) {
        activateAnimationSticker.getPreviewAnimation().setPlayOnce(true);
      }
    }
    stickerSmallView.setSticker(activateAnimationSticker);
    setCaptionText(reaction.getTitle());
  }

  public void setChecked (boolean checked, boolean animated) {
    this.setNumber(checked ? 0 : -1, animated);
  }

  public void setNumber (int number, boolean animated) {
    boolean checked = number >= 0;
    if (number == this.number) {
      return;
    }

    this.number = number;

    if (!animated) {
      counter.setCount(Math.max(0, number), false);
      checkedFactor.setValue(checked, false);
      return;
    }

    counter.setCount(Math.max(0, number), true);
    checkedFactor.setValue(checked, true);

    if (checked && animated) {
      if (activateAnimationSticker.getPreviewAnimation() != null) {
        activateAnimationSticker.getPreviewAnimation().setLooped(false);
      }
      stickerSmallView.invalidate();
    }
  }

  public TGStickerObj getSticker () {
    return stickerSmallView.getSticker();
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    super.dispatchDraw(c);

    c.save();

    float cx = getWidth() / 2f + Screen.dp(10);
    float cy = Screen.dp(47 + 6);
    float r1 = Screen.dp(7.5f);
    float r2 = Screen.dp(6f);

    c.drawCircle(cx, cy, r1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.fillingColor())));
    c.drawCircle(cx, cy, r2, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioFillingColor())));

    if (counter.getVisibility() > 0f) {
      counter.draw(c, cx, cy, Gravity.CENTER, counter.getVisibility());
    } else {
      float x1 = cx - Screen.dp(1);
      float y1 = cy + Screen.dp(3.5f);
      float w2 = Screen.dp(8f) * checkedFactor.getFloatValue();
      float h1 = Screen.dp(4f) * checkedFactor.getFloatValue();

      c.rotate(-45f, x1, y1);
      c.drawRect(x1, y1 - h1, x1 + lineSize, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioCheckColor())));
      c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioCheckColor())));
    }

    c.restore();
  }

  @Override
  public void performDestroy () {
    stickerSmallView.performDestroy();
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    recolor();
    invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    stickerSmallView.setAlpha(factor / 2f + 0.5f);
    captionTextView.setAlpha(factor / 2f + 0.5f);
    invalidate();
  }
}
