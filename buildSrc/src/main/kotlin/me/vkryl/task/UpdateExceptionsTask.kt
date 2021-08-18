package me.vkryl.task

import org.gradle.api.tasks.TaskAction
import getOrThrow
import loadProperties

open class UpdateExceptionsTask : BaseTask() {
  @TaskAction
  fun updateExceptions () {
    val appVersion = loadProperties("version.properties").getOrThrow("version.app").toInt()
    if (appVersion == 0)
      error("appVersion == 0")
    val regex = Regex("(?<=(ClientException|DatabaseException|TdlibLaunchException))_?[0-9]*")
    editFile("app/src/main/java/org/drinkmore/Tracer.java") { line ->
      line.replace(regex, "_${appVersion}")
    }
    /*editFile("tdlib/src/main/java/org/drinkless/td/libcore/telegram/Client.java") { line ->
      line.replace(regex, "_${AppVersion.TDLIB}")
    }*/
  }
}