package tgx.test

import org.drinkless.tdlib.TdApi
import tgx.td.toBasicGroupId
import tgx.td.toSupergroupId
import tgx.test.ChatKind.BASIC_GROUP
import tgx.test.ChatKind.CHANNEL
import tgx.test.ChatKind.PRIVATE
import tgx.test.ChatKind.SECRET
import tgx.test.ChatKind.SUPERGROUP

/** Where a type of message can happen */
enum class ChatKind { PRIVATE, SECRET, BASIC_GROUP, SUPERGROUP, CHANNEL }

/**
 * A type of message as the server or TDLib sends it: its content, whether it can be incoming and
 * outgoing, and in which kinds of chats
 */
class ContentSample(
  val name: String, // e.g. "MessagePhoto" or "MessagePhoto, caption" for variants
  val content: TdApi.MessageContent,
  val incoming: Boolean,
  val outgoing: Boolean,
  val chats: Set<ChatKind>
)

/**
 * What samples need from the test: the account, made-up users, and real media of the account.
 * Media are objects of messages of the account, so that their files load: TdApi.Photo by "photo",
 * TdApi.Video by "video", TdApi.Animation by "animation", TdApi.Audio by "audio", TdApi.Document by
 * "document", TdApi.VoiceNote by "voiceNote", TdApi.VideoNote by "videoNote" and TdApi.Sticker by
 * "sticker", each missing when the account has none
 */
class ContentKit(
  val myUserId: Long,
  val users: List<TdApi.User>, // at least 3 made-up users
  val chatIds: Map<ChatKind, Long>, // a made-up chat of each kind
  val media: Map<String, TdApi.Object>,
  val now: Int // the date of messages, from which dates in samples count
)

private const val MINUTE = 60
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

private val ANY_CHAT = ChatKind.entries.toSet()
private val NOT_SECRET = ANY_CHAT - SECRET
private val PRIVATE_CHAT = setOf(PRIVATE)
private val GROUPS = setOf(BASIC_GROUP, SUPERGROUP)
private val GROUPS_AND_CHANNELS = setOf(BASIC_GROUP, SUPERGROUP, CHANNEL)

// Chats where users talk to each other, neither channels nor secret chats
private val CONVERSATIONS = setOf(PRIVATE, BASIC_GROUP, SUPERGROUP)

// Chats that can be boosted and have giveaways
private val BOOSTED = setOf(CHANNEL, SUPERGROUP)

private const val TON_ADDRESS = "UQCx7mP2kXq9fLr4tNc8wHs3dJb6yVe1zKg5oRu0iTnAxBc2"

fun contentSamples(kit: ContentKit): List<ContentSample> {
  // Every type of content, except those that only bots or business accounts get:
  // MessagePaymentSuccessfulBot, a payment received by a bot or a business account,
  // MessageWebAppDataReceived, data of a Web App received by its bot, and
  // MessagePassportDataReceived, Telegram Passport data received by a bot
  return Samples(kit).apply {
    addTexts()
    addMedia()
    addPlaces()
    addInteractive()
    addCalls()
    addVideoChats()
    addChatChanges()
    addMembers()
    addPrivateChatEvents()
    addPayments()
    addGifts()
    addGiveaways()
    addSuggestedPosts()
    addBotEvents()
    both("MessageUnsupported", ANY_CHAT) {
      TdApi.MessageUnsupported()
    }
  }.list
}

/**
 * Samples being collected, with the users that take part in them: [peer] is the user of the
 * private chat, [member] and [anotherMember] are other users, e.g. members of groups
 */
private class Samples(val kit: ContentKit) {
  val list = ArrayList<ContentSample>()
  val now = kit.now
  val me = kit.myUserId
  val peer: TdApi.User = kit.users.firstOrNull {
    it.id == kit.chatIds[PRIVATE]
  } ?: kit.users[0]
  private val others = kit.users - peer
  val member: TdApi.User = others[0]
  val anotherMember: TdApi.User = others[1]

  val photo = kit.media["photo"] as TdApi.Photo?
  val video = kit.media["video"] as TdApi.Video?
  val animation = kit.media["animation"] as TdApi.Animation?
  val audio = kit.media["audio"] as TdApi.Audio?
  val document = kit.media["document"] as TdApi.Document?
  val voiceNote = kit.media["voiceNote"] as TdApi.VoiceNote?
  val videoNote = kit.media["videoNote"] as TdApi.VideoNote?
  val sticker = kit.media["sticker"] as TdApi.Sticker?

  fun chatId(kind: ChatKind): Long = kit.chatIds.getValue(kind)

  fun ago(seconds: Int): Int = now - seconds

  fun both(name: String, chats: Set<ChatKind>, content: () -> TdApi.MessageContent) {
    list.add(ContentSample(name, content(), incoming = true, outgoing = true, chats = chats))
  }

  fun incoming(name: String, chats: Set<ChatKind>, content: () -> TdApi.MessageContent) {
    list.add(ContentSample(name, content(), incoming = true, outgoing = false, chats = chats))
  }

  fun outgoing(name: String, chats: Set<ChatKind>, content: () -> TdApi.MessageContent) {
    list.add(ContentSample(name, content(), incoming = false, outgoing = true, chats = chats))
  }

  fun eachDirection(
    name: String,
    chats: Set<ChatKind>,
    content: (isOutgoing: Boolean) -> TdApi.MessageContent
  ) {
    // Contents that differ by direction, e.g. by the sender and the receiver of a gift
    incoming("$name, incoming", chats) {
      content(false)
    }
    outgoing("$name, outgoing", chats) {
      content(true)
    }
  }

