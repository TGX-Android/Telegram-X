package tgx.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.tool.UI
import tgx.td.MAX_CHANNEL_ID
import tgx.td.MAX_GROUP_ID
import tgx.td.MAX_USER_ID
import tgx.td.fromBasicGroupId
import tgx.td.fromSecretChatId
import tgx.td.fromSupergroupId
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "FakeChats"

// Made-up ids are the last ones of their ranges, far from ids of real chats
private const val ID_RANGE = 1_000_000L

/**
 * Chats that only the app knows, made up by tests: users, private and secret chats, basic groups,
 * supergroups, channels and broadcast groups with their members and messages. They come to the
 * app by updates, as from TDLib, and queries of the app about them are answered here, by the
 * interceptor of the client of TDLib, so that their screens look as with real chats, e.g. for
 * images of types of messages or for stores in each language. Queries about them that aren't
 * answered yet go to TDLib and are logged with tag FakeChats
 */
class FakeChats private constructor(private val tdlib: Tdlib) : Client.Interceptor {
  companion object {
    private val instances = HashMap<Int, FakeChats>()

    fun of(tdlib: Tdlib): FakeChats = synchronized(instances) {
      instances.getOrPut(tdlib.id()) { FakeChats(tdlib) }.also {
        // The client changes when the account restarts
        tdlib.client().setInterceptor(it)
      }
    }
  }

  private val users = ConcurrentHashMap<Long, TdApi.User>()
  private val userFullInfos = ConcurrentHashMap<Long, TdApi.UserFullInfo>()
  private val basicGroups = ConcurrentHashMap<Long, TdApi.BasicGroup>()
  private val basicGroupFullInfos = ConcurrentHashMap<Long, TdApi.BasicGroupFullInfo>()
  private val supergroups = ConcurrentHashMap<Long, TdApi.Supergroup>()
  private val supergroupFullInfos = ConcurrentHashMap<Long, TdApi.SupergroupFullInfo>()
  private val secretChats = ConcurrentHashMap<Int, TdApi.SecretChat>()
  private val chats = ConcurrentHashMap<Long, TdApi.Chat>()
  private val messages = ConcurrentHashMap<Long, ConcurrentSkipListMap<Long, TdApi.Message>>()

  private val lastUserId = AtomicLong(MAX_USER_ID - ID_RANGE)
  private val lastBasicGroupId = AtomicLong(MAX_GROUP_ID - ID_RANGE)
  private val lastSupergroupId = AtomicLong(MAX_CHANNEL_ID - ID_RANGE)
  private val lastSecretChatId = AtomicLong(Int.MAX_VALUE - ID_RANGE)
  private val lastOrder = AtomicLong(Long.MAX_VALUE - ID_RANGE)
  private val lastMessageId = AtomicLong()

  private val now: Int get() = (System.currentTimeMillis() / 1000).toInt()

  fun isMadeUp(chatId: Long): Boolean = chats.containsKey(chatId)

  fun chat(chatId: Long): TdApi.Chat? = chats[chatId]

  fun user(userId: Long): TdApi.User? = users[userId]

  fun addUser(firstName: String, lastName: String = "", accentColorId: Int = 0): TdApi.User {
    val user = TdApi.User().apply {
      id = lastUserId.incrementAndGet()
      this.firstName = firstName
      this.lastName = lastName
      phoneNumber = ""
      status = TdApi.UserStatusRecently()
      this.accentColorId = accentColorId
      profileAccentColorId = -1
      haveAccess = true
      type = TdApi.UserTypeRegular()
      languageCode = ""
    }
    users[user.id] = user
    userFullInfos[user.id] = TdApi.UserFullInfo().apply {
      canBeCalled = true
      supportsVideoCalls = true
      val gifts = TdApi.AcceptedGiftTypes(true, true, true, true, true)
      giftSettings = TdApi.GiftSettings(false, gifts)
    }
    tdlib.sendFakeUpdate(TdApi.UpdateUser(user))
    return user
  }

