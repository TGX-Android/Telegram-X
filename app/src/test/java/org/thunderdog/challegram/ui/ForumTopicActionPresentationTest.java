package org.thunderdog.challegram.ui;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import org.thunderdog.challegram.data.ForumTopicSelection;
import org.thunderdog.challegram.data.ForumTopicSelection.Action;
import org.thunderdog.challegram.telegram.ForumTopicActions;

import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.Assert.*;
import static org.thunderdog.challegram.data.ForumTopicSelection.Action.*;

/** Synthetic presentation checks: no Android views, user data or transport. */
public class ForumTopicActionPresentationTest {
  private ForumTopicActions.BatchOutcome outcome (int id, TdApi.Error error, boolean uncertain) {
    return new ForumTopicActions.BatchOutcome(new ForumTopicActions.BatchTarget(id, "Synthetic " + id), error, uncertain);
  }

  private ForumTopicActions.BatchOutcome ok (int id) { return outcome(id, null, false); }
  private ForumTopicActions.BatchOutcome denied (int id) { return outcome(id, new TdApi.Error(403, "CHAT_ADMIN_REQUIRED"), false); }
  private ForumTopicActions.BatchResult result (Action action, ForumTopicActions.BatchOutcome... outcomes) {
    return new ForumTopicActions.BatchResult(action, Arrays.asList(outcomes));
  }

  @Test public void noCommonActionsHasNoOverflow () {
    assertTrue(ForumTopicActionPresentation.overflowActions(EnumSet.noneOf(Action.class), EnumSet.noneOf(Action.class)).isEmpty());
  }

  @Test public void allCommonActionsAlreadyInHeaderHasNoOverflow () {
    assertTrue(ForumTopicActionPresentation.overflowActions(EnumSet.of(MUTE), EnumSet.of(MUTE)).isEmpty());
  }

  @Test public void onlyRemainingCommonActionsAppearInOverflow () {
    assertEquals(EnumSet.of(CLOSE, READ_MENTIONS), ForumTopicActionPresentation.overflowActions(EnumSet.of(PIN, MUTE, CLOSE, READ_MENTIONS), EnumSet.of(PIN, MUTE)));
  }

  @Test public void noDirectSlotsKeepsEveryCommonActionInOverflow () {
    assertEquals(0, ForumTopicSelection.primaryActionSlots(264, 2));
    EnumSet<Action> common = EnumSet.of(PIN, MUTE, CLOSE);
    assertEquals(common, ForumTopicActionPresentation.overflowActions(common, EnumSet.noneOf(Action.class)));
  }

  @Test public void narrowHeaderKeepsDirectCapableSpilloverActionsInOverflow () {
    assertEquals(1, ForumTopicSelection.primaryActionSlots(264, 1));
    assertEquals(EnumSet.of(MUTE, DELETE), ForumTopicActionPresentation.overflowActions(EnumSet.of(PIN, MUTE, DELETE), EnumSet.of(PIN)));
  }

  @Test public void revokedOverflowActionCannotProduceEmptySheet () {
    EnumSet<Action> header = EnumSet.of(MUTE);
    assertEquals(EnumSet.of(CLOSE), ForumTopicActionPresentation.overflowActions(EnumSet.of(MUTE, CLOSE), header));
    assertTrue(ForumTopicActionPresentation.overflowActions(EnumSet.of(MUTE), header).isEmpty());
  }

  @Test public void directionChangeDoesNotHideNewActionBehindStaleHeader () {
    assertEquals(EnumSet.of(UNMUTE), ForumTopicActionPresentation.overflowActions(EnumSet.of(UNMUTE), EnumSet.of(MUTE)));
  }

  @Test public void overflowDoesNotMutateCommonOrHeaderActions () {
    EnumSet<Action> common = EnumSet.of(PIN, MUTE), header = EnumSet.of(PIN);
    ForumTopicActionPresentation.overflowActions(common, header).clear();
    assertEquals(EnumSet.of(PIN, MUTE), common);
    assertEquals(EnumSet.of(PIN), header);
  }

  @Test public void completeSuccessHasNoErrorNotice () {
    assertNull(ForumTopicActionPresentation.failure(result(MUTE, ok(17), ok(18))));
  }

