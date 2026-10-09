package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure width-policy and header-geometry checks; caption/author widths are synthetic inputs.
 * TextWrapper reflow is deliberately not simulated: nonempty Text parsing calls
 * Emoji.instance().replaceEmoji(), which requires persistent Settings even without emoji.
 * No Android, TGMessage, account, JNI or font/direction-dependent rendering is exercised.
 */
public class ForumMessageLayoutTest {
  @Test public void standaloneForumTopicsUseWideContentWithoutTabs () {
    for (int topicId : new int[] {1, 17, Integer.MAX_VALUE}) {
      assertTrue(ForumMessageLayout.useWideContent(false, new TdApi.MessageTopicForum(topicId), false, false));
    }
  }

  @Test public void forumTabsKeepWideContentForAllAndSelectedTopics () {
    assertTrue(ForumMessageLayout.useWideContent(true, null, false, false));
    assertTrue(ForumMessageLayout.useWideContent(true, new TdApi.MessageTopicForum(1), false, false));
    assertTrue(ForumMessageLayout.useWideContent(true, new TdApi.MessageTopicForum(17), false, false));
  }

  @Test public void ordinaryChatsAndOtherTopicTypesKeepLegacyContentWidth () {
    assertFalse(ForumMessageLayout.useWideContent(false, null, false, false));
    // Matching numeric IDs must not turn channel comments or other topics into forums.
    for (long id : new long[] {1, 17, (1L << 32) + 17}) {
      for (TdApi.MessageTopic topic : new TdApi.MessageTopic[] {
        new TdApi.MessageTopicThread(id), new TdApi.MessageTopicDirectMessages(id), new TdApi.MessageTopicSavedMessages(id)
      }) {
        assertFalse(ForumMessageLayout.useWideContent(false, topic, false, false));
      }
    }
  }

  @Test public void eventLogAndThreadHeaderNeverUseWideContent () {
    for (boolean tabs : new boolean[] {false, true}) {
      for (TdApi.MessageTopic topic : new TdApi.MessageTopic[] {null, new TdApi.MessageTopicForum(1), new TdApi.MessageTopicForum(17)}) {
        assertFalse(ForumMessageLayout.useWideContent(tabs, topic, true, false));
        assertFalse(ForumMessageLayout.useWideContent(tabs, topic, false, true));
        assertFalse(ForumMessageLayout.useWideContent(tabs, topic, true, true));
      }
    }
  }

  @Test public void effectiveCommentThreadDoesNotInheritAnExplicitForumTopicWidth () {
    TdApi.MessageTopic forum = new TdApi.MessageTopicForum(17);
    assertTrue(ForumMessageLayout.useWideContent(false, MessageTopics.effectiveTopic(null, forum), false, false));
    assertFalse(ForumMessageLayout.useWideContent(false, MessageTopics.effectiveTopic(ThreadInfo.INVALID, forum), false, false));
  }

  @Test public void standaloneAndTabbedTopicsShareCaptionAndMediaGeometry () {
    for (boolean tabs : new boolean[] {false, true}) {
      for (int topicId : new int[] {1, 17}) {
        boolean wide = ForumMessageLayout.useWideContent(tabs, new TdApi.MessageTopicForum(topicId), false, false);
        assertEquals(0, ForumMessageLayout.bubbleGutterDp(wide, false));
        for (int width : new int[] {248, 348, 288, 248, 348}) {
          for (int padding : new int[] {0, 24}) {
            int captionWidth = ForumMessageLayout.captionMaxWidth(wide, true, width, 96, padding);
            assertEquals(width - padding, captionWidth);
            int mediaWidth = ForumMessageLayout.expandedMediaWidth(wide, 96, captionWidth, padding, width);
            assertEquals(width, mediaWidth);
            assertEquals(mediaWidth, ForumMessageLayout.mediaContentWidth(wide, true, mediaWidth, captionWidth, padding));
            assertEquals(96, ForumMessageLayout.expandedMediaWidth(wide, 96, 32, padding, width));
          }
        }
      }
    }
  }

  @Test public void normalChatsKeepExistingBubbleGutters () {
    assertEquals(56, ForumMessageLayout.bubbleGutterDp(false, false));
    assertEquals(8, ForumMessageLayout.bubbleGutterDp(false, true));
  }

