/*
 * Copyright 2024 The Android Open Source Project
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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ConnectionState}. */
@RunWith(AndroidJUnit4.class)
public class ConnectionStateTest {

  @Test
  public void roundTripViaBundle_restoresEqualInstance() {
    Context context = ApplicationProvider.getApplicationContext();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession session = new MediaSession.Builder(context, player).build();
    Bundle tokenExtras = new Bundle();
    tokenExtras.putString("key", "token");
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key", "session");
    ConnectionState connectionState =
        new ConnectionState(
            MediaLibraryInfo.VERSION_INT,
            MediaSessionStub.VERSION_INT,
            new MediaSessionStub(session.getImpl()),
            /* sessionActivity= */ PendingIntent.getActivity(
                context,
                /* requestCode= */ 0,
                new Intent(),
                /* flags= */ Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0),
            /* customLayout= */ ImmutableList.of(
                new CommandButton.Builder(CommandButton.ICON_ARTIST)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                    .build()),
            /* mediaButtonPreferences= */ ImmutableList.of(
                new CommandButton.Builder(CommandButton.ICON_HEART_FILLED)
                    .setPlayerCommand(Player.COMMAND_PREPARE)
                    .build()),
            /* commandButtonsForMediaItems= */ ImmutableList.of(
                new CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                    .build()),
            new SessionCommands.Builder().add(new SessionCommand("action", Bundle.EMPTY)).build(),
            /* playerCommandsFromSession= */ new Player.Commands.Builder()
                .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
                .build(),
            /* playerCommandsFromPlayer= */ new Player.Commands.Builder()
                .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build(),
            tokenExtras,
            sessionExtras,
            PlayerInfo.DEFAULT.copyWithIsPlaying(true),
            session.getPlatformToken());

    ConnectionState restoredConnectionState =
        ConnectionState.fromBundle(
            connectionState.toBundleForRemoteProcess(MediaControllerStub.VERSION_INT));
    session.release();
    player.release();

    assertThat(restoredConnectionState.libraryVersion).isEqualTo(connectionState.libraryVersion);
    assertThat(restoredConnectionState.sessionInterfaceVersion)
        .isEqualTo(connectionState.sessionInterfaceVersion);
    assertThat(restoredConnectionState.sessionActivity).isEqualTo(connectionState.sessionActivity);
    assertThat(restoredConnectionState.sessionBinder).isEqualTo(connectionState.sessionBinder);
    assertThat(restoredConnectionState.customLayout).isEqualTo(connectionState.customLayout);
    assertThat(restoredConnectionState.mediaButtonPreferences)
        .isEqualTo(connectionState.mediaButtonPreferences);
    assertThat(restoredConnectionState.commandButtonsForMediaItems)
        .isEqualTo(connectionState.commandButtonsForMediaItems);
    assertThat(restoredConnectionState.sessionCommands).isEqualTo(connectionState.sessionCommands);
    assertThat(restoredConnectionState.playerCommandsFromSession)
        .isEqualTo(connectionState.playerCommandsFromSession);
    assertThat(restoredConnectionState.playerCommandsFromPlayer)
        .isEqualTo(connectionState.playerCommandsFromPlayer);
    assertThat(restoredConnectionState.tokenExtras.getString("key")).isEqualTo("token");
    assertThat(restoredConnectionState.sessionExtras.getString("key")).isEqualTo("session");
    assertThat(restoredConnectionState.playerInfo.isPlaying).isTrue();
    assertThat(restoredConnectionState.platformToken).isEqualTo(connectionState.platformToken);
  }

  @Test
  public void
      roundTripViaBundle_controllerVersion6OrLower_usesMediaButtonPreferencesAsCustomLayout() {
    Context context = ApplicationProvider.getApplicationContext();
    Player player = new TestExoPlayerBuilder(context).build();
    MediaSession session = new MediaSession.Builder(context, player).build();
    ConnectionState connectionState =
        new ConnectionState(
            MediaLibraryInfo.VERSION_INT,
            MediaSessionStub.VERSION_INT,
            new MediaSessionStub(session.getImpl()),
            /* sessionActivity= */ null,
            /* customLayout= */ ImmutableList.of(),
            /* mediaButtonPreferences= */ ImmutableList.of(
                new CommandButton.Builder(CommandButton.ICON_HEART_FILLED)
                    .setSessionCommand(new SessionCommand("action", Bundle.EMPTY))
                    .build()),
            /* commandButtonsForMediaItems= */ ImmutableList.of(),
            SessionCommands.EMPTY,
            /* playerCommandsFromSession= */ Player.Commands.EMPTY,
            /* playerCommandsFromPlayer= */ Player.Commands.EMPTY,
            /* tokenExtras= */ Bundle.EMPTY,
            /* sessionExtras= */ Bundle.EMPTY,
            PlayerInfo.DEFAULT,
            session.getPlatformToken());

    ConnectionState restoredConnectionState =
        ConnectionState.fromBundle(
            connectionState.toBundleForRemoteProcess(/* controllerInterfaceVersion= */ 6));
    session.release();
    player.release();

    assertThat(restoredConnectionState.customLayout)
        .isEqualTo(connectionState.mediaButtonPreferences);
    assertThat(restoredConnectionState.mediaButtonPreferences).isEmpty();
  }
}