  @Test public void unexpectedEmptyResultIsNotMistakenForSuccess () {
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result(MUTE));
    assertNotNull(notice);
    assertNull(notice.error);
    assertTrue(notice.retryTargets.isEmpty());
  }

  @Test public void partialFailureOffersOnlyFailedTargetWithRetrySemantics () {
    ForumTopicActions.BatchOutcome failure = denied(18);
    ForumTopicActions.BatchResult result = result(CLOSE, ok(17), failure, ok(19));
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result);
    assertNotNull(notice);
    assertSame(failure.error, notice.error);
    assertFalse(notice.uncertain);
    assertEquals(1, notice.retryTargets.size());
    assertEquals(18, notice.retryTargets.get(0).topicId);
    assertTrue(notice.retryTargets.get(0).retrying);
    assertTrue(result.outcomes.get(0).getSuccessful());
    assertFalse(result.outcomes.get(1).getSuccessful());
    assertTrue(result.outcomes.get(2).getSuccessful());
  }

  @Test public void sameCauseAcrossAllFailuresUsesOneHumaneMessage () {
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result(PIN, denied(17), denied(18)));
    assertEquals(403, notice.error.code);
    assertEquals(2, notice.retryTargets.size());
  }

  @Test public void differentCausesUseGenericErrorInsteadOfMisleadingSingleCause () {
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result(MUTE, denied(17), outcome(18, new TdApi.Error(500, "Synthetic server failure"), false)));
    assertNotNull(notice);
    assertNull(notice.error);
    assertEquals(2, notice.retryTargets.size());
  }

  @Test public void preflightSkippedSiblingDoesNotMaskActualCause () {
    ForumTopicActions.BatchOutcome skipped = outcome(17, new TdApi.Error(ForumTopicActions.BATCH_PREFLIGHT_ABORTED, "Synthetic unsent"), false);
    ForumTopicActions.BatchOutcome denied = denied(18);
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result(CLOSE, skipped, denied));
    assertSame(denied.error, notice.error);
    assertEquals(2, notice.retryTargets.size());
  }

  @Test public void preflightWithoutSpecificCauseStillShowsError () {
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result(CLOSE, outcome(17, new TdApi.Error(ForumTopicActions.BATCH_PREFLIGHT_ABORTED, "Synthetic unsent"), false)));
    assertNotNull(notice);
    assertNull(notice.error);
    assertEquals(1, notice.retryTargets.size());
  }

  @Test public void uncertainDestructiveTimeoutWarnsWithoutOfferingRetry () {
    ForumTopicActions.BatchResult result = result(DELETE, outcome(17, new TdApi.Error(408, "Synthetic timeout"), true), ok(18));
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result);
    assertNotNull(notice);
    assertTrue(notice.uncertain);
    assertTrue(notice.retryTargets.isEmpty());
    assertFalse(result.outcomes.get(0).getSuccessful());
  }

  @Test public void mixedUncertainAndDefiniteFailureRetriesOnlyDefiniteFailure () {
    ForumTopicActions.BatchOutcome denied = denied(18);
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result(DELETE, outcome(17, new TdApi.Error(500, "Synthetic unknown outcome"), true), denied, ok(19)));
    assertTrue(notice.uncertain);
    assertSame(denied.error, notice.error);
    assertEquals(1, notice.retryTargets.size());
    assertEquals(18, notice.retryTargets.get(0).topicId);
  }

  @Test public void nonDestructiveTimeoutStillShowsErrorAndAllowsExplicitRetry () {
    ForumTopicActionPresentation.BatchFailure notice = ForumTopicActionPresentation.failure(result(MUTE, outcome(17, new TdApi.Error(408, "Synthetic timeout"), false)));
    assertNotNull(notice);
    assertFalse(notice.uncertain);
    assertEquals(408, notice.error.code);
    assertEquals(1, notice.retryTargets.size());
  }

  @Test public void translatedErrorsRemainReadable () {
    assertEquals("Synthetic readable explanation", ForumTopicActionPresentation.readableError(new TdApi.Error(403, "SYNTHETIC_DENIED"), "Synthetic readable explanation", "Fallback"));
  }

  @Test public void rawDiagnosticErrorsUseHumaneFallback () {
    TdApi.Error error = new TdApi.Error(500, "SYNTHETIC_BACKEND_FAILURE");
    assertEquals("Fallback", ForumTopicActionPresentation.readableError(error, "#500: SYNTHETIC_BACKEND_FAILURE", "Fallback"));
  }

  @Test public void emptyAndAbortedTranslationsDoNotSwallowErrors () {
    TdApi.Error error = new TdApi.Error(500, "Synthetic error");
    assertEquals("Fallback", ForumTopicActionPresentation.readableError(error, null, "Fallback"));
    assertEquals("Fallback", ForumTopicActionPresentation.readableError(error, "", "Fallback"));
    assertEquals("Fallback", ForumTopicActionPresentation.readableError(error, "  ", "Fallback"));
  }
}
