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
package androidx.media3.demo.shortform.viewpager

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.demo.shortform.MediaItemDatabase
import androidx.media3.demo.shortform.PlayerPool
import androidx.media3.demo.shortform.R
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

@OptIn(UnstableApi::class)
class ViewPagerMediaAdapter(
  private val mediaItemDatabase: MediaItemDatabase,
  numberOfPlayers: Int,
  context: Context,
) : RecyclerView.Adapter<ViewPagerMediaHolder>() {
  private val preloadManager: DefaultPreloadManager
  private val currentMediaItemsAndIndexes: ArrayDeque<Pair<MediaItem, Int>> = ArrayDeque()
  private var playerPool: PlayerPool
  private val holderMap: MutableMap<Int, ViewPagerMediaHolder>
  private val preloadControl: DefaultPreloadControl

  companion object {
    private const val TAG = "ViewPagerMediaAdapter"
    private const val LOAD_CONTROL_MIN_BUFFER_MS = 5_000
    private const val LOAD_CONTROL_MAX_BUFFER_MS = 20_000
    private const val LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS = 500
    private const val MANAGED_ITEM_COUNT = 10
    private const val ITEM_ADD_REMOVE_COUNT = 4
  }

  init {
    val loadControl =
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(
          LOAD_CONTROL_MIN_BUFFER_MS,
          LOAD_CONTROL_MAX_BUFFER_MS,
          LOAD_CONTROL_BUFFER_FOR_PLAYBACK_MS,
          DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    preloadControl = DefaultPreloadControl()
    val preloadManagerBuilder =
      DefaultPreloadManager.Builder(context.applicationContext, preloadControl)
        .setLoadControl(loadControl)
    playerPool = PlayerPool(numberOfPlayers, preloadManagerBuilder)
    holderMap = mutableMapOf()
    preloadManager = preloadManagerBuilder.build()
    for (i in 0 until MANAGED_ITEM_COUNT) {
      addMediaItem(index = i, isAddingToRightEnd = true)
    }
    preloadManager.invalidate()
  }

  override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
    playerPool.destroyPlayers()
    preloadManager.release()
    holderMap.clear()
    super.onDetachedFromRecyclerView(recyclerView)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewPagerMediaHolder {
    val view =
      LayoutInflater.from(parent.context).inflate(R.layout.media_item_view_pager, parent, false)
    val holder = ViewPagerMediaHolder(view, playerPool)
    view.addOnAttachStateChangeListener(holder)
    return holder
  }

  override fun onBindViewHolder(holder: ViewPagerMediaHolder, position: Int) {
    val mediaItem = mediaItemDatabase.get(position)
    Log.d(TAG, "onBindViewHolder: Getting item at position $position")
    var currentMediaSource = preloadManager.getMediaSource(mediaItem)
    if (currentMediaSource == null) {
      preloadManager.add(mediaItem, position)
      currentMediaSource = preloadManager.getMediaSource(mediaItem)!!
    }
    holder.bindData(currentMediaSource)
  }

  override fun onViewAttachedToWindow(holder: ViewPagerMediaHolder) {
    val holderBindingAdapterPosition = holder.bindingAdapterPosition
    holderMap[holderBindingAdapterPosition] = holder

    if (!currentMediaItemsAndIndexes.isEmpty()) {
      val leftMostIndex = currentMediaItemsAndIndexes.first().second
      val rightMostIndex = currentMediaItemsAndIndexes.last().second

      if (rightMostIndex - holderBindingAdapterPosition <= 2) {
        Log.d(TAG, "onViewAttachedToWindow: Approaching to the rightmost item")
        for (i in 1 until ITEM_ADD_REMOVE_COUNT + 1) {
          addMediaItem(index = rightMostIndex + i, isAddingToRightEnd = true)
          removeMediaItem(isRemovingFromRightEnd = false)
        }
      } else if (holderBindingAdapterPosition - leftMostIndex <= 2) {
        Log.d(TAG, "onViewAttachedToWindow: Approaching to the leftmost item")
        for (i in 1 until ITEM_ADD_REMOVE_COUNT + 1) {
          addMediaItem(index = leftMostIndex - i, isAddingToRightEnd = false)
          removeMediaItem(isRemovingFromRightEnd = true)
        }
      }
    }
  }

  override fun onViewDetachedFromWindow(holder: ViewPagerMediaHolder) {
    holderMap.remove(holder.bindingAdapterPosition)
  }

  override fun getItemCount(): Int {
    // Effectively infinite scroll
    return Int.MAX_VALUE
  }

  fun onPageSelected(position: Int) {
    holderMap[position]?.playIfPossible()
    preloadControl.currentPlayingIndex = position
    preloadManager.setCurrentPlayingIndex(position)
    preloadManager.invalidate()
  }

  private fun addMediaItem(index: Int, isAddingToRightEnd: Boolean) {
    if (index < 0) {
      return
    }
    Log.d(TAG, "addMediaItem: Adding item at index $index")
    val mediaItem = mediaItemDatabase.get(index)
    preloadManager.add(mediaItem, index)
    if (isAddingToRightEnd) {
      currentMediaItemsAndIndexes.addLast(Pair(mediaItem, index))
    } else {
      currentMediaItemsAndIndexes.addFirst(Pair(mediaItem, index))
    }
  }

  private fun removeMediaItem(isRemovingFromRightEnd: Boolean) {
    if (currentMediaItemsAndIndexes.size <= MANAGED_ITEM_COUNT) {
      return
    }
    val itemAndIndex =
      if (isRemovingFromRightEnd) {
        currentMediaItemsAndIndexes.removeLast()
      } else {
        currentMediaItemsAndIndexes.removeFirst()
      }
    Log.d(TAG, "removeMediaItem: Removing item at index ${itemAndIndex.second}")
    preloadManager.remove(itemAndIndex.first)
  }

  inner class DefaultPreloadControl(var currentPlayingIndex: Int = C.INDEX_UNSET) :
    TargetPreloadStatusControl<Int> {

    override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.Status? {
      if (abs(rankingData - currentPlayingIndex) == 2) {
        return DefaultPreloadManager.Status(STAGE_LOADED_FOR_DURATION_MS, 500L)
      } else if (abs(rankingData - currentPlayingIndex) == 1) {
        return DefaultPreloadManager.Status(STAGE_LOADED_FOR_DURATION_MS, 1000L)
      }
      return null
    }
  }
}
