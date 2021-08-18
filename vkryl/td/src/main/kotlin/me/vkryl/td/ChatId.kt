@file:JvmName("ChatId")

package me.vkryl.td

import org.drinkless.td.libcore.telegram.TdApi.*

const val MIN_SECRET_ID = -2002147483648L
const val ZERO_SECRET_ID = -2000000000000L
const val MAX_SECRET_ID = -1997852516353L
const val MIN_CHANNEL_ID = -1002147483647L
const val MAX_CHANNEL_ID = -1000000000000L
const val MIN_CHAT_ID = -2147483647L
const val MAX_USER_ID = 2147483647L

@ChatType.Constructors
@JvmOverloads
fun getType (chatId: Long, validate: Boolean = true): Int {
  return when (chatId) {
    in 1 .. MAX_USER_ID -> ChatTypePrivate.CONSTRUCTOR
    in MIN_CHAT_ID .. -1 -> ChatTypeBasicGroup.CONSTRUCTOR
    in MIN_CHANNEL_ID until MAX_CHANNEL_ID -> ChatTypeSupergroup.CONSTRUCTOR
    in MIN_SECRET_ID until MAX_SECRET_ID -> ChatTypeSecret.CONSTRUCTOR
    else -> if (validate) error(chatId.toString()) else 0
  }
}

fun toUserId (chatId: Long): Int {
  return if (getType(chatId, false) == ChatTypePrivate.CONSTRUCTOR) chatId.toInt() else 0
}
fun toSecretChatId (chatId: Long): Int {
  return if (getType(chatId, false) == ChatTypeSecret.CONSTRUCTOR) (chatId - ZERO_SECRET_ID).toInt() else 0
}
fun toBasicGroupId (chatId: Long): Int {
  return if (getType(chatId, false) == ChatTypeBasicGroup.CONSTRUCTOR) (-chatId).toInt() else 0
}
fun toSupergroupId (chatId: Long): Int {
  return if (getType(chatId, false) == ChatTypeSupergroup.CONSTRUCTOR) (MAX_CHANNEL_ID - chatId).toInt() else 0
}

fun isBasicGroup (chatId: Long): Boolean = toBasicGroupId(chatId) != 0
fun isSecret (chatId: Long): Boolean = toSecretChatId(chatId) != 0
fun isPrivate (chatId: Long): Boolean = toUserId(chatId) != 0
fun isSupergroup (chatId: Long): Boolean = toSupergroupId(chatId) != 0

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

fun fromUserId (userId: Int): Long = userId.toLong()
fun fromBasicGroupId (basicGroupId: Int): Long = (-basicGroupId).toLong()
fun fromSupergroupId (supergroupId: Int): Long = MAX_CHANNEL_ID - supergroupId.toLong()
fun fromSecretChatId (secretChatId: Int): Long = ZERO_SECRET_ID + secretChatId.toLong()

