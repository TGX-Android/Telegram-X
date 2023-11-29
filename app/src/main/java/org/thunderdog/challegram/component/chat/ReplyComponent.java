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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGWebPage;
import org.thunderdog.challegram.helper.InlineSearchContext;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
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
  private static final int FLAG_USE_COLORIZE = 1 << 5;

  private final Tdlib tdlib;
  private final @NonNull TGMessage parent;

  private TdApi.MessageSender sender;
  private String senderName;
  private TdlibAccentColor accentColor;

  private int maxWidth;
  private int flags;

  private String title;
  private ContentPreview content;
  private MediaPreview mediaPreview;

  private Text trimmedTitle, trimmedContent;

  private ViewProvider viewProvider;

  public ReplyComponent (@NonNull TGMessage message) {
    if (lineWidth == 0) {
      initSizes();
    }
    this.tdlib = message.tdlib();
    this.parent = message;
    this.translatedText = message.getTranslatedText();
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

  public TdApi.MessageSender getSender () {
    return sender;
  }

  public void layout (int maxWidth) {
    if (this.maxWidth != maxWidth && maxWidth > 0) {
      this.maxWidth = maxWidth;
      buildLayout();
    }
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

  private static final float CORNER_PADDING = 1f;

  private int getContentWidth (boolean isTitle) {
    int maxWidth = this.maxWidth - paddingLeft - lineWidth - paddingLeft;
    if (hasPreview()) {
      maxWidth -= lineWidth + (isMessageComponent() ? mHeight : height);
    }
    if (isTitle && cornerDrawable != null) {
      maxWidth -= cornerDrawable.getMinimumWidth() + Screen.dp(CORNER_PADDING);
    }
    return maxWidth;
  }

  public int width () {
    return width(false);
  }

  public int width (boolean isWhite) {
    int contentWidth = Math.max(getTextWidth(), getTitleWidth() + (cornerDrawable != null ? cornerDrawable.getMinimumWidth() + Screen.dp(CORNER_PADDING) : 0)) + paddingLeft + paddingLeft + lineWidth;
    if (hasPreview()) {
      contentWidth += lineWidth + (isMessageComponent() ? mHeight : height);
    }
    if (isWhite) {
      contentWidth += Screen.dp(3f);
    }
    return contentWidth;
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
    int width = getContentWidth(true);
    String titleText = computeTitleText(Lang.getString(isChannel() ? R.string.LoadingChannel : R.string.LoadingUser));
    trimmedTitle = new Text.Builder(titleText, width, getTitleStyleProvider(isMessageComponent()), getTitleColorSet())
      .singleLine()
      .clipTextArea()
      .allBold(sender != null || StringUtils.isEmpty(senderName))
      .allClickable()
      .viewProvider(viewProvider)
      .build();
  }

  private void buildContent () {
    int width = getContentWidth(false);

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
            /*viewProvider.performWithViews((view) -> {
              if (view instanceof ReplyView) {
                ComplexReceiver receiver = ((ReplyView) view).getTextMediaReceiver();
                if (!text.invalidateMediaContent(receiver, specificMedia)) {
                  requestTextContent(receiver);
                }
              } else {
                throw new UnsupportedOperationException();
              }
            });*/
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
    if (mediaPreview != null) {
      receiver.setRadius(mediaPreview.previewCircle ? imageHeight / 2f : 0);
      receiver.requestFile(mediaPreview.miniThumbnail, mediaPreview.preview);
    }
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
    return mediaPreview != null && (mediaPreview.miniThumbnail != null || mediaPreview.preview != null);
  }

  private int getTextLeft () {
    return hasPreview() ? lineWidth + paddingLeft + lineWidth + mHeight : lineWidth + paddingLeft;
  }

  private TextColorSet getTitleColorSet () {
    if (parent != null) {
      if (parent.separateReplyFromContent())
        return parent::getBubbleMediaReplyTextColor;
      if (isPsa())
        return parent.getChatAuthorPsaColorSet();
      if (useAccentColor())
        return accentColorSet();
      return parent.getChatAuthorColorSet();
    }
    if (useAccentColor())
      return accentColorSet();
    return TextColorSets.Regular.NORMAL;
  }

  private TextColorSet accentColorSet () {
    if (accentColor == null) {
      throw new IllegalStateException();
    }
    return new TextColorSet() {
      @Override
      public int defaultTextColor () {
        return accentColor.getNameColor();
      }

      @Override
      public long mediaTextComplexColor () {
        return accentColor.getNameComplexColor();
      }
    };
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

  public int getLastX () {
    return lastX;
  }

  public int getLastY () {
    return lastY;
  }

  public void draw (Canvas c, int startX, int startY, int endX, int width, Receiver receiver, ComplexReceiver textMediaReceiver, boolean rtl) {
    boolean isWhite = parent != null && parent.separateReplyFromContent();

    lastX = startX;
    lastY = startY;

    final boolean isOutBubble = isOutBubble();
    final @ColorInt int lineColor;
    if (isMessageComponent()) {
      lineColor = isWhite ? parent.getBubbleMediaReplyTextColor() : isOutBubble ? Theme.getColor(ColorId.bubbleOut_chatVerticalLine) : useAccentColor() ? accentColor.getVerticalLineColor() : Theme.getColor(ColorId.messageVerticalLine);
    } else {
      lineColor = useAccentColor() ? accentColor.getVerticalLineColor() : Theme.getColor(ColorId.messageVerticalLine);
    }

    float cornerPositionX, cornerPositionY;
    cornerPositionX = startX + width;
    cornerPositionY = startY;

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

    if (cornerDrawable != null) {
      // TODO: optimize
      Drawables.draw(c, cornerDrawable, cornerPositionX - cornerDrawable.getMinimumWidth(), cornerPositionY, Paints.getPorterDuffPaint(lineColor));
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
      if (receiver.needPlaceholder() && mediaPreview != null) {
        receiver.drawPlaceholderContour(c, mediaPreview.contour);
      }
      receiver.draw(c);
      if (Config.DEBUG_STICKER_OUTLINES && mediaPreview != null) {
        receiver.drawPlaceholderContour(c, mediaPreview.contour);
      }
      if (mediaPreview != null && mediaPreview.hasSpoiler) {
        float radius = Theme.getBubbleMergeRadius();
        DrawAlgorithms.drawRoundRect(c, radius, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.fillingPaint(Theme.getColor(ColorId.spoilerMediaOverlay)));
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
      c.drawRoundRect(rectF, lineWidth / 2f, lineWidth / 2f, Paints.fillingPaint(lineColor));

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
    c.drawRoundRect(Paints.getRectF(), lineWidth / 2f, lineWidth / 2f, Paints.fillingPaint(lineColor));
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
    this.accentColor = tdlib.senderAccentColor(msg.senderId);
    setTitleImpl(getTitle(forcedTitle, msg));
    setMessage(msg, false, false);
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return (flags & FLAG_ALLOW_TOUCH_EVENTS) != 0 && trimmedContent != null && trimmedContent.onTouchEvent(view, e);
  }

  public void set (String title, ContentPreview content, TdApi.Minithumbnail miniThumbnail, TdApi.File file) {
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

    setContent(title, content, new MediaPreview(preview, image, null, false, false), false);
  }

  private @Nullable TdApi.Function<?> retryFunction;
  private boolean ignoreFailures;
  private @Nullable TdApi.FormattedText quote;
  private Drawable cornerDrawable;

  public void load () {
    if (parent == null)
      return;
    TdApi.Message message = parent.getMessage();
    if (message.replyTo == null) {
      return;
    }
    final TdApi.Function<?> function, retryFunction;
    switch (message.replyTo.getConstructor()) {
      case TdApi.MessageReplyToMessage.CONSTRUCTOR: {
        TdApi.MessageReplyToMessage replyToMessage = (TdApi.MessageReplyToMessage) message.replyTo;
        if (replyToMessage.isQuoteManual) {
          cornerDrawable = Drawables.load(R.drawable.baseline_format_quote_close_18);
        } else {
          cornerDrawable = null;
        }
        if (replyToMessage.origin != null) {
          handleOrigin(replyToMessage.origin);
        }
        if (message.chatId == replyToMessage.chatId) {
          TdApi.Message foundMessage = parent.manager().getAdapter().tryFindMessage(replyToMessage.chatId, replyToMessage.messageId);
          if (foundMessage != null) {
            setMessage(foundMessage, false, true);
            return;
          }
        }
        if (!Td.isEmpty(replyToMessage.quote) || replyToMessage.content != null) {
          this.quote = replyToMessage.quote;
          TdApi.Message fakeMessage = TD.newFakeMessage(replyToMessage.chatId, sender, replyToMessage.content == null ? new TdApi.MessageText(replyToMessage.quote, null, null) : replyToMessage.content);
          fakeMessage.id = replyToMessage.messageId;

          ContentPreview contentPreview = ContentPreview.getChatListPreview(tdlib, fakeMessage.chatId, fakeMessage, true);
          if (!Td.isEmpty(replyToMessage.quote)) {
            contentPreview = new ContentPreview(contentPreview, replyToMessage.quote);
          }
          MediaPreview mediaPreview = replyToMessage.content != null ? newMediaPreview(replyToMessage.chatId, replyToMessage.content) : null;
          this.content = new ContentPreview(translatedText, contentPreview);
          setTitleImpl(computeTitleText(null));
          this.mediaPreview = mediaPreview;
          buildLayout();
          invalidate(mediaPreview != null);
          this.ignoreFailures = true;
        }
        if (replyToMessage.origin == null) {
          if (message.forwardInfo != null && message.forwardInfo.fromChatId != 0 && message.forwardInfo.fromMessageId != 0 && !parent.isRepliesChat()) {
            function = new TdApi.GetRepliedMessage(message.forwardInfo.fromChatId, message.forwardInfo.fromMessageId);
          } else {
            function = new TdApi.GetRepliedMessage(message.chatId, message.id);
          }
          if (replyToMessage.chatId != 0 && replyToMessage.messageId != 0) {
            retryFunction = new TdApi.GetMessage(replyToMessage.chatId, replyToMessage.messageId);
          } else {
            retryFunction = null;
          }
        } else {
          function = null;
          retryFunction = null;
        }
        break;
      }
      case TdApi.MessageReplyToStory.CONSTRUCTOR: {
        TdApi.MessageReplyToStory replyToStory = (TdApi.MessageReplyToStory) message.replyTo;
        function = new TdApi.GetStory(replyToStory.storySenderChatId, replyToStory.storyId, true);
        retryFunction = new TdApi.GetStory(replyToStory.storySenderChatId, replyToStory.storyId, false);
        break;
      }
      default: {
        Td.assertMessageReplyTo_699c5345();
        throw Td.unsupported(message.replyTo);
      }
    }
    if (function != null) {
      flags |= FLAG_LOADING;
      this.retryFunction = retryFunction;
      tdlib.client().send(function, this);
    }
  }

  private void parseContent (final TdApi.Message msg, final boolean forceRequestImage) {
    Background.instance().post(() -> setMessage(msg, forceRequestImage, false));
  }

  private void parseContent (final TdApi.Story story, final boolean forceRequestImage) {
    Background.instance().post(() -> setStory(story, forceRequestImage, false));
  }

  public void setChannelTitle (final String title) {
    Background.instance().post(() -> {
      setTitleImpl(title);
      buildTitle();
      invalidate(false);
    });
  }

  private void setContent (final String title, final ContentPreview content, MediaPreview mediaPreview, final boolean forceRequest) {
    Background.instance().post(() -> {
      ReplyComponent.this.currentMessage = null;
      ReplyComponent.this.content = new ContentPreview(translatedText, content);
      setTitleImpl(title);
      ReplyComponent.this.mediaPreview = mediaPreview;
      buildLayout();
      invalidate(!isMessageComponent() || mediaPreview != null || forceRequest);
    });
  }

  private TdApi.Message currentMessage;
  private TdApi.Error currentError;

  public boolean hasValidMessage () {
    return currentMessage != null;
  }

  public TdApi.Error getError () {
    return currentError;
  }

  public CharSequence toErrorText () {
    if (currentError != null && currentError.code == 404) {
      return Lang.getString(R.string.ReplyMessageDeletedHint);
    }
    return TD.toErrorString(currentError);
  }

  public boolean replaceMessageContent (long messageId, TdApi.MessageContent content) {
    if (currentMessage != null && currentMessage.id == messageId) {
      currentMessage.content = content;
      parseContent(currentMessage, true);
      return true;
    }
    return false;
  }

  private TdApi.FormattedText translatedText;

  public boolean replaceMessageTranslation (long messageId, TdApi.FormattedText translation) {
    if (currentMessage != null && currentMessage.id == messageId) {
      translatedText = translation;
      parseContent(currentMessage, true);
      return true;
    }
    return false;
  }

  public boolean deleteMessageContent (long messageId) {
    if (currentMessage != null && currentMessage.id == messageId) {
      setContent(Lang.getString(R.string.Error), new ContentPreview(null, R.string.DeletedMessage), null, true);
      return true;
    }
    return false;
  }

  // private boolean isPrivate;

  private void setStory (TdApi.Story story, boolean forceRequestImage, boolean forceLocal) {
    // TODO
  }

  private String computeTitleText (String defaultText) {
    return BitwiseUtils.hasFlag(flags, FLAG_FORCE_TITLE) ? this.title :
      sender == null ? StringUtils.isEmpty(senderName) ? (StringUtils.isEmpty(title) ? defaultText : title) : senderName :
      StringUtils.isEmpty(senderName) ? tdlib.senderName(sender, isMessageComponent()) :
      Lang.getString(isChannel() ? R.string.format_channelAndSignature : R.string.format_chatAndSignature, tdlib.senderName(sender, isMessageComponent()), senderName);
  }

  private static class MediaPreview {
    private final ImageFile miniThumbnail;
    private final ImageFile preview;
    private final Path contour;
    private final boolean hasSpoiler;
    private final boolean previewCircle;

    public MediaPreview (ImageFile miniThumbnail, ImageFile preview, Path contour, boolean hasSpoiler, boolean previewCircle) {
      this.miniThumbnail = miniThumbnail;
      this.preview = preview;
      this.contour = contour;
      this.hasSpoiler = hasSpoiler;
      this.previewCircle = previewCircle;
    }
  }

  private MediaPreview newMediaPreview (long chatId, TdApi.MessageContent content) {
    boolean isPrivate = Td.isSecret(content);
    Path contour = null;
    TdApi.Thumbnail thumbnail = null;
    TdApi.PhotoSize photoSize = null;
    TdApi.Minithumbnail miniThumbnail = null;
    boolean previewCircle = false;
    boolean hasSpoiler = false;
    //noinspection SwitchIntDef
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.MessagePhoto messagePhoto = (TdApi.MessagePhoto) content;
        TdApi.Photo photo = messagePhoto.photo;
        photoSize = MediaWrapper.pickDisplaySize(tdlib, photo.sizes, chatId);
        TdApi.PhotoSize smallest = Td.findSmallest(photo);
        if (smallest != null && smallest != photoSize) {
          thumbnail = TD.toThumbnail(photoSize);
        }
        miniThumbnail = photo.minithumbnail;
        hasSpoiler = messagePhoto.hasSpoiler;
        break;
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
        TdApi.ChatPhoto photo = ((TdApi.MessageChatChangePhoto) content).photo;
        photoSize = MediaWrapper.pickDisplaySize(tdlib, photo.sizes, chatId);
        TdApi.PhotoSize smallest = Td.findSmallest(photo.sizes);
        if (smallest != null && smallest != photoSize) {
          thumbnail = TD.toThumbnail(photoSize);
        }
        miniThumbnail = photo.minithumbnail;
        previewCircle = true;
        break;
      }
      case TdApi.MessageGame.CONSTRUCTOR: {
        TdApi.Photo photo = ((TdApi.MessageGame) content).game.photo;
        photoSize = MediaWrapper.pickDisplaySize(tdlib, photo.sizes, chatId);
        miniThumbnail = photo.minithumbnail;
        break;
      }
      case TdApi.MessageSticker.CONSTRUCTOR: {
        TdApi.Sticker sticker = ((TdApi.MessageSticker) content).sticker;
        thumbnail = sticker.thumbnail != null ? sticker.thumbnail : TD.toThumbnail(sticker);
        contour = Td.buildOutline(sticker, isMessageComponent() ? mHeight : height);
        break;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.MessageVideo messageVideo = (TdApi.MessageVideo) content;
        TdApi.Video video = messageVideo.video;
        miniThumbnail = video.minithumbnail;
        thumbnail = video.thumbnail;
        hasSpoiler = messageVideo.hasSpoiler;
        break;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.MessageAnimation messageAnimation = (TdApi.MessageAnimation) content;
        TdApi.Animation animation = messageAnimation.animation;
        miniThumbnail = animation.minithumbnail;
        thumbnail = animation.thumbnail;
        hasSpoiler = messageAnimation.hasSpoiler;
        break;
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        TdApi.VideoNote videoNote = ((TdApi.MessageVideoNote) content).videoNote;
        miniThumbnail = videoNote.minithumbnail;
        thumbnail = videoNote.thumbnail;
        previewCircle = true;
        break;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document doc = ((TdApi.MessageDocument) content).document;
        miniThumbnail = doc.minithumbnail;
        thumbnail = doc.thumbnail;
        break;
      }
      case TdApi.MessageText.CONSTRUCTOR: {
        TdApi.WebPage webPage = ((TdApi.MessageText) content).webPage;
        if (webPage != null) {
          if (webPage.photo != null) {
            photoSize = MediaWrapper.pickDisplaySize(tdlib, webPage.photo.sizes, chatId);
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
    return new MediaPreview(miniPreview, preview, contour, hasSpoiler, previewCircle);
  }

  private void setMessage (TdApi.Message msg, boolean forceRequestImage, boolean forceLocal) {
    currentMessage = msg;
    MediaPreview mediaPreview = newMediaPreview(msg.chatId, msg.content);
    ContentPreview contentPreview = ContentPreview.getChatListPreview(tdlib, msg.chatId, msg, true);
    if (!Td.isEmpty(quote)) {
      contentPreview = new ContentPreview(contentPreview, quote);
    }
    if (msg.forwardInfo != null /*&& (parent != null && parent.getMessage().forwardInfo != null)*/) {
      handleOrigin(msg.forwardInfo.origin);
    } else if (msg.importInfo != null) {
      handleOrigin(new TdApi.MessageOriginHiddenUser(msg.importInfo.senderName));
    } else {
      switch (msg.senderId.getConstructor()) {
        case TdApi.MessageSenderUser.CONSTRUCTOR: {
          handleOrigin(new TdApi.MessageOriginUser(((TdApi.MessageSenderUser) msg.senderId).userId));
          break;
        }
        case TdApi.MessageSenderChat.CONSTRUCTOR: {
          handleOrigin(new TdApi.MessageOriginChat(((TdApi.MessageSenderChat) msg.senderId).chatId, tdlib.isFromAnonymousGroupAdmin(msg) ? msg.authorSignature : null));
          break;
        }
        default: {
          Td.assertMessageSender_439d4c9c();
          throw Td.unsupported(msg.senderId);
        }
      }
    }
    String title = computeTitleText(null);
    if (Thread.currentThread() == Background.instance().thread() || forceLocal) {
      this.content = new ContentPreview(translatedText, contentPreview);
      setTitleImpl(title);
      this.mediaPreview = mediaPreview;
      buildLayout();
      invalidate(forceRequestImage || !isMessageComponent() || mediaPreview != null);
    } else {
      setContent(title, contentPreview, mediaPreview, false);
    }
    if (parent != null) {
      UI.execute(() -> parent.updateReactionAvatars(true));
    }
  }

  private void handleOrigin (TdApi.MessageOrigin origin) {
    switch (origin.getConstructor()) {
      case TdApi.MessageOriginUser.CONSTRUCTOR:
        sender = new TdApi.MessageSenderUser(((TdApi.MessageOriginUser) origin).senderUserId);
        senderName = null;
        break;
      case TdApi.MessageOriginChannel.CONSTRUCTOR: {
        TdApi.MessageOriginChannel channel = ((TdApi.MessageOriginChannel) origin);
        sender = new TdApi.MessageSenderChat(channel.chatId);
        senderName = !StringUtils.isEmpty(channel.authorSignature) ? channel.authorSignature : null;
        break;
      }
      case TdApi.MessageOriginChat.CONSTRUCTOR: {
        TdApi.MessageOriginChat chat = (TdApi.MessageOriginChat) origin;
        sender = new TdApi.MessageSenderChat(chat.senderChatId);
        senderName = chat.authorSignature;
        break;
      }
      case TdApi.MessageOriginHiddenUser.CONSTRUCTOR: {
        senderName = ((TdApi.MessageOriginHiddenUser) origin).senderName;
        sender = null;
        break;
      }
      default: {
        Td.assertMessageOrigin_f2224a59();
        throw Td.unsupported(origin);
      }
    }
    accentColor = sender != null ? tdlib.senderAccentColor(sender) : null;
  }

  private boolean useAccentColor () {
    return accentColor != null && BitwiseUtils.hasFlag(flags, FLAG_USE_COLORIZE);
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
      case TdApi.Story.CONSTRUCTOR: {
        parseContent((TdApi.Story) object, false);
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        if (retryFunction != null) {
          TdApi.Function<?> function = retryFunction;
          retryFunction = null;
          tdlib.client().send(function, this);
          return;
        }
        if (!ignoreFailures) {
          this.currentError = (TdApi.Error) object;
          String errorTitle = Lang.getString(R.string.Error);
          setContent((sender == null && StringUtils.isEmpty(senderName)) ? errorTitle : computeTitleText(errorTitle), TD.errorCode(object) == 404 ? new ContentPreview(null, R.string.DeletedMessage) : new ContentPreview(null, 0, TD.toErrorString(object), true), null, false);
          currentMessage = null;
        }
        break;
      }
      default: {
        throw new UnsupportedOperationException(object.toString());
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
    this.cornerDrawable = null;
    this.isDestroyed = true;
  }
}
