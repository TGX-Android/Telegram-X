package org.thunderdog.challegram.data;

import org.thunderdog.challegram.tool.EmojiData;

public class TGDefaultEmoji {
  public final int strRes;
  public final String emoji;
  public final int emojiColorState;
  public final boolean isRecent;

  public TGDefaultEmoji (String emoji) {
    this.emoji = emoji;
    this.emojiColorState = EmojiData.instance().getEmojiColorState(emoji);
    this.strRes = 0;
    this.isRecent = false;
  }

  public TGDefaultEmoji (String emoji, boolean isRecent) {
    this.emoji = emoji;
    this.emojiColorState = EmojiData.instance().getEmojiColorState(emoji);
    this.strRes = 0;
    this.isRecent = isRecent;
  }

  public boolean canBeColored () {
    return emojiColorState != EmojiData.STATE_NO_COLORS;
  }
}
