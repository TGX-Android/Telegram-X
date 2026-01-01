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
 * File created for forum topics support
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class ForumTopicView extends BaseView implements TdlibEmojiManager.Watcher, TdlibStatusManager.HelperTarget, Text.TextMediaListener {
  private static TextPaint titlePaint;
  private static TextPaint senderPaint;
  private static TextPaint previewPaint;
  private static TextPaint timePaint;
  private static Paint iconPaint;

  private Tdlib tdlib;
  private TdApi.ForumTopic topic;

  private String titleText;
  private String senderText;
  private String previewText;
  private String timeText;
  private Counter unreadCounter;
  private Counter reactionsCounter;
  private boolean isMuted;
  private String highlightQuery;

  // Message status for outgoing messages
  private boolean isSending;
  private boolean isOutgoing;
  private boolean isMessageUnread;
  private boolean showingDraft;

  // Icon loading
  private long customEmojiId;
  private TdlibEmojiManager.Entry customEmoji;
  private ImageFile thumbnail;
  private ImageFile imageFile;
  private GifFile gifFile;
  private final ComplexReceiver iconReceiver;

  // Text media (custom emoji in preview)
  private final ComplexReceiver textMediaReceiver;

  // Typing status
  private TdlibStatusManager.Helper statusHelper;
  private boolean isAttached;

  private static final int PADDING_LEFT = 72;
  private static final int PADDING_RIGHT = 16;
  private static final int ICON_SIZE = 44;
  private static final int ICON_LEFT = 14;

  public ForumTopicView (Context context) {
    super(context, null);
    setWillNotDraw(false);
    RippleSupport.setTransparentSelector(this);
    initPaints();
    iconReceiver = new ComplexReceiver(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    textMediaReceiver = new ComplexReceiver(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
  }

  private static void initPaints () {
    if (titlePaint == null) {
      titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      titlePaint.setTextSize(Screen.dp(16f));
      titlePaint.setTypeface(Fonts.getRobotoMedium());
      titlePaint.setColor(Theme.textAccentColor());
      ThemeManager.addThemeListener(titlePaint, ColorId.text);

      senderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      senderPaint.setTextSize(Screen.dp(15f));
      senderPaint.setTypeface(Fonts.getRobotoRegular());
      senderPaint.setColor(Theme.textAccentColor());
      ThemeManager.addThemeListener(senderPaint, ColorId.text);

      previewPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      previewPaint.setTextSize(Screen.dp(15f));
      previewPaint.setTypeface(Fonts.getRobotoRegular());
      previewPaint.setColor(Theme.textDecentColor());
      ThemeManager.addThemeListener(previewPaint, ColorId.textLight);

      timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      timePaint.setTextSize(Screen.dp(12f));
      timePaint.setTypeface(Fonts.getRobotoRegular());
      timePaint.setColor(Theme.textDecentColor());
      ThemeManager.addThemeListener(timePaint, ColorId.textLight);

      iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }
  }

  public void attach () {
    iconReceiver.attach();
    textMediaReceiver.attach();
    isAttached = true;
    if (statusHelper != null && topic != null) {
      statusHelper.attachToChat(topic.info.chatId, new TdApi.MessageTopicForum(topic.info.forumTopicId));
    }
  }

  public void detach () {
    iconReceiver.detach();
    textMediaReceiver.detach();
    isAttached = false;
    if (statusHelper != null) {
      statusHelper.detachFromAnyChat();
    }
  }

  public void destroy () {
    iconReceiver.performDestroy();
    textMediaReceiver.performDestroy();
    if (customEmojiId != 0 && customEmoji == null && tdlib != null) {
      tdlib.emoji().forgetWatcher(customEmojiId, this);
    }
    if (statusHelper != null) {
      statusHelper.detachFromAnyChat();
    }
  }

  // TdlibStatusManager.HelperTarget implementation

  @Override
  public void layoutChatAction () {
    // Typing text layout is handled in onDraw
    invalidate();
  }

  @Override
  public void invalidateTypingPart (boolean onlyIcon) {
    invalidate();
  }

  @Override
  public boolean canLoop () {
    return isAttached;
  }

  @Override
  public boolean canAnimate () {
    return isAttached;
  }

  public void setTopic (Tdlib tdlib, TdApi.ForumTopic topic) {
    setTopic(tdlib, topic, null);
  }

  public void setTopic (Tdlib tdlib, TdApi.ForumTopic topic, @Nullable String highlightQuery) {
    this.tdlib = tdlib;
    this.topic = topic;
    this.highlightQuery = highlightQuery;

    // Initialize status helper for typing status
    if (statusHelper == null) {
      statusHelper = new TdlibStatusManager.Helper(context(), tdlib, this, null);
    }
    // Attach to this topic for typing status
    if (isAttached) {
      statusHelper.attachToChat(topic.info.chatId, new TdApi.MessageTopicForum(topic.info.forumTopicId));
    }

    // Build title
    this.titleText = topic.info.name;
    if (topic.info.isClosed) {
      this.titleText = "\uD83D\uDD12 " + titleText; // Lock emoji
    }
    if (topic.isPinned) {
      this.titleText = "\uD83D\uDCCC " + titleText; // Pin emoji
    }
    // Check if muted (respects useDefaultMuteFor and parent chat settings)
    boolean isMuted = tdlib.forumTopicNeedsMuteIcon(topic.info.chatId, topic);
    if (isMuted) {
      this.titleText = "\uD83D\uDD07 " + titleText; // Muted speaker emoji
    }

    // Check if we should show draft (draft exists with text input)
    boolean hasDraft = topic.draftMessage != null &&
      topic.draftMessage.inputMessageText != null &&
      topic.draftMessage.inputMessageText.getConstructor() == TdApi.InputMessageText.CONSTRUCTOR;

    if (hasDraft) {
      // Show draft preview
      this.showingDraft = true;
      TdApi.InputMessageText inputText = (TdApi.InputMessageText) topic.draftMessage.inputMessageText;
      String draftText = inputText.text != null && !StringUtils.isEmpty(inputText.text.text) ?
        inputText.text.text : "";
      this.senderText = Lang.getString(R.string.Draft);
      this.previewText = draftText;
      this.timeText = Lang.timeOrDateShort(topic.draftMessage.date, java.util.concurrent.TimeUnit.SECONDS);
      this.isOutgoing = false;
      this.isSending = false;
      this.isMessageUnread = false;
    } else if (topic.lastMessage != null) {
      // Build preview text from last message
      this.showingDraft = false;
      ContentPreview preview = ContentPreview.getChatListPreview(tdlib, topic.info.chatId, topic.lastMessage, true);
      String messageText = preview != null ? preview.buildText(false) : "";

      // Sender name on separate line (like 3-line chat list mode)
      if (topic.lastMessage.isOutgoing) {
        this.senderText = Lang.getString(R.string.FromYou);
      } else {
        String senderName = tdlib.senderName(topic.lastMessage, false, false);
        this.senderText = !StringUtils.isEmpty(senderName) ? senderName : "";
      }
      this.previewText = messageText;
      this.timeText = Lang.timeOrDateShort(topic.lastMessage.date, java.util.concurrent.TimeUnit.SECONDS);

      // Calculate message status for outgoing messages
      this.isOutgoing = topic.lastMessage.isOutgoing;
      this.isSending = tdlib.messageSending(topic.lastMessage);
      // Message is unread if message ID > last read outbox message ID
      this.isMessageUnread = topic.lastMessage.id > topic.lastReadOutboxMessageId;
    } else {
      this.showingDraft = false;
      this.senderText = "";
      this.previewText = "";
      this.timeText = "";
      this.isOutgoing = false;
      this.isSending = false;
      this.isMessageUnread = false;
    }

    // Check muted state (respects useDefaultMuteFor and parent chat settings)
    this.isMuted = tdlib.forumTopicNeedsMuteIcon(topic.info.chatId, topic);

    // Unread counter - pass muted state for proper badge coloring
    if (topic.unreadCount > 0) {
      if (unreadCounter == null) {
        unreadCounter = new Counter.Builder().callback(this).build();
      }
      unreadCounter.setCount(topic.unreadCount, isMuted, false);
    } else {
      unreadCounter = null;
    }

    // Reactions counter - show if there are unread reactions
    // Using same pattern as TGChat: baseline_favorite_14 icon at 16f size
    if (topic.unreadReactionCount > 0) {
      if (reactionsCounter == null) {
        reactionsCounter = new Counter.Builder()
          .drawable(R.drawable.baseline_favorite_14, 16f, 0f, Gravity.CENTER)
          .callback(this)
          .build();
      }
      reactionsCounter.setCount(topic.unreadReactionCount, isMuted, false);
    } else {
      reactionsCounter = null;
    }

    // Load topic icon
    loadTopicIcon();

    invalidate();
  }

  private int lastMeasuredWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    if (lastMeasuredWidth != width) {
      lastMeasuredWidth = width;
      buildTextLayouts();
    }
  }

  private void buildTextLayouts () {
    int width = getMeasuredWidth();
    if (width <= 0 || topic == null) {
      displayTitle = null;
      displaySender = null;
      displayPreview = null;
      return;
    }

    int textLeft = Screen.dp(PADDING_LEFT);
    int textRight = width - Screen.dp(PADDING_RIGHT);
    int availWidth = textRight - textLeft;

    // Build title Text with emoji support (4-parameter constructor for String)
    if (!StringUtils.isEmpty(titleText)) {
      Highlight highlight = !StringUtils.isEmpty(highlightQuery) ? Highlight.valueOf(titleText, highlightQuery) : null;
      displayTitle = new Text.Builder(
        titleText,
        availWidth,
        Paints.robotoStyleProvider(16f),
        TextColorSets.Regular.NORMAL
      ).singleLine()
       .highlight(highlight)
       .allBold()
       .ignoreNewLines()
       .build();
    } else {
      displayTitle = null;
    }

    // Build sender Text with emoji support (4-parameter constructor for String)
    if (!StringUtils.isEmpty(senderText)) {
      displaySender = new Text.Builder(
        senderText,
        availWidth,
        Paints.robotoStyleProvider(15f),
        showingDraft ? TextColorSets.Regular.NEGATIVE : TextColorSets.Regular.NORMAL
      ).singleLine()
       .ignoreNewLines()
       .build();
    } else {
      displaySender = null;
    }

    // Build preview Text with custom emoji support
    if (previewFormattedText != null && !StringUtils.isEmpty(previewFormattedText.text)) {
      displayPreview = new Text.Builder(
        tdlib,
        previewFormattedText,
        null, // urlOpenParameters
        availWidth,
        Paints.robotoStyleProvider(15f),
        TextColorSets.Regular.LIGHT,
        this // textMediaListener for custom emoji loading
      ).singleLine()
       .ignoreNewLines()
       .build();
    } else if (!StringUtils.isEmpty(previewText)) {
      displayPreview = new Text.Builder(
        previewText,
        availWidth,
        Paints.robotoStyleProvider(15f),
        TextColorSets.Regular.LIGHT
      ).singleLine()
       .ignoreNewLines()
       .build();
    } else {
      displayPreview = null;
    }

    // Request text media for custom emoji
    requestTextMedia();
  }

  /**
   * Sets the topic view to display a message search result.
   */
  public void setMessageSearchResult (Tdlib tdlib, TdApi.ForumTopic topic, TdApi.Message foundMessage, String highlightQuery) {
    setTopic(tdlib, topic, highlightQuery);
  }

  private void loadTopicIcon () {
    TdApi.ForumTopicIcon icon = topic.info.icon;
    if (icon == null) {
      return;
    }

    // Clear previous files
    imageFile = null;
    gifFile = null;
    thumbnail = null;
    iconReceiver.clear();

    if (icon.customEmojiId != 0) {
      // Custom emoji icon - request loading
      this.customEmojiId = icon.customEmojiId;
      this.customEmoji = tdlib.emoji().findOrPostponeRequest(customEmojiId, this);
      if (customEmoji != null && !customEmoji.isNotFound()) {
        buildCustomEmojiIcon(customEmoji);
      } else {
        // Trigger loading of postponed emoji requests
        tdlib.emoji().performPostponedRequests();
      }
    } else {
      this.customEmojiId = 0;
      this.customEmoji = null;
    }

    requestIconFiles();
  }

  private void buildCustomEmojiIcon (TdlibEmojiManager.Entry entry) {
    TdApi.Sticker sticker = entry.value;
    if (sticker == null) return;

    int size = Screen.dp(ICON_SIZE);

    // Thumbnail
    thumbnail = TD.toImageFile(tdlib, sticker.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(size);
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
      thumbnail.setNoBlur();
    }

    // Main image/animation
    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR: {
        this.gifFile = new GifFile(tdlib, sticker);
        this.gifFile.setScaleType(GifFile.FIT_CENTER);
        this.gifFile.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
        this.gifFile.setRequestedSize(size);
        break;
      }
      case TdApi.StickerFormatWebp.CONSTRUCTOR: {
        this.imageFile = new ImageFile(tdlib, sticker.sticker);
        this.imageFile.setSize(size);
        this.imageFile.setScaleType(ImageFile.FIT_CENTER);
        this.imageFile.setNoBlur();
        break;
      }
    }
  }

  private void requestIconFiles () {
    DoubleImageReceiver preview = iconReceiver.getPreviewReceiver(0);
    preview.requestFile(null, thumbnail);
    if (imageFile != null) {
      iconReceiver.getImageReceiver(0).requestFile(imageFile);
    } else if (gifFile != null) {
      iconReceiver.getGifReceiver(0).requestFile(gifFile);
    }
  }

  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    this.customEmoji = entry;
    if (!entry.isNotFound()) {
      buildCustomEmojiIcon(entry);
    }
    // Request files on UI thread
    if (tdlib != null) {
      tdlib.ui().post(() -> {
        requestIconFiles();
        invalidate();
      });
    }
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (topic == null) return;

    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    // Draw topic icon
    int iconLeft = Screen.dp(ICON_LEFT);
    int iconSize = Screen.dp(ICON_SIZE);
    int iconCenterX = iconLeft + iconSize / 2;
    int iconCenterY = height / 2;
    int iconRadius = iconSize / 2;

    TdApi.ForumTopicIcon icon = topic.info.icon;
    if (icon != null) {
      boolean hasLoadedEmoji = customEmojiId != 0 && customEmoji != null && !customEmoji.isNotFound();

      // Draw colored circle background only if no custom emoji loaded
      if (!hasLoadedEmoji) {
        iconPaint.setColor(getTopicColor(icon.color));
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, iconPaint);
      }

      // Draw custom emoji icon if available
      if (hasLoadedEmoji) {
        drawCustomEmojiIcon(canvas, iconLeft, iconCenterY - iconRadius, iconLeft + iconSize, iconCenterY + iconRadius);
      } else if (customEmojiId == 0) {
        // No custom emoji - draw first letter
        drawLetterIcon(canvas, iconCenterX, iconCenterY);
      }
      // If customEmojiId != 0 but not loaded yet, just show colored circle
    }

    // Calculate text bounds
    int textLeft = Screen.dp(PADDING_LEFT);
    int textRight = width - Screen.dp(PADDING_RIGHT);

    // Draw time on the right
    float timeWidth = 0;
    float statusIconWidth = 0;
    if (!StringUtils.isEmpty(timeText)) {
      timeWidth = timePaint.measureText(timeText);
      canvas.drawText(timeText, textRight - timeWidth, Screen.dp(28f), timePaint);

      // Draw status icon for outgoing messages (to the left of time)
      if (isOutgoing) {
        float iconY = Screen.dp(28f);
        if (isSending) {
          // Clock icon for sending messages
          int iconX = (int) (textRight - timeWidth - Screen.dp(4f) - Screen.dp(Icons.CLOCK_SHIFT_X) - Screen.dp(10f));
          Drawables.draw(canvas, Icons.getClockIcon(ColorId.iconLight), iconX, iconY - Screen.dp(Icons.CLOCK_SHIFT_Y) - Screen.dp(10f), Paints.getIconLightPorterDuffPaint());
          statusIconWidth = Screen.dp(14f);
        } else {
          // Single tick for sent, double tick for read
          int iconX = (int) (textRight - timeWidth - Screen.dp(4f) - Screen.dp(Icons.TICKS_SHIFT_X) - Screen.dp(14f));
          Drawable tickIcon = isMessageUnread ? Icons.getSingleTick(ColorId.ticks) : Icons.getDoubleTick(ColorId.ticks);
          Paint tickPaint = isMessageUnread ? Paints.getTicksPaint() : Paints.getTicksReadPaint();
          Drawables.draw(canvas, tickIcon, iconX, iconY - Screen.dp(Icons.TICKS_SHIFT_Y) - Screen.dp(10f), tickPaint);
          statusIconWidth = Screen.dp(18f);
        }
      }
    }

    // Draw title with optional highlighting
    int titleRight = (int) (textRight - timeWidth - statusIconWidth - Screen.dp(8f));
    if (!StringUtils.isEmpty(titleText)) {
      String ellipsizedTitle = TextUtils.ellipsize(titleText, titlePaint, titleRight - textLeft, TextUtils.TruncateAt.END).toString();
      float titleY = Screen.dp(28f);

      if (!StringUtils.isEmpty(highlightQuery)) {
        // Draw title with highlight
        drawHighlightedText(canvas, ellipsizedTitle, textLeft, titleY, titlePaint, highlightQuery);
      } else {
        canvas.drawText(ellipsizedTitle, textLeft, titleY, titlePaint);
      }
    }

    // Draw counters on the right side
    int previewRight = textRight;
    float counterCenterY = height / 2 + Screen.dp(12f);

    // Draw unread counter (rightmost)
    if (unreadCounter != null) {
      float counterWidth = unreadCounter.getWidth();
      previewRight -= (int) (counterWidth + Screen.dp(8f));
      unreadCounter.draw(canvas, textRight - counterWidth / 2, counterCenterY, Gravity.CENTER, 1f);
      textRight -= (int) (counterWidth + Screen.dp(4f));
    }

    // Draw reactions counter (to the left of unread counter)
    if (reactionsCounter != null) {
      float counterWidth = reactionsCounter.getWidth();
      previewRight -= (int) (counterWidth + Screen.dp(4f));
      int textColorId = isMuted ? ColorId.badgeMutedText : ColorId.badgeText;
      reactionsCounter.draw(canvas, textRight - counterWidth / 2, counterCenterY, Gravity.CENTER, 1f, this, textColorId);
      textRight -= (int) (counterWidth + Screen.dp(4f));
    }

    // Check if we should show typing status instead of sender/preview text
    TdlibStatusManager.ChatState typingState = statusHelper != null ? statusHelper.drawingState() : null;
    float senderY = Screen.dp(46f);  // Row 2: Sender name
    float previewY = Screen.dp(64f); // Row 3: Message preview

    if (typingState != null) {
      // Draw typing status on row 2 (sender position)
      String typingText = statusHelper.fullText();
      if (!StringUtils.isEmpty(typingText)) {
        float textCenterY = senderY - Screen.dp(6f);
        // Draw typing animation icon
        int iconWidth = DrawAlgorithms.drawStatus(canvas, typingState, textLeft, textCenterY, Theme.getColor(ColorId.textLight), this, ColorId.textLight);
        // Draw typing text
        String ellipsizedTyping = TextUtils.ellipsize(typingText, senderPaint, previewRight - textLeft - iconWidth, TextUtils.TruncateAt.END).toString();
        canvas.drawText(ellipsizedTyping, textLeft + iconWidth, senderY, senderPaint);
      }
    } else {
      // Row 2: Draw sender name (white/accent color)
      if (!StringUtils.isEmpty(senderText)) {
        if (showingDraft) {
          // Draw "Draft" in red
          int savedColor = senderPaint.getColor();
          senderPaint.setColor(Theme.textRedColor());
          String ellipsizedSender = TextUtils.ellipsize(senderText, senderPaint, previewRight - textLeft, TextUtils.TruncateAt.END).toString();
          canvas.drawText(ellipsizedSender, textLeft, senderY, senderPaint);
          senderPaint.setColor(savedColor);
        } else {
          String ellipsizedSender = TextUtils.ellipsize(senderText, senderPaint, previewRight - textLeft, TextUtils.TruncateAt.END).toString();
          canvas.drawText(ellipsizedSender, textLeft, senderY, senderPaint);
        }
      }

      // Row 3: Draw message preview with custom emoji support
      if (displayPreview != null) {
        // Convert baseline to top position (previewY is baseline at 64dp, top is ~52dp)
        int previewTop = (int) previewY - Screen.dp(12f);
        displayPreview.draw(canvas, textLeft, previewTop, null, 1f, textMediaReceiver);
      }
    }

    // Draw separator line at bottom
    canvas.drawLine(textLeft, height - 1, width, height - 1, Paints.strokeSeparatorPaint(ColorId.separator));
  }

  private void drawCustomEmojiIcon (Canvas canvas, int left, int top, int right, int bottom) {
    // Check if we need to apply color filter for themed stickers
    boolean needRepainting = customEmoji != null && TD.needThemedColorFilter(customEmoji.value);

    Receiver content;
    if (imageFile != null) {
      ImageReceiver image = iconReceiver.getImageReceiver(0);
      image.setBounds(left, top, right, bottom);
      content = image;
    } else if (gifFile != null) {
      GifReceiver gif = iconReceiver.getGifReceiver(0);
      gif.setBounds(left, top, right, bottom);
      content = gif;
    } else {
      content = null;
    }

    DoubleImageReceiver preview = content == null || content.needPlaceholder() ? iconReceiver.getPreviewReceiver(0) : null;
    if (preview != null) {
      if (needRepainting) {
        preview.setThemedPorterDuffColorId(ColorId.icon);
      } else {
        preview.disablePorterDuffColorFilter();
      }
      preview.setBounds(left, top, right, bottom);
      preview.draw(canvas);
    }
    if (content != null) {
      if (needRepainting) {
        content.setThemedPorterDuffColorId(ColorId.icon);
      } else {
        content.disablePorterDuffColorFilter();
      }
      content.draw(canvas);
    }
  }

  private void drawLetterIcon (Canvas canvas, int centerX, int centerY) {
    // For General topic (id = 1), show hash symbol "#" instead of letter
    // This matches Telegram for Android (TGA) behavior
    String displayChar;
    if (topic.info.forumTopicId == 1) {
      displayChar = "#";
    } else if (!StringUtils.isEmpty(topic.info.name)) {
      displayChar = topic.info.name.substring(0, 1).toUpperCase();
    } else {
      return;
    }

    TextPaint letterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    letterPaint.setColor(0xFFFFFFFF);
    letterPaint.setTextSize(Screen.dp(20f));
    letterPaint.setTypeface(Fonts.getRobotoMedium());
    letterPaint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics fm = letterPaint.getFontMetrics();
    float textY = centerY - (fm.ascent + fm.descent) / 2;
    canvas.drawText(displayChar, centerX, textY, letterPaint);
  }

  private int getTopicColor (int colorValue) {
    // Telegram topic colors are passed as actual color values like 0x6FB9F0
    // If color is 0 or very small, it's likely a color index (old format)
    if (colorValue > 0x00FFFFFF) {
      // It's already an actual color value with alpha
      return colorValue;
    } else if (colorValue >= 0x100000) {
      // It's a color without alpha - add full opacity
      return 0xFF000000 | colorValue;
    }

    // Fallback: treat as color index for backwards compatibility
    int[] colors = {
      0xFF6FB9F0, // Blue
      0xFFFFD67E, // Yellow
      0xFFCB86DB, // Purple
      0xFF8EEE98, // Green
      0xFFFF93B2, // Pink
      0xFFFB6F5F  // Red
    };
    if (colorValue >= 0 && colorValue < colors.length) {
      return colors[colorValue];
    }
    return colors[0];
  }

  private void drawHighlightedText (Canvas canvas, String text, float x, float y, TextPaint paint, String query) {
    if (StringUtils.isEmpty(query)) {
      canvas.drawText(text, x, y, paint);
      return;
    }

    String lowerText = text.toLowerCase();
    String lowerQuery = query.toLowerCase();
    int matchStart = lowerText.indexOf(lowerQuery);

    if (matchStart < 0) {
      // No match found - draw normal text
      canvas.drawText(text, x, y, paint);
      return;
    }

    int matchEnd = matchStart + query.length();

    // Draw text before highlight
    if (matchStart > 0) {
      String beforeMatch = text.substring(0, matchStart);
      canvas.drawText(beforeMatch, x, y, paint);
      x += paint.measureText(beforeMatch);
    }

    // Draw highlighted part with background
    String matchedText = text.substring(matchStart, matchEnd);
    float matchWidth = paint.measureText(matchedText);

    // Draw highlight background
    Paint.FontMetrics fm = paint.getFontMetrics();
    RectF highlightRect = new RectF(
      x - Screen.dp(1f),
      y + fm.ascent - Screen.dp(1f),
      x + matchWidth + Screen.dp(1f),
      y + fm.descent + Screen.dp(1f)
    );
    Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    highlightPaint.setColor(Theme.getColor(ColorId.textSearchQueryHighlight));
    canvas.drawRoundRect(highlightRect, Screen.dp(2f), Screen.dp(2f), highlightPaint);

    // Draw highlighted text
    canvas.drawText(matchedText, x, y, paint);
    x += matchWidth;

    // Draw text after highlight
    if (matchEnd < text.length()) {
      String afterMatch = text.substring(matchEnd);
      canvas.drawText(afterMatch, x, y, paint);
    }
  }

  // Text.TextMediaListener implementation
  @Override
  public void onInvalidateTextMedia (Text text, @Nullable TextMedia specificMedia) {
    if (text == displayPreview) {
      invalidate();
    }
  }

  private void requestTextMedia () {
    if (displayPreview != null && displayPreview.hasMedia()) {
      textMediaReceiver.clear();
      displayPreview.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }
}
