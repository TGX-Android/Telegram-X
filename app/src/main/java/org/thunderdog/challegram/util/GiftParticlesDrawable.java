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
 * File created on 05/01/2023
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

import kotlin.random.Random;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class GiftParticlesDrawable extends Drawable {
  public interface ParticleValidator {
    boolean isValidPosition (int x, int y);
  }

  private @Nullable ParticleValidator particleValidator;
  private Particle[] particles;
  private int width;
  private int height;

  public GiftParticlesDrawable () {
    this(null, 0, 0);
  }

  public GiftParticlesDrawable (ParticleValidator validator) {
    this(validator, 0, 0);
  }

  public GiftParticlesDrawable (ParticleValidator validator, int width, int height) {
    if (particle1 == null || particle2 == null) {
      particle1 = Drawables.get(R.drawable.giveaway_particle_1);
      particle2 = Drawables.get(R.drawable.giveaway_particle_2);
    }
    setParticleValidator(validator);
    setSize(width, height);
  }

  public void setParticleValidator (@Nullable ParticleValidator particleValidator) {
    this.particleValidator = particleValidator;
  }

  @Override
  protected void onBoundsChange (@NonNull Rect bounds) {
    super.onBoundsChange(bounds);
    setSize(bounds.width(), bounds.height());
  }

  private static final int GRID_SIZE = 35;

  private void setSize (int width, int height) {
    if (this.width == width && this.height == height) {
      return;
    }

    final int gridSection = Screen.dp(GRID_SIZE);
    int gridSectionsX = (int) Math.floor((float) width / gridSection);
    int gridSectionsY = (int) Math.floor((float) height / gridSection);
    int gridStartX = (width - gridSection * gridSectionsX) / 2;
    int gridStartY = (height - gridSection * gridSectionsY) / 2;

    this.width = width;
    this.height = height;

    ArrayList<Particle> particles = new ArrayList<>(gridSectionsX * gridSectionsY);
    for (int x = 0; x < gridSectionsX; x++) {
      for (int y = 0; y < gridSectionsY; y++) {
        int sx = gridStartX + gridSection * x;
        int sy = gridStartY + gridSection * y;
        int ex = sx + gridSection;
        int ey = sy + gridSection;
        int px = MathUtils.random(sx, ex);
        int py = MathUtils.random(sy, ey);

        if (particleValidator == null || particleValidator.isValidPosition(px, py)) {
          particles.add(new Particle(MathUtils.random(0, 3), particleColors[MathUtils.random(0, 5)], px, py, 0.75f + Random.Default.nextFloat() * 0.5f, Random.Default.nextFloat() * 360f));
        }
      }
    }
    this.particles = particles.toArray(new Particle[0]);
  }

  @Override
  public void draw (@NonNull Canvas c) {
    final float radius = Screen.dp(3.5f);
    for (Particle particle : particles) {
      c.save();
      c.scale(1.5f * particle.scale, 1.5f * particle.scale, particle.x, particle.y);
      c.rotate(particle.angle, particle.x, particle.y);

      final int color = ColorUtils.alphaColor(0.4f, Theme.getColor(particle.color));
      if (particle.type == 0) {
        c.drawCircle(particle.x, particle.y, radius, Paints.fillingPaint(color));
      } else if (particle.type == 1) {
        c.drawRect(particle.x - radius, particle.y - radius, particle.x + radius, particle.y + radius, Paints.fillingPaint(color));
      } else if (particle.type == 2) {
        Drawables.drawCentered(c, particle1, particle.x, particle.y, Paints.getPorterDuffPaint(color));
      } else if (particle.type == 3) {
        Drawables.drawCentered(c, particle2, particle.x, particle.y, Paints.getPorterDuffPaint(color));
      }

      c.restore();
    }
  }

  @Override
  public void setAlpha (int alpha) { }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) { }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }

  private static Drawable particle1;
  private static Drawable particle2;

  private static final int[] particleColors = new int[] {ColorId.confettiGreen, ColorId.confettiBlue, ColorId.confettiYellow, ColorId.confettiRed, ColorId.confettiCyan, ColorId.confettiPurple};

  private static class Particle {
    public final int type;
    public final int color;
    public final float scale;
    public final float angle;
    public final int x;
    public final int y;

    public Particle (int type, int color, int x, int y, float scale, float angle) {
      this.type = type;
      this.color = color;
      this.x = x;
      this.y = y;
      this.scale = scale;
      this.angle = angle;
    }
  }
}
