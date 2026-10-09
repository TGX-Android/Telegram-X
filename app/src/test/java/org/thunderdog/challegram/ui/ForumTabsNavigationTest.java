package org.thunderdog.challegram.ui;

import org.junit.Test;
import org.thunderdog.challegram.data.ForumTopicEditorState;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Pure routing policies with synthetic identities; no Android views, transport or user data. */
public class ForumTabsNavigationTest {
  private static final long CHAT = 700;
  private static final int ACCOUNT = 2;

  private static final class Page {
    final int account;
    final long chat;
    final boolean live, host, removable;

    Page (int account, long chat, boolean live, boolean host, boolean removable) {
      this.account = account;
      this.chat = chat;
      this.live = live;
      this.host = host;
      this.removable = removable;
    }

    boolean matches () { return live && account == ACCOUNT && chat == CHAT; }
  }

  private Page host () { return new Page(ACCOUNT, CHAT, true, true, false); }
  private Page profile () { return new Page(ACCOUNT, CHAT, true, false, true); }
  private int nearest (List<Page> stack, int owner) {
    return ForumTabsNavigation.nearestHostIndex(stack, owner, page -> page.matches() && page.host);
  }
  private boolean unwind (List<Page> stack, int owner, int host) {
    return ForumTabsNavigation.canUnwind(stack, owner, host, page -> page.matches() && page.removable);
  }

  @Test public void allIsExplicitEvenWhenOwnerAndHostHaveTypedTopics () {
    assertEquals(0, ForumTabsNavigation.viewModeTopic(false, 17, 31));
    assertEquals(0, ForumTabsNavigation.viewModeTopic(false, 0, 31));
  }

  @Test public void showTopicsReturnsToTheProfileOrEditorTypedTopic () {
    assertEquals(17, ForumTabsNavigation.viewModeTopic(true, 17, 31));
    assertEquals(17, ForumTabsNavigation.viewModeTopic(true, 17, 0));
    assertEquals(1, ForumTabsNavigation.viewModeTopic(true, 1, 0)); // General is typed, not All.
  }

  @Test public void showTopicsWithoutTypedContextKeepsTheHostSelection () {
    assertEquals(31, ForumTabsNavigation.viewModeTopic(true, 0, 31));
    assertEquals(0, ForumTabsNavigation.viewModeTopic(true, 0, 0));
    assertEquals(0, ForumTabsNavigation.viewModeTopic(true, 0, -1));
  }

  @Test public void ownerHostIsUsedDirectly () {
    List<Page> stack = Arrays.asList(host(), profile(), host());
    assertEquals(2, nearest(stack, 2));
    assertTrue(unwind(stack, 2, 2));
  }

  @Test public void nearestMatchingHostWinsWithoutLookingAhead () {
    List<Page> stack = Arrays.asList(host(), host(), profile(), host());
    assertEquals(1, nearest(stack, 2));
    assertEquals(0, nearest(stack, 0));
  }

  @Test public void otherAccountChatDestroyedAndNonHostMessagesCannotBecomeHost () {
    List<Page> stack = Arrays.asList(host(),
      new Page(3, CHAT, true, true, false),
      new Page(ACCOUNT, CHAT + 1, true, true, false),
      new Page(ACCOUNT, CHAT, false, true, false),
      new Page(ACCOUNT, CHAT, true, false, false), profile());
    assertEquals(0, nearest(stack, 5));
    assertFalse(unwind(stack, 5, 0));
  }

  @Test public void missingOrDetachedOwnerCannotReuseAnArbitraryHost () {
    List<Page> stack = Arrays.asList(profile(), host());
    assertEquals(-1, nearest(stack, 0));
    assertEquals(-1, nearest(stack, -1));
    assertEquals(-1, nearest(stack, stack.size()));
  }

  @Test public void onlyOwnProfileAndSafeEditorChainMayBeUnwound () {
    List<Page> stack = Arrays.asList(host(), profile(), profile());
    assertTrue(unwind(stack, 2, 0));
    assertEquals(3, stack.size()); // Planning is read-only.
  }

  @Test public void unrelatedScreenOrDirtyEditorBlocksTheEntireUnwind () {
    List<Page> stack = Arrays.asList(host(), new Page(ACCOUNT, CHAT, true, false, false), profile());
    assertEquals(0, nearest(stack, 2)); // Existing host still prevents duplicate fallback.
    assertFalse(unwind(stack, 2, 0));
    assertEquals(3, stack.size());
  }

