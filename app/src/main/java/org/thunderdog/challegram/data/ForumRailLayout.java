package org.thunderdog.challegram.data;

/** Geometry shared by measurement, hit testing and the navigation gesture boundary. */
public final class ForumRailLayout {
  private ForumRailLayout () { }

  public static float progress (float value) { return Math.max(0f, Math.min(1f, value)); }

  public static float interpolate (float from, float to, float progress) {
    return from + (to - from) * progress(progress);
  }

  /** The body already has a leading rail margin. Gesture distance remains the full viewport. */
  public static float topicTranslation (int viewportWidth, int railWidth, float openProgress, boolean rtl) {
    return Math.max(0, viewportWidth - railWidth) * (1f - progress(openProgress)) * (rtl ? -1f : 1f);
  }

  public static int widthDp (float viewportDp) {
    return viewportDp >= 600 ? 72 : viewportDp < 360 ? 56 : 64;
  }

  public static int occupied (int railWidth, float reveal) {
    return Math.round(railWidth * Math.max(0f, Math.min(1f, reveal)));
  }

  /** A short initial page must not make LinearLayoutManager fill its end gap above the anchor. */
  public static boolean canRestoreScroll (int count, int position, int offset, int rowHeight, int viewportHeight, boolean endReached) {
    if (count <= 0 || position < 0 || rowHeight <= 0 || viewportHeight <= 0) return false;
    if (endReached) return true; // Only the real list end may legitimately clamp the viewport.
    return position < count && (long) (count - position) * rowHeight + offset >= viewportHeight;
  }

  public static boolean hitRail (int viewportWidth, int occupied, float x, boolean rtl) {
    return occupied > 0 && x >= 0 && x < viewportWidth && (rtl ? x >= viewportWidth - occupied : x < occupied);
  }

  public static boolean hitRail (int viewportWidth, int occupied, float x, float y, int headerBottom, int contentBottom, boolean rtl) {
    return y >= headerBottom && y < contentBottom && hitRail(viewportWidth, occupied, x, rtl);
  }

  public static float contentX (int occupied, float x, boolean rtl) {
    return rtl ? x : x - occupied;
  }
}
