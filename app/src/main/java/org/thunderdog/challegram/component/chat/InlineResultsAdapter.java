package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.emoji.GifView;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultButton;
import org.thunderdog.challegram.data.InlineResultGif;
import org.thunderdog.challegram.data.InlineResultPhoto;
import org.thunderdog.challegram.data.InlineResultSticker;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;

/**
 * Date: 03/12/2016
 * Author: default
 */

public class InlineResultsAdapter extends RecyclerView.Adapter<InlineResultsAdapter.ViewHolder> {
  private final Context context;
  private final InlineResultsWrap parent;
  private final ArrayList<InlineResult<?>> items;
  private final ThemeListenerList themeProvider;

  private Tdlib tdlib;

  public interface HeightProvider {
    int provideHeight ();
  }

  private boolean useDarkMode;

  public InlineResultsAdapter (Context context, InlineResultsWrap parent, ThemeListenerList themeProvider) {
    this.context = context;
    this.items = new ArrayList<>();
    this.parent = parent;
    this.themeProvider = themeProvider;
  }

  public void setTdlib (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public boolean useDarkMode () {
    return useDarkMode;
  }

  public void setUseDarkMode (boolean useDarkMode) {
    this.useDarkMode = useDarkMode;
  }

  public void setItems (ArrayList<InlineResult<?>> items) {
    int oldItemCount = getItemCount();
    this.items.clear();
    if (items != null && !items.isEmpty()) {
      this.items.addAll(items);
    }
    U.notifyItemsReplaced(this, oldItemCount);
  }

  public void removeItemAt (int i) {
    items.remove(i);
    notifyItemRemoved(i + 1);
  }

  public void addItems (ArrayList<InlineResult<?>> items) {
    int i = getItemCount();
    this.items.addAll(items);
    notifyItemRangeInserted(i, items.size());
  }

  @Override
  public ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return ViewHolder.create(context, tdlib, viewType, useDarkMode, this.parent, this.parent, this.parent, this.parent, this.parent, themeProvider);
  }

