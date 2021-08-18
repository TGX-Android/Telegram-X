package me.vkryl.task

import org.gradle.api.tasks.TaskAction

open class GeneratePhoneFormatTask : BaseTask() {
  @TaskAction
  fun generatePhoneFormat () {
    // TODO generate org.thunderdog.challegram.tool.TGPhoneFormat based on phoneformat_masks.txt
  }
}