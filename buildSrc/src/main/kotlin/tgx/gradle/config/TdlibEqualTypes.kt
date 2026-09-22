package tgx.gradle.config

import tgx.gradle.data.ArrayType
import tgx.gradle.data.TdlibType

fun tdlibEqualTypes(): Array<TdlibType> = arrayOf(
  TdlibType("ChatMemberStatus",
    ignoredFields = setOf(
      "ChatMemberStatusAdministrator.canBeEdited",
      "ChatMemberStatusMember.memberUntilDate"
    )
  ),
  TdlibType("InternalLinkType",
    isExperimental = true
  ),
  TdlibType("InviteLinkChatType"),

  TdlibType("Location"),
  TdlibType("Venue"),
  TdlibType("MessageLocation"),

  TdlibType("Proxy"),
  TdlibType("ProxyType"),

  TdlibType("SettingsSection"),

  TdlibType("InlineKeyboardButtonType"),
  TdlibType("InlineButton",
    array = ArrayType.TWO_DIMENSIONAL
  ),
  TdlibType("KeyboardButtonType"),
  TdlibType("KeyboardButton",
    array = ArrayType.TWO_DIMENSIONAL
  ),
  TdlibType("ButtonStyle"),
  TdlibType("InlineKeyboardButton",
    array = ArrayType.TWO_DIMENSIONAL
  ),
  TdlibType("WebAppOpenMode"),
  TdlibType("ReplyMarkup"),

  TdlibType("GroupCall"),
  TdlibType("MessageGroupCall"),
  TdlibType("GroupCallRecentSpeaker",
    array = ArrayType.REGULAR
  ),
  TdlibType("GroupCallParticipantVideoInfo"),
  TdlibType("GroupCallParticipant"),
  TdlibType("GroupCallVideoSourceGroup",
    array = ArrayType.REGULAR
  ),

  TdlibType("Call"),
  TdlibType("CallState"),
  TdlibType("CallDiscardReason"),
  TdlibType("CallProtocol"),
  TdlibType("CallServer",
    array = ArrayType.REGULAR
  ),
  TdlibType("CallServerType"),

  TdlibType("RemoteFile"),
  TdlibType("LocalFile"),

  TdlibType("UserStatus"),
  TdlibType("UserPrivacySettingRule"),
  TdlibType("Usernames"),

  TdlibType("DeviceToken"),
  TdlibType("ReactionType"),
  TdlibType("BackgroundFill"),
  TdlibType("Background"),

  TdlibType("PhotoSize"),
  TdlibType("Photo"),
  TdlibType("Animation"),
  TdlibType("Document",
    array = ArrayType.REGULAR
  ),
  TdlibType("AnimatedChatPhoto"),
  TdlibType("Sticker",
    array = ArrayType.REGULAR
  ),
  TdlibType("SpeechRecognitionResult"),
  TdlibType("VideoNote"),
  TdlibType("Video"),
  TdlibType("Audio"),
  TdlibType("PollVoteRestrictionReason"),

  TdlibType("StoryList"),
  TdlibType("StoryContentType"),
  TdlibType("SuggestedAction",
    isExperimental = true
  ),

  TdlibType("TargetChat"),
  TdlibType("TargetChatTypes"),

  TdlibType("ChatActionBar"),
  TdlibType("AccountInfo"),
  TdlibType("ChatPermissions"),
  TdlibType("ChatPhoto"),
  TdlibType("ChatAdministratorRights"),
  TdlibType("ChatAction"),
  TdlibType("ChatSource"),
  TdlibType("ChatPhotoStickerType"),
  TdlibType("ChatPhotoSticker"),
  TdlibType("ChatFolderIcon"),
  TdlibType("ChatList"),

  TdlibType("MaskPosition"),
  TdlibType("MaskPoint"),
  TdlibType("Point"),
  TdlibType("VectorPathCommand",
    array = ArrayType.REGULAR
  ),
  TdlibType("ClosedVectorPath",
    array = ArrayType.REGULAR
  ),
  TdlibType("Error"),

  TdlibType("StickerType"),
  TdlibType("StickerFormat"),
  TdlibType("StickerFullType"),
  TdlibType("ThumbnailFormat"),
  TdlibType("Thumbnail",
    array = ArrayType.REGULAR
  ),

  TdlibType("ProfileAccentColors"),
  TdlibType("ProfileAccentColor"),
  TdlibType("AccentColor"),
  TdlibType("ThemeSettings"),
  TdlibType("BuiltInTheme"),

  TdlibType("InputRichMessage"),
  TdlibType("InputMessageContent",
    isExperimental = true
  ),
  TdlibType("InputAnimation"),
  TdlibType("InputVideo"),
  TdlibType("InputAudio"),
  TdlibType("InputDocument"),
  TdlibType("InputChecklist",
    isExperimental = true
  ),
  TdlibType("InputChecklistTask",
    isExperimental = true,
    array = ArrayType.REGULAR
  ),
  TdlibType("Contact"),
  TdlibType("LiveLocation"),
  TdlibType("Invoice"),
  TdlibType("LabeledPricePart",
    array = ArrayType.REGULAR
  ),
  TdlibType("InputPaidMedia",
    array = ArrayType.REGULAR
  ),
  TdlibType("InputFile"),
  TdlibType("InputThumbnail"),
  TdlibType("InputPaidMediaType"),
  TdlibType("InputVoiceNote"),
  TdlibType("InputVideoNote"),
  TdlibType("InputPhoto"),
  TdlibType("InputPollMedia"),
  TdlibType("InputPollOption",
    isExperimental = true,
    array = ArrayType.REGULAR
  ),
  TdlibType("InputPollType",
    isExperimental = true
  ),
  TdlibType("InputSticker"),
  TdlibType("InputMessagePaidMedia",
    isExperimental = true,
    array = ArrayType.REGULAR
  ),
  TdlibType("InputPageBlock",
    array = ArrayType.REGULAR
  ),
  TdlibType("InputPageBlockListItem",
    array = ArrayType.REGULAR
  ),
  TdlibType("InputRichMessageMedia",
    array = ArrayType.REGULAR
  ),

  TdlibType("RichMessageSource"),

  TdlibType("RichMessage"),
  TdlibType("RichText",
    array = ArrayType.REGULAR
  ),
  TdlibType("PageBlock",
    array = ArrayType.REGULAR
  ),
  TdlibType("PageBlockCaption"),
  TdlibType("PageBlockListItem",
    array = ArrayType.REGULAR
  ),
  TdlibType("PageBlockRelatedArticle",
    array = ArrayType.REGULAR
  ),
  TdlibType("PageBlockTableCell",
    array = ArrayType.TWO_DIMENSIONAL
  ),
  TdlibType("PageBlockHorizontalAlignment"),
  TdlibType("PageBlockVerticalAlignment"),
  TdlibType("ChatPhotoInfo"),

  TdlibType("DraftMessageContent",
    isExperimental = true
  ),

  TdlibType("CanSendMessageToUserResult"),
  TdlibType("MessageSelfDestructType"),
  TdlibType("MessageCopyOptions",
    isExperimental = true
  ),
  TdlibType("MessageSender",
    array = ArrayType.REGULAR
  ),
  TdlibType("TextQuote",
    isExperimental = true
  ),
  TdlibType("MessageReplyTo",
    ignoredFields = setOf(
      "MessageReplyToMessage.quote", // FIXME(?)
      "MessageReplyToMessage.origin",
      "MessageReplyToMessage.originSendDate",
      "MessageReplyToMessage.content",
    )
  ),
  TdlibType("MessageTopic"),

  TdlibType("LinkPreviewType"),
  TdlibType("LinkPreviewAlbumMedia",
    array = ArrayType.REGULAR
  ),

  TdlibType("DateTimeFormattingType"),
  TdlibType("DateTimePartPrecision"),

  TdlibType("TextEntityType"),
  TdlibType("TextEntity"),

  TdlibType("OptionValue"),

  TdlibType("ChatFolderName",
    isExperimental = true
  ),

  TdlibType("Gift"),
  TdlibType("GiftAuction"),
  TdlibType("GiftPurchaseLimits"),
  TdlibType("GiftBackground"),

  TdlibType("UpgradedGift",
    isExperimental = true
  ),
  TdlibType("UpgradedGiftColors"),
  TdlibType("UpgradedGiftModel"),
  TdlibType("UpgradedGiftSymbol"),
  TdlibType("UpgradedGiftBackdrop"),
  TdlibType("UpgradedGiftBackdropColors"),
  TdlibType("UpgradedGiftOriginalDetails",
    isExperimental = true
  ),
  TdlibType("UpgradedGiftAttributeRarity"),
  TdlibType("GiftResaleParameters"),
)