  fun addPrivateChat(user: TdApi.User): TdApi.Chat =
    addChat(TdApi.ChatTypePrivate(user.id), name(user), user.accentColorId)

  fun name(user: TdApi.User): String = "${user.firstName} ${user.lastName}".trim()

  fun addSecretChat(user: TdApi.User): TdApi.Chat {
    val secretChat = TdApi.SecretChat().apply {
      id = lastSecretChatId.incrementAndGet().toInt()
      userId = user.id
      state = TdApi.SecretChatStateReady()
      isOutbound = true
      keyHash = ByteArray(36)
      layer = 144
    }
    secretChats[secretChat.id] = secretChat
    tdlib.sendFakeUpdate(TdApi.UpdateSecretChat(secretChat))
    val chat = addChat(
      TdApi.ChatTypeSecret(secretChat.id, user.id),
      name(user),
      user.accentColorId
    )
    check(chat.id == fromSecretChatId(secretChat.id))
    return chat
  }

  fun addBasicGroup(title: String, members: List<TdApi.User>, accentColorId: Int = 0): TdApi.Chat {
    val group = TdApi.BasicGroup().apply {
      id = lastBasicGroupId.incrementAndGet()
      memberCount = members.size + 1
      status = TdApi.ChatMemberStatusMember(0)
      isActive = true
    }
    basicGroups[group.id] = group
    basicGroupFullInfos[group.id] = TdApi.BasicGroupFullInfo().apply {
      description = ""
      creatorUserId = members.firstOrNull()?.id ?: tdlib.myUserId()
      this.members = (members.map { it.id } + tdlib.myUserId()).map { member(it) }.toTypedArray()
      botCommands = arrayOf()
    }
    tdlib.sendFakeUpdate(TdApi.UpdateBasicGroup(group))
    val chat = addChat(TdApi.ChatTypeBasicGroup(group.id), title, accentColorId)
    check(chat.id == fromBasicGroupId(group.id))
    return chat
  }

  fun addSupergroup(
    title: String, members: List<TdApi.User>, isChannel: Boolean = false,
    isBroadcastGroup: Boolean = false, accentColorId: Int = 0
  ): TdApi.Chat {
    val supergroup = TdApi.Supergroup().apply {
      id = lastSupergroupId.incrementAndGet()
      usernames = TdApi.Usernames(arrayOf(), arrayOf(), "", arrayOf(), "")
      date = now
      status = TdApi.ChatMemberStatusMember(0)
      memberCount = members.size + 1
      this.isChannel = isChannel
      this.isBroadcastGroup = isBroadcastGroup
    }
    supergroups[supergroup.id] = supergroup
    supergroupFullInfos[supergroup.id] = TdApi.SupergroupFullInfo().apply {
      description = ""
      memberCount = supergroup.memberCount
      canGetMembers = !isChannel
      isAllHistoryAvailable = true
      botCommands = arrayOf()
    }
    tdlib.sendFakeUpdate(TdApi.UpdateSupergroup(supergroup))
    val chat = addChat(TdApi.ChatTypeSupergroup(supergroup.id, isChannel), title, accentColorId)
    check(chat.id == fromSupergroupId(supergroup.id))
    return chat
  }

  private fun member(userId: Long): TdApi.ChatMember = TdApi.ChatMember(
    TdApi.MessageSenderUser(userId), "", 0, now, TdApi.ChatMemberStatusMember(0)
  )

