@file:JvmName("Td")
@file:JvmMultifileClass

package me.vkryl.td

import org.drinkless.td.libcore.telegram.TdApi.*

fun ChatPermissions.copyTo (dst: ChatPermissions) {
  dst.canSendMessages = this.canSendMessages
  dst.canSendMediaMessages = this.canSendMediaMessages
  dst.canSendOtherMessages = this.canSendOtherMessages
  dst.canAddWebPagePreviews = this.canAddWebPagePreviews
  dst.canSendPolls = this.canSendPolls
  dst.canInviteUsers = this.canInviteUsers
  dst.canPinMessages = this.canPinMessages
  dst.canChangeInfo = this.canChangeInfo
}

fun ChatPosition.copyTo (dst: ChatPosition) {
  dst.list = this.list
  dst.order = this.order
  dst.isPinned = this.isPinned
  dst.source = this.source
}

fun File.copyTo (dst: File): Boolean {
  val hasChanges = !this.equalsTo(dst, false)

  dst.local.downloadedSize = this.local.downloadedSize
  dst.local.downloadOffset = this.local.downloadOffset
  dst.local.downloadedPrefixSize = this.local.downloadedPrefixSize
  dst.local.isDownloadingActive = this.local.isDownloadingActive
  dst.local.isDownloadingCompleted = this.local.isDownloadingCompleted
  dst.local.canBeDeleted = this.local.canBeDeleted
  dst.local.canBeDownloaded = this.local.canBeDownloaded
  dst.local.path = this.local.path

  dst.remote.uploadedSize = this.remote.uploadedSize
  dst.remote.isUploadingActive = this.remote.isUploadingActive
  dst.remote.isUploadingCompleted = this.remote.isUploadingCompleted
  dst.remote.uniqueId = this.remote.uniqueId
  dst.remote.id = this.remote.id

  dst.expectedSize = this.expectedSize
  dst.size = this.size

  return hasChanges
}

fun User.copyTo (dst: User) {
  dst.firstName = this.firstName
  dst.lastName = this.lastName
  dst.username = this.username
  dst.phoneNumber = this.phoneNumber
  dst.profilePhoto = this.profilePhoto
  dst.isContact = this.isContact
  dst.isMutualContact = this.isMutualContact
  dst.isVerified = this.isVerified
  dst.restrictionReason = this.restrictionReason
  dst.haveAccess = this.haveAccess
  dst.type = this.type
  dst.languageCode = this.languageCode
}

fun Message.copyTo (dst: Message) {
  dst.id = this.id
  dst.sender = this.sender
  dst.chatId = this.chatId
  dst.sendingState = this.sendingState
  dst.schedulingState = this.schedulingState
  dst.isOutgoing = this.isOutgoing
  dst.isPinned = this.isPinned
  dst.canBeEdited = this.canBeEdited
  dst.canBeForwarded = this.canBeForwarded
  dst.canBeDeletedOnlyForSelf = this.canBeDeletedOnlyForSelf
  dst.canBeDeletedForAllUsers = this.canBeDeletedForAllUsers
  dst.canGetStatistics = this.canGetStatistics
  dst.canGetMessageThread = this.canGetMessageThread
  dst.canGetViewers = this.canGetViewers
  dst.canGetMediaTimestampLinks = this.canGetMediaTimestampLinks
  dst.hasTimestampedMedia = this.hasTimestampedMedia
  dst.isChannelPost = this.isChannelPost
  dst.containsUnreadMention = this.containsUnreadMention
  dst.date = this.date
  dst.editDate = this.editDate
  dst.forwardInfo = this.forwardInfo
  dst.replyToMessageId = this.replyToMessageId
  dst.ttl = this.ttl
  dst.ttlExpiresIn = this.ttlExpiresIn
  dst.viaBotUserId = this.viaBotUserId
  dst.authorSignature = this.authorSignature
  dst.interactionInfo = this.interactionInfo
  dst.replyInChatId = this.replyInChatId
  dst.messageThreadId = this.messageThreadId
  dst.mediaAlbumId = this.mediaAlbumId
  dst.restrictionReason = this.restrictionReason
  dst.content = this.content
  dst.replyMarkup = this.replyMarkup
}

fun File?.copyOf (): File? {
  return this?.let {
    File(
      this.id,
      this.size,
      this.expectedSize,
      this.local.copyOf(),
      this.remote.copyOf())
  }
}

fun LocalFile?.copyOf (): LocalFile? {
  return this?.let {
    LocalFile(
      this.path,
      this.canBeDownloaded,
      this.canBeDeleted,
      this.isDownloadingActive,
      this.isDownloadingCompleted,
      this.downloadOffset,
      this.downloadedPrefixSize,
      this.downloadedSize
    )
  }
}

