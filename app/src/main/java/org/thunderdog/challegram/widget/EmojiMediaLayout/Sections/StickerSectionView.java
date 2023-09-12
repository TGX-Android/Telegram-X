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
 * File created on 19/08/2023
 */
package org.thunderdog.challegram.widget.EmojiMediaLayout.Sections;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class StickerSectionView extends View implements Destroyable, FactorAnimator.Target {
  private static final int WIDTH = 44;
  private static final int PADDING = 10;

  private final ImageReceiver receiver;
  private final GifReceiver gifReceiver;

  private float selectionFactor;

  public StickerSectionView (Context context) {
    super(context);
    receiver = new ImageReceiver(this, 0);
    gifReceiver = new GifReceiver(this);
  }

  public void attach () {
    receiver.attach();
    gifReceiver.attach();
  }

  public void detach () {
    receiver.detach();
    gifReceiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    gifReceiver.destroy();
  }

  private TGStickerSetInfo info;
  private Path contour;

  public void setStickerSet (@NonNull TGStickerSetInfo info) {
    this.info = info;
    this.contour = info.getPreviewContour(Math.min(receiver.getWidth(), receiver.getHeight()));
    receiver.requestFile(info.getPreviewImage());
    gifReceiver.requestFile(info.getPreviewAnimation());
  }

  private FactorAnimator animator;

  public void setSelectionFactor (float factor, boolean animated) {
    if (animated && this.selectionFactor != factor) {
      if (animator == null) {
        animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, selectionFactor);
      }
      animator.animateTo(factor);
    } else {
      if (animator != null) {
        animator.forceFactor(factor);
      }
      setSelectionFactor(factor);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == 0) {
      setSelectionFactor(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  private void setSelectionFactor (float factor) {
    if (this.selectionFactor != factor) {
      this.selectionFactor = factor;
      invalidate();
    }
  }

  public @Nullable TGStickerSetInfo getStickerSet () {
    return info;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.makeMeasureSpec(Screen.dp(WIDTH), MeasureSpec.EXACTLY), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    setBounds();
  }

  private void setBounds () {
    int padding = Screen.dp(PADDING);
    int width = receiver.getWidth(), height = receiver.getHeight();
    receiver.setBounds(padding, padding, getMeasuredWidth() - padding, getMeasuredHeight() - padding);
    gifReceiver.setBounds(padding, padding, getMeasuredWidth() - padding, getMeasuredHeight() - padding);
    if (info != null && (width != receiver.getWidth() || height != receiver.getHeight())) {
      this.contour = info.getPreviewContour(Math.min(receiver.getWidth(), receiver.getHeight()));
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;

    final boolean saved = selectionFactor != 0f;
    if (saved) {
      final int selectionColor = Theme.chatSelectionColor();
      final int selectionAlpha = Color.alpha(selectionColor);
      int color = ColorUtils.color((int) ((float) selectionAlpha * selectionFactor), selectionColor);
      int radius = Screen.dp(18f) - (int) ((float) Screen.dp(4f) * (1f - selectionFactor));

      c.drawCircle(cx, cy, radius, Paints.fillingPaint(color));
      c.save();
      float scale = .85f + .15f * (1f - selectionFactor);
      c.scale(scale, scale, cx, cy);
    }

    DrawAlgorithms.drawRepaintedSticker(c, info, gifReceiver, receiver, contour);

    if (saved) {
      c.restore();
    }
  }
}