  private fun addChat(type: TdApi.ChatType, title: String, accentColorId: Int): TdApi.Chat {
    val chat = TdApi.Chat().apply {
      id = when (type) {
        is TdApi.ChatTypePrivate -> type.userId
        is TdApi.ChatTypeSecret -> fromSecretChatId(type.secretChatId)
        is TdApi.ChatTypeBasicGroup -> fromBasicGroupId(type.basicGroupId)
        is TdApi.ChatTypeSupergroup -> fromSupergroupId(type.supergroupId)
        else -> error(type)
      }
      this.type = type
      this.title = title
      this.accentColorId = accentColorId
      profileAccentColorId = -1
      permissions = TdApi.ChatPermissions(
        true, true, true, true, true, true, true, true,
        true, true, true, true, true, true, true, true
      )
      positions = newestPosition()
      chatLists = arrayOf(TdApi.ChatListMain())
      notificationSettings = TdApi.ChatNotificationSettings(
        true, 0, true, 0, true, true, true, false, true, 0, true, true, true, false, true, false
      )
      availableReactions = TdApi.ChatAvailableReactionsAll(0)
      videoChat = TdApi.VideoChat(0, false, null)
      clientData = ""
    }
    chats[chat.id] = chat
    messages[chat.id] = ConcurrentSkipListMap()
    tdlib.sendFakeUpdate(TdApi.UpdateNewChat(chat))
    return chat
  }

  fun addMessage(
    chatId: Long, content: TdApi.MessageContent, outgoing: Boolean,
    sender: TdApi.MessageSender? = null, date: Int = now
  ): TdApi.Message {
    val chat = chats.getValue(chatId)
    val message = TdApi.Message().apply {
      // Ids of messages on the server, which TDLib shifts by 20 bits
      id = lastMessageId.incrementAndGet() shl 20
      this.chatId = chatId
      senderId = sender ?: if (outgoing) {
        TdApi.MessageSenderUser(tdlib.myUserId())
      } else {
        incomingSender(chatId)
      }
      isOutgoing = outgoing
      // Messages that can't be saved make the window secure and screenshots black
      canBeSaved = true
      isChannelPost = chat.type.let { it is TdApi.ChatTypeSupergroup && it.isChannel }
      this.date = date
      unreadReactions = arrayOf()
      senderTag = ""
      authorSignature = ""
      summaryLanguageCode = ""
      this.content = content
    }
    messages.getValue(chatId)[message.id] = message
    tdlib.sendFakeUpdate(TdApi.UpdateNewMessage(message))
    chat.lastMessage = message
    // The chat goes to the top of the list, as with a new message from the server
    tdlib.sendFakeUpdate(TdApi.UpdateChatLastMessage(chatId, message, newestPosition()))
    return message
  }

