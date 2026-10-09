package org.thunderdog.challegram.navigation;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.data.ForumRailLayout;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** A one-transaction projection of already visible avatars. No animator, account or image cache. */
public final class ForumRailTransition {
  public interface Drawing { void draw (Canvas canvas, float decorations); }
  public interface Visibility { void setHidden (boolean hidden); }

  /** Keep captured holders bound to their IDs while adapter updates wait for the next layout. */
  public static Runnable holdSourceLayout (RecyclerView source) {
    boolean alreadySuppressed = source.isLayoutSuppressed();
    source.stopScroll();
    source.suppressLayout(true);
    return new Runnable() {
      private boolean released;
      @Override public void run () {
        if (released) return;
        released = true;
        source.suppressLayout(alreadySuppressed);
      }
    };
  }

  public static final class Avatar {
    public final long chatId;
    public final float x, y, radius;
    private final Drawing drawing;
    private final Visibility visibility;

    public Avatar (long chatId, float x, float y, float radius, Drawing drawing, Visibility visibility) {
      this.chatId = chatId;
      this.x = x;
      this.y = y;
      this.radius = radius;
      this.drawing = drawing;
      this.visibility = visibility;
    }

    /** Drawing uses the view's existing coordinates; receiver bounds are never mutated. */
    public static Avatar capture (long id, ViewGroup host, View view, float cx, float cy, float radius,
                                  Drawing drawing, Visibility visibility) {
      Rect position = new Rect(0, 0, view.getWidth(), view.getHeight());
      host.offsetDescendantRectToMyCoords(view, position);
      return new Avatar(id, position.left + cx, position.top + cy, radius, (canvas, decorations) -> {
        canvas.scale(1f / radius, 1f / radius);
        canvas.translate(-cx, -cy);
        drawing.draw(canvas, decorations);
      }, visibility);
    }

    private void draw (Canvas canvas, float x, float y, float radius, float alpha, float decorations) {
      if (alpha <= 0f || radius <= 0f) return;
      int save = canvas.save();
      canvas.translate(x, y);
      canvas.scale(radius, radius);
      if (alpha < 1f) Views.saveLayerAlpha(canvas, -2f, -2f, 2f, 2f, Math.round(255f * alpha), Canvas.ALL_SAVE_FLAG);
      drawing.draw(canvas, decorations);
      canvas.restoreToCount(save);
    }
  }

  private static final class Pair {
    Avatar from, to;
  }

  private final List<Pair> pairs = new ArrayList<>();
  private boolean closed;

  public ForumRailTransition (List<Avatar> source, List<Avatar> target) {
    LinkedHashMap<Long, Pair> byId = new LinkedHashMap<>();
    for (Avatar avatar : source) {
      Pair pair = new Pair();
      pair.from = avatar;
      byId.put(avatar.chatId, pair);
      if (avatar.visibility != null) avatar.visibility.setHidden(true);
    }
    for (Avatar avatar : target) {
      Pair pair = byId.get(avatar.chatId);
      if (pair == null) {
        pair = new Pair();
        byId.put(avatar.chatId, pair);
      }
      pair.to = avatar;
    }
    pairs.addAll(byId.values());
  }

  public void draw (Canvas canvas, float progress) {
    if (closed) return;
    float p = ForumRailLayout.progress(progress);
    for (Pair pair : pairs) {
      Avatar from = pair.from, to = pair.to;
      if (p == 0f) {
        if (from != null) from.draw(canvas, from.x, from.y, from.radius, 1f, 0f);
        continue;
      }
      if (p == 1f) {
        if (to != null) to.draw(canvas, to.x, to.y, to.radius, 1f, 1f);
        continue;
      }
      if (from != null && to != null) {
        float x = ForumRailLayout.interpolate(from.x, to.x, p);
        float y = ForumRailLayout.interpolate(from.y, to.y, p);
        float radius = ForumRailLayout.interpolate(from.radius, to.radius, p);
        // Keep the image opaque while blending receivers; badges appear with the rail.
        to.draw(canvas, x, y, radius, 1f, p);
        if (p < 1f) from.draw(canvas, x, y, radius, 1f - p, 0f);
      } else if (from != null) {
        from.draw(canvas, from.x, from.y, from.radius, 1f - p, 0f);
      } else if (to != null) {
        to.draw(canvas, to.x, to.y, to.radius, p, 1f);
      }
    }
  }

  public void close () {
    if (closed) return;
    closed = true;
    for (Pair pair : pairs) {
      if (pair.from != null && pair.from.visibility != null) pair.from.visibility.setHidden(false);
    }
    pairs.clear();
  }
}
