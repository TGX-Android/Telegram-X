package org.thunderdog.challegram.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumPresentation;
import org.thunderdog.challegram.data.ForumNavigation;
import org.thunderdog.challegram.data.ForumTabsState;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A host-fed projection, not a topic-list owner. All public mutations run on the UI thread.
 * Place this view only in the history area: the header/composer remain full-width siblings.
 * The owner supplies effective snapshots and calls destroy() when discarding the widget.
 */
@UiThread
public final class ForumTopicsTabsView extends ViewGroup implements ThemeInvalidateListener, Lang.Listener {
  public interface Listener {
    void onSelectTopic (int topicId);
    void onNewTopic ();
    void onCyclePlacement ();
    void onLoadMore ();
    void onRetry ();
  }

  private static final int NEW_TOPIC = -1;
  private static final Object REBIND = new Object();
  private final ViewController<?> owner;
  private final Listener listener;
  private final RecyclerView list;
  private final LinearLayoutManager layout;
  private final TabsAdapter adapter = new TabsAdapter();
  private final PlacementButton placementButton;
  private final StateButton stateButton;
  private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Anchor[] orientationAnchors = new Anchor[2];
  private final Runnable settleTask = this::settle;
  private ForumTopicsTabsLayout.Parts parts;
  private @Nullable Anchor pendingAnchor;
  private int placement = ForumTabsState.TOP, nextHorizontal = ForumTabsState.BOTTOM;
  private int selectedId = Integer.MIN_VALUE;
  private int serverVisibleCount, sourceCount, sourceTail;
  private int requestedCount = -1, requestedTail;
  private boolean loading, failed, endReached = true, destroyed, attached, revealSelection, retryRequested, hasSnapshot;

