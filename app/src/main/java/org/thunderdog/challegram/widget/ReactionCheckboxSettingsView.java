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
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class ReactionCheckboxSettingsView extends LinearLayout implements ThemeInvalidateListener, Destroyable {
  private final StickerSmallView stickerSmallView;
  private final TextView captionTextView;
  private boolean checked;
  private float checkedFactor;
  private TGReaction reaction;

  private TGStickerObj activateAnimationSticker;

  private final float lineSize;
  private final Drawable premiumDrawable;

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
    stickerSmallView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 55, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
    stickerSmallView.setIsSuggestion();
    addView(stickerSmallView);

    captionTextView = new TextView(context);
    captionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    captionTextView.setTextSize(13);
    captionTextView.setMaxLines(2);
    addView(captionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 15));

    premiumDrawable = Drawables.get(R.drawable.baseline_star_20);

    recolor();

    checked = true;
    checkedFactor = 1f;
  }

  public StickerSmallView getStickerSmallView () {
    return stickerSmallView;
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
      activateAnimationSticker.getPreviewAnimation().setPlayOnce(true);
      stickerSmallView.setPadding(0);
    } else {
      stickerSmallView.setPadding(Screen.dp(12));
    }
    stickerSmallView.setSticker(activateAnimationSticker);
    setCaptionText(reaction.reaction.title);
  }

  public void toggleChecked () {
    setChecked(!checked, true);
  }

  public void setChecked (boolean checked, boolean animated) {
    if (checked == this.checked) {
      return;
    }

    this.checked = checked;

    if (!animated) {
      setCheckedFactor(checked ? 1f : 0f);
      return;
    }

    ValueAnimator obj;
    final float startFactor = getCheckedFactor();
    obj = AnimatorUtils.simpleValueAnimator();
    if (checked) {
      final float diffFactor = 1f - startFactor;
      obj.addUpdateListener(animation -> setCheckedFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    } else {
      obj.addUpdateListener(animation -> setCheckedFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
    }
    obj.setDuration(165l);
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.start();

    if (checked && animated) {
      if (activateAnimationSticker.getPreviewAnimation() != null) {
        activateAnimationSticker.getPreviewAnimation().setLooped(false);
      }
      stickerSmallView.invalidate();
    }
  }

  public boolean getChecked () {
    return checked;
  }

  public void setCheckedFactor (float factor) {
    if (this.checkedFactor != factor) {
      this.checkedFactor = factor;
      stickerSmallView.setAlpha(this.checkedFactor / 2f + 0.5f);
      captionTextView.setAlpha(this.checkedFactor / 2f + 0.5f);
      invalidate();
    }
  }

  public float getCheckedFactor () {
    return this.checkedFactor;
  }

  public TGStickerObj getSticker () {
    return stickerSmallView.getSticker();
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    super.dispatchDraw(c);

    c.save();
    float cx = getWidth() / 2f + Screen.dp(12);
    float cy = Screen.dp(50);
    float r1 = Screen.dp(8.6f);
    float r2 = Screen.dp(6.6f);

    c.drawCircle(cx, cy, r1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor, Theme.backgroundColor())));
    c.drawCircle(cx, cy, r2, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor, Theme.radioFillingColor())));

    float x1 = cx - Screen.dp(1);
    float y1 = cy + Screen.dp(3.5f);
    float w2 = Screen.dp(8.6f) * checkedFactor;
    float h1 = Screen.dp(4.66f) * checkedFactor;

    c.rotate(-45f, x1, y1);
    c.drawRect(x1, y1 - h1, x1 + lineSize, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor, Theme.backgroundColor())));
    c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor, Theme.backgroundColor())));

    c.restore();

    if (!reaction.canSend) {
      Paint paint = PorterDuffPaint.get(R.id.theme_color_bubbleOut_file, 1);
      Drawables.draw(c, premiumDrawable, cx - Screen.dp(10), cy - Screen.dp(10), paint);
    }
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
}
