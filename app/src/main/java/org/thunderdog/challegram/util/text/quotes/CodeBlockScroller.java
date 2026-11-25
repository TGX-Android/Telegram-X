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
 * File created on 26/10/2026
 */

package org.thunderdog.challegram.util.text.quotes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.view.ViewConfiguration;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.tool.Views;

/**
 * Fling and overscroll of a code block, like in HorizontalScrollView
 */
class CodeBlockScroller {
  // Part of fling that releases the stretch, as in scroll views of the platform
  private static final float FLING_DESTRETCH_FACTOR = 4f;

  private final OverScroller scroller;
  private final EdgeEffect leftEdge, rightEdge;
  private final int overflingDistance;
  // Stretch applies to the render node it's drawn into, so the block has its own
  private @Nullable CodeBlockRenderNode renderNode;
  // A render node adds up stretches of all its recordings, and only views clear them, so the last
  // stretch is undone by the same one in the opposite direction
  private final EdgeEffect leftUndo, rightUndo;

  CodeBlockScroller (Context context) {
    this.scroller = new OverScroller(context);
    this.leftEdge = new EdgeEffect(context);
    this.rightEdge = new EdgeEffect(context);
    this.leftUndo = new EdgeEffect(context);
    this.rightUndo = new EdgeEffect(context);
    this.overflingDistance = ViewConfiguration.get(context).getScaledOverflingDistance();
  }

  boolean catchScroll (float displacement) {
    final boolean wasAnimating = isFlinging() || hasEdgeEffect();
    scroller.forceFinished(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (!leftEdge.isFinished()) {
        leftEdge.onPullDistance(0f, 1f - displacement);
      }
      if (!rightEdge.isFinished()) {
        rightEdge.onPullDistance(0f, displacement);
      }
    }
    return wasAnimating;
  }

  boolean isFlinging () {
    return !scroller.isFinished();
  }

