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
package androidx.media3.demo.shortform

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager.Builder
import androidx.media3.exoplayer.util.EventLogger
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.Maps
import java.util.Collections
import java.util.LinkedList
import java.util.Queue

@OptIn(UnstableApi::class)
class PlayerPool(private val numberOfPlayers: Int, preloadManagerBuilder: Builder) {

  /** Creates a player instance to be used by the pool. */
  interface PlayerFactory {
    /** Creates an [ExoPlayer] instance. */
    fun createPlayer(): ExoPlayer
  }

  private val availablePlayerQueue: Queue<Int> = LinkedList()
  private val playerMap: BiMap<Int, ExoPlayer> = Maps.synchronizedBiMap(HashBiMap.create())
  private val playerRequestTokenSet: MutableSet<Int> = Collections.synchronizedSet(HashSet<Int>())
  private val playerFactory: PlayerFactory = DefaultPlayerFactory(preloadManagerBuilder)

  fun acquirePlayer(token: Int, callback: (ExoPlayer) -> Unit) {
    synchronized(playerMap) {
      // Add token to set of views requesting players
      playerRequestTokenSet.add(token)
      acquirePlayerInternal(token, callback)
    }
  }

  private fun acquirePlayerInternal(token: Int, callback: (ExoPlayer) -> Unit) {
    synchronized(playerMap) {
      if (!availablePlayerQueue.isEmpty()) {
        val playerNumber = availablePlayerQueue.remove()
        playerMap[playerNumber]?.let { callback.invoke(it) }
        playerRequestTokenSet.remove(token)
        return
      } else if (playerMap.size < numberOfPlayers) {
        val player = playerFactory.createPlayer()
        playerMap[playerMap.size] = player
        callback.invoke(player)
        playerRequestTokenSet.remove(token)
        return
      } else if (playerRequestTokenSet.contains(token)) {
        Handler(Looper.getMainLooper()).postDelayed({ acquirePlayerInternal(token, callback) }, 500)
      }
    }
  }

  /** Calls [Player.play()] for the given player and pauses all other players. */
  fun play(player: Player) {
    pauseAllPlayers(player)
    player.play()
  }

  /**
   * Pauses all players.
   *
   * @param keepOngoingPlayer The optional player that should keep playing if not paused.
   */
  private fun pauseAllPlayers(keepOngoingPlayer: Player? = null) {
    for (player in playerMap.values) {
      if (player != keepOngoingPlayer) {
        player.pause()
      }
    }
  }

  fun releasePlayer(token: Int, player: ExoPlayer?) {
    synchronized(playerMap) {
      // Remove token from set of views requesting players & remove potential callbacks
      // trying to grab the player
      playerRequestTokenSet.remove(token)
      // Stop the player and release into the pool for reusing, do not player.release()
      player?.stop()
      player?.clearMediaItems()
      if (player != null) {
        val playerNumber = playerMap.inverse()[player]
        availablePlayerQueue.add(playerNumber)
      }
    }
  }

  fun destroyPlayers() {
    synchronized(playerMap) {
      for (i in 0 until playerMap.size) {
        playerMap[i]?.release()
        playerMap.remove(i)
      }
    }
  }

  @OptIn(UnstableApi::class)
  private class DefaultPlayerFactory(private val preloadManagerBuilder: Builder) : PlayerFactory {
    private var playerCounter = 0

    override fun createPlayer(): ExoPlayer {
      val player = preloadManagerBuilder.buildExoPlayer()
      player.addAnalyticsListener(EventLogger("player-$playerCounter"))
      playerCounter++
      player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
      return player
    }
  }
}