  private fun newestPosition(): Array<TdApi.ChatPosition> {
    // Above all real chats, also pinned ones, and above made-up chats of earlier calls
    val order = lastOrder.incrementAndGet()
    return arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), order, false, null))
  }

  fun incomingSender(chatId: Long): TdApi.MessageSender {
    // Of a message that the test doesn't give a sender: the user of the chat, the creator of a
    // basic group, the first made-up user in a supergroup, or the channel itself
    val chat = chats.getValue(chatId)
    return when (val type = chat.type) {
      is TdApi.ChatTypePrivate -> TdApi.MessageSenderUser(type.userId)
      is TdApi.ChatTypeSecret -> TdApi.MessageSenderUser(type.userId)
      is TdApi.ChatTypeBasicGroup ->
        TdApi.MessageSenderUser(basicGroupFullInfos.getValue(type.basicGroupId).creatorUserId)
      is TdApi.ChatTypeSupergroup -> if (type.isChannel) {
        TdApi.MessageSenderChat(chatId)
      } else {
        TdApi.MessageSenderUser(users.keys.min())
      }
      else -> error(type)
    }
  }

  override fun intercept(query: TdApi.Function<*>): TdApi.Object? {
    val answer: TdApi.Object? = when (query) {
      is TdApi.GetUser -> users[query.userId]
      is TdApi.GetUserFullInfo -> userFullInfos[query.userId]
      is TdApi.GetBasicGroup -> basicGroups[query.basicGroupId]
      is TdApi.GetBasicGroupFullInfo -> basicGroupFullInfos[query.basicGroupId]
      is TdApi.GetSupergroup -> supergroups[query.supergroupId]
      is TdApi.GetSupergroupFullInfo -> supergroupFullInfos[query.supergroupId]
      is TdApi.GetSecretChat -> secretChats[query.secretChatId]
      is TdApi.GetChat -> chats[query.chatId]
      is TdApi.OpenChat -> ok(query.chatId)
      is TdApi.CloseChat -> ok(query.chatId)
      is TdApi.ViewMessages -> ok(query.chatId)
      is TdApi.GetChatHistory -> messages[query.chatId]?.let { history(it, query) }
      is TdApi.GetMessage -> messages[query.chatId]?.get(query.messageId)
      is TdApi.GetMessageLocally -> messages[query.chatId]?.get(query.messageId)
      is TdApi.GetMessages -> messages[query.chatId]?.let { chatMessages ->
        val found = query.messageIds.map { chatMessages[it] }.toTypedArray()
        TdApi.Messages(found.size, found)
      }
      is TdApi.GetMessageProperties ->
        messages[query.chatId]?.get(query.messageId)?.let { properties(it) }
      is TdApi.SearchChatMessages -> if (isMadeUp(query.chatId)) {
        TdApi.FoundChatMessages(0, arrayOf(), 0)
      } else {
        null
      }
      is TdApi.GetChatMessageCount -> messages[query.chatId]?.let { chatMessages ->
        TdApi.Count(chatMessages.values.count { matches(query.filter, it.content) })
      }
      is TdApi.SearchChatRecentLocationMessages ->
        if (isMadeUp(query.chatId)) TdApi.Messages(0, arrayOf()) else null
      is TdApi.GetChatAdministrators -> chats[query.chatId]?.let { administrators(it) }
      is TdApi.GetChatSponsoredMessages ->
        if (isMadeUp(query.chatId)) TdApi.SponsoredMessages(arrayOf(), 0) else null
      else -> null
    }
    if (answer == null && mentionsMadeUp(query)) {
      Log.w(TAG, "unanswered ${query.javaClass.simpleName}")
    }
    return answer
  }

  private fun matches(filter: TdApi.SearchMessagesFilter?, content: TdApi.MessageContent): Boolean =
    when (filter) {
      null, is TdApi.SearchMessagesFilterEmpty -> true
      is TdApi.SearchMessagesFilterPhoto -> content is TdApi.MessagePhoto
      is TdApi.SearchMessagesFilterVideo -> content is TdApi.MessageVideo
      is TdApi.SearchMessagesFilterPhotoAndVideo ->
        content is TdApi.MessagePhoto || content is TdApi.MessageVideo
      is TdApi.SearchMessagesFilterAnimation -> content is TdApi.MessageAnimation
      is TdApi.SearchMessagesFilterAudio -> content is TdApi.MessageAudio
      is TdApi.SearchMessagesFilterDocument -> content is TdApi.MessageDocument
      is TdApi.SearchMessagesFilterVoiceNote -> content is TdApi.MessageVoiceNote
      is TdApi.SearchMessagesFilterVideoNote -> content is TdApi.MessageVideoNote
      is TdApi.SearchMessagesFilterVoiceAndVideoNote ->
        content is TdApi.MessageVoiceNote || content is TdApi.MessageVideoNote
      else -> false
    }

  private fun administrators(chat: TdApi.Chat): TdApi.ChatAdministrators {
    // The creator of a basic group, the account in other groups and channels
    val owner = when (val type = chat.type) {
      is TdApi.ChatTypeBasicGroup -> basicGroupFullInfos[type.basicGroupId]?.creatorUserId
      is TdApi.ChatTypeSupergroup -> tdlib.myUserId()
      else -> null
    }
    val administrators = listOfNotNull(owner).map { TdApi.ChatAdministrator(it, "", true, false) }
    return TdApi.ChatAdministrators(administrators.toTypedArray())
  }

  private fun ok(chatId: Long): TdApi.Ok? = if (isMadeUp(chatId)) TdApi.Ok() else null

  private fun history(
    chatMessages: ConcurrentSkipListMap<Long, TdApi.Message>, query: TdApi.GetChatHistory
  ): TdApi.Messages {
    // Newest first from fromMessageId, or from the last message when it's 0
    val from = if (query.fromMessageId == 0L) Long.MAX_VALUE else query.fromMessageId
    val older = chatMessages.headMap(from, true).descendingMap().values.toList()
    val newer = chatMessages.tailMap(from, false).values.toList()
    val all = newer.takeLast(-minOf(0, query.offset)).reversed() + older
    val result = all.take(query.limit)
    return TdApi.Messages(result.size, result.toTypedArray())
  }

  private fun properties(message: TdApi.Message) = TdApi.MessageProperties().apply {
    canBeCopied = true
    canBeDeletedOnlyForSelf = true
    canBeDeletedForAllUsers = message.isOutgoing
    canBeEdited = message.isOutgoing && message.content is TdApi.MessageText
    canBeForwarded = true
    canBePinned = true
    canBeReplied = true
    canBeRepliedInAnotherChat = true
    canBeSaved = true
    canGetLink = chats[message.chatId]?.type is TdApi.ChatTypeSupergroup
  }

  private val idFields = ConcurrentHashMap<Class<*>, List<Field>>()

  private fun mentionsMadeUp(query: TdApi.Function<*>): Boolean {
    // Fields of ids of the query, e.g. chatId, userId or supergroupId
    val fields = idFields.getOrPut(query.javaClass) {
      query.javaClass.fields.filter {
        !Modifier.isStatic(it.modifiers) && it.name.endsWith("Id") &&
          (it.type == Long::class.javaPrimitiveType || it.type == Int::class.javaPrimitiveType)
      }
    }
    return fields.any { field ->
      when (val value = (field.get(query) as Number).toLong()) {
        0L -> false
        else -> chats.containsKey(value) || users.containsKey(value) ||
          basicGroups.containsKey(value) || supergroups.containsKey(value) ||
          secretChats.containsKey(value.toInt())
      }
    }
  }
}

