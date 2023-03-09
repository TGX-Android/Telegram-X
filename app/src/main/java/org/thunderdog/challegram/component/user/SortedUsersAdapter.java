/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 26/12/2016
 */
package org.thunderdog.challegram.component.user;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.UserContext;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.UserProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;

public class SortedUsersAdapter extends RecyclerView.Adapter<SortedUsersAdapter.ViewHolder> implements Comparator<SortedUsersAdapter.UserItem>, TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener {
  private static @Nullable UpdateHandler __handler; // = new UpdateHandler();

  private static UpdateHandler getHandler () {
    if (__handler == null) {
      synchronized (UpdateHandler.class) {
        if (__handler == null) {
          __handler = new UpdateHandler();
        }
      }
    }
    return __handler;
  }

  public static final int MODE_HORIZONTAL = 0;
  public static final int MODE_VERTICAL = 0;

  private final ViewController<?> context;
  private final int mode;
  private final @Nullable View.OnClickListener onClickListener;
  private final @Nullable View.OnLongClickListener onLongClickListener;
  private final ArrayList<UserItem> users;

  private @Nullable String searchQuery;
  private ArrayList<UserItem> searchItems;

  public SortedUsersAdapter (ViewController<?> context, int mode, @Nullable View.OnClickListener onClickListener, @Nullable View.OnLongClickListener onLongClickListener) {
    this.context = context;
    this.mode = mode;
    this.onClickListener = onClickListener;
    this.onLongClickListener = onLongClickListener;
    this.users = new ArrayList<>();
  }

  public void search (String query) {
    if (StringUtils.equalsOrBothEmpty(searchQuery, query)) {
      return;
    }
  }

  public void destroy () {
    clear(true);
  }

