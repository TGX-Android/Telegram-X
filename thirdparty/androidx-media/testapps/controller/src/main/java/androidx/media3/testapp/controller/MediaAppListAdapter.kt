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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

/** A sectioned RecyclerView Adapter that displays list(s) of media apps. */
class MediaAppListAdapter(val mediaAppSelectedListener: MediaAppSelectedListener) :
  RecyclerView.Adapter<ViewHolder>() {

  /** Click listener for when an app is selected. */
  interface MediaAppSelectedListener {
    fun onMediaAppClicked(mediaAppDetails: MediaAppDetails)
  }

  /** The types of views that this recycler view adapter displays. */
  enum class ViewType(val layoutId: Int) {
    /**
     * A media app entry, with icon, app name, and package name. Tapping on one of these entries
     * will fire the MediaAppSelectedListener callback.
     */
    AppView(R.layout.media_app_item) {
      override fun create(itemLayout: View): ViewHolder = AppEntry.ViewHolder(itemLayout)
    },
    /** A section header, only displayed if the adapter has multiple sections. */
    HeaderView(R.layout.media_app_list_header) {
      override fun create(itemLayout: View): ViewHolder = Header.ViewHolder(itemLayout)
    },
    /** An error, such as "no apps", or "missing permission". Can optionally provide an action. */
    ErrorView(R.layout.media_app_list_error) {
      override fun create(itemLayout: View): ViewHolder = Error.ViewHolder(itemLayout)
    };

    abstract fun create(itemLayout: View): ViewHolder
  }

  /** An interface for items in the recycler view. */
  interface RecyclerViewItem {
    fun viewType(): ViewType

    fun bindTo(holder: ViewHolder)
  }

  /** An implementation of [RecyclerViewItem] for media apps. */
  class AppEntry(
    private val appDetails: MediaAppDetails,
    private val appSelectedListener: MediaAppSelectedListener,
  ) : RecyclerViewItem {
    override fun viewType(): ViewType = ViewType.AppView

    override fun bindTo(holder: RecyclerView.ViewHolder) {
      if (holder is ViewHolder) {
        holder.appIconView?.setImageBitmap(appDetails.icon)
        holder.appIconView?.contentDescription =
          holder.appIconView?.context?.getString(R.string.app_icon_desc, appDetails.appName)
        holder.appNameView?.text = appDetails.appName
        holder.appPackageView?.text = appDetails.packageName

        holder.controlButton?.setOnClickListener {
          appSelectedListener.onMediaAppClicked(appDetails)
        }
      }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val appIconView: ImageView? = itemView.findViewById(R.id.app_icon)
      val appNameView: TextView? = itemView.findViewById(R.id.app_name)
      val appPackageView: TextView? = itemView.findViewById(R.id.package_name)
      val controlButton: Button? = itemView.findViewById(R.id.app_control)
    }
  }

  /** An implementation of [RecyclerViewItem] for headers. */
  class Header(@StringRes private val labelResId: Int) : RecyclerViewItem {
    override fun viewType(): ViewType = ViewType.HeaderView

    override fun bindTo(holder: RecyclerView.ViewHolder) {
      if (holder is ViewHolder) {
        holder.headerView?.setText(labelResId)
      }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val headerView: TextView? = itemView.findViewById(R.id.header_text)
    }
  }

  /** An implementation of [RecyclerViewItem] for error states, with an optional action. */
  class Error(
    @StringRes private val errorMsgId: Int,
    @StringRes private val errorDetailId: Int,
    @StringRes private val errorButtonId: Int,
    private val clickListener: View.OnClickListener?,
  ) : RecyclerViewItem {
    override fun viewType(): ViewType = ViewType.ErrorView

    override fun bindTo(holder: RecyclerView.ViewHolder) {
      if (holder is ViewHolder) {
        holder.errorMessage?.setText(errorMsgId)
        holder.errorDetail?.setText(errorDetailId)
        holder.errorMessage?.visibility = if (errorDetailId == 0) View.GONE else View.VISIBLE
        holder.actionButton?.setOnClickListener(clickListener)
        if (errorButtonId == 0 || clickListener == null) {
          holder.actionButton?.visibility = View.GONE
        } else {
          holder.actionButton?.visibility = View.VISIBLE
          holder.actionButton?.setText(errorButtonId)
        }
      }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val errorMessage: TextView? = itemView.findViewById(R.id.error_message)
      val errorDetail: TextView? = itemView.findViewById(R.id.error_detail)
      val actionButton: Button? = itemView.findViewById(R.id.error_action)
    }
  }

  /** Represents a section of items in the recycler view. */
  inner class Section(@StringRes internal val label: Int) {
    internal val items = mutableListOf<RecyclerViewItem>()

    val size: Int
      get() = items.size

    fun setError(@StringRes message: Int, @StringRes detail: Int) =
      setError(message, detail, 0, null)

    fun setError(
      @StringRes message: Int,
      @StringRes detail: Int,
      @StringRes buttonText: Int,
      onClickListener: View.OnClickListener?,
    ) {
      items.clear()
      items.add(Error(message, detail, buttonText, onClickListener))
      updateData()
    }

    fun setAppsList(appEntries: List<MediaAppDetails?>) {
      items.clear()
      for (appEntry in appEntries) {
        if (appEntry != null) {
          items.add(AppEntry(appEntry, mediaAppSelectedListener))
        }
      }
      updateData()
    }
  }

  private val sections = ArrayList<Section>()
  private val recyclerViewEntries = ArrayList<RecyclerViewItem>()

  fun addSection(@StringRes label: Int): Section {
    val section = Section(label)
    sections.add(section)
    return section
  }

  fun updateData() {
    val oldEntries = ArrayList<RecyclerViewItem>(recyclerViewEntries)
    recyclerViewEntries.clear()
    for (section in sections) {
      if (section.size > 0) {
        recyclerViewEntries.add(Header(section.label))
      }
      recyclerViewEntries.addAll(section.items)
    }

    val diffResult: DiffUtil.DiffResult =
      DiffUtil.calculateDiff(
        object : DiffUtil.Callback() {
          override fun getOldListSize(): Int = oldEntries.size

          override fun getNewListSize(): Int = recyclerViewEntries.size

          override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldEntries[oldItemPosition] == recyclerViewEntries[newItemPosition]

          override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            areItemsTheSame(oldItemPosition, newItemPosition)
        }
      )
    diffResult.dispatchUpdatesTo(this)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val type: ViewType = ViewType.values()[viewType]
    val itemLayout: View = LayoutInflater.from(parent.context).inflate(type.layoutId, parent, false)
    return type.create(itemLayout)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) =
    recyclerViewEntries[position].bindTo(holder)

  override fun getItemViewType(position: Int) = recyclerViewEntries[position].viewType().ordinal

  override fun getItemCount(): Int = recyclerViewEntries.size
}
