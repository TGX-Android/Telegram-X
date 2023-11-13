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
 * File created on 25/09/2023
 */
package org.thunderdog.challegram.widget.emoji.header;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.Arrays;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;

public class EmojiCategoriesRecyclerView extends CustomRecyclerView implements Destroyable {
  private static final int CATEGORY_WIDTH = 38;
  private static final int SHADOW_SIZE = 30;

  private final GradientDrawable gradientDrawableLeft = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{ 0, Theme.fillingColor() });
  private final GradientDrawable gradientDrawableRight = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ 0, Theme.fillingColor() });
  private final ComplexReceiver receiverForPriorityLoading = new ComplexReceiver();
  private final LinearLayoutManager layoutManager;
  private EmojiSearchTypesAdapter emojiSearchTypesAdapter;
  private int minimalLeftPadding;

  public EmojiCategoriesRecyclerView (Context context) {
    super(context);
    layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
    setLayoutManager(layoutManager);

    addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == 0) {
          float availWidth = parent.getMeasuredWidth() - minimalLeftPadding - Screen.dp(SHADOW_SIZE);
          float itemsCount = (float) (Math.floor((availWidth - Screen.dp(CATEGORY_WIDTH * 0.65f)) / Screen.dp(CATEGORY_WIDTH)) + 0.65f);
          int itemsWidth = Screen.dp(CATEGORY_WIDTH * itemsCount);
          
          outRect.set(parent.getMeasuredWidth() - itemsWidth, 0, 0, 0);
        }
      }
    });

    setPadding(0, 0, Screen.dp(7), 0);
    setClipToPadding(false);
  }

  public void init (ViewController<?> controller, RunnableData<String> onSelectCategoryListener) {
    emojiSearchTypesAdapter = new EmojiSearchTypesAdapter(controller, layoutManager);
    emojiSearchTypesAdapter.setOnSectionClickListener(onSelectCategoryListener);
    setAdapter(emojiSearchTypesAdapter);

    controller.tdlib().send(new TdApi.GetEmojiCategories(new TdApi.EmojiCategoryTypeDefault()), (emojiCategories, error) -> {
      if (emojiCategories != null) {
        UI.post(() -> emojiSearchTypesAdapter.requestEmojiCategories(emojiCategories.categories, receiverForPriorityLoading));
      }
    });
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    receiverForPriorityLoading.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    receiverForPriorityLoading.detach();
  }

  @Override
  public void performDestroy () {
    receiverForPriorityLoading.performDestroy();
  }

  public void setMinimalLeftPadding (int minimalLeftPadding) {
    this.minimalLeftPadding = minimalLeftPadding;
    invalidateItemDecorations();
  }

  public void reset () {
    emojiSearchTypesAdapter.setActiveIndex(-1);
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    int sL = getFirstItemX();
    int sR = computeHorizontalScrollRange() - computeHorizontalScrollOffset() - computeHorizontalScrollExtent();

    int alphaL = (int) ((1f - (MathUtils.clamp((float) sL / Screen.dp(SHADOW_SIZE)))) * 255);
    int alphaR = (int) (MathUtils.clamp((float) sR / Screen.dp(SHADOW_SIZE)) * 255);

    canvas.drawRect(sL, 0, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));

    super.dispatchDraw(canvas);

    checkGradients();

    gradientDrawableLeft.setAlpha(alphaL);
    gradientDrawableLeft.setBounds(0, 0, Screen.dp(SHADOW_SIZE), getMeasuredHeight());
    gradientDrawableLeft.draw(canvas);

    gradientDrawableRight.setAlpha(255);
    gradientDrawableRight.setBounds(sL - Screen.dp(SHADOW_SIZE), 0, sL, getMeasuredHeight());
    gradientDrawableRight.draw(canvas);

    gradientDrawableRight.setAlpha(alphaR);
    gradientDrawableRight.setBounds(getMeasuredWidth() - Screen.dp(SHADOW_SIZE), 0, getMeasuredWidth(), getMeasuredHeight());
    gradientDrawableRight.draw(canvas);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    int sL = getFirstItemX();
    float x = e.getX();
    return x > sL && super.onTouchEvent(e);
  }

  @Override
  public void onScrolled (int dx, int dy) {
    super.onScrolled(dx, dy);
    invalidate();
  }

  private int getFirstItemX () {
    View view = layoutManager.findViewByPosition(0);
    if (view == null) {
      return 0;
    }
    return view.getLeft();
  }

  private int lastColor;
  private void checkGradients () {
    int color = Theme.backgroundColor();
    if (color != lastColor) {
      gradientDrawableRight.setColors(new int[]{ 0, lastColor = Theme.fillingColor() });
    }
  }



  /*  */

  private static class EmojiSearchTypesViewHolder extends RecyclerView.ViewHolder {
    public EmojiSearchTypesViewHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static EmojiSearchTypesViewHolder create (ViewController<?> context, View.OnClickListener onClickListener) {
      StickerSmallView stickerSmallView = new StickerSmallView(context.context());
      stickerSmallView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(CATEGORY_WIDTH), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER, 0, Screen.dp(9), 0, Screen.dp(9)));
      stickerSmallView.setOnClickListener(onClickListener);
      stickerSmallView.setPadding(Screen.dp(5.5f));
      stickerSmallView.init(context.tdlib());
      stickerSmallView.setStickerMovementCallback(new StickerSmallView.StickerMovementCallback() {
        @Override
        public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
          onClickListener.onClick(view);
          return true;
        }

        @Override
        public long getStickerOutputChatId () {
          return 0;
        }

        @Override
        public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {

        }

        @Override
        public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
          return false;
        }

        @Override
        public boolean onStickerLongClick (StickerSmallView view, TGStickerObj sticker) {
          return true;
        }

        @Override
        public boolean needsLongDelay (StickerSmallView view) {
          return false;
        }

        @Override
        public int getStickersListTop () {
          return 0;
        }

        @Override
        public int getViewportHeight () {
          return 0;
        }
      });
      context.addThemeInvalidateListener(stickerSmallView);

      return new EmojiSearchTypesViewHolder(stickerSmallView);
    }
  }

  public static class EmojiSearchTypesAdapter extends RecyclerView.Adapter<EmojiSearchTypesViewHolder> implements View.OnClickListener {
    private final ViewController<?> context;
    private final RecyclerView.LayoutManager manager;
    private RunnableData<String> onSectionClickListener;
    private int activeIndex = -1;

    public EmojiSearchTypesAdapter (ViewController<?> context, RecyclerView.LayoutManager manager) {
      this.context = context;
      this.manager = manager;
    }

    public void setOnSectionClickListener (RunnableData<String> onSectionClickListener) {
      this.onSectionClickListener = onSectionClickListener;
    }

    @NonNull
    @Override
    public EmojiSearchTypesViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return EmojiSearchTypesViewHolder.create(context, this);
    }

    @Override
    public void onBindViewHolder (@NonNull EmojiSearchTypesViewHolder holder, int position) {
      StickerSmallView view = (StickerSmallView) holder.itemView;
      view.setSticker(categoryStickers[position]);
      view.setThemedColorId(position == activeIndex ? ColorId.iconActive : ColorId.icon);
      view.setTag(position);
      view.invalidate();
    }

    @Override
    public void onViewAttachedToWindow (EmojiSearchTypesViewHolder holder) {
      ((StickerSmallView) holder.itemView).attach();
    }

    @Override
    public void onViewDetachedFromWindow (EmojiSearchTypesViewHolder holder) {
      ((StickerSmallView) holder.itemView).detach();
    }

    @Override
    public void onViewRecycled (EmojiSearchTypesViewHolder holder) {
      ((StickerSmallView) holder.itemView).performDestroy();
    }

    @Override
    public int getItemCount () {
      return categories != null ? categories.length : 0;
    }

    public void setActiveIndex (int activeIndex) {
      final int oldActiveIndex = this.activeIndex;
      if (oldActiveIndex == activeIndex) {
        return;
      }

      this.activeIndex = activeIndex;
      if (activeIndex != -1) {
        View view = manager.findViewByPosition(activeIndex);
        if (view instanceof StickerSmallView) {
          ((StickerSmallView) view).setThemedColorId(ColorId.iconActive);
          view.invalidate();
        } else {
          notifyItemChanged(activeIndex);
        }
      }
      if (oldActiveIndex != -1) {
        View view = manager.findViewByPosition(oldActiveIndex);
        if (view instanceof StickerSmallView) {
          ((StickerSmallView) view).setThemedColorId(ColorId.icon);
          view.invalidate();
        } else {
          notifyItemChanged(oldActiveIndex);
        }
      }
    }

    @Override
    public void onClick (View v) {
      int index = (int) v.getTag();
      if (index != activeIndex) {
        setActiveIndex(index);
        onSectionClickListener.runWithData(StringUtils.join(" ", " ", Arrays.asList(categories[index].emojis)));
      } else {
        setActiveIndex(-1);
        onSectionClickListener.runWithData(null);
      }
    }

    private TdApi.EmojiCategory[] categories;
    private TGStickerObj[] categoryStickers;

    public void requestEmojiCategories (TdApi.EmojiCategory[] categories, ComplexReceiver receiverForPriorityLoading) {
      final int itemCount = getItemCount();
      if (itemCount > 0) {
        notifyItemRangeRemoved(0, itemCount);
      }
      this.categories = categories;
      this.categoryStickers = new TGStickerObj[categories.length];
      for (int a = 0; a < categories.length; a++) {
        TdApi.EmojiCategory category = categories[a];
        categoryStickers[a] = new TGStickerObj(context.tdlib(), category.icon, category.icon.fullType, category.emojis);
        if (categoryStickers[a].getPreviewAnimation() != null) {
          categoryStickers[a].getPreviewAnimation().setHighPriorityForDecode();
          categoryStickers[a].getPreviewAnimation().setPlayOnce(true);
          categoryStickers[a].getPreviewAnimation().setLooped(false);
          receiverForPriorityLoading.getGifReceiver(a).requestFile(categoryStickers[a].getPreviewAnimation());
        }
      }
      notifyItemRangeInserted(0, categories.length);
    }
  }
}
