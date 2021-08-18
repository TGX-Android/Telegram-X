/**
 * File created on 04/08/15 at 17:36
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.preview.PreviewLayout;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.util.ArrayList;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class TGWebPage implements FileProgressComponent.SimpleListener, MediaWrapper.OnClickListener, TGInlineKeyboard.ClickListener, Client.ResultHandler {
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
        return true;
    }
    return false;
  }

  private static final int FLAG_DESTROYED = 1;
  private static final int FLAG_PROCESSED = 1 << 1;

  private int flags;

  private boolean isImageBig;
  // private boolean fakeName;
  // private boolean nameRtl;

  /*
  * article, photo, audio, video, document, profile, app
  * */

  private int type;
  private int availWidth, height;

  // private String trimmedName;
  // private int trimmedNameWidth;

  private long chatId, messageId;
  private final TGMessageText parent;
  private final TdApi.WebPage webPage;
  private final String url;

  private FileComponent component;
  private int componentY;

  private float instantTextWidth;
  private @Nullable String instantText;
  private @Nullable ArrayList<MediaItem> instantItems;
  private int instantPosition;

  public TGWebPage (@NonNull TGMessageText parent, TdApi.WebPage webPage, String url) {
    if (paddingLeft == 0) {
      initSizes();
    }
    /*if (whitePaint == null) {
      initPaints();
    }*/
    this.viewProvider = parent.currentViews;
    this.parent = parent;
    this.webPage = webPage;
    this.url = url;
    this.chatId = parent.getChatId();
    this.messageId = parent.getId();
  }

  public int getType () {
    return this.type;
  }

  @SuppressWarnings ("SuspiciousNameCombination")
  public void buildLayout (int width) {
    this.availWidth = width;

    int maxWidth = width - paddingLeft;
    height = 0;

    if (this.type == 0) {
      final String type = webPage.type;
      switch (type) {
        case "video": {
          this.type = TYPE_VIDEO;
          break;
        }
        case "article": {
          if (webPage.video != null) {
            this.type = TYPE_VIDEO;
          } else {
            this.type = TYPE_ARTICLE;
          }
          break;
        }
        case "gif": {
          if (webPage.animation != null) {
            this.type = TYPE_GIF;
          } else {
            Log.w("WebPage, received null %s", webPage.type);
          }
          break;
        }
        case "photo": {
          if (webPage.photo != null) {
            this.type = TYPE_PHOTO;
          } else {
            Log.w("WebPage, received null %s", webPage.type);
          }
          break;
        }
        case "app": {
          this.type = TYPE_APP;
          break;
        }
        case "sticker":
        case "voice":
        case "audio":
        case "document": {
          if (webPage.video != null) {
            this.type = TYPE_VIDEO;
          } else if (webPage.sticker != null) {
            this.type = Math.max(webPage.sticker.width, webPage.sticker.height) <= STICKER_SIZE_LIMIT ? TYPE_STICKER : TYPE_PHOTO;
          } else if (webPage.animation != null) {
            this.type = TYPE_GIF;
          } else if (webPage.voiceNote != null || webPage.audio != null || webPage.document != null) {
            this.type = TYPE_DOCUMENT;
          } else {
            Log.w("WebPage, received null for %s", webPage.type);
          }
          break;
        }
        case "telegram_channel": {
          this.type = TYPE_TELEGRAM_CHANNEL;
          break;
        }
        case "telegram_megagroup": {
          this.type = TYPE_TELEGRAM_MEGAGROUP;
          break;
        }
        case "telegram_chat": {
          this.type = TYPE_TELEGRAM_CHAT;
          break;
        }
        case "telegram_bot": {
          this.type = TYPE_TELEGRAM_BOT;
          break;
        }
        case "telegram_user": {
          this.type = TYPE_TELEGRAM_USER;
          break;
        }
        case "telegram_message": {
          this.type = TYPE_TELEGRAM_MESSAGE;
          break;
        }
        case "telegram_album": {
          this.type = TYPE_TELEGRAM_ALBUM;
          break;
        }
        case "telegram_theme": {
          this.type = TYPE_TELEGRAM_THEME;
          break;
        }
        default: {
          Log.w("Unsupported WebPage content, type: %s", webPage.type);
          break;
        }
      }
    }

    if (!isTelegramType(type) && parent.tdlib().ui().getTelegramLinkType(url) == TdlibUi.TME_URL_MESSAGE) {
      this.type = TYPE_TELEGRAM_MESSAGE;
    }

    if (hasHeader()) {
      buildHeader(webPage, maxWidth);
    }

    if (needInstantPreview(webPage)) {
      if (webPage.animation != null) {
        buildGif(webPage, maxWidth);
      } else if (webPage.video != null) {
        buildVideo(webPage, maxWidth);
      } else {
        buildPhoto(webPage, maxWidth);
      }
    } else {
      switch (this.type) {
        case TYPE_VIDEO: {
          buildVideo(webPage, maxWidth);
          break;
        }
        case TYPE_GIF: {
          buildGif(webPage, maxWidth);
          break;
        }
        case TYPE_PHOTO: {
          buildPhoto(webPage, maxWidth);
          break;
        }
        default: {
          if (webPage.audio != null) {
            this.component = new FileComponent(parent, webPage.audio, null, null);
          } else if (webPage.voiceNote != null) {
            this.component = new FileComponent(parent, webPage.voiceNote, null, null);
          } else if (webPage.document != null) {
            this.component = new FileComponent(parent, webPage.document);
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
          } else if (isSmallPhotoType(this.type)) {
            if (mediaWrapper != null && height < imageY + imageHeight) {
              height = imageY + imageHeight;
            }
            height += lineAdd;
          } else if (webPage.video != null) {
            buildVideo(webPage, maxWidth);
          } else if (webPage.animation != null) {
            buildGif(webPage, maxWidth);
          } else if (webPage.photo != null || webPage.sticker != null) {
            buildPhoto(webPage, maxWidth);
          }
          break;
        }
      }
    }
    buildRippleButton();
    if ((flags & FLAG_PROCESSED) == 0 && needsSpecialProcessing()) {
      flags |= FLAG_PROCESSED;
      parent.tdlib().client().send(new TdApi.GetWebPageInstantView(url, false), this);
    }
    setViewProvider(viewProvider);
    height += lineWidth;
  }

  public int getInstantPosition () {
    return instantPosition;
  }

  public ArrayList<MediaItem> getInstantItems () {
    return instantItems;
  }

  private void setInstantItems (@NonNull ArrayList<MediaItem> items, String text, float textWidth, int position) {
    if (isDestroyed()) {
      return;
    }
    this.instantItems = items;
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

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.WebPageInstantView.CONSTRUCTOR: {
        TdApi.WebPageInstantView instantView = (TdApi.WebPageInstantView) object;
        TdApi.PageBlock[] mediaBlocks = null;
        main: for (TdApi.PageBlock pageBlock : instantView.pageBlocks) {
          switch (pageBlock.getConstructor()) {
            case TdApi.PageBlockSlideshow.CONSTRUCTOR: {
              mediaBlocks = ((TdApi.PageBlockSlideshow) pageBlock).pageBlocks;
              break main;
            }
            case TdApi.PageBlockCollage.CONSTRUCTOR: {
              mediaBlocks = ((TdApi.PageBlockCollage) pageBlock).pageBlocks;
              break main;
            }
          }
        }
        if (mediaBlocks == null || mediaBlocks.length <= 1) {
          return;
        }
        MediaWrapper currentWrapper = mediaWrapper;
        TdApi.File currentFile = currentWrapper != null ? currentWrapper.getTargetFile() : null;
        int currentFileId = currentFile != null ? currentFile.id : 0;
        final ArrayList<MediaItem> mediaItems = new ArrayList<>(mediaBlocks.length);
        int position = -1;
        int i = 0;
        for (TdApi.PageBlock mediaBlock : mediaBlocks) {
          MediaItem item = null;
          // TODO entities
          switch (mediaBlock.getConstructor()) {
            case TdApi.PageBlockAnimation.CONSTRUCTOR: {
              TdApi.PageBlockAnimation animation = (TdApi.PageBlockAnimation) mediaBlock;
              String text = TD.getText(animation.caption.text);
              item = MediaItem.valueOf(parent.context(), parent.tdlib(), animation.animation, new TdApi.FormattedText(text, Text.findEntities(text, Text.ENTITY_FLAGS_EXTERNAL)));
              break;
            }
            case TdApi.PageBlockVideo.CONSTRUCTOR: {
              TdApi.PageBlockVideo video = (TdApi.PageBlockVideo) mediaBlock;
              String text = TD.getText(video.caption.text);
              item = MediaItem.valueOf(parent.context(), parent.tdlib(), video.video, new TdApi.FormattedText(text, Text.findEntities(text, Text.ENTITY_FLAGS_EXTERNAL)));
              break;
            }
            case TdApi.PageBlockPhoto.CONSTRUCTOR: {
              TdApi.PageBlockPhoto photo = (TdApi.PageBlockPhoto) mediaBlock;
              String text = TD.getText(photo.caption.text);
              item = MediaItem.valueOf(parent.context(), parent.tdlib(), photo.photo, new TdApi.FormattedText(text, Text.findEntities(text, Text.ENTITY_FLAGS_EXTERNAL)));
              break;
            }
          }
          if (item == null) {
            mediaItems.clear();
            break;
          }
          if (position == -1 && item.getFileId() == currentFileId) {
            item.setSourceMessage(parent.getMessage());
            position = i;
          }
          mediaItems.add(item);
          i++;
        }
        if (mediaItems.size() <= 1) {
          return;
        }
        if (position == -1) {
          position = 0;
        }

        if (!isDestroyed()) {
          final int positionFinal = position;
          final String text = Lang.getString(R.string.XofY, position + 1, mediaItems.size());
          final float textWidth = U.measureText(text, Paints.whiteMediumPaint(13f, false, true));
          parent.tdlib().ui().post(() -> setInstantItems(mediaItems, text, textWidth, positionFinal));
        }
        break;
      }
    }
  }

  public boolean isDestroyed () {
    return (flags & FLAG_DESTROYED) != 0;
  }

  public void destroy () {
    flags |= FLAG_DESTROYED;
  }

  public MediaWrapper getMediaWrapper () {
    return mediaWrapper;
  }

  private static boolean needInstantPreview (TdApi.WebPage webPage) {
    if (webPage != null && TD.hasInstantView(webPage)) {
      if (webPage.photo != null && !TD.isPhotoEmpty(webPage.photo)) {
        TdApi.PhotoSize size = TD.findBiggest(webPage.photo);
        return size != null && Math.max(size.width, size.height) >= 400;
      }
      return webPage.video != null && Math.max(webPage.video.width, webPage.video.height) >= 400;
    }
    return false;
  }

  private int contentY;
  private int imageX, imageY, imageWidth, imageHeight;

  private Text siteName, title, description;

  private void setSmallPhoto (TdApi.Photo photo) {
    TdApi.PhotoSize small = TD.findSmallest(photo);

    if (small == null) {
      return;
    }

    isImageBig = false;

    mediaWrapper = new MediaWrapper(parent.context(), parent.tdlib(), photo, chatId, messageId, parent, false);
    mediaWrapper.setViewProvider(viewProvider);
    mediaWrapper.setHideLoader(true);
    mediaWrapper.setOnClickListener(this);
    mediaWrapper.buildContent(imageSize, imageSize);
    mediaWrapper.setViewProvider(viewProvider);
  }

  @Override
  public boolean onClick (View view, MediaWrapper wrapper) {
    if ((isSmallPhotoType(type) && !isImageBig && webPage.photo != null) || instantItems != null) {
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
        if (!PreviewLayout.show(parent.controller(), webPage)) {
          if (mediaWrapper != null && webPage.photo != null) {
            MediaViewController.openFromMessage(parent);
          } else {
            parent.tdlib().ui().openUrl(parent.controller(), url, rippleButton.firstButton().openParameters(view));
          }
        }
        break;
      }
    }
    return true;
  }

  public TdApi.WebPage getWebPage () {
    return webPage;
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

  private static float TEXT_PADDING = 4f, TEXT_PADDING_START = 2f;

  private void buildHeader (TdApi.WebPage webPage, int maxWidth) {
    final int textMaxWidth;
    if (!needInstantPreview(webPage) && isSmallPhotoType(type) && !TD.isPhotoEmpty(webPage.photo)) {
      textMaxWidth = maxWidth - imageMarginLeft - imageSize;
      imageX = availWidth - imageSize;
      imageY = imageOffset;
      imageWidth = imageHeight = imageSize;
      if (mediaWrapper == null) {
        setSmallPhoto(webPage.photo);
      }
    } else {
      textMaxWidth = maxWidth;
    }

    boolean needLineWidthProvider = mediaWrapper != null && !isImageBig;
    int textHeight = 0;

    if (!StringUtils.isEmpty(webPage.siteName)) {
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

      siteName = new Text.Builder(webPage.siteName, maxWidth, TGMessage.getTextStyleProvider(), parent.getChatAuthorColorSet())
        .maxLineCount(2)
        .lineWidthProvider(provider)
        .textFlags(Text.FLAG_ALL_BOLD | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0))
        .clipTextArea()
        .entities(new TextEntity[]{TextEntity.valueOf(parent.tdlib, webPage.siteName, new TdApi.TextEntity(0, webPage.siteName.length(), new TdApi.TextEntityTypeTextUrl(url)), parent.openParameters())})
        .build();
      textHeight += siteName.getHeight();
    } else {
      siteName = null;
    }

    if (!StringUtils.isEmpty(webPage.title)) {
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

      title = new Text.Builder(webPage.title, maxWidth, TGMessage.getTextStyleProvider(), parent.getTextColorSet())
        .maxLineCount(TD.hasInstantView(webPage) ? -1 : MAX_TITLE_LINES).lineWidthProvider(provider)
        .textFlags(Text.FLAG_ALL_BOLD | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0))
        .clipTextArea()
        .build();
      textHeight += title.getHeight();
    } else {
      title = null;
    }
    if (!Td.isEmpty(webPage.description)) {
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

      description = new Text.Builder(webPage.description.text, maxWidth, TGMessage.getTextStyleProvider(), parent.getTextColorSet())
        .maxLineCount(MAX_DESCRIPTION_LINES)
        .lineWidthProvider(provider)
        .textFlags(Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0)
        .entities(TextEntity.valueOf(parent.tdlib, webPage.description, parent.openParameters()))
        .build();
      textHeight += description.getHeight();
    } else {
      description = null;
    }

    height += textHeight;

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
      component.getFileProgress().updateMessageId(oldMessageId, newMessageId, success);
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

    if (webPage.sticker != null && (Math.max(webPage.sticker.width, webPage.sticker.height) <= STICKER_SIZE_LIMIT || webPage.sticker.isAnimated)) {
      float max = Screen.dp(TGMessageSticker.MAX_STICKER_SIZE);
      float ratio = Math.min(max / (float) webPage.sticker.width, max / (float) webPage.sticker.height);

      contentWidth = (int) (webPage.sticker.width * ratio);
      contentHeight = (int) (webPage.sticker.height * ratio);

      if (webPage.sticker.isAnimated) {
        this.simpleGifFile = new GifFile(parent.tdlib(), webPage.sticker);
        this.simpleGifFile.setScaleType(ImageFile.FIT_CENTER);
      } else {
        this.simpleImageFile = new ImageFile(parent.tdlib(), webPage.sticker.sticker);
        this.simpleImageFile.setScaleType(ImageFile.FIT_CENTER);
        this.simpleImageFile.setWebp();
        this.simpleImageFile.setSize(Math.max(contentWidth, contentHeight));
      }

      this.simplePreview = TD.toImageFile(parent.tdlib(), webPage.sticker.thumbnail);
      if (simplePreview != null) {
        this.simplePreview.setScaleType(ImageFile.FIT_CENTER);
        this.simplePreview.setWebp();
      }

      simpleImageWidth = contentWidth;
      simpleImageHeight = contentHeight;

      height += contentHeight;
    } else {
      if (webPage.sticker != null) {
        mediaWrapper = new MediaWrapper(parent.context(), parent.tdlib(), TD.convertToPhoto(webPage.sticker), chatId, messageId, parent, false);
        setDuration(Strings.buildSize(webPage.sticker.sticker.size));
      } else if (webPage.video != null) {
        mediaWrapper = new MediaWrapper(parent.context(), parent.tdlib(), webPage.video, chatId, messageId, parent, false);
      } else if (webPage.photo != null) {
        mediaWrapper = new MediaWrapper(parent.context(), parent.tdlib(), webPage.photo, chatId, messageId, parent, false, false, EmbeddedService.parse(webPage));
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
    return !StringUtils.isEmpty(webPage.siteName) || !StringUtils.isEmpty(webPage.title) || !Td.isEmpty(webPage.description);
  }

  private void buildVideo (final TdApi.WebPage webPage, int maxWidth) {
    if (webPage.video != null || webPage.photo != null) {
      if (webPage.duration != 0) {
        setDuration(Strings.buildDuration(webPage.duration));
      }
      if (hasHeader()) {
        setBigPhoto(maxWidth, contentPadding, contentPadding + lineAdd);
      } else {
        setBigPhoto(maxWidth, 0, -lineWidth);
      }
    }
  }

  // GIF

  private void buildGif (TdApi.WebPage webPage, int maxWidth) {
    TdApi.Animation gif = webPage.animation;

    mediaWrapper = new MediaWrapper(parent.context(), parent.tdlib(), gif, chatId, messageId, parent, false, false, false, EmbeddedService.parse(webPage));
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

  private void buildPhoto (TdApi.WebPage webPage, int maxWidth) {
    if (hasHeader()) {
      setBigPhoto(maxWidth, contentPadding, contentPadding + lineAdd);
    } else {
      setBigPhoto(maxWidth, 0, -lineWidth);
    }
  }

  private int rippleButtonY;
  private TGInlineKeyboard rippleButton;

  private void buildRippleButton () {
    int message = 0;
    int icon = 0;

    if (needInstantView()) {
      message = R.string.InstantView;
      icon = R.drawable.deproko_baseline_instantview_24;
    } else {
      switch (type) {
        case TYPE_TELEGRAM_USER:
          message = R.string.OpenProfile;
          break;
        case TYPE_TELEGRAM_MESSAGE:
        case TYPE_TELEGRAM_ALBUM:
          if (parent.tdlib().isTmeUrl(url))
            message = R.string.OpenMessage;
          break;
        case TYPE_TELEGRAM_CHANNEL:
          message = R.string.OpenChannel;
          break;
        case TYPE_TELEGRAM_MEGAGROUP:
          message = R.string.OpenGroup;
          break;
        case TYPE_TELEGRAM_BOT:
          message = R.string.OpenBot;
          break;
        case TYPE_TELEGRAM_CHAT:
          message = R.string.OpenChat;
          break;
      }
    }

    if (message != 0) {
      rippleButtonY = height + Screen.dp(6f);
      height = rippleButtonY + TGInlineKeyboard.getButtonHeight();
      rippleButton = new TGInlineKeyboard(parent, false);
      rippleButton.setCustom(icon, Lang.getString(message), availWidth - paddingLeft, this);
    }
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    if (needInstantView()) {
      button.makeActive();
      button.showProgressDelayed();
      String anchor = parent.findUriFragment(webPage);
      parent.tdlib().client().send(new TdApi.GetWebPageInstantView(url, false), getInstantViewCallback(view, button, webPage, anchor));
    } else {
      open(view, false);
    }
  }

  public int getLastLineWidth () {
    return TGMessage.BOTTOM_LINE_EXPAND_HEIGHT;
  }

  public void autodownloadContent (TdApi.ChatType info) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().downloadAutomatically(info);
    }
  }

  public void requestPreview (DoubleImageReceiver receiver, int startX, int startY) {
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
      return component.getFileProgress().getFile();
    }
    if (mediaWrapper != null) {
      return mediaWrapper.getTargetFile();
    }
    return null;
  }

  public FileComponent getFileComponent () {
    return component;
  }

  public boolean needInstantView () {
    return TD.hasInstantView(webPage) && !needsSpecialProcessing();
  }

  protected boolean needsSpecialProcessing () {
    return type == TYPE_TELEGRAM_ALBUM || TD.shouldInlineIv(webPage.displayUrl); //  && !Strings.isEmpty(webPage.author)
  }

  public boolean performLongPress (View view, TGMessageText msg) {
    return (siteName != null && siteName.performLongPress(view)) || (title != null && title.performLongPress(view)) || (description != null && description.performLongPress(view)) || (rippleButton != null && rippleButton.performLongPress(view));
  }

  private final ClickHelper helper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      Receiver receiver = needGif() ? ((MessageView) view).getGifReceiver() : ((MessageView) view).getPreviewReceiver();
      if (receiver != null && receiver.isInsideContent(x, y, 0, 0)) {
        if ((simpleGifFile != null || simpleImageFile != null) && webPage.sticker != null && webPage.sticker.setId != 0) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      if (webPage.sticker != null && webPage.sticker.setId != 0) {
        parent.tdlib().ui().showStickerSet(parent.controller(), webPage.sticker.setId);
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

  private void drawHeader (Canvas c, int startX, int startY, int previewWidth, boolean rtl) {
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
      description.draw(c, rtl ? startX : startX + paddingLeft, rtl ? startX + previewWidth - paddingLeft : startX + previewWidth, 0, startY + textY);
      textY += description.getHeight();
    }
  }

  public void draw (MessageView view, Canvas c, int startX, int startY, Receiver preview, Receiver receiver, float alpha) {
    int previewWidth = getWidth();
    boolean rtl = Lang.rtl();
    drawHeader(c, startX, startY, previewWidth, rtl);
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
      if (receiver.needPlaceholder()) {
        preview.setBounds(imageX, imageY, imageX + simpleImageWidth, imageY + simpleImageHeight);
        preview.draw(c);
      }
      receiver.setBounds(imageX, imageY, imageX + simpleImageWidth, imageY + simpleImageHeight);
      receiver.draw(c);
    }
    if (rippleButton != null) {
      // c.drawRect(startX + paddingLeft, startY + instantButtonY, startX + width, startY + height, Paints.fillingPaint(0xa0ff0000));
      rippleButton.draw(view, c, rtl ? startX : startX + paddingLeft, startY + rippleButtonY);
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

  private Client.ResultHandler getInstantViewCallback (final View view, final TGInlineKeyboard.Button button, final TdApi.WebPage instantViewSource, final String anchor) {
    final int currentContextId = button.getContextId();
    final boolean[] signal = new boolean[1];
    return new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.WebPageInstantView.CONSTRUCTOR: {
            final TdApi.WebPageInstantView instantView = (TdApi.WebPageInstantView) object;

            if (!TD.hasInstantView(instantView.version)) {
              parent.tdlib().ui().post(() -> {
                if (currentContextId == button.getContextId()) {
                  button.makeInactive();
                  button.showTooltip(view, R.string.InstantViewUnsupported);
                }
              });
              break;
            }

            if (instantView.pageBlocks == null || instantView.pageBlocks.length == 0) {
              boolean retry = !signal[0] && !instantView.isFull;
              if (retry) {
                signal[0] = true;
                parent.tdlib().client().send(new TdApi.GetWebPageInstantView(instantViewSource.url, false), this);
              } else {
                parent.tdlib().ui().post(() -> {
                  if (currentContextId == button.getContextId()) {
                    button.makeInactive();
                    button.showTooltip(view, "TDLib: instantView.pageBlocks returned null " + (signal[0] ? "twice isFull == " + instantView.isFull : "with isFull == " + instantView.isFull));
                  }
                });
              }

              break;
            }

            parent.tdlib().ui().post(() -> {
              if (currentContextId != button.getContextId()) {
                return;
              }

              button.makeInactive();

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

            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            parent.tdlib().ui().post(() -> {
              if (currentContextId != button.getContextId()) {
                return;
              }
              button.makeInactive();
              button.showTooltip(view, TD.toErrorString(object));
            });
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.GetWebPageInstantView.class, TdApi.WebPageInstantView.class);
            parent.tdlib().ui().post(button::makeInactive);
            break;
          }
        }
      }
    };
  }
}
