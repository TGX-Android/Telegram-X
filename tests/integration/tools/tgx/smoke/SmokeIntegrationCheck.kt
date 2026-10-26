package tgx.smoke

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import me.vkryl.android.animatorx.BoolAnimator
import org.junit.Test
import org.junit.runner.RunWith
import tgx.test.*

private const val TAG = "TgxSmoke"

private const val WIDTH = 540
private const val HEIGHT = 960
private const val ITEM_HEIGHT = 96

@RunWith(AndroidJUnit4::class)
class SmokeIntegrationCheck {
  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun countsAllocations() {
    val objects = arrayOfNulls<Any>(100)
    val count = countAllocations {
      for (i in objects.indices) {
        objects[i] = Any()
      }
    }
    Log.i(TAG, "countAllocations of 100 objects: $count")
    assertWithMessage("Counted allocations of 100 objects").that(count).isAtLeast(100)
    assertNoAllocations {
      objects[0] = objects[1]
    }
  }

  @Test
  fun drawsWithoutAllocations() {
    val view = onMainThread {
      SmokeView(context, wrong = false)
    }
    assertDrawsWithoutAllocations(view, WIDTH, ITEM_HEIGHT)
  }

  @Test
  fun animatesSmoothly() {
    val view = onMainThread {
      SmokeView(context, wrong = false)
    }
    val counted = countAnimationFrames(
      view,
      WIDTH,
      ITEM_HEIGHT,
      isAnimating = { view.isAnimating }
    ) {
      view.checked.animatedValue = true
    }
    Log.i(TAG, "Correct animation: allocations ${counted.allocations}, layouts ${counted.layouts}")
    onMainThread {
      view.checked.forcedValue = false
    }
    assertAnimatesSmoothly(view, WIDTH, ITEM_HEIGHT, isAnimating = { view.isAnimating }) {
      view.checked.animatedValue = true
    }
  }

  @Test
  fun catchesWrongAnimation() {
    val view = onMainThread {
      SmokeView(context, wrong = true)
    }
    val counted = countAnimationFrames(
      view,
      WIDTH,
      ITEM_HEIGHT,
      isAnimating = { view.isAnimating }
    ) {
      view.checked.animatedValue = true
    }
    Log.i(TAG, "Wrong animation: allocations ${counted.allocations}, layouts ${counted.layouts}")
    assertWithMessage("Allocations caught in wrong animation")
      .that(counted.allocations)
      .isGreaterThan(0)
    assertWithMessage("Layout passes caught in wrong animation")
      .that(counted.layouts)
      .isGreaterThan(0)
  }

  @Test
  fun scrollsSmoothly() {
    val counted = countScrollFrames(smokeList(wrong = false), WIDTH, HEIGHT)
    Log.i(TAG, "Correct scrolling: $counted")
    assertScrollsSmoothly(smokeList(wrong = false), WIDTH, HEIGHT)
  }

  @Test
  fun catchesWrongScrolling() {
    val counted = countScrollFrames(smokeList(wrong = true), WIDTH, HEIGHT)
    Log.i(TAG, "Wrong scrolling: $counted")
    assertWithMessage("Allocations caught in wrong scrolling")
      .that(counted.allocations)
      .isGreaterThan(0)
    assertWithMessage("Layout passes caught in wrong scrolling")
      .that(counted.layouts)
      .isGreaterThan(0)
  }

  // 200 items are longer than 4 heights of the list
  private fun smokeList(wrong: Boolean): RecyclerView = recyclerViewOf(
    200,
    context,
    createView = {
      SmokeItemView(it, wrong)
    }
  ) { view, position ->
    view.position = position
  }
}

private class SmokeView(context: Context, private val wrong: Boolean) : View(context) {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  // Public, so that allocations are not removed as unused
  var label: String? = null

  val checked = BoolAnimator(1000L, LinearInterpolator()) { _, value, _, _ ->
    if (wrong) {
      label = "Progress $value"
      requestLayout()
    }
    invalidate()
  }

  val isAnimating: Boolean
    get() = checked.state == BoolAnimator.State.INTERMEDIATE

  override fun onDraw(canvas: Canvas) {
    paint.color = Color.BLUE
    canvas.drawCircle(width / 2f, height / 2f, height / 4f * (1f + checked.floatValue), paint)
  }
}

private class SmokeItemView(context: Context, private val wrong: Boolean) : View(context) {
  private val paint = Paint()
  // Public, so that allocations are not removed as unused
  var label: String? = null

  var position = 0
    set(value) {
      field = value
      if (wrong) {
        label = "Item $value"
      }
      invalidate()
    }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ITEM_HEIGHT)
  }

  override fun onDraw(canvas: Canvas) {
    paint.color = if (position % 2 == 0) Color.LTGRAY else Color.GRAY
    canvas.drawRect(0f, 0f, width * (position % 10 + 1) / 10f, height.toFloat(), paint)
    if (wrong) {
      requestLayout()
    }
  }
}
