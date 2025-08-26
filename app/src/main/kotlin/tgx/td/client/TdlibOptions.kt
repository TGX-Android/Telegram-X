/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 26/10/2024
 */
package tgx.td.client

import android.os.SystemClock
import androidx.annotation.IntDef
import me.vkryl.core.BitwiseUtils
import me.vkryl.core.clamp
import org.drinkless.tdlib.TdApi.*
import org.thunderdog.challegram.Log
import tgx.td.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

data class TdlibOptions(
  @JvmField var version: String = "",
  @JvmField var commitHash: String = "",
  @JvmField var unixTime: TdlibTime = TdlibTime(),
  @JvmField var utcTimeOffset: Long = 0L,

  @JvmField var authorizationDate: Long = 0L,
  @JvmField var myId: Long = 0L,
  @JvmField var authenticationToken: String = "",

  @JvmField var expectBlocking: Boolean = false,

  @JvmField var canIgnoreSensitiveContentRestrictions: Boolean = false,
  @JvmField var ignoreSensitiveContentRestrictions: Boolean = false,

  @JvmField var canSetNewChatPrivacySettings: Boolean = false,
  @JvmField var canArchiveAndMuteNewChatsFromUnknownUsers: Boolean = false,
  @JvmField var canEditFactCheck: Boolean = false,
  @JvmField var canWithdrawChatRevenue: Boolean = false,
  @JvmField var canGiftStars: Boolean = false,
  @JvmField var canEnablePaidMessages: Boolean = false,
  @JvmField var canPreloadWeather: Boolean = false,

  @JvmField var disableTopChats: Boolean = false,
  @JvmField var disableContactRegisteredNotifications: Boolean = false,
  @JvmField var disableSentScheduledMessageNotifications: Boolean = false,

  @JvmField var isPremium: Boolean = false,
  @JvmField var isPremiumAvailable: Boolean = false,

  @JvmField @GiftPremiumFrom var giftPremiumFrom: Int = 0,
  @JvmField var premiumUploadSpeedup: Int = 10,
  @JvmField var premiumDownloadSpeedup: Int = 10,
  @JvmField var premiumGiftBoostCount: Int = 3,

  @JvmField var giveawayBoostCountPerPremium: Int = 4,
  @JvmField var giveawayAdditionalChatCountMax: Int = 10,
  @JvmField var giveawayDurationMax: Int = 2678400,
  @JvmField var giveawayCountryCountMax: Int = 10,

  @JvmField var usdToThousandStarRate: Long = 0,
  @JvmField var thousandStarToUsdRate: Long = 0,
  @JvmField var millionToncoinToUsdRate: Long = 0,

  @JvmField var giftTextLengthMax: Int = 128,
  @JvmField var pinnedGiftCountMax: Int = 6,

  @JvmField var giftSellPeriod: Long = 7776000L,
  @JvmField var giftResaleStarCountMin: Long = 125L,
  @JvmField var giftResaleStarCountMax: Long = 100000L,
  @JvmField var giftResaleEarningsPerMille: Long = 800L,

  @JvmField var starWithdrawalCountMin: Long = 1000L,
  @JvmField var starWithdrawalCountMax: Long = 25000000L,
  @JvmField var paidReactionStarCountMax: Long = 2500L,
  @JvmField var paidMessageEarningsPerMille: Long = 850L,
  @JvmField var paidMessageStarCountMax: Long = 10000L,
  @JvmField var paidMediaMessageStarCountMax: Long = 10000L,
  @JvmField var subscriptionStarCountMin: Long = 10000L,
  @JvmField var subscriptionStarCountMax: Long = 10000L,
  @JvmField var directChannelMessageStarCountDefault: Long = 10L,

  @JvmField var affiliateProgramCommissionPerMilleMin: Long = 1L,
  @JvmField var affiliateProgramCommissionPerMilleMax: Long = 800L,

  @JvmField var quickReplyShortcutCountMax: Int = 100,
  @JvmField var quickReplyShortcutMessageCountMax: Int = 20,

  @JvmField var suggestedPostLifetimeMin: Int = 86400,
  @JvmField var suggestedPostStarCountMin: Long = 5L,
  @JvmField var suggestedPostStarCountMax: Long = 100000L,
  @JvmField var suggestedPostStarEarningsPerMille: Long = 850L,
  @JvmField var suggestedPostToncoinCentCountMin: Long = 1L,
  @JvmField var suggestedPostToncoinCentCountMax: Long = 1000000L,
  @JvmField var suggestedPostToncoinEarningsPerMille: Long = 850L,
  @JvmField var suggestedPostSendDelayMin: Int = 300,
  @JvmField var suggestedPostSendDelayMax: Int = 2678400,

  @JvmField var tMeUrl: String = "",
  @JvmField var tonBlockchainExplorerUrl: String = "",
  @JvmField var toncoinTopUpUrl: String = "",

  @JvmField var languagePackId: String = "",
  @JvmField var suggestedLanguagePackId: String = "",
  @JvmField var webAppAllowedProtocols: Set<String> = setOf("https"),

  @JvmField var animationSearchBotUsername: String = "gif",
  @JvmField var venueSearchBotUsername: String = "foursquare",
  @JvmField var photoSearchBotUsername: String = "pic",

  @JvmField var antiSpamBotUserId: Long = 0L,
  @JvmField var channelBotUserId: Long = TELEGRAM_CHANNEL_BOT_ACCOUNT_ID,
  @JvmField var groupAnonymousBotUserId: Long = 0L,
  @JvmField var repliesBotUserId: Long = TELEGRAM_REPLIES_BOT_ACCOUNT_ID,

  @JvmField var repliesBotChatId: Long = fromUserId(repliesBotUserId),
  @JvmField var telegramServiceNotificationsChatId: Long = fromUserId(TELEGRAM_ACCOUNT_ID),
  @JvmField var verificationCodesBotChatId: Long = fromUserId(VERIFICATION_CODES_BOT_ACCOUNT_ID),

  @JvmField var botMediaPreviewCountMax: Int = 12,
  @JvmField var botVerificationCustomDescriptionLengthMax: Int = 70,

  @JvmField var canAcceptCalls: Boolean = false,
  @JvmField var callsEnabled: Boolean = true,

  @JvmField var groupCallParticipantCountMax: Int = 200,

  @JvmField var callConnectTimeoutMs: Long = 30000L,
  @JvmField var callPacketTimeoutMs: Long = 10000L,

  @JvmField var suggestedVideoNoteVideoBitrate: Long = 1000L,
  @JvmField var suggestedVideoNoteAudioBitrate: Long = 64L,
  @JvmField var suggestedVideoNoteLength: Long = 384L,
  @JvmField var videoNoteMaxSize: Long = 12582912L,

  @JvmField var forwardedMessageCountMax: Int = 100,
  @JvmField var favoriteStickersLimit: Int = 5,

  @JvmField var chatAvailableReactionCountMax: Int = 100,
  @JvmField var chatBoostLevelMax: Int = 100,

  @JvmField var chatFolderCountMax: Int = 10,
  @JvmField var chatFolderChosenChatCountMax: Int = 100,
  @JvmField var chatFolderInviteLinkCountMax: Int = 3,
  @JvmField var chatFolderNewChatsUpdatePeriod: Int = 300,
  @JvmField var addedShareableChatFolderCountMax: Int = 2,

  @JvmField var bioLengthMax: Int = 70,

  @JvmField var messageTextLengthMax: Int = 4000,
  @JvmField var messageCaptionLengthMax: Int = 1024,
  @JvmField var messageReplyQuoteLengthMax: Int = 1024,

  @JvmField var pollAnswerCountMax: Int = 12,

  @JvmField var checklistTaskCountMax: Int = 30,
  @JvmField var checklistTaskTextLengthMax: Int = 200,
  @JvmField var checklistTitleLengthMax: Int = 255,

  @JvmField var factCheckLengthMax: Int = 1024,

  @JvmField var basicGroupSizeMax: Int = 200,
  @JvmField var supergroupSizeMax: Int = 200000,

  @JvmField var pinnedChatCountMax: Int = 5,
  @JvmField var pinnedArchivedChatCountMax: Int = 100,
  @JvmField var pinnedForumTopicCountMax: Int = 5,
  @JvmField var pinnedSavedMessagesTopicCountMax: Int = 5,

  @JvmField var pinnedStoryCountMax: Int = 3,
  @JvmField var activeStoryCountMax: Int = 100,
  @JvmField var weeklySentStoryCountMax: Int = 700,
  @JvmField var monthlySentStoryCountMax: Int = 3000,

  @JvmField var canUseTextEntitiesInStoryCaption: Boolean = false,
  @JvmField var storyCaptionLengthMax: Int = 2048,
  @JvmField var storySuggestedReactionAreaCountMax: Int = 5,
  @JvmField var storyViewersExpirationDelay: Int = 86400,
  @JvmField var storyStealthModeCooldownPeriod: Int = 3600,
  @JvmField var storyStealthModeFuturePeriod: Int = 1500,
  @JvmField var storyStealthModePastPeriod: Int = 300,
  @JvmField var storyLinkAreaCountMax: Int = 3,

  @JvmField var businessStartPageTitleLengthMax: Int = 32,
  @JvmField var businessStartPageMessageLengthMax: Int = 70,
  @JvmField var businessChatLinkCountMax: Int = 100,

  // Server config

  @JvmField var forceInAppUpdate: Boolean = false,
  @JvmField var youtubePipDisabled: Boolean = false,

  @JvmField var qrLoginCamera: Boolean = true,

  @JvmField var stickersEmojiSuggestOnlyApi: Boolean = false,
  @JvmField var emojiesAnimatedZoom: Float = DEFAULT_EMOJIES_ANIMATED_ZOOM.toFloat(),

  @JvmField var dialogFiltersEnabled: Boolean = true,
  @JvmField var dialogFiltersTooltip: Boolean = true,

  @JvmField var giveawayGiftsPurchaseAvailable: Boolean = true,
  @JvmField var starsPurchaseBlocked: Boolean = false,
  @JvmField var starGiftsBlocked: Boolean = false,

  @JvmField var premiumPlayMarketDirectCurrencyList: List<String> = listOf(),

  @JvmField var storyExpirePeriod: Long = 0,
  @JvmField var storyPostingEnabled: Boolean = true,

  @JvmField var translationsManualEnabled: Boolean = true,
  @JvmField var translationsAutoEnabled: Boolean = true,

  @JvmField var starRefProgramAllowed: Boolean = false,
  @JvmField var starRefConnectAllowed: Boolean = false,

  @JvmField var isDeveloper: Boolean = false,

  @JvmField var groupCallVideoParticipantsMax: Int = 10000,
  @JvmField var rtcServers: List<RtcServer> = listOf()
) {
  private val map: MutableMap<String, OptionValue> = mutableMapOf()

  private operator fun set (name: String, value: OptionValue): Int {
    val previousValue = map[name]
    map[name] = value

    when (name) {
      "version" ->
        version = value.stringValue()
      "commit_hash" ->
        commitHash = value.stringValue()
      "unix_time" ->
        unixTime = TdlibTime(value)
      "utc_time_offset" ->
        utcTimeOffset = value.longValue()

      "authorization_date" ->
        authorizationDate = value.longValue()
      "authentication_token" ->
        authenticationToken = value.stringValue()
      "my_id" ->
        myId = value.longValue()

      "expect_blocking" ->
        expectBlocking = value.boolValue()

      "can_ignore_sensitive_content_restrictions" ->
        canIgnoreSensitiveContentRestrictions = value.boolValue()
      "ignore_sensitive_content_restrictions" ->
        ignoreSensitiveContentRestrictions = value.boolValue()

      "can_archive_and_mute_new_chats_from_unknown_users" ->
        canArchiveAndMuteNewChatsFromUnknownUsers = value.boolValue()
      "can_set_new_chat_privacy_settings" ->
        canSetNewChatPrivacySettings = value.boolValue()
      "can_edit_fact_check" ->
        canEditFactCheck = value.boolValue()
      "can_withdraw_chat_revenue" ->
        canWithdrawChatRevenue = value.boolValue()
      "can_gift_stars" ->
        canGiftStars = value.boolValue()
      "can_enable_paid_messages" ->
        canEnablePaidMessages = value.boolValue()
      "can_preload_weather" ->
        canPreloadWeather = value.boolValue()

      "disable_top_chats" ->
        disableTopChats = value.boolValue()
      "disable_contact_registered_notifications" ->
        disableContactRegisteredNotifications = value.boolValue()
      "disable_sent_scheduled_message_notifications" ->
        disableSentScheduledMessageNotifications = value.boolValue()

      "is_premium" ->
        isPremium = value.boolValue()
      "is_premium_available" ->
        isPremiumAvailable = value.boolValue()

      "gift_premium_from_attachment_menu" ->
        giftPremiumFrom = BitwiseUtils.setFlag(giftPremiumFrom, GiftPremiumFrom.ATTACHMENT_MENU, value.boolValue())
      "gift_premium_from_input_field" ->
        giftPremiumFrom = BitwiseUtils.setFlag(giftPremiumFrom, GiftPremiumFrom.INPUT_FIELD, value.boolValue())
      "premium_upload_speedup" ->
        premiumUploadSpeedup = value.intValue()
      "premium_download_speedup" ->
        premiumDownloadSpeedup = value.intValue()
      "premium_gift_boost_count" ->
        premiumGiftBoostCount = value.intValue()

      "giveaway_boost_count_per_premium" ->
        giveawayBoostCountPerPremium = value.intValue()
      "giveaway_additional_chat_count_max" ->
        giveawayAdditionalChatCountMax = value.intValue()
      "giveaway_duration_max" ->
        giveawayDurationMax = value.intValue()
      "giveaway_country_count_max" ->
        giveawayCountryCountMax = value.intValue()

      "usd_to_thousand_star_rate" ->
        usdToThousandStarRate = value.longValue()
      "thousand_star_to_usd_rate" ->
        thousandStarToUsdRate = value.longValue()
      "million_toncoin_to_usd_rate" ->
        millionToncoinToUsdRate = value.longValue()

      "gift_text_length_max" ->
        giftTextLengthMax = value.intValue()
      "pinned_gift_count_max" ->
        pinnedGiftCountMax = value.intValue()

      "gift_sell_period" ->
        giftSellPeriod = value.longValue()
      "gift_resale_star_count_min" ->
        giftResaleStarCountMin = value.longValue()
      "gift_resale_star_count_max" ->
        giftResaleStarCountMax = value.longValue()
      "gift_resale_earnings_per_mille" ->
        giftResaleEarningsPerMille = value.longValue()

      "star_withdrawal_count_min" ->
        starWithdrawalCountMin = value.longValue()
      "star_withdrawal_count_max" ->
        starWithdrawalCountMax = value.longValue()
      "paid_reaction_star_count_max" ->
        paidReactionStarCountMax = value.longValue()
      "paid_message_earnings_per_mille" ->
        paidMessageEarningsPerMille = value.longValue()
      "paid_message_star_count_max" ->
        paidMessageStarCountMax = value.longValue()
      "paid_media_message_star_count_max" ->
        paidMediaMessageStarCountMax = value.longValue()
      "subscription_star_count_min" ->
        subscriptionStarCountMin = value.longValue()
      "subscription_star_count_max" ->
        subscriptionStarCountMax = value.longValue()
      "direct_channel_message_star_count_default" ->
        directChannelMessageStarCountDefault = value.longValue() /*10*/

      "affiliate_program_commission_per_mille_min" ->
        affiliateProgramCommissionPerMilleMin = value.longValue()
      "affiliate_program_commission_per_mille_max" ->
        affiliateProgramCommissionPerMilleMax = value.longValue()

      "quick_reply_shortcut_count_max" ->
        quickReplyShortcutCountMax = value.intValue()
      "quick_reply_shortcut_message_count_max" ->
        quickReplyShortcutMessageCountMax = value.intValue()

      "suggested_post_lifetime_min" ->
        suggestedPostLifetimeMin = value.intValue()
      "suggested_post_star_count_min" ->
        suggestedPostStarCountMin = value.longValue()
      "suggested_post_star_count_max" ->
        suggestedPostStarCountMax = value.longValue()
      "suggested_post_star_earnings_per_mille" ->
        suggestedPostStarEarningsPerMille = value.longValue()
      "suggested_post_toncoin_cent_count_min" ->
        suggestedPostToncoinCentCountMin = value.longValue()
      "suggested_post_toncoin_cent_count_max" ->
        suggestedPostToncoinCentCountMax = value.longValue()
      "suggested_post_toncoin_earnings_per_mille" ->
        suggestedPostToncoinEarningsPerMille = value.longValue()
      "suggested_post_send_delay_min" ->
        suggestedPostSendDelayMin = value.intValue()
      "suggested_post_send_delay_max" ->
        suggestedPostSendDelayMax = value.intValue()

      "t_me_url" ->
        tMeUrl = value.stringValue()
      "ton_blockchain_explorer_url" ->
        tonBlockchainExplorerUrl = value.stringValue()
      "toncoin_top_up_url" ->
        toncoinTopUpUrl = value.stringValue()

      "language_pack_id" ->
        languagePackId = value.stringValue()
      "suggested_language_pack_id" ->
        suggestedLanguagePackId = value.stringValue()

      "web_app_allowed_protocols" ->
        webAppAllowedProtocols = value.stringValue().split(" ").toHashSet()

      "animation_search_bot_username" ->
        animationSearchBotUsername = value.stringValue()
      "venue_search_bot_username" ->
        venueSearchBotUsername = value.stringValue()
      "photo_search_bot_username" ->
        photoSearchBotUsername = value.stringValue()

      "anti_spam_bot_user_id" ->
        antiSpamBotUserId = value.longValue()
      "channel_bot_user_id" ->
        channelBotUserId = value.longValue()
      "group_anonymous_bot_user_id" ->
        groupAnonymousBotUserId = value.longValue()
      "replies_bot_user_id" ->
        repliesBotUserId = value.longValue()

      "replies_bot_chat_id" ->
        repliesBotChatId = value.longValue()
      "telegram_service_notifications_chat_id" ->
        telegramServiceNotificationsChatId = value.longValue()
      "verification_codes_bot_chat_id" ->
        verificationCodesBotChatId = value.longValue()

      "bot_media_preview_count_max" ->
        botMediaPreviewCountMax = value.intValue()
      "bot_verification_custom_description_length_max" ->
        botVerificationCustomDescriptionLengthMax = value.intValue()

      "can_accept_calls" ->
        canAcceptCalls = value.boolValue()
      "calls_enabled" ->
        callsEnabled = value.boolValue()

      "group_call_participant_count_max" ->
        groupCallParticipantCountMax = value.intValue()

      "call_connect_timeout_ms" ->
        callConnectTimeoutMs = value.longValue()
      "call_packet_timeout_ms" ->
        callPacketTimeoutMs = value.longValue()

      "suggested_video_note_video_bitrate" ->
        suggestedVideoNoteVideoBitrate = value.longValue()
      "suggested_video_note_audio_bitrate" ->
        suggestedVideoNoteAudioBitrate = value.longValue()
      "suggested_video_note_length" ->
        suggestedVideoNoteLength = value.longValue()

      "forwarded_message_count_max" ->
        forwardedMessageCountMax = value.intValue()
      "favorite_stickers_limit" ->
        favoriteStickersLimit = value.intValue()

      "chat_available_reaction_count_max" ->
        chatAvailableReactionCountMax = value.intValue()
      "chat_boost_level_max" ->
        chatBoostLevelMax = value.intValue()

      "chat_folder_count_max" ->
        chatFolderCountMax = value.intValue()
      "chat_folder_chosen_chat_count_max" ->
        chatFolderChosenChatCountMax = value.intValue()
      "chat_folder_invite_link_count_max" ->
        chatFolderInviteLinkCountMax = value.intValue()
      "chat_folder_new_chats_update_period" ->
        chatFolderNewChatsUpdatePeriod = value.intValue()
      "added_shareable_chat_folder_count_max" ->
        addedShareableChatFolderCountMax = value.intValue()

      "bio_length_max" ->
        bioLengthMax = value.intValue()

      "message_text_length_max" ->
        messageTextLengthMax = value.intValue()
      "message_caption_length_max" ->
        messageCaptionLengthMax = value.intValue()
      "message_reply_quote_length_max" ->
        messageReplyQuoteLengthMax = value.intValue()

      "poll_answer_count_max" ->
        pollAnswerCountMax = value.intValue()

      "checklist_task_count_max" ->
        checklistTaskCountMax = value.intValue()
      "checklist_task_text_length_max" ->
        checklistTaskTextLengthMax = value.intValue()
      "checklist_title_length_max" ->
        checklistTitleLengthMax = value.intValue()

      "fact_check_length_max" ->
        factCheckLengthMax = value.intValue()

      "basic_group_size_max" ->
        basicGroupSizeMax = value.intValue()
      "supergroup_size_max" ->
        supergroupSizeMax = value.intValue()

      "pinned_chat_count_max" ->
        pinnedChatCountMax = value.intValue()
      "pinned_archived_chat_count_max" ->
        pinnedArchivedChatCountMax = value.intValue()
      "pinned_forum_topic_count_max" ->
        pinnedForumTopicCountMax = value.intValue()
      "pinned_saved_messages_topic_count_max" ->
        pinnedSavedMessagesTopicCountMax = value.intValue()

      "pinned_story_count_max" ->
        pinnedStoryCountMax = value.intValue()
      "active_story_count_max" ->
        activeStoryCountMax = value.intValue()
      "weekly_sent_story_count_max" ->
        weeklySentStoryCountMax = value.intValue()
      "monthly_sent_story_count_max" ->
        monthlySentStoryCountMax = value.intValue()

      "can_use_text_entities_in_story_caption" ->
        canUseTextEntitiesInStoryCaption = value.boolValue()
      "story_caption_length_max" ->
        storyCaptionLengthMax = value.intValue()
      "story_suggested_reaction_area_count_max" ->
        storySuggestedReactionAreaCountMax = value.intValue()
      "story_viewers_expiration_delay" ->
        storyViewersExpirationDelay = value.intValue()
      "story_stealth_mode_cooldown_period" ->
        storyStealthModeCooldownPeriod = value.intValue()
      "story_stealth_mode_future_period" ->
        storyStealthModeFuturePeriod = value.intValue()
      "story_stealth_mode_past_period" ->
        storyStealthModePastPeriod = value.intValue()
      "story_link_area_count_max" ->
        storyLinkAreaCountMax = value.intValue()

      "business_start_page_title_length_max" ->
        businessStartPageTitleLengthMax = value.intValue()
      "business_start_page_message_length_max" ->
        businessStartPageMessageLengthMax = value.intValue()
      "business_chat_link_count_max" ->
        businessChatLinkCountMax = value.intValue()

      "test_mode",

      "notification_sound_count_max",
      "notification_sound_duration_max",
      "notification_sound_size_max",
      "localization_target",
      "language_pack_database_path",
      "ignore_file_names",
      "ignore_platform_restrictions",
      "is_emulator",
      "use_storage_optimizer",
      "storage_max_files_size",
      "storage_max_time_from_last_access",
      "use_pfs",
      "process_pinned_messages_as_mentions",
      "use_quick_ack",
      "connection_parameters",
      "notification_group_count_max",
      "notification_group_size_max",
      "ignore_default_disable_notification" -> {
        // Do nothing.
      }
      else -> {
        if (name.isNotEmpty() && name[0] == 'x') {
          // Custom property. Do nothing
        } else {
          if (isLoggingEnabled) {
            Log.w(Log.TAG_TDLIB_OPTIONS, "Unknown TDLib option: %s %s", name, value)
          }
        }
      }
    }

    val changed = previousValue?.equalsTo(value) ?: false
    return if (changed) {
      UpdateResult.VALUE_UPDATED
    } else if (previousValue == null) {
      UpdateResult.VALUE_SET
    } else {
      UpdateResult.VALUE_NOT_CHANGED
    }
  }

  private fun set (name: String, value: JsonValue) {
    when (name) {
      "force_inapp_update" ->
        forceInAppUpdate = value.boolValue()
      "youtube_pip" ->
        youtubePipDisabled = value.stringValue() == "disabled"

      "qr_login_camera" ->
        qrLoginCamera = value.boolValue()

      "stickers_emoji_suggest_only_api" ->
        stickersEmojiSuggestOnlyApi = value.boolValue()
      "emojies_animated_zoom" ->
        emojiesAnimatedZoom = maxOf(DEFAULT_EMOJIES_ANIMATED_ZOOM, clamp(value.numberValue())).toFloat()

      "dialog_filters_enabled" ->
        dialogFiltersEnabled = value.boolValue()
      "dialog_filters_tooltip" ->
        dialogFiltersTooltip = value.boolValue()

      "giveaway_gifts_purchase_available" ->
        giveawayGiftsPurchaseAvailable = value.boolValue()
      "stars_purchase_blocked" ->
        starsPurchaseBlocked = value.boolValue()
      "stargifts_blocked" ->
        starGiftsBlocked = value.boolValue()

      "premium_playmarket_direct_currency_list" ->
        premiumPlayMarketDirectCurrencyList = value.let {
          if (it is JsonValueArray) {
            it.values.map {
              it.stringValue()
            }.filter {
              it.isNotEmpty()
            }
          } else {
            listOf()
          }
        }

      "story_expire_period" ->
        storyExpirePeriod = value.longValue()
      "stories_posting" ->
        storyPostingEnabled = value.stringValue() == "enabled"

      "translations_manual_enabled" ->
        translationsManualEnabled = value.stringValue() == "enabled"
      "translations_auto_enabled" ->
        translationsAutoEnabled = value.stringValue() == "enabled"

      "starref_program_allowed" ->
        starRefProgramAllowed = value.boolValue()
      "starref_connect_allowed" ->
        starRefConnectAllowed = value.boolValue()

      "dev" ->
        isDeveloper = value.boolValue()

      "groupcall_video_participants_max" ->
        groupCallVideoParticipantsMax = value.intValue()
      "rtc_servers" ->
        rtcServers = value.let {
          if (it is JsonValueArray) {
            it.values.mapNotNull {
              if (it is JsonValueObject) {
                RtcServer(it)
              } else {
                null
              }
            }
          } else {
            listOf()
          }
        }

      "round_video_encoding" -> {
        if (value is JsonValueObject) {
          for (property in value.members) {
            if (property.value !is JsonValueNumber)
              continue
            val number = property.value.numberValue().roundToLong()
            when (property.key) {
              "diameter" ->
                suggestedVideoNoteLength = number
              "video_bitrate" ->
                suggestedVideoNoteVideoBitrate = number
              "audio_bitrate" ->
                suggestedVideoNoteAudioBitrate = number
              "max_size" ->
                videoNoteMaxSize = number
            }
          }
        }
      }

      "ios_disable_parallel_channel_reset",
      "small_queue_max_active_operations_count",
      "large_queue_max_active_operations_count",
      "lite_battery_min_pct",
      "lite_device_class",
      "lite_app_options",
      "test" -> {
        // Nothing to do
      }
      else -> {
        if (isLoggingEnabled) {
          Log.w(Log.TAG_TDLIB_OPTIONS, "Unknown server option: %s %s", name, value)
        }
      }
    }
  }

  @Retention(AnnotationRetention.SOURCE)
  @IntDef(
    UpdateResult.VALUE_NOT_CHANGED,
    UpdateResult.VALUE_SET,
    UpdateResult.VALUE_UPDATED
  )
  annotation class UpdateResult {
    companion object {
      const val VALUE_NOT_CHANGED: Int = 0
      const val VALUE_SET: Int = 1
      const val VALUE_UPDATED: Int = 2
    }
  }

  private val isLoggingEnabled: Boolean
    get() = Log.isEnabled(Log.TAG_TDLIB_OPTIONS)

  fun handleUpdate(update: UpdateOption): Int {
    val name = update.name
    if (isLoggingEnabled) {
      when (update.value.constructor) {
        OptionValueEmpty.CONSTRUCTOR -> {
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionEmpty %s", name)
        }
        OptionValueInteger.CONSTRUCTOR -> {
          val value = update.value.longValue()
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionInteger %s -> %d", name, value)
        }
        OptionValueString.CONSTRUCTOR -> {
          val value = update.value.stringValue()
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionString %s -> %s", name, value)
        }
        OptionValueBoolean.CONSTRUCTOR -> {
          val value = update.value.boolValue()
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionBoolean %s -> %s", name, value)
        }
        else -> {
          assertOptionValue_710db1a4()
          throw unsupported(update.value)
        }
      }
    }
    return set(name, update.value)
  }

  fun handleApplicationConfig(config: JsonValue) {
    if (config !is JsonValueObject) return
    for (member in config.members) {
      if (member.key.isNotEmpty()) {
        set(member.key, member.value)
      }
    }
  }
}

