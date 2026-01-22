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

import static java.lang.Math.toRadians;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import androidx.media3.common.OverlaySettings;
import androidx.media3.effect.CanvasOverlay;
import androidx.media3.effect.StaticOverlaySettings;

/* package */ final class ClockOverlay extends CanvasOverlay {
  private static final int CLOCK_COLOR = Color.WHITE;

  private static final int DIAL_SIZE = 200;
  private static final float DIAL_WIDTH = 3.f;
  private static final float NEEDLE_WIDTH = 3.f;
  private static final int NEEDLE_LENGTH = DIAL_SIZE / 2 - 20;
  private static final int CENTRE_X = DIAL_SIZE / 2;
  private static final int CENTRE_Y = DIAL_SIZE / 2;
  private static final int DIAL_INSET = 5;
  private static final RectF DIAL_BOUND =
      new RectF(
          /* left= */ DIAL_INSET,
          /* top= */ DIAL_INSET,
          /* right= */ DIAL_SIZE - DIAL_INSET,
          /* bottom= */ DIAL_SIZE - DIAL_INSET);
  private static final int HUB_SIZE = 5;

  private static final float BOTTOM_RIGHT_ANCHOR_X = 1.f;
  private static final float BOTTOM_RIGHT_ANCHOR_Y = -1.f;
  private static final float ANCHOR_INSET_X = 0.1f;
  private static final float ANCHOR_INSET_Y = -0.1f;

  private final Paint dialPaint;
  private final Paint needlePaint;
  private final Paint hubPaint;

  public ClockOverlay() {
    super(/* useInputFrameSize= */ false);
    setCanvasSize(/* width= */ DIAL_SIZE, /* height= */ DIAL_SIZE);

    dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dialPaint.setStyle(Paint.Style.STROKE);
    dialPaint.setStrokeWidth(DIAL_WIDTH);
    dialPaint.setColor(CLOCK_COLOR);

    needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    needlePaint.setStrokeWidth(NEEDLE_WIDTH);
    needlePaint.setColor(CLOCK_COLOR);

    hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    hubPaint.setColor(CLOCK_COLOR);
  }

  @Override
  public void onDraw(Canvas canvas, long presentationTimeUs) {
    // Clears the canvas
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

    // Draw the dial
    canvas.drawArc(
        DIAL_BOUND, /* startAngle= */ 0, /* sweepAngle= */ 360, /* useCenter= */ false, dialPaint);

    // Draw the needle
    float angle = 6 * presentationTimeUs / 1_000_000.f - 90;
    double radians = toRadians(angle);

    float startX = CENTRE_X - (float) (10 * Math.cos(radians));
    float startY = CENTRE_Y - (float) (10 * Math.sin(radians));
    float endX = CENTRE_X + (float) (NEEDLE_LENGTH * Math.cos(radians));
    float endY = CENTRE_Y + (float) (NEEDLE_LENGTH * Math.sin(radians));

    canvas.drawLine(startX, startY, endX, endY, needlePaint);

    // Draw a small hub at the center
    canvas.drawCircle(CENTRE_X, CENTRE_Y, HUB_SIZE, hubPaint);
  }

  @Override
  public OverlaySettings getOverlaySettings(long presentationTimeUs) {
    return new StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(
            BOTTOM_RIGHT_ANCHOR_X - ANCHOR_INSET_X, BOTTOM_RIGHT_ANCHOR_Y - ANCHOR_INSET_Y)
        .setOverlayFrameAnchor(BOTTOM_RIGHT_ANCHOR_X, BOTTOM_RIGHT_ANCHOR_Y)
        .build();
  }
}
