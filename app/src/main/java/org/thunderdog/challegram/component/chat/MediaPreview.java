package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.DrawableProvider;

import java.io.File;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.td.Td;

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

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Video video, int size, int cornerRadius) {
    if (video.thumbnail != null || video.minithumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, video.thumbnail, video.minithumbnail);
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
    if (profilePhoto != null || thumbnail != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, profilePhoto, thumbnail);
    }
    return null;
  }

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.ChatPhotoInfo photoInfo, int size, int cornerRadius) {
    if (photoInfo != null) {
      return new MediaPreviewSimple(tdlib, size, cornerRadius, photoInfo);
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

  public static MediaPreview valueOf (Tdlib tdlib, TdApi.Message message, @Nullable TD.ContentPreview preview, int size, int cornerRadius) {
    Tdlib.Album album = preview != null ? preview.getAlbum() : null;
    if (album != null) {
      // TODO album preview?
      TdApi.Message firstMessage = album.messages.get(album.messages.size() - 1);
      if (firstMessage != message) {
        return valueOf(tdlib, firstMessage, null, size, cornerRadius);
      }
    }

    switch (message.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR: {
        TdApi.WebPage webPage = ((TdApi.MessageText) message.content).webPage;
        if (webPage != null) {
          if (webPage.video != null) {
            // video preview
            return valueOf(tdlib, webPage.video, size, cornerRadius);
          } else if (webPage.sticker != null) {
            // sticker preview
            return new MediaPreviewSimple(tdlib, cornerRadius, size, webPage.sticker);
          } else if (webPage.animation != null) {
            // gif preview
            if (webPage.animation.thumbnail != null || webPage.animation.minithumbnail != null) {
              return new MediaPreviewSimple(tdlib, size, cornerRadius, webPage.animation.thumbnail, webPage.animation.minithumbnail);
            }
          } else if (webPage.videoNote != null) {
            return new MediaPreviewSimple(tdlib, size, size / 2, webPage.videoNote.thumbnail, webPage.videoNote.minithumbnail);
          } else if (webPage.voiceNote != null) {
            // TODO voice note preview?
          } else if (webPage.document != null) {
            // doc preview
            return valueOf(tdlib, webPage.document, size, cornerRadius);
          } else if (webPage.photo != null) {
            TdApi.PhotoSize thumbnail = Td.findSmallest(webPage.photo);
            if (thumbnail != null || webPage.photo.minithumbnail != null) {
              return new MediaPreviewSimple(tdlib, size, cornerRadius, TD.toThumbnail(thumbnail), webPage.photo.minithumbnail);
            }
          }
        }
        break;
      }
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.Photo photo = ((TdApi.MessagePhoto) message.content).photo;
        TdApi.PhotoSize thumbnail = Td.findSmallest(photo);
        if (thumbnail != null || photo.minithumbnail != null) {
          return new MediaPreviewSimple(tdlib, size, cornerRadius, TD.toThumbnail(thumbnail), photo.minithumbnail);
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
        TdApi.Video video = ((TdApi.MessageVideo) message.content).video;
        return valueOf(tdlib, video, size, cornerRadius);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Animation animation = ((TdApi.MessageAnimation) message.content).animation;
        if (animation.minithumbnail != null || animation.thumbnail != null) {
          return new MediaPreviewSimple(tdlib, size, cornerRadius, animation.thumbnail, animation.minithumbnail);
        }
        break;
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
        TdApi.VideoNote videoNote = ((TdApi.MessageVideoNote) message.content).videoNote;
        return new MediaPreviewSimple(tdlib, size, size / 2, videoNote.thumbnail, videoNote.minithumbnail);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        // TODO voice note preview?
        TdApi.VoiceNote voiceNote = ((TdApi.MessageVoiceNote) message.content).voiceNote;
        break;
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
