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
import android.graphics.PointF;
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
import java.util.List;

import kotlin.random.Random;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class GiftParticlesDrawable extends Drawable {
  public interface ParticleValidator {
    boolean isValidPosition (float x, float y);
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

  private static final int GRID_SIZE = 25;

  private void setSize (int width, int height) {
    if (this.width == width && this.height == height) {
      return;
    }
    this.width = width;
    this.height = height;

    List<PointF> points = poissonDiskSampling(Screen.dp(GRID_SIZE), width, height, 10);
    if (particleValidator != null) {
      points = ArrayUtils.filter(points, p -> particleValidator.isValidPosition(p.x, p.y));
    }

    ArrayList<Particle> particles = new ArrayList<>(points.size());
    for (PointF pointF: points) {
      particles.add(new Particle(MathUtils.random(0, 3), particleColors[MathUtils.random(0, 5)], pointF.x, pointF.y, 0.75f + Random.Default.nextFloat() * 0.5f, Random.Default.nextFloat() * 360f));
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
    public final float x;
    public final float y;

    public Particle (int type, int color, float x, float y, float scale, float angle) {
      this.type = type;
      this.color = color;
      this.x = x;
      this.y = y;
      this.scale = scale;
      this.angle = angle;
    }
  }
  
  /* * */

  static boolean isValidPoint(PointF[][] grid, int width, int height, float cellsize,
                       int gwidth, int gheight,
                       PointF p, float radius) {
    /* Make sure the point is on the screen */
    final int gp = Screen.dp(15) / 2;
    if ((p.x < gp) || (p.x >= (width - gp)) || (p.y < gp) || (p.y >= (height - gp)))
      return false;

    /* Check neighboring eight cells */
    int xindex = (int)Math.floor(p.x / cellsize);
    int yindex = (int)Math.floor(p.y / cellsize);
    int i0 = Math.max(xindex - 1, 0);
    int i1 = Math.min(xindex + 1, gwidth - 1);
    int j0 = Math.max(yindex - 1, 0);
    int j1 = Math.min(yindex + 1, gheight - 1);

    for (int i = i0; i <= i1; i++)
      for (int j = j0; j <= j1; j++)
        if (grid[i][j] != null)
          if (MathUtils.distance(grid[i][j].x, grid[i][j].y, p.x, p.y) < radius)
            return false;

    /* If we get here, return true */
    return true;
  }

  static void insertPoint(PointF[][] grid, float cellsize, PointF point) {
    int xindex = (int)Math.floor(point.x / cellsize);
    int yindex = (int)Math.floor(point.y / cellsize);
    grid[xindex][yindex] = point;
  }

  static ArrayList<PointF> poissonDiskSampling(float radius, int width, int height, int k) {
    int N = 2;
    /* The final set of points to return */
    ArrayList<PointF> points = new ArrayList<PointF>();
    /* The currently "active" set of points */
    ArrayList<PointF> active = new ArrayList<PointF>();
    /* Initial point p0 */
    PointF p0 = new PointF(MathUtils.random(0, width), MathUtils.random(0, height));
    PointF[][] grid;
    float cellsize = (float) Math.floor(radius/Math.sqrt(N));

    /* Figure out no. of cells in the grid for our canvas */
    int ncells_width = (int)Math.ceil(width/cellsize) + 1;
    int ncells_height = (int)Math.ceil(height/cellsize) + 1;

    /* Allocate the grid an initialize all elements to null */
    grid = new PointF[ncells_width][ncells_height];
    for (int i = 0; i < ncells_width; i++)
      for (int j = 0; j < ncells_height; j++)
        grid[i][j] = null;

    insertPoint(grid, cellsize, p0);
    points.add(p0);
    active.add(p0);

    while (active.size() > 0) {
      int random_index = MathUtils.random(0, active.size() - 1);
      PointF p = active.get(random_index);

      boolean found = false;
      for (int tries = 0; tries < k; tries++) {
        float theta = MathUtils.random(0, 360);
        float new_radius = MathUtils.random((int)radius, (int)(2*radius));
        float pnewx = (float) (p.x + new_radius * Math.cos(Math.toRadians(theta)));
        float pnewy = (float) (p.y + new_radius * Math.sin(Math.toRadians(theta)));
        PointF pnew = new PointF(pnewx, pnewy);

        if (!isValidPoint(grid, width, height, cellsize,
          ncells_width, ncells_height,
          pnew, radius))
          continue;

        points.add(pnew);
        insertPoint(grid, cellsize, pnew);
        active.add(pnew);
        found = true;
        break;
      }

      /* If no point was found after k tries, remove p */
      if (!found)
        active.remove(random_index);
    }

    return points;
  }
}
