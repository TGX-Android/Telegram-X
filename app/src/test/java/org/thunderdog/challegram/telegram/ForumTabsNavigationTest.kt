package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.data.ForumNavigation

/** Pure routing checks with synthetic IDs; no account, Android, JNI or network. */
class ForumTabsNavigationTest {
  private val forum = TdApi.MessageTopicForum(73)
  private val eligibleTopics = arrayOf<TdApi.MessageTopic?>(null, forum)

  private fun tabs(
    isForum: Boolean = true,
    hasTabs: Boolean = true,
    topic: TdApi.MessageTopic? = null,
    thread: Boolean = false,
    scheduled: Boolean = false,
    filter: Boolean = false,
    payload: Boolean = false
  ) = ForumNavigation.openTabs(isForum, hasTabs, topic, thread, scheduled, filter, payload)

  @Test fun tabbedForumAllowsCommonStreamAndExplicitForumTopic() {
    for (topic in eligibleTopics) assertTrue(tabs(topic = topic))
  }

  @Test fun classicForumDoesNotAcquireTabs() {
    for (topic in eligibleTopics) assertFalse(tabs(hasTabs = false, topic = topic))
    // The classic list route remains available; tab routing must not absorb it.
    assertTrue(ForumNavigation.openTopicList(true, true, null, false, false, false, false, false, false))
  }

  @Test fun nonForumNeverAcquiresTabsEvenWithTabsFlag() {
    for (hasTabs in listOf(false, true)) {
      for (topic in eligibleTopics) assertFalse(tabs(isForum = false, hasTabs = hasTabs, topic = topic))
    }
  }

  @Test fun allFourTypedTopicKindsAreDistinguishedByTypeNotNumericIdentity() {
    assertTrue(tabs(topic = TdApi.MessageTopicForum(73)))
    assertFalse(tabs(topic = TdApi.MessageTopicThread(73)))
    assertFalse(tabs(topic = TdApi.MessageTopicSavedMessages(73)))
    assertFalse(tabs(topic = TdApi.MessageTopicDirectMessages(73)))
  }

  @Test fun explicitGeneralIsATypedForumNotAnInventedWholeChatIdentity() {
    val general = TdApi.MessageTopicForum(ForumNavigation.GENERAL_TOPIC_ID)
    assertTrue(tabs(topic = general))
    assertEquals(ForumNavigation.GENERAL_TOPIC_ID, general.forumTopicId)
    assertTrue(tabs(topic = null))
    assertFalse(ForumNavigation.openTopicList(true, true, general, false, false, false, false, false, false))
  }

  @Test fun explicitForumIdentityIsNotNarrowedOrChangedByRouting() {
    val topic = TdApi.MessageTopicForum(Int.MAX_VALUE)
    assertTrue(tabs(topic = topic))
    assertEquals(Int.MAX_VALUE, topic.forumTopicId)
  }

  @Test fun genericThreadExcludesBothCommonAndExplicitForumTargets() {
    for (topic in eligibleTopics) assertFalse(tabs(topic = topic, thread = true))
  }

  @Test fun scheduledMessagesExcludeBothCommonAndExplicitForumTargets() {
    for (topic in eligibleTopics) assertFalse(tabs(topic = topic, scheduled = true))
  }

  @Test fun filteredMessagesExcludeBothCommonAndExplicitForumTargets() {
    for (topic in eligibleTopics) assertFalse(tabs(topic = topic, filter = true))
  }

  @Test fun payloadExcludesBothCommonAndExplicitForumTargets() {
    for (topic in eligibleTopics) assertFalse(tabs(topic = topic, payload = true))
  }

  @Test fun routingMatrixKeepsEveryGateIndependentIncludingCombinedExclusions() {
    val topicKinds = listOf<Pair<TdApi.MessageTopic?, Boolean>>(
      null to true,
      forum to true,
      TdApi.MessageTopicThread(73) to false,
      TdApi.MessageTopicSavedMessages(73) to false,
      TdApi.MessageTopicDirectMessages(73) to false
    )
    for (isForum in listOf(false, true)) {
      for (hasTabs in listOf(false, true)) {
        for ((topic, eligible) in topicKinds) {
          for (exclusions in 0 until 16) {
            assertEquals(
              "forum=$isForum tabs=$hasTabs topic=${topic?.javaClass?.simpleName} exclusions=$exclusions",
              isForum && hasTabs && eligible && exclusions == 0,
              tabs(isForum, hasTabs, topic,
                thread = exclusions and 1 != 0,
                scheduled = exclusions and 2 != 0,
                filter = exclusions and 4 != 0,
                payload = exclusions and 8 != 0)
            )
          }
        }
      }
    }
  }

  @Test fun explicitAnchorStillResolvesItsTopicInTabbedForum() {
    assertTrue(ForumNavigation.resolveAnchorTopic(true, true))
  }

  @Test fun restoredCommonStreamAnchorDoesNotOverrideSelectedTab() {
    assertFalse(ForumNavigation.resolveAnchorTopic(false, true))
  }

  @Test fun explicitAnchorStillResolvesWithoutTabs() {
    assertTrue(ForumNavigation.resolveAnchorTopic(true, false))
  }

  @Test fun restoredCommonStreamAnchorRetainsLegacyResolutionWithoutTabs() {
    assertTrue(ForumNavigation.resolveAnchorTopic(false, false))
  }
}
