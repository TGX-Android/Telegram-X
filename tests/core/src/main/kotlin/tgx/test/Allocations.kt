// Allocations can be counted only in the process where test runs,
// so these helpers are used by integration tests

// Debug.startAllocCounting is the only way to count allocations of a thread
@file:Suppress("DEPRECATION")

package tgx.test

import android.animation.ValueAnimator
import android.os.Build
import android.os.Debug
import android.view.View
import com.google.common.truth.Truth.assertWithMessage

fun countAllocations(block: () -> Unit): Int {
  Debug.resetThreadAllocCount()
  Debug.startAllocCounting()
  try {
    block()
    return Debug.getThreadAllocCount()
  } finally {
    Debug.stopAllocCounting()
  }
}

/** Warm-up runs let code allocate once, e.g. to create a cache when it's first used */
fun assertNoAllocations(warmUpRuns: Int = 1, block: () -> Unit) {
  repeat(warmUpRuns) {
    block()
  }
  assertWithMessage("Objects allocated").that(countAllocations(block)).isEqualTo(0)
}

fun assertDrawsWithoutAllocations(view: View, width: Int, height: Int) = onMainThread {
  val canvas = view.layoutForDrawing(width, height)
  assertNoAllocations {
    view.draw(canvas)
  }
}

class AnimationFrames(val allocations: Int, val layouts: Int)

/**
 * Allocations include animators, their listeners, layout and drawing,
 * but not other work of the main thread between frames, e.g. app initialization.
 * Allocations and layout made once when animation starts and during its first frames
 * are not counted.
 * Animation must last at least [frames] + 4 frames, e.g. 120 ms on 60 Hz display by default.
 */
fun countAnimationFrames(
  view: View,
  width: Int,
  height: Int,
  frames: Int = 3,
  isAnimating: () -> Boolean,
  start: () -> Unit
): AnimationFrames {
  check(Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()) {
    "Animations are disabled on device"
  }
  val canvas = onMainThread {
    view.layoutForDrawing(width, height)
  }
  onMainThread(start)
  var frameAllocations = 0
  var allocations = 0
  var layouts = 0
  Debug.startAllocCounting()
  try {
    runOnFrames(
      SKIPPED_FRAMES + frames,
      beforeFrame = {
        frameAllocations = Debug.getThreadAllocCount()
      }
    ) { frame ->
      if (view.layoutIfRequested(width, height) && frame > SKIPPED_FRAMES) {
        layouts++
      }
      view.draw(canvas)
      if (frame > SKIPPED_FRAMES) {
        allocations += Debug.getThreadAllocCount() - frameAllocations
      }
    }
  } finally {
    Debug.stopAllocCounting()
  }
  check(onMainThread(isAnimating)) {
    "Animation finished before its frames were counted"
  }
  return AnimationFrames(allocations, layouts)
}

/**
 * Animators of `me.vkryl.android.animatorx` don't allocate objects on frames,
 * so allocations come from their listeners, layout or drawing
 */
fun assertAnimatesSmoothly(
  view: View,
  width: Int,
  height: Int,
  frames: Int = 3,
  isAnimating: () -> Boolean,
  start: () -> Unit
) {
  val counted = countAnimationFrames(view, width, height, frames, isAnimating, start)
  assertWithMessage("Objects allocated during animation").that(counted.allocations).isEqualTo(0)
  assertWithMessage("Layout passes during animation").that(counted.layouts).isEqualTo(0)
}

// Animation changes state from FALSE to INTERMEDIATE on its second frame,
// which may allocate objects and request layout once
private const val SKIPPED_FRAMES = 3