  @Override
  public SortedUsersAdapter.ViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return ViewHolder.create(context.context(), onClickListener, onLongClickListener, viewType);
  }

  @Override
  public void onViewAttachedToWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case ViewHolder.TYPE_HORIZONTAL: {
        ((HorizontalUserView) holder.itemView).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (ViewHolder holder) {
    switch (holder.getItemViewType()) {
      case ViewHolder.TYPE_HORIZONTAL: {
        ((HorizontalUserView) holder.itemView).detach();
        break;
      }
    }
  }

  @Override
  public void onBindViewHolder (SortedUsersAdapter.ViewHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case ViewHolder.TYPE_HORIZONTAL: {
        UserItem item = users.get(position);
        ((HorizontalUserView) holder.itemView).setUser(item);
        break;
      }
    }
  }

  @Override
  public int getItemCount () {
    return users.size(); // users.isEmpty() ? 1 : 0;
  }

  @Override
  public int getItemViewType (int position) {
    return users.isEmpty() ? ViewHolder.TYPE_EMPTY : mode == MODE_HORIZONTAL ? ViewHolder.TYPE_HORIZONTAL : ViewHolder.TYPE_NORMAL;
  }

  @Override
  public int compare (UserItem o1, UserItem o2) {
    UserContext u1 = o1.getContext();
    UserContext u2 = o2.getContext();

    TdApi.User left = u1.getUser();
    TdApi.User right = u2.getUser();

    if (left == null && right == null) {
      return 0;
    }
    if (left == null) {
      return 1;
    }
    if (right == null) {
      return -1;
    }

    long myUserId = context.tdlib().myUserId();
    int x, y;
    if (left.id == myUserId) {
      x = Integer.MAX_VALUE;
      y = TD.getLastSeen(right);
    } else if (right.id == myUserId) {
      x = TD.getLastSeen(left);
      y = Integer.MAX_VALUE;
    } else {
      x = TD.getLastSeen(left);
      y = TD.getLastSeen(right);
    }

    if (x == y) {
      int j1 = o1.getJoinDate();
      int j2 = o2.getJoinDate();

      return j1 > j2 ? -1 : j1 < j2 ? 1 : left.id > right.id ? -1 : left.id < right.id ? 1 : 0;
    }

    return x > y ? -1 : 1;
  }

  private void addUser (UserItem userItem, boolean notify) {
    int index = Collections.binarySearch(users, userItem, this);
    if (index < 0) {
      int newIndex = (-index) - 1;
      users.add(newIndex, userItem);
      if (notify) {
        notifyItemInserted(newIndex);
      }
    }
  }

  private void clear (boolean notify) {
    if (!users.isEmpty()) {
      LongList ids = new LongList(users.size());
      for (UserItem item : users) {
        ids.append(item.getId());
      }
      context.tdlib().cache().unsubscribeFromUserUpdates(ids.get(), this);
      int oldItemCount = getItemCount();
      users.clear();
      if (notify) {
        U.replaceItems(this, oldItemCount);
      }
    }
  }

  public void resetWithMembers (TdApi.ChatMember[] members) {
    if (users.isEmpty()) {
      setMembers(members);
      return;
    }
    if (members.length == 0) {
      clear(true);
      return;
    }

    LongSparseArray<TdApi.ChatMember> sparseMembers = new LongSparseArray<>(members.length);

    for (TdApi.ChatMember member : members) {
      sparseMembers.put(((TdApi.MessageSenderUser) member.memberId).userId, member);
    }

    int prevItemWidth = calculateItemWidth();

    int changedCount = 0;
    final int size = users.size();
    for (int i = size - 1; i >= 0; i--) {
      UserItem item = users.get(i);
      int keyIndex = sparseMembers.indexOfKey(item.getId());
      if (keyIndex < 0) { // Removed from list
        users.remove(i);
        notifyItemRemoved(i);
        changedCount--;
      } else {
        sparseMembers.removeAt(keyIndex);
      }
    }

    for (int i = 0; i < sparseMembers.size(); i++) { // Added members
      TdApi.ChatMember member = sparseMembers.valueAt(i);
      UserItem item = new UserItem(context.tdlib(), member);
      addUser(item, true);
      changedCount++;
    }

    if (changedCount != 0 && calculateItemWidth() != prevItemWidth) {
      notifyItemRangeChanged(0, getItemCount());
    }
  }

  private int calculateItemWidth () {
    int padding = Screen.dp(17f);
    int imageSize = Screen.dp(50f);
    int minWidth = padding + padding + imageSize;
    int itemCount = getItemCount();
    int itemWidth;
    if (itemCount != 0 && !recyclerViews.isEmpty()) {
      int parentWidth = recyclerViews.get(0).getMeasuredWidth();
      itemWidth = Math.max(minWidth, parentWidth / itemCount);
      if (itemWidth > minWidth) {
        int diff = itemWidth - minWidth;
        itemWidth = Math.max(minWidth, (parentWidth - diff) / itemCount);
      }
    } else {
      itemWidth = minWidth;
    }
    return itemWidth;
  }

  public void setMembers (TdApi.ChatMember[] members) {
    if (users.isEmpty() && members.length == 0) {
      return;
    }
    int oldItemCount = getItemCount();
    clear(false);
    users.ensureCapacity(members.length);
    LongList ids = new LongList(members.length);
    for (TdApi.ChatMember member : members) {
      addUser(new UserItem(context.tdlib(), member), false);
      ids.append(((TdApi.MessageSenderUser) member.memberId).userId);
    }
    context.tdlib().cache().subscribeToUserUpdates(ids.get(), this);
    U.replaceItems(this, oldItemCount);
  }

  private int indexOfUser (long userId) {
    int i = 0;
    for (UserItem item : users) {
      if (item.getId() == userId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public void updateChatMember (TdApi.ChatMember member) {
    if (member.memberId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
      int i = indexOfUser(((TdApi.MessageSenderUser) member.memberId).userId);
      if (i != -1) {
        users.set(i, new UserItem(context.tdlib(), member));
        notifyItemChanged(i);
      }
    }
  }

  public @Nullable TdApi.ChatMember getChatMember (TdApi.MessageSender senderId) {
    if (senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
      return getChatMember(((TdApi.MessageSenderUser) senderId).userId);
    } else {
      return null;
    }
  }

  public @Nullable TdApi.ChatMember getChatMember (long userId) {
    int i = indexOfUser(userId);
    return i != -1 ? users.get(i).member : null;
  }

  private final ArrayList<RecyclerView> recyclerViews = new ArrayList<>();

  @Override
  public void onAttachedToRecyclerView (@NonNull RecyclerView recyclerView) {
    recyclerViews.add(recyclerView);
  }

  @Override
  public void onDetachedFromRecyclerView (@NonNull RecyclerView recyclerView) {
    recyclerViews.remove(recyclerView);
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    UpdateHandler handler = getHandler();
    handler.sendMessage(Message.obtain(handler, UpdateHandler.USER_UPDATED, new Object[] {this, user}));
  }

  private void updateUserInternal (TdApi.User user) {
    int i = indexOfUser(user.id);
    if (i == -1) {
      return;
    }
    UserItem item = users.get(i);
    item.getContext().set(user);
    boolean needNotify = false;
    for (RecyclerView recyclerView : recyclerViews) {
      View view = recyclerView.getLayoutManager().findViewByPosition(i);
      if (view != null) {
        if (view instanceof HorizontalUserView) {
          ((HorizontalUserView) view).setUser(item);
          view.invalidate();
          continue;
        }
      }
      needNotify = true;
    }
    if (needNotify) {
      notifyItemChanged(i);
    }
  }

  private void updateStatusInternal (int index, long userId, boolean isOnline) {
    boolean needNotify = false;
    for (RecyclerView recyclerView : recyclerViews) {
      View view = recyclerView.getLayoutManager().findViewByPosition(index);
      if (view != null) {
        if (view instanceof HorizontalUserView) {
          if (((HorizontalUserView) view).getUserId() == userId) {
            ((HorizontalUserView) view).setIsOnline(isOnline);
            continue;
          }
        }
      }
      needNotify = true;
    }
    if (needNotify) {
      notifyItemChanged(index);
    }
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return false;
  }

  @UiThread
  @Override
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    if (uiOnly) {
      return;
    }
    int oldIndex = indexOfUser(userId);
    if (oldIndex != -1) {
      UserItem item = users.get(oldIndex);
      if (!item.getContext().setStatus(status)) {
        return;
      }

      updateStatusInternal(oldIndex, item.getId(), context.tdlib().cache().isOnline(item.getId()));

      users.remove(oldIndex);
      int bestIndex = Collections.binarySearch(users, item, this);
      if (bestIndex >= 0) { // unlikely
        users.add(oldIndex, item);
      } else {
        int newIndex = (-bestIndex) - 1;
        users.add(oldIndex, item);
        if (newIndex != oldIndex) {
          notifyItemMoved(oldIndex, newIndex);
        }
      }
    }
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_HORIZONTAL = 1;
    public static final int TYPE_GRID = 2;
    public static final int TYPE_EMPTY = 3;

    public ViewHolder (View itemView) {
      super(itemView);
    }

    public static ViewHolder create (Context context, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, int viewType) {
      switch (viewType) {
        case TYPE_HORIZONTAL: {
          HorizontalUserView userView = new HorizontalUserView(context);
          Views.setClickable(userView);
          RippleSupport.setTransparentSelector(userView);
          userView.setOnClickListener(onClickListener);
          userView.setOnLongClickListener(onLongClickListener);
          userView.setId(R.id.user);
          return new ViewHolder(userView);
        }
        case TYPE_EMPTY: {
          TextView textView = Views.newTextView(context, 16f, Theme.textDecentColor(), Gravity.CENTER, 0);
          textView.setText(Lang.getString(R.string.Nobody));
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new ViewHolder(textView);
        }
      }
      throw new IllegalArgumentException("viewType == " + viewType);
    }
  }

  public static class UserItem implements UserProvider {
    private final UserContext context;
    private final TdApi.ChatMember member;

    public UserItem (Tdlib tdlib, TdApi.ChatMember member) {
      this.context = new UserContext(tdlib, ((TdApi.MessageSenderUser) member.memberId).userId);
      this.member = member;
    }

    public long getId () {
      return context.getId();
    }

    public UserContext getContext () {
      return context;
    }

    public int getJoinDate () {
      return member != null ? member.joinedChatDate : 0;
    }

    public ImageFile getImageFile () {
      return context.getImageFile();
    }

    @Override
    public TdApi.User getTdUser () {
      return context.getUser();
    }
  }

  public static class HorizontalUserView extends View implements FactorAnimator.Target {
    private final ImageReceiver receiver;
    private int minWidth;

    public HorizontalUserView (Context context) {
      super(context);
      int padding = Screen.dp(17f);
      int imageSize = Screen.dp(50f);
      minWidth = padding + padding + imageSize;
      receiver = new ImageReceiver(this, imageSize / 2);
      setMinimumWidth(minWidth);
      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(95f)));
    }

    private int getDesiredWidth () {
      if (getParent() != null) {
        int itemCount = ((RecyclerView) getParent()).getAdapter().getItemCount();
        int itemWidth;
        if (itemCount != 0) {
          int parentWidth = ((RecyclerView) getParent()).getMeasuredWidth();
          itemWidth = Math.max(minWidth, parentWidth / itemCount);
          if (itemWidth > minWidth) {
            int diff = itemWidth - minWidth;
            itemWidth = Math.max(minWidth, (parentWidth - diff) / itemCount);
          }
        } else {
          itemWidth = minWidth;
        }
        return itemWidth;
      }
      return minWidth;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(MeasureSpec.makeMeasureSpec(getDesiredWidth(), MeasureSpec.EXACTLY),
        getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));

      int centerX = getMeasuredWidth() / 2;
      int paddingTop = Screen.dp(11f);
      int imageSize = Screen.dp(50f);
      receiver.setBounds(centerX - imageSize / 2, paddingTop, centerX + imageSize / 2, paddingTop + imageSize);
    }

    public long getUserId () {
      return user != null ? user.getId() : 0;
    }

    public void attach () {
      receiver.attach();
    }

    public void detach () {
      receiver.detach();
    }

    private UserItem user;

    private String firstName;
    private int firstNameWidth;
    private String trimmedName;

    public void setUser (UserItem user) {
      this.user = user;
      if (user != null) {
        this.receiver.requestFile(user.getImageFile());
      } else {
        this.receiver.requestFile(null);
      }
      if (user != null) {
        user.getContext().measureTexts(17f, null);
        setTag(user.getContext().getUser());
      } else {
        setTag(null);
      }
      setFirstName(user != null ? user.getContext().getFirstName() : null);
      setIsOnline(user != null && user.context.tdlib().cache().isOnline(user.getId()), false);
      if (getMeasuredWidth() != 0 && getMeasuredWidth() != getDesiredWidth()) {
        if (getParent() != null && ((RecyclerView) getParent()).isComputingLayout()) {
          post(() -> requestLayout());
        } else {
          requestLayout();
        }
      }
    }

    private void setFirstName (String firstName) {
      if (this.firstName == null && firstName == null) {
        return;
      }
      if (this.firstName == null || !this.firstName.equals(firstName)) {
        this.firstName = firstName;
        if (firstName != null) {
          this.trimmedName = TextUtils.ellipsize(firstName, Paints.getSmallTitlePaint(), Screen.dp(50f) + Screen.dp(26f), TextUtils.TruncateAt.END).toString();
          this.firstNameWidth = (int) U.measureText(trimmedName, Paints.getSmallTitlePaint());
        } else {
          this.firstName = null;
          this.firstNameWidth = 0;
        }
      }
    }

    private float onlineFactor;

    private void setOnlineFactor (float factor) {
      if (this.onlineFactor != factor) {
        this.onlineFactor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case 0: {
          setOnlineFactor(factor);
          break;
        }
      }
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

    }

    private FactorAnimator onlineAnimator;

    private void forceOnlineFactor (boolean isOnline) {
      if (onlineAnimator != null) {
        onlineAnimator.forceFactor(isOnline ? 1f : 0f);
      }
      setOnlineFactor(isOnline ? 1f : 0f);
    }

    private void animateOnlineFactor (boolean isOnline) {
      if (onlineAnimator == null) {
        onlineAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      onlineAnimator.cancel();
      if (isOnline && onlineFactor == 0f) {
        onlineAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
        onlineAnimator.setDuration(210l);
      } else {
        onlineAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        onlineAnimator.setDuration(100l);
      }
      onlineAnimator.animateTo(isOnline ? 1f : 0f);
    }

    private boolean isOnline;

    public void setIsOnline (boolean isOnline) {
      setIsOnline(isOnline, true);
    }

    private void setIsOnline (boolean isOnline, boolean animated) {
      if (this.isOnline != isOnline) {
        this.isOnline = isOnline;
        if (animated) {
          animateOnlineFactor(isOnline);
        } else {
          forceOnlineFactor(isOnline);
        }
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (user == null) {
        return;
      }
      if (user.getContext().hasPhoto()) {
        if (receiver.needPlaceholder()) {
          receiver.drawPlaceholderRounded(c, Screen.dp(50f) / 2);
        }
        receiver.draw(c);
      } else {
        user.getContext().drawPlaceholder(c, Screen.dp(50f) / 2, receiver.getLeft(), receiver.getTop(), 17f);
      }

      if (trimmedName != null) {
        c.drawText(trimmedName, getMeasuredWidth() / 2 - firstNameWidth / 2, Screen.dp(82f), Paints.getSmallTitlePaint());
      }

      DrawAlgorithms.drawOnline(c, receiver, onlineFactor);
    }
  }

  // Handler

  private static class UpdateHandler extends Handler {
    public static final int USER_UPDATED = 0;

    public UpdateHandler () {
      super(Looper.getMainLooper());
    }

    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case USER_UPDATED: {
          Object[] args = (Object[]) msg.obj;
          ((SortedUsersAdapter) args[0]).updateUserInternal((TdApi.User) args[1]);
          args[0] = null;
          args[1] = null;
          break;
        }
      }
      super.handleMessage(msg);
    }
  }
}
