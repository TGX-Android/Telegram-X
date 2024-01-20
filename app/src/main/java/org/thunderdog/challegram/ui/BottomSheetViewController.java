package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.LickView;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.widget.FrameLayoutFix;

public abstract class BottomSheetViewController<T> extends ViewPagerController<T> implements
  PopupLayout.TouchSectionProvider, PopupLayout.PopupHeightProvider {

  protected FrameLayoutFix wrapView;
  protected RelativeLayout contentView;
  protected View fixView;
  protected View pagerInFrameLayoutFix;

  protected abstract int getHeaderHeight ();
  protected abstract int getContentOffset ();
  protected abstract HeaderView onCreateHeaderView ();
  protected void onBeforeCreateView () {};
  protected void onAfterCreateView () {};

  protected int getHideByScrollBorder () {
    return Screen.dp(150);
  }

  protected final int getHeaderHeight (boolean withOffset) {
    return getHeaderHeight() + (withOffset ? HeaderView.getTopOffset() : 0);
  }

  protected final int getContentMinHeight () {
    return (getTargetHeight() - getHeaderHeight(true) - getContentOffset());
  }

  protected final int getContentVisibleHeight () {
    return getTargetHeight() - (getTopEdge() + getHeaderHeight(true));
  }


  public BottomSheetViewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected View onCreateView (Context context) {
    onBeforeCreateView();

    headerView = onCreateHeaderView();

    contentView = new RelativeLayout(context) {
      @Override
      protected void onDraw (Canvas canvas) {
        if (headerView != null) {
          canvas.drawRect(0, headerTranslationY, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.getColor(getBackgroundColorId())));
        }
        super.onDraw(canvas);
      }

      @Override
      protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
        if (child == pagerInFrameLayoutFix && headerView != null) {
          canvas.save();
          canvas.clipRect(0, headerTranslationY + HeaderView.getTopOffset(), getMeasuredWidth(), getMeasuredHeight());
          boolean result = super.drawChild(canvas, child, drawingTime);
          canvas.restore();
          return result;
        } else {
          return super.drawChild(canvas, child, drawingTime);
        }
      }
    };
    contentView.setWillNotDraw(false);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addThemeInvalidateListener(contentView);

    FrameLayout.LayoutParams fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f));
    fp.topMargin = getHeaderHeight();
    fixView = new View(context);
    ViewSupport.setThemedBackground(fixView, ColorId.background, this);
    fixView.setLayoutParams(fp);

    wrapView = new FrameLayoutFix(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent e) {
        boolean b = (e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < (getTopEdge() + HeaderView.getTopOffset()));
        return b || super.onInterceptTouchEvent(e);
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        boolean b = (e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < (getTopEdge() + HeaderView.getTopOffset()));
        return b && super.onTouchEvent(e);
      }

      private int oldHeight = -1;

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        post(() -> {
          final int height = getTargetHeight();
          if (height != oldHeight) {
            invalidateAllItemDecorations();
            boolean disallowKeyboardHide = isDisallowKeyboardHideOnPageScrolled();
            setDisallowKeyboardHideOnPageScrolled(true);
            onPageScrolled(currentMediaPosition, currentPositionOffset, 0);
            setDisallowKeyboardHideOnPageScrolled(disallowKeyboardHide);
            oldHeight = height;
          }
        });
      }

      /*
      @Override
      protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int headerHeight = getHeaderHeight(true);
        int DP = Screen.dp(1);

        canvas.drawRect(0, 0, getMeasuredWidth(), getTargetHeight(), Paints.strokeBigPaint(Color.RED));

        canvas.drawRect(DP, headerHeight, getMeasuredWidth() - DP, headerHeight + getContentOffset(), Paints.strokeBigPaint(Color.GREEN));
        canvas.drawRect(DP, headerHeight + getContentOffset(), getMeasuredWidth() - DP, headerHeight + getContentOffset() + getContentMinHeight(), Paints.strokeBigPaint(Color.GREEN));

        canvas.drawRect(DP * 2, getMeasuredHeight() - getHideByScrollBorder(), getMeasuredWidth() - DP * 2, getMeasuredHeight(), Paints.strokeBigPaint(Color.BLUE));
      }
      */
    };

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = getHeaderHeight() + HeaderView.getTopOffset();
    pagerInFrameLayoutFix = super.onCreateView(context);
    pagerInFrameLayoutFix.setLayoutParams(params);
    contentView.addView(pagerInFrameLayoutFix);

    wrapView.addView(fixView);
    wrapView.addView(contentView);
    wrapView.addView(headerView);
    wrapView.setWillNotDraw(false);
    addThemeInvalidateListener(wrapView);
    if (HeaderView.getTopOffset() > 0) {
      lickView = new LickView(context);
      addThemeInvalidateListener(lickView);
      lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
      wrapView.addView(lickView);
    }

    onAfterCreateView();

    return wrapView;
  }

  @Nullable
  protected ViewController<?> findCurrentCachedController () {
    return findCachedControllerByPosition(getViewPager().getCurrentItem());
  }

  @Nullable
  protected ViewController<?> findCachedControllerByPosition (int position) {
    if (getViewPager().getAdapter() instanceof ViewPagerController.ViewPagerAdapter) {
      ViewPagerController.ViewPagerAdapter adapter = (ViewPagerController.ViewPagerAdapter) (getViewPager().getAdapter());
      return adapter.getCachedItemByPosition(position);
    }
    return null;
  }


  protected int getBackgroundColorId () {
    return ColorId.background;
  }

  private boolean ignoreAnyPagerScrollEventsBecauseOfMovements;

  protected void setIgnoreAnyPagerScrollEventsBecauseOfMovements (boolean ignore) {
    this.ignoreAnyPagerScrollEventsBecauseOfMovements = ignore;
  }

  protected boolean getIgnoreAnyPagerScrollEventsBecauseOfMovements () {
    return ignoreAnyPagerScrollEventsBecauseOfMovements;
  }

  protected boolean canHideByScroll () {
    return false;
  }

  protected int currentMediaPosition;
  protected float currentPositionOffset;
  protected int checkedPosition = -1, checkedBasePosition = -1;

  protected void invalidateCachedPosition () {
    checkedPosition = -1;
    checkedBasePosition = -1;
  }

  protected float lastHeaderPosition;

  protected void checkHeaderPosition (RecyclerView recyclerView) {
    lastHeaderPosition = Math.max(Views.getRecyclerFirstElementTop(recyclerView), 0) + HeaderView.getTopOffset();
    setHeaderPosition(lastHeaderPosition);
    /*
    View view = null;
    if (recyclerView != null) {
      view = recyclerView.getLayoutManager().findViewByPosition(0);
    }
    int top = HeaderView.getTopOffset();
    if (view != null) {
      top = Math.max(view.getTop() + (recyclerView != null ? recyclerView.getTop() : 0) + HeaderView.getTopOffset(), HeaderView.getTopOffset());
    }

    if (headerView != null) {
      setHeaderPosition(lastHeaderPosition = top);
    }
    */
  }

  protected int getTargetHeight () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return Screen.currentHeight()
        + (context.isKeyboardVisible() ? Keyboard.getSize() : 0)
        - (Screen.needsKeyboardPadding(context) ? Screen.getNavigationBarFrameDifference() : 0)
        + (context.isKeyboardVisible() && Device.NEED_ADD_KEYBOARD_SIZE ? Screen.getNavigationBarHeight() : 0);
    } else {
      return Screen.currentHeight();
    }
  }

  protected void invalidateAllItemDecorations () {
    for (int i = 0; i < getPagerItemCount(); i++) {
      ViewController<?> c = findCachedControllerByPosition(i);
      if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        RecyclerView customRecyclerView = ((BottomSheetBaseControllerPage) c).getRecyclerView();
        if (customRecyclerView != null) {
          customRecyclerView.invalidateItemDecorations();
        }
      }
    }
  }

  protected float headerBackgroundFactor;
  protected float headerTranslationY;

  protected void setHeaderPosition (float y) {
    y = Math.max(y, HeaderView.getTopOffset());
    headerTranslationY = y;
    float realHeaderOffset = y;
    if (headerView != null) {
      headerView.setTranslationY(realHeaderOffset);
    }
    fixView.setTranslationY(realHeaderOffset);
    contentView.invalidate();
    fixView.invalidate();
    if (lickView != null) {
      final int topOffset = HeaderView.getTopOffset();
      final float top = y - topOffset;
      lickView.setTranslationY(realHeaderOffset - topOffset);
      float factor = top > topOffset ? 0f : 1f - ((float) top / (float) topOffset);
      lickView.setFactor(factor);
      onUpdateLickViewFactor(factor);
      // headerView.getFilling().setShadowAlpha(factor);
      setHeaderBackgroundFactor(factor);
    }
  }

  protected void setHeaderBackgroundFactor (float headerBackgroundFactor) {
    this.headerBackgroundFactor = headerBackgroundFactor;
  }

  protected int getTopEdge () {
    return Math.max(0, (int) ((headerView != null ? headerTranslationY : 0) - HeaderView.getTopOffset()));
  }

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return headerView != null && y < headerTranslationY - HeaderView.getSize(true);
  }

  @Override
  public int getCurrentPopupHeight () {
    return (getTargetHeight() - getTopEdge() - (int) ((float) HeaderView.getTopOffset())) + Math.max(wrapView.getMeasuredHeight() - getTargetHeight(), 0);
  }

  public int maxItemsScrollYOffset () {
    return maxItemsScrollY();
  }

  public int maxItemsScrollY () {
    return getContentOffset();
  }

  public void checkContentScrollY (BottomSheetBaseControllerPage c) {
    int maxScrollY = maxItemsScrollYOffset();
    int scrollY = (int) (getContentOffset() - (headerView != null ? headerTranslationY : 0) + HeaderView.getTopOffset()); //();
    if (c != null) {
      c.ensureMaxScrollY(scrollY, maxScrollY);
    }
  }

  private static final boolean PREVENT_HEADER_ANIMATOR = false; // TODO

  @Override
  protected boolean launchCustomHeaderTransformAnimator (boolean open, int transformMode, Animator.AnimatorListener listener) {
    return PREVENT_HEADER_ANIMATOR && open && getTopEdge() > 0;
  }

  protected int calculateTotalHeight () {
    return getTargetHeight() - (getContentOffset() + HeaderView.getTopOffset());
  }

  protected void checkContentScrollY (int position) {
    if (getViewPager().getAdapter() instanceof ViewPagerController.ViewPagerAdapter) {
      ViewPagerController.ViewPagerAdapter adapter = (ViewPagerController.ViewPagerAdapter) (getViewPager().getAdapter());
      ViewController<?> controller = adapter.getCachedItemByPosition(position);
      if (controller instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        checkContentScrollY((BottomSheetBaseControllerPage) controller);
      }
    }
  }

  @Override
  protected void setCurrentPagerPosition (int position, boolean animated) {
    if (headerCell != null && animated) {
      headerCell.getTopView().setFromTo(getViewPager().getCurrentItem(), position);
    }
    super.setCurrentPagerPosition(position, animated);
  }

  @Override
  protected String[] getPagerSections () {
    return null;
  }

  @Override
  public View getCustomHeaderCell () {
    return null;
  }

  @Override
  public void destroy () {
    super.destroy();
    context().removeFullScreenView(this, false);
  }



  // PopupLayout

  private PopupLayout popupLayout;
  private boolean openLaunched;
  protected boolean isFirstCreation = true;

  public void show () {
    if (tdlib == null) {
      return;
    }
    popupLayout = new PopupLayout(context()) {
      @Override
      public void onCustomShowComplete () {
        super.onCustomShowComplete();
        isFirstCreation = false;
        if (!isDestroyed()) {
          BottomSheetViewController.this.onCustomShowComplete();
          ViewController<?> c = getCurrentPagerItem();
          if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
            RecyclerView r = ((BottomSheetBaseControllerPage) c).getRecyclerView();
            if (r != null) {
              r.invalidateItemDecorations();
              checkHeaderPosition(r);
            }
          }
        }
      }
    };

    setupPopupLayout(popupLayout);
    getValue();
    context().addFullScreenView(this, false);
  }

  protected void onCustomShowComplete () {

  }

  protected void setupPopupLayout (PopupLayout popupLayout) {
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(true);
    popupLayout.setTouchProvider(this);
  }

  protected void launchOpenAnimation () {
    if (!openLaunched) {
      openLaunched = true;
      popupLayout.showSimplePopupView(getValue(), calculateTotalHeight());
    }
  }

  public void hidePopupWindow (boolean animated) {
    popupLayout.hideWindow(animated);
  }

  public void setDismissListener (PopupLayout.DismissListener l) {
    popupLayout.setDismissListener(l);
  }

  protected PopupLayout getPopupLayout () {
    return popupLayout;
  }



  // LickView

  private @Nullable LickView lickView;

  protected float getLickViewFactor () {
    return lickView != null ? lickView.getFactor() : 0;
  }

  protected void onUpdateLickViewFactor (float factor) {

  }

  protected void setLickViewColor (int color) {
    if (lickView != null) {
      lickView.setHeaderBackground(color);
    }
  }





  // Custom tooltip manager, used in case the popup window is opened on top of all content.
  // Might be worth moving this to the ViewController class

  private TooltipOverlayView tooltipOverlayView;

  public TooltipOverlayView tooltipManager () {
    if (tooltipOverlayView == null) {
      tooltipOverlayView = new TooltipOverlayView(context());
      tooltipOverlayView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      tooltipOverlayView.setAvailabilityListener((overlayView, hasChildren) -> {
        if (hasChildren) {
          if (tooltipOverlayView.getParent() != null)
            return;
          addToViewController(tooltipOverlayView);
        } else {
          removeFromViewController(tooltipOverlayView);
        }
      });
    }
    return tooltipOverlayView;
  }

  public void addToViewController (View view) {
    wrapView.addView(view);
  }

  public void removeFromViewController (View view) {
    wrapView.removeView(view);
  }



  // Default listeners ind decorators

  protected void setDefaultListenersAndDecorators (BottomSheetBaseControllerPage controller) {
    RecyclerView recyclerView = controller.getRecyclerView();
    recyclerView.setVerticalScrollBarEnabled(false);
    recyclerView.addOnScrollListener(new ScrollListener(this));
    recyclerView.addItemDecoration(new ContentDecoration(this, controller));
    recyclerView.addOnLayoutChangeListener(new LayoutChangeListener(this));
    addThemeInvalidateListener(recyclerView);
    checkContentScrollY(controller);
    if (canHideByScroll()) {
      UI.post(() -> recyclerView.scrollBy(0, getContentMinHeight() + getHeaderHeight()));
    }
  }

  public static class ContentDecoration extends RecyclerView.ItemDecoration {
    private final BottomSheetViewController<?> controller;
    private final BottomSheetBaseControllerPage page;

    public ContentDecoration (BottomSheetViewController<?> controller, BottomSheetBaseControllerPage page) {
      this.controller = controller;
      this.page = page;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, RecyclerView parent, @NonNull RecyclerView.State state) {
      final int position = parent.getChildAdapterPosition(view);
      final int itemCount = parent.getAdapter().getItemCount();
      final boolean isUnknown = position == RecyclerView.NO_POSITION;
      int top = 0, bottom = 0;

      if (position == 0 || isUnknown) {
        top = controller.canHideByScroll() ?
          (controller.getTargetHeight() - HeaderView.getTopOffset()):
          (controller.getContentOffset());
      }
      if (position == itemCount - 1 || isUnknown) {
        final int itemsHeight = isUnknown ? view.getMeasuredHeight() : page.getItemsHeight(parent);
        final int parentHeight = parent.getMeasuredHeight();
        bottom = parentHeight - itemsHeight;
      }

      outRect.set(
        0, page.needTopDecorationOffsets(parent) ? Math.max(top, 0) : 0,
        0, page.needBottomDecorationOffsets(parent) ? Math.max(0, bottom) : 0);
    }
  }

  public static class ScrollListener extends RecyclerView.OnScrollListener {
    private final BottomSheetViewController<?> controller;

    public ScrollListener (BottomSheetViewController<?> controller) {
      this.controller = controller;
    }

    private boolean ignoreScrollChangeState;

    @Override
    public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
      super.onScrollStateChanged(recyclerView, newState);
      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        if (ignoreScrollChangeState) {
          ignoreScrollChangeState = false;
          return;
        }

        ViewController<?> c = controller.findCurrentCachedController();
        boolean canHideByScroll = controller.canHideByScroll();
        int contentOffset = controller.getContentOffset();
        int topEdge = controller.getTopEdge();

        if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
          BottomSheetBaseControllerPage ci = (BottomSheetBaseControllerPage) c;
          if (ci.getRecyclerView() == recyclerView && !controller.getIgnoreAnyPagerScrollEventsBecauseOfMovements()) {
            if (controller.getLickViewFactor() != 0f && controller.getLickViewFactor() != 1f) {
              ci.onScrollToTopRequested();
            } else if (canHideByScroll && (topEdge > contentOffset)) {
              if (controller.getContentVisibleHeight() > controller.getHideByScrollBorder()) {
                ci.getRecyclerView().smoothScrollBy(0, topEdge - contentOffset);
                ignoreScrollChangeState = true;
              } else {
                controller.hidePopupWindow(true);
              }
            }
          }
        }
      }
    }

    @Override
    public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
      ViewController<?> c = controller.findCurrentCachedController();
      boolean canHideByScroll = controller.canHideByScroll() && recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING;
      int contentOffset = controller.getContentOffset();
      int topEdge = controller.getTopEdge();

      if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        BottomSheetBaseControllerPage ci = (BottomSheetBaseControllerPage) c;
        if (ci.getRecyclerView() == recyclerView && (!controller.getIgnoreAnyPagerScrollEventsBecauseOfMovements())) {
          controller.checkHeaderPosition(recyclerView);
          if (canHideByScroll && (topEdge > contentOffset)) {
            if (!(controller.getContentVisibleHeight() > controller.getHideByScrollBorder())) {
              controller.hidePopupWindow(true);
            }
          }
        }
      }
    }
  }

  public static class LayoutChangeListener implements View.OnLayoutChangeListener {
    private final BottomSheetViewController<?> controller;

    public LayoutChangeListener (BottomSheetViewController<?> controller) {
      this.controller = controller;
    }

    @Override
    public void onLayoutChange (View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
      ViewController<?> c = controller.findCurrentCachedController();
      if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        BottomSheetBaseControllerPage ci = (BottomSheetBaseControllerPage) c;
        if (ci.getRecyclerView() == view && controller.currentPositionOffset == 0f) {
          controller.checkHeaderPosition((RecyclerView) view);
        }
      }
    }
  }



  //

  public interface BottomSheetBaseControllerPage {
    void onScrollToTopRequested ();
    void onScrollToBottomRequested ();
    void ensureMaxScrollY (int scrollY, int maxScrollY);
    RecyclerView getRecyclerView ();

    int getItemsHeight (RecyclerView parent);
    boolean needTopDecorationOffsets (RecyclerView parent);
    boolean needBottomDecorationOffsets (RecyclerView parent);
  }



  //

  public static abstract class BottomSheetBaseRecyclerViewController<T> extends RecyclerViewController<T> implements BottomSheetBaseControllerPage {
    public BottomSheetBaseRecyclerViewController (Context context, Tdlib tdlib) {
      super(context, tdlib);
    }

    public abstract int getItemsHeight (RecyclerView parent);

    public boolean needTopDecorationOffsets (RecyclerView parent) {
      return true;
    }

    public boolean needBottomDecorationOffsets (RecyclerView parent) {
      return true;
    }

    public final void ensureMaxScrollY (int scrollY, int maxScrollY) {
      CustomRecyclerView recyclerView = getRecyclerView();
      if (recyclerView != null) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (scrollY < maxScrollY) {
          manager.scrollToPositionWithOffset(0, -scrollY);
          return;
        }

        int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == 0 || firstVisiblePosition == -1) {
          View view = manager.findViewByPosition(0);
          if (view != null) {
            int top = view.getTop();
            if (top > 0) {
              manager.scrollToPositionWithOffset(0, -maxScrollY);
            }
          } else {
            manager.scrollToPositionWithOffset(0, -maxScrollY);
          }
        }
      }
    }

    public void onScrollToBottomRequested () {
      CustomRecyclerView recyclerView = getRecyclerView();
      if (recyclerView == null) {
        return;
      }

      recyclerView.stopScroll();
      recyclerView.smoothScrollToPosition(0);
    }
  }
}
