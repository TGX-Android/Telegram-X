/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

import me.vkryl.core.CurrencyUtils;

final class PremiumCatalog {
  private PremiumCatalog () { }

  static int featureTitle (TdApi.PremiumFeature type) {
    return switch (type.getConstructor()) {
      case TdApi.PremiumFeatureIncreasedLimits.CONSTRUCTOR -> R.string.TgxPremiumFeatureIncreasedLimits;
      case TdApi.PremiumFeatureIncreasedUploadFileSize.CONSTRUCTOR -> R.string.TgxPremiumFeatureIncreasedUploadFileSize;
      case TdApi.PremiumFeatureImprovedDownloadSpeed.CONSTRUCTOR -> R.string.TgxPremiumFeatureImprovedDownloadSpeed;
      case TdApi.PremiumFeatureVoiceRecognition.CONSTRUCTOR -> R.string.TgxPremiumFeatureVoiceRecognition;
      case TdApi.PremiumFeatureDisabledAds.CONSTRUCTOR -> R.string.TgxPremiumFeatureDisabledAds;
      case TdApi.PremiumFeatureUniqueReactions.CONSTRUCTOR -> R.string.TgxPremiumFeatureUniqueReactions;
      case TdApi.PremiumFeatureUniqueStickers.CONSTRUCTOR -> R.string.TgxPremiumFeatureUniqueStickers;
      case TdApi.PremiumFeatureCustomEmoji.CONSTRUCTOR -> R.string.TgxPremiumFeatureCustomEmoji;
      case TdApi.PremiumFeatureAdvancedChatManagement.CONSTRUCTOR -> R.string.TgxPremiumFeatureAdvancedChatManagement;
      case TdApi.PremiumFeatureProfileBadge.CONSTRUCTOR -> R.string.TgxPremiumFeatureProfileBadge;
      case TdApi.PremiumFeatureEmojiStatus.CONSTRUCTOR -> R.string.TgxPremiumFeatureEmojiStatus;
      case TdApi.PremiumFeatureAnimatedProfilePhoto.CONSTRUCTOR -> R.string.TgxPremiumFeatureAnimatedProfilePhoto;
      case TdApi.PremiumFeatureForumTopicIcon.CONSTRUCTOR -> R.string.TgxPremiumFeatureForumTopicIcon;
      case TdApi.PremiumFeatureAppIcons.CONSTRUCTOR -> R.string.TgxPremiumFeatureAppIcons;
      case TdApi.PremiumFeatureRealTimeChatTranslation.CONSTRUCTOR -> R.string.TgxPremiumFeatureRealTimeChatTranslation;
      case TdApi.PremiumFeatureUpgradedStories.CONSTRUCTOR -> R.string.TgxPremiumFeatureUpgradedStories;
      case TdApi.PremiumFeatureChatBoost.CONSTRUCTOR -> R.string.TgxPremiumFeatureChatBoost;
      case TdApi.PremiumFeatureAccentColor.CONSTRUCTOR -> R.string.TgxPremiumFeatureAccentColor;
      case TdApi.PremiumFeatureBackgroundForBoth.CONSTRUCTOR -> R.string.TgxPremiumFeatureBackgroundForBoth;
      case TdApi.PremiumFeatureSavedMessagesTags.CONSTRUCTOR -> R.string.TgxPremiumFeatureSavedMessagesTags;
      case TdApi.PremiumFeatureMessagePrivacy.CONSTRUCTOR -> R.string.TgxPremiumFeatureMessagePrivacy;
      case TdApi.PremiumFeatureLastSeenTimes.CONSTRUCTOR -> R.string.TgxPremiumFeatureLastSeenTimes;
      case TdApi.PremiumFeatureBusiness.CONSTRUCTOR -> R.string.TgxPremiumFeatureBusiness;
      case TdApi.PremiumFeatureMessageEffects.CONSTRUCTOR -> R.string.TgxPremiumFeatureMessageEffects;
      case TdApi.PremiumFeatureChecklists.CONSTRUCTOR -> R.string.TgxPremiumFeatureChecklists;
      case TdApi.PremiumFeaturePaidMessages.CONSTRUCTOR -> R.string.TgxPremiumFeaturePaidMessages;
      case TdApi.PremiumFeatureProtectPrivateChatContent.CONSTRUCTOR -> R.string.TgxPremiumFeatureProtectPrivateChatContent;
      case TdApi.PremiumFeatureTextComposition.CONSTRUCTOR -> R.string.TgxPremiumFeatureTextComposition;
      case TdApi.PremiumFeatureRichMessages.CONSTRUCTOR -> R.string.TgxPremiumFeatureRichMessages;
      default -> R.string.TgxPremiumUnknownFeature;
    };
  }

