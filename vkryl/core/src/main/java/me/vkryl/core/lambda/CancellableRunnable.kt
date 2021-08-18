/**
 * File created on 15/05/15 at 22:49
 * Copyright Vyacheslav Krylov, 2014
 */
package me.vkryl.core.lambda

import android.os.Handler
import androidx.core.os.CancellationSignal

abstract class CancellableRunnable : Runnable {
  private val signal = CancellationSignal()
  private var attachedToHandler: Handler? = null
  private val lock = Any()

  fun cancel() {
    signal.cancel()
  }

  fun removeOnCancel(handler: Handler?): CancellableRunnable {
    synchronized(lock) { attachedToHandler = handler }
    if (handler != null) {
      signal.setOnCancelListener {
        synchronized(lock) { attachedToHandler?.removeCallbacks(this) }
      }
    } else {
      signal.setOnCancelListener(null)
    }
    return this
  }

  val isPending: Boolean
    get() = !signal.isCanceled

  abstract fun act()

  final override fun run() {
    if (!signal.isCanceled) {
      act()
    }
  }
}