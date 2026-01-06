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
 * File created on 04/08/2015 at 17:36
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.preview.PreviewLayout;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.Td;

public class TGWebPage implements FileProgressComponent.SimpleListener, MediaWrapper.OnClickListener, TGInlineKeyboard.ClickListener, Destroyable {
  private static final int MAX_TITLE_LINES = 4;
  private static final int MAX_DESCRIPTION_LINES = 8;

  public static final int TYPE_VIDEO = 1;
  public static final int TYPE_ARTICLE = 2;
  public static final int TYPE_GIF = 3;
  public static final int TYPE_PHOTO = 4;
  public static final int TYPE_APP = 5;
  public static final int TYPE_DOCUMENT = 6;
  public static final int TYPE_STICKER = 7;

  public static final int TYPE_TELEGRAM_CHANNEL = 10;
  public static final int TYPE_TELEGRAM_MEGAGROUP = 11;
  public static final int TYPE_TELEGRAM_CHAT = 12;
  public static final int TYPE_TELEGRAM_BOT = 13;
  public static final int TYPE_TELEGRAM_USER = 14;
  public static final int TYPE_TELEGRAM_MESSAGE = 15;
  public static final int TYPE_TELEGRAM_ALBUM = 16;
  public static final int TYPE_TELEGRAM_THEME = 17;
  public static final int TYPE_TELEGRAM_BACKGROUND = 18;
  public static final int TYPE_TELEGRAM_AD = 19;

  private static boolean isTelegramType (int type) {
    switch (type) {
      case TYPE_TELEGRAM_CHANNEL:
      case TYPE_TELEGRAM_MEGAGROUP:
      case TYPE_TELEGRAM_CHAT:
      case TYPE_TELEGRAM_BOT:
      case TYPE_TELEGRAM_USER:
      case TYPE_TELEGRAM_MESSAGE:
      case TYPE_TELEGRAM_ALBUM:
      case TYPE_TELEGRAM_THEME:
      case TYPE_TELEGRAM_BACKGROUND:
      case TYPE_TELEGRAM_AD:
        return true;
    }
    return false;
  }

  private static final int FLAG_DESTROYED = 1;
  private static final int FLAG_PROCESSED = 1 << 1;

  private int flags;

  private boolean isImageBig;

  /*
  * article, photo, audio, video, document, profile, app
  * */

  private int type;
  private int availWidth, height;

  private final long chatId;
  private long messageId;
  private final TGMessageText parent;
  private final TdApi.LinkPreview linkPreview;
  private final String url;
  private final TdApi.LinkPreviewOptions linkPreviewOptions;

  private BaseComponent component;
  private int componentY;

  private float instantTextWidth;
  private @Nullable String instantText;
  private @Nullable List<MediaItem> instantItems;
  private int instantPosition;
  private final boolean isAdvertisement;

  public TGWebPage (@NonNull TGMessageText parent, TdApi.LinkPreview linkPreview, String url, @Nullable TdApi.LinkPreviewOptions linkPreviewOptions) {
    if (paddingLeft == 0) {
      initSizes();
    }
    /*if (whitePaint == null) {
      initPaints();
    }*/
    this.viewProvider = parent.currentViews;
    this.linkPreviewOptions = linkPreviewOptions;
    this.parent = parent;
    this.linkPreview = linkPreview;
    this.url = url;
    this.chatId = parent.getChatId();
    this.messageId = parent.getId();
    this.isAdvertisement = parent.isSponsoredMessage();
  }

  public int getType () {
    return this.type;
  }

