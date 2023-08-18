package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.content.Context;

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

public class EmojiLayoutEmojiPacksController extends EmojiLayoutAbstractController {
  private static final int MINIMUM_EMOJI_COUNT = 8;

  public EmojiLayoutEmojiPacksController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected int calculateSpanCount () {
    int width = recyclerView != null ? recyclerView.getMeasuredWidth(): 0;
    if (width == 0) {
      width = Screen.currentWidth();
    }
    return Math.max(MINIMUM_EMOJI_COUNT, width / Screen.dp(48f));
  }

  @Override
  protected void onScrollToSectionStart (int section) {
    if (emojiLayout != null) {
      emojiLayout.setCurrentEmojiSection(section);
    }
  }
}