data class TdlibTime(
  val unixTime: Long,
  val receivedAt: Long
) {
  constructor() : this(0L, 0L)
  constructor(value: OptionValue) : this(value.longValue(), SystemClock.elapsedRealtime())

  fun currentTimeMillis(): Long {
    if (unixTime != 0L) {
      val elapsedMillis = SystemClock.elapsedRealtime() - receivedAt
      return TimeUnit.SECONDS.toMillis(unixTime) + elapsedMillis
    }
    return System.currentTimeMillis()
  }
}

@Retention(AnnotationRetention.SOURCE)
@IntDef(value = [
  GiftPremiumFrom.INPUT_FIELD,
  GiftPremiumFrom.ATTACHMENT_MENU
], flag = true)
annotation class GiftPremiumFrom {
  companion object {
    const val INPUT_FIELD: Int = 1
    const val ATTACHMENT_MENU: Int = 1 shl 1
  }
}

data class RtcServer(
  val host: String,
  val port: Int,
  val username: String,
  val password: String
) {
  constructor(json: JsonValueObject) : this(json.asMap()!!)
  private constructor(map: Map<String, JsonValue>) : this(
    map["host"]?.stringValue() ?: "",
    map["port"]?.intValue() ?: 0,
    map["username"]?.stringValue() ?: "",
    map["password"]?.stringValue() ?: ""
  )
}

const val DEFAULT_EMOJIES_ANIMATED_ZOOM = .75