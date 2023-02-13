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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.collection.SparseArrayCompat;
import androidx.core.graphics.ColorUtils;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewReactionsCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class MessageOptionsPagerController extends BottomSheetViewController<Void> implements
  FactorAnimator.Target, View.OnClickListener, Menu, DrawableProvider,
  Counter.Callback, ReactionsSelectorRecyclerView.ReactionSelectDelegate, TextColorSet {

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

  // Create view

  @Override
  protected HeaderView onCreateHeaderView () {
    HeaderView headerView = new HeaderView(context) {
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

    return headerView;
  };

  @Override
  protected void onBeforeCreateView () {
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
        hidePopupWindow(true);
      }
    });
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    if (this.checkedBasePosition != position) {
      checkedBasePosition = position;
      checkContentScrollY(position);
    }
    if (positionOffset == 0f) {
      checkedPosition = -1;
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
      ViewController<?> controller = findCachedControllerByPosition(0);
      if (controller instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        ((BottomSheetBaseControllerPage) controller).onScrollToBottomRequested();
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
  protected void setHeaderBackgroundFactor (float headerBackgroundFactor) {
    final int headerBackground = ColorUtils.blendARGB(
      Theme.getColor(R.id.theme_color_background),
      Theme.getColor(R.id.theme_color_headerLightBackground),
      headerBackgroundFactor
    );
    setLickViewColor(headerBackground);
    if (headerView != null) {
      headerView.setBackgroundColor(headerBackground);
    }
    if (headerCell != null) {
      headerCell.updatePaints(headerBackground);
    }
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



  private CharSequence cachedHint;
  private int cachedHintHeight, cachedHintAvailWidth;

  @Override
  protected int getContentOffset () {
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

  // Pager

  private final int OPTIONS_POSITION;
  private final int SEEN_POSITION;
  private final int ALL_REACTED_POSITION;
  private final int REACTED_START_POSITION;

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    View v = null;

    if (position == OPTIONS_POSITION) {
      View.OnClickListener onClickListener = view -> {
        ViewController<?> c = context().navigation().getCurrentStackItem();
        if (c instanceof OptionDelegate && ((OptionDelegate) c).onOptionItemPressed(view, view.getId())) {
          hidePopupWindow(true);
        }
      };

      MessageOptionsController c = new MessageOptionsController(context, this.tdlib, getThemeListeners());
      c.setArguments(new MessageOptionsController.Args(options, onClickListener));
      c.get();
      setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
      setDefaultListenersAndDecorators(c);
      return c;
    }

    if (position == ALL_REACTED_POSITION) {
      MessageOptionsReactedController c = new MessageOptionsReactedController(context, this.tdlib, getPopupLayout(), message, null);
      c.get();
      setDefaultListenersAndDecorators(c);
      return c;
    }

    if (position == SEEN_POSITION) {
      MessageOptionsSeenController c = new MessageOptionsSeenController(context, this.tdlib, getPopupLayout(), message);
      c.get();
      setDefaultListenersAndDecorators(c);
      return c;
    }

    if (position >= REACTED_START_POSITION && REACTED_START_POSITION != -1) {
      MessageOptionsReactedController c = new MessageOptionsReactedController(context, this.tdlib, getPopupLayout(), message, reactions[position - REACTED_START_POSITION].type);
      c.get();
      if (isFirstCreation && !needShowOptions) {
        setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
        isFirstCreation = false;
      }
      setDefaultListenersAndDecorators(c);
      return c;
    }

    throw new IllegalArgumentException("position == " + position);
  }

  @Override
  protected int getPagerItemCount () {
    return (needShowOptions ? 1 : 0) + (needShowViews ? 1 : 0) + (needShowReactions ? reactions.length + 1 : 0);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
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

  @Override
  protected int getHeaderHeight () {
    return Screen.dp(54);
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

  @Override
  public int defaultTextColor () {
    return Theme.getColor(R.id.theme_color_text);
  }

  // Reactions selector delegate

  @Override
  public void onClick (View v, TGReaction reaction) {
    int[] positionCords = new int[2];
    v.getLocationOnScreen(positionCords);

    int startX = positionCords[0] + v.getMeasuredWidth() / 2;
    int startY = positionCords[1] + v.getMeasuredHeight() / 2;

    boolean hasReaction = message.getMessageReactions().hasReaction(reaction.type);
    if (hasReaction || message.messagesController().callNonAnonymousProtection(message.getId() + reaction.getId(), tooltipManager().builder(v))) {
      if (message.getMessageReactions().toggleReaction(reaction.type, false, true, handler(v, () -> {
      }))) {
        message.scheduleSetReactionAnimationFromBottomSheet(reaction, new Point(startX, startY));
      }
      hidePopupWindow(true);
    }
  }

  @Override
  public void onLongClick (View v, TGReaction reaction) {
    int[] positionCords = new int[2];
    v.getLocationOnScreen(positionCords);

    int startX = positionCords[0] + v.getMeasuredWidth() / 2;
    int startY = positionCords[1] + v.getMeasuredHeight() / 2;

    if (message.messagesController().callNonAnonymousProtection(message.getId() + reaction.getId(), tooltipManager().builder(v))) {
      if (message.getMessageReactions().toggleReaction(reaction.type, true, true, handler(v, () -> {
      }))) {
        message.scheduleSetReactionAnimationFullscreenFromBottomSheet(reaction, new Point(startX, startY));
      }
      hidePopupWindow(true);
    }
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
}
