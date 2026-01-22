/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.demo.transformer;

import android.animation.FloatEvaluator;
import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Pair;
import android.view.animation.LinearInterpolator;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DrawableOverlay;
import androidx.media3.effect.TextureOverlay;
import java.util.concurrent.CountDownLatch;

/**
 * An animated {@link TextureOverlay} using {@link android.animation}.
 *
 * <p>The rotation is controlled by a simple {@link ValueAnimator}, while the position is controlled
 * by key frames.
 */
public class AnimatedLogoOverlay extends DrawableOverlay {

  private static final long ROTATION_PERIOD_MS = 2_000;
  private static final long POSITION_PERIOD_MS = 5_000;
  private static final float POSITION_X_BOUND = 0.8f;
  private static final float POSITION_Y_BOUND = 0.7f;

  private final Drawable logo;
  private final AnimatedOverlaySettings overlaySettings;

  public AnimatedLogoOverlay(Context context) {
    try {
      logo = context.getPackageManager().getApplicationIcon(context.getPackageName());
    } catch (PackageManager.NameNotFoundException e) {
      throw new IllegalStateException(e);
    }
    logo.setBounds(
        /* left= */ 0, /* top= */ 0, logo.getIntrinsicWidth(), logo.getIntrinsicHeight());

    ValueAnimator rotationAnimator = ValueAnimator.ofFloat(0, 360);
    rotationAnimator.setRepeatMode(ValueAnimator.RESTART);
    rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
    rotationAnimator.setDuration(ROTATION_PERIOD_MS);
    // Rotate the logo with a constant angular velocity.
    rotationAnimator.setInterpolator(new LinearInterpolator());

    Keyframe[] keyFrames = new Keyframe[5];
    keyFrames[0] =
        Keyframe.ofObject(/* fraction= */ 0f, Pair.create(-POSITION_X_BOUND, -POSITION_Y_BOUND));
    keyFrames[2] =
        Keyframe.ofObject(/* fraction= */ 0.5f, Pair.create(POSITION_X_BOUND, POSITION_Y_BOUND));
    keyFrames[1] =
        Keyframe.ofObject(/* fraction= */ 0.25f, Pair.create(-POSITION_X_BOUND, POSITION_Y_BOUND));
    keyFrames[3] =
        Keyframe.ofObject(/* fraction= */ 0.75f, Pair.create(POSITION_X_BOUND, -POSITION_Y_BOUND));
    keyFrames[4] =
        Keyframe.ofObject(/* fraction= */ 1f, Pair.create(-POSITION_X_BOUND, -POSITION_Y_BOUND));
    PropertyValuesHolder positionValuesHolder =
        PropertyValuesHolder.ofKeyframe("position", keyFrames);

    ValueAnimator positionAnimator = ValueAnimator.ofPropertyValuesHolder(positionValuesHolder);
    // The position can also be animated using separate animators for x and y, the purpose of
    // PairEvaluator is to use one animator for both x and y.
    positionAnimator.setEvaluator(new AnimatedOverlaySettings.PairEvaluator());
    positionAnimator.setRepeatMode(ValueAnimator.RESTART);
    positionAnimator.setRepeatCount(ValueAnimator.INFINITE);
    positionAnimator.setDuration(POSITION_PERIOD_MS);

    overlaySettings = new AnimatedOverlaySettings(rotationAnimator, positionAnimator);
  }

  @Override
  public Drawable getDrawable(long presentationTimeUs) {
    return logo;
  }

  @Override
  public OverlaySettings getOverlaySettings(long presentationTimeUs) {
    overlaySettings.setCurrentPresentationTimeUs(presentationTimeUs);
    return overlaySettings;
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    overlaySettings.stopAnimation();
  }

  private static class AnimatedOverlaySettings implements OverlaySettings {
    private final ValueAnimator rotationAnimator;
    private final ValueAnimator positionAnimator;
    private final Handler mainThreadHandler;

    private boolean started;

    public AnimatedOverlaySettings(ValueAnimator rotationAnimator, ValueAnimator positionAnimator) {
      this.rotationAnimator = rotationAnimator;
      this.positionAnimator = positionAnimator;
      mainThreadHandler = new Handler(Util.getCurrentOrMainLooper());
    }

    public void setCurrentPresentationTimeUs(long presentationTimeUs) {
      // Sets the animation time to the video presentation time, so the animation is presentation
      // time based.
      rotationAnimator.setCurrentPlayTime(presentationTimeUs / 1000);
      positionAnimator.setCurrentPlayTime(presentationTimeUs / 1000);
    }

    @Override
    public float getRotationDegrees() {
      maybeStartAnimator();
      return (float) rotationAnimator.getAnimatedValue();
    }

    @Override
    public Pair<Float, Float> getBackgroundFrameAnchor() {
      maybeStartAnimator();
      return (Pair<Float, Float>) positionAnimator.getAnimatedValue();
    }

    public void stopAnimation() {
      mainThreadHandler.post(
          () -> {
            rotationAnimator.cancel();
            positionAnimator.cancel();
          });
    }

    private void maybeStartAnimator() {
      if (!started) {
        CountDownLatch latch = new CountDownLatch(1);
        mainThreadHandler.post(
            () -> {
              rotationAnimator.start();
              positionAnimator.start();
              latch.countDown();
            });
        try {
          // Block until the animators are actually started, or they'll return null values.
          latch.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
        started = true;
      }
    }

    /** An {@link TypeEvaluator} to animate position in the form of {@link Pair} of floats. */
    private static class PairEvaluator implements TypeEvaluator<Pair<Float, Float>> {
      private final FloatEvaluator floatEvaluator;

      private PairEvaluator() {
        floatEvaluator = new FloatEvaluator();
      }

      @Override
      public Pair<Float, Float> evaluate(
          float fraction, Pair<Float, Float> startValue, Pair<Float, Float> endValue) {
        return Pair.create(
            floatEvaluator.evaluate(fraction, startValue.first, endValue.first),
            floatEvaluator.evaluate(fraction, startValue.second, endValue.second));
      }
    }
  }
}
