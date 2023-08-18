package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.content.Context;

import org.thunderdog.challegram.telegram.Tdlib;

public class EmojiLayoutTrendingSetsController extends EmojiLayoutAbstractController {
  private int spanCountToSet;

  public EmojiLayoutTrendingSetsController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public void setSpanCount (int spanCount) {
    this.spanCountToSet = spanCount;
    checkSpanCount();
  }

  @Override
  protected int calculateSpanCount () {
    return spanCountToSet;
  }

  @Override
  protected void onScrollToSectionStart (int section) {
    /* if (emojiLayout != null) {
      emojiLayout.setCurrentStickerSectionByPosition(section, true, true);
    } */
  }
}