  int drag (int scrollX, int maxScrollX, int deltaX, float displacement, int width) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // Drag in the opposite direction releases the stretch first
      int consumed = 0;
      if (deltaX < 0 && rightEdge.getDistance() != 0f) {
        final float distance = rightEdge.onPullDistance((float) deltaX / width, displacement);
        consumed = Math.round(width * distance);
      } else if (deltaX > 0 && leftEdge.getDistance() != 0f) {
        final float distance = leftEdge.onPullDistance((float) -deltaX / width, 1f - displacement);
        consumed = Math.round(-width * distance);
      }
      deltaX -= consumed;
    }
    final int pulledToX = scrollX + deltaX;
    if (deltaX != 0) {
      if (pulledToX < 0) {
        pull(leftEdge, (float) -deltaX / width, 1f - displacement);
        if (!rightEdge.isFinished()) {
          rightEdge.onRelease();
        }
      } else if (pulledToX > maxScrollX) {
        pull(rightEdge, (float) deltaX / width, displacement);
        if (!leftEdge.isFinished()) {
          leftEdge.onRelease();
        }
      }
    }
    return Math.max(0, Math.min(pulledToX, maxScrollX));
  }

  private static void pull (EdgeEffect edge, float deltaDistance, float displacement) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      edge.onPullDistance(deltaDistance, displacement);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      edge.onPull(deltaDistance, displacement);
    } else {
      edge.onPull(deltaDistance);
    }
  }

  void release (int scrollX, int maxScrollX, int velocityX, int width) {
    if (velocityX != 0) {
      fling(scrollX, maxScrollX, velocityX, width);
    } else {
      scroller.springBack(scrollX, 0, 0, maxScrollX, 0, 0);
    }
    leftEdge.onRelease();
    rightEdge.onRelease();
  }

  private void fling (int scrollX, int maxScrollX, int velocityX, int width) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      absorbFling(scrollX, maxScrollX, velocityX, width)) {
      return;
    }
    scroller.fling(scrollX, 0, velocityX, 0, 0, maxScrollX, 0, 0, width / 2, 0);
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private boolean absorbFling (int scrollX, int maxScrollX, int velocityX, int width) {
    // Fling into the stretch, or not long enough to release it, is absorbed by it
    if (scrollX == 0 && !leftEdge.isFinished()) {
      if (shouldAbsorb(leftEdge, -velocityX, width)) {
        leftEdge.onAbsorb(-velocityX);
        return true;
      }
    } else if (scrollX == maxScrollX && !rightEdge.isFinished()) {
      if (shouldAbsorb(rightEdge, velocityX, width)) {
        rightEdge.onAbsorb(velocityX);
        return true;
      }
    }
    return false;
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private boolean shouldAbsorb (EdgeEffect edge, int velocity, int width) {
    if (velocity > 0) {
      return true;
    }
    // OverScroller tells the distance of a fling only by a fling, which stops at once
    scroller.fling(0, 0, -velocity, 0, 0, Integer.MAX_VALUE, 0, 0);
    final int flingDistance = scroller.getFinalX();
    scroller.forceFinished(true);
    return flingDistance < edge.getDistance() * width;
  }

  int computeScroll (int scrollX, int maxScrollX, int width) {
    if (!scroller.computeScrollOffset()) {
      return scrollX;
    }
    final int x = scroller.getCurrX();
    final int deltaX = consumeFlingInStretch(scrollX, maxScrollX, x - scrollX, width);
    if (deltaX == 0) {
      return scrollX;
    }
    // Fling goes a little beyond the edges and returns
    final int newScrollX =
      Math.max(-overflingDistance, Math.min(scrollX + deltaX, maxScrollX + overflingDistance));
    if (newScrollX != scrollX + deltaX && !scroller.isFinished()) {
      scroller.springBack(newScrollX, 0, 0, maxScrollX, 0, 0);
    }
    if (x < 0 && scrollX >= 0) {
      leftEdge.onAbsorb((int) scroller.getCurrVelocity());
    } else if (x > maxScrollX && scrollX <= maxScrollX) {
      rightEdge.onAbsorb((int) scroller.getCurrVelocity());
    }
    return newScrollX;
  }

  private int consumeFlingInStretch (int scrollX, int maxScrollX, int unconsumed, int width) {
    // Fling away from a stretched edge releases the stretch first
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollX >= 0 && scrollX <= maxScrollX) {
      if (unconsumed > 0 && leftEdge.getDistance() != 0f) {
        final float deltaDistance = -unconsumed * FLING_DESTRETCH_FACTOR / width;
        final float distance = leftEdge.onPullDistance(deltaDistance, .5f);
        final int consumed = Math.round(-width / FLING_DESTRETCH_FACTOR * distance);
        if (consumed != unconsumed) {
          leftEdge.finish();
        }
        return unconsumed - consumed;
      }
      if (unconsumed < 0 && rightEdge.getDistance() != 0f) {
        final float deltaDistance = unconsumed * FLING_DESTRETCH_FACTOR / width;
        final float distance = rightEdge.onPullDistance(deltaDistance, .5f);
        final int consumed = Math.round(width / FLING_DESTRETCH_FACTOR * distance);
        if (consumed != unconsumed) {
          rightEdge.finish();
        }
        return unconsumed - consumed;
      }
    }
    return unconsumed;
  }

  boolean hasEdgeEffect () {
    return !leftEdge.isFinished() || !rightEdge.isFinished();
  }

  Canvas beginStretch (Canvas c, RectF bounds) {
    // Since Android 12 overscroll stretches the render node of a hardware canvas, so the content
    // of the block is drawn into its own
    final boolean canStretch =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && c.isHardwareAccelerated();
    if (canStretch && hasEdgeEffect()) {
      if (renderNode == null) {
        renderNode = new CodeBlockRenderNode();
      }
      return renderNode.beginRecording(bounds);
    }
    return c;
  }

  boolean drawEdges (Canvas c, Canvas content, RectF bounds) {
    final int width = (int) bounds.width();
    final int height = (int) bounds.height();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && content != c && renderNode != null) {
      // Stretch doesn't depend on position, and at the origin of the render node the undone one is
      // exactly opposite
      final int saveCount = Views.save(content);
      content.translate((int) bounds.left, (int) bounds.top);
      boolean needInvalidate = stretch(content, leftEdge, leftUndo, 270, height, width);
      needInvalidate |= stretch(content, rightEdge, rightUndo, 90, height, width);
      Views.restore(content, saveCount);
      renderNode.endRecording(c);
      return needInvalidate;
    }
    boolean needInvalidate = false;
    if (!leftEdge.isFinished()) {
      needInvalidate = drawEdge(c, leftEdge, bounds.left, bounds.bottom, 270, height, width);
    }
    if (!rightEdge.isFinished()) {
      needInvalidate |= drawEdge(c, rightEdge, bounds.right, bounds.top, 90, height, width);
    }
    return needInvalidate;
  }

  private static boolean drawEdge (
    Canvas c,
    EdgeEffect edge,
    float x,
    float y,
    float degrees,
    int width,
    int height
  ) {
    final int saveCount = Views.save(c);
    c.translate(x, y);
    c.rotate(degrees);
    edge.setSize(width, height);
    final boolean needInvalidate = edge.draw(c);
    Views.restore(c, saveCount);
    return needInvalidate;
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private static boolean stretch (
    Canvas c,
    EdgeEffect edge,
    EdgeEffect undo,
    float degrees,
    int edgeWidth,
    int maxStretch
  ) {
    if (undo.getDistance() != 0f) {
      drawEdge(c, undo, 0, 0, degrees - 180, edgeWidth, maxStretch);
    }
    boolean needInvalidate = false;
    if (!edge.isFinished()) {
      needInvalidate = drawEdge(c, edge, 0, 0, degrees, edgeWidth, maxStretch);
    }
    undo.finish();
    if (edge.getDistance() != 0f) {
      undo.onPullDistance(edge.getDistance(), .5f);
    }
    return needInvalidate;
  }
}
