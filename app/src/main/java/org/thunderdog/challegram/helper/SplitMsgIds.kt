package org.thunderdog.challegram.helper

import org.drinkless.td.libcore.telegram.TdApi

class SplitMsgIds (val messageIds: LongArray, val sponsoredIds: IntArray) {
  companion object {
    @JvmStatic fun fromArray (src: LongArray) = src.partition { it > 0 }.let { parts -> SplitMsgIds(parts.first.toLongArray(), parts.second.map { id -> ((-id).toInt()) }.toIntArray()) }
  }

  fun hasSponsoredMessages() = sponsoredIds.isNotEmpty()
  fun getSponsoredQueries (chatId: Long) = sponsoredIds.map { TdApi.ViewSponsoredMessage(chatId, it) }.toTypedArray()
}