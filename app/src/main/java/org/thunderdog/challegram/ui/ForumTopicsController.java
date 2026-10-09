package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.ForumTopicView;
import org.thunderdog.challegram.component.chat.ForumTopicListDiff;
import org.thunderdog.challegram.component.chat.ChatHeaderView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ForumTopicSelection;
import org.thunderdog.challegram.data.ForumTopicSelection.Action;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.ForumTopicActions;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CircleButton;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;

/** Owns a cancellable list session; no forum identifiers are encoded as generic threads. */
public final class ForumTopicsController extends RecyclerViewController<ForumTopicsController.Arguments> implements ChatListener, CleanupStartupDelegate, NotificationSettingsListener, TdlibCache.SupergroupDataChangeListener {
  public static final class Arguments {
    public final long chatId;
    public final TdApi.ChatList chatList;
    public Arguments (long chatId, TdApi.ChatList chatList) { this.chatId = chatId; this.chatList = chatList; }
  }

  private ForumTopicStore.ListSession session;
  private ForumTopicStore.Snapshot snapshot;
  private TopicAdapter adapter;
  private String query = "";
  private Object sessionEpoch;
  private volatile Object lifecycleEpoch = new Object();
  private boolean subscribed, restoreSearch, restoringSearch, selectionFromSearch;
  private int selectionHeaderRetries;
  private int restorePosition = -1, restoreOffset;
  private ForumTopicUi topicUi;
  private ChatHeaderView chatHeader;
  private ForumTopicSelection selection;
  private int[] restoredSelection;
  private final Map<Integer, ForumTopicStore.TopicSubscription> selectedObservers = new HashMap<>();
  private EnumSet<Action> headerActions = EnumSet.noneOf(Action.class);
  private long supergroupId;
  private CircleButton createButton;

