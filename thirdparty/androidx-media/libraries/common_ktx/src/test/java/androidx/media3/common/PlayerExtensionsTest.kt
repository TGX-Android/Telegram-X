/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.common

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Unit tests for Kotlin extension functions on the [Player]. */
@RunWith(AndroidJUnit4::class)
class PlayerExtensionsTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun playerListen_receivesVolumeEvent() = runTest {
    var volumeFromInsideOnEvents: Float? = null
    val player: ExoPlayer = TestExoPlayerBuilder(context).build()
    val listenJob = launch {
      player.listen { events ->
        if (Player.EVENT_VOLUME_CHANGED in events) {
          volumeFromInsideOnEvents = player.volume
        }
      }
    }
    // Wait for the Player.Listener to be registered inside player.listen
    testScheduler.runCurrent()

    // Set the volume to a non-default value to trigger an event
    player.volume = 0.5f

    // Let the volume change propagate
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(volumeFromInsideOnEvents).isEqualTo(0.5f)
    listenJob.cancelAndJoin()
  }

  @Test
  fun playerListen_withInternalCancel_cancelsCoroutineAndUnregistersListener() = runTest {
    val player = PlayerWithListeners(TestExoPlayerBuilder(context).build())
    val listenJob = launch {
      player.listen { events ->
        if (Player.EVENT_VOLUME_CHANGED in events) {
          throw CancellationException()
        }
      }
    }
    // Wait for the Player.Listener to be registered inside player.listen
    testScheduler.runCurrent()

    assertThat(player.listeners.size).isEqualTo(1)

    // Set the volume to a non-default value to trigger an event
    player.volume = 0.5f
    // Let the volume change propagate
    shadowOf(Looper.getMainLooper()).idle()
    // Let the CancellationException propagate and trigger listener removal
    testScheduler.runCurrent()

    assertThat(player.listeners.size).isEqualTo(0)
    assertThat(listenJob.isCancelled).isTrue()
    assertThat(listenJob.isCompleted).isTrue()
  }

  @Test
  fun playerListen_withExternalCancel_unregistersListener() = runTest {
    val player = PlayerWithListeners(TestExoPlayerBuilder(context).build())
    val listenJob = launch { player.listen { _ -> } }
    // Wait for the Player.Listener to be registered inside player.listen
    testScheduler.runCurrent()

    assertThat(player.listeners.size).isEqualTo(1)

    listenJob.cancelAndJoin()

    assertThat(player.listeners.size).isEqualTo(0)
    assertThat(listenJob.isCancelled).isTrue()
    assertThat(listenJob.isCompleted).isTrue()
  }

  @Test
  fun playerListen_onEventsThrowsException_bubblesOutAndUnregistersListener() = runTest {
    val player = PlayerWithListeners(TestExoPlayerBuilder(context).build())
    val exceptionFromListen = async {
      runCatching {
          player.listen { events ->
            if (Player.EVENT_VOLUME_CHANGED in events) {
              throw IllegalStateException("Volume event!")
            }
          }
        }
        .exceptionOrNull()
    }
    // Wait for the Player.Listener to be registered inside player.listen
    testScheduler.runCurrent()

    assertThat(player.listeners.size).isEqualTo(1)

    // Set the volume to a non-default value to trigger an event
    player.volume = 0.5f
    // Let the volume change propagate
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(exceptionFromListen.await()).hasMessageThat().isEqualTo("Volume event!")
    assertThat(player.listeners.size).isEqualTo(0)
  }

  @Test
  fun playerListen_calledFromDifferentThread_receivesVolumeEvent() = runTest {
    val applicationThread = HandlerThread("app-thread")
    applicationThread.start()
    val applicationHandler = Handler(applicationThread.looper)
    // Construct the player on application thread != test thread
    // This is where Player.Events will be delivered to
    val player =
      withContext(applicationHandler.asCoroutineDispatcher()) {
        TestExoPlayerBuilder(context).build()
      }
    val volumeFromInsideOnEventsJob = CompletableDeferred<Float>()
    val listenJob = launch {
      // Start listening from test thread != application thread
      // Player is accessed from a different thread to where it was created
      player.listen { events ->
        if (Player.EVENT_VOLUME_CHANGED in events) {
          // Complete a Job of getting the new volume out of a forever listening loop with success
          volumeFromInsideOnEventsJob.complete(player.volume)
        }
      }
    }
    // Wait for the Player.Listener to be registered inside player.listen
    testScheduler.runCurrent()

    // Set the volume to a non-default value to trigger an event
    // Use the application thread where the Player was constructed
    launch(applicationHandler.asCoroutineDispatcher()) { player.volume = 0.5f }

    assertThat(volumeFromInsideOnEventsJob.await()).isEqualTo(0.5f)
    listenJob.cancelAndJoin()
    applicationThread.quit()
  }

  private class PlayerWithListeners(player: Player) : ForwardingPlayer(player) {
    val listeners: MutableSet<Player.Listener> = HashSet()

    override fun addListener(listener: Player.Listener) {
      super.addListener(listener)
      listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
      super.removeListener(listener)
      listeners.remove(listener)
    }
  }
}
