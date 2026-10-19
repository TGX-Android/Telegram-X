package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ForumTopicPolicy;
import org.thunderdog.challegram.data.ForumTopicSelection;
import org.thunderdog.challegram.data.ForumTopicSelection.Action;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-authoritative operations. Permission checks and confirmation dialogs belong to the UI. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class ForumTopicActions {
  public interface Callback<T extends TdApi.Object> {
    void onResult (@Nullable T value, @Nullable TdApi.Error error);
  }

  private interface ErrorCallback {
    void onResult (@Nullable TdApi.Error error);
  }

  private final ForumTopicStore store;

  public ForumTopicActions (ForumTopicStore store) {
    this.store = store;
  }

  private <T extends TdApi.Object> void send (TdApi.Function<T> request, long chatId, @Nullable Key key, int constructor, Callback<T> callback) {
    send(request, chatId, key, constructor, callback, true);
  }

  @SuppressWarnings("unchecked")
  private <T extends TdApi.Object> void send (TdApi.Function<T> request, long chatId, @Nullable Key key, int constructor, Callback<T> callback, boolean changesState) {
    store.perform(request, chatId, key, constructor, changesState, (value, error) -> callback.onResult((T) value, error));
  }

  public void create (long chatId, String name, boolean isNameImplicit, TdApi.ForumTopicIcon icon, Callback<TdApi.ForumTopicInfo> callback) {
    send(new TdApi.CreateForumTopic(chatId, name, isNameImplicit, icon), chatId, null, TdApi.ForumTopicInfo.CONSTRUCTOR, callback);
  }

  public void edit (Key key, String name, boolean editIconCustomEmoji, long customEmojiId, Callback<TdApi.Ok> callback) {
    send(new TdApi.EditForumTopic(key.chatId, key.forumTopicId, name, editIconCustomEmoji, customEmojiId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void setClosed (Key key, boolean closed, Callback<TdApi.Ok> callback) {
    send(new TdApi.ToggleForumTopicIsClosed(key.chatId, key.forumTopicId, closed), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void setGeneralHidden (long chatId, boolean hidden, Callback<TdApi.Ok> callback) {
    send(new TdApi.ToggleGeneralForumTopicIsHidden(chatId, hidden), chatId, new Key(chatId, 1), TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void setPinned (Key key, boolean pinned, Callback<TdApi.Ok> callback) {
    send(new TdApi.ToggleForumTopicIsPinned(key.chatId, key.forumTopicId, pinned), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void setPinnedOrder (long chatId, int[] topicIds, Callback<TdApi.Ok> callback) {
    send(new TdApi.SetPinnedForumTopics(chatId, topicIds.clone()), chatId, null, TdApi.Ok.CONSTRUCTOR, callback);
  }

  // TDLib decides whether the topic disappears or only its history is cleared (notably General).
  public void delete (Key key, Callback<TdApi.Ok> callback) {
    send(new TdApi.DeleteForumTopic(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void setNotifications (Key key, TdApi.ChatNotificationSettings settings, Callback<TdApi.Ok> callback) {
    send(new TdApi.SetForumTopicNotificationSettings(key.chatId, key.forumTopicId, settings), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void readAllMentions (Key key, Callback<TdApi.Ok> callback) {
    send(new TdApi.ReadAllForumTopicMentions(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void readAllReactions (Key key, Callback<TdApi.Ok> callback) {
    send(new TdApi.ReadAllForumTopicReactions(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void readAllPollVotes (Key key, Callback<TdApi.Ok> callback) {
    send(new TdApi.ReadAllForumTopicPollVotes(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void unpinAllMessages (Key key, Callback<TdApi.Ok> callback) {
    send(new TdApi.UnpinAllForumTopicMessages(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public void getLink (Key key, Callback<TdApi.MessageLink> callback) {
    send(new TdApi.GetForumTopicLink(key.chatId, key.forumTopicId), key.chatId, key, TdApi.MessageLink.CONSTRUCTOR, callback, false);
  }

  public void setViewAsTopics (long chatId, boolean viewAsTopics, Callback<TdApi.Ok> callback) {
    send(new TdApi.ToggleChatViewAsTopics(chatId, viewAsTopics), chatId, null, TdApi.Ok.CONSTRUCTOR, callback);
  }

  public static final class BatchTarget {
    public final int topicId;
    public final String name;
    public final boolean retrying;

    public BatchTarget (int topicId, String name) {
      this(topicId, name, false);
    }

    public BatchTarget (int topicId, String name, boolean retrying) {
      this.topicId = topicId;
      this.name = name;
      this.retrying = retrying;
    }
  }

  public static final class BatchOutcome {
    public final BatchTarget target;
    public final @Nullable TdApi.Error error;
    public final boolean uncertain;

    public BatchOutcome (BatchTarget target, @Nullable TdApi.Error error) {
      this(target, error, false);
    }

    public BatchOutcome (BatchTarget target, @Nullable TdApi.Error error, boolean uncertain) {
      this.target = target;
      this.error = error;
      this.uncertain = uncertain;
    }

    public boolean getSuccessful () {
      return error == null;
    }

    public boolean getRetryable () {
      return error != null && !uncertain;
    }
  }

  public static final class BatchResult {
    public final Action action;
    public final List<BatchOutcome> outcomes;

    public BatchResult (Action action, List<BatchOutcome> outcomes) {
      this.action = action;
      this.outcomes = new ArrayList<>(outcomes);
    }

    public List<BatchTarget> failedTargets () {
      List<BatchTarget> targets = new ArrayList<>();
      for (BatchOutcome outcome : outcomes) {
        if (outcome.getRetryable()) {
          targets.add(new BatchTarget(outcome.target.topicId, outcome.target.name, true));
        }
      }
      return targets;
    }
  }

  public interface BatchCallback {
    void onProgress (int completed, int total);
    void onComplete (BatchResult result);
  }

  private @Nullable Batch activeBatch;

  /** One account-wide runner, at most 100 targets, one request in flight. Never auto-retries a write. */
  public @Nullable Batch startBatch (long chatId, long selfUserId, Action action, List<BatchTarget> targets, BatchCallback callback) {
    if (activeBatch != null) return null;
    Batch batch = new Batch(chatId, selfUserId, action, new ArrayList<>(targets), callback);
    activeBatch = batch;
    batch.start();
    return batch;
  }

  public final class Batch {
    private final long chatId;
    private final long selfUserId;
    private final Action action;
    private final List<BatchTarget> targets;
    private final BatchCallback callback;
    private boolean cancelled;
    private boolean finished;
    private @Nullable TdApi.ChatMemberStatus status;
    private boolean groupMuted;
    private int pinLimit;
    private final HashMap<Integer, TdApi.Error> preflightErrors = new HashMap<>();
    private final HashSet<Integer> alreadyDeleted = new HashSet<>();
    private final ArrayList<BatchOutcome> outcomes = new ArrayList<>();

    Batch (long chatId, long selfUserId, Action action, List<BatchTarget> targets, BatchCallback callback) {
      this.chatId = chatId;
      this.selfUserId = selfUserId;
      this.action = action;
      this.targets = targets;
      this.callback = callback;
    }

    /** Stops unsent operations. An already sent request cannot be recalled. */
    public void cancel () {
      cancelled = true;
      if (activeBatch == this) activeBatch = null;
    }

    private boolean live () {
      return !cancelled && !finished;
    }

    private TdApi.Error unavailable () {
      return new TdApi.Error(BATCH_UNAVAILABLE, "Topic action is no longer available");
    }

    private void failAll (TdApi.Error error) {
      if (!live()) return;
      for (int i = outcomes.size(); i < targets.size(); i++) {
        outcomes.add(new BatchOutcome(targets.get(i), error));
      }
      finish();
    }

    private void finish () {
      if (!live()) return;
      finished = true;
      if (activeBatch == this) activeBatch = null;
      callback.onComplete(new BatchResult(action, outcomes));
    }

    private <T extends TdApi.Object> void read (TdApi.Function<T> request, int constructor, Callback<T> block) {
      if (!live()) return;
      send(request, chatId, null, constructor, (value, error) -> {
        if (live()) block.onResult(value, error);
      }, false);
    }

    private void rights (ErrorCallback done) {
      read(new TdApi.GetChatMember(chatId, new TdApi.MessageSenderUser(selfUserId)), TdApi.ChatMember.CONSTRUCTOR, (member, error) -> {
        status = member != null ? member.status : null;
        done.onResult(error != null ? error : !ForumTopicPolicy.isMember(status) ? unavailable() : null);
      });
    }

    private void muteDefault (ErrorCallback done) {
      if (action != Action.MUTE && action != Action.UNMUTE) {
        done.onResult(null);
        return;
      }
      read(new TdApi.GetChat(chatId), TdApi.Chat.CONSTRUCTOR, (chat, error) -> {
        if (error != null) {
          done.onResult(error);
          return;
        }
        TdApi.ChatNotificationSettings settings = chat != null ? chat.notificationSettings : null;
        if (settings == null) {
          done.onResult(unavailable());
          return;
        }
        if (!settings.useDefaultMuteFor) {
          groupMuted = settings.muteFor > 0;
          done.onResult(null);
        } else {
          read(new TdApi.GetScopeNotificationSettings(new TdApi.NotificationSettingsScopeGroupChats()), TdApi.ScopeNotificationSettings.CONSTRUCTOR, (scope, failure) -> {
            groupMuted = scope != null && scope.muteFor > 0;
            done.onResult(failure);
          });
        }
      });
    }

    void start () {
      Set<Integer> ids = new HashSet<>();
      boolean invalidTarget = false;
      for (BatchTarget target : targets) {
        if (target.topicId <= 0) invalidTarget = true;
        ids.add(target.topicId);
      }
      if (chatId == 0 || selfUserId == 0 || targets.isEmpty() || targets.size() > ForumTopicSelection.MAX_SELECTED ||
          invalidTarget || ids.size() != targets.size() || action == Action.CLEAR_GENERAL && targets.size() != 1) {
        failAll(unavailable());
        return;
      }
      callback.onProgress(0, targets.size());
      rights(error -> {
        if (error != null) failAll(error);
        else muteDefault(failure -> {
          if (failure != null) failAll(failure);
          else preflight(0);
        });
      });
    }

    private List<Integer> remainingIds (int start) {
      List<Integer> ids = new ArrayList<>();
      for (int i = start; i < targets.size(); i++) ids.add(targets.get(i).topicId);
      return ids;
    }

    private void preflight (int index) {
      if (!live()) return;
      if (index == targets.size()) {
        if (!preflightErrors.isEmpty()) {
          for (BatchTarget target : targets) {
            TdApi.Error error = preflightErrors.get(target.topicId);
            outcomes.add(new BatchOutcome(target, error != null ? error : new TdApi.Error(BATCH_PREFLIGHT_ABORTED, "Not sent because another selected topic failed preflight")));
          }
          finish();
          return;
        }
        if (action == Action.PIN || action == Action.UNPIN) {
          checkPins(remainingIds(0), error -> {
            if (error != null) failAll(error);
            else next();
          });
        } else next();
        return;
      }
      BatchTarget target = targets.get(index);
      read(new TdApi.GetForumTopic(chatId, target.topicId), TdApi.ForumTopic.CONSTRUCTOR, (topic, error) -> {
        if (target.retrying && action == Action.DELETE && error != null && error.code == 404) {
          alreadyDeleted.add(target.topicId);
        } else if (error != null) {
          preflightErrors.put(target.topicId, error);
        } else if (topic == null || topic.info.chatId != chatId || topic.info.forumTopicId != target.topicId ||
            !ForumTopicSelection.allowed(action, topic, status) || !target.retrying && !ForumTopicSelection.applicable(action, topic, groupMuted)) {
          preflightErrors.put(target.topicId, unavailable());
        }
        preflight(index + 1);
      });
    }

    /** Full server prefix, not a cached search result; cursor- and page-bounded. */
    private void checkPins (List<Integer> remaining, ErrorCallback done) {
      read(new TdApi.GetOption("pinned_forum_topic_count_max"), TdApi.OptionValueInteger.CONSTRUCTOR, (option, error) -> {
        if (error != null) {
          done.onResult(error);
          return;
        }
        pinLimit = option instanceof TdApi.OptionValueInteger ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, ((TdApi.OptionValueInteger) option).value)) : 0;
        Set<Integer> pinned = new HashSet<>();
        Set<ForumTopicStore.Cursor> cursors = new HashSet<>();
        class Prefix {
          void page (ForumTopicStore.Cursor cursor, int pages) {
            if (pages >= MAX_PIN_PAGES || !cursors.add(cursor)) {
              done.onResult(new TdApi.Error(BATCH_PIN_PREFIX, "Unable to verify the full pinned topic order"));
              return;
            }
            read(new TdApi.GetForumTopics(chatId, "", cursor.date, cursor.messageId, cursor.topicId, 100), TdApi.ForumTopics.CONSTRUCTOR, (result, failure) -> {
              if (failure != null || result == null) {
                done.onResult(failure != null ? failure : unavailable());
                return;
              }
              boolean reachedUnpinned = false;
              for (TdApi.ForumTopic topic : result.topics) {
                if (topic.info.chatId != chatId) {
                  done.onResult(unavailable());
                  return;
                }
                if (topic.isPinned && !reachedUnpinned) pinned.add(topic.info.forumTopicId);
                else reachedUnpinned = true;
              }
              ForumTopicStore.Cursor next = new ForumTopicStore.Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId);
              if (reachedUnpinned || next.isEmpty()) {
                int needed = 0;
                for (int id : remaining) if (!pinned.contains(id)) needed++;
                done.onResult(action == Action.PIN && (long) pinned.size() + needed > pinLimit ? new TdApi.Error(BATCH_PIN_LIMIT, "Pinned topic limit exceeded") : null);
              } else page(next, pages + 1);
            });
          }
        }
        new Prefix().page(new ForumTopicStore.Cursor(), 0);
      });
    }

    private void next () {
      if (!live()) return;
      if (outcomes.size() == targets.size()) {
        finish();
        return;
      }
      BatchTarget target = targets.get(outcomes.size());
      if (alreadyDeleted.contains(target.topicId)) {
        record(target, null);
        return;
      }
      // Permissions and states can change during a long batch; refresh immediately before each write.
      rights(error -> {
        if (error != null) {
          record(target, error);
          return;
        }
        muteDefault(failure -> {
          if (failure != null) {
            record(target, failure);
            return;
          }
          read(new TdApi.GetForumTopic(chatId, target.topicId), TdApi.ForumTopic.CONSTRUCTOR, (topic, problem) -> {
            if (problem != null) {
              record(target, problem);
              return;
            }
            if (topic == null || topic.info.chatId != chatId || topic.info.forumTopicId != target.topicId || !ForumTopicSelection.allowed(action, topic, status)) {
              record(target, unavailable());
              return;
            }
            // Another client may already have applied this exact non-destructive direction.
            if (!ForumTopicSelection.applicable(action, topic, groupMuted)) {
              record(target, null);
              return;
            }
            if (action == Action.PIN || action == Action.UNPIN) {
              checkPins(remainingIds(outcomes.size()), pinError -> {
                if (pinError != null) failAll(pinError);
                else apply(target, topic);
              });
            } else apply(target, topic);
          });
        });
      });
    }

    private void apply (BatchTarget target, TdApi.ForumTopic topic) {
      if (!live()) return;
      Key key = new Key(chatId, target.topicId);
      Callback<TdApi.Ok> done = (value, error) -> record(target, error,
        action.isDestructive() && error != null && (error.code == 408 || error.code >= 500 || error.code == 0));
      switch (action) {
        case PIN: case UNPIN: setPinned(key, action == Action.PIN, done); break;
        case MUTE: case UNMUTE: setNotifications(key, ForumTopicPolicy.notifications(topic.notificationSettings, false, action == Action.MUTE ? Integer.MAX_VALUE : 0), done); break;
        case CLOSE: case OPEN: setClosed(key, action == Action.CLOSE, done); break;
        case DELETE: case CLEAR_GENERAL: delete(key, done); break;
        case READ_MENTIONS: readAllMentions(key, done); break;
        case READ_REACTIONS: readAllReactions(key, done); break;
        case READ_POLL_VOTES: readAllPollVotes(key, done); break;
      }
    }

    private void record (BatchTarget target, @Nullable TdApi.Error error) {
      record(target, error, false);
    }

    private void record (BatchTarget target, @Nullable TdApi.Error error, boolean uncertain) {
      if (!live()) return;
      outcomes.add(new BatchOutcome(target, error, uncertain));
      callback.onProgress(outcomes.size(), targets.size());
      next();
    }
  }

  public static final int BATCH_UNAVAILABLE = -8101;
  public static final int BATCH_PIN_LIMIT = -8102;
  public static final int BATCH_PIN_PREFIX = -8103;
  public static final int BATCH_PREFLIGHT_ABORTED = -8104;
  private static final int MAX_PIN_PAGES = 32;
}