  fun chatPhoto(photo: TdApi.Photo): TdApi.ChatPhoto =
    TdApi.ChatPhoto(
      5_120_448_901_337_216L,
      ago(DAY),
      photo.minithumbnail,
      photo.sizes,
      null,
      null,
      null
    )
}

private fun text(
  value: String,
  vararg parts: Pair<String, TdApi.TextEntityType>
): TdApi.FormattedText {
  // Entities of the first occurrences of the parts in the text
  val entities = parts.map { (part, type) ->
    TdApi.TextEntity(value.indexOf(part), part.length, type)
  }
  return TdApi.FormattedText(value, entities.toTypedArray())
}

private fun plain(text: String) = TdApi.RichTextPlain(text)

private fun bullet(text: String) =
  TdApi.PageBlockListItem("•", arrayOf(TdApi.PageBlockParagraph(plain(text))), false, false, 0, "")

private fun Samples.addTexts() {
  both("MessageText", ANY_CHAT) {
    TdApi.MessageText(text("Are we still on for dinner tonight?"), null, null)
  }
  both("MessageText, formatting", ANY_CHAT) {
    val value = "The meeting moved to 3 pm tomorrow, room B-204, agenda: example.com/agenda"
    TdApi.MessageText(
      text(
        value,
        "3 pm tomorrow" to TdApi.TextEntityTypeBold(),
        "B-204" to TdApi.TextEntityTypeCode(),
        "agenda" to TdApi.TextEntityTypeItalic(),
        "example.com/agenda" to TdApi.TextEntityTypeUrl()
      ),
      null,
      null
    )
  }
  both("MessageText, link preview", ANY_CHAT) {
    val url = "https://example.com/hiking/spring-trails"
    val preview = TdApi.LinkPreview(
      url,
      "example.com/hiking/spring-trails",
      "Example Outdoors",
      "Ten spring trails within an hour of the city",
      text("From easy lakeside walks to steep ridge climbs, with maps and parking tips."),
      "Maya Hart",
      TdApi.LinkPreviewTypeArticle(photo),
      photo != null, // hasLargeMedia
      photo != null, // showLargeMedia
      false, // showMediaAboveDescription
      true, // skipConfirmation, as the text shows the link
      false, // showAboveText
      0 // instantViewVersion
    )
    TdApi.MessageText(
      text("These look great for Saturday $url", url to TdApi.TextEntityTypeUrl()),
      preview,
      null
    )
  }
  both("MessageText, emoji", ANY_CHAT) {
    TdApi.MessageText(text("😂"), null, null)
  }
  both("MessageRichMessage", NOT_SECRET) {
    val intro = TdApi.RichTexts(
      arrayOf(plain("Everything we need for the "), TdApi.RichTextBold(plain("weekend trip")))
    )
    val items = arrayOf(bullet("Tent and sleeping bags"), bullet("Headlamps"), bullet("Snacks"))
    val blocks = arrayOf(
      TdApi.PageBlockSectionHeading(plain("Packing list"), 2),
      TdApi.PageBlockParagraph(intro),
      TdApi.PageBlockList(items)
    )
    TdApi.MessageRichMessage(TdApi.RichMessage(blocks, false, true))
  }
}

private fun Samples.addMedia() {
  photo?.let { photo ->
    both("MessagePhoto", ANY_CHAT) {
      TdApi.MessagePhoto(photo, null, text(""), false, false, false)
    }
    both("MessagePhoto, caption", ANY_CHAT) {
      TdApi.MessagePhoto(photo, null, text("Morning view from the balcony"), false, false, false)
    }
    // Spoilers aren't supported in secret chats
    both("MessagePhoto, spoiler", NOT_SECRET) {
      TdApi.MessagePhoto(
        photo,
        null,
        text(""),
        false,
        true, // hasSpoiler
        false
      )
    }
  }
  video?.let { video ->
    both("MessageVideo", ANY_CHAT) {
      TdApi.MessageVideo(video, arrayOf(), arrayOf(), null, 0, text(""), false, false, false)
    }
    both("MessageVideo, caption", ANY_CHAT) {
      TdApi.MessageVideo(
        video,
        arrayOf(),
        arrayOf(),
        null,
        0,
        text("First ride without training wheels!"),
        false,
        false,
        false
      )
    }
  }
  animation?.let { animation ->
    both("MessageAnimation", ANY_CHAT) {
      TdApi.MessageAnimation(animation, text(""), false, false, false)
    }
    both("MessageAnimation, caption", ANY_CHAT) {
      TdApi.MessageAnimation(animation, text("Me on Monday mornings"), false, false, false)
    }
  }
  audio?.let { audio ->
    both("MessageAudio", ANY_CHAT) {
      TdApi.MessageAudio(audio, text(""))
    }
    both("MessageAudio, caption", ANY_CHAT) {
      TdApi.MessageAudio(audio, text("This one has been stuck in my head all week"))
    }
  }
  document?.let { document ->
    both("MessageDocument", ANY_CHAT) {
      TdApi.MessageDocument(document, text(""))
    }
    both("MessageDocument, caption", ANY_CHAT) {
      TdApi.MessageDocument(document, text("Signed copy of the lease"))
    }
  }
  voiceNote?.let { voiceNote ->
    both("MessageVoiceNote", ANY_CHAT) {
      TdApi.MessageVoiceNote(
        voiceNote,
        text(""),
        true // isListened
      )
    }
  }
  videoNote?.let { videoNote ->
    both("MessageVideoNote", ANY_CHAT) {
      TdApi.MessageVideoNote(
        videoNote,
        true, // isViewed
        false // isSecret
      )
    }
  }
  sticker?.let { sticker ->
    both("MessageSticker", ANY_CHAT) {
      TdApi.MessageSticker(
        sticker,
        false // isPremium
      )
    }
  }
  addPaidMedia()
  // Self-destructing media of private chats after it was opened, while secret chats delete it
  both("MessageExpiredPhoto", PRIVATE_CHAT) {
    TdApi.MessageExpiredPhoto()
  }
  both("MessageExpiredVideo", PRIVATE_CHAT) {
    TdApi.MessageExpiredVideo()
  }
  both("MessageExpiredVoiceNote", PRIVATE_CHAT) {
    TdApi.MessageExpiredVoiceNote()
  }
  both("MessageExpiredVideoNote", PRIVATE_CHAT) {
    TdApi.MessageExpiredVideoNote()
  }
}

