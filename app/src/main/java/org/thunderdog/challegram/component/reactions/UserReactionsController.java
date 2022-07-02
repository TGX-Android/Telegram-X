package org.thunderdog.challegram.component.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class UserReactionsController extends ViewController<UserReactionsController.Args> implements FactorAnimator.Target, BottomSheet.ScrollableDelegate {

    public static final int EXPAND_ANIMATOR = 1;
    public static final int COLLAPSE_ANIMATOR = 2;

    public interface UserReactionsCallback {
        void onUserSelected(TGUser user);
    }

    public static class Args {
        private final TGReaction[] reactions;
        private final boolean displayTotal;

        public Args(TGReaction[] reactions) {
            this.reactions = reactions;
            displayTotal = true;
        }

        public Args(TGReaction reaction) {
            this.reactions = new TGReaction[] { reaction };
            this.displayTotal = false;
        }
    }

    private UserReactionsCallback callback;
    private UserReactionsRecyclerView recyclerView;


    public UserReactionsController(@NonNull Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    public void setCallback(UserReactionsCallback callback) {
        this.callback = callback;
    }


    @Override
    protected View onCreateView(Context context) {
        this.recyclerView = createUsersRecycler(context);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setBackgroundColor(Theme.backgroundColor());
        runOnBackgroundThread(() -> {
            if (getArguments() != null) {
                List<BaseAdapterItem> items = fetchItems(getArguments().reactions);
                runOnUiThread(() -> {
                    recyclerView.submitList(items);
                });
            }
        });
        return recyclerView;
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {}

    @Override
    public int getId() {
        return R.id.controller_user_reactions;
    }

    @Override
    public void onThemePropertyChanged(int themeId, int propertyId, float value, boolean isDefault) {
        super.onThemePropertyChanged(themeId, propertyId, value, isDefault);
    }

    @Override
    public void dispatchScrollableTouchEvent(MotionEvent e) {
        if (recyclerView == null) return;
        recyclerView.dispatchTouchEvent(e);
    }

    @Override
    public int getScrollableScrollY() {
        return recyclerView != null ? recyclerView.getScrollY() : 0;
    }

    @Override
    public void forceScrollToTop() {
        if (recyclerView != null) recyclerView.scrollTo(0, 0);
    }

    @Override
    public boolean isInsideScrollable(float x, float y) {
        if (recyclerView == null) return false;
        return y >= recyclerView.getTranslationY() && y <= recyclerView.getTranslationY() + recyclerView.getMeasuredHeight();
    }


    private UserReactionsRecyclerView createUsersRecycler(Context context) {
        UserReactionsRecyclerView recyclerView = new UserReactionsRecyclerView(context, this);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        recyclerView.setLayoutParams(layoutParams);
        return recyclerView;
    }

    private List<BaseAdapterItem> fetchItems(TGReaction[] userReactions) {
        List<BaseAdapterItem> items = new ArrayList<>(userReactions.length);
        int totalCount = 0;
        boolean shouldDisplayTotal = getArguments() != null && getArguments().displayTotal;
        for (final TGReaction reaction : userReactions) {
            TdApi.MessageSender[] senders = reaction.getReactionSenders();
            totalCount += reaction.getTotalCount();
            for (TdApi.MessageSender sender : senders) {
                if (sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
                    long userId = ((TdApi.MessageSenderUser) sender).userId;
                    TdApi.User user = tdlib().cache().user(userId);
                    if (user != null) {
                        UserReaction userReaction = new UserReaction(new TGUser(tdlib, user), shouldDisplayTotal ? reaction : null);
                        items.add(userReaction);
                    } else {
                        tdlib().client().send(new TdApi.GetUser(userId), object -> {
                            if (object.getConstructor() == TdApi.User.CONSTRUCTOR) {
                                UserReaction userReaction = new UserReaction(new TGUser(tdlib, (TdApi.User) object), shouldDisplayTotal ? reaction : null);
                                items.add(userReaction);
                            }
                        });
                    }
                }
            }
        }
        if (shouldDisplayTotal) items.add(new CounterItem(Lang.plural(R.string.xReaction, totalCount)));
        return items;
    }

    private static class UserReactionsRecyclerView extends RecyclerView {

        private final UserReactionAdapter reactionAdapter;

        public UserReactionsRecyclerView(@NonNull Context context, UserReactionsController parent) {
            super(context);
            reactionAdapter = new UserReactionAdapter(parent);
            DividerDecoration dividerItemDecoration = new DividerDecoration(Screen.dp(61), Theme.backgroundColor());
            addItemDecoration(dividerItemDecoration);
            this.setAdapter(reactionAdapter);
            setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        }

        void submitList(List<BaseAdapterItem> baseAdapterItems) {
            reactionAdapter.submitList(baseAdapterItems);
        }
    }

    private static class DividerDecoration extends RecyclerView.ItemDecoration {

        private final int startOffset;
        private final Paint paint;
        private final int lineSize;

        public DividerDecoration(int startOffset, @ColorInt int color) {
            this.startOffset = startOffset;
            this.paint = new Paint();
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setColor(color);
            lineSize = Screen.dp(1);
            this.paint.setStrokeWidth(lineSize);
        }

        @Override
        public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.onDrawOver(c, parent, state);
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                int left = child.getLeft() + startOffset;
                c.drawLine(left, child.getBottom() - lineSize, child.getRight(), child.getBottom(), paint);
            }
        }
    }

    private static class UserReactionAdapter extends ListAdapter<BaseAdapterItem, BaseAdapterHolder> {

        private static final int VIEW_TYPE_USER = 0;
        private static final int VIEW_TYPE_COUNTER = 1;
        private final UserReactionsController parent;

        protected UserReactionAdapter(UserReactionsController parent) {
            super(new DiffUtilCallback());
            this.parent = parent;
        }

        @Override
        public int getItemViewType(int position) {
            if (getItem(position) instanceof CounterItem) {
                return VIEW_TYPE_COUNTER;
            }
            return VIEW_TYPE_USER;
        }

        @NonNull
        @Override
        public BaseAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_COUNTER) {
                return createCounterHolder(parent.getContext());
            }
            return createReactionHolder(parent.getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull BaseAdapterHolder holder, int position) {
            holder.bind(getItem(position));
        }

        private UserReactionHolder createReactionHolder(Context context) {
            UserReactionLayout userReactionLayout = new UserReactionLayout(context, parent.tdlib());
            userReactionLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ViewUtils.setBackground(userReactionLayout, Theme.fillingSelector(R.id.theme_color_chatBackground));
            Views.setClickable(userReactionLayout);
            parent.addThemeInvalidateListener(userReactionLayout);
            return new UserReactionHolder(userReactionLayout, parent.callback);
        }

        private CounterHolder createCounterHolder(Context context) {
            TextView textView = new TextView(context);
            textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setTextColor(Theme.textDecentColor());
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(0, Screen.dp(16), 0, Screen.dp(16));
            parent.addThemeInvalidateListener(textView);
            return new CounterHolder(textView);
        }
    }

    private static abstract class BaseAdapterHolder extends RecyclerView.ViewHolder {

        BaseAdapterHolder(@NonNull View itemView) {
            super(itemView);
        }

        public abstract void bind(BaseAdapterItem item);
    }

    private static class UserReactionHolder extends BaseAdapterHolder {
        private final UserReactionsCallback callback;

        public UserReactionHolder(@NonNull UserReactionLayout itemView, UserReactionsCallback callback) {
            super(itemView);
            this.callback = callback;
        }

        @Override
        public void bind(BaseAdapterItem item) {
            if (item instanceof UserReaction) {
                UserReaction userReaction = (UserReaction) item;
                ((UserReactionLayout) itemView).setUserReaction(userReaction);
                itemView.setOnClickListener((v) -> callback.onUserSelected(userReaction.user));
            }
        }
    }

    private static class CounterHolder extends BaseAdapterHolder {

        public CounterHolder(@NonNull View itemView) {
            super(itemView);
        }

        @Override
        public void bind(BaseAdapterItem item) {
            if (item instanceof CounterItem) {
                CounterItem counterItem = (CounterItem) item;
                ((TextView) itemView).setText(counterItem.text);
            }
        }
    }

    private static class BaseAdapterItem {
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return super.equals(obj);
        }
    }

    private static class UserReaction extends BaseAdapterItem {
        private final TGUser user;
        private final TGReaction reaction;

        public UserReaction(TGUser user, TGReaction reaction) {
            this.user = user;
            this.reaction = reaction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserReaction that = (UserReaction) o;
            return Objects.equals(user, that.user) && Objects.equals(reaction, that.reaction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, reaction);
        }

        @NonNull
        @Override
        public String toString() {
            return "UserReaction{" +
                    "user=" + user.getUser() +
                    ", reaction=" + (reaction != null ? reaction.getReactionString() : "") +
                    '}';
        }
    }

    private static class CounterItem extends BaseAdapterItem {
        private final String text;

        public CounterItem(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CounterItem that = (CounterItem) o;
            return Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text);
        }
    }

    private static class DiffUtilCallback extends DiffUtil.ItemCallback<BaseAdapterItem> {

        @Override
        public boolean areItemsTheSame(@NonNull BaseAdapterItem oldItem, @NonNull BaseAdapterItem newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull BaseAdapterItem oldItem, @NonNull BaseAdapterItem newItem) {
            return oldItem.equals(newItem);
        }
    }

    private static class UserReactionLayout extends FrameLayoutFix {

        private final UserView userView;
        private final ReactionView reactionView;

        public UserReactionLayout(Context context, Tdlib tdlib) {
            super(context);
            userView = new UserView(getContext(), tdlib);
            userView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            userView.setOffsetLeft(Screen.dp(8));
            userView.setClickable(false);
            userView.setFocusable(false);
            userView.setBackgroundColor(Theme.getColor(R.id.theme_color_filling));
            userView.setFocusableInTouchMode(false);
            addView(userView);

            reactionView = new ReactionView(context, tdlib);
            FrameLayout.LayoutParams layoutParams = new LayoutParams(Screen.dp(24), Screen.dp(24));
            layoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            layoutParams.setMargins(0, 0, Screen.dp(16), 0);
            reactionView.setLayoutParams(layoutParams);
            addView(reactionView);
        }

        public void setUserReaction(UserReaction userReaction) {
            userView.setUser(userReaction.user);
            if (userReaction.reaction != null) reactionView.setReaction(userReaction.reaction);
        }
    }
}