  static int featureDescription (TdApi.PremiumFeature type) {
    return switch (type.getConstructor()) {
      case TdApi.PremiumFeatureIncreasedLimits.CONSTRUCTOR -> R.string.TgxPremiumDetailIncreasedLimits;
      case TdApi.PremiumFeatureIncreasedUploadFileSize.CONSTRUCTOR -> R.string.TgxPremiumDetailIncreasedUploadFileSize;
      case TdApi.PremiumFeatureImprovedDownloadSpeed.CONSTRUCTOR -> R.string.TgxPremiumDetailImprovedDownloadSpeed;
      case TdApi.PremiumFeatureVoiceRecognition.CONSTRUCTOR -> R.string.TgxPremiumDetailVoiceRecognition;
      case TdApi.PremiumFeatureDisabledAds.CONSTRUCTOR -> R.string.TgxPremiumDetailDisabledAds;
      case TdApi.PremiumFeatureUniqueReactions.CONSTRUCTOR -> R.string.TgxPremiumDetailUniqueReactions;
      case TdApi.PremiumFeatureUniqueStickers.CONSTRUCTOR -> R.string.TgxPremiumDetailUniqueStickers;
      case TdApi.PremiumFeatureCustomEmoji.CONSTRUCTOR -> R.string.TgxPremiumDetailCustomEmoji;
      case TdApi.PremiumFeatureAdvancedChatManagement.CONSTRUCTOR -> R.string.TgxPremiumDetailAdvancedChatManagement;
      case TdApi.PremiumFeatureProfileBadge.CONSTRUCTOR -> R.string.TgxPremiumDetailProfileBadge;
      case TdApi.PremiumFeatureEmojiStatus.CONSTRUCTOR -> R.string.TgxPremiumDetailEmojiStatus;
      case TdApi.PremiumFeatureAnimatedProfilePhoto.CONSTRUCTOR -> R.string.TgxPremiumDetailAnimatedProfilePhoto;
      case TdApi.PremiumFeatureForumTopicIcon.CONSTRUCTOR -> R.string.TgxPremiumDetailForumTopicIcon;
      case TdApi.PremiumFeatureAppIcons.CONSTRUCTOR -> R.string.TgxPremiumDetailAppIcons;
      case TdApi.PremiumFeatureRealTimeChatTranslation.CONSTRUCTOR -> R.string.TgxPremiumDetailRealTimeChatTranslation;
      case TdApi.PremiumFeatureUpgradedStories.CONSTRUCTOR -> R.string.TgxPremiumDetailUpgradedStories;
      case TdApi.PremiumFeatureChatBoost.CONSTRUCTOR -> R.string.TgxPremiumDetailChatBoost;
      case TdApi.PremiumFeatureAccentColor.CONSTRUCTOR -> R.string.TgxPremiumDetailAccentColor;
      case TdApi.PremiumFeatureBackgroundForBoth.CONSTRUCTOR -> R.string.TgxPremiumDetailBackgroundForBoth;
      case TdApi.PremiumFeatureSavedMessagesTags.CONSTRUCTOR -> R.string.TgxPremiumDetailSavedMessagesTags;
      case TdApi.PremiumFeatureMessagePrivacy.CONSTRUCTOR -> R.string.TgxPremiumDetailMessagePrivacy;
      case TdApi.PremiumFeatureLastSeenTimes.CONSTRUCTOR -> R.string.TgxPremiumDetailLastSeenTimes;
      case TdApi.PremiumFeatureBusiness.CONSTRUCTOR -> R.string.TgxPremiumDetailBusiness;
      case TdApi.PremiumFeatureMessageEffects.CONSTRUCTOR -> R.string.TgxPremiumDetailMessageEffects;
      case TdApi.PremiumFeatureChecklists.CONSTRUCTOR -> R.string.TgxPremiumDetailChecklists;
      case TdApi.PremiumFeaturePaidMessages.CONSTRUCTOR -> R.string.TgxPremiumDetailPaidMessages;
      case TdApi.PremiumFeatureProtectPrivateChatContent.CONSTRUCTOR -> R.string.TgxPremiumDetailProtectPrivateChatContent;
      case TdApi.PremiumFeatureTextComposition.CONSTRUCTOR -> R.string.TgxPremiumDetailTextComposition;
      case TdApi.PremiumFeatureRichMessages.CONSTRUCTOR -> R.string.TgxPremiumDetailRichMessages;
      default -> 0;
    };
  }

