/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/11/2026
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.util.text.Text;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animatorx.FloatAnimator;
import me.vkryl.android.animatorx.FloatAnimatorListener;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

final class QuoteHighlight {
  private static final long SHRINK_START_MS = 500L;
  private static final long SHRINK_END_MS = 750L;
  private static final long FADE_START_MS = 2500L;
  private static final long FADE_END_MS = 2900L;

  private final TGMessage message;
  private final FloatAnimator animator = new FloatAnimator(
    FADE_END_MS,
    AnimatorUtils.LINEAR_INTERPOLATOR,
    0f,
    new FloatAnimatorListener() {
      @Override
      public void onValueChange (float newValue) {
        applyProgress(newValue);
      }

      @Override
      public void onValueChangeFinished (float finalValue, boolean byAnimationEnd) {
        if (finalValue == 1f) {
          cancel(true);
        }
      }
    }
  );
  private final Path path = new Path();

  private @Nullable List<RectF> quoteRects;
  private @Nullable String quote;
  private int position;
  private float shrinkFactor;
  private float fadeFactor;
  private boolean needsRectsRefresh;

  QuoteHighlight (TGMessage message) {
    this.message = message;
  }

  boolean isActive () {
    return quoteRects != null;
  }

  boolean start (String quote, int position) {
    cancel(false);
    List<RectF> newRects = findRects(quote, position);
    if (newRects == null) {
      return false;
    }
    this.quote = quote;
    this.position = position;
    quoteRects = newRects;
    needsRectsRefresh = true;
    message.cancelHighlightRevoke();
    animator.setAnimatedValue(1f);
    return true;
  }

  void cancel (boolean restoreHighlight) {
    animator.stopAnimation();
    animator.setForcedValue(0f);
    quote = null;
    quoteRects = null;
    shrinkFactor = fadeFactor = 0f;
    if (restoreHighlight) {
      message.setHighlight(0f);
    }
  }

  @Nullable Rect refreshBounds () {
    List<RectF> newRects = quote != null ? findRects(quote, position) : null;
    if (newRects == null || newRects.isEmpty()) {
      return null;
    }
    quoteRects = newRects;
    int top = Integer.MAX_VALUE;
    int bottom = Integer.MIN_VALUE;
    for (RectF rect : newRects) {
      top = Math.min(top, (int) rect.top);
      bottom = Math.max(bottom, (int) Math.ceil(rect.bottom));
    }
    return new Rect(0, top, 0, bottom);
  }

  void draw (View view, Canvas c, boolean foreground) {
    if (quoteRects == null || fadeFactor == 1f) {
      return;
    }
    float bubbleFactor = message.useBubbles() ? shrinkFactor : 0f;
    float alpha = (foreground ? bubbleFactor : 1f - bubbleFactor) * (1f - fadeFactor);
    if (alpha == 0f) {
      return;
    }
    float top = message.findTopEdge();
    float bottom = message.findBottomEdge();
    path.rewind();
    for (RectF target : quoteRects) {
      path.addRect(
        MathUtils.fromTo(0f, target.left, shrinkFactor),
        MathUtils.fromTo(top, target.top, shrinkFactor),
        MathUtils.fromTo(view.getMeasuredWidth(), target.right, shrinkFactor),
        MathUtils.fromTo(bottom, target.bottom, shrinkFactor),
        Path.Direction.CW
      );
    }
    int color = ColorUtils.alphaColor(alpha, message.getSelectionColor(1f));
    c.drawPath(path, Paints.fillingPaint(color));
  }

  private void applyProgress (float progress) {
    float elapsedMs = progress * FADE_END_MS;
    shrinkFactor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(
      MathUtils.clamp((elapsedMs - SHRINK_START_MS) / (SHRINK_END_MS - SHRINK_START_MS), 0f, 1f)
    );
    fadeFactor = MathUtils.clamp((elapsedMs - FADE_START_MS) / (FADE_END_MS - FADE_START_MS), 0f, 1f);
    if (shrinkFactor != 0f && needsRectsRefresh) {
      needsRectsRefresh = false;
      refreshBounds();
    }
    message.invalidateParentOrSelf(false);
  }

  private @Nullable List<RectF> findRects (String quote, int position) {
    Text text = message.getTextSelectionLayout();
    String messageText = text != null ? text.getText() : null;
    if (messageText == null || quote.isEmpty()) {
      return null;
    }
    int nextIndex = messageText.indexOf(quote, position);
    int previousIndex = messageText.lastIndexOf(quote, position);
    boolean isPreviousCloser = previousIndex != -1 &&
      position - previousIndex <= nextIndex - position;
    int index = nextIndex == -1 || isPreviousCloser ? previousIndex : nextIndex;
    List<RectF> localRects = new ArrayList<>();
    if (index == -1 || !text.getRangeRects(index, index + quote.length(), localRects)) {
      return null;
    }
    Rect textBounds = new Rect();
    text.toRect(textBounds);
    for (RectF rect : localRects) {
      rect.offset(textBounds.left + message.getTextSelectionOffsetX(), textBounds.top);
    }
    return localRects;
  }
}