private fun Samples.addPaidMedia() {
  // Paid media is posted in channels and sent to users by bots. Users who haven't paid see its
  // preview, while its sender sees the media
  val caption = "Behind the scenes of our photo shoot"
  val size = photo?.sizes?.lastOrNull()
  incoming("MessagePaidMedia, locked", setOf(PRIVATE, CHANNEL)) {
    val preview = TdApi.PaidMediaPreview(
      size?.width ?: 1280,
      size?.height ?: 853,
      0, // duration
      photo?.minithumbnail
    )
    TdApi.MessagePaidMedia(50, arrayOf(preview), text(caption), false)
  }
  photo?.let { photo ->
    both("MessagePaidMedia", setOf(CHANNEL)) {
      TdApi.MessagePaidMedia(50, arrayOf(TdApi.PaidMediaPhoto(photo, null)), text(caption), false)
    }
  }
}

private fun Samples.addPlaces() {
  both("MessageLocation", ANY_CHAT) {
    TdApi.MessageLocation(TdApi.Location(52.5163, 13.3777, 0.0))
  }
  // Secret chats get only static locations
  both("MessageLiveLocation", NOT_SECRET) {
    val location = TdApi.LiveLocation(
      TdApi.Location(52.5208, 13.4094, 12.0),
      HOUR, // livePeriod
      90, // heading
      0 // proximityAlertRadius
    )
    TdApi.MessageLiveLocation(location, 40 * MINUTE)
  }
  both("MessageVenue", ANY_CHAT) {
    val venue = TdApi.Venue(
      TdApi.Location(52.5301, 13.4012, 0.0),
      "Corner Café",
      "12 Linden Lane",
      "foursquare",
      "5a1f3c9e8b2d4e0012ab34cd",
      "food/coffeeshop"
    )
    TdApi.MessageVenue(venue)
  }
  both("MessageContact", ANY_CHAT) {
    val contact = TdApi.Contact("+12025550147", member.firstName, member.lastName, "", member.id)
    TdApi.MessageContact(contact)
  }
}

private fun Samples.addInteractive() {
  sticker?.let { sticker ->
    // A sticker of the account stands for the animated emoji, which TDLib takes from its own set
    both("MessageAnimatedEmoji", ANY_CHAT) {
      val emoji = TdApi.AnimatedEmoji(sticker, sticker.width, sticker.height, 0, null, true)
      TdApi.MessageAnimatedEmoji(emoji, sticker.emoji.ifEmpty { "👍" })
    }
  }
  // Dice aren't sent to secret chats. Their stickers come later with updates of TDLib, and the app
  // finds them by emoji and value
  both("MessageDice", NOT_SECRET) {
    TdApi.MessageDice(null, null, "🎲", 4, 0)
  }
  both("MessageStakeDice", CONVERSATIONS) {
    TdApi.MessageStakeDice(
      null,
      null,
      5, // value
      1_000_000_000L, // stakeGramAmount
      2_000_000_000L // prizeGramAmount
    )
  }
  photo?.let { photo ->
    // Bots send games, and users send them by inline bots, but not to channels or secret chats
    both("MessageGame", CONVERSATIONS) {
      val game = TdApi.Game(
        6_311_402_715_830_042_625L,
        "tower_run",
        "Tower Run",
        text(""),
        "Stack the blocks as high as you can",
        photo,
        null
      )
      TdApi.MessageGame(game)
    }
  }
  both("MessageGameScore", CONVERSATIONS) {
    TdApi.MessageGameScore(0, 6_311_402_715_830_042_625L, 1250)
  }
  addPolls()
  // Stories can't be forwarded to secret chats
  both("MessageStory", NOT_SECRET) {
    TdApi.MessageStory(chatId(CHANNEL), 17, false)
  }
  eachDirection("MessageStory, mention", PRIVATE_CHAT) { isOutgoing ->
    TdApi.MessageStory(if (isOutgoing) me else peer.id, 3, true)
  }
  // Checklists can't be sent to channels and secret chats
  both("MessageChecklist", CONVERSATIONS) {
    val tasks = arrayOf(
      TdApi.ChecklistTask(1, text("Book the hotel"), TdApi.MessageSenderUser(peer.id), ago(HOUR)),
      TdApi.ChecklistTask(2, text("Rent a car"), null, 0),
      TdApi.ChecklistTask(3, text("Buy sunscreen"), null, 0)
    )
    val checklist = TdApi.Checklist(
      text("Before the trip"),
      tasks,
      true, // othersCanAddTasks
      true, // canAddTasks
      true, // othersCanMarkTasksAsDone
      true // canMarkTasksAsDone
    )
    TdApi.MessageChecklist(checklist)
  }
  both("MessageChecklistTasksDone", CONVERSATIONS) {
    TdApi.MessageChecklistTasksDone(0, intArrayOf(2), intArrayOf())
  }
  both("MessageChecklistTasksAdded", CONVERSATIONS) {
    val task = TdApi.ChecklistTask(4, text("Pack chargers"), null, 0)
    TdApi.MessageChecklistTasksAdded(0, arrayOf(task))
  }
  // Bots send invoices, and users send them by inline bots
  both("MessageInvoice", NOT_SECRET) {
    val description = text("Margherita, 40 cm, delivered in about 30 minutes")
    TdApi.MessageInvoice(
      TdApi.ProductInfo("Large pizza", description, photo),
      "USD",
      1899,
      "pizza-large", // startParameter
      false, // isTest
      true, // needShippingAddress
      0, // receiptMessageId
      null,
      null
    )
  }
}

