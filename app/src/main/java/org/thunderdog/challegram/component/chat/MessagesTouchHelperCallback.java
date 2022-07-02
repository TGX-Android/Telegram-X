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
import android.util.Log;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.data.TGMessageChat;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.MathUtils;

public class MessagesTouchHelperCallback extends CustomTouchHelper.Callback {
  // public static final float SWIPE_MINIMUM_HEIGHT = 55f;
  public static final float SWIPE_THRESHOLD_WIDTH = 124f;
  public static final float SWIPE_VERTICAL_HEIGHT = 80f;

  private CustomTouchHelper helper;
  private MessagesController controller;

  private float currentDx = 0f;
  private float currentDy = 0f;
  private float lastDx = 0f;
  private float lastDy = 0f;

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
        m instanceof TGMessageChat ||
        m instanceof TGMessageBotInfo ||
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
    lastDx = currentDx;
    lastDy = currentDy;

    if (direction == CustomTouchHelper.DOWN || direction == CustomTouchHelper.UP) {
      return true;
    }

    int actionsCount = lastDx > 0 ? msg.getLeftQuickReactions().size() : msg.getRightQuickReactions().size();
    float verticalTranslate = MathUtils.clamp(lastDy / Screen.dp(SWIPE_VERTICAL_HEIGHT), 0, Math.max(actionsCount - 1f, 0f));
    int actionIndex = Math.round(verticalTranslate);

    if (msg.useBubbles()) {
      final TGMessage.SwipeQuickAction action;

      if (direction == CustomTouchHelper.LEFT && msg.getRightQuickReactions().size() > actionIndex) {
        action = msg.getRightQuickReactions().get(actionIndex);
      } else if (direction == CustomTouchHelper.RIGHT && msg.getLeftQuickReactions().size() > actionIndex) {
        action = msg.getLeftQuickReactions().get(actionIndex);
      } else {
        action = null;
      }

      Runnable after = () -> {
        if (action != null) {
          action.onSwipe();
        }
      };

      msg.normalizeTranslation(holder.itemView, after, action == null || action.needDelay);
      return true;
    }
    return false;
  }

  @Override
  public float getSwipeThreshold (RecyclerView.ViewHolder holder) {
    float itemWidth = (float) holder.itemView.getMeasuredWidth();
    if (controller.getManager().useBubbles()) {
      return (float) Screen.dp(TGMessage.BUBBLE_MOVE_THRESHOLD) / itemWidth;
    } else {
      return (float) Screen.dp(SWIPE_THRESHOLD_WIDTH) / itemWidth * (Settings.instance().needChatQuickShare() ? 1 : 3);
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
    int actionsCount = lastDx > 0 ? msg.getLeftQuickReactions().size() : msg.getRightQuickReactions().size();
    float verticalTranslate = MathUtils.clamp(lastDy / Screen.dp(SWIPE_VERTICAL_HEIGHT), 0, Math.max(actionsCount - 1f, 0f));
    int actionIndex = Math.round(verticalTranslate);

    if (msg.getTranslation() != 0f) {
      msg.completeTranslation();
      if (holder.itemView instanceof MessageViewGroup) {
        ((MessageViewGroup) holder.itemView).setSwipeTranslation(0f);
      }

      if (swipeDir == CustomTouchHelper.LEFT && msg.getRightQuickReactions().size() > actionIndex) {
        msg.getRightQuickReactions().get(actionIndex).onSwipe();
      }

      if (swipeDir == CustomTouchHelper.RIGHT && msg.getLeftQuickReactions().size() > actionIndex) {
        msg.getLeftQuickReactions().get(actionIndex).onSwipe();
      }
    }
  }

  @Override
  public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder holder, float dx, float dy, int state, boolean isActive) {
    if (state == ItemTouchHelper.ACTION_STATE_SWIPE && MessagesHolder.isMessageType(holder.getItemViewType())) {
      currentDx = dx;
      currentDy = dy;

      final MessageView v = MessagesHolder.findMessageView(holder.itemView);
      final TGMessage msg = v.getMessage();

      float newVerticalPosition = (isActive ? dy : lastDy) / Screen.dp(SWIPE_VERTICAL_HEIGHT);
      int actionsCount = dx > 0 ? msg.getLeftQuickReactions().size() : msg.getRightQuickReactions().size();
      float verticalPosition = MathUtils.clamp(newVerticalPosition, 0, Math.max(actionsCount - 1f, 0f));

      msg.translate(dx, verticalPosition, true);
      if (holder.itemView instanceof MessageViewGroup) {
        ((MessageViewGroup) holder.itemView).setSwipeTranslation(msg.getTranslation());
      }
    }
  }
}
