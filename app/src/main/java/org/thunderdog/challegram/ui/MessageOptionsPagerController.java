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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewReactionsCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class MessageOptionsPagerController extends ViewPagerController<Void> implements
  FactorAnimator.Target, PopupLayout.PopupHeightProvider,
  View.OnClickListener, Menu, PopupLayout.TouchSectionProvider,
  DrawableProvider, Counter.Callback, ReactionsSelectorRecyclerView.ReactionSelectDelegate, TextColorSet {

  private final Options options;
  private final TGMessage message;

  private final boolean needShowOptions;
  private final boolean needShowViews;
  private final boolean needShowReactions;

  private final TdApi.MessageReaction[] reactions;
  private final ViewPagerTopView.Item[] counters;
  private int baseCountersWidth;
  private int startPage = 0;

  public MessageOptionsPagerController (Context context, Tdlib tdlib, Options options, TGMessage message, TdApi.ReactionType defaultReactionType) {
    super(context, tdlib);
    this.options = options;
    this.message = message;

    final boolean needHideViews = !message.canGetViewers() || message.isUnread() || message.noUnread();
    this.reactions = message.getMessageReactions().getReactions();
    this.needShowOptions = options != null;
    this.needShowViews = !needHideViews;
    this.needShowReactions = reactions != null && message.canGetAddedReactions() && message.getMessageReactions().getTotalCount() > 0 && !tdlib.isUserChat(message.getChatId());
    this.counters = new ViewPagerTopView.Item[getPagerItemCount()];
    this.baseCountersWidth = 0;

    int i = 0;
    if (needShowOptions) {
      OPTIONS_POSITION = i++;
      counters[OPTIONS_POSITION] = new ViewPagerTopView.Item();
    } else {
      OPTIONS_POSITION = -1;
    }

    if (needShowReactions) {
      ALL_REACTED_POSITION = i++;
      counters[ALL_REACTED_POSITION] = new ViewPagerTopView.Item(new Counter.Builder()
        .noBackground().allBold(true).textSize(13f).colorSet(this).callback(this)
        .drawable(R.drawable.baseline_favorite_16, 16f, 6f, Gravity.LEFT)
        .build(), this, Screen.dp(16));
      counters[ALL_REACTED_POSITION].counter.setCount(message.getMessageReactions().getTotalCount(), false);
      baseCountersWidth += counters[ALL_REACTED_POSITION].calculateWidth(null);
    } else {
      ALL_REACTED_POSITION = -1;
    }

    if (needShowViews) {
      SEEN_POSITION = i++;
      counters[SEEN_POSITION] = new ViewPagerTopView.Item(new Counter.Builder()
        .noBackground().allBold(true).textSize(13f).colorSet(this).callback(this).visibleIfZero()
        .drawable(R.drawable.baseline_visibility_16, 16f, 6f, Gravity.LEFT)
        .build(), this, Screen.dp(16));
      counters[SEEN_POSITION].counter.setCount(1, false);
      int itemWidth = counters[SEEN_POSITION].calculateWidth(null); // - Screen.dp(16);
      baseCountersWidth += itemWidth;
      counters[SEEN_POSITION].setStaticWidth(itemWidth - Screen.dp(16));
      counters[SEEN_POSITION].counter.setCount(Tdlib.CHAT_LOADING, false);
      getMessageOptions();
    } else {
      SEEN_POSITION = -1;
    }

    if (needShowReactions) {
      REACTED_START_POSITION = i;
      for (TdApi.MessageReaction reaction : reactions) {
        TGReaction tgReaction = tdlib.getReaction(reaction.type);
        counters[i] = new ViewPagerTopView.Item(tgReaction, new Counter.Builder()
          .noBackground().allBold(true).textSize(13f).colorSet(this).callback(this)
          .build(), this, Screen.dp(9));
        counters[i].counter.setCount(reaction.totalCount, false);
        if (Td.equalsTo(reaction.type, defaultReactionType)) {
          startPage = i;
        }
        i += 1;
      }
    } else {
      REACTED_START_POSITION = -1;
    }
  }

  private ViewPagerHeaderViewReactionsCompact headerCell;
  private View pagerInFrameLayoutFix;
  private FrameLayoutFix wrapView;
  private RelativeLayout contentView;
  private LickView lickView;
  private View fixView;

  // Create view

  @Override
  protected View onCreateView (Context context) {
    headerCell = new ViewPagerHeaderViewReactionsCompact(context, tdlib, message, needShowOptions ? baseCountersWidth : 0, needShowOptions, needShowReactions, needShowViews) {
      @Override
      public void onThemeInvalidate (boolean isTempUpdate) {
        setHeaderBackgroundFactor(headerBackgroundFactor);
        getBackButton().setColor(Theme.getColor(R.id.theme_color_headerLightIcon));
        super.onThemeInvalidate(isTempUpdate);
      }
    };
    headerCell.setReactionsSelectorDelegate(this);
    addThemeInvalidateListener(headerCell);
    headerView = new HeaderView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(54f), MeasureSpec.EXACTLY));
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (startPage != 0) {
          setCurrentPagerPosition(startPage, false);
          startPage = 0;
        }
      }
    };
    headerView.initWithSingleController(this, false);
    headerView.getFilling().setShadowAlpha(0f);
    headerView.getBackButton().setIsReverse(true);
    ViewSupport.setThemedBackground(headerView, R.id.theme_color_background, this);

    contentView = new RelativeLayout(context) {
      @Override
      protected void onDraw (Canvas canvas) {
        if (headerView != null) {
          canvas.drawRect(0, headerView.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.backgroundColor()));
        }
        super.onDraw(canvas);
      }

      @Override
      protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
        if (child == pagerInFrameLayoutFix && headerView != null) {
          canvas.save();
          canvas.clipRect(0, headerView.getTranslationY() + HeaderView.getTopOffset(), getMeasuredWidth(), getMeasuredHeight());
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
    fp.topMargin = Screen.dp(54);
    fixView = new View(context);
    ViewSupport.setThemedBackground(fixView, R.id.theme_color_background, this);
    fixView.setLayoutParams(fp);

    wrapView = new FrameLayoutFix(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent e) {
        return (e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < headerView.getTranslationY()) || super.onInterceptTouchEvent(e);
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return !(e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < headerView.getTranslationY()) && super.onTouchEvent(e);
      }

      private int oldHeight = -1;

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        post(() -> {
          final int height = getTargetHeight();
          if (height != oldHeight) {
            invalidateAllItemDecorations();
            onPageScrolled(currentMediaPosition, currentPositionOffset, 0);
            oldHeight = height;
          }
        });
      }

      /*@Override
      public void setTranslationY (float translationY) {
        super.setTranslationY(translationY);
        invalidate();
      }

      @Override
      protected void onDraw (Canvas canvas) {
        final int diff = Math.max(wrapView.getMeasuredHeight() - getTargetHeight(), 0);
        final int y = (int) (getMeasuredHeight() - getTranslationY() - diff);
        if (diff > 0) {
          canvas.drawRect(0, y, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.backgroundColor()));
        }
        super.onDraw(canvas);
      }

      @Override
      protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
        final int diff = Math.max(wrapView.getMeasuredHeight() - getTargetHeight(), 0);
        final int y = (int) (getMeasuredHeight() - getTranslationY() - diff);
        if (diff > 0) {
          canvas.save();
          canvas.clipRect(0, 0, getMeasuredWidth(), y);
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (diff > 0) {
          canvas.restore();
        }


        canvas.drawRect(0, 0, getMeasuredWidth(), getTargetHeight(), Paints.strokeBigPaint(Color.RED));
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Paints.strokeBigPaint(Color.GREEN));
        canvas.drawRect(0, 0, getMeasuredWidth(), y, Paints.strokeBigPaint(Color.BLUE));
        canvas.drawRect(0, getMeasuredHeight() - getCurrentPopupHeight(), getMeasuredWidth(), getMeasuredHeight(), Paints.strokeBigPaint(Color.MAGENTA));

        return result;
      }*/
    };

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = Screen.dp(54) + HeaderView.getTopOffset();
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

    return wrapView;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    pager.setOffscreenPageLimit(1);
    prepareControllerForPosition(startPage, null);
    tdlib.ui().post(this::launchOpenAnimation);

    headerCell.getTopView().setTextPadding(Screen.dp(0));
    headerCell.getTopView().setItems(Arrays.asList(counters));
    headerCell.getTopView().setOnItemClickListener(this);
    headerCell.getTopView().setSelectionColorId(R.id.theme_color_text);
    addThemeInvalidateListener(headerCell.getTopView());

    headerCell.getBackButton().setColor(Theme.getColor(R.id.theme_color_headerLightIcon));
    headerCell.getBackButton().setOnClickListener((v) -> {
      if (needShowOptions) {
        headerCell.getTopView().getOnItemClickListener().onPagerItemClick(0);
      } else {
        popupLayout.hideWindow(true);
      }
    });
  }


  //

  private TdApi.Users messageViewers;
  private void getMessageOptions () {
    tdlib.client().send(new TdApi.GetMessageViewers(message.getChatId(), message.getId()), (obj) -> {
      if (obj.getConstructor() != TdApi.Users.CONSTRUCTOR) return;
      runOnUiThreadOptional(() -> {
        messageViewers = (TdApi.Users) obj;
        if (SEEN_POSITION != -1) {
          counters[SEEN_POSITION].counter.setCount(messageViewers.totalCount, false);
        }
      });
    });
  }

  private float lastHeaderPosition;
  private void checkHeaderPosition (RecyclerView recyclerView) {
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
  }

  private int headerBackground;
  private float headerBackgroundFactor;

  private void setHeaderPosition (float y) {
    y = Math.max(y, HeaderView.getTopOffset());

    headerView.setTranslationY(y);
    fixView.setTranslationY(y);
    contentView.invalidate();
    fixView.invalidate();
    if (lickView != null) {
      final int topOffset = HeaderView.getTopOffset();
      final float top = y - topOffset;
      lickView.setTranslationY(top);
      float factor = top > topOffset ? 0f : 1f - ((float) top / (float) topOffset);
      lickView.setFactor(factor);
      headerView.getFilling().setShadowAlpha(factor);
      setHeaderBackgroundFactor(factor);
    }
  }

  public void setHeaderBackgroundFactor (float headerBackgroundFactor) {
    this.headerBackgroundFactor = headerBackgroundFactor;
    headerBackground = ColorUtils.blendARGB(
      Theme.getColor(R.id.theme_color_background),
      Theme.getColor(R.id.theme_color_headerLightBackground),
      headerBackgroundFactor
    );
    headerView.setBackgroundColor(headerBackground);
    headerCell.updatePaints(headerBackground);
  }


  private static final boolean PREVENT_HEADER_ANIMATOR = false; // TODO

  @Override
  protected boolean launchCustomHeaderTransformAnimator (boolean open, int transformMode, Animator.AnimatorListener listener) {
    return PREVENT_HEADER_ANIMATOR && open && getTopEdge() > 0;
  }

  private int getTopEdge () {
    return Math.max(0, (int) (headerView.getTranslationY() - HeaderView.getTopOffset()));
  }


  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return headerView != null && y < headerView.getTranslationY() - HeaderView.getSize(true);
  }

  private PopupLayout popupLayout;

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
          ViewController<?> c = getCurrentPagerItem();
          if (c instanceof RecyclerViewController<?>) {
            RecyclerView r = ((RecyclerViewController<?>) c).getRecyclerView();
            if (r != null) {
              r.invalidateItemDecorations();
              checkHeaderPosition(r);
            }
          }
        }
      }
    };
    // popupLayout.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    // popupLayout.set®(View.LAYER_TYPE_HARDWARE, Views.LAYER_PAINT);
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(true);
    //popupLayout.setHideKeyboard();
    //popupLayout.setNeedRootInsets();
    popupLayout.setTouchProvider(this);
    //popupLayout.setIgnoreHorizontal();
    //popupLayout.setNeedFullScreen(true);
    get();
    context().addFullScreenView(this, false);
  }

  private boolean openLaunched;

  private void launchOpenAnimation () {
    if (!openLaunched) {
      openLaunched = true;
      popupLayout.showSimplePopupView(get(), calculateTotalHeight());
    }
  }

  private int getTargetHeight () {
    return Screen.currentHeight()
      + (context.isKeyboardVisible() ? Keyboard.getSize() : 0)
      - (Screen.needsKeyboardPadding(context) ? Screen.getNavigationBarFrameDifference() : 0)
      + (context.isKeyboardVisible() && Device.NEED_ADD_KEYBOARD_SIZE ? Screen.getNavigationBarHeight() : 0);
  }

  private CharSequence cachedHint;
  private int cachedHintHeight, cachedHintAvailWidth;

  private int getContentOffset () {
    if (needShowOptions) {
      int optionItemsHeight = Screen.dp(54) * options.items.length;
      int hintHeight;
      if (!StringUtils.isEmpty(options.info)) {
        int availWidth = Screen.currentWidth() - Screen.dp(16f) * 2; // FIXME: rely on parent view width
        if (cachedHint != null && cachedHintAvailWidth == availWidth && cachedHint.equals(options.info)) {
          hintHeight = cachedHintHeight;
        } else {
          hintHeight = CustomTextView.measureHeight(this, options.info, 15f, availWidth);
          cachedHint = options.info;
          cachedHintAvailWidth = availWidth;
          cachedHintHeight = hintHeight;
        }
        hintHeight += Screen.dp(14f) + Screen.dp(6f);
      } else {
        hintHeight = 0;
      }
      return (
        getTargetHeight()
          - (Screen.dp(54) + HeaderView.getTopOffset())
          - optionItemsHeight
          - hintHeight
          - Screen.dp(1)
      );
    } else {
      return Screen.currentHeight() / 2;
    }
  }

  private int calculateTotalHeight () {
    return getTargetHeight() - (getContentOffset() + HeaderView.getTopOffset());
  }

  @Override
  public int getCurrentPopupHeight () {
    return (getTargetHeight() - getTopEdge() - (int) ((float) HeaderView.getTopOffset())) + Math.max(wrapView.getMeasuredHeight() - getTargetHeight(), 0);
  }

  private boolean ignoreAnyPagerScrollEventsBecauseOfMovements;

  public void setIgnoreAnyPagerScrollEventsBecauseOfMovements (boolean ignore) {
    this.ignoreAnyPagerScrollEventsBecauseOfMovements = ignore;
  }


  // Pager

  private void setRecyclerView (View v, MessageBottomSheetBaseController<?> controller, int position) {
    if (v instanceof RecyclerView) {
      RecyclerView recyclerView = (RecyclerView) v;
      recyclerView.setVerticalScrollBarEnabled(false);
      addThemeInvalidateListener(recyclerView);
      recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
          super.onScrollStateChanged(recyclerView, newState);
          if (newState == RecyclerView.SCROLL_STATE_IDLE && lickView != null && lickView.getFactor() != 0f && lickView.getFactor() != 1f) {
            MessageBottomSheetBaseController<?> controller = findCurrentCachedController();
            if (controller != null && controller.getRecyclerView() == recyclerView && (!ignoreAnyPagerScrollEventsBecauseOfMovements)) {
              controller.onScrollToTopRequested();
            }

          }
        }

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          MessageBottomSheetBaseController<?> controller = findCurrentCachedController();
          if (controller == null) {
            return;
          }
          if (controller.getRecyclerView() == recyclerView && (!ignoreAnyPagerScrollEventsBecauseOfMovements)) {
            checkHeaderPosition(recyclerView);
          }
        }
      });
      recyclerView.addItemDecoration(new ContentDecoration(controller, !(needShowOptions && position == 0)));
      recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange (View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
          MessageBottomSheetBaseController<?> controller = findCurrentCachedController();
          if (controller == null) {
            return;
          }
          if (controller.getRecyclerView() == v && currentPositionOffset == 0f) {
            checkHeaderPosition((RecyclerView) v);
          }
        }
      });
    }
    checkContentScrollY(controller);
  }

  private int OPTIONS_POSITION;
  private int SEEN_POSITION;
  private int ALL_REACTED_POSITION;
  private int REACTED_START_POSITION;

  boolean isFirstCreation = true;

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    View v = null;

    if (position == OPTIONS_POSITION) {
      View.OnClickListener onClickListener = view -> {
        ViewController<?> c = context().navigation().getCurrentStackItem();
        if (c instanceof OptionDelegate && ((OptionDelegate) c).onOptionItemPressed(view, view.getId())) {
          popupLayout.hideWindow(true);
        }
      };

      MessageOptionsController c = new MessageOptionsController(context, this.tdlib, getThemeListeners());
      c.setArguments(new MessageOptionsController.Args(options, onClickListener));
      c.get();
      setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
      setRecyclerView(c.getRecyclerView(), c, position);
      return c;
    }

    if (position == ALL_REACTED_POSITION) {
      MessageOptionsReactedController c = new MessageOptionsReactedController(context, this.tdlib, popupLayout, message, null);
      c.get();
      setRecyclerView(c.getRecyclerView(), c, position);
      return c;
    }

    if (position == SEEN_POSITION) {
      MessageOptionsSeenController c = new MessageOptionsSeenController(context, this.tdlib, popupLayout, message);
      c.get();
      setRecyclerView(c.getRecyclerView(), c, position);
      return c;
    }

    if (position >= REACTED_START_POSITION && REACTED_START_POSITION != -1) {
      MessageOptionsReactedController c = new MessageOptionsReactedController(context, this.tdlib, popupLayout, message, reactions[position - REACTED_START_POSITION].type);
      c.get();
      if (isFirstCreation && !needShowOptions) {
        setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
        isFirstCreation = false;
      }
      setRecyclerView(c.getRecyclerView(), c, position);
      return c;
    }

    throw new IllegalArgumentException("position == " + position);
  }

  @Override
  protected String[] getPagerSections () {
    return null;
  }

  @Override
  protected int getPagerItemCount () {
    return (needShowOptions ? 1 : 0) + (needShowViews ? 1 : 0) + (needShowReactions ? reactions.length + 1 : 0);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected void setCurrentPagerPosition (int position, boolean animated) {
    if (headerCell != null && animated) {
      headerCell.getTopView().setFromTo(getViewPager().getCurrentItem(), position);
    }
    super.setCurrentPagerPosition(position, animated);
  }

  private int currentMediaPosition;
  private float currentPositionOffset;
  private int checkedPosition = -1, checkedBasePosition = -1;

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    if (this.checkedBasePosition != position) {
      checkedBasePosition = position;
      checkContentScrollY(position);
    }
    if (positionOffset == 0f) {
      checkedPosition = -1;
    } else {
      // eventsBelongToSlider = false;
    }
    if (positionOffset != 0f) {
      int checkPosition = position + 1;
      if (this.checkedPosition != checkPosition) {
        this.checkedPosition = checkPosition;
        checkContentScrollY(checkPosition);
      }
    }
    currentMediaPosition = position;
    currentPositionOffset = positionOffset;
    setIgnoreAnyPagerScrollEventsBecauseOfMovements(positionOffset != 0f);

    if (headerCell != null) {
      headerCell.onPageScrolled(position, positionOffset);
    }

    if (position == 0 && positionOffset == 0f && needShowOptions) {
      MessageBottomSheetBaseController<?> controller = findCachedControllerByPosition(0);
      if (controller != null) {
        controller.onScrollToBottomRequested();
      }
    }

    if (position == 0 && needShowOptions) {
      float targetPosition = getContentOffset() + HeaderView.getTopOffset();
      float animPosition = targetPosition + (lastHeaderPosition - targetPosition) * positionOffset;
      setHeaderPosition(animPosition);
      if (positionOffset == 0f) {
        lastHeaderPosition = targetPosition;
      }
    }

    super.onPageScrolled(position, positionOffset, positionOffsetPixels);
  }

  @Override
  public void onPageSelected (int position, int actualPosition) {
    if (position == 0) {
      /*MessageBottomSheetBaseController<?> controller = findCachedControllerByPosition(0);
      if (controller != null) {
        controller.onScrollToBottomRequested();
      }*/
    }
  }

  private void invalidateCachedPosition () {
    checkedPosition = -1;
    checkedBasePosition = -1;
  }


  // Scroll ?

  private void checkContentScrollY (int position) {
    if (getViewPager().getAdapter() instanceof ViewPagerController.ViewPagerAdapter) {
      ViewPagerController.ViewPagerAdapter adapter = (ViewPagerController.ViewPagerAdapter) (getViewPager().getAdapter());
      ViewController<?> controller = adapter.getCachedItemByPosition(position);
      if (controller instanceof MessageBottomSheetBaseController<?>) {
        checkContentScrollY((MessageBottomSheetBaseController<?>) controller);
      }
    }
  }

  public int maxItemsScrollYOffset () {
    return maxItemsScrollY();// - getPagerTopViewHeight() - getTopViewTopPadding();
  }

  private int maxItemsScrollY () {
    return getContentOffset();// - getShadowBottomHeight() - getPagerTopViewHeight();
  }

  public void checkContentScrollY (MessageBottomSheetBaseController<?> c) {
    int maxScrollY = maxItemsScrollYOffset();
    int scrollY = (int) (getContentOffset() - headerView.getTranslationY() + HeaderView.getTopOffset()); //();
    if (c != null) {
      c.ensureMaxScrollY(scrollY, maxScrollY);
    }
  }


  // getters

  @Override
  public int getId () {
    return R.id.controller_messageOptionsPager;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }


  // listeners

  @Override
  public void onClick (View v) {

  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    return false;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {

    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {

    }
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    if (headerCell != null) {
      headerCell.getTopView().invalidate();
    }
  }


  // Settings

  @Override
  public void destroy () {
    super.destroy();
    context().removeFullScreenView(this, false);
  }


  // Sparse drawable function
  private SparseArrayCompat<Drawable> sparseDrawables;

  @Override
  public final SparseArrayCompat<Drawable> getSparseDrawableHolder () {
    return (sparseDrawables != null ? sparseDrawables : (sparseDrawables = new SparseArrayCompat<>()));
  }

  @Override
  public final Resources getSparseDrawableResources () {
    return context().getResources();
  }


  //

  private void invalidateAllItemDecorations () {
    for (int i = 0; i < getPagerItemCount(); i++) {
      MessageBottomSheetBaseController<?> controller = findCachedControllerByPosition(i);
      if (controller != null) {
        CustomRecyclerView customRecyclerView = controller.getRecyclerView();
        if (customRecyclerView != null) {
          customRecyclerView.invalidateItemDecorations();
        }
      }
    }
  }

  @Nullable
  private MessageBottomSheetBaseController<?> findCurrentCachedController () {
    return findCachedControllerByPosition(getViewPager().getCurrentItem());
  }

  @Nullable
  private MessageBottomSheetBaseController<?> findCachedControllerByPosition (int position) {
    if (getViewPager().getAdapter() instanceof ViewPagerController.ViewPagerAdapter) {
      ViewPagerController.ViewPagerAdapter adapter = (ViewPagerController.ViewPagerAdapter) (getViewPager().getAdapter());
      ViewController<?> controller = adapter.getCachedItemByPosition(position);
      if (controller instanceof MessageBottomSheetBaseController<?>) {
        return (MessageBottomSheetBaseController<?>) controller;
      }
    }
    return null;
  }

  @Override
  public int defaultTextColor () {
    return Theme.getColor(R.id.theme_color_text);
  }

  private class ContentDecoration extends RecyclerView.ItemDecoration {
    MessageBottomSheetBaseController<?> controller;
    final boolean needBottomOffsets;

    public ContentDecoration (MessageBottomSheetBaseController<?> controller, boolean needBottomOffsets) {
      this.controller = controller;
      this.needBottomOffsets =  needBottomOffsets;
    }

    @Override
    public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
      final int position = parent.getChildAdapterPosition(view);
      final int itemCount = parent.getAdapter().getItemCount();
      final boolean isUnknown = position == RecyclerView.NO_POSITION;
      int top = 0, bottom = 0;

      if (position == 0 || isUnknown) {
        top = getContentOffset();
      }
      if (position == itemCount - 1 || isUnknown) {
        final int itemsHeight = isUnknown ? view.getMeasuredHeight() : controller.getItemsHeight(parent);// - SettingHolder.measureHeightForType(ListItem.TYPE_HEADER);
        final int parentHeight = parent.getMeasuredHeight(); // - getShadowBottomHeight();
        bottom = Math.max(0, parentHeight - itemsHeight /*- getHiddenContentHeight() - getPagerTopViewHeight()*/);
      }

      outRect.set(0, Math.max(top, 0), 0, needBottomOffsets ? Math.max(bottom, 0) : 0);
    }
  }

  public static abstract class MessageBottomSheetBaseController<T> extends RecyclerViewController<T> {
    public MessageBottomSheetBaseController (Context context, Tdlib tdlib) {
      super(context, tdlib);
    }

    public abstract int getItemsHeight (RecyclerView parent);

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
            /*if (parent != null) {
              top -= parent.getItemsBound();
            }*/
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

      /*LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      View view = manager.findViewByPosition(0);
      recyclerView.stopScroll();
      if (view != null) {
        int scrollTop = -view.getTop();
        getRecyclerView().smoothScrollBy(0, -scrollTop);
      }*/
    }
  }


  // Reactions selector delegate

  @Override
  public void onClick (View v, TGReaction reaction) {
    int[] positionCords = new int[2];
    v.getLocationOnScreen(positionCords);

    int startX = positionCords[0] + v.getMeasuredWidth() / 2;
    int startY = positionCords[1] + v.getMeasuredHeight() / 2;

    if (message.getMessageReactions().sendReaction(reaction.type, false, handler(v, () -> {}))) {
      message.scheduleSetReactionAnimationFromBottomSheet(reaction, new Point(startX, startY));
    }
    popupLayout.hideWindow(true);
  }

  @Override
  public void onLongClick (View v, TGReaction reaction) {
    int[] positionCords = new int[2];
    v.getLocationOnScreen(positionCords);

    int startX = positionCords[0] + v.getMeasuredWidth() / 2;
    int startY = positionCords[1] + v.getMeasuredHeight() / 2;

    message.getMessageReactions().sendReaction(reaction.type, true, handler(v, () -> {}));
    message.scheduleSetReactionAnimationFullscreenFromBottomSheet(reaction, new Point(startX, startY));
    popupLayout.hideWindow(true);
  }

  private Client.ResultHandler handler (View v, Runnable onSuccess) {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          tdlib.ui().post(onSuccess);
          break;
        case TdApi.Error.CONSTRUCTOR:
          tdlib.ui().post(() -> onSendError(v, (TdApi.Error) object));
          break;
      }
    };
  }

  private void onSendError (View v, TdApi.Error error) {
    context().tooltipManager().builder(v).show(tdlib, TD.toErrorString(error)).hideDelayed(3500, TimeUnit.MILLISECONDS);
    message.cancelScheduledSetReactionAnimation();
  }


  private class LickView extends View {
    public LickView (Context context) {
      super(context);
    }

    private float factor;

    public float getFactor () {
      return factor;
    }

    public void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (factor > 0f) {
        int bottom = getMeasuredHeight();
        int top = bottom - (int) ((float) bottom * factor);
        c.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(
          ColorUtils.compositeColors(Theme.getColor(R.id.theme_color_statusBar), headerBackground)
        ));
      }
    }
  }
}
