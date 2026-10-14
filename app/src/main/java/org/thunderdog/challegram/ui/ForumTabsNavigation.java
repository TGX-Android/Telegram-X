package org.thunderdog.challegram.ui;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ForumTopicEditorState;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Local tab commands. A tab selection is never a server-side viewAsTopics preference. */
final class ForumTabsNavigation {
  private ForumTabsNavigation () { }

  enum Result { RETURNED, QUEUED, RETRY, UNAVAILABLE }

  static boolean isTabForum (ViewController<?> owner, long chatId) {
    return owner.tdlib().isForum(chatId) && owner.tdlib().hasForumTabs(chatId);
  }

  private static boolean sameChat (ViewController<?> owner, ViewController<?> item, long chatId) {
    return item != null && !item.isDestroyed() && item.tdlib() == owner.tdlib() && item.getChatId() == chatId;
  }

  static @Nullable MessagesController findHost (ViewController<?> owner, long chatId) {
    NavigationController navigation = owner.context().navigation();
    if (owner.isDestroyed() || navigation == null || !isTabForum(owner, chatId)) return null;
    NavigationStack stack = navigation.getStack();
    int index = nearestHostIndex(stack.getAll(), stack.indexOf(owner), item ->
      sameChat(owner, item, chatId) && item instanceof MessagesController && ((MessagesController) item).isForumTabsHost());
    return index >= 0 ? (MessagesController) stack.get(index) : null;
  }

  static boolean isLiveHost (ViewController<?> owner, long chatId, @Nullable MessagesController host) {
    if (!sameChat(owner, host, chatId) || !isTabForum(owner, chatId) || !host.isForumTabsHost()) return false;
    NavigationController navigation = owner.context().navigation();
    if (navigation == null) return false;
    NavigationStack stack = navigation.getStack();
    int hostIndex = stack.indexOf(host), ownerIndex = stack.indexOf(owner);
    return hostIndex >= 0 && ownerIndex >= hostIndex;
  }

  private static boolean isCurrent (ViewController<?> owner) {
    NavigationController navigation = owner.context().navigation();
    return !owner.isDestroyed() && owner.isFocused() && navigation != null && navigation.getCurrentStackItem() == owner;
  }

  private static boolean canRemovePage (ViewController<?> owner, ViewController<?> item, long chatId) {
    if (!sameChat(owner, item, chatId)) return false;
    if (item instanceof ForumTopicEditController) return ((ForumTopicEditController) item).canReturnToForumTabs();
    // A group profile can itself be an editor or have an active selection. Probe
    // its non-committing Back handler before treating it as a disposable profile.
    return (item instanceof ForumTopicProfileController || item instanceof ProfileController) && !item.performOnBackPressed(true, false);
  }

  /** Queue before exposing the host; never remove another chat, account or screen kind. */
  static Result requestAndReturn (ViewController<?> owner, MessagesController host, int topicId) {
    if (topicId < 0 || !isCurrent(owner) || !isLiveHost(owner, owner.getChatId(), host)) return Result.UNAVAILABLE;
    NavigationController navigation = owner.context().navigation();
    NavigationStack stack = navigation.getStack();
    if (owner.context().isNavigationBusy() || stack.isLocked()) return Result.RETRY;
    int ownerIndex = stack.indexOf(owner), hostIndex = stack.indexOf(host);
    boolean canReturn = canUnwind(stack.getAll(), ownerIndex, hostIndex, item -> canRemovePage(owner, item, host.getChatId()));
    if (!host.requestForumTab(topicId)) return Result.UNAVAILABLE;
    if (owner == host) return Result.RETURNED;
    // Keep the selection queued for the user's eventual Back navigation. In
    // particular, do not open a duplicate host to jump over unrelated history.
    if (!canReturn) return Result.QUEUED;
    for (int i = ownerIndex - 1; i > hostIndex; i--) {
      ViewController<?> intermediate = stack.get(i);
      if (intermediate.isAttachedToNavigationController()) navigation.removeChildWrapper(intermediate);
      stack.destroy(i);
    }
    // Leave the visible owner to NavigationProcessor: it needs the outgoing
    // view throughout the Back animation and will destroy it on completion.
    return owner.navigateBack() ? Result.RETURNED : Result.RETRY;
  }