  @Test public void forumTabMessagesDoNotReserveAnotherGutter () {
    assertEquals(0, ForumMessageLayout.bubbleGutterDp(true, false));
  }

  @Test public void normalBubbleCaptionsRemainMediaBoundAtEveryHistoryWidth () {
    for (int contentWidth : new int[] {248, 288, 348}) {
      // Media width, expected caption budget after 24px padding.
      for (int[] sample : new int[][] {{96, 72}, {128, 104}, {176, 152}}) {
        assertEquals("Normal chat: history=" + contentWidth + ", media=" + sample[0],
          sample[1], ForumMessageLayout.captionMaxWidth(false, true, contentWidth, sample[0], 24));
      }
    }
  }

  @Test public void forumBubbleCaptionBudgetIsIndependentOfNarrowPortraitMosaic () {
    // Content width, expected caption budget; changing the mosaic must not change it.
    for (int[] sample : new int[][] {{248, 224}, {288, 264}, {348, 324}}) {
      for (int mediaWidth : new int[] {96, 128, 176}) {
        assertEquals("Forum tabs: history=" + sample[0] + ", media=" + mediaWidth,
          sample[1], ForumMessageLayout.captionMaxWidth(true, true, sample[0], mediaWidth, 24));
      }
    }
  }

  @Test public void nonBubbleCaptionsKeepContentWidthWithoutSubtractingPadding () {
    for (boolean forumTabs : new boolean[] {false, true}) {
      for (int contentWidth : new int[] {0, 248, 288, 348}) {
        for (int padding : new int[] {0, 24}) {
          assertEquals("Non-bubbles: forum=" + forumTabs + ", padding=" + padding,
            contentWidth, ForumMessageLayout.captionMaxWidth(forumTabs, false, contentWidth, 96, padding));
        }
      }
    }
  }

  @Test public void forwardedCaptionWithZeroPaddingKeepsTheCorrectWidthSource () {
    for (int contentWidth : new int[] {248, 288, 348}) {
      assertEquals(contentWidth, ForumMessageLayout.captionMaxWidth(true, true, contentWidth, 128, 0));
      assertEquals(128, ForumMessageLayout.captionMaxWidth(false, true, contentWidth, 128, 0));
    }
    assertEquals(240, ForumMessageLayout.mediaContentWidth(true, true, 128, 240, 0));
    assertEquals(240, ForumMessageLayout.mediaContentWidth(false, true, 128, 240, 0));
    assertEquals(128, ForumMessageLayout.mediaContentWidth(true, true, 128, 32, 0));
  }

  @Test public void bubbleCaptionBudgetClampsAfterSelectingTheApplicableWidth () {
    // Selected width, expected max(1, width - padding), including either side of the boundary.
    for (int[] sample : new int[][] {{0, 1}, {1, 1}, {23, 1}, {24, 1}, {25, 1}, {26, 2}}) {
      assertEquals("Forum content width=" + sample[0], sample[1],
        ForumMessageLayout.captionMaxWidth(true, true, sample[0], 348, 24));
      assertEquals("Normal media width=" + sample[0], sample[1],
        ForumMessageLayout.captionMaxWidth(false, true, 348, sample[0], 24));
    }
  }

  @Test public void widerCaptionExpandsForumContentWithoutBecomingMediaBound () {
    int captionBudget = ForumMessageLayout.captionMaxWidth(true, true, 288, 96, 24);
    assertEquals(264, captionBudget);
    // A long caption may fill its budget even though the portrait mosaic is only 96px wide.
    assertEquals(288, ForumMessageLayout.mediaContentWidth(true, true, 96, captionBudget, 24));
    // A caption shorter than its budget must use its own width, not fill the entire history.
    assertEquals(224, ForumMessageLayout.mediaContentWidth(true, true, 96, 200, 24));
  }

  @Test public void onlyForumBubblesAddCaptionPaddingToMediaContent () {
    assertEquals(264, ForumMessageLayout.mediaContentWidth(true, true, 128, 240, 24));
    assertEquals(240, ForumMessageLayout.mediaContentWidth(false, true, 128, 240, 24));
    assertEquals(240, ForumMessageLayout.mediaContentWidth(true, false, 128, 240, 24));
    assertEquals(240, ForumMessageLayout.mediaContentWidth(false, false, 128, 240, 24));
  }

