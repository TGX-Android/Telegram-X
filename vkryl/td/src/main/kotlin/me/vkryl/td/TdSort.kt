@file:JvmName("Td")
@file:JvmMultifileClass

package me.vkryl.td

import org.drinkless.td.libcore.telegram.TdApi.*

private fun ChatList.toSortKey (): Int {
  return when (this.constructor) {
    ChatListMain.CONSTRUCTOR -> 0
    ChatListArchive.CONSTRUCTOR -> 1
    ChatListFilter.CONSTRUCTOR -> 2 + (this as ChatListFilter).chatFilterId
    else -> error(this.toString())
  }
}

fun Array<Chat>.sort (chatList: ChatList? = CHAT_LIST_MAIN) {
  this.sortWith(compareByDescending<Chat> { it.getOrder(chatList) }.thenByDescending { it.id })
}
fun MutableList<Chat>.sort (chatList: ChatList? = CHAT_LIST_MAIN) {
  this.sortWith(compareByDescending<Chat> { it.getOrder(chatList) }.thenByDescending { it.id })
}

fun Array<TextEntity>.sort () {
  this.sortWith(compareBy<TextEntity> {it.offset}.thenByDescending {it.length})
}
fun MutableList<TextEntity>.sort () {
  this.sortWith(compareBy<TextEntity> {it.offset}.thenByDescending {it.length})
}

fun Array<Message>.sort () = this.sortWith(compareBy {it.id})
fun Array<ChatPosition>.sort () = this.sortWith(compareBy {it.list.toSortKey()})

fun Array<Session>.sort () {
  this.sortWith(compareByDescending<Session> { it.isCurrent }.thenByDescending { it.isPasswordPending }.thenByDescending { it.lastActiveDate })
}
fun Array<LanguagePackInfo>.sort (activeLanguagePackId: String) {
  this.sortWith(compareByDescending<LanguagePackInfo> { it.isInstalled() }.thenBy { it.isBeta }.thenByDescending { it.isOfficial }.thenByDescending { !it.isInstalled() && activeLanguagePackId == it.id })
}