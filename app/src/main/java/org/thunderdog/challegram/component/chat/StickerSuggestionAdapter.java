package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Date: 01/12/2016
 * Author: default
 */

public class StickerSuggestionAdapter extends RecyclerView.Adapter<StickerSuggestionAdapter.StickerSuggestionHolder> implements StickerSmallView.StickerMovementCallback {
  public interface Callback {
    boolean onSendStickerSuggestion (View view, TGStickerObj sticker, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState);
    int getStickerSuggestionsTop ();
    int getStickerSuggestionPreviewViewportHeight ();
    long getStickerSuggestionsChatId ();
  }

  private final RecyclerView.LayoutManager manager;
  private final ViewController context;
  private final Callback callback;
  private @Nullable ArrayList<TGStickerObj> stickers;
  private @Nullable ViewController themeProvider;

  public StickerSuggestionAdapter (ViewController context, Callback callback, RecyclerView.LayoutManager manager, @Nullable ViewController themeProvider) {
    this.context = context;
    this.callback = callback;
    this.manager = manager;
    this.themeProvider = themeProvider;
  }

  public boolean hasStickers () {
    return stickers != null && !stickers.isEmpty();
  }

  public void addStickers (@NonNull ArrayList<TGStickerObj> stickers) {
    if (this.stickers == null || this.stickers.isEmpty()) {
      throw new IllegalStateException();
    }
    int size = this.stickers.size();
    this.stickers.addAll(stickers);
    notifyItemRangeInserted(size + 1, stickers.size());
  }

  public void setStickers (@Nullable ArrayList<TGStickerObj> stickers) {
    int oldItemCount = getItemCount();
    this.stickers = stickers;
    int newItemCount = getItemCount();
    if (newItemCount == 0 || oldItemCount == 0) {
      U.notifyItemsReplaced(this, oldItemCount);
    } else {
      int oldStickerCount = oldItemCount - 2;
      int newStickerCount = newItemCount - 2;
      if (oldStickerCount == newStickerCount) {
        notifyItemRangeChanged(1, newStickerCount);
      } else if (oldStickerCount < newItemCount) {
        notifyItemRangeChanged(1, oldStickerCount);
        notifyItemRangeInserted(oldItemCount - 1, newStickerCount - oldStickerCount);
      } else {
        notifyItemRangeChanged(1, newStickerCount);
        notifyItemRangeRemoved(newItemCount - 1, oldStickerCount - newStickerCount);
      }
    }
  }

  // Adapter stuff

  @Override
  public StickerSuggestionHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return StickerSuggestionHolder.create(context.context(), context.tdlib(), viewType, this, themeProvider);
  }

  @Override
  public void onBindViewHolder (StickerSuggestionHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case StickerSuggestionHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).setSticker(stickers != null ? stickers.get(position - 1) : null);
        break;
      }
    }
  }

  @Override
  public void onViewAttachedToWindow (StickerSuggestionHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerSuggestionHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (StickerSuggestionHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerSuggestionHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).detach();
        break;
      }
    }
  }

  @Override
  public int getItemViewType (int position) {
    return position-- == 0 || stickers == null ? StickerSuggestionHolder.TYPE_START : position < stickers.size() ? StickerSuggestionHolder.TYPE_STICKER : StickerSuggestionHolder.TYPE_END;
  }

  @Override
  public int getItemCount () {
    return stickers != null && !stickers.isEmpty() ? stickers.size() + 2 : 0;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState) {
    return callback.onSendStickerSuggestion(clickView, sticker, forceDisableNotification, schedulingState);
  }

  @Override
  public long getStickerOutputChatId () {
    return callback.getStickerSuggestionsChatId();
  }

  @Override
  public int getViewportHeight () {
    return callback.getStickerSuggestionPreviewViewportHeight();
  }

  // Sticker movement

  private int indexOfSticker (TGStickerObj sticker) {
    if (this.stickers != null && !this.stickers.isEmpty()) {
      int i = 0;
      for (TGStickerObj checkSticker : stickers) {
        if (checkSticker.equals(sticker)) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    int i = indexOfSticker(sticker);
    if (i != -1) {
      final View childView = manager != null ? manager.findViewByPosition(i + 1) : null;
      if (childView != null && childView instanceof StickerSmallView) {
        ((StickerSmallView) childView).setStickerPressed(isPressed);
      } else {
        notifyItemChanged(i + 1);
      }
    }
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return true;
  }

  @Override
  public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {

  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return true;
  }

  @Override
  public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) {

  }

  @Override
  public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {

  }

  @Override
  public int getStickersListTop () {
    return callback.getStickerSuggestionsTop();
  }

  // Holder

  public static class StickerSuggestionHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_START = 0;
    public static final int TYPE_END = 1;
    public static final int TYPE_STICKER = 2;

    public StickerSuggestionHolder (View itemView) {
      super(itemView);
    }

    public static StickerSuggestionHolder create (Context context, Tdlib tdlib, int viewType, StickerSmallView.StickerMovementCallback callback, @Nullable ViewController themeProvider) {
      switch (viewType) {
        case TYPE_START: {
          FrameLayoutFix contentView = new FrameLayoutFix(context);
          contentView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(34f), ViewGroup.LayoutParams.MATCH_PARENT));

          Drawable drawable = Theme.filteredDrawable(R.drawable.stickers_back_left, R.id.theme_color_overlayFilling, themeProvider);

          View view = new View(context);
          ViewUtils.setBackground(view, drawable);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(view);
          }
          view.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(12f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
          contentView.addView(view);

          return new StickerSuggestionHolder(contentView);
        }
        case TYPE_END: {
          FrameLayoutFix contentView = new FrameLayoutFix(context);
          contentView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(34f), ViewGroup.LayoutParams.MATCH_PARENT));

          Drawable drawable = Theme.filteredDrawable(R.drawable.stickers_back_right, R.id.theme_color_overlayFilling, themeProvider);

          View view = new View(context);
          ViewUtils.setBackground(view, drawable);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(view);
          }
          view.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(12f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
          contentView.addView(view);

          return new StickerSuggestionHolder(contentView);
        }
        case TYPE_STICKER: {
          StickerSmallView stickerView = new StickerSmallView(context);
          stickerView.init(tdlib);
          stickerView.setStickerMovementCallback(callback);

          Drawable drawable = Theme.filteredDrawable(R.drawable.stickers_back_center, R.id.theme_color_overlayFilling, themeProvider);

          ViewUtils.setBackground(stickerView, drawable);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(stickerView);
          }
          stickerView.setIsSuggestion();
          stickerView.setPadding(0, Screen.dp(2.5f), 0, Screen.dp(6.5f));
          stickerView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new StickerSuggestionHolder(stickerView);
        }
      }
      throw new RuntimeException("viewType == " + viewType);
    }
  }
}
