package org.thunderdog.challegram.component.user;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.MeasuredAdapterDelegate;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.List;

import me.vkryl.android.ViewUtils;

/**
 * Date: 20/10/2016
 * Author: default
 */

public class SimpleUsersAdapter extends RecyclerView.Adapter<SimpleUsersAdapter.SimpleUserHolder> implements View.OnClickListener, View.OnLongClickListener, MeasuredAdapterDelegate {
  public interface Callback {
    void onUserPicked (TGUser user);
    void onUserSelected (int selectedCount, TGUser user, boolean isSelected);
  }

  public static final int OPTION_CLICKABLE = 0x01;
  public static final int OPTION_SELECTABLE = 0x02;
  public static final int OPTION_COUNTER = 0x04;

  private final ViewController<?> context;
  private final Callback callback;
  private final boolean isClickable;
  private final boolean isSelectable;
  private final boolean needCounter;
  private final @Nullable ViewController<?> themeProvider;

  public SimpleUsersAdapter (ViewController<?> context, Callback callback, int options, @Nullable  ViewController<?> themeProvider) {
    this.context = context;
    this.callback = callback;
    this.isClickable = (options & OPTION_CLICKABLE) != 0;
    this.isSelectable = (options & OPTION_SELECTABLE) != 0;
    this.selected = isSelectable ? new SparseArrayCompat<>() : null;
    this.needCounter = (options & OPTION_COUNTER) != 0;
    this.themeProvider = themeProvider;
  }

  private List<TGUser> users;
  private final SparseArrayCompat<TGUser> selected;

  public void setUsers (List<TGUser> users) {
    int oldItemCount = getItemCount();
    this.users = users;
    U.notifyItemsReplaced(this, oldItemCount);
  }

  public SparseArrayCompat<TGUser> getSelectedUsers () {
    return selected;
  }

  public void clearSelectedUsers (LinearLayoutManager manager) {
    if (!isSelectable || users == null || users.isEmpty()) {
      return;
    }
    selected.clear();
    int first = manager.findFirstVisibleItemPosition();
    int last = manager.findLastVisibleItemPosition();
    for (int i = first; i <= last; i++) {
      if (getItemViewType(i) == SimpleUserHolder.VIEW_TYPE_USER) {
        View view = manager.findViewByPosition(i);
        if (view != null) {
          ((UserView) view).setChecked(false, true);
        }
      }
    }
    if (first > 0) {
      notifyItemRangeChanged(0, first);
    }
    if (last < users.size()) {
      notifyItemRangeChanged(last, users.size() - last);
    }
  }

  @Override
  public SimpleUserHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return SimpleUserHolder.create(context.context(), context.tdlib(), viewType, isClickable ? this : null, isSelectable ? this : null, themeProvider);
  }

  @Override
  public void onBindViewHolder (SimpleUserHolder holder, int position) {
    TGUser user = users.get(position);
    if (isSelectable) {
      holder.setUser(user, selected.get(user.getId()) != null);
    } else {
      holder.setUser(user);
    }
  }

  @Override
  public void onViewAttachedToWindow (SimpleUserHolder holder) {
    if (holder.getItemViewType() == SimpleUserHolder.VIEW_TYPE_USER) {
      ((UserView) holder.itemView).attachReceiver();
    }
  }

  @Override
  public void onViewDetachedFromWindow (SimpleUserHolder holder) {
    if (holder.getItemViewType() == SimpleUserHolder.VIEW_TYPE_USER) {
      ((UserView) holder.itemView).detachReceiver();
    }
  }

  @Override
  public int getItemCount () {
    return users != null && !users.isEmpty() ? users.size() + (needCounter ? 1 : 0) : 0;
  }

  @Override
  public int getItemViewType (int position) {
    return users != null && !users.isEmpty() && position == users.size() ? SimpleUserHolder.VIEW_TYPE_COUNTER : SimpleUserHolder.VIEW_TYPE_USER;
  }

  @Override
  public void onClick (View v) {
    if (!isSelectable) {
      if (callback != null) {
        callback.onUserPicked(((UserView) v).getUser());
      }
      return;
    }

    TGUser user = ((UserView) v).getUser();

    boolean inSelectMode = selected.size() > 0;
    boolean isSelected = selected.get(user.getId()) != null;

    if (isSelected) {
      selected.remove(user.getId());
    } else if (inSelectMode) {
      selected.put(user.getId(), user);
    }

    if (inSelectMode) {
      ((UserView) v).setChecked(!isSelected, true);
    }

    if (callback != null) {
      if (inSelectMode) {
        callback.onUserSelected(selected.size(), user, !isSelected);
      } else {
        callback.onUserPicked(user);
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    TGUser user = ((UserView) v).getUser();
    boolean isSelected = selected.get(user.getId()) != null;

    if (isSelected) {
      selected.remove(user.getId());
    } else {
      selected.put(user.getId(), user);
    }

    ((UserView) v).setChecked(!isSelected, true);
    if (callback != null) {
      callback.onUserSelected(selected.size(), user, !isSelected);
    }

    return true;
  }

  @Override
  public int measureHeight (int maxHeight) {
    if (getItemCount() == 0) {
      return 0;
    }
    int fullHeight = Screen.dp(UserView.HEIGHT) * users.size() + (needCounter ? Screen.dp(42f) : 0);
    return maxHeight < 0 ? fullHeight : Math.min(maxHeight, fullHeight);
  }

  @Override
  public int measureScrollTop (int position) {
    return Screen.dp(UserView.HEIGHT) * position;
  }

  static class SimpleUserHolder extends RecyclerView.ViewHolder {
    public static final int VIEW_TYPE_USER = 0;
    public static final int VIEW_TYPE_COUNTER = 1;

    public SimpleUserHolder (View itemView) {
      super(itemView);
    }

    public void setUser (TGUser user, boolean checked) {
      ((UserView) itemView).setUser(user);
      ((UserView) itemView).setChecked(checked, false);
    }

    public void setUser (TGUser user) {
      ((UserView) itemView).setUser(user);
    }

    public static SimpleUserHolder create (Context context, Tdlib tdlib, int viewType, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, @Nullable ViewController<?> themeProvider) {
      switch (viewType) {
        case VIEW_TYPE_USER: {

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

          return new SimpleUserHolder(userView);
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