private fun Samples.addPolls() {
  // Users send polls to private chats only with bots, and polls can't be sent to secret chats
  both("MessagePoll", NOT_SECRET) {
    val options = arrayOf(
      pollOption("0", "Italian", 6, 50, isChosen = true),
      pollOption("1", "Sushi", 4, 33),
      pollOption("2", "Tacos", 2, 17)
    )
    TdApi.MessagePoll(
      poll(5_844_120_336_918_016_001L, "Where should we have dinner on Friday?", options, 12),
      text(""),
      null,
      false // canAddOption
    )
  }
  both("MessagePoll, quiz", NOT_SECRET) {
    val options = arrayOf(
      pollOption("0", "Jupiter", 0, 0),
      pollOption("1", "Saturn", 0, 0),
      pollOption("2", "Neptune", 0, 0)
    )
    // Correct options and the explanation are unknown until the quiz is answered
    val quiz = TdApi.PollTypeQuiz(intArrayOf(), text(""), null)
    TdApi.MessagePoll(
      poll(5_844_120_336_918_016_002L, "Which planet has the most moons?", options, 7, quiz),
      text(""),
      null,
      false // canAddOption
    )
  }
  both("MessagePollOptionAdded", GROUPS) {
    TdApi.MessagePollOptionAdded(0, "3", text("Thai"))
  }
  both("MessagePollOptionDeleted", GROUPS) {
    TdApi.MessagePollOptionDeleted(0, "2", text("Tacos"))
  }
}

private fun poll(
  id: Long,
  question: String,
  options: Array<TdApi.PollOption>,
  totalVoterCount: Int,
  type: TdApi.PollType = TdApi.PollTypeRegular()
) =
  TdApi.Poll(
    id,
    text(question),
    options,
    totalVoterCount,
    arrayOf(), // recentVoterIds
    false, // canGetVoters
    options.any { it.isChosen }, // canSeeResults
    true, // isAnonymous
    false, // allowsMultipleAnswers
    false, // allowsRevoting
    false, // membersOnly
    arrayOf(), // countryCodes
    intArrayOf(), // optionOrder
    type,
    0, // openPeriod
    0, // closeDate
    false, // isClosed
    null
  )

private fun pollOption(
  id: String,
  value: String,
  voterCount: Int,
  votePercentage: Int,
  isChosen: Boolean = false
) =
  TdApi.PollOption(
    id,
    text(value),
    null,
    voterCount,
    votePercentage,
    arrayOf(), // recentVoterIds
    isChosen,
    false, // isBeingChosen
    null,
    0 // additionDate
  )

private fun Samples.addCalls() {
  both("MessageCall", PRIVATE_CHAT) {
    TdApi.MessageCall(
      7_240_118_093_512_884_101L, // uniqueId
      false, // isVideo
      TdApi.CallDiscardReasonHungUp(),
      4 * MINUTE + 23
    )
  }
  both("MessageCall, video", PRIVATE_CHAT) {
    TdApi.MessageCall(
      7_240_118_093_512_884_102L,
      true, // isVideo
      TdApi.CallDiscardReasonHungUp(),
      25 * MINUTE + 8
    )
  }
  both("MessageCall, missed", PRIVATE_CHAT) {
    TdApi.MessageCall(7_240_118_093_512_884_103L, false, TdApi.CallDiscardReasonMissed(), 0)
  }
  both("MessageCall, declined", PRIVATE_CHAT) {
    TdApi.MessageCall(7_240_118_093_512_884_104L, false, TdApi.CallDiscardReasonDeclined(), 0)
  }
  // Incoming group calls that aren't active, missed or ended would show the screen of a call
  both("MessageGroupCall", PRIVATE_CHAT) {
    TdApi.MessageGroupCall(
      7_240_118_093_512_884_105L, // uniqueId
      false, // isActive
      false, // wasMissed
      false, // isVideo
      12 * MINUTE + 40,
      arrayOf(TdApi.MessageSenderUser(member.id))
    )
  }
  both("MessageGroupCall, missed", PRIVATE_CHAT) {
    TdApi.MessageGroupCall(
      7_240_118_093_512_884_106L,
      false, // isActive
      true, // wasMissed
      true, // isVideo
      0,
      arrayOf(TdApi.MessageSenderUser(member.id))
    )
  }
}

private fun Samples.addVideoChats() {
  val groupCallId = 48_211
  both("MessageVideoChatScheduled", GROUPS_AND_CHANNELS) {
    TdApi.MessageVideoChatScheduled(groupCallId, now + DAY + 2 * HOUR)
  }
  both("MessageVideoChatStarted", GROUPS_AND_CHANNELS) {
    TdApi.MessageVideoChatStarted(groupCallId)
  }
  both("MessageVideoChatEnded", GROUPS_AND_CHANNELS) {
    TdApi.MessageVideoChatEnded(45 * MINUTE + 12)
  }
  both("MessageInviteVideoChatParticipants", GROUPS_AND_CHANNELS) {
    TdApi.MessageInviteVideoChatParticipants(groupCallId, longArrayOf(member.id, anotherMember.id))
  }
}

