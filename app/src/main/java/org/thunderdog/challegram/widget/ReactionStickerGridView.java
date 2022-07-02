/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;

import java.util.Arrays;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class ReactionStickerGridView extends FrameLayoutFix implements ReactionClickListener, Destroyable {
  private CustomRecyclerView recyclerView;
  private ReactionStickerGridView.EmojiAdapter adapter;
  private Tdlib tdlib;
  private TdApi.Reaction[] reactions;
  private OnClickListener listener;
  private int mode;

  public ReactionStickerGridView(Context context) {
    this(context, null, 0);
  }

  public ReactionStickerGridView(Context context, int mode, @Nullable OnClickListener onReactionSelected) {
    this(context, null, 0);
    this.listener = onReactionSelected;
    this.mode = mode;
  }

  public ReactionStickerGridView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ReactionStickerGridView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    ViewSupport.setThemedBackground(this, R.id.theme_color_headerLightBackground/*R.id.theme_color_headerBackground*/);
  }

  public void init(Tdlib tdlib) {
    this.tdlib = tdlib;
    reactions = tdlib.getSupportedReactions();
    GridLayoutManager manager = new RtlGridLayoutManager(getContext(), 4).setAlignOnly(true);
    adapter = new EmojiAdapter(getContext(), this, reactions, tdlib);

    recyclerView = (CustomRecyclerView) Views.inflate(getContext(), R.layout.recycler_custom, null);
    recyclerView.setHasFixedSize(true);
    recyclerView.setItemAnimator(null);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setLayoutManager(manager);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(adapter);
    addView(recyclerView);
  }

  public void setSelectedReaction(String[] selectedReactions) {
    adapter.updateSelectedItems(selectedReactions);
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onClick(View view, int position) {
    EmbeddableStickerView stickerView = ((EmbeddableStickerView) view);
    stickerView.toggle(true);
    TdApi.Sticker sticker;
    if (stickerView.isChecked()) {
      sticker = reactions[position].selectAnimation;
      TdApi.Sticker effect = reactions[position].effectAnimation;
      stickerView.setStickerEffect(new TGStickerObj(tdlib, effect, null, effect.type));
      adapter.updateSelectedItems(new String[]{stickerView.getCaptionText().toString()});
      stickerView.setAlpha(1f);
    } else {
      sticker = reactions[position].staticIcon;
      adapter.updateSelectedItems(new String[]{});
      stickerView.setAlpha(0.3f);
    }
    if (mode == 0) {
      invalidateCheckedEmoji(position);
    }
    stickerView.setSticker(new TGStickerObj(tdlib, sticker, null, sticker.type));
    if (listener != null) {
      listener.onClick(view);
    }
  }

  @Override
  public void performDestroy() {
    // TODO tim provide destroy to emoji views
    /*if (recyclerView.getLayoutManager() != null) {
      for (int i = 0; i < adapter.getItemCount(); i++) {
        View view = recyclerView.getLayoutManager().findViewByPosition(i);
        if (view != null) {
          ((EmbeddableStickerView) view).performDestroy();
        }
      }
    }*/
  }


  private void invalidateCheckedEmoji(int excludePosition) {
    if (recyclerView.getLayoutManager() != null) {
      for (int i = 0; i < adapter.getItemCount(); i++) {
        if (excludePosition == i) {
          continue;
        }
        View view = recyclerView.getLayoutManager().findViewByPosition(i);
        if (view != null) {
          if (((EmbeddableStickerView) view).isChecked()) {
            adapter.notifyItemChanged(i);
          }
        }
      }
    }
  }

  private static class ItemHolder extends RecyclerView.ViewHolder {
    public ItemHolder (View itemView) {
      super(itemView);
    }
  }

  public static class EmojiAdapter extends RecyclerView.Adapter<ItemHolder> {
    private final Context context;
    private final ReactionClickListener onClickListener;
    private final TdApi.Reaction[] items;
    private String[] selectedReactions;
    private final Tdlib tdlib;

    public EmojiAdapter (Context context, ReactionClickListener onClickListener, TdApi.Reaction[] items, Tdlib tdlib) {
      this.context = context;
      this.onClickListener = onClickListener;
      this.items = items;
      this.tdlib = tdlib;
    }

    public void updateSelectedItems(String[] selectedReactions) {
      this.selectedReactions = selectedReactions;
    }

    @NonNull
    @Override
    public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      EmbeddableStickerView stickerView = new EmbeddableStickerView(context, 48, true);
      stickerView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      stickerView.attach();
      Views.setClickable(stickerView);
      RippleSupport.setTransparentSelector(stickerView);
      return new ItemHolder(stickerView);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
      holder.itemView.setId(R.id.reaction);
      EmbeddableStickerView view = (EmbeddableStickerView) holder.itemView;
      view.setOnClickListener(v -> onClickListener.onClick(view, position));
      TdApi.Reaction reaction = items[position];
      String stickerTitle = reaction.title;
      TdApi.Sticker sticker = reaction.staticIcon;
      view.setSticker(new TGStickerObj(tdlib, sticker, null, sticker.type));
      view.setCaptionText(stickerTitle);
      view.setChecked(isSelectedByTitle(reaction.title) || isSelectedByEmoji(reaction.reaction), false);
      view.setAlpha(isSelectedByTitle(reaction.title) || isSelectedByEmoji(reaction.reaction) ? 1f : 0.3f);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
      super.onAttachedToRecyclerView(recyclerView);
      if (recyclerView.getLayoutManager() != null) {
        for (int i = 0; i < getItemCount(); i++) {
          View view = recyclerView.getLayoutManager().findViewByPosition(i);
          if (view != null) {
            ((EmbeddableStickerView) view).attach();
          }
        }
      }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
      super.onDetachedFromRecyclerView(recyclerView);
      if (recyclerView.getLayoutManager() != null) {
        for (int i = 0; i < getItemCount(); i++) {
          View view = recyclerView.getLayoutManager().findViewByPosition(i);
          if (view != null) {
            ((EmbeddableStickerView) view).detach();
          }
        }
      }
    }

    @Override
    public int getItemCount() {
      return items.length;
    }

    private boolean isSelectedByTitle(String reactionTitle) {
      if (selectedReactions != null && selectedReactions.length != 0) {
        return Arrays.asList(selectedReactions).contains(reactionTitle);
      } else {
        return false;
      }
    }

    private boolean isSelectedByEmoji(String reactionEmoji) {
      if (selectedReactions != null && selectedReactions.length != 0) {
        return Arrays.asList(selectedReactions).contains(reactionEmoji);
      } else {
        return false;
      }
    }
  }
}
