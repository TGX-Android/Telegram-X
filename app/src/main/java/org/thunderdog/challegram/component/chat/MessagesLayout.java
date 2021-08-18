/**
 * File created on 21/02/16 at 21:28
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.View;
import android.widget.RelativeLayout;

import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.EmojiLayout;

import me.vkryl.android.animator.Animated;

public class MessagesLayout extends RelativeLayout implements Animated {
  private MessagesController controller;

  public MessagesLayout (Context context) {
    super(context);
  }

  public void setController (MessagesController controller) {
    this.controller = controller;
  }

  boolean changedMin;
  int lastMeasuredWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    boolean emojiState = controller.getEmojiState();
    EmojiLayout emojiLayout = controller.getEmojiLayout();

    boolean commandsState = controller.getCommandsState();
    CommandKeyboardLayout keyboardLayout = controller.getKeyboardLayout();

    int height = getMeasuredHeight();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    changedMin = height > getMeasuredHeight() && ((emojiState && emojiLayout != null) || (commandsState && keyboardLayout != null)) && getMeasuredWidth() == lastMeasuredWidth;
    lastMeasuredWidth = getMeasuredWidth();
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (changedMin) {
      boolean emojiState = controller.getEmojiState();
      EmojiLayout emojiLayout = controller.getEmojiLayout();

      boolean commandsState = controller.getCommandsState();
      CommandKeyboardLayout keyboardLayout = controller.getKeyboardLayout();

      if (emojiState && emojiLayout != null) {
        emojiLayout.onKeyboardStateChanged(true);
      }
      if (commandsState && keyboardLayout != null) {
        keyboardLayout.onKeyboardStateChanged(true);
      }
    }
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }
}