  static int limitTitle (TdApi.PremiumLimitType type) {
    return switch (type.getConstructor()) {
      case TdApi.PremiumLimitTypeSupergroupCount.CONSTRUCTOR -> R.string.TgxPremiumLimitSupergroupCount;
      case TdApi.PremiumLimitTypePinnedChatCount.CONSTRUCTOR -> R.string.TgxPremiumLimitPinnedChatCount;
      case TdApi.PremiumLimitTypeCreatedPublicChatCount.CONSTRUCTOR -> R.string.TgxPremiumLimitCreatedPublicChatCount;
      case TdApi.PremiumLimitTypeSavedAnimationCount.CONSTRUCTOR -> R.string.TgxPremiumLimitSavedAnimationCount;
      case TdApi.PremiumLimitTypeFavoriteStickerCount.CONSTRUCTOR -> R.string.TgxPremiumLimitFavoriteStickerCount;
      case TdApi.PremiumLimitTypeChatFolderCount.CONSTRUCTOR -> R.string.TgxPremiumLimitChatFolderCount;
      case TdApi.PremiumLimitTypeChatFolderChosenChatCount.CONSTRUCTOR -> R.string.TgxPremiumLimitChatFolderChosenChatCount;
      case TdApi.PremiumLimitTypePinnedArchivedChatCount.CONSTRUCTOR -> R.string.TgxPremiumLimitPinnedArchivedChatCount;
      case TdApi.PremiumLimitTypePinnedSavedMessagesTopicCount.CONSTRUCTOR -> R.string.TgxPremiumLimitPinnedSavedMessagesTopicCount;
      case TdApi.PremiumLimitTypeMessageTextLength.CONSTRUCTOR -> R.string.TgxPremiumLimitMessageTextLength;
      case TdApi.PremiumLimitTypeCaptionLength.CONSTRUCTOR -> R.string.TgxPremiumLimitCaptionLength;
      case TdApi.PremiumLimitTypeBioLength.CONSTRUCTOR -> R.string.TgxPremiumLimitBioLength;
      case TdApi.PremiumLimitTypeChatFolderInviteLinkCount.CONSTRUCTOR -> R.string.TgxPremiumLimitChatFolderInviteLinkCount;
      case TdApi.PremiumLimitTypeShareableChatFolderCount.CONSTRUCTOR -> R.string.TgxPremiumLimitShareableChatFolderCount;
      case TdApi.PremiumLimitTypeActiveStoryCount.CONSTRUCTOR -> R.string.TgxPremiumLimitActiveStoryCount;
      case TdApi.PremiumLimitTypeWeeklyPostedStoryCount.CONSTRUCTOR -> R.string.TgxPremiumLimitWeeklyPostedStoryCount;
      case TdApi.PremiumLimitTypeMonthlyPostedStoryCount.CONSTRUCTOR -> R.string.TgxPremiumLimitMonthlyPostedStoryCount;
      case TdApi.PremiumLimitTypeStoryCaptionLength.CONSTRUCTOR -> R.string.TgxPremiumLimitStoryCaptionLength;
      case TdApi.PremiumLimitTypeStorySuggestedReactionAreaCount.CONSTRUCTOR -> R.string.TgxPremiumLimitStorySuggestedReactionAreaCount;
      case TdApi.PremiumLimitTypeSimilarChatCount.CONSTRUCTOR -> R.string.TgxPremiumLimitSimilarChatCount;
      case TdApi.PremiumLimitTypeOwnedBotCount.CONSTRUCTOR -> R.string.TgxPremiumLimitOwnedBotCount;
      case TdApi.PremiumLimitTypeCustomTextCompositionStyleCount.CONSTRUCTOR -> R.string.TgxPremiumLimitCustomTextCompositionStyleCount;
      default -> R.string.TgxPremiumUnknownLimit;
    };
  }

