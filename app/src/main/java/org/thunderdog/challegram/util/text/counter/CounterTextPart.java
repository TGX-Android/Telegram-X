package org.thunderdog.challegram.util.text.counter;

import android.graphics.Canvas;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.util.text.TextColorSet;

import me.vkryl.android.animator.CounterAnimator;

public interface CounterTextPart extends CounterAnimator.TextDrawable {
  void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme, @FloatRange(from = 0f, to = 1f) float alpha);
}
