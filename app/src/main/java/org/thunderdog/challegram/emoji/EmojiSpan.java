package org.thunderdog.challegram.emoji;

import android.graphics.Paint;

/**
 * Date: 2019-05-04
 * Author: default
 */
public interface EmojiSpan {
  CharSequence getEmojiCode ();
  int getRawSize (Paint paint);
  boolean needRefresh ();
}
