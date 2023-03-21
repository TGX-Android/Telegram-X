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
 * File created on 21/02/2016 at 16:37
 */
package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class ReplyComponent implements Client.ResultHandler, Destroyable {
  private static final int FLAG_ALLOW_TOUCH_EVENTS = 1 << 1;
  private static final int FLAG_FORCE_TITLE = 1 << 3;
  private static final int FLAG_LOADING = 1 << 4;
  private static final int FLAG_PINNED_MESSAGE = 1 << 5;
  private static final int FLAG_USE_COLORIZE = 1 << 7;

  private final Tdlib tdlib;
  private final @Nullable TGMessage parent;

  private TdApi.MessageSender sender;
  private String senderName;
  private @ThemeColorId int nameColorId;

  private int maxWidth;
  private int flags;

  private String title;
  private TD.ContentPreview content;
  private ImageFile miniThumbnail;
  private ImageFile preview;
  private Path contour;
  private boolean hasSpoiler;
  private boolean previewCircle;

  private Text trimmedTitle, trimmedContent;

  private ViewProvider viewProvider;

  public ReplyComponent (@NonNull Tdlib tdlib) {
    if (lineWidth == 0) {
      initSizes();
    }
    this.tdlib = tdlib;
    this.parent = null;
  }

  public ReplyComponent (@NonNull TGMessage message) {
    if (lineWidth == 0) {
      initSizes();
    }
    this.tdlib = message.tdlib();
    this.parent = message;
    /*TODO optimize displaying for channels, where you can reply to yourself only?
       if (channelTitle != null) {
      flags |= FLAG_CHANNEL;
      setTitleImpl(channelTitle);
    }*/
  }

  private boolean isChannel () {
    return parent != null ? parent.isChannel() : currentMessage != null && currentMessage.isChannelPost;
  }

  public void setUseColorize (boolean useColorize) {
    flags = BitwiseUtils.setFlag(flags, FLAG_USE_COLORIZE, useColorize);
  }

  public String getAuthor () {
    return sender != null ? tdlib.senderName(sender) : senderName;
  }

  public void layout (int maxWidth) {
    if (this.maxWidth != maxWidth && maxWidth > 0) {
      this.maxWidth = maxWidth;
      buildLayout();
    }
  }

  public void postLayout (final int maxWidth) {
    Background.instance().post(() -> {
      if (ReplyComponent.this.maxWidth != maxWidth) {
        ReplyComponent.this.maxWidth = maxWidth;
        buildLayout();
        invalidate(false);
      }
    });
  }

  public void setCurrentView (@Nullable View view) {
    setViewProvider(new SingleViewProvider(view));
  }

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
  }

  public void invalidate (boolean requestImage) {
    if (!UI.inUiThread()) {
      UI.post(() -> invalidate(requestImage));
      return;
    }
    if (requestImage || (trimmedContent != null && trimmedContent.hasMedia())) {
      viewProvider.invalidateContent(this);
    }
    viewProvider.invalidate();
  }
  
  private boolean isMessageComponent () {
    return parent != null;
  }

  private boolean isOutBubble () {
    return parent != null && parent.isOutgoingBubble();
  }

  private boolean isPsa () {
    return parent != null && parent.isPsa();
  }

  private void buildLayout () {
    if (maxWidth > 0) {
      buildTitle();
      buildContent();
    }
  }

  private int getContentWidth () {
    return !hasPreview() ? maxWidth - paddingLeft - lineWidth - paddingLeft : maxWidth - paddingLeft - paddingLeft - lineWidth - lineWidth - (isMessageComponent() ? mHeight : height);
  }

  public int width () {
    return width(false);
  }

  public int width (boolean isWhite) {
    return (!hasPreview() ? Math.max(getTextWidth(), getTitleWidth()) + paddingLeft + paddingLeft + lineWidth : Math.max(getTextWidth(), getTitleWidth()) + paddingLeft + lineWidth + lineWidth + paddingLeft + (isMessageComponent() ? mHeight : height)) + (isWhite ? Screen.dp(3f) : 0);
  }

  private int getTextWidth () {
    return trimmedContent != null ? trimmedContent.getWidth() : 0;
  }

  private int getTitleWidth () {
    return trimmedTitle != null ? trimmedTitle.getWidth() : 0;
  }

  private static TextStyleProvider getTitleStyleProvider (boolean big) {
    return Paints.robotoStyleProvider(big ? 15 : 13);
  }

  private static TextStyleProvider getTextStyleProvider () {
    return Paints.robotoStyleProvider(13);
  }

  private void buildTitle () {
    int width = getContentWidth();
    String titleText = title != null ? title : Lang.getString(isChannel() ? R.string.LoadingChannel : R.string.LoadingUser);
    trimmedTitle = new Text.Builder(titleText, width, getTitleStyleProvider(isMessageComponent()), getTitleColorSet())
      .singleLine()
      .clipTextArea()
      .allBold()
      .allClickable()
      .viewProvider(viewProvider)
      .build();
  }

  private void buildContent () {
    int width = getContentWidth();

    //noinspection UnsafeOptInUsageError
    Text trimmedContent = new Text.Builder(content != null ? content.buildText(true) : Lang.getString(R.string.LoadingMessage), width, isMessageComponent() ? TGMessage.getTextStyleProvider() : getTextStyleProvider(), getContentColorSet())
      .singleLine()
      .textFlags(Text.FLAG_CUSTOM_LONG_PRESS)
      .ignoreNewLines().ignoreContinuousNewLines()
      .entities(
        content != null && !Td.isEmpty(content.formattedText) ? TextEntity.valueOf(tdlib, content.formattedText.text, content.formattedText.entities, null) : null,
        (text, specificMedia) -> {
          if (isMessageComponent()) {
            parent.invalidateReplyTextMediaReceiver(text, specificMedia);
          } else {
            viewProvider.performWithViews((view) -> {
              if (view instanceof ReplyView) {
                ComplexReceiver receiver = ((ReplyView) view).getTextMediaReceiver();
                if (!text.invalidateMediaContent(receiver, specificMedia)) {
                  requestTextContent(receiver);
                }
              } else {
                throw new UnsupportedOperationException();
              }
            });
          }
        }
      )
      .viewProvider(viewProvider)
      .build();

    if (Looper.myLooper() == Looper.getMainLooper()) {
      this.trimmedContent = trimmedContent;
    } else {
      final Text __trimmedContent = trimmedContent;
      UI.post(() -> {
        ReplyComponent.this.trimmedContent = __trimmedContent;
      });
    }
    if (trimmedContent.hasMedia()) {
      invalidate(true);
    }
  }

  public void requestTextContent (ComplexReceiver textMediaReceiver) {
    if (trimmedContent != null) {
      trimmedContent.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  public void requestPreview (DoubleImageReceiver receiver, ComplexReceiver textMediaReceiver) {
    int imageHeight = isMessageComponent() ? mHeight : height;
    receiver.setRadius(previewCircle ? imageHeight / 2f : 0);
    receiver.requestFile(miniThumbnail, preview);
    requestTextContent(textMediaReceiver);
  }

  private Path path;
  private int pathLeft, pathTop, pathRight, pathBottom, pathRadius;

  private void layoutPath (int left, int top, int right, int bottom) {
    if (path == null) {
      path = new Path();
    }
    int radius = Screen.dp(Theme.getImageRadius());
    if (pathLeft != left || pathTop != top || pathRight != right || pathBottom != bottom || pathRadius != radius) {
      pathLeft = left;
      pathTop = top;
      pathRight = right;
      pathBottom = bottom;
      pathRadius = radius;
      path.reset();
      RectF rectF = Paints.getRectF();
      rectF.set(left, top, right, bottom);
      path.addRoundRect(rectF, radius, radius, Path.Direction.CCW);
    }
  }

  public int lastX, lastY;

  public boolean isInside (float x, float y, boolean needWhite) {
    return x >= lastX && x <= lastX + width(needWhite) && y >= lastY && y <= lastY + mHeight;
  }

  private boolean hasPreview () {
    return miniThumbnail != null || preview != null;
  }

  private int getTextLeft () {
    return hasPreview() ? lineWidth + paddingLeft + lineWidth + mHeight : lineWidth + paddingLeft;
  }

  private TextColorSet getTitleColorSet () {
    if (parent != null) {
      if (parent.separateReplyFromContent())
        return parent::getBubbleMediaReplyTextColor;
      if (nameColorId != 0)
        return () -> Theme.getColor(nameColorId);
      if (isPsa())
        return parent.getChatAuthorPsaColorSet();
      return parent.getChatAuthorColorSet();
    }
    return TextColorSets.Regular.NORMAL;
  }

  private TextColorSet getContentColorSet () {
    if (parent != null) {
      return parent.separateReplyFromContent() ? new TextColorSet() {
        @Override
        public int defaultTextColor () {
          return parent.getBubbleMediaReplyTextColor();
        }

        @Override
        public int clickableTextColor (boolean isPressed) {
          return defaultTextColor(); // U.alphaColor(.7f, parent.getBubbleMediaReplyTextColor());
        }
      } : new TextColorSet() {
        @Override
        public int defaultTextColor () {
          return parent.getTextColor();
        }

        @Override
        public int clickableTextColor (boolean isPressed) {
          return defaultTextColor(); // U.alphaColor(.35f, parent.getTextColor());
        }
      };
    }
    if (BitwiseUtils.hasFlag(flags, FLAG_ALLOW_TOUCH_EVENTS)) {
      return TextColorSets.Regular.NORMAL;
    }
    return new TextColorSet() {
      @Override
      public int defaultTextColor () {
        return Theme.textAccentColor();
      }

      @Override
      public int clickableTextColor (boolean isPressed) {
        return defaultTextColor();
      }
    };
  }

  public void draw (Canvas c, int startX, int startY, int endX, int width, Receiver receiver, ComplexReceiver textMediaReceiver, boolean rtl) {
    boolean isWhite = parent != null && parent.separateReplyFromContent();

    lastX = startX;
    lastY = startY;

    final boolean isOutBubble = isOutBubble();

    RectF rectF = Paints.getRectF();
    if (isWhite) {
      int padding = Screen.dp(8f);
      int paddingHorizontal = Screen.dp(12f);
      rectF.top = startY - padding;
      rectF.bottom = startY + mHeight + padding;
      rectF.left = startX - paddingHorizontal;
      rectF.right = startX + width;
      float mergeRadius = Theme.getBubbleMergeRadius();
      c.drawRoundRect(rectF, mergeRadius, mergeRadius, Paints.fillingPaint(parent.getBubbleMediaReplyBackgroundColor()));
    }

    if (hasPreview()) {
      int imageX = lineWidth + lineWidth;
      if (rtl) {
        imageX = endX - imageX - (isMessageComponent() ? mHeight : height);
      } else {
        imageX += startX;
      }
      if (isMessageComponent()) {
        receiver.setBounds(imageX, startY, imageX + mHeight, startY + mHeight);
      } else {
        receiver.setBounds(imageX, startY, imageX + height, startY + height);
      }
      final int restoreToCount;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        layoutPath(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
        restoreToCount = ViewSupport.clipPath(c, path);
      } else {
        restoreToCount = Integer.MIN_VALUE;
      }
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderContour(c, contour);
      }
      receiver.draw(c);
      if (Config.DEBUG_STICKER_OUTLINES) {
        receiver.drawPlaceholderContour(c, contour);
      }
      if (hasSpoiler) {
        float radius = Theme.getBubbleMergeRadius();
        DrawAlgorithms.drawRoundRect(c, radius, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.fillingPaint(Theme.getColor(R.id.theme_color_spoilerMediaOverlay)));
        DrawAlgorithms.drawParticles(c, radius, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), 1f);
      }
      ViewSupport.restoreClipPath(c, restoreToCount);
    }

    if (isMessageComponent()) {
      int textLeft = getTextLeft();

      if (trimmedTitle != null) {
        trimmedTitle.draw(c, startX + textLeft, startX + textLeft + trimmedTitle.getWidth(), 0, startY + Screen.dp(1f));
      }

      if (trimmedContent != null) {
        trimmedContent.draw(c, startX + textLeft, startX + textLeft + trimmedContent.getWidth(), 0, startY + Screen.dp(22f), null, 1f, textMediaReceiver);
      }

      if (rtl) {
        rectF.set(endX - lineWidth, startY, endX, startY + mHeight);
      } else {
        rectF.set(startX, startY, startX + lineWidth, startY + mHeight);
      }
      c.drawRoundRect(rectF, lineWidth / 2f, lineWidth / 2f, Paints.fillingPaint(isWhite ? parent.getBubbleMediaReplyTextColor() : isOutBubble ? Theme.getColor(R.id.theme_color_bubbleOut_chatVerticalLine) : nameColorId != 0 ? Theme.getColor(nameColorId) : Theme.getColor(R.id.theme_color_messageVerticalLine)));

      return;
    }

    int left = hasPreview() ? startX + lineWidth + paddingLeft + lineWidth + height : startX + lineWidth + paddingLeft;

    if (trimmedTitle != null) {
      trimmedTitle.draw(c, left, left + trimmedTitle.getWidth(), 0, startY + Screen.dp(1f));
    }

    if (trimmedContent != null) {
      trimmedContent.draw(c, left, left + trimmedContent.getWidth(), 0, startY + Screen.dp(19f), null, 1f, textMediaReceiver);
    }

    Paints.getRectF().set(startX, startY, startX + lineWidth, startY + height);
    c.drawRoundRect(Paints.getRectF(), lineWidth / 2f, lineWidth / 2f, Paints.fillingPaint(Theme.getColor(R.id.theme_color_messageVerticalLine)));
  }

  // Data workers

  private CharSequence getTitle (@Nullable CharSequence forcedTitle, @Nullable TdApi.Message message) {
    if (!StringUtils.isEmpty(forcedTitle))
      return forcedTitle;
    if (message != null) {
      return tdlib.messageAuthor(message, false, isMessageComponent());
    }
    return Lang.getString(isChannel() ? R.string.LoadingChannel : R.string.LoadingUser);
  }

  public void set (@Nullable CharSequence forcedTitle, @NonNull TdApi.Message msg) {
    setTitleImpl(getTitle(forcedTitle, msg));
    setMessage(msg, false, false);
  }

  public void set (@Nullable CharSequence forcedTitle, TdApi.Message msg, boolean isPinnedMessage) {
    setTitleImpl(getTitle(forcedTitle, msg));
    this.flags = BitwiseUtils.setFlag(flags, FLAG_FORCE_TITLE, !StringUtils.isEmpty(forcedTitle));
    this.flags = BitwiseUtils.setFlag(flags, FLAG_PINNED_MESSAGE, isPinnedMessage);
    setMessage(msg, false, false);
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return (flags & FLAG_ALLOW_TOUCH_EVENTS) != 0 && trimmedContent != null && trimmedContent.onTouchEvent(view, e);
  }

  public void set (String title, TD.ContentPreview content, TdApi.Minithumbnail miniThumbnail, TdApi.File file) {
    ImageFile image;
    if (file == null || TD.isFileEmpty(file)) {
      image = null;
    } else {
      image = new ImageFile(tdlib, file);
      image.setDecodeSquare(true);
      image.setSize(height);
      image.setNoBlur();
      image.setScaleType(ImageFile.CENTER_CROP);
    }

    ImageFile preview;
    if (miniThumbnail != null) {
      preview = new ImageFileLocal(miniThumbnail);
      preview.setScaleType(ImageFile.CENTER_CROP);
      preview.setDecodeSquare(true);
    } else {
      preview = null;
    }

    setContent(title, content, false, null, preview, image, false, false);
  }

  private @Nullable TdApi.Function<TdApi.Message> retryFunction;

  public void load () {
    if (parent == null)
      return;
    TdApi.Message message = parent.getMessage();
    if (message.chatId == message.replyInChatId && message.replyToMessageId != 0) {
      TdApi.Message foundMessage = parent.manager().getAdapter().tryFindMessage(message.replyInChatId, message.replyToMessageId);
      if (foundMessage != null) {
        setMessage(foundMessage, false, true);
        return;
      }
    }
    flags |= FLAG_LOADING;
    final TdApi.Function<TdApi.Message> function;
    if (message.forwardInfo != null && message.forwardInfo.fromChatId != 0 && message.forwardInfo.fromMessageId != 0 && !parent.isRepliesChat()) {
      function = new TdApi.GetRepliedMessage(message.forwardInfo.fromChatId, message.forwardInfo.fromMessageId);
    } else {
      function = new TdApi.GetRepliedMessage(message.chatId, message.id);
    }
    if (message.replyInChatId != 0 && message.replyToMessageId != 0) {
      retryFunction = new TdApi.GetMessage(message.replyInChatId, message.replyToMessageId);
    } else {
      retryFunction = null;
    }
    tdlib.send(function, this);
  }

  private void parseContent (final TdApi.Message msg, final boolean forceRequestImage) {
    Background.instance().post(() -> setMessage(msg, forceRequestImage, false));
  }

  public void setChannelTitle (final String title) {
    Background.instance().post(() -> {
      setTitleImpl(title);
      buildTitle();
      invalidate(false);
    });
  }

  public void setTitle (final String title) {
    Background.instance().post(() -> {
      setTitleImpl(title);
      buildTitle();
      invalidate(false);
    });
  }

  private void setContent (final String title, final TD.ContentPreview content, boolean hasSpoiler, final Path contour, final ImageFile miniThumbnail, final ImageFile preview, final boolean previewCircle, final boolean forceRequest) {
    Background.instance().post(() -> {
      ReplyComponent.this.currentMessage = null;
      ReplyComponent.this.content = content;
      setTitleImpl(title);
      ReplyComponent.this.contour = contour;
      ReplyComponent.this.miniThumbnail = miniThumbnail;
      ReplyComponent.this.preview = preview;
      ReplyComponent.this.hasSpoiler = hasSpoiler;
      ReplyComponent.this.previewCircle = previewCircle;
      buildLayout();
      invalidate(!isMessageComponent() || preview != null || miniThumbnail != null || forceRequest);
    });
  }

  private TdApi.Message currentMessage;

  public boolean hasValidMessage () {
    return currentMessage != null;
  }

  public boolean replaceMessageContent (long messageId, TdApi.MessageContent content) {
    if (currentMessage != null && currentMessage.id == messageId) {
      currentMessage.content = content;
      parseContent(currentMessage, true);
      return true;
    }
    return false;
  }

  public boolean deleteMessageContent (long messageId) {
    if (currentMessage != null && currentMessage.id == messageId) {
      setContent(Lang.getString(R.string.Error), new TD.ContentPreview(null, R.string.DeletedMessage), false, null, null, null, false, true);
      return true;
    }
    return false;
  }

  // private boolean isPrivate;

  private void setMessage (TdApi.Message msg, boolean forceRequestImage, boolean forceLocal) {
    currentMessage = msg;
    if ((flags & FLAG_USE_COLORIZE) != 0 && !tdlib.isSelfSender(msg)) {
      nameColorId = TD.getNameColorId(new TdlibSender(tdlib, msg.chatId, msg.senderId).getAvatarColorId());
    } else {
      nameColorId = ThemeColorId.NONE;
    }
    boolean isPrivate = msg.selfDestructTime != 0;
    Path contour = null;
    TdApi.Thumbnail thumbnail = null;
    TdApi.PhotoSize photoSize = null;
    TdApi.Minithumbnail miniThumbnail = null;
    boolean previewCircle = false;
    boolean hasSpoiler = false;
    //noinspection SwitchIntDef
    switch (msg.content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.MessagePhoto messagePhoto = (TdApi.MessagePhoto) msg.content;
        TdApi.Photo photo = messagePhoto.photo;
        photoSize = MediaWrapper.pickDisplaySize(tdlib, photo.sizes, msg.chatId);
        TdApi.PhotoSize smallest = Td.findSmallest(photo);
        if (smallest != null && smallest != photoSize) {
          thumbnail = TD.toThumbnail(photoSize);
        }
        miniThumbnail = photo.minithumbnail;
        hasSpoiler = messagePhoto.hasSpoiler;
        break;
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
        TdApi.ChatPhoto photo = ((TdApi.MessageChatChangePhoto) msg.content).photo;
        photoSize = MediaWrapper.pickDisplaySize(tdlib, photo.sizes, msg.chatId);
        TdApi.PhotoSize smallest = Td.findSmallest(photo.sizes);
        if (smallest != null && smallest != photoSize) {
          thumbnail = TD.toThumbnail(photoSize);
        }
        miniThumbnail = photo.minithumbnail;
        previewCircle = true;
        break;
      }
      case TdApi.MessageGame.CONSTRUCTOR: {
        TdApi.Photo photo = ((TdApi.MessageGame) msg.content).game.photo;
        photoSize = MediaWrapper.pickDisplaySize(tdlib, photo.sizes, msg.chatId);
        miniThumbnail = photo.minithumbnail;
        break;
      }
      case TdApi.MessageSticker.CONSTRUCTOR: {
        TdApi.Sticker sticker = ((TdApi.MessageSticker) msg.content).sticker;
        if (sticker == null) {
          return;
        }
        thumbnail = sticker.thumbnail != null ? sticker.thumbnail : TD.toThumbnail(sticker);
        contour = Td.buildOutline(sticker, isMessageComponent() ? mHeight : height);
        break;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.MessageVideo messageVideo = (TdApi.MessageVideo) msg.content;
        TdApi.Video video = messageVideo.video;
        if (video == null) {
          return;
        }
        miniThumbnail = video.minithumbnail;
        thumbnail = video.thumbnail;
        hasSpoiler = messageVideo.hasSpoiler;
        break;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.MessageAnimation messageAnimation = (TdApi.MessageAnimation) msg.content;
        TdApi.Animation animation = messageAnimation.animation;
        miniThumbnail = animation.minithumbnail;
        thumbnail = animation.thumbnail;
        hasSpoiler = messageAnimation.hasSpoiler;
        break;
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        TdApi.VideoNote videoNote = ((TdApi.MessageVideoNote) msg.content).videoNote;
        miniThumbnail = videoNote.minithumbnail;
        thumbnail = videoNote.thumbnail;
        previewCircle = true;
        break;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document doc = ((TdApi.MessageDocument) msg.content).document;
        if (doc == null) {
          return;
        }
        miniThumbnail = doc.minithumbnail;
        thumbnail = doc.thumbnail;
        break;
      }
      case TdApi.MessageText.CONSTRUCTOR: {
        TdApi.WebPage webPage = ((TdApi.MessageText) msg.content).webPage;
        if (webPage != null) {
          if (webPage.photo != null) {
            photoSize = MediaWrapper.pickDisplaySize(tdlib, webPage.photo.sizes, msg.chatId);
            miniThumbnail = webPage.photo.minithumbnail;
          } else if (webPage.document != null && webPage.document.thumbnail != null) {
            thumbnail = webPage.document.thumbnail;
            miniThumbnail = webPage.document.minithumbnail;
          } else if (webPage.sticker != null && webPage.sticker.thumbnail != null) {
            thumbnail = webPage.sticker.thumbnail;
            contour = Td.buildOutline(webPage.sticker, isMessageComponent() ? mHeight : height);
          } else if (webPage.animation != null && webPage.animation.thumbnail != null) {
            thumbnail = webPage.animation.thumbnail;
            miniThumbnail = webPage.animation.minithumbnail;
          }
        }
        break;
      }
    }
    ImageFile preview = TD.toImageFile(tdlib, thumbnail);
    boolean webp = preview != null && thumbnail.format.getConstructor() == TdApi.ThumbnailFormatWebp.CONSTRUCTOR;
    if (preview == null && photoSize != null) {
      preview = new ImageFile(tdlib, photoSize.photo);
    }
    if (preview != null) {
      preview.setScaleType(webp ? ImageFile.FIT_CENTER : ImageFile.CENTER_CROP);
      if (!webp) {
        preview.setDecodeSquare(true);
      }
      preview.setSize(height);
      preview.setNoBlur();
      if (webp) {
        preview.setWebp();
      }
      if (isPrivate || hasSpoiler) {
        preview.setIsPrivate();
        preview.setNeedBlur();
      }
    }
    ImageFile miniPreview;
    if (miniThumbnail != null) {
      miniPreview = new ImageFileLocal(miniThumbnail);
      miniPreview.setDecodeSquare(true);
      miniPreview.setScaleType(webp ? ImageFile.FIT_CENTER : ImageFile.CENTER_CROP);
    } else {
      miniPreview = null;
    }
    TD.ContentPreview contentPreview = TD.getChatListPreview(tdlib, msg.chatId, msg);
    if (msg.forwardInfo != null && (parent != null && parent.getMessage().forwardInfo != null)) {
      switch (msg.forwardInfo.origin.getConstructor()) {
        case TdApi.MessageForwardOriginUser.CONSTRUCTOR:
          sender = new TdApi.MessageSenderUser(((TdApi.MessageForwardOriginUser) msg.forwardInfo.origin).senderUserId);
          senderName = null;
          break;
        case TdApi.MessageForwardOriginChannel.CONSTRUCTOR: {
          TdApi.MessageForwardOriginChannel channel = ((TdApi.MessageForwardOriginChannel) msg.forwardInfo.origin);
          sender = new TdApi.MessageSenderChat(channel.chatId);
          senderName = !StringUtils.isEmpty(channel.authorSignature) ? channel.authorSignature : null;
          break;
        }
        case TdApi.MessageForwardOriginChat.CONSTRUCTOR: {
          TdApi.MessageForwardOriginChat chat = (TdApi.MessageForwardOriginChat) msg.forwardInfo.origin;
          sender = new TdApi.MessageSenderChat(chat.senderChatId);
          senderName = chat.authorSignature;
          break;
        }
        case TdApi.MessageForwardOriginHiddenUser.CONSTRUCTOR: {
          senderName = ((TdApi.MessageForwardOriginHiddenUser) msg.forwardInfo.origin).senderName;
          sender = null;
          break;
        }
        case TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR: {
          TdApi.MessageForwardOriginMessageImport messageImport = (TdApi.MessageForwardOriginMessageImport) msg.forwardInfo.origin;
          senderName = messageImport.senderName;
          sender = null;
          break;
        }
        default: {
          throw new IllegalArgumentException(msg.forwardInfo.origin.toString());
        }
      }
    } else {
      sender = msg.senderId;
      senderName = tdlib.isFromAnonymousGroupAdmin(msg) ? msg.authorSignature : null;
    }
    String title = BitwiseUtils.hasFlag(flags, FLAG_FORCE_TITLE) ? this.title :
      sender == null ? senderName :
      StringUtils.isEmpty(senderName) ? tdlib.senderName(sender, isMessageComponent()) :
      Lang.getString(isChannel() ? R.string.format_channelAndSignature : R.string.format_chatAndSignature, tdlib.senderName(sender, isMessageComponent()), senderName);
    if (Thread.currentThread() == Background.instance().thread() || forceLocal) {
      this.content = contentPreview;
      setTitleImpl(title);
      this.miniThumbnail = miniPreview;
      this.contour = contour;
      this.preview = preview;
      this.hasSpoiler = hasSpoiler;
      this.previewCircle = previewCircle;
      buildLayout();
      invalidate(forceRequestImage || !isMessageComponent() || miniPreview != null || preview != null);
    } else {
      setContent(title, contentPreview, hasSpoiler, contour, miniPreview, preview, previewCircle, false);
    }
  }

  private void setTitleImpl (CharSequence title) {
    this.title = title != null ? title.toString() : null; // TODO charSequence?
  }

  public void setAllowTouchEvents (boolean allow) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_ALLOW_TOUCH_EVENTS, allow);
  }

  @Override
  public void onResult (TdApi.Object object) {
    if (isDestroyed)
      return;
    switch (object.getConstructor()) {
      case TdApi.Message.CONSTRUCTOR: {
        parseContent((TdApi.Message) object, false);
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        if (retryFunction != null) {
          TdApi.Function<?> function = retryFunction;
          retryFunction = null;
          tdlib.client().send(function, this);
          return;
        }
        setContent(Lang.getString(R.string.Error), TD.errorCode(object) == 404 ? new TD.ContentPreview(null, R.string.DeletedMessage) : new TD.ContentPreview(null, 0, TD.toErrorString(object), true), false, null, null, null, false, false);
        currentMessage = null;
        break;
      }
      default: {
        Log.unexpectedTdlibResponse(object, TdApi.GetMessage.class, TdApi.Message.class);
        break;
      }
    }
    flags &= ~FLAG_LOADING;
  }

  private static int lineWidth;
  private static int height;
  private static int paddingLeft;

  private static int mHeight;

  public static int height () {
    return mHeight + Screen.dp(10f);
  }

  public static void reset () {
    initSizes();
  }

  private static void initSizes () {
    lineWidth = Screen.dp(3f);
    paddingLeft = Screen.dp(7f);

    height = Screen.dp(34f);
    mHeight = Screen.dp(41f);
  }

  private volatile boolean isDestroyed;

  @Override
  public void performDestroy () {
    this.isDestroyed = true;
  }
}
