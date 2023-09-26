package org.thunderdog.challegram.widget.EmojiMediaLayout.Headers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
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
import me.vkryl.core.lambda.RunnableData;

public class EmojiCategoriesRecyclerView extends CustomRecyclerView {
  private static final int CATEGORY_WIDTH = 38;
  private static final int SHADOW_SIZE = 30;

  private final GradientDrawable gradientDrawableLeft = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{ 0, Theme.fillingColor() });
  private final GradientDrawable gradientDrawableRight = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ 0, Theme.fillingColor() });
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
          float itemsCount = (float) (Math.floor((availWidth - Screen.dp(CATEGORY_WIDTH / 2f)) / Screen.dp(CATEGORY_WIDTH)) + 0.5f);
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

    controller.tdlib().send(new TdApi.GetEmojiCategories(new TdApi.EmojiCategoryTypeDefault()), object -> {
      if (object.getConstructor() == TdApi.EmojiCategories.CONSTRUCTOR) {
        TdApi.EmojiCategories categories = (TdApi.EmojiCategories) object;
        UI.post(() -> emojiSearchTypesAdapter.setEmojiCategories(categories.categories));
      }
    });
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
      view.setRepaintingColorId(position == activeIndex ? ColorId.iconActive : ColorId.icon);
      view.setTag(position);
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
          ((StickerSmallView) view).setRepaintingColorId(ColorId.iconActive);
          view.invalidate();
        } else {
          notifyItemChanged(activeIndex);
        }
      }
      if (oldActiveIndex != -1) {
        View view = manager.findViewByPosition(oldActiveIndex);
        if (view instanceof StickerSmallView) {
          ((StickerSmallView) view).setRepaintingColorId(ColorId.icon);
          view.invalidate();
        } else {
          notifyItemChanged(oldActiveIndex);
        }
      }
    }

    @Override
    public void onClick (View v) {
      int index = (int) v.getTag();
      setActiveIndex(index);
      onSectionClickListener.runWithData(StringUtils.join(" ", " ", Arrays.asList(categories[index].emojis)));
    }

    private TdApi.EmojiCategory[] categories;
    private TGStickerObj[] categoryStickers;

    public void setEmojiCategories (TdApi.EmojiCategory[] categories) {
      notifyItemRangeRemoved(0, getItemCount());
      this.categories = categories;
      this.categoryStickers = new TGStickerObj[categories.length];
      for (int a = 0; a < categories.length; a++) {
        TdApi.EmojiCategory category = categories[a];
        categoryStickers[a] = new TGStickerObj(context.tdlib(), category.icon, category.icon.fullType, category.emojis);
        if (categoryStickers[a].getPreviewAnimation() != null) {
          categoryStickers[a].getPreviewAnimation().setPlayOnce(true);
        }
      }
      notifyItemRangeInserted(0, categories.length);
    }
  }
}