/**
 * Made-up chats driven by broadcasts of tests, e.g.
 * `adb shell am broadcast -n org.example.tgx/tgx.test.FakeChatReceiver --es action chats`:
 * `chats` makes a chat of each kind with a message, logged as `chat <kind> <id>`, `open` with
 * `--el chat <id>` opens a chat
 */
class FakeChatReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val tdlib = TdlibManager.instance().current()
    val fake = FakeChats.of(tdlib)
    when (intent.getStringExtra("action")) {
      "chats" -> {
        val anna = fake.addUser("Anna", "Lee", 2)
        val ben = fake.addUser("Ben", "Ross", 4)
        val kinds = listOf(
          "private" to fake.addPrivateChat(anna),
          "secret" to fake.addSecretChat(ben),
          "group" to fake.addBasicGroup("Weekend trip", listOf(anna, ben), 1),
          "supergroup" to fake.addSupergroup("Book club", listOf(anna, ben), accentColorId = 3),
          "channel" to fake.addSupergroup("Daily news", listOf(), true, accentColorId = 5),
          "broadcast" to fake.addSupergroup(
            "Town hall", listOf(anna, ben), isBroadcastGroup = true, accentColorId = 6
          )
        )
        for ((kind, chat) in kinds) {
          val text = TdApi.FormattedText("A message in a $kind chat", arrayOf())
          fake.addMessage(chat.id, TdApi.MessageText(text, null, null), outgoing = false)
          Log.i(TAG, "chat $kind ${chat.id}")
        }
      }
      "open" -> {
        val chatId = intent.getLongExtra("chat", 0)
        UI.post {
          val controller = UI.getUiContext()?.navigation()?.currentStackItem
          controller?.let { tdlib.ui().openChat(it, chatId, null) }
        }
      }
    }
  }
}
