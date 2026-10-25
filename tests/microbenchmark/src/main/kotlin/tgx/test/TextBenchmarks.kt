package tgx.test

import org.junit.Test

class TextBenchmarks(sample: TextSample) : TextSampleBenchmark(sample) {
  @Test
  fun layout_Text() = measure {
    sample.toText()
  }

  @Test
  fun layout_BoringOrStaticLayout() = measure {
    sample.toBoringOrStaticLayout()
  }

  @Test
  fun draw_Text() {
    val text = onMainThread { sample.toText() }
    measureDrawing(TEXT_WIDTH, text.height) {
      text.draw(it, 0, 0)
    }
  }

  @Test
  fun draw_BoringOrStaticLayout() {
    val layout = onMainThread { sample.toBoringOrStaticLayout() }
    measureDrawing(TEXT_WIDTH, layout.height) {
      layout.draw(it)
    }
  }
}
