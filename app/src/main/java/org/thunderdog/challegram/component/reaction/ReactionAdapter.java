package org.thunderdog.challegram.component.reaction;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactionAdapter extends RecyclerView.Adapter<ReactionAdapter.ViewHolder> {
  private static final Map<String, Integer> ITEM_IDS = new HashMap<>();

  @NonNull
  private List<TdApi.Reaction> items = Collections.emptyList();

  @NonNull
  private final Tdlib tdlib;
  @Nullable
  private final View.OnClickListener onClickListener;

  public ReactionAdapter (@NonNull Tdlib tdlib, @Nullable View.OnClickListener onClickListener) {
    this.tdlib = tdlib;
    this.onClickListener = onClickListener;
    setHasStableIds(true);
  }

  public void setItems (@Nullable List<TdApi.Reaction> items) {
    this.items = items != null ? items : Collections.emptyList();
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return ViewHolder.create(parent, tdlib, onClickListener);
  }

  @Override
  public void onBindViewHolder (@NonNull ViewHolder holder, int position) {
    final TdApi.Reaction item = items.get(position);
    holder.bind(item);
  }

  @Override
  public long getItemId (int position) {
    return getItemViewType(position);
  }

  @Override
  public int getItemViewType (int position) {
    final TdApi.Reaction item = items.get(position);
    Integer itemId = ITEM_IDS.get(item.reaction);
    if (itemId == null) {
      itemId = ITEM_IDS.size();
      ITEM_IDS.put(item.reaction, itemId);
    }
    return itemId;
  }

  @Override
  public int getItemCount () {
    return items.size();
  }

  @Override
  public void onViewAttachedToWindow (@NonNull ViewHolder holder) {
    holder.attach();
  }

  @Override
  public void onViewDetachedFromWindow (@NonNull ViewHolder holder) {
    holder.detach();
  }

  @Override
  public void onViewRecycled (@NonNull ViewHolder holder) {
    holder.detach();
    holder.itemView.setTag(null);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {

    private final ReactionView reactionView;

    public ViewHolder (@NonNull ReactionView itemView) {
      super(itemView);
      reactionView = itemView;
    }

    public void bind (@NonNull TdApi.Reaction item) {
      reactionView.setReaction(item, true);
      reactionView.setTag(item);
    }

    public void attach () {
      reactionView.attach();
    }

    public void detach () {
      reactionView.detach();
    }

    @NonNull
    public static ViewHolder create (@NonNull ViewGroup parent, @NonNull Tdlib tdlib, @Nullable View.OnClickListener onClickListener) {
      ReactionView itemView = new ReactionView(parent.getContext(), tdlib);
      Views.setClickable(itemView);
      RippleSupport.setTransparentSelector(itemView);
      itemView.setPadding(Screen.dp(10f));
      itemView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
      itemView.setOnClickListener(onClickListener);
      return new ViewHolder(itemView);
    }
  }
}
