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
 * File created on 27/05/2017
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;

import me.vkryl.android.widget.FrameLayoutFix;

public class ViewPagerHeaderViewCompact extends FrameLayoutFix implements PagerHeaderView, StretchyHeaderView, ViewPagerTopView.SelectionChangeListener {
  private static class VH extends RecyclerView.ViewHolder {
    public VH (View itemView) {
      super(itemView);
      setIsRecyclable(false);
    }
  }

  @Override
  public void checkRtl () {
    LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
    if (manager.getReverseLayout() != Lang.rtl()) {
      View view = manager.findViewByPosition(0);
      boolean needScroll = false;
      int scrollOffset = 0;
      if (view != null) {
        int parentWidth = getRecyclerView().getMeasuredWidth();
        int viewWidth = view.getMeasuredWidth();
        int viewOffset = view.getLeft();
        if (viewWidth > parentWidth) {
          int availScrollX = viewWidth - parentWidth;
          int scrolledX;
          if (Lang.rtl()) { // LTR -> RTL
            scrolledX = -viewOffset;
          } else { // RTL -> LTR
            scrolledX = availScrollX + viewOffset;
          }
          scrollOffset = -scrolledX;
          needScroll = true;
        }
      }
      manager.setReverseLayout(Lang.rtl());
      if (needScroll) {
        manager.scrollToPositionWithOffset(0, scrollOffset);
      }
    }
    getTopView().checkRtl();
  }

  private static class A extends RecyclerView.Adapter<VH> {
    private final ViewPagerTopView topView;

    public A (ViewPagerTopView topView) {
      this.topView = topView;
    }

    @Override
    public VH onCreateViewHolder (ViewGroup parent, int viewType) {
      if (topView.getParent() != null) {
        Log.w("ViewPagerHeaderViewCompact: topView is already attached to another cel");
        ((ViewGroup) topView.getParent()).removeView(topView);
      }
      return new VH(topView);
    }

    @Override
    public void onBindViewHolder (VH holder, int position) { }

    @Override
    public int getItemCount () {
      return 1;
    }
  }

  private final A adapter;
  private final RecyclerView recyclerView;

  public ViewPagerHeaderViewCompact (Context context) {
    super(context);

    ViewPagerTopView topView = new ViewPagerTopView(context);
    topView.setSelectionColorId(R.id.theme_color_headerTabActive);
    topView.setTextFromToColorId(R.id.theme_color_headerTabInactiveText, R.id.theme_color_headerTabActiveText);
    topView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Size.getHeaderPortraitSize()));
    topView.setSelectionChangeListener(this);

    adapter = new A(topView);

    recyclerView = new RecyclerView(context);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize(), Gravity.TOP));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS :OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, Lang.rtl()));
    recyclerView.setAdapter(adapter);
    addView(recyclerView);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderBigPortraitSize(true)));
  }

  @Override
  public void onSelectionChanged (int selectionLeft, int selectionWidth, int firstItemWidth, int lastItemWidth, float totalFactor, boolean animated) {
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    if (view == null) {
      return;
    }
    final int viewWidth = view.getMeasuredWidth();
    final int parentWidth = recyclerView.getMeasuredWidth();
    if (viewWidth <= parentWidth) {
      return;
    }
    if (recyclerView.isComputingLayout()) {
      return;
    }

    final int availScrollX = viewWidth - parentWidth;
    final int scrolledX;
    if (Lang.rtl()) {
      scrolledX = availScrollX + view.getLeft();
    } else {
      scrolledX = -view.getLeft();
    }
    int viewX = -scrolledX;

    if ((getParent() != null && ((View) getParent()).getMeasuredWidth() > getMeasuredWidth()) || (viewWidth - parentWidth) < lastItemWidth / 2) {
      int desiredViewLeft = (int) ((float) -(viewWidth - parentWidth) * totalFactor);
      if (viewX != desiredViewLeft) {
        recyclerView.stopScroll();
        int diff = (desiredViewLeft - viewX) * (Lang.rtl() ? 1 : -1);
        if (animated) {
          recyclerView.smoothScrollBy(diff, 0);
        } else {
          recyclerView.scrollBy(diff, 0);
        }
      }
    } else {
      int visibleSelectionX = selectionLeft + viewX;
      int desiredSelectionX = (int) ((float) Screen.dp(16f) * (selectionLeft >= selectionWidth ? 1f : (float) selectionLeft / (float) selectionWidth));

      if (visibleSelectionX != desiredSelectionX) {
        int newViewX = viewX + (desiredSelectionX - visibleSelectionX);
        int maxX = parentWidth - viewWidth;
        if (newViewX < maxX) {
          newViewX = maxX;
        }
        if (newViewX != viewX) {
          recyclerView.stopScroll();
          int offset = (viewX - newViewX) * (Lang.rtl() ? -1 : 1);
          if (animated) {
            recyclerView.smoothScrollBy(offset, 0);
          } else {
            recyclerView.scrollBy(offset, 0);
          }
        }
      }
    }/* else {
      int visibleSelectionLeft = selectionLeft + viewOffset;

      int desiredLeft = (int) ((float) Screen.dp(16f) * (selectionLeft >= selectionWidth ? 1f : (float) selectionLeft / (float) selectionWidth));

      if (visibleSelectionLeft != desiredLeft) {
        int newViewLeft = viewOffset + (desiredLeft - visibleSelectionLeft);

        int maxLeft = parentWidth - viewWidth;
        if (newViewLeft < maxLeft) {
          newViewLeft = maxLeft;
        }


        if (newViewLeft != viewOffset) {
          recyclerView.stopScroll();
          int offset = viewOffset - newViewLeft;
          if (animated) {
            recyclerView.smoothScrollBy(offset, 0);
          } else {
            recyclerView.scrollBy(offset, 0);
          }
        }
      }
    }*/
  }

  public boolean canScrollLeft () {
    int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i != 0) {
      return true;
    }
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    return view == null || view.getLeft() < 0;
  }

  public boolean canScrollInAnyDirection () {
    int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i != 0) {
      return i != RecyclerView.NO_POSITION;
    }
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    return view == null || view.getLeft() < 0 || view.getRight() > recyclerView.getMeasuredWidth();
  }

  public RecyclerView getRecyclerView () {
    return recyclerView;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return !(e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) && super.onTouchEvent(e);
  }

  private boolean canTouchAt (float x, float y) {
    y -= recyclerView.getTop() + (int) recyclerView.getTranslationY();
    return y >= 0 && y < adapter.topView.getMeasuredHeight();
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    return (e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) || super.onInterceptTouchEvent(e);
  }

  @Override
  public ViewPagerTopView getTopView () {
    return adapter.topView;
  }

  private static final float TOP_SCALE_LIMIT = .25f;

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    //final float totalScale = (float) Size.HEADER_DRAWER_SIZE / (float) Size.HEADER_SIZE_DIFFERENCE;
    //scaleFactor = scaleFactor / totalScale;
    scaleFactor = 1f - scaleFactor;

    //noinspection Range
    recyclerView.setAlpha(scaleFactor <= TOP_SCALE_LIMIT ? 0f : (scaleFactor - TOP_SCALE_LIMIT) / TOP_SCALE_LIMIT);
    recyclerView.setTranslationY(Size.getHeaderSizeDifference(true) * (1f - scaleFactor));
  }
}