private fun Samples.addChatChanges() {
  both("MessageBasicGroupChatCreate", setOf(BASIC_GROUP)) {
    TdApi.MessageBasicGroupChatCreate("Weekend trip", kit.users.map { it.id }.toLongArray())
  }
  both("MessageSupergroupChatCreate", setOf(SUPERGROUP, CHANNEL)) {
    TdApi.MessageSupergroupChatCreate("Neighborhood news")
  }
  both("MessageChatChangeTitle", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatChangeTitle("Trip to Lisbon")
  }
  photo?.let { photo ->
    both("MessageChatChangePhoto", GROUPS_AND_CHANNELS) {
      TdApi.MessageChatChangePhoto(chatPhoto(photo))
    }
  }
  both("MessageChatDeletePhoto", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatDeletePhoto()
  }
  // Upgrades refer to the made-up chat of the other kind
  both("MessageChatUpgradeTo", setOf(BASIC_GROUP)) {
    TdApi.MessageChatUpgradeTo(toSupergroupId(chatId(SUPERGROUP)))
  }
  both("MessageChatUpgradeFrom", setOf(SUPERGROUP)) {
    TdApi.MessageChatUpgradeFrom("Weekend trip", toBasicGroupId(chatId(BASIC_GROUP)))
  }
  both("MessagePinMessage", NOT_SECRET) {
    TdApi.MessagePinMessage(0)
  }
  both("MessageChatSetMessageAutoDeleteTime", ANY_CHAT) {
    TdApi.MessageChatSetMessageAutoDeleteTime(DAY, 0)
  }
  both("MessageChatSetMessageAutoDeleteTime, disabled", ANY_CHAT) {
    TdApi.MessageChatSetMessageAutoDeleteTime(0, 0)
  }
  both("MessageChatBoost", setOf(SUPERGROUP)) {
    TdApi.MessageChatBoost(1)
  }
  both("MessagePaidMessagePriceChanged", setOf(SUPERGROUP)) {
    TdApi.MessagePaidMessagePriceChanged(10)
  }
  both("MessageDirectMessagePriceChanged", setOf(CHANNEL)) {
    TdApi.MessageDirectMessagePriceChanged(true, 25)
  }
  both("MessageForumTopicCreated", setOf(SUPERGROUP)) {
    TdApi.MessageForumTopicCreated("Book suggestions", false, TdApi.ForumTopicIcon(0x6FB9F0, 0))
  }
  both("MessageForumTopicEdited", setOf(SUPERGROUP)) {
    TdApi.MessageForumTopicEdited("Reading schedule", false, 0)
  }
  both("MessageForumTopicIsClosedToggled", setOf(SUPERGROUP)) {
    TdApi.MessageForumTopicIsClosedToggled(true)
  }
  both("MessageForumTopicIsHiddenToggled", setOf(SUPERGROUP)) {
    TdApi.MessageForumTopicIsHiddenToggled(true)
  }
  // Communities consist of supergroups, channels and chats with bots
  val communityId = 7_351_204L
  both("MessageChatAddedToCommunity", BOOSTED) {
    TdApi.MessageChatAddedToCommunity(communityId)
  }
  both("MessageChatRemovedFromCommunity", BOOSTED) {
    TdApi.MessageChatRemovedFromCommunity()
  }
  both("MessageChatJoinFromCommunity", BOOSTED) {
    TdApi.MessageChatJoinFromCommunity(communityId)
  }
  // Actions of the server, e.g. announcements, which no user sends
  incoming("MessageCustomServiceAction", NOT_SECRET) {
    TdApi.MessageCustomServiceAction("Welcome to the community!")
  }
}

private fun Samples.addMembers() {
  both("MessageChatAddMembers", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatAddMembers(longArrayOf(member.id))
  }
  both("MessageChatAddMembers, several", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatAddMembers(longArrayOf(member.id, anotherMember.id))
  }
  // Another member joined, or the account joined, e.g. a channel
  both("MessageChatJoinByLink", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatJoinByLink()
  }
  both("MessageChatJoinByRequest", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatJoinByRequest()
  }
  both("MessageChatDeleteMember", GROUPS) {
    TdApi.MessageChatDeleteMember(anotherMember.id)
  }
  // Ownership is transferred in basic groups, supergroups and channels
  both("MessageChatOwnerChanged", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatOwnerChanged(member.id)
  }
  // The owner who left doesn't see the chat anymore
  incoming("MessageChatOwnerLeft", GROUPS_AND_CHANNELS) {
    TdApi.MessageChatOwnerLeft(member.id)
  }
}

