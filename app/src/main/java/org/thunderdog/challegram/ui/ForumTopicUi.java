package org.thunderdog.challegram.ui;

import android.app.AlertDialog;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.data.ForumTopicSelection.Action;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ForumPresentation;
import org.thunderdog.challegram.data.ForumNavigation;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ForumTopicActions;
import org.thunderdog.challegram.telegram.ForumTopicStore;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared topic actions for the list and the history header. Never mutates a cached TDLib value. */
public final class ForumTopicUi implements ChatListener {
  private final ViewController<?> owner;
  private final Tdlib tdlib;
  private final long chatId;
  private boolean busy;
  private ForumTopicStore.ListSession pinsSession;
  private Boolean pendingViewMode;
  private boolean modeAccepted;
  private int navigationGeneration;
  private boolean navigatingTabs;
  private boolean destroyed;
  private ForumTopicActions.Batch batch;
  private AlertDialog batchProgress;

  public ForumTopicUi (ViewController<?> owner, long chatId) {
    this.owner = owner;
    this.tdlib = owner.tdlib();
    this.chatId = chatId;
    tdlib.listeners().subscribeToChatUpdates(chatId, this);
    owner.addDestroyListener(this::destroy);
  }

  public void destroy () {
    if (destroyed) return;
    destroyed = true;
    ++navigationGeneration;
    if (batch != null) { batch.cancel(); batch = null; }
    if (batchProgress != null) { batchProgress.dismiss(); batchProgress = null; }
    pendingViewMode = null;
    if (pinsSession != null) { pinsSession.close(); pinsSession = null; }
    tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
  }

  private boolean isActive () { return !destroyed && !owner.isDestroyed() && owner.getChatId() == chatId; }
  public boolean isBusy () { return busy; }

  public void cancelPendingActions () {
    ++navigationGeneration;
    navigatingTabs = false;
    if (batch != null) { batch.cancel(); batch = null; }
    if (batchProgress != null) { batchProgress.dismiss(); batchProgress = null; }
    if (pinsSession != null) { pinsSession.close(); pinsSession = null; }
    pendingViewMode = null; busy = false;
  }