  @Test public void otherChatAccountAndDestroyedProfilesCannotBeRemoved () {
    for (Page unrelated : Arrays.asList(
      new Page(ACCOUNT + 1, CHAT, true, false, true),
      new Page(ACCOUNT, CHAT + 1, true, false, true),
      new Page(ACCOUNT, CHAT, false, false, true))) {
      assertFalse(unwind(Arrays.asList(host(), unrelated, profile()), 2, 0));
    }
  }

  @Test public void unsupportedOwnerAndInvalidIndicesCannotUnwind () {
    List<Page> stack = Arrays.asList(host(), new Page(ACCOUNT, CHAT, true, false, false));
    assertFalse(unwind(stack, 1, 0));
    assertFalse(unwind(stack, 1, -1));
    assertFalse(unwind(stack, 0, 1));
    assertFalse(unwind(stack, 2, 0));
  }

  private ForumTopicEditorState submitted () {
    ForumTopicEditorState form = ForumTopicEditorState.create();
    form.setName("Synthetic new topic");
    assertTrue(form.beginSubmit(true, true, true));
    return form;
  }

  @Test public void emptyEditorIsSafeButDirtyPendingAndFailedEditorsAreNot () {
    assertFalse(ForumTabsNavigation.canLeaveEditor(null));
    ForumTopicEditorState form = ForumTopicEditorState.create();
    assertTrue(ForumTabsNavigation.canLeaveEditor(form));
    form.setName("Synthetic draft");
    assertFalse(ForumTabsNavigation.canLeaveEditor(form));
    assertTrue(form.beginSubmit(true, true, true));
    assertFalse(ForumTabsNavigation.canLeaveEditor(form));
    form.failed(403, "Synthetic denied");
    assertFalse(ForumTabsNavigation.canLeaveEditor(form));
    assertEquals("Synthetic draft", form.name());
    assertFalse(form.claimCompletion());
  }

  @Test public void successfulCreationReturnsTheNewTypedIdAndCompletesOnce () {
    ForumTopicEditorState form = submitted();
    form.succeeded(37);
    assertTrue(ForumTabsNavigation.canLeaveEditor(form));
    assertEquals(37, form.completedTopicId());
    assertTrue(form.claimCompletion());
    assertFalse(form.claimCompletion());
    assertFalse(ForumTopicEditorState.restore(form.snapshot()).claimCompletion());
    assertFalse(form.beginSubmit(true, true, true));
  }

  @Test public void uncertainCreationCannotLeaveUntilExplicitlyReconciled () {
    ForumTopicEditorState form = submitted();
    form.failed(408, "Synthetic timeout");
    assertFalse(ForumTabsNavigation.canLeaveEditor(form));
    assertFalse(form.claimCompletion());
    form.succeeded(37); // Late responses do not override reconciliation.
    assertFalse(ForumTabsNavigation.canLeaveEditor(form));
    assertFalse(form.resolveToExisting(0));
    assertTrue(form.resolveToExisting(41));
    assertTrue(ForumTabsNavigation.canLeaveEditor(form));
    assertEquals(41, form.completedTopicId());
    assertTrue(form.claimCompletion());
    assertFalse(form.claimCompletion());
  }

  @Test public void malformedSuccessCannotNavigateToAllInsteadOfTheNewTopic () {
    ForumTopicEditorState form = submitted();
    form.succeeded(0);
    assertFalse(ForumTabsNavigation.canLeaveEditor(form));
    assertFalse(form.claimCompletion());
  }

  @Test public void restoredPendingCreateMustReconcileWithoutSendingAgain () {
    ForumTopicEditorState form = ForumTopicEditorState.restore(submitted().snapshot());
    assertEquals(ForumTopicEditorState.Phase.UNKNOWN, form.phase());
    assertFalse(ForumTabsNavigation.canLeaveEditor(form));
    assertFalse(form.beginSubmit(true, true, true));
    assertTrue(form.resolveToExisting(43));
    form = ForumTopicEditorState.restore(form.snapshot());
    assertTrue(ForumTabsNavigation.canLeaveEditor(form));
    assertEquals(43, form.completedTopicId());
    assertTrue(form.claimCompletion());
  }
}
