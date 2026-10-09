package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.view.Gravity;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

/** Fits the complete rail counter, including its outline, without clipping its text. */
public final class ForumRailBadgeLayout {
  public static final float TEXT_SIZE_DP = 13f;

  private ForumRailBadgeLayout () { }

  /** The counter must use TEXT_SIZE_DP and the default background/outline sizing. */
  public static void draw (Canvas canvas, Counter counter, int width, int height, float avatarRadius, boolean rtl, float alpha) {
    if (alpha <= 0f || counter.getVisibility() <= 0f) return;
    float inset = Screen.dp(3f);
    float availableWidth = width - inset * 2f, availableHeight = height - inset * 2f;
    if (availableWidth <= 0f || availableHeight <= 0f) return;

    // DrawAlgorithms adds a 1.5dp outline outside getWidth() and the (textSize - 2) radius.
    float outline = Screen.dp(1.5f);
    float outerWidth = counter.getWidth() + outline * 2f;
    float outerHeight = (Screen.dp(TEXT_SIZE_DP - 2f) + outline) * 2f;
    float scale = Math.min(1f, Math.min(availableWidth / outerWidth, availableHeight / outerHeight));
    float halfWidth = outerWidth * scale / 2f, halfHeight = outerHeight * scale / 2f;
    float desiredX = width / 2f + (rtl ? -1f : 1f) * (avatarRadius - halfWidth);
    float desiredY = height / 2f + avatarRadius - Screen.dp(3f);
    float cx = Math.max(inset + halfWidth, Math.min(width - inset - halfWidth, desiredX));
    float cy = Math.max(inset + halfHeight, Math.min(height - inset - halfHeight, desiredY));

    // RIGHT/LEFT anchor the rounded cap's center, not the badge edge. Center anchoring also
    // mirrors placement without mirroring the glyphs. Only over-wide counters are scaled down.
    int save = canvas.save();
    try {
      canvas.translate(cx, cy);
      canvas.scale(scale, scale);
      counter.draw(canvas, 0f, 0f, Gravity.CENTER, alpha);
    } finally {
      canvas.restoreToCount(save);
    }
  }
}
