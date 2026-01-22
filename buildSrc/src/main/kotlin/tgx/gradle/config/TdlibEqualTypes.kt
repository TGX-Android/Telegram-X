package tgx.gradle.config

import tgx.gradle.data.TdlibType

fun tdlibEqualTypes(): Array<TdlibType> = arrayOf(
  TdlibType("ChatMemberStatus", ignoredFields = setOf(
    "ChatMemberStatusAdministrator.canBeEdited",
    "ChatMemberStatusMember.memberUntilDate"
  )),
  TdlibType("InternalLinkType", isExperimental = true),
  TdlibType("InviteLinkChatType"),
  TdlibType("PollType", isExperimental = true),

  TdlibType("ProxyType"),
  TdlibType("InlineKeyboardButtonType"),
  TdlibType("KeyboardButtonType"),
  TdlibType("KeyboardButton"),
  TdlibType("InlineKeyboardButton"),
  TdlibType("WebAppOpenMode"),
  TdlibType("ReplyMarkup"),

  TdlibType("GroupCall"),
  TdlibType("GroupCallRecentSpeaker"),

  TdlibType("Call"),
  TdlibType("CallState"),
  TdlibType("CallDiscardReason"),
  TdlibType("CallProtocol"),
  TdlibType("CallServer"),
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
  TdlibType("Document"),
  TdlibType("AnimatedChatPhoto"),
  TdlibType("Sticker"),
  TdlibType("SpeechRecognitionResult"),
  TdlibType("VideoNote"),
  TdlibType("Video"),
  TdlibType("Audio"),

  TdlibType("StoryList"),
  TdlibType("SuggestedAction", isExperimental = true),

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
  TdlibType("VectorPathCommand"),
  TdlibType("ClosedVectorPath"),
  TdlibType("Error"),

  TdlibType("StickerType"),
  TdlibType("StickerFormat"),
  TdlibType("StickerFullType"),
  TdlibType("ThumbnailFormat"),
  TdlibType("Thumbnail"),

  TdlibType("ProfileAccentColors"),
  TdlibType("ProfileAccentColor"),
  TdlibType("AccentColor"),
  TdlibType("ThemeSettings"),
  TdlibType("BuiltInTheme"),

  TdlibType("CanSendMessageToUserResult"),
  TdlibType("MessageSelfDestructType"),
  TdlibType("MessageSender"),
  TdlibType("TextQuote", isExperimental = true),
  TdlibType("MessageReplyTo", ignoredFields = setOf(
    "MessageReplyToMessage.quote", // FIXME(?)
    "MessageReplyToMessage.origin",
    "MessageReplyToMessage.originSendDate",
    "MessageReplyToMessage.content",
  )),
  TdlibType("MessageTopic"),

  TdlibType("LinkPreviewType"),
  TdlibType("LinkPreviewAlbumMedia"),

  TdlibType("TextEntityType"),
  TdlibType("TextEntity"),

  TdlibType("OptionValue"),

  TdlibType("ChatFolderName", isExperimental = true),

  TdlibType("Gift"),
  TdlibType("GiftAuction"),
  TdlibType("GiftPurchaseLimits"),
  TdlibType("GiftBackground"),

  TdlibType("UpgradedGift", isExperimental = true),
  TdlibType("UpgradedGiftColors"),
  TdlibType("UpgradedGiftModel"),
  TdlibType("UpgradedGiftSymbol"),
  TdlibType("UpgradedGiftBackdrop"),
  TdlibType("UpgradedGiftBackdropColors"),
  TdlibType("UpgradedGiftOriginalDetails", isExperimental = true),
  TdlibType("GiftResaleParameters"),
)