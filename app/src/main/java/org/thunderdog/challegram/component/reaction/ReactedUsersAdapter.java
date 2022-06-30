package org.thunderdog.challegram.component.reaction;

import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.MeasuredAdapterDelegate;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.component.user.SimpleUsersAdapter;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.List;

import me.vkryl.android.ViewUtils;

public class ReactedUsersAdapter extends RecyclerView.Adapter<ReactedUsersAdapter.UserWithReactionHolder> implements MeasuredAdapterDelegate {
  public interface Callback {
    void onUserPicked (TGUser user);
    void onUserSelected (int selectedCount, TGUser user, boolean isSelected);
  }

  public static final int OPTION_CLICKABLE = 0x01;
  public static final int OPTION_SELECTABLE = 0x02;
  public static final int OPTION_COUNTER = 0x04;

  private final ViewController<?> context;
  private final SimpleUsersAdapter.Callback callback;
  private final boolean isClickable;
  private final boolean isSelectable;
  private final boolean needCounter;
  private final @Nullable ViewController<?> themeProvider;

  public ReactedUsersAdapter (ViewController<?> context, SimpleUsersAdapter.Callback callback, int options, @Nullable  ViewController<?> themeProvider) {
    this.context = context;
    this.callback = callback;
    this.isClickable = (options & OPTION_CLICKABLE) != 0;
    this.isSelectable = (options & OPTION_SELECTABLE) != 0;
    this.selected = isSelectable ? new LongSparseArray<>() : null;
    this.needCounter = (options & OPTION_COUNTER) != 0;
    this.themeProvider = themeProvider;
  }

  private List<Pair<TGUser, String>> usersWithReactions;
  private final LongSparseArray<TGUser> selected;

  public void setUsers (List<Pair<TGUser, String>> usersWithReactions) {
    int oldItemCount = getItemCount();
    this.usersWithReactions = usersWithReactions;
    U.notifyItemsReplaced(this, oldItemCount);
  }

  public LongSparseArray<TGUser> getSelectedUsers () {
    return selected;
  }

  @Override
  public ReactedUsersAdapter.UserWithReactionHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return ReactedUsersAdapter.UserWithReactionHolder.create(context.context(), context.tdlib(), viewType, null, null, themeProvider);
  }

  @Override
  public void onBindViewHolder (ReactedUsersAdapter.UserWithReactionHolder holder, int position) {
    Pair<TGUser, String> userWithReaction = usersWithReactions.get(position);
    if (isSelectable) {
      holder.setUser(userWithReaction.first, selected.get(userWithReaction.first.getUserId()) != null);
    } else {
      holder.setUser(userWithReaction.first);
    }
    context.tdlib().client().send(new TdApi.GetAnimatedEmoji(userWithReaction.second), result -> {
      if (result.getConstructor() == TdApi.AnimatedEmoji.CONSTRUCTOR) {
        TdApi.AnimatedEmoji emoji = (TdApi.AnimatedEmoji) result;
        TGStickerObj tgStickerObj = new TGStickerObj(context.tdlib(), emoji.sticker, userWithReaction.second, new TdApi.StickerTypeStatic());
        holder.reactionView.setSticker(tgStickerObj);
      }
    });
  }

  @Override
  public void onViewAttachedToWindow (ReactedUsersAdapter.UserWithReactionHolder holder) {
    if (holder.getItemViewType() == ReactedUsersAdapter.UserWithReactionHolder.VIEW_TYPE_USER) {
      holder.userView.attachReceiver();
    }
  }

  @Override
  public void onViewDetachedFromWindow (ReactedUsersAdapter.UserWithReactionHolder holder) {
    if (holder.getItemViewType() == ReactedUsersAdapter.UserWithReactionHolder.VIEW_TYPE_USER) {
      holder.userView.detachReceiver();
    }
  }

  @Override
  public int getItemCount () {
    return usersWithReactions != null && !usersWithReactions.isEmpty() ? usersWithReactions.size() + (needCounter ? 1 : 0) : 0;
  }

  @Override
  public int getItemViewType (int position) {
    return usersWithReactions != null && !usersWithReactions.isEmpty() && position == usersWithReactions.size() ? ReactedUsersAdapter.UserWithReactionHolder.VIEW_TYPE_COUNTER : ReactedUsersAdapter.UserWithReactionHolder.VIEW_TYPE_USER;
  }

  @Override
  public int measureHeight (int maxHeight) {
    if (getItemCount() == 0) {
      return 0;
    }
    int fullHeight = Screen.dp(UserView.HEIGHT) * usersWithReactions.size() + (needCounter ? Screen.dp(42f) : 0);
    return maxHeight < 0 ? fullHeight : Math.min(maxHeight, fullHeight);
  }

  @Override
  public int measureScrollTop (int position) {
    return Screen.dp(UserView.HEIGHT) * position;
  }

  static class UserWithReactionHolder extends RecyclerView.ViewHolder {
    public static final int VIEW_TYPE_USER = 0;
    public static final int VIEW_TYPE_COUNTER = 1;

    private final UserView userView;
    private final ReactionView reactionView;

    public UserWithReactionHolder (View itemView, UserView userView, ReactionView reactionView) {
      super(itemView);
      this.userView = userView;
      this.reactionView = reactionView;
    }

    public void setUser (TGUser user, boolean checked) {
      userView.setUser(user);
      userView.setChecked(checked, false);
    }

    public void setUser (TGUser user) {
      userView.setUser(user);
    }

    public static ReactedUsersAdapter.UserWithReactionHolder create (Context context, Tdlib tdlib, int viewType, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, @Nullable ViewController<?> themeProvider) {
      switch (viewType) {
        case VIEW_TYPE_USER: {

          LinearLayout wrapper = new LinearLayout(context);
          wrapper.setOrientation(LinearLayout.HORIZONTAL);
          wrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

          int offsetLeft = Screen.dp(18f);
          UserView userView;
          userView = new UserView(context, tdlib);
          userView.setOffsetLeft(offsetLeft);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(userView);
          }
          if (onClickListener != null || onLongClickListener != null) {
            userView.setOnClickListener(onClickListener);
            userView.setOnLongClickListener(onLongClickListener);
            ViewUtils.setBackground(userView, Theme.fillingSelector(R.id.theme_color_chatBackground));
            Views.setClickable(userView);
          }
          userView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          wrapper.addView(userView);

          ReactionView reactionView = new ReactionView(context);
          reactionView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f)));
          wrapper.addView(reactionView);

          return new ReactedUsersAdapter.UserWithReactionHolder(wrapper, userView, reactionView);
        }
        case VIEW_TYPE_COUNTER: {
          // TODO
          return null;
        }
        default: {
          throw new IllegalArgumentException("viewType is unknown");
        }
      }
    }
  }
}
