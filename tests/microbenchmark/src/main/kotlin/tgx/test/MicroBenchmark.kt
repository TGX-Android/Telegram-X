package tgx.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RenderNode
import android.os.Build
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeatedOnMainThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.thunderdog.challegram.core.Media
import org.thunderdog.challegram.theme.Theme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests that compare implementations of the same operation are named `operation_Implementation`,
 * e.g. `layout_Text` and `layout_BoringOrStaticLayout`,
 * so that `describeBenchmarkResults` shows them side by side.
 */
@RunWith(AndroidJUnit4::class)
abstract class MicroBenchmark {
  @get:Rule
  val benchmark = BenchmarkRule()

  @get:Rule
  val testName = TestName()

  inline fun measure(crossinline block: () -> Unit) = benchmark.measureRepeatedOnMainThread {
    block()
  }

  /**
   * Saves result of measured code, e.g. `output(parse(input))`,
   * so that `compareBenchmarkResults` finds its changes
   */
  fun output(value: Any?) = outputText(outputName, value)

  /**
   * Measures recording of canvas commands, like drawing of views, without rendering by GPU.
   * The drawing is saved as output, see [outputDrawing].
   */
  fun measureDrawing(width: Int, height: Int, draw: (Canvas) -> Unit) {
    outputDrawing(outputName, width, height, draw)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val node = RenderNode(null)
      node.setPosition(0, 0, width, height)
      measure {
        draw(node.beginRecording())
        node.endRecording()
      }
    } else {
      // Before Android 10 display lists of views aren't public,
      // picture records canvas commands similarly
      val picture = Picture()
      measure {
        draw(picture.beginRecording(width, height))
        picture.endRecording()
      }
    }
  }

  private val outputName: String
    get() = "${javaClass.simpleName}.${testName.methodName}"
}

/** Saves [value] as output named [name], so that `compareBenchmarkResults` finds its changes */
fun outputText(name: String, value: Any?) = outputFile(name, "txt").writeText(value.toString())

/**
 * Saves drawing as output named [name], when images requested by drawing are loaded, e.g. emoji,
 * so that `compareBenchmarkResults` finds its changes
 */
fun outputDrawing(name: String, width: Int, height: Int, draw: (Canvas) -> Unit) {
  @Suppress("UseKtx") // Like in bitmapCanvas
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  onMainThread {
    draw(canvas)
  }
  waitForBackgroundLoading()
  // Text of the app is drawn on the background of the theme, which viewers of images don't have
  bitmap.eraseColor(Theme.fillingColor())
  onMainThread {
    draw(canvas)
  }
  outputFile(name, "png").outputStream().use {
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
  }
}

// Gradle replaces other characters in names of files that it copies from device
private val UNSAFE_FILE_NAME_CHARACTERS = Regex("""[^0-9A-Za-z._-]+""")

private fun outputFile(name: String, extension: String): File {
  // MicroBenchmarkRunner packs outputs into one file, which Gradle copies from device
  val fileName = name.replace(UNSAFE_FILE_NAME_CHARACTERS, "_")
  return File(outputsDirectory.apply { mkdirs() }, "$fileName.output.$extension")
}

private fun waitForBackgroundLoading() {
  val loaded = CountDownLatch(1)
  Media.instance().post {
    loaded.countDown()
  }
  check(loaded.await(10, TimeUnit.SECONDS)) {
    "Images were not loaded"
  }
  InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
