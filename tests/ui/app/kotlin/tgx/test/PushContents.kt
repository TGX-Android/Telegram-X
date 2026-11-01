package tgx.test

import org.drinkless.tdlib.TdApi
import tgx.test.ChatKind.BASIC_GROUP
import tgx.test.ChatKind.CHANNEL
import tgx.test.ChatKind.PRIVATE
import tgx.test.ChatKind.SECRET
import tgx.test.ChatKind.SUPERGROUP

/**
 * A type of notification as it comes by Firebase before the whole message: its content, the kinds
 * of chats where it comes, and the name of the sample of [contentSamples] whose message replaces
 * it when it loads, null when nothing replaces it, e.g. hidden or pinned contents
 */
class PushSample(
  val name: String,
  val content: TdApi.PushMessageContent,
  val chats: Set<ChatKind>,
  val message: String?
)

private val ANY_CHAT = ChatKind.entries.toSet()
private val NOT_SECRET = ANY_CHAT - SECRET
private val PRIVATE_CHAT = setOf(PRIVATE)
private val GROUPS = setOf(BASIC_GROUP, SUPERGROUP)
private val GROUPS_AND_CHANNELS = setOf(BASIC_GROUP, SUPERGROUP, CHANNEL)
private val CONVERSATIONS = setOf(PRIVATE, BASIC_GROUP, SUPERGROUP)

