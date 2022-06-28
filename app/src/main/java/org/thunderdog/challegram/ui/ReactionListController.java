package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.EmojiView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ReactionsLayout;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class ReactionListController extends ViewController<ReactionsLayout> implements View.OnClickListener {
    private CustomRecyclerView recyclerView;
    private LinearLayoutManager manager;
    private ReactionListController.ReactionAdapter adapter;
    private EmojiToneHelper toneHelper;
    private List<Item> items;

    public ReactionListController (Context context, Tdlib tdlib) {
        super(context, tdlib);

        items = new ArrayList<>();
    }

    @Override
    public int getId () {
        return R.id.controller_reactions;
    }

    private boolean useDarkMode;

    @Override
    protected void handleLanguageDirectionChange () {
        super.handleLanguageDirectionChange();
        if (recyclerView != null)
            recyclerView.requestLayout();
    }

    @Override
    protected View onCreateView (Context context) {
        manager = new LinearLayoutManager(context);
        manager.setOrientation(RecyclerView.HORIZONTAL);
        toneHelper = new EmojiToneHelper(context, getArgumentsStrict().getToneDelegate(), this);
        adapter = new ReactionListController.ReactionAdapter(context, items, this, this);

        this.useDarkMode = getArgumentsStrict().useDarkMode();

        recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, getArguments());
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(manager);
        recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
        recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140l));
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    @Override
    public void destroy () {
        super.destroy();
        Views.destroyRecyclerView(recyclerView);
    }

    @Override
    public void onClick (View v) {
        if (!(v instanceof EmojiView)) {
            return;
        }
        EmojiView emojiView = (EmojiView) v;
        String rawEmoji = emojiView.getRawEmoji();
        String emoji = emojiView.getEmojiColored();
    }

    @Override
    public void setArguments(ReactionsLayout reactionsLayout) {
        String[] reactions = reactionsLayout.getReactions();
        for (int i = 0; i < reactions.length; i++) {
            String reaction = reactions[i];
            int reactionColorState = EmojiData.instance().getEmojiColorState(reaction);
            items.add(new Item(reaction, reactionColorState));
            if (adapter != null) {
                adapter.notifyItemInserted(i);
            }
        }

        super.setArguments(reactionsLayout);
    }

    private static class Item {
        public final String reaction;
        public final int reactionColorState;

        public Item (String reaction, int reactionColorState) {
            this.reaction = reaction;
            this.reactionColorState = reactionColorState;
        }
    }

    private static class ItemHolder extends RecyclerView.ViewHolder {
        public ItemHolder (View itemView) {
            super(itemView);
        }
    }

    private static class ReactionAdapter extends RecyclerView.Adapter<ReactionListController.ItemHolder> {
        private static final float reactionSize = 60f;
        private static final float reactionPadding = 10f;

        private final Context context;
        private final View.OnClickListener onClickListener;
        private final ReactionListController parent;
        private final List<Item> items;

        public ReactionAdapter (Context context, List<Item> items, View.OnClickListener onClickListener, ReactionListController parent) {
            this.context = context;
            this.onClickListener = onClickListener;
            this.parent = parent;
            this.items = items;
        }
        @NonNull
        @Override
        public ItemHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
            EmojiView imageView = new EmojiView(context, this.parent.toneHelper);
            imageView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(reactionSize), Screen.dp(reactionSize)));
            imageView.setPadding(
                    Screen.dp(reactionPadding),
                    Screen.dp(reactionPadding),
                    Screen.dp(reactionPadding),
                    Screen.dp(reactionPadding)
            );
            imageView.setOnClickListener(onClickListener);
            Views.setClickable(imageView);
            RippleSupport.setTransparentSelector(imageView);
            return new ReactionListController.ItemHolder(imageView);
        }

        @Override
        public void onBindViewHolder (@NonNull ItemHolder holder, int position) {
            ReactionListController.Item item = items.get(position);
            holder.itemView.setId(R.id.emoji);
            ((EmojiView) holder.itemView).setEmoji(item.reaction, item.reactionColorState);
        }

        @Override
        public int getItemCount () {
            return items.size();
        }
    }
}
