package org.thunderdog.challegram.data

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.R
import org.thunderdog.challegram.ui.MessagesController.ReplyInfo
import tgx.td.data.MessageWithProperties

class ForumPresentationTest {
  private fun message(chat: Long = 100, topic: TdApi.MessageTopic? = TdApi.MessageTopicForum(17)) = TdApi.Message().apply {
    chatId = chat; id = 1000; topicId = topic
  }
  private fun reply(message: TdApi.Message) = ReplyInfo(null, MessageWithProperties(message, TdApi.MessageProperties()), null, 0, "")
  private fun forumId(topic: TdApi.MessageTopic?) = (topic as TdApi.MessageTopicForum).forumTopicId

  @Test fun createdTopicHasServicePreview() {
    assertEquals(R.string.ForumTopicCreated, ForumPresentation.servicePreview(TdApi.MessageForumTopicCreated()))
  }
  @Test fun editedTopicHasServicePreview() {
    assertEquals(R.string.ForumTopicUpdated, ForumPresentation.servicePreview(TdApi.MessageForumTopicEdited()))
  }
  @Test fun closedAndReopenedPreviewsAreDistinct() {
    assertEquals(R.string.ForumTopicClosed, ForumPresentation.servicePreview(TdApi.MessageForumTopicIsClosedToggled(true)))
    assertEquals(R.string.ForumTopicReopened, ForumPresentation.servicePreview(TdApi.MessageForumTopicIsClosedToggled(false)))
  }
  @Test fun hiddenAndShownGeneralPreviewsAreDistinct() {
    assertEquals(R.string.ForumGeneralHidden, ForumPresentation.servicePreview(TdApi.MessageForumTopicIsHiddenToggled(true)))
    assertEquals(R.string.ForumGeneralShown, ForumPresentation.servicePreview(TdApi.MessageForumTopicIsHiddenToggled(false)))
  }
  @Test fun otherMessagesKeepTheirOriginalPreview() {
    assertEquals(0, ForumPresentation.servicePreview(TdApi.MessageText()))
    assertEquals(0, ForumPresentation.servicePreview(TdApi.MessageUnsupported()))
  }

