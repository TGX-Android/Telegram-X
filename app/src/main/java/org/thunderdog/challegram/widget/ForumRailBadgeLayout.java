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
 */
package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.view.Gravity;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

/** Fits the complete rail counter, including its outline, without clipping its text. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class ForumRailBadgeLayout {
  public static final float TEXT_SIZE_DP = 13f;

  private ForumRailBadgeLayout () {
  }

  /** The counter must use TEXT_SIZE_DP and the default background/outline sizing. */
  public static void draw (
    Canvas canvas,
    Counter counter,
    int width,
    int height,
    float avatarRadius,
    boolean rtl,
    float alpha
  ) {
    if (alpha <= 0f || counter.getVisibility() <= 0f) {
      return;
    }
    float inset = Screen.dp(3f);
    float availableWidth = width - inset * 2f, availableHeight = height - inset * 2f;
    if (availableWidth <= 0f || availableHeight <= 0f) {
      return;
    }

    // DrawAlgorithms adds a 1.5dp outline outside getWidth() and the (textSize - 2) radius.
    float outline = Screen.dp(1.5f);
    float outerWidth = counter.getWidth() + outline * 2f;
    float outerHeight = (Screen.dp(TEXT_SIZE_DP - 2f) + outline) * 2f;
    float scale =
      Math.min(1f, Math.min(availableWidth / outerWidth, availableHeight / outerHeight));
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