  @SuppressWarnings ("SuspiciousNameCombination")
  public void buildLayout (int width) {
    this.availWidth = width;

    int maxWidth = width - paddingLeft;
    height = 0;

    if (isAdvertisement) {
      this.type = TYPE_TELEGRAM_AD;
    } else if (this.type == 0) {
      final TdApi.LinkPreviewType type = linkPreview.type;

      switch (type.getConstructor()) {
        case TdApi.LinkPreviewTypeVideo.CONSTRUCTOR: {
          this.type = TYPE_VIDEO;
          break;
        }
        case TdApi.LinkPreviewTypeApp.CONSTRUCTOR: {
          this.type = TYPE_APP;
          break;
        }
        case TdApi.LinkPreviewTypeArticle.CONSTRUCTOR: {
          this.type = TYPE_ARTICLE;
          break;
        }
        case TdApi.LinkPreviewTypeEmbeddedVideoPlayer.CONSTRUCTOR: {
          TdApi.LinkPreviewTypeEmbeddedVideoPlayer videoPlayer = (TdApi.LinkPreviewTypeEmbeddedVideoPlayer) type;
          if (videoPlayer.thumbnail != null) {
            this.type = TYPE_PHOTO;
          } else {
            this.type = TYPE_ARTICLE;
          }
          break;
        }
        case TdApi.LinkPreviewTypeAnimation.CONSTRUCTOR: {
          this.type = TYPE_GIF;
          break;
        }
        case TdApi.LinkPreviewTypePhoto.CONSTRUCTOR: {
          this.type = TYPE_PHOTO;
          break;
        }
        case TdApi.LinkPreviewTypeSticker.CONSTRUCTOR: {
          TdApi.LinkPreviewTypeSticker sticker = (TdApi.LinkPreviewTypeSticker) type;
          if (Math.max(sticker.sticker.width, sticker.sticker.height) <= STICKER_SIZE_LIMIT) {
            this.type = TYPE_STICKER;
          } else {
            this.type = TYPE_PHOTO;
          }
          break;
        }
        case TdApi.LinkPreviewTypeVoiceNote.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeDocument.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeAudio.CONSTRUCTOR: {
          this.type = TYPE_DOCUMENT;
          break;
        }

        case TdApi.LinkPreviewTypeChat.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeDirectMessagesChat.CONSTRUCTOR: {
          this.type = TYPE_TELEGRAM_CHAT;
          break;
        }
        case TdApi.LinkPreviewTypeUser.CONSTRUCTOR: {
          TdApi.LinkPreviewTypeUser user = (TdApi.LinkPreviewTypeUser) type;
          if (user.isBot) {
            this.type = TYPE_TELEGRAM_BOT;
          } else {
            this.type = TYPE_TELEGRAM_USER;
          }
          break;
        }
        case TdApi.LinkPreviewTypeMessage.CONSTRUCTOR: {
          this.type = TYPE_TELEGRAM_MESSAGE;
          break;
        }
        case TdApi.LinkPreviewTypeAlbum.CONSTRUCTOR: {
          this.type = TYPE_TELEGRAM_ALBUM;
          break;
        }
        case TdApi.LinkPreviewTypeBackground.CONSTRUCTOR: {
          this.type = TYPE_TELEGRAM_BACKGROUND;
          break;
        }
        case TdApi.LinkPreviewTypeTheme.CONSTRUCTOR: {
          this.type = TYPE_TELEGRAM_THEME;
          break;
        }

        case TdApi.LinkPreviewTypeEmbeddedAnimationPlayer.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeEmbeddedAudioPlayer.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeStickerSet.CONSTRUCTOR:

        case TdApi.LinkPreviewTypeExternalAudio.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeExternalVideo.CONSTRUCTOR:
        
        case TdApi.LinkPreviewTypeChannelBoost.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeInvoice.CONSTRUCTOR:
        case TdApi.LinkPreviewTypePremiumGiftCode.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeShareableChatFolder.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeStory.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeStoryAlbum.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeLiveStory.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeSupergroupBoost.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeVideoChat.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeGroupCall.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeVideoNote.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeWebApp.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeUpgradedGift.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeGiftCollection.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeGiftAuction.CONSTRUCTOR:
        case TdApi.LinkPreviewTypeUnsupported.CONSTRUCTOR:
          break;

        default: {
          Td.assertLinkPreviewType_a9a3ffcd();
          if (BuildConfig.DEBUG) {
            Tracer.onTdlibHandlerError(new UnsupportedOperationException(type.toString()));
          }
          Log.w("Unsupported WebPage content, type: %s", type);
          break;
        }
      }
    }

    if (!isTelegramType(type) && parent.tdlib().ui().getTelegramLinkType(url) == TdlibUi.TME_URL_MESSAGE) {
      this.type = TYPE_TELEGRAM_MESSAGE;
    }

    if (hasHeader()) {
      buildHeader(linkPreview, maxWidth);
    }

    if (linkPreview.showLargeMedia || mediaWrapper == null) {
      int duration = Td.getDuration(linkPreview.type);
      if (duration != 0) {
        setDuration(Strings.buildDuration(duration));
      } else if (this.type == TYPE_STICKER) {
        TdApi.Sticker sticker = ((TdApi.LinkPreviewTypeSticker) linkPreview.type).sticker;
        setDuration(Strings.buildSize(sticker.sticker.size));
      }
      if (needInstantPreview(linkPreview)) {
        if (Td.getAnimation(linkPreview.type) != null) {
          buildGif(linkPreview, maxWidth);
        } else if (Td.getVideo(linkPreview.type) != null) {
          buildVideo(linkPreview, maxWidth);
        } else {
          buildPhoto(linkPreview, maxWidth);
        }
      } else {
        switch (this.type) {
          case TYPE_TELEGRAM_AD: {
            break;
          }
          case TYPE_VIDEO: {
            buildVideo(linkPreview, maxWidth);
            break;
          }
          case TYPE_GIF: {
            buildGif(linkPreview, maxWidth);
            break;
          }
          case TYPE_PHOTO: {
            buildPhoto(linkPreview, maxWidth);
            break;
          }
          default: {
            if (type == TYPE_TELEGRAM_BACKGROUND) {
              TdApi.LinkPreviewTypeBackground background = (TdApi.LinkPreviewTypeBackground) linkPreview.type;
              String[] partedUrl = url.split("/bg/");
              if (partedUrl.length == 2) {
                this.component = new WallpaperComponent(parent, linkPreview, partedUrl[1]);
              } else if (background.document != null) {
                this.component = new FileComponent(parent, parent.getMessage(), background.document);
              } else {
                this.component = null;
              }
            } else if (Td.getAudio(linkPreview.type) != null) {
              this.component = new FileComponent(parent, parent.getMessage(), Td.getAudio(linkPreview.type), null, null);
            } else if (Td.getVoiceNote(linkPreview.type) != null) {
              this.component = new FileComponent(parent, parent.getMessage(), Td.getVoiceNote(linkPreview.type), null, null);
            } else if (Td.getDocument(linkPreview.type) != null) {
              this.component = new FileComponent(parent, parent.getMessage(), Td.getDocument(linkPreview.type));
            } else {
              this.component = null;
            }
            if (this.component != null) {
              this.component.setViewProvider(viewProvider);
              this.component.buildLayout(maxWidth);
              if (hasHeader()) {
                height += contentPadding;
              }
              this.componentY = height;
              this.height += component.getHeight();
            } else if (Td.getVideo(linkPreview.type) != null) {
              buildVideo(linkPreview, maxWidth);
            } else if (Td.getAnimation(linkPreview.type) != null) {
              buildGif(linkPreview, maxWidth);
            } else if (Td.getSticker(linkPreview.type) != null || Td.hasPhoto(linkPreview.type)) {
              buildPhoto(linkPreview, maxWidth);
            }
            break;
          }
        }
      }
    }
    buildRippleButton();
    if ((flags & FLAG_PROCESSED) == 0 && needsSpecialProcessing()) {
      flags |= FLAG_PROCESSED;
      if (linkPreview.type.getConstructor() == TdApi.LinkPreviewTypeAlbum.CONSTRUCTOR) {
        processAlbum((TdApi.LinkPreviewTypeAlbum) linkPreview.type, true);
      }
    }
    setViewProvider(viewProvider);
    height += lineWidth;
  }

