/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.testapp.controller

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TimelineAdapter(
  private val activity: Activity,
  private val mediaController: MediaController
) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {
  private var timeline: Timeline = mediaController.currentTimeline
  private var currentIndex: Int = -1

  init {
    val timelineList: RecyclerView = activity.findViewById(R.id.timeline_items_list)
    timelineList.layoutManager = LinearLayoutManager(activity)
    timelineList.setHasFixedSize(true)
    timelineList.adapter = this

    val refreshButton: Button = activity.findViewById(R.id.refresh_button)
    refreshButton.setOnClickListener {
      refreshTimeline(mediaController.currentTimeline, mediaController.currentMediaItemIndex)
    }

    val listener =
      object : Player.Listener {
        override fun onTimelineChanged(newTimeline: Timeline, reason: Int) {
          timeline = newTimeline
          notifyDataSetChanged()
        }

        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int
        ) {
          currentIndex = newPosition.mediaItemIndex
          notifyItemChanged(oldPosition.mediaItemIndex)
          notifyItemChanged(newPosition.mediaItemIndex)
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
          notifyDataSetChanged()
        }
      }

    mediaController.addListener(listener)
  }

  fun refreshTimeline(newTimeline: Timeline, index: Int) {
    timeline = newTimeline
    currentIndex = index
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
    ViewHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.media_timeline_item, parent, false)
    )

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val window = timeline.getWindow(position, Timeline.Window())
    val mediaMetadata = window.mediaItem.mediaMetadata
    holder.name.text = mediaMetadata.title ?: "Title metadata empty"
    holder.subtitle.text = mediaMetadata.subtitle ?: "Subtitle metadata empty"

    when {
      mediaMetadata.artworkUri != null -> {
        holder.icon.setImageURI(mediaMetadata.artworkUri)
      }
      mediaMetadata.artworkData != null -> {
        val bitmap: Bitmap =
          BitmapFactory.decodeByteArray(
            mediaMetadata.artworkData,
            0,
            mediaMetadata.artworkData!!.size
          )
        holder.icon.setImageBitmap(bitmap)
      }
      else -> {
        holder.icon.setImageResource(R.drawable.ic_album_black_24dp)
      }
    }

    holder.itemView.setOnClickListener { mediaController.seekToDefaultPosition(position) }
    holder.removeButton.apply {
      if (mediaController.availableCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
        visibility = View.VISIBLE
        setOnClickListener { mediaController.removeMediaItem(position) }
      } else {
        visibility = View.GONE
        setOnClickListener {}
      }
    }

    val colorId =
      if (position == currentIndex) {
        R.color.background_grey
      } else {
        R.color.background_transparent
      }
    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(activity.resources, colorId, null))
  }

  override fun getItemCount(): Int = timeline.windowCount

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.item_name)
    val subtitle: TextView = itemView.findViewById(R.id.item_subtitle)
    val icon: ImageView = itemView.findViewById(R.id.item_icon)
    val removeButton: Button = itemView.findViewById(R.id.remove_button)
  }
}
