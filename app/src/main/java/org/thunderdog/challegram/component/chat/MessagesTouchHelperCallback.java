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
 * File created on 23/02/2016 at 03:16
 */
package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;

public class MessagesTouchHelperCallback extends CustomTouchHelper.Callback {
  private CustomTouchHelper helper;
  private MessagesController controller;

  public void setTouchHelper (CustomTouchHelper helper) {
    this.helper = helper;
  }

  public void setController (MessagesController controller) {
    this.controller = controller;
  }

  @Override
  public boolean canScroll () {
    return false;
  }

  @Override
  public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder holder) {
    if (controller == null ||
        controller.inSelectMode() ||
        controller.navigationController() == null ||
        !MessagesHolder.isMessageType(holder.getItemViewType())) {
      return 0;
    }
    TGMessage m = MessagesHolder.findMessageView(holder.itemView).getMessage();
    if (!m.canSwipe() ||
        m.isSending() ||
        m.getChatId() == 0) {
      return 0;
    }

    int flags = 0;

    if (m.getRightQuickReactions().size() > 0) {
      flags |= ItemTouchHelper.LEFT;
    }

    if (m.getLeftQuickReactions().size() > 0) {
      flags |= ItemTouchHelper.RIGHT;
    }

    if (m.getLeftQuickReactions().size() > 1 || m.getRightQuickReactions().size() > 1) {
      flags |= ItemTouchHelper.UP;
      flags |= ItemTouchHelper.DOWN;
    }

    return makeMovementFlags(0, flags);
  }

  @Override
  public boolean isLongPressDragEnabled () {
    return false;
  }

  @Override
  public boolean isItemViewSwipeEnabled () {
    return false;
  }

  @Override
  public boolean onBeforeSwipe (RecyclerView.ViewHolder holder, int direction) {
    final TGMessage msg = MessagesHolder.findMessageView(holder.itemView).getMessage();
    if (direction == CustomTouchHelper.DOWN || direction == CustomTouchHelper.UP) {
      return true;
    }

    if (msg.useBubbles()) {
      final TGMessage.SwipeQuickAction action = msg.getSwipeHelper().getChosenQuickAction();
      Runnable after = () -> {
        if (action != null) {
          action.onSwipe();
        }
      };

      msg.normalizeTranslation(holder.itemView, after, action == null || action.needDelay);
      return true;
    }

    msg.getSwipeHelper().onBeforeSwipe();
    return false;
  }

  @Override
  public float getSwipeThreshold (RecyclerView.ViewHolder holder) {
    float itemWidth = (float) holder.itemView.getMeasuredWidth();
    if (controller.getManager().useBubbles()) {
      return (float) Screen.dp(TGMessage.BUBBLE_MOVE_THRESHOLD) / itemWidth;
    } else {
      return (float) Screen.dp(MessageQuickActionSwipeHelper.SWIPE_THRESHOLD_WIDTH) / itemWidth;  /*(Settings.instance().needChatQuickShare() ? 1 : 3);*/
    }
  }

  @Override
  public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
    return false;
  }

  @Override
  public void onSwiped (RecyclerView.ViewHolder holder, int swipeDir) {
    helper.ignoreSwipe(holder, swipeDir);

    TGMessage msg = MessagesHolder.findMessageView(holder.itemView).getMessage();
    if (msg.getTranslation() != 0f) {
      TGMessage.SwipeQuickAction action = msg.getSwipeHelper().getChosenQuickAction();
      msg.completeTranslation();
      if (holder.itemView instanceof MessageViewGroup) {
        ((MessageViewGroup) holder.itemView).setSwipeTranslation(0f);
      }

      if (action != null) {
        action.onSwipe();
      }
    }
  }

  @Override
  public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder holder, float dx, float dy, int state, boolean isActive) {
    if (state == ItemTouchHelper.ACTION_STATE_SWIPE && MessagesHolder.isMessageType(holder.getItemViewType())) {
      final MessageView v = MessagesHolder.findMessageView(holder.itemView);
      final TGMessage msg = v.getMessage();

      msg.getSwipeHelper().translate(dx, dy, true);
      if (holder.itemView instanceof MessageViewGroup) {
        ((MessageViewGroup) holder.itemView).setSwipeTranslation(msg.getTranslation());
      }
    }
  }
}