  private TdlibForumTopicManager.Key key (int id) { return new TdlibForumTopicManager.Key(chatId, id); }
  private TdApi.ChatMemberStatus status () { return tdlib.chatStatus(chatId); }
  private TdApi.ForumTopic topic (int id) {
    TdlibForumTopicManager.Entry entry = tdlib.topics().find(key(id));
    return entry != null ? entry.value : null;
  }
  public boolean canCreate () {
    TdApi.Chat chat = tdlib.chat(chatId);
    return chat != null && tdlib.isForum(chatId) && ForumTopicPolicy.canCreate(status(), chat.permissions);
  }
  public boolean canEdit (int id) {
    TdApi.ForumTopic topic = topic(id);
    return topic != null && ForumTopicPolicy.canEdit(status(), topic.info);
  }
  private boolean check (boolean allowed) {
    if (!allowed) error(Lang.getString(R.string.ForumActionUnavailable));
    return allowed;
  }
  private void error (String message) {
    if (isActive()) owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.ForumTopicTitle)).setMessage(message).setPositiveButton(Lang.getString(R.string.OK), null));
  }
  private void run (Consumer<ForumTopicActions.Callback<TdApi.Ok>> action) {
    if (busy || !isActive()) return;
    busy = true;
    action.accept((ok, failure) -> {
      busy = false;
      if (failure != null) error(failure.message);
      // Ok only means accepted: the store refreshes full topics and lists.
    });
  }
  private void menu (String title, List<String> labels, List<Runnable> actions) {
    if (isActive() && !busy && !navigatingTabs) owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(title).setItems(labels.toArray(new String[0]), (dialog, which) -> {
        if (isActive() && !busy && !navigatingTabs) actions.get(which).run();
      }).setNegativeButton(Lang.getString(R.string.Cancel), null));
  }
  private static void add (List<String> labels, List<Runnable> actions, int label, Runnable action) {
    labels.add(Lang.getString(label)); actions.add(action);
  }

  public void showListMenu (Runnable refresh) {
    ArrayList<String> labels = new ArrayList<>(); ArrayList<Runnable> actions = new ArrayList<>();
    if (canCreate()) add(labels, actions, R.string.ForumCreateTopic, () -> edit(0));
    if (tdlib.canInviteUsers(tdlib.chat(chatId))) add(labels, actions, R.string.AddMember, this::addMembers);
    add(labels, actions, R.string.ForumRefresh, refresh);
    add(labels, actions, R.string.ForumShowAllMessages, () -> setViewMode(false));
    menu(tdlib.chatTitle(chatId), labels, actions);
  }

  public void showTopicMenu (int id) {
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null)) return;
    ArrayList<String> labels = new ArrayList<>(); ArrayList<Runnable> actions = new ArrayList<>();
    if (canEdit(id)) {
      add(labels, actions, R.string.ForumEditTopic, () -> edit(id));
      add(labels, actions, t.info.isClosed ? R.string.ForumReopenTopic : R.string.ForumCloseTopic, () -> setClosed(id, !t.info.isClosed));
    }
    if (ForumTopicPolicy.canManage(status())) {
      if (t.info.isGeneral) add(labels, actions, t.info.isHidden ? R.string.ForumShowGeneral : R.string.ForumHideGeneral, () -> {
        TdApi.ForumTopic current = topic(id);
        if (check(current != null && ForumTopicPolicy.canManage(status()))) run(cb -> tdlib.topics().actions.setGeneralHidden(chatId, !t.info.isHidden, cb));
      });
      add(labels, actions, t.isPinned ? R.string.ForumUnpinTopic : R.string.ForumPinTopic, () -> pin(id, !t.isPinned));
      if (t.isPinned) {
        add(labels, actions, R.string.ForumMovePinUp, () -> movePin(id, -1));
        add(labels, actions, R.string.ForumMovePinDown, () -> movePin(id, 1));
      }
    }
    add(labels, actions, R.string.ForumNotifications, () -> notifications(id));
    add(labels, actions, R.string.ForumCopyTopicLink, () -> copyLink(id));
    add(labels, actions, R.string.ForumShowAllMessages, () -> setViewMode(false));
    add(labels, actions, R.string.ForumShowTopics, () -> setViewMode(true));
    if (t.unreadMentionCount > 0) add(labels, actions, R.string.ForumReadMentions, () -> run(cb -> tdlib.topics().actions.readAllMentions(key(id), cb)));
    if (t.unreadReactionCount > 0) add(labels, actions, R.string.ForumReadReactions, () -> run(cb -> tdlib.topics().actions.readAllReactions(key(id), cb)));
    if (t.unreadPollVoteCount > 0) {
      labels.add(Lang.getString(R.string.ForumUnreadPollVotes, t.unreadPollVoteCount)); actions.add(() -> openUnreadPollVote(id));
      add(labels, actions, R.string.ForumReadPollVotes, () -> run(cb -> tdlib.topics().actions.readAllPollVotes(key(id), cb)));
    }
    add(labels, actions, R.string.ForumGroupProfile, () -> tdlib.ui().openChatProfile(owner, chatId, null, null));
    if (ForumTopicPolicy.canOfferDelete(status(), t.info)) add(labels, actions,
      t.info.isGeneral ? R.string.ForumClearGeneral : R.string.ForumDeleteTopic, () -> confirmDelete(id));
    menu(t.info.name, labels, actions);
  }

  public void setViewMode (boolean topics) {
    if (busy || navigatingTabs || !isActive()) return;
    if (ForumTabsNavigation.isTabForum(owner, chatId)) {
      pendingViewMode = null;
      modeAccepted = false;
      // Keep this separate from mutation busy: the host checks isBusy() before
      // switching tabs, including when this command originates on that host.
      navigatingTabs = true;
      final int generation = ++navigationGeneration;
      ForumTabsNavigation.openViewMode(owner, chatId, topics,
        () -> isActive() && generation == navigationGeneration,
        () -> { if (generation == navigationGeneration) navigatingTabs = false; });
      return;
    }
    pendingViewMode = topics; modeAccepted = false;
    run(cb -> tdlib.topics().actions.setViewAsTopics(chatId, topics, (ok, failure) -> {
      cb.onResult(ok, failure);
      if (failure != null) pendingViewMode = null;
      else { modeAccepted = true; applyViewMode(); }
    }));
  }

  @Override public void onChatViewAsTopics (long chatId, boolean viewAsTopics) {
    tdlib.ui().post(this::applyViewMode);
  }

  private void applyViewMode () {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (pendingViewMode == null || !modeAccepted || chat == null || chat.viewAsTopics != pendingViewMode ||
        !isActive() || !owner.isFocused()) return;
    boolean topics = pendingViewMode;
    pendingViewMode = null;
    openViewMode(owner, topics);
  }

  /** Classic mode follows the authoritative preference; tabforums use only local selection. */
  public static void openViewMode (ViewController<?> owner, boolean topics) {
    openViewMode(owner, topics, 8);
  }

  private static void openViewMode (ViewController<?> owner, boolean topics, int retries) {
    if (owner.isDestroyed() || !owner.isFocused() || owner.context().navigation().getCurrentStackItem() != owner) return;
    if (ForumTabsNavigation.isTabForum(owner, owner.getChatId())) {
      ForumTabsNavigation.openViewMode(owner, owner.getChatId(), topics, () -> !owner.isDestroyed(), () -> { });
      return;
    }
    TdApi.Chat chat = owner.tdlib().chat(owner.getChatId());
    if (chat == null || chat.viewAsTopics != topics) return;
    if (owner.context().isNavigationBusy()) {
      if (retries > 0) owner.tdlib().ui().postDelayed(() -> openViewMode(owner, topics, retries - 1), 150);
      return;
    }
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters();
    if (owner instanceof MessagesController) params.chatList(((MessagesController) owner).chatList());
    else if (owner instanceof ForumTopicsController) params.chatList(((ForumTopicsController) owner).getArgumentsStrict().chatList);
    else if (owner instanceof ForumTopicProfileController) params.chatList(((ForumTopicProfileController) owner).getChatList());
    if (!topics) params.forumMessages();
    owner.tdlib().ui().openChat(owner, chat.id, params);
  }

  public void copyLink (int id) {
    if (busy) return;
    busy = true;
    tdlib.topics().actions.getLink(key(id), (link, failure) -> {
      busy = false;
      if (!isActive()) return;
      if (failure != null) error(failure.message);
      else UI.copyText(link.link, R.string.CopiedLink);
    });
  }

  public void openUnreadPollVote (int id) {
    if (busy) return;
    busy = true;
    tdlib.send(ForumPresentation.unreadPollVotes(chatId, id), (found, failure) -> tdlib.ui().post(() -> {
      busy = false;
      if (!isActive()) return;
      if (failure != null) error(failure.message);
      else if (found.messages.length > 0 && ForumNavigation.forumTopic(chatId, found.messages[0]) instanceof TdApi.MessageTopicForum &&
          ((TdApi.MessageTopicForum) found.messages[0].topicId).forumTopicId == id) {
        tdlib.ui().openChat(owner, chatId, new TdlibUi.ChatOpenParameters().messageTopic(new TdApi.MessageTopicForum(id)).highlightMessage(found.messages[0]).keepStack());
      } else error(Lang.getString(R.string.ForumNoUnreadPollVotes));
    }));
  }

  public void confirmDelete (int id) {
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null && ForumTopicPolicy.canOfferDelete(status(), t.info))) return;
    ArrayList<ForumTopicActions.BatchTarget> targets = new ArrayList<>();
    targets.add(new ForumTopicActions.BatchTarget(id, t.info.name));
    runBatch(t.info.isGeneral ? Action.CLEAR_GENERAL : Action.DELETE, targets, null);
  }

  private void withPins (Consumer<List<TdApi.ForumTopic>> action) {
    if (!check(ForumTopicPolicy.canManage(status())) || busy) return;
    busy = true;
    pinsSession = tdlib.topics().openList(chatId, "", value -> {
      if (!isActive() || pinsSession == null) return;
      if (value.error != null) {
        pinsSession.close(); pinsSession = null; busy = false; error(value.error.message);
      } else if (value.initialized && !value.refreshing && !value.stale) {
        if (!ForumTopicPolicy.completePinnedPrefix(value.topics, value.endReached)) { pinsSession.loadMore(); return; }
        pinsSession.close(); pinsSession = null; busy = false;
        if (check(ForumTopicPolicy.canManage(status()))) action.accept(value.topics);
      }
    });
    pinsSession.refresh();
  }

  public void pin (int id, boolean pinnedState) {
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null && ForumTopicPolicy.canManage(status()))) return;
    ArrayList<ForumTopicActions.BatchTarget> targets = new ArrayList<>();
    targets.add(new ForumTopicActions.BatchTarget(id, t.info.name));
    runBatch(pinnedState ? Action.PIN : Action.UNPIN, targets, null);
  }
  public void movePin (int id, int direction) {
    withPins(topics -> {
      int[] order = ForumTopicPolicy.movePin(topics, id, direction);
      if (order == null) { error(Lang.getString(R.string.ForumPinAtEdge)); return; }
      run(cb -> tdlib.topics().actions.setPinnedOrder(chatId, order, cb));
    });
  }

  public void notifications (int id) {
    String[] labels = {Lang.getString(R.string.ForumNotificationsDefault), Lang.getString(R.string.ForumNotificationsOn),
      Lang.getString(R.string.ForumMuteHour), Lang.getString(R.string.ForumMuteDay), Lang.getString(R.string.ForumMuteForever)};
    int[] durations = {0, 0, 3600, 86400, Integer.MAX_VALUE};
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null && t.notificationSettings != null)) return;
    String state = Lang.getString(t.notificationSettings.useDefaultMuteFor ? R.string.ForumNotificationsDefault :
      t.notificationSettings.muteFor > 0 ? R.string.ForumNotificationsMuted : R.string.ForumNotificationsOn);
    owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.ForumNotificationsState, state))
      .setItems(labels, (dialog, which) -> {
        TdApi.ForumTopic current = topic(id);
        if (check(current != null && current.notificationSettings != null)) run(cb -> tdlib.topics().actions.setNotifications(key(id),
          ForumTopicPolicy.notifications(current.notificationSettings, which == 0, durations[which]), cb));
      }).setNegativeButton(Lang.getString(R.string.Cancel), null));
  }

  /** Stable entry point; the shared full-screen editor replaces edit() at integration. */
  public void openEditor (int id) { edit(id); }

  /** Explicit override: unmute never re-inherits a muted group, and all other fields survive. */
  public void setMuted (int id, boolean muted) {
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null)) return;
    ArrayList<ForumTopicActions.BatchTarget> targets = new ArrayList<>();
    targets.add(new ForumTopicActions.BatchTarget(id, t.info.name));
    runBatch(muted ? Action.MUTE : Action.UNMUTE, targets, null);
  }

  public void setClosed (int id, boolean closed) {
    TdApi.ForumTopic t = topic(id);
    if (!check(t != null)) return;
    ArrayList<ForumTopicActions.BatchTarget> targets = new ArrayList<>();
    targets.add(new ForumTopicActions.BatchTarget(id, t.info.name));
    runBatch(closed ? Action.CLOSE : Action.OPEN, targets, null);
  }

  public static int actionLabel (Action action) {
    switch (action) {
      case PIN: return R.string.ForumPinTopic;
      case UNPIN: return R.string.ForumUnpinTopic;
      case MUTE: return R.string.ForumSelectionMute;
      case UNMUTE: return R.string.ForumSelectionUnmute;
      case CLOSE: return R.string.ForumCloseTopic;
      case OPEN: return R.string.ForumReopenTopic;
      case DELETE: return R.string.ForumDeleteTopic;
      case CLEAR_GENERAL: return R.string.ForumClearGeneral;
      case READ_MENTIONS: return R.string.ForumReadMentions;
      case READ_REACTIONS: return R.string.ForumReadReactions;
      case READ_POLL_VOTES: return R.string.ForumReadPollVotes;
      default: throw new IllegalArgumentException();
    }
  }

  private String batchError (TdApi.Error error) {
    switch (error.code) {
      case ForumTopicActions.BATCH_UNAVAILABLE: return Lang.getString(R.string.ForumActionUnavailable);
      case ForumTopicActions.BATCH_PIN_LIMIT: return Lang.getString(R.string.ForumPinLimit, tdlib.options().pinnedForumTopicCountMax);
      case ForumTopicActions.BATCH_PIN_PREFIX: return Lang.getString(R.string.ForumSelectionPinsUnavailable);
      case ForumTopicActions.BATCH_PREFLIGHT_ABORTED: return Lang.getString(R.string.ForumActionUnavailable);
      default: return ForumTopicActionPresentation.readableError(error, TD.translateError(error.code, error.message), Lang.getString(R.string.LaunchSubtitleFatalError));
    }
  }

  /** Confirmation and retry always use this immutable target list, never the current selection. */
  public void runBatch (Action action, List<ForumTopicActions.BatchTarget> input, Consumer<ForumTopicActions.BatchResult> completed) {
    if (!isActive() || busy || input.isEmpty()) return;
    ArrayList<ForumTopicActions.BatchTarget> targets = new ArrayList<>(input);
    Runnable start = () -> startBatch(action, targets, completed);
    if (!action.isDestructive()) { start.run(); return; }
    StringBuilder scope = new StringBuilder(Lang.getString(action == Action.CLEAR_GENERAL ? R.string.ForumSelectionClearConfirm : R.string.ForumSelectionDeleteConfirm, targets.size()));
    for (ForumTopicActions.BatchTarget target : targets) scope.append("\n• ").append(target.name);
    owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(actionLabel(action))).setMessage(scope)
      .setNegativeButton(Lang.getString(R.string.Cancel), null)
      .setPositiveButton(Lang.getString(actionLabel(action)), (dialog, which) -> start.run()));
  }

  private void startBatch (Action action, List<ForumTopicActions.BatchTarget> targets, Consumer<ForumTopicActions.BatchResult> completed) {
    if (!isActive() || busy) return;
    busy = true;
    batchProgress = owner.showAlert(new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(actionLabel(action))).setMessage(Lang.getString(R.string.ForumSelectionProgress, 0, targets.size())).setCancelable(false));
    batch = tdlib.topics().actions.startBatch(chatId, tdlib.myUserId(), action, targets, new ForumTopicActions.BatchCallback() {
      @Override public void onProgress (int done, int total) {
        if (isActive() && batchProgress != null) batchProgress.setMessage(Lang.getString(R.string.ForumSelectionProgress, done, total));
      }
      @Override public void onComplete (ForumTopicActions.BatchResult result) {
        busy = false; batch = null;
        if (batchProgress != null) { batchProgress.dismiss(); batchProgress = null; }
        if (!isActive()) return;
        if (completed != null) completed.accept(result);
        showBatchError(result, completed);
      }
    });
    if (batch == null && busy) {
      busy = false;
      if (batchProgress != null) { batchProgress.dismiss(); batchProgress = null; }
      error(Lang.getString(R.string.ForumSelectionBusy));
    }
  }

  private void showBatchError (ForumTopicActions.BatchResult result, Consumer<ForumTopicActions.BatchResult> completed) {
    ForumTopicActionPresentation.BatchFailure failure = ForumTopicActionPresentation.failure(result);
    if (failure == null) return;
    String text = failure.error != null ? batchError(failure.error) : Lang.getString(R.string.LaunchSubtitleFatalError);
    if (failure.uncertain) {
      String warning = Lang.getString(R.string.ForumSelectionUncertain);
      text = failure.retryTargets.isEmpty() ? warning : text + "\n\n" + warning;
    }
    AlertDialog.Builder dialog = new AlertDialog.Builder(owner.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.Error)).setMessage(text).setPositiveButton(Lang.getString(R.string.OK), null);
    if (!failure.retryTargets.isEmpty()) dialog.setNeutralButton(Lang.getString(R.string.ForumSelectionRetryFailed), (d, which) -> runBatch(result.action, failure.retryTargets, completed));
    owner.showAlert(dialog);
  }

  /** Existing picker and shared member-status API; no admin-assignment screen or parallel member store. */
  public void addMembers () {
    if (!isActive() || busy || !check(tdlib.canInviteUsers(tdlib.chat(chatId)))) return;
    ContactsController picker = new ContactsController(owner.context(), tdlib);
    picker.initWithMode(ContactsController.MODE_ADD_MEMBER);
    picker.setAllowBots(true); picker.setAllowChats(false, false);
    picker.setChatTitle(R.string.AddMember, tdlib.chatTitle(chatId));
    boolean[] adding = {false};
    picker.setArguments(new ContactsController.Args((context, view, sender) -> {
      if (adding[0] || !(sender instanceof TdApi.MessageSenderUser)) return true;
      if (!tdlib.canInviteUsers(tdlib.chat(chatId))) { error(Lang.getString(R.string.ForumActionUnavailable)); return true; }
      adding[0] = true;
      tdlib.send(new TdApi.GetChatMember(chatId, sender), (member, failure) -> tdlib.ui().post(() -> {
        adding[0] = false;
        if (context.isDestroyed() || !isActive()) return;
        if (failure != null || TD.isMember(member.status)) {
          context.context().tooltipManager().builder(view).show(context, tdlib, R.drawable.baseline_info_24,
            failure != null ? TD.toErrorString(failure) : Lang.getString(R.string.XIsAlreadyInChat, tdlib.senderName(sender)));
          return;
        }
        context.showAlert(new AlertDialog.Builder(context.context(), Theme.dialogTheme())
          .setTitle(Lang.getString(R.string.AddMember)).setMessage(Lang.getString(R.string.AddToTheGroup, tdlib.senderName(sender)))
          .setNegativeButton(Lang.getString(R.string.Cancel), null)
          .setPositiveButton(Lang.getString(R.string.AddMember), (d, which) -> {
            if (adding[0] || context.isDestroyed() || !tdlib.canInviteUsers(tdlib.chat(chatId))) return;
            adding[0] = true;
            tdlib.setChatMemberStatus(chatId, sender, new TdApi.ChatMemberStatusMember(), member.status, (success, error, failedToAdd) -> tdlib.ui().post(() -> {
              adding[0] = false;
              if (context.isDestroyed()) return;
              if (success) context.navigateBack();
              else context.context().tooltipManager().builder(view).show(context, tdlib, R.drawable.baseline_error_24,
                error != null && TD.ERROR_USER_PRIVACY.equals(error.message) ? Lang.getString(R.string.errorPrivacyAddMember) : TD.toErrorString(error));
            }));
          }));
      }));
      return true;
    }));
    owner.navigateTo(picker);
  }

  private void edit (int id) {
    if (!(id == 0 ? canCreate() : canEdit(id))) return;
    ForumTopicEditController.open(owner, chatId, id);
  }
}
