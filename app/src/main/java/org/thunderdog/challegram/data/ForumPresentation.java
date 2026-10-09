package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;

/** Pure common-stream rules: display identity, composer identity and read actions must agree. */
public final class ForumPresentation {
  private ForumPresentation () { }

  /** Compact service text for previews; the history retains its full actor/name formatting. */
  public static int servicePreview (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
        return R.string.ForumTopicCreated;
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
        return R.string.ForumTopicUpdated;
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
        return ((TdApi.MessageForumTopicIsClosedToggled) content).isClosed ? R.string.ForumTopicClosed : R.string.ForumTopicReopened;
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
        return ((TdApi.MessageForumTopicIsHiddenToggled) content).isHidden ? R.string.ForumGeneralHidden : R.string.ForumGeneralShown;
      default:
        return 0;
    }
  }

  public static boolean showTopicButton (TdApi.MessageTopic message, TdApi.MessageTopic viewed, boolean thread, boolean scheduled, boolean preview) {
    return ForumHistory.isForum(message) && viewed == null && !thread && !scheduled && !preview;
  }

  public static TdApi.MessageTopic composerTopic (long chatId, TdApi.MessageTopic viewed, TdApi.Message reply) {
    return ForumHistory.outgoingTopic(viewed, ForumNavigation.forumTopic(chatId, reply));
  }

  public static boolean isMuted (TdApi.ChatNotificationSettings settings, boolean groupMuted) {
    return settings == null || settings.useDefaultMuteFor ? groupMuted : settings.muteFor > 0;
  }

  public static TdApi.Function<TdApi.Ok> readMentions (long chatId, TdApi.MessageTopic topic) {
    return ForumHistory.isForum(topic) ? new TdApi.ReadAllForumTopicMentions(chatId, ((TdApi.MessageTopicForum) topic).forumTopicId) : new TdApi.ReadAllChatMentions(chatId);
  }

  public static TdApi.Function<TdApi.Ok> readReactions (long chatId, TdApi.MessageTopic topic) {
    return ForumHistory.isForum(topic) ? new TdApi.ReadAllForumTopicReactions(chatId, ((TdApi.MessageTopicForum) topic).forumTopicId) : new TdApi.ReadAllChatReactions(chatId);
  }

  public static TdApi.SearchChatMessages unreadPollVotes (long chatId, int topicId) {
    return new TdApi.SearchChatMessages(chatId, new TdApi.MessageTopicForum(topicId), "", null, 0, 0, 1, new TdApi.SearchMessagesFilterUnreadPollVote());
  }
}
