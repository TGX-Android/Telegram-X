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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import java.util.Stack

/** Helper class that enables navigation on tree in MediaBrowser. */
class BrowseMediaItemsAdapter(
  private val activity: Activity,
  private val mediaBrowser: MediaBrowser
) : RecyclerView.Adapter<BrowseMediaItemsAdapter.ViewHolder>() {
  private var items: List<MediaItem> = emptyList()
  // Stack that holds ancestors of current item.
  private val nodes = Stack<String>()

  init {
    val browseTreeList: RecyclerView = activity.findViewById(R.id.media_items_list)
    browseTreeList.layoutManager = LinearLayoutManager(activity)
    browseTreeList.setHasFixedSize(true)
    browseTreeList.adapter = this

    val topButtonView: View = activity.findViewById(R.id.media_browse_tree_top)
    topButtonView.setOnClickListener {
      if (!supportsSubscribe() || !supportsUnsubscribe()) {
        Toast.makeText(activity, R.string.command_not_supported_msg, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      if (nodes.size > 1) {
        unsubscribe()
        while (nodes.size > 1) nodes.pop()
        subscribe()
      }
    }

    val upButtonView: View = activity.findViewById(R.id.media_browse_tree_up)
    upButtonView.setOnClickListener {
      if (!supportsSubscribe() || !supportsUnsubscribe()) {
        Toast.makeText(activity, R.string.command_not_supported_msg, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      if (nodes.size > 1) {
        unsubscribe()
        nodes.pop()
        subscribe()
      }
    }

    if (
      mediaBrowser.isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
    ) {
      val libraryResult: ListenableFuture<LibraryResult<MediaItem>> =
        mediaBrowser.getLibraryRoot(null)
      libraryResult.addListener(
        {
          val result: LibraryResult<MediaItem> = libraryResult.get()
          result.value?.let { setRoot(it.mediaId) }
        },
        ContextCompat.getMainExecutor(activity)
      )
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
    ViewHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.media_browse_item, parent, false)
    )

  @SuppressWarnings("FutureReturnValueIgnored")
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    if (items.isEmpty()) {
      if (!supportsSubscribe() || !supportsUnsubscribe()) {
        setMessageForEmptyList(holder, activity.getString(R.string.command_not_supported_msg))
      } else {
        setMessageForEmptyList(holder, activity.getString(R.string.media_browse_tree_empty))
      }
      return
    }

    val mediaMetadata: MediaMetadata = items[position].mediaMetadata
    holder.name.text = mediaMetadata.title ?: "Title metadata empty"
    holder.subtitle.text = mediaMetadata.subtitle ?: "Subtitle metadata empty"
    holder.subtitle.visibility = View.VISIBLE
    holder.icon.visibility = View.VISIBLE

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

    val item: MediaItem = items[position]
    holder.itemView.setOnClickListener {
      if (mediaMetadata.isBrowsable == true) {
        unsubscribe()
        nodes.push(item.mediaId)
        subscribe()
      }
      if (mediaMetadata.isPlayable == true) {
        mediaBrowser.setMediaItem(MediaItem.Builder().setMediaId(item.mediaId).build())
        mediaBrowser.prepare()
        mediaBrowser.play()
      }
    }
  }

  override fun getItemCount(): Int {
    // Leave one item for message if nodes or items are empty.
    if (nodes.size == 0 || items.isEmpty()) return 1
    return items.size
  }

  private fun supportsSubscribe(): Boolean =
    mediaBrowser.isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)

  private fun supportsUnsubscribe(): Boolean =
    mediaBrowser.isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)

  private fun setMessageForEmptyList(holder: ViewHolder, message: String) {
    holder.name.text = message
    holder.subtitle.visibility = View.GONE
    holder.icon.visibility = View.GONE
    holder.itemView.setOnClickListener {}
  }

  fun updateItems(newItems: List<MediaItem>) {
    items = newItems
    notifyDataSetChanged()
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private fun subscribe() {
    if (nodes.isNotEmpty() && supportsSubscribe()) {
      mediaBrowser.subscribe(nodes.peek(), null)
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private fun unsubscribe() {
    if (nodes.isNotEmpty() && supportsUnsubscribe()) {
      mediaBrowser.unsubscribe(nodes.peek())
    }
    updateItems(emptyList())
  }

  private fun setRoot(root: String) {
    unsubscribe()
    nodes.clear()
    nodes.push(root)
    subscribe()
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.item_name)
    val subtitle: TextView = itemView.findViewById(R.id.item_subtitle)
    val icon: ImageView = itemView.findViewById(R.id.item_icon)
  }
}