private fun Samples.addPrivateChatEvents() {
  incoming("MessageContactRegistered", PRIVATE_CHAT) {
    TdApi.MessageContactRegistered()
  }
  // Screenshots of secret chats, and of self-destructing media of private chats
  both("MessageScreenshotTaken", setOf(PRIVATE, SECRET)) {
    TdApi.MessageScreenshotTaken()
  }
  both("MessageChatSetBackground", PRIVATE_CHAT) {
    val background = TdApi.Background(
      4_816_502_221_093_376_001L,
      false, // isDefault
      false, // isDark
      "8bd5e8-3a6ea5?rotation=45",
      null,
      TdApi.BackgroundTypeFill(TdApi.BackgroundFillGradient(0x8BD5E8, 0x3A6EA5, 45))
    )
    TdApi.MessageChatSetBackground(
      0, // oldBackgroundMessageId
      TdApi.ChatBackground(background, 0),
      false // onlyForSelf
    )
  }
  both("MessageChatSetTheme", PRIVATE_CHAT) {
    TdApi.MessageChatSetTheme(TdApi.ChatThemeEmoji("🌷"))
  }
  both("MessageChatSetTheme, reset", PRIVATE_CHAT) {
    TdApi.MessageChatSetTheme(null)
  }
  // Users of private chats agree to turn off protection of content
  both("MessageChatHasProtectedContentToggled", PRIVATE_CHAT) {
    TdApi.MessageChatHasProtectedContentToggled(
      0, // requestMessageId
      false, // oldHasProtectedContent
      true // newHasProtectedContent
    )
  }
  both("MessageChatHasProtectedContentDisableRequested", PRIVATE_CHAT) {
    TdApi.MessageChatHasProtectedContentDisableRequested(false)
  }
  photo?.let { photo ->
    both("MessageSuggestProfilePhoto", PRIVATE_CHAT) {
      TdApi.MessageSuggestProfilePhoto(chatPhoto(photo))
    }
  }
  both("MessageSuggestBirthdate", PRIVATE_CHAT) {
    TdApi.MessageSuggestBirthdate(TdApi.Birthdate(14, 3, 1994))
  }
  // The traveler sends the alert
  eachDirection("MessageProximityAlertTriggered", CONVERSATIONS) { isOutgoing ->
    val traveler = TdApi.MessageSenderUser(if (isOutgoing) me else peer.id)
    val watcher = TdApi.MessageSenderUser(if (isOutgoing) peer.id else me)
    TdApi.MessageProximityAlertTriggered(traveler, watcher, 150)
  }
}

private fun Samples.addPayments() {
  // Payments of the account for invoices of bots
  outgoing("MessagePaymentSuccessful", PRIVATE_CHAT) {
    TdApi.MessagePaymentSuccessful(
      chatId(PRIVATE), // invoiceChatId
      0, // invoiceMessageId
      "USD",
      1899,
      0, // subscriptionUntilDate
      false, // isRecurring
      false, // isFirstRecurring
      "Large pizza"
    )
  }
  // Refunds of bots for payments of the account
  incoming("MessagePaymentRefunded", PRIVATE_CHAT) {
    TdApi.MessagePaymentRefunded(
      TdApi.MessageSenderUser(peer.id),
      "XTR",
      250,
      byteArrayOf(), // invoicePayload, for bots only
      "stxQ7nM2kP9vR4tL8wX1cB6",
      ""
    )
  }
  both("MessagePaidMessagesRefunded", PRIVATE_CHAT) {
    TdApi.MessagePaidMessagesRefunded(3, 30)
  }
  eachDirection("MessageTonWalletTransfer", PRIVATE_CHAT) { isOutgoing ->
    val amount = 2_500_000_000L
    TdApi.MessageTonWalletTransfer(
      if (isOutgoing) me else peer.id, // senderUserId
      "3f9a1c7e5b2d8046e1a9c3b7d5f20864a1c3e5b7d9f10234c6e8a0b2d4f61357",
      TON_ADDRESS,
      if (isOutgoing) -amount else amount,
      "For the concert tickets",
      false // isCommentEncrypted
    )
  }
  // Requests of dApps come to the account
  incoming("MessageTonConnectRequest", PRIVATE_CHAT) {
    TdApi.MessageTonConnectRequest(
      8_420_117_365_904_112_731L, // sessionId
      TdApi.TonConnectRequestStatePending(now + 5 * MINUTE),
      "Ticket Market",
      "",
      ""
    )
  }
}

private fun Samples.addGifts() {
  // The gifter is 0 in outgoing messages, and the receiver is 0 in incoming ones
  eachDirection("MessageGiftedPremium", PRIVATE_CHAT) { isOutgoing ->
    TdApi.MessageGiftedPremium(
      if (isOutgoing) 0L else peer.id,
      if (isOutgoing) peer.id else 0L,
      text("Enjoy the extra features!"),
      "USD",
      1199,
      "", // cryptocurrency
      0,
      3, // monthCount
      90, // dayCount
      null
    )
  }
  eachDirection("MessageGiftedStars", PRIVATE_CHAT) { isOutgoing ->
    TdApi.MessageGiftedStars(
      if (isOutgoing) 0L else peer.id,
      if (isOutgoing) peer.id else 0L,
      "USD",
      999,
      "", // cryptocurrency
      0,
      500, // starCount
      if (isOutgoing) "" else "stx4Gq7Lm2Pz9RkW3hT", // transactionId, for the receiver only
      null
    )
  }
  eachDirection("MessageGiftedGrams", PRIVATE_CHAT) { isOutgoing ->
    TdApi.MessageGiftedGrams(
      if (isOutgoing) 0L else peer.id,
      if (isOutgoing) peer.id else 0L,
      5_000_000_000L,
      if (isOutgoing) "" else "gtx8Hn3Vb6Qm1Zc5Yd2", // transactionId, for the receiver only
      null
    )
  }
  eachDirection("MessagePremiumGiftCode", PRIVATE_CHAT) { isOutgoing ->
    TdApi.MessagePremiumGiftCode(
      TdApi.MessageSenderUser(if (isOutgoing) me else peer.id),
      text("For all the help with the move"),
      false, // isFromGiveaway
      false, // isUnclaimed
      "USD",
      1199,
      "", // cryptocurrency
      0,
      3, // monthCount
      90, // dayCount
      null,
      "aB3dE5fG7hJ9kL1m"
    )
  }
  sticker?.let { addGiftsWithSticker(it) }
}

