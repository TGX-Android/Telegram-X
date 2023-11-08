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
 * File created on 19/08/2023
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.StickerSuggestionAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.AnimatedFrameLayout;
import me.vkryl.android.widget.FrameLayoutFix;

public class StickersSuggestionsLayout extends AnimatedFrameLayout implements FactorAnimator.Target {
  private static final int ANIMATOR_STICKERS = 1;

  private final ImageView stickerSuggestionArrowView;
  private final LinearLayoutManager manager;
  private final RecyclerView stickerSuggestionsView;
  private StickerSuggestionAdapter stickerSuggestionAdapter;

  private MessagesController parent;
  private FactorAnimator stickersAnimator;
  private Delegate delegate;
  private boolean choosingSuggestionSent;
  private boolean areStickersVisible;
  private boolean isEmoji;

  public interface Delegate {
    void notifyChoosingEmoji (int emojiType, boolean isChoosingEmoji);
  }

  public StickersSuggestionsLayout (Context context) {
    super(context);

    manager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);

    stickerSuggestionsView = new RecyclerView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return areStickersVisible && getAlpha() == 1f && super.onTouchEvent(e);
      }
    };
    stickerSuggestionsView.setItemAnimator(null);
    stickerSuggestionsView.setPadding(Screen.dp(48), 0, Screen.dp(48), 0);
    stickerSuggestionsView.setClipToPadding(false);
    stickerSuggestionsView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    stickerSuggestionsView.setLayoutManager(manager);

    addView(stickerSuggestionsView);

    stickerSuggestionArrowView = new ImageView(context);
    stickerSuggestionArrowView.setScaleType(ImageView.ScaleType.CENTER);
    stickerSuggestionArrowView.setImageResource(R.drawable.stickers_back_arrow);
    stickerSuggestionArrowView.setColorFilter(new PorterDuffColorFilter(Theme.headerFloatBackgroundColor(), PorterDuff.Mode.MULTIPLY));
    addView(stickerSuggestionArrowView);
  }

  public void init (@NonNull MessagesController parent, boolean isEmoji) {
    this.parent = parent;
    this.isEmoji = isEmoji;
    this.stickerSuggestionAdapter = new StickerSuggestionAdapter(parent, manager, parent, isEmoji) {
      @Override
      public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {
        delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI : EmojiMediaType.STICKER, true);
        if (areStickersVisible) {
          choosingSuggestionSent = true;
        }
      }

      @Override
      public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {
        delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI : EmojiMediaType.STICKER, true);
        if (areStickersVisible) {
          choosingSuggestionSent = true;
        }
      }

      @Override
      public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) {
        if (!choosingSuggestionSent) {
          delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI : EmojiMediaType.STICKER, false);
        }
      }
    };
    this.stickerSuggestionAdapter.setCallback(parent);
    this.stickerSuggestionsView.setAdapter(stickerSuggestionAdapter);

    parent.addThemeSpecialFilterListener(stickerSuggestionArrowView, ColorId.overlayFilling);


    int stickersListTopHeight = Screen.dp(isEmoji ? 36 : 72) + Screen.dp(2.5f);
    int stickersListTotalHeight = stickersListTopHeight + Screen.dp(6.5f);
    int stickerArrowHeight = Screen.dp(12f);

    RelativeLayout.LayoutParams params;
    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, stickersListTotalHeight + stickerArrowHeight);
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
    params.bottomMargin = -(Screen.dp(8f) + stickerArrowHeight);
    setLayoutParams(params);

    stickerSuggestionsView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, stickersListTotalHeight));

    FrameLayoutFix.LayoutParams fparams;
    fparams = FrameLayoutFix.newParams(Screen.dp(27f), stickerArrowHeight);
    fparams.topMargin = stickersListTopHeight;
    // fparams.leftMargin = Screen.dp(55f) + Screen.dp(2.5f);

    // setPivotX(fparams.leftMargin + Screen.dp(27f) / 2f);
    setPivotY(stickersListTopHeight + stickerArrowHeight);

    stickerSuggestionArrowView.setLayoutParams(fparams);
  }

  public boolean hasStickers () {
    return stickerSuggestionAdapter.hasStickers();
  }

  public void setStickers (@NonNull MessagesController parent, @Nullable ArrayList<TGStickerObj> stickers) {
    stickerSuggestionAdapter.setCallback(parent);
    stickerSuggestionAdapter.setStickers(stickers);
    stickerSuggestionsView.scrollToPosition(0);
  }

  public void addStickers (@NonNull MessagesController parent, @Nullable ArrayList<TGStickerObj> stickers) {
    stickerSuggestionAdapter.setCallback(parent);
    stickerSuggestionAdapter.addStickers(stickers);
  }

  public void setOnScrollListener (RecyclerView.OnScrollListener onScrollListener) {
    stickerSuggestionsView.setOnScrollListener(onScrollListener);
  }

  public void setArrowX (int arrowX) {
    int width = stickerSuggestionsView.getMeasuredWidth();
    int paddingLeft = Math.max(arrowX - Screen.dp(24), Screen.dp(48));
    stickerSuggestionsView.setPadding(paddingLeft, 0, Screen.dp(48), 0);
    stickerSuggestionArrowView.setTranslationX(arrowX - Screen.dp(27) / 2f);
    setPivotX(arrowX);
  }

  public void setChoosingDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  private float stickersFactor;

  private void setStickersFactor (float factor) {
    if (this.stickersFactor != factor) {
      this.stickersFactor = factor;

      final float scale = .8f + .2f * factor;
      setScaleX(scale /* (isEmoji ? 0.5f : 1f)*/);
      setScaleY(scale /* (isEmoji ? 0.5f : 1f)*/);
      setAlpha(Math.min(1f, Math.max(0f, factor)));
    }
  }

  private void animateStickersFactor (float toFactor, boolean onLayout) {
    if (stickersAnimator == null) {
      stickersAnimator = new FactorAnimator(ANIMATOR_STICKERS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, stickersFactor);
    }
    if (toFactor == 1f && stickersFactor == 0f) {
      stickersAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
      stickersAnimator.setDuration(210L);
    } else {
      stickersAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      stickersAnimator.setDuration(100L);
    }
    stickersAnimator.animateTo(toFactor, onLayout ? this : null);
  }

  public void setStickersVisible (boolean areVisible) {
    if (this.areStickersVisible != areVisible) {
      this.areStickersVisible = areVisible;
      if (areVisible) {
        updatePosition(true);
        stickerSuggestionsView.scrollToPosition(0);
      }
      if (choosingSuggestionSent) {
        if (!areVisible) {
          delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI : EmojiMediaType.STICKER, false);
        }
        choosingSuggestionSent = false;
      }
      boolean onLayout = getParent() == null && areVisible;
      if (onLayout) {
        parent.context().addToRoot(this, false);
      }
      animateStickersFactor(areVisible ? 1f : 0f, onLayout);
    }
  }

  public boolean isStickersVisible () {
    return areStickersVisible;
  }

  private void onStickersDisappeared () {
    // stickerSuggestionAdapter.setStickers(null); // todo: clear stickers ??
    parent.context().removeFromRoot(this);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ANIMATOR_STICKERS) {
      setStickersFactor(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_STICKERS) {
      if (finalFactor == 0f) {
        onStickersDisappeared();
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updatePosition(true);
  }

  public void updatePosition (boolean needTranslate) {
    ViewController<?> c = UI.getCurrentStackItem(getContext());
    float tx = 0;
    if (c instanceof MessagesController) {
      int[] cords = ((MessagesController) c).getInputCursorOffset();
      setArrowX(cords[0]);
      setTranslationY(cords[1]);
      tx -= ((MessagesController) c).getPagerScrollOffsetInPixels();
    } else {
      // setTranslationY(0);
    }
    NavigationController navigation = UI.getContext(getContext()).navigation();
    if (needTranslate && navigation != null && navigation.isAnimating()) {
      float translate = navigation.getHorizontalTranslate();
      if (c instanceof MessagesController) {
        tx = translate;
      }
    }
    setTranslationX(tx);
  }

}