  public ForumTopicsTabsView (ViewController<?> owner, Listener listener) {
    // A constructor-free, null-TDLib presentation owner can use the already supplied app
    // context. This is also useful for isolated previews; it never acquires an account.
    super(owner.context() != null ? owner.context() : UI.getAppContext());
    this.owner = owner;
    this.listener = listener;
    setWillNotDraw(false);
    setBackgroundColor(Theme.fillingColor());
    list = new RecyclerView(getContext());
    layout = new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, Lang.rtl());
    // Reverse explicitly, including API 16. Do not also let LinearLayoutManager reverse for RTL.
    if (Build.VERSION.SDK_INT >= 17) list.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    list.setLayoutManager(layout);
    list.setItemAnimator(null);
    list.setHorizontalScrollBarEnabled(false);
    list.setVerticalScrollBarEnabled(false);
    list.setClipToPadding(false);
    list.setAdapter(adapter);
    list.setContentDescription(string(R.string.ForumTabsLabel));
    addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    stateButton = new StateButton(getContext());
    stateButton.setVisibility(GONE);
    addView(stateButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    placementButton = new PlacementButton(getContext());
    addView(placementButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    updatePlacementDescription();
    list.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled (@NonNull RecyclerView recycler, int dx, int dy) { scheduleSettle(); }
      @Override public void onScrollStateChanged (@NonNull RecyclerView recycler, int state) {
        if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
          // An explicit drag wins over a deferred off-page selection/anchor restoration.
          revealSelection = false;
          pendingAnchor = null;
        }
      }
    });
    list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> scheduleSettle());
    owner.addThemeInvalidateListener(this);
    Lang.addLanguageListener(this);
  }

  public int recommendedHorizontalHeight () {
    return ForumTopicsTabsLayout.horizontalHeight(getResources().getDisplayMetrics().density, fontScale());
  }

  public int recommendedSideWidth () {
    return ForumTopicsTabsLayout.sideWidth(getResources().getDisplayMetrics().density, fontScale());
  }

  /** Controller-replacement state: only placement/identities/offsets, never topic data or media. */
  public @NonNull Bundle saveState () {
    Bundle state = new Bundle();
    state.putInt("version", 1);
    state.putInt("placement", placement);
    state.putInt("nextHorizontal", nextHorizontal);
    state.putInt("selected", selectedId);
    Anchor current = captureAnchor();
    putAnchor(state, "current", current);
    putAnchor(state, "horizontal", side() ? orientationAnchors[0] : current);
    putAnchor(state, "side", side() ? current : orientationAnchors[1]);
    return state;
  }

  /** May be called before or after the first setTopics; selection is revealed once afterwards. */
  public void restoreState (@Nullable Bundle state) {
    if (destroyed || state == null || state.getInt("version", 0) != 1) return;
    int savedPlacement = state.getInt("placement", ForumTabsState.TOP);
    if (savedPlacement != ForumTabsState.TOP && savedPlacement != ForumTabsState.START && savedPlacement != ForumTabsState.BOTTOM) return;
    setPlacement(savedPlacement, state.getInt("nextHorizontal", ForumTabsState.BOTTOM));
    orientationAnchors[0] = readAnchor(state, "horizontal");
    orientationAnchors[1] = readAnchor(state, "side");
    pendingAnchor = readAnchor(state, "current");
    if (!hasSnapshot) selectedId = state.getInt("selected", Integer.MIN_VALUE);
    revealSelection = true;
    scheduleSettle();
  }

  private static void putAnchor (Bundle state, String key, @Nullable Anchor anchor) {
    if (anchor != null) state.putIntArray(key, new int[] {anchor.id, anchor.position, anchor.offset});
  }

  private static @Nullable Anchor readAnchor (Bundle state, String key) {
    int[] values = state.getIntArray(key);
    return values != null && values.length == 3 && values[1] >= 0 ? new Anchor(values[0], values[1], values[2]) : null;
  }

  private float fontScale () { return Math.max(1f, getResources().getConfiguration().fontScale); }
  private boolean side () { return placement == ForumTabsState.START; }
  private boolean reduceMotion () { return owner.tdlib() == null || Settings.instance().needReduceMotion(); }
  private String string (int resId, Object... args) {
    // Cloud-language and Settings singletons must not be initialized by an account-free view.
    return owner.tdlib() == null ? getResources().getString(resId, args) : Lang.getString(resId, args);
  }
  private String label (Item item) { return item.id == 0 ? string(R.string.ForumTabsAll) : item.id == NEW_TOPIC ? string(R.string.ForumTabsNew) : item.name; }
  private String unreadBadge (int count) {
    String compact = owner.tdlib() != null ? Lang.compactNumber(count) : null;
    // Lang.compactNumber is nullable on pre-24/vendor ICU implementations.
    return compact != null ? compact : count > 999 ? "999+" : Integer.toString(count);
  }

  /** nextHorizontal is TOP or BOTTOM, and is used when the current placement is START. */
  public void setPlacement (int placement, int nextHorizontal) {
    if (destroyed) return;
    if (placement != ForumTabsState.TOP && placement != ForumTabsState.START && placement != ForumTabsState.BOTTOM) {
      throw new IllegalArgumentException("Unknown forum tabs placement");
    }
    this.nextHorizontal = nextHorizontal == ForumTabsState.TOP ? ForumTabsState.TOP : ForumTabsState.BOTTOM;
    if (this.placement != placement) {
      list.stopScroll();
      Anchor previous = captureAnchor();
      orientationAnchors[side() ? 1 : 0] = previous;
      this.placement = placement;
      Anchor saved = orientationAnchors[side() ? 1 : 0];
      pendingAnchor = saved != null ? saved : previous;
      // Offsets belong to the old viewport. Restore its identity first, then reveal the
      // selected tab after the new orientation and control widths have been laid out.
      // An explicit drag can still cancel this one-shot adjustment.
      revealSelection = true;
      layout.setOrientation(side() ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL);
      layout.setReverseLayout(!side() && Lang.rtl());
      refreshRows();
      requestLayout();
      scheduleSettle();
    }
    updatePlacementDescription();
    invalidate();
  }

  /**
   * Visible server topics keep their supplied order. A separately fetched selection is appended
   * only if absent; All stays first and New Topic stays last. No list subscriptions or TDLib
   * requests are made here (custom-emoji media follows the normal per-view Text receiver path).
   */
  public void setTopics (List<TdApi.ForumTopic> topics, @Nullable TdApi.ForumTopic selectedTopic,
                         int selectedTopicId, boolean canCreate, boolean loading,
                         @Nullable TdApi.Error error, boolean endReached) {
    if (destroyed) return;
    Anchor anchor = captureAnchor();
    hasSnapshot = true;
    boolean changedSelection = selectedId != selectedTopicId;
    selectedId = selectedTopicId;
    if (changedSelection) {
      revealSelection = true;
      orientationAnchors[0] = orientationAnchors[1] = null;
    }
    this.loading = loading;
    this.failed = error != null && !loading;
    this.endReached = endReached;
    retryRequested = false;
    sourceCount = topics.size();
    sourceTail = 0;
    List<Item> next = new ArrayList<>(topics.size() + 3);
    next.add(new Item(0, selectedId == 0));
    Set<Integer> seen = new HashSet<>();
    serverVisibleCount = 0;
    for (TdApi.ForumTopic topic : topics) {
      if (topic == null || topic.info == null) continue;
      sourceTail = topic.info.forumTopicId;
      if (topic.info.forumTopicId <= 0 || topic.info.isHidden || !seen.add(topic.info.forumTopicId)) continue;
      next.add(topicItem(topic));
      serverVisibleCount++;
    }
    if (selectedTopic != null && selectedTopic.info != null && selectedTopicId > 0 &&
        selectedTopic.info.forumTopicId == selectedTopicId && !selectedTopic.info.isHidden && !seen.contains(selectedTopicId)) {
      next.add(topicItem(selectedTopic));
    }
    if (canCreate) next.add(new Item(NEW_TOPIC, false));
    List<Item> before = adapter.items;
    DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
      @Override public int getOldListSize () { return before.size(); }
      @Override public int getNewListSize () { return next.size(); }
      @Override public boolean areItemsTheSame (int oldIndex, int newIndex) { return before.get(oldIndex).id == next.get(newIndex).id; }
      @Override public boolean areContentsTheSame (int oldIndex, int newIndex) { return before.get(oldIndex).sameContent(next.get(newIndex)); }
      @Override public Object getChangePayload (int oldIndex, int newIndex) { return REBIND; }
    });
    boolean changed = before.size() != next.size();
    if (!changed) for (int i = 0; i < before.size(); i++) {
      if (before.get(i).id != next.get(i).id || !before.get(i).sameContent(next.get(i))) { changed = true; break; }
    }
    adapter.items = next;
    if (changed) {
      pendingAnchor = anchor;
      diff.dispatchUpdatesTo(adapter);
    }
    boolean showState = loading || failed;
    if ((stateButton.getVisibility() == VISIBLE) != showState) {
      pendingAnchor = anchor;
      stateButton.setVisibility(showState ? VISIBLE : GONE);
      requestLayout();
    }
    stateButton.update();
    scheduleSettle();
  }

  private Item topicItem (TdApi.ForumTopic topic) {
    return new Item(topic, selectedId == topic.info.forumTopicId,
      ForumPresentation.isMuted(topic.notificationSettings, owner.tdlib() != null && owner.tdlib().chatMuteFor(topic.info.chatId) > 0));
  }

  private void refreshRows () {
    // Payload updates retain the holder and its receiver, including during a placement cycle.
    adapter.notifyItemRangeChanged(0, adapter.getItemCount(), REBIND);
    for (TabView row : adapter.rows) row.refreshPresentation();
  }

  private void scheduleSettle () {
    if (destroyed || !attached) return;
    list.removeCallbacks(settleTask);
    list.postOnAnimation(settleTask);
  }

  private void settle () {
    if (destroyed || !attached || !hasSnapshot || list.getWidth() == 0 || list.getHeight() == 0) return;
    if (list.isComputingLayout() || list.isLayoutRequested()) { scheduleSettle(); return; }
    // Restore before revealing: a selected tab already inside the restored viewport must not
    // be unnecessarily moved to its leading edge. Missing paged anchors survive early loads.
    if (pendingAnchor != null && !adapter.items.isEmpty()) {
      Anchor anchor = pendingAnchor;
      int position = adapter.indexOf(anchor.id);
      if (position < 0 && revealSelection && adapter.indexOf(selectedId) >= 0) {
        // The old off-page selection may have disappeared during controller replacement.
        // Its missing anchor must not delay an already available new selection.
        pendingAnchor = null;
        scheduleSettle();
        return;
      }
      if (position < 0 && !endReached && anchor.position >= adapter.getItemCount()) {
        loadMoreIfNeeded();
        return;
      }
      pendingAnchor = null;
      if (position < 0) position = Math.min(anchor.position, adapter.getItemCount() - 1);
      View target = layout.findViewByPosition(position);
      int extent = target != null ? (side() ? target.getHeight() : target.getWidth()) :
        (side() ? Screen.dp(100) : Math.max(Screen.dp(48), list.getWidth()));
      layout.scrollToPositionWithOffset(position, Math.max(1 - extent, Math.min(0, anchor.offset)));
      scheduleSettle();
      return;
    }
    if (revealSelection) {
      int position = adapter.indexOf(selectedId);
      if (position >= 0) {
        revealSelection = false;
        pendingAnchor = null;
        View target = layout.findViewByPosition(position);
        boolean visible = target != null && (side()
          ? layout.getDecoratedTop(target) >= 0 && layout.getDecoratedBottom(target) <= list.getHeight()
          : layout.getDecoratedLeft(target) >= 0 && layout.getDecoratedRight(target) <= list.getWidth());
        if (!visible) {
          layout.scrollToPositionWithOffset(position, 0);
          scheduleSettle();
          return;
        }
      }
    }
    loadMoreIfNeeded();
  }

  private @Nullable Anchor captureAnchor () {
    if (pendingAnchor != null) return pendingAnchor;
    int position = layout.findFirstVisibleItemPosition();
    if (position < 0 || position >= adapter.items.size()) return null;
    View view = layout.findViewByPosition(position);
    if (view == null) return null;
    int offset = side() ? layout.getDecoratedTop(view) : layout.getReverseLayout()
      ? list.getWidth() - layout.getDecoratedRight(view) : layout.getDecoratedLeft(view);
    return new Anchor(adapter.items.get(position).id, position, offset);
  }

  private void loadMoreIfNeeded () {
    if (destroyed || !attached || loading || failed || retryRequested || endReached || !isShown()) return;
    if (!ForumTopicsTabsLayout.nearEnd(layout.findLastVisibleItemPosition(), serverVisibleCount)) return;
    // A repeated, empty or failed page must not create a UI-driven request loop. The host can
    // explicitly retry; a changed server prefix permits another near-end request.
    if (requestedCount == sourceCount && requestedTail == sourceTail) return;
    requestedCount = sourceCount; requestedTail = sourceTail;
    listener.onLoadMore();
  }

  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    int width = resolveSize(side() ? recommendedSideWidth() : Screen.dp(320), widthSpec);
    int height = resolveSize(side() ? Screen.dp(320) : recommendedHorizontalHeight(), heightSpec);
    setMeasuredDimension(width, height);
    parts = ForumTopicsTabsLayout.partition(width, height, Screen.dp(48), side(), Lang.rtl(), stateButton.getVisibility() == VISIBLE);
    measurePart(list, parts.tabs);
    measurePart(stateButton, parts.state);
    measurePart(placementButton, parts.placement);
  }

  private void measurePart (View view, ForumTopicsTabsLayout.Bounds bounds) {
    view.measure(MeasureSpec.makeMeasureSpec(bounds.width(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(bounds.height(), MeasureSpec.EXACTLY));
  }

  @Override protected void onLayout (boolean changed, int l, int t, int r, int b) {
    if (parts == null) return;
    layoutPart(list, parts.tabs); layoutPart(stateButton, parts.state); layoutPart(placementButton, parts.placement);
    scheduleSettle();
  }

  private void layoutPart (View view, ForumTopicsTabsLayout.Bounds bounds) { view.layout(bounds.left, bounds.top, bounds.right, bounds.bottom); }

  @Override protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);
    dividerPaint.setColor(Theme.separatorColor());
    if (side()) {
      float x = Lang.rtl() ? 0 : getWidth() - 1;
      canvas.drawRect(x, 0, x + 1, getHeight(), dividerPaint);
    } else {
      float y = placement == ForumTabsState.BOTTOM ? 0 : getHeight() - 1;
      canvas.drawRect(0, y, getWidth(), y + 1, dividerPaint);
    }
  }

  @Override public void onThemeInvalidate (boolean isTempUpdate) {
    if (destroyed) return;
    setBackgroundColor(Theme.fillingColor());
    for (TabView row : adapter.rows) row.updateTheme();
    placementButton.invalidate(); stateButton.invalidate(); invalidate();
  }

  @Override public void onLanguagePackEvent (int event, int arg1) {
    if (destroyed || event == Lang.EVENT_DATE_FORMAT_CHANGED) return;
    pendingAnchor = captureAnchor();
    layout.setReverseLayout(!side() && Lang.rtl());
    list.setContentDescription(string(R.string.ForumTabsLabel));
    refreshRows(); updatePlacementDescription(); stateButton.update();
    requestLayout(); scheduleSettle();
  }

  @Override protected void onConfigurationChanged (Configuration configuration) {
    super.onConfigurationChanged(configuration);
    if (destroyed) return;
    pendingAnchor = captureAnchor();
    refreshRows(); requestLayout(); scheduleSettle();
  }

  @Override protected void onAttachedToWindow () {
    super.onAttachedToWindow(); attached = true;
    if (!destroyed) { onThemeInvalidate(false); scheduleSettle(); }
  }

  @Override protected void onDetachedFromWindow () {
    pendingAnchor = captureAnchor();
    attached = false; list.removeCallbacks(settleTask);
    super.onDetachedFromWindow();
  }

  public void destroy () {
    if (destroyed) return;
    destroyed = true;
    list.removeCallbacks(settleTask); list.stopScroll();
    Lang.removeLanguageListener(this);
    owner.removeThemeListenerByTarget(this);
    list.setAdapter(null);
    list.getRecycledViewPool().clear();
    for (TabView row : adapter.rows) { row.clear(); row.receiver.detach(); row.receiver.performDestroy(); }
    adapter.rows.clear(); adapter.items = Collections.emptyList();
    pendingAnchor = null; orientationAnchors[0] = orientationAnchors[1] = null;
    placementButton.setOnClickListener(null); stateButton.setOnClickListener(null);
  }

  private static final class Anchor {
    final int id, position, offset;
    Anchor (int id, int position, int offset) { this.id = id; this.position = position; this.offset = offset; }
  }

  /** Copy presentation primitives: mutable TDLib objects are never used as DiffUtil baselines. */
  private static final class Item {
    final int id, color, unread, mentions, reactions, votes;
    final long emojiId;
    final String name;
    final boolean selected, general, pinned, closed, muted;
    Item (int id, boolean selected) {
      this.id = id; this.selected = selected; name = "";
      color = unread = mentions = reactions = votes = 0; emojiId = 0;
      general = pinned = closed = muted = false;
    }
    Item (TdApi.ForumTopic topic, boolean selected, boolean muted) {
      id = topic.info.forumTopicId; name = topic.info.name != null ? topic.info.name : "";
      this.selected = selected; this.muted = muted;
      general = topic.info.isGeneral || id == ForumNavigation.GENERAL_TOPIC_ID;
      pinned = topic.isPinned; closed = topic.info.isClosed;
      color = topic.info.icon != null ? topic.info.icon.color : 0x6fb9f0;
      emojiId = !general && topic.info.icon != null ? topic.info.icon.customEmojiId : 0;
      unread = Math.max(0, topic.unreadCount); mentions = Math.max(0, topic.unreadMentionCount);
      reactions = Math.max(0, topic.unreadReactionCount); votes = Math.max(0, topic.unreadPollVoteCount);
    }
    boolean sameContent (Item item) {
      return selected == item.selected && color == item.color && emojiId == item.emojiId && name.equals(item.name) &&
        unread == item.unread && mentions == item.mentions && reactions == item.reactions && votes == item.votes &&
        general == item.general && pinned == item.pinned && closed == item.closed && muted == item.muted;
    }
  }

  private final class TabsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    List<Item> items = Collections.emptyList();
    final List<TabView> rows = new ArrayList<>();
    TabsAdapter () { setHasStableIds(true); }
    int indexOf (int id) { for (int i = 0; i < items.size(); i++) if (items.get(i).id == id) return i; return -1; }
    @Override public int getItemCount () { return items.size(); }
    @Override public long getItemId (int position) {
      int id = items.get(position).id;
      return id == NEW_TOPIC ? Long.MIN_VALUE : id; // -1 is RecyclerView.NO_ID, not a stable ID.
    }
    @Override public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int type) {
      TabView row = new TabView(parent.getContext()); rows.add(row);
      row.setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      return new RecyclerView.ViewHolder(row) { };
    }
    @Override public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) { ((TabView) holder.itemView).bind(items.get(position)); }
    @Override public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) { onBindViewHolder(holder, position); }
    @Override public void onViewRecycled (@NonNull RecyclerView.ViewHolder holder) { ((TabView) holder.itemView).clear(); }
  }

  private final class TabView extends ViewGroup {
    final ComplexReceiver receiver = new ComplexReceiver(this);
    final android.widget.TextView label;
    final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final Path path = new Path();
    final Drawable pin = Drawables.get(getResources(), R.drawable.deproko_baseline_pin_16);
    final Drawable lock = Drawables.get(getResources(), R.drawable.baseline_lock_16);
    final Drawable mute = Drawables.get(getResources(), R.drawable.baseline_notifications_off_16);
    @Nullable Item item;
    @Nullable Text customEmoji;
    long emojiId;
    String badge = "", displayedBadge = "";
    float badgeWidth, metadataWidth, metadataHeight;
    int labelStart, labelEnd, labelTop;

    TabView (Context context) {
      super(context);
      receiver.detach();
      setWillNotDraw(false);
      setFocusable(true); setClickable(true);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
      RippleSupport.setTransparentSelector(this);
      label = new android.widget.TextView(context);
      label.setTypeface(Fonts.getRobotoMedium());
      label.setIncludeFontPadding(false);
      label.setEllipsize(TextUtils.TruncateAt.END);
      label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      label.setClickable(false); label.setFocusable(false);
      addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      setOnClickListener(v -> {
        if (destroyed || item == null) return;
        if (item.id == NEW_TOPIC) listener.onNewTopic();
        else {
          // Even clicking the current, partially clipped tab is an explicit visibility request.
          revealSelection = true;
          listener.onSelectTopic(item.id);
          scheduleSettle();
        }
      });
    }

    void bind (Item item) {
      this.item = item;
      setSelected(item.selected);
      badge = item.unread > 0 ? unreadBadge(item.unread) : item.mentions > 0 ? "@" : item.reactions > 0 ? "♥" : item.votes > 0 ? "✓" : "";
      receiver.setAnimationDisabled(reduceMotion());
      if (emojiId != item.emojiId) {
        clearEmoji(); emojiId = item.emojiId;
        if (emojiId != 0 && owner.tdlib() != null) {
          TdApi.FormattedText emoji = new TdApi.FormattedText("*", new TdApi.TextEntity[] {
            new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(emojiId))
          });
          customEmoji = new Text.Builder(owner.tdlib(), emoji, null, Screen.dp(48), Paints.robotoStyleProvider(24), TextColorSets.WHITE, (text, media) -> {
            if (!destroyed && text == customEmoji) { text.requestMedia(receiver); invalidate(); }
          }).singleLine().build();
          customEmoji.requestMedia(receiver);
        }
      }
      refreshPresentation();
    }

    void refreshPresentation () {
      if (item == null) return;
      label.setText(label(item).replace('\n', ' '));
      // Keep a narrow rail even at very large accessibility scales; its text still grows to
      // 30dp, with one readable line when two no longer fit inside the compact row.
      label.setTextSize(TypedValue.COMPLEX_UNIT_SP, side() ? Math.min(11f, 30f / fontScale()) : 14);
      label.setMaxLines(side() ? 2 : 1);
      label.setGravity(side() ? Gravity.CENTER : (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        label.setTextDirection(Lang.rtl() ? View.TEXT_DIRECTION_FIRST_STRONG_RTL : View.TEXT_DIRECTION_FIRST_STRONG_LTR);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        // Before API 23 FIRST_STRONG uses the view's layout direction as its fallback.
        label.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        label.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
      }
      StringBuilder description = new StringBuilder(item.id == 0 ? string(R.string.ForumTabsAllDescription) : label(item));
      if (item.selected) append(description, R.string.ForumTabsSelected);
      if (item.general) append(description, R.string.ForumGeneral);
      if (item.muted) append(description, R.string.ForumNotificationsMuted);
      if (item.closed) append(description, R.string.ForumTopicClosed);
      if (item.pinned) append(description, R.string.ForumPinned);
      if (item.unread > 0 || item.mentions > 0 || item.reactions > 0 || item.votes > 0) {
        description.append(". ").append(string(R.string.ForumTabsUnread, item.unread, item.mentions, item.reactions, item.votes));
      }
      setContentDescription(description);
      if (Build.VERSION.SDK_INT >= 26) setTooltipText(description);
      updateTheme(); requestLayout();
    }

    void append (StringBuilder builder, int resId) { builder.append(". ").append(string(resId)); }
    void updateTheme () {
      label.setTextColor(Theme.getColor(item != null && (item.selected || item.id == NEW_TOPIC) ? ColorId.iconActive : ColorId.text));
      invalidate();
    }

    void clearEmoji () {
      if (customEmoji != null) customEmoji.performDestroy();
      customEmoji = null; emojiId = 0; receiver.clear();
    }
    void clear () {
      clearEmoji(); item = null; setSelected(false); label.setText(null); setContentDescription(null);
      if (Build.VERSION.SDK_INT >= 26) setTooltipText(null);
    }
    @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); if (!destroyed) receiver.attach(); }
    @Override protected void onDetachedFromWindow () { receiver.detach(); super.onDetachedFromWindow(); }

    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setClassName("android.widget.Button"); info.setSelected(isSelected());
      info.setCheckable(item != null && item.id != NEW_TOPIC); info.setChecked(isSelected());
    }

    private void badgePaint () {
      paint.setTypeface(Fonts.getRobotoMedium());
      paint.setTextSize(Math.min(Screen.sp(10), Screen.dp(side() ? 12 : 14)));
      paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override protected void onMeasure (int widthSpec, int heightSpec) {
      int states = item == null ? 0 : (item.pinned ? 1 : 0) + (item.closed ? 1 : 0) + (item.muted ? 1 : 0);
      displayedBadge = side() && item != null && item.unread > 99 ? "99+" : badge;
      badgePaint();
      metadataHeight = Math.max(Screen.dp(14), paint.descent() - paint.ascent() + Screen.dp(3));
      badgeWidth = displayedBadge.isEmpty() ? 0 : Math.max(metadataHeight, paint.measureText(displayedBadge) + Screen.dp(6));
      metadataWidth = states * Screen.dp(14) + badgeWidth + (states > 0 && badgeWidth > 0 ? Screen.dp(3) : 0);
      int width, height;
      if (side()) {
        width = list.getMeasuredWidth() > 0 ? list.getMeasuredWidth() : recommendedSideWidth();
        height = ForumTopicsTabsLayout.sideRowHeight(getResources().getDisplayMetrics().density, fontScale());
        labelStart = Screen.dp(4); labelEnd = Math.max(labelStart, width - Screen.dp(4));
        labelTop = Screen.dp(32);
        int labelHeight = Math.max(0, height - labelTop - Screen.dp(4));
        label.setMaxLines(labelHeight >= label.getLineHeight() * 2 ? 2 : 1);
        badgeWidth = Math.min(badgeWidth, Math.max(0, width / 2f - Screen.dp(2)));
        label.measure(MeasureSpec.makeMeasureSpec(labelEnd - labelStart, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(labelHeight, MeasureSpec.EXACTLY));
      } else {
        height = list.getMeasuredHeight() > 0 ? list.getMeasuredHeight() : recommendedHorizontalHeight();
        // Measure the native label, not an approximate paint advance. In particular All must
        // get its complete localized label after the icon, including TextView/px rounding.
        label.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int leading = Screen.dp(34), trailing = Screen.dp(10);
        float desired = leading + trailing + label.getMeasuredWidth() + (metadataWidth > 0 ? metadataWidth + Screen.dp(5) : 0);
        int maxWidth = Math.min(Screen.dp(240), Math.max(Screen.dp(48), list.getMeasuredWidth()));
        width = Math.min(maxWidth, Math.max(Screen.dp(48), (int) Math.ceil(desired)));
        int start = Math.min(width, leading);
        int end = Math.max(start, width - trailing - (int) Math.ceil(metadataWidth) - (metadataWidth > 0 ? Screen.dp(5) : 0));
        labelStart = Lang.rtl() ? width - end : start;
        labelEnd = Lang.rtl() ? width - start : end;
        int labelHeight = Math.max(0, height - Screen.dp(8));
        labelTop = Screen.dp(4);
        label.measure(MeasureSpec.makeMeasureSpec(Math.max(0, labelEnd - labelStart), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(labelHeight, MeasureSpec.EXACTLY));
      }
      setMeasuredDimension(width, height);
    }

    @Override protected void onLayout (boolean changed, int l, int t, int r, int b) {
      label.layout(labelStart, labelTop, labelEnd, labelTop + label.getMeasuredHeight());
    }

    private void rounded (Canvas c, float left, float top, float right, float bottom, float radius) {
      rect.set(left, top, right, bottom); c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      if (item == null) return;
      paint.setStyle(Paint.Style.FILL);
      if (item.selected) {
        paint.setColor(Theme.getColor(ColorId.iconActive)); paint.setAlpha(28);
        rounded(canvas, Screen.dp(3), Screen.dp(4), getWidth() - Screen.dp(3), getHeight() - Screen.dp(4), Screen.dp(10));
        paint.setAlpha(255);
        if (side()) {
          float x = Lang.rtl() ? Screen.dp(2) : getWidth() - Screen.dp(5);
          rounded(canvas, x, Screen.dp(16), x + Screen.dp(3), getHeight() - Screen.dp(16), Screen.dp(2));
        } else {
          float y = placement == ForumTabsState.BOTTOM ? Screen.dp(1) : getHeight() - Screen.dp(4);
          rounded(canvas, Screen.dp(12), y, getWidth() - Screen.dp(12), y + Screen.dp(3), Screen.dp(2));
        }
      }
      float iconX = side() ? getWidth() / 2f : Lang.rtl() ? getWidth() - Screen.dp(18) : Screen.dp(18);
      drawTopicIcon(canvas, iconX, side() ? Screen.dp(16) : getHeight() / 2f);
      if (side()) drawSideMetadata(canvas);
      else if (metadataWidth > 0) drawMetadata(canvas, getWidth() - Screen.dp(10) - metadataWidth, getHeight() / 2f);
      if (isFocused()) {
        paint.setColor(Theme.getColor(ColorId.iconActive)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(2));
        rounded(canvas, Screen.dp(3), Screen.dp(3), getWidth() - Screen.dp(3), getHeight() - Screen.dp(3), Screen.dp(10));
        paint.setStyle(Paint.Style.FILL);
      }
    }

    private int topicIconSize () { return ForumTopicsTabsLayout.topicIconSize(getResources().getDisplayMetrics().density, side()); }

    private void drawTopicIcon (Canvas c, float cx, float cy) {
      float size = topicIconSize(), r = size / 2;
      if (customEmoji != null) {
        int save = c.save();
        c.translate(cx - r, cy - r);
        c.scale(size / Math.max(1, customEmoji.getWidth()), size / Math.max(1, customEmoji.getHeight()));
        customEmoji.draw(c, 0, 0, null, 1f, receiver); c.restoreToCount(save);
        return;
      }
      // The fallback paths use a 24dp coordinate system, just like custom emoji's media slot.
      int save = c.save(); c.translate(cx, cy); c.scale(size / Screen.dp(24), size / Screen.dp(24));
      cx = cy = 0; r = Screen.dp(12);
      paint.setColor(item.id <= 0 ? Theme.getColor(ColorId.iconActive) : 0xff000000 | item.color);
      paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(2)); paint.setStrokeCap(Paint.Cap.ROUND);
      if (item.id == NEW_TOPIC) {
        c.drawCircle(cx, cy, r - Screen.dp(2), paint);
        c.drawLine(cx - Screen.dp(5), cy, cx + Screen.dp(5), cy, paint);
        c.drawLine(cx, cy - Screen.dp(5), cx, cy + Screen.dp(5), paint);
      } else if (item.id == 0) {
        rounded(c, cx - r, cy - r + Screen.dp(2), cx + r - Screen.dp(5), cy + r - Screen.dp(5), Screen.dp(4));
        c.drawLine(cx - Screen.dp(5), cy + r, cx + r, cy + r, paint);
        c.drawLine(cx + r, cy - Screen.dp(5), cx + r, cy + r, paint);
      } else if (item.general) {
        // General has its own hash mark, never a custom emoji or a renamed-topic initial.
        c.drawLine(cx - Screen.dp(3), cy - Screen.dp(8), cx - Screen.dp(5), cy + Screen.dp(8), paint);
        c.drawLine(cx + Screen.dp(5), cy - Screen.dp(8), cx + Screen.dp(3), cy + Screen.dp(8), paint);
        c.drawLine(cx - Screen.dp(8), cy - Screen.dp(3), cx + Screen.dp(8), cy - Screen.dp(3), paint);
        c.drawLine(cx - Screen.dp(8), cy + Screen.dp(3), cx + Screen.dp(8), cy + Screen.dp(3), paint);
      } else {
        paint.setStyle(Paint.Style.FILL);
        rounded(c, cx - r, cy - r, cx + r, cy + r - Screen.dp(2), Screen.dp(6));
        path.reset(); path.moveTo(cx - Screen.dp(8), cy + Screen.dp(5)); path.lineTo(cx - Screen.dp(8), cy + r + Screen.dp(1));
        path.lineTo(cx + Screen.dp(1), cy + Screen.dp(7)); path.close(); c.drawPath(path, paint);
        String name = item.name.trim();
        String initial = name.isEmpty() ? "#" : name.substring(0, name.offsetByCodePoints(0, 1));
        paint.setTypeface(Fonts.getRobotoMedium()); paint.setTextSize(Screen.dp(14)); paint.setTextAlign(Paint.Align.CENTER); paint.setColor(0xffffffff);
        c.drawText(initial, cx, cy - (paint.ascent() + paint.descent()) / 2, paint);
      }
      paint.setStrokeCap(Paint.Cap.BUTT); paint.setStyle(Paint.Style.FILL);
      c.restoreToCount(save);
    }

    private void drawMetadata (Canvas c, float start, float cy) {
      if (item.pinned) { drawState(c, pin, start, cy, Screen.dp(12)); start += Screen.dp(14); }
      if (item.closed) { drawState(c, lock, start, cy, Screen.dp(12)); start += Screen.dp(14); }
      if (item.muted) { drawState(c, mute, start, cy, Screen.dp(12)); start += Screen.dp(14); }
      if (badgeWidth > 0) {
        if (item.pinned || item.closed || item.muted) start += Screen.dp(3);
        drawBadge(c, start, cy);
      }
    }

    private void drawSideMetadata (Canvas c) {
      // Status glyphs stay beside the icon; the unread chip overlays its upper trailing edge.
      // Neither consumes a third row nor steals space from the one/two-line short label.
      float cy = Screen.dp(7.5f), step = Screen.dp(9);
      if (item.pinned) { drawState(c, pin, Screen.dp(4), cy, Screen.dp(9)); cy += step; }
      if (item.closed) { drawState(c, lock, Screen.dp(4), cy, Screen.dp(9)); cy += step; }
      if (item.muted) drawState(c, mute, Screen.dp(4), cy, Screen.dp(9));
      if (badgeWidth > 0) drawBadge(c, getWidth() - Screen.dp(3) - badgeWidth, Screen.dp(3) + metadataHeight / 2);
    }

    private void drawBadge (Canvas c, float logical, float cy) {
      float left = Lang.rtl() ? getWidth() - logical - badgeWidth : logical;
      paint.setColor(item.muted ? Theme.badgeMutedColor() : Theme.badgeColor());
      rounded(c, left, cy - metadataHeight / 2, left + badgeWidth, cy + metadataHeight / 2, metadataHeight / 2);
      badgePaint(); paint.setColor(Theme.badgeTextColor());
      CharSequence text = TextUtils.ellipsize(displayedBadge, paint, Math.max(0, badgeWidth - Screen.dp(4)), TextUtils.TruncateAt.END);
      c.drawText(text.toString(), left + badgeWidth / 2, cy - (paint.ascent() + paint.descent()) / 2, paint);
    }

    private void drawState (Canvas c, Drawable drawable, float logical, float cy, float size) {
      float left = Lang.rtl() ? getWidth() - logical - size : logical;
      int save = c.save(); c.translate(left, cy - size / 2);
      c.scale(size / Math.max(1, drawable.getMinimumWidth()), size / Math.max(1, drawable.getMinimumHeight()));
      Drawables.draw(c, drawable, 0, 0, PorterDuffPaint.get(ColorId.iconLight)); c.restoreToCount(save);
    }
  }

  private int nextPlacement () { return side() ? nextHorizontal : ForumTabsState.START; }
  private void updatePlacementDescription () {
    int target = nextPlacement();
    String text = string(target == ForumTabsState.TOP ? R.string.ForumTabsMoveTop :
      target == ForumTabsState.START ? R.string.ForumTabsMoveStart : R.string.ForumTabsMoveBottom);
    placementButton.setContentDescription(text);
    if (Build.VERSION.SDK_INT >= 26) placementButton.setTooltipText(text);
    placementButton.invalidate();
  }

  private class PlacementButton extends View {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    PlacementButton (Context context) {
      super(context); setFocusable(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      RippleSupport.setTransparentSelector(this);
      setOnClickListener(v -> { if (!destroyed) listener.onCyclePlacement(); });
    }
    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button");
    }
    @Override protected void onDraw (Canvas c) {
      super.onDraw(c);
      float cx = getWidth() / 2f, cy = getHeight() / 2f, dx = Screen.dp(10), dy = Screen.dp(8);
      paint.setColor(Theme.getColor(isFocused() ? ColorId.iconActive : ColorId.icon));
      paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(1.5f));
      rect.set(cx - dx, cy - dy, cx + dx, cy + dy); c.drawRoundRect(rect, Screen.dp(3), Screen.dp(3), paint);
      paint.setStyle(Paint.Style.FILL); paint.setColor(Theme.getColor(ColorId.iconActive));
      int target = nextPlacement();
      if (target == ForumTabsState.START) {
        float x = Lang.rtl() ? cx + dx - Screen.dp(6) : cx - dx + Screen.dp(2);
        rect.set(x, cy - dy + Screen.dp(2), x + Screen.dp(4), cy + dy - Screen.dp(2));
      } else {
        float y = target == ForumTabsState.TOP ? cy - dy + Screen.dp(2) : cy + dy - Screen.dp(6);
        rect.set(cx - dx + Screen.dp(2), y, cx + dx - Screen.dp(2), y + Screen.dp(4));
      }
      c.drawRoundRect(rect, Screen.dp(1), Screen.dp(1), paint);
    }
  }

  private final class StateButton extends View {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final Drawable retry = Drawables.get(getResources(), R.drawable.baseline_replay_24);
    StateButton (Context context) {
      super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      if (Build.VERSION.SDK_INT >= 19) setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
      RippleSupport.setTransparentSelector(this);
      setOnClickListener(v -> {
        if (destroyed || !failed || loading || retryRequested) return;
        retryRequested = true; requestedCount = -1;
        update(); listener.onRetry();
      });
    }
    void update () {
      boolean canRetry = failed && !loading && !retryRequested;
      setClickable(canRetry); setFocusable(canRetry);
      String description = string(canRetry ? R.string.ForumTabsRetry : R.string.ForumTabsLoading);
      setContentDescription(description);
      if (Build.VERSION.SDK_INT >= 26) setTooltipText(description);
      invalidate();
    }
    @Override public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setClassName(isClickable() ? "android.widget.Button" : "android.widget.ProgressBar");
    }
    @Override protected void onDraw (Canvas c) {
      super.onDraw(c);
      if (destroyed) return;
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      if (failed && !retryRequested && !loading) {
        float size = Screen.dp(side() ? 24 : 20);
        int save = c.save(); c.translate(cx - size / 2, cy - size / 2);
        c.scale(size / Math.max(1, retry.getMinimumWidth()), size / Math.max(1, retry.getMinimumHeight()));
        Drawables.draw(c, retry, 0, 0, PorterDuffPaint.get(ColorId.iconNegative)); c.restoreToCount(save);
      } else {
        paint.setColor(Theme.getColor(ColorId.iconActive)); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Screen.dp(2)); paint.setStrokeCap(Paint.Cap.ROUND);
        rect.set(cx - Screen.dp(9), cy - Screen.dp(9), cx + Screen.dp(9), cy + Screen.dp(9));
        boolean animate = !reduceMotion();
        c.drawArc(rect, animate ? (SystemClock.uptimeMillis() % 1200) * .3f : -90, 260, false, paint);
        if (animate && attached && isShown()) postInvalidateOnAnimation();
      }
    }
  }
}