private fun Samples.addGiftsWithSticker(sticker: TdApi.Sticker) {
  eachDirection("MessageGift", PRIVATE_CHAT) { isOutgoing ->
    val senderId = TdApi.MessageSenderUser(if (isOutgoing) me else peer.id)
    val receiverId = TdApi.MessageSenderUser(if (isOutgoing) peer.id else me)
    giftMessage(sticker, senderId, receiverId, isReceiver = !isOutgoing)
  }
  // Administrators of channels learn about gifts to their channels
  incoming("MessageGift, channel", setOf(CHANNEL)) {
    val senderId = TdApi.MessageSenderUser(peer.id)
    giftMessage(sticker, senderId, TdApi.MessageSenderChat(chatId(CHANNEL)), isReceiver = true)
  }
  eachDirection("MessageUpgradedGift", PRIVATE_CHAT) { isOutgoing ->
    val senderId = if (isOutgoing) me else peer.id
    val receiverId = if (isOutgoing) peer.id else me
    TdApi.MessageUpgradedGift(
      upgradedGift(sticker, receiverId),
      TdApi.MessageSenderUser(senderId),
      TdApi.MessageSenderUser(receiverId),
      TdApi.UpgradedGiftOriginTransfer(),
      if (isOutgoing) "" else "1045", // receivedGiftId, for the receiver only
      text(""),
      false, // isPrivate
      false, // isSaved
      !isOutgoing, // canBeTransferred
      false, // wasTransferred
      if (isOutgoing) 0L else 25L, // transferStarCount
      0, // dropOriginalDetailsStarCount
      0, // nextTransferDate
      0, // nextResaleDate
      0, // exportDate
      0 // craftDate
    )
  }
  eachDirection("MessageRefundedUpgradedGift", PRIVATE_CHAT) { isOutgoing ->
    val senderId = if (isOutgoing) me else peer.id
    val receiverId = if (isOutgoing) peer.id else me
    TdApi.MessageRefundedUpgradedGift(
      gift(sticker),
      TdApi.MessageSenderUser(senderId),
      TdApi.MessageSenderUser(receiverId),
      TdApi.UpgradedGiftOriginUpgrade(0)
    )
  }
  // Offers to buy a gift of the receiver of the message
  eachDirection("MessageUpgradedGiftPurchaseOffer", PRIVATE_CHAT) { isOutgoing ->
    TdApi.MessageUpgradedGiftPurchaseOffer(
      upgradedGift(sticker, if (isOutgoing) peer.id else me),
      TdApi.GiftPurchaseOfferStatePending(),
      TdApi.GiftResalePriceStar(2500),
      now + DAY // expirationDate
    )
  }
  // Rejections by the owner of the gift, who sends the message
  eachDirection("MessageUpgradedGiftPurchaseOfferRejected", PRIVATE_CHAT) { isOutgoing ->
    TdApi.MessageUpgradedGiftPurchaseOfferRejected(
      upgradedGift(sticker, if (isOutgoing) me else peer.id),
      TdApi.GiftResalePriceStar(2500),
      0, // offerMessageId
      false // wasExpired
    )
  }
}

private fun gift(sticker: TdApi.Sticker) =
  TdApi.Gift(
    5_170_145_012_310_081_615L,
    0, // publisherChatId
    sticker,
    50, // starCount
    40, // defaultSellStarCount
    25, // upgradeStarCount
    48, // upgradeVariantCount
    false, // hasColors
    false, // isForBirthday
    false, // isPremium
    null,
    0, // nextSendDate
    null,
    null,
    null,
    0, // firstSendDate
    0 // lastSendDate
  )

private fun giftMessage(
  sticker: TdApi.Sticker,
  senderId: TdApi.MessageSender,
  receiverId: TdApi.MessageSender,
  isReceiver: Boolean
) =
  TdApi.MessageGift(
    gift(sticker),
    senderId,
    receiverId,
    if (isReceiver) "1043" else "", // receivedGiftId, for the receiver only
    text("Happy birthday! 🎂"),
    0, // uniqueGiftNumber
    40, // sellStarCount
    0, // prepaidUpgradeStarCount
    false, // isUpgradeSeparate
    false, // isFromAuction
    false, // isPrivate
    false, // isSaved
    false, // isPrepaidUpgrade
    isReceiver, // canBeUpgraded
    false, // canBeDeleted
    false, // wasConverted
    false, // wasUpgraded
    false, // wasRefunded
    "", // upgradedReceivedGiftId
    "" // prepaidUpgradeHash
  )

private fun upgradedGift(sticker: TdApi.Sticker, ownerId: Long) =
  TdApi.UpgradedGift(
    6_022_871_455_190_337_024L,
    5_170_145_012_310_081_615L, // regularGiftId
    0, // publisherChatId
    "Lucky Cat",
    "LuckyCat-1024",
    1024, // number
    15_320, // totalUpgradedCount
    50_000, // maxUpgradedCount
    false, // isBurned
    false, // isCrafted
    false, // isPremium
    true, // isThemeAvailable
    0, // usedThemeChatId
    null,
    TdApi.MessageSenderUser(ownerId),
    "", // ownerAddress
    "", // ownerName
    "", // giftAddress
    false, // canBeTonWalletCardBackground
    TdApi.UpgradedGiftModel(
      "Golden Paw",
      sticker,
      TdApi.UpgradedGiftAttributeRarityPerMille(15),
      false // isCrafted
    ),
    TdApi.UpgradedGiftSymbol("Clover", sticker, TdApi.UpgradedGiftAttributeRarityPerMille(8)),
    TdApi.UpgradedGiftBackdrop(
      7,
      "Midnight Blue",
      TdApi.UpgradedGiftBackdropColors(0x4B6CB7, 0x182848, 0x9DB4E8, 0xFFFFFF),
      TdApi.UpgradedGiftAttributeRarityPerMille(12)
    ),
    null,
    null,
    null,
    true, // canSendPurchaseOffer
    0, // craftProbabilityPerMille
    "USD",
    1250, // valueAmount
    1250 // valueUsdAmount
  )

