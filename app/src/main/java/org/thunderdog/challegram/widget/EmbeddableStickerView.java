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

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class EmbeddableStickerView extends LinearLayout implements ThemeInvalidateListener, Destroyable {
  // TODO tim merge to one view and manage animators to one factor producer
  private final StickerSmallView stickerEffectView;
  private final StickerSmallView stickerSmallView;
  private final TextView captionTextView;

  private final BoolAnimator isChecked = new BoolAnimator(0, (id, factor, fraction, callee) -> invalidate(),
          AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  public EmbeddableStickerView (Context context) {
    this(context, null, 0, 0, false);
  }

  public EmbeddableStickerView (Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0, 0, false);
  }

  public EmbeddableStickerView (Context context, int stickerSize, boolean withEffect) {
    this(context, null, 0, stickerSize, withEffect);
  }

  public EmbeddableStickerView (Context context, @Nullable AttributeSet attrs, int defStyleAttr, int stickerSize, boolean withEffect) {
    super(context, attrs, defStyleAttr);
    setOrientation(LinearLayout.VERTICAL);
    if (stickerSize == 0) {
      stickerSize = 128;
    }

    if (withEffect) {
      stickerEffectView = new StickerSmallView(context) {
        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
          return false;
        }
      };
    } else {
      stickerEffectView = null;
    }

    stickerSmallView = new StickerSmallView(context) {
      @Override
      public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
      }
    };

    if (stickerEffectView != null) {
      FrameLayoutFix frameLayoutFix = new FrameLayoutFix(context);
      FrameLayout.LayoutParams params = LayoutHelper.createFrame(stickerSize, stickerSize, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0);
      params.gravity = Gravity.CENTER;
      frameLayoutFix.addView(stickerSmallView, params);
      frameLayoutFix.addView(stickerEffectView, params);
      addView(frameLayoutFix);
    } else {
      stickerSmallView.setLayoutParams(LayoutHelper.createLinear(stickerSize, stickerSize, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
      addView(stickerSmallView);
    }

    captionTextView = new TextView(context);
    captionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
    captionTextView.setTextSize(14);
    captionTextView.setMovementMethod(LinkMovementMethod.getInstance());
    captionTextView.setHighlightColor(Theme.textLinkHighlightColor());
    if (withEffect) {
      captionTextView.setMaxLines(2);
      captionTextView.setEllipsize(TextUtils.TruncateAt.END);
    }
    addView(captionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 16, 8, 16, 8));

    recolor();
  }

  public void recolor () {
    captionTextView.setTextColor(Theme.textDecentColor());
    captionTextView.setHighlightColor(Theme.textLinkHighlightColor());
  }

  public void attach () {
    stickerSmallView.attach();
    if (stickerEffectView != null) {
      stickerEffectView.attach();
    }
  }

  public void detach () {
    stickerSmallView.detach();
    if (stickerEffectView != null) {
      stickerEffectView.detach();
    }
  }

  public void setCaptionText (CharSequence text) {
    captionTextView.setText(text);
  }

  public CharSequence getCaptionText () { return captionTextView.getText(); }

  public void init (Tdlib tdlib) {
    stickerSmallView.init(tdlib);
  }

  public void setSticker (TGStickerObj tgStickerObj) {
    if (tgStickerObj != null && !tgStickerObj.isEmpty() && tgStickerObj.isAnimated()) {
      tgStickerObj.getPreviewAnimation().setPlayOnce(true);
    }
    stickerSmallView.setSticker(tgStickerObj);
  }

  public void setStickerEffect (TGStickerObj tgStickerObj) {
    if (stickerEffectView != null) {
      if (tgStickerObj != null && !tgStickerObj.isEmpty() && tgStickerObj.isAnimated()) {
        tgStickerObj.getPreviewAnimation().setPlayOnce(true);
      }
      stickerEffectView.setSticker(null);
      stickerEffectView.setSticker(tgStickerObj);
    }
  }

  public void setChecked(boolean isChecked, boolean animate) {
    this.isChecked.setValue(isChecked, animate);
  }

  public void toggle(boolean animate) {
    this.isChecked.setValue(!this.isChecked.getValue(), animate);
  }

  public boolean isChecked() {
    return this.isChecked.getValue();
  }

  @Override
  public void performDestroy () {
    stickerSmallView.performDestroy();
    if (stickerEffectView != null) {
      stickerEffectView.performDestroy();
    }
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    recolor();
    invalidate();
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    int x = (int) (stickerSmallView.getX() + stickerSmallView.getWidth() - Screen.dp(10f));
    int y = stickerSmallView.getHeight() - Screen.dp(2f);
    SimplestCheckBox.draw(canvas, x, y, isChecked.getFloatValue(), null, null, Theme.checkFillingColor(), Theme.checkCheckColor(), false, 0f, Screen.dp(8f), Screen.dp(11f));
  }
}
