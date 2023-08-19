package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.content.Context;

import org.thunderdog.challegram.telegram.Tdlib;

public class EmojiLayoutStickersSetsController extends EmojiLayoutAbstractController {
  private int spanCountToSet;

  public EmojiLayoutStickersSetsController (Context context, Tdlib tdlib, int controllerId) {
    super(context, tdlib, controllerId);
  }

  public void setSpanCount (int spanCount) {
    this.spanCountToSet = spanCount;
    checkSpanCount();
  }

  @Override
  protected int calculateSpanCount () {
    return spanCountToSet;
  }
}