private fun Samples.addGiveaways() {
  both("MessageGiveawayCreated", BOOSTED) {
    TdApi.MessageGiveawayCreated(0)
  }
  both("MessageGiveawayCreated, stars", BOOSTED) {
    TdApi.MessageGiveawayCreated(5000)
  }
  both("MessageGiveawayCompleted", BOOSTED) {
    TdApi.MessageGiveawayCompleted(0, 10, false, 0)
  }
  // Giveaways and their prizes refer to the boosted chat, where winners get their prizes too
  for (kind in BOOSTED) {
    val chatId = chatId(kind)
    val suffix = kind.name.lowercase()
    both("MessageGiveaway, $suffix", setOf(kind)) {
      val parameters = TdApi.GiveawayParameters(
        chatId,
        longArrayOf(), // additionalChatIds
        now + 3 * DAY, // winnersSelectionDate
        false, // onlyNewMembers
        true, // hasPublicWinners
        arrayOf(), // countryCodes
        "" // prizeDescription
      )
      TdApi.MessageGiveaway(parameters, 10, TdApi.GiveawayPrizePremium(3), null)
    }
    both("MessageGiveawayWinners, $suffix", setOf(kind)) {
      TdApi.MessageGiveawayWinners(
        chatId,
        0, // giveawayMessageId
        0, // additionalChatCount
        ago(HOUR), // actualWinnersSelectionDate
        false, // onlyNewMembers
        false, // wasRefunded
        TdApi.GiveawayPrizePremium(3),
        "", // prizeDescription
        3, // winnerCount
        kit.users.map { it.id }.toLongArray(),
        0 // unclaimedPrizeCount
      )
    }
    incoming("MessagePremiumGiftCode, giveaway, $suffix", setOf(kind)) {
      TdApi.MessagePremiumGiftCode(
        TdApi.MessageSenderChat(chatId),
        text(""),
        true, // isFromGiveaway
        false, // isUnclaimed
        "",
        0,
        "",
        0,
        3, // monthCount
        90, // dayCount
        null,
        "Zx8Yw7Vu6Ts5Rq4P"
      )
    }
    incoming("MessageGiveawayPrizeStars, $suffix", setOf(kind)) {
      TdApi.MessageGiveawayPrizeStars(
        1000,
        "stx9Jk2Lm5Np8Qr1St",
        chatId,
        0, // giveawayMessageId
        false, // isUnclaimed
        null
      )
    }
  }
}

private fun Samples.addSuggestedPosts() {
  // Direct messages chats of channels, where users suggest posts, are supergroups
  val chats = setOf(SUPERGROUP)
  both("MessageSuggestedPostApproved", chats) {
    TdApi.MessageSuggestedPostApproved(0, TdApi.SuggestedPostPriceStar(500), now + 2 * HOUR)
  }
  both("MessageSuggestedPostApprovalFailed", chats) {
    TdApi.MessageSuggestedPostApprovalFailed(0, TdApi.SuggestedPostPriceStar(500))
  }
  both("MessageSuggestedPostDeclined", chats) {
    TdApi.MessageSuggestedPostDeclined(0, "We don't post ads on weekends")
  }
  both("MessageSuggestedPostPaid", chats) {
    TdApi.MessageSuggestedPostPaid(0, TdApi.StarAmount(425, 0), 0)
  }
  both("MessageSuggestedPostRefunded", chats) {
    TdApi.MessageSuggestedPostRefunded(0, TdApi.SuggestedPostRefundReasonPostDeleted())
  }
}

private fun Samples.addBotEvents() {
  // The account does these in chats with bots, which get them as messages of the user
  outgoing("MessageUsersShared", PRIVATE_CHAT) {
    TdApi.MessageUsersShared(arrayOf(TdApi.SharedUser(member.id, "", "", "", null)), 1)
  }
  outgoing("MessageChatShared", PRIVATE_CHAT) {
    TdApi.MessageChatShared(TdApi.SharedChat(chatId(SUPERGROUP), "", "", null), 2)
  }
  outgoing("MessageBotWriteAccessAllowed", PRIVATE_CHAT) {
    val reason = TdApi.BotWriteAccessAllowReasonConnectedWebsite("example.com")
    TdApi.MessageBotWriteAccessAllowed(reason)
  }
  outgoing("MessageBotWriteAccessAllowed, request", PRIVATE_CHAT) {
    TdApi.MessageBotWriteAccessAllowed(TdApi.BotWriteAccessAllowReasonAcceptedRequest())
  }
  outgoing("MessageWebAppDataSent", PRIVATE_CHAT) {
    TdApi.MessageWebAppDataSent("Choose a size")
  }
  outgoing("MessagePassportDataSent", PRIVATE_CHAT) {
    val types = arrayOf(
      TdApi.PassportElementTypePersonalDetails(),
      TdApi.PassportElementTypePassport(),
      TdApi.PassportElementTypeEmailAddress()
    )
    TdApi.MessagePassportDataSent(types)
  }
  // A made-up user stands for the new bot, the manager bot is the bot of the chat
  outgoing("MessageManagedBotCreated", PRIVATE_CHAT) {
    TdApi.MessageManagedBotCreated(member.id, peer.id)
  }
}