  @Test fun commonStreamShowsForumButton() {
    assertTrue(ForumPresentation.showTopicButton(TdApi.MessageTopicForum(17), null, false, false, false))
  }
  @Test fun generalIsARealTopicButton() {
    assertTrue(ForumPresentation.showTopicButton(TdApi.MessageTopicForum(1), null, false, false, false))
  }
  @Test fun explicitTopicDoesNotShowNestedTopicButton() {
    assertFalse(ForumPresentation.showTopicButton(TdApi.MessageTopicForum(17), TdApi.MessageTopicForum(17), false, false, false))
  }
  @Test fun otherExplicitViewDoesNotBecomeCommonStream() {
    assertFalse(ForumPresentation.showTopicButton(TdApi.MessageTopicForum(17), TdApi.MessageTopicThread(17), false, false, false))
  }
  @Test fun threadScheduledAndPreviewDoNotShowButton() {
    val topic = TdApi.MessageTopicForum(17)
    assertFalse(ForumPresentation.showTopicButton(topic, null, true, false, false))
    assertFalse(ForumPresentation.showTopicButton(topic, null, false, true, false))
    assertFalse(ForumPresentation.showTopicButton(topic, null, false, false, true))
  }
  @Test fun nullAndGenericMessagesDoNotBecomeGeneral() {
    assertFalse(ForumPresentation.showTopicButton(null, null, false, false, false))
    assertFalse(ForumPresentation.showTopicButton(TdApi.MessageTopicThread(1), null, false, false, false))
  }
  @Test fun commonStreamReplySelectsItsForum() {
    assertEquals(17, forumId(ForumPresentation.composerTopic(100, null, message())))
  }
  @Test fun generalReplyStaysExplicitGeneral() {
    assertEquals(1, forumId(ForumPresentation.composerTopic(100, null, message(topic = TdApi.MessageTopicForum(1)))))
  }
  @Test fun externalReplyNeverImportsAnotherChatsForum() {
    assertNull(ForumPresentation.composerTopic(100, null, message(chat = 200)))
  }
  @Test fun currentForumWinsOverCrossTopicReply() {
    assertEquals(18, forumId(ForumPresentation.composerTopic(100, TdApi.MessageTopicForum(18), message())))
  }
  @Test fun noReplyOrNonForumReplyDoesNotInventTopic() {
    assertNull(ForumPresentation.composerTopic(100, null, null))
    assertNull(ForumPresentation.composerTopic(100, null, message(topic = TdApi.MessageTopicThread(17))))
  }
  @Test fun productionReplyInfoKeepsTypedDestinationAndLocalReply() {
    val reply = reply(message()).withTopic(100, null)
    assertEquals(17, forumId(reply.inTopicId))
    assertTrue(reply.toInputMessageReply() is TdApi.InputMessageReplyToMessage)
    assertEquals(1000L, (reply.toInputMessageReply() as TdApi.InputMessageReplyToMessage).messageId)
  }
  @Test fun productionReplyInfoExplicitOtherTopicUsesExternalReply() {
    val reply = reply(message()).withTopic(100, TdApi.MessageTopicForum(18))
    assertEquals(18, forumId(reply.inTopicId))
    val external = reply.toInputMessageReply() as TdApi.InputMessageReplyToExternalMessage
    assertEquals(100L, external.chatId); assertEquals(1000L, external.messageId)
  }
  @Test fun productionForeignReplyHasNoForeignDestinationTopic() {
    val reply = reply(message(chat = 200)).withTopic(100, null)
    assertNull(reply.inTopicId)
    assertTrue(reply.toInputMessageReply() is TdApi.InputMessageReplyToExternalMessage)
  }
  @Test fun productionReplyKeepsQuoteAndChecklistPollMetadata() {
    val quote = TdApi.InputTextQuote(TdApi.FormattedText("Synthetic", null), 0)
    val reply = ReplyInfo(null, MessageWithProperties(message(), TdApi.MessageProperties()), quote, 7, "option").withTopic(100, null)
    val input = reply.toInputMessageReply() as TdApi.InputMessageReplyToMessage
    assertTrue(input.quote === quote); assertEquals(7, input.checklistTaskId); assertEquals("option", input.pollOptionId)
  }
  @Test fun readMentionsOnlyTouchesTheForumTopic() {
    val request = ForumPresentation.readMentions(100, TdApi.MessageTopicForum(17)) as TdApi.ReadAllForumTopicMentions
    assertEquals(100L, request.chatId); assertEquals(17, request.forumTopicId)
  }
  @Test fun readReactionsOnlyTouchesTheForumTopic() {
    val request = ForumPresentation.readReactions(100, TdApi.MessageTopicForum(18)) as TdApi.ReadAllForumTopicReactions
    assertEquals(100L, request.chatId); assertEquals(18, request.forumTopicId)
  }
  @Test fun generalReadDoesNotReadEntireChat() {
    assertEquals(1, (ForumPresentation.readMentions(100, TdApi.MessageTopicForum(1)) as TdApi.ReadAllForumTopicMentions).forumTopicId)
    assertEquals(1, (ForumPresentation.readReactions(100, TdApi.MessageTopicForum(1)) as TdApi.ReadAllForumTopicReactions).forumTopicId)
  }
  @Test fun commonStreamReadRetainsChatScope() {
    assertTrue(ForumPresentation.readMentions(100, null) is TdApi.ReadAllChatMentions)
    assertTrue(ForumPresentation.readReactions(100, null) is TdApi.ReadAllChatReactions)
  }
  @Test fun pollVoteSearchCarriesTypedTopic() {
    val request = ForumPresentation.unreadPollVotes(100, 17)
    assertEquals(100L, request.chatId); assertEquals(17, forumId(request.topicId))
    assertTrue(request.filter is TdApi.SearchMessagesFilterUnreadPollVote)
    assertEquals(1, request.limit); assertEquals(0L, request.fromMessageId)
  }
  @Test fun defaultNotificationSettingsInheritGroupMute() {
    val settings = TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = true; muteFor = 99 }
    assertTrue(ForumPresentation.isMuted(settings, true)); assertFalse(ForumPresentation.isMuted(settings, false))
  }
  @Test fun explicitNotifyOverridesMutedGroup() {
    val settings = TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = false; muteFor = 0 }
    assertFalse(ForumPresentation.isMuted(settings, true))
  }
  @Test fun explicitMuteOverridesUnmutedGroup() {
    val settings = TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = false; muteFor = 3600 }
    assertTrue(ForumPresentation.isMuted(settings, false))
  }
  @Test fun unknownSettingsUseGroupWithoutMutation() {
    assertTrue(ForumPresentation.isMuted(null, true)); assertFalse(ForumPresentation.isMuted(null, false))
  }
}
