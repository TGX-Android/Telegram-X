package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;

public class ForumTopicEditorStateTest {
  private ForumTopicEditorState create () {
    ForumTopicEditorState state = ForumTopicEditorState.create();
    state.setName("Synthetic topic");
    return state;
  }

  private TdApi.Sticker defaultIcon (long emojiId) {
    TdApi.Sticker sticker = new TdApi.Sticker();
    TdApi.StickerFullTypeCustomEmoji type = new TdApi.StickerFullTypeCustomEmoji();
    type.customEmojiId = emojiId;
    sticker.fullType = type;
    return sticker;
  }

  @Test public void createStartsEmptyWithValidOrdinaryIcon () {
    ForumTopicEditorState state = ForumTopicEditorState.create();
    assertEquals("", state.name());
    assertEquals(0, state.emoji());
    assertTrue(ForumTopicPolicy.validColor(state.color()));
    assertFalse(state.hasChanges());
    assertFalse(state.canSubmit(true, true, false));
    assertEquals(ForumTopicEditorState.Error.EMPTY_NAME, state.validate(true, true, false));
  }

  @Test public void validationUsesTrimmedUnicodeCodePointsWithoutTruncatingInput () {
    ForumTopicEditorState state = create();
    state.setName(" \t ");
    assertEquals(ForumTopicEditorState.Error.EMPTY_NAME, state.validate(true, true, true));
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < 128; i++) name.append("\uD83D\uDE80");
    state.setName(" " + name + " ");
    assertTrue(state.canSubmit(true, true, true));
    state.setName(name + "x");
    assertEquals(ForumTopicEditorState.Error.LONG_NAME, state.validate(true, true, true));
    assertEquals(name + "x", state.name());
  }

  @Test public void unchangedEditAndWhitespaceOnlyChangesCannotBeSaved () {
    ForumTopicEditorState state = ForumTopicEditorState.edit("Synthetic", ForumTopicPolicy.ICON_COLORS[0], 0, false);
    assertFalse(state.canSubmit(true, true, true));
    state.setName(" Synthetic ");
    assertFalse(state.hasChanges());
    assertFalse(state.canSubmit(true, true, true));
    state.setName("Changed");
    assertTrue(state.canSubmit(true, true, true));
  }

  @Test public void defaultIconOnlyChangeEnablesSaveAndRevertingDisablesIt () {
    TdApi.Sticker[] defaults = {defaultIcon(31), defaultIcon(32)};
    ForumTopicEditorState state = ForumTopicEditorState.edit("Synthetic", ForumTopicPolicy.ICON_COLORS[0], 31, false);
    assertFalse(state.canSubmit(true, ForumTopicPolicy.allowedIcon(state.emoji(), false, defaults), true));

    assertTrue(state.setEmoji(32));
    assertEquals(state.originalName, state.name());
    assertTrue(state.hasChanges());
    assertTrue(state.changesEmoji());
    boolean iconAllowed = ForumTopicPolicy.allowedIcon(state.emoji(), false, defaults);
    assertEquals(ForumTopicEditorState.Error.NONE, state.validate(true, iconAllowed, true));
    assertTrue(state.canSubmit(true, iconAllowed, true));

    assertTrue(state.setEmoji(31));
    assertFalse(state.hasChanges());
    assertFalse(state.changesEmoji());
    assertFalse(state.canSubmit(true, ForumTopicPolicy.allowedIcon(state.emoji(), false, defaults), true));

    assertTrue(state.setEmoji(0));
    assertTrue(state.canSubmit(true, ForumTopicPolicy.allowedIcon(state.emoji(), false, defaults), true));
  }

  @Test public void reopenedAfterRenameCanSubmitDefaultIconOnlyEdit () {
    TdApi.Sticker[] defaults = {defaultIcon(31), defaultIcon(32)};
    ForumTopicEditorState previous = ForumTopicEditorState.edit("Synthetic", ForumTopicPolicy.ICON_COLORS[0], 31, false);
    previous.setName("Synthetic renamed");
    assertTrue(previous.beginSubmit(true, ForumTopicPolicy.allowedIcon(previous.emoji(), false, defaults), true));
    previous.succeeded(7);
    assertTrue(previous.claimCompletion());

    ForumTopicEditorState reopened = ForumTopicEditorState.edit(previous.submittedName(), previous.color(), previous.emoji(), false);
    assertFalse(reopened.canSubmit(true, ForumTopicPolicy.allowedIcon(reopened.emoji(), false, defaults), true));
    assertTrue(reopened.setEmoji(32));
    assertEquals(reopened.originalName, reopened.name());
    reopened = ForumTopicEditorState.restore(reopened.snapshot());
    boolean iconAllowed = ForumTopicPolicy.allowedIcon(reopened.emoji(), false, defaults);
    assertTrue(reopened.canSubmit(true, iconAllowed, true));
    assertTrue(reopened.beginSubmit(true, iconAllowed, true));
    assertFalse(reopened.canSubmit(true, iconAllowed, true));
  }

  @Test public void onlyCreateMayChangeRgbAndOnlyToPolicyColors () {
    ForumTopicEditorState creating = create();
    assertFalse(creating.setColor(0));
    assertTrue(creating.setColor(ForumTopicPolicy.ICON_COLORS[1]));
    ForumTopicEditorState editing = ForumTopicEditorState.edit("Synthetic", ForumTopicPolicy.ICON_COLORS[0], 0, false);
    assertFalse(editing.setColor(ForumTopicPolicy.ICON_COLORS[1]));
    assertEquals(editing.originalColor, editing.color());
  }

  @Test public void generalAllowsRenameButNeverEmojiOrColorChange () {
    ForumTopicEditorState state = ForumTopicEditorState.edit("General", ForumTopicPolicy.ICON_COLORS[0], 0, true);
    assertFalse(state.setEmoji(31));
    assertFalse(state.setEmoji(0));
    assertFalse(state.setColor(ForumTopicPolicy.ICON_COLORS[1]));
    state.setName("General renamed");
    assertTrue(state.canSubmit(true, true, true));
    assertFalse(state.changesEmoji());
  }

  @Test public void premiumRestrictionDoesNotInvalidateUnchangedExistingIcon () {
    ForumTopicEditorState state = ForumTopicEditorState.edit("Synthetic", ForumTopicPolicy.ICON_COLORS[0], 31, false);
    state.setName("Changed");
    assertTrue(state.canSubmit(true, false, false));
    assertFalse(state.changesEmoji());
    state.setEmoji(32);
    assertEquals(ForumTopicEditorState.Error.ICON_LOADING, state.validate(true, false, false));
    assertEquals(ForumTopicEditorState.Error.PREMIUM, state.validate(true, false, true));
    assertTrue(state.canSubmit(true, true, true));
    state.setEmoji(0);
    assertTrue(state.canSubmit(true, true, true));
  }

  @Test public void createCustomIconNeedsCurrentPolicyApproval () {
    ForumTopicEditorState state = create();
    state.setEmoji(31);
    assertFalse(state.beginSubmit(true, false, false));
    assertFalse(state.beginSubmit(true, false, true));
    assertTrue(state.beginSubmit(true, true, true));
  }

  @Test public void emojiIdentifiersAreOpaqueAndStillRequirePolicyApproval () {
    ForumTopicEditorState state = create();
    assertTrue(state.setEmoji(Long.MIN_VALUE + 7));
    assertFalse(state.canSubmit(true, false, true));
    assertTrue(state.canSubmit(true, true, true));
  }

  @Test public void revokedAccessPreventsSendingAndKeepsInput () {
    ForumTopicEditorState state = create();
    assertEquals(ForumTopicEditorState.Error.UNAVAILABLE, state.validate(false, true, true));
    assertFalse(state.beginSubmit(false, true, true));
    assertEquals("Synthetic topic", state.name());
    assertTrue(state.canSubmit(true, true, true));
  }

  @Test public void doubleSubmitAndMutationsAreBlockedWhilePending () {
    ForumTopicEditorState state = create();
    assertTrue(state.beginSubmit(true, true, true));
    assertFalse(state.beginSubmit(true, true, true));
    assertFalse(state.setName("Lost input"));
    assertFalse(state.setEmoji(31));
    assertFalse(state.setColor(ForumTopicPolicy.ICON_COLORS[1]));
    assertEquals("Synthetic topic", state.name());
  }

  @Test public void definiteServerFailureKeepsAllFieldsAndAllowsExplicitRetry () {
    ForumTopicEditorState state = create();
    state.setEmoji(31);
    state.setColor(ForumTopicPolicy.ICON_COLORS[2]);
    state.beginSubmit(true, true, true);
    state.failed(400, "Synthetic validation failure");
    assertEquals(ForumTopicEditorState.Phase.READY, state.phase());
    assertEquals("Synthetic topic", state.name());
    assertEquals(31, state.emoji());
    assertEquals(ForumTopicPolicy.ICON_COLORS[2], state.color());
    assertEquals("Synthetic validation failure", state.serverError());
    assertTrue(state.beginSubmit(true, true, true));
  }

  @Test public void uncertainCreateIsFrozenAndCannotBeRetried () {
    for (int code : new int[] {0, -1, 408, 500, 502, 503}) {
      ForumTopicEditorState state = create();
      state.beginSubmit(true, true, true);
      state.failed(code, "Synthetic connection failure");
      assertEquals(ForumTopicEditorState.Phase.UNKNOWN, state.phase());
      assertFalse(state.beginSubmit(true, true, true));
      assertFalse(state.setName("Another topic"));
      assertFalse(state.setEmoji(31));
      state.succeeded(7); // A late response may not silently override the reconciliation choice.
      assertEquals(ForumTopicEditorState.Phase.UNKNOWN, state.phase());
    }
  }

  @Test public void reconciliationOnlySuggestsExactOutgoingCandidates () {
    ForumTopicEditorState state = create();
    state.setEmoji(31);
    state.beginSubmit(true, true, true);
    state.failed(408, "Synthetic timeout");
    assertTrue(state.matchesCandidate("Synthetic topic", state.color(), 31, true));
    assertFalse(state.matchesCandidate("Other topic", state.color(), 31, true));
    assertFalse(state.matchesCandidate("Synthetic topic", state.color(), 32, true));
    assertFalse(state.matchesCandidate("Synthetic topic", 0, 31, true));
    assertFalse(state.matchesCandidate("Synthetic topic", state.color(), 31, false));
    assertFalse(state.resolveToExisting(0));
    assertTrue(state.resolveToExisting(7));
    assertEquals(7, state.completedTopicId());
    assertTrue(state.claimCompletion());
    assertFalse(state.claimCompletion());
  }

  @Test public void successfulCompletionIsConsumedOnceEvenAfterRestoration () {
    ForumTopicEditorState state = create();
    state.beginSubmit(true, true, true);
    state.succeeded(7);
    state = ForumTopicEditorState.restore(state.snapshot());
    assertTrue(state.claimCompletion());
    assertFalse(ForumTopicEditorState.restore(state.snapshot()).claimCompletion());
    assertFalse(state.beginSubmit(true, true, true));
  }

  @Test public void restoredPendingCreateBecomesUnknownWithoutResending () {
    ForumTopicEditorState state = create();
    state.setEmoji(31);
    state.beginSubmit(true, true, true);
    ForumTopicEditorState restored = ForumTopicEditorState.restore(state.snapshot());
    assertEquals(ForumTopicEditorState.Phase.UNKNOWN, restored.phase());
    assertEquals(state.name(), restored.name());
    assertEquals(state.emoji(), restored.emoji());
    assertFalse(restored.canSubmit(true, true, true));
  }

  @Test public void snapshotSerializationPreservesUnsavedFieldsAndInlineError () throws Exception {
    ForumTopicEditorState state = create();
    state.setColor(ForumTopicPolicy.ICON_COLORS[2]);
    state.setEmoji(31);
    state.beginSubmit(true, true, true);
    state.failed(400, "Synthetic error");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(state.snapshot()); }
    ForumTopicEditorState.Snapshot saved;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      saved = (ForumTopicEditorState.Snapshot) input.readObject();
    }
    ForumTopicEditorState restored = ForumTopicEditorState.restore(saved);
    assertEquals(state.name(), restored.name());
    assertEquals(state.color(), restored.color());
    assertEquals(state.emoji(), restored.emoji());
    assertEquals(state.serverError(), restored.serverError());
    assertTrue(restored.canSubmit(true, true, true));
  }

  @Test public void restoredEditKeepsInputAndNeverChangesContextOrRgb () {
    ForumTopicEditorState state = ForumTopicEditorState.edit("Synthetic", ForumTopicPolicy.ICON_COLORS[4], 31, false);
    state.setName("Changed");
    state.beginSubmit(true, true, true);
    ForumTopicEditorState restored = ForumTopicEditorState.restore(state.snapshot());
    assertFalse(restored.creating);
    assertEquals("Synthetic", restored.originalName);
    assertEquals("Changed", restored.name());
    assertEquals(state.originalColor, restored.color());
    assertFalse(restored.changesEmoji());
    assertFalse(restored.beginSubmit(false, true, true));
  }
}
