package org.thunderdog.challegram.widget;

/** Pure, widget-local geometry. The host owns the full-width header and composer. */
public final class ForumTopicsTabsLayout {
  private ForumTopicsTabsLayout () { }

  public static int horizontalHeight (float density, float fontScale) {
    // One 14sp line, never a second metadata row. Only extreme font sizes need extra height.
    return px(Math.max(44, 18 * scale(fontScale) + 8), density);
  }

  public static int sideWidth (float density, float fontScale) {
    return px(64 + 8 * Math.min(1, Math.max(0, (scale(fontScale) - 1.6f) / .4f)), density);
  }

  public static int sideRowHeight (float density, float fontScale) {
    return px(Math.min(76, 64 + 24 * (scale(fontScale) - 1)), density);
  }

  public static int topicIconSize (float density, boolean side) {
    return px(side ? 24 : 20, density);
  }

  /** Input is the full host width, never an already-inset RecyclerView width. */
  public static int historyWidth (int hostWidth, boolean side, int sideWidth) {
    if (hostWidth <= 0) return 0;
    return Math.max(1, hostWidth - (side ? Math.max(0, sideWidth) : 0));
  }

  /** All is position zero; the separately appended selection/New Topic are not server rows. */
  public static boolean nearEnd (int lastVisiblePosition, int serverVisibleCount) {
    return lastVisiblePosition >= 0 && lastVisiblePosition >= Math.max(0, serverVisibleCount - 3);
  }

  private static float scale (float value) { return Float.isNaN(value) || Float.isInfinite(value) || value < 1 ? 1 : value; }
  private static int px (float value, float density) {
    if (Float.isNaN(density) || Float.isInfinite(density) || density <= 0) density = 1;
    return Math.max(1, Math.round(value * density));
  }

  public static final class Bounds {
    public final int left, top, right, bottom;
    Bounds (int left, int top, int right, int bottom) {
      this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public int width () { return right - left; }
    public int height () { return bottom - top; }
  }

  public static final class Parts {
    public final Bounds tabs, state, placement;
    Parts (Bounds tabs, Bounds state, Bounds placement) {
      this.tabs = tabs; this.state = state; this.placement = placement;
    }
  }

  /** The placement button has priority even when an IME or a tiny host leaves no tab space. */
  public static Parts partition (int width, int height, int touchSize, boolean side, boolean rtl, boolean showState) {
    width = Math.max(0, width); height = Math.max(0, height); touchSize = Math.max(0, touchSize);
    if (side) {
      int button = Math.min(height, touchSize);
      int state = showState ? Math.min(height - button, touchSize) : 0;
      return new Parts(new Bounds(0, 0, width, height - button - state),
        new Bounds(0, height - button - state, width, height - button),
        new Bounds(0, height - button, width, height));
    }
    int button = Math.min(width, touchSize);
    int state = showState ? Math.min(width - button, touchSize) : 0;
    if (rtl) {
      return new Parts(new Bounds(button + state, 0, width, height),
        new Bounds(button, 0, button + state, height), new Bounds(0, 0, button, height));
    }
    return new Parts(new Bounds(0, 0, width - button - state, height),
      new Bounds(width - button - state, 0, width - button, height),
      new Bounds(width - button, 0, width, height));
  }
}
