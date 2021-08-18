package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

/**
 * Date: 7/13/18
 * Author: default
 */
class PlayPauseButton extends View implements FactorAnimator.Target {
  private final Path playPausePath = new Path();
  private float playPauseDrawFactor = -1f;
  private final BoolAnimator playPauseAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 160l);

  public PlayPauseButton (Context context) {
    super(context);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public void setIsPlaying (boolean isPlaying, boolean animated) {
    playPauseAnimator.setValue(isPlaying, animated);
  }

  @Override
  protected void onDraw (Canvas c) {
    DrawAlgorithms.drawPlayPause(c, getMeasuredWidth() / 2, getMeasuredHeight() / 2, Screen.dp(12f), playPausePath, playPauseDrawFactor, playPauseDrawFactor = playPauseAnimator.getFloatValue(), 1f, 0xffffffff);
  }
}
