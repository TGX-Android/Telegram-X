/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.EmptyTextView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public abstract class MediaBottomBaseController<T> extends ViewController<T> {
  protected final MediaLayout mediaLayout;
  private final int titleRes;
  private String titleString;

  protected MediaBottomBaseController (MediaLayout context, int titleResource) {
    super(context.getContext(), context.tdlib());
    this.titleRes = titleResource;
    this.titleString = "";
    this.mediaLayout = context;
    initMetrics();
  }

  protected MediaBottomBaseController (MediaLayout context, String titleString) {
    super(context.getContext(), context.tdlib());
    this.titleRes = 0;
    this.titleString = titleString;
    this.mediaLayout = context;
    initMetrics();
  }

  public @TdApi.ChatAction.Constructors int getBroadcastingAction () {
    return TdApi.ChatActionCancel.CONSTRUCTOR;
  }

  @Override
  public CharSequence getName () {
    return titleRes != 0 ? Lang.getString(titleRes) : titleString;
  }

  public void setName (String name) {
    titleString = name;
    mediaLayout.getHeaderView().setTitle(this);
  }

  // Settings

  public boolean allowSpoiler () {
    return false;
  }

  @Override
  protected final int getHeaderTextColorId () {
    return R.id.theme_color_text;
  }

  @Override
  protected final int getHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected final int getHeaderIconColorId () {
    return R.id.theme_color_headerLightIcon;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inSearchMode()) {
      mediaLayout.getHeaderView().closeSearchMode(true, null);
      return true;
    }
    return false;
  }

  public boolean canMoveRecycler () {
    return !inTransformMode(); // aka isAnimating
  }

  public boolean supportsMediaGrouping () {
    return false;
  }

  public boolean ignoreStartHeightLimits() {
    return false;
  }

  // Metrics

  private int contentHeight, startHeight, currentHeight;

  private int lastHeightIncreaseCheck;
  private boolean isHeightIncreasing; // true if it increased

  protected final void initMetrics () { // FIXME make private again and move calling from constructor to some proper place
    contentHeight = getInitialContentHeight();
    resetStartHeights(true);
  }

  private int getBarHeightIfAvailable () {
    return mediaLayout.inSpecificMode() ? 0 : MediaBottomBar.getBarHeight();
  }

  private void resetStartHeights (boolean initial) {
    startHeight = Math.min(contentHeight + getBarHeightIfAvailable() + HeaderView.getSize(false), Math.min(getMaxStartHeight(), getMaxHeight()));
    setCurrentHeight(getRecyclerScrollY() > 0 || lastIsExpanded ? getMaxHeight() : startHeight, !initial);
  }

  public final int getStartHeight () {
    return startHeight;
  }

  public final int getCurrentWidth () {
    return mediaLayout.getCurrentContentWidth();
  }

  public final int getCurrentHeight () {
    return currentHeight;
  }

  public final int getContentHeight () {
    return contentHeight;
  }

  // Metrics (internal)

  protected int getInitialContentHeight () {
    return getMaxInitialContentHeight();
  }

  protected final int getMaxInitialContentHeight () {
    return Screen.smallestSide() - HeaderView.getSize(false);
  }

  private int getMaxStartHeight () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return Math.min(getContentHeight() + getBarHeightIfAvailable() + HeaderView.getSize(false), Math.min(getCurrentWidth() + getBarHeightIfAvailable(), getMaxStartHeightLimit()));
  }

  protected int getMaxStartHeightLimit () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return getMaxHeight() - MediaBottomBar.getBarHeight() / 4;
  }

  protected int getRecyclerHeaderOffset () {
    return 0;
  }

  protected boolean canExpandHeight () {
    return true;
  }

  protected boolean canMinimizeHeight () {
    return true;
  }

  public boolean showExitWarning (boolean isExitingSelection) {
    // override
    return false;
  }

  private int findYForStaticView (int viewHeight) {
    return (int) recyclerView.getTranslationY() + getRecyclerHeaderOffset() + (Math.max(startHeight, getCurrentHeight()) - getRecyclerHeaderOffset()) / 2 - getBarHeightIfAvailable() - viewHeight / 2;
  }

  // Recycler position

  public void onViewportChanged (int width, int height) {
    if (recyclerView != null) {
      resetStartHeights(false);
      updateRecyclerTop(height);
    }
  }

  private void updateRecyclerTop () {
    if (contentView != null) {
      int currentHeight = contentView.getMeasuredHeight();
      updateRecyclerTop(currentHeight == 0 ? getTargetHeight() : currentHeight);
    }
  }

  private static int getTargetHeight () {
    return Screen.currentHeight() - HeaderView.getTopOffset();
  }

  private void updateRecyclerTop (int height) {
    if (recyclerView != null) {
      float top = height - currentHeight;
      recyclerView.setTranslationY(top);
      onRecyclerTopUpdate(top);
    }
  }

  // General pattern

  protected MediaContentView contentView;
  private View progressView;
  protected MediaBottomBaseRecyclerView recyclerView;
  private EmptyTextView emptyView;

  protected final MediaContentView buildContentView (boolean needProgress) {
    contentView = new MediaContentView(context());
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.setBoundController(this);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = HeaderView.getSize(false);
    params.bottomMargin = HeaderView.getTopOffset();

    recyclerView = new MediaBottomBaseRecyclerView(context());
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position != RecyclerView.NO_POSITION && recyclerView.getAdapter() != null && position == recyclerView.getAdapter().getItemCount() - 1 && mediaLayout.getCounterFactor() == 1f) {
          outRect.set(0, 0, 0, MediaBottomBar.getBarHeight());
        } else {
          outRect.setEmpty();
        }
      }
    });
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    ViewSupport.setThemedBackground(recyclerView, getRecyclerBackgroundColorId());
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 150l));
    recyclerView.setLayoutParams(params);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE) {
          hideSoftwareKeyboard();
        }
      }
    });
    contentView.addView(recyclerView);

    if (needProgress) {
      params = FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      params.topMargin = HeaderView.getSize(false);
      progressView = Views.simpleProgressView(context(), params);
      progressView.setTranslationY(findYForStaticView(Screen.dp(48f)));
      contentView.addView(progressView);
    }

    updateRecyclerTop();

    return contentView;
  }

  public void onRecyclerFirstMovement () {

  }

  protected @ThemeColorId int getRecyclerBackgroundColorId () {
    return R.id.theme_color_filling;
  }

  public void dispatchRecyclerTouchEvent (MotionEvent e) {
    recyclerView.processEvent(e);
  }

  public int getRecyclerScrollY () {
    if (recyclerView == null) {
      return 0;
    }
    RecyclerView.LayoutManager manager = getLayoutManager();
    if (!(manager instanceof LinearLayoutManager)) {
      return 0;
    }
    RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
    if (!(adapter instanceof MeasuredAdapterDelegate)) {
      return 0;
    }
    int firstPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
    if (firstPosition == RecyclerView.NO_POSITION) {
      return 0;
    }
    int scrollTop = ((MeasuredAdapterDelegate) adapter).measureScrollTop(firstPosition);
    View view = manager.findViewByPosition(firstPosition);
    if (view != null) {
      scrollTop -= view.getTop();
    }
    return scrollTop;
  }

  protected final void setLayoutManager (RecyclerView.LayoutManager manager) {
    recyclerView.setLayoutManager(manager);
  }

  protected final RecyclerView.LayoutManager getLayoutManager () {
    return recyclerView.getLayoutManager();
  }

  protected final void addItemDecoration (RecyclerView.ItemDecoration decoration) {
    recyclerView.addItemDecoration(decoration);
  }

  protected final void setAdapter (RecyclerView.Adapter<?> adapter) {
    recyclerView.setAdapter(adapter);
  }

  protected final void dispatchError (final String error, final String resolveErrorButtonText, final View.OnClickListener onResolveButtonClick, final boolean animated) {
    runOnUiThread(() -> showError(error, resolveErrorButtonText, onResolveButtonClick, animated));
  }

  public void updateExtraSpacingDecoration () {
    recyclerView.invalidateItemDecorations();
  }

  protected final void showError (@StringRes int errorRes, @StringRes int resolveErrorButtonRes, View.OnClickListener onResolveButtonClick, boolean animated) {
    showError(Lang.getString(errorRes), Lang.getString(resolveErrorButtonRes), onResolveButtonClick, animated);
  }

  protected void hideError () {
    if (emptyView != null) {
      emptyView.setAlpha(0f);
    }
  }

  // Called when removed from view tree
  public final void resetState () {
    // force scroll to top
  }

  protected void showError (String error, @Nullable String resolveButtonText, @Nullable View.OnClickListener onResolveButtonClick, boolean animated) {
    if (emptyView == null) {
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      params.topMargin = HeaderView.getSize(false);

      emptyView = new EmptyTextView(context());
      emptyView.setLayoutParams(params);
      emptyView.setTranslationY(findYForStaticView(Screen.dp(18f)));
      contentView.addView(emptyView);
    } else {
      animated = false;
    }
    emptyView.setText(error);
    if (!animated || progressView == null) {
      emptyView.setAlpha(1f);
      return;
    }
    emptyView.setAlpha(0f);
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> {
      float factor = AnimatorUtils.getFraction(animation);
      if (factor <= .5f) {
        factor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor / .5f);
        progressView.setAlpha(1f - factor);
      } else {
        if (progressView.getAlpha() != 0f) {
          progressView.setAlpha(0f);
        }
        factor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((factor - .5f) / .5f);
        emptyView.setAlpha(factor);
      }
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        contentView.removeView(progressView);
        progressView = null;
      }
    });
    animator.setDuration(300l);
    animator.start();
  }

  protected static final long HIDE_PROGRESS_DURATION = 140l;

  protected void hideProgress () {
    hideProgress(null);
  }

  protected void hideProgress (final Runnable after) {
    if (progressView == null) {
      return;
    }
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> progressView.setAlpha(1f - AnimatorUtils.getFraction(animation)));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        contentView.removeView(progressView);
        progressView = null;
        if (after != null) {
          after.run();
        }
      }
    });
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.setDuration(HIDE_PROGRESS_DURATION);
    animator.start();
  }

  private void applyStartHeight (int newStartHeight) {
    if (this.startHeight != newStartHeight) {
      this.startHeight = this.currentHeight = newStartHeight;
      updateRecyclerTop();
      mediaLayout.onContentHeightChanged();
    }
  }

  protected void expandStartHeight (MeasuredAdapterDelegate adapter) {
    this.contentHeight = adapter.measureHeight(-1);
    int newStartHeight = getMaxStartHeight();

    if (newStartHeight <= startHeight) {
      return;
    }

    if (isMoving || animatingHeight || currentHeight > startHeight) {
      startHeight = newStartHeight;
      return;
    }

    final float fromHeight = startHeight;
    final float heightDiff = newStartHeight - startHeight;

    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> applyStartHeight(Math.round(fromHeight + heightDiff * AnimatorUtils.getFraction(animation))));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.setDuration(150l);
    animator.start();
  }

  private boolean lastIsExpanded;

  private void setCurrentHeight (int height, boolean updateMediaLayout) {
    if (this.currentHeight != height) {
      this.currentHeight = height;

      final int maxHeight = getMaxHeight();

      lastIsExpanded = height == maxHeight;

      if (Math.abs(height - lastHeightIncreaseCheck) >= Screen.getTouchSlop()) {
        isHeightIncreasing = height > lastHeightIncreaseCheck;
        lastHeightIncreaseCheck = height;
      }

      updateRecyclerTop();
      mediaLayout.setContentVisible(currentHeight < maxHeight);

      if (updateMediaLayout) {
        if (currentHeight == startHeight) {
          mediaLayout.setBottomBarFactor(1f);
          mediaLayout.setHeaderFactor(0f);
        } else if (currentHeight < startHeight) {
          mediaLayout.setBottomBarFactor((float) currentHeight / (float) startHeight);
          mediaLayout.setHeaderFactor(0f);
        } else {
          float barFactor = (float) (getMaxHeight() - currentHeight) / (float) (maxHeight - startHeight);
          mediaLayout.setBottomBarFactor(barFactor);
          mediaLayout.setHeaderFactor(1f - barFactor);
        }
      }
    }
  }

  protected void onUpdateBottomBarFactor (float bottomBarFactor, float counterFactor, float y) {
  }

  private boolean animatingHeight;
  private ValueAnimator heightAnimator;

  protected void animateCurrentHeight (int toHeight, boolean fast) {
    if (animatingHeight) {
      animatingHeight = false;
      if (heightAnimator != null) {
        heightAnimator.cancel();
        heightAnimator = null;
      }
    }

    if (currentHeight == toHeight) {
      return;
    }

    animatingHeight = true;

    final float fromHeight = this.currentHeight;
    final float heightDiff = toHeight - fromHeight;

    heightAnimator = AnimatorUtils.simpleValueAnimator();
    heightAnimator.addUpdateListener(animation -> {
      if (animatingHeight) {
        float factor = AnimatorUtils.getFraction(animation);
        setCurrentHeight(Math.round(fromHeight + heightDiff * factor), true);
      }
    });
    heightAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        animatingHeight = false;
      }
    });
    heightAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    heightAnimator.setDuration(fast ? 150l : MediaLayout.REVEAL_DURATION);
    heightAnimator.start();
  }

  // Height expanding

  public boolean isAnimating () {
    return animatingHeight || useBottomBarChange;
  }

  public boolean isInsideRecyclerView (float x, float y) {
    return y >= recyclerView.getTranslationY() && y <= recyclerView.getTranslationY() + recyclerView.getMeasuredHeight();
  }

  public static int getMaxHeight () {
    return getTargetHeight(); //  - HeaderView.getSize();
  }

  private int moveStartHeight;
  private boolean isMoving;

  private boolean useBottomBarChange;
  private int barChangeFromHeight;
  private int barChangeHeightDiff;

  public void onBottomBarAnimationStart (boolean beforeExit) {
    if (beforeExit) {
      useBottomBarChange = true;
      barChangeFromHeight = currentHeight;
      barChangeHeightDiff = -currentHeight;
    }
  }

  public void onBottomBarAnimationFraction (float factor) {
    if (useBottomBarChange) {
      setCurrentHeight(barChangeFromHeight + (int) ((float) barChangeHeightDiff * factor), false);
    }
  }

  public void onBottomBarAnimationComplete () {
    useBottomBarChange = false;
  }

  public void onRecyclerMovementStarted () {
    moveStartHeight = currentHeight;
    isMoving = true;
  }

  public boolean handleFling (boolean toUp) {
    if (isMoving) {
      isMoving = false;
      useBottomBarChange = false;

      float expandFactor = currentHeight < startHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - startHeight);

      mediaLayout.prepareHeader();
      if (toUp) {
        if (canExpandHeight()) {
          animateCurrentHeight(getMaxHeight(), true);
        } else {
          animateCurrentHeight(startHeight, true);
        }
      } else if (expandFactor >= .2f || !canMinimizeHeight()) {
        animateCurrentHeight(startHeight, false);
      } else {
        useBottomBarChange = true;
        barChangeFromHeight = currentHeight;
        barChangeHeightDiff = -currentHeight;
        mediaLayout.hideBottomBarAndDismiss();
      }

      return true;
    }
    return false;
  }

  public void onRecyclerMovementFinished () {
    if (isMoving) {
      isMoving = false;
      useBottomBarChange = false;

      float openFactor = currentHeight >= startHeight ? 1f : (float) currentHeight / (float) startHeight;
      float expandFactor = currentHeight < startHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - startHeight);

      mediaLayout.prepareHeader();

      if (openFactor <= .45f && !isHeightIncreasing) {
        useBottomBarChange = true;
        barChangeFromHeight = currentHeight;
        barChangeHeightDiff = -currentHeight;
        mediaLayout.hideBottomBarAndDismiss();
      } else if (expandFactor >= .35f && isHeightIncreasing) {
        animateCurrentHeight(getMaxHeight(), true);
      } else {
        animateCurrentHeight(startHeight, false);
      }
    }
  }

  private boolean recyclerEverMoved;

  public boolean moveRecyclerView (float diffY) {
    int maxHeight = canExpandHeight() ? getMaxHeight() : startHeight;
    int newHeight = Math.min(maxHeight, moveStartHeight - (int) diffY);

    if (newHeight < startHeight && !canMinimizeHeight()) {
      return false;
    }

    if (currentHeight == newHeight) {
      return newHeight == maxHeight;
    }

    if (!recyclerEverMoved && newHeight > startHeight) {
      recyclerEverMoved = true;
      onRecyclerFirstMovement();
    }

    if (currentHeight > startHeight) {
      mediaLayout.prepareHeader();
    }
    setCurrentHeight(newHeight, true);

    return newHeight == maxHeight;
  }

  // Override

  protected void preload (Runnable after, long timeout) {
    // do heavy load
    if (after != null) {
      after.run();
    }
  }

  @CallSuper
  protected void onRecyclerTopUpdate (float top) {
    if (progressView != null) {
      progressView.setTranslationY(findYForStaticView(progressView.getLayoutParams().height));
    }
    if (emptyView != null) {
      emptyView.setTranslationY(findYForStaticView(Screen.dp(18f)));
    }
    // Use to align overlay views
  }

  public void expandFully () {
    mediaLayout.prepareHeader();
    animateCurrentHeight(getMaxHeight(), false);
  }

  public boolean isExpanded () {
    return currentHeight == getMaxHeight();
  }

  public void collapseToStart () {
    int scrollY = getRecyclerScrollY();
    if (scrollY != 0) {
      recyclerView.smoothScrollBy(0, -scrollY);
    }
    animateCurrentHeight(startHeight, false);
  }

  protected void forceScrollRecyclerToTop () {
    RecyclerView.LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      ((LinearLayoutManager) manager).scrollToPositionWithOffset(0, 0);
    }
  }

  protected void onCompleteShow (boolean isPopup) {
    // Do all heavy work like layout or etc, no animation will lag
  }

  protected void onMultiSendPress (View view, @NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
    // Send all selected shit
  }

  protected void addCustomItems (View view, @NonNull List<HapticMenuHelper.MenuItem> hapticItems) {
    // Add specific items
  }

  protected boolean canRemoveMarkdown () {
    return false;
  }

  protected void onCancelMultiSelection () {
    // unselect all selected shit
  }

  protected ViewGroup createCustomBottomBar () {
    return null;
  }

  @Override
  public void destroy () {
    super.destroy();
    if (recyclerView != null) {
      Views.destroyRecyclerView(recyclerView);
    }
  }
}