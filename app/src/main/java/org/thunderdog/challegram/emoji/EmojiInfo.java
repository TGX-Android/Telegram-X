package org.thunderdog.challegram.emoji;

import android.graphics.Rect;

/**
 * Date: 2019-05-04
 * Author: default
 */
public class EmojiInfo {
  public final Rect rect;
  public final int page1, page2;

  public EmojiInfo (Rect rect, int page1, int page2) {
    this.rect = rect;
    this.page1 = page1;
    this.page2 = page2;
  }
}