  private List<MediaItem> processAlbum (TdApi.LinkPreviewTypeAlbum album, boolean updateTexts) {
    MediaWrapper currentWrapper = mediaWrapper;
    TdApi.File currentFile = currentWrapper != null ? currentWrapper.getTargetFile() : null;
    int currentFileId = currentFile != null ? currentFile.id : 0;
    final ArrayList<MediaItem> mediaItems = new ArrayList<>(album.media.length);
    int position = -1;
    int i = 0;

    for (TdApi.LinkPreviewAlbumMedia media : album.media) {
      MediaItem item;
      switch (media.getConstructor()) {
        case TdApi.LinkPreviewAlbumMediaPhoto.CONSTRUCTOR: {
          TdApi.LinkPreviewAlbumMediaPhoto photo = (TdApi.LinkPreviewAlbumMediaPhoto) media;
          item = MediaItem.valueOf(parent.context(), parent.tdlib(), photo.photo, null);
          break;
        }
        case TdApi.LinkPreviewAlbumMediaVideo.CONSTRUCTOR: {
          TdApi.LinkPreviewAlbumMediaVideo video = (TdApi.LinkPreviewAlbumMediaVideo) media;
          item = MediaItem.valueOf(parent.context(), parent.tdlib(), video.video, null, null, null);
          break;
        }
        default: {
          Td.assertLinkPreviewAlbumMedia_8c33c943();
          throw Td.unsupported(media);
        }
      }
      item.setSourceMessage(parent.getMessage());
      if (position == -1 && item.getFileId() == currentFileId) {
        TdApi.FormattedText caption = StringUtils.isEmpty(album.caption) ? null : new TdApi.FormattedText(album.caption, Text.findEntities(album.caption, Text.ENTITY_FLAGS_EXTERNAL));
        item.setCaption(caption);
        position = i;
      }
      mediaItems.add(item);
      i++;
    }

    if (mediaItems.size() <= 1) {
      return null;
    }
    if (position == -1) {
      position = 0;
    }

    if (updateTexts) {
      final String text = Lang.getString(R.string.XofY, position + 1, mediaItems.size());
      final float textWidth = U.measureText(text, Paints.whiteMediumPaint(13f, false, true));
      setInstantItems(mediaItems, text, textWidth, position);
    }

    return mediaItems;
  }

  public List<MediaItem> getInstantItems () {
    if (linkPreview.type.getConstructor() != TdApi.LinkPreviewTypeAlbum.CONSTRUCTOR) {
      return null;
    }
    return instantItems = processAlbum((TdApi.LinkPreviewTypeAlbum) linkPreview.type, false);
  }

  public int getInstantPosition () {
    return instantPosition;
  }

  private void setInstantItems (@NonNull ArrayList<MediaItem> items, String text, float textWidth, int position) {
    if (isDestroyed()) {
      return;
    }
    this.instantText = text;
    this.instantTextWidth = textWidth;
    this.instantPosition = position;
    if (this.mediaWrapper != null) {
      this.mediaWrapper.getFileProgress().setIgnoreLoaderClicks(true);
    }
    if (viewProvider != null) {
      viewProvider.invalidate();
    }
  }

  public boolean isDestroyed () {
    return (flags & FLAG_DESTROYED) != 0;
  }

  @Override
  public void performDestroy () {
    if (!isDestroyed()) {
      flags |= FLAG_DESTROYED;
      if (component != null) {
        component.performDestroy();
      }
      if (siteName != null) {
        siteName.performDestroy();
      }
      if (title != null) {
        title.performDestroy();
      }
      if (description != null) {
        description.performDestroy();
      }
    }
  }

  public MediaWrapper getMediaWrapper () {
    return mediaWrapper;
  }

  private static boolean needInstantPreview (TdApi.LinkPreview linkPreview) {
    if (TD.hasInstantView(linkPreview)) {
      TdApi.Photo photo = Td.getPhoto(linkPreview.type);
      if (photo != null && !TD.isPhotoEmpty(photo)) {
        TdApi.PhotoSize size = Td.findBiggest(photo);
        return size != null && Math.max(size.width, size.height) >= 400;
      }
      TdApi.Video video = Td.getVideo(linkPreview.type);
      return video != null && Math.max(video.width, video.height) >= 400;
    }
    return false;
  }

  public boolean hasMedia () {
    // The only TdApi.FormattedText inside TdApi.WebPage is `description`
    return description != null && description.hasMedia();
  }

  public void requestTextMedia (ComplexReceiver receiver, long startKey) {
    if (hasMedia()) {
      description.requestMedia(receiver, startKey, Integer.MAX_VALUE);
    } else {
      receiver.clearReceiversWithHigherKey(startKey);
    }
  }

  private int contentY;
  private int imageX, imageY, imageWidth, imageHeight;

  private Text siteName, title, description;

  private boolean setSmallMedia () {
    TdApi.Sticker sticker = Td.getSticker(linkPreview.type);
    if (sticker != null && (Math.max(sticker.width, sticker.height) <= STICKER_SIZE_LIMIT || Td.isAnimated(sticker.format))) {
      // simple animated sticker
      return false; // TODO
    } else if (sticker != null) {
      setSmallMediaWrapper(new MediaWrapper(parent.context(), parent.tdlib(), TD.convertToPhoto(sticker), chatId, messageId, parent, false));
      return true;
    } else if (Td.getVideo(linkPreview.type) != null) {
      setSmallMediaWrapper(new MediaWrapper(parent.context(), parent.tdlib(), Td.getVideo(linkPreview.type), Td.getPhoto(linkPreview.type), chatId, messageId, parent, false));
      return true;
    } else {
      TdApi.Photo photo = Td.getPhoto(linkPreview.type);
      if (photo != null) {
        setSmallMediaWrapper(new MediaWrapper(parent.context(), parent.tdlib(), photo, chatId, messageId, parent, false, false, EmbeddedService.parse(linkPreview)));
        return true;
      }
    }
    return false;
  }

  private void setSmallMediaWrapper (MediaWrapper mediaWrapper) {
    isImageBig = false;
    setMediaWrapper(mediaWrapper);
    if (!mediaWrapper.isVideo()) {
      mediaWrapper.setHideLoader(true);
    }
    mediaWrapper.setOnClickListener(this);
    mediaWrapper.buildContent(imageSize, imageSize);
    mediaWrapper.setViewProvider(viewProvider);
  }

  @Override
  public boolean onClick (View view, MediaWrapper wrapper) {
    if ((isSmallPhotoType(type) && !isImageBig && Td.hasPhoto(linkPreview.type)) || instantItems != null || type == TYPE_VIDEO) {
      mediaWrapper.fileProgress.downloadIfNeeded();
      MediaViewController.openFromMessage(parent);
      return true;
    }
    return open(view, true);
  }

