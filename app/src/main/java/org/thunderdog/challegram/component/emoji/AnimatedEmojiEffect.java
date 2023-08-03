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
 * File created on 05/06/2023
 */
package org.thunderdog.challegram.component.emoji;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;

import kotlin.random.Random;
import me.vkryl.core.MathUtils;

public class AnimatedEmojiEffect {

  public AnimatedEmojiDrawable animatedEmojiDrawable;
  Rect bounds = new Rect();

  ArrayList<Particle> particles = new ArrayList<>();
  View parentView;

  long startTime;
  boolean longAnimation;
  boolean firsDraw = true;

  private AnimatedEmojiEffect(AnimatedEmojiDrawable animatedEmojiDrawable, boolean longAnimation) {
    this.animatedEmojiDrawable = animatedEmojiDrawable;
    this.longAnimation = longAnimation;
    startTime = System.currentTimeMillis();
  }

  public static AnimatedEmojiEffect createFrom(AnimatedEmojiDrawable animatedEmojiDrawable, boolean longAnimation) {
    return new AnimatedEmojiEffect(animatedEmojiDrawable, longAnimation);
  }

  public void setBounds(int l, int t, int r, int b) {
    bounds.set(l, t, r, b);
  }

  long lastGenerateTime;

  public void draw(Canvas canvas) {
    if (!longAnimation) {
      if (firsDraw) {
        for (int i = 0; i < 7; i++) {
          Particle particle = new Particle();
          particle.generate();
          particles.add(particle);
        }
      }
    } else {
      long currentTime = System.currentTimeMillis();
      if (particles.size() < 12 && (currentTime - startTime < 1500) && currentTime - startTime > 200) {
        if (currentTime - lastGenerateTime > 50 && Random.Default.nextInt() % 6 == 0) {
          Particle particle = new Particle();
          particle.generate();
          particles.add(particle);
          lastGenerateTime = currentTime;
        }
      }
    }

    for (int i = 0; i < particles.size(); i++) {
      particles.get(i).draw(canvas);
      if (particles.get(i).progress >= 1f) {
        particles.remove(i);
        i--;
      }
    }
    if (parentView != null) {
      parentView.invalidate();
    }
    firsDraw = false;
  }

  public boolean done() {
    return System.currentTimeMillis() - startTime > 2500;
  }

  public void setView(View view) {
    animatedEmojiDrawable.attach();
    parentView = view;
  }

  public void removeView() {
    animatedEmojiDrawable.detach();
  }

  private class Particle {
    float fromX, fromY;
    float toX;
    float toY1;
    float toY2;
    float fromSize;
    float toSize;
    float progress;
    long duration;

    boolean mirror;
    float randomRotation;

    public void generate() {
      progress = 0;
      float bestDistance = 0;
      float bestX = randX();
      float bestY = randY();
      for (int k = 0; k < 20; k++) {
        float randX = randX();
        float randY = randY();
        float minDistance = Integer.MAX_VALUE;
        for (int j = 0; j < particles.size(); j++) {
          float rx = particles.get(j).toX - randX;
          float ry = particles.get(j).toY1 - randY;

          float distance = rx * rx + ry * ry;

          if (distance < minDistance) {
            minDistance = distance;
          }
        }
        if (minDistance > bestDistance) {
          bestDistance = minDistance;
          bestX = randX;
          bestY = randY;
        }
      }

      float pivotX = longAnimation ? 0.8f : 0.5f;
      toX = bestX;
      if (toX > bounds.width() * pivotX) {
        fromX = bounds.width() * pivotX;// + bounds.width() * 0.1f * (Math.abs(Random.Default.nextInt() % 100) / 100f);
      } else {
        fromX = bounds.width() * pivotX;// - bounds.width() * 0.3f * (Math.abs(Random.Default.nextInt() % 100) / 100f);
        if (toX > fromX) {
          toX = fromX - 0.1f;
        }
      }

      fromY = bounds.height() * 0.45f + bounds.height() * 0.1f * (Math.abs(Random.Default.nextInt() % 100) / 100f);



      if (longAnimation) {
        fromSize = bounds.width() * 0.05f + bounds.width() * 0.1f * (Math.abs(Random.Default.nextInt() % 100) / 100f);
        toSize = fromSize * (1.5f + 1.5f * (Math.abs(Random.Default.nextInt() % 100) / 100f));
        toY1 = fromSize / 2f + (bounds.height() * 0.1f * (Math.abs(Random.Default.nextInt() % 100) / 100f));
        toY2 = bounds.height() + fromSize;
        duration = 1000 + Math.abs(Random.Default.nextInt() % 600);
      } else {
        fromSize = bounds.width() * 0.05f + bounds.width() * 0.1f * (Math.abs(Random.Default.nextInt() % 100) / 100f);
        toSize = fromSize * (1.5f + 0.5f * (Math.abs(Random.Default.nextInt() % 100) / 100f));
        toY1 = bestY;
        toY2 = toY1 + bounds.height();
        duration = 1800;
      }
      duration /= 1.75f;
      mirror = Random.Default.nextBoolean();
      randomRotation = 20 * ((Random.Default.nextInt() % 100) / 100f);
    }

    private float randY() {
      return (bounds.height() * 0.5f * (Math.abs(Random.Default.nextInt() % 100) / 100f));
    }

    private long randDuration() {
      return 1000 + Math.abs(Random.Default.nextInt() % 900);
    }

    private float randX() {
      if (longAnimation) {
        return bounds.width() * -0.25f + bounds.width() * 1.5f * (Math.abs(Random.Default.nextInt() % 100) / 100f);
      } else {
        return bounds.width() * (Math.abs(Random.Default.nextInt() % 100) / 100f);
      }
    }

    public void draw(Canvas canvas) {
      progress += (float) Math.min(40, 1000f / Screen.refreshRate()) / duration;
      progress = MathUtils.clamp(progress);
      float progressInternal = CubicBezierInterpolator.EASE_OUT.getInterpolation(progress);
      float cx = MathUtils.fromTo(fromX, toX, progressInternal);
      float cy;
      float k = 0.3f;
      float k1 = 1f - k;
      if (progress < k) {
        cy = MathUtils.fromTo(fromY, toY1, CubicBezierInterpolator.EASE_OUT.getInterpolation(progress / k));
      } else {
        cy = MathUtils.fromTo(toY1, toY2, CubicBezierInterpolator.EASE_IN.getInterpolation((progress - k) / k1));
      }

      float size = MathUtils.fromTo(fromSize, toSize, progressInternal);
      float outAlpha = 1f;
      if (!longAnimation) {
        float bottomBound = bounds.height() * 0.8f;
        if (cy > bottomBound) {
          outAlpha = 1f - MathUtils.clamp((cy - bottomBound) / Screen.dp(16));
        }
      }
      float sizeHalf = size / 2f * outAlpha;
      final int saveCount = Views.save(canvas);
      if (mirror) {
        canvas.scale(-1f, 1f, cx, cy);
      }
      canvas.rotate(randomRotation, cx, cy);
      animatedEmojiDrawable.setAlpha((int) (255 * outAlpha * MathUtils.clamp(progress / 0.2f)));
      animatedEmojiDrawable.setBounds((int) (cx - sizeHalf), (int) (cy - sizeHalf), (int) (cx + sizeHalf), (int) (cy + sizeHalf));
      animatedEmojiDrawable.draw(canvas);
      animatedEmojiDrawable.setAlpha(255);
      Views.restore(canvas, saveCount);
    }
  }
}