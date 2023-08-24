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
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
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
  public final RecyclerView stickerSuggestionsView;
  public StickerSuggestionAdapter stickerSuggestionAdapter;

  private MessagesController parent;
  private FactorAnimator stickersAnimator;
  private Delegate delegate;
  private ViewGroup contentView;
  private boolean choosingSuggestionSent;
  private boolean areStickersVisible;
  private boolean needAddToRoot;
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

  public void init (@NonNull MessagesController parent, ViewGroup contentView, @Nullable ArrayList<TGStickerObj> stickers, boolean isEmoji, boolean needAddToRoot) {
    this.contentView = contentView;
    this.parent = parent;
    this.isEmoji = isEmoji;
    this.needAddToRoot = needAddToRoot;
    this.stickerSuggestionAdapter = new StickerSuggestionAdapter(parent, parent, manager, parent, isEmoji) {
      @Override
      public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {
        delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI: EmojiMediaType.STICKER, true);
        if (areStickersVisible) {
          choosingSuggestionSent = true;
        }
      }

      @Override
      public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {
        delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI: EmojiMediaType.STICKER, true);
        if (areStickersVisible) {
          choosingSuggestionSent = true;
        }
      }

      @Override
      public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) {
        if (!choosingSuggestionSent) {
          delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI: EmojiMediaType.STICKER, false);
        }
      }
    };
    this.stickerSuggestionAdapter.setStickers(stickers);
    this.stickerSuggestionsView.setAdapter(stickerSuggestionAdapter);

    parent.addThemeSpecialFilterListener(stickerSuggestionArrowView, ColorId.overlayFilling);


    int stickersListTopHeight = Screen.dp(isEmoji ? 36: 72) + Screen.dp(2.5f);
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
      setScaleX(scale /* (isEmoji ? 0.5f: 1f)*/);
      setScaleY(scale /* (isEmoji ? 0.5f: 1f)*/);
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
      if (choosingSuggestionSent) {
        if (!areVisible) {
          delegate.notifyChoosingEmoji(isEmoji ? EmojiMediaType.EMOJI: EmojiMediaType.STICKER, false);
        }
        choosingSuggestionSent = false;
      }
      boolean onLayout = getParent() == null && areVisible;
      if (onLayout) {
        if (needAddToRoot) {
          parent.context().addToRoot(this, false);
        } else {
          contentView.addView(this);
        }
      }
      animateStickersFactor(areVisible ? 1f : 0f, onLayout);
    }
  }

  public boolean isStickersVisible () {
    return areStickersVisible;
  }

  private void onStickersDisappeared () {
    // stickerSuggestionAdapter.setStickers(null); // todo: clear stickers ??
    if (needAddToRoot) {
      parent.context().removeFromRoot(this);
    } else {
      contentView.removeView(this);
    }
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
}
