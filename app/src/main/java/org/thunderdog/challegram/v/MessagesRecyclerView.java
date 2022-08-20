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
 * File created on 25/09/2015 at 14:40
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.CustomTouchHelper;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesHolder;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.chat.MessagesTouchHelperCallback;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

public class MessagesRecyclerView extends RecyclerView implements FactorAnimator.Target {
  public static final long ITEM_ANIMATOR_DURATION = 140L;

  private MessagesManager manager;
  private CustomTouchHelper touchHelper;
  private MessagesTouchHelperCallback callback;

  public MessagesRecyclerView (Context context) {
    super(context);
    init();
  }

  public MessagesRecyclerView (Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public MessagesRecyclerView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  private CustomItemAnimator itemAnimator;

  private boolean animatorEnabled;

  public void setMessageAnimatorEnabled (boolean isEnabled) {
    if (this.animatorEnabled != isEnabled) {
      this.animatorEnabled = isEnabled;
      // setItemAnimator(isEnabled ? itemAnimator : null);
    }
  }

  private int prevWidth, prevHeight;

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    if (manager != null) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      if (prevWidth != width || prevHeight != height) {
        prevWidth = width;
        prevHeight = height;
        manager.controller().onMessagesFrameChanged();
      } else {
        manager.onViewportMeasure();
      }
    }
  }

  private MessagesItemDecoration itemDecoration;

  private void init () {
    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, ITEM_ANIMATOR_DURATION);
    itemAnimator.setSupportsChangeAnimations(false);
    setItemAnimator(null);
    callback = new MessagesTouchHelperCallback();
    touchHelper = new CustomTouchHelper(callback);
    callback.setTouchHelper(touchHelper);
    touchHelper.attachToRecyclerView(this);
    addItemDecoration(itemDecoration = new MessagesItemDecoration());
  }

  public MessagesTouchHelperCallback getMessagesTouchHelper () {
    return callback;
  }

  public void setManager (MessagesManager manager) {
    this.manager = manager;
  }

  public void setController (MessagesController controller) {
    callback.setController(controller);
  }

  public void startSwipe (View view) {
    RecyclerView.ViewHolder holder = getChildViewHolder(view);
    if (holder != null && holder.getItemViewType() != MessagesHolder.TYPE_EMPTY) {
      touchHelper.startSwipe(holder);
    }
  }

  public MessagesManager getManager () {
    return manager;
  }

  private boolean ignore;

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (!ignore) {
      ignore = true;
      manager.rebuildFirstItem(true, changed);
      ignore = false;
    }
    if (manager != null) {
      manager.controller().onMessagesFrameChanged();
    }
  }

  private boolean disallowIntercept;

  @Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    this.disallowIntercept = disallowIntercept;
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }

  public boolean disallowInterceptTouchEvent () {
    return disallowIntercept;
  }

  public void invalidateAll () {
    final LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
      int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
      for (int i = first; i <= last; i++) {
        View v = manager.findViewByPosition(i);
        if (v != null) {
          v.invalidate();
        }
      }
    } else {
      for (int i = 0; i < getChildCount(); i++) {
        View v = getChildAt(i);
        if (v != null) {
          v.invalidate();
        }
      }
    }
  }

  private boolean isScrolling;
  private float scrollFactor;
  private FactorAnimator scrollAnimator;

  private boolean startAnimatorOnAnimationEnd;

  public void setIsScrolling (boolean isScrolling) {
    if (this.isScrolling != isScrolling) {
      this.isScrolling = isScrolling;
      if (scrollAnimator == null) {
        scrollAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.scrollFactor);
      }
      if (scrollAnimator.isAnimating() && !isScrolling) {
        startAnimatorOnAnimationEnd = true;
        return;
      }
      startAnimatorOnAnimationEnd = false;
      scrollAnimator.setStartDelay(isScrolling ? 0 : 1500);
      scrollAnimator.setDuration(isScrolling ? 120l : 180l);
      scrollAnimator.animateTo(isScrolling ? 1f : 0f);
    }
  }

  public final void invalidateDate () {
    int topOffset = getTopOffset();
    invalidate(topOffset, 0, getMeasuredWidth(), topOffset + TGMessage.getDateHeight(manager.useBubbles()));
  }

  public void showDateForcely () {
    if (!isScrolling && false) {
      isScrolling = true;
      if (scrollFactor != 0f) {
        scrollFactor = 1f;
        if (scrollAnimator != null) {
          scrollAnimator.forceFactor(1f);
        }
      }
      invalidateDate();
      setIsScrolling(false);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.scrollFactor != factor) {
      this.scrollFactor = factor;
      invalidateDate();
      if (factor == 1f && startAnimatorOnAnimationEnd && !isScrolling) {
        startAnimatorOnAnimationEnd = false;
        scrollAnimator.setStartDelay(1500);
        scrollAnimator.setDuration(180l);
        scrollAnimator.animateTo(0f);
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  public void setTranslationY (float translationY) {
    if (scrollFactor == 0f) {
      super.setTranslationY(translationY);
    } else if (getTranslationY() != translationY) {
      super.setTranslationY(translationY);
      invalidateDate();
    }
  }

  private int getTopOffset () {
    int topOffset = manager.getTopOverlayOffset();
    HeaderView headerView = UI.getHeaderView(getContext());
    if (headerView != null) {
      topOffset += headerView.getFilling().getPlayerOffset();
    }
    return topOffset;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    boolean res = super.onInterceptTouchEvent(e);
    if (scrollFactor > 0f && e.getAction() == MotionEvent.ACTION_DOWN && isInsideDate(e.getX(), e.getY())) {
      return true;
    }
    return res;
  }

  private boolean listeningClick;
  private float listenClickX, listenClickY;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();
        if (listeningClick = scrollFactor > 0f && isInsideDate(x, y)) {
          listenClickX = x;
          listenClickY = y;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listeningClick && (Math.abs(listenClickX - e.getX()) > Screen.getTouchSlop() || Math.abs(listenClickY - e.getY()) > Screen.getTouchSlop())) {
          listeningClick = false;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        listeningClick = false;
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (listeningClick) {
          if (onDateClick()) {
            ViewUtils.onClick(this);
          }
          listeningClick = false;
        }
        break;
      }
    }
    return super.onTouchEvent(e);
  }

  private int jumpToBeginningOfTheDay;

  private boolean isInsideDate (float x, float y) {
    if (itemDecoration == null) {
      return false;
    }
    if (manager.controller().inPreviewMode()) {
      return false;
    }
    if (itemDecoration.lastMessage == null || itemDecoration.lastAlpha == 0f) {
      return false;
    }
    int dateHeight = Screen.dp(26f);
    final int cx = getMeasuredWidth() / 2;
    final int cy = itemDecoration.lastTop + Screen.dp(manager.useBubbles() ? 5f : 8f) + dateHeight / 2; // + dateHeight / 2;
    int dateWidth = itemDecoration.lastMessage.getDateWidth();
    int padding = itemDecoration.lastMessage.getDatePadding();
    int bound = Screen.dp(4f);
    jumpToBeginningOfTheDay = itemDecoration.lastMessage.getDate();
    int boundHorizontal = dateWidth / 2 + padding + bound;
    int boundVertical = dateHeight / 2 + bound;

    return x >= cx - boundHorizontal && x < cx + boundHorizontal && y >= cy - boundVertical && y < cy + boundVertical;

    // return x >= cx - padding - dateWidth / 2 - bound && x < cx + padding + dateWidth / 2 + bound && y >= cy - padding - dateHeight / 2 - bound && y < cy + padding + dateHeight / 2 + bound;
  }

  private boolean onDateClick () {
    if (jumpToBeginningOfTheDay != 0) {
      manager.controller().jumpToBeginningOfTheDay(jumpToBeginningOfTheDay);
      return true;
    }
    return false;
  }

  private class MessagesItemDecoration extends ItemDecoration {
    protected TGMessage lastMessage;
    protected int lastTop;
    protected float lastAlpha;

    @Override
    public void onDrawOver (Canvas c, RecyclerView parent, State state) {
      this.lastMessage = null;
      this.lastTop = 0;
      this.lastAlpha = 0f;

      MessagesAdapter adapter = (MessagesAdapter) parent.getAdapter();
      if (adapter.getMessageCount() == 0) {
        return;
      }

      final int centerX = parent.getMeasuredWidth() / 2;

      // int lastTop = 0;
      boolean hasDrawn = false;

      int dateHeight = TGMessage.getDateHeight(manager.useBubbles());
      int topOffset = getTopOffset();
      // TGMessage lastMessage = null;

      int lastIndex = parent.getChildCount() - 1;
      while (lastIndex >= 0) {
        View lastView = parent.getChildAt(lastIndex);
        if (lastView.getTop() + lastView.getMeasuredHeight() > topOffset) {
          break;
        }
        lastIndex--;
      }

      // this will iterate over every visible view
      for (int i = 0; i <= lastIndex; i++) {
        // get the view
        final View view = parent.getChildAt(i);
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();

        // get the position
        final int position = params.getViewAdapterPosition();

        // and finally draw the separator
        if (position < state.getItemCount()) {
          final TGMessage msg = adapter.getMessage(position);
          if (msg != null) {
            int viewTop = view.getTop();
            if (msg.hasDate()) {
              viewTop += msg.getDrawDateY();
            }
            int top = viewTop;
            if (top < topOffset) {
              if (hasDrawn) {
                int maxY = lastTop - dateHeight;
                if (lastMessage != null) {
                  maxY -= lastMessage.getDrawDateY();
                }
                top = Math.min(maxY, topOffset);
              } else {
                top = topOffset;
              }
            }
            boolean isLast = i == lastIndex;
            int checkTop = viewTop - topOffset;
            float detachFactor = !isLast || checkTop >= 0 ? 0f : MathUtils.clamp((float) -checkTop / (float) dateHeight);
            float alpha = isLast ? scrollFactor : 1f;
            float drawAlpha = detachFactor == 1f ? alpha : 1f;
            if (msg.drawDate(c, centerX, top, detachFactor, drawAlpha)) {
              hasDrawn = true;
              lastTop = top;
              lastMessage = msg;
              lastAlpha = drawAlpha;
              continue;
            }
            if (isLast) {
              Object tag = msg.getTag();
              if (tag != null) {
                TGMessage tagMsg = (TGMessage) tag;
                if (!tagMsg.isDestroyed() && tagMsg.drawDate(c, centerX, top, 1f, alpha)) {
                  hasDrawn = true;
                  lastTop = top;
                  lastMessage = tagMsg;
                  lastAlpha = alpha;
                  continue;
                }
                msg.setTag(null);
              }
              for (int j = position + 1; j < adapter.getMessageCount(); j++) {
                TGMessage olderMessage = adapter.getMessage(j);
                if (olderMessage != null && olderMessage.drawDate(c, centerX, top, 1f, alpha)) {
                  msg.setTag(olderMessage);
                  hasDrawn = true;
                  lastTop = top;
                  lastMessage = msg;
                  lastAlpha = alpha;
                  break;
                }
              }
            }
          }
        }
      }
    }
  }
}