  @Override
  public void onViewAttachedToWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case ViewHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).attach();
        break;
      }
      case ViewHolder.TYPE_GIF: {
        ((GifView) holder.itemView).attach();
        break;
      }
      case ViewHolder.TYPE_PHOTO: {
        ((DoubleImageView) holder.itemView).attach();
        break;
      }
      case ViewHolder.TYPE_CUSTOM: {
        ((CustomResultView) holder.itemView).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case ViewHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).detach();
        break;
      }
      case ViewHolder.TYPE_GIF: {
        ((GifView) holder.itemView).detach();
        break;
      }
      case ViewHolder.TYPE_PHOTO: {
        ((DoubleImageView) holder.itemView).detach();
        break;
      }
      case ViewHolder.TYPE_CUSTOM: {
        ((CustomResultView) holder.itemView).detach();
        break;
      }
    }
  }

  @Override
  public void onBindViewHolder (ViewHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case ViewHolder.TYPE_HEADER: {
        if (holder.itemView.getMeasuredHeight() != parent.provideHeight()) {
          holder.itemView.requestLayout();
        }
        break;
      }
      case ViewHolder.TYPE_STICKER: {
        InlineResult<?> result = items.get(position - 1);
        ((StickerSmallView) holder.itemView).setSticker(((InlineResultSticker) result).getSticker());
        holder.itemView.setTag(result);
        break;
      }
      case ViewHolder.TYPE_CUSTOM: {
        InlineResult<?> result = items.get(position - 1);
        ((CustomResultView) holder.itemView).setInlineResult(result);
        holder.itemView.setTag(result);
        break;
      }
      case ViewHolder.TYPE_GIF: {
        InlineResult<?> result = items.get(position - 1);
        ((GifView) holder.itemView).setGif(((InlineResultGif) result).getGif());
        holder.itemView.setTag(result);
        break;
      }
      case ViewHolder.TYPE_PHOTO: {
        InlineResultPhoto result = (InlineResultPhoto) items.get(position - 1);
        ((DoubleImageView) holder.itemView).setImage(result.getMiniThumbnail(), result.getPreview(), result.getImage());
        holder.itemView.setTag(result);
        break;
      }
      case ViewHolder.TYPE_BUTTON: {
        InlineResultButton button = (InlineResultButton) items.get(position - 1);
        ((TextView) holder.itemView).setText(button.getText().toUpperCase());
        holder.itemView.setTag(button);
        break;
      }
    }
  }

  @Override
  public int getItemCount () {
    return items.size() + 1;
  }

  @Override
  public int getItemViewType (int position) {
    if (position-- == 0) {
      return ViewHolder.TYPE_HEADER;
    }
    switch (items.get(position).getType()) {
      case InlineResult.TYPE_STICKER: {
        return ViewHolder.TYPE_STICKER;
      }
      case InlineResult.TYPE_BUTTON: {
        return ViewHolder.TYPE_BUTTON;
      }
      case InlineResult.TYPE_GIF: {
        return ViewHolder.TYPE_GIF;
      }
      case InlineResult.TYPE_PHOTO: {
        return ViewHolder.TYPE_PHOTO;
      }

      case InlineResult.TYPE_HASHTAG:
      case InlineResult.TYPE_COMMAND:
      case InlineResult.TYPE_MENTION:
      default: {
        return ViewHolder.TYPE_CUSTOM;
      }
    }
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_CUSTOM = 1;
    public static final int TYPE_BUTTON = 2;
    public static final int TYPE_STICKER = 3;
    public static final int TYPE_GIF = 4;
    public static final int TYPE_PHOTO = 5;

    public ViewHolder (View itemView) {
      super(itemView);
    }

    public static ViewHolder create (Context context, Tdlib tdlib, int viewType, boolean useDarkMode, BaseView.CustomControllerProvider customControllerProvider, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, StickerSmallView.StickerMovementCallback callback, final HeightProvider provider, final @Nullable ThemeListenerList themeList) {
      switch (viewType) {
        case TYPE_HEADER: {
          ShadowView view = new ShadowView(context) {
            @Override
            protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
              setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                MeasureSpec.makeMeasureSpec(provider.provideHeight(), MeasureSpec.EXACTLY));
            }
          };
          view.setSimpleTopShadow(false);
          view.setAlignBottom();
          if (themeList != null && !useDarkMode) {
            themeList.addThemeInvalidateListener(view);
          }
          return new ViewHolder(view);
        }
        case TYPE_GIF: {
          GifView gifView = new GifView(context);
          gifView.setCustomControllerProvider(customControllerProvider);
          gifView.setId(R.id.result);
          gifView.setOnClickListener(onClickListener);
          return new ViewHolder(gifView);
        }
        case TYPE_STICKER: {
          StickerSmallView stickerView = new StickerSmallView(context);
          stickerView.init(tdlib);
          stickerView.setId(R.id.result);
          stickerView.setEmojiDisabled();
          stickerView.setStickerMovementCallback(callback);
          return new ViewHolder(stickerView);
        }
        case TYPE_CUSTOM: {
          CustomResultView customView = new CustomResultView(context);
          customView.setId(R.id.result);
          customView.setOnClickListener(onClickListener);
          customView.setOnLongClickListener(onLongClickListener);
          customView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          if (themeList != null && !useDarkMode) {
            themeList.addThemeInvalidateListener(customView);
          }
          return new ViewHolder(customView);
        }
        case TYPE_PHOTO: {
          DoubleImageView photoView = new DoubleImageView(context);
          photoView.setCustomControllerProvider(customControllerProvider);
          photoView.setId(R.id.result);
          photoView.setOnClickListener(onClickListener);
          return new ViewHolder(photoView);
        }
        case TYPE_BUTTON: {
          TextView textView = new NoScrollTextView(context) {
            @Override
            protected void onDraw (Canvas c) {
              super.onDraw(c);
              int height = Math.max(1, Screen.dp(.5f));
              c.drawRect(0, getMeasuredHeight() - height, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.separatorColor()));
            }
          };
          textView.setId(R.id.btn_switchPmButton);
          textView.setGravity(Gravity.CENTER);
          textView.setOnClickListener(onClickListener);
          textView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(1f));
          textView.setTypeface(Fonts.getRobotoMedium());
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          textView.setTextColor(useDarkMode ? Theme.getColor(R.id.theme_color_textNeutral, ThemeId.NIGHT_BLACK) : Theme.getColor(R.id.theme_color_textNeutral));
          if (themeList != null && !useDarkMode) {
            themeList.addThemeColorListener(textView, R.id.theme_color_textNeutral);
            themeList.addThemeInvalidateListener(textView);
          }
          Views.setClickable(textView);
          RippleSupport.setTransparentSelector(textView);
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(36f) + Screen.dp(1f)));
          return new ViewHolder(textView);
        }
      }
      throw new RuntimeException("viewType == " + viewType);
    }
  }
}
