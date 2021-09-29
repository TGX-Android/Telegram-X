@file:JvmName("ChatId")

package me.vkryl.td

import org.drinkless.td.libcore.telegram.TdApi.*

// the last (1 << 31) - 1 identifiers will be used for secret chat dialog identifiers
const val MAX_CHANNEL_ID = 1000000000000L - (1L shl 31)
const val MAX_USER_ID = (1L shl 40) - 1
const val MAX_GROUP_ID = 999999999999L
const val ZERO_SECRET_CHAT_ID = -2000000000000L
const val ZERO_CHANNEL_ID = -1000000000000L

@ChatType.Constructors
@JvmOverloads
fun getType (chatId: Long, validate: Boolean = true): Int {
  return when {
    chatId in 1..MAX_USER_ID -> {
      ChatTypePrivate.CONSTRUCTOR
    }
    chatId < 0 -> when {
      -MAX_GROUP_ID <= chatId -> {
        ChatTypeBasicGroup.CONSTRUCTOR
      }
      ZERO_CHANNEL_ID - MAX_CHANNEL_ID <= chatId && chatId != ZERO_CHANNEL_ID -> {
        ChatTypeSupergroup.CONSTRUCTOR
      }
      ZERO_SECRET_CHAT_ID + Int.MIN_VALUE <= chatId && chatId != ZERO_SECRET_CHAT_ID -> {
        ChatTypeSecret.CONSTRUCTOR
      }
      else -> {
        if (validate) {
          error(chatId.toString())
        }
        0
      }
    }
    else -> {
      if (validate) {
        error(chatId.toString())
      }
      0
    }
  }
}

fun toUserId (chatId: Long): Long {
  return if (getType(chatId, false) == ChatTypePrivate.CONSTRUCTOR) chatId else 0
}
fun toSecretChatId (chatId: Long): Int {
  return if (getType(chatId, false) == ChatTypeSecret.CONSTRUCTOR) (chatId - ZERO_SECRET_CHAT_ID).toInt() else 0
}
fun toBasicGroupId (chatId: Long): Long {
  return if (getType(chatId, false) == ChatTypeBasicGroup.CONSTRUCTOR) -chatId else 0
}
fun toSupergroupId (chatId: Long): Long {
  return if (getType(chatId, false) == ChatTypeSupergroup.CONSTRUCTOR) (ZERO_CHANNEL_ID - chatId) else 0
}

fun isBasicGroup (chatId: Long): Boolean = toBasicGroupId(chatId) != 0L
fun isSecret (chatId: Long): Boolean = toSecretChatId(chatId) != 0
fun isPrivate (chatId: Long): Boolean = toUserId(chatId) != 0L
fun isSupergroup (chatId: Long): Boolean = toSupergroupId(chatId) != 0L

fun isUserChat (chatId: Long): Boolean {
  return when (getType(chatId, false)) {
    ChatTypePrivate.CONSTRUCTOR, ChatTypeSecret.CONSTRUCTOR -> true
    ChatTypeBasicGroup.CONSTRUCTOR, ChatTypeSupergroup.CONSTRUCTOR -> false
    else -> false
  }
}
fun isMultiChat (chatId: Long): Boolean {
  return when (getType(chatId, false)) {
    ChatTypePrivate.CONSTRUCTOR, ChatTypeSecret.CONSTRUCTOR -> false
    ChatTypeBasicGroup.CONSTRUCTOR, ChatTypeSupergroup.CONSTRUCTOR -> true
    else -> false
  }
}

fun fromUserId (userId: Long): Long = userId
fun fromBasicGroupId (basicGroupId: Long): Long = -basicGroupId
fun fromSupergroupId (supergroupId: Long): Long = ZERO_CHANNEL_ID - supergroupId;
fun fromSecretChatId (secretChatId: Int): Long = ZERO_SECRET_CHAT_ID + secretChatId