fun pushSamples(kit: ContentKit): List<PushSample> {
  // Every type of PushMessageContent, with the same texts and media as the samples of messages
  // that replace them, and each one that can be about a pinned message also pinned
  val list = ArrayList<PushSample>()
  fun add(name: String, chats: Set<ChatKind>, message: String?, content: TdApi.PushMessageContent) {
    list.add(PushSample(name, content, chats, message))
  }
  fun pinnable(
    name: String, chats: Set<ChatKind>, message: String?,
    content: (isPinned: Boolean) -> TdApi.PushMessageContent
  ) {
    add(name, chats, message, content(false))
    add("$name, pinned", chats - SECRET, null, content(true))
  }
  val member = kit.users[1]
  val memberName = "${member.firstName} ${member.lastName}"

  add("PushMessageContentHidden", ANY_CHAT, null, TdApi.PushMessageContentHidden(false))
  pinnable("PushMessageContentText", ANY_CHAT, "MessageText") {
    TdApi.PushMessageContentText("Are we still on for dinner tonight?", it)
  }
  (kit.media["photo"] as TdApi.Photo?)?.let { photo ->
    pinnable("PushMessageContentPhoto", NOT_SECRET, "MessagePhoto, caption") {
      TdApi.PushMessageContentPhoto(photo, "Morning view from the balcony", false, it)
    }
    add(
      "PushMessageContentPhoto, secret", setOf(SECRET), null,
      TdApi.PushMessageContentPhoto(photo, "", true, false)
    )
  }
  (kit.media["video"] as TdApi.Video?)?.let { video ->
    pinnable("PushMessageContentVideo", NOT_SECRET, "MessageVideo, caption") {
      TdApi.PushMessageContentVideo(video, "First ride without training wheels!", false, it)
    }
    add(
      "PushMessageContentVideo, secret", setOf(SECRET), null,
      TdApi.PushMessageContentVideo(video, "", true, false)
    )
  }
  (kit.media["animation"] as TdApi.Animation?)?.let { animation ->
    pinnable("PushMessageContentAnimation", NOT_SECRET, "MessageAnimation, caption") {
      TdApi.PushMessageContentAnimation(animation, "Me on Monday mornings", it)
    }
  }
  (kit.media["audio"] as TdApi.Audio?)?.let { audio ->
    pinnable("PushMessageContentAudio", NOT_SECRET, "MessageAudio") {
      TdApi.PushMessageContentAudio(audio, it)
    }
  }
  (kit.media["document"] as TdApi.Document?)?.let { document ->
    pinnable("PushMessageContentDocument", NOT_SECRET, "MessageDocument") {
      TdApi.PushMessageContentDocument(document, it)
    }
  }
  (kit.media["voiceNote"] as TdApi.VoiceNote?)?.let { voiceNote ->
    pinnable("PushMessageContentVoiceNote", NOT_SECRET, "MessageVoiceNote") {
      TdApi.PushMessageContentVoiceNote(voiceNote, it)
    }
  }
  (kit.media["videoNote"] as TdApi.VideoNote?)?.let { videoNote ->
    pinnable("PushMessageContentVideoNote", NOT_SECRET, "MessageVideoNote") {
      TdApi.PushMessageContentVideoNote(videoNote, it)
    }
  }
  (kit.media["sticker"] as TdApi.Sticker?)?.let { sticker ->
    pinnable("PushMessageContentSticker", NOT_SECRET, "MessageSticker") {
      TdApi.PushMessageContentSticker(sticker, sticker.emoji, it)
    }
  }
  pinnable("PushMessageContentPaidMedia", setOf(CHANNEL), "MessagePaidMedia, locked") {
    TdApi.PushMessageContentPaidMedia(50, it)
  }
  pinnable("PushMessageContentContact", NOT_SECRET, "MessageContact") {
    TdApi.PushMessageContentContact(memberName, it)
  }
  pinnable("PushMessageContentLocation", NOT_SECRET, "MessageLocation") {
    TdApi.PushMessageContentLocation(false, it)
  }
  pinnable("PushMessageContentLocation, live", CONVERSATIONS, "MessageLiveLocation") {
    TdApi.PushMessageContentLocation(true, it)
  }
  pinnable("PushMessageContentGame", NOT_SECRET, "MessageGame") {
    TdApi.PushMessageContentGame("Tower Run", it)
  }
  pinnable("PushMessageContentGameScore", CONVERSATIONS, "MessageGameScore") {
    TdApi.PushMessageContentGameScore("Tower Run", 1250, it)
  }
  pinnable("PushMessageContentInvoice", NOT_SECRET, "MessageInvoice") {
    TdApi.PushMessageContentInvoice("$18.99", it)
  }
  pinnable("PushMessageContentPoll", NOT_SECRET, "MessagePoll") {
    TdApi.PushMessageContentPoll("Where should we have dinner on Friday?", true, it)
  }
  pinnable("PushMessageContentPoll, quiz", NOT_SECRET, "MessagePoll, quiz") {
    TdApi.PushMessageContentPoll("Where should we have dinner on Friday?", false, it)
  }
  pinnable("PushMessageContentChecklist", CONVERSATIONS, "MessageChecklist") {
    TdApi.PushMessageContentChecklist("Before the trip", it)
  }
  pinnable("PushMessageContentStory", NOT_SECRET, "MessageStory") {
    TdApi.PushMessageContentStory(false, it)
  }
  add(
    "PushMessageContentStory, mention", PRIVATE_CHAT, "MessageStory, mention, incoming",
    TdApi.PushMessageContentStory(true, false)
  )
  pinnable("PushMessageContentGiveaway", setOf(CHANNEL, SUPERGROUP), "MessageGiveaway") {
    TdApi.PushMessageContentGiveaway(10, TdApi.GiveawayPrizePremium(3), it)
  }
  add(
    "PushMessageContentGiveaway, stars", setOf(CHANNEL, SUPERGROUP), "MessageGiveaway",
    TdApi.PushMessageContentGiveaway(10, TdApi.GiveawayPrizeStars(5000), false)
  )
  add(
    "PushMessageContentContactRegistered", PRIVATE_CHAT, "MessageContactRegistered",
    TdApi.PushMessageContentContactRegistered(false)
  )
  add(
    "PushMessageContentContactRegistered, premium", PRIVATE_CHAT, "MessageContactRegistered",
    TdApi.PushMessageContentContactRegistered(true)
  )
  add(
    "PushMessageContentPremiumGiftCode", PRIVATE_CHAT, "MessagePremiumGiftCode, incoming",
    TdApi.PushMessageContentPremiumGiftCode(3)
  )
  add(
    "PushMessageContentTonWalletTransfer", PRIVATE_CHAT, "MessageTonWalletTransfer, incoming",
    TdApi.PushMessageContentTonWalletTransfer(member.id, "2.5", "For the concert tickets")
  )
  add(
    "PushMessageContentTonConnectRequest", PRIVATE_CHAT, "MessageTonConnectRequest",
    TdApi.PushMessageContentTonConnectRequest("Ticket Market")
  )
  add(
    "PushMessageContentGift", PRIVATE_CHAT, "MessageGift, incoming",
    TdApi.PushMessageContentGift(50, false)
  )
  add(
    "PushMessageContentGift, prepaid upgrade", PRIVATE_CHAT, "MessageGift, incoming",
    TdApi.PushMessageContentGift(50, true)
  )
  add(
    "PushMessageContentUpgradedGift", PRIVATE_CHAT, "MessageUpgradedGift, incoming",
    TdApi.PushMessageContentUpgradedGift(false, false)
  )
  add(
    "PushMessageContentUpgradedGift, upgrade", PRIVATE_CHAT, "MessageUpgradedGift, incoming",
    TdApi.PushMessageContentUpgradedGift(true, false)
  )
  add(
    "PushMessageContentScreenshotTaken", setOf(PRIVATE, SECRET), "MessageScreenshotTaken",
    TdApi.PushMessageContentScreenshotTaken()
  )
  add(
    "PushMessageContentBasicGroupChatCreate", setOf(BASIC_GROUP), "MessageBasicGroupChatCreate",
    TdApi.PushMessageContentBasicGroupChatCreate()
  )
  add(
    "PushMessageContentVideoChatStarted", GROUPS_AND_CHANNELS, "MessageVideoChatStarted",
    TdApi.PushMessageContentVideoChatStarted()
  )
  add(
    "PushMessageContentVideoChatEnded", GROUPS_AND_CHANNELS, "MessageVideoChatEnded",
    TdApi.PushMessageContentVideoChatEnded()
  )
  add(
    "PushMessageContentInviteVideoChatParticipants", GROUPS,
    "MessageInviteVideoChatParticipants",
    TdApi.PushMessageContentInviteVideoChatParticipants(false)
  )
  add(
    "PushMessageContentInviteVideoChatParticipants, me", GROUPS,
    "MessageInviteVideoChatParticipants",
    TdApi.PushMessageContentInviteVideoChatParticipants(true)
  )
  add(
    "PushMessageContentChatAddMembers", GROUPS, "MessageChatAddMembers",
    TdApi.PushMessageContentChatAddMembers(memberName, false, false)
  )
  add(
    "PushMessageContentChatAddMembers, me", GROUPS, "MessageChatAddMembers",
    TdApi.PushMessageContentChatAddMembers(memberName, true, false)
  )
  add(
    "PushMessageContentChatAddMembers, returned", GROUPS, "MessageChatAddMembers",
    TdApi.PushMessageContentChatAddMembers(memberName, false, true)
  )
  add(
    "PushMessageContentChatDeleteMember", GROUPS, "MessageChatDeleteMember",
    TdApi.PushMessageContentChatDeleteMember(memberName, false, false)
  )
  add(
    "PushMessageContentChatDeleteMember, me", GROUPS, "MessageChatDeleteMember",
    TdApi.PushMessageContentChatDeleteMember(memberName, true, false)
  )
  add(
    "PushMessageContentChatDeleteMember, left", GROUPS, "MessageChatDeleteMember",
    TdApi.PushMessageContentChatDeleteMember(memberName, false, true)
  )
  add(
    "PushMessageContentChatChangePhoto", GROUPS_AND_CHANNELS, "MessageChatChangePhoto",
    TdApi.PushMessageContentChatChangePhoto()
  )
  add(
    "PushMessageContentChatChangeTitle", GROUPS_AND_CHANNELS, "MessageChatChangeTitle",
    TdApi.PushMessageContentChatChangeTitle("Trip to Lisbon")
  )
  add(
    "PushMessageContentChatSetBackground", PRIVATE_CHAT, "MessageChatSetBackground",
    TdApi.PushMessageContentChatSetBackground(false)
  )
  add(
    "PushMessageContentChatSetBackground, same", PRIVATE_CHAT, "MessageChatSetBackground",
    TdApi.PushMessageContentChatSetBackground(true)
  )
  add(
    "PushMessageContentChatSetTheme", PRIVATE_CHAT, "MessageChatSetTheme",
    TdApi.PushMessageContentChatSetTheme("🌷")
  )
  add(
    "PushMessageContentChatSetTheme, reset", PRIVATE_CHAT, "MessageChatSetTheme, reset",
    TdApi.PushMessageContentChatSetTheme("")
  )
  add(
    "PushMessageContentChatJoinByLink", GROUPS, "MessageChatJoinByLink",
    TdApi.PushMessageContentChatJoinByLink()
  )
  add(
    "PushMessageContentChatJoinByRequest", GROUPS, "MessageChatJoinByRequest",
    TdApi.PushMessageContentChatJoinByRequest()
  )
  add(
    "PushMessageContentRecurringPayment", PRIVATE_CHAT, "MessagePaymentSuccessful",
    TdApi.PushMessageContentRecurringPayment("$18.99")
  )
  add(
    "PushMessageContentSuggestProfilePhoto", PRIVATE_CHAT, "MessageSuggestProfilePhoto",
    TdApi.PushMessageContentSuggestProfilePhoto()
  )
  add(
    "PushMessageContentSuggestBirthdate", PRIVATE_CHAT, "MessageSuggestBirthdate",
    TdApi.PushMessageContentSuggestBirthdate()
  )
  add(
    "PushMessageContentProximityAlertTriggered", CONVERSATIONS,
    "MessageProximityAlertTriggered, incoming",
    TdApi.PushMessageContentProximityAlertTriggered(150)
  )
  add(
    "PushMessageContentChecklistTasksAdded", CONVERSATIONS, "MessageChecklistTasksAdded",
    TdApi.PushMessageContentChecklistTasksAdded(1)
  )
  add(
    "PushMessageContentChecklistTasksDone", CONVERSATIONS, "MessageChecklistTasksDone",
    TdApi.PushMessageContentChecklistTasksDone(2)
  )
  add(
    "PushMessageContentPollOptionAdded", CONVERSATIONS, "MessagePollOptionAdded",
    TdApi.PushMessageContentPollOptionAdded("Thai")
  )
  // Several messages at once, which no single message replaces
  add(
    "PushMessageContentMessageForwards", NOT_SECRET, null,
    TdApi.PushMessageContentMessageForwards(3)
  )
  add(
    "PushMessageContentMediaAlbum", NOT_SECRET, null,
    TdApi.PushMessageContentMediaAlbum(4, true, true, false, false)
  )
  add(
    "PushMessageContentMediaAlbum, documents", NOT_SECRET, null,
    TdApi.PushMessageContentMediaAlbum(3, false, false, false, true)
  )
  return list
}