  @Test public void shortOrAbsentCaptionKeepsMediaWidthInsteadOfFillingHistory () {
    for (boolean forumTabs : new boolean[] {false, true}) {
      for (boolean bubbles : new boolean[] {false, true}) {
        for (int captionWidth : new int[] {0, 32, 80}) {
          assertEquals("Short caption: forum=" + forumTabs + ", bubbles=" + bubbles + ", width=" + captionWidth,
            128, ForumMessageLayout.mediaContentWidth(forumTabs, bubbles, 128, captionWidth, 24));
        }
      }
    }
  }

  @Test public void forumBubblePaddingIsAddedExactlyOnceAtTheMediaBoundary () {
    assertEquals(128, ForumMessageLayout.mediaContentWidth(true, true, 128, 103, 24));
    assertEquals(128, ForumMessageLayout.mediaContentWidth(true, true, 128, 104, 24));
    assertEquals(129, ForumMessageLayout.mediaContentWidth(true, true, 128, 105, 24));
  }

  @Test public void repeatedWidthsRestoreCaptionAndContentBudgetsWithoutAccumulatingPadding () {
    // Content width, expected caption budget: narrow/wide/middle, then revisit each.
    int[][] widths = {{248, 224}, {348, 324}, {288, 264}, {248, 224}, {288, 264}, {348, 324}, {248, 224}};
    for (int[] sample : widths) {
      int captionBudget = ForumMessageLayout.captionMaxWidth(true, true, sample[0], 128, 24);
      assertEquals("Repeated forum caption budget at " + sample[0], sample[1], captionBudget);
      assertEquals("Saturated caption content at " + sample[0], sample[0],
        ForumMessageLayout.mediaContentWidth(true, true, 128, captionBudget, 24));
      assertEquals("Normal caption budget stays media-bound at " + sample[0], 104,
        ForumMessageLayout.captionMaxWidth(false, true, sample[0], 128, 24));
      assertEquals(128, ForumMessageLayout.mediaContentWidth(false, true, 128, 104, 24));
    }
  }

  @Test public void captionExpansionUsesMeasuredCaptionAndAddsPaddingExactlyOnce () {
    assertEquals(224, ForumMessageLayout.expandedMediaWidth(true, 128, 200, 24, 288));
    assertEquals(288, ForumMessageLayout.expandedMediaWidth(true, 128, 264, 24, 288));
    assertEquals(128, ForumMessageLayout.expandedMediaWidth(true, 128, 103, 24, 288));
    assertEquals(128, ForumMessageLayout.expandedMediaWidth(true, 128, 104, 24, 288));
    assertEquals(129, ForumMessageLayout.expandedMediaWidth(true, 128, 105, 24, 288));
  }

  @Test public void captionExpansionIsBoundedByAvailableWidthWithoutShrinkingExistingMedia () {
    for (int available : new int[] {248, 288, 348}) {
      assertEquals(available, ForumMessageLayout.expandedMediaWidth(true, 128, available, 24, available));
      assertEquals(available, ForumMessageLayout.expandedMediaWidth(true, 128, available * 2, 24, available));
    }
    // A new, smaller budget is not permission for this expansion-only helper to shrink media.
    assertEquals(288, ForumMessageLayout.expandedMediaWidth(true, 288, 400, 24, 248));
    assertEquals(128, ForumMessageLayout.expandedMediaWidth(true, 128, 400, 24, 0));
  }

  @Test public void legacyAndAbsentCaptionsDisableMediaExpansion () {
    // Legacy/non-bubble callers and a missing caption are represented by fillCaption=false.
    for (int mediaWidth : new int[] {16, 96, 128, 288}) {
      for (int captionWidth : new int[] {0, 32, 264, 600}) {
        for (int padding : new int[] {0, 24}) {
          assertEquals("Expansion disabled: media=" + mediaWidth + ", caption=" + captionWidth,
            mediaWidth, ForumMessageLayout.expandedMediaWidth(false, mediaWidth, captionWidth, padding, 248));
        }
      }
    }
  }

  @Test public void shortCaptionDoesNotStretchMediaToHistoryWidth () {
    for (int available : new int[] {248, 288, 348}) {
      for (int captionWidth : new int[] {1, 32, 80, 104}) {
        assertEquals(128, ForumMessageLayout.expandedMediaWidth(true, 128, captionWidth, 24, available));
      }
    }
  }

