/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 22/11/2016
 */
package org.thunderdog.challegram.component.sticker;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class ReactionInChatWrapper extends FrameLayoutFix implements Destroyable {

  public static final float PADDING = 8f;

  private final int[] locationOnScreen = new int[2];

  public int yOffset = 0;

  public ReactionInChatWrapper (Context context) {
    super(context);
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    this.getLocationOnScreen(locationOnScreen);
    yOffset = locationOnScreen[1];
  }

  @Override
  public void performDestroy () {
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View view = getChildAt(i);
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      } else if (view instanceof ViewGroup) {
        Views.destroy((ViewGroup) view);
      }
      removeViewAt(i);
    }
  }

  public void reattach (ReactionInChatView reactionInChatView) {
    reactionInChatView.detach();

    int[] location = new int[2];
    reactionInChatView.getLocationOnScreen(location);
    int x = location[0];
    int y = location[1];

    ((ViewGroup) reactionInChatView.getParent()).removeView(reactionInChatView);
    this.addView(reactionInChatView);

    reactionInChatView.setPadding(0, 0, 0, 0);
    LayoutParams layoutParams = (LayoutParams) reactionInChatView.getLayoutParams();
    layoutParams.leftMargin = x;
    layoutParams.topMargin = y - yOffset;
    reactionInChatView.setLayoutParams(layoutParams);

    reactionInChatView.attach();
  }
}
