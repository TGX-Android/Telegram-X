/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.ImmutableIntArray;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CommandButton}. */
@RunWith(AndroidJUnit4.class)
public class CommandButtonTest {

  @Test
  public void
      isButtonCommandAvailable_playerCommandAvailableOrUnavailableInPlayerCommands_isEnabledCorrectly() {
    CommandButton button =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .build();
    Player.Commands availablePlayerCommands =
        Player.Commands.EMPTY.buildUpon().add(Player.COMMAND_SEEK_TO_NEXT).build();

    assertThat(
            CommandButton.isButtonCommandAvailable(
                button, SessionCommands.EMPTY, Player.Commands.EMPTY))
        .isFalse();
    assertThat(
            CommandButton.isButtonCommandAvailable(
                button, SessionCommands.EMPTY, availablePlayerCommands))
        .isTrue();
  }

  @Test
  public void isButtonCommandAvailable_sessionCommandAvailableOrUnavailable_isEnabledCorrectly() {
    SessionCommand command1 = new SessionCommand("command1", Bundle.EMPTY);
    CommandButton button =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command1)
            .build();
    SessionCommands availableSessionCommands =
        SessionCommands.EMPTY.buildUpon().add(command1).build();

    assertThat(
            CommandButton.isButtonCommandAvailable(
                button, SessionCommands.EMPTY, Player.Commands.EMPTY))
        .isFalse();
    assertThat(
            CommandButton.isButtonCommandAvailable(
                button, availableSessionCommands, Player.Commands.EMPTY))
        .isTrue();
  }

  @Test
  public void copyWithUnavailableButtonsDisabled() {
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();
    SessionCommand command2 = new SessionCommand("command2", Bundle.EMPTY);
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button2")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(command2)
            .build();
    SessionCommands availableSessionCommands =
        SessionCommands.EMPTY.buildUpon().add(command2).build();
    Player.Commands availablePlayerCommands =
        Player.Commands.EMPTY.buildUpon().add(Player.COMMAND_SEEK_TO_PREVIOUS).build();

    assertThat(
            CommandButton.copyWithUnavailableButtonsDisabled(
                ImmutableList.of(button1, button2), SessionCommands.EMPTY, Player.Commands.EMPTY))
        .containsExactly(button1.copyWithIsEnabled(false), button2.copyWithIsEnabled(false));
    assertThat(
            CommandButton.copyWithUnavailableButtonsDisabled(
                ImmutableList.of(button1, button2),
                availableSessionCommands,
                availablePlayerCommands))
        .containsExactly(button1.copyWithIsEnabled(true), button2.copyWithIsEnabled(true));
  }

  @Test
  public void getIconUri_returnsUri() {
    Uri uri = Uri.parse("content://test");
    CommandButton button =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setIconUri(uri)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();

    assertThat(button.iconUri).isEqualTo(uri);
  }

  @Test
  public void getIconUri_returnsNullIfUnset() {
    CommandButton button =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();

    assertThat(button.iconUri).isNull();
  }

  @Test
  public void getIconUri_returnsUriAfterSerialisation() {
    Uri uri = Uri.parse("content://test");
    CommandButton button =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setIconUri(uri)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();

    CommandButton serialisedButton =
        CommandButton.fromBundle(button.toBundle(), MediaSessionStub.VERSION_INT);

    assertThat(serialisedButton.iconUri).isEqualTo(uri);
  }

  @Test
  public void getIconUri_returnsNullIfUnsetAfterSerialisation() {
    CommandButton button =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();

    CommandButton serialisedButton =
        CommandButton.fromBundle(button.toBundle(), MediaSessionStub.VERSION_INT);

    assertThat(serialisedButton.iconUri).isNull();
  }

  @Test
  public void equals() {
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setIconUri(Uri.parse("content://test"))
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_CENTRAL)
                .build())
        .isEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setIconUri(Uri.parse("content://test"))
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_CENTRAL)
                .build());
  }

  @Test
  public void equals_minimalDifference_notEqual() {
    CommandButton button =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setSlots(CommandButton.SLOT_BACK)
            .build();

    assertThat(button)
        .isEqualTo(CommandButton.fromBundle(button.toBundle(), MediaSessionStub.VERSION_INT));
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button2")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.media3_icon_play)
                .setDisplayName("button")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setEnabled(false)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setIconUri(Uri.parse("content://test"))
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    assertThat(button)
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build());
  }

  @Test
  public void equals_differenceInExtras_ignored() {
    CommandButton.Builder builder =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setCustomIconResId(R.drawable.media3_notification_small_icon);
    CommandButton button1 = builder.build();
    Bundle extras2 = new Bundle();
    extras2.putInt("something", 0);
    Bundle extras3 = new Bundle();
    extras3.putInt("something", 1);
    extras3.putInt("something2", 2);

    assertThat(button1).isEqualTo(builder.setExtras(extras2).build());
    assertThat(builder.setExtras(extras2).build()).isEqualTo(builder.setExtras(extras3).build());
  }

  @Test
  public void equals_differencesInSessionCommand_notEqual() {
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                .setDisplayName("button")
                .build())
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
                .setDisplayName("button")
                .build());
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                .setDisplayName("button")
                .build())
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setSessionCommand(new SessionCommand("customAction", Bundle.EMPTY))
                .setDisplayName("button")
                .build());
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setSessionCommand(new SessionCommand("customAction", Bundle.EMPTY))
                .setDisplayName("button")
                .build())
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setSessionCommand(new SessionCommand("customAction2", Bundle.EMPTY))
                .setDisplayName("button")
                .build());
  }

  @Test
  public void equals_differenceInSessionCommandExtras_ignored() {
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setSessionCommand(new SessionCommand("customAction", Bundle.EMPTY))
                .setDisplayName("button")
                .build())
        .isEqualTo(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setExtras(extras)
                .setSessionCommand(new SessionCommand("customAction", extras))
                .setDisplayName("button")
                .build());
  }

  @Test
  public void hashCode_equalButtons_sameHashcode() {
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode())
        .isEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode());
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode())
        .isNotEqualTo(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName("button")
                .setCustomIconResId(R.drawable.media3_notification_small_icon)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .hashCode());
  }

  @Test
  public void build_withoutSessionOrPlayerCommandSet_throwsIllegalStateException() {
    CommandButton.Builder builder =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button")
            .setCustomIconResId(R.drawable.media3_notification_small_icon);
    assertThrows(IllegalStateException.class, builder::build);
  }

  @Test
  public void build_withoutSlots_assignsDefaultSlots() {
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_CENTRAL));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_CENTRAL));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(Player.COMMAND_STOP)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_CENTRAL));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_BACK));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_BACK));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_BACK));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_BACK));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_BACK));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_BACK));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_BACK));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_FORWARD));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_FORWARD));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_FORWARD));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_FORWARD));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_FORWARD));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_FORWARD));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_FORWARD));
    assertThat(
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                .build()
                .slots)
        .isEqualTo(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW));
  }

  @Test
  public void fromBundle_afterToBundle_returnsEqualInstance() {
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    CommandButton buttonWithSessionCommand =
        new CommandButton.Builder(CommandButton.ICON_CLOSED_CAPTIONS)
            .setDisplayName("name")
            .setEnabled(true)
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setIconUri(Uri.parse("content://test"))
            .setExtras(extras)
            .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
            .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_BACK)
            .build();
    CommandButton buttonWithPlayerCommand =
        new CommandButton.Builder(CommandButton.ICON_CLOSED_CAPTIONS)
            .setDisplayName("name")
            .setEnabled(true)
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setIconUri(Uri.parse("content://test"))
            .setExtras(extras)
            .setPlayerCommand(Player.COMMAND_GET_METADATA)
            .setSlots(CommandButton.SLOT_CENTRAL)
            .build();
    CommandButton buttonWithDefaultValues =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build();

    CommandButton restoredButtonWithSessionCommand =
        CommandButton.fromBundle(buttonWithSessionCommand.toBundle(), MediaSessionStub.VERSION_INT);
    CommandButton restoredButtonWithPlayerCommand =
        CommandButton.fromBundle(buttonWithPlayerCommand.toBundle(), MediaSessionStub.VERSION_INT);
    CommandButton restoredButtonWithDefaultValues =
        CommandButton.fromBundle(buttonWithDefaultValues.toBundle(), MediaSessionStub.VERSION_INT);

    assertThat(restoredButtonWithSessionCommand).isEqualTo(buttonWithSessionCommand);
    assertThat(restoredButtonWithSessionCommand.extras.get("key")).isEqualTo("value");
    assertThat(restoredButtonWithPlayerCommand).isEqualTo(buttonWithPlayerCommand);
    assertThat(restoredButtonWithPlayerCommand.extras.get("key")).isEqualTo("value");
    assertThat(restoredButtonWithDefaultValues).isEqualTo(buttonWithDefaultValues);
  }

  @Test
  public void fromBundle_withSessionInterfaceVersionLessThan3_setsEnabledToTrue() {
    CommandButton buttonWithEnabledFalse =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setEnabled(false)
            .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
            .build();

    CommandButton restoredButtonAssumingOldSessionInterface =
        CommandButton.fromBundle(
            buttonWithEnabledFalse.toBundle(), /* sessionInterfaceVersion= */ 2);

    assertThat(restoredButtonAssumingOldSessionInterface.isEnabled).isTrue();
  }

  @Test
  public void getCustomLayoutFromMediaButtonPreferences_noBackForwardSlots_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ true, /* forwardSlotAllowed= */ true);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void getCustomLayoutFromMediaButtonPreferences_withBackSlot_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ true, /* forwardSlotAllowed= */ true);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getCustomLayoutFromMediaButtonPreferences_withBackSlotButNoBackSlotAllowed_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ false, /* forwardSlotAllowed= */ true);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void getCustomLayoutFromMediaButtonPreferences_withForwardSlot_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ true, /* forwardSlotAllowed= */ true);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getCustomLayoutFromMediaButtonPreferences_withForwardSlotButNoForwardSlotAllowed_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ true, /* forwardSlotAllowed= */ false);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getCustomLayoutFromMediaButtonPreferences_withForwardAndBackSlot_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(new SessionCommand("action5", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_BACK)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ true, /* forwardSlotAllowed= */ true);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(new SessionCommand("action5", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getCustomLayoutFromMediaButtonPreferences_withForwardAndBackSlotButNoForwardBackSlotsAllowed_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(new SessionCommand("action5", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_BACK)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ false, /* forwardSlotAllowed= */ false);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(new SessionCommand("action4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getCustomLayoutFromMediaButtonPreferences_disabledAndNonCustomCommands_returnsCorrectButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_BLOCK)
                .setSessionCommand(new SessionCommand("action2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(false)
                .build());

    ImmutableList<CommandButton> customLayout =
        CommandButton.getCustomLayoutFromMediaButtonPreferences(
            mediaButtonPreferences, /* backSlotAllowed= */ true, /* forwardSlotAllowed= */ true);

    assertThat(customLayout)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("action1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build());
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withPrevAndNextCommands_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, false);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, false);
    Player.Commands playerCommands =
        new Player.Commands.Builder()
            .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
            .build();

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withPrevCommandNoNextReservation_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, false);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, false);
    Player.Commands playerCommands =
        new Player.Commands.Builder().addAll(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build();

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withPrevCommandAndNextReservation_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, false);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true);
    Player.Commands playerCommands =
        new Player.Commands.Builder().addAll(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build();

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withNextCommandNoPrevReservation_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, false);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, false);
    Player.Commands playerCommands =
        new Player.Commands.Builder().addAll(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build();

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withNextCommandAndPrevReservation_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, false);
    Player.Commands playerCommands =
        new Player.Commands.Builder().addAll(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build();

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withoutPrevNextCommandsNoReservations_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, false);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, false);
    Player.Commands playerCommands = Player.Commands.EMPTY;

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(
                    CommandButton.SLOT_BACK,
                    CommandButton.SLOT_FORWARD,
                    CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withoutPrevNextCommandsAndPrevReservation_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, false);
    Player.Commands playerCommands = Player.Commands.EMPTY;

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withoutPrevNextCommandsAndNextReservation_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, false);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true);
    Player.Commands playerCommands = Player.Commands.EMPTY;

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void
      getMediaButtonPreferencesFromCustomLayout_withoutPrevNextCommandsAndPrevNextReservations_returnsCorrectSlots() {
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    Bundle reservationBundle = new Bundle();
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true);
    reservationBundle.putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true);
    Player.Commands playerCommands = Player.Commands.EMPTY;

    ImmutableList<CommandButton> mediaButtonPreferences =
        CommandButton.getMediaButtonPreferencesFromCustomLayout(
            customLayout, playerCommands, reservationBundle);

    assertThat(mediaButtonPreferences)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ARTIST)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build())
        .inOrder();
  }

  @Test
  public void displayConstraintsResolve_withMaxButtonsPerSlot_limitsToDefinedMaximum() {
    // Define preferences that match, exceed or are below the allowed number of buttons per slot.
    // Also provide fallback slots to check they are used if the first preference is not available.
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_BACK_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_BACK_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command5", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command6", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build());
    // Including edge cases of 0 and max integer number of slots.
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder()
            .setMaxButtonsForSlot(CommandButton.SLOT_CENTRAL, /* maxButtons= */ 2)
            .setMaxButtonsForSlot(CommandButton.SLOT_FORWARD, /* maxButtons= */ 0)
            .setMaxButtonsForSlot(CommandButton.SLOT_BACK, /* maxButtons= */ 1)
            .setMaxButtonsForSlot(CommandButton.SLOT_BACK_SECONDARY, /* maxButtons= */ 2)
            .setMaxButtonsForSlot(CommandButton.SLOT_OVERFLOW, /* maxButtons= */ Integer.MAX_VALUE)
            .build();
    Player player = createFixedStatePlayer();

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command4", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("command5", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build())
        .inOrder();
  }

  @Test
  public void
      displayConstraintsResolve_withAllowedSessionCommandsPerSlot_limitsToAllowedCommands() {
    // Define preferences and constraints with no, single or multiple matches.
    // Also provide fallback slots to check they are used if the first preference is not available.
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(
                    new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(
                    new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(
                    new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build());
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder()
            .setAllowedSessionCommandsForSlot(
                CommandButton.SLOT_CENTRAL,
                new SessionCommands.Builder()
                    .add(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                    .build())
            .setAllowedSessionCommandsForSlot(
                CommandButton.SLOT_BACK,
                new SessionCommands.Builder()
                    .add(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
                    .add(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
                    .build())
            .setAllowedSessionCommandsForSlot(
                CommandButton.SLOT_FORWARD,
                new SessionCommands.Builder()
                    .add(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                    .build())
            .setAllowedSessionCommandsForSlot(CommandButton.SLOT_OVERFLOW, SessionCommands.EMPTY)
            .build();
    Player player = createFixedStatePlayer();

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(
                    new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(
                    new SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build())
        .inOrder();
  }

  @Test
  public void displayConstraintsResolve_withAllowCustomCommandsPerSlot_limitsToAllowedCommands() {
    // Define some custom commands, but also a non-custom one to check it's used if custom commands
    // are not allowed.
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("custom1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("custom2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("custom3", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_STOP)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build());
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder()
            // Leave out SLOT_FORWARD to test default value of "true"
            .setAllowCustomCommandsForSlot(CommandButton.SLOT_BACK, /* allowCustomCommands= */ true)
            .setAllowCustomCommandsForSlot(
                CommandButton.SLOT_CENTRAL, /* allowCustomCommands= */ false)
            .setAllowCustomCommandsForSlot(
                CommandButton.SLOT_OVERFLOW, /* allowCustomCommands= */ false)
            .build();
    Player player = createFixedStatePlayer();

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("custom1", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setSessionCommand(new SessionCommand("custom2", Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_STOP)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build())
        .inOrder();
  }

  @Test
  public void
      displayConstraintsResolve_withAllowedPlayerCommandsPerSlot_limitsToAllowedCustomCommands() {
    // Define preferences and constraints with no, single or multiple matches.
    // Also provide fallback slots to check they are used if the first preference is not available.
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_CENTRAL, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_STOP)
                .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setSlots(CommandButton.SLOT_OVERFLOW, CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_GET_TRACKS)
                .setSlots(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build());
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder()
            .setAllowedPlayerCommandsForSlot(
                CommandButton.SLOT_CENTRAL,
                new Player.Commands.Builder().add(Player.COMMAND_CHANGE_MEDIA_ITEMS).build())
            .setAllowedPlayerCommandsForSlot(
                CommandButton.SLOT_BACK,
                new Player.Commands.Builder()
                    .addAll(Player.COMMAND_PREPARE, Player.COMMAND_GET_TRACKS)
                    .build())
            .setAllowedPlayerCommandsForSlot(
                CommandButton.SLOT_FORWARD,
                new Player.Commands.Builder().add(Player.COMMAND_STOP).build())
            .setAllowedPlayerCommandsForSlot(CommandButton.SLOT_OVERFLOW, Player.Commands.EMPTY)
            .build();
    Player player = createFixedStatePlayer();

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_PREPARE)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_STOP)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build())
        .inOrder();
  }

  @Test
  public void displayConstraintsResolve_defaultConstraintsNoPreferences_createsDefaultButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences = ImmutableList.of();
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder().build();
    // Allow multiple forward/back operations to check the preferred one is used.
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ new Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD)
                .build(),
            /* playWhenReady= */ true,
            /* playbackState= */ Player.STATE_READY);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build())
        .inOrder();
  }

  @Test
  public void
      displayConstraintsResolve_defaultConstraintsNoPreferencesWithoutAvailableCommands_createsDisabledDefaultButtons() {
    ImmutableList<CommandButton> mediaButtonPreferences = ImmutableList.of();
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder().build();
    // Add a single available command to test the combination of available/unavailable seek actions.
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            /* playWhenReady= */ false,
            /* playbackState= */ Player.STATE_READY);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_PLAY)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .setEnabled(false)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_BACK)
                .setEnabled(false)
                .build())
        .inOrder();
  }

  @Test
  public void displayConstraintsResolve_noSpaceForDefaultButtons_createsNoDefaultButtons() {
    // Block slots by a mix of button preferences and setting max buttons to zero.
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_SET_SPEED_AND_PITCH)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build());
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder()
            .setMaxButtonsForSlot(CommandButton.SLOT_CENTRAL, /* maxButtons= */ 0)
            .setMaxButtonsForSlot(CommandButton.SLOT_BACK, /* maxButtons= */ 0)
            .build();
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ new Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            /* playWhenReady= */ true,
            /* playbackState= */ Player.STATE_READY);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_ALBUM)
                .setPlayerCommand(Player.COMMAND_SET_SPEED_AND_PITCH)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build());
  }

  @Test
  public void
      displayConstraintsResolve_onlySpaceForDefaultForwardButton_createsDefaultForwardButton() {
    ImmutableList<CommandButton> mediaButtonPreferences = ImmutableList.of();
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder()
            .setMaxButtonsForSlot(CommandButton.SLOT_CENTRAL, /* maxButtons= */ 0)
            .setMaxButtonsForSlot(CommandButton.SLOT_BACK, /* maxButtons= */ 0)
            .build();
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ new Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            /* playWhenReady= */ true,
            /* playbackState= */ Player.STATE_READY);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build());
  }

  @Test
  public void displayConstraintsResolve_onlySpaceForDefaultBackButton_createsDefaultBackButton() {
    ImmutableList<CommandButton> mediaButtonPreferences = ImmutableList.of();
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder()
            .setMaxButtonsForSlot(CommandButton.SLOT_CENTRAL, /* maxButtons= */ 0)
            .setMaxButtonsForSlot(CommandButton.SLOT_FORWARD, /* maxButtons= */ 0)
            .build();
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ Player.Commands.EMPTY,
            /* playWhenReady= */ true,
            /* playbackState= */ Player.STATE_READY);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_BACK)
                .setEnabled(false)
                .build());
  }

  @Test
  public void
      displayConstraintsResolve_defaultConstraintsNoPreferencesWithBackForwardCommands_createsDefaultButtonsWithMatchingIncrement() {
    ImmutableList<CommandButton> mediaButtonPreferences = ImmutableList.of();
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder().build();
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ new Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD)
                .build(),
            /* playWhenReady= */ true,
            /* playbackState= */ Player.STATE_READY,
            /* seekBackIncrementMs= */ 5500,
            /* seekForwardIncrementMs= */ 14000);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SKIP_BACK_5)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build())
        .inOrder();
  }

  @Test
  public void
      displayConstraintsResolve_withForwardButtonPreference_createsMatchingDefaultBackButton() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build());
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder().build();
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ new Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build(),
            /* playWhenReady= */ true,
            /* playbackState= */ Player.STATE_READY);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_REWIND)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .setSlots(CommandButton.SLOT_BACK)
                .build())
        .inOrder();
  }

  @Test
  public void
      displayConstraintsResolve_withBackButtonPreference_createsMatchingDefaultForwardButton() {
    ImmutableList<CommandButton> mediaButtonPreferences =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_BACK)
                .build());
    CommandButton.DisplayConstraints displayConstraints =
        new CommandButton.DisplayConstraints.Builder().build();
    Player player =
        createFixedStatePlayer(
            /* availableCommands= */ new Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build(),
            /* playWhenReady= */ true,
            /* playbackState= */ Player.STATE_READY);

    ImmutableList<CommandButton> resolvedButtons =
        displayConstraints.resolve(mediaButtonPreferences, player);

    assertThat(resolvedButtons)
        .containsExactly(
            new CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            new CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build())
        .inOrder();
  }

  private static Player createFixedStatePlayer() {
    return createFixedStatePlayer(
        /* availableCommands= */ Player.Commands.EMPTY,
        /* playWhenReady= */ false,
        /* playbackState= */ Player.STATE_IDLE);
  }

  private static Player createFixedStatePlayer(
      Player.Commands availableCommands, boolean playWhenReady, @Player.State int playbackState) {
    return createFixedStatePlayer(
        availableCommands,
        playWhenReady,
        playbackState,
        /* seekBackIncrementMs= */ 5500,
        /* seekForwardIncrementMs= */ 14000);
  }

  private static Player createFixedStatePlayer(
      Player.Commands availableCommands,
      boolean playWhenReady,
      @Player.State int playbackState,
      long seekBackIncrementMs,
      long seekForwardIncrementMs) {
    return new SimpleBasePlayer(Looper.myLooper()) {
      @Override
      protected State getState() {
        return new State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlaylist(ImmutableList.of(new MediaItemData.Builder("uid").build()))
            .setSeekBackIncrementMs(seekBackIncrementMs)
            .setSeekForwardIncrementMs(seekForwardIncrementMs)
            .build();
      }
    };
  }
}
