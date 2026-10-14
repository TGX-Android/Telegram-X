package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumRailLayout;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.ForumRailTransition;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.ForumUnreadCounter;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.RunnableLong;

/** A paged projection of the existing account/folder list, not a second chat cache. */
public final class ChatRailView extends FrameLayout implements ChatListListener, NotificationSettingsListener {
  private final Tdlib tdlib;
  private final TdlibChatListSlice slice;
  private final RecyclerView list;
  private final LinearLayoutManager layout;
  private final RailAdapter adapter = new RailAdapter();
  private final ArrayList<Long> chats = new ArrayList<>();
  private final RunnableLong openChat;
  private boolean destroyed, loading, initialized;
  private long selectedChat;
  private int restorePosition = -1, restoreOffset;
  private long restoreChatId;
  private boolean transitionPaused;
  private final ArrayList<Runnable> pendingUpdates = new ArrayList<>();

  public ChatRailView (Context context, Tdlib tdlib, TdApi.ChatList source, RunnableLong openChat) {
    super(context);
    this.tdlib = tdlib;
    this.openChat = openChat;
    slice = tdlib.chatList(source).slice(null);
    setBackgroundColor(Theme.fillingColor());
    list = new RecyclerView(context);
    layout = new LinearLayoutManager(context);
    list.setLayoutManager(layout);
    list.setAdapter(adapter);
    list.setItemAnimator(null);
    list.setClipToPadding(false);
    list.setVerticalScrollBarEnabled(false);
    list.setContentDescription(Lang.getString(R.string.ForumRailChats));
    addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    updateTheme();
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled (@NonNull RecyclerView view, int dx, int dy) { loadMore(); }
    });
    list.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (restorePosition >= 0) {
        // Insets/height are unknown when the rail is created. Do not consume its anchor yet.
        applyScrollPosition();
        loadMore();
      } else {
        list.setAlpha(1f); // Reveal only after the requested position has actually been laid out.
      }
    });
    tdlib.listeners().subscribeToSettingsUpdates(this);
    slice.initializeList(this, entries -> {
      ArrayList<Long> ids = new ArrayList<>(entries.size());
      for (TdlibChatListSlice.Entry entry : entries) ids.add(entry.chat.id);
      dispatch(() -> {
        int start = chats.size();
        for (Long id : ids) if (!chats.contains(id)) chats.add(id);
        adapter.notifyItemRangeInserted(start, chats.size() - start);
        applyScrollPosition();
        loadMore();
      });
    }, 30, () -> dispatch(() -> { initialized = true; applyScrollPosition(); loadMore(); }));
  }

  public void setInsets (int top, int bottom) {
    if (list.getPaddingTop() != top || list.getPaddingBottom() != bottom) {
      list.setPadding(0, top, 0, bottom);
    }
  }

  public void updateTheme () {
    setBackgroundColor(Theme.fillingColor());
    adapter.notifyItemRangeChanged(0, chats.size());
  }

  public void setSelectedChat (long chatId) {
    if (selectedChat == chatId) return;
    long old = selectedChat;
    selectedChat = chatId;
    int oldIndex = chats.indexOf(old), newIndex = chats.indexOf(chatId);
    if (oldIndex >= 0) adapter.notifyItemChanged(oldIndex);
    if (newIndex >= 0) adapter.notifyItemChanged(newIndex);
    // Deliberately do not insert or bringToTop a deep-linked chat outside this folder.
  }

  private void dispatch (Runnable action) {
    tdlib.ui().post(() -> {
      if (destroyed) return;
      if (transitionPaused) pendingUpdates.add(action); else action.run();
    });
  }

  public void setTransitionPaused (boolean paused) {
    transitionPaused = paused;
    if (!paused && !pendingUpdates.isEmpty()) {
      ArrayList<Runnable> updates = new ArrayList<>(pendingUpdates);
      pendingUpdates.clear();
      if (!destroyed) for (Runnable update : updates) update.run();
    }
  }

  public List<ForumRailTransition.Avatar> captureAvatars (ViewGroup host) {
    ArrayList<ForumRailTransition.Avatar> result = new ArrayList<>();
    if (!initialized || restorePosition >= 0 || list.isLayoutRequested()) return result;
    list.stopScroll();
    for (int i = 0; i < list.getChildCount(); i++) {
      RailRow row = (RailRow) list.getChildAt(i);
      if (row.chatId == 0 || row.getBottom() <= list.getPaddingTop() || row.getTop() >= list.getHeight() - list.getPaddingBottom()) continue;
      result.add(ForumRailTransition.Avatar.capture(row.chatId, host, row,
        row.getWidth() / 2f, row.getHeight() / 2f, row.avatarRadius(), row::drawContents, null));
    }
    return result;
  }

  private void loadMore () {
    if (destroyed || !initialized || loading || !slice.canLoad() || list.getHeight() <= list.getPaddingTop() + list.getPaddingBottom()) return;
    if (restorePosition < 0 && layout.findLastVisibleItemPosition() < chats.size() - 6) return;
    loading = true;
    int previousSize = chats.size();
    slice.loadMore(30, () -> dispatch(() -> {
      loading = false;
      applyScrollPosition();
      if (chats.size() > previousSize) loadMore(); // A failed/empty page must not spin on reconnect.
    }));
  }

  public int scrollPosition () { return restorePosition >= 0 ? restorePosition : Math.max(0, layout.findFirstVisibleItemPosition()); }
  public int scrollOffset () {
    if (restorePosition >= 0) return restoreOffset;
    View first = layout.findViewByPosition(layout.findFirstVisibleItemPosition());
    return first != null ? layout.getDecoratedTop(first) - list.getPaddingTop() : 0;
  }
  public void restoreScrollPosition (int position, int offset) {
    restoreChatId = 0;
    restorePosition = Math.max(0, position); restoreOffset = offset;
    list.setAlpha(0f);
    applyScrollPosition();
    loadMore();
  }
  public void restoreScrollAnchor (long chatId, int position, int offset) {
    restorePosition = Math.max(0, position);
    restoreOffset = offset;
    restoreChatId = chatId;
    list.setAlpha(0f);
    applyScrollPosition();
    loadMore();
  }
  private void applyScrollPosition () {
    if (restoreChatId != 0) {
      int position = chats.indexOf(restoreChatId);
      if (position >= 0) restorePosition = position;
      else if (!slice.isEndReached()) return;
      else restoreChatId = 0;
    }
    int viewport = list.getHeight() - list.getPaddingTop() - list.getPaddingBottom();
    if (ForumRailLayout.canRestoreScroll(chats.size(), restorePosition, restoreOffset, Screen.dp(64), viewport, slice.isEndReached())) {
      layout.scrollToPositionWithOffset(Math.min(restorePosition, chats.size() - 1), restoreOffset);
      restorePosition = -1;
      restoreChatId = 0;
    } else if (chats.isEmpty() && initialized && slice.isEndReached()) {
      restorePosition = -1;
      restoreChatId = 0;
      list.setAlpha(1f);
    }
  }

  private void changed (long chatId) {
    int index = chats.indexOf(chatId);
    if (index >= 0) adapter.notifyItemChanged(index);
  }

  @Override public void onChatAdded (TdlibChatList source, TdApi.Chat chat, int at, Tdlib.ChatChange change) {
    long id = chat.id;
    dispatch(() -> {
      if (chats.contains(id)) { changed(id); return; }
      int index = Math.min(at, chats.size());
      chats.add(index, id); adapter.notifyItemInserted(index);
    });
  }
  @Override public void onChatRemoved (TdlibChatList source, TdApi.Chat chat, int from, Tdlib.ChatChange change) {
    long id = chat.id;
    dispatch(() -> { int i = chats.indexOf(id); if (i >= 0) { chats.remove(i); adapter.notifyItemRemoved(i); } });
  }
  @Override public void onChatMoved (TdlibChatList source, TdApi.Chat chat, int from, int to, Tdlib.ChatChange change) {
    long id = chat.id;
    dispatch(() -> {
      int old = chats.indexOf(id);
      if (old >= 0) { chats.remove(old); int target = Math.min(to, chats.size()); chats.add(target, id); adapter.notifyItemMoved(old, target); }
    });
  }
  @Override public void onChatChanged (TdlibChatList source, TdApi.Chat chat, int at, Tdlib.ChatChange change) { long id = chat.id; dispatch(() -> changed(id)); }
  @Override public void onChatListItemChanged (TdlibChatList source, TdApi.Chat chat, int type) { long id = chat.id; dispatch(() -> changed(id)); }
  @Override public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) { dispatch(() -> changed(chatId)); }
  @Override public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) { dispatch(() -> adapter.notifyItemRangeChanged(0, chats.size())); }

  public void destroy () {
    if (destroyed) return;
    destroyed = true;
    pendingUpdates.clear();
    slice.performDestroy();
    tdlib.listeners().unsubscribeFromSettingsUpdates(this);
    list.setAdapter(null);
    for (RailRow row : adapter.rows) row.destroy();
    adapter.rows.clear();
    chats.clear();
  }

  private final class RailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    final List<RailRow> rows = new ArrayList<>();
    RailAdapter () { setHasStableIds(true); }
    @Override public long getItemId (int position) { return chats.get(position); }
    @Override public int getItemCount () { return chats.size(); }
    @Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int type) {
      RailRow row = new RailRow(parent.getContext());
      rows.add(row);
      row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(64)));
      return new RecyclerView.ViewHolder(row) { };
    }
    @Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) { ((RailRow) holder.itemView).bind(chats.get(position)); }
    @Override public void onViewRecycled (RecyclerView.ViewHolder holder) { ((RailRow) holder.itemView).clear(); }
  }

  private final class RailRow extends View {
    final AvatarReceiver avatar = new AvatarReceiver(this);
    final Counter counter = new Counter.Builder().textSize(ForumRailBadgeLayout.TEXT_SIZE_DP)
      .callback(this).outlineColor(ColorId.filling).build();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    long chatId;
    boolean attached;
    ForumUnreadCounter.Subscription unreadSubscription;
    RailRow (Context context) {
      super(context);
      avatar.setAvatarRadiusPropertyIds(PropertyId.AVATAR_RADIUS_CHAT_LIST, PropertyId.AVATAR_RADIUS_CHAT_LIST_FORUM);
      setFocusable(true);
      RippleSupport.setTransparentSelector(this);
      setOnClickListener(v -> { if (chatId != 0) openChat.runWithLong(chatId); });
      setOnLongClickListener(v -> { if (android.os.Build.VERSION.SDK_INT >= 26) { setTooltipText(tdlib.chatTitle(chatId)); return false; } return false; });
      avatar.detach();
    }
    void bind (long id) {
      if (chatId != id) stopUnreadUpdates();
      chatId = id;
      avatar.requestChat(tdlib, id, AvatarReceiver.Options.SHOW_ONLINE);
      setSelected(id == selectedChat);
      startUnreadUpdates();
      updateCounter();
    }
    void updateCounter () {
      TdApi.Chat chat = tdlib.chat(chatId);
      boolean muted = !tdlib.chatNotificationsEnabled(chatId);
      int unreadCount = chat != null ? tdlib.topics().unreadCount(chat) : 0;
      counter.setCount(unreadCount, muted, false);
      setContentDescription((chat != null ? chat.title : "") + (muted ? ", " + Lang.getString(R.string.ForumRailMuted) : "") +
        (unreadCount > 0 ? ", " + Lang.getString(tdlib.isForum(chatId) ? R.string.ForumRailUnreadTopics : R.string.ForumRailUnread, unreadCount) : ""));
      invalidate();
    }
    void startUnreadUpdates () {
      if (!tdlib.isForum(chatId)) { stopUnreadUpdates(); return; }
      if (attached && unreadSubscription == null && !tdlib.hasPasscode(chatId)) {
        final long id = chatId;
        unreadSubscription = tdlib.topics().observeUnread(id, count -> { if (attached && chatId == id) updateCounter(); });
      }
    }
    void stopUnreadUpdates () { if (unreadSubscription != null) { unreadSubscription.close(); unreadSubscription = null; } }
    void clear () { stopUnreadUpdates(); chatId = 0; avatar.clear(); }
    void destroy () { stopUnreadUpdates(); avatar.destroy(); }
    @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); attached = true; avatar.attach(); startUnreadUpdates(); updateCounter(); }
    @Override protected void onDetachedFromWindow () { attached = false; stopUnreadUpdates(); avatar.detach(); super.onDetachedFromWindow(); }
    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) { super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button"); info.setSelected(isSelected()); }
    @Override protected void onDraw (Canvas canvas) {
      drawContents(canvas, 1f);
    }
    int avatarRadius () { return Math.min(Screen.dp(23), getWidth() / 2 - Screen.dp(5)); }
    void drawContents (Canvas canvas, float decorations) {
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      int radius = avatarRadius();
      if (isSelected()) {
        paint.setColor(Theme.getColor(ColorId.iconActive));
        paint.setAlpha(Math.round(255f * decorations));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(2));
        canvas.drawCircle(cx, cy, radius + Screen.dp(3), paint); paint.setStyle(Paint.Style.FILL);
      }
      avatar.setBounds((int) cx - radius, (int) cy - radius, (int) cx + radius, (int) cy + radius);
      if (avatar.needPlaceholder()) avatar.drawPlaceholder(canvas);
      avatar.draw(canvas);
      ForumRailBadgeLayout.draw(canvas, counter, getWidth(), getHeight(), radius, Lang.rtl(), decorations);
    }
  }
}
