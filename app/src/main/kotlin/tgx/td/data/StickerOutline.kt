package tgx.td.data

import android.graphics.Path
import me.vkryl.android.util.InvalidateDelegate
import org.drinkless.tdlib.TdApi.Outline
import org.drinkless.tdlib.TdApi.Sticker
import org.thunderdog.challegram.telegram.Tdlib
import tgx.td.buildOutline

class StickerOutline @JvmOverloads constructor(
  val sticker: Sticker,
  private val isAnimatedEmoji: Boolean = false,
  private val isClickedEmojiMessage: Boolean = false
) {
  private var outline: Outline? = null

  private var path: Path? = null
  private var pathWidth: Float = 0.0f
  private var pathHeight: Float = 0.0f
  private var displayWidth: Float = 0.0f
  private var displayHeight: Float = 0.0f

  fun setOutline(outline: Outline) {
    if (this.outline != outline) {
      this.outline = outline
      rebuild()
    }
  }

  fun setDisplayDimensions(width: Float, height: Float) {
    if (this.displayWidth != width || this.displayHeight != height) {
      this.displayWidth = width
      this.displayHeight = height
      rebuild()
    }
  }

  fun setDisplaySize(size: Float) = setDisplayDimensions(size, size)

  fun rebuild() {
    if (outline != null && displayWidth > 0 && displayHeight > 0) {
      if (path == null || pathWidth != displayWidth || pathHeight != displayHeight) {
        path = buildOutline(outline, sticker, displayWidth, displayHeight, path)
        pathWidth = displayWidth
        pathHeight = displayHeight
      }
    }
  }

  fun hasPath(): Boolean {
    return path != null && pathWidth == displayWidth && pathHeight == displayHeight
  }

  fun getPath(): Path {
    return path!!
  }

  @Synchronized
  fun requestOutline(tdlib: Tdlib, target: InvalidateDelegate) {
    if (outline != null) return
    tdlib.outline().requestStickerOutline(sticker.sticker.id, isAnimatedEmoji, isClickedEmojiMessage, { _, entry ->
      entry.value?.let { outline ->
        setOutline(outline)
        tdlib.runOnUiThread {
          target.invalidate()
        }
      }
    }, true)
  }
}