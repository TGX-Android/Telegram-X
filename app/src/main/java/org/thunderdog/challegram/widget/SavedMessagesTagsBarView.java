package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.data.TGSavedMessagesTag;
import org.thunderdog.challegram.data.TGSavedMessagesTags;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Horizontal bar showing Saved Messages tags for filtering.
 */
public class SavedMessagesTagsBarView extends RecyclerView {
  private final Tdlib tdlib;
  private TagsAdapter adapter;
  private @Nullable TagSelectionListener listener;
  private @Nullable TagLongPressListener longPressListener;
  private @Nullable String selectedTagKey;

  public interface TagSelectionListener {
    void onTagSelected (@Nullable TdApi.ReactionType tag);
  }

  public interface TagLongPressListener {
    void onTagLongPressed (TGSavedMessagesTag tag);
  }

  public SavedMessagesTagsBarView (@NonNull Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;

    LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
      @Override
      protected boolean isLayoutRTL () {
        return false;
      }
    };

    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    setPadding(Screen.dp(8), Screen.dp(4), Screen.dp(8), Screen.dp(4));
    setClipToPadding(false);
    setHasFixedSize(true);
    addItemDecoration(new ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
        outRect.set(Screen.dp(4), 0, Screen.dp(4), 0);
      }
    });

    setLayoutManager(layoutManager);
    setAdapter(adapter = new TagsAdapter());
  }

  public void setTagSelectionListener (@Nullable TagSelectionListener listener) {
    this.listener = listener;
  }

  public void setTagLongPressListener (@Nullable TagLongPressListener listener) {
    this.longPressListener = listener;
  }

  public void setTags (@Nullable TGSavedMessagesTags tags) {
    adapter.setTags(tags);
  }

  public void setSelectedTag (@Nullable TdApi.ReactionType tag) {
    String newKey = tag != null ? TD.makeReactionKey(tag) : null;
    if ((selectedTagKey == null && newKey == null) ||
        (selectedTagKey != null && selectedTagKey.equals(newKey))) {
      return;
    }
    selectedTagKey = newKey;
    adapter.notifyDataSetChanged();
  }

  @Nullable
  public String getSelectedTagKey () {
    return selectedTagKey;
  }

  private void onTagClicked (TGSavedMessagesTag tag) {
    String key = tag.getKey();
    if (key.equals(selectedTagKey)) {
      // Deselect
      selectedTagKey = null;
      adapter.notifyDataSetChanged();
      if (listener != null) {
        listener.onTagSelected(null);
      }
    } else {
      // Select
      selectedTagKey = key;
      adapter.notifyDataSetChanged();
      if (listener != null) {
        listener.onTagSelected(tag.getReactionType());
      }
    }
  }

  private boolean onTagLongClicked (TGSavedMessagesTag tag) {
    if (longPressListener != null) {
      longPressListener.onTagLongPressed(tag);
      return true;
    }
    return false;
  }

  private class TagsAdapter extends RecyclerView.Adapter<TagViewHolder> {
    private final List<TGSavedMessagesTag> tags = new ArrayList<>();

    public void setTags (@Nullable TGSavedMessagesTags tagsContainer) {
      tags.clear();
      if (tagsContainer != null) {
        tags.addAll(tagsContainer.getTags());
      }
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      TagChipView view = new TagChipView(parent.getContext());
      view.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Screen.dp(32)
      ));
      return new TagViewHolder(view);
    }

    @Override
    public void onBindViewHolder (@NonNull TagViewHolder holder, int position) {
      TGSavedMessagesTag tag = tags.get(position);
      boolean isSelected = tag.getKey().equals(selectedTagKey);
      holder.bind(tag, isSelected);
    }

    @Override
    public int getItemCount () {
      return tags.size();
    }
  }

  private class TagViewHolder extends RecyclerView.ViewHolder {
    private final TagChipView chipView;

    public TagViewHolder (@NonNull View itemView) {
      super(itemView);
      this.chipView = (TagChipView) itemView;
      itemView.setOnClickListener(v -> {
        int position = getAdapterPosition();
        if (position != RecyclerView.NO_POSITION && position < adapter.tags.size()) {
          onTagClicked(adapter.tags.get(position));
        }
      });
      itemView.setOnLongClickListener(v -> {
        int position = getAdapterPosition();
        if (position != RecyclerView.NO_POSITION && position < adapter.tags.size()) {
          return onTagLongClicked(adapter.tags.get(position));
        }
        return false;
      });
    }

    public void bind (TGSavedMessagesTag tag, boolean isSelected) {
      chipView.setTag(tag, isSelected);
    }
  }

  /**
   * View for a single tag chip showing emoji + optional label + count
   */
  private class TagChipView extends FrameLayoutFix {
    private @Nullable TGSavedMessagesTag tag;
    private @Nullable StickerSmallView stickerView;
    private boolean isSelected;
    private Counter countCounter;
    private final RectF rect = new RectF();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TagChipView (Context context) {
      super(context);
      setWillNotDraw(false);

      stickerView = new StickerSmallView(context, 0);
      stickerView.setLayoutParams(newParams(Screen.dp(24), Screen.dp(24), Gravity.LEFT | Gravity.CENTER_VERTICAL));
      addView(stickerView);

      countCounter = new Counter.Builder()
        .noBackground()
        .textColor(ColorId.text, ColorId.text, ColorId.text)
        .textSize(12f)
        .allBold(false)
        .callback(this)
        .build();
    }

    public void setTag (@Nullable TGSavedMessagesTag tag, boolean isSelected) {
      this.tag = tag;
      this.isSelected = isSelected;

      if (tag != null) {
        TGReaction reaction = tag.getReaction();
        if (reaction != null && stickerView != null) {
          TGStickerObj sticker = reaction.staticCenterAnimationSicker();
          stickerView.setSticker(sticker);
        }

        // Set count or label
        String label = tag.getLabel();
        if (label != null && !label.isEmpty()) {
          countCounter.setCount(1, false, label, false);
        } else {
          countCounter.setCount(tag.getCount(), false, false);
        }
      }

      requestLayout();
      invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int height = Screen.dp(32);
      int width = Screen.dp(24 + 8); // sticker + padding

      if (tag != null) {
        width += (int) countCounter.getWidth() + Screen.dp(8);
      }

      setMeasuredDimension(width, height);

      if (stickerView != null) {
        int stickerSize = Screen.dp(24);
        stickerView.measure(
          MeasureSpec.makeMeasureSpec(stickerSize, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(stickerSize, MeasureSpec.EXACTLY)
        );
      }
    }

    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
      if (stickerView != null) {
        int stickerSize = Screen.dp(24);
        int stickerLeft = Screen.dp(4);
        int stickerTop = (bottom - top - stickerSize) / 2;
        stickerView.layout(stickerLeft, stickerTop, stickerLeft + stickerSize, stickerTop + stickerSize);
      }
    }

    @Override
    protected void onDraw (Canvas canvas) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      int radius = height / 2;

      // Draw background
      int bgColor = isSelected
        ? Theme.getColor(ColorId.fillingPositive)
        : Theme.getColor(ColorId.filling);
      backgroundPaint.setColor(bgColor);
      rect.set(0, 0, width, height);
      canvas.drawRoundRect(rect, radius, radius, backgroundPaint);

      // Draw count/label
      if (tag != null) {
        float textX = Screen.dp(24 + 8);
        float textY = height / 2f;
        countCounter.draw(canvas, textX, textY, Gravity.LEFT, 1f);
      }
    }
  }
}
