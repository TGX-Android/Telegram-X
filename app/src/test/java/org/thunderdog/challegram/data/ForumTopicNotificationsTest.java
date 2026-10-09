package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ForumTopicNotificationsTest {
  private static TdApi.ChatNotificationSettings settings () {
    return new TdApi.ChatNotificationSettings(true, 42, false, 123, false, true, false, true, false, 456, false, true, false, true, false, true);
  }

  private static void unchangedExcept (TdApi.ChatNotificationSettings before, TdApi.ChatNotificationSettings after, String... edited) throws Exception {
    assertNotSame(before, after);
    Set<String> excluded = new HashSet<>(Arrays.asList(edited));
    for (Field field : TdApi.ChatNotificationSettings.class.getFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && !excluded.contains(field.getName())) assertEquals(field.getName(), field.get(before), field.get(after));
    }
  }

  @Test public void fastUnmuteOverridesMutedGroupWithoutChangingAnythingElse () throws Exception {
    TdApi.ChatNotificationSettings source = settings();
    TdApi.ChatNotificationSettings result = ForumTopicNotifications.toggleMute(source, true);
    assertFalse(result.useDefaultMuteFor); assertEquals(0, result.muteFor);
    assertFalse(ForumPresentation.isMuted(result, true));
    assertTrue(source.useDefaultMuteFor); assertEquals(42, source.muteFor);
    unchangedExcept(source, result, "useDefaultMuteFor", "muteFor");
  }

  @Test public void fastMuteUsesExplicitOverrideEvenWithUnmutedDefault () throws Exception {
    TdApi.ChatNotificationSettings source = settings();
    TdApi.ChatNotificationSettings result = ForumTopicNotifications.toggleMute(source, false);
    assertFalse(result.useDefaultMuteFor); assertEquals(Integer.MAX_VALUE, result.muteFor);
    assertTrue(ForumPresentation.isMuted(result, false));
    unchangedExcept(source, result, "useDefaultMuteFor", "muteFor");
  }

  @Test public void explicitTopicOverrideWinsOverGroupAndTogglesIndependently () {
    TdApi.ChatNotificationSettings source = settings(); source.useDefaultMuteFor = false; source.muteFor = 0;
    assertEquals(Integer.MAX_VALUE, ForumTopicNotifications.toggleMute(source, true).muteFor);
    source.muteFor = 3600;
    assertEquals(0, ForumTopicNotifications.toggleMute(source, false).muteFor);
  }

  @Test public void inheritanceAndDurationsPreserveAllOtherFields () throws Exception {
    TdApi.ChatNotificationSettings source = settings();
    for (int duration : new int[] {0, 3600, 86400, Integer.MAX_VALUE}) {
      TdApi.ChatNotificationSettings result = ForumTopicNotifications.mute(source, false, duration);
      assertFalse(result.useDefaultMuteFor); assertEquals(duration, result.muteFor);
      unchangedExcept(source, result, "useDefaultMuteFor", "muteFor");
    }
    TdApi.ChatNotificationSettings inherited = ForumTopicNotifications.mute(source, true, 3600);
    assertTrue(inherited.useDefaultMuteFor); assertEquals(0, inherited.muteFor);
    unchangedExcept(source, inherited, "useDefaultMuteFor", "muteFor");
  }

  @Test public void exactCopyPreservesEvenInactiveOverrideValues () throws Exception {
    TdApi.ChatNotificationSettings source = settings(); unchangedExcept(source, ForumTopicNotifications.copy(source));
  }

  @Test public void soundNeverResetsMuteOrStorySettings () throws Exception {
    TdApi.ChatNotificationSettings source = settings();
    for (long soundId : new long[] {0, 789}) {
      TdApi.ChatNotificationSettings result = ForumTopicNotifications.sound(source, false, soundId);
      assertFalse(result.useDefaultSound); assertEquals(soundId, result.soundId);
      unchangedExcept(source, result, "useDefaultSound", "soundId");
    }
  }

  @Test public void everySupportedBooleanHasIndependentInheritanceAndNoOtherChanges () throws Exception {
    TdApi.ChatNotificationSettings source = settings();
    for (ForumTopicNotifications.Field field : ForumTopicNotifications.Field.values()) {
      String inherit = field == ForumTopicNotifications.Field.PREVIEW ? "useDefaultShowPreview" : field == ForumTopicNotifications.Field.PINNED ? "useDefaultDisablePinnedMessageNotifications" : "useDefaultDisableMentionNotifications";
      String value = field == ForumTopicNotifications.Field.PREVIEW ? "showPreview" : field == ForumTopicNotifications.Field.PINNED ? "disablePinnedMessageNotifications" : "disableMentionNotifications";
      for (boolean useDefault : new boolean[] {false, true}) for (boolean enabled : new boolean[] {false, true}) {
        TdApi.ChatNotificationSettings result = ForumTopicNotifications.field(source, field, useDefault, enabled);
        assertEquals(useDefault, TdApi.ChatNotificationSettings.class.getField(inherit).get(result));
        assertEquals(field == ForumTopicNotifications.Field.PREVIEW ? enabled : !enabled, TdApi.ChatNotificationSettings.class.getField(value).get(result));
        unchangedExcept(source, result, inherit, value);
      }
    }
  }
}
