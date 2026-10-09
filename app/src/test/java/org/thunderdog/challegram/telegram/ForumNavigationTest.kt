package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.data.ForumNavigation
import org.thunderdog.challegram.data.MessageTopics
import org.thunderdog.challegram.ui.MessagesController
import tgx.td.MessageId

/** Synthetic ids only; no account, Android runtime or native TDLib dependency. */
class ForumNavigationTest {
  private val forum = TdApi.MessageTopicForum(17)
  private fun message(topic: TdApi.MessageTopic? = forum, chat: Long = 100) = TdApi.Message().apply {
    id = 300
    chatId = chat
    topicId = topic
  }
  private fun list(isForum: Boolean = true, viewAsTopics: Boolean = true, topic: TdApi.MessageTopic? = null,
                   thread: Boolean = false, message: Boolean = false, scheduled: Boolean = false,
                   filter: Boolean = false, payload: Boolean = false, whole: Boolean = false) =
    ForumNavigation.openTopicList(isForum, viewAsTopics, topic, thread, message, scheduled, filter, payload, whole)
  private fun link(topic: TdApi.MessageTopic? = null, message: TdApi.Message? = null) = TdApi.MessageLinkInfo().apply {
    chatId = 100
    topicId = topic
    this.message = message
  }

  @Test fun plainForumOpensList() { assertTrue(list()) }
  @Test fun serverPreferenceOpensCommonStream() { assertFalse(list(viewAsTopics = false)) }
  @Test fun nonForumIsNotRedirected() { assertFalse(list(isForum = false)) }
  @Test fun explicitForumBypassesList() { assertFalse(list(topic = forum)) }
  @Test fun generalIsExplicitNotWholeChat() { assertFalse(list(topic = TdApi.MessageTopicForum(1))) }
  @Test fun genericThreadBypassesList() { assertFalse(list(thread = true)) }
  @Test fun typedThreadIsNotForumList() { assertFalse(list(topic = TdApi.MessageTopicThread(17))) }
  @Test fun savedMessagesAreNotForumList() { assertFalse(list(topic = TdApi.MessageTopicSavedMessages(17))) }
  @Test fun directMessagesAreNotForumList() { assertFalse(list(topic = TdApi.MessageTopicDirectMessages(17))) }
  @Test fun messageTargetIsNotDiscarded() { assertFalse(list(message = true)) }
  @Test fun scheduledTargetIsNotDiscarded() { assertFalse(list(scheduled = true)) }
  @Test fun filterIsNotDiscarded() { assertFalse(list(filter = true)) }
  @Test fun shareDraftAndCallPayloadsAreNotDiscarded() { assertFalse(list(payload = true)) }
  @Test fun explicitStreamDoesNotChangePreference() { assertFalse(list(whole = true)); assertTrue(list()) }
  @Test fun linkKeepsExplicitForum() { assertTrue(forum === ForumNavigation.linkTopic(link(forum, message(TdApi.MessageTopicForum(18))))) }
  @Test fun linkKeepsForumWhenMessageDeleted() { assertTrue(forum === ForumNavigation.linkTopic(link(forum))) }
  @Test fun linkFallsBackToMessageTopic() { assertTrue(forum === ForumNavigation.linkTopic(link(message = message()))) }
  @Test fun missingMessageWithoutTopicRemainsWholeChat() { assertNull(ForumNavigation.linkTopic(link())) }
  @Test fun threadLinkDoesNotBecomeForum() {
    val thread = TdApi.MessageTopicThread(17)
    assertTrue(thread === ForumNavigation.linkTopic(link(thread, message())))
  }
  @Test fun resolvedMessageMustBelongToChat() { assertNull(ForumNavigation.forumTopic(101, message())) }
  @Test fun resolvedThreadIsNotForum() { assertNull(ForumNavigation.forumTopic(100, message(TdApi.MessageTopicThread(17)))) }
  @Test fun resolvedForumRetainsIdentity() { assertTrue(forum === ForumNavigation.forumTopic(100, message())) }
  @Test fun fullNotificationRetainsTopic() {
    assertTrue(forum === ForumNavigation.notificationTopic(TdApi.NotificationTypeNewMessage(message(), true)))
  }
  @Test fun pushHasNoInventedGeneralTopic() { assertNull(ForumNavigation.notificationTopic(TdApi.NotificationTypeNewPushMessage())) }
  @Test fun missingNotificationHasNoInventedTopic() { assertNull(ForumNavigation.notificationTopic(null)) }
  @Test fun replyWithDeletedMessageRetainsExplicitForum() { assertTrue(forum === ForumNavigation.replyTopic(100, forum, null)) }
  @Test fun replyResolvesTopicFromMessage() { assertTrue(forum === ForumNavigation.replyTopic(100, null, message())) }
  @Test fun unresolvedReplyCannotFallBackToGeneral() { assertNull(ForumNavigation.replyTopic(100, null, null)) }
  @Test fun replyCannotUseForeignChat() { assertNull(ForumNavigation.replyTopic(101, null, message())) }
  @Test fun replyDoesNotConfuseThreadWithForum() { assertNull(ForumNavigation.replyTopic(100, TdApi.MessageTopicThread(17), null)) }
  @Test fun typedOpenParametersAndHighlightPreserveForum() {
    val params = TdlibUi.ChatOpenParameters().messageTopic(forum).highlightMessage(MessageId(100, 300)).keepStack()
    assertTrue(forum === params.messageTopicId)
    assertNull(params.threadInfo)
    assertEquals(300L, params.highlightMessageId.messageId)
  }
  @Test fun messageObjectHighlightInfersForum() {
    val params = TdlibUi.ChatOpenParameters().highlightMessage(message())
    assertTrue(forum === params.messageTopicId)
  }
  @Test fun topicArgumentsNeverNeedFakeThreadInfo() {
    val chat = TdApi.Chat().apply { id = 100 }
    val args = MessagesController.Arguments(null, chat, null, forum, MessageId(100, 300), 1, null)
    assertNull(args.messageThread)
    assertTrue(forum === args.messageTopicId)
  }
  @Test fun reopeningBFromADoesNotDeduplicateByGroup() {
    assertFalse(MessageTopics.sameChat(100, forum, 100, TdApi.MessageTopicForum(18)))
    assertFalse(MessageTopics.sameChat(100, forum, 100, null))
    assertTrue(MessageTopics.sameChat(100, forum, 100, TdApi.MessageTopicForum(17)))
  }
  @Test fun newestNavigationInvalidatesLateMetadata() {
    val requests = ForumNavigation.RequestGate()
    val a = requests.begin()
    assertTrue(requests.isCurrent(a))
    val b = requests.begin()
    assertFalse(requests.isCurrent(a))
    assertTrue(requests.isCurrent(b))
  }
  @Test fun noRequestIsCurrentUntilStarted() { assertFalse(ForumNavigation.RequestGate().isCurrent(0)) }
  @Test fun navigationTicketsAreScopedToTheirOwner() {
    val first = ForumNavigation.RequestGate()
    val second = ForumNavigation.RequestGate()
    val a = first.begin()
    val b = second.begin()
    second.begin()
    assertTrue(first.isCurrent(a))
    assertFalse(second.isCurrent(b))
  }
}
