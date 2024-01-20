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
 * File created on 25/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.EmojiListController;
import org.thunderdog.challegram.ui.EmojiMediaListController;
import org.thunderdog.challegram.ui.EmojiStatusListController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutRecyclerController;
import org.thunderdog.challegram.widget.emoji.header.EmojiHeaderView;
import org.thunderdog.challegram.widget.emoji.header.MediaHeaderView;
import org.thunderdog.challegram.widget.emoji.section.EmojiSection;
import org.thunderdog.challegram.widget.emoji.section.EmojiSectionView;
import org.thunderdog.challegram.widget.emoji.section.StickerSectionView;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class EmojiLayout extends FrameLayoutFix implements ViewTreeObserver.OnPreDrawListener, ViewPager.OnPageChangeListener, FactorAnimator.Target, View.OnClickListener, Lang.Listener, EmojiLayoutRecyclerController.Callback {
  public interface Listener {
    default void onEnterEmoji (String emoji) {}
    default void onEnterCustomEmoji (TGStickerObj sticker) {}

    default boolean onSendSticker (@Nullable View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
      return false;
    }
    default boolean onSendGIF (@Nullable View view, TdApi.Animation animation) {
      return false;
    }
    default boolean onSetEmojiStatus (@Nullable View view, TGStickerObj sticker, TdApi.EmojiStatus emojiStatus) {
      return false;
    }

    default boolean isEmojiInputEmpty () { return true; }

    default void onDeleteEmoji () {}

    default void onSearchRequested (EmojiLayout layout, boolean areStickers) {}

    default long getOutputChatId () { return 0; }

    default void onSectionSwitched (EmojiLayout layout, @EmojiMediaType int section, @EmojiMediaType int prevSection) { }
    default void onSectionInteracted (EmojiLayout layout, @EmojiMediaType int section, boolean interactionFinished) { }
  }

  private ViewController<?> parentController;
  private @Nullable Listener listener;

  private RtlViewPager pager;
  private Adapter adapter;

  private FrameLayoutFix headerView;

  public static int getHeaderSize () {
    return Screen.dp(47f);
  }

  public static int getHeaderPadding () {
    return Screen.dp(6f);
  }

  public static int getHorizontalPadding () {
    return Screen.dp(2.5f);
  }

  private ShadowView shadowView;
  private @Nullable EmojiHeaderView emojiHeaderView;
  private @Nullable MediaHeaderView mediaSectionsView;

  private int emojiSectionsSize = 0;

  public void clearRecentStickers () {
    if (themeProvider != null && mediaSectionsView.hasRecents()) {
      themeProvider.showOptions(null, new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {
        Lang.getString(animatedEmojiOnly ? R.string.ClearRecentEmojiStatuses : R.string.ClearRecentStickers),
        Lang.getString(R.string.Cancel)
      }, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_auto_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_done) {
          setShowRecents(false);
          if (animatedEmojiOnly) {
            ViewController<?> c = adapter.getCachedItem(0);
            if (c != null) {
              ((EmojiStatusListController) c).removeRecentStickers();
            }
            parentController.tdlib().client().send(new TdApi.ClearRecentEmojiStatuses(), parentController.tdlib().okHandler());
            return true;
          }
          ViewController<?> c = adapter.getCachedItem(1);
          if (c != null) {
            ((EmojiMediaListController) c).removeRecentStickers();
          }
          parentController.tdlib().client().send(new TdApi.ClearRecentStickers(), parentController.tdlib().okHandler());
        }
        return true;
      });
    }
  }

  private void clearRecentEmoji () {
    if (themeProvider != null) {
      themeProvider.showOptions(null, new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearRecentEmojiAction), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_auto_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_delete) {
          Emoji.instance().clearRecents();
          ViewController<?> c = adapter.getCachedItem(0);
          if (c != null && !animatedEmojiOnly) {
            ((EmojiListController) c).resetRecentEmoji();
          }
        }
        return true;
      });
    }
  }

  public void openEmojiSetOptions (final TGStickerSetInfo info) {
    if (themeProvider == null) return;

    boolean isTrending = info.isTrendingEmoji();
    themeProvider.showOptions(null, new int[] {
      R.id.btn_copyLink,
      isTrending ? R.id.btn_addStickerSet : R.id.more_btn_delete
    }, new String[] {
      Lang.getString(R.string.CopyLink),
      Lang.getString(isTrending ? R.string.AddPack : R.string.DeletePack)
    }, new int[] {
      ViewController.OptionColor.NORMAL,
      isTrending ? ViewController.OptionColor.NORMAL : ViewController.OptionColor.RED
    }, new int[] {
      R.drawable.baseline_link_24,
      isTrending ? R.drawable.deproko_baseline_insert_sticker_24 : R.drawable.baseline_delete_24
    }, (itemView, id) -> {
      if (id == R.id.more_btn_delete) {
        if (themeProvider != null) {
          themeProvider.showOptions(Lang.getStringBold(R.string.RemoveEmojiSet, info.getTitle()), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RemoveStickerSetAction), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (resultItemView, resultId) -> {
            if (resultId == R.id.btn_delete) {
              ViewController<?> c = adapter.getCachedItem(0);
              if (c != null) {
                ((EmojiStatusListController) c).removeStickerSet(info);
              }
              parentController.tdlib().client().send(new TdApi.ChangeStickerSet(info.getId(), false, false), parentController.tdlib().okHandler());
            }
            return true;
          });
        }
      } else if (id == R.id.btn_addStickerSet) {
        info.unsetIsTrendingEmoji();
        parentController.tdlib().client().send(new TdApi.ChangeStickerSet(info.getId(), true, false), parentController.tdlib().okHandler());
      } else if (id == R.id.btn_copyLink) {
        TdApi.StickerSetInfo stickerSetInfo = info.getInfo();
        if (stickerSetInfo != null) {
          String url = parentController.tdlib().tMeStickerSetUrl(stickerSetInfo);
          UI.copyText(url, R.string.CopiedLink);
        }
      }
      return true;
    });
  }

  public void removeStickerSet (final TGStickerSetInfo info) {
    if (animatedEmojiOnly) return;

    if (themeProvider != null) {
      themeProvider.showOptions(null, new int[] {R.id.btn_copyLink, R.id.btn_archive, R.id.more_btn_delete}, new String[] {Lang.getString(R.string.CopyLink), Lang.getString(R.string.ArchivePack), Lang.getString(R.string.DeletePack)}, new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED}, new int[] {R.drawable.baseline_link_24, R.drawable.baseline_archive_24, R.drawable.baseline_delete_24}, (itemView, id) -> {
        if (id == R.id.more_btn_delete) {
          if (themeProvider != null) {
            themeProvider.showOptions(Lang.getStringBold(R.string.RemoveStickerSet, info.getTitle()), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RemoveStickerSetAction), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (resultItemView, resultId) -> {
              if (resultId == R.id.btn_delete) {
                parentController.tdlib().client().send(new TdApi.ChangeStickerSet(info.getId(), false, false), parentController.tdlib().okHandler());
              }
              return true;
            });
          }
        } else if (id == R.id.btn_archive) {
          if (themeProvider != null) {
            themeProvider.showOptions(Lang.getStringBold(R.string.ArchiveStickerSet, info.getTitle()), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ArchiveStickerSetAction), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_archive_24, R.drawable.baseline_cancel_24}, (resultItemView, resultId) -> {
              if (resultId == R.id.btn_delete) {
                parentController.tdlib().client().send(new TdApi.ChangeStickerSet(info.getId(), false, true), parentController.tdlib().okHandler());
              }
              return true;
            });
          }
        } else if (id == R.id.btn_copyLink) {
          TdApi.StickerSetInfo stickerSetInfo = info.getInfo();
          if (stickerSetInfo != null) {
            String url = parentController.tdlib().tMeStickerSetUrl(stickerSetInfo);
            UI.copyText(url, R.string.CopiedLink);
          }
        }
        return true;
      });
    }
  }

  public boolean isUseDarkMode () {
    return useDarkMode;
  }

  private CircleButton circleButton;

  public void onEnterEmoji (String emoji) {
    if (listener != null) {
      listener.onEnterEmoji(emoji);
    }
  }

  private @Nullable ViewController<?> themeProvider;
  private boolean allowMedia;
  private boolean animatedEmojiOnly;
  private boolean classicEmojiOnly;
  private boolean allowPremiumFeatures;
  private boolean useDarkMode;

  public EmojiToneHelper.Delegate getToneDelegate () {
    return parentController != null && parentController instanceof EmojiToneHelper.Delegate ? (EmojiToneHelper.Delegate) parentController : null;
  }

  public boolean isAnimatedEmojiOnly () {
    return animatedEmojiOnly;
  }

  public boolean useDarkMode () {
    return useDarkMode;
  }

  public EmojiLayout (Context context) {
    super(context);
  }

  public FrameLayoutFix getHeaderView () {
    return headerView;
  }

  public void initWithEmojiStatus (ViewController<?> context, @NonNull Listener listener, @Nullable ViewController<?> themeProvider) {
    initWithMediasEnabled(context, false, true, listener, themeProvider, false, false);
  }

  public void initWithMediasEnabled (ViewController<?> context, boolean allowMedia, @NonNull Listener listener, @Nullable ViewController<?> themeProvider, boolean useDarkMode) {
    initWithMediasEnabled(context, allowMedia, false, listener, themeProvider, useDarkMode, false);
  }

  public int getEmojiSectionsSize () {
    return emojiSectionsSize;
  }

  public void initWithMediasEnabled (ViewController<?> context, boolean allowMedia, boolean animatedEmojiOnly, @NonNull Listener listener, @Nullable ViewController<?> themeProvider, boolean useDarkMode, boolean classicEmojiOnly) {
    this.parentController = context;
    this.listener = listener;
    this.themeProvider = themeProvider;
    this.allowMedia = allowMedia && !animatedEmojiOnly;
    this.animatedEmojiOnly = animatedEmojiOnly;
    this.classicEmojiOnly = classicEmojiOnly;
    this.useDarkMode = useDarkMode;

    /*
    this.emojiSections = new ArrayList<>();
    this.emojiSections.add(new EmojiSection(this, 0, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24).setFactor(1f, false).setMakeFirstTransparent().setOffsetHalf(false));
    this.emojiSections.add(new EmojiSection(this, 1, R.drawable.baseline_emoticon_outline_24, R.drawable.baseline_emoticon_24).setMakeFirstTransparent());
    this.emojiSections.add(new EmojiSection(this, 2, R.drawable.deproko_baseline_animals_outline_24, R.drawable.deproko_baseline_animals_24).setIsPanda(!useDarkMode));
    this.emojiSections.add(new EmojiSection(this, 3, R.drawable.baseline_restaurant_menu_24, R.drawable.baseline_restaurant_menu_24));
    this.emojiSections.add(new EmojiSection(this, 4, R.drawable.baseline_directions_car_24, R.drawable.baseline_directions_car_24));
    this.emojiSections.add(new EmojiSection(this, 5, R.drawable.deproko_baseline_lamp_24, R.drawable.deproko_baseline_lamp_filled_24));
    this.emojiSections.add(new EmojiSection(this, 6, R.drawable.deproko_baseline_flag_outline_24, R.drawable.deproko_baseline_flag_filled_24).setMakeFirstTransparent());

    if (allowMedia) {
      this.emojiSections.add(new EmojiSection(this, 7, R.drawable.deproko_baseline_stickers_24, 0).setActiveDisabled().setOffsetHalf(true));
    } else {
      this.emojiSections.get(this.emojiSections.size() - 1).setOffsetHalf(true);
    }
    */

    emojiSectionsSize = 7 + (allowMedia ? 1 : 0);

    adapter = new Adapter(context, this, allowMedia, themeProvider);
    pager = new RtlViewPager(getContext());
    pager.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    pager.addOnPageChangeListener(this);
    pager.setAdapter(adapter);
    pager.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    int headerSize = getHeaderSize();
    headerView = new FrameLayoutFix(getContext()) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        return true;
      }
    };
    if (useDarkMode) {
      headerView.setBackgroundColor(Theme.getColor(ColorId.filling, ThemeId.NIGHT_BLACK));
    } else {
      ViewSupport.setThemedBackground(headerView, ColorId.filling, themeProvider);
    }
    headerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerSize));

    // Emoji sections

    if (!animatedEmojiOnly) {
      ArrayList<EmojiSection> emojiSections = new ArrayList<>(2);
      emojiSections.add(new EmojiSection(this, EmojiSection.SECTION_EMOJI_TRENDING, R.drawable.outline_whatshot_24, R.drawable.baseline_whatshot_24).setMakeFirstTransparent());
      emojiSections.add(new EmojiSection(this, EmojiSection.SECTION_EMOJI_RECENT, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24)/*.setFactor(1f, false)*/.setMakeFirstTransparent().setOffsetHalf(false));

      ArrayList<EmojiSection> expandableSections = new ArrayList<>(6);
      expandableSections.add(new EmojiSection(this, EmojiSection.SECTION_EMOJI_SMILEYS, R.drawable.baseline_emoticon_outline_24, R.drawable.baseline_emoticon_24).setMakeFirstTransparent());
      expandableSections.add(new EmojiSection(this,  EmojiSection.SECTION_EMOJI_ANIMALS, R.drawable.deproko_baseline_animals_outline_24, R.drawable.deproko_baseline_animals_24));/*.setIsPanda(!useDarkMode)*/
      expandableSections.add(new EmojiSection(this,  EmojiSection.SECTION_EMOJI_FOOD, R.drawable.baseline_restaurant_menu_24, R.drawable.baseline_restaurant_menu_24));
      expandableSections.add(new EmojiSection(this,  EmojiSection.SECTION_EMOJI_TRAVEL, R.drawable.baseline_directions_car_24, R.drawable.baseline_directions_car_24));
      expandableSections.add(new EmojiSection(this,  EmojiSection.SECTION_EMOJI_SYMBOLS, R.drawable.deproko_baseline_lamp_24, R.drawable.deproko_baseline_lamp_filled_24));
      expandableSections.add(new EmojiSection(this,  EmojiSection.SECTION_EMOJI_FLAGS, R.drawable.deproko_baseline_flag_outline_24, R.drawable.deproko_baseline_flag_filled_24).setMakeFirstTransparent());

      emojiHeaderView = new EmojiHeaderView(getContext(), this, themeProvider, emojiSections, expandableSections, allowMedia);
      emojiHeaderView.setSectionsOnClickListener(this);
      emojiHeaderView.setSectionsOnLongClickListener(this::onEmojiHeaderLongClick);
      checkAllowPremiumFeatures();
      headerView.addView(emojiHeaderView);
    }


    // Media sections

    if (allowMedia || animatedEmojiOnly) {
      mediaSectionsView = new MediaHeaderView(getContext());
      mediaSectionsView.init(this, themeProvider, this);
      headerView.addView(mediaSectionsView);
    } else {
      mediaSectionsView = null;
    }

    // Shadow and etc

    shadowView = new ShadowView(getContext());
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(shadowView);
    }
    shadowView.setSimpleBottomTransparentShadow(true);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(shadowView.getLayoutParams().width, shadowView.getLayoutParams().height);
    params.topMargin = headerSize;
    shadowView.setLayoutParams(params);

    int position;
    if (allowMedia) {
      position = Settings.instance().getEmojiPosition();
      if (pager.getCurrentItem() != position) {
        pager.setCurrentItem(position, false);
      }
    } else {
      position = 0;
    }

    final int padding = Screen.dp(4);
    params = FrameLayoutFix.newParams(Screen.dp(23f) * 2 + padding * 2, Screen.dp(23f) * 2 + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;

    circleButton = new CircleButton(getContext());
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(circleButton);
    }
    circleButton.setId(R.id.btn_circle);
    if (position == 0) {
      circleButton.init(R.drawable.baseline_backspace_24, -Screen.dp(BACKSPACE_OFFSET), 46f, 4f, ColorId.circleButtonOverlay, ColorId.circleButtonOverlayIcon);
      setCircleVisible(hasLeftButton(), false, 0, 0);
    } else {
      circleButton.init(R.drawable.baseline_search_24, 46f, 4f, ColorId.circleButtonOverlay, ColorId.circleButtonOverlayIcon);
      setCircleVisible(hasRightButton(), false, 0, 0);
    }
    circleButton.setOnClickListener(this);
    circleButton.setLayoutParams(params);
    updateCircleStyles();

    addView(pager);
    addView(headerView);
    addView(shadowView);
    addView(circleButton);

    checkBackground();
    // NewEmoji.instance().loadAllEmoji();

    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  private void checkBackground () {
    if (useDarkMode) {
      setBackgroundColor(Theme.getColor(ColorId.chatKeyboard, ThemeId.NIGHT_BLACK));
    } else {
      ViewSupport.setThemedBackground(this, isOptimizedForDisplayMessageOptionsWindow ? ColorId.filling : ColorId.chatKeyboard, themeProvider);
    }
  }

  public void setAllowPremiumFeatures (boolean allowPremiumFeatures) {
    this.allowPremiumFeatures = allowPremiumFeatures;
    checkAllowPremiumFeatures();
  }

  private void checkAllowPremiumFeatures () {
    if (emojiHeaderView != null && parentController != null) {
      emojiHeaderView.setIsPremium((allowPremiumFeatures || parentController.tdlib().hasPremium()) && !classicEmojiOnly, false);
    }
  }

  public void onTextChanged (CharSequence charSequence) {
    if (pager.getCurrentItem() == 0) {
      setCircleVisible(charSequence.length() > 0, true, R.drawable.baseline_backspace_24, -Screen.dp(BACKSPACE_OFFSET));
    }
  }

  private static final float BACKSPACE_OFFSET = 1.5f;

  private static final int CIRCLE_ANIMATOR = 1;
  private FactorAnimator circleAnimator;

  private boolean isCircleButtonVisible = true;
  private float circleFactor = 1f;

  private void setCircleVisible (boolean isVisible, boolean animated, @DrawableRes int resourceIfNotVisible, int offsetLeft) {
    if (this.isCircleButtonVisible != isVisible) {
      if (isVisible && resourceIfNotVisible != 0) {
        circleButton.setIcon(resourceIfNotVisible, offsetLeft);
      }
      this.isCircleButtonVisible = isVisible;
      setCircleFactor(isVisible ? 1f : 0f, animated);
    }
  }

  public void setCircleVisible (boolean isVisible, boolean isSearch) {
    if (!isSearch || !noInlineSearch) {
      setCircleVisible(isVisible, true, isSearch ? R.drawable.baseline_search_24 : R.drawable.baseline_backspace_24, isSearch ? 0 : -Screen.dp(BACKSPACE_OFFSET));
    }
  }

  private boolean isOptimizedForDisplayMessageOptionsWindow;

  public void optimizeForDisplayMessageOptionsWindow (boolean needOptimize) {
    isOptimizedForDisplayMessageOptionsWindow = needOptimize;
    optimizeForDisplayTextFormattingLayout(needOptimize);
    checkBackground();
  }

  public void optimizeForDisplayTextFormattingLayout (boolean needOptimize) {
    int visibility = needOptimize ? GONE : VISIBLE;
    if (headerView != null) headerView.setVisibility(needOptimize ? INVISIBLE : VISIBLE);
    if (shadowView != null) shadowView.setVisibility(visibility);
    if (pager != null) pager.setVisibility(visibility);
    if (circleButton != null) circleButton.setVisibility(visibility);
  }

  public int getCurrentItem () {
    return pager.getCurrentItem();
  }

  private void setCircleFactor (float toFactor, boolean animated) {
    if (this.circleFactor != toFactor && animated && getVisibility() == View.VISIBLE) {
      if (circleAnimator == null) {
        circleAnimator = new FactorAnimator(CIRCLE_ANIMATOR, this, AnimatorUtils.OVERSHOOT_INTERPOLATOR, 210, circleFactor);
      }

      if (toFactor == 1f && circleFactor == 0f) {
        circleAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
        circleAnimator.setDuration(210L);
      } else {
        circleAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        circleAnimator.setDuration(100L);
      }

      circleAnimator.animateTo(toFactor);
    } else {
      if (circleAnimator != null) {
        circleAnimator.forceFactor(toFactor);
      }
      setCircleFactor(toFactor);
    }
  }

  private static final float MIN_SCALE = .6f;

  private void setCircleFactor (float factor) {
    if (this.circleFactor != factor) {
      this.circleFactor = factor;
      updateCircleStyles();
    }
  }

  private void updateCircleStyles () {
    if (circleButton != null) {
      float factor = circleFactor;
      final float scale = MIN_SCALE + (1f - MIN_SCALE) * factor;
      final float alpha = Math.min(1f, Math.max(0f, factor));
      circleButton.setAlpha(alpha);
      circleButton.setScaleX(scale);
      circleButton.setScaleY(scale);
    }
  }

  public void setShowRecents (boolean showRecents) {
    mediaSectionsView.setShowRecents(showRecents);
  }

  public void setShowFavorite (boolean showFavorite) {
    mediaSectionsView.setShowFavorite(showFavorite);
  }

  public void setStickerSets (ArrayList<TGStickerSetInfo> stickers, boolean showFavorite, boolean showRecents) {
    setStickerSets(stickers, showFavorite, showRecents, false, false);
  }

  public void setStickerSets (ArrayList<TGStickerSetInfo> stickers, boolean showFavorite, boolean showRecents, boolean showTrending, boolean isFound) {
    mediaSectionsView.setStickerSets(stickers, showFavorite, showRecents, showTrending, isFound);
  }

  public void setEmojiPacks (ArrayList<TGStickerSetInfo> stickers) {
    if (emojiHeaderView != null) {
      emojiHeaderView.setStickerSets(stickers);
    }
  }

  public void invalidateStickerSets () {
    mediaSectionsView.invalidateStickerSets();
  }

  private void scrollToStickerSet (@NonNull TGStickerSetInfo stickerSet) {
    if (animatedEmojiOnly) {
      ViewController<?> c = adapter.getCachedItem(0);
      if (c != null) {
        ((EmojiStatusListController) c).showStickerSet(stickerSet);
      }
      return;
    }

    if (stickerSet.isEmoji()) {
      ViewController<?> c = adapter.getCachedItem(0);
      if (c != null) {
        ((EmojiListController) c).showStickerSet(stickerSet);
      }
      return;

    }

    ViewController<?> c = adapter.getCachedItem(1);
    if (c != null) {
      ((EmojiMediaListController) c).showStickerSet(stickerSet);
    }
  }

  private void scrollToEmojiSection (int sectionIndex) {
    ViewController<?> c = adapter.getCachedItem(0);
    if (c != null && !animatedEmojiOnly) {
      ((EmojiListController) c).showEmojiSection(sectionIndex);
    }
  }

  public boolean setEmojiStatus (View view, TGStickerObj sticker, long expirationDate) {
    return listener != null && listener.onSetEmojiStatus(view, sticker, new TdApi.EmojiStatus(sticker.getCustomEmojiId(), (int) expirationDate));
  }

  public boolean sendSticker (View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
    return listener != null && listener.onSendSticker(view, sticker, sendOptions);
  }

  public void onEnterCustomEmoji (TGStickerObj sticker) {
    if (!sticker.isRecent()) {
      Emoji.instance().saveRecentCustomEmoji(sticker.getCustomEmojiId());
    }
    if (listener != null) {
      listener.onEnterCustomEmoji(sticker);
    }
  }

  public long findOutputChatId () {
    return listener != null ? listener.getOutputChatId() : 0;
  }

  public boolean sendGif (View view, TdApi.Animation animation) {
    return listener != null && listener.onSendGIF(view, animation);
  }

  public boolean onEmojiHeaderLongClick (View v) {
    int viewId = v.getId();

    if (v instanceof StickerSectionView) {
      StickerSectionView sectionView = (StickerSectionView) v;
      TGStickerSetInfo info = sectionView.getStickerSet();
      removeStickerSet(info);
      return true;
    } else if (viewId == R.id.btn_section) {
      EmojiSection section = ((EmojiSectionView) v).getSection();

      if (section.index == 0 && Emoji.instance().canClearRecents()) {
        clearRecentEmoji();
        return true;
      }
    }
    return false;
  }

  @Override
  public void onClick (View v) {
    if (scrollState != ViewPager.SCROLL_STATE_IDLE) {
      return;
    }

    final int viewId = v.getId();
    if (viewId == R.id.btn_stickerSet) {
      TGStickerSetInfo info = ((StickerSectionView) v).getStickerSet();

      if (info != null) {
        scrollToStickerSet(info);
      }
    } else if (viewId == R.id.btn_circle) {
      switch (pager.getCurrentItem()) {
        case 0: {
          if (listener != null) {
            listener.onDeleteEmoji();
          }
          break;
        }
        case 1: {
          if (listener != null) {
            listener.onSearchRequested(this, false);
          }
          break;
        }
      }
    } else if (viewId == R.id.btn_section) {
      EmojiSection section = ((EmojiSectionView) v).getSection();

      int prevSection = getCurrentEmojiSection();
      int newSection = -1;

      if (animatedEmojiOnly) {
        ViewController<?> c = adapter.getCachedItem(0);
        if (c != null) {
          if (section.isTrending()) {
            ((EmojiStatusListController) c).scrollToTrendingStickers(true);
          } else {
            ((EmojiStatusListController) c).scrollToSystemStickers(true);
          }
        }
      } else if (section.index >= 0) {
        scrollToEmojiSection(section.index);
        newSection = EmojiMediaType.EMOJI;
      } else {
        if (section.index == EmojiSection.SECTION_EMOJI_TRENDING) {
          ViewController<?> c = adapter.getCachedItem(0);
          if (c instanceof EmojiListController) {
            ((EmojiListController) c).showTrending();
          }
        } else if (section.index == EmojiSection.SECTION_SWITCH_TO_MEDIA) {
          pager.setCurrentItem(1, true);
          newSection = getCurrentMediaEmojiSection();
        }
        int index = -(section.index) - 1;
        switch (index) {
          case 0: {
            pager.setCurrentItem(0, true);
            newSection = EmojiMediaType.EMOJI;
            break;
          }
          case 1: {
            ViewController<?> c = adapter.getCachedItem(1);
            if (c != null) {
              boolean shownGifs = ((EmojiMediaListController) c).showGIFs();
              if (!shownGifs && listener != null) {
                listener.onSearchRequested(this, false);
              }
            }
            break;
          }
          case 2: {
            ViewController<?> c = adapter.getCachedItem(1);
            if (c != null) {
              ((EmojiMediaListController) c).showHot();
            }
            break;
          }
          case 3: {
            ViewController<?> c = adapter.getCachedItem(1);
            if (c != null) {
              ((EmojiMediaListController) c).showSystemStickers();
            }
            break;
          }
        }
      }

      if (listener != null && newSection != -1) {
        listener.onSectionSwitched(this, newSection, prevSection);
      }
    }
  }

  private float headerHideFactor;
  private float headerOffset;

  public float getHeaderHideFactor () {
    return headerHideFactor;
  }

  public void setHeaderOffset (float offset) {
    setHeaderHideFactor (headerHideFactor, offset);
  }

  public void setHeaderHideFactor (float factor) {
    setHeaderHideFactor(factor, headerOffset);
  }

  public void setHeaderHideFactor (float factor, float offset) {
    if (animatedEmojiOnly) {
      factor = 0f;
    }

    if (this.headerHideFactor != factor || headerOffset != offset) {
      this.headerHideFactor = factor;
      this.headerOffset = offset;
      float y = ((float) -getHeaderSize()) * headerHideFactor;
      headerView.setTranslationY(y + offset);
      shadowView.setTranslationY(y + offset);
      float alpha = 1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(Math.max(0f, Math.min(1f, factor / .5f)));
      if (emojiHeaderView != null) {
        emojiHeaderView.setAlpha(alpha);
      }
      if (mediaSectionsView != null) {
        mediaSectionsView.setAlpha(alpha);
      }
    }
  }

  public int getHeaderBottom () {
    return (int) (getHeaderSize() * (1f - headerHideFactor)) + Screen.dp(12f);
  }

  private void showOrHideHeader () {
    if (headerHideFactor != 0f && headerHideFactor != 1f) {
      float hideFactor = headerHideFactor > .25f && lastY - getHeaderSize() > 0 ? 1f : 0f;
      moveHeaderImpl(hideFactor, true);
    }
  }

  private void moveHeaderImpl (float factor, boolean animated) {
    if (factor == 1f) {
      lastHeaderVisibleY = Math.max(0, lastY - getHeaderSize());
    } else  {
      lastHeaderVisibleY = lastDesiredHeaderVisibleY = lastY;
    }
    setHeaderHideFactor(factor, animated);
  }

  private static final int HIDE_ANIMATOR = 0;
  private FactorAnimator hideAnimator;

  public void setHeaderHideFactor (float factor, boolean animated) {
    if (animated) {
      if (hideAnimator == null) {
        hideAnimator = new FactorAnimator(HIDE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 210L, headerHideFactor);
      }
      hideAnimator.animateTo(factor);
    } else {
      if (hideAnimator != null) {
        hideAnimator.forceFactor(factor);
      }
      setHeaderHideFactor(factor);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case HIDE_ANIMATOR: {
        setHeaderHideFactor(factor);
        break;
      }
      case CIRCLE_ANIMATOR: {
        setCircleFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private int lastY;
  private int lastHeaderVisibleY;
  private int lastDesiredHeaderVisibleY;
  private boolean ignoreMovement;

  private void resetScrollingCache (int scrollY, boolean silent) {
    lastY = scrollY;
    int headerOffset = (int) (headerHideFactor * (float) getHeaderSize());
    lastHeaderVisibleY = lastDesiredHeaderVisibleY = Math.max(0, scrollY - headerOffset);
    if (scrollY - headerOffset < 0) {
      setHeaderHideFactor(0f);
    }
  }

  private boolean ignoreFirstScrollEvent;

  public boolean isWatchingMovements () {
    return !ignoreMovement;
  }

  public void setIgnoreMovement (boolean ignoreMovement) {
    if (this.ignoreMovement != ignoreMovement) {
      this.ignoreMovement = ignoreMovement;

      if (ignoreMovement) {
        ignoreFirstScrollEvent = true;
      } else {
        resetScrollState(false);
      }
    }
  }

  public void moveHeaderFull (int y) {
    if (ignoreFirstScrollEvent) {
      resetScrollState(false);
      ignoreFirstScrollEvent = false;
      return;
    }
    if (ignoreMovement || scrollState != org.thunderdog.challegram.widget.ViewPager.SCROLL_STATE_IDLE) {
      return;
    }
    lastHeaderVisibleY = 0;
    lastY = y;
    setHeaderHideFactor(Math.min(1f, Math.max(0f, (float) y / (float) getHeaderSize())), false);
    setCircleVisible(headerHideFactor == 0f, true);
  }

  private void moveHeaderImpl (int y) {
    lastY = y;
    if (ignoreFirstScrollEvent) {
      resetScrollState(false);
      ignoreFirstScrollEvent = false;
      return;
    }
    if (ignoreMovement || scrollState != org.thunderdog.challegram.widget.ViewPager.SCROLL_STATE_IDLE) {
      return;
    }

    float hideFactor = Math.max(0f, Math.min(1f, (float) (y - lastHeaderVisibleY) / (float) getHeaderSize()));

    if (hideFactor == 1f) {
      lastDesiredHeaderVisibleY = Math.max(0, y - getHeaderSize());
    } else if (hideFactor == 0f) {
      lastHeaderVisibleY = lastDesiredHeaderVisibleY = y;
    }
    setHeaderHideFactor(hideFactor, false);
  }

  public void applyHeaderVisibleY () {
    lastHeaderVisibleY = lastDesiredHeaderVisibleY;
  }

  private boolean isScrolling;

  public void setIsScrolling (boolean isScrolling) {
    if (this.isScrolling != isScrolling) {
      this.isScrolling = isScrolling;

      if (!isScrolling) {
        applyHeaderVisibleY();
        showOrHideHeader();
      }
    }
  }

  public void putCachedItem (ViewController<?> c, int position) {
    adapter.cachedItems.put(position, c);
  }

  private static class Adapter extends PagerAdapter {
    private final ViewController<?> context;
    private final EmojiLayout parent;
    private final SparseArrayCompat<ViewController<?>> cachedItems;
    private final boolean allowMedia;
    private final ViewController<?> themeProvider;

    public Adapter (ViewController<?> context, EmojiLayout parent, boolean allowMedia, @Nullable ViewController<?> themeProvider) {
      this.context = context;
      this.parent = parent;
      this.cachedItems = new SparseArrayCompat<>(2);
      this.themeProvider = themeProvider;
      this.allowMedia = allowMedia;
    }

    public @Nullable ViewController<?> getCachedItem (int position) {
      return cachedItems.get(position);
    }

    public void updateCachedItemsSpanCounts () {
      for (int i = 0; i < cachedItems.size(); i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        final int controllerId = c.getId();
        if (controllerId == R.id.controller_emoji) {
          ((EmojiListController) c).checkSpanCount();
        } else if (controllerId == R.id.controller_emojiMedia) {
          ((EmojiMediaListController) c).checkSpanCount();
        } else if (controllerId == R.id.controller_emojiCustom) {
          ((EmojiStatusListController) c).checkSpanCount();
        }
      }
      parent.resetScrollState(false);
    }

    public void invalidateCachedItems () {
      for (int i = 0; i < cachedItems.size(); i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        if (c.getId() == R.id.controller_emoji) {
          ((EmojiListController) c).invalidateItems();
        }
      }
    }

    public void destroyCachedItems () {
      final int count = cachedItems.size();
      for (int i = 0; i < count; i++) {
        cachedItems.valueAt(i).destroy();
      }
      cachedItems.clear();
    }

    @Override
    public int getCount () {
      return allowMedia ? 2 : 1;
    }

    @Override
    public void destroyItem (ViewGroup container, int position, @NonNull Object object) {
      container.removeView(((ViewController<?>) object).getValue());
    }

    @Override
    public Object instantiateItem (@NonNull ViewGroup container, int position) {
      ViewController<?> c = cachedItems.get(position);
      if (c == null) {
        if (position == 0) {
          if (parent.animatedEmojiOnly) {
            EmojiStatusListController mediaListController = new EmojiStatusListController(context.context(), context.tdlib());
            mediaListController.setArguments(parent);
            c = mediaListController;
          } else {
            EmojiListController emojiListController = new EmojiListController(context.context(), context.tdlib(), parent.classicEmojiOnly);
            emojiListController.setArguments(parent);
            c = emojiListController;
          }
        } else if (position == 1) {
          EmojiMediaListController mediaListController = new EmojiMediaListController(context.context(), context.tdlib());
          mediaListController.setArguments(parent);
          c = mediaListController;
        } else {
          throw new RuntimeException("position == " + position);
        }
        cachedItems.put(position, c);
        if (themeProvider != null) {
          c.bindThemeListeners(themeProvider);
        }
      }
      container.addView(c.getValue());
      return c;
    }

    @Override
    public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
      return object instanceof ViewController && ((ViewController<?>) object).getValue() == view;
    }
  }

  private int scrollState;
  private float currentPageFactor;

  private void setCurrentPageFactor (float factor) {
    if (this.currentPageFactor != factor) {
      this.currentPageFactor = factor;
      updatePositions();
    }
  }

  private boolean hasLeftButton () {
    return listener != null && !listener.isEmojiInputEmpty();
  }

  private boolean noInlineSearch;

  public void setNoInlineSearch () {
    noInlineSearch = true;
  }

  private boolean hasRightButton () {
    ViewController<?> c = adapter.getCachedItem(1);
    return c != null && !noInlineSearch && ((EmojiMediaListController) c).needSearchButton();
  }

  private void updatePositions () {
    float currentPageFactor = animatedEmojiOnly ? 1f : this.currentPageFactor;
    if (emojiHeaderView != null) {
      emojiHeaderView.setTranslationX((float) (emojiHeaderView.getMeasuredWidth()) * currentPageFactor * (Lang.rtl() ? 1f : -1f));
    }
    if (mediaSectionsView != null) {
      mediaSectionsView.setTranslationX(mediaSectionsView.getMeasuredWidth() * (1f - currentPageFactor) * (Lang.rtl() ? -1f : 1f));
    }
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    setCurrentPageFactor((float) position + positionOffset);

    if (affectHeight) {
      float factor = fromHeightHideFactor + Math.abs(fromPageFactor - currentPageFactor) * heightFactorDiff;
      moveHeaderImpl(factor, false);
    }
  }

  @Override
  public void onPageSelected (int position) {
    Settings.instance().setEmojiPosition(position);
    boolean hasLeft = hasLeftButton();
    boolean hasRight = hasRightButton();
    if (hasLeft && hasRight) {
      if (position == 0) {
        circleButton.replaceIcon(R.drawable.baseline_backspace_24, -Screen.dp(BACKSPACE_OFFSET));
      } else {
        circleButton.replaceIcon(R.drawable.baseline_search_24);
      }
    } else if (hasLeft || hasRight) {
      setCircleVisible((hasLeft && position == 0) || (hasRight && position == 1), true, position == 0 ? R.drawable.baseline_backspace_24 : R.drawable.baseline_search_24, position == 0 ? -Screen.dp(BACKSPACE_OFFSET) : 0);
    }
    resetScrollState(false);
  }

  private boolean affectHeight;
  private float fromHeightHideFactor, heightFactorDiff, fromPageFactor;

  private float getDesiredPageFactor (int pageIndex) {
    if (pageIndex == 1) {
      ViewController<?> c = adapter.getCachedItem(1);
      if (c != null) {
        return ((EmojiMediaListController) c).getDesiredHeaderHideFactor();
      }
    }
    return 0f;
  }

  private void setAffectHeight (boolean affectHeight) {
    if (this.affectHeight != affectHeight) {
      this.affectHeight = affectHeight;
      if (affectHeight) {
        fromHeightHideFactor = headerHideFactor;
        fromPageFactor = currentPageFactor;
        float toFactor = getDesiredPageFactor(1 - Math.round(currentPageFactor));
        heightFactorDiff = toFactor - headerHideFactor;
      }
    }
  }

  @Override
  public void onPageScrollStateChanged (int state) {
    this.scrollState = state;
    setAffectHeight(state != ViewPager.SCROLL_STATE_IDLE);
  }

  public boolean canSlideBack () {
    return scrollState == ViewPager.SCROLL_STATE_IDLE && currentPageFactor == 0f;
  }

  // layout and callback

  public void setListener (@Nullable Listener listener) {
    this.listener = listener;
  }

  private int lastMeasuredWidth;
  private int forceHeight = -1;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(forceHeight > 0 ? forceHeight : Keyboard.getSize(), MeasureSpec.EXACTLY));
    checkWidth(getMeasuredWidth());
  }

  public boolean checkWidth (int width) {
    if (width != 0 && lastMeasuredWidth != width) {
      lastMeasuredWidth = width;
      updatePositions();
      adapter.updateCachedItemsSpanCounts();
      return true;
    }
    return false;
  }

  public void setForceHeight (int forceHeight) {
    this.forceHeight = forceHeight;
  }

  // Icon

  private int getCurrentMediaEmojiSection () {
    ViewController<?> c = adapter.getCachedItem(1);
    if (c instanceof EmojiMediaListController) {
      return ((EmojiMediaListController) c).getMediaSection();
    }
    return Settings.instance().getEmojiMediaSection();
  }

  public @EmojiMediaType int getCurrentEmojiSection () {
    if (allowMedia) {
      int currentItem = pager.getCurrentItem();
      if (currentItem == 1) {
        return getCurrentMediaEmojiSection();
      }
    }
    return EmojiMediaType.EMOJI;
  }

  public void setPreferredSection (@EmojiMediaType int section) {
    if (allowMedia) {
      Settings.instance().setEmojiMediaSection(section);
      setMediaSection(section == EmojiMediaType.GIF);
    }
  }

  public Listener getListener () {
    return listener;
  }

  public void setMediaSection (boolean isGif) {
    if (emojiHeaderView != null) {
      emojiHeaderView.setMediaSection(isGif);
    }
  }

  public static @EmojiMediaType int getTargetSection () {
    int position = Settings.instance().getEmojiPosition();
    if (position == 0) {
      return EmojiMediaType.EMOJI;
    }
    return Settings.instance().getEmojiMediaSection();
  }

  public static @DrawableRes int getTargetIcon (boolean isMessage) {
    int position = Settings.instance().getEmojiPosition();

    if (position == 0) {
      return isMessage ? R.drawable.deproko_baseline_insert_emoticon_26 : R.drawable.baseline_emoticon_outline_24;
    }

    int section = Settings.instance().getEmojiMediaSection();
    if (isMessage) {
      return section == EmojiMediaType.GIF ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_insert_sticker_26;
    } else {
      return section == EmojiMediaType.GIF ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_stickers_24;
    }
  }

  // Legacy

  public void reset () {
    ViewController<?> c = adapter.getCachedItem(1);
    if (c != null) {
      ((EmojiMediaListController) c).applyScheduledChanges();
    }
    ViewController<?> c2 = adapter.getCachedItem(0);
    if (c2 instanceof EmojiListController) {
      ((EmojiListController) c2).applyScheduledChanges();
    }
  }

  public void destroy () {
    adapter.destroyCachedItems();
  }

  public void rebuildLayout () {
    // Nothing to do?
  }

  public void invalidateAll () {
    if (adapter != null) {
      adapter.invalidateCachedItems();
    }
  }

  public int getSize () {
    return Keyboard.getSize();
  }

  private static final int STATE_NONE = 0;
  private static final int STATE_AWAITING_SHOW = 1;
  private static final int STATE_AWAITING_HIDE = 2;

  private int keyboardState;

  public void showKeyboard (android.widget.EditText input) {
    keyboardState = STATE_AWAITING_SHOW;
    Keyboard.show(input);
  }

  public void hideKeyboard (android.widget.EditText input) {
    keyboardState = STATE_AWAITING_HIDE;
    Keyboard.hide(input);
  }

  public void onKeyboardStateChanged (boolean visible) {
    if (keyboardState == STATE_AWAITING_SHOW && visible) {
      framesDropped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? 45 : 55;
    } else if (keyboardState == STATE_AWAITING_HIDE && !visible) {
      keyboardState = STATE_NONE;
    }
  }

  private int framesDropped;

  @Override
  public boolean onPreDraw () {
    if (keyboardState == STATE_AWAITING_SHOW || keyboardState == STATE_AWAITING_HIDE) {
      if (++framesDropped >= 60) {
        framesDropped = 0;
        keyboardState = STATE_NONE;
        return true;
      }
      return false;
    }

    return true;
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    if (Lang.hasDirectionChanged(event, arg1)) {
      if (mediaSectionsView != null) {
        ((LinearLayoutManager) mediaSectionsView.getLayoutManager()).setReverseLayout(Lang.rtl());
      }
      if (pager != null) {
        pager.checkRtl();
      }
    }
  }


  /* Interface */

  public static final @IdRes int STICKERS_INSTALLED_CONTROLLER_ID = R.id.controller_emojiLayoutStickers;
  public static final @IdRes int STICKERS_TRENDING_CONTROLLER_ID = R.id.controller_emojiLayoutStickersTrending;
  public static final @IdRes int EMOJI_INSTALLED_CONTROLLER_ID = R.id.controller_emojiLayoutEmoji;
  public static final @IdRes int EMOJI_TRENDING_CONTROLLER_ID = R.id.controller_emojiLayoutEmojiTrending;

  public static @EmojiMediaType int getEmojiMediaType (int controllerId) {
    return controllerId == R.id.controller_emojiLayoutEmojiTrending
      || controllerId == R.id.controller_emojiLayoutEmoji ? EmojiMediaType.EMOJI : EmojiMediaType.STICKER;
  }

  @Override
  public void onAddStickerSection (@IdRes int controllerId, int section, TGStickerSetInfo info) {
    if (controllerId == EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID && mediaSectionsView != null) {
      mediaSectionsView.addStickerSection(section, info);
    } else if (controllerId == EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID && emojiHeaderView != null) {
      emojiHeaderView.addStickerSection(section, info);
    }
  }

  @Override
  public void onMoveStickerSection (@IdRes int controllerId, int fromSection, int toSection) {
    if (controllerId == EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID && mediaSectionsView != null) {
      mediaSectionsView.moveStickerSection(fromSection, toSection);
    } else if (controllerId == EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID && emojiHeaderView != null) {
      emojiHeaderView.moveStickerSection(fromSection, toSection);
    }
  }

  @Override
  public void onRemoveStickerSection (@IdRes int controllerId, int section) {
    if (controllerId == EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID && mediaSectionsView != null) {
      mediaSectionsView.removeStickerSection(section);
    } else if (controllerId == EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID && emojiHeaderView != null) {
      emojiHeaderView.removeStickerSection(section);
    }
  }

  @Override
  public void setCurrentStickerSectionByPosition (@IdRes int controllerId, int i, boolean isStickerSection, boolean animated) {
    if (controllerId == R.id.controller_emojiLayoutStickers && mediaSectionsView != null) {
      mediaSectionsView.setCurrentStickerSectionByPosition(i, isStickerSection, animated);
    } else if (controllerId == EMOJI_INSTALLED_CONTROLLER_ID && emojiHeaderView != null) {
      emojiHeaderView.setCurrentStickerSectionByPosition(i + (isStickerSection ? 1: 0), animated);
    }
  }

  @Override
  public boolean onStickerClick (@IdRes int controllerId, StickerSmallView view, View clickView, TGStickerSetInfo stickerSet, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    if (sticker.isTrending() && !isMenuClick) {
      if (stickerSet != null) {
        stickerSet.show(parentController);
        return true;
      }
      return false;
    } else if (sticker.isCustomEmoji()) {
      onEnterCustomEmoji(sticker);
      return true;
    } else {
      return sendSticker(clickView, sticker, sendOptions);
    }
  }

  @Override
  public boolean canFindChildViewUnder (int controllerId, StickerSmallView view, int recyclerX, int recyclerY) {
    return recyclerY > getHeaderBottom();
  }

  public void setHasNewHots (@IdRes int controllerId, boolean hasHots) {
    if (controllerId == STICKERS_TRENDING_CONTROLLER_ID && mediaSectionsView != null) {
      mediaSectionsView.setHasNewHots(hasHots);
    }
  }

  @Override
  public void onSectionInteracted (@EmojiMediaType int mediaType, boolean interactionFinished) {
    if (listener != null) {
      listener.onSectionInteracted(this, mediaType, interactionFinished);
    }
  }

  @Override
  public void onSectionInteractedScroll (@EmojiMediaType int mediaType, boolean moved) {
    if (moved) {
      onSectionInteracted(mediaType, false);
    }
  }

  @Override
  public void moveHeader (int totalDy) {
    moveHeaderImpl(totalDy);
  }

  @Override
  public void resetScrollState (boolean silent) {
    switch (pager.getCurrentItem()) {
      case 0: {
        ViewController<?> c = adapter.getCachedItem(0);
        if (c != null && !animatedEmojiOnly) {
          resetScrollingCache(((EmojiListController) c).getCurrentScrollY(), silent);
        }
        break;
      }
      case 1: {
        ViewController<?> c = adapter.getCachedItem(1);
        if (c != null) {
          resetScrollingCache(((EmojiMediaListController) c).getCurrentScrollY(), silent);
        }
        break;
      }
    }
  }

}
