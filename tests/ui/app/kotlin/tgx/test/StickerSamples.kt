package tgx.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.os.Build
import android.util.Log
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibManager
import java.io.File

private const val TAG = "StickerSamples"

// Requests of TDLib, downloads included
private const val TIMEOUT_MS = 60_000L

// Animated emoji whose stickers change colors of skin by fitzpatrick types
private val FITZPATRICK_EMOJI = listOf("👍", "👋")

// Sets of animated emoji that the app loads by their names: of Telegram, of Telegram X, dice
private val SPECIAL_SETS = listOf(
  "AnimatedEmojies" to "animated emoji",
  "AnimatedTgxEmojies" to "animated emoji of Telegram X",
  "BetterDice" to "dice of Telegram X"
)

/**
 * Saves animated stickers of the account as samples of benchmarks of drawing, e.g.
 * `adb shell am broadcast -n org.example.tgx/tgx.test.StickerSamplesReceiver`: `.tgs` files named
 * by unique ids of their files, so that they stay the same between runs, in folders of `stickers`
 * in external files of the app, e.g. `/sdcard/Android/data/org.example.tgx/files/stickers`:
 * `sticker` of installed sets of stickers, `emoji` of installed sets of custom emoji, `effect` of
 * full-screen effects of their stickers and of reactions, `fitz` of animated emoji with colors of
 * skin, `reaction` of other animations of reactions, `animated` of other animated emoji and dice,
 * `status` of emoji statuses. Each file is saved once, in the first folder that has it.
 * `samples.tsv` lists the files with what they are, the links of their sets, their emoji and
 * whether they change colors of skin. Logs `done <count>` or the error with tag `StickerSamples`
 */
class StickerSamplesReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val tdlib = TdlibManager.instance().current()
    val directory = File(context.getExternalFilesDir(null), "stickers")
    // Downloads take longer than a receiver may run
    Thread {
      try {
        Log.i(TAG, "done ${StickerSamples(tdlib, directory).save()}")
      } catch (e: Throwable) {
        Log.i(TAG, "failed: $e")
      }
    }.start()
  }
}

private class StickerSamples(private val tdlib: Tdlib, private val directory: File) {
  private val saved = HashSet<String>()
  private val rows = mutableListOf("file\tkind\tset\temoji\tskin")
  private val links = HashMap<Long, String>()

  fun save(): Int {
    directory.deleteRecursively()
    for (set in installedSets(TdApi.StickerTypeRegular())) {
      for (sticker in set.stickers) {
        save("sticker", sticker, "sticker")
        val fullType = sticker.fullType
        if (fullType is TdApi.StickerFullTypeRegular) {
          fullType.premiumAnimation?.let {
            save("effect", it, sticker.format, "effect of a premium sticker", sticker)
          }
        }
      }
    }
    for (set in installedSets(TdApi.StickerTypeCustomEmoji())) {
      for (sticker in set.stickers) {
        save("emoji", sticker, "custom emoji")
      }
    }
    val reactions = tdlib.activeEmojiReactions.orEmpty().map { execute(TdApi.GetEmojiReaction(it)) }
    for (reaction in reactions) {
      save("effect", reaction.effectAnimation, "effect of a reaction", reaction.emoji)
    }
    for (emoji in FITZPATRICK_EMOJI) {
      save("fitz", execute(TdApi.GetAnimatedEmoji(emoji)).sticker, "animated emoji", skin = true)
    }
    for (reaction in reactions) {
      save("reaction", reaction.appearAnimation, "appearing reaction", reaction.emoji)
      save("reaction", reaction.selectAnimation, "reaction in the menu", reaction.emoji)
      save("reaction", reaction.activateAnimation, "chosen reaction", reaction.emoji)
      save("reaction", reaction.aroundAnimation, "effect around a reaction", reaction.emoji)
      save("reaction", reaction.centerAnimation, "center of a reaction", reaction.emoji)
    }
    for (sticker in execute(TdApi.GetCustomEmojiReactionAnimations()).stickers) {
      save("effect", sticker, "effect of a custom emoji reaction")
    }
    for ((name, kind) in SPECIAL_SETS) {
      val set = runCatching { execute(TdApi.SearchStickerSet(name, false)) }
        .onFailure { Log.i(TAG, "no set $name: $it") }
        .getOrNull() ?: continue
      for (sticker in set.stickers) {
        save("animated", sticker, kind, skin = hasSkin(sticker))
      }
    }
    val statuses = execute(TdApi.GetDefaultEmojiStatuses()).customEmojiIds +
      execute(TdApi.GetThemedEmojiStatuses()).customEmojiIds
    val statusStickers = execute(TdApi.GetCustomEmojiStickers(statuses.distinct().toLongArray()))
    for (sticker in statusStickers.stickers) {
      save("status", sticker, "emoji status")
    }
    File(directory, "samples.tsv").writeText(rows.joinToString("\n", postfix = "\n"))
    return saved.size
  }

  private fun installedSets(type: TdApi.StickerType): List<TdApi.StickerSet> =
    execute(TdApi.GetInstalledStickerSets(type)).sets.map {
      execute(TdApi.GetStickerSet(it.id))
    }

  private fun hasSkin(sticker: TdApi.Sticker): Boolean {
    // getAnimatedEmoji takes a fitzpatrick modifier after any emoji, so it comes from Unicode
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
      UCharacter.hasBinaryProperty(sticker.emoji.codePointAt(0), UProperty.EMOJI_MODIFIER_BASE)
  }

  private fun save(
    folder: String, sticker: TdApi.Sticker?, kind: String, emoji: String? = null,
    skin: Boolean = false
  ) {
    if (sticker != null) {
      save(folder, sticker.sticker, sticker.format, kind, sticker, emoji, skin)
    }
  }

  private fun save(
    folder: String, file: TdApi.File, format: TdApi.StickerFormat, kind: String,
    sticker: TdApi.Sticker, emoji: String? = null, skin: Boolean = false
  ) {
    val uniqueId = file.remote.uniqueId
    if (format !is TdApi.StickerFormatTgs || !saved.add(uniqueId)) {
      return
    }
    val target = File(directory, "$folder/$uniqueId.tgs")
    val downloaded = execute(TdApi.DownloadFile(file.id, 1, 0, 0, true))
    target.parentFile!!.mkdirs()
    File(downloaded.local.path).copyTo(target)
    val skinColumn = if (skin) "yes" else ""
    rows += listOf(
      "$folder/${target.name}", kind, link(sticker.setId), emoji ?: sticker.emoji, skinColumn
    ).joinToString("\t")
  }

  private fun link(setId: Long): String = if (setId == 0L) "" else links.getOrPut(setId) {
    // e.g. https://t.me/addstickers/<name>, or "" without a set
    val set = execute(TdApi.GetStickerSet(setId))
    val path = if (set.stickerType is TdApi.StickerTypeCustomEmoji) "addemoji" else "addstickers"
    "https://t.me/$path/${set.name}"
  }

  private fun <T : TdApi.Object> execute(function: TdApi.Function<T>): T =
    tdlib.clientExecuteT(function, TIMEOUT_MS, true)
}
