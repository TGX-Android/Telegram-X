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
 */
package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.DrawableProvider;

import java.io.File;

import me.vkryl.android.animator.ListAnimator;
import tgx.td.Td;

public abstract class MediaPreview implements ListAnimator.Measurable {
  /*private static MediaWrapper createMediaWrapper (BaseActivity context, Tdlib tdlib, TdApi.Message message, TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return new MediaWrapper(context, tdlib, ((TdApi.MessagePhoto) content).photo, message.chatId, message.id, null, true);
      case TdApi.MessageVideo.CONSTRUCTOR:
        return new MediaWrapper(context, tdlib, ((TdApi.MessageVideo) content).video, message.chatId, message.id, null, true);
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return new MediaWrapper(context, tdlib, ((TdApi.MessageAnimation) content).animation, message.chatId, message.id, null, true);
      default:
        throw new IllegalArgumentException("message.content == " + content);
    }
  }*/

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Video video, int size, int cornerRadius, boolean hasSpoiler) {
    if (video.thumbnail != null || video.minithumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, video.thumbnail, video.minithumbnail, hasSpoiler);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Animation animation, int size, int cornerRadius, boolean hasSpoiler) {
    if (animation.thumbnail != null || animation.minithumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, animation.thumbnail, animation.minithumbnail, hasSpoiler);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Photo photo, int size, int cornerRadius, boolean needFireIcon) {
    TdApi.PhotoSize thumbnail = Td.findSmallest(photo);
    if (thumbnail != null || photo.minithumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, TD.toThumbnail(thumbnail), photo.minithumbnail, needFireIcon);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Audio audio, int size, int cornerRadius) {
    if (audio.albumCoverThumbnail != null || audio.albumCoverMinithumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, audio.albumCoverThumbnail, audio.albumCoverMinithumbnail);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Game game, int size, int cornerRadius) {
    if (game.animation != null) {
      // animation preview
      if (game.animation.minithumbnail != null || game.animation.thumbnail != null) {
        return new MediaPreviewSimple(tdlib, size, cornerRadius, game.animation.thumbnail, game.animation.minithumbnail);
      }
    } else if (game.photo != null) {
      TdApi.PhotoSize thumbnail = Td.findSmallest(game.photo);
      if (thumbnail != null || game.photo.minithumbnail != null) {
        return new MediaPreviewSimple(tdlib, size, cornerRadius, TD.toThumbnail(thumbnail), game.photo.minithumbnail);
      }
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Thumbnail thumbnail, TdApi.Minithumbnail minithumbnail, int size, int cornerRadius) {
    if (thumbnail != null || minithumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, thumbnail, minithumbnail);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Document document, int size, int cornerRadius) {
    if (document.minithumbnail != null || document.thumbnail != null) {
      // TODO FileComponent.createFullPreview(tdlib, document)
      return new MediaPreviewSimple(tdlib, size, cornerRadius, document.thumbnail, document.minithumbnail);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.ProfilePhoto profilePhoto, TdApi.Thumbnail thumbnail, int size, int cornerRadius) {
    if (thumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, thumbnail, profilePhoto != null ? profilePhoto.minithumbnail : null);
    } else if (profilePhoto != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, profilePhoto);
    } else {
      return null;
    }
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.ChatPhoto chatPhoto, int size, int cornerRadius) {
    if (chatPhoto != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, chatPhoto);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.ChatPhotoInfo chatPhotoInfo, int size, int cornerRadius) {
    if (chatPhotoInfo != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, chatPhotoInfo);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Location location, TdApi.Thumbnail thumbnail, int size, int cornerRadius) {
    if (location != null || thumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, location, thumbnail);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Venue venue, TdApi.Thumbnail thumbnail, int size, int cornerRadius) {
    if (venue != null || thumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, venue, thumbnail);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, File file, String mimeType, int size, int cornerRadius) {
    try {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, file, mimeType);
    } catch (UnsupportedOperationException ignored) {
      return null;
    }
  }

  public static boolean hasMedia (TdApi.LinkPreview linkPreview) {
    switch (linkPreview.type.getConstructor()) {
      case TdApi.LinkPreviewTypeVideo.CONSTRUCTOR: {
        TdApi.LinkPreviewTypeVideo video = (TdApi.LinkPreviewTypeVideo) linkPreview.type;
        if (video.video != null) {
          return video.video.thumbnail != null || video.video.minithumbnail != null;
        }
        break;
      }
      case TdApi.LinkPreviewTypeAnimation.CONSTRUCTOR: {
        TdApi.LinkPreviewTypeAnimation animation = (TdApi.LinkPreviewTypeAnimation) linkPreview.type;
        if (animation.animation.thumbnail != null || animation.animation.minithumbnail != null) {
          return true;
        }
        break;
      }
      case TdApi.LinkPreviewTypeVoiceNote.CONSTRUCTOR: {
        // TODO voice note preview?
        break;
      }
      case TdApi.LinkPreviewTypeVideoNote.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeSticker.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeDocument.CONSTRUCTOR:
      case TdApi.LinkPreviewTypePhoto.CONSTRUCTOR: {
        return true;
      }

      case TdApi.LinkPreviewTypeAlbum.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeApp.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeArticle.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeAudio.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeBackground.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeChannelBoost.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeChat.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeDirectMessagesChat.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeEmbeddedAudioPlayer.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeEmbeddedVideoPlayer.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeInvoice.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeMessage.CONSTRUCTOR:
      case TdApi.LinkPreviewTypePremiumGiftCode.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeShareableChatFolder.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeStickerSet.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeStory.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeStoryAlbum.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeSupergroupBoost.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeTheme.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeUnsupported.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeUpgradedGift.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeGiftAuction.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeLiveStory.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeGiftCollection.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeUser.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeGroupCall.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeVideoChat.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeWebApp.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeEmbeddedAnimationPlayer.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeExternalAudio.CONSTRUCTOR:
      case TdApi.LinkPreviewTypeExternalVideo.CONSTRUCTOR: {
        // TODO support more types
        break;
      }
      default:
        Td.assertLinkPreviewType_a9a3ffcd();
        throw Td.unsupported(linkPreview.type);
    }
    return false;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Message message, @Nullable ContentPreview preview, int size, int cornerRadius) {
    Tdlib.Album album = preview != null ? preview.getAlbum() : null;
    if (album != null) {
      // TODO album preview?
      TdApi.Message firstMessage = album.messages.get(album.messages.size() - 1);
      if (firstMessage != message) {
        return valueOf(tdlib, firstMessage, null, size, cornerRadius);
      }
    }

    //noinspection SwitchIntDdef
    switch (message.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR: {
        TdApi.LinkPreview linkPreview = ((TdApi.MessageText) message.content).linkPreview;
        if (linkPreview != null) {
          switch (linkPreview.type.getConstructor()) {
            case TdApi.LinkPreviewTypeVideo.CONSTRUCTOR: {
              TdApi.LinkPreviewTypeVideo video = (TdApi.LinkPreviewTypeVideo) linkPreview.type;
              if (video.video != null) {
                return valueOf(tdlib, video.video, size, cornerRadius, false);
              }
              break;
            }
            case TdApi.LinkPreviewTypeSticker.CONSTRUCTOR: {
              TdApi.LinkPreviewTypeSticker sticker = (TdApi.LinkPreviewTypeSticker) linkPreview.type;
              return new MediaPreviewSimple(tdlib, cornerRadius, size, sticker.sticker);
            }
            case TdApi.LinkPreviewTypeAnimation.CONSTRUCTOR: {
              TdApi.LinkPreviewTypeAnimation animation = (TdApi.LinkPreviewTypeAnimation) linkPreview.type;
              if (animation.animation.thumbnail != null || animation.animation.minithumbnail != null) {
                return new MediaPreviewSimple(tdlib, size, cornerRadius, animation.animation.thumbnail, animation.animation.minithumbnail);
              }
              break;
            }
            case TdApi.LinkPreviewTypeVideoNote.CONSTRUCTOR: {
              TdApi.LinkPreviewTypeVideoNote videoNote = (TdApi.LinkPreviewTypeVideoNote) linkPreview.type;
              return new MediaPreviewSimple(tdlib, size, size / 2, videoNote.videoNote.thumbnail, videoNote.videoNote.minithumbnail);
            }
            case TdApi.LinkPreviewTypeVoiceNote.CONSTRUCTOR: {
              // TODO voice note preview?
              break;
            }
            case TdApi.LinkPreviewTypeDocument.CONSTRUCTOR: {
              TdApi.LinkPreviewTypeDocument document = (TdApi.LinkPreviewTypeDocument) linkPreview.type;
              return valueOf(tdlib, document.document, size, cornerRadius);
            }
            case TdApi.LinkPreviewTypePhoto.CONSTRUCTOR: {
              TdApi.LinkPreviewTypePhoto photo = (TdApi.LinkPreviewTypePhoto) linkPreview.type;
              return valueOf(tdlib, photo.photo, size, cornerRadius, false);
            }
            case TdApi.LinkPreviewTypeAlbum.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeApp.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeArticle.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeAudio.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeBackground.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeChannelBoost.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeChat.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeDirectMessagesChat.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeEmbeddedAudioPlayer.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeEmbeddedVideoPlayer.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeInvoice.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeMessage.CONSTRUCTOR:
            case TdApi.LinkPreviewTypePremiumGiftCode.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeShareableChatFolder.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeStickerSet.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeStory.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeStoryAlbum.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeSupergroupBoost.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeTheme.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeUnsupported.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeUpgradedGift.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeGiftCollection.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeGiftAuction.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeLiveStory.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeUser.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeVideoChat.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeGroupCall.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeWebApp.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeEmbeddedAnimationPlayer.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeExternalAudio.CONSTRUCTOR:
            case TdApi.LinkPreviewTypeExternalVideo.CONSTRUCTOR: {
              // TODO support more types
              break;
            }
            default:
              Td.assertLinkPreviewType_a9a3ffcd();
              throw Td.unsupported(linkPreview.type);
          }
        }
        break;
      }
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.MessagePhoto messagePhoto = (TdApi.MessagePhoto) message.content;
        return valueOf(tdlib, messagePhoto.photo, size, cornerRadius, messagePhoto.isSecret || messagePhoto.hasSpoiler);
      }
      case TdApi.MessagePaidMedia.CONSTRUCTOR: {
        TdApi.MessagePaidMedia paidMedia = (TdApi.MessagePaidMedia) message.content;
        if (paidMedia.media.length > 0) {
          TdApi.PaidMedia media = paidMedia.media[0];
          switch (media.getConstructor()) {
            case TdApi.PaidMediaPreview.CONSTRUCTOR: {
              TdApi.PaidMediaPreview paidPreview = (TdApi.PaidMediaPreview) media;
              return new MediaPreviewSimple(tdlib, size, cornerRadius, null, paidPreview.minithumbnail, MediaPreviewSimple.IconType.STAR);
            }
            case TdApi.PaidMediaPhoto.CONSTRUCTOR: {
              TdApi.PaidMediaPhoto paidPhoto = (TdApi.PaidMediaPhoto) media;
              return valueOf(tdlib, paidPhoto.photo, size, cornerRadius, false);
            }
            case TdApi.PaidMediaVideo.CONSTRUCTOR: {
              TdApi.PaidMediaVideo paidVideo = (TdApi.PaidMediaVideo) media;
              if (paidVideo.video.thumbnail != null || paidVideo.video.minithumbnail != null) {
                return valueOf(tdlib, paidVideo.video, size, cornerRadius, false);
              }
              break;
            }
            case TdApi.PaidMediaUnsupported.CONSTRUCTOR: {
              // TODO(server): preview fallback?
              break;
            }
            default:
              Td.assertPaidMedia_a2956719();
              throw Td.unsupported(media);
          }
        }
        break;
      }
      case TdApi.MessageLocation.CONSTRUCTOR: {
        // map preview
        return valueOf(tdlib, ((TdApi.MessageLocation) message.content).location, null, size, cornerRadius);
      }
      case TdApi.MessageVenue.CONSTRUCTOR: {
        // map preview
        TdApi.Venue venue = ((TdApi.MessageVenue) message.content).venue;
        return valueOf(tdlib, venue, null, size, cornerRadius);
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
        TdApi.ChatPhoto photo = ((TdApi.MessageChatChangePhoto) message.content).photo;
        TdApi.PhotoSize thumbnail = Td.findSmallest(photo.sizes);
        if (thumbnail != null || photo.minithumbnail != null) {
          return new MediaPreviewSimple(tdlib, size, cornerRadius, TD.toThumbnail(thumbnail), photo.minithumbnail);
        }
        break;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.MessageVideo messageVideo = (TdApi.MessageVideo) message.content;
        return valueOf(tdlib, messageVideo.video, size, cornerRadius, messageVideo.isSecret || messageVideo.hasSpoiler);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.MessageAnimation messageAnimation = (TdApi.MessageAnimation) message.content;
        return valueOf(tdlib, messageAnimation.animation, size, cornerRadius, messageAnimation.isSecret || messageAnimation.hasSpoiler);
      }
      case TdApi.MessageGame.CONSTRUCTOR: {
        TdApi.Game game = ((TdApi.MessageGame) message.content).game;
        return valueOf(tdlib, game, size, cornerRadius);
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        // doc preview
        TdApi.Document document = ((TdApi.MessageDocument) message.content).document;
        return valueOf(tdlib, document, size, cornerRadius);
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        // audio note preview
        TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
        if (audio.albumCoverThumbnail != null || audio.albumCoverMinithumbnail != null) {
          return new MediaPreviewSimple(tdlib, size, cornerRadius, audio.albumCoverThumbnail, audio.albumCoverMinithumbnail);
        }
        break;
      }
      case TdApi.MessageSticker.CONSTRUCTOR: {
        TdApi.Sticker sticker = ((TdApi.MessageSticker) message.content).sticker;
        return new MediaPreviewSimple(tdlib, size, cornerRadius, sticker);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        TdApi.MessageVideoNote messageVideoNote = (TdApi.MessageVideoNote) message.content;
        TdApi.VideoNote videoNote = messageVideoNote.videoNote;
        return new MediaPreviewSimple(tdlib, size, size / 2, videoNote.thumbnail, videoNote.minithumbnail, messageVideoNote.isSecret);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        // TODO voice note preview?
        TdApi.VoiceNote voiceNote = ((TdApi.MessageVoiceNote) message.content).voiceNote;
        break;
      }
      case TdApi.MessageGiftedPremium.CONSTRUCTOR: {
        TdApi.MessageGiftedPremium giftedPremium = (TdApi.MessageGiftedPremium) message.content;
        if (giftedPremium.sticker != null)
          return new MediaPreviewSimple(tdlib, size, cornerRadius, giftedPremium.sticker);
        break;
      }
      case TdApi.MessageGiftedStars.CONSTRUCTOR: {
        TdApi.MessageGiftedStars giftedStars = (TdApi.MessageGiftedStars) message.content;
        if (giftedStars.sticker != null)
          return new MediaPreviewSimple(tdlib, size, cornerRadius, giftedStars.sticker);
        break;
      }
      case TdApi.MessageGiftedTon.CONSTRUCTOR: {
        TdApi.MessageGiftedTon giftedTon = (TdApi.MessageGiftedTon) message.content;
        if (giftedTon.sticker != null)
          return new MediaPreviewSimple(tdlib, size, cornerRadius, giftedTon.sticker);
        break;
      }
      case TdApi.MessagePremiumGiftCode.CONSTRUCTOR: {
        TdApi.Sticker sticker = ((TdApi.MessagePremiumGiftCode) message.content).sticker;
        if (sticker != null)
          return new MediaPreviewSimple(tdlib, size, cornerRadius, sticker);
        break;
      }
      case TdApi.MessageGiveaway.CONSTRUCTOR: {
        TdApi.Sticker sticker = ((TdApi.MessageGiveaway) message.content).sticker;
        if (sticker != null)
          return new MediaPreviewSimple(tdlib, size, cornerRadius, sticker);
        break;
      }
      case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
      case TdApi.MessageExpiredVideo.CONSTRUCTOR:
      case TdApi.MessageExpiredVideoNote.CONSTRUCTOR:
      case TdApi.MessageExpiredVoiceNote.CONSTRUCTOR:
      case TdApi.MessageContact.CONSTRUCTOR:
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
      case TdApi.MessageDice.CONSTRUCTOR:
      case TdApi.MessagePoll.CONSTRUCTOR:
      case TdApi.MessageStory.CONSTRUCTOR:
      case TdApi.MessageInvoice.CONSTRUCTOR:
      case TdApi.MessageCall.CONSTRUCTOR:
      case TdApi.MessageGroupCall.CONSTRUCTOR:
      case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
      case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
      case TdApi.MessageVideoChatEnded.CONSTRUCTOR:
      case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
      case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
      case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
      case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
      case TdApi.MessageChatAddMembers.CONSTRUCTOR:
      case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
      case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
      case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
      case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
      case TdApi.MessagePinMessage.CONSTRUCTOR:
      case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
      case TdApi.MessageChatSetBackground.CONSTRUCTOR:
      case TdApi.MessageChatSetTheme.CONSTRUCTOR:
      case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR:
      case TdApi.MessageChatBoost.CONSTRUCTOR:
      case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
      case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
      case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
      case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
      case TdApi.MessageSuggestBirthdate.CONSTRUCTOR:
      case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
      case TdApi.MessageGameScore.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
      case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
      case TdApi.MessagePaymentRefunded.CONSTRUCTOR:
      case TdApi.MessageGiveawayCreated.CONSTRUCTOR:
      case TdApi.MessageGiveawayCompleted.CONSTRUCTOR:
      case TdApi.MessageGiveawayWinners.CONSTRUCTOR:
      case TdApi.MessageGiveawayPrizeStars.CONSTRUCTOR:
      case TdApi.MessagePaidMessagePriceChanged.CONSTRUCTOR:
      case TdApi.MessagePaidMessagesRefunded.CONSTRUCTOR:
      case TdApi.MessageDirectMessagePriceChanged.CONSTRUCTOR:
      case TdApi.MessageGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOffer.CONSTRUCTOR:
      case TdApi.MessageUpgradedGiftPurchaseOfferRejected.CONSTRUCTOR:
      case TdApi.MessageStakeDice.CONSTRUCTOR:
      case TdApi.MessageRefundedUpgradedGift.CONSTRUCTOR:
      case TdApi.MessageContactRegistered.CONSTRUCTOR:
      case TdApi.MessageUsersShared.CONSTRUCTOR:
      case TdApi.MessageChatShared.CONSTRUCTOR:
      case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR:
      case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
      case TdApi.MessageWebAppDataReceived.CONSTRUCTOR:
      case TdApi.MessagePassportDataSent.CONSTRUCTOR:
      case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
      case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR:
      case TdApi.MessageChecklist.CONSTRUCTOR:
      case TdApi.MessageChecklistTasksDone.CONSTRUCTOR:
      case TdApi.MessageChecklistTasksAdded.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostApprovalFailed.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostApproved.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostDeclined.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostPaid.CONSTRUCTOR:
      case TdApi.MessageSuggestedPostRefunded.CONSTRUCTOR:
      case TdApi.MessageUnsupported.CONSTRUCTOR: {
        // No media preview.
        break;
      }
      default: {
        Td.assertMessageContent_11bff7df();
        throw Td.unsupported(message.content);
      }
    }
    return null;
  }

  protected final int size;
  protected int cornerRadius;

  protected MediaPreview (int size, int cornerRadius) {
    this.size = size;
    this.cornerRadius = cornerRadius;
  }

  @CallSuper
  public void setCornerRadius (int radius) {
    this.cornerRadius = radius;
  }

  public final int getCornerRadius () {
    return cornerRadius;
  }

  @Override
  public int getWidth () {
    return size;
  }

  @Override
  public int getHeight () {
    return size;
  }

  public abstract boolean needPlaceholder (ComplexReceiver receiver);
  public void requestFiles (ComplexReceiver receiver, boolean invalidate) {
    receiver.clear();
  }

  public final <T extends View & DrawableProvider> void draw (T view, Canvas c, ComplexReceiver receiver, float x, float y, float alpha) {
    draw(view, c, receiver, x, y, getWidth(), getHeight(), cornerRadius, alpha);
  }

  public abstract <T extends View & DrawableProvider> void draw (T view, Canvas c, ComplexReceiver receiver, float x, float y, float width, float height, int cornerRadius, float alpha);
}
