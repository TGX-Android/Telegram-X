package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import org.thunderdog.challegram.ui.MessagesController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  EmojiMediaType.EMOJI, EmojiMediaType.GIF, EmojiMediaType.STICKER
})
public @interface EmojiMediaType {
  int STICKER = 0, GIF = 1, EMOJI = 2;
}
