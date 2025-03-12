/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessageOptionsPagerController;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

@SuppressLint("ViewConstructor")
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
    @NonNull
    public VH onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (topView.getParent() != null) {
        Log.w("ViewPagerHeaderViewCompact: topView is already attached to another cel");
        ((ViewGroup) topView.getParent()).removeView(topView);
      }
      return new VH(topView);
    }

    @Override
    public void onBindViewHolder (@NonNull VH holder, int position) {
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
  private final @Nullable ImageView moreButton;
  private boolean isScrollEnabled = true;

  private final MessageOptionsPagerController.State state;

  private final boolean needReactionSelector;
  private final boolean needShowReactions, needShowViews;

  public ViewPagerHeaderViewReactionsCompact (Context context, MessageOptionsPagerController.State state) {
    super(context);
    this.state = state;

    this.needReactionSelector = state.needShowMessageOptions;
    this.needShowReactions = state.needShowMessageReactionSenders;
    this.needShowViews = state.needShowMessageViews;

    final boolean needShowMoreButton = state.needShowReactionsPopupPicker;
    final int rightOffset = state.headerAlwaysVisibleCountersWidth;

    ViewPagerTopView topView = new ViewPagerTopView(context);
    topView.setSelectionColorId(ColorId.headerTabActive);
    topView.setTextFromToColorId(ColorId.headerTabInactiveText, ColorId.headerTabActiveText);
    topView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    topView.setSelectionChangeListener(this);

    adapter = new A(topView);

    if (this.needReactionSelector) {
      final int rightOffsetR = state.headerAlwaysVisibleCountersWidth + Screen.dp(needShowMoreButton ? 56: 0);
      reactionsSelectorRecyclerView = new ReactionsSelectorRecyclerView(context, state);
      reactionsSelectorRecyclerView.setLayoutParams(FrameLayoutFix.newParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        Gravity.CENTER_VERTICAL, 0, 0, rightOffsetR, 0));
      reactionsSelectorRecyclerView.setNeedDrawBorderGradient(rightOffset > 0);
      addView(reactionsSelectorRecyclerView);
      if (state.needShowReactionsPopupPicker) {
        reactionsSelectorRecyclerView.setVisibility(GONE);
      }
    } else {
      reactionsSelectorRecyclerView = null;
    }

    backButton = new BackHeaderButton(context);
    backButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
    backButton.setButtonFactor(this.needReactionSelector ? BackHeaderButton.TYPE_BACK : BackHeaderButton.TYPE_CLOSE);
    RippleSupport.setTransparentSelector(backButton);
    Views.setClickable(backButton);
    addView(backButton);

    if (needShowMoreButton) {
      moreButton = new ImageView(context);
      moreButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
      moreButton.setImageResource(R.drawable.baseline_small_arrow_down_24);
      moreButton.setScaleType(ImageView.ScaleType.CENTER);
      moreButton.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
      RippleSupport.setTransparentSelector(moreButton);
      Views.setClickable(moreButton);
      addView(moreButton);
    } else {
      moreButton = null;
    }

    setBackButtonAlpha(this.needReactionSelector ? 0f: 1f);

    recyclerView = new RecyclerView(context) {
      @Override
      protected void dispatchDraw (Canvas c) {
        super.dispatchDraw(c);
        if (shadowPaint1 == null) {
          return;
        }
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

  private float getReactionPickerHiddenZoneLeft () {
    final int x = (int) (reactionsSelectorRecyclerView != null ? reactionsSelectorRecyclerView.getTranslationX() : 0);
    final float width = MessageOptionsPagerController.getReactionsPickerRightHiddenWidth(state);
    return getMeasuredWidth() - width + x;
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    if (state.needShowReactionsPopupPicker) {
      c.drawRect(getReactionPickerHiddenZoneLeft(), 0, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.backgroundColor()));
    }
    super.dispatchDraw(c);
  }

  LinearGradient shader1;
  private Paint shadowPaint1;
  private final int shadowSize = Screen.dp(35);

  private int oldPaintsColor = 0;

  public void updatePaints (int color) {
    if (oldPaintsColor != color) {
      oldPaintsColor = color;
    } else return;

    shader1 = new LinearGradient(0, 0, shadowSize / 2f, 0, color, 0, Shader.TileMode.CLAMP);
    if (shadowPaint1 == null) {
      shadowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint1.setShader(shader1);
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
        float offset = (width - state.headerAlwaysVisibleCountersWidth - Screen.dp(56)) * (1f - positionOffset);
        if (needShowReactions && needShowViews) {
          getTopView().setItemTranslationX(1, (int) (Screen.dp(-8) * (1f - positionOffset)));
          getTopView().setItemTranslationX(2, (int) (Screen.dp(-8) * (1f - positionOffset)));
        } else if (needShowReactions || needShowViews) {
          getTopView().setItemTranslationX(1, (int) (Screen.dp(-8) * (1f - positionOffset)));
        }
        recyclerView.setTranslationX(offset);
        reactionsSelectorRecyclerView.setTranslationX(-width * positionOffset);
        backButton.setTranslationX(offset);
        if (moreButton != null) {
          moreButton.setTranslationX(offset);
        }
        setBackButtonAlpha(positionOffset);
        isScrollEnabled = false;
      } else {
        getTopView().setItemTranslationX(1, 0);
        getTopView().setItemTranslationX(2, 0);
        recyclerView.setTranslationX(0);
        reactionsSelectorRecyclerView.setTranslationX(-width);
        backButton.setTranslationX(0);
        if (moreButton != null) {
          moreButton.setTranslationX(0);
        }
        setBackButtonAlpha(1f);
        isScrollEnabled = true;
      }
      recyclerView.invalidate();
      invalidate();
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

      if (moreButton != null) {
        final float alphaMore = 1f - alpha;
        moreButton.setAlpha(alphaMore);
        if (alphaMore > 0f && moreButton.getVisibility() != VISIBLE) {
          moreButton.setVisibility(View.VISIBLE);
        }
        if (alphaMore == 0f && moreButton.getVisibility() != GONE) {
          moreButton.setVisibility(View.GONE);
        }
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
    return !(e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) && super.onTouchEvent(e)
      || (state.needShowReactionsPopupPicker && (e.getX() > getReactionPickerHiddenZoneLeft()));
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
  public View getView () {
    return this;
  }

  @Override
  public ViewPagerTopView getTopView () {
    return adapter.topView;
  }

  public BackHeaderButton getBackButton () {
    return backButton;
  }

  @Nullable public ImageView getMoreButton () {
    return moreButton;
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
