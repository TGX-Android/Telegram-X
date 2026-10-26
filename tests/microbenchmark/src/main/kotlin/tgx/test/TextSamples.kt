package tgx.test

import android.text.BoringLayout
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import org.drinkless.tdlib.TdApi
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.thunderdog.challegram.data.TD
import org.thunderdog.challegram.emoji.Emoji
import org.thunderdog.challegram.tool.Paints
import org.thunderdog.challegram.tool.Screen
import org.thunderdog.challegram.util.text.Text
import org.thunderdog.challegram.util.text.TextColorSets
import org.thunderdog.challegram.util.text.TextStyleProvider

/** E.g. `TextSample("links", "Read ", url("telegram.org"))` */
class TextSample(private val name: String, vararg parts: Any, val maxLines: Int = -1) {
  val text: TdApi.FormattedText

  init {
    val builder = StringBuilder()
    val entities = mutableListOf<TdApi.TextEntity>()
    for (part in parts) {
      if (part is Entity) {
        part.types.mapTo(entities) {
          TdApi.TextEntity(builder.length, part.text.length, it)
        }
        builder.append(part.text)
      } else {
        builder.append(part)
      }
    }
    text = TdApi.FormattedText(builder.toString(), entities.toTypedArray())
  }

  // Name of sample in names of tests
  override fun toString(): String = name
}

class Entity(val text: String, vararg val types: TdApi.TextEntityType)

fun bold(text: String) = Entity(text, TdApi.TextEntityTypeBold())
fun italic(text: String) = Entity(text, TdApi.TextEntityTypeItalic())
fun boldItalic(text: String) =
  Entity(text, TdApi.TextEntityTypeBold(), TdApi.TextEntityTypeItalic())
fun underline(text: String) = Entity(text, TdApi.TextEntityTypeUnderline())
fun strikethrough(text: String) = Entity(text, TdApi.TextEntityTypeStrikethrough())
fun code(text: String) = Entity(text, TdApi.TextEntityTypeCode())
fun pre(text: String) = Entity(text, TdApi.TextEntityTypePre())
fun preCode(text: String, language: String) = Entity(text, TdApi.TextEntityTypePreCode(language))
fun url(text: String) = Entity(text, TdApi.TextEntityTypeUrl())
fun textUrl(text: String, url: String) = Entity(text, TdApi.TextEntityTypeTextUrl(url))
fun mention(text: String) = Entity(text, TdApi.TextEntityTypeMention())
fun hashtag(text: String) = Entity(text, TdApi.TextEntityTypeHashtag())
fun phoneNumber(text: String) = Entity(text, TdApi.TextEntityTypePhoneNumber())
fun quote(text: String) = Entity(text, TdApi.TextEntityTypeBlockQuote())

private const val LONG_TEXT =
  "Text layout splits paragraphs into lines by words, measures every run of characters " +
    "with the same style and places them one after another, so its speed depends on the " +
    "number of words, styles and lines rather than on characters alone. Messages in chats " +
    "are usually a few lines long, while channel posts may take the whole screen."

val TEXT_SAMPLES = listOf(
  TextSample("short", "See you tomorrow at the office"),
  TextSample("long", LONG_TEXT),
  // Preview of the last message in chats list
  TextSample("ellipsized", LONG_TEXT, maxLines = 2),
  TextSample(
    "formatting",
    bold("Text layout"),
    " splits ",
    italic("paragraphs"),
    " into lines by ",
    boldItalic("words"),
    ", measures ",
    underline("every run"),
    " of characters with ",
    strikethrough("the same"),
    " style and places them one after another."
  ),
  TextSample(
    "links",
    "Read ",
    textUrl("the release notes", "https://telegram.org/blog"),
    " at ",
    url("https://telegram.org"),
    ", ask ",
    mention("@telegram"),
    " or search for ",
    hashtag("#android"),
    " and ",
    hashtag("#release"),
    " in chats."
  ),
  TextSample(
    "code",
    "Call ",
    code("Text.Builder(text, width)"),
    " and then:\n",
    pre("val text = builder.build()\ntext.draw(canvas, 0, 0)")
  ),
  // Code block with language, which can be highlighted
  TextSample(
    "syntax",
    "Kotlin:\n",
    preCode(
      "fun greet(name: String): String {\n" +
        "  // Template with a variable\n" +
        "  return \"Hello, \$name!\"\n" +
        "}",
      "kotlin"
    )
  ),
  TextSample("emoji", "Great news 🎉 Thanks 🙏 See you 👋😊"),
  TextSample(
    "rtl",
    "שלום עולם, this message mixes Hebrew and English, مرحبا بالعالم and Arabic in one paragraph."
  )
)

/** Width of text in a message, so long texts take several lines */
val TEXT_WIDTH: Int by lazy { Screen.dp(300f) }

private val textStyle: TextStyleProvider
  get() = Paints.robotoStyleProvider(15f)

fun TextSample.toText(
  width: Int = TEXT_WIDTH,
  maxLines: Int = this.maxLines,
  options: Text.Builder.() -> Unit = {}
): Text =
  Text.Builder(null, text, null, width, textStyle, TextColorSets.Regular.NORMAL, null)
    .maxLineCount(maxLines)
    .apply(options)
    .build()

fun TextSample.toBoringOrStaticLayout(width: Int = TEXT_WIDTH): Layout {
  val spans = Emoji.instance().replaceEmoji(TD.toDisplayCharSequence(text))
  val paint = textStyle.textPaint
  val metrics = BoringLayout.isBoring(spans, paint)
  return if (metrics != null && metrics.width <= width) {
    BoringLayout.make(spans, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, metrics, true)
  } else {
    val builder = StaticLayout.Builder.obtain(spans, 0, spans.length, paint, width)
    if (maxLines > 0) {
      builder.setMaxLines(maxLines)
      builder.setEllipsize(TextUtils.TruncateAt.END)
    }
    builder.build()
  }
}

@RunWith(Parameterized::class)
abstract class TextSampleBenchmark(val sample: TextSample) : MicroBenchmark() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "text={0}")
    fun samples() = TEXT_SAMPLES
  }
}
