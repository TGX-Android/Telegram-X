/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.demo.shortform.viewpager

import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.demo.shortform.PlayerPool
import androidx.media3.demo.shortform.R
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

@OptIn(UnstableApi::class)
class ViewPagerMediaHolder(itemView: View, private val playerPool: PlayerPool) :
  RecyclerView.ViewHolder(itemView), View.OnAttachStateChangeListener {
  private val playerView: PlayerView = itemView.findViewById(R.id.player_view)
  private var exoPlayer: ExoPlayer? = null
  private var isInView: Boolean = false
  private var pendingPlayRequestUponSetupPlayer: Boolean = false

  private lateinit var mediaSource: MediaSource

  companion object {
    private const val TAG = "ViewPagerMediaHolder"
  }

  init {
    // Define click listener for the ViewHolder's View
    playerView.findViewById<PlayerView>(R.id.player_view).setOnClickListener {
      if (it is PlayerView) {
        it.player?.run { playWhenReady = !playWhenReady }
      }
    }
  }

  private val player: ExoPlayer?
    get() {
      return exoPlayer
    }

  override fun onViewAttachedToWindow(view: View) {
    Log.d(TAG, "onViewAttachedToWindow: $bindingAdapterPosition")
    isInView = true
    if (player == null) {
      playerPool.acquirePlayer(bindingAdapterPosition, ::setupPlayer)
    }
  }

  override fun onViewDetachedFromWindow(view: View) {
    Log.d(TAG, "onViewDetachedFromWindow: $bindingAdapterPosition")
    isInView = false
    releasePlayer(exoPlayer)
  }

  fun bindData(mediaSource: MediaSource) {
    this.mediaSource = mediaSource
  }

  fun playIfPossible() {
    player?.let { playerPool.play(it) }
    if (player == null) {
      Log.d(TAG, "playIfPossible: The player hasn't been setup yet")
      pendingPlayRequestUponSetupPlayer = true
    }
  }

  private fun releasePlayer(player: ExoPlayer?) {
    playerPool.releasePlayer(bindingAdapterPosition, player ?: exoPlayer)
    this.exoPlayer = null
    playerView.player = null
  }

  private fun setupPlayer(player: ExoPlayer) {
    if (!isInView) {
      releasePlayer(player)
    } else {
      if (player != exoPlayer) {
        releasePlayer(exoPlayer)
      }

      player.run {
        repeatMode = ExoPlayer.REPEAT_MODE_ONE
        setMediaSource(mediaSource)
        seekTo(currentPosition)
        this@ViewPagerMediaHolder.exoPlayer = player
        player.prepare()
        playerView.player = player
        if (pendingPlayRequestUponSetupPlayer) {
          playerPool.play(player)
          pendingPlayRequestUponSetupPlayer = false
        }
      }
    }
  }
}