fun RemoteFile?.copyOf (): RemoteFile? {
  return this?.let {
    RemoteFile(
      this.id,
      this.uniqueId,
      this.isUploadingActive,
      this.isUploadingCompleted,
      this.uploadedSize
    )
  }
}

fun Message?.copyOf (): Message? {
  return this?.let {
    Message(
      this.id,
      this.sender,
      this.chatId,
      this.sendingState,
      this.schedulingState,
      this.isOutgoing,
      this.isPinned,
      this.canBeEdited,
      this.canBeForwarded,
      this.canBeDeletedOnlyForSelf,
      this.canBeDeletedForAllUsers,
      this.canGetStatistics,
      this.canGetMessageThread,
      this.canGetViewers,
      this.canGetMediaTimestampLinks,
      this.hasTimestampedMedia,
      this.isChannelPost,
      this.containsUnreadMention,
      this.date,
      this.editDate,
      this.forwardInfo,
      this.interactionInfo,
      this.replyInChatId,
      this.replyToMessageId,
      this.messageThreadId,
      this.ttl,
      this.ttlExpiresIn,
      this.viaBotUserId,
      this.authorSignature,
      this.mediaAlbumId,
      this.restrictionReason,
      this.content,
      this.replyMarkup
    )
  }
}

fun Chat?.copyOf (): Chat? {
  return this?.let {
    Chat(
      this.id,
      this.type,
      this.title,
      this.photo,
      this.permissions,
      this.lastMessage,
      if (this.positions != null) this.positions.copyOf() else null,
      this.isMarkedAsUnread,
      this.isBlocked,
      this.hasScheduledMessages,
      this.canBeDeletedOnlyForSelf,
      this.canBeDeletedForAllUsers,
      this.canBeReported,
      this.defaultDisableNotification,
      this.unreadCount,
      this.lastReadInboxMessageId,
      this.lastReadOutboxMessageId,
      this.unreadMentionCount,
      this.notificationSettings,
      this.messageTtlSetting,
      this.themeName,
      this.actionBar,
      this.voiceChat,
      this.replyMarkupMessageId,
      this.draftMessage,
      this.clientData
    )
  }
}

fun ChatPermissions?.copyOf (): ChatPermissions? {
  return this?.let {
    ChatPermissions(
      this.canSendMessages,
      this.canSendMediaMessages,
      this.canSendPolls,
      this.canSendOtherMessages,
      this.canAddWebPagePreviews,
      this.canChangeInfo,
      this.canInviteUsers,
      this.canPinMessages
    )
  }
}

fun StickerSetInfo?.copyOf (): StickerSetInfo? {
  return this?.let {
    StickerSetInfo(
      this.id,
      this.title,
      this.name,
      this.thumbnail,
      this.thumbnailOutline,
      this.isInstalled,
      this.isArchived,
      this.isOfficial,
      this.isAnimated,
      this.isMasks,
      this.isViewed,
      this.size,
      this.covers
    )
  }
}

fun StickerSet?.copyOf (): StickerSet? {
  return this?.let {
    StickerSet(
      this.id,
      this.title,
      this.name,
      this.thumbnail,
      this.thumbnailOutline,
      this.isInstalled,
      this.isArchived,
      this.isOfficial,
      this.isAnimated,
      this.isMasks,
      this.isViewed,
      this.stickers,
      this.emojis
    )
  }
}

fun ChatMemberStatus?.copyOf (): ChatMemberStatus? {
  return this?.let {
    when (constructor) {
      ChatMemberStatusCreator.CONSTRUCTOR -> ChatMemberStatusCreator(
        (this as ChatMemberStatusCreator).customTitle,
        this.isAnonymous,
        this.isMember
      )
      ChatMemberStatusAdministrator.CONSTRUCTOR -> ChatMemberStatusAdministrator(
        (this as ChatMemberStatusAdministrator).customTitle,
        this.canBeEdited,
        this.canManageChat,
        this.canChangeInfo,
        this.canPostMessages,
        this.canEditMessages,
        this.canDeleteMessages,
        this.canInviteUsers,
        this.canRestrictMembers,
        this.canPinMessages,
        this.canPromoteMembers,
        this.canManageVoiceChats,
        this.isAnonymous
      )
      ChatMemberStatusBanned.CONSTRUCTOR -> ChatMemberStatusBanned((this as ChatMemberStatusBanned).bannedUntilDate)
      ChatMemberStatusLeft.CONSTRUCTOR -> ChatMemberStatusLeft()
      ChatMemberStatusMember.CONSTRUCTOR -> ChatMemberStatusMember()
      ChatMemberStatusRestricted.CONSTRUCTOR -> ChatMemberStatusRestricted(
        (this as ChatMemberStatusRestricted).isMember,
        this.restrictedUntilDate,
        this.permissions.copyOf()
      )
      else -> TODO(this.toString())
    }
  }
}