  static boolean isPaymentLink (TdApi.InternalLinkType link) {
    if (link instanceof TdApi.InternalLinkTypeBotStart bot) {
      return bot.botUsername != null && bot.botUsername.matches("[A-Za-z0-9_]+");
    }
    if (link instanceof TdApi.InternalLinkTypeInvoice invoice) {
      return invoice.invoiceName != null && !invoice.invoiceName.isEmpty();
    }
    return false;
  }

  static boolean isDisplayablePlan (TdApi.PremiumStatePaymentOption plan) {
    return plan != null && plan.paymentOption != null && plan.paymentOption.monthCount > 0 &&
      plan.paymentOption.amount > 0 && currencyExponent(plan.paymentOption.currency) >= 0;
  }

  static int currencyExponent (String code) {
    if (code == null || !code.matches("[A-Z]{3}")) {
      return -1;
    }
    CurrencyUtils.Currency known = CurrencyUtils.MAP.get(code);
    if (known != null) {
      return known.exp;
    }
    try {
      return Currency.getInstance(code).getDefaultFractionDigits();
    } catch (IllegalArgumentException ignored) {
      return -1;
    }
  }

  static String formatAmount (TdApi.PremiumPaymentOption option, Locale locale) {
    int fractionDigits = currencyExponent(option.currency);
    NumberFormat format = NumberFormat.getNumberInstance(locale);
    format.setMinimumFractionDigits(fractionDigits);
    format.setMaximumFractionDigits(fractionDigits);
    return format.format(BigDecimal.valueOf(option.amount, fractionDigits)) + " " + option.currency;
  }

  static boolean canOpenPlan (TdApi.PremiumStatePaymentOption plan, boolean premium) {
    return isDisplayablePlan(plan) && !plan.isCurrent &&
      (!premium || plan.isUpgrade) && isPaymentLink(plan.paymentOption.paymentLink);
  }

  static boolean sameOffer (TdApi.PremiumStatePaymentOption expected, TdApi.PremiumStatePaymentOption current) {
    if (expected == null || current == null || expected.paymentOption == null || current.paymentOption == null) {
      return false;
    }
    TdApi.PremiumPaymentOption a = expected.paymentOption;
    TdApi.PremiumPaymentOption b = current.paymentOption;
    return a.monthCount == b.monthCount && a.amount == b.amount && Objects.equals(a.currency, b.currency) &&
      expected.isCurrent == current.isCurrent && expected.isUpgrade == current.isUpgrade &&
      equalPaymentLinks(a.paymentLink, b.paymentLink);
  }

  private static boolean equalPaymentLinks (TdApi.InternalLinkType first, TdApi.InternalLinkType second) {
    if (first instanceof TdApi.InternalLinkTypeBotStart a && second instanceof TdApi.InternalLinkTypeBotStart b) {
      return Objects.equals(a.botUsername, b.botUsername) &&
        Objects.equals(a.startParameter, b.startParameter) && a.autostart == b.autostart;
    }
    if (first instanceof TdApi.InternalLinkTypeInvoice a && second instanceof TdApi.InternalLinkTypeInvoice b) {
      return Objects.equals(a.invoiceName, b.invoiceName);
    }
    return false;
  }
}