  public ForumTopicsController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_forumTopics; }
  @Override public long getChatId () { return getArgumentsStrict().chatId; }
  @Override public CharSequence getName () { return tdlib.chatTitle(getChatId()); }
  @Override protected int getRecyclerBackground () { return ColorId.filling; }
  @Override protected int getMenuId () { return R.id.menu_forumTopics; }
  // Selection buttons depend on the current intersection and available pane width.
  @Override protected boolean allowMenuReuse () { return false; }
  @Override protected int getSearchMenuId () { return R.id.menu_clear; }
  @Override protected int getSearchHint () { return R.string.ForumSearchTopics; }
  @Override protected String getSearchStartQuery () { return query; }
  @Override protected int getSelectMenuId () { return R.id.menu_forumSelection; }
  private void updateCreateButton () {
    if (createButton != null) {
      createButton.setVisibility(topicUi != null && topicUi.canCreate() && (selection == null || selection.isEmpty()) && !inSearchMode() ? View.VISIBLE : View.GONE);
      createButton.setContentDescription(Lang.getString(R.string.ForumCreateTopic));
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) createButton.getLayoutParams();
      params.gravity = Gravity.BOTTOM | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT);
      params.leftMargin = params.rightMargin = Screen.dp(12);
      params.bottomMargin = Screen.dp(12) + extraBottomInset;
      createButton.setLayoutParams(params);
    }
  }

  @Override protected View onCreateView (Context context) {
    FrameLayout content = (FrameLayout) super.onCreateView(context);
    createButton = new CircleButton(context);
    createButton.init(R.drawable.baseline_add_24, 56, 4, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);
    createButton.setFocusable(true);
    createButton.setOnClickListener(v -> { if (topicUi.canCreate() && !topicUi.isBusy()) topicUi.openEditor(0); });
    content.addView(createButton, new FrameLayout.LayoutParams(Screen.dp(64), Screen.dp(64), Gravity.BOTTOM | Gravity.RIGHT));
    addThemeInvalidateListener(createButton);
    updateCreateButton();
    return content;
  }
  @Override protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    updateCreateButton();
  }

  @Override public void setArguments (Arguments arguments) {
    if (selection != null) clearTopicSelection();
    super.setArguments(arguments);
    selection = new ForumTopicSelection(tdlib.id(), arguments.chatId);
  }

  @Override public View getCustomHeaderCell () {
    if (chatHeader == null) {
      chatHeader = new ChatHeaderView(context(), tdlib, this);
      chatHeader.setInnerMargins(Screen.dp(56), Screen.dp(98));
      chatHeader.setChat(tdlib, tdlib.chat(getChatId()), null);
      chatHeader.setCallback(() -> tdlib.ui().openChatProfile(this, getChatId(), null, null));
      addThemeInvalidateListener(chatHeader);
    }
    return chatHeader;
  }

  @Override protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    topicUi = new ForumTopicUi(this, getChatId());
    adapter = new TopicAdapter();
    recyclerView.setAdapter(adapter);
    recyclerView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (right-left != oldRight-oldLeft && headerView != null && inSelectMode()) headerView.updateCustomButtons(this, R.id.menu_forumSelection);
    });
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled (@NonNull RecyclerView view, int dx, int dy) { loadMoreIfNeeded(); }
    });
    tdlib.listeners().subscribeToChatUpdates(getChatId(), this);
    tdlib.listeners().subscribeToSettingsUpdates(this);
    tdlib.listeners().addCleanupListener(this);
    TdApi.Chat groupChat = tdlib.chat(getChatId());
    if (groupChat != null && groupChat.type instanceof TdApi.ChatTypeSupergroup) {
      supergroupId = ((TdApi.ChatTypeSupergroup) groupChat.type).supergroupId;
      tdlib.cache().subscribeToSupergroupUpdates(supergroupId, this);
    }
    tdlib.openChat(getChatId(), this);
    subscribed = true;
    openSession();
  }

  private void closeSession () {
    sessionEpoch = new Object();
    if (session != null) session.close();
    session = null;
  }

  private void openSession () {
    closeSession();
    Object epoch = sessionEpoch;
    snapshot = tdlib.topics().cachedList(getChatId(), query);
    adapter.update(snapshot);
    session = tdlib.topics().openList(getChatId(), query, value -> {
      if (isDestroyed() || sessionEpoch != epoch || !query.equals(value.key.query)) return;
      snapshot = value;
      adapter.update(value);
      if (restorePosition >= 0 && !value.topics.isEmpty() && (restorePosition < value.topics.size() || value.endReached)) {
        ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(Math.min(restorePosition, value.topics.size()-1), restoreOffset);
        restorePosition = -1;
      }
      getRecyclerView().post(this::loadMoreIfNeeded);
    });
  }

  private void loadMoreIfNeeded () {
    if (session == null || snapshot == null || snapshot.error != null || snapshot.endReached || snapshot.loadingInitial || snapshot.loadingMore || snapshot.refreshing) return;
    LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
    if (manager != null && (restorePosition >= adapter.topics.size() || manager.findLastVisibleItemPosition() >= adapter.topics.size()-6)) session.loadMore();
  }

  private void setQuery (String value) {
    value = value.trim();
    if (query.equals(value)) return;
    query = value;
    if (!restoringSearch) clearTopicSelection();
    restorePosition = -1;
    snapshot = null;
    adapter.update(null);
    getRecyclerView().scrollToPosition(0);
    if (session != null) session.setQuery(query);
  }

  @Override protected void onSearchInputChanged (String query) { super.onSearchInputChanged(query); setQuery(query); }
  @Override protected void onEnterSearchMode () { super.onEnterSearchMode(); updateCreateButton(); }
  @Override protected void onLeaveSearchMode () {
    if (!selectionFromSearch) setQuery("");
    getRecyclerView().post(this::updateCreateButton);
  }
  @Override public void onFocus () {
    super.onFocus();
    if (restoreSearch) {
      restoreSearch = false;
      if (selection != null && !selection.isEmpty()) selectionFromSearch = true;
      else {
        restoringSearch = true;
        String savedQuery = query;
        openSearchMode();
        setSearchInput(savedQuery);
        restoringSearch = false;
      }
    }
    rebindRows(); // Local drafts may have changed while a topic was open.
    updateSelectionHeader();
    updateCreateButton();
    TdApi.Chat chat = tdlib.chat(getChatId());
    if (chat != null && !chat.viewAsTopics) getRecyclerView().post(() -> ForumTopicUi.openViewMode(this, false));
  }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_forumTopics) {
      header.addSearchButton(menu, this);
      header.addButton(menu, R.id.menu_forumActions, R.drawable.baseline_more_vert_24, getHeaderIconColorId(), this, Screen.dp(49));
    } else if (id == R.id.menu_clear) header.addClearButton(menu, this);
    else if (id == R.id.menu_forumSelection) {
      header.addButton(menu, R.id.menu_btn_pinUnpin, R.drawable.deproko_baseline_pin_24, getSelectHeaderIconColorId(), this, Screen.dp(48));
      header.addButton(menu, R.id.menu_btn_muteUnmute, R.drawable.baseline_notifications_off_24, getSelectHeaderIconColorId(), this, Screen.dp(48));
      header.addButton(menu, R.id.menu_btn_delete, R.drawable.baseline_delete_24, getSelectHeaderIconColorId(), this, Screen.dp(48));
      header.addButton(menu, R.id.menu_btn_more, R.drawable.baseline_more_vert_24, getSelectHeaderIconColorId(), this, Screen.dp(48));
      updateCustomMenu(id, menu);
    }
  }
  @Override public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) openSearchMode();
    else if (id == R.id.menu_btn_clear) clearSearchInput();
    else if (id == R.id.menu_forumActions) topicUi.showListMenu(() -> { if (session != null) session.refresh(); });
    else if (id == R.id.menu_btn_more) showSelectionMenu();
    else if (id == R.id.menu_btn_pinUnpin) runSelectionAction(selectionActions().contains(Action.PIN) ? Action.PIN : Action.UNPIN);
    else if (id == R.id.menu_btn_muteUnmute) runSelectionAction(selectionActions().contains(Action.MUTE) ? Action.MUTE : Action.UNMUTE);
    else if (id == R.id.menu_btn_delete) runSelectionAction(selectionActions().contains(Action.CLEAR_GENERAL) ? Action.CLEAR_GENERAL : Action.DELETE);
  }

  public EnumSet<Action> selectionActions () {
    if (selection == null) return EnumSet.noneOf(Action.class);
    ForumTopicStore.Snapshot pins = tdlib.topics().cachedList(getChatId(), "");
    boolean complete = pins.initialized && !pins.stale && pins.error == null && ForumTopicPolicy.completePinnedPrefix(pins.topics, pins.endReached);
    int count = 0;
    for (TdApi.ForumTopic t : pins.topics) if (t.isPinned) count++;
    return ForumTopicSelection.intersection(selection.topics(), tdlib.chatStatus(getChatId()), tdlib.chatMuteFor(getChatId()) > 0,
      complete, count, tdlib.options().pinnedForumTopicCountMax);
  }

  @Override protected void updateCustomMenu (int menuId, LinearLayout menu) {
    if (menuId != R.id.menu_forumSelection) return;
    EnumSet<Action> available = selectionActions();
    headerActions.clear();
    // The forum body reserves rail space, but its selection header spans the whole screen.
    HeaderView header = context().navigation().getHeaderView();
    int width = header != null ? header.getMeasuredWidth() : 0;
    if (width <= 0) width = Screen.dp(context().getResources().getConfiguration().screenWidthDp);
    int slots = ForumTopicSelection.primaryActionSlots(width / context().getResources().getDisplayMetrics().density,
      context().getResources().getConfiguration().fontScale);
    Action[] direct = {available.contains(Action.PIN) ? Action.PIN : Action.UNPIN,
      available.contains(Action.MUTE) ? Action.MUTE : Action.UNMUTE,
      available.contains(Action.CLEAR_GENERAL) ? Action.CLEAR_GENERAL : Action.DELETE};
    int[] ids = {R.id.menu_btn_pinUnpin, R.id.menu_btn_muteUnmute, R.id.menu_btn_delete};
    int[] icons = {direct[0] == Action.PIN ? R.drawable.deproko_baseline_pin_24 : R.drawable.deproko_baseline_pin_undo_24,
      direct[1] == Action.MUTE ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_24, R.drawable.baseline_delete_24};
    for (int i = 0; i < direct.length; i++) {
      View button = menu.findViewById(ids[i]);
      boolean show = available.contains(direct[i]) && slots > 0;
      if (show) { slots--; headerActions.add(direct[i]); }
      if (button != null) {
        button.setVisibility(show ? View.VISIBLE : View.GONE); button.setEnabled(topicUi == null || !topicUi.isBusy());
        button.setContentDescription(Lang.getString(ForumTopicUi.actionLabel(direct[i])));
        if (button instanceof HeaderButton) ((HeaderButton) button).setImageResource(icons[i]);
      }
    }
    View more = menu.findViewById(R.id.menu_btn_more);
    if (more != null) {
      more.setVisibility(ForumTopicActionPresentation.overflowActions(available, headerActions).isEmpty() ? View.GONE : View.VISIBLE);
      more.setEnabled(topicUi == null || !topicUi.isBusy());
      more.setContentDescription(Lang.getString(R.string.ForumSelectionActions));
    }
  }

  private void showSelectionMenu () {
    if (topicUi.isBusy() || selection == null || selection.isEmpty()) return;
    EnumSet<Action> available = ForumTopicActionPresentation.overflowActions(selectionActions(), headerActions);
    // Permissions/state may change between rendering the ellipsis and tapping it.
    if (available.isEmpty()) { updateSelectionHeader(); return; }
    ArrayList<Action> actions = new ArrayList<>(available);
    String[] labels = new String[actions.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = Lang.getString(ForumTopicUi.actionLabel(actions.get(i)));
    android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(context(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ForumSelectionActions));
    dialog.setItems(labels, (d, which) -> runSelectionAction(actions.get(which)));
    showAlert(dialog.setNegativeButton(Lang.getString(R.string.Cancel), null));
  }

  private void runSelectionAction (Action action) {
    if (topicUi.isBusy() || !selectionActions().contains(action)) return;
    ArrayList<ForumTopicActions.BatchTarget> targets = new ArrayList<>();
    for (TdApi.ForumTopic t : selection.topics()) targets.add(new ForumTopicActions.BatchTarget(t.info.forumTopicId, t.info.name));
    // ForumTopicUi guards the confirmation/start interval and duplicate submissions.
    topicUi.runBatch(action, targets, result -> {
      // A full success exits selection silently; failed/uncertain targets remain selected.
      for (ForumTopicActions.BatchOutcome outcome : result.outcomes) if (outcome.getSuccessful()) removeSelected(outcome.target.topicId);
      updateSelectionHeader();
    });
  }

  private void toggleSelection (TdApi.ForumTopic topic) {
    if (topicUi.isBusy()) return;
    if (selection.contains(topic.info.forumTopicId)) removeSelected(topic.info.forumTopicId);
    else if (selection.add(topic)) observeSelected(topic.info.forumTopicId);
    else showAlert(new android.app.AlertDialog.Builder(context(), Theme.dialogTheme()).setMessage(Lang.getString(R.string.ForumSelectionLimit, ForumTopicSelection.MAX_SELECTED)).setPositiveButton(Lang.getString(R.string.OK), null));
    updateSelectionHeader();
  }

  private void observeSelected (int id) {
    if (selectedObservers.containsKey(id)) return;
    selectedObservers.put(id, tdlib.topics().observeTopic(new TdlibForumTopicManager.Key(getChatId(), id), (topic, error) -> {
      if (isDestroyed() || selection == null || !selection.contains(id)) return;
      if (topic != null) selection.add(topic);
      else if (error != null && (error.code == 404 || "TOPIC_NOT_FOUND".equals(error.message) || "TOPIC_DELETED".equals(error.message))) removeSelected(id);
      updateSelectionHeader();
    }));
  }
  private void removeSelected (int id) {
    if (selection != null) selection.remove(id);
    ForumTopicStore.TopicSubscription observer = selectedObservers.remove(id);
    if (observer != null) observer.close();
  }

  /** Rail calls this before saving a per-chat snapshot; rotation does not call it. Query is retained. */
  public void clearTopicSelection () {
    selectionFromSearch = false;
    restoredSelection = null;
    if (selection != null) selection.clear();
    for (ForumTopicStore.TopicSubscription observer : selectedObservers.values()) observer.close();
    selectedObservers.clear();
    if (inSelectMode()) closeSelectMode();
    updateSelectionRows();
    updateCreateButton();
  }
  @Override public void onLeaveSelectMode () {
    super.onLeaveSelectMode();
    boolean reopenSearch = selectionFromSearch;
    clearTopicSelection();
    if (reopenSearch && getRecyclerView() != null) getRecyclerView().post(() -> {
      if (!isDestroyed() && isFocused()) openSearchMode();
    });
  }

  private void updateSelectionHeader () {
    if (selection == null || isDestroyed()) return;
    if (!isFocused()) { updateSelectionRows(); return; }
    if (!selection.isEmpty() && inSearchMode()) {
      selectionFromSearch = true;
      closeSearchMode(this::updateSelectionHeader);
      if (selectionHeaderRetries++ < 8 && getRecyclerView() != null) getRecyclerView().postDelayed(this::updateSelectionHeader, 150);
      updateSelectionRows();
      return;
    }
    if (selection.isEmpty()) {
      if (inSelectMode()) {
        closeSelectMode();
        if (selectionHeaderRetries++ < 8 && getRecyclerView() != null) getRecyclerView().postDelayed(this::updateSelectionHeader, 150);
      } else selectionHeaderRetries = 0;
    }
    else {
      if (!inSelectMode()) openSelectMode(selection.size());
      else setSelectedCount(selection.size());
      if (!inSelectMode() && selectionHeaderRetries++ < 8 && getRecyclerView() != null) getRecyclerView().postDelayed(this::updateSelectionHeader, 150);
      else if (inSelectMode()) selectionHeaderRetries = 0;
      if (headerView != null) headerView.updateCustomButtons(this, R.id.menu_forumSelection);
    }
    updateSelectionRows();
    updateCreateButton();
  }
  private void updateSelectionRows () {
    RecyclerView recycler = getRecyclerView();
    if (recycler == null) return;
    // Update only attached views; offscreen rows receive the current identity state at bind time.
    for (int i = 0; i < recycler.getChildCount(); i++) {
      View view = recycler.getChildAt(i);
      if (view instanceof ForumTopicView) {
        ForumTopicView row = (ForumTopicView) view;
        if (row.getTopic() != null) row.setSelection(selection != null && !selection.isEmpty(), selection != null && selection.contains(row.getTopic().info.forumTopicId), true);
      }
    }
  }

  @Override public void onChatTitleChanged (long chatId, String title) {
    runOnUiThreadOptional(() -> { if (chatHeader != null) chatHeader.setChat(tdlib, tdlib.chat(getChatId()), null); });
  }
  @Override public void onChatPhotoChanged (long chatId, TdApi.ChatPhotoInfo photo) { onChatTitleChanged(chatId, null); }
  @Override public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) { runOnUiThreadOptional(this::updateSelectionHeader); }
  @Override public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    if (supergroup.id == supergroupId) runOnUiThreadOptional(() -> {
      if (chatHeader != null) chatHeader.setChat(tdlib, tdlib.chat(getChatId()), null);
      updateSelectionHeader();
    });
  }

  @Override public void onChatViewAsTopics (long chatId, boolean viewAsTopics) {
    if (!viewAsTopics) tdlib.ui().post(() -> ForumTopicUi.openViewMode(this, false));
  }

  @Override public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    if (chatId == getChatId()) runOnUiThreadOptional(this::rebindRows);
  }
  @Override public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    if (tgx.td.Td.matchesScope(tdlib.chatType(getChatId()), scope)) runOnUiThreadOptional(this::rebindRows);
  }

  private void rebindRows () {
    if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount(), ForumTopicListDiff.CONTENT_PAYLOAD);
    updateSelectionHeader();
  }

  @Override public void onConfigurationChanged (Configuration configuration) {
    super.onConfigurationChanged(configuration);
    rebindRows();
    if (getRecyclerView() != null) getRecyclerView().requestLayout();
  }

  @Override public void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    rebindRows();
  }

  @Override public void onScrollToTopRequested () {
    if (getRecyclerView() != null) getRecyclerView().smoothScrollToPosition(0);
  }

  @Override public void onPerformUserCleanup () {
    Object epoch = lifecycleEpoch = new Object();
    tdlib.ui().post(() -> {
      if (isDestroyed() || lifecycleEpoch != epoch) return;
      closeSession(); snapshot = null;
      clearTopicSelection();
      if (topicUi != null) topicUi.cancelPendingActions();
      if (adapter != null) adapter.update(null);
    });
  }
  @Override public void onPerformRestart () { onPerformUserCleanup(); }
  @Override public void onPerformStartup (boolean afterRestart) {
    Object epoch = lifecycleEpoch = new Object();
    tdlib.ui().post(() -> {
      if (!isDestroyed() && lifecycleEpoch == epoch && adapter != null) openSession();
    });
  }

  @Override public void destroy () {
    closeSession();
    for (ForumTopicStore.TopicSubscription observer : selectedObservers.values()) observer.close();
    selectedObservers.clear();
    if (chatHeader != null) chatHeader.performDestroy();
    if (createButton != null) createButton.performDestroy();
    if (subscribed) {
      tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
      tdlib.listeners().unsubscribeFromSettingsUpdates(this);
      tdlib.listeners().removeCleanupListener(this);
      if (supergroupId != 0) tdlib.cache().unsubscribeFromSupergroupUpdates(supergroupId, this);
      tdlib.closeChat(getChatId(), this, false);
      subscribed = false;
    }
    if (getRecyclerView() != null) getRecyclerView().setAdapter(null);
    super.destroy();
  }

  @Override public boolean saveInstanceState (Bundle out, String prefix) {
    super.saveInstanceState(out, prefix);
    out.putLong(prefix+"forum_chat", getChatId());
    out.putString(prefix+"forum_list", getArgumentsStrict().chatList != null ? TD.makeChatListKey(getArgumentsStrict().chatList) : "");
    out.putString(prefix+"forum_query", query);
    out.putInt(prefix+"forum_selection_account", tdlib.id());
    java.util.LinkedHashSet<Integer> savedSelection = new java.util.LinkedHashSet<>();
    if (selection != null) for (int id : selection.ids()) savedSelection.add(id);
    if (restoredSelection != null) for (int id : restoredSelection) savedSelection.add(id);
    out.putIntArray(prefix+"forum_selection", savedSelection.stream().mapToInt(Integer::intValue).toArray());
    return true;
  }
  @Override public boolean restoreInstanceState (Bundle in, String prefix) {
    long chatId = in.getLong(prefix+"forum_chat");
    if (chatId == 0 || tdlib.chatSync(chatId) == null || !tdlib.isForum(chatId)) return false;
    setArguments(new Arguments(chatId, TD.chatListFromKey(in.getString(prefix+"forum_list"))));
    query = in.getString(prefix+"forum_query", "");
    restoreSearch = !query.isEmpty();
    restorePosition = in.getInt(prefix+"base_scroll_position", -1);
    restoreOffset = in.getInt(prefix+"base_scroll_offset", 0);
    if (in.getInt(prefix+"forum_selection_account", -1) == tdlib.id()) restoredSelection = in.getIntArray(prefix+"forum_selection");
    super.restoreInstanceState(in, prefix);
    return true;
  }

  private final class TopicAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<TdApi.ForumTopic> topics = Collections.emptyList();
    TopicAdapter () { setHasStableIds(true); }
    @Override public long getItemId (int position) { return position < topics.size() ? topics.get(position).info.forumTopicId : Long.MIN_VALUE; }
    @Override public int getItemCount () { return topics.size()+1; }
    @Override public int getItemViewType (int position) { return position < topics.size() ? 0 : 1; }

    void update (ForumTopicStore.Snapshot value) {
      List<TdApi.ForumTopic> old = topics;
      List<TdApi.ForumTopic> next = value != null ? value.topics : Collections.emptyList();
      DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new ForumTopicListDiff(old, next));
      topics = next;
      diff.dispatchUpdatesTo(this);
      if (selection != null) {
        selection.update(next);
        if (restoredSelection != null && value != null && value.initialized) {
          ArrayList<Integer> remaining = new ArrayList<>();
          for (int id : restoredSelection) {
            TdlibForumTopicManager.Entry entry = tdlib.topics().find(new TdlibForumTopicManager.Key(getChatId(), id));
            if (entry != null && entry.value != null && selection.add(entry.value)) observeSelected(id);
            else if (!value.endReached) remaining.add(id);
          }
          restoredSelection = remaining.isEmpty() ? null : remaining.stream().mapToInt(Integer::intValue).toArray();
        }
        updateSelectionHeader();
      }
    }

    @Override public RecyclerView.ViewHolder onCreateViewHolder (ViewGroup parent, int type) {
      View view;
      if (type == 0) {
        ForumTopicView row = new ForumTopicView(parent.getContext(), tdlib);
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> {
          TdApi.ForumTopic topic = row.getTopic();
          if (topic != null && selection != null && !selection.isEmpty()) { toggleSelection(topic); return; }
          if (topic != null && !context().isNavigationBusy()) {
            tdlib.ui().openChat(ForumTopicsController.this, getChatId(), new TdlibUi.ChatOpenParameters().chatList(getArgumentsStrict().chatList).messageTopic(new TdApi.MessageTopicForum(topic.info.forumTopicId)).keepStack());
          }
        });
        row.setOnLongClickListener(v -> {
          if (row.getTopic() == null) return false;
          toggleSelection(row.getTopic());
          return true;
        });
        view = row;
      } else {
        TextView footer = new TextView(parent.getContext());
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(14);
        // The Create button must never cover the last selectable row, including an empty footer.
        footer.setPadding(Screen.dp(20), Screen.dp(24), Screen.dp(20), Screen.dp(88));
        footer.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addThemeTextColorListener(footer, ColorId.textLight);
        footer.setOnClickListener(v -> { if (session != null) session.retry(); });
        view = footer;
      }
      addThemeInvalidateListener(view);
      return new RecyclerView.ViewHolder(view) { };
    }

    @Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position) {
      if (holder.itemView instanceof ForumTopicView) {
        ((ForumTopicView) holder.itemView).setTopic(topics.get(position));
        ((ForumTopicView) holder.itemView).setSelection(selection != null && !selection.isEmpty(), selection != null && selection.contains(topics.get(position).info.forumTopicId), false);
      } else {
        TextView footer = (TextView) holder.itemView;
        int text = snapshot == null || snapshot.loadingInitial || snapshot.loadingMore || snapshot.refreshing ? R.string.ForumTopicsLoading :
          snapshot.error != null ? R.string.ForumTopicsLoadFailed : topics.isEmpty() ? R.string.ForumTopicsEmpty : snapshot.stale ? R.string.ForumTopicsStale : 0;
        footer.setText(text != 0 ? Lang.getString(text) : "");
        footer.setTextColor(Theme.textDecentColor());
        footer.setEnabled(snapshot != null && (snapshot.error != null || snapshot.stale));
      }
    }
    @Override public void onBindViewHolder (RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
      if (payloads.contains(ForumTopicListDiff.SELECTION_PAYLOAD) && holder.itemView instanceof ForumTopicView && payloads.size() == 1) {
        ((ForumTopicView) holder.itemView).setSelection(selection != null && !selection.isEmpty(), selection != null && selection.contains(topics.get(position).info.forumTopicId), true);
      } else onBindViewHolder(holder, position);
    }
    @Override public void onViewRecycled (RecyclerView.ViewHolder holder) {
      if (holder.itemView instanceof ForumTopicView) ((ForumTopicView) holder.itemView).clear();
    }
  }
}
