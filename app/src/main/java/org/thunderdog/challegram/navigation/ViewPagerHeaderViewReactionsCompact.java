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
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class ViewPagerHeaderViewReactionsCompact extends FrameLayoutFix implements PagerHeaderView, StretchyHeaderView, ViewPagerTopView.SelectionChangeListener, ThemeInvalidateListener {
  private static class VH extends RecyclerView.ViewHolder {
    public VH (View itemView) {
      super(itemView);
      setIsRecyclable(false);
    }
  }

  @Override
  public void checkRtl () {
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
    public void onBindViewHolder (VH holder, int position) {
    }

    @Override
    public int getItemCount () {
      return 1;
    }
  }

  private final A adapter;
  private final RecyclerView recyclerView;
  @Nullable
  private final ReactionsSelectorRecyclerView reactionsSelectorRecyclerView;
  private final BackHeaderButton backButton;
  private boolean isScrollEnabled = true;
  private int rightOffset;
  private final boolean needReactionSelector;
  private final boolean needShowReactions, needShowViews;

  public ViewPagerHeaderViewReactionsCompact (Context context, Tdlib tdlib, TGMessage message, int rightOffset, boolean needReactionSelector, boolean needShowReactions, boolean needShowViews) {
    super(context);
    this.rightOffset = rightOffset; // - (needShowReactions || needShowViews ? Screen.dp(12) : 0);
    this.needReactionSelector = needReactionSelector;
    this.needShowReactions = needShowReactions;
    this.needShowViews = needShowViews;

    ViewPagerTopView topView = new ViewPagerTopView(context);
    topView.setSelectionColorId(R.id.theme_color_headerTabActive);
    topView.setTextFromToColorId(R.id.theme_color_headerTabInactiveText, R.id.theme_color_headerTabActiveText);
    topView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    topView.setSelectionChangeListener(this);

    adapter = new A(topView);

    if (needReactionSelector) {
      reactionsSelectorRecyclerView = new ReactionsSelectorRecyclerView(context) {
        @Override
        protected void dispatchDraw (Canvas c) {
          super.dispatchDraw(c);
          if (rightOffset > 0) {
            int width = getMeasuredWidth();
            float s = computeHorizontalScrollRange() - computeHorizontalScrollOffset() - computeHorizontalScrollExtent();
            int alpha = (int) (MathUtils.clamp(s / Screen.dp(20f)) * 255);

            shadowPaint2.setAlpha(alpha);
            c.save();
            c.translate(width - shadowSize, 0);
            c.drawRect(0, 0, shadowSize, Screen.dp(52), shadowPaint2);
            c.restore();
            shadowPaint2.setAlpha(255);
          }
        }

        @Override
        public void onScrolled (int dx, int dy) {
          if (rightOffset > 0) {
            invalidate();
          }
        }
      };
      reactionsSelectorRecyclerView.setMessage(message);
      reactionsSelectorRecyclerView.setLayoutParams(FrameLayoutFix.newParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        Gravity.CENTER_VERTICAL, 0, 0, this.rightOffset, 0));
      addView(reactionsSelectorRecyclerView);
    } else {
      reactionsSelectorRecyclerView = null;
    }

    backButton = new BackHeaderButton(context);
    backButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
    backButton.setButtonFactor(needReactionSelector ? BackHeaderButton.TYPE_BACK : BackHeaderButton.TYPE_CLOSE);
    RippleSupport.setTransparentSelector(backButton);
    Views.setClickable(backButton);
    setBackButtonAlpha(1f);

    addView(backButton);

    recyclerView = new RecyclerView(context) {
      @Override
      protected void dispatchDraw (Canvas c) {
        super.dispatchDraw(c);
        int width = getMeasuredWidth();
        float translationX = getTranslationX();
        int alpha1 = (int) ((1f - MathUtils.clamp(translationX / (width / 2f))) * 255);
        int alpha2 = (int) (MathUtils.clamp((float) computeHorizontalScrollOffset() / Screen.dp(20f)) * 255);

        shadowPaint1.setAlpha(Math.min(alpha1, alpha2));
        c.drawRect(0, 0, shadowSize, Screen.dp(52), shadowPaint1);
        shadowPaint1.setAlpha(255);
      }

      @Override
      public void onScrolled (int dx, int dy) {
        invalidate();
      }
    };
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
      Gravity.TOP, Screen.dp(56), 0, 0, 0));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
      @Override
      protected boolean isLayoutRTL () {
        return false;
      }

      @Override
      public boolean canScrollHorizontally () {
        return isScrollEnabled && super.canScrollHorizontally();
      }
    });
    recyclerView.setAdapter(adapter);
    addView(recyclerView);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54)));
    updatePaints(Theme.backgroundColor());
  }

  LinearGradient shader1;
  LinearGradient shader2;
  private Paint shadowPaint1;
  private Paint shadowPaint2;
  private final int shadowSize = Screen.dp(35);

  public void setReactionsSelectorDelegate (ReactionsSelectorRecyclerView.ReactionSelectDelegate delegate) {
    if (reactionsSelectorRecyclerView != null) {
      reactionsSelectorRecyclerView.setDelegate(delegate);
    }
  }

  private int oldPaintsColor = 0;

  public void updatePaints (int color) {
    if (oldPaintsColor != color) {
      oldPaintsColor = color;
    } else return;

    shader1 = new LinearGradient(0, 0, shadowSize / 2f, 0, color, 0, Shader.TileMode.CLAMP);
    shader2 = new LinearGradient(0, 0, shadowSize, 0, 0, color, Shader.TileMode.CLAMP);
    if (shadowPaint1 == null) {
      shadowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    if (shadowPaint2 == null) {
      shadowPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint1.setShader(shader1);
    shadowPaint2.setShader(shader2);
    if (reactionsSelectorRecyclerView != null) {
      reactionsSelectorRecyclerView.invalidate();
    }
    recyclerView.invalidate();
    invalidate();
  }

  public void onPageScrolled (int position, float positionOffset) {
    adapter.topView.setSelectionFactor((float) position + positionOffset);

    if (needReactionSelector && reactionsSelectorRecyclerView != null) {
      int width = getMeasuredWidth();
      if (position == 0) {
        float offset = (width - rightOffset - Screen.dp(56)) * (1f - positionOffset);
        if (needShowReactions && needShowViews) {
          getTopView().setItemTranslationX(1, (int) (Screen.dp(-8) * (1f - positionOffset)));
          getTopView().setItemTranslationX(2, (int) (Screen.dp(-8) * (1f - positionOffset)));
        } else if (needShowReactions || needShowViews) {
          getTopView().setItemTranslationX(1, (int) (Screen.dp(-8) * (1f - positionOffset)));
        }
        recyclerView.setTranslationX(offset);
        reactionsSelectorRecyclerView.setTranslationX(-width * positionOffset);
        backButton.setTranslationX(offset);
        setBackButtonAlpha(positionOffset);
        isScrollEnabled = false;
      } else {
        getTopView().setItemTranslationX(1, 0);
        getTopView().setItemTranslationX(2, 0);
        recyclerView.setTranslationX(0);
        reactionsSelectorRecyclerView.setTranslationX(-width);
        backButton.setTranslationX(0);
        setBackButtonAlpha(1f);
        isScrollEnabled = true;
      }
      recyclerView.invalidate();
    }
  }

  float backButtonAlpha = 0.5f;

  private void setBackButtonAlpha (float alpha) {
    if (alpha != backButtonAlpha) {
      backButtonAlpha = alpha;
      backButton.setAlpha(alpha);
      if (alpha > 0f && backButton.getVisibility() != VISIBLE) {
        backButton.setVisibility(View.VISIBLE);
      }
      if (alpha == 0f && backButton.getVisibility() != GONE) {
        backButton.setVisibility(View.GONE);
      }
    }
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    if (reactionsSelectorRecyclerView != null) {
      reactionsSelectorRecyclerView.invalidate();
    }
    recyclerView.invalidate();
    invalidate();
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
    scrolledX = -view.getLeft();
    int viewX = -scrolledX;

    if ((getParent() != null && ((View) getParent()).getMeasuredWidth() > getMeasuredWidth()) || (viewWidth - parentWidth) < lastItemWidth / 2) {
      int desiredViewLeft = (int) ((float) -(viewWidth - parentWidth) * totalFactor);
      if (viewX != desiredViewLeft) {
        recyclerView.stopScroll();
        int diff = (desiredViewLeft - viewX) * -1;
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
          int offset = (viewX - newViewX);
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

  public BackHeaderButton getBackButton () {
    return backButton;
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
