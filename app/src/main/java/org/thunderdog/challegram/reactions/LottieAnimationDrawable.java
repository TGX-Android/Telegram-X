package org.thunderdog.challegram.reactions;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LottieAnimationDrawable extends Drawable implements Animatable {
  private Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private LottieAnimation anim;
  // [0] is drawn onto the canvas, [1] is rendered into in a background thread
  private Bitmap[] bitmaps = new Bitmap[2];
  private int frame;
  private long startTime;
  private boolean running;
  private boolean drawing;
  private boolean loop;
  private long frameDelay;
  private double frameRate;
  private Runnable onEnd;

  public LottieAnimationDrawable (LottieAnimation anim, int width, int height) {
    this.anim = anim;
    for (int i = 0; i < 2; i++) {
      bitmaps[i] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }
    frameDelay = Math.round(1000 / anim.getFrameRate());
    frameRate = anim.getFrameRate();
    getNextFrame(); // draw first frame synchronously
  }

  @Override
  public void start () {
    if (running)
      return;
    startTime = SystemClock.uptimeMillis() - Math.round(frame * (1000.0 / frameRate));
    running = true;
    maybeAdvance();
  }

  @Override
  public void stop () {
    running = false;
  }

  @Override
  public boolean isRunning () {
    return running;
  }

  @Override
  public void draw (@NonNull Canvas canvas) {
    synchronized (this) {
      canvas.drawBitmap(bitmaps[0], null, getBounds(), paint);
    }
    maybeAdvance();
  }

  @Override
  public void setAlpha (int alpha) {

  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {

  }

  @Override
  public int getOpacity () {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getIntrinsicWidth () {
    return bitmaps[0].getWidth();
  }

  @Override
  public int getIntrinsicHeight () {
    return bitmaps[0].getHeight();
  }

  public void setLoop (boolean loop) {
    this.loop = loop;
  }

  private synchronized void swapBuffers () {
    Bitmap tmp = bitmaps[0];
    bitmaps[0] = bitmaps[1];
    bitmaps[1] = tmp;
  }

  private void maybeAdvance () {
    double _newFrame = Math.min((SystemClock.uptimeMillis() - startTime) / (1000.0 / frameRate), anim.getFrameCount() - 1);
    int newFrame = (int) Math.round(_newFrame);
    if (newFrame != frame && running) {
      frame = newFrame;
      if (frame == anim.getFrameCount() - 1) {
        if (loop) {
          frame = 0;
        } else {
          running = false;
          if (onEnd != null)
            onEnd.run();
        }
      }
      if (!drawing) {
        drawing = true;
        LottieAnimationThreadPool.submit(this::getNextFrame);
      }
    }
    invalidateSelf();
  }

  private void getNextFrame () {
    anim.getFrame(bitmaps[1], frame);
    swapBuffers();
    drawing = false;
  }

  public void setOnEnd (Runnable onEnd) {
    this.onEnd = onEnd;
  }

  public void setFrame (int frame) {
    this.frame = frame;
    if (!drawing) {
      drawing = true;
      LottieAnimationThreadPool.submit(this::getNextFrame);
    }
  }

  public int getTotalFrames () {
    return (int) anim.getFrameCount();
  }
}
