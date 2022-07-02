package org.thunderdog.challegram.ui;


import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.EmojiView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class QuickReactionController extends ViewController<QuickReactionController.Arguments> implements View.OnClickListener {

    public static class Arguments {
        public final int selectedQuickReaction;

        public Arguments (int selectedQuickReaction) {
            this.selectedQuickReaction = selectedQuickReaction;
        }
    }

    public QuickReactionController(@NonNull Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    private CustomRecyclerView recyclerView;
    private GridLayoutManager manager;
    private QuickReactionController.EmojiAdapter adapter;
    private EmojiToneHelper toneHelper;
    private static final int MINIMUM_EMOJI_COUNT = 4;

    private int calculateSpanCount () {
        int width = 0;
        if (recyclerView != null) {
            width = recyclerView.getMeasuredWidth();
        }
        if (width == 0) {
            width = Screen.currentWidth();
        }
        return Math.max(MINIMUM_EMOJI_COUNT, width / Screen.dp(48f));
    }

    @Override
    public int getId() {
        return R.id.controller_quickReactions;
    }

    @Override
    protected View onCreateView(Context context) {
        manager = new RtlGridLayoutManager(context, calculateSpanCount()).setAlignOnly(true);
        // toneHelper = new EmojiToneHelper(context, getArgumentsStrict().getToneDelegate(), this);
        adapter = new QuickReactionController.EmojiAdapter(context, this);

        recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, null);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(manager);
        recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
        recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140l));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
                /*boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
                if (getArguments() != null && getArguments().getCurrentItem() == 0) {
                    getArguments().setIsScrolling(isScrolling);
                }*/
            }

            @Override
            public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
                /*if (getArguments() != null && getArguments().isWatchingMovements() && getArguments().getCurrentItem() == 0) {
                    getArguments().onScroll(getCurrentScrollY());
                    if (lastScrollAnimator == null || !lastScrollAnimator.isAnimating()) {
                        getArguments().setCurrentEmojiSection(getCurrentSection());
                    }
                }*/
            }
        });
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize (int position) {
                return 1;
            }
        });
        recyclerView.setAdapter(adapter);

        //TGLegacyManager.instance().addEmojiListener(this);
        //Emoji.instance().addEmojiChangeListener(adapter);

        return recyclerView;
    }

    @Override
    public void onClick(View v) {

    }

    private static class Item {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_EMOJI = 1;
        public static final int TYPE_OFFSET = 2;

        public final String emoji;
        public final String title;
        public final boolean isChecked;

        public Item (int emoji, int title, boolean isChecked) {
            this.emoji = Lang.getString(emoji);
            this.title = Lang.getString(title);
            this.isChecked = isChecked;
        }
    }

    private static class ItemHolder extends RecyclerView.ViewHolder {
        public ItemHolder (View itemView) {
            super(itemView);
        }
    }

    private static class EmojiAdapter extends RecyclerView.Adapter<QuickReactionController.ItemHolder> {
        private final Context context;
        private final View.OnClickListener onClickListener;
        private final ArrayList<QuickReactionController.Item> items;

        public EmojiAdapter (Context context, View.OnClickListener onClickListener) {
            this.context = context;
            this.onClickListener = onClickListener;

            this.items = new ArrayList<>();
        }

        @NonNull
        @Override
        public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            EmojiView imageView = new EmojiView(context, null);
            imageView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setOnClickListener(onClickListener);
            Views.setClickable(imageView);
            RippleSupport.setTransparentSelector(imageView);
            return new QuickReactionController.ItemHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
            QuickReactionController.Item item = items.get(position);
            holder.itemView.setId(R.id.emoji);
            ((EmojiView) holder.itemView).setEmoji(item.emoji, 0);
        }

        @Override
        public int getItemCount () {
            return items.size();
        }
    }
}
