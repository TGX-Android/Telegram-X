@file:JvmName("AppState")

package org.thunderdog.challegram.unsorted

import android.os.Process
import android.os.SystemClock
import androidx.tracing.trace
import org.drinkmore.Tracer
import org.thunderdog.challegram.BuildConfig
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.N
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.telegram.TdlibNotificationUtils
import org.thunderdog.challegram.util.Crash
import tgx.flavor.collectLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess


private var isInitialized: Boolean = false
private var startupTime = SystemClock.uptimeMillis()

fun uptime(): Long =
  startupTime.let {
    if (it != 0L) {
      SystemClock.uptimeMillis() - it
    } else {
      0L
    }
  }

fun resetUptime() {
  startupTime = SystemClock.uptimeMillis()
}

fun onInitializationAlreadyCompleted() = trace("alreadyCompleted") {
  TdlibManager.instance().watchDog().letsHelpDoge()
}

private fun initApplicationImpl() {
  if (isInitialized) {
    onInitializationAlreadyCompleted()
    return
  }

  trace("CrashManager") {
    CrashManager.instance().register()
  }
  trace("native") {
    N.init()
  }
  trace("Settings") {
    Settings.instance()
  }
  trace("TdlibNotificationUtils") {
    TdlibNotificationUtils.initialize()
  }

  if (BuildConfig.DEBUG || BuildConfig.EXPERIMENTAL) {
    trace("CrashReporter") {
      val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
      val isCrashing = AtomicBoolean(false)
      Thread.setDefaultUncaughtExceptionHandler(object : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
          if (isCrashing.getAndSet(true)) {
            return
          }
          error.printStackTrace()
          Settings.instance().storeCrash(Crash.Builder("Uncaught exception!", thread, error))
          isCrashing.set(false)
          if (defaultUncaughtExceptionHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(defaultUncaughtExceptionHandler)
            defaultUncaughtExceptionHandler.uncaughtException(thread, error)
            Thread.setDefaultUncaughtExceptionHandler(this)
          } else {
            Process.killProcess(Process.myPid())
            exitProcess(10)
          }
        }
      })
    }
  }

  if (BuildConfig.DEBUG) {
    Test.executeBeforeAppInit()
  }

  try {
    trace("TdlibManager") {
      TdlibManager.instance()
    }
  } catch (t: Throwable) {
    Tracer.onLaunchError(t)
    Log.e("App initialization failed", t)
    return
  }

  isInitialized = true

  if (BuildConfig.DEBUG) {
    Test.executeAfterAppInit()
  }
}

@Synchronized
fun initApplication() = trace("tgx:init") {
  initApplicationImpl()
}

fun ensureReady() {
  if (!isInitialized) {
    try {
      error("Trying to do something before application initialization. Log: \n${collectLog()}")
    } catch (e: Exception) {
      Tracer.onLaunchError(e)
    }
  }
}