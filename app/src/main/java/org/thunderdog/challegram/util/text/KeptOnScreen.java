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
 * File created on 19/11/2026
 */
package org.thunderdog.challegram.util.text;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Ways of what a change of a text moves, kept on the screen: what leaves or enters it over an edge
 * ends or starts just beyond that edge, and the rest of its way passes unseen. Nothing is kept when
 * anything crosses the whole screen
 */
public final class KeptOnScreen {
  private final @Nullable int[] visible;
  private final int margin, line;
  private final float startFactor;
  // From the top of the visible part
  private float[] starts = new float[64], ends = new float[64];
  private int count;
  private boolean hasCrossing;
  private HashMap<Long, Float> keptEnds, keptStarts;

  KeptOnScreen (@Nullable int[] visible, int margin, int line, float startFactor) {
    // visible: of Text.findVisiblePart at startFactor and at the end; null keeps nothing
    this.visible = visible;
    this.margin = margin;
    this.line = line;
    this.startFactor = startFactor;
  }

  private float bottomAtStart () {
    return visible[1] - visible[0] + margin;
  }

  private float bottomAtEnd () {
    return visible[3] - visible[2] + margin;
  }

  private static long key (float start, float end) {
    return ((long) Float.floatToIntBits(start) << 32) | (Float.floatToIntBits(end) & 0xffffffffL);
  }

  public void add (float startY, float toY) {
    // startY: where it's drawn at startFactor, toY: where the change takes it
    if (visible == null) {
      return;
    }
    if (count == starts.length) {
      starts = Arrays.copyOf(starts, count * 2);
      ends = Arrays.copyOf(ends, count * 2);
    }
    final float start = startY - visible[0], end = toY - visible[2];
    // Also what nearly crosses, which a group at each edge would keep less than a line apart
    if ((start < line - margin && end > bottomAtEnd() - line) ||
      (start > bottomAtStart() - line && end < line - margin)) {
      hasCrossing = true;
    }
    starts[count] = start;
    ends[count] = end;
    count++;
    keptEnds = keptStarts = null;
  }

  private void keep () {
    if (keptEnds != null) {
      return;
    }
    keptEnds = new HashMap<>();
    keptStarts = new HashMap<>();
    if (visible == null || hasCrossing) {
      // Groups kept at both edges would meet what goes between them
      return;
    }
    final Integer[] byStart = new Integer[count], byEnd = new Integer[count];
    for (int i = 0; i < count; i++) {
      byStart[i] = byEnd[i] = i;
    }
    Arrays.sort(byStart, (a, b) -> starts[a] != starts[b] ?
      Float.compare(starts[a], starts[b]) : Float.compare(ends[a], ends[b]));
    Arrays.sort(byEnd, (a, b) -> ends[a] != ends[b] ?
      Float.compare(ends[a], ends[b]) : Float.compare(starts[a], starts[b]));
    // Ends of what starts seen, from the top and from the bottom, then starts of what ends seen
    keepChain(byStart, starts, ends, bottomAtStart(), bottomAtEnd(), false, true);
    keepChain(byStart, starts, ends, bottomAtStart(), bottomAtEnd(), true, true);
    keepChain(byEnd, ends, starts, bottomAtEnd(), bottomAtStart(), false, false);
    keepChain(byEnd, ends, starts, bottomAtEnd(), bottomAtStart(), true, false);
  }

  private void keepChain (
    Integer[] order,
    float[] seen,
    float[] other,
    float seenBottom,
    float otherBottom,
    boolean isTop,
    boolean isEnd
  ) {
    // seen: places on the screen; other: kept just beyond the edge, apart from the previous by
    // their space on the screen or, up to a line, at the other end, so that split lines stay apart
    final float sign = isTop ? -1f : 1f;
    final float edge = isTop ? -margin : otherBottom;
    int lastKept = -1, lastNotKept = -1;
    float lastPlace = 0f;
    for (int n = 0; n < order.length; n++) {
      final int i = order[isTop ? order.length - 1 - n : n];
      if (seen[i] < -margin || seen[i] > seenBottom) {
        continue;
      }
      if (sign * (other[i] - edge) <= 0f) {
        lastNotKept = i;
        continue;
      }
      float place = sign * edge;
      if (lastKept != -1) {
        place = Math.max(place, sign * lastPlace + space(seen, other, lastKept, i, sign));
      }
      if (lastNotKept != -1) {
        final float after = sign * other[lastNotKept];
        place = Math.max(place, after + space(seen, other, lastNotKept, i, sign));
      }
      place *= sign;
      if (isEnd) {
        keptEnds.put(key(seen[i], other[i]), place);
      } else {
        keptStarts.put(key(other[i], seen[i]), place);
      }
      lastKept = i;
      lastPlace = place;
    }
  }

  private float space (float[] seen, float[] other, int before, int after, float sign) {
    final float otherSpace = Math.min(sign * (other[after] - other[before]), line);
    return Math.max(sign * (seen[after] - seen[before]), otherSpace);
  }

  public float to (float startY, float toY) {
    // At the end of the change, once everything is added
    keep();
    if (visible == null) {
      return toY;
    }
    final Float kept = keptEnds.get(key(startY - visible[0], toY - visible[2]));
    return kept != null ? kept + visible[2] : toY;
  }

  public float from (float startY, float toY, float keptToY) {
    // Where it is at the factor 0, so that it's at its kept start at startFactor
    keep();
    final Float kept = visible != null ?
      keptStarts.get(key(startY - visible[0], toY - visible[2])) : null;
    final float start = kept != null ? kept + visible[0] : startY;
    return startFactor < 1f ? (start - startFactor * keptToY) / (1f - startFactor) : keptToY;
  }
}