  static void openViewMode (ViewController<?> owner, long chatId, boolean topics, BooleanSupplier active, Runnable finished) {
    openViewMode(owner, chatId, topics, active, finished, 8);
  }

  private static void openViewMode (ViewController<?> owner, long chatId, boolean topics, BooleanSupplier active, Runnable finished, int retries) {
    if (!active.getAsBoolean() || !isCurrent(owner) || owner.getChatId() != chatId || !isTabForum(owner, chatId)) {
      finished.run();
      return;
    }
    NavigationController navigation = owner.context().navigation();
    Result result = Result.RETRY;
    if (!owner.context().isNavigationBusy() && !navigation.getStack().isLocked()) {
      MessagesController host = findHost(owner, chatId);
      int target = viewModeTopic(topics, ownerTopicId(owner), host != null ? host.getSelectedForumTabId() : 0);
      if (host == null) {
        // Explicit All bypasses any remembered topic; a typed target bypasses
        // the classic topic-list route. Keep unrelated navigation history intact.
        owner.tdlib().ui().openChat(owner, chatId, parameters(owner, target).onDone(finished));
        return;
      }
      result = requestAndReturn(owner, host, target);
    }
    if (result == Result.RETRY && retries > 0) {
      owner.tdlib().ui().postDelayed(() -> openViewMode(owner, chatId, topics, active, finished, retries - 1), 150);
    } else {
      finished.run();
    }
  }

  private static int ownerTopicId (ViewController<?> owner) {
    if (owner instanceof ForumTopicProfileController) return ((ForumTopicProfileController) owner).getForumTopicId();
    if (owner instanceof ForumTopicEditController) return ((ForumTopicEditController) owner).getArgumentsStrict().topicId;
    if (owner instanceof MessagesController) {
      MessagesController messages = (MessagesController) owner;
      if (messages.isForumTabsHost()) return messages.getSelectedForumTabId();
      TdApi.MessageTopic topic = messages.getMessageTopicId();
      if (topic instanceof TdApi.MessageTopicForum) return ((TdApi.MessageTopicForum) topic).forumTopicId;
    }
    return 0;
  }

  private static TdlibUi.ChatOpenParameters parameters (ViewController<?> owner, int topicId) {
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack();
    if (owner instanceof MessagesController) params.chatList(((MessagesController) owner).chatList());
    else if (owner instanceof ForumTopicProfileController) params.chatList(((ForumTopicProfileController) owner).getChatList());
    else if (owner instanceof ForumTopicsController) params.chatList(((ForumTopicsController) owner).getArgumentsStrict().chatList);
    if (topicId > 0) params.messageTopic(new TdApi.MessageTopicForum(topicId));
    else params.forumMessages();
    return params;
  }

  // Pure policy seams: tests use synthetic stack entries, never Android views.
  static int viewModeTopic (boolean topics, int ownerTopicId, int hostTopicId) {
    return topics ? (ownerTopicId > 0 ? ownerTopicId : Math.max(0, hostTopicId)) : 0;
  }

  static <T> int nearestHostIndex (List<T> stack, int ownerIndex, Predicate<T> isHost) {
    if (ownerIndex < 0 || ownerIndex >= stack.size()) return -1;
    for (int i = ownerIndex; i >= 0; i--) if (isHost.test(stack.get(i))) return i;
    return -1;
  }

  static <T> boolean canUnwind (List<T> stack, int ownerIndex, int hostIndex, Predicate<T> removable) {
    if (hostIndex < 0 || ownerIndex < hostIndex || ownerIndex >= stack.size()) return false;
    for (int i = ownerIndex; i > hostIndex; i--) if (!removable.test(stack.get(i))) return false;
    return true;
  }

  static boolean canLeaveEditor (@Nullable ForumTopicEditorState form) {
    return form != null && (form.phase() == ForumTopicEditorState.Phase.SUCCEEDED && form.completedTopicId() > 0 ||
      form.phase() == ForumTopicEditorState.Phase.READY && !form.hasChanges());
  }
}
