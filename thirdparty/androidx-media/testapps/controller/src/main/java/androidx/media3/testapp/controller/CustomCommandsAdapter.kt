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
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Helper class that displays and handles custom commands. */
class CustomCommandsAdapter(
  activity: Activity,
  private val mediaController: MediaController,
  packageName: String,
) : RecyclerView.Adapter<CustomCommandsAdapter.ViewHolder>() {
  private var commands: List<CommandButton> = emptyList()
  private val resources: Resources = activity.packageManager.getResourcesForApplication(packageName)

  init {
    val customCommandsList: RecyclerView = activity.findViewById(R.id.custom_commands_list)
    customCommandsList.layoutManager = LinearLayoutManager(activity)
    customCommandsList.setHasFixedSize(true)
    customCommandsList.adapter = this
    setCommands(mediaController.customLayout)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
    ViewHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.media_custom_command, parent, false)
    )

  @SuppressWarnings("FutureReturnValueIgnored")
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val commandButton: CommandButton = commands[position]
    holder.name.text = commandButton.displayName
    holder.description.text = commandButton.sessionCommand?.customAction
    if (commandButton.iconResId != 0) {
      val iconDrawable: Drawable? =
        ResourcesCompat.getDrawable(resources, commandButton.iconResId, null)
      holder.icon.setImageDrawable(iconDrawable)
    }
    holder.itemView.setOnClickListener {
      commandButton.sessionCommand?.let { mediaController.sendCustomCommand(it, Bundle.EMPTY) }
    }
  }

  override fun getItemCount(): Int = commands.size

  fun setCommands(newCommands: List<CommandButton>) {
    val diffResult: DiffUtil.DiffResult =
      DiffUtil.calculateDiff(
        object : DiffUtil.Callback() {
          override fun getOldListSize(): Int = commands.size

          override fun getNewListSize(): Int = newCommands.size

          override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            commands.size == newCommands.size &&
              commands[oldItemPosition] == newCommands[newItemPosition]

          override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            commands[oldItemPosition] == newCommands[newItemPosition]
        }
      )
    commands = newCommands
    diffResult.dispatchUpdatesTo(this)
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.action_name)
    val description: TextView = itemView.findViewById(R.id.action_description)
    val icon: ImageView = itemView.findViewById(R.id.action_icon)
  }
}