  @Override
  public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    return open(view, true);
  }

  public boolean open (View view, boolean allowRipple) {
    if (getType() == TYPE_TELEGRAM_BACKGROUND) {
      parent.tdlib().ui().openUrl(parent.controller(), url, rippleButton.firstButton().openParameters(view).disableInstantView());
      return true;
    }
    
    if (mediaWrapper != null && allowRipple && isTelegramType(type)) {
      MediaViewController.openFromMessage(parent);
      return true;
    }
    if (allowRipple && rippleButton != null && rippleButton.clickFirstButton(view)) {
      return true;
    }
    switch (getType()) {
      case TYPE_TELEGRAM_BOT:
      case TYPE_TELEGRAM_CHANNEL:
      case TYPE_TELEGRAM_MEGAGROUP:
      case TYPE_TELEGRAM_USER:
      case TYPE_TELEGRAM_CHAT:
      case TYPE_TELEGRAM_MESSAGE:
      case TYPE_TELEGRAM_ALBUM: {
        parent.tdlib().ui().openUrl(parent.controller(), url, rippleButton.firstButton().openParameters(view).disableInstantView());
        break;
      }
      case TYPE_TELEGRAM_AD: {
        parent.openSponsoredMessage();
        break;
      }
      case TGWebPage.TYPE_PHOTO:
      case TGWebPage.TYPE_GIF: {
        MediaViewController.openFromMessage(parent);
        break;
      }
      case TGWebPage.TYPE_ARTICLE:
      case TGWebPage.TYPE_VIDEO:
      default: {
        if (mediaWrapper != null && mediaWrapper.isVideo()) {
          MediaViewController.openFromMessage(parent);
          break;
        }
        if (!PreviewLayout.show(parent.controller(), linkPreview, parent.isSecretChat())) {
          if (mediaWrapper != null && Td.hasPhoto(linkPreview.type)) {
            MediaViewController.openFromMessage(parent);
          } else {
            parent.tdlib().ui().openUrl(parent.controller(), url, new TdlibUi.UrlOpenParameters(rippleButton.firstButton().openParameters(view)).disableEmbedView());
          }
        }
        break;
      }
    }
    return true;
  }

  public TdApi.LinkPreview getLinkPreview () {
    return linkPreview;
  }

  public @Nullable TdApi.LinkPreviewOptions getLinkPreviewOptions () {
    return linkPreviewOptions;
  }

  public boolean isPreviewOf (String url) {
    return isPreviewOf(linkPreview.url, url);
  }

  public static boolean isPreviewOf (String webPageUrl, String url) {
    if (StringUtils.isEmpty(url))
      return false;
    if (!url.contains("://"))
      url = "https://" + url;
    try {
      Uri uri = Uri.parse(url);
      Uri webPageUri = Uri.parse(webPageUrl);

      String host = uri.getHost().toLowerCase(Locale.ROOT).replaceAll("^(?:www\\.|m\\.)", "");
      String webPageHost = webPageUri.getHost().toLowerCase(Locale.ROOT).replaceAll("^(?:www\\.|m\\.)", "");

      return
        StringUtils.equalsOrBothEmpty(host, webPageHost) &&
        StringUtils.equalsOrBothEmpty(uri.getPath(), webPageUri.getPath()) &&
        StringUtils.equalsOrBothEmpty(uri.getQuery(), webPageUri.getQuery());
    } catch (Throwable t) {
      Log.i("Invalid url", t);
    }
    return false;
  }

  @Override
  public void onStateChanged (TdApi.File file, @TdlibFilesManager.FileDownloadState int state) {

  }

  @Override
  public void onProgress (TdApi.File file, float progress) {

  }

  private static boolean isSmallPhotoType (int type) {
    switch (type) {
      case TYPE_APP:
      case TYPE_ARTICLE:

      case TYPE_TELEGRAM_BOT:
      case TYPE_TELEGRAM_CHANNEL:
      case TYPE_TELEGRAM_MEGAGROUP:
      case TYPE_TELEGRAM_USER:
      case TYPE_TELEGRAM_CHAT: {
        return true;
      }
    }
    return false;
  }

  private static final float TEXT_PADDING = 4f, TEXT_PADDING_START = 2f;

  private void buildHeader (TdApi.LinkPreview linkPreview, int maxWidth) {
    final int textMaxWidth;
    int minHeight = 0;
    if (!linkPreview.showLargeMedia && setSmallMedia()) {
      textMaxWidth = maxWidth - imageMarginLeft - imageSize;
      imageX = availWidth - imageSize;
      imageY = imageOffset;
      imageWidth = imageHeight = imageSize;
      minHeight = imageY + imageHeight + lineAdd;
    } else {
      textMaxWidth = maxWidth;
    }

    boolean needLineWidthProvider = mediaWrapper != null && !isImageBig;
    int textHeight = 0;

    if (!StringUtils.isEmpty(linkPreview.siteName) || isTgWallpaper()) {
      textHeight += Screen.dp(TEXT_PADDING_START);

      final int textHeightFinal = textHeight;
      Text.LineWidthProvider provider = needLineWidthProvider ? (lineIndex, y, defaultMaxWidth, lineHeight) -> {
        y += textHeightFinal;
        if (y + lineHeight < imageY || y >= imageY + imageSize) {
          return defaultMaxWidth;
        } else {
          return textMaxWidth;
        }
      } : null;

      String actualSiteName = isTgWallpaper() ? Lang.getString(R.string.ChatBackground) : linkPreview.siteName;

      siteName = new Text.Builder(actualSiteName, maxWidth, TGMessage.getTextStyleProvider(), parent.getChatAuthorColorSet())
        .maxLineCount(2)
        .lineWidthProvider(provider)
        .textFlags(Text.FLAG_ALL_BOLD | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0))
        .clipTextArea()
        .viewProvider(viewProvider)
        .entities(new TextEntity[]{TextEntity.valueOf(parent.tdlib, actualSiteName, new TdApi.TextEntity(0, actualSiteName.length(), new TdApi.TextEntityTypeTextUrl(url)), parent.openParameters())}, null)
        .highlight(parent.getHighlightedText(Highlight.Pool.KEY_SITE_NAME, actualSiteName))
        .build();
      textHeight += siteName.getHeight();
    } else {
      siteName = null;
    }

    if (!StringUtils.isEmpty(linkPreview.title) && !isTgWallpaper()) {
      if (textHeight > 0)
        textHeight += Screen.dp(TEXT_PADDING);

      final int textHeightFinal = textHeight;
      Text.LineWidthProvider provider = needLineWidthProvider ? (lineIndex, y, defaultMaxWidth, lineHeight) -> {
        y += textHeightFinal;
        if (y + lineHeight < imageY || y >= imageY + imageSize) {
          return defaultMaxWidth;
        } else {
          return textMaxWidth;
        }
      } : null;

      title = new Text.Builder(linkPreview.title, maxWidth, TGMessage.getTextStyleProvider(), parent.getTextColorSet())
        .maxLineCount(TD.hasInstantView(linkPreview) ? -1 : MAX_TITLE_LINES).lineWidthProvider(provider)
        .textFlags(Text.FLAG_ALL_BOLD | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0))
        .viewProvider(viewProvider)
        .clipTextArea()
        .highlight(parent.getHighlightedText(Highlight.Pool.KEY_SITE_TITLE, linkPreview.title))
        .build();
      textHeight += title.getHeight();
    } else {
      title = null;
    }
    if (description != null) {
      description.performDestroy();
    }
    if (!Td.isEmpty(linkPreview.description)) {
      if (textHeight > 0)
        textHeight += Screen.dp(TEXT_PADDING);

      final int textHeightFinal = textHeight;
      Text.LineWidthProvider provider = needLineWidthProvider ? (lineIndex, y, defaultMaxWidth, lineHeight) -> {
        y += textHeightFinal;
        if (y + lineHeight < imageY || y >= imageY + imageSize) {
          return defaultMaxWidth;
        } else {
          return textMaxWidth;
        }
      } : null;

      description = new Text.Builder(linkPreview.description.text, maxWidth, TGMessage.getTextStyleProvider(), parent.getTextColorSet())
        .maxLineCount(MAX_DESCRIPTION_LINES)
        .lineWidthProvider(provider)
        .viewProvider(viewProvider)
        .textFlags(Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0)
        .entities(TextEntity.valueOf(parent.tdlib, linkPreview.description, parent.openParameters()), parent::invalidateTextMediaReceiver)
        .highlight(parent.getHighlightedText(Highlight.Pool.KEY_SITE_TEXT, linkPreview.description.text))
        .build();
      textHeight += description.getHeight();
    } else {
      description = null;
    }

    height += textHeight;
    height = Math.max(height, minHeight);

    if (component != null) {
      height += component.getHeight();
    }

    contentY = height;
  }

  private ViewProvider viewProvider;

  public void setViewProvider (ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    if (siteName != null) {
      siteName.setViewProvider(viewProvider);
    }
    if (title != null) {
      title.setViewProvider(viewProvider);
    }
    if (description != null) {
      description.setViewProvider(viewProvider);
    }
    if (mediaWrapper != null) {
      mediaWrapper.setViewProvider(viewProvider);
    }
    if (component != null) {
      component.setViewProvider(viewProvider);
    }
    if (rippleButton != null) {
      rippleButton.setViewProvider(viewProvider);
    }
  }

  public void notifyInvalidateTargetsChanged () {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().notifyInvalidateTargetsChanged();
    }
  }

  public void updateMessageId (long oldMessageId, long newMessageId, boolean success) {
    this.messageId = newMessageId;
    if (mediaWrapper != null) {
      mediaWrapper.updateMessageId(oldMessageId, newMessageId, success);
    }
    if (component != null) {
      if (component.getFileProgress() != null) {
        component.getFileProgress().updateMessageId(oldMessageId, newMessageId, success);
      }
    }
    if (instantItems != null) {
      for (MediaItem item : instantItems) {
        item.getFileProgress().updateMessageId(oldMessageId, newMessageId, success);
        item.setSourceMessage(parent.getMessage());
      }
    }
  }

  // Content builders

  private MediaWrapper mediaWrapper;

  private ImageFile simpleImageFile, simplePreview;
  private GifFile simpleGifFile;
  private int simpleImageWidth, simpleImageHeight;

  public static final int STICKER_SIZE_LIMIT = 512;

  @SuppressWarnings ("SuspiciousNameCombination")
  private void setBigPhoto (int maxWidth, int topY, int bottomY) {
    isImageBig = true;

    int maxHeight = parent.getSmallestMaxContentHeight();
    int contentWidth, contentHeight;

    TdApi.Sticker sticker = Td.getSticker(linkPreview.type);
    if (sticker != null && (Math.max(sticker.width, sticker.height) <= STICKER_SIZE_LIMIT || Td.isAnimated(sticker.format))) {
      float max = Screen.dp(TGMessageSticker.MAX_STICKER_SIZE);
      float ratio = Math.min(max / (float) sticker.width, max / (float) sticker.height);

      contentWidth = (int) (sticker.width * ratio);
      contentHeight = (int) (sticker.height * ratio);

      if (Td.isAnimated(sticker.format)) {
        this.simpleGifFile = new GifFile(parent.tdlib(), sticker);
        this.simpleGifFile.setScaleType(ImageFile.FIT_CENTER);
      } else {
        this.simpleImageFile = new ImageFile(parent.tdlib(), sticker.sticker);
        this.simpleImageFile.setScaleType(ImageFile.FIT_CENTER);
        this.simpleImageFile.setWebp();
        this.simpleImageFile.setSize(Math.max(contentWidth, contentHeight));
      }

      this.simplePreview = TD.toImageFile(parent.tdlib(), sticker.thumbnail);
      if (simplePreview != null) {
        this.simplePreview.setScaleType(ImageFile.FIT_CENTER);
        this.simplePreview.setWebp();
      }

      simpleImageWidth = contentWidth;
      simpleImageHeight = contentHeight;

      height += contentHeight;
    } else {
      if (sticker != null) {
        setMediaWrapper(new MediaWrapper(parent.context(), parent.tdlib(), TD.convertToPhoto(sticker), chatId, messageId, parent, false));
      } else if (Td.getVideo(linkPreview.type) != null) {
        setMediaWrapper(new MediaWrapper(parent.context(), parent.tdlib(), Td.getVideo(linkPreview.type), Td.getPhoto(linkPreview.type), chatId, messageId, parent, false));
      } else if (Td.hasPhoto(linkPreview.type)) {
        setMediaWrapper(new MediaWrapper(parent.context(), parent.tdlib(), Td.getPhoto(linkPreview.type), chatId, messageId, parent, false, false, EmbeddedService.parse(linkPreview)));
      } else {
        throw new NullPointerException();
      }
      mediaWrapper.setViewProvider(viewProvider);
      mediaWrapper.setOnClickListener(this);

      contentWidth = mediaWrapper.getContentWidth();
      contentHeight = mediaWrapper.getContentHeight();

      float ratio = Math.min((float) maxWidth / (float) contentWidth, (float) maxHeight / (float) contentHeight);
      int cellWidth = (int) (ratio * contentWidth);
      int cellHeight = (int) (ratio * contentHeight);

      mediaWrapper.buildContent(cellWidth, cellHeight);

      imageWidth = cellWidth;
      imageHeight = cellHeight;
    }

    contentY += topY;

    imageX = paddingLeft;
    imageY = contentY;

    height += imageHeight + bottomY;
  }

  // Video

  private String duration;
  private int durationWidth;

  private void setDuration (String text) {
    this.duration = text;
    durationWidth = (int) U.measureText(text, Paints.whiteMediumPaint(13f, false, true));
  }

  private boolean hasHeader () {
    return !StringUtils.isEmpty(linkPreview.siteName) || !StringUtils.isEmpty(linkPreview.title) || !Td.isEmpty(linkPreview.description) || isTgWallpaper();
  }

  private void buildVideo (final TdApi.LinkPreview linkPreview, int maxWidth) {
    if (Td.getVideo(linkPreview.type) != null || Td.hasPhoto(linkPreview.type)) {
      if (hasHeader()) {
        setBigPhoto(maxWidth, contentPadding, contentPadding + lineAdd);
      } else {
        setBigPhoto(maxWidth, 0, -lineWidth);
      }
    }
  }

  // GIF

  private void setMediaWrapper (MediaWrapper mediaWrapper) {
    MediaWrapper oldMediaWrapper = this.mediaWrapper;
    this.mediaWrapper = mediaWrapper;
    if (oldMediaWrapper != null) {
      oldMediaWrapper.destroy();
    }
  }

  private void buildGif (TdApi.LinkPreview linkPreview, int maxWidth) {
    TdApi.Animation gif = Td.getAnimation(linkPreview.type);

    setMediaWrapper(new MediaWrapper(parent.context(), parent.tdlib(), gif, chatId, messageId, parent, false, false, false, EmbeddedService.parse(linkPreview)));
    mediaWrapper.setOnClickListener(this);
    mediaWrapper.setViewProvider(viewProvider);
    int maxHeight = parent.getSmallestMaxContentHeight();
    int contentWidth = mediaWrapper.getContentWidth();
    int contentHeight = mediaWrapper.getContentHeight();
    float ratio = Math.min((float) maxWidth / (float) contentWidth, (float) maxHeight / (float) contentHeight);
    int cellWidth = (int) (ratio * contentWidth);
    int cellHeight = (int) (ratio * contentHeight);
    mediaWrapper.buildContent(cellWidth, cellHeight);
    imageWidth = cellWidth;
    imageHeight = cellHeight;

    if (hasHeader()) {
      contentY += contentPadding;
    }

    imageX = paddingLeft;
    imageY = contentY;

    int bottomY = hasHeader() ? contentPadding + lineAdd : -lineWidth;
    height += imageHeight + bottomY;
  }

  private void buildPhoto (TdApi.LinkPreview linkPreview, int maxWidth) {
    if (hasHeader()) {
      setBigPhoto(maxWidth, contentPadding, contentPadding + lineAdd);
    } else {
      setBigPhoto(maxWidth, 0, -lineWidth);
    }
  }

  private int rippleButtonY;
  private TGInlineKeyboard rippleButton;

  private void buildRippleButton () {
    int stringRes = 0;
    int icon = 0;
    String text = null;

    if (needInstantView()) {
      stringRes = R.string.InstantView;
      icon = R.drawable.deproko_baseline_instantview_24;
    } else {
      switch (type) {
        case TYPE_TELEGRAM_USER:
          stringRes = R.string.OpenProfile;
          break;
        case TYPE_TELEGRAM_MESSAGE:
        case TYPE_TELEGRAM_ALBUM:
          if (parent.tdlib().isTmeUrl(url))
            stringRes = R.string.OpenMessage;
          break;
        case TYPE_TELEGRAM_CHANNEL:
          stringRes = R.string.OpenChannel;
          break;
        case TYPE_TELEGRAM_MEGAGROUP:
          stringRes = R.string.OpenGroup;
          break;
        case TYPE_TELEGRAM_BOT:
          stringRes = R.string.OpenBot;
          break;
        case TYPE_TELEGRAM_AD: {
          TdApi.SponsoredMessage sponsoredMessage = parent.getSponsoredMessage();
          if (sponsoredMessage != null) {
            text = sponsoredMessage.buttonText;
          }
          break;
        }
        case TYPE_TELEGRAM_CHAT:
          stringRes = R.string.OpenChat;
          break;
        case TYPE_TELEGRAM_BACKGROUND:
          stringRes = R.string.ChatBackgroundView;
          break;
      }
    }
    if (text == null && stringRes != 0) {
      text = Lang.getString(stringRes);
    }

    if (!StringUtils.isEmpty(text)) {
      rippleButtonY = height + Screen.dp(6f);
      height = rippleButtonY + TGInlineKeyboard.getButtonHeight();
      rippleButton = new TGInlineKeyboard(parent, false);
      rippleButton.setCustom(icon, text, availWidth - paddingLeft, type != TYPE_TELEGRAM_AD, this);
    }
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    if (needInstantView()) {
      button.makeActive();
      button.showProgressDelayed();
      String anchor = parent.findUriFragment(linkPreview);
      parent.tdlib().fetchInstantView(url, getInstantViewCallback(view, button, linkPreview, anchor));
    } else {
      open(view, false);
    }
  }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    if (type == TYPE_TELEGRAM_AD) {
      ViewController<?> c = parent.controller().getParentOrSelf();

      if (c == null) {
        return false;
      }

      String url = parent.getSponsoredMessageUrl();
      if (!StringUtils.isEmpty(url)) {
        c.showCopyUrlOptions(url, parent.openParameters(), null);
        return true;
      }
    }

    return false;
  }

  public int getLastLineWidth () {
    return TGMessage.BOTTOM_LINE_EXPAND_HEIGHT;
  }

  public void autodownloadContent (TdApi.ChatType info) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().downloadAutomatically(info);
    }
  }

  public void requestPreview (DoubleImageReceiver receiver) {
    if (simpleImageFile != null || simpleGifFile != null) {
      receiver.requestFile(null, simplePreview);
    } else if (mediaWrapper != null) {
      mediaWrapper.requestPreview(receiver);
    } else if (component != null) {
      component.requestPreview(receiver);
    } else {
      receiver.clear();
    }
  }

  public int getImageContentRadius (boolean isPreview) {
    return component != null ? component.getContentRadius(Config.USE_SCALED_ROUNDINGS ? Screen.dp(Theme.getImageRadius()) : 0) : 0;
  }

  public void requestContent (ImageReceiver receiver, int startX, int startY) {
    if (simpleImageFile != null) {
      receiver.requestFile(simpleImageFile);
    } else if (mediaWrapper != null) {
      mediaWrapper.requestImage(receiver);
    } else if (component != null) {
      component.requestContent(receiver);
    } else {
      receiver.requestFile(null);
    }
  }

  public void requestGif (GifReceiver receiver, int startX, int startY) {
    if (simpleGifFile != null) {
      receiver.requestFile(simpleGifFile);
    } else if (mediaWrapper != null) {
      mediaWrapper.requestGif(receiver);
    } else {
      receiver.requestFile(null);
    }
  }

  public TdApi.File getTargetFile () {
    if (component != null) {
      return component.getFile();
    }
    if (mediaWrapper != null) {
      return mediaWrapper.getTargetFile();
    }
    return null;
  }

  public FileComponent getFileComponent () {
    return component instanceof FileComponent ? (FileComponent) component : null;
  }

  public boolean needInstantView () {
    return type != TYPE_TELEGRAM_AD && TD.hasInstantView(linkPreview) && !needsSpecialProcessing();
  }

  protected boolean needsSpecialProcessing () {
    return linkPreview.type.getConstructor() == TdApi.LinkPreviewTypeAlbum.CONSTRUCTOR;
  }

  protected boolean isTgWallpaper() {
    return type == TYPE_TELEGRAM_BACKGROUND;
  }

  public boolean performLongPress (View view, TGMessageText msg) {
    return (siteName != null && siteName.performLongPress(view)) || (title != null && title.performLongPress(view)) || (description != null && description.performLongPress(view)) || (rippleButton != null && rippleButton.performLongPress(view));
  }

  private final ClickHelper helper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      Receiver receiver = needGif() ? ((MessageView) view).getGifReceiver() : ((MessageView) view).getPreviewReceiver();
      if (receiver != null && receiver.isInsideContent(x, y, 0, 0)) {
        if ((simpleGifFile != null || simpleImageFile != null) && Td.getSticker(linkPreview.type) != null && Td.getSticker(linkPreview.type).setId != 0) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      TdApi.Sticker sticker = Td.getSticker(linkPreview.type);
      if (sticker != null && sticker.setId != 0) {
        parent.tdlib().ui().showStickerSet(parent.controller(), sticker.setId, null);
      }
    }
  });


  public boolean onTouchEvent (MessageView view, MotionEvent e, int startX, int startY, Text.ClickCallback callback) {
    if (rippleButton != null && rippleButton.onTouchEvent(view, e)) {
      return true;
    }
    if (mediaWrapper != null) {
      if (mediaWrapper.onTouchEvent(view, e)) {
        return true;
      }
    }
    if (component != null) {
      if (component.onTouchEvent(view, e)) {
        return true;
      }
    }
    if ((simpleImageFile != null || simpleGifFile != null) && helper.onTouchEvent(view, e)) {
      return true;
    }

    return
      (siteName != null && siteName.onTouchEvent(view, e, callback)) ||
      (title != null && title.onTouchEvent(view, e, callback)) ||
      (description != null && description.onTouchEvent(view, e, callback));
  }

  private void drawHeader (Canvas c, int startX, int startY, int previewWidth, boolean rtl, ComplexReceiver textMediaReceiver) {
    RectF rectF = Paints.getRectF();
    if (rtl) {
      rectF.set(startX + previewWidth - lineWidth, startY, startX + previewWidth, startY + height);
    } else {
      rectF.set(startX, startY, startX + lineWidth, startY + height);
    }
    c.drawRoundRect(rectF, lineWidth / 2, lineWidth / 2, Paints.fillingPaint(parent.getVerticalLineColor()));
    int textY = 0;
    // int y = startY + titleOffset;
    if (siteName != null) {
      textY += Screen.dp(TEXT_PADDING_START);
      siteName.draw(c, rtl ? startX : startX + paddingLeft, rtl ? startX + previewWidth - paddingLeft : startX + previewWidth, 0, startY + textY);
      textY += siteName.getHeight();
    }
    if (title != null) {
      if (textY > 0)
        textY += Screen.dp(TEXT_PADDING);
      title.draw(c, rtl ? startX : startX + paddingLeft, rtl ? startX + previewWidth - paddingLeft : startX + previewWidth, 0, startY + textY);
      textY += title.getHeight();
    }
    if (description != null) {
      if (textY > 0)
        textY += Screen.dp(TEXT_PADDING);
      description.draw(c, rtl ? startX : startX + paddingLeft, rtl ? startX + previewWidth - paddingLeft : startX + previewWidth, 0, startY + textY, null, 1f, textMediaReceiver);
      textY += description.getHeight();
    }
  }

  public void draw (MessageView view, Canvas c, int startX, int startY, Receiver preview, Receiver receiver, float alpha, ComplexReceiver textMediaReceiver) {
    int previewWidth = getWidth();
    boolean rtl = Lang.rtl();
    if (type != TYPE_TELEGRAM_AD) {
      drawHeader(c, startX, startY, previewWidth, rtl, textMediaReceiver);
    }
    if (component != null) {
      component.draw(view, c, rtl ? startX : startX + paddingLeft, startY + componentY, preview, receiver, parent != null ? parent.getContentBackgroundColor() : 0, parent != null ? parent.getContentReplaceColor() : 0, alpha, 0f);
    } else if (mediaWrapper != null) {
      final int imageX = rtl ? (startX + previewWidth - this.imageX - this.imageWidth) : startX + this.imageX;
      final int imageY = startY + this.imageY;
      mediaWrapper.draw(view, c, imageX, imageY, preview, receiver, 1f);
      String text = null;
      float textWidth = 0;
      if (!StringUtils.isEmpty(duration)) {
        text = duration;
        textWidth = durationWidth;
      } else if (!StringUtils.isEmpty(instantText)) {
        text = instantText;
        textWidth = instantTextWidth;
      }
      if (!StringUtils.isEmpty(text)) {
        int dx = imageX + imageWidth;
        int dy = imageY + imageHeight;
        RectF rectF = Paints.getRectF();
        if (text.charAt(0) == '1') {
          rectF.set(dx - durationMargin - durationPadding * 2 - textWidth + durationDecrease, dy - durationMargin - durationHeight, dx - durationMargin, dy - durationMargin);
        } else {
          rectF.set(dx - durationMargin - durationPadding * 2 - textWidth, dy - durationMargin - durationHeight, dx - durationMargin, dy - durationMargin);
        }
        c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(DURATION_COLOR));
        c.drawText(text, dx - durationMargin - durationPadding - textWidth, dy - durationMargin - durationHeight + durationOffset, Paints.whiteMediumPaint(13f, false, false));
      }
    } else if (simpleImageFile != null || simpleGifFile != null) {
      final int imageX = rtl ? startX + previewWidth - this.imageX - this.imageWidth : startX + this.imageX;
      final int imageY = startY + this.imageY;
      DrawAlgorithms.drawReceiver(c, preview, receiver, false, true, imageX, imageY, imageX + simpleImageWidth, imageY + simpleImageHeight);
    }
    if (rippleButton != null) {
      // c.drawRect(startX + paddingLeft, startY + instantButtonY, startX + width, startY + height, Paints.fillingPaint(0xa0ff0000));
      if (type == TYPE_TELEGRAM_AD) {
        rippleButton.draw(view, c, startX, startY + rippleButtonY);
      } else {
        rippleButton.draw(view, c, rtl ? startX : startX + paddingLeft, startY + rippleButtonY);
      }
    }
  }

  public boolean needGif () {
    return (mediaWrapper != null && mediaWrapper.isGif()) || simpleGifFile != null;
  }

  public boolean isBuilt () {
    return availWidth > 0;
  }

  public int getWidth () {
    return rippleButton != null ? availWidth : Math.max(simpleImageWidth != 0 ? simpleImageWidth + paddingLeft : 0, Math.max(component != null ? paddingLeft + component.getWidth() : 0, Math.max(siteName != null ? paddingLeft + siteName.getWidth() : 0, Math.max(mediaWrapper != null ? imageX + imageWidth : 0, Math.max(title != null ? paddingLeft + title.getWidth() : 0, description != null ? paddingLeft + description.getWidth() : 0)))));
  }

  public int getMaxWidth () {
    return this.availWidth;
  }

  public int getHeight () {
    return height;
  }

  private static final int DURATION_COLOR = 0x4c000000;

  private static int lineAdd;
  private static int imageMarginLeft, imageSize, imageOffset;

  private static int paddingLeft;
  private static int lineWidth;
  private static int contentPadding;

  private static int durationMargin, durationPadding, durationHeight, durationOffset, durationDecrease;

  public static void reset () {
    paddingLeft = 0;
  }

  private static void initSizes () {
    lineAdd = Screen.dp(2f);
    imageMarginLeft = Screen.dp(12f);
    imageSize = Screen.dp(60f);
    imageOffset = Screen.dp(23f);
    lineWidth = Screen.dp(3f);
    paddingLeft = Screen.dp(10f);
    contentPadding = Screen.dp(8f);
    durationMargin = Screen.dp(6f);
    durationPadding = Screen.dp(5f);
    durationHeight = Screen.dp(20f);
    durationOffset = Screen.dp(14.5f);
    durationDecrease = Screen.dp(1f);
  }

  // Instant view

  private void runOnUiThread (Runnable runnable) {
    parent.tdlib().ui().post(runnable);
  }

  private Tdlib.ResultHandler<TdApi.WebPageInstantView> getInstantViewCallback (final View view, final TGInlineKeyboard.Button button, final TdApi.LinkPreview instantViewSource, final String anchor) {
    final int currentContextId = button.getContextId();
    return (instantView, error) -> {
      runOnUiThread(() -> {
        if (currentContextId != button.getContextId()) {
          return;
        }
        button.makeInactive();
        if (parent.tdlib().isBadInstantView(instantView)) {
          if (error != null) {
            button.showTooltip(view, TD.toErrorString(error));
          } else {
            button.showTooltip(view, !TD.hasInstantView(instantView.version) ? R.string.InstantViewUnsupported : R.string.InstantViewUnavailable);
          }
          return;
        }

        InstantViewController controller = new InstantViewController(parent.controller().context(), parent.tdlib());
        controller.setArguments(new InstantViewController.Args(instantViewSource, instantView, anchor));
        try {
          controller.show();
        } catch (UnsupportedOperationException e) {
          Log.w("Unsupported Instant View block:%s", e, instantViewSource.url);
          button.showTooltip(view, R.string.InstantViewUnsupported);
          controller.destroy();
        } catch (Throwable t) {
          Log.e("Unable to open Instant View, url:%s", t, instantViewSource.url);
          button.showTooltip(view, R.string.InstantViewError);
          controller.destroy();
        }
      });
    };
  }
}
