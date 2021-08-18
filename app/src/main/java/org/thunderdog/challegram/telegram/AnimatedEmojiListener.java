package org.thunderdog.challegram.telegram;

public interface AnimatedEmojiListener {
  int TYPE_TGX = 0;
  int TYPE_EMOJI = 1;
  int TYPE_DICE = 2;

  default void onAnimatedEmojiChanged (int type) { }
}
