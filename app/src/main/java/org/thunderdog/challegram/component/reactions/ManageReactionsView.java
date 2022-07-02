package org.thunderdog.challegram.component.reactions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.GridSpacingItemDecoration;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import me.vkryl.android.widget.FrameLayoutFix;

@SuppressLint("ViewConstructor")
public class ManageReactionsView extends RecyclerView implements ReactionsManager.QuickReactionListener, ReactionsManager.ChatReactionsListener {

    public static final int SELECTION_MODE_SINGLE = 0;
    public static final int SELECTION_MODE_MULTIPLE = 1;

    public static class ManageInfo {
        final int selectionMode;

        public ManageInfo() {
            this.selectionMode = SELECTION_MODE_SINGLE;
        }

        public ManageInfo(int selectionMode) {
            this.selectionMode = selectionMode;
        }
    }

    private final ReactionsGridAdapter gridAdapter;
    private final Set<String> selected = new HashSet<>();
    private int selectionMode = SELECTION_MODE_SINGLE;
    private int lastSelectionPosition = -1;
    private int selectionPosition = -1;
    private final ReactionsManager reactionsManager;
    private final Tdlib tdlib;

    public ManageReactionsView(@NonNull Context context, Tdlib tdlib) {
        super(context);
        int padding = Screen.dp(16);
        setPadding(padding, padding, padding, padding);
        this.tdlib = tdlib;
        gridAdapter = new ReactionsGridAdapter();
        this.reactionsManager = ReactionsManager.instance(tdlib);
        this.setItemAnimator(null);
        this.setAdapter(gridAdapter);
        this.addItemDecoration(new GridSpacingItemDecoration(4, Screen.dp(16), false, true, false));
        this.setLayoutManager(new GridLayoutManager(context, 4, RecyclerView.VERTICAL, false));
        this.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onQuickReactionStateUpdated() {
        if (selectionMode == SELECTION_MODE_SINGLE) {
            TGReaction quickReaction = convertReaction(reactionsManager.getQuickReaction());
            if (quickReaction == null) {
                int currentPosition = selectionPosition;
                selectionPosition = -1;
                lastSelectionPosition = selectionPosition;
                gridAdapter.notifyItemChanged(currentPosition);
                return;
            }
            int reactionPosition = findReactionPosition(gridAdapter.getCurrentList(), quickReaction);
            if (reactionPosition != selectionPosition) {
                if (lastSelectionPosition != -1) {
                    gridAdapter.notifyItemChanged(lastSelectionPosition);
                }
                selectionPosition = reactionPosition;
                lastSelectionPosition = selectionPosition;
                gridAdapter.notifyItemChanged(selectionPosition);

            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onChatReactionStateUpdated() {
        if (selectionMode == SELECTION_MODE_MULTIPLE) {
            String[] available = reactionsManager.getChatAvailableReactions();
            if (available == null || available.length == 0) {
                selected.clear();
                gridAdapter.notifyDataSetChanged();
                return;
            }
            Collection<String> availableCollection = Arrays.asList(available);
            if (selected.containsAll(availableCollection)) return;
            selected.addAll(availableCollection);
            gridAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reactionsManager.addQuickReactionStateListener(this);
        reactionsManager.addChatReactionsListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reactionsManager.removeQuickReactionStateListener(this);
        reactionsManager.removeChatReactionsListener(this);
    }

    public void setManageInfo(ManageInfo manageInfo) {
        this.selectionMode = manageInfo.selectionMode;
        List<TGReaction> supportedReaction = convertReactions(reactionsManager.getSupportedReactions());

        if (selectionMode == SELECTION_MODE_SINGLE) {
            TGReaction quickReaction = convertReaction(reactionsManager.getQuickReaction());
            selectionPosition = findReactionPosition(supportedReaction, quickReaction);
            lastSelectionPosition = selectionPosition;
        }

        if (selectionMode == SELECTION_MODE_MULTIPLE) {
            String[] available = reactionsManager.getChatAvailableReactions();
            selected.clear();
            if (available != null) {
                selected.addAll(Arrays.asList(available));
            }
        }

        gridAdapter.submitList(supportedReaction);
    }

    private int findReactionPosition(List<TGReaction> currentList, TGReaction reaction) {
        if (reaction == null || currentList == null || currentList.isEmpty()) return -1;
        for (int i = 0; i < currentList.size(); i++) {
            TGReaction item = currentList.get(i);
            if (Objects.equals(item.getReactionString(), reaction.getReactionString())) {
                return i;
            }
        }
        return -1;
    }

    private List<TGReaction> convertReactions(List<TdApi.Reaction> reactions) {
        List<TGReaction> result = new ArrayList<>(reactions.size());
        for (TdApi.Reaction r: reactions) {
            result.add(convertReaction(r));
        }
        return result;
    }

    private TGReaction convertReaction(TdApi.Reaction r) {
        if (r == null) return null;
        TGReaction reaction = new TGReaction(tdlib, reactionsManager);
        reaction.setReaction(r);
        return reaction;
    }

    private boolean isItemSelected(int position, String key) {
        if (selectionMode == SELECTION_MODE_SINGLE) {
            return selectionPosition == position;
        } else {
            return selected.contains(key);
        }
    }

    private boolean select(int position, String key) {
        boolean isSelected;
        if (selectionMode == SELECTION_MODE_SINGLE) {
            if (position == selectionPosition) {
                isSelected = false;
                selectionPosition = -1;
                lastSelectionPosition = -1;
            } else {
                isSelected = true;
                lastSelectionPosition = selectionPosition;
                selectionPosition = position;
            }
            if(lastSelectionPosition != -1) {
                gridAdapter.notifyItemChanged(lastSelectionPosition);
            }
            onSelectionUpdated();
        } else if (selectionMode == SELECTION_MODE_MULTIPLE){
            if (!selected.contains(key)) {
                selected.add(key);
                isSelected = true;
            } else {
                selected.remove(key);
                isSelected = false;
            }
            gridAdapter.notifyItemChanged(position);
            onSelectionUpdated();
        } else {
            isSelected = false;
        }
        return isSelected;
    }

    private void onSelectionUpdated() {
        if (selectionMode == SELECTION_MODE_SINGLE) {
            boolean isQuickEnabled = selectionPosition != -1;
            if (isQuickEnabled) {
                TGReaction current = gridAdapter.getCurrentList().get(selectionPosition);
                reactionsManager.setQuickReaction(current.getReactionString());
            } else {
                reactionsManager.setQuickReactionEnabled(false);
            }
        }

        if (selectionMode == SELECTION_MODE_MULTIPLE) {
            String[] reactionStrings = selected.toArray(new String[]{});
            reactionsManager.updateAvailableReactions(reactionStrings);
        }
    }

    private class ReactionsGridAdapter extends ListAdapter<TGReaction, ReactionHolder> {

        public ReactionsGridAdapter() {
            super(new ReactionGridDiffUtil());
        }

        @NonNull
        @Override
        public ReactionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ReactionHolder(new ReactionItemView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull ReactionHolder holder, int position) {
            holder.bind(getItem(position));
        }
    }

    private static class ReactionGridDiffUtil extends DiffUtil.ItemCallback<TGReaction> {

        @Override
        public boolean areItemsTheSame(@NonNull TGReaction oldItem, @NonNull TGReaction newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull TGReaction oldItem, @NonNull TGReaction newItem) {
            return oldItem.equals(newItem);
        }
    }

    private class ReactionHolder extends RecyclerView.ViewHolder implements OnClickListener {

        private TGReaction reaction;

        public ReactionHolder(@NonNull ReactionItemView itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
        }

        public void bind(TGReaction reaction) {
            this.reaction = reaction;
            if (itemView instanceof ReactionItemView) {
                ((ReactionItemView) itemView).setReaction(reaction);
                itemView.setSelected(isItemSelected(getBindingAdapterPosition(), reaction.getReactionString()));
            }
        }

        @Override
        public void onClick(View view) {
            boolean isSelected = select(getBindingAdapterPosition(), reaction.getReactionString());
            itemView.setSelected(isSelected);
        }
    }

    private static class ReactionItemView extends FrameLayoutFix {

        private TextView textView;
        private ImageView imageView;
        private ImageView selectionView;
        private final int imageSize;
        private final int spaceBetween;
        private final int checkSize;

        public ReactionItemView(@NonNull Context context) {
            super(context);
            imageSize = Screen.dp(24);
            spaceBetween = Screen.dp(16);
            checkSize = Screen.dp(16);
            setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(80)));
            createView(context);
        }

        public void setReaction(TGReaction reaction) {
            this.textView.setText(reaction.getReactionTitle());
            Drawable drawable = new ReactionDrawable(new ReactionDrawable.ReactionInfo(reaction, this.imageView));
            this.imageView.setImageDrawable(drawable);
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            imageView.setAlpha(selected ? 1f : 0.65f);
            textView.setAlpha(selected ? 1f : 0.65f);
            selectionView.setVisibility(selected ? VISIBLE : GONE);
        }

        private void createView(Context context) {
            imageView = new ImageView(context);
            LayoutParams imageLp = new LayoutParams(imageSize, imageSize);
            imageLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            addView(imageView, imageLp);

            textView = new TextView(context);
            LayoutParams textLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            textLp.topMargin = imageSize + spaceBetween;
            textView.setTextSize(12);
            textView.setTextColor(Theme.getColor(R.id.theme_color_text));
            textView.setGravity(Gravity.CENTER);
            textView.setMaxLines(2);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            addView(textView, textLp);

            selectionView = new ImageView(context);
            LayoutParams selectionViewLp = new LayoutParams(checkSize, checkSize);
            selectionViewLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            selectionViewLp.leftMargin = Screen.dp(10);
            selectionViewLp.topMargin = imageSize - checkSize / 2;
            selectionView.setImageDrawable(createSelectionDrawable());
            addView(selectionView, selectionViewLp);
        }

        private Drawable createSelectionDrawable() {
            ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());
            shapeDrawable.getPaint().setColor(Theme.backgroundColor());
            shapeDrawable.setBounds(0, 0, checkSize, checkSize);
            shapeDrawable.setShape(new OvalShape());
            Drawable checkCircle = Drawables.get(R.drawable.baseline_check_circle_24);
            final int offset = Screen.dp(1);
            checkCircle.setBounds(offset, offset, checkSize - offset, checkSize - offset );
            checkCircle.setColorFilter(new PorterDuffColorFilter(Theme.getColor(R.id.theme_color_controlActive), PorterDuff.Mode.SRC_ATOP));
            return new LayerDrawable(new Drawable[]{ shapeDrawable, checkCircle});
        }
    }
}
