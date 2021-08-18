@file:JvmName("MessageId")

package me.vkryl.td

import me.vkryl.core.addElement
import me.vkryl.core.removeElement
import kotlin.math.min

class MessageId @JvmOverloads constructor (val chatId: Long, val messageId: Long, val otherMessageIds: LongArray? = null) {
  fun toServerMessageId () = toServerMessageId(messageId)

  fun isHistoryStart () = messageId == MIN_VALID_ID
  fun isHistoryEnd () = messageId == MAX_VALID_ID || messageId == 0L

  fun getMinMessageId (): Long = if (otherMessageIds?.isNotEmpty() == true) {
    min(this.messageId, otherMessageIds.minOrNull() ?: this.messageId)
  } else {
    this.messageId
  }

  fun compareTo (chatId: Long, messageId: Long) = this.chatId == chatId && (this.messageId == messageId || otherMessageIds?.contains(messageId) == true)
  fun compareTo (messageId: MessageId): Boolean {
    if (this.chatId == messageId.chatId) {
      if (this.messageId == messageId.messageId || otherMessageIds?.contains(messageId.messageId) == true) {
        return true
      }
      if (messageId.otherMessageIds != null) {
        for (otherMessageId in messageId.otherMessageIds) {
          if (this.messageId == otherMessageId || otherMessageIds?.contains(otherMessageId) == true) {
            return true
          }
        }
      }
    }
    return false
  }

  fun replaceMessageId (oldMessageId: Long, newMessageId: Long): MessageId {
    return if (this.messageId == oldMessageId) {
      MessageId(this.chatId, newMessageId, this.otherMessageIds)
    } else {
      val index = this.otherMessageIds?.indexOf(oldMessageId) ?: -1
      if (index != -1) {
        val otherMessageIds = this.otherMessageIds!!.removeElement(index).addElement(newMessageId)
        otherMessageIds.sort()
        MessageId(this.chatId, this.messageId, otherMessageIds)
      } else {
        this
      }
    }
  }

  companion object {
    const val MAX_VALID_ID = (Int.MAX_VALUE shl 20).toLong()
    const val MIN_VALID_ID = 9L

    @JvmStatic fun fromServerMessageId (serverMessageId: Long): Long = serverMessageId shl 20
    @JvmStatic fun toServerMessageId (tdlibMessageId: Long): Long = if (tdlibMessageId % (1 shl 20) == 0L) tdlibMessageId shr 20 else 0
  }
}