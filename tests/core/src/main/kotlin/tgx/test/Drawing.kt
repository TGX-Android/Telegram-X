// Views checked by tests are not attached to window,
// so tests lay them out, draw them and run code on frames themselves
package tgx.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Choreographer
import android.view.View
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Frames are numbered from 1 */
internal fun interface FrameAction {
  fun onFrame(frame: Int)
}

/**
 * Runs [action] on [frames] frames after callbacks of frames posted earlier, e.g. of animators.
 * [beforeFrame] runs on the same frames before all callbacks of frames.
 */
internal fun runOnFrames(frames: Int, beforeFrame: Runnable? = null, action: FrameAction) {
  require(frames > 0)
  val finished = CountDownLatch(1)
  onMainThread {
    val choreographer = Choreographer.getInstance()
    val beforeFrameCallback = beforeFrame?.let {
      object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
          post()
          it.run()
        }

        fun post() {
          // Callbacks of a frame run in order of time when they are due,
          // so callback due earlier than posted runs first
          choreographer.postFrameCallbackDelayed(this, -BEFORE_FRAME_DELAY_MS)
        }
      }
    }
    beforeFrameCallback?.post()
    choreographer.postFrameCallback(object : Choreographer.FrameCallback {
      private var frame = 0

      override fun doFrame(frameTimeNanos: Long) {
        frame++
        action.onFrame(frame)
        if (frame == frames) {
          if (beforeFrameCallback != null) {
            choreographer.removeFrameCallback(beforeFrameCallback)
          }
          finished.countDown()
        } else {
          choreographer.postFrameCallback(this)
        }
      }
    })
  }
  check(finished.await(10, TimeUnit.SECONDS)) {
    "Frames were not drawn"
  }
}

// Longer than a frame of any display,
// so callback with this negative delay is due before callbacks posted on the previous frame
private const val BEFORE_FRAME_DELAY_MS = 1000L

// createBitmap of androidx.core does the same, tests don't need the library for it
@Suppress("UseKtx")
fun bitmapCanvas(width: Int, height: Int): Canvas =
  Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

internal fun View.layoutForDrawing(width: Int, height: Int): Canvas {
  layoutExactly(width, height)
  return bitmapCanvas(width, height)
}

private fun View.layoutExactly(width: Int, height: Int) {
  measure(
    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
  )
  layout(0, 0, width, height)
}

internal fun View.layoutIfRequested(width: Int, height: Int): Boolean {
  if (!isLayoutRequested) {
    return false
  }
  layoutExactly(width, height)
  return true
}
