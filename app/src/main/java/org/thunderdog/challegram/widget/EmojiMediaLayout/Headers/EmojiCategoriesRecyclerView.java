package org.thunderdog.challegram.widget.EmojiMediaLayout.Headers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.RunnableData;

public class EmojiCategoriesRecyclerView extends CustomRecyclerView {
  private final GradientDrawable gradientDrawableLeft = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{ 0, Theme.fillingColor() });
  private final GradientDrawable gradientDrawableRight = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ 0, Theme.fillingColor() });
  private final LinearLayoutManager layoutManager;

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
          outRect.set(parent.getMeasuredWidth() - Screen.dp(148), 0, 0, 0);
        }
      }
    });

    setPadding(0, 0, Screen.dp(7), 0);
    setClipToPadding(false);
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    int sL = getFirstItemX();
    int sR = computeHorizontalScrollRange() - computeHorizontalScrollOffset() - computeHorizontalScrollExtent();

    int alphaL = (int) ((1f - (MathUtils.clamp((float) sL / Screen.dp(30f)))) * 255);
    int alphaR = (int) (MathUtils.clamp((float) sR / Screen.dp(30f)) * 255);

    canvas.drawRect(sL, 0, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));

    super.dispatchDraw(canvas);

    checkGradients();

    gradientDrawableLeft.setAlpha(alphaL);
    gradientDrawableLeft.setBounds(0, 0, Screen.dp(30), getMeasuredHeight());
    gradientDrawableLeft.draw(canvas);

    gradientDrawableRight.setAlpha(255);
    gradientDrawableRight.setBounds(sL - Screen.dp(30), 0, sL, getMeasuredHeight());
    gradientDrawableRight.draw(canvas);

    gradientDrawableRight.setAlpha(alphaR);
    gradientDrawableRight.setBounds(getMeasuredWidth() - Screen.dp(30), 0, getMeasuredWidth(), getMeasuredHeight());
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

    public static EmojiSearchTypesViewHolder create (Context context, View.OnClickListener onClickListener) {
      ImageView imageView = new ImageView(context);
      imageView.setScaleType(ImageView.ScaleType.CENTER);
      imageView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(38), ViewGroup.LayoutParams.MATCH_PARENT));
      imageView.setOnClickListener(onClickListener);
      return new EmojiSearchTypesViewHolder(imageView);
    }
  }

  public static class EmojiSearchTypesAdapter extends RecyclerView.Adapter<EmojiSearchTypesViewHolder> implements View.OnClickListener {
    private static final int[] icons = new int[] {
      R.drawable.baseline_favorite_24, R.drawable.baseline_emoji_thumb_up, R.drawable.baseline_emoji_thumb_down, R.drawable.baseline_emoji_party_popper,
      R.drawable.baseline_emoji_mdi_emoticon_excited, R.drawable.baseline_emoji_mdi_emoticon_sad, R.drawable.baseline_emoji_mdi_emoticon_frown,
      R.drawable.baseline_emoji_emoticon_neutral, R.drawable.baseline_emoji_mdi_emoticon_angry, R.drawable.baseline_emoji_emoticon_tongue
    };
    private static final String[] strings = new String[] {
      "love", "like", "dislike", "party", "excited",
      "sad", "frown", "neutral", "angry", "tongue"
    };

    private final ViewController<?> context;
    private final RunnableData<String> onSectionClickListener;
    private int activeIndex = -1;

    public EmojiSearchTypesAdapter (ViewController<?> context, RunnableData<String> onSectionClickListener) {
      this.context = context;
      this.onSectionClickListener = onSectionClickListener;
    }

    @NonNull
    @Override
    public EmojiSearchTypesViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return EmojiSearchTypesViewHolder.create(context.context(), this);
    }

    @Override
    public void onBindViewHolder (@NonNull EmojiSearchTypesViewHolder holder, int position) {
      ImageView view = (ImageView) holder.itemView;
      view.setImageResource(icons[position]);
      view.setColorFilter(position == activeIndex ? Theme.getColor(ColorId.iconActive) : Theme.iconColor());
      view.setTag(position);
      // parent.addThemeFilterListener(imageView, ColorId.icon);
    }

    @Override
    public int getItemCount () {
      return 10;
    }

    public void setActiveIndex (int activeIndex) {
      final int oldActiveIndex = this.activeIndex;
      if (oldActiveIndex == activeIndex) {
        return;
      }

      this.activeIndex = activeIndex;
      if (activeIndex != -1) {
        notifyItemChanged(activeIndex);
      }
      if (oldActiveIndex != -1) {
        notifyItemChanged(oldActiveIndex);
      }
    }

    @Override
    public void onClick (View v) {
      int index = (int) v.getTag();
      setActiveIndex(index);
      onSectionClickListener.runWithData(strings[index]);
    }



    public void setEmojiCategories (TdApi.EmojiCategory[] categories) {

    }
  }

}