  @Test public void forwardedExpansionDoesNotInventCaptionPadding () {
    assertEquals(200, ForumMessageLayout.expandedMediaWidth(true, 128, 200, 0, 288));
    assertEquals(128, ForumMessageLayout.expandedMediaWidth(true, 128, 128, 0, 288));
    assertEquals(129, ForumMessageLayout.expandedMediaWidth(true, 128, 129, 0, 288));
    assertEquals(288, ForumMessageLayout.expandedMediaWidth(true, 128, 600, 0, 288));
  }

  @Test public void repeatedPanelWidthsRecomputeExpansionFromTheNaturalMediaWidth () {
    for (int padding : new int[] {0, 24}) {
      for (int available : new int[] {248, 348, 288, 248, 288, 348, 248}) {
        int captionWidth = ForumMessageLayout.captionMaxWidth(true, true, available, 128, padding);
        assertEquals("Expanded media at " + available + ", padding=" + padding, available,
          ForumMessageLayout.expandedMediaWidth(true, 128, captionWidth, padding, available));
        assertEquals("A short replacement caption restores the natural width", 128,
          ForumMessageLayout.expandedMediaWidth(true, 128, 32, padding, available));
        assertEquals(128, ForumMessageLayout.expandedMediaWidth(false, 128, captionWidth, padding, available));
      }
    }
  }

  @Test public void authorHeaderPlacesSmallAvatarBeforeAuthorAndAdminSignAtTrailingEdge () {
    assertEquals(17, ForumMessageLayout.headerAvatarLeft(false, 17, 301, 24));
    assertEquals(49, ForumMessageLayout.headerAuthorLeft(false, 17, 301, 24, 8, 123));
    assertEquals(259, ForumMessageLayout.headerTrailingLeft(false, 17, 301, 42));
    assertEquals(277, ForumMessageLayout.headerAvatarLeft(true, 17, 301, 24));
    assertEquals(146, ForumMessageLayout.headerAuthorLeft(true, 17, 301, 24, 8, 123));
    assertEquals(17, ForumMessageLayout.headerTrailingLeft(true, 17, 301, 42));
  }

  @Test public void authorHeaderMirrorsLocalBoundsWithoutMovingTheTrailingAdminSignIntoTheAuthor () {
    for (int left : new int[] {0, 17, 91}) {
      for (int width : new int[] {248, 348, 288, 248}) {
        int right = left + width;
        for (int avatarSize : new int[] {16, 20, 24}) {
          for (int gap : new int[] {0, 6, 8}) {
            for (int trailingWidth : new int[] {0, 42, 68}) {
              int authorBudget = width - avatarSize - gap - trailingWidth;
              for (int authorWidth : new int[] {0, 80, authorBudget}) {
                int ltrAvatar = ForumMessageLayout.headerAvatarLeft(false, left, right, avatarSize);
                int rtlAvatar = ForumMessageLayout.headerAvatarLeft(true, left, right, avatarSize);
                int ltrAuthor = ForumMessageLayout.headerAuthorLeft(false, left, right, avatarSize, gap, authorWidth);
                int rtlAuthor = ForumMessageLayout.headerAuthorLeft(true, left, right, avatarSize, gap, authorWidth);
                int ltrAdmin = ForumMessageLayout.headerTrailingLeft(false, left, right, trailingWidth);
                int rtlAdmin = ForumMessageLayout.headerTrailingLeft(true, left, right, trailingWidth);
                assertEquals(left + right - ltrAvatar - avatarSize, rtlAvatar);
                assertEquals(left + right - ltrAuthor - authorWidth, rtlAuthor);
                assertEquals(left + right - ltrAdmin - trailingWidth, rtlAdmin);
                assertEquals(left, ltrAvatar);
                assertEquals(right, rtlAvatar + avatarSize);
                assertEquals(gap, ltrAuthor - ltrAvatar - avatarSize);
                assertEquals(gap, rtlAvatar - rtlAuthor - authorWidth);
                assertEquals(right, ltrAdmin + trailingWidth);
                assertEquals(left, rtlAdmin);
                assertTrue("LTR author stays before admin sign", ltrAuthor + authorWidth <= ltrAdmin);
                assertTrue("RTL author stays after admin sign", rtlAdmin + trailingWidth <= rtlAuthor);
                assertTrue(ltrAuthor >= left && rtlAuthor + authorWidth <= right);
              }
            }
          }
        }
      }
    }
  }
}
