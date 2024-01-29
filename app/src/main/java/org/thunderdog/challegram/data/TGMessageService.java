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
 * File created on 02/09/2022, 19:31.
 */

package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.TextColorSet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public final class TGMessageService extends TGMessageServiceImpl {
  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageContactRegistered contactRegistered) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.NotificationContactJoined,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageScreenshotTaken screenshotTaken) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(R.string.YouTookAScreenshot);
      } else {
        return getText(
          R.string.XTookAScreenshot,
          new SenderArgument(sender, isUserChat())
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageGiftedPremium giftedPremium) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getPlural(
          R.string.YouGiftedPremium,
          giftedPremium.monthCount,
          new BoldArgument(CurrencyUtils.buildAmount(giftedPremium.currency, giftedPremium.amount))
        );
      } else {
        return getPlural(
          R.string.GiftedPremium,
          giftedPremium.monthCount,
          new SenderArgument(sender, isUserChat()),
          new BoldArgument(CurrencyUtils.buildAmount(giftedPremium.currency, giftedPremium.amount))
        );
      }
    });
    // TODO design for giftedPremium.sticker
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePremiumGiftCode premiumGiftCode) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getPlural(
          R.string.YouGiftedPremiumCode,
          premiumGiftCode.monthCount
        );
      } else {
        return getPlural(
          R.string.GiftedPremiumCode,
          premiumGiftCode.monthCount,
          new SenderArgument(new TdlibSender(tdlib, msg.chatId, premiumGiftCode.creatorId), isUserChat())
        );
      }
    });
    // TODO design for premiumGiftCode.sticker
    // TODO show details of the gift code
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePremiumGiveawayCreated giveawayCreated) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.BoostingGiveawayJustStarted,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePremiumGiveawayCompleted giveawayCompleted) {
    super(context, msg);
    setTextCreator(() ->
      getPlural(
        R.string.BoostingGiveawayServiceWinnersSelected,
        giveawayCompleted.winnerCount
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatSetTheme setTheme) {
    super(context, msg);
    setTextCreator(() -> {
      if (StringUtils.isEmpty(setTheme.themeName)) {
        if (msg.isOutgoing) {
          return getText(
            R.string.ChatThemeDisabled_outgoing
          );
        } else {
          return getText(
            R.string.ChatThemeDisabled,
            new SenderArgument(sender, isUserChat())
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            R.string.ChatThemeSet_outgoing,
            new BoldArgument(setTheme.themeName)
          );
        } else {
          return getText(
            R.string.ChatThemeSet,
            new SenderArgument(sender, isUserChat()),
            new BoldArgument(setTheme.themeName)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredPhoto expiredPhoto) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.AttachPhotoExpired)
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredVideo expiredVideo) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.AttachVideoExpired)
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredVoiceNote expiredVoiceNote) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.AttachVoiceNoteExpired)
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredVideoNote expiredVideoNote) {
    super(context, msg);
    setTextCreator(() ->
      getText(R.string.AttachVideoNoteExpired)
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageProximityAlertTriggered proximityAlertTriggered) {
    super(context, msg);
    TdlibSender travelerSender = new TdlibSender(tdlib(), msg.chatId, proximityAlertTriggered.travelerId);
    TdlibSender watcherSender = new TdlibSender(tdlib(), msg.chatId, proximityAlertTriggered.watcherId);
    setTextCreator(() -> {
      boolean inKilometers = proximityAlertTriggered.distance >= 1000;
      int distance = inKilometers ?
        (proximityAlertTriggered.distance / 1000) :
        proximityAlertTriggered.distance;
      if (travelerSender.isSelf()) {
        return getPlural(
          inKilometers ?
            R.string.ProximityAlertYouKM :
            R.string.ProximityAlertYouM,
          distance,
          new SenderArgument(watcherSender, isUserChat())
        );
      } else if (watcherSender.isSelf()) {
        return getPlural(
          inKilometers ?
            R.string.ProximityAlertOtherKM :
            R.string.ProximityAlertOtherM,
          distance,
          new SenderArgument(travelerSender, isUserChat())
        );
      } else {
        return getPlural(
          inKilometers ?
            R.string.ProximityAlertKM :
            R.string.ProximityAlertM,
          distance,
          new SenderArgument(travelerSender), // isUserChat() is always false in such cases
          new SenderArgument(watcherSender)
        );
      }
    });
  }

  private static final int MAX_PINNED_MESSAGE_PREVIEW_LENGTH = 40;

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePinMessage pinMessage) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(R.string.YouPinnedMessage);
      } else {
        return getText(
          R.string.NotificationActionPinnedNoTextChannel,
          new SenderArgument(sender)
        );
      }
    });
    setDisplayMessage(msg.chatId, pinMessage.messageId, message -> {
      setTextCreator(new ServiceMessageCreator() {
        @Override
        public FormattedText createText () {
          TdApi.FormattedText formattedText = Td.textOrCaption(message.content);
          if (!Td.isEmpty(formattedText)) {
            return getText(
              R.string.ActionPinnedText,
              new SenderArgument(sender, isUserChat()),
              new MessageArgument(message, Td.ellipsize(formattedText, MAX_PINNED_MESSAGE_PREVIEW_LENGTH))
            );
          }
          @StringRes int staticResId;
          switch (message.content.getConstructor()) {
            case TdApi.MessageGame.CONSTRUCTOR:
              return getText(
                R.string.ActionPinnedText,
                new SenderArgument(sender),
                new GameArgument(message)
              );
            case TdApi.MessageInvoice.CONSTRUCTOR:
              return getText(
                R.string.ActionPinnedText,
                new SenderArgument(sender),
                new InvoiceArgument(message)
              );
            case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
              return getText(
                R.string.ActionPinnedText,
                new SenderArgument(sender),
                new MessageArgument(message, Td.ellipsize(
                  new TdApi.FormattedText(((TdApi.MessageCustomServiceAction) message.content).text, null),
                  MAX_PINNED_MESSAGE_PREVIEW_LENGTH
                ))
              );
            case TdApi.MessageAnimation.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedGif;
              break;
            case TdApi.MessageAudio.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedMusic;
              break;
            case TdApi.MessageDocument.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedFile;
              break;
            case TdApi.MessagePhoto.CONSTRUCTOR:
            case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedPhoto;
              break;
            case TdApi.MessageVideo.CONSTRUCTOR:
            case TdApi.MessageExpiredVideo.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedVideo;
              break;
            case TdApi.MessageVoiceNote.CONSTRUCTOR:
            case TdApi.MessageExpiredVoiceNote.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedVoice;
              break;
            case TdApi.MessageVideoNote.CONSTRUCTOR:
            case TdApi.MessageExpiredVideoNote.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedRound;
              break;
            case TdApi.MessageSticker.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedSticker;
              break;
            case TdApi.MessagePoll.CONSTRUCTOR:
              staticResId = ((TdApi.MessagePoll) message.content).poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR ?
                R.string.ActionPinnedQuiz :
                R.string.ActionPinnedPoll;
              break;
            case TdApi.MessageLocation.CONSTRUCTOR:
              staticResId = ((TdApi.MessageLocation) message.content).livePeriod > 0 ?
                R.string.ActionPinnedGeoLive :
                R.string.ActionPinnedGeo;
              break;
            case TdApi.MessageVenue.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedGeo;
              break;
            case TdApi.MessageContact.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedContact;
              break;
            case TdApi.MessageStory.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedStory;
              break;
            case TdApi.MessageDice.CONSTRUCTOR: // TODO?
              // unreachable
            case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
            case TdApi.MessageText.CONSTRUCTOR:
              // cannot be pinned
            case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
            case TdApi.MessageCall.CONSTRUCTOR:
            case TdApi.MessageChatAddMembers.CONSTRUCTOR:
            case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
            case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
            case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
            case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
            case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
            case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
            case TdApi.MessageChatSetTheme.CONSTRUCTOR:
            case TdApi.MessageChatSetBackground.CONSTRUCTOR:
            case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR:
            case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
            case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
            case TdApi.MessageContactRegistered.CONSTRUCTOR:
            case TdApi.MessageGameScore.CONSTRUCTOR:
            case TdApi.MessageGiftedPremium.CONSTRUCTOR:
            case TdApi.MessagePremiumGiftCode.CONSTRUCTOR:
            case TdApi.MessagePremiumGiveawayCreated.CONSTRUCTOR:
            case TdApi.MessagePremiumGiveawayCompleted.CONSTRUCTOR:
            case TdApi.MessagePremiumGiveawayWinners.CONSTRUCTOR:
            case TdApi.MessagePremiumGiveaway.CONSTRUCTOR:
            case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
            case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
            case TdApi.MessagePassportDataSent.CONSTRUCTOR:
            case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
            case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
            case TdApi.MessagePinMessage.CONSTRUCTOR:
            case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR:
            case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
            case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
            case TdApi.MessageUnsupported.CONSTRUCTOR:
            case TdApi.MessageVideoChatEnded.CONSTRUCTOR:
            case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
            case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
            case TdApi.MessageWebAppDataReceived.CONSTRUCTOR:
            case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
            case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
            case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
            case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
            case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
            case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
            case TdApi.MessageUsersShared.CONSTRUCTOR:
            case TdApi.MessageChatShared.CONSTRUCTOR:
            case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR:
              staticResId = R.string.ActionPinnedNoText;
              break;
            default:
              Td.assertMessageContent_cfe6660a();
              throw Td.unsupported(message.content);
          }
          String format = Lang.getString(staticResId);
          int startIndex = format.indexOf("**");
          int endIndex = startIndex != -1 ? format.indexOf("**", startIndex + 2) : -1;
          if (startIndex != -1 && endIndex != -1) {
            String arg = format.substring(startIndex + 2, endIndex);
            format = format.substring(0, startIndex) + "%2$s" + format.substring(endIndex + 2);
            return formatText(
              format,
              new SenderArgument(sender, isUserChat()),
              new MessageArgument(message, new TdApi.FormattedText(arg, null))
            );
          } else {
            // This might happen if language pack doesn't have **text**.
            // Displaying string as is without link to the message
            return new FormattedText(format);
          }
        }

        @Override
        public boolean ignoreNewLines () {
          return true;
        }
      });
      return true;
    });
  }

  // Group & Channel Messages

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageBasicGroupChatCreate basicGroupCreate) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) { // should never be true
        return getText(
          R.string.channel_create_somebody,
          new BoldArgument(basicGroupCreate.title)
        );
      } else if (msg.isOutgoing) {
        return getText(
          R.string.group_create_you,
          new BoldArgument(basicGroupCreate.title)
        );
      } else {
        return getText(
          R.string.group_created,
          new SenderArgument(sender),
          new BoldArgument(basicGroupCreate.title)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageSupergroupChatCreate supergroupCreate) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(
          R.string.channel_create_somebody,
          new BoldArgument(supergroupCreate.title)
        );
      } else if (msg.isOutgoing) {
        return getText(
          R.string.group_create_you,
          new BoldArgument(supergroupCreate.title)
        );
      } else {
        return getText(
          R.string.group_created,
          new SenderArgument(sender),
          new BoldArgument(supergroupCreate.title)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangeTitle changeTitle) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(
          R.string.ChannelRenamed,
          new BoldArgument(changeTitle.title)
        );
      } else if (msg.isOutgoing) {
        return getText(
          R.string.group_title_changed_you,
          new BoldArgument(changeTitle.title)
        );
      } else {
        return getText(
          R.string.group_title_changed,
          new SenderArgument(sender),
          new BoldArgument(changeTitle.title)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangePhoto changePhoto) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.ActionChannelChangedPhoto);
      } else if (msg.isOutgoing) {
        return getText(R.string.group_photo_changed_you);
      } else {
        return getText(
          R.string.group_photo_changed,
          new SenderArgument(sender)
        );
      }
    });
    setDisplayChatPhoto(changePhoto.photo);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatDeletePhoto deletePhoto) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        return getText(R.string.ActionChannelRemovedPhoto);
      } else if (msg.isOutgoing) {
        return getText(R.string.group_photo_deleted_you);
      } else {
        return getText(
          R.string.group_photo_deleted,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeTo upgradeToSupergroup) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        upgradeToSupergroup.supergroupId != 0 ?
          R.string.GroupUpgradedTo :
          R.string.GroupUpgraded
      )
    );
    if (upgradeToSupergroup.supergroupId != 0) {
      setOnClickListener(() ->
        tdlib.ui().openSupergroupChat(controller(), upgradeToSupergroup.supergroupId, new TdlibUi.ChatOpenParameters().urlOpenParameters(openParameters()))
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeFrom upgradeFromBasicGroup) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        upgradeFromBasicGroup.basicGroupId != 0 ?
          R.string.GroupUpgradedFrom :
          R.string.GroupUpgraded
      )
    );
    if (upgradeFromBasicGroup.basicGroupId != 0) {
      setOnClickListener(() ->
        tdlib.ui().openBasicGroupChat(controller(), upgradeFromBasicGroup.basicGroupId, new TdlibUi.ChatOpenParameters().urlOpenParameters(openParameters()))
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatJoinByLink joinByLink) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.YouJoinedByLink :
            R.string.group_user_join_by_link_self
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.XJoinedByLink :
            R.string.group_user_join_by_link,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatJoinByRequest joinByRequest) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.YouAcceptedToChannel :
            R.string.YouAcceptedToGroup
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.XAcceptedToChannel :
            R.string.XAcceptedToGroup,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatAddMembers addMembers) {
    super(context, msg);
    TdlibSender[] addedMembers = TdlibSender.valueOfUserIds(tdlib(), msg.chatId, addMembers.memberUserIds);
    setTextCreator(() -> {
      TdlibSender targetMember =
        addedMembers.length == 1 ?
          addedMembers[0] :
          null;
      if (sender.isSelf()) {
        if (sender.isSameSender(targetMember)) {
          return getText(
            msg.isChannelPost ?
              R.string.channel_user_add_self :
              R.string.group_user_add_self
          );
        } else {
          return getText(
            R.string.group_user_self_added,
            addedMembers.length == 1 ?
              new SenderArgument(addedMembers[0]) :
              new SenderListArgument(addedMembers)
          );
        }
      } else {
        if (sender.isSameSender(targetMember)) {
          return getText(
            msg.isChannelPost ?
              R.string.channel_user_add :
              R.string.group_user_add,
            new SenderArgument(sender)
          );
        } else if (targetMember != null && targetMember.isSelf()) {
          return getText(
            R.string.group_user_added_self,
            new SenderArgument(sender)
          );
        } else {
          return getText(
            R.string.group_user_added,
            new SenderArgument(sender),
            addedMembers.length == 1 ?
              new SenderArgument(addedMembers[0]) :
              new SenderListArgument(addedMembers)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatDeleteMember deleteMember) {
    super(context, msg);
    TdlibSender targetSender = new TdlibSender(tdlib, msg.chatId, new TdApi.MessageSenderUser(deleteMember.userId));
    setTextCreator(() -> {
      if (sender.isSameSender(targetSender)) {
        if (sender.isSelf()) {
          return getText(
            msg.isChannelPost ?
              R.string.channel_user_remove_self :
              R.string.group_user_remove_self
          );
        } else {
          return getText(
            msg.isChannelPost ?
              R.string.channel_user_remove :
              R.string.group_user_remove,
            new SenderArgument(sender)
          );
        }
      } else {
        if (sender.isSelf()) {
          return getText(
            R.string.group_user_self_removed,
            new SenderArgument(targetSender)
          );
        } else if (targetSender.isSelf()) {
          return getText(
            R.string.group_user_removed_self,
            new SenderArgument(sender)
          );
        } else {
          return getText(
            R.string.group_user_removed,
            new SenderArgument(sender),
            new SenderArgument(targetSender)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageBotWriteAccessAllowed botWriteAccessAllowed) {
    super(context, msg);
    switch (botWriteAccessAllowed.reason.getConstructor()) {
      case TdApi.BotWriteAccessAllowReasonConnectedWebsite.CONSTRUCTOR: {
        TdApi.BotWriteAccessAllowReasonConnectedWebsite connectedWebsite = (TdApi.BotWriteAccessAllowReasonConnectedWebsite) botWriteAccessAllowed.reason;
        setTextCreator(() ->
          getText(
            R.string.BotWebsiteAllowed,
            new BoldArgument(connectedWebsite.domainName)
          )
        );
        break;
      }
      case TdApi.BotWriteAccessAllowReasonAddedToAttachmentMenu.CONSTRUCTOR: {
        setTextCreator(() ->
          getText(R.string.BotAttachAllowed)
        );
        break;
      }
      case TdApi.BotWriteAccessAllowReasonLaunchedWebApp.CONSTRUCTOR: {
        TdApi.BotWriteAccessAllowReasonLaunchedWebApp launchedWebApp = (TdApi.BotWriteAccessAllowReasonLaunchedWebApp) botWriteAccessAllowed.reason;
        setTextCreator(() ->
          getText(
            R.string.BotAppAllowed,
            new BoldArgument(launchedWebApp.webApp.title)
          )
        );
        break;
      }
      case TdApi.BotWriteAccessAllowReasonAcceptedRequest.CONSTRUCTOR: {
        setTextCreator(() ->
          getText(R.string.BotWebappAllowed)
        );
        break;
      }
      default: {
        Td.assertBotWriteAccessAllowReason_d7597302();
        throw Td.unsupported(botWriteAccessAllowed.reason);
      }
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatSetMessageAutoDeleteTime setMessageAutoDeleteTime) {
    super(context, msg);
    setTextCreator(() -> {
      boolean isUserChat = ChatId.isUserChat(msg.chatId);
      if (setMessageAutoDeleteTime.messageAutoDeleteTime == 0) {
        if (msg.isOutgoing) {
          return getText(
            isUserChat ?
              R.string.YouDisabledTimer :
            msg.isChannelPost ?
              R.string.YouDisabledAutoDeletePosts :
              R.string.YouDisabledAutoDelete
          );
        } else {
          return getText(
            isUserChat ?
              R.string.XDisabledTimer :
            msg.isChannelPost ?
              R.string.XDisabledAutoDeletePosts :
              R.string.XDisabledAutoDelete,
            new SenderArgument(sender, true)
          );
        }
      } else if (isUserChat) {
        if (msg.isOutgoing) {
          return getDuration(
            R.string.YouSetTimerSeconds, R.string.YouSetTimerMinutes, R.string.YouSetTimerHours, R.string.YouSetTimerDays, R.string.YouSetTimerWeeks, R.string.YouSetTimerMonths,
            setMessageAutoDeleteTime.messageAutoDeleteTime, TimeUnit.SECONDS
          );
        } else {
          return getDuration(
            R.string.XSetTimerSeconds, R.string.XSetTimerMinutes, R.string.XSetTimerHours, R.string.XSetTimerDays, R.string.XSetTimerWeeks, R.string.XSetTimerMonths,
            setMessageAutoDeleteTime.messageAutoDeleteTime, TimeUnit.SECONDS,
            new SenderArgument(sender, true)
          );
        }
      } else if (msg.isChannelPost) {
        if (msg.isOutgoing) {
          return getDuration(
            R.string.YouSetAutoDeletePostsSeconds, R.string.YouSetAutoDeletePostsMinutes, R.string.YouSetAutoDeletePostsHours, R.string.YouSetAutoDeletePostsDays, R.string.YouSetAutoDeletePostsWeeks, R.string.YouSetAutoDeletePostsMonths,
            setMessageAutoDeleteTime.messageAutoDeleteTime, TimeUnit.SECONDS
          );
        } else {
          return getDuration(
            R.string.XSetAutoDeletePostsSeconds, R.string.XSetAutoDeletePostsMinutes, R.string.XSetAutoDeletePostsHours, R.string.XSetAutoDeletePostsDays, R.string.XSetAutoDeletePostsWeeks, R.string.XSetAutoDeletePostsMonths,
            setMessageAutoDeleteTime.messageAutoDeleteTime, TimeUnit.SECONDS,
            new SenderArgument(sender, true)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getDuration(
            R.string.YouSetAutoDeleteSeconds, R.string.YouSetAutoDeleteMinutes, R.string.YouSetAutoDeleteHours, R.string.YouSetAutoDeleteDays, R.string.YouSetAutoDeleteWeeks, R.string.YouSetAutoDeleteMonths,
            setMessageAutoDeleteTime.messageAutoDeleteTime, TimeUnit.SECONDS
          );
        } else {
          return getDuration(
            R.string.XSetAutoDeleteSeconds, R.string.XSetAutoDeleteMinutes, R.string.XSetAutoDeleteHours, R.string.XSetAutoDeleteDays, R.string.XSetAutoDeleteWeeks, R.string.XSetAutoDeleteMonths,
            setMessageAutoDeleteTime.messageAutoDeleteTime, TimeUnit.SECONDS,
            new SenderArgument(sender, true)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageGameScore gameScore) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getPlural(
          R.string.game_ActionYouScored,
          gameScore.score
        );
      } else {
        return getPlural(
          R.string.game_ActionUserScored,
          gameScore.score,
          new SenderArgument(sender)
        );
      }
    });
    if (gameScore.gameMessageId != 0) {
      setDisplayMessage(msg.chatId, gameScore.gameMessageId, (message) -> {
        if (!Td.isGame(message.content)) {
          return false;
        }
        setTextCreator(() -> {
          if (msg.isOutgoing) {
            return getPlural(
              R.string.game_ActionYouScoredInGame,
              gameScore.score,
              new GameArgument(message)
            );
          } else {
            return getPlural(
              R.string.game_ActionUserScoredInGame,
              gameScore.score,
              new SenderArgument(sender),
              new GameArgument(message)
            );
          }
        });
        return true;
      });
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePaymentSuccessful paymentSuccessful) {
    super(context, msg); // TODO: recurring payment strings
    // TODO click to view receipt
    String amount = CurrencyUtils.buildAmount(paymentSuccessful.currency, paymentSuccessful.totalAmount);
    TdlibSender targetSender = new TdlibSender(tdlib, msg.chatId, tdlib.sender(msg.chatId));
    setTextCreator(() ->
      getText(
        R.string.PaymentSuccessfullyPaidNoItem,
        new BoldArgument(amount),
        new SenderArgument(targetSender)
      )
    );
    if (paymentSuccessful.invoiceChatId != 0 && paymentSuccessful.invoiceMessageId != 0) {
      setDisplayMessage(
        paymentSuccessful.invoiceChatId,
        paymentSuccessful.invoiceMessageId,
        message -> {
          if (!Td.isInvoice(message.content)) {
            return false;
          }
          setTextCreator(() ->
            getText(
              R.string.PaymentSuccessfullyPaid,
              new BoldArgument(amount),
              new SenderArgument(targetSender),
              new InvoiceArgument(message)
            )
          );
          return true;
        }
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageWebAppDataSent webAppDataSent) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.BotDataSent,
        new BoldArgument(webAppDataSent.buttonText)
      )
    );
  }

  // Video chats

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatStarted videoChatStarted) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamStartedYou :
            R.string.VoiceChatStartedYou
        );
      } else if (sender.isAnonymousGroupAdmin()) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamStarted :
            R.string.VoiceChatStarted
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamStartedBy :
            R.string.VoiceChatStartedBy,
          new SenderArgument(sender)
        );
      }
    });
    setOnClickListener(() ->
      tdlib.ui().openVoiceChat(controller(), videoChatStarted.groupCallId, openParameters())
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatScheduled videoChatScheduled) {
    super(context, msg);
    setTextCreator(() -> {
      String timestamp = Lang.getMessageTimestamp(videoChatScheduled.startDate, TimeUnit.SECONDS);
      return getText(
        msg.isChannelPost ?
          R.string.LiveStreamScheduledOn :
          R.string.VideoChatScheduledFor,
        new BoldArgument(timestamp)
      );
    });
    setOnClickListener(() ->
      tdlib.ui().openVoiceChat(controller(), videoChatScheduled.groupCallId, openParameters())
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatEnded videoChatEnded) {
    super(context, msg);
    setTextCreator(() -> {
      String duration = Lang.getCallDuration(videoChatEnded.duration);
      if (msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamEndedYou :
            R.string.VoiceChatEndedYou,
          new BoldArgument(duration)
        );
      } else if (sender.isAnonymousGroupAdmin()) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamEnded :
            R.string.VoiceChatEnded,
          new BoldArgument(duration)
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamEndedBy :
            R.string.VoiceChatEndedBy,
          new SenderArgument(sender),
          new BoldArgument(duration)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageInviteVideoChatParticipants inviteVideoChatParticipants) {
    super(context, msg);
    TdlibSender[] invitedParticipants = TdlibSender.valueOfUserIds(tdlib(), msg.chatId, inviteVideoChatParticipants.userIds);
    setTextCreator(() -> {
      if (sender.isSelf() || msg.isOutgoing) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamInviteOther :
            R.string.VoiceChatInviteOther,
          invitedParticipants.length == 1 ?
            new SenderArgument(invitedParticipants[0]) :
            new SenderListArgument(invitedParticipants)
        );
      } else if (invitedParticipants.length == 1 && invitedParticipants[0].isSelf()) {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamInviteSelf :
            R.string.VoiceChatInviteSelf,
          new SenderArgument(sender)
        );
      } else {
        return getText(
          msg.isChannelPost ?
            R.string.LiveStreamInvite :
            R.string.VoiceChatInvite,
          new SenderArgument(sender),
          new SenderListArgument(invitedParticipants)
        );
      }
    });
  }

  // Forum Topics

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageForumTopicCreated forumTopicCreated) {
    super(context, msg);
    setUnsupportedTextCreator();
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageForumTopicEdited forumTopicEdited) {
    super(context, msg);
    setUnsupportedTextCreator();
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageForumTopicIsClosedToggled forumTopicIsClosedToggled) {
    super(context, msg);
    setUnsupportedTextCreator();
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageForumTopicIsHiddenToggled forumTopicIsHiddenToggled) {
    super(context, msg);
    setUnsupportedTextCreator();
  }

  private void setUnsupportedTextCreator () {
    setTextCreator(() ->
      getText(R.string.UnsupportedMessage)
    );
  }

  // Custom server's service message

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageCustomServiceAction custom) {
    super(context, msg);
    setTextCreator(() ->
      new FormattedText(custom.text)
    );
  }

  // Recent actions (chat events)

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageDeleted messageDeleted) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.EventLogDeletedMessages,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageEdited messageEdited) {
    super(context, msg);
    setTextCreator(() -> {
      if (Td.isText(messageEdited.newMessage.content) ||
          Td.isAnimatedEmoji(messageEdited.newMessage.content)) {
        return getText(R.string.EventLogEditedMessages, new SenderArgument(sender));
      } else if (Td.isEmpty(Td.textOrCaption(messageEdited.newMessage.content))) {
        return getText(R.string.EventLogRemovedCaption, new SenderArgument(sender));
      } else {
        return getText(R.string.EventLogEditedCaption, new SenderArgument(sender));
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventPollStopped pollStopped) {
    super(context, msg);
    setTextCreator(() -> {
      final boolean isQuiz =
        Td.isPoll(pollStopped.message.content) &&
        ((TdApi.MessagePoll) pollStopped.message.content).poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
      return getText(
        isQuiz ?
          R.string.EventLogQuizStopped :
          R.string.EventLogPollStopped,
        new SenderArgument(sender)
      );
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageUnpinned messageUnpinned) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        R.string.EventLogUnpinnedMessages,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInvitesToggled invitesToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        invitesToggled.canInviteUsers ?
          R.string.EventLogToggledInvitesOn :
          R.string.EventLogToggledInvitesOff,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventSignMessagesToggled signMessagesToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        signMessagesToggled.signMessages ?
          R.string.EventLogToggledSignaturesOn :
          R.string.EventLogToggledSignaturesOff,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventHasProtectedContentToggled protectedContentToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        protectedContentToggled.hasProtectedContent ?
          R.string.EventLogToggledProtectionOn :
          R.string.EventLogToggledProtectionOff,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventIsAllHistoryAvailableToggled isAllHistoryAvailableToggled) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        isAllHistoryAvailableToggled.isAllHistoryAvailable ?
          R.string.XMadeGroupHistoryVisible :
          R.string.XMadeGroupHistoryHidden,
        new SenderArgument(sender)
      )
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatMuteNewParticipantsToggled newParticipantsToggled) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        if (msg.isOutgoing) {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogChannelMutedNewParticipantsYou :
              R.string.EventLogChannelUnmutedNewParticipantsYou
          );
        } else {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogChannelMutedNewParticipants :
              R.string.EventLogChannelUnmutedNewParticipants,
            new SenderArgument(sender)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogMutedNewParticipantsYou :
              R.string.EventLogUnmutedNewParticipantsYou
          );
        } else {
          return getText(
            newParticipantsToggled.muteNewParticipants ?
              R.string.EventLogMutedNewParticipants :
              R.string.EventLogUnmutedNewParticipants,
            new SenderArgument(sender)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatParticipantIsMutedToggled videoChatParticipantIsMutedToggled) {
    super(context, msg);
    TdlibSender targetSender = new TdlibSender(tdlib, msg.chatId, videoChatParticipantIsMutedToggled.participantId);
    setTextCreator(() -> {
      if (msg.isChannelPost) {
        if (msg.isOutgoing) {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogChannelMutedParticipantYou :
              R.string.EventLogChannelUnmutedParticipantYou,
            new SenderArgument(targetSender)
          );
        } else {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogChannelMutedParticipant :
              R.string.EventLogChannelUnmutedParticipant,
            new SenderArgument(sender),
            new SenderArgument(targetSender)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogMutedParticipantYou :
              R.string.EventLogUnmutedParticipantYou,
            new SenderArgument(targetSender)
          );
        } else {
          return getText(
            videoChatParticipantIsMutedToggled.isMuted ?
              R.string.EventLogMutedParticipant :
              R.string.EventLogUnmutedParticipant,
            new SenderArgument(sender),
            new SenderArgument(targetSender)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventHasAggressiveAntiSpamEnabledToggled hasAggressiveAntiSpamEnabledToggled) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(hasAggressiveAntiSpamEnabledToggled.hasAggressiveAntiSpamEnabled ?
          R.string.EventLogAntiSpamEnabledYou :
          R.string.EventLogAntiSpamDisabledYou
        );
      } else {
        return getText(hasAggressiveAntiSpamEnabledToggled.hasAggressiveAntiSpamEnabled ?
          R.string.EventLogAntiSpamEnabled :
          R.string.EventLogAntiSpamDisabled,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventActiveUsernamesChanged activeUsernamesChanged) {
    super(context, msg);

    Set<String> newUsernames = new HashSet<>();
    Collections.addAll(newUsernames, activeUsernamesChanged.newUsernames);
    Set<String> oldUsernames = new HashSet<>();
    Collections.addAll(oldUsernames, activeUsernamesChanged.oldUsernames);

    Set<String> addedUsernames = new HashSet<>(newUsernames);
    addedUsernames.removeAll(oldUsernames);
    Set<String> removedUsernames = new HashSet<>(oldUsernames);
    removedUsernames.removeAll(newUsernames);

    if (addedUsernames.isEmpty() == removedUsernames.isEmpty()) {
      // %1$s changed active usernames from %2$s to %3$s
      // %1$s changed order of usernames from %2$s to %3$s
      setTextCreator(() -> {
        boolean changedOrder = addedUsernames.isEmpty();
        String oldUsernamesList = Strings.join(Lang.getConcatSeparator(), activeUsernamesChanged.oldUsernames, username -> "@" + username);
        String newUsernamesList = Strings.join(Lang.getConcatSeparator(), activeUsernamesChanged.newUsernames, username -> "@" + username);
        if (msg.isOutgoing) {
          return getText(
            changedOrder ? R.string.EventLogUsernamesChangedOrderYou : R.string.EventLogUsernamesChangedYou,
            new BoldArgument(oldUsernamesList),
            new BoldArgument(newUsernamesList)
          );
        } else {
          return getText(
            changedOrder ? R.string.EventLogUsernamesChangedOrder : R.string.EventLogUsernamesChanged,
            new SenderArgument(sender),
            new BoldArgument(oldUsernamesList),
            new BoldArgument(newUsernamesList)
          );
        }
      });
    } else {
      int size = Math.max(addedUsernames.size(), removedUsernames.size());
      boolean isActivate = removedUsernames.isEmpty();
      if (size == 1) {
        // %1$s activated %2$s username
        // %1$s deactivated %2$s username
        setTextCreator(() -> {
          String singleUsername = (addedUsernames.isEmpty() ?
            removedUsernames.iterator() :
            addedUsernames.iterator()
          ).next();
          if (msg.isOutgoing) {
            return getText(
              isActivate ? R.string.EventLogUsernameActivatedYou : R.string.EventLogUsernameDeactivatedYou,
              new BoldArgument(singleUsername)
            );
          } else {
            return getText(
              isActivate ? R.string.EventLogUsernameActivated : R.string.EventLogUsernameDeactivated,
              new SenderArgument(sender),
              new BoldArgument(singleUsername)
            );
          }
        });
      } else {
        // %2$s activated %1$s usernames: %3$s
        // %2$s deactivated %1$s usernames: %3$s
        setTextCreator(() -> {
          Set<String> usernames = isActivate ? addedUsernames : removedUsernames;
          String usernamesList = Strings.join(Lang.getConcatSeparator(), usernames, username -> "@" + username);
          if (msg.isOutgoing) {
            return getPlural(
              isActivate ? R.string.EventLogUsernamesActivatedYou : R.string.EventLogUsernamesDeactivatedYou,
              usernames.size(),
              new BoldArgument(usernamesList)
            );
          } else {
            return getPlural(
              isActivate ? R.string.EventLogUsernamesActivated : R.string.EventLogUsernamesDeactivated,
              usernames.size(),
              new SenderArgument(sender),
              new BoldArgument(usernamesList)
            );
          }
        });
      }
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventIsForumToggled isForumToggled) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(isForumToggled.isForum ?
          R.string.EventLogForumEnabledYou :
          R.string.EventLogForumDisabledYou
        );
      } else {
        return getText(isForumToggled.isForum ?
          R.string.EventLogForumEnabled :
          R.string.EventLogForumDisabled,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventForumTopicCreated forumTopicCreated) {
    this(context, msg, forumTopicCreated.topicInfo, R.string.EventLogForumTopicCreated, R.string.EventLogForumTopicCreatedYou);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventForumTopicDeleted forumTopicDeleted) {
    this(context, msg, forumTopicDeleted.topicInfo, R.string.EventLogForumTopicDeleted, R.string.EventLogForumTopicDeletedYou);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventForumTopicPinned forumTopicPinned) {
    this(context, msg, forumTopicPinned.newTopicInfo != null ? forumTopicPinned.newTopicInfo : forumTopicPinned.oldTopicInfo, R.string.EventLogForumTopicPinned, R.string.EventLogForumTopicPinnedYou);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventForumTopicToggleIsClosed forumTopicToggleIsClosed) {
    this(context, msg, forumTopicToggleIsClosed.topicInfo,
      forumTopicToggleIsClosed.topicInfo.isClosed ?
        R.string.EventLogForumTopicClosed :
        R.string.EventLogForumTopicReopened,
      forumTopicToggleIsClosed.topicInfo.isClosed ?
        R.string.EventLogForumTopicClosedYou :
        R.string.EventLogForumTopicReopenedYou
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventForumTopicToggleIsHidden forumTopicToggleIsHidden) {
    this(context, msg, forumTopicToggleIsHidden.topicInfo,
      forumTopicToggleIsHidden.topicInfo.isHidden ?
        R.string.EventLogForumTopicHidden :
        R.string.EventLogForumTopicUnhidden,
      forumTopicToggleIsHidden.topicInfo.isHidden ?
        R.string.EventLogForumTopicHiddenYou :
        R.string.EventLogForumTopicUnhiddenYou
    );
  }

  private TGMessageService (MessagesManager context, TdApi.Message msg, @Nullable TdApi.ForumTopicInfo forumTopicInfo, @StringRes int topicTextResId, @StringRes int topicTextOutgoingResId) {
    super(context, msg);
    String topicName = forumTopicInfo != null ? forumTopicInfo.name : "?";
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(
          topicTextOutgoingResId,
          new BoldArgument(topicName)
        );
      } else {
        return getText(
          topicTextResId,
          new SenderArgument(sender),
          new BoldArgument(topicName)
        );
      }
    });
    if (forumTopicInfo != null) {
      setDisplayMessage(msg.chatId, forumTopicInfo.messageThreadId, message -> {
        setTextCreator(() -> {
          if (msg.isOutgoing) {
            return getText(
              topicTextOutgoingResId,
              new MessageArgument(message, new TdApi.FormattedText(topicName, null))
            );
          } else {
            return getText(
              topicTextResId,
              new SenderArgument(sender),
              new MessageArgument(message, new TdApi.FormattedText(topicName, null))
            );
          }
        });
        return true;
      });
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventForumTopicEdited forumTopicEdited) {
    super(context, msg);
    setTextCreator(() -> {
      if (msg.isOutgoing) {
        return getText(R.string.EventLogForumTopicEditedNameYou,
          new BoldArgument(forumTopicEdited.oldTopicInfo.name),
          new BoldArgument(forumTopicEdited.newTopicInfo.name)
        );
      } else {
        return getText(R.string.EventLogForumTopicEditedName,
          new SenderArgument(sender),
          new BoldArgument(forumTopicEdited.oldTopicInfo.name),
          new BoldArgument(forumTopicEdited.newTopicInfo.name)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatParticipantVolumeLevelChanged videoChatParticipantVolumeLevelChanged) {
    super(context, msg);
    TdlibSender targetSender = new TdlibSender(tdlib, msg.chatId, videoChatParticipantVolumeLevelChanged.participantId);
    setTextCreator(() -> {
      final FormattedArgument volume = new BoldArgument((videoChatParticipantVolumeLevelChanged.volumeLevel / 100) + "%");
      if (msg.isOutgoing) {
        return getText(
          R.string.EventLogChangedVolumeYou,
          new SenderArgument(targetSender),
          volume
        );
      } else if (targetSender.isSelf()) {
        return getText(
          R.string.EventLogChangedYourVolume,
          new SenderArgument(sender),
          volume
        );
      } else {
        return getText(
          R.string.EventLogChangedVolume,
          new SenderArgument(sender),
          new SenderArgument(targetSender),
          volume
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventUsernameChanged usernameChanged) {
    super(context, msg);
    setTextCreator(() -> {
      boolean hasUsername = !StringUtils.isEmpty(usernameChanged.newUsername);
      if (msg.isChannelPost) {
        return getText(
          hasUsername ?
            R.string.EventLogChangedChannelLink :
            R.string.EventLogRemovedChannelLink,
          new SenderArgument(sender)
        );
      } else {
        return getText(
          hasUsername ?
            R.string.EventLogChangedGroupLink :
            R.string.EventLogRemovedGroupLink,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventDescriptionChanged descriptionChanged) {
    super(context, msg);
    setTextCreator(() -> {
      boolean hasDescription = !StringUtils.isEmpty(descriptionChanged.newDescription);
      if (msg.isChannelPost) {
        return getText(
          hasDescription ?
            R.string.EventLogEditedChannelDescription :
            R.string.EventLogRemovedChannelDescription,
          new SenderArgument(sender)
        );
      } else {
        return getText(
          hasDescription ?
            R.string.EventLogEditedGroupDescription :
            R.string.EventLogRemovedGroupDescription,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventStickerSetChanged stickerSetChanged) {
    super(context, msg);
    setTextCreator(() ->
      getText(
        stickerSetChanged.newStickerSetId != 0 ?
          R.string.XChangedGroupStickerSet :
          R.string.XRemovedGroupStickerSet,
        new SenderArgument(sender)
      )
    );
    long stickerSetId = stickerSetChanged.newStickerSetId != 0 ?
      stickerSetChanged.newStickerSetId :
      stickerSetChanged.oldStickerSetId;
    if (stickerSetId != 0) {
      setOnClickListener(() ->
        tdlib.ui().showStickerSet(controller(), stickerSetId, openParameters())
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventLinkedChatChanged linkedChatChanged) {
    super(context, msg);
    TdlibSender linkedChat = new TdlibSender(tdlib, msg.chatId, new TdApi.MessageSenderChat(
      linkedChatChanged.newLinkedChatId != 0 ?
        linkedChatChanged.newLinkedChatId :
        linkedChatChanged.oldLinkedChatId
    ));
    setTextCreator(() -> {
      boolean hasLinkedChat = linkedChatChanged.newLinkedChatId != 0;
      if (msg.isChannelPost) {
        return getText(
          hasLinkedChat ?
            R.string.EventLogLinkedGroupChanged :
            R.string.EventLogLinkedGroupRemoved,
          new SenderArgument(sender),
          new SenderArgument(linkedChat)
        );
      } else if (sender.isServiceChannelBot() || Td.getSenderId(msg) == msg.chatId) {
        return getText(
          hasLinkedChat ?
            R.string.EventLogLinkedChannelChangedUnknown :
            R.string.EventLogLinkedChannelRemovedUnknown,
          new SenderArgument(linkedChat)
        );
      } else {
        return getText(
          hasLinkedChat ?
            R.string.EventLogLinkedChannelChanged :
            R.string.EventLogLinkedChannelRemoved,
          new SenderArgument(sender),
          new SenderArgument(linkedChat)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventLocationChanged locationChanged) {
    super(context, msg);
    setTextCreator(() -> {
      if (locationChanged.newLocation != null) {
        return getText(
          locationChanged.oldLocation != null ?
            R.string.EventLogLocationChanged :
            R.string.EventLogLocationSet,
          new SenderArgument(sender),
          new BoldArgument(locationChanged.newLocation.address)
        );
      } else {
        return getText( // TODO: display locationChanged.oldLocation.address
          R.string.EventLogLocationRemoved,
          new SenderArgument(sender)
        );
      }
    });
    TdApi.ChatLocation chatLocation = locationChanged.newLocation != null ?
      locationChanged.newLocation :
      locationChanged.oldLocation;
    if (chatLocation != null) {
      setOnClickListener(() ->
        tdlib.ui().openMap(this, new MapController.Args(
            chatLocation.location.latitude,
            chatLocation.location.longitude
          ).setChatId(msg.chatId, messagesController().getMessageThreadId())
            .setLocationOwnerChatId(msg.chatId)
            .setIsFaded(locationChanged.newLocation == null)
        )
      );
    }
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventPhotoChanged photoChanged) {
    super(context, msg);
    setTextCreator(() -> {
      if (photoChanged.newPhoto != null) {
        if (msg.isChannelPost) {
          return getText(R.string.ActionChannelChangedPhoto);
        } else if (msg.isOutgoing) {
          return getText(R.string.group_photo_changed_you);
        } else {
          return getText(R.string.group_photo_changed, new SenderArgument(sender));
        }
      } else {
        if (msg.isChannelPost) {
          return getText(R.string.ActionChannelRemovedPhoto);
        } else if (msg.isOutgoing) {
          return getText(R.string.group_photo_deleted_you);
        } else {
          return getText(R.string.group_photo_deleted, new SenderArgument(sender));
        }
      }
    });
    TdApi.ChatPhoto chatPhoto = photoChanged.newPhoto != null ?
      photoChanged.newPhoto :
      photoChanged.oldPhoto;
    if (chatPhoto != null) {
      setDisplayChatPhoto(chatPhoto);
    }
  }

  private void setBackgroundTextCreator (int oldAccentColorId, int newAccentColorId,
                                         long oldBackgroundCustomEmojiId, long newBackgroundCustomEmojiId,
                                         boolean isProfile) {
    setTextCreator(() -> {
      if (oldBackgroundCustomEmojiId == newBackgroundCustomEmojiId) {
        // Only accent color changed
        if (isProfile && (oldAccentColorId == -1 || newAccentColorId == -1)) {
          boolean isUnset = newAccentColorId == -1;
          int accentColorId = isUnset ?
            oldAccentColorId :
            newAccentColorId;
          if (msg.isOutgoing) {
            return getText(
              isUnset ? R.string.EventLogProfileColorUnsetYou : R.string.EventLogProfileColorSetYou,
              new AccentColorArgument(tdlib.accentColor(accentColorId))
            );
          } else {
            return getText(
              isUnset ? R.string.EventLogProfileColorUnset : R.string.EventLogProfileColorSet,
              new SenderArgument(sender),
              new AccentColorArgument(tdlib.accentColor(accentColorId))
            );
          }
        } else {
          if (msg.isOutgoing) {
            return getText(
              isProfile ? R.string.EventLogProfileColorChangedYou : R.string.EventLogAccentColorChangedYou,
              new AccentColorArgument(tdlib.accentColor(oldAccentColorId)),
              new AccentColorArgument(tdlib.accentColor(newAccentColorId))
            );
          } else {
            return getText(
              isProfile ? R.string.EventLogProfileColorChanged : R.string.EventLogAccentColorChanged,
              new SenderArgument(sender),
              new AccentColorArgument(tdlib.accentColor(oldAccentColorId)),
              new AccentColorArgument(tdlib.accentColor(newAccentColorId))
            );
          }
        }
      } else if (oldAccentColorId == newAccentColorId) {
        // Only background changed
        TdlibAccentColor repaintAccentColor = newAccentColorId != -1 ? tdlib.accentColor(newAccentColorId) : null;
        if (newBackgroundCustomEmojiId == 0 || oldBackgroundCustomEmojiId == 0) {
          boolean isUnset = newBackgroundCustomEmojiId == 0;
          long backgroundCustomEmojiId = isUnset ?
            oldBackgroundCustomEmojiId :
            newBackgroundCustomEmojiId;
          if (msg.isOutgoing) {
            return getText(
              isProfile ?
                (isUnset ? R.string.EventLogProfileEmojiUnsetYou : R.string.EventLogProfileEmojiSetYou) :
                (isUnset ? R.string.EventLogEmojiUnsetYou : R.string.EventLogEmojiSetYou),
              new CustomEmojiArgument(tdlib, backgroundCustomEmojiId, repaintAccentColor)
            );
          } else {
            return getText(
              isProfile ?
                (isUnset ? R.string.EventLogProfileEmojiUnset : R.string.EventLogProfileEmojiSet) :
                (isUnset ? R.string.EventLogEmojiUnset : R.string.EventLogEmojiSet),
              new SenderArgument(sender),
              new CustomEmojiArgument(tdlib, backgroundCustomEmojiId, repaintAccentColor)
            );
          }
        } else {
          if (msg.isOutgoing) {
            return getText(
              isProfile ? R.string.EventLogProfileEmojiChangedYou : R.string.EventLogEmojiChangedYou,
              new CustomEmojiArgument(tdlib, oldBackgroundCustomEmojiId, repaintAccentColor),
              new CustomEmojiArgument(tdlib, newBackgroundCustomEmojiId, repaintAccentColor)
            );
          } else {
            return getText(
              isProfile ? R.string.EventLogProfileEmojiChanged : R.string.EventLogEmojiChanged,
              new SenderArgument(sender),
              new CustomEmojiArgument(tdlib, oldBackgroundCustomEmojiId, repaintAccentColor),
              new CustomEmojiArgument(tdlib, newBackgroundCustomEmojiId, repaintAccentColor)
            );
          }
        }
      } else {
        // Both color and emoji changed

        boolean hadIconOrColor = oldAccentColorId != -1 || oldBackgroundCustomEmojiId != 0;
        boolean hasIconOrColor = newAccentColorId != -1 || newBackgroundCustomEmojiId != 0;

        if (!hadIconOrColor || !hasIconOrColor) {
          boolean isUnset = !hasIconOrColor;
          int accentColorId = isUnset ?
            oldAccentColorId :
            newAccentColorId;
          long backgroundCustomEmojiId = isUnset ?
            oldBackgroundCustomEmojiId :
            newBackgroundCustomEmojiId;
          if (msg.isOutgoing) {
            return getText(
              isUnset ? R.string.EventLogProfileColorIconUnsetYou : R.string.EventLogProfileColorIconSetYou,
              new AccentColorArgument(accentColorId != -1 ? tdlib.accentColor(accentColorId) : null, backgroundCustomEmojiId)
            );
          } else {
            return getText(
              isUnset ? R.string.EventLogProfileColorIconUnset : R.string.EventLogProfileColorIconSet,
              new SenderArgument(sender),
              new AccentColorArgument(accentColorId != -1 ? tdlib.accentColor(accentColorId) : null, backgroundCustomEmojiId)
            );
          }
        } else {
          if (msg.isOutgoing) {
            return getText(
              R.string.EventLogProfileColorIconChangedYou,
              new AccentColorArgument(oldAccentColorId != -1 ? tdlib.accentColor(oldAccentColorId) : null, oldBackgroundCustomEmojiId),
              new AccentColorArgument(newAccentColorId != -1 ? tdlib.accentColor(newAccentColorId) : null, newBackgroundCustomEmojiId)
            );
          } else {
            return getText(
              R.string.EventLogProfileColorIconChanged,
              new SenderArgument(sender),
              new AccentColorArgument(oldAccentColorId != -1 ? tdlib.accentColor(oldAccentColorId) : null, oldBackgroundCustomEmojiId),
              new AccentColorArgument(newAccentColorId != -1 ? tdlib.accentColor(newAccentColorId) : null, newBackgroundCustomEmojiId)
            );
          }
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventAccentColorChanged accentColorChanged) {
    super(context, msg);
    setBackgroundTextCreator(
      accentColorChanged.oldAccentColorId, accentColorChanged.newAccentColorId,
      accentColorChanged.oldBackgroundCustomEmojiId, accentColorChanged.newBackgroundCustomEmojiId,
      false
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventProfileAccentColorChanged profileAccentColorChanged) {
    super(context, msg);
    setBackgroundTextCreator(
      profileAccentColorChanged.oldProfileAccentColorId, profileAccentColorChanged.newProfileAccentColorId,
      profileAccentColorChanged.oldProfileBackgroundCustomEmojiId, profileAccentColorChanged.newProfileBackgroundCustomEmojiId,
      true
    );
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventEmojiStatusChanged emojiStatusChanged) {
    super(context, msg);
    setTextCreator(() -> {
      if (emojiStatusChanged.oldEmojiStatus == null || emojiStatusChanged.newEmojiStatus == null) {
        boolean isUnset = emojiStatusChanged.newEmojiStatus == null;
        long backgroundCustomEmojiId = isUnset ?
          (emojiStatusChanged.oldEmojiStatus != null ? emojiStatusChanged.oldEmojiStatus.customEmojiId : 0) :
          emojiStatusChanged.newEmojiStatus.customEmojiId;
        if (msg.isOutgoing) {
          return getText(
            (isUnset ? R.string.EventLogEmojiStatusUnsetYou : R.string.EventLogEmojiStatusSetYou),
            new CustomEmojiArgument(tdlib, backgroundCustomEmojiId, null)
          );
        } else {
          return getText(
            (isUnset ? R.string.EventLogEmojiStatusUnset : R.string.EventLogEmojiStatusSet),
            new SenderArgument(sender),
            new CustomEmojiArgument(tdlib, backgroundCustomEmojiId, null)
          );
        }
      } else {
        long oldBackgroundCustomEmojiId = emojiStatusChanged.oldEmojiStatus.customEmojiId;
        long newBackgroundCustomEmojiId = emojiStatusChanged.newEmojiStatus.customEmojiId;
        if (msg.isOutgoing) {
          return getText(
            R.string.EventLogEmojiStatusChangedYou,
            new CustomEmojiArgument(tdlib, oldBackgroundCustomEmojiId, null),
            new CustomEmojiArgument(tdlib, newBackgroundCustomEmojiId, null)
          );
        } else {
          return getText(
            R.string.EventLogEmojiStatusChanged,
            new SenderArgument(sender),
            new CustomEmojiArgument(tdlib, oldBackgroundCustomEmojiId, null),
            new CustomEmojiArgument(tdlib, newBackgroundCustomEmojiId, null)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventSlowModeDelayChanged slowModeDelayChanged) {
    super(context, msg);
    setTextCreator(() -> {
      if (slowModeDelayChanged.newSlowModeDelay != 0) {
        String duration = Lang.getDuration(slowModeDelayChanged.newSlowModeDelay);
        if (msg.isOutgoing) {
          return getText(
            R.string.EventLogSlowModeChangedYou,
            new BoldArgument(duration)
          );
        } else {
          return getText(
            R.string.EventLogSlowModeChanged,
            new SenderArgument(sender),
            new BoldArgument(duration)
          );
        }
      } else {
        return getText(
          R.string.EventLogSlowModeDisabled,
          new SenderArgument(sender)
        );
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessagePinned messagePinned) {
    super(context, msg);
    setTextCreator(() -> {
      if (sender.isServiceChannelBot()) {
        return getText(R.string.EventLogPinnedMessages, new SenderArgument(new TdlibSender(tdlib(), msg.chatId, messagePinned.message.senderId)));
      } else {
        return getText(R.string.EventLogPinnedMessages, new SenderArgument(sender));
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMemberJoinedByInviteLink joinedByInviteLink) {
    super(context, msg);
    TdlibSender linkAuthor = new TdlibSender(tdlib(), msg.chatId, new TdApi.MessageSenderUser(joinedByInviteLink.inviteLink.creatorUserId));
    setTextCreator(() -> {
      if (joinedByInviteLink.inviteLink.isPrimary) {
        if (msg.isOutgoing) {
          return getText(
            msg.isChannelPost ?
              R.string.LinkJoinChannelPrimaryYou :
              R.string.LinkJoinPrimaryYou,
            new InviteLinkArgument(joinedByInviteLink.inviteLink, true)
          );
        } else {
          return getText(
            msg.isChannelPost ?
              R.string.LinkJoinChannelPrimary :
              R.string.LinkJoinPrimary,
            new SenderArgument(sender),
            new InviteLinkArgument(joinedByInviteLink.inviteLink, true)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            Td.isTemporary(joinedByInviteLink.inviteLink) ?
              msg.isChannelPost ?
                R.string.LinkJoinChannelTempYou :
                R.string.LinkJoinTempYou :
              msg.isChannelPost ?
                R.string.LinkJoinChannelOtherYou :
                R.string.LinkJoinOtherYou,
            new SenderArgument(linkAuthor),
            new InviteLinkArgument(joinedByInviteLink.inviteLink, true)
          );
        } else {
          return getText(
            Td.isTemporary(joinedByInviteLink.inviteLink) ?
              msg.isChannelPost ?
                R.string.LinkJoinChannelTemp :
                R.string.LinkJoinTemp :
              msg.isChannelPost ?
                R.string.LinkJoinChannelOther :
                R.string.LinkJoinOther,
            new SenderArgument(sender),
            new SenderArgument(linkAuthor),
            new InviteLinkArgument(joinedByInviteLink.inviteLink, true)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMemberJoinedByRequest joinedByRequest) {
    super(context, msg);
    TdlibSender approvedBy = new TdlibSender(tdlib(), msg.chatId, new TdApi.MessageSenderUser(joinedByRequest.approverUserId));
    TdlibSender linkAuthor =
      joinedByRequest.inviteLink != null ?
        new TdlibSender(tdlib(), msg.chatId, new TdApi.MessageSenderUser(joinedByRequest.inviteLink.creatorUserId)) :
        null;
    setTextCreator(() -> {
      if (joinedByRequest.inviteLink != null) {
        if (joinedByRequest.inviteLink.isPrimary) {
          if (msg.isOutgoing) {
            return getText(
              msg.isChannelPost ?
                R.string.LinkJoinChannelPrimaryYouWithApproval :
                R.string.LinkJoinPrimaryYouWithApproval,
              new InviteLinkArgument(joinedByRequest.inviteLink, true),
              new SenderArgument(approvedBy)
            );
          } else {
            return getText(
              msg.isChannelPost ?
                R.string.LinkJoinChannelPrimaryWithApproval :
                R.string.LinkJoinPrimaryWithApproval,
              new SenderArgument(sender),
              new InviteLinkArgument(joinedByRequest.inviteLink, true),
              new SenderArgument(approvedBy)
            );
          }
        } else {
          if (msg.isOutgoing) {
            return getText(
              Td.isTemporary(joinedByRequest.inviteLink) ?
                msg.isChannelPost ?
                  R.string.LinkJoinChannelTempYouWithApproval :
                  R.string.LinkJoinTempYouWithApproval :
                msg.isChannelPost ?
                  R.string.LinkJoinChannelOtherYouWithApproval :
                  R.string.LinkJoinOtherYouWithApproval,
              new SenderArgument(linkAuthor),
              new InviteLinkArgument(joinedByRequest.inviteLink, true),
              new SenderArgument(approvedBy)
            );
          } else {
            return getText(
              Td.isTemporary(joinedByRequest.inviteLink) ?
                msg.isChannelPost ?
                  R.string.LinkJoinChannelTempWithApproval :
                  R.string.LinkJoinTempWithApproval :
                msg.isChannelPost ?
                  R.string.LinkJoinChannelOtherWithApproval :
                  R.string.LinkJoinOtherWithApproval,
              new SenderArgument(sender),
              new SenderArgument(linkAuthor),
              new InviteLinkArgument(joinedByRequest.inviteLink, true),
              new SenderArgument(approvedBy)
            );
          }
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            msg.isChannelPost ?
              R.string.YouAcceptedToChannelBy :
              R.string.YouAcceptedToGroupBy,
            new SenderArgument(approvedBy)
          );
        } else {
          return getText(
            msg.isChannelPost ?
              R.string.XAcceptedToChannelBy :
              R.string.XAcceptedToGroupBy,
            new SenderArgument(sender),
            new SenderArgument(approvedBy)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInviteLinkRevoked inviteLinkRevoked) {
    super(context, msg);
    TdlibSender linkAuthor = new TdlibSender(tdlib(), msg.chatId, new TdApi.MessageSenderUser(inviteLinkRevoked.inviteLink.creatorUserId));
    setTextCreator(() -> {
      if (inviteLinkRevoked.inviteLink.isPrimary) {
        if (msg.isOutgoing) {
          return getText(
            R.string.LinkRevokePrimaryYou,
            new InviteLinkArgument(inviteLinkRevoked.inviteLink, true)
          );
        } else {
          return getText(
            R.string.LinkRevokePrimary,
            new SenderArgument(sender),
            new InviteLinkArgument(inviteLinkRevoked.inviteLink, true)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            Td.isTemporary(inviteLinkRevoked.inviteLink) ?
              R.string.LinkRevokeTempYou :
              R.string.LinkRevokeOtherYou,
            new SenderArgument(linkAuthor),
            new InviteLinkArgument(inviteLinkRevoked.inviteLink, true)
          );
        } else {
          return getText(
            Td.isTemporary(inviteLinkRevoked.inviteLink) ?
              R.string.LinkRevokeTemp :
              R.string.LinkRevokeOther,
            new SenderArgument(sender),
            new SenderArgument(linkAuthor),
            new InviteLinkArgument(inviteLinkRevoked.inviteLink, true)
          );
        }
      }
    });
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInviteLinkDeleted inviteLinkDeleted) {
    super(context, msg);
    TdlibSender linkAuthor = new TdlibSender(tdlib(), msg.chatId, new TdApi.MessageSenderUser(inviteLinkDeleted.inviteLink.creatorUserId));
    setTextCreator(() -> {
      if (inviteLinkDeleted.inviteLink.isPrimary) {
        if (msg.isOutgoing) {
          return getText(
            R.string.LinkDeletePrimaryYou,
            new InviteLinkArgument(inviteLinkDeleted.inviteLink, false)
          );
        } else {
          return getText(
            R.string.LinkDeletePrimary,
            new SenderArgument(sender),
            new InviteLinkArgument(inviteLinkDeleted.inviteLink, false)
          );
        }
      } else {
        if (msg.isOutgoing) {
          return getText(
            Td.isTemporary(inviteLinkDeleted.inviteLink) ?
              R.string.LinkDeleteTempYou :
              R.string.LinkDeleteOtherYou,
            new SenderArgument(linkAuthor),
            new InviteLinkArgument(inviteLinkDeleted.inviteLink, false)
          );
        } else {
          return getText(
            Td.isTemporary(inviteLinkDeleted.inviteLink) ?
              R.string.LinkDeleteTemp :
              R.string.LinkDeleteOther,
            new SenderArgument(sender),
            new SenderArgument(linkAuthor),
            new InviteLinkArgument(inviteLinkDeleted.inviteLink, false)
          );
        }
      }
    });
  }

  // Pre-impl: utilities used by constructors

  @Override
  @NonNull
  protected TextColorSet defaultTextColorSet () {
    if (useBubbles()) {
      return new TextColorSet() {
        @Override
        public int defaultTextColor () {
          return getBubbleDateTextColor();
        }

        @Override
        public int clickableTextColor (boolean isPressed) {
          return ColorUtils.fromToArgb(
            getBubbleDateTextColor(),
            Theme.getColor(ColorId.messageAuthor),
            messagesController().wallpaper().getBackgroundTransparency()
          );
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          int colorId = backgroundColorId(isPressed);
          return colorId != 0 ?
            ColorUtils.alphaColor(.2f, Theme.getColor(colorId)) :
            0;
        }

        @Override
        public int backgroundColorId (boolean isPressed) {
          float transparency = messagesController().wallpaper().getBackgroundTransparency();
          return isPressed && transparency == 1f ?
            ColorId.messageAuthor :
            0;
        }

        @Override
        public int iconColor () {
          float transparency = messagesController().wallpaper().getBackgroundTransparency();
          return ColorUtils.alphaColor(
            .4f + (1f - .4f) * transparency,
            defaultTextColor()
          );
        }
      };
    } else {
      return new TextColorSet() {
        @Override
        public int defaultTextColor () {
          return Theme.textAccentColor();
        }

        @Override
        public int clickableTextColor (boolean isPressed) {
          return Theme.getColor(ColorId.messageAuthor);
        }

        @Override
        public int backgroundColorId (boolean isPressed) {
          return isPressed ? ColorId.messageAuthor : 0;
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          int colorId = backgroundColorId(isPressed);
          return colorId != 0 ?
            ColorUtils.alphaColor(.2f, Theme.getColor(colorId)) :
            0;
        }

        @Override
        public int iconColor () {
          return Theme.getColor(ColorId.icon);
        }
      };
    }
  }
}
