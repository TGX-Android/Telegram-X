// Debug.getThreadAllocCount is the only way to count allocations of a thread
@file:Suppress("DEPRECATION")

package tgx.test

import android.content.Context
import android.os.Debug
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.ceil

/** Unlike a lambda, receives position without boxing */
fun interface ItemBinder<V : View> {
  fun bind(view: V, position: Int)
}

fun <V : View> recyclerViewOf(
  itemCount: Int,
  context: Context = InstrumentationRegistry.getInstrumentation().targetContext,
  createView: (Context) -> V,
  binder: ItemBinder<V>
): RecyclerView =
  onMainThread {
    RecyclerView(context).apply {
      layoutManager = LinearLayoutManager(context)
      adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = itemCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
          val view = createView(parent.context)
          if (view.layoutParams == null) {
            view.layoutParams = RecyclerView.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.WRAP_CONTENT
            )
          }
          return object : RecyclerView.ViewHolder(view) { }
        }

        @Suppress("UNCHECKED_CAST")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
          binder.bind(holder.itemView as V, position)
        }
      }
    }
  }

/**
 * Durations of work on frames are slower than in app,
 * but can be compared before and after a change on the same device
 */
class ScrollFrames(
  val allocations: Int,
  val layouts: Int,
  val jumps: Int,
  val frameMsP50: Float,
  val frameMsP90: Float
) {
  override fun toString(): String =
    "allocations: $allocations, layouts: $layouts, jumps: $jumps, " +
      "frameMsP50: $frameMsP50, frameMsP90: $frameMsP90"
}

/**
 * Scrolls [list] laid out with given size by [step] pixels on every frame:
 * [frames] frames down and back up, so its items are created and bound once,
 * then [frames] frames down again, which are counted: objects allocated by scrolling,
 * layout and drawing of the list, layout passes of the list, jumps — frames when its items
 * move not by [step], e.g. because code changes scroll position or size of items —
 * and duration of scrolling, layout and drawing on each frame.
 * Other work of the main thread between frames, e.g. app initialization, is not counted.
 * List must be longer than its height plus [frames] × [step], 4 heights by default.
 */
fun countScrollFrames(
  list: RecyclerView,
  width: Int,
  height: Int,
  step: Int = height / 10,
  frames: Int = 30
): ScrollFrames {
  require(step > 0)
  val canvas = onMainThread {
    list.layoutForDrawing(width, height)
  }
  val warmUpFrames = 2 * frames
  val items = ItemTracker(list)
  val frameNanos = LongArray(frames)
  var allocations = 0
  var layouts = 0
  var jumps = 0
  var ended = false
  Debug.startAllocCounting()
  try {
    runOnFrames(warmUpFrames + frames) { frame ->
      val allocationsBefore = Debug.getThreadAllocCount()
      val start = System.nanoTime()
      val shift = if (frame > frames && frame <= warmUpFrames) -step else step
      // Items move between frames when list is laid out again or code changes them
      var jumped = items.moved(0)
      var layoutRequested = list.isLayoutRequested
      list.scrollBy(0, shift)
      jumped = items.moved(shift) || jumped
      layoutRequested = list.layoutIfRequested(width, height) || layoutRequested
      list.draw(canvas)
      if (frame > warmUpFrames) {
        frameNanos[frame - warmUpFrames - 1] = System.nanoTime() - start
        allocations += Debug.getThreadAllocCount() - allocationsBefore
        if (layoutRequested) {
          layouts++
        }
        if (jumped) {
          jumps++
        }
        if (!list.canScrollVertically(1)) {
          ended = true
        }
      }
    }
  } finally {
    Debug.stopAllocCounting()
  }
  check(!ended) {
    "List ended before its frames were counted: " +
      "it must be longer than its height plus frames * step"
  }
  frameNanos.sort()
  return ScrollFrames(
    allocations,
    layouts,
    jumps,
    frameNanos[frames / 2] / 1_000_000f,
    frameNanos[ceil(frames * 0.9).toInt() - 1] / 1_000_000f
  )
}

fun assertScrollsSmoothly(
  list: RecyclerView,
  width: Int,
  height: Int,
  step: Int = height / 10,
  frames: Int = 30
) {
  val counted = countScrollFrames(list, width, height, step, frames)
  assertWithMessage("Objects allocated during scrolling").that(counted.allocations).isEqualTo(0)
  assertWithMessage("Layout passes during scrolling").that(counted.layouts).isEqualTo(0)
  assertWithMessage("Frames with jumps during scrolling").that(counted.jumps).isEqualTo(0)
}

// More than any screen can show
private const val MAX_TRACKED_ITEMS = 256

/** Remembers positions of attached items without allocations */
private class ItemTracker(private val list: RecyclerView) {
  private val positions = IntArray(MAX_TRACKED_ITEMS)
  private val tops = IntArray(MAX_TRACKED_ITEMS)
  private var count = 0

  /**
   * Returns whether items attached on the previous call moved not by [shift] since then,
   * and remembers their current positions
   */
  fun moved(shift: Int): Boolean {
    var moved = false
    for (i in 0 until list.childCount) {
      val child = list.getChildAt(i)
      val index = indexOf(list.getChildAdapterPosition(child))
      if (index >= 0 && child.top != tops[index] - shift) {
        moved = true
      }
    }
    count = 0
    for (i in 0 until minOf(list.childCount, MAX_TRACKED_ITEMS)) {
      val child = list.getChildAt(i)
      val position = list.getChildAdapterPosition(child)
      if (position != RecyclerView.NO_POSITION) {
        positions[count] = position
        tops[count] = child.top
        count++
      }
    }
    return moved
  }

  private fun indexOf(position: Int): Int {
    if (position != RecyclerView.NO_POSITION) {
      for (i in 0 until count) {
        if (positions[i] == position) {
          return i
        }
      }
    }
    return -1
  }
}
