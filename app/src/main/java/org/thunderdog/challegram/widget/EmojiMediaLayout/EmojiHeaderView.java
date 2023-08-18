package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.EmojiLayout;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

@SuppressLint("ViewConstructor")
public class EmojiHeaderView extends FrameLayout {
  public static final int DEFAULT_PADDING = 4;

  public static final int TRENDING_SECTION = -12;

  private final ArrayList<EmojiLayout.EmojiSection> emojiSections;
  private final EmojiLayoutEmojiHeaderAdapter adapter;
  private final RecyclerView recyclerView;
  private final EmojiHeaderSectionView goToMediaPageSection;

  private Paint shadowPaint;

  private int currentEmojiSection;
  private float currentPageFactor;
  private float headerHideFactor;

  public EmojiHeaderView (@NonNull Context context, EmojiLayout emojiLayout, ViewController<?> themeProvider) {
    super(context);
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48)));

    emojiSections = new ArrayList<>(7);
    emojiSections.add(new EmojiLayout.EmojiSection(emojiLayout, TRENDING_SECTION, R.drawable.outline_whatshot_24, R.drawable.baseline_whatshot_24).setMakeFirstTransparent());
    emojiSections.add(new EmojiLayout.EmojiSection(emojiLayout, 0, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24)/*.setFactor(1f, false)*/.setMakeFirstTransparent().setOffsetHalf(false));

    LinearLayoutManager manager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, Lang.rtl());

    recyclerView = new RecyclerView(context);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(manager);
    recyclerView.setPadding(Screen.dp(DEFAULT_PADDING), 0, Screen.dp(DEFAULT_PADDING + 44), 0);
    recyclerView.setClipToPadding(false);
    recyclerView.setAdapter(adapter = new EmojiLayoutEmojiHeaderAdapter(manager, themeProvider, emojiLayout, emojiSections));
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        float range = recyclerView.computeHorizontalScrollRange();
        float offset = recyclerView.computeHorizontalScrollOffset();
        float extent = recyclerView.computeHorizontalScrollExtent();
        float s = range - offset - extent;

        int alpha = (int) (MathUtils.clamp(s / Screen.dp(20f)) * 255);
        shadowPaint.setAlpha(alpha);
        invalidate();
      }
    });
    addView(recyclerView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    goToMediaPageSection = new EmojiHeaderSectionView(context);
    goToMediaPageSection.setSection(new EmojiLayout.EmojiSection(emojiLayout, 7, R.drawable.deproko_baseline_stickers_24, 0).setActiveDisabled());
    goToMediaPageSection.setForceWidth(Screen.dp(48));
    goToMediaPageSection.setId(R.id.btn_section);

    addView(goToMediaPageSection, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));

    updatePaints(Theme.fillingColor());
    adapter.setSelectedIndex(0, true);
  }

  public void setSectionsOnClickListener (OnClickListener onClickListener) {
    this.adapter.setOnClickListener(onClickListener);
    this.goToMediaPageSection.setOnClickListener(onClickListener);
  }

  public void setSectionsOnLongClickListener (OnLongClickListener onLongClickListener) {
    this.adapter.setOnLongClickListener(onLongClickListener);
    this.goToMediaPageSection.setOnLongClickListener(onLongClickListener);
  }

  public void setCurrentEmojiSection (int section) {
    if (this.currentEmojiSection != section && section != -1) {
      adapter.setSelectedIndex(section, true);
      this.currentEmojiSection = section;
    }
  }

  public void setHeaderHideFactor (float headerHideFactor) {
    this.headerHideFactor = headerHideFactor;
  }

  public void setCurrentPageFactor (float currentPageFactor) {
    this.currentPageFactor = currentPageFactor;
  }

  public void addStickerSet (int index, TGStickerSetInfo info) {
    adapter.addStickerSet(index, info);
  }

  public void removeStickerSet (int index) {
    adapter.removeStickerSet(index);
  }

  public void moveStickerSet (int fromIndex, int toIndex) {
    adapter.moveStickerSet(fromIndex, toIndex);
  }

  public void setStickerSets (ArrayList<TGStickerSetInfo> stickers) {
    adapter.setStickerSets(stickers);
  }

  public void setMediaSection (boolean isGif) {
    goToMediaPageSection.getSection().changeIcon(isGif ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_stickers_24);
  }

  private void updatePaints (int color) {
    LinearGradient shader = new LinearGradient(0, 0, Screen.dp(48), 0, 0, color, Shader.TileMode.CLAMP);
    if (shadowPaint == null) {
      shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint.setShader(shader);
    invalidate();
  }

  @Override
  protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
    if (child == goToMediaPageSection) {
      canvas.save();
      canvas.translate(getMeasuredWidth() - Screen.dp(96), 0);
      canvas.drawRect(0, 0, Screen.dp(96), getMeasuredHeight(), shadowPaint);
      canvas.restore();
    }
    return super.drawChild(canvas, child, drawingTime);
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_SECTION = 0;
    public static final int TYPE_STICKER_SET = 1;
    public static final int TYPE_SECTIONS_EXPANDABLE = 2;

    public ViewHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static ViewHolder create (Context context, int viewType, ViewController<?> themeProvider, EmojiLayout emojiLayout, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
      if (viewType == TYPE_SECTION) {
        EmojiHeaderSectionView sectionView = new EmojiHeaderSectionView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(sectionView);
        }
        sectionView.setId(R.id.btn_section);
        sectionView.setOnClickListener(onClickListener);
        sectionView.setOnLongClickListener(onLongClickListener);
        sectionView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sectionView.setAdditionParentPadding(Screen.dp(44));
        return new ViewHolder(sectionView);
      } else if (viewType == TYPE_STICKER_SET) {
        EmojiLayout.StickerSectionView sectionView = new EmojiLayout.StickerSectionView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(sectionView);
        }
        sectionView.setOnLongClickListener(onLongClickListener);
        sectionView.setId(R.id.btn_stickerSet);
        sectionView.setOnClickListener(onClickListener);
        sectionView.setItemCount(8);
        sectionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new ViewHolder(sectionView);
      } else if (viewType == TYPE_SECTIONS_EXPANDABLE) {
        EmojiHeaderCollapsibleSectionView v = new EmojiHeaderCollapsibleSectionView(context);
        v.init(new EmojiLayout.EmojiSection[]{
          new EmojiLayout.EmojiSection(emojiLayout, 1, R.drawable.baseline_emoticon_outline_24, R.drawable.baseline_emoticon_24).setMakeFirstTransparent(),
          new EmojiLayout.EmojiSection(emojiLayout, 2, R.drawable.deproko_baseline_animals_outline_24, R.drawable.deproko_baseline_animals_24)/*.setIsPanda(!useDarkMode)*/,
          new EmojiLayout.EmojiSection(emojiLayout, 3, R.drawable.baseline_restaurant_menu_24, R.drawable.baseline_restaurant_menu_24),
          new EmojiLayout.EmojiSection(emojiLayout, 4, R.drawable.baseline_directions_car_24, R.drawable.baseline_directions_car_24),
          new EmojiLayout.EmojiSection(emojiLayout, 5, R.drawable.deproko_baseline_lamp_24, R.drawable.deproko_baseline_lamp_filled_24),
          new EmojiLayout.EmojiSection(emojiLayout, 6, R.drawable.deproko_baseline_flag_outline_24, R.drawable.deproko_baseline_flag_filled_24).setMakeFirstTransparent()
        });
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.setOnButtonClickListener(onClickListener);
        v.setThemeInvalidateListener(themeProvider);
        return new ViewHolder(v);
      }

      return new ViewHolder(new View(context));
    }
  }

  public static class EmojiLayoutEmojiHeaderAdapter extends RecyclerView.Adapter<ViewHolder> {
    private final ArrayList<EmojiLayout.EmojiSection> emojiSections;
    private final ViewController<?> themeProvider;
    private final ArrayList<TGStickerSetInfo> stickerSets;
    private final LinearLayoutManager manager;
    private final EmojiLayout emojiLayout;
    private final int expandableItemSize = 6;
    private final int expandableItemPosition = 2;

    private View.OnClickListener onClickListener;
    private View.OnLongClickListener onLongClickListener;
    private int selectedIndex = -1;

    public EmojiLayoutEmojiHeaderAdapter (LinearLayoutManager manager, ViewController<?> themeProvider, EmojiLayout emojiLayout, ArrayList<EmojiLayout.EmojiSection> emojiSections) {
      this.themeProvider = themeProvider;
      this.emojiSections = emojiSections;
      this.stickerSets = new ArrayList<>();
      this.emojiLayout = emojiLayout;
      this.manager = manager;
    }

    public void setOnClickListener (OnClickListener onClickListener) {
      this.onClickListener = onClickListener;
    }

    public void setOnLongClickListener (OnLongClickListener onLongClickListener) {
      this.onLongClickListener = onLongClickListener;
    }

    public void addStickerSet (int index, TGStickerSetInfo info) {
      // todo: move selectedIndex
      stickerSets.add(index, info);
      notifyItemInserted(index + emojiSections.size());
    }

    public void removeStickerSet (int index) {
      // todo: move selectedIndex
      if (index >= 0 && index < stickerSets.size()) {
        stickerSets.remove(index);
        notifyItemRemoved(index + emojiSections.size());
      }
    }

    public void moveStickerSet (int fromIndex, int toIndex) {
      // todo: move selectedIndex
      TGStickerSetInfo info = stickerSets.remove(fromIndex);
      stickerSets.add(toIndex, info);
      fromIndex += emojiSections.size();
      toIndex += emojiSections.size();
      notifyItemMoved(fromIndex, toIndex);
    }

    public void setStickerSets (ArrayList<TGStickerSetInfo> stickers) {
      if (!stickerSets.isEmpty()) {
        int removedCount = stickerSets.size();
        stickerSets.clear();
        notifyItemRangeRemoved(emojiSections.size(), removedCount);
      }
      if (stickers != null && !stickers.isEmpty()) {
        int addedCount;
        if (!stickers.get(0).isSystem()) {
          stickerSets.addAll(stickers);
          addedCount = stickers.size();
        } else {
          addedCount = 0;
          for (int i = 0; i < stickers.size(); i++) {
            TGStickerSetInfo stickerSet = stickers.get(i);
            if (stickerSet.isSystem()) {
              continue;
            }
            stickerSets.add(stickerSet);
            addedCount++;
          }
        }
        notifyItemRangeInserted(emojiSections.size(), addedCount);
      }
    }

    public void setSelectedIndex (int index, boolean animated) {
      Log.i("WTF_DEBUG", "offset" + index);

      //if (newSelectedViewType == ViewHolder.TYPE_SECTION) {
        index += 1;
     // }

      final int oldIndex = selectedIndex;
      final int oldSelectedPosition = getSelectedPosition(oldIndex);
      final int oldSelectedViewType = getItemViewType(oldSelectedPosition);
      final int newSelectedPosition = getSelectedPosition(index);
      final int newSelectedViewType = getItemViewType(newSelectedPosition);

      this.selectedIndex = index;

      if (newSelectedViewType == ViewHolder.TYPE_SECTIONS_EXPANDABLE) {
        View view = manager.findViewByPosition(expandableItemPosition);
        if (view instanceof EmojiHeaderCollapsibleSectionView) {
          ((EmojiHeaderCollapsibleSectionView) view).setSelectedIndex(index - expandableItemPosition, animated);
        }
      }

      if (oldSelectedPosition == newSelectedPosition) {
        return;
      }

      if (newSelectedViewType == ViewHolder.TYPE_STICKER_SET) {
        View view = manager.findViewByPosition(newSelectedPosition);
        if (view instanceof EmojiLayout.StickerSectionView) {
          ((EmojiLayout.StickerSectionView) view).setSelectionFactor(1f, animated);
        }
      } else if (newSelectedViewType == ViewHolder.TYPE_SECTION) {
        View view = manager.findViewByPosition(newSelectedPosition);
        if (view instanceof EmojiHeaderSectionView) {
          ((EmojiHeaderSectionView) view).getSection().setFactor(1f, animated);
        }
      }

      if (oldSelectedViewType == ViewHolder.TYPE_STICKER_SET) {
        View view = manager.findViewByPosition(oldSelectedPosition);
        if (view instanceof EmojiLayout.StickerSectionView) {
          ((EmojiLayout.StickerSectionView) view).setSelectionFactor(0f, animated);
        }
      } else if (oldSelectedViewType == ViewHolder.TYPE_SECTION) {
        View view = manager.findViewByPosition(oldSelectedPosition);
        if (view instanceof EmojiHeaderSectionView) {
          ((EmojiHeaderSectionView) view).getSection().setFactor(0f, animated);
        }
      } else if (oldSelectedViewType == ViewHolder.TYPE_SECTIONS_EXPANDABLE) {
        View view = manager.findViewByPosition(oldSelectedPosition);
        if (view instanceof EmojiHeaderCollapsibleSectionView) {
          ((EmojiHeaderCollapsibleSectionView) view).setSelectedIndex(-1, animated);
        }
      }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return ViewHolder.create(parent.getContext(), viewType, themeProvider, emojiLayout, onClickListener, onLongClickListener);
    }

    @Override
    public void onBindViewHolder (@NonNull ViewHolder holder, int position) {
      final int viewType = getItemViewType(position);
      final boolean isSelected = position == getSelectedPosition(selectedIndex);

      if (position > expandableItemPosition && expandableItemSize > 0) {
        position -= 1;
      }

      if (viewType == ViewHolder.TYPE_SECTION) {
        ((EmojiHeaderSectionView) holder.itemView).setSection(emojiSections.get(position));
      } else if (viewType == ViewHolder.TYPE_STICKER_SET) {
        TGStickerSetInfo info = stickerSets.get(position - emojiSections.size());
        ((EmojiLayout.StickerSectionView) holder.itemView).setSelectionFactor(isSelected ? 1f : 0f, false);
        ((EmojiLayout.StickerSectionView) holder.itemView).setStickerSet(info);
      } else if (viewType == ViewHolder.TYPE_SECTIONS_EXPANDABLE) {

        ((EmojiHeaderCollapsibleSectionView) holder.itemView).setSelectedIndex(isSelected ? (selectedIndex - expandableItemPosition): -1, false);
      }
    }

    private int getSelectedPosition (int index) {
      if (expandableItemSize > 0 && index >= expandableItemPosition) {
        if (index < expandableItemPosition + expandableItemSize) {
          return expandableItemPosition;
        } else {
          return index - expandableItemSize + 1;
        }
      }
      return index;
    }

    @Override
    public int getItemViewType (int position) {
      if (position == expandableItemPosition) {
        return ViewHolder.TYPE_SECTIONS_EXPANDABLE;
      } else if (position > expandableItemPosition && expandableItemSize > 0) {
        position -= 1;
      }

      if (position < emojiSections.size()) {
        return ViewHolder.TYPE_SECTION;
      } else {
        return ViewHolder.TYPE_STICKER_SET;
      }
    }

    @Override
    public int getItemCount () {
      return emojiSections.size() + stickerSets.size() + (expandableItemSize > 0 ? 1: 0) ;
    }

    @Override
    public void onViewAttachedToWindow (ViewHolder holder) {
      if (holder.getItemViewType() == ViewHolder.TYPE_STICKER_SET) {
        ((EmojiLayout.StickerSectionView) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (ViewHolder holder) {
      if (holder.getItemViewType() == ViewHolder.TYPE_STICKER_SET) {
        ((EmojiLayout.StickerSectionView) holder.itemView).detach();
      }
    }

    @Override
    public void onViewRecycled (ViewHolder holder) {
      if (holder.getItemViewType() == ViewHolder.TYPE_STICKER_SET) {
        ((EmojiLayout.StickerSectionView) holder.itemView).performDestroy();
      }
    }
  }
}
