package org.thunderdog.challegram.component.reactions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

@SuppressLint("ViewConstructor")
public class ReactionsPanelLayout extends FrameLayoutFix implements ReactionsManager.AvailableReactionsListener, FactorAnimator.Target {

    public interface Callback {
        void onReactionClicked(String reaction);
    }

    public static final int REVEAL_ANIMATOR = 1;
    public static final int EXPAND_ANIMATOR = 2;
    public static final int COLLAPSE_ANIMATOR = 3;

    private final ViewController<?> parentViewController;
    private final ReactionsAdapter adapter;
    private final ReactionsManager reactionsManager;
    private final Map<String, TGReaction> availableReactionsMap = new HashMap<>();
    private Callback callback;

    public ReactionsPanelLayout(@NonNull Context context, @NonNull ViewController<?> parent, ReactionsManager reactionsManager) {
        super(context);
        this.parentViewController = parent;
        this.reactionsManager = reactionsManager;

        this.adapter = new ReactionsAdapter();
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setAdapter(this.adapter);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setHorizontalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(Screen.dp(40));
        MarginLayoutParams rvLayoutParams = new MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        recyclerView.setLayoutParams(rvLayoutParams);
        addView(recyclerView);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.reactionsManager.addAvailableReactionsListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.reactionsManager.removeAvailableReactionsListener(this);
    }

    public void loadMessageAvailableReaction(long messageId) {
        reactionsManager.addAvailableReactionsListener(this);
        reactionsManager.loadMessageAvailableReaction(messageId);
    }

    @Override
    public void onMessageAvailableReactions(long chatId, long messageId, String[] reactions) {
        TGReaction[] tgReactions = new TGReaction[reactions.length];
        for (int i = 0; i < reactions.length; i++) {
            TGReaction tgReaction = new TGReaction(parentViewController.tdlib(), reactionsManager);
            tgReaction.setReaction(reactionsManager.getReaction(reactions[i]));
            tgReaction.setState(TGReaction.State.APPEARING);
            tgReactions[i] = tgReaction;
            availableReactionsMap.put(reactions[i], tgReaction);
        }
        adapter.submitList(Arrays.asList(tgReactions));
    }

    @Override
    public void onChatAvailableReactionsUpdated(long chatId, String[] reactions) {

    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {

    }

    private class ReactionsAdapter extends ListAdapter<TGReaction, ReactionHolder> {

        protected ReactionsAdapter() {
            super(new DiffUtilCallback());
        }

        @NonNull
        @Override
        public ReactionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ReactionView reactionView = new ReactionView(getContext(), parentViewController.tdlib());
            reactionView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            int paddingsH = Screen.dp(8);
            int paddingsV = Screen.dp(16);
            reactionView.setPadding(paddingsH, paddingsV, paddingsH, paddingsV);
            return new ReactionHolder(reactionView);
        }

        @Override
        public void onBindViewHolder(@NonNull ReactionHolder holder, int position) {
            holder.bind(getItem(position));
            holder.itemView.setOnClickListener(view -> {
                if (callback != null) callback.onReactionClicked(getItem(position).getReactionString());
            });
        }

    }

    private static class ReactionHolder extends RecyclerView.ViewHolder {

        public ReactionHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void bind(TGReaction tgReaction) {
            if (itemView instanceof ReactionView) {
                ((ReactionView) itemView).setReaction(tgReaction);
            }
        }
    }

    private static class DiffUtilCallback extends DiffUtil.ItemCallback<TGReaction> {

        @Override
        public boolean areItemsTheSame(@NonNull TGReaction oldItem, @NonNull TGReaction newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull TGReaction oldItem, @NonNull TGReaction newItem) {
            return oldItem.equals(newItem);
        }
    }
}
