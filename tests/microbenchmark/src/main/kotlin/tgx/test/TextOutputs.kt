package tgx.test

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.thunderdog.challegram.tool.Screen
import org.thunderdog.challegram.util.text.Highlight
import org.thunderdog.challegram.util.text.Text

// Monospace text without characters that split it into lines
private val MONOSPACE_WORD = "MonospaceTextWithoutSplitters".repeat(4)

private val MORE_TEXT_SAMPLES = listOf(
  TextSample("paragraphs", "First paragraph\n\nThird line after an empty one\n    indented line"),
  TextSample(
    "longWord",
    "Address https://telegram.org/blog/address-that-is-longer-than-the-whole-line-of-a-message" +
      " without spaces"
  ),
  TextSample("cjk", "你好，世界！これは日本語のテキストです。한국어 텍스트도 있습니다."),
  TextSample(
    "quote",
    quote("Text layout splits paragraphs into lines by words"),
    "\nAnswer after the quote"
  ),
  TextSample("codeWord", code(MONOSPACE_WORD)),
  TextSample("preWord", pre(MONOSPACE_WORD)),
  TextSample(
    "phoneAfterMinus",
    "Receive error [Error : 0 : Receive invalid user identifier], while parsing push payload " +
      """{"loc_key":"READ_STORIES","loc_args":[],"custom":{"from_id":"-""",
    phoneNumber("1005031786"),
    """","max_id":"70"}"""
  )
)

class TextOptions(private val name: String, val apply: Text.Builder.(text: String) -> Unit) {
  override fun toString(): String = name
}

private val DEFAULT_OPTIONS = TextOptions("default") {}

// Options of Text.Builder that app uses
private val TEXT_OPTIONS = listOf(
  TextOptions("singleLine") {
    singleLine()
  },
  TextOptions("alignCenter") {
    addFlags(Text.FLAG_ALIGN_CENTER)
  },
  TextOptions("alignRight") {
    addFlags(Text.FLAG_ALIGN_RIGHT)
  },
  // Icons before the first line in chats list
  TextOptions("firstLineMargin") {
    lineMarginProvider { lineIndex, _, _, _ ->
      if (lineIndex == 0) Screen.dp(24f) else 0
    }
  },
  // Lines next to image of link preview
  TextOptions("narrowTop") {
    lineWidthProvider { _, y, maxWidth, _ ->
      if (y < Screen.dp(40f)) maxWidth - Screen.dp(80f) else maxWidth
    }
  },
  // Name of sender before message in chats list
  TextOptions("suffix") {
    suffix(": ")
  },
  TextOptions("highlight") { text ->
    highlight(Highlight.valueOf(text, "the"))
  },
  TextOptions("allBold") {
    allBold()
  },
  TextOptions("ignoreNewLines") {
    ignoreNewLines()
  },
  TextOptions("article") {
    addFlags(Text.FLAG_ARTICLE)
  }
)

/** Text of [sample] laid out in [widthDp] with [maxLines] and [options] */
class TextCase(
  private val sample: TextSample,
  private val widthDp: Int,
  private val maxLines: Int,
  private val options: TextOptions
) {
  val width: Int
    get() = Screen.dp(widthDp.toFloat())

  fun toText(): Text = sample.toText(width, maxLines) {
    options.apply(this, sample.text.text)
  }

  // Name of case in names of tests
  override fun toString(): String =
    "text=$sample,width=$widthDp,maxLines=$maxLines,options=$options"
}

val TEXT_CASES: List<TextCase> = run {
  val samples = TEXT_SAMPLES.filter {
    it.maxLines < 0
  } + MORE_TEXT_SAMPLES
  val sizes = samples.flatMap { sample ->
    listOf(100, 200, 300).flatMap { width ->
      listOf(-1, 2).map { maxLines ->
        TextCase(sample, width, maxLines, DEFAULT_OPTIONS)
      }
    }
  }
  val options = samples.flatMap { sample ->
    TEXT_OPTIONS.map { options ->
      TextCase(sample, 200, -1, options)
    }
  }
  sizes + options
}

/**
 * Saves drawings and sizes of texts in more widths, limits of lines and options than benchmarks
 * measure, in about a minute, so that `compareBenchmarkResults` finds their changes
 */
@RunWith(Parameterized::class)
class TextOutputs(private val case: TextCase) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases() = TEXT_CASES
  }

  @get:Rule
  val testName = TestName()

  @Test
  fun draw_Text() {
    val text = onMainThread { case.toText() }
    val name = "TextOutputs.${testName.methodName}"
    outputDrawing(name, case.width, text.height) {
      text.draw(it, 0, 0)
    }
    outputText(name, sizesOf(text, case.width))
  }
}

/** Sizes of texts in all cases of [TextOutputs], e.g. to compare variants of code in one run */
fun sizesOfTextCases(): String = onMainThread {
  TEXT_CASES.joinToString("") { "$it\n${sizesOf(it.toText(), it.width)}" }
}

// Views place other content by sizes of text, e.g. time of message next to its last line
private fun sizesOf(text: Text, maxWidth: Int): String {
  val lineWidths = (0 until text.lineCount).map(text::getLineWidth)
  val rtl = if (text.lastLineIsRtl) ", right to left" else ""
  return "width ${text.width} of $maxWidth, height ${text.height}\n" +
    "${text.lineCount} lines of widths ${lineWidths.joinToString()}\n" +
    "last line width ${text.lastLineWidth}$rtl\n"
}
