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
 * File created on 31/05/2023
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ReactionsOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ClearButton;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ViewPager;
import org.thunderdog.challegram.widget.decoration.ItemDecorationFirstViewTop;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class EmojiStatusSelectorEmojiPage extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<Void>
  implements BottomSheetViewController.BottomSheetBaseControllerPage, Menu, FactorAnimator.Target, EmojiLayout.Listener {

  private static final int SEARCH_FIELD_IS_NOT_EMPTY_ANIMATOR = 0;
  private static final int EMOJI_SELECTOR_VISIBILITY_ANIMATOR = 1;
  private static final int EMOJI_SELECTOR_VISIBILITY_2_ANIMATOR = 2;
  private static final int SEARCH_MODE_VISIBILITY_ANIMATOR = 3;

  private final BoolAnimator searchModeVisibility = new BoolAnimator(SEARCH_MODE_VISIBILITY_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);
  private final BoolAnimator searchFieldIsNotEmpty = new BoolAnimator(SEARCH_FIELD_IS_NOT_EMPTY_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);
  private final BoolAnimator emojiSelectorVisibility = new BoolAnimator(EMOJI_SELECTOR_VISIBILITY_ANIMATOR, this, AnimatorUtils.ACCELERATE_INTERPOLATOR, 330L);
  private final BoolAnimator emojiSelectorButtonsVisibility = new BoolAnimator(EMOJI_SELECTOR_VISIBILITY_2_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);

  private final Wrapper parent;

  private String currentSearchInput;
  private EmojiLayout emojiCustomListLayout;
  private EmojiStatusListController emojiCustomListController;
  private CustomRecyclerView customRecyclerView;
  private ItemDecorationFirstViewTop emojiStatusPickerTopDecoration;
  private ForegroundSearchByEmojiView foregroundEmojiLayout;
  private HeaderButtons headerButtons;

  public EmojiStatusSelectorEmojiPage (Context context, Tdlib tdlib, Wrapper parent) {
    super(context, tdlib);
    this.parent = parent;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    headerView = new HeaderView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(56), MeasureSpec.EXACTLY));
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        super.onTouchEvent(e);
        return true;
      }
    };
    headerView.initWithSingleController(this, false);
    headerView.setBackgroundHeight(Screen.dp(56));
    headerView.getBackButton().setIsReverse(true);
    parent.addThemeInvalidateListener(headerView);

    emojiCustomListLayout = new EmojiLayout(context());
    emojiCustomListLayout.initWithEmojiStatus(this, this, this);

    emojiCustomListController = new EmojiStatusListController(context, tdlib) {
      @Override
      public void onSetEmojiStatusFromPreview (StickerSmallView view, View clickView, TGStickerObj sticker, long emojiId, long expirationDate) {
        context.replaceReactionPreviewCords(parent.animationDelegate.getDestX(), parent.animationDelegate.getDestY());
        parent.hidePopupWindow(true);
        scheduleClickAnimation(sticker.getCustomEmojiId());
        destX = parent.animationDelegate.getDestX();
        destY = parent.animationDelegate.getDestY();
        parent.animationDelegate.onAnimationStart();
        UI.post(EmojiStatusSelectorEmojiPage.this::onSetStatusAnimationFinish, 180L);
      }
    };
    emojiCustomListController.setArguments(emojiCustomListLayout);
    emojiCustomListController.initWithFakeViews(customRecyclerView = recyclerView);
    emojiCustomListController.getValue();
    emojiCustomListController.setOnStickersLoadListener(parent::launchOpenAnimation);

    emojiStatusPickerTopDecoration = ItemDecorationFirstViewTop.attach(customRecyclerView, parent::getContentOffset);
    customRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        final int position = parent.getChildAdapterPosition(view);
        final int itemCount = parent.getAdapter().getItemCount();
        if (position == itemCount - 1) {
          int bottom = getKeyboardState() ? Keyboard.getSize(Keyboard.getSize()) : 0;
          outRect.set(0, 0, 0, bottom);
        }
      }
    });
    customRecyclerView.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
      if (emojiCustomListLayout.checkWidth(customRecyclerView.getMeasuredWidth())) {
        emojiCustomListLayout.invalidateStickerSets();
        customRecyclerView.getAdapter().notifyDataSetChanged();
      }
    });


    emojiCustomListLayout.putCachedItem(emojiCustomListController, 0);
    emojiCustomListLayout.removeView(emojiCustomListLayout.getHeaderView());
    parent.foregroundView.addView(emojiCustomListLayout.getHeaderView());

    foregroundEmojiLayout = new ForegroundSearchByEmojiView(context, this) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        if (emojiSelectorVisibility.getFloatValue() < 1f) return false;
        return super.onTouchEvent(event);
      }
    };
    foregroundEmojiLayout.setOnClickListener(v -> closeEmojiSelectMode());
    parent.foregroundView.addView(foregroundEmojiLayout);

    UI.post(parent::launchOpenAnimation, 150);
  }

  private int destX, destY;

  @Override
  public boolean onSetEmojiStatus (@Nullable View view, TGStickerObj sticker, TdApi.EmojiStatus emojiStatus) {
    tdlib.client().send(new TdApi.SetEmojiStatus(emojiStatus), tdlib.okHandler());
    parent.hidePopupWindow(true);
    if (view == null) return true;

    int[] positionCords = new int[2];
    view.getLocationOnScreen(positionCords);

    int startX = positionCords[0] + view.getMeasuredWidth() / 2;
    int startY = positionCords[1] + view.getMeasuredHeight() / 2;

    context().reactionsOverlayManager().addOverlay(
      new ReactionsOverlayView.ReactionInfo(context().reactionsOverlayManager())
        .setSticker(sticker, false)
        .setRepaintingColorIds(ColorId.iconActive, ColorId.white)
        .setAnimationEndListener(this::onSetStatusAnimationFinish)
        .setAnimatedPosition(
          new Point(startX, startY),
          new Point(destX = parent.animationDelegate.getDestX(), destY = parent.animationDelegate.getDestY()),
          view.getMeasuredHeight(),
          Screen.dp(28),
          new TGMessage.QuickReactionAnimatedPositionProvider(Screen.dp(80)),
          500L
        )
    );
    scheduleClickAnimation(sticker.getCustomEmojiId());
    scheduledClickEffectSticker = sticker;
    parent.animationDelegate.onAnimationStart();

    return true;
  }

  public void onSetStatusAnimationFinish () {
    parent.animationDelegate.onAnimationEnd();
    if (scheduledClickSticker == null) return;
    context().reactionsOverlayManager().addOverlay(
      new ReactionsOverlayView.ReactionInfo(context().reactionsOverlayManager())
        .setSticker(scheduledClickSticker, true)
        .setRepaintingColorIds(ColorId.iconActive, ColorId.white)
        .setEmojiStatusEffect(scheduledClickEffectSticker)
        .setUseDefaultSprayAnimation(true)
        .setPosition(new Point(destX, destY), Screen.dp(90))
    );
  }

  private long scheduledClickAnimation;
  private TGStickerObj scheduledClickSticker;
  private TGStickerObj scheduledClickEffectSticker;

  private void scheduleClickAnimation (final long emojiId) {
    scheduledClickAnimation = emojiId;
    scheduledClickSticker = null;
    tdlib.pickRandomGenericOverlaySticker(sticker -> {
      if (scheduledClickAnimation == emojiId) {
        if (sticker != null) {
          scheduledClickSticker = new TGStickerObj(tdlib, sticker, null, sticker.fullType);
        }
      }
    });
  }





  public void setHeaderPosition (float y) {
    foregroundEmojiLayout.setTranslationY(y - Screen.getStatusBarHeight());
    emojiCustomListLayout.setHeaderOffset(y - Screen.getStatusBarHeight());
  }

  public HeaderView getHeaderView () {
    return headerView;
  }

  @Override
  public void onScrollToTopRequested () {
    try {
      LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
      getRecyclerView().stopScroll();
      int firstVisiblePosition = manager.findFirstVisibleItemPosition();
      if (firstVisiblePosition == RecyclerView.NO_POSITION) {
        return;
      }
      int scrollTop = 0; // ((SettingsAdapter) recyclerView.getAdapter()).measureScrollTop(firstVisiblePosition);
      View view = manager.findViewByPosition(firstVisiblePosition);
      if (view != null) {
        scrollTop -= view.getTop();
      }
      getRecyclerView().smoothScrollBy(0, -scrollTop);
    } catch (Throwable t) {
      Log.w("Cannot scroll to top", t);
    }

  }

  @Override
  public int getItemsHeight (RecyclerView parent) {
    return 0;
  }

  @Override
  public CustomRecyclerView getRecyclerView () {
    return customRecyclerView;
  }





  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }



  @Override
  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_ONLY_WRITABLE | SearchManager.FLAG_NEED_GLOBAL_SEARCH;
  }



  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    header.addButton(menu, headerButtons = new HeaderButtons(context, this, header));
    headerButtons.setFactors(searchModeVisibility.getFloatValue(), searchFieldIsNotEmpty.getFloatValue(), emojiSelectorButtonsVisibility.getFloatValue(), true);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    super.onMenuItemPressed(id, view);
    if (id == R.id.menu_btn_emoji) {
      openEmojiSelectMode();
    } else if (id == R.id.menu_btn_emoji_close) {
      closeEmojiSelectMode();
    }
  }

  @Override
  public boolean needTopDecorationOffsets (RecyclerView parent) {
    return false;
  }

  @Override
  public boolean needBottomDecorationOffsets (RecyclerView parent) {
    return false;
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    boolean result = super.onKeyboardStateChanged(visible);
    customRecyclerView.invalidateItemDecorations();
    emojiCustomListController.onKeyboardStateChanged(visible);
    return result;
  }



  /* * */

  private String emojiSearchRequest;

  @Override
  public void onEnterEmoji (String emoji) {
    closeEmojiSelectMode();
    setSearchInput(emojiSearchRequest = emoji);
    getSearchHeaderView(headerView).editView().setEnabled(false);
  }

  private boolean inEmojiSelectMode;

  private void openEmojiSelectMode () {
    inEmojiSelectMode = true;
    foregroundEmojiLayout.setVisibility(View.VISIBLE);
    emojiSelectorVisibility.setValue(true, true);
    emojiSelectorButtonsVisibility.setValue(true, true);
    closeSearchMode(null);
  }

  private void closeEmojiSelectMode () {
    inEmojiSelectMode = false;
    emojiSelectorVisibility.setValue(false, true);
    emojiSelectorButtonsVisibility.setValue(false, true);
    openSearchMode();
  }

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    searchModeVisibility.setValue(true, true);
    getSearchHeaderView(headerView).editView().setEnabled(true);
    emojiStatusPickerTopDecoration.scheduleDisableDecorationOffset();
  }

  @Override
  protected void onAfterLeaveSearchMode () {
    super.onAfterLeaveSearchMode();
    if (!inEmojiSelectMode) {
      emojiCustomListController.search(currentSearchInput = null, emojiSearchRequest = null);
      UI.post(emojiStatusPickerTopDecoration::enableDecorationOffset, 250);
      searchModeVisibility.setValue(false, true);
    }
  }

  @Override
  protected void onSearchInputChanged (String input) {
    super.onSearchInputChanged(currentSearchInput = input);
    if (StringUtils.isEmpty(input)) {
      searchModeVisibility.setValue(true, true);
      getSearchHeaderView(headerView).editView().setEnabled(true);
      Keyboard.show(getSearchHeaderView(headerView).editView());
      if (!StringUtils.isEmpty(emojiSearchRequest)) {
        emojiSearchRequest = null;
      }
    }
    searchFieldIsNotEmpty.setValue(!StringUtils.isEmpty(input), true);
    emojiCustomListController.search(currentSearchInput, emojiSearchRequest);
  }



  /* Animations */

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == EMOJI_SELECTOR_VISIBILITY_ANIMATOR) {
      foregroundEmojiLayout.setFactor(factor);
    }
    headerButtons.setFactors(searchModeVisibility.getFloatValue(), searchFieldIsNotEmpty.getFloatValue(), emojiSelectorButtonsVisibility.getFloatValue(), true);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == EMOJI_SELECTOR_VISIBILITY_ANIMATOR) {
      if (finalFactor == 0) {
        foregroundEmojiLayout.setVisibility(View.GONE);
      }
    }
  }



  /* Controller methods */

  @Override
  public int getId () {
    return R.id.controller_setEmojiStatusPager;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(inEmojiSelectMode ? R.string.FilterByEmoji : R.string.SelectEmojiStatus);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean closeSearchModeByBackPress (boolean fromTop) {
    return true;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inEmojiSelectMode) {
      closeEmojiSelectMode();
      return true;
    } else if (inSearchMode()) {
      closeSearchMode(null);
      return true;
    }
    return false;
  }

  @Override
  protected View getSearchAntagonistView () {
    return customRecyclerView;
  }

  @Override
  protected boolean allowMenuReuse () {
    return false;
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.icon;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }



  /* Views */

  private static class HeaderButtons extends FrameLayout {
    private final ClearButton clearButton;
    private final HeaderButton searchButton;
    private final HeaderButton emojiButton;
    private final HeaderButton keyboardButton;

    public HeaderButtons (@NonNull Context context, EmojiStatusSelectorEmojiPage controller, HeaderView headerView) {
      super(context);

      clearButton = new ClearButton(getContext());
      clearButton.setId(R.id.menu_btn_clear);
      clearButton.setColorId(controller.getHeaderIconColorId());
      clearButton.setButtonBackground(controller.getBackButtonResource());
      clearButton.setOnClickListener(headerView);

      searchButton = headerView.genButton(R.id.menu_btn_search, R.drawable.baseline_search_24, controller.getHeaderIconColorId(), controller, Screen.dp(52f), headerView);
      emojiButton = headerView.genButton(R.id.menu_btn_emoji, R.drawable.baseline_emoticon_outline_24, controller.getHeaderIconColorId(), controller, Screen.dp(52f), headerView);
      keyboardButton = headerView.genButton(R.id.menu_btn_emoji_close, R.drawable.baseline_keyboard_24, controller.getHeaderIconColorId(), controller, Screen.dp(52f), headerView);

      addView(clearButton);
      addView(searchButton);
      addView(emojiButton);
      addView(keyboardButton);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(Screen.dp(52), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
      );
    }

    private void setFactors (float clearVisibility, float searchVisibility, float emojiVisibility, float keyboardVisibility, boolean updateViewVisibility) {
      if (updateViewVisibility) {
        clearButton.setVisibility(clearVisibility > 0f ? VISIBLE : GONE);
        searchButton.setVisibility(searchVisibility > 0f ? VISIBLE : GONE);
        emojiButton.setVisibility(emojiVisibility > 0f ? VISIBLE : GONE);
        keyboardButton.setVisibility(keyboardVisibility > 0f ? VISIBLE : GONE);
      }
      clearButton.setAlpha(clearVisibility);
      searchButton.setAlpha(searchVisibility);
      emojiButton.setAlpha(emojiVisibility);
      keyboardButton.setAlpha(keyboardVisibility);
    }

    public void setFactors (float searchModeVisibility, float inputFieldIsNotEmpty, float emojiSelectorVisibility, boolean updateViewVisibility) {
      float clearVisibility = Math.min(inputFieldIsNotEmpty, searchModeVisibility);
      float searchVisibility = 1f - searchModeVisibility;
      float emojiVisibility = Math.min(searchModeVisibility, Math.min(1f - inputFieldIsNotEmpty, 1f - emojiSelectorVisibility));
      float keyboardVisibility = Math.min(searchModeVisibility, Math.min(1f - inputFieldIsNotEmpty, emojiSelectorVisibility));
      setFactors(clearVisibility, searchVisibility, emojiVisibility, keyboardVisibility, updateViewVisibility);

      float offset = (Screen.dp(-32) * emojiSelectorVisibility);
      emojiButton.setTranslationY(offset);
      keyboardButton.setTranslationY(offset + Screen.dp(32));
    }
  }

  @SuppressLint("ViewConstructor")
  private static class ForegroundSearchByEmojiView extends FrameLayout {
    private final EmojiLayout foregroundEmojiLayout;
    private final Path path = new Path();
    private float factor = 0;

    public ForegroundSearchByEmojiView (@NonNull Context context, EmojiStatusSelectorEmojiPage controller) {
      super(context);

      foregroundEmojiLayout = new EmojiLayout(context);
      foregroundEmojiLayout.initWithMediasEnabled(controller, false, false, controller, controller, false, true);
      foregroundEmojiLayout.setCircleVisible(false, false);
      foregroundEmojiLayout.setBackgroundColor(Theme.fillingColor());

      setVisibility(GONE);
      addView(foregroundEmojiLayout);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int height = MeasureSpec.getSize(heightMeasureSpec);
      foregroundEmojiLayout.setForceHeight(height * 13 / 20);
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      updatePath(factor);
    }

    @Override
    protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
      if (factor == 1f) {
        return super.drawChild(canvas, child, drawingTime);
      }

      canvas.save();
      canvas.clipPath(path);
      boolean b = super.drawChild(canvas, child, drawingTime);
      canvas.restore();
      return b;
    }

    public void setFactor (float factor) {
      this.factor = factor;
      setBackgroundColor(ColorUtils.alphaColor(factor * 0.55f, 0xFF000000));
      updatePath(factor);
      invalidate();
    }

    private void updatePath (float factor) {
      path.reset();
      path.addCircle(getMeasuredWidth(), 0,
        MathUtils.distance(getMeasuredWidth(), getMeasuredHeight(), 0, 0) * factor,
        Path.Direction.CW);
      path.close();
    }
  }



  public interface AnimationsEmojiStatusSetDelegate {
    void onAnimationStart ();
    void onAnimationEnd ();
    int getDestX ();
    int getDestY ();
  }


  /* * */

  public static class Wrapper extends BottomSheetViewController<Void> {
    private final EmojiStatusSelectorEmojiPage fragment;
    private final ViewController<?> parent;
    private final FrameLayout foregroundView;
    private final AnimationsEmojiStatusSetDelegate animationDelegate;

    public Wrapper (Context context, Tdlib tdlib, ViewController<?> parent, AnimationsEmojiStatusSetDelegate delegate) {
      super(context, tdlib);
      this.parent = parent;
      this.animationDelegate = delegate;
      fragment = new EmojiStatusSelectorEmojiPage(context, tdlib, this);
      foregroundView = new FrameLayout(context);
    }

    @Override
    protected void onBeforeCreateView () {

      fragment.setArguments(getArguments());
      fragment.getValue();
    }

    @Override
    protected HeaderView onCreateHeaderView () {
      return fragment.getHeaderView();
    }

    @Override
    protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
      pager.setOffscreenPageLimit(1);
      contentView.addView(foregroundView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onAfterCreateView () {
      setLickViewColor(Theme.getColor(ColorId.headerLightBackground));
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, ColorState state) {
      super.onThemeColorsChanged(areTemp, state);
      setLickViewColor(Theme.getColor(ColorId.headerLightBackground));
    }

    @Override
    protected void setupPopupLayout (PopupLayout popupLayout) {
      popupLayout.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      popupLayout.setBoundController(fragment);
      popupLayout.setPopupHeightProvider(this);
      popupLayout.init(true);
      popupLayout.setHideKeyboard();
      popupLayout.setNeedRootInsets();
      popupLayout.setTouchProvider(this);
      popupLayout.setIgnoreHorizontal();
      popupLayout.setTag(parent);
    }

    @Override
    protected void setHeaderPosition (float y) {
      int t = 0;
      super.setHeaderPosition(y + t);
      fragment.setHeaderPosition(y + t);
    }

    @Override
    public int getId () {
      return fragment.getId();
    }

    @Override
    protected int getPagerItemCount () {
      return 1;
    }

    @Override
    protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
      if (position != 0) return null;
      setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
      setDefaultListenersAndDecorators(fragment);
      return fragment;
    }

    @Override
    protected int getContentOffset () {
      return (getTargetHeight() - getHeaderHeight(true)) / 3;
    }

    @Override
    protected int getHeaderHeight () {
      return Screen.dp(56);
    }

    @Override
    protected boolean canHideByScroll () {
      return false;
    }

    @Override
    public boolean needsTempUpdates () {
      return true;
    }
  